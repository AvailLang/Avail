/*
 * L2_EXTRACT_OBJECT_TYPE_VARIANT_ID.kt
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

import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.staticObjectTypeVariantIdMethod
import avail.descriptor.types.A_Type.Companion.objectTypeVariant
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.WRITE_INT
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor

/**
 * Extract the [ObjectLayoutVariant] of the given object type, then extract its
 * [variantId][ObjectLayoutVariant.variantId] as an [Int].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object L2_EXTRACT_OBJECT_TYPE_VARIANT_ID : L2Operation(
	READ_BOXED.named("object type"),
	WRITE_INT.named("variantId"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val variantId = instruction.operand<L2WriteIntOperand>(1)
		renderPreamble(instruction, builder)
		builder
			.append(' ')
			.append(variantId.registerString())
			.append(" ← OBJECT TYPE VARIANT_ID(")
			.append(value.registerString())
			.append(")")
	}

	override fun generateReplacement(
		instruction: L2Instruction,
		regenerator: L2Regenerator)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val variantId = instruction.operand<L2WriteIntOperand>(1)

		// If the variantId is statically deducible at this point, use the
		// constant.
		val generator = regenerator.targetGenerator
		val restriction =
			generator.currentManifest.restrictionFor(value.semanticValue())
		restriction.constantOrNull?.let { constant ->
			// Extract the variantId from the actual constant right now.
			val variant = constant.objectTypeVariant
			generator.addInstruction(
				L2_MOVE_CONSTANT.unboxedInt,
				L2IntImmediateOperand(variant.variantId),
				variantId)
			return
		}
		super.generateReplacement(instruction, regenerator)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		val variantId = instruction.operand<L2WriteIntOperand>(1)

		// :: variantId = staticObjectVariantId(value);
		translator.load(method, value.register())
		staticObjectTypeVariantIdMethod.generateCall(method)
		translator.store(method, variantId.register())
	}
}
