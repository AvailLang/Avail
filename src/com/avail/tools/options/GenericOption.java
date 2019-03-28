/*
 * GenericOption.java
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

package com.avail.tools.options;

import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Continuation2;
import com.avail.utility.evaluation.Continuation2NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * An implementation of {@link Option} whose accessible state is initialized
 * during construction.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @param <OptionKeyType> The type of the option.
 */
public class GenericOption<OptionKeyType extends Enum<OptionKeyType>>
implements Option<OptionKeyType>
{
	/** The option key. */
	private final OptionKeyType optionKey;

	@Override
	public OptionKeyType key ()
	{
		return optionKey;
	}

	/**
	 * The {@linkplain LinkedHashSet set} of keywords that indicate this
	 * {@linkplain GenericOption option}.
	 */
	private final LinkedHashSet<String> keywords = new LinkedHashSet<>();

	@SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
	@Override
	public LinkedHashSet<String> keywords ()
	{
		return keywords;
	}

	/**
	 * An end-user comprehensible description of the {@linkplain GenericOption
	 * option}.
	 */
	private final String description;

	@Override
	public String description ()
	{
		return description;
	}

	/**
	 * The {@linkplain Continuation2 action} that should be performed upon
	 * setting of this {@linkplain GenericOption option}.  The second parameter
	 * <em>may</em> be required to be null or required to be not null, depending
	 * on how this option was created.
	 */
	private final Continuation2<String, String> action;

	@Override
	public Continuation2<String, String> action ()
	{
		return action;
	}

	/**
	 * Construct a new {@code GenericOption} that takes a {@link String}
	 * argument.
	 *
	 * @param optionKey
	 *        The option key.
	 * @param keywords
	 *        The keywords that indicate this {@code GenericOption}.
	 * @param description
	 *        A description of the {@code GenericOption}.
	 * @param action
	 *        The {@linkplain Continuation2NotNull action} that should be
	 *        performed upon setting of this {@code GenericOption}.
	 */
	public GenericOption (
		final OptionKeyType optionKey,
		final Collection<String> keywords,
		final String description,
		final Continuation2NotNull<String, String> action)
	{
		this.optionKey   = optionKey;
		this.action      = action::value;
		this.description = description;
		this.keywords.addAll(keywords);
	}

	/**
	 * Construct a new {#code GenericOption} that takes no argument.
	 *
	 * @param optionKey
	 *        The option key.
	 * @param keywords
	 *        The keywords that indicate this {@code GenericOption}.
	 * @param description
	 *        A description of the {@code GenericOption}.
	 * @param action
	 *        The {@linkplain Continuation1NotNull action} that should be
	 *        performed upon setting of this {@code GenericOption}.
	 */
	public GenericOption (
		final OptionKeyType optionKey,
		final Collection<String> keywords,
		final String description,
		final Continuation1NotNull<String> action)
	{
		this.optionKey   = optionKey;
		this.action      = (keyword, unused) ->
		{
			assert keyword != null;
			if (unused != null)
			{
				throw new OptionProcessingException(
					keyword + ": An argument was specified, but none " +
						"are permitted.");
			}
			action.value(keyword);
		};
		this.description = description;
		this.keywords.addAll(keywords);
	}

	/**
	 * Construct a new {#code GenericOption} that takes no argument.
	 *
	 * @param optionKey
	 *        The option key.
	 * @param keywords
	 *        The keywords that indicate this {@code GenericOption}.
	 * @param description
	 *        A description of the {@code GenericOption}.
	 * @param action1
	 *        The {@linkplain Continuation1NotNull action} that should be
	 *        performed upon setting of this {@code GenericOption} with no
	 *        argument.
	 * @param action2
	 *        The {@linkplain Continuation2NotNull action} that should be
	 *        performed upon setting of this {@code GenericOption} with an
	 *        argument.
	 */
	public GenericOption (
		final OptionKeyType optionKey,
		final Collection<String> keywords,
		final String description,
		final Continuation1NotNull<String> action1,
		final Continuation2NotNull<String, String> action2)
	{
		this.optionKey   = optionKey;
		this.action      = (keyword, optionalArgument) ->
		{
			assert keyword != null;
			if (optionalArgument == null)
			{
				action1.value(keyword);
			}
			else
			{
				action2.value(keyword, optionalArgument);
			}
		};
		this.description = description;
		this.keywords.addAll(keywords);
	}
}
