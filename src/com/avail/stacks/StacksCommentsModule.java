/**
 * StacksCommentsModule.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import com.avail.AvailRuntime;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.UnresolvedDependencyException;
import com.avail.compiler.ModuleHeader;
import com.avail.compiler.ModuleImport;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.StringDescriptor;
import com.avail.utility.Pair;
import com.avail.utility.json.JSONWriter;

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
	 * An optional prefix to the stacks file link location in the website
	 */
	private final String linkPrefix;

	/**
	 * @return the exportedNames
	 */
	public HashMap<String, StacksExtendsModule>
		extendedNamesImplementations ()
	{
		return extendedNamesImplementations;
	}

	/**
	 * The name of the file extension being used in this method.
	 */
	private final String fileExtensionName;

	/**
	 * A map of the modules extended by this module to the {@linkplain
	 * StacksExtendsModule module} content.
	 */
	private final HashMap<A_String, HashMap<String,ImplementationGroup>>
		stickyNamesImplementations;

	/**
	 * @return the exportedNames
	 */
	public HashMap<A_String, HashMap<String,ImplementationGroup>>
		stickyNamesImplementations ()
	{
		return stickyNamesImplementations;
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
	private final HashMap<String,
		HashMap<A_String, ArrayList<AbstractCommentImplementation>>>
			usesModuleToImplementedNamesToImplementation;

	/**
	 * @return the usesModuleToImplementedNamesToImplementation
	 */
	public HashMap<String,HashMap<A_String,
		ArrayList<AbstractCommentImplementation>>>
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
	 * A map keyed by exported method names to the file path-name.
	 */
	private final HashMap<A_String, StacksFilename>
		inScopeMethodsToFileNames;

	/**
	 * @return the inScopeMethodsToFileNames
	 */
	public HashMap<A_String, StacksFilename>
		exportedMethodsTofileNames ()
	{
		return inScopeMethodsToFileNames;
	}

	/**
	 *  All the {@linkplain ImplementationGroup named implementations}
	 *  exported out of this module that will be documented if this is
	 *  The outermost {@linkplain StacksCommentsModule module} for the
	 *  generation request.
	 */
	private HashMap<A_String, HashMap<String, ImplementationGroup>>
		finalImplementationsGroupMap;

	/**
	 * A map keyed by a method name with no path to a map keyed by the qualified
	 * module path it is originally named from to the {@linkplain
	 * ImplementationGroup}.  These are all the methods exported from this
	 * {@linkplain StacksCommentsModule module}
	 */
	private final HashMap<A_String, HashMap<String, ImplementationGroup>>
		extendsMethodLeafNameToModuleName;

	/**
	 * @return the extendsMethodLeafNameToModuleName
	 */
	public HashMap<A_String, HashMap<String, ImplementationGroup>>
		extendsMethodLeafNameToModuleName ()
	{
		return extendsMethodLeafNameToModuleName;
	}

	/**
	 * A map keyed by a method name with no path to a map keyed by the qualified
	 * module path it is originally named from to the {@linkplain
	 * ImplementationGroup}.  These are all the methods defined in this
	 * {@linkplain StacksCommentsModule module} and are "public" because
	 * it uses a module where the name is exported from.
	 */
	private final HashMap<A_String, HashMap<String, ImplementationGroup>>
		usesMethodLeafNameToModuleName;

	/**
	 * @return the extendsMethodLeafNameToModuleName
	 */
	public HashMap<A_String, HashMap<String, ImplementationGroup>>
		usesMethodLeafNameToModuleName ()
	{
		return usesMethodLeafNameToModuleName;
	}

	/**
	 * All private methods/classes from this module.
	 */
	private final HashMap<A_String,ImplementationGroup>
		privateCommentImplementations;

	/**
	 * @return the privateCommentImplementations
	 */
	public HashMap<A_String,ImplementationGroup> privateCommentImplementations()
	{
		return privateCommentImplementations;
	}

	/**
	 * Add an implementation to an {@linkplain ImplementationGroup} of the
	 * appropriate module.
	 * @param comment
	 * 		The final parsed {@linkplain AbstractCommentImplementation comment
	 * 		implementation}.
	 */
	void addImplementation(final AbstractCommentImplementation comment)
	{
		final A_String nameToCheck =
			StringDescriptor.from(comment.signature().name());

		if (extendsMethodLeafNameToModuleName.containsKey(nameToCheck))
		{

			if (namedPublicCommentImplementations.containsKey(nameToCheck))
			{
				comment.addToImplementationGroup(
					namedPublicCommentImplementations.get(nameToCheck));
			}

			//Should only be one group in collection, else Avail will
			//throw an error due to ambiguous names.
			for (final ImplementationGroup group :
				extendsMethodLeafNameToModuleName.get(nameToCheck).values())
			{
				comment.addToImplementationGroup(group);
			}

		}
		else if (usesMethodLeafNameToModuleName.containsKey(nameToCheck))
		{
			//Should only be one group in collection, else Avail will
			//throw an error due to ambiguous names.
			for (final ImplementationGroup group :
				usesMethodLeafNameToModuleName.get(nameToCheck).values())
			{
				comment.addToImplementationGroup(group);
			}
		}
		else
		{
			if (!privateCommentImplementations.containsKey(nameToCheck))
			{
				long hashedName = nameToCheck.hash();
				hashedName &= 0xFFFFFFFFL;

				final StacksFilename filename = new StacksFilename(moduleName,
					String.valueOf(hashedName) + "." + fileExtensionName);

				final ImplementationGroup privateGroup =
					new ImplementationGroup(
						nameToCheck, moduleName, filename, true);

				privateCommentImplementations.put(nameToCheck, privateGroup);
			}

			comment.addToImplementationGroup(
				privateCommentImplementations.get(nameToCheck));
		}

		if (comment.isSticky())
		{
			long hashedName = nameToCheck.hash();
			hashedName &= 0xFFFFFFFFL;

			final StacksFilename filename = new StacksFilename(moduleName,
				String.valueOf(hashedName) + "." + fileExtensionName);

			final ImplementationGroup stickyGroup =
				new ImplementationGroup(
					nameToCheck, moduleName, filename, true);
			comment.addToImplementationGroup(stickyGroup);
			stickyGroup.hasStickyComment(true);
			if (!stickyNamesImplementations.keySet()
				.contains(nameToCheck))
			{
				stickyNamesImplementations.put(nameToCheck,
					new HashMap<>(0));
			}
			stickyNamesImplementations.get(nameToCheck)
				.put(comment.signature().module(),stickyGroup);
		}
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
	 * @param linkingFileMap
	 * 		A map for all output files in Stacks
	 * @param linkPrefix
	 * 		An optional prefix to all files' link web links
	 */
	public StacksCommentsModule(
		final ModuleHeader header,
		final A_Tuple commentTokens,
		final StacksErrorLog errorLog,
		final ModuleNameResolver resolver,
		final HashMap<String, StacksCommentsModule> moduleToComments,
		final LinkingFileMap linkingFileMap,
		final String linkPrefix)
	{
		this.moduleName = header.moduleName.qualifiedName();
		this.linkPrefix = linkPrefix;

		this.fileExtensionName = "json";

		this.usesModuleToImplementedNamesToImplementation =
			new HashMap<>();

		this.stickyNamesImplementations =
			new HashMap<>();

		this.privateCommentImplementations =
			new HashMap<>();

		this.namedPublicCommentImplementations =
			new HashMap<>();

		this.extendsMethodLeafNameToModuleName =
			new HashMap<>();

		this.usesMethodLeafNameToModuleName =
			new HashMap<>();

		this.inScopeMethodsToFileNames =
			createFileNames(header.exportedNames, moduleName,
				fileExtensionName);

		for (final StacksCommentsModule comment : moduleToComments.values())
		{
			for (final Entry <A_String,
				HashMap<String, ImplementationGroup>> entry :
				comment.stickyNamesImplementations().entrySet())
			{
				final HashMap<String, ImplementationGroup> localEntry =
					stickyNamesImplementations.get(entry.getKey());
				if (!(localEntry == null))
				{
					localEntry.putAll(entry.getValue());
					stickyNamesImplementations.put(entry.getKey(), localEntry);
				}
				else
				{
					stickyNamesImplementations.put(entry.getKey(),
						entry.getValue());
				}
			}
		}

		for (final A_String implementationName : header.exportedNames)
		{
			final ImplementationGroup group =
				new ImplementationGroup(implementationName, moduleName,
					inScopeMethodsToFileNames.get(implementationName), false);
			this.namedPublicCommentImplementations.put(implementationName,
				group);

			final HashMap<String, ImplementationGroup>
				moduleNameToImplementation =
				new HashMap<>();
			moduleNameToImplementation.put(this.moduleName,
					group);

			this.extendsMethodLeafNameToModuleName
				.put(implementationName, moduleNameToImplementation);
		}

		this.usesNamesImplementations = new HashMap<>();

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
						aToken,moduleName,linkingFileMap);

				if (!(implementation == null))
				{
					addImplementation(implementation);
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
		final HashMap<String,StacksExtendsModule> extendsMap =
			new HashMap<>();

		final HashMap<String,StacksUsesModule> usesMap =
			new HashMap<>();

		for (final ModuleImport moduleImport : header.importedModules)
		{
			try
			{
				final String moduleImportName = resolver.resolve(
					header.moduleName
						.asSibling(moduleImport.moduleName.asNativeString()),
					header.moduleName).qualifiedName();

				if (moduleImport.isExtension)
				{
					A_Set collectedExtendedNames =
						SetDescriptor.empty();

					if (moduleImport.wildcard)
					{
						collectedExtendedNames =
							collectedExtendedNames.setUnionCanDestroy(
								SetDescriptor.fromCollection(
									moduleToComments.get(moduleImportName)
										.extendsMethodLeafNameToModuleName()
											.keySet()),
								true);

						collectedExtendedNames =
							collectedExtendedNames.setUnionCanDestroy(
								SetDescriptor.fromCollection(moduleToComments
									.get(moduleImportName)
										.namedPublicCommentImplementations()
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
					final A_Set removeRenames =
						moduleImport.renames.valuesAsTuple().asSet();

					collectedExtendedNames = collectedExtendedNames
							.setMinusCanDestroy(removeRenames, true);

					collectedExtendedNames =
						collectedExtendedNames.setUnionCanDestroy(
							moduleImport.names, true);

					final StacksExtendsModule stacksExtends =
						new StacksExtendsModule(
							moduleToComments.get(moduleImportName));

					final ArrayList<A_String> renameValues =
						new ArrayList<>();

					for (final A_String rename :
						moduleImport.renames.keysAsSet())
					{
						renameValues.add(rename);
					}

					final HashMap<A_String, StacksFilename>
						renameFileNames =
						createFileNames(renameValues,moduleName,
							fileExtensionName);

					inScopeMethodsToFileNames.putAll(renameFileNames);

					for (final A_String rename :
						moduleImport.renames.keysAsSet())
					{
						stacksExtends.renameImplementation(
							moduleImport.renames.mapAt(rename),
								rename,this, renameFileNames.get(rename),
								!collectedExtendedNames.hasElement(
									moduleImport.renames.mapAt(rename)));
					}

					A_Set removeSet = SetDescriptor.fromCollection(
						stacksExtends.extendsMethodLeafNameToModuleName()
							.keySet());

					removeSet = removeSet.setMinusCanDestroy(
						collectedExtendedNames, true);

					for (final A_String name : removeSet)
					{
						stacksExtends.extendsMethodLeafNameToModuleName()
								.remove(name);
					}

					extendsMap.put(moduleImportName,stacksExtends);
					for (final A_String key :
						stacksExtends.extendsMethodLeafNameToModuleName()
							.keySet())
					{
						final HashMap<A_String,HashMap<String,ImplementationGroup>>
							addOn =
							stacksExtends.extendsMethodLeafNameToModuleName();
						if (extendsMethodLeafNameToModuleName.containsKey(key))
						{
							final HashMap<String, ImplementationGroup>
								modToImplement = addOn.get(key);
							for (final Entry<String, ImplementationGroup>
									stringImplementationGroupEntry
								: modToImplement.entrySet())
							{
								extendsMethodLeafNameToModuleName.get(key).put(
									stringImplementationGroupEntry.getKey(),
									stringImplementationGroupEntry.getValue());
							}
						}
						else
						{
							extendsMethodLeafNameToModuleName.put(key,
								addOn.get(key));
						}
					}

					final ArrayList<Pair<A_String,ImplementationGroup>>
						visibleValues =
						new ArrayList<>();
					for (final A_String name :
						usesMethodLeafNameToModuleName.keySet())
					{
						for (final String module :
							usesMethodLeafNameToModuleName.get(name).keySet())
						{
							visibleValues.add
								(new Pair<>(
									name,
									usesMethodLeafNameToModuleName.get(name)
										.get(module)));
						}
					}

					for (final A_String name :
						extendsMethodLeafNameToModuleName.keySet())
					{
						for (final String module :
							extendsMethodLeafNameToModuleName.get(name).keySet())
						{
							visibleValues.add
								(new Pair<>(
									name,
									extendsMethodLeafNameToModuleName.get(name)
										.get(module)));
						}
					}

					final HashMap<A_String, StacksFilename>
						visibleFileNames =
						createFileNames(visibleValues, fileExtensionName);

					inScopeMethodsToFileNames.putAll(visibleFileNames);
				}
				else
				{
					A_Set collectedUsesNames =
						SetDescriptor.empty();

					if (moduleImport.wildcard)
					{
						collectedUsesNames =
							collectedUsesNames.setUnionCanDestroy(
								SetDescriptor.fromCollection(
									moduleToComments.get(moduleImportName)
										.extendsMethodLeafNameToModuleName()
											.keySet()),
								true);
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
					final A_Set removeRenames =
						moduleImport.renames.valuesAsTuple().asSet();

					collectedUsesNames =
						collectedUsesNames
							.setMinusCanDestroy(removeRenames, true);

					collectedUsesNames =
						collectedUsesNames.setUnionCanDestroy(
							moduleImport.names, true);

					final StacksUsesModule stacksUses =
						new StacksUsesModule(
							moduleToComments.get(moduleImportName),
							moduleImport.renames);

					final ArrayList<A_String> renameValues =
						new ArrayList<>();

					for (final A_String rename :
						moduleImport.renames.keysAsSet())
					{
						renameValues.add(rename);
					}

					final HashMap<A_String, StacksFilename>
						renameFileNames =
						createFileNames(renameValues, moduleName, ".json");

					for (final  A_String rename :
						moduleImport.renames.keysAsSet())
					{
						stacksUses.renameImplementation(
							moduleImport.renames.mapAt(rename),
							rename,this,  renameFileNames.get(rename),
							!collectedUsesNames.hasElement(
								moduleImport.renames.mapAt(rename)));
					}

					A_Set removeSet = SetDescriptor.fromCollection(
						stacksUses.extendsMethodLeafNameToModuleName()
							.keySet());

					removeSet = removeSet.setMinusCanDestroy(
						collectedUsesNames, true);

					for (final A_String name : removeSet)
					{
						stacksUses.extendsMethodLeafNameToModuleName()
								.remove(name);
					}

					usesMap.put(moduleImportName,stacksUses);

					for (final A_String  key :
						stacksUses.extendsMethodLeafNameToModuleName()
							.keySet())
					{
						final HashMap<A_String,HashMap<String,ImplementationGroup>>
							addOn =
								stacksUses.extendsMethodLeafNameToModuleName();
						if (usesMethodLeafNameToModuleName.containsKey(key))
						{
							final HashMap<String, ImplementationGroup>
								modToImplement = addOn.get(key);
							for (final String mod : modToImplement.keySet())
							{
								usesMethodLeafNameToModuleName.get(key)
								.put(mod,modToImplement.get(mod));
							}
						}
						else
						{
							usesMethodLeafNameToModuleName.put(key,
								addOn.get(key));
						}
					}

					final ArrayList<Pair<A_String,ImplementationGroup>>
						visibleValues =
						new ArrayList<>();
					for (final A_String name :
						usesMethodLeafNameToModuleName.keySet())
					{
						for (final String module :
							usesMethodLeafNameToModuleName.get(name).keySet())
						{
							visibleValues.add
								(new Pair<>(
									name,
									usesMethodLeafNameToModuleName.get(name)
										.get(module)));
						}
					}

					for (final A_String name :
						extendsMethodLeafNameToModuleName.keySet())
					{
						for (final String module :
							extendsMethodLeafNameToModuleName.get(name).keySet())
						{
							visibleValues.add
								(new Pair<>(
									name,
									extendsMethodLeafNameToModuleName.get(name)
										.get(module)));
						}
					}

					final HashMap<A_String, StacksFilename>
						visibleFileNames =
						createFileNames(visibleValues, "json");


					inScopeMethodsToFileNames.putAll(visibleFileNames);
				}
			}
			catch (final UnresolvedDependencyException e)
			{
					e.printStackTrace();
			}
		}
		extendedNamesImplementations = extendsMap;
		usesNamesImplementations = usesMap;

		final HashMap<String,Pair<String,ImplementationGroup>>
			namesExtendsImplementationsMap =
			new HashMap<>();

		for (final StacksUsesModule module : usesNamesImplementations.values())
		{
			for (final StacksUsesModule usesModule :
				module.moduleNameToUsesList().values())
			{
				for (final A_String nameToCheck :
					usesModule.implementations().keySet())
				{
					if (usesModule.extendsMethodLeafNameToModuleName()
						.containsKey(nameToCheck))
					{
						final HashMap<String, ImplementationGroup>
							moduleToImplementation =
								usesModule.extendsMethodLeafNameToModuleName()
									.get(nameToCheck);
						for (final String usesModName :
							moduleToImplementation.keySet())
						{
							if (moduleToImplementation.get(usesModName)
								.isPopulated())
							{
								for (final StacksExtendsModule extendsModule :
									extendedNamesImplementations.values())
								{
									//Create map used down below this block
									namesExtendsImplementationsMap.putAll(
										extendsModule.flattenImplementationGroups()
											.first());

									if (extendsModule
										.extendsMethodLeafNameToModuleName()
										.containsKey(nameToCheck))
									{
										/*
										 * In theory this should never loop twice
										 * as there should only ever be one export
										 * name if this were to happen.  If there
										 * were more than one, than Avail will not
										 * parse this as any added implementation
										 * would be ambiguous between the two
										 * names.
										 */
										final HashMap<A_String,HashMap<String,
											ImplementationGroup>>
											extendsLeaves =
											extendsModule
											.extendsMethodLeafNameToModuleName();


										if (
											extendsLeaves
												.containsKey(nameToCheck))
										{
											final HashMap<String,
											ImplementationGroup> extendsLeaf =
												extendsLeaves.get(nameToCheck);

											if (extendsLeaf.containsKey(usesModName))
											{
												extendsLeaf.get(usesModName)
													.mergeWith(
														moduleToImplementation
															.get(usesModName));
											}
										}

									}
								}
							}
						}
					}
				}

				final HashMap<String,Pair<String,ImplementationGroup>>
					namesUsesExtendsImplementationsMap =
					new HashMap<>();

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
					final HashMap<A_String,ImplementationGroup>
						nameToImplementations = usesModules
							.get(extendsModuleName).implementations();

					for (final A_String methodName:
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
						final HashMap<A_String,ImplementationGroup>
							nameToImplementations = usesModules
								.get(otherExtendsModuleName).implementations();

						for (final A_String methodName:
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
	 * Create the String file names for the methods defined in this module.
	 *
	 * @param names A pair of {@linkplain A_String name} and {@linkplain
	 * 		ImplementationGroup group}  that are to have file names constructed
	 * 		for them.
	 * @param fileExtension The string extension the stacks output files should
	 * 		have (e.g. "json")
	 * @return A map of method name to the file name where it will be
	 * 		output into.
	 */
	private static HashMap <A_String, StacksFilename> createFileNames (
		final List<Pair<A_String, ImplementationGroup>> names,
		final String fileExtension)
	{
		final HashMap<A_String,Integer> newHashNameMap =
			new HashMap<>();

		final HashMap <A_String, StacksFilename> namesToFileNames =
			new HashMap<>();

		for (final Pair<A_String,ImplementationGroup> pair : names)
		{
			A_String nameToBeHashed = pair.first();
			if (newHashNameMap.containsKey(pair.first()))
			{
				newHashNameMap.put(pair.first(), newHashNameMap
					.get(pair.first()) + 1);
				nameToBeHashed =
					StringDescriptor.from(pair.first().asNativeString()
						+ newHashNameMap.get(pair.first()));
			}
			else
			{
				newHashNameMap.put(nameToBeHashed, 0);
			}

			long hashedName = nameToBeHashed.hash();
			hashedName &= 0xFFFFFFFFL;
			final String fileName = String.valueOf(hashedName) + "."
				+ fileExtension;

			namesToFileNames.put(pair.first(),
				new StacksFilename(pair.second().namingModule(), fileName));
		}
		return namesToFileNames;
	}

	/**
	 * Create the String file names for the methods defined in this module.
	 * @param names {@linkplain A_String names} that are to have file names
	 * 		constructed for them.
	 * @param originatingModuleName
	 * 		The name of the module the method comes from.
	 * @param fileExtension
	 *		The type of file extension of the output file (e.g. html, json)
	 * @return A map of method name to the file name of the output.
	 */
	private static HashMap <A_String, StacksFilename> createFileNames (
		final Collection<A_String> names, final String originatingModuleName,
		final String fileExtension)
	{
		final HashMap<A_String,Integer> newHashNameMap =
			new HashMap<>();

		final HashMap <A_String, StacksFilename> namesToFileNames =
			new HashMap<>();

		for (final A_String key : names)
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
			hashedName &= 0xFFFFFFFFL;
			final String fileName = String.valueOf(hashedName) + "."
				+ fileExtension;

			namesToFileNames.put(key,
				new StacksFilename(originatingModuleName, fileName));
		}
		return namesToFileNames;
	}


	/**
	 * Acquire all distinct implementations being directly exported or extended
	 * by this module and populate finalImplementationsGroupMap.
	 *
	 * @param linkingFileMap
	 *        A map for all files in stacks
	 * @param outputPath
	 *        The path where the files will end up.
	 * @param runtime
	 *        An {@linkplain AvailRuntime runtime}.
	 * @param topLevelLinkFolderPath
	 *        The folder that the Avail documentation sits in above the
	 *        providedDocumentPath.
	 * @return The number of files to be created
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	public int calculateFinalImplementationGroupsMap(
			final LinkingFileMap linkingFileMap,
			final Path outputPath,
			final AvailRuntime runtime,
			final String topLevelLinkFolderPath)
		throws IOException
	{
		final HashMap<A_String, HashMap<String, ImplementationGroup>>
			filteredMap =
			new HashMap<>();

		final HashMap<String,ImplementationGroup> notPopulated =
			new HashMap<>();

		int fileCount = 0;

		final HashMap<A_String, HashMap<String, ImplementationGroup>>
		ambiguousMethodFileMap = new
			HashMap<>();

		for (final A_String key : extendsMethodLeafNameToModuleName.keySet())
		{
			final HashMap<String, ImplementationGroup> tempMap =
				extendsMethodLeafNameToModuleName.get(key);

			final HashMap<String, ImplementationGroup> stickyMap =
				stickyNamesImplementations.get(key);

			/* If there is a sticky implementation group, force it into
			 * the filteredMap for exporting.  If a duplicate file is created,
			 * the documentation should be updated. */
			if (!(stickyMap == null))
			{
				tempMap.putAll(stickyMap);
			}

			if (tempMap.size() > 1)
			{
				ambiguousMethodFileMap.put(key, tempMap);
			}

			for (final String modName : tempMap.keySet())
			{
				final ImplementationGroup implementation = tempMap.get(modName);
				if (tempMap.get(modName).isPopulated())
				{
					if (filteredMap.containsKey(key))
					{
						filteredMap.get(key).put(modName, implementation);
					}
					else
					{
						final HashMap<String, ImplementationGroup>
						modToImplement =
							new HashMap<>();
						modToImplement.put(modName, implementation);
						filteredMap.put(key,modToImplement);
					}

					for (final String category : implementation.categories())
					{
						linkingFileMap.addCategoryMethodPair(category,
							key.asNativeString(),
							implementation.filepath().relativeFilePath());
					}

					if (tempMap.size() == 1)
					{
						linkingFileMap.addNamedFileLinks(key.asNativeString(),
							linkPrefix
							+ implementation.filepath().relativeFilePath());
					}

					fileCount++;
				}
				else
				{
					notPopulated.put(modName + "/" + key.asNativeString(),
						tempMap.get(modName));
				}
			}
		}

		/* Stage all documents that are sticky but not exported
		 * to be written to a file.*/
		for (final A_String key : stickyNamesImplementations.keySet())
		{
			final HashMap<String, ImplementationGroup> tempMap =
				stickyNamesImplementations.get(key);

			final HashMap<String, ImplementationGroup> exportMap =
				extendsMethodLeafNameToModuleName.get(key);

			if (exportMap == null)
			{
				if (tempMap.size() > 1)
				{
					ambiguousMethodFileMap.put(key, tempMap);
				}

				for (final String modName : tempMap.keySet())
				{
					final ImplementationGroup implementation =
						tempMap.get(modName);
					if (tempMap.get(modName).isPopulated())
					{
						if (filteredMap.containsKey(key))
						{
							filteredMap.get(key).put(modName, implementation);
						}
						else
						{
							final HashMap<String, ImplementationGroup>
							modToImplement =
								new HashMap<>();
							modToImplement.put(modName, implementation);
							filteredMap.put(key,modToImplement);
						}

						for (final String category : implementation.categories())
						{
							linkingFileMap.addCategoryMethodPair(category,
								key.asNativeString(),
								implementation.filepath().relativeFilePath());
						}

						if (tempMap.size() == 1)
						{
							linkingFileMap.addNamedFileLinks(
								key.asNativeString(),
								linkPrefix
								+ implementation.filepath().relativeFilePath());
						}

						fileCount++;
					}
					else
					{
						notPopulated.put(modName + "/" + key.asNativeString(),
							tempMap.get(modName));
					}
				}
			}
		}

		finalImplementationsGroupMap = filteredMap;

		//Write ambiguous files
		final int ambiguousFileCount = ambiguousMethodFileMap.size();

		if (ambiguousFileCount > 0)
		{
			final StacksSynchronizer synchronizer =
				new StacksSynchronizer(ambiguousFileCount);

			writeAmbiguousMethodsJSONFiles(outputPath,synchronizer,
				runtime, ambiguousMethodFileMap,
				linkingFileMap);

			synchronizer.waitForWorkUnitsToComplete();
		}
		writeAmbiguousAliasJSONFiles(outputPath,
			runtime, ambiguousMethodFileMap, topLevelLinkFolderPath,
			linkingFileMap);

		return fileCount;
	}

	/**
	 * Write all the methods and extends methods to file.
	 *
	 * @param outputPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for documentation and
	 *        data files.
	 * @param synchronizer
	 *        The {@linkplain StacksSynchronizer} used to control the creation
	 *        of Stacks documentation
	 * @param runtime
	 *        An {@linkplain AvailRuntime runtime}.
	 * @param linkingFileMap
	 *        A map for all files in stacks.
	 * @param errorLog
	 *        The file for outputting all errors.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	public void writeMethodsToJSONFiles(
			final Path outputPath,
			final StacksSynchronizer synchronizer,
			final AvailRuntime runtime,
			final LinkingFileMap linkingFileMap,
			final StacksErrorLog errorLog)
		throws IOException
	{
		final StringBuilder newLogEntry = new StringBuilder()
			.append("<h3>Internal Link Errors</h3>\n")
			.append("<ol>\n");

		final ByteBuffer errorBuffer = ByteBuffer.wrap(
			newLogEntry.toString().getBytes(StandardCharsets.UTF_8));
		errorLog.addLogEntry(errorBuffer,0);

		for (final A_String implementationName :
			finalImplementationsGroupMap.keySet())
		{
			final HashMap<String, ImplementationGroup> tempImplementationMap =
				finalImplementationsGroupMap.get(implementationName);

			for (final String modulePath : tempImplementationMap.keySet())
			{
				final ImplementationGroup implementation =
					tempImplementationMap.get(modulePath);
				implementation.toJSON(outputPath, synchronizer, runtime,
					linkingFileMap, implementationName.asNativeString(),
					errorLog);
			}
		}

		final StringBuilder closeLogEntry = new StringBuilder()
			.append("</ol>\n");

		final ByteBuffer closeErrorBuffer = ByteBuffer.wrap(
			closeLogEntry.toString().getBytes(StandardCharsets.UTF_8));
		errorLog.addLogEntry(closeErrorBuffer,0);
	}

	/**
	 * Write all the methods and extends methods to file.
	 *
	 * @param outputPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for documentation and
	 *        data files.
	 * @param synchronizer
	 *        The {@linkplain StacksSynchronizer} used to control the creation
	 *        of Stacks documentation
	 * @param runtime
	 *        An {@linkplain AvailRuntime runtime}.
	 * @param ambiguousMethodFileMap
	 *        The map of ambiguous names requiring ambiguous files.
	 * @param linkingFileMap
	 *        A map for all files in Stacks
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private void writeAmbiguousMethodsJSONFiles (
		final Path outputPath,
		final StacksSynchronizer synchronizer,
		final AvailRuntime runtime,
		final HashMap<A_String, HashMap<String, ImplementationGroup>>
			ambiguousMethodFileMap,
		final LinkingFileMap linkingFileMap)
		throws IOException
	{
		final HashMap<String,String> internalLinks =
			new HashMap<>();

		final Path outputFolder = outputPath
			.resolve("_Ambiguities");

		final String linkLocation = linkPrefix + "/_Ambiguities";

		//Get aliases from ambiguous implementations
		for (final A_String key :
			ambiguousMethodFileMap.keySet())
		{
			final HashMap<String, ImplementationGroup> tempImplementationMap =
				ambiguousMethodFileMap.get(key);

			for (final String modulePath : tempImplementationMap.keySet())
			{
				final ImplementationGroup group =
					tempImplementationMap.get(modulePath);

				if (group.isPopulated())
				{
					for (final String alias : group.aliases())
					{
						internalLinks.put(alias, linkLocation
							+ group.filepath().relativeFilePath());
					}
				}
			}
		}

		for (final A_String key :
			ambiguousMethodFileMap.keySet())
		{
			final HashMap<String, ImplementationGroup> tempImplementationMap =
				ambiguousMethodFileMap.get(key);

			final HashSet<Pair<String, String>> ambiguousLinks =
				new HashSet<>();

			if (linkingFileMap.aliasesToFileLink()
				.containsKey(key.asNativeString()))
			{
				for (final String link : linkingFileMap.aliasesToFileLink()
					.get(key.asNativeString()))
				{
					ambiguousLinks.add(new Pair<>(link, link));
				}
			}

			//Create ambiguous link list
			final HashSet<String> ambiguousNoLinks = new HashSet<>();
			for (final String modulePath : tempImplementationMap.keySet())
			{
				final ImplementationGroup group =
					tempImplementationMap.get(modulePath);

				if (group.isPopulated())
				{
					ambiguousLinks.add(new Pair<>(
						linkPrefix
							+ group.filepath().relativeFilePath(),
						group.namingModule()));
				}
				else
				{
					ambiguousNoLinks.add(group.filepath().pathName());
				}
			}

			final JSONWriter jsonWriter = new JSONWriter();
			jsonWriter.startObject();
			jsonWriter.write("type");
			jsonWriter.write("ambiguous");
			jsonWriter.write("files");
			jsonWriter.startArray();

			for (final Pair<String, String> link : ambiguousLinks)
			{
				jsonWriter.startObject();
				jsonWriter.write("link");
				jsonWriter.write(link.first());
				jsonWriter.write("module");
				jsonWriter.write(link.second());
				jsonWriter.endObject();
			}

			for (final String noLink : ambiguousNoLinks)
			{
				jsonWriter.startObject();
				jsonWriter.write("link");
				jsonWriter.write("");
				jsonWriter.write("module");
				jsonWriter.write(noLink);
				jsonWriter.endObject();
			}

			jsonWriter.endArray();
			jsonWriter.endObject();

			final String fileName =
				(key.hash() & 0xFFFFFFFFL) + "." + fileExtensionName;

			internalLinks.put(key.asNativeString(),
				linkLocation + "/"  + fileName);

			final StacksOutputFile linkingFile = new StacksOutputFile(
				outputFolder, synchronizer, fileName,
				runtime, key.asNativeString());

			linkingFile
				.write(jsonWriter.toString());
			jsonWriter.close();
		}

			linkingFileMap.internalLinks(internalLinks);
	}

	/**
	 * Write all the methods and extends methods to file.
	 *
	 * @param outputPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for documentation and
	 *        data files.
	 * @param runtime
	 *        An {@linkplain AvailRuntime runtime}.
	 * @param ambiguousMethodFileMap
	 *        The map of ambiguous names requiring ambiguous files.
	 * @param topLevelLinkFolderPath
	 *        The folder that the Avail documentation sits in above the
	 *        providedDocumentPath.
	 * @param linkingFileMap
	 *        A map for all files in Stacks
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private void writeAmbiguousAliasJSONFiles (
			final Path outputPath,
			final AvailRuntime runtime,
			final HashMap<A_String, HashMap<String, ImplementationGroup>>
				ambiguousMethodFileMap,
			final String topLevelLinkFolderPath,
			final LinkingFileMap linkingFileMap)
		throws IOException
	{

		final HashMap<String,String> internalLinks =
			new HashMap<>();

		final Path outputFolder = outputPath
			.resolve("library-documentation/_Ambiguities");

		final HashMap<String,HashSet<String>> tempAmbiguousAliasMap =
			new HashMap<>();

		for (final String key : linkingFileMap.namedFileLinks().keySet())
		{
			if (!linkingFileMap.aliasesToFileLink().containsKey(key))
			{
				internalLinks.put(key, linkingFileMap.namedFileLinks().get(key));
			}
			else
			{
				tempAmbiguousAliasMap.put(key,
					linkingFileMap.aliasesToFileLink().get(key));

				tempAmbiguousAliasMap.get(key)
					.add(linkingFileMap.namedFileLinks().get(key));

				if (tempAmbiguousAliasMap.get(key).size() == 1)
				{
					internalLinks.put(key,
						linkingFileMap.namedFileLinks().get(key));
					tempAmbiguousAliasMap.remove(key);
				}
			}
		}

		final int additionalAmbiguityFileCount = tempAmbiguousAliasMap.size();

		if (additionalAmbiguityFileCount > 0)
		{
			final StacksSynchronizer ambiguousAliasSynchronizer =
				new StacksSynchronizer(additionalAmbiguityFileCount);

			for (final String ambiguousAliasKey : tempAmbiguousAliasMap.keySet())
			{
				final HashSet<String> ambiguousLinks =
					tempAmbiguousAliasMap.get(ambiguousAliasKey);

				final JSONWriter jsonWriter = new JSONWriter();
				jsonWriter.startObject();
				jsonWriter.write("type");
				jsonWriter.write("ambiguous");
				jsonWriter.write("files");
				//Create ambiguous link list
				jsonWriter.startArray();
				for (final String ambiguousLink : ambiguousLinks)
				{
					jsonWriter.startObject();
					jsonWriter.write("link");
					jsonWriter.write(ambiguousLink);
					jsonWriter.write("module");
					jsonWriter.write(ambiguousLink);
					jsonWriter.endObject();
				}

				jsonWriter.endArray();
				jsonWriter.endObject();
				final String fileName =
					(StringDescriptor.from(ambiguousAliasKey).hash()
							& 0xFFFFFFFFL)
						+ "." + fileExtensionName;

				internalLinks.put(ambiguousAliasKey,
					linkPrefix + "/_Ambiguities/" + fileName);

				final StacksOutputFile jsonFile = new StacksOutputFile(
					outputFolder, ambiguousAliasSynchronizer, fileName,
					runtime, ambiguousAliasKey);

				jsonFile.write(jsonWriter.toString());
				jsonWriter.close();
			}

			ambiguousAliasSynchronizer.waitForWorkUnitsToComplete();
		}

		linkingFileMap.internalLinks().putAll(internalLinks);
	}
}
