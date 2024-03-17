/*
 * L2_MULTIWAY_JUMP.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectTypeDescriptor
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i31
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TypeTag
import avail.descriptor.types.TypeTag.Companion.tagFromOrdinal
import avail.dispatch.LookupTree
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.ARBITRARY_CONSTANT
import avail.interpreter.levelTwo.L2OperandType.Companion.PC_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_INT
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.optimizer.L2ControlFlowGraph.Zone
import avail.optimizer.L2ControlFlowGraph.ZoneType
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.L2MeetsRestrictionCondition.Companion.typeRestrictionCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticBoxedValue
import avail.utility.cast
import avail.utility.notNullAnd
import avail.utility.removeLast
import org.objectweb.asm.MethodVisitor
import kotlin.math.max
import kotlin.math.min

/**
 * Given an integer and a constant tuple of N distinct integers in ascending
 * order, determine where the provided integer fits between the integers in the
 * tuple.  Then jump to one of the N+1 targets based on that computed index.
 *
 * If the integer is less than the first entry, jump to the first target.  If
 * the integer is greater than or equal to the first entry but less than the
 * second entry, jump to the second target, and so on.  If the integer is
 * greater than the last entry, jump to the N+1st target.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object L2_MULTIWAY_JUMP : L2ConditionalJump(
	READ_INT.named("value"),
	ARBITRARY_CONSTANT.named("splitter"),
	PC_VECTOR.named("branch edges", SUCCESS))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val value = instruction.operand<L2ReadIntOperand>(0)
		val splitterConstant =
			instruction.operand<L2ArbitraryConstantOperand>(1)
		//val edges = instruction.operand<L2PcVectorOperand>(2)

		renderPreamble(instruction, builder)
		builder
			.append(" ")
			.append(value.registerString())
			.append(" in ")
			.append(extractSplitter(splitterConstant))
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder)
	}

	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		// Note: Instructions that add multi-way jumps always set up the
		// manifests in the edges.
		val value = instruction.operand<L2ReadIntOperand>(0)
		val splitterConstant =
			instruction.operand<L2ArbitraryConstantOperand>(1)
		val edges = instruction.operand<L2PcVectorOperand>(2)

		value.instructionWasAdded(manifest)
		splitterConstant.instructionWasAdded(manifest)
		edges.edges.forEach { edge ->
			// Feed the edge its own manifest.
			edge.instructionWasAdded(edge.manifest())
		}
	}

	override val isPlaceholder get() = true

	/**
	 * Extract the [AbstractMultiWaySplitter] from the given
	 * [L2ConstantOperand].
	 */
	private fun extractSplitter(
		constantOperand: L2ArbitraryConstantOperand
	) = constantOperand.constant as AbstractMultiWaySplitter

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		val value = transformedOperands[0] as L2ReadIntOperand
		val splitterConstant =
			transformedOperands[1] as L2ArbitraryConstantOperand
		val edges = transformedOperands[2] as L2PcVectorOperand

		// Delegate to the MultiWaySplitter.
		extractSplitter(splitterConstant)
			.emitInstruction(value, edges.edges, regenerator)
	}

	override fun interestingSplitConditions(
		instruction: L2Instruction
	): List<L2SplitCondition?>
	{
		val value = instruction.operand<L2ReadIntOperand>(0)
		val splitterConstant =
			instruction.operand<L2ArbitraryConstantOperand>(1)
		val edges = instruction.operand<L2PcVectorOperand>(2)

		// Delegate to the MultiWaySplitter.
		return extractSplitter(splitterConstant)
			.interestingConditions(value, edges.edges)
	}

	override fun generateReplacement(
		instruction: L2Instruction,
		regenerator: L2Regenerator)
	{
		//TODO: If a lookupswitch instruction would be better than the binary
		// search mechanism, we could just do a super call to leave this
		// instruction intact, and then alter translateToJVM to generate the
		// lookupswitch instruction.
		assert(this == instruction.operation)
		val value = regenerator.transformOperand(
			instruction.operand<L2ReadIntOperand>(0))
		val splitterConstant = regenerator.transformOperand(
			instruction.operand<L2ArbitraryConstantOperand>(1))
		val edges = regenerator.transformOperand(
			instruction.operand<L2PcVectorOperand>(2))

		val splitter = extractSplitter(splitterConstant)
		generateSubtree(
			regenerator,
			value,
			splitter,
			splitter.originalValueSource(value),
			edges.edges,
			1,
			splitter.splitPoints.size,
			ZoneType.MULTI_WAY_EXPANSION.createZone(
				"multi-way branch:\n\tsplits = ${splitter.splitPoints}"))
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		throw UnsupportedOperationException(
			"${javaClass.simpleName} should " +
				"have been replaced during optimization")
	}

	/**
	 * Use the split values with indices `[`firstSplit..lastSplit`]` to
	 * determine which target to jump to.  For example if `firstSplit = 1` and
	 * `lastSplit = 1`, it should test against splitPoints`[`1`]` and branch to
	 * either edges`[`1`]` or edges`[`2`]`.  As another example, if
	 * `firstSplit = 5` and `lastSplit = 4`, this indicates edges`[`5`]` should
	 * be used without a further test.
	 *
	 * @param regenerator
	 *   The [L2Regenerator] to generate the subtree.
	 * @param value
	 *   The [L2ReadIntOperand] value to generate the subtree for.
	 * @param splitter
	 *   The [AbstractMultiWaySplitter] controlling this multi-way jump.
	 * @param originalValueSource
	 *   If available, this is the [L2SemanticBoxedValue] representing the
	 *   original from which the int value has been extracted, such as a tag or
	 *   variant id.
	 * @param edges
	 *   The edges list as a [List] of [L2PcOperand]s.  There should be one more
	 *   than there are split points in the [splitter].
	 * @param firstSplit
	 *   The index of the first split point.
	 * @param lastSplit
	 *   The index of the last split point.
	 * @param zone
	 *   The optional [Zone] in which to create new basic blocks.
	 */
	private fun generateSubtree(
		regenerator: L2Regenerator,
		value: L2ReadIntOperand,
		splitter: AbstractMultiWaySplitter,
		originalValueSource: L2SemanticBoxedValue?,
		edges: List<L2PcOperand>,
		firstSplit: Int,
		lastSplit: Int,
		zone: Zone?)
	{
		if (!regenerator.currentlyReachable()) return
		if (firstSplit > lastSplit)
		{
			// It's a leaf.
			assert(firstSplit == lastSplit + 1)
			val edge = edges[firstSplit - 1]  // Convert to zero-based.
			regenerator.addInstruction(L2_JUMP, edge)
			return
		}
		// It's not a leaf.  Pick a split point near the middle of the range.
		val splitIndex = (firstSplit + lastSplit) ushr 1
		val splitValue = splitter.splitPoints[splitIndex - 1]
		val (leftName, rightName) = splitter.leftAndRightTargetNames(
			regenerator.currentManifest.restrictionFor(value.semanticValue()),
			splitValue)
		val leftBlock = regenerator.createBasicBlock(leftName, zone)
		val rightBlock = regenerator.createBasicBlock(rightName, zone)
		regenerator.compareAndBranchInt(
			NumericComparator.GreaterOrEqual,
			value,
			regenerator.unboxedIntConstant(splitValue),
			L2PcOperand(
				rightBlock,
				false,
				optionalName = rightName),
			L2PcOperand(
				leftBlock,
				false,
				optionalName = leftName))
		// Recursively generate the left side.
		regenerator.startBlock(leftBlock)
		generateSubtree(
			regenerator,
			value,
			splitter,
			originalValueSource,
			edges,
			firstSplit,
			splitIndex - 1,
			zone)
		// Recursively generate the right side.
		regenerator.startBlock(rightBlock)
		generateSubtree(
			regenerator,
			value,
			splitter,
			originalValueSource,
			edges,
			splitIndex + 1,
			lastSplit,
			zone)
	}
}

