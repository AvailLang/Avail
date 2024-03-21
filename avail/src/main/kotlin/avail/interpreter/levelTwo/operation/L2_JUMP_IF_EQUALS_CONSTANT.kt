/*
 * L2_JUMP_IF_EQUALS_CONSTANT.kt
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

import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.CONSTANT
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForConstant
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
 * Jump to `"if equal"` if the value equals the constant, otherwise jump
 * to `"if unequal"`.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct an `L2_JUMP_IF_EQUALS_CONSTANT`.
 */
object L2_JUMP_IF_EQUALS_CONSTANT :
	L2ConditionalJump(
		READ_BOXED.named("value"),
		CONSTANT.named("constant"),
		PC.named("if equal", SUCCESS),
		PC.named("if unequal", FAILURE))
{
	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		val reader = instruction.operand<L2ReadBoxedOperand>(0)
		val constant = instruction.operand<L2ConstantOperand>(1)
		val ifEqual = instruction.operand<L2PcOperand>(2)
		val ifNotEqual = instruction.operand<L2PcOperand>(3)
		super.instructionWasAdded(instruction, manifest)

		// Restrict the value to the constant along the ifEqual branch, and
		// exclude the constant along the ifNotEqual branch.
		val oldRestriction = reader.restriction()
		ifEqual.manifest().setRestriction(
			reader.semanticValue(),
			boxedRestrictionForConstant(constant.constant))
		ifNotEqual.manifest().setRestriction(
			reader.semanticValue(),
			oldRestriction.minusValue(constant.constant))
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constant = instruction.operand<L2ConstantOperand>(1)
		//		final L2PcOperand ifEqual = instruction.operand(2);
		//		final L2PcOperand ifUnequal = instruction.operand(3);

		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(value.registerString())
		builder.append(" = ")
		builder.append(constant.constant)
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun generateReplacement(
		instruction: L2Instruction,
		regenerator: L2Regenerator)
	{
		val value = regenerator.transformOperand(
			instruction.operand<L2ReadBoxedOperand>(0))
		val constant = regenerator.transformOperand(
			instruction.operand<L2ConstantOperand>(1))
		val ifEqual = regenerator.transformOperand(
			instruction.operand<L2PcOperand>(2))
		val ifUnequal = regenerator.transformOperand(
			instruction.operand<L2PcOperand>(3))
		regenerator.jumpIfEqualsConstant(
			value,
			constant.constant,
			ifEqual.targetBlock(),
			ifUnequal.targetBlock())
	}

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		val value = transformedOperands[0] as L2ReadBoxedOperand
		val constant = transformedOperands[1] as L2ConstantOperand
		val ifEqual = transformedOperands[2] as L2PcOperand
		val ifUnequal = transformedOperands[3] as L2PcOperand


		assert(!regenerator.currentManifest.hasImpossibleRestriction)
		val valueRestriction =
			regenerator.currentManifest.restrictionFor(value.semanticValue())
		valueRestriction.constantOrNull?.let { valueValue ->
			// The value is a constant here, so compare it statically.
			val target = when
			{
				valueValue.equals(constant.constant) -> ifEqual
				else -> ifUnequal
			}
			regenerator.jumpTo(target.targetBlock())
			return
		}
		if (!valueRestriction.intersectsType(
				instanceTypeOrMetaOn(constant.constant)))
		{
			// The restriction says it can never equal the constant.
			regenerator.jumpTo(ifUnequal.targetBlock())
			return
		}
		if (constant.constant.isInt)
		{
			// Do the comparison as ints, if possible.
			if (valueRestriction.isUnboxedInt)
			{
				// Otherwise, compare as ints.
				regenerator.compareAndBranchInt(
					NumericComparator.Equal,
					regenerator.currentManifest.readInt(
						L2SemanticUnboxedInt(value.semanticValue())),
					regenerator.unboxedIntConstant(
						constant.constant.extractInt),
					ifEqual,
					ifUnequal)
				return
			}
		}
		// Fall back to the object equality check.
		super.emitTransformedInstruction(transformedOperands, regenerator)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val constant = instruction.operand<L2ConstantOperand>(1)
		val ifEqual = instruction.operand<L2PcOperand>(2)
		val ifUnequal = instruction.operand<L2PcOperand>(3)

		if (constant.constant.isInstanceOf(i32))
		{
			// Even though the value might not be an i32, we can use the
			// A_Number.equalsIntStatic(i32) method.
			translator.load(method, value.register())
			translator.intConstant(method, constant.constant.extractInt)
			A_Number.equalsIntMethod.generateCall(method)
		}
		else
		{
			// :: if (value.equals(constant)) goto ifEqual;
			// :: else goto ifUnequal;
			translator.load(method, value.register())
			translator.literal(method, constant.constant)
			A_BasicObject.equalsMethod.generateCall(method)
		}
		emitBranch(
			translator, method, instruction, Opcodes.IFNE, ifEqual, ifUnequal)
	}

	override fun interestingSplitConditions(
		instruction: L2Instruction
	): List<L2SplitCondition?>
	{
		val reader = instruction.operand<L2ReadBoxedOperand>(0)
		val constant = instruction.operand<L2ConstantOperand>(1)
		val ifEqual = instruction.operand<L2PcOperand>(2)
		val ifUnequal = instruction.operand<L2PcOperand>(3)

		val conditions = mutableListOf<L2SplitCondition?>()
		if (!ifEqual.targetBlock().isCold)
		{
			// The ifEqual path is warm, so allow a code split back to a point
			// where it's known to be equal to the constant.
			if (constant.constant.isInt)
			{
				conditions.add(unboxedIntCondition(listOf(reader.register())))
				conditions.add(
					typeRestrictionCondition(
						setOf(reader.register()),
						intRestrictionForConstant(
							constant.constant.extractInt)))
			}
			conditions.add(
				typeRestrictionCondition(
					setOf(reader.register()),
					boxedRestrictionForConstant(constant.constant)))
		}
		if (!ifUnequal.targetBlock().isCold)
		{
			// The ifUnequal path is warm, so allow a code split back to a point
			// where the value is known to be unequal to the constant.
			conditions.add(
				typeRestrictionCondition(
					setOf(reader.register()),
					boxedRestrictionForType(ANY.o)
						.minusValue(constant.constant)))
		}
		return conditions
	}
}
