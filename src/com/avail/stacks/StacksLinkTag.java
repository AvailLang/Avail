/**
 * StacksLinkTag.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.stacks;

import java.util.ArrayList;
import java.util.List;
import com.avail.utility.json.JSONWriter;

/**
 * The "@link" tag use in an Avail comment to link to an external web page.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksLinkTag extends AbstractStacksTag
{
	/**
	 * The external site to be linked to along with any way you want the link
	 * to be represented.  If only the web address is listed, the web address
	 * is what is displayed, otherwise the text following the web address will
	 * be used as the linking text.
	 */
	final private List<AbstractStacksToken> displayLinkTokens;

	/**
	 * The main link.
	 */
	final QuotedStacksToken link;

	/**
	 * Construct a new {@link StacksLinkTag}.
	 *
	 * @param displayLinkTokens
	 * 		The external site to be linked to along with any way you want the
	 * 		link to be represented.  If only the web address is listed, the web
	 * 		address is what is displayed, otherwise the text following the web
	 * 		address will be used as the linking text.
	 * @param link The main link.
	 */
	public StacksLinkTag (final QuotedStacksToken link,
		final List<AbstractStacksToken> displayLinkTokens)
	{
		this.link = link;
		this.displayLinkTokens = displayLinkTokens;
	}

	/**
	 * Construct a new {@link StacksLinkTag}.
	 *
	 * @param link The main link.
	 */
	public StacksLinkTag (final QuotedStacksToken link)
	{
		this.link = link;
		this.displayLinkTokens = new ArrayList<AbstractStacksToken>();
	}

	/**
	 * @return the linkTokens
	 */
	public List<AbstractStacksToken> displayLinkTokens ()
	{
		return displayLinkTokens;
	}

	/**
	 * @return the linkTokens
	 */
	public QuotedStacksToken link ()
	{
		return link;
	}

	@Override
	public String toHTML (final LinkingFileMap linkingFileMap,
		final int hashID, final StacksErrorLog errorLog, final int position)
	{
		final StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("<a href=\"")
			.append(link.toHTML(linkingFileMap, hashID, errorLog)).append("\">");

		if (displayLinkTokens.isEmpty())
		{
			stringBuilder.append(link.toHTML(linkingFileMap, hashID, errorLog));
		}
		else
		{
			final int linkTokenSize = displayLinkTokens.size();
			for (int i = 0; i < linkTokenSize - 1; i++)
			{
				stringBuilder.append(displayLinkTokens.get(i)
					.toHTML(linkingFileMap, hashID, errorLog)).append(" ");
			}
			stringBuilder.append(displayLinkTokens.get(linkTokenSize - 1)
				.toHTML(linkingFileMap, hashID, errorLog));
		}

		return stringBuilder.append("</a>").toString();
	}

	@Override
	public void toJSON (
		final LinkingFileMap linkingFileMap,
		final int hashID,
		final StacksErrorLog errorLog,
		final int position,
		final JSONWriter jsonWriter)
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
	public String toJSON (
		final LinkingFileMap linkingFileMap,
		final int hashID,
		final StacksErrorLog errorLog,
		final JSONWriter jsonWriter)
	{
		final StringBuilder stringBuilder = new StringBuilder();
		stringBuilder
			.append("<a href=")
			.append('"')
			.append(link.toJSON(linkingFileMap, hashID, errorLog, jsonWriter))
			.append('"')
			.append(">");

		if (displayLinkTokens.isEmpty())
		{
			stringBuilder.append(link
				.toJSON(linkingFileMap, hashID, errorLog, jsonWriter));
		}
		else
		{
			final int linkTokenSize = displayLinkTokens.size();
			for (int i = 0; i < linkTokenSize - 1; i++)
			{
				stringBuilder.append(displayLinkTokens.get(i)
						.toJSON(linkingFileMap, hashID, errorLog, jsonWriter))
					.append(" ");
			}
			stringBuilder.append(displayLinkTokens.get(linkTokenSize - 1)
				.toJSON(linkingFileMap, hashID, errorLog, jsonWriter));
		}

		return stringBuilder
			//.append('<')
			//.append('\\')
			.append("</a>").toString();
	}
}
