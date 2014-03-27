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
import java.util.HashMap;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.UnresolvedDependencyException;
import com.avail.compiler.AbstractAvailCompiler.ModuleHeader;
import com.avail.compiler.AbstractAvailCompiler.ModuleImport;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.A_String;
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
	 * Stacks Error log
	 */
	String erroLog;

	/**
	 * A map of the modules extended by this module to the {@linkplain
	 * StacksExtendsModule module} content.
	 */
	private final HashMap<String, StacksExtendsModule>
		extendedNamesImplementations;

	/**
	 * @return the exportedNames
	 */
	public HashMap<String, StacksExtendsModule>
		extendedNamesImplementations ()
	{
		return extendedNamesImplementations;
	}

	/**
	 *	The name of the module that contains these Stacks Comments.
	 */
	private final String moduleName;

	/**
	 * The get method for moduleName
	 * @return
	 */
	public String moduleName()
	{
		return moduleName;
	}

	/**
	 * All public methods/classes from this module.
	 */
	private final HashMap<A_String,ImplementationGroup>
		namedPublicCommentImplementations;

	/**
	 * Get namedPublicCommentImplementations
	 * @return
	 */
	public HashMap<A_String,ImplementationGroup>
		namedPublicCommentImplementations()
	{
		return namedPublicCommentImplementations;
	}

	/**
	 * A map keyed by a method name with no path to the qualified module
	 * path it is originally named from.  This list includes all the methods
	 * from the {@linkplain StacksExtendsModule modules} extended by this
	 * module as well as its own names.
	 */
	private final HashMap<A_String,String> methodLeafNameToModuleName;

	/**
	 * @return the methodLeafNameToModuleName
	 */
	public HashMap<A_String,String> methodLeafNameToModuleName ()
	{
		return methodLeafNameToModuleName;
	}

	/**
	 * Add an implementation to an {@linkplain ImplementationGroup} of the
	 * appropriate module.
	 * @param name
	 * 		The implementation name
	 * @param comment
	 * 		The final parsed {@linkplain AbstractCommentImplementation comment
	 * 		implementation}.
	 */
	void addImplementation(
		final String name,
		final AbstractCommentImplementation comment)
	{
		final A_String nameToCheck =
			StringDescriptor.from(name);

		if (methodLeafNameToModuleName.containsKey(nameToCheck))
		{

			if (namedPublicCommentImplementations.containsKey(nameToCheck))
			{
				comment.addToImplementationGroup(
					namedPublicCommentImplementations.get(nameToCheck));
			}
			else
			{
				for (final StacksExtendsModule extendsModule :
					extendedNamesImplementations.values())
				{
					final StacksExtendsModule owningModule = extendsModule
						.getExtendsModuleForImplementationName(nameToCheck);

					if (! (owningModule == null))
					{
						comment.addImplementationToExtendsModule(
							nameToCheck,owningModule);
					}
				}
			}
		}
		else
		{
			if (!privateCommentImplementations.containsKey(nameToCheck))
			{
				privateCommentImplementations
					.put(nameToCheck, new ImplementationGroup(nameToCheck));
			}

			comment.addToImplementationGroup(
				privateCommentImplementations.get(nameToCheck));
		}
	}

	/**
	 * All private methods/classes from this module.
	 */
	private final HashMap<A_String,ImplementationGroup>
		privateCommentImplementations;

	/**
	 * @return the privateCommentImplementations
	 */
	public HashMap<A_String,ImplementationGroup> privateCommentImplementations ()
	{
		return privateCommentImplementations;
	}

	/**
	 * Construct a new {@link StacksCommentsModule}.
	 *
	 * @param header
	 * @param commentTokens
	 * @param errorLog
	 * @param resolver
	 * @param moduleToComments
	 */
	public StacksCommentsModule(
		final ModuleHeader header,
		final A_Tuple commentTokens,
		final StacksErrorLog errorLog,
		final ModuleNameResolver resolver,
		final HashMap<String, StacksCommentsModule> moduleToComments)
	{
		this.moduleName = header.moduleName.qualifiedName();

		this.privateCommentImplementations =
			new HashMap<A_String,ImplementationGroup>();

		this.namedPublicCommentImplementations =
			new HashMap<A_String,ImplementationGroup>();

		this.methodLeafNameToModuleName =
			new HashMap<A_String,String>();

		for (final A_String implementationName : header.exportedNames)
		{
			this.namedPublicCommentImplementations.put(implementationName,
				new ImplementationGroup(implementationName));

			this.methodLeafNameToModuleName
				.put(implementationName, this.moduleName);
		}

		this.extendedNamesImplementations = allExtendsModules(
			header,resolver, moduleToComments);

		final StringBuilder errorMessages = new StringBuilder().append("");
		int errorCount = 0;

		for (final A_Token aToken : commentTokens)
		{
			try
			{
				final AbstractCommentImplementation implementation =
					StacksScanner.processCommentString(
						aToken,moduleName);

				addImplementation(
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
	 * @param resolver
	 * @param moduleToComments
	 * @return
	 */
	private HashMap<String,StacksExtendsModule> allExtendsModules (
		final ModuleHeader header,
		final ModuleNameResolver resolver,
		final HashMap<String,StacksCommentsModule> moduleToComments)
	{
		A_Set collectedExtendedNames =
			SetDescriptor.empty();

		final HashMap<String,StacksExtendsModule> extendsMap =
			new HashMap<String,StacksExtendsModule>();

		for (final ModuleImport moduleImport : header.importedModules)
		{
			final String moduleImportName;
			try
			{
				moduleImportName = resolver.resolve(
					header.moduleName
						.asSibling(moduleImport.moduleName.asNativeString()),
					header.moduleName).qualifiedName();

				if (moduleImport.isExtension)
				{
					if (moduleImport.wildcard)
					{
						collectedExtendedNames =
							collectedExtendedNames.setUnionCanDestroy(
								SetDescriptor.fromCollection(
									moduleToComments.get(moduleImportName)
										.namedPublicCommentImplementations
											.keySet()),
							true);
					}
					if (!moduleImport.excludes.equals(SetDescriptor.empty()))
					{
						collectedExtendedNames = collectedExtendedNames
							.setMinusCanDestroy(moduleImport.excludes, true);
					}

					//Determine what keys need to be explicitly removed
					//due to the rename.
					final A_Set removeRenamesKeys =
						moduleImport.renames.keysAsSet()
							.setMinusCanDestroy(moduleImport.names, true);

					collectedExtendedNames =
						collectedExtendedNames.setUnionCanDestroy(
							moduleImport.names, true);

					final StacksExtendsModule stacksExtends =
						moduleToComments.get(moduleImportName)
							.convertToStacksExtendsModule();

					final A_Set methodsToDelete = (SetDescriptor.fromCollection(
						stacksExtends.implementations().keySet())
							.setMinusCanDestroy(collectedExtendedNames,true))
						.setUnionCanDestroy(removeRenamesKeys, true);

					for (final A_String rename :
						moduleImport.renames.keysAsSet())
					{
						stacksExtends.renameImplementation(rename,
							moduleImport.renames.mapAt(rename));
					}

					for (final A_String key : methodsToDelete)
					{
						stacksExtends.removeImplementation(key);
					}

					extendsMap.put(moduleImportName,stacksExtends);
					methodLeafNameToModuleName
						.putAll(stacksExtends.methodLeafNameToModuleName());
				}
			}
			catch (final UnresolvedDependencyException e)
			{
					// TODO Auto-generated catch block
					e.printStackTrace();
			}
		}
		return extendsMap;
	}

	/**
	 * Convert a {@linkplain StacksCommentsModule} to a {@linkplain
	 * StacksExtendsModule}
	 * @return
	 * 		the newly created StacksExtendsModule
	 */
	public StacksExtendsModule convertToStacksExtendsModule()
	{
		return new StacksExtendsModule(moduleName,
			new HashMap<A_String,ImplementationGroup>(
				namedPublicCommentImplementations),
			new HashMap<String,StacksExtendsModule>(
				extendedNamesImplementations),
			new HashMap<A_String,String>(methodLeafNameToModuleName));
	}

	@Override
	public String toString()
	{
		final StringBuilder stringBuilder = new StringBuilder().append("<h2>")
			.append(moduleName).append("</h2>")
			.append("<h3>Names</h3><ol>");

		for (final A_String key : namedPublicCommentImplementations.keySet())
		{
			stringBuilder.append("<li>").append(key.asNativeString())
				.append("</li>");
		}
		stringBuilder.append("</ol><h3>Extends</h3><ol>");

		for (final StacksExtendsModule value :
			extendedNamesImplementations.values())
		{
			stringBuilder.append(value.toString());
		}
		return stringBuilder.toString();
	}
}
