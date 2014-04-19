/**
 * JSONReader.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.MalformedInputException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import com.avail.annotations.Nullable;

/**
 * A {@code JSONReader} produces {@link JSONFriendly JSON-friendly} {@linkplain
 * JSONValue values} given a valid JSON document.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class JSONReader
implements Closeable
{
	/** The {@linkplain Reader source} of the raw JSON document. */
	private final Reader reader;

	/**
	 * Construct a new {@link JSONReader}.
	 *
	 * @param reader
	 *        The {@linkplain Reader source} of the raw JSON document. The
	 *        reader must {@linkplain Reader#markSupported() support marking}.
	 * @throws IllegalArgumentException
	 *         If the reader does not support marking.
	 */
	public JSONReader (final Reader reader) throws IllegalArgumentException
	{
		if (!reader.markSupported())
		{
			throw new IllegalArgumentException();
		}
		this.reader = reader;
	}

	/**
	 * Read a code point from the {@linkplain Reader source}.
	 *
	 * @return The next code point of the source, or {@code -1} if the source is
	 *         exhausted.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private int readCodePoint () throws IOException
	{
		int next = reader.read();
		if (next == -1)
		{
			return -1;
		}
		assert (next & 0xFFFF) == next;
		final char high = (char) next;
		if (Character.isSurrogate(high))
		{
			next = reader.read();
			if (next == -1)
			{
				throw new MalformedInputException(1);
			}
			assert (next & 0xFFFF) == next;
			final char low = (char) next;
			if (!Character.isSurrogate(low))
			{
				throw new MalformedInputException(1);
			}
			return Character.toCodePoint(high, low);
		}
		return high;
	}

	/**
	 * Peek at the next character of the {@linkplain Reader source}.
	 *
	 * @return The next character of the source, or {@code -1} if the source is
	 *         exhausted.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private int peekCodePoint () throws IOException
	{
		reader.mark(2);
		final int next = readCodePoint();
		reader.reset();
		return next;
	}

	/**
	 * Skip whitespace at the current position of the {@linkplain Reader
	 * source}.
	 *
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private void skipWhitespace () throws IOException
	{
		while (true)
		{
			reader.mark(2);
			final int codePoint = readCodePoint();
			switch (codePoint)
			{
				case ' ':
				case '\t':
				case '\n':
				case '\r':
					break;
				default:
					reader.reset();
					return;
			}
		}
	}

	/**
	 * Peek at the next code point of the {@linkplain Reader source}. If the
	 * code point is a {@linkplain Character#DECIMAL_DIGIT_NUMBER decimal
	 * digit}, then consume it from the source and {@linkplain
	 * StringBuilder#appendCodePoint(int) append} it to the given {@link
	 * StringBuilder}.
	 *
	 * @param builder
	 *        A {@code StringBuilder}, or {@code null} if the character should
	 *        not be accumulated.
	 * @return {@code true} if the code point is a decimal digit (and was
	 *         consumed and appended), {@code false} otherwise.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private boolean peekForDigit (final @Nullable StringBuilder builder)
		throws IOException
	{
		reader.mark(2);
		final int codePoint = readCodePoint();
		if (Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER)
		{
			if (builder != null)
			{
				builder.appendCodePoint(codePoint);
			}
			return true;
		}
		reader.reset();
		return false;
	}

	/**
	 * Peek at the next code point of the {@linkplain Reader source}. If the
	 * code point is a hexadecimal digit, then consume it from the source and
	 * {@linkplain StringBuilder#appendCodePoint(int) append} it to the given
	 * {@link StringBuilder}.
	 *
	 * @param builder
	 *        A {@code StringBuilder}, or {@code null} if the character should
	 *        not be accumulated.
	 * @return {@code true} if the code point is a hexadecimal digit (and was
	 *         consumed and appended), {@code false} otherwise.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private boolean peekForHexDigit (final @Nullable StringBuilder builder)
		throws IOException
	{
		reader.mark(2);
		final int codePoint = readCodePoint();
		if (Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER)
		{
			if (builder != null)
			{
				builder.appendCodePoint(codePoint);
			}
			return true;
		}
		switch (codePoint)
		{
			case 'A':
			case 'B':
			case 'C':
			case 'D':
			case 'E':
			case 'F':
			case 'a':
			case 'b':
			case 'c':
			case 'd':
			case 'e':
			case 'f':
				if (builder != null)
				{
					builder.appendCodePoint(codePoint);
				}
				return true;
		}
		reader.reset();
		return false;
	}

	/**
	 * Peek at the next code point of the {@linkplain Reader source}. If the
	 * code point is the one specified, then consume it from the source and
	 * {@linkplain StringBuilder#appendCodePoint(int) append} it to the given
	 * {@link StringBuilder}.
	 *
	 * @param codePoint
	 *        An arbitrary code point.
	 * @param builder
	 *        A {@code StringBuilder}, or {@code null} if the character should
	 *        not be accumulated.
	 * @return {@code true} if the code point is the one specified (and was
	 *         consumed and appended), {@code false} otherwise.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private boolean peekFor (
			final int codePoint,
			final @Nullable StringBuilder builder)
		throws IOException
	{
		reader.mark(2);
		final int next = readCodePoint();
		if (next == codePoint)
		{
			if (builder != null)
			{
				builder.appendCodePoint(codePoint);
			}
			return true;
		}
		reader.reset();
		return false;
	}

	/**
	 * Peek ahead in the {@linkplain Reader source} in search of the specified
	 * keyword. If the keyword is found, then consume it from the source and
	 * {@linkplain StringBuilder#append(String) append} it to the given {@link
	 * StringBuilder}.
	 *
	 * @param keyword
	 *        An arbitrary keyword.
	 * @param builder
	 *        A {@code StringBuilder}, or {@code null} if the keyword should
	 *        not be accumulated.
	 * @return {@code true} if the keyword is the one specified (and was
	 *         consumed and appended), {@code false} otherwise.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private boolean peekForKeyword (
			final String keyword,
			final @Nullable StringBuilder builder)
		throws IOException
	{
		final int size = keyword.length();
		reader.mark(size);
		for (int i = 0; i < size; )
		{
			final int expected = keyword.codePointAt(i);
			final int codePoint = readCodePoint();
			if (codePoint != expected)
			{
				reader.reset();
				return false;
			}
			i += Character.charCount(codePoint);
		}
		if (builder != null)
		{
			builder.append(keyword);
		}
		return true;
	}

	/**
	 * Read a {@link JSONNumber} from the underlying document.
	 *
	 * @return A {@code JSONNumber}.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 */
	private JSONNumber readNumber () throws IOException
	{
		final StringBuilder builder = new StringBuilder();
		peekFor('-', builder);
		while (peekForDigit(builder))
		{
			// Do nothing.
		}
		if (peekFor('.', builder))
		{
			// This is a decimal point, so expect fractional digits.
			while (peekForDigit(builder))
			{
				// Do nothing.
			}
		}
		if (peekFor('e', builder) || peekFor('E', builder))
		{
			// Engineering notation.
			if (peekFor('-', builder) || peekFor('+', builder))
			{
				// Optional exponent sign.
			}
			while (peekForDigit(builder))
			{
				// Do nothing.
			}
		}
		return new JSONNumber(new BigDecimal(builder.toString()));
	}

	/**
	 * Read a JSON {@link String} from the underlying document.
	 *
	 * @return A {@code String}.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 * @throws MalformedJSONException
	 *         If the string does not terminate.
	 */
	private String readString () throws IOException, MalformedJSONException
	{
		final StringBuilder builder = new StringBuilder();
		peekFor('"', null);
		while (!peekFor('"', null))
		{
			if (peekFor('\\', null))
			{
				final int codePoint = readCodePoint();
				switch (codePoint)
				{
					case '"':
						builder.append('"');
						break;
					case '\\':
						builder.append('\\');
						break;
					case '/':
						builder.append('/');
						break;
					case 'b':
						builder.append('\b');
						break;
					case 'f':
						builder.append('\f');
						break;
					case 'n':
						builder.append('\n');
						break;
					case 'r':
						builder.append('\r');
						break;
					case 't':
						builder.append('\t');
						break;
					case 'u':
						final StringBuilder hex = new StringBuilder(4);
						for (int i = 0; i < 4; i++)
						{
							if (!peekForHexDigit(hex))
							{
								final int bad = peekCodePoint();
								throw new MalformedInputException(
									Character.charCount(bad));
							}
						}
						builder.appendCodePoint(
							Integer.parseInt(hex.toString(), 16));
						break;
					default:
						throw new MalformedInputException(
							Character.charCount(codePoint));
				}
			}
			else
			{
				final int codePoint = readCodePoint();
				if (codePoint == -1)
				{
					throw new MalformedJSONException();
				}
				if (codePoint <= 0x001F)
				{
					throw new MalformedInputException(
						Character.charCount(codePoint));
				}
				builder.appendCodePoint(codePoint);
			}
		}
		return builder.toString();
	}

	/**
	 * Read a {@link JSONArray} from the underlying document.
	 *
	 * @return A {@code JSONArray}.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 * @throws MalformedJSONException
	 *         If the JSON document is malformed.
	 */
	private JSONArray readArray () throws IOException, MalformedJSONException
	{
		final List<JSONData> list = new LinkedList<>();
		peekFor('[', null);
		if (peekFor(']', null))
		{
			return JSONArray.empty();
		}
		do
		{
			list.add(readData());
			skipWhitespace();
		}
		while (peekFor(',', null));
		if (!peekFor(']', null))
		{
			throw new MalformedJSONException();
		}
		return new JSONArray(list.toArray(new JSONData[list.size()]));
	}

	/**
	 * Read a {@link JSONObject} from the underlying document.
	 *
	 * @return A {@code JSONObject}.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 * @throws MalformedJSONException
	 *         If the JSON document is malformed.
	 */
	private JSONObject readObject () throws IOException, MalformedJSONException
	{
		final Map<String, JSONData> map = new HashMap<>();
		peekFor('{', null);
		skipWhitespace();
		if (peekFor('}', null))
		{
			return JSONObject.empty();
		}
		do
		{
			final String key = readString();
			skipWhitespace();
			if (!peekFor(':', null))
			{
				throw new MalformedJSONException();
			}
			final JSONData value = readData();
			map.put(key, value);
			skipWhitespace();
		}
		while (peekFor(',', null));
		if (!peekFor('}', null))
		{
			throw new MalformedJSONException();
		}
		return new JSONObject(map);
	}

	/**
	 * Read an arbitrary {@link JSONData} from the underlying document.
	 *
	 * @return A {@code JSONData}, or {@code null} if no value was available.
	 * @throws IOException
	 *         If an I/O exception occurs.
	 * @throws MalformedJSONException
	 *         If the JSON document is malformed.
	 */
	private @Nullable JSONData readData ()
		throws IOException, MalformedJSONException
	{
		skipWhitespace();
		final int firstCodePoint = peekCodePoint();
		final JSONData data;
		switch (firstCodePoint)
		{
			case -1:
				// Handle an empty document.
				data = null;
				break;
			case '-':
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
				// Read a JSON number.
				data = readNumber();
				break;
			case '"':
				// Read a JSON string.
				data = new JSONValue(readString());
				break;
			case '[':
				// Read a JSON array.
				data = readArray();
				break;
			case '{':
				// Read a JSON object.
				data = readObject();
				break;
			case 'f':
				// Read a JSON false.
				if (peekForKeyword("false", null))
				{
					data = JSONValue.jsonFalse();
				}
				else
				{
					throw new MalformedJSONException();
				}
				break;
			case 'n':
				// Read a JSON value (true, false, or null).
				if (peekForKeyword("null", null))
				{
					data = JSONData.jsonNull();
				}
				else
				{
					throw new MalformedJSONException();
				}
				break;
			case 't':
				// Read a JSON true.
				if (peekForKeyword("true", null))
				{
					data = JSONValue.jsonTrue();
				}
				else
				{
					throw new MalformedJSONException();
				}
				break;
			default:
				throw new MalformedJSONException();
		}
		return data;
	}

	/**
	 * Read the entire underlying JSON document as a single {@code JSONData}.
	 *
	 * @return A {@code JSONData}.
	 * @throws JSONException
	 *         If anything goes wrong.
	 */
	public @Nullable JSONData read () throws JSONException
	{
		try
		{
			final JSONData data = readData();
			skipWhitespace();
			final int codePoint = readCodePoint();
			if (codePoint != -1)
			{
				throw new MalformedJSONException();
			}
			return data;
		}
		catch (final IOException e)
		{
			throw new JSONIOException(e);
		}
	}

	@Override
	public void close () throws IOException
	{
		reader.close();
	}
}