/**
 * An abstraction for factoring out the maintenance of multi-way dispatch logic
 * without polluting code with logic for special uses (dispatching by [TypeTag]
 * or [ObjectLayoutVariant]).
 */
abstract class AbstractMultiWaySplitter(
	val splitPoints: List<Int>)
{
	/**
	 * Determine conditions that would be nice to know statically, and that it
	 * might be profitable to avoid losing due to a control flow merge.  By
	 * default, those conditions include knowing that the input int register has
	 * some proper subset of the range of int ranges or values required along
	 * these edges.  This would allow at least one impossible case to be
	 * omitted, along with the corresponding check in a generated decision tree.
	 * We encode this as a set of conditions where the exact value (or range) is
	 * known to hold, plus a set of conditions where an exact value or range is
	 * known *not* to hold.
	 */
	open fun interestingConditions(
		read: L2ReadIntOperand,
		edges: List<L2PcOperand>
	): List<L2SplitCondition?>
	{
		val register = read.register()
		val semanticValue = read.semanticValue()
		val allConstantInts =
			edges.mapNotNull {
				it.manifest().restrictionFor(semanticValue).constantOrNull
			}
		val warmConstantInts = edges
			.filterNot { it.targetBlock().isCold }
			.mapNotNull {
				it.manifest().restrictionFor(semanticValue).constantOrNull
			}
		val conditions = mutableListOf<L2SplitCondition?>()
		allConstantInts.forEach { constant ->
			val exclude = read.restriction().minusValue(constant)
			conditions.add(
				typeRestrictionCondition(setOf(register), exclude))
		}
		warmConstantInts.forEach { constant ->
			val include = intRestrictionForConstant(constant.extractInt)
			conditions.add(
				typeRestrictionCondition(setOf(register), include))
		}
		return conditions
	}

	/**
	 * Emit an [L2_MULTIWAY_JUMP] instruction using the given [readValue] to
	 * produce an unboxed int value, and edges suitable for this
	 * [AbstractMultiWaySplitter].  Remove unreachable edges, and strengthen the
	 * manifests on the remaining edges to take into account the consequences of
	 * having matched the int value or range along that edge.
	 *
	 * @param readValue
	 *   The [L2ReadIntOperand] supplying the integer on which to dispatch.
	 * @param edges
	 *   The [List] of [L2PcOperand]s separated by the [splitPoints].
	 * @param generator
	 *   The [L2RegeneratorInterface] on which to write the instruction.
	 */
	fun emitInstruction(
		readValue: L2ReadIntOperand,
		edges: List<L2PcOperand>,
		generator: L2GeneratorInterface)
	{
		adjustEdgeManifests(readValue, edges)
		val possibleEdges = edges.filterNot { edge ->
			edge.manifest().hasImpossibleRestriction
		}.toSet()

		if (possibleEdges.isEmpty())
		{
			// Shouldn't happen, but play nice.
			generator.addUnreachableCode()
			return
		}
		if (possibleEdges.size == 1)
		{
			generator.addInstruction(
				L2_JUMP,
				possibleEdges.single())
			return
		}

		// Let impossible edges share a target path with one of their neighbors,
		// since they can't actually be reached by the condition that the
		// impossible edge had.
		val newEdges = mutableListOf<L2PcOperand>()
		val newSplits = mutableListOf<Int>()
		edges.forEachIndexed { i, edge ->
			if (edge in possibleEdges)
			{
				if (newEdges.isEmpty() ||
					edge.targetBlock() != newEdges.last().targetBlock())
				{
					// Preserve the edge.
					newEdges.add(edge)
					if (i < splitPoints.size)
					{
						newSplits.add(splitPoints[i])
					}
					else
					{
						//Dummy value, will be removed.
						newSplits.add(Int.MIN_VALUE)
					}
				}
			}
		}
		newSplits.removeLast()
		assert(newSplits.size == newEdges.size - 1)
		val newSplitter = cloneForReducedEdges(edges, newEdges, newSplits)
		newSplitter.adjustEdgeManifests(readValue, newEdges)
		generator.addInstruction(
			L2_MULTIWAY_JUMP,
			readValue,
			L2ArbitraryConstantOperand(newSplitter),
			L2PcVectorOperand(newEdges))
	}

	/**
	 * Create a new instance like the receiver, but based on the possibly
	 * reduced list of edges and split points.
	 *
	 * @param oldEdges
	 *   The edges that match up with the receiver.
	 * @param newEdges
	 *   The possibly reduced list of edges for the new instance.
	 * @param newSplitPoints
	 *   The possible reduced sorted list of [Int]s at which to test "≥".
	 * @return
	 *   The new [AbstractMultiWaySplitter].
	 */
	abstract fun cloneForReducedEdges(
		oldEdges: List<L2PcOperand>,
		newEdges: List<L2PcOperand>,
		newSplitPoints: List<Int>
	): AbstractMultiWaySplitter

	/**
	 * Provide an opportunity for subclasses to strengthen semantic values that
	 * are correlated with the int value being dispatched on.  The int values
	 * have already been adjusted.
	 */
	open fun adjustEdgeManifests(
		readInt: L2ReadIntOperand,
		edges: List<L2PcOperand>)
	{
		// Subclasses can restrict values in the edges' manifests.
	}

	fun originalValueSource(
		readInt: L2ReadIntOperand
	): L2SemanticBoxedValue?
	{
		val sourceExtraction = readInt.definition().instruction
		return when (val operation = sourceExtraction.operation)
		{
			is L2_EXTRACT_TAG_ORDINAL ->
				operation.sourceOfExtractTag(sourceExtraction).semanticValue()
			else -> null
		}
	}

	/** Provide meaningful names for the target blocks of a branch. */
	open fun leftAndRightTargetNames(
		restriction: TypeRestriction,
		splitValue: Int
	): Pair<String, String> = "< $splitValue" to "≥ $splitValue"
}

