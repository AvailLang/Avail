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
import avail.compiler.splitter.MessageSplitter
import avail.descriptor.module.A_Module
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.allTokens
import avail.descriptor.phrases.SendPhraseDescriptor
import avail.descriptor.representation.AvailObject
import avail.descriptor.tokens.A_Token
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.copyStringFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.PrefixSharingList.Companion.withoutLast
import kotlin.math.min

/**
 * [ParsingStepState] captures the current state while parsing an expression.
 * It's mutable, but in the event of a fork (say, due to a [BranchForward]), a
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
 * @property staticTokens
 *   The immutable [List] of "static" [A_Token]s that have been encountered
 *   and consumed for the current method or macro invocation being parsed.
 *   These also include the one-based part index of the static token within the
 *   [MessageSplitter]'s tuple of tokens.
 * @property initialIndentationString
 *   An optional, lazy [A_String], computed only if an indentation-sensitive
 *   instruction has asked what the initial indentation level (whitespace
 *   string) was. This was the indentation at the [initialTokenPosition] if we
 *   started with a keyword, otherwise the leftmost token of the first argument,
 *   or if that's tokenless somehow, the leftmost token of the first argument
 *   that has any tokens.
 * @property superexpressions
 *   The [PartialSubexpressionList] (or null) which forms a chain of partially
 *   constructed outer phrases, used for describing the circumstance of a syntax
 *   error.
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
	var staticTokens: List<Pair<A_Token, Int>>,
	private var initialIndentationString: A_String? = null,
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
			staticTokens,
			initialIndentationString,
			superexpressions,
			continuation)
		copy.update()
		return copy
	}

	/** Modify the state to have one more phrase. */
	fun push(phrase: A_Phrase)
	{
		argsSoFar = argsSoFar.append(phrase)
	}

	/** Modify the state to have one fewer phrase, returning it. */
	fun pop(): A_Phrase
	{
		val phrase = argsSoFar.last()
		argsSoFar = argsSoFar.withoutLast()
		return phrase
	}

	/**
	 * Modify the state to have one more marker [Int] value, used to track
	 * whether a repeated subsection made any progress.
	 */
	fun pushMarker(marker: Int)
	{
		marksSoFar = marksSoFar.append(marker)
	}

	/**
	 * Modify the state to have one fewer marker [Int] value, used to track
	 * whether a repeated subsection made any progress.  Answer the marker.
	 */
	fun popMarker(): Int
	{
		val marker = marksSoFar.last()
		marksSoFar = marksSoFar.withoutLast()
		return marker
	}

	/**
	 * Scan forward starting at the current parse position to skip any
	 * whitespace, then scan backward through the whitespace.  If during that
	 * scan we reach a linefeed (or the beginning of the source), capture the
	 * whitespace following that linefeed and return it.  Otherwise answer null.
	 *
	 * @param specialEndOfFileHandling
	 *   Whether to consider the end of the file to be a linefeed.
	 */
	fun currentIndentationString(
		specialEndOfFileHandling: Boolean = false
	): A_String?
	{
		val source = start.lexingState.compilationContext.source
		val size = source.tupleSize
		var pos = start.position
		while (pos <= size
			&& Character.isWhitespace(source.tupleCodePointAt(pos)))
		{
			pos++
		}
		// Pos is now just past any whitespace.  Note that a lexer would still
		// have to consume that whitespace, since we're parsing at `start`.
		var back = pos - 1
		while(back >= 1)
		{
			val codePoint = source.tupleCodePointAt(back)
			if (codePoint == '\n'.code) break
			if (!Character.isWhitespace(codePoint))
			{
				if (specialEndOfFileHandling && pos > size) break
				else return null
			}
			back--
		}
		return source.copyStringFromToCanDestroy(back + 1, pos - 1, false)
	}

	/**
	 * Answer the string of whitespace that begins the line containing the
	 * leftmost parsed token of this partially parsed phrase or any of the
	 * subphrases that have been parsed for it.  Save this in the current
	 * parsing state, if it's not already present.
	 */
	fun initialIndentationString(): A_String
	{
		var str = initialIndentationString
		if (str === null)
		{
			str = computeIndentationStringAt(computeInitialPosition())
			initialIndentationString = str
		}
		return str
	}

	/**
	 * Answer the string of whitespace that begins the line containing the
	 * given one-based character position in the source.  The position should be
	 * the first character of a token, but this is not required.
	 */
	private fun computeIndentationStringAt(position: Int): A_String
	{
		val source = start.lexingState.compilationContext.source
		val previousLineEnd = (position - 1 downTo 1).firstOrNull { i ->
			source.tupleCodePointAt(i) == '\n'.code
		} ?: 0
		val firstNonBlankOnLine =
			(previousLineEnd + 1 .. position).firstOrNull { i ->
				!Character.isWhitespace(source.tupleCodePointAt(i))
			} ?: position
		return source.copyStringFromToCanDestroy(
			previousLineEnd + 1, firstNonBlankOnLine - 1, false
		).makeShared()
	}

	/**
	 * Compute the initial position for the phrase being parsed.
	 */
	private fun computeInitialPosition(): Int
	{
		val source = start.lexingState.compilationContext.source
		val size = source.tupleSize
		var position = initialTokenPosition.position
		// Skip whitespace after parse position.
		while (position <= size
			&& Character.isWhitespace(source.tupleCodePointAt(position)))
		{
			position++
		}
		var leftmostTokenPosition = position
		// The phrase we're in the process of building might have started with
		// a leading phrase, so .
		val module = start.lexingState.compilationContext.module
		for (arg in argsSoFar)
		{
			val start = startOfPhrase(arg, module)
			if (start != null)
			{
				leftmostTokenPosition = min(leftmostTokenPosition, start)
				break
			}
		}
		return leftmostTokenPosition
	}

	/**
	 * Given a [phrase], answer its starting position in the source, or null if
	 * it contains no tokens from the current [module].
	 *
	 * This can be inefficient, as it computes the collection of all tokens in
	 * the phrase.
	 */
	private fun startOfPhrase(phrase: A_Phrase, module: A_Module): Int?
	{
		return phrase.allTokens
			.filter { it.isInCurrentModule(module) }
			.minOfOrNull(AvailObject::start)
	}
}
