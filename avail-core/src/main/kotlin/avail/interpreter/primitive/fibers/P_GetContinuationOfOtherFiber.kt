/*
 * P_GetContinuationOfOtherFiber.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.fibers

import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.exceptions.AvailErrorCode.E_FIBER_IS_TERMINATED
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.Primitive.Flag.ReadsFromHiddenGlobalState
import avail.interpreter.Primitive.Flag.Unknown
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Ask another fiber what it's doing.  Fail if the fiber's
 * continuation chain is empty (i.e., it is terminated).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_GetContinuationOfOtherFiber : Primitive(
	1,
	CanSuspend,
	HasSideEffect,
	Unknown,
	// Both writes and reads global state, once to request the other fiber to
	// produce its continuation, and once to read it.
	WritesToHiddenGlobalState,
	ReadsFromHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val otherFiber = interpreter.argument(0)

		return interpreter.suspendThen {
			otherFiber.whenContinuationIsAvailableDo { theContinuation ->
				when {
					theContinuation.notNil -> succeed(theContinuation)
					else -> fail(E_FIBER_IS_TERMINATED)
				}
			}
		}
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_FIBER_IS_TERMINATED))

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(mostGeneralFiberType()), mostGeneralContinuationType())
}
