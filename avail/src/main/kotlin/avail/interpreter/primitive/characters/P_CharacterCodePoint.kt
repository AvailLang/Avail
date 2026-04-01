/*
 * P_CharacterCodePoint.kt
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
package avail.interpreter.primitive.characters

import avail.descriptor.character.A_Character.Companion.codePoint
import avail.descriptor.character.CharacterDescriptor
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.SetDescriptor.Companion.generateSetFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.characterCodePoints
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.CHARACTER
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2_CODEPOINT_TO_CHARACTER
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.values.L2SemanticUnboxedInt.Companion.boxed

/**
* **Primitive:** Extract the [code&#32;point][IntegerDescriptor] from a
 * [character][CharacterDescriptor].
 */
@Suppress("unused")
object P_CharacterCodePoint : Primitive(1, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val character = interpreter.argument(0)
		return interpreter.primitiveSuccess(fromInt(character.codePoint))
	}

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper): Boolean
	{
		if (!currentManifest.caresAboutSemanticValues) return false
		val characterRead = arguments.single()
		val characterInstruction =
			characterRead.definitionSkippingMoves(currentManifest)
		if (characterInstruction is L2_CODEPOINT_TO_CHARACTER)
		{
			callSiteHelper.useAnswer(
				readBoxed(characterInstruction.source.semanticValue().boxed),
				false)
			return true
		}
		return false
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type
	{
		val charType = argumentTypes[0]
		if (charType.equals(CHARACTER())) return characterCodePoints
		val characters = charType.instances
		val charactersIterator = characters.iterator()
		val codePoints = generateSetFrom(characters.setSize) {
			fromInt(charactersIterator.next().codePoint)
		}
		assert(!charactersIterator.hasNext())
		return enumerationWith(codePoints)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				CHARACTER()),
			characterCodePoints)

	override val canDestroyArguments get() = false
}
