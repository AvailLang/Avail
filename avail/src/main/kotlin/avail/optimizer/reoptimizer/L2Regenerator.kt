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
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2CommentOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadMixedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_FLOAT_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2Generator
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2GeneratorInterface.SpecialBlock
import avail.optimizer.L2Optimizer.Companion.shouldSanityCheck
import avail.optimizer.L2Optimizer.GenerationMode
import avail.optimizer.L2Optimizer.GenerationMode.ByRegister
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.L2Optimizer.GenerationMode.WithFixedRegisterMap
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.Companion.fakeCondition
import avail.optimizer.L2SplitCondition.Companion.reducedConditions
import avail.optimizer.L2Synonym
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedFloat
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.constant
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
 * @property mode
 *   Controls whether to produce phi instructions automatically based on
 *   [L2SemanticValue]s that are in common among incoming edges at merge points.
 *   This is generally [ByRegister] when phis have already been replaced with
 *   non-SSA moves.
 *
 * @constructor
 *   Construct a new `L2Regenerator`.
 */
abstract class L2Regenerator
constructor(
	private val targetGenerator: L2Generator,
	override val mode: GenerationMode
) : L2GeneratorInterface by targetGenerator
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

		override fun doOperand(operand: L2CommentOperand) = Unit

		override fun doOperand(operand: L2ConstantOperand) = Unit

		override fun doOperand(operand: L2IntImmediateOperand) = Unit

		override fun doOperand(operand: L2FloatImmediateOperand) = Unit

		override fun doOperand(operand: L2ArbitraryConstantOperand<*>) = Unit

		override fun doOperand(operand: L2PcOperand)
		{
			// Note: Even during code splitting, always produce an edge to the
			// no-conditions version of the replacement block.  The splitter has
			// the opportunity to generate new code in the no-conditions block
			// before any of the has-conditions variants, so just prior to that
			// we adjust the target of any *predecessors* of the no-conditions
			// block.  We can't make that selection here, because we don't have
			// complete manifest information for the edge yet.
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
					manifestInOldGraph.restrictionFor(equivalentInOldGraph)
				}
			}
			operand.instruction.readOperands
				.forEach { readOperand -> local(readOperand) }
			val edge = L2PcOperand(
				mapBlock(operand.targetBlock()),
				operand.isBackward,
				L2ValueManifest(manifestCopy),
				operand.optionalName)
			// Leave it up to the instruction's instructionWasAdded() to set up
			// the correct clamped values.
			currentOperand = edge
		}

		override fun doOperand(operand: L2ReadBoxedVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadBoxedVectorOperand(
				operand.elements.map(::transformOperand))
		}

		override fun doOperand(operand: L2ReadIntVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadIntVectorOperand(
				operand.elements.map(::transformOperand))
		}

		override fun doOperand(operand: L2ReadFloatVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadFloatVectorOperand(
				operand.elements.map(::transformOperand))
		}

		override fun doOperand(operand: L2ReadMixedVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2ReadMixedVectorOperand(
				operand.elements.map(::transformOperand))
		}

		override fun doOperand(operand: L2WriteBoxedVectorOperand)
		{
			// Note: this clobbers currentOperand, but we'll set it later.
			currentOperand = L2WriteBoxedVectorOperand(
				operand.elements.map(::transformOperand))
		}

		override fun doOperand(operand: L2PcVectorOperand)
		{
			currentOperand = L2PcVectorOperand(
				operand.edges.map(::transformOperand))
		}
	}

	/**
	 * An [OperandSemanticTransformer] is an [L2OperandDispatcher] suitable for
	 * copying operands for the enclosing [L2Regenerator], when operand
	 * equivalency is via [L2SemanticValue]s (i.e., when [mode] is
	 * [BySemanticValue]).
	 */
	inner class OperandSemanticTransformer : AbstractOperandTransformer()
	{
		override fun <K: RegisterKind<K>> mapReadSemanticValue(
			oldSemanticValue: L2SemanticValue<K>
		): L2SemanticValue<K>
		{
			val equivalent = currentManifest
				.equivalentPopulatedSemanticValue(oldSemanticValue)
			if (equivalent !== null) return equivalent
			assert(oldSemanticValue in currentManifest.postponedInstructions())
			return oldSemanticValue
		}

		override fun doOperand(operand: L2ReadIntOperand)
		{
			val equivalent = mapReadSemanticValue(operand.semanticValue())
			currentOperand = L2ReadIntOperand(
				equivalent,
				currentManifest
					.restrictionFor(equivalent)
					.intersection(operand.restriction()))
		}

		override fun doOperand(operand: L2ReadFloatOperand)
		{
			val equivalent = mapReadSemanticValue(operand.semanticValue())
			currentOperand = L2ReadFloatOperand(
				equivalent,
				currentManifest
					.restrictionFor(equivalent)
					.intersection(operand.restriction()))
		}

		override fun doOperand(operand: L2ReadBoxedOperand)
		{
			val equivalent = mapReadSemanticValue(operand.semanticValue())
			currentOperand = L2ReadBoxedOperand(
				equivalent,
				currentManifest
					.restrictionFor(equivalent)
					.intersection(operand.restriction()))
		}

		override fun doOperand(operand: L2WriteIntOperand)
		{
			currentOperand = L2WriteIntOperand(
				operand.semanticValues().mapToSet {
					mapWriteSemanticValue(it) as L2SemanticUnboxedInt
				},
				operand.restriction().restrictingKindsTo(UNBOXED_INT_FLAG.mask),
				L2IntRegister(nextUnique()))
		}

		override fun doOperand(operand: L2WriteFloatOperand)
		{
			currentOperand = L2WriteFloatOperand(
				operand.semanticValues().mapToSet {
					mapWriteSemanticValue(it) as L2SemanticUnboxedFloat
				},
				operand.restriction().restrictingKindsTo(
					UNBOXED_FLOAT_FLAG.mask),
				L2FloatRegister(nextUnique()))
		}

		override fun doOperand(operand: L2WriteBoxedOperand)
		{
			currentOperand = L2WriteBoxedOperand(
				operand.semanticValues().mapToSet { mapWriteSemanticValue(it) },
				operand.restriction().restrictingKindsTo(BOXED_FLAG.mask))
		}
	}

	/**
	 * An [OperandRegisterTransformer] is an [L2OperandDispatcher] suitable for
	 * copying operands for the enclosing [L2Regenerator], when operand
	 * equivalency is via [L2Register] identity (i.e., when [mode] is
	 * [ByRegister]).
	 *
	 * @constructor
	 *   Create an [OperandRegisterTransformer] with an optional [registerMap].
	 * @property registerMap
	 *   An optionally provided [MutableMap] for transforming [L2Register]s.
	 */
	inner class OperandRegisterTransformer
	constructor(
		private val registerMap: MutableMap<L2Register<*>, L2Register<*>> =
			mutableMapOf()
	): AbstractOperandTransformer()
	{
		override fun doOperand(operand: L2ReadIntOperand)
		{
			when
			{
				operand.isConstantRead ->
				{
					// Reuse the same register, since it can only be used as a
					// source of a constant read anyhow.
					currentOperand = INTEGER_KIND.readOperand(
						constant(operand.constantOrNull!!).unboxedInt,
						operand.restriction(),
						operand.register())
				}
				else ->
				{
					currentOperand = INTEGER_KIND.readOperand(
						operand.semanticValue(),
						operand.restriction(),
						registerMap[operand.register()] as L2IntRegister)
				}
			}
		}

		override fun doOperand(operand: L2ReadFloatOperand)
		{
			when
			{
				operand.isConstantRead ->
				{
					// Reuse the same register, since it can only be used as a
					// source of a constant read anyhow.
					currentOperand = FLOAT_KIND.readOperand(
						constant(operand.constantOrNull!!).unboxedFloat,
						operand.restriction(),
						operand.register())
				}
				else ->
				{
					currentOperand = FLOAT_KIND.readOperand(
						operand.semanticValue(),
						operand.restriction(),
						registerMap[operand.register()] as L2FloatRegister)
				}
			}
		}

		override fun doOperand(operand: L2ReadBoxedOperand)
		{
			when
			{
				operand.isConstantRead ->
				{
					// Reuse the same register, since it can only be used as a
					// source of a constant read anyhow.
					currentOperand = BOXED_KIND.readOperand(
						constant(operand.constantOrNull!!),
						operand.restriction(),
						operand.register())
				}
				else ->
				{
					currentOperand = BOXED_KIND.readOperand(
						operand.semanticValue(),
						operand.restriction(),
						registerMap[operand.register()] as L2BoxedRegister)
				}
			}
		}

		override fun doOperand(operand: L2WriteIntOperand)
		{
			val newRegister = registerMap.computeIfAbsent(operand.register()) {
				L2IntRegister(nextUnique())
			}
			currentOperand = L2WriteIntOperand(
				operand.semanticValues(),
				operand.restriction().restrictingKindsTo(UNBOXED_INT_FLAG.mask),
				newRegister as L2IntRegister)
		}

		override fun doOperand(operand: L2WriteFloatOperand)
		{
			val newRegister = registerMap.computeIfAbsent(operand.register()) {
				L2FloatRegister(nextUnique())
			}
			currentOperand = L2WriteFloatOperand(
				operand.semanticValues(),
				operand.restriction().restrictingKindsTo(
					UNBOXED_FLOAT_FLAG.mask),
				newRegister as L2FloatRegister)
		}

		override fun doOperand(operand: L2WriteBoxedOperand)
		{
			val newRegister = registerMap.computeIfAbsent(operand.register()) {
				L2BoxedRegister(nextUnique())
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
	 * Start code regeneration for the given [L2BasicBlock].  This is not a loop
	 * head, so ensure all predecessor blocks have already finished generation.
	 *
	 * If [mode] is [BySemanticValue] (the default), reconcile the live
	 * [L2SemanticValue]s and how they're grouped into [L2Synonym]s in each
	 * predecessor edge, creating [L2_PHI]s as needed.
	 *
	 * @param block
	 *   The [L2BasicBlock] beginning its code generation.
	 */
	fun startBlock(
		block: L2BasicBlock
	): Unit = startBlock(block, this)

	/** This regenerator's reusable [AbstractOperandTransformer]. */
	private val operandInlineTransformer = when (val m = mode)
	{
		BySemanticValue -> OperandSemanticTransformer()
		ByRegister -> OperandRegisterTransformer()
		is WithFixedRegisterMap -> OperandRegisterTransformer(m.registerMap)
	}

	/**
	 * The mapping from the [L2BasicBlock]s in the source graph to the generated
	 * [L2BasicBlock]s in the target graph, keyed by the set of required
	 * [L2SplitCondition]s.
	 */
	private val blockMap = mutableMapOf<
		L2BasicBlock,
		MutableMap<Set<L2SplitCondition>, L2BasicBlock>>()

	/**
	 * Transform the given [L2BasicBlock].  Use the [blockMap], adding an entry
	 * if necessary.  Always produce the block representing the default code
	 * splitting path, the one with no conditions.
	 *
	 * @param block
	 *   The basic block to look up.
	 * @return
	 *   The looked up or created-and-stored basic block.
	 */
	open fun mapBlock(block: L2BasicBlock): L2BasicBlock
	{
		return blockMap[block]!![emptySet()]!!
	}

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
	 * The client must set up the receiver to be generating at a reachable
	 * state, prior to this call.
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
		oldGraph.basicBlockOrder.forEach { originalBlock ->
			val newBlock = L2BasicBlock(
				name = originalBlock.name(),
				zone = originalBlock.zone,
				isLoopHead = originalBlock.isLoopHead,
				isCold = originalBlock.isCold)
			newBlock.debugNote.append(originalBlock.debugNote)
			blockMap[originalBlock] = mutableMapOf(
				emptySet<L2SplitCondition>() to newBlock)
			inverseSpecialBlockMap[originalBlock]?.let { special ->
				specialBlocks[special] = newBlock
			}
		}
		blockMap[firstSourceBlock] = mutableMapOf(
			emptySet<L2SplitCondition>() to start)
		oldGraph.forwardVisit { originalBlock ->
			// All predecessors must have already been processed.
			val submap = blockMap[originalBlock]!!
			val noConditionBlock = submap[emptySet()]!!
			// If true, force each incoming edge to go to a new version of the
			// target block, even if there was no benefit to splitting.  This is
			// only used to ensure entry point blocks have only one predecessor.
			val forceCodeSplit = originalBlock.entryPointOrNull() !== null
				&& noConditionBlock.predecessorEdges().size > 1
			val interestingConditions =
				interestingConditionsByOldBlock[originalBlock] ?: emptySet()
			if (forceCodeSplit || interestingConditions.isNotEmpty())
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
							setOf(fakeCondition(nextUnique()))
						}
						else
						{
							interestingConditions.filterTo(mutableSetOf()) {
								it.holdsFor(incomingEdge.manifest())
							}
						}
					val betterBlock = submap.computeIfAbsent(trueConditions) {
						val reduced = reducedConditions(trueConditions)
						val suffix = when (reduced.size)
						{
							0 -> "\n(no split)"
							1 -> "\nsplit: ${reduced.single().toString()}"
							else -> reduced
								.joinToString(",", "\nsplits:") { "\n\t$it" }
						}
						val newBlock = L2BasicBlock(
							name = originalBlock.name() + suffix,
							zone = originalBlock.zone,
							isLoopHead = originalBlock.isLoopHead,
							isCold = originalBlock.isCold)
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
				startBlock(targetBlock)
				if (!currentlyReachable()) return@forEach
				if (mode == BySemanticValue)
				{
					// Since the incoming edges in the old graph are the only
					// place where a relevant manifest still exists, we take the
					// intersection of the sets of semantic values that were
					// present along these edges.  And in case we're removing
					// dead code, narrow this to the semantic values that are
					// live here.
					val commonSemanticValues = currentManifest.synonymsArray()
						.flatMapTo(mutableSetOf(), L2Synonym<*>::semanticValues)
					val manifests = originalBlock.predecessorEdges()
						.map(L2PcOperand::manifest)
					manifests.forEach { m ->
						commonSemanticValues.retainAll(m::hasSemanticValue)
					}
					// For each semantic value, determine all other semantic
					// values that are in the same synonym with it in all
					// predecessors.  We'll use that to reconstitute any
					// synonyms that we may have missed in the new manifest.
					val commonSynonyms = commonSemanticValues
						.associateWithTo(mutableMapOf()) { sv ->
							manifests
								.map {
									it.semanticValueToSynonym(sv)
										.semanticValues()
								}
								.reduce(Set<L2SemanticValue<*>>::intersect)
								.intersect(commonSemanticValues)
						}
					// Compute the union of the restrictions for each semantic
					// value.  We'll use that to narrow the restrictions in the
					// new manifest.
					val commonRestrictions =
						commonSemanticValues.associateWith { sv ->
							manifests
								.map { it.restrictionFor(sv) }
								.reduce(TypeRestriction::union)
						}
					// To ease computation, keep only one representative of each
					// synonymous set.  The sets are disjoint, and their members
					// were synonymous in each predecessor, so that will cover
					// all current synonyms.
					val synonymRepresentatives =
						mutableSetOf<L2SemanticValue<*>>()
					commonSynonyms.forEach { sv, set ->
						if (set.intersect(synonymRepresentatives).isEmpty())
						{
							// Only keep a candidate if it's in the current
							// manifest, since we might be stripping dead code.
							// Either at least one will be alive, or we don't
							// need to preserve the synonym's information.
							if (currentManifest.hasSemanticValue(sv))
							{
								synonymRepresentatives.add(sv)
							}
						}
					}
					// We now have one (live) representative from each common
					// incoming synonym.  We can iterate over them to process
					// the synonym merges and restrictions.
					synonymRepresentatives.forEach { sv ->
						assert(currentManifest.hasSemanticValue(sv))
						commonSynonyms[sv]!!.forEach { otherSv ->
							currentManifest.dynamicMergeExistingSemanticValues(
								sv, otherSv)
						}
						currentManifest.updateRestriction(sv) {
							commonRestrictions[sv]!!
						}
					}
				}
				originalBlock.instructions().forEach(::processInstruction)
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
		val transformed = basicTransformInstruction(sourceInstruction)
		if (!transformed.hasSideEffect
			&& sourceInstruction.writeOperands.size == 1)
		{
			// No side-effect, and it only produces one value.  See if there is
			// already an extant equivalent value that we can just move.  This
			// embedded inline function is parametric on RegisterKind to allow
			// parameterization by correlated RegisterKind.
			fun <K: RegisterKind<K>> tryPopulate(
				write: L2WriteOperand<K>
			): Boolean
			{
				write.semanticValues()
					.firstOrNull { readIfAvailable(it) != null }
					?.let { existing ->
						// Found one. Populate the rest..
						val others = write.semanticValues()
							.filterNot(currentManifest::hasSemanticValue)
						if (others.isNotEmpty())
						{
							moveRegister(existing, others)
							return true
						}
					}
				return false
			}
			if (tryPopulate(sourceInstruction.writeOperands.single())) return
			// There wasn't an equivalent register handy.  Fall back to emitting
			// a copy of this instruction.
		}
		basicTransformInstruction(sourceInstruction).run {
			emitTransformedInstruction()
		}
	}

	/**
	 * Transform the instruction's operands, updating the isomorphism, and emit
	 * the same kind of instruction.
	 *
	 * @param sourceInstruction
	 *   An [L2Instruction] from the source graph.
	 */
	fun <I : L2Instruction> basicTransformInstruction(sourceInstruction: I): I
	{
		// Never translate a phi instruction.  Either they should be produced
		// as part of generation, or they should already have been replaced by
		// moves.
		assert(sourceInstruction !is L2_PHI<*>)
		val transformed = sourceInstruction.transformedByRegenerator(this)
		if (shouldSanityCheck)
		{
			// Special sanity check.
			for (read in transformed.readOperands)
			{
				val semanticValue = read.semanticValue()
				if (read.constantOrNull == null && mode == BySemanticValue)
				{
					assert(
						currentManifest.hasSemanticValue(semanticValue) ||
							semanticValue in
								currentManifest.postponedInstructions())
				}
			}
		}
		return transformed.cast()
	}
}
