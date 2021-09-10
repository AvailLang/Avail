/*
 * A_Token.kt
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
package com.avail.descriptor.tokens

import com.avail.compiler.scanning.LexingState
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.tuples.A_String

/**
 * `A_Token` is an interface that specifies the token-specific operations that
 * an [AvailObject] must implement.  It's a sub-interface of [A_BasicObject],
 * the interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Token : A_BasicObject
{
	/**
	 * Disconnect this token from its internal cache of what comes next.
	 */
	fun clearLexingState()

	/**
	 * Answer whether this token is a
	 * [literal&#32;token][LiteralTokenDescriptor], such as a string or number.
	 *
	 * @return
	 *   Whether the token is a literal.
	 */
	fun isLiteralToken(): Boolean

	/**
	 * The line number of this token in the source file.
	 *
	 * @return
	 *   The token's line number.
	 */
	fun lineNumber(): Int

	/**
	 * Extract the literal value from this token.  It must be a literal token.
	 *
	 * @return
	 *   The value of the literal token.
	 */
	fun literal(): AvailObject

	/**
	 * Answer this token's string representation converted to lower case.
	 *
	 * @return
	 *   The token's lowercase representation.
	 */
	fun lowerCaseString(): A_String

	/**
	 * Answer the [LexingState] that follows this token, or `null` if it hasn't
	 * been set yet.
	 *
	 * @return
	 *   The next [LexingState] or `null`.
	 */
	fun nextLexingState(): LexingState

	/**
	 * Answer the pojo [AvailObject] containing the ][LexingState] that follows
	 * this token, or [nil] if it hasn't been set yet.
	 *
	 * @return
	 *   Either the pojo [AvailObject] containing the next [LexingState] or
	 *   [nil].
	 */
	fun nextLexingStatePojo(): AvailObject

	/**
	 * Set this token's next [LexingState].
	 *
	 * @param priorLexingState
	 *   The lexing state just prior to this token.
	 */
	fun setNextLexingStateFromPrior(priorLexingState: LexingState)

	/**
	 * Answer this token's initial character position in the source file.
	 *
	 * @return
	 *   The token's source position.
	 */
	fun start(): Int

	/**
	 * Answer this token's exact string representation as it appeared in the
	 * source code.
	 *
	 * @return
	 *   The token's string representation.
	 */
	fun string(): A_String

	/**
	 * Answer the [TokenDescriptor.TokenType] of this token.
	 *
	 * @return
	 *   A TokenType.
	 */
	fun tokenType(): TokenDescriptor.TokenType
}
