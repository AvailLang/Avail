/*
 * AbstractStacksToken.kt
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

package com.avail.stacks.tokens

import com.avail.stacks.LinkingFileMap
import com.avail.stacks.StacksErrorLog
import com.avail.utility.json.JSONWriter

/**
 * The abstract form of a token in a Stacks comment that has been lexed.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property lexeme
 *   The string to be tokenized.
 * @property lineNumber
 *   The line number where the token occurs/begins
 * @property position
 *   The absolute start position of the token
 * @property startOfTokenLinePosition
 *   The position on the line where the token starts.
 * @property moduleName
 *   The module this token is in.
 * @property isSectionToken
 *   Whether this is a section (§) token.
 *
 * @constructor
 * Construct a new `AbstractStacksToken`.
 *
 * @param lexeme
 *   The string to be tokenized.
 * @param lineNumber
 *   The line number where the token occurs/begins
 * @param position
 *   The absolute start position of the token
 * @param startOfTokenLinePosition
 *   The position on the line where the token starts.
 * @param moduleName
 *   The module this token is in.
 * @param isSectionToken
 *   Whether this is a section (§) token.
 */
abstract class AbstractStacksToken constructor(
	internal val lexeme: String,
	internal val lineNumber: Int,
	internal val position: Int,
	internal val startOfTokenLinePosition: Int,
	internal val moduleName: String,
	val isSectionToken: Boolean)
{
	/**
	 * Provide the token's string representation.
	 *
	 * @return
	 */
	fun quotedLexeme(): String = "\"" + lexeme + '"'.toString()

	/**
	 * Create JSON form of token.
	 *
	 * @param linkingFileMap
	 *   The map of all files in Stacks
	 * @param hashID
	 *   The ID for this implementation
	 * @param errorLog
	 *   The [StacksErrorLog].
	 * @param jsonWriter
	 *   The [writer][JSONWriter] collecting the stacks content.
	 * @return The string form of the JSON representation
	 */
	open fun toJSON(
		linkingFileMap: LinkingFileMap,
		hashID: Int,
		errorLog: StacksErrorLog,
		jsonWriter: JSONWriter): String = lexeme

	override fun toString(): String =
		StringBuilder()
			.append('〖')
			.append(lexeme)
			.append("〗 (class: ")
			.append(this.javaClass.simpleName)
			.append(")")
			.toString()
}
