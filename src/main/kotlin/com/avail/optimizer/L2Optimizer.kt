/*
 * L2Optimizer.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.optimizer

import com.avail.AvailRuntimeSupport
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2Operand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadOperand
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.interpreter.levelTwo.operation.L2_JUMP
import com.avail.interpreter.levelTwo.operation.L2_JUMP_BACK
import com.avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE
import com.avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE.sourceOfImmutable
import com.avail.interpreter.levelTwo.operation.L2_MOVE
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import com.avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind
import com.avail.optimizer.L2ControlFlowGraph.StateFlag
import com.avail.optimizer.values.L2SemanticValue
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport
import com.avail.utility.Strings.increaseIndentation
import com.avail.utility.cast
import com.avail.utility.mapToSet
import com.avail.utility.structures.EnumMap.Companion.enumMap
import java.util.ArrayDeque
import java.util.BitSet
import java.util.Collections
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
class L2Optimizer internal constructor(val generator: L2Generator)
{
	/** The [L2ControlFlowGraph] to optimize.  */
	private val controlFlowGraph: L2ControlFlowGraph =
		generator.controlFlowGraph

	/** The mutable list of blocks taken from the [controlFlowGraph].  */
	val blocks: MutableList<L2BasicBlock> = controlFlowGraph.basicBlockOrder

	/**
	 * Whether any [L2_VIRTUAL_CREATE_LABEL] instructions, or other
	 * placeholders, were replaced since the last time we cleared the flag.
	 */
	private var replacedAnyPlaceholders = false

	/** The register coloring algorithm.  */
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
		val blocksToVisit: Deque<L2BasicBlock> = ArrayDeque(blocks
			.filter { obj: L2BasicBlock? -> obj!!.isIrremovable })
		val reachableBlocks = mutableSetOf<L2BasicBlock?>()
		while (!blocksToVisit.isEmpty())
		{
			val block = blocksToVisit.removeLast()
			if (!reachableBlocks.contains(block))
			{
				reachableBlocks.add(block)
				val iterator = block.successorEdgesIterator()
				while (iterator.hasNext())
				{
					blocksToVisit.add(iterator.next().targetBlock())
				}
			}
		}
		val unreachableBlocks = blocks.toMutableSet()
		unreachableBlocks.removeAll(reachableBlocks)
		for (block in unreachableBlocks)
		{
			block.instructions().forEach { it.justRemoved() }
			block.instructions().clear()
		}
		val changed = blocks.retainAll(reachableBlocks)
		// See if any blocks no longer need to be a loop head.
		if (changed)
		{
			for (block in blocks)
			{
				if (block.isLoopHead
					&& block.predecessorEdgesCopy().stream()
						.noneMatch(L2PcOperand::isBackward))
				{
					// It's a loop head that has no back-edges pointing to it.
					block.isLoopHead = false
				}
			}
		}
		return changed
	}

	/**
	 * Remove any unnecessary instructions.  Answer true if any were removed.
	 *
	 * @param dataCouplingMode
	 *   The [DataCouplingMode] that chooses how to trace liveness.
	 * @return
	 *   Whether any dead instructions were removed or changed.
	 */
	private fun removeDeadInstructions(
		dataCouplingMode: DataCouplingMode
	): Boolean
	{
		val analyzer = DeadCodeAnalyzer(dataCouplingMode, controlFlowGraph)
		analyzer.analyzeReads()
		val liveInstructions: Set<L2Instruction?> = analyzer.liveInstructions()
		var anyRemoved = false
		for (block in blocks)
		{
			val iterator =
				block.instructions().listIterator()
			while (iterator.hasNext())
			{
				val instruction = iterator.next()
				if (!liveInstructions.contains(instruction))
				{
					anyRemoved = true
					val replacement =
						instruction.optionalReplacementForDeadInstruction()
					if (replacement === null)
					{
						iterator.remove()
						instruction.justRemoved()
					}
					else
					{
						iterator.set(replacement)
						instruction.justRemoved()
						if (replacement.operation() === L2_JUMP)
						{
							replacement.justInserted()
						}
					}
				}
			}
		}
		if (anyRemoved)
		{
			updateAllSemanticValuesOfReads()
			updateAllManifests()
		}
		return anyRemoved
	}

	/**
	 * Visit all manifests, retaining only the [L2Register]s that are written by
	 * live code along *all* of that manifest's histories. Loop edges may have
	 * to be visited multiple times to ensure convergence to a maximal fixed
	 * point.
	 */
	private fun updateAllManifests()
	{
		// Visit all L2WriteOperands, recording which L2Registers are written.
		val registersToKeep = mutableSetOf<L2Register>()
		blocks.forEach { it.instructions().forEach { ins ->
			ins.writeOperands().forEach {
				writeOperand: L2WriteOperand<*> ->
					registersToKeep.add(writeOperand.register()) }
				}
			}
		// Visit each edge's manifest.  In the manifest's definitions map's
		// values (which are lists of L2Registers), retain only those registers
		// for which writes were found above.
		blocks.forEach { it.successorEdgesIterator().forEachRemaining { edge ->
			edge.manifest().retainRegisters(registersToKeep)
			}
		}
	}

	/**
	 * Code has been removed, replaced, or moved.  As a consequence, some
	 * [L2ReadOperand]s may now be referring to [L2SemanticValue]s that are
	 * inaccessible at the point of read, via an [L2Register] that is still
	 * accessible.  Replace the [L2SemanticValue] of that read to one that's
	 * present in every [L2WriteOperand] that writes to the register.
	 *
	 * It should be the case that there will always be at least one common
	 * semantic value among all writes to a register.
	 */
	private fun updateAllSemanticValuesOfReads()
	{
		val favoredSemanticValuesMap =
			mutableMapOf<L2Register, L2SemanticValue>()
		for (block in blocks)
		{
			for (instruction in block.instructions())
			{
				if (instruction.operation().isPhi)
				{
					// Don't mess with the semantic values coming into a phi, as
					// they're always expected to mutually agree.
					continue
				}
				for (read in instruction.readOperands())
				{
					var favoredSemanticValue =
						favoredSemanticValuesMap[read.register()]
					if (favoredSemanticValue === null)
					{
						var intersection: MutableSet<L2SemanticValue>? = null
						for (write in read.register().definitions())
						{
							intersection?.retainAll(write.semanticValues())
								?: {
									intersection =
										write.semanticValues().toMutableSet()
								}()
						}
						assert(intersection !== null)
						assert(intersection!!.isNotEmpty())
						favoredSemanticValue = intersection!!.iterator().next()
						favoredSemanticValuesMap[read.register()] =
							favoredSemanticValue
					}
					read.updateSemanticValue(favoredSemanticValue)
				}
			}
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
	fun removeDeadCode(dataCouplingMode: DataCouplingMode)
	{
		// Removing instructions won't cause blocks to be inaccessible, so just
		// clean up unreachable blocks once at the start.
		removeUnreachableBlocks()
		if (!removeDeadInstructions(dataCouplingMode))
		{
			// No instructions were removed, so don't bother cleaning up the
			// manifests.
			return
		}
		// Clean up all manifests so that they only mention registers that
		// are guaranteed to have values at that point.
		val visibleRegisters =
			mutableMapOf<L2PcOperand, MutableSet<L2Register>>()
		val visibleSemanticValues =
			mutableMapOf<L2PcOperand, MutableSet<L2SemanticValue>>()
		blocks.forEach { b: L2BasicBlock ->
			b.predecessorEdgesDo { e: L2PcOperand ->
				visibleRegisters[e] = mutableSetOf()
				visibleSemanticValues[e] = mutableSetOf()
			}
		}
		// These two collections should maintain the same membership.
		val toVisitQueue: Deque<L2BasicBlock> = ArrayDeque(blocks)
		val toVisitSet = blocks.toMutableSet()
		while (toVisitQueue.isNotEmpty())
		{
			val block = toVisitQueue.removeFirst()!!
			toVisitSet.remove(block)
			val regs: MutableSet<L2Register>
			val values: MutableSet<L2SemanticValue>
			val predecessors =
				block.predecessorEdgesIterator()
			if (predecessors.hasNext())
			{
				val first = predecessors.next()
				regs = visibleRegisters[first]!!.toMutableSet()
				values = visibleSemanticValues[first]!!.toMutableSet()
				predecessors.forEachRemaining {
					regs.retainAll(visibleRegisters[it]!!)
					values.retainAll(visibleSemanticValues[it]!!)
				}
			}
			else
			{
				regs = mutableSetOf()
				values = mutableSetOf()
			}
			for (instruction in block.instructions())
			{
				if (!instruction.altersControlFlow())
				{
					instruction.writeOperands().forEach {
						regs.add(it.register())
						values.addAll(it.semanticValues())
					}
				}
				else
				{
					instruction.edgesAndPurposesDo { edge, purpose ->
						val regsForEdge: MutableSet<L2Register> =
							regs.toMutableSet()
						val valuesForEdge =
							values.toMutableSet()
						instruction.writesForPurposeDo(purpose!!) {
							regsForEdge.add(it.register())
							valuesForEdge.addAll(
								it.semanticValues())
						}
						var changed =
							visibleRegisters[edge]!!.addAll(regsForEdge)
						changed = changed or visibleSemanticValues[edge]!!
							.addAll(valuesForEdge)
						if (changed && toVisitSet.add(edge.targetBlock()))
						{
							toVisitQueue.add(edge.targetBlock())
						}
					}
				}
			}
		}
		// Now that we have the complete registers available at each edge,
		// narrow each edge's manifest accordingly.
		visibleRegisters.forEach { (edge, regs) ->
			edge.manifest().retainRegisters(regs)
		}
		visibleSemanticValues.forEach { (edge, values) ->
			edge.manifest().retainSemanticValues(values)
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
			if (sourceBlock.successorEdgesCount() > 1)
			{
				sourceBlock.successorEdgesDo { edge: L2PcOperand ->
					val targetBlock = edge.targetBlock()
					if (targetBlock.predecessorEdgesCount() > 1)
					{
						val newBlock =
							edge.splitEdgeWith(controlFlowGraph)
						// Add it somewhere that looks sensible for debugging,
						// although we'll order the blocks later.
						blocks.add(blocks.indexOf(targetBlock), newBlock)
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
		for (block in blocks)
		{
			block.predecessorEdgesDo { predecessor ->
				predecessor.alwaysLiveInRegisters.clear()
				predecessor.sometimesLiveInRegisters.clear()
			}
		}

		// The deque and the set maintain the same membership.
		val workQueue: Deque<L2BasicBlock> = ArrayDeque(blocks)
		val workSet = blocks.toMutableSet()
		while (!workQueue.isEmpty())
		{
			val block = workQueue.removeLast()
			workSet.remove(block)
			// Take the union of the outbound edges' sometimes-live registers.
			// Also find the intersection of those edges' always-live registers.
			val alwaysLive = mutableSetOf<L2Register>()
			if (block!!.successorEdgesCount() > 0)
			{
				// Before processing instructions in reverse order, the
				// always-live-in set will be the intersection of the successor
				// edges' always-live-in sets.  Pick any edge's always-live-in
				// set as the starting case, to be intersected with each edge's
				// set in the loop below.
				alwaysLive.addAll(
					block.successorEdgeAt(0).alwaysLiveInRegisters)
			}
			val sometimesLive = mutableSetOf<L2Register>()
			val successorsIterator =
				block.successorEdgesIterator()
			while (successorsIterator.hasNext())
			{
				val edge = successorsIterator.next()
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
				if (instruction.operation().isPhi)
				{
					// We've reached the phis at the start of the block.
					lastPhiIndex = i
					break
				}
				sometimesLive.removeAll(instruction.destinationRegisters())
				sometimesLive.addAll(instruction.sourceRegisters())
				alwaysLive.removeAll(instruction.destinationRegisters())
				alwaysLive.addAll(instruction.sourceRegisters())
			}

			// Add in the predecessor-specific live-in information for each edge
			// based on the corresponding positions inside phi instructions.
			val finalLastPhiIndex = lastPhiIndex
			var edgeIndex = 0
			block.predecessorEdgesDo { edge ->
				val edgeAlwaysLiveIn =
					alwaysLive.toMutableSet()
				val edgeSometimesLiveIn =
					sometimesLive.toMutableSet()
				// Add just the registers used along this edge.
				for (i in finalLastPhiIndex downTo 0)
				{
					val phiInstruction = instructions[i]
					val phiOperation: L2_PHI_PSEUDO_OPERATION<*, *, *> =
						phiInstruction.operation().cast()
					edgeSometimesLiveIn.removeAll(
						phiInstruction.destinationRegisters())
					edgeAlwaysLiveIn.removeAll(
						phiInstruction.destinationRegisters())
					val sources =
						phiOperation.sourceRegisterReads(phiInstruction)
					val source = sources[edgeIndex].register()
					edgeSometimesLiveIn.add(source)
					edgeAlwaysLiveIn.add(source)
				}
				val predecessorEdge =
					block.predecessorEdgeAt(edgeIndex)
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
	 * Try to move any side-effect-less defining instructions to later points in
	 * the control flow graph.  If such an instruction defines a register that's
	 * used in the same basic block, don't bother moving it.  Also don't attempt
	 * to move it if it's always-live-in at each successor block, since the
	 * point of moving it forward is to avoid inessential computations.
	 *
	 * So in the remaining case that the register is used in only some of the
	 * future paths, we attempt to move copies of the instruction into each
	 * successor that may require it.  Note that this can be *all* of the
	 * successors, if some of them are only maybe-live-in.
	 *
	 * This process is repeated until no more instructions are eligible to
	 * move forward.
	 *
	 * This requires edge-split form.  It does not preserve SSA.
	 */
	fun postponeConditionallyUsedValues()
	{
		var changed: Boolean
		do
		{
			changed = false
			for (block in blocks)
			{
				val registersConsumedLaterInBlock = mutableSetOf<L2Register>()
				val semanticValuesConsumedLaterInBlock =
					mutableSetOf<L2SemanticValue>()
				var dependentReadsLaterInBlock = 0
				var allWritesLaterInBlock = 0
				val instructions = block.instructions().toList()
				// This set is populated right away by the last instruction of
				// the block, which is the only place control flow can be
				// altered.
				val reachableTargetEdges = mutableSetOf<L2PcOperand>()
				for (i in instructions.indices.reversed())
				{
					val instruction = instructions[i]
					val operation = instruction.operation()
					if (operation.goesMultipleWays())
					{
						// Don't move anything past an instruction that goes
						// multiple ways from the same circumstance.  Otherwise,
						// any value produced in the first edge would be
						// redundant with (and maybe even disagree with, in the
						// case of identity) the values that would be produced
						// later in the second edge.
						break
					}
					val edgesToMoveThrough = successorEdgesToMoveThrough(
						instruction,
						registersConsumedLaterInBlock,
						semanticValuesConsumedLaterInBlock,
						dependentReadsLaterInBlock,
						allWritesLaterInBlock,
						reachableTargetEdges)
					if (edgesToMoveThrough !== null
						&& edgesToMoveThrough.none(L2PcOperand::isBackward))
					{
						assert(edgesToMoveThrough.isNotEmpty())
						changed = true
						var mutableRead: L2ReadBoxedOperand? = null
						var pair: Pair<Set<L2SemanticValue>, TypeRestriction>? =
							null
						if (operation === L2_MAKE_IMMUTABLE)
						{
							mutableRead = sourceOfImmutable(instruction)
							pair = mutableRead.findSourceInformation()
						}
						block.instructions().remove(instruction)
						instruction.justRemoved()
						val writesList = instruction.writeOperands()
						val writtenSet = writesList.mapToSet { it.register() }
						for (edge in edgesToMoveThrough)
						{
							if (writtenSet.isNotEmpty())
							{
								edge.manifest().forgetRegisters(writtenSet)
							}
							if (operation === L2_MAKE_IMMUTABLE)
							{
								edge.manifest().recordSourceInformation(
									mutableRead!!.register(),
									pair!!.first,
									pair.second)
							}
							val destinationBlock = edge.targetBlock()
							val newInstruction = L2Instruction(
								destinationBlock,
								operation,
								*instruction.operands())
							destinationBlock.insertInstruction(
								destinationBlock.indexAfterEntryPointAndPhis(),
								newInstruction)
							// None of the registers defined by the instruction
							// should be live-in any more at the edge.
							edge.sometimesLiveInRegisters.removeAll(
								newInstruction.destinationRegisters())
							edge.alwaysLiveInRegisters.removeAll(
								newInstruction.destinationRegisters())
							edge.sometimesLiveInRegisters.addAll(
								newInstruction.sourceRegisters())
							edge.alwaysLiveInRegisters.addAll(
								newInstruction.sourceRegisters())
						}
						// Erase its writes in every path at and after the edges
						// that we *didn't* move it to.  We don't have to worry
						// about hitting a block that does care about the value,
						// because we already know it was never live-in anywhere
						// after this edge.
						reachableTargetEdges.forEach { edge: L2PcOperand ->
							if (!edgesToMoveThrough.contains(edge))
							{
								edge.forgetRegistersInManifestsRecursively(
									writtenSet)
							}
						}

						// We moved the instruction *just* out of the block, so
						// when we move prior instructions, they'll still end up
						// before this one.  Therefore, don't track its reads.
						// Similarly, its writes have also moved past where any
						// new instructions might get moved.
					}
					else
					{
						// The instruction stayed where it was.  Record the
						// registers or semantic values that it consumed, to pin
						// any prior instructions that provide those values,
						// since they also can't be moved out of the block.
						registersConsumedLaterInBlock.addAll(
							instruction.sourceRegisters())
						semanticValuesConsumedLaterInBlock.addAll(
							instruction.readOperands().map { it.semanticValue() })
						// It might read values that prior instructions write,
						// so we should prevent those instructions from moving.
						dependentReadsLaterInBlock =
							dependentReadsLaterInBlock or
								operation.readsHiddenVariablesMask
						// It might overwrite hidden values needed by prior
						// instructions, so prevent those readers from moving.
						allWritesLaterInBlock = allWritesLaterInBlock or
							operation.writesHiddenVariablesMask
						// The last instruction of the block can alter the
						// control flow, so capture its edges.
						reachableTargetEdges.addAll(instruction.targetEdges())
					}
				}
			}
			if (changed)
			{
				updateAllManifests()
				computeLivenessAtEachEdge()
			}
		}
		while (changed)
	}

	/**
	 * Find any remaining occurrences of [L2_VIRTUAL_CREATE_LABEL], or any other
	 * [L2Instruction] using an [L2Operation] that says it
	 * [L2Operation.isPlaceholder].
	 *
	 * Replace the instruction with code it produces via
	 * [L2Generator.replaceInstructionByGenerating].
	 *
	 * If any placeholder instructions were found and replaced, set
	 * [replacedAnyPlaceholders].
	 */
	fun replacePlaceholderInstructions()
	{
		replacedAnyPlaceholders = false
		// Since placeholder expansions can introduce new basic blocks, it's
		// best to start the search at the beginning again after a substitution.
		outer@ while (true)
		{
			for (block in blocks)
			{
				for (instruction in block.instructions())
				{
					if (instruction.operation().isPlaceholder)
					{
						generator.replaceInstructionByGenerating(instruction)
						computeLivenessAtEachEdge()
						replacedAnyPlaceholders = true
						continue@outer
					}
				}
			}
			// There were no replacements this round, so we're done.
			break
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
		insertPhiMoves_private<L2Register>()
	}

	/**
	 * For every phi operation, insert a move at the end of the block that leads
	 * to it.  Because of our version of edge splitting, that block always
	 * contains just a jump.  The CFG will no longer be in SSA form, because the
	 * phi variables will have multiple defining instructions (the moves).
	 *
	 * Also eliminate the phi functions.
	 *
	 * This function requires a bogus type parameter to get around a type
	 * deduction bug in Kotlin.
	 */
	private fun <R: L2Register> insertPhiMoves_private()
	{
		for (block in blocks)
		{
			val instructionIterator = block.instructions().iterator()
			while (instructionIterator.hasNext())
			{
				val instruction = instructionIterator.next()
				if (!instruction.operation().isPhi)
				{
					// Phi functions are always at the start, so we must be past
					// them, if any.
					break
				}
				val phiOperation: L2_PHI_PSEUDO_OPERATION<R, *, *> =
					instruction.operation().cast()
				val phiSources = phiOperation.sourceRegisterReads(instruction)
				val fanIn = block.predecessorEdgesCount()
				assert(fanIn == phiSources.size)
				val targetWriter: L2WriteOperand<*> =
					phiOperation.destinationRegisterWrite(instruction)
				// Insert a non-SSA move in each predecessor block.
				for (i in 0 until fanIn)
				{
					val edge = block.predecessorEdgeAt(i)
					val predecessor = edge.sourceBlock()
					val instructions =
						predecessor.instructions()
					assert(predecessor.finalInstruction().operation()
							   .isUnconditionalJump)
					val sourceRead = phiSources[i]
					val move = L2Instruction(
						predecessor,
						phiOperation.moveOperation,
						sourceRead,
						targetWriter.clone())
					predecessor.insertInstruction(
						instructions.size - 1, move)
					if (edge.isBackward)
					{
						edge.manifest().replaceDefinitions(
							phiOperation.moveOperation.destinationOf(move))
					}
					else
					{
						edge.manifest().recordDefinition(
							phiOperation.moveOperation.destinationOf(move))
					}
					if (edge.forcedClampedEntities !== null)
					{
						// Replace the semantic value(s) and register in the
						// clamped set of entities, if present.
						val clamped = edge.forcedClampedEntities
						if (clamped!!.remove(sourceRead.semanticValue()))
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
		colorer = L2RegisterColorer(controlFlowGraph)
		colorer!!.computeInterferenceGraph()
	}

	/**
	 * For each [L2_MOVE] instruction, if the register groups associated with
	 * the source and destination registers don't have an interference edge
	 * between them then merge the groups together.  The resulting merged group
	 * should have interferences with each group that the either the source
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
		val byKindAndIndex =
			enumMap<RegisterKind, MutableMap<Int, L2Register>>(
				RegisterKind.values())
		val remap: MutableMap<L2Register, L2Register> = mutableMapOf()
		// Also collect all the old registers.
		val oldRegisters = mutableSetOf<L2Register>()
		val action: (L2Register) -> Unit = { reg: L2Register ->
			remap[reg] = byKindAndIndex
				.getOrPut(reg.registerKind()) { mutableMapOf() }
				.computeIfAbsent(
					reg.finalIndex()) { reg.copyAfterColoring() }
			oldRegisters.add(reg)
		}
		blocks.forEach { block->
			block.instructions().forEach { instruction ->
				instruction.sourceRegisters().forEach { action.invoke(it) }
				instruction.destinationRegisters().forEach{ action.invoke(it) }
			}
		}
		// Actually remap every register.
		blocks.forEach { block ->
			block.instructions().forEach { it.replaceRegisters(remap) }
		}
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
				if (instruction.operation().isMove
					&& instruction.sourceRegisters()[0].finalIndex()
					== instruction.destinationRegisters()[0].finalIndex())
				{
					iterator.remove()
					instruction.justRemoved()
				}
			}
		}
	}

	/**
	 * Any control flow edges that land on jumps should be redirected to the
	 * ultimate target of the jump, taking into account chains of jumps.
	 *
	 * Don't adjust jumps that land on a jump inside a loop head block.
	 */
	fun adjustEdgesLeadingToJumps()
	{
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
				val jumpEdge: L2PcOperand
				jumpEdge = if (soleInstruction.operation() === L2_JUMP)
				{
					L2_JUMP.jumpTarget(soleInstruction)
				}
				else if (soleInstruction.operation() === L2_JUMP_BACK)
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
				for (inEdge in block.predecessorEdgesCopy())
				{
					changed = true
					inEdge.switchTargetBlockNonSSA(jumpTarget, isBackward)
				}
				assert(block.predecessorEdgesCount() == 0)
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
			countdowns[block] = AtomicInteger(block.predecessorEdgesCount())
		}
		val order = mutableListOf<L2BasicBlock>()
		assert(blocks[0].predecessorEdgesCount() == 0)
		val zeroed: Deque<L2BasicBlock> = ArrayDeque()
		for (i in blocks.indices.reversed())
		{
			if (blocks[i].predecessorEdgesCount() == 0)
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
				val iterator =
					block!!.successorEdgesIterator()
				while (iterator.hasNext())
				{
					val edge = iterator.next()
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
					if (value.get() < key.predecessorEdgesCount())
					{
						victim = key
						break
					}
				}
				// No remaining block has had a predecessor placed.  Pick a
				// block at random.
				if (victim === null)
				{
					victim = countdowns.keys.iterator().next()
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
			register: L2Register,
			registerIdFunction: (L2Register) -> Int)
		{
			assert(liveRegistersByKind[register.registerKind().ordinal]
			   .get(registerIdFunction.invoke(register)))
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
			register: L2Register,
			registerIdFunction: (L2Register) -> Int)
		{
			liveRegistersByKind[register.registerKind().ordinal]
				.set(registerIdFunction.invoke(register))
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

		/** Create an instance with no tracking information.  */
		internal constructor()
		{
			val kinds = RegisterKind.values()
			liveRegistersByKind = Array(kinds.size) { BitSet() }
		}

		/**
		 * Duplicate an existing instance.
		 *
		 * @param original
		 *   The existing instance to duplicate.
		 */
		internal constructor(original: UsedRegisters)
		{
			val kinds = RegisterKind.values()
			liveRegistersByKind = Array(kinds.size)
			{
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
				append(increaseIndentation(
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
		val uses =
			mutableMapOf<L2Register, MutableSet<L2ReadOperand<*>>>()
		val definitions =
			mutableMapOf<L2Register, MutableSet<L2WriteOperand<*>>>()
		blocks.forEach { block ->
			block.instructions().forEach { instruction ->
				instruction.assertHasBeenEmitted()
				instruction.readOperands().forEach {
					uses.computeIfAbsent(it.register()) { mutableSetOf() }.add(it)
				}
				instruction.writeOperands().forEach {
					definitions.computeIfAbsent(it.register()) { mutableSetOf() }
						.add(it)
				}
			}
		}
		val mentionedRegs = uses.keys.toMutableSet()
		mentionedRegs.addAll(definitions.keys)
		val myEmptySet = setOf<L2ReadOperand<*>>()
		for (reg in mentionedRegs)
		{
			assert(uses.getOrDefault(reg, myEmptySet) == reg.uses())
			assert(
				definitions.getOrDefault(reg, myEmptySet) == reg.definitions())
		}
	}

	/**
	 * Ensure all instructions' operands occur only once, including within
	 * vector operands.
	 */
	private fun checkUniqueOperands()
	{
		val allOperands = mutableSetOf<L2Operand>()
		blocks.forEach { block: L2BasicBlock ->
			block.instructions().forEach { instruction: L2Instruction ->
				instruction.operandsDo { operand ->
					val added = allOperands.add(operand)
					assert(added)
					if (operand is L2ReadVectorOperand<*, *>)
					{
						val vector = operand
							.cast<L2Operand?, L2ReadVectorOperand<*, *>>()
						vector.elements().forEach {
							val ok = allOperands.add(it)
							assert(ok)
						}
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
				assert(!instruction.operation().isPhi
					   || instruction.sourceRegisters().size
					   == block.predecessorEdgesCount())
				allEdgesFromBlock.addAll(instruction.targetEdges())
			}
			assert(block.successorEdgesCopy() == allEdgesFromBlock)
			assert(allEdgesFromBlock.toSet()
			   == block.successorEdgesCopy().toSet())
			block.successorEdgesDo { edge: L2PcOperand ->
				assert(edge.sourceBlock() == block)
				val targetBlock = edge.targetBlock()
				assert(!edge.isBackward || targetBlock.isLoopHead)
				assert(blocks.contains(targetBlock))
				assert(targetBlock.predecessorEdgesCopy().contains(edge))
			}
			// Also check incoming edges.
			block.predecessorEdgesDo { inEdge: L2PcOperand ->
				assert(inEdge.targetBlock() == block)
				val predecessorBlock = inEdge.sourceBlock()
				assert(blocks.contains(predecessorBlock))
				assert(predecessorBlock.successorEdgesCopy().contains(inEdge))
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
		registerIdFunction: (L2Register) -> Int)
	{
		val blocksToCheck: Deque<Pair<L2BasicBlock, UsedRegisters>> =
			ArrayDeque()
		blocksToCheck.add(Pair(blocks[0], UsedRegisters()))
		val inSets: MutableMap<L2BasicBlock, UsedRegisters> = mutableMapOf()
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
				if (!instruction.operation().isPhi)
				{
					for (register in instruction.sourceRegisters())
					{
						workingSet.readRegister(register, registerIdFunction)
					}
					for (register in instruction.destinationRegisters())
					{
						workingSet.writeRegister(register, registerIdFunction)
					}
				}
			}
			val iterator = block.successorEdgesIterator()
			while (iterator.hasNext())
			{
				// Handle the phi instructions of the target here.  Create a
				// workingCopy for each edge.
				val edge = iterator.next()
				val workingCopy = UsedRegisters(workingSet)
				val targetBlock = edge.targetBlock()
				val predecessorIndex =
					targetBlock.predecessorEdgesCopy().indexOf(edge)
				if (predecessorIndex == -1)
				{
					System.err.println("Phi predecessor not found")
					assert(false) { "Phi predecessor not found" }
				}
				for (phiInTarget in targetBlock.instructions())
				{
					if (!phiInTarget.operation().isPhi)
					{
						// All the phis are at the start of the block.
						break
					}
					val phiSource =
						phiInTarget.sourceRegisters()[predecessorIndex]
					workingCopy.readRegister(phiSource, registerIdFunction)
					workingCopy.writeRegister(
						phiInTarget.destinationRegisters()[0],
						registerIdFunction)
				}
				blocksToCheck.add(Pair(targetBlock, workingCopy))
			}
		}
	}

	/**
	 * Ensure each instruction that's an
	 * [entry point][L2Instruction.isEntryPoint] occurs at the start of a block.
	 */
	private fun checkEntryPoints()
	{
		blocks.forEach { b: L2BasicBlock ->
			b.instructions().forEach {
				assert(!it.isEntryPoint || b.instructions()[0] == it) }
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
			checkRegistersAreInitialized(L2Register::uniqueValue)
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
			for (phase in OptimizationPhase.values())
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
			System.err.println("Unrecoverable problem during optimization.")
			throw e
		}
	}

	companion object
	{
		/** Whether to sanity-check the graph between optimization steps.  */
		var shouldSanityCheck = true //TODO false;

		/** Statistic for tracking the cost of sanity checks.  */
		private val sanityCheckStat = Statistic(
			"(Sanity check)",
			StatisticReport.L2_OPTIMIZATION_TIME)

		/**
		 * If this instruction can be moved/duplicated into one or more successor
		 * blocks, answer a [List] of those blocks.  Otherwise answer `null`.
		 *
		 * @param instruction
		 *   The instruction to analyze.
		 * @param registersConsumedLaterInBlock
		 *   The set of [L2Register]s which are consumed by instructions after
		 *   the given one within the same [L2BasicBlock].  If the given
		 *   instruction produces an output consumed by a later instruction, the
		 *   given instruction cannot be moved forward out of its basic block.
		 * @param semanticValuesConsumedLaterInBlock
		 *   The set of [L2SemanticValue]s which are consumed by instructions
		 *   after the given one within the same block.  If the given
		 *   instruction produces an output consumed by a later instruction, the
		 *   given instruction cannot be moved forward out of its basic block.
		 * @param readMaskLaterInBlock
		 *   A mask of [L2Operation.HiddenVariable] dependencies that
		 *   instructions later in the block will read.  Writes to these hidden
		 *   variables must not be moved past those reads, or the reads won't be
		 *   able to access the values.
		 * @param writeMaskLaterInBlock
		 *   A mask of [L2Operation.HiddenVariable] dependencies that
		 *   instructions later in the block will write.  Reads of these hidden
		 *   variables must not be moved past those writes, or the reads won't
		 *   be able to access the values.
		 * @param candidateTargetEdges
		 *   The edges that this instruction might be moved through.  These are
		 *   all outbound edges in instructions that occur later in the current
		 *   basic block.
		 * @return
		 *   The successor [L2PcOperand]s through which the instruction can be
		 *   moved, or `null` if the instruction should not move.
		 */
		private fun successorEdgesToMoveThrough(
			instruction: L2Instruction?,
			registersConsumedLaterInBlock: Set<L2Register?>,
			semanticValuesConsumedLaterInBlock: Set<L2SemanticValue?>,
			readMaskLaterInBlock: Int,
			writeMaskLaterInBlock: Int,
			candidateTargetEdges: Set<L2PcOperand>): Set<L2PcOperand>?
		{
			if (instruction!!.altersControlFlow()
				|| instruction.operation().isPhi
				|| instruction.isEntryPoint)
			{
				return null
			}
			if (instruction.hasSideEffect())
			{
				return null
			}
			val writeMask = instruction.operation().writesHiddenVariablesMask
			if (writeMask and readMaskLaterInBlock != 0
				|| writeMask and writeMaskLaterInBlock != 0)
			{
				// Either the instruction writes something that a later instruction
				// reads, or there are multiple writes that should maintain their
				// order in case a later block has a dependent read.  Don't move it.
				return null
			}
			val readMask = instruction.operation().readsHiddenVariablesMask
			if (readMask and writeMaskLaterInBlock != 0)
			{
				// The instruction reads something that a later instruction would
				// clobber.  Don't move it.
				return null
			}
			val written: List<L2Register?> = instruction.destinationRegisters()
			val writtenSemanticValues = mutableListOf<L2SemanticValue?>()
			instruction.writeOperands().forEach {
				writtenSemanticValues.addAll(it.semanticValues())
			}
			assert(written.isNotEmpty()) {
				("Every instruction should either have side effects or write "
				 + "to at least one register")
			}
			if (!Collections.disjoint(written, registersConsumedLaterInBlock))
			{
				// A later instruction in the current basic block consumes one of
				// the registers produced by the given instruction, so we can't move
				// the given instruction into later blocks.
				return null
			}
			if (!Collections.disjoint(
					writtenSemanticValues, semanticValuesConsumedLaterInBlock))
			{
				// A later instruction in the current basic block consumes one of
				// the semantic values produced by the given instruction, so we
				// can't move the given instruction into later blocks.
				return null
			}
			if (candidateTargetEdges.size == 1)
			{
				// There's only one successor edge.  Since the CFG is in edge-split
				// form, the successor might have multiple predecessors.  Don't move
				// across the edge in that case, since it may cause the instruction
				// to run in situations that it doesn't need to.
				//
				// TODO When code splitting is eventually implemented, it should
				// clean up this case by duplicating the successor block just for
				// this edge.
				val successorEdge =
					candidateTargetEdges.iterator().next()
				val successor = successorEdge.targetBlock()
				if (successor.predecessorEdgesCount() > 1)
				{
					return null
				}
			}
			val destinations = mutableSetOf<L2PcOperand>()
			var shouldMoveInstruction = false
			for (edge in candidateTargetEdges)
			{
				val targetBlock = edge.targetBlock()
				assert(targetBlock.predecessorEdgesCount() == 1) {
					"CFG is not in edge-split form"
				}
				if (!edge.alwaysLiveInRegisters.containsAll(written))
				{
					// There's an edge that it shouldn't flow to.
					shouldMoveInstruction = true
				}
				if (!Collections.disjoint(
						edge.sometimesLiveInRegisters, written))
				{
					// There's an edge that's only sometimes live-in.
					shouldMoveInstruction = true
					destinations.add(edge)
				}
			}
			if (!shouldMoveInstruction)
			{
				// It was always-live-in for every successor.
				return null
			}
			// Due to previous code motion, the destinations list might be empty.
			// Skip the move, and let dead code elimination get it instead.
			return if (destinations.isEmpty())
			{
				null
			}
			else destinations
		}
	}
}
