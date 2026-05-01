/*
 * P_CatchException.kt
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
package avail.interpreter.primitive.controlflow

import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.two
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Exceptions.exceptionType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import avail.exceptions.AvailErrorCode.E_REQUIRED_FAILURE
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CatchException
import avail.interpreter.primitive.Primitive.Flag.PreserveArguments
import avail.interpreter.primitive.Primitive.Flag.PreserveGuardVariable
import avail.interpreter.primitive.Primitive3

/**
 * **Primitive:** Always fail. The Avail failure code invokes the bodyBlock, and
 * then, if an unwind has not happened, it marks the first local with the
 * handler sentinel, invokes the unwind function, then marks the local with the
 * unwind sentinel.
 *
 * The handlerBlocks are only examined by [P_RaiseException] when it's searching
 * for the topmost frame that has not yet unwound.
 *
 * If the handlerBlocks and ensureBlock are sufficient to guarantee it will
 * catch all exceptions for at least the unwind, fail with [E_REQUIRED_FAILURE],
 * otherwise fail with [E_INCORRECT_ARGUMENT_TYPE].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_CatchException : Primitive3(
	CatchException, PreserveGuardVariable, PreserveArguments, CanInline)
{
	override fun attempt3(
		interpreter: Interpreter,
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		//val bodyBlock: A_Function = arg1
		val handlerBlocks: A_Tuple = arg2
		//val ensureBlock: A_Function = arg3

		for (block in handlerBlocks)
		{
			if (!block.kind().argsTupleType.typeAtIndex(1).isSubtypeOf(
					exceptionType))
			{
				return interpreter.fail(E_INCORRECT_ARGUMENT_TYPE)
			}
		}
		return interpreter.fail(E_REQUIRED_FAILURE)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				functionType(emptyTuple, TOP()),
				zeroOrMoreOf(functionType(tuple(bottom), TOP())),
				functionType(emptyTuple, TOP())),
			bottom)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(
				E_REQUIRED_FAILURE,
				E_INCORRECT_ARGUMENT_TYPE))

	/**
	 * The slot in which the guard variable is placed.  The frame layout is:
	 *
	 * ```
	 *   1. arg: body
	 *   2. arg: handlers
	 *   3. arg: unwind
	 *   4. first local variable: guardVariable
	 *   [...potentially other variables...]
	 *   ≥5. first local slot: primitive failure slot
	 * ```
	 */
	const val slotIndexOfHandlersTuple = 2

	/**
	 * The slot in which the guard variable is placed.  The frame layout is:
	 *
	 * ```
	 *   1. arg: body
	 *   2. arg: handlers
	 *   3. arg: unwind
	 *   4. first local variable: guardVariable
	 *   [...potentially other variables...]
	 *   ≥5. first local slot: primitive failure slot
	 * ```
	 */
	const val slotIndexOfGuardVariable = 4

	/**
	 * The value which, when it occurs in a frame for this primitive, indicates
	 * a handler has begun execution of a handler.
	 */
	val handlerSentinel = one

	/**
	 * The value which, when it occurs in a frame for this primitive, indicates
	 * a handler has begun execution of the unwind.
	 */
	val unwindSentinel = two

	/**
	 * The content type for the state variable that must be declared immediately
	 * after the arguments and primitive failure variable.
	 */
	val guardVariableContentType =
		enumerationWith(
			set(
				zero,
				handlerSentinel,
				unwindSentinel))
}
