/*
 * ParsingStepState.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.compiler

import avail.compiler.AvailCompiler.PartialSubexpressionList
import avail.compiler.ParsingOperation.BRANCH_FORWARD
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.SendPhraseDescriptor
import avail.descriptor.tokens.A_Token
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.PrefixSharingList.Companion.withoutLast

/**
 * [ParsingStepState] captures the current state while parsing an expression.
 * It's mutable, but in the event of a fork (say, due to a [BRANCH_FORWARD]), a
 * copy must be created before the original is mutated again.
 *
 * @constructor
 *   Construct a new [ParsingStepState].
 *
 * @property start
 *   Where to start parsing.
 * @property firstArgOrNull
 *   Either the already-parsed first argument or `null`. If we're looking
 *   for leading-argument message sends to wrap an expression then this is
 *   not-`null` before the first argument position is encountered, otherwise
 *   it's `null` and we should reject attempts to start with an argument
 *   (before a keyword).
 * @property argsSoFar
 *   The message arguments that have been parsed so far.
 * @property marksSoFar
 *   The parsing markers that have been recorded so far.
 * @property initialTokenPosition
 *   The position at which parsing of this message started. If it was parsed
 *   as a leading argument send (i.e., firstArgOrNull started out
 *   non-`null`) then the position is of the token following the first
 *   argument.
 * @property consumedAnything
 *   Whether any tokens or arguments have been consumed yet.
 * @property consumedAnythingBeforeLatestArgument
 *   Whether any tokens or arguments had been consumed before encountering
 *   the most recent argument.  This is to improve diagnostics when argument
 *   type checking is postponed past matches for subsequent tokens.
 * @property consumedStaticTokens
 *   The immutable [List] of "static" [A_Token]s that have been encountered
 *   and consumed for the current method or macro invocation being parsed.
 *   These are the tokens that correspond with tokens that occur verbatim
 *   inside the name of the method or macro.
 * @property continuation
 *   What to do with a complete [message&#32;send][SendPhraseDescriptor].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
internal class ParsingStepState constructor(
	var start: ParserState,
	var firstArgOrNull: A_Phrase?,
	var argsSoFar: List<A_Phrase>,
	var marksSoFar: List<Int>,
	var initialTokenPosition: ParserState,
	var consumedAnything: Boolean,
	var consumedAnythingBeforeLatestArgument: Boolean,
	var consumedStaticTokens: List<A_Token>,
	var superexpressions: PartialSubexpressionList?,
	var continuation: (ParserState, A_Phrase)->Unit)
{
	/**
	 * Create a copy of the receiver, running the supplied update extension
	 * function on it after the initial copying.
	 *
	 * @param update
	 *   An optional extension function to run on the result.
	 */
	fun copy(update: ParsingStepState.()->Unit = {}): ParsingStepState
	{
		val copy = ParsingStepState(
			start,
			firstArgOrNull,
			argsSoFar,
			marksSoFar,
			initialTokenPosition,
			consumedAnything,
			consumedAnythingBeforeLatestArgument,
			consumedStaticTokens,
			superexpressions,
			continuation)
		copy.update()
		return copy
	}

	fun push(phrase: A_Phrase)
	{
		argsSoFar = argsSoFar.append(phrase)
	}

	fun pop(): A_Phrase
	{
		val phrase = argsSoFar.last()
		argsSoFar = argsSoFar.withoutLast()
		return phrase
	}

	fun pushMarker(marker: Int)
	{
		marksSoFar = marksSoFar.append(marker)
	}

	fun popMarker(): Int
	{
		val marker = marksSoFar.last()
		marksSoFar = marksSoFar.withoutLast()
		return marker
	}
}
