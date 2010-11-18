/**
 * com.avail.compiler/RenamesFileParser.java
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

package com.avail.compiler;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Set;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailModuleDescriptor;

/**
 * A {@code RenamesFileParser} parses a {@linkplain File file} of Avail
 * {@linkplain AvailModuleDescriptor module} renaming rules and answers a
 * {@linkplain Map map}.
 * 
 * <p>The format of the renames file is specified by the following
 * simple grammar:</p>
 * 
 * <pre>
 * renamesFile ::= renameRule* ;
 * renameRule ::= quotedModulePath "->" quotedFilePath ;
 * quotedModulePath ::= '"' modulePath '"' ;
 * quotedFilePath ::= '"' filePath '"' ;
 * modulePath ::= moduleId ++ "/" ;
 * filePath ::= moduleId ++ "/" ;
 * moduleId ::= [^/"]+ ;
 * </pre>
 * 
 * <p>Conceptually the renames file establishes a set of module reference
 * renaming rules. On the left-hand side of a rule is a module reference
 * (<em>quotedModulePath</em>) of the form "/X/.../Y/Z", where
 * <strong>X</strong> is a public module group available on the Avail module
 * path, <strong>Y</strong> is a module group recursively within module
 * group <strong>X</strong>, and <strong>Z</strong> is a local module name.
 * On the right-hand side of a rule is a file reference
 * (<em>quotedFilePath</em>) of the form "/A/B", where
 * <strong>A.avail</strong> is a directory on the Avail module path and
 * <strong>B.avail</strong> is a file or subdirectory of
 * <strong>A.avail</strong>. The rule resolves references to
 * <strong>Z</strong> within module group <strong>Y</strong> to the module
 * at <strong>/A.avail/B.avail</strong> or the module group representative
 * <strong>/A.avail/B.avail/Main.avail</strong>.</p>
 * 
 * <p>Note that some operating systems may have difficulty handling certain
 * <em>moduleId</em>s if they contain arbitrary Unicode characters.</p>
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class RenamesFileParser
{
	/**
	 * The {@linkplain File file} of {@linkplain AvailModuleDescriptor module}
	 * rename rules.
	 */
	private final @NotNull File renamesFile;
	
	/**
	 * The {@linkplain File root directories} of the Avail {@linkplain
	 * AvailModuleDescriptor module} path.
	 */
	private final @NotNull Set<File> roots;
	
	/**
	 * Construct a new {@link RenamesFileParser}.
	 *
	 * @param renamesFile
	 *        The {@linkplain File file} of {@linkplain AvailModuleDescriptor
	 *        module} rename rules.
	 * @param roots
	 *        The {@linkplain File root directories} of the Avail {@linkplain
	 *        AvailModuleDescriptor module} path.
	 */
	public RenamesFileParser (
		final @NotNull File renamesFile,
		final @NotNull Set<File> roots)
	{
		this.renamesFile = renamesFile;
		this.roots       = roots;
	}
	
	/**
	 * The types of the {@linkplain Token tokens}.
	 */
	private static enum TokenType
	{
		/**
		 * A {@linkplain AvailModuleDescriptor module} or {@linkplain
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
	 * lexeme} from the text of the {@linkplain #renamesFile renames file}. 
	 */
	private static class Token
	{
		/** The {@link TokenType}. */
		final @NotNull TokenType tokenType;
		
		/** The {@linkplain String lexeme}. */
		final @NotNull String lexeme;
		
		/**
		 * Construct a new {@link Token}.
		 *
		 * @param tokenType The {@link TokenType}.
		 * @param lexeme The {@linkplain String lexeme}.
		 */
		Token (final @NotNull TokenType tokenType, final @NotNull String lexeme)
		{
			this.tokenType = tokenType;
			this.lexeme    = lexeme;
		}
	}
	
	/**
	 * The {@linkplain Reader reader} responsible for fetching {@linkplain
	 * Token tokens} from the underlying {@linkplain #renamesFile renames file}.
	 */
	private Reader reader;
	
	/**
	 * Has the scanner read the entire {@linkplain #renamesFile renames file}?
	 * 
	 * @return {@code true} if the scanner has read the entire {@linkplain
	 *         #renamesFile renames file}, {@code false} otherwise.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private boolean atEnd () throws IOException
	{
		reader.mark(1);
		int next = reader.read();
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
		int next = reader.read();
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
		int next = reader.read();
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
	@NotNull Token scanDoubleQuote () throws IOException
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
	@NotNull Token scanHyphen () throws IOException
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
	Token scanSlash () throws IOException
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
	Token scanWhitespace ()
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
	@NotNull Token scanUnknown (final char unknownChar)
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
			@NotNull Token scan (
					final @NotNull RenamesFileParser parser,
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
			@NotNull Token scan (
					final @NotNull RenamesFileParser parser,
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
			Token scan (
					final @NotNull RenamesFileParser parser,
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
			Token scan (
				final @NotNull RenamesFileParser parser,
				final char firstChar)
			{
				return parser.scanWhitespace();
			}
		},
		
		/** A character of unknown significance was just seen. */
		UNKNOWN
		{
			@Override
			@NotNull Token scan (
					final @NotNull RenamesFileParser parser,
					final char firstChar)
				throws IOException
			{
				return parser.scanUnknown(firstChar);
			}
		};
		
		/**
		 * Answer the next {@linkplain Token token} from the {@linkplain
		 * #renamesFile renames file}.
		 * 
		 * @param parser A {@link RenamesFileParser}.
		 * @param firstChar The character used to select the {@link
		 *                  ScannerAction}.
		 * @return A {@linkplain Token token}.
		 * @throws IOException
		 *         If the scanner encounters an error while trying to scan for
		 *         the next {@linkplain Token token}. 
		 */
		abstract Token scan (
				@NotNull RenamesFileParser parser,
				char firstChar)
			throws IOException;
	}

	/**
	 * A map from Unicode code points to the ordinals of the {@link
	 * ScannerAction}s responsible for scanning constructs that begin with them.
	 */
	private static final byte[] scannerTable = new byte[65536];

	/*
	 * Initialize the scanner table.
	 */
	static
	{
		for (int i = 0; i < 65536; i++)
		{
			char c = (char) i;
			ScannerAction action;
			if (c == '"')
			{
				action = ScannerAction.DOUBLE_QUOTE;
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
	 *         If an {@linkplain IOException I/O exception} (or other failure)
	 *         occurs.
	 */
	private @NotNull Token scan () throws IOException
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
					throw new IOException(
						"Unknown token (" + token.lexeme + ")");
				}
				
				return token;
			}
		}
		
		return new Token(TokenType.EOF, "<EOF>");
	}
	
	// TODO: [TLS] Write parsing routines!
	
	/**
	 * Parse the {@linkplain #renamesFile renames file} and answer an
	 * appropriate {@linkplain Map map} from logical {@linkplain
	 * AvailModuleDescriptor module} paths to absolute {@linkplain File file
	 * references}.
	 * 
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} is encountered
	 *         during bulk tokenization.
	 */
	public @NotNull Map<String, File> parse () throws IOException
	{
		final Reader reader = new BufferedReader(new FileReader(renamesFile));
		// TODO: Fix this!
		return null;
	}
}
