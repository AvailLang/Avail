/*
 * CommandLineConfigurator.java
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

package com.avail.tools.compiler.configuration;

import com.avail.annotations.InnerAccess;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleRoots;
import com.avail.builder.RenamesFileParser;
import com.avail.performance.StatisticReport;
import com.avail.tools.options.DefaultOption;
import com.avail.tools.options.GenericHelpOption;
import com.avail.tools.options.GenericOption;
import com.avail.tools.options.OptionProcessingException;
import com.avail.tools.options.OptionProcessor;
import com.avail.tools.options.OptionProcessorFactory;
import com.avail.utility.MutableOrNull;
import com.avail.utility.configuration.ConfigurationException;
import com.avail.utility.configuration.Configurator;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

import static com.avail.performance.StatisticReport.reportFor;
import static com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Provides the configuration for the command-line compiler. Specifies the
 * options that are available as arguments to the compiler and their effects
 * on the compile process or user interface.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public class CommandLineConfigurator
implements Configurator<CompilerConfiguration>
{
	/**
	 * {@code OptionKey} enumerates the valid configuration options.
	 */
	enum OptionKey
	{
		/**
		 * Specification of the {@linkplain File path} to the {@linkplain
		 * RenamesFileParser renames file}.
		 */
		AVAIL_RENAMES,

		/**
		 * Specification of the {@linkplain ModuleRoots Avail roots}.
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
		 * The option to request standard verbosity or set the {@linkplain
		 * VerbosityLevel verbosity level}.
		 */
		VERBOSE_MODE,

		/**
		 * Request display of help text.
		 */
		HELP,

		/**
		 * Specification of the target {@linkplain ModuleName module name}.
		 */
		TARGET_MODULE_NAME

	}

	/**
	 * Create an {@linkplain OptionProcessor option processor} suitable for
	 * {@linkplain #updateConfiguration() updating} a {@linkplain
	 * CompilerConfiguration compiler configuration}.
	 *
	 * @return An option processor.
	 */
	private OptionProcessor<OptionKey> createOptionProcessor ()
	{
		final MutableOrNull<OptionProcessor<OptionKey>> processor =
			new MutableOrNull<>();
		final OptionProcessorFactory<OptionKey> factory =
			new OptionProcessorFactory<>(OptionKey.class);

		factory.addOption(new GenericOption<>(
			AVAIL_RENAMES,
			singletonList("availRenames"),
			"The path to the renames file. This option overrides environment "
				+ "variables.",
			(keyword, renamesString) ->
			{
				processor.value().checkEncountered(AVAIL_RENAMES, 0);
				configuration.setRenamesFilePath(renamesString);
			}));

		factory.addOption(new GenericOption<>(
			AVAIL_ROOTS,
			singletonList("availRoots"),
			"The Avail roots, as a semicolon (;) separated list of module root "
				+ "specifications. Each module root specification comprises a "
				+ "logical root name, then an equals (=), then a module root "
				+ "location. A module root location comprises the absolute "
				+ "path to a binary module repository, then optionally a comma "
				+ "(,) and the absolute path to a source package. This option "
				+ "overrides environment variables.",
			(keyword, rootsString) ->
			{
				processor.value().checkEncountered(AVAIL_ROOTS, 0);
				configuration.setAvailRootsPath(rootsString);
			}));

		factory.addOption(new GenericOption<>(
			COMPILE_MODULES,
			asList("c", "compile"),
			"Compile the target module and its ancestors.",
			(keyword) ->
			{
				processor.value().checkEncountered(COMPILE_MODULES, 0);
				configuration.setCompileModulesFlag();
			}));

		factory.addOption(new GenericOption<>(
			CLEAR_REPOSITORIES,
			asList("f", "clearRepositories"),
			"Force removal of all repositories for which the Avail root path "
				+ "has valid source directories specified. This option can be "
				+ "used in isolation and will cause the repositories to be "
				+ "emptied. In an invocation with a valid target module name, "
				+ "the repositories will be cleared before compilation is "
				+ "attempted. Mutually exclusive with -g.",
			(keyword) ->
			{
				processor.value().checkEncountered(CLEAR_REPOSITORIES, 0);
				processor.value().checkEncountered(GENERATE_DOCUMENTATION, 0);
				configuration.setClearRepositoriesFlag();
			}));

		factory.addOption(new GenericOption<>(
			GENERATE_DOCUMENTATION,
			asList("g", "generateDocumentation"),
			"Generate Stacks documentation for the target module and its "
				+ "ancestors. The relevant repositories must already contain "
				+ "compilations for every module implied by the request. "
				+ "Mutually exclusive with -f.",
			(keyword) ->
			{
				processor.value().checkEncountered(GENERATE_DOCUMENTATION, 0);
				processor.value().checkEncountered(CLEAR_REPOSITORIES, 0);
				processor.value().checkEncountered(SHOW_STATISTICS, 0);
				configuration.setGenerateDocumenationFlag();
			}));

		factory.addOption(new GenericOption<>(
			DOCUMENTATION_PATH,
			asList("G", "documentationPath"),
			"The path to the output directory where documentation and data "
				+ "files will appear when Stacks documentation is generated. "
				+ "Requires -g.",
			(keyword, pathString) ->
			{
				processor.value().checkEncountered(DOCUMENTATION_PATH, 0);
				final Path path;
				try
				{
					path = Paths.get(pathString);
				}
				catch (final InvalidPathException e)
				{
					throw new OptionProcessingException(
						keyword
							+ ": invalid path: "
							+ e.getLocalizedMessage());
				}
				configuration.setDocumentationPath(path);
			}));

		factory.addOption(new GenericOption<>(
			QUIET,
			asList("q", "quiet"),
			"Mute all output originating from user code.",
			(keyword) ->
			{
				processor.value().checkEncountered(QUIET, 0);
				configuration.setQuietFlag();
			}));

		factory.addOption(new GenericOption<>(
			SHOW_STATISTICS,
			asList("s", "showStatistics"),
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
			(keyword) ->
			{
				processor.value().checkEncountered(SHOW_STATISTICS, 0);
				processor.value().checkEncountered(GENERATE_DOCUMENTATION, 0);
				final EnumSet<StatisticReport> reports =
					EnumSet.allOf(StatisticReport.class);
				configuration.setReports(reports);
			},
			(keyword, reportsString) ->
			{
				processor.value().checkEncountered(SHOW_STATISTICS, 0);
				processor.value().checkEncountered(GENERATE_DOCUMENTATION, 0);
				final String[] reportsArr = reportsString.split(",");
				final EnumSet<StatisticReport> reports =
					EnumSet.noneOf(StatisticReport.class);
				for (final String reportName : reportsArr)
				{
					final @Nullable StatisticReport report =
						reportFor(reportName);

					// This will also catch the illegal use of "="
					// without any items following.
					if (report == null)
					{
						throw new OptionProcessingException(
							keyword + ": Illegal argument.");
					}
					reports.add(report);
				}
				configuration.setReports(reports);
			}));

		factory.addOption(new GenericOption<>(
			VERBOSE_MODE,
			asList("v", "verboseMode"),
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
			(keyword) ->
			{
				processor.value().checkEncountered(VERBOSE_MODE, 0);
				configuration.setVerbosityLevel(
					VerbosityLevel.atLevel(1));
			},
			(keyword, verboseString) ->
			{
				processor.value().checkEncountered(VERBOSE_MODE, 0);
				try
				{
					// This parseInt will (also) throw an exception if
					// it tries to parse "" as a result of the illegal
					// use of "=" without any items following.
					final int level = Integer.parseInt(verboseString);
					configuration.setVerbosityLevel(
						VerbosityLevel.atLevel(level));
				}
				catch (final NumberFormatException e)
				{
					throw new OptionProcessingException(
						keyword + ": Illegal argument.",
						e);
				}
			}));

		factory.addOption(new GenericHelpOption<>(
			HELP,
			processor,
			"The Avail compiler understands the following options: ",
			helpStream));

		factory.addOption(new DefaultOption<>(
			TARGET_MODULE_NAME,
			"The target module name for compilation and/or documentation "
				+ "generation. The module is specified via a path relative to "
				+ "an AVAIL_ROOTS root name. For example, if AVAIL_ROOTS "
				+ "specifies a root named \"foo\" at path "
				+ "/usr/local/avail/stuff/, and module \"frog\" is in the root "
				+ "directory as /usr/local/avail/stuff/frog, the target module "
				+ "name would be /foo/frog.",
			(keyword, targetModuleString) ->
			{
				processor.value().checkEncountered(TARGET_MODULE_NAME, 0);
				try
				{
					configuration.setTargetModuleName(
						new ModuleName(targetModuleString));
				}
				catch (final OptionProcessingException e)
				{
					throw new OptionProcessingException(
						"«default»: " + e.getMessage(),
						e);
				}
			}));

		processor.value = factory.createOptionProcessor();
		return processor.value();
	}

	/** The {@linkplain CompilerConfiguration configuration}. */
	@InnerAccess final CompilerConfiguration configuration;

	/** The command line arguments. */
	private final String[] commandLineArguments;

	/**
	 * The {@linkplain Appendable appendable} to which help text should be
	 * written.
	 */
	private final Appendable helpStream;

	@Override
	public CompilerConfiguration configuration ()
	{
		return configuration;
	}

	/**
	 * Has the {@linkplain CommandLineConfigurator configurator} been run yet?
	 */
	private boolean isConfigured;

	@Override
	public synchronized void updateConfiguration ()
		throws ConfigurationException
	{
		if (!isConfigured)
		{
			try
			{
				createOptionProcessor().processOptions(commandLineArguments);
				isConfigured = true;
			}
			catch (final Exception e)
			{
				throw new ConfigurationException(
					"unexpected configuration error", e);
			}
		}
	}

	/**
	 * Construct a new {@code CommandLineConfigurator}.
	 *
	 * @param configuration
	 *        The base {@linkplain CompilerConfiguration compiler
	 *        configuration}.
	 * @param commandLineArguments
	 *        The command-line arguments.
	 * @param helpStream
	 *        The {@link Appendable} to which help text should be written.
	 */
	public CommandLineConfigurator (
		final CompilerConfiguration configuration,
		final String[] commandLineArguments,
		final Appendable helpStream)
	{
		this.configuration = configuration;
		this.commandLineArguments = commandLineArguments.clone();
		this.helpStream = helpStream;
	}
}
