/**
 * L2RegisterVector.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.interpreter.levelTwo.register;

import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.L2Translator;

/**
 * {@code L2RegisterVector} aggregates {@linkplain L2ObjectRegister object
 * registers} for convenient manipulation.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class L2RegisterVector
implements Iterable<L2ObjectRegister>
{
	/**
	 * The {@linkplain List list} of aggregated {@linkplain L2ObjectRegister
	 * object registers}.
	 */
	private final @NotNull List<L2ObjectRegister> registers;

	/**
	 * Answer the {@linkplain List list} of aggregated {@linkplain
	 * L2ObjectRegister object registers}.
	 *
	 * @return The aggregated {@linkplain L2ObjectRegister object registers}.
	 */
	public List<L2ObjectRegister> registers ()
	{
		return registers;
	}

	/**
	 * Construct a new {@link L2RegisterVector}.
	 *
	 * @param registers
	 *        The {@linkplain List list} of aggregated {@linkplain
	 *        L2ObjectRegister object registers}.
	 */
	public L2RegisterVector (
		final @NotNull List<L2ObjectRegister> registers)
	{
		this.registers = registers;
	}

	/**
	 * Do all member {@linkplain L2ObjectRegister registers} contain constant
	 * values?
	 *
	 * @param translator The {@linkplain L2Translator translator}.
	 * @return {@code true} if each member {@linkplain L2ObjectRegister
	 *         register} contains a constant value, {@code false} otherwise.
	 */
	public boolean allRegistersAreConstantsIn (
		final @NotNull L2Translator translator)
	{
		for (final L2ObjectRegister register : registers)
		{
			if (!translator.registerHasConstantAt(register))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public Iterator<L2ObjectRegister> iterator ()
	{
		return registers.iterator();
	}
}
