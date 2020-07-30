/*
 * L2Inliner.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.optimizer.reoptimizer

import com.avail.descriptor.functions.A_RawFunction
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandDispatcher
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2CommentOperand
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2Operand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import com.avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import com.avail.interpreter.levelTwo.operand.L2SelectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L2BasicBlock
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.values.Frame
import com.avail.optimizer.values.L2SemanticValue
import com.avail.utility.cast
import java.util.Collections

/**
 * This is used to transform and embed a called function's chunk's control flow
 * graph into the calling function's chunk's control flow graph.  Doing so:
 *
 *  * eliminates the basic cost of the call and return,
 *  * passes parameters in and result out with moves that are easily
 *    eliminated,
 *  * allows stronger call-site types to narrow method lookups,
 *  * exposes primitive cancellation patterns like &lt;x, y&gt;[1] → x,
 *
 *  * exposes L1 instruction cancellation, like avoiding creation of
 *    closures and label continuations,
 *  * allows nearly all conditional and loop control flow to be expressed
 *    as simple jumps,
 *  * exposes opportunities to operate on intermediate values in an unboxed
 *    form.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property targetGenerator
 *   The [L2Generator] on which to output the transformed L2 code.
 * @property inlineFrame
 *   The [Frame] representing the invocation being inlined.
 * @property invokeInstruction
 *   The invoke-like [L2Instruction] being inlined.
 * @property code
 *   The [A_RawFunction] being inlined.
 * @property result
 *   The register to write the result of the inlined call into.
 * @property completionBlock
 *   The [L2BasicBlock] that should be reached when the inlined call completes
 *   successfully.
 * @property reificationBlock
 *   The [L2BasicBlock] that should be reached when reification happens during
 *   the inlined call.
 *
 * @constructor
 * Construct a new `L2Inliner`.
 *
 * @param targetGenerator
 *   The [L2Generator] on which to write new instructions.
 * @param inlineFrame
 *   The [Frame] representing this call site.  The top frame in each manifest
 *   of the callee, including embedded inside other [L2SemanticValue]s, must be
 *   transformed into the provided inlineFrame.
 * @param invokeInstruction
 *   The [L2Instruction] that invokes the function being inlined.
 * @param code
 *   The [A_RawFunction] being inlined.
 * @param outers
 *   The [List] of [L2ReadBoxedOperand]s that were captured by the function
 *   being inlined.
 * @param arguments
 *   The [List] of [L2ReadBoxedOperand]s corresponding to the arguments to this
 *   function invocation.
 * @param result
 *   Where to cause the function's result to be written.
 * @param completionBlock
 *   Where to arrange to jump after the function invocation.
 * @param reificationBlock
 *   Where to arrange to jump on reification while executing the inlined
 *   function.
 */
