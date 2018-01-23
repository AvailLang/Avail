/*
 * L2Operand.java
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
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.utility.PublicCloneable;

import java.util.List;
import java.util.Map;

/**
 * An {@code L2Operand} knows its {@link L2OperandType} and any specific value
 * that needs to be captured for that type of operand.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class L2Operand
extends PublicCloneable<L2Operand>
{
	/**
	 * Answer this operand's {@link L2OperandType}.
	 *
	 * @return An {@code L2OperandType}.
	 */
	public abstract L2OperandType operandType ();

	/**
	 * Dispatch this {@code L2Operand} to the provided {@link
	 * L2OperandDispatcher}.
	 *
	 * @param dispatcher The {@code L2OperandDispatcher} visiting the receiver.
	 */
	public abstract void dispatchOperand (
		final L2OperandDispatcher dispatcher);

	/**
	 * This is an operand of the given instruction, which was just added to its
	 * basic block.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} that was just added.
	 */
	public void instructionWasAdded (
		final L2Instruction instruction)
	{
		// Do nothing by default.
	}

	/**
	 * This is an operand of the given instruction, which was just removed from
	 * its basic block.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} that was just added.
	 */
	public void instructionWasRemoved (
		final L2Instruction instruction)
	{
		// Do nothing by default.
	}

	/**
	 * Replace occurrences in this operand of each register that is a key of
	 * this map with the register that is the corresponding value.  Do nothing
	 * to registers that are not keys of the map.  Update all secondary
	 * structures, such as the instruction's source/destination collections.
	 *
	 * @param registerRemap
	 *        A mapping to transform registers in-place.
	 * @param instruction
	 *        The instruction containing this operand.
	 */
	public void replaceRegisters (
		final Map<L2Register, L2Register> registerRemap,
		final L2Instruction instruction)
	{
		// By default do nothing.
	}

	/**
	 * Move any registers used as sources within me into the provided list.
	 *
	 * @param sourceRegisters The {@link List} to update.
	 */
	public void addSourceRegistersTo (final List<L2Register> sourceRegisters)
	{
		// Do nothing by default.
	}

	/**
	 * Move any registers used as destinations within me into the provided list.
	 *
	 * @param destinationRegisters The {@link List} to update.
	 */
	public void addDestinationRegistersTo (
		final List<L2Register> destinationRegisters)
	{
		// Do nothing by default.
	}

	@Override
	public abstract String toString ();
}
