/*
 * L2_MOVE_CONSTANT.java
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

import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.MethodVisitor;

import static com.avail.interpreter.levelTwo.L2OperandType.CONSTANT;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;

/**
 * Move a constant {@link AvailObject} into an object register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2_MOVE_CONSTANT
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_MOVE_CONSTANT().init(
			CONSTANT.is("constant"),
			WRITE_POINTER.is("destination"));

	@Override
	protected void propagateTypes (
		@NotNull final L2Instruction instruction,
		@NotNull final RegisterSet registerSet,
		final L2Translator translator)
	{
		final AvailObject constant = instruction.constantAt(0);
		final L2WritePointerOperand destinationReg =
			instruction.writeObjectRegisterAt(1);
		registerSet.constantAtPut(
			destinationReg.register(), constant, instruction);
	}

	/**
	 * Given an {@link L2Instruction} using this operation, extract the constant
	 * that is moved by the instruction.
	 *
	 * @param instruction
	 *        The constant-moving instruction to examine.
	 * @return The constant {@link AvailObject} that is moved by the
	 *         instruction.
	 */
	public static AvailObject constantOf (
		final L2Instruction instruction)
	{
		assert instruction.operation == instance;
		return instruction.constantAt(0);
	}

	@Override
	public String debugNameIn (
		final L2Instruction instruction)
	{
		final AvailObject constant = instruction.constantAt(0);
		return super.debugNameIn(instruction)
			+ "(const=" + constant.typeTag() + ")";
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final AvailObject constant = instruction.constantAt(0);
		final L2ObjectRegister destinationReg =
			instruction.writeObjectRegisterAt(1).register();

		// :: destination = constant;
		translator.literal(method, constant);
		translator.store(method, destinationReg);
	}
}
