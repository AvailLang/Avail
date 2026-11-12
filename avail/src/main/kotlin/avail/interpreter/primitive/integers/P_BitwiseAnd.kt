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

import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.bitwiseAnd
import avail.descriptor.representation.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_Number.Companion.equalsLong
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Number.Companion.extractLong
import avail.descriptor.representation.A_Number.Companion.greaterOrEqual
import avail.descriptor.representation.A_Number.Companion.isInt
import avail.descriptor.representation.A_Number.Companion.lessThan
import avail.descriptor.representation.A_Number.Companion.plusCanDestroy
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instances
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.typeIntersection
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i63
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i64
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.dispatch.TestForConstantsDecisionStep
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.dispatch.L2_MULTIWAY_JUMP
import avail.interpreter.levelTwo.operation.dispatch.ShiftedHashSplitter
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.And
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive2
import avail.interpreter.primitive.general.P_Hash
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2GeneratorInterface.Companion.readTwoInts
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation
import avail.optimizer.values.PatternBuilder.Companion.pattern
import avail.utility.notNullAnd

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
		a: AvailObject,
		b: AvailObject
	): A_BasicObject? = interpreter.run {
		return a.bitwiseAnd(b, true)
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type
	{
		assert(argumentTypes.size == 2)
		val (aRange, bRange) = argumentTypes

		val aLower = aRange.lowerBound
		val aUpper = aRange.upperBound
		val bLower = bRange.lowerBound
		val bUpper = bRange.upperBound
		// If either value is constrained to a positive range, then its upper
		// bound is the limit.
		val upper = if (aLower.greaterOrEqual(zero))
		{
			if (bLower.greaterOrEqual(zero))
			{
				// Use the min of the upper bounds.
				if (aUpper.lessThan(bUpper)) aUpper
				else bUpper
			}
			else aUpper
		}
		else if (bLower.greaterOrEqual(zero)) bUpper
		else
		{
			// Give up, as the result may be negative.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		}

		// It's non-negative.
		if (upper.isFinite) return inclusive(zero, upper)
		return wholeNumbers
	}

	override fun L2GeneratorInterface.readAsTwoInts(
		semanticValue1: L2SemanticValue,
		semanticValue2: L2SemanticValue,
		onFailure: L2BasicBlock,
		range: A_Type
	): Pair<L2ReadIntOperand, L2ReadIntOperand>?
	{
		readTwoInts(semanticValue1, semanticValue2, onFailure, range)
			?.let { return it }
		val type1 = restrictionFor(semanticValue1).type
		val type2 = restrictionFor(semanticValue2).type
		// Special case.  If type1 is a non-negative value in the range, it has
		// no bits at or above the range's sign bit.  So if type2 is any integer
		// constant, we can pre-mask it with the range's upper bound and use
		// that constant as an int argument instead.
		val nonnegativeRange = range.typeIntersection(wholeNumbers)
		if (type1.isSubtypeOf(nonnegativeRange)
			&& type2.run { lowerBound == upperBound })
		{
			val masked = type2.upperBound.bitwiseAnd(range.upperBound, false)
				.extractLong
			return Pair(
				readIntNoFail(semanticValue1),
				unboxedIntConstant(masked))
		}
		// And the same thing where the first argument is the constant.
		if (type2.isSubtypeOf(nonnegativeRange)
			&& type1.run { lowerBound == upperBound })
		{
			val masked = type1.upperBound.bitwiseAnd(range.upperBound, false)
				.extractLong
			return Pair(
				unboxedIntConstant(masked),
				readIntNoFail(semanticValue2))
		}
		return null
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
				},
				range = i64)
		}
	}

	override fun L2GeneratorInterface.emitTransformedInfalliblePrimitive(
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand)
	{
		val (x, y) = arguments.elements
		when
		{
			// x & 0 = 0
			y.constantOrNull.notNullAnd { equalsInt(0) } ->
				moveConstant(zero, result.semanticValues())
			// 0 & y = 0
			x.constantOrNull.notNullAnd { equalsInt(0) } ->
				move(y.semanticValue(), result.semanticValues())
			// x & x = x
			currentManifest.isEquivalentSemanticValue(
				x.semanticValue(), y.semanticValue()
			) -> move(x.semanticValue(), result.semanticValues())
			// x & k, where x ∈ i63 & k ≥ 0, -> x & (k & maxLong)
			x.restriction.type.isSubtypeOf(i63)
				&& y.constantOrNull.notNullAnd { greaterOrEqual(zero) }
			-> +L2_BIT_LOGIC_OP(
				And,
				readIntNoFail(x.semanticValue()),
				unboxedIntConstant(
					y.constantOrNull!!.bitwiseAnd(
						i63.upperBound,
						true
					).extractLong),
				intWrite(result.semanticValues(), x.restriction))
			// k & y, where y ∈ i63 & k ≥ 0, -> (k & maxLong) & y
			y.restriction.type.isSubtypeOf(i63)
				&& x.constantOrNull.notNullAnd { greaterOrEqual(zero) }
			-> +L2_BIT_LOGIC_OP(
				And,
				readIntNoFail(y.semanticValue()),
				unboxedIntConstant(
					x.constantOrNull!!.bitwiseAnd(
						i63.upperBound,
						true
					).extractLong),
				intWrite(result.semanticValues(), y.restriction))
			else -> emitBasicInfalliblePrimitive(rawFunction, arguments, result)
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
	 * right, and masks it to some number of low bits,  That [u32] value is used
	 * in an [L2_MULTIWAY_JUMP] using a [ShiftedHashSplitter] to choose among
	 * 2^n branch targets with what eventually becomes a `lookupswitch` JVM
	 * bytecode.  The code at each target can then be checked against the
	 * expected value(s) that hashed to that entry.
	 *
	 * @param arguments
	 *   The two [L2SemanticValue]s fed to this [P_BitwiseAnd] primitive.
	 */
	override fun propagateManifestRestrictions(
		arguments: List<L2SemanticValue>,
		manifest: L2ValueManifest,
		restriction: TypeRestriction)
	{
		// Merge with the commuted version, if present.
		val regular = primitiveInvocation(this, arguments)
		val commuted = primitiveInvocation(this, arguments.reversed())
		manifest.mergeSemanticValueEquivalentsIfPresent(commuted, regular)

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
			val equivalentIntValueToHash = manifest
				.equivalentPopulatedSemanticValue(
					valueToHash,
					INTEGER_KIND
				) ?: return@matchForEach
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
