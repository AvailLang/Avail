/*
 * ParserState.java
 * Copyright © 1993-2018, The Avail Foundation, LLC. All rights reserved.
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
import com.avail.compiler.problems.CompilerDiagnostics;
import com.avail.compiler.scanning.LexingState;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_String;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.MapDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Describer;
import com.avail.utility.evaluation.SimpleDescriber;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.evaluation.Transformer1NotNull;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.AvailRuntime.currentRuntime;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;

/**
 * {@code ParserState} instances are immutable and keep track of a current
 * {@link #lexingState} and {@link #clientDataMap} during parsing.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class ParserState
{
	/**
	 * The state of the lexer at this parse position.
	 */
	final LexingState lexingState;

	/**
	 * A {@linkplain MapDescriptor map} of interesting information used by the
	 * compiler.
	 */
	public final A_Map clientDataMap;

	/**
	 * Construct a new immutable {@code ParserState}.
	 *
	 * @param lexingState
	 *        The {@link LexingState} at this parse position.
	 * @param clientDataMap
	 *        The {@link MapDescriptor map} of data used by macros while
	 *        parsing Avail code.
	 */
	ParserState (
		final LexingState lexingState,
		final A_Map clientDataMap)
	{
		this.lexingState = lexingState;
		// Note that this map *must* be marked as shared, since parsing
		// proceeds in parallel.
		this.clientDataMap = clientDataMap.makeShared();
	}

	@Override
	public int hashCode ()
	{
		return lexingState.hashCode() * AvailObject.multiplier
			^ clientDataMap.hash();
	}

	@Override
	public boolean equals (final @Nullable Object another)
	{
		if (!(another instanceof ParserState))
		{
			return false;
		}
		// Don't bother comparing allTokens, since there should never be a case
		// where the lexingState and clientDataMap agree, but different lists of
		// tokens were accumulated to get there.
		final ParserState anotherState = (ParserState) another;
		return lexingState == anotherState.lexingState
			&& clientDataMap.equals(anotherState.clientDataMap);
	}

	@Override
	public String toString ()
	{
		final A_String source = lexingState.compilationContext.source();
		return format(
			"%s%n\tPOSITION = %d%n\tTOKENS = %s %s %s%n\tCLIENT_DATA = %s",
			getClass().getSimpleName(),
			lexingState.position,
			((A_String)source.copyTupleFromToCanDestroy(
				max(lexingState.position - 20, 1),
				max(lexingState.position - 1, 0),
				false)).asNativeString(),
			CompilerDiagnostics.errorIndicatorSymbol,
			((A_String)source.copyTupleFromToCanDestroy(
				min(lexingState.position, source.tupleSize() + 1),
				min(lexingState.position + 20, source.tupleSize()),
				false)).asNativeString(),
			clientDataMap);
	}

	public String shortString ()
	{
		final A_String source = lexingState.compilationContext.source();
		if (lexingState.position == 1)
		{
			return "(start)";
		}
		if (lexingState.position == source.tupleSize() + 1)
		{
			return "(end)";
		}
		final A_String nearbyText = (A_String)source.copyTupleFromToCanDestroy(
			lexingState.position,
			min(lexingState.position + 20, source.tupleSize()),
			false);
		return lexingState.lineNumber + ":" + nearbyText.asNativeString() + '…';
	}

	/**
	 * Create a {@code ParserState} with a different {@link #clientDataMap}.
	 *
	 * @param replacementMap
	 *        The {@link A_Map} to replace this parser state's {@link
	 *        #clientDataMap} in the new parser state.
	 * @return The new {@code ParserState}.
	 */
	public ParserState withMap (final A_Map replacementMap)
	{
		return new ParserState(lexingState, replacementMap);
	}

	/**
	 * Answer this state's position in the source.
	 *
	 * @return The one-based index into the source.
	 */
	public int position ()
	{
		return lexingState.position;
	}

	/**
	 * Answer the one-based line number of this state.
	 *
	 * @return The one-based line number.
	 */
	public int lineNumber ()
	{
		return lexingState.lineNumber;
	}

	/**
	 * Determine if this state represents the end of the file.
	 *
	 * @return Whether this state represents the end of the file.
	 */
	public boolean atEnd ()
	{
		return lexingState.position ==
			lexingState.compilationContext.source().tupleSize() + 1;
	}

	/**
	 * Record an expectation at the current parse position. The expectations
	 * captured at the rightmost few reached parse positions constitute the
	 * error message in case the parse fails.
	 *
	 * <p>
	 * The expectation is a {@linkplain Describer}, in case constructing a
	 * {@link String} frivolously would be prohibitive. There is also {@link
	 * #expected(String) another} version of this method that accepts a
	 * String directly.
	 * </p>
	 *
	 * @param describer
	 *        The {@code describer} to capture.
	 */
	void expected (final Describer describer)
	{
		lexingState.compilationContext.diagnostics.expectedAt(
			describer, lexingState);
	}

	/**
	 * Record an expectation at the current parse position. The expectations
	 * captured at the rightmost parse position constitute the error message
	 * in case the parse fails.
	 *
	 * @param values
	 *        A list of arbitrary {@linkplain AvailObject Avail values} that
	 *        should be stringified.
	 * @param transformer
	 *        A {@linkplain Transformer1 transformer} that accepts the
	 *        stringified values and answers an expectation message.
	 */
	void expected (
		final List<? extends A_BasicObject> values,
		final Transformer1NotNull<List<String>, String> transformer)
	{
		expected(continuation -> Interpreter.stringifyThen(
			currentRuntime(),
			lexingState.compilationContext.getTextInterface(),
			values,
			list -> continuation.value(transformer.value(list))));
	}

	/**
	 * Record an indication of what was expected at this parse position.
	 *
	 * @param aString
	 *        The string describing something that was expected at this
	 *        position under some interpretation so far.
	 */
	void expected (final String aString)
	{
		expected(new SimpleDescriber(aString));
	}

	/**
	 * Queue an action to be performed later, passing an argument.
	 *
	 * @param <ArgType>
	 *        The type of argument to the given continuation.
	 * @param continuation
	 *        What to execute with the passed argument.
	 * @param argument
	 *        What to pass as an argument to the provided {@linkplain
	 *        Continuation1NotNull one-argument continuation}.
	 */
	public <ArgType> void workUnitDo (
		final Continuation1NotNull<ArgType> continuation,
		final ArgType argument)
	{
		lexingState.workUnitDo(continuation, argument);
	}
}