/**
 * Used for splitting control flow based on some value's tag.
 *
 * @constructor
 *   Create a [TagSplitter] with the given N-1 split points and N optional
 *   [TypeTag]s used to re-strengthen the outbound edges on regeneration.
 * @param splitPoints
 *   The ascending [Int]s used to look up the tag ordinal.
 * @param edgeTags The edge tags.
 */
class TagSplitter(
	splitPoints: List<Int>,
	private val edgeTags: List<TypeTag?>
) : AbstractMultiWaySplitter(splitPoints)
{
	init
	{
		assert(splitPoints.size == edgeTags.size - 1)
	}

	override fun toString(): String = "tag splits: $splitPoints"

	/**
	 * In addition to the [L2SplitCondition]s related to preserving which
	 * integer values or ranges can or cannot be taken, we also add conditions
	 * that check if the supremum of the tags or collections of tags apply to
	 * the value whose tag is being branched on.  This allows splitting on the
	 * tag to happen prior to the tag extraction itself, in the event that there
	 * are more specific types upstream for the original value.
	 *
	 * As with the int values, we include splits for both the positive and
	 * negative cases of type membership, giving the opportunity to avoid some
	 * paths if some types can be eliminated.
	 */
	override fun interestingConditions(
		read: L2ReadIntOperand,
		edges: List<L2PcOperand>
	): List<L2SplitCondition?>
	{
		val sourceInstructionOfInt = read.definitionSkippingMoves()
		val conditions =
			super.interestingConditions(read, edges).toMutableList()
		if (sourceInstructionOfInt.operation is L2_EXTRACT_TAG_ORDINAL)
		{
			val originalSource =
				L2_EXTRACT_TAG_ORDINAL.sourceOfExtractTag(sourceInstructionOfInt)
			val originalSourceRegister = originalSource.register()
			val intSemanticValue = read.semanticValue()
			edges.forEach { edge ->
				val intRestriction =
					edge.manifest().restrictionFor(intSemanticValue)
				intRestriction.constantOrNull?.let { tagOrdinal ->
					val tag = tagFromOrdinal(tagOrdinal.extractInt)
					val supremum = tag.supremum
					if (!supremum.equals(TOP.o))
					{
						// It would be profitable to know that this tag's
						// supremum is always satisfied somewhere upstream.
						conditions.add(
							typeRestrictionCondition(
								listOf(originalSourceRegister),
								boxedRestrictionForType(supremum)))
						// Allow splitting if there's an upstream point that can
						// guarantee that the supremum is *not* satisfied.
						conditions.add(
							typeRestrictionCondition(
								listOf(originalSourceRegister),
								boxedRestrictionForType(ANY.o)
									.minusType(supremum)))
					}
				}
			}
		}
		return conditions
	}

	/**
	 * Also update the [edgeTags] in the new instance to have the same structure
	 * as [newEdges].
	 */
	override fun cloneForReducedEdges(
		oldEdges: List<L2PcOperand>,
		newEdges: List<L2PcOperand>,
		newSplitPoints: List<Int>
	): TagSplitter
	{
		val newTags = (oldEdges zip edgeTags)
			.filter { (edge, _) -> edge in newEdges}
			.map(Pair<*, TypeTag?>::second)
		return TagSplitter(newSplitPoints, newTags)
	}

	/**
	 * Adjust the original value that the tag was extracted from.
	 */
	override fun adjustEdgeManifests(
		readInt: L2ReadIntOperand,
		edges: List<L2PcOperand>)
	{
		val intValue = readInt.semanticValue()
		val sourceInstruction = readInt.definitionSkippingMoves()
		val sourceValue = when (sourceInstruction.operation)
		{
			is L2_EXTRACT_TAG_ORDINAL ->
				L2_EXTRACT_TAG_ORDINAL.sourceOfExtractTag(sourceInstruction)
					.semanticValue()
			is L2_MOVE_CONSTANT<*, *> -> null
			else -> return
		}
		val bottomOrdinal = TypeTag.BOTTOM_TYPE_TAG.ordinal
		(edges zip edgeTags).forEachIndexed { i, (edge, tag) ->
			val couldBeBottom = edge.manifest().restrictionFor(intValue)
				.intersectsType(bottomMeta)
			var low = if (i > 0) splitPoints[i - 1] else 0
			var high =
				if (i < splitPoints.size) splitPoints[i] - 1 else bottomOrdinal
			tag?.let {
				low = max(low, tag.ordinal)
				high = min(high, tag.highOrdinal)
			}
			edge.manifest().updateRestriction(intValue) {
				if (couldBeBottom && high != bottomOrdinal)
				{
					// A range with a hole in the middle.
					intersectionWithType(inclusive(low, bottomOrdinal))
						.minusType(inclusive(high + 1, bottomOrdinal - 1))
				}
				else
				{
					intersectionWithType(inclusive(low, high))
				}
			}
			if (tag !== null && sourceValue !== null)
			{
				edge.manifest().updateRestriction(sourceValue) {
					intersectionWithType(tag.supremum)
				}
			}
		}
		// The semantic value for the int tag and the semantic value for the
		// value that's the source of that tag are already automatically updated
		// in lockstep by the manifest, so there's nothing more to do here.
	}

	override fun leftAndRightTargetNames(
		restriction: TypeRestriction,
		splitValue: Int
	): Pair<String, String>
	{
		var (leftName, rightName) =
			super.leftAndRightTargetNames(restriction, splitValue)

		// This is a tag dispatch, so be descriptive.
		val range = restriction
			.type
			.typeIntersection(inclusive(0, TypeTag.entries.size - 1))
		val low = range.lowerBound.extractInt
		val high = range.upperBound.extractInt
		leftName += " ${tagFromOrdinal(low).shorterName}"
		if (low < splitValue - 1)
		{
			leftName += "..${tagFromOrdinal(splitValue - 1).shorterName}"
		}
		rightName += " ${tagFromOrdinal(splitValue).shorterName}"
		if (splitValue < high)
		{
			rightName += "..${tagFromOrdinal(high).shorterName}"
		}
		return leftName to rightName
	}

	/**
	 * Calculates the supremum for a range of type tags.
	 *
	 * @param low
	 *   The lower bound of the range (inclusive).
	 * @param high
	 *   The upper bound of the range (inclusive).  Must be ≥ [low].
	 * @return
	 *   The supremum of the type tags in the specified range.  This may become
	 *   conservatively large (include values that should not be present), if
	 *   the type unions of the suprema are also conservative.
	 */
	private fun supremumForTagRange(low: Int, high: Int): A_Type
	{
		assert(high >= low)
		var type: A_Type? = null
		var index = low
		while (index <= high)
		{
			var tag = tagFromOrdinal(index)
			while (tag.parent.notNullAnd { highOrdinal <= high })
			{
				tag = tag.parent!!
			}
			type = type?.typeUnion(tag.supremum) ?: tag.supremum
			index = tag.highOrdinal + 1
		}
		return type!!
	}
}

