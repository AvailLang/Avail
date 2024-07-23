/*
 * CheckIndent.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

import avail.compiler.IncreaseIndent
import avail.compiler.MatchIndent
import avail.compiler.ForbidIncreaseIndent
import avail.compiler.splitter.CheckIndent.IndentationMatchType
import avail.compiler.splitter.MessageSplitter.Metacharacter
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.types.A_Type

/**
 * A [CheckIndent] expression is an occurrence of either the
 * [Metacharacter.INCREASE_INDENT] ("⇥") or [Metacharacter.MATCH_INDENT] ("↹")
 * in a message name.  The parse position, after skipping any whitespace, must
 * be immediately after whitespace that starts a line, otherwise the parse
 * theory is rejected.  That leading whitespace is compared to the whitespace
 * that occurred on the line at which the current phrase began (including a
 * leading argument, if any).  If the [indentationMatchType] is [MatchIndent],
 * the indentation strings must be equal to proceed past this test.  If the
 * match type is [IncreaseIndent], the current indentation must be an extension
 * of the original indentation to pass.
 *
 * @property indentationMatchType
 *   An [IndentationMatchType] that selects the kind of matching to be
 *   performed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a `CheckIndent`.
 *
 * @param startInName
 *   The [CheckIndent]'s position in the message name.
 * @param pastEndInName
 *   Just past the [CheckIndent] in the message name.
 * @param indentationMatchType
 *   An [IndentationMatchType] that selects the kind of matching to be
 *   performed.
 */
internal class CheckIndent constructor(
	startInName: Int,
	pastEndInName: Int,
	private val indentationMatchType: IndentationMatchType
) : Expression(startInName, pastEndInName)
{
	/**
	 * [IndentationMatchType] selects the kind of matching to be performed.
	 */
	enum class IndentationMatchType
	{
		/** The indentation must match the original indentation. */
		MatchIndent,

		/** The indentation must be an extension of the original indentation. */
		IncreaseIndent,

		/**
		 * The indentation must not be an increase of the original indentation.
		 */
		ForbidIncreaseIndent
	}

	override fun applyCaseInsensitive(): CheckIndent = this

	override fun children(): List<Expression> = emptyList()

	override fun checkType(argumentType: A_Type, sectionNumber: Int)
	{
		throw AssertionError(
			"checkType() should not be called for a CheckIndent expression")
	}

	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		// Tidy up any partially-constructed groups and invoke the
		// appropriate prefix function.  Note that the partialListsCount is
		// constrained to always be at least one here.
		generator.flushDelayed()
		when (indentationMatchType)
		{
			IndentationMatchType.MatchIndent ->
				generator.emit(this, MatchIndent)
			IndentationMatchType.IncreaseIndent ->
				generator.emit(this, IncreaseIndent)
			IndentationMatchType.ForbidIncreaseIndent ->
				generator.emit(this, ForbidIncreaseIndent)
		}
		return wrapState
	}

	override fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)
	{
		when (indentationMatchType)
		{
			IndentationMatchType.MatchIndent -> builder.appendCodePoint(
				Metacharacter.MATCH_INDENT.codepoint)
			IndentationMatchType.IncreaseIndent -> builder.appendCodePoint(
				Metacharacter.INCREASE_INDENT.codepoint)
			IndentationMatchType.ForbidIncreaseIndent ->
				builder.appendCodePoint(
					Metacharacter.FORBID_INCREASE_INDENT.codepoint)
		}
	}

	override val shouldBeSeparatedOnLeft: Boolean get() = true

	override val shouldBeSeparatedOnRight: Boolean get() = true

	override fun mightBeEmpty(phraseType: A_Type) = true

	override fun checkListStructure(phrase: A_Phrase): Boolean =
		throw RuntimeException(
			"checkListStructure() inapplicable for CheckIndent.")
}
