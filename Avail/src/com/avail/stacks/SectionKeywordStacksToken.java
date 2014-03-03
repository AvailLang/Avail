/**
 * SectionKeywordStacksToken.java
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

/**
 * A keyword that indicates a full section of tokens that "belong to it" will
 * follow.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class SectionKeywordStacksToken extends KeywordStacksToken
{

	/**
	 * Construct a new {@link SectionKeywordStacksToken}.
	 *
	 * @param string
	 * 		The string to be tokenized.
	 * @param lineNumber
	 * 		The line number where the token occurs/begins
	 * @param postion
	 * 		The absolute start position of the token
	 * @param startOfTokenLinePostion
	 * 		The position on the line where the token starts.
	 * @param moduleName
	 * 		The module the token is in
	 */
	private SectionKeywordStacksToken (
		final String string,
		final int lineNumber,
		final int postion,
		final int startOfTokenLinePostion,
		final String moduleName)
	{
		super(string, lineNumber, postion, startOfTokenLinePostion,moduleName);
		this.isSectionToken = true;
	}

	/**
	 *
	 * @param string
	 * 		The string to be tokenized.
	 * @param lineNumber
	 * 		The line number where the token occurs/begins
	 * @param postion
	 * 		The absolute start position of the token
	 * @param startOfTokenLinePostion
	 * 		The position on the line where the token starts.
	 * @param moduleName
	 * 		The module the token takes place in.
	 * @return a new {@link StacksToken stacks token}
	 */
	public static SectionKeywordStacksToken create (
		final String string,
		final int lineNumber,
		final int postion,
		final int startOfTokenLinePostion,
		final String moduleName)
	{
		return new SectionKeywordStacksToken(
			string, lineNumber, postion, startOfTokenLinePostion, moduleName);
	}
}
