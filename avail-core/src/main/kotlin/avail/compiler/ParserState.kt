/*
 * ParserState.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import avail.AvailRuntime.Companion.currentRuntime
import avail.compiler.problems.CompilerDiagnostics
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel
import avail.compiler.scanning.LexingState
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.MapDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.utility.evaluation.Describer
import avail.utility.evaluation.SimpleDescriber
import java.lang.String.format
import kotlin.math.max
import kotlin.math.min

/**
 * `ParserState` instances are immutable and keep track of a current
 * [lexingState] and [clientDataMap] during parsing.
 *
 * @property lexingState
 *   The state of the lexer at this parse position.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new immutable `ParserState`.
 *
 * @param lexingState
 *   The [LexingState] at this parse position.
 * @param clientDataMap
 * The [map][MapDescriptor] of data used by macros while parsing Avail code.
 */
class ParserState internal constructor(
	val lexingState: LexingState,
	clientDataMap: A_Map)
{
	/**
	 * A [map][MapDescriptor] of interesting information used by the compiler.
	 *
	 * Note that this map *must* be marked as shared, since parsing
	 * proceeds in parallel.
	 */
	val clientDataMap: A_Map = clientDataMap.makeShared()

	override fun hashCode() =
		lexingState.hashCode() * AvailObject.multiplier xor clientDataMap.hash()

	override fun equals(other: Any?): Boolean
	{
		if (other === null)
		{
			return false
		}
		if (other !is ParserState)
		{
			return false
		}
		// Don't bother comparing allTokens, since there should never be a case
		// where the lexingState and clientDataMap agree, but different lists of
		// tokens were accumulated to get there.
		val anotherState = other as ParserState?
		return lexingState == anotherState!!.lexingState
			&& clientDataMap.equals(anotherState.clientDataMap)
	}

	override fun toString(): String
	{
		val source = lexingState.compilationContext.source
		return format(
			"%s%n\tPOSITION = %d%n\tTOKENS = %s %s %s%n\tCLIENT_DATA = %s",
			this@ParserState.javaClass.simpleName,
			lexingState.position,
			source.copyStringFromToCanDestroy(
				max(lexingState.position - 20, 1),
				max(lexingState.position - 1, 0),
				false).asNativeString(),
			CompilerDiagnostics.errorIndicatorSymbol,
			source.copyStringFromToCanDestroy(
				min(lexingState.position, source.tupleSize + 1),
				min(lexingState.position + 20, source.tupleSize),
				false).asNativeString(),
			clientDataMap)
	}

	/**
	 * Report a short description of this state.
	 *
	 * @return
	 *   A [String] describing this `ParserState`.
	 */
	internal fun shortString(): String
	{
		val source = lexingState.compilationContext.source
		if (lexingState.position == 1)
		{
			return "(start)"
		}
		if (lexingState.position == source.tupleSize + 1)
		{
			return "(end)"
		}
		val nearbyText = source.copyStringFromToCanDestroy(
			lexingState.position,
			min(lexingState.position + 20, source.tupleSize),
			false)
		return (
			lexingState.lineNumber.toString() + ":"
			+ nearbyText.asNativeString().replace("\n", "\\n")
			+ '…'.toString())
	}

	/**
	 * Create a `ParserState` with a different [clientDataMap].
	 *
	 * @param replacementMap
	 *   The [A_Map] to replace this parser state's [clientDataMap] in the new
	 *   parser state.
	 * @return
	 *   The new `ParserState`.
	 */
	internal fun withMap(replacementMap: A_Map) =
		ParserState(lexingState, replacementMap)

	/**
	 * The one-based index into the source.
	 */
	val position get() = lexingState.position

	/**
	 * The one-based line number.
	 */
	val lineNumber get() = lexingState.lineNumber

	/**
	 * `true` if this state represents the end of the file, `false` otherwise.
	 */
	val atEnd get() =
		lexingState.position ==
			lexingState.compilationContext.source.tupleSize + 1

	/**
	 * Record an expectation at the current parse position. The expectations
	 * captured at the rightmost few reached parse positions constitute the
	 * error message in case the parse fails.
	 *
	 * The expectation is a [Describer], in case constructing a [String]
	 * frivolously would be prohibitive. There is also [another][expected]
	 * version of this method that accepts a String directly.
	 *
	 * @param level
	 *   The [ParseNotificationLevel] that indicates the priority of the parse
	 *   theory that failed.
	 * @param describer
	 *   The `describer` to capture.
	 */
	internal fun expected(level: ParseNotificationLevel, describer: Describer) =
		lexingState.compilationContext.diagnostics.expectedAt(
			level, describer, lexingState)

	/**
	 * Record an expectation at the current parse position. The expectations
	 * captured at the rightmost parse position constitute the error message in
	 * case the parse fails.
	 *
	 * @param level
	 *   The [ParseNotificationLevel] that indicates the priority of the parse
	 *   theory that failed.
	 * @param values
	 *   A list of arbitrary [Avail&#32;values][AvailObject] that should be
	 *   stringified.
	 * @param transformer
	 *   A transformer that accepts the stringified values and answers an
	 *   expectation message.
	 */
	internal fun expected(
		level: ParseNotificationLevel,
		values: List<A_BasicObject>,
		transformer: (List<String>)->String
	) =
		expected(level) { continuation ->
			currentRuntime().stringifyThen(
				values,
				lexingState.compilationContext.textInterface)
			{
				continuation(transformer(it))
			}
		}

	/**
	 * Record an indication of what was expected at this parse position.
	 *
	 * @param level
	 *   The [ParseNotificationLevel] that indicates the priority of the parse
	 *   theory that failed.
	 * @param aString
	 *   The string describing something that was expected at this position
	 *   under some interpretation so far.
	 */
	internal fun expected(level: ParseNotificationLevel, aString: String) =
		expected(level, SimpleDescriber(aString))

	/**
	 * Queue an action to be performed later, with no arguments.
	 *
	 * @param continuation
	 *   What to execute.
	 */
	internal fun workUnitDo(continuation: () -> Unit) =
		lexingState.workUnitDo(continuation)
}
