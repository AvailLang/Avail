/**
 * UnresolvedModuleException.java
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

package com.avail.builder;

import java.util.ArrayList;
import com.avail.annotations.Nullable;

/**
 * UnresolvedModuleException is a type of UnresolvedDependencyException that is
 * specifically for the case that the compiler could not find a module it
 * needed in order to resolve its dependency graph. It contains the list of
 * locations checked by the compiler for that module, which is all of the
 * acceptable locations for the missing module according to its dependent's
 * location.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public class UnresolvedModuleException
extends UnresolvedDependencyException
{

	/** The serial version identifier. */
	private static final long serialVersionUID = 5538552037288443414L;

	/**
	 * The list of the places the unresolved module could have been.
	 */
	private final ArrayList<ModuleName> acceptablePaths;

	/**
	 * Answer the list of the places the unresolved module could have been.
	 *
	 * @return A {@linkplain ArrayList} list of the {@linkplain ModuleName
	 *         module names}
	 */
	public ArrayList<ModuleName> acceptablePaths()
	{
		return acceptablePaths;
	}

	/**
	 * Construct a new {@link UnresolvedModuleException}.
	 *
	 * @param referringModuleName The name of the module which contained the invalid module
	 *            reference.
	 * @param unresolvedModuleName The name of the module which could not be resolved.
	 * @param acceptablePaths The list of places the module could have been.
	 */
	UnresolvedModuleException (
		final @Nullable ResolvedModuleName referringModuleName,
		final String unresolvedModuleName,
		final ArrayList<ModuleName> acceptablePaths)
	{
		super(referringModuleName, unresolvedModuleName);
		this.acceptablePaths = acceptablePaths;
	}

}
