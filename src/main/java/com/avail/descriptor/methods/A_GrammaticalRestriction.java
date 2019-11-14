/*
 * A_GrammaticalRestriction.java
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

package com.avail.descriptor.methods;

import com.avail.descriptor.*;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.bundles.MessageBundleDescriptor;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.descriptor.tuples.A_Tuple;

/**
 * {@code A_GrammaticalRestriction} is an interface that specifies the
 * operations suitable for a {@linkplain GrammaticalRestrictionDescriptor
 * grammatical restriction}.  It's a sub-interface of {@link A_BasicObject}, the
 * interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_GrammaticalRestriction
extends A_BasicObject
{
	/**
	 * Answer the {@linkplain TupleDescriptor tuple} of {@linkplain
	 * SetDescriptor sets} of {@linkplain MessageBundleDescriptor message
	 * bundles} that are forbidden from occurring in the corresponding
	 * underscore positions of this {@linkplain GrammaticalRestrictionDescriptor
	 * grammatical restriction}'s message bundle.
	 *
	 * @return A tuple of sets of message bundles.
	 */
	A_Tuple argumentRestrictionSets ();

	/**
	 * Answer the {@linkplain MessageBundleDescriptor message bundle} that is
	 * restricted by this grammatical restriction.
	 *
	 * @return The message bundle for which this grammatical restriction
	 *         applies.
	 */
	A_Bundle restrictedBundle ();

	/**
	 * Answer the {@linkplain ModuleDescriptor module} in which this grammatical
	 * restriction was defined.
	 *
	 * <p>Also defined in {@link A_Definition} and {@link
	 * A_SemanticRestriction}.</p>
	 *
	 * @return The module to which this grammatical restriction applies.
	 */
	A_Module definitionModule ();
}
