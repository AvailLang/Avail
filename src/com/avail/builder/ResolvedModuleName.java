/**
 * ResolvedModuleName.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import java.io.File;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.persistence.IndexedRepositoryManager;

/**
 * A {@code ResolvedModuleName} represents the canonical name of an Avail
 * {@linkplain ModuleDescriptor module} that has been resolved to an
 * {@linkplain File#isAbsolute() absolute} {@linkplain File file reference}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class ResolvedModuleName
extends ModuleName
{
	/**
	 * The {@link ModuleRoots} in which to look up the root name.
	 */
	private final ModuleRoots moduleRoots;

	private ModuleRoot moduleRoot ()
	{
		return moduleRoots.moduleRootFor(rootName());
	}

	/**
	 * Answer the {@linkplain ModuleNameResolver#resolve(ModuleName,
	 * ResolvedModuleName) resolved} {@linkplain IndexedRepositoryManager
	 * repository}.
	 *
	 * @return The resolved repository.
	 */
	public IndexedRepositoryManager repository ()
	{
		return moduleRoot().repository();
	}

	/**
	 * Answer the {@linkplain ModuleNameResolver#resolve(ModuleName,
	 * ResolvedModuleName) resolved} source {@linkplain File file reference}.
	 *
	 * @return The resolved source file reference.
	 */
	public File sourceReference ()
	{
		final StringBuilder builder = new StringBuilder(100);
		final File sourceDirectory = moduleRoot().sourceDirectory();
		assert sourceDirectory != null;
		builder.append(sourceDirectory);
		for (final String part : rootRelativeName().split("/"))
		{
			builder.append('/');
			builder.append(part);
			builder.append(ModuleNameResolver.availExtension);
		}
		return new File(builder.toString());
	}

	/**
	 * Does the {@linkplain ResolvedModuleName resolved module name} represent
	 * a package?
	 */
	private final boolean isPackage;

	/**
	 * Does the {@linkplain ResolvedModuleName resolved module name} represent
	 * a package?  A package representative is a module file with the same name
	 * as the directory in which it resides.
	 *
	 * @return {@code true} if the {@linkplain ResolvedModuleName resolved
	 *         module name} represents a package, {@code false} otherwise.
	 */
	public boolean isPackage ()
	{
		return isPackage;
	}

	/**
	 * Answer the size, in bytes, of the {@linkplain ModuleDescriptor module}.
	 * If the source module is available, then the size of the source module is
	 * used; otherwise, the size of the compiled module is used.
	 *
	 * @return The size of the module, in bytes.
	 */
	public long moduleSize ()
	{
		final File ref = sourceReference();
		return ref.length();
	}

	/**
	 * Construct a new {@link ResolvedModuleName}.
	 *
	 * @param qualifiedName
	 *        The just-resolved {@linkplain ModuleName module name}.
	 * @param moduleRoots
	 *        The {@linkplain ModuleRoots} with which to look up the module.
	 * @param isRename
	 *        Whether module resolution followed a renaming rule.
	 */
	ResolvedModuleName (
		final ModuleName qualifiedName,
		final ModuleRoots moduleRoots,
		final boolean isRename)
	{
		super(qualifiedName.qualifiedName(), isRename);
		this.moduleRoots = moduleRoots;
		final File ref = sourceReference();
		assert ref.isFile();
		final String fileName = ref.getName();
		final File directoryName = ref.getParentFile();
		this.isPackage = directoryName != null
			&& fileName.equals(directoryName.getName());
	}

	/**
	 * Answer the local module name as a sibling of the {@linkplain
	 * ResolvedModuleName receiver}.
	 *
	 * @param localName A local module name.
	 * @return A {@linkplain ModuleName module name}.
	 */
	public ModuleName asSibling (final String localName)
	{
		final String packageName = isPackage()
			? qualifiedName()
			: packageName();
		return new ModuleName(packageName, localName);
	}
}
