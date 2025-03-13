/*
 * NumericComparator.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.levelTwo.operation

import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.lowerInclusive
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.upperInclusive
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.bottomRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operation.numbers.L2_JUMP_IF_COMPARE_INT
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import org.objectweb.asm.Opcodes

/**
 * A [NumericComparator] represents the effect of comparing two numbers, whether
 * boxed or unboxed.  The [L2ConditionalJump] subclasses
 * [L2_JUMP_IF_COMPARE_BOXED] and [L2_JUMP_IF_COMPARE_INT], and related
 * variations, handle the specifics of boxed and int values, respectively.
 *
 * @constructor
 * @param comparatorName
 *   The symbolic name of the opcode for this compare-and-branch.
 * @param opcode
 *   The JVM opcode number for the int version of this compare-and-branch.
 * @param reflexive
 *   Whether (x op x) is true for any (and actually all) x.
 * @property comparatorMethod
 *   The static [CheckedMethod] that compares the numbers, leaving a JVM boolean
 *   on the stack.
 * @param ifTrue1
 *   The function to evaluate with (low1, high1, low2, high2) to produce the
 *   restriction for the first argument along the true path.
 * @param ifFalse1
 *   The function to evaluate with (low2, high2, low1, high1) to produce the
 *   restriction for the first argument along the false path.
 * @param ifTrue2
 *   The function to evaluate with (low1, high1, low2, high2) to produce the
 *   restriction for the first argument along the true path.
 * @param ifFalse2
 *   The function to evaluate with (low2, high2, low1, high1) to produce the
 *   restriction for the second argument along the false path.
 */
