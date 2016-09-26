/**
 * JSONValue.java
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

/**
 * A {@code JSONValue} is an arbitrary JSON value. {@code JSONValue}s are
 * produced by a {@link JSONReader}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class JSONValue
extends JSONData
{
	/** The {@link Object}. */
	private final Object value;

	/**
	 * Construct a new {@link JSONValue}.
	 *
	 * @param value
	 *        The value.
	 */
	public JSONValue (final boolean value)
	{
		this.value = value;
	}

	/**
	 * Construct a new {@link JSONValue}.
	 *
	 * @param value
	 *        The value.
	 */
	public JSONValue (final String value)
	{
		this.value = value;
	}

	/** The sole JSON {@code false}. */
	private static final JSONValue jsonFalse = new JSONValue(false);

	/**
	 * Answer a JSON {@code false}.
	 *
	 * @return A JSON {@code false}.
	 */
	public static final JSONValue jsonFalse ()
	{
		return jsonFalse;
	}

	/** The sole JSON {@code true}. */
	private static final JSONValue jsonTrue = new JSONValue(true);

	/**
	 * Answer a JSON {@code true}.
	 *
	 * @return A JSON {@code true}.
	 */
	public static final JSONValue jsonTrue ()
	{
		return jsonTrue;
	}

	@Override
	public boolean isBoolean ()
	{
		return value instanceof Boolean;
	}

	/**
	 * Extract a {@code boolean}.
	 *
	 * @return A {@code boolean}.
	 * @throws ClassCastException
	 *         If the value is not a {@code boolean}.
	 */
	public boolean getBoolean () throws ClassCastException
	{
		return (boolean) value;
	}

	@Override
	public boolean isString ()
	{
		return value instanceof String;
	}

	/**
	 * Extract a {@link String}.
	 *
	 * @return A {@code String}.
	 * @throws ClassCastException
	 *         If the value is not a {@code String}.
	 */
	public String getString () throws ClassCastException
	{
		return (String) value;
	}

	@Override
	public void writeTo (final JSONWriter writer)
	{
		final Class<?> valueClass = value.getClass();
		if (valueClass == Boolean.class)
		{
			writer.write(getBoolean());
		}
		else
		{
			assert valueClass == String.class;
			writer.write(getString());
		}
	}
}
