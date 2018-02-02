/*
 * L2_MOVE_FLOAT.java
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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteFloatOperand;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_FLOAT;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_FLOAT;

/**
 * Move a {@code double} from the source to the destination. The {@link
 * L2Translator} creates more moves than are strictly necessary, but various
 * mechanisms cooperate to remove redundant inter-register moves.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2_MOVE_FLOAT
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_MOVE_FLOAT().init(
			READ_FLOAT.is("source"),
			WRITE_FLOAT.is("destination"));

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ReadFloatOperand sourceReg =
			instruction.readFloatRegisterAt(0);
		final L2WriteFloatOperand destinationReg =
			instruction.writeFloatRegisterAt(1);

		assert sourceReg.register() != destinationReg.register();
		registerSet.removeConstantAt(destinationReg.register());
		if (registerSet.hasTypeAt(sourceReg.register()))
		{
			registerSet.typeAtPut(
				destinationReg.register(),
				registerSet.typeAt(sourceReg.register()),
				instruction);
		}
		else
		{
			registerSet.removeTypeAt(destinationReg.register());
		}

		if (registerSet.hasConstantAt(sourceReg.register()))
		{
			registerSet.constantAtPut(
				destinationReg.register(),
				registerSet.constantAt(sourceReg.register()),
				instruction);
		}
		registerSet.propagateMove(
			sourceReg.register(), destinationReg.register(), instruction);
	}

	@Override
	public boolean shouldEmit (final L2Instruction instruction)
	{
		final L2ReadFloatOperand sourceReg =
			instruction.readFloatRegisterAt(0);
		final L2WriteFloatOperand destinationReg =
			instruction.writeFloatRegisterAt(1);

		return sourceReg.finalIndex() != destinationReg.finalIndex();
	}

	@Override
	public boolean isMove ()
	{
		return true;
	}

	/**
	 * Given an {@link L2Instruction} using this operation, extract the source
	 * {@link L2ReadFloatOperand} that is moved by the instruction.
	 *
	 * @param instruction
	 *        The move instruction to examine.
	 * @return The move's source {@link L2ReadFloatOperand}.
	 */
	private static L2ReadFloatOperand sourceOf (
		final L2Instruction instruction)
	{
		assert instruction.operation == instance;
		return instruction.readFloatRegisterAt(0);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation;
		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(instruction.writeFloatRegisterAt(1).register());
		builder.append(" ← ");
		builder.append(instruction.readFloatRegisterAt(0).register());
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2FloatRegister sourceReg =
			instruction.readFloatRegisterAt(0).register();
		final L2FloatRegister destinationReg =
			instruction.writeFloatRegisterAt(1).register();

		// :: destination = source;
		translator.load(method, sourceReg);
		translator.store(method, destinationReg);
	}
}
