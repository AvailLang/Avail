/**
 * OptionProcessor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import com.avail.utility.MutableInt;
import com.avail.utility.ParagraphFormatter;
import com.avail.utility.ParagraphFormatterStream;
import com.avail.utility.evaluation.Continuation1;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * An {@code OptionProcessor} serves primarily to support command-line
 * argument processing, but may occasionally prove useful in other
 * circumstances.
 *
 * <p>An option processor is parametric on the type of its option keys. The
 * client provides this information for maximum type-safety and code-reuse.</p>
 *
 * <p>The sole public protocol of an option processor is {@link
 * #processOptions(String[]) processOptions}. It accepts an array of {@linkplain
 * String strings} which it treats as containing separate option strings. It
 * extracts a keyword and an optional argument. The keyword is mapped to a
 * client-specified {@linkplain Continuation1 action}; the argument will be
 * passed to this action upon positive identification of the option by keyword.
 * {@code null} will be passed in lieu of an argument if the argument was
 * omitted.</p>
 *
 * <p>Option strings may have the following forms:</p>
 *
 * <ul>
 * <li>Begins with "-" (hyphen): Each subsequent character is treated as a
 * distinct short option keyword. Short option keywords associate {@code null}
 * arguments with their actions.</li>
 * <li>Begins with "--" (double hyphen): The subsequent characters up to an
 * "=" (equals) are treated collectively as a long option keyword. If no equals
 * is discovered, then the action associated with the option is performed with a
 * {@code null} argument; otherwise any characters following the equals are
 * treated collectively as the argument of that action.</li>
 * <li>Entirety is "--" (double hyphen): Disable special processing of hyphen
 * prefixes. All further option strings are treated as unprefixed.</li>
 * <li>Does not begin with "-" (hyphen): It is treated as the argument of the
 * action associated with the default option. An option processor supports a
 * single default option. It is distinguished in that one of its keywords is
 * the empty string.</li>
 * </ul>
 *
 * <p>A new option processor is obtainable via an appropriately parameterized
 * {@linkplain OptionProcessorFactory factory}. This allows incremental and
 * arbitrary-order specification of the option processor independent of any
 * runtime assembly constraints.</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @param <OptionKeyType> The type of the option.
 */
public class OptionProcessor<OptionKeyType extends Enum<OptionKeyType>>
{
	/**
	 * A mapping between recognizable keywords and the option keys that they
	 * indicate.
	 */
	private final HashMap<String, OptionKeyType> allKeywords =
		new HashMap<>();

	/** A mapping between option keys and {@linkplain Option options}. */
	private final
	EnumMap<OptionKeyType, Option<OptionKeyType>> allOptions;

	/** A mapping from option key to times encountered during processing. */
	private final
	EnumMap<OptionKeyType, MutableInt> timesEncountered;

	/**
	 * Construct a new <code>{@link OptionProcessor}</code>.
	 *
	 * @param optionKeyType
	 *        The {@linkplain Class type} of option keys.
	 * @param keywords
	 *        The complete mapping of recognizable keywords to option keys.
	 * @param options
	 *        The complete {@linkplain Collection collection} of {@linkplain
	 *        Option options}.
	 */
	OptionProcessor (
		final Class<OptionKeyType> optionKeyType,
		final Map<String, OptionKeyType> keywords,
		final Collection<Option<OptionKeyType>> options)
	{
		allKeywords.putAll(keywords);
		allOptions = new EnumMap<>(optionKeyType);
		timesEncountered = new EnumMap<>(optionKeyType);
		for (final Option<OptionKeyType> option : options)
		{
			allOptions.put(option.key(), option);
			timesEncountered.put(option.key(), new MutableInt(0));
		}
	}

