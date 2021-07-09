/*
 * A_Lexer.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.parsing

import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.dispatch
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tokens.A_Token

/**
 * `A_Lexer` is an interface that specifies the [lexer][LexerDescriptor]
 * operations that an [AvailObject] must implement.  It's a sub-interface of
 * [A_BasicObject], the interface that defines the behavior that all
 * [AvailObject]s are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Lexer : A_BasicObject {
	companion object {
		/**
		 * Answer the [A_Method] in which this lexer is defined.  Even though
		 * the internals of the method aren't needed by the lexer, it's
		 * convenient to associated the lexer with a method for controlling
		 * imports and renames.
		 *
		 * @return
		 *   The lexer's method.
		 */
		val A_Lexer.lexerMethod: A_Method
			get() = dispatch { o_LexerMethod(it) }

		/**
		 * Answer the function to run (as the base call of a fiber), with the
		 * character at the current lexer position, to determine if the body
		 * function should be attempted.
		 *
		 * @return
		 *   The lexer's filter function.
		 */
		val A_Lexer.lexerFilterFunction: A_Function
			get() = dispatch { o_LexerFilterFunction(it) }

		/**
		 * Answer the function to run (as the base call of a fiber) to generate
		 * some tokens from the source string and position.  The function should
		 * produce a tuple of potential next tokens, which may or may not have
		 * additional tokens explicitly chained onto them via
		 * [A_Token.setNextLexingStateFromPrior].
		 *
		 * @return
		 *   The lexer's body function.
		 */
		val A_Lexer.lexerBodyFunction: A_Function
			get() = dispatch { o_LexerBodyFunction(it) }

		/**
		 * Answer the module in which this lexer was defined.  This is used for
		 * filtering – a module can only use this lexer if the module's ancestry
		 * includes the lexer's `definitionModule`.
		 *
		 * If we didn't have this rule (and similar ones for parsing method and
		 * macro invocations, and applying grammatical restrictions), then a
		 * module's compilation rules would change if an incomparable module
		 * (i.e., not an ancestor) concurrently added a
		 * lexer/definition/restriction.
		 *
		 * We do allow new method definitions to be added in concurrently loaded
		 * modules, and their introduction has an immediate effect on the
		 * behavior of existing call sites.  There are ways to reduce the impact
		 * of this, such as the suggestion that new definitions should only be
		 * added for at least one argument type that didn't exist before this
		 * module was loaded.
		 *
		 * Concurrently loaded modules might conflict when attempting to add a
		 * lexer to the same method, but this is probably a really bad idea
		 * anyhow. If we ever need to support it, we can allow a method to have
		 * a list of lexers instead of just one.
		 *
		 * @return
		 *   The [A_Module] in which this lexer was defined.
		 */
		val A_Lexer.definitionModule: A_Module
			get() = dispatch { o_DefinitionModule(it) }

		/**
		 * If the filter function cas already run for this single-byte codePoint
		 * in the past, answer the boolean value that it produced.  Otherwise
		 * answer `null`.
		 *
		 * @param codePoint
		 *   The codePoint [Int] in the range [0..255].
		 */
		fun A_Lexer.lexerApplicability(codePoint: Int): Boolean? =
			dispatch { o_LexerApplicability(it, codePoint) }

		/**
		 * Record the fact that the filter ran for this single-byte codePoint,
		 * and produced the provided [applicability] boolean.
		 *
		 * @param codePoint
		 *   The codePoint [Int] in the range [0..255] that was tested.
		 * @param applicability
		 *   A boolean indicating whether the lexer's filter indicated that the
		 *   lexer's body should run when this codePoint is encountered.
		 */
		fun A_Lexer.setLexerApplicability(
			codePoint: Int,
			applicability: Boolean
		) = dispatch { o_SetLexerApplicability(it, codePoint, applicability) }
	}
}
