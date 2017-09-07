/**
 * ExpectedToken.java
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

package com.avail.compiler;

import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import com.avail.descriptor.A_String;
import com.avail.descriptor.StringDescriptor;
import com.avail.descriptor.TokenDescriptor.TokenType;

/**
 * These are the tokens that are understood directly by the Avail compiler.
 */
public enum ExpectedToken
{
	/** Module header token: Precedes the name of the defined module. */
	MODULE("Module", KEYWORD),

	/**
	 * Module header token: Precedes the list of versions for which the
	 * defined module guarantees compatibility.
	 */
	VERSIONS("Versions", KEYWORD),

	/** Module header token: Precedes the list of pragma strings. */
	PRAGMA("Pragma", KEYWORD),

	/**
	 * Module header token: Occurs in pragma strings to assert some quality
	 * of the virtual machine.
	 */
	PRAGMA_CHECK("check", KEYWORD),

	/**
	 * Module header token: Occurs in pragma strings to define bootstrap
	 * method.
	 */
	PRAGMA_METHOD("method", KEYWORD),

	/**
	 * Module header token: Occurs in pragma strings to define bootstrap
	 * macros.
	 */
	PRAGMA_MACRO("macro", KEYWORD),

	/**
	 * Module header token: Occurs in a pragma string to define the
	 * stringification method.
	 */
	PRAGMA_STRINGIFY("stringify", KEYWORD),

	/**
	 * Module header token: Occurs in a pragma string to define a lexer.
	 */
	PRAGMA_LEXER("lexer", KEYWORD),

	/**
	 * Module header token: Precedes the list of imported modules whose
	 * (filtered) names should be re-exported to clients of the defined
	 * module.
	 */
	EXTENDS("Extends", KEYWORD),

	/**
	 * Module header token: Precedes the list of imported modules whose
	 * (filtered) names are imported only for the private use of the
	 * defined module.
	 */
	USES("Uses", KEYWORD),

	/**
	 * Module header token: Precedes the list of names exported for use by
	 * clients of the defined module.
	 */
	NAMES("Names", KEYWORD),

	/** Module header token: Precedes the list of entry points. */
	ENTRIES("Entries", KEYWORD),

	/** Module header token: Precedes the contents of the defined module. */
	BODY("Body", KEYWORD),

	/**
	 * Module header token: Mutually exclusive alternative to {@link #BODY};
	 * provides a way to treat files without Avail headers as modules.
	 */
	FILES("Files", KEYWORD),

	/** Module header token: Separates string literals. */
	COMMA(",", OPERATOR),

	/** Module header token: Separates string literals for renames. */
	RIGHT_ARROW("→", OPERATOR),

	/** Module header token: Prefix to indicate exclusion. */
	MINUS("-", OPERATOR),

	/** Module header token: Indicates wildcard import */
	ELLIPSIS("…", OPERATOR),

	/** Uses related to declaration and assignment. */
	EQUALS("=", OPERATOR),

	/** Module header token: Uses related to grouping. */
	OPEN_PARENTHESIS("(", OPERATOR),

	/** Module header token: Uses related to grouping. */
	CLOSE_PARENTHESIS(")", OPERATOR);

	/** The Java {@link String} form of the lexeme. */
	public final String lexemeJavaString;

	/**
	 * The {@linkplain StringDescriptor Avail string} form of the lexeme.
	 */
	private final A_String lexeme;

	/** The {@linkplain TokenType token type}. */
	private final TokenType tokenType;

	/**
	 * Answer the {@linkplain StringDescriptor lexeme}.
	 *
	 * @return The lexeme as an Avail string.
	 */
	public A_String lexeme ()
	{
		return lexeme;
	}

	/**
	 * Answer the {@linkplain TokenType token type}.
	 *
	 * @return The token type.
	 */
	TokenType tokenType ()
	{
		return tokenType;
	}

	/**
	 * Construct a new {@link ExpectedToken}.
	 *
	 * @param lexemeJavaString
	 *        The Java {@linkplain String lexeme string}, i.e. the text
	 *        of the token.
	 * @param tokenType
	 *        The {@linkplain TokenType token type}.
	 */
	ExpectedToken (
		final String lexemeJavaString,
		final TokenType tokenType)
	{
		this.tokenType = tokenType;
		this.lexemeJavaString = lexemeJavaString;
		this.lexeme = StringDescriptor.stringFrom(lexemeJavaString).makeShared();
	}
}
