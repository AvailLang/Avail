/*
 * L2Operand.java
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ValueManifest;
import com.avail.utility.PublicCloneable;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.util.List;
import java.util.Map;

import static com.avail.utility.Nulls.stripNull;

/**
 * An {@code L2Operand} knows its {@link L2OperandType} and any specific value
 * that needs to be captured for that type of operand.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class L2Operand
extends PublicCloneable<L2Operand>
{
	/**
	 * A backpointer to the {@link L2Instruction} that this operand is part of.
	 * This is only populated for instructions that have already been emitted to
	 * an {@link L2BasicBlock}.
	 */
	private @Nullable L2Instruction instruction = null;

	/**
	 * Answer the {@link L2Instruction} containing this operand.
	 *
	 * @return An {@link L2Instruction}
	 */
	public L2Instruction instruction ()
	{
		return stripNull(instruction);
	}

	/**
	 * Answer whether this write operand has been written yet as the destination
	 * of some instruction.
	 *
	 * @return {@code true} if this operand has been written inside an
	 *         {@link L2Instruction}, otherwise {@code false}.
	 */
	public boolean instructionHasBeenEmitted ()
	{
		return instruction != null;
	}

	/**
	 * Assert that this operand knows its instruction, which should always be
	 * the case if the instruction has already been emitted.
	 */
	@OverridingMethodsMustInvokeSuper
	public void assertHasBeenEmitted ()
	{
		assert instruction != null;
	}

	/**
	 * Answer this operand's {@link L2OperandType}.
	 *
	 * @return An {@code L2OperandType}.
	 */
	public abstract L2OperandType operandType ();

	/**
	 * Dispatch this {@code L2Operand} to the provided {@link
	 * L2OperandDispatcher}.
	 *
	 * @param dispatcher
	 *        The {@code L2OperandDispatcher} visiting the receiver.
	 */
	public abstract void dispatchOperand (
		final L2OperandDispatcher dispatcher);

	/**
	 * This is an operand of the given instruction, which was just added to its
	 * basic block.  Its instruction was just set.
	 *
	 * @param manifest
	 *        The {@link L2ValueManifest} that is active where this {@link
	 *        L2Instruction} was just added to its {@link L2BasicBlock}.
	 */
	@OverridingMethodsMustInvokeSuper
	public void instructionWasAdded (
		final L2ValueManifest manifest)
	{
		assert instruction != null;
	}

	/**
	 * This vector operand is the input to a {@link L2_PHI_PSEUDO_OPERATION}
	 * instruction that has just been added.  Update it specially, to take into
	 * account the correspondence between vector elements and predecessor edges.
	 *
	 * @param readVector
	 *        The {@link L2ReadVectorOperand} for which we're noting that the
	 *        containing instruction has just been added.
	 * @param predecessorEdges
	 *        The {@link List} of predecessor edges ({@link L2PcOperand}s) that
	 *        correspond positionally with the elements of the vector.
	 */
	public static void instructionWasAddedForPhi (
		final L2ReadVectorOperand<?, ?> readVector,
		final List<L2PcOperand> predecessorEdges)
	{
		assert readVector.instruction().operation()
			instanceof L2_PHI_PSEUDO_OPERATION;

		final int fanIn = readVector.elements.size();
		assert fanIn == predecessorEdges.size();
		for (int i = 0; i < fanIn; i++)
		{
			// The read operand should use the corresponding incoming manifest.
			readVector.elements.get(i).instructionWasAdded(
				predecessorEdges.get(i).manifest());
		}
	}

	/**
	 * This is an operand of the given instruction, which was just inserted into
	 * its basic block as part of an optimization pass.
	 *
	 * @param newInstruction
	 *        The {@link L2Instruction} that was just inserted.
	 */
	@OverridingMethodsMustInvokeSuper
	public void instructionWasInserted (
		final L2Instruction newInstruction)
	{
		// Nothing by default.  The L2Instruction already set my instruction
		// field in a previous pass.
	}

	/**
	 * This is an operand of the given instruction, which was just removed from
	 * its basic block.
	 */
	@OverridingMethodsMustInvokeSuper
	public void instructionWasRemoved ()
	{
		// Nothing by default.  The L2Instruction already set my instruction
		// field in a previous pass.
	}

	/**
	 * Replace occurrences in this operand of each register that is a key of
	 * this map with the register that is the corresponding value.  Do nothing
	 * to registers that are not keys of the map.  Update all secondary
	 * structures, such as the instruction's source/destination collections.
	 *
	 * @param registerRemap
	 *        A mapping to transform registers in-place.
	 * @param theInstruction
	 *        The instruction containing this operand.
	 */
	@SuppressWarnings({"unused", "EmptyMethod"})
	public void replaceRegisters (
		final Map<L2Register, L2Register> registerRemap,
		final L2Instruction theInstruction)
	{
		// By default do nothing.
	}

	/**
	 * Replace occurrences within this operand of {@link L2WriteOperand}s that
	 * are keys of the given {@link Map}.  Only update {@link L2ReadOperand}s
	 * and {@link L2PcOperand}s, since those capture (through an
	 * {@link L2ValueManifest} for the latter) defining writes that need to be
	 * updated.  Specifically <em>DO NOT</em> update {@link L2WriteOperand}s.
	 *
	 * @param writesMap
	 *        A mapping between {@link L2WriteOperand}s
	 */
	public void replaceWriteDefinitions (
		final Map<L2WriteOperand<?>, L2WriteOperand<?>> writesMap)
	{
		// By default do nothing.
	}

	/**
	 * Move any registers used as sources within me into the provided list.
	 *
	 * @param sourceRegisters The {@link List} to update.
	 */
	@SuppressWarnings({"unused", "EmptyMethod"})
	public void addSourceRegistersTo (
		final List<L2Register> sourceRegisters)
	{
		// Do nothing by default.
	}

	/**
	 * Move any registers used as destinations within me into the provided list.
	 *
	 * @param destinationRegisters The {@link List} to update.
	 */
	@SuppressWarnings({"unused", "EmptyMethod"})
	public void addDestinationRegistersTo (
		final List<L2Register> destinationRegisters)
	{
		// Do nothing by default.
	}

	@Override
	public abstract String toString ();

	/**
	 * This is a freshly cloned operand.  Adjust it for use in the given
	 * {@link L2Instruction}.  Note that the new instruction has not yet been
	 * installed into an {@link L2BasicBlock}.
	 *
	 * @param theInstruction
	 *        The theInstruction that this operand is being installed in.
	 */
	@OverridingMethodsMustInvokeSuper
	public void adjustCloneForInstruction (final L2Instruction theInstruction)
	{
		// The instruction will be set correctly when this instruction is
		// emitted to an L2BasicBlock.
		setInstruction(null);
	}

	/**
	 * Set the {@link #instruction} field.
	 *
	 * @param theInstruction
	 *        The {@link L2Instruction} or {@code null}.
	 */
	public void setInstruction (final @Nullable L2Instruction theInstruction)
	{
		this.instruction = theInstruction;
	}
}
