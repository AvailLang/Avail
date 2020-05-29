/*
 * CompletelyOptional.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.ListPhraseTypeDescriptor.emptyListPhraseType

/**
 * A `CompletelyOptional` is a special [expression][Expression] indicated by a
 * [double&#32;question&#32;mark][Metacharacter.DOUBLE_QUESTION_MARK] (⁇) following a
 * [simple][Simple] or [simple group][Group]. It may not contain
 * [arguments][Argument] or non-simple subgroups and it may not contain a
 * [double&#32;dagger][Metacharacter.DOUBLE_DAGGER]. The expression may appear zero
 * or one times.
 *
 * A completely optional does not produce any information. No facility is
 * provided to determine whether there was an occurrence of the expression.
 * The message "very⁇good" accepts no arguments, but may parse either
 * "very good" or "good".
 *
 * @property sequence
 *   The governed [sequence][Sequence].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `CompletelyOptional`.
 *
 * @param positionInName
 *   The position of the start of this phrase in the message name.
 * @param sequence
 *   The governed [sequence][Sequence].
 */
internal class CompletelyOptional constructor(
	positionInName: Int,
	private val sequence: Sequence) : Expression(positionInName)
{
	override val isLowerCase: Boolean
		get() = sequence.isLowerCase

	override fun applyCaseInsensitive(): Expression =
		CompletelyOptional(positionInName, sequence.applyCaseInsensitive())

	override val underscoreCount: Int
		get()
		{
			assert(sequence.underscoreCount == 0)
			return 0
		}

	override fun extractSectionCheckpointsInto(
			sectionCheckpoints: MutableList<SectionCheckpoint>)
		= sequence.extractSectionCheckpointsInto(sectionCheckpoints)

	override fun checkType(argumentType: A_Type, sectionNumber: Int)
	{
		assert(false) {
			"checkType() should not be called for CompletelyOptional " +
				"expressions"
		}
	}

	@Suppress("LocalVariableName")
	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		/* branch to @expressionSkip.
		 * push current parse position on the mark stack.
		 * ...Simple or stuff before dagger (i.e., all expressions).
		 * check progress and update saved position, or abort.
		 * discard mark position
		 * @expressionSkip:
		 */
		val needsProgressCheck = sequence.mightBeEmpty(emptyListPhraseType())
		val `$expressionSkip` = Label()
		generator.emitBranchForward(this, `$expressionSkip`)
		generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION)
		// The partialListsCount stays the same, in case there's a section
		// checkpoint marker within this completely optional region.  That's a
		// reasonable way to indicate that a prefix function should only run
		// when the optional section actually occurs.  Since no completely
		// optional section can produce a value (argument, counter, etc),
		// there's no problem.
		for (expression in sequence.expressions)
		{
			expression.emitOn(
				emptyListPhraseType(),
				generator,
				WrapState.SHOULD_NOT_HAVE_ARGUMENTS)
		}
		generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS)
		generator.emitIf(
			needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION)
		generator.emit(`$expressionSkip`)
		return wrapState
	}

	override fun toString(): String =
		"${javaClass.simpleName}(${sequence.expressions})"

	override fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)
	{
		// Don't consume any real arguments.  If the sequence contains just a
		// single Simple, show it with a double question mark.  Otherwise place
		// guillemets around the sequence and follow it with the double question
		// mark.
		if (sequence.expressions.size == 1 && sequence.expressions[0] is Simple)
		{
			// A single optional token.
			sequence.printWithArguments(
				emptyTuple().iterator(), builder, indent)
		}
		else
		{
			// A sequence of tokens that are optional (in aggregate).
			builder.append('«')
			sequence.printWithArguments(
				emptyTuple().iterator(), builder, indent)
			builder.append('»')
		}
		builder.append('⁇')
	}

	override val shouldBeSeparatedOnLeft: Boolean
		get() = sequence.shouldBeSeparatedOnLeft

	override val shouldBeSeparatedOnRight: Boolean
		// Emphasize the double question mark that will always be printed
		// by ensuring a space follows it.
		get() = true

	override fun mightBeEmpty(phraseType: A_Type): Boolean
	{
		// Completely optional expressions can be absent.
		return true
	}
}