/**
 * Used for splitting control flow based on some object or object type's
 * [ObjectLayoutVariant].
 *
 * @constructor
 *   Create a [VariantSplitter] with the given N-1 split points and N optional
 *   [ObjectLayoutVariant]s used to re-strengthen the outbound edges on
 *   regeneration.
 * @param isInstance
 *   `true` if the value whose variant is being dispatched is an
 *   [object][ObjectDescriptor], or `false` if it's an
 *   [object&#32;type][ObjectTypeDescriptor].
 * @param splitPoints
 *   The ascending [Int]s used to look up the [ObjectLayoutVariant] number.
 * @param edgeVariants The [ObjectLayoutVariant].
 */
class VariantSplitter(
	private val isInstance: Boolean,
	splitPoints: List<Int>,
	private val edgeVariants: List<ObjectLayoutVariant?>,
) : AbstractMultiWaySplitter(splitPoints)
{
	init
	{
		assert(splitPoints.size == edgeVariants.size - 1)
	}

	override fun toString(): String = "variant splits: $splitPoints"

	/**
	 * In addition to the [L2SplitCondition]s related to preserving which
	 * integer values or ranges can or cannot be taken, we also add conditions
	 * that narrow the original object's restriction, both to check for the
	 * variants being known upstream and for a type constraint based on the
	 * most general type of that variant.
	 *
	 * As with the int values, we include splits for both the positive and
	 * negative cases of restriction membership, giving the opportunity to avoid
	 * some paths if some cases can be eliminated as impossible.
	 */
	override fun interestingConditions(
		read: L2ReadIntOperand,
		edges: List<L2PcOperand>
	): List<L2SplitCondition?>
	{
		val sourceInstructionOfInt = read.definitionSkippingMoves()
		val conditions =
			super.interestingConditions(read, edges).toMutableList()
		val intVariantRegister = read.register()
		edgeVariants.filterNotNull().forEach { variant ->
			// Split if this variant id is a match upstream.
			conditions.add(
				typeRestrictionCondition(
					listOf(intVariantRegister),
					intRestrictionForConstant(variant.variantId)))
			// Split if this variant id is excluded upstream.
			conditions.add(
				typeRestrictionCondition(
					listOf(intVariantRegister),
					intRestrictionForType(i31)
						.minusValue(fromInt(variant.variantId))))
		}
		when (sourceInstructionOfInt.operation)
		{
			is L2_EXTRACT_OBJECT_VARIANT_ID ->
			{
				// It's a variant dispatch on an object instance.
				assert(isInstance)
				val originalSource =
					L2_EXTRACT_OBJECT_VARIANT_ID.sourceOfObjectVariant(
						sourceInstructionOfInt)
				val originalSourceRegister = originalSource.register()
				val baseRestriction =
					boxedRestrictionForType(mostGeneralObjectType)
				edgeVariants.filterNotNull().forEach { variant ->
					// It would be profitable to know that this
					// variant's most general object type is always
					// satisfied somewhere upstream.
					conditions.add(
						typeRestrictionCondition(
							listOf(originalSourceRegister),
							baseRestriction
								.intersectionWithObjectVariant(
									variant)))
					// Allow splitting if there's an upstream point that
					// can guarantee that the most general type for this
					// variant is *not* satisfied.
					conditions.add(
						typeRestrictionCondition(
							listOf(originalSourceRegister),
							baseRestriction
								.minusObjectVariant(variant)))
				}
			}
			is L2_EXTRACT_OBJECT_TYPE_VARIANT_ID ->
			{
				// It's a variant dispatch on an object *type*.
				assert(!isInstance)
				val originalSource =
					L2_EXTRACT_OBJECT_TYPE_VARIANT_ID.sourceOfObjectTypeVariant(
						sourceInstructionOfInt)
				val originalSourceRegister = originalSource.register()
				val baseRestriction =
					boxedRestrictionForType(mostGeneralObjectMeta)
				edgeVariants.filterNotNull().forEach { variant ->
					// It would be profitable to know that this variant's most
					// general object meta is always satisfied somewhere
					// upstream.
					conditions.add(
						typeRestrictionCondition(
							listOf(originalSourceRegister),
							baseRestriction
								.intersectionWithObjectTypeVariant(
									variant)))
					// Allow splitting if there's an upstream point that can
					// guarantee that the most general meta for this variant is
					// *not* satisfied.
					conditions.add(
						typeRestrictionCondition(
							listOf(originalSourceRegister),
							baseRestriction
								.minusObjectTypeVariant(variant)))
				}
			}
		}
		return conditions
	}

	/**
	 * Also update the [edgeVariants] in the new instance to have the same
	 * structure as [newEdges].
	 */
	override fun cloneForReducedEdges(
		oldEdges: List<L2PcOperand>,
		newEdges: List<L2PcOperand>,
		newSplitPoints: List<Int>
	): VariantSplitter
	{
		val newVariants = (oldEdges zip edgeVariants)
			.filter { (edge, _) -> edge in newEdges}
			.map(Pair<*, ObjectLayoutVariant?>::second)
		return VariantSplitter(isInstance, newSplitPoints, newVariants)
	}

	/**
	 * Adjust the original value that the tag was extracted from.
	 */
	override fun adjustEdgeManifests(
		readInt: L2ReadIntOperand,
		edges: List<L2PcOperand>)
	{
		val intValue = readInt.semanticValue()
		(edges zip edgeVariants).forEachIndexed { i, (edge, _) ->
			edge.manifest().updateRestriction(intValue) {
				val low = if (i > 0) splitPoints[i - 1] else 0
				val high =
					if (i < splitPoints.size) splitPoints[i] - 1
					else Int.MAX_VALUE
				intersectionWithType(inclusive(low, high))
			}
		}
		val sourceInstruction = readInt.definitionSkippingMoves()
		when (sourceInstruction.operation)
		{
			is L2_EXTRACT_OBJECT_VARIANT_ID ->
			{
				// It's a variant dispatch on an object instance.
				assert(isInstance)
				val sourceValue =
					L2_EXTRACT_OBJECT_VARIANT_ID.sourceOfObjectVariant(
						sourceInstruction
					).semanticValue()
				(edges zip edgeVariants).forEach { (edge, variant) ->
					if (variant === null) return@forEach
					edge.manifest().updateRestriction(sourceValue)
					{
						intersectionWithObjectVariant(variant)
					}
				}
			}
			is L2_EXTRACT_OBJECT_TYPE_VARIANT_ID ->
			{
				// It's a variant dispatch on an object *type*.
				assert(!isInstance)
				val sourceValue =
					L2_EXTRACT_OBJECT_TYPE_VARIANT_ID.sourceOfObjectTypeVariant(
						sourceInstruction
					).semanticValue()
				(edges zip edgeVariants).forEach { (edge, variant) ->
					if (variant === null) return@forEach
					edge.manifest().updateRestriction(sourceValue)
					{
						intersectionWithObjectTypeVariant(variant)
					}
				}
			}
		}
		// The semantic value for the int tag and the semantic value for the
		// value that's the source of that tag are already automatically updated
		// in lockstep by the manifest, so there's nothing more to do here.
	}
}

