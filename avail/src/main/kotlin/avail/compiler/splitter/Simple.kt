/*
 * Simple.kt
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
package avail.compiler.splitter

import avail.compiler.ParsingOperation.PARSE_PART
import avail.compiler.ParsingOperation.PARSE_PART_CASE_INSENSITIVELY
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type

/**
 * A `Simple` is an [expression][Expression] that
 * represents a single token, except for the double-dagger character.
 *
 * @property token
 *   The [A_String] for this simple expression.
 * @property tokenIndex
 *   The one-based index of this token within the (zero-based)
 *   [MessageSplitter.messageParts].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *   Construct a new `Simple` expression representing a specific token expected
 *   in the input.
 *
 * @param startInName
 *   The one-based index of the token within the entire name string.
 * @param pastEndInName
 *   The one-based position just past the end of the token in the message name.
 * @param token
 *   The [A_String] form of this static token.
 * @param tokenIndex
 *   The one-based position of this static token within the [MessageSplitter]'s
 *   tuple of [parts][MessageSplitter.messageParts].
 */
internal class Simple constructor(
	startInName: Int,
	pastEndInName: Int,
	private val token: A_String,
	private val tokenIndex: Int
) : Expression(startInName, pastEndInName)
{
	init
	{
		assert(tokenIndex > 0)
	}

	override val isLowerCase: Boolean
		get()
		{
			val nativeString = token.asNativeString()
			return nativeString.lowercase()
				.equals(nativeString, ignoreCase = true)
		}

	override fun applyCaseInsensitive(): Expression =
		CaseInsensitive(startInName, pastEndInName, this)

	override fun checkType(
		argumentType: A_Type,
		sectionNumber: Int)
	{
		assert(false) {
			"checkType() should not be called for Simple expressions"
		}
	}

	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		// Parse the specific keyword.
		val op = when
		{
			generator.caseInsensitive -> PARSE_PART_CASE_INSENSITIVELY
			else -> PARSE_PART
		}
		generator.emit(this, op, tokenIndex)
		return wrapState
	}

	override fun toString(): String = "${javaClass.simpleName}($token)"

	override fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)
	{
		builder.append(token.asNativeString())
	}

	override val shouldBeSeparatedOnLeft: Boolean
		get()
		{
			assert(token.tupleSize > 0)
			val firstCharacter = token.tupleCodePointAt(1)
			return Character.isUnicodeIdentifierPart(firstCharacter)
				|| charactersThatLikeSpacesBefore.indexOf(
					firstCharacter.toChar()) >= 0
		}

	override val shouldBeSeparatedOnRight: Boolean
		get()
		{
			assert(token.tupleSize > 0)
			val lastCharacter = token.tupleCodePointAt(token.tupleSize)
			return Character.isUnicodeIdentifierPart(lastCharacter)
				|| charactersThatLikeSpacesAfter.indexOf(
					lastCharacter.toChar()) >= 0
		}

	override fun checkListStructure(phrase: A_Phrase): Boolean =
		throw RuntimeException(
			"checkListStructure() inapplicable for Simple expression.")

	companion object
	{
		/**
		 * Characters which, if they start a token, should vote for having a
		 * space before the token.  If the predecessor agrees, there will be a
		 * space.
		 */
		private const val charactersThatLikeSpacesBefore = "(=+-×÷*/∧∨:?⊆∈"

		/**
		 * Characters which, if they end a token, should vote for having a
		 * space after the token.  If the successor agrees, there will be a
		 * space.
		 */
		private const val charactersThatLikeSpacesAfter = ")]=+-×÷*/∧∨→⊆∈"
	}
}
