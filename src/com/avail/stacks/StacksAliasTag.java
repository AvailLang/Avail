/**
 * StacksAliasTag.java
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

import java.util.List;
import com.avail.utility.json.JSONWriter;

/**
 * An @alias tag for listing Stacks aliases
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksAliasTag extends AbstractStacksTag
{
	/**
	 * The list of the aliases for which the method/type is known by
	 */
	private final List<QuotedStacksToken> aliases;


	/**
	 * Construct a new {@link StacksAliasTag}.
	 * @param aliases
	 *		The list of the aliases for which the method/type is known by
	 */
	public StacksAliasTag (final List<QuotedStacksToken> aliases)
	{
		this.aliases = aliases;
	}

	/**
	 * @return the aliases
	 */
	public List<QuotedStacksToken> aliases ()
	{
		return aliases;
	}

	@Override
	public void toJSON (
		final LinkingFileMap linkingFileMap,
		final int hashID,
		final StacksErrorLog errorLog,
		final int position,
		final JSONWriter jsonWriter)
	{
		jsonWriter.write("aliases");
		jsonWriter.startArray();
		for(final QuotedStacksToken token : aliases)
		{
			jsonWriter.write(
				token.toJSON(linkingFileMap, hashID, errorLog, jsonWriter));
		}
		jsonWriter.endArray();

	}

	@Override
	public String toString ()
	{
		final StringBuilder sb = new StringBuilder("Categories: ");
		for (int i = 0; i < aliases.size() - 1; i++)
		{
			sb.append(aliases.get(i).lexeme()).append(", ");
		}
		sb.append(aliases.get(aliases.size() - 1).lexeme());
		return sb.toString();
	}
}
