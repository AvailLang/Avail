/**
 * UnresolvedDependencyException.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import com.avail.descriptor.ModuleDescriptor;

/**
 * A {@code UnresolvedDependencyException} is thrown by the {@linkplain
 * AvailBuilder builder} when an unresolved reference to a {@linkplain
 * ModuleDescriptor module} is discovered.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class UnresolvedDependencyException
extends Exception
{
	/** The serial version identifier. */
	private static final long serialVersionUID = 3805218550541316595L;

	/**
	 * The module that contained an unresolved reference to another module.
	 */
	private final ResolvedModuleName referringModuleName;

	/**
	 * The name of the module that could not be resolved.
	 */
	private final String unresolvedModuleName;

	/**
	 * Answer the name of the {@linkplain ModuleDescriptor module} that could
	 * not be resolved.
	 *
	 * @return A {@linkplain String} naming the unresolved local module.
	 */
	public ResolvedModuleName referringModuleName()
	{
		return referringModuleName;
	}

	/**
	 * Answer the name of the {@linkplain ModuleDescriptor module} that could
	 * not be resolved.
	 *
	 * @return A {@linkplain String} naming the unresolved local module.
	 */
	public String unresolvedModuleName()
	{
		return unresolvedModuleName;
	}

	/**
	 * Construct a new {@link UnresolvedDependencyException}.
	 *
	 * @param referringModuleName
	 *            The name of the module which contained the invalid module
	 *            reference.
	 * @param unresolvedModuleName
	 *            The name of the module which could not be resolved.
	 */
	UnresolvedDependencyException (
		final ResolvedModuleName referringModuleName,
		final String unresolvedModuleName)
	{
		super(
			"module \""
			+ referringModuleName.qualifiedName()
			+ "\" refers to unresolved module \""
			+ unresolvedModuleName
			+ "\".");
		this.referringModuleName = referringModuleName;
		this.unresolvedModuleName = unresolvedModuleName;
	}
}
