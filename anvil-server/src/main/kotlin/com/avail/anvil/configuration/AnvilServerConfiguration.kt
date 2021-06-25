/*
 * AnvilServerConfiguration.kt
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

package com.avail.anvil.configuration

import com.avail.anvil.AnvilServer
import com.avail.builder.ModuleNameResolver
import com.avail.builder.ModuleRoots
import com.avail.builder.RenamesFileParser
import com.avail.builder.RenamesFileParserException
import com.avail.files.FileManager
import com.avail.utility.configuration.Configuration
import java.io.File
import java.io.FileNotFoundException
import java.io.StringReader
import kotlin.text.Charsets.UTF_8

/**
 * An `AnvilServerConfiguration` specifies the operational parameters of an
 * [AnvilServer].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property fileManager
 *   The [FileManager] that will manage the Avail files.
 *
 * @constructor
 * Construct a new [AnvilServerConfiguration]
 *
 * @param fileManager
 *   The [FileManager] that will manage the Avail files.
 */
class AnvilServerConfiguration constructor (
	private val fileManager: FileManager)
: Configuration
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
	private var privateAvailRoots: ModuleRoots? = null

	/** The [Avail roots][ModuleRoots]. */
	@Suppress("MemberVisibilityCanBePrivate")
	val availRoots: ModuleRoots
		get()
		{
			var roots = privateAvailRoots
			if (roots === null)
			{
				roots = ModuleRoots(fileManager, availRootsPath) {
					it.forEach { msg -> System.err.println(msg) }
				}
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
	private var moduleNameResolver: ModuleNameResolver? = null

	/** The server authority.  */
	var serverAuthority = "localhost"

	/** The server port.  */
	var serverPort = 40000

	/**
	 * Answer the [module&#32;name&#32;resolver][ModuleNameResolver] correct for
	 * the current configuration.
	 *
	 * @return
	 *   A module name resolver.
	 * @throws FileNotFoundException
	 *   If the [renames&#32;file&#32;path][RenamesFileParser] has been
	 *   specified, but is invalid.
	 * @throws RenamesFileParserException
	 *   If the renames file is invalid.
	 */
	@Throws(FileNotFoundException::class, RenamesFileParserException::class)
	fun moduleNameResolver(): ModuleNameResolver
	{
		var resolver = moduleNameResolver
		if (resolver === null)
		{
			val reader = when (val path = renamesFilePath)
			{
				null -> StringReader("")
				else -> File(path).inputStream().reader(UTF_8).buffered()
			}
			val renameParser = RenamesFileParser(reader, availRoots)
			resolver = renameParser.parse()
			moduleNameResolver = resolver
		}
		return resolver
	}
}
