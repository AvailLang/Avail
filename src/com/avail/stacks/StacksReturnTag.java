/**
 * StacksReturnTag.java
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
 * The "@returns" component of an Avail comment.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksReturnTag extends AbstractStacksTag
{
	/**
	 * The type of the return
	 */
	final private RegionStacksToken returnType;

	/**
	 * The description of the return.
	 */
	final private StacksDescription returnDescription;

	/**
	 * Construct a new {@link StacksReturnTag}.
	 * @param returnType
	 * 		The type of the return
	 * @param returnDescription
	 * 		The description of the return.
	 *
	 */
	public StacksReturnTag (
		final RegionStacksToken returnType,
		final StacksDescription returnDescription)
	{
		this.returnType = returnType;
		this.returnDescription = returnDescription;
	}

	/**
	 * @return the returnDescription
	 */
	public StacksDescription returnDescription ()
	{
		return returnDescription;
	}

	/**
	 * @return the returnType
	 */
	public RegionStacksToken returnType ()
	{
		return returnType;
	}

	@Override
	public void toJSON (
		final LinkingFileMap linkingFileMap,
		final int hashID,
		final StacksErrorLog errorLog,
		final int position,
		final JSONWriter jsonWriter)
	{
		jsonWriter.write("returns");
		jsonWriter.startArray();
			jsonWriter.write(
				returnType.toJSON(linkingFileMap, hashID, errorLog, jsonWriter));
			returnDescription.toJSON(linkingFileMap, hashID, errorLog,
				jsonWriter);
			jsonWriter.write(
				linkingFileMap.internalLinks().get(returnType.lexeme()));
		jsonWriter.endArray();

	}
}
