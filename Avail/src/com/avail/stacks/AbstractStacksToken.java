/**
 * AbstractStacksToken.java
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
 * The abstract form of a token in a Stacks comment that has been lexed.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public abstract class AbstractStacksToken
{

	/**
	 * The string exactly as it appeared in the source comment.
	 */
	final String lexeme;

	/**
	 * The string exactly as it appeared in the source comment.
	 */
	final String moduleName;

	/**
	 * The line number where the token occurs/begins.
	 */
	final int lineNumber;

	/**
	 * The absolute start position of the token.
	 */
	final int position;

	/**
	 * The position on the line where the token starts.
	 */
	final int startOfTokenLinePostion;

	/**
	 * Is this a section token tag?
	 */
	boolean isSectionToken;

	/**
	 *
	 * @return
	 * 		Is this a section token tag?
	 */
	public boolean isSectionToken ()
	{
		return isSectionToken;
	}

	/**
	 * Construct a new {@link AbstractStacksToken}.
	 * @param string
	 * 		The string to be tokenized.
	 * @param lineNumber
	 * 		The line number where the token occurs/begins
	 * @param postion
	 * 		The absolute start position of the token
	 * @param startOfTokenLinePostion
	 * 		The position on the line where the token starts.
	 * @param moduleName
	 * 		The module this token is in.
	 */
	public AbstractStacksToken (
		final String string,
		final int lineNumber,
		final int postion,
		final int startOfTokenLinePostion,
		final String moduleName)
	{
		this.lexeme = string;
		this.lineNumber = lineNumber;
		this.position = postion;
		this.startOfTokenLinePostion = startOfTokenLinePostion;
		this.moduleName = moduleName;
		this.isSectionToken = false;
	}

	/**
	 * Provide the token's string representation.
	 * @return
	 */
	public String lexeme()
	{
		return lexeme;
	}

	/**
	 * Provide the token's string representation.
	 * @return
	 */
	public String quotedLexeme()
	{
		return new StringBuilder()
	    	.append('"')
	    	.append(lexeme)
	    	.append('"')
	    	.toString();
	}

	/**
	 * Provide the token's string representation.
	 * @return
	 */
	public int position()
	{
		return position;
	}

	/**
	 * Provide the token's string representation.
	 * @return
	 */
	public int lineNumber()
	{
		return lineNumber;
	}

	/**
	 * Provide the token's string representation.
	 * @return
	 */
	public int startOfTokenLinePostion()
	{
		return startOfTokenLinePostion;
	}

	/**
	 * Create HTML content of the token
	 * @param htmlFileMap
	 * 		The map of all HTML files in Stacks
	 * @return the HTML tagged content
	 */
	public String toHTML(final HTMLFileMap htmlFileMap)
	{
		return lexeme();
	}
}
