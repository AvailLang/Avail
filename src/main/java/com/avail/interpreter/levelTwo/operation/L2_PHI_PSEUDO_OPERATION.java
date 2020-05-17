/*
 * L2_PHI_PSEUDO_OPERATION.java
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2NamedOperandType;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.utility.MutableInt;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.interpreter.levelTwo.operand.L2Operand.instructionWasAddedForPhi;

/**
 * The {@code L2_PHI_PSEUDO_OPERATION} occurs at the start of a {@link
 * L2BasicBlock}.  It's a convenient fiction that allows an {@link
 * L2ControlFlowGraph} to be in Static Single Assignment form (SSA), where each
 * {@link L2Register} has exactly one instruction that writes to it.
 *
 * <p>The vector of source registers are in the same order as the corresponding
 * predecessors of the containing {@link L2BasicBlock}.  The runtime effect
 * would be to select from that vector, based on the predecessor from which
 * control arrives, and move that register's value to the destination register.
 * However, that's a fiction, and the phi operation is instead removed during
 * the transition of the control flow graph out of SSA, being replaced by move
 * instructions along each incoming edge.</p>
 *
 * @param <R> The kind of {@link L2Register} to merge.
 * @param <RR> The kind of {@link L2ReadOperand}s to merge.
 * @param <WR> The kind of {@link L2WriteOperand}s to write the result to.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_PHI_PSEUDO_OPERATION <
	R extends L2Register,
	RR extends L2ReadOperand<R>,
	WR extends L2WriteOperand<R>>
extends L2Operation
{
	/**
	 * Construct an {@code L2_PHI_PSEUDO_OPERATION}.
	 *
	 * @param moveOperation
	 *        The {@link L2_MOVE} operation to substitute for this instruction
	 *        on incoming split edges.
	 * @param theNamedOperandTypes
	 *        An array of {@link L2NamedOperandType}s that describe this
	 *        particular L2Operation, allowing it to be specialized by register
	 *        type.
	 */
	private L2_PHI_PSEUDO_OPERATION (
		final L2_MOVE<R, RR, WR> moveOperation,
		final L2NamedOperandType... theNamedOperandTypes)
	{
		super(theNamedOperandTypes);
		this.moveOperation = moveOperation;
	}

	/**
	 * The {@link L2_MOVE} operation to substitute for this instruction on
	 * incoming split edges.
	 */
	public final L2_MOVE<R, RR, WR> moveOperation;

	/**
	 * Initialize the instance used for merging boxed values.
	 */
	public static final L2_PHI_PSEUDO_OPERATION<
		L2BoxedRegister, L2ReadBoxedOperand, L2WriteBoxedOperand>
		boxed = new L2_PHI_PSEUDO_OPERATION<>(
			L2_MOVE.boxed,
			READ_BOXED_VECTOR.is("potential boxed sources"),
			WRITE_BOXED.is("boxed destination"));

	/**
	 * Initialize the instance used for merging boxed values.
	 */
	public static final L2_PHI_PSEUDO_OPERATION<
		L2IntRegister, L2ReadIntOperand, L2WriteIntOperand>
		unboxedInt = new L2_PHI_PSEUDO_OPERATION<>(
			L2_MOVE.unboxedInt,
			READ_INT_VECTOR.is("potential int sources"),
			WRITE_INT.is("int destination"));

	/**
	 * Initialize the instance used for merging boxed values.
	 */
	public static final L2_PHI_PSEUDO_OPERATION<
		L2FloatRegister, L2ReadFloatOperand, L2WriteFloatOperand>
		unboxedFloat = new L2_PHI_PSEUDO_OPERATION<>(
			L2_MOVE.unboxedFloat,
			READ_FLOAT_VECTOR.is("potential float sources"),
			WRITE_FLOAT.is("float destination"));

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ReadVectorOperand<RR, R> sources = instruction.operand(0);
		final L2WriteOperand<R> destination = instruction.operand(1);

		final Iterator<RR> iterator = sources.elements().iterator();
		assert iterator.hasNext();
		TypeRestriction restriction = iterator.next().restriction();
		while (iterator.hasNext())
		{
			restriction = restriction.union(iterator.next().restriction());
		}

		registerSet.removeConstantAt(destination.register());
		registerSet.removeTypeAt(destination.register());
		registerSet.typeAtPut(
			destination.register(), restriction.type, instruction);
		if (restriction.constantOrNull != null)
		{
			registerSet.constantAtPut(
				destination.register(),
				restriction.constantOrNull,
				instruction);
		}
	}

	@Override
	public boolean isPhi ()
	{
		return true;
	}

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		// The reads in the input vector are from the positionally corresponding
		// incoming edges, which carry the manifests that should be used to
		// look up the best source semantic values.
		final L2ReadVectorOperand<RR, R> sources = instruction.operand(0);
		final L2WriteOperand<R> destination = instruction.operand(1);

		final List<L2PcOperand> predecessorEdges =
			instruction.basicBlock().predecessorEdgesCopy();
		instructionWasAddedForPhi(sources, predecessorEdges);
		destination.instructionWasAdded(manifest);
	}

	@Override
	public boolean shouldEmit (
		final L2Instruction instruction)
	{
		// Phi instructions are converted to moves along predecessor edges.
		return false;
	}

	/**
	 * One of this phi function's predecessors has been removed because it's
	 * dead code.  Clean up its vector of inputs by removing the specified
	 * index.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} whose operation has this type.
	 * @param inputIndex
	 *        The index to remove.
	 * @return A replacement {@link L2Instruction}, whose operation may be
	 *         either another {@code L2_PHI_PSEUDO_OPERATION} or an {@link
	 *         L2_MOVE}.
	 */
	public L2Instruction withoutIndex (
		final L2Instruction instruction,
		final int inputIndex)
	{
		final L2ReadVectorOperand<RR, R> oldVector = instruction.operand(0);
		final L2WriteOperand<R> destinationReg = instruction.operand(1);

		final List<RR> newSources = new ArrayList<>(oldVector.elements());
		newSources.remove(inputIndex);

		final boolean onlyOneRegister = new HashSet<>(newSources).size() == 1;
		if (onlyOneRegister)
		{
			// Replace the phi function with a simple move.
			return new L2Instruction(
				instruction.basicBlock(),
				moveOperation,
				newSources.get(0),
				destinationReg);
		}
		return new L2Instruction(
			instruction.basicBlock(),
			this,
			oldVector.clone(newSources),
			destinationReg);
	}

	/**
	 * Replace this phi by providing a lambda that alters a copy of the list of
	 * {@link L2ReadOperand}s that it's passed.  The predecessor edges are
	 * expected to correspond with the inputs.  Do not attempt to normalize the
	 * phi to a move.
	 *
	 * @param instruction
	 *        The phi instruction to augment.
	 * @param updater
	 *        What to do to a copied mutable {@link List} of read operands that
	 *        starts out having all of the vector operand's elements.
	 */
	public void updateVectorOperand(
		final L2Instruction instruction,
		final Consumer<List<RR>> updater)
	{
		assert instruction.operation() == this;
		final L2BasicBlock block = instruction.basicBlock();
		final int instructionIndex = block.instructions().indexOf(instruction);
		final L2ReadVectorOperand<RR, R> vectorOperand = instruction.operand(0);
		final L2WriteOperand<R> writeOperand = instruction.operand(1);

		instruction.justRemoved();
		final List<RR> originalElements = vectorOperand.elements();
		final List<RR> passedCopy = new ArrayList<>(originalElements);
		updater.accept(passedCopy);
		final List<RR> finalCopy = new ArrayList<>(passedCopy);

		final L2Instruction replacementInstruction = new L2Instruction(
			block, this, vectorOperand.clone(finalCopy), writeOperand);

		block.instructions().set(instructionIndex, replacementInstruction);
		replacementInstruction.justInserted();
	}

	/**
	 * Examine the instruction and answer the predecessor {@link L2BasicBlock}s
	 * that supply a value from the specified register.
	 *
	 * @param instruction
	 *        The phi-instruction to examine.
	 * @param usedRegister
	 *        The {@link L2Register} whose use we're trying to trace back to its
	 *        definition.
	 * @return A {@link List} of predecessor blocks that supplied the
	 *         usedRegister as an input to this phi operation.
	 */
	public List<L2BasicBlock> predecessorBlocksForUseOf (
		final L2Instruction instruction,
		final L2Register usedRegister)
	{
		assert this == instruction.operation();
		final L2ReadVectorOperand<RR, R> sources = instruction.operand(0);
//		final L2WriteOperand<R> destination = instruction.operand(1);

		assert sources.elements().size()
			== instruction.basicBlock().predecessorEdgesCount();
		final List<L2BasicBlock> list = new ArrayList<>();
		final MutableInt i = new MutableInt(0);
		instruction.basicBlock().predecessorEdgesDo(edge ->
		{
			if (sources.elements().get(i.value++).register() == usedRegister)
			{
				list.add(edge.sourceBlock());
			}
		});
		return list;
	}

	/**
	 * Answer the {@link L2WriteOperand} from this phi function.  This should
	 * only be used when generating phi moves (which takes the {@link
	 * L2ControlFlowGraph} out of Static Single Assignment form).
	 *
	 * @param <W>
	 *        The type of the {@link L2WriteOperand}.
	 * @param instruction
	 *        The instruction to examine.  It must be a phi operation.
	 * @return The instruction's destination {@link L2WriteOperand}.
	 */
	public <W extends L2WriteOperand<R>>
	W destinationRegisterWrite (final L2Instruction instruction)
	{
		assert this == instruction.operation();
		return instruction.operand(1);
	}

	/**
	 * Answer the {@link List} of {@link L2ReadOperand}s for this phi function.
	 * The order is correlated to the instruction's blocks predecessorEdges.
	 *
	 * @param instruction
	 *        The phi instruction.
	 * @return The instruction's list of sources.
	 */
	public List<RR> sourceRegisterReads (
		final L2Instruction instruction)
	{
		final L2ReadVectorOperand<RR, R> vector = instruction.operand(0);
		return vector.elements();
	}

	/**
	 * Update an {@code L2_PHI_PSEUDO_OPERATION} instruction that's in a loop
	 * head basic block.
	 *
	 * @param predecessorManifest
	 *        The {@link L2ValueManifest} in some predecessor edge.
	 * @param instruction
	 *        The phi instruction itself.
	 */
	public void updateLoopHeadPhi (
		final L2ValueManifest predecessorManifest,
		final L2Instruction instruction)
	{
		assert instruction.operation() == this;
		final L2SemanticValue semanticValue =
			sourceRegisterReads(instruction).get(0).semanticValue();
		final RegisterKind kind = moveOperation.kind;
		final RR readOperand = kind.readOperand(
			semanticValue,
			predecessorManifest.restrictionFor(semanticValue),
			predecessorManifest.getDefinition(semanticValue, kind));
		updateVectorOperand(
			instruction,
			mutableSources -> mutableSources.add(readOperand));
	}

	@Override
	public String toString ()
	{
		return super.toString() + "(" + moveOperation.kind.getKindName() + ")";
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<? extends L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2Operand vector = instruction.operand(0);
		final L2Operand target = instruction.operand(1);
		builder.append("ϕ ");
		builder.append(target);
		builder.append(" ← ");
		builder.append(vector);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		throw new UnsupportedOperationException(
			"This instruction should be factored out before JVM translation");
	}
}
