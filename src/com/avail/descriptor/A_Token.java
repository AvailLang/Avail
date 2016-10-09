/**
 * A_Token.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import com.avail.compiler.scanning.AvailScanner;
import com.avail.descriptor.TokenDescriptor.TokenType;


/**
 * {@code A_Token} is an interface that specifies the token-specific operations
 * that an {@link AvailObject} must implement.  It's a sub-interface of {@link
 * A_BasicObject}, the interface that defines the behavior that all AvailObjects
 * are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Token
extends A_BasicObject
{
	/**
	 * Answer the {@linkplain TokenType} of this token.
	 *
	 * @return A TokenType.
	 */
	TokenType tokenType ();

	/**
	 * Answer whether this token is a {@linkplain LiteralTokenDescriptor literal
	 * token}, such as a string or number.
	 *
	 * @return Whether the token is a literal.
	 */
	boolean isLiteralToken ();

	/**
	 * Answer this token's exact string representation as it appeared in the
	 * source code.
	 *
	 * @return The token's string representation.
	 */
	A_String string ();

	/**
	 * Answer this token's exact leading whitespace as it appeared in the source
	 * code.
	 *
	 * @return The token's leading whitespace.
	 */
	A_String leadingWhitespace ();

	/**
	 * Answer this token's exact trailing whitespace as it appeared in the
	 * source code.
	 *
	 * @return The token's trailing whitespace.
	 */
	A_String trailingWhitespace ();

	/**
	 * Set this token's exact trailing whitespace as it appeared in the source
	 * code. This capability makes the {@linkplain AvailScanner scanner} much
	 * easier to build and maintain.
	 *
	 * @param trailingWhitespace A string.
	 */
	void trailingWhitespace (A_String trailingWhitespace);

	/**
	 * Answer this token's string representation converted to lower case.
	 *
	 * @return The token's lowercase representation.
	 */
	A_String lowerCaseString();

	/**
	 * Answer this token's initial character position in the source file.
	 *
	 * @return The token's source position.
	 */
	int start ();

	/**
	 * The line number of this token in the source file.
	 *
	 * @return the token's line number.
	 */
	int lineNumber ();

	/**
	 * Extract the literal value from this token.  It must be a literal token.
	 *
	 * @return The value of the literal token.
	 */
	AvailObject literal ();

	/**
	 * Answer this token's module of origin.
	 *
	 * @return The module this token appears in
	 */
	A_String moduleName ();

	/**
	 * Answer the zero-based index of this token within the list of this
	 * module's tokenized source.
	 *
	 * @return The zero-based subscript of this token.
	 */
	int tokenIndex ();
}
