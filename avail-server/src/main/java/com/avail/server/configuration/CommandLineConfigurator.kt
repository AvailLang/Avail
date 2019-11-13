/*
 * CommandLineConfigurator.kt
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

import com.avail.builder.ModuleRoots
import com.avail.builder.RenamesFileParser
import com.avail.server.AvailServer
import com.avail.server.configuration.CommandLineConfigurator.OptionKey.AVAIL_RENAMES
import com.avail.server.configuration.CommandLineConfigurator.OptionKey.AVAIL_ROOTS
import com.avail.server.configuration.CommandLineConfigurator.OptionKey.DOCUMENT_ROOT
import com.avail.server.configuration.CommandLineConfigurator.OptionKey.HELP
import com.avail.server.configuration.CommandLineConfigurator.OptionKey.SERVER_AUTHORITY
import com.avail.server.configuration.CommandLineConfigurator.OptionKey.SERVER_PORT
import com.avail.tools.options.GenericHelpOption
import com.avail.tools.options.GenericOption
import com.avail.tools.options.OptionProcessingException
import com.avail.tools.options.OptionProcessor
import com.avail.tools.options.OptionProcessorFactory
import com.avail.utility.configuration.ConfigurationException
import com.avail.utility.configuration.Configurator
import java.io.File

/**
 * Provides the [configuration][AvailServerConfiguration] for the [Avail
 * server][AvailServer]. Specifies the options that are available as arguments
 * to the server.
 *
 * @property helpStream
 *   The [appendable][Appendable] to which help text should be written.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new instance.
 *
 * @param configuration
 *   The base [server][AvailServerConfiguration].
 * @param commandLineArguments
 *   The command-line arguments.
 * @param helpStream
 *   The [Appendable] to which help text should be written.
 */
class CommandLineConfigurator constructor(
	override val configuration: AvailServerConfiguration,
	commandLineArguments: Array<String>,
	private val helpStream: Appendable) : Configurator<AvailServerConfiguration>
{
	/** The command line arguments.  */
	private val commandLineArguments = commandLineArguments.clone()

	/** Has the [configurator][CommandLineConfigurator] been run yet? */
	private var isConfigured: Boolean = false

	/**
	 * `OptionKey` enumerates the valid configuration options.
	 */
	internal enum class OptionKey
	{
		/**
		 * Specification of the [path][File] to the [renames
		 * file][RenamesFileParser].
		 */
		AVAIL_RENAMES,

		/**
		 * Specification of the [Avail roots][ModuleRoots].
		 */
		AVAIL_ROOTS,

		/**
		 * Specification of the server authority.
		 */
		SERVER_AUTHORITY,

		/**
		 * Specification of the server port.
		 */
		SERVER_PORT,

		/**
		 * Specification of the web document root.
		 */
		DOCUMENT_ROOT,

		/**
		 * Request display of help text.
		 */
		HELP
	}

	/**
	 * Create an [option processor][OptionProcessor] suitable for
	 * [updating][updateConfiguration] a [server
	 * configuration][AvailServerConfiguration].
	 *
	 * @return
	 *   An option processor.
	 */
	private fun createOptionProcessor(): OptionProcessor<OptionKey>
	{
		val factory = OptionProcessorFactory(OptionKey::class.java)
		factory.configure {
			addOption(GenericOption(
				AVAIL_RENAMES,
				listOf("availRenames"),
				"The path to the renames file. This option overrides environment "
				+ "variables.",
				{ _, renamesString ->
					checkEncountered(AVAIL_RENAMES, 0)
					configuration.renamesFilePath = renamesString
				}))
			factory.addOption(GenericOption(
				AVAIL_ROOTS,
				listOf("availRoots"),
				"The Avail roots, as a semicolon (;) separated list of module "
				+ "root specifications. Each module root specification "
				+ "comprises a  logical root name, then an equals (=), then a "
				+ "module root location. A module root location comprises the "
				+ "absolute path to a binary module repository, then "
				+ "optionally a comma (,) and the  absolute path to a source "
				+ "package. This option overrides environment variables.",
				{ _, rootsString ->
					checkEncountered(AVAIL_ROOTS, 0)
					configuration.availRootsPath = rootsString!!
				}))
			factory.addOption(GenericOption(
				SERVER_AUTHORITY,
				listOf("serverAuthority"),
				"The server authority, i.e., the name of the Avail server. "
				+ "If not specified, then the server authority defaults to "
				+ "\"localhost\".",
				{ _, nameString ->
					checkEncountered(SERVER_AUTHORITY, 0)
					configuration.serverAuthority = nameString!!
				}))
			factory.addOption(GenericOption(
				SERVER_PORT,
				listOf("serverPort"),
				"The server port. If not specified, then the server port "
				+ "defaults to 40000.",
				{ _, portString ->
					checkEncountered(SERVER_PORT, 0)
					try
					{
						configuration.serverPort = Integer.parseInt(portString)
					}
					catch (e: NumberFormatException)
					{
						throw OptionProcessingException(
							"expected an integer \"p\" where 0 ≤ p < 65535",
							e)
					}
				}))
			factory.addOption(GenericOption(
				DOCUMENT_ROOT,
				listOf("documentRoot"),
				"The document root, as a path to a directory. The document "
				+ "root contains static files that should be served by the "
				+ "Avail server. These files are available through GET "
				+ "requests under the URI /doc. If not specified, then the "
				+ "Avail server will reject all such requests.",
				{ _, pathString ->
					checkEncountered(DOCUMENT_ROOT, 0)
					configuration.documentPath = pathString
				}))
			factory.addOption(GenericHelpOption(
				HELP,
				"The Avail server understands the following options: ",
				helpStream))
		}
		return factory.createOptionProcessor()
	}

	@Synchronized
	@Throws(ConfigurationException::class)
	override fun updateConfiguration()
	{
		if (!isConfigured)
		{
			try
			{
				createOptionProcessor().processOptions(commandLineArguments)
				isConfigured = true
			}
			catch (e: Exception)
			{
				throw ConfigurationException(
					"unexpected configuration error", e)
			}

		}
	}
}