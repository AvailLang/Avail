/*
 * SemanticRestrictionCommentImplementation.kt
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

import com.avail.descriptor.A_String
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.stacks.CommentGroup
import com.avail.stacks.LinkingFileMap
import com.avail.stacks.StacksDescription
import com.avail.stacks.StacksErrorLog
import com.avail.stacks.comment.signature.SemanticRestrictionCommentSignature
import com.avail.stacks.module.StacksImportModule
import com.avail.stacks.tags.*
import com.avail.utility.json.JSONWriter
import java.util.*

/**
 * A comment implementation of grammatical restrictions
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class SemanticRestrictionComment
/**
 * Construct a new [SemanticRestrictionComment].
 *
 * @param signature
 * The [signature][SemanticRestrictionCommentSignature] of the
 * class/method the comment describes.
 * @param commentStartLine
 * The start line in the module the comment being parsed appears.
 * @param author
 * The [authors][StacksAuthorTag] of the implementation.
 * @param sees
 * A [ArrayList] of any [&quot;@sees&quot;][StacksSeeTag] references.
 * @param description
 * The overall description of the implementation
 * @param categories
 * The categories the implementation appears in
 * @param aliases
 * The aliases the implementation is known by
 * @param restricts
 * The list of input types in the semantic restriction.
 * @param returnsContent
 * The [&quot;@returns&quot;][StacksReturnTag] content
 */
	(
	signature: SemanticRestrictionCommentSignature,
	commentStartLine: Int,
	author: ArrayList<StacksAuthorTag>,
	sees: ArrayList<StacksSeeTag>,
	description: StacksDescription,
	categories: ArrayList<StacksCategoryTag>,
	aliases: ArrayList<StacksAliasTag>,
	/**
	 * The list of input types in the semantic restriction.
	 */
	internal val restricts: ArrayList<StacksRestrictsTag>,
	/**
	 * The [&quot;@returns&quot;][StacksReturnTag] content
	 */
	internal val returnsContent: ArrayList<StacksReturnTag>) :
	AvailComment(
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
	 * The hash id for this implementation
	 */
	private val hashID: Int

	init
	{

		val concatenatedInputParams = StringBuilder()

		for (param in signature.orderedInputTypes)
		{
			concatenatedInputParams.append(param)
		}

		this.hashID = stringFrom(concatenatedInputParams.toString()).hash()
	}

	override fun addToImplementationGroup(commentGroup: CommentGroup)
	{
		commentGroup.addSemanticRestriction(this)
	}

	override fun addImplementationToImportModule(
		name: A_String, importModule: StacksImportModule)
	{
		importModule.addSemanticImplementation(name, this)
	}

	override fun toJSON(
		linkingFileMap: LinkingFileMap,
		nameOfGroup: String,
		errorLog: StacksErrorLog,
		jsonWriter: JSONWriter)
	{
		jsonWriter.write("type")
		jsonWriter.write("method")
		signature.toJSON(nameOfGroup, isSticky, jsonWriter)

		jsonWriter.write("sees")
		jsonWriter.startArray()
		for (see in sees)
		{
			jsonWriter.write(
				see.thingToSee().toJSON(
					linkingFileMap, hashID,
					errorLog, jsonWriter))
		}
		jsonWriter.endArray()

		jsonWriter.write("description")
		description.toJSON(linkingFileMap, hashID, errorLog, jsonWriter)

		//The ordered position of the parameter in the method signature.
		jsonWriter.write("parameters")
		jsonWriter.startArray()
		var position = 1
		for (restrictTag in restricts)
		{
			restrictTag.toJSON(
				linkingFileMap, hashID, errorLog, position++,
				jsonWriter)
		}
		jsonWriter.endArray()

		if (returnsContent.isNotEmpty())
		{
			returnsContent[0]
				.toJSON(linkingFileMap, hashID, errorLog, 1, jsonWriter)
		}
		else
		{
			jsonWriter.write("returns")
			jsonWriter.startArray()
			jsonWriter.endArray()
		}
	}
}