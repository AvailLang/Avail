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

package com.avail.compiler.scanner;

import com.avail.annotations.NotNull;
import com.avail.compiler.scanner.AvailEndOfStatementToken;
import com.avail.compiler.scanner.AvailKeywordToken;
import com.avail.compiler.scanner.AvailLiteralToken;
import com.avail.compiler.scanner.AvailOperatorToken;
import com.avail.compiler.scanner.AvailToken;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ByteStringDescriptor;
import com.avail.descriptor.DoubleDescriptor;
import com.avail.descriptor.FloatDescriptor;
import com.avail.descriptor.IntegerDescriptor;
import java.util.ArrayList;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

public class AvailScanner
{
	String _inputString;
	int _position;
	int _startOfToken;
	List<AvailToken> _outputTokens;
	boolean _encounteredNamesToken;

	// private

	void addToken (
			final AvailToken anAvailToken)
	{
		anAvailToken.start(_startOfToken);
		anAvailToken.string(currentTokenString());
		_outputTokens.add(anAvailToken);
	}

	void addTokenForLiteral (
			final AvailObject anAvailObject)
	{
		final AvailLiteralToken token = new AvailLiteralToken();
		token.literal(anAvailObject);
		addToken(token);
	}



	// private dispatch

	void scanAlpha ()
	{
		while (peekForLetterOrAlphaNumeric())
		{
			// no body
		}
		final AvailKeywordToken token = new AvailKeywordToken();
		addToken(token);
		if (token.string().equals("Names"))
		{
			_encounteredNamesToken = true;
		}
	}

	void scanDigit ()
	{
		//  Parse an integer, float, or double literal.

		skip(-1);
		assert (position() == _startOfToken);
		while (peekIsDigit())
			next();
		long mantissa;
		boolean isDoublePrecision;
		int decimalExponentPart1;
		int decimalExponent;
		int decimalExponentPart2;
		AvailObject result;
		long twoTo59;
		position(_startOfToken);
		if ((peekFor('d') || (peekFor('e') || (peekFor('.') && peekIsDigit()))))
		{
			mantissa = 0;
			decimalExponent = 0;
			twoTo59 = 0x800000000000000L;
			isDoublePrecision = false;
			while (peekIsDigit())
				if (mantissa > twoTo59)
				{
					decimalExponent++;
				}
				else
				{
					mantissa = ((mantissa * 10) + nextDigitValue());
				}
			if (peekFor('.'))
			{
				next();
				while (peekIsDigit())
					if (mantissa <= twoTo59)
					{
						mantissa = ((mantissa * 10) + nextDigitValue());
						decimalExponent--;
					}
					else
					{
						next();
					}
			}
			isDoublePrecision = peekFor('d');
			if ((isDoublePrecision || (peek() == 'e')))
			{
				int explicitExponent = 0;
				final boolean negateExponent = peekFor('-');
				while (peekIsDigit()) {
					explicitExponent = ((explicitExponent * 10) + nextDigitValue());
					if (explicitExponent > 0x2710)
					{
						error("Literal exponent is (way) out of bounds");
						return;
					}
				}
				decimalExponent = (negateExponent ? (decimalExponent - explicitExponent) : (decimalExponent + explicitExponent));
			}
			//  The mantissa is in the range of a long, so even if 10**e would overflow we know
			//  that 10**(e/2) will not (unless the double we're parsing is way out of bounds).
			decimalExponentPart1 = decimalExponent / 2;
			decimalExponentPart2 = decimalExponent - decimalExponentPart1;
			double d;
			if (true)
			{
				d = mantissa;
				d *= Math.pow(10, decimalExponentPart1);
				d *= Math.pow(10, decimalExponentPart2);
				result = isDoublePrecision
					? DoubleDescriptor.objectFromDouble(d)
					: FloatDescriptor.objectFromFloat((float)d);
			}
		}
		else
		{
			result = IntegerDescriptor.zero();
			final AvailObject ten = IntegerDescriptor.objectFromByte(((byte)(10)));
			while (peekIsDigit()) {
				result = result.timesCanDestroy(ten, true);
				result = result.plusCanDestroy(IntegerDescriptor.objectFromByte(nextDigitValue()), true);
			}
		}
		addTokenForLiteral(result);
	}

	void scanDoubleQuote ()
	{
		//  Parse double-quoted string literal.

		StringBuilder stringBuilder = new StringBuilder(40);
		while (true)
		{
			char c = next();
			if (c == '\"')
			{
				if (!peekFor('\"'))
				{
					String string = stringBuilder.toString();
					AvailObject availValue = ByteStringDescriptor.mutableObjectFromNativeString(string);
					availValue.makeImmutable();
					addTokenForLiteral(availValue);
					return;
				}
			}
			stringBuilder.append(c);
		}
	}

	void scanOperator ()
	{
		//  Operator characters are never grouped.  Instead, method names treat a
		//  string of operator characters as separate pseudo-keywords.

		addToken(new AvailOperatorToken());
	}

	void scanSemicolon ()
	{
		addToken(new AvailEndOfStatementToken());
	}

