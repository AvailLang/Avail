/*
 * Pair.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.utility;

import javax.annotation.Nullable;

/**
 * An immutable pair of {@linkplain Object objects}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @param <FirstType> The {@linkplain Class type} of the first element.
 * @param <SecondType> The {@linkplain Class type} of the second element.
 */
public class Pair<FirstType, SecondType>
{
	/** The first element of the {@linkplain Pair pair}. */
	private final FirstType first;

	/**
	 * Answer the first element of the {@linkplain Pair pair}.
	 *
	 * @return The first element of the {@linkplain Pair pair}.
	 */
	public final FirstType first ()
	{
		return first;
	}

	/** The second element of the {@linkplain Pair pair}. */
	private final SecondType second;

	/**
	 * Answer the second element of the {@linkplain Pair pair}.
	 *
	 * @return The second element of the {@linkplain Pair pair}.
	 */
	public final SecondType second ()
	{
		return second;
	}

	/**
	 * Construct a new <code>{@link Pair}</code>.
	 *
	 * @param first The first element.
	 * @param second The second element.
	 */
	public Pair (
		final FirstType first,
		final SecondType second)
	{
		this.first = first;
		this.second = second;
	}

	@Override
	public boolean equals (final @Nullable Object obj)
	{
		if (obj != null && this.getClass() == obj.getClass())
		{
			final Pair<?, ?> otherPair = (Pair<?, ?>) obj;
			return first.equals(otherPair.first)
				&& second.equals(otherPair.second);
		}

		return false;
	}

	@Override
	public int hashCode ()
	{
		return first.hashCode() ^ (second.hashCode() * 395826401);
	}

	@Override
	public String toString ()
	{
		return first + "," + second;
	}

	/**
	 * Compatibility for deconstruction in Kotlin.
	 * @return The {@link #first} component.
	 */
	public FirstType component1 ()
	{
		return first;
	}

	/**
	 * Compatibility for deconstruction in Kotlin.
	 * @return The {@link #second} component.
	 */
	public SecondType component2 ()
	{
		return second;
	}
}
