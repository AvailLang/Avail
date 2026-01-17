/*
 * L2_JUMP_IF_UNBOX_INT.kt
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

import avail.descriptor.numbers.A_Number
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.A_Type.Companion.instanceTag
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operation.L2ConditionalJump
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.typeRestrictionConditions
import avail.optimizer.L2SplitCondition.Companion.unboxedIntConditions
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import avail.optimizer.values.L2SemanticExtractedTag
import avail.optimizer.values.L2SemanticUnboxedInt
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Jump to `"if unboxed"` if an [Int] was unboxed from an [AvailObject],
 * otherwise jump to `"if not unboxed"`.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_JUMP_IF_UNBOX_INT
constructor(
	var source: L2ReadBoxedOperand,
	@On(SUCCESS) var destination: L2WriteIntOperand,
	@On(FAILURE) var ifNotUnboxed: L2PcOperand,
	@On(SUCCESS) var ifUnboxed: L2PcOperand
): L2ConditionalJump()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(destination.registerString())
		append(" ←? ")
		append(source.registerString())
		renderOperandsExcludingFields(
			desiredOperandTypes, ::source, ::destination)
	}

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		destination.restrict { source.restriction().forUnboxedInt() }
		super.instructionWasAdded(manifest)
		ifUnboxed.manifest().intersectType(source.semanticValue(), i32)
		ifNotUnboxed.manifest().subtractType(source.semanticValue(), i32)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: if (!source.isInt()) goto ifNotUnboxed;
		translator.load(method, source)
		A_Number.isIntMethod.generateCall(method)
		translator.jumpIf(method, Opcodes.IFEQ, ifNotUnboxed)
		// :: else {
		// ::    destination = source.extractInt();
		// ::    goto ifUnboxed;
		// :: }
		translator.load(method, source)
		A_Number.extractIntStaticMethod.generateCall(method)
		translator.store(method, destination.register())
		translator.jumpOrFallThrough(method, ifUnboxed)
	}

	override fun interestingConditions(): List<L2SplitCondition?> = buildList {
		if (!ifUnboxed.targetBlock().isCold)
		{
			// The ifUnboxed path is warm, so split to preserve the value being
			// in an unboxed int register.
			addAll(unboxedIntConditions(listOf(source.register())))
			addAll(unboxedIntConditions(listOf(destination.register())))
		}
		if (!ifNotUnboxed.targetBlock().isCold)
		{
			// The ifNotUnboxed path is warm, so split to preserve the value
			// falling entirely outside the range of an int.
			addAll(
				typeRestrictionConditions(
					listOf(source.register()),
					boxedRestrictionForType(ANY()).minusType(i32)))
		}
	}

	override fun traceCandidateSplitConditions(
		writeOperand: L2WriteOperand<*>,
		restriction: TypeRestriction,
		tracer: L2SplitCondition.RestrictionTracer)
	{
		tracer.continueTracing(source.register(), restriction.forBoxed())
	}

	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun transformedByRegenerator(
		regenerator: L2Regenerator
	): L2Instruction
	{
		// If the i32 output is unused, just jump to the non-i32 target block.
		if (destination.register().uses().isEmpty())
		{
			return L2_JUMP(ifNotUnboxed).transformedByRegenerator(regenerator)
		}
		return super.transformedByRegenerator(regenerator)
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		if (replaceWithJumpIfPossible(this)) return
		// Regeneration can strengthen this type via code splitting, or even
		// obviate the need to re-extract into an int register if it's already
		// in one along this split path.
		val sourceRestriction =
			currentManifest.restrictionFor(source.semanticValue())
		if (sourceRestriction.containedByType(i32))
		{
			// It has been strengthened to definitely be an int.  Let the
			// L2_UNBOX_INT class handle any special cases.
			L2_UNBOX_INT(source, destination).run {
				emitTransformedInstruction()
			}
			jumpTo(ifUnboxed.targetBlock())
			return
		}
		val sourceSemanticValue = source.semanticValue()
		val semanticValues = destination.semanticValues()
		semanticValues.firstNotNullOfOrNull {
			currentManifest.equivalentSemanticValue(it)
		}?.let { equivalent ->
			// There's an equivalent semantic value already populated, so do an
			// int move to ensure all of the destination semantic values get
			// populated.
			moveRegister(equivalent, semanticValues)
			jumpTo(ifUnboxed.targetBlock())
			return
		}
		val sourceInt = currentManifest
			.semanticValueToSynonym(source.semanticValue())
			.semanticValues()
			.map(::L2SemanticUnboxedInt)
			.firstOrNull(currentManifest::hasSemanticValue)
			?: source.semanticValue().unboxedInt
		// If the value's tag has been extracted already, strengthen it.
		val tagSemanticValue =
			currentManifest.equivalentPopulatedSemanticValue(
				L2SemanticExtractedTag(sourceSemanticValue).unboxedInt)
		tagSemanticValue?.let {
			// Narrow the tag's range if possible.
			currentManifest.updateRestriction(it) {
				intersectionWithType(
					sourceRestriction.type.instanceTag.tagRangeType)
			}
		}
		when
		{
			currentManifest.hasSemanticValue(sourceInt) ->
			{
				// It's already unboxed.  However, ensure all destination
				// semantic values have been written.
				destination.semanticValues().forEach { dest ->
					if (!currentManifest.hasSemanticValue(dest))
						moveIntRegister(sourceInt, setOf(dest))
				}
				tagSemanticValue?.let {
					currentManifest.updateRestriction(it) {
						intersectionWithType(
							currentManifest.restrictionFor(sourceInt).type.instanceTag
								.tagRangeType)
					}
				}
				jumpTo(ifUnboxed.targetBlock())
			}
			sourceRestriction.containedByType(i32) ->
			{
				// It's not already unboxed, but it's an int32.
				+L2_UNBOX_INT(source, destination)
				jumpTo(ifUnboxed.targetBlock())
			}
			!sourceRestriction.intersectsType(i32) ->
			{
				// It can't be an int32.
				jumpTo(ifNotUnboxed.targetBlock())
			}
			else ->
			{
				// It's still contingent on the value.
				+this@L2_JUMP_IF_UNBOX_INT
			}
		}
	}
}
