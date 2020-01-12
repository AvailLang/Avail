/*
 * CollectionExtensions.java
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

import java.util.Collection;
import java.util.EnumMap;
import java.util.function.Function;

/**
 * Extensions for building and manipulating {@link Collection}s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public enum CollectionExtensions
{
	; // Static members only.

	/**
	 * Create a fully populated EnumMap, applying the function to each enum
	 * value.
	 *
	 * @param enumClass The enum {@link Class} for the keys.
	 * @param generator A function to map keys to values.
	 * @param <K> The key type, an {@link Enum}.
	 * @param <V> The value type produced by the function.
	 * @return A fully populated {@link EnumMap}.
	 */
	public static <K extends Enum<K>, V> EnumMap<K, V> populatedEnumMap(
		final Class<K> enumClass,
		final Function<K, V> generator)
	{
		final EnumMap<K, V> map = new EnumMap<>(enumClass);
		for (final K key : enumClass.getEnumConstants())
		{
			map.put(key, generator.apply(key));
		}
		return map;
	}
}
