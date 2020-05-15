/*
 * L2_GET_ARGUMENTS.java
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

import com.avail.descriptor.representation.AvailObject;
import com.avail.interpreter.execution.Interpreter;
import com.avail.interpreter.JavaLibrary;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_ARGUMENTS;
import com.avail.interpreter.levelTwo.ReadsHiddenVariable;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.levelTwo.L2OperandType.INT_IMMEDIATE;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Type.getInternalName;

/**
 * Ask the {@link Interpreter} for its {@link Interpreter#argsBuffer}'s n-th
 * element.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@ReadsHiddenVariable(theValue = CURRENT_ARGUMENTS.class)
public final class L2_GET_ARGUMENT
extends L2Operation
{
	/**
	 * Construct an {@code L2_GET_ARGUMENT}.
	 */
	private L2_GET_ARGUMENT ()
	{
		super(
			INT_IMMEDIATE.is("subscript into argsBuffer"),
			WRITE_BOXED.is("argument"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_GET_ARGUMENT instance = new L2_GET_ARGUMENT();

	@Override
	public boolean hasSideEffect ()
	{
		// Keep this instruction pinned in place for safety during inlining.
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
		final L2IntImmediateOperand subscript = instruction.operand(0);
		final L2WriteBoxedOperand argument = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(argument.registerString());
		builder.append(" ← arg#");
		builder.append(subscript.value);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2IntImmediateOperand subscript = instruction.operand(0);
		final L2WriteBoxedOperand argument = instruction.operand(1);

		// :: argument = interpreter.argsBuffer.get(«subscript - 1»);
		translator.loadInterpreter(method);
		Interpreter.argsBufferField.generateRead(method);
		translator.literal(method, subscript.value - 1);
		JavaLibrary.getListGetMethod().generateCall(method);
		method.visitTypeInsn(CHECKCAST, getInternalName(AvailObject.class));
		translator.store(method, argument.register());
	}
}
