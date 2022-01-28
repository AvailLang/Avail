/*
 * L2Regenerator.kt
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
package avail.optimizer.reoptimizer

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2SelectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_FLOAT_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.L2Register.RegisterKind
import avail.optimizer.L1Translator
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2EntityAndKind
import avail.optimizer.L2Generator
import avail.optimizer.L2Generator.SpecialBlock
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast
import avail.utility.mapToSet
import java.util.ArrayDeque

/**
 * This is used to transform and embed a called function's chunk's control flow
 * graph into the calling function's chunk's control flow graph.  Doing so:
 *
 *  * eliminates the basic cost of the call and return,
 *  * passes parameters in and result out with moves that are easily
 *    eliminated,
 *  * allows stronger call-site types to narrow method lookups,
 *  * exposes primitive cancellation patterns like `<x,y>[1] → x`,
 *  * exposes L1 instruction cancellation, like avoiding creation of
 *    closures and label continuations,
 *  * allows nearly all conditional and loop control flow to be expressed
 *    as simple jumps,
 *  * exposes opportunities to operate on intermediate values in an unboxed
 *    form.
 *
 * In addition, this class also gives the opportunity to postpone the
 * transformation of certain virtualized [L2Instruction]s like
 * [L2_VIRTUAL_CREATE_LABEL] into a subgraph of real instructions, *after* they
 * have been moved and duplicated as a unit through [L2ControlFlowGraph].
 *
 * Finally, it can be used for code-splitting, where a section of code is
 * duplicated and specialized to take advantage of stronger type knowledge at a
 * point where merging control flow paths would destroy that extra information.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property targetGenerator
 *   The [L2Generator] on which to output the transformed L2 code.
 * @property generatePhis
 *   Whether to produce phi instructions automatically based on semantic values
 *   that are in common among incoming edges at merge points.  This is false
 *   when phis have already been replaced with non-SSA moves.
 *
 * @constructor
 *   Construct a new `L2Regenerator`.
 */
