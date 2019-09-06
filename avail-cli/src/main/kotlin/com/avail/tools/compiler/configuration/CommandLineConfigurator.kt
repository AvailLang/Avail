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

package com.avail.tools.compiler.configuration

import com.avail.builder.ModuleName
import com.avail.builder.ModuleRoots
import com.avail.builder.RenamesFileParser
import com.avail.performance.StatisticReport
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.*
import com.avail.tools.options.*
import com.avail.utility.MutableOrNull
import com.avail.utility.configuration.ConfigurationException
import com.avail.utility.configuration.Configurator
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Provides the configuration for the command-line compiler. Specifies the
 * options that are available as arguments to the compiler and their effects
 * on the compile process or user interface.
 *
 * @property helpStream
 *   The [appendable][Appendable] to which help text should be written.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `CommandLineConfigurator`.
 *
 * @param configuration
 *   The base [compiler][CompilerConfiguration].
 * @param commandLineArguments
 *   The command-line arguments.
 * @param helpStream
 *   The [Appendable] to which help text should be written.
 */
class CommandLineConfigurator constructor(
	override val configuration: CompilerConfiguration,
	commandLineArguments: Array<String>,
	private val helpStream: Appendable) : Configurator<CompilerConfiguration>
{
	/** The command line arguments.  */
	private val commandLineArguments = commandLineArguments.clone()

	/** Has the [configurator][CommandLineConfigurator] been run yet? */
	private var isConfigured = false

	/**
	 * `OptionKey` enumerates the valid configuration options.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
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
		 * The option to compile the target module and its ancestors.
		 */
		COMPILE_MODULES,

		/**
		 * The option to force removal of all repositories for which the Avail
		 * roots path has valid source directories specified.
		 */
		CLEAR_REPOSITORIES,

		/**
		 * The option to generate Stacks documentation.
		 */
		GENERATE_DOCUMENTATION,

		/**
		 * The option to set the Stacks module-oriented documentation path.
		 */
		DOCUMENTATION_PATH,

		/**
		 * The option to mute all output originating from user code.
		 */
		QUIET,

		/**
		 * The option to emit performance statistics for the Avail Virtual
		 * Machine.
		 */
		SHOW_STATISTICS,

		/**
		 * The option to request standard verbosity or set the [verbosity
		 * level][VerbosityLevel].
		 */
		VERBOSE_MODE,

		/**
		 * Request display of help text.
		 */
		HELP,

		/**
		 * Specification of the target [module name][ModuleName].
		 */
		TARGET_MODULE_NAME
	}

	/**
	 * Create an [option processor][OptionProcessor] suitable for
	 * [updating][updateConfiguration] a [compiler
	 * configuration][CompilerConfiguration].
	 *
	 * @return
	 *   An option processor.
	 */
	private fun createOptionProcessor(): OptionProcessor<OptionKey>
	{
		val processor = MutableOrNull<OptionProcessor<OptionKey>>()
		val factory = OptionProcessorFactory(OptionKey::class.java)

		factory.addOption(GenericOption(
			AVAIL_RENAMES,
			listOf("availRenames"),
			"The path to the renames file. This option overrides "
				+ "environment variables.")
			{ _, renamesString ->
				processor.value().checkEncountered(AVAIL_RENAMES, 0)
				configuration.renamesFilePath = renamesString
			})

		factory.addOption(GenericOption(
			AVAIL_ROOTS,
			listOf("availRoots"),
			"The Avail roots, as a semicolon (;) separated list of module root "
				+ "specifications. Each module root specification comprises a "
				+ "logical root name, then an equals (=), then a module root "
				+ "location. A module root location comprises the absolute "
				+ "path to a binary module repository, then optionally a comma "
				+ "(,) and the absolute path to a source package. This option "
				+ "overrides environment variables.")
			{ _, rootsString ->
				processor.value().checkEncountered(AVAIL_ROOTS, 0)
				configuration.availRootsPath = rootsString
			})

		factory.addOption(GenericOption(
			COMPILE_MODULES,
			listOf("c", "compile"),
			"Compile the target module and its ancestors.")
			{ _ ->
				processor.value().checkEncountered(COMPILE_MODULES, 0)
				configuration.compileModules = true
			})

		factory.addOption(GenericOption(
			CLEAR_REPOSITORIES,
			listOf("f", "clearRepositories"),
			"Force removal of all repositories for which the Avail root path "
				+ "has valid source directories specified. This option can be "
				+ "used in isolation and will cause the repositories to be "
				+ "emptied. In an invocation with a valid target module name, "
				+ "the repositories will be cleared before compilation is "
				+ "attempted. Mutually exclusive with -g.")
			{ _ ->
				processor.value().checkEncountered(CLEAR_REPOSITORIES, 0)
				processor.value()
					.checkEncountered(GENERATE_DOCUMENTATION, 0)
				configuration.clearRepositories = true
			})

		factory.addOption(GenericOption(
			GENERATE_DOCUMENTATION,
			listOf("g", "generateDocumentation"),
			"Generate Stacks documentation for the target module and its "
				+ "ancestors. The relevant repositories must already contain "
				+ "compilations for every module implied by the request. "
				+ "Mutually exclusive with -f.")
			{ _ ->
				processor.value()
					.checkEncountered(GENERATE_DOCUMENTATION, 0)
				processor.value().checkEncountered(CLEAR_REPOSITORIES, 0)
				processor.value().checkEncountered(SHOW_STATISTICS, 0)
				configuration.generateDocumentation = true
			})

		factory.addOption(GenericOption(
			DOCUMENTATION_PATH,
			listOf("G", "documentationPath"),
			"The path to the output directory where documentation and data "
				+ "files will appear when Stacks documentation is generated. "
				+ "Requires -g.")
			{ keyword, pathString ->
				processor.value().checkEncountered(DOCUMENTATION_PATH, 0)
				val path: Path
				try
				{
					path = Paths.get(pathString)
				}
				catch (e: InvalidPathException)
				{
					throw OptionProcessingException(
						"$keyword: invalid path: ${e.localizedMessage}")
				}

				configuration.documentationPath = path
			})

		factory.addOption(GenericOption(
			QUIET,
			listOf("q", "quiet"),
			"Mute all output originating from user code.")
			{ _ ->
				processor.value().checkEncountered(QUIET, 0)
				configuration.quiet = true
			})

		factory.addOption(GenericOption(
			SHOW_STATISTICS,
			listOf("s", "showStatistics"),
			"Request statistics about the most time-intensive operations in "
				+ "all categories ( -s or --showStatistics ) or for specific "
				+ "categories, using a comma-separated list of keywords "
				+ "( --showStatistics=#,# ). Requires -c.\n"
				+ "\nPossible values in # include:"
				+ "\nL2Operations - The most time-intensive level-two "
				+ "operations."
				+ "\nDynamicLookups - The most time-intensive dynamic method "
				+ "lookups."
				+ "\nPrimitives - The primitives that are the most "
				+ "time-intensive to run overall."
				+ "\nPrimitiveReturnTypeChecks - The primitives that take the "
				+ "most time checking return types.",
			{
				processor.value().checkEncountered(SHOW_STATISTICS, 0)
				processor.value()
					.checkEncountered(GENERATE_DOCUMENTATION, 0)
				val reports = EnumSet.allOf(StatisticReport::class.java)
				configuration.reports = reports
			},
			{ keyword, reportsString ->
				processor.value().checkEncountered(SHOW_STATISTICS, 0)
				processor.value()
					.checkEncountered(GENERATE_DOCUMENTATION, 0)
				val reportsArr = reportsString.split(",".toRegex())
					.dropLastWhile { it.isEmpty() }.toTypedArray()
				val reports = EnumSet.noneOf(StatisticReport::class.java)
				for (reportName in reportsArr)
				{
					val report = StatisticReport.reportFor(reportName)
						?: throw OptionProcessingException(
							"$keyword: Illegal argument.")

					// This will also catch the illegal use of "="
					// without any items following.
					reports.add(report)
				}
				configuration.reports = reports
			}))

		factory.addOption(GenericOption(
			VERBOSE_MODE,
			listOf("v", "verboseMode"),
			"Request minimum verbosity ( -v or --verboseMode ) "
				+ "or manually set the verbosity level ( --verboseMode=# ).\n"
				+ "\nPossible values for # include:"
				+ "\n0 - Zero extra verbosity. Only error messages will be "
				+ "output. This is the default level for the compiler and is "
				+ "used when the verboseMode option is not used."
				+ "\n1 - The minimum verbosity level. The global progress and "
				+ "any error messages will be output. This is the default "
				+ "level for this option when a level is not specified."
				+ "\n2 - Global progress is output along with the local module "
				+ "compilation progress and any error messages.",
			{
				processor.value().checkEncountered(VERBOSE_MODE, 0)
				configuration.verbosityLevel = VerbosityLevel.atLevel(1)
			},
			{ keyword, verboseString ->
				processor.value().checkEncountered(VERBOSE_MODE, 0)
				try
				{
					// This parseInt will (also) throw an exception if
					// it tries to parse "" as a result of the illegal
					// use of "=" without any items following.
					val level = Integer.parseInt(verboseString)
					configuration.verbosityLevel = VerbosityLevel.atLevel(level)
				}
				catch (e: NumberFormatException)
				{
					throw OptionProcessingException(
						"$keyword: Illegal argument.",
						e)
				}
			}))

		factory.addOption(
			GenericHelpOption(
				HELP,
				processor,
				"The Avail compiler understands the following options: ",
				helpStream))

		factory.addOption(DefaultOption(
			TARGET_MODULE_NAME,
			"The target module name for compilation and/or documentation "
				+ "generation. The module is specified via a path relative to "
				+ "an AVAIL_ROOTS root name. For example, if AVAIL_ROOTS "
				+ "specifies a root named \"foo\" at path "
				+ "/usr/local/avail/stuff/, and module \"frog\" is in the root "
				+ "directory as /usr/local/avail/stuff/frog, the target module "
				+ "name would be /foo/frog.")
			{ _, targetModuleString ->
				processor.value().checkEncountered(TARGET_MODULE_NAME, 0)
				try
				{
					configuration.targetModuleName =
						ModuleName(targetModuleString)
				}
				catch (e: OptionProcessingException)
				{
					throw OptionProcessingException(
						"«default»: ${e.message}",
						e)
				}
			})

		processor.value = factory.createOptionProcessor()
		return processor.value()
	}

	@Synchronized @Throws(ConfigurationException::class)
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
