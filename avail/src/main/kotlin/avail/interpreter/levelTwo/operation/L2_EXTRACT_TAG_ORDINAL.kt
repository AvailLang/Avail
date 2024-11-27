/*
 * L2_EXTRACT_TAG_ORDINAL.kt
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

import avail.descriptor.representation.AbstractDescriptor.Companion.staticTypeTagOrdinalMethod
import avail.descriptor.types.A_Type.Companion.instanceTag
import avail.descriptor.types.TypeTag
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForConstant
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.unboxedIntCondition
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

	override fun interestingConditions(): List<L2SplitCondition?>
	{
		return listOf(unboxedIntCondition(listOf(tagOrdinal.register())))
	}

	/**
	 * Extract the [L2ReadBoxedOperand] that provided the object whose [TypeTag]
	 * is being extracted.
	 */
	fun sourceOfExtractTag(instruction: L2Instruction): L2ReadBoxedOperand
	{
		return instruction.operand(0)
	}

	override fun generateReplacement(
		regenerator: L2Regenerator,
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
			regenerator.run {
				val existingValue =
					tagOrdinal.semanticValues().firstOrNull {
						currentManifest.hasSemanticValue(it)
					}
				when (existingValue)
				{
					null ->
						regenerator.moveIntRegister(
							regenerator.unboxedIntConstant(baseTag.ordinal)
								.semanticValue(),
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
			}
			return
		}
		super.generateReplacement(regenerator, originalInstruction)
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
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
			regenerator.run {
				val existingValue =
					tagOrdinal.semanticValues().firstOrNull {
						currentManifest.hasSemanticValue(it)
					}
				when (existingValue)
				{
					null -> moveIntRegister(
						regenerator.unboxedIntConstant(baseTag.ordinal)
							.semanticValue(),
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
			}
			return
		}
		super.emitTransformedInstruction(regenerator)
	}

	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: tagOrdinal = value.staticTypeTagOrdinal();
		translator.load(method, value.register())
		staticTypeTagOrdinalMethod.generateCall(method)
		translator.store(method, tagOrdinal.register())
	}
}
