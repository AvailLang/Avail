/*
 * CompilerSolution.kt
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

import com.avail.descriptor.A_Phrase
import com.avail.descriptor.PhraseDescriptor

/**
 * An `CompilerSolution` is a record of having parsed some
 * [phrase][PhraseDescriptor] from a stream of tokens, combined with the
 * [position and state][ParserState] of the parser after the phrase was parsed.
 *
 * @property endState
 *   The parse position after this solution.
 * @property phrase
 *   A phrase that ends at the specified ending position.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `CompilerSolution`.
 *
 * @param endState
 *   The [ParserState] after the specified phrase's tokens.
 * @param phrase
 *   The [phrase][PhraseDescriptor] that ends at the specified `endState`.
 */
internal class CompilerSolution constructor(
	internal val endState: ParserState,
	internal val phrase: A_Phrase) : AbstractSolution
{
	override fun equals(other: Any?): Boolean
	{
		if (other === null)
		{
			return false
		}
		if (other !is CompilerSolution)
		{
			return false
		}
		return endState == other.endState && phrase.equals(other.phrase)
	}

	override fun hashCode() = phrase.hash()

	override fun toString(): String
	{
		return "Solution(@${endState.position}: ${endState.clientDataMap}) " +
			   "= $phrase"
	}
}
