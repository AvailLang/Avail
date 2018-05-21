/*
 * Combinator.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

/**
 * Utility for applying a Continuation1 recursively.
 */
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public final class Combinator
{
	/**
	 * Invoke the given {@link Continuation1NotNull} with a {@link
	 * Continuation0} which will, when evaluated, do this again.
	 *
	 * <p>This is handy for making it possible to reinvoke the original
	 * continuation from within itself (either in the same {@link Thread} or
	 * another).  This bypasses a Java Catch-22 whereby only final variables can
	 * be accessed from a lambda, but the lambda can't be stored in a final
	 * variable if its definition referes to that variable.</p>
	 *
	 * @param body
	 *        The {@link Continuation1NotNull} itself.
	 */
	public static void recurse (
		final Continuation1NotNull<Continuation0> body)
	{
		body.value(() -> recurse(body));
	}

	/**
	 * Invoke the given {@link Continuation2NotNull} with a supplied argument
	 * and a {@link Continuation1NotNull} which, when invoked, will once again
	 * invoke the original {@link Continuation2NotNull} with the new argument,
	 * etc.
	 *
	 * <p>This is handy for making it possible to reinvoke the original
	 * continuation from within itself (either in the same {@link Thread} or
	 * another).  This bypasses a Java Catch-22 whereby only final variables can
	 * be accessed from a lambda, but the lambda can't be stored in a final
	 * variable if its definition referes to that variable.</p>
	 *
	 * @param argument
	 *        The first argument to pass to the body.
	 * @param body
	 *        The {@link Continuation2NotNull} to invoke.
	 */
	public static <A> void recurse (
		final A argument,
		final Continuation2NotNull<A, Continuation1NotNull<A>> body)
	{
		body.value(argument, nextArgument -> recurse(nextArgument, body));
	}
}