enum class NumericComparator(
	internal val comparatorName: String,
	internal val opcode: Int,
	internal val reflexive: Boolean,
	internal val reversed: ()->NumericComparator,
	internal val comparatorMethod: CheckedMethod,
	private val ifTrue1: (A_Type, A_Type) -> TypeRestriction,
	private val ifTrue2: (A_Type, A_Type) -> TypeRestriction,
	private val ifFalse1: (A_Type, A_Type) -> TypeRestriction,
	private val ifFalse2: (A_Type, A_Type) -> TypeRestriction)
{
	/** An instance for testing whether a < b. */
	Less(
		comparatorName = "<",
		opcode = Opcodes.IF_ICMPLT,
		reflexive = false,
		reversed = {Greater},
		comparatorMethod = A_Number.numericLessThanMethod,
		ifTrue1 = ::lessHelper,
		ifTrue2 = ::greaterHelper,
		ifFalse1 = ::greaterOrEqualHelper,
		ifFalse2 = ::lessOrEqualHelper),

	/** An instance for testing whether a > b. */
	Greater(
		comparatorName = ">",
		opcode = Opcodes.IF_ICMPGT,
		reflexive = false,
		reversed = {Less},
		comparatorMethod = A_Number.numericGreaterThanMethod,
		ifTrue1 = ::greaterHelper,
		ifTrue2 = ::lessHelper,
		ifFalse1 = ::lessOrEqualHelper,
		ifFalse2 = ::greaterOrEqualHelper),

	/** An instance for testing whether a ≤ b. */
	LessOrEqual(
		comparatorName = "≤",
		opcode = Opcodes.IF_ICMPLE,
		reflexive = true,
		reversed = {GreaterOrEqual},
		comparatorMethod = A_Number.numericLessOrEqualMethod,
		ifTrue1 = ::lessOrEqualHelper,
		ifTrue2 = ::greaterOrEqualHelper,
		ifFalse1 = ::greaterHelper,
		ifFalse2 = ::lessHelper),

	/** An instance for testing whether a ≥ b. */
	GreaterOrEqual(
		comparatorName = "≥",
		opcode = Opcodes.IF_ICMPGE,
		reflexive = true,
		reversed = {LessOrEqual},
		comparatorMethod = A_Number.numericGreaterOrEqualMethod,
		ifTrue1 = ::greaterOrEqualHelper,
		ifTrue2 = ::lessOrEqualHelper,
		ifFalse1 = ::lessHelper,
		ifFalse2 = ::greaterHelper),

	/**
	 * An instance for testing whether a = b – but only numerically.  Note that
	 * this is *NOT* the same thing as Avail's general equality check , as
	 * floating point numbers can be *numerically* equal to integers.  Floating
	 * point number scan also be unequal to themselves if they're NaNs.  If
	 * restricted to integers, this *is* the same as general equality.
	 */
	Equal(
		comparatorName = "=",
		opcode = Opcodes.IF_ICMPEQ,
		reflexive = true,
		reversed = {Equal},
		comparatorMethod = A_Number.numericEqualMethod,
		ifTrue1 = ::equalHelper,
		ifTrue2 = ::equalHelper,
		ifFalse1 = ::unequalHelper,
		ifFalse2 = ::unequalHelper),

	/** An instance for testing whether a ≠ b. */
	NotEqual(
		comparatorName = "≠",
		opcode = Opcodes.IF_ICMPNE,
		reflexive = false,
		reversed = {NotEqual},
		comparatorMethod = A_Number.numericNotEqualMethod,
		ifTrue1 = ::unequalHelper,
		ifTrue2 = ::unequalHelper,
		ifFalse1 = ::equalHelper,
		ifFalse2 = ::equalHelper);

	/**
	 * Compute the output ranges along the ifTrue and ifFalse edges. It takes
	 * the [TypeRestriction]s of the two integer values being compared (which
	 * may include infinities), and produces four restrictions for the outbound
	 * edges:
	 *   1. the first operand if the condition holds,
	 *   2. the second operand if the condition holds,
	 *   3. the first operand if the condition fails,
	 *   4. the second operand if the condition fails.
	 *
	 * @param restriction1
	 *   The first type restriction.
	 * @param restriction2
	 *   The second type restriction.
	 * @return A tuple containing the computed type restrictions.
	 */
	fun computeRestrictions(
		restriction1: TypeRestriction,
		restriction2: TypeRestriction
	): List<TypeRestriction>
	{
		if (restriction1.isImpossible || restriction2.isImpossible)
		{
			// At least one input is impossible, so the tighter restrictions are
			// also impossible.
			return listOf(
				bottomRestriction,
				bottomRestriction,
				bottomRestriction,
				bottomRestriction)
		}
		assert(restriction1.isBoxed)
		assert(restriction2.isBoxed)
		val type1 = restriction1.type
		val type2 = restriction2.type
		assert(type1.isIntegerRangeType)
		assert(type2.isIntegerRangeType)
		return listOf(
			ifTrue1(type1, type2).intersection(restriction1),
			ifTrue2(type2, type1).intersection(restriction2),
			ifFalse1(type1, type2).intersection(restriction1),
			ifFalse2(type2, type1).intersection(restriction2))
	}

	/**
	 * Compare the boxed number register values and branch to one target or the
	 * other. Restrict the possible values as much as possible along both
	 * branches. Convert the branch to an unconditional jump if possible.  Also,
	 * produce an [L2_JUMP_IF_COMPARE_INT] instruction with int registers if
	 * they're available, actually extracting them if the extraction for both
	 * cannot fail.  We could extract them conditionally, (i.e., have a failure
	 * path to fall back on), but that probably isn't any faster here.
	 */
	fun L2GeneratorInterface.generateCompareAndBranchBoxed(
		number1Read: L2ReadBoxedOperand,
		number2Read: L2ReadBoxedOperand,
		ifTrue: L2PcOperand,
		ifFalse: L2PcOperand)
	{
		// If optimizations have caused the branches to go to the same place,
		// eliminate the branch entirely.
		if (ifTrue.targetBlock() == ifFalse.targetBlock())
		{
			jumpTo(ifTrue.targetBlock())
			return
		}

		val restriction1 = number1Read.restriction()
		val restriction2 = number2Read.restriction()

		val int1SemanticValue = currentManifest.equivalentSemanticValue(
			number1Read.semanticValue().unboxedInt)
		val int2SemanticValue = currentManifest.equivalentSemanticValue(
			number2Read.semanticValue().unboxedInt)
		if (int1SemanticValue !== null && int2SemanticValue !== null)
		{
			// We can compare the int registers instead.
			assert(restriction1.containedByType(i32))
			assert(restriction2.containedByType(i32))
			compareAndBranchInt(
				this@NumericComparator,
				readIntNoFail(int1SemanticValue),
				readIntNoFail(int2SemanticValue),
				ifTrue,
				ifFalse)
			return
		}
		if (!restriction1.containedByType(integers)
			|| !restriction2.containedByType(integers))
		{
			// They're not just integers, so don't bother doing a range
			// analysis.  With concerns like infinities, NaNs, and mixing
			// numeric kinds, it would be too tricky anyhow.  Plus, only the
			// integers have range types.
			+L2_JUMP_IF_COMPARE_BOXED(
				L2ArbitraryConstantOperand(this@NumericComparator),
				number1Read,
				number2Read,
				ifTrue,
				ifFalse)
			return
		}
		// They're both (boxed) integers.

		// Restrict both values along both branches.
		val (rest1, rest2, rest3, rest4) =
			computeRestrictions(restriction1, restriction2)
		when
		{
			rest1.type.isBottom || rest2.type.isBottom ->
			{
				// One of the registers would have an impossible value if the
				// ifTrue branch is taken, so always jump to the ifFalse case.
				currentManifest.setRestriction(
					number1Read.semanticValue(), rest3)
				currentManifest.setRestriction(
					number2Read.semanticValue(),
					restriction2.intersection(rest4))
				jumpTo(ifFalse.targetBlock())
			}
			rest3.type.isBottom || rest4.type.isBottom ->
			{
				// One of the registers would have an impossible value if the
				// ifFalse branch is taken, so always jump to the ifTrue case.
				currentManifest.setRestriction(
					number1Read.semanticValue(),
					restriction1.intersection(rest1))
				currentManifest.setRestriction(
					number2Read.semanticValue(),
					restriction2.intersection(rest2))
				jumpTo(ifTrue.targetBlock())
			}
			restriction1.constantOrNull !== null ->
				// First value is constant, so reverse them.
				+L2_JUMP_IF_COMPARE_BOXED(
					L2ArbitraryConstantOperand(reversed()),
					number2Read,
					number1Read,
					ifTrue,
					ifFalse)
			else ->
				+L2_JUMP_IF_COMPARE_BOXED(
					L2ArbitraryConstantOperand(this@NumericComparator),
					number1Read,
					number2Read,
					ifTrue,
					ifFalse)
		}
	}

	/**
	 * Compare the int register values and branch to one target or the other.
	 * Restrict the possible values as much as possible along both branches.
	 * Convert the branch to an unconditional jump if possible.
	 */
	fun L2GeneratorInterface.generateCompareAndBranchInt(
		int1Reg: L2ReadIntOperand,
		int2Reg: L2ReadIntOperand,
		ifTrue: L2PcOperand,
		ifFalse: L2PcOperand)
	{
		// If optimizations have caused the branches to go to the same place,
		// eliminate the branch entirely.
		if (ifTrue.targetBlock() == ifFalse.targetBlock())
		{
			jumpTo(ifTrue.targetBlock())
			return
		}

		val restriction1 = int1Reg.restriction()
		val restriction2 = int2Reg.restriction()

		assert(restriction1.containedByType(i32))
		assert(restriction2.containedByType(i32))
		// Restrict both values along both branches.
		val (rest1, rest2, rest3, rest4) = computeRestrictions(
			restriction1.forBoxed(), restriction2.forBoxed()
		).map(TypeRestriction::forUnboxedInt)
		when
		{
			currentManifest.semanticValueToSynonym(int1Reg.semanticValue()) ==
				currentManifest.semanticValueToSynonym(
					int2Reg.semanticValue()) ->
			{
				jumpTo(
					(if (reflexive) ifTrue else ifFalse).targetBlock())
			}
			rest1.type.isBottom || rest2.type.isBottom ->
			{
				// One of the registers would have an impossible value if the
				// ifTrue branch is taken, so always jump to the ifFalse case.
				currentManifest.updateRestriction(int1Reg.semanticValue())
				{
					restriction1.intersection(rest3)
				}
				currentManifest.updateRestriction(int2Reg.semanticValue())
				{
					restriction2.intersection(rest4)
				}
				jumpTo(ifFalse.targetBlock())
			}
			rest3.type.isBottom || rest4.type.isBottom ->
			{
				// One of the registers would have an impossible value if the
				// ifFalse branch is taken, so always jump to the ifTrue case.
				currentManifest.updateRestriction(int1Reg.semanticValue())
				{
					restriction1.intersection(rest1)
				}
				currentManifest.updateRestriction(int2Reg.semanticValue())
				{
					restriction2.intersection(rest2)
				}
				jumpTo(ifTrue.targetBlock())
			}
			// First value is constant, so reverse them.
			restriction1.constantOrNull !== null ->
				+L2_JUMP_IF_COMPARE_INT(
					L2ArbitraryConstantOperand(reversed()),
					int2Reg,
					int1Reg,
					ifTrue,
					ifFalse)
			else ->
				+L2_JUMP_IF_COMPARE_INT(
					L2ArbitraryConstantOperand(this@NumericComparator),
					int1Reg,
					int2Reg,
					ifTrue,
					ifFalse)
		}
	}
}

