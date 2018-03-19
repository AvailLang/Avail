/*
 * JSONReaderTest.java
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
import com.avail.utility.json.JSONIOException;
import com.avail.utility.json.JSONObject;
import com.avail.utility.json.JSONReader;
import com.avail.utility.json.MalformedJSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.StringReader;

import static com.avail.test.utility.json.TestJSONKeyValue.*;
import static com.avail.utility.Nulls.stripNull;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A {@code JSONReaderTest} contains unit tests for the {@link JSONReader}.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class JSONReaderTest
{
	/**
	 * Answer a {@link JSONObject} from the provided {@link StringBuilder}.
	 *
	 * @param sb
	 *        The {@code StringBuilder} that contains the JSON payload.
	 * @return A {@code JSONObject}.
	 */
	private static JSONObject getJsonData (final StringBuilder sb)
	{
		try (final JSONReader reader = new JSONReader(
			new StringReader(sb.toString())))
		{
			final JSONObject content = (JSONObject) reader.read();
			assert content != null : "The payload should not be empty!";
			return content;
		}
		catch (final JSONException e)
		{
			fail(String.format(
				"The following test JSON could not be parsed:\n%s",
				sb));
		}
		catch (final IOException e)
		{
			fail(String.format(
				"The following test JSON could not be created due to an "
					+ "IOException:\n%s",
				sb));
		}
		return null; // Shouldn't get here
	}

	/**
	 * Run a test using {@link Assertions#assertThrows(Class, Executable)} to
	 * test an expected failure.
	 *
	 * @param sb
	 *        The {@code StringBuilder} that contains the JSON payload.
	 * @param expectedThrowable
	 *        The {@link Throwable} that is expected.
	 * @param <T> A type of {@code Throwable}.
	 */
	private static <T extends Throwable> void testFailedParse (
		final StringBuilder sb,
		final Class<T> expectedThrowable)
	{
		try (final JSONReader reader = new JSONReader(
			new StringReader(sb.toString())))
		{
			assertThrows(expectedThrowable, reader::read);
		}
		catch (final IOException e)
		{
			fail(String.format(
				"The following test could not be created due to an "
					+ "IOException:\n%s",
				sb));
		}
	}

	/**
	 * Display the test payload to screen.
	 *
	 * @param sb
	 *        The {@link StringBuilder} with the test JSON.
	 */
	private static void displayTestPayload (final StringBuilder sb)
	{
		System.out.println("Test Payload\n============\n" + sb + "\n");
	}

	@Test
	@DisplayName("JSON payload (no superfluous whitespace) reads in correctly")
	void correctCompactJSONTest ()
	{
		final StringBuilder sb = new StringBuilder("{");
		addToBuilder(true, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE, IMAFALSE, IMANULL, IMACOMPACTARRAY);
		IMANOBJECT.addKeyToBuilder(sb);
		sb.append(":{");
		OBJSTRING.addToBuilder(true, sb);
		OBJINT.addToBuilder(false, sb);
		sb.append("}}");
		final JSONObject content = getJsonData(sb);
		test(stripNull(content), IMASTRING, IMANINT, IMALONG, IMAFLOAT,
			IMADOUBLE, IMATRUE, IMAFALSE, IMANULL, IMACOMPACTARRAY, IMANOBJECT);
		final JSONObject objContent =
			content.getObject(IMANOBJECT.key);
		test(
			objContent, OBJSTRING, OBJINT);
		displayTestPayload(sb);
	}

	@Test
	@DisplayName("JSON payload (superfluous whitespace) reads in correctly")
	void correctSparseJSONTest ()
	{
		final StringBuilder sb = new StringBuilder("{");
		IMASTRING.addToBuilder(false, sb);
		sb.append("  , \n");
		IMANINT.addKeyToBuilder(sb);
		sb.append("   \n \t : \n\n");
		IMANINT.addValueToBuilder(sb);
		sb.append(',');
		addToBuilder(true, sb, IMALONG, IMAFLOAT, IMADOUBLE,
			IMATRUE, IMAFALSE, IMANULL, IMANARRAY);
		IMANOBJECT.addKeyToBuilder(sb);
		sb.append(":{");
		OBJSTRING.addToBuilder(true, sb);
		OBJINT.addToBuilder(false, sb);
		sb.append("}}");
		final JSONObject content = getJsonData(sb);
		test(
			stripNull(content), IMASTRING, IMANINT, IMALONG, IMAFLOAT,
			IMADOUBLE, IMATRUE, IMAFALSE, IMANULL, IMANARRAY, IMANOBJECT);
		final JSONObject objContent =
			content.getObject(IMANOBJECT.key);
		test(
			objContent, OBJSTRING, OBJINT);
		displayTestPayload(sb);
	}

	@Test
	@DisplayName("JSON payload missing comma")
	void missingCommaJSONTest ()
	{
		final StringBuilder sb = new StringBuilder("{");
		addToBuilder(true, sb, IMASTRING, IMANINT, IMALONG);
		// Intentionally leave out comma after next key-value pair
		IMAFLOAT.addToBuilder(false, sb);
		addToBuilder(false, sb, IMADOUBLE, IMATRUE);
		sb.append("}");
		testFailedParse(sb, MalformedJSONException.class);
		displayTestPayload(sb);
	}

	@Test
	@DisplayName("JSON payload missing ':' between key-value pair")
	void missingColonJSONTest ()
	{
		final StringBuilder sb = new StringBuilder("{");
		IMANINT.addToBuilder(true, sb);
		IMALONG.addToBuilder(true, sb);
		IMASTRING.addToBuilder(false, sb);
		sb.append("  , \n");
		IMANINT.addKeyToBuilder(sb);
		sb.append("   \n \t  \n\n");
		IMANINT.addValueToBuilder(sb);
		sb.append(',');
		IMATRUE.addToBuilder(true, sb);
		sb.append("}");
		testFailedParse(sb, MalformedJSONException.class);
		displayTestPayload(sb);
	}

	@Test
	@DisplayName("JSON payload escapes String internal newline")
	void escapeWhitespaceJSONTest ()
	{
		final StringBuilder sb = new StringBuilder("{");
		addToBuilder(true, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE);
		sb.append("\"escapeMe\" : \"fooo\\nboo\"");
		sb.append("}");
		final JSONObject content = getJsonData(sb);
		assertTrue(content.containsKey("escapeMe"));
		assertEquals(content.getString("escapeMe"),"fooo\nboo");
		test(stripNull(content), IMASTRING, IMANINT, IMALONG, IMAFLOAT,
			IMADOUBLE, IMATRUE);
		displayTestPayload(sb);
	}

	@Test
	@DisplayName("JSON payload does not escape String internal newline")
	void missingEscapeWhitespaceJSONTest ()
	{
		final StringBuilder sb = new StringBuilder("{");
		IMASTRING.addToBuilder(true, sb);
		IMANINT.addToBuilder(true, sb);
		IMALONG.addToBuilder(true, sb);
		sb.append("\"escapeMe\" : \"fooo\nboo\",");
		IMAFLOAT.addToBuilder(true, sb);
		IMADOUBLE.addToBuilder(true, sb);
		IMATRUE.addToBuilder(false, sb);
		sb.append("}");
		testFailedParse(sb, JSONIOException.class);
		displayTestPayload(sb);
	}

	@Test
	@DisplayName("JSON payload missing opening '{'")
	void missingOpeningBraceJSONTest ()
	{
		final StringBuilder sb = new StringBuilder();
		addToBuilder(false, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE);
		sb.append("}");
		testFailedParse(sb, MalformedJSONException.class);
		displayTestPayload(sb);
	}

	@Test
	@DisplayName("JSON payload missing closing '}'")
	void missingClosingBraceJSONTest ()
	{
		final StringBuilder sb = new StringBuilder("}");
		addToBuilder(false, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE);
		testFailedParse(sb, MalformedJSONException.class);
		displayTestPayload(sb);
	}

	@Test
	@DisplayName("JSON payload key with space")
	void spaceInKeyJSONTest ()
	{
		final StringBuilder sb = new StringBuilder("{");
		addToBuilder(true, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE);
		sb.append("\"some space\" : \"foo\"");
		sb.append("}");
		final JSONObject content = getJsonData(sb);
		assertTrue(content.containsKey("some space"));
		assertEquals(content.getString("some space"),"foo");
		test(stripNull(content), IMASTRING, IMANINT, IMALONG, IMAFLOAT,
			IMADOUBLE, IMATRUE);
		displayTestPayload(sb);
	}

	@Test
	@DisplayName("JSON payload premature close of object")
	void prematureClosingBraceJSONTest ()
	{
		final StringBuilder sb = new StringBuilder();
		addToBuilder(true, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE);
		sb.append("\"some space\" }");
		testFailedParse(sb, MalformedJSONException.class);
		displayTestPayload(sb);
	}

	@Test
	@DisplayName("JSON payload array comma (,) after last value")
	void malformedArrayJSONTest ()
	{
		final StringBuilder sb = new StringBuilder();
		addToBuilder(true, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT);
		sb.append("\"myArray\" : [1,2,3,],");
		IMADOUBLE.addToBuilder(true, sb);
		IMATRUE.addToBuilder(true, sb);
		sb.append('}');
		testFailedParse(sb, MalformedJSONException.class);
		displayTestPayload(sb);
	}
}
