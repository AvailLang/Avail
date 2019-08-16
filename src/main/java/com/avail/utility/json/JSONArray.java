/*
 * JSONArray.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import java.util.Arrays;
import java.util.ListIterator;

/**
 * A {@code JSONArray} is produced by a {@link JSONReader} when an array is
 * read. Each element is a {@link JSONData}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class JSONArray
extends JSONData
implements Iterable<JSONData>
{
	/** The array of {@link JSONData}. */
	private final JSONData[] array;

	/**
	 * Construct a new {@link JSONArray}.
	 *
	 * @param array
	 *        The array of {@link JSONData}s. This must not be modified by the
	 *        caller afterward; this call transfers ownership of the reference.
	 */
	JSONArray (final JSONData[] array)
	{
		this.array = array;
	}

	/** The canonical {@linkplain #empty() emptySet} {@link JSONArray}. */
	private static final JSONArray empty = new JSONArray(new JSONData[0]);

	/**
	 * Answer an emptySet {@link JSONArray}.
	 *
	 * @return The {@code JSONArray}.
	 */
	static JSONArray empty ()
	{
		return empty;
	}

	/**
	 * Answer a singleton {@link JSONArray}.
	 *
	 * @param value
	 *        The sole {@linkplain JSONData element} of the {@code JSONArray}.
	 * @return The {@code JSONArray}.
	 */
	static JSONArray singleton (final JSONData value)
	{
		final JSONData[] array = new JSONData[1];
		array[0] = value;
		return new JSONArray(array);
	}

	@Override
	public boolean isArray ()
	{
		return true;
	}

	/**
	 * Answer the length of the {@linkplain JSONArray receiver}.
	 *
	 * @return The length of the receiver.
	 */
	public int size ()
	{
		return array.length;
	}

	/**
	 * Get a {@link JSONData} at the requested subscript.
	 *
	 * @param index
	 *        The array subscript.
	 * @return The {@code JSONData} at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *         If the subscript is out of bounds.
	 */
	public JSONData get (final int index) throws ArrayIndexOutOfBoundsException
	{
		return array[index];
	}

	/**
	 * Get a {@code boolean} at the requested subscript.
	 *
	 * @param index
	 *        The array subscript.
	 * @return The {@code boolean} at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *         If the subscript is out of bounds.
	 * @throws ClassCastException
	 *         If the element at the requested subscript is not a {@link
	 *         JSONValue}.
	 */
	public boolean getBoolean (final int index)
		throws ArrayIndexOutOfBoundsException, ClassCastException
	{
		return ((JSONValue) array[index]).getBoolean();
	}

	/**
	 * Get a {@link JSONNumber} at the requested subscript.
	 *
	 * @param index
	 *        The array subscript.
	 * @return The {@code JSONNumber} at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *         If the subscript is out of bounds.
	 * @throws ClassCastException
	 *         If the element at the requested subscript is not a {@code
	 *         JSONNumber}.
	 */
	public JSONNumber getNumber (final int index)
		throws ArrayIndexOutOfBoundsException, ClassCastException
	{
		return (JSONNumber) array[index];
	}

	/**
	 * Get a {@link String} at the requested subscript.
	 *
	 * @param index
	 *        The array subscript.
	 * @return The {@code String} at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *         If the subscript is out of bounds.
	 * @throws ClassCastException
	 *         If the element at the requested subscript is not a {@link
	 *         JSONValue}.
	 */
	public String getString (final int index)
		throws ArrayIndexOutOfBoundsException, ClassCastException
	{
		return ((JSONValue) array[index]).getString();
	}

	/**
	 * Get a {@link JSONArray} at the requested subscript.
	 *
	 * @param index
	 *        The array subscript.
	 * @return The {@code JSONArray} at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *         If the subscript is out of bounds.
	 * @throws ClassCastException
	 *         If the element at the requested subscript is not a {@code
	 *         JSONArray}.
	 */
	public JSONArray getArray (final int index)
		throws ArrayIndexOutOfBoundsException, ClassCastException
	{
		return (JSONArray) array[index];
	}

	/**
	 * Get a {@link JSONObject} at the requested subscript.
	 *
	 * @param index
	 *        The array subscript.
	 * @return The {@code JSONObject} at the requested subscript.
	 * @throws ArrayIndexOutOfBoundsException
	 *         If the subscript is out of bounds.
	 * @throws ClassCastException
	 *         If the element at the requested subscript is not a {@code
	 *         JSONObject}.
	 */
	public JSONObject getObject (final int index)
		throws ArrayIndexOutOfBoundsException, ClassCastException
	{
		return (JSONObject) array[index];
	}

	@Override
	public ListIterator<JSONData> iterator ()
	{
		return Arrays.asList(array).listIterator();
	}

	@Override
	public void writeTo (final JSONWriter writer)
	{
		writer.startArray();
		for (final JSONData value : array)
		{
			value.writeTo(writer);
		}
		writer.endArray();
	}
}
