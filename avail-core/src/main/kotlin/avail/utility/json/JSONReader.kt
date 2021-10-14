/*
 * JSONReader.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.utility.json

import java.io.Closeable
import java.io.IOException
import java.io.Reader
import java.math.BigDecimal
import java.nio.charset.MalformedInputException
import java.util.LinkedList

/**
 * A `JSONReader` produces [JSON-friendly][JSONFriendly] value given a valid
 * JSON document.
 *
 * @property reader
 *   The [source][Reader] of the raw JSON document.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [JSONReader].
 *
 * @param reader
 *   The [source][Reader] of the raw JSON document. The reader must
 *   [support&#32;marking][Reader.markSupported].
 * @throws IllegalArgumentException
 *   If the reader does not support marking.
 */
class JSONReader @Throws(IllegalArgumentException::class) constructor(
	private val reader: Reader) : Closeable
{
	init
	{
		require(reader.markSupported())
	}

	/**
	 * Read a code point from the [source][Reader].
	 *
	 * @return
	 *   The next code point of the source, or `-1` if the source is exhausted.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun readCodePoint(): Int
	{
		var next = reader.read()
		if (next == -1)
		{
			return -1
		}
		assert(next and 0xFFFF == next)
		val high = next.toChar()
		if (Character.isSurrogate(high))
		{
			next = reader.read()
			if (next == -1)
			{
				throw MalformedInputException(1)
			}
			assert(next and 0xFFFF == next)
			val low = next.toChar()
			if (!Character.isSurrogate(low))
			{
				throw MalformedInputException(1)
			}
			return Character.toCodePoint(high, low)
		}
		return high.code
	}

	/**
	 * Peek at the next character of the [source][Reader].
	 *
	 * @return
	 *   The next character of the source, or `-1` if the source is exhausted.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun peekCodePoint(): Int
	{
		reader.mark(2)
		val next = readCodePoint()
		reader.reset()
		return next
	}

	/**
	 * Skip whitespace at the current position of the [source][Reader].
	 *
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun skipWhitespace()
	{
		while (true)
		{
			reader.mark(2)
			when (readCodePoint())
			{
				' '.code, '\t'.code, '\n'.code, '\r'.code ->
				{
				}
				else ->
				{
					reader.reset()
					return
				}
			}
		}
	}

	/**
	 * Peek at the next code point of the [source][Reader]. If the code point is
	 * a [decimal][Character.DECIMAL_DIGIT_NUMBER], then consume it from the
	 * source and [append][java.lang.StringBuilder.appendCodePoint] it to the
	 * given
	 * [StringBuilder].
	 *
	 * @param builder
	 *   A `StringBuilder`, or `null` if the character should not be
	 *   accumulated.
	 * @return
	 *   `true` if the code point is a decimal digit (and was consumed and
	 *   appended), `false` otherwise.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun peekForDigit(builder: StringBuilder?): Boolean
	{
		reader.mark(2)
		val codePoint = readCodePoint()
		if (Character.getType(codePoint)
			== Character.DECIMAL_DIGIT_NUMBER.toInt())
		{
			builder?.appendCodePoint(codePoint)
			return true
		}
		reader.reset()
		return false
	}

	/**
	 * Peek at the next code point of the [source][Reader]. If the code point is
	 * a hexadecimal digit, then consume it from the source and
	 * [append][java.lang.StringBuilder.appendCodePoint] it to the given
	 * [StringBuilder].
	 *
	 * @param builder
	 *   A `StringBuilder`, or `null` if the character should not be
	 *   accumulated.
	 * @return
	 *   `true` if the code point is a hexadecimal digit (and was consumed
	 *   and appended), `false` otherwise.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun peekForHexDigit(builder: StringBuilder?): Boolean
	{
		reader.mark(2)
		val codePoint = readCodePoint()
		if (Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER.toInt())
		{
			builder?.appendCodePoint(codePoint)
			return true
		}
		when (codePoint)
		{
			'A'.code, 'B'.code, 'C'.code, 'D'.code, 'E'.code,
			'F'.code, 'a'.code, 'b'.code, 'c'.code, 'd'.code,
			'e'.code, 'f'.code ->
			{
				builder?.appendCodePoint(codePoint)
				return true
			}
		}
		reader.reset()
		return false
	}

	/**
	 * Peek at the next code point of the [source][Reader]. If the code point is
	 * the one specified, then consume it from the source and
	 * [append][java.lang.StringBuilder.appendCodePoint] it to the given
	 * [StringBuilder].
	 *
	 * @param codePoint
	 *   An arbitrary code point.
	 * @param builder
	 *   A `StringBuilder`, or `null` if the character should not be
	 *   accumulated.
	 * @return
	 *   `true` if the code point is the one specified (and was consumed
	 *   and appended), `false` otherwise.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun peekFor(
		codePoint: Int,
		builder: StringBuilder?): Boolean
	{
		reader.mark(2)
		val next = readCodePoint()
		if (next == codePoint)
		{
			builder?.appendCodePoint(codePoint)
			return true
		}
		reader.reset()
		return false
	}

	/**
	 * Peek ahead in the [source][Reader] in search of the specified
	 * keyword. If the keyword is found, then consume it from the source.
	 *
	 * @param keyword
	 *   An arbitrary keyword.
	 * @return
	 *   `true` if the keyword is the one specified (and was consumed
	 *    and appended), `false` otherwise.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun peekForKeyword(
		keyword: String): Boolean
	{
		val size = keyword.length
		reader.mark(size)
		var codePoint: Int
		var i = 0
		while (i < size)
		{
			val expected = keyword.codePointAt(i)
			codePoint = readCodePoint()
			if (codePoint != expected)
			{
				reader.reset()
				return false
			}
			i += Character.charCount(codePoint)
		}
		return true
	}

	/**
	 * Read a [JSONNumber] from the underlying document.
	 *
	 * @return
	 *   A `JSONNumber`.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun readNumber(): JSONNumber
	{
		val builder = StringBuilder()
		peekFor('-'.code, builder)
		while (peekForDigit(builder))
		{
			// Do nothing.
		}
		if (peekFor('.'.code, builder))
		{
			// This is a decimal point, so expect fractional digits.
			while (peekForDigit(builder))
			{
				// Do nothing.
			}
		}
		// Optional exponent sign.
		if (peekFor('e'.code, builder) || peekFor('E'.code, builder))
		{
			// Engineering notation.
			peekFor('-'.code, builder) || peekFor('+'.code, builder)
			while (peekForDigit(builder))
			{
				// Do nothing.
			}
		}
		return JSONNumber(BigDecimal(builder.toString()))
	}

	/**
	 * Read a JSON [String] from the underlying document.
	 *
	 * @return
	 *   A `String`.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 * @throws MalformedJSONException
	 *   If the string does not terminate.
	 */
	@Throws(IOException::class, MalformedJSONException::class)
	private fun readString(): String
	{
		peekFor('"'.code, null)
		val builder = StringBuilder()
		while (!peekFor('"'.code, null))
		{
			if (peekFor('\\'.code, null))
			{
				when (val codePoint = readCodePoint())
				{
					'"'.code -> builder.append('"')
					'\\'.code -> builder.append('\\')
					'/'.code -> builder.append('/')
					'b'.code -> builder.append('\b')
					'f'.code -> builder.append("\\f")
					'n'.code -> builder.append('\n')
					'r'.code -> builder.append('\r')
					't'.code -> builder.append('\t')
					'u'.code ->
					{
						val hex = StringBuilder(4)
						for (i in 0 .. 3)
						{
							if (!peekForHexDigit(hex))
							{
								val bad = peekCodePoint()
								throw MalformedInputException(
									Character.charCount(bad))
							}
						}
						builder.appendCodePoint(
							Integer.parseInt(hex.toString(), 16))
					}
					else -> throw MalformedInputException(
						Character.charCount(codePoint))
				}
			}
			else
			{
				val codePoint = readCodePoint()
				if (codePoint == -1)
				{
					throw MalformedJSONException()
				}
				if (codePoint <= 0x001F)
				{
					throw MalformedInputException(
						Character.charCount(codePoint))
				}
				builder.appendCodePoint(codePoint)
			}
		}
		return builder.toString()
	}

	/**
	 * Read a [JSONArray] from the underlying document.
	 *
	 * @return
	 *   A `JSONArray`.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 * @throws MalformedJSONException
	 *   If the JSON document is malformed.
	 */
	@Throws(IOException::class, MalformedJSONException::class)
	private fun readArray(): JSONArray
	{
		peekFor('['.code, null)
		if (peekFor(']'.code, null))
		{
			return JSONArray.empty()
		}
		val list = LinkedList<JSONData>()
		do
		{
			list.add(readData())
			skipWhitespace()
		}
		while (peekFor(','.code, null))
		if (!peekFor(']'.code, null))
		{
			throw MalformedJSONException()
		}
		return JSONArray(list.toTypedArray())
	}

	/**
	 * Read a [JSONObject] from the underlying document.
	 *
	 * @return
	 *   A `JSONObject`.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 * @throws MalformedJSONException
	 *   If the JSON document is malformed.
	 */
	@Throws(IOException::class, MalformedJSONException::class)
	private fun readObject(): JSONObject
	{
		peekFor('{'.code, null)
		skipWhitespace()
		if (peekFor('}'.code, null))
		{
			return JSONObject.empty
		}
		val map = mutableMapOf<String, JSONData>()
		do
		{
			skipWhitespace()
			val key = readString()
			skipWhitespace()
			if (!peekFor(':'.code, null))
			{
				throw MalformedJSONException()
			}
			val value = readData()
			map[key] = value
			skipWhitespace()
		}
		while (peekFor(','.code, null))
		if (!peekFor('}'.code, null))
		{
			throw MalformedJSONException()
		}
		return JSONObject(map)
	}

	/**
	 * Read an arbitrary [JSONData] from the underlying document.
	 *
	 * @return
	 *   A `JSONData`, or `null` if no value was available.
	 * @throws IOException
	 * If an I/O exception occurs.
	 * @throws MalformedJSONException
	 * If the JSON document is malformed.
	 */
	@Throws(IOException::class, MalformedJSONException::class)
	private fun readData(): JSONData
	{
		skipWhitespace()
		val firstCodePoint = peekCodePoint()
		val data: JSONData
		when (firstCodePoint)
		{
			-1 ->
				// Handle an empty document.
				data = JSONData.jsonNull
			'-'.code, '0'.code, '1'.code, '2'.code, '3'.code,
			'4'.code, '5'.code, '6'.code, '7'.code, '8'.code,
			'9'.code ->
				// Read a JSON number.
				data = readNumber()
			'"'.code ->
				// Read a JSON string.
				data = JSONValue(readString())
			'['.code ->
				// Read a JSON array.
				data = readArray()
			'{'.code ->
				// Read a JSON object.
				data = readObject()
			'f'.code ->
				// Read a JSON false.
				if (peekForKeyword("false"))
				{
					data = JSONValue.jsonFalse
				}
				else
				{
					throw MalformedJSONException()
				}
			'n'.code ->
				// Read a JSON value (true, false, or null).
				if (peekForKeyword("null"))
				{
					data = JSONData.jsonNull
				}
				else
				{
					throw MalformedJSONException()
				}
			't'.code ->
				// Read a JSON true.
				if (peekForKeyword("true"))
				{
					data = JSONValue.jsonTrue
				}
				else
				{
					throw MalformedJSONException()
				}
			else -> throw MalformedJSONException()
		}
		return data
	}

	/**
	 * Read the entire underlying JSON document as a single `JSONData`.
	 *
	 * @return
	 *   A `JSONData`.
	 * @throws JSONException
	 *   If anything goes wrong.
	 */
	@Throws(JSONException::class)
	fun read(): JSONData?
	{
		try
		{
			val data = readData()
			skipWhitespace()
			val codePoint = readCodePoint()
			if (codePoint != -1)
			{
				throw MalformedJSONException()
			}
			return data
		}
		catch (e: IOException)
		{
			throw JSONIOException(e)
		}

	}

	@Throws(IOException::class)
	override fun close()
	{
		reader.close()
	}
}
