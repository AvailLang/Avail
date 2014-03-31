/**
 * ModuleRootOrPackageNode.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.environment;

import com.avail.builder.AvailBuilder;
import com.avail.builder.ResolvedModuleName;

/**
 * This is a tree node representing a module file or a package.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("serial")
class ModuleOrPackageNode extends AbstractBuilderFrameTreeNode
{
	/** The resolved name of the module. */
	final ResolvedModuleName resolvedModuleName;

	/** Whether this module's name refers to a package (directory). */
	final boolean isPackage;

	/**
	 * Construct a new {@link ModuleOrPackageNode}.
	 *
	 * @param builder The builder for which this node is being built.
	 * @param resolvedModuleName The name of the module or package.
	 * @param isPackage Whether it's a package.
	 */
	public ModuleOrPackageNode (
		final AvailBuilder builder,
		final ResolvedModuleName resolvedModuleName,
		final boolean isPackage)
	{
		super(builder);
		this.resolvedModuleName = resolvedModuleName;
		this.isPackage = isPackage;
	}

	@Override
	String text (final boolean selected)
	{
		return resolvedModuleName.localName();
	}

	@Override
	String htmlStyle (final boolean selected)
	{
		synchronized (builder)
		{
			String base = super.htmlStyle(selected);
			if (isPackage)
			{
				base = base + ";font-weight:bold";
			}
			else if (builder.getLoadedModule(resolvedModuleName) == null)
			{
				base = base + ";font-style:italic";
				if (!selected)
				{
					base = base + ";color:gray";
				}
			}
			return base;
		}
	}
}