/*
 * L2WriteIntOperand.java
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2Register;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

/**
 * An {@code L2WriteIntOperand} is an operand of type {@link
 * L2OperandType#WRITE_INT}.  It holds the actual {@link L2IntegerRegister}
 * that is to be accessed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2WriteIntOperand
extends L2Operand
{
	/**
	 * The actual {@link L2IntegerRegister}.
	 */
	private L2IntegerRegister register;

	/**
	 * Answer the {@link L2IntegerRegister}'s {@link
	 * L2IntegerRegister#finalIndex finalIndex}.
	 *
	 * @return The index of the integer register, computed during register
	 *         coloring.
	 */
	public final int finalIndex ()
	{
		return register.finalIndex();
	}

	/**
	 * Construct a new {@code L2WriteIntOperand} with the specified {@link
	 * L2IntegerRegister}.
	 *
	 * @param register The integer register.
	 */
	public L2WriteIntOperand (final L2IntegerRegister register)
	{
		this.register = register;
	}

	/**
	 * Answer the register that is to be written.
	 *
	 * @return An {@link L2IntegerRegister}.
	 */
	public final L2IntegerRegister register ()
	{
		return register;
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.WRITE_INT;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	/**
	 * Answer an {@link L2ReadIntOperand} on the same register as this {@code
	 * L2WriteIntOperand}.
	 *
	 * @return The new {@link L2ReadIntOperand}.
	 */
	public final L2ReadIntOperand read ()
	{
		return new L2ReadIntOperand(register);
	}

	@Override
	public void instructionWasAdded (final L2Instruction instruction)
	{
		register.addDefinition(instruction);
	}

	@Override
	public void instructionWasRemoved (final L2Instruction instruction)
	{
		register.removeDefinition(instruction);
	}

	@Override
	public void replaceRegisters (
		final Map<L2Register, L2Register> registerRemap,
		final L2Instruction instruction)
	{
		final @Nullable L2Register replacement = registerRemap.get(register);
		if (replacement == null || replacement == register)
		{
			return;
		}
		register.removeDefinition(instruction);
		replacement.addDefinition(instruction);
		register = L2IntegerRegister.class.cast(replacement);
	}

	@Override
	public void addDestinationRegistersTo (
		final List<L2Register> destinationRegisters)
	{
		destinationRegisters.add(register);
	}

	@Override
	public String toString ()
	{
		return format("WriteInt(%s)", register);
	}
}
