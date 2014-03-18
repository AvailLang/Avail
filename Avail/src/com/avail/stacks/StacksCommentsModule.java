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
import com.avail.builder.UnresolvedDependencyException;
import com.avail.compiler.AbstractAvailCompiler.ModuleHeader;
import com.avail.compiler.AbstractAvailCompiler.ModuleImport;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.A_String;
import com.avail.descriptor.MapDescriptor;
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
	 * All public methods/classes from this module.
	 */
	private final HashMap<A_String,List<AbstractCommentImplementation>>
		namedPulbicCommentImplementations;


	/**
	 * Get namedPulbicCommentImplementations
	 * @return
	 */
	public HashMap<A_String,List<AbstractCommentImplementation>>
		namedPulbicCommentImplementations()
	{
		return namedPulbicCommentImplementations;
	}

	/**
	 *
	 */
	@Override
	public String toString()
	{
		final StringBuilder stringBuilder = new StringBuilder().append("<ol>");

		for (final List<AbstractCommentImplementation> value :
			namedPulbicCommentImplementations.values())
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
	void addPublicNamedImplementation(
		final A_String name,
		final AbstractCommentImplementation comment)
	{
		final A_String nameToCheck =StringDescriptor.from(name.asNativeString().substring(1, name.tupleSize()-1));
		if (exportedNames.hasElement(nameToCheck))
		{
			if (namedPulbicCommentImplementations.containsKey(nameToCheck))
			{
				namedPulbicCommentImplementations.get(nameToCheck).add(comment);
			}
			else
			{
				final ArrayList<AbstractCommentImplementation> newCommentList =
					new ArrayList<AbstractCommentImplementation>(1);
				newCommentList.add(comment);
				namedPulbicCommentImplementations.put(nameToCheck,newCommentList);
			}
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
	 * @param publicComments
	 */
	public StacksCommentsModule(
		final ModuleHeader header,
		final A_Tuple commentTokens,
		final HashMap<A_String,A_Set> moduleToMethodMap,
		final StacksErrorLog errorLog,
		final ModuleNameResolver resolver,
		final HashMap<A_String,StacksCommentsModule> publicComments)
	{
		this.moduleName = StringDescriptor
			.from(header.moduleName.qualifiedName());

		this.namedPulbicCommentImplementations =
			new HashMap<A_String,List<AbstractCommentImplementation>>();
		this.exportedNames = allExportedNames(
			header,moduleToMethodMap,resolver, publicComments);

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

				addPublicNamedImplementation(
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
	 * @param publicComments
	 * @return
	 */
	private A_Set allExportedNames (final ModuleHeader header,
		final HashMap<A_String,A_Set> moduleToMethodMap,
		final ModuleNameResolver resolver,
		final HashMap<A_String,StacksCommentsModule> publicComments)
	{
		A_Set collectedExtendedNames =
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

				if (moduleToMethodMap.get(moduleImportName) == null)
				{

					moduleToMethodMap
						.put(moduleImportName,SetDescriptor.empty());
				}

				if (moduleImport.isExtension)
				{
					if (moduleImport.wildcard)
					{
						collectedExtendedNames =
							collectedExtendedNames.setUnionCanDestroy(
								moduleToMethodMap
								.get(moduleImportName),
							true);
					}
					if (!moduleImport.names.equals(SetDescriptor.empty()))
					{
						collectedExtendedNames = collectedExtendedNames
							.setUnionCanDestroy(moduleImport.names,true);
					}

					if (!moduleImport.renames.equals(MapDescriptor.empty()))
					{
						collectedExtendedNames = collectedExtendedNames
							.setMinusCanDestroy(
								moduleImport.renames.keysAsSet(),true);
						collectedExtendedNames = collectedExtendedNames
							.setUnionCanDestroy(moduleImport.renames
								.valuesAsTuple().asSet(),true);
					}
					if (!moduleImport.excludes.equals(SetDescriptor.empty()))
					{
						collectedExtendedNames = collectedExtendedNames
							.setMinusCanDestroy(moduleImport.excludes, true);
					}
					for (final A_String implementationName :
						collectedExtendedNames)
					{

						if (publicComments.get(moduleImportName)
							.namedPulbicCommentImplementations()
							.containsKey(implementationName))
						{
							if (namedPulbicCommentImplementations()
								.containsKey(implementationName))
							{
								namedPulbicCommentImplementations()
									.get(implementationName)
									.addAll(publicComments
										.get(moduleImportName)
										.namedPulbicCommentImplementations()
										.get(implementationName));
							}
							else
							{
								final ArrayList<AbstractCommentImplementation>
									newCommentList =
									new ArrayList
										<AbstractCommentImplementation>(1);
								newCommentList.addAll(publicComments
									.get(moduleImportName)
									.namedPulbicCommentImplementations()
									.get(implementationName));
								namedPulbicCommentImplementations
									.put(implementationName,newCommentList);
							}
						}
					}
					collectedExtendedNames = collectedExtendedNames
						.setUnionCanDestroy(collectedExtendedNames, true);
				}
			}
			catch (final UnresolvedDependencyException e)
			{
					// TODO Auto-generated catch block
					e.printStackTrace();
			}
		}
		return collectedExtendedNames.setUnionCanDestroy(
			SetDescriptor.fromCollection(header.exportedNames), true);
	}
}
