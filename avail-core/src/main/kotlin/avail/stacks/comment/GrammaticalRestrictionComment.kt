/*
 * GrammaticalRestrictionComment.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.stacks.comment

import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.stacks.CommentGroup
import avail.stacks.LinkingFileMap
import avail.stacks.StacksDescription
import avail.stacks.StacksErrorLog
import avail.stacks.comment.signature.CommentSignature
import avail.stacks.module.StacksImportModule
import avail.stacks.tags.StacksAliasTag
import avail.stacks.tags.StacksAuthorTag
import avail.stacks.tags.StacksCategoryTag
import avail.stacks.tags.StacksForbidsTag
import avail.stacks.tags.StacksSeeTag
import org.availlang.json.JSONWriter

/**
 * A comment implementation of grammatical restrictions
 *
 * @property forbids
 *   The forbids tag contents.
 * @author Richard Arriaga &lt;Rich@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [GrammaticalRestrictionComment].
 *
 * @param signature
 *   The [signature][CommentSignature] of the class/method the comment
 *   describes.
 * @param commentStartLine
 *   The start line in the module the comment being parsed appears.
 * @param author
 *   The [authors][StacksAuthorTag] of the implementation.
 * @param sees
 *   A list of any [@sees][StacksSeeTag] references.
 * @param description
 *   The overall description of the implementation.
 * @param categories
 *   The categories the implementation appears in.
 * @param aliases
 *   The aliases the implementation is known by.
 * @param forbids
 *   The forbids tag contents.
 */
class GrammaticalRestrictionComment constructor(
	signature: CommentSignature,
	commentStartLine: Int,
	author: List<StacksAuthorTag>,
	sees: List<StacksSeeTag>,
	description: StacksDescription,
	categories: List<StacksCategoryTag>,
	aliases: List<StacksAliasTag>,
	private val forbids: MutableMap<Int, StacksForbidsTag>)
: AvailComment(
	signature,
	commentStartLine,
	author,
	sees,
	description,
	categories,
	aliases,
	false)
{

	/**
	 * All modules where Grammatical Restrictions for this method are defined.
	 */
	internal val modules = mutableListOf<String>()

	/** The hash id for this implementation. */
	private val hashID: Int

	init
	{
		this.modules.add(signature.module)
		this.hashID = stringFrom(signature.name).hash()
	}

	override fun addToImplementationGroup(
		commentGroup: CommentGroup)
	{
		commentGroup.addGrammaticalRestriction(this)
	}

	/**
	 * Merge two [GrammaticalRestrictionComment].
	 *
	 * @param implementation
	 *   The [GrammaticalRestrictionComment] to merge with.
	 */
	fun mergeGrammaticalRestrictionImplementations(
		implementation: GrammaticalRestrictionComment)
	{
		modules.add(implementation.signature.module)
		for (arity in implementation.forbids.keys)
		{
			if (forbids.containsKey(arity))
			{
				forbids[arity]?.let {
					implementation.forbids[arity]?.let { v ->
						it.forbidMethods.addAll(v.forbidMethods)
					}
				}
			}
			else
			{
				implementation.forbids[arity]?.let {
					forbids[arity] = it
				}
			}
		}
	}

	override fun addImplementationToImportModule(
		name: A_String, importModule: StacksImportModule)
	{
		importModule.addGrammaticalImplementation(name, this)
	}

	override fun toJSON(
		linkingFileMap: LinkingFileMap,
		nameOfGroup: String,
		errorLog: StacksErrorLog,
		jsonWriter: JSONWriter)
	{
		jsonWriter.write("grammaticalRestrictions")
		jsonWriter.startArray()
		for (arity in forbids.keys)
		{
			forbids[arity]
				?.toJSON(linkingFileMap, hashID, errorLog, 1, jsonWriter)
		}
		jsonWriter.endArray()
	}

	override fun toString(): String = signature.toString()
}

