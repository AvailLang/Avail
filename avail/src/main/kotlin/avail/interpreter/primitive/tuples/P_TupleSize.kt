/*
 * P_TupleSize.kt
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
package avail.interpreter.primitive.tuples

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i31
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operation.tuples.L2_TUPLE_SIZE
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive1
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import avail.optimizer.values.L2SemanticUnboxedInt.Companion.boxed
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation

/**
 * **Primitive:** Answer the size of the [tuple][TupleDescriptor].
 */
@Suppress("unused")
object P_TupleSize : Primitive1(CannotFail, CanFold, CanInline)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val tuple = arg1
		return fromInt(tuple.tupleSize)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(mostGeneralTupleType),
			wholeNumbers)

	override fun propagateManifestRestrictions(
		arguments: List<L2SemanticValue<BOXED_KIND>>,
		manifest: L2ValueManifest,
		restriction: TypeRestriction)
	{
		val sizeRange = restriction.type
		manifest.equivalentSemanticValue(arguments[0])?.let { tupleValue ->
			manifest.updateRestriction(tupleValue) {
				intersectionWithType(
					tupleTypeForSizesTypesDefaultType(
						sizeRange, emptyTuple, ANY()))
			}
		}
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type = argumentTypes[0].sizeRange.typeIntersection(i31)

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val tupleReg = arguments[0]

		val returnType = returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		val lower = returnType.lowerBound
		val upper = returnType.upperBound
		when
		{
			lower.equals(upper) ->
				// If the exact size of the tuple is known, then leverage that
				// information to produce a constant.
				callSiteHelper.useAnswer(boxedConstant(lower), false)
			else ->
			{
				// The exact size of the tuple isn't known, so generate code to
				// extract it into an int register, then move that to a boxed
				// register.  If the boxed form isn't needed, that instruction
				// will be eliminated later.
				val restriction = intRestrictionForType(returnType)
				val sizeBoxed = primitiveInvocation(
					P_TupleSize,
					listOf(tupleReg.semanticValue()))
				val sizeInt = sizeBoxed.unboxedInt
				val equivalent =
					currentManifest.equivalentSemanticValue(sizeInt)
				if (equivalent !== null)
				{
					// It already exists, so reuse it.
					if (equivalent != sizeInt)
					{
						moveIntRegister(equivalent, setOf(sizeInt))
					}
				}
				else
				{
					// It's not yet available, so compute it.
					val writer = intWrite(setOf(sizeInt), restriction)
					+L2_TUPLE_SIZE(tupleReg, writer)
				}
				callSiteHelper.useAnswer(
					readBoxed(sizeInt.boxed),
					false)
			}
		}
		return true
	}

	override val canDestroyArguments get() = false
}
