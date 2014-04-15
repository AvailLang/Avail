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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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
	 *  All the {@linkplain ImplementationGroup named implementations}
	 *  exported out of this module that will be documented if this is
	 *  The outermost {@linkplain StacksCommentsModule module} for the
	 *  generation request.
	 */
	private HashMap<String,Pair<String,ImplementationGroup>>
		finalImplementationsGroupMap;

	/**
	 * A map keyed by a method name with no path to the qualified module
	 * path it is originally named from.  This list includes all the methods
	 * from the {@linkplain StacksExtendsModule modules} extended by this
	 * module as well as its own names.
	 */
	private final HashMap<String,String> extendsMethodLeafNameToModuleName;

	/**
	 * @return the extendsMethodLeafNameToModuleName
	 */
	public HashMap<String,String> extendsMethodLeafNameToModuleName ()
	{
		return extendsMethodLeafNameToModuleName;
	}

	/**
	 * A map keyed by a method name with no path to the qualified module
	 * path it is originally named from.  This list includes all the methods
	 * from the {@linkplain StacksUsesModule modules} used by this
	 * module.
	 */
	private final HashMap<String,String> usesMethodLeafNameToModuleName;

	/**
	 * @return the extendsMethodLeafNameToModuleName
	 */
	public HashMap<String,String> usesMethodLeafNameToModuleName ()
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
		/*final A_String nameToCheck =
			StringDescriptor.from(name);*/

		if (extendsMethodLeafNameToModuleName.containsKey(name))
		{

			if (namedPublicCommentImplementations.containsKey(name))
			{
				comment.addToImplementationGroup(
					namedPublicCommentImplementations.get(name));
			}
			else
			{
				for (final StacksExtendsModule extendsModule :
					extendedNamesImplementations.values())
				{
					final StacksExtendsModule owningModule = extendsModule
						.getExtendsModuleForImplementationName(name);

					if (owningModule != null)
					{
						comment.addImplementationToImportModule(
							name,owningModule);
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
						.getExtendsModuleNameForImplementationName(name);
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
							name, owningModule);

					comment.addImplementationToImportModule(
						name,originatingModule);
				}
				else if (importModule != null)
				{
					if (importModule.implementations()
						.containsKey(name))
					{
						comment.addImplementationToImportModule(name,
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
								name,
								primaryExtendModule);
						}
					}
				}
				else
				{
					if (!privateCommentImplementations.containsKey(name))
					{
						privateCommentImplementations
							.put(name, new ImplementationGroup(name));
					}

					comment.addToImplementationGroup(
						privateCommentImplementations.get(name));
				}
			}
			else
			{
				if (!privateCommentImplementations.containsKey(name))
				{
					privateCommentImplementations
						.put(name, new ImplementationGroup(name));
				}

				comment.addToImplementationGroup(
					privateCommentImplementations.get(name));
			}
		}
	}

	/**
	 * All private methods/classes from this module.
	 */
	private final HashMap<String,ImplementationGroup>
		privateCommentImplementations;

	/**
	 * @return the privateCommentImplementations
	 */
	public HashMap<String,ImplementationGroup> privateCommentImplementations ()
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

		this.privateCommentImplementations =
			new HashMap<String,ImplementationGroup>();

		this.namedPublicCommentImplementations =
			new HashMap<String,ImplementationGroup>();

		this.extendsMethodLeafNameToModuleName =
			new HashMap<String,String>();

		this.usesMethodLeafNameToModuleName =
			new HashMap<String,String>();

		for (final A_String implementationName : header.exportedNames)
		{
			this.namedPublicCommentImplementations.put(moduleName + "/" +
				implementationName.asNativeString(),
				new ImplementationGroup(implementationName.asNativeString()));

			this.extendsMethodLeafNameToModuleName.put(moduleName + "/" +
				implementationName.asNativeString(), this.moduleName);
		}

		this.usesNamesImplementations = new HashMap<String,StacksUsesModule>();

		buildModuleImportMaps(
			header,resolver, moduleToComments);

		populateExtendsFromUsesExtends();

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
		final HashSet<String> collectedExtendedNames = new HashSet<String>();

		final HashSet<String> collectedUsesNames = new HashSet<String>();

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
						collectedExtendedNames.addAll(
							new HashSet<String>(
								moduleToComments.get(moduleImportName)
									.extendsMethodLeafNameToModuleName()
										.keySet()));

						collectedExtendedNames.addAll(
							new HashSet<String>(moduleToComments
									.get(moduleImportName)
										.namedPublicCommentImplementations()
											.keySet()));
					}
					if (!moduleImport.excludes.equals(SetDescriptor.empty()))
					{
						for (final A_String leafName : moduleImport.excludes)
						{
							final String fullName = moduleImportName + "/" +
								leafName.asNativeString();
							collectedExtendedNames.remove(fullName);
						}
					}

					//Determine what keys need to be explicitly removed
					//due to the rename.
					final A_Set removeRenames =
						moduleImport.renames.valuesAsTuple().asSet();

					for (final A_String removeRename : removeRenames)
					{
						final String fullName = moduleImportName + "/" +
							removeRename.asNativeString();
						collectedExtendedNames.remove(fullName);

					}

					for (final A_String addName : moduleImport.names)
					{
						final String fullName = moduleImportName + "/" +
							addName.asNativeString();
						collectedExtendedNames.add(fullName);
					}

					final StacksExtendsModule stacksExtends =
						new StacksExtendsModule(
							moduleToComments.get(moduleImportName));

					for (final A_String rename :
						moduleImport.renames.keysAsSet())
					{
						final String fullName = moduleName + "/" +
							rename.asNativeString();
						final String fullRename = moduleImportName + "/" +
							moduleImport.renames.mapAt(rename).asNativeString();
						stacksExtends.renameImplementation(fullRename,
							fullName,moduleName);
					}

					final Set<String> removeTheseExtendedMethods =
						new HashSet <String>(stacksExtends
							.extendsMethodLeafNameToModuleName().keySet());

					removeTheseExtendedMethods
						.removeAll(collectedExtendedNames);

					for (final String toRemove : removeTheseExtendedMethods)
					{
						stacksExtends.extendsMethodLeafNameToModuleName()
							.remove(toRemove);
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
						collectedUsesNames.addAll(
							new HashSet<String>(
								moduleToComments.get(moduleImportName)
									.extendsMethodLeafNameToModuleName()
										.keySet()));

						collectedUsesNames.addAll(
							new HashSet<String>(moduleToComments
									.get(moduleImportName)
										.namedPublicCommentImplementations()
											.keySet()));
					}
					if (!moduleImport.excludes.equals(SetDescriptor.empty()))
					{
						for (final A_String leafName : moduleImport.excludes)
						{
							final String fullName = moduleImportName + "/" +
								leafName.asNativeString();
							collectedUsesNames.remove(fullName);
						}
					}

					//Determine what keys need to be explicitly removed
					//due to the rename.
					final A_Set removeRenames =
						moduleImport.renames.valuesAsTuple().asSet();

					for (final A_String removeRename : removeRenames)
					{
						final String fullName = moduleImportName + "/" +
							removeRename.asNativeString();
						collectedUsesNames.remove(fullName);

					}

					for (final A_String addName : moduleImport.names)
					{
						final String fullName = moduleImportName + "/" +
							addName.asNativeString();
						collectedUsesNames.add(fullName);
					}

					final StacksUsesModule stacksUses =
						new StacksUsesModule(
							moduleToComments.get(moduleImportName),
							moduleImport.renames);

					for (final A_String rename :
						moduleImport.renames.keysAsSet())
					{
						final String fullName = moduleName + "/" +
							rename.asNativeString();
						final String fullRename = moduleImportName + "/" +
							moduleImport.renames.mapAt(rename).asNativeString();
						stacksUses.renameImplementation(fullRename,
							fullName,moduleName);
					}

					final Set<String> removeTheseExtendedMethods =
						new HashSet <String>(stacksUses
						.extendsMethodLeafNameToModuleName().keySet());

					removeTheseExtendedMethods
						.removeAll(collectedUsesNames);

					for (final String toRemove : removeTheseExtendedMethods)
					{
						stacksUses.extendsMethodLeafNameToModuleName()
							.remove(toRemove);
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

		final HashMap<String,Pair<String,ImplementationGroup>>
			namesExtendsImplementationsMap =
				new HashMap<String,Pair<String,ImplementationGroup>>();

		for (final StacksUsesModule module : usesNamesImplementations.values())
		{
			for (final StacksUsesModule usesModule :
				module.moduleNameToUsesList().values())
			{
				for (final String nameToCheck :
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

				final HashMap<String,Pair<String,ImplementationGroup>>
					namesUsesExtendsImplementationsMap =
						new HashMap<String,Pair<String,ImplementationGroup>>();

				for (final StacksExtendsModule usesExtendsModule :
					usesModule.moduleNameToExtendsList().values())
				{
					final HashMap<String, Pair<String,ImplementationGroup>>
						first =
						usesExtendsModule.flattenImplementationGroups().first();

					for (final String key : first.keySet())
					{
						if (first.get(key).second().isPopulated())
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
						namesExtendsImplementationsMap.get(key).second()
							.mergeWith(
								namesUsesExtendsImplementationsMap
									.get(key).second());
					}
				}
			}
		}
	}

	/**
	 * Obtain implementations defined in this {@linkplain StacksCommentsModule
	 * module's} usesNamesImplementations ({@linkplain StacksUsesModule uses
	 * modules}) that are defined in one of this module's
	 * extendsNamesImplementation ({@linkplain StacksExtendsModule extends
	 * modules}) and include them with the extended names from this module.
	 * Additionally, obtain implementations defined in this {@linkplain
	 * StacksCommentsModule module's} extendsNamesImplementations ({@linkplain
	 * StacksExtendsModule extends modules}) that are defined in one of this
	 * module's other extendsNamesImplementation ({@linkplain
	 * StacksExtendsModule extends modules}) and include them with the extended
	 * names from this module.
	 */
	private void populateExtendsFromUsesExtends()
	{
		final Set<String> extendedModuleNames =
			extendedNamesImplementations.keySet();

		//Start with extended names with implementations in one of the
		//uses modules.
		for (final String extendsModuleName : extendedModuleNames)
		{
			for (final StacksUsesModule uses :
				usesNamesImplementations.values())
			{
				final HashMap<String,StacksUsesModule> usesModules =
					uses.moduleNameToUsesList();
				if(usesModules.containsKey(extendsModuleName))
				{
					final HashMap<String,ImplementationGroup>
						nameToImplementations = usesModules
							.get(extendsModuleName).implementations();

					for (final String methodName :
						nameToImplementations.keySet())
					{
						if (extendedNamesImplementations.get(extendsModuleName)
							.implementations().containsKey(methodName))
						{
							extendedNamesImplementations.get(extendsModuleName)
								.implementations().get(methodName)
									.mergeWith(nameToImplementations
										.get(methodName));
						}
					}
				}
			}

			for (final String otherExtendsModuleName : extendedModuleNames)
			{
				if (!otherExtendsModuleName.equals(extendsModuleName))
				{
					final HashMap<String,StacksUsesModule> usesModules =
						extendedNamesImplementations.get(otherExtendsModuleName)
							.moduleNameToUsesList();

					if(usesModules.containsKey(otherExtendsModuleName))
					{
						final HashMap<String,ImplementationGroup>
							nameToImplementations = usesModules
								.get(otherExtendsModuleName).implementations();

						for (final String methodName:
							nameToImplementations.keySet())
						{
							extendedNamesImplementations.get(extendsModuleName)
								.implementations().get(methodName)
									.mergeWith(nameToImplementations
										.get(methodName));
						}
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
		final HashMap<String,Pair<String,ImplementationGroup>>
			newMap =
			new HashMap<String,Pair<String,ImplementationGroup>>();

		final HashMap<String,String> nameToLinkMap =
			new HashMap<String,String>();

		for (final StacksExtendsModule extendsModule :
			extendedNamesImplementations.values())
		{
			final Pair<HashMap<String,Pair<String,ImplementationGroup>>,
				HashMap<String,String>> pair =
					extendsModule.flattenImplementationGroups();
			newMap.putAll(pair.first());
			nameToLinkMap.putAll(pair.second());
		}

		final HashMap<A_String,Integer> newHashNameMap =
			new HashMap<A_String,Integer>();

		for (final String key : namedPublicCommentImplementations.keySet())
		{
			A_String nameToBeHashed = StringDescriptor.from(key);
			final A_String availName = StringDescriptor.from(key);
			if (newHashNameMap.containsKey(key))
			{
				newHashNameMap.put(availName, newHashNameMap.get(key) + 1);
				nameToBeHashed =
					StringDescriptor.from(key
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
				+ key;
			newMap.put(qualifiedMethodName, new Pair<String,ImplementationGroup>
				(key,
					namedPublicCommentImplementations.get(key)));
			nameToLinkMap.put(key, qualifiedName);
		}

		final HashMap<String,Pair<String,ImplementationGroup>> filteredMap =
			new HashMap<String,Pair<String,ImplementationGroup>>();

		final HashMap<String,ImplementationGroup> notPopulated =
			new HashMap<String,ImplementationGroup>();

		for (final String key : newMap.keySet())
		{
			if (newMap.get(key).second().isPopulated())
			{
				filteredMap.put(key, newMap.get(key));
			}
			else
			{
				notPopulated.put(newMap.get(key).first(),
					newMap.get(key).second());
			}
		}

		finalImplementationsGroupMap = filteredMap;

		for (final String methodName : nameToLinkMap.keySet())
		{
			final String filterMapKey = nameToLinkMap.get(methodName);
			if (filteredMap.containsKey(filterMapKey))
			{
				for (final String category :
					filteredMap.get(filterMapKey).second().getCategorySet())
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

			final String name =
				finalImplementationsGroupMap.get(implementationName).first();

			finalImplementationsGroupMap.get(implementationName).second()
				.toHTML(outputPath, implementationName,
					htmlSplitTemplate[0], htmlSplitTemplate[1], synchronizer,
					runtime, htmlFileMap, implementationProperties, 3, name);
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
