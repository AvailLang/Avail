/**
 * A_Lexer.java
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

package com.avail.descriptor;

/**
 * {@code A_Lexer} is an interface that specifies the {@linkplain
 * LexerDescriptor lexer-specific operations that an {@link AvailObject} must
 * implement.  It's a sub-interface of {@link A_BasicObject}, the interface that
 * defines the behavior that all AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Lexer
extends A_BasicObject
{
	/**
	 * Answer the {@link A_Method} in which this lexer is defined.
	 *
	 * @return The lexer's method.
	 */
	A_Method lexerMethod ();

	/**
	 * Answer the function to run (as the base call of a fiber), with the
	 * character at the current lexer position, to determine if the body
	 * function should be attempted.
	 *
	 * @return The lexer's filter function.
	 */
	A_Function lexerFilterFunction ();

	/**
	 * Answer the function to run (as the base call of a fiber) to generate some
	 * tokens from the source string and position.  The function should produce
	 * a tuple of potential next tokens, which may or may not have additional
	 * tokens explicitly chained onto them via their {@link
	 * A_Token#nextLexingState()}.
	 *
	 * @return The lexer's body function.
	 */
	A_Function lexerBodyFunction ();

	/**
	 * Answer the module in which this lexer was defined.  This is used for
	 * filtering – a module can only use this lexer if the module's ancestry
	 * includes the lexer's definitionModule.
	 *
	 * <p>If we didn't have this rule (and similar ones for parsing method and
	 * macro invocations, and applying grammatical restrictions), then a
	 * module's compilation rules would change if an incomparable module (i.e.,
	 * not an ancestor) concurrently added a lexer/definition/restriction.</p>
	 *
	 * <p>We do allow new method definitions to be added in concurrently loaded
	 * modules, and their appearance has an immediate effect on the behavior of
	 * existing call sites.  There are ways to reduce the impact of this, such
	 * as the suggestion that new definitions should only be added for at least
	 * one argument type that didn't exist before this module was loaded.</p>
	 *
	 * <p>Concurrently loaded modules might conflict when attempting to add a
	 * lexer to the same method, but this is probably a really bad idea anyhow.
	 * If we ever need to support it, we can allow a method to have a list of
	 * lexers instead of just one.</p>
	 *
	 * @return The {@link A_Module} in which this lexer was defined.
	 */
	A_Module definitionModule ();
}
