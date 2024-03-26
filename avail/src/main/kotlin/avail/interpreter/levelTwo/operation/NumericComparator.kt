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
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.A_Number.Companion.lessThan
import avail.descriptor.numbers.A_Number.Companion.plusCanDestroy
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.interpreter.levelTwo.L2OperandType.Companion.CONSTANT
import avail.interpreter.levelTwo.L2OperandType.Companion.INT_IMMEDIATE
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.values.L2SemanticUnboxedInt
import org.objectweb.asm.Opcodes

/**
 * A [NumericComparator] represents the effect of comparing two numbers, whether
 * boxed or unboxed.  The two [L2OldControlFlowOperation] subclasses
 * [L2_JUMP_IF_COMPARE_BOXED] and [L2_JUMP_IF_COMPARE_INT] handle the
 * specifics of boxed and int values, respectively.
 *
 * @constructor
 * @param comparatorName
 *   The symbolic name of the opcode for this compare-and-branch.
 * @param opcode
 *   The JVM opcode number for the int version of this compare-and-branch.
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
	internal val reversed: ()->NumericComparator,
	internal val comparatorMethod: CheckedMethod,
	private val ifTrue1:
		(A_Number, A_Number, A_Number, A_Number) -> TypeRestriction,
	private val ifTrue2:
		(A_Number, A_Number, A_Number, A_Number) -> TypeRestriction,
	private val ifFalse1:
		(A_Number, A_Number, A_Number, A_Number) -> TypeRestriction,
	private val ifFalse2:
		(A_Number, A_Number, A_Number, A_Number) -> TypeRestriction)
{
	/** An instance for testing whether a < b. */
	Less(
		"<",
		Opcodes.IF_ICMPLT,
		{Greater},
		A_Number.numericLessThanMethod,
		::lessHelper,
		::greaterHelper,
		::greaterOrEqualHelper,
		::lessOrEqualHelper),

	/** An instance for testing whether a > b. */
	Greater(
		">",
		Opcodes.IF_ICMPGT,
		{Less},
		A_Number.numericGreaterThanMethod,
		::greaterHelper,
		::lessHelper,
		::lessOrEqualHelper,
		::greaterOrEqualHelper),

	/** An instance for testing whether a ≤ b. */
	LessOrEqual(
		"≤",
		Opcodes.IF_ICMPLE,
		{GreaterOrEqual},
		A_Number.numericLessOrEqualMethod,
		::lessOrEqualHelper,
		::greaterOrEqualHelper,
		::greaterHelper,
		::lessHelper),

	/** An instance for testing whether a ≥ b. */
	GreaterOrEqual(
		"≥",
		Opcodes.IF_ICMPGE,
		{LessOrEqual},
		A_Number.numericGreaterOrEqualMethod,
		::greaterOrEqualHelper,
		::lessOrEqualHelper,
		::lessHelper,
		::greaterHelper),

	/**
	 * An instance for testing whether a = b – but only numerically.  Note that
	 * this is *NOT* the same thing as Avail's general equality check , as
	 * floating point numbers can be *numerically* equal to integers.  Floating
	 * point number scan also be unequal to themselves if they're NaNs.  If
	 * restricted to integers, this *is* the same as general equality.
	 */
	Equal(
		"=",
		Opcodes.IF_ICMPEQ,
		{Equal},
		A_Number.numericEqualMethod,
		::equalHelper,
		::equalHelper,
		::unequalHelper,
		::unequalHelper),

	/** An instance for testing whether a ≠ b. */
	NotEqual(
		"≠",
		Opcodes.IF_ICMPNE,
		{NotEqual},
		A_Number.numericNotEqualMethod,
		::unequalHelper,
		::unequalHelper,
		::equalHelper,
		::equalHelper);

	/**
	 * A suitable [L2_JUMP_IF_COMPARE_BOXED] to use as an operation for
	 * numerically comparing two boxed numbers.
	 */
	private val jumpIfCompareNumber = L2_JUMP_IF_COMPARE_BOXED(this)

	/**
	 * A suitable [L2_JUMP_IF_COMPARE_BOXED] to use as an operation for
	 * numerically comparing a boxed number to an constant (a [CONSTANT]].
	 */
	private val jumpIfCompareNumberConstant =
		L2_JUMP_IF_COMPARE_BOXED_CONSTANT(this)

	/**
	 * A suitable [L2_JUMP_IF_COMPARE_INT] to use as an operation for comparing
	 * two ints already in int registers.
	 */
	private val jumpIfCompareInt = L2_JUMP_IF_COMPARE_INT(this)

	/**
	 * A suitable [L2_JUMP_IF_COMPARE_INT_CONSTANT] to use as an operation for
	 * comparing two ints, the second of which is a constant (an
	 * [INT_IMMEDIATE]).
	 */
	private val jumpIfCompareIntConstant = L2_JUMP_IF_COMPARE_INT_CONSTANT(this)

	/**
	 * Compute the output ranges along the ifTrue and ifFalse edges. It takes
	 * the [TypeRestriction]s of the two values being compared, and produces
	 * four restrictions for the outbound edges:
	 *   1. the first operand if the condition holds,
	 *   2. the second operand if the condition holds,
	 *   3. the first operand if the condition fails,
	 *   4. the second operand if the condition fails.
	 * Computes the restrictions for two type restrictions.
	 *
	 * @param restriction1 The first type restriction.
	 * @param restriction2 The second type restriction.
	 * @return A tuple containing the computed type restrictions.
	 */
	fun computeRestrictions(
		restriction1: TypeRestriction,
		restriction2: TypeRestriction
	): List<TypeRestriction>
	{
		assert(restriction1.isBoxed)
		assert(restriction2.isBoxed)
		val type1 = restriction1.type
		val type2 = restriction2.type
		assert(type1.isIntegerRangeType)
		assert(type2.isIntegerRangeType)
		val low1 = type1.lowerBound
		val high1 = type1.upperBound
		val low2 = type2.lowerBound
		val high2 = type2.upperBound
		return listOf(
			ifTrue1(low1, high1, low2, high2),
			ifTrue2(low2, high2, low1, high1),
			ifFalse1(low1, high1, low2, high2),
			ifFalse2(low2, high2, low1, high1))
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
	fun compareAndBranchBoxed(
		generator: L2Generator,
		number1Reg: L2ReadBoxedOperand,
		number2Reg: L2ReadBoxedOperand,
		ifTrue: L2PcOperand,
		ifFalse: L2PcOperand)
	{
		val restriction1 = number1Reg.restriction()
		val restriction2 = number2Reg.restriction()

		val manifest = generator.currentManifest
		val int1SemanticValue = L2SemanticUnboxedInt(number1Reg.semanticValue())
		val int2SemanticValue = L2SemanticUnboxedInt(number2Reg.semanticValue())
		if (manifest.hasSemanticValue(int1SemanticValue)
			&& manifest.hasSemanticValue(int2SemanticValue))
		{
			// We can compare the int registers instead.
			assert(restriction1.containedByType(i32))
			assert(restriction2.containedByType(i32))
			val unreachable = L2BasicBlock("Should not reach")
			compareAndBranchInt(
				generator,
				generator.readInt(int1SemanticValue, unreachable),
				generator.readInt(int2SemanticValue, unreachable),
				ifTrue,
				ifFalse)
			assert(unreachable.currentlyReachable())
			return
		}
		if (!restriction1.containedByType(integers)
			|| !restriction2.containedByType(integers))
		{
			// They're not just integers, so don't bother doing a range
			// analysis.  With concerns like infinities, NaNs, and mixing
			// numeric kinds, it would be too tricky anyhow.  Plus, only the
			// integers have range types.
			generator.addInstruction(
				jumpIfCompareNumber,
				number1Reg,
				number2Reg,
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
				generator.currentManifest.setRestriction(
					number1Reg.semanticValue(),
					restriction1.intersection(rest3))
				generator.currentManifest.setRestriction(
					number2Reg.semanticValue(),
					restriction2.intersection(rest4))
				generator.jumpTo(ifFalse.targetBlock())
			}
			rest3.type.isBottom || rest4.type.isBottom ->
			{
				// One of the registers would have an impossible value if the
				// ifFalse branch is taken, so always jump to the ifTrue case.
				generator.currentManifest.setRestriction(
					number1Reg.semanticValue(),
					restriction1.intersection(rest1))
				generator.currentManifest.setRestriction(
					number2Reg.semanticValue(),
					restriction2.intersection(rest2))
				generator.jumpTo(ifTrue.targetBlock())
			}
			restriction2.constantOrNull !== null ->
			{
				// Special case where second value is constant.
				generator.addInstruction(
					jumpIfCompareNumberConstant,
					number1Reg,
					L2ConstantOperand(restriction2.constantOrNull!!),
					ifTrue,
					ifFalse)
			}
			restriction1.constantOrNull !== null ->
			{
				// First value is constant, so reverse them.
				generator.addInstruction(
					reversed().jumpIfCompareNumberConstant,
					number2Reg,
					L2ConstantOperand(restriction1.constantOrNull!!),
					ifTrue,
					ifFalse)
			}
			else -> generator.addInstruction(
				jumpIfCompareNumber, number1Reg, number2Reg, ifTrue, ifFalse)
		}
	}

	/**
	 * Compare the int register values and branch to one target or the other.
	 * Restrict the possible values as much as possible along both branches.
	 * Convert the branch to an unconditional jump if possible.
	 */
	fun compareAndBranchInt(
		generator: L2Generator,
		int1Reg: L2ReadIntOperand,
		int2Reg: L2ReadIntOperand,
		ifTrue: L2PcOperand,
		ifFalse: L2PcOperand)
	{
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
			rest1.type.isBottom || rest2.type.isBottom ->
			{
				// One of the registers would have an impossible value if the
				// ifTrue branch is taken, so always jump to the ifFalse case.
				generator.currentManifest.updateRestriction(
					int1Reg.semanticValue())
				{
					intersection(restriction1).intersection(rest3)
				}
				generator.currentManifest.updateRestriction(
					int2Reg.semanticValue())
				{
					intersection(restriction2).intersection(rest4)
				}
				generator.jumpTo(ifFalse.targetBlock())
			}
			rest3.type.isBottom || rest4.type.isBottom ->
			{
				// One of the registers would have an impossible value if the
				// ifFalse branch is taken, so always jump to the ifTrue case.
				generator.currentManifest.updateRestriction(
					int1Reg.semanticValue())
				{
					intersection(restriction1).intersection(rest1)
				}
				generator.currentManifest.updateRestriction(
					int2Reg.semanticValue())
				{
					intersection(restriction2).intersection(rest2)
				}
				generator.jumpTo(ifTrue.targetBlock())
			}
			restriction2.constantOrNull !== null ->
			{
				// Special case where second value is constant.
				generator.addInstruction(
					jumpIfCompareIntConstant,
					int1Reg,
					L2IntImmediateOperand(
						restriction2.constantOrNull!!.extractInt),
					ifTrue,
					ifFalse)
			}
			restriction1.constantOrNull !== null ->
			{
				// First value is constant, so reverse them.
				generator.addInstruction(
					reversed().jumpIfCompareIntConstant,
					int2Reg,
					L2IntImmediateOperand(
						restriction1.constantOrNull!!.extractInt),
					ifTrue,
					ifFalse)
			}
			else -> generator.addInstruction(
				jumpIfCompareInt, int1Reg, int2Reg, ifTrue, ifFalse)
		}
	}
}

