/**
 * CommandLineConfigurator.java
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

package com.avail.tools.compiler.configuration;

import static java.util.Arrays.asList;
import static com.avail.tools.compiler.configuration.CommandLineConfigurator.OptionKey.*;
import java.io.File;
import java.util.EnumSet;
import com.avail.annotations.InnerAccess;
import com.avail.annotations.Nullable;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleRoots;
import com.avail.builder.RenamesFileParser;
import com.avail.tools.configuration.ConfigurationException;
import com.avail.tools.configuration.Configurator;
import com.avail.tools.options.DefaultOption;
import com.avail.tools.options.GenericHelpOption;
import com.avail.tools.options.GenericOption;
import com.avail.tools.options.OptionProcessingException;
import com.avail.tools.options.OptionProcessor;
import com.avail.tools.options.OptionProcessorFactory;
import com.avail.utility.Continuation2;
import com.avail.utility.MutableOrNull;

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
	static enum OptionKey
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
		 * The option to force removal of all repositories for which the Avail
		 * roots path has valid source directories specified.
		 */
		CLEAR_REPOSITORIES,

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
		 * The option to emit the time taken to clear the repositories, or the
		 * elapsed build time following a successful or failed build.
		 */
		SHOW_TIMING,

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

		factory.addOption(new GenericOption<OptionKey>(
			AVAIL_RENAMES,
			asList("availRenames"),
			"The absolute path to the renames file. This option overrides " +
			"environment variables.",
			new Continuation2<String, String>()
			{
				@Override
				public void value (
					final @Nullable String keyword,
					final @Nullable String renamesString)
				{
					assert renamesString != null;
					processor.value().checkEncountered(AVAIL_RENAMES, 0);
					try
					{
						configuration.setRenamesFilePath(renamesString);
					}
					catch (final OptionProcessingException e)
					{
						throw new OptionProcessingException(
							keyword + ": " + e.getMessage(),
							e);
					}
				}
			}));

		factory.addOption(new GenericOption<OptionKey>(
			AVAIL_ROOTS,
			asList("availRoots"),
			"The Avail roots, as a semicolon (;) separated list of module root "
			+ "specifications. Each module root specification comprises a "
			+ "logical root name, then an equals (=), then a module root "
			+ "location. A module root location comprises the absolute path to "
			+ "a binary module repository, then optionally a comma (,) and the "
			+ "absolute path to a source package. This option overrides " +
			"environment variables.",
			new Continuation2<String, String>()
			{
				@Override
				public void value (
					final @Nullable String keyword,
					final @Nullable String rootsString)
				{
					assert rootsString != null;
					processor.value().checkEncountered(AVAIL_ROOTS, 0);
					try
					{
						configuration.setAvailRootsPath(rootsString);
					}
					catch (final OptionProcessingException e)
					{
						throw new OptionProcessingException(
							keyword + ": " + e.getMessage(),
							e);
					}
				}
			}));

		factory.addOption(new GenericOption<OptionKey>(
			CLEAR_REPOSITORIES,
			asList("f", "clearRepositories"),
			"The option to force removal of all repositories for which the "
			+ "Avail root path has valid source directories specified. This "
			+ "option can be used in isolation and will cause the repositories "
			+ "to be emptied. In an invocation with a valid target module "
			+ "name, the repositories will be cleared before compilation "
			+ "is attempted.",
			new Continuation2<String, String>()
			{
				@Override
				public void value (
					final @Nullable String keyword,
					final @Nullable String unused)
				{
					processor.value().checkEncountered(CLEAR_REPOSITORIES, 0);
					if (unused != null)
					{
						throw new OptionProcessingException(
							keyword + ": An argument was specified, but none " +
									"are permitted.");
					}
					configuration.setClearRepositoriesFlag();
				}
			}));

		factory.addOption(new GenericOption<OptionKey>(
			QUIET,
			asList("q", "quiet"),
			"The option to mute all output originating from user code.",
			new Continuation2<String, String>()
			{
				@Override
				public void value (
					final @Nullable String keyword,
					final @Nullable String unused)
				{
					processor.value().checkEncountered(QUIET, 0);
					if (unused != null)
					{
						throw new OptionProcessingException(
							keyword + ": An argument was specified, but none " +
									"are permitted.");
					}
					configuration.setQuietFlag();
				}
			}));

		factory.addOption(new GenericOption<OptionKey>(
			SHOW_STATISTICS,
			asList("s", "showStatistics"),
			"The option to request statistics about the most time-intensive " +
			"operations in all categories ( -s or --showStatistics ) or for " +
			"specific categories, using a comma-separated list of keywords ( " +
			"--showStatistics=#,# ). This option overrides environment " +
			"variables." +
			"\n" +
			"\nPossible values in # include:" +
			"\nL2Operations - The most time-intensive level-two operations." +
			"\nDynamicLookups - The most time-intensive dynamic method " +
			"lookups." +
			"\nPrimitives - The primitives that are the most time-intensive " +
			"to run overall." +
			"\nPrimitiveReturnTypeChecks - The primitives that take the most " +
			"time checking return types.",
			new Continuation2<String, String>()
			{
				@Override
				public void value (
					final @Nullable String keyword,
					final @Nullable String reportsString)
				{
					processor.value().checkEncountered(SHOW_STATISTICS, 0);
					EnumSet<StatisticReport> reports;
					if (reportsString == null)
					{
						reports = EnumSet.allOf(StatisticReport.class);
					}
					else
					{
						final String[] reportsArr = reportsString.split(",");
						reports = EnumSet.noneOf(StatisticReport.class);
						StatisticReport report;
						for (final String reportName : reportsArr)
						{
							report = StatisticReport.reportFor(reportName);

							// This will also catch the illegal use of "="
							// without any items following.
							if (report == null)
							{
								throw new OptionProcessingException(
									keyword + ": Illegal argument.");
							}
							reports.add(report);
						}
					}
					configuration.setReports(reports);
				}
			}));

		factory.addOption(new GenericOption<OptionKey>(
			SHOW_TIMING,
			asList("t", "showTiming"),
			"Emits the time taken to clear the repositories, or the elapsed " +
				"build time following a successful or failed build.",
			new Continuation2<String, String>()
			{
				@Override
				public void value (
					final @Nullable String keyword,
					final @Nullable String unused)
				{
					processor.value().checkEncountered(SHOW_TIMING, 0);
					if (unused != null)
					{
						throw new OptionProcessingException(
							keyword + ": An argument was specified, but none " +
									"are permitted.");
					}
					configuration.setShowTimingFlag();
				}
			}));

		factory.addOption(new GenericOption<OptionKey>(
			VERBOSE_MODE,
			asList("v", "verboseMode"),
			"The option to request minimum verbosity ( -v or --verboseMode ) " +
			"or manually set the verbosity level ( --verboseMode=# ). This " +
			"option overrides environment variables. " +
			"\n" +
			"\nPossible values for # include:" +
			"\n0 - Zero extra verbosity. Only error messages will be " +
			"output. This is the default level for the compiler and is " +
			"used when the verboseMode option is not used." +
			"\n1 - The minimum verbosity level. The global progress and " +
			"any error messages will be output. This is the default level " +
			"for this option when a level is not specified." +
			"\n2 - Global progress is output along with the local module " +
			"compilation progress and any error messages.",
			new Continuation2<String, String>()
			{
				@Override
				public void value (
					final @Nullable String keyword,
					final @Nullable String verboseString)
				{
					processor.value().checkEncountered(VERBOSE_MODE, 0);
					if (verboseString == null)
					{
						configuration.setVerbosityLevel(
							VerbosityLevel.atLevel(1));
					}
					else
					{
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
					}
				}
			}));

		factory.addOption(new GenericHelpOption<OptionKey>(
			HELP,
			processor,
			"The Avail compiler understands the following options: ",
			helpStream));

		factory.addOption(new DefaultOption<OptionKey>(
			TARGET_MODULE_NAME,
			"The target module name for compilation. The module is specified " +
			"via a path relative to an AVAIL_ROOTS root name. For example, " +
			"if AVAIL_ROOTS specifies a root named \"foo\" at path " +
			"/usr/local/avail/stuff/, and module \"frog\" is in the root " +
			"folder as /usr/local/avail/stuff/frog, the target module name " +
			"would be /foo/frog.",
			new Continuation2<String, String>()
			{
				@Override
				public void value (
					final @Nullable String keyword,
					final @Nullable String targetModuleString)
				{
					assert targetModuleString != null;
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
			final OptionProcessor<OptionKey> optionProcessor;
			try
			{
				optionProcessor = createOptionProcessor();
				optionProcessor.processOptions(commandLineArguments);
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
	 * Construct a new {@link CommandLineConfigurator}.
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
		this.commandLineArguments = commandLineArguments;
		this.helpStream = helpStream;
	}
}
