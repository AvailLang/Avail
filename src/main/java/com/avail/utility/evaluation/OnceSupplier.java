/*
 * OnceSupplier.java
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

package com.avail.utility.evaluation;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Converts any {@link Supplier} into a supplier that caches its value upon
 * first evaluation, returning the same value thereafter.  The supplier must not
 * produce {@code null}.
 *
 * @param <T> The type of value being supplied.  Must not be {@link Nullable}.
 */
public final class OnceSupplier<T> implements Supplier<T>
{
	/**
	 * The cached value produced by the {@link #innerSupplier}, or {@code null}
	 * if it has not yet been evaluated.
	 */
	private @Nullable volatile T cachedValue;

	/**
	 * The {@link Supplier} that should be evaluated at most once.
	 */
	private final Supplier<T> innerSupplier;

	/**
	 * Create a {@code OnceSupplier} from the given {@link Supplier}.
	 *
	 * @param innerSupplier
	 *        The {@link Supplier} to be evaluated at most once.
	 */
	public OnceSupplier (final Supplier<T> innerSupplier)
	{
		this.innerSupplier = innerSupplier;
	}

	@Override
	public T get ()
	{
		// A double-check pattern is safe here, since it's on a volatile field.
		@Nullable T value = cachedValue;
		if (value == null)
		{
			synchronized (this)
			{
				value = cachedValue;
				if (value == null)
				{
					value = innerSupplier.get();
					cachedValue = value;
				}
			}
		}
		return value;
	}
}
