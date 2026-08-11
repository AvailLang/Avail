/*
 * L2_UNBOX_INT.kt
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

package avail.interpreter.levelTwo.operation.numbers

import avail.descriptor.representation.A_Number
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.unboxedIntConditions
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticUnboxedInt

/**
 * Unbox an [Int] from an [AvailObject].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_UNBOX_INT(
	var source: L2ReadBoxedOperand,
	var destination: L2WriteIntOperand
) : L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(destination.registerString())
		append(" ← ")
		append(source.registerString())
	}

	override fun instructionWasAdded(manifest: L2ValueManifest)
	{
		destination.restrict { source.restriction().forUnboxedInt() }
		super.instructionWasAdded(manifest)
		// The [translateToJVM] below emits an unguarded extractInt, so the
		// source must already have been proven to be an i32, normally by an
		// L2_JUMP_IF_KIND_OF_OBJECT on the edge leading here.  Check this only
		// after the super call, which is what re-restricts the read operands
		// from the manifest; the restriction captured when the operand was
		// built can predate the very type test that establishes the guarantee.
		assert(source.restriction().containedByType(i32))
		{
			"L2_UNBOX_INT source was not proven to be an i32: " +
				source.restriction()
		}
	}

	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun JVMTranslator.translateToJVM()
	{
		// :: destination = source.extractInt();
		load(source)
		generateCall(A_Number.extractIntStaticMethod)
		store(destination.register())
	}

	override fun interestingConditions(): List<L2SplitCondition?> = buildList {
		addAll(unboxedIntConditions(listOf(source.register())))
		addAll(unboxedIntConditions(listOf(destination.register())))
	}

	override fun traceCandidateSplitConditions(
		writeOperand: L2WriteOperand<*>,
		restriction: TypeRestriction,
		tracer: L2SplitCondition.RestrictionTracer)
	{
		tracer.continueTracing(source.register(), restriction.forBoxed())
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// Synonyms of ints are tricky, so check if there's an int version of
		// a synonym of the source available.
		val otherUnboxeds = currentManifest
			.semanticValueToSynonym(source.semanticValue())
			.semanticValues()
			.map(::L2SemanticUnboxedInt)
		val existingUnboxed = otherUnboxeds
			.filter(currentManifest::hasSemanticValue)
			.filter { currentManifest.getDefinitions(it).isNotEmpty() }
		if (existingUnboxed.isNotEmpty())
		{
			// There's already an int semantic value with the needed value.  Do
			// a move into all the remaining int semantic values.
			val existing = existingUnboxed.first()
			val unpopulated = (otherUnboxeds + destination.semanticValues())
				.filterNot(currentManifest::hasSemanticValue)
			if (unpopulated.isNotEmpty())
			{
				moveIntRegister(existing, unpopulated)
			}
		}
		else
		{
			// We have to unbox it.
			+L2_UNBOX_INT(
				source,
				intWrite(
					(destination.semanticValues() + otherUnboxeds).toSet(),
					currentManifest.restrictionFor(source).forUnboxedInt()))
		}
	}
}
