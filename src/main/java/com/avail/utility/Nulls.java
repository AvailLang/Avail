/*
 * Nulls.java
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

package com.avail.utility;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Static utility methods related to null handling.
 */
public final class Nulls
{
	/** Prevent instantiation. */
	private Nulls () {}

	/**
	 * Strip the nullness from the value. If the value is null, throw an {@link
	 * AssertionError}.
	 *
	 * @param x
	 *        The value to strip the {@code nullness} from.
	 * @param <X>
	 *        The type of the input value.
	 * @return The value.
	 */
	public static @Nonnull <X> X stripNull (final @Nullable X x)
	{
		assert x != null;
		return x;
	}

	/**
	 * Strip the nullness from the value.  If the value is null, throw an {@link
	 * AssertionError}.
	 *
	 * @param x
	 *        The value to strip the {@code nullness} from.
	 * @param failMessage
	 *        A {@code String} message to be associated with the assertion
	 *        failure if the value is {@code null}.
	 * @param <X>
	 *        The type of the input value.
	 * @return The value.
	 */
	public static @Nonnull <X> X stripNull (
		final @Nullable X x,
		final @Nonnull String failMessage)
	{
		assert x != null : failMessage;
		return x;
	}

	/**
	 * Strip the nullness from the value.  If the value is null, throw an {@link
	 * AssertionError}.
	 *
	 * @param x
	 *        The value to strip the {@code nullness} from.
	 * @param throwableSupplier
	 *        A {@link Supplier} that provides a {@link Throwable} to be thrown
	 *        if the value is {@code null}.
	 * @param <X>
	 *        The type of the input value.
	 * @param <T>
	 *        The type of {@link Throwable} to be thrown if the value is null.
	 * @return The value.
	 * @throws T The subtype of {@link Throwable} to throw.
	 */
	public static @Nonnull <X, T extends Throwable> X stripNull (
		final @Nullable X x,
		final @Nonnull Supplier<T> throwableSupplier)
	throws T
	{
		if (x == null)
		{
			throw throwableSupplier.get();
		}
		return x;
	}
}
