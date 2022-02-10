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

import avail.descriptor.atoms.A_Atom
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.maps.A_Map.Companion.mapAtReplacingCanDestroy
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.bitSet
import avail.descriptor.numbers.A_Number.Companion.bitTest
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.objectVariant
import avail.descriptor.representation.AvailObjectRepresentation
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceTag
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.objectTypeVariant
import avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NONTYPE
import avail.descriptor.types.TypeTag
import avail.descriptor.types.TypeTag.BOTTOM_TYPE_TAG
import avail.descriptor.types.TypeTag.META_TAG
import avail.descriptor.types.TypeTag.OBJECT_TAG
import avail.descriptor.types.TypeTag.OBJECT_TYPE_TAG
import avail.descriptor.types.TypeTag.PHRASE_TAG
import avail.descriptor.types.TypeTag.TOP_TYPE_TAG
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
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
 * @property alreadyTagTestedArguments
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
 * @property alreadyMetaInstanceExtractArguments
 *   An Avail [integer][A_Number] coding which arguments (and extras that may
 *   have been generated during traversal of ancestors) were known to be
 *   metatypes, and have had their instance (a type) extracted already into
 *   another field via an [ExtractMetaInstanceDecisionStep] in an ancestor.
 *   Argument #n is indicated by a set bit in the n-1st bit position, the one
 *   whose value in the integer is 2^(n-1).
 * @property alreadyPhraseTypeExtractArguments
 *   An Avail [integer][A_Number] coding which arguments (and extras that may
 *   have been generated during traversal of ancestors) were known to be phrase
 *   types, and have had their yield type extracted already into another field
 *   via an [ExtractPhraseTypeDecisionStep] in an ancestor.  Argument #n is
 *   indicated by a set bit in the n-1st bit position, the one whose value in
 *   the integer is 2^(n-1).
 * @property alreadyEnumerationOfNontypeTested
 *   An Avail [integer][A_Number] coding which arguments (and extras that may
 *   have been generated during traversal of ancestors) were known to contain at
 *   least one enumeration of non-types.  Those actual values can be used to
 *   look up subtrees for which that value is a possible instance of an actual
 *   provided type.  See [TestForEnumerationOfNontypeDecisionStep].  The flag
 *   for argument #n is indicated by a set bit in the n-1st bit position, and
 *   serves to suppress subsequent attempts to dispatch this way on this
 *   argument in subtrees.
 * @property alreadyExtractedFields
 *   An [A_Map] from Avail integer to Avail integer.  They key integer is the
 *   one-based argument (or extras) subscript of the source object, and the
 *   value integer is an encoding of which fields have been extracted, where bit
 *   2^(N-1) indicates the Nth field (one-based) has been extracted already.
 *   Since objects and object types are disjoint, this same map is used for both
 *   purposes without ambiguity.
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
	val alreadyTagTestedArguments: A_Number,
	private val alreadyVariantTestedArguments: A_Number,
	val alreadyMetaInstanceExtractArguments: A_Number,
	val alreadyPhraseTypeExtractArguments: A_Number,
	val alreadyTestedConstants: A_Number,
	val alreadyEnumerationOfNontypeTested: A_Number,
	val alreadyExtractedFields: A_Map)
