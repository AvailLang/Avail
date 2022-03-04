/*
 * GlobalComment.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import avail.stacks.CommentGroup
import avail.stacks.LinkingFileMap
import avail.stacks.StacksDescription
import avail.stacks.StacksErrorLog
import avail.stacks.comment.signature.CommentSignature
import avail.stacks.comment.signature.MethodCommentSignature
import avail.stacks.module.StacksImportModule
import avail.stacks.tags.StacksAliasTag
import avail.stacks.tags.StacksAuthorTag
import avail.stacks.tags.StacksCategoryTag
import avail.stacks.tags.StacksGlobalTag
import avail.stacks.tags.StacksSeeTag
import org.availlang.json.JSONWriter

/**
 * A module global variable comment
 *
 * @property globalTag
 *   A global module variable comment tag
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new [GlobalComment].
 *
 * @param signature
 *   The [signature][MethodCommentSignature] of the class/method the comment
 *   describes.
 * @param commentStartLine
 *   The start line in the module the comment being parsed appears.
 * @param author
 *   The [author][StacksAuthorTag] of the implementation.
 * @param sees
 *   A list of any [&quot;@sees&quot;][StacksSeeTag] references.
 * @param description
 *   The overall description of the implementation.
 * @param categories
 *   The categories the implementation appears in.
 * @param aliases
 *   The aliases the implementation.
 * @param globalTag
 *   A global module variable comment tag.
 */
class GlobalComment constructor (
	signature: CommentSignature,
	commentStartLine: Int,
	author: List<StacksAuthorTag>,
	sees: List<StacksSeeTag>,
	description: StacksDescription,
	categories: List<StacksCategoryTag>,
	aliases: List<StacksAliasTag>,
	private val globalTag: StacksGlobalTag)
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
	override fun addToImplementationGroup (commentGroup: CommentGroup)
	{
		commentGroup.global(this)
	}

	// Do nothing as globals will never be defined outside of its module.
	override fun addImplementationToImportModule(
		name: A_String, importModule: StacksImportModule) = Unit

	// DO NOTHING AS GLOBALS AREN'T WRITTEN TO DOCUMENTATION
	override fun toJSON(
		linkingFileMap: LinkingFileMap,
		nameOfGroup: String,
		errorLog: StacksErrorLog,
		jsonWriter: JSONWriter) = Unit

	override fun toString(): String = signature.toString()
}
