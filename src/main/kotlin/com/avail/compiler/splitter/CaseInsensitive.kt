/*
 * CaseInsensitive.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.compiler.ParsingOperation
import com.avail.compiler.splitter.MessageSplitter.Metacharacter
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.types.A_Type
import com.avail.exceptions.SignatureException

/**
 * `CaseInsensitive` is a special decorator [expression][Expression] that causes
 * the decorated expression's keywords to generate
 * [parse&#32;instructions][ParsingOperation] that cause case insensitive
 * parsing. It is indicated by a trailing [tilde][Metacharacter.TILDE] ("~").
 *
 * @property expression
 *   The [expression][Expression] whose keywords should be matched
 *   case-insensitively.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `CaseInsensitive`.
 *
 * @param positionInName
 *   The position of this expression in the message name.
 * @param expression
 *   The [expression][Expression] whose keywords should be matched
 *   case-insensitively.
 */
internal class CaseInsensitive constructor(
	positionInName: Int,
	val expression: Expression) : Expression(positionInName)
{
	override val yieldsValue
		get() = expression.yieldsValue

	override val isGroup
		get() = expression.isGroup

	override val isLowerCase: Boolean
		get()
		{
			assert(expression.isLowerCase)
			return true
		}

	// It's already case-insensitive.
	override fun applyCaseInsensitive() = this

	init
	{
		if (expression.canBeReordered)
		{
			explicitOrdinal = expression.explicitOrdinal
			expression.explicitOrdinal = -1
		}
	}

	override val underscoreCount: Int
		get() = expression.underscoreCount

	override fun extractSectionCheckpointsInto(
			sectionCheckpoints: MutableList<SectionCheckpoint>) =
		expression.extractSectionCheckpointsInto(sectionCheckpoints)

	@Throws(SignatureException::class)
	override fun checkType(
		argumentType: A_Type,
		sectionNumber: Int) =
		expression.checkType(argumentType, sectionNumber)

	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		val oldInsensitive = generator.caseInsensitive
		generator.caseInsensitive = true
		val newWrapState = expression.emitOn(phraseType, generator, wrapState)
		generator.caseInsensitive = oldInsensitive
		return newWrapState
	}

	override fun toString(): String =
		"${this@CaseInsensitive.javaClass.simpleName}($expression)"

	override fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)
	{
		expression.printWithArguments(
			arguments,
			builder,
			indent)
		builder.append('~')
	}

	override val shouldBeSeparatedOnLeft: Boolean
		get() = expression.shouldBeSeparatedOnLeft

	override val shouldBeSeparatedOnRight: Boolean
		// Since we show the tilde (~) after the subexpression, and since
		// case insensitivity is most likely to apply to textual tokens, we
		// visually emphasize the tilde by ensuring a space follows it.
		get() = true

	override fun mightBeEmpty(phraseType: A_Type) =
		expression.mightBeEmpty(phraseType)

	override fun checkListStructure(phrase: A_Phrase): Boolean =
		expression.checkListStructure(phrase)
}
