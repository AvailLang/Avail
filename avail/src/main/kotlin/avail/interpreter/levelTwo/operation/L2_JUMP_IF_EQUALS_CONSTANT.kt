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
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2ConstantOperand
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
 * Jump to `"if equal"` if the value equals the constant, otherwise jump to `"if
 * unequal"`.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct an `L2_JUMP_IF_EQUALS_CONSTANT`.
 */
class L2_JUMP_IF_EQUALS_CONSTANT(
	var value: L2ReadBoxedOperand,
	var constant: L2ConstantOperand,
	@On(SUCCESS) var ifEqual: L2PcOperand,
	@On(FAILURE) var ifNotEqual: L2PcOperand
): L2ConditionalJump()
{
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)

		// Restrict the value to the constant along the ifEqual branch, and
		// exclude the constant along the ifNotEqual branch.
		val oldRestriction = value.restriction()
		ifEqual.manifest().setRestriction(
			value.semanticValue(),
			boxedRestrictionForConstant(constant.constant))
		ifNotEqual.manifest().setRestriction(
			value.semanticValue(),
			oldRestriction.minusValue(constant.constant))
	}

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		builder.append(' ')
		builder.append(value.registerString())
		builder.append(" = ")
		builder.append(constant.constant)
		renderOperandsExcludingFields(builder, ::value, ::constant)
	}

	override fun generateReplacement(
		regenerator: L2Regenerator)
	{
		regenerator.jumpIfEqualsConstant(
			value,
			constant.constant,
			ifEqual.targetBlock(),
			ifNotEqual.targetBlock())
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
		assert(!regenerator.currentManifest.hasImpossibleRestriction)
		val valueRestriction =
			regenerator.currentManifest.restrictionFor(value.semanticValue())
		valueRestriction.constantOrNull?.let { valueValue ->
			// The value is a constant here, so compare it statically.
			val target = when
			{
				valueValue.equals(constant.constant) -> ifEqual
				else -> ifNotEqual
			}
			regenerator.jumpTo(target.targetBlock())
			return
		}
		if (!valueRestriction.intersectsType(
				instanceTypeOrMetaOn(constant.constant)))
		{
			// The restriction says it can never equal the constant.
			regenerator.jumpTo(ifNotEqual.targetBlock())
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
					ifNotEqual)
				return
			}
		}
		// Fall back to the object equality check.
		super.emitTransformedInstruction(regenerator)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
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
			translator, method, this, Opcodes.IFNE, ifEqual, ifNotEqual)
	}

	override fun interestingConditions(): List<L2SplitCondition?>
	{
		val conditions = mutableListOf<L2SplitCondition?>()
		if (!ifEqual.targetBlock().isCold)
		{
			// The ifEqual path is warm, so allow a code split back to a point
			// where it's known to be equal to the constant.
			if (constant.constant.isInt)
			{
				conditions.add(unboxedIntCondition(listOf(value.register())))
				conditions.add(
					typeRestrictionCondition(
						setOf(value.register()),
						intRestrictionForConstant(
							constant.constant.extractInt)))
			}
			conditions.add(
				typeRestrictionCondition(
					setOf(value.register()),
					boxedRestrictionForConstant(constant.constant)))
		}
		if (!ifNotEqual.targetBlock().isCold)
		{
			// The ifUnequal path is warm, so allow a code split back to a point
			// where the value is known to be unequal to the constant.
			conditions.add(
				typeRestrictionCondition(
					setOf(value.register()),
					boxedRestrictionForType(ANY.o)
						.minusValue(constant.constant)))
		}
		return conditions
	}
}
