/*
 * DecisionStep.kt
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

package com.avail.dispatch

import com.avail.descriptor.objects.ObjectLayoutVariant
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.TypeTag
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import java.lang.String.format

/**
 * This abstraction represents a mechanism for achieving a quantum of
 * progress toward looking up which method definition to invoke, or some
 * similar usage.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
sealed class DecisionStep<Element : A_BasicObject, Result : A_BasicObject>
{
	/**
	 * Given an optional array of values used to supplement the lookup, answer
	 * the updated optional array of values that takes this step into account.
	 * The given and resulting arrays may be modified or replaced, at the
	 * discretion of the step.
	 *
	 * By default, simply return the input.
	 */
	fun updateExtraValues(
		extraValues: Array<Element?>?
	): Array<Element?>? = extraValues

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided list of arguments.  Answer another [LookupTree] with which
	 * to continue the search.
	 *
	 * @param argValues
	 *   The [List] of arguments being looked up.
	 * @param extraValues
	 *   An optional mutable array of additional values, only created when
	 *   needed.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next [LookupTree] to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByValues(
		argValues: List<A_BasicObject>,
		extraValues: Array<Element?>?,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided tuple of arguments.  Answer another [LookupTree] with which
	 * to continue the search.
	 *
	 * @param argValues
	 *   The [tuple][A_Tuple] of arguments being looked up.
	 * @param extraValues
	 *   An optional mutable array of additional values, only created when
	 *   needed.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next [LookupTree] to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByValues(
		argValues: A_Tuple,
		extraValues: Array<Element?>?,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided list of argument types.  Answer another [LookupTree] with
	 * which to continue the search.
	 *
	 * @param argTypes
	 *   The [list][List] of argument types being looked up.
	 * @param extraValues
	 *   An optional mutable array of additional values, only created when
	 *   needed.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next [LookupTree] to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByTypes(
		argTypes: List<A_Type>,
		extraValues: Array<Element?>?,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided tuple of argument types.  Answer another [LookupTree] with
	 * which to continue the search.
	 *
	 * @param argTypes
	 *   The [tuple][A_Tuple] of argument types being looked up.
	 * @param extraValues
	 *   An optional mutable array of additional values, only created when
	 *   needed.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next [LookupTree] to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByTypes(
		argTypes: A_Tuple,
		extraValues: Array<Element?>?,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided value.  Answer another [LookupTree] with which to continue
	 * the search.
	 *
	 * @param probeValue
	 *   The value being looked up.
	 * @param extraValues
	 *   An optional mutable array of additional values, only created when
	 *   needed.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return The next [LookupTree] to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByValue(
		probeValue: A_BasicObject,
		extraValues: Array<Element?>?,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Output a description of this step on the given [builder].  Do not expand
	 * any subtrees that are still lazy.
	 */
	abstract fun describe(
		node : InternalLookupTree<Element, Result>,
		indent: Int,
		builder: StringBuilder)

	/**
	 * Visit this step with the given [LookupTreeTraverser].
	 */
	abstract fun <AdaptorMemento, TraversalMemento> visitStep(
		traverser : LookupTreeTraverser<
			Element, Result, AdaptorMemento, TraversalMemento>)

	/**
	 * This is a [DecisionStep] which tests a particular argument position
	 * against some constant type.
	 *
	 * @constructor
	 * Construct the new instance.
	 *
	 * @property argumentTypeToTest
	 *   The type to test against an argument type at this node.
	 * @property argumentPositionToTest
	 *   The 1-based index of the argument to be tested at this node.
	 * @property ifCheckHolds
	 *   The tree to visit if the supplied arguments conform.
	 * @property ifCheckFails
	 *   The tree to visit if the supplied arguments do not conform.
	 */
	class TestArgumentDecisionStep<
		Element : A_BasicObject,
		Result : A_BasicObject>
	constructor(
		val argumentTypeToTest: A_Type,
		val argumentPositionToTest: Int,
		val ifCheckHolds: LookupTree<Element, Result>,
		val ifCheckFails: LookupTree<Element, Result>
	) : DecisionStep<Element, Result>()
	{
		override fun <AdaptorMemento> lookupStepByValues(
			argValues: List<A_BasicObject>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues[index - 1]
			return if (argument.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByValues(
			argValues: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues.tupleAt(index)
			return if (argument.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: List<A_Type>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes[index - 1]
			return if (argumentType.isSubtypeOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes.tupleAt(index)
			return if (argumentType.isSubtypeOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByValue(
			probeValue: A_BasicObject,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index == 0)
			return if (probeValue.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun describe(
			node : InternalLookupTree<Element, Result>,
			indent: Int,
			builder: StringBuilder
		): Unit = builder.run {
			append(
				format(
					"(u=%d, p=%d) #%d ∈ %s: known=%s%n",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					argumentTypeToTest,
					node.knownArgumentRestrictions))
			for (i in 0..indent)
			{
				append("\t")
			}
			append(ifCheckHolds.toString(indent + 1))
			append(format("%n"))
			for (i in 0..indent)
			{
				append("\t")
			}
			append(ifCheckFails.toString(indent + 1))
		}

		override fun <AdaptorMemento, TraversalMemento> visitStep(
			traverser : LookupTreeTraverser<
				Element, Result, AdaptorMemento, TraversalMemento>)
		{
			// Create a memento to share between the below actions operating
			// at the same position in the tree.  Push them in *reverse*
			// order of their execution, which is why the *last* action is where
			// the memento actually gets created.
			var memento: TraversalMemento? = null
			traverser.actionStack.addAll(
				arrayOf(
					{ traverser.visitPostInternalNode(memento!!) },
					{ traverser.visit(ifCheckFails) },
					{ traverser.visitIntraInternalNode(memento!!) },
					{ traverser.visit(ifCheckHolds) },
					{
						memento = traverser.visitPreInternalNode(
							argumentPositionToTest,
							argumentTypeToTest)
					}))
		}
	}

	/**
	 * This is a [DecisionStep] which extracts a value or type that's a part of
	 * another value or type, and can be used to filter more quickly than the
	 * original.  For example, a phrase type's yield type depends covariantly on
	 * the phrase type, but sometimes the yield type can be efficiently
	 * dispatched by a subsequent [TypeTagDecisionStep], which would be
	 * inaccessible if we didn't extract the yield type first.
	 *
	 * Note that if the value to be tested at the current tree node isn't all of
	 * one [TypeTag], this type of step will not yet be available, and a
	 * [TypeTagDecisionStep] can be used first.
	 *
	 * @constructor
	 * Construct the new instance.
	 *
	 * @property argumentTypeToTest
	 *   The type to test against an argument type at this node.
	 * @property argumentPositionToTest
	 *   The 1-based index of the argument to be tested at this node.
	 * @property ifCheckHolds
	 *   The tree to visit if the supplied arguments conform.
	 * @property ifCheckFails
	 *   The tree to visit if the supplied arguments do not conform.
	 */
	class ExtractVariantDecisionStep<
		Element : A_BasicObject,
		Result : A_BasicObject>
	constructor(
		val argumentTypeToTest: A_Type,
		val argumentPositionToTest: Int,
		val ifCheckHolds: LookupTree<Element, Result>,
		val ifCheckFails: LookupTree<Element, Result>
	) : DecisionStep<Element, Result>()
	{
		override fun <AdaptorMemento> lookupStepByValues(
			argValues: List<A_BasicObject>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues[index - 1]
			return if (argument.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByValues(
			argValues: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues.tupleAt(index)
			return if (argument.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: List<A_Type>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes[index - 1]
			return if (argumentType.isSubtypeOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes.tupleAt(index)
			return if (argumentType.isSubtypeOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByValue(
			probeValue: A_BasicObject,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index == 0)
			return if (probeValue.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun describe(
			node : InternalLookupTree<Element, Result>,
			indent: Int,
			builder: StringBuilder
		): Unit = builder.run {
			append(
				format(
					"(u=%d, p=%d) #%d ∈ %s: known=%s%n",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					argumentTypeToTest,
					node.knownArgumentRestrictions))
			for (i in 0..indent)
			{
				append("\t")
			}
			append(ifCheckHolds.toString(indent + 1))
			append(format("%n"))
			for (i in 0..indent)
			{
				append("\t")
			}
			append(ifCheckFails.toString(indent + 1))
		}

		override fun <AdaptorMemento, TraversalMemento> visitStep(
			traverser : LookupTreeTraverser<
				Element, Result, AdaptorMemento, TraversalMemento>)
		{
			// Create a memento to share between the below actions operating
			// at the same position in the tree.  Push them in *reverse*
			// order of their execution, which is why the *last* action is where
			// the memento actually gets created.
			var memento: TraversalMemento? = null
			traverser.actionStack.addAll(
				arrayOf(
					{ traverser.visitPostInternalNode(memento!!) },
					{ traverser.visit(ifCheckFails) },
					{ traverser.visitIntraInternalNode(memento!!) },
					{ traverser.visit(ifCheckHolds) },
					{
						memento = traverser.visitPreInternalNode(
							argumentPositionToTest,
							argumentTypeToTest)
					}))
		}
	}

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
	 * @property argumentTypeToTest
	 *   The type to test against an argument type at this node.
	 * @property argumentPositionToTest
	 *   The 1-based index of the argument to be tested at this node.
	 * @property ifCheckHolds
	 *   The tree to visit if the supplied arguments conform.
	 * @property ifCheckFails
	 *   The tree to visit if the supplied arguments do not conform.
	 */
	class TypeTagDecisionStep<
		Element : A_BasicObject,
		Result : A_BasicObject>
	constructor(
		val argumentTypeToTest: A_Type,
		val argumentPositionToTest: Int,
		val ifCheckHolds: LookupTree<Element, Result>,
		val ifCheckFails: LookupTree<Element, Result>
	) : DecisionStep<Element, Result>()
	{
		override fun <AdaptorMemento> lookupStepByValues(
			argValues: List<A_BasicObject>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues[index - 1]
			return if (argument.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByValues(
			argValues: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argument = argValues.tupleAt(index)
			return if (argument.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: List<A_Type>,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes[index - 1]
			return if (argumentType.isSubtypeOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByTypes(
			argTypes: A_Tuple,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index > 0)
			val argumentType = argTypes.tupleAt(index)
			return if (argumentType.isSubtypeOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun <AdaptorMemento> lookupStepByValue(
			probeValue: A_BasicObject,
			extraValues: Array<Element?>?,
			adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
			memento: AdaptorMemento): LookupTree<Element, Result>
		{
			val index = argumentPositionToTest
			assert(index == 0)
			return if (probeValue.isInstanceOf(argumentTypeToTest))
			{
				ifCheckHolds
			}
			else ifCheckFails
		}

		override fun describe(
			node : InternalLookupTree<Element, Result>,
			indent: Int,
			builder: StringBuilder
		): Unit = builder.run {
			append(
				format(
					"(u=%d, p=%d) #%d ∈ %s: known=%s%n",
					node.undecidedElements.size,
					node.positiveElements.size,
					argumentPositionToTest,
					argumentTypeToTest,
					node.knownArgumentRestrictions))
			for (i in 0..indent)
			{
				append("\t")
			}
			append(ifCheckHolds.toString(indent + 1))
			append(format("%n"))
			for (i in 0..indent)
			{
				append("\t")
			}
			append(ifCheckFails.toString(indent + 1))
		}

		override fun <AdaptorMemento, TraversalMemento> visitStep(
			traverser : LookupTreeTraverser<
				Element, Result, AdaptorMemento, TraversalMemento>)
		{
			// Create a memento to share between the below actions operating
			// at the same position in the tree.  Push them in *reverse*
			// order of their execution, which is why the *last* action is where
			// the memento actually gets created.
			var memento: TraversalMemento? = null
			traverser.actionStack.addAll(
				arrayOf(
					{ traverser.visitPostInternalNode(memento!!) },
					{ traverser.visit(ifCheckFails) },
					{ traverser.visitIntraInternalNode(memento!!) },
					{ traverser.visit(ifCheckHolds) },
					{
						memento = traverser.visitPreInternalNode(
							argumentPositionToTest,
							argumentTypeToTest)
					}))
		}
	}
}
