/**
 * ModuleRootNode.java
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

package com.avail.environment.nodes;

import com.avail.annotations.Nullable;
import com.avail.builder.AvailBuilder;
import com.avail.builder.ModuleRoot;

/**
 * This is a tree node representing a {@link ModuleRoot}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("serial")
public class ModuleRootNode extends AbstractBuilderFrameTreeNode
{
	/** The {@link ModuleRoot} that this represents. */
	final ModuleRoot moduleRoot;

	/**
	 * Answer the {@link ModuleRoot} that this represents.
	 * @return The module root.
	 */
	public ModuleRoot moduleRoot ()
	{
		return moduleRoot;
	}

	/**
	 * Construct a new {@link ModuleRootNode}.
	 *
	 * @param builder The builder for which this node is being built.
	 * @param moduleRoot The {@link ModuleRoot} that this represents.
	 */
	public ModuleRootNode (
		final AvailBuilder builder,
		final ModuleRoot moduleRoot)
	{
		super(builder);
		this.moduleRoot = moduleRoot;
	}

	@Override
	@Nullable String iconResourceName ()
	{
		return null;
	}

	@Override
	String text (final boolean selected)
	{
		return moduleRoot.name();
	}

	@Override
	String htmlStyle (final boolean selected)
	{
		return "font-weight:900";
	}
}