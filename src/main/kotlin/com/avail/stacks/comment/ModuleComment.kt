/*
 * ModuleCommentImplementation.kt
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
import com.avail.stacks.tags.StacksAuthorTag
import com.avail.stacks.tags.StacksSeeTag
import com.avail.utility.json.JSONWriter

/**
 * A comment that describes a particular module
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new [ModuleComment].
 *
 * @param signature
 *   The [signature][CommentSignature] of the class/method the comment
 *   describes.
 * @param commentStartLine
 *   The start line in the module the comment being parsed appears.
 * @param author
 *   The [author][StacksAuthorTag] of the implementation.
 * @param sees
 *   A [List] of any [&quot;@sees&quot;][StacksSeeTag] references.
 * @param description
 *   The overall description of the implementation
 * @param sticky
 *   or not the method should be documented regardless of
 * visibility
 */
class ModuleComment constructor(
	signature: CommentSignature,
	commentStartLine: Int,
	author: MutableList<StacksAuthorTag>,
	sees: MutableList<StacksSeeTag>,
	description: StacksDescription,
	sticky: Boolean)
	: AvailComment(
		signature,
		commentStartLine,
		author,
		sees,
		description,
		mutableListOf(),
		mutableListOf(),
		sticky)
{

	override fun addToImplementationGroup(commentGroup: CommentGroup)
	{
		// Do not use
	}

	override fun addImplementationToImportModule(
		name: A_String, importModule: StacksImportModule)
	{
		// Do not use
	}

	override fun toJSON(
		linkingFileMap: LinkingFileMap,
		nameOfGroup: String,
		errorLog: StacksErrorLog,
		jsonWriter: JSONWriter)
	{
		jsonWriter.startObject()
		jsonWriter.write("module")
		jsonWriter.write(signature.module)
		jsonWriter.write("description")
		description.toJSON(linkingFileMap, 0, errorLog, jsonWriter)
		jsonWriter.write("sees")
		jsonWriter.startArray()
		for (see in sees)
		{
			jsonWriter.write(
				see.thingToSee().toJSON(
					linkingFileMap, 0,
					errorLog, jsonWriter))
		}
		jsonWriter.endArray()
		jsonWriter.write("authors")
		jsonWriter.startArray()
		for (author in authors)
		{
			author.toJSON(
				linkingFileMap, 0, errorLog, commentStartLine,
				jsonWriter)
		}
		jsonWriter.endArray()
		jsonWriter.endObject()
	}

	override fun toString(): String = signature.toString()
}
