/*
 * StacksUsesModule.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.stacks.CommentGroup
import com.avail.stacks.StacksFilename
import com.avail.stacks.comment.GrammaticalRestrictionComment
import com.avail.stacks.comment.MacroComment
import com.avail.stacks.comment.MethodComment
import com.avail.stacks.comment.SemanticRestrictionComment

/**
 * A grouping of all implementationGroups originating from the names section of
 * this module that this is being used by another module.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class StacksUsesModule : StacksImportModule
{

	/**
	 * The [map][MapDescriptor] of renames ([MapDescriptor] → string) explicitly
	 * specified in this import declaration.  The keys are the newly introduced
	 * names and the values are the names provided by the predecessor module.
	 */
	val renames: A_Map

	/**
	 * Construct a new [StacksUsesModule].
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
	 * @param renames
	 *   The [map][MapDescriptor] of renames ([StringDescriptor] → string)
	 *   explicitly specified in this import declaration.  The keys are the
	 *   newly introduced names and the values are the names provided by the
	 *   predecessor module.
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
		usesMethodLeafNameToModuleName: MutableMap<A_String, MutableMap<String, CommentGroup>>,
		renames: A_Map)
		: super(
			moduleImportName,
			commentGroups,
			moduleNameToExtendsList,
			methodLeafNameToModuleName,
			moduleNameToUsesList,
			usesMethodLeafNameToModuleName)
	{
		this.renames = renames
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
		commentGroups[key] = classCommentGroup
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
		var group = CommentGroup(
			key,
			newlyDefinedModule.moduleName, newFileName, false)
		for (aGroup in extendsMethodLeafNameToModuleName[key]!!.values)
		{
			group = CommentGroup(
				aGroup,
				newFileName,
				newlyDefinedModule.moduleName,
				newName)
		}
		if (newlyDefinedModule.usesMethodLeafNameToModuleName
				.containsKey(newName))
		{
			newlyDefinedModule.usesMethodLeafNameToModuleName[newName]?.let {
				it[newlyDefinedModule.moduleName] = group
			}
		}
		else
		{
			val newMap = mutableMapOf<String, CommentGroup>()

			newMap[newlyDefinedModule.moduleName] = group

			newlyDefinedModule.usesMethodLeafNameToModuleName[newName] =
				newMap
		}
		if (deleteOriginal)
		{
			extendsMethodLeafNameToModuleName.remove(key)
		}
	}

	/**
	 * Construct a new `StacksUsesModule` from a [CommentsModule].
	 *
	 * @param module
	 *   The [CommentsModule] to be transformed
	 * @param renamesMap
	 *   The renames [map][A_Map]
	 */
	constructor(
		module: CommentsModule, renamesMap: A_Map) : super(
			module.moduleName,
			module.namedPublicCommentImplementations.toMutableMap(),
			module.extendedNamesImplementations.toMutableMap(),
			module.extendsMethodLeafNameToModuleName.toMutableMap(),
			module.usesNamesImplementations,
			module.usesMethodLeafNameToModuleName)
	{
		this.renames = renamesMap
	}

	override fun toString(): String = StringBuilder().apply {
		append("<h4>").append(moduleName).append("</h4>").append("<ol>")
		for (key in commentGroups.keys)
		{
			append("<li>").append(key.asNativeString()).append("</li>")
		}
		append("</ol>")

		for (usesModule in moduleNameToUsesList.values)
		{
			append(usesModule)
		}
	}.toString()
}

