/**
 * StacksModuleComment.java
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

package com.avail.stacks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.avail.builder.ResolvedModuleName;
import com.avail.compiler.AbstractAvailCompiler.ModuleHeader;
import com.avail.compiler.AbstractAvailCompiler.ModuleImport;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.A_String;

/**
 * A representation of all the fully parsed {@linkplain CommentTokenDescriptor
 * comments} in a given module
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksModuleComment
{
	/**
	 * all the named methods exported from the file
	 */
	private final List<A_String> exportedNames;

	/**
	 * @return the exportedNames
	 */
	public List<A_String> exportedNames ()
	{
		return exportedNames;
	}

	/**
	 * all
	 */
	private final List<ModuleImport> extensensionModules =
		new ArrayList<ModuleImport>();

	/**
	 *
	 */
	private final ResolvedModuleName moduleName;

	/**
	 * The {@linkplain A_Tuple tuple} of {@linkplain CommentTokenDescriptor
	 * comment tokens}.
	 */
	private final A_Tuple commentTokens;

	/**
	 *
	 */
	HashMap<String,List<AbstractCommentImplementation>>
		namedCommentImplementations =
			new HashMap<String,List<AbstractCommentImplementation>>();

	/**
	 * @param name
	 * 		The implementation name
	 * @param comment
	 * 		The final parsed {@linkplain AbstractCommentImplementation comment
	 * 		implementation}.
	 */
	void addNamedImplementation(
		final String name,
		final AbstractCommentImplementation comment)
	{
		if (namedCommentImplementations.containsKey(name))
		{
			namedCommentImplementations.get(name).add(comment);
		}
		else
		{
			final ArrayList<AbstractCommentImplementation> newCommentList =
				new ArrayList<AbstractCommentImplementation>(1);
			newCommentList.add(comment);
			namedCommentImplementations.put(name,newCommentList);
		}
	}

	/**
	 * Construct a new {@link StacksModuleComment}.
	 *
	 * @param header
	 * @param commentTokens
	 */
	public StacksModuleComment(
		final ModuleHeader header,
		final A_Tuple commentTokens)
	{
		this.commentTokens = commentTokens;
		this.exportedNames = header.exportedNames;
		this.moduleName = header.moduleName;

	}

	private List<A_String> allExportedNames (final ModuleHeader header,
		final HashMap<A_String,List<A_String>> moduleToMethodMap)
	{
		final List<A_String> collectedExtendedNames =
			new ArrayList<A_String>();
		for (final ModuleImport moduleImport : header.importedModules)
		{
			if (moduleImport.isExtension)
			{
				if (moduleImport.wildcard)
				{
					collectedExtendedNames.addAll(moduleToMethodMap
						.get(moduleImport.moduleName));
				}
				else
				{

				}
			}
		}
		return collectedExtendedNames;
	}
}
