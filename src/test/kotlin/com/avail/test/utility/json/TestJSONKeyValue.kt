/*
 * TestJSONKeyValue.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.test.utility.json

import com.avail.utility.json.JSONNumber
import com.avail.utility.json.JSONObject
import com.avail.utility.json.JSONWriter
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * A `TestJSONKeyValue` is an enum used for creating static test data for
 * testing JSON utility functionality in Avail.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property key
 *   The JSON pair key.
 *
 * @constructor
 * Construct a `TestJSONKeyValue`.
 *
 * @param key
 *   The JSON pair key.
 */
enum class TestJSONKeyValue constructor(val key: String)
{
	/** For testing a String  */
	IMASTRING("imastring")
	{
		/** The test value  */
		private val value = "foo"

		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append('"').append(value).append('"')
		}

		override fun equalityCheck(content: JSONObject)
		{
			assertEquals(value, content.getString(key))
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.write(value)
		}
	},

	/** For testing an integer  */
	IMANINT("imanint")
	{
		/** The test value  */
		private val value = 52

		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append(value)
		}

		override fun equalityCheck(content: JSONObject)
		{
			assertEquals(value, content.getNumber(key).int)
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.write(value)
		}
	},

	/** For testing a long  */
	IMALONG("imalong")
	{
		/** The test value  */
		private val value = 34359738368L

		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append(value)
		}

		override fun equalityCheck(content: JSONObject)
		{
			assertEquals(value, content.getNumber(key).long)
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.write(value)
		}
	},

	/** For testing a float  */
	IMAFLOAT("imafloat")
	{
		/** The test value  */
		private val value = 3.2f

		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append(value)
		}

		override fun equalityCheck(content: JSONObject)
		{
			assertEquals(value, content.getNumber(key).float)
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.write(value)
		}
	},

	/** For testing a double  */
	IMADOUBLE("imadouble")
	{
		/** The test value  */
		private val value = 3.12345678910

		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append(value)
		}

		override fun equalityCheck(content: JSONObject)
		{
			assertEquals(value, content.getNumber(key).double)
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.write(value)
		}
	},

	/** For testing a boolean that evaluates to true  */
	IMATRUE("imatrue")
	{
		/** The test value  */
		private val value = true

		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append(value)
		}

		override fun equalityCheck(content: JSONObject)
		{
			assertEquals(value, content.getBoolean(key))
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.write(value)
		}
	},

	/** For testing a boolean that evaluates to false  */
	IMAFALSE("imafalse")
	{
		/** The test value  */
		private val value = false

		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append(value)
		}

		override fun equalityCheck(content: JSONObject)
		{
			assertEquals(value, content.getBoolean(key))
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.write(value)
		}
	},

	/** For testing a boolean that evaluates to false  */
	IMANULL("imanull")
	{
		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append("null")
		}

		override fun equalityCheck(content: JSONObject)
		{
			val contentValue = content[key]
			assertTrue(
				contentValue.isNull,
				"Expected a null, but received $contentValue")
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.writeNull()
		}
	},

	/** For testing an integer array with no spaces  */
	IMACOMPACTARRAY("imacompactarrayKey")
	{
		/** The test value  */
		private val value = intArrayOf(1, 2, 3, 4, 5)

		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append('[')
			for (i in 0 until value.size - 1)
			{
				sb.append(value[i]).append(',')
			}
			sb.append(value[value.size - 1]).append(']')
		}

		override fun equalityCheck(content: JSONObject)
		{
			val jsonArray = content.getArray(key)
			val array = IntArray(jsonArray.size())
			for (i in 0 until jsonArray.size())
			{
				array[i] = (jsonArray[i] as JSONNumber).int
			}
			assertArrayEquals(value, array)
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.startArray()
			for (v in value)
			{
				writer.write(v)
			}
			writer.endArray()
		}
	},

	/** For testing an array with superfluous spaces  */
	IMANARRAY("imanarray")
	{
		/** The test value  */
		private val value = intArrayOf(1, 2, 3, 4, 5)

		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append("[\n")
			for (i in 0 until value.size - 1)
			{
				sb.append('\t').append(value[i]).append(", \n")
			}
			sb.append('\t').append(value[value.size - 1]).append("\n]")
		}

		override fun equalityCheck(content: JSONObject)
		{
			val jsonArray = content.getArray(key)
			val array = IntArray(jsonArray.size())
			for (i in 0 until jsonArray.size())
			{
				array[i] = (jsonArray[i] as JSONNumber).int
			}
			assertArrayEquals(value, array)
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.startArray()
			for (v in value)
			{
				writer.write(v)
			}
			writer.endArray()
		}
	},

	/** For testing a String in a JSON object.  */
	OBJSTRING("objstring")
	{
		/** The test value. */
		private val value = "bar"

		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append('"').append(value).append('"')
		}

		override fun equalityCheck(content: JSONObject)
		{
			assertEquals(value, content.getString(key))
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.write(value)
		}
	},

	/** For testing an integer in a JSON object.  */
	OBJINT("objint")
	{
		/** The test value  */
		private val value = 10

		override fun addValueToBuilder(sb: StringBuilder)
		{
			sb.append(value)
		}

		override fun equalityCheck(content: JSONObject)
		{
			assertEquals(value, content.getNumber(key).int)
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			writer.write(key)
			writer.write(value)
		}
	},

	/** For testing a JSON object.  */
	IMANOBJECT("imanobject")
	{
		override fun addValueToBuilder(sb: StringBuilder)
		{
			// Do nothing as this should be done via the sub fields.
		}

		override fun equalityCheck(content: JSONObject)
		{
			// Do nothing as this should be checked via the sub fields.
		}

		override fun addValueToWriter(writer: JSONWriter)
		{
			throw UnsupportedOperationException(
				"TestJSONKeyValue.IMANOBJECT must be built manually. Try"
					+ " something like: \n\t addObjectToWriter(IMANOBJECT.key, "
					+ "writer, OBJSTRING, OBJINT);\n")
		}
	};

	/**
	 * Add the `TestJSONKeyValue`'s value to the [StringBuilder].
	 *
	 * @param sb
	 *   The `StringBuilder` to add to.
	 */
	internal abstract fun addValueToBuilder(sb: StringBuilder)

	/**
	 * Add the `TestJSONKeyValue`'s value to the [JSONWriter].
	 *
	 * @param writer
	 *   The `JSONWriter` to add to.
	 */
	internal abstract fun addValueToWriter(writer: JSONWriter)

	/**
	 * Check if the [JSONObject] contains this `TestJSONKeyValue`'s value at the
	 * correct key.
	 *
	 * @param content
	 *   The [JSONObject] to explore.
	 */
	internal abstract fun equalityCheck(content: JSONObject)

	/**
	 * Add the JSON key to the [StringBuilder].
	 *
	 * @param sb
	 *   The `StringBuilder` to add to.
	 */
	internal fun addKeyToBuilder(sb: StringBuilder)
	{
		sb.append('"').append(key).append('"')
	}

	/**
	 * Compactly add this `TestJSONKeyValue` to a [StringBuilder].
	 *
	 * @param addComma
	 *   `true` indicates the last character to be added is a comma (,), `false`
	 *   indicates no comma (,) should be added at the end of the line.
	 * @param sb
	 *   The `StringBuilder` to add to.
	 */
	internal fun addToBuilder(addComma: Boolean, sb: StringBuilder)
	{
		addKeyToBuilder(sb)
		sb.append(':')
		addValueToBuilder(sb)
		if (addComma)
		{
			sb.append(',')
		}
	}

	/**
	 * Check if the [JSONObject] contains this `TestJSONKeyValue`'s [key].
	 *
	 * @param content
	 *   The [JSONObject] to explore.
	 */
	internal fun checkKeyExists(content: JSONObject)
	{
		assertTrue(content.containsKey(key), "Missing key: $key")
	}

	/**
	 * Run the [checkKeyExists] test and the [equalityCheck] test for this
	 * `TestJSONKeyValue`.
	 *
	 * @param content
	 * The [JSONObject] content to get values from.
	 */
	internal fun test(content: JSONObject)
	{
		checkKeyExists(content)
		equalityCheck(content)
	}

	companion object
	{
		/**
		 * Add the `TestJSONKeyValue`s to a [StringBuilder].
		 *
		 * @param terminateWithComma
		 *   `true` indicates the last character to be added is a comma (,),
		 *   `false` indicates no comma (,) should be added at the end of the
		 *   input.
		 * @param sb
		 *   The `StringBuilder` to add to.
		 * @param keyValues
		 *   An array of `TestJSONKeyValue`s to call
		 *   `TestJSONKeyValue.test(JSONObject)` on.
		 */
		internal fun addToBuilder(
			terminateWithComma: Boolean,
			sb: StringBuilder,
			vararg keyValues: TestJSONKeyValue)
		{
			for (i in 0 until keyValues.size - 1)
			{
				keyValues[i].addToBuilder(true, sb)
			}
			keyValues[keyValues.size - 1].addToBuilder(terminateWithComma, sb)
		}

		/**
		 * Add the `TestJSONKeyValue`s to a [JSONWriter].
		 *
		 * @param writer
		 *   The `JSONWriter` to add to.
		 * @param keyValues
		 *   An array of `TestJSONKeyValue`s to call
		 *   `TestJSONKeyValue.test(JSONObject)` on.
		 */
		internal fun addToWriter(
			writer: JSONWriter,
			vararg keyValues: TestJSONKeyValue)
		{
			for (i in keyValues.indices)
			{
				keyValues[i].addValueToWriter(writer)
			}
		}

		/**
		 * Add a `JSONObject` to a [JSONWriter].
		 *
		 * @param keyName
		 *   The name of the object field.
		 * @param writer
		 *   The `JSONWriter` to add to.
		 * @param keyValues
		 *   An array of `TestJSONKeyValue`s to call
		 *   `TestJSONKeyValue.test(JSONObject)` on.
		 */
		internal fun addObjectToWriter(
			keyName: String,
			writer: JSONWriter,
			vararg keyValues: TestJSONKeyValue)
		{
			writer.write(keyName)
			writer.startObject()
			addToWriter(writer, *keyValues)
			writer.endObject()
		}

		/**
		 * Test multiple `TestJSONKeyValue`s.
		 *
		 * @param content
		 *   The [JSONObject] content to get values from.
		 * @param keyValues
		 *   An array of `TestJSONKeyValue`s to call
		 *   `TestJSONKeyValue.test(JSONObject)` on.
		 */
		internal fun test(
			content: JSONObject,
			vararg keyValues: TestJSONKeyValue)
		{
			for (TestJSONKeyValue in keyValues)
			{
				TestJSONKeyValue.test(content)
			}
		}
	}
}
