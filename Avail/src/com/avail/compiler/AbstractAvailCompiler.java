/**
 * AbstractAvailCompiler.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith. All rights reserved.
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

import static com.avail.compiler.AbstractAvailCompiler.ExpectedToken.*;
import static com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.builder.*;
import com.avail.compiler.scanning.*;
import com.avail.descriptor.*;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * The abstract compiler for Avail code.  Subclasses may wish to implement, oh,
 * say, a system version with a hard-coded basic syntax and a non-system version
 * with no hard-coded syntax but macro capability.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public abstract class AbstractAvailCompiler
{
	/**
	 * The Avail {@linkplain ModuleDescriptor module} undergoing compilation.
	 */
	AvailObject module;

	/**
	 * The {@link ModuleName} of the module undergoing compilation.
	 */
	private final ModuleName moduleName;

	/**
	 * The {@linkplain L2Interpreter interpreter} to use when evaluating
	 * top-level expressions.
	 */
	@InnerAccess final @NotNull L2Interpreter interpreter;

	/**
	 * The source text of the Avail {@linkplain ModuleDescriptor module}
	 * undergoing compilation.
	 */
	private String source;

	/**
	 * The complete {@linkplain List list} of {@linkplain TokenDescriptor
	 * tokens} parsed from the source text.
	 */
	@InnerAccess List<AvailObject> tokens;

	/**
	 * The position of the rightmost {@linkplain TokenDescriptor token} reached
	 * by any parsing attempt.
	 */
	@InnerAccess int greatestGuess;

	/**
	 * The {@linkplain List list} of {@linkplain String} {@linkplain Generator
	 * generators} that describe what was expected (but not found) at the
	 * {@linkplain #greatestGuess rightmost reached position}.
	 */
	@InnerAccess final @NotNull List<Generator<String>> greatExpectations =
		new ArrayList<Generator<String>>();

	/** The memoization of results of previous parsing attempts. */
	@InnerAccess AvailCompilerFragmentCache fragmentCache;

	/**
	 * The versions for which the module undergoing compilation guarantees
	 * support.
	 */
	@InnerAccess List<AvailObject> versions;

	/**
	 * The {@linkplain ModuleDescriptor modules} extended by the module
	 * undergoing compilation. Each element is a {@linkplain TupleDescriptor
	 * 3-tuple} whose first element is a module {@linkplain StringDescriptor
	 * name}, whose second element is the {@linkplain SetDescriptor set} of
	 * {@linkplain MethodImplementationDescriptor method} names to import (and
	 * re-export), and whose third element is the set of conformant versions.
	 */
	public List<AvailObject> extendedModules;

	/**
	 * The {@linkplain ModuleDescriptor modules} used by the module undergoing
	 * compilation. Each element is a {@linkplain TupleDescriptor 3-tuple} whose
	 * first element is a module {@linkplain StringDescriptor name}, whose
	 * second element is the {@linkplain SetDescriptor set} of {@linkplain
	 * MethodImplementationDescriptor method} names to import, and whose third
	 * element is the set of conformant versions.
	 */
	public List<AvailObject> usedModules;

	/**
	 * The {@linkplain AtomDescriptor names} defined and exported by the
	 * {@linkplain ModuleDescriptor module} undergoing compilation.
	 */
	@InnerAccess List<AvailObject> exportedNames;

	/**
	 * The {@linkplain String pragma strings}.
	 */
	@InnerAccess List<AvailObject> pragmas;

	/**
	 * The {@linkplain Continuation3 action} that should be performed repeatedly
	 * by the {@linkplain AbstractAvailCompiler compiler} to report compilation
	 * progress.
	 */
	Continuation4<ModuleName, Long, Long, Long> progressBlock;

	/**
	 * Answer whether this is a {@linkplain AvailSystemCompiler system
	 * compiler}.  A system compiler is used for modules that start with the
	 * keyword "{@linkplain ExpectedToken#SYSTEM System}".  Such modules use a
	 * predefined syntax.
	 *
	 * @return Whether this is a system compiler.
	 */
	boolean isSystemCompiler ()
	{
		return false;
	}

	/**
	 * These are the tokens that are understood by the Avail compilers. Most of
	 * these tokens exist to support the {@linkplain AvailSystemCompiler
	 * system compiler}, though a few (related to module headers) are needed
	 * also by the {@linkplain AvailCompiler standard compiler}.
	 */
	public enum ExpectedToken
	{
		/** Module header token. Must be the first token of a system module. */
		SYSTEM("System", KEYWORD),

		/** Module header token: Precedes the name of the defined module. */
		MODULE("Module", KEYWORD),

		/**
		 * Module header token: Precedes the list of versions for which the
		 * defined module guarantees compatibility.
		 */
		VERSIONS("Versions", KEYWORD),

		/** Module header token: Precedes the list of pragma strings. */
		PRAGMA("Pragma", KEYWORD),

		/**
		 * Module header token: Precedes the list of imported modules whose
		 * (filtered) names should be re-exported to clients of the defined
		 * module.
		 */
		EXTENDS("Extends", KEYWORD),

		/**
		 * Module header token: Precedes the list of imported modules whose
		 * (filtered) names are imported only for the private use of the
		 * defined module.
		 */
		USES("Uses", KEYWORD),

		/**
		 * Module header token: Precedes the list of names exported for use by
		 * clients of the defined module.
		 */
		NAMES("Names", KEYWORD),

		/** Module header token: Precedes the contents of the defined module. */
		BODY("Body", KEYWORD),

		/** Leads a primitive binding. */
		PRIMITIVE("Primitive", KEYWORD),

		/** Leads a label. */
		DOLLAR_SIGN("$", OPERATOR),

		/** Leads a reference. */
		UP_ARROW("↑", OPERATOR),

		/** Module header token: Separates tokens. */
		COMMA(",", OPERATOR),

		/** Uses related to declaration and assignment. */
		COLON(":", OPERATOR),

		/** Uses related to declaration and assignment. */
		EQUALS("=", OPERATOR),

		/** Leads a lexical block. */
		OPEN_SQUARE("[", OPERATOR),

		/** Ends a lexical block. */
		CLOSE_SQUARE("]", OPERATOR),

		/** Leads a function body. */
		VERTICAL_BAR("|", OPERATOR),

		/** Leads an exception set. */
		CARET("^", OPERATOR),

		/** Module header token: Uses related to grouping. */
		OPEN_PARENTHESIS("(", OPERATOR),

		/** Module header token: Uses related to grouping. */
		CLOSE_PARENTHESIS(")", OPERATOR),

		/** End of statement. */
		SEMICOLON(";", END_OF_STATEMENT);

		/** The {@linkplain StringDescriptor lexeme}. */
		private final @NotNull AvailObject lexeme;

		/** The {@linkplain TokenType token type}. */
		private final @NotNull TokenType tokenType;

		/**
		 * Answer the {@linkplain StringDescriptor lexeme}.
		 *
		 * @return The lexeme.
		 */
		public @NotNull AvailObject lexeme ()
		{
			return lexeme;
		}

		/**
		 * Answer the {@linkplain TokenType token type}.
		 *
		 * @return The token type.
		 */
		@NotNull TokenType tokenType ()
		{
			return tokenType;
		}

		/**
		 * Construct a new {@link ExpectedToken}.
		 *
		 * @param lexeme
		 *        The {@linkplain StringDescriptor lexeme}, i.e. the text
		 *        of the token.
		 * @param tokenType
		 *        The {@linkplain TokenType token type}.
		 */
		ExpectedToken (
			final @NotNull String lexeme,
			final @NotNull TokenType tokenType)
		{
			this.lexeme = StringDescriptor.from(lexeme);
			this.tokenType = tokenType;
		}
	}

	/**
	 * Construct a suitable {@linkplain AbstractAvailCompiler compiler} to
	 * parse the specified {@linkplain ModuleName module name}, using the given
	 * {@linkplain L2Interpreter interpreter}.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ModuleName qualified name} of the {@linkplain
	 *        ModuleDescriptor module} being defined.
	 * @param interpreter
	 *        The {@link Interpreter} used to execute code during compilation.
	 * @param stopAfterBodyToken
	 *        Whether to stop parsing at the occurrence of the BODY token. This
	 *        is an optimization for faster build analysis.
	 * @return The new {@linkplain AbstractAvailCompiler compiler}.
	 */
	public static @NotNull AbstractAvailCompiler create (
		final @NotNull ModuleName qualifiedName,
		final @NotNull L2Interpreter interpreter,
		final boolean stopAfterBodyToken)
	{
		final AvailRuntime runtime = interpreter.runtime();
		final ResolvedModuleName resolvedName =
			runtime.moduleNameResolver().resolve(qualifiedName);
		final String source = extractSource(qualifiedName, resolvedName);
		final List<AvailObject> tokens = tokenize(
			source,
			stopAfterBodyToken);
		AbstractAvailCompiler compiler;
		if (!tokens.isEmpty()
			&& tokens.get(0).string().equals(SYSTEM.lexeme()))
		{
			compiler = new AvailSystemCompiler(
				interpreter,
				qualifiedName,
				source,
				tokens);
		}
		else
		{
			compiler = new AvailCompiler(
				interpreter,
				qualifiedName,
				source,
				tokens);
		}
		return compiler;
	}

	/**
	 * Construct a new {@link AbstractAvailCompiler} which will use the given
	 * {@link L2Interpreter} to evaluate expressions.
	 *
	 * @param interpreter
	 *        The interpreter to be used for evaluating expressions.
	 * @param moduleName
	 *        The {@link ModuleName} of the module being compiled.
	 * @param source
	 *        The source code {@linkplain StringDescriptor string}.
	 * @param tokens
	 *        The list of {@linkplain TokenDescriptor tokens}.
	 */
	public AbstractAvailCompiler (
		final @NotNull L2Interpreter interpreter,
		final @NotNull ModuleName moduleName,
		final @NotNull String source,
		final @NotNull List<AvailObject> tokens)
	{
		this.interpreter = interpreter;
		this.moduleName = moduleName;
		this.source = source;
		this.tokens = tokens;
	}

	/**
	 * This is actually a two-argument continuation, but it has only a single
	 * type parameter because the first one is always the {@linkplain
	 * ParserState parser state} that indicates where the continuation should
	 * continue parsing.
	 *
	 * @param <AnswerType>
	 *        The type of the second parameter of the {@linkplain
	 *        Con#value(ParserState, Object)} method.
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	abstract class Con<AnswerType>
	implements Continuation2<ParserState, AnswerType>
	{
		/**
		 * A debugging description of this continuation.
		 */
		final @NotNull String description;

		/**
		 * Construct a new {@link AvailCompiler.Con} with the provided
		 * description.
		 *
		 * @param description
		 *            The provided description.
		 */
		Con (final @NotNull String description)
		{
			this.description = description;
		}

		@Override
		public @NotNull String toString ()
		{
			return "Con(" + description + ")";
		}

		@Override
		public abstract void value (
			@NotNull ParserState state,
			AnswerType answer);
	}


	/**
	 * A {@link Runnable} which supports a natural ordering (via the {@link
	 * Comparable} interface) which will favor processing of the leftmost
	 * available tasks first.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	abstract class ParsingTask implements Runnable, Comparable<ParsingTask>
	{
		/**
		 * The description associated with this task.  Only used for debugging.
		 */
		final @NotNull String description;

		/**
		 * The parsing position (token index) at which this task will operate.
		 */
		final int position;

		/**
		 * Construct a new {@link AbstractAvailCompiler.ParsingTask}.
		 *
		 * @param description What this task will do.
		 * @param position Where in the parse stream this task operates.
		 */
		public ParsingTask (
			@NotNull final String description,
			final int position)
		{
			this.description = description;
			this.position = position;
		}

		@Override
		public String toString()
		{
			return description + "@pos(" + position + ")";
		}

		@Override
		public int compareTo (final ParsingTask o)
		{
			return position - o.position;
		}
	}

	/**
	 * The maximum number of entries that may be in the work pool.
	 */
	private final static int maxWorkPoolSize = 10000;

	/**
	 * A collection of {@link Runnable} parsing tasks that need to be explored
	 * at some point.  The contents of this collection should only be accessed
	 * through synchronized methods.
	 */
	private final static @NotNull BlockingQueue<Runnable> workPool =
		new PriorityBlockingQueue<Runnable>(
			maxWorkPoolSize);

	/**
	 * A static flag indicating whether parallel parsing should be allowed.
	 * As of 2011.05.01 there is not yet correct support for multiple Avail
	 * threads manipulating AvailObjects safely, but it will be forthcoming.
	 */
	private final static boolean enableParallelParsing = false;

	/**
	 * A {@link ThreadPoolExecutor} with which to execute parsing tasks.
	 */
	private final static @NotNull ThreadPoolExecutor workPoolExecutor =
		enableParallelParsing
			?
				new ThreadPoolExecutor(
					1,
					Runtime.getRuntime().availableProcessors(),
					2,
					TimeUnit.MINUTES,
					workPool)
			:
				new ThreadPoolExecutor(
					1,
					1,
					2,
					TimeUnit.MINUTES,
					workPool);

	/**
	 * The number of work units that have been queued.  This should only be
	 * accessed through synchronized methods.
	 */
	long workUnitsQueued = 0;

	/**
	 * The number of work units that have been started.  This should only be
	 * accessed through synchronized methods.
	 */
	long workUnitsStarted = 0;

	/**
	 * The number of work units that have been completed.  This should only be
	 * accessed through synchronized methods.
	 */
	long workUnitsCompleted = 0;

	/**
	 * Execute the block, passing a continuation that it should run upon finding
	 * a local solution. If exactly one solution is found, unwrap the stack (but
	 * not the token stream position or scopeMap), and pass the result to the
	 * continuation. Otherwise report that an unambiguous statement was
	 * expected.
	 *
	 * @param start
	 *        Where to start parsing
	 * @param tryBlock
	 *        The block to attempt.
	 * @param supplyAnswer
	 *        What to do if exactly one result was produced.
	 */
	void tryIfUnambiguousThen (
		final @NotNull ParserState start,
		final @NotNull Con<Con<AvailObject>> tryBlock,
		final @NotNull Con<AvailObject> supplyAnswer)
	{
		assert workPool.isEmpty();
		final Mutable<Integer> count = new Mutable<Integer>(0);
		final Mutable<AvailObject> solution = new Mutable<AvailObject>();
		final Mutable<AvailObject> another = new Mutable<AvailObject>();
		final Mutable<ParserState> where = new Mutable<ParserState>();
		attempt(
			start,
			tryBlock,
			new Con<AvailObject>("Unambiguous statement")
			{
				@Override
				public void value (
					final ParserState afterSolution,
					final AvailObject aSolution)
				{
					synchronized (AbstractAvailCompiler.this)
					{
						if (count.value == 0)
						{
							solution.value = aSolution;
							where.value = afterSolution;
						}
						else
						{
							assert aSolution != solution.value
								: "Same solution was presented twice!";
							another.value = aSolution;
							AbstractAvailCompiler.this.notifyAll();
						}
						count.value++;
					}
				}
			});
		synchronized (this)
		{
			try
			{
				while (workUnitsCompleted < workUnitsQueued && count.value <= 1)
				{
					wait();
				}
				// Note: count.value increases monotonically.
				if (count.value > 1)
				{
					workPoolExecutor.shutdownNow();
					workPoolExecutor.awaitTermination(2, TimeUnit.MINUTES);
				}
				else
				{
					assert workPool.isEmpty();
				}
				workUnitsStarted = workUnitsQueued;
				workUnitsCompleted = workUnitsQueued;
			}
			catch (final InterruptedException e)
			{
				// Treat an interrupt of this thread as a failure.
				throw new RuntimeException(e);
			}
		}
		if (count.value == 0)
		{
			return;
		}
		if (count.value > 1)
		{
			// Indicate the problem on the last token of the ambiguous
			// expression.
			reportAmbiguousInterpretations(
				new ParserState(
					where.value.position - 1,
					where.value.scopeMap),
				solution.value,
				another.value);
			return;
		}
		// We found exactly one solution. Advance the token stream just past it,
		// and commit its side-effects to the scope, then invoke the
		// continuation with the solution.
		assert count.value == 1;
		assert workPool.isEmpty();
		supplyAnswer.value(where.value, solution.value);
		assert workPool.isEmpty();
	}

	/**
	 * {@link ParserState} instances are immutable and keep track of a current
	 * {@link #position} and {@link #scopeMap} during parsing.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	class ParserState
	{
		/**
		 * The position represented by this {@link ParserState}. In particular,
		 * it's the (zero-based) index of the current token.
		 */
		final int position;

		/**
		 * A {@linkplain MapDescriptor map} from each name that is in scope to
		 * the {@linkplain DeclarationNodeDescriptor declaration} with that
		 * name.  The names are {@linkplain StringDescriptor strings}.
		 *
		 * <p>
		 * The map should be immutable, as each new declaration must create a
		 * fresh map.  Backtracking (or closing a block scope) causes some
		 * previous version of the map to be used, so its state must never be
		 * destroyed.  Note that this is especially important since the parser
		 * is both continuation-passing and potentially breadth-first.
		 * </p>
		 */
		final @NotNull AvailObject scopeMap;

		/**
		 * Construct a new immutable {@link ParserState}.
		 *
		 * @param position
		 *            The index of the current token.
		 * @param scopeMap
		 *            The {@link MapDescriptor map} of bindings.
		 */
		ParserState (
			final int position,
			final @NotNull AvailObject scopeMap)
		{
			assert scopeMap != null;

			this.position = position;
			this.scopeMap = scopeMap;
		}

		@Override
		public int hashCode ()
		{
			return position * 473897843 ^ scopeMap.hashCode();
		}

		@Override
		public boolean equals (final Object another)
		{
			if (!(another instanceof ParserState))
			{
				return false;
			}
			final ParserState anotherState = (ParserState) another;
			return position == anotherState.position
					&& scopeMap.equals(anotherState.scopeMap);
		}

		@Override
		public @NotNull String toString ()
		{
			return String.format(
				"%s%n" + "\tPOSITION=%d%n" + "\tSCOPE_STACK = %s",
				getClass().getSimpleName(),
				position,
				scopeMap);
		}

		/**
		 * Determine if this state represents the end of the file. If so, one
		 * should not invoke {@link #peekToken()} or {@link #afterToken()}
		 * again.
		 *
		 * @return Whether this state represents the end of the file.
		 */
		boolean atEnd ()
		{
			return this.position == tokens.size() - 1;
		}

		/**
		 * Answer the {@linkplain TokenDescriptor token} at the current
		 * position.
		 *
		 * @return The token.
		 */
		@NotNull AvailObject peekToken ()
		{
			return tokens.get(position);
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param expectedToken
		 *        The {@linkplain ExpectedToken expected token} to look for.
		 * @return Whether the specified token was found.
		 */
		boolean peekToken (final @NotNull ExpectedToken expectedToken)
		{
			if (atEnd())
			{
				return false;
			}
			final AvailObject token = peekToken();
			return token.tokenType() == expectedToken.tokenType()
				&& token.string().equals(expectedToken.lexeme());
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param expectedToken
		 *        The {@linkplain ExpectedToken expected token} to look for.
		 * @param expected
		 *        A {@linkplain Generator generator} of a message to record if
		 *        the specified token is not present.
		 * @return Whether the specified token is present.
		 */
		boolean peekToken (
			final @NotNull ExpectedToken expectedToken,
			final @NotNull Generator<String> expected)
		{
			final AvailObject token = peekToken();
			final boolean found = token.tokenType() == expectedToken.tokenType()
					&& token.string().equals(expectedToken.lexeme());
			if (!found)
			{
				expected(expected);
			}
			return found;
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param expectedToken
		 *        The {@linkplain ExpectedToken expected token} to look for.
		 * @param expected
		 *        A message to record if the specified token is not present.
		 * @return Whether the specified token is present.
		 */
		boolean peekToken (
			final @NotNull ExpectedToken expectedToken,
			final @NotNull String expected)
		{
			return peekToken(expectedToken, generate(expected));
		}

		/**
		 * Return a new {@link ParserState} like this one, but advanced by one
		 * token.
		 *
		 * @return A new parser state.
		 */
		@NotNull ParserState afterToken ()
		{
			assert !atEnd();
			return new ParserState(position + 1, scopeMap);
		}

		/**
		 * Parse a string literal. Answer the {@linkplain LiteralTokenDescriptor
		 * string literal token} if found, otherwise answer {@code null}.
		 *
		 * @return The actual {@linkplain LiteralTokenDescriptor literal token}
		 *         or {@code null}.
		 */
		AvailObject peekStringLiteral ()
		{
			final AvailObject token = peekToken();
			if (token.isInstanceOfKind(LITERAL_TOKEN.o()))
			{
				return token;
			}
			return null;
		}

		/**
		 * Return a new {@linkplain ParserState parser state} like this one, but
		 * with the given declaration added.
		 *
		 * @param declaration
		 *        The {@linkplain DeclarationNodeDescriptor declaration} to add
		 *        to the map of visible bindings.
		 * @return The new parser state including the declaration.
		 */
		@NotNull ParserState withDeclaration (
			final @NotNull AvailObject declaration)
		{
			final AvailObject name = declaration.token().string();
			assert !scopeMap.hasKey(name);
			return new ParserState(
				position,
				scopeMap.mapAtPuttingCanDestroy(
					name,
					declaration,
					false));
		}

		/**
		 * Record an expectation at the current parse position. The expectations
		 * captured at the rightmost parse position constitute the error message
		 * in case the parse fails.
		 *
		 * <p>
		 * The expectation is a {@linkplain Generator Generator<String>}, in
		 * case constructing a {@link String} would be prohibitive. There is
		 * also {@link #expected(String) another} version of this method that
		 * accepts a String directly.
		 * </p>
		 *
		 * @param stringGenerator
		 *        The {@code Generator<String>} to capture.
		 */
		void expected (final @NotNull Generator<String> stringGenerator)
		{
			// System.out.println(Integer.toString(position) + " expected " +
			// stringBlock.value());
			if (position == greatestGuess)
			{
				greatExpectations.add(stringGenerator);
			}
			if (position > greatestGuess)
			{
				greatestGuess = position;
				greatExpectations.clear();
				greatExpectations.add(stringGenerator);
			}
		}

		/**
		 * Record an indication of what was expected at this parse position.
		 *
		 * @param aString
		 *        The string to look up.
		 */
		void expected (final @NotNull String aString)
		{
			expected(generate(aString));
		}
	}

	/**
	 * Parse one or more string literals separated by commas. This parse isn't
	 * backtracking like the rest of the grammar - it's greedy. It considers a
	 * comma followed by something other than a string literal to be an
	 * unrecoverable parsing error (not a backtrack).
	 *
	 * <p>
	 * Return the {@link ParserState} after the strings if successful, otherwise
	 * null. Populate the passed {@link List} with the
	 * {@linkplain StringDescriptor actual Avail strings}.
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param stringTokens
	 *        The initially empty list of strings to populate.
	 * @return The parser state after the list of strings, or null if the list
	 *         of strings is malformed.
	 */
	private static @NotNull ParserState parseStringLiterals (
		final @NotNull ParserState start,
		final @NotNull List<AvailObject> stringTokens)
	{
		assert stringTokens.isEmpty();

		AvailObject token = start.peekStringLiteral();
		if (token == null)
		{
			return start;
		}
		stringTokens.add(token.literal());
		ParserState state = start.afterToken();
		while (state.peekToken(COMMA))
		{
			state = state.afterToken();
			token = state.peekStringLiteral();
			if (token == null)
			{
				state.expected("another string literal after comma");
				return null;
			}
			state = state.afterToken();
			stringTokens.add(token.literal());
		}
		return state;
	}

	/**
	 * Parse one or more {@linkplain ModuleDescriptor module} imports separated
	 * by commas. This parse isn't backtracking like the rest of the grammar -
	 * it's greedy. It considers any parse error to be unrecoverable (not a
	 * backtrack).
	 *
	 * <p>
	 * Return the {@link ParserState} after the imports if successful, otherwise
	 * {@code null}. Populate the passed {@linkplain List list} with {@linkplain
	 * TupleDescriptor 2-tuples}. Each tuple's first element is a module
	 * {@linkplain StringDescriptor name} and second element is the
	 * collection of {@linkplain MethodImplementationDescriptor method} names to
	 * import.
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param imports
	 *        The initially empty list of imports to populate.
	 * @return The parser state after the list of imports, or {@code null} if
	 *         the list of imports is malformed.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	private static @NotNull ParserState parseImports (
		final @NotNull ParserState start,
		final @NotNull List<AvailObject> imports)
	{
		assert imports.isEmpty();

		ParserState state = start;
		do
		{
			final AvailObject token = state.peekStringLiteral();
			if (token == null)
			{
				state.expected("another module name after comma");
				return imports.isEmpty() ? state : null;
			}

			final AvailObject moduleName = token.literal();
			state = state.afterToken();

			final List<AvailObject> versions = new ArrayList<AvailObject>();
			if (state.peekToken(OPEN_PARENTHESIS))
			{
				state = state.afterToken();
				state = parseStringLiterals(state, versions);
				if (!state.peekToken(
					CLOSE_PARENTHESIS,
					"a close parenthesis following acceptable versions"))
				{
					return null;
				}
				state = state.afterToken();
			}

			final List<AvailObject> names = new ArrayList<AvailObject>();
			if (state.peekToken(EQUALS))
			{
				state = state.afterToken();
				if (!state.peekToken(
					OPEN_PARENTHESIS,
					"an open parenthesis preceding import list"))
				{
					return null;
				}
				state = state.afterToken();
				state = parseStringLiterals(state, names);
				if (!state.peekToken(
					CLOSE_PARENTHESIS,
					"a close parenthesis following import list"))
				{
					return null;
				}
				state = state.afterToken();
			}

			imports.add(TupleDescriptor.from(
				moduleName,
				TupleDescriptor.fromCollection(names).asSet(),
				TupleDescriptor.fromCollection(versions).asSet()));
		}
		while (state.peekToken(COMMA) && (state = state.afterToken()) != null);

		return state;
	}

	/**
	 * Read the source string for the {@linkplain ModuleDescriptor module}
	 * specified by the fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param qualifiedName
	 *        A fully-qualified {@linkplain ModuleName module name}.
	 * @param resolvedName
	 *        The {@linkplain ResolvedModuleName resolved name} of the module.
	 * @return The module's {@linkplain String source code}.
	 * @throws AvailCompilerException
	 *         If source extraction failed for any reason.
	 */
	private static @NotNull String extractSource (
			final @NotNull ModuleName qualifiedName,
			final @NotNull ResolvedModuleName resolvedName)
		throws AvailCompilerException
	{
		String source;
		if (resolvedName == null)
		{
			throw new AvailCompilerException(
				qualifiedName,
				0,
				0,
				"Unable to resolve fully-qualified module name \""
					+ qualifiedName.qualifiedName()
					+ "\" to an existing file");
		}

		try
		{
			final StringBuilder sourceBuilder = new StringBuilder(4096);
			final char[] buffer = new char[4096];
			final Reader reader = new BufferedReader(
				new FileReader(resolvedName.fileReference()));
			int charsRead;
			while ((charsRead = reader.read(buffer)) > 0)
			{
				sourceBuilder.append(buffer, 0, charsRead);
			}
			source = sourceBuilder.toString();
		}
		catch (final IOException e)
		{
			throw new AvailCompilerException(
				qualifiedName,
				0,
				0,
				"Encountered an I/O exception while reading source module \""
					+ qualifiedName.qualifiedName() + "\" (resolved to \""
					+ resolvedName.fileReference().getAbsolutePath()
					+ "\")");
		}
		return source;
	}

	/**
	 * Tokenize the {@linkplain ModuleDescriptor module} specified by the
	 * fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param source
	 *        The {@linkplain String string} containing the module's source
	 *        code.
	 * @param stopAfterBodyToken
	 *        Stop scanning after encountering the BODY token?
	 * @return The {@linkplain ResolvedModuleName resolved module name}.
	 * @throws AvailCompilerException
	 *         If tokenization failed for any reason.
	 */
	static @NotNull List<AvailObject> tokenize (
			final @NotNull String source,
			final boolean stopAfterBodyToken)
		throws AvailCompilerException
	{
		return new AvailScanner().scanString(source, stopAfterBodyToken);
	}

	/**
	 * Map the tree through the (destructive) transformation specified by
	 * aBlock, children before parents. The block takes three arguments: the
	 * node, its parent, and the list of enclosing block nodes. Answer the
	 * resulting tree.
	 *
	 * @param object
	 *        The current {@linkplain ParseNodeDescriptor parse node}.
	 * @param aBlock
	 *        What to do with each descendant.
	 * @param parentNode
	 *        This node's parent.
	 * @param outerNodes
	 *        The list of {@linkplain BlockNodeDescriptor blocks} surrounding
	 *        this node, from outermost to innermost.
	 * @param nodeMap
	 *        The {@link Map} from old {@linkplain ParseNodeDescriptor parse
	 *        nodes} to newly copied, mutable parse nodes.  This should ensure
	 *        the consistency of declaration references.
	 * @return A replacement for this node, possibly this node itself.
	 */
	static AvailObject treeMapWithParent (
		final @NotNull AvailObject object,
		final @NotNull Transformer3<
				AvailObject,
				AvailObject,
				List<AvailObject>,
				AvailObject>
			aBlock,
		final @NotNull AvailObject parentNode,
		final @NotNull List<AvailObject> outerNodes,
		final @NotNull Map<AvailObject, AvailObject> nodeMap)
	{
		if (nodeMap.containsKey(object))
		{
			return object;
		}
		final AvailObject objectCopy = object.copyMutableParseNode();
		objectCopy.childrenMap(
			new Transformer1<AvailObject, AvailObject>()
			{
				@Override
				public AvailObject value (final AvailObject child)
				{
					assert child.isInstanceOfKind(PARSE_NODE.mostGeneralType());
					return treeMapWithParent(
						child,
						aBlock,
						objectCopy,
						outerNodes,
						nodeMap);
				}
			});
		final AvailObject transformed = aBlock.value(
			objectCopy,
			parentNode,
			outerNodes);
		transformed.makeImmutable();
		nodeMap.put(object, transformed);
		return transformed;
	}

	/**
	 * Visit the entire tree with the given {@linkplain Continuation3 block},
	 * children before parents.  The block takes three arguments: the
	 * node, its parent, and the list of enclosing block nodes.
	 *
	 * @param object
	 *        The current {@linkplain ParseNodeDescriptor parse node}.
	 * @param aBlock
	 *        What to do with each descendant.
	 * @param parentNode
	 *        This node's parent.
	 * @param outerNodes
	 *        The list of {@linkplain BlockNodeDescriptor blocks} surrounding
	 *        this node, from outermost to innermost.
	 */
	static void treeDoWithParent (
		final @NotNull AvailObject object,
		final @NotNull Continuation3<
			AvailObject, AvailObject, List<AvailObject>> aBlock,
		final @NotNull AvailObject parentNode,
		final @NotNull List<AvailObject> outerNodes)
	{
		object.childrenDo(
			new Continuation1<AvailObject>()
			{
				@Override
				public void value (final AvailObject child)
				{
					assert child.isInstanceOfKind(PARSE_NODE.mostGeneralType());
					treeDoWithParent(
						child,
						aBlock,
						object,
						outerNodes);
				}
			});
		aBlock.value(
			object,
			parentNode,
			outerNodes);
	}

	/**
	 * Answer a {@linkplain Generator generator} that will produce the given
	 * string.
	 *
	 * @param string
	 *        The exact string to generate.
	 * @return A generator that produces the string that was provided.
	 */
	@NotNull Generator<String> generate (final @NotNull String string)
	{
		return new Generator<String>()
		{
			@Override
			public String value ()
			{
				return string;
			}
		};
	}

	/**
	 * Report an error by throwing an {@link AvailCompilerException}. The
	 * exception encapsulates the {@linkplain ModuleName module name} of the
	 * {@linkplain ModuleDescriptor module} undergoing compilation, the error
	 * string, and the text position. This position is the rightmost position
	 * encountered during the parse, and the error strings in
	 * {@link #greatExpectations} are the things that were expected but not
	 * found at that position. This seems to work very well in practice.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ModuleName qualified name} of the {@linkplain
	 *        ModuleDescriptor source module}.
	 * @throws AvailCompilerException
	 *        Always thrown.
	 */
	void reportError (final @NotNull ModuleName qualifiedName)
		throws AvailCompilerException
	{
		reportError(
			qualifiedName,
			tokens.get(greatestGuess),
			"Expected...",
			greatExpectations);
	}

	/**
	 * A bunch of dash characters, wide enough to catch the eye.
	 */
	static final String rowOfDashes;

	static
	{
		final char[] chars = new char[70];
		Arrays.fill(chars, '-');
		rowOfDashes = new String(chars);
	}

	/**
	 * Report an error by throwing an {@link AvailCompilerException}. The
	 * exception encapsulates the {@linkplain ModuleName module name} of the
	 * {@linkplain ModuleDescriptor module} undergoing compilation, the error
	 * string, and the text position. This position is the rightmost position
	 * encountered during the parse, and the error strings in
	 * {@link #greatExpectations} are the things that were expected but not
	 * found at that position. This seems to work very well in practice.
	 *
	 * @param token
	 *        Where the error occurred.
	 * @param qualifiedName
	 *        The {@linkplain ModuleName qualified name} of the {@linkplain
	 *        ModuleDescriptor source module}.
	 * @param banner
	 *        The string that introduces the problem text.
	 * @param problems
	 *        A list of {@linkplain Generator generators} that may be
	 *        invoked to produce problem strings.
	 * @throws AvailCompilerException
	 *         Always thrown.
	 */
	void reportError (
			final @NotNull ModuleName qualifiedName,
			final @NotNull AvailObject token,
			final @NotNull String banner,
			final @NotNull List<Generator<String>> problems)
		throws AvailCompilerException
	{
		assert problems.size() > 0 : "Bug - empty problem list";
		final long charPos = token.start();
		final String sourceUpToError = source.substring(0, (int) charPos);
		final int startOfPreviousLine = sourceUpToError.lastIndexOf('\n') + 1;
		final Formatter text = new Formatter();
		text.format("%n");
		int wedges = 3;
		for (int i = startOfPreviousLine; i < charPos; i++)
		{
			if (source.codePointAt(i) == '\t')
			{
				while (wedges > 0)
				{
					text.format(">");
					wedges--;
				}
				text.format("\t");
			}
			else
			{
				if (wedges > 0)
				{
					text.format(">");
					wedges--;
				}
				else
				{
					text.format(" ");
				}
			}
		}
		text.format("^-- %s", banner);
		text.format("%n>>>%s", rowOfDashes);
		final Set<String> alreadySeen = new HashSet<String>(problems.size());
		for (final Generator<String> generator : problems)
		{
			final String str = generator.value();
			if (!alreadySeen.contains(str))
			{
				alreadySeen.add(str);
				text.format("\n>>>\t%s", str.replace("\n", "\n>>>\t"));
			}
		}
		text.format(
			"%n(file=\"%s\", line=%d)",
			moduleName.qualifiedName(),
			token.lineNumber());
		text.format("%n>>>%s", rowOfDashes);
		int endOfLine = source.indexOf('\n', (int) charPos);
		if (endOfLine == -1)
		{
			source = source + "\n";
			endOfLine = source.length() - 1;
		}
		throw new AvailCompilerException(
			qualifiedName,
			charPos,
			endOfLine,
			text.toString());
	}

	/**
	 * A statement was parsed correctly in two different ways. There may be more
	 * ways, but we stop after two as it's already an error. Report the error.
	 *
	 * @param where
	 *        Where the expressions were parsed from.
	 * @param interpretation1
	 *        The first interpretation as a {@linkplain ParseNodeDescriptor
	 *        parse node}.
	 * @param interpretation2
	 *        The second interpretation as a {@linkplain ParseNodeDescriptor
	 *        parse node}.
	 */
	private void reportAmbiguousInterpretations (
		final @NotNull ParserState where,
		final @NotNull AvailObject interpretation1,
		final @NotNull AvailObject interpretation2)
	{
		where.expected(
			new Generator<String>()
			{
				@Override
				public String value ()
				{
					final StringBuilder builder = new StringBuilder(200);
					builder.append("unambiguous interpretation.  ");
					builder.append("Here are two possible parsings...\n");
					builder.append("\t");
					builder.append(interpretation1.toString());
					builder.append("\n\t");
					builder.append(interpretation2.toString());
					return builder.toString();
				}
			});
	}

	/**
	 * Attempt the zero-argument continuation. The implementation is free to
	 * execute it now or to put it in a bag of continuations to run later <em>in
	 * an arbitrary order</em>.  There may be performance and/or scale benefits
	 * to processing entries in FIFO, LIFO, or some hybrid order, but the
	 * correctness is not affected by a choice of order.  Parallel execution is
	 * even expected to be implemented at some point.
	 *
	 * @param continuation
	 *        What to do at some point in the future.
	 * @param description
	 *        Debugging information about what is to be parsed.
	 * @param position
	 *        Debugging information about where the parse is happening.
	 */
	synchronized void eventuallyDo (
		final @NotNull Continuation0 continuation,
		final @NotNull String description,
		final int position)
	{
		final AbstractAvailCompiler thisCompiler = this;
		try
		{
			workUnitsQueued++;
			workPoolExecutor.execute(new ParsingTask(description, position)
			{
				@Override
				public void run ()
				{
					synchronized (thisCompiler)
					{
						workUnitsStarted++;
					}
					continuation.value();
					synchronized (thisCompiler)
					{
						workUnitsCompleted++;
						if (workUnitsCompleted == workUnitsQueued)
						{
							thisCompiler.notifyAll();
						}
					}
				}
			});
		}
		catch (final RejectedExecutionException e)
		{
			throw new RuntimeException("Probably recursive parse error", e);
		}
	}

	/**
	 * Wrap the {@linkplain Continuation1 continuation of one argument} inside a
	 * {@linkplain Continuation0 continuation of zero arguments} and record that
	 * as per {@linkplain #eventuallyDo(Continuation0, String, int)}.
	 *
	 * @param <ArgType>
	 *        The type of argument to the given continuation.
	 * @param here
	 *        Where to start parsing when the continuation runs.
	 * @param continuation
	 *        What to execute with the passed argument.
	 * @param argument
	 *        What to pass as an argument to the provided {@code Continuation1
	 *        one-argument continuation}.
	 */
	<ArgType> void attempt (
		final @NotNull ParserState here,
		final @NotNull Con<ArgType> continuation,
		final @NotNull ArgType argument)
	{
		eventuallyDo(
			new Continuation0()
			{
				@Override
				public void value ()
				{
					continuation.value(here, argument);
				}
			},
			continuation.description,
			here.position);
	}

	/**
	 * Evaluate a {@linkplain ParseNodeDescriptor parse node} in the module's
	 * context; lexically enclosing variables are not considered in scope, but
	 * module variables and constants are in scope.
	 *
	 * @param expressionNode
	 *            A {@linkplain ParseNodeDescriptor parse node}.
	 * @return The result of generating a {@linkplain FunctionDescriptor
	 *         function} from the argument and evaluating it.
	 */
	@NotNull AvailObject evaluate (final @NotNull AvailObject expressionNode)
	{
		final AvailObject block = BlockNodeDescriptor.newBlockNode(
			Collections.<AvailObject>emptyList(),
			(short) 0,
			Collections.singletonList(expressionNode),
			TOP.o(),
			SetDescriptor.empty());
		validate(block);
		final AvailCodeGenerator codeGenerator = new AvailCodeGenerator();
		final AvailObject compiledBlock = block.generate(codeGenerator);
		// The block is guaranteed context-free (because imported
		// variables/values are embedded directly as constants in the generated
		// code), so build a function with no copied data.
		assert compiledBlock.numOuters() == 0;
		final AvailObject function = FunctionDescriptor.create(
			compiledBlock,
			TupleDescriptor.empty());
		function.makeImmutable();
		List<AvailObject> args;
		args = new ArrayList<AvailObject>();
		final AvailObject result = interpreter.runFunctionArguments(
			function,
			args);
		return result;
	}

	/**
	 * Ensure that the {@linkplain BlockNodeDescriptor block node} is valid.
	 * Throw an appropriate exception if it is not.
	 *
	 * @param blockNode
	 *            The {@linkplain BlockNodeDescriptor block node} to validate.
	 */
	public void validate (final @NotNull AvailObject blockNode)
	{
		final List<AvailObject> blockStack = new ArrayList<AvailObject>(3);
		treeDoWithParent(
			blockNode,
			new Continuation3<AvailObject, AvailObject, List<AvailObject>>()
			{
				@Override
				public void value (
					final AvailObject node,
					final AvailObject parent,
					final List<AvailObject> blockNodes)
				{
					node.validateLocally(parent, blockNodes, interpreter);
				}
			},
			null,
			blockStack);
		assert blockStack.isEmpty();
		assert blockNode.neededVariables().tupleSize() == 0;
	}

	/**
	 * Clear the fragment cache.
	 */
	private void privateClearFrags ()
	{
		fragmentCache.clear();
	}

	/**
	 * Look up a local declaration that has the given name, or null if not
	 * found.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param name
	 *        The name of the variable declaration for which to look.
	 * @return The declaration or null.
	 */
	@NotNull AvailObject lookupDeclaration (
		final @NotNull ParserState start,
		final @NotNull AvailObject name)
	{
		if (start.scopeMap.hasKey(name))
		{
			return start.scopeMap.mapAt(name);
		}
		return null;
	}

	/**
	 * Start definition of a {@linkplain ModuleDescriptor module}. The entire
	 * definition can be rolled back because the {@linkplain L2Interpreter
	 * interpreter}'s context module will contain all methods and precedence
	 * rules defined between the transaction start and the rollback (or commit).
	 * Committing simply clears this information.
	 *
	 * @param moduleNameString
	 *        The name of the {@linkplain ModuleDescriptor module}.
	 */
	void startModuleTransaction (final @NotNull AvailObject moduleNameString)
	{
		assert module == null;
		module = ModuleDescriptor.newModule(moduleNameString);
		interpreter.setModule(module);
	}

	/**
	 * Rollback the {@linkplain ModuleDescriptor module} that was defined since
	 * the most recent {@link #startModuleTransaction(AvailObject)}.
	 */
	void rollbackModuleTransaction ()
	{
		assert module != null;
		module.removeFrom(interpreter);
		module = null;
		interpreter.setModule(null);
	}

	/**
	 * Commit the {@linkplain ModuleDescriptor module} that was defined since
	 * the most recent {@link #startModuleTransaction(AvailObject)}. Simply
	 * clear the {@linkplain #module} field.
	 */
	void commitModuleTransaction ()
	{
		assert module != null;
		interpreter.runtime().addModule(module);
		module.cleanUpAfterCompile();
		module = null;
		interpreter.setModule(null);
	}

	/**
	 * Evaluate a parse tree node. It's a top-level statement in a module.
	 * Declarations are handled differently - they cause a variable to be
	 * declared in the module's scope.
	 *
	 * @param expr
	 *        The expression to compile and evaluate as a top-level statement in
	 *        the module.
	 */
	void evaluateModuleStatement (final @NotNull AvailObject expr)
	{
		if (!expr.isInstanceOfKind(DECLARATION_NODE.mostGeneralType()))
		{
			evaluate(expr);
			return;
		}
		// It's a declaration...
		final AvailObject name = expr.token().string();
		if (expr.declarationKind() == LOCAL_CONSTANT)
		{
			final AvailObject val = evaluate(expr.initializationExpression());
			module.addConstantBinding(
				name,
				val.makeImmutable());
		}
		else
		{
			final AvailObject var = VariableDescriptor.forInnerType(
				expr.declaredType());
			if (!expr.initializationExpression().equalsNull())
			{
				var.setValue(evaluate(expr.initializationExpression()));
			}
			module.addVariableBinding(
				name,
				var.makeImmutable());
		}
	}

	/**
	 * A complete {@linkplain SendNodeDescriptor send node} has been parsed.
	 * Create the send node and invoke the continuation.
	 *
	 * <p>
	 * If this is a macro, invoke the body immediately with the argument
	 * expressions to produce a parse node.
	 * </p>
	 *
	 * @param stateBeforeCall
	 *            The initial parsing state, prior to parsing the entire
	 *            message.
	 * @param stateAfterCall
	 *            The parsing state after the message.
	 * @param argumentExpressions
	 *            The {@linkplain ParseNodeDescriptor parse nodes} that will be
	 *            arguments of the new send node.
	 * @param bundle
	 *            The {@linkplain MessageBundleDescriptor message bundle} that
	 *            identifies the message to be sent.
	 * @param continuation
	 *            What to do with the resulting send node.
	 */
	void completedSendNode (
		final ParserState stateBeforeCall,
		final ParserState stateAfterCall,
		final List<AvailObject> argumentExpressions,
		final AvailObject bundle,
		final Con<AvailObject> continuation)
	{
		final Mutable<Boolean> valid = new Mutable<Boolean>(true);
		final AvailObject message = bundle.message();
		final AvailObject impSet = interpreter.runtime().methodsAt(message);
		assert !impSet.equalsNull();
		final AvailObject implementationsTuple = impSet.implementationsTuple();
		assert implementationsTuple.tupleSize() > 0;

		if (implementationsTuple.tupleAt(1).isMacro())
		{
			// Macro definitions and non-macro definitions are not allowed to
			// mix within a method.
			completedSendNodeForMacro(
				stateBeforeCall,
				stateAfterCall,
				argumentExpressions,
				bundle,
				impSet,
				continuation);
			return;
		}
		// It invokes a method (not a macro).
		final List<AvailObject> argTypes =
			new ArrayList<AvailObject>(argumentExpressions.size());
		for (final AvailObject argumentExpression : argumentExpressions)
		{
			argTypes.add(argumentExpression.expressionType());
		}
		final AvailObject returnType =
			impSet.validateArgumentTypesInterpreterIfFail(
				argTypes,
				interpreter,
				new Continuation1<Generator<String>>()
				{
					@Override
					public void value (
						final Generator<String> errorGenerator)
					{
						valid.value = false;
						stateAfterCall.expected(errorGenerator);
					}
				});
		if (valid.value)
		{
			final AvailObject sendNode = SendNodeDescriptor.mutable().create();
			sendNode.method(impSet);
			sendNode.arguments(
				TupleDescriptor.fromCollection(argumentExpressions));
			sendNode.returnType(returnType);
			attempt(
				new ParserState(
					stateAfterCall.position,
					stateBeforeCall.scopeMap),
				continuation,
				sendNode);
		}
	}

	/**
	 * A macro invocation has just been parsed.  Run it now if macro execution
	 * is supported.
	 *
	 * @param stateBeforeCall
	 *            The initial parsing state, prior to parsing the entire
	 *            message.
	 * @param stateAfterCall
	 *            The parsing state after the message.
	 * @param argumentExpressions
	 *            The {@linkplain ParseNodeDescriptor parse nodes} that will be
	 *            arguments of the new send node.
	 * @param bundle
	 *            The {@linkplain MessageBundleDescriptor message bundle} that
	 *            identifies the message to be sent.
	 * @param impSet
	 *            The {@linkplain MethodDescriptor method}
	 *            that contains the macro signature to be invoked.
	 * @param continuation
	 *            What to do with the resulting send node.
	 */
	abstract void completedSendNodeForMacro (
		final ParserState stateBeforeCall,
		final ParserState stateAfterCall,
		final List<AvailObject> argumentExpressions,
		final AvailObject bundle,
		final AvailObject impSet,
		final Con<AvailObject> continuation);

	/**
	 * Parse a {@linkplain ModuleDescriptor module} and install it into the
	 * {@linkplain AvailRuntime runtime}.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ModuleName qualified name} of the {@linkplain
	 *        ModuleDescriptor source module}.
	 * @param aBlock
	 *        A {@linkplain Continuation3 continuation} that accepts the
	 *        {@linkplain ModuleName name} of the {@linkplain ModuleDescriptor
	 *        module} undergoing {@linkplain AbstractAvailCompiler compilation},
	 *        the position of the ongoing parse (in bytes), and the size of the
	 *        module (in bytes).
	 * @throws AvailCompilerException
	 *         If compilation fails.
	 */
	public void parseModule (
			final @NotNull ModuleName qualifiedName,
			final @NotNull Continuation4<ModuleName, Long, Long, Long> aBlock)
		throws AvailCompilerException
	{
		progressBlock = aBlock;
		greatestGuess = -1;
		greatExpectations.clear();
		final ResolvedModuleName resolvedName =
			interpreter.runtime().moduleNameResolver().resolve(qualifiedName);
		source = extractSource(qualifiedName, resolvedName);
		tokens = tokenize(source, false);
		startModuleTransaction(
			StringDescriptor.from(resolvedName.qualifiedName()));
		try
		{
			parseModule(resolvedName);
		}
		catch (final AvailCompilerException e)
		{
			rollbackModuleTransaction();
			throw e;
		}
		catch (final AvailAssertionFailedException e)
		{
			rollbackModuleTransaction();
			final Generator<String> errorProducer = new Generator<String>()
			{
				@Override
				public @NotNull String value ()
				{
					return e.assertionString().asNativeString();
				}
			};
			reportError(
				qualifiedName,
				tokens.get(greatestGuess),
				"Assertion failed ...",
				Collections.singletonList(errorProducer));
		}
		catch (final Throwable e)
		{
			rollbackModuleTransaction();
			final Generator<String> errorProducer = new Generator<String>()
			{
				@Override
				public @NotNull String value ()
				{
					final StringWriter writer = new StringWriter(500);
					final PrintWriter printer = new PrintWriter(writer);
					e.printStackTrace(printer);
					return writer.toString();
				}
			};
			reportError(
				qualifiedName,
				tokens.get(greatestGuess),
				"Encountered exception during compilation ...",
				Collections.singletonList(errorProducer));
		}
		commitModuleTransaction();
	}

	/**
	 * Parse the {@linkplain ModuleDescriptor module} with the specified
	 * fully-qualified {@linkplain ModuleName module name} from the
	 * {@linkplain TokenDescriptor token} stream.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ResolvedModuleName resolved name} of the
	 *        {@linkplain ModuleDescriptor source module}.
	 * @throws AvailCompilerException
	 *         If compilation fails.
	 */
	private final void parseModule (
		final @NotNull ResolvedModuleName qualifiedName)
	throws AvailCompilerException
	{
		final AvailRuntime runtime = interpreter.runtime();
		final ModuleNameResolver resolver = runtime.moduleNameResolver();
		final long sourceLength = qualifiedName.fileReference().length();
		final Mutable<AvailObject> interpretation = new Mutable<AvailObject>();
		final Mutable<ParserState> state = new Mutable<ParserState>();
		assert interpreter.unresolvedForwards().setSize() == 0;
		greatestGuess = 0;
		greatExpectations.clear();

		state.value = parseModuleHeader(qualifiedName, false);
		if (state.value == null)
		{
			reportError(qualifiedName);
			assert false;
		}
		if (!state.value.atEnd())
		{
			final AvailObject token = state.value.peekToken();
			progressBlock.value(
				qualifiedName,
				(long) token.lineNumber(),
				(long) token.start(),
				sourceLength);
		}
		module.versions(TupleDescriptor.fromCollection(versions).asSet());
		for (final AvailObject modImport : extendedModules)
		{
			assert modImport.isTuple();
			assert modImport.tupleSize() == 3;

			final ResolvedModuleName ref = resolver.resolve(
				qualifiedName.asSibling(
					modImport.tupleAt(1).asNativeString()));
			final AvailObject availRef = StringDescriptor.from(
				ref.qualifiedName());
			if (!runtime.includesModuleNamed(availRef))
			{
				state.value.expected(
					"module \"" + ref.qualifiedName()
					+ "\" to be loaded already");
				reportError(qualifiedName);
				assert false;
			}

			final AvailObject mod = runtime.moduleAt(availRef);
			final AvailObject reqVersions = modImport.tupleAt(3);
			if (reqVersions.setSize() > 0)
			{
				final AvailObject modVersions = mod.versions();
				final AvailObject intersection =
					modVersions.setIntersectionCanDestroy(
						reqVersions, false);
				if (intersection.setSize() == 0)
				{
					state.value.expected(
						"version compatibility; module \"" + ref.localName()
						+ "\" guarantees versions " + modVersions
						+ " but current module requires " + reqVersions);
					reportError(qualifiedName);
					assert false;
				}
			}

			final AvailObject modNames = modImport.tupleAt(2).setSize() > 0
				? modImport.tupleAt(2)
				: mod.names().keysAsSet();
			for (final AvailObject strName : modNames)
			{
				if (!mod.names().hasKey(strName))
				{
					state.value.expected(
						"module \"" + ref.qualifiedName()
						+ "\" to export \"" + strName + "\"");
					reportError(qualifiedName);
					assert false;
				}
				final AvailObject trueNames = mod.names().mapAt(strName);
				for (final AvailObject trueName : trueNames)
				{
					module.atNameAdd(strName, trueName);
				}
			}
		}
		for (final AvailObject modImport : usedModules)
		{
			assert modImport.isTuple();
			assert modImport.tupleSize() == 3;

			final ResolvedModuleName ref = resolver.resolve(
				qualifiedName.asSibling(
					modImport.tupleAt(1).asNativeString()));
			final AvailObject availRef = StringDescriptor.from(
				ref.qualifiedName());
			if (!runtime.includesModuleNamed(availRef))
			{
				state.value.expected(
					"module \"" + ref.qualifiedName()
					+ "\" to be loaded already");
				reportError(qualifiedName);
				assert false;
			}

			final AvailObject mod = runtime.moduleAt(availRef);
			final AvailObject reqVersions = modImport.tupleAt(3);
			if (reqVersions.setSize() > 0)
			{
				final AvailObject modVersions = mod.versions();
				final AvailObject intersection =
					modVersions.setIntersectionCanDestroy(
						reqVersions, false);
				if (intersection.setSize() == 0)
				{
					state.value.expected(
						"version compatibility; module \"" + ref.localName()
						+ "\" guarantees versions " + modVersions
						+ " but current module requires " + reqVersions);
					reportError(qualifiedName);
					assert false;
				}
			}

			final AvailObject modNames = modImport.tupleAt(2).setSize() > 0
				? modImport.tupleAt(2)
				: mod.names().keysAsSet();
			for (final AvailObject strName : modNames)
			{
				if (!mod.names().hasKey(strName))
				{
					state.value.expected(
						"module \"" + ref.qualifiedName()
						+ "\" to export \"" + strName + "\"");
					reportError(qualifiedName);
					assert false;
				}
				final AvailObject trueNames = mod.names().mapAt(strName);
				for (final AvailObject trueName : trueNames)
				{
					module.atPrivateNameAdd(strName, trueName);
				}
			}
		}

		for (final AvailObject name : exportedNames)
		{
			assert name.isString();
			final AvailObject trueName = AtomDescriptor.create(
				name,
				module);
			module.atNewNamePut(name, trueName);
			module.atNameAdd(name, trueName);
		}

		for (final AvailObject pragmaString : pragmas)
		{
			final String nativeString = pragmaString.asNativeString();
			final String[] parts = nativeString.split("=", 2);
			assert parts.length == 2;
			final String pragmaKey = parts[0].trim();
			final String pragmaValue = parts[1].trim();
			if (!pragmaKey.matches("\\w+"))
			{
				state.value.expected(
					"pragma key ("
					+ pragmaKey
					+ ") must not contain internal whitespace");
				reportError(qualifiedName);
				assert false;
			}
			if (pragmaKey.equals("bootstrapDefiningMethod"))
			{
				interpreter.bootstrapDefiningMethod(pragmaValue);
			}
			else if (pragmaKey.equals("bootstrapSpecialObject"))
			{
				interpreter.bootstrapSpecialObject(pragmaValue);
			}
		}

		module.buildFilteredBundleTreeFrom(
			interpreter.runtime().rootBundleTree());
		fragmentCache = new AvailCompilerFragmentCache();
		while (!state.value.atEnd())
		{
			greatestGuess = 0;
			greatExpectations.clear();
			interpretation.value = null;
			parseOutermostStatement(
				state.value,
				new Con<AvailObject>("Outermost statement")
				{
					@Override
					public void value (
						final ParserState afterStatement,
						final AvailObject stmt)
					{
						assert interpretation.value == null
							: "Statement parser was supposed to catch "
							+ "ambiguity";
						if (stmt.expressionType().equals(
							BottomTypeDescriptor.bottom()))
						{
							afterStatement.expected(
								"top-level statement not to have type ⊥");
							return;
						}
						interpretation.value = stmt;
						state.value = new ParserState(
							afterStatement.position,
							state.value.scopeMap);
					}
				});
			assert workPool.isEmpty();

			if (interpretation.value == null)
			{
				reportError(qualifiedName);
				assert false;
			}
			// Clear the section of the fragment cache associated with the
			// (outermost) statement just parsed...
			privateClearFrags();
			// Now execute the statement so defining words have a chance to
			// run. This lets the defined words be used in subsequent code.
			// It's even callable in later statements and type expressions.
			try
			{
				evaluateModuleStatement(interpretation.value);
			}
			catch (final AvailAssertionFailedException e)
			{
				reportError(
					qualifiedName,
					tokens.get(state.value.position - 1),
					"Assertion failed...",
					Collections.<Generator<String>>singletonList(
						new Generator<String>()
						{
							@Override
							public @NotNull String value ()
							{
								return e.assertionString().asNativeString();
							}
						}));

			}
			if (!state.value.atEnd())
			{
				final AvailObject token = tokens.get(state.value.position - 1);
				progressBlock.value(
					qualifiedName,
					(long) token.lineNumber(),
					(long) token.start() + 2,
					sourceLength);
			}
		}
		assert state.value.atEnd();
		if (interpreter.unresolvedForwards().setSize() != 0)
		{
			final Formatter formatter = new Formatter();
			formatter.format("the following forwards to be resolved:");
			for (final AvailObject forward : interpreter.unresolvedForwards())
			{
				formatter.format("%n\t%s", forward);
			}
			state.value.expected(formatter.toString());
			reportError(qualifiedName);
		}
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} header for the specified
	 * {@linkplain ModuleName module name}. Populate {@link #extendedModules}
	 * and {@link #usedModules}.
	 *
	 * @param qualifiedName
	 *        The expected module name.
	 * @throws AvailCompilerException
	 *         If compilation fails.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public void parseModuleHeader (final @NotNull ModuleName qualifiedName)
		throws AvailCompilerException
	{
		progressBlock = null;
		greatestGuess = -1;
		greatExpectations.clear();
		final ResolvedModuleName resolvedName =
			interpreter.runtime().moduleNameResolver().resolve(qualifiedName);
		if (parseModuleHeader(resolvedName, true) == null)
		{
			reportError(resolvedName);
			assert false;
		}
	}

	/**
	 * Parse the header of the module from the token stream. If successful,
	 * return the {@link ParserState} just after the header, otherwise return
	 * {@code null}.
	 *
	 * <p>If the {@code dependenciesOnly} parameter is true, only parse the bare
	 * minimum needed to determine information about which modules are used by
	 * this one.</p>
	 *
	 * @param qualifiedName
	 *        The expected module name.
	 * @param dependenciesOnly
	 *        Whether to do the bare minimum parsing required to determine
	 *        the modules to which this one refers.
	 * @return The state of parsing just after the header, or null if it failed.
	 */
	private ParserState parseModuleHeader (
		final @NotNull ResolvedModuleName qualifiedName,
		final boolean dependenciesOnly)
	{
		assert workPool.isEmpty();

		// Initialize module structures.
		versions = new ArrayList<AvailObject>();
		extendedModules = new ArrayList<AvailObject>();
		usedModules = new ArrayList<AvailObject>();
		exportedNames = new ArrayList<AvailObject>();
		pragmas = new ArrayList<AvailObject>();

		// Create the initial parser state: no tokens have been seen, no names
		// are in scope.
		ParserState state = new ParserState(
			0,
			MapDescriptor.empty());

		// The module header must begin with either SYSTEM MODULE or MODULE,
		// followed by the local name of the module.
		if (isSystemCompiler())
		{
			if (!state.peekToken(SYSTEM, "System keyword"))
			{
				return null;
			}
			state = state.afterToken();
		}
		if (!state.peekToken(ExpectedToken.MODULE, "Module keyword"))
		{
			return null;
		}
		state = state.afterToken();
		final AvailObject localNameToken = state.peekStringLiteral();
		if (localNameToken == null)
		{
			state.expected("module name");
			return null;
		}
		if (!dependenciesOnly)
		{
			final AvailObject localName = localNameToken.literal();
			if (!qualifiedName.localName().equals(localName.asNativeString()))
			{
				state.expected("declared local module name to agree with "
						+ "fully-qualified module name");
				return null;
			}
		}
		state = state.afterToken();

		// Module header section tracking.
		final List<ExpectedToken> expected = new ArrayList<ExpectedToken>(
			Arrays.asList(new ExpectedToken[]
				{ VERSIONS, EXTENDS, USES, NAMES, PRAGMA, BODY }));
		final Set<AvailObject> seen = new HashSet<AvailObject>(5);
		final Generator<String> expectedMessage = new Generator<String>()
		{
			@Override
			public @NotNull String value ()
			{
				final StringBuilder builder = new StringBuilder();
				builder.append(
					expected.size() == 1
					? "module header keyword "
					: "one of the following module header keywords: ");
				boolean first = true;
				for (final ExpectedToken token : expected)
				{
					if (!first)
					{
						builder.append(", ");
					}
					builder.append(token.lexeme().asNativeString());
					first = false;
				}
				return builder.toString();
			}
		};

		// Permit the other sections to appear optionally, singly, and in any
		// order. Parsing of the module header is complete when BODY has been
		// consumed.
		while (true)
		{
			final AvailObject token = state.peekToken();
			final AvailObject lexeme = token.string();
			int tokenIndex = 0;
			for (final ExpectedToken expectedToken : expected)
			{
				if (expectedToken.tokenType() == token.tokenType()
					&& expectedToken.lexeme().equals(lexeme))
				{
					break;
				}
				tokenIndex++;
			}
			// The token was not recognized as beginning a module section, so
			// record what was expected and fail the parse.
			if (tokenIndex == expected.size())
			{
				if (seen.contains(lexeme))
				{
					state.expected(
						lexeme.asNativeString()
						+ " keyword (and related section) to occur only once");
				}
				else
				{
					state.expected(expectedMessage);
				}
				return null;
			}
			expected.remove(tokenIndex);
			seen.add(lexeme);
			state = state.afterToken();
			// When BODY has been encountered, the parse of the module header is
			// complete.
			if (lexeme.equals(BODY.lexeme()))
			{
				return state;
			}
			// On VERSIONS, record the versions.
			else if (lexeme.equals(VERSIONS.lexeme()))
			{
				state = parseStringLiterals(state, versions);
			}
			// On EXTENDS, record the imports.
			else if (lexeme.equals(EXTENDS.lexeme()))
			{
				state = parseImports(state, extendedModules);
			}
			// On USES, record the imports.
			else if (lexeme.equals(USES.lexeme()))
			{
				state = parseImports(state, usedModules);
			}
			// On NAMES, record the names.
			else if (lexeme.equals(NAMES.lexeme()))
			{
				state = parseStringLiterals(state, exportedNames);
			}
			// On PRAGMA, record the pragma strings.
			else if (lexeme.equals(PRAGMA.lexeme()))
			{
				state = parseStringLiterals(state, pragmas);
			}
			// If the parser state is now null, then fail the parse.
			if (state == null)
			{
				return null;
			}
		}
	}

	/**
	 * Parse an expression. Backtracking will find all valid interpretations.
	 * This method is a key optimization point, so the fragmentCache is used to
	 * keep track of parsing solutions at this point, simply replaying them on
	 * subsequent parses, as long as the variable declarations up to that point
	 * were identical.
	 *
	 * <p>
	 * Additionally, the fragmentCache also keeps track of actions to perform
	 * when another solution is found at this position, so the solutions and
	 * actions can be added in arbitrary order while ensuring that each action
	 * gets a chance to try each solution.
	 * </p>
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param originalContinuation
	 *        What to do with the expression.
	 */
	void parseExpressionThen (
		final @NotNull ParserState start,
		final @NotNull Con<AvailObject> originalContinuation)
	{
		// The first time we parse at this position the fragmentCache will have
		// no knowledge about it.
		if (!fragmentCache.hasStartedParsingAt(start))
		{
			fragmentCache.indicateParsingHasStartedAt(start);
			eventuallyDo(
				new Continuation0()
				{
					@Override
					public void value ()
					{
						parseExpressionUncachedThen(
							start,
							new Con<AvailObject>("Uncached expression")
							{
								@Override
								public void value (
									final ParserState afterExpr,
									final AvailObject expr)
								{
									fragmentCache.addSolution(
										start,
										new AvailCompilerCachedSolution(
											afterExpr,
											expr));
								}
							});
					}
				},
				"Capture expression for caching",
				start.position);
		}
		fragmentCache.addAction(start, originalContinuation);
	}

	/**
	 * Parse an expression whose type is (at least) someType. Evaluate the
	 * expression, yielding a type, and pass that to the continuation. Note that
	 * the longest syntactically correct and type correct expression is what
	 * gets used. It's an ambiguity error if two or more possible parses of this
	 * maximum length are possible.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param someType
	 *        The type that the expression must return.
	 * @param continuation
	 *        What to do with the result of expression evaluation.
	 */
	void parseAndEvaluateExpressionYieldingInstanceOfThen (
		final @NotNull ParserState start,
		final @NotNull AvailObject someType,
		final @NotNull Con<AvailObject> continuation)
	{
		final ParserState startWithoutScope = new ParserState(
			start.position,
			MapDescriptor.empty());
		parseExpressionThen(startWithoutScope, new Con<AvailObject>(
			"Evaluate expression")
		{
			@Override
			public void value (
				final ParserState afterExpression,
				final AvailObject expression)
			{
				if (expression.expressionType().isSubtypeOf(someType))
				{
					// A unique, longest type-correct expression was found.
					final AvailObject value = evaluate(expression);
					if (value.isInstanceOf(someType))
					{
						assert afterExpression.scopeMap ==
							startWithoutScope.scopeMap
						: "Subexpression should not have been able "
							+ "to cause declaration";
						// Make sure we continue with the position after the
						// expression, but the scope stack we started with.
						// That's because the expression was parsed for
						// execution, and as such was excluded from seeing
						// things that would be in scope for regular
						// subexpressions at this point.
						attempt(
							new ParserState(
								afterExpression.position,
								start.scopeMap),
							continuation,
							value);
					}
					else
					{
						afterExpression.expected(
							"expression to respect its own type declaration");
					}
				}
				else
				{
					afterExpression.expected(
						new Generator<String>()
						{
							@Override
							public String value ()
							{
								return "expression to have type " + someType;
							}
						});
				}
			}
		});
	}

	/**
	 * Parse a top-level statement.  This is the <em>only</em> boundary for the
	 * backtracking grammar (it used to be that <em>all</em> statements had to
	 * be unambiguous, even those in blocks).  The passed continuation will be
	 * invoked at most once, and only if the top-level statement had a single
	 * interpretation.
	 *
	 * <p>
	 * The {@link #workPool} should be empty when invoking this method, as it
	 * will be drained by this method.
	 * </p>
	 *
	 * @param start
	 *            Where to start parsing a top-level statement.
	 * @param continuation
	 *            What to do with the (unambiguous) top-level statement.
	 */
	abstract void parseOutermostStatement (
		final ParserState start,
		final Con<AvailObject> continuation);

	/**
	 * Parse a statement within a block, invoking the continuation with it.
	 * Statements inside a block may be ambiguous, but top level statements may
	 * not.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param canBeLabel
	 *        Whether this statement can be a label declaration.
	 * @param argDecls
	 *        The enclosing block's argument declarations.
	 * @param continuation
	 *        What to do with the unambiguous, parsed statement.
	 */
	abstract void parseInnerStatement (
		final @NotNull ParserState start,
		final boolean canBeLabel,
		final @NotNull List<AvailObject> argDecls,
		final @NotNull Con<AvailObject> continuation);

	/**
	 * Parse an expression, without directly using the
	 * {@linkplain #fragmentCache}.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param continuation
	 *        What to do with the expression.
	 */
	abstract void parseExpressionUncachedThen (
		final @NotNull ParserState start,
		final @NotNull Con<AvailObject> continuation);

	/**
	 * Parse an occurrence of a raw keyword or operator token, then invoke the
	 * continuation with it.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do after parsing the raw token.  This continuation
	 *            should expect a token, not a parse node.
	 */
	protected void parseRawTokenThen (
		final @NotNull ParserState start,
		final @NotNull Con<AvailObject> continuation)
	{
		final AvailObject token = start.peekToken();
		if (token.tokenType() == KEYWORD || token.tokenType() == OPERATOR)
		{
			attempt(start.afterToken(), continuation, token);
		}
		else
		{
			start.expected("raw token");
		}
	}
}
