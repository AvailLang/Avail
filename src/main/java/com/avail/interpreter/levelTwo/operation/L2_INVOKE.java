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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.functions.A_Function;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_ARGUMENTS;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.LATEST_RETURN_VALUE;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.STACK_REIFIER;
import com.avail.interpreter.levelTwo.WritesHiddenVariable;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.CheckedMethod;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.Interpreter.*;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * The given function is invoked.  The function may be a primitive, and the
 * primitive may succeed, fail, or replace the current continuation (after
 * reifying the stack).  It may also trigger reification of this frame by
 * Java-returning a {@link StackReifier} instead of null.
 *
 * <p>The return value can be picked up from
 * {@link Interpreter#getLatestResult()} in a subsequent
 * {@link L2_GET_LATEST_RETURN_VALUE} instruction. Note that the value that was
 * returned has not been dynamically type-checked yet, so if its validity can't
 * be proven statically by the VM, the calling function should check the type
 * against its expectation (prior to the value getting captured in any
 * continuation).</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@WritesHiddenVariable({
	CURRENT_ARGUMENTS.class,
	LATEST_RETURN_VALUE.class,
	STACK_REIFIER.class
})
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
			WRITE_BOXED.is("result", SUCCESS),
			PC.is("on return", SUCCESS),
			PC.is("on reification", OFF_RAMP));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_INVOKE instance = new L2_INVOKE();

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove invocations -- but inlining might make them go away.
		return true;
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand function = instruction.operand(0);
		final L2ReadBoxedVectorOperand arguments = instruction.operand(1);
		final L2WriteBoxedOperand result = instruction.operand(2);
//		final L2PcOperand onReturn = instruction.operand(3);
//		final L2PcOperand onReification = instruction.operand(4);

		renderPreamble(instruction, builder);
		builder.append(result.registerString());
		builder.append(" ← ");
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
		final L2WriteBoxedOperand result = instruction.operand(2);
		final L2PcOperand onReturn = instruction.operand(3);
		final L2PcOperand onReification = instruction.operand(4);

		translator.loadInterpreter(method);
		// :: [interpreter]
		translator.loadInterpreter(method);
		// :: [interpreter, interpreter]
		chunkField.generateRead(method);
		// :: [interpreter, callingChunk]
		translator.loadInterpreter(method);
		// :: [interpreter, callingChunk, interpreter]
		translator.load(method, function.register());
		// :: [interpreter, callingChunk, interpreter, function]
		generatePushArgumentsAndInvoke(
			translator,
			method,
			arguments.elements(),
			result,
			onReturn,
			onReification);
	}

	/**
	 * An array of {@link Interpreter#preinvokeMethod} variants, where the
	 * index in the array is the number of arguments.
	 */
	private static final CheckedMethod[] preinvokeMethods =
	{
		preinvoke0Method,
		preinvoke1Method,
		preinvoke2Method,
		preinvoke3Method
	};

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
	 * @param argsRegsList
	 *        The {@link List} of {@link L2ReadBoxedOperand} arguments.
	 * @param result
	 *        Where to write the return result if the call returns without
	 *        reification.
	 * @param onNormalReturn
	 *        Where to jump if the call completes.
	 * @param onReification
	 *        Where to jump if reification is requested during the call.
	 */
	static void generatePushArgumentsAndInvoke (
		final JVMTranslator translator,
		final MethodVisitor method,
		final List<L2ReadBoxedOperand> argsRegsList,
		final L2WriteBoxedOperand result,
		final L2PcOperand onNormalReturn,
		final L2PcOperand onReification)
	{
		// :: caller set up [interpreter, callingChunk, interpreter, function]
		final int numArgs = argsRegsList.size();
		if (numArgs < preinvokeMethods.length)
		{
			argsRegsList.forEach(
				arg -> translator.load(method, arg.register()));
			// :: [interpreter, callingChunk, interpreter, function, [args...]]
			preinvokeMethods[numArgs].generateCall(method);
		}
		else
		{
			translator.objectArray(method, argsRegsList, AvailObject.class);
			// :: [interpreter, callingChunk, interpreter, function, argsArray]
			preinvokeMethod.generateCall(method);
		}
		// :: [interpreter, callingChunk, callingFunction]
		translator.loadInterpreter(method);
		// :: [interpreter, callingChunk, callingFunction, interpreter]
		interpreterRunChunkMethod.generateCall(method);
		// :: [interpreter, callingChunk, callingFunction, reifier]
		postinvokeMethod.generateCall(method);
		// :: [reifier]
		method.visitVarInsn(ASTORE, translator.reifierLocal());
		// :: []
		method.visitVarInsn(ALOAD, translator.reifierLocal());
		// :: if (reifier != null) goto onReificationPreamble;
		// :: result = interpreter.getLatestResult();
		// :: goto onNormalReturn;
		// :: onReificationPreamble: ...
		final Label onReificationPreamble = new Label();
		method.visitJumpInsn(IFNONNULL, onReificationPreamble);
		translator.loadInterpreter(method);
		// :: [interpreter]
		getLatestResultMethod.generateCall(method);
		// :: [latestResult]
		translator.store(method, result.register());
		// :: []
		method.visitJumpInsn(
			GOTO, translator.labelFor(onNormalReturn.offset()));
		method.visitLabel(onReificationPreamble);
		translator.generateReificationPreamble(method, onReification);
	}
}
