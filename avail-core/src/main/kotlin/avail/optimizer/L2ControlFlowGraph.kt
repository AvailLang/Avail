/*
 * L2ControlFlowGraph.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import avail.descriptor.functions.A_Continuation
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operation.L2_INVOKE
import avail.interpreter.levelTwo.operation.L2_INVOKE_CONSTANT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2ControlFlowGraph.StateFlag.IS_SSA
import avail.utility.Strings.increaseIndentation
import java.lang.StringBuilder
import java.util.Collections
import kotlin.reflect.KClass

/**
 * This is a control graph. The vertices are [L2BasicBlock]s, which are
 * connected via their successor and predecessor lists.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2ControlFlowGraph
{
	/**
	 * Flags that indicate the current state of the graph.
	 */
	abstract class StateFlag
	{
		/**
		 * Whether the control flow graph is in static single-assignment form.
		 * In this form, every register has a single instruction that writes to
		 * it.  Where control flow merges, the target [L2BasicBlock] can contain
		 * "phi" ([L2_PHI_PSEUDO_OPERATION]) instructions.  Such an instruction
		 * writes to its output register the value corresponding to the numbered
		 * predecessor edge by which the block was reached.
		 */
		class IS_SSA : StateFlag()

		/**
		 * Whether the control flow graph is in edge-split form, which ensures
		 * that no edge both leads from a node with multiple successor edges and
		 * leads to a node with multiple predecessor edges.
		 */
		@Suppress("unused")
		class IS_EDGE_SPLIT : StateFlag()

		/**
		 * Indicates that every [L2_PHI_PSEUDO_OPERATION] has been replaced by
		 * moves to the same [L2Register] along each (split) incoming edge.
		 */
		@Suppress("unused")
		class HAS_ELIMINATED_PHIS : StateFlag()
	}

	/**
	 * The current state of the graph.  New control flow graphs are expected to
	 * be in SSA form.
	 */
	val state = mutableSetOf<KClass<out StateFlag>>(IS_SSA::class)

	/**
	 * Set each of the specified [StateFlag]s.
	 *
	 * @param flags
	 *   The collection of [StateFlag]s to add.
	 */
	fun set(flags: Collection<KClass<out StateFlag>>)
	{
		state.addAll(flags)
	}

	/**
	 * Clear each of the specified [StateFlag]s.
	 *
	 * @param flags
	 *   The collection of [StateFlag]s to remove.
	 */
	fun clear(flags: Collection<KClass<out StateFlag>>)
	{
		state.removeAll(flags)
	}

	/**
	 * Assert that each of the specified [StateFlag]s has been set.
	 *
	 * @param flags
	 *   The collection of [StateFlag]s to check.
	 */
	fun check(flags: Collection<KClass<out StateFlag>>)
	{
		assert(state.containsAll(flags))
	}

	/**
	 * Assert that each of the specified [StateFlag]s has been cleared.
	 *
	 * @param flags
	 *   The collection of [StateFlag]s to check for absence.
	 */
	fun checkNot(flags: Collection<KClass<out StateFlag>>)
	{
		assert(Collections.disjoint(state, flags))
	}

	/**
	 * [L2BasicBlock]s can be grouped into zones for better visualization of the
	 * control flow graph by the [L2ControlFlowGraphVisualizer]. This class is
	 * instantiated from the [ZoneType.createZone] factory method.
	 *
	 * @property zoneType
	 *   The nature of this zone.
	 * @property zoneName
	 *   The optional name of this zone, which is not necessarily unique.
	 *
	 * @constructor
	 * Create a now Zone with the given optional name.  Clients should use
	 * the [ZoneType.createZone] factor method.
	 *
	 * @param zoneType
	 *   The [ZoneType] of this zone.
	 * @param zoneName
	 *   The zone's optional (non-unique) descriptive name.
	 */
	class Zone internal constructor(
		val zoneType: ZoneType,
		val zoneName: String)

	/**
	 * A categorization of kinds of [Zone]s that will be shown as  subgraphs
	 * (clusters) by the [L2ControlFlowGraphVisualizer].
	 *
	 * @property color
	 *   A string indicating the boundary color for this zone.
	 * @property bgcolor
	 *   A string indicating the fill color for this zone.
	 *
	 * @constructor
	 * Create a `ZoneType` enum value.  Capture the boundary color and
	 * fill color, for use by the [L2ControlFlowGraphVisualizer].
	 *
	 * @param color
	 *   A string indicating the boundary color.
	 * @param bgcolor
	 *   A string indicating the fill color.
	 */
	enum class ZoneType constructor(val color: String, val bgcolor: String)
	{
		/** A zone used for reifying to handle a fiber interrupt. */
		BEGIN_REIFICATION_FOR_INTERRUPT("#c0c0ff/505090", "#d8d8ff/282850"),

		/**
		 * A zone used for reifying the call stack to create an [A_Continuation]
		 * to be used as a [label][L1Operation.L1Ext_doPushLabel].
		 */
		BEGIN_REIFICATION_FOR_LABEL("#e0d090/604010", "#ffe0b0/302010"),

		/**
		 * The target of an on-reification branch from an [L2_INVOKE] or
		 * [L2_INVOKE_CONSTANT_FUNCTION].  A reification is already in progress
		 * when this zone is entered, and the invoke logic ensures the callers
		 * have already been reified by the time this zone runs.
		 */
		PROPAGATE_REIFICATION_FOR_INVOKE("#c0e0c0/10a010", "#e0ffe0/103010");

		/**
		 * Create a new [Zone] of this type, with an optional descriptive
		 * (non-unique) name.
		 *
		 * @param zoneName
		 *   The optional descriptive name of the [Zone].
		 * @return
		 *   A new [Zone].
		 */
		fun createZone(zoneName: String): Zone = Zone(this, zoneName)
	}

	/**
	 * The basic blocks of the graph.  They're either in the order they were
	 * generated, or in a suitable order for final L2 instruction emission.
	 */
	val basicBlockOrder = mutableListOf<L2BasicBlock>()

	/**
	 * Begin code generation in the given block.
	 *
	 * @param block
	 *   The [L2BasicBlock] in which to start generating [L2Instruction]s.
	 */
	fun startBlock(block: L2BasicBlock)
	{
		assert(block.instructions().isEmpty())
		assert(!basicBlockOrder.contains(block))
		if (block.isIrremovable || block.predecessorEdges().isNotEmpty())
		{
			basicBlockOrder.add(block)
		}
	}

	/**
	 * Collect the list of all distinct [L2Register]s assigned anywhere within
	 * this control flow graph.
	 *
	 * @return
	 *   A [List] of [L2Register]s without repetitions.
	 */
	fun allRegisters(): List<L2Register>
	{
		val allRegisters = mutableSetOf<L2Register>()
		for (block in basicBlockOrder)
		{
			for (instruction in block.instructions())
			{
				allRegisters.addAll(instruction.destinationRegisters())
			}
		}
		return allRegisters.toMutableList()
	}

	/**
	 * Remove all [L2BasicBlock]s, moving them to another [L2ControlFlowGraph]
	 * that is initially empty.
	 */
	fun evacuateTo(destinationControlFlowGraph: L2ControlFlowGraph)
	{
		assert(destinationControlFlowGraph.basicBlockOrder.isEmpty())
		destinationControlFlowGraph.basicBlockOrder.addAll(basicBlockOrder)
		basicBlockOrder.clear()
	}

	override fun toString(): String = buildString {
		for (block in basicBlockOrder)
		{
			append(block.name())
			append(":\n")
			block.predecessorEdges().forEach { edge: L2PcOperand? ->
				append("\t\tFrom: ")
				append(edge!!.sourceBlock().name())
				append("\n\t\t\t[")
				append("always live-in: ")
				append(edge.alwaysLiveInRegisters)
				append(", sometimes live-in: ")
				append(edge.sometimesLiveInRegisters)
				append("]\n")
			}
			for (instruction in block.instructions())
			{
				append("\t")
				append(increaseIndentation(instruction.toString(), 1))
				append("\n")
			}
			append("\n")
		}
	}

	/**
	 * Produce the final list of instructions.  Should only be called after all
	 * optimizations have been performed.
	 *
	 * @param instructions
	 *   The list of instructions to populate.
	 */
	fun generateOn(instructions: MutableList<L2Instruction>)
	{
		for (block in basicBlockOrder)
		{
			block.generateOn(instructions)
		}
	}

	/**
	 * Answer a visualization of this `L2ControlFlowGraph`. This is a
	 * debug method, intended to be called via evaluation during debugging.
	 *
	 * @return
	 *   The requested visualization.
	 */
	@Suppress("unused")
	fun visualize() = StringBuilder().let { builder ->
		L2ControlFlowGraphVisualizer(
			"«control flow graph»",
			"«chunk»",
			80,
			this,
			true,
			true,
			true,
			builder
		).visualize()
		builder.toString()
	}

	/**
	 * Answer a visualization of this `L2ControlFlowGraph`. This is a
	 * debug method, intended to be called via evaluation during debugging.
	 *
	 * @return
	 *   The requested visualization.
	 */
	fun simplyVisualize() = StringBuilder().let { builder ->
		L2ControlFlowGraphVisualizer(
			"«SIMPLE control flow graph»",
			"«chunk»",
			80,
			this,
			false,
			false,
			false,
			builder
		).visualize()
		builder.toString()
	}
}
