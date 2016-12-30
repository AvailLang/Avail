/**
 * MalformedPragmaException.java
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

package com.avail.exceptions;

/**
 * A {@code MalformedPragmaException} is thrown when a pragma within a module
 * header is malformed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class MalformedPragmaException
extends Exception
{
	/** The serial version identifier. */
	private static final long serialVersionUID = -7584923574827897383L;

	/**
	 * A {@link String} that describes the problem with the malformed pragma.
	 */
	private final String problem;

	/**
	 * Construct a new {@link MalformedPragmaException} with a string that
	 * describes the problem.
	 *
	 * @param problem
	 *        A {@link String} that describes what was malformed about the
	 *        pragma.
	 */
	public MalformedPragmaException (
		final String problem)
	{
		this.problem = problem;
	}

	/**
	 * Answer a description of how the pragma is malformed.
	 *
	 * @return A description of what is wrong with the encountered pragma.
	 */
	public String problem ()
	{
		return problem;
	}
}
