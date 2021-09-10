/*
 * Alternation.kt
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
package com.avail.compiler.splitter

import com.avail.compiler.ParsingOperation.DISCARD_SAVED_PARSE_POSITION
import com.avail.compiler.ParsingOperation.ENSURE_PARSE_PROGRESS
import com.avail.compiler.ParsingOperation.SAVE_PARSE_POSITION
import com.avail.compiler.splitter.InstructionGenerator.Label
import com.avail.compiler.splitter.MessageSplitter.Metacharacter
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.ListPhraseTypeDescriptor.Companion.emptyListPhraseType

/**
 * An `Alternation` is a special [expression][Expression] indicated by
 * interleaved [vertical&#32;bars][Metacharacter.VERTICAL_BAR] between
 * [simples][Simple] and [simple&#32;groups][Group]. It may not contain
 * [arguments][Argument].
 *
 * An alternation specifies several alternative parses but does not produce any
 * information. No facility is provided to determine which alternative occurred
 * during a parse. The message "a|an_" may be parsed as either "a_" or "an_".
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `Alternation`.
 *
 * @param positionInName
 *   The position of this expression in the message name.
 * @param alternatives
 *   The alternative [expressions][Expression].
 */
internal class Alternation constructor(
	positionInName: Int,
	alternatives: List<Expression>) : Expression(positionInName)
{
	override val recursivelyContainsReorders: Boolean
		get() = alternatives.any { it.recursivelyContainsReorders }

	/** The alternative [expressions][Expression]. */
	internal val alternatives: List<Expression> = alternatives.toList()

	override val isLowerCase: Boolean
		get() = alternatives.stream().allMatch(Expression::isLowerCase)

	override fun applyCaseInsensitive(): Alternation {
		return Alternation(
			positionInName, alternatives.map(Expression::applyCaseInsensitive))
	}

	override fun extractSectionCheckpointsInto(
		sectionCheckpoints: MutableList<SectionCheckpoint>)
	{
		alternatives.forEach { alternative ->
			alternative.extractSectionCheckpointsInto(sectionCheckpoints)
		}
	}

	 override fun checkType(argumentType: A_Type, sectionNumber: Int)
	{
		assert(false) {
			"checkType() should not be called for Alternation expressions"
		}
	}

	@Suppress("LocalVariableName")
	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		/* push current parse position on the mark stack
		 * branch to @branches[0]
		 * ...First alternative.
		 * jump to @branches[N-1] (the last branch label)
		 * @branches[0]:
		 * ...Repeat for each alternative, omitting the branch and jump for
		 * ...the last alternative.
		 * @branches[N-1]:
		 * check progress and update saved position, or abort.
		 * pop the parse position.
		 */
		val needsProgressCheck =
			alternatives.stream().anyMatch { it.mightBeEmpty(bottom) }
		generator.flushDelayed()
		generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION)
		val `$after` = Label()
		for (i in alternatives.indices)
		{
			// Generate a branch to the next alternative unless this is the last
			// alternative.
			val `$nextAlternative` = Label()
			if (i < alternatives.size - 1)
			{
				generator.emitBranchForward(this, `$nextAlternative`)
			}
			// The partialListsCount stays the same, in case there's a section
			// checkpoint marker in one of the alternatives.  That's a
			// reasonable way to indicate that a prefix function should only run
			// when that alternative occurs.  Since no alternative can produce a
			// value (argument, counter, etc), there's no problem.
			val newWrapState = alternatives[i].emitOn(
				emptyListPhraseType(), generator, wrapState)
			assert(newWrapState === wrapState)
			// Generate a jump to the last label unless this is the last
			// alternative.
			if (i < alternatives.size - 1)
			{
				generator.emitJumpForward(this, `$after`)
			}
			generator.emit(`$nextAlternative`)
		}
		generator.emit(`$after`)
		generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS)
		generator.emitIf(
			needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION)
		return wrapState
	}

	override fun toString(): String
	{
		val builder = StringBuilder()
		builder.append(this@Alternation.javaClass.simpleName)
		builder.append('(')
		var first = true
		for (expression in alternatives)
		{
			if (!first)
			{
				builder.append(',')
			}
			builder.append(expression)
			first = false
		}
		builder.append(')')
		return builder.toString()
	}

	override fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)
	{
		var isFirst = true
		for (alternative in alternatives)
		{
			if (!isFirst)
			{
				builder.append('|')
			}
			alternative.printWithArguments(
				null,
				builder,
				indent)
			isFirst = false
		}
	}

	override val shouldBeSeparatedOnLeft: Boolean
		get() = alternatives[0].shouldBeSeparatedOnLeft

	override val shouldBeSeparatedOnRight: Boolean
		get()
		{
			val last = alternatives[alternatives.size - 1]
			return last.shouldBeSeparatedOnRight
		}

	override fun mightBeEmpty (phraseType: A_Type): Boolean =
		alternatives.any { it.mightBeEmpty(bottom) }

	override fun checkListStructure (phrase: A_Phrase): Boolean =
		throw RuntimeException(
			"checkListStructure() inapplicable for Alternation.")
}
