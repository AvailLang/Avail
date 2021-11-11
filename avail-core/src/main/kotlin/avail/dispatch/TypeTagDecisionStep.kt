/*
 * TypeTagDecisionStep.kt
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

package avail.dispatch

import avail.descriptor.methods.A_Definition
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instanceTag
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.TypeTag
import avail.descriptor.types.TypeTag.Companion.tagFromOrdinal
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_EXTRACT_TAG_ORDINAL
import avail.interpreter.levelTwo.operation.L2_MULTIWAY_JUMP
import avail.interpreter.levelTwo.operation.L2_STRENGTHEN_TYPE
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.values.L2SemanticExtractedTag
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.Strings.increaseIndentation
import avail.utility.Strings.newlineTab
import avail.utility.cast
import avail.utility.notNullAnd
import avail.utility.partitionRunsBy
import avail.utility.removeLast
import java.lang.String.format

/**
 * This is a [DecisionStep] which dispatches to subtrees by looking up the
 * [TypeTag] for a particular argument position.  Narrowing the effective
 * [TypeRestriction] on the argument in this way can quickly reduce the
 * number of applicable elements in the subtrees, which may also promote
 * other techniques, such as dispatching on an object or object type's
 * [ObjectLayoutVariant], or extracting a covariant or contravariant
 * parameter into a separate argument.
 *
 * @constructor
 * Construct the new instance.
 *
 * @property argumentPositionToTest
 *   The 1-based index of the argument for which to test by [TypeTag].
 * @property tagToSubtree
 *   A [Map] from [TypeTag] to the child [LookupTree] that should be visited
 *   if the given tag occurs during lookup.  If the provided tag is not
 *   present, its ancestors will be looked up until successful.
 */
class TypeTagDecisionStep<
	Element : A_BasicObject,
	Result : A_BasicObject>
