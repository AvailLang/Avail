/*
 * AvailComment.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.stacks.comment

import com.avail.descriptor.tuples.A_String
import com.avail.stacks.CommentGroup
import com.avail.stacks.LinkingFileMap
import com.avail.stacks.StacksDescription
import com.avail.stacks.StacksErrorLog
import com.avail.stacks.comment.signature.CommentSignature
import com.avail.stacks.module.StacksImportModule
import com.avail.stacks.tags.StacksAliasTag
import com.avail.stacks.tags.StacksAuthorTag
import com.avail.stacks.tags.StacksCategoryTag
import com.avail.stacks.tags.StacksSeeTag
import com.avail.utility.json.JSONWriter

/**
 * An abstract Avail comment.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property signature
 *   The [signature][CommentSignature] of the class/method the comment
 *   describes.
 * @property commentStartLine
 *   The start line in the module the comment being parsed appears.
 * @property authors
 *   The [author][StacksAuthorTag] of the implementation.
 * @property sees
 *   A [List] of any [&quot;@sees&quot;][StacksSeeTag] references.
 * @property description
 *   The overall description of the implementation
 * @property categories
 *   The categories the implementation appears in
 * @property aliases
 *   The aliases the implementation is known by
 * @property isSticky
 *   Indicates whether or not the comment is sticky
 * @constructor
 * Construct a new [AvailComment].
 *
 * @param signature
 *   The [signature][CommentSignature] of the class/method the comment
 *   describes.
 * @param commentStartLine
 *   The start line in the module the comment being parsed appears.
 * @param authors
 *   The [author][StacksAuthorTag] of the implementation.
 * @param sees
 *   A [List] of any [&quot;@sees&quot;][StacksSeeTag] references.
 * @param description
 *   The overall description of the implementation.
 * @param categories
 *   The categories the implementation appears in.
 * @param aliases
 *   The aliases the implementation is known by.
 * @param isSticky
 *   Indicates whether or not the comment is sticky.
 */
abstract class AvailComment internal constructor(
	internal val signature: CommentSignature,
	internal val commentStartLine: Int,
	internal val authors: List<StacksAuthorTag>,
	internal val sees: List<StacksSeeTag>,
	internal val description: StacksDescription,
	internal val categories: List<StacksCategoryTag>,
	internal val aliases: List<StacksAliasTag>,
	val isSticky: Boolean)
{
	/** A set of category String names for this implementation. */
	@Suppress("unused")
	val categorySet: MutableSet<String>
		get()
		{
			val categorySet = HashSet<String>()
			for (aTag in categories)
			{
				categorySet.addAll(aTag.categorySet)
			}
			return categorySet
		}

	/**
	 * Create a string that is unique to this [AvailComment].
	 *
	 * @return An identifying string.
	 */
	fun identityCheck(): String =
		signature.module + signature.name + commentStartLine

	/**
	 * Add the implementation to the provided group.
	 *
	 * @param commentGroup
	 */
	abstract fun addToImplementationGroup(
		commentGroup: CommentGroup)

	/**
	 * Add the implementation to the provided [StacksImportModule].
	 *
	 * @param name
	 *   Name of the implementation to add to the module.
	 * @param importModule
	 *   The module to add the implementation to
	 */
	abstract fun addImplementationToImportModule(
		name: A_String, importModule: StacksImportModule)

	/**
	 * Create JSON content from implementation.
	 *
	 * @param linkingFileMap
	 *   The map of file linkage
	 * @param nameOfGroup
	 *   The name of the implementation as it is to be displayed
	 * @param errorLog
	 *   The [StacksErrorLog]
	 * @param jsonWriter
	 *   The [writer][JSONWriter] collecting the stacks content.
	 */
	abstract fun toJSON(
		linkingFileMap: LinkingFileMap,
		nameOfGroup: String,
		errorLog: StacksErrorLog,
		jsonWriter: JSONWriter)
}
