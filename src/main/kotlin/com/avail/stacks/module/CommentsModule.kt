/*
 * StacksCommentsModule.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.stacks.module

import com.avail.AvailRuntime
import com.avail.builder.ModuleName
import com.avail.builder.ModuleNameResolver
import com.avail.builder.UnresolvedDependencyException
import com.avail.compiler.ModuleHeader
import com.avail.descriptor.maps.A_Map.Companion.keysAsSet
import com.avail.descriptor.maps.A_Map.Companion.mapAt
import com.avail.descriptor.maps.A_Map.Companion.valuesAsTuple
import com.avail.descriptor.sets.A_Set.Companion.hasElement
import com.avail.descriptor.sets.A_Set.Companion.setMinusCanDestroy
import com.avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import com.avail.descriptor.tokens.CommentTokenDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.asSet
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.stacks.CommentGroup
import com.avail.stacks.LinkingFileMap
import com.avail.stacks.StacksErrorLog
import com.avail.stacks.StacksFilename
import com.avail.stacks.StacksOutputFile
import com.avail.stacks.StacksSynchronizer
import com.avail.stacks.comment.AvailComment
import com.avail.stacks.exceptions.StacksCommentBuilderException
import com.avail.stacks.exceptions.StacksScannerException
import com.avail.stacks.scanner.StacksScanner
import com.avail.utility.json.JSONWriter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * A representation of all the fully parsed [comments][CommentTokenDescriptor]
 * in a given module
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property linkPrefix
 *   An optional prefix to the stacks file link location in the website.
 *
 * @constructor
 * Construct a new [CommentsModule].
 *
 * @param header
 *   The [ModuleHeader] of the current file
 * @param commentTokens
 *   A [A_Tuple] of all the comment tokens.
 * @param errorLog
 *   The file for outputting all errors.
 * @param resolver
 *   The [ModuleNameResolver] for resolving module paths.
 * @param moduleToComments
 *   A map of [module&#32;names][ModuleName] to a list of all the method names
 *   exported from said module
 * @param linkingFileMap
 *   A map for all output files in Stacks
 * @param linkPrefix
 *   An optional prefix to all files' link web links
 */
