/*
 * StacksLinkTag.kt
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
import java.util.*

/**
 * The "@link" tag use in an Avail comment to link to an external web page.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class StacksLinkTag : StacksTag
{
	/**
	 * The external site to be linked to along with any way you want the link
	 * to be represented.  If only the web address is listed, the web address
	 * is what is displayed, otherwise the text following the web address will
	 * be used as the linking text.
	 */
	private val displayLinkTokens: List<AbstractStacksToken>

	/**
	 * The main link.
	 */
	internal val link: QuotedStacksToken

	/**
	 * Construct a new [StacksLinkTag].
	 *
	 * @param displayLinkTokens
	 * The external site to be linked to along with any way you want the
	 * link to be represented.  If only the web address is listed, the web
	 * address is what is displayed, otherwise the text following the web
	 * address will be used as the linking text.
	 * @param link The main link.
	 */
	constructor(
		link: QuotedStacksToken,
		displayLinkTokens: List<AbstractStacksToken>)
	{
		this.link = link
		this.displayLinkTokens = displayLinkTokens
	}

	/**
	 * Construct a new [StacksLinkTag].
	 *
	 * @param link The main link.
	 */
	constructor(link: QuotedStacksToken)
	{
		this.link = link
		this.displayLinkTokens = ArrayList()
	}

	/**
	 * @return the linkTokens
	 */
	fun displayLinkTokens(): List<AbstractStacksToken>
	{
		return displayLinkTokens
	}

	/**
	 * @return the linkTokens
	 */
	fun link(): QuotedStacksToken
	{
		return link
	}

	override fun toJSON(
		linkingFileMap: LinkingFileMap,
		hashID: Int,
		errorLog: StacksErrorLog,
		position: Int,
		jsonWriter: JSONWriter)
	{
		//DO NOTHING AS HANDLED IN BracketStacks
	}

	/**
	 * @param linkingFileMap
	 * @param hashID
	 * @param errorLog
	 * @param jsonWriter
	 * @return
	 */
	fun toJSON(
		linkingFileMap: LinkingFileMap,
		hashID: Int,
		errorLog: StacksErrorLog,
		jsonWriter: JSONWriter): String
	{
		val stringBuilder = StringBuilder()
		stringBuilder
			.append("<a href=")
			.append('"')
			.append(link.toJSON(linkingFileMap, hashID, errorLog, jsonWriter))
			.append('"')
			.append(">")

		if (displayLinkTokens.isEmpty())
		{
			stringBuilder.append(
				link
					.toJSON(linkingFileMap, hashID, errorLog, jsonWriter))
		}
		else
		{
			val linkTokenSize = displayLinkTokens.size
			for (i in 0 until linkTokenSize - 1)
			{
				stringBuilder.append(
					displayLinkTokens[i]
						.toJSON(linkingFileMap, hashID, errorLog, jsonWriter))
					.append(" ")
			}
			stringBuilder.append(
				displayLinkTokens[linkTokenSize - 1]
					.toJSON(linkingFileMap, hashID, errorLog, jsonWriter))
		}

		return stringBuilder
			//.append('<')
			//.append('\\')
			.append("</a>").toString()
	}

	override fun toString(): String
	{
		return "Link: " + link.lexeme
	}
}
