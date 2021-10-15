/*
 * LookupTree.kt
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
import avail.descriptor.representation.A_BasicObject

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
abstract class LookupTree<
	Element : A_BasicObject,
	Result : A_BasicObject>
{
	/**
	 * If it has not already been computed, compute and cache the [DecisionStep]
	 * to use for making progress at this [InternalLookupTree].  Fail if this is
	 * not an [InternalLookupTree].
	 */
	abstract fun <AdaptorMemento> expandIfNecessary(
		adaptor: LookupTreeAdaptor<Element, Result, AdaptorMemento>,
		memento: AdaptorMemento
	): DecisionStep<Element, Result>

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