	/**
	 * Perform the {@linkplain Continuation1 action} associated with the option
	 * key bound to the specified keyword.
	 *
	 * @param keyword A potential keyword of the {@link OptionProcessor
	 *                receiver}.
	 * @param argument The argument associated with the keyword, if any.
	 * @throws UnrecognizedKeywordException
	 *         If the specified keyword was unrecognized.
	 * @throws OptionProcessingException
	 *         If any exception occurs during option processing.
	 */
	private void performKeywordAction (
			final String keyword,
			final @Nullable String argument)
		throws OptionProcessingException
	{
		final OptionKeyType optionKey = allKeywords.get(keyword);
		if (optionKey == null)
		{
			throw new UnrecognizedKeywordException(keyword);
		}

		allOptions.get(optionKey).action().value(keyword, argument);
		timesEncountered.get(optionKey).value++;
	}

	/** Continue processing keywords? */
	private static final ThreadLocal<Boolean>
		continueProcessingKeywords = ThreadLocal.withInitial(() -> true);

	/**
	 * Process the argument as a long keyword, potentially one with an
	 * associated argument.
	 *
	 * @param keyword
	 *        A long keyword -- if this is empty, then it denotes the end of
	 *        keyword processing.
	 * @throws OptionProcessingException
	 *         If any exception occurs during option processing.
	 */
	private void processLongKeyword (final String keyword)
		throws OptionProcessingException
	{
		// If we have encountered a bare double hyphen, then discontinue
		// keyword processing. All remaining option strings will be passed to
		// the designated default action (and potentially throw exceptions if
		// no such action has been defined).
		if (keyword.isEmpty())
		{
			continueProcessingKeywords.set(false);
		}
		else
		{
			final int index = keyword.indexOf('=');

			// If we didn't find an equals, then treat the keyword as having no
			// argument.
			if (index == -1)
			{
				performKeywordAction(keyword, null);
			}

			// If we found an equals, then treat the right-hand side as an
			// argument associated with the keyword.
			else
			{
				performKeywordAction(
					keyword.substring(0, index),
					keyword.substring(index + 1));
			}
		}
	}

	/**
	 * Process the argument as a collection of single-character keywords.
	 *
	 * @param keywords The keywords.
	 * @throws OptionProcessingException
	 *         If any exception occurs during option processing.
	 */
	private void processShortKeywords (final String keywords)
		throws OptionProcessingException
	{
		for (int i = 0; i < keywords.length(); i++)
		{
			performKeywordAction(keywords.substring(i, i + 1), null);
		}
	}

	/**
	 * Perform the default {@linkplain Continuation1 action} (associated with
	 * the empty keyword) with the specified argument.
	 *
	 * @param argument
	 *        The argument for the default {@linkplain Continuation1 action}.
	 * @throws OptionProcessingException
	 *         If any exception occurs during option processing.
	 */
	private void performDefaultAction (final String argument)
		throws OptionProcessingException
	{
		performKeywordAction("", argument);
	}

	/**
	 * Process the specified string, including any option keyword prefix.
	 *
	 * @param string An option string.
	 * @throws OptionProcessingException
	 *         If any exception occurs during option processing.
	 */
	private void processString (final String string)
		throws OptionProcessingException
	{
		// Reject an empty string.
		if (string.isEmpty())
		{
			throw new UnrecognizedKeywordException("");
		}
		// A hyphen introduces an option keyword -- or terminates keyword
		// processing.
		else if (string.charAt(0) == '-')
		{
			// If there are no further characters, then the string is
			// malformed.
			if (string.length() == 1)
			{
				throw new OptionProcessingException(
					"option syntax error -- bare hyphen encountered");
			}
			// Two hyphens introduces a long option keyword.
			else if (string.charAt(1) == '-')
			{
				processLongKeyword(string.substring(2));
			}
			// A single hyphen (followed by additional characters) introduces
			// a set of short option keywords.
			else
			{
				processShortKeywords(string.substring(1));
			}
		}
		// Treat the string as implicitly associated with the default action.
		else
		{
			performDefaultAction(string);
		}
	}

