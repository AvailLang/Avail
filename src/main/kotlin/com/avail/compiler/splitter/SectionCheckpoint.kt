/*
 * SectionCheckpoint.kt
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

import com.avail.compiler.ParserState
import com.avail.compiler.ParsingOperation.PREPARE_TO_RUN_PREFIX_FUNCTION
import com.avail.compiler.ParsingOperation.RUN_PREFIX_FUNCTION
import com.avail.compiler.splitter.MessageSplitter.Metacharacter
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.types.A_Type

/**
 * An `SectionCheckpoint` expression is an occurrence of the
 * [section&#32;sign][Metacharacter.SECTION_SIGN] (§) in a message name.  It
 * indicates a position at which to save the argument expressions for the
 * message *up to this point*.  This value is captured in the [ParserState] for
 * subsequent use by primitive macros that need to know an outer message send's
 * initial argument expressions while parsing a subsequent argument expression
 * of the same message.
 *
 * In particular, the block definition macro has to capture its (optional)
 * argument declarations before parsing the (optional) label, declaration, since
 * the latter has to be created with a suitable continuation type that includes
 * the argument types.
 *
 * @property subscript
 *   The occurrence number of this `SectionCheckpoint`.  The section checkpoints
 *   are one-based and are numbered consecutively in the order in which they
 *   occur in the whole method name.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a `SectionCheckpoint`.
 *
 * @param positionInName
 *   The section checkpoint's position in the message name.
 * @param subscript
 *   This section checkpoint's one-based index.
 */
internal class SectionCheckpoint constructor(
	positionInName: Int,
	private val subscript: Int) : Expression(positionInName)
{
	override fun applyCaseInsensitive(): SectionCheckpoint = this

	override fun extractSectionCheckpointsInto(
		sectionCheckpoints: MutableList<SectionCheckpoint>)
	{
		sectionCheckpoints.add(this)
	}

	override fun checkType(argumentType: A_Type, sectionNumber: Int)
	{
		assert(false) {
			"checkType() should not be called for SectionCheckpoint expressions"
		}
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
		generator.emit(
			this,
			PREPARE_TO_RUN_PREFIX_FUNCTION,
			generator.partialListsCount)
		generator.emit(this, RUN_PREFIX_FUNCTION, subscript)
		return wrapState
	}

	override fun printWithArguments(
		arguments: Iterator<A_Phrase>?,
		builder: StringBuilder,
		indent: Int)
	{
		builder.append('§')
	}

	override val shouldBeSeparatedOnLeft: Boolean
		// The section symbol should always stand out.
		get() = true

	override val shouldBeSeparatedOnRight: Boolean
		// The section symbol should always stand out.
		get() = true

	override fun mightBeEmpty(phraseType: A_Type) = true
}
