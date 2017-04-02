/**
 * CatalogGenerator.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.jetbrains.annotations.Nullable;
import com.avail.utility.IO;
import com.avail.utility.json.JSONData;
import com.avail.utility.json.JSONFriendly;
import com.avail.utility.json.JSONReader;
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
	 * Read {@code "allCodePoints.json"} from the specified {@linkplain Path
	 * directory} to obtain a {@linkplain Catalog catalog}.
	 *
	 * @param directory
	 *        A directory.
	 * @return A catalog, or {@code null} if the file does not exist.
	 * @throws Exception
	 *         If anything goes wrong.
	 */
	private static @Nullable Catalog readCatalog (final Path directory)
		throws Exception
	{
		JSONReader reader = null;
		try
		{
			final Path catalogPath = directory.resolve("allCodePoints.json");
			final Reader fileReader = Files.newBufferedReader(
				catalogPath, StandardCharsets.UTF_8);
			reader = new JSONReader(fileReader);
			final JSONData data = reader.read();
			return Catalog.readFrom(data);
		}
		catch (final NoSuchFileException e)
		{
			return null;
		}
		finally
		{
			IO.closeIfNotNull(reader);
		}
	}

	/**
	 * Write a {@linkplain JSONFriendly JSON-friendly} {@linkplain Catalog
	 * catalog} representative to the specified {@linkplain Path path}.
	 *
	 * @param path
	 *        The target path.
	 * @param catalogRepresentative
	 *        The catalog representative.
	 * @throws Exception
	 *         If anything goes wrong.
	 */
	private static void writeCatalog (
			final Path path,
			final JSONFriendly catalogRepresentative)
		throws Exception
	{
		final StandardOpenOption[] options = new StandardOpenOption[]
			{CREATE, TRUNCATE_EXISTING, WRITE};
		final Writer writer = Files.newBufferedWriter(
			path,
			StandardCharsets.UTF_8,
			options);
		final JSONWriter jsonWriter = new JSONWriter(writer);
		catalogRepresentative.writeTo(jsonWriter);
		jsonWriter.close();
	}

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
		final UnicodeConfiguration configuration = new UnicodeConfiguration();
		final CommandLineConfigurator configurator =
				new CommandLineConfigurator(
					configuration, commandLineArguments, System.out);
		configurator.updateConfiguration();
		// Read an existing catalog file.
		final Catalog oldCatalog = readCatalog(configuration.catalogPath);
		// Update the catalog.
		final Catalog catalog;
		if (oldCatalog == null)
		{
			catalog = new Catalog();
		}
		else
		{
			oldCatalog.refresh();
			catalog = oldCatalog;
		}
		// Write out the new catalog files.
		writeCatalog(
			configuration.catalogPath.resolve("allCodePoints.json"),
			catalog.jsonFriendlyCodePoints());
		writeCatalog(
			configuration.catalogPath.resolve("nonAsciiCodePoints.json"),
			catalog.jsonFriendlyNonAsciiCodePoints());
		writeCatalog(
			configuration.catalogPath.resolve("symbolicCodePoints.json"),
			catalog.jsonFriendlySymbolicCodePoints());
	}
}
