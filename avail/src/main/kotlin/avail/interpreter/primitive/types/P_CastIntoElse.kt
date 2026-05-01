/*
 * P_CastIntoElse.kt
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
package avail.interpreter.primitive.types

import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.Invokes
import avail.interpreter.primitive.Primitive3
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator.Companion.edgeTo

/**
 * **Primitive:** If the second argument, a [function][A_Function], accepts the
 * first argument as its parameter, do the invocation. Otherwise invoke the
 * third argument, a zero-argument function.
 */
@Suppress("unused")
object P_CastIntoElse : Primitive3(Invokes, CanInline, CannotFail)
{
	override fun attempt3(
		interpreter: Interpreter,
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		val value = arg1
		val castFunction = arg2
		val elseFunction = arg3

		interpreter.argsBuffer.clear()
		val expectedType =
			castFunction.code().functionType().argsTupleType.typeAtIndex(1)
		// "Jump" into the castFunction or elseFunction, to keep this frame from
		// showing up.
		return when
		{
			value.isInstanceOf(expectedType) ->
			{
				interpreter.argsBuffer.add(value)
				interpreter.invokeInPrimitive(castFunction)
			}
			else -> interpreter.invokeInPrimitive(elseFunction)
		}
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>): A_Type
	{
		// Keep it simple.
		val castFunctionType = argumentTypes[1]
		val elseFunctionType = argumentTypes[2]
		return castFunctionType.returnType.typeUnion(
			elseFunctionType.returnType)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ANY(),
				functionType(
					tuple(
						bottom),
					TOP()),
				functionType(
					emptyTuple,
					TOP())),
			TOP())

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		// Inline the invocation of this P_CastIntoElse primitive, such that it
		// does a type test for the type being cast to, then either invokes the
		// first block with the value being cast or the second block with no
		// arguments.
		val (valueRead, castFunctionRead, elseFunctionRead) = arguments
		val castBlock = createBasicBlock("cast type matched")
		val elseBlock = createBasicBlock("cast type did not match")

		val typeTest = castFunctionRead.exactSoleArgumentType(currentManifest)
		if (typeTest !== null)
		{
			// By tracing where the castBlock came from, we were able to
			// determine the exact type to compare the value against.  This is
			// the usual case for casts, typically where the castBlock phrase is
			// simply a function closure.  First see if we can eliminate the
			// runtime test entirely.
			var bypassTesting = true
			val constant = valueRead.constantOrNull
			val passedTest: Boolean = when {
				constant !== null -> constant.isInstanceOf(typeTest)
				valueRead.type().isSubtypeOf(typeTest) -> true
				valueRead.type().typeIntersection(typeTest).isBottom -> false
				else -> {
					bypassTesting = false
					false  // Keep compiler happy below.
				}
			}
			if (bypassTesting)
			{
				// Run the castBlock or elseBlock without having to do the
				// runtime type test (since we just did it).  Don't do a type
				// check on the result, because the client will deal with it.
				when {
					passedTest -> generateGeneralFunctionInvocation(
						castFunctionRead,
						true,
						callSiteHelper,
						listOf(valueRead))
					else -> generateGeneralFunctionInvocation(
						elseFunctionRead, true, callSiteHelper, emptyList())
				}
				return true
			}

			// We know the exact type to compare the value against, but we
			// couldn't statically eliminate the type test.  Emit a branch.
			jumpIfKindOfConstant(valueRead, typeTest, castBlock, elseBlock)
		}
		else
		{
			// We don't statically know the type to compare the value against,
			// but we can get it at runtime by extracting the actual
			// castFunction's argument type.  Note that we can't phi-strengthen
			// the valueRead along the branches, since we don't statically know
			// the type that it was compared to.
			val parameterTypeRead =
				extractParameterTypeFromFunction(castFunctionRead, 1)
			+L2_JUMP_IF_KIND_OF_OBJECT(
				valueRead,
				parameterTypeRead,
				edgeTo(castBlock),
				edgeTo(elseBlock))
		}

		// We couldn't skip the runtime type check, which takes us to either
		// castBlock or elseBlock, after which we merge the control flow back.
		// Start by generating the invocation of castFunction.
		startBlock(castBlock)
		generateGeneralFunctionInvocation(
			castFunctionRead, true, callSiteHelper, listOf(valueRead))

		// Now deal with invoking the elseBlock instead.
		startBlock(elseBlock)
		generateGeneralFunctionInvocation(
			elseFunctionRead, true, callSiteHelper, emptyList())

		return true
	}
}
