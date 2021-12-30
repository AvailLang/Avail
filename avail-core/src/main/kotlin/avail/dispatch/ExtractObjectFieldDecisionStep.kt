/*
 * ExtractObjectFieldDecisionStep.kt
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
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.methods.A_Definition
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.types.A_Type
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operation.L2_GET_OBJECT_FIELD
import avail.interpreter.primitive.objects.P_GetObjectField
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.values.L2SemanticValue
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.Strings.increaseIndentation
import avail.utility.Strings.newlineTab
import avail.utility.cast
import java.lang.String.format

/**
 * This is a [DecisionStep] which extracts one field from an argument or extra,
 * and adds it to the list of extras, for subsequent dispatching.  This new
 * value should have a useful subsequent dispatching power, otherwise there
 * would be no point in extracting it.
 *
 * The [InternalLookupTree] containing this step will have exactly one child.
 *
 * @constructor
 * Construct the new instance.
 *
 * @property argumentPositionToTest
 *   The 1-based index of the argument from which to extract a field.
 * @property field
 *   The [name][A_Atom] of the field to traverse.  Note that the [fieldIndex]
 *   is actually used for the traversal, for performance.
 * @property fieldIndex
 *   The one-based index of the field to extract from the object.  It's safe to
 *   use the numeric index, since the exact variant has already been determined.
 * @property childNode
 *   The sole child of the node using this step.
 */
class ExtractObjectFieldDecisionStep<
	Element : A_BasicObject,
	Result : A_BasicObject>
constructor(
	argumentPositionToTest: Int,
	private val field: A_Atom,
	private val fieldIndex: Int,
	val childNode: InternalLookupTree<Element, Result>
) : DecisionStep<Element, Result>(argumentPositionToTest)
{
	override fun updateExtraValuesByValues(
		argValues: List<A_BasicObject>,
		extraValues: List<Element>
	): List<Element>
	{
		val i = argumentPositionToTest
		val baseValue = when
		{
			i > 0 -> argValues[i - 1]
			else -> extraValues[-i]
		}
		val newValue = (baseValue as AvailObject).fieldAtIndex(fieldIndex)
		return extraValues.append(newValue.cast())
	}

	override fun updateExtraValuesByTypes(
		types: List<A_Type>,
		extraValues: List<Element>
	): List<Element>
	{
		val i = argumentPositionToTest
		val baseValue = when
		{
			i > 0 -> types[i - 1]
			else -> extraValues[-i]
		}
		val newValue = (baseValue as AvailObject).fieldAtIndex(fieldIndex)
		return extraValues.append(newValue.cast())
	}

	override fun updateExtraValuesByTypes(
		argTypes: A_Tuple,
		extraValues: List<Element>
	): List<Element>
	{
		val i = argumentPositionToTest
		val baseValue = when
		{
			i > 0 -> argTypes.tupleAt(i)
			else -> extraValues[-i]
		}
		val newValue = (baseValue as AvailObject).fieldAtIndex(fieldIndex)
		return extraValues.append(newValue.cast())
	}

	override fun updateExtraValuesByValue(
		probeValue: A_BasicObject,
		extraValues: List<Element>
	): List<Element>
	{
		val baseValue = when (val i = argumentPositionToTest)
		{
			0 -> probeValue
			else -> extraValues[-i]
		}
		val newValue = (baseValue as AvailObject).fieldAtIndex(fieldIndex)
		return extraValues.append(newValue.cast())
	}

	override fun <AdaptorMemento> lookupStepByValues(
		argValues: List<A_BasicObject>,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		return childNode
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: List<A_Type>,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		return childNode
	}

	override fun <AdaptorMemento> lookupStepByTypes(
		argTypes: A_Tuple,
		extraValues: List<Element>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>
	{
		return childNode
	}

	override fun <AdaptorMemento> lookupStepByValue(
		probeValue: A_BasicObject,
		extraValues: List<Element>,
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
					"(u=%d, p=%d) #%d extract field (%s) : known=%s",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					field.atomName.asNativeString(),
					node.knownArgumentRestrictions),
				indent + 1))
		newlineTab(indent + 1)
		append(childNode.toString(indent + 1))
	}

	private fun newSemanticValue(
		semanticValues: List<L2SemanticValue>,
		extraSemanticValues: List<L2SemanticValue>
	) = L2SemanticValue.primitiveInvocation(
		P_GetObjectField,
		listOf(
			sourceSemanticValue(semanticValues, extraSemanticValues),
			L2SemanticValue.constant(field)))

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
		val fieldSemanticValue =
			newSemanticValue(semanticArguments, extraSemanticArguments)
		val baseRestriction =
			generator.currentManifest.restrictionFor(baseSemanticValue)
		val fieldRestriction = restrictionForType(
			baseRestriction.type.fieldTypeAt(field), BOXED_FLAG)
		generator.addInstruction(
			L2_GET_OBJECT_FIELD,
			generator.readBoxed(baseSemanticValue),
			L2ConstantOperand(field),
			generator.boxedWrite(fieldSemanticValue, fieldRestriction))
		val target = L2BasicBlock("after extracting field")
		generator.jumpTo(target)
		return listOf(
			Triple(
				target, childNode.castForGenerator(), extraSemanticArguments))
	}
}
