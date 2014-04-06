/**
 * StacksDescription.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

/**
 * A collection of {@linkplain AbstractStacksToken tokens} that make up a
 * comment description.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksDescription
{

	/**
	 * The tokens that make up a description in a comment.
	 */
	final ArrayList<AbstractStacksToken> descriptionTokens;

	/**
	 * Construct a new {@link StacksDescription}.
	 * @param descriptionTokens
	 * 		The tokens that make up a description in a comment.
	 *
	 */
	public StacksDescription (
		final ArrayList<AbstractStacksToken> descriptionTokens)
	{
		this.descriptionTokens = descriptionTokens;
	}

	/**
	 * Create HTML content from the description.
	 * @param htmlFileMap
	 * 		A map for all HTML files in Stacks
	 * @return
	 */
	public String toHTML (final HTMLFileMap htmlFileMap)
	{
		final StringBuilder stringBuilder = new StringBuilder();
		final int listSize = descriptionTokens.size();
		if (listSize > 0)
		{
			for (int i = 0; i < listSize - 1; i++)
			{
				stringBuilder
					.append(descriptionTokens.get(i).toHTML(htmlFileMap))
					.append(" ");
			}
			stringBuilder
				.append(descriptionTokens.get(listSize - 1)
					.toHTML(htmlFileMap));
		}
		return stringBuilder.toString();
	}

}
