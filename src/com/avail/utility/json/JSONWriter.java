/**
 * JSONWriter.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import static com.avail.utility.json.JSONWriter.JSONState.*;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Deque;
import java.util.Formatter;
import java.util.LinkedList;
import com.avail.annotations.InnerAccess;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.avail.utility.evaluation.Continuation0;

/**
 * A {@code JSONWriter} produces ASCII-only documents that adhere strictly to
 * ECMA 404: "The JSON Data Interchange Format".
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a href=
 *      "http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf">
 *      ECMA 404: "The JSON Data Interchange Format"</a>
 */
public final class JSONWriter
implements AutoCloseable
{
	/** The {@linkplain Writer target} for the raw JSON document. */
	private final Writer writer;

	/**
	 * Construct a new {@link JSONWriter}.
	 */
	public JSONWriter ()
	{
		this.writer = new StringWriter();
	}

	/**
	 * Construct a new {@link JSONWriter}.
	 *
	 * @param writer
	 *        The {@linkplain Writer target} for the raw JSON document.
	 */
	public JSONWriter (final Writer writer)
	{
		this.writer = writer;
	}

	/**
	 * Answer a {@link JSONWriter} that targets an internal {@link
	 * StringWriter}.
	 *
	 * @return A JSONWriter.
	 */
	public static JSONWriter newWriter ()
	{
		return new JSONWriter(new StringWriter());
	}

	/**
	 * Write the specified character to the underlying document {@linkplain
	 * Writer writer}.
	 *
	 * @param c
	 *        The character to write. Only the 16 low order bits will be
	 *        written. (If assertions are enabled, then attempting to write
	 *        values that would be truncated causes an {@link AssertionError}.)
	 * @throws JSONIOException
	 *         If the operation fails.
	 */
	@InnerAccess void privateWrite (final int c)
		throws JSONIOException
	{
		assert (c & 0xFFFF) == c;
		try
		{
			writer.write(c);
		}
		catch (final IOException e)
		{
			throw new JSONIOException(e);
		}
	}

	/**
	 * Write the specified text to the underlying document {@linkplain Writer
	 * writer}.
	 *
	 * @param text
	 *        The text that should be written.
	 * @throws JSONIOException
	 *         If the operation fails.
	 */
	@InnerAccess void privateWrite (final String text)
		throws JSONIOException
	{
		try
		{
			writer.write(text);
		}
		catch (final IOException e)
		{
			throw new JSONIOException(e);
		}
	}

	/**
	 * A {@JSONState} represents the {@linkplain JSONWriter writer}'s view of
	 * what operations are legal based on what operations have become before.
	 */
	enum JSONState
	{
		/**
		 * The {@linkplain JSONWriter writer} is expecting a single arbitrary
		 * value.
		 */
		EXPECTING_SINGLE_VALUE
		{
			@Override
			JSONState nextStateAfterValue ()
			{
				return EXPECTING_END_OF_DOCUMENT;
			}

			@Override
			void checkCanEndDocument () throws IllegalStateException
			{
				// Do nothing.
			}
		},

		/**
		 * The {@linkplain JSONWriter writer} is expecting the end of the
		 * document.
		 */
		EXPECTING_END_OF_DOCUMENT
		{
			@Override
			JSONState nextStateAfterValue ()
			{
				throw new IllegalStateException();
			}

			@Override
			void checkCanEndDocument () throws IllegalStateException
			{
				// Do nothing.
			}

			@Override
			void checkCanWriteAnyValue () throws IllegalStateException
			{
				throw new IllegalStateException();
			}

			@Override
			void checkCanWriteStringValue () throws IllegalStateException
			{
				throw new IllegalStateException();
			}

			@Override
			void checkCanWriteObjectStart () throws IllegalStateException
			{
				throw new IllegalStateException();
			}

			@Override
			void checkCanWriteArrayStart () throws IllegalStateException
			{
				throw new IllegalStateException();
			}
		},

		/**
		 * The {@linkplain JSONWriter writer} is expecting the first object key
		 * or the end of an object.
		 */
		EXPECTING_FIRST_OBJECT_KEY_OR_OBJECT_END
		{
			@Override
			JSONState nextStateAfterValue ()
			{
				return EXPECTING_OBJECT_VALUE;
			}

			@Override
			void checkCanWriteAnyValue () throws IllegalStateException
			{
				throw new IllegalStateException();
			}

			@Override
			void checkCanWriteObjectStart () throws IllegalStateException
			{
				throw new IllegalStateException();
			}

			@Override
			void checkCanWriteObjectEnd ()
			{
				// Do nothing.
			}

			@Override
			void checkCanWriteArrayStart () throws IllegalStateException
			{
				throw new IllegalStateException();
			}
		},

		/**
		 * The {@linkplain JSONWriter writer} is expecting an object key or
		 * the end of an object.
		 */
		EXPECTING_OBJECT_KEY_OR_OBJECT_END
		{
			@Override
			JSONState nextStateAfterValue ()
			{
				return EXPECTING_OBJECT_VALUE;
			}

			@Override
			void checkCanWriteAnyValue () throws IllegalStateException
			{
				throw new IllegalStateException();
			}

			@Override
			void checkCanWriteObjectStart () throws IllegalStateException
			{
				throw new IllegalStateException();
			}

			@Override
			void checkCanWriteObjectEnd ()
			{
				// Do nothing.
			}

			@Override
			void checkCanWriteArrayStart () throws IllegalStateException
			{
				throw new IllegalStateException();
			}

			@Override
			void writePrologueTo (final JSONWriter writer)
				throws JSONIOException
			{
				writer.privateWrite(',');
			}
		},

		/**
		 * The {@linkplain JSONWriter writer} is expecting an object value.
		 */
		EXPECTING_OBJECT_VALUE
		{
			@Override
			JSONState nextStateAfterValue ()
			{
				return EXPECTING_OBJECT_KEY_OR_OBJECT_END;
			}

			@Override
			void writePrologueTo (final JSONWriter writer)
				throws JSONIOException
			{
				writer.privateWrite(':');
			}
		},

		/**
		 * The {@linkplain JSONWriter writer} is expecting the first value of an
		 * array or the end of an array.
		 */
		EXPECTING_FIRST_VALUE_OR_ARRAY_END
		{
			@Override
			JSONState nextStateAfterValue ()
			{
				return EXPECTING_VALUE_OR_ARRAY_END;
			}

			@Override
			void checkCanWriteArrayEnd ()
			{
				// Do nothing.
			}
		},

		/**
		 * The {@linkplain JSONWriter writer} is expecting an arbitrary value or
		 * the end of a JSON array.
		 */
		EXPECTING_VALUE_OR_ARRAY_END
		{
			@Override
			JSONState nextStateAfterValue ()
			{
				return EXPECTING_VALUE_OR_ARRAY_END;
			}

			@Override
			void checkCanWriteArrayEnd ()
			{
				// Do nothing.
			}

			@Override
			void writePrologueTo (final JSONWriter writer)
				throws JSONIOException
			{
				writer.privateWrite(',');
			}
		};

		/**
		 * Check that the receiver permits the {@linkplain JSONWriter writer} to
		 * be {@linkplain JSONWriter#close() closed}.
		 *
		 * @throws IllegalStateException
		 *         If the document cannot be closed yet.
		 */
		void checkCanEndDocument () throws IllegalStateException
		{
			throw new IllegalStateException();
		}

		/**
		 * Check that the receiver permits the {@linkplain JSONWriter writer} to
		 * emit an arbitrary value.
		 *
		 * @throws IllegalStateException
		 *         If an arbitrary value cannot be written.
		 */
		void checkCanWriteAnyValue () throws IllegalStateException
		{
			// Do nothing.
		}

		/**
		 * Check that the receiver permits the {@linkplain JSONWriter writer} to
		 * emit a {@link String value}.
		 *
		 * @throws IllegalStateException
		 *         If an arbitrary value cannot be written.
		 */
		void checkCanWriteStringValue () throws IllegalStateException
		{
			// Do nothing.
		}

		/**
		 * Check that the receiver permits the {@linkplain JSONWriter writer} to
		 * emit an object beginning.
		 *
		 * @throws IllegalStateException
		 *         If an object beginning cannot be written.
		 */
		void checkCanWriteObjectStart () throws IllegalStateException
		{
			// Do nothing.
		}

		/**
		 * Check that the receiver permits the {@linkplain JSONWriter writer} to
		 * emit an object ending.
		 *
		 * @throws IllegalStateException
		 *         If an object ending cannot be written.
		 */
		void checkCanWriteObjectEnd () throws IllegalStateException
		{
			throw new IllegalStateException();
		}

		/**
		 * Check that the receiver permits the {@linkplain JSONWriter writer} to
		 * emit an array beginning.
		 *
		 * @throws IllegalStateException
		 *         If an array beginning cannot be written.
		 */
		void checkCanWriteArrayStart () throws IllegalStateException
		{
			// Do nothing.
		}

		/**
		 * Check that the receiver permits the {@linkplain JSONWriter writer} to
		 * emit an array ending.
		 *
		 * @throws IllegalStateException
		 *         If an array ending cannot be written.
		 */
		void checkCanWriteArrayEnd () throws IllegalStateException
		{
			throw new IllegalStateException();
		}

		/**
		 * Answer the next {@linkplain JSONState state} following the writing of
		 * a value.
		 *
		 * @return The next state.
		 */
		abstract JSONState nextStateAfterValue ();

		/**
		 * Write any prologue required before a value has been written, given
		 * that the {@linkplain JSONState receiver} is the current state.
		 *
		 * @param writer
		 *        A writer.
		 * @throws JSONIOException
		 *         If an I/O exception occurs.
		 */
		@InnerAccess void writePrologueTo (final JSONWriter writer)
			throws JSONIOException
		{
			// Do nothing.
		}
	}

	/**
	 * The {@linkplain Deque stack} of {@linkplain JSONState states}, to ensure
	 * correct usage of the {@linkplain JSONWriter writer}.
	 */
	@InnerAccess final Deque<JSONState> stack = new LinkedList<>();

	{
		stack.addFirst(EXPECTING_SINGLE_VALUE);
	}

	/**
	 * Write a JSON {@code null} to the underlying document {@linkplain Writer
	 * writer}.
	 *
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an arbitrary value cannot be written.
	 */
	public void writeNull () throws JSONIOException, IllegalStateException
	{
		final JSONState state = stack.removeFirst();
		state.checkCanWriteAnyValue();
		state.writePrologueTo(this);
		// Without this cast to Object, the compiler apparently RANDOMLY chooses
		// which overload to compile. Yeah…
		privateWrite(String.valueOf((Object) null));
		stack.addFirst(state.nextStateAfterValue());
	}

	/**
	 * Write the specified {@code boolean} to the underlying document
	 * {@linkplain Writer writer} as a JSON boolean.
	 *
	 * @param value
	 *        A {@code boolean} value.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an arbitrary value cannot be written.
	 */
	public void write (final boolean value)
		throws JSONIOException, IllegalStateException
	{
		final JSONState state = stack.removeFirst();
		state.checkCanWriteAnyValue();
		state.writePrologueTo(this);
		privateWrite(Boolean.toString(value));
		stack.addFirst(state.nextStateAfterValue());
	}

	/**
	 * Write the specified {@link BigDecimal} to the underlying document
	 * {@linkplain Writer writer} as a JSON number.
	 *
	 * @param value
	 *        A {@code BigDecimal} value.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an arbitrary value cannot be written.
	 */
	public void write (final BigDecimal value)
		throws JSONIOException, IllegalStateException
	{
		final JSONState state = stack.removeFirst();
		state.checkCanWriteAnyValue();
		state.writePrologueTo(this);
		privateWrite(value.toString());
		stack.addFirst(state.nextStateAfterValue());
	}

	/**
	 * Write the specified {@link BigInteger} to the underlying document
	 * {@linkplain Writer writer} as a JSON number.
	 *
	 * @param value
	 *        A {@code BigInteger} value.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an arbitrary value cannot be written.
	 */
	public void write (final BigInteger value)
		throws JSONIOException, IllegalStateException
	{
		final JSONState state = stack.removeFirst();
		state.checkCanWriteAnyValue();
		state.writePrologueTo(this);
		privateWrite(value.toString());
		stack.addFirst(state.nextStateAfterValue());
	}

	/**
	 * Write the specified {@code int} to the underlying document {@linkplain
	 * Writer writer} as a JSON number.
	 *
	 * @param value
	 *        A {@code int} value.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an arbitrary value cannot be written.
	 */
	public void write (final int value)
		throws JSONIOException, IllegalStateException
	{
		final JSONState state = stack.removeFirst();
		state.checkCanWriteAnyValue();
		state.writePrologueTo(this);
		privateWrite(String.format("%d", value));
		stack.addFirst(state.nextStateAfterValue());
	}

	/**
	 * Write the specified {@code long} to the underlying document {@linkplain
	 * Writer writer} as a JSON number.
	 *
	 * @param value
	 *        A {@code long} value.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an arbitrary value cannot be written.
	 */
	public void write (final long value)
		throws JSONIOException, IllegalStateException
	{
		final JSONState state = stack.removeFirst();
		state.checkCanWriteAnyValue();
		state.writePrologueTo(this);
		privateWrite(String.format("%d", value));
		stack.addFirst(state.nextStateAfterValue());
	}

	/**
	 * Write the specified {@code float} to the underlying document {@linkplain
	 * Writer writer} as a JSON number.
	 *
	 * @param value
	 *        A {@code float} value.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an arbitrary value cannot be written.
	 */
	public void write (final float value)
		throws JSONIOException, IllegalStateException
	{
		final JSONState state = stack.removeFirst();
		state.checkCanWriteAnyValue();
		state.writePrologueTo(this);
		if (Float.isInfinite(value))
		{
			privateWrite(value == Float.POSITIVE_INFINITY
				? "Infinity"
				: "-Infinity");
		}
		else if (Float.isNaN(value))
		{
			privateWrite("NaN");
		}
		else
		{
			privateWrite(String.format("%g", value));
		}
		stack.addFirst(state.nextStateAfterValue());
	}

	/**
	 * Write the specified {@code double} to the underlying document {@linkplain
	 * Writer writer} as a JSON number. Use JSON 5 extensions (and an additional
	 * NaN extension).
	 *
	 * @param value
	 *        A {@code double} value.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an arbitrary value cannot be written.
	 */
	public void write (final double value)
		throws JSONIOException, IllegalStateException
	{
		final JSONState state = stack.removeFirst();
		state.checkCanWriteAnyValue();
		state.writePrologueTo(this);
		if (Double.isInfinite(value))
		{
			privateWrite(value == Double.POSITIVE_INFINITY
				? "Infinity"
				: "-Infinity");
		}
		else if (Double.isNaN(value))
		{
			privateWrite("NaN");
		}
		else
		{
			privateWrite(String.format("%g", value));
		}
		stack.addFirst(state.nextStateAfterValue());
	}

	/**
	 * Write the specified {@link String} to the underlying document {@linkplain
	 * Writer writer} as a JSON string. All non-ASCII characters are encoded as
	 * Unicode escape sequences, so only ASCII characters will be written.
	 *
	 * @param pattern
	 *        A {@linkplain Formatter pattern} {@code String} (may be {@code
	 *        null}).
	 * @param args
	 *        The arguments to the pattern.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an arbitrary value cannot be written.
	 */
	public void format (
			final @Nullable String pattern,
			final Object... args)
		throws JSONIOException, IllegalStateException
	{
		if (pattern == null)
		{
			writeNull();
		}
		else
		{
			final JSONState state = stack.removeFirst();
			state.checkCanWriteStringValue();
			state.writePrologueTo(this);
			final String value = String.format(pattern, args);
			privateWrite('"');
			int codePoint;
			for (
				int i = 0, size = value.length();
				i < size;
				i += Character.charCount(codePoint))
			{
				codePoint = value.codePointAt(i);
				if (Character.isISOControl(codePoint))
				{
					switch (codePoint)
					{
						// Backspace.
						case '\b':
							privateWrite("\\b");
							break;
						// Character tabulation.
						case '\t':
							privateWrite("\\t");
							break;
						// Line feed.
						case '\n':
							privateWrite("\\n");
							break;
						// Form feed.
						case '\f':
							privateWrite("\\f");
							break;
						// Carriage return.
						case '\r':
							privateWrite("\\r");
							break;
						default:
							privateWrite(String.format("\\u%04X", codePoint));
							break;
					}
				}
				else if (codePoint == '\\')
				{
					privateWrite("\\\\");
				}
				else if (codePoint == '"')
				{
					privateWrite("\\\"");
				}
				else if (codePoint < 128)
				{
					// Even though Writer doesn't work on general code points,
					// we have just proven that the code point is ASCII, so this
					// is fine.
					privateWrite(codePoint);
				}
				else if (Character.isSupplementaryCodePoint(codePoint))
				{
					// Supplementary code points need to be written as two
					// Unicode escapes.
					privateWrite(String.format(
						"\\u%04X\\u%04X",
						(int) Character.highSurrogate(codePoint),
						(int) Character.lowSurrogate(codePoint)));
				}
				else
				{
					// Force all non-ASCII characters to Unicode escape
					// sequences.
					privateWrite(String.format("\\u%04X", codePoint));
				}
			}
			privateWrite('"');
			stack.addFirst(state.nextStateAfterValue());
		}
	}

	/**
	 * Write the specified {@link String} to the underlying document {@linkplain
	 * Writer writer} as a JSON string. All non-ASCII characters are encoded as
	 * Unicode escape sequences, so only ASCII characters will be written.
	 *
	 * @param value
	 *        A {@code String} (may be {@code null}).
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an arbitrary value cannot be written.
	 */
	public void write (final @Nullable String value)
	{
		if (value == null)
		{
			writeNull();
		}
		else
		{
			format("%s", value);
		}
	}

	/**
	 * Write the contents of the specified {@link JSONWriter} to the underlying
	 * document as a JSON value. This is only permitted whenever an arbitrary
	 * JSON value would be permitted.
	 *
	 * @param other
	 *        Another {@code JSONWriter}.
	 */
	public void write (final JSONWriter other)
	{
		final JSONState state = stack.removeFirst();
		state.checkCanWriteAnyValue();
		state.writePrologueTo(this);
		privateWrite(other.toString());
		stack.addFirst(state.nextStateAfterValue());
	}

	/**
	 * Write the specified {@linkplain JSONFriendly JSON-friendly} value to the
	 * underlying document {@linkplain Writer writer}.
	 *
	 * @param friendly
	 *        A {@link JSONFriendly} value.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an arbitrary value cannot be written.
	 */
	public void write (final JSONFriendly friendly)
	{
		friendly.writeTo(this);
	}

	/**
	 * Write an object beginning to the underlying document {@linkplain Writer
	 * writer}.
	 *
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an object beginning cannot be written.
	 */
	public void startObject () throws JSONIOException
	{
		final JSONState state = stack.peekFirst();
		state.checkCanWriteObjectStart();
		state.writePrologueTo(this);
		privateWrite('{');
		stack.addFirst(EXPECTING_FIRST_OBJECT_KEY_OR_OBJECT_END);
	}

	/**
	 * Write an object ending to the underlying document {@linkplain Writer
	 * writer}.
	 *
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an object ending cannot be written.
	 */
	public void endObject () throws JSONIOException, IllegalStateException
	{
		final JSONState state = stack.removeFirst();
		state.checkCanWriteObjectEnd();
		privateWrite('}');
		stack.addFirst(stack.removeFirst().nextStateAfterValue());
	}

	/**
	 * Write an object, using {@linkplain Continuation0 action} to supply the
	 * contents.
	 *
	 * @param action
	 *        An action that writes the contents of an object.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an object cannot be written.
	 */
	public void writeObject (final Continuation0 action)
		throws JSONIOException, IllegalStateException
	{
		startObject();
		action.value();
		endObject();
	}

	/**
	 * Write an array beginning to the underlying document {@linkplain Writer
	 * writer}.
	 *
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an array beginning cannot be written.
	 */
	public void startArray () throws JSONIOException, IllegalStateException
	{
		final JSONState state = stack.peekFirst();
		state.checkCanWriteArrayStart();
		state.writePrologueTo(this);
		privateWrite('[');
		stack.addFirst(EXPECTING_FIRST_VALUE_OR_ARRAY_END);
	}
	/**
	 * Write an array ending to the underlying document {@linkplain Writer
	 * writer}.
	 *
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an array ending cannot be written.
	 */
	public void endArray () throws JSONIOException, IllegalStateException
	{
		final JSONState state = stack.removeFirst();
		state.checkCanWriteArrayEnd();
		privateWrite(']');
		stack.addFirst(stack.removeFirst().nextStateAfterValue());
	}

	/**
	 * Write an array, using {@linkplain Continuation0 action} to supply the
	 * contents.
	 *
	 * @param action
	 *        An action that writes the contents of an array.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an array cannot be written.
	 */
	public void writeArray (final Continuation0 action)
		throws JSONIOException, IllegalStateException
	{
		startArray();
		action.value();
		endArray();
	}

	/**
	 * Write an array of {@code boolean} values.
	 *
	 * @param values
	 *        An array of {@code boolean} values.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an array cannot be written.
	 */
	public void writeArray (final boolean... values)
		throws JSONIOException, IllegalStateException
	{
		startArray();
		for (final boolean value : values)
		{
			write(value);
		}
		endArray();
	}

	/**
	 * Write an array of {@code int} values.
	 *
	 * @param values
	 *        An array of {@code int} values.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an array cannot be written.
	 */
	public void writeArray (final int... values)
		throws JSONIOException, IllegalStateException
	{
		startArray();
		for (final int value : values)
		{
			write(value);
		}
		endArray();
	}

	/**
	 * Write an array of {@code long} values.
	 *
	 * @param values
	 *        An array of {@code long} values.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an array cannot be written.
	 */
	public void writeArray (final long... values)
		throws JSONIOException, IllegalStateException
	{
		startArray();
		for (final long value : values)
		{
			write(value);
		}
		endArray();
	}

	/**
	 * Write an array of {@code float} values.
	 *
	 * @param values
	 *        An array of {@code float} values.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an array cannot be written.
	 */
	public void writeArray (final float... values)
		throws JSONIOException, IllegalStateException
	{
		startArray();
		for (final float value : values)
		{
			write(value);
		}
		endArray();
	}

	/**
	 * Write an array of {@code double} values.
	 *
	 * @param values
	 *        An array of {@code double} values.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an array cannot be written.
	 */
	public void writeArray (final double... values)
		throws JSONIOException, IllegalStateException
	{
		startArray();
		for (final double value : values)
		{
			write(value);
		}
		endArray();
	}

	/**
	 * Write an array of {@link String} values.
	 *
	 * @param values
	 *        An array of {@code String} values.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an array cannot be written.
	 */
	public void writeArray (final String... values)
		throws JSONIOException, IllegalStateException
	{
		startArray();
		for (final String value : values)
		{
			write(value);
		}
		endArray();
	}

	/**
	 * Write an array of {@link JSONFriendly JSON-friendly} values.
	 *
	 * @param values
	 *        An array of JSON-friendly values.
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 * @throws IllegalStateException
	 *         If an array cannot be written.
	 */
	public void writeArray (final JSONFriendly... values)
		 throws JSONIOException, IllegalStateException
	{
		startArray();
		for (final JSONFriendly value : values)
		{
			write(value);
		}
		endArray();
	}

	/**
	 * Flush any buffered data to the underlying document {@linkplain Writer
	 * writer}.
	 *
	 * @throws JSONIOException
	 *         If an I/O exception occurs.
	 */
	public void flush () throws JSONIOException
	{
		try
		{
			writer.flush();
		}
		catch (final IOException e)
		{
			throw new JSONIOException(e);
		}
	}

	@Override
	public void close () throws JSONIOException, IllegalStateException
	{
		// Don't actually remove the remaining state; we want any errant
		// subsequent operations to fail appropriately, not because the stack is
		// empty.
		final JSONState state = stack.peekFirst();
		state.checkCanEndDocument();
		assert stack.size() == 1;
		flush();
		try
		{
			writer.close();
		}
		catch (final IOException e)
		{
			throw new JSONIOException(e);
		}
	}

	@Override
	public String toString () throws IllegalStateException
	{
		try
		{
			final JSONState state = stack.peekFirst();
			state.checkCanEndDocument();
			return writer.toString();
		}
		catch (final @Nonnull Throwable e)
		{
			// Do not allow an exception of any stripe to derail the
			// stringification operation.
			return String.format(
				"BROKEN: %s <%s>: %s",
				e.getClass().getName(),
				e.getMessage(),
				writer.toString());
		}
	}
}
