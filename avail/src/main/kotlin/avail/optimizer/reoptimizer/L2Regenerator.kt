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
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2SelectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_FLOAT_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2Entity
import avail.optimizer.L2Generator
import avail.optimizer.L2Generator.SpecialBlock
import avail.optimizer.L2Optimizer.Companion.shouldSanityCheck
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2FakeCondition.Companion.fakeCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.Graph
import avail.utility.cast
import avail.utility.isNullOr
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
 * @property isRegeneratingDeadCode
 *   True iff this regenerator is being used to strip dead code from the
 *   graph, in which case even if [shouldSanityCheck] is true, we shouldn't
 *   attempt to check that synonyms have been reconstituted completely.
 *
 * @constructor
 *   Construct a new `L2Regenerator`.
 */
abstract class L2Regenerator internal constructor(
	val targetGenerator: L2Generator,
	private val generatePhis: Boolean,
	private val isRegeneratingDeadCode: Boolean)
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
		 * Transform the given [L2SemanticValue] into another.  This is a
		 * semantic value that is being used for reading, not writing.  By
		 * default this just looks for an equivalent (e.g., a
		 * [L2SemanticPrimitiveInvocation] whose arguments are in the same
		 * mutual synonyms), but for a subclass that performs inlining, this can
		 * be useful for indicating that a semantic value is for the inlined
		 * frame rather than outer frame.
		 *
		 * @param oldSemanticValue
		 *   The original [L2SemanticValue] from the source graph.
		 * @return
		 *   The replacement [L2SemanticValue].
		 */
		open fun <K: RegisterKind<K>> mapReadSemanticValue(
			oldSemanticValue: L2SemanticValue<K>
		): L2SemanticValue<K> = oldSemanticValue

		/**
		 * Transform the given [L2SemanticValue] into another, for the purpose
		 * of writing to it.  By default this does nothing, but for a subclass
		 * that performs inlining, this can be useful for indicating that a
		 * semantic value is for the inlined frame rather than outer frame.
		 *
		 * @param oldSemanticValue
		 *   The original [L2SemanticValue] from the source graph.
		 * @return
		 *   The replacement [L2SemanticValue].
		 */
		open fun <K: RegisterKind<K>> mapWriteSemanticValue(
			oldSemanticValue: L2SemanticValue<K>
		): L2SemanticValue<K> = oldSemanticValue

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
			//
			// As a great simplification, edges (like this one) leading from
			// instructions that *read* semantic values should have their
			// manifests initialized to a copy of the regenerator's current
			// manifest, but with the those semantic values, if still present in
			// the new manifest, restricted by the constraints in the manifests
			// on the original edges.  That should cover the majority of cases
			// with branches and such.  Also ensure any synonyms tied to those
			// semantic values get merged together.
			//
			// Operations that conditionally write to a register will still have
			// to do additional custom work with the manifests on their edges.
			val manifestInOldGraph = operand.manifest()
			val manifestCopy = L2ValueManifest(currentManifest)
			fun <K: RegisterKind<K>> local(relatedRead: L2ReadOperand<K>)
			{
				val equivalentInOldGraph = manifestInOldGraph
					.equivalentSemanticValue(relatedRead.semanticValue())
				equivalentInOldGraph ?: return
				val synonymInOldGraph = manifestInOldGraph
					.semanticValueToSynonym(equivalentInOldGraph)
				val semanticValuesInNewGraph = synonymInOldGraph
					.semanticValues()
					.filter(manifestCopy::hasSemanticValue)
				if (semanticValuesInNewGraph.isEmpty()) return
				// Merge the synonyms as indicated in the old edge's manifest.
				semanticValuesInNewGraph.zipWithNext(
					manifestCopy::mergeExistingSemanticValues)
				val equivalentInNewGraph = manifestCopy.equivalentSemanticValue(
					semanticValuesInNewGraph.first())!!
				// Restrict the new edge's manifest the same way.
				manifestCopy.updateRestriction(equivalentInNewGraph) {
					intersection(
						manifestInOldGraph.restrictionFor(equivalentInOldGraph))
				}
			}
			operand.instruction.readOperands
				.forEach { readOperand -> local(readOperand) }
			val edge = L2PcOperand(
				mapBlock(operand.targetBlock()),
				operand.isBackward,
				manifest = manifestCopy,
				operand.optionalName)
			// Generate clamped entities based on the originals.
			operand.forcedClampedEntities?.let { oldClamped ->
				val newClamped = mutableSetOf<L2Entity<*>>()
				oldClamped.forEach { entity ->
					when (entity)
					{
						is L2SemanticValue<*> ->
						{
							// Clamp the same L2SemanticValue in the target as
							// was clamped in the source.
							newClamped.add(entity)
							newClamped.add(manifestCopy.getDefinition(entity))
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
				operand.elements.map(::transformOperand).cast())
		}

		override fun doOperand(operand: L2ReadIntVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadIntVectorOperand(
				operand.elements.map(::transformOperand).cast())
		}

		override fun doOperand(operand: L2ReadFloatVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadFloatVectorOperand(
				operand.elements.map(::transformOperand).cast())
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
		override fun <K: RegisterKind<K>> mapReadSemanticValue(
			oldSemanticValue: L2SemanticValue<K>
		): L2SemanticValue<K>
		{
			return currentManifest
				.equivalentSemanticValue(oldSemanticValue)!!
		}

		override fun doOperand(operand: L2ReadIntOperand)
		{
			val equivalent = mapReadSemanticValue(operand.semanticValue())
			currentOperand = L2ReadIntOperand(
				equivalent,
				currentManifest
					.restrictionFor(equivalent)
					.intersection(operand.restriction()),
				currentManifest)
		}

		override fun doOperand(operand: L2ReadFloatOperand)
		{
			val equivalent = mapReadSemanticValue(operand.semanticValue())
			currentOperand = L2ReadFloatOperand(
				equivalent,
				currentManifest
					.restrictionFor(equivalent)
					.intersection(operand.restriction()),
				currentManifest)
		}

		override fun doOperand(operand: L2ReadBoxedOperand)
		{
			val equivalent = mapReadSemanticValue(operand.semanticValue())
			currentOperand = L2ReadBoxedOperand(
				equivalent,
				currentManifest
					.restrictionFor(equivalent)
					.intersection(operand.restriction()),
				currentManifest)
		}

		override fun doOperand(operand: L2WriteIntOperand)
		{
			currentOperand = L2WriteIntOperand(
				operand.semanticValues().mapToSet {
					mapWriteSemanticValue(it) as L2SemanticUnboxedInt
				},
				operand.restriction().restrictingKindsTo(UNBOXED_INT_FLAG.mask),
				L2IntRegister(targetGenerator.nextUnique()))
		}

		override fun doOperand(operand: L2WriteFloatOperand)
		{
			currentOperand = L2WriteFloatOperand(
				operand.semanticValues().mapToSet {
					mapWriteSemanticValue(it) as L2SemanticUnboxedFloat
				},
				operand.restriction().restrictingKindsTo(
					UNBOXED_FLOAT_FLAG.mask),
				L2FloatRegister(targetGenerator.nextUnique()))
		}

		override fun doOperand(operand: L2WriteBoxedOperand)
		{
			currentOperand = L2WriteBoxedOperand(
				operand.semanticValues().mapToSet { mapWriteSemanticValue(it) },
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
		private val registerMap = mutableMapOf<L2Register<*>, L2Register<*>>()

		override fun doOperand(operand: L2ReadIntOperand)
		{
			currentOperand = L2ReadIntOperand(
				operand.semanticValue(),
				currentManifest
					.restrictionFor(operand.semanticValue())
					.intersection(operand.restriction()),
				registerMap[operand.register()] as L2IntRegister)
		}

		override fun doOperand(operand: L2ReadFloatOperand)
		{
			currentOperand = L2ReadFloatOperand(
				operand.semanticValue(),
				currentManifest
					.restrictionFor(operand.semanticValue())
					.intersection(operand.restriction()),
				registerMap[operand.register()] as L2FloatRegister)
		}

		override fun doOperand(operand: L2ReadBoxedOperand)
		{
			currentOperand = L2ReadBoxedOperand(
				operand.semanticValue(),
				currentManifest
					.restrictionFor(operand.semanticValue())
					.intersection(operand.restriction()),
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

	/**
	 * Redirect this to the [targetGenerator].
	 */
	val currentManifest: L2ValueManifest get() = targetGenerator.currentManifest

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
			val noConditionBlock = submap[emptySet()]!!
			// If true, force each incoming edge to go to a new version of the
			// target block, even if there was no benefit to splitting.  This is
			// only used to ensure entry point blocks have only one predecessor.
			val forceCodeSplit = originalBlock.entryPointOrNull() !== null
				&& noConditionBlock.predecessorEdges().size > 1
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
				// Copy the list because we'll be removing the entries that
				// should point elsewhere.
				val incomingEdges = noConditionBlock.predecessorEdges().toList()
				incomingEdges.forEach { incomingEdge ->
					val trueConditions =
						if (forceCodeSplit)
						{
							setOf(fakeCondition(targetGenerator.nextUnique()))
						}
						else
						{
							interestingConditions.filterTo(mutableSetOf()) {
								it.holdsFor(incomingEdge.manifest())
							}
						}

					val betterBlock = submap.computeIfAbsent(trueConditions) {
						val suffix = when (trueConditions.size)
						{
							0 -> "\n(no split)"
							1 -> "\nsplit: ${trueConditions.single()}"
							else -> submap.keys
								.filter(Set<*>::isNotEmpty)
								.joinToString(",", "\nsplit:") { "\n\t$it" }
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
				val manifest = currentManifest
				if (targetGenerator.currentlyReachable())
				{
					if (shouldSanityCheck && !isRegeneratingDeadCode)
					{
						// Make sure every semantic value that was present at
						// this position in the original graph is available at
						// this new block, which is one of the code-split
						// versions of the old block.
						// Since the incoming edges in the old graph are the
						// only place where a relevant manifest still exists, we
						// take the intersection of the sets of semantic values
						// that were present along these edges.
						val iterator = originalBlock.predecessorEdges()
							.map {
								it.manifest().liveOrPostponedSemanticValues()
							}
							.iterator()
						if (iterator.hasNext())
						{
							val originals = iterator.next()  // It's a copy.
							iterator.forEachRemaining(originals::retainAll)
							val missing = originals.filter {
								// We only care about the boxed ones, since on
								// some code-split paths the unboxed ones may or
								// may not be available.
								it is L2SemanticBoxedValue &&
									manifest.equivalentSemanticValue(it) ===
										null
							}
							assert(missing.isEmpty())
							{
								// These semantic values were present in the
								// previous version of the graph, so they are
								// *required* to be present in the copy.
								val providers = missing.associateWith { oldSV ->
									originalBlock.predecessorEdges()
										.flatMap {
											it.manifest().getDefinitions(oldSV)
										}
										.flatMapTo(
											mutableSetOf(),
											L2Register<*>::definitions)
										.map(L2WriteOperand<*>::instruction)
								}
								buildString {
									append("Some semantic values should have ")
									append("been present in the regenerated ")
									append("graph:")
									providers.forEach { (oldSV, instructions) ->
										append("\n\t")
										append(oldSV)
										append(" ->:\n\t\t")
										instructions.joinTo(this, "\n\t\t")
									}
								}
							}
						}
					}
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
	 * Emit an [L2Instruction] into the [L2Regenerator]'s current block.  Use
	 * the given [L2Operation] and [L2Operand]s to construct the instruction.
	 * The operands should have been transformed by this inliner already.
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
	 * The instruction must not currently be in the current
	 * `postponedInstructions` map.
	 *
	 * TODO Make this iterative instead of recursive.
	 */
	fun forcePostponedTranslationNow(sourceInstruction: L2Instruction)
	{
		val manifest = currentManifest
		assert(
			!shouldSanityCheck ||
			sourceInstruction.writeOperands
				.flatMap(L2WriteOperand<*>::semanticValues)
				.all { sv ->
					manifest.postponedInstructions()[sv].isNullOr {
						!contains(sourceInstruction)
					}
				}
		) { "instruction should have been removed from postponed map"}
		sourceInstruction.readOperands.forEach { read ->
			forceTranslationForRead(read, manifest)
		}
		basicProcessInstruction(sourceInstruction)
	}

	/**
	 * Force all postponed instructions for any semantic value synonymous with
	 * the given one
	 */
	private fun <K: RegisterKind<K>> forceTranslationForRead(
		read: L2ReadOperand<K>,
		manifest: L2ValueManifest)
	{
		val semanticValue = read.semanticValue()
		// If there's a postponed instruction that produces the needed value,
		// always use that, even if the value appears to be available in a
		// register.
		val postponedMap = manifest.postponedInstructions()
		manifest.semanticValueToSynonym(semanticValue)
			.semanticValues()
			.mapNotNull(postponedMap::get)
			.maxByOrNull(List<*>::size)
			?.let { list ->
				val instruction = list.last()
				manifest.removePostponedSourceInstruction(instruction)
				forcePostponedTranslationNow(instruction)
			}
		// At this point there must not be any other postponed instructions for
		// the semantic value's synonym.
		assert(manifest.semanticValueToSynonym(semanticValue).semanticValues()
			.none(postponedMap::contains))
		// We just generated the side-effectless instruction that populated
		// equivalentSemanticValue, so emit a move to include semanticValue, and
		// anything else that should be populated by the same write operand.
		for (otherSemanticValue in read.definition().semanticValues())
		{
			if (!manifest.hasSemanticValue(otherSemanticValue))
			{
				manifest.extendSynonym(
					manifest.semanticValueToSynonym(semanticValue),
					otherSemanticValue)
			}
		}
	}

	/**
	 * Force all postponed instructions to be generated now.  Some of these may
	 * end up being considered dead code, and will be removed by a later pass.
	 *
	 * If [omitConstantMoves] is true, don't translate [L2_MOVE_CONSTANT]
	 * instructions unless the value is needed by some other instruction being
	 * translated here.
	 */
	fun forceAllPostponedTranslationsExceptConstantMoves(
		semanticValue: L2SemanticValue<*>? = null,
		omitConstantMoves: Boolean)
	{
		val manifest = currentManifest
		// Copy the map but not the contained mutable lists.
		if (semanticValue == null)
		{
			manifest.postponedInstructions().keys.toList().forEach { sv ->
				// We're modifying postponedInstructions, so check if it's still
				// present.
				if (sv in manifest.postponedInstructions())
				{
					forceAllPostponedTranslationsExceptConstantMoves(
						sv, omitConstantMoves)
				}
			}
			if (shouldSanityCheck)
			{
				manifest.postponedInstructions().values.forEach { sub ->
					sub.forEach { instruction ->
						assert(instruction.operation is L2_MOVE<*>
							|| instruction.operation is L2_MOVE_CONSTANT<*, *>)
					}
				}
			}
			return
		}
		val lists = manifest.semanticValueToSynonym(semanticValue)
			.semanticValues()
			.mapNotNull { manifest.postponedInstructions()[it] }
			.distinct()
		val list: List<L2Instruction> = when (lists.size)
		{
			0 -> return
			1 -> lists.single().toList()
			else ->
			{
				// Combine multiple lists.  The orderings of instructions in
				// each list must be preserved in the aggregate order, but other
				// than that, the order doesn't matter.  None of the lists
				// should have an ordering constraint that conflicts with
				// another list.  Keep it simple and use a graph.
				val graph = Graph<L2Instruction>()
				lists.forEach { it.forEach(graph::includeVertex) }
				lists.forEach { sub ->
					(0 ..< sub.size - 1).forEach { i ->
						graph.includeEdge(sub[i], sub[i + 1])
					}
				}
				assert(!graph.isCyclic)
				val order = mutableListOf<L2Instruction>()
				graph.parallelVisit { instr, done ->
					order.add(instr)
					done()
				}
				order
			}
		}
		if (omitConstantMoves &&
			list.all {
				it.operation is L2_MOVE<*> ||
					it.operation is L2_MOVE_CONSTANT<*, *> })
		{
			// There are only moves and constant moves here.  Leave them
			// postponed for now.
			return
		}
		// The list is already a copy here.  Remove all of these postponed
		// instructions before anything else.
		list.forEach(manifest::removePostponedSourceInstruction)
		list.forEach { instruction ->
			// The mutable list may have had instructions removed.  In fact,
			// the original map may have had whole entries removed, but not
			// without emptying entry's list first.
			forcePostponedTranslationNow(instruction)
		}
		// At this point, only move-constants may still be postponed.
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
		semanticValue: L2SemanticValue<*>)
	{
		targetGenerator.generateRetroactivelyBeforeEdge(edge) {
			forceAllPostponedTranslationsExceptConstantMoves(
				semanticValue, false)
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
