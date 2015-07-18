/**
 * AvailScannerResult.java
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

package com.avail.compiler.scanning;

import java.util.Collections;
import java.util.List;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.TokenDescriptor;

/**
 * An {@code AvailScannerResult} holds the results of a {@linkplain
 * AvailScanner scan} of an Avail source {@linkplain ModuleDescriptor module}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public final class AvailScannerResult
{
	/**
	 * The complete source code that produced the {@linkplain TokenDescriptor
	 * tokens}.
	 */
	private final String source;

	/**
	 * Answer the complete source code that produced the {@linkplain
	 * TokenDescriptor tokens}.
	 *
	 * @return The source.
	 */
	public String source ()
	{
		return source;
	}

	/**
	 * The complete {@linkplain Collections#unmodifiableList(List) unmodifiable}
	 * collection of {@linkplain TokenDescriptor tokens} that were produced by
	 * the {@linkplain AvailScanner scanner}.
	 */
	private final List<A_Token> outputTokens;

	/**
	 * Answer the complete {@linkplain Collections#unmodifiableList(List)
	 * unmodifiable} collection of {@linkplain TokenDescriptor tokens} that were
	 * produced by the {@linkplain AvailScanner scanner}.
	 *
	 * @return Some tokens.
	 */
	public List<A_Token> outputTokens ()
	{
		return outputTokens;
	}

	/**
	 * The complete {@linkplain Collections#unmodifiableList(List) unmodifiable}
	 * collection of {@linkplain CommentTokenDescriptor comment tokens} that
	 * were produced by the {@linkplain AvailScanner scanner}.
	 */
	private final List<A_Token> commentTokens;

	/**
	 * Answer the complete {@linkplain Collections#unmodifiableList(List)
	 * unmodifiable} collection of {@linkplain CommentTokenDescriptor
	 * tokens} that were produced by the {@linkplain AvailScanner scanner}.
	 *
	 * @return Some comment tokens.
	 */
	public List<A_Token> commentTokens ()
	{
		return commentTokens;
	}

	/**
	 * Construct a new {@link AvailScannerResult}.
	 *
	 * @param source
	 *        The complete source code that produced the {@linkplain
	 *        TokenDescriptor tokens}.
	 * @param outputTokens
	 *        The complete collection of {@linkplain TokenDescriptor tokens}
	 *        that were produced by the {@linkplain AvailScanner scanner}.
	 * @param commentTokens
	 *        The complete collection of {@linkplain CommentTokenDescriptor
	 *        comment tokens} that were produced by the {@linkplain AvailScanner
	 *        scanner}.
	 */
	AvailScannerResult (
		final String source,
		final List<A_Token> outputTokens,
		final List<A_Token> commentTokens)
	{
		this.source = source;
		this.outputTokens = Collections.unmodifiableList(outputTokens);
		this.commentTokens = Collections.unmodifiableList(commentTokens);
	}
}
