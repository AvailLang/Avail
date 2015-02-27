/**
 * AvailCompilerCachedSolution.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import com.avail.compiler.AbstractAvailCompiler.ParserState;
import com.avail.descriptor.*;

/**
 * An {@code AvailCompilerCachedSolution} is a record of having parsed some
 * {@linkplain ParseNodeDescriptor parse node} from a stream of tokens,
 * combined with the {@linkplain ParserState position and state} of the parser
 * after the parse node was parsed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class AvailCompilerCachedSolution
{

	/**
	 * The parse position after this solution.
	 */
	final ParserState endState;

	/**
	 * A parse node that ends at the specified ending position.
	 */
	final A_Phrase parseNode;


	/**
	 * Answer the {@linkplain ParserState position} just after the parse node's
	 * tokens.
	 *
	 * @return the position after which this solution ends.
	 */
	ParserState endState ()
	{
		return endState;
	}

	/**
	 * The {@linkplain ParseNodeDescriptor parse node} that this solution
	 * represents.
	 *
	 * @return a parse node.
	 */
	A_Phrase parseNode ()
	{
		return parseNode;
	}


	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("Solution(@");
		builder.append(endState.position);
		builder.append(": ");
		builder.append(endState.clientDataMap);
		builder.append(") = ");
		builder.append(parseNode().toString());
		return builder.toString();
	}

	/**
	 * Construct a new {@link AvailCompilerCachedSolution}.
	 *
	 * @param endState
	 *            The {@link ParserState} after the specified parse node's
	 *            tokens.
	 * @param parseNode
	 *            The {@linkplain ParseNodeDescriptor parse node} that ends at
	 *            the specified endState.
	 */
	AvailCompilerCachedSolution (
		final ParserState endState,
		final A_Phrase parseNode)
	{
		this.endState = endState;
		this.parseNode = parseNode;
	}
}
