/*
 * L2_GET_OBJECT_FIELD.kt
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

import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.utility.mapToSet
import avail.utility.notNullAnd
import org.objectweb.asm.MethodVisitor

/**
 * Extract the specified field of the object.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_GET_OBJECT_FIELD(
	var sourceObject: L2ReadBoxedOperand,
	var fieldAtom: L2ConstantOperand,
	var fieldValue: L2WriteBoxedOperand
): L2Instruction()
{
	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		builder.append(' ')
		builder.append(fieldValue.registerString())
		builder.append(" ← ")
		builder.append(sourceObject)
		builder.append("[")
		builder.append(fieldAtom)
		builder.append("]")
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
		// Strengthen the field value's type in case the incoming object type
		// is now stronger, perhaps due to code splitting.
		val manifest = regenerator.currentManifest
		val objectRestriction = sourceObject.restriction().intersection(
			manifest.restrictionFor(sourceObject.semanticValue()))
		val objectType = objectRestriction.type
		val fieldType = objectType.fieldTypeAt(fieldAtom.constant)
		val newFieldRestriction =
			fieldValue.restriction().intersectionWithType(fieldType)
		val newFieldWrite = L2WriteBoxedOperand(
			fieldValue.semanticValues(),
			newFieldRestriction,
			fieldValue.register())
		regenerator.addInstruction(
			L2_GET_OBJECT_FIELD(sourceObject, fieldAtom, newFieldWrite))
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		translator.load(method, sourceObject.register())
		val variants = sourceObject.restriction().positiveGroup.objectVariants
		val indices = variants
			?.mapToSet { it.fieldToSlotIndex[fieldAtom.constant]!! }
		if (indices.notNullAnd { size == 1 })
		{
			// The field index is the same for every variant possible at this
			// point.  Get the field by index.
			translator.intConstant(method, indices!!.single())
			AvailObject.fieldAtIndexMethod.generateCall(method)
		}
		else
		{
			translator.literal(method, fieldAtom.constant)
			AvailObject.fieldAtMethod.generateCall(method)
		}
		translator.store(method, fieldValue.register())
	}
}
