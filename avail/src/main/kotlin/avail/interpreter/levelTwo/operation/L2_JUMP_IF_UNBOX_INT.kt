/*
 * L2_JUMP_IF_UNBOX_INT.kt
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
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.A_Type.Companion.instanceTag
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_INT
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2IsUnboxedIntCondition.Companion.unboxedIntCondition
import avail.optimizer.L2SplitCondition.L2MeetsRestrictionCondition.Companion.typeRestrictionCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticExtractedTag
import avail.optimizer.values.L2SemanticUnboxedInt
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Jump to `"if unboxed"` if an [Int] was unboxed from an [AvailObject],
 * otherwise jump to `"if not unboxed"`.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_JUMP_IF_UNBOX_INT : L2ConditionalJump(
	READ_BOXED.named("source"),
	WRITE_INT.named("destination", SUCCESS),
	PC.named("if not unboxed", FAILURE),
	PC.named("if unboxed", SUCCESS))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)
		//val ifNotUnboxed = instruction.operand<L2PcOperand>(2)
		//val ifUnboxed = instruction.operand<L2PcOperand>(3)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(destination.registerString())
		builder.append(" ←? ")
		builder.append(source.registerString())
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)
		val ifNotUnboxed = instruction.operand<L2PcOperand>(2)
		val ifUnboxed = instruction.operand<L2PcOperand>(3)

		source.instructionWasAdded(manifest)
		val semanticSource = source.semanticValue()
		// Don't add the destination along the failure edge.
		ifNotUnboxed.instructionWasAdded(
			L2ValueManifest(manifest).apply {
				subtractType(semanticSource, i32)
			})
		// Ensure the value is available along the success edge.
		manifest.intersectType(source.semanticValue(), i32)
		destination.instructionWasAdded(manifest)
		ifUnboxed.instructionWasAdded(
			L2ValueManifest(manifest).apply {
				intersectType(destination.pickSemanticValue(), i32)
			})
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)
		val ifNotUnboxed = instruction.operand<L2PcOperand>(2)
		val ifUnboxed = instruction.operand<L2PcOperand>(3)

		// :: if (!source.isInt()) goto ifNotUnboxed;
		translator.load(method, source.register())
		A_Number.isIntMethod.generateCall(method)
		method.visitJumpInsn(
			Opcodes.IFEQ, translator.labelFor(ifNotUnboxed.offset()))
		// :: else {
		// ::    destination = source.extractInt();
		// ::    goto ifUnboxed;
		// :: }
		translator.load(method, source.register())
		A_Number.extractIntStaticMethod.generateCall(method)
		translator.store(method, destination.register())
		translator.jump(method, instruction, ifUnboxed)
	}

	override fun interestingSplitConditions(
		instruction: L2Instruction
	): List<L2SplitCondition?>
	{
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		val destination = instruction.operand<L2WriteIntOperand>(1)
		val ifNotUnboxed = instruction.operand<L2PcOperand>(2)
		val ifUnboxed = instruction.operand<L2PcOperand>(3)

		val conditions = mutableListOf<L2SplitCondition?>()
		if (!ifUnboxed.targetBlock().isCold)
		{
			// The ifUnboxed path is warm, so split to preserve the value being
			// in an unboxed int register.
			conditions.add(
				unboxedIntCondition(
					listOf(source.register(), destination.register())))
		}
		if (!ifNotUnboxed.targetBlock().isCold)
		{
			// The ifNotUnboxedpath is warm, so split to preserve the value
			// falling entirely outside the range of an int.
			conditions.add(
				typeRestrictionCondition(
					listOf(source.register(), destination.register()),
					boxedRestrictionForType(ANY.o).minusType(i32)))
		}
		return conditions
	}

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		val source = transformedOperands[0] as L2ReadBoxedOperand
		val destination = transformedOperands[1] as L2WriteIntOperand
		val ifNotUnboxed = transformedOperands[2] as L2PcOperand
		val ifUnboxed = transformedOperands[3] as L2PcOperand

		// Regeneration can strengthen this type via code splitting, or even
		// obviate the need to re-extract into an int register if it's
		// already in one along this split path.
		val manifest = regenerator.currentManifest
		val sourceRestriction = manifest.restrictionFor(source.semanticValue())
		val sourceSemanticValue = source.semanticValue()
		// See if there's an int version of a synonym of the source.  There
		// must be a less messy way of doing this.
		val sourceInt = manifest.semanticValueToSynonym(source.semanticValue())
			.semanticValues()
			.map(::L2SemanticUnboxedInt)
			.firstOrNull(manifest::hasSemanticValue)
			?: L2SemanticUnboxedInt(source.semanticValue())
		// If the value's tag has been extracted already, strengthen it.
		val tagSemanticValue = manifest.equivalentPopulatedSemanticValue(
			L2SemanticUnboxedInt(L2SemanticExtractedTag(sourceSemanticValue)))
		tagSemanticValue?.let {
			// Narrow the tag's range if possible.
			manifest.updateRestriction(it) {
				intersectionWithType(
					sourceRestriction.type.instanceTag.tagRangeType())
			}
		}
		when
		{
			manifest.hasSemanticValue(sourceInt) ->
			{
				// It's already unboxed.  However, ensure all destination
				// semantic values have been written.
				destination.semanticValues().forEach { dest ->
					if (!manifest.hasSemanticValue(dest))
						regenerator.moveRegister(
							L2_MOVE.unboxedInt, sourceInt, setOf(dest))
				}
				tagSemanticValue?.let {
					manifest.updateRestriction(it) {
						intersectionWithType(
							manifest.restrictionFor(sourceInt).type.instanceTag
								.tagRangeType())
					}
				}
				regenerator.jumpTo(ifUnboxed.targetBlock())
			}
			sourceRestriction.containedByType(i32) ->
			{
				// It's not already unboxed, but it's an int32.
				regenerator.addInstruction(L2_UNBOX_INT, source, destination)
				regenerator.jumpTo(ifUnboxed.targetBlock())
			}
			!sourceRestriction.intersectsType(i32) ->
			{
				// It can't be an int32.
				regenerator.jumpTo(ifNotUnboxed.targetBlock())
			}
			else ->
			{
				// It's still contingent on the value.
				super.emitTransformedInstruction(
					transformedOperands, regenerator)
			}
		}
	}
}
