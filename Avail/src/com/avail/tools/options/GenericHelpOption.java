/**
 * GenericHelpOption.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

import java.io.IOException;
import java.util.Arrays;
import com.avail.annotations.*;
import com.avail.utility.Continuation1;
import com.avail.utility.Mutable;

/**
 * A {@code GenericHelpOption} provides an application help message that
 * displays a customizable preamble followed by the complete set of {@linkplain
 * Option options}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <OptionKeyType> The type of the option.
 */
public final class GenericHelpOption<OptionKeyType extends Enum<OptionKeyType>>
extends GenericOption<OptionKeyType>
{
	/**
	 * Write the specified preamble followed by the {@linkplain
	 * Option#description() descriptions} of the {@linkplain Option options}
	 * defined by the specified {@linkplain OptionProcessor option processor}
	 * into the specified {@link Appendable}.
	 *
	 * @param <KeyType> The type of the option.
	 * @param optionProcessor The {@linkplain OptionProcessor option processor}
	 *                        whose {@linkplain Option options} should be
	 *                        described by the new {@link GenericHelpOption}.
	 * @param preamble The preamble, i.e. any text that should precede an
	 *                 enumeration of the {@linkplain Option options}.
	 * @param appendable The {@link Appendable} into which the help text should
	 *                   be written.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	@InnerAccess static <KeyType extends Enum<KeyType>> void writeHelpText (
			final OptionProcessor<KeyType> optionProcessor,
			final String preamble,
			final Appendable appendable)
		throws IOException
	{
		appendable.append(String.format("%s%n%n", preamble));
		optionProcessor.writeOptionDescriptions(appendable);
	}

	/**
	 * Construct a new {@link GenericHelpOption}.
	 *
	 * @param optionKey
	 *        The option key.
	 * @param optionProcessor
	 *        A {@linkplain Mutable volatile container} holding the {@linkplain
	 *        OptionProcessor option processor} whose {@linkplain Option
	 *        options} should be described by the new {@link GenericHelpOption}.
	 *        This effectively provides a late-bound strong reference to an
	 *        option processor that has not yet been instantiated.
	 * @param preamble
	 *        The preamble, i.e. any text that should precede an enumeration of
	 *        the {@linkplain Option options}.
	 * @param appendable
	 *        The {@link Appendable} into which the help text should be written.
	 */
	public GenericHelpOption (
		final OptionKeyType optionKey,
		final Mutable<OptionProcessor<OptionKeyType>> optionProcessor,
		final String preamble,
		final Appendable appendable)
	{
		super(
			optionKey,
			Arrays.asList(new String[] { "?" }),
			"Display help text containing a description of the application "
			+ "and an enumeration of its options.",
			new Continuation1<String>()
			{
				@Override
				public void value (final @Nullable String arg)
				{
					try
					{
						// It would be nice if we could prove to Java that this
						// action won't actually be invoked inside the
						// constructor.
						writeHelpText(
							optionProcessor.value, preamble, appendable);
						System.exit(0);
					}
					catch (final IOException e)
					{
						throw new OptionProcessingException(e);
					}
				}
			});
	}
}