: LookupTree<Element, Result>()
{
	override val solutionOrNull: Result? get() = null

	init
	{
		assert(alreadyTagTestedArguments.descriptor().isShared)
		assert(alreadyVariantTestedArguments.descriptor().isShared)
		assert(alreadyMetaInstanceExtractArguments.descriptor().isShared)
		assert(alreadyPhraseTypeExtractArguments.descriptor().isShared)
		assert(alreadyTestedConstants.descriptor().isShared)
		assert(alreadyExtractedFields.descriptor().isShared)
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
		numNaturalArgs: Int,
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
					signatureExtrasExtractor, adaptor, numNaturalArgs, memento)
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
		numNaturalArgs: Int,
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
			val commonArgTypes = arrayOfNulls<A_Type>(numArgs)
			// See if the type tags differ for any of the values.  Assume type
			// tag dispatch is always more efficient than type testing.
			val commonTags = arrayOfNulls<TypeTag>(numArgs)
			// Collect the constants that are available for dispatch in each
			// argument position.  Enumerations (not metas, other than
			// bottomMeta) may provide multiple values.
			val constantSets = Array(numArgs) { mutableSetOf<A_BasicObject>() }
			// Collect the (element, restrictedSignatureTupleType) pairs.
			val restrictedElements = undecidedElements.map { element ->
				element to adaptor.restrictedSignature(
					element, signatureExtrasExtractor, bound)
			}
			val iterator = restrictedElements.iterator()
			val firstRestricted = iterator.next().second
			// Check for any tag variations.  Also check for common types that
			// have not yet been verified at this point.
			repeat(numArgs) { argNumber ->
				// Note that this is a statement about the *restriction*, so
				// it doesn't use anything from any elements.
				val argType = firstRestricted.typeAtIndex(argNumber + 1)
				commonArgTypes[argNumber] = argType
				commonTags[argNumber] = argType.instanceTag
				if (!alreadyTestedConstants.bitTest(argNumber))
				{
					if (argType.isEnumeration &&
						(!argType.isInstanceMeta || argType.equals(bottomMeta)))
					{
						constantSets[argNumber].addAll(argType.instances)
					}
				}
			}
			iterator.forEachRemaining { (_, argsType) ->
				repeat(numArgs) { argNumber ->
					val argType = argsType.typeAtIndex(argNumber + 1)
					if (commonArgTypes[argNumber].notNullAnd {
							!equals(argType) })
					{
						commonArgTypes[argNumber] = null
					}
					if (commonTags[argNumber].notNullAnd {
							!equals(argType.instanceTag)
						})
					{
						commonTags[argNumber] = null
					}
				}
			}
			val mostConstants =
				constantSets.withIndex().maxByOrNull { (_, set) -> set.size }!!
			if (mostConstants.value.size >= 3)
			{
				// At least 2 constants can be used to dispatch this argument.
				// Save computation time for building this up in subtrees by
				// figuring out which argument positions have less than 2
				// constants, and pretending those have already done their
				// constant-dispatch.
				var testedFlags = alreadyTestedConstants
					.bitSet(mostConstants.index, true, true)
				constantSets.forEachIndexed { i, set ->
					if (set.size < 3)
					{
						testedFlags = testedFlags.bitSet(i, true, true)
					}
				}
				return buildTestConstants(
					adaptor,
					memento,
					mostConstants.index + 1,
					testedFlags.makeShared(),
					signatureExtrasExtractor)
			}

			for (argNumber in numArgs downTo 1)
			{
				val zeroArgNumber = argNumber - 1
				val commonTag = commonTags[zeroArgNumber]
				if (!alreadyTagTestedArguments.bitTest(zeroArgNumber) &&
					(commonTag === null
						|| commonTag === OBJECT_TAG
						|| commonTag === OBJECT_TYPE_TAG
						|| commonTag.isSubtagOf(PHRASE_TAG)
						|| commonTag === META_TAG))
				{
					// A type tag dispatch here reduces the number of undecided
					// elements in subtrees.
					return buildTypeTagTest(
						adaptor, memento, argNumber, signatureExtrasExtractor)
				}
			}

			for (argNumber in numArgs downTo 1)
			{
				val zeroArgNumber = argNumber - 1
				val restriction = knownArgumentRestrictions[zeroArgNumber]
				if (restriction.type.isSubtypeOf(instanceMeta(topMeta()))
					&& !restriction.type.isSubtypeOf(bottomMeta))
				{
					// Extract the meta's instance, itself a type.  The instance
					// covaries with the meta due to metacovariance.
					// First, however, make sure the meta isn't the degenerate
					// type ⊥.  ⊥'s type is fine, however.
					assert(alreadyTagTestedArguments.bitTest(zeroArgNumber))
					if (!alreadyMetaInstanceExtractArguments
							.bitTest(zeroArgNumber))
					{
						return buildExtractMetaInstance(adaptor, argNumber)
					}
				}
				if (!alreadyEnumerationOfNontypeTested.bitTest(zeroArgNumber)
					&& restriction.type.isSubtypeOf(topMeta())
					&& !restriction.type.isSubtypeOf(bottomMeta))
				{
					// If at least one of the elements expect a meta, *and* that
					// meta's instance is an enumeration, but *not* of other
					// types, then we can extract that enumeration's values and
					// use each one wrapped in a singular instance type as keys
					// in a lookup map.  When we later go to look up an
					// argument, if it's a key of the map, we use the associated
					// subtree.  All other cases use a fallThrough subtree.
					val instanceTypes = restrictedElements
						.flatMap { restricted ->
							val (_, tupleType) = restricted
							val argMeta = tupleType.typeAtIndex(argNumber)
							when
							{
								!argMeta.isInstanceMeta -> emptyList()
								argMeta.instance.run {
									!isEnumeration
										|| isBottom
										|| !isSubtypeOf(NONTYPE.o)
								} -> emptyList()
								else -> argMeta.instance.instances
							}
						}
						.map(::instanceType)
						.distinct<A_Type>()
					if (instanceTypes.isNotEmpty())
					{
						val elementsByInstanceType =
							instanceTypes.associateWith { instanceType ->
								restrictedElements
									.filter { (_, tupleType) ->
										instanceType.isInstanceOf(
											tupleType.typeAtIndex(argNumber))
									}
									.map { it.first }
									.toSet()
							}
						return buildDispatchByEnumerationOfNontype(
							adaptor, memento, argNumber, elementsByInstanceType)
					}
				}
				if (restriction.type.isSubtypeOf(mostGeneralObjectType)
					&& !restriction.type.isBottom)
				{
					// The argument is an object.  Do a variant dispatch if we
					// haven't already.
					if (!alreadyVariantTestedArguments.bitTest(zeroArgNumber))
					{
						return buildObjectLayoutVariantStep(
							adaptor, argNumber,signatureExtrasExtractor)
					}
					// We know the argument is an object and we know it has been
					// narrowed down to one variant.  See if extracting a field
					// will be productive.
					val variant =
						restriction.positiveGroup.objectVariants!!.single()
					val withCounts = variant.fieldToSlotIndex.entries
						.filter { (_, index) ->
							index > 0 &&
								!(alreadyExtractedFields
									.mapAtOrNull(fromInt(argNumber)) ?: zero)
									.bitTest(index - 1)
						}
						.map { entry ->
							entry to restrictedElements.distinctBy {
								it.second.typeAtIndex(argNumber)
									.fieldTypeAtIndex(entry.value)
							}.size
						}
					if (withCounts.isNotEmpty())
					{
						val (best, bestCount) =
							withCounts.maxByOrNull(Pair<*, Int>::second)!!
						if (bestCount > 1)
						{
							// For this argument, we've found the field with the
							// most distinct types (over the elements). There's
							// more than one such type for this field, so
							// extract it and let subsequent steps differentiate
							// them efficiently.
							return buildExtractObjectField(
								adaptor, argNumber, best.key, best.value)
						}
					}
					// None of the fields of this argument had more than one
					// expected type across the remaining elements.
					continue
				}

				if (restriction.type.isSubtypeOf(mostGeneralObjectMeta)
					&& !restriction.type.isSubtypeOf(bottomMeta))
				{
					// It's definitely an object type, but it might be the
					// degenerate type bottom.  Fall back to comparison testing
					// if bottom is still possible here.
					if (!alreadyTagTestedArguments.bitTest(zeroArgNumber)
						&& restriction.intersectsType(bottomMeta))
					{
						return buildTypeTagTest(
							adaptor,
							memento,
							argNumber,
							signatureExtrasExtractor)
					}
					// The type tag test produces a BOTTOM_TYPE_TAG case if it
					// was possible, so we may be in such a situation at this
					// point (and we should fall through to simple type
					// testing).  Otherwise introduce a type variant dispatch if
					// we haven't already.
					if (!restriction.intersectsType(bottomMeta)
						&& !alreadyVariantTestedArguments.bitTest(
							zeroArgNumber))
					{
						return buildObjectTypeLayoutVariantStep(
							adaptor, argNumber, signatureExtrasExtractor)
					}
					// We know the argument is an object type, and we know it
					// has been narrowed down to one variant.  See if extracting
					// a field type will be productive.
					val variant =
						restriction.positiveGroup.objectTypeVariants!!.single()
					val withCounts = variant.fieldToSlotIndex.entries
						.filter { (_, index) ->
							index > 0 &&
								!(alreadyExtractedFields
									.mapAtOrNull(fromInt(argNumber)) ?: zero)
									.bitTest(index - 1)
						}
						.map { entry ->
							entry to restrictedElements.distinctBy {
								it.second.typeAtIndex(argNumber).instance
									.fieldTypeAtIndex(entry.value)
							}.size
						}
					if (withCounts.isNotEmpty())
					{
						val (best, bestCount) =
							withCounts.maxByOrNull(Pair<*, Int>::second)!!
						if (bestCount > 1)
						{
							// For this argument, an object type, we've found
							// the field with the most distinct field types
							// (over the elements). There's more than one such
							// type for this field, so extract it and let
							// subsequent steps differentiate them efficiently.
							return buildExtractObjectTypeField(
								adaptor, argNumber, best.key, best.value)
						}
					}
					// None of the fields of this argument had more than one
					// expected type across the remaining elements.
					continue
				}

				if (restriction.type.isSubtypeOf(PARSE_PHRASE.mostGeneralType)
					&& !alreadyPhraseTypeExtractArguments
						.bitTest(zeroArgNumber))
				{
					// The argument is a phrase.  Extract its expression type if
					// it hasn't been extracted yet, and if the elements differ
					// in what they expect for the expression type.
					val distinctCount = restrictedElements.distinctBy {
						it.second.typeAtIndex(argNumber)
							.phraseTypeExpressionType
					}.size
					if (distinctCount > 1)
					{
						// It's useful to extract the phrase's expression type
						// and dispatch in some way on it.
						return buildExtractPhraseType(adaptor, argNumber)
					}
				}

				// NOTE: Other covariant relationships can be added here.
			}

			// See if everybody requires the same type for a *natural* argument
			// (i.e., not an extracted value) but doesn't guarantee that it's
			// satisfied yet.
			if (positiveElements.isEmpty())
			{
				for (argNumber in 1 .. numNaturalArgs)
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
		}
		// Fall back to doing a simple type test.  Choose a signature to test
		// that guarantees it eliminates the most undecided definitions,
		// regardless of whether the test passes or fails.  If the larger of the
		// two cases (success or failure of the test) is a tie between two
		// criteria, break it by choosing the criterion that eliminates the most
		// undecided definitions in the *best* case.
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
			alreadyTagTestedArguments,
			alreadyVariantTestedArguments,
			alreadyMetaInstanceExtractArguments,
			alreadyPhraseTypeExtractArguments,
			alreadyTestedConstants,
			alreadyEnumerationOfNontypeTested,
			alreadyExtractedFields,
			memento)
		// Since we're using TypeRestrictions, there are cases with instance
		// enumerations in which a failed test can actually certify a new
		// answer.  Merge the newly certified and already certified results.
		positiveIfFalse.addAll(positiveElements)
		val ifCheckFails = adaptor.createTree(
			positiveIfFalse,
			undecidedIfFalse,
			negativeKnownRestrictions,
			alreadyTagTestedArguments,
			alreadyVariantTestedArguments,
			alreadyMetaInstanceExtractArguments,
			alreadyPhraseTypeExtractArguments,
			alreadyTestedConstants,
			alreadyEnumerationOfNontypeTested,
			alreadyExtractedFields,
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
		val tagToElements =
			mutableMapOf<TypeTag, MutableSet<Pair<Element, A_Type>>>()
		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)
		val restrictedElements = (undecidedElements + positiveElements).map {
			it to adaptor.restrictedSignature(
				it, signatureExtrasExtractor, bound)
		}
		restrictedElements.forEach { restrictedElement ->
			val argType = restrictedElement.second.typeAtIndex(argumentIndex)
			val tag = argType.instanceTag
			val subset = tagToElements.computeIfAbsent(tag) { mutableSetOf() }
			subset.add(restrictedElement)
			if (tag.isSubtagOf(TOP_TYPE_TAG))
			{
				// It's a type, so pump BOTTOM_TYPE_TAG as well.
				val bottomTypeSubset =
					tagToElements.computeIfAbsent(BOTTOM_TYPE_TAG) {
						mutableSetOf()
					}
				bottomTypeSubset.add(restrictedElement)
			}
		}
		// For each TypeTag that's present in the map, add in the elements
		// associated with its ancestors.  Later, when using this lookup tree,
		// the actually occurring TypeTag will have to be looked up, and if not
		// found, its ancestors must be searched.
		// NOTE: Among other things, this can cause elements taking primitive
		// types (e.g., ANY.o, or even MODULE.o) to be copied down into, say,
		// OBJECT_TYPE_TAG, so be aware of this possibility during subsequent
		// variant testing.
		tagToElements.forEach { (k, v) ->
			var p = k.parent
			while (p != null) {
				tagToElements[p]?.let(v::addAll)
				p = p.parent
			}
		}
		assert(!alreadyTagTestedArguments.bitTest(argumentIndex - 1))
		val alreadyTested = alreadyTagTestedArguments
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
					element.second.typeIntersection(
						adaptor.restrictedSignature(
							element.first,
							signatureExtrasExtractor,
							boundForTag)))
				positiveComparison.applyEffect(
					element.first, positive, undecided)
			}
			adaptor.createTree(
				positive.distinct(),
				undecided.distinct(),
				restrictions,
				alreadyTested,
				alreadyVariantTestedArguments,
				alreadyMetaInstanceExtractArguments,
				alreadyPhraseTypeExtractArguments,
				alreadyTestedConstants,
				alreadyEnumerationOfNontypeTested,
				alreadyExtractedFields,
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
				commonArgType.isEnumeration ->
				{
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
				else ->
				{
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
		argumentIndex: Int,
		signatureExtrasExtractor: (Element) -> Pair<A_Type?, List<A_Type>>,
	): ObjectTypeLayoutVariantDecisionStep<Element, Result>
	{
		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)
		val variantToElementsSet =
			mutableMapOf<ObjectLayoutVariant, MutableSet<Element>>()
		undecidedElements.forEach { element ->
			val argType = adaptor
				.restrictedSignature(element, signatureExtrasExtractor, bound)
				.typeAtIndex(argumentIndex)
			// Intersect it with object, to ensure there's a variant (i.e.,
			// using the root variant if the element expected something above
			// object.
			val intersectedType = argType.instance
				.typeIntersection(mostGeneralObjectType)
			// There's probably an easier way of excluding non-object types
			// (e.g., MODULE.o) earlier, but this should work fine.
			if (!intersectedType.isBottom)
			{
				val variant = intersectedType.objectTypeVariant
				variantToElementsSet.getOrPut(variant) {
					mutableSetOf()
				}.add(element)
			}
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
	 * Create an [ExtractMetaInstanceDecisionStep] for the given tree.  We've
	 * already selected a particular argument known to contain a metatype, and
	 * we wish to extract its instance, itself a type, to expose faster
	 * dispatching, such as by tag or object variant.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param argumentIndex
	 *   The one-based index of the metatype argument having its instance
	 *   extracted.
	 * @return
	 *   The resulting [DecisionStep].
	 */
	private fun <AdaptorMemento> buildExtractMetaInstance(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		argumentIndex: Int,
	): ExtractMetaInstanceDecisionStep<Element, Result>
	{
		// While this step extracts a covariant subobject (the meta's instance),
		// it doesn't make any decisions itself.
		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)
		val typeOfMeta = bound.typeAtIndex(argumentIndex)
		val typeOfInstance = typeOfMeta.instance
		// Note: We can't reapply the source's restrictions in any way here,
		// because it's a restriction on which metatypes could occur, not which
		// types could be instances of them.
		val newRestriction = restrictionForType(typeOfInstance, BOXED_FLAG)
		val child = InternalLookupTree<Element, Result>(
			positiveElements,
			undecidedElements,
			knownArgumentRestrictions.append(newRestriction),
			alreadyTagTestedArguments,
			// Phrases don't have variants, but set this for good measure.
			alreadyVariantTestedArguments,
			alreadyMetaInstanceExtractArguments
				.bitSet(argumentIndex - 1, true, false).makeShared(),
			alreadyPhraseTypeExtractArguments,
			alreadyTestedConstants,
			alreadyEnumerationOfNontypeTested,
			alreadyExtractedFields)
		return ExtractMetaInstanceDecisionStep(argumentIndex, child)
	}

	/**
	 * Create an [ExtractPhraseTypeDecisionStep] for the given tree.  We've
	 * already selected a particular argument known to contain a phrase, and we
	 * wish to extract its yield type to expose faster dispatching, such as by
	 * tag or object variant.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param argumentIndex
	 *   The one-based index of the phrase argument having its yield type
	 *   extracted.
	 * @return
	 *   The resulting [DecisionStep].
	 */
	private fun <AdaptorMemento> buildExtractPhraseType(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
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
			alreadyTagTestedArguments,
			// Phrases don't have variants, but set this for good measure.
			alreadyVariantTestedArguments,
			alreadyMetaInstanceExtractArguments,
			alreadyPhraseTypeExtractArguments
				.bitSet(argumentIndex - 1, true, false).makeShared(),
			alreadyTestedConstants,
			alreadyEnumerationOfNontypeTested,
			alreadyExtractedFields)
		return ExtractPhraseTypeDecisionStep(argumentIndex, child)
	}

	/**
	 * Create an [ExtractObjectFieldDecisionStep] for the given tree.  We've
	 * already selected a particular argument known to contain an object with a
	 * particular variant, as well as the field and its known index that we wish
	 * to extract.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param argumentIndex
	 *   The one-based index of the object argument having a field extracted.
	 * @param fieldName
	 *   The field (an [A_Atom]) to extract.
	 * @param fieldIndex
	 *   The slot index of the field to extract.  Note that this requires the
	 *   exact [ObjectLayoutVariant] to be known.
	 * @return
	 *   The resulting [DecisionStep].
	 */
	private fun <AdaptorMemento> buildExtractObjectField(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		argumentIndex: Int,
		fieldName: A_Atom,
		fieldIndex: Int
	): ExtractObjectFieldDecisionStep<Element, Result>
	{
		// While this step extracts a covariant subobject (an object's field),
		// it doesn't make any decisions itself.
		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)
		val objectType = bound.typeAtIndex(argumentIndex)
		val fieldType = objectType.fieldTypeAtIndex(fieldIndex)
		val newExtractedMap = alreadyExtractedFields
			.mapAtReplacingCanDestroy(fromInt(argumentIndex), zero, false) {
					_, bits -> bits.bitSet(fieldIndex - 1, true, false)
			}
			.makeShared()
		assert(!newExtractedMap.equals(alreadyExtractedFields))
		val child = InternalLookupTree<Element, Result>(
			positiveElements,
			undecidedElements,
			knownArgumentRestrictions.append(
				restrictionForType(fieldType, BOXED_FLAG)),
			alreadyTagTestedArguments,
			alreadyVariantTestedArguments,
			alreadyMetaInstanceExtractArguments,
			alreadyPhraseTypeExtractArguments,
			alreadyTestedConstants,
			alreadyEnumerationOfNontypeTested,
			newExtractedMap)
		return ExtractObjectFieldDecisionStep(
			argumentIndex, fieldName, fieldIndex, child)
	}

	/**
	 * Create an [ExtractObjectTypeFieldDecisionStep] for the given tree.  We've
	 * already selected a particular argument known to contain an object type
	 * with a particular variant, as well as the field and its known index that
	 * we wish to extract.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param argumentIndex
	 *   The one-based index of the object type argument having a field
	 *   extracted.
	 * @param fieldName
	 *   The field (an [A_Atom]) to extract.
	 * @param fieldIndex
	 *   The slot index of the field type to extract.  Note that this requires
	 *   the exact [ObjectLayoutVariant] to be known.
	 * @return
	 *   The resulting [DecisionStep].
	 */
	private fun <AdaptorMemento> buildExtractObjectTypeField(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		argumentIndex: Int,
		fieldName: A_Atom,
		fieldIndex: Int
	): ExtractObjectTypeFieldDecisionStep<Element, Result>
	{
		// While this step extracts a covariant subobject (an object type's
		// field type), it doesn't make any decisions itself.
		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)
		val objectMeta = bound.typeAtIndex(argumentIndex)
		val fieldType = objectMeta.instance.fieldTypeAtIndex(fieldIndex)
		val fieldMeta = instanceMeta(fieldType)
		val newExtractedMap = alreadyExtractedFields
			.mapAtReplacingCanDestroy(fromInt(argumentIndex), zero, false) {
					_, bits -> bits.bitSet(fieldIndex - 1, true, false)
			}
			.makeShared()
		assert(!newExtractedMap.equals(alreadyExtractedFields))
		val child = InternalLookupTree<Element, Result>(
			positiveElements,
			undecidedElements,
			knownArgumentRestrictions.append(
				restrictionForType(fieldMeta, BOXED_FLAG)),
			alreadyTagTestedArguments,
			alreadyVariantTestedArguments,
			alreadyMetaInstanceExtractArguments,
			alreadyPhraseTypeExtractArguments,
			alreadyTestedConstants,
			alreadyEnumerationOfNontypeTested,
			newExtractedMap)
		return ExtractObjectTypeFieldDecisionStep(
			argumentIndex, fieldName, fieldIndex, child)
	}

	/**
	 * Create a [TestForConstantsDecisionStep] for the given tree.  The idea is
	 * that we know there are at least a threshold number of constants being
	 * looked for in the given argument position, so use them to dispatch this
	 * method.  If the value doesn't happen to be one of those constants, use a
	 * fall-through subtree.  Also, because looking up by type won't work the
	 * same way, create a pass-through tree with all elements for type-based
	 * lookups.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param memento
	 *   The memento to be provided to the adaptor.
	 * @param argumentIndex
	 *   The one-based index of the argument being constant-tested.
	 * @param testedFlags
	 *   An Avail integer that indicates which arguments have already had
	 *   constant testing performed in super-trees, or have been permanently
	 *   disqualified by having too few constants (further dispatching won't
	 *   increase this number).
	 * @param signatureExtrasExtractor
	 *   A function that extracts a [List] of [A_Type]s from an [Element],
	 *   corresponding with the extras that have been extracted from the value
	 *   being looked up at this point.
	 * @return
	 *   The resulting [DecisionStep].
	 */
	private fun <AdaptorMemento> buildTestConstants(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento,
		argumentIndex: Int,
		testedFlags: A_Number,
		signatureExtrasExtractor: (Element) -> Pair<A_Type?, List<A_Type>>,
	): TestForConstantsDecisionStep<Element, Result>
	{
		val elementsByConstant =
			mutableMapOf<A_BasicObject, MutableSet<Element>>()
		val noMatches = mutableSetOf<Element>()
		val bound = adaptor.extractBoundingType(knownArgumentRestrictions)
		undecidedElements.forEach { element ->
			val type = adaptor.restrictedSignature(
				element, signatureExtrasExtractor, bound)
			val argType = type.typeAtIndex(argumentIndex)
			if (argType.isEnumeration &&
				(!argType.isInstanceMeta || argType.equals(bottomMeta)))
			{
				argType.instances.forEach { instance ->
					elementsByConstant.compute(instance) { _, set ->
						(set ?: mutableSetOf()).apply { add(element) }
					}
				}
			}
			else
			{
				noMatches.add(element)
			}
		}
		return TestForConstantsDecisionStep(
			argumentIndex,
			elementsByConstant.mapValues { (constant, elements) ->
				val newRestrictions = knownArgumentRestrictions.toMutableList()
				newRestrictions[argumentIndex - 1] =
					restrictionForConstant(constant, BOXED_FLAG)
				val newBound = adaptor.extractBoundingType(newRestrictions)
				// The positive elements were processed along with the undecided
				// elements, so they're already represented in the tag-specific
				// subsets.
				val positives = positiveElements.toMutableList()
				val undecided = mutableListOf<Element>()
				elements.forEach { element ->
					val comparison = adaptor.compareTypes(
						newRestrictions,
						adaptor.restrictedSignature(
							element,
							signatureExtrasExtractor,
							newBound))
					comparison.applyEffect(element, positives, undecided)
				}
				adaptor.createTree(
					positives,
					undecided,
					newRestrictions,
					alreadyTagTestedArguments,
					alreadyVariantTestedArguments,
					alreadyMetaInstanceExtractArguments,
					alreadyPhraseTypeExtractArguments,
					testedFlags,
					alreadyEnumerationOfNontypeTested,
					alreadyExtractedFields,
					memento)
			},
			noMatchSubtree = adaptor.createTree(
				positiveElements,
				noMatches.toList(),
				knownArgumentRestrictions.toMutableList().also {
					it[argumentIndex - 1] = it[argumentIndex - 1].minusType(
						enumerationWith(
							setFromCollection(elementsByConstant.keys)))
				},
				alreadyTagTestedArguments,
				alreadyVariantTestedArguments,
				alreadyMetaInstanceExtractArguments,
				alreadyPhraseTypeExtractArguments,
				testedFlags,
				alreadyEnumerationOfNontypeTested,
				alreadyExtractedFields,
				memento),
			bypassForTypeLookup = adaptor.createTree(
				positiveElements,
				undecidedElements,
				knownArgumentRestrictions,
				alreadyTagTestedArguments,
				alreadyVariantTestedArguments,
				alreadyMetaInstanceExtractArguments,
				alreadyPhraseTypeExtractArguments,
				testedFlags,
				alreadyEnumerationOfNontypeTested,
				alreadyExtractedFields,
				memento)
		)
	}

	/**
	 * Create a [TestForEnumerationOfNontypeDecisionStep] for the given tree. We
	 * know that some of the elements are expecting a type to be passed in the
	 * indicated argument, and that the type is an enumeration over non-types.
	 * We are provided a map from instanceType (a singular enumeration over a
	 * non-type) to the [Element]s that can accept that singular enumeration as
	 * an argument.  This includes [Element]s that have kinds (non-enumerations)
	 * that subsume the particular non-type.
	 *
	 * Lookup for singular instance types is as fast as a map lookup for those
	 * cases where the instance type is a key of the map.  For all other cases,
	 * a separate subtree is used, which contains all of the current node's
	 * [Element]s, and which uses other techniques for dispatch.  This handles
	 * the case of enumerations of other values, enumerations of more than one
	 * non-type value, or the bottom type.
	 *
	 * @param adaptor
	 *   The [LookupTreeAdaptor] to use for expanding the tree.
	 * @param memento
	 *   The memento to be provided to the adaptor.
	 * @param argumentIndex
	 *   The one-based index of the argument being tested.  This decision step
	 *   is expected to be useful when the argument is an enumeration type
	 *   containing exactly one non-type value, which is also present as an
	 *   instance of at least one of the [Element]s.
	 * @param elementsByInstanceType
	 *   A map from instance types to the [Element]s that will accept that
	 *   instance type.  The included [Element]s may accept more than that one
	 *   value, either by being enumerations with multiple instances or by being
	 *   non-enumerations.
	 * @return
	 *   The resulting [DecisionStep].
	 */
	private fun <AdaptorMemento> buildDispatchByEnumerationOfNontype(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento,
		argumentIndex: Int,
		elementsByInstanceType: Map<A_Type, Set<Element>>
	): TestForEnumerationOfNontypeDecisionStep<Element, Result>
	{
		// Avoid duplicating this type of test in all children, including the
		// fallThrough.
		val testedFlags = alreadyEnumerationOfNontypeTested
			.bitSet(argumentIndex - 1, true, true).makeShared()
		val subtreesByInstanceType = elementsByInstanceType.mapValues {
				(instanceType, elements) ->
			adaptor.createTree(
				positiveElements,
				elements.toList(),
				knownArgumentRestrictions.toMutableList().also {
					it[argumentIndex - 1] =
						it[argumentIndex - 1].intersectionWithType(
							instanceMeta(instanceType))
				},
				alreadyTagTestedArguments,
				alreadyVariantTestedArguments,
				alreadyMetaInstanceExtractArguments,
				alreadyPhraseTypeExtractArguments,
				alreadyTestedConstants,
				testedFlags,
				alreadyExtractedFields,
				memento)
		}
		val fallThrough = adaptor.createTree(
			positiveElements,
			undecidedElements,
			knownArgumentRestrictions,
			alreadyTagTestedArguments,
			alreadyVariantTestedArguments,
			alreadyMetaInstanceExtractArguments,
			alreadyPhraseTypeExtractArguments,
			alreadyTestedConstants,
			testedFlags,
			alreadyExtractedFields,
			memento)
		return TestForEnumerationOfNontypeDecisionStep(
			argumentIndex, subtreesByInstanceType, fallThrough)
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
