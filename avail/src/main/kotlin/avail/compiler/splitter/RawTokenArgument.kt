/*
 * RawTokenArgument.kt
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

import avail.compiler.ParsingOperation.PARSE_ANY_RAW_TOKEN
import avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT
import avail.compiler.splitter.MessageSplitter.Companion.indexForConstant
import avail.compiler.splitter.MessageSplitter.Metacharacter
import avail.descriptor.types.A_Type

/**
 * A `RawTokenArgument` is an occurrence of [ellipsis][Metacharacter.ELLIPSIS]
 * (…) in a message name, followed by an
 * [exclamation&#32;mark][Metacharacter.EXCLAMATION_MARK] (!). It indicates
 * where *any* raw token is expected, which gets captured as an argument,
 * wrapped in a literal phrase.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a `RawTokenArgument`, given the one-based position of the token in
 * the name, and the absolute index of this argument in the entire message name.
 *
 * @param startInName
 *   The one-based position of the start of the token in the message name.
 * @param absoluteUnderscoreIndex
 *   The one-based index of this argument within the entire message name's list
 *   of arguments.
 */
internal open class RawTokenArgument constructor(
	startInName: Int,
	pastEndInName: Int,
	absoluteUnderscoreIndex: Int
) : Argument(startInName, pastEndInName, absoluteUnderscoreIndex)
{
	override fun emitOn(
		phraseType: A_Type,
		generator: InstructionGenerator,
		wrapState: WrapState): WrapState
	{
		generator.flushDelayed()
		generator.emit(this, PARSE_ANY_RAW_TOKEN)
		generator.emitDelayed(
			this,
			TYPE_CHECK_ARGUMENT,
			indexForConstant(phraseType))
		return wrapState
	}
}
