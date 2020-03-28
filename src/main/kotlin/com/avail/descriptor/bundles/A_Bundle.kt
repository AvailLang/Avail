/*
 * A_Bundle.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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
package com.avail.descriptor.bundles

import com.avail.compiler.splitter.MessageSplitter
import com.avail.descriptor.A_BasicObject
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_GrammaticalRestriction
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.parsing.DefinitionParsingPlanDescriptor
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tuples.A_Tuple

/**
 * `A_Bundle` is an interface that specifies the [ ]-specific operations that an [ ] must implement.  It's a sub-interface of [A_BasicObject],
 * the interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Bundle : A_BasicObject {
	/**
	 * Add a [definition parsing plan][DefinitionParsingPlanDescriptor] to
	 * this bundle, to bring it into agreement with the method's definitions and
	 * macro definitions.
	 *
	 * @param plan
	 * The definition parsing plan to add.
	 */
	fun addDefinitionParsingPlan(plan: A_DefinitionParsingPlan)

	/**
	 * Remove information about this [definition][A_Definition] from this
	 * bundle.
	 *
	 * @param definition The definition whose plan should be removed.
	 */
	fun removePlanForDefinition(definition: A_Definition)

	/**
	 * Add a [grammatical][GrammaticalRestrictionDescriptor] to the receiver.
	 *
	 * @param grammaticalRestriction The grammatical restriction to be added.
	 */
	fun addGrammaticalRestriction(
		grammaticalRestriction: A_GrammaticalRestriction)

	/**
	 * Answer the [method][MethodDescriptor] that this bundle names.
	 * Multiple bundles may refer to the same method to support renaming of
	 * imported names.
	 *
	 * @return This bundle's method.
	 */
	fun bundleMethod(): A_Method

	/**
	 * Answer this bundle's [A_Map] from [A_Definition] to
	 * [A_DefinitionParsingPlan].
	 *
	 * @return The map of definition parsing plans.
	 */
	fun definitionParsingPlans(): A_Map

	/**
	 * Answer the set of [ grammatical restrictions][GrammaticalRestrictionDescriptor] that have been attached to this bundle.
	 *
	 * @return This bundle's grammatical restrictions.
	 */
	fun grammaticalRestrictions(): A_Set

	/**
	 * Answer whether this bundle has any [ ].
	 *
	 * @return Whether this bundle has grammatical restrictions.
	 */
	fun hasGrammaticalRestrictions(): Boolean

	/**
	 * Answer the name of this bundle.  It must be parsable as a method name
	 * according to the rules of the [MessageSplitter].
	 *
	 * @return An [atom][AtomDescriptor] naming this bundle.
	 */
	fun message(): A_Atom

	/**
	 * Answer the message parts produced by the [MessageSplitter] when
	 * applied to this bundle's name.  It's basically a [ ] of [strings][StringDescriptor] in the
	 * order the tokens appear in the bundle's name.
	 *
	 * @return A tuple of strings extracted from the bundle's message.
	 * @see .message
	 */
	fun messageParts(): A_Tuple

	/**
	 * Answer the [MessageSplitter] holding parse planning information for
	 * invocations of this message bundle.
	 *
	 * @return The bundle's [MessageSplitter].
	 */
	fun messageSplitter(): MessageSplitter

	/**
	 * Remove a [grammatical][GrammaticalRestrictionDescriptor] from the receiver.
	 *
	 * @param obsoleteRestriction The grammatical restriction to remove.
	 */
	fun removeGrammaticalRestriction(
		obsoleteRestriction: A_GrammaticalRestriction)
}