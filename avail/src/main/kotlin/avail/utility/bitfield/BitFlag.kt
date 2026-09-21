/*
 * BitFlag.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.utility.bitfield

import avail.utility.cast
import kotlin.reflect.KProperty

/**
 * An enum class `X` can extend [BitFlag]&#91;X&#93;.  Another class `C` can
 * extend [PackedBits]<`X`> to inherit an [Int] property in which bits can be
 * made available as var or val fields.  If X has an enum entry Foo, `C` can
 * define:
 *
 *    var foo by X.Foo
 *
 * which will access a bit indexed by X.Foo's ordinal (must be ≤ 31) within the
 * [Int] property [PackedBits.packedBits] that `C` inherits.
 */
interface BitFlag<F>
where
	F: BitFlag<F>,
	F: Enum<F>
{
	operator fun getValue(
		thisRef: PackedBits<F>,
		property: KProperty<*>
	): Boolean = thisRef[this.cast()]

	operator fun setValue(
		thisRef: PackedBits<F>,
		property: KProperty<*>,
		value: Boolean)
	{
		thisRef[this.cast()] = value
	}
}
