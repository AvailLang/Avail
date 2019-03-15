/*
 * Compiler.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.tools.compiler;

import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.builder.AvailBuilder;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ModuleRoot;
import com.avail.builder.RenamesFileParserException;
import com.avail.compiler.AvailCompiler.CompilerProgressReporter;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.io.ConsoleInputChannel;
import com.avail.io.ConsoleOutputChannel;
import com.avail.io.TextInterface;
import com.avail.performance.StatisticReport;
import com.avail.tools.compiler.configuration.CommandLineConfigurator;
import com.avail.tools.compiler.configuration.CompilerConfiguration;
import com.avail.tools.compiler.configuration.EnvironmentConfigurator;
import com.avail.tools.compiler.configuration.VerbosityLevel;
import com.avail.utility.NullOutputStream;
import com.avail.utility.configuration.ConfigurationException;
import com.avail.utility.evaluation.Continuation2;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

/**
 * The Avail command-line compiler understands the following options:
 *
 * --availRenames
 *     The path to the renames file. This option overrides environment
 *     variables.
 *
 * --availRoots
 *     The Avail roots, as a semicolon (;) separated list of module root
 *     specifications. Each module root specification comprises a logical root
 *     name, then an equals (=), then a module root location. A module root
 *     location comprises the absolute path to a binary module repository, then
 *     optionally a comma (,) and the absolute path to a source package. This
 *     option overrides environment variables.
 *
 * -c
 * --compile
 *     Compile the target module and its ancestors.
 *
 * -f
 * --clearRepositories
 *     Force removal of all repositories for which the Avail root path has valid
 *     source directories specified. This option can be used in isolation and
 *     will cause the repositories to be emptied. In an invocation with a valid
 *     target module name, the repositories will be cleared before compilation
 *     is attempted. Mutually exclusive with -f.
 *
 * -g
 * --generateDocumentation
 *     Generate Stacks documentation for the target module and its ancestors.
 *     The relevant repositories must already contain compilations for every
 *     module implied by the request. Mutually exclusive with -f.
 *
 * -G
 * --documentationPath
 *     The path to the output directory where documentation and data files will
 *     appear when Stacks documentation is generated. Requires -g.
 *
 * -q
 * --quiet
 *     Mute all output originating from user code.
 *
 * -s
 * --showStatistics
 *     Request statistics about the most time-intensive operations in all
 *     categories ( -s or --showStatistics ) or for specific categories, using a
 *     comma-separated list of keywords ( --showStatistics=#,# ). Requires -c.
 *
 *     Possible values in # include:
 *     L2Operations - The most time-intensive level-two operations.
 *     DynamicLookups - The most time-intensive dynamic method lookups.
 *     Primitives - The primitives that are the most time-intensive to run
 *                  overall.
 *     PrimitiveReturnTypeChecks - The primitives that take the most time
 *                                 checking return types.
 *
 * -v
 * --verboseMode
 *     Request minimum verbosity ( -v or --verboseMode ) or manually set the
 *     verbosity level ( --verboseMode=# ).
 *
 *     Possible values for # include:
 *     0 - Zero extra verbosity. Only error messages will be output. This is
 *         the default level for the compiler and is used when the verboseMode
 *         option is not used.
 *     1 - The minimum verbosity level. The global progress and any error
 *         messages will be output. This is the default level for this option
 *         when a level is not specified.
 *     2 - Global progress is output along with the local module compilation
 *         progress and any error messages.
 *
 * -?
 *     Display help text containing a description of the application and an
 *     enumeration of its options.
 *
 * &lt;bareword&gt;
 *     The target module name for compilation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public final class Compiler
{
	/**
	 * Forbid instantiation.
	 */
	private Compiler ()
	{
		// No implementation required.
	}

	/**
	 * Configure the {@code Compiler} to build the target {@linkplain
	 * ModuleDescriptor module}.
	 *
	 * @param args
	 *        The command-line arguments.
	 * @return A viable {@linkplain CompilerConfiguration compiler
	 *         configuration}.
	 * @throws ConfigurationException
	 *         If configuration fails for any reason.
	 */
	private static CompilerConfiguration configure (final String[] args)
		throws ConfigurationException
	{
		final CompilerConfiguration configuration = new CompilerConfiguration();
		// Update the configuration using the environment first.
		final EnvironmentConfigurator environmentConfigurator =
			new EnvironmentConfigurator(configuration);
		environmentConfigurator.updateConfiguration();
		// Update the configuration using the command-line arguments.
		final CommandLineConfigurator commandLineConfigurator =
			new CommandLineConfigurator(configuration, args, System.out);
		commandLineConfigurator.updateConfiguration();
		return configuration;
	}

	/**
	 * @param configuration
	 *        The configuration from which to read the verbosity level.
	 * @return A local tracker to store information about the progress of the
	 *         compilation of the current module.
	 */
	private static CompilerProgressReporter localTracker (
		final CompilerConfiguration configuration)
	{
		return (module, moduleSize, position) ->
		{
			assert module != null;
			assert moduleSize != null;
			assert position != null;

			final int percent = (int) ((position * 100) / moduleSize);
			String modName = module.qualifiedName();
			final int maxModuleNameLength = 61;
			final int len = modName.length();
			if (len > maxModuleNameLength)
			{
				modName = "…" + modName.substring(
					len - maxModuleNameLength + 1, len);
			}

			final String localStatus = String.format(
				"  |  %-61s - %3d%%", modName, percent);
			final VerbosityLevel level = configuration.verbosityLevel();
			if (level.displayLocalProgress())
			{
				System.out.println(globalStatus + localStatus);
			}
		};
	}

	/**
	 * A local copy of the global status notification text is shared so a
	 * unified status notification can be output regardless of the verbosity
	 * level.
	 */
	@InnerAccess static volatile String globalStatus = "";

	/**
	 * @param configuration The configuration from which to read the verbosity
	 *                      level.
	 * @return A global tracker to store information about the progress on
	 *         all modules to be compiled.
	 */
	private static Continuation2<Long, Long> globalTracker(
		final CompilerConfiguration configuration)
	{
		return (processedBytes, totalBytes) ->
		{
			assert processedBytes != null;
			assert totalBytes != null;

			final int perThousand =
				(int) ((processedBytes * 1000) / totalBytes);
			final float percent = perThousand / 10.0f;
			globalStatus = String.format(
				"Build Progress - %3.1f%%", percent);
			final VerbosityLevel level = configuration.verbosityLevel();
			if (level.displayGlobalProgress() &&
				!level.displayLocalProgress())
			{
				System.out.println(globalStatus);
			}
		};
	}

	/**
	 * Clear all repositories for which a valid source directory has been
	 * specified.
	 *
	 * @param resolver
	 *        The ModuleNameResolver which contains the locations of all of the
	 *        Avail source directories and repositories.
	 */
	private static void doClearRepositories (
		final ModuleNameResolver resolver)
	{
		for (final ModuleRoot root : resolver.moduleRoots().roots())
		{
			final @Nullable File dir = root.sourceDirectory();
			if (dir != null && dir.isDirectory())
			{
				root.repository().clear();
			}
		}
	}

	/**
	 * The entry point for command-line invocation of the Avail compiler.
	 *
	 * @param args The command-line arguments.
	 */
	public static void main (final String[] args)
	{
		// Configure the compiler according to the command-line arguments and
		// ensure that any supplied paths are syntactically valid.
		final CompilerConfiguration configuration;
		final ModuleNameResolver resolver;
		try
		{
			configuration = configure(args);
			resolver = configuration.moduleNameResolver();
		}
		catch (final ConfigurationException |
					 FileNotFoundException |
					 RenamesFileParserException e)
		{
			// The command-line arguments were malformed, or
			// The arguments specified a missing file, or
			// The renames file did not parse correctly
			System.err.println(e.getMessage());
			return;
		}

		// Clear the repositories, if requested.
		if (configuration.clearRepositories())
		{
			doClearRepositories(resolver);
		}

		final ModuleName moduleName = configuration.targetModuleName();
		final AvailRuntime runtime = new AvailRuntime(resolver);

		// Mute output, if requested.
		if (configuration.quiet())
		{
			runtime.setTextInterface(new TextInterface(
				new ConsoleInputChannel(System.in),
				new ConsoleOutputChannel(
					new PrintStream(new NullOutputStream())),
				new ConsoleOutputChannel(System.err)));
		}

		try
		{
			final @Nullable AvailBuilder builder;
			if (configuration.compileModules()
				|| configuration.generateDocumentation())
			{
				builder = new AvailBuilder(runtime);
			}
			else
			{
				builder = null;
			}

			// Compile modules.
			if (configuration.compileModules())
			{
				assert builder != null;
				builder.buildTarget(
					moduleName,
					localTracker(configuration),
					globalTracker(configuration));

				// Successful compilation.
				if (configuration.hasReports())
				{
					System.out.append(
						StatisticReport.produceReports(
							configuration.reports()));
				}
			}

			// Generate Stacks documentation.
			if (configuration.generateDocumentation())
			{
				assert builder != null;
				builder.generateDocumentation(
					moduleName, configuration.documentationPath());
			}
		}
		finally
		{
			runtime.destroy();
		}
	}
}