	/**
	 * Treat the specified array as containing option strings. Process each
	 * option string separately, synchronously executing the {@link
	 * Continuation1 action} associated with it upon its discovery.
	 *
	 * <p>This operation is safe for concurrent access.</p>
	 *
	 * @param strings The option strings.
	 * @throws OptionProcessingException
	 *         If any exception occurs during option processing.
	 */
	public void processOptions (final String[] strings)
		throws OptionProcessingException
	{
		// Rather than using monitors to support concurrent access, we use a
		// single ThreadLocal to track keyword processing.
		try
		{
			for (final String string : strings)
			{
				if (continueProcessingKeywords.get())
				{
					processString(string);
				}
				else
				{
					performDefaultAction(string);
				}
			}
		}
		finally
		{
			// Any particular thread is unlikely to process options again, so
			// make the ThreadLocal available for garbage collection.
			continueProcessingKeywords.remove();
		}
	}

	/**
	 * Answer all {@linkplain Option options} defined by the {@linkplain
	 * OptionProcessor option processor}.
	 *
	 * @return The {@linkplain Collection collection} of defined {@linkplain
	 *         Option options}.
	 */
	public Set<Option<OptionKeyType>> allOptions ()
	{
		return Collections.unmodifiableSet(
			new LinkedHashSet<>(allOptions.values()));
	}

	/**
	 * Answer the number of times that the {@linkplain Option option} indicated
	 * by the specified key has been processed already.
	 *
	 * @param key An option key.
	 * @return The number of times that the option has been processed.
	 */
	public int timesEncountered (final OptionKeyType key)
	{
		return timesEncountered.get(key).value;
	}

	/**
	 * If the specified key was encountered more times than allowed, then throw
	 * an {@link OptionProcessingException}. A key is considered encountered
	 * only once it has been completely processed (and any user supplied
	 * {@linkplain Continuation1 action} invoked).
	 *
	 * @param key
	 *        An option key.
	 * @param timesAllowed
	 *        The maximum number of times that the option key may be specified.
	 * @throws OptionProcessingException
	 *         If {@code key} was processed more than {@code timesAllowed}
	 *         times.
	 */
	public void checkEncountered (
			final OptionKeyType key,
			final int timesAllowed)
		throws OptionProcessingException
	{
		final int timesActuallyEncountered = timesEncountered(key);
		if (timesEncountered(key) > timesAllowed)
		{
			throw new OptionProcessingException(String.format(
				"%s: encountered %d time(s) (allowed at most %d time(s))",
				key,
				timesActuallyEncountered,
				timesAllowed));
		}
	}

	/**
	 * Write the {@linkplain Option#description() descriptions} of the
	 * {@linkplain Option options} defined by the {@linkplain OptionProcessor
	 * option processor} into the specified {@link Appendable}.
	 *
	 * @param appendable An {@link Appendable}.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	public void writeOptionDescriptions (final Appendable appendable)
		throws IOException
	{
		final Set<Option<OptionKeyType>> options = allOptions();

		// Write the descriptions of the options onto the specified Appendable
		// in the order in which they are specified in the source file.
		for (final Option<OptionKeyType> option : options)
		{
			// Keywords should be left-justified within the 80 char window.
			final ParagraphFormatter keywordFormatter =
				new ParagraphFormatter(80);
			final ParagraphFormatterStream keywordStream =
				new ParagraphFormatterStream(keywordFormatter, appendable);

			// Descriptions should be indented by 4 spaces.
			final ParagraphFormatter descriptionFormatter =
				new ParagraphFormatter(80, 4, 4);
			final ParagraphFormatterStream descriptionStream =
				new ParagraphFormatterStream(descriptionFormatter, appendable);

			final LinkedHashSet<String> keywords =
				new LinkedHashSet<>(option.keywords());
			for (final String keyword : keywords)
			{
				if (option instanceof DefaultOption)
				{
					keywordStream.append(String.format(
						"<bareword>%n"));
				}
				else
				{
					keywordStream.append(String.format(
						"%s%s%n",
						(keyword.length() == 1 ? "-" : "--"),
						keyword));
				}
			}

			descriptionStream.append(
				String.format("%s%n%n", option.description()));
		}
	}
}
