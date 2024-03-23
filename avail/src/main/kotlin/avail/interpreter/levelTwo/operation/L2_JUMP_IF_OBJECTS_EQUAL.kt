/*
 * L2_JUMP_IF_OBJECTS_EQUAL.kt
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

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2IsUnboxedIntCondition.Companion.unboxedIntCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticUnboxedInt
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Branch based on whether the two values are equal to each other.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_JUMP_IF_OBJECTS_EQUAL : L2ConditionalJump(
	READ_BOXED.named("first value"),
	READ_BOXED.named("second value"),
	PC.named("is equal", SUCCESS),
	PC.named("is not equal", FAILURE))
{
	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		val first = instruction.operand<L2ReadBoxedOperand>(0)
		val second = instruction.operand<L2ReadBoxedOperand>(1)
		val ifEqual = instruction.operand<L2PcOperand>(2)
		//val ifNotEqual = instruction.operand<L2PcOperand>(3)

		super.instructionWasAdded(instruction, manifest)
		// Merge the source and destination only along the ifEqual branch.
		ifEqual.manifest().mergeExistingSemanticValues(
			first.semanticValue(), second.semanticValue())
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		val first = instruction.operand<L2ReadBoxedOperand>(0)
		val second = instruction.operand<L2ReadBoxedOperand>(1)
		//val ifEqual = instruction.operand<L2PcOperand>(2)
		//val ifNotEqual = instruction.operand<L2PcOperand>(3)

		instruction.renderPreamble(builder)
		builder.append(' ')
		builder.append(first.registerString())
		builder.append(" = ")
		builder.append(second.registerString())
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val first = instruction.operand<L2ReadBoxedOperand>(0)
		val second = instruction.operand<L2ReadBoxedOperand>(1)
		val ifEqual = instruction.operand<L2PcOperand>(2)
		val ifNotEqual = instruction.operand<L2PcOperand>(3)

		// :: if (first.equals(second)) goto ifEqual;
		// :: else goto notEqual;
		translator.load(method, first.register())
		translator.load(method, second.register())
		A_BasicObject.equalsMethod.generateCall(method)
		emitBranch(
			translator, method, instruction, Opcodes.IFNE, ifEqual, ifNotEqual)
	}

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		val boxed1Reg = transformedOperands[0] as L2ReadBoxedOperand
		val boxed2Reg = transformedOperands[1] as L2ReadBoxedOperand
		val ifTrue = transformedOperands[2] as L2PcOperand
		val ifFalse = transformedOperands[3] as L2PcOperand

		val manifest = regenerator.currentManifest
		val restriction1 = manifest.restrictionFor(boxed1Reg.semanticValue())
		val restriction2 = manifest.restrictionFor(boxed2Reg.semanticValue())
		if (restriction1.intersection(restriction2).isImpossible)
		{
			// The restrictions are disjoint, so the comparison is always false.
			// Jump unconditionally to the false case.
			regenerator.jumpTo(ifFalse.targetBlock())
			return
		}
		restriction1.constantOrNull?.let { c1 ->
			restriction2.constantOrNull?.let { c2 ->
				if (c1.equals(c2))
				{
					// The restrictions say the values are the same constant, so
					// it's always true.  Jump unconditionally to the true case.
					regenerator.jumpTo(ifTrue.targetBlock())
					return
				}
			}
		}
		if (!boxed1Reg.restriction().containedByType(i32)
			|| !boxed2Reg.restriction().containedByType(i32))
		{
			return super.emitTransformedInstruction(
				transformedOperands, regenerator)
		}
		// The values are definitely ints, even if they're not necessarily both
		// (or either) in int registers.
		val unreachable = L2BasicBlock("should not reach")
		val int1Reg = regenerator.readInt(
			L2SemanticUnboxedInt(boxed1Reg.semanticValue()), unreachable)
		val int2Reg = regenerator.readInt(
			L2SemanticUnboxedInt(boxed2Reg.semanticValue()), unreachable)
		// Note that we *must not* reuse the manifests in the translated edges
		// ifTrue and ifFalse, since they might not include information about
		// registers freshly generated for int1Reg and int2Reg, which might have
		// had to be constructed from boxed forms.  In particular, there was a
		// case where a boxed value was unboxed (unconditionally), but removed
		// as dead code in the same pass that translated a downstream occurrence
		// of L2_JUMP_IF_OBJECT_EQUAL, which could be translated to an
		// L2_JUMP_IF_COMPARE_INT by the compareAndBranchInt() below.
		regenerator.compareAndBranchInt(
			NumericComparator.Equal,
			int1Reg,
			int2Reg,
			L2PcOperand(ifTrue.targetBlock(), ifTrue.isBackward),
			L2PcOperand(ifFalse.targetBlock(), ifFalse.isBackward))
		assert(!unreachable.currentlyReachable())
	}

	override fun interestingSplitConditions(
		instruction: L2Instruction
	): List<L2SplitCondition?>
	{
		val first = instruction.operand<L2ReadBoxedOperand>(0)
		val second = instruction.operand<L2ReadBoxedOperand>(1)
		//val ifEqual = instruction.operand<L2PcOperand>(2)
		//val ifNotEqual = instruction.operand<L2PcOperand>(3)

		val conditions = mutableListOf<L2SplitCondition?>()
		if (first.restriction().intersectsType(i32)
			&& second.restriction().intersectsType(i32))
		{
			conditions.add(
				unboxedIntCondition(
					listOf(first.register(), second.register())))
		}
		return conditions
	}
}
