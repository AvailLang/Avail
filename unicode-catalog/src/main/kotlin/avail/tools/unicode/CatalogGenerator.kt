/*
 * CatalogGenerator.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.tools.unicode

import org.availlang.json.JSONFriendly
import org.availlang.json.JSONReader
import org.availlang.json.JSONWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * The Unicode catalog generator: it emits, as a JSON file, every code point in
 * use by the Avail project.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object CatalogGenerator
{
	/**
	 * Read `"allCodePoints.json"` from the specified [directory][Path] to
	 * obtain a [catalog][Catalog].
	 *
	 * @param directory
	 *   A directory.
	 * @return
	 *   A catalog, or `null` if the file does not exist.
	 * @throws Exception
	 *   If anything goes wrong.
	 */
	@Throws(Exception::class)
	private fun readCatalog(directory: Path): Catalog?
	{
		try
		{
			JSONReader(Files.newBufferedReader(
					directory.resolve("allCodePoints.json"),
					StandardCharsets.UTF_8)).use { reader ->
				val data = reader.read()
				return Catalog.readFrom(data!!)
			}
		}
		catch (e: NoSuchFileException)
		{
			return null
		}
	}

	/**
	 * Write a [JSON-friendly][JSONFriendly] [catalog][Catalog] representative
	 * to the specified [path][Path].
	 *
	 * @param path
	 *   The target path.
	 * @param catalogRepresentative
	 *   The catalog representative.
	 * @throws Exception
	 *   If anything goes wrong.
	 */
	@Throws(Exception::class)
	private fun writeCatalog(path: Path, catalogRepresentative: JSONFriendly)
	{
		val options = arrayOf(CREATE, TRUNCATE_EXISTING, WRITE)
		val writer = Files.newBufferedWriter(
			path,
			StandardCharsets.UTF_8,
			*options)
		val jsonWriter = JSONWriter(writer)
		catalogRepresentative.writeTo(jsonWriter)
		jsonWriter.close()
	}

	/**
	 * Generate the Unicode catalog.
	 *
	 * @param commandLineArguments
	 *   The command-line arguments.
	 * @throws Exception
	 *   If anything goes wrong.
	 */
	@Throws(Exception::class)
	@JvmStatic
	fun main(commandLineArguments: Array<String>)
	{
		// Produce a configuration.
		val configuration = UnicodeConfiguration()
		val configurator = CommandLineConfigurator(
			configuration, commandLineArguments, System.out)
		configurator.updateConfiguration()
		// Read an existing catalog file.
		val oldCatalog = readCatalog(configuration.catalogPath)
		// Update the catalog.
		val catalog =
			if (oldCatalog === null)
			{
				Catalog()
			}
			else
			{
				oldCatalog.refresh()
				oldCatalog
			}
		// Write out the new catalog files.
		writeCatalog(
			configuration.catalogPath.resolve("allCodePoints.json"),
			catalog.jsonFriendlyCodePoints)
		writeCatalog(
			configuration.catalogPath.resolve("nonAsciiCodePoints.json"),
			catalog.jsonFriendlyNonAsciiCodePoints)
		writeCatalog(
			configuration.catalogPath.resolve("symbolicCodePoints.json"),
			catalog.jsonFriendlySymbolicCodePoints)
	}
}
