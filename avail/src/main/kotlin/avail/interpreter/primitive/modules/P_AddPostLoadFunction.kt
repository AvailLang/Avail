/*
 * P_AddPostLoadFunction.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.interpreter.primitive.modules

import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Fiber.Companion.availLoader
import avail.descriptor.representation.A_Function
import avail.descriptor.representation.A_Module.Companion.addPostLoadFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import avail.interpreter.effects.LoadingEffectToRunPrimitive
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.primitive.Primitive1

/**
* **Primitive:** Add the specified [post-load&#32;function][A_Function] to the
 * [current][Interpreter.module] module.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_AddPostLoadFunction : Primitive1(
	CanInline, HasSideEffect, WritesToHiddenGlobalState)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val postLoadFunction = arg1
		val loader = fiber().availLoader ?: return fail(E_LOADING_IS_OVER)
		val module = loader.module
		module.addPostLoadFunction(postLoadFunction)
		loader.recordEffect(
			LoadingEffectToRunPrimitive(
				SpecialMethodAtom.ADD_POSTLOAD_FUNCTION, postLoadFunction)
		)
		// No need to record a loader effect here.  The eventual execution of
		// the function will be sufficient to capture side effects for the fast
		// loader to replay.
		return nil
	}

	/**
	 * The function might contain outers that are escaped variables, but they're
	 * made shared here.
	 */
	override fun mightMakeEscapedVariableShared(
		argumentTypes: List<A_Type>
	): Boolean = true

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(functionTypeReturning(TOP())), TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_LOADING_IS_OVER))
}
