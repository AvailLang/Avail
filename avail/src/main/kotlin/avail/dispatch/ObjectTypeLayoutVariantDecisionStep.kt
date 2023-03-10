/*
 * ObjectTypeLayoutVariantDecisionStep.kt
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

import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.methods.A_Definition
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.objectTypeVariant
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.nonnegativeInt32
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_EXTRACT_OBJECT_TYPE_VARIANT_ID
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_COMPARE_INT
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MULTIWAY_JUMP
import avail.interpreter.levelTwo.operation.L2_STRENGTHEN_TYPE
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.values.L2SemanticObjectVariantId
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.Strings.increaseIndentation
import avail.utility.Strings.newlineTab
import avail.utility.removeLast
import java.lang.String.format
import java.util.concurrent.ConcurrentHashMap

/**
 * This is a [DecisionStep] which dispatches to subtrees by looking up the
 * [ObjectLayoutVariant] of the argument, which has already been proven to be an
 * object type.
 *
 * The idea is to filter out most variants based on the fields that they
 * define, *and* the fields they specifically don't define.  If variant #1
 * has fields {x, y}, and variant #2 has fields {y, z}, then an object type with
 * variant #1 can't ever be a subtype of an object type using variant #2.
 *
 * Note that only the variants which have actually been dispatched have entries
 * in this step; new variants arriving to be dispatched will be dynamically
 * added to this step.
 *
 * @constructor
 * Construct the new instance.
 *
 * @property thisInternalLookupTree
 *   A reference to the [InternalLookupTree] in which this has been
 *   installed as the [DecisionStep].
 * @property argumentPositionToTest
 *   The 1-based index of the argument for which to test by the object type's
 *   [ObjectLayoutVariant].
 * @property signatureExtrasExtractor
 *   A function to extract the extra types from an element that are needed at
 *   this tree node.
 * @property variantToElements
 *   A [Map] grouping this step's [Element]s by their [ObjectLayoutVariant]
 *   at the indicated [argumentPositionToTest].
 */
class ObjectTypeLayoutVariantDecisionStep<
	Element : A_BasicObject,
	Result : A_BasicObject>
