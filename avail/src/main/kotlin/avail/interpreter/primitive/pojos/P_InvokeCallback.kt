/*
 * P_InvokeCallback.kt
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
package avail.interpreter.primitive.pojos

import avail.CallbackSystem
import avail.CallbackSystem.Callback
import avail.descriptor.pojos.PojoDescriptor
import avail.descriptor.pojos.PojoDescriptor.Companion.newPojo
import avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.PojoTypeDescriptor.Companion.pojoTypeForClass
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelOne.L1InstructionWriter
import avail.interpreter.primitive.Primitive.Flag.CanSuspend
import avail.interpreter.primitive.Primitive.Flag.HasSideEffect
import avail.interpreter.primitive.Primitive.Flag.Private
import avail.interpreter.primitive.PrimitiveN

/**
 * **Primitive:** Given zero or more arguments, invoke the [Callback] that's in
 * a [pojo][PojoDescriptor] stored in the sole outer variable.
 *
 * If a Java [Throwable] is thrown while executing the [Callback], or if the
 * specific callback indicates failure of some other form, invoke the handler
 * for Java exceptions in callbacks.  Otherwise, answer the result of
 * successfully executing the callback.  The callback body runs in the
 * [CallbackSystem]'s thread pool.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_InvokeCallback : PrimitiveN(-1, Private, CanSuspend, HasSideEffect)
{
	override fun Interpreter.attemptN(
		args: Array<AvailObject>
	): A_BasicObject?
	{
		availLoaderOrNull()?.statementCanBeSummarized(false)
		val primitiveFunction = function!!
		assert(primitiveFunction.code().codePrimitive() === P_InvokeCallback)
		val callbackPojo = primitiveFunction.outerVarAt(1)
		val argumentsTuple = tupleFromArray(*args)
		return suspendThen {
			runtime.callbackSystem().executeCallbackTask(
				callbackPojo.javaObjectNotNull(),
				argumentsTuple,
				completion = ::succeed,
				failure = { throwable: Throwable ->
					fail(
						newPojo(
							identityPojo(throwable),
							pojoTypeForClass(throwable.javaClass)
						).makeShared())
				})
		}
	}

	/**
	 * An argument might capture an escaped variable and make it shared here.
	 */
	override fun mightMakeEscapedVariableShared(
		argumentTypes: List<A_Type>
	): Boolean = true

	/** This primitive is suitable for any block signature. */
	override fun privateBlockTypeRestriction(): A_Type = bottom

	override fun privateFailureVariableType(): A_Type =
		pojoTypeForClass(Throwable::class.java)

	override fun writeDefaultFailureCode(
		lineNumber: Int,
		writer: L1InstructionWriter,
		numArgs: Int)
	{
		// Raw functions using this primitive should not be constructed through
		// this default mechanism.  See CallbackSystem for details.
		throw UnsupportedOperationException(
			"${javaClass.simpleName} must not create a function through the " +
				"bootstrap mechanism"
		)
	}
}
