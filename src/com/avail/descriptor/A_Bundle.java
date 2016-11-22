/**
 * A_Bundle.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import com.avail.compiler.splitter.MessageSplitter;

/**
 * {@code A_Bundle} is an interface that specifies the {@linkplain
 * MessageBundleDescriptor message-bundle}-specific operations that an {@link
 * AvailObject} must implement.  It's a sub-interface of {@link A_BasicObject},
 * the interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Bundle
extends A_BasicObject
{
	/**
	 * Add a {@link DefinitionParsingPlanDescriptor definition parsing plan} to
	 * this bundle, to bring it into agreement with the method's definitions and
	 * macro definitions.
	 *
	 * @param plan
	 *            The definition parsing plan to add.
	 */
	void addDefinitionParsingPlan (A_DefinitionParsingPlan plan);

	/**
	 * Remove information about this {@link A_Definition definition} from this
	 * bundle.
	 *
	 * @param definition The definition whose plan should be removed.
	 */
	void removePlanForDefinition (A_Definition definition);

	/**
	 * Add a {@linkplain GrammaticalRestrictionDescriptor grammatical
	 * restriction} to the receiver.
	 *
	 * @param grammaticalRestriction The grammatical restriction to be added.
	 */
	void addGrammaticalRestriction (
		A_GrammaticalRestriction grammaticalRestriction);

	/**
	 * Answer the {@linkplain MethodDescriptor method} that this bundle names.
	 * Multiple bundles may refer to the same method to support renaming of
	 * imported names.
	 *
	 * @return This bundle's method.
	 */
	A_Method bundleMethod ();

	/**
	 * Answer this bundle's {@link A_Map} from {@link A_Definition} to
	 * {@link A_DefinitionParsingPlan}.
	 *
	 * @return The map of definition parsing plans.
	 */
	A_Map definitionParsingPlans ();

	/**
	 * Answer the set of {@linkplain GrammaticalRestrictionDescriptor
	 * grammatical restrictions} that have been attached to this bundle.
	 *
	 * @return This bundle's grammatical restrictions.
	 */
	A_Set grammaticalRestrictions ();

	/**
	 * Answer whether this bundle has any {@linkplain
	 * GrammaticalRestrictionDescriptor grammatical restrictions}.
	 *
	 * @return Whether this bundle has grammatical restrictions.
	 */
	boolean hasGrammaticalRestrictions ();

	/**
	 * Answer the name of this bundle.  It must be parsable as a method name
	 * according to the rules of the {@link MessageSplitter}.
	 *
	 * @return An {@linkplain AtomDescriptor atom} naming this bundle.
	 */
	A_Atom message ();

	/**
	 * Answer the message parts produced by the {@link MessageSplitter} when
	 * applied to this bundle's name.  It's basically a {@linkplain
	 * TupleDescriptor tuple} of {@linkplain StringDescriptor strings} in the
	 * order the tokens appear in the bundle's name.
	 *
	 * @return A tuple of strings extracted from the bundle's message.
	 * @see #message()
	 */
	A_Tuple messageParts ();

	/**
	 * Answer the {@link MessageSplitter} holding parse planning information for
	 * invocations of this message bundle.
	 *
	 * @return The bundle's {@link MessageSplitter}.
	 */
	MessageSplitter messageSplitter ();

	/**
	 * Remove a {@linkplain GrammaticalRestrictionDescriptor grammatical
	 * restriction} from the receiver.
	 *
	 * @param obsoleteRestriction The grammatical restriction to remove.
	 */
	void removeGrammaticalRestriction (
		A_GrammaticalRestriction obsoleteRestriction);
}
