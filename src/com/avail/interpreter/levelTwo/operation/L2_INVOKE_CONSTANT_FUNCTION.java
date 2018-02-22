/*
 * L2_INVOKE_CONSTANT_FUNCTION.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L2Translator;
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
 * The given (constant) function is invoked.  The function may be a primitive,
 * and the primitive may succeed, fail, or replace the current continuation
 * (after reifying the stack).  It may also trigger reification of this frame by
 * Java-returning a {@link StackReifier} instead of null.
 *
 * <p>The return value can be picked up from {@link Interpreter#latestResult} in
 * a subsequent {@link L2_GET_LATEST_RETURN_VALUE} instruction.  Note that the
 * value that was returned has not been dynamically type-checked yet, so if its
 * validity can't be proven statically by the VM, the calling function should
 * check the type against its expectation (prior to the value getting captured
 * in any continuation).</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_INVOKE_CONSTANT_FUNCTION
extends L2Operation
{
	/**
	 * Construct an {@code L2_INVOKE_CONSTANT_FUNCTION}.
	 */
	private L2_INVOKE_CONSTANT_FUNCTION ()
	{
		super(
			CONSTANT.is("constant function"),
			READ_VECTOR.is("arguments"),
			PC.is("on return", SUCCESS),
			PC.is("on reification", OFF_RAMP));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_INVOKE_CONSTANT_FUNCTION instance =
		new L2_INVOKE_CONSTANT_FUNCTION();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
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
		assert this == instruction.operation;
		final L2Operand[] operands = instruction.operands;
		final L2Operand calledFunction = operands[0];
		final L2Operand argsRegsList = operands[1];
//		final L2PcOperand onNormalReturn = instruction.pcAt(2);
//		final L2PcOperand onReification = instruction.pcAt(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(calledFunction);
		builder.append("(");
		builder.append(argsRegsList);
		builder.append(")");
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final AvailObject calledFunction = instruction.constantAt(0);
		final List<L2ReadPointerOperand> argsRegsList =
			instruction.readVectorRegisterAt(1);
		final L2PcOperand onNormalReturn = instruction.pcAt(2);
		final L2PcOperand onReification = instruction.pcAt(3);

		// :: reifier = L2_INVOKE.invoke(
		// ::   interpreter,
		// ::   calledFunction,
		// ::   args)
		translator.loadInterpreter(method);
		translator.literal(method, calledFunction);
		translator.objectArray(method, argsRegsList, AvailObject.class);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(L2_INVOKE.class),
			"invoke",
			getMethodDescriptor(
				getType(StackReifier.class),
				getType(Interpreter.class),
				getType(A_Function.class),
				getType(AvailObject[].class)),
			false);
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
