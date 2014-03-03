/**
 * AvailDocumentationJSONObjectGenerator.java
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * TODO: Document AvailDocumentationJSONObjectGenerator!
 *
 * @author Richard A Arriaga &lt;rich@availlang.org&gt;
 */
public class AvailDocumentationJSONObjectGenerator
{

	/**
	 * Construct a new {@link AvailDocumentationJSONObjectGenerator}.
	 *
	 * @param jsonObjects An array of {@link AvailDocumentationJSONObject} that
	 * 	will make up the JSON file.
	 */
	public AvailDocumentationJSONObjectGenerator (
		final AvailDocumentationJSONObject [] jsonObjects)
	{
		this.jsonObjects = jsonObjects;
	}

	/**
	 * An array of {@link AvailDocumentationJSONObject} that will comprise
	 * the file.
	 */
	final private AvailDocumentationJSONObject [] jsonObjects;

	/**
	 * Create the string contents of the JSON objects to be exported to a
	 * single file.  This representation will lack readable formatting.
	 * @return
	 */
	private String flattenJsonObjectsArrayToMinimizedString ()
	{
		final StringBuilder sb = new StringBuilder('[');
		final int arraySize = jsonObjects.length;
		for (int i = 0; i < (arraySize - 1) ; i++)
		{
			sb.append(jsonObjects[i]).append(',');
		}

		return sb.append(jsonObjects[jsonObjects.length])
			.append(']')
			.toString();
	}

	/**
	 * Create the string contents of the JSON objects to be exported to a
	 * single file.  This representation will lack readable formatting.
	 * @return
	 */
	private String flattenJsonObjectsArrayToFormattedString ()
	{
		final StringBuilder sb = new StringBuilder("[\n");
		final int arraySize = jsonObjects.length;
		for (int i = 0; i < (arraySize - 1) ; i++)
		{
			sb.append(jsonObjects[i]).append(',');
		}

		return sb.append(jsonObjects[jsonObjects.length])
			.append("\n]")
			.toString();
	}

	/**
	 * Create the string contents of the JSON objects to be exported to a
	 * single file.  This representation will lack readable formatting.
	 * @param minimize Determines if output should be minimized or formatted.
	 * @return
	 */
	private String flattenJsonObjectsArrayToString (final boolean minimize)
	{
		if (minimize)
		{return flattenJsonObjectsArrayToMinimizedString();}

		return flattenJsonObjectsArrayToFormattedString();
	}


	/**
	 * @param filePathName The name of the file and the path of its storage
	 * @param minimize Determines if output should be minimized or formatted.
	 * @throws IOException
	 */
	public void writeToJsonFile(
		final String filePathName,
		final boolean minimize) throws IOException {
		try {

			final File file = new File(filePathName);

			// if file doesn't exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}

			final FileWriter fw = new FileWriter(file.getAbsoluteFile());
			final BufferedWriter bw = new BufferedWriter(fw);
			bw.write(flattenJsonObjectsArrayToString(minimize));
			bw.close();

		} catch (final IOException e) {
			throw new IOException(
				String.format("Could not create/write to", filePathName));
		}
	}

}
