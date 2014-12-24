/**
 * MalformedMessageException.java
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

package com.avail.exceptions;

import com.avail.compiler.MessageSplitter;
import com.avail.utility.Generator;

/**
 * A {@code MalformedMessageException} is thrown when a method name is malformed
 * and therefore cannot be converted to parsing instructions.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @see SignatureException
 */
public final class MalformedMessageException
extends AvailException
{
	/** The serial version identifier. */
	private static final long serialVersionUID = -1566262280388678301L;

	/**
	 * A {@link Generator} that can produce a description of what the problem
	 * is with the message name.
	 */
	private final Generator<String> describer;

	/**
	 * Construct a new {@link MalformedMessageException} with the specified
	 * {@linkplain AvailErrorCode error code} and the specified {@link
	 * Generator} that describes the problem.
	 *
	 * @param errorCode
	 *        The {@linkplain AvailErrorCode error code}.
	 * @param describer
	 *        A {@link Generator} that produces a {@link String} describing what
	 *        was malformed about the signature that failed to be parsed by a
	 *        {@link MessageSplitter}.
	 */
	public MalformedMessageException (
		final AvailErrorCode errorCode,
		final Generator<String> describer)
	{
		super(errorCode);
		this.describer = describer;
	}

	/**
	 * Answer a description of how the signature is malformed.
	 *
	 * @return A description of what is wrong with the signature being analyzed.
	 */
	public String describeProblem ()
	{
		return describer.value();
	}
}
