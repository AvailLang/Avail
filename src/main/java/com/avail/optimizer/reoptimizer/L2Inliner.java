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

import com.avail.descriptor.A_ChunkDependable;
import com.avail.descriptor.A_RawFunction;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2CommentOperand;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2FloatImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2InternalCounterOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2SelectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteFloatOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2ControlFlowGraph;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticValue;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.avail.utility.Casts.cast;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

/**
 * This is used to transform and embed a called function's chunk's control flow
 * graph into the calling function's chunk's control flow graph.  Doing so:
 *
 * <ul>
 *     <li>eliminates the basic cost of the call and return,</li>
 *     <li>passes parameters in and result out with moves that are easily
 *     eliminated,</li>
 *     <li>allows stronger call-site types to narrow method lookups,</li>
 *     <li>exposes primitive cancellation patterns like &lt;x, y&gt;[1] → x,
 *     </li>
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
	class OperandInlineTransformer
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
			// Manifest of edge must already be defined if we're inlining.
			// There isn't a solid plan yet about how to narrow the types.
			final L2ValueManifest oldManifest = operand.manifest();
			currentOperand = new L2PcOperand(
				operand,
				mapBlock(operand.targetBlock()),
				mapManifest(oldManifest));
		}

		@SuppressWarnings("EmptyMethod")
		@Override
		public void doOperand (final L2PrimitiveOperand operand) { }

		@Override
		public void doOperand (final L2ReadIntOperand operand)
		{
			currentOperand =
				new L2ReadIntOperand(
					mapSemanticValue(operand.semanticValue()),
					operand.restriction(),
					targetManifest());
		}

		@Override
		public void doOperand (final L2ReadFloatOperand operand)
		{
			currentOperand =
				new L2ReadFloatOperand(
					mapSemanticValue(operand.semanticValue()),
					operand.restriction(),
					targetManifest());
		}

		@Override
		public void doOperand (final L2ReadBoxedOperand operand)
		{
			currentOperand = new L2ReadBoxedOperand(
				mapSemanticValue(operand.semanticValue()),
				operand.restriction(),
				targetManifest());
		}

		@Override
		public void doOperand (final L2ReadBoxedVectorOperand operand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = new L2ReadBoxedVectorOperand(
				operand.elements().stream()
					.map(L2Inliner.this::transformOperand)
					.collect(toList()));
		}

		@Override
		public void doOperand (final L2ReadIntVectorOperand operand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = new L2ReadIntVectorOperand(
				operand.elements().stream()
					.map(L2Inliner.this::transformOperand)
					.collect(toList()));
		}

		@Override
		public void doOperand (final L2ReadFloatVectorOperand operand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = new L2ReadFloatVectorOperand(
				operand.elements().stream()
					.map(L2Inliner.this::transformOperand)
					.collect(toList()));
		}

		@SuppressWarnings("EmptyMethod")
		@Override
		public void doOperand (final L2SelectorOperand operand) { }

		@Override
		public void doOperand (final L2WriteIntOperand operand)
		{
			currentOperand = targetGenerator.intWrite(
				mapSemanticValue(operand.semanticValue()),
				operand.restriction());
		}

		@Override
		public void doOperand (final L2WriteFloatOperand operand)
		{
			currentOperand = targetGenerator.floatWrite(
				mapSemanticValue(operand.semanticValue()),
				operand.restriction());
		}

		@Override
		public void doOperand (final L2WriteBoxedOperand operand)
		{
			currentOperand = targetGenerator.boxedWrite(
				mapSemanticValue(operand.semanticValue()),
				operand.restriction());
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
	final L2Generator targetGenerator;

	/** The {@link Frame} representing the invocation being inlined. */
	final Frame inlineFrame;

	/** The invoke-like {@link L2Instruction} being inlined. */
	final L2Instruction invokeInstruction;

	/** The {@link A_RawFunction} being inlined. */
	final A_RawFunction code;

	/** The registers providing values captured by the closure being inlined. */
	final List<L2ReadBoxedOperand> outers;

	/** The registers providing arguments to the invocation being inlined. */
	final List<L2ReadBoxedOperand> arguments;

	/** The register to write the result of the inlined call into. */
	final L2WriteBoxedOperand result;

	/**
	 * The {@link L2BasicBlock} that should be reached when the inlined call
	 * completes successfully.
	 */
	final L2BasicBlock completionBlock;

	/**
	 * The {@link L2BasicBlock} that should be reached when reification happens
	 * during the inlined call.
	 */
	final L2BasicBlock reificationBlock;

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
	 * Answer the {@link L2ValueManifest} at the current target code generation
	 * position.
	 *
	 * @return The target {@link L2Generator}'s {@link L2ValueManifest}.
	 */
	L2ValueManifest targetManifest ()
	{
		return targetGenerator.currentManifest();
	}

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
	 * @param invokeInstruction
	 *        The {@link L2Instruction} that invokes the function being inlined.
	 * @param code
	 *        The {@link A_RawFunction} being inlined.
	 * @param outers
	 *        The {@link List} of {@link L2ReadBoxedOperand}s that were captured
	 *        by the function being inlined.
	 * @param arguments
	 *        The {@link List} of {@link L2ReadBoxedOperand}s corresponding to
	 *        the arguments to this function invocation.
	 * @param result
	 *        Where to cause the function's result to be written.
	 * @param completionBlock
	 *        Where to arrange to jump after the function invocation.
	 * @param reificationBlock
	 *        Where to arrange to jump on reification while executing the
	 *        inlined function.
	 */
	L2Inliner (
		final L2Generator targetGenerator,
		final Frame inlineFrame,
		final L2Instruction invokeInstruction,
		final A_RawFunction code,
		final List<L2ReadBoxedOperand> outers,
		final List<L2ReadBoxedOperand> arguments,
		final L2WriteBoxedOperand result,
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
	 * Transform an {@link L2PcOperand}'s {@link L2ValueManifest} in preparation
	 * for inlining.  The new manifest should take into account the bindings of
	 * the old manifest, but shifted into a sub-{@link Frame}, combined with the
	 * {@link #targetGenerator}'s {@link L2Generator#currentManifest()}.
	 *
	 * @param oldManifest The original {@link L2ValueManifest}.
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
