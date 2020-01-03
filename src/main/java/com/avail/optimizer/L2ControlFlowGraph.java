/*
 * L2ControlFlowGraph.java
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

package com.avail.optimizer;

import com.avail.descriptor.A_Continuation;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operation.L2_INVOKE;
import com.avail.interpreter.levelTwo.operation.L2_INVOKE_CONSTANT_FUNCTION;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2Register;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.avail.utility.Strings.increaseIndentation;

/**
 * This is a control graph.  The vertices are {@link L2BasicBlock}s, which are
 * connected via their successor and predecessor lists.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2ControlFlowGraph
{
	/**
	 * Flags that indicate the current state of the graph.
	 */
	public enum StateFlags
	{
		/**
		 * Indicates that every {@link L2_PHI_PSEUDO_OPERATION} has been
		 * replaced by moves to the same {@link L2Register} along each (split)
		 * incoming edge.
		 */
		HasEliminatedPhis;
	}

	/** The current state of the graph. */
	public final EnumSet<StateFlags> state =
		EnumSet.noneOf(StateFlags.class);

	/**
	 * {@link L2BasicBlock}s can be grouped into zones for better visualization
	 * of the control flow graph by the {@link L2ControlFlowGraphVisualizer}.
	 * This class is instantiated from the {@link ZoneType#createZone(String)}
	 * factory method.
	 */
	public static class Zone
	{
		/** The nature of this zone. */
		final ZoneType zoneType;

		/** The optional name of this zone, which is not necessarily unique. */
		final @Nullable String zoneName;

		/**
		 * Create a now Zone with the given optional name.  Clients should use
		 * the {@link ZoneType#createZone(String)} factor method.
		 *
		 * @param zoneType The {@link ZoneType} of this zone.
		 * @param zoneName The zone's optional (non-unique) descriptive name.
		 */
		Zone (
			final ZoneType zoneType,
			final @Nullable String zoneName)
		{
			this.zoneType = zoneType;
			this.zoneName = zoneName;
		}
	}

	/**
	 * A categorization of kinds of {@link Zone}s that will be shown as
	 * subgraphs (clusters) by the {@link L2ControlFlowGraphVisualizer}.
	 */
	public enum ZoneType
	{
		/** A zone used for reifying to handle a fiber interrupt. */
		BEGIN_REIFICATION_FOR_INTERRUPT("#c0c0ff/505090", "#d8d8ff/282850"),

		/**
		 * A zone used for reifying the call stack to create an
		 * {@link A_Continuation} to be used as a
		 * {@linkplain L1Operation#L1Ext_doPushLabel label}.
		 */
		BEGIN_REIFICATION_FOR_LABEL("#e0d090/604010", "#ffe0b0/302010"),

		/**
		 * The target of an on-reification branch from an {@link L2_INVOKE} or
		 * {@link L2_INVOKE_CONSTANT_FUNCTION}.  A reification is already in
		 * progress when this zone is entered, and the invoke logic ensures the
		 * callers have already been reified by the time this zone runs.
		 */
		PROPAGATE_REIFICATION_FOR_INVOKE("#c0e0c0/10a010", "#e0ffe0/103010");

		/** A string indicating the boundary color for this zone. */
		public final String color;

		/** A string indicating the fill color for this zone. */
		public final String bgcolor;

		/**
		 * Create a {@code ZoneType} enum value.  Capture the boundary color and
		 * fill color, for use by the {@link L2ControlFlowGraphVisualizer}.
		 *
		 * @param color A string indicating the boundary color.
		 * @param bgcolor A string indicating the fill color.
		 */
		ZoneType(final String color, final String bgcolor)
		{
			this.color = color;
			this.bgcolor = bgcolor;
		}

		/**
		 * Create a new {@link Zone} of this type, with an optional descriptive
		 * (non-unique) name.
		 *
		 * @param zoneName The optional descriptive name of the {@link Zone}.
		 * @return A new {@link Zone}.
		 */
		public final Zone createZone (final @Nullable String zoneName)
		{
			return new Zone(this, zoneName);
		}
	}


	/**
	 * The basic blocks of the graph.  They're either in the order they were
	 * generated, or in a suitable order for final L2 instruction emission.
	 */
	public final List<L2BasicBlock> basicBlockOrder = new ArrayList<>();

	/**
	 * Begin code generation in the given block.
	 *
	 * @param block
	 *        The {@link L2BasicBlock} in which to start generating {@link
	 *        L2Instruction}s.
	 */
	public void startBlock (final L2BasicBlock block)
	{
		assert block.instructions().isEmpty();
		assert !basicBlockOrder.contains(block);
		if (block.isIrremovable() || block.predecessorEdgesCount() > 0)
		{
			basicBlockOrder.add(block);
		}
	}

	/**
	 * Collect the list of all distinct {@link L2Register}s assigned anywhere
	 * within this control flow graph.
	 *
	 * @return A {@link List} of {@link L2Register}s without repetitions.
	 */
	public List<L2Register> allRegisters ()
	{
		final Set<L2Register> allRegisters = new HashSet<>();
		for (final L2BasicBlock block : basicBlockOrder)
		{
			for (final L2Instruction instruction : block.instructions())
			{
				allRegisters.addAll(instruction.destinationRegisters());
			}
		}
		return new ArrayList<>(allRegisters);
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		for (final L2BasicBlock block : basicBlockOrder)
		{
			builder.append(block.name());
			builder.append(":\n");
			final Iterator<L2PcOperand> iterator =
				block.predecessorEdgesIterator();
			while (iterator.hasNext())
			{
				final L2PcOperand edge = iterator.next();
				builder
					.append("\t\tFrom: ")
					.append(edge.sourceBlock().name())
					.append("\n\t\t\t[")
					.append("always live-in: ")
					.append(edge.alwaysLiveInRegisters)
					.append(", sometimes live-in: ")
					.append(edge.sometimesLiveInRegisters)
					.append("]\n");
			}
			for (final L2Instruction instruction : block.instructions())
			{
				builder.append("\t");
				builder.append(increaseIndentation(instruction.toString(), 1));
				builder.append("\n");
			}
			builder.append("\n");
		}
		return builder.toString();
	}

	/**
	 * Produce the final list of instructions.  Should only be called after all
	 * optimizations have been performed.
	 *
	 * @param instructions
	 *        The list of instructions to populate.
	 */
	public void generateOn (final List<L2Instruction> instructions)
	{
		for (final L2BasicBlock block : basicBlockOrder)
		{
			block.generateOn(instructions);
		}
	}

	/**
	 * Answer a visualization of this {@code L2ControlFlowGraph}. This is a
	 * debug method, intended to be called via evaluation during debugging.
	 *
	 * @return The requested visualization.
	 */
	@SuppressWarnings("unused")
	public String visualize ()
	{
		final StringBuilder builder = new StringBuilder();
		final L2ControlFlowGraphVisualizer visualizer =
			new L2ControlFlowGraphVisualizer(
				"«control flow graph»",
				"«chunk»",
				80,
				this,
				true,
				true,
				builder);
		visualizer.visualize();
		return builder.toString();
	}
}
