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
import java.util.function.Consumer;

import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.Strings.increaseIndentation;

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
	 * A back-pointer to the {@link L2Instruction} that this operand is part of.
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
	 * This is an operand of the given instruction, which was once emitted, then
	 * removed, and has just now been added again to a basic block.  Its
	 * instruction was just set.
	 *
	 * <p>Since this operation can take place after initial code generation, we
	 * have to compensate for reads of semantic values that no longer exist,
	 * due to elision of moves that would have augmented a synonym.  This logic
	 * is in {@link L2ReadOperand#adjustedForReinsertion(L2ValueManifest)} and
	 * {@link L2ReadVectorOperand#adjustedForReinsertion(L2ValueManifest)}.</p>
	 *
	 * @param manifest
	 *        The {@link L2ValueManifest} that is active where this {@link
	 *        L2Instruction} was just added to its {@link L2BasicBlock}.
	 * @return The replacement {@code L2Operand}, possibly the receiver.
	 */
	public L2Operand adjustedForReinsertion (final L2ValueManifest manifest)
	{
		return this;
	}

	/**
	 * This vector operand is the input to an {@link L2_PHI_PSEUDO_OPERATION}
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
	public void replaceRegisters (
		final Map<L2Register, L2Register> registerRemap,
		final L2Instruction theInstruction)
	{
		// By default do nothing.
	}

	/**
	 * Capture all {@link L2ReadOperand}s within this operand into the provided
	 * {@link List}.
	 *
	 * @param readOperands
	 *        The mutable {@link List} of {@link L2ReadOperand}s being
	 *        populated.
	 */
	public void addReadsTo (final List<L2ReadOperand<?>> readOperands)
	{
		// Do nothing by default.
	}

	/**
	 * Capture all {@link L2WriteOperand}s within this operand into the provided
	 * {@link List}.
	 *
	 * @param writeOperands
	 *        The mutable {@link List} of {@link L2WriteOperand}s being
	 *        populated.
	 */
	public void addWritesTo (final List<L2WriteOperand<?>> writeOperands)
	{
		// Do nothing by default.
	}

	/**
	 * Move any registers used as sources within me into the provided list.
	 *
	 * @param sourceRegisters The {@link List} to update.
	 */
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
	public void addDestinationRegistersTo (
		final List<L2Register> destinationRegisters)
	{
		// Do nothing by default.
	}

	@Override
	public final String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		appendWithWarningsTo(builder, 0, flag -> { /* ignored */ });
		return builder.toString();
	}

	/**
	 * Append a textual representation of this operand to the provided
	 * {@link StringBuilder}.  If a style change is appropriate while building
	 * the string, invoke the warningStyleChange {@link Consumer} with
	 * {@code true} to enable the warning style, and {@code false} to turn it
	 * off again.
	 *
	 * @param builder
	 *        The {@link StringBuilder} on which to describe this operand.
	 * @param indent
	 *        How much additional indentation to add to successive lines.
	 * @param warningStyleChange
	 *        A {@link Consumer} to invoke to turn the warning style on or off,
	 *        with a mechanism specified (or ignored) by the caller.
	 */
	public final void appendWithWarningsTo (
		final StringBuilder builder,
		final int indent,
		final Consumer<Boolean> warningStyleChange)
	{
		if (instruction == null)
		{
			warningStyleChange.accept(true);
			builder.append("DEAD-OPERAND: ");
			warningStyleChange.accept(false);
		}
		else if (isMisconnected())
		{
			warningStyleChange.accept(true);
			builder.append("MISCONNECTED: ");
			warningStyleChange.accept(false);
		}
		// Call the inner method that can be overridden.
		final StringBuilder temp = new StringBuilder();
		appendTo(temp);
		builder.append(increaseIndentation(temp.toString(), indent));
	}

	/**
	 * Answer whether this operand is misconnected to its {@link L2Instruction}.
	 *
	 * @return {@code false} if the operand is connected correctly, otherwise
	 *         {@code true}.
	 */
	public boolean isMisconnected ()
	{
		if (instruction == null)
		{
			return true;
		}
		final L2Operand[] operands = instruction.operands();
		for (int i = 0, limit = operands.length; i < limit; i++)
		{
			final L2Operand operand = operands[i];
			if (operand == this)
			{
				return false;
			}
			if (operand instanceof L2ReadVectorOperand)
			{
				final L2ReadVectorOperand<?, ?> vectorOperand = cast(operand);
				if (vectorOperand.elements.contains(this))
				{
					return false;
				}
			}
		}
		// Operand wasn't found inside the instruction.
		return true;
	}

	/**
	 * Write a description of this operand to the given {@link StringBuilder}.
	 *
	 * @param builder
	 *        The {@link StringBuilder} on which to describe this operand.
	 */
	public abstract void appendTo (final StringBuilder builder);

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