private fun A_Type.narrow(): A_Type = when
{
	lowerBound.equals(upperBound) -> instanceType(lowerBound)
	else -> this
}

/** Compute the minimum of the two boxed integers. */
private fun min(number1: A_Number, number2: A_Number) =
	(if (number1.lessThan(number2)) number1 else number2).makeImmutable()

/** Compute the maximum of the two boxed integers. */
private fun max(number1: A_Number, number2: A_Number) =
	(if (number1.greaterThan(number2)) number1 else number2).makeImmutable()

/** Add an int to the given boxed number. */
private operator fun A_Number.plus(delta: Int) =
	plusCanDestroy(fromInt(delta), false).makeImmutable()

/**
 * Given two [i32] subranges, answer the range that a value from the first range
 * can have if it's known to be less than a value from the second range.
 */
@Suppress("UNUSED_PARAMETER")
private fun lessHelper(
	low1: A_Number, high1: A_Number, low2: A_Number, high2: A_Number
) = boxedRestrictionForType(inclusive(low1, min(high1, high2 + -1)).narrow())

/**
 * Given two [i32] subranges, answer the range that a value from the first range
 * can have if it's known to be less than or equal to a value from the second
 * range.
 */
@Suppress("UNUSED_PARAMETER")
private fun lessOrEqualHelper(
	low1: A_Number, high1: A_Number, low2: A_Number, high2: A_Number
) = boxedRestrictionForType(inclusive(low1, min(high1, high2)).narrow())

