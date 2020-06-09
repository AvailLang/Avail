/*
 * P_CastInto.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteMustFail
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.Invokes
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED
import com.avail.interpreter.levelTwo.operation.L2_FUNCTION_PARAMETER_TYPE
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.optimizer.L2Generator.Companion.edgeTo

/**
 * **Primitive:** If the second argument, a [function][A_Function], accepts the
 * first argument as its parameter, do the invocation. Otherwise fail the
 * primitive.
 */
@Suppress("unused")
object P_CastInto : Primitive(2, Invokes, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val value = interpreter.argument(0)
		val castFunction = interpreter.argument(1)

		val expectedType =
			castFunction.code().functionType().argsTupleType().typeAtIndex(1)
		if (value.isInstanceOf(expectedType))
		{
			// "Jump" into the castFunction, to keep this frame from showing up.
			interpreter.argsBuffer.clear()
			interpreter.argsBuffer.add(value)
			interpreter.function = castFunction
			return Result.READY_TO_INVOKE
		}
		// Fail the primitive.
		return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			ObjectTupleDescriptor.tuple(
				ANY.o(),
				functionType(
					tuple(
						bottom()),
					TOP.o())),
			TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_INCORRECT_ARGUMENT_TYPE))

	// Keep it simple.  In theory, if we could show that the cast would not
	// fail, and that the function was a primitive, we could ask the
	// primitive what it would produce.
	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>
	): A_Type = argumentTypes[1].returnType()

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>): Fallibility
	{
		val valueType = argumentTypes[0]
		val castFunctionType = argumentTypes[1]

		// Only deal with a constant castFunction for now, otherwise assume it
		// could either succeed or fail.
		if (castFunctionType.instanceCount().equalsInt(1))
		{
			val function = castFunctionType.instance()
			val code = function.code()
			val argType = code.functionType().argsTupleType().typeAtIndex(1)
			if (valueType.isSubtypeOf(argType))
			{
				return CallSiteCannotFail
			}
			if (valueType.typeIntersection(argType).isBottom)
			{
				return CallSiteMustFail
			}
		}
		return CallSiteCanFail
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		// Inline the invocation of this P_CastInto primitive, such that it
		// does a type test for the type being cast to, then either invokes the
		// first block with the value being cast or the second block with no
		// arguments.
		val (valueRead, castFunctionRead) = arguments

		val generator = translator.generator
		val castBlock = generator.createBasicBlock("cast type matched")
		val elseBlock = generator.createBasicBlock("cast type did not match")

		val constantValue = valueRead.constantOrNull()
		val typeTest = castFunctionRead.exactSoleArgumentType()
		val passedTest: Boolean? = typeTest?.run{
			when {
				constantValue !== null -> constantValue.isInstanceOf(typeTest)
				valueRead.type().isSubtypeOf(typeTest) -> true
				valueRead.type().typeIntersection(typeTest).isBottom -> false
				else -> null
			}
		}
		when {
			typeTest === null -> {
				// We don't statically know the type to compare the value
				// against, but we can get it at runtime by extracting the
				// actual castFunction's argument type.  Note that we can't
				// phi-strengthen the valueRead along the branches, since we
				// don't statically know the type that it was compared to.
				val parameterTypeWrite = generator.boxedWriteTemp(
					restrictionForType(anyMeta(), BOXED))
				translator.addInstruction(
					L2_FUNCTION_PARAMETER_TYPE,
					castFunctionRead,
					L2IntImmediateOperand(1),
					parameterTypeWrite)
				translator.addInstruction(
					L2_JUMP_IF_KIND_OF_OBJECT,
					valueRead,
					translator.readBoxed(parameterTypeWrite),
					edgeTo(castBlock),
					edgeTo(elseBlock))
			}
			passedTest === null ->
				// Couldn't prove or disprove type test, but we know statically
				// the cast block's exact argument type.
				translator.jumpIfKindOfConstant(
					valueRead, typeTest, castBlock, elseBlock)
			else ->
				// We proved the test always passes or always fails.
				translator.generator.jumpTo(
					if (passedTest) castBlock else elseBlock)
		}

		// In castBlock, generate the invocation of castFunction.
		generator.startBlock(castBlock)
		if (generator.currentlyReachable()) {
			translator.generateGeneralFunctionInvocation(
				castFunctionRead, listOf(valueRead), true, callSiteHelper)
		}

		// In elseBlock, generate the invocation of the actual block implemented
		// with this primitive, which should always run the failure code.
		generator.startBlock(elseBlock)
		if (generator.currentlyReachable()) {
			translator.generateGeneralFunctionInvocation(
				functionToCallReg, arguments, false, callSiteHelper)
		}
		return true
	}
}