	void scanSlash ()
	{
		//  Check to see if this is the start of a comment.  Otherwise it's just the
		//  usual slash operator.  Supports nested comments.

		if (!peekFor('*'))
		{
			addToken(new AvailOperatorToken());
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
			if ((peekFor('/') && peekFor('*')))
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

	void scanUnknown ()
	{
		error("Unknown character");
		return;
	}

	void scanWhitespace ()
	{
		//  Do nothing.  The whitespace character was eaten.

		return;
	}

	void scanZeroWidthNonbreakingWhitespace ()
	{
		//  Do nothing.  The whitespace character was eaten.

		return;
	}



	// private scanning

	boolean atEnd ()
	{
		return _position == _inputString.length();
	}

	String currentTokenString ()
	{
		return _inputString.substring(_startOfToken, _position);
	}

	char next ()
	{
		_position++;
		return _inputString.charAt(_position-1);
	}

	byte nextDigitValue ()
	{
		assert peekIsDigit();
		return (byte)(next() - '0');
	}

	char peek ()
	{
		return _inputString.charAt(_position);
	}

	boolean peekFor (
			final char aCharacter)
	{
		//  Skip aCharacter if it's next in the input stream.  Answer whether it was present.

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

	boolean peekIsDigit ()
	{
		return Character.isDigit(peek());
	}

	int position ()
	{
		return _position;
	}

	void position (
			final int anInteger)
	{
		_position = anInteger;
	}

	void skip (
			final int anInteger)
	{
		_position += anInteger;
		assert 0 <= _position && _position <= _inputString.length();
	}

	/**
	 * Answer the {@linkplain List list} of {@linkplain AvailToken tokens} that
	 * comprise a {@linkplain ModuleDescriptor module}.
	 * 
	 * @param string The text of an Avail {@linkplain ModuleDescriptor
	 *               module} (or at least the prefix up to the <em>Names</em>
	 *               token).
	 * @param stopAfterNamesToken
	 *        Stop scanning after encountering the <em>Names</em> token?
	 * @return A {@linkplain List list} of {@linkplain AvailToken tokens}
	 *         terminated by {@link AvailEndOfFileToken}.
	 */
	public @NotNull List<AvailToken> scanString (
		final @NotNull String string,
		final boolean stopAfterNamesToken)
	{
		_inputString = string;
		_position = 0;
		_outputTokens = new ArrayList<AvailToken>(100);
		while (!(stopAfterNamesToken ? _encounteredNamesToken : atEnd()))
		{
			_startOfToken = position();
			int c = next();
			ScannerAction.values()[DispatchTable[c]].scan(this);
		}
		addToken(new AvailEndOfFileToken());
		return _outputTokens;
	}

	enum ScannerAction
	{
		DIGIT ()
		{
			@Override
			void scan (AvailScanner scanner)
			{
				scanner.scanDigit();
			}
		},
		DOUBLE_QUOTE ()
		{
			@Override
			void scan (AvailScanner scanner)
			{
				scanner.scanDoubleQuote();
			}
		},
		IDENTIFIER_START ()
		{
			@Override
			void scan (AvailScanner scanner)
			{
				scanner.scanAlpha();
			}
		},
		SEMICOLON ()
		{
			@Override
			void scan (AvailScanner scanner)
			{
				scanner.scanSemicolon();
			}
		},
		SLASH ()
		{
			@Override
			void scan (AvailScanner scanner)
			{
				scanner.scanSlash();
			}
		},
		UNKNOWN ()
		{
			@Override
			void scan (AvailScanner scanner)
			{
				scanner.scanUnknown();
			}
		},
		WHITESPACE ()
		{
			@Override
			void scan (AvailScanner scanner)
			{
				scanner.scanWhitespace();
			}
		},
		ZEROWIDTHWHITESPACE ()
		{
			@Override
			void scan (AvailScanner scanner)
			{
				scanner.scanZeroWidthNonbreakingWhitespace();
			}
		},
		OPERATOR ()
		{
			@Override
			void scan (AvailScanner scanner)
			{
				scanner.scanOperator();
			}
		};

		abstract void scan (AvailScanner scanner);
	}

	public static boolean isOperatorCharacter (char c)
	{
		return DispatchTable[c] == (byte)ScannerAction.OPERATOR.ordinal();
	}

	static byte[] DispatchTable = new byte [65536];

	// Static initialization
	static
	{
		for (int i = 0; i < 65536; i++)
		{
			char c = (char) i;
			ScannerAction action;
			if (Character.isDigit(c))
			{
				action = ScannerAction.DIGIT;
			}
			else if (c == '\"')
			{
				action = ScannerAction.DOUBLE_QUOTE;
			}
			else if (Character.isUnicodeIdentifierStart(c))
			{
				action = ScannerAction.IDENTIFIER_START;
			}
			else if (c == ';')
			{
				action = ScannerAction.SEMICOLON;
			}
			else if (c == '/')
			{
				action = ScannerAction.SLASH;
			}
			else if (!Character.isSpaceChar(c)
				&& !Character.isWhitespace(c)
				&& (c < 32 || (c > 126 && c < 160) || (c >= 65534)))
			{
				action = ScannerAction.UNKNOWN;
			}
			else if (Character.isSpaceChar(c) || Character.isWhitespace(c))
			{
				action = ScannerAction.WHITESPACE;
			}
			else if (c == '\ufeff')
			{
				action = ScannerAction.ZEROWIDTHWHITESPACE;
			}
			else
			{
				action = ScannerAction.OPERATOR;
			}
			DispatchTable[i] = (byte)action.ordinal();
		}
	}

}
