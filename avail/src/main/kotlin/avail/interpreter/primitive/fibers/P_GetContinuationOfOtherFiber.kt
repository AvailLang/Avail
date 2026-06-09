/*
 * P_GetContinuationOfOtherFiber.kt
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

package avail.interpreter.primitive.fibers

import avail.descriptor.fiber.A_Fiber.Companion.whenContinuationIsAvailableDo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.exceptions.AvailErrorCode.E_FIBER_IS_TERMINATED
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanSuspend
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive.Flag.ReadsFromHiddenGlobalState
import avail.interpreter.primitive.Primitive.Flag.Unknown
import avail.interpreter.primitive.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.primitive.Primitive1
import avail.optimizer.StackReifier
import avail.optimizer.StackReifier.AfterReification.SWITCH_FROM_FIBER

/**
 * **Primitive:** Ask another fiber what it's doing.  Fail if the fiber's
 * continuation chain is empty (i.e., it is terminated).
 *
 * Note that we don't override [mightMakeEscapedVariableShared], since only
 * something in *the other* fiber can become shared.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_GetContinuationOfOtherFiber : Primitive1(
	CanSuspend,
	HasSideEffect,
	Unknown,
	// Both writes and reads global state, once to request the other fiber to
	// produce its continuation, and once to read it.
	WritesToHiddenGlobalState,
	ReadsFromHiddenGlobalState)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val otherFiber = arg1

		currentReifier = StackReifier(true, reificationForNoninlineStat) {
			suspendThen {
				otherFiber.whenContinuationIsAvailableDo { otherContinuation ->
					when {
						otherContinuation.notNil -> succeed(otherContinuation)
						else -> fail(E_FIBER_IS_TERMINATED)
					}
				}
			}
			SWITCH_FROM_FIBER
		}
		return null
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_FIBER_IS_TERMINATED))

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralFiberType()),
			mostGeneralContinuationType)
}
