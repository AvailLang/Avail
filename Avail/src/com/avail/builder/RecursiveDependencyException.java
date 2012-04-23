/**
 * RecursiveDependencyException.java
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

import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.ModuleDescriptor;

/**
 * A {@code RecursiveDependencyException} is thrown by the {@linkplain
 * AvailBuilder builder} when a recursive {@linkplain ModuleDescriptor module}
 * dependency is discovered.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class RecursiveDependencyException
extends Exception
{
	/** The serial version identifier. */
	private static final long serialVersionUID = 6654397420941119005L;

	/**
	 * The path that the {@linkplain AvailBuilder builder} followed to encounter
	 * the dependency recursion.
	 */
	private final @NotNull List<ResolvedModuleName> recursionPath =
		new ArrayList<ResolvedModuleName>(10);

	/**
	 * Answer the {@linkplain ResolvedModuleName resolved module name} of the
	 * {@linkplain ModuleDescriptor module} that recursively depends upon
	 * itself.
	 *
	 * @return A {@linkplain ResolvedModuleName module name}.
	 */
	public @NotNull ResolvedModuleName recursiveDependent ()
	{
		return recursionPath.get(recursionPath.size() - 1);
	}

	/**
	 * Answer the path that the {@linkplain AvailBuilder builder} followed to
	 * encounter the dependency recursion.
	 *
	 * @return The path that the {@linkplain AvailBuilder builder} followed to
	 *         reach the dependency recursion.
	 */
	public @NotNull List<ResolvedModuleName> recursionPath ()
	{
		return Collections.unmodifiableList(recursionPath);
	}

	/**
	 * Add a {@link ResolvedModuleName} to the beginning of my list of module
	 * dependencies (that end with a circularity).
	 *
	 * @param previousResolvedModuleName
	 *            The {@code ResolvedModuleName} to prepend.
	 */
	void prependModule (
		final @NotNull ResolvedModuleName previousResolvedModuleName)
	{
		recursionPath.add(0, previousResolvedModuleName);
	}

	/**
	 * Construct a new {@link RecursiveDependencyException}.
	 *
	 * @param circularModuleName
	 *            The module for which a circular dependency was detected.
	 */
	RecursiveDependencyException (
		final @NotNull ResolvedModuleName circularModuleName)
	{
		super(
			"module \""
			+ circularModuleName.qualifiedName()
			+ "\" recursively depends upon itself");
		prependModule(circularModuleName);
	}
}
