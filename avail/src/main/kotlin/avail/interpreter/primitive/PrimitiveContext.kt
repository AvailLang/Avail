/*
 * PrimitiveContext.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.interpreter.primitive

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.exceptions.AvailErrorCode
import avail.interpreter.execution.Interpreter
import avail.optimizer.ExecutableChunk

/**
 * All that an [Isolated] primitive may ask of whatever runs it: a way to fail.
 * An isolated primitive computes its result from its arguments alone, so it
 * can run where there is no [Interpreter], such as in a chunk's
 * [freeze][ExecutableChunk.freeze] method, where it only ever
 * runs at a call site that cannot fail.
 *
 * The [Interpreter] is the context of every primitive it runs, and
 * [FreezePrimitiveContext] is the context of the isolated primitives that a
 * freeze runs.
 */
interface PrimitiveContext
{
	/**
	 * Record the given value as the reason the running primitive failed, and
	 * answer `null`, which the primitive answers to say that it failed.
	 *
	 * @param failureValue
	 *   The value that describes the failure, usually an error code's
	 *   [numeric&#32;code][AvailErrorCode.numericCode].
	 * @return
	 *   `null`, typed as [AvailObject]`?` as a convenience.
	 */
	fun fail(failureValue: A_BasicObject?): AvailObject?

	/**
	 * Record the [numeric&#32;code][AvailErrorCode.numericCode] of the given
	 * [AvailErrorCode] as the reason the running primitive failed, and answer
	 * `null`, which the primitive answers to say that it failed.
	 *
	 * @param code
	 *   The [AvailErrorCode] that says why the primitive failed.
	 * @return
	 *   `null`, typed as [AvailObject]`?` as a convenience.
	 */
	fun fail(code: AvailErrorCode): AvailObject? = fail(code.numericCode())
}
