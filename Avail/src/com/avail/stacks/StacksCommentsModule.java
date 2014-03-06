/**
 * StacksCommentsModule.java
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
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.A_String;
import com.avail.descriptor.NilDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.StringDescriptor;

/**
 * A representation of all the fully parsed {@linkplain CommentTokenDescriptor
 * comments} in a given module
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksCommentsModule
{
	/**
	 * all the named methods exported from the file
	 */
	private final A_Set exportedNames;

	/**
	 * @return the exportedNames
	 */
	public A_Set exportedNames ()
	{
		return exportedNames;
	}

	/**
	 *	The name of the module that contains these Stacks Comments.
	 */
	private final A_String moduleName;

	/**
	 *
	 */
	HashMap<A_String,List<AbstractCommentImplementation>>
		namedCommentImplementations =
			new HashMap<A_String,List<AbstractCommentImplementation>>();


	/**
	 * @param name
	 * 		The implementation name
	 * @param comment
	 * 		The final parsed {@linkplain AbstractCommentImplementation comment
	 * 		implementation}.
	 */
	void addNamedImplementation(
		final A_String name,
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
	 * Construct a new {@link StacksCommentsModule}.
	 *
	 * @param header
	 * @param commentTokens
	 * @param moduleToMethodMap
	 * @throws StacksCommentBuilderException
	 * @throws StacksScannerException
	 */
	public StacksCommentsModule(
		final ModuleHeader header,
		final A_Tuple commentTokens,
		final HashMap<A_String,A_Set> moduleToMethodMap)
			throws StacksScannerException, StacksCommentBuilderException
	{
		this.exportedNames = allExportedNames(header,moduleToMethodMap);
		this.moduleName = StringDescriptor
			.from(header.moduleName.qualifiedName());

		moduleToMethodMap.put(
			this.moduleName,
			SetDescriptor.fromCollection(header.exportedNames));

		for (final A_Token aToken : commentTokens)
		{
			//TODO [RAA} Handle errors by logging them.
			final AbstractCommentImplementation implementation =
				StacksScanner.processCommentString(aToken);

			addNamedImplementation(
				implementation.signature.name, implementation);
		}
	}

	/**
	 * @param header
	 * @param moduleToMethodMap
	 * @return
	 */
	private A_Set allExportedNames (final ModuleHeader header,
		final HashMap<A_String,A_Set> moduleToMethodMap)
	{
		final A_Set collectedExtendedNames =
			SetDescriptor.empty();
		for (final ModuleImport moduleImport : header.importedModules)
		{
			if (moduleImport.isExtension)
			{
				if (moduleImport.wildcard)
				{
					collectedExtendedNames.setUnionCanDestroy(
						moduleToMethodMap
							.get(moduleImport.moduleName),
						true);
				}

				if (moduleImport.excludes != NilDescriptor.nil())
				{
					collectedExtendedNames
						.setMinusCanDestroy(moduleImport.excludes, true);
				}

				if (moduleImport.renames != NilDescriptor.nil())
				{
					collectedExtendedNames
						.setMinusCanDestroy(
							moduleImport.renames.keysAsSet(),true);
					collectedExtendedNames.setUnionCanDestroy(
						moduleImport.renames.valuesAsTuple().asSet(),true);
				}

				if (moduleImport.names != NilDescriptor.nil())
				{
					collectedExtendedNames.setUnionCanDestroy(
						moduleImport.names,
						true);
				}
			}
		}
		collectedExtendedNames.setUnionCanDestroy(
			SetDescriptor.fromCollection(header.exportedNames), true);

		return collectedExtendedNames;
	}
}
