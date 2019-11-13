/*
 * AvailServerConfiguration.kt
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

package com.avail.server.configuration

import com.avail.builder.ModuleNameResolver
import com.avail.builder.ModuleRoots
import com.avail.builder.RenamesFileParser
import com.avail.builder.RenamesFileParserException
import com.avail.server.AvailServer
import com.avail.utility.configuration.Configuration
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.StandardCharsets

/**
 * An `AvailServerConfiguration` specifies the operational parameters of an
 * [AvailServer].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class AvailServerConfiguration : Configuration
{
	/** The [Avail roots][ModuleRoots] path. */
	@Suppress("MemberVisibilityCanBePrivate")
	var availRootsPath: String = ""
		set (value)
		{
			field = value
			privateAvailRoots = null
		}

	/** The [Avail roots][ModuleRoots]. */
	@Transient
	private var privateAvailRoots: ModuleRoots? = null

	/** The [Avail roots][ModuleRoots]. */
	@Suppress("MemberVisibilityCanBePrivate")
	val availRoots: ModuleRoots
		get()
		{
			var roots = privateAvailRoots
			if (roots == null)
			{
				roots = ModuleRoots(availRootsPath)
				privateAvailRoots = roots
			}
			return roots
		}

	/** The path to the [renames file][RenamesFileParser]. */
	@Suppress("MemberVisibilityCanBePrivate")
	var renamesFilePath: String? = null
		set (value)
		{
			field = value
			moduleNameResolver = null
		}

	/** The [module name resolver][ModuleNameResolver]. */
	@Transient
	private var moduleNameResolver: ModuleNameResolver? = null

	/**
	 * The path to the web root, or `null` if document requests should not be
	 * honored by the [AvailServer].
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var documentPath: String? = null

	/** The server authority.  */
	var serverAuthority = "localhost"

	/** The server port.  */
	var serverPort = 40000

	override
	val isValid: Boolean
		get()
		{
			// Just try to create a module name resolver. If this fails, then
			// the configuration is invalid. Otherwise, it should be okay.
			try
			{
				moduleNameResolver()
			}
			catch (e: FileNotFoundException)
			{
				return false
			}
			catch (e: RenamesFileParserException)
			{
				return false
			}

			return true
		}

	/**
	 * Answer the [module name resolver][ModuleNameResolver] correct for the
	 * current configuration.
	 *
	 * @return
	 *   A module name resolver.
	 * @throws FileNotFoundException
	 *   If the [renames file path][RenamesFileParser] has been specified, but
	 *   is invalid.
	 * @throws RenamesFileParserException
	 *   If the renames file is invalid.
	 */
	@Throws(FileNotFoundException::class, RenamesFileParserException::class)
	fun moduleNameResolver(): ModuleNameResolver
	{
		var resolver = moduleNameResolver
		if (resolver == null)
		{
			val reader = when (val path = renamesFilePath)
			{
				null -> StringReader("")
				else ->
				{
					BufferedReader(InputStreamReader(
						FileInputStream(File(path)), StandardCharsets.UTF_8))
				}
			}
			val renameParser = RenamesFileParser(reader, availRoots)
			resolver = renameParser.parse()
			moduleNameResolver = resolver
		}
		return resolver
	}

	/**
	 * `true` if the server should serve up documents from the web root, `false`
	 * otherwise.
	 */
	val shouldServeDocuments get() = documentPath != null
}