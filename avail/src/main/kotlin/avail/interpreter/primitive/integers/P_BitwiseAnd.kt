/*
 * P_BitwiseAnd.kt
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

package avail.interpreter.primitive.integers

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.bitwiseAnd
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.equalsLong
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.greaterOrEqual
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.A_Number.Companion.isLong
import avail.descriptor.numbers.A_Number.Companion.plusCanDestroy
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.dispatch.TestForConstantsDecisionStep
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.dispatch.L2_MULTIWAY_JUMP
import avail.interpreter.levelTwo.operation.dispatch.ShiftedHashSplitter
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.And
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive2
import avail.interpreter.primitive.general.P_Hash
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation
import avail.optimizer.values.PatternBuilder.Companion.pattern
import kotlin.math.min

/**
 * **Primitive:** Compute the bitwise AND of the [arguments][IntegerDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_BitwiseAnd : Primitive2(CannotFail, CanFold, CanInline)
{
	override fun attempt2(
		interpreter: Interpreter,
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val a = arg1
		val b = arg2
		return a.bitwiseAnd(b, true)
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type
	{
		assert(argumentTypes.size == 2)
		val (aRange, bRange) = argumentTypes

		// If either value is constrained to a positive range, then at least
		// guarantee the bit-wise and can't be greater than or equal to the next
		// higher power of two of that range's upper bound.
		val upper: Long =
			if (aRange.lowerBound.greaterOrEqual(zero)
				&& aRange.upperBound.isLong)
			{
				if (bRange.lowerBound.greaterOrEqual(zero)
					&& bRange.upperBound.isLong)
				{
					min(
						aRange.upperBound.extractLong,
						bRange.upperBound.extractLong)
				}
				else
				{
					aRange.upperBound.extractLong
				}
			}
			else if (bRange.lowerBound.greaterOrEqual(zero)
				&& bRange.upperBound.isLong)
			{
				bRange.upperBound.extractLong
			}
			else
			{
				// Give up, as the result may be negative or exceed a long.
				return super.returnTypeGuaranteedByVM(
					rawFunction, argumentTypes)
			}
		// At least one value is positive, so the result is positive.
		// At least one is a long, so the result must be a long.
		val highOneBit = upper.takeHighestOneBit()
		if (highOneBit == 0L)
		{
			// One of the ranges is constrained to be exactly zero.
			return singleInt(0)
		}
		val maxValue = highOneBit - 1 or highOneBit
		return integerRangeType(zero, true, fromLong(maxValue), true)
	}

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val bound = returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		if (bound.lowerBound.equals(bound.upperBound))
		{
			// Constant result.
			callSiteHelper.useAnswer(boxedConstant(bound.lowerBound), false)
			return true
		}
		val (range1, range2) = argumentTypes
		// Check if one value (x) is bounded to n bits and the other (y) is a
		// mask consisting of at least those n lowest bits.  In that event, the
		// value x will be unaffected by the bitwise and.
		if (range2.lowerBound.run {
			equals(range2.upperBound) &&
				bitwiseAnd(plusCanDestroy(one, false), false).equalsInt(0)
			})
		{
			// The second argument is a constant power of two.  See if it's big
			// enough to accomodate any value that the first argument might be.
			if (range1.isSubtypeOf(inclusive(zero, range2.upperBound)))
			{
				// range2 is a constant power of two big enough to include all
				// non-zero bits of the first argument.
				callSiteHelper.useAnswer(arguments[0], false)
				return true
			}
		}
		// Now check the same in reverse.
		if (range1.lowerBound.run {
				equals(range1.upperBound) &&
					bitwiseAnd(plusCanDestroy(one, false), false).equalsInt(0)
			})
		{
			// The first argument is a constant power of two.  See if it's big
			// enough to accomodate any value that the second argument might be.
			if (range2.isSubtypeOf(inclusive(zero, range1.upperBound)))
			{
				// range1 is a constant power of two big enough to include all
				// non-zero bits of the second argument.
				callSiteHelper.useAnswer(arguments[1], false)
				return true
			}
		}
		return And.run {
			generateBinaryIntOperation(
				this@P_BitwiseAnd,
				arguments,
				argumentTypes,
				callSiteHelper,
				typeGuaranteeFunction = { restrictedArgTypes ->
					returnTypeGuaranteedByVM(rawFunction, restrictedArgTypes)
				},
				fallbackBody = {
					generateGeneralFunctionInvocation(
						functionToCallReg, false, callSiteHelper, arguments)
				})
		}
	}

	/**
	 * The [manifest] has just gotten a narrower [TypeRestriction] set for a
	 * semantic invocation of this primitive with the given semantic
	 * [arguments].
	 *
	 * When there's an enumeration type involved in a dispatch, the tree may be
	 * expanded to include a [TestForConstantsDecisionStep].  That part of the
	 * tree translateds to code that hashes the value, conceptually masks it
	 * against 0xFFFF_FFFF (i.e., treats it as unsigned), optionally shifts it
	 * right, and masks it to some number of low bits,  That [i32] value is used
	 * in an [L2_MULTIWAY_JUMP] using a [ShiftedHashSplitter] to choose among
	 * 2^n branch targets with what eventually becomes a `lookupswitch` JVM
	 * bytecode.  The code at each target can then be checked against the
	 * expected value(s) that hashed to that entry.
	 *
	 * @param arguments
	 *   The two [L2SemanticBoxedValue]s fed to this [P_BitwiseAnd] primitive.
	 */
	override fun propagateManifestRestrictions(
		arguments: List<L2SemanticValue<BOXED_KIND>>,
		manifest: L2ValueManifest,
		restriction: TypeRestriction)
	{
		// Merge with the commuted version, if present.
		val regular = primitiveInvocation(this, arguments)
		val commuted = primitiveInvocation(this, arguments.reversed())
		manifest.mergeSemanticValueEquivalentsIfPresent(commuted, regular)
		manifest.mergeSemanticValueEquivalentsIfPresent(
			commuted.unboxedInt, regular.unboxedInt)

		// Only attempt to do the propagation if the value is an enumeration,
		// so that some entries might be eliminated by their hash.
		if (!restriction.type.isEnumeration) return
		val (premask, mask) = arguments
		// See if the second argmuent of the bitwise-and is a constant.
		if (!mask.isConstant) return
		val maskConstant = mask.constant!!
		// Is the mask an int?
		if (!maskConstant.isInt) return
		val maskInt = maskConstant.extractInt
		// Is the mask a power of two?
		if (maskInt and (maskInt + 1) != 0) return
		// First look for this being a bitwise-and of the hash of some value.
		pattern {
			P_Hash(capture(0))
		}.matchForEach(premask, manifest) { (valueToHash) ->
			val equivalentIntValueToHash =
				manifest.equivalentSemanticValue(valueToHash.unboxedInt)
					?: return@matchForEach
			val type = manifest.restrictionFor(equivalentIntValueToHash).type
			if (type.isEnumeration)
			{
				val values = type.instances.filter { v ->
					restriction.containsValue(fromInt(v.hash() and maskInt))
				}
				manifest.intersectType(
					equivalentIntValueToHash,
					enumerationWith(setFromCollection(values)))
			}
		}
		pattern {
			P_BitShiftRight(
				P_BitwiseAnd(
					P_Hash(capture(0)),
					captureConstant(1)),
				captureConstant(2))
		}.matchForEach(premask, manifest) {
				(valueToHash, unsignedMask, shift) ->
			if (!unsignedMask.constant!!.equalsLong(0xFFFF_FFFFL))
				return@matchForEach
			val shiftVal = shift.constant!!
			if (!shiftVal.isInt) return@matchForEach
			val shiftInt = shiftVal.extractInt
			if (shiftInt !in 0..31) return@matchForEach
			val equivalentIntValueToHash =
				manifest.equivalentSemanticValue(valueToHash)
					?: return@matchForEach
			val type = manifest.restrictionFor(equivalentIntValueToHash).type
			if (!type.isEnumeration) return@matchForEach
			// Ignore metatypes, since their instances' subtypes would also be
			// considered members of the type, and they could have any hashes.
			// TODO Refine this to handle bottomMeta and singleton metas.
			if (type.isInstanceMeta) return@matchForEach
			val values = type.instances.filter { v ->
				restriction.containsValue(
					fromInt((v.hash() ushr shiftInt) and maskInt))
			}
			manifest.intersectType(
				equivalentIntValueToHash,
				enumerationWith(setFromCollection(values)))
		}
	}

	override val semanticInfixOperatorString: String? get() = "And"

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(integers, integers), integers)
}