class CommentsModule constructor(
	header: ModuleHeader,
	commentTokens: A_Tuple,
	errorLog: StacksErrorLog,
	resolver: ModuleNameResolver,
	moduleToComments: MutableMap<String, CommentsModule>,
	linkingFileMap: LinkingFileMap,
	private val linkPrefix: String)
{
	/**
	 *  A map of the modules extended by this module to the
	 *  [StacksExtendsModule] content.
	 */
	val extendedNamesImplementations =
		mutableMapOf<String, StacksExtendsModule>()

	/**
	 * The name of the file extension being used in this method.
	 */
	private val fileExtensionName: String = "json"

	/**
	 * A map of the modules extended by this module to the
	 * [comment&#32;group][CommentGroup] content.
	 */
	private val stickyNamesImplementations =
		mutableMapOf<A_String, MutableMap<String, CommentGroup>>()

	/**
	 * A map of the modules used by this module to the [StacksUsesModule]
	 * content.
	 */
	var usesNamesImplementations = mutableMapOf<String, StacksUsesModule>()

	/**
	 * The name of the module that contains these Stacks Comments.
	 */
	val moduleName: String = header.moduleName.qualifiedName

	/**
	 * All public methods/classes from this module.
	 */
	val namedPublicCommentImplementations =
		mutableMapOf<A_String, CommentGroup>()

	/**
	 * A map keyed by exported method names to the file path-name.
	 */
	private val inScopeMethodsToFileNames: MutableMap<A_String, StacksFilename>

	/**
	 * All the [named&#32;implementations][CommentGroup] exported out of this
	 * module that will be documented if this is The outermost
	 * [module][CommentsModule] for the generation request.
	 */
	private var finalImplementationsGroupMap =
		mutableMapOf<A_String, MutableMap<String, CommentGroup>>()

	/**
	 * A map keyed by a method name with no path to a map keyed by the qualified
	 * module path it is originally named from to the [CommentGroup].  These are
	 * all the methods exported from this [module][CommentsModule]
	 */
	val extendsMethodLeafNameToModuleName =
		mutableMapOf<A_String, MutableMap<String, CommentGroup>>()

	/**
	 * A map keyed by a method name with no path to a map keyed by the qualified
	 * module path it is originally named from to the [CommentGroup].
	 * These are all the methods defined in this [module][CommentsModule]
	 * and are "public" because it uses a module where the name is exported
	 * from.
	 */
	val usesMethodLeafNameToModuleName =
		mutableMapOf<A_String, MutableMap<String, CommentGroup>>()

	/**
	 * All private methods/classes from this module.
	 */
	private val privateCommentImplementations =
		mutableMapOf<A_String, CommentGroup>()

	init
	{
		this.inScopeMethodsToFileNames = createFileNames(
			header.exportedNames, moduleName,
			fileExtensionName)

		for (comment in moduleToComments.values)
		{
			for ((key, value) in comment.stickyNamesImplementations)
			{
				val localEntry = stickyNamesImplementations[key]
				if (localEntry !== null)
				{
					localEntry.putAll(value)
					stickyNamesImplementations[key] = localEntry
				}
				else
				{
					stickyNamesImplementations[key] = value
				}
			}
		}

		for (implementationName in header.exportedNames)
		{
			val group = CommentGroup(
				implementationName, moduleName,
				inScopeMethodsToFileNames[implementationName]!!, false)
			this.namedPublicCommentImplementations[implementationName] = group

			val moduleNameToImplementation =
				mutableMapOf<String, CommentGroup>()
			moduleNameToImplementation[this.moduleName] = group

			this.extendsMethodLeafNameToModuleName[implementationName] =
				moduleNameToImplementation
		}

		buildModuleImportMaps(
			header, resolver, moduleToComments)

		populateExtendsFromUsesExtends()

		val errorMessages = StringBuilder()
		var errorCount = 0

		for (aToken in commentTokens)
		{
			try
			{
				val implementation = StacksScanner.processCommentString(
					aToken, moduleName, linkingFileMap)

				if (implementation !== null)
				{
					addImplementation(implementation)
				}
			}
			catch (e: StacksScannerException)
			{
				errorMessages.append(e.message)
				errorCount++
			}
			catch (e: StacksCommentBuilderException)
			{
				errorMessages.append(e.message)
				errorCount++
			}

		}

		if (errorCount > 0)
		{
			val newLogEntry = StringBuilder()
				.append("<h3>")
				.append(header.moduleName.qualifiedName)
				.append(" <em>(")
				.append(errorCount)
				.append(")</em></h3>\n<ol>")
			errorMessages.append("</ol>\n")
			newLogEntry.append(errorMessages)

			val errorBuffer = ByteBuffer.wrap(
				newLogEntry.toString().toByteArray(StandardCharsets.UTF_8))
			errorLog.addLogEntry(errorBuffer, errorCount)
		}
	}

	/**
	 * Add an implementation to an [CommentGroup] of the
	 * appropriate module.
	 *
	 * @param comment
	 *   The final parsed [comment][AvailComment].
	 */
	private fun addImplementation(comment: AvailComment)
	{
		val nameToCheck = stringFrom(comment.signature.name)

		when
		{
			extendsMethodLeafNameToModuleName.containsKey(nameToCheck) ->
			{
				if (namedPublicCommentImplementations.containsKey(nameToCheck))
				{
					comment.addToImplementationGroup(
						namedPublicCommentImplementations[nameToCheck]!!)
				}

				//Should only be one group in collection, else Avail will
				//throw an error due to ambiguous names.
				for (group in extendsMethodLeafNameToModuleName[nameToCheck]!!.values)
				{
					comment.addToImplementationGroup(group)
				}
			}
			usesMethodLeafNameToModuleName.containsKey(nameToCheck) ->
				//Should only be one group in collection, else Avail will
				//throw an error due to ambiguous names.
				for (group in usesMethodLeafNameToModuleName[nameToCheck]!!.values)
				{
					comment.addToImplementationGroup(group)
				}
			else ->
			{
				if (!privateCommentImplementations.containsKey(nameToCheck))
				{
					var hashedName = nameToCheck.hash().toLong()
					hashedName = hashedName and 0xFFFFFFFFL

					val filename = StacksFilename(
						moduleName,
						"$hashedName.$fileExtensionName")

					val privateGroup = CommentGroup(
						nameToCheck, moduleName, filename, true)

					privateCommentImplementations[nameToCheck] = privateGroup
				}

				comment.addToImplementationGroup(
					privateCommentImplementations[nameToCheck]!!)
			}
		}

		if (comment.isSticky)
		{
			var hashedName = nameToCheck.hash().toLong()
			hashedName = hashedName and 0xFFFFFFFFL

			val filename = StacksFilename(
				moduleName,
				"$hashedName.$fileExtensionName")

			val stickyGroup = CommentGroup(
				nameToCheck, moduleName, filename, true)
			comment.addToImplementationGroup(stickyGroup)
			stickyGroup.hasStickyComment(true)
			stickyNamesImplementations
				.getOrPut(nameToCheck) {
					mutableMapOf()
				}[comment.signature.module] = stickyGroup
		}
	}

	/**
	 * Construct all the maps that connects all visible method names to the
	 * modules they come from.  Build all [StacksExtendsModule] and
	 * [StacksUsesModule] for all modules imported by this module.
	 *
	 * @param header
	 *   The [ModuleHeader] of the current file
	 * @param resolver
	 *   The [ModuleNameResolver] for resolving module paths.
	 * @param moduleToComments
	 *   A map of [module&#32;names][ModuleName] to a list of all the method
	 *   names exported from said module
	 */
	private fun buildModuleImportMaps(
		header: ModuleHeader,
		resolver: ModuleNameResolver,
		moduleToComments: MutableMap<String, CommentsModule>)
	{
		val usesMap = mutableMapOf<String, StacksUsesModule>()
		for (moduleImport in header.importedModules)
		{
			try
			{
				val moduleImportName = resolver.resolve(
					header.moduleName
						.asSibling(moduleImport.moduleName.asNativeString()),
					header.moduleName).qualifiedName

				if (moduleImport.isExtension)
				{
					var collectedExtendedNames = emptySet

					if (moduleImport.wildcard)
					{
						collectedExtendedNames =
							collectedExtendedNames.setUnionCanDestroy(
								setFromCollection(
									moduleToComments[moduleImportName]!!
										.extendsMethodLeafNameToModuleName
										.keys),
								true)

						collectedExtendedNames =
							collectedExtendedNames.setUnionCanDestroy(
								setFromCollection(
									moduleToComments[moduleImportName]
										?.namedPublicCommentImplementations
										?.keys ?: emptySet()),
								true)
					}
					if (!moduleImport.excludes.equals(emptySet))
					{
						collectedExtendedNames = collectedExtendedNames
							.setMinusCanDestroy(moduleImport.excludes, true)
					}

					//Determine what keys need to be explicitly removed
					//due to the rename.
					val removeRenames =
						moduleImport.renames.valuesAsTuple().asSet()

					collectedExtendedNames = collectedExtendedNames
						.setMinusCanDestroy(removeRenames, true)

					collectedExtendedNames =
						collectedExtendedNames.setUnionCanDestroy(
							moduleImport.names, true)

					val stacksExtends =
						StacksExtendsModule(
							moduleToComments[moduleImportName]!!)

					val renameValues = mutableListOf<A_String>()

					for (rename in moduleImport.renames.keysAsSet())
					{
						renameValues.add(rename)
					}

					val renameFileNames = createFileNames(
						renameValues, moduleName,
						fileExtensionName)

					inScopeMethodsToFileNames.putAll(renameFileNames)

					for (rename in moduleImport.renames.keysAsSet())
					{
						stacksExtends.renameImplementation(
							moduleImport.renames.mapAt(rename),
							rename,
							this,
							renameFileNames[rename]!!,
							!collectedExtendedNames.hasElement(
								moduleImport.renames.mapAt(rename)))
					}

					var removeSet = setFromCollection(
						stacksExtends.extendsMethodLeafNameToModuleName
							.keys)

					removeSet = removeSet.setMinusCanDestroy(
						collectedExtendedNames, true)

					for (name in removeSet)
					{
						stacksExtends.extendsMethodLeafNameToModuleName
							.remove(name)
					}

					extendedNamesImplementations[moduleImportName] = stacksExtends
					for (key in
						stacksExtends.extendsMethodLeafNameToModuleName.keys)
					{
						val addOn =
							stacksExtends.extendsMethodLeafNameToModuleName
						if (extendsMethodLeafNameToModuleName.containsKey(key))
						{
							val modToImplement = addOn[key]
							for ((key1, value) in modToImplement!!)
							{
								extendsMethodLeafNameToModuleName[key]?.put(
									key1,
									value)
							}
						}
						else
						{
							extendsMethodLeafNameToModuleName[key] =
								addOn[key]!!
						}
					}

					val visibleValues =
						mutableListOf<Pair<A_String, CommentGroup>>()
					for ((name, value) in usesMethodLeafNameToModuleName)
					{
						for ((_, commentGroup) in value)
						{
							visibleValues.add(name to commentGroup)
						}
					}

					for ((name, groupMap) in extendsMethodLeafNameToModuleName)
					{
						for ((_, commentGroup) in groupMap)
						{
							visibleValues.add(name to commentGroup)
						}
					}

					val visibleFileNames =
						createFileNames(visibleValues, fileExtensionName)

					inScopeMethodsToFileNames.putAll(visibleFileNames)
				}
				else
				{
					var collectedUsesNames = emptySet

					if (moduleImport.wildcard)
					{
						collectedUsesNames =
							collectedUsesNames.setUnionCanDestroy(
								setFromCollection(
									moduleToComments[moduleImportName]!!
										.extendsMethodLeafNameToModuleName
										.keys),
								true)
						collectedUsesNames =
							collectedUsesNames.setUnionCanDestroy(
								setFromCollection(
									moduleToComments[moduleImportName]!!
										.namedPublicCommentImplementations
										.keys),
								true)
					}
					if (!moduleImport.excludes.equals(emptySet))
					{
						collectedUsesNames = collectedUsesNames
							.setMinusCanDestroy(moduleImport.excludes, true)
					}

					//Determine what keys need to be explicitly removed
					//due to the rename.
					val removeRenames =
						moduleImport.renames.valuesAsTuple().asSet()

					collectedUsesNames = collectedUsesNames
						.setMinusCanDestroy(removeRenames, true)

					collectedUsesNames = collectedUsesNames.setUnionCanDestroy(
						moduleImport.names, true)

					val stacksUses = StacksUsesModule(
						moduleToComments[moduleImportName]!!,
						moduleImport.renames)

					val renameValues = mutableListOf<A_String>()

					for (rename in moduleImport.renames.keysAsSet())
					{
						renameValues.add(rename)
					}

					val renameFileNames =
						createFileNames(renameValues, moduleName, ".json")

					for (rename in moduleImport.renames.keysAsSet())
					{
						stacksUses.renameImplementation(
							moduleImport.renames.mapAt(rename),
							rename,
							this,
							renameFileNames[rename]!!,
							!collectedUsesNames.hasElement(
								moduleImport.renames.mapAt(rename)))
					}

					var removeSet = setFromCollection(
						stacksUses.extendsMethodLeafNameToModuleName.keys)

					removeSet = removeSet.setMinusCanDestroy(
						collectedUsesNames, true)

					for (name in removeSet)
					{
						stacksUses.extendsMethodLeafNameToModuleName
							.remove(name)
					}

					usesMap[moduleImportName] = stacksUses

					for ((key, value) in
						stacksUses.extendsMethodLeafNameToModuleName)
					{
						if (usesMethodLeafNameToModuleName.containsKey(key))
						{
							for ((mod, cGroup) in value)
							{
								value[mod] = cGroup
							}
						}
						else
						{
							usesMethodLeafNameToModuleName[key] = value
						}
					}

					val visibleValues =
						mutableListOf<Pair<A_String, CommentGroup>>()
					for ((name, cGroupMap) in usesMethodLeafNameToModuleName)
					{
						for ((_, commentGroup) in cGroupMap)
						{
							visibleValues.add(name to commentGroup)
						}
					}

					for ((name, cGroupMap) in extendsMethodLeafNameToModuleName)
					{
						for ((_, commentGroup) in cGroupMap)
						{
							visibleValues.add(name to commentGroup)
						}
					}

					val visibleFileNames =
						createFileNames(visibleValues, "json")

					inScopeMethodsToFileNames.putAll(visibleFileNames)
				}
			}
			catch (e: UnresolvedDependencyException)
			{
				e.printStackTrace()
			}

		}
		usesNamesImplementations = usesMap

		val namesExtendsImplementationsMap =
			mutableMapOf<String, Pair<String, CommentGroup>>()

		for (module in usesNamesImplementations.values)
		{
			for (usesModule in module.moduleNameToUsesList.values)
			{
				for (nameToCheck in usesModule.commentGroups.keys)
				{
					if (usesModule.extendsMethodLeafNameToModuleName
							.containsKey(nameToCheck))
					{
						val moduleToImplementation =
							usesModule.extendsMethodLeafNameToModuleName[nameToCheck]!!
						for (usesModName in moduleToImplementation.keys)
						{
							if (moduleToImplementation[usesModName]!!.isPopulated)
							{
								for (extendsModule in this.extendedNamesImplementations.values)
								{
									//Create map used down below this block
									namesExtendsImplementationsMap.putAll(
										extendsModule.flattenImplementationGroups()
											.first)

									if (extendsModule
											.extendsMethodLeafNameToModuleName
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
										val extendsLeaves = extendsModule
											.extendsMethodLeafNameToModuleName


										if (extendsLeaves
												.containsKey(nameToCheck))
										{
											val extendsLeaf =
												extendsLeaves[nameToCheck]

											if (extendsLeaf!!.containsKey(
													usesModName))
											{
												extendsLeaf[usesModName]!!
													.mergeWith(
														moduleToImplementation[usesModName]!!)
											}
										}
									}
								}
							}
						}
					}
				}

				val namesUsesExtendsImplementationsMap =
					mutableMapOf<String, Pair<String, CommentGroup>>()

				for (usesExtendsModule in
					usesModule.moduleNameToExtendsList.values)
				{
					val first =
						usesExtendsModule.flattenImplementationGroups().first

					for (key in first.keys)
					{
						if (first[key]!!.second.isPopulated)
						{
							namesUsesExtendsImplementationsMap[key] =
								first[key]!!
						}
					}
				}

				for (key in namesUsesExtendsImplementationsMap.keys)
				{
					if (namesExtendsImplementationsMap.containsKey(key))
					{
						namesExtendsImplementationsMap[key]!!.second
							.mergeWith(
								namesUsesExtendsImplementationsMap[key]!!.second)
					}
				}
			}
		}
	}

	/**
	 * Obtain implementations defined in this [module's][CommentsModule]
	 * [usesNamesImplementations] ([uses&#32;modules][StacksUsesModule]) that
	 * are defined in one of this module's [extendedNamesImplementations]
	 * [extends&#32;modules][StacksExtendsModule]) and include them with the
	 * extended names from this module. Additionally, obtain implementations
	 * defined in this [module's][CommentsModule] [extendedNamesImplementations]
	 * [extends&#32;modules][StacksExtendsModule]}) that are defined in one of
	 * this module's other `extendsNamesImplementation`
	 * ([extends&#32;modules][StacksExtendsModule]) and include them with the
	 * extended names from this module.
	 */
	private fun populateExtendsFromUsesExtends()
	{
		val extendedModuleNames = extendedNamesImplementations.keys

		//Start with extended names with implementations in one of the
		//uses modules.
		for (extendsModuleName in extendedModuleNames)
		{
			for (uses in usesNamesImplementations.values)
			{
				val usesModules = uses.moduleNameToUsesList
				if (usesModules.containsKey(extendsModuleName))
				{
					usesModules[extendsModuleName]?.commentGroups?.let {
						for (methodName in it.keys)
						{
							extendedNamesImplementations[extendsModuleName]
								?.let { sem ->
									if (sem.commentGroups.containsKey(methodName))
									{
										sem.commentGroups[methodName]
											?.mergeWith(it[methodName]!!)
									}
							}
						}
					}
				}
			}

			for (otherExtendsModuleName in extendedModuleNames)
			{
				if (otherExtendsModuleName != extendsModuleName)
				{
					val usesModules =
						extendedNamesImplementations[otherExtendsModuleName]
							?.moduleNameToUsesList!!

					if (usesModules.containsKey(otherExtendsModuleName))
					{
						val nameToImplementations =
							usesModules[otherExtendsModuleName]
								?.commentGroups!!
						for (methodName in nameToImplementations.keys)
						{
							extendedNamesImplementations[extendsModuleName]!!
								.commentGroups[methodName]!!
								.mergeWith(nameToImplementations[methodName]!!)
						}
					}
				}
			}
		}
	}

	/**
	 * Create the String file names for the methods defined in this module.
	 *
	 * @param names
	 *   A pair of [A_String] and [CommentGroup] that are to have file names
	 *   constructed for them.
	 * @param fileExtension
	 *   The string extension the stacks output files should have (e.g. "json")
	 * @return A map of method name to the file name where it will be
	 * output into.
	 */
	private fun createFileNames(
		names: List<Pair<A_String, CommentGroup>>,
		fileExtension: String): MutableMap<A_String, StacksFilename>
	{
		val newHashNameMap = mutableMapOf<A_String, Int>()

		val namesToFileNames = mutableMapOf<A_String, StacksFilename>()

		for (pair in names)
		{
			var nameToBeHashed = pair.first
			if (newHashNameMap.containsKey(pair.first))
			{
				newHashNameMap[pair.first] =
					newHashNameMap[pair.first]!! + 1
				nameToBeHashed =
					stringFrom(pair.first.asNativeString() + newHashNameMap[pair.first])
			}
			else
			{
				newHashNameMap[nameToBeHashed] = 0
			}

			var hashedName = nameToBeHashed.hash().toLong()
			hashedName = hashedName and 0xFFFFFFFFL
			val fileName = (hashedName.toString() + "."
				+ fileExtension)

			namesToFileNames[pair.first] =
				StacksFilename(pair.second.namingModule, fileName)
		}
		return namesToFileNames
	}

	/**
	 * Create the String file names for the methods defined in this module.
	 * @param names
	 *   [A_String] that are to have file names constructed for them.
	 * @param originatingModuleName
	 *   The name of the module the method comes from.
	 * @param fileExtension
	 *   The type of file extension of the output file (e.g. html, json)
	 * @return A map of method name to the file name of the output.
	 */
	private fun createFileNames(
		names: Collection<A_String>, originatingModuleName: String,
		fileExtension: String): MutableMap<A_String, StacksFilename>
	{
		val newHashNameMap = mutableMapOf<A_String, Int>()

		val namesToFileNames = mutableMapOf<A_String, StacksFilename>()

		for (key in names)
		{
			var nameToBeHashed = key
			if (newHashNameMap.containsKey(key))
			{
				newHashNameMap[key] = newHashNameMap[key]!! + 1
				nameToBeHashed =
					stringFrom(key.asNativeString() + newHashNameMap[key])
			}
			else
			{
				newHashNameMap[nameToBeHashed] = 0
			}

			var hashedName = nameToBeHashed.hash().toLong()
			hashedName = hashedName and 0xFFFFFFFFL
			val fileName = (hashedName.toString() + "."
				+ fileExtension)

			namesToFileNames[key] =
				StacksFilename(originatingModuleName, fileName)
		}
		return namesToFileNames
	}

	/**
	 * Acquire all distinct implementations being directly exported or extended
	 * by this module and populate finalImplementationsGroupMap.
	 *
	 * @param linkingFileMap
	 *   A map for all files in stacks
	 * @param outputPath
	 *   The path where the files will end up.
	 * @param runtime
	 *   An [runtime][AvailRuntime].
	 * @param topLevelLinkFolderPath
	 *   The folder that the Avail documentation sits in above the
	 *   providedDocumentPath.
	 * @return The number of files to be created
	 * @throws IOException If an [I/O&#32;exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	fun calculateFinalImplementationGroupsMap(
		linkingFileMap: LinkingFileMap,
		outputPath: Path,
		runtime: AvailRuntime,
		topLevelLinkFolderPath: String): Int
	{
		val filteredMap =
			mutableMapOf<A_String, MutableMap<String, CommentGroup>>()

		val notPopulated = mutableMapOf<String, CommentGroup>()

		var fileCount = 0

		val ambiguousMethodFileMap =
			mutableMapOf<A_String, MutableMap<String, CommentGroup>>()

		for ((key, value) in extendsMethodLeafNameToModuleName)
		{
			val stickyMap = stickyNamesImplementations[key]

			/* If there is a sticky implementation group, force it into
			 * the filteredMap for exporting.  If a duplicate file is created,
			 * the documentation should be updated. */
			if (stickyMap !== null)
			{
				value.putAll(stickyMap)
			}

			if (value.size > 1)
			{
				ambiguousMethodFileMap[key] = value
			}

			for (modName in value.keys)
			{
				val implementation = value[modName]
				if (implementation?.isPopulated == true)
				{
					filteredMap[key]?.let {
						it[modName] = implementation
					} ?: {
						val modToImplement =
							mutableMapOf<String, CommentGroup>()
						modToImplement[modName] = implementation
						filteredMap[key] = modToImplement
					}()

					for (category in implementation.categories())
					{
						linkingFileMap.addCategoryMethodPair(
							category,
							key.asNativeString(),
							implementation.filepath!!.relativeFilePath())
					}

					if (value.size == 1)
					{
						linkingFileMap.addNamedFileLinks(
							key.asNativeString(),
							linkPrefix +
								implementation.filepath!!.relativeFilePath())
					}

					fileCount++
				}
				else
				{
					notPopulated[modName + "/" + key.asNativeString()] =
						value[modName]!!
				}
			}
		}

		/* Stage all documents that are sticky but not exported
		 * to be written to a file.*/
		for ((key, value) in stickyNamesImplementations)
		{
			val exportMap = extendsMethodLeafNameToModuleName[key]

			if (exportMap === null)
			{
				if (value.size > 1)
				{
					ambiguousMethodFileMap[key] = value
				}

				for (tempEntry in value)
				{
					val implementation = tempEntry.value
					if (filteredMap.containsKey(key))
					filteredMap[key]?.let {
						it[tempEntry.key] = implementation
					} ?: {
						val modToImplement =
							mutableMapOf<String, CommentGroup>()
						modToImplement[tempEntry.key] = implementation
						filteredMap[key] = modToImplement
					}()

					for (category in implementation.categories())
					{
						linkingFileMap.addCategoryMethodPair(
							category,
							key.asNativeString(),
							implementation.filepath!!.relativeFilePath())
					}

					if (value.size == 1)
					{
						linkingFileMap.addNamedFileLinks(
							key.asNativeString(),
							linkPrefix +
								implementation.filepath!!.relativeFilePath())
					}
					fileCount++
				}
			}
		}

		finalImplementationsGroupMap = filteredMap

		//Write ambiguous files
		val ambiguousFileCount = ambiguousMethodFileMap.size

		if (ambiguousFileCount > 0)
		{
			val synchronizer =
				StacksSynchronizer(ambiguousFileCount)

			writeAmbiguousMethodsJSONFiles(
				outputPath,
				synchronizer,
				runtime,
				ambiguousMethodFileMap,
				linkingFileMap)

			synchronizer.waitForWorkUnitsToComplete()
		}
		writeAmbiguousAliasJSONFiles(
			outputPath,
			runtime,
			ambiguousMethodFileMap,
			topLevelLinkFolderPath,
			linkingFileMap)

		return fileCount
	}

	/**
	 * Write all the methods and extends methods to file.
	 *
	 * @param outputPath
	 *   The [path][Path] to the output directory for documentation and data
	 *   files.
	 * @param synchronizer
	 *   The [StacksSynchronizer] used to control the creation of Stacks
	 *   documentation
	 * @param runtime
	 *   An [runtime][AvailRuntime].
	 * @param linkingFileMap
	 *   A map for all files in stacks.
	 * @param errorLog
	 *   The file for outputting all errors.
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	fun writeMethodsToJSONFiles(
		outputPath: Path,
		synchronizer: StacksSynchronizer,
		runtime: AvailRuntime,
		linkingFileMap: LinkingFileMap,
		errorLog: StacksErrorLog)
	{
		val newLogEntry = "<h3>Internal Link Errors</h3>\n" + "<ol>\n"
		val errorBuffer = ByteBuffer.wrap(
			newLogEntry.toByteArray(StandardCharsets.UTF_8))
		errorLog.addLogEntry(errorBuffer, 0)

		for (implementationName in finalImplementationsGroupMap.keys)
		{
			val tempImplementationMap =
				finalImplementationsGroupMap[implementationName]

			for (modulePath in tempImplementationMap!!.keys)
			{
				val implementation = tempImplementationMap[modulePath]!!
				implementation.toJSON(
					outputPath, synchronizer, runtime,
					linkingFileMap, implementationName.asNativeString(),
					errorLog)
			}
		}

		val closeErrorBuffer = ByteBuffer.wrap(
			"</ol>\n".toByteArray(StandardCharsets.UTF_8))
		errorLog.addLogEntry(closeErrorBuffer, 0)
	}

	/**
	 * Write all the methods and extends methods to file.
	 *
	 * @param outputPath
	 *   The [path][Path] to the output directory for documentation and data
	 *   files.
	 * @param synchronizer
	 *   The [StacksSynchronizer] used to control the creation of Stacks
	 *   documentation
	 * @param runtime
	 *   An [runtime][AvailRuntime].
	 * @param ambiguousMethodFileMap
	 *   The map of ambiguous names requiring ambiguous files.
	 * @param linkingFileMap
	 *   A map for all files in Stacks
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun writeAmbiguousMethodsJSONFiles(
		outputPath: Path,
		synchronizer: StacksSynchronizer,
		runtime: AvailRuntime,
		ambiguousMethodFileMap: Map<A_String, Map<String, CommentGroup>>,
		linkingFileMap: LinkingFileMap)
	{
		val internalLinks = mutableMapOf<String, String>()

		val outputFolder = outputPath
			.resolve("_Ambiguities")

		val linkLocation = "$linkPrefix/_Ambiguities"

		//Get aliases from ambiguous implementations
		for (key in ambiguousMethodFileMap.keys)
		{
			val tempImplementationMap = ambiguousMethodFileMap[key]

			for (modulePath in tempImplementationMap!!.keys)
			{
				tempImplementationMap[modulePath]?.let { group ->
					if (group.isPopulated)
					{
						for (alias in group.aliases)
						{
							internalLinks[alias] =
								linkLocation + group.filepath!!.relativeFilePath()
						}
					}
				}
			}
		}

		for (key in ambiguousMethodFileMap.keys)
		{
			val tempImplementationMap = ambiguousMethodFileMap[key]

			val ambiguousLinks = mutableSetOf<Pair<String, String>>()

			if (linkingFileMap.aliasesToFileLink
					.containsKey(key.asNativeString()))
			{
				for (link in
				linkingFileMap.aliasesToFileLink[key.asNativeString()]!!)
				{
					ambiguousLinks.add(link to link)
				}
			}

			//Create ambiguous link list
			val ambiguousNoLinks = mutableSetOf<String>()
			for (modulePath in tempImplementationMap!!.keys)
			{
				tempImplementationMap[modulePath]?.let {group ->

					if (group.isPopulated)
					{
						ambiguousLinks.add(
							Pair(
								linkPrefix + group.filepath!!.relativeFilePath(),
								group.namingModule))
					}
					else
					{
						ambiguousNoLinks.add(group.filepath!!.pathName)
					}
				}
			}

			val jsonWriter = JSONWriter()
			jsonWriter.startObject()
			jsonWriter.write("type")
			jsonWriter.write("ambiguous")
			jsonWriter.write("files")
			jsonWriter.startArray()

			for (link in ambiguousLinks)
			{
				jsonWriter.startObject()
				jsonWriter.write("link")
				jsonWriter.write(link.first)
				jsonWriter.write("module")
				jsonWriter.write(link.second)
				jsonWriter.endObject()
			}

			for (noLink in ambiguousNoLinks)
			{
				jsonWriter.startObject()
				jsonWriter.write("link")
				jsonWriter.write("")
				jsonWriter.write("module")
				jsonWriter.write(noLink)
				jsonWriter.endObject()
			}

			jsonWriter.endArray()
			jsonWriter.endObject()

			val fileName =
				(key.hash() and 0xFFFFFFFF.toInt()).toString() +
					"." + fileExtensionName

			internalLinks[key.asNativeString()] = "$linkLocation/$fileName"

			val linkingFile = StacksOutputFile(
				outputFolder, synchronizer, fileName,
				runtime, key.asNativeString())

			linkingFile
				.write(jsonWriter.toString())
			jsonWriter.close()
		}

		linkingFileMap.internalLinks(internalLinks)
	}

	/**
	 * Write all the methods and extends methods to file.
	 *
	 * @param outputPath
	 *   The [path][Path] to the output [path][BasicFileAttributes.isDirectory]
	 *   for documentation and data files.
	 * @param runtime
	 *   An [runtime][AvailRuntime].
	 * @param ambiguousMethodFileMap
	 *   The map of ambiguous names requiring ambiguous files.
	 * @param topLevelLinkFolderPath
	 *   The folder that the Avail documentation sits in above the
	 *   providedDocumentPath.
	 * @param linkingFileMap
	 *   A map for all files in Stacks
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun writeAmbiguousAliasJSONFiles(
		outputPath: Path,
		runtime: AvailRuntime,
		@Suppress("UNUSED_PARAMETER")
		ambiguousMethodFileMap:
			MutableMap<A_String, MutableMap<String, CommentGroup>>,
		@Suppress("UNUSED_PARAMETER")
		topLevelLinkFolderPath: String,
		linkingFileMap: LinkingFileMap)
	{

		val internalLinks = mutableMapOf<String, String>()
		val outputFolder = outputPath.resolve(
			"library-documentation/_Ambiguities")
		val tempAmbiguousAliasMap = mutableMapOf<String, MutableSet<String>>()

		for (key in linkingFileMap.namedFileLinks.keys)
		{
			if (!linkingFileMap.aliasesToFileLink.containsKey(key))
			{
				internalLinks[key] = linkingFileMap.namedFileLinks[key]!!
			}
			else
			{
				tempAmbiguousAliasMap[key] =
					linkingFileMap.aliasesToFileLink[key]!!

				tempAmbiguousAliasMap[key]!!
					.add(linkingFileMap.namedFileLinks[key]!!)

				if (tempAmbiguousAliasMap[key]!!.size == 1)
				{
					internalLinks[key] = linkingFileMap.namedFileLinks[key]!!
					tempAmbiguousAliasMap.remove(key)
				}
			}
		}

		val additionalAmbiguityFileCount = tempAmbiguousAliasMap.size

		if (additionalAmbiguityFileCount > 0)
		{
			val ambiguousAliasSynchronizer =
				StacksSynchronizer(additionalAmbiguityFileCount)

			for (ambiguousAliasKey in tempAmbiguousAliasMap.keys)
			{
				val ambiguousLinks = tempAmbiguousAliasMap[ambiguousAliasKey]

				val jsonWriter = JSONWriter()
				jsonWriter.startObject()
				jsonWriter.write("type")
				jsonWriter.write("ambiguous")
				jsonWriter.write("files")
				//Create ambiguous link list
				jsonWriter.startArray()
				for (ambiguousLink in ambiguousLinks!!)
				{
					jsonWriter.startObject()
					jsonWriter.write("link")
					jsonWriter.write(ambiguousLink)
					jsonWriter.write("module")
					jsonWriter.write(ambiguousLink)
					jsonWriter.endObject()
				}

				jsonWriter.endArray()
				jsonWriter.endObject()
				val fileName =
					((stringFrom(ambiguousAliasKey).hash() and 0xFFFFFFFF.toInt())
						.toString() + "." + fileExtensionName)

				internalLinks[ambiguousAliasKey] =
					"$linkPrefix/_Ambiguities/$fileName"

				val jsonFile = StacksOutputFile(
					outputFolder, ambiguousAliasSynchronizer, fileName,
					runtime, ambiguousAliasKey)

				jsonFile.write(jsonWriter.toString())
				jsonWriter.close()
			}

			ambiguousAliasSynchronizer.waitForWorkUnitsToComplete()
		}

		linkingFileMap.internalLinks!!.putAll(internalLinks)
	}
}
