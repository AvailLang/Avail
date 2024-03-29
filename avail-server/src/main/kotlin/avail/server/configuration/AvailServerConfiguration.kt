/*
 * AvailServerConfiguration.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.server.configuration

import avail.builder.ModuleNameResolver
import avail.builder.ModuleRoots
import avail.builder.RenamesFileParser
import avail.builder.RenamesFileParserException
import avail.files.FileManager
import avail.server.AvailServer
import avail.server.io.SocketAdapter
import avail.server.io.WebSocketAdapter
import avail.utility.configuration.Configuration
import java.io.File
import java.io.FileNotFoundException
import java.io.StringReader
import kotlin.text.Charsets.UTF_8

/**
 * An `AvailServerConfiguration` specifies the operational parameters of an
 * [AvailServer].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property fileManager
 *   The [FileManager] that will manage the Avail files.
 *
 * @constructor
 * Construct a new [AvailServerConfiguration]
 *
 * @param fileManager
 *   The [FileManager] that will manage the Avail files.
 */
class AvailServerConfiguration constructor(private val fileManager: FileManager)
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
	@Transient
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
	@Transient
	private var moduleNameResolver: ModuleNameResolver? = null

	/**
	 * The path to the web root, or `null` if document requests should not be
	 * honored by the [AvailServer].
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var documentPath: String? = null

	/**
	 * Whether to start a [WebSocketAdapter] instead of a normal
	 * [SocketAdapter].
	 */
	var startWebSocketAdapter = false

	/** The server authority. */
	var serverAuthority = "localhost"

	/** The server port. */
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

	/**
	 * `true` if the server should serve up documents from the web root, `false`
	 * otherwise.
	 */
	val shouldServeDocuments get() = documentPath !== null
}
