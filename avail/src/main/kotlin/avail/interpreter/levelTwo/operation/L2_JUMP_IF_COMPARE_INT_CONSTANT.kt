/*
 * L2_JUMP_IF_COMPARE_INT_CONSTANT.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForConstant
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor

/**
 * Jump to the target if int1 compares to the immediate int in the way
 * requested by the [numericComparator].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * @property numericComparator
 *   The [NumericComparator] on which this [L2_JUMP_IF_COMPARE_INT_CONSTANT] is
 *   based.
 */
class L2_JUMP_IF_COMPARE_INT_CONSTANT(
	private val numericComparator: NumericComparator,
	var intValue: L2ReadIntOperand,
	var constant: L2IntImmediateOperand,
	@On(SUCCESS) var ifTrue: L2PcOperand,
	@On(FAILURE) var ifFalse: L2PcOperand
) : L2ConditionalJump()
{
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		val restriction1 = intValue.restriction()
		val restriction2 = intRestrictionForConstant(constant.value)

		// Restrict the value along both branches.
		val (rest1, _, rest3, _) = numericComparator.computeRestrictions(
			restriction1.forBoxed(), restriction2.forBoxed()
		).map(TypeRestriction::forUnboxedInt)
		ifTrue.manifest().setRestriction(
			intValue.semanticValue(),
			restriction1.intersection(rest1))
		ifFalse.manifest().setRestriction(
			intValue.semanticValue(),
			restriction1.intersection(rest3))
	}

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit) = with(builder)
	{
		renderPreamble(builder)
		append(' ')
		append(intValue.registerString())
		append(' ')
		append(numericComparator.comparatorName)
		append(" #")
		append(constant.value.toString())
		renderOperandsExcludingFields(
			builder, desiredOperandTypes, ::intValue, ::constant)
	}

	override val name: String
		get() = "${super.name} (${numericComparator.comparatorName})"

	override fun interestingConditions(): List<L2SplitCondition?>
	{
		// We can tell from the output restrictions what condition to wish for
		// (and its negation).  However, earlier comparisons may have made the
		// restriction unduly restrictive, and it may fail to find a suitable
		// split position (i.e., for v>10, when we've already narrowed v to
		// [0..20], we should still split (for the positive case) with the test
		// v ∈ [11..∞) instead of v ∈ [11..20], so that it can split at an
		// earlier position where, say, v ∈ [11..1000] was known.  Wishing for
		// v ∈ [11.20] would fail to detect that split point.
		//
		// Note that even though we know the value is an i32 here, we wish for
		// [11..∞) instead of [11..MAX_INT], in case there was a point before
		// the unboxing that detected, say, [11..10^100].

		val restriction1 = boxedRestrictionForType(integers)
		val restriction2 = boxedRestrictionForConstant(fromInt(constant.value))
		val (rest1, _, rest3, _) =
			numericComparator.computeRestrictions(restriction1, restriction2)
				.map(TypeRestriction::forUnboxedInt)
		// Wish it was statically true or statically false.  But only if that
		// situation would lead to a block that isn't cold.
		val conditions = mutableListOf<L2SplitCondition?>()
		if (!ifTrue.targetBlock().isCold)
		{
			conditions.add(
				typeRestrictionCondition(setOf(intValue.register()), rest1))
		}
		if (!ifFalse.targetBlock().isCold)
		{
			conditions.add(
				typeRestrictionCondition(setOf(intValue.register()), rest3))
		}
		return conditions
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
		val manifest = regenerator.currentManifest
		val int1Value = intValue.semanticValue()
		val restriction1 = intValue.restriction().intersection(
			manifest.restrictionFor(int1Value))
		val restriction2 = intRestrictionForConstant(constant.value)

		assert(restriction1.containedByType(i32))
		assert(restriction2.containedByType(i32))
		// Restrict values along both branches.
		val (rest1, _, rest3, _) = numericComparator.computeRestrictions(
			restriction1.forBoxed(), restriction2.forBoxed()
		).map(TypeRestriction::forUnboxedInt)
		val trueManifest = L2ValueManifest(manifest)
		trueManifest.updateRestriction(int1Value) { intersection(rest1) }
		val falseManifest = L2ValueManifest(manifest)
		falseManifest.updateRestriction(int1Value) { intersection(rest3) }
		when
		{
			trueManifest.hasImpossibleRestriction ->
			{
				// One of the registers would have an impossible value if the
				// ifTrue branch is taken, so always jump to the ifFalse case.
				manifest.updateRestriction(int1Value) { intersection(rest3) }
				regenerator.jumpTo(ifFalse.targetBlock())
			}
			falseManifest.hasImpossibleRestriction ->
			{
				// One of the registers would have an impossible value if the
				// ifFalse branch is taken, so always jump to the ifTrue case.
				manifest.updateRestriction(int1Value) { intersection(rest1) }
				regenerator.jumpTo(ifTrue.targetBlock())
			}
			else ->
			{
				regenerator.addInstruction(
					L2_JUMP_IF_COMPARE_INT_CONSTANT(
						numericComparator,
						intValue,
						constant,
						L2PcOperand(
							ifTrue.targetBlock(),
							ifTrue.isBackward,
							trueManifest),
						L2PcOperand(
							ifFalse.targetBlock(),
							ifFalse.isBackward,
							falseManifest)))
			}
		}
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: if (int1 op const) goto ifTrue;
		// :: else goto ifFalse;
		translator.load(method, intValue.register())
		translator.intConstant(method, constant.value)
		emitBranch(
			translator,
			method,
			this,
			numericComparator.opcode,
			ifTrue,
			ifFalse)
	}
}
