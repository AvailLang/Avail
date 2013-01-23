/**
 * SignatureException.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import com.avail.descriptor.*;

/**
 * An {@code SignatureException} is thrown when a {@linkplain
 * DefinitionDescriptor signature declaration} is invalid.  This might indicate
 * a problem with the signature itself, or perhaps an inconsistency between it
 * and other signatures already installed in the system.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @see DefinitionDescriptor
 */
public final class SignatureException
extends AvailException
{
	/** The serial version identifier. */
	private static final long serialVersionUID = -1062035730137304902L;

	/**
	 * Construct a new {@link SignatureException} with the specified {@linkplain
	 * AvailErrorCode error code}.
	 *
	 * @param errorCode
	 *        The {@linkplain AvailErrorCode error code}.
	 */
	public SignatureException (final AvailErrorCode errorCode)
	{
		super(errorCode);
	}

	/**
	 * Construct a new {@link SignatureException} with the specified {@linkplain
	 * Throwable cause}.
	 *
	 * @param errorCode
	 *        The {@linkplain AvailErrorCode error code}.
	 * @param cause
	 *        The proximal {@linkplain Throwable cause} of the {@linkplain
	 *        SignatureException exception}.
	 */
	public SignatureException (
		final AvailErrorCode errorCode,
		final Throwable cause)
	{
		super(errorCode, cause);
	}
}
