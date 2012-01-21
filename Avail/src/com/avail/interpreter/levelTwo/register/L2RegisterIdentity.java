/**
 * L2RegisterIdentity.java
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

/**
 * {@code L2RegisterIdentity} is used by the {@linkplain L2Translator level two
 * Avail translator} to color {@linkplain L2Register registers}. It maps a
 * conceptual register, i.e. fast value storage location, to a runtime work slot
 * within an {@linkplain L2Interpreter interpreter}. Two registers having the
 * same identity are considered synonymous.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class L2RegisterIdentity
{
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

	/** The generator of {@linkplain #printId print identifiers}. */
	private static AtomicLong idGenerator = new AtomicLong(0);

	/**
	 * The lazily set print identifier that uniquely identifies a {@linkplain
	 * L2Register register} for the lifetime of the {@linkplain
	 * L2Interpreter interpreter}. This facility is provided for debugging
	 * purposes only.
	 */
	private long printId = -1;

	/**
	 * Answer the print identifier that uniquely identifies a {@linkplain
	 * L2Register register} for the lifetime of the {@linkplain L2Interpreter
	 * interpreter}.
	 *
	 * @return The print identifier.
	 */
	long printId ()
	{
		if (printId == -1)
		{
			printId = idGenerator.incrementAndGet();
		}
		return printId;
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("Id#");
		builder.append(printId());
		if (finalIndex != -1)
		{
			builder.append("[");
			builder.append(finalIndex);
			builder.append("]");
		}
		return builder.toString();
	}
}
