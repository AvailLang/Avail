/**
 * CatalogGenerator.java
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

package com.avail.tools.unicode;

import static java.nio.file.StandardOpenOption.*;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import com.avail.utility.json.JSONFriendly;
import com.avail.utility.json.JSONWriter;

/**
 * The Unicode catalog generator: it emits, as a JSON file, every code point in
 * use by the Avail project.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class CatalogGenerator
{
	/**
	 * Generate the Unicode catalog.
	 *
	 * @param commandLineArguments
	 *        The command-line arguments.
	 * @throws Exception
	 *         If anything goes wrong.
	 */
	public static void main (final String[] commandLineArguments)
		throws Exception
	{
		// Produce a configuration.
		final Configuration configuration = new Configuration();
		final CommandLineConfigurator configurator =
				new CommandLineConfigurator(
					configuration, commandLineArguments, System.out);
		configurator.updateConfiguration();
		// Set up the output writer.
		final Writer writer;
		if (configuration.targetPath == null)
		{
			writer = new PrintWriter(System.out);
		}
		else
		{
			final StandardOpenOption[] options = new StandardOpenOption[]
				{CREATE, TRUNCATE_EXISTING, WRITE};
			writer = Files.newBufferedWriter(
				configuration.targetPath,
				StandardCharsets.UTF_8,
				options);
		}
		final JSONWriter jsonWriter = new JSONWriter(writer);
		// Produce the catalog.
		final Catalog catalog = new Catalog();
		final JSONFriendly catalogRepresentative =
			configuration.includeAsciiCodePoints
			? catalog.jsonFriendlyCodePoints()
			: catalog.jsonFriendlyNonAsciiCodePoints();
		catalogRepresentative.writeTo(jsonWriter);
		jsonWriter.close();
	}
}
