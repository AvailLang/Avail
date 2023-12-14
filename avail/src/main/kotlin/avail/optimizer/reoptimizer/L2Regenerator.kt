/*
 * L2Regenerator.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
import avail.optimizer.L2SplitCondition
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast
import avail.utility.mapToSet

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
		 * Transform the given [L2BasicBlock].  Use the [blockMap], adding an
		 * entry if necessary.  Always produce the block representing the
		 * default code splitting path, the one with no conditions.
		 *
		 * @param block
		 *   The basic block to look up.
		 * @return
		 *   The looked up or created-and-stored basic block.
		 */
		open fun mapBlock(block: L2BasicBlock): L2BasicBlock
		{
			val special = inverseSpecialBlockMap[block]
			val submap = blockMap.computeIfAbsent(block) { mutableMapOf() }
			// Here we always select the block representing the unconstrained
			// path.  The optimizer will tweak this after the whole instruction
			// has been translated, so that the manifests will be available on
			// the outgoing edges.
			return submap.computeIfAbsent(emptySet()) {
				val newBlock = L2BasicBlock(
					block.name(),
					block.isLoopHead,
					block.zone)
				if (block.isIrremovable) newBlock.makeIrremovable()
				if (special !== null)
				{
					// Don't split special blocks.
					assert(targetGenerator.specialBlocks[special] == null)
					targetGenerator.specialBlocks[special] = newBlock
				}
				newBlock
			}
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
			// Note: Even during code splitting, always produce an edge to the
			// no-conditions version of the replacement block.  The splitter has
			// the opportunity to generate new code in the no-conditions block
			// before any of the has-conditions variants, so just prior to that
			// we adjust the target of any *predecessors* of the no-conditions
			// block.  We can't make that selection here, because we don't have
			// complete manifest information for the edge yet.
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
									manifest.getDefinition(entity, kind), kind))
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
				operand.elements.map(this@L2Regenerator::transformOperand))
		}

		override fun doOperand(operand: L2ReadIntVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadIntVectorOperand(
				operand.elements.map(this@L2Regenerator::transformOperand))
		}

		override fun doOperand(operand: L2ReadFloatVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadFloatVectorOperand(
				operand.elements.map(this@L2Regenerator::transformOperand))
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
				operand.semanticValues(),
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
				operand.semanticValues(),
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

	/**
	 * Answer whether this [L2Generator] is allowed to collapse unconditional
	 * jumps during code generation.  This is usually allowed, but the code
	 * splitter disallows it to make the logic simpler.
	 */
	open val canCollapseUnconditionalJumps: Boolean get() = true

	/** This regenerator's reusable [AbstractOperandTransformer]. */
	private val operandInlineTransformer =
		if (generatePhis) OperandSemanticTransformer()
		else OperandRegisterTransformer()

	/**
	 * The mapping from the [L2BasicBlock]s in the source graph to the generated
	 * [L2BasicBlock]s in the target graph, keyed by the set of required
	 * [L2SplitCondition]s.
	 */
	val blockMap = mutableMapOf<
		L2BasicBlock,
		MutableMap<Set<L2SplitCondition>, L2BasicBlock>>()

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
	open fun <O : L2Operand> transformOperand(operand: O): O
	{
		operandInlineTransformer.currentOperand = operand
		operand.dispatchOperand(operandInlineTransformer)
		return operandInlineTransformer.currentOperand!!.cast()
		// Don't bother clearing the currentOperand field afterward.
	}

	/**
	 * Given an original [L2ControlFlowGraph], translate each [L2BasicBlock] (in
	 * topological order), translating each instruction within that block.
	 * Since the new graph has essentially the same shape as the original, we
	 * don't need to do any special synchronization at merge points. There's no
	 * way for a block of the original to be reached before one of its
	 * predecessors, and the translation of an instruction can't suddenly jump
	 * to a point in the target graph that corresponds with an earlier point in
	 * the source graph.  Therefore, when we reach a block in the original, we
	 * can safely assume that its translation(s) in the target graph have
	 * already had their predecessors completely generated.
	 *
	 * The client must set up the [targetGenerator] to be generating at a
	 * reachable state, prior to this call.
	 *
	 * @param oldGraph
	 *   The [L2ControlFlowGraph] from which to start translation and code
	 *   generation.
	 * @param interestingConditionsByOldBlock
	 *   A map from each [L2BasicBlock] in the [oldGraph] to a set of split
	 *   conditions, each of which should be preserved for potential use by a
	 *   downstream block.  Create a new [L2BasicBlock] for each encountered
	 *   (old block, split set), where the split set is a subset of conditions
	 *   to be preserved at this old block, and was actually ensured by some
	 *   regenerated edge.
	 */
	fun processSourceGraph(
		oldGraph: L2ControlFlowGraph,
		interestingConditionsByOldBlock:
			Map<L2BasicBlock, Set<L2SplitCondition>>)
	{
		val firstSourceBlock = oldGraph.basicBlockOrder[0]
		val start = L2BasicBlock(firstSourceBlock.name())
		start.makeIrremovable()
		blockMap[firstSourceBlock] = mutableMapOf(
			emptySet<L2SplitCondition>() to start)
		oldGraph.forwardVisit { originalBlock ->
			// All predecessors must have already been processed.
			val submap = blockMap.computeIfAbsent(originalBlock) {
				mutableMapOf(emptySet<L2SplitCondition>() to L2BasicBlock(
					originalBlock.name(),
					originalBlock.isLoopHead,
					originalBlock.zone))
			}
			val interestingConditions =
				interestingConditionsByOldBlock[originalBlock] ?: emptySet()
			if (interestingConditions.isNotEmpty())
			{
				// During translation of previous blocks, they were all directed
				// to point to the no-condition image of this block.  Do the
				// code splitting here, *altering* the target of each
				// predecessor if it should target something more specific than
				// the no-condition block.  We have to do it here rather than at
				// edge generation time, because the edge's manifest isn't fully
				// available then.
				val noConditionBlock = submap[emptySet()]!!
				// Copy the list because we'll be removing the entries that
				// should point elsewhere.
				val incomingEdges = noConditionBlock.predecessorEdges().toList()
				incomingEdges.forEach { incomingEdge ->
					val trueConditions =
						interestingConditions.filterTo(mutableSetOf()) {
							it.holdsFor(incomingEdge.manifest())
						}

					val betterBlock = submap.computeIfAbsent(trueConditions) {
						val suffix = when (trueConditions.size)
						{
							1 -> "\nsplit: ${trueConditions.single()}"
							else -> "\nsplits:\n\t" +
								submap.keys
									.filter(Set<*>::isNotEmpty)
									.joinToString(
										",\n\t", transform = Any::toString)
						}
						val newBlock = L2BasicBlock(
							originalBlock.name() + suffix,
							originalBlock.isLoopHead,
							originalBlock.zone)
						if (originalBlock.isIrremovable)
							newBlock.makeIrremovable()
						newBlock
					}
					if (betterBlock !== incomingEdge.targetBlock())
					{
						// This also removes it from the current block's
						// predecessors, but above we made sure to iterate over
						// a copy.
						incomingEdge.changeUngeneratedTarget(betterBlock)
					}
				}
			}
			submap.forEach { (_, targetBlock) ->
				targetGenerator.startBlock(targetBlock, generatePhis, this)
				if (targetGenerator.currentlyReachable())
				{
					originalBlock.instructions().forEach(::processInstruction)
				}
			}
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
	 * A helper method for instruction postponement.  Given an [L2Regenerator]
	 * and an [L2Instruction] from the old graph being regenerated, emit a
	 * translated version of that instruction.  If the instruction uses values
	 * that are not yet available in registers due to postponement, first
	 * translate the instructions that produce those values.
	 *
	 * TODO Make this iterative instead of recursive.
	 */
	fun forcePostponedTranslationNow(sourceInstruction: L2Instruction)
	{
		val manifest = targetGenerator.currentManifest
		for (read in sourceInstruction.readOperands)
		{
			val semanticValue = read.semanticValue()
			// If there's a postponed instruction that produces the needed
			// value, always use that, even if the value appears to be
			// available in a register.  That's because a postponed instruction
			// like L2_MAKE_IMMUTABLE can't hide the registers that hold the
			// mutable inputs (because forcing the L2_MAKE_IMMUTABLE to be
			// translated would still have to access the mutable inputs).
			if (semanticValue in manifest.postponedInstructions)
			{
				// There's no register yet (or it's shadowed by a postponed
				// instruction.  Generate (recursively) the instruction(s)
				// needed to produce the value in a register.
				forcePostponedTranslationNow(
					manifest.removePostponedSourceInstruction(semanticValue))
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
			targetGenerator.currentManifest.postponedInstructions.forEach {
					(_, instructions) ->
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
			if (postponedSet.isEmpty()) return
			postponedSet.forEach { sourceInstruction ->
				// Check if it was already emitted as a prerequisite of another
				// postponed instruction.
				val someWrite = sourceInstruction.writeOperands[0]
				val sv = someWrite.pickSemanticValue()
				val manifest = targetGenerator.currentManifest
				if (manifest.postponedInstructions[sv]?.isNotEmpty() == true)
				{
					forcePostponedTranslationNow(
						manifest.removePostponedSourceInstruction(sv))
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
		semanticValue: L2SemanticValue)
	{
		targetGenerator.generateRetroactivelyBeforeEdge(edge) {
			forcePostponedTranslationNow(
				targetGenerator.currentManifest
					.removePostponedSourceInstruction(semanticValue))
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
