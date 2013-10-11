/**
 * Compiler.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.annotations.Nullable;
import com.avail.builder.*;
import com.avail.compiler.AvailCompilerException;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.persistence.IndexedFileException;
import com.avail.tools.compiler.configuration.CommandLineConfigurator;
import com.avail.tools.compiler.configuration.CompilerConfiguration;
import com.avail.tools.compiler.configuration.EnvironmentConfigurator;
import com.avail.tools.compiler.configuration.StatisticReport;
import com.avail.tools.compiler.configuration.VerbosityLevel;
import com.avail.tools.configuration.ConfigurationException;
import com.avail.utility.Continuation3;
import com.avail.utility.Continuation4;
import com.avail.utility.NullOutputStream;

/**
 * The Avail command-line compiler understands the following options:
 *
 * --availRenames
 *     The absolute path to the renames file. This option overrides environment
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
 * -f
 * --clearRepositories
 *     The option to force removal of all repositories for which the Avail root
 *     path has valid source directories specified. This option can be used in
 *     isolation and will cause the repositories to be emptied. In an
 *     invocation with a valid target module name, the repositories will be
 *     cleared before compilation is attempted.
 *
 * -q
 * --quiet
 *     The option to mute all output originating from user code.
 *
 * -s
 * --showStatistics
 *     The option to request statistics about the most time-intensive
 *     operations in all categories ( -s or --showStatistics ) or for specific
 *     categories, using a comma-separated list of keywords
 *     ( --showStatistics=#,# ). This option overrides environment variables.
 *
 *     Possible values in # include:
 *     L2Operations - The most time-intensive level-two operations.
 *     DynamicLookups - The most time-intensive dynamic method lookups.
 *     Primitives - The primitives that are the most time-intensive to run
 *                  overall.
 *     PrimitiveReturnTypeChecks - The primitives that take the most time
 *                                 checking return types.
 *
 * -t
 * --showTiming
 *     Emits the time taken to clear the repositories, or the elapsed build
 *     time following a successful or failed build.
 *
 * -v
 * --verboseMode
 *     The option to request minimum verbosity ( -v or --verboseMode ) or
 *     manually set the verbosity level ( --verboseMode=# ). This option
 *     overrides environment variables.
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
public class Compiler
{
	/**
	 * Configure the {@linkplain Compiler compiler} to build the target
	 * {@linkplain ModuleDescriptor module}.
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
	 * @param configuration The configuration from which to read the verbosity
	 *                      level.
	 * @return A local tracker to store information about the progress of the
	 *         compilation of the current module.
	 */
	private static Continuation4<ModuleName, Long, Long, Long> localTracker(
		final CompilerConfiguration configuration)
	{
		return new Continuation4<ModuleName, Long, Long, Long>()
		{
			@Override
			public void value (
				final @Nullable ModuleName module,
				final @Nullable Long lineNumber,
				final @Nullable Long parsePosition,
				final @Nullable Long moduleSize)
			{
				assert module != null;
				assert lineNumber != null;
				assert parsePosition != null;
				assert moduleSize != null;

				final int percent = (int) ((parsePosition * 100) / moduleSize);
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
			}
		};
	}

	/**
	 * A local copy of the global status notification text is shared so a
	 * unified status notification can be output regardless of the verbosity
	 * level.
	 */
	@InnerAccess static String globalStatus = "";

	/**
	 * @param configuration The configuration from which to read the verbosity
	 *                      level.
	 * @return A global tracker to store information about the progress on
	 *         all modules to be compiled.
	 */
	private static Continuation3<ModuleName, Long, Long> globalTracker(
		final CompilerConfiguration configuration)
	{
		//
		return new Continuation3<ModuleName, Long, Long>()
		{
			@Override
			public void value (
				@Nullable final ModuleName module,
				@Nullable final Long processedBytes,
				@Nullable final Long totalBytes)
			{
				assert module != null;
				assert processedBytes != null;
				assert totalBytes != null;

				final int percent = (int) ((processedBytes * 100) / totalBytes);
				globalStatus = String.format(
					"Build Progress - %3d%%", percent);
				final VerbosityLevel level = configuration.verbosityLevel();
				if (level.displayGlobalProgress() &&
					!level.displayLocalProgress())
				{
					System.out.println(globalStatus);
				}
			}
		};
	}

	/**
	 * Clear all repositories for which a valid source directory has been
	 * specified.
	 *
	 * @param resolver The ModuleNameResolver which contains the locations of
	 *                 all of the Avail source directories and repositories.
	 */
	private static void doClearRepositories(final ModuleNameResolver resolver)
	{
		try
		{
			for (final ModuleRoot root : resolver.moduleRoots().roots())
			{
				final File dir = root.sourceDirectory();
				if (dir != null && dir.isDirectory())
				{
					root.repository().clear();
				}
			}
		}
		catch (final IOException e)
		{
			throw new IndexedFileException(e);
		}
	}

	/**
	 * Output the appropriate {@linkplain StatisticReport reports}.
	 *
	 * @param configuration The compiler configuration where the report settings
	 *                      are stored.
	 */
	private static void printReports(final CompilerConfiguration configuration)
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("\n\n");
		if (configuration.reports().contains(StatisticReport.L2_OPERATIONS))
		{
			L2Operation.reportStatsOn(builder);
			builder.append("\n");
		}
		if (configuration.reports().contains(StatisticReport.DYNAMIC_LOOKUPS))
		{
			Interpreter.reportDynamicLookups(builder);
			builder.append("\n");
		}
		if (configuration.reports().contains(StatisticReport.PRIMITIVES))
		{
			Primitive.reportRunTimes(builder);
			builder.append("\n");
		}
		if (configuration.reports().contains(
			StatisticReport.PRIMITIVE_RETURN_TYPE_CHECKS))
		{
			Primitive.reportReturnCheckTimes(builder);
			builder.append("\n");
		}
		System.out.print(builder.toString());
	}

	/**
	 * Output the details of the given {@linkplain RecursiveDependencyException
	 * exception}.
	 *
	 * @param e The exception whose details to output.
	 */
	private static void reportRecursiveDependency (
		final RecursiveDependencyException e)
	{
		final List<ResolvedModuleName> circuit = e.recursionPath();
		final StringBuilder sb = new StringBuilder(500);

		sb.append("ERROR: A recursive dependency was found in the build " +
			"path.");

		sb.append("\n\nCircuit entry point: ");
		sb.append(circuit.get(0).qualifiedName());

		sb.append("\nCircuit path: ");
		boolean firstTime = true;
		for (final ResolvedModuleName mod : circuit)
		{
			if (!firstTime)
			{
				sb.append(" < ");
			}
			else
			{
				firstTime = false;
			}
			sb.append(mod.localName());
		}

		final String errorMsg = sb.toString();
		System.err.println(errorMsg);
		return;
	}

	/**
	 * Output the details of the given {@linkplain UnresolvedDependencyException
	 * exception}.
	 *
	 * @param e The exception whose details to output.
	 */
	private static void reportUnresolvedDependency (
		final UnresolvedDependencyException e)
	{
		final StringBuilder sb = new StringBuilder(500);

		final ResolvedModuleName currentModule = e.referringModuleName();
		final String missingModule = e.unresolvedModuleName();

		sb.append("ERROR: An unresolved dependency was found in the " +
			"build path.");

		if (e instanceof UnresolvedModuleException)
		{
			final UnresolvedModuleException exc = (UnresolvedModuleException) e;

			sb.append("\n\nModule ");
			sb.append(currentModule);
			sb.append(" was unable to find \"");
			sb.append(missingModule);
			sb.append("\"; tried:\n");

			final ArrayList<ModuleName> names = exc.acceptablePaths();
			for (int i = 0; i < names.size(); i++)
			{
				sb.append("\t");
				sb.append(names.get(i).qualifiedName());
				sb.append("\n");
			}
		}
		else  //e is an instance of UnresolvedRootException
		{
			final UnresolvedRootException exc = (UnresolvedRootException) e;
			final String rootName = exc.unresolvedRootName();

			sb.append("\n\nRoot \"");
			sb.append(rootName);
			sb.append("\" was not found.\n");
		}

		final String errorMsg = sb.toString();
		System.err.println(errorMsg);
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

		// Obtain the start time.
		final long startTimeMillis = configuration.showTiming()
			? System.currentTimeMillis() : 0;

		// Clear the repositories, if requested.
		if (configuration.clearRepositories())
		{
			doClearRepositories(resolver);
		}

		final ModuleName moduleName = configuration.targetModuleName();

		final AvailRuntime runtime = new AvailRuntime(resolver);

		// Mute user output, if requested.
		if (configuration.quiet())
		{
			runtime.setStandardStreams(
				new PrintStream(new NullOutputStream()), null, null);
		}

		try
		{
			final AvailBuilder builder = new AvailBuilder(
				runtime,
				localTracker(configuration),
				globalTracker(configuration));
			builder.build(moduleName);

			// Successful compilation.
			if (configuration.hasReports())
			{
				printReports(configuration);
			}

			// Output timing details, if requested.
			if (configuration.showTiming())
			{
				final long stopTimeMillis = System.currentTimeMillis();
				final long timeElapsed = stopTimeMillis - startTimeMillis;
				System.out.printf("Time elapsed: %d.%03d s%n",
					timeElapsed / 1000, timeElapsed % 1000);
			}
		}
		catch (final RecursiveDependencyException e)
		{
			reportRecursiveDependency(e);
			return;
		}
		catch (final UnresolvedDependencyException e)
		{
			reportUnresolvedDependency(e);
			return;
		}
		catch (final AvailCompilerException e)
		{
			// User code error.
			System.err.println(e.getMessage());

			if (configuration.showTiming())
			{
				final long stopTimeMillis = System.currentTimeMillis();
				final long timeElapsed = stopTimeMillis - startTimeMillis;
				System.out.printf("%nTime elapsed: %d.%03ds%n",
					timeElapsed / 1000, timeElapsed % 1000);
			}

			return;
		}
		catch (final InterruptedException e)
		{
			// Programmer error within the compiler.
			e.printStackTrace();
			return;
		}
		finally
		{
			runtime.destroy();
		}
	}
}
