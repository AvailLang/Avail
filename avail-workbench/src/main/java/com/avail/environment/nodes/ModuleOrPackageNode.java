/*
 * ModuleRootOrPackageNode.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.environment.nodes;

import com.avail.builder.AvailBuilder;
import com.avail.builder.ModuleName;
import com.avail.builder.ResolvedModuleName;

import javax.annotation.Nullable;

/**
 * This is a tree node representing a module file or a package.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("serial")
public class ModuleOrPackageNode
extends AbstractBuilderFrameTreeNode
{
	/** The local name of this module, prior to renames and resolution. */
	final ModuleName originalModuleName;

	/** The resolved name of the module. */
	final ResolvedModuleName resolvedModuleName;

	/** Whether this module's name refers to a package (directory). */
	final boolean isPackage;

	/**
	 * Answer the {@link ResolvedModuleName} that this represents.
	 *
	 * @return The resolved module name.
	 */
	public ResolvedModuleName resolvedModuleName ()
	{
		return resolvedModuleName;
	}

	/**
	 * Answer whether this represents a package.
	 *
	 * @return The {@link #isPackage} flag.
	 */
	public boolean isPackage ()
	{
		return isPackage;
	}

	/**
	 * Construct a new {@link ModuleOrPackageNode}.
	 *
	 * @param builder
	 *        The builder for which this node is being built.
	 * @param originalModuleName
	 *        The name of the module/package prior to resolution (renames).
	 * @param resolvedModuleName
	 *        The resolved name of the module or package.
	 * @param isPackage
	 *        Whether it's a package.
	 */
	public ModuleOrPackageNode (
		final AvailBuilder builder,
		final ModuleName originalModuleName,
		final ResolvedModuleName resolvedModuleName,
		final boolean isPackage)
	{
		super(builder);
		this.originalModuleName = originalModuleName;
		this.resolvedModuleName = resolvedModuleName;
		this.isPackage = isPackage;
	}

	@Override
	@Nullable String iconResourceName ()
	{
		return isPackage ? "PackageInTree" : "ModuleInTree";
	}

	@Override
	String text (final boolean selected)
	{
		if (isRenamedSource())
		{
			return originalModuleName.localName()
				+ " → "
				+ resolvedModuleName.qualifiedName();
		}
		else
		{
			return resolvedModuleName.localName();
		}
	}

	/**
	 * Is the {@linkplain ModuleOrPackageNode module or package} loaded?
	 *
	 * @return {@code true} if the module or package is already loaded, {@code
	 *         false} otherwise.
	 */
	public boolean isLoaded ()
	{
		synchronized (builder)
		{
			return builder.getLoadedModule(resolvedModuleName) != null;
		}
	}

	/**
	 * Answer whether this is a module that's the subject (source) of a rename
	 * rule.
	 *
	 * @return If this is a renamed module or package.
	 */
	public boolean isRenamedSource ()
	{
		return resolvedModuleName.isRename();
	}

	@Override
	String htmlStyle (final boolean selected)
	{
		String base = super.htmlStyle(selected);
		final boolean renamed = isRenamedSource();
		if (renamed)
		{
			base += ";color:orange";
		}
		if (isPackage)
		{
			base += ";font-weight:bold";
		}
		if (!isLoaded())
		{
			base += ";font-style:italic";
			if (!selected && !renamed)
			{
				base += ";color:gray";
			}
		}
		return base;
	}

	@Override
	public int sortMajor ()
	{
		return isPackage ? 10 : 20;
	}
}
