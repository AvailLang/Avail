/*
 * JSONNumber.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.utility.json

import java.math.BigDecimal
import java.math.BigInteger

/**
 * A `JSONNumber` is a JSON number. It provides convenience methods for
 * extracting numeric values in different formats.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
class JSONNumber : JSONData
{
	/** The [BigDecimal] that encodes the value.  */
	@Suppress("MemberVisibilityCanBePrivate")
	val bigDecimal: BigDecimal

	override val isNumber: Boolean
		get() = true

	/**
	 * Get the numeric value as a [BigInteger].
	 *
	 * @return
	 *   A `BigInteger`.
	 * @throws ArithmeticException
	 *   If the fractional part of the value is nonzero.
	 */
	val bigInteger: BigInteger
		@Throws(ArithmeticException::class)
		get() = bigDecimal.toBigIntegerExact()

	/**
	 * Extract an [Int].
	 *
	 * @return
	 *   A [Int].
	 * @throws ArithmeticException
	 *   If the fractional part of the value is nonzero.
	 */
	val int: Int
		@Throws(ArithmeticException::class)
		get() = bigDecimal.intValueExact()

	/**
	 * Extract a `Long`.
	 *
	 * @return
	 *   A `Long`.
	 * @throws ArithmeticException
	 *   If the fractional part of the value is nonzero.
	 */
	val long: Long
		@Throws(ArithmeticException::class)
		get() = bigDecimal.longValueExact()

	/**
	 * Extract a `Float`.
	 *
	 * @return
	 *   A `Float`. This may be [Float.POSITIVE_INFINITY] or
	 *   [Float.NEGATIVE_INFINITY] if the internal value exceeds the
	 *   representational limitations of `Float`.
	 */
	val float: Float
		get() = bigDecimal.toFloat()

	/**
	 * Extract a `Double`.
	 *
	 * @return
	 *   A `Float`. This may be [Double.POSITIVE_INFINITY] or
	 *   [Double.NEGATIVE_INFINITY] if the internal value exceeds the
	 *   representational limitations of `Float`.
	 */
	val double: Double
		get() = bigDecimal.toDouble()

	/**
	 * Construct a new [JSONNumber].
	 *
	 * @param value
	 *   The [BigDecimal] that encodes the value.
	 */
	constructor(value: BigDecimal)
	{
		this.bigDecimal = value
	}

	/**
	 * Construct a new [JSONNumber].
	 *
	 * @param value
	 *   The `Long` that encodes the value.
	 */
	constructor(value: Long)
	{
		this.bigDecimal = BigDecimal(value)
	}

	/**
	 * Construct a new [JSONNumber].
	 *
	 * @param value
	 *   The [Int] that encodes the value.
	 */
	constructor(value: Int)
	{
		this.bigDecimal = BigDecimal(value)
	}

	/**
	 * Construct a new [JSONNumber].
	 *
	 * @param value
	 *   The `Double` that encodes the value.
	 */
	constructor(value: Double)
	{
		this.bigDecimal = BigDecimal(value)
	}

	/**
	 * Construct a new [JSONNumber].
	 *
	 * @param value
	 *   The `Float` that encodes the value.
	 */
	constructor(value: Float)
	{
		this.bigDecimal = BigDecimal(value.toDouble())
	}

	override fun writeTo(writer: JSONWriter)
	{
		writer.write(bigDecimal)
	}
}
