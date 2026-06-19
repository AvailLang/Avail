/*
 * P_CurrentFiber.kt
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

import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Continuation
import avail.descriptor.representation.A_Type
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive0

/**
 * **Primitive:** Answer the currently running [fiber][FiberDescriptor].
 *
 * It's marked with [HasSideEffect] because an [L2Chunk] that captures the
 * current fiber and stores it in a saved register in an [A_Continuation] *must
 * not* use that cached value in place of a subsequent call if the continuation
 * is restarted or resumed in a different fiber.
 */
@Suppress("unused")
object P_CurrentFiber : Primitive0(CanInline, CannotFail, HasSideEffect)
{
	override fun Interpreter.attempt0(): A_BasicObject?
	{
		return fiber().makeImmutable()
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(emptyTuple, mostGeneralFiberType())

	override val canDestroyArguments get() = false
}
