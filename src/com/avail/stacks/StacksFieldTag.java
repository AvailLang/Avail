/**
 * StacksFieldTag.java
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
 * The "@field" tag in an Avail Class comment.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksFieldTag extends AbstractStacksTag
{
	/**
	 * The type of the field
	 */
	final private QuotedStacksToken fieldType;

	/**
	 * The name of the field.
	 */
	final private QuotedStacksToken fieldName;

	/**
	 * The description of the field.
	 */
	final private StacksDescription fieldDescription;

	/**
	 * Construct a new {@link StacksFieldTag}.
	 *
	 * @param fieldType
	 *		The type of the field
	 * @param fieldDescription
	 * 		The description of the field.
	 * @param fieldName
	 * 		The name of the field.
	 */
	public StacksFieldTag (
		final QuotedStacksToken fieldName,
		final QuotedStacksToken fieldType,
		final StacksDescription fieldDescription)
	{
		this.fieldDescription = fieldDescription;
		this.fieldName = fieldName;
		this.fieldType = fieldType;
	}

	/**
	 * @return the fieldType
	 */
	public QuotedStacksToken fieldType ()
	{
		return fieldType;
	}

	/**
	 * @return the fieldDescription
	 */
	public StacksDescription fieldDescription ()
	{
		return fieldDescription;
	}

	/**
	 * @return the fieldName
	 */
	public QuotedStacksToken fieldName ()
	{
		return fieldName;
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
			fieldDescription
				.toJSON(linkingFileMap, hashID, errorLog, jsonWriter);
			jsonWriter.write("data");
			jsonWriter.startArray();
				jsonWriter.write(position);
				jsonWriter.write(
					fieldName
						.toJSON(linkingFileMap, hashID, errorLog, jsonWriter));
			jsonWriter.endArray();
			jsonWriter.write("typeInfo");
			jsonWriter.startArray();
				jsonWriter.write(
					fieldType
						.toJSON(linkingFileMap, hashID, errorLog, jsonWriter));
				jsonWriter.write(
					linkingFileMap.internalLinks().get(fieldType.lexeme()));
			jsonWriter.endArray();
		jsonWriter.endObject();
	}
}
