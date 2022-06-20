/*
 * ValidationException.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.tools.options

/**
 * Exception thrown by the [factory][OptionProcessorFactory]'s validation
 * process in the event that the client-specified
 * [option&#32;processor][OptionProcessor] fails validation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
class ValidationException : RuntimeException
{
	/**
	 * Construct a new [ValidationException].
	 */
	internal constructor()

	/**
	 * Construct a new [ValidationException].
	 *
	 * @param message
	 *   A (hopefully) informative message explaining why the
	 *   [factory][OptionProcessorFactory] could not validate the specified
	 *   [option&#32;processor][OptionProcessor].
	 */
	internal constructor(message: String) : super(message)

	/**
	 * Construct a new [ValidationException].
	 *
	 * @param cause
	 *   The original [exception][Throwable] which caused the new instance to be
	 *   raised.
	 */
	internal constructor(cause: Throwable) : super(cause)

	/**
	 * Construct a new [ValidationException].
	 *
	 * @param message
	 *   A (hopefully) informative message explaining why the
	 *   [factory][OptionProcessorFactory] could not validate the specified
	 *   [option&#32;processor][OptionProcessor].
	 * @param cause
	 *   The original [exception][Throwable] which caused the new instance to be
	 *   raised.
	 */
	internal constructor(message: String, cause: Throwable)
		: super(message, cause)
}
