/*
 * NumberedChoice.kt
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

import com.avail.compiler.ParsingOperation.PUSH_LITERAL
import com.avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT
import com.avail.compiler.splitter.InstructionGenerator.Label
import com.avail.compiler.splitter.MessageSplitter.Companion.indexForConstant
import com.avail.compiler.splitter.MessageSplitter.Companion.throwSignatureException
import com.avail.compiler.splitter.MessageSplitter.Metacharacter
import com.avail.compiler.splitter.WrapState.SHOULD_NOT_HAVE_ARGUMENTS
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.ListPhraseTypeDescriptor.Companion.emptyListPhraseType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_NUMBERED_CHOICE
import com.avail.exceptions.SignatureException
import java.util.Collections

/**
 * A `NumberedChoice` is a special subgroup (i.e., not a root group) indicated
 * by an [exclamation&#32;mark][Metacharacter.EXCLAMATION_MARK] following a
 * [group][Group].  It must not contain [arguments][Argument] or subgroups and
 * it must not contain a [double&#32;dagger][Metacharacter.DOUBLE_DAGGER].  The
 * group contains an [Alternation], and parsing the group causes exactly one of
 * the alternatives to be parsed. The 1-based index of the alternative is
 * produced as a literal constant argument.
 *
 * For example, consider parsing a send of the message "my«cheese|bacon|Elvis»!"
 * from the string "my bacon cheese".  The bacon token will be parsed, causing
 * this to be an invocation of the message with the single argument 2
 * (indicating the second choice).  The cheese token is not considered part of
 * this message send (and will lead to a failed parse if some method like
 * "_cheese" is not present.
 *
 * @property alternation
 *   The alternation expression, exactly one alternative of which must be
 *   chosen.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `NumberedChoice`.
 *
 * @param alternation
 *   The enclosed [Alternation].
 */
internal class NumberedChoice constructor(private val alternation: Alternation)
: Expression(alternation.positionInName)
{
	override val recursivelyContainsReorders: Boolean
		get() = alternation.recursivelyContainsReorders

	override val yieldsValue: Boolean
		get() = true

	override val isLowerCase: Boolean
		get() = alternation.isLowerCase

	override fun applyCaseInsensitive() =
		NumberedChoice(alternation.applyCaseInsensitive())

	override val underscoreCount: Int
		get()
		{
			assert(alternation.underscoreCount == 0)
			return 0
		}

	override fun extractSectionCheckpointsInto(
			sectionCheckpoints: MutableList<SectionCheckpoint>)
		= alternation.extractSectionCheckpointsInto(sectionCheckpoints)

	@Throws(SignatureException::class)
	override fun checkType(argumentType: A_Type, sectionNumber: Int)
	{
		// The declared type of the subexpression must be a subtype of
		// [1..N] where N is the number of alternatives.
		if (!argumentType.isSubtypeOf(
				inclusive(1, alternation.alternatives.size.toLong())))
		{
			throwSignatureException(E_INCORRECT_TYPE_FOR_NUMBERED_CHOICE)
		}
	}

	@Suppress("LocalVariableName")
	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		/* branch to @target1.
		 * ...do first alternative.
		 * push literal 1.
		 * jump to @done.
		 * @target1:
		 *
		 * branch to @target2.
		 * ...do second alternative.
		 * push literal 2.
		 * jump to @done.
		 * @target2:
		 * ...
		 * @targetN-2nd:
		 *
		 * branch to @targetN-1st.
		 * ...do N-1st alternative.
		 * push literal N-1.
		 * jump to @done.
		 * @targetN-1st:
		 *
		 * ;;;no branch
		 * ...do Nth alternative.
		 * push literal N.
		 * ;;;no jump
		 * @done:
		 * ...
		 */
		generator.flushDelayed()
		val numAlternatives = alternation.alternatives.size - 1
		val `$exit` = Label()
		for (index in 0..numAlternatives)
		{
			val `$next` = Label()
			val last = index == numAlternatives
			if (!last)
			{
				generator.emitBranchForward(this, `$next`)
			}
			val alternative = alternation.alternatives[index]
			// If a section checkpoint occurs within a numbered choice, we *do
			// not* pass the choice number as an argument.  Therefore nothing
			// new has been pushed for us to clean up at this point.
			alternative.emitOn(
				emptyListPhraseType(),
				generator,
				SHOULD_NOT_HAVE_ARGUMENTS)
			generator.emit(
				this, PUSH_LITERAL, indexForConstant(
					fromInt(index + 1)))
			if (!last)
			{
				generator.emitJumpForward(this, `$exit`)
				generator.emit(`$next`)
			}
		}
		generator.emit(`$exit`)
		generator.emitDelayed(
			this,
			TYPE_CHECK_ARGUMENT,
			indexForConstant(phraseType))
		return wrapState.processAfterPushedArgument(this, generator)
	}

	override fun toString(): String =
		"${this@NumberedChoice.javaClass.simpleName}($alternation)"

	override fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)
	{
		val literal = arguments!!.next()
		assert(
			literal.isInstanceOf(
				PhraseKind.LITERAL_PHRASE.mostGeneralType()))
		val index = literal.token().literal().extractInt
		builder.append('«')
		val alternative = alternation.alternatives[index - 1]
		alternative.printWithArguments(
			Collections.emptyIterator(), builder, indent)
		builder.append("»!")
	}

	override val shouldBeSeparatedOnLeft: Boolean
		// Starts with a guillemet, so don't bother with a leading space.
		get() = false

	override val shouldBeSeparatedOnRight: Boolean
		// Don't bother with a space after the close guillemet and
		// exclamation mark.
		get() = false

	override fun mightBeEmpty(phraseType: A_Type): Boolean =
		alternation.mightBeEmpty(bottom)

	override fun checkListStructure(phrase: A_Phrase): Boolean = true
}
