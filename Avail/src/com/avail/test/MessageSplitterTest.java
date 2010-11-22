/**
 * com.avail.compiler/MessageSplitterTest.java
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import com.avail.AvailRuntime;
import com.avail.compiler.MessageSplitter;
import com.avail.compiler.ModuleNameResolver;
import com.avail.compiler.ModuleRoots;
import com.avail.compiler.RenamesFileParser;
import com.avail.compiler.RenamesFileParserException;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ByteStringDescriptor;


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

	ModuleNameResolver  moduleNameResolver;
	AvailRuntime        runtime;

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
		RenamesFileParser renamesFileParser = new RenamesFileParser( 
			new StringReader(""),
			new ModuleRoots(""));
		ModuleNameResolver resolver = renamesFileParser.parse();
		runtime = new AvailRuntime(resolver);
	}

	static String[][] splitCases = {
		{"Foo", "Foo", "[6]"},
		{"Print_", "Print", "_", "[6, 0]"},
		{"_+_", "_", "+", "_", "[0, 10, 0]"},
		{"||_||", "|", "|", "_", "|", "|", "[6, 10, 0, 18, 22]"},
		{"_+_*_", "_", "+", "_", "*", "_", "[0, 10, 0, 18, 0]"},
		{"_;", "_", ";", "[0, 10]"},
		{"`__", "`", "_", "_", "[10, 0]"},
		{"«_‡,»", "«", "_", "‡", ",", "»", "[1, 32, 0, 2, 32, 18, 13]"},
		{"_`«_", "_", "`", "«", "_", "[0, 14, 0]"},
		{"_``_", "_", "`", "`", "_", "[0, 14, 0]"},
	};
	
	@Test
	public void testSplitting ()
	{
		for (String[] splitCase : Arrays.asList(splitCases))
		{
			String msgString = splitCase[0];
			AvailObject message =
				ByteStringDescriptor.mutableObjectFromNativeString(msgString);
			MessageSplitter splitter = new MessageSplitter(message);
			AvailObject parts = splitter.messageParts();
			assert splitCase.length == parts.tupleSize() + 2;
			for (int i = 1; i <= parts.tupleSize(); i++)
			{
				assert parts.tupleAt(i).asNativeString().equals(splitCase[i]); 
			}
			AvailObject instructionsTuple = splitter.instructionsTuple();
			List<Integer> instructionsList = new ArrayList<Integer>();
			for (AvailObject instruction : instructionsTuple)
			{
				instructionsList.add(instruction.extractInt());
			}
			assert instructionsList.toString()
				.equals(splitCase[splitCase.length - 1]);
		}
	}
}
