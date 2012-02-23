/**
 * VariableGetException.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;

/**
 * {@code VariableGetException} is thrown when {@link AvailObject#getValue}
 * fails for any reason.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class VariableGetException
extends AvailRuntimeException
{
	/** The serial version identifier. */
	private static final long serialVersionUID = 4122068935229408946L;

	/**
	 * Construct a new {@link VariableGetException}.
	 *
	 * @param errorCode An {@linkplain AvailErrorCode error code}.
	 */
	public VariableGetException (final @NotNull AvailErrorCode errorCode)
	{
		super(errorCode);
	}

	/**
	 * Construct a new {@link VariableGetException}.
	 *
	 * @param errorCode
	 *        An {@linkplain AvailErrorCode error code}.
	 * @param cause
	 *        The proximal {@linkplain Throwable cause} of the {@linkplain
	 *        VariableGetException exception}.
	 */
	public VariableGetException (
		final @NotNull AvailErrorCode errorCode,
		final @NotNull Throwable cause)
	{
		super(errorCode, cause);
	}
}
