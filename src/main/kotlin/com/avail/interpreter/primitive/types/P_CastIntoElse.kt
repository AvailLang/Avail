/*
 * P_CastIntoElse.kt
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
package com.avail.interpreter.primitive.types

import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.returnType
import com.avail.descriptor.types.A_Type.Companion.typeAtIndex
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.Primitive.Flag.Invokes
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.optimizer.L2Generator.Companion.edgeTo

/**
 * **Primitive:** If the second argument, a [function][A_Function], accepts the
 * first argument as its parameter, do the invocation. Otherwise invoke the
 * third argument, a zero-argument function.
 */
@Suppress("unused")
object P_CastIntoElse : Primitive(3, Invokes, CanInline, CannotFail)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val value = interpreter.argument(0)
		val castFunction = interpreter.argument(1)
		val elseFunction = interpreter.argument(2)

		interpreter.argsBuffer.clear()
		val expectedType =
			castFunction.code().functionType().argsTupleType.typeAtIndex(1)
		// "Jump" into the castFunction or elseFunction, to keep this frame from
		// showing up.
		interpreter.function = when {
			value.isInstanceOf(expectedType) -> {
				interpreter.argsBuffer.add(value)
				castFunction
			}
			else -> elseFunction
		}
		return Result.READY_TO_INVOKE
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
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
				ANY.o,
				functionType(
					tuple(
						bottom),
					TOP.o),
				functionType(
					emptyTuple,
					TOP.o)),
			TOP.o)

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		// Inline the invocation of this P_CastIntoElse primitive, such that it
		// does a type test for the type being cast to, then either invokes the
		// first block with the value being cast or the second block with no
		// arguments.
		val valueRead = arguments[0]
		val castFunctionRead = arguments[1]
		val elseFunctionRead = arguments[2]

		val castBlock =
			translator.generator.createBasicBlock("cast type matched")
		val elseBlock =
			translator.generator.createBasicBlock("cast type did not match")

		val typeTest = castFunctionRead.exactSoleArgumentType()
		if (typeTest !== null)
		{
			// By tracing where the castBlock came from, we were able to
			// determine the exact type to compare the value against.  This is
			// the usual case for casts, typically where the castBlock phrase is
			// simply a function closure.  First see if we can eliminate the
			// runtime test entirely.
			var bypassTesting = true
			val constant = valueRead.constantOrNull()
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
					passedTest -> translator.generateGeneralFunctionInvocation(
						castFunctionRead,
						listOf(valueRead),
						true,
						callSiteHelper)
					else -> translator.generateGeneralFunctionInvocation(
						elseFunctionRead, emptyList(), true, callSiteHelper)
				}
				return true
			}

			// We know the exact type to compare the value against, but we
			// couldn't statically eliminate the type test.  Emit a branch.
			translator.jumpIfKindOfConstant(
				valueRead, typeTest, castBlock, elseBlock)
		}
		else
		{
			// We don't statically know the type to compare the value against,
			// but we can get it at runtime by extracting the actual
			// castFunction's argument type.  Note that we can't phi-strengthen
			// the valueRead along the branches, since we don't statically know
			// the type that it was compared to.
			val parameterTypeRead =
				translator.generator.extractParameterTypeFromFunction(
					castFunctionRead, 1)
			translator.addInstruction(
				L2_JUMP_IF_KIND_OF_OBJECT,
				valueRead,
				parameterTypeRead,
				edgeTo(castBlock),
				edgeTo(elseBlock))
		}

		// We couldn't skip the runtime type check, which takes us to either
		// castBlock or elseBlock, after which we merge the control flow back.
		// Start by generating the invocation of castFunction.
		translator.generator.startBlock(castBlock)
		translator.generateGeneralFunctionInvocation(
			castFunctionRead, listOf(valueRead), true, callSiteHelper)

		// Now deal with invoking the elseBlock instead.
		translator.generator.startBlock(elseBlock)
		translator.generateGeneralFunctionInvocation(
			elseFunctionRead, emptyList(), true, callSiteHelper)

		return true
	}
}
