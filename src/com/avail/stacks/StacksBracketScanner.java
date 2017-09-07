/**
 * StacksBracketScanner.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.stacks;

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.TokenDescriptor;

import java.util.ArrayList;
import java.util.List;


/**
 * A Stacks scanner that tokenizes the lexeme of a {@link BracketedStacksToken}.
 *
 * @author Richard Arriaga &lt;Rich@availlang.org&gt;
 */
public final class StacksBracketScanner extends AbstractStacksScanner
{
	/**
	 * The module file name without the path.
	 */
	private final String moduleLeafName;

	/**
	 * Construct a new {@link StacksBracketScanner}.
	 * @param bracketToken
	 *
	 */
	private StacksBracketScanner (final BracketedStacksToken bracketToken)
	{
		this.moduleName = bracketToken.moduleName;
		this.moduleLeafName =
			moduleName.substring(moduleName.lastIndexOf(' ') + 1);

		final String bracketString =
			bracketToken.lexeme();
		this.tokenString(bracketString);
		this.outputTokens = new ArrayList<>(
			tokenString().length() / 20);
		this.lineNumber(bracketToken.lineNumber());
		this.filePosition(bracketToken.position());
		this.position(1);
		this.beingTokenized = new StringBuilder();
	}

	/**
	 * Answer whether we have exhausted the comment string.
	 *
	 * @return Whether we are finished scanning.
	 */
	@InnerAccess @Override
	boolean atEnd ()
	{
		return position() == tokenString().length() - 1;
	}

	/**
	 * Answer the {@linkplain List list} of {@linkplain TokenDescriptor tokens}
	 * that comprise a {@linkplain CommentTokenDescriptor Avail comment}.
	 *
	 * @param bracketToken
	 *		A {@linkplain BracketedStacksToken comment bracket} to be
	 *		tokenized.
	 * @return a {@link List list} of all tokenized words in the {@link
	 * 		CommentTokenDescriptor Avail comment}.
	 * @throws StacksScannerException If scanning fails.
	 */
	public static List<AbstractStacksToken> scanBracketString (
		final BracketedStacksToken bracketToken)
		throws StacksScannerException
	{
		final StacksBracketScanner scanner =
			new StacksBracketScanner(bracketToken);
		scanner.scan();
		return scanner.outputTokens;
	}

	/**
	 * Scan the already-specified {@link String} to produce {@linkplain
	 * #outputTokens tokens}.
	 *
	 * @throws StacksScannerException
	 */
	private void scan ()
		throws StacksScannerException
	{
		while (!atEnd())
		{
			startOfToken(position());
			ScannerAction.forCodePoint(next()).scan(this);
		}
	}

	/**
	 * The module file name without the path.
	 * @return the moduleLeafName
	 */
	public String moduleLeafName ()
	{
		return moduleLeafName;
	}
}