constructor(
	argumentPositionToTest: Int,
	private val tagToSubtree: Map<TypeTag, LookupTree<Element, Result>>
) : DecisionStep<Element, Result>(argumentPositionToTest)
{
	//////////////////////////////
	//       Lookup steps.      //
	//////////////////////////////

	override fun <AdaptorMemento> lookupStepByValues(
		argValues: List<A_BasicObject>,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argument = extractArgument(argValues, extraValues)
		var tag = argument.typeTag
		while (true)
		{
			tagToSubtree[tag]?.let { return it }
			tag = tag.parent ?: return adaptor.emptyLeaf
		}
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: List<A_Type>,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argumentType = extractArgumentType(argTypes, extraValues)
		var tag = argumentType.instanceTag
		while (true)
		{
			tagToSubtree[tag]?.let { return it }
			tag = tag.parent ?: return adaptor.emptyLeaf
		}
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: A_Tuple,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argumentType = extractArgumentType(argTypes, extraValues)
		var tag = argumentType.instanceTag
		while (true)
		{
			tagToSubtree[tag]?.let { return it }
			tag = tag.parent ?: return adaptor.emptyLeaf
		}
	}

	override fun <AdaptorMemento> lookupStepByValue(
		probeValue: A_BasicObject,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		var tag = extractValue(probeValue, extraValues).typeTag
		while (true)
		{
			tagToSubtree[tag]?.let { return it }
			tag = tag.parent ?: return adaptor.emptyLeaf
		}
	}

	override fun describe(
		node: InternalLookupTree<Element, Result>,
		indent: Int,
		builder: StringBuilder
	): Unit = with(builder)
	{
		append(
			increaseIndentation(
				format(
					"(u=%d, p=%d) #%d typeTag : known=%s",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					node.knownArgumentRestrictions),
				indent + 1))
		for ((k, v) in tagToSubtree.entries.sortedBy { it.key.ordinal })
		{
			newlineTab(indent + 1)
			append("$k(${k.ordinal}): ")
			append(v.toString(indent + 1))
		}
	}

	override fun addChildrenTo(
		list: MutableList<
			Pair<LookupTree<Element, Result>, List<L2SemanticValue>>>,
		semanticValues: List<L2SemanticValue>,
		extraSemanticValues: List<L2SemanticValue>)
	{
		tagToSubtree.values.forEach { subtree ->
			list.add(subtree to extraSemanticValues)
		}
	}

	/**
	 * A private class for keeping track of a run of tags and its associated
	 * information.
	 *
	 * @property low
	 *   The lowest [TypeTag] ordinal included in this [Span].
	 * @property high
	 *   The highest [TypeTag] ordinal included in this [Span].
	 * @property subtree
	 *   The [LookupTree] reachable through this [Span].
	 * @property tag
	 *   The [TypeTag] that this [Span] guarantees the value will be in. If
	 *   this is null, the [Span] should be treated as a don't-care.
	 * @property restriction
	 *   The [TypeRestriction] for the value when this [Span] is in effect.
	 */
	private data class Span(
		val low: Int,
		val high: Int,
		val subtree: LookupTree<A_Definition, A_Tuple>?,
		val tag: TypeTag?,
		var restriction: TypeRestriction? = tag?.run {
			restrictionForType(supremum, BOXED_FLAG)
		})

	/**
	 * Given a [List] of [Span]s that are contiguous and *don't disagree*
	 * about their subtrees (although some may be `null`), compute a
	 * replacement [Span] that includes the entire range and has the common
	 * ancestor of any tags that were present.
	 */
	private fun mergeSpans(spans: List<Span>): Span
	{
		val low = spans[0].low
		var high = low - 1
		var tag: TypeTag? = null
		spans.forEach { span ->
			assert(span.low == high + 1)
			high = span.high
			span.tag?.let { tag = tag?.commonAncestorWith(it) ?: it }
		}
		val subtrees = spans.mapNotNull { it.subtree }.toSet()
		assert(subtrees.size <= 1)
		return Span(low, high, subtrees.firstOrNull(), tag)
	}

	override fun generateEdgesFor(
		semanticArguments: List<L2SemanticValue>,
		extraSemanticArguments: List<L2SemanticValue>,
		callSiteHelper: CallSiteHelper
	): List<
		Triple<
			L2BasicBlock,
			LookupTree<A_Definition, A_Tuple>,
			List<L2SemanticValue>>>
	{
		// For simplicity, let super-lookups via type tags always fall back.
		//  They're *very* difficult to reason about.
		if (!callSiteHelper.superUnionType
				.typeAtIndex(argumentPositionToTest)
				.isBottom)
		{
			callSiteHelper.generator().jumpTo(
				callSiteHelper.onFallBackToSlowLookup)
			return emptyList()
		}

		// Convert the tags' ordinal ranges into a flat list of runs.
		// Use a stack to keep track of which ordinal ranges are still
		// outstanding, to know when to resume or finish them.
		val semanticSource =
			sourceSemanticValue(semanticArguments, extraSemanticArguments)
		val generator = callSiteHelper.generator()
		val currentRestriction =
			generator.currentManifest.restrictionFor(semanticSource)
		val couldBeBottom = currentRestriction.intersectsType(bottomMeta)
		val restrictionTag = currentRestriction.type.instanceTag
		val strongTagToSubtree:
				Map<TypeTag, LookupTree<A_Definition, A_Tuple>> =
			tagToSubtree.cast()
		// Keep the entries that are both valid solutions (1 method def) and
		// reachable (tags could actually occur).
		val reducedMap = strongTagToSubtree
			.filterKeys {
				restrictionTag.isSubtagOf(it)
					|| it.isSubtagOf(restrictionTag)
			}
			.mapValuesTo(mutableMapOf()) { (tag, subtree) ->
				when
				{
					!containsAnyValidLookup(subtree, semanticArguments) -> null
					restrictionTag.isSubtagOf(tag) -> subtree
					tag.isSubtagOf(restrictionTag) &&
							currentRestriction.intersectsType(tag.supremum)
						-> subtree
					else -> null
				}
			}
		if (!couldBeBottom)
		{
			// This condition shouldn't be possible at runtime, so force an
			// actual bottom type coming in to be looked up the slow way.
			reducedMap[TypeTag.BOTTOM_TYPE_TAG] = null
		}
		val runs = mutableListOf(Span(0, TypeTag.count - 1, null, null))
		reducedMap.entries.sortedBy { it.key }.forEach { (tag, subtree) ->
			val index = runs.binarySearch { (low, high, _, _) ->
				when
				{
					tag.highOrdinal < low -> 1
					tag.ordinal > high -> -1
					else -> 0
				}
			}
			assert(0 <= index && index < runs.size)
			// Subtract the new tag's supremum from all existing spans, then
			// insert the new tag's spans at the appropriate place.
			runs.forEach { span ->
				span.restriction?.let {
					span.restriction = it.minusType(tag.supremum)
				}
			}
			// Replace the existing element with a left part, the new value,
			// and a right part, omitting any empty ranges.
			val (low, high, existing, oldTag, oldRestriction) = runs[index]
			runs.removeAt(index)
			runs.addAll(
				index,
				listOf(
					Span(
						low,
						tag.ordinal - 1,
						existing,
						oldTag,
						oldRestriction),
					Span(
						tag.ordinal,
						tag.ordinal,
						(if (tag.isAbstract) null else subtree),
						(if (tag.isAbstract) null else tag)),
					Span(
						tag.ordinal + 1,
						tag.highOrdinal,
						subtree,
						tag),
					Span(
						tag.highOrdinal + 1,
						high,
						existing,
						oldTag,
						oldRestriction)
				).filter { (low, high) -> low <= high })
		}
		val ordinalRestriction = restrictionForType(
			inclusive(
				fromInt(
					restrictionTag.ordinal +
						(if (restrictionTag.isAbstract) 1 else 0)),
				fromInt(
					if (couldBeBottom) TypeTag.BOTTOM_TYPE_TAG.ordinal
					else restrictionTag.highOrdinal)),
			UNBOXED_INT_FLAG)
		// We have to smear it both directions, in case there were multiple
		// entries that homogenized with entries that later homogenized with
		// something different, breaking the equivalence.  Forward then
		// backward over the indices should be sufficient to handle all such
		// cases.
		val ordinalLow = ordinalRestriction.type.lowerBound.extractInt
		val ordinalHigh = ordinalRestriction.type.upperBound.extractInt
		val reachableSpans = runs.filter { (low, high) ->
			high >= ordinalLow && low <= ordinalHigh
		}
		if (reachableSpans.all { it.subtree == null })
		{
			// Just jump to the slow lookup, and don't continue down any
			// more lookup subtrees.
			generator.jumpTo(callSiteHelper.onFallBackToSlowLookup)
			return emptyList()
		}
		// Expand the ranges through the don't-cares that were removed, so
		// that the entire tag range is covered.  Initially pad to the left,
		// then do a separate step at the end to pad the last one rightward.
		var nextOrdinal = 0
		val padded = reachableSpans.map { (_, high, subtree, tag) ->
			Span(nextOrdinal, high, subtree, tag).also {
				nextOrdinal = high + 1
			}
		}.toMutableList()
		// Extend the last one.
		padded.add(padded.removeLast().copy(high = TypeTag.count - 1))
		// Merge consecutive spans that have the same outcome.
		val reducedSpans = padded
			.partitionRunsBy(Span::subtree)
			.map(::mergeSpans)

		// We now have contiguous runs that cover the tag space, with no
		// spurious checks.
		if (reducedSpans.size == 1)
		{
			// Only one path is reachable.
			reducedSpans[0].run {
				// Check if the value is already as strong as the
				// restriction in the span.
				if (currentRestriction.isStrongerThan(restriction!!))
				{
					// No need to strengthen the type.
					val target = L2BasicBlock("Sole target")
					generator.jumpTo(target)
					return listOf(
						Triple(target, subtree!!, extraSemanticArguments))
				}
				// We need to strengthen the type to correspond with the
				// fact that it now has this tag.
				val strengthenerBlock = L2BasicBlock(
					"Strengthen for " +
						"[$low(${tagFromOrdinal(low)}).." +
						"$high(${tagFromOrdinal(high)})]")
				val soleTarget = L2BasicBlock(
					"Guaranteed lookup for " +
						"[$low(${tagFromOrdinal(low)}).." +
						"$high(${tagFromOrdinal(high)})]")
				generator.jumpTo(strengthenerBlock)
				generator.startBlock(strengthenerBlock)
				generator.addInstruction(
					L2_STRENGTHEN_TYPE,
					generator.readBoxed(semanticSource),
					generator.boxedWrite(
						semanticSource,
						currentRestriction.intersection(restriction!!)))
				generator.jumpTo(soleTarget)
				return listOf(
					Triple(soleTarget, subtree!!, extraSemanticArguments))
			}
		}
		// Generate a multi-way branch.
		val splitsTuple =
			tupleFromIntegerList(reducedSpans.drop(1).map(Span::low))
		val edges = reducedSpans.map { (low, high, subtree) ->
			when (subtree)
			{
				null -> callSiteHelper.onFallBackToSlowLookup
				else -> L2BasicBlock("Tag in [$low..$high]")
			}
		}
		val semanticTag = L2SemanticUnboxedInt(
			L2SemanticExtractedTag(semanticSource))
		return generator.run {
			// Assume the base type is sufficient to limit the possible tag
			// ordinals.
			addInstruction(
				L2_EXTRACT_TAG_ORDINAL,
				readBoxed(semanticSource),
				intWrite(setOf(semanticTag), ordinalRestriction))
			addInstruction(
				L2_MULTIWAY_JUMP,
				currentManifest.readInt(semanticTag),
				L2ConstantOperand(splitsTuple),
				L2PcVectorOperand(edges.map { L2PcOperand(it, false) }))
			// Generate type strengthening clauses along every non-fallback
			// path.
			reducedSpans.mapIndexedNotNull {
					index, (low, high, subtree, tag, restriction) ->
				val supremum = tag?.supremum
				when
				{
					subtree == null -> null
					supremum.notNullAnd {
						currentRestriction.type.isSubtypeOf(this@notNullAnd)
					} ->
					{
						// No need to further restrict the type.
						Triple(edges[index], subtree, extraSemanticArguments)
					}
					else ->
					{
						// Restrict the type to the supremum that the actual
						// encountered type tag guarantees.
						startBlock(edges[index])
						addInstruction(
							L2_STRENGTHEN_TYPE,
							readBoxed(semanticSource),
							boxedWrite(
								semanticSource,
								currentRestriction.intersection(
									restriction!!)))
						val newBlock =
							L2BasicBlock("Strengthened [$low..$high]")
						jumpTo(newBlock)
						Triple(newBlock, subtree, extraSemanticArguments)
					}
				}
			}
		}
	}
}
