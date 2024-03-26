/*
 * L2_PHI_PSEUDO_OPERATION.kt
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

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2OldInstruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_FLOAT_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_INT_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_BOXED
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_FLOAT
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_INT
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2Generator
import avail.optimizer.L2Synonym
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticValue
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.cast
import avail.utility.mapToSet
import org.objectweb.asm.MethodVisitor

/**
 * The `L2_PHI_PSEUDO_OPERATION` occurs at the start of a [L2BasicBlock].  It's
 * a convenient fiction that allows an [L2ControlFlowGraph] to be in Static
 * Single Assignment form (SSA), where each [L2Register] has exactly one
 * instruction that writes to it.
 *
 * The vector of source registers are in the same order as the corresponding
 * predecessors of the containing [L2BasicBlock].  The runtime effect
 * would be to select from that vector, based on the predecessor from which
 * control arrives, and move that register's value to the destination register.
 * However, that's a fiction, and the phi operation is instead removed during
 * the transition of the control flow graph out of SSA, being replaced by move
 * instructions along each incoming edge.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property K
 *   The [RegisterKind] that says what kind of data is being processed.
 *
 * @property moveOperation
 *   The [L2_MOVE] operation to substitute for this instruction on incoming
 *   split edges.
 *
 * @constructor
 *   Construct an `L2_PHI_PSEUDO_OPERATION`.
 *
 * @param readOperandType
 *   The [L2NamedOperandType] that describes the read source of this move.
 * @param writeOperandType
 *   The [L2NamedOperandType] that describes the write destination of this move.
 */
