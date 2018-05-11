/*
 * StacksScannerException.java
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

package com.avail.stacks;

import com.avail.descriptor.A_Token;
import com.avail.descriptor.CommentTokenDescriptor;

/**
 * An Stacks scanner exception
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksScannerException extends Exception
{
	/**
	 *
	 */
	private static final long serialVersionUID = 1941203667489406705L;

	/**
	 * The {@link StacksScanner} that failed.
	 */
	final AbstractStacksScanner failedScanner;

	/**
	 * Answer the name of the module that failed lexical scanning.
	 *
	 * @return A {@link String} describing which module failed lexical scanning.
	 */
	public String moduleName ()
	{
		return failedScanner.moduleName();
	}

	/**
	 * Return the file position at which the {@link StacksScanner} failed.
	 *
	 * @return The position in the file at which the scanner failed.
	 */
	public int failurePosition ()
	{
		return failedScanner.position();
	}

	/**
	 * Return the line number at which the {@link StacksScanner} failed.
	 *
	 * @return The line number at which the scanner failed.
	 */
	public int failureLineNumber ()
	{
		return failedScanner.lineNumber();
	}

	/**
	 * Construct a new {@link StacksScannerException}.
	 *
	 * @param message
	 *            The error message indicating why the {@link StacksScanner}
	 *            failed.
	 * @param failedScanner
	 *            The AbstractStacksScanner that failed, positioned to the failure point.
	 */
	public StacksScannerException (
		final String message,
		final AbstractStacksScanner failedScanner)
	{
		super(message);
		this.failedScanner = failedScanner;
	}

	/**
	 * Construct a new {@link StacksScannerException}.  Plug in a dummy {@link
	 * StacksScanner}.
	 * @param cause
	 *            The original problem to be treated as a scanner problem.
	 * @param moduleName
	 *            The name of the module that failed lexical scanning.
	 * @param availComment
	 *		The {@link CommentTokenDescriptor Avail comment} to be tokenized.
	 * @throws StacksCommentBuilderException
	 */
	public StacksScannerException (
		final Throwable cause,
		final String moduleName,
		final A_Token availComment) throws StacksCommentBuilderException
	{
		super(cause);
		try
		{
			StacksScanner.processCommentString(availComment,moduleName(), null);
			assert false : "Should have thrown exception";
			// And throw in case assertions are off.  Keeps Java compiler happy.
			throw new RuntimeException("Should have thrown exception");
		}
		catch (final StacksScannerException contrivedException)
		{
			this.failedScanner = contrivedException.failedScanner;
		}
	}
}