abstract class L2Regenerator internal constructor(
	val targetGenerator: L2Generator,
	private val generatePhis: Boolean)
{
	/**
	 * An [AbstractOperandTransformer] is an [L2OperandDispatcher] suitable for
	 * transforming operands for the enclosing [L2Regenerator].  Subclasses may
	 * choose different strategies for mapping the registers underlying the
	 * read and write operands.
	 */
	abstract inner class AbstractOperandTransformer : L2OperandDispatcher
	{
		/**
		 * The current operand being transformed.  It gets set before a dispatch
		 * and read afterward, allowing the dispatch operation to replace it.
		 */
		var currentOperand: L2Operand? = null

		/**
		 * The mapping from the [L2BasicBlock]s in the source graph to the
		 * corresponding `L2BasicBlock` in the target graph.
		 */
		private val blockMap = mutableMapOf<L2BasicBlock, L2BasicBlock>()

		/**
		 * Transform the given [L2BasicBlock].  Use the [blockMap], adding an
		 * entry if necessary.
		 *
		 * @param block
		 *   The basic block to look up.
		 * @return
		 *   The looked up or created-and-stored basic block.
		 */
		open fun mapBlock(block: L2BasicBlock) =
			blockMap.computeIfAbsent(block) { oldBlock: L2BasicBlock ->
				val newBlock = L2BasicBlock(
					oldBlock.name(), oldBlock.isLoopHead, oldBlock.zone)
				if (oldBlock.isIrremovable) newBlock.makeIrremovable()
				inverseSpecialBlockMap[oldBlock]?.let { special ->
					targetGenerator.specialBlocks[special] = newBlock
				}
				newBlock
			}

		/**
		 * Transform the given [L2SemanticValue] into another.  By default this
		 * does nothing, but for a subclass that performs inlining, this can be
		 * useful for indicating that a semantic value is for the inlined frame
		 * rather than outer frame.
		 *
		 * @param oldSemanticValue
		 *   The original [L2SemanticValue] from the source graph.
		 * @return
		 *   The replacement [L2SemanticValue].
		 */
		open fun mapSemanticValue(oldSemanticValue: L2SemanticValue) =
			oldSemanticValue

		override fun doOperand(operand: L2ArbitraryConstantOperand) = Unit

		override fun doOperand(operand: L2CommentOperand) = Unit

		override fun doOperand(operand: L2ConstantOperand) = Unit

		override fun doOperand(operand: L2IntImmediateOperand) = Unit

		override fun doOperand(operand: L2FloatImmediateOperand) = Unit

		override fun doOperand(operand: L2PcOperand)
		{
			// Add the source edge to the appropriate queue.
			when
			{
				operand.isBackward -> backEdgeVisitQueue.add(operand)
				else -> edgeVisitQueue.add(operand)
			}
			// Note: ignore the manifest of the source edge.
			val edge = L2PcOperand(
				mapBlock(operand.targetBlock()),
				operand.isBackward,
				targetGenerator.currentManifest,
				operand.optionalName)
			// Generate clamped entities based on the originals.
			operand.forcedClampedEntities?.let { oldClamped ->
				val manifest = targetGenerator.currentManifest
				val newClamped = mutableSetOf<L2EntityAndKind>()
				oldClamped.forEach { entityAndKind ->
					val (entity, kind) = entityAndKind
					when (entity)
					{
						is L2SemanticValue ->
						{
							// Clamp the same L2SemanticValue in the target as
							// was clamped in the source.
							newClamped.add(entityAndKind)
							newClamped.add(
								L2EntityAndKind(
									manifest.getDefinition(entity, kind),
									kind))
						}
					}
				}
				edge.forcedClampedEntities = newClamped
			}
			currentOperand = edge
		}

		override fun doOperand(operand: L2PrimitiveOperand) = Unit

		override fun doOperand(operand: L2ReadBoxedVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadBoxedVectorOperand(
				operand.elements().map(this@L2Regenerator::transformOperand))
		}

		override fun doOperand(operand: L2ReadIntVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadIntVectorOperand(
				operand.elements().map(this@L2Regenerator::transformOperand))
		}

		override fun doOperand(operand: L2ReadFloatVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadFloatVectorOperand(
				operand.elements().map(this@L2Regenerator::transformOperand))
		}

		override fun doOperand(operand: L2SelectorOperand) = Unit

		override fun doOperand(operand: L2PcVectorOperand)
		{
			currentOperand = L2PcVectorOperand(
				operand.edges.map(this@L2Regenerator::transformOperand))
		}
	}

	/**
	 * An [OperandSemanticTransformer] is an [L2OperandDispatcher] suitable for
	 * copying operands for the enclosing [L2Regenerator], when operand
	 * equivalency is via [L2SemanticValue]s (i.e., when [generatePhis] is
	 * `true`).
	 */
	inner class OperandSemanticTransformer : AbstractOperandTransformer()
	{
		override fun doOperand(operand: L2ReadIntOperand)
		{
			currentOperand = L2ReadIntOperand(
				mapSemanticValue(operand.semanticValue()),
				targetGenerator.currentManifest.restrictionFor(
					operand.semanticValue()),
				targetGenerator.currentManifest)
		}

		override fun doOperand(operand: L2ReadFloatOperand)
		{
			currentOperand = L2ReadFloatOperand(
				mapSemanticValue(operand.semanticValue()),
				targetGenerator.currentManifest.restrictionFor(
					operand.semanticValue()),
				targetGenerator.currentManifest)
		}

		override fun doOperand(operand: L2ReadBoxedOperand)
		{
			currentOperand = L2ReadBoxedOperand(
				mapSemanticValue(operand.semanticValue()),
				targetGenerator.currentManifest.restrictionFor(
					operand.semanticValue()),
				targetGenerator.currentManifest)
		}

		override fun doOperand(operand: L2WriteIntOperand)
		{
			currentOperand = L2WriteIntOperand(
				operand.semanticValues().mapToSet {
					mapSemanticValue(it) as L2SemanticUnboxedInt
				},
				operand.restriction().restrictingKindsTo(UNBOXED_INT_FLAG.mask),
				L2IntRegister(targetGenerator.nextUnique()))
		}

		override fun doOperand(operand: L2WriteFloatOperand)
		{
			currentOperand = L2WriteFloatOperand(
				operand.semanticValues().mapToSet {
					mapSemanticValue(it) as L2SemanticUnboxedFloat
				},
				operand.restriction().restrictingKindsTo(
					UNBOXED_FLOAT_FLAG.mask),
				L2FloatRegister(targetGenerator.nextUnique()))
		}

		override fun doOperand(operand: L2WriteBoxedOperand)
		{
			currentOperand = L2WriteBoxedOperand(
				operand.semanticValues().mapToSet { mapSemanticValue(it) },
				operand.restriction().restrictingKindsTo(BOXED_FLAG.mask),
				L2BoxedRegister(targetGenerator.nextUnique()))
		}
	}

	/**
	 * An [OperandRegisterTransformer] is an [L2OperandDispatcher] suitable for
	 * copying operands for the enclosing [L2Regenerator], when operand
	 * equivalency is via [L2Register] identity (i.e., when [generatePhis] is
	 * `false`).
	 */
	inner class OperandRegisterTransformer : AbstractOperandTransformer()
	{
		/**
		 * The mapping from each [L2Register] in the source control flow graph
		 * to the corresponding register in the target graph.
		 */
		private val registerMap = mutableMapOf<L2Register, L2Register>()

		override fun doOperand(operand: L2ReadIntOperand)
		{
			currentOperand = L2ReadIntOperand(
				operand.semanticValue(),
				targetGenerator.currentManifest.restrictionFor(
					operand.semanticValue()),
				registerMap[operand.register()] as L2IntRegister)
		}

		override fun doOperand(operand: L2ReadFloatOperand)
		{
			currentOperand = L2ReadFloatOperand(
				operand.semanticValue(),
				targetGenerator.currentManifest.restrictionFor(
					operand.semanticValue()),
				registerMap[operand.register()] as L2FloatRegister)
		}

		override fun doOperand(operand: L2ReadBoxedOperand)
		{
			currentOperand = L2ReadBoxedOperand(
				operand.semanticValue(),
				targetGenerator.currentManifest.restrictionFor(
					operand.semanticValue()),
				registerMap[operand.register()] as L2BoxedRegister)
		}

		override fun doOperand(operand: L2WriteIntOperand)
		{
			val newRegister =
				registerMap.computeIfAbsent(operand.register()) {
					val unique = targetGenerator.nextUnique()
					L2IntRegister(unique)
				}
			currentOperand = L2WriteIntOperand(
				operand.semanticValues().cast(),
				operand.restriction().restrictingKindsTo(UNBOXED_INT_FLAG.mask),
				newRegister as L2IntRegister)
		}

		override fun doOperand(operand: L2WriteFloatOperand)
		{
			val newRegister =
				registerMap.computeIfAbsent(operand.register()) {
					val unique = targetGenerator.nextUnique()
					L2FloatRegister(unique)
				}
			currentOperand = L2WriteFloatOperand(
				operand.semanticValues().cast(),
				operand.restriction().restrictingKindsTo(
					UNBOXED_FLOAT_FLAG.mask),
				newRegister as L2FloatRegister)
		}

		override fun doOperand(operand: L2WriteBoxedOperand)
		{
			val newRegister =
				registerMap.computeIfAbsent(operand.register()) {
					val unique = targetGenerator.nextUnique()
					L2BoxedRegister(unique)
				}
			currentOperand = L2WriteBoxedOperand(
				operand.semanticValues(),
				operand.restriction().restrictingKindsTo(BOXED_FLAG.mask),
				newRegister as L2BoxedRegister)
		}
	}

	/** This regenerator's reusable [AbstractOperandTransformer]. */
	private val operandInlineTransformer =
		if (generatePhis) OperandSemanticTransformer()
		else OperandRegisterTransformer()

	/**
	 * An inverse mapping from source block to [SpecialBlock].  This is used
	 * to detect when we need to record a corresponding target block as a
	 * [SpecialBlock].
	 */
	val inverseSpecialBlockMap = mutableMapOf<L2BasicBlock, SpecialBlock>()

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

	/**
	 * The set of [L2BasicBlock]s from the source graph that have already been
	 * completely processed.  See [processSourceGraphStartingAt].
	 */
	private val completedSourceBlocks = mutableSetOf<L2BasicBlock>()

	/**
	 * A queue of [L2PcOperand]s from the source graph, which have not yet been
	 * processed.  A block is translated when all of its normal (forward)
	 * predecessor blocks have completed translation, and are therefore in the
	 * [completedSourceBlocks].  After all forward edges have been exhausted,
	 * the remaining backward edges are processed, which should always lead
	 * directly to blocks that have already been processed.
	 *
	 * See [processSourceGraphStartingAt].
	 */
	private val edgeVisitQueue = ArrayDeque<L2PcOperand>()

	/**
	 * A queue of *back-edge* [L2PcOperand]s from the source graph.  These are
	 * processed only after all forward edges have completed, thereby finishing
	 * all nodes of the source graph.
	 *
	 * See [processSourceGraphStartingAt].
	 */
	private val backEdgeVisitQueue = ArrayDeque<L2PcOperand>()

	/**
	 * Given an [L2Instruction] from the source [L2ControlFlowGraph], start
	 * translating and emitting to the target graph from that point, until
	 * nothing else is reachable.  Process any encountered back-edges after all
	 * forward edges and blocks have completed.
	 *
	 * The client must set up the [targetGenerator] to be generating at a
	 * reachable state, prior to this call.
	 *
	 * @param startingInstruction
	 *   The [L2Instruction] of the source control flow graph from which to
	 *   start translation and code generation.
	 */
	fun processSourceGraphStartingAt(startingInstruction: L2Instruction)
	{
		assert(edgeVisitQueue.isEmpty())
		assert(backEdgeVisitQueue.isEmpty())
		assert(targetGenerator.currentlyReachable()) {
			"Caller should have set up starting block, or other starting state"
		}
		val startBlock = startingInstruction.basicBlock()
		val startIndex = startBlock.instructions().indexOf(startingInstruction)
		assert(startIndex >= 0)
		for (i in startIndex until startBlock.instructions().size) {
			processInstruction(startBlock.instructions()[i])
		}
		completedSourceBlocks.add(startBlock)
		blocks@while (edgeVisitQueue.isNotEmpty())
		{
			val block = edgeVisitQueue.remove().targetBlock()
			if (completedSourceBlocks.contains(block))
			{
				// This can happen when the code splitter primes the
				// completedSourceBlocks to indicate where the "ends" of the
				// source subgraph that is being specialized are, and where they
				// lead to in the target graph.
				continue@blocks
			}
			if (block.predecessorEdges().any {
					!it.isBackward
						&& !completedSourceBlocks.contains(it.sourceBlock()) })
			{
				// At least one predecessor has not been processed yet.  This
				// block will get a chance later, once the other predecessors
				// have been processed.
				continue@blocks
			}
			// All forward-edge predecessors have already been processed.
			targetGenerator.startBlock(
				operandInlineTransformer.mapBlock(block),
				generatePhis,
				this)
			instructions@for (instruction in block.instructions())
			{
				if (!targetGenerator.currentlyReachable())
				{
					// Abort the rest of this block, since the target position
					// is unreachable.  Make sure to also mark it as complete,
					// to ensure any blocks that are reachable from it in the
					// source, but *also* from other blocks in the source, can
					// proceed with generation if all the other blocks have been
					// completed.  We also have to keep walking through any
					// successor edges, since there may be a whole subgraph that
					// has become inaccessible, and we must ensure paths that
					// eventually join with it (i.e., where it *would* have been
					// merging control flow) can continue code generation with
					// only some predecessors having produced code.
					block.successorEdges().forEach(this::transformOperand)
					break@instructions
				}
				processInstruction(instruction)
			}
			// Now consider the block to have been fully processed.
			completedSourceBlocks.add(block)
		}
		// Process any encountered back-edges.
		while (backEdgeVisitQueue.isNotEmpty())
		{
			val backEdge = backEdgeVisitQueue.remove()
			val block = backEdge.targetBlock()
			assert(completedSourceBlocks.contains(block))
			block.addPredecessorEdge(backEdge)
		}
	}

	/**
	 * Process the single instruction from the source graph, transforming it as
	 * needed.  Subclasses should override this to accomplish postponed
	 * instruction rewriting, code splitting, and inlining.  The typical result
	 * is to rewrite some translation of the instruction to the target graph.
	 *
	 * By default, simply transform the instruction's operands, updating the
	 * isomorphism, and emit the same kind of instruction.
	 *
	 * @param sourceInstruction
	 *   An [L2Instruction] from the source graph.
	 */
	open fun processInstruction(sourceInstruction: L2Instruction)
	{
		basicProcessInstruction(sourceInstruction)
	}

	/**
	 * Transform the instruction's operands, updating the isomorphism, and emit
	 * the same kind of instruction to the [targetGenerator].
	 *
	 * @param sourceInstruction
	 *   An [L2Instruction] from the source graph.
	 */
	fun basicProcessInstruction(sourceInstruction: L2Instruction)
	{
		// Never translate a phi instruction.  Either they should be produced
		// as part of generation, or they should already have been replaced by
		// moves.
		assert(!sourceInstruction.operation.isPhi)
		sourceInstruction.transformAndEmitOn(this)
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
	 * A helper method for instruction postponement.  Given an
	 * [L2Regenerator] and an [L2Instruction] from the old graph being
	 * regenerated, emit a translated version of that instruction.  If the
	 * instruction uses values that are not yet available in registers due
	 * to postponement, first translate the instructions that produce those
	 * values.
	 *
	 * TODO Make this iterative instead of recursive.
	 */
	fun forcePostponedTranslationNow(sourceInstruction: L2Instruction)
	{
		val manifest = targetGenerator.currentManifest
		for (read in sourceInstruction.readOperands)
		{
			val semanticValue = read.semanticValue()
			val kind = read.registerKind
			// If there's a postponed instruction that produces the needed
			// value, always use that, even if the value appears to be
			// available in a register.  That's because a postponed instruction
			// like L2_MAKE_IMMUTABLE can't hide the registers that hold the
			// mutable inputs (because forcing the L2_MAKE_IMMUTABLE to be
			// translated would still have to access the mutable inputs).
			if (manifest.postponedInstructions
					?.getOrNull(kind)
					?.get(semanticValue)
				!= null)
			{
				// There's no register yet (or it's shadowed by a postponed
				// instruction.  Generate (recursively) the instruction(s)
				// needed to produce the value in a register.
				forcePostponedTranslationNow(
					manifest.removePostponedSourceInstruction(
						kind, semanticValue))
			}
		}
		basicProcessInstruction(sourceInstruction)
	}

	/**
	 * Force all postponed instructions to be generated now.  Some of these may
	 * end up being considered dead code, and will be removed by a later pass.
	 *
	 * Don't translate [L2_MOVE_CONSTANT] instructions unless the value is
	 * needed by some other instruction being translated (i.e., skip them in
	 * this loop).
	 */
	fun forceAllPostponedTranslationsExceptConstantMoves()
	{
		while (true)
		{
			val postponedSet = mutableSetOf<L2Instruction>()
			targetGenerator.currentManifest.postponedInstructions?.forEach {
				(_, submap) ->
				submap.forEach { (_, instructions) ->
					// Only produce the last element of the list of postponed
					// instructions on each pass, to ensure instructions that
					// feed an L2_MAKE_IMMUTABLE don't try to translate
					// themselves before the L2_MAKE_IMMUTABLE.
					val instruction = instructions.last()
					if (instruction.operation !is L2_MOVE_CONSTANT<*, *, *>)
					{
						postponedSet.add(instruction)
					}
				}
			}
			if (postponedSet.isEmpty()) return
			postponedSet.forEach { sourceInstruction ->
				// Check if it was already emitted as a prerequisite of another
				// postponed instruction.
				val someWrite = sourceInstruction.writeOperands[0]
				val kind = someWrite.registerKind
				val sv = someWrite.pickSemanticValue()
				val manifest = targetGenerator.currentManifest
				if (manifest.postponedInstructions
						?.get(kind)
						?.get(sv)
						?.isNotEmpty()
					== true)
				{
					forcePostponedTranslationNow(
						manifest.removePostponedSourceInstruction(kind, sv))
				}
			}
		}
	}

	/**
	 * During a control flow merge, the phi creation mechanism detected that the
	 * incoming edges all provided a particular [L2SemanticValue] for a
	 * [RegisterKind], at least one source was a postponed instruction, and *not
	 * all* of the incoming edges had their values postponed by the same
	 * instruction.
	 *
	 * Go back to just before the edge (safe because it's in edge-split SSA
	 * form), and generate the postponed instruction responsible for the given
	 * kind and semantic value.  It's safe to generate these just prior to the
	 * last (control-flow altering) instruction of a predecessor block, because
	 * the control flow instruction didn't produce the desired semantic value,
	 * and new instructions won't interfere.  Update the edge's manifest during
	 * this code generation.
	 */
	fun forcePostponedTranslationBeforeEdge(
		edge: L2PcOperand,
		kind: RegisterKind,
		semanticValue: L2SemanticValue)
	{
		targetGenerator.generateRetroactivelyBeforeEdge(edge) {
			forcePostponedTranslationNow(
				targetGenerator.currentManifest
					.removePostponedSourceInstruction(kind, semanticValue))
		}
	}

	/**
	 * Generate a number unique within the [targetGenerator].
	 *
	 * @return
	 *   An [Int] that the targetGenerator had not previously produced.
	 */
	fun nextUnique(): Int = targetGenerator.nextUnique()
}
