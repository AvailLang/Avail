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
 * @constructor
 *   Build an instance using an initial [Int] value, perhaps constructed via the
 *   [mask] class.
 * @param setupFlags
 *   A lambda that can set up initial flag values using [MaskBuilder]'s function
 *   invocation syntax (`MyFlag(aBoolean)`).
 */
abstract class PackedBits<F>
constructor(
	initialFlags: Int = 0,
	setupFlags: MaskBuilder<F>.()->Unit
)
where
	F: BitFlag<F>,
	F: Enum<F>
{
	constructor(
		setupFlags: MaskBuilder<F>.()->Unit
	): this(0, setupFlags)

	var packedBits: Int = run {
		val builder = MaskBuilder<F>(initialFlags)
		builder.setupFlags()
		builder.value
	}
	private set

	operator fun get(flag: F): Boolean =
		packedBits and flag.mask != 0

	operator fun set(flag: F, value: Boolean) {
		packedBits = when (value)
		{
			true -> packedBits or flag.mask
			else -> packedBits and flag.mask.inv()
		}
	}
}
