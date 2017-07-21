/**
 * EntryPointModuleNode.java
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

package com.avail.environment.nodes;

import org.jetbrains.annotations.Nullable;
import com.avail.builder.AvailBuilder;
import com.avail.builder.ResolvedModuleName;

/**
 * This is a tree node representing a module that has one or more entry points,
 * presented via {@link EntryPointNode}s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("serial")
public class EntryPointModuleNode extends AbstractBuilderFrameTreeNode
{
	/** The resolved name of the represented module. */
	final ResolvedModuleName resolvedModuleName;

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
	 * Construct a new {@link EntryPointNode}.
	 *
	 * @param builder The builder for which this node is being built.
	 * @param resolvedModuleName The name of the represented module.
	 */
	public EntryPointModuleNode (
		final AvailBuilder builder,
		final ResolvedModuleName resolvedModuleName)
	{
		super(builder);
		this.resolvedModuleName = resolvedModuleName;
	}

	@Override
	@Nullable String iconResourceName ()
	{
		return "ModuleInTree";
	}

	@Override
	String text (final boolean selected)
	{
		return resolvedModuleName.qualifiedName();
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

	@Override
	String htmlStyle (final boolean selected)
	{
		String base = super.htmlStyle(selected) + ";font-weight:bold";
		if (!isLoaded())
		{
			base += ";font-style:italic";
			if (!selected)
			{
				base += ";color:gray";
			}
		}
		return base;
	}
}
