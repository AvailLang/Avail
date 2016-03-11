/**
 * StacksRestrictsTag.java
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

import com.avail.utility.json.JSONWriter;

/**
 * The "@restricts" tag in an Avail comment that represents the meta type of an
 * Avail method's paramater's type as used in a semantic restriction.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksRestrictsTag extends AbstractStacksTag
{
	/**
	 * The type of the method parameter's type.
	 */
	final private QuotedStacksToken paramMetaType;

	/**
	 * Excess tokens of unknown purpose
	 */
	final private StacksDescription description;

	/**
	 * Construct a new {@link StacksRestrictsTag}.
	 *
	 * @param paramMetaType
	 * 		The type of the method parameter's type.
	 * @param description
	 * 		description or restricts?
	 */
	public StacksRestrictsTag (
		final QuotedStacksToken paramMetaType,
		final StacksDescription description)
	{
		this.paramMetaType = paramMetaType;
		this.description = description;
	}

	/**
	 * @return the paramMetaType
	 */
	public QuotedStacksToken paramMetaType ()
	{
		return paramMetaType;
	}

	/**
	 * @return the extraUnspecifiedTokens
	 */
	public StacksDescription description ()
	{
		return description;
	}

	@Override
	public void toJSON (
		final LinkingFileMap linkingFileMap,
		final int hashID,
		final StacksErrorLog errorLog,
		final int position,
		final JSONWriter jsonWriter)
	{
		jsonWriter.startObject();
		jsonWriter.write("description");
		description
			.toJSON(linkingFileMap, hashID, errorLog, jsonWriter);
		jsonWriter.write("typeInfo");
		jsonWriter.startArray();
			jsonWriter.write(
				paramMetaType
					.toJSON(linkingFileMap, hashID, errorLog, jsonWriter));
			jsonWriter.write(
				linkingFileMap.internalLinks().get(paramMetaType.lexeme()));
		jsonWriter.endArray();
	jsonWriter.endObject();
}
}
