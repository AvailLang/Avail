/*
 * StacksDescription.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.stacks

import com.avail.stacks.tokens.AbstractStacksToken
import com.avail.utility.json.JSONWriter

/**
 * A collection of [tokens][AbstractStacksToken] that make up a
 * comment description.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property descriptionTokens
 *   The tokens that make up a description in a comment.
 *
 * @constructor
 * Construct a new [StacksDescription].
 *
 * @param descriptionTokens
 *   The tokens that make up a description in a comment.
 */
class StacksDescription constructor(
	private val descriptionTokens: List<AbstractStacksToken>)
{

	/**
	 * Create JSON content from the description
	 *
	 * @param linkingFileMap
	 *   A map for all files in Stacks
	 * @param hashID
	 *   The ID for this implementation
	 * @param errorLog
	 *   The [StacksErrorLog]
	 * @param jsonWriter
	 *   The [writer][JSONWriter] collecting the stacks content.
	 */
	fun toJSON(
		linkingFileMap: LinkingFileMap,
		hashID: Int,
		errorLog: StacksErrorLog,
		jsonWriter: JSONWriter)
	{
		val stringBuilder = StringBuilder()
		val listSize = descriptionTokens.size
		if (listSize > 0)
		{
			for (i in 0 until listSize - 1)
			{
				stringBuilder.append(
					descriptionTokens[i].toJSON(
						linkingFileMap, hashID, errorLog, jsonWriter))

				when (descriptionTokens[i + 1].lexeme)
				{
					".", ",", ":", "?", ";", "!" ->
					{
					}
					else -> stringBuilder.append(" ")
				}
			}
			stringBuilder.append(
				descriptionTokens[listSize - 1].toJSON(
					linkingFileMap, hashID, errorLog, jsonWriter))
		}
		jsonWriter.write(stringBuilder.toString())
	}
}
