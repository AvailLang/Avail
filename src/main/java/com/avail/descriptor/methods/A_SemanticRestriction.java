/*
 * A_SemanticRestriction.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.functions.A_Continuation;
import com.avail.descriptor.functions.A_Function;

/**
 * {@code A_SemanticRestriction} is an interface that specifies the operations
 * suitable for a {@linkplain SemanticRestrictionDescriptor semantic
 * restriction}.  It's a sub-interface of {@link A_BasicObject}, the interface
 * that defines the behavior that all AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_SemanticRestriction
extends A_BasicObject
{
	/**
	 * Answer the function to execute to determine the effect of this semantic
	 * restriction on a list of argument static types at a call site.
	 *
	 * <p>Also defined in {@link A_Continuation}.</p>
	 *
	 * @return The function.
	 */
	A_Function function ();

	/**
	 * Answer the {@linkplain MethodDescriptor method} for which this semantic
	 * restriction applies.
	 *
	 * <p>Also defined in {@link A_Definition} and {@link
	 * A_GrammaticalRestriction}.</p>
	 *
	 * @return The method to which this semantic restriction applies.
	 */
	A_Method definitionMethod ();

	/**
	 * Answer the {@linkplain ModuleDescriptor module} in which this semantic
	 * restriction was defined.
	 *
	 * <p>Also defined in {@link A_Definition}.</p>
	 *
	 * @return The method to which this semantic restriction applies.
	 */
	A_Module definitionModule ();
}
