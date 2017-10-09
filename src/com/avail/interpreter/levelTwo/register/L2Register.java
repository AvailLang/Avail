/**
 * L2Register.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
 *   may be used to endorse or promote products derived set this software
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

package com.avail.interpreter.levelTwo.register;

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.L2Translator;
import com.avail.utility.Nulls;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.avail.utility.Nulls.stripNull;

/**
 * {@code L2Register} models the conceptual use of a register by a {@linkplain
 * L2Operation level two Avail operation} in the {@link L2Translator}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2Register
{
	/**
	 * A coloring number to be used by the {@linkplain Interpreter
	 * interpreter} at runtime to identify the storage location of a
	 * {@linkplain L2Register register}.
	 */
	private int finalIndex = -1;

	/**
	 * Answer the coloring number to be used by the {@linkplain Interpreter
	 * interpreter} at runtime to identify the storage location of a {@linkplain
	 * L2Register register}.
	 *
	 * @return An {@code L2Register} coloring number.
	 */
	public int finalIndex ()
	{
		return finalIndex;
	}

	/**
	 * Set the coloring number to be used by the {@linkplain Interpreter
	 * interpreter} at runtime to identify the storage location of an {@code
	 * L2Register}.
	 *
	 * @param theFinalIndex
	 *        An {@code L2Register} coloring number.
	 */
	public void setFinalIndex (final int theFinalIndex)
	{
		assert finalIndex == -1
			: "Only set the finalIndex of an L2RegisterIdentity once";
		finalIndex = theFinalIndex;
	}

	/**
	 * A value used to distinguish distinct registers.
	 */
	public final long uniqueValue;

	/**
	 * Construct a new {@code L2Register}.
	 *
	 * @param debugValue A {@code long} used to identify this register visually.
	 */
	L2Register (final long debugValue)
	{
		this.uniqueValue = debugValue;
	}

	/** The instruction that assigns to this register. */
	private @Nullable L2Instruction definition;

	/**
	 * Capture the instruction that writes to this register.  This must only be
	 * set once.
	 *
	 * @param instruction
	 *        The instruction that writes to this register in the SSA control
	 *        flow graph of basic blocks.
	 */
	public void setDefinition (final L2Instruction instruction)
	{
		assert definition == null
			: "Register's defining instruction must only be set once";
		definition = instruction;
	}

	/**
	 * Answer the {@link L2Instruction} which assigns this register in the SSA
	 * control flow graph.  It must have been assigned already.
	 */
	public L2Instruction definition ()
	{
		return stripNull(definition);
	}

	/**
	 * The instructions that read from this register.  This is a {@link Set}, so
	 * that an instruction that uses the same register twice only counts once.
	 */
	private final Set<L2Instruction> uses = new HashSet<>();

	/**
	 * Capture another instruction that uses this register.
	 *
	 * @param instruction
	 *        An instruction that reads from this register.
	 */
	public void addUse (final L2Instruction instruction)
	{
		uses.add(instruction);
	}

	/**
	 * Answer the set of instructions that read from this register.  Do not
	 * modify the returned collection.
	 *
	 * @return A {@link Set} of {@link L2Instruction}s.
	 */
	public Set<L2Instruction> uses ()
	{
		return uses;
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("Reg");
		if (finalIndex != -1)
		{
			builder.append("[");
			builder.append(finalIndex);
			builder.append("]");
		}
		builder.append("@");
		builder.append(uniqueValue);
		return builder.toString();
	}
}
