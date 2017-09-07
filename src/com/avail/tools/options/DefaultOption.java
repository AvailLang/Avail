/**
 * DefaultOption.java
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

import com.avail.utility.evaluation.Continuation2;

import static java.util.Arrays.asList;

/**
 * A {@code DefaultOption} is the {@linkplain Option option} that an {@linkplain
 * OptionProcessor option processor} recognizes bare arguments as being
 * implicitly associated with.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @param <OptionKeyType> The type of the option.
 */
public final class DefaultOption<OptionKeyType extends Enum<OptionKeyType>>
extends GenericOption<OptionKeyType>
{
	/**
	 * Construct a new instance.
	 *
	 * @param optionKey
	 *        The option key.
	 * @param description
	 *        A description of the {@linkplain DefaultOption option}.
	 * @param action
	 *        The {@linkplain Continuation2 action} that should be performed
	 *        upon setting of this {@linkplain DefaultOption option}.
	 */
	public DefaultOption (
		final OptionKeyType optionKey,
		final String description,
		final Continuation2<String, String> action)
	{
		super(optionKey, asList(""), description, action);
	}
}
