/*
 * L2WriteVectorOperand.java
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
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * An {@code L2WriteVectorOperand} is an operand of type {@link
 * L2OperandType#WRITE_VECTOR}.  It holds a {@link List} of {@link
 * L2WritePointerOperand}s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2WriteVectorOperand
extends L2Operand
{
	/**
	 * The {@link List} of {@link L2WritePointerOperand}s.
	 */
	private final List<L2WritePointerOperand> elements;

	/**
	 * Answer my {@link List} of {@link L2WritePointerOperand}s.
	 */
	public List<L2WritePointerOperand> elements ()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return elements;
	}

	/**
	 * Construct a new {@code L2WriteVectorOperand} with the specified
	 * {@link List} of {@link L2WritePointerOperand}s.
	 *
	 * @param elements The list of {@link L2WritePointerOperand}s.
	 */
	public L2WriteVectorOperand (final List<L2WritePointerOperand> elements)
	{
		this.elements = Collections.unmodifiableList(elements);
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public L2Operand clone ()
	{
		final List<L2WritePointerOperand> clonedElements = elements.stream()
			.map(r -> (L2WritePointerOperand) r.clone())
			.collect(toList());
		return new L2WriteVectorOperand(clonedElements);
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.WRITE_VECTOR;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public void instructionWasAdded (final L2Instruction instruction)
	{
		for (final L2WritePointerOperand element : elements)
		{
			element.instructionWasAdded(instruction);
		}
	}

	@Override
	public void instructionWasRemoved(final L2Instruction instruction)
	{
		for (final L2WritePointerOperand element : elements)
		{
			element.instructionWasRemoved(instruction);
		}
	}

	@Override
	public void replaceRegisters (
		final Map<L2Register, L2Register> registerRemap,
		final L2Instruction instruction)
	{
		elements.forEach(
			write -> write.replaceRegisters(registerRemap, instruction));
	}

	@Override
	public void addDestinationRegistersTo (
		final List<L2Register> destinationRegisters)
	{
		elements.forEach(
			write -> write.addDestinationRegistersTo(destinationRegisters));
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("→<");
		boolean first = true;
		for (final L2WritePointerOperand write : elements)
		{
			if (!first)
			{
				builder.append(", ");
			}
			final L2ObjectRegister register = write.register();
			builder.append(register);
			builder.append(register.restriction().suffixString());
			first = false;
		}
		builder.append(">");
		return builder.toString();
	}
}
