/**
 * CommandLineConfigurator.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.tools.unicode;

import static com.avail.tools.unicode.CommandLineConfigurator.OptionKey.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.avail.annotations.InnerAccess;
import com.avail.annotations.Nullable;
import com.avail.tools.options.DefaultOption;
import com.avail.tools.options.GenericHelpOption;
import com.avail.tools.options.OptionProcessor;
import com.avail.tools.options.OptionProcessorFactory;
import com.avail.utility.MutableOrNull;
import com.avail.utility.configuration.ConfigurationException;
import com.avail.utility.configuration.Configurator;
import com.avail.utility.evaluation.Continuation2;

/**
 * {@code CommandLineConfigurator} provides the command-line configuration for
 * the {@linkplain CatalogGenerator Unicode catalog generator}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class CommandLineConfigurator
implements Configurator<Configuration>
{
	/**
	 * {@code OptionKey} enumerates the valid configuration options.
	 */
	static enum OptionKey
	{
		/**
		 * Request display of help text.
		 */
		HELP,

		/**
		 * Specification of the target {@linkplain Path path}.
		 */
		TARGET_PATH
	}

	/** The {@linkplain Configuration configuration}. */
	@InnerAccess final Configuration configuration;

	/** The command line arguments. */
	private final String[] commandLineArguments;

	/**
	 * The {@linkplain Appendable appendable} to which help text should be
	 * written.
	 */
	private final Appendable helpStream;

	/**
	 * Construct a new {@link CommandLineConfigurator}.
	 *
	 * @param configuration
	 *        The base {@linkplain Configuration configuration}.
	 * @param commandLineArguments
	 *        The command-line arguments.
	 * @param helpStream
	 *        The {@link Appendable} to which help text should be written.
	 */
	public CommandLineConfigurator (
		final Configuration configuration,
		final String[] commandLineArguments,
		final Appendable helpStream)
	{
		this.configuration = configuration;
		this.commandLineArguments = commandLineArguments;
		this.helpStream = helpStream;
	}

	/**
	 * Create an {@linkplain OptionProcessor option processor} suitable for
	 * {@linkplain #updateConfiguration() updating} a {@linkplain
	 * Configuration configuration}.
	 *
	 * @return An option processor.
	 */
	private OptionProcessor<OptionKey> createOptionProcessor ()
	{
		final MutableOrNull<OptionProcessor<OptionKey>> processor =
			new MutableOrNull<>();
		final OptionProcessorFactory<OptionKey> factory =
			new OptionProcessorFactory<>(OptionKey.class);
		factory.addOption(new GenericHelpOption<OptionKey>(
			HELP,
			processor,
			"The Unicode catalog generator understands the following "
			+ "options: ",
			helpStream));
		factory.addOption(new DefaultOption<OptionKey>(
			TARGET_PATH,
			"The location of the target JSON file. If a regular file already "
			+ "exists at this location, then it will be overwritten.",
			new Continuation2<String, String>()
			{
				@Override
				public void value (
					final @Nullable String unused,
					final @Nullable String pathString)
				{
					assert pathString != null;
					processor.value().checkEncountered(TARGET_PATH, 0);
					configuration.catalogPath = Paths.get(pathString);
				}
			}));
		processor.value = factory.createOptionProcessor();
		return processor.value();
	}

	/**
	 * Has the {@linkplain CommandLineConfigurator configurator} been run yet?
	 */
	private boolean isConfigured;

	@Override
	public void updateConfiguration () throws ConfigurationException
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

	@Override
	public Configuration configuration ()
	{
		return configuration;
	}
}
