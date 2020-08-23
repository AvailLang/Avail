/*
 * P_TupleToSet.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.sets

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.one
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tuples.A_Tuple.Companion.asSet
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import com.avail.descriptor.types.A_Type.Companion.unionOfTypesAtThrough
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.A_Type.Companion.upperInclusive
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import com.avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import com.avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding
import com.avail.interpreter.levelTwo.operation.L2_CREATE_SET
import com.avail.optimizer.L1Translator

/**
 * **Primitive:** Convert a [tuple][TupleDescriptor] into a
 * [set][SetDescriptor].
 */
@Suppress("unused")
object P_TupleToSet : Primitive(1, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val tuple = interpreter.argument(0)
		return interpreter.primitiveSuccess(tuple.asSet())
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType()),
			mostGeneralSetType())

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val tupleType = argumentTypes[0]

		val unionType = tupleType.unionOfTypesAtThrough(1, Integer.MAX_VALUE)
		unionType.makeImmutable()
		val tupleSizes = tupleType.sizeRange()
		// Technically, if two tuple entries have disjoint types then the
		// minimum set size is two.  Generalizing this leads to computing the
		// Birkhoff chromatic polynomial of the graph whose vertices are the
		// tuple subscripts and which has edges when two tuple subscript types
		// are disjoint (their intersection is bottom).  This is the optimum
		// bound for the minimum size of the resulting set.  The maximum can be
		// improved by a not yet worked out pigeon hole principle when there are
		// element types with a small number of possible instances (e.g.,
		// enumerations) and those sets of instances overlap between many tuple
		// elements.  We do neither optimization here, but we do note that only
		// the empty tuple can produce the empty set, and the set size is never
		// greater than the tuple size.
		val minSize =
			if (tupleSizes.lowerBound().equalsInt(0)) zero()
			else one()
		val setSizes = integerRangeType(
			minSize,
			true,
			tupleSizes.upperBound(),
			tupleSizes.upperInclusive())
		return setTypeForSizesContentType(setSizes, unionType)
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: L1Translator.CallSiteHelper): Boolean
	{
		val tupleReg = arguments[0]

		val generator = translator.generator
		if (!generator.currentlyReachable())
		{
			// Generator is not at a live position, so pretend we generated the
			// code for this primitive invocation.
			return true
		}

		val sizeRange = tupleReg.type().sizeRange()
		val size = sizeRange.lowerBound()
		if (!size.isInt || !sizeRange.upperBound().equals(size))
			return false
		val sizeInt = size.extractInt()
		val elementRegs = generator.explodeTupleIfPossible(
			tupleReg,
			tupleReg.type().tupleOfTypesFromTo(1, sizeInt).toList())
		elementRegs ?: return false

		// Create the set directly from the values.  This may turn the tuple
		// creation instruction into dead code.
		val restriction = returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		val semanticResult = generator.primitiveInvocation(this, arguments)
		val write = generator.boxedWrite(
			semanticResult,
			restrictionForType(restriction, RestrictionFlagEncoding.BOXED))
		generator.addInstruction(
			L2_CREATE_SET,
			L2ReadBoxedVectorOperand(elementRegs),
			write)
		callSiteHelper.useAnswer(translator.readBoxed(write))
		return true
	}
}
