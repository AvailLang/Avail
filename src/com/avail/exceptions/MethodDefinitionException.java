/**
 * MethodDefinitionException.java
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

package com.avail.exceptions;

import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.MethodDefinitionDescriptor;
import com.avail.descriptor.MethodDescriptor;

import static com.avail.exceptions.AvailErrorCode.*;

/**
 * A {@code MethodDefinitionException} is raised whenever an error condition is
 * discovered that pertains to failed resolution of a {@linkplain
 * MethodDescriptor method} or {@linkplain MethodDefinitionDescriptor method
 * definition} or failed invocation of a method definition.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class MethodDefinitionException
extends AvailException
{
	/** The serial version identifier. */
	private static final long serialVersionUID = -8106007037531230402L;

	/**
	 * Construct a new {@link MethodDefinitionException}.
	 *
	 * @param code
	 *        An {@linkplain AvailErrorCode error code}.
	 */
	private MethodDefinitionException (final AvailErrorCode code)
	{
		super(code);
	}

	/**
	 * Answer a {@link MethodDefinitionException} that indicates the
	 * nonexistence of a {@linkplain MethodDescriptor method}.
	 *
	 * @return The requested exception.
	 */
	public static MethodDefinitionException noMethod ()
	{
		return new MethodDefinitionException(E_NO_METHOD);
	}

	/**
	 * Answer a {@link MethodDefinitionException} that indicates the
	 * nonexistence of a {@linkplain MethodDescriptor method}.
	 *
	 * @return The requested exception.
	 */
	public static A_Definition extractUniqueMethod (
		final A_Tuple methodDefinitions)
	throws MethodDefinitionException
	{
		switch (methodDefinitions.tupleSize())
		{
			case 0: throw new MethodDefinitionException(E_NO_METHOD_DEFINITION);
			case 1: return methodDefinitions.tupleAt(1);
			default: throw new MethodDefinitionException(
				E_AMBIGUOUS_METHOD_DEFINITION);
		}
	}
}
