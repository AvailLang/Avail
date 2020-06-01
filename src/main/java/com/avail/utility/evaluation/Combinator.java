/*
 * Combinator.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

/**
 * Utilities for applying functions recursively and enabling function
 * self-reference.
 */
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public final class Combinator
{
	/**
	 * Invoke the given {@link Function1} with a {@link Function0} which will,
	 * when evaluated, do this again.
	 *
	 * @param body
	 *        The {@link Function1} itself.
	 */
	public static void recurse (final Function1<Function0<Unit>, Unit> body)
	{
		body.invoke(() ->
			{
				recurse(body);
				return Unit.INSTANCE;
			});
	}

	/**
	 * Invoke the given {@link Function2} with a supplied argument and a {@link
	 * Function1} which, when invoked, will once again invoke the original
	 * {@code Function2} with the new argument, etc.
	 *
	 * <p>This is handy for making it possible to re-invoke the original
	 * continuation from within itself (either in the same {@link Thread} or
	 * another).  This bypasses a Java Catch-22 whereby only final variables can
	 * be accessed from a lambda, but the lambda can't be stored in a final
	 * variable if its definition refers to that variable.</p>
	 *
	 * @param argument
	 *        The first argument to pass to the body.
	 * @param body
	 *        The {@link Function1} to invoke.
	 * @param <A>
	 *        The type of the argument.
	 */
	public static <A> void recurse (
		final A argument,
		final Function2<A, Function1<A, Unit>, Unit> body)
	{
		body.invoke(
			argument,
			nextArgument ->
			{
				recurse(nextArgument, body);
				return Unit.INSTANCE;
			});
	}
}
