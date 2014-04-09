/**
 * BracketedStacksToken.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import java.util.List;
/**
 * A stacks token representing a bracketed region in the comment.  This region
 * generally contains some sort of action such as a link.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class BracketedStacksToken extends RegionStacksToken
{
	/**
	 * The tokens that have been parsed so far.
	 */
	List<AbstractStacksToken> subTokens;


	/**
	 * Construct a new {@link BracketedStacksToken}.
	 *
	 * @param string
	 * 		The string to be tokenized.
	 * @param lineNumber
	 * 		The line number where the token occurs/begins
	 * @param position
	 * 		The absolute start position of the token
	 * @param startOfTokenLinePostion
	 * 		The position on the line where the token starts.
	 * @param moduleName
	 * @throws StacksScannerException
	 */
	public BracketedStacksToken (
		final String string,
		final int lineNumber,
		final int position,
		final int startOfTokenLinePostion,
		final String moduleName) throws StacksScannerException
	{
		super(string, lineNumber, position,
			startOfTokenLinePostion, moduleName, '\"', '\"');
		this.subTokens = StacksBracketScanner.scanBracketString(this);
	}
	/**
	 *  Statically create a new {@link BracketedStacksToken}.
	 * @param string
	 * 		The string to be tokenized.
	 * @param lineNumber
	 * 		The line number where the token occurs/begins
	 * @param position
	 * 		The absolute start position of the token
	 * @param startOfTokenLinePostion
	 * 		The position on the line where the token starts.
	 * @param moduleName
	 * 		The name of the module the token is in.
	 * @return a new {@link BracketedStacksToken stacks token}
	 * @throws StacksScannerException
	 */
	public static BracketedStacksToken create (
		final String string,
		final int lineNumber,
		final int position,
		final int startOfTokenLinePostion,
		final String moduleName) throws StacksScannerException
	{
		return new BracketedStacksToken(
			string, lineNumber, position, startOfTokenLinePostion, moduleName);
	}

	@Override
	public String toHTML(final HTMLFileMap htmlFileMap)
	{
		//TODO update with parsed tags with appropriate links.
		final StringBuilder stringBuilder = new StringBuilder();
		final int listSize = subTokens.size();
		if (listSize > 0)
		{
			for (int i = 0; i < listSize - 1; i++)
			{
				stringBuilder
					.append(subTokens.get(i).toHTML(htmlFileMap)).append(" ");
			}
			stringBuilder
				.append(subTokens.get(listSize - 1).toHTML(htmlFileMap));
		}

		return stringBuilder.toString();
	}
}
