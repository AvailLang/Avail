/*
 * QuotedStacksToken.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.stacks.tokens

import com.avail.stacks.LinkingFileMap
import com.avail.stacks.StacksErrorLog
import com.avail.utility.json.JSONWriter

/**
 * A stacks token representing a quoted region in the comment.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class QuotedStacksToken
/**
 * Construct a new [QuotedStacksToken].
 *
 * @param string
 * The string to be tokenized.
 * @param lineNumber
 * The line number where the token occurs/begins
 * @param position
 * The absolute start position of the token
 * @param startOfTokenLinePosition
 * The position on the line where the token starts.
 * @param moduleName
 * The name of the module the token is in.
 */
private constructor(
	string: String,
	lineNumber: Int,
	position: Int,
	startOfTokenLinePosition: Int,
	moduleName: String) : RegionStacksToken(
	string,
	lineNumber,
	position,
	startOfTokenLinePosition,
	moduleName,
	'\"',
	'\"')
{

	override fun toJSON(
		linkingFileMap: LinkingFileMap, hashID: Int,
		errorLog: StacksErrorLog, jsonWriter: JSONWriter): String
	{
		return lexeme.replace("<", "&lt;")
	}

	companion object
	{

		/**
		 * Tokenize a quoted string.
		 *
		 * @param string
		 *   The string to be tokenized.
		 * @param lineNumber
		 *   The line number where the token occurs/begins
		 * @param position
		 *   The absolute start position of the token
		 * @param startOfTokenLinePosition
		 *   The position on the line where the token starts.
		 * @param moduleName
		 *   The name of the module the token is in.
		 * @return
		 *   A new [stacks&#32;token][QuotedStacksToken]
		 */
		fun create(
			string: String,
			lineNumber: Int,
			position: Int,
			startOfTokenLinePosition: Int,
			moduleName: String): QuotedStacksToken
		{
			return QuotedStacksToken(
				string,
				lineNumber,
				position,
				startOfTokenLinePosition,
				moduleName)
		}
	}
}
