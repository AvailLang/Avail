/*
 * L2_JUMP_IF_COMPARE_BOXED.kt
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

import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionCondition
import avail.optimizer.L2SplitCondition.Companion.unboxedIntCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Jump to the target if number1 compares to number2 in the way requested by the
 * [numericComparator].  Note that they may be incomparable, due to the way
 * floating point numbers work, in which case the comparison produces false.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *   Construct an [L2_JUMP_IF_COMPARE_BOXED].
 * @property numericComparator
 *   The [NumericComparator] on which this [L2_JUMP_IF_COMPARE_BOXED] is based.
 */
class L2_JUMP_IF_COMPARE_BOXED(
	private val numericComparator: NumericComparator,
	var number1: L2ReadBoxedOperand,
	var number2: L2ReadBoxedOperand,
	@On(SUCCESS) var ifTrue: L2PcOperand,
	@On(FAILURE) var ifFalse: L2PcOperand
) : L2ConditionalJump()
{
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		val restriction1 = number1.restriction()
		val restriction2 = number2.restriction()

		if (restriction1.containedByType(integers)
			&& restriction2.containedByType(integers))
		{
			// Restrict both values along both branches.
			val (rest1, rest2, rest3, rest4) =
				numericComparator.computeRestrictions(
					restriction1, restriction2)
			ifTrue.manifest().setRestriction(number1.semanticValue(), rest1)
			ifTrue.manifest().setRestriction(number2.semanticValue(), rest2)
			ifFalse.manifest().setRestriction(number1.semanticValue(), rest3)
			ifFalse.manifest().setRestriction(number2.semanticValue(), rest4)
		}
	}

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		builder.append(' ')
		builder.append(number1.registerString())
		builder.append(" ")
		builder.append(numericComparator.comparatorName)
		builder.append(" ")
		builder.append(number2.registerString())
		renderOperandsExcludingFields(
			builder, desiredOperandTypes, ::number1, ::number2)
	}

	override val name: String
		get() = "${super.name} (${numericComparator.comparatorName})"

	override fun interestingConditions(): List<L2SplitCondition?>
	{
		val conditions = mutableListOf<L2SplitCondition?>()
		if (number1.restriction().intersectsType(i32)
			&& number2.restriction().intersectsType(i32))
		{
			// Both values could be in int registers at some point in the past.
			// A conjunction mechanism would be very hard to use, and harder to
			// implement, so we split on each register instead.
			conditions.add(unboxedIntCondition(listOf(number1.register())))
			conditions.add(unboxedIntCondition(listOf(number2.register())))
		}
		number2.restriction().constantOrNull?.let { constant ->
			// If the constant is an integer, and if the argument is an extended
			// integer, we can try to leverage that by keeping the code split
			// whenever the comparison would have been always true or always
			// false.
			if (constant.isInstanceOf(integers)
				&& number1.restriction().containedByType(integers))
			{
				// HOWEVER, don't use the current restriction for the value,
				// since it might have been narrowed by previous comparisons.
				// Use the broadest range (integers), to get the broadest type
				// that can be used to split the code as early as possible.
				val restriction1 = boxedRestrictionForType(integers)
				val restriction2 = boxedRestrictionForConstant(constant)
				val (rest1, _, rest3, _) =
					numericComparator.computeRestrictions(
						restriction1, restriction2)
				// First, wish it was true, but only if the true path isn't
				// cold.
				if (!ifTrue.targetBlock().isCold)
				{
					conditions.add(
						typeRestrictionCondition(
							setOf(number1.register()),
							rest1))
				}
				// Also wish it was false, but only if the false path isn't
				// cold.
				if (!ifFalse.targetBlock().isCold)
				{
					conditions.add(
						typeRestrictionCondition(
							setOf(number1.register()),
							rest3))
				}
			}
		}
		return conditions
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
		// Use the basic generator to check if the branch can be elided.
		regenerator.compareAndBranchBoxed(
			numericComparator,
			number1,
			number2,
			ifTrue,
			ifFalse)
	}

	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: if (num1 op num2) goto ifTrue;
		// :: else goto ifFalse;
		translator.load(method, number1.register())
		translator.load(method, number2.register())
		numericComparator.comparatorMethod.generateCall(method)
		// The boolean is now on the stack.  See if we can emit a single branch
		// and fall-through, versus having to emit a branch and a jump.
		when (offset + 1)
		{
			ifTrue.instruction.offset ->
				translator.jumpIf(method, Opcodes.IFEQ, ifFalse)
			ifFalse.instruction.offset ->
				translator.jumpIf(method, Opcodes.IFNE, ifTrue)
			else ->
			{
				// Can't fall through.  Emit a branch and a jump.
				translator.jumpIf(method, Opcodes.IFEQ, ifFalse)
				translator.jump(method, ifTrue)
			}
		}
	}
}
