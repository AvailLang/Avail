/*
 * DeadCodeAnalyzer.kt
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
package avail.optimizer

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast
import avail.utility.notNullAnd
import java.util.Collections

/**
 * A mechanism for determining which instructions are dead versus live.
 *
 * @property dataCouplingMode
 *   The policy about which kinds of [L2Entity] to consider.
 * @property controlFlowGraph
 *   The [L2ControlFlowGraph] to analyze.
 *
 * @constructor
 * Construct a `DeadCodeAnalyzer`.
 *
 * @param dataCouplingMode
 *   The policy about what kinds of [L2Entity] should be traced.
 * @param controlFlowGraph
 *   The [L2ControlFlowGraph] being analyzed.
 */
internal class DeadCodeAnalyzer constructor(
	private val dataCouplingMode: DataCouplingMode,
	private val controlFlowGraph: L2ControlFlowGraph)
{
	/**
	 * A [Map] from each [L2PcOperand] to the [Set] of
	 * [entities][L2Entity]/[RegisterKind] pairs that might be consumed after
	 * this edge.
	 */
	private val edgeNeeds =
		mutableMapOf<L2PcOperand, MutableSet<L2Entity<*>>>()

	/** The [L2Instruction]s that have been marked as live so far. */
	private val liveInstructions = mutableSetOf<L2Instruction>()

	/**
	 * Calculate which operations are live, either because they have a side
	 * effect, or they produce a value consumed (recursively) by a live
	 * instruction.
	 */
	fun analyzeReads()
	{
		assert(liveInstructions.isEmpty())
		// First seed all edges that have forcedClampedEntities.  In theory, we
		// could do better by determining liveness by iterating, but since the
		// clamped entities are pretty minimal, we're not likely to eliminate a
		// lot of dead code.
		controlFlowGraph.forwardVisit { block ->
			block.predecessorEdges().forEach { edge ->
				edge.forcedClampedEntities?.let { clamped ->
					val needs = mutableSetOf<L2Entity<*>>()
					if (dataCouplingMode.considersSemanticValues)
					{
						clamped.filterIsInstance<L2SemanticValue<*>>()
							.toCollection(needs)
					}
					if (dataCouplingMode.considersRegisters)
					{
						clamped.filterIsInstance<L2Register<*>>()
							.toCollection(needs)
					}
					edgeNeeds[edge] = needs
				}
			}
		}

		// Visit the blocks in reverse dependency order, ignoring back-edges.
		// Collect all instructions that have side effects or produce values
		// consumed by a later non-dead instruction.
		controlFlowGraph.backwardVisit { block ->
			// All of its successors have already been processed.
			val neededEntities = mutableSetOf<L2Entity<*>>()
			block.successorEdges().forEach {
				neededEntities.addAll(edgeNeeds[it]!!)
			}
			assert(neededEntities.none { it == null} ) //TODO Romeve - kotlin type problem.
			val predecessorCount = block.predecessorEdges().size
			val instructions = block.instructions()
			var index = instructions.size
			while (--index >= 0)
			{
				val instruction = instructions[index]
				if (instruction is L2_PHI<*>)
				{
					break
				}
				// As a simplifying assumption, pretend an altersControlFlow
				// instruction at the end of the block populates *all* of the
				// entities that are visible along any of its successor edges.
				var dropInstruction = false
				if (instruction is L2_MOVE<*>
					&& dataCouplingMode.considersSemanticValues
					&& instruction.destination.register() !in neededEntities)
				{
					// The register being written by this move isn't consumed.
					// However, it may augment the manifest's synonym or
					// restriction in a way that we care about.  We want to keep
					// augmentations like semantic primitives because they can
					// be reused by the global value number scheme that semantic
					// values are about.  Things like stack slots don't add that
					// same value (when they're not read later).
					val writtenValues = instruction.destination.semanticValues()
					val readValues = instruction.source.register().definition()
						.semanticValues()
					val newValues = writtenValues - readValues
					if (newValues.none { it.isUsefulForGlobalValueNumbering })
					{
						dropInstruction = true
					}
					assert(neededEntities.none { it == null }) //TODO Remove – Kotlin type weakness
				}
				if (neededEntities.removeAll(
						dataCouplingMode.writeEntitiesOf(instruction))
					|| instruction.hasSideEffect)
				{
					if (!dropInstruction)
					{
						liveInstructions.add(instruction)
						neededEntities.addAll(
							dataCouplingMode.readEntitiesOf(instruction))
						assert(neededEntities.none { it == null }) //TODO Remove – Kotlin type weakness
					}
				}
			}
			assert(neededEntities.none { it == null }) //TODO Remove – Kotlin type weakness
			assert(block.predecessorEdges().isNotEmpty()
				|| neededEntities.isEmpty())
			{
				("Instruction consumes $neededEntities but a preceding "
					+ "definition was not found")
			}
			// Make a copy per predecessor (reusing the original for #0).
			val entitiesByPredecessor = (0..predecessorCount).map {
				if (it == 0) neededEntities
				else neededEntities.toMutableSet()
			}
			assert(neededEntities.none { it == null }) //TODO Remove – Kotlin type weakness
			assert(entitiesByPredecessor.all { s -> s.none { it == null } }) //TODO Remove – Kotlin type weakness
			// Customize
			while (index >= 0)
			{
				val phiInstruction = instructions[index]
				phiInstruction as L2_PHI<*>
				for (predecessorIndex in 0 until predecessorCount)
				{
					val entities = entitiesByPredecessor[predecessorIndex]
					if (entities.removeAll(
							dataCouplingMode.writeEntitiesOf(phiInstruction))
						|| phiInstruction.hasSideEffect)
					{
						liveInstructions.add(phiInstruction)
						val readOperand = phiInstruction.sources
							.elements[predecessorIndex]
						dataCouplingMode.addEntitiesFromRead(
							readOperand, entities)
						entities.addAll(
							dataCouplingMode.readEntitiesOf(readOperand))
						assert(entities.none { it == null }) // TODO – Remove, Kotlin type weakness.
					}
				}
				index--
			}
			assert(neededEntities.none { it == null }) //TODO Remove – Kotlin type weakness
			block.predecessorEdges()
				.zip(entitiesByPredecessor)
				.forEach { (edge, needed) ->
					// Some semantic constants get added to synonyms along edges
					// with no instruction being the apparent cause.  That's due
					// to a branching type test upstream.  If the downstream
					// needs a semantic value but it has no definition, check if
					// any equivalent (synonymous) semantic value has a
					// definition. If so, replace the needed value with an
					// equivalent that has a definition.
					val manifest = edge.manifest()
					needed
						.filterIsInstance<L2SemanticValue<*>>()
						.toList()  // Copy to avoid concurrent modification
						.forEach { semanticValue ->
							val equivalent = manifest
								.equivalentPopulatedSemanticValue(semanticValue)
							if (equivalent.notNullAnd { this != semanticValue })
							{
								needed.remove(semanticValue)
								needed.add(equivalent.cast())
							}
							else if (semanticValue.isConstant)
							{
								// The semantic constant has no equivalent
								// with a definition. Remove it from needed.
								needed.remove(semanticValue)
							}
						}
				}
			assert(neededEntities.none { it == null }) //TODO Remove – Kotlin type weakness
			block.predecessorEdges()
				.zip(entitiesByPredecessor)
				.forEach { (edge, needed) ->
					assert(edgeNeeds.containsKey(edge) == edge.isBackward)
					if (!edge.isBackward)
					{
						// No need to copy it, as it won't be modified again.

						// TODO - remove check for Kotlin type analysis weakness.
						assert(needed.none { it == null })

						edgeNeeds[edge] = needed
					}
				}
		}
	}

	/**
	 * Answer the [L2Instruction]s that were found to be live by a prior call to
	 * [analyzeReads].
	 *
	 * @return
	 *   An immutable [Set] of live [L2Instruction]s.
	 */
	fun liveInstructions(): Set<L2Instruction>
	{
		assert(liveInstructions.isNotEmpty())
		return Collections.unmodifiableSet(liveInstructions)
	}
}
