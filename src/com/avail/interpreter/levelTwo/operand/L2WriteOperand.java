/*
 * L2WriteOperand.java
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
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2Register;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * {@code L2WriteOperand} abstracts the capabilities of actual register write
 * operands.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <R>
 *        The subclass of {@link L2Register}.
 * @param <T>
 *        The type for {@link TypeRestriction}s.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class L2WriteOperand<
	R extends L2Register,
	T extends A_BasicObject>
extends L2Operand
{
	/**
	 * The actual {@link L2Register}.
	 */
	R register;

	/**
	 * Answer the {@link L2Register}'s {@link L2Register#finalIndex finalIndex}.
	 *
	 * @return The index of the register, computed during register coloring.
	 */
	public final int finalIndex ()
	{
		return register.finalIndex();
	}

	/**
	 * Construct a new {@code L2WriteOperand} from the specified {@link
	 * L2Register}.
	 *
	 * @param register
	 *        The {@link L2Register}.
	 */
	L2WriteOperand (final R register)
	{
		this.register = register;
	}

	/**
	 * Answer the register that is to be written.
	 *
	 * @return An {@link L2IntRegister}.
	 */
	public final R register ()
	{
		return register;
	}

	/**
	 * Answer an {@link L2ReadOperand} on the same register as this {@code
	 * L2WriteOperand}.
	 *
	 * @return The new {@link L2ReadOperand}.
	 */
	public abstract L2ReadOperand<R, T> read ();

	@Override
	public final void instructionWasAdded (final L2Instruction instruction)
	{
		register.addDefinition(instruction);
	}

	@Override
	public final void instructionWasRemoved (final L2Instruction instruction)
	{
		register.removeDefinition(instruction);
	}

	@Override
	public final void replaceRegisters (
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
		//noinspection unchecked
		register = (R) register.getClass().cast(replacement);
	}

	@Override
	public final void addDestinationRegistersTo (
		final List<L2Register> destinationRegisters)
	{
		destinationRegisters.add(register);
	}

	@Override
	public final String toString ()
	{
		return "→"
			+ register
			+ register.restriction().suffixString();
	}
}
