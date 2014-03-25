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
	 * Stacks Error log
	 */
	String erroLog;

	/**
	 * all the named methods exported from the file
	 */
	private final HashMap <String, A_String> exportedNamesToExtendsModule;

	/**
	 * @return the exportedNames
	 */
	public HashMap <String, A_String> exportedNamesToExtendsModule ()
	{
		return exportedNamesToExtendsModule;
	}

	/**
	 * all the named methods extended from the file
	 */
	private final HashMap<A_String, StacksExtendsModule>
		extendedNamesImplementations;

	/**
	 * @return the exportedNames
	 */
	public HashMap<A_String, StacksExtendsModule>
		extendedNamesImplementations ()
	{
		return extendedNamesImplementations;
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
	private final HashMap<String,ImplementationGroup>
		namedPublicCommentImplementations;

	/**
	 * Get namedPublicCommentImplementations
	 * @return
	 */
	public HashMap<String,ImplementationGroup>
		namedPublicCommentImplementations()
	{
		return namedPublicCommentImplementations;
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
		if (namedPublicCommentImplementations.containsKey(nameToCheck))
		{
			comment.addToImplementationGroup(
				namedPublicCommentImplementations.get(nameToCheck));
		}
		else if (exportedNamesToExtendsModule.containsKey(nameToCheck))
		{
			comment.addToImplementationGroup(
				extendedNamesImplementations
					.get(exportedNamesToExtendsModule
						.get(nameToCheck)).implementations().get(nameToCheck));
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

		this.privateCommentImplementations =
			new HashMap<A_String,ImplementationGroup>();

		this.namedPublicCommentImplementations =
			new HashMap<String,ImplementationGroup>();

		for (final A_String implementationName : header.exportedNames)
		{
			this.namedPublicCommentImplementations.put(
				(moduleName.asNativeString() + "/"
					+ implementationName.asNativeString()),
				new ImplementationGroup(implementationName));
		}

		this.exportedNamesToExtendsModule =
			new HashMap<String,A_String>();

		this.extendedNamesImplementations = allExportedNames(
			header,moduleToMethodMap,resolver, publicComments);

		final ArrayList<A_String> exportedNames = new ArrayList<A_String>();

		for (final String exportedName : this.exportedNamesToExtendsModule.keySet())
		{
			exportedNames.add(StringDescriptor.from(exportedName));
		}

		moduleToMethodMap.put(
			this.moduleName, SetDescriptor.fromCollection(exportedNames));

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
	 * @param moduleToMethodMap
	 * @param resolver
	 * @param publicComments
	 * @return
	 */
	private HashMap<A_String,StacksExtendsModule> allExportedNames (
		final ModuleHeader header,
		final HashMap<A_String,A_Set> moduleToMethodMap,
		final ModuleNameResolver resolver,
		final HashMap<A_String,StacksCommentsModule> publicComments)
	{
		A_Set collectedExtendedNames =
			SetDescriptor.empty();

		final HashMap<A_String,StacksExtendsModule> extendsMap =
			new HashMap<A_String,StacksExtendsModule>();

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
					if (!moduleImport.excludes.equals(SetDescriptor.empty()))
					{
						collectedExtendedNames = collectedExtendedNames
							.setMinusCanDestroy(moduleImport.excludes, true);
					}

					final HashMap<String,ImplementationGroup> extendsGroup =
						new HashMap<String,ImplementationGroup>();

					for (final A_String implementation : collectedExtendedNames)
					{
						final String key = moduleImportName.asNativeString()
							+ "/" + implementation.asNativeString();
						extendsGroup.put((key),
							publicComments.get(moduleImportName)
								.namedPublicCommentImplementations()
									.get(implementation));
					}
					if (!moduleImport.renames.equals(MapDescriptor.empty()))
					{
						for (final A_String implementation :
							moduleImport.renames.keysAsSet())
						{
							final String oldKey =
								moduleImportName.asNativeString() + "/"
								+ implementation.asNativeString();
							final A_String rename =
								moduleImport.renames.mapAt(implementation);
							final String newKey =
								moduleImportName.asNativeString()
								+ "/" + rename.asNativeString();
							extendsGroup.put(
								newKey, extendsGroup.get(implementation));
							extendsGroup.remove(oldKey);
							extendsGroup.get(newKey).rename(rename);
						}

						for (final String implementation :
							extendsGroup.keySet())
						{
							exportedNamesToExtendsModule
								.put(implementation, moduleImportName);
						}
					}
					extendsMap.put(moduleImportName,
						new StacksExtendsModule(moduleImportName,extendsGroup));
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
}
