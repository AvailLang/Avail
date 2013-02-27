/**
 * RenamesFileParser.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.builder;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import com.avail.annotations.*;
import com.avail.descriptor.ModuleDescriptor;

/**
 * A {@code RenamesFileParser} parses a {@linkplain File file} of Avail
 * {@linkplain ModuleDescriptor module} renaming rules and answers a
 * {@linkplain Map map}.
 *
 * <p>The format of the renames file is specified by the following
 * simple grammar:</p>
 *
 * <pre>
 * renamesFile ::= renameRule* ;
 * renameRule ::= quotedModulePath "->" quotedModulePath ;
 * quotedModulePath ::= '"' modulePath '"' ;
 * modulePath ::= moduleId ++ "/" ;
 * moduleId ::= [^/"]+ ;
 * </pre>
 *
 * <p>Conceptually the renames file establishes a set of module reference
 * renaming rules. On the left-hand side of a rule is a fully-qualified module
 * reference (<em>quotedModulePath</em>) of the form
 * <strong>"/R/X/.../Y/Z"</strong>, where <strong>R</strong> is a root name
 * referring to a vendor, <strong>X</strong> is a public package provided
 * by the vendor, <strong>Y</strong> is a package recursively within module
 * group <strong>X</strong>, and <strong>Z</strong> is a local module name. On
 * the right-hand side of a rule is another module reference of the form
 * <strong>"/Q/A/.../B/C"</strong>, where <strong>Q</strong> is a root name
 * referring to a vendor, <strong>A</strong> is a public package provided
 * by the vendor, <strong>B</strong> is a package recursively within module
 * group <strong>A</strong>, and <strong>C</strong> is a local module name.</p>
 *
 * <p>Note that some operating systems may have difficulty resolving certain
 * <em>moduleId</em>s if they contain arbitrary Unicode characters.</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class RenamesFileParser
{
	/**
	 * Build the source text of a renames file from the specified rules.
	 *
	 * @param rules
	 *        An array of rules, where each rule is a 2-element {@linkplain
	 *        String string} array whose first element is an Avail {@linkplain
	 *        ModuleDescriptor module} path and whose second element is a
	 *        logical file path.
	 * @return The source text of a renames file that declares the specified
	 *         rules.
	 */
	@ThreadSafe
	public static String renamesFileFromRules (
		final String[][] rules)
	{
		final StringBuilder builder = new StringBuilder(100);
		builder.append("/* Generated by ");
		builder.append(RenamesFileParser.class.getCanonicalName());
		builder.append(".renameFileFromRules() */\n");
		for (final String[] rule : rules)
		{
			builder.append('"');
			builder.append(rule[0]);
			builder.append("\" -> \"");
			builder.append(rule[1]);
			builder.append("\"\n");
		}
		return builder.toString();
	}

	/**
	 * The {@linkplain Reader reader} responsible for fetching {@linkplain
	 * Token tokens}.
	 */
	private final Reader reader;

	/** The Avail {@linkplain ModuleRoots module roots}. */
	private final ModuleRoots roots;

	/**
	 * Construct a new {@link RenamesFileParser}.
	 *
	 * @param reader
	 *        The {@linkplain Reader reader} responsible for fetching
	 *        {@linkplain Token tokens}.
	 * @param roots
	 *        The Avail {@linkplain ModuleRoots module roots}.
	 */
	@ThreadSafe
	public RenamesFileParser (
		final Reader reader,
		final ModuleRoots roots)
	{
		this.reader = reader;
		this.roots  = roots;
	}

	/**
	 * The types of the {@linkplain Token tokens}.
	 */
	private static enum TokenType
	{
		/**
		 * A {@linkplain ModuleDescriptor module} or {@linkplain
		 * File file} path.
		 */
		PATH,

		/** An arrow (->). */
		ARROW,

		/** An insignificant token. */
		UNKNOWN,

		/** End of file. */
		EOF;
	}

	/**
	 * A {@code Token} associates a {@link TokenType} with a {@linkplain String
	 * lexeme} from the source text of the renames file.
	 */
	private static class Token
	{
		/** The {@link TokenType}. */
		final TokenType tokenType;

		/** The {@linkplain String lexeme}. */
		final String lexeme;

		/**
		 * Construct a new {@link Token}.
		 *
		 * @param tokenType The {@link TokenType}.
		 * @param lexeme The {@linkplain String lexeme}.
		 */
		Token (final TokenType tokenType, final String lexeme)
		{
			this.tokenType = tokenType;
			this.lexeme    = lexeme;
		}
	}

	/**
	 * Has the scanner read the entire source text?
	 *
	 * @return {@code true} if the scanner has read the entire {@linkplain
	 *         Reader stream} of source text, {@code false} otherwise.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private boolean atEnd () throws IOException
	{
		reader.mark(1);
		final int next = reader.read();
		reader.reset();
		return next == -1;
	}

	/**
	 * Answer and consume the next character from the {@linkplain #reader}.
	 *
	 * @return The next character from the {@linkplain #reader}.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private char nextCharacter () throws IOException
	{
		final int next = reader.read();
		assert next != -1;
		return (char) next;
	}

	/**
	 * Answer (but don't consume) the next character from the {@linkplain
	 * #reader}.
	 *
	 * @return The next character from the {@linkplain #reader}.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private char peekCharacter () throws IOException
	{
		reader.mark(1);
		final int next = reader.read();
		assert next != -1;
		reader.reset();
		return (char) next;
	}

	/**
	 * Peek for the specified character. If the next character from the
	 * {@linkplain #reader} matches, then consume it and answer {@code true}.
	 *
	 * @param c A character.
	 * @return {@code true} if the next character from the {@linkplain #reader}
	 *         matches the specified character, {@code false} otherwise.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private boolean peekFor (final char c) throws IOException
	{
		if (atEnd())
		{
			return false;
		}

		if (peekCharacter() != c)
		{
			return false;
		}

		nextCharacter();
		return true;
	}

	/**
	 * Answer a {@linkplain Token token} whose lexeme began with a double-quote
	 * (").
	 *
	 * @return A {@linkplain Token token}.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} or unexpected
	 *         end-of-file occurs.
	 */
	Token scanDoubleQuote () throws IOException
	{
		if (atEnd())
		{
			return new Token(TokenType.UNKNOWN, "\"");
		}

		final StringBuilder builder = new StringBuilder(50);
		while (true)
		{
			if (atEnd())
			{
				throw new EOFException(
					"Expected close quote to correspond with open quote "
					+ "but found end-of-file");
			}
			if (peekFor('"'))
			{
				return new Token(TokenType.PATH, builder.toString());
			}
			builder.append(nextCharacter());
		}
	}

	/**
	 * Answer a {@linkplain Token token} whose lexeme began with a hyphen (-).
	 *
	 * @return A {@linkplain Token token}.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} or unexpected
	 *         end-of-file occurs.
	 */
	Token scanHyphen () throws IOException
	{
		if (peekFor('>'))
		{
			return new Token(TokenType.ARROW, "->");
		}

		return new Token(TokenType.UNKNOWN, "-");
	}

	/**
	 * Answer a {@linkplain Token token} whose lexeme began with a slash (/).
	 *
	 * @return A {@linkplain Token token} or {@code null}.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} or unexpected
	 *         end-of-file occurs.
	 */
	@Nullable Token scanSlash () throws IOException
	{
		if (!peekFor('*'))
		{
			return new Token(TokenType.UNKNOWN, "/");
		}

		int depth = 1;
		while (true)
		{
			if (atEnd())
			{
				throw new EOFException(
					"Expected close comment to correspond with open comment "
					+ "but found end-of-file");
			}

			if (peekFor('/') && peekFor('*'))
			{
				depth++;
			}
			else if (peekFor('*') && peekFor('/'))
			{
				depth--;
			}
			else
			{
				nextCharacter();
			}

			if (depth == 0)
			{
				return null;
			}
		}
	}

	/**
	 * Consume whitespace.
	 *
	 * @return {@code null}.
	 */
	@Nullable Token scanWhitespace ()
	{
		return null;
	}

	/**
	 * Answer a {@linkplain Token token} whose lexeme is the specified
	 * character of unknown significance.
	 *
	 * @param unknownChar A character.
	 * @return A {@linkplain Token token}.
	 */
	Token scanUnknown (final char unknownChar)
	{
		return new Token(TokenType.UNKNOWN, Character.toString(unknownChar));
	}

	/**
	 * A {@code ScannerAction} attempts to read a {@linkplain Token token} from
	 * the {@linkplain #reader}.
	 */
	private static enum ScannerAction
	{
		/** A double quote (") was just seen. */
		DOUBLE_QUOTE
		{
			@Override
			Token scan (
					final RenamesFileParser parser,
					final char firstChar)
				throws IOException
			{
				return parser.scanDoubleQuote();
			}
		},

		/** A hyphen (-) was just seen. */
		HYPHEN
		{
			@Override
			Token scan (
					final RenamesFileParser parser,
					final char firstChar)
				throws IOException
			{
				return parser.scanHyphen();
			}
		},

		/** A forward slash (/) was just seen. */
		SLASH
		{
			@Override
			@Nullable Token scan (
					final RenamesFileParser parser,
					final char firstChar)
				throws IOException
			{
				return parser.scanSlash();
			}
		},

		/** A whitespace character was just seen. */
		WHITESPACE
		{
			@Override
			@Nullable Token scan (
				final RenamesFileParser parser,
				final char firstChar)
			{
				return parser.scanWhitespace();
			}
		},

		/** A character of unknown significance was just seen. */
		UNKNOWN
		{
			@Override
			Token scan (
					final RenamesFileParser parser,
					final char firstChar)
				throws IOException
			{
				return parser.scanUnknown(firstChar);
			}
		};

		/**
		 * Answer the next {@linkplain Token token} from the {@linkplain
		 * #reader stream}.
		 *
		 * @param parser A {@link RenamesFileParser}.
		 * @param firstChar The character used to select the {@link
		 *                  ScannerAction}.
		 * @return A {@linkplain Token token}.
		 * @throws IOException
		 *         If the scanner encounters an error while trying to scan for
		 *         the next {@linkplain Token token}.
		 */
		abstract @Nullable Token scan (
				RenamesFileParser parser,
				char firstChar)
			throws IOException;
	}

	/**
	 * A map from Unicode code points to the ordinals of the {@link
	 * ScannerAction}s responsible for scanning constructs that begin with them.
	 */
	private static final byte[] scannerTable = new byte[256];

	/*
	 * Initialize the scanner table.
	 */
	static
	{
		for (int i = 0; i < 256; i++)
		{
			final char c = (char) i;
			ScannerAction action;
			if (c == '"')
			{
				action = ScannerAction.DOUBLE_QUOTE;
			}
			else if (c == '-')
			{
				action = ScannerAction.HYPHEN;
			}
			else if (c == '/')
			{
				action = ScannerAction.SLASH;
			}
			else if (Character.isSpaceChar(c) || Character.isWhitespace(c))
			{
				action = ScannerAction.WHITESPACE;
			}
			else
			{
				action = ScannerAction.UNKNOWN;
			}
			scannerTable[i] = (byte) action.ordinal();
		}
	}

	/**
	 * Answer the next {@linkplain Token token}.
	 *
	 * @return A {@linkplain Token token}.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 * @throws RenamesFileParserException
	 *         If an {@linkplain TokenType#UNKNOWN unknown} {@linkplain Token
	 *         token} is scanned.
	 */
	private Token scan ()
		throws IOException, RenamesFileParserException
	{
		while (!atEnd())
		{
			final char c = nextCharacter();
			final Token token =
				ScannerAction.values()[scannerTable[c]].scan(this, c);
			if (token != null)
			{
				if (token.tokenType == TokenType.UNKNOWN)
				{
					throw new RenamesFileParserException(
						"Unknown token (" + token.lexeme + ")");
				}

				return token;
			}
		}

		return new Token(TokenType.EOF, "<EOF>");
	}

	/**
	 * A {@linkplain ModuleNameResolver module name resolver}. The goal of the
	 * {@linkplain RenamesFileParser parser} is to populate the resolver with
	 * renaming rules.
	 */
	private volatile @Nullable ModuleNameResolver resolver;

	/**
	 * Parse a rename rule (<em>renameRule</em>) and install an appropriate
	 * transformation rule into the {@linkplain ModuleNameResolver module
	 * name resolver}.
	 *
	 * @param modulePath A {@linkplain ModuleDescriptor module} path.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception}.
	 * @throws RenamesFileParserException
	 *         If a semantic rule is violated.
	 */
	private void parseRenameRule (final String modulePath)
		throws IOException, RenamesFileParserException
	{
		final Token token = scan();
		if (token.tokenType != TokenType.ARROW)
		{
			throw new RenamesFileParserException(
				"expected -> but found (" + token.lexeme + ")");
		}

		final Token filePath = scan();
		if (filePath.tokenType != TokenType.PATH)
		{
			throw new RenamesFileParserException(
				"expected a file path but found (" + filePath.lexeme + ")");
		}
		if (filePath.lexeme.isEmpty())
		{
			throw new RenamesFileParserException(
				"module path (" + modulePath + ") must not bind an empty "
				+ "file path");
		}

		final ModuleNameResolver theResolver = resolver;
		assert theResolver != null;
		if (theResolver.hasRenameRuleFor(modulePath))
		{
			throw new RenamesFileParserException(
				"duplicate rename rule for \"" + modulePath + "\" "
				+ "is not allowed");
		}
		theResolver.addRenameRule(modulePath, filePath.lexeme);
	}

	/**
	 * Parse a renames file (<em>renamesFile</em>).
	 *
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception}.
	 * @throws RenamesFileParserException
	 *         If a semantic rule is violated.
	 */
	private void parseRenamesFile ()
		throws IOException, RenamesFileParserException
	{
		Token token;
		while ((token = scan()).tokenType == TokenType.PATH)
		{
			if (token.lexeme.isEmpty())
			{
				throw new RenamesFileParserException(
					"module path must not be empty");
			}
			parseRenameRule(token.lexeme);
		}
		if (token.tokenType != TokenType.EOF)
		{
			throw new RenamesFileParserException(
				"expected end of file but found (" + token.lexeme + ")");
		}
	}

	/**
	 * Parse the source text and answer a {@linkplain ModuleNameResolver module
	 * name resolver} with the appropriate renaming rules.
	 *
	 * @return A {@linkplain ModuleNameResolver module name resolver}.
	 * @throws RenamesFileParserException
	 *         If the parse fails for any reason.
	 */
	public ModuleNameResolver parse ()
		throws RenamesFileParserException
	{
		ModuleNameResolver theResolver = resolver;
		if (theResolver == null)
		{
			theResolver = new ModuleNameResolver(roots);
			resolver = theResolver;
			try
			{
				parseRenamesFile();
			}
			catch (final IOException e)
			{
				throw new RenamesFileParserException(e);
			}
		}
		return theResolver;
	}
}
