/*
 * VariableQuote.kt
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

import com.avail.compiler.ParsingOperation.CHECK_ARGUMENT
import com.avail.compiler.ParsingOperation.PARSE_VARIABLE_REFERENCE
import com.avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT
import com.avail.compiler.splitter.MessageSplitter.Companion.indexForConstant
import com.avail.compiler.splitter.MessageSplitter.Metacharacter
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.ReferencePhraseDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.variables.VariableDescriptor
import java.util.IdentityHashMap

/**
 * A `VariableQuote` is an occurrence of [up&#32;arrow][Metacharacter.UP_ARROW]
 * (↑) after an underscore in a message name. It indicates that the expression
 * must be the name of a [variable][VariableDescriptor] that is currently
 * in-scope. It produces a [reference][ReferencePhraseDescriptor] to the
 * variable, rather than extracting its value.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a `VariableQuote`, given the one-based position of the
 * token in the name, and the absolute index of this argument in the entire
 * message name.
 *
 * @param positionInName
 *   The one-based position of the start of the token in the message name.
 * @param absoluteUnderscoreIndex
 *   The one-based index of this argument within the entire message name's list
 *   of arguments.
 */
internal class VariableQuote constructor(
	positionInName: Int,
	absoluteUnderscoreIndex: Int)
: Argument(positionInName, absoluteUnderscoreIndex)
{
	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		generator.flushDelayed()
		generator.emit(this, PARSE_VARIABLE_REFERENCE)
		generator.emitDelayed(this, CHECK_ARGUMENT, absoluteUnderscoreIndex)
		generator.emitDelayed(
			this,
			TYPE_CHECK_ARGUMENT,
			indexForConstant(phraseType))
		return wrapState
	}

	override fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)
	{
		// Describe the variable reference that was parsed as this argument.
		arguments!!.next().printOnAvoidingIndent(
			builder,
			IdentityHashMap(),
			indent + 1)
		builder.append('↑')
	}
}
