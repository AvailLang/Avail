/*
 * FormattingDescriber.java
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

package com.avail.utility.evaluation;

import java.util.Formatter;

import static java.lang.String.format;

/**
 * A {@code FormattingDescriber} is a {@link Describer} that is given a {@link
 * String} to act as a {@link Formatter} pattern, and an array of {@link
 * Object}s to supply to it.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class FormattingDescriber implements Describer
{
	/** The {@link String} to use as a {@link Formatter} pattern. */
	final String patternString;

	/** The arguments to supply to the pattern. */
	final Object[] arguments;

	/**
	 * Construct a new {@link FormattingDescriber}.
	 *
	 * @param patternString The pattern {@link String}.
	 * @param arguments The arguments to populate the pattern.
	 */
	public FormattingDescriber (
		final String patternString,
		final Object... arguments)
	{
		this.patternString = patternString;
		this.arguments = arguments;
	}

	/**
	 * Produce the formatted string and pass it to the specified {@linkplain
	 * Continuation1NotNull continuation}.
	 *
	 * @param continuation
	 *        What to do with the formatted {@link String}.
	 */
	@Override
	public void describeThen (final Continuation1NotNull<String> continuation)
	{
		continuation.value(format(patternString, arguments));
	}
}
