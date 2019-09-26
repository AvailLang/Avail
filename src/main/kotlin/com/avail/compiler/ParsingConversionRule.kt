/*
 * ParsingConversionRule.kt
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

package com.avail.compiler

import com.avail.compiler.scanning.LexingState
import com.avail.descriptor.A_Phrase
import com.avail.descriptor.AvailObject
import com.avail.descriptor.IntegerDescriptor
import com.avail.descriptor.ListPhraseDescriptor
import com.avail.descriptor.LiteralPhraseDescriptor
import com.avail.descriptor.PhraseDescriptor

import com.avail.descriptor.IntegerDescriptor.fromInt
import com.avail.descriptor.LiteralPhraseDescriptor.literalNodeFromToken
import com.avail.descriptor.LiteralPhraseDescriptor.syntheticLiteralNodeFor
import com.avail.descriptor.LiteralTokenDescriptor.literalToken
import com.avail.descriptor.MacroSubstitutionPhraseDescriptor.newMacroSubstitution
import com.avail.descriptor.StringDescriptor.stringFrom

/**
 * A `ParsingConversionRule` describes how to convert the argument at the
 * top of the parsing stack from one [phrase][PhraseDescriptor] to
 * another.
 *
 * @property number
 *   The rule number.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `ParsingConversionRule`.
 *
 * @param number
 *   The rule number.
 */
enum class ParsingConversionRule constructor(val number: Int)
{
	/**
	 * `0` - No conversion.
	 */
	@Suppress("unused")
	NO_CONVERSION(0)
	{
		override fun convert(
			compilationContext: CompilationContext,
			lexingState: LexingState,
			input: A_Phrase,
			continuation: (A_Phrase) -> Unit,
			onProblem: (Throwable) -> Unit)
		{
			continuation(input)
		}
	},

	/**
	 * `1` - Convert a [list][ListPhraseDescriptor] into a [literal
	 * phrase][LiteralPhraseDescriptor] that yields an
	 * [integer][IntegerDescriptor] representing the
	 * [size][AvailObject.tupleSize] of the original list.
	 */
	LIST_TO_SIZE(1)
	{
		override fun convert(
			compilationContext: CompilationContext,
			lexingState: LexingState,
			input: A_Phrase,
			continuation: (A_Phrase) -> Unit,
			onProblem: (Throwable) -> Unit)
		{
			val expressions = input.expressionsTuple()
			val count = fromInt(expressions.tupleSize())
			val token = literalToken(
				stringFrom(count.toString()),
				lexingState.position,
				lexingState.lineNumber,
				count)
			continuation(literalNodeFromToken(token))
		}
	},

	/**
	 * `2` - Immediately evaluate the [phrase][PhraseDescriptor] on the stack to
	 * produce a value.  Replace the phrase with a literal phrase holding this
	 * value.
	 */
	EVALUATE_EXPRESSION(2)
	{
		override fun convert(
			compilationContext: CompilationContext,
			lexingState: LexingState,
			input: A_Phrase,
			continuation: (A_Phrase) -> Unit,
			onProblem: (Throwable) -> Unit)
		{
			compilationContext.evaluatePhraseAtThen(
				lexingState,
				input,
				{ value ->
					continuation(
						newMacroSubstitution(
							input, syntheticLiteralNodeFor(value)))
				},
				onProblem)
		}
	};

	/**
	 * Convert an input [AvailObject] into an output AvailObject, using the
	 * specific conversion rule's implementation.
	 *
	 * @param compilationContext
	 *   The [CompilationContext] to use during conversion, if needed.
	 * @param lexingState
	 *   The [LexingState] after the phrase.
	 * @param input
	 *   The phrase to be converted.
	 * @param continuation
	 *   What to do with the replacement phrase.
	 * @param onProblem
	 *   What to do if there's a problem.
	 */
	abstract fun convert(
		compilationContext: CompilationContext,
		lexingState: LexingState,
		input: A_Phrase,
		continuation: (A_Phrase) -> Unit,
		onProblem: (Throwable) -> Unit)

	companion object
	{
		/** An array of all [ParsingConversionRule]s.  */
		private val all = values()

		/**
		 * Lookup the specified `ParsingConversionRule conversion rule` by
		 * number.
		 *
		 * @param number
		 *   The rule number.
		 * @return
		 *   The appropriate parsing conversion rule.
		 */
		fun ruleNumber(number: Int): ParsingConversionRule
		{
			if (number < all.size)
			{
				return all[number]
			}
			throw RuntimeException("reserved conversion rule number")
		}
	}
}
