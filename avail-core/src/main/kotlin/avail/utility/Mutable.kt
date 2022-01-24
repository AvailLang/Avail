/*
 * Mutable.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package avail.utility

/**
 * Support explicit mutable wrapping of variables. This is used specifically for
 * allowing non-final variables to be used by inner classes. The uses were
 * generated automatically by the Smalltalk → Java translator after flow
 * analysis.
 *
 * @param T
 *   The type of mutable object.
 * @property value
 *   Expose a public field for readability.
 * @author Mark van Gulik <mark@availlang.org>
 *
 * @constructor
 *
 * Constructor that takes an initial value.
 *
 * @param value
 *   The initial value.
 */
class Mutable<T> constructor (var value: T)
{
	override fun toString (): String = value?.toString() ?: "null"

	/**
	 * Update the [Mutable] via an extension function.  Within the function, the
	 * receiver will be the old value of the Mutable, and the function must
	 * return a replacement value.
	 *
	 * @param updater
	 *   An extension function taking a receiver T, the old value, and
	 *   returning the replacement value.
	 */
	inline fun update(updater: T.()->T)
	{
		value = value.updater()
	}
}
