/*
 * SemanticRestrictionCommentSignature.kt
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

package com.avail.stacks.comment.signature

import com.avail.utility.json.JSONWriter

import java.util.ArrayList

import java.lang.String.format

/**
 * The defining characteristic of a semantic restriction comment as it pertains
 * to the implementation it describes.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class SemanticRestrictionCommentSignature
/**
 * Construct a new [SemanticRestrictionCommentSignature].
 *
 * @param name
 * The name of the class/method the comment describes.
 * @param module
 * The module this implementation appears in.
 * @param orderedInputTypes
 * The semantic restriction parameter input types in order of input
 */
	(
	name: String,
	module: String,
	/**
	 * The method parameter input types in order of input
	 */
	internal val orderedInputTypes: ArrayList<String>) : CommentSignature(
	name,
	module)
{

	override fun toString(): String
	{
		return format(
			"%s -> %s",
			name,
			orderedInputTypes.toString())
	}

	override fun toJSON(
		nameOfGroup: String, sticky: Boolean,
		jsonWriter: JSONWriter)
	{
		jsonWriter.write("source")
		jsonWriter.write(module)
	}
}
