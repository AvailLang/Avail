/*
 * L2_EXTRACT_OBJECT_VARIANT_ID.kt
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

import avail.descriptor.objects.ObjectDescriptor.Companion.staticObjectVariantIdMethod
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.representation.A_BasicObject.Companion.objectVariant
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor

/**
 * Extract the [ObjectLayoutVariant] of the given object, then extract its
 * [variantId][ObjectLayoutVariant.variantId] as an [Int].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_EXTRACT_OBJECT_VARIANT_ID(
	var sourceObject: L2ReadBoxedOperand,
	var variantId: L2WriteIntOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(variantId.registerString())
		append(" ← VARIANT_ID(")
		append(sourceObject.registerString())
		append(")")
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
		val manifest = regenerator.currentManifest
		variantId.semanticValues()
			.firstOrNull { regenerator.readIfAvailable(it) != null }
			?.let { existingValue ->
				// Found one. Populate the rest..
				val others = variantId.semanticValues()
					.filterNot(manifest::hasSemanticValue)
				if (others.isNotEmpty())
				{
					regenerator.moveIntRegister(existingValue, others)
					return
				}
			}
		// There wasn't an equivalent register handy.  Fall back to emitting a
		// copy of this instruction.
		super.emitTransformedInstruction(regenerator)
	}

	override fun generateReplacement(
		regenerator: L2Regenerator,
		originalInstruction: L2Instruction)
	{
		// If the variantId is statically deducible at this point, use the
		// constant.
		val restriction = regenerator.currentManifest.restrictionFor(
			sourceObject.semanticValue())
		restriction.constantOrNull?.let { constant ->
			// Extract the variantId from the actual constant right now.
			val variant = constant.objectVariant
			regenerator.moveIntRegister(
				regenerator.unboxedIntConstant(variant.variantId)
					.semanticValue(),
				variantId.semanticValues())
			return
		}
		super.generateReplacement(regenerator, originalInstruction)
	}

	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: variantId = staticObjectVariantId(value);
		translator.load(method, sourceObject.register())
		staticObjectVariantIdMethod.generateCall(method)
		translator.store(method, variantId.register())
	}
}