class L2_PHI_PSEUDO_OPERATION<K: RegisterKind<K>>
private constructor(
	val moveOperation: L2_MOVE<K>,
	readOperandType: L2NamedOperandType,
	writeOperandType: L2NamedOperandType
) : L2Operation(readOperandType, writeOperandType)
{
	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		// The reads in the input vector are from the positionally corresponding
		// incoming edges, which carry the manifests that should be used to
		// look up the best source semantic values.
		val sources: L2ReadVectorOperand<L2ReadOperand<K>> =
			instruction.operand(0)
		val destination: L2WriteOperand<K> = instruction.operand(1)
		sources.instructionWasAddedForPhi(
			instruction.basicBlock().predecessorEdges())
		destination.instructionWasAdded(manifest)
	}

	override fun shouldEmit(
		instruction: L2Instruction): Boolean
	{
		// Phi instructions are converted to moves along predecessor edges.
		return false
	}

	/**
	 * One of this phi function's predecessors has been removed because it's
	 * dead code.  Clean up its vector of inputs by removing the specified
	 * index.
	 *
	 * @param instruction
	 *   The [L2Instruction] whose operation has this type.
	 * @param inputIndex
	 *   The index to remove.
	 * @return
	 *   A replacement [L2Instruction], whose operation may be either another
	 *   `L2_PHI_PSEUDO_OPERATION` or an [L2_MOVE].
	 */
	fun phiWithoutIndex(
		instruction: L2Instruction,
		inputIndex: Int
	): L2Instruction
	{
		val oldVector: L2ReadVectorOperand<L2ReadOperand<K>> =
			instruction.operand(0)
		val destinationReg: L2WriteOperand<K> = instruction.operand(1)
		val newSources = oldVector.elements.toMutableList()
		newSources.removeAt(inputIndex)
		val onlyOneRegister = newSources.size == 1
		return when
		{
			onlyOneRegister ->
			{
				// Replace the phi function with a simple move.
				L2OldInstruction(
					moveOperation,
					newSources[0],
					destinationReg)
			}
			else ->
			{
				L2OldInstruction(
					this,
					oldVector.clone(newSources),
					destinationReg)
			}
		}.cloneFor(instruction.basicBlock())
	}

	/**
	 * Replace this phi by providing a lambda that alters a copy of the list of
	 * [L2ReadOperand]s that it's passed.  The predecessor edges are expected to
	 * correspond with the inputs.  Do not attempt to normalize the phi to a
	 * move.
	 *
	 * @param instruction
	 *   The phi instruction to augment.
	 * @param updater
	 *   What to do to a copied mutable [List] of read operands that starts out
	 *   having all of the vector operand's elements.
	 */
	private fun updateVectorOperand(
		instruction: L2Instruction,
		updater: (MutableList<L2ReadOperand<K>>) -> Unit)
	{
		val block = instruction.basicBlock()
		val instructionIndex = block.instructions().indexOf(instruction)
		val vectorOperand: L2ReadVectorOperand<L2ReadOperand<K>> =
			instruction.operand(0)
		val writeOperand: L2WriteOperand<K> = instruction.operand(1)
		instruction.justRemoved()
		val passedCopy = vectorOperand.elements.toMutableList()
		updater(passedCopy)
		val finalCopy: List<L2ReadOperand<K>> = passedCopy.toList()
		val replacementInstruction =
			L2OldInstruction(
				this, vectorOperand.clone(finalCopy), writeOperand
			).cloneFor(block)
		block.instructions()[instructionIndex] = replacementInstruction
		replacementInstruction.justInserted()
	}

	/**
	 * Examine the instruction and answer the predecessor [L2BasicBlock]s
	 * that supply a value from the specified register.
	 *
	 * @param instruction
	 *   The phi-instruction to examine.
	 * @param usedRegister
	 *   The [L2Register] whose use we're trying to trace back to its
	 *   definition.
	 * @return
	 *   A [List] of predecessor blocks that supplied the usedRegister as an
	 *   input to this phi operation.
	 */
	fun predecessorBlocksForUseOf(
		instruction: L2Instruction,
		usedRegister: L2Register<*>): List<L2BasicBlock>
	{
		val sources: L2ReadVectorOperand<L2ReadOperand<K>> =
			instruction.operand(0)
		assert(
			sources.elements.size
				== instruction.basicBlock().predecessorEdges().size)
		val list = mutableListOf<L2BasicBlock>()
		instruction.basicBlock().predecessorEdges().forEachIndexed { i, edge ->
			if (sources.elements[i].register() === usedRegister)
			{
				list.add(edge.sourceBlock())
			}
		}
		return list
	}

	/**
	 * Answer the [L2WriteOperand] from this phi function.  This should only be
	 * used when generating phi moves (which takes the [L2ControlFlowGraph] out
	 * of Static Single Assignment form).
	 *
	 * @param instruction
	 *   The instruction to examine. It must be a phi operation.
	 * @return
	 *   The instruction's destination [L2WriteOperand].
	 */
	fun phiDestinationRegisterWrite(
		instruction: L2Instruction
	): L2WriteOperand<K> = instruction.operand(1)

	/**
	 * Answer the [List] of [L2ReadOperand]s for this phi function.
	 * The order is correlated to the instruction's blocks predecessorEdges.
	 *
	 * @param instruction
	 *   The phi instruction.
	 * @return
	 *   The instruction's list of sources.
	 */
	fun phiSourceRegisterReads(
		instruction: L2Instruction
	): List<L2ReadOperand<*>> =
		instruction.operand<L2ReadVectorOperand<*>>(0).elements

	/**
	 * Update an `L2_PHI_PSEUDO_OPERATION` instruction that's in a loop head
	 * basic block.
	 *
	 * @param predecessorManifest
	 *   The [L2ValueManifest] in some predecessor edge.
	 * @param instruction
	 *   The phi instruction itself.
	 */
	fun updateLoopHeadPhi(
		predecessorManifest: L2ValueManifest,
		instruction: L2Instruction)
	{
		val semanticValue: L2SemanticValue<K> =
			phiSourceRegisterReads(instruction)[0].semanticValue().cast()
		val kind = moveOperation.kind
		val readOperand = kind.readOperand(
			semanticValue,
			predecessorManifest.restrictionFor(semanticValue),
			predecessorManifest.getDefinition(semanticValue))
		updateVectorOperand(instruction) { it.add(readOperand) }
	}

	/**
	 * Write the given [L2Operation]'s equivalent effect through the given
	 * [L2Regenerator], with the given already-transformed [L2Operand]s.
	 *
	 * Don't reproduce phi instructions like this one, since suitable ones will
	 * already have been automatically generated by this regenerator. However,
	 * make sure synonyms are updated to conform to the old phi, by attempting
	 * to generate a move.
	 *
	 * @param transformedOperands
	 *   The operands of the instruction, already transformed for the
	 *   regenerator.
	 * @param regenerator
	 *   The [L2Regenerator] through which to write the instruction's equivalent
	 *   effect.
	 */
	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		//val readVector: RV = transformedOperands[0].cast()
		val write: L2WriteOperand<K> = transformedOperands[1].cast()

		val manifest = regenerator.currentManifest
		val defined = mutableListOf<L2SemanticValue<K>>()
		val undefined = mutableListOf<L2SemanticValue<K>>()
		write.semanticValues().forEach {
			when
			{
				manifest.hasSemanticValue(it) -> defined.add(it)
				else -> undefined.add(it)
			}
		}
		assert(defined.isNotEmpty())
		val source = defined[0]
		for (eachTarget in undefined)
		{
			regenerator.moveRegister(
				moveOperation, source, setOf(eachTarget))
		}
	}


	override fun toString(): String =
		super.toString() + "(" + moveOperation.kind.kindName + ")"

	override fun appendToWithWarnings(
		instruction: L2OldInstruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		val vector = instruction.operand<L2Operand>(0)
		val target = instruction.operand<L2Operand>(1)
		builder.append("ϕ ")
		builder.append(target)
		builder.append(" ← ")
		builder.append(vector)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		throw UnsupportedOperationException(
			"This instruction should be factored out before JVM translation")
	}

	/**
	 * Generate an [L2_PHI_PSEUDO_OPERATION] and any additional moves to ensure
	 * the given set of related [L2SemanticValue]s are populated with values
	 * from the given sources.
	 *
	 * @param generator
	 *   The [L2Generator] on which to write instructions.
	 * @param relatedSemanticValues
	 *   The [List] of [L2SemanticValue]s that should constitute a synonym in
	 *   the current manifest, due to their being mutually connected to a
	 *   synonym in each predecessor manifest.  The synonyms may differ in the
	 *   predecessor manifests, but within each manifest there must be a synonym
	 *   for that manifest that contains all of these semantic values.
	 * @param forcePhiCreation
	 *   Whether to force creation of a phi instruction, even if all incoming
	 *   sources of the value are the same.
	 * @param typeRestriction
	 *   The [TypeRestriction] to bound the synonym.
	 * @param sourceManifests
	 *   A [List] of [L2ValueManifest]s, one for each incoming edge.
	 */
	fun generatePhi(
		generator: L2Generator,
		relatedSemanticValues: List<L2SemanticValue<K>>,
		forcePhiCreation: Boolean,
		typeRestriction: TypeRestriction,
		sourceManifests: List<L2ValueManifest>)
	{
		// Check if there's a register common to all incoming edges, whose
		// definitions each cover the relatedSemanticValues.  If so, use that
		// register directly.  Otherwise generate a phi move into a temp, then
		// move it to another register representing the relatedSemanticValues
		// for the current register kind.
		val relatedSemanticValuesSet = relatedSemanticValues.toSet()
		val manifest = generator.currentManifest
		val pickSemanticValue = relatedSemanticValues[0]
		val completeRegistersBySource = sourceManifests.map { m ->
			m.getDefinitions(pickSemanticValue).filter {
				r -> r.definitions().all {
					w -> w.semanticValues().containsAll(relatedSemanticValues)
				}
			}
		}
		val completeRegisters = completeRegistersBySource[0].toMutableList()
		completeRegistersBySource.forEach(completeRegisters::retainAll)
		val restriction = sourceManifests
			.map { it.restrictionFor(pickSemanticValue) }
			.reduce(TypeRestriction::union)
			.intersection(typeRestriction)
		// We've already done all the synonym extensions for moves as they were
		// recorded as postponed instructions.  So be delicate when extending
		// synonyms in general.  Adding actual move instructions will normally
		// turn into an extension of the latest write if it's in the same block,
		// but we couldn't do that for postponed instructions, because we don't
		// know what block(s) they'll end up in.
		val (inSynonym, notInSynonym) =
			relatedSemanticValuesSet.partition(manifest::hasSemanticValue)
		val existingSynonyms =
			inSynonym.mapToSet(transform = manifest::semanticValueToSynonym)
		if (existingSynonyms.isNotEmpty())
		{
			// There's at least one synonym.  Merge them, then add any new
			// semantic values.
			val pick = existingSynonyms.first().pickSemanticValue()
			existingSynonyms.forEach {
				manifest.mergeExistingSemanticValues(
					pick, it.pickSemanticValue())
			}
			notInSynonym.forEach {
				manifest.extendSynonym(
					manifest.semanticValueToSynonym(pick), it)
			}
		}
		else
		{
			// None of the semantic values is in a synonym yet, so create it in
			// one step.
			manifest.introduceSynonym(
				L2Synonym(relatedSemanticValues), restriction)
		}
		when
		{
			completeRegisters.isNotEmpty() && !forcePhiCreation ->
			{
				// At least one register covers the complete set of semantic
				// values in each of the predecessors.  Expose one of the
				// existing registers directly.  The updateConstraint() works
				// whether the synonym exists yet or not.
				manifest.updateDefinitions(pickSemanticValue) {
					// No need to keep multiple registers around for the same
					// purpose.
					append(completeRegisters[0])
				}
			}
			else ->
			{
				// None of the registers covers all of the required semantic
				// values from all of the incoming edges.  Use a phi function to
				// get it into a new register for the required synonym.
				val sources = sourceManifests.map {
					moveOperation.createRead(pickSemanticValue, it)
				}
				generator.addInstruction(
					this,
					moveOperation.createVector(sources),
					moveOperation.createWrite(
						generator::nextUnique,
						relatedSemanticValuesSet,
						typeRestriction))
			}
		}
		manifest.check()
	}

	/**
	 * We don't need to do anything for phi instructions, since the generator
	 * framework itself handles it.  This includes forcing creation of phi
	 * instructions when an [L2SemanticValue] is present in every incoming edge.
	 *
	 * @param instruction
	 *   The [L2Instruction] being replaced.
	 * @param regenerator
	 *   An [L2Regenerator] that has been configured for writing arbitrary
	 *   replacement code for this instruction.
	 */
	override fun generateReplacement(
		instruction: L2Instruction,
		regenerator: L2Regenerator)
	{
		// Don't generate a phi here, because startBlock() handled it.
	}

	companion object
	{
		/**
		 * Initialize the instance used for merging boxed values.
		 */
		@JvmField
		val boxed = L2_PHI_PSEUDO_OPERATION(
			L2_MOVE.boxed,
			READ_BOXED_VECTOR.named("potential boxed sources"),
			WRITE_BOXED.named("boxed destination"))

		/**
		 * Initialize the instance used for merging boxed values.
		 */
		@JvmField
		val unboxedInt = L2_PHI_PSEUDO_OPERATION(
			L2_MOVE.unboxedInt,
			READ_INT_VECTOR.named("potential int sources"),
			WRITE_INT.named("int destination"))

		/**
		 * Initialize the instance used for merging boxed values.
		 */
		@JvmField
		val unboxedFloat = L2_PHI_PSEUDO_OPERATION(
			L2_MOVE.unboxedFloat,
			READ_FLOAT_VECTOR.named("potential float sources"),
			WRITE_FLOAT.named("float destination"))

		/**
		 * The collection of phi operations, one per [RegisterKind].
		 */
		val allPhiOperations = listOf(boxed, unboxedInt, unboxedFloat)
	}
}
