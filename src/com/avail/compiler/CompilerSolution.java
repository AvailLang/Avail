/*
 * CompilerSolution.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.compiler;

import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.PhraseDescriptor;

import javax.annotation.Nullable;

/**
 * An {@code CompilerSolution} is a record of having parsed some {@linkplain
 * PhraseDescriptor phrase} from a stream of tokens, combined with the
 * {@linkplain ParserState position and state} of the parser after the phrase
 * was parsed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class CompilerSolution implements AbstractSolution
{

	/**
	 * The parse position after this solution.
	 */
	private final ParserState endState;

	/**
	 * A phrase that ends at the specified ending position.
	 */
	private final A_Phrase phrase;


	/**
	 * Answer the {@linkplain ParserState position} just after the phrase's
	 * tokens.
	 *
	 * @return the position after which this solution ends.
	 */
	ParserState endState ()
	{
		return endState;
	}

	/**
	 * The {@linkplain PhraseDescriptor phrase} that this solution
	 * represents.
	 *
	 * @return a phrase.
	 */
	A_Phrase phrase ()
	{
		return phrase;
	}

	@Override
	public boolean equals (final @Nullable Object obj)
	{
		if (!(obj instanceof CompilerSolution))
		{
			return false;
		}
		final CompilerSolution other = (CompilerSolution) obj;
		return endState.equals(other.endState)
			&& phrase.equals(other.phrase);
	}

	@Override
	public int hashCode ()
	{
		return phrase.hash();
	}

	@Override
	public String toString ()
	{
		return
			"Solution(@"
				+ endState.position()
				+ ": "
				+ endState.clientDataMap
				+ ") = "
				+ phrase();
	}

	/**
	 * Construct a new {@link CompilerSolution}.
	 *
	 * @param endState
	 *            The {@link ParserState} after the specified phrase's
	 *            tokens.
	 * @param phrase
	 *            The {@linkplain PhraseDescriptor phrase} that ends at
	 *            the specified endState.
	 */
	CompilerSolution (
		final ParserState endState,
		final A_Phrase phrase)
	{
		this.endState = endState;
		this.phrase = phrase;
	}
}
