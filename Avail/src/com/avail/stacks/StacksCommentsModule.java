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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.avail.builder.ModuleNameResolver;
import com.avail.compiler.AbstractAvailCompiler.ModuleHeader;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.A_String;
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
	 * Stacks Error log
	 */
	String erroLog;

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
	 * The get method for moduleName
	 * @return
	 */
	public A_String moduleName()
	{
		return moduleName;
	}

	/**
	 *
	 */
	HashMap<A_String,List<AbstractCommentImplementation>>
		namedCommentImplementations =
			new HashMap<A_String,List<AbstractCommentImplementation>>();

	/**
	 *
	 */
	@Override
	public String toString()
	{
		final StringBuilder stringBuilder = new StringBuilder().append("<ol>");

		for (final List<AbstractCommentImplementation> value :
			namedCommentImplementations.values())
		{
		   for(final AbstractCommentImplementation method : value)
		   {
			   stringBuilder.append(method.toString());
		   }
		}
		return stringBuilder.append("</ol>\n").toString();
	}


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
	 * @param errorLog
	 * @param resolver
	 */
	public StacksCommentsModule(
		final ModuleHeader header,
		final A_Tuple commentTokens,
		final HashMap<A_String,A_Set> moduleToMethodMap,
		final StacksErrorLog errorLog,
		final ModuleNameResolver resolver)
	{
		this.moduleName = StringDescriptor
			.from(header.moduleName.qualifiedName());
		this.exportedNames = allExportedNames(
			header,moduleToMethodMap,resolver);

		moduleToMethodMap.put(
			this.moduleName,
			this.exportedNames);

		final StringBuilder errorMessages = new StringBuilder().append("");
		int errorCount = 0;

		for (final A_Token aToken : commentTokens)
		{
			try
			{
				final AbstractCommentImplementation implementation =
					StacksScanner.processCommentString(
						aToken,moduleName);

				addNamedImplementation(
					implementation.signature.name, implementation);
			}
			catch (StacksScannerException | StacksCommentBuilderException e)
			{
				errorMessages.append(e.getMessage());
				errorCount++;
			}
		}

		if (errorCount > 0)
		{
			final StringBuilder newLogEntry = new StringBuilder()
				.append("<h3>")
				.append(header.moduleName.qualifiedName())
				.append(" <em>(")
				.append(errorCount)
				.append(")</em></h3>\n<ol>");
			errorMessages.append("</ol>\n");
			newLogEntry.append(errorMessages);

			final ByteBuffer errorBuffer = ByteBuffer.wrap(
				newLogEntry.toString().getBytes(StandardCharsets.UTF_8));
			errorLog.addLogEntry(errorBuffer,errorCount);
		}
	}

	/**
	 * @param header
	 * @param moduleToMethodMap
	 * @param resolver
	 * @return
	 */
	private A_Set allExportedNames (final ModuleHeader header,
		final HashMap<A_String,A_Set> moduleToMethodMap,
		final ModuleNameResolver resolver)
	{
		/*final A_Set collectedExtendedNames =
			SetDescriptor.empty();

		for (final ModuleImport moduleImport : header.importedModules)
		{
		final A_String moduleImportName;
			try
			{
				moduleImportName = StringDescriptor.from(resolver.resolve(
					header.moduleName
						.asSibling(moduleImport.moduleName.asNativeString()),
					header.moduleName).qualifiedName());
			}
			catch (final UnresolvedDependencyException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (moduleToMethodMap.get(moduleImportName) == null)
			{

				moduleToMethodMap
					.put(moduleImportName,SetDescriptor.empty());
			}

			if (moduleImport.isExtension)
			{
				if (moduleImport.wildcard)
				{
					collectedExtendedNames.setUnionCanDestroy(
						moduleToMethodMap
							.get(moduleImportName),
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
		return collectedExtendedNames.setUnionCanDestroy(
			SetDescriptor.fromCollection(header.exportedNames), true);*/
			return null;
	}
}
