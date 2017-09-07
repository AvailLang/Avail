/**
 * StacksForbidsTag.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
 *   may be used to endorse or promote products derived set this software
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

import com.avail.utility.json.JSONWriter;

import java.util.ArrayList;

/**
 * The "@forbids" tag in an Avail Class comment.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksForbidsTag extends AbstractStacksTag
{
	/**
	 * The forbids arity index.
	 */
	private final AbstractStacksToken arityIndex;
	/**
	 * The list of the methods for which the method is "forbidden" to used in
	 * conjunction with.
	 */
	private final ArrayList<QuotedStacksToken> forbidMethods;

	/**
	 * Construct a new {@link StacksForbidsTag}.
	 * @param arityIndex
	 *		The forbids arity index.
	 * @param forbidMethods
	 * 		The list of the methods for which the method is "forbidden" to used
	 * 		in conjunction with.
	 */
	public StacksForbidsTag (final AbstractStacksToken arityIndex,
		final ArrayList<QuotedStacksToken> forbidMethods)
	{
		this.arityIndex = arityIndex;
		this.forbidMethods = forbidMethods;
	}

	/**
	 * @return the forbidMethods
	 */
	public ArrayList<QuotedStacksToken> forbidMethods ()
	{
		return forbidMethods;
	}

	/**
	 * @return the arityIndex
	 */
	public AbstractStacksToken arityIndex ()
	{
		return arityIndex;
	}

	/**
	 * Merge two {@linkplain StacksForbidsTag forbids tags} of the same arity
	 * @param tag
	 * 		The {@linkplain StacksForbidsTag} to merge with
	 */
	public void mergeForbidsTag(final StacksForbidsTag tag)
	{
		forbidMethods.addAll(tag.forbidMethods());
	}

	@Override
	public void toJSON (
		final LinkingFileMap linkingFileMap,
		final int hashID,
		final StacksErrorLog errorLog,
		final int position,
		final JSONWriter jsonWriter)
	{
		final int rowSize = forbidMethods.size();
		jsonWriter.startObject();
		jsonWriter.write("position");
		jsonWriter.write("Argument " + arityIndex.lexeme);
		jsonWriter.write("expressions");
		jsonWriter.startArray();
		for (final QuotedStacksToken forbidMethod : forbidMethods)
		{
			final String method = forbidMethod.lexeme;
			jsonWriter.startArray();
			jsonWriter.write(method);
			jsonWriter.write(linkingFileMap.internalLinks().get(method));
			jsonWriter.endArray();
		}
		jsonWriter.endArray();
		jsonWriter.endObject();
	}

	@Override
	public String toString ()
	{
		final StringBuilder sb = new StringBuilder("Forbids: ");
		for (int i = 0; i < forbidMethods.size() - 1; i++)
		{
			sb.append(forbidMethods.get(i).lexeme()).append(", ");
		}
		sb.append(forbidMethods.get(forbidMethods.size() - 1).lexeme());
		return sb.toString();
	}
}

