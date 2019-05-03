/*
 * L2Inliner.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.optimizer.reoptimizer;

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_ChunkDependable;
import com.avail.descriptor.A_RawFunction;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.avail.utility.Casts.cast;
import static java.util.Collections.unmodifiableList;

/**
 * This is used to transform and embed a called function's chunk's control flow
 * graph into the calling function's chunk's control flow graph.  Doing so:
 *
 * <ul>
 *     <li>eliminates the basic cost of the call and return,</li>
 *     <li>passes parameters in and result out with moves that are easily
 *     eliminated,</li>
 *     <li>allows stronger call-site types to narrow method lookups,</li>
 *     <li>exposes primitive cancellation patterns like &lt;x, y>[1] → x,</li>
 *     <li>exposes L1 instruction cancellation, like avoiding creation of
 *     closures and label continuations,</li>
 *     <li>allows nearly all conditional and loop control flow to be expressed
 *     as simple jumps,</li>
 *     <li>exposes opportunities to operate on intermediate values in an unboxed
 *     form.</li>
 * </ul>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2Inliner
{
	/**
	 * An {@link L2OperandDispatcher} subclass suitable for copying operands for
	 * the enclosing {@link L2Inliner}.
	 */
	@InnerAccess class OperandInlineTransformer
	implements L2OperandDispatcher
	{
		/**
		 * The current operand being transformed.  It gets set before a dispatch
		 * and read afterward, allowing the dispatch operation to replace it.
		 */
		@Nullable L2Operand currentOperand = null;

		@SuppressWarnings("EmptyMethod")
		@Override
		public void doOperand (final L2CommentOperand operand) { }

		@SuppressWarnings("EmptyMethod")
		@Override
		public void doOperand (final L2ConstantOperand operand) { }

		@Override
		public void doOperand (final L2InternalCounterOperand operand)
		{
			// Create a new counter.
			currentOperand = new L2InternalCounterOperand();
		}

		@SuppressWarnings("EmptyMethod")
		@Override
		public void doOperand (final L2IntImmediateOperand operand) { }

		@SuppressWarnings("EmptyMethod")
		@Override
		public void doOperand (final L2FloatImmediateOperand operand) { }

		@Override
		public void doOperand (final L2PcOperand operand)
		{
			final PhiRestriction[] phiRestrictions =
				operand.phiRestrictions.clone();
			for (int i = 0; i < phiRestrictions.length; i++)
			{
				final PhiRestriction phiRestriction = phiRestrictions[i];
				phiRestrictions[i] = new PhiRestriction(
					mapRegister(phiRestriction.register),
					phiRestriction.typeRestriction);
			}
			currentOperand = new L2PcOperand(
				mapBlock(operand.targetBlock()),
				mapManifest(operand.manifest()),
				phiRestrictions);
		}

		@SuppressWarnings("EmptyMethod")
		@Override
		public void doOperand (final L2PrimitiveOperand operand) { }

		@Override
		public void doOperand (final L2ReadIntOperand operand)
		{
			currentOperand =
				new L2ReadIntOperand(
					mapRegister(operand.register()), operand.restriction());
		}

		@Override
		public void doOperand (final L2ReadFloatOperand operand)
		{
			currentOperand =
				new L2ReadFloatOperand(
					mapRegister(operand.register()), operand.restriction());
		}

		@Override
		public void doOperand (final L2ReadPointerOperand operand)
		{
			currentOperand = new L2ReadPointerOperand(
				mapRegister(operand.register()), operand.restriction());
		}

		@Override
		public <
			RR extends L2ReadOperand<R>,
			R extends L2Register>
		void doOperand (final L2ReadVectorOperand<RR, R> operand)
		{
			final List<RR> oldElements = operand.elements();
			final List<RR> newElements = new ArrayList<>(oldElements.size());
			for (final RR oldElement : oldElements)
			{
				// Note: this clobbers currentOperand, but we'll set it later.
				newElements.add(transformOperand(oldElement));
			}
			currentOperand = new L2ReadVectorOperand<>(newElements);
		}

		@SuppressWarnings("EmptyMethod")
		@Override
		public void doOperand (final L2SelectorOperand operand) { }

		@Override
		public void doOperand (final L2WriteIntOperand operand)
		{
			// Writes should always be encountered before reads, and only once.
			final L2IntRegister oldRegister = operand.register();
			assert !registerMap.containsKey(oldRegister);
			final TypeRestriction restriction =
				oldRegister.restriction();
			final L2WriteIntOperand writer =
				targetGenerator.newIntRegisterWriter(restriction);
			final L2IntRegister newRegister = writer.register();
			registerMap.put(oldRegister, newRegister);
			currentOperand = writer;
		}

		@Override
		public void doOperand (final L2WriteFloatOperand operand)
		{
			// Writes should always be encountered before reads, and only once.
			final L2FloatRegister oldRegister = operand.register();
			assert !registerMap.containsKey(oldRegister);
			final TypeRestriction restriction =
				oldRegister.restriction();
			final L2WriteFloatOperand writer =
				targetGenerator.newFloatRegisterWriter(restriction);
			final L2FloatRegister newRegister = writer.register();
			registerMap.put(oldRegister, newRegister);
			currentOperand = writer;
		}

		@Override
		public void doOperand (final L2WritePointerOperand operand)
		{
			// Writes should always be encountered before reads, and only once.
			final L2ObjectRegister oldRegister = operand.register();
			assert !registerMap.containsKey(oldRegister);
			final TypeRestriction restriction =
				oldRegister.restriction();
			final L2WritePointerOperand writer =
				targetGenerator.newObjectRegisterWriter(restriction);
			final L2ObjectRegister newRegister = writer.register();
			registerMap.put(oldRegister, newRegister);
			currentOperand = writer;
		}

		@Override
		public <R extends L2Register>
		void doOperand (final L2WritePhiOperand<R> operand)
		{
			// Writes should always be encountered before reads, and only once.
			final R oldRegister = operand.register();
			assert !registerMap.containsKey(oldRegister);
			final R copiedRegister =
				cast(
					oldRegister.copyForTranslator(
						targetGenerator, oldRegister.restriction()));
			final L2WritePhiOperand<R> writer =
				targetGenerator.newPhiRegisterWriter(copiedRegister);
			final R newRegister = writer.register();
			registerMap.put(oldRegister, newRegister);
			currentOperand = writer;
		}
	}

	/** This inliner's reusable {@link OperandInlineTransformer}. */
	private final OperandInlineTransformer operandInlineTransformer =
		new OperandInlineTransformer();

	/**
	 * Produce a transformed copy of the given {@link L2Operand}, strengthened
	 * to a suitable type.  <em>NOT</em> thread-safe for multiple threads using
	 * the same inliner.
	 *
	 * @param operand
	 *        The original {@link L2Operand} to transform of type {@link O}.
	 * @param <O>
	 *        The {@link L2Operand} subtype.
	 * @return The transformed {@link L2Operand}, also of type {@link O}.
	 */
	public <O extends L2Operand> O transformOperand (final O operand)
	{
		operandInlineTransformer.currentOperand = operand;
		operand.dispatchOperand(operandInlineTransformer);
		return cast(operandInlineTransformer.currentOperand);
		// Don't bother clearing the currentOperand field afterward.
	}

	/** The {@link L2Generator} on which to output the transformed L2 code. */
	@InnerAccess final L2Generator targetGenerator;

	/** The {@link Frame} representing the invocation being inlined. */
	@InnerAccess final Frame inlineFrame;

	/** The invoke-like {@link L2Instruction} being inlined. */
	@InnerAccess final L2Instruction invokeInstruction;

	/** The {@link A_RawFunction} being inlined. */
	@InnerAccess final A_RawFunction code;

	/** The registers providing values captured by the closure being inlined. */
	@InnerAccess final List<L2ReadPointerOperand> outers;

	/** The registers providing arguments to the invocation being inlined. */
	@InnerAccess final List<L2ReadPointerOperand> arguments;

	/** The register to write the result of the inlined call into. */
	@InnerAccess final L2WritePointerOperand result;

	/**
	 * The {@link L2BasicBlock} that should be reached when the inlined call
	 * completes successfully.
	 */
	@InnerAccess final L2BasicBlock completionBlock;

	/**
	 * The {@link L2BasicBlock} that should be reached when reification happens
	 * during the inlined call.
	 */
	@InnerAccess final L2BasicBlock reificationBlock;

	/**
	 * The accumulated mapping from original {@link L2BasicBlock}s to their
	 * replacements.  When code splitting is implemented, the key of this
	 * structure might be reworked as an &lt;{@link L2BasicBlock}, {@link
	 * L2ValueManifest}&gt; pair.
	 */
	private final Map<L2BasicBlock, L2BasicBlock> blockMap = new HashMap<>();

	/**
	 * The accumulated mapping from original {@link L2SemanticValue}s to their
	 * replacements.
	 */
	private final Map<L2SemanticValue, L2SemanticValue> semanticValueMap =
		new HashMap<>();

	/**
	 * The accumulated mapping from original {@link Frame}s to their
	 * replacements.
	 */
	private final Map<Frame, Frame> frameMap = new HashMap<>();

	/**
	 * The accumulated mapping from original {@link L2Register}s to their
	 * replacements.
	 */
	final Map<L2Register, L2Register> registerMap = new HashMap<>();

	/**
	 * Construct a new {@code L2Inliner}.
	 *
	 * @param targetGenerator
	 *        The {@link L2Generator} on which to write new instructions.
	 * @param inlineFrame
	 *        The {@link Frame} representing this call site.  The top frame in
	 *        each manifest of the callee, including embedded inside other
	 *        {@link L2SemanticValue}s, must be transformed into the provided
	 *        inlineFrame.
	 * @param arguments
	 *        The {@link List} of {@link L2ReadPointerOperand}s corresponding to
	 *        the arguments to this function invocation.
	 */
	L2Inliner (
		final L2Generator targetGenerator,
		final Frame inlineFrame,
		final L2Instruction invokeInstruction,
		final A_RawFunction code,
		final List<L2ReadPointerOperand> outers,
		final List<L2ReadPointerOperand> arguments,
		final L2WritePointerOperand result,
		final L2BasicBlock completionBlock,
		final L2BasicBlock reificationBlock)
	{
		this.targetGenerator = targetGenerator;
		this.inlineFrame = inlineFrame;
		this.invokeInstruction = invokeInstruction;
		this.code = code;
		this.outers = unmodifiableList(outers);
		this.arguments = unmodifiableList(arguments);
		this.result = result;
		this.completionBlock = completionBlock;
		this.reificationBlock = reificationBlock;

		// TODO MvG – Seed the frameMap.
 		// this.frameMap.put(topFrame, inlineFrame);
	}

	/**
	 * Inline the supplied function invocation.
	 */
	void generateInline ()
	{
		// TODO MvG - Implement.  Scan code's chunk's CFG's blocks in order,
		// verifying that predecessor blocks have all run before starting each
		// new one.  Scan all instructions within the block.  Remember to update
		// the dependency in the targetGenerator to include anything the inlined
		// chunk depended on.

		final L2Chunk inlinedChunk = code.startingChunk();
		// Caller must ensure the code being inlined is L2-optimized and valid.
		assert inlinedChunk != L2Chunk.unoptimizedChunk;
		assert inlinedChunk.isValid();
		assert targetGenerator.currentlyReachable()
			: "Inlined code is not reachable!";
		final L2ControlFlowGraph graph = inlinedChunk.controlFlowGraph();
		for (final L2BasicBlock block : graph.basicBlockOrder)
		{
			final L2BasicBlock newBlock = mapBlock(block);
			targetGenerator.startBlock(newBlock);
			if (targetGenerator.currentlyReachable())
			{
				for (final L2Instruction instruction : block.instructions())
				{
					instruction.transformAndEmitOn(this);
				}
			}
		}
		// Add the inlined chunk's dependencies.
		for (final A_ChunkDependable dependency
			: inlinedChunk.contingentValues())
		{
			targetGenerator.addContingentValue(dependency);
		}




		assert false;
	}

	/**
	 * Transform the given {@link L2BasicBlock}.  Use the {@link #blockMap},
	 * adding an entry if necessary.
	 *
	 * @param block The basic block to look up.
	 * @return The looked up or created-and-stored basic block.
	 */
	public L2BasicBlock mapBlock (final L2BasicBlock block)
	{
		return blockMap.computeIfAbsent(
			block, b -> targetGenerator.createBasicBlock(b.name()));
	}

	/**
	 * Transform the given {@link L2Register}.  Use the {@link #registerMap},
	 * adding an entry if necessary.
	 *
	 * @param register The {@link L2Register} to look up.
	 * @return The looked up or created-and-stored {@link L2Register}.
	 */
	@SuppressWarnings("unchecked")
	public <R extends L2Register> R mapRegister (final R register)
	{
		final R copy = (R) registerMap.computeIfAbsent(
			register, r -> r.copyForInliner(this));
		assert register.registerKind() == copy.registerKind();
		return copy;
	}

	/**
	 * Transform an {@link L2PcOperand}'s {@link L2ValueManifest} in preparation
	 * for inlining.  The new manifest should take into account the bindings of
	 * the old manifest, but shifted into a sub-{@link Frame}, combined with the
	 * {@link #targetGenerator}'s {@link L2Generator#currentManifest()}.
	 *
	 * @return The new {@link L2ValueManifest}.
	 */
	public L2ValueManifest mapManifest (final L2ValueManifest oldManifest)
	{
		return oldManifest.transform(this::mapSemanticValue, this::mapFrame);
	}

	/**
	 * Transform an {@link L2SemanticValue} into another one by substituting
	 * {@link #inlineFrame} for the top frame everywhere it occurs structurally
	 * within the given semantic value.
	 *
	 * @param oldSemanticValue
	 *        The original {@link L2SemanticValue} from the callee's code.
	 * @return The replacement {@link L2SemanticValue}.
	 */
	public L2SemanticValue mapSemanticValue (
		final L2SemanticValue oldSemanticValue)
	{
		return semanticValueMap.computeIfAbsent(
			oldSemanticValue,
			old -> old.transform(this::mapSemanticValue, this::mapFrame));
	}

	/**
	 * Transform a {@link Frame} by replacing the top frame with {@link
	 * #inlineFrame}.
	 *
	 * @param frame
	 *        The original {@link Frame} from the callee's code.
	 * @return The replacement {@link Frame}.
	 */
	public Frame mapFrame (final Frame frame)
	{
		final Frame mapped = frameMap.get(frame);
		if (mapped != null)
		{
			return mapped;
		}
		assert frame.outerFrame != null
			: "The frameMap should have been seeded with the outer frame.";
		final Frame mappedOuter = mapFrame(frame.outerFrame);
		final Frame newFrame = new Frame(mappedOuter, frame.code, "Inlined");
		frameMap.put(frame, newFrame);
		return newFrame;
	}

	/**
	 * Emit an {@link L2Instruction} into the {@link L1Translator}'s current
	 * block.  Use the given {@link L2Operation} and {@link L2Operand}s to
	 * construct the instruction.  The operands should have been transformed by
	 * this inliner already.
	 *
	 * @param operation
	 *        The {@link L2Operation} of the instruction.
	 * @param operands
	 *        The {@link L2Operand}s of the instruction, having already been
	 *        transformed for this inliner.
	 */
	public void emitInstruction (
		final L2Operation operation,
		final L2Operand... operands)
	{
		targetGenerator.addInstruction(operation, operands);
	}

	/**
	 * Generate a number unique within the {@link #targetGenerator}.
	 *
	 * @return An {@code int} that the targetGenerator had not previously
	 *         produced.
	 */
	public int nextUnique ()
	{
		return targetGenerator.nextUnique();
	}
}
