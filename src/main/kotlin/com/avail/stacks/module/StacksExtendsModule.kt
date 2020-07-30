/*
 * StacksExtendsModule.kt
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

import com.avail.descriptor.tuples.A_String
import com.avail.stacks.CommentGroup
import com.avail.stacks.StacksFilename
import com.avail.stacks.comment.GrammaticalRestrictionComment
import com.avail.stacks.comment.MacroComment
import com.avail.stacks.comment.MethodComment
import com.avail.stacks.comment.SemanticRestrictionComment

/**
 * A grouping of all implementationGroups originating from the names section of
 * this module that this is being extended by another module.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class StacksExtendsModule : StacksImportModule
{
	/**
	 * Construct a new [StacksExtendsModule].
	 *
	 * @param moduleImportName
	 *   The name of the module
	 * @param commentGroups
	 *   The a map of [implementationGroups][CommentGroup] keyed by the
	 *   implementation name.
	 * @param moduleNameToExtendsList
	 *   A map of module names to other [modules][StacksExtendsModule] extended
	 *   by this module.
	 * @param methodLeafNameToModuleName
	 *   A map keyed by a method name with no path to the qualified module path
	 *   it is originally named from.
	 * @param moduleNameToUsesList
	 *   A map of module names to other [modules][StacksUsesModule] used by this
	 *   module.
	 * @param usesMethodLeafNameToModuleName
	 *   A map keyed by visible (uses) method names with no path to the
	 *   qualified module path it is originally named from.
	 */
	constructor(
		moduleImportName: String,
		commentGroups: MutableMap<A_String, CommentGroup>,
		moduleNameToExtendsList: MutableMap<String, StacksExtendsModule>,
		methodLeafNameToModuleName: MutableMap<A_String, MutableMap<String, CommentGroup>>,
		moduleNameToUsesList: MutableMap<String, StacksUsesModule>,
		usesMethodLeafNameToModuleName: MutableMap<A_String, MutableMap<String, CommentGroup>>)
			: super(
				moduleImportName,
				commentGroups,
				moduleNameToExtendsList,
				methodLeafNameToModuleName,
				moduleNameToUsesList,
				usesMethodLeafNameToModuleName)

	/**
	 * @param moduleNameToSearch
	 *   The implementation to search for
	 * @return The [StacksExtendsModule] the implementation belongs to.
	 */
	fun getExtendsModule(moduleNameToSearch: String): StacksExtendsModule?
	{
		if (moduleName == moduleNameToSearch)
		{
			return this
		}
		return if (moduleNameToExtendsList.isNotEmpty())
		{
			if (moduleNameToExtendsList.containsKey(moduleNameToSearch))
			{
				moduleNameToExtendsList[moduleNameToSearch]
			}
			else moduleNameToExtendsList
				.values
				.stream()
				.findFirst()
				.map { extendsModule ->
					extendsModule.getExtendsModule(moduleNameToSearch)
				}
				.orElse(null)

		}
		else null
	}

	override fun addMethodImplementation(
		key: A_String,
		implementation: MethodComment)
	{
		commentGroups[key]?.addMethod(implementation)

		//Should only have one group, anything else would be an error in Avail.
		for (group in extendsMethodLeafNameToModuleName[key]!!.values)
		{
			group.addMethod(implementation)
		}
	}

	override fun addMacroImplementation(
		key: A_String,
		implementation: MacroComment)
	{
		commentGroups[key]?.addMacro(implementation)

		//Should only have one group, anything else would be an error in Avail.
		for (group in extendsMethodLeafNameToModuleName[key]!!.values)
		{
			group.addMacro(implementation)
		}
	}

	override fun addSemanticImplementation(
		key: A_String,
		implementation: SemanticRestrictionComment)
	{
		commentGroups[key]?.addSemanticRestriction(implementation)

		//Should only have one group, anything else would be an error in Avail.
		for (group in extendsMethodLeafNameToModuleName[key]!!.values)
		{
			group.addSemanticRestriction(implementation)
		}
	}

	override fun addGrammaticalImplementation(
		key: A_String,
		implementation: GrammaticalRestrictionComment)
	{
		commentGroups[key]?.addGrammaticalRestriction(implementation)

		//Should only have one group, anything else would be an error in Avail.
		for (group in extendsMethodLeafNameToModuleName[key]!!.values)
		{
			group.addGrammaticalRestriction(implementation)
		}
	}

	override fun addClassImplementationGroup(
		key: A_String,
		classCommentGroup: CommentGroup)
	{
		if (commentGroups.containsKey(key))
		{
			commentGroups[key] = classCommentGroup
		}
	}

	override fun addGlobalImplementationGroup(
		key: A_String,
		globalCommentGroup: CommentGroup)
	{
		if (commentGroups.containsKey(key))
		{
			commentGroups[key] = globalCommentGroup
		}
	}

	override fun renameImplementation(
		key: A_String,
		newName: A_String,
		newlyDefinedModule: CommentsModule,
		newFileName: StacksFilename,
		deleteOriginal: Boolean)
	{
		val groupMap = extendsMethodLeafNameToModuleName[key]

		if (groupMap !== null)
		{

			var group = CommentGroup(
				newName,
				newlyDefinedModule.moduleName, newFileName, false)

			for (aGroup in groupMap.values)
			{
				group = CommentGroup(
					aGroup, newFileName,
					newlyDefinedModule.moduleName, newName)
			}
			if (newlyDefinedModule.extendsMethodLeafNameToModuleName
					.containsKey(newName))
			{
				newlyDefinedModule.namedPublicCommentImplementations[newName] =
					group
				newlyDefinedModule.extendsMethodLeafNameToModuleName[newName]
					?.let { it[newlyDefinedModule.moduleName] = group }
			}
			else
			{
				val newMap = mutableMapOf<String, CommentGroup>()

				newMap[newlyDefinedModule.moduleName] = group

				newlyDefinedModule.extendsMethodLeafNameToModuleName[newName] =
					newMap
			}
			if (deleteOriginal)
			{
				extendsMethodLeafNameToModuleName.remove(key)
			}
		}
	}

	/**
	 * Construct a new [StacksExtendsModule] from a [CommentsModule].
	 *
	 * @param module
	 *   The [CommentsModule] to be transformed
	 */
	constructor(
		module: CommentsModule) : super(
		module.moduleName,
		module.namedPublicCommentImplementations.toMutableMap(),
		module.extendedNamesImplementations.toMutableMap(),
		module.extendsMethodLeafNameToModuleName.toMutableMap(),
		module.usesNamesImplementations,
		module.usesMethodLeafNameToModuleName)

	override fun toString(): String = StringBuilder().apply {
		append("<h4>").append(moduleName).append("</h4>").append("<ol>")
		for (key in commentGroups.keys)
		{
			append("<li>").append(key.asNativeString()).append("</li>")
		}
		append("</ol>")

		for (extendsModule in moduleNameToExtendsList.values)
		{
			append(extendsModule)
		}
	}.toString()
}
