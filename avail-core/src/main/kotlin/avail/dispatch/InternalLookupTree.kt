/*
 * InternalLookupTree.kt
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

import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.bitSet
import avail.descriptor.numbers.A_Number.Companion.bitTest
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.objectVariant
import avail.descriptor.representation.AvailObjectRepresentation
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceTag
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.objectTypeVariant
import avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.TypeTag
import avail.descriptor.types.TypeTag.BOTTOM_TYPE_TAG
import avail.descriptor.types.TypeTag.OBJECT_TAG
import avail.descriptor.types.TypeTag.OBJECT_TYPE_TAG
import avail.descriptor.types.TypeTag.TOP_TYPE_TAG
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.Strings.increaseIndentation
import avail.utility.notNullAnd
import java.lang.String.format
import kotlin.math.max
import kotlin.math.min

/**
 * A [LookupTree] representing an incomplete search.  To further the search, its
 * [decisionStep] performs some sort of dynamic test, producing the next subtree
 * to visit.
 *
 * @param Element
 *   The kind of elements in the lookup tree, such as method definitions.
 * @param Result
 *   What we expect to produce from a lookup activity, such as the tuple of
 *   most-specific matching method definitions for some arguments.
 * @property positiveElements
 *   The elements which definitely apply to the supplied arguments at this point
 *   in the decision tree.
 * @property undecidedElements
 *   The elements for which a decision about whether they apply to the supplied
 *   arguments has not yet been made at this point in the decision tree.
 * @property knownArgumentRestrictions
 *   The list of argument [TypeRestriction]s known to hold at this position in
 *   the decision tree.  Each element corresponds with an argument position for
 *   the method.
 * @property alreadyTypeTestedArguments
 *   An Avail [integer][A_Number] coding whether the arguments (and extras that
 *   may have been generated during traversal of ancestors) have had their
 *   [TypeTag] extracted and dispatched on by an ancestor.  Argument #n is
 *   indicated by a set bit in the n-1st bit position, the one whose value in
 *   the integer is 2^(n-1).
 * @property alreadyVariantTestedArguments
 *   An Avail [integer][A_Number] coding whether the arguments (and extras
 *   that may have been generated during traversal of ancestors) have been
 *   proven to be an [object][ObjectDescriptor], and was already dispatched
 *   via an [ObjectLayoutVariantDecisionStep] in an ancestor.  Argument #n
 *   is indicated by a set bit in the n-1st bit position, the one whose
 *   value in the integer is 2^(n-1).
 * @property alreadyPhraseTypeExtractArguments
 *   An Avail [integer][A_Number] coding which arguments (and extras that may
 *   have been generated during traversal of ancestors) were known to be phrase
 *   types, and have had their yield type extracted already into another field
 *   via an [ExtractPhraseTypeDecisionStep] in an ancestor.  Argument #n is
 *   indicated by a set bit in the n-1st bit position, the one whose value in
 *   the integer is 2^(n-1).
 *
 * @constructor
 *
 * Construct a new [InternalLookupTree].  It is constructed lazily at first.  An
 * attempted lookup that reaches this node will cause it to be expanded locally.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class InternalLookupTree<
	Element : A_BasicObject,
	Result : A_BasicObject>
internal constructor(
	val positiveElements: List<Element>,
	val undecidedElements: List<Element>,
	val knownArgumentRestrictions: List<TypeRestriction>,
	val alreadyTypeTestedArguments: A_Number,
	private val alreadyVariantTestedArguments: A_Number,
	val alreadyPhraseTypeExtractArguments: A_Number)
: LookupTree<Element, Result>()
{
	override val solutionOrNull: Result? get() = null

	init
	{
		assert(alreadyTypeTestedArguments.descriptor().isShared)
	}

	/**
	 * The current [DecisionStep], initialized via [expandIfNecessary] when
	 * needed.
	 */
	@Volatile
	private var decisionStep: DecisionStep<Element, Result>? = null

	/** `true` if this node has been expanded, otherwise `false`. */
	private val isExpanded: Boolean get() = decisionStep !== null

	/** Access the [decisionStep] if available, otherwise answer `null`. */
	val decisionStepOrNull get() = decisionStep

	/**
	 * If it has not already been computed, compute and cache the [decisionStep]
	 * to use for making progress at this [InternalLookupTree].
	 */
	override fun <AdaptorMemento> expandIfNecessary(
		signatureExtrasExtractor: (Element) -> Pair<A_Type?, List<A_Type>>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento
	): DecisionStep<Element, Result> =
		decisionStep ?: synchronized(this) {
			// We have to double-check if another thread has run this function
			// since our first check.  We're in a synchronized mutual exclusion
			// now, so this is a stable check. Also, decisionStep is volatile,
			// and its internal fields should be final (val), ensuring Java's
			// infamous double-check problem won't bite us.
			decisionStep ?: run {
				val step = createDecisionStep(
					signatureExtrasExtractor, adaptor, memento)
				decisionStep = step
				step
			}
		}

	/**
	 * We're doing a method lookup or some similar lookup operation, and
	 * [decisionStep] was `null`, indicating a lazy subtree.  Expand it by
	 * choosing and recording a criterion to test at this node, then populating
	 * the branches of the tree with nodes that may themselves need to be
	 * expanded in the future.
	 *
	 * The criterion to choose should be one which serves to eliminate at least
	 * one of the [undecidedElements], regardless of whether the test happens
	 * to be affirmative or negative.  Eliminating more than one is better,
	 * however.  Ideally, we should choose a test which serves to eliminate as
	 * much indecision as possible in the worst case (i.e., along the path that
	 * is the least effective of the two). We do this, but we also break ties by
	 * eliminating as much indecision as possible in the *best* case.
	 *
	 * We eliminate some redundancy of a naïve decision tree by testing a single
	 * argument at a time, keeping track of the types we have tested that
	 * argument against.
	 *
	 * Since the negative case is already efficient at eliminating uncertainty,
	 * we only need to track positive information about the argument types.
	 * Thus, for each argument we maintain precise information about what type
	 * each argument must be at this point in the tree.  A single type for each
	 * argument suffices, since Avail's type lattice is precise with respect to
	 * type intersection, which is exactly what we use during decision tree
	 * construction.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 */
	private fun <AdaptorMemento> createDecisionStep(
		signatureExtrasExtractor: (Element) -> Pair<A_Type?, List<A_Type>>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento
	): DecisionStep<Element, Result>
	{
		assert(!isExpanded)
		val numArgs = knownArgumentRestrictions.size
		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)

		// To reduce duplication of the same tests, any argument that has the
		// same type in all definitions, but has not been proven yet, should be
		// selected first.
		if (undecidedElements.size > 1)
		{
			val commonArgTypes = MutableList<A_Type?>(numArgs) { null }
			// See if the type tags differ for any of the values.  Assume type
			// tag dispatch is always more efficient than type testing.
			val commonTags = MutableList<TypeTag?>(numArgs) { null }
			// Check for any tag variations.  Also check for common types that
			// have not yet been verified at this point.
			undecidedElements.forEachIndexed { elementIndex, element ->
				val argTypes = adaptor
					.restrictedSignature(
						element, signatureExtrasExtractor, bound)
					.tupleOfTypesFromTo(1, numArgs)
				for (i in 0 until numArgs)
				{
					val argType = argTypes.tupleAt(i + 1)
					if (elementIndex == 0)
					{
						commonArgTypes[i] = argType
						commonTags[i] = argType.instanceTag
					}
					else
					{
						if (commonArgTypes[i].notNullAnd { !equals(argType) })
						{
							commonArgTypes[i] = null
						}
						if (commonTags[i].notNullAnd {
								!equals(argType.instanceTag) })
						{
							commonTags[i] = null
						}
					}
				}
			}

			for (argNumber in 1 .. numArgs)
			{
				if (commonTags[argNumber - 1] === null
					&& !alreadyTypeTestedArguments.bitTest(argNumber - 1))
				{
					// A type tag dispatch here is guaranteed to reduce the
					// number of undecided elements in every subtree.
					return buildTypeTagTest(
						adaptor, memento, argNumber, signatureExtrasExtractor)
				}
			}

			// If an argument is known to have the OBJECT_TAG and hasn't been
			// dispatched via a ObjectLayoutVariantDecisionStep, do so now.
			for (argNumber in numArgs downTo 1)
			{
				if (commonTags[argNumber - 1] === OBJECT_TAG
					&& !alreadyVariantTestedArguments.bitTest(argNumber - 1))
				{
					// All the elements expect an object to be present.
					if (knownArgumentRestrictions[argNumber - 1]
							.type.isSubtypeOf(mostGeneralObjectType))
					{
						// The value is already known to be an object. Dispatch
						// on the variant.
						return buildObjectLayoutVariantStep(
							adaptor,
							memento,
							argNumber,
							signatureExtrasExtractor)
					}
					// The value hasn't been proven to be an object yet.
					// Introduce a tag dispatch, and the next layer will be
					// able to do the object dispatch safely.
					return buildTypeTagTest(
						adaptor,
						memento,
						argNumber,
						signatureExtrasExtractor)
				}
				if (commonTags[argNumber - 1] === OBJECT_TYPE_TAG
					&& !alreadyVariantTestedArguments.bitTest(argNumber - 1))
				{
					// All the elements expect an object type to be present.
					if (knownArgumentRestrictions[argNumber - 1]
							.type.isSubtypeOf(mostGeneralObjectMeta))
					{
						// The value is already known to be an object type.
						// Dispatch on the variant.
						return buildObjectTypeLayoutVariantStep(
							adaptor,
							memento,
							argNumber,
							signatureExtrasExtractor)
					}
					// The value hasn't been proven to be an object type yet.
					// Introduce a tag dispatch, and the next layer will be
					// able to do the object type dispatch safely.
					return buildTypeTagTest(
						adaptor,
						memento,
						argNumber,
						signatureExtrasExtractor)
				}
			}

			// See if everybody requires the same type for an argument, but
			// doesn't guarantee it's satisfied yet.
			if (positiveElements.isEmpty())
			{
				for (argNumber in 1 .. numArgs)
				{
					val commonType = commonArgTypes[argNumber - 1]
					if (commonType !== null
						&& !knownArgumentRestrictions[argNumber - 1]
							.containedByType(commonType))
					{
						// Everybody needs this argument to satisfy this exact
						// type, but the argument isn't known to satisfy it yet.
						// This test will be required by every traversal, so
						// rather than have duplicates near the leaves, test it
						// as early as possible.
						return buildTestArgument(
							adaptor,
							memento,
							argNumber,
							commonType,
							signatureExtrasExtractor)
					}
				}
			}

			// Make fast (tag or variant) dispatching available on a phrase's
			// expression type, by extracting it here, if possible.
			for (argNumber in 1 .. numArgs)
			{
				if (alreadyTypeTestedArguments.bitTest(argNumber - 1)
					|| alreadyPhraseTypeExtractArguments.bitTest(argNumber - 1))
					continue
				if (knownArgumentRestrictions[argNumber - 1].type.isSubtypeOf(
						PARSE_PHRASE.mostGeneralType))
				{
					// Extract the yield type of the phrase types, and dispatch
					// on that.  This is a relatively quick step to perform
					// during actual lookup, so don't bother optimizing away the
					// case that it doesn't lead to an interesting, more
					// efficient subtree.
					return buildExtractPhraseType(adaptor, memento, argNumber)
				}
				// TODO: Other covariant relationships can be traversed here...
			}
		}

		// Choose a signature to test that guarantees it eliminates the most
		// undecided definitions, regardless of whether the test passes or
		// fails.  If the larger of the two cases (success or failure of the
		// test) is a tie between two criteria, break it by choosing the
		// criterion that eliminates the most undecided definitions in the
		// *best* case.
		var bestSignature: A_Type? = null
		var smallestMax = Integer.MAX_VALUE
		var smallestMin = Integer.MAX_VALUE
		val undecidedCount = undecidedElements.size
		for (criterionIndex in undecidedCount - 1 downTo 0)
		{
			val criterion = undecidedElements[criterionIndex]
			val criterionRestrictions =
				knownArgumentRestrictions.toMutableList()
			val boundedCriterionSignature = adaptor.restrictedSignature(
				criterion, signatureExtrasExtractor, bound)
			assert(!boundedCriterionSignature.isBottom)
			for (i in 1..numArgs)
			{
				criterionRestrictions[i - 1] =
					criterionRestrictions[i - 1].intersectionWithType(
						boundedCriterionSignature.typeAtIndex(i))
			}
			var undecidedCountIfTrue = 0
			var undecidedCountIfFalse = 0
			for (eachIndex in 0 until undecidedCount)
			{
				// Skip the element itself, since after a comparison it'll be
				// known to be included or be known not to be included.
				if (eachIndex != criterionIndex)
				{
					val each = undecidedElements[eachIndex]
					val eachSignature = adaptor.restrictedSignature(
						each, signatureExtrasExtractor, bound)
					val comparison = adaptor.compareTypes(
						criterionRestrictions, eachSignature)
					when (comparison)
					{
						TypeComparison.SAME_TYPE ->
						{
							// This might occur if the projection of two
							// elements under call-site-specific type bounds
							// yields the same type.  Or something unforeseen.
							undecidedCountIfTrue++
						}
						TypeComparison.PROPER_ANCESTOR_TYPE,
						TypeComparison.DISJOINT_TYPE ->
							undecidedCountIfFalse++
						TypeComparison.PROPER_DESCENDANT_TYPE ->
							undecidedCountIfTrue++
						TypeComparison.UNRELATED_TYPE ->
						{
							undecidedCountIfTrue++
							undecidedCountIfFalse++
						}
					}
				}
			}
			val maxCount = max(undecidedCountIfTrue, undecidedCountIfFalse)
			val minCount = min(undecidedCountIfTrue, undecidedCountIfFalse)
			assert(maxCount < undecidedElements.size)
			// Ties go to the higher numbered argument index, so that the
			// extracted extras will be used in preference to the values that
			// they were extracted from.
			if (maxCount < smallestMax
				|| (maxCount == smallestMax && minCount < smallestMin))
			{
				smallestMax = maxCount
				smallestMin = minCount
				bestSignature = boundedCriterionSignature
			}
		}
		assert(bestSignature !== null)

		// We have chosen one of the best signatures to test.  However, we still
		// need to decide which argument position to test.  Use the rightmost
		// one which is not already guaranteed by tests that have already been
		// performed.  In particular, ignore arguments whose knownArgumentTypes
		// information is a subtype of the chosen signature's argument type at
		// that position.
		var selectedTypeToTest: A_Type? = null
		var positionToTest: Int
		// Must be replaced in the loop below.
		positionToTest = -999
		// Use the reverse order, so that usable variations found in extras
		// will be preferred to variations found in the base arguments.
		for (i in numArgs downTo 1)
		{
			val knownRestriction = knownArgumentRestrictions[i - 1]
			val criterionArgumentType = bestSignature!!.typeAtIndex(i)
			if (knownRestriction.containedByType(criterionArgumentType))
			{
				// Don't use this position, because it will always be true.
				continue
			}
			if (!knownRestriction.intersectsType(criterionArgumentType))
			{
				// Don't use this position, because it will always be false.
				continue
			}
			positionToTest = i
			selectedTypeToTest = criterionArgumentType
			break
		}
		assert(positionToTest >= 1)
		return buildTestArgument(
			adaptor,
			memento,
			positionToTest,
			selectedTypeToTest!!,
			signatureExtrasExtractor)
	}

	/**
	 * Create a [TestArgumentDecisionStep] for the given values.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param memento
	 *   The memento to be provided to the adaptor.
	 * @param argumentIndex
	 *   The one-based index of the argument being tested.
	 * @param typeToTest
	 *   The [A_Type] that this node should test for.
	 * @param signatureExtrasExtractor
	 *   A function that extracts a [List] of [A_Type]s from an [Element],
	 *   corresponding with the extras that have been extracted from the value
	 *   being looked up at this point.
	 * @return
	 *   The resulting [DecisionStep].
	 */
	private fun <AdaptorMemento> buildTestArgument(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento,
		argumentIndex: Int,
		typeToTest: A_Type,
		signatureExtrasExtractor: (Element) -> Pair<A_Type?, List<A_Type>>,
	): TestArgumentDecisionStep<Element, Result>
	{
		val zeroBasedIndex = argumentIndex - 1
		val oldRestriction = knownArgumentRestrictions[zeroBasedIndex]

		val positiveKnownRestrictions =
			knownArgumentRestrictions.toMutableList()
		positiveKnownRestrictions[zeroBasedIndex] =
			oldRestriction.intersectionWithType(typeToTest)
		val positiveBound =
			adaptor.extractBoundingType(positiveKnownRestrictions)

		val negativeKnownRestrictions =
			knownArgumentRestrictions.toMutableList()
		negativeKnownRestrictions[zeroBasedIndex] =
			oldRestriction.minusType(typeToTest)
		val negativeBound =
			adaptor.extractBoundingType(negativeKnownRestrictions)

		// Check each element against the positiveKnownRestrictions, and
		// classify it as a positive hit in the holds branch, an undecided in
		// the holds branch, an undecided in the fails branch, or some
		// combination (but not both collections in the holds branch).
		val positiveIfTrue = positiveElements.toMutableList()
		val undecidedIfTrue = mutableListOf<Element>()
		val positiveIfFalse = mutableListOf<Element>()
		val undecidedIfFalse = mutableListOf<Element>()
		for (undecidedElement in undecidedElements)
		{
			val positiveComparison = adaptor.compareTypes(
				positiveKnownRestrictions,
				adaptor.restrictedSignature(
					undecidedElement, signatureExtrasExtractor, positiveBound))
			val negativeComparison = adaptor.compareTypes(
				negativeKnownRestrictions,
				adaptor.restrictedSignature(
					undecidedElement, signatureExtrasExtractor, negativeBound))
			positiveComparison.applyEffect(
				undecidedElement,
				positiveIfTrue,
				undecidedIfTrue)
			negativeComparison.applyEffect(
				undecidedElement,
				positiveIfFalse,
				undecidedIfFalse)
		}
		val ifCheckHolds = adaptor.createTree(
			positiveIfTrue,
			undecidedIfTrue,
			positiveKnownRestrictions,
			alreadyTypeTestedArguments,
			alreadyVariantTestedArguments,
			alreadyPhraseTypeExtractArguments,
			memento)
		// Since we're using TypeRestrictions, there are cases with instance
		// enumerations in which a failed test can actually certify a new
		// answer.  Merge the newly certified and already certified results.
		positiveIfFalse.addAll(positiveElements)
		val ifCheckFails = adaptor.createTree(
			positiveIfFalse,
			undecidedIfFalse,
			negativeKnownRestrictions,
			alreadyTypeTestedArguments,
			alreadyVariantTestedArguments,
			alreadyPhraseTypeExtractArguments,
			memento)
		// This is a volatile write, so all previous writes had to precede it.
		// If another process runs expandIfNecessary(), it will either see null
		// for this field, or see non-null and be guaranteed that all subsequent
		// reads will see all the previous writes.
		return TestArgumentDecisionStep(
			typeToTest.makeShared(), argumentIndex, ifCheckHolds, ifCheckFails)
	}

	/**
	 * Create a [TypeTagDecisionStep] for the given values.  The basic idea is
	 * to create a [Map] from tag to a [Set] of [Result]s.  Only add elements at
	 * the [A_Type.instanceTag] reported by the argument type.  During lookup,
	 * the [typeTag][AvailObjectRepresentation.typeTag] of the actual argument
	 * value is used to find an entry in this map, but if there's no entry, its
	 * parent chain is searched instead.
	 *
	 * [BOTTOM_TYPE_TAG] is problematic, because it breaks the tree shape.  We
	 * can't just leave it out, and we can't pretend it's not a child of all
	 * other types.  For simplicity, every time elements are added to any subtag
	 * of [TOP_TYPE_TAG], they're also added to [BOTTOM_TYPE_TAG].  That makes
	 * lookup inefficient only when the ⊥ type is the actual argument value.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param memento
	 *   The memento to be provided to the adaptor.
	 * @param argumentIndex
	 *   The one-based index of the argument being tested.
	 * @param signatureExtrasExtractor
	 *   A function that extracts a [List] of [A_Type]s from an [Element],
	 *   corresponding with the extras that have been extracted from the value
	 *   being looked up at this point.
	 * @return
	 *   The resulting [DecisionStep].
	 */
	private fun <AdaptorMemento> buildTypeTagTest(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento,
		argumentIndex: Int,
		signatureExtrasExtractor: (Element) -> Pair<A_Type?, List<A_Type>>,
	): TypeTagDecisionStep<Element, Result>
	{
		val tagToElements = mutableMapOf<TypeTag, MutableSet<Element>>()
		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)
		listOf(undecidedElements, positiveElements).forEach { list ->
			list.forEach { element ->
				val commonArgType = adaptor
					.restrictedSignature(
						element, signatureExtrasExtractor, bound)
					.typeAtIndex(argumentIndex)
				val tag = commonArgType.instanceTag
				val subset =
					tagToElements.computeIfAbsent(tag) { mutableSetOf() }
				subset.add(element)
				if (tag.isSubtagOf(TOP_TYPE_TAG))
				{
					// It's a type, so pump BOTTOM_TYPE_TAG as well.
					val bottomTypeSubset =
						tagToElements.computeIfAbsent(BOTTOM_TYPE_TAG) {
							mutableSetOf()
						}
					bottomTypeSubset.add(element)
				}
			}
		}
		// For each TypeTag that's present in the map, add in the elements
		// associated with its ancestors.  Later, when using this lookup tree,
		// the actually occurring TypeTag will have to be looked up, and if not
		// found, its ancestors must be searched.
		tagToElements.forEach { (k, v) ->
			var p = k.parent
			while (p != null) {
				tagToElements[p]?.let(v::addAll)
				p = p.parent
			}
		}
		val alreadyTested = alreadyTypeTestedArguments
			.bitSet(argumentIndex - 1, true, false)
			.makeShared()
		val tagToSubtree = tagToElements.mapValues { (tag, elements) ->
			val restrictions = knownArgumentRestrictions.toMutableList()
			var restriction = restrictions[argumentIndex - 1]
			restriction = restriction.intersectionWithType(tag.supremum)
			// NOTE: Exclude the supremum of any proper subtags that are present
			// in this dispatch step.  That's because during lookup,
			// encountering that other tag would have taken it on that other
			// branch, and any value under that supremum type would have had
			// that other tag or a subtag.
			tagToElements.forEach { (otherTag, _) ->
				if (otherTag !== tag && otherTag.isSubtagOf(tag))
				{
					restriction = restriction.minusType(otherTag.supremum)
				}
			}
			restrictions[argumentIndex - 1] = restriction

			val boundForTag = adaptor.extractBoundingType(restrictions)
			// The positive elements were processed along with the undecided
			// elements, so they're already represented in the tag-specific
			// subsets.
			val positive = mutableListOf<Element>()
			val undecided = mutableListOf<Element>()
			elements.forEach { element ->
				val positiveComparison = adaptor.compareTypes(
					restrictions,
					adaptor.restrictedSignature(
						element, signatureExtrasExtractor, boundForTag))
				//assert (positiveComparison != TypeComparison.DISJOINT_TYPE)
				positiveComparison.applyEffect(element, positive, undecided)
			}
			adaptor.createTree(
				positive.distinct(),
				undecided.distinct(),
				restrictions,
				alreadyTested,
				alreadyVariantTestedArguments,
				alreadyPhraseTypeExtractArguments,
				memento)
		}
		return TypeTagDecisionStep(argumentIndex, tagToSubtree)
	}

	/**
	 * Create an [ObjectLayoutVariantDecisionStep] for the given tree.  The
	 * idea is that we have already proven that the indicated argument is an
	 * object, so maintain a map from the exact argument variant to subtree, and
	 * populate it lazily.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param memento
	 *   The memento to be provided to the adaptor.
	 * @param argumentIndex
	 *   The one-based index of the argument being tested.
	 * @param signatureExtrasExtractor
	 *   A function that extracts a [List] of [A_Type]s from an [Element],
	 *   corresponding with the extras that have been extracted from the value
	 *   being looked up at this point.
	 * @return
	 *   The resulting [DecisionStep].
	 */
	private fun <AdaptorMemento> buildObjectLayoutVariantStep(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		@Suppress("UNUSED_PARAMETER") memento: AdaptorMemento,
		argumentIndex: Int,
		signatureExtrasExtractor: (Element) -> Pair<A_Type?, List<A_Type>>,
	): ObjectLayoutVariantDecisionStep<Element, Result>
	{
		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)
		val variantToElementsSet =
			mutableMapOf<ObjectLayoutVariant, MutableSet<Element>>()
		undecidedElements.forEach { element ->
			val commonArgType = adaptor
				.restrictedSignature(element, signatureExtrasExtractor, bound)
				.typeAtIndex(argumentIndex)
			when
			{
				commonArgType.isEnumeration -> {
					// The method has an argument typed as an enumeration of
					// some specific objects.  Add this element under each
					// instance's variant.
					commonArgType.instances.forEach { instance ->
						val variant = instance.objectVariant
						variantToElementsSet.getOrPut(variant) {
							mutableSetOf()
						}.add(element)
					}
				}
				else -> {
					// Store the element under the object type's variant.
					val variant = commonArgType.objectTypeVariant
					variantToElementsSet.getOrPut(variant) {
						mutableSetOf()
					}.add(element)
				}
			}
		}
		return ObjectLayoutVariantDecisionStep(
			this,
			argumentIndex,
			signatureExtrasExtractor,
			variantToElementsSet.mapValues { it.value.toList() },
			alreadyVariantTestedArguments.bitSet(argumentIndex - 1, true, false)
				.makeShared())
	}

	/**
	 * Create an [ObjectTypeLayoutVariantDecisionStep] for the given tree.  The
	 * idea is that we have already proven that the indicated argument is an
	 * object type, so maintain a map from the exact argument variant to
	 * subtree, and populate it lazily.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param memento
	 *   The memento to be provided to the adaptor.
	 * @param argumentIndex
	 *   The one-based index of the argument being tested.
	 * @param signatureExtrasExtractor
	 *   A function that extracts a [List] of [A_Type]s from an [Element],
	 *   corresponding with the extras that have been extracted from the value
	 *   being looked up at this point.
	 * @return
	 *   The resulting [DecisionStep].
	 */
	private fun <AdaptorMemento> buildObjectTypeLayoutVariantStep(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		@Suppress("UNUSED_PARAMETER") memento: AdaptorMemento,
		argumentIndex: Int,
		signatureExtrasExtractor: (Element) -> Pair<A_Type?, List<A_Type>>,
	): ObjectTypeLayoutVariantDecisionStep<Element, Result>
	{
		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)
		val variantToElementsSet =
			mutableMapOf<ObjectLayoutVariant, MutableSet<Element>>()
		undecidedElements.forEach { element ->
			val commonArgType = adaptor
				.restrictedSignature(element, signatureExtrasExtractor, bound)
				.typeAtIndex(argumentIndex)
			val variant = commonArgType.instance.objectTypeVariant
			variantToElementsSet.getOrPut(variant) {
				mutableSetOf()
			}.add(element)
		}
		return ObjectTypeLayoutVariantDecisionStep(
			this,
			argumentIndex,
			signatureExtrasExtractor,
			variantToElementsSet.mapValues { it.value.toList() },
			alreadyVariantTestedArguments.bitSet(argumentIndex - 1, true, false)
				.makeShared())
	}

	/**
	 * Create an [ExtractPhraseTypeDecisionStep] for the given tree.  We've
	 * already selected a particular argument known to contain a phrase, and we
	 * wish to extract its yield type to expose faster dispatching, such as by
	 * tag or object variant.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param memento
	 *   The memento to be provided to the adaptor.
	 * @param argumentIndex
	 *   The one-based index of the phrase argument having its yield type
	 *   extracted.
	 * @return
	 *   The resulting [DecisionStep].
	 */
	private fun <AdaptorMemento> buildExtractPhraseType(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		@Suppress("UNUSED_PARAMETER") memento: AdaptorMemento,
		argumentIndex: Int,
	): ExtractPhraseTypeDecisionStep<Element, Result>
	{
		// While this step extracts a covariant subobject (the expression type
		// of a phrase), it doesn't make any decisions itself.
		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)
		val phraseType = bound.typeAtIndex(argumentIndex)
		val expressionType = instanceMeta(phraseType.phraseTypeExpressionType)
		val child = InternalLookupTree<Element, Result>(
			positiveElements,
			undecidedElements,
			knownArgumentRestrictions.append(
				restrictionForType(expressionType, BOXED_FLAG)),
			alreadyTypeTestedArguments,
			// Phrases don't have variants, but set this for good measure.
			alreadyVariantTestedArguments,
			alreadyPhraseTypeExtractArguments
				.bitSet(argumentIndex - 1, true, false).makeShared())
		return ExtractPhraseTypeDecisionStep(argumentIndex, child)
	}

	override fun toString(indent: Int): String = when (val step = decisionStep)
	{
		null -> increaseIndentation(
			format(
				"Lazy internal node: (u=%d, p=%d) known=%s",
				undecidedElements.size,
				positiveElements.size,
				knownArgumentRestrictions),
			indent + 1)
		else -> buildString {
			step.describe(this@InternalLookupTree, indent, this@buildString)
		}
	}
}
