/**
 * RenamesFileParserException.java
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

package com.avail.builder;

/**
 * {@code RenamesFileParserException} is thrown by a {@link RenamesFileParser}
 * when a {@linkplain RenamesFileParser#parse() parse} fails for any reason.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class RenamesFileParserException
extends Exception
{
	/** The serial version identifier. */
	private static final long serialVersionUID = -7113513880608719974L;

	/**
	 * Construct a new {@link RenamesFileParserException}.
	 *
	 * @param message The detail message.
	 */
	RenamesFileParserException (final String message)
	{
		super(message);
	}

	/**
	 * Construct a new {@link RenamesFileParserException}.
	 *
	 * @param cause The original {@link Throwable} that caused this {@linkplain
	 *              RenamesFileParserException exception}.
	 */
	RenamesFileParserException (final Throwable cause)
	{
		super(cause);
	}
}
