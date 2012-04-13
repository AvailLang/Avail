/**
 * L2Register.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import java.util.concurrent.atomic.AtomicLong;
import com.avail.interpreter.levelTwo.*;
import com.avail.optimizer.L2Translator;

/**
 * {@code L2Register} models the conceptual use of a register by a {@linkplain
 * L2Operation level two Avail operation} in the {@linkplain L2Translator
 * translator}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class L2Register
{
	/**
	 * A monotonic counter for distinguishing registers when printed.
	 */
	static AtomicLong debugCounter = new AtomicLong();

	/**
	 * A coloring number to be used by the {@linkplain L2Interpreter
	 * interpreter} at runtime to identify the storage location of a
	 * {@linkplain L2Register register}.
	 */
	private int finalIndex = -1;

	/**
	 * Answer the coloring number to be used by the {@linkplain L2Interpreter
	 * interpreter} at runtime to identify the storage location of a {@linkplain
	 * L2Register register}.
	 *
	 * @return A {@linkplain L2Register register} coloring number.
	 */
	public int finalIndex ()
	{
		return finalIndex;
	}

	/**
	 * Set the coloring number to be used by the {@linkplain L2Interpreter
	 * interpreter} at runtime to identify the storage location of a {@linkplain
	 * L2Register register}.
	 *
	 * @param finalIndex
	 *        A {@linkplain L2Register register} coloring number.
	 */
	public void setFinalIndex (final int finalIndex)
	{
		assert this.finalIndex == -1
			: "Only set the finalIndex of an L2RegisterIdentity once";
		this.finalIndex = finalIndex;
	}

	/**
	 * A value used to distinguish distinct registers.
	 */
	public final long debugValue;

	/**
	 * Construct a new {@link L2Register}.
	 *
	 * @param debugValue A {@code long} used to identify this register visually.
	 */
	L2Register (final long debugValue)
	{
		this.debugValue = debugValue;
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
		builder.append(debugValue);
		return builder.toString();
	}
}
