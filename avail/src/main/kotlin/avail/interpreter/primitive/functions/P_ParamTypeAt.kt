/*
 * P_ParamTypeAt.kt
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
package avail.interpreter.primitive.functions

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.A_Number.Companion.lessOrEqual
import avail.descriptor.numbers.A_Number.Companion.lessThan
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.defaultType
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionMeta
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.Primitive.Fallibility.CallSiteMustFail
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2_GET_TYPE
import avail.optimizer.L1Translator

/**
 * **Primitive:** Answer the type of the parameter at the given index within the
 * given functionType.
 */
@Suppress("unused")
object P_ParamTypeAt : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val functionType = interpreter.argument(0)
		val indexObject = interpreter.argument(1)

		val parametersType = functionType.argsTupleType
		val sizeRange = parametersType.sizeRange
		if (sizeRange.upperBound.lessThan(indexObject)) {
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		if (!indexObject.isInt) {
			// The function type must accept a very large number of arguments.
			return interpreter.primitiveSuccess(parametersType.defaultType)
		}
		val index = indexObject.extractInt
		return interpreter.primitiveSuccess(
			functionType.argsTupleType.typeAtIndex(index))
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS))

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				functionMeta(),
				naturalNumbers),
			anyMeta())

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>): Fallibility
	{
		val (functionMeta: A_Type, indexType) = argumentTypes

		val functionType = functionMeta.instance
		val sizeRange = functionType.argsTupleType.sizeRange
		if (sizeRange.isBottom)
		{
			// We know nothing about the actual function's parameter count.
			return CallSiteCanFail
		}
		if (indexType.upperBound.lessOrEqual(sizeRange.lowerBound))
		{
			return CallSiteCannotFail
		}
		if (indexType.lowerBound.greaterThan(sizeRange.upperBound))
		{
			return CallSiteMustFail
		}
		return CallSiteCannotFail
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: L1Translator.CallSiteHelper
	): Boolean
	{
		val (functionTypeRead, _) = arguments
		val (functionMeta, indexType) = argumentTypes

		val functionTypeDefinition = functionTypeRead.definition().instruction
		val exactIndex = indexType.lowerBound
		if (!indexType.upperBound.equals(exactIndex)) return false
		// The exact index is known.
		val argsRange = functionMeta.instance.argsTupleType.sizeRange
		val minArgs = argsRange.lowerBound
		if (!minArgs.isInt || exactIndex.greaterThan(minArgs)) return false
		// The exact index will always be in range.
		if (functionTypeDefinition.operation !is L2_GET_TYPE) return false
		// This is the pattern "x's type [y]".  Since x is some actual
		// function, the resulting argument type can't be top or bottom.
		val function = L2_GET_TYPE.sourceValueOf(functionTypeDefinition)
		val generator = translator.generator
		val read = generator.extractParameterTypeFromFunction(
			function, exactIndex.extractInt)
		callSiteHelper.useAnswer(read)
		return true
	}
}
