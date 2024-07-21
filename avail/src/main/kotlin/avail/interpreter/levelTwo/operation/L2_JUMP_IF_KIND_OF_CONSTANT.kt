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
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionCondition
import avail.optimizer.L2SplitCondition.Companion.unboxedIntCondition
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
class L2_JUMP_IF_KIND_OF_CONSTANT(
	var value: L2ReadBoxedOperand,
	var constantType: L2ConstantOperand,
	@On(SUCCESS) var ifKind: L2PcOperand,
	@On(FAILURE) var ifNotKind: L2PcOperand
): L2ConditionalJump()
{
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		// Restrict to the intersection along the ifKind branch, and exclude the
		// type along the ifNotKind branch.
		ifKind.manifest().intersectType(value, constantType.constant)
		ifNotKind.manifest().subtractType(value, constantType.constant)
	}

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		builder.append(' ')
		builder.append(value.registerString())
		builder.append(" ∈ ")
		builder.append(constantType.constant)
		renderOperandsExcludingFields(
			builder, desiredOperandTypes, ::value, ::constantType)
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
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
		super.emitTransformedInstruction(regenerator)
	}

	override fun interestingConditions(): List<L2SplitCondition?>
	{
		val conditions = mutableListOf<L2SplitCondition?>()
		if (!ifKind.targetBlock().isCold)
		{
			// The ifKind target is warm, so allow a split back to a point where
			// the value is known to be of the requested kind.
			val constantTypeWhenInt =
				constantType.constant.typeIntersection(i32)
			if (!constantTypeWhenInt.isVacuousType)
			{
				conditions.add(unboxedIntCondition(listOf(value.register())))
			}
			conditions.add(
				typeRestrictionCondition(
					listOf(value.register()),
					boxedRestrictionForType(constantType.constant)))
		}
		if (!ifNotKind.targetBlock().isCold)
		{
			// The ifNotKind target is warm, so allow a split back to a point
			// where the value is known *not* to be an instance.
			conditions.add(
				typeRestrictionCondition(
					listOf(value.register()),
					boxedRestrictionForType(ANY.o)
						.minusType(constantType.constant)))
		}
		return conditions
	}
	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: if (value.isInstanceOf(type)) goto isKind;
		// :: else goto notKind;
		translator.load(method, value.register())
		translator.literal(method, constantType.constant)
		A_BasicObject.isInstanceOfMethod.generateCall(method)
		emitBranch(translator, method, this, Opcodes.IFNE, ifKind, ifNotKind)
	}
}
