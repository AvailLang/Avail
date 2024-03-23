/*
 * L2Optimizer.kt
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

import avail.AvailRuntimeSupport
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.debugAvailableSplits
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OldInstruction
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_JUMP_BACK
import avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2ControlFlowGraph.StateFlag
import avail.optimizer.L2ControlFlowGraph.StateFlag.IS_SSA
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticValue
import avail.performance.Statistic
import avail.performance.StatisticReport.L2_OPTIMIZATION_TIME
import avail.utility.Strings.increaseIndentation
import avail.utility.deepForEach
import avail.utility.removeLast
import java.util.ArrayDeque
import java.util.BitSet
import java.util.Deque
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * An `L2Optimizer` optimizes its [L2ControlFlowGraph]. This is a control graph.
 * The vertices are [L2BasicBlock]s, which are connected via their successor and
 * predecessor lists.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property generator
 *   An [L2Generator] used for splicing short sequences of code as part of
 *   optimization.
 *
 * @constructor
 * Create an optimizer for the given [L2ControlFlowGraph] and its mutable [List]
 * of [L2BasicBlock]s.
 *
 * @param generator
 *   An [L2Generator] used for splicing in short sequences of new code as part
 *   of optimization.
 */
class L2Optimizer internal constructor(
	val generator: L2Generator)
{
	/** The [L2ControlFlowGraph] to optimize. */
	private val controlFlowGraph: L2ControlFlowGraph
		get() = generator.controlFlowGraph

	/** The mutable list of blocks taken from the [controlFlowGraph]. */
	val blocks: MutableList<L2BasicBlock>
		get() = controlFlowGraph.basicBlockOrder

	/** The register coloring algorithm. */
	private var colorer: L2RegisterColorer? = null

	/**
	 * Set each of the specified [StateFlag]s in the [controlFlowGraph].
	 *
	 * @param flags
	 *   The collection of [StateFlag]s to add.
	 */
	fun set(flags: Collection<KClass<out StateFlag>>)
	{
		controlFlowGraph.set(flags)
	}

	/**
	 * Clear each of the specified [StateFlag]s from the [controlFlowGraph].
	 *
	 * @param flags
	 *   The collection of [StateFlag]s to remove.
	 */
	fun clear(flags: Collection<KClass<out StateFlag>>)
	{
		controlFlowGraph.clear(flags)
	}

	/**
	 * Assert that each of the specified [StateFlag]s has been set in the
	 * [controlFlowGraph].
	 *
	 * @param flags
	 *   The collection of [StateFlag]s to check.
	 */
	fun check(flags: Collection<KClass<out StateFlag>>)
	{
		controlFlowGraph.check(flags)
	}

	/**
	 * Assert that each of the specified [StateFlag]s has been cleared in the
	 * [controlFlowGraph].
	 *
	 * @param flags
	 *   The collection of [StateFlag]s to check for absence.
	 */
	fun checkNot(flags: Collection<KClass<out StateFlag>>)
	{
		controlFlowGraph.checkNot(flags)
	}

	/**
	 * Find the [L2BasicBlock] that are actually reachable recursively from the
	 * blocks marked as [L2BasicBlock.isIrremovable].
	 *
	 * @return
	 *   `true` if any blocks were removed, otherwise `false`.
	 */
	fun removeUnreachableBlocks(): Boolean
	{
		val blocksToVisit = blocks.filterTo(ArrayDeque()) { it.isIrremovable }
		val reachableBlocks = mutableSetOf<L2BasicBlock?>()
		while (!blocksToVisit.isEmpty())
		{
			val block = blocksToVisit.removeLast()
			if (reachableBlocks.add(block))
			{
				block.successorEdges().mapTo(
					blocksToVisit, L2PcOperand::targetBlock)
			}
		}
		val unreachableBlocks = blocks.toMutableSet()
		unreachableBlocks.removeAll(reachableBlocks)
		for (block in unreachableBlocks)
		{
			block.instructions().forEach(L2Instruction::justRemoved)
			block.instructions().clear()
		}
		val changed = blocks.retainAll(reachableBlocks)
		// See if any blocks no longer need to be a loop head.
		if (changed)
		{
			for (block in blocks)
			{
				if (block.isLoopHead
					&& block.predecessorEdges().none(L2PcOperand::isBackward))
				{
					// It's a loop head that has no back-edges pointing to it.
					block.isLoopHead = false
				}
			}
		}
		return changed
	}

	/**
	 * Remove any unnecessary instructions.
	 *
	 * @param dataCouplingMode
	 *   The [DataCouplingMode] that chooses how to trace liveness.
	 * @param generatePhis
	 *   Whether to produce [L2_PHI_PSEUDO_OPERATION]s automatically.
	 */
	private fun removeDeadInstructions(
		dataCouplingMode: DataCouplingMode,
		generatePhis: Boolean)
	{
		val analyzer = DeadCodeAnalyzer(dataCouplingMode, controlFlowGraph)
		analyzer.analyzeReads()
		val liveInstructions = analyzer.liveInstructions()
		regenerateGraph(
			generatePhis = generatePhis,
			isRemovingDeadCode = true
		) { sourceInstruction ->
			if (sourceInstruction in liveInstructions)
				basicProcessInstruction(sourceInstruction)
		}
	}

	/**
	 * Remove all unreachable blocks and all instructions that don't either have
	 * a side-effect or produce a value ultimately used by an instruction that
	 * has a side-effect.
	 *
	 * @param dataCouplingMode
	 *   How to trace data dependencies.
	 */
	fun removeDeadCode(
		dataCouplingMode: DataCouplingMode,
		generatePhis: Boolean = true)
	{
		// Removing instructions won't cause blocks to be inaccessible, so just
		// clean up unreachable blocks once at the start.
		removeUnreachableBlocks()
		removeDeadInstructions(dataCouplingMode, generatePhis)
	}

	/**
	 * Find places where control flow diverges due to a condition that was known
	 * at some point earlier in the chain of phis leading to it.  Find all
	 * vertices from the phi where control flow merged and the knowledge of the
	 * condition was lost, up to the point where the condition is being tested
	 * again.
	 *
	 * Do this for every such control-flow branch point, accumulating the
	 * estimated profitability of each split.
	 *
	 * Then regenerate the instruction graph, but instead of merging and losing
	 * information at the affected phis, produce a duplicate for each reached
	 * profitable combination of conditions, allowing the code regeneration to
	 * take advantage of the stronger condition along that path... at the
	 * expense of producing more code.
	 *
	 * The graph starts and ends in SSA form.
	 */
	fun doCodeSplitting()
	{
		val startingRequests =
			mutableMapOf<L2BasicBlock, MutableSet<L2SplitCondition>>()
		val allConditions = mutableSetOf<L2SplitCondition>()
		// Find instructions that invoke hooks for failed dynamic type tests,
		// failed method lookups, and anything else that we don't expect to be
		// reached often.  Paths that always lead to reification might also be
		// considered cold (to be determined).  Cold paths will be excluded from
		// code splitting.
		controlFlowGraph.backwardVisit { block ->
			val lastInstruction = block.instructions().last()
			if (lastInstruction.isCold)
			{
				block.isCold = true
				return@backwardVisit
			}
			var successors =
				block.successorEdges().filterNot(L2PcOperand::isBackward)
			if (successors.isEmpty())
			{
				// The final instruction wasn't considered cold, and there are
				// no forward-pointing successors.  It's not cold.
				return@backwardVisit
			}
			if (successors.all { edge ->
				edge.targetBlock().isCold
					|| edge.targetBlock().entryPointOrNull() !== null
			})
			{
				// There's at least one successor edge, and all of them are
				// already cold (or an entry point).  This block is therefore
				// also cold.
				block.isCold = true
			}
		}
		controlFlowGraph.forwardVisit { block ->
			// Don't allow split wishes along cold paths to be honored.
			if (block.isCold) return@forwardVisit
			val newConditions = block.instructions()
				.flatMap(L2Instruction::interestingConditions)
				.filterNotNull()
			// Ignore ones that were already true along all incoming edges
			// of the block holding that instruction.
			val notAlreadyTrue = newConditions.filterNot { condition ->
				block.predecessorEdges().all { edge ->
					condition.holdsFor(edge.manifest())
				}
			}
			if (notAlreadyTrue.isNotEmpty())
			{
				assert(block !in startingRequests)
				startingRequests[block] = notAlreadyTrue.toMutableSet()
				allConditions.addAll(notAlreadyTrue)
			}
		}
		// We now know which blocks wished which conditions were true, with
		// deduplication for multiple instructions that reported equal
		// conditions within the same block.  Figure out which of these
		// conditions are *ever* satisfied, anywhere in the CFG.
		val everSatisfied = allConditions.filterTo(mutableSetOf()) {
				condition ->
			blocks.any { block ->
				block.predecessorEdges().any { edge ->
					!edge.isBackward && condition.holdsFor(edge.manifest())
				}
			}
		}
		if (everSatisfied.isEmpty())
		{
			// All wishes are either already fulfilled or impossible.
			return
		}
		// Remove wishes that aren't satisfied anywhere.
		if (everSatisfied.size < allConditions.size)
		{
			startingRequests.entries.retainAll { (_, conditions) ->
				conditions.retainAll(everSatisfied)
				conditions.isNotEmpty()
			}
		}
		// Sweep backward through the CFG (ignoring backward jumps), propagating
		// all wishes that are not yet fulfilled.  If an edge's manifest
		// fulfills a wish, remove it.  At branches (blocks with multiple
		// successors), take the union of the unfulfilled wishes.
		// Note that some wishes may propagate all the way to the start block,
		// unfulfilled.  That's because not every path from the start will
		// necessarily set the desired condition.  We do a separate pass forward
		// through the graph, removing wishes that are not satisfied in any
		// predecessor.
		val edgeWishes = mutableMapOf<L2PcOperand, Set<L2SplitCondition>>()
		startingRequests.forEach { (block, conditions) ->
			block.predecessorEdges().forEach { incomingEdge ->
				edgeWishes[incomingEdge] = conditions
			}
		}
		controlFlowGraph.backwardVisit { block ->
			val successorEdges =
				block.successorEdges().filterNot(L2PcOperand::isBackward)
			val unionOfConditions =
				if (successorEdges.isNotEmpty())
				{
					// Figure out what conditions the successors need that the
					// block doesn't produce locally.
					successorEdges
						.map { edge ->
							// NOTE: *Don't* remove conditions that were granted
							// by the block, since in the previous graph it had
							// to do that redundant work to grant the condition.
							edgeWishes[edge] ?: emptySet()
						}
						.reduce(Set<L2SplitCondition>::union)
				}
				else
				{
					emptySet()
				}
			// Mix in what the instructions of this block wished for.
			val extendedUnion = unionOfConditions.union(
				startingRequests[block] ?: emptySet())
			block.predecessorEdges().forEach { edge ->
				edgeWishes[edge] = extendedUnion
			}
		}
		// Now figure out what conditions are *possible* along each edge, if
		// suitably split, while simultaneously filtering the edgeWishes to the
		// possible ones.
		val edgePossibilities =
			mutableMapOf<L2PcOperand, Set<L2SplitCondition>>()
		controlFlowGraph.forwardVisit { block ->
			val possible = mutableSetOf<L2SplitCondition>()
			block.predecessorEdges().forEach { predecessorEdge ->
				if (!predecessorEdge.isBackward)
				{
					edgePossibilities[predecessorEdge]?.let {
						possible.addAll(it)
					}
				}
			}
			block.successorEdges().forEach { successorEdge ->
				if (!successorEdge.isBackward)
				{
					val wishes = edgeWishes[successorEdge]!!
					val newWishes = wishes.filter { condition ->
						condition in possible ||
							condition.holdsFor(successorEdge.manifest())
					}.toSet()
					edgeWishes[successorEdge] = newWishes
					edgePossibilities[successorEdge] = newWishes
				}
			}
		}

		// We now know all the places that code splits can start, which blocks
		// are affected, and where the subgraphs end.
		val splitConditions = blocks.associateWithTo(mutableMapOf()) { block ->
			block.predecessorEdges().flatMapTo(mutableSetOf()) { edge ->
				edgeWishes[edge]!!
			}
		}
		splitConditions.values.removeAll { it.isEmpty() }
		splitConditions.keys.removeIf(L2BasicBlock::isCold)
		if (splitConditions.isEmpty())
		{
			// Nothing to split.
			return
		}
		if (debugAvailableSplits)
		{
			// Annotate the original graph to make it easier to see what
			// splitting should happen.
			blocks.forEach { block ->
				splitConditions[block]?.let { conditions ->
					block.debugNote.append("Available splits:")
					conditions.forEach { condition ->
						block.debugNote.append("\n\t")
						block.predecessorEdges().forEach { edge ->
							block.debugNote.append(
								when
								{
									condition.holdsFor(edge.manifest()) -> '+'
									else -> '-'
								})
						}
						block.debugNote.append(' ')
						block.debugNote.append(condition)
					}
				}
			}
		}
		// Here's a good place to breakpoint to see the condition-labeled graph
		// prior to regeneration.

		regenerateGraph(
			generatePhis = true,
			interestingConditionsByOldBlock = splitConditions)
		{ sourceInstruction ->
			if (!sourceInstruction.isPhi)
			{
				basicProcessInstruction(sourceInstruction)
			}
		}
	}

	/**
	 * For every edge leading from a multiple-out block to a multiple-in block,
	 * split it by inserting a new block along it.  Note that we do this
	 * regardless of whether the target block has any phi functions.
	 */
	fun transformToEdgeSplitSSA()
	{
		// Copy the list of blocks, to safely visit existing blocks while new
		// ones are added inside the loop.
		blocks.toList().forEach { sourceBlock ->
			if (sourceBlock.successorEdges().size > 1)
			{
				sourceBlock.successorEdges().forEach { edge: L2PcOperand ->
					val targetBlock = edge.targetBlock()
					if (targetBlock.predecessorEdges().size > 1)
					{
						val newBlock = edge.splitEdgeWith(controlFlowGraph)
						// Add it somewhere that looks sensible for debugging,
						// although we'll order the blocks later.
						blocks.add(blocks.indexOf(targetBlock), newBlock)
					}
				}
			}
		}
		if (shouldSanityCheck)
		{
			blocks.forEach { sourceBlock ->
				if (sourceBlock.successorEdges().size > 1)
				{
					sourceBlock.successorEdges().forEach { edge: L2PcOperand ->
						val targetBlock = edge.targetBlock()
						assert(targetBlock.predecessorEdges().size == 1)
					}
				}
			}
		}
	}

	/**
	 * Determine which registers are live-in for each block.  We distinguish
	 * between always-live-in, where all future paths from the start of a block
	 * lead to a use of the register, and sometimes-live-in, where at least one
	 * future path from the start of the block leads to a use of the register.
	 */
	fun computeLivenessAtEachEdge()
	{
		blocks.deepForEach({ predecessorEdges() }) { predecessor ->
			predecessor.alwaysLiveInRegisters.clear()
			predecessor.sometimesLiveInRegisters.clear()
		}

		// The deque and the set maintain the same membership.
		val workQueue = ArrayDeque(blocks)
		val workSet = blocks.toMutableSet()
		while (!workQueue.isEmpty())
		{
			val block = workQueue.removeLast()
			workSet.remove(block)
			// Take the union of the outbound edges' sometimes-live registers.
			// Also find the intersection of those edges' always-live registers.
			val alwaysLive = mutableSetOf<L2Register<*>>()
			if (block.successorEdges().isNotEmpty())
			{
				// Before processing instructions in reverse order, the
				// always-live-in set will be the intersection of the successor
				// edges' always-live-in sets.  Pick any edge's always-live-in
				// set as the starting case, to be intersected with each edge's
				// set in the loop below.
				alwaysLive.addAll(
					block.successorEdges()[0].alwaysLiveInRegisters)
			}
			val sometimesLive = mutableSetOf<L2Register<*>>()
			block.successorEdges().forEach { edge ->
				sometimesLive.addAll(edge.sometimesLiveInRegisters)
				alwaysLive.retainAll(edge.alwaysLiveInRegisters)
			}
			// Now work backward through each instruction, removing registers
			// that it writes, and adding registers that it reads.
			val instructions = block.instructions()
			var lastPhiIndex = -1
			for (i in instructions.indices.reversed())
			{
				val instruction = instructions[i]
				if (instruction.isPhi)
				{
					// We've reached the phis at the start of the block.
					lastPhiIndex = i
					break
				}
				@Suppress("ConvertArgumentToSet")
				sometimesLive.removeAll(instruction.destinationRegisters)
				sometimesLive.addAll(instruction.sourceRegisters)
				@Suppress("ConvertArgumentToSet")
				alwaysLive.removeAll(instruction.destinationRegisters)
				alwaysLive.addAll(instruction.sourceRegisters)
			}

			// Add in the predecessor-specific live-in information for each edge
			// based on the corresponding positions inside phi instructions.
			val finalLastPhiIndex = lastPhiIndex
			var edgeIndex = 0
			block.predecessorEdges().forEach { edge ->
				val edgeAlwaysLiveIn = alwaysLive.toMutableSet()
				val edgeSometimesLiveIn = sometimesLive.toMutableSet()
				// Add just the registers used along this edge.
				for (i in finalLastPhiIndex downTo 0)
				{
					val phiInstruction = instructions[i]
					edgeSometimesLiveIn.removeAll(
						phiInstruction.destinationRegisters)
					edgeAlwaysLiveIn.removeAll(
						phiInstruction.destinationRegisters)
					val sources = phiInstruction.phiSourceRegisterReads
					val source = sources[edgeIndex].register()
					edgeSometimesLiveIn.add(source)
					edgeAlwaysLiveIn.add(source)
				}
				val predecessorEdge = block.predecessorEdges()[edgeIndex]
				var changed =
					predecessorEdge.sometimesLiveInRegisters.addAll(
						edgeSometimesLiveIn)
				changed =
					changed or predecessorEdge.alwaysLiveInRegisters.addAll(
						edgeAlwaysLiveIn)
				if (changed)
				{
					// We added to the known live registers of the edge.
					// Continue propagating to the predecessor.
					val predecessor = edge.sourceBlock()
					if (!workSet.contains(predecessor))
					{
						workQueue.addFirst(predecessor)
						workSet.add(predecessor)
					}
				}
				edgeIndex++
			}
		}
	}

	/**
	 * Replace constant-valued registers with fresh registers that have no
	 * definitions.  The JVM code generator will recognize that these are
	 * constants, and produce code to produce them on the JVM stack by reading
	 * the constants pool or via special instructions for int/double constants.
	 */
	fun replaceConstantRegisters()
	{
		//TODO Not yet implemented.
	}

	/**
	 * Regenerate the edge-split SSA graph, postponing emission of
	 * side-effectless instructions until just before they're needed.
	 *
	 * The [L2ValueManifest] maintains a map from [L2SemanticValue] to a list of
	 * [L2Instruction]s that were translated from the original graph, but not
	 * yet emitted.  When a semantic value is needed by an instruction being
	 * emitted, we emit a copy of the postponed instructions to provide that
	 * value (recursively, as needed).
	 *
	 * This maximally postpones construction of values, ensuring they're only
	 * constructed along paths where they're actually needed.
	 *
	 * The astute reader will have noticed I haven't mentioned control flow
	 * merges.  Normally a control flow merge produces phi instructions for any
	 * semantic values common to all incoming edges that aren't from a common
	 * register.  When postponing instructions in this way, we want to avoid
	 * generating the same value multiple times along any path.  Therefore, if
	 * we have a semantic value available in a register on at least one incoming
	 * path, and that semantic value is either available in the manifests or
	 * available as a postponed instruction in each of the incoming edges, we
	 * force the postponed instructions to be generated in the predecessor
	 * blocks, just prior to their final jump.  This is safe, because we're at a
	 * merge point in an edge-split SSA graph, so none of the predecessors can
	 * have multiple successors.
	 *
	 * This effects a complete redundancy elimination (other than for loops).
	 * The resulting graph is still in edge-split SSA form.
	 */
	fun postponeConditionallyUsedValues()
	{
		// Emit the transformation of the given instruction, emitting any
		// necessary postponed instructions first.
		regenerateGraph(true) { sourceInstruction ->
			if (sourceInstruction.goesMultipleWays)
			{
				// Don't allow instructions to be delayed across an instruction
				// that goes both ways, since that would make the computation in
				// one of the forks redundant with the computation in the other.
				// Specifically, an L2_SAVE_ALL_AND_PC_TO_INT must act as a
				// barrier against postponement, since values created after the
				// fork will not affect the collection of registers that need to
				// be saved in a register dump and restored on the second path.
				// For simplicity, just recursively force all postponed
				// instructions to be generated here.
				forceAllPostponedTranslationsExceptConstantMoves(null, true)
			}
			when
			{
				sourceInstruction.isPhi ->
				{
					// Ignore it.
				}
				sourceInstruction.hasSideEffect ->
				{
					// Emit the translation right now.
					forcePostponedTranslationNow(sourceInstruction)
				}
				else ->
				{
					// Postpone the translation.  The same instruction may be
					// translated multiple times, in different basic blocks.
					currentManifest
						.recordPostponedSourceInstruction(sourceInstruction)
				}
			}
		}
	}


	/**
	 * Regenerate the [controlFlowGraph], using the given instruction
	 * transformer function.
	 *
	 * @param generatePhis
	 *   Whether to produce phi instructions automatically based on semantic
	 *   values that are in common among incoming edges at merge points.  This
	 *   is false when phis have already been replaced with non-SSA moves.
	 * @param interestingConditionsByOldBlock
	 *   A map that contains information about which conditions should be
	 *   preserved through splitting of which original blocks (because the
	 *   condition may be tested downstream.  If an original block is not
	 *   present, it should not be split.
	 * @param transformer
	 *   What to do with each [L2Instruction] encountered in the old graph.
	 */
	private fun regenerateGraph(
		generatePhis: Boolean,
		isRemovingDeadCode: Boolean = false,
		interestingConditionsByOldBlock:
			Map<L2BasicBlock, Set<L2SplitCondition>> = emptyMap(),
		transformer: L2Regenerator.(L2Instruction)->Unit)
	{
		// Use an L2Regenerator to do the substitution.  First empty the CFG
		// into oldGraph, then scan oldGraph to regenerate (with substitutions)
		// into the emptied CFG.
		val oldGraph = L2ControlFlowGraph()
		controlFlowGraph.evacuateTo(oldGraph)
		val inverseSpecialBlockMap =
			generator.specialBlocks.entries.associate { (s, b) -> b to s }
		val regenerator = object : L2Regenerator(
			generator,
			generatePhis = generatePhis,
			isRegeneratingDeadCode = isRemovingDeadCode)
		{
			/**
			 * Collapsing unconditional jumps wouldn't preserve all blocks that
			 * we're generating, which would break some simplifying assumptions.
			 */
			override val canCollapseUnconditionalJumps: Boolean get() = false

			override fun processInstruction(
				sourceInstruction: L2Instruction)
			{
				if (!sourceInstruction.isPhi)
				{
					transformer(sourceInstruction)
				}
				if (shouldSanityCheck &&
					!isRemovingDeadCode &&
					!sourceInstruction.altersControlFlow)
				{
					// Make sure all the semantic values that were in the old
					// graph have values in the new graph, even if some of them
					// might be latent in the manifest's postponed instructions.
					// We also have to be at a reachable place here.
					assert(currentlyReachable())
					val unpopulated = mutableSetOf<L2SemanticValue<*>>()
					sourceInstruction.writeOperands
						.deepForEach(L2WriteOperand<*>::semanticValues)
						{
							if (!currentManifest.hasSemanticValue(it))
							{
								unpopulated.add(it)
							}
						}
					assert(unpopulated.isEmpty()) {
						buildString {
							append("Regeneration (")
							append(sourceInstruction)
							append(") failed to populate values: ")
							append(unpopulated)
						}
					}
				}
			}
		}
		regenerator.inverseSpecialBlockMap.clear()
		regenerator.inverseSpecialBlockMap.putAll(inverseSpecialBlockMap)
		generator.specialBlocks.clear()
		regenerator.processSourceGraph(
			oldGraph, interestingConditionsByOldBlock)
	}

	/**
	 * Find any remaining occurrences of [L2_VIRTUAL_CREATE_LABEL], or any other
	 * [L2Instruction] using an [L2Operation] that says it
	 * [L2Operation.isPlaceholder].  This happens in a fresh control flow graph,
	 * as part of the injected behavior of an [L2Regenerator].
	 */
	fun replacePlaceholderInstructions()
	{
		if (blocks.all { it.instructions().all { i -> !i.isPlaceholder } })
		{
			// There were no placeholder instructions.
			return
		}
		// Use an L2Regenerator to do the substitution.
		regenerateGraph(true) { sourceInstruction ->
			sourceInstruction.generateReplacement(this)
		}
	}

	/**
	 * For every phi operation, insert a move at the end of the block that leads
	 * to it.  Because of our version of edge splitting, that block always
	 * contains just a jump.  The CFG will no longer be in SSA form, because the
	 * phi variables will have multiple defining instructions (the moves).
	 *
	 * Also eliminate the phi functions.
	 */
	fun insertPhiMoves()
	{
		for (block in blocks)
		{
			val instructionIterator = block.instructions().iterator()
			while (instructionIterator.hasNext())
			{
				val instruction = instructionIterator.next()
				if (!instruction.isPhi)
				{
					// Phi functions are always at the start, so we must be past
					// them, if any.
					break
				}
				val phiSources = instruction.phiSourceRegisterReads
				val fanIn = block.predecessorEdges().size
				assert(fanIn == phiSources.size)
				val targetWriter = instruction.phiDestinationRegisterWrite
				// Insert a non-SSA move in each predecessor block.
				for (i in 0 until fanIn)
				{
					val edge = block.predecessorEdges()[i]
					val predecessor = edge.sourceBlock()
					val instructions = predecessor.instructions()
					assert(predecessor.finalInstruction()
						.isUnconditionalJumpForward)
					val sourceRead = phiSources[i]
					val move = L2OldInstruction(
						instruction.phiMoveOperation,
						sourceRead,
						targetWriter.clone()
					).cloneFor(predecessor)
					predecessor.insertInstruction(instructions.size - 1, move)
					if (edge.isBackward)
					{
						edge.manifest().replaceDefinitions(
							instruction.phiMoveOperation.destinationOf(move))
					}
					else
					{
						edge.manifest().recordDefinitionNoCheck(
							instruction.phiMoveOperation.destinationOf(move))
					}
					if (edge.forcedClampedEntities !== null)
					{
						// Replace the semantic value(s) and register in the
						// clamped set of entities, if present.
						val clamped = edge.forcedClampedEntities!!
						if (clamped.remove(sourceRead.semanticValue()))
						{
							clamped.addAll(targetWriter.semanticValues())
						}
						if (clamped.remove(sourceRead.register()))
						{
							clamped.add(targetWriter.register())
						}
					}
				}
				// Eliminate the phi function itself.
				instructionIterator.remove()
				instruction.justRemoved()
			}
		}
	}

	/**
	 * Determine which pairs of registers have to be simultaneously live and
	 * potentially holding distinct values.
	 */
	fun computeInterferenceGraph()
	{
		computeLivenessAtEachEdge()
		colorer = L2RegisterColorer(controlFlowGraph)
		colorer!!.computeInterferenceGraph()
	}

	/**
	 * For each [L2_MOVE] instruction, if the register groups associated with
	 * the source and destination registers don't have an interference edge
	 * between them then merge the groups together.  The resulting merged group
	 * should have interferences with each group that either the source
	 * register's group or the destination register's group had interferences
	 * with.
	 */
	fun coalesceNoninterferingMoves()
	{
		colorer!!.coalesceNoninterferingMoves()
	}

	/**
	 * Assign final coloring to each register based on the interference graph
	 * and coalescing map.
	 */
	fun computeColors()
	{
		colorer!!.computeColors()
		colorer = null
	}

	/**
	 * Create a new register for every &lt;kind, finalIndex&gt; (i.e., color) of
	 * an existing register, then transform every instruction of this control
	 * flow graph to use the new registers.  The new registers have a
	 * [L2Register.uniqueValue] that's the same as its
	 * [finalIndex][L2Register.finalIndex].
	 */
	fun replaceRegistersByColor()
	{
		// Create new registers for each <kind, finalIndex> in the existing
		// registers.
		val byKindAndIndex: Map<RegisterKind<*>, MutableMap<Int, L2Register<*>>> =
			RegisterKind.all.associateWithTo(mutableMapOf()) { mutableMapOf() }
		val remap: MutableMap<L2Register<*>, L2Register<*>> = mutableMapOf()
		// Also collect all the old registers.
		val oldRegisters = mutableSetOf<L2Register<*>>()
		val action: (L2Register<*>)->Unit = { reg: L2Register<*> ->
			remap[reg] = byKindAndIndex[reg.kind]!!
				.computeIfAbsent(reg.finalIndex()) { reg.copyAfterColoring() }
			oldRegisters.add(reg)
		}
		blocks.deepForEach({ instructions() }) { instruction ->
			instruction.sourceRegisters.forEach(action)
			instruction.destinationRegisters.forEach(action)
		}
		// Actually remap every register.
		blocks.deepForEach({ instructions() }) { it.replaceRegisters(remap) }
		// Check that the obsolete registers have no uses or definitions.
		oldRegisters.forEach { r ->
			assert(r.uses().isEmpty() && r.definitions().isEmpty()) {
				"OBSOLETE register still refers to instructions"
			}
		}
	}

	/**
	 * Eliminate any [L2_MOVE]s between registers of the same color. The graph
	 * must have been colored already, and is not expected to be in SSA form,
	 * and is certainly not after this, since removed moves are the SSA
	 * definition points for their target registers.
	 */
	fun removeSameColorMoves()
	{
		for (block in blocks)
		{
			val iterator =
				block.instructions().iterator()
			while (iterator.hasNext())
			{
				val instruction = iterator.next()
				if (instruction.isMove
					&& instruction.sourceRegisters[0].finalIndex()
					== instruction.destinationRegisters[0].finalIndex())
				{
					iterator.remove()
					instruction.justRemoved()
				}
			}
		}
	}

	/**
	 * Each block that ends with an unconditional [L2_JUMP] to a block with only
	 * that incoming edge should be merged with that successor block, eliding
	 * the jump.  Additionally, any block that contains only an unconditional
	 * jump can be removed, adjusting its incoming edges to point to the jump's
	 * target instead.
	 *
	 * Don't do this if the destination block is [L2BasicBlock.isIrremovable].
	 */
	fun adjustEdgesLeadingToJumps()
	{
		do
		{
			val blocksToRemove = mutableSetOf<L2BasicBlock>()
			for (block in controlFlowGraph.basicBlockOrder)
			{
				assert(block !in blocksToRemove)
				val jump = block.finalInstruction()
				if (!jump.isUnconditionalJumpForward) continue
				val edge = jump.targetEdges.single()
				val target = edge.targetBlock()
				// Don't remove the block if it's irremovable or a loop head.
				if (block.isIrremovable || block.isLoopHead) continue
				// If we have instructions to push forward to get rid of the
				// block, don't push them to an irremovable or loop head.
				if (block.instructions().size > 1)
				{
					// There are instructions that would have to be moved to the
					// target block.
					if (target.isIrremovable) continue
					if (target.isLoopHead) continue
					if (edge.isBackward) continue
					if (target.predecessorEdges().size > 1) continue
				}
				// Move the instructions (if any) other than the jump from the
				// block to the target, since either there aren't any such
				// instructions or we're the sole predecessor of the target.
				block.instructions().removeLast()  // the jump
				jump.justRemoved()
				while (block.instructions().isNotEmpty())
				{
					val instruction = block.instructions().last()
					instruction.moveToBlock(target, 0)
				}
				block.predecessorEdges().toList().forEach { predecessorEdge ->
					predecessorEdge.switchTargetBlockNonSSA(
						target, predecessorEdge.isBackward || edge.isBackward)
				}
				assert (block.predecessorEdges().isEmpty())
				blocksToRemove.add(block)
			}
			blocks.removeAll(blocksToRemove)
		} while (blocksToRemove.isNotEmpty())

		// Part 2: redirect all edges that lead to blocks that contain only an
		// unconditional jump.
		var changed: Boolean
		do
		{
			changed = false
			val blockIterator = blocks.iterator()
			while (blockIterator.hasNext())
			{
				val block = blockIterator.next()
				if (block.isLoopHead || block.instructions().size != 1)
				{
					continue
				}
				val soleInstruction = block.finalInstruction()
				val jumpEdge = if (soleInstruction.isUnconditionalJumpForward)
				{
					L2_JUMP.jumpTarget(soleInstruction)
				}
				else if (soleInstruction.isUnconditionalJumpBackward)
				{
					L2_JUMP_BACK.jumpTarget(soleInstruction)
				}
				else
				{
					continue
				}
				// Redirect all predecessors through the jump.
				val jumpTarget = jumpEdge.targetBlock()
				val isBackward = jumpEdge.isBackward
				// Copy it; the predecessorEdges list will change in the loop.
				for (inEdge in block.predecessorEdges().toList())
				{
					changed = true
					inEdge.switchTargetBlockNonSSA(jumpTarget, isBackward)
				}
				assert(block.predecessorEdges().isEmpty())
				if (!block.isIrremovable)
				{
					block.instructions().clear()
					soleInstruction.justRemoved()
					blockIterator.remove()
				}
			}
		}
		while (changed)
	}

	/**
	 * Re-order the blocks to minimize the number of pointless jumps.  When we
	 * start generating JVM code, this should also try to make one of the paths
	 * from conditional branches come after the branch, otherwise an extra jump
	 * instruction has to be generated.
	 *
	 * The initial block should always come first.
	 *
	 * For now, use the simple heuristic of only placing a block if all its
	 * predecessors have been placed (or if there are only cycles unplaced, pick
	 * one arbitrarily).
	 */
	fun orderBlocks()
	{
		val countdowns = mutableMapOf<L2BasicBlock, AtomicInteger>()
		for (block in blocks)
		{
			countdowns[block] = AtomicInteger(block.predecessorEdges().size)
		}
		val order = mutableListOf<L2BasicBlock>()
		assert(blocks[0].predecessorEdges().isEmpty())
		val zeroed: Deque<L2BasicBlock> = ArrayDeque()
		for (i in blocks.indices.reversed())
		{
			if (blocks[i].predecessorEdges().isEmpty())
			{
				zeroed.add(blocks[i])
			}
		}
		assert(zeroed.last == blocks[0])
		while (countdowns.isNotEmpty())
		{
			if (zeroed.isNotEmpty())
			{
				val block = zeroed.removeLast()
				order.add(block)
				block.successorEdges().forEach { edge ->
					val countdown = countdowns[edge.targetBlock()]
					// Note that the entry may have been removed to break a
					// cycle.  See below.
					if (countdown !== null && countdown.decrementAndGet() == 0)
					{
						countdowns.remove(edge.targetBlock())
						zeroed.add(edge.targetBlock())
					}
				}
			}
			else
			{
				// Only cycles and blocks reachable from cycles are left.  Pick
				// a node at random, preferring one that has had at least one
				// predecessor placed.
				var victim: L2BasicBlock? = null
				for ((key, value) in countdowns)
				{
					if (value.get() < key.predecessorEdges().size)
					{
						victim = key
						break
					}
				}
				// No remaining block has had a predecessor placed.  Pick a
				// block at random.
				if (victim === null)
				{
					victim = countdowns.keys.first()
				}
				countdowns.remove(victim)
				zeroed.add(victim)
			}
		}
		assert(order.size == blocks.size)
		assert(order[0] == blocks[0])
		blocks.clear()
		blocks.addAll(order)
	}

	/**
	 * Insert an [L2_MAKE_IMMUTABLE] instruction just prior to any use of a
	 * register that is not already provably immutable and may be used again
	 * later.
	 *
	 * In particular, walk the graph forwards, using the live-in information on
	 * edges, captured by [computeLivenessAtEachEdge].  When a write to a
	 * register is encountered, discard information about that register and
	 * record whether it's known to start out immutable (e.g., from a constant).
	 * When a read of the register is encountered (treating reads as occurring
	 * before writes within an instruction), and if the register is not known to
	 * be immutable, record where in the basic block it was read.  If we already
	 * have an entry for that register, then we've just encountered exactly the
	 * second read of it within the block, so insert an [L2_MAKE_IMMUTABLE]
	 * instruction just before the first read, and mark the register as being
	 * immutable.
	 *
	 * When we reach the end of the block, use the sometimes-live-in information
	 * on the outbound edges to determine which registers are still potentially
	 * live (the union of the outgoing edges' sometimes-live-in sets).  Each
	 * potentially live, mutable register should have an [L2_MAKE_IMMUTABLE]
	 * emitted just before its first use.
	 *
	 * When starting a block, begin by taking the union of the mutable sets of
	 * the incoming edges (i.e., the registers which have been written with a
	 * potentially mutable value, and have not yet been read).
	 */
	fun insertMakeImmutable()
	{
		// For each edge, this is the set of registers that are both live and
		// possibly mutable.
		val mutablesByEdge = mutableMapOf<L2PcOperand, Set<L2BoxedRegister>>()
		controlFlowGraph.forwardVisit { block ->
			val mutables = mutableSetOf<L2BoxedRegister>()
			block.predecessorEdges().forEach {
				if (!it.isBackward) mutables.addAll(mutablesByEdge[it]!!)
			}
			val firstUses =
				mutableMapOf<L2BoxedRegister, Pair<Int, L2ReadBoxedOperand>>()
			val instructions = block.instructions()
			val insertions = Array(instructions.size) {
				mutableListOf<L2ReadBoxedOperand>()
			}
			instructions.forEachIndexed { i, instruction ->
				// Deal with the register reads.
				if (instruction.isMove
					&& instruction.sourceRegisters.single()
						== instruction.destinationRegisters.single())
				{
					// Treat it as a pass-through, since it just moves from a
					// register to itself.
					return@forEachIndexed
				}
				instruction.readOperands
					.filterIsInstance<L2ReadBoxedOperand>()
					.forEach { read ->
						val readReg = read.register() as L2BoxedRegister
						val pair = firstUses[readReg]
						when
						{
							pair !== null ->
							{
								// We just hit the second use within the block.
								insertions[pair.first].add(pair.second)
								// It's no longer mutable.
								mutables.remove(readReg)
								firstUses.remove(readReg)
							}
							readReg in mutables ->
							{
								// Record this first use of a mutable.
								firstUses[readReg] = i to read
							}
						}
					}
				// Deal with the register writes.
				instruction.destinationRegisters
					.filterIsInstance<L2BoxedRegister>()
					.forEach { writeReg ->
						firstUses.remove(writeReg)
						mutables.remove(writeReg)
						when
						{
							instruction.isMoveConstant ->
							{
								// Constants are always immutable.
							}
							// Note: Rely on register coloring's L2_MOVE
							// elimination to minimize spurious make-immutables.
							//is L2_MOVE<*> -> { ??? }
							else ->
							{
								mutables.add(writeReg)
							}
						}
					}
			}
			// We've processed the block's instructions.  Now use the live-in
			// information on the outbound edges as additional uses, to
			// determine whether to insert a make-immutable.  We can use the
			// union of the outbound registers, because the colorer treats the
			// outputs of branching instructions as interfering with each other.
			val unionOfLive = block.successorEdges()
				.flatMapTo(
					mutableSetOf(), L2PcOperand::sometimesLiveInRegisters)
				.filterIsInstance<L2BoxedRegister>()
			// Treat these as reads that happen "during" the block's final
			// instruction.
			for (readReg in unionOfLive)
			{
				firstUses[readReg]?.let { (i, read) ->
					// It may be used after this block – make it immutable.
					insertions[i].add(read)
					// It's no longer mutable.
					mutables.remove(readReg)
					firstUses.remove(readReg)
				}
			}
			// Now insert the L2_MAKE_IMMUTABLE instructions where we indicated,
			// in descending order to bypass problems with indexing.
			insertions.withIndex().reversed().forEach { (i, reads) ->
				reads.forEach { read ->
					block.insertInstruction(
						i,
						L2OldInstruction(
							L2_MAKE_IMMUTABLE,
							read,
							L2WriteBoxedOperand(
								setOf(read.semanticValue()),
								read.restriction(),
								read.register())
						).cloneFor(block)
					)
				}
			}
			// Add edges for the successor blocks to use.
			block.successorEdges().forEach { edge ->
				mutablesByEdge[edge] = mutables
			}
		}
	}

	/**
	 * Remove information from the [L2ControlFlowGraph] that will no longer be
	 * needed.  Note that during subsequent inlining of this chunk at a call
	 * site, the type information will be reconstructed without too much cost.
	 */
	fun postOptimizationCleanup()
	{
		blocks.deepForEach(
			L2BasicBlock::instructions, L2Instruction::postOptimizationCleanup)
	}

	/**
	 * A helper class used for sanity checking the liveness of registers.
	 */
	private class UsedRegisters
	{
		/**
		 * Which registers are live here, organized by [RegisterKind]'s ordinal.
		 */
		val liveRegistersByKind: Array<BitSet>

		/**
		 * Reduce the collection of registers live here by intersecting it with
		 * the argument.  Answer whether it changed.
		 *
		 * @param another
		 *   The other `UsedRegisters`.
		 * @return
		 *   Whether the intersection made a change.
		 */
		fun restrictTo(another: UsedRegisters): Boolean
		{
			var changed = false
			for (i in liveRegistersByKind.indices)
			{
				val registers = liveRegistersByKind[i]
				val count = registers.cardinality()
				registers.and(another.liveRegistersByKind[i])
				changed = changed or (registers.cardinality() != count)
			}
			return changed
		}

		/**
		 * Record a register being read.
		 *
		 * @param register
		 *   The register being read.
		 * @param registerIdFunction
		 *   How to extract an id from the register.
		 */
		fun readRegister(
			register: L2Register<*>,
			registerIdFunction: (L2Register<*>)->Int)
		{
			assert(
				liveRegistersByKind[register.kind.ordinal]
					.get(registerIdFunction(register)))
		}

		/**
		 * Process a register being written.
		 *
		 * @param register
		 *   The register being written.
		 * @param registerIdFunction
		 *   How to extract an id from the register.
		 */
		fun writeRegister(
			register: L2Register<*>,
			registerIdFunction: (L2Register<*>)->Int)
		{
			liveRegistersByKind[register.kind.ordinal]
				.set(registerIdFunction(register))
		}

		/**
		 * Clear usage information about all registers.
		 */
		@Suppress("unused")
		fun clearAll()
		{
			for (i in liveRegistersByKind.indices)
			{
				liveRegistersByKind[i].clear()
			}
		}

		/** Create an instance with no tracking information. */
		constructor()
		{
			liveRegistersByKind = Array(RegisterKind.all.size) { BitSet() }
		}

		/**
		 * Duplicate an existing instance.
		 *
		 * @param original
		 *   The existing instance to duplicate.
		 */
		constructor(original: UsedRegisters)
		{
			liveRegistersByKind = Array(RegisterKind.all.size) {
				original.liveRegistersByKind[it].clone() as BitSet
			}
		}
	}

	override fun toString(): String = buildString {
		for (block in blocks)
		{
			append(block.name())
			append(":\n")
			for (instruction in block.instructions())
			{
				append('\t')
				append(
					increaseIndentation(
						instruction.toString(), 1))
				append('\n')
			}
			append('\n')
		}
	}

	/**
	 * Check that each instruction of each block has that block set for its
	 * [L2Instruction.basicBlock] field.  Also check that every
	 * instruction's applicable operands are listed as uses or definitions of
	 * the register that they access, and that there are no other uses or
	 * definitions.
	 */
	private fun checkBlocksAndInstructions()
	{
		val uses = mutableMapOf<L2Register<*>, MutableSet<L2ReadOperand<*>>>()
		val definitions =
			mutableMapOf<L2Register<*>, MutableSet<L2WriteOperand<*>>>()
		val allSuccessors = mutableListOf<L2PcOperand>()
		val allPredecessors = mutableListOf<L2PcOperand>()
		blocks.forEach { block ->
			assert(block.instructions().isNotEmpty())
			assert(block.instructions().last().altersControlFlow)
			block.instructions().forEach { instruction ->
				instruction.assertHasBeenEmitted()
				instruction.readOperands.forEach {
					uses.getOrPut(it.register(), ::mutableSetOf).add(it)
				}
				instruction.writeOperands.forEach {
					definitions
						.getOrPut(it.register(), ::mutableSetOf)
						.add(it)
				}
			}
			// Ensure the successorEdges of the block agree with the edges of
			// the last instruction.  Also collect all successor and predecessor
			// edges to check for duplicates.
			val successors = block.instructions().last().targetEdges
			assert(successors == block.successorEdges())
			successors.forEach { successorEdge ->
				assert(successorEdge.sourceBlock() == block)
				val targetBlock = successorEdge.targetBlock()
				assert(successorEdge in targetBlock.predecessorEdges())
			}
			allSuccessors.addAll(successors)
			allPredecessors.addAll(block.predecessorEdges())
		}
		assert(allSuccessors.size == allSuccessors.toSet().size)
		assert(allPredecessors.size == allPredecessors.toSet().size)
		assert(allSuccessors.toSet() == allPredecessors.toSet())
		val mentionedRegs = uses.keys.toMutableSet()
		mentionedRegs.addAll(definitions.keys)
		val myEmptySet = setOf<L2ReadOperand<*>>()
		for (reg in mentionedRegs)
		{
			assert((uses[reg] ?: myEmptySet) == reg.uses())
			assert((definitions[reg] ?: myEmptySet) == reg.definitions())
		}
	}

	/**
	 * Ensure all instructions' operands occur only once, including within
	 * vector operands.
	 */
	private fun checkUniqueOperands()
	{
		val allOperands = mutableSetOf<L2Operand>()
		blocks.deepForEach({ instructions() }) { instruction: L2Instruction ->
			instruction.operands.forEach { operand ->
				val added = allOperands.add(operand)
				assert(added)
				if (operand is L2ReadVectorOperand<*>)
				{
					operand.elements.forEach {
						val ok = allOperands.add(it)
						assert(ok)
					}
				}
			}
		}
	}

	/**
	 * Check that all edges are correctly connected, and that phi functions have
	 * the right number of inputs.
	 */
	private fun checkEdgesAndPhis()
	{
		for (block in blocks)
		{
			val allEdgesFromBlock = mutableListOf<L2PcOperand>()
			for (instruction in block.instructions())
			{
				if (instruction.isPhi)
				{
					assert(
						instruction.sourceRegisters.size
							== block.predecessorEdges().size)
				}
				allEdgesFromBlock.addAll(instruction.targetEdges)
			}
			assert(block.successorEdges() == allEdgesFromBlock)
			assert(allEdgesFromBlock.toSet() == block.successorEdges().toSet())
			block.successorEdges().forEach { edge: L2PcOperand ->
				assert(edge.sourceBlock() == block)
				val targetBlock = edge.targetBlock()
				assert(!edge.isBackward || targetBlock.isLoopHead)
				assert(blocks.contains(targetBlock))
				assert(targetBlock.predecessorEdges().contains(edge))
			}
			// Also check incoming edges.
			block.predecessorEdges().forEach { inEdge: L2PcOperand ->
				assert(inEdge.targetBlock() == block)
				val predecessorBlock = inEdge.sourceBlock()
				assert(blocks.contains(predecessorBlock))
				assert(predecessorBlock.successorEdges().contains(inEdge))
			}
		}
	}

	/**
	 * Perform a basic sanity check on the instruction graph, ensuring that each
	 * use of a register is preceded in all histories by a write to it.  Use the
	 * provided function to indicate what "the same" register means, so that
	 * this can be used for uncolored SSA and colored non-SSA graphs.
	 *
	 * @param registerIdFunction
	 *   A function that transforms a register into the index that should be
	 *   used to identify it.  This allows pre-colored and post-colored register
	 *   uses to be treated differently.
	 */
	private fun checkRegistersAreInitialized(
		registerIdFunction: (L2Register<*>)->Int)
	{
		val blocksToCheck: Deque<Pair<L2BasicBlock, UsedRegisters>> =
			ArrayDeque()
		blocksToCheck.add(blocks[0] to UsedRegisters())
		val inSets: MutableMap<L2BasicBlock, UsedRegisters> = HashMap()
		while (!blocksToCheck.isEmpty())
		{
			val pair = blocksToCheck.removeLast()
			val block = pair.first
			val newUsed = pair.second
			var checked = inSets[block]
			if (checked === null)
			{
				checked = UsedRegisters(newUsed)
				inSets[block] = checked
			}
			else
			{
				if (!checked.restrictTo(newUsed))
				{
					// We've already checked this block with this restricted set
					// of registers.  Ignore this path.
					continue
				}
			}
			// Check the block (or check it again with fewer valid registers)
			val workingSet = UsedRegisters(checked)
			for (instruction in block.instructions())
			{
				if (!instruction.isPhi)
				{
					for (register in instruction.sourceRegisters)
					{
						workingSet.readRegister(register, registerIdFunction)
					}
					for (register in instruction.destinationRegisters)
					{
						workingSet.writeRegister(register, registerIdFunction)
					}
				}
			}
			block.successorEdges().forEach { edge ->
				// Handle the phi instructions of the target here.  Create a
				// workingCopy for each edge.
				val workingCopy = UsedRegisters(workingSet)
				val targetBlock = edge.targetBlock()
				val predecessorIndex =
					targetBlock.predecessorEdges().indexOf(edge)
				if (predecessorIndex == -1)
				{
					System.err.println("Phi predecessor not found")
					throw AssertionError("Phi predecessor not found")
				}
				for (phiInTarget in targetBlock.instructions())
				{
					if (!phiInTarget.isPhi)
					{
						// All the phis are at the start of the block.
						break
					}
					val phiSource =
						phiInTarget.sourceRegisters[predecessorIndex]
					workingCopy.readRegister(phiSource, registerIdFunction)
					workingCopy.writeRegister(
						phiInTarget.destinationRegisters[0],
						registerIdFunction)
				}
				blocksToCheck.add(targetBlock to workingCopy)
			}
		}
	}

	/**
	 * Check that all registers have a unique definition.  This should only be
	 * called if the graph is expected to be in single-static-assignment form
	 * ([IS_SSA]).
	 */
	private fun checkUniqueRegisterDefinitions()
	{
		for (block in blocks)
		{
			for (instruction in block.instructions())
			{
				instruction.sourceRegisters.forEach { r ->
					assert(r.definitions().size == 1)
				}
				instruction.destinationRegisters.forEach { r ->
					assert(r.definitions().size == 1)
				}
			}
		}
	}

	/**
	 * Ensure each instruction that's an
	 * [entry point][L2Instruction.isEntryPoint] occurs at the start of a block.
	 */
	private fun checkEntryPoints()
	{
		blocks.forEach { block ->
			var anyNonPhi = false
			for (instruction in block.instructions())
			{
				if (instruction.isEntryPoint)
				{
					assert(!anyNonPhi) {
						"Entry point must be after phis"
					}
					// Also catch a second entry point.
					anyNonPhi = true
				}
				else if (!instruction.isPhi)
				{
					anyNonPhi = true
				}
			}
		}
	}

	/**
	 * Perform a basic sanity check on the instruction graph.
	 *
	 * @param interpreter
	 *   The current [Interpreter].
	 */
	private fun sanityCheck(interpreter: Interpreter)
	{
		if (shouldSanityCheck)
		{
			val before = AvailRuntimeSupport.captureNanos()
			checkBlocksAndInstructions()
			checkUniqueOperands()
			checkEdgesAndPhis()
			checkRegistersAreInitialized(L2Register<*>::uniqueValue)
			if (IS_SSA::class in generator.controlFlowGraph.state)
			{
				checkUniqueRegisterDefinitions()
			}
			checkEntryPoints()
			val after = AvailRuntimeSupport.captureNanos()
			sanityCheckStat.record(
				after - before, interpreter.interpreterIndex)
		}
	}

	/**
	 * Optimize the graph of instructions.
	 *
	 * @param interpreter
	 *   The current [Interpreter].
	 */
	fun optimize(interpreter: Interpreter)
	{
		try
		{
			sanityCheck(interpreter)

			for (phase in OptimizationPhase.entries)
			{
				val before = AvailRuntimeSupport.captureNanos()
				phase.run(this)
				val after = AvailRuntimeSupport.captureNanos()
				phase.stat.record(after - before, interpreter.interpreterIndex)
				sanityCheck(interpreter)
			}
		}
		catch (e: Throwable)
		{
			// Here's a good place for a breakpoint, to allow L2 translation to
			// restart, since the outer catch is already too late.
			System.err.println("Unrecoverable problem during optimization: $e")
			throw e
		}
	}

	companion object
	{
		/** Whether to sanity-check the graph between optimization steps. */
		var shouldSanityCheck = false

		/** Statistic for tracking the cost of sanity checks. */
		private val sanityCheckStat = Statistic(
			L2_OPTIMIZATION_TIME, "(Sanity check)")
	}
}
