/*
 * TypeTagDecisionStep.kt
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

package avail.dispatch

import avail.descriptor.methods.A_Definition
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instanceTag
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.TypeTag
import avail.descriptor.types.TypeTag.Companion.tagFromOrdinal
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.bottomRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operation.L2_EXTRACT_TAG_ORDINAL
import avail.interpreter.levelTwo.operation.TagSplitter
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticExtractedTag
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.utility.Strings.increaseIndentation
import avail.utility.Strings.newlineTab
import avail.utility.isNullOr
import avail.utility.partitionRunsBy
import avail.utility.removeLast
import java.lang.String.format
import kotlin.math.max
import kotlin.math.min

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
		extraValues: List<A_BasicObject>,
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
		extraValues: List<A_Type>,
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
		extraValues: List<A_Type>,
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
		extraValues: List<A_BasicObject>,
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
					"(u=%d, p=%d) #%d typeTag: known=%s",
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

	override fun simplyAddChildrenTo(
		list: MutableList<LookupTree<Element, Result>>)
	{
		list.addAll(tagToSubtree.values)
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
			boxedRestrictionForType(supremum)
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
		// Compute the union of any restrictions that occurred in this run.
		// None of them should have been bottom.  If they were all null, use
		// null as the restriction, which simply indicates the fallback lookup
		// should be performed for this case.
		val restrictionUnion = spans
			.mapNotNull(Span::restriction)
			.fold(bottomRestriction, TypeRestriction::union)
		val subtrees = spans.mapNotNull { it.subtree }.toSet()
		assert(subtrees.size <= 1)
		return Span(
			low,
			high,
			subtrees.firstOrNull(),
			tag,
			if (restrictionUnion.isImpossible) null
			else restrictionUnion)
	}

	override fun generateEdgesFor(
		semanticArguments: List<L2SemanticBoxedValue>,
		extraSemanticArguments: List<L2SemanticBoxedValue>,
		callSiteHelper: CallSiteHelper
	): List<
		Triple<
			L2BasicBlock,
			LookupTree<A_Definition, A_Tuple>,
			List<L2SemanticBoxedValue>>>
	{
		// For simplicity, let super-lookups via type tags always fall back.
		// They're *very* difficult to reason about.
		if (callSiteHelper.isSuper)
		{
			callSiteHelper.generator.jumpTo(
				callSiteHelper.onFallBackToSlowLookup)
			return emptyList()
		}

		// Convert the tags' ordinal ranges into a flat list of runs.
		// Use a stack to keep track of which ordinal ranges are still
		// outstanding, to know when to resume or finish them.
		val semanticSource =
			sourceSemanticValue(semanticArguments, extraSemanticArguments)
		val generator = callSiteHelper.generator
		val currentRestriction =
			generator.currentManifest.restrictionFor(semanticSource)
		val couldBeBottom = currentRestriction.intersectsType(bottomMeta)
		val restrictionTag = currentRestriction.type.instanceTag
		// Keep the entries that are both valid solutions (1 method def) and
		// reachable (tags could actually occur).
		val reducedMap = tagToSubtree
			.filterKeys {
				restrictionTag.isSubtagOf(it) || it.isSubtagOf(restrictionTag)
			}
			.mapValuesTo(mutableMapOf()) { (tag, subtreeWeak) ->
				val subtree = subtreeWeak.castForGenerator()
				when
				{
					!containsAnyValidLookup(subtree) -> null
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
			reducedMap.remove(TypeTag.BOTTOM_TYPE_TAG)
		}
		val runs = mutableListOf(
			Span(
				0,
				TypeTag.count - 1,
				null,
				null,
				currentRestriction))
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
			// and a right part, omitting any empty ranges, or ranges that only
			// contain impossible (e.g., abstract) tags.
			val (low, high, existing, oldTag, oldRestriction) = runs[index]
			runs.removeAt(index)
			val newRestriction = boxedRestrictionForType(tag.supremum)
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
						if (tag.isAbstract) null else subtree,
						if (tag.isAbstract) null else tag,
						newRestriction),
					Span(
						tag.ordinal + 1,
						tag.highOrdinal,
						subtree,
						tag,
						newRestriction),
					Span(
						tag.highOrdinal + 1,
						high,
						existing,
						oldTag,
						oldRestriction)
				).filter { (low, high) -> low <= high })
		}
		var ordinalRestriction = run {
			val low = restrictionTag.ordinal +
				(if (restrictionTag.isAbstract) 1 else 0)
			val high = restrictionTag.highOrdinal
			val bottomOrdinal = TypeTag.BOTTOM_TYPE_TAG.ordinal
			if (couldBeBottom && high != bottomOrdinal)
			{
				intRestrictionForType(inclusive(low, bottomOrdinal))
					.minusType(inclusive(high + 1, bottomOrdinal - 1))
			}
			else
			{
				intRestrictionForType(inclusive(low, high))
			}
		}
		// Exclude all abstract type tags from the ordinalRestriction, since
		// there are no values that have exactly those tags.
		val impossibleOrdinals =
			(restrictionTag.ordinal..<restrictionTag.highOrdinal)
				.filter { ord ->
					val tag = tagFromOrdinal(ord)
					tag.isAbstract
						|| !currentRestriction.intersectsType(tag.supremum)
						|| run {
							val newRestriction = reducedMap.keys
								.filter { subtag ->
									subtag != tag && subtag.isSubtagOf(tag) }
								.map(TypeTag::supremum)
								.fold(
									currentRestriction
										.intersectionWithType(tag.supremum),
									TypeRestriction::minusType)
							newRestriction.isImpossible
						}
				}
		if (impossibleOrdinals.isNotEmpty())
		{
			ordinalRestriction = ordinalRestriction.minusValues(
				impossibleOrdinals.map(::fromInt))
		}
		val ordinalLow = ordinalRestriction.type.lowerBound.extractInt
		val ordinalHigh = ordinalRestriction.type.upperBound.extractInt
		val reachableSpans = runs.filter { (low, high, _, _, restriction) ->
			// Keep the span if it has a possible tag and a possible type.
			ordinalRestriction.intersectsType(inclusive(low, high))
				&& restriction.isNullOr {
					intersection(currentRestriction) != bottomRestriction
				}
		}
		if (reachableSpans.all { it.subtree == null })
		{
			// Just jump to the slow lookup, and don't continue down any more
			// lookup subtrees.
			generator.jumpTo(callSiteHelper.onFallBackToSlowLookup)
			return emptyList()
		}
		// Expand the ranges through the don't-cares that were removed, so
		// that the entire tag range is covered.  Initially pad to the left,
		// then do a separate step at the end to pad the last one rightward.
		var nextOrdinal = 0
		val padded = reachableSpans.map { (_, high, subtree, tag, rest) ->
			Span(nextOrdinal, high, subtree, tag, rest).also {
				nextOrdinal = high + 1
			}
		}.toMutableList()
		// Extend the last one.
		padded.removeLast().let { old ->
			padded.add(
				old.copy(
					high = TypeTag.count - 1,
					restriction = old.restriction))
		}
		// Merge consecutive spans that have the same outcome.
		val reducedSpans = padded
			.partitionRunsBy(Span::subtree)
			.map(::mergeSpans)

		// We now have contiguous runs that cover the tag space, with no
		// unnecessary checks.
		if (reducedSpans.size == 1)
		{
			// Only one path is reachable.
			val span = reducedSpans[0]
			span.restriction?.let { r ->
				generator.currentManifest.updateRestriction(semanticSource) {
					intersection(r)
				}
			}
			val target = L2BasicBlock("Sole target")
			generator.jumpTo(target)
			return listOf(
				Triple(target, span.subtree!!, extraSemanticArguments))
		}
		// Generate a multi-way branch.
		val splits = reducedSpans.drop(1).map(Span::low)
		val semanticTag =
			L2SemanticUnboxedInt(L2SemanticExtractedTag(semanticSource))
		return generator.run {
			if (!currentManifest.hasSemanticValue(semanticTag))
			{
				// Assume the base type is sufficient to limit the possible tag
				// ordinals.
				addInstruction(
					L2_EXTRACT_TAG_ORDINAL,
					readBoxed(semanticSource),
					intWrite(setOf(semanticTag), ordinalRestriction))
			}
			val edges = reducedSpans.mapIndexed {
					index, (low, high, subtree, _, restriction) ->
				val nameLow =
					if (index == 0) ordinalLow
					else splits[index - 1]
				val nameHigh =
					if (index == splits.size) ordinalHigh
					else splits[index] - 1
				val lowName = tagFromOrdinal(nameLow).shorterName
				val highName = tagFromOrdinal(nameHigh).shorterName
				val spanName =
					if (nameLow == nameHigh) lowName
					else "$lowName..$highName"
				val edgeManifest = L2ValueManifest(currentManifest)
				edgeManifest.updateRestriction(semanticTag) {
					intersectionWithType(inclusive(low, high))
				}
				when (subtree)
				{
					null ->
						L2PcOperand(
							callSiteHelper.onFallBackToSlowLookup,
							false,
							edgeManifest,
							"Fallback: $nameLow..$nameHigh, $spanName")
					else ->
					{
						val target = L2BasicBlock(
							"Tag in [${max(low, ordinalLow)}.." +
								"${min(high, ordinalHigh)}]")
						restriction?.let { r ->
							edgeManifest.updateRestriction(semanticSource) {
								intersection(r)
							}
						}
						L2PcOperand(
							target,
							false,
							edgeManifest,
							"$nameLow..$nameHigh, $spanName")
					}
				}
			}
			val splitter = TagSplitter(splits, reducedSpans.map(Span::tag))
//			splitter.cloneForReducedEdges(edges, edges, splitter.splitPoints)
			splitter.emitInstruction(
				currentManifest.readInt(semanticTag), edges, this@run)
			reducedSpans.mapIndexedNotNull { index, (_, _, subtree, _, _) ->
				subtree?.let {
					// No need to further restrict the type.
					Triple(
						edges[index].targetBlock(),
						subtree,
						extraSemanticArguments)
				}
			}
		}
	}
}
