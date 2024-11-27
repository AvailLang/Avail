/*
 * ResourceAccess.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.tools.bootstrap

import java.util.Locale
import java.util.ResourceBundle

/**
 * An abstraction for restricting the modes of access to a [ResourceBundle].
 *
 * @param K
 *   The type of key suitable for use with this instance.
 * @property bundle
 *   The [ResourceBundle] being wrapped.  This can be constructed automatically
 *   by the secondary constructor.
 * @param extractKey
 *   The function to convert a key ([K]) to a [String] that may appear as a
 *   property key in the underlying [ResourceBundle]'s file.
 *
 * @constructor
 * @param bundle
 *   The [ResourceBundle] being wrapped.
 * @param extractKey
 *   How to convert a [K] to a [String] for accessing the [ResourceBundle].
 */
internal abstract class ResourceAccess<K>
constructor(
	val bundle: ResourceBundle,
	val extractKey: (K)->String)
{
	/**
	 * A secondary constructor.
	 *
	 * @param baseName
	 *   The name passed to [ResourceBundle.getBundle].
	 * @param locale
	 *   The [Locale] passed to [ResourceBundle.getBundle].
	 * @param extractKey
	 *   A function to transform a key of type [K] to a String that the
	 *   [ResourceBundle] can look up.
	 */
	constructor(
		baseName: String,
		locale: Locale,
		extractKey: (K)->String
	) : this(
		ResourceBundle.getBundle(
			baseName,
			locale,
			BootstrapGenerator::class.java.classLoader,
			BootstrapGenerator.control),
		extractKey)

	/**
	 * Check whether the string associated with this [key] in the [bundle] is
	 * present and non-empty.
	 *
	 * @param key
	 *   The key to look up in the [bundle].
	 * @return
	 *   Whether the key is present in the [bundle] and non-empty.
	 */
	operator fun contains(key: K): Boolean
	{
		val keyName = extractKey(key)
		return bundle.containsKey(keyName) &&
			bundle.getString(keyName).isNotEmpty()
	}

	/**
	 * Look up the [key] in the [bundle], failing if absent.  It may be empty.
	 *
	 * @param key
	 *   The key to look up in the [bundle].
	 * @return
	 *   The looked-up [String], which may be empty.
	 */
	internal operator fun get(key: K): String =
		bundle.getString(extractKey(key))

	/**
	 * Look up the [key] in the [bundle], invoking [otherwise] if the key is not
	 * present or the value is empty.
	 *
	 * @param key
	 *   The key to look up in the [bundle].
	 * @param otherwise
	 *   A function to invoke if the [key] is absent or its value is empty. Note
	 *   that this method is inline, so a literal block is allowed to effect
	 *   non-local control flow.
	 * @return
	 *   Either the looked-up non-empty [String], or the result of running the
	 *   [otherwise] function.
	 */
	internal inline fun getOr(key: K, otherwise: ()->String): String
	{
		val keyName = extractKey(key)
		if (bundle.containsKey(keyName))
		{
			return bundle.getString(keyName).ifEmpty(otherwise)
		}
		else
		{
			return otherwise()
		}
	}
}
