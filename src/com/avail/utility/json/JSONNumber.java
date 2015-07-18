/**
 * JSONNumber.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.utility.json;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * A {@code JSONNumber} is a JSON number. It provides convenience methods for
 * extracting numeric values in different formats.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class JSONNumber
extends JSONData
{
	/** The {@link BigDecimal} that encodes the value. */
	private final BigDecimal value;

	/**
	 * Construct a new {@link JSONNumber}.
	 *
	 * @param value
	 *        The {@link BigDecimal} that encodes the value.
	 */
	JSONNumber (final BigDecimal value)
	{
		this.value = value;
	}

	@Override
	public boolean isNumber ()
	{
		return true;
	}

	/**
	 * Get the numeric value as a {@link BigDecimal}.
	 *
	 * @return A {@code BigDecimal}.
	 */
	public BigDecimal getBigDecimal ()
	{
		return value;
	}

	/**
	 * Get the numeric value as a {@link BigInteger}.
	 *
	 * @return A {@code BigInteger}.
	 * @throws ArithmeticException
	 *         If the fractional part of the value is nonzero.
	 */
	public BigInteger getBigInteger () throws ArithmeticException
	{
		return value.toBigIntegerExact();
	}

	/**
	 * Extract an {@code int}.
	 *
	 * @return A {@code int}.
	 * @throws ArithmeticException
	 *         If the fractional part of the value is nonzero.
	 */
	public int getInt () throws ArithmeticException
	{
		return value.intValueExact();
	}

	/**
	 * Extract a {@code long}.
	 *
	 * @return A {@code long}.
	 * @throws ArithmeticException
	 *         If the fractional part of the value is nonzero.
	 */
	public long getLong () throws ArithmeticException
	{
		return value.longValueExact();
	}

	/**
	 * Extract a {@code float}.
	 *
	 * @return A {@code float}. This may be {@link Float#POSITIVE_INFINITY} or
	 *         {@link Float#NEGATIVE_INFINITY} if the internal value exceeds the
	 *         representational limitations of {@code float}.
	 */
	public float getFloat ()
	{
		return value.floatValue();
	}

	/**
	 * Extract a {@code double}.
	 *
	 * @return A {@code float}. This may be {@link Double#POSITIVE_INFINITY} or
	 *         {@link Double#NEGATIVE_INFINITY} if the internal value exceeds
	 *         the representational limitations of {@code float}.
	 */
	public double getDouble ()
	{
		return value.doubleValue();
	}

	@Override
	public void writeTo (final JSONWriter writer)
	{
		writer.write(value);
	}
}
