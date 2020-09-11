/*
 * Compiler.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.tools.compiler

import com.avail.AvailRuntime
import com.avail.builder.AvailBuilder
import com.avail.builder.ModuleNameResolver
import com.avail.builder.RenamesFileParserException
import com.avail.compiler.CompilerProgressReporter
import com.avail.compiler.GlobalProgressReporter
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.io.ConsoleInputChannel
import com.avail.io.ConsoleOutputChannel
import com.avail.io.TextInterface
import com.avail.performance.StatisticReport
import com.avail.tools.compiler.configuration.CommandLineConfigurator
import com.avail.tools.compiler.configuration.CompilerConfiguration
import com.avail.tools.compiler.configuration.EnvironmentConfigurator
import com.avail.utility.NullOutputStream
import com.avail.utility.configuration.ConfigurationException
import java.io.FileNotFoundException
import java.io.PrintStream
import java.util.concurrent.locks.ReentrantLock

/**
 * The Avail command-line compiler understands the following options:
 *
 * --availRenames
 *  > The path to the renames file. This option overrides environment variables.
 *
 * --availRoots
 *  > The Avail roots, as a semicolon (;) separated list of module root
 *  > specifications. Each module root specification comprises a logical root
 *  > name, then an equals (=), then a module root location. A module root
 *  > location comprises the absolute path to a binary module repository, then
 *  > optionally a comma (,) and the absolute path to a source package. This
 *  > option overrides environment variables.
 *
 * -c
 * --compile
 * > Compile the target module and its ancestors.
 *
 * -f
 * --clearRepositories
 * > Force removal of all repositories for which the Avail root path has valid
 * > source directories specified. This option can be used in isolation and
 * > will cause the repositories to be emptied. In an invocation with a valid
 * > target module name, the repositories will be cleared before compilation
 * > is attempted. Mutually exclusive with -f.
 *
 * -g
 * --generateDocumentation
 * > Generate Stacks documentation for the target module and its ancestors. The
 * > relevant repositories must already contain compilations for every module
 * > implied by the request. Mutually exclusive with -f.
 *
 * -G
 * --documentationPath
 * > The path to the output directory where documentation and data files will
 * > appear when Stacks documentation is generated. Requires -g.
 *
 * -q
 * --quiet
 * > Mute all output originating from user code.
 *
 * -s
 * --showStatistics
 * > Request statistics about the most time-intensive operations in all
 * > categories ( -s or --showStatistics ) or for specific categories, using a
 * > comma-separated list of keywords ( --showStatistics=#,# ). Requires -c.
 *
 * > Possible values in # include:
 * > L2Operations — The most time-intensive level-two operations.
 * > DynamicLookups — The most time-intensive dynamic method lookups.
 * > Primitives — The primitives that are the most time-intensive to run
 * >              overall.
 * > PrimitiveReturnTypeChecks — The primitives that take the most time checking
 * >                             return types.
 *
 * -v
 * --verboseMode
 * > Request minimum verbosity ( -v or --verboseMode ) or manually set the
 * > verbosity level ( --verboseMode=# ).
 *
 * > Possible values for # include:
 * > 0 — Zero extra verbosity. Only error messages will be output. This is
 * >     the default level for the compiler and is used when the `verboseMode`
 * >     option is not used.
 * > 1 — The minimum verbosity level. The global progress and any error messages
 * >     will be output. This is the default level for this option when a level
 * >     is not specified.
 * > 2 — Global progress is output along with the local module compilation
 * >     progress and any error messages.
 *
 * -?
 * > Display help text containing a description of the application and an
 * > enumeration of its options.
 *
 * &lt;bareword&gt;
 * > The target module name for compilation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
object Compiler
{
	/**
	 * Configure the `Compiler` to build the target [module][ModuleDescriptor].
	 *
	 * @param args
	 *   The command-line arguments.
	 * @return
	 *   A viable [compiler][CompilerConfiguration].
	 * @throws ConfigurationException
	 *   If configuration fails for any reason.
	 */
	@Throws(ConfigurationException::class)
	private fun configure(args: Array<String>): CompilerConfiguration
	{
		val configuration = CompilerConfiguration()
		// Update the configuration using the environment first.
		val environmentConfigurator = EnvironmentConfigurator(configuration)
		environmentConfigurator.updateConfiguration()
		// Update the configuration using the command-line arguments.
		val commandLineConfigurator =
			CommandLineConfigurator(configuration, args, System.out)
		commandLineConfigurator.updateConfiguration()
		return configuration
	}

	/**
	 * The [lock][ReentrantLock] that serializes access to the trackers.
	 */
	private val statusLock = ReentrantLock()

	/**
	 * A local copy of the global status notification text is shared so a
	 * unified status notification can be output regardless of the verbosity
	 * level.
	 */
	private var globalStatus = ""

	/**
	 * Answer a global tracker to store information about the progress on all
	 * modules to be compiled.
	 *
	 * @param configuration
	 *   The configuration from which to read the verbosity level.
	 * @return
	 *   A global tracker.
	 */
	private fun globalTracker(
			configuration: CompilerConfiguration): GlobalProgressReporter =
		{ processedBytes, totalBytes ->
			synchronized(statusLock) {
				val perThousand = (processedBytes * 1000 / totalBytes).toInt()
				val percent = perThousand / 10.0f
				val status = String.format(
					"\u001b[33mGlobal\u001b[0m - %5.1f%%", percent)
				val level = configuration.verbosityLevel
				if (level.displayGlobalProgress && !level.displayLocalProgress)
				{
					if (System.console() !== null)
					{
						// Erase the previous global status.
						print("\b".repeat(globalStatus.length))
						print(status)
					}
					else
					{
						println(status)
					}
				}
				globalStatus = status
			}
		}

	/**
	 * The local status is preserved across to calls to permit overwrite effects
	 * on [consoles][System.console].
	 */
	private var localStatus = ""

	/**
	 * Answer a local tracker to store information about the progress of the
	 * compilation of the current module.
	 *
	 * @param configuration
	 *   The configuration from which to read the verbosity level.
	 * @return
	 *   A local tracker.
	 */
	private fun localTracker(
			configuration: CompilerConfiguration): CompilerProgressReporter =
		{ module, moduleSize, position, line ->
			synchronized(statusLock) {
				val level = configuration.verbosityLevel
				if (level.displayLocalProgress)
				{
					val percent = (position * 100 / moduleSize).toInt()
					var modName = module.qualifiedName
					var maxModuleNameLength = 61
					if (line != Int.MAX_VALUE) {
						modName += "\u001b[35m:$line"
						maxModuleNameLength += 5  // Just the escape sequence.
					}
					val len = modName.length
					if (len > maxModuleNameLength)
					{
						modName = "…" + modName.substring(
							len - maxModuleNameLength + 1, len)
					}

					val status = String.format(
						"%s  |  \u001b[34m%-${maxModuleNameLength}s" +
							"\u001b[0m - %3d%%",
						globalStatus,
						modName,
						percent)
					if (System.console() !== null)
					{
						print("\b".repeat(localStatus.length))
						if (position != moduleSize)
						{
							print(status)
						}
					}
					else
					{
						println(status)
					}
					localStatus = status
				}
			}
		}

	/**
	 * Clear all repositories for which a valid source directory has been
	 * specified.
	 *
	 * @param resolver
	 *   The ModuleNameResolver which contains the locations of all of the Avail
	 *   source directories and repositories.
	 */
	private fun doClearRepositories(resolver: ModuleNameResolver)
	{
		resolver.moduleRoots.roots.forEach { root ->
			val dir = root.sourceDirectory
			if (dir !== null && dir.isDirectory)
			{
				root.clearRepository()
			}
		}
	}

	/**
	 * The entry point for command-line invocation of the Avail compiler.
	 *
	 * @param args
	 *   The command-line arguments.
	 */
	@JvmStatic
	fun main(args: Array<String>)
	{
		// Configure the compiler according to the command-line arguments and
		// ensure that any supplied paths are syntactically valid.
		val configuration: CompilerConfiguration
		val resolver: ModuleNameResolver
		try
		{
			configuration = configure(args)
			resolver = configuration.moduleNameResolver!!
		}
		catch (e: ConfigurationException)
		{
			// The command-line arguments were malformed, or
			// The arguments specified a missing file, or
			// The renames file did not parse correctly.
			System.err.println(e.message)
			return
		}
		catch (e: FileNotFoundException)
		{
			System.err.println(e.message)
			return
		}
		catch (e: RenamesFileParserException)
		{
			System.err.println(e.message)
			return
		}

		// Clear the repositories, if requested.
		if (configuration.clearRepositories)
		{
			doClearRepositories(resolver)
		}

		val moduleName = configuration.targetModuleName
		val runtime = AvailRuntime(resolver)

		// Mute output, if requested.
		if (configuration.quiet)
		{
			runtime.setTextInterface(
				TextInterface(
					ConsoleInputChannel(System.`in`),
					ConsoleOutputChannel(
						PrintStream(NullOutputStream())),
					ConsoleOutputChannel(System.err)))
		}

		try
		{
			// Compile modules.
			if (configuration.compileModules)
			{
				val builder = AvailBuilder(runtime)
				builder.buildTarget(
					moduleName!!,
					localTracker(configuration),
					globalTracker(configuration),
					builder.buildProblemHandler)

				// Successful compilation.
				if (configuration.reports.isNotEmpty())
				{
					System.out.append(
						StatisticReport.produceReports(
							configuration.reports))
				}
			}

			// Generate Stacks documentation.
			if (configuration.generateDocumentation)
			{
				val builder = AvailBuilder(runtime)
				builder.generateDocumentation(
					moduleName!!,
					configuration.documentationPath,
					builder.buildProblemHandler)
			}
		}
		finally
		{
			runtime.destroy()
		}
	}
}
