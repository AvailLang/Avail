/*
 * P_ElementInSet.kt
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
package avail.interpreter.primitive.sets

import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.A_Set.Companion.hasElement
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2_CREATE_SET
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT_BOXED
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive2
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator

/**
 * **Primitive:** Check if the [object][AvailObject] is an element of the
 * [set][SetDescriptor].
 */
@Suppress("unused")
object P_ElementInSet : Primitive2(CannotFail, CanFold, CanInline)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val element = arg1
		val set = arg2

		return objectFromBoolean(set.hasElement(element))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ANY(),
				mostGeneralSetType()),
			booleanType)

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val (value, set) = arguments
		val setSource = set.definitionSkippingMoves(currentManifest)
		val allSources = when
		{
			setSource is L2_CREATE_SET -> setSource.values.elements
			(setSource is L2_MOVE_CONSTANT_BOXED
				&& setSource.constant().constant.setSize <=
					largestChainedTest * 3
			) -> setSource.constant().constant.map(::boxedConstant)
			else -> return false
		}
		// We can see the instruction that created the set, so we can just
		// check if any of the elements equals the value being tested.  We
		// can even eliminate the tests for values in the set whose type is
		// disjoint from the test value's type.
		if (allSources.size > largestChainedTest * 3)
		{
			// Don't even bother scanning for vacuous type intersections.
			return false
		}
		val possibleElements = allSources.filterNot { element ->
			element.restriction().intersection(value.restriction())
				.isImpossible
		}
		if (possibleElements.size > largestChainedTest)
		{
			return false
		}
		// There are few enough elements that we can just test them.  This
		// has a potential advantage of not constructing the set, but also
		// perhaps exposing splittable paths where a particular semantic
		// value of the set is known to match or not match the test value at
		// the test site.
		val anyMatched = createBasicBlock("matched element")
		possibleElements.forEachIndexed { i, possible ->
			val notMatched = createBasicBlock("didn't match #${i+1}")
			jumpIfEqualsObjects(value, possible, anyMatched, notMatched)
			startBlock(notMatched)
		}
		// Code generation is at the point where nothing matched.
		callSiteHelper.useConstantAnswer(falseObject)
		startBlock(anyMatched)
		if (currentlyReachable())
		{
			callSiteHelper.useConstantAnswer(trueObject)
		}
		return true
	}

	override val canDestroyArguments get() = false

	/**
	 * If we know the set creation instruction that created the set under test,
	 * we can replace it with a chain of tests, but only if the chain would be
	 * no longer than this.
 	 */
	const val largestChainedTest = 5
}
