/*
 * Casts.java
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
 * Static utility methods related to Java type-casting.
 */
public final class Casts
{
	/** Prevent instantiation. */
	private Casts () {}

	/**
	 * When you know better, this bypasses static type-safety, while leaving
	 * dynamic type-safety intact, other than generics and nulls.
	 *
	 * @param value The value to cast, of type {@code I}.
	 * @return The argument, strengthened to the indicated type {@code O}.
	 * @param <I> The input type.
	 * @param <O> The output type.
	 */
	@SuppressWarnings("unchecked")
	public static <I, O extends I> O cast (final I value)
	{
		return (O) value;
	}

	/**
	 * When you know better, this bypasses static type-safety, while leaving
	 * dynamic type-safety intact, other than generics and nulls.
	 *
	 * @param value The nullable value to cast, of type {@code I}.
	 * @return The argument, strengthened to the indicated type {@code O}.
	 * @param <I> The input type.
	 * @param <O> The output type.
	 */
	@SuppressWarnings("unchecked")
	public static @Nullable <I, O extends I> O nullableCast (
		final @Nullable I value)
	{
		return (O) value;
	}
}
