/*
 * PackedBits.kt
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

/**
 * This is an inheritable mechanism to use a [BitFlag] enum class's ordinals to
 * index bits stored in a private, inherited field ([packedBits]).
 *
 * It's parameterized by the [BitFlag] enum class that provides elements with
 * ordinals corresponding to the bit positions.
 *
 * Say there's a BitFlag subclass `X` with `Foo` and `Bar` as two of the enum
 * elements.  Then a class inheriting from [PackedBits]<[F]> can introduce the
 * properties:
 *
 *    var foo by X.Foo
 *    var bar by X.Bar
 *
 * These [Boolean] property will store/retrive their state based on a bit in the
 * [Int] stored in [packedBits], indexed by `X`.`Foo`'s ordinal and `X`.`Bar`'s
 * ordinal, respectively.
 *
 * This mechanism currently supports up to 32 enumeration values.
 *
 * The constructor, which must be called by subclasses, supports a vararg of
 * enumeration values from the [BitFlag] type [F], which is especially
 * convenient for initializing val properties.  For even more convenience, some
 * entries of the vararg array can be `null`, which are ignored.
 */
abstract class PackedBits<F>
constructor(
	vararg initiallySet: F?)
where
	F: BitFlag<F>,
	F: Enum<F>
{
	private var packedBits: Int =
		initiallySet.filterNotNull().sumOf { 1 shl it.ordinal }

	operator fun get(flag: F): Boolean =
		packedBits and (1 shl flag.ordinal) != 0

	operator fun set(flag: F, value: Boolean) {
		val mask = 1 shl flag.ordinal
		packedBits = when (value)
		{
			true -> packedBits or mask
			else -> packedBits and mask.inv()
		}
	}
}
