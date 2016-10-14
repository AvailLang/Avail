/**
 * JSONObject.java
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

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A {@code JSONObject} is produced by a {@link JSONReader} when an object is
 * read. Each key of the object is a {@link String} and each value is a {@link
 * JSONData}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class JSONObject
extends JSONData
implements Iterable<Map.Entry<String, JSONData>>
{
	/**
	 * The field assignments of the {@link JSONObject} as a {@linkplain Map map}
	 * from {@linkplain String key}s to {@linkplain JSONData value}s.
	 */
	private final Map<String, JSONData> map;

	/**
	 * Construct a new {@link JSONObject}.
	 *
	 * @param map
	 *        The field assignments of the {@link JSONObject} as a {@linkplain
	 *        Map map} from {@linkplain String key}s to {@linkplain JSONData
	 *        value}s. This must not be modified by the caller afterward; this
	 *        call transfers ownership of the reference.
	 */
	JSONObject (final Map<String, JSONData> map)
	{
		this.map = map;
	}

	/** The sole empty {@code JSONObject}. */
	private static final JSONObject empty =
		new JSONObject(Collections.<String, JSONData>emptyMap());

	/**
	 * Answer the sole empty {@link JSONObject}.
	 *
	 * @return An empty {@code JSONObject}.
	 */
	public static final JSONObject empty ()
	{
		return empty;
	}

	@Override
	public boolean isObject ()
	{
		return true;
	}

	/**
	 * Does the {@link JSONObject} include a binding for the specified key?
	 *
	 * @param k
	 *        The key.
	 * @return {@code true} if the {@code JSONObject} includes a binding for
	 *         the key, {@code false} otherwise.
	 */
	public boolean containsKey (final String k)
	{
		return map.containsKey(k);
	}

	/**
	 * Get a {@link JSONData} associated with requested key.
	 *
	 * @param k
	 *        The key.
	 * @return The {@code JSONData} associated with requested key.
	 * @throws NullPointerException
	 *         If the requested key is not present.
	 */
	public JSONData get (final String k)
	{
		final JSONData v = map.get(k);
		if (v == null)
		{
			throw new NullPointerException();
		}
		return v;
	}

	/**
	 * Get a {@code boolean} associated with requested key.
	 *
	 * @param k
	 *        The key.
	 * @return The {@code boolean} associated with requested key.
	 * @throws NullPointerException
	 *         If the requested key is not present.
	 * @throws ClassCastException
	 *         If the value associated with the requested key is not a {@link
	 *         JSONValue}.
	 */
	public boolean getBoolean (final String k)
		throws NullPointerException, ClassCastException
	{
		return ((JSONValue) get(k)).getBoolean();
	}

	/**
	 * Get a {@link JSONNumber} associated with requested key.
	 *
	 * @param k
	 *        The key.
	 * @return The {@code JSONNumber} associated with requested key.
	 * @throws NullPointerException
	 *         If the requested key is not present.
	 * @throws ClassCastException
	 *         If the value associated with the requested key is not a {@code
	 *         JSONNumber}.
	 */
	public JSONNumber getNumber (final String k)
		throws NullPointerException, ClassCastException
	{
		return (JSONNumber) get(k);
	}

	/**
	 * Get a {@link String} associated with requested key.
	 *
	 * @param k
	 *        The key.
	 * @return The {@code String} associated with requested key.
	 * @throws NullPointerException
	 *         If the requested key is not present.
	 * @throws ClassCastException
	 *         If the value associated with the requested key is not a {@link
	 *         JSONValue}.
	 */
	public String getString (final String k)
		throws NullPointerException, ClassCastException
	{
		return ((JSONValue) get(k)).getString();
	}

	/**
	 * Get a {@link JSONArray} associated with requested key.
	 *
	 * @param k
	 *        The key.
	 * @return The {@code JSONArray} associated with requested key.
	 * @throws NullPointerException
	 *         If the requested key is not present.
	 * @throws ClassCastException
	 *         If the value associated with the requested key is not a {@code
	 *         JSONArray}.
	 */
	public JSONArray getArray (final String k)
		throws NullPointerException, ClassCastException
	{
		return (JSONArray) get(k);
	}

	/**
	 * Get a {@link JSONObject} associated with requested key.
	 *
	 * @param k
	 *        The key.
	 * @return The {@code JSONObject} associated with requested key.
	 * @throws NullPointerException
	 *         If the requested key is not present.
	 * @throws ClassCastException
	 *         If the value associated with the requested key is not a {@code
	 *         JSONObject}.
	 */
	public JSONObject getObject (final String k)
		throws NullPointerException, ClassCastException
	{
		return (JSONObject) get(k);
	}

	@Override
	public Iterator<Entry<String, JSONData>> iterator ()
	{
		return map.entrySet().iterator();
	}

	@Override
	public void writeTo (final JSONWriter writer)
	{
		writer.startObject();
		for (final Map.Entry<String, JSONData> entry : map.entrySet())
		{
			writer.write(entry.getKey());
			entry.getValue().writeTo(writer);
		}
		writer.endObject();
	}
}
