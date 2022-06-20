/*
 * ArgumentForMacroOnly.kt
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

import avail.compiler.ParsingOperation.CHECK_ARGUMENT
import avail.compiler.ParsingOperation.PARSE_TOP_VALUED_ARGUMENT
import avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT
import avail.compiler.splitter.MessageSplitter.Companion.indexForConstant
import avail.compiler.splitter.MessageSplitter.Metacharacter
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.ListPhraseDescriptor
import avail.descriptor.types.A_Type

/**
 * An `ArgumentForMacroOnly` is the translation of an
 * [underscore][Metacharacter.UNDERSCORE] (_) in a message name, followed
 * immediately by an [exclamation&#32;mark][Metacharacter.EXCLAMATION_MARK] (!).
 * It indicates where an argument is expected – but the argument is allowed to
 * be ⊤-valued or ⊥-valued.  Functions (and therefore method definitions) may
 * not take arguments of type ⊤ or ⊥, so this mechanism is restricted to use by
 * macros, where the phrases themselves (including phrases yielding ⊤ or ⊥) are
 * what get passed to the macro body.
 *
 * Because [list&#32;phrases][ListPhraseDescriptor] have an
 * [expression&#32;type][A_Phrase.phraseExpressionType] that depends on the
 * types of the `expressionType` of each subexpression, and because ⊥ as an
 * element in a tuple type makes the entire resulting tuple type also be ⊥, we
 * can't just directly accept an expression that produces ⊤ or ⊥ (e.g., the
 * resulting list's apparent cardinality would be lost, as ⊥ is a subtype of
 * every tuple type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct an `ArgumentForMacroOnly`, given the one-based position of the
 * token in the name, and the absolute index of this argument in the entire
 * message name.
 *
 * @param positionInName
 *   The one-based position of the start of the token in the message name.
 * @param absoluteUnderscoreIndex
 *   The one-based index of this argument within the entire message name's list
 *   of arguments.
 */
internal class ArgumentForMacroOnly constructor(
	positionInName: Int,
	absoluteUnderscoreIndex: Int)
: Argument(positionInName, absoluteUnderscoreIndex)
{
	/**
	 * Parse an argument expression which might be top-valued.
	 *
	 * @param phraseType
	 *   The type of the phrase being parsed at and inside this parse point.
	 *   Note that when this is for a list phrase type, it's used for unrolling
	 *   leading iterations of loops up to the end of the variation (typically
	 *   just past the list phrase's tuple type's [A_Type.typeTuple]).
	 * @param generator
	 *   The [InstructionGenerator] that accumulates the parsing instructions.
	 * @param wrapState
	 *   The initial [WrapState] that indicates what has been pushed and what
	 *   the desired stack structure is.
	 * @return
	 *   The resulting [WrapState], indicating the state of the stack.
	 */
	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		generator.flushDelayed()
		generator.emit(this, PARSE_TOP_VALUED_ARGUMENT)
		generator.emitDelayed(this, CHECK_ARGUMENT, absoluteUnderscoreIndex)
		generator.emitDelayed(
			this, TYPE_CHECK_ARGUMENT, indexForConstant(phraseType))
		return wrapState
	}
}
