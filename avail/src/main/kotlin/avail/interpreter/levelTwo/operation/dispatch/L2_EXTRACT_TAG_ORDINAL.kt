/*
 * L2_EXTRACT_TAG_ORDINAL.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

import avail.descriptor.representation.AbstractDescriptor.Companion.staticTypeTagOrdinalMethod
import avail.descriptor.types.A_Type.Companion.instanceTag
import avail.descriptor.types.TypeTag
import avail.descriptor.types.TypeTag.Companion.restrictionForTagRestriction
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForConstant
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.constantConditions
import avail.optimizer.L2SplitCondition.Companion.unboxedIntConditions
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor

/**
 * Extract the [TypeTag] of the given object, then extract its
 * [ordinal][Enum.ordinal] as an [Int].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_EXTRACT_TAG_ORDINAL(
	var value: L2ReadBoxedOperand,
	var tagOrdinal: L2WriteIntOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(tagOrdinal.registerString())
		append(" ← TAG(")
		append(value.registerString())
		append(")")
	}

	override fun interestingConditions(): List<L2SplitCondition?> = buildList {
		// Don't merge in the past if one of the paths knew the tag ordinal.
		addAll(unboxedIntConditions(listOf(tagOrdinal.register())))
		// Don't merge in the past if the tag ordinal was known exactly.
		addAll(constantConditions(listOf(tagOrdinal.register())))
	}

	override fun traceCandidateSplitConditions(
		writeOperand: L2WriteOperand<*>,
		restriction: TypeRestriction,
		tracer: L2SplitCondition.RestrictionTracer)
	{
		assert(restriction.isUnboxedInt)
		assert(writeOperand == tagOrdinal)
		if (!tracer.traceTags) return
		// Examine the tag restriction to determine a corresponding (or
		// approximate) restriction with which to split on the value.
		val valueRestriction = restrictionForTagRestriction(restriction)
		tracer.continueTracing(value.register(), valueRestriction)
	}

	override fun L2Regenerator.generateReplacement(
		originalInstruction: L2Instruction)
	{
		// If the tag is statically deducible at this point, use the constant.
		val type = value.type()
		val baseTag = type.instanceTag
		if (baseTag.ordinal == baseTag.highOrdinal
			&& (!baseTag.isSubtagOf(TypeTag.TOP_TYPE_TAG)
				|| baseTag == TypeTag.BOTTOM_TYPE_TAG))
		{
			// This tag always applies, and it has no children, not even the
			// bottom type (which is special in the TypeTag hierarchy).
			val existingValue =
				tagOrdinal.semanticValues().firstOrNull {
					currentManifest.hasSemanticValue(it)
				}
			when (existingValue)
			{
				null -> moveIntRegister(
					unboxedIntConstant(baseTag.ordinal).semanticValue(),
					intWrite(
						tagOrdinal.semanticValues(),
						intRestrictionForConstant(baseTag.ordinal)
					).semanticValues())
				else -> tagOrdinal.semanticValues().forEach { otherValue ->
					if (!currentManifest.hasSemanticValue(otherValue))
					{
						moveIntRegister(existingValue, setOf(otherValue))
					}
				}
			}
			return
		}
		emitTransformedInstruction()
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// If the tag is statically deducible at this point, use the constant.
		val type = value.type()
		val baseTag = type.instanceTag
		if (baseTag.ordinal == baseTag.highOrdinal
			&& (!baseTag.isSubtagOf(TypeTag.TOP_TYPE_TAG)
				|| baseTag == TypeTag.BOTTOM_TYPE_TAG))
		{
			// This tag always applies, and it has no children, not even the
			// bottom type (which is special in the TypeTag hierarchy).
			val existingValue =
				tagOrdinal.semanticValues().firstOrNull {
					currentManifest.hasSemanticValue(it)
				}
			when (existingValue)
			{
				null -> moveIntRegister(
					unboxedIntConstant(baseTag.ordinal).semanticValue(),
					intWrite(
						tagOrdinal.semanticValues(),
						intRestrictionForConstant(baseTag.ordinal)
					).semanticValues())
				else -> tagOrdinal.semanticValues().forEach { otherValue ->
					if (!currentManifest.hasSemanticValue(otherValue))
					{
						moveIntRegister(existingValue, setOf(otherValue))
					}
				}
			}
			return
		}
		+this@L2_EXTRACT_TAG_ORDINAL
	}

	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: tagOrdinal = value.staticTypeTagOrdinal();
		translator.load(method, value)
		staticTypeTagOrdinalMethod.generateCall(method)
		translator.store(method, tagOrdinal.register())
	}
}
