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
package avail.interpreter.levelTwo.operation.dispatch

import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.negativeOne
import avail.descriptor.objects.ObjectDescriptor.Companion.staticObjectVariantIdMethod
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectLayoutVariant.Companion.variantFromId
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Type.Companion.instances
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT_INT
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator

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
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(variantId.registerString())
		append(" ← VARIANT_ID(")
		append(sourceObject.registerString())
		append(")")
	}

	override fun L2GeneratorInterface.analyzeAndOptionallyRewrite(
	): L2Instruction?
	{
		variantId.semanticValues()
			.firstOrNull { readIfAvailable(it, INTEGER_KIND) != null }
			?.let { existingValue ->
				// Found one. Populate the rest.
				val others = variantId.semanticValues()
					.filterNot(currentManifest::hasSemanticValue)
				if (others.isNotEmpty())
				{
					move(existingValue, others)
					return null
				}
			}
		// There wasn't an equivalent register handy.  Fall back to emitting a
		// copy of this instruction.
		return this@L2_EXTRACT_OBJECT_VARIANT_ID
	}

	override fun transformedByRegenerator(
		regenerator: L2Regenerator
	): L2Instruction
	{
		var idRestriction = variantId.restriction()
		sourceObject.restriction().positiveGroup.objectVariants
			?.map { fromInt(it.variantId) }
			?.let { ids ->
				idRestriction = idRestriction.intersectionWithType(
					enumerationWith(setFromCollection(ids)))
			}
		sourceObject.restriction().negativeGroup.objectVariants
			?.map { fromInt(it.variantId) }
			?.let { ids ->
				idRestriction = idRestriction.minusValues(
					setFromCollection(ids))
			}
		val destination = variantId.clone() as L2WriteIntOperand
		destination.restrict { idRestriction }
		val replacement = when
		{
			idRestriction.isImpossible ->
			{
				// Populate the output variant id with -1, which isn't a valid
				// variant id.
				regenerator.addInstruction(
					INTEGER_KIND.moveConstant(
						negativeOne,
						destination.semanticValues()))
				// Answer something that makes the entire manifest impossible,
				// but will transform into an L2_IMPOSSIBLE_CODE in the next
				// regeneration pass.
				regenerator.impossibleCodeInstruction()
			}
			idRestriction.isConstant ->
				L2_MOVE_CONSTANT_INT(
					L2IntImmediateOperand(
						idRestriction.constantOrNull!!.extractInt),
					destination)
			else -> L2_EXTRACT_OBJECT_VARIANT_ID(sourceObject, destination)
		}
		replacement.layout.updateOperands(
			replacement,
			regenerator::transformOperand)
		return replacement
	}

	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun traceCandidateSplitConditions(
		writeOperand: L2WriteOperand<*>,
		restriction: TypeRestriction,
		tracer: L2SplitCondition.RestrictionTracer)
	{
		assert(writeOperand == variantId)
		if (!tracer.traceVariants) return
		// If only one or a few variant ids are present in the variant's
		// restriction, we can approximate the constraint on the sourceObject.
		val variantsType = restriction.type
		if (!variantsType.isEnumeration)
		{
			// We should only handle variant id enumerations.  In theory this
			// could be expanded to handle contiguous spans of variant ids, but
			// these don't really occur in practice, and the split conditions
			// don't take advantage of them anyhow.
			return
		}
		assert(!variantsType.isInstanceMeta)
		// Collect the possible variants.  Note that if a variant has been
		// garbage collected, an object or object type instance can't ever be
		// constructed for it, so we can ignore it as a possibility.
		val variants =
			variantsType.instances.mapNotNull { variantFromId(it.extractInt) }
		if (variants.isEmpty())
		{
			// No variants remain possible that would satisfy this condition.
			// Don't expend any effort at further tracing the origin for this
			// path for code splitting.
			return
		}
		variants.forEach { variant ->
			tracer.continueTracing(
				sourceObject.register(),
				restrictionForType(variant.mostGeneralObjectType)
					.intersectionWithObjectVariant(variant)
			)
		}
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: variantId = staticObjectVariantId(value);
		load(sourceObject)
		generateCall(staticObjectVariantIdMethod)
		store(variantId.register())
	}
}
