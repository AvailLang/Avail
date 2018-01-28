/*
 * L2ReadVectorOperand.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.register.L2Register;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.unmodifiableList;

/**
 * An {@code L2ReadVectorOperand} is an operand of type {@link
 * L2OperandType#READ_VECTOR}. It holds a {@link List} of {@link
 * L2ReadOperand}s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @param <R>
 *        A subclass of L2Register&lt;T>
 * @param <T>
 *        A subclass of {@link L2ReadOperand}.
 */
public class L2ReadVectorOperand<
	RR extends L2ReadOperand<R, T>,
	R extends L2Register<T>,
	T extends A_BasicObject>
extends L2Operand
{
	/**
	 * The {@link List} of {@link L2ReadPointerOperand}s.
	 */
	private final List<RR> elements;

	/**
	 * Construct a new {@code L2ReadVectorOperand} with the specified {@link
	 * List} of {@link L2ReadOperand}s.
	 *
	 * @param elements
	 *        The list of {@link L2ReadOperand}s.
	 */
	public L2ReadVectorOperand (
		final List<? extends RR> elements)
	{
		this.elements = unmodifiableList(elements);
	}

	@SuppressWarnings({"MethodDoesntCallSuperMethod", "unchecked"})
	@Override
	public L2ReadVectorOperand<RR, R, T> clone ()
	{
		final List<RR> clonedElements = new ArrayList<>(elements.size());
		for (final RR element : elements)
		{
			clonedElements.add((RR) element.clone());
		}
		return new L2ReadVectorOperand<>(clonedElements);
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.READ_VECTOR;
	}

	/**
	 * Answer my {@link List} of {@link L2ReadOperand}s.
	 *
	 * @return The requested operands.
	 */
	public List<RR> elements ()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return elements;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public void instructionWasAdded (final L2Instruction instruction)
	{
		for (final RR element : elements)
		{
			element.instructionWasAdded(instruction);
		}
	}

	@Override
	public void instructionWasRemoved (final L2Instruction instruction)
	{
		for (final RR element : elements)
		{
			element.instructionWasRemoved(instruction);
		}
	}

	@Override
	public void replaceRegisters (
		final Map<L2Register<?>, L2Register<?>> registerRemap,
		final L2Instruction instruction)
	{
		for (final RR read : elements)
		{
			read.replaceRegisters(registerRemap, instruction);
		}
	}

	@Override
	public void addSourceRegistersTo (final List<L2Register<?>> sourceRegisters)
	{
		for (final RR read : elements)
		{
			read.addSourceRegistersTo(sourceRegisters);
		}
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("@<");
		boolean first = true;
		for (final RR read : elements)
		{
			if (!first)
			{
				builder.append(", ");
			}
			builder.append(read.register());
			builder.append(read.restriction().suffixString());
			first = false;
		}
		builder.append(">");
		return builder.toString();
	}
}
