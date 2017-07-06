/**
 * LexingState.java
 * Copyright © 1993-2015, The Avail Foundation, LLC. All rights reserved.
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

package com.avail.compiler.scanning;
import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.compiler.CompilationContext;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.GeneralFlag;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.AvailLoader.LexicalScanner;
import com.avail.interpreter.Interpreter;
import com.avail.utility.Generator;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Describer;
import com.avail.utility.evaluation.SimpleDescriber;
import com.avail.utility.evaluation.Transformer1;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link LexingState} instances represent the lexing state between tokens.
 * They bind together consecutive tokens into a directed acyclic graph, allowing
 * the {@link AvailCompiler} to process all possible paths (lexings), typically
 * in aggregate.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class LexingState
{
	/**
	 * The compilation context for which this is a state of lexing.
	 */
	public final CompilationContext compilationContext;

	/**
	 * The position represented by this {@link LexingState}.  In particular,
	 * it's the (one-based) start of the current token within the source.
	 */
	public final int position;

	/**
	 * The one-based line number at which this state occurs in the source.
	 */
	public final int lineNumber;

	/**
	 * The immutable {@link List} of {@link A_Token tokens} that may each be
	 * next, starting at this lexing position.
	 *
	 * <p>This is generally {@code null} until it has been computed by lexing
	 * fibers.</p>
	 */
	List<A_Token> nextTokens = null;

	/**
	 * The collection of actions that should run when the {@link #nextTokens}
	 * list has been computed.
	 *
	 * <p>This is replaced by {@code null} when the list has been computed,
	 * running the waiting actions.  Newer actions are launched upon arrival
	 * after the tokens have been computed.</p>
	 */
	@InnerAccess List<Continuation1<List<A_Token>>> actions = new ArrayList<>();

	/**
	 * Construct a new immutable {@link LexingState}.
	 *
	 * @param position
	 *        The one-based position in the source.
	 * @param lineNumber
	 *        The one-based line number of this position in the source.
\	 */
	public LexingState (
		final CompilationContext compilationContext,
		final int position,
		final int lineNumber)
	{
		this.compilationContext = compilationContext;
		this.position = position;
		this.lineNumber = lineNumber;
	}

	/**
	 * Eventually invoke the given {@link Continuation1} with the {@link List}
	 * of {@link A_Token tokens} at this position.  If necessary, launch {@link
	 * A_Fiber fibers} to run {@link A_Lexer lexers}, invoking the continuation
	 * only when all possible next tokens have been computed.
	 *
	 * @param newAction
	 *        What to do with the list of tokens.
	 */
	public synchronized void withTokensDo (
		final Continuation1<List<A_Token>> newAction)
	{
		if (actions == null)
		{
			// The list of tokens was already computed.
			assert nextTokens != null;
			compilationContext.eventuallyDo(
				this,
				new Continuation0()
				{
					@Override
					public void value ()
					{
						newAction.value(nextTokens);
					}
				});
			return;
		}
		actions.add(newAction);
		if (actions.size() > 1)
		{
			// This wasn't the first action.  When the first action arrived, it
			// started computing the tokens, so having added the new action,
			// we're done.
			return;
		}
		// This was the first action, so launch the lexers to produce the
		// list of nextTokens and then run the queued actions.
		nextTokens = new ArrayList<>(2);
		final LexicalScanner scanner =
			compilationContext.loader().lexicalScanner();
		final int codePoint =
			compilationContext.source().tupleCodePointAt(position);
		scanner.getLexersForCodePointThen(
			codePoint,
			new Continuation1<A_Tuple>()
			{
				@Override
				public void value (
					@Nullable final A_Tuple applicableLexers)
				{
					assert applicableLexers != null;
					synchronized (LexingState.this)
					{
						if (applicableLexers.tupleSize() == 0)
						{
							// No applicable lexers.
							expected(
								"an applicable lexer, but all filter functions "
								+ "returned false");
							for (final Continuation1<List<A_Token>> action
								: actions)
							{
								compilationContext.eventuallyDo(
									LexingState.this,
									new Continuation0()
									{
										@Override
										public void value ()
										{
											action.value(nextTokens);
										}
									});
							}
							return;
						}
						// There's at least one applicable lexer.  Launch
						// fibers which, when the last one completes, will
						// capture the list of tokens and run the actions.
						final AtomicInteger countdown =
							new AtomicInteger(applicableLexers.tupleSize());
						final List<A_BasicObject> arguments =
							Arrays.<A_BasicObject>asList(
								compilationContext.source(),
								IntegerDescriptor.fromInt(position));
						for (final A_Lexer lexer : applicableLexers)
						{
							evaluateLexerAndRunActionsWhenZero(
								lexer, arguments, countdown);
						}
					}
				}
			},
			new Continuation1<Map<A_Lexer, Throwable>>()
			{
				@Override
				public void value (final Map<A_Lexer, Throwable> filterFailures)
				{
					for (final Entry<A_Lexer, Throwable> entry
						: filterFailures.entrySet())
					{
						expected(
							new Describer()
							{
								@Override
								public void describeThen (
									final Continuation1<String> afterDescribing)
								{
									final StringWriter stringWriter =
										new StringWriter();
									entry.getValue().printStackTrace(
										new PrintWriter(stringWriter));
									final String text = String.format(
										"%s not to have failed while "
										+ "evaluating its filter function:\n%s",
										entry.getKey().toString(),
										stringWriter.toString());
								}
							});
					}
				}
			});
	}

	@InnerAccess void evaluateLexerAndRunActionsWhenZero (
		final A_Lexer lexer,
		final List<A_BasicObject> arguments,
		final AtomicInteger countdown)
	{
		final AvailLoader loader = compilationContext.loader();
		final A_Fiber fiber = FiberDescriptor.newLoaderFiber(
			LexerDescriptor.lexerBodyFunctionType().resultType(),
			loader,
			new Generator<A_String>()
			{
				@Override
				public A_String value ()
				{
					return StringDescriptor.format(
						"Lexer body on line %d for %s.",
						lineNumber,
						lexer);
				}
			});
		// Set up fiber variables for lexing.
//		A_Map fiberGlobals = fiber.fiberGlobals();
//		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
//			CLIENT_DATA_GLOBAL_KEY.atom, clientParseData, true);
//		fiber.fiberGlobals(fiberGlobals);
		fiber.textInterface(compilationContext.getTextInterface());
		fiber.resultContinuation(
			new Continuation1<AvailObject>()
			{
				@Override
				public void value (@Nullable final AvailObject newTokens)
				{
					assert newTokens.isTuple();
					lexerBodyWasSuccessful(newTokens, countdown);
				}
			});
		fiber.failureContinuation(
			new Continuation1<Throwable>()
			{
				@Override
				public void value (@Nullable final Throwable throwable)
				{
					if (throwable instanceof AvailRejectedParseException)
					{
						final AvailRejectedParseException rej =
							(AvailRejectedParseException) throwable;
						expected(rej.rejectionString().asNativeString());
						return;
					}
					// Report the problem as an expectation, with a stack trace.
					expected(
						new Describer()
						{
							@Override
							public void describeThen (
								final Continuation1<String> afterDescribing)
							{
								final StringWriter stringWriter =
									new StringWriter();
								throwable.printStackTrace(
									new PrintWriter(stringWriter));
								final String text = String.format(
									"%s not to have failed while "
										+ "evaluating its body:\n%s",
									lexer.toString(),
									stringWriter.toString());
								afterDescribing.value(text);
							}
						});
				}
			}
		);
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE);
		Interpreter.runOutermostFunction(
			loader.runtime(),
			fiber,
			lexer.lexerBodyFunction(),
			arguments);
	}

	/**
	 * A lexer body completed successfully with the given tuple of next tokens
	 * (all tokens that the lexer indicates might be the very next token).
	 *
	 * @param newTokens
	 *        All tokens that might be the very next one, according to the lexer
	 *        that just completed.
	 * @param countdown
	 *        The {@link AtomicInteger} which counts down to zero with each
	 *        successful invocation (not each token), then runs all outstanding
	 *        actions once.
	 */
	final void lexerBodyWasSuccessful (
		final A_Tuple newTokens,
		final AtomicInteger countdown)
	{
		if (newTokens.tupleSize() == 0)
		{
			expected(
				"Lexer body to have produced at least one sequence  of tokens, "
				+ "since its filter replied true. This is not required of "
				+ "lexers, but it might indicate an inefficiency.");
		}
		synchronized (this)
		{
			for (A_Token token : newTokens)
			{
				nextTokens.add(token);
			}
		}
		if (countdown.decrementAndGet() != 0)
		{
			return;
		}
		// We just heard from the last lexer.  Run the actions once.
		synchronized (this)
		{
			for (final Continuation1<List<A_Token>> action : actions)
			{
				compilationContext.eventuallyDo(
					LexingState.this,
					new Continuation0()
					{
						@Override
						public void value ()
						{
							action.value(nextTokens);
						}
					});
			}
			actions = null;
		}
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
	public void expected (final Describer describer)
	{
		compilationContext.diagnostics.expectedAt(describer, this);
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
	public void expected (
		final List<? extends A_BasicObject> values,
		final Transformer1<List<String>, String> transformer)
	{
		expected(new Describer()
		{
			@Override
			public void describeThen (
				final Continuation1<String> continuation)
			{
				Interpreter.stringifyThen(
					AvailRuntime.current(),
					compilationContext.getTextInterface(),
					values,
					new Continuation1<List<String>>()
					{
						@Override
						public void value (
							final @Nullable List<String> list)
						{
							continuation.value(transformer.value(list));
						}
					});
			}
		});
	}

	/**
	 * Record an indication of what was expected at this parse position.
	 *
	 * @param aString
	 *        The string describing something that was expected at this
	 *        position under some interpretation so far.
	 */
	public void expected (final String aString)
	{
		expected(new SimpleDescriber(aString));
	}
}
