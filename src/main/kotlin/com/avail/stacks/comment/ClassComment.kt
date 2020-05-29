/*
 * ClassCommentImplementation.kt
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
import com.avail.descriptor.tuples.StringDescriptor.stringFrom
import com.avail.stacks.CommentGroup
import com.avail.stacks.LinkingFileMap
import com.avail.stacks.StacksDescription
import com.avail.stacks.StacksErrorLog
import com.avail.stacks.comment.signature.CommentSignature
import com.avail.stacks.module.StacksImportModule
import com.avail.stacks.tags.StacksAliasTag
import com.avail.stacks.tags.StacksAuthorTag
import com.avail.stacks.tags.StacksCategoryTag
import com.avail.stacks.tags.StacksFieldTag
import com.avail.stacks.tags.StacksSeeTag
import com.avail.stacks.tags.StacksSuperTypeTag
import com.avail.utility.json.JSONWriter
import java.util.*

/**
 * A comment that describes a particular class.
 *
 * @author Richard Arriaga &lt;Rich@availlang.org&gt;
 *
 * @property signature
 *   The [signature][CommentSignature] of the class/method the comment
 *   describes.
 * @property commentStartLine
 *   The start line in the module the comment being parsed appears.
 * @property sees
 *   A [ArrayList] of any [&quot;@sees&quot;][StacksSeeTag] references.
 * @property description
 *   The overall description of the implementation
 * @property categories
 *   The categories the implementation appears in
 * @property aliases
 *   The aliases the implementation is known by
 * @property supertypes
 *   The [ArrayList] of the class's [supertypes][StacksSuperTypeTag]
 * @property fields
 *   The [ArrayList] of the class's [fields][StacksFieldTag]
 *
 * @constructor
 * Construct a new [ClassComment].
 *
 * @param signature
 *   The [signature][CommentSignature] of the class/method the comment
 *   describes.
 * @param commentStartLine
 *   The start line in the module the comment being parsed appears.
 * @param author
 *   The [author][StacksAuthorTag] of the implementation.
 * @param sees
 *   A [ArrayList] of any [&quot;@sees&quot;][StacksSeeTag] references.
 * @param description
 *   The overall description of the implementation
 * @param categories
 *   The categories the implementation appears in
 * @param aliases
 *   The aliases the implementation is known by
 * @param supertypes
 *   The [ArrayList] of the class's [supertypes][StacksSuperTypeTag]
 * @param fields
 *   The [ArrayList] of the class's [fields][StacksFieldTag]
 * @param sticky
 *   Whether or not the method should be documented regardless of visibility
 */
class ClassComment constructor (
		signature: CommentSignature,
		commentStartLine: Int,
		author: ArrayList<StacksAuthorTag>,
		sees: ArrayList<StacksSeeTag>,
		description: StacksDescription,
		categories: ArrayList<StacksCategoryTag>,
		aliases: ArrayList<StacksAliasTag>,
		private val supertypes: ArrayList<StacksSuperTypeTag>,
		internal val fields: ArrayList<StacksFieldTag>, sticky: Boolean)
	: AvailComment(
			signature, commentStartLine, author, sees, description, categories,
			aliases, sticky)
{
	/** The hash id for this implementation */
	private val hashID: Int = stringFrom(signature.name).hash()

	override fun addToImplementationGroup(
		commentGroup: CommentGroup)
	{
		commentGroup.classImplementation(this)
	}

	override fun addImplementationToImportModule(
		name: A_String, importModule: StacksImportModule)
	{
		//Do nothing as new implementations can't be introduced for classes
	}

	override fun toJSON(
		linkingFileMap: LinkingFileMap,
		nameOfGroup: String,
		errorLog: StacksErrorLog,
		jsonWriter: JSONWriter)
	{
		signature.toJSON(nameOfGroup, isSticky, jsonWriter)
		jsonWriter.write("supertypes")
		jsonWriter.startArray()
		for (supertype in supertypes)
		{
			supertype.toJSON(linkingFileMap, hashID, errorLog, 1, jsonWriter)
		}
		jsonWriter.endArray()

		if (categories.size > 0)
		{
			categories[0].toJSON(
				linkingFileMap,
				hashID, errorLog, 1, jsonWriter)
		}
		else
		{
			jsonWriter.write("categories")
			jsonWriter.startArray()
			jsonWriter.endArray()
		}

		if (aliases.size > 0)
		{
			aliases[0].toJSON(
				linkingFileMap,
				hashID, errorLog, 1, jsonWriter)
		}
		else
		{
			jsonWriter.write("aliases")
			jsonWriter.startArray()
			jsonWriter.endArray()
		}

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
		jsonWriter.write("fields")
		jsonWriter.startArray()
		var position = 1
		for (fieldTag in fields)
		{
			fieldTag.toJSON(
				linkingFileMap, hashID, errorLog, position++,
				jsonWriter)
		}
		jsonWriter.endArray()
	}

	override fun toString(): String = signature.toString()
}
