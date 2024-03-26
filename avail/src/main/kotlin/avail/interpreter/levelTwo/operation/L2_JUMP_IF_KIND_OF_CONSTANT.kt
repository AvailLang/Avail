/*
 * L2_JUMP_IF_KIND_OF_CONSTANT.kt
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

import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OldInstruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.CONSTANT
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2IsUnboxedIntCondition.Companion.unboxedIntCondition
import avail.optimizer.L2SplitCondition.L2MeetsRestrictionCondition.Companion.typeRestrictionCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticUnboxedInt
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Jump to the target if the object is an instance of the constant type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_JUMP_IF_KIND_OF_CONSTANT : L2OldConditionalJump(
	READ_BOXED.named("value"),
	CONSTANT.named("constant type"),
	PC.named("is kind", SUCCESS),
	PC.named("is not kind", FAILURE))
{
	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		val ifKind = instruction.operand<L2PcOperand>(2)
		val ifNotKind = instruction.operand<L2PcOperand>(3)

		super.instructionWasAdded(instruction, manifest)
		// Restrict to the intersection along the ifKind branch, and exclude the
		// type along the ifNotKind branch.
		val oldRestriction = value.restriction().intersection(
			manifest.restrictionFor(value.semanticValue()))
		ifKind.manifest().setRestriction(
			value.semanticValue(),
			oldRestriction.intersectionWithType(constantType.constant))
		ifNotKind.manifest().setRestriction(
			value.semanticValue(),
			oldRestriction.minusType(constantType.constant))
	}

	override fun appendToWithWarnings(
		instruction: L2OldInstruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		//		final L2PcOperand ifKind = instruction.operand(2);
//		final L2PcOperand ifNotKind = instruction.operand(3);
		instruction.renderPreamble(builder)
		builder.append(' ')
		builder.append(value.registerString())
		builder.append(" ∈ ")
		builder.append(constantType.constant)
		instruction.renderOperandsStartingAt(2, desiredTypes, builder)
	}

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		val value = transformedOperands[0] as L2ReadBoxedOperand
		val constantType = transformedOperands[1] as L2ConstantOperand
		val ifKind = transformedOperands[2] as L2PcOperand
		val ifNotKind = transformedOperands[3] as L2PcOperand

		// Check for special cases.
		val valueValue = value.semanticValue()
		val unboxedValueValue = L2SemanticUnboxedInt(valueValue)
		val typeConstant = constantType.constant
		val manifest = regenerator.currentManifest
		val restriction = manifest.restrictionFor(value.semanticValue())
		when
		{
			// Always true.
			restriction.containedByType(typeConstant) ->
			{
				regenerator.jumpTo(ifKind.targetBlock())
				return
			}
			// Always false.
			!restriction.intersectsType(typeConstant) ->
			{
				regenerator.jumpTo(ifNotKind.targetBlock())
				return
			}
			// Contingent.  Check int range case.
			manifest.hasSemanticValue(unboxedValueValue) ->
			{
				// We have the value in an unboxed int.  Use it.
				val constantIntType = typeConstant.typeIntersection(i32)
				val low = constantIntType.lowerBound.extractInt
				val high = constantIntType.upperBound.extractInt
				val isContiguous = !constantIntType.isEnumeration
					|| constantIntType.instanceCount.equalsInt(
						high - low + 1)
				if (isContiguous)
				{
					val firstSuccess = L2BasicBlock("low bound ok")
					regenerator.compareAndBranchInt(
						NumericComparator.GreaterOrEqual,
						manifest.readInt(unboxedValueValue),
						regenerator.unboxedIntConstant(low),
						edgeTo(firstSuccess),
						ifNotKind)
					regenerator.startBlock(firstSuccess)
					regenerator.compareAndBranchInt(
						NumericComparator.LessOrEqual,
						manifest.readInt(unboxedValueValue),
						regenerator.unboxedIntConstant(high),
						ifKind,
						ifNotKind)
					return
				}
				// Rather than do spot-checks here, just fall through.
			}
		}
		// The test is still contingent, and too much hassle to optimize.
		super.emitTransformedInstruction(transformedOperands, regenerator)
	}

	override fun interestingSplitConditions(
		instruction: L2Instruction
	): List<L2SplitCondition?>
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1).constant
		val ifKind = instruction.operand<L2PcOperand>(2)
		val ifNotKind = instruction.operand<L2PcOperand>(3)

		val conditions = mutableListOf<L2SplitCondition?>()
		if (!ifKind.targetBlock().isCold)
		{
			// The ifKind target is warm, so allow a split back to a point where
			// the value is known to be of the requested kind.
			val constantTypeWhenInt = constantType.typeIntersection(i32)
			if (!constantTypeWhenInt.isVacuousType)
			{
				conditions.add(unboxedIntCondition(listOf(value.register())))
			}
			conditions.add(
				typeRestrictionCondition(
					listOf(value.register()),
					boxedRestrictionForType(constantType)))
		}
		if (!ifNotKind.targetBlock().isCold)
		{
			// The ifNotKind target is warm, so allow a split back to a point
			// where the value is known *not* to be an instance.
			conditions.add(
				typeRestrictionCondition(
					listOf(value.register()),
					boxedRestrictionForType(ANY.o)
						.minusType(constantType)))
		}
		return conditions
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constantType = instruction.operand<L2ConstantOperand>(1)
		val ifKind = instruction.operand<L2PcOperand>(2)
		val ifNotKind = instruction.operand<L2PcOperand>(3)

		// :: if (value.isInstanceOf(type)) goto isKind;
		// :: else goto notKind;
		translator.load(method, value.register())
		translator.literal(method, constantType.constant)
		A_BasicObject.isInstanceOfMethod.generateCall(method)
		emitBranch(translator, method, instruction, Opcodes.IFNE, ifKind, ifNotKind)
	}
}
