/*
 * L2_PHI.kt
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

import avail.exceptions.unsupported
import avail.interpreter.levelTwo.InstructionLayout
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast

/**
 * The `L2_PHI` occurs at the start of a [L2BasicBlock].  It's
 * a convenient fiction that allows an [L2ControlFlowGraph] to be in Static
 * Single Assignment form (SSA), where each [L2Register] has exactly one
 * instruction that writes to it.
 *
 * The vector of source registers are in the same order as the corresponding
 * predecessors of the containing [L2BasicBlock].  The runtime effect would be
 * to select from that vector, based on the predecessor from which control
 * arrives, and move that register's value to the destination register. However,
 * that's a fiction, and the phi operation is instead removed during the
 * transition of the control flow graph out of SSA, being replaced by move
 * instructions (into a common register) along each incoming edge.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property K
 *   The [RegisterKind] that says what kind of data is being processed.
 *
 * @constructor
 *   Construct an `L2_PHI`.
 */
sealed class L2_PHI<K: RegisterKind<K>> : L2Instruction()
{
	/** The [RegisterKind] operated on by this phi instruction. */
	abstract val kind: K

	/**
	 * The sources of this phi move.  This is a member function instead of a
	 * field, to simplify the reflection logic in [InstructionLayout].  The
	 * elements of the vector are in the same order as the incoming edges to the
	 * basic block holding this phi instruction.
	 */
	abstract val sources: L2ReadVectorOperand<L2ReadOperand<K>>

	/**
	 * The destination of this move.  This is a member function instead of a
	 * field, to simplify the reflection logic in [InstructionLayout].
	 */
	abstract val destination: L2WriteOperand<K>

	override fun clone(): L2_PHI<K> = super.clone().cast()

	override fun cloneFor(
		generator: L2GeneratorInterface,
		forceBlock: L2BasicBlock?
	): L2_PHI<K> = super.cloneFor(generator, forceBlock).cast()

