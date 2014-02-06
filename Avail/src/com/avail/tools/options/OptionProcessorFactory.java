/**
 * OptionProcessorFactory.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.tools.options;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * An {@code OptionProcessorFactory} enables a client to dynamically specify and
 * assemble an {@linkplain OptionProcessor option processor}. In particular, the
 * factory allows a client to flexibly define a particular option processor
 * while ignoring specification and evaluation order dependency. Validation is
 * postponed until final assembly time, at which time a <code>{@linkplain
 * ValidationException}</code> will be thrown in the event of incorrect or
 * incomplete specification; otherwise, the constructed option processor
 * provably reflects the client specification.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <OptionKeyType> The type of the option.
 */
public class OptionProcessorFactory<OptionKeyType extends Enum<OptionKeyType>>
{
	/** The {@linkplain Class type} of option keys. */
	private final Class<OptionKeyType> optionKeyType;

	/** The {@linkplain Set set} of all {@linkplain Option options}. */
	private final Set<Option<OptionKeyType>> allOptions =
		new HashSet<Option<OptionKeyType>>();

	/**
	 * Construct a new <code>{@link OptionProcessorFactory}</code>.
	 *
	 * @param optionKeyType The {@linkplain Class type} of option keys.
	 */
	public OptionProcessorFactory (
		final Class<OptionKeyType> optionKeyType)
	{
		this.optionKeyType = optionKeyType;
	}

	/**
	 * Add a new {@linkplain Option option}.
	 *
	 * @param option An {@linkplain Option option}.
	 */
	public void addOption (final Option<OptionKeyType> option)
	{
		assert !allOptions.contains(option);
		allOptions.add(option);
	}

	/**
	 * Check that the resulting {@linkplain OptionProcessor option processor}
	 * will have no defects. In particular:
	 *
	 * <ul>
	 *    <li>Every option key uniquely specifies an option</li>
	 *    <li>Every keyword must uniquely indicate an option key</li>
	 *    <li>All {@linkplain Option options} must have non-empty {@link
	 *        Option#description() descriptions}</li>
	 *    <li>All option keys must be bound to options</li>
	 * </ul>
	 *
	 * @throws ValidationException
	 *         If the specified {@linkplain OptionProcessor option processor}
	 *         fails validation for any reason.
	 */
	private void validate () throws ValidationException
	{
		final EnumSet<OptionKeyType> optionKeys = EnumSet.noneOf(optionKeyType);
		for (final Option<OptionKeyType> option : allOptions)
		{
			if (optionKeys.contains(option.key()))
			{
				throw new ValidationException(
					"some option keys of "
					+ optionKeyType.getCanonicalName()
					+ " do not uniquely specify options");
			}
			optionKeys.add(option.key());
		}

		final HashSet<String> keywords = new HashSet<String>();
		for (final Option<OptionKeyType> option : allOptions)
		{
			for (final String keyword : option.keywords())
			{
				if (keywords.contains(keyword))
				{
					throw new ValidationException(
						"some option keywords do not uniquely indicate option"
						+ " keys of " + optionKeyType.getCanonicalName());
				}
				keywords.add(keyword);
			}
		}

		for (final Option<OptionKeyType> option : allOptions)
		{
			if (option.description().isEmpty())
			{
				throw new ValidationException(
					"some options have empty end-user descriptions");
			}
		}

		if (allOptions.size() != optionKeyType.getEnumConstants().length)
		{
			throw new ValidationException(
				"some option keys of "
				+ optionKeyType.getCanonicalName()
				+ " are not bound to options");
		}
	}

	/**
	 * Create an instance of the {@linkplain OptionProcessor option processor}
	 * described by the {@linkplain OptionProcessorFactory receiver}.
	 *
	 * @return The new validated {@linkplain OptionProcessor option processor}.
	 * @throws ValidationException If validation fails.
	 */
	public OptionProcessor<OptionKeyType> createOptionProcessor ()
		throws ValidationException
	{
		validate();

		final HashMap<String, OptionKeyType> keywords =
			new HashMap<String, OptionKeyType>();
		for (final Option<OptionKeyType> option : allOptions)
		{
			for (final String keyword : option.keywords())
			{
				keywords.put(keyword, option.key());
			}
		}

		return new OptionProcessor<OptionKeyType>(
			optionKeyType, keywords, allOptions);
	}
}
