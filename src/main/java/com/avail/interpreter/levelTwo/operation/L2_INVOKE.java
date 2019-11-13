/*
 * L2_INVOKE.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.A_Function;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * The given function is invoked.  The function may be a primitive, and the
 * primitive may succeed, fail, or replace the current continuation (after
 * reifying the stack).  It may also trigger reification of this frame by
 * Java-returning a {@link StackReifier} instead of null.
 *
 * <p>The return value can be picked up from {@link Interpreter#latestResult()
 * latestResult} in a subsequent {@link L2_GET_LATEST_RETURN_VALUE} instruction.
 * Note that the value that was returned has not been dynamically type-checked
 * yet, so if its validity can't be proven statically by the VM, the calling
 * function should check the type against its expectation (prior to the value
 * getting captured in any continuation).</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_INVOKE
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_INVOKE}.
	 */
	private L2_INVOKE ()
	{
		super(
			READ_BOXED.is("called function"),
			READ_BOXED_VECTOR.is("arguments"),
			PC.is("on return", SUCCESS),
			PC.is("on reification", OFF_RAMP));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_INVOKE instance = new L2_INVOKE();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator generator)
	{
		// Successful return or a reification off-ramp.
		assert registerSets.size() == 2;
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove invocations -- but inlining might make them go away.
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		final L2ReadBoxedOperand function = instruction.operand(0);
		final L2ReadBoxedVectorOperand arguments = instruction.operand(1);
//		final L2PcOperand onReturn = instruction.operand(2);
//		final L2PcOperand onReification = instruction.operand(3);

		assert this == instruction.operation();
		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(function.registerString());
		builder.append("(");
		builder.append(arguments.elements());
		builder.append(")");
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand function = instruction.operand(0);
		final L2ReadBoxedVectorOperand arguments = instruction.operand(1);
		final L2PcOperand onReturn = instruction.operand(2);
		final L2PcOperand onReification = instruction.operand(3);

		translator.loadInterpreter(method);
		translator.loadInterpreter(method);
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"chunk",
			getDescriptor(L2Chunk.class));
		translator.loadInterpreter(method);
		translator.load(method, function.register());
		// :: [interpreter, callingChunk, interpreter, function]
		generatePushArgumentsAndInvoke(
			translator,
			method,
			instruction,
			arguments.elements(),
			onReturn,
			onReification);
	}

	/**
	 * Generate code to push the arguments and invoke.  This expects the stack
	 * to already contain the {@link Interpreter}, the calling {@link L2Chunk},
	 * another occurrence of the {@link Interpreter}, and the {@link A_Function}
	 * to be invoked.
	 *
	 * @param translator
	 *        The translator on which to generate the invocation.
	 * @param method
	 *        The {@link MethodVisitor} controlling the method being written.
	 * @param instruction
	 *        The {@link L2Instruction} being translated.
	 * @param argsRegsList
	 *        The {@link List} of {@link L2ReadBoxedOperand} arguments.
	 * @param onNormalReturn
	 *        Where to jump if the call completes.
	 * @param onReification
	 *        Where to jump if reification is requested during the call.
	 */
	static void generatePushArgumentsAndInvoke (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction,
		final List<L2ReadBoxedOperand> argsRegsList,
		final L2PcOperand onNormalReturn,
		final L2PcOperand onReification)
	{
		// Generate special code for common cases of 0-3 arguments.
		//
		// :: caller set up [interpreter, callingChunk, interpreter, function]
		// :: [interpreter, callingChunk, interpreter, function, [args...]]
		// :: interpreter.preinvoke[0-3]?(function, [args...])
		// :: [interpreter, callingChunk, callingFunction]
		// :: -> [interpreter, callingChunk, callingFunction, interpreter]
		// :: interpreter.runChunk()
		// :: [interpreter, callingChunk, callingFunction, reifier]
		// :: interpreter.postinvoke(callingChunk, callingFunction, reifier)
		// :: [reifier]
		switch (argsRegsList.size())
		{
			case 0:
			{
				method.visitMethodInsn(
					INVOKEVIRTUAL,
					getInternalName(Interpreter.class),
					"preinvoke0",
					getMethodDescriptor(
						getType(A_Function.class),
						getType(A_Function.class)),
					false);
				break;
			}
			case 1:
			{
				translator.load(method, argsRegsList.get(0).register());
				method.visitMethodInsn(
					INVOKEVIRTUAL,
					getInternalName(Interpreter.class),
					"preinvoke1",
					getMethodDescriptor(
						getType(A_Function.class),
						getType(A_Function.class),
						getType(AvailObject.class)),
					false);
				break;
			}
			case 2:
			{
				translator.load(method, argsRegsList.get(0).register());
				translator.load(method, argsRegsList.get(1).register());
				method.visitMethodInsn(
					INVOKEVIRTUAL,
					getInternalName(Interpreter.class),
					"preinvoke2",
					getMethodDescriptor(
						getType(A_Function.class),
						getType(A_Function.class),
						getType(AvailObject.class),
						getType(AvailObject.class)),
					false);
				break;
			}
			case 3:
			{
				translator.load(method, argsRegsList.get(0).register());
				translator.load(method, argsRegsList.get(1).register());
				translator.load(method, argsRegsList.get(2).register());
				method.visitMethodInsn(
					INVOKEVIRTUAL,
					getInternalName(Interpreter.class),
					"preinvoke3",
					getMethodDescriptor(
						getType(A_Function.class),
						getType(A_Function.class),
						getType(AvailObject.class),
						getType(AvailObject.class),
						getType(AvailObject.class)),
					false);
				break;
			}
			default:
			{
				translator.objectArray(method, argsRegsList, AvailObject.class);
				method.visitMethodInsn(
					INVOKEVIRTUAL,
					getInternalName(Interpreter.class),
					"preinvoke",
					getMethodDescriptor(
						getType(A_Function.class),
						getType(A_Function.class),
						getType(AvailObject[].class)),
					false);
				break;
			}
		}
		// :: [interpreter, callingChunk, callingFunction]
		translator.loadInterpreter(method);
		// :: -> [interpreter, callingChunk, callingFunction, interpreter]
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"runChunk",
			getMethodDescriptor(
				getType(StackReifier.class)),
			false);
		// :: [interpreter, callingChunk, callingFunction, reifier]
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"postinvoke",
			getMethodDescriptor(
				getType(StackReifier.class),
				getType(L2Chunk.class),
				getType(A_Function.class),
				getType(StackReifier.class)),
			false);
		// :: [reifier]
		method.visitInsn(DUP);
		method.visitVarInsn(ASTORE, translator.reifierLocal());
		// :: if (reifier != null) goto onNormalReturn;
		// :: else goto onReification;
		translator.branch(
			method,
			instruction,
			IFNULL,
			onNormalReturn,
			onReification);
	}
}