class L2Inliner internal constructor(
	val targetGenerator: L2Generator,
	val inlineFrame: Frame,
	val invokeInstruction: L2Instruction,
	val code: A_RawFunction,
	outers: List<L2ReadBoxedOperand>,
	arguments: List<L2ReadBoxedOperand>,
	val result: L2WriteBoxedOperand,
	val completionBlock: L2BasicBlock,
	val reificationBlock: L2BasicBlock)
{
	/**
	 * An [L2OperandDispatcher] subclass suitable for copying operands for the
	 * enclosing [L2Inliner].
	 */
	internal inner class OperandInlineTransformer : L2OperandDispatcher
	{
		/**
		 * The current operand being transformed.  It gets set before a dispatch
		 * and read afterward, allowing the dispatch operation to replace it.
		 */
		var currentOperand: L2Operand? = null

		override fun doOperand(operand: L2CommentOperand) = Unit

		override fun doOperand(operand: L2ConstantOperand)  = Unit

		override fun doOperand(operand: L2IntImmediateOperand)  = Unit

		override fun doOperand(operand: L2FloatImmediateOperand)  = Unit

		override fun doOperand(operand: L2PcOperand)
		{
			// Manifest of edge must already be defined if we're inlining.
			// There isn't a solid plan yet about how to narrow the types.
			val oldManifest = operand.manifest()
			currentOperand = L2PcOperand(
				mapBlock(operand.targetBlock()),
				operand.isBackward,
				mapManifest(oldManifest))
		}

		override fun doOperand(operand: L2PrimitiveOperand) = Unit

		override fun doOperand(operand: L2ReadIntOperand)
		{
			currentOperand = L2ReadIntOperand(
				mapSemanticValue(operand.semanticValue()),
				operand.restriction(),
				targetManifest())
		}

		override fun doOperand(operand: L2ReadFloatOperand)
		{
			currentOperand = L2ReadFloatOperand(
				mapSemanticValue(operand.semanticValue()),
				operand.restriction(),
				targetManifest())
		}

		override fun doOperand(operand: L2ReadBoxedOperand)
		{
			currentOperand = L2ReadBoxedOperand(
				mapSemanticValue(operand.semanticValue()),
				operand.restriction(),
				targetManifest())
		}

		override fun doOperand(operand: L2ReadBoxedVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadBoxedVectorOperand(
				operand.elements()
					.map { op: L2ReadBoxedOperand -> transformOperand(op) })
		}

		override fun doOperand(operand: L2ReadIntVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadIntVectorOperand(
				operand.elements()
					.map { op: L2ReadIntOperand -> transformOperand(op) })
		}

		override fun doOperand(operand: L2ReadFloatVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadFloatVectorOperand(
				operand.elements()
					.map { op: L2ReadFloatOperand -> transformOperand(op) })
		}

		override fun doOperand(operand: L2SelectorOperand) = Unit

		override fun doOperand(operand: L2WriteIntOperand)
		{
			currentOperand = targetGenerator.intWrite(
				mapSemanticValue(operand.pickSemanticValue()),
				operand.restriction())
		}

		override fun doOperand(operand: L2WriteFloatOperand)
		{
			currentOperand = targetGenerator.floatWrite(
				mapSemanticValue(operand.pickSemanticValue()),
				operand.restriction())
		}

		override fun doOperand(operand: L2WriteBoxedOperand)
		{
			currentOperand = targetGenerator.boxedWrite(
				mapSemanticValue(operand.pickSemanticValue()),
				operand.restriction())
		}
	}

	/** This inliner's reusable [OperandInlineTransformer].  */
	private val operandInlineTransformer = OperandInlineTransformer()

	/**
	 * Produce a transformed copy of the given [L2Operand], strengthened to a
	 * suitable type.  *NOT* thread-safe for multiple threads using the same
	 * inliner.
	 *
	 * @param operand
	 *   The original [L2Operand] to transform of type [O].
	 * @param O
	 *   The [L2Operand] subtype.
	 * @return
	 *   The transformed [L2Operand], also of type [O].
	 */
	fun <O : L2Operand> transformOperand(operand: O): O
	{
		operandInlineTransformer.currentOperand = operand
		operand.dispatchOperand(operandInlineTransformer)
		return operandInlineTransformer.currentOperand!!.cast()
		// Don't bother clearing the currentOperand field afterward.
	}

	/** The registers providing values captured by the closure being inlined. */
	val outers: List<L2ReadBoxedOperand> = Collections.unmodifiableList(outers)

	/** The registers providing arguments to the invocation being inlined.  */
	val arguments: List<L2ReadBoxedOperand> =
		Collections.unmodifiableList(arguments)

	/**
	 * The accumulated mapping from original [L2BasicBlock]s to their
	 * replacements.  When code splitting is implemented, the key of this
	 * structure might be reworked as an &lt;[L2BasicBlock],
	 * [L2ValueManifest]&gt; pair.
	 */
	private val blockMap =
		mutableMapOf<L2BasicBlock, L2BasicBlock> ()

	/**
	 * The accumulated mapping from original [L2SemanticValue]s to their
	 * replacements.
	 */
	private val semanticValueMap =
		mutableMapOf<L2SemanticValue, L2SemanticValue>()

	/**
	 * The accumulated mapping from original [Frame]s to their replacements.
	 */
	private val frameMap = mutableMapOf<Frame?, Frame>()

	/**
	 * Answer the [L2ValueManifest] at the current target code generation
	 * position.
	 *
	 * @return
	 *   The target [L2Generator]'s [L2ValueManifest].
	 */
	fun targetManifest(): L2ValueManifest = targetGenerator.currentManifest()

	/**
	 * Inline the supplied function invocation.
	 */
	fun generateInline()
	{
		// TODO MvG - Implement.  Scan code's chunk's CFG's blocks in order,
		// verifying that predecessor blocks have all run before starting each
		// new one.  Scan all instructions within the block.  Remember to update
		// the dependency in the targetGenerator to include anything the inlined
		// chunk depended on.
		val inlinedChunk = code.startingChunk()
		assert(inlinedChunk != L2Chunk.unoptimizedChunk)
		assert(inlinedChunk.isValid)
		assert(targetGenerator.currentlyReachable())
			{ "Inlined code is not reachable!" }
		val graph = inlinedChunk.controlFlowGraph()
		for (block in graph.basicBlockOrder)
		{
			val newBlock = mapBlock(block)
			targetGenerator.startBlock(newBlock)
			if (targetGenerator.currentlyReachable())
			{
				for (instruction in block.instructions())
				{
					instruction.transformAndEmitOn(this)
				}
			}
		}
		// Add the inlined chunk's dependencies.
		for (dependency in inlinedChunk.contingentValues)
		{
			targetGenerator.addContingentValue(dependency)
		}
		assert(false)
	}

	/**
	 * Transform the given [L2BasicBlock].  Use the [blockMap], adding an entry
	 * if necessary.
	 *
	 * @param block
	 *   The basic block to look up.
	 * @return
	 *   The looked up or created-and-stored basic block.
	 */
	fun mapBlock(block: L2BasicBlock): L2BasicBlock =
		blockMap.computeIfAbsent(block) { b: L2BasicBlock ->
			targetGenerator.createBasicBlock(b.name())
		}

	/**
	 * Transform an [L2PcOperand]'s [L2ValueManifest] in preparation for
	 * inlining.  The new manifest should take into account the bindings of the
	 * old manifest, but shifted into a sub-[Frame], combined with the
	 * [targetGenerator]'s [L2Generator.currentManifest].
	 *
	 * @param oldManifest
	 *   The original [L2ValueManifest].
	 * @return
	 *   The new [L2ValueManifest].
	 */
	fun mapManifest(oldManifest: L2ValueManifest): L2ValueManifest =
		oldManifest.transform(this::mapSemanticValue)

	/**
	 * Transform an [L2SemanticValue] into another one by substituting
	 * [inlineFrame] for the top frame everywhere it occurs structurally within
	 * the given semantic value.
	 *
	 * @param oldSemanticValue
	 *   The original [L2SemanticValue] from the callee's code.
	 * @return
	 *   The replacement [L2SemanticValue].
	 */
	fun mapSemanticValue(oldSemanticValue: L2SemanticValue): L2SemanticValue =
		semanticValueMap.computeIfAbsent(oldSemanticValue) {
			old: L2SemanticValue ->
			old.transform(this::mapSemanticValue, this::mapFrame)
		}

	/**
	 * Transform a [Frame] by replacing the top frame with [inlineFrame].
	 *
	 * @param frame
	 *   The original [Frame] from the callee's code.
	 * @return
	 *   The replacement [Frame].
	 */
	fun mapFrame(frame: Frame?): Frame
	{
		val mapped = frameMap[frame]
		if (mapped !== null)
		{
			return mapped
		}
		assert(frame!!.outerFrame !== null)
			{ "The frameMap should have been seeded with the outer frame." }
		val mappedOuter = mapFrame(frame!!.outerFrame)
		val newFrame = Frame(mappedOuter, frame.code, "Inlined")
		frameMap[frame] = newFrame
		return newFrame
	}

	/**
	 * Emit an [L2Instruction] into the [L1Translator]'s current block.  Use the
	 * given [L2Operation] and [L2Operand]s to construct the instruction.  The
	 * operands should have been transformed by this inliner already.
	 *
	 * @param operation
	 *   The [L2Operation] of the instruction.
	 * @param operands
	 *   The [L2Operand]s of the instruction, having already been transformed
	 *   for this inliner.
	 */
	fun emitInstruction(operation: L2Operation, vararg operands: L2Operand)
	{
		targetGenerator.addInstruction(operation, *operands)
	}

	/**
	 * Generate a number unique within the [targetGenerator].
	 *
	 * @return
	 *   An [Int] that the targetGenerator had not previously produced.
	 */
	fun nextUnique(): Int = targetGenerator.nextUnique()

	init
	{
		// TODO MvG – Seed the frameMap.
		// this.frameMap.put(topFrame, inlineFrame);
	}
}
