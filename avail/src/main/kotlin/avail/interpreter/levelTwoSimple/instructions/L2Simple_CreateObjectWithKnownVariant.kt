/*
 * L2Simple_CreateObjectWithKnownVariant.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.interpreter.levelTwoSimple.instructions

import avail.descriptor.objects.ObjectDescriptor.Companion.createUninitializedObject
import avail.descriptor.objects.ObjectDescriptor.Companion.setField
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.representation.A_Type
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwoSimple.L2SimpleInstructionTransformer
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.RegisterSet
import avail.interpreter.levelTwoSimple.instructions.registers.Write

/**
 * We know the [ObjectLayoutVariant] of an object that we will create.  We also
 * have a [ReadArray] of values to use for the slot values, in the same order
 * that the variant lists them.
 *
 * The site of object creation is guaranteed to produce an object with a type at
 * least as specific as [guaranteedType].
 */
class L2Simple_CreateObjectWithKnownVariant(
	val variant: ObjectLayoutVariant,
	val guaranteedType: A_Type,
	val valuesInVariantSlotOrder: ReadArray,
	val outputObject: Write,
	nextOffset: Offset
) : L2SimpleInstruction(nextOffset)
{
	init {
		assert(variant.realSlotCount == valuesInVariantSlotOrder.size)
		guaranteedType.makeShared()
	}

	override fun step(
		registers: RegisterSet,
		interpreter: Interpreter
	): Offset
	{
		val newObject = createUninitializedObject(variant, guaranteedType)
		for (i in 0 until variant.realSlotCount)
		{
			setField(newObject, i + 1, registers[valuesInVariantSlotOrder[i]])
		}
		registers[outputObject] = newObject
		return nextOffset
	}

	override fun L2SimpleInstructionTransformer.transformed() =
		L2Simple_CreateObjectWithKnownVariant(
			variant = variant,
			guaranteedType = guaranteedType,
			valuesInVariantSlotOrder = read(valuesInVariantSlotOrder),
			outputObject = write(outputObject),
			nextOffset = target(nextOffset))
}
