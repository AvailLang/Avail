/*
 * AvailRejectedParseException.java
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

package com.avail.compiler;

import com.avail.descriptor.A_String;
import com.avail.descriptor.StringDescriptor;
import com.avail.exceptions.PrimitiveThrownException;
import com.avail.interpreter.primitive.phrases.P_RejectParsing;
import com.avail.utility.Generator;

import javax.annotation.Nullable;

import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;

/**
 * An {@code AvailRejectedParseException} is thrown by primitive {@link
 * P_RejectParsing} to indicate the fiber running a semantic restriction
 * (or macro body or prefix function) has rejected the argument types or phrases
 * for the reason specified in the exception's constructor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class AvailRejectedParseException
extends PrimitiveThrownException
{
	/**
	 * The serial version identifier.
	 */
	private static final long serialVersionUID = -5638050952579212324L;

	/**
	 * The {@linkplain StringDescriptor error message} indicating why a
	 * particular parse was rejected.
	 */
	@Nullable A_String rejectionString;

	/**
	 * A {@link Generator} that will produce the rejectionString if needed.
	 */
	final Generator<A_String> rejectionGenerator;

	/**
	 * Return the {@linkplain StringDescriptor error message} indicating why
	 * a particular parse was rejected.
	 *
	 * @return The reason the parse was rejected.
	 */
	public A_String rejectionString ()
	{
		if (rejectionString == null)
		{
			final Generator<A_String> generator = stripNull(rejectionGenerator);
			rejectionString = generator.value();
		}
		return stripNull(rejectionString);
	}

	/**
	 * Construct a new instance.  If this diagnostic is deemed relevant, the
	 * string will be presented after the word "Expected...".
	 *
	 * @param rejectionString
	 *        The Avail {@linkplain A_String string} indicating why a particular
	 *        parse was rejected.
	 */
	public AvailRejectedParseException (
		final A_String rejectionString)
	{
		this.rejectionGenerator = () -> rejectionString;
		this.rejectionString = rejectionString;
	}

	/**
	 * Construct a new instance with a Java {@link String} as the pattern for
	 * the explanation, and arguments to be substituted into the pattern.  If
	 * this diagnostic is deemed relevant, the string will be presented after
	 * the word "Expected...".
	 *
	 * @param rejectionPattern
	 *        The String to use as a pattern in {@link String#format(String,
	 *        Object...)}.  The arguments with which to instantiate the pattern
	 *        are also supplied.
	 * @param rejectionArguments
	 *        The arguments that should be substituted into the pattern.
	 */
	public AvailRejectedParseException (
		final String rejectionPattern,
		final Object... rejectionArguments)
	{
		this.rejectionGenerator = () ->
			stringFrom(format(rejectionPattern, rejectionArguments));
		this.rejectionString = null;
	}

	/**
	 * Construct a new instance the most general way, with a {@link Generator}
	 * to produce an {@link A_String Avail string} as needed.  If this
	 * diagnostic is deemed relevant, the string will be presented after the
	 * word "Expected...".
	 *
	 * @param generator
	 *        The {@link Generator} that produces a diagnostic {@link A_String
	 *        Avail string} upon first request.
	 */
	public AvailRejectedParseException (
		final Generator<A_String> generator)
	{
		this.rejectionGenerator = generator;
		this.rejectionString = null;
	}
}