	override fun aboutToAdd(generator: L2GeneratorInterface): Boolean
	{
		// The reads are specific to each incoming edge, and the writes will be
		// handled later by instructionWasAdded.
		return true
	}

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		// The reads in the input vector are from the positionally corresponding
		// incoming edges, which carry the manifests that should be used to
		// look up the best source semantic values.
		assert(!manifest.hasEliminatedPhis)
		sources.instructionWasAddedForPhi(basicBlock().predecessorEdges())
		// Try to improve the restriction, in case this has been regenerated by
		// code splitting or other optimizations.
		destination.restrict {
			sources.elements
				.map(L2ReadOperand<K>::restriction)
				.reduce(TypeRestriction::union)
		}
		destination.instructionWasAdded(manifest)
	}

	/** Phi instructions are converted to moves along predecessor edges. */
	override val shouldEmit get() = false

	override fun postponeInstruction(generator: L2GeneratorInterface)
	{
		error("Phi instructions cannot be postponed")
	}

	/**
	 * One of this phi function's predecessors has been removed because it's
	 * dead code.  Clean up its vector of inputs by removing the specified
	 * index.
	 *
	 * @param inputIndex
	 *   The index to remove.
	 * @return
	 *   A replacement [L2Instruction], which will be either another
	 *   [L2_PHI] or an [L2_MOVE] if there is only one actual
	 *   source register (which is also the case when we're down to only one
	 *   incoming edge).
	 */
	fun phiWithoutIndex(
		inputIndex: Int
	): L2Instruction
	{
		val newSources = sources.elements.toMutableList()
		newSources.removeAt(inputIndex)
		val onlyOneRegister = newSources.size == 1 ||
			newSources.map(L2ReadOperand<K>::register).distinct().size == 1
		if (onlyOneRegister)
		{
			// Replace the phi function with a simple move.
			return kind.move(newSources[0], destination)
		}
		val clone = clone()
		clone.layout.updateOperands(clone) { operand ->
			if (operand == sources) kind.createVector(newSources)
			else destination
		}
		return clone.clone()
	}

	/**
	 * Replace this phi by providing a lambda that alters a copy of the list of
	 * [L2ReadOperand]s that it's passed.  The predecessor edges are expected to
	 * correspond with the inputs.  Do not attempt to normalize the phi to a
	 * move.
	 *
	 * @param updater
	 *   What to do to a copied mutable [List] of read operands that starts out
	 *   having all the vector operand's elements.
	 */
	private fun updateVectorOperand(
		updater: (MutableList<L2ReadOperand<K>>) -> Unit)
	{
		val passedCopy = sources.elements.toMutableList()
		updater(passedCopy)
		val newElements: List<L2ReadOperand<K>> = passedCopy.toList()
		val clone = clone()
		clone.destination.forceRegister(destination.register())
		val cloneSources = clone.sources
		clone.layout.updateOperands(clone) { operand ->
			if (operand == cloneSources) cloneSources.clone(newElements)
			else operand
		}
		// Rebuild the sourceRegisters and destinationRegisters lists.
		clone.sourceRegisters.clear()
		clone.destinationRegisters.clear()
		clone.operands.forEach { operand ->
			operand.addSourceRegistersTo(clone.sourceRegisters)
			operand.addDestinationRegistersTo(clone.destinationRegisters)
		}
		replaceWith(clone)
	}

	/**
	 * Update an [L2_PHI] instruction that's in a loop head basic block.
	 *
	 * @param predecessorManifest
	 *   The [L2ValueManifest] in some predecessor edge.
	 */
	fun updateLoopHeadPhi(
		predecessorManifest: L2ValueManifest)
	{
		val semanticValue = sources.elements[0].semanticValue()
		val readOperand = kind.readOperand(
			semanticValue,
			predecessorManifest.restrictionFor(semanticValue),
			predecessorManifest.getDefinition(semanticValue))
		updateVectorOperand { it.add(readOperand) }
	}

	/**
	 * Phi instructions are regenerated as needed, so the postponement pass can
	 * ignore them.
	 */
	override fun L2Regenerator.regenerateForPostponement() = unsupported

	/**
	 * Write the given [L2Instruction]'s equivalent effect through the given
	 * [L2GeneratorInterface], with the given already-transformed [L2Operand]s.
	 *
	 * Don't reproduce phi instructions like this one, since suitable ones will
	 * already have been automatically generated by this generator. However,
	 * make sure synonyms are updated to conform to the old phi, by attempting
	 * to generate a move.
	 *
	 * @receiver
	 *   The [L2GeneratorInterface] through which to write the instruction's
	 *   equivalent effect.
	 */
	override fun L2GeneratorInterface.analyzeAndOptionallyRewrite(
	): L2Instruction?
	{
		val defined = mutableListOf<L2SemanticValue<K>>()
		val undefined = mutableListOf<L2SemanticValue<K>>()
		destination.semanticValues().forEach {
			when
			{
				currentManifest.hasSemanticValue(it) -> defined.add(it)
				else -> undefined.add(it)
			}
		}
		assert(defined.isNotEmpty())
		val source = defined[0]
		for (eachTarget in undefined)
		{
			moveRegister(source, setOf(eachTarget))
		}
		return null
	}

	/**
	 * Phi instructions cannot be replaced by moves in this way.  However,
	 * pretend we wrote something, since it will be automatically generated as
	 * needed.
	 */
	override fun L2GeneratorInterface.populateFromSourceInstructionIfPossible(
	): Boolean = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(" ")
		append(destination)
		append(" ← ")
		append(sources)
	}

	override val name: String get() = "ϕ"

	override fun JVMTranslator.translateToJVM()
	{
		throw UnsupportedOperationException(
			"This instruction should be factored out before JVM translation")
	}

	/**
	 * We don't need to do anything for phi instructions, since the generator
	 * framework itself handles it.  This includes forcing creation of phi
	 * instructions when an [L2SemanticValue] is present in every incoming edge.
	 *
	 * @receiver
	 *   An [L2GeneratorInterface] that has been configured for writing
	 *   arbitrary replacement code for this instruction.
	 * @param originalInstruction
	 *   The [L2Instruction] that the receiver was based on.
	 */
	override fun L2Regenerator.generateReplacement(
		originalInstruction: L2Instruction)
	{
		// Don't generate a phi here, because startBlock() handled it.
	}

	/**
	 * Answer a suitable [L2_MOVE] that writes (breaking non-SSA) into the same
	 * destination register that the receiver was writing.  It should draw its
	 * source value from the [sources] vector at the specified zero-based
	 * [index].
	 */
	fun replacementMoveForIndex(
		index: Int,
	): L2_MOVE<K> =
		kind.move(sources.elements[index], destination.clone().cast())
			.clone()
			.cast()

	override fun traceCandidateSplitConditions(
		writeOperand: L2WriteOperand<*>,
		restriction: TypeRestriction,
		tracer: L2SplitCondition.RestrictionTracer)
	{
		sources.elements.forEach { read ->
			tracer.continueTracing(read.register(), restriction)
		}
	}
}

/** A phi instruction for [BOXED_KIND] registers. */
class L2_PHI_BOXED
constructor(
	var privateSources: L2ReadBoxedVectorOperand,
	var privateDestination: L2WriteBoxedOperand
): L2_PHI<BOXED_KIND>()
{
	override val kind get() = BOXED_KIND

	override val sources get() = privateSources

	override val destination get() = privateDestination
}

/** A phi instruction for [INTEGER_KIND] registers. */
class L2_PHI_INT
constructor(
	var privateSources: L2ReadIntVectorOperand,
	var privateDestination: L2WriteIntOperand
): L2_PHI<INTEGER_KIND>()
{
	override val kind get() = INTEGER_KIND

	override val sources get() = privateSources

	override val destination get() = privateDestination
}

/**
 * Initialize the instance used for merging boxed values.
 */
class L2_PHI_FLOAT
constructor(
	var privateSources: L2ReadFloatVectorOperand,
	var privateDestination: L2WriteFloatOperand
): L2_PHI<FLOAT_KIND>()
{
	override val kind get() = FLOAT_KIND

	override val sources get() = privateSources

	override val destination get() = privateDestination
}
