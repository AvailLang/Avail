/*
 * StacksParameterTag.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.stacks.tags

import avail.stacks.LinkingFileMap
import avail.stacks.StacksDescription
import avail.stacks.StacksErrorLog
import avail.stacks.tokens.QuotedStacksToken
import org.availlang.json.JSONWriter

/**
 * The contents of an Avail comment "@param" tag
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class StacksParameterTag
/**
 * Construct a new [StacksParameterTag].
 *
 * @param paramType
 * The type of the parameter
 * @param paramDescription
 * The description of the parameter.
 * @param paramName
 * The name of the parameter variable.
 */
	(
	/**
	 * The name of the parameter variable.
	 */
	private val paramName: QuotedStacksToken,
	/**
	 * The type of the parameter
	 */
	private val paramType: QuotedStacksToken,
	/**
	 * The description of the parameter.
	 */
	private val paramDescription: StacksDescription) : StacksTag()
{

	/**
	 * @return the returnDescription
	 */
	fun paramDescription(): StacksDescription
	{
		return paramDescription
	}

	/**
	 * @return the returnType
	 */
	fun paramType(): QuotedStacksToken
	{
		return paramType
	}

	/**
	 * @return the paramName
	 */
	fun paramName(): QuotedStacksToken
	{
		return paramName
	}

	override fun toJSON(
		linkingFileMap: LinkingFileMap,
		hashID: Int,
		errorLog: StacksErrorLog,
		position: Int,
		jsonWriter: JSONWriter)
	{
		//The element id to link back to the param on the page
		jsonWriter.startObject()
		jsonWriter.write("description")
		paramDescription
			.toJSON(linkingFileMap, hashID, errorLog, jsonWriter)
		jsonWriter.write("elementID")
		val elementID = paramName.lexeme + hashID
		jsonWriter.write(elementID)
		jsonWriter.write("data")
		jsonWriter.startArray()
		jsonWriter.write(position)
		jsonWriter.write(
			paramName
				.toJSON(linkingFileMap, hashID, errorLog, jsonWriter))
		jsonWriter.endArray()
		jsonWriter.write("typeInfo")
		jsonWriter.startArray()
		jsonWriter.write(
			paramType
				.toJSON(linkingFileMap, hashID, errorLog, jsonWriter))
		jsonWriter.write(
			linkingFileMap.internalLinks!![paramType.lexeme])
		jsonWriter.endArray()
		jsonWriter.endObject()
	}
}