private fun A_Type.narrow(): A_Type = when
{
	lowerBound.equals(upperBound) -> instanceType(lowerBound)
	else -> this
}

/**
 * Given two extended integer subranges, answer the range that a value from the
 * first range can have if it's known to be less than a value from the second
 * range.  As a convenience, this will be intersected with the first type by the
 * caller.
 *
 * If the second range's maximum is some finite X < ∞, it's sufficient to
 * constrain the first range's upper bound to be less than X.  If the second
 * range's maximum X is ∞, whether it's inclusive or not, the first range must
 * still be constrained less than X, so < ∞.
 *
 * Consider the cases, and the resulting restrictions on the first range:
 * ```
 *   [2..7] < [3..5] -> [-∞, 5) -> [2..4]
 *   [2..7] < [3..9] -> [-∞, 9) -> [2..7]
 *   [2..7] < [3..∞] -> [-∞, ∞) -> [2..7]
 *   [2..7] < [3..∞) -> [-∞, ∞) -> [2..7]
 *   [2..∞) < [3..∞] -> [-∞, ∞) -> [2..∞)
 *   [2..∞) < [3..∞) -> [-∞, ∞) -> [2..∞)
 *   [2..∞] < [3..∞] -> [-∞, ∞) -> [2..∞)
 *   [2..∞] < [3..∞) -> [-∞, ∞) -> [2..∞)
 * ```
 */