/**
 * Used for splitting control flow based on the value's hash, right-shifted some
 * amount, and masked to include a limited number of low bits.  This is an
 * efficient scheme when there are many specific enumeration values in a
 * [LookupTree].
 *
 * @constructor
 *   Create a [ShiftedHashSplitter] with the given N-1 split points and N
 *   outbound edges, some of which may lead to a fallback case.
 * @param rightShift
 *   How much to right-shift the hash values, chosen to maximize the spread of
 *   the hashes of the values being tested for.
 * @param lowMask
 *   A mask for the right-shifted values.
 * @param splitPoints
 *   The list of N-1 positions at which "≥" tests can be performed to figure out
 *   which of the N edges to take.
 * @param activeInts
 *   A list of optional [Int]s that correspond to the instruction's edges,
 *   indicating the guaranteed [Int] value if that edge is taken.  If an entry
 *   is `null`, the corresponding edge cannot deduce anything about the value.
 *   This is useful for collapsing adjacent "fallback" paths together.
 */
class ShiftedHashSplitter constructor(
	private val rightShift: Int,
	private val lowMask: Int,
	splitPoints: List<Int> = (1..lowMask).toList(),
	private val activeInts: List<Int?>
) : AbstractMultiWaySplitter(splitPoints)
{
	override fun toString(): String = "hashed split (>> $rightShift & $lowMask)"

	/**
	 * In addition to the [L2SplitCondition]s related to preserving which
	 * integer values or ranges can or cannot be taken, we also add conditions
	 * that narrow the original object's restriction, both to check for the
	 * variants being known upstream and for a type constraint based on the
	 * most general type of that variant.
	 *
	 * As with the int values, we include splits for both the positive and
	 * negative cases of restriction membership, giving the opportunity to avoid
	 * some paths if some cases can be eliminated as impossible.
	 */
	override fun interestingConditions(
		read: L2ReadIntOperand,
		edges: List<L2PcOperand>
	): List<L2SplitCondition?>
	{
		val conditions =
			super.interestingConditions(read, edges).toMutableList()
		originalValueRead(read)?.let { sourceRead ->
			val sourceType = sourceRead.restriction().type
			if (sourceType.isEnumeration && !sourceType.isInstanceMeta)
			{
				sourceType.instances.forEach { instance ->
					// Split if there's a point upstream that knows the
					// exact constant that we're looking up.
					conditions.add(
						typeRestrictionCondition(
							listOf(sourceRead.register()),
							boxedRestrictionForConstant(instance)))
					// Split if there's a point upstream that knows that
					// one or more of the constants will not be possible
					// here.
					conditions.add(
						typeRestrictionCondition(
							listOf(sourceRead.register()),
							boxedRestrictionForType(ANY.o)
								.minusValue(instance)))
				}
			}
		}
		return conditions
	}

	/**
	 * Answer the [L2ReadBoxedOperand] that was fed to an [L2_HASH] operation,
	 * then shifted and masked to provide the given [read], which is dispatched
	 * by this multi-way jump operation.  Reach across moves.  If such a source
	 * cannot be found, answer `null`.
	 *
	 * @param read
	 *   The [L2ReadIntOperand] providing the masked, shifted, hash of some
	 *   value.
	 * @return
	 *   The [L2ReadBoxedOperand] providing the value that was hashed, shifted,
	 *   and masked.
	 */
	private fun originalValueRead(
		read: L2ReadIntOperand
	): L2ReadBoxedOperand?
	{
		val sourceInstructionOfMasked = read.definitionSkippingMoves()
		if (sourceInstructionOfMasked.operation != L2_BIT_LOGIC_OP.bitwiseAnd)
			return null
		val sourceInstructionOfShifted = sourceInstructionOfMasked
			.readOperands.first()  // value & mask
			.definitionSkippingMoves()
		val sourceInstructionOfHash: L2Instruction = when
		{
			sourceInstructionOfShifted.operation
					== L2_BIT_LOGIC_OP.bitwiseUnsignedShiftRight ->
			{
				sourceInstructionOfShifted
					.readOperands.first() // value >>> shift
					.definitionSkippingMoves()
			}
			// No shift was needed in this case.
			else -> sourceInstructionOfShifted
		}
		if (sourceInstructionOfHash.operation != L2_HASH)
			return null
		return sourceInstructionOfHash.readOperands.single().cast()
	}

	override fun cloneForReducedEdges(
		oldEdges: List<L2PcOperand>,
		newEdges: List<L2PcOperand>,
		newSplitPoints: List<Int>
	): ShiftedHashSplitter
	{
		assert(newEdges.size == newSplitPoints.size + 1)
		val newActiveInts = (oldEdges zip activeInts)
			.filter { (edge, _) -> edge in newEdges }
			.map(Pair<*, Int?>::second)
		return ShiftedHashSplitter(
			rightShift, lowMask, newSplitPoints, newActiveInts)
	}

	/**
	 * Adjust the original value that the hash was extracted from.  The actual
	 * masked, shifted hash of the value has already been strengthened in each
	 * edge.
	 */
	override fun adjustEdgeManifests(
		readInt: L2ReadIntOperand,
		edges: List<L2PcOperand>)
	{
		originalValueRead(readInt)?.let { sourceRead ->
			val enumerationType = sourceRead.restriction().type
			if (!enumerationType.isEnumeration
				|| enumerationType.isInstanceMeta)
			{
				return
			}
			val allInstances = enumerationType.instances
			val semanticValue = sourceRead.semanticValue()
			val possibilitiesByInt = allInstances.groupBy { instance ->
				(instance.hash() ushr rightShift) and lowMask
			}
			(edges zip activeInts).forEach { (edge, activeInt) ->
				possibilitiesByInt[activeInt]?.let { possible ->
					edge.manifest().updateRestriction(semanticValue) {
						intersectionWithType(
							enumerationWith(setFromCollection(possible)))
					}
				}
			}
		}
	}
}
