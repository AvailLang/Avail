/**
 * A_Bundle.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import com.avail.compiler.MessageSplitter;
import com.avail.compiler.ParsingOperation;

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
	 * Answer the {@linkplain MethodDescriptor method} that this bundle names.
	 * Multiple bundles may refer to the same method to support renaming of
	 * imported names.
	 *
	 * @return This bundle's method.
	 */
	A_Method bundleMethod ();

	/**
	 * Answer the set of {@linkplain GrammaticalRestrictionDescriptor
	 * grammatical restrictions} that have been attached to this bundle.
	 *
	 * @return This bundle's grammatical restrictions.
	 */
	A_Set grammaticalRestrictions ();

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
	 * Answer a {@linkplain TupleDescriptor tuple} of {@linkplain
	 * IntegerDescriptor integers} encoding the {@linkplain ParsingOperation
	 * parsing instructions} required to parse a call to this bundle's method
	 * using this bundle's name (message).
	 *
	 * <p>
	 * Matching parsing instructions for multiple messages can (usually) be
	 * executed in aggregate, avoiding the separate cost of attempting to parse
	 * each possible message at each place where a call may occur.
	 * </p>
	 *
	 * @return A tuple of integers encoding this bundle's parsing instructions.
	 */
	A_Tuple parsingInstructions ();

	/**
	 * Add a {@linkplain GrammaticalRestrictionDescriptor grammatical
	 * restriction} to the receiver.
	 *
	 * @param grammaticalRestriction The grammatical restriction to be added.
	 */
	void addGrammaticalRestriction (
		A_GrammaticalRestriction grammaticalRestriction);

	/**
	 * Remove a {@linkplain GrammaticalRestrictionDescriptor grammatical
	 * restriction} from the receiver.
	 *
	 * @param obsoleteRestriction The grammatical restriction to remove.
	 */
	void removeGrammaticalRestriction (
		A_GrammaticalRestriction obsoleteRestriction);

	/**
	 * Answer whether this bundle has any {@linkplain
	 * GrammaticalRestrictionDescriptor grammatical restrictions}.
	 *
	 * @return Whether this bundle has grammatical restrictions.
	 */
	boolean hasGrammaticalRestrictions ();
}