@Suppress("unused")
private fun lessHelper(
	type1: A_Type,
	type2: A_Type
) = boxedRestrictionForType(
	integerRangeType(negativeInfinity, true,  type2.upperBound, false).narrow())

/**
 * Given two extended integer subranges, answer the range that a value from the
 * first range can have if it's known to be less than or equal to a value from
 * the second range.  As a convenience, this will be intersected with the first
 * type by the caller.
 *
 * If the second range is X inclusive, the first range can be intersected with
 * [-∞..X].  Likewise, if the second range is X exclusive, the first range can
 * be intersected with [-∞..X).
 */
@Suppress("unused")
private fun lessOrEqualHelper(
	type1: A_Type,
	type2: A_Type
) = boxedRestrictionForType(
	integerRangeType(
		negativeInfinity, true, type2.upperBound, type2.upperInclusive
	).narrow())

/**
 * Given two extended integer subranges, answer the range that a value from the
 * first range can have if it's known to be greater than a value from the second
 * range.  As a convenience, this will be intersected with the first type by the
 * caller.
 *
 * The cases are analogous to [lessHelper], but with reversed direction.
 */
@Suppress("unused")
private fun greaterHelper(
	type1: A_Type,
	type2: A_Type
) = boxedRestrictionForType(
	integerRangeType(type2.lowerBound, false, positiveInfinity, true).narrow())

/**
 * Given two extended integer subranges, answer the range that a value from the
 * first range can have if it's known to be greater than or equal to a value
 * from the second range.  As a convenience, this will be intersected with the
 * first type by the caller.
 *
 * The cases are analogous to [lessOrEqualHelper], but with reversed direction.
 */
@Suppress("unused")
private fun greaterOrEqualHelper(
	type1: A_Type,
	type2: A_Type
) = boxedRestrictionForType(
	integerRangeType(
		type2.lowerBound, type2.lowerInclusive, positiveInfinity, true
	).narrow())

/**
 * Given two extended integer subranges, answer the range that a value from the
 * first range can have if it's known to be equal to a value from the second
 * range.  As a convenience, this will be intersected with the first type by the
 * caller.
 */
private fun equalHelper(
	type1: A_Type,
	type2: A_Type
) = boxedRestrictionForType(type1.typeIntersection(type2).narrow())

/**
 * Given two extended integer subranges, answer the range that a value from the
 * first range can have if it's known to be unequal to some value from the
 * second range.  As a convenience, this will be intersected with the first type
 * by the caller.
 */
private fun unequalHelper(
	type1: A_Type,
	type2: A_Type
): TypeRestriction
{
	if (type2.lowerBound.equals(type2.upperBound))
	{
		// Type2 has only one value, so produce a restriction based on type1,
		// but with that one value removed.
		return boxedRestrictionForType(type1).minusValue(type2.lowerBound)
	}
	return lessHelper(type1, type2).union(greaterHelper(type1, type2))
}
