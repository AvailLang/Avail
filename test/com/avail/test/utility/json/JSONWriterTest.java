/*
 * JSONWriterTest.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.test.utility.json;
import com.avail.utility.json.JSONException;
import com.avail.utility.json.JSONObject;
import com.avail.utility.json.JSONReader;
import com.avail.utility.json.JSONWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static com.avail.test.utility.json.TestJSONKeyValue.IMACOMPACTARRAY;
import static com.avail.test.utility.json.TestJSONKeyValue.IMAFALSE;
import static com.avail.test.utility.json.TestJSONKeyValue.IMAFLOAT;
import static com.avail.test.utility.json.TestJSONKeyValue.IMALONG;
import static com.avail.test.utility.json.TestJSONKeyValue.IMANINT;
import static com.avail.test.utility.json.TestJSONKeyValue.IMANOBJECT;
import static com.avail.test.utility.json.TestJSONKeyValue.IMANULL;
import static com.avail.test.utility.json.TestJSONKeyValue.IMASTRING;
import static com.avail.test.utility.json.TestJSONKeyValue.IMATRUE;
import static com.avail.test.utility.json.TestJSONKeyValue.OBJINT;
import static com.avail.test.utility.json.TestJSONKeyValue.OBJSTRING;
import static com.avail.test.utility.json.TestJSONKeyValue.addObjectToWriter;
import static com.avail.test.utility.json.TestJSONKeyValue.addToWriter;
import static com.avail.test.utility.json.TestJSONKeyValue.test;
import static com.avail.utility.Nulls.stripNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@code JSONWriterTest} contains unit tests for the {@link JSONWriter}.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@SuppressWarnings("WeakerAccess")
public class JSONWriterTest
{
	/**
	 * Answer a {@link JSONObject} from the provided {@link StringBuilder}.
	 *
	 * @param writer
	 *        The {@code StringBuilder} that contains the JSON payload.
	 * @return A {@code JSONObject}.
	 */
	private static JSONObject getJsonData (final JSONWriter writer)
	{
		try (final JSONReader reader = new JSONReader(
			new StringReader(writer.toString())))
		{
			final JSONObject content = (JSONObject) reader.read();
			assert content != null : "The payload should not be empty!";
			return content;
		}
		catch (final JSONException e)
		{
			fail(String.format(
				"The following test JSON could not be parsed:\n%s",
				writer.contents()));
		}
		catch (final IllegalStateException|IOException e)
		{
			fail(String.format(
				"The following test JSON could not be created due to an "
					+ "exception:\n%s",
				writer.contents()));
		}
		return null; // Shouldn't get here
	}

	/**
	 * Display the test payload to screen.
	 *
	 * @param writer
	 *        The {@link JSONWriter} with the test JSON.
	 */
	private static void displayTestPayload (final JSONWriter writer)
	{
		//noinspection ConstantConditions,ConstantIfStatement
		if (false)
		{
			System.out.println("Test Payload\n============\n" + writer + "\n");
		}
	}

	@SuppressWarnings("JUnitTestMethodWithNoAssertions")
	@Test
	@DisplayName("Correctly built JSON")
	void correctlyBuiltJSONTest ()
	{
		final JSONWriter writer = new JSONWriter();
		writer.startObject();
		addToWriter(writer, IMASTRING, IMANINT, IMALONG, IMAFLOAT, IMATRUE,
			IMAFALSE, IMANULL, IMACOMPACTARRAY);
		IMASTRING.addValueToWriter(writer);
		addObjectToWriter(IMANOBJECT.key, writer, OBJSTRING, OBJINT);
		writer.endObject();
		final JSONObject content = getJsonData(writer);
		test(stripNull(content), IMASTRING, IMANINT, IMALONG, IMAFLOAT,
			IMATRUE, IMAFALSE, IMANULL, IMACOMPACTARRAY, IMANOBJECT);
		final JSONObject objContent = content.getObject(IMANOBJECT.key);
		test(objContent, OBJSTRING, OBJINT);
		displayTestPayload(writer);
	}

	@Test
	@DisplayName("Test Failure: Close an array when not expected")
	void inappropriateCloseArray ()
	{
		final JSONWriter writer = new JSONWriter();
		writer.startObject();
		assertThrows(IllegalStateException.class, writer::endArray);
		displayTestPayload(writer);
	}

// TODO the following should fail but don't; need to investigate.
//	@Test
//	@DisplayName("Test Failure: Open an array as first write")
//	void inappropriateOpenArray ()
//	{
//		final JSONWriter writer = new JSONWriter();
//		assertThrows(IllegalStateException.class, writer::startArray);
//		displayTestPayload(writer);
//	}
//
//	@Test
//	@DisplayName("Test Failure: Attempt to write data without starting object")
//	void noInitialObjectStart ()
//	{
//		final JSONWriter writer = new JSONWriter();
//		assertThrows(
//			IllegalStateException.class, () -> writer.write("foo"));
//		displayTestPayload(writer);
//	}
}
