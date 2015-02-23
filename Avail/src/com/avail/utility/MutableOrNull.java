/**
 * MutableOrNull.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import com.avail.annotations.Nullable;

/**
 * Just like {@link Mutable}, but allows {@linkplain #value} to hold {@code
 * null}.  Also provides a default constructor.
 *
 * @author Mark van Gulik&lt;mark@availlang.org&gt;
 * @param <T> The type of mutable object.
 */
public class MutableOrNull<T>
{
	/**
	 * Expose a public field for readability.  For instance, one could declare
	 * something "final Mutable<Integer> x = new Mutable<>();" and
	 * then have code within inner classes like "x.value = 5" or "x.value++".
	 */
	public @Nullable T value;

	/**
	 * Zero-argument constructor.
	 */
	public MutableOrNull ()
	{
		super();
	}

	/**
	 * Constructor that takes an initial value.
	 *
	 * @param value The initial value.
	 */
	public MutableOrNull (@Nullable final T value)
	{
		this.value = value;
	}

	/**
	 * Return the value, asserting that it is not {@code null}.
	 *
	 * @return The non-null value.
	 */
	public T value ()
	{
		final T v = value;
		assert v != null;
		return v;
	}

	@Override
	public String toString ()
	{
		final T v = value;
		return v == null ? "null" : v.toString();
	}
}