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

package com.avail.tools.unicode

import com.avail.tools.options.OptionProcessor
import com.avail.tools.options.OptionProcessorFactory
import com.avail.tools.unicode.CommandLineConfigurator.OptionKey.HELP
import com.avail.tools.unicode.CommandLineConfigurator.OptionKey.TARGET_PATH
import com.avail.utility.configuration.ConfigurationException
import com.avail.utility.configuration.Configurator
import java.nio.file.Path
import java.nio.file.Paths

/**
 * `CommandLineConfigurator` provides the command-line configuration for
 * the [Unicode&#32;catalog&#32;generator][CatalogGenerator].
 *
 * @property configuration
 *   The [configuration][UnicodeConfiguration].
 * @property helpStream
 *   The [appendable][Appendable] to which help text should be written.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `CommandLineConfigurator`.
 *
 * @param configuration
 *   The base [configuration][UnicodeConfiguration].
 * @param commandLineArguments
 *   The command-line arguments.
 * @param helpStream
 *   The [Appendable] to which help text should be written.
 */
internal class CommandLineConfigurator constructor(
	override val configuration: UnicodeConfiguration,
	commandLineArguments: Array<String>,
	private val helpStream: Appendable) : Configurator<UnicodeConfiguration>
{
	/** The command line arguments.  */
	private val commandLineArguments = commandLineArguments.clone()

	/** Has the [configurator][CommandLineConfigurator] been run yet? */
	private var isConfigured = false

	/**
	 * `OptionKey` enumerates the valid configuration options.
	 */
	internal enum class OptionKey
	{
		/**
		 * Request display of help text.
		 */
		HELP,

		/**
		 * Specification of the target [path][Path].
		 */
		TARGET_PATH
	}

	/**
	 * Create an [option processor][OptionProcessor] suitable for
	 * [updating][updateConfiguration] a [configuration][UnicodeConfiguration].
	 *
	 * @return An option processor.
	 */
	private fun createOptionProcessor() =
		OptionProcessorFactory.create<OptionKey> {
			helpOption(
				HELP,
				"The Unicode catalog generator understands the following "
					+ "options: ",
				helpStream)
			defaultOption(
				TARGET_PATH,
				"The location of the target JSON file. If a regular file "
					+ "already exists at this location, then it will be "
					+ "overwritten.")
				{
					configuration.catalogPath = Paths.get(argument)
				}
		}

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
