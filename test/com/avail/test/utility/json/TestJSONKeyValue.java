/*
 * TestJSONKeyValue.java
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

package com.avail.test.utility.json;
import com.avail.utility.json.JSONArray;
import com.avail.utility.json.JSONData;
import com.avail.utility.json.JSONNumber;
import com.avail.utility.json.JSONObject;
import com.avail.utility.json.JSONWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A {@code TestJSONKeyValue} is an enum used for creating static test data for
 * testing JSON utility functionality in Avail.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public enum TestJSONKeyValue
{
	/** For testing a String */
	IMASTRING("imastring")
	{
		/** The test value */
		private static final String value = "foo";

		@Override
		void addValueToBuilder (final StringBuilder sb)
		{
			sb.append('"').append(value).append('"');
		}

		@Override
		void equalityCheck (final JSONObject content)
		{
			assertEquals(value, content.getString(key));
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.write(value);
		}
	},

	/** For testing an integer */
	IMANINT("imanint")
	{
		/** The test value */
		private static final int value = 52;

		@Override
		void addValueToBuilder (final StringBuilder sb) { sb.append(value); }

		@Override
		void equalityCheck (final JSONObject content)
		{
			assertEquals(value, content.getNumber(key).getInt());
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.write(value);
		}
	},

	/** For testing a long */
	IMALONG("imalong")
	{
		/** The test value */
		private static final long value = 34359738368L;

		@Override
		void addValueToBuilder (final StringBuilder sb) { sb.append(value); }

		@Override
		void equalityCheck (final JSONObject content)
		{
			assertEquals(value, content.getNumber(key).getLong());
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.write(value);
		}
	},

	/** For testing a float */
	IMAFLOAT("imafloat")
	{
		/** The test value */
		private static final float value = 3.2f;

		@Override
		void addValueToBuilder (final StringBuilder sb) { sb.append(value); }

		@Override
		void equalityCheck (final JSONObject content)
		{
			assertEquals(value, content.getNumber(key).getFloat());
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.write(value);
		}
	},

	/** For testing a double */
	IMADOUBLE("imadouble")
	{
		/** The test value */
		private static final double value = 3.12345678910d;

		@Override
		void addValueToBuilder (final StringBuilder sb) { sb.append(value); }

		@Override
		void equalityCheck (final JSONObject content)
		{
			assertEquals(value, content.getNumber(key).getDouble());
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.write(value);
		}
	},

	/** For testing a boolean that evaluates to true */
	IMATRUE("imatrue")
	{
		/** The test value */
		private static final boolean value = true;

		@Override
		void addValueToBuilder (final StringBuilder sb) { sb.append(value); }

		@Override
		void equalityCheck (final JSONObject content)
		{
			assertEquals(value, content.getBoolean(key));
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.write(value);
		}
	},

	/** For testing a boolean that evaluates to false */
	IMAFALSE("imafalse")
	{
		/** The test value */
		private static final boolean value = false;

		@Override
		void addValueToBuilder (final StringBuilder sb) { sb.append(value); }

		@Override
		void equalityCheck (final JSONObject content)
		{
			assertEquals(value, content.getBoolean(key));
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.write(value);
		}
	},

	/** For testing a boolean that evaluates to false */
	IMANULL("imanull")
	{
		@Override
		void addValueToBuilder (final StringBuilder sb) { sb.append("null"); }

		@Override
		void equalityCheck (final JSONObject content)
		{
			final JSONData contentValue = content.get(key);
			assertTrue(contentValue.isNull(),
				"Expected a null, but received " + contentValue);
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.writeNull();
		}
	},

	/** For testing an integer array with no spaces */
	IMACOMPACTARRAY("imacompactarrayKey")
	{
		/** The test value */
		private final int[] value = {1,2,3,4,5};

		@Override
		void addValueToBuilder (final StringBuilder sb)
		{
			sb.append('[');
			for (int i = 0; i < value.length - 1; i++)
			{
				sb.append(value[i]).append(',');
			}
			sb.append(value[value.length - 1]).append(']');
		}

		@Override
		void equalityCheck (final JSONObject content)
		{
			final JSONArray jsonArray = content.getArray(key);
			final int[] array = new int[jsonArray.size()];
			for (int i = 0; i < jsonArray.size(); i++)
			{
				array[i] = ((JSONNumber) jsonArray.get(i)).getInt();
			}
			assertArrayEquals(value, array);
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.startArray();
			for (final int v : value)
			{
				writer.write(v);
			}
			writer.endArray();
		}
	},

	/** For testing an array with superfluous spaces */
	IMANARRAY("imanarray")
	{
		/** The test value */
		private final int[] value = {1,2,3,4,5};

		@Override
		void addValueToBuilder (final StringBuilder sb)
		{
			sb.append("[\n");
			for (int i = 0; i < value.length - 1; i++)
			{
				sb.append('\t').append(value[i]).append(", \n");
			}
			sb.append('\t').append(value[value.length - 1]).append("\n]");
		}

		@Override
		void equalityCheck (final JSONObject content)
		{
			final JSONArray jsonArray = content.getArray(key);
			final int[] array = new int[jsonArray.size()];
			for (int i = 0; i < jsonArray.size(); i++)
			{
				array[i] = ((JSONNumber) jsonArray.get(i)).getInt();
			}
			assertArrayEquals(value, array);
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.startArray();
			for (final int v : value)
			{
				writer.write(v);
			}
			writer.endArray();
		}
	},

	/** For testing a String in a JSON object. */
	OBJSTRING("objstring")
	{
		/** The test value */
		private static final String value = "bar";

		@Override
		void addValueToBuilder (final StringBuilder sb)
		{
			sb.append('"').append(value).append('"');
		}

		@Override
		void equalityCheck (final JSONObject content)
		{
			assertEquals(value, content.getString(key));
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.write(value);
		}
	},

	/** For testing an integer in a JSON object. */
	OBJINT("objint")
	{
		/** The test value */
		private static final int value = 10;

		@Override
		void addValueToBuilder (final StringBuilder sb) { sb.append(value); }

		@Override
		void equalityCheck (final JSONObject content)
		{
			assertEquals(value, content.getNumber(key).getInt());
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			writer.write(key);
			writer.write(value);
		}
	},

	/** For testing a JSON object. */
	IMANOBJECT("imanobject")
	{
		@Override
		void addValueToBuilder (final StringBuilder sb)
		{
			// Do nothing as this should be done via the sub fields.
		}

		@Override
		void equalityCheck (final JSONObject content)
		{
			// Do nothing as this should be checked via the sub fields.
		}

		@Override
		void addValueToWriter (final JSONWriter writer)
		{
			throw new UnsupportedOperationException(
				"TestJSONKeyValue.IMANOBJECT must be built manually. Try"
					+ " something like: \n\t addObjectToWriter(IMANOBJECT.key, "
					+ "writer, OBJSTRING, OBJINT);\n");
		}
	};

	/** The JSON pair key */
	public final String key;

	/**
	 * Add the {@code TestJSONKeyValue}'s value to the {@link StringBuilder}.
	 *
	 * @param sb
	 *        The {@code StringBuilder} to add to.
	 */
	abstract void addValueToBuilder (final StringBuilder sb);

	/**
	 * Add the {@code TestJSONKeyValue}'s value to the {@link JSONWriter}.
	 *
	 * @param writer
	 *        The {@code JSONWriter} to add to.
	 */
	abstract void addValueToWriter (final JSONWriter writer);

	/**
	 * Check if the {@link JSONObject} contains this {@code TestJSONKeyValue}'s
	 * value at the correct key.
	 *
	 * @param content
	 *        The {@link JSONObject} to explore.
	 */
	abstract void equalityCheck (final JSONObject content);

	/**
	 * Add the JSON key to the {@link StringBuilder}.
	 *
	 * @param sb
	 *        The {@code StringBuilder} to add to.
	 */
	void addKeyToBuilder (final StringBuilder sb)
	{
		sb.append('"').append(key).append('"');
	}

	/**
	 * Compactly add this {@code TestJSONKeyValue} to a {@link StringBuilder}.
	 *
	 * @param addComma
	 *        {@code true} indicates the last character to be added is a
	 *        comma (,), {@code false} indicates no comma (,) should be
	 *        added at the end of the line.
	 * @param sb
	 *        The {@code StringBuilder} to add to.
	 */
	void addToBuilder (
		final boolean addComma,
		final StringBuilder sb)
	{
		addKeyToBuilder(sb);
		sb.append(':');
		addValueToBuilder(sb);
		if (addComma)
		{
			sb.append(',');
		}
	}

	/**
	 * Add the {@code TestJSONKeyValue}s to a {@link StringBuilder}.
	 *
	 * @param terminateWithComma
	 *        {@code true} indicates the last character to be added is a
	 *        comma (,), {@code false} indicates no comma (,) should be
	 *        added at the end of the input.
	 * @param sb
	 *        The {@code StringBuilder} to add to.
	 * @param keyValues
	 *        An array of {@code TestJSONKeyValue}s to call {@code
	 *        TestJSONKeyValue.test(JSONObject)} on.
	 */
	static void addToBuilder (
		final boolean terminateWithComma,
		final StringBuilder sb,
		final TestJSONKeyValue... keyValues)
	{
		for (int i = 0; i < keyValues.length - 1; i++)
		{
			keyValues[i].addToBuilder(true, sb);
		}
		keyValues[keyValues.length - 1].addToBuilder(terminateWithComma, sb);
	}

	/**
	 * Add the {@code TestJSONKeyValue}s to a {@link JSONWriter}.
	 *
	 * @param writer
	 *        The {@code JSONWriter} to add to.
	 * @param keyValues
	 *        An array of {@code TestJSONKeyValue}s to call {@code
	 *        TestJSONKeyValue.test(JSONObject)} on.
	 */
	static void addToWriter (
		final JSONWriter writer,
		final TestJSONKeyValue... keyValues)
	{
		for (int i = 0; i < keyValues.length; i++)
		{
			keyValues[i].addValueToWriter(writer);
		}
	}

	/**
	 * Add a {@code JSONObject} to a {@link JSONWriter}.
	 *
	 * @param keyName
	 *        The name of the object field.
	 * @param writer
	 *        The {@code JSONWriter} to add to.
	 * @param keyValues
	 *        An array of {@code TestJSONKeyValue}s to call {@code
	 *        TestJSONKeyValue.test(JSONObject)} on.
	 */
	static void addObjectToWriter (
		final String keyName,
		final JSONWriter writer,
		final TestJSONKeyValue... keyValues)
	{
		writer.write(keyName);
		writer.startObject();
		addToWriter(writer, keyValues);
		writer.endObject();
	}

	/**
	 * Check if the {@link JSONObject} contains this {@code TestJSONKeyValue}'s
	 * {@linkplain #key}.
	 *
	 * @param content
	 *        The {@link JSONObject} to explore.
	 */
	void checkKeyExists (final JSONObject content)
	{
		assertTrue(content.containsKey(key), "Missing key: " + key);
	}

	/**
	 * Run the {@link #checkKeyExists(JSONObject)} test and the {@link
	 * #equalityCheck(JSONObject)} test for this {@code TestJSONKeyValue}.
	 *
	 * @param content
	 *        The {@link JSONObject} content to get values from.
	 */
	void test (final JSONObject content)
	{
		checkKeyExists(content);
		equalityCheck(content);
	}

	/**
	 * Test multiple {@code TestJSONKeyValue}s.
	 *
	 * @param content
	 *        The {@link JSONObject} content to get values from.
	 * @param keyValues
	 *        An array of {@code TestJSONKeyValue}s to call {@code
	 *        TestJSONKeyValue.test(JSONObject)} on.
	 */
	static void test (
		final JSONObject content,
		final TestJSONKeyValue... keyValues)
	{
		for (final TestJSONKeyValue TestJSONKeyValue : keyValues)
		{
			TestJSONKeyValue.test(content);
		}
	}

	/**
	 * Construct a {@code TestJSONKeyValue}.
	 *
	 * @param key
	 *        The JSON pair key.
	 */
	TestJSONKeyValue (final String key)
	{
		this.key = key;
	}
}