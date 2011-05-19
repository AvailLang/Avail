/**
 * compiler/scanner/AvailScanner.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.compiler.scanning;

import static com.avail.compiler.scanning.AvailScanner.ScannerAction.*;
import static com.avail.descriptor.AvailObject.error;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.compiler.scanning.TokenDescriptor.TokenType;
import com.avail.descriptor.*;

/**
 * An {@code AvailScanner} converts a stream of characters into a {@link List}
 * of {@link TokenDescriptor tokens}, which are tastier for the compiler.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailScanner
{
	/**
	 * The string being parsed, usually the entire contents of an Avail source
	 * file.
	 */
	String _inputString;

	/**
	 * The current position in the input string.
	 */
	int _position;

	/**
	 * The position of the start of the token currently being parsed.
	 */
	int _startOfToken;

	/**
	 * The position of the start of the token currently being parsed.
	 */
	int _lineNumber;

	/**
	 * The tokens that have been parsed so far.
	 */
	List<AvailObject> _outputTokens;

	/**
	 * Whether to stop scanning after encountering the keyword "Names".
	 */
	boolean _stopAfterNamesToken;

	/**
	 * Whether the "Names" token has already been encountered.  Only set if
	 * {@link #_stopAfterNamesToken} is set.
	 */
	boolean _encounteredNamesToken;


	// private

	/**
	 * Add the (uninitialized) token to my sequence of parsed tokens.  Also set
	 * its:
	 * <ul>
	 * <li>{@link TokenDescriptor#o_Start(AvailObject, int) start},
	 * <li>{@link TokenDescriptor#o_String(AvailObject, AvailObject) string},
	 *     and
	 * <li>{@link TokenDescriptor#o_TokenType(AvailObject, TokenType) token
	 *     type} based on the passed {@link TokenDescriptor.TokenType}.
	 * </ul>
	 *
	 * @param anAvailToken The token to initialize and add.
	 * @param tokenType The {@link TokenDescriptor.TokenType enumeration value}
	 *                  to set in the token.
	 */
	void addToken (
			final AvailObject anAvailToken,
			final TokenDescriptor.TokenType tokenType)
	{
		anAvailToken.tokenType(tokenType);
		anAvailToken.start(_startOfToken);
		anAvailToken.lineNumber(_lineNumber);
		anAvailToken.string(ByteStringDescriptor.from(currentTokenString()));
		anAvailToken.makeImmutable();
		_outputTokens.add(anAvailToken);
	}

	/**
	 * Create an ordinary {@link TokenDescriptor token}, initialize it, and add
	 * it to my sequence of parsed tokens.  In particular, create the token
	 * and set its:
	 * <ul>
	 * <li>{@link TokenDescriptor#o_Start(AvailObject, int) start},
	 * <li>{@link TokenDescriptor#o_String(AvailObject, AvailObject) string}, and
	 * <li>{@link TokenDescriptor#o_TokenType(AvailObject, TokenType) token type}
	 *     based on the passed {@link TokenDescriptor.TokenType}.
	 * </ul>
	 *
	 * @param tokenType The {@link TokenDescriptor.TokenType enumeration value}
	 *                  to set in the token.
	 */
	void addToken (final TokenDescriptor.TokenType tokenType)
	{
		final AvailObject token = TokenDescriptor.mutable().create();
		addToken(token, tokenType);
	}

	/**
	 * Add the provided uninitialized {@link LiteralTokenDescriptor literal
	 * token}.
	 *
	 * @param anAvailObject A {@link LiteralTokenDescriptor literal token}.
	 */
	void addTokenForLiteral (final AvailObject anAvailObject)
	{
		final AvailObject token = LiteralTokenDescriptor.mutable().create();
		token.literal(anAvailObject);
		addToken(token, TokenType.LITERAL);
	}


	/**
	 * An alphabetic was encountered.  Scan a keyword.
	 */
	void scanAlpha ()
	{
		while (peekForLetterOrAlphaNumeric())
		{
			// no body
		}
		final AvailObject token = TokenDescriptor.mutable().create();
		addToken(token, TokenType.KEYWORD);
		if (_stopAfterNamesToken
				&& token.string().asNativeString().equals("Names"))
		{
			_encounteredNamesToken = true;
		}
	}

	/**
	 * A digit was encountered.  Scan a (positive) numeric constant.  The
	 * constant may be an integer, float, or double.
	 */
	void scanDigit ()
	{
		backUp();
		assert _position == _startOfToken;
		while (peekIsDigit())
		{
			next();
		}
		long mantissa;
		boolean isDoublePrecision;
		int decimalExponentPart1;
		int decimalExponent;
		int decimalExponentPart2;
		AvailObject result;
		long twoTo59;
		_position = _startOfToken;
		if (peekFor('d') || peekFor('e') || peekFor('.') && peekIsDigit())
		{
			mantissa = 0;
			decimalExponent = 0;
			twoTo59 = 0x800000000000000L;
			isDoublePrecision = false;
			while (peekIsDigit())
			{
				if (mantissa > twoTo59)
				{
					decimalExponent++;
				}
				else
				{
					mantissa = mantissa * 10 + nextDigitValue();
				}
			}
			if (peekFor('.'))
			{
				next();
				while (peekIsDigit())
				{
					if (mantissa <= twoTo59)
					{
						mantissa = mantissa * 10 + nextDigitValue();
						decimalExponent--;
					}
					else
					{
						next();
					}
				}
			}
			isDoublePrecision = peekFor('d');
			if (isDoublePrecision || peek() == 'e')
			{
				int explicitExponent = 0;
				final boolean negateExponent = peekFor('-');
				while (peekIsDigit()) {
					explicitExponent = explicitExponent * 10 + nextDigitValue();
					if (explicitExponent > 0x2710)
					{
						error("Literal exponent is (way) out of bounds");
						return;
					}
				}
				decimalExponent = negateExponent
					? decimalExponent - explicitExponent
					: decimalExponent + explicitExponent;
			}
			// The mantissa is in the range of a long, so even if 10**e would
			// overflow we know that 10**(e/2) will not (unless the double we're
			// parsing is way out of bounds.
			decimalExponentPart1 = decimalExponent / 2;
			decimalExponentPart2 = decimalExponent - decimalExponentPart1;
			double d = mantissa;
			d *= Math.pow(10, decimalExponentPart1);
			d *= Math.pow(10, decimalExponentPart2);
			result = isDoublePrecision
				? DoubleDescriptor.objectFromDouble(d)
				: FloatDescriptor.objectFromFloat((float)d);
		}
		else
		{
			result = IntegerDescriptor.zero();
			final AvailObject ten = IntegerDescriptor.ten();
			while (peekIsDigit())
			{
				result = result.noFailTimesCanDestroy(ten, true);
				result = result.noFailPlusCanDestroy(
					IntegerDescriptor.fromUnsignedByte(
						nextDigitValue()), true);
			}
		}
		addTokenForLiteral(result);
	}

	/**
	 * Parse double-quoted string literal.
	 */
	void scanDoubleQuote ()
	{

		final StringBuilder stringBuilder = new StringBuilder(40);
		while (true)
		{
			final char c = next();
			if (c == '\"')
			{
				if (!peekFor('\"'))
				{
					final String string = stringBuilder.toString();
					final AvailObject availValue = ByteStringDescriptor.from(string);
					availValue.makeImmutable();
					addTokenForLiteral(availValue);
					return;
				}
			}
			stringBuilder.append(c);
		}
	}

	/**
	 * Operator characters are never grouped.  Instead, method names treat a
	 * string of operator characters as separate pseudo-keywords.
	 */
	void scanOperator ()
	{

		addToken(TokenType.OPERATOR);
	}

	/**
	 * The semicolon is not considered an operator character and cannot be used
	 * within operators.  Parse it by itself as a {@link TokenDescriptor token}
	 * whose type is {@link TokenType#END_OF_STATEMENT}.
	 */
	void scanSemicolon ()
	{
		addToken(TokenType.END_OF_STATEMENT);
	}

	/**
	 * A slash was encountered.  Check if it's the start of a comment, and if
	 * so skip it.  If not, add the slash as a {@link TokenDescriptor token} of
	 * type {@link TokenType#OPERATOR}.
	 * <p>
	 * Support nested comments.
	 */
	void scanSlash ()
	{
		if (!peekFor('*'))
		{
			addToken(TokenType.OPERATOR);
			return;
		}
		int depth = 1;
		while (true)
		{
			if (atEnd())
			{
				error("Expected close comment to correspond with open comment");
				return;
			}
			if (peekFor('/') && peekFor('*'))
			{
				depth++;
			}
			else if (peekFor('*'))
			{
				if (peekFor('/'))
				{
					depth--;
				}
			}
			else
			{
				next();
			}
			if (depth == 0)
			{
				break;
			}
		}
	}

	/**
	 * Scan an unrecognized character.
	 */
	void scanUnknown ()
	{
		error("Unknown character");
		return;
	}

	/**
	 * A whitespace character was encountered.  Just skip it.
	 */
	void scanWhitespace ()
	{
		return;
	}

	/**
	 * The zero-width-nonbreaking-space character was encountered.  This is also
	 * known as the byte-order-mark and generally only appears at the start of
	 * a file as an hint about the file's endianness.
	 * <p>
	 * Treat it as whitespace even though Unicode says it isn't.
	 */
	void scanZeroWidthNonbreakingWhitespace ()
	{
		return;
	}



	/**
	 * Answer whether we have exhausted the input string.
	 *
	 * @return Whether we are finished scanning.
	 */
	boolean atEnd ()
	{
		return _position == _inputString.length();
	}

	/**
	 * Extract a native {@link String} from the input, from the {@link
	 * #_startOfToken} to the current {@link #_position}.
	 *
	 * @return A substring of the input.
	 */
	String currentTokenString ()
	{
		return _inputString.substring(_startOfToken, _position);
	}

	/**
	 * Extract the current character and increment the {@link #_position}.
	 *
	 * @return The consumed character.
	 */
	char next ()
	{
		char c = _inputString.charAt(_position);
		if (c == '\n')
		{
			_lineNumber++;
		}
		_position++;
		return c;
	}

	/**
	 * Extract the value of the next characater, which must be a digit.
	 *
	 * @return The digit's value.
	 */
	byte nextDigitValue ()
	{
		assert peekIsDigit();
		return (byte)(next() - '0');
	}

	/**
	 * Answer the character at the current {@link #_position} of the input.
	 *
	 * @return The current character.
	 */
	char peek ()
	{
		return _inputString.charAt(_position);
	}

	/**
	 * Skip aCharacter if it's next in the input stream.  Answer whether it was
	 * present.
	 *
	 * @param aCharacter The character to look for.
	 * @return Whether the specified character was found and skipped.
	 */
	boolean peekFor (
			final char aCharacter)
	{
		if (atEnd())
		{
			return false;
		}
		if (peek() != aCharacter)
		{
			return false;
		}
		next();
		return true;
	}

	/**
	 * Skip the next character if it's alphanumeric.  Answer whether such a
	 * character was encountered.
	 *
	 * @return Whether an alphanumeric character was encountered and skipped.
	 */
	boolean peekForLetterOrAlphaNumeric ()
	{
		if (!atEnd())
		{
			if (Character.isUnicodeIdentifierPart(peek()))
			{
				next();
				return true;
			}
		}
		return false;
	}

	/**
	 * Answer whether the current character is a digit.
	 *
	 * @return Whether the current character is a digit.
	 */
	boolean peekIsDigit ()
	{
		if (!atEnd())
		{
			return Character.isDigit(peek());
		}
		return false;
	}

	/**
	 * Move the current {@link #_position} back by one character.
	 */
	void backUp ()
	{
		_position--;
		assert 0 <= _position && _position <= _inputString.length();
	}

	/**
	 * An enumeration of actions to be performed based on the next character
	 * encountered.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	enum ScannerAction
	{
		/**
		 * A digit indicates a numeric constant.
		 */
		DIGIT ()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.scanDigit();
			}
		},
		/**
		 * A double quote portends a string constant.
		 */
		DOUBLE_QUOTE ()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.scanDoubleQuote();
			}
		},
		/**
		 * Keywords start this way.
		 */
		IDENTIFIER_START ()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.scanAlpha();
			}
		},
		/**
		 * This is the end of a statement.
		 */
		SEMICOLON ()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.scanSemicolon();
			}
		},
		/**
		 * A slash can act as an ordinary operator or may be the start of a
		 * slash-star-content-star-slash comment.
		 */
		SLASH ()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.scanSlash();
			}
		},
		/**
		 * An unknown character.
		 */
		UNKNOWN ()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.scanUnknown();
			}
		},
		/**
		 * Whitespace can be ignored.
		 */
		WHITESPACE ()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.scanWhitespace();
			}
		},
		/**
		 * We skip the zero-width-non-breaking-space (also known as the
		 * byte-order-mark).
		 */
		ZEROWIDTHWHITESPACE ()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.scanZeroWidthNonbreakingWhitespace();
			}
		},
		/**
		 * An operator character, such a + or *.
		 */
		OPERATOR ()
		{
			@Override
			void scan (final AvailScanner scanner)
			{
				scanner.scanOperator();
			}
		};

		/**
		 * Process the current character.
		 *
		 * @param scanner The scanner processing this character.
		 */
		abstract void scan (AvailScanner scanner);
	}

	/**
	 * Answer the {@linkplain List list} of {@linkplain TokenDescriptor tokens}
	 * that comprise a {@linkplain ModuleDescriptor module}.
	 *
	 * @param string The text of an Avail {@linkplain ModuleDescriptor
	 *               module} (or at least the prefix up to the <em>Names</em>
	 *               token).
	 * @param stopAfterNamesToken
	 *        Stop scanning after encountering the <em>Names</em> token?
	 * @return A {@linkplain List list} of {@linkplain TokenDescriptor tokens}
	 *         terminated by a token of type {@link TokenType#END_OF_FILE}.
	 */
	public @NotNull List<AvailObject> scanString (
		final @NotNull String string,
		final boolean stopAfterNamesToken)
	{
		_inputString = string;
		_position = 0;
		_lineNumber = 1;
		_outputTokens = new ArrayList<AvailObject>(100);
		_stopAfterNamesToken = stopAfterNamesToken;
		while (!(stopAfterNamesToken ? _encounteredNamesToken : atEnd()))
		{
			_startOfToken = _position;
			final int c = next();
			values()[DispatchTable[c]].scan(this);
		}
		addToken(TokenType.END_OF_FILE);
		return _outputTokens;
	}

	/**
	 * Answer whether the passed character can appear as an Avail operator.
	 *
	 * @param c The character to test.
	 * @return Whether it's a valid operator character.
	 */
	public static boolean isOperatorCharacter (final char c)
	{
		return DispatchTable[c] == (byte)OPERATOR.ordinal();
	}

	/**
	 * A table whose indices are Unicode characters (up to 65535) and whose
	 * values are {@link AvailScanner.ScannerAction scanner actions}.
	 */
	static byte[] DispatchTable = new byte [65536];

	/**
	 * Statically initialize the {@link DispatchTable} with suitable {@link
	 * AvailScanner.ScannerAction scanner actions}.  Note that this happens as
	 * part of class loading.
	 */
	static
	{
		for (int i = 0; i < 65536; i++)
		{
			final char c = (char) i;
			AvailScanner.ScannerAction action;
			if (Character.isDigit(c))
			{
				action = DIGIT;
			}
			else if (c == '\"')
			{
				action = DOUBLE_QUOTE;
			}
			else if (Character.isUnicodeIdentifierStart(c))
			{
				action = IDENTIFIER_START;
			}
			else if (c == ';')
			{
				action = SEMICOLON;
			}
			else if (c == '/')
			{
				action = SLASH;
			}
			else if (!Character.isSpaceChar(c)
				&& !Character.isWhitespace(c)
				&& (c < 32 || c > 126 && c < 160 || c >= 65534))
			{
				action = UNKNOWN;
			}
			else if (Character.isSpaceChar(c) || Character.isWhitespace(c))
			{
				action = WHITESPACE;
			}
			else if (c == '\ufeff')
			{
				action = ZEROWIDTHWHITESPACE;
			}
			else
			{
				action = OPERATOR;
			}
			DispatchTable[i] = (byte)action.ordinal();
		}
	}
}
