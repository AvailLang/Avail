/**
 * StacksCommentBuilderException.java
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
 * An exception that occurs in a {@link CommentImplementationBuilder}
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksCommentBuilderException extends Exception
{
	/**
	 *
	 */
	private static final long serialVersionUID = 1969888642664158924L;

	/**
	 * The error {@link CommentImplementationBuilder builder} in question
	 */
	final CommentImplementationBuilder failedBuilder;

	/**
	 * Answer the name of the module that failed lexical scanning.
	 *
	 * @return A {@link String} describing which module failed lexical scanning.
	 */
	public String moduleName ()
	{
		return failedBuilder.moduleName();
	}

	/**
	 * Construct a new {@link StacksCommentBuilderException}.
	 *
	 * @param message
	 * 		The error message.
	 * @param failedBuilder
	 * 		The error {@link CommentImplementationBuilder builder} in question
	 */
	public StacksCommentBuilderException (final String message,
		final CommentImplementationBuilder failedBuilder)
	{
		super(message);
		this.failedBuilder = failedBuilder;
	}

	/**
	 * Construct a new {@link StacksCommentBuilderException}.
	 *
	 * @param cause
	 * 		The original problem to be treated as a builder problem.
	 * @param moduleName
	 * 		The name of the module that failed lexical scanning.
	 * @param lineNumber
	 * 		The line number of the comment
	 */
	public StacksCommentBuilderException (final Throwable cause,
		final String moduleName, final int lineNumber
		)
	{
		super(cause);
		try
		{
			CommentImplementationBuilder.createBuilder(moduleName, lineNumber);
			assert false : "Should have thrown exception";
			// And throw in case assertions are off.  Keeps Java compiler happy.
			throw new RuntimeException("Should have thrown exception");
		}
		catch (final StacksCommentBuilderException contrivedException)
		{
			this.failedBuilder = contrivedException.failedBuilder;
		}
	}
}
