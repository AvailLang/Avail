/*
 * L2_CODEPOINT_TO_CHARACTER.kt
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

package avail.interpreter.levelTwo.operation

import avail.descriptor.character.A_Character
import avail.descriptor.character.CharacterDescriptor
import avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import avail.descriptor.character.CharacterDescriptor.Companion.staticFromByteCodePointMethod
import avail.descriptor.character.CharacterDescriptor.Companion.staticFromCodePointMethod
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.characterRestriction
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

/**
 * Given an [Int] representing a codepoint, produce the corresponding
 * [A_Character].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_CODEPOINT_TO_CHARACTER(
	var source: L2ReadIntOperand,
	var destination: L2WriteBoxedOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		A_Character
		renderPreamble()
		append(' ')
		append(destination.registerString())
		append(" ← char(")
		append(source.registerString())
		append(')')
	}

	override fun instructionWasAdded(manifest: L2ValueManifest)
	{
		destination.restrict { characterRestriction }
		val codepointRestriction = source.restriction()
		val codepoints = codepointRestriction.type
		if (codepoints.isEnumeration)
		{
			// A particular subset of characters is possible.
			val charSet =
				codepoints.instances.map { fromCodePoint(it.extractInt) }
			destination.restrict {
				boxedRestrictionForType(
					enumerationWith(setFromCollection(charSet)))
			}
		}
		else if (codepointRestriction.excludedValues.isNotEmpty()
			|| codepointRestriction.excludedTypes.isNotEmpty())
		{
			val inclusionCount = codepoints.upperBound.extractInt -
				codepoints.lowerBound.extractInt + 1
			val exclusionCount = codepointRestriction.excludedValues.size +
				codepointRestriction.excludedTypes.sumOf {
					it.upperBound.extractInt - it.lowerBound.extractInt + 1
				}
			val excludedPairs = (
				codepointRestriction.excludedValues.map { it to it } +
					codepointRestriction.excludedTypes.map {
						it.lowerBound to it.upperBound
					})
			val excludedRanges = excludedPairs
				.map { (low, high) ->
					(low as AvailObject).extractInt ..
						(high as AvailObject).extractInt
				}
				.sortedBy(IntRange::first)
			if (inclusionCount - exclusionCount < 100)
			{
				// Create a positive restriction including < 100 characters.
				val inclusionRanges = mutableSetOf<IntRange>()
				var limit = codepoints.upperBound.extractInt
				val exclusionIterator = excludedRanges.iterator()
				var i =  codepoints.lowerBound.extractInt
				while (i <= limit)
				{
					if (exclusionIterator.hasNext())
					{
						val exclude = exclusionIterator.next()
						inclusionRanges.add(i..exclude.first - 1)
						i = exclude.last + 1
					}
					else
					{
						inclusionRanges.add(i..limit)
						i = limit + 1
					}
				}
				val characters = inclusionRanges
					.filterNot(IntRange::isEmpty)
					.flatMap(IntRange::toList)
					.map(CharacterDescriptor::fromCodePoint)
				destination.restrict {
					boxedRestrictionForType(
						enumerationWith(setFromCollection(characters)))
				}
			}
			else if (exclusionCount < 100)
			{
				// Create a negative restriction excluding <100 characters.
				val excludedTypes = excludedRanges
					.map { range -> inclusive(range.first, range.last) }
				destination.restrict {
					characterRestriction.minusTypes(excludedTypes)
				}
			}
		}
		super.instructionWasAdded(manifest)
	}

	override fun traceCandidateSplitConditions(
		writeOperand: L2WriteOperand<*>,
		restriction: TypeRestriction,
		tracer: L2SplitCondition.RestrictionTracer)
	{
		tracer.continueTracing(source.register(), restriction.forUnboxedInt())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: destination = CharacterDescriptor.fromCodePoint(source);
		translator.load(method, source)
		when (source.type().isSubtypeOf(u8))
		{
			true -> staticFromByteCodePointMethod.generateCall(method)
			else -> staticFromCodePointMethod.generateCall(method)
		}
		translator.store(method, destination.register())
	}
}
