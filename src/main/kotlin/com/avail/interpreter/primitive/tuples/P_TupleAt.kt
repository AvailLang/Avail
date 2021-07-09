/*
 * P_TupleAt.kt
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
package com.avail.interpreter.primitive.tuples

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.A_Number.Companion.greaterOrEqual
import com.avail.descriptor.numbers.A_Number.Companion.greaterThan
import com.avail.descriptor.numbers.A_Number.Companion.lessOrEqual
import com.avail.descriptor.numbers.A_Number.Companion.lessThan
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.one
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.unionOfTypesAtThrough
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.nonnegativeInt32
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_COMPARE_INT
import com.avail.interpreter.levelTwo.operation.L2_MOVE
import com.avail.interpreter.levelTwo.operation.L2_TUPLE_AT_CONSTANT
import com.avail.interpreter.levelTwo.operation.L2_TUPLE_AT_NO_FAIL
import com.avail.interpreter.levelTwo.operation.L2_TUPLE_SIZE
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.optimizer.L2Generator.Companion.edgeTo
import com.avail.optimizer.values.L2SemanticUnboxedInt
import com.avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation
import java.lang.Integer.MAX_VALUE

/**
 * **Primitive:** Look up an element in the [tuple][TupleDescriptor].
 */
@Suppress("unused")
object P_TupleAt : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val tuple = interpreter.argument(0)
		val indexObject = interpreter.argument(1)
		if (!indexObject.isInt)
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		val index = indexObject.extractInt
		return if (index > tuple.tupleSize)
		{
			interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		else interpreter.primitiveSuccess(tuple.tupleAt(index))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType,
				naturalNumbers),
			ANY.o)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val tupleType = argumentTypes[0]
		val subscripts = argumentTypes[1]

		val lower = subscripts.lowerBound
		val upper = subscripts.upperBound
		val lowerInt = if (lower.isInt) lower.extractInt else 1
		val upperInt = if (upper.isInt) upper.extractInt else MAX_VALUE
		val unionType = tupleType.unionOfTypesAtThrough(lowerInt, upperInt)
		unionType.makeImmutable()
		return unionType
	}

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_SUBSCRIPT_OUT_OF_BOUNDS))

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>): Fallibility
	{
		val tupleType = argumentTypes[0]
		val subscripts = argumentTypes[1]

		val tupleTypeSizes = tupleType.sizeRange
		val minTupleSize = tupleTypeSizes.lowerBound
		return if (subscripts.lowerBound.greaterOrEqual(one)
			&& subscripts.upperBound.lessOrEqual(minTupleSize))
		{
			CallSiteCannotFail
		}
		else super.fallibilityForArgumentTypes(argumentTypes)
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val tupleReg = arguments[0]
		val subscriptReg = arguments[1]
		val generator = translator.generator
		if (fallibilityForArgumentTypes(argumentTypes) != CallSiteCannotFail)
		{
			// We can't guarantee success, so do a dynamic bounds check.
			val failed = generator.createBasicBlock("failed bounds check")
			val unboxedSemanticSize = L2SemanticUnboxedInt(
				primitiveInvocation(
					P_TupleSize, listOf(tupleReg.semanticValue())))
			val intSizeRestriction = restrictionForType(
				tupleReg.type().sizeRange.typeIntersection(nonnegativeInt32),
				UNBOXED_INT_FLAG)
			val intSizeType = intSizeRestriction.type
			if (intSizeType.lowerBound.equals(intSizeType.upperBound))
			{
				val sizeRead = generator.unboxedIntConstant(
					intSizeType.lowerBound.extractInt)
				generator.moveRegister(
					L2_MOVE.unboxedInt,
					sizeRead.semanticValue(),
					unboxedSemanticSize)
			}
			else
			{
				val sizeWriter = generator.intWrite(
					setOf(unboxedSemanticSize), intSizeRestriction)
				translator.addInstruction(
					L2_TUPLE_SIZE,
					tupleReg,
					sizeWriter)
			}
			val readSubscript = generator.readInt(
				L2SemanticUnboxedInt(subscriptReg.semanticValue()),
				failed)
			// At this position, we have the tuple size and subscript in int
			// registers. Check the lower bound, if necessary.
			if (generator.currentlyReachable()
				&& readSubscript.restriction().type.lowerBound.lessThan(one))
			{
				val success1 = generator.createBasicBlock(
					"passed lower bound check")
				generator.addInstruction(
					L2_JUMP_IF_COMPARE_INT.greaterOrEqual,
					readSubscript,
					generator.unboxedIntConstant(1),
					edgeTo(success1),
					edgeTo(failed))
				generator.startBlock(success1)
			}
			// Check the upper bound, if necessary.
			if (generator.currentlyReachable()
				&& subscriptReg.type().upperBound.greaterThan(
					intSizeRestriction.type.lowerBound))
			{
				val success2 = generator.createBasicBlock(
					"passed upper bound check")
				generator.addInstruction(
					L2_JUMP_IF_COMPARE_INT.lessOrEqual,
					readSubscript,
					generator.currentManifest().readInt(unboxedSemanticSize),
					edgeTo(success2),
					edgeTo(failed))
				generator.startBlock(success2)
			}
			if (generator.currentlyReachable())
			{
				val resultRestriction = restrictionForType(
					returnTypeGuaranteedByVM(
						rawFunction,
						listOf(argumentTypes[0], intSizeRestriction.type)),
					BOXED_FLAG)
				val semanticResult = primitiveInvocation(
					this, arguments.map { it.semanticValue() })
				val writeResult =
					generator.boxedWrite(semanticResult, resultRestriction)
				generator.addInstruction(
					L2_TUPLE_AT_NO_FAIL,
					tupleReg,
					readSubscript,
					writeResult)
				callSiteHelper.useAnswer(translator.readBoxed(writeResult))
			}
			generator.startBlock(failed)
			return if (!generator.currentlyReachable())
			{
				// The failure path can't be reached.
				true
			}
			else super.tryToGenerateSpecialPrimitiveInvocation(
				functionToCallReg,
				rawFunction,
				arguments,
				argumentTypes,
				translator,
				callSiteHelper)
			// We failed the dynamic range check, so fall back to a regular call
			// site.
		}
		// The primitive cannot fail at this site.
		val subscriptType = subscriptReg.type()
		val lower = subscriptType.lowerBound
		val upper = subscriptType.upperBound
		val writer = generator.boxedWriteTemp(
			restrictionForType(
				returnTypeGuaranteedByVM(rawFunction, argumentTypes),
				BOXED_FLAG))
		if (lower.equals(upper))
		{
			// The subscript is a constant (and it's within range).
			val subscriptInt = lower.extractInt
			translator.addInstruction(
				L2_TUPLE_AT_CONSTANT,
				tupleReg,
				L2IntImmediateOperand(subscriptInt),
				writer)
			callSiteHelper.useAnswer(translator.readBoxed(writer))
			return true
		}
		// The subscript isn't a constant, but it's known to be in range.
		val subscriptConversionFailure =
			generator.createBasicBlock("Should be unreachable")
		val subscriptIntReg = generator.readInt(
			L2SemanticUnboxedInt(subscriptReg.semanticValue()),
			subscriptConversionFailure)
		assert(subscriptConversionFailure.predecessorEdges().isEmpty())
		translator.addInstruction(
			L2_TUPLE_AT_NO_FAIL,
			tupleReg,
			subscriptIntReg,
			writer)
		callSiteHelper.useAnswer(translator.readBoxed(writer))
		return true
	}
}
