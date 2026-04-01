/*
 * L2_JUMP_IF_COMPARE_INT.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
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

package avail.interpreter.levelTwo.operation.numbers

import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operation.L2ConditionalJump
import avail.interpreter.levelTwo.operation.NumericComparator
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionConditions
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator

/**
 * Jump to the target if int1 compares to int2 in the way requested by the
 * [numericComparator].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * @property numericComparator
 *   The [NumericComparator] on which this [L2_JUMP_IF_COMPARE_INT] is based.
 */
class L2_JUMP_IF_COMPARE_INT(
	var numericComparator: L2ArbitraryConstantOperand<NumericComparator>,
	var int1: L2ReadIntOperand,
	var int2: L2ReadIntOperand,
	@On(SUCCESS) var ifTrue: L2PcOperand,
	@On(FAILURE) var ifFalse: L2PcOperand
): L2ConditionalJump()
{
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)

		val restriction1 = int1.restriction().intersection(
			manifest.restrictionFor(int1.semanticValue()))
		val restriction2 = int2.restriction().intersection(
			manifest.restrictionFor(int2.semanticValue()))

		// Restrict both values along both branches.
		val (rest1, rest2, rest3, rest4) =
			numericComparator.constant.computeRestrictions(
				restriction1.forBoxed(), restriction2.forBoxed()
			).map(TypeRestriction::forUnboxedInt)
		ifTrue.manifest().setRestriction(int1.semanticValue(), rest1)
		ifTrue.manifest().setRestriction(int2.semanticValue(), rest2)
		ifFalse.manifest().setRestriction(int1.semanticValue(), rest3)
		ifFalse.manifest().setRestriction(int2.semanticValue(), rest4)
		if (numericComparator.constant == NumericComparator.Equal)
		{
			// Along the <"=", ifTrue> branch, the values are now synonyms.
			ifTrue.manifest().agglomerateSynonym(
				setOf(int1.semanticValue(), int2.semanticValue()),
				int1.restriction().intersection(int2.restriction()))
		}
		else if (numericComparator.constant == NumericComparator.NotEqual)
		{
			// Along the <"≠", ifFalse> branch, the values are now synonyms.
			ifFalse.manifest().agglomerateSynonym(
				setOf(int1.semanticValue(), int2.semanticValue()),
				int1.restriction().intersection(int2.restriction()))
		}
	}

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(int1.registerString())
		append(" ")
		append(numericComparator.constant.comparatorName)
		append(" ")
		append(int2.registerString())
		renderOperandsExcludingFields(
			desiredOperandTypes, ::int1, ::int2)
	}

	override val name: String
		get() = "${super.name} (${numericComparator.constant.comparatorName})"

	override fun interestingConditions(): List<L2SplitCondition?> = buildList {
		int2.constantOrNull?.let { constant ->
			// We can tell from the output restrictions what condition to wish
			// for (and its negation).  However, earlier comparisons may have
			// made the restriction unduly restrictive, and it may fail to find
			// a suitable split position (i.e., for v>10, when we've already
			// narrowed v to [0..20], we should still split (for the positive
			// case) with the test v ∈ [11..∞] instead of v ∈ [11..20], so that
			// it can split at an earlier position where, say, v ∈ [11..1000]
			// was known.  Wishing for v ∈ [11.20] would fail to detect that
			// split point.
			//
			// Note that even though we know the value is an i32 here, we wish
			// for [11..∞] instead of [11..MAX_INT], in case there was a point
			// before the unboxing that detected, say, [11..10^100].
			val restriction1 = boxedRestrictionForType(extendedIntegers)
			val restriction2 = boxedRestrictionForConstant(constant)
			val (rest1, _, rest3, _) =
				numericComparator.constant
					.computeRestrictions(restriction1, restriction2)
					.map(TypeRestriction::forUnboxedInt)
			// Wish it was statically true or statically false.  But only if
			// that situation would lead to a block that isn't cold.
			if (!ifTrue.targetBlock().isCold)
			{
				addAll(typeRestrictionConditions(setOf(int1.register()), rest1))
			}
			if (!ifFalse.targetBlock().isCold)
			{
				addAll(typeRestrictionConditions(setOf(int1.register()), rest3))
			}
		}
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// Use the basic generator to check if the branch can be elided.
		compareAndBranchInt(
			numericComparator.constant, int1, int2, ifTrue, ifFalse)
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: if (int1 op int2) goto ifTrue;
		// :: else goto ifFalse;
		load(int1)
		load(int2)
		emitBranch(
			this@L2_JUMP_IF_COMPARE_INT,
			numericComparator.constant.opcode,
			ifTrue,
			ifFalse)
	}
}
