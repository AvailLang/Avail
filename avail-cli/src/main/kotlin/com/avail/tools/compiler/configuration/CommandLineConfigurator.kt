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
import com.avail.builder.RenamesFileParserException
import com.avail.performance.StatisticReport
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.AVAIL_RENAMES
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.AVAIL_ROOTS
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.CLEAR_REPOSITORIES
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.COMPILE_MODULES
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.DOCUMENTATION_PATH
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.GENERATE_DOCUMENTATION
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.HELP
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.QUIET
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.SHOW_STATISTICS
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.TARGET_MODULE_NAME
import com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.VERBOSE_MODE
import com.avail.tools.options.OptionProcessingException
import com.avail.tools.options.OptionProcessor
import com.avail.tools.options.OptionProcessorFactory
import com.avail.utility.configuration.ConfigurationException
import com.avail.utility.configuration.Configurator
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.EnumSet

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
		 * Specification of the [path][File] to the
		 * [renames&#32;file][RenamesFileParser].
		 */
		AVAIL_RENAMES,

		/**
		 * Specification of the [Avail&#32;roots][ModuleRoots].
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
		 * The option to request standard verbosity or set the
		 * [verbosity&#32;level][VerbosityLevel].
		 */
		VERBOSE_MODE,

		/**
		 * Request display of help text.
		 */
		HELP,

		/**
		 * Specification of the target [module&#32;name][ModuleName].
		 */
		TARGET_MODULE_NAME
	}

	/**
	 * Create an [option&#32;processor][OptionProcessor] suitable for
	 * [updating][updateConfiguration] a
	 * [compiler&#32;configuration][CompilerConfiguration].
	 *
	 * @return
	 *   An option processor.
	 */
	private fun createOptionProcessor() =
		OptionProcessorFactory.create<OptionKey> {
			optionWithArgument(
				AVAIL_RENAMES,
				listOf("availRenames"),
				"The path to the renames file. This option overrides "
				+ "environment variables.")
				{
					configuration.renamesFilePath = argument
				}
			optionWithArgument(
				AVAIL_ROOTS,
				listOf("availRoots"),
				"The Avail roots, as a semicolon (;) separated list of module root "
				+ "specifications. Each module root specification comprises a "
				+ "logical root name, then an equals (=), then a module root "
				+ "location. A module root location comprises the absolute "
				+ "path to a binary module repository, then optionally a comma "
				+ "(,) and the absolute path to a source package. This option "
				+ "overrides environment variables.")
				{
					configuration.availRootsPath = argument
				}
			option(
				COMPILE_MODULES,
				listOf("c", "compile"),
				"Compile the target module and its ancestors.")
				{
					configuration.compileModules = true
				}
			option(
				CLEAR_REPOSITORIES,
				listOf("f", "clearRepositories"),
				"Force removal of all repositories for which the Avail root path "
				+ "has valid source directories specified. This option can be "
				+ "used in isolation and will cause the repositories to be "
				+ "emptied. In an invocation with a valid target module name, "
				+ "the repositories will be cleared before compilation is "
				+ "attempted. Mutually exclusive with -g.")
				{
					processor.checkEncountered(GENERATE_DOCUMENTATION, 0)
					configuration.clearRepositories = true
				}
			option(
				GENERATE_DOCUMENTATION,
				listOf("g", "generateDocumentation"),
				"Generate Stacks documentation for the target module and its "
				+ "ancestors. The relevant repositories must already contain "
				+ "compilations for every module implied by the request. "
				+ "Mutually exclusive with -f.")
				{
					processor.checkEncountered(CLEAR_REPOSITORIES, 0)
					processor.checkEncountered(SHOW_STATISTICS, 0)
					configuration.generateDocumentation = true
				}
			optionWithArgument(
				DOCUMENTATION_PATH,
				listOf("G", "documentationPath"),
				"The path to the output directory where documentation and data "
				+ "files will appear when Stacks documentation is generated. "
				+ "Requires -g.")
				{
					val path: Path
					try
					{
						path = Paths.get(argument)
					}
					catch (e: InvalidPathException)
					{
						throw OptionProcessingException(
							"$keyword: invalid path: ${e.localizedMessage}")
					}
					configuration.documentationPath = path
				}
			option(
				QUIET,
				listOf("q", "quiet"),
				"Mute all output originating from user code.")
				{
					configuration.quiet = true
				}
			optionWithArgument(
				SHOW_STATISTICS,
				listOf("s", "showStatistics"),
				"Request statistics about the most time-intensive operations in "
				+ "all categories ( -s or --showStatistics ) or for specific "
				+ "categories, using a comma-separated list of keywords "
				+ "( --showStatistics=#,# ). Requires -c.\n\n"
				+ "Possible values in # include:\n"
				+ "* - All statistics\n"
				+ "L2Operations - The most time-intensive level-two "
				+ "operations.\n"
				+ "DynamicLookups - The most time-intensive dynamic method "
				+ "lookups.\n"
				+ "Primitives - The primitives that are the most "
				+ "time-intensive to run overall.\n"
				+ "PrimitiveReturnTypeChecks - The primitives that take the "
				+ "most time checking return types.")
				{
					processor.checkEncountered(GENERATE_DOCUMENTATION, 0)
					val reportsArr = argument
						.split(",".toRegex())
						.filter(String::isNotEmpty)
						.ifEmpty { listOf("") }  // Triggers exception below.
					val reports = EnumSet.noneOf(StatisticReport::class.java)
					for (reportName in reportsArr) {
						if (reportName == "*") {
							reports.addAll(
								EnumSet.allOf(StatisticReport::class.java))
						}
						else {
							reports.add(
								StatisticReport.reportFor(reportName)
									?: throw OptionProcessingException(
										"$keyword: Illegal argument."))
						}
					}
					configuration.reports = reports
				}
			optionWithArgument(
				VERBOSE_MODE,
				listOf("v", "verboseMode"),
				"Request verbosity ( -v # or --verboseMode=# ).\n\n"
				+ "Possible values for # include:\n"
				+ "0 - Zero extra verbosity. Only error messages will be "
				+ "output. This is the default level for the compiler and is "
				+ "used when the verboseMode option is not used.\n"
				+ "1 - The minimum verbosity level. The global progress and "
				+ "any error messages will be output.\n"
				+ "2 - Global progress is output along with the local module "
				+ "compilation progress and any error messages.")
				{
					try
					{
						// This parseInt will (also) throw an exception if
						// it tries to parse "" as a result of the illegal
						// use of "=" without any items following.
						val level = Integer.parseInt(argument)
						configuration.verbosityLevel = VerbosityLevel.atLevel(level)
					}
					catch (e: NumberFormatException)
					{
						throw OptionProcessingException(
							"$keyword: Illegal argument.",
							e)
					}
				}
			helpOption(
				HELP,
				"The Avail compiler understands the following options: ",
				helpStream)
			defaultOption(
				TARGET_MODULE_NAME,
				"The target module name for compilation and/or documentation "
				+ "generation. The module is specified via a path relative to "
				+ "an AVAIL_ROOTS root name. For example, if AVAIL_ROOTS "
				+ "specifies a root named \"foo\" at path "
				+ "/usr/local/avail/stuff/, and module \"frog\" is in the root "
				+ "directory as /usr/local/avail/stuff/frog, the target module "
				+ "name would be /foo/frog.")
				{
					  try
					  {
						  configuration.targetModuleName =
							  ModuleName(argument)
					  }
					  catch (e: OptionProcessingException)
					  {
						  throw OptionProcessingException(
							  "«default»: ${e.message}",
							  e)
					  }
				}
			configuration.rule(
				"Must include either --compile, --generateDocumentation, "
					+ "--clearRepositories, or -?"
			) {
				compileModules || generateDocumentation || clearRepositories
			}
			configuration.rule("Could not resolve specified module") {
				try {
					// Just try to create a module name resolver. If this fails,
					// then the configuration is invalid. Otherwise, it should
					// be okay.
					moduleNameResolver
					true
				} catch (e: FileNotFoundException) {
					false
				} catch (e: RenamesFileParserException) {
					false
				}
			}
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
