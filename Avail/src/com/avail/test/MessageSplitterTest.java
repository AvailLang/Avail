/**
 * compiler/MessageSplitterTest.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.test;

import java.io.StringReader;
import java.util.*;
import org.junit.*;
import com.avail.AvailRuntime;
import com.avail.compiler.*;
import com.avail.descriptor.*;


/**
 * Test the {@link MessageSplitter}.  It splits method names into a sequence of
 * tokens to expect and underscores, but it also implements the repeated
 * argument mechanism by producing a sequence of mini-instructions to say how
 * to do the parsing.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MessageSplitterTest
{
	/** A {@linkplain ModuleNameResolver module name resolver}. */
	private ModuleNameResolver moduleNameResolver;

	/** An {@linkplain AvailRuntime Avail runtime}. */
	private AvailRuntime runtime;

	/**
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime.
	 *
	 * @throws RenamesFileParserException
	 *         Never happens.
	 */
	@Before
	public void initializeAllWellKnownObjects ()
		throws RenamesFileParserException
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
		final RenamesFileParser renamesFileParser = new RenamesFileParser(
			new StringReader(""),
			new ModuleRoots(""));
		final ModuleNameResolver resolver = renamesFileParser.parse();
		runtime = new AvailRuntime(resolver);
	}

	/** Test cases. */
	private static String[][] splitCases =
	{
		{"Foo", "Foo", "[10]"},
		{"Print_", "Print", "_", "[10, 0]"},
		{"_+_", "_", "+", "_", "[0, 18, 0]"},
		{"||_||", "|", "|", "_", "|", "|", "[10, 18, 0, 34, 42]"},
		{"_+_*_", "_", "+", "_", "*", "_", "[0, 18, 0, 34, 0]"},
		{"_;", "_", ";", "[0, 18]"},
		/* Backquotes */
		{"`__", "`", "_", "_", "[18, 0]"},
		{"_`«_", "_", "`", "«", "_", "[0, 26, 0]"},
		{"_``_", "_", "`", "`", "_", "[0, 26, 0]"},
		/* Repeated groups */
		{"«_;»", "«", "_", ";", "»",
			"[3, 1, 88, 0, 26, 2, 80, 5, 33, 5, 4]"},
		{"«x»", "«", "x", "»",
			"[3, 1, 96, 1, 18, 80, 2, 5, 33, 2, 5, 4]"},
		{"«x y»", "«", "x", "y", "»",
			"[3, 1, 104, 1, 18, 26, 88, 2, 5, 33, 2, 5, 4]"},
		{"«x_y»", "«", "x", "_", "y", "»",
			"[3, 1, 96, 18, 0, 34, 2, 88, 5, 33, 5, 4]"},
		{"«_:_»", "«", "_", ":", "_", "»",
			"[3, 1, 128, 1, 0, 2, 26, 0, 2, 112, 2, 5, 33, 2, 5, 4]"},
		{"«»", "«", "»",
			"[3, 1, 88, 1, 72, 2, 5, 33, 2, 5, 4]"},
		{"«»«»", "«", "»", "«", "»",
			"[3, 1, 88, 1, 72, 2, 5, 33, 2, 5, 4, 3, 1, 176, 1, 160, 2, 5, 121, 2, 5, 4]"},
		/* With dagger */
		{"«_‡,»", "«", "_", "‡", ",", "»",
			"[3, 1, 88, 0, 2, 80, 34, 5, 33, 5, 4]"},
		{"«‡»", "«", "‡", "»",
			"[3, 1, 88, 1, 72, 2, 5, 33, 2, 5, 4]"},
		{"new_«with«_=_‡,»»",
			"new", "_", "«", "with", "«", "_", "=", "_", "‡", ",", "»", "»",
			"[10, 0, 3, 1, 232, 34, 3, 1, 184, 1, 0, 2, 58, 0, 2, 168, 82, 2, 5, 81, 2, 5, 4, 2, 224, 5, 49, 5, 4]"},
	};

	/**
	 * Test: Split the test cases.
	 */
	@Test
	public void testSplitting ()
	{
		for (final String[] splitCase : Arrays.asList(splitCases))
		{
			final String msgString = splitCase[0];
			final AvailObject message =
				ByteStringDescriptor.from(msgString);
			final MessageSplitter splitter = new MessageSplitter(message);
			final AvailObject parts = splitter.messageParts();
			assert splitCase.length == parts.tupleSize() + 2;
			for (int i = 1; i <= parts.tupleSize(); i++)
			{
				assert parts.tupleAt(i).asNativeString().equals(splitCase[i]);
			}
			final AvailObject instructionsTuple = splitter.instructionsTuple();
			final List<Integer> instructionsList = new ArrayList<Integer>();
			for (final AvailObject instruction : instructionsTuple)
			{
				instructionsList.add(instruction.extractInt());
			}
			assert instructionsList.toString()
				.equals(splitCase[splitCase.length - 1])
			: "Generated parse code was not as expected";
		}
	}
}