constructor(
	private val thisInternalLookupTree: InternalLookupTree<Element, Result>,
	argumentPositionToTest: Int,
	private val signatureExtrasExtractor:
		(Element) ->Pair<A_Type?, List<A_Type>>,
	private val variantToElements: Map<ObjectLayoutVariant, List<Element>>,
	private val alreadyVariantTestedArgumentsForChildren: A_Number
) : DecisionStep<Element, Result>(argumentPositionToTest)
{
	/**
	 * A [Map] from [ObjectLayoutVariant.variantId] to the child [LookupTree]
	 * that should be visited if the given [ObjectLayoutVariant] occurs during
	 * lookup.  If the provided variant is not present, it will be added
	 * dynamically.
	 */
	private val variantToSubtree: ConcurrentHashMap
			<ObjectLayoutVariant, LookupTree<Element, Result>> =
		ConcurrentHashMap()

	/**
	 * Given the actual [variant] that has been supplied for an actual
	 * lookup, collect the relevant [Element]s into a suitable [LookupTree].
	 */
	private fun elementsForVariant(
		variant: ObjectLayoutVariant
	): List<Element>
	{
		val entries = variantToElements.entries.filter {
			variant.isSubvariantOf(it.key)
		}
		return when (entries.size)
		{
			0 -> emptyList()
			1 -> entries.first().value
			else -> entries.flatMap { it.value }
		}
	}

	override fun <AdaptorMemento> lookupStepByValues(
		argValues: List<A_BasicObject>,
		extraValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argument = extractArgument(argValues, extraValues)
		return getSubtree(argument.objectTypeVariant, adaptor, memento)
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: List<A_Type>,
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argumentType = extractArgumentType(argTypes, extraValues)
		return getSubtree(
			argumentType.instance.objectTypeVariant, adaptor, memento)
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: A_Tuple,
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argumentType = extractArgumentType(argTypes, extraValues)
		return getSubtree(
			argumentType.instance.objectTypeVariant, adaptor, memento)
	}

	override fun <AdaptorMemento> lookupStepByValue(
		probeValue: A_BasicObject,
		extraValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		val argument = extractValue(probeValue, extraValues)
		return getSubtree(argument.objectTypeVariant, adaptor, memento)
	}

	/**
	 * Lookup or synthesize a subtree for the given variant.
	 *
	 * @param variant
	 *   The variant to look up, or under which to store a new subtree.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 */
	private fun <AdaptorMemento> getSubtree(
		variant: ObjectLayoutVariant,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento
	) = variantToSubtree.getOrPut(variant) {
		thisInternalLookupTree.run {
			val restrictions = knownArgumentRestrictions.toMutableList()
			restrictions[argumentPositionToTest - 1] =
				restrictions[argumentPositionToTest - 1]
					.intersectionWithObjectTypeVariant(variant)
			val newPositive = positiveElements.toMutableList()
			val newUndecided = mutableListOf<Element>()
			val bound = adaptor.extractBoundingType(restrictions)
			elementsForVariant(variant).forEach { element ->
				val signature = adaptor.restrictedSignature(
					element, signatureExtrasExtractor, bound)
				val comparison = adaptor.compareTypes(restrictions, signature)
				comparison.applyEffect(element, newPositive, newUndecided)
			}
			adaptor.createTree(
				newPositive,
				newUndecided,
				restrictions,
				alreadyTagTestedArguments,
				alreadyVariantTestedArgumentsForChildren,
				alreadyMetaInstanceExtractArguments,
				alreadyPhraseTypeExtractArguments,
				alreadyTestedConstants,
				alreadyEnumerationOfNontypeTested,
				alreadyExtractedFields,
				memento)
		}
	}

	override fun describe(
		node: InternalLookupTree<Element, Result>,
		indent: Int,
		builder: StringBuilder
	): Unit = with(builder)
	{
		val entries = variantToSubtree.entries.toList()
		append(
			increaseIndentation(
				format(
					"(u=%d, p=%d) #%d object type variants: known=%s",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					node.knownArgumentRestrictions),
				indent + 1))
		entries.sortedBy { it.key.variantId }.forEach { (variant, child) ->
			newlineTab(indent + 1)
			append("VAR#${variant.variantId}")
			variant.allFields
				.map { it.atomName.asNativeString() }
				.sorted()
				.joinTo(this, ", ", " (", "): ")
			append(child.toString(indent + 1))
		}
	}

	override fun simplyAddChildrenTo(
		list: MutableList<LookupTree<Element, Result>>)
	{
		list.addAll(variantToSubtree.values)
	}

	override fun addChildrenTo(
		list: MutableList<
			Pair<LookupTree<Element, Result>, List<L2SemanticValue>>>,
		semanticValues: List<L2SemanticValue>,
		extraSemanticValues: List<L2SemanticValue>)
	{
		variantToSubtree.values.forEach { subtree ->
			list.add(subtree to extraSemanticValues)
		}
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
		// For simplicity, let super-lookups via object type layout variant
		// always fall back.  They're *very* difficult to reason about.
		if (callSiteHelper.isSuper)
		{
			callSiteHelper.generator().jumpTo(
				callSiteHelper.onFallBackToSlowLookup)
			return emptyList()
		}

		// Create a multi-way branch using an object's variant.  Any variant
		// that wasn't present during generation will jump to the slower,
		// general lookup.  The slow lookup will populate the map with a new
		// subtree, which (TODO) should increase pressure to reoptimize the
		// calling method, specifically to include the new variant.
		val semanticSource =
			sourceSemanticValue(semanticArguments, extraSemanticArguments)
		val generator = callSiteHelper.generator()
		val currentRestriction =
			generator.currentManifest.restrictionFor(semanticSource)
		val restrictionType = currentRestriction.type.traversed()
		val restrictionVariant = restrictionType.instance.objectTypeVariant
		// Only keep relevant variants, and only if they lead to at least
		// one success.
		val applicableEntries = variantToSubtree.entries
			.filter { (key, subtree) ->
				key.isSubvariantOf(restrictionVariant)
					&& containsAnyValidLookup(subtree.castForGenerator())
			}
			.sortedBy { (key, _) -> key.variantId }
		if (applicableEntries.isEmpty())
		{
			// Just jump to the slow lookup, and don't continue down any
			// more lookup subtrees.
			generator.jumpTo(callSiteHelper.onFallBackToSlowLookup)
			return emptyList()
		}
		val semanticVariantId = L2SemanticUnboxedInt(
			L2SemanticObjectVariantId(semanticSource))

		when (val exactVariantId = currentRestriction
			.positiveGroup.objectTypeVariants?.single()?.variantId)
		{
			null -> generator.addInstruction(
				L2_EXTRACT_OBJECT_TYPE_VARIANT_ID,
				generator.readBoxed(semanticSource),
				generator.intWrite(
					setOf(semanticVariantId),
					restrictionForType(nonnegativeInt32, UNBOXED_INT_FLAG)))
			// The exact variant is known, which can make dispatching
			// particularly fast.
			else -> generator.addInstruction(
				L2_MOVE.unboxedInt,
				generator.unboxedIntConstant(exactVariantId),
				generator.intWrite(
					setOf(semanticVariantId),
					restrictionForConstant(
						fromInt(exactVariantId), UNBOXED_INT_FLAG)))
		}
		if (applicableEntries.size == 1)
		{
			// Check for the only variant that leads to a solution.
			val (variant, subtree) = applicableEntries[0]
			val variantId = variant.variantId
			val matchBlock = L2BasicBlock("matches variant #$variantId")
			val trulyUnreachable = L2BasicBlock("truly unreachable")
			L2_JUMP_IF_COMPARE_INT.equal.compareAndBranch(
				callSiteHelper.generator(),
				generator.readInt(semanticVariantId, trulyUnreachable),
				generator.unboxedIntConstant(variantId),
				edgeTo(matchBlock),
				edgeTo(callSiteHelper.onFallBackToSlowLookup))
			assert(trulyUnreachable.predecessorEdges().isEmpty())

			// We need to strengthen the restriction to correspond with the fact
			// that it now has this variant.
			generator.startBlock(matchBlock)
			val soleTarget = L2BasicBlock(
				"Guaranteed lookup for variant #$variantId")
			generator.addInstruction(
				L2_STRENGTHEN_TYPE,
				generator.readBoxed(semanticSource),
				generator.boxedWrite(
					semanticSource,
					currentRestriction.intersectionWithObjectTypeVariant(
						variant)))
			generator.jumpTo(soleTarget)
			return listOf(
				Triple(
					soleTarget,
					subtree.castForGenerator(),
					extraSemanticArguments))
		}
		// There are at least two variants that can lead to valid solutions,
		// so create a multi-way branch.
		val splits = mutableListOf<Int>()
		val targets = mutableListOf(callSiteHelper.onFallBackToSlowLookup)
		val edges = mutableListOf<
			Triple<
				L2BasicBlock,
				LookupTree<A_Definition, A_Tuple>,
				ObjectLayoutVariant>>()
		var lastSplit = 0
		// The multi-way branch has positive cases for each individual
		// possible variant (based on the known restrictions at this site),
		// and fall-through cases for the spans between them.
		applicableEntries.forEach { (variant, subtree) ->
			val variantId = variant.variantId
			if (variantId == lastSplit)
			{
				// Two adjacent variantIds occurred, so we save a split.
				assert(targets.last()
					== callSiteHelper.onFallBackToSlowLookup)
				targets.removeLast()
				splits.removeLast()
			}
			val target = L2BasicBlock("Variant = #$variantId")
			splits.add(variantId)
			targets.add(target)
			edges.add(Triple(target, subtree.castForGenerator(), variant))
			splits.add(variantId + 1)
			targets.add(callSiteHelper.onFallBackToSlowLookup)
			lastSplit = variantId + 1
		}
		assert(targets.size == splits.size + 1)
		assert(edges.size == applicableEntries.size)
		// Generate the multi-way branch.
		generator.addInstruction(
			L2_MULTIWAY_JUMP,
			generator.currentManifest.readInt(semanticVariantId),
			L2ConstantOperand(tupleFromIntegerList(splits)),
			L2PcVectorOperand(
				targets.mapIndexed { index, target ->
					val low = if (index == 0) "-∞" else splits[index - 1]
					val high = if (index == splits.size) "∞" else splits[index]
					L2PcOperand(
						target,
						false,
						null,
						"$low..$high")
				}))
		return edges.map { (block, subtree, variant) ->
			// We need to strengthen the restriction to correspond with the fact
			// that it now has this variant.
			val strengthenedTarget =
				L2BasicBlock("Strengthened variant #${variant.variantId}")
			generator.startBlock(block)
			generator.addInstruction(
				L2_STRENGTHEN_TYPE,
				generator.readBoxed(semanticSource),
				generator.boxedWrite(
					semanticSource,
					currentRestriction.intersectionWithObjectTypeVariant(
						variant)))
			generator.jumpTo(strengthenedTarget)
			Triple(strengthenedTarget, subtree, extraSemanticArguments)
		}
	}
}
