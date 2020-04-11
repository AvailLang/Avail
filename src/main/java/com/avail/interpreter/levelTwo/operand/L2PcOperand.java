/*
 * L2PcOperand.java
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.functions.ContinuationRegisterDumpDescriptor;
import com.avail.interpreter.JavaLibrary;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Entity;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.jvm.JVMChunk;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.values.L2SemanticValue;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;

import static com.avail.interpreter.levelTwo.register.L2Register.RegisterKind.BOXED;
import static com.avail.utility.CollectionExtensions.populatedEnumMap;
import static com.avail.utility.Nulls.stripNull;
import static java.util.Comparator.comparingInt;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.getInternalName;

/**
 * An {@code L2PcOperand} is an operand of type {@link L2OperandType#PC}.
 * It refers to a target {@link L2BasicBlock}, that either be branched to at
 * runtime, or captured in some other way that flow control may end up there.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2PcOperand
extends L2Operand
{
	/** The {@link L2BasicBlock} that this operand leads to. */
	private L2BasicBlock targetBlock;

	/**
	 * The manifest linking semantic values and registers at this control flow
	 * edge.
	 */
	private @Nullable L2ValueManifest manifest = null;

	/**
	 * Whether this edge points backward to a block marked as
	 * {@link L2BasicBlock#isLoopHead}, thereby closing a loop.
	 */
	private boolean isBackward;

	/**
	 * The {@link Set} of {@link L2Register}s that are written in all pasts, and
	 * are consumed along all future paths after the start of this block.  This
	 * is only populated during optimization, while the control flow graph is
	 * still in SSA form.
	 *
	 * <p>This is a superset of {@link #sometimesLiveInRegisters}.</p>
	 */
	public final Set<L2Register> alwaysLiveInRegisters = new HashSet<>();

	/**
	 * The {@link Set} of {@link L2Register}s that are written in all pasts, and
	 * are consumed along at least one future after the start of this block.
	 * This is only populated during optimization, while the control flow graph
	 * is still in SSA form.
	 *
	 * <p>This is a subset of {@link #alwaysLiveInRegisters}.</p>
	 */
	public final Set<L2Register> sometimesLiveInRegisters = new HashSet<>();

	/**
	 * Either {@code null}, the normal case, or a set with each {@link L2Entity}
	 * that is allowed to pass along this edge.  This mechanism is used to break
	 * control flow cycles, allowing a simple liveness algorithm to be used,
	 * instead of iterating (backward) through loops until the live set has
	 * converged.
	 */
	public @Nullable Set<L2Entity> forcedClampedEntities = null;

	/**
	 * A counter of how many times this edge has been traversed.  This will be
	 * used to determine the amount of effort to apply to subsequent
	 * re-optimization attempts, modulating inlining, order of tests, whether to
	 * optimize for space, run time, or compile time; that sort of thing.  The
	 * counter itself (a {@link LongAdder}) is passed as a constant through a
	 * special class loader, and captured as a final constant within the
	 * {@link L2Chunk}'s class.
	 *
	 * <p>Most edges don't benefit from having a counter, and a final optimized
	 * form has no need for any counters, so this field can be {@code null}.</p>
	 */
	public @Nullable LongAdder counter = null;

	/**
	 * Construct a new {@code L2PcOperand} that leads to the specified
	 * {@link L2BasicBlock}.  Set {@link #isBackward} to true if this is a
	 * back-link to a {@linkplain L2BasicBlock#isLoopHead loop head},
	 *
	 * @param targetBlock
	 *        The {@link L2BasicBlock} The target basic block.
	 * @param isBackward
	 *        Whether this edge is a back-link to a loop head.
	 */
	public L2PcOperand (
		final L2BasicBlock targetBlock,
		final boolean isBackward)
	{
		this.targetBlock = targetBlock;
		this.isBackward = isBackward;
	}

	/**
	 * Create a remapped {@code L2PcOperand} from the original operand, the new
	 * target {@link L2BasicBlock}, and the transformed {@link L2ValueManifest}.
	 * Set {@link #isBackward} to true if this is a back-link to a
	 * {@linkplain L2BasicBlock#isLoopHead loop head}.
	 *
	 * @param newTargetBlock
	 *        The transformed target {@link L2BasicBlock} of the new edge.
	 * @param isBackward
	 *        Whether this edge is a back-link to a loop head.
	 * @param newManifest
	 *        The transformed {@link L2ValueManifest} for the new edge.
	 */
	public L2PcOperand (
		final L2BasicBlock newTargetBlock,
		final boolean isBackward,
		final L2ValueManifest newManifest)
	{
		this(newTargetBlock, isBackward);
		manifest = newManifest;
	}

	@Override
	public void adjustCloneForInstruction (
		final L2Instruction theInstruction)
	{
		super.adjustCloneForInstruction(theInstruction);
		counter = null;
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.PC;
	}

	/**
	 * Answer whether this edge points backward to a block marked as
	 * {@link L2BasicBlock#isLoopHead}, thereby closing a loop.
	 */
	public boolean isBackward ()
	{
		return isBackward;
	}

	/**
	 * Answer the {@link L2ValueManifest} for this edge, which describes which
	 * {@link L2Register}s hold which {@link L2SemanticValue}s.
	 *
	 * @return This edge's {@link L2ValueManifest}.
	 */
	public L2ValueManifest manifest ()
	{
		return stripNull(manifest);
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public void instructionWasAdded (
		final L2ValueManifest theManifest)
	{
		super.instructionWasAdded(theManifest);
		instruction().basicBlock().addSuccessorEdge(this);
		manifest = new L2ValueManifest(theManifest);
		targetBlock.addPredecessorEdge(this);
	}

	@Override
	public void instructionWasInserted (
		final L2Instruction newInstruction)
	{
		super.instructionWasInserted(newInstruction);
		newInstruction.basicBlock().addSuccessorEdge(this);
		manifest = new L2ValueManifest(manifest());
		targetBlock.addPredecessorEdge(this);
	}

	@Override
	public void instructionWasRemoved ()
	{
		final L2BasicBlock sourceBlock = instruction().basicBlock();
		sourceBlock.removeSuccessorEdge(this);
		targetBlock.removePredecessorEdge(this);
		if (instruction().operation().altersControlFlow())
		{
			sourceBlock.removedControlFlowInstruction();
		}
		super.instructionWasRemoved();
	}

	@Override
	public void replaceRegisters (
		final Map<L2Register, L2Register> registerRemap,
		final L2Instruction theInstruction)
	{
		if (forcedClampedEntities != null)
		{
			new ArrayList<>(forcedClampedEntities).forEach(entity ->
			{
				//noinspection SuspiciousMethodCalls
				if (registerRemap.containsKey(entity))
				{
					forcedClampedEntities.remove(entity);
					//noinspection SuspiciousMethodCalls
					forcedClampedEntities.add(registerRemap.get(entity));
				}
			});
		}
		super.replaceRegisters(registerRemap, theInstruction);
	}

	/**
	 * Answer the target {@link L2BasicBlock} that this operand refers to.
	 *
	 * @return The target basic block.
	 */
	public L2BasicBlock targetBlock ()
	{
		return targetBlock;
	}

	/**
	 * Answer the L2 offset at the start of the {@link L2BasicBlock} that this
	 * operand refers to.
	 *
	 * @return The target L2 offset.
	 */
	public int offset ()
	{
		return targetBlock.offset();
	}

	/**
	 * Answer the source {@link L2BasicBlock} that this operand is an edge from.
	 *
	 * @return The source basic block.
	 */
	public L2BasicBlock sourceBlock ()
	{
		return instruction().basicBlock();
	}

	@Override
	public void appendTo (final StringBuilder builder)
	{
		// Show the basic block's name.
		if (offset() != -1)
		{
			builder.append("pc ").append(offset()).append(": ");
		}
		builder.append(targetBlock.name());
	}

	/**
	 * Create a new {@link L2BasicBlock} that will be the new target of this
	 * edge, and write an {@link L2_JUMP} into the new block to jump to the
	 * old target of this edge.  Be careful to maintain predecessor order at the
	 * target block.
	 *
	 * @param controlFlowGraph
	 *        The {@link L2ControlFlowGraph} being updated.
	 * @return The new {@link L2BasicBlock} that splits the given edge.  This
	 *         block has not yet been added to the controlFlowGraph, and the
	 *         client should do this to keep the graph consistent.
	 */
	public L2BasicBlock splitEdgeWith (
		final L2ControlFlowGraph controlFlowGraph)
	{
		assert instructionHasBeenEmitted();

		// Capture where this edge originated.
		final L2Instruction source = instruction();

		// Create a new intermediary block that initially just contains a jump
		// to itself.
		final L2BasicBlock newBlock = new L2BasicBlock(
			"edge-split ["
				+ source.basicBlock().name()
				+ " / "
				+ targetBlock.name()
				+ "]",
			false,
			source.basicBlock().zone);
		controlFlowGraph.startBlock(newBlock);
		final L2ValueManifest manifestCopy = new L2ValueManifest(manifest());
		newBlock.insertInstruction(
			0,
			new L2Instruction(
				newBlock,
				L2_JUMP.instance,
				new L2PcOperand(newBlock, isBackward, manifestCopy)));
		final L2Instruction newJump = newBlock.instructions().get(0);
		final L2PcOperand jumpEdge = L2_JUMP.jumpTarget(newJump);

		// Now swap my target with the new jump's target.  I'll end up pointing
		// to the new block, which will contain a jump pointing to the block I
		// used to point to.

		final L2BasicBlock finalTarget = targetBlock;
		targetBlock = jumpEdge.targetBlock;
		jumpEdge.targetBlock = finalTarget;
		isBackward = false;

		// Fix up the blocks' predecessors edges.
		newBlock.replacePredecessorEdge(jumpEdge, this);
		finalTarget.replacePredecessorEdge(this, jumpEdge);

		return newBlock;
	}

	/**
	 * In a non-SSA control flow graph that has had its phi functions removed
	 * and converted to moves, switch the target of this edge.
	 *
	 * @param newTarget The new target {@link L2BasicBlock} of this edge.
	 * @param isBackwardFlag Whether to also mark it as a backward edge.
	 */
	public void switchTargetBlockNonSSA (
		final L2BasicBlock newTarget,
		final boolean isBackwardFlag)
	{
		final L2BasicBlock oldTarget = targetBlock;
		targetBlock = newTarget;
		oldTarget.removePredecessorEdge(this);
		newTarget.addPredecessorEdge(this);
		isBackward = isBackwardFlag;
	}

	/**
	 * Write JVM bytecodes to the JVMTranslator which will push:
	 * <ol>
	 *     <li>An {@link AvailObject}[] containing the value of each live boxed
	 *     register, and</li>
	 *     <li>A {@code long[]} containing encoded data from each live unboxed
	 *     register.</li>
	 * </ol>
	 *
	 * <p>Also, associate within the {@link JVMTranslator} the information
	 * needed to extract these live registers when the target {@link
	 * L2_ENTER_L2_CHUNK} is reached.</p>
	 *
	 * <p>These arrays are suitable arguments for creating a
	 * {@link ContinuationRegisterDumpDescriptor} instance.</p>
	 *
	 * @param translator
	 *        The {@link JVMTranslator} in which to record the saved register
	 *        layout.
	 * @param method
	 *        The {@link MethodVisitor} on which to write code to push the
	 *        register dump.
	 */
	public void createAndPushRegisterDumpArrays (
		final JVMTranslator translator,
		final MethodVisitor method)
	{
		// Capture both the constant L2 offset of the target, and a register
		// dump containing the state of all live registers.  A subsequent
		// L2_CREATE_CONTINUATION will use both, and the L2_ENTER_L2_CHUNK at
		// the target will restore the register dump found in the continuation.
		final L2Instruction targetInstruction =
			targetBlock.instructions().get(0);
		assert targetInstruction.operation() == L2_ENTER_L2_CHUNK.instance;
		final EnumMap<RegisterKind, List<Integer>> liveMap =
			populatedEnumMap(RegisterKind.class, k -> new ArrayList<>());
		final Set<L2Register> liveRegistersSet =
			new HashSet<>(alwaysLiveInRegisters);
		liveRegistersSet.addAll(sometimesLiveInRegisters);
		final List<L2Register> liveRegistersList =
			new ArrayList<>(liveRegistersSet);
		liveRegistersList.sort(comparingInt(L2Register::finalIndex));
		liveRegistersList.forEach(reg ->
			liveMap.get(reg.registerKind()).add(
				translator.localNumberFromRegister(reg)));

		translator.liveLocalNumbersByKindPerEntryPoint.put(
			targetInstruction, liveMap);

		// Emit code to save those registers' values.  Start with the objects.
		// :: array = new «arrayClass»[«limit»];
		// :: array[0] = ...; array[1] = ...;
		final List<Integer> boxedLocalNumbers = liveMap.get(BOXED);
		if (boxedLocalNumbers.isEmpty())
		{
			JVMChunk.noObjectsField.generateRead(method);
		}
		else
		{
			translator.intConstant(method, boxedLocalNumbers.size());
			method.visitTypeInsn(ANEWARRAY, getInternalName(AvailObject.class));
			for (int i = 0; i < boxedLocalNumbers.size(); i++)
			{
				method.visitInsn(DUP);
				translator.intConstant(method, i);
				method.visitVarInsn(
					BOXED.loadInstruction,
					boxedLocalNumbers.get(i));
				method.visitInsn(AASTORE);
			}
		}
		// Now create the array of longs, including both ints and doubles.
		final List<Integer> intLocalNumbers = liveMap.get(RegisterKind.INTEGER);
		final List<Integer> floatLocalNumbers = liveMap.get(RegisterKind.FLOAT);
		if (intLocalNumbers.size() + floatLocalNumbers.size() == 0)
		{
			JVMChunk.noLongsField.generateRead(method);
		}
		else
		{
			translator.intConstant(
				method, intLocalNumbers.size() + floatLocalNumbers.size());
			method.visitIntInsn(NEWARRAY, T_LONG);
			int i;
			for (i = 0; i < intLocalNumbers.size(); i++)
			{
				method.visitInsn(DUP);
				translator.intConstant(method, i);
				method.visitVarInsn(
					RegisterKind.INTEGER.loadInstruction,
					intLocalNumbers.get(i));
				method.visitInsn(I2L);
				method.visitInsn(LASTORE);
			}
			for (int j = 0; j < floatLocalNumbers.size(); j++, i++)
			{
				method.visitInsn(DUP);
				translator.intConstant(method, i);
				method.visitVarInsn(
					RegisterKind.FLOAT.loadInstruction,
					floatLocalNumbers.get(i));
				JavaLibrary.getBitCastDoubleToLongMethod().generateCall(method);
				method.visitInsn(LASTORE);
			}
		}
		// The stack is now AvailObject[], long[].
	}

	/**
	 * Erase all information about the given registers from this edge's
	 * manifest, and in the manifests of any edges recursively reachable from
	 * this edge. Terminate the recursion if an edge already doesn't know any of
	 * these registers.  Ignore back-edges, as their manifests are explicitly
	 * created to contain only the function's arguments.
	 *
	 * <p>Also fix up synonyms that no longer have backing registers, or that
	 * have lost their last register of a particular {@link RegisterKind}.</p>
	 *
	 * @param registersToForget
	 *        The {@link Set} of {@link L2Register}s that we must erase all
	 *        knowledge about in manifests reachable from here.
	 */
	public void forgetRegistersInManifestsRecursively (
		final Set<L2Register> registersToForget)
	{
		final Deque<L2PcOperand> workQueue = new ArrayDeque<>();
		workQueue.add(this);
		while (!workQueue.isEmpty())
		{
			final L2PcOperand edge = workQueue.remove();
			if (edge.isBackward())
			{
				continue;
			}
			if (edge.manifest().forgetRegisters(registersToForget))
			{
				workQueue.addAll(edge.targetBlock().successorEdgesCopy());
			}
		}
	}

	/**
	 * Create and install a {@link LongAdder} to count visits through this edge
	 * in the final JVM code.
	 */
	public void installCounter ()
	{
		assert counter == null;  // Don't install twice.
		counter = new LongAdder();
	}
}
