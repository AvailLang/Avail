/*
 * L2_INVOKE_CONSTANT_FUNCTION.java
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

import com.avail.interpreter.execution.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_ARGUMENTS;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_FUNCTION;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.LATEST_RETURN_VALUE;
import com.avail.interpreter.levelTwo.WritesHiddenVariable;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.execution.Interpreter.chunkField;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * The given (constant) function is invoked.  The function may be a primitive,
 * and the primitive may succeed, fail, or replace the current continuation
 * (after reifying the stack).  It may also trigger reification of this frame by
 * Java-returning a {@link StackReifier} instead of null.
 *
 * <p>The return value can be picked up from
 * {@link Interpreter#getLatestResult() latestResult} in a subsequent
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
	CURRENT_FUNCTION.class,
	CURRENT_ARGUMENTS.class,
	LATEST_RETURN_VALUE.class
})
public final class L2_INVOKE_CONSTANT_FUNCTION
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_INVOKE_CONSTANT_FUNCTION}.
	 */
	private L2_INVOKE_CONSTANT_FUNCTION ()
	{
		super(
			CONSTANT.is("constant function"),
			READ_BOXED_VECTOR.is("arguments"),
			WRITE_BOXED.is("result", SUCCESS),
			PC.is("on return", SUCCESS),
			PC.is("on reification", OFF_RAMP));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_INVOKE_CONSTANT_FUNCTION instance =
		new L2_INVOKE_CONSTANT_FUNCTION();

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove invocations -- but inlining might make them go away.
		return true;
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<? extends L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ConstantOperand constantFunction = instruction.operand(0);
		final L2ReadBoxedVectorOperand arguments = instruction.operand(1);
		final L2WriteBoxedOperand result = instruction.operand(2);
//		final L2PcOperand onReturn = instruction.operand(3);
//		final L2PcOperand onReification = instruction.operand(4);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(result.registerString());
		builder.append(" ← ");
		builder.append(constantFunction.object);
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
		final L2ConstantOperand constantFunction = instruction.operand(0);
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
		translator.literal(method, constantFunction.object);
		// :: [interpreter, callingChunk, interpreter, function]
		L2_INVOKE.generatePushArgumentsAndInvoke(
			translator,
			method,
			arguments.elements(),
			result,
			onReturn,
			onReification);
	}
}
