/*
 * ExtractPhraseTypeDecisionStep.kt
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
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operation.L2_GET_PHRASE_EXPRESSION_TYPE
import avail.interpreter.primitive.phrases.P_PhraseExpressionType
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.values.L2SemanticValue
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.Strings.increaseIndentation
import avail.utility.Strings.newlineTab
import avail.utility.cast
import java.lang.String.format

/**
 * This is a [DecisionStep] which takes a phrase from an argument or extra, and
 * adds it to the list of extras, for subsequent dispatching.  This new value
 * should have a useful subsequent dispatching power, otherwise there would be
 * no point in extracting it.
 *
 * The [InternalLookupTree] containing this step will have exactly one child.
 *
 * @constructor
 * Construct the new instance.
 *
 * @param argumentPositionToTest
 *   The 1-based index of the argument (or negative index for an extra) from
 *   which to extract a field.
 * @property childNode
 *   The sole child of the node using this step.
 */
class ExtractPhraseTypeDecisionStep<
	Element : A_BasicObject,
	Result : A_BasicObject>
constructor(
	argumentPositionToTest: Int,
	val childNode: InternalLookupTree<Element, Result>
) : DecisionStep<Element, Result>(argumentPositionToTest)
{
	override fun updateExtraValuesByValues(
		argValues: List<A_BasicObject>,
		extraValues: List<Element>
	): List<Element>
	{
		val i = argumentPositionToTest
		val inExtras = i - argValues.size
		val baseValue = when
		{
			inExtras > 0 -> extraValues[inExtras - 1]
			else -> argValues[i - 1]
		}
		val newValue = (baseValue as A_Phrase).phraseExpressionType
		return extraValues.append(newValue.cast())
	}

	override fun updateExtraValuesByTypes(
		types: List<A_Type>,
		extraValues: List<A_Type>
	): List<A_Type>
	{
		val i = argumentPositionToTest
		val inExtras = i - types.size
		val baseType = when
		{
			inExtras > 0 -> extraValues[inExtras - 1]
			else -> types[i - 1]
		}
		val newValue = baseType.phraseTypeExpressionType
		return extraValues.append(newValue.cast())
	}

	override fun updateExtraValuesByTypes(
		argTypes: A_Tuple,
		extraValues: List<A_Type>
	): List<A_Type>
	{
		val i = argumentPositionToTest
		val inExtras = i - argTypes.tupleSize
		val baseType = when
		{
			inExtras > 0 -> extraValues[inExtras - 1]
			else -> argTypes.tupleAt(i)
		}
		val newValue = baseType.phraseTypeExpressionType
		return extraValues.append(newValue.cast())
	}

	override fun updateExtraValuesByValue(
		probeValue: A_BasicObject,
		extraValues: List<Element>
	): List<Element>
	{
		val i = argumentPositionToTest
		val inExtras = i - 1
		val baseValue = when
		{
			inExtras > 0 -> extraValues[inExtras - 1]
			else -> probeValue
		}
		val newValue = (baseValue as A_Phrase).phraseExpressionType
		return extraValues.append(newValue.cast())
	}

	override fun <Memento> updateSignatureExtrasExtractor(
		adaptor: LookupTreeAdaptor<Element, Result, Memento>,
		extrasTypeExtractor: (Element)->Pair<A_Type?, List<A_Type>>,
		numArgs: Int
	): (Element)->Pair<A_Type?, List<A_Type>>
	{
		val inExtras = argumentPositionToTest - numArgs
		return when
		{
			inExtras > 0 -> { element ->
				val (optionalBaseType, extrasList) =
					extrasTypeExtractor(element)
				val phraseType = extrasList[inExtras - 1]
				val expressionType =
					instanceMeta(phraseType.phraseTypeExpressionType)
				optionalBaseType to extrasList.append(expressionType)
			}
			else -> { element ->
				val (optionalBaseType, extrasList) =
					extrasTypeExtractor(element)
				val baseType =
					optionalBaseType ?: adaptor.extractSignature(element)
				val phraseType = baseType.typeAtIndex(argumentPositionToTest)
				val expressionType =
					instanceMeta(phraseType.phraseTypeExpressionType)
				baseType to extrasList.append(expressionType)
			}
		}
	}

	override fun <AdaptorMemento> lookupStepByValues(
		argValues: List<A_BasicObject>,
		extraValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		return childNode
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: List<A_Type>,
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		return childNode
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: A_Tuple,
		extraValues: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		return childNode
	}

	override fun <AdaptorMemento> lookupStepByValue(
		probeValue: A_BasicObject,
		extraValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		return childNode
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
					"(u=%d, p=%d) #%d extract phrase yield type: known=%s",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					node.knownArgumentRestrictions),
				indent + 1))
		newlineTab(indent + 1)
		append(childNode.toString(indent + 1))
	}

	private fun newSemanticValue(
		semanticValues: List<L2SemanticValue>,
		extraSemanticValues: List<L2SemanticValue>
	) = L2SemanticValue.primitiveInvocation(
		P_PhraseExpressionType,
		listOf(
			sourceSemanticValue(semanticValues, extraSemanticValues)))

	override fun simplyAddChildrenTo(
		list: MutableList<LookupTree<Element, Result>>)
	{
		list.add(childNode)
	}

	override fun addChildrenTo(
		list: MutableList<
			Pair<LookupTree<Element, Result>, List<L2SemanticValue>>>,
		semanticValues: List<L2SemanticValue>,
		extraSemanticValues: List<L2SemanticValue>)
	{
		val newSemanticValue =
			newSemanticValue(semanticValues, extraSemanticValues)
		list.add(childNode to extraSemanticValues.append(newSemanticValue))
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
		val generator = callSiteHelper.generator()
		val baseSemanticValue =
			sourceSemanticValue(semanticArguments, extraSemanticArguments)
		val baseRestriction =
			generator.currentManifest.restrictionFor(baseSemanticValue)
		val expressionTypeRestriction = restrictionForType(
			instanceMeta(baseRestriction.type.phraseTypeExpressionType),
			BOXED_FLAG)
		val expressionTypeSemanticValue = L2SemanticValue.primitiveInvocation(
			P_PhraseExpressionType,
			listOf(baseSemanticValue))
		generator.addInstruction(
			L2_GET_PHRASE_EXPRESSION_TYPE,
			generator.readBoxed(baseSemanticValue),
			generator.boxedWrite(
				expressionTypeSemanticValue, expressionTypeRestriction))
		val target = L2BasicBlock("after extracting field")
		generator.jumpTo(target)
		return listOf(
			Triple(
				target,
				childNode.castForGenerator(),
				extraSemanticArguments + expressionTypeSemanticValue))
	}
}
