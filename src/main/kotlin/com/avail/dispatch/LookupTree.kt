/*
 * LookupTree.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_BasicObject
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.A_Type

/**
 * `LookupTree` is used to look up method definitions by argument types,
 * although it's more general than that.
 *
 * @param Element
 *   The kind of elements in the lookup tree, such as method definitions.
 * @param Result
 *   What we expect to produce from a lookup activity, such as the tuple of
 *   most-specific matching method definitions for some arguments.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
internal abstract class LookupTree<
	Element : A_BasicObject,
	Result : A_BasicObject>
{
	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided list of argument types.  Answer another [LookupTree] with
	 * which to continue the search.  Requires [solutionOrNull] to be `null`,
	 * indicating the search has not concluded.
	 *
	 * @param argTypes
	 *   The [list][List] of argument types being looked up.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next `LookupTree` to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByTypes(
		argTypes: List<A_Type>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided tuple of argument types.  Answer another [LookupTree] with
	 * which to continue the search.  Requires [solutionOrNull] to be `null`,
	 * indicating the search has not concluded.
	 *
	 * @param argTypes
	 *   The [tuple][A_Tuple] of argument types being looked up.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next `LookupTree` to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByTypes(
		argTypes: A_Tuple,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided list of arguments.  Answer another [LookupTree] with which
	 * to continue the search.  Requires [solutionOrNull] to be `null`,
	 * indicating the search has not concluded.
	 *
	 * @param argValues
	 *   The [List] of arguments being looked up.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next `LookupTree` to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByValues(
		argValues: List<A_BasicObject>,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided tuple of arguments.  Answer another [LookupTree] with which
	 * to continue the search.  Requires [solutionOrNull] to be null, indicating
	 * the search has not concluded.
	 *
	 * @param argValues
	 *   The [tuple][A_Tuple] of arguments being looked up.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return
	 *   The next `LookupTree` to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByValues(
		argValues: A_Tuple,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Perform one step of looking up the most-specific [Result] that matches
	 * the provided value.  Answer another `LookupTree` with which to continue
	 * the search.  Requires [solutionOrNull] to be null, indicating the search
	 * has not concluded.
	 *
	 * @param probeValue
	 *   The value being looked up.
	 * @param adaptor
	 *   The adaptor for interpreting the values in the tree, and deciding how
	 *   to narrow the elements that are still applicable at each internal node
	 *   of the tree.
	 * @param memento
	 *   A memento for the adaptor to use.
	 * @return The next `LookupTree` to search.
	 */
	abstract fun <AdaptorMemento> lookupStepByValue(
		probeValue: A_BasicObject,
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento): LookupTree<Element, Result>

	/**
	 * Answer the lookup solution ([List] of [definitions][A_Definition] at this
	 * leaf node, or `null` if this is not a leaf node.
	 *
	 * @return The solution or null.
	 */
	abstract val solutionOrNull: Result?

	/**
	 * Describe this `LookupTree` at a given indent level.
	 *
	 * @param indent
	 *   How much to indent.
	 * @return
	 *   The description of this `LookupTree`
	 */
	abstract fun toString(indent: Int): String

	override fun toString(): String = toString(0)
}
