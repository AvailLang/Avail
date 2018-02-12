/*
 * ValidationException.java
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

package com.avail.utility.fsm;

/**
 * Exception thrown by the {@linkplain StateMachineFactory factory}'s
 * validation process in the event that the client-specified
 * {@linkplain StateMachine state machine} fails validation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class ValidationException
extends RuntimeException
{
	/** The serial version identifier. */
	private static final long serialVersionUID = -7627928027143297507L;

	/**
	 * Construct a new <code>{@link ValidationException}</code>.
	 */
	ValidationException ()
	{
		// No implementation required.
	}

	/**
	 * Construct a new {@link ValidationException}>.
	 *
	 * @param message
	 *        A (hopefully) informative message explaining why the {@linkplain
	 *        StateMachineFactory factory} could not validate the specified
	 *        {@linkplain StateMachine state machine}.
	 */
	ValidationException (final String message)
	{
		super(message);
	}

	/**
	 * Construct a new {@link ValidationException}.
	 *
	 * @param cause
	 *        The original {@linkplain Throwable exception} which caused the new
	 *        instance to be raised.
	 */
	ValidationException (final Throwable cause)
	{
		super(cause);
	}

	/**
	 * Construct a new <code>{@link ValidationException}</code>.
	 *
	 * @param message
	 *        A (hopefully) informative message explaining why the {@linkplain
	 *        StateMachineFactory factory} could not validate the specified
	 *        {@linkplain StateMachine state machine}.
	 * @param cause
	 *        The original {@linkplain Throwable exception} which caused the new
	 *        instance to be raised.
	 */
	ValidationException (final String message, final Throwable cause)
	{
		super(message, cause);
	}
}