/**
 * Given two [i32] subranges, answer the range that a value from the first range
 * can have if it's known to be greater than a value from the second range.
 */
@Suppress("UNUSED_PARAMETER")
private fun greaterHelper(
	low1: A_Number, high1: A_Number, low2: A_Number, high2: A_Number
) = boxedRestrictionForType(
	inclusive(max(low1, low2 + 1), high1).narrow())

/**
 * Given two [i32] subranges, answer the range that a value from the first range
 * can have if it's known to be greater than or equal to a value from the second
 * range.
 */
@Suppress("UNUSED_PARAMETER")
private fun greaterOrEqualHelper(
	low1: A_Number, high1: A_Number, low2: A_Number, high2: A_Number
) = boxedRestrictionForType(inclusive(max(low1, low2), high1).narrow())

/**
 * Given two [i32] subranges, answer the range that a value from the first range
 * can have if it's known to be equal to a value from the second range.
 */
private fun equalHelper(
	low1: A_Number, high1: A_Number, low2: A_Number, high2: A_Number
) = boxedRestrictionForType(
	inclusive(max(low1, low2), min(high1, high2)).narrow())

/**
 * Given two [i32] subranges, answer the range that a value from the first range
 * can have if it's known to be unequal to some value from the second range.
 */
private fun unequalHelper(
	low1: A_Number, high1: A_Number, low2: A_Number, high2: A_Number
): TypeRestriction
{
	if (low2.equals(high2))
	{
		// The second value is a particular constant which we can exclude in the
		// event the values are unequal.
		return TypeRestriction.restriction(
			type = inclusive(low1, high1),
			constantOrNull = null,
			givenExcludedValues = setOf(low2))
	}
	return lessHelper(low1, high1, low2, high2).union(
		greaterHelper(low1, high1, low2, high2))
}
