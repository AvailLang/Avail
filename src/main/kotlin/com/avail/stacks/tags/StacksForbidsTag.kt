/*
 * StacksForbidsTag.kt
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

package com.avail.stacks.tags

import com.avail.stacks.LinkingFileMap
import com.avail.stacks.StacksErrorLog
import com.avail.stacks.tokens.AbstractStacksToken
import com.avail.stacks.tokens.QuotedStacksToken
import com.avail.utility.json.JSONWriter

/**
 * The "@forbids" tag in an Avail Class comment.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property arityIndex
 *   The forbids arity index.
 * @property forbidMethods
 *   The list of the methods for which the method is "forbidden" to used in
 *   conjunction with.
 *
 * @constructor
 * Construct a new [StacksForbidsTag].
 * @param arityIndex
 *   The forbids arity index.
 * @param forbidMethods
 *   The list of the methods for which the method is "forbidden" to used in
 *   conjunction with.
 */
class StacksForbidsTag constructor(
		val arityIndex: AbstractStacksToken,
		val forbidMethods: MutableList<QuotedStacksToken>)
	: StacksTag()
{
	/**
	 * Merge two [forbids tags][StacksForbidsTag] of the same arity
	 * @param tag
	 * The [StacksForbidsTag] to merge with
	 */
	fun mergeForbidsTag(tag: StacksForbidsTag)
	{
		forbidMethods.addAll(tag.forbidMethods)
	}

	override fun toJSON(
		linkingFileMap: LinkingFileMap,
		hashID: Int,
		errorLog: StacksErrorLog,
		position: Int,
		jsonWriter: JSONWriter)
	{
		// val rowSize = forbidMethods.size
		jsonWriter.startObject()
		jsonWriter.write("position")
		jsonWriter.write("Argument " + arityIndex.lexeme)
		jsonWriter.write("expressions")
		jsonWriter.startArray()
		for (forbidMethod in forbidMethods)
		{
			val method = forbidMethod.lexeme
			jsonWriter.startArray()
			jsonWriter.write(method)
			jsonWriter.write(linkingFileMap.internalLinks!![method])
			jsonWriter.endArray()
		}
		jsonWriter.endArray()
		jsonWriter.endObject()
	}

	override fun toString(): String
	{
		val sb = StringBuilder("Forbids: ")
		for (i in 0 until forbidMethods.size - 1)
		{
			sb.append(forbidMethods[i].lexeme).append(", ")
		}
		sb.append(forbidMethods[forbidMethods.size - 1].lexeme)
		return sb.toString()
	}
}

