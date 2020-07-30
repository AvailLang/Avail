/*
 * StacksImportModule.kt
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

import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.stacks.CommentGroup
import com.avail.stacks.StacksFilename
import com.avail.stacks.comment.GrammaticalRestrictionComment
import com.avail.stacks.comment.MacroComment
import com.avail.stacks.comment.MethodComment
import com.avail.stacks.comment.SemanticRestrictionComment
/**
 * A grouping of all implementationGroups originating from the names section of
 * this module that this is being imported by another module.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property moduleName
 *   The name of the module
 * @property commentGroups
 *   The a map of [implementationGroups][CommentGroup] keyed by the
 *   implementation name.
 * @property moduleNameToExtendsList
 *   A map of module names to other [modules][StacksExtendsModule] extended by
 *   this module.
 * @property extendsMethodLeafNameToModuleName
 *   A map keyed by exported method names with no path to the qualified module
 *   path it is originally named from.
 * @property moduleNameToUsesList
 *   A map of module names to other [modules][StacksUsesModule] used by this
 *   module.
 * @property usesMethodLeafNameToModuleName
 *   A map keyed by visible (uses) method names with no path to the qualified
 *   module path it is originally named from.
 *
 * @constructor
 * Construct a new `StacksImportModule`.
 *
 * @param moduleName
 *   The name of the module
 * @param commentGroups
 *   The a map of [implementationGroups][CommentGroup] keyed by the
 *   implementation name.
 * @param moduleNameToExtendsList
 *   A map of module names to other [modules][StacksExtendsModule] extended by
 *   this module.
 * @param extendsMethodLeafNameToModuleName
 *   A map keyed by exported method names with no path to the qualified module
 *   path it is originally named from.
 * @param moduleNameToUsesList
 *   A map of module names to other [modules][StacksUsesModule] used by this
 *   module.
 * @param usesMethodLeafNameToModuleName
 *   A map keyed by visible (uses) method names with no path to the qualified
 *   module path it is originally named from.
 */
abstract class StacksImportModule constructor(
	val moduleName: String,
	val commentGroups: MutableMap<A_String, CommentGroup>,
	val moduleNameToExtendsList: MutableMap<String, StacksExtendsModule>,
	val extendsMethodLeafNameToModuleName:
		MutableMap<A_String, MutableMap<String, CommentGroup>>,
	val moduleNameToUsesList: MutableMap<String, StacksUsesModule>,
	val usesMethodLeafNameToModuleName:
		MutableMap<A_String, MutableMap<String, CommentGroup>>)
{
	/**
	 * @param key
	 *   The name of the method used as a key to look up ImplementationGroup in
	 *   the map.
	 * @param implementation
	 *   The method implementation to add.
	 */
	abstract fun addMethodImplementation(
		key: A_String,
		implementation: MethodComment)

	/**
	 * @param key
	 *   The name of the method used as a key to look up ImplementationGroup in
	 *   the map.
	 * @param implementation
	 *   The method implementation to add.
	 */
	abstract fun addMacroImplementation(
		key: A_String,
		implementation: MacroComment)

	/**
	 * @param key
	 *   The name of the method used as a key to look up ImplementationGroup in
	 *   the map.
	 * @param implementation
	 *   The semantic restriction implementation to add.
	 */
	abstract fun addSemanticImplementation(
		key: A_String,
		implementation: SemanticRestrictionComment)

	/**
	 * @param key
	 *   The name of the method used as a key to look up ImplementationGroup in
	 *   the map.
	 * @param implementation
	 *   The grammatical restriction implementation to add.
	 */
	abstract fun addGrammaticalImplementation(
		key: A_String,
		implementation: GrammaticalRestrictionComment)

	/**
	 * @param key
	 *   The name of the class
	 * @param classCommentGroup
	 *   The [CommentGroup] holding a class that is added to
	 *   implementationGroups.
	 */
	abstract fun addClassImplementationGroup(
		key: A_String,
		classCommentGroup: CommentGroup)

	/**
	 * @param key
	 *   The name of the module global variable
	 * @param globalCommentGroup
	 *   The [CommentGroup] holding a module global variable that is
	 *   added to implementationGroups.
	 */
	abstract fun addGlobalImplementationGroup(
		key: A_String,
		globalCommentGroup: CommentGroup)

	/**
	 * Rename an implementation in this Module.
	 *
	 * @param key
	 *   The key and original name of the implementation
	 * @param newName
	 *   The new name to rename the implementation to
	 * @param newlyDefinedModule
	 *   The [module][CommentsModule] where the rename takes place
	 * @param newFileName
	 *   The filename for this new group
	 * @param deleteOriginal
	 *   Is the original name to be deleted?
	 */
	abstract fun renameImplementation(
		key: A_String,
		newName: A_String,
		newlyDefinedModule: CommentsModule,
		newFileName: StacksFilename,
		deleteOriginal: Boolean)

	/**
	 * Create a new map from implementationGroups with new keys using the
	 * method qualified name
	 *
	 * @return
	 * A map with keyed by the method qualified name to the implementation.
	 */
	fun qualifiedImplementationNameToImplementation()
		: Pair<MutableMap<String, Pair<String, CommentGroup>>, MutableMap<String, String>>
	{
		val newMap = mutableMapOf<String, Pair<String, CommentGroup>>()
		val newHashNameMap = mutableMapOf<A_String, Int>()
		val nameToLinkMap = mutableMapOf<String, String>()
		for ((name, value) in commentGroups)
		{
			var nameToBeHashed = name
			if (newHashNameMap.containsKey(name))
			{
				newHashNameMap[name] = newHashNameMap[name]!! + 1
				nameToBeHashed = stringFrom(
					name.asNativeString() + newHashNameMap[name])
			}
			else
			{
				newHashNameMap[nameToBeHashed] = 0
			}
			var hashedName = nameToBeHashed.hash().toLong()
			hashedName = hashedName and 0xFFFFFFFFL
			val qualifiedName = (moduleName + "/"
				+ hashedName + ".json")
			newMap[qualifiedName] = Pair(name.asNativeString(), value)
			nameToLinkMap[name.asNativeString()] = qualifiedName
		}
		return Pair(newMap, nameToLinkMap)
	}

	/**
	 * Flatten out moduleNameToExtendsList map so that all modules in tree
	 * are in one flat map keyed by the qualified method name to the
	 * implementation.  Pair this with a map of method name to html link.
	 * @return
	 * A pair with both maps.
	 */
	fun flattenImplementationGroups(): Pair<MutableMap<String, Pair<String, CommentGroup>>, MutableMap<String, String>>
	{
		val newMap = mutableMapOf<String, Pair<String, CommentGroup>>()
		val nameToLinkMap = mutableMapOf<String, String>()
		for (extendsModule in moduleNameToExtendsList.values)
		{
			val pair = extendsModule.flattenImplementationGroups()
			newMap.putAll(pair.first)
			nameToLinkMap.putAll(pair.second)
		}
		val aPair = qualifiedImplementationNameToImplementation()
		newMap.putAll(aPair.first)
		nameToLinkMap.putAll(aPair.second)
		return Pair(newMap, nameToLinkMap)
	}
}
