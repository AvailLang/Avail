/*
 * JSONReaderTest.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.test.utility.json

import com.avail.test.utility.json.TestJSONKeyValue.*
import com.avail.utility.Nulls.stripNull
import com.avail.utility.json.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.StringReader

/**
 * A `JSONReaderTest` contains unit tests for the [JSONReader].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class JSONReaderTest
{
	/**
	 * Answer a [JSONObject] from the provided [StringBuilder].
	 *
	 * @param sb
	 *   The `StringBuilder` that contains the JSON payload.
	 * @return
	 *   A `JSONObject`.
	 */
	private fun getJsonData(sb: StringBuilder): JSONObject
	{
		try
		{
			JSONReader(
				StringReader(sb.toString())).use { reader ->
				return reader.read() as JSONObject?
					?: error("The payload should not be empty!")
			}
		}
		catch (e: JSONException)
		{
			fail<Any>("The following test JSON could not be parsed:\n$sb")
		}
		catch (e: IOException)
		{
			fail<Any>(
				"The following test JSON could not be created due to an " +
					"IOException:\n$sb")
		}

		throw UnsupportedOperationException()  // Shouldn't get here
	}

	/**
	 * Run a test using [Assertions.assertThrows] to test an expected failure.
	 *
	 * @param sb
	 *   The `StringBuilder` that contains the JSON payload.
	 * @param expectedThrowable
	 *   The [Throwable] that is expected.
	 * @param T
	 *   A type of `Throwable`.
	 */
	private fun <T : Throwable> testFailedParse(
		sb: StringBuilder, expectedThrowable: Class<T>)
	{
		try
		{
			JSONReader(
				StringReader(sb.toString())).use { reader ->
				assertThrows(expectedThrowable) { reader.read() }
			}
		}
		catch (e: IOException)
		{
			fail<Any>(
				"The following test could not be created due to an " +
					"IOException:\n$sb")
		}

	}

	/**
	 * Display the test payload to screen.
	 *
	 * @param sb
	 *   The [StringBuilder] with the test JSON.
	 * @param printTestPayload
	 *   `true` indicates the `sb` should be printed to standard out; `false`
	 *   otherwise.
	 */
	private fun displayTestPayload(
		sb: StringBuilder, printTestPayload: Boolean)
	{
		if (printTestPayload)
		{
			println("Test Payload\n============\n$sb\n")
		}
	}

	@Test
	@DisplayName("JSON payload (no superfluous whitespace) reads in correctly")
	internal fun correctCompactJSONTest()
	{
		val sb = StringBuilder("{")
		Companion.addToBuilder(
			true, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE, IMAFALSE, IMANULL, IMACOMPACTARRAY)
		IMANOBJECT.addKeyToBuilder(sb)
		sb.append(":{")
		OBJSTRING.addToBuilder(true, sb)
		OBJINT.addToBuilder(false, sb)
		sb.append("}}")
		val content = getJsonData(sb)
		Companion.test(
			stripNull(content), IMASTRING, IMANINT, IMALONG, IMAFLOAT,
			IMADOUBLE, IMATRUE, IMAFALSE, IMANULL, IMACOMPACTARRAY, IMANOBJECT)
		val objContent = content.getObject(IMANOBJECT.key)
		Companion.test(
			objContent, OBJSTRING, OBJINT)
		displayTestPayload(sb, false)
	}

	@Test
	@DisplayName("JSON payload (superfluous whitespace) reads in correctly")
	internal fun correctSparseJSONTest()
	{
		val sb = StringBuilder("{")
		IMASTRING.addToBuilder(false, sb)
		sb.append("  , \n")
		IMANINT.addKeyToBuilder(sb)
		sb.append("   \n \t : \n\n")
		IMANINT.addValueToBuilder(sb)
		sb.append(',')
		Companion.addToBuilder(
			true, sb, IMALONG, IMAFLOAT, IMADOUBLE,
			IMATRUE, IMAFALSE, IMANULL, IMANARRAY)
		IMANOBJECT.addKeyToBuilder(sb)
		sb.append(":{")
		OBJSTRING.addToBuilder(true, sb)
		OBJINT.addToBuilder(false, sb)
		sb.append("}}")
		val content = getJsonData(sb)
		Companion.test(
			stripNull(content), IMASTRING, IMANINT, IMALONG, IMAFLOAT,
			IMADOUBLE, IMATRUE, IMAFALSE, IMANULL, IMANARRAY, IMANOBJECT)
		val objContent = content.getObject(IMANOBJECT.key)
		Companion.test(
			objContent, OBJSTRING, OBJINT)
		displayTestPayload(sb, false)
	}

	@Test
	@DisplayName("Test Failure: JSON payload missing comma")
	internal fun missingCommaJSONTest()
	{
		val sb = StringBuilder("{")
		Companion.addToBuilder(true, sb, IMASTRING, IMANINT, IMALONG)
		// Intentionally leave out comma after next key-value pair
		IMAFLOAT.addToBuilder(false, sb)
		Companion.addToBuilder(false, sb, IMADOUBLE, IMATRUE)
		sb.append("}")
		testFailedParse(sb, MalformedJSONException::class.java)
		displayTestPayload(sb, false)
	}

	@Test
	@DisplayName("Test Failure: JSON payload missing ':' between key-value pair")
	internal fun missingColonJSONTest()
	{
		val sb = StringBuilder("{")
		IMANINT.addToBuilder(true, sb)
		IMALONG.addToBuilder(true, sb)
		IMASTRING.addToBuilder(false, sb)
		sb.append("  , \n")
		IMANINT.addKeyToBuilder(sb)
		sb.append("   \n \t  \n\n")
		IMANINT.addValueToBuilder(sb)
		sb.append(',')
		IMATRUE.addToBuilder(true, sb)
		sb.append("}")
		testFailedParse(sb, MalformedJSONException::class.java)
		displayTestPayload(sb, false)
	}

	@Test
	@DisplayName("JSON payload escapes String internal newline")
	internal fun escapeWhitespaceJSONTest()
	{
		val sb = StringBuilder("{")
		Companion.addToBuilder(
			true, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE)
		sb.append("\"escapeMe\" : \"fooo\\nboo\"")
		sb.append("}")
		val content = getJsonData(sb)
		assertTrue(content.containsKey("escapeMe"))
		assertEquals(content.getString("escapeMe"), "fooo\nboo")
		Companion.test(
			stripNull(content), IMASTRING, IMANINT, IMALONG, IMAFLOAT,
			IMADOUBLE, IMATRUE)
		displayTestPayload(sb, false)
	}

	@Test
	@DisplayName("Test Failure: JSON payload does not escape String internal newline")
	internal fun missingEscapeWhitespaceJSONTest()
	{
		val sb = StringBuilder("{")
		IMASTRING.addToBuilder(true, sb)
		IMANINT.addToBuilder(true, sb)
		IMALONG.addToBuilder(true, sb)
		sb.append("\"escapeMe\" : \"fooo\nboo\",")
		IMAFLOAT.addToBuilder(true, sb)
		IMADOUBLE.addToBuilder(true, sb)
		IMATRUE.addToBuilder(false, sb)
		sb.append("}")
		testFailedParse(sb, JSONIOException::class.java)
		displayTestPayload(sb, false)
	}

	@Test
	@DisplayName("Test Failure: JSON payload missing opening '{'")
	internal fun missingOpeningBraceJSONTest()
	{
		val sb = StringBuilder()
		Companion.addToBuilder(
			false, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE)
		sb.append("}")
		testFailedParse(sb, MalformedJSONException::class.java)
		displayTestPayload(sb, false)
	}

	@Test
	@DisplayName("Test Failure: JSON payload missing closing '}'")
	internal fun missingClosingBraceJSONTest()
	{
		val sb = StringBuilder("}")
		Companion.addToBuilder(
			false, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE)
		testFailedParse(sb, MalformedJSONException::class.java)
		displayTestPayload(sb, false)
	}

	@Test
	@DisplayName("JSON payload key with space")
	internal fun spaceInKeyJSONTest()
	{
		val sb = StringBuilder("{")
		Companion.addToBuilder(
			true, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE)
		sb.append("\"some space\" : \"foo\"")
		sb.append("}")
		val content = getJsonData(sb)
		assertTrue(content.containsKey("some space"))
		assertEquals(content.getString("some space"), "foo")
		Companion.test(
			stripNull(content), IMASTRING, IMANINT, IMALONG, IMAFLOAT,
			IMADOUBLE, IMATRUE)
		displayTestPayload(sb, false)
	}

	@Test
	@DisplayName("Test Failure: JSON payload premature close of object")
	internal fun prematureClosingBraceJSONTest()
	{
		val sb = StringBuilder()
		Companion.addToBuilder(
			true, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT, IMADOUBLE, IMATRUE)
		sb.append("\"some space\" }")
		testFailedParse(sb, MalformedJSONException::class.java)
		displayTestPayload(sb, false)
	}

	@Test
	@DisplayName("Test Failure: JSON payload array comma (,) after last value")
	internal fun malformedArrayJSONTest()
	{
		val sb = StringBuilder()
		Companion.addToBuilder(
			true, sb, IMASTRING, IMANINT, IMALONG,
			IMAFLOAT)
		sb.append("\"myArray\" : [1,2,3,],")
		IMADOUBLE.addToBuilder(true, sb)
		IMATRUE.addToBuilder(true, sb)
		sb.append('}')
		testFailedParse(sb, MalformedJSONException::class.java)
		displayTestPayload(sb, false)
	}
}
