/**
 * StacksSeeTag.java
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

/**
 * The "@see" Avail comment tag.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksSeeTag extends AbstractStacksTag
{
	/**
	 * The thing that should be 'seen'
	 */
	final private RegionStacksToken thingToSee;

	/**
	 * Construct a new {@link StacksSeeTag}.
	 *
	 * @param thingToSee
	 * 		The thing that should be 'seen'
	 */
	public StacksSeeTag (
		final RegionStacksToken thingToSee)
	{
		this.thingToSee = thingToSee;
	}

	/**
	 * @return the thingToSee
	 */
	public RegionStacksToken thingToSee ()
	{
		return thingToSee;
	}

	@Override
	public String toHTML (final LinkingFileMap htmlFileMap,
		final int hashID, final StacksErrorLog errorLog, int position)
	{
		final StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("see: ");

		if (thingToSee.lexeme().startsWith("{", 0))
		{
			return stringBuilder
				.append(thingToSee.toHTML(htmlFileMap, hashID, errorLog))
				.toString();
		}
		stringBuilder.append("<a href=\"")
			.append(thingToSee.toHTML(htmlFileMap, hashID, errorLog))
			.append("\">")
			.append(thingToSee.toHTML(htmlFileMap, hashID, errorLog))
			.append("</a>");
		return stringBuilder.toString();
	}

}
