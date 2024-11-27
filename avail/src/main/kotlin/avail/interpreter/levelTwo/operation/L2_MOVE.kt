/*
 * L2_MOVE.kt
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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator
import avail.optimizer.L2Optimizer.GenerationMode
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticBoxedValue
import avail.utility.Strings.truncateTo
import avail.utility.cast
import avail.utility.notNullAnd
import org.objectweb.asm.MethodVisitor

/**
 * Move an [AvailObject] from the source to the destination.  The [L2Generator]
 * creates more moves than are strictly necessary, but various mechanisms
 * cooperate to remove redundant inter-register moves.
 *
 * The object being moved is not made immutable by this operation, as that is
 * the responsibility of the [L2_MAKE_IMMUTABLE] operation, injected at
 * necessary points during very late analysis.
 *
 * @param K
 *   The [RegisterKind] of [L2Register] to be moved.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property kind
 *   The kind of data moved by this operation.
 * @constructor
 * Construct an `L2_MOVE` operation.
 *
 * @param kind
 *   The [RegisterKind] serviced by this operation.
 */
sealed class L2_MOVE<K: RegisterKind<K>>
protected constructor(
	val kind: K,
) : L2Instruction()
{
	/**
	 * The source of this move.  Subclasses further strengthen this property.
	 */
	abstract val source: L2ReadOperand<K>

	/**
	 * The destination of this move.  Subclasses further strengthen this
	 * property.
	 */
	abstract val destination: L2WriteOperand<K>

	override fun cloneFor(block: L2BasicBlock): L2_MOVE<K> =
		super.cloneFor(block).cast()

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		// Ensure the new write ends up in the same synonym as the source.
		source.instructionWasAdded(manifest)
		destination.instructionWasAddedForMove(source, manifest)
		//TODO Remove check
		if (manifest.mode == GenerationMode.BySemanticValue &&
			source.semanticValue() in destination.semanticValues())
		{
			println("MOVE to itself")
		}
	}

	/**
	 * Omit the move if the source and destination registers have the same
	 * color (finalIndex).
	 */
	override val shouldEmit: Boolean get() =
		source.finalIndex() != destination.finalIndex()

	override val producesAnyJvmCode: Boolean
		get() = source.finalIndex() == -1
			|| source.finalIndex() != destination.finalIndex()

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		if (destination.restriction().constantOrNull.notNullAnd { isNil })
		{
			// Assume propagation of nil into a new semantic value will be
			// both successful and uninteresting.
			val tempDest = destination.run {
				buildString { appendWithWarningsTo(0) { } }
			}
			append(tempDest.truncateTo(30))
			append(" ← ")
			val tempSource = source.run {
				buildString { appendWithWarningsTo(0) { } }
			}
			append(tempSource.truncateTo(20))
		}
		else
		{
			destination.run { appendWithWarningsTo(0, warningStyleChange) }
			append(" ← ")
			source.run { appendWithWarningsTo(0, warningStyleChange) }
		}
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
		val manifest = regenerator.currentManifest
		val restriction = manifest.restrictionFor(source.semanticValue())
		val newDestination = kind.createWrite(
			regenerator::nextUnique,
			destination.semanticValues(),
			restriction,
			destination.register())
		val clone: L2_MOVE<K> = clone().cast()
		clone.layout.updateOperands(this) { operand ->
			if (operand == source) source
			else newDestination
		}
		if (regenerator.mode == GenerationMode.BySemanticValue)
		{
			// When regenerating the graph in such a way that instructions are
			// coupled by semantic values, we can look for a write to the source
			// register within the current block, and if present we can simply
			// augment the write to include one more semantic value.
			val definingWrite = clone.source.definition()
			val definingInstruction = definingWrite.instruction
			if (definingInstruction.basicBlock() == regenerator.currentBlock()
				&& definingInstruction !is L2_PHI<*>)
			{
				// It was defined in the current block.  Augment the write.
				// Note that phis don't count, since regeneration ignores them
				// in BySemanticValue mode, regenerating them afresh.
				newDestination.semanticValues().forEach { newSemanticValue ->
					if (!manifest.hasSemanticValue(newSemanticValue))
					{
						definingWrite.retroactivelyIncludeSemanticValue(
							newSemanticValue)
						manifest.extendSynonym(
							manifest.semanticValueToSynonym(
								clone.source.semanticValue()),
							newSemanticValue)
					}
				}
				return
			}
		}
		regenerator.addInstruction(clone)
	}

	override fun sourceOfMoveToRegister(
		destinationRegister: L2Register<*>
	): L2Register<*>?
	{
		assert(destinationRegister == destination.register())
		return source.register()
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		assert(source.register() != destination.register()) {
			"vacuous move should have been skipped by shouldEmit."
		}
		// :: destination = source;
		translator.load(method, source.register())
		translator.store(method, destination.register())
	}
}

class L2_MOVE_BOXED
constructor(
	var moveSource: L2ReadBoxedOperand,
	var moveDestination: L2WriteBoxedOperand
): L2_MOVE<BOXED_KIND>(BOXED_KIND)
{
	override val source: L2ReadBoxedOperand get() = moveSource

	override val destination: L2WriteBoxedOperand get() = moveDestination

	override val constantCode: A_RawFunction?
		get() = source.definitionSkippingMoves().constantCode

	override fun extractTupleElement(
		tupleRead: L2ReadBoxedOperand,
		index: Int,
		destinationSemanticValues: Set<L2SemanticBoxedValue>,
		generator: L2Generator
	): Unit = generator.extractTupleElement(
		source, index, destinationSemanticValues)
}

class L2_MOVE_INT
constructor(
	var moveSource: L2ReadIntOperand,
	var moveDestination: L2WriteIntOperand
): L2_MOVE<INTEGER_KIND>(INTEGER_KIND)
{
	override val source: L2ReadIntOperand get() = moveSource

	override val destination: L2WriteIntOperand get() = moveDestination
}

class L2_MOVE_FLOAT
constructor(
	var moveSource: L2ReadFloatOperand,
	var moveDestination: L2WriteFloatOperand
): L2_MOVE<FLOAT_KIND>(FLOAT_KIND)
{
	override val source: L2ReadFloatOperand get() = moveSource

	override val destination: L2WriteFloatOperand get() = moveDestination
}
