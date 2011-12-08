/**
 * ResolvedModuleName.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.compiler;

import java.io.File;
import com.avail.annotations.NotNull;
import com.avail.descriptor.ModuleDescriptor;

/**
 * A {@code ResolvedModuleName} represents the canonical name of an Avail
 * {@linkplain ModuleDescriptor module} that has been resolved to an
 * {@linkplain File#isAbsolute() absolute} {@linkplain File file reference}.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class ResolvedModuleName
extends ModuleName
{
	/**
	 * The {@linkplain ModuleNameResolver#resolve(ModuleName) resolved}
	 * {@linkplain File file reference}.
	 */
	private final @NotNull File fileReference;

	/**
	 * Answer the {@linkplain ModuleNameResolver#resolve(ModuleName) resolved}
	 * {@linkplain File file reference}.
	 *
	 * @return The {@linkplain ModuleNameResolver#resolve(ModuleName) resolved}
	 *         {@linkplain File file reference}.
	 */
	public @NotNull File fileReference ()
	{
		return fileReference;
	}

	/**
	 * Does the {@linkplain ResolvedModuleName resolved module name} represent
	 * a module group? This is a cached value produced by {@link
	 * #isModuleGroup()}.
	 */
	private final boolean isModuleGroup;

	/**
	 * Does the {@linkplain ResolvedModuleName resolved module name} represent
	 * a module group?
	 *
	 * @return {@code true} if the {@linkplain ResolvedModuleName resolved
	 *         module name} represents a module group, {@code false} otherwise.
	 */
	public boolean isModuleGroup ()
	{
		return isModuleGroup;
	}

	/**
	 * Answer the local module name as a sibling of the {@linkplain
	 * ResolvedModuleName receiver}.
	 *
	 * @param localName A local module name.
	 * @return A {@linkplain ModuleName module name}.
	 */
	public ModuleName asSibling (final @NotNull String localName)
	{
		final String moduleGroup = isModuleGroup()
			? qualifiedName()
			: moduleGroup();
		return new ModuleName(moduleGroup, localName);
	}

	/**
	 * Construct a new {@link ResolvedModuleName}.
	 *
	 * @param qualifiedName
	 *        The just-resolved {@linkplain ModuleName module name}.
	 * @param isModuleGroup
	 *        {@code true} if the {@linkplain ModuleName module name} represents
	 *        a module group, {@code false} otherwise.
	 * @param fileReference
	 *        The {@linkplain ModuleNameResolver#resolve(ModuleName) resolved}
	 *        {@linkplain File file reference}.
	 */
	ResolvedModuleName (
		final @NotNull ModuleName qualifiedName,
		final boolean isModuleGroup,
		final @NotNull File fileReference)
	{
		super(qualifiedName.qualifiedName());
		this.isModuleGroup = isModuleGroup;
		this.fileReference = fileReference;
	}
}
