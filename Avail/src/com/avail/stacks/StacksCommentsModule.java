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
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import com.avail.AvailRuntime;
import com.avail.builder.ModuleName;
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
import com.avail.utility.Pair;

/**
 * A representation of all the fully parsed {@linkplain CommentTokenDescriptor
 * comments} in a given module
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksCommentsModule
{
	/**
	 * A map of the modules extended by this module to the {@linkplain
	 * StacksExtendsModule module} content.
	 */
	private HashMap<String, StacksExtendsModule>
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
	 * A map of the modules used by this module to the {@linkplain
	 * StacksUsesModule module} content.
	 */
	private HashMap<String,StacksUsesModule> usesNamesImplementations;

	/**
	 * @return the usesNamesImplementations
	 */
	public HashMap<String,StacksUsesModule> usesNamesImplementations ()
	{
		return usesNamesImplementations;
	}

	/**
	 * A map keyed by the qualified module names used by this module to a map
	 * keyed by a method name to implementations created in this module.
	 *
	 */
	private final HashMap<String,ArrayList<AbstractCommentImplementation>>
		usesModuleToImplementedNamesToImplementation;

	/**
	 * @return the usesModuleToImplementedNamesToImplementation
	 */
	public HashMap<String,ArrayList<AbstractCommentImplementation>>
		usesModuleToImplementedNamesToImplementation ()
	{
		return usesModuleToImplementedNamesToImplementation;
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
	 *  All the {@linkplain ImplementationGroup named implementations}
	 *  exported out of this module that will be documented if this is
	 *  The outermost {@linkplain StacksCommentsModule module} for the
	 *  generation request.
	 */
	private HashMap<String,ImplementationGroup> finalImplementationsGroupMap;

	/**
	 * A map keyed by a method name with no path to the qualified module
	 * path it is originally named from.  This list includes all the methods
	 * from the {@linkplain StacksExtendsModule modules} extended by this
	 * module as well as its own names.
	 */
	private final HashMap<A_String,String> extendsMethodLeafNameToModuleName;

	/**
	 * @return the extendsMethodLeafNameToModuleName
	 */
	public HashMap<A_String,String> extendsMethodLeafNameToModuleName ()
	{
		return extendsMethodLeafNameToModuleName;
	}

	/**
	 * A map keyed by a method name with no path to the qualified module
	 * path it is originally named from.  This list includes all the methods
	 * from the {@linkplain StacksUsesModule modules} used by this
	 * module.
	 */
	private final HashMap<A_String,String> usesMethodLeafNameToModuleName;

	/**
	 * @return the extendsMethodLeafNameToModuleName
	 */
	public HashMap<A_String,String> usesMethodLeafNameToModuleName ()
	{
		return usesMethodLeafNameToModuleName;
	}

	/**
	 * Links the name to the appropriate file name.
	 */
	final HashMap<A_String,Integer> nameToFileName =
		new HashMap<A_String,Integer>();

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

		if (extendsMethodLeafNameToModuleName.containsKey(nameToCheck))
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

					if (owningModule != null)
					{
						comment.addImplementationToImportModule(
							nameToCheck,owningModule);
					}
				}
			}
		}
		else
		{
			String owningModule = null;
			StacksUsesModule importModule = null;
			for (final StacksUsesModule usesModule :
				usesNamesImplementations.values())
			{
				if (owningModule == null)
				{
					owningModule = usesModule
						.getExtendsModuleNameForImplementationName(nameToCheck);
					if ((owningModule != null))
					{
						importModule = usesModule;
					}
				}
			}

			if (owningModule != null)
			{
				int separaterIndex = owningModule.lastIndexOf("/");
				String parentModule = owningModule
					.substring(0,separaterIndex);

				StacksUsesModule owningUsesModule = null;

				while (separaterIndex >= 0 && owningUsesModule == null)
				{
					owningUsesModule =
						usesNamesImplementations.get(parentModule);

					if (owningUsesModule == null)
					{
						parentModule = parentModule
							.substring(0,separaterIndex);
						separaterIndex = parentModule.lastIndexOf("/");
					}
				}


				if (owningUsesModule !=  null && importModule != null)
				{
					final StacksExtendsModule originatingModule =
						importModule.getExtendsModuleForImplementationName(
							nameToCheck, owningModule);

					comment.addImplementationToImportModule(
						nameToCheck,originatingModule);
				}
				else if (importModule != null)
				{
					if (importModule.implementations()
						.containsKey(nameToCheck))
					{
						comment.addImplementationToImportModule(nameToCheck,
							importModule);
					}
					else
					{
						StacksExtendsModule primaryExtendModule =
							usesNamesImplementations.get(
								importModule.moduleName)
									.moduleNameToExtendsList()
										.get(owningModule);

						if (primaryExtendModule == null)
						{
							for (final StacksExtendsModule extendModule :
								usesNamesImplementations.get(
									importModule.moduleName)
										.moduleNameToExtendsList().values())
							{
								if (primaryExtendModule == null)
								{
									primaryExtendModule = extendModule
										.getExtendsModule(owningModule);
								}
							}
						}

						if (primaryExtendModule != null)
						{
							comment.addImplementationToImportModule(
								nameToCheck,
								primaryExtendModule);
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
	 * 		The {@linkplain ModuleHeader} of the current file
	 * @param commentTokens
	 * 		A {@linkplain A_Tuple} of all the comment tokens.
	 * @param errorLog
	 * 		The file for outputting all errors.
	 * @param resolver
	 * 		The {@linkplain ModuleNameResolver} for resolving module paths.
	 * @param moduleToComments
	 * 		A map of {@linkplain ModuleName module names} to a list of all
	 * 		the method names exported from said module
	 * @param htmlFileMap
	 * 		A map for all HTML files ins Stacks
	 */
	public StacksCommentsModule(
		final ModuleHeader header,
		final A_Tuple commentTokens,
		final StacksErrorLog errorLog,
		final ModuleNameResolver resolver,
		final HashMap<String, StacksCommentsModule> moduleToComments,
		final HTMLFileMap htmlFileMap)
	{
		this.moduleName = header.moduleName.qualifiedName();

		this.usesModuleToImplementedNamesToImplementation =
			new HashMap<String,ArrayList<AbstractCommentImplementation>>();

		this.privateCommentImplementations =
			new HashMap<A_String,ImplementationGroup>();

		this.namedPublicCommentImplementations =
			new HashMap<A_String,ImplementationGroup>();

		this.extendsMethodLeafNameToModuleName =
			new HashMap<A_String,String>();

		this.usesMethodLeafNameToModuleName =
			new HashMap<A_String,String>();

		for (final A_String implementationName : header.exportedNames)
		{
			this.namedPublicCommentImplementations.put(implementationName,
				new ImplementationGroup(implementationName));

			this.extendsMethodLeafNameToModuleName
				.put(implementationName, this.moduleName);
		}

		this.usesNamesImplementations = new HashMap<String,StacksUsesModule>();

		buildModuleImportMaps(
			header,resolver, moduleToComments);

		final StringBuilder errorMessages = new StringBuilder().append("");
		int errorCount = 0;

		for (final A_Token aToken : commentTokens)
		{
			try
			{
				final AbstractCommentImplementation implementation =
					StacksScanner.processCommentString(
						aToken,moduleName,htmlFileMap);

				if (!(implementation == null))
				{
					addImplementation(
						implementation.signature().name(), implementation);
				}
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
	 * Construct all the maps that connects all visible method names to the
	 * modules they come from.  Build all {@linkplain StacksExtendsModule}
	 * and {@linkplain StacksUsesModule} for all modules imported by this
	 * module.
	 * @param header
	 * 		The {@linkplain ModuleHeader} of the current file
	 * @param resolver
	 * 		The {@linkplain ModuleNameResolver} for resolving module paths.
	 * @param moduleToComments
	 * 		A map of {@linkplain ModuleName module names} to a list of all
	 * 		the method names exported from said module
	 */
	private void buildModuleImportMaps (
		final ModuleHeader header,
		final ModuleNameResolver resolver,
		final HashMap<String,StacksCommentsModule> moduleToComments)
	{
		A_Set collectedExtendedNames =
			SetDescriptor.empty();

		A_Set collectedUsesNames =
			SetDescriptor.empty();

		final HashMap<String,StacksExtendsModule> extendsMap =
			new HashMap<String,StacksExtendsModule>();

		final HashMap<String,StacksUsesModule> usesMap =
			new HashMap<String,StacksUsesModule>();

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
						new StacksExtendsModule(
							moduleToComments.get(moduleImportName));

					final A_Set methodsToDelete = (SetDescriptor.fromCollection(
						stacksExtends.implementations().keySet())
							.setMinusCanDestroy(collectedExtendedNames,true))
						.setUnionCanDestroy(removeRenamesKeys, true);

					for (final A_String rename :
						moduleImport.renames.keysAsSet())
					{
						stacksExtends.renameImplementation(
							moduleImport.renames.mapAt(rename),rename);
					}

					for (final A_String key : methodsToDelete)
					{
						stacksExtends.removeImplementation(key);
					}

					extendsMap.put(moduleImportName,stacksExtends);
					extendsMethodLeafNameToModuleName
						.putAll(
							stacksExtends.extendsMethodLeafNameToModuleName());
				}
				else
				{
					if (moduleImport.wildcard)
					{
						collectedUsesNames =
							collectedUsesNames.setUnionCanDestroy(
								SetDescriptor.fromCollection(
									moduleToComments.get(moduleImportName)
										.namedPublicCommentImplementations
											.keySet()),
							true);
					}
					if (!moduleImport.excludes.equals(SetDescriptor.empty()))
					{
						collectedUsesNames = collectedUsesNames
							.setMinusCanDestroy(moduleImport.excludes, true);
					}

					//Determine what keys need to be explicitly removed
					//due to the rename.
					final A_Set removeRenamesKeys =
						moduleImport.renames.keysAsSet()
							.setMinusCanDestroy(moduleImport.names, true);

					collectedUsesNames =
						collectedUsesNames.setUnionCanDestroy(
							moduleImport.names, true);

					final StacksUsesModule stacksUses =
						new StacksUsesModule(
							moduleToComments.get(moduleImportName),
							moduleImport.renames);

					final A_Set methodsToDelete = (SetDescriptor.fromCollection(
						stacksUses.implementations().keySet())
							.setMinusCanDestroy(collectedUsesNames,true))
						.setUnionCanDestroy(removeRenamesKeys, true);

					for (final A_String rename :
						moduleImport.renames.keysAsSet())
					{
						stacksUses.renameImplementation(
							moduleImport.renames.mapAt(rename),rename);
					}

					for (final A_String key : methodsToDelete)
					{
						stacksUses.removeImplementation(key);
					}

					usesMap.put(moduleImportName,stacksUses);
					usesMethodLeafNameToModuleName
						.putAll(stacksUses.usesMethodLeafNameToModuleName());
				}
			}
			catch (final UnresolvedDependencyException e)
			{
					// TODO Auto-generated catch block
					e.printStackTrace();
			}
		}
		extendedNamesImplementations = extendsMap;
		usesNamesImplementations = usesMap;

		final HashMap<String,ImplementationGroup>
			namesExtendsImplementationsMap =
				new HashMap<String,ImplementationGroup>();

		for (final StacksUsesModule module : usesNamesImplementations.values())
		{
			for (final StacksUsesModule usesModule :
				module.moduleNameToUsesList().values())
			{
				for (final A_String nameToCheck :
					usesModule.implementations().keySet())
				{
					if (usesModule.implementations()
						.get(nameToCheck).isPopulated())
					{
						for (final StacksExtendsModule extendsModule :
							extendedNamesImplementations.values())
						{
							//Create map used down below this block
							namesExtendsImplementationsMap.putAll(
								extendsModule.flattenImplementationGroups()
									.first());

							final StacksExtendsModule owningModule =
								extendsModule
								.getExtendsModuleForImplementationName(
									nameToCheck);

							if (owningModule != null)
							{
									owningModule.implementations()
										.get(nameToCheck)
											.mergeWith(usesModule
												.implementations()
												.get(nameToCheck));
							}
						}
					}
				}

				final HashMap<String,ImplementationGroup>
					namesUsesExtendsImplementationsMap =
						new HashMap<String,ImplementationGroup>();

				for (final StacksExtendsModule usesExtendsModule :
					usesModule.moduleNameToExtendsList().values())
				{
					final HashMap<String, ImplementationGroup> first =
						usesExtendsModule.flattenImplementationGroups().first();

					for (final String key : first.keySet())
					{
						if (first.get(key).isPopulated())
						{
							namesUsesExtendsImplementationsMap
								.put(key, first.get(key));
						}
					}
				}

				for (final String key :
					namesUsesExtendsImplementationsMap.keySet())
				{
					if (namesExtendsImplementationsMap.containsKey(key))
					{
						namesExtendsImplementationsMap.get(key)
							.mergeWith(
								namesUsesExtendsImplementationsMap
									.get(key));
					}
				}
			}
		}
	}

	/**
	 * Acquire all distinct implementations being directly exported or extended
	 * by this module and populate finalImplementationsGroupMap.
	 * @param htmlFileMap
	 * 		A map for all html files in stacks
	 * @return
	 * 		The size of the map
	 */
	public int
		calculateFinalImplementationGroupsMap(
			final HTMLFileMap htmlFileMap)
	{
		//A map of all method names to exported ImplementGroups
		//regardless if the implementation is not populated.
		final HashMap<String,ImplementationGroup> newMap =
			new HashMap<String,ImplementationGroup>();

		final HashMap<String,String> nameToLinkMap =
			new HashMap<String,String>();

/*		for ( final String extendsModuleName :
			extendedNamesImplementations.keySet())
		{
			if (usesModuleToImplementedNamesToImplementation
				.containsKey(extendsModuleName))
			{
				for (final ArrayList<AbstractCommentImplementation> implementations :
					usesModuleToImplementedNamesToImplementation.values())
				{
					for (final AbstractCommentImplementation comment :
						implementations)
					{
						comment.addImplementationToImportModule(
							StringDescriptor.from(comment.signature().name()),
							extendedNamesImplementations
								.get(extendsModuleName));
					}
				}
			}
		}*/

		for (final StacksExtendsModule extendsModule :
			extendedNamesImplementations.values())
		{
			final Pair<HashMap<String,ImplementationGroup>,
				HashMap<String,String>> pair =
					extendsModule.flattenImplementationGroups();
			newMap.putAll(pair.first());
			nameToLinkMap.putAll(pair.second());
		}

		final HashMap<A_String,Integer> newHashNameMap =
			new HashMap<A_String,Integer>();

		for (final A_String key : namedPublicCommentImplementations.keySet())
		{
			A_String nameToBeHashed = key;
			if (newHashNameMap.containsKey(key))
			{
				newHashNameMap.put(key, newHashNameMap.get(key) + 1);
				nameToBeHashed =
					StringDescriptor.from(key.asNativeString()
						+ newHashNameMap.get(key));
			}
			else
			{
				newHashNameMap.put(nameToBeHashed, 0);
			}

			long hashedName = nameToBeHashed.hash();
			hashedName = hashedName & 0xFFFFFFFFL;

			final String qualifiedName = moduleName + "/"
				+ String.valueOf(hashedName) + ".html";

			final String qualifiedMethodName = moduleName + "/"
				+ key.asNativeString();
			newMap.put(qualifiedMethodName,
				namedPublicCommentImplementations.get(key));
			nameToLinkMap.put(key.asNativeString(), qualifiedName);
		}

		final HashMap<String,ImplementationGroup> filteredMap =
			new HashMap<String,ImplementationGroup>();

		for (final String key : newMap.keySet())
		{
			if (newMap.get(key).isPopulated())
			{
				filteredMap.put(key, newMap.get(key));
			}
	/*		else //WRITE A METHOD that checks to see if there is a uses
				//implementation
			{
				//Add appropriate implementations to ImplementationGroup
				//Then add ImplementationGroup to filteredList
			}*/
		}

		finalImplementationsGroupMap = filteredMap;

		for (final String methodName : nameToLinkMap.keySet())
		{
			final String filterMapKey = nameToLinkMap.get(methodName);
			if (filteredMap.containsKey(filterMapKey))
			{
				for (final String category :
					filteredMap.get(filterMapKey).getCategorySet())
				{
					htmlFileMap.addCategoryMethodPair(category, methodName,
						filterMapKey);
				}
			}
		}
		return finalImplementationsGroupMap.size();
	}

	/**
	 * Write all the methods and extends methods to file.
	 * @param outputPath
	 * 		The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for documentation and
	 *        data files.
	 * @param synchronizer
	 *		The {@linkplain StacksSynchronizer} used to control the creation
	 * 		of Stacks documentation
	 * @param runtime
	 *        An {@linkplain AvailRuntime runtime}.
	 * @param htmlFileMap
	 * 		A map for all htmlFiles in stacks
	 * @param templateFilePath
	 * 		The path of the template file used to wrap the generated HTML
	 * @param implementationProperties
	 * 		The file path location of the HTML properties used to generate
	 * 		the bulk of the inner html of the implementations.
	 */
	public void writeMethodsToHTMLFiles(final Path outputPath,
		final StacksSynchronizer synchronizer, final AvailRuntime runtime,
		final HTMLFileMap htmlFileMap, final Path templateFilePath,
		final Path implementationProperties)
	{
		for (final String implementationName :
			finalImplementationsGroupMap.keySet())
		{
			final String htmlTemplate =
				HTMLBuilder.getOuterHTMLTemplate(templateFilePath);

			final String [] htmlSplitTemplate = htmlTemplate
				.split("IMPLEMENTATION-GROUP");

			finalImplementationsGroupMap.get(implementationName)
				.toHTML(outputPath, implementationName,
					htmlSplitTemplate[0], htmlSplitTemplate[1], synchronizer,
					runtime, htmlFileMap, implementationProperties, 3);
		}
	}

	/**
	 * @param numberOfTabs
	 * 		the number of tabs to insert into the string.
	 * @return
	 * 		a String consisting of the number of tabs requested in
	 * 		in numberOfTabs.
	 */
	public String tabs(final int numberOfTabs)
	{
		final StringBuilder stringBuilder = new StringBuilder();
		for (int i = 1; i <= numberOfTabs; i++)
		{
			stringBuilder.append('\t');
		}
		return stringBuilder.toString();
	}
}
