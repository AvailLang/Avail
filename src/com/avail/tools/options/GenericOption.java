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

import com.avail.utility.evaluation.Continuation2;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * An implementation of {@link Option} whose accessible state is initializable
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
	 * setting of this {@linkplain GenericOption option}.
	 */
	private final Continuation2<String, String> action;

	@Override
	public Continuation2<String, String> action ()
	{
		return action;
	}

	/**
	 * Construct a new <code>{@link GenericOption}</code>.
	 *
	 * @param optionKey
	 *        The option key.
	 * @param keywords
	 *        The keywords that indicate this {@linkplain GenericOption option}.
	 * @param description
	 *        A description of the {@linkplain GenericOption option}.
	 * @param action
	 *        The {@linkplain Continuation2 action} that should be performed
	 *        upon setting of this {@linkplain GenericOption option}.
	 */
	public GenericOption (
		final OptionKeyType optionKey,
		final Collection<String> keywords,
		final String description,
		final Continuation2<String, String> action)
	{
		this.optionKey   = optionKey;
		this.action      = action;
		this.description = description;
		this.keywords.addAll(keywords);
	}
}
