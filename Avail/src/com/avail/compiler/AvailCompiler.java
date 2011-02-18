/**
 * compiler/AvailCompiler.java Copyright (c) 2010, Mark van Gulik. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

import static com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind.LOCAL_CONSTANT;
import static com.avail.compiler.scanning.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.io.*;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.compiler.node.*;
import com.avail.compiler.scanning.*;
import com.avail.compiler.scanning.TokenDescriptor.TokenType;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * I parse a source file to create a {@link ModuleDescriptor module}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailCompiler
{
	/**
	 * The {@linkplain L2Interpreter interpreter} to use when evaluating
	 * top-level expressions.
	 */
	@InnerAccess
	final @NotNull
	L2Interpreter interpreter;

	/**
	 * The Avail {@linkplain ModuleDescriptor module} undergoing compilation.
	 */
	private AvailObject module;

	/**
	 * The source text of the Avail {@linkplain ModuleDescriptor module}
	 * undergoing compilation.
	 */
	private String source;

	/**
	 * The complete {@linkplain List list} of {@linkplain TokenDescriptor
	 * tokens} parsed from the source text.
	 */
	@InnerAccess
	List<AvailObject> tokens;

	/**
	 * The position of the rightmost {@linkplain TokenDescriptor token} reached
	 * by any parsing attempt.
	 */
	@InnerAccess
	int greatestGuess;

	/**
	 * The {@linkplain List list} of {@linkplain String} {@linkplain Generator
	 * generators} that describe what was expected (but not found) at the
	 * {@linkplain #greatestGuess rightmost reached position}.
	 */
	@InnerAccess
	final @NotNull
	List<Generator<String>> greatExpectations =
		new ArrayList<Generator<String>>();

	/** The memoization of results of previous parsing attempts. */
	@InnerAccess
	AvailCompilerFragmentCache fragmentCache;

	/**
	 * The {@linkplain ModuleDescriptor modules} extended by the module
	 * undergoing compilation.
	 */
	@InnerAccess
	List<AvailObject> extendedModules;

	/**
	 * The {@linkplain ModuleDescriptor modules} used by the module undergoing
	 * compilation.
	 */
	@InnerAccess
	List<AvailObject> usedModules;

	/**
	 * The {@linkplain CyclicTypeDescriptor names} defined and exported by the
	 * {@linkplain ModuleDescriptor module} undergoing compilation.
	 */
	@InnerAccess
	List<AvailObject> exportedNames;

	/**
	 * The {@linkplain Continuation3 action} that should be performed repeatedly
	 * by the {@linkplain AvailCompiler compiler} to report compilation
	 * progress.
	 */
	private Continuation4<ModuleName, Long, Long, Long> progressBlock;

	/**
	 * Construct a new {@link AvailCompiler} which will use the given
	 * {@link Interpreter} to evaluate expressions.
	 *
	 * @param interpreter
	 *            The interpreter to be used for evaluating expressions.
	 */
	public AvailCompiler (@NotNull final L2Interpreter interpreter)
	{
		this.interpreter = interpreter;
	}

	/**
	 * A stack of {@link Continuation0 continuations} that need to be explored
	 * at some point.
	 */
	final Deque<Continuation0> workStack = new ArrayDeque<Continuation0>();

	/**
	 * This is actually a two-argument continuation, but it has only a single
	 * type parameter because the first one is always the {@link ParserState}
	 * that indicates where the continuation should continue parsing.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 * @param <AnswerType>
	 *            The type of the second parameter of the
	 *            {@link Con#value(ParserState, Object)} method.
	 */
	protected abstract class Con<AnswerType> implements
			Continuation2<ParserState, AnswerType>
	{
		/**
		 * A debugging description of this continuation.
		 */
		final String description;

		/**
		 * Construct a new {@link AvailCompiler.Con} with the provided
		 * description.
		 *
		 * @param description
		 *            The provided description.
		 */
		public Con (final String description)
		{
			this.description = description;
		}

		@Override
		public String toString ()
		{
			return "Con(" + description + ")";
		}

		@Override
		public abstract void value (ParserState state, AnswerType answer);
	}

	/**
	 * Execute the block, passing a continuation that it should run upon finding
	 * a local solution. If exactly one solution is found, unwrap the stack (but
	 * not the token stream position or scopeStack), and pass the result to the
	 * continuation. Otherwise report that an unambiguous statement was
	 * expected.
	 *
	 * @param start
	 *            Where to start parsing
	 * @param tryBlock
	 *            The block to attempt.
	 * @param continuation
	 *            What to do if exactly one result was produced.
	 */
	private void tryIfUnambiguousThen (
		final ParserState start,
		final Con<Con<AvailObject>> tryBlock,
		final Con<AvailObject> continuation)
	{
		final Mutable<Integer> count = new Mutable<Integer>(0);
		final Mutable<AvailObject> solution = new Mutable<AvailObject>();
		final Mutable<AvailObject> another = new Mutable<AvailObject>();
		final Mutable<ParserState> where = new Mutable<ParserState>();
		final Mutable<Boolean> markerFired = new Mutable<Boolean>(false);
		attempt(new Continuation0()
		{
			@Override
			public void value ()
			{
				markerFired.value = true;
			}
		}, "Marker for try unambiguous", start.position);
		attempt(start, tryBlock, new Con<AvailObject>("Unambiguous statement")
		{
			@Override
			public void value (
				final ParserState afterSolution,
				final AvailObject aSolution)
			{
				if (count.value == 0)
				{
					solution.value = aSolution;
					where.value = afterSolution;
				}
				else
				{
					if (aSolution == solution.value)
					{
						error("Same solution was presented twice!");
					}
					another.value = aSolution;
				}
				count.value++;
			}
		});
		while (!markerFired.value)
		{
			workStack.pop().value();
		}
		if (count.value > 1)
		{
			// Indicate the problem on the last token of the ambiguous
			// expression.
			ambiguousInterpretationsAnd(new ParserState(
				where.value.position - 1,
				where.value.scopeStack), solution.value, another.value);
			return;
		}
		if (count.value == 0)
		{
			return;
		}
		assert count.value == 1;
		// We found exactly one solution. Advance the token stream just past it,
		// and redo any side-effects to the scopeStack, then invoke the
		// continuation with the solution.

		attempt(where.value, continuation, solution.value);
	}

	/**
	 * {@link ParserState} instances are immutable and keep track of a current
	 * {@link #position} and {@link #scopeStack} during parsing.
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
		 * The {@link AvailCompilerScopeStack scope stack}. This is a
		 * non-destructive singly-linked list of bindings. They're searched
		 * sequentially to resolve variables, but that's not likely to ever be a
		 * bottleneck.
		 */
		final AvailCompilerScopeStack scopeStack;

		/**
		 * Construct a new immutable {@link ParserState}.
		 *
		 * @param position
		 *            The index of the current token.
		 * @param scopeStack
		 *            The {@link AvailCompilerScopeStack}.
		 */
		ParserState (
				final int position,
				final AvailCompilerScopeStack scopeStack)
		{
			assert scopeStack != null;

			this.position = position;
			this.scopeStack = scopeStack;
		}

		@Override
		public int hashCode ()
		{
			return position * 473897843 ^ scopeStack.hashCode();
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
					&& scopeStack.equals(anotherState.scopeStack);
		}

		/**
		 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
		 */
		@Override
		public String toString ()
		{
			return String.format(
				"%s%n" + "\tPOSITION=%d%n" + "\tSCOPE_STACK = %s",
				getClass().getSimpleName(),
				position,
				scopeStack);
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
		 * Answer the token at the current position.
		 *
		 * @return The token.
		 */
		AvailObject peekToken ()
		{
			assert !atEnd();
			return tokens.get(position);
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param tokenType
		 *            The {@link TokenType type} of token to look for.
		 * @param string
		 *            The exact token content to look for.
		 * @return Whether the specified token was found.
		 */
		boolean peekToken (final TokenType tokenType, final String string)
		{
			if (atEnd())
			{
				return false;
			}
			final AvailObject token = peekToken();
			return token.tokenType() == tokenType
					&& token.string().asNativeString().equals(string);
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param tokenType
		 *            The {@link TokenType type} of token to look for.
		 * @param string
		 *            The exact token content to look for.
		 * @param expected
		 *            A generator of a string message to record if the specified
		 *            token is not present.
		 * @return Whether the specified token is present.
		 */
		boolean peekToken (
			final TokenType tokenType,
			final String string,
			final Generator<String> expected)
		{
			final AvailObject token = peekToken();
			final boolean found = token.tokenType() == tokenType
					&& token.string().asNativeString().equals(string);
			if (!found)
			{
				expected(expected);
			}
			return found;
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param tokenType
		 *            The {@link TokenType type} of token to look for.
		 * @param string
		 *            The exact token content to look for.
		 * @param expected
		 *            A message to record if the specified token is not present.
		 * @return Whether the specified token is present.
		 */
		boolean peekToken (
			final TokenType tokenType,
			final String string,
			final String expected)
		{
			return peekToken(tokenType, string, generate(expected));
		}

		/**
		 * Return a new {@link ParserState} like this one, but advanced by one
		 * token.
		 *
		 * @return A new parser state.
		 */
		ParserState afterToken ()
		{
			assert !atEnd();
			return new ParserState(position + 1, scopeStack);
		}

		/**
		 * Parse a string literal. Answer the {@link LiteralTokenDescriptor
		 * string literal token} if found, otherwise answer null.
		 *
		 * @return The actual {@link LiteralTokenDescriptor literal token} or
		 *         null.
		 */
		AvailObject peekStringLiteral ()
		{
			final AvailObject token = peekToken();
			if (token.isInstanceOfSubtypeOf(LITERAL_TOKEN.o()))
			{
				return token;
			}
			return null;
		}

		/**
		 * Return a new {@link ParserState} like this one, but with the given
		 * declaration added.
		 *
		 * @param declaration
		 *            The {@link DeclarationNodeDescriptor declaration} to add
		 *            to the resulting {@link AvailCompilerScopeStack scope
		 *            stack}.
		 * @return The new parser state including the declaration.
		 */
		ParserState withDeclaration (final AvailObject declaration)
		{
			return new ParserState(position, new AvailCompilerScopeStack(
				declaration,
				scopeStack));
		}

		/**
		 * Record an expectation at the current parse position. The expectations
		 * captured at the rightmost parse position constitute the error message
		 * in case the parse fails.
		 * <p>
		 * The expectation is a {@link Generator Generator<String>}, in case
		 * constructing a {@link String} would be prohibitive. There is also
		 * {@link #expected(String) another} version of this method that accepts
		 * a String directly.
		 *
		 * @param stringGenerator
		 *            The {@code Generator<String>} to capture.
		 */
		void expected (final Generator<String> stringGenerator)
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
		 *            The string to look up.
		 */
		void expected (final String aString)
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
	 * {@link ByteStringDescriptor actual Avail strings}.
	 * </p>
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param stringTokens
	 *            The list of strings to populate.
	 * @return The parser state after the list of strings, or null if the list
	 *         of strings is malformed.
	 */
	ParserState parseStringLiterals (
		final ParserState start,
		final List<AvailObject> stringTokens)
	{
		assert stringTokens.isEmpty();

		AvailObject token = start.peekStringLiteral();
		if (token == null)
		{
			return start;
		}
		stringTokens.add(token.literal());
		ParserState state = start.afterToken();
		while (state.peekToken(OPERATOR, ","))
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
	 * Parse a statement. This is the boundary for the backtracking grammar. A
	 * statement must be unambiguous (in isolation) to be valid. The passed
	 * continuation will be invoked at most once, and only if the statement had
	 * a single interpretation.
	 *
	 * <p>
	 * The {@link #workStack} should have the same content before and after this
	 * method is invoked.
	 * </p>
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param outermost
	 *            Whether this statement is outermost in the module.
	 * @param canBeLabel
	 *            Whether this statement can be a label declaration.
	 * @param continuation
	 *            What to do with the unambiguous, parsed statement.
	 */
	private void parseStatementAsOutermostCanBeLabelThen (
		final ParserState start,
		final boolean outermost,
		final boolean canBeLabel,
		final Con<AvailObject> continuation)
	{
		assert !(outermost & canBeLabel);
		tryIfUnambiguousThen(start, new Con<Con<AvailObject>>(
			"Detect ambiguity")
		{
			@Override
			public void value (
				final ParserState ignored,
				final Con<AvailObject> whenFoundStatement)
			{
				parseDeclarationThen(start, new Con<AvailObject>(
					"Semicolon after declaration")
				{
					@Override
					public void value (
						final ParserState afterDeclaration,
						final AvailObject declaration)
					{
						if (afterDeclaration.peekToken(
							END_OF_STATEMENT,
							";",
							"; to end declaration statement"))
						{
							ParserState afterSemicolon =
								afterDeclaration.afterToken();
							if (outermost)
							{
								afterSemicolon = new ParserState(
									afterSemicolon.position,
									new AvailCompilerScopeStack(null, null));
							}
							whenFoundStatement.value(
								afterSemicolon,
								declaration);
						}
					}
				});
				parseExpressionThen(start, new Con<AvailObject>(
					"Semicolon after expression")
				{
					@Override
					public void value (
						final ParserState afterExpression,
						final AvailObject expression)
					{
						if (!afterExpression.peekToken(
							END_OF_STATEMENT,
							";",
							"; to end statement"))
						{
							return;
						}
						if (!outermost
							|| expression.expressionType().equals(
								VOID_TYPE.o()))
						{
							whenFoundStatement.value(
								afterExpression.afterToken(),
								expression);
						}
						else
						{
							afterExpression.expected(
								"outer level statement to have void type");
						}
					}
				});
				if (canBeLabel)
				{
					parseLabelThen(start, new Con<AvailObject>(
						"Semicolon after label")
					{
						@Override
						public void value (
							final ParserState afterDeclaration,
							final AvailObject label)
						{
							if (afterDeclaration.peekToken(
								END_OF_STATEMENT,
								";",
								"; to end label statement"))
							{
								whenFoundStatement.value(
									afterDeclaration.afterToken(),
									label);
							}
						}
					});
				}
			}
		},
			continuation);
	}

	/**
	 * Parse a label declaration, then invoke the continuation.
	 *
	 * @param start
	 *            Where to start parsing
	 * @param continuation
	 *            What to do after parsing a label.
	 */
	void parseLabelThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		if (!start.peekToken(
			OPERATOR,
			"$",
			"label statement starting with \"$\""))
		{
			return;
		}
		final ParserState atName = start.afterToken();
		final AvailObject token = atName.peekToken();
		if (token.tokenType() != KEYWORD)
		{
			atName.expected("name of label after $");
			return;
		}
		final ParserState atColon = atName.afterToken();
		if (!atColon.peekToken(
			OPERATOR,
			":",
			"colon for label's type declaration"))
		{
			return;
		}
		final ParserState afterColon = atColon.afterToken();
		attempt(new Continuation0()
		{
			@Override
			public void value ()
			{
				parseAndEvaluateExpressionYieldingInstanceOfThen(
					afterColon,
					CONTINUATION_TYPE.o(),
					new Con<AvailObject>("Check label type expression")
					{
						@Override
						public void value (
							final ParserState afterExpression,
							final AvailObject contType)
						{
							final AvailObject label =
								DeclarationNodeDescriptor.newLabel(
									token,
									contType);
							final ParserState afterDeclaration =
								afterExpression.withDeclaration(label);
							attempt(afterDeclaration, continuation, label);
						}
					});
			}
		},
			"Label type",
			afterColon.position);
	}

	/**
	 * Parse an expression whose type is (at least) someType. Evaluate the
	 * expression, yielding a type, and pass that to the continuation. Note that
	 * the longest syntactically correct and type correct expression is what
	 * gets used. It's an ambiguity error if two or more possible parses of this
	 * maximum length are possible.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param someType
	 *            The type that the expression must return.
	 * @param continuation
	 *            What to do with the result of expression evaluation.
	 */
	void parseAndEvaluateExpressionYieldingInstanceOfThen (
		final ParserState start,
		final AvailObject someType,
		final Con<AvailObject> continuation)
	{
		final ParserState startWithoutScope = new ParserState(
			start.position,
			new AvailCompilerScopeStack(null, null));
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
					if (value.isInstanceOfSubtypeOf(someType))
					{
						assert afterExpression.scopeStack ==
							startWithoutScope.scopeStack
						: "Subexpression should not have been able "
							+ "to cause declaration";
						// Make sure we continue with the position after the
						// expression, but the scope stack we started with.
						// That's because the expression was parsed for
						// execution, and as such was excluded from seeing
						// things that would be in scope for regular
						// subexpressions at this point.
						attempt(new ParserState(
							afterExpression.position,
							start.scopeStack), continuation, value);
					}
					else
					{
						afterExpression.expected(
							"expression to respect its own type declaration");
					}
				}
				else
				{
					afterExpression.expected(new Generator<String>()
					{
						@Override
						public String value ()
						{
							return "expression to have type "
								+ someType.toString();
						}
					});
				}
			}
		});
	}

	/**
	 * Parse a local variable declaration. These have one of three forms:
	 * <ul>
	 * <li>a simple declaration (var : type),</li>
	 * <li>an initializing declaration (var : type := value), or</li>
	 * <li>a constant declaration (var ::= expr).</li>
	 * </ul>
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the local variable declaration.
	 */
	void parseDeclarationThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		final AvailObject localName = start.peekToken();
		if (localName.tokenType() != KEYWORD)
		{
			start.expected("a variable or constant declaration");
			return;
		}
		final ParserState afterVar = start.afterToken();
		if (!afterVar.peekToken(
			OPERATOR,
			":",
			": or ::= for simple/constant/initializing declaration"))
		{
			return;
		}
		final ParserState afterFirstColon = afterVar.afterToken();
		if (afterFirstColon.peekToken(
			OPERATOR,
			":",
			"second colon for constant declaration (a ::= expr)"))
		{
			final ParserState afterSecondColon = afterFirstColon.afterToken();
			if (afterSecondColon.peekToken(
				OPERATOR,
				"=",
				"= part of ::= in constant declaration"))
			{
				final ParserState afterEquals = afterSecondColon.afterToken();
				parseExpressionThen(afterEquals, new Con<AvailObject>(
					"Complete var ::= expr")
				{
					@Override
					public void value (
						final ParserState afterInitExpression,
						final AvailObject initExpression)
					{
						final AvailObject constantDeclaration =
							DeclarationNodeDescriptor.newConstant(
								localName,
								initExpression);
						attempt(
							afterInitExpression.withDeclaration(
								constantDeclaration),
							continuation,
							constantDeclaration);
					}
				});
			}
		}
		parseAndEvaluateExpressionYieldingInstanceOfThen(
			afterFirstColon,
			TYPE.o(),
			new Con<AvailObject>("Type expression of var : type")
			{
				@Override
				public void value (
					final ParserState afterType,
					final AvailObject type)
				{
					if (type.equals(VOID_TYPE.o())
							|| type.equals(TERMINATES.o()))
					{
						afterType.expected(
							"a type for the variable other than"
							+ " void or terminates");
						return;
					}
					// Try the simple declaration... var : type;
					final AvailObject simpleDeclaration =
						DeclarationNodeDescriptor.newVariable(localName, type);
					attempt(
						afterType.withDeclaration(simpleDeclaration),
						continuation,
						simpleDeclaration);

					// Also try for var : type := init.
					if (!afterType.peekToken(
						OPERATOR,
						":",
						"Second colon of var : type := init"))
					{
						return;
					}
					final ParserState afterSecondColon = afterType.afterToken();
					if (!afterSecondColon.peekToken(
						OPERATOR,
						"=",
						"Equals sign in var : type := init"))
					{
						return;
					}
					final ParserState afterEquals =
						afterSecondColon.afterToken();

					parseExpressionThen(afterEquals, new Con<AvailObject>(
						"After expr of var : type := expr")
					{
						@Override
						public void value (
							final ParserState afterInit,
							final AvailObject initExpr)
						{
							if (initExpr.expressionType().isSubtypeOf(type))
							{
								final AvailObject initDecl =
									DeclarationNodeDescriptor.newVariable(
										localName,
										type,
										initExpr);
								attempt(
									afterInit.withDeclaration(initDecl),
									continuation,
									initDecl);
							}
							else
							{
								afterInit.expected(
									"initializing expression's type to "
									+ "agree with declared type");
							}
						}
					});
				}
			});
	}

	/**
	 * Attempt the zero-argument continuation. The implementation is free to
	 * execute it now or to put it in a stack of continuations to run later, but
	 * they have to be run in the reverse order that they were pushed.
	 *
	 * @param continuation
	 *            What to do at some point in the future.
	 * @param description
	 *            Debugging information about what is to be parsed.
	 * @param position
	 *            Debugging information about where the parse is happening.
	 */
	void attempt (
		final Continuation0 continuation,
		final String description,
		final int position)
	{
		workStack.push(new Continuation0()
		{
			@Override
			public void value ()
			{
				continuation.value();
			}
		});
		if (workStack.size() > 1000)
		{
			throw new RuntimeException("Probable recursive parse error");
		}
	}

	/**
	 * Wrap the {@link Continuation1 continuation of one argument} inside a
	 * {@link Continuation0 continuation of zero arguments} and record that as
	 * per {@link #attempt(Continuation0, String, int)}.
	 *
	 * @param <ArgType>
	 *            The type of argument to the given continuation.
	 * @param here
	 *            Where to start parsing when the continuation runs.
	 * @param continuation
	 *            What to execute with the passed argument.
	 * @param argument
	 *            What to pass as an argument to the provided
	 *            {@code Continuation1 one-argument continuation}.
	 */
	<ArgType> void attempt (
		final ParserState here,
		final Con<ArgType> continuation,
		final ArgType argument)
	{
		attempt(new Continuation0()
		{
			@Override
			public void value ()
			{
				continuation.value(here, argument);
			}
		}, continuation.description, here.position);
	}

	/**
	 * Evaluate a {@link ParseNodeDescriptor parse node} in the module's
	 * context; lexically enclosing variables are not considered in scope, but
	 * module variables and constants are in scope.
	 *
	 * @param expressionNode
	 *            A {@link ParseNodeDescriptor parse node}.
	 * @return The result of generating a {@link ClosureDescriptor closure} from
	 *         the argument and evaluating it.
	 */
	AvailObject evaluate (final AvailObject expressionNode)
	{
		final AvailObject block = BlockNodeDescriptor.newBlockNode(
			Collections.<AvailObject>emptyList(),
			(short) 0,
			Collections.singletonList(expressionNode),
			VOID_TYPE.o());
		validate(block);
		final AvailCodeGenerator codeGenerator = new AvailCodeGenerator();
		final AvailObject compiledBlock = block.generate(codeGenerator);
		// The block is guaranteed context-free (because imported
		// variables/values are embedded directly as constants in the generated
		// code), so build a closure with no copied data.
		assert compiledBlock.numOuters() == 0;
		final AvailObject closure = ClosureDescriptor.create(
			compiledBlock,
			TupleDescriptor.empty());
		closure.makeImmutable();
		List<AvailObject> args;
		args = new ArrayList<AvailObject>();
		final AvailObject result = interpreter.runClosureArguments(
			closure,
			args);
		// System.out.println(Integer.toString(position) + " evaluated (" +
		// expressionNode.toString() + ") = " + result.toString());
		return result;
	}

	/**
	 * Ensure that the {@link BlockNodeDescriptor block node} is valid. Throw an
	 * appropriate exception if it is not.
	 *
	 * @param blockNode
	 *            The block node to validate.
	 */
	public void validate (final AvailObject blockNode)
	{
		final List<AvailObject> blockStack = new ArrayList<AvailObject>(3);
		treeDoWithParent(
			blockNode,
			new Continuation3<
					AvailObject,
					AvailObject,
					List<AvailObject>>()
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
	 * Map the tree through the (destructive) transformation specified by
	 * aBlock, children before parents. The block takes three arguments: the
	 * node, its parent, and the list of enclosing block nodes. Answer the
	 * resulting tree.
	 *
	 * @param object
	 *            The current {@linkplain ParseNodeDescriptor parse node}.
	 * @param aBlock
	 *            What to do with each descendant.
	 * @param parentNode
	 *            This node's parent.
	 * @param outerNodes
	 *            The list of {@linkplain BlockNodeDescriptor blocks}
	 *            surrounding this node, from outermost to innermost.
	 * @param nodeMap
	 *            The {@link Map} from old {@linkplain ParseNodeDescriptor
	 *            parse nodes} to newly copied, mutable parse nodes.  This
	 *            should ensure the consistency of declaration references.
	 * @return A replacement for this node, possibly this node itself.
	 */
	public static AvailObject treeMapWithParent (
		final AvailObject object,
		final Transformer3<
				AvailObject,
				AvailObject,
				List<AvailObject>,
				AvailObject>
			aBlock,
		final AvailObject parentNode,
		final List<AvailObject> outerNodes,
		final Map<AvailObject, AvailObject> nodeMap)
	{
		if (nodeMap.containsKey(object))
		{
			return object;
		}
		final AvailObject objectCopy = object.copyMutableParseNode();
		objectCopy.childrenMap(new Transformer1<AvailObject, AvailObject>()
		{
			@Override
			public AvailObject value (final AvailObject child)
			{
				assert child.isInstanceOfSubtypeOf(PARSE_NODE.o());
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
	 * Visit the entire tree with the given {@link Continuation3 block},
	 * children before parents.  The block takes three arguments: the
	 * node, its parent, and the list of enclosing block nodes.
	 *
	 * @param object
	 *            The current {@linkplain ParseNodeDescriptor parse node}.
	 * @param aBlock
	 *            What to do with each descendant.
	 * @param parentNode
	 *            This node's parent.
	 * @param outerNodes
	 *            The list of {@linkplain BlockNodeDescriptor blocks}
	 *            surrounding this node, from outermost to innermost.
	 */
	public static void treeDoWithParent (
		final AvailObject object,
		final Continuation3<AvailObject, AvailObject, List<AvailObject>> aBlock,
		final AvailObject parentNode,
		final List<AvailObject> outerNodes)
	{
		object.childrenDo(new Continuation1<AvailObject>()
		{
			@Override
			public void value (final AvailObject child)
			{
				assert child.isInstanceOfSubtypeOf(PARSE_NODE.o());
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
	 * Evaluate a parse tree node. It's a top-level statement in a module.
	 * Declarations are handled differently - they cause a variable to be
	 * declared in the module's scope.
	 *
	 * @param expr
	 *            The expression to compile and evaluate as a top-level
	 *            statement in the module.
	 */
	private void evaluateModuleStatement (final AvailObject expr)
	{
		if (!expr.isInstanceOfSubtypeOf(DECLARATION_NODE.o()))
		{
			evaluate(expr);
			return;
		}
		// It's a declaration...
		final AvailObject name = expr.token().string();
		if (expr.declarationKind() == LOCAL_CONSTANT)
		{
			final AvailObject val = evaluate(expr.initializationExpression());
			module.constantBindings(
				module.constantBindings().mapAtPuttingCanDestroy(
					name,
					val.makeImmutable(),
					true));
		}
		else
		{
			final AvailObject var = ContainerDescriptor.forInnerType(
				expr.declaredType());
			if (!expr.initializationExpression().equalsVoid())
			{
				var.setValue(evaluate(expr.initializationExpression()));
			}
			module.variableBindings(
				module.variableBindings().mapAtPuttingCanDestroy(
					name,
					var.makeImmutable(),
					true));
		}
	}

	/**
	 * Tokenize the {@linkplain ModuleDescriptor module} specified by the
	 * fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param qualifiedName
	 *            A fully-qualified {@linkplain ModuleName module name}.
	 * @param stopAfterNamesToken
	 *            Stop scanning after encountering the <em>Names</em> token?
	 * @return The {@linkplain ResolvedModuleName resolved module name}.
	 * @throws AvailCompilerException
	 *             If tokenization failed for any reason.
	 */
	private @NotNull
	ResolvedModuleName tokenize (
		final @NotNull ModuleName qualifiedName,
		final boolean stopAfterNamesToken) throws AvailCompilerException
	{
		final ModuleNameResolver resolver =
			interpreter.runtime().moduleNameResolver();
		final ResolvedModuleName resolvedName = resolver.resolve(qualifiedName);
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

		tokens = new AvailScanner().scanString(source, stopAfterNamesToken);
		return resolvedName;
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} and install it into the
	 * {@linkplain AvailRuntime runtime}.
	 *
	 * @param qualifiedName
	 *            The {@linkplain ModuleName qualified name} of the
	 *            {@linkplain ModuleDescriptor source module}.
	 * @param aBlock
	 *            A {@linkplain Continuation3 continuation} that accepts the
	 *            {@linkplain ModuleName name} of the
	 *            {@linkplain ModuleDescriptor module} undergoing
	 *            {@linkplain AvailCompiler compilation}, the position of the
	 *            ongoing parse (in bytes), and the size of the module (in
	 *            bytes).
	 * @throws AvailCompilerException
	 *             If compilation fails.
	 */
	public void parseModule (
		final @NotNull ModuleName qualifiedName,
		final @NotNull Continuation4<ModuleName, Long, Long, Long> aBlock)
	throws AvailCompilerException
	{
		progressBlock = aBlock;
		greatestGuess = -1;
		greatExpectations.clear();
		final ResolvedModuleName resolvedName = tokenize(qualifiedName, false);
		startModuleTransaction(
			ByteStringDescriptor.from(qualifiedName.qualifiedName()));
		try
		{
			parseModule(resolvedName);
		}
		catch (final AvailCompilerException e)
		{
			rollbackModuleTransaction();
			throw e;
		}
		commitModuleTransaction();
	}

	/**
	 * Parse the {@linkplain ModuleDescriptor module} with the specified
	 * fully-qualified {@linkplain ModuleName module name} from the
	 * {@linkplain TokenDescriptor token} stream.
	 *
	 * @param qualifiedName
	 *            The {@linkplain ResolvedModuleName resolved name} of the
	 *            {@linkplain ModuleDescriptor source module}.
	 * @throws AvailCompilerException
	 *             If compilation fails.
	 */
	private void parseModule (final @NotNull ResolvedModuleName qualifiedName)
	throws AvailCompilerException
	{
		final AvailRuntime runtime = interpreter.runtime();
		final ModuleNameResolver resolver = runtime.moduleNameResolver();
		final long sourceLength = qualifiedName.fileReference().length();
		final Mutable<AvailObject> interpretation = new Mutable<AvailObject>();
		final Mutable<ParserState> state = new Mutable<ParserState>();
		interpreter.checkUnresolvedForwards();
		greatestGuess = 0;
		greatExpectations.clear();

		state.value = parseHeader(qualifiedName, false);
		if (state.value == null)
		{
			reportError(new ParserState(0, new AvailCompilerScopeStack(
				null,
				null)), qualifiedName);
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
		for (final AvailObject modName : extendedModules)
		{
			assert modName.isString();
			final ModuleName ref = resolver.canonicalNameFor(
				qualifiedName.asSibling(modName.asNativeString()));
			final AvailObject availRef = ByteStringDescriptor.from(
				ref.qualifiedName());
			if (!runtime.includesModuleNamed(availRef))
			{
				state.value.expected(
					"module \"" + ref.qualifiedName()
					+ "\" to be loaded already");
				reportError(state.value, qualifiedName);
				assert false;
			}
			final AvailObject mod = runtime.moduleAt(availRef);
			final AvailObject modNames = mod.names().keysAsSet();
			for (final AvailObject strName : modNames)
			{
				final AvailObject trueNames = mod.names().mapAt(strName);
				for (final AvailObject trueName : trueNames)
				{
					module.atNameAdd(strName, trueName);
				}
			}
		}
		for (final AvailObject modName : usedModules)
		{
			assert modName.isString();
			final ModuleName ref = resolver.canonicalNameFor(
				qualifiedName.asSibling(modName.asNativeString()));
			final AvailObject availRef = ByteStringDescriptor.from(
				ref.qualifiedName());
			if (!runtime.includesModuleNamed(availRef))
			{
				state.value.expected(
					"module \"" + ref.qualifiedName()
					+ "\" to be loaded already");
				reportError(state.value, qualifiedName);
				assert false;
			}
			final AvailObject mod = runtime.moduleAt(availRef);
			final AvailObject modNames = mod.names().keysAsSet();
			for (final AvailObject strName : modNames)
			{
				final AvailObject trueNames = mod.names().mapAt(strName);
				for (final AvailObject trueName : trueNames)
				{
					module.atPrivateNameAdd(strName, trueName);
				}
			}
		}
		for (final AvailObject stringObject : exportedNames)
		{
			assert stringObject.isString();
			final AvailObject trueNameObject =
				CyclicTypeDescriptor.create(stringObject);
			module.atNameAdd(stringObject, trueNameObject);
			module.atNewNamePut(stringObject, trueNameObject);
		}
		module.buildFilteredBundleTreeFrom(
			interpreter.runtime().rootBundleTree());
		fragmentCache = new AvailCompilerFragmentCache();
		while (!state.value.atEnd())
		{
			greatestGuess = 0;
			greatExpectations.clear();
			interpretation.value = null;
			parseStatementAsOutermostCanBeLabelThen(
				state.value,
				true,
				false,
				new Con<AvailObject>("Outermost statement")
				{
					@Override
					public void value (
						final ParserState afterStatement,
						final AvailObject stmt)
					{
						assert interpretation.value == null
						: "Statement parser was supposed to catch ambiguity";
						interpretation.value = stmt;
						state.value = afterStatement;
					}
				});
			while (!workStack.isEmpty())
			{
				workStack.pop().value();
			}

			if (interpretation.value == null)
			{
				reportError(state.value, qualifiedName);
				assert false;
			}
			// Clear the section of the fragment cache associated with the
			// (outermost) statement just parsed...
			privateClearFrags();
			// Now execute the statement so defining words have a chance to
			// run. This lets the defined words be used in subsequent code.
			// It's even callable in later statements and type expressions.
			evaluateModuleStatement(interpretation.value);
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
		interpreter.checkUnresolvedForwards();
		assert state.value.atEnd();
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} header from the specified
	 * string. Populate {@link #extendedModules} and {@link #usedModules}.
	 *
	 * @param qualifiedName
	 *            The {@linkplain ModuleName qualified name} of the
	 *            {@linkplain ModuleDescriptor source module}.
	 * @throws AvailCompilerException
	 *             If compilation fails.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	void parseModuleHeader (final @NotNull ModuleName qualifiedName)
			throws AvailCompilerException
	{
		progressBlock = null;
		greatestGuess = -1;
		greatExpectations.clear();
		final ResolvedModuleName resolvedName = tokenize(qualifiedName, true);
		if (parseHeader(resolvedName, true) == null)
		{
			reportError(new ParserState(0, new AvailCompilerScopeStack(
				null,
				null)), resolvedName);
			assert false;
		}
	}

	/**
	 * Parse the header of the module from the token stream. If successful,
	 * return the {@link ParserState} just after the header, otherwise return
	 * null.
	 *
	 * <p>
	 * If the dependenciesOnly parameter is true, only parse the bare minimum
	 * needed to determine information about which modules are used by this one.
	 * </p>
	 *
	 * @param qualifiedName
	 *            The expected module name.
	 * @param dependenciesOnly
	 *            Whether to do the bare minimum parsing required to determine
	 *            the modules to which this one refers.
	 * @return The state of parsing just after the header, or null if it failed.
	 */
	private ParserState parseHeader (
		final @NotNull ResolvedModuleName qualifiedName,
		final boolean dependenciesOnly)
	{
		assert workStack.isEmpty();
		extendedModules = new ArrayList<AvailObject>();
		usedModules = new ArrayList<AvailObject>();
		exportedNames = new ArrayList<AvailObject>();
		ParserState state = new ParserState(0, new AvailCompilerScopeStack(
			null,
			null));

		if (!state.peekToken(KEYWORD, "Module", "initial Module keyword"))
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

		if (state.peekToken(KEYWORD, "Pragma"))
		{
			state = state.afterToken();
			final List<AvailObject> strings = new ArrayList<AvailObject>();
			state = parseStringLiterals(state, strings);
			if (state == null)
			{
				return null;
			}
			if (!dependenciesOnly)
			{
				for (int index = 0; index < strings.size(); index++)
				{
					final AvailObject pragmaString = strings.get(index);
					final String nativeString = pragmaString.asNativeString();
					final String[] parts = nativeString.split("=", 2);
					assert parts.length == 2;
					final String pragmaKey = parts[0].trim();
					final String pragmaValue = parts[1].trim();
					if (!pragmaKey.matches("\\w+"))
					{
						final ParserState badStringState = new ParserState(
							state.position + (index - strings.size()) * 2 + 1,
							state.scopeStack);
						badStringState.expected("pragma key (" + pragmaKey
								+ ") must not contain internal whitespace");
						return null;
					}
					if (pragmaKey.equals("bootstrapDefiningMethod"))
					{
						interpreter.bootstrapDefiningMethod(pragmaValue);
					}
					else if (pragmaKey.equals("bootstrapSpecialObject"))
					{
						interpreter.bootstrapSpecialObject(pragmaValue);
					}
					else if (pragmaKey.equals("bootstrapAssignmentStatement"))
					{
						interpreter.bootstrapAssignmentStatement(pragmaValue);
					}
				}
			}
		}

		if (!state.peekToken(KEYWORD, "Extends", "Extends keyword"))
		{
			return null;
		}
		state = state.afterToken();
		state = parseStringLiterals(state, extendedModules);
		if (state == null)
		{
			return null;
		}

		if (!state.peekToken(KEYWORD, "Uses", "Uses keyword"))
		{
			return null;
		}
		state = state.afterToken();
		state = parseStringLiterals(state, usedModules);
		if (state == null)
		{
			return null;
		}

		if (!state.peekToken(KEYWORD, "Names", "Names keyword"))
		{
			return null;
		}
		state = state.afterToken();
		if (dependenciesOnly)
		{
			// We've parsed everything necessary for intermodule information.
			return state;
		}
		state = parseStringLiterals(state, exportedNames);
		if (state == null)
		{
			return null;
		}

		if (!state.peekToken(KEYWORD, "Body", "Body keyword"))
		{
			return null;
		}
		state = state.afterToken();

		assert workStack.isEmpty();
		return state;
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
	 * @param state
	 *            Where the error occurred.
	 * @param qualifiedName
	 *            The {@linkplain ModuleName qualified name} of the
	 *            {@linkplain ModuleDescriptor source module}.
	 * @throws AvailCompilerException
	 *             Always thrown.
	 */
	private void reportError (
		final ParserState state,
		final @NotNull ModuleName qualifiedName) throws AvailCompilerException
	{
		final long charPos = tokens.get(greatestGuess).start();
		final String sourceUpToError = source.substring(0, (int) charPos);
		final int startOfPreviousLine = sourceUpToError.lastIndexOf('\n') + 1;
		final StringBuilder text = new StringBuilder(100);
		text.append('\n');
		int wedges = 3;
		for (int i = startOfPreviousLine; i < charPos; i++)
		{
			if (source.codePointAt(i) == '\t')
			{
				while (wedges > 0)
				{
					text.append('>');
					wedges--;
				}
				text.append('\t');
			}
			else
			{
				if (wedges > 0)
				{
					text.append('>');
					wedges--;
				}
				else
				{
					text.append(' ');
				}
			}
		}
		text.append("^-- Expected...");
		text.append("\n>>>---------------------------------------------------------------------");
		assert greatExpectations.size() > 0 : "Bug - empty expectation list";
		final Set<String> alreadySeen = new HashSet<String>(
			greatExpectations.size());
		for (final Generator<String> generator : greatExpectations)
		{
			final String str = generator.value();
			if (!alreadySeen.contains(str))
			{
				text.append("\n");
				alreadySeen.add(str);
				text.append(">>>\t");
				text.append(str.replace("\n", "\n>>>\t"));
			}
		}
		text.append("\n>>>---------------------------------------------------------------------");
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
	 * Parse more of a block's formal arguments from the token stream. A
	 * vertical bar is required after the arguments if there are any (which
	 * there are if we're here).
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param argsSoFar
	 *            The arguments that have been parsed so far.
	 * @param continuation
	 *            What to do with the list of arguments.
	 */
	void parseAdditionalBlockArgumentsAfterThen (
		final ParserState start,
		final List<AvailObject> argsSoFar,
		final Con<List<AvailObject>> continuation)
	{
		if (start.peekToken(OPERATOR, ",", "comma and more block arguments"))
		{
			parseBlockArgumentThen(start.afterToken(), new Con<AvailObject>(
				"Additional block argument")
			{
				@Override
				public void value (
					final ParserState afterArgument,
					final AvailObject arg)
				{
					final List<AvailObject> newArgsSoFar =
						new ArrayList<AvailObject>(argsSoFar);
					newArgsSoFar.add(arg);
					parseAdditionalBlockArgumentsAfterThen(
						afterArgument,
						Collections.unmodifiableList(newArgsSoFar),
						continuation);
				}
			});
		}

		if (start.peekToken(
			OPERATOR,
			"|",
			"command and more block arguments or a vertical bar"))
		{
			attempt(
				start.afterToken(),
				continuation,
				new ArrayList<AvailObject>(argsSoFar));
		}
	}

	/**
	 * Parse a block's formal arguments from the token stream. A vertical bar
	 * ("|") is required after the arguments if there are any.
	 *
	 * @param start
	 *            Where to parse.
	 * @param continuation
	 *            What to do with the list of block arguments.
	 */
	private void parseBlockArgumentsThen (
		final ParserState start,
		final Con<List<AvailObject>> continuation)
	{
		// Try it with no arguments.
		attempt(start, continuation, new ArrayList<AvailObject>());
		parseBlockArgumentThen(start, new Con<AvailObject>("Block argument")
		{
			@Override
			public void value (
				final ParserState afterFirstArg,
				final AvailObject firstArg)
			{
				parseAdditionalBlockArgumentsAfterThen(
					afterFirstArg,
					Collections.singletonList(firstArg),
					continuation);
			}
		});
	}

	/**
	 * Parse a single block argument.
	 *
	 * @param start
	 *            Where to parse.
	 * @param continuation
	 *            What to do with the parsed block argument.
	 */
	void parseBlockArgumentThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		final AvailObject localName = start.peekToken();
		if (localName.tokenType() != KEYWORD)
		{
			start.expected(": then block argument type");
			return;
		}
		final ParserState afterArgName = start.afterToken();
		if (!afterArgName.peekToken(OPERATOR, ":", ": then argument type"))
		{
			return;
		}
		parseAndEvaluateExpressionYieldingInstanceOfThen(
			afterArgName.afterToken(),
			TYPE.o(),
			new Con<AvailObject>("Type of block argument")
			{
				@Override
				public void value (
					final ParserState afterArgType,
					final AvailObject type)
				{
					if (type.equals(VOID_TYPE.o()))
					{
						afterArgType.expected(
							"a type for the argument other than void");
					}
					else if (type.equals(TERMINATES.o()))
					{
						afterArgType.expected(
							"a type for the argument other than terminates");
					}
					else
					{
						final AvailObject decl =
							DeclarationNodeDescriptor.newArgument(
								localName,
								type);
						attempt(
							afterArgType.withDeclaration(decl),
							continuation,
							decl);
					}
				}
			});
	}

	/**
	 * Parse a block (a closure).
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the parsed block.
	 */
	private void parseBlockThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		if (!start.peekToken(OPERATOR, "["))
		{
			// Don't suggest a block was expected here unless at least the "["
			// was present.
			return;
		}
		final AvailCompilerScopeStack scopeOutsideBlock = start.scopeStack;
		parseBlockArgumentsThen(start.afterToken(), new Con<List<AvailObject>>(
			"Block arguments")
		{
			@Override
			public void value (
				final ParserState afterArguments,
				final List<AvailObject> arguments)
			{
				parseOptionalPrimitiveForArgCountThen(
					afterArguments,
					arguments.size(),
					new Con<Short>("Optional primitive")
					{
						@Override
						public void value (
							final ParserState afterOptionalPrimitive,
							final Short primitive)
						{
							parseStatementsThen(
								afterOptionalPrimitive,
								new Con<List<AvailObject>>("Block statements")
								{
									@Override
									public void value (
										final ParserState afterStatements,
										final List<AvailObject> statements)
									{
										finishBlockThen(
											afterStatements,
											arguments,
											primitive,
											statements,
											scopeOutsideBlock,
											continuation);
									}
								});
						}
					});
			}
		});
	}

	/**
	 * Finish parsing a block. We've just parsed the list of statements.
	 *
	 * @param afterStatements
	 *            Where to start parsing, now that the statements have all been
	 *            parsed.
	 * @param arguments
	 *            The list of block arguments.
	 * @param primitive
	 *            The primitive number
	 * @param statements
	 *            The list of statements.
	 * @param scopeOutsideBlock
	 *            The scope that existed before the block started to be parsed.
	 * @param continuation
	 *            What to do with the {@link BlockNodeDescriptor block}.
	 */
	void finishBlockThen (
		final ParserState afterStatements,
		final List<AvailObject> arguments,
		final short primitive,
		final List<AvailObject> statements,
		final AvailCompilerScopeStack scopeOutsideBlock,
		final Con<AvailObject> continuation)
	{
		if (primitive != 0 && primitive != 256 && statements.isEmpty())
		{
			afterStatements.expected(
				"mandatory failure code for primitive method (except #256)");
			return;
		}
		if (!afterStatements.peekToken(
			OPERATOR,
			"]",
			"close bracket (']') to end block"))
		{
			return;
		}
		final ParserState afterClose = afterStatements.afterToken();

		final Mutable<AvailObject> lastStatementType =
			new Mutable<AvailObject>();
		if (statements.size() > 0)
		{
			final AvailObject stmt = statements.get(statements.size() - 1);
			if (stmt.isInstanceOfSubtypeOf(DECLARATION_NODE.o()))
			{
				lastStatementType.value = VOID_TYPE.o();
			}
			else
			{
				lastStatementType.value = stmt.expressionType();
			}
		}
		else
		{
			lastStatementType.value = VOID_TYPE.o();
		}
		final ParserState stateOutsideBlock = new ParserState(
			afterClose.position,
			scopeOutsideBlock);

		if (statements.isEmpty() && primitive != 0)
		{
			afterClose.expected(
				"return type declaration for primitive block with "
				+ "no statements");
		}
		else
		{
			boolean blockTypeGood = true;
			if (statements.size() > 0
					&& statements.get(0).isInstanceOfSubtypeOf(LABEL_NODE.o()))
			{
				final AvailObject labelNode = statements.get(0);
				final AvailObject labelClosureType =
					labelNode.declaredType().closureType();
				blockTypeGood = labelClosureType.numArgs() == arguments.size()
						&& labelClosureType.returnType().equals(
							lastStatementType.value);
				for (int i = 1; i <= arguments.size(); i++)
				{
					if (blockTypeGood
							&& !labelClosureType.argTypeAt(i).equals(
								arguments.get(i - 1).declaredType()))
					{
						blockTypeGood = false;
					}
				}
			}
			if (blockTypeGood)
			{
				final AvailObject blockNode = BlockNodeDescriptor.newBlockNode(
					arguments,
					primitive,
					statements,
					lastStatementType.value);
				attempt(stateOutsideBlock, continuation, blockNode);
			}
			else
			{
				afterClose.expected(
					"block with label to have return type void "
					+ "(otherwise exiting would need to provide a value)");
			}
		}

		if (!stateOutsideBlock.peekToken(
			OPERATOR,
			":",
			"optional block return type declaration"))
		{
			return;
		}
		parseAndEvaluateExpressionYieldingInstanceOfThen(
			stateOutsideBlock.afterToken(),
			TYPE.o(),
			new Con<AvailObject>("Block return type declaration")
			{
				@Override
				public void value (
					final ParserState afterReturnType,
					final AvailObject returnType)
				{
					if (statements.isEmpty() && primitive != 0
							|| lastStatementType.value.isSubtypeOf(returnType))
					{
						boolean blockTypeGood = true;
						if (statements.size() > 0
								&& statements.get(0).isInstanceOfSubtypeOf(
									LABEL_NODE.o()))
						{
							final AvailObject labelNode = statements.get(0);
							final AvailObject labelClosureType =
								labelNode.declaredType().closureType();
							blockTypeGood = labelClosureType.numArgs()
									== arguments.size()
								&& labelClosureType.returnType().equals(
									returnType);
							for (int i = 1; i <= arguments.size(); i++)
							{
								if (blockTypeGood
									&& !labelClosureType.argTypeAt(i).equals(
										arguments.get(i - 1).declaredType()))
								{
									blockTypeGood = true;
								}
							}
						}
						if (blockTypeGood)
						{
							final AvailObject blockNode =
								BlockNodeDescriptor.newBlockNode(
									arguments,
									primitive,
									statements,
									returnType);
							attempt(afterReturnType, continuation, blockNode);
						}
						else
						{
							stateOutsideBlock.expected(
								"label's type to agree with block type");
						}
					}
					else
					{
						afterReturnType.expected(new Generator<String>()
						{
							@Override
							public String value ()
							{
								return "last statement's type \""
										+ lastStatementType.value.toString()
										+ "\" to agree with block's declared "
										+ "result type \""
										+ returnType.toString() + "\".";
							}
						});
					}
				}
			});
	}

	/**
	 * Parse an expression, without directly using the
	 * {@linkplain #fragmentCache}.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the expression.
	 */
	void parseExpressionUncachedThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		parseLeadingKeywordSendThen(start, new Con<AvailObject>(
			"Uncached leading keyword send")
		{
			@Override
			public void value (
				final ParserState afterSendNode,
				final AvailObject sendNode)
			{
				parseOptionalLeadingArgumentSendAfterThen(
					afterSendNode,
					sendNode,
					continuation);
			}
		});
		parseSimpleThen(start, new Con<AvailObject>(
			"Uncached simple expression")
		{
			@Override
			public void value (
				final ParserState afterSimple,
				final AvailObject simpleNode)
			{
				parseOptionalLeadingArgumentSendAfterThen(
					afterSimple,
					simpleNode,
					continuation);
			}
		});
		parseBlockThen(start, new Con<AvailObject>("Uncached block expression")
		{
			@Override
			public void value (
				final ParserState afterBlock,
				final AvailObject blockNode)
			{
				parseOptionalLeadingArgumentSendAfterThen(
					afterBlock,
					blockNode,
					continuation);
			}
		});
	}

	/**
	 * Parse an expression. Backtracking will find all valid interpretations.
	 * Note that a list expression requires at least two terms to form a list
	 * node. This method is a key optimization point, so the fragmentCache is
	 * used to keep track of parsing solutions at this point, simply replaying
	 * them on subsequent parses, as long as the variable declarations up to
	 * that point were identical.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param originalContinuation
	 *            What to do with the expression.
	 */
	void parseExpressionThen (
		final ParserState start,
		final Con<AvailObject> originalContinuation)
	{
		if (!fragmentCache.hasComputedForState(start))
		{
			final Mutable<Boolean> markerFired = new Mutable<Boolean>(false);
			attempt(new Continuation0()
			{
				@Override
				public void value ()
				{
					markerFired.value = true;
				}
			}, "Expression marker", start.position);
			fragmentCache.startComputingForState(start);
			final Con<AvailObject> justRecord = new Con<AvailObject>(
				"Expression")
			{
				@Override
				public void value (
					final ParserState afterExpr,
					final AvailObject expr)
				{
					fragmentCache.addSolution(
						start,
						new AvailCompilerCachedSolution(afterExpr, expr));
				}
			};
			attempt(new Continuation0()
			{
				@Override
				public void value ()
				{
					parseExpressionUncachedThen(start, justRecord);
				}
			}, "Capture expression for caching", start.position);
			// Force the previous attempts to all complete.
			while (!markerFired.value)
			{
				workStack.pop().value();
			}
		}
		// Deja vu! We were asked to parse an expression starting at this point
		// before. Luckily we had the foresight to record what those resulting
		// expressions were (as well as the scopeStack just after parsing each).
		// Replay just these solutions to the passed continuation. This has the
		// effect of eliminating each 'local' misparsing exactly once. I'm not
		// sure what happens to the order of the algorithm, but it might go from
		// exponential to small polynomial.
		final List<AvailCompilerCachedSolution> solutions =
			fragmentCache.solutionsAt(start);
		for (final AvailCompilerCachedSolution solution : solutions)
		{
			attempt(
				solution.endState(),
				originalContinuation,
				solution.parseNode());
		}
	}

	/**
	 * We've parsed part of a send. Try to finish the job.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param bundleTree
	 *            The bundle tree used to parse at this position.
	 * @param firstArgOrNull
	 *            Either null or an argument that must be consumed before any
	 *            keywords (or completion of a send).
	 * @param initialTokenPosition
	 *            The parse position where the send node started to be
	 *            processed. Does not count the position of the first argument
	 *            if there are no leading keywords.
	 * @param argsSoFar
	 *            The list of arguments parsed so far. I do not modify it. This
	 *            is a stack of expressions that the parsing instructions will
	 *            assemble into a list that correlates with the top-level
	 *            non-backquoted underscores and chevron groups in the message
	 *            name.
	 * @param innerArgsSoFar
	 *            The list of lists of innermost arguments. I do not modify it,
	 *            nor any of its contained lists. The positions in the outer
	 *            list correspond to non-backquoted underscores in the message
	 *            name.
	 * @param continuation
	 *            What to do with a fully parsed send node.
	 */
	void parseRestOfSendNode (
		final ParserState start,
		final AvailObject bundleTree,
		final AvailObject firstArgOrNull,
		final ParserState initialTokenPosition,
		final List<AvailObject> argsSoFar,
		final List<List<AvailObject>> innerArgsSoFar,
		final Con<AvailObject> continuation)
	{
		bundleTree.expand();
		final AvailObject complete = bundleTree.complete();
		final AvailObject incomplete = bundleTree.incomplete();
		final AvailObject special = bundleTree.specialActions();
		final boolean anyComplete = complete.mapSize() > 0;
		final boolean anyIncomplete = incomplete.mapSize() > 0;
		final boolean anySpecial = special.mapSize() > 0;
		assert anyComplete || anyIncomplete || anySpecial
		: "Expected a nonempty list of possible messages";
		if (anyComplete && firstArgOrNull == null
				&& start.position != initialTokenPosition.position)
		{
			// There are complete messages, we didn't leave a leading argument
			// stranded, and we made progress in the file (i.e., the message
			// send does not consist of exactly zero tokens, nor does it consist
			// of a solitary underscore).
			complete.mapDo(new Continuation2<AvailObject, AvailObject>()
			{
				@Override
				public void value (
					final AvailObject message,
					final AvailObject bundle)
				{
					if (interpreter.runtime().hasMethodsAt(message))
					{
						completedSendNode(
							start,
							argsSoFar,
							innerArgsSoFar,
							bundle,
							continuation);
					}
				}
			});
		}
		if (anyIncomplete && firstArgOrNull == null && !start.atEnd())
		{
			final AvailObject keywordToken = start.peekToken();
			if (keywordToken.tokenType() == KEYWORD
					|| keywordToken.tokenType() == OPERATOR)
			{
				final AvailObject keywordString = keywordToken.string();
				if (incomplete.hasKey(keywordString))
				{
					final AvailObject subtree = incomplete.mapAt(keywordString);
					attempt(new Continuation0()
					{
						@Override
						public void value ()
						{
							parseRestOfSendNode(
								start.afterToken(),
								subtree,
								null,
								initialTokenPosition,
								argsSoFar,
								innerArgsSoFar,
								continuation);
						}
					},
						"Continue send after keyword: "
								+ keywordString.asNativeString(),
						start.afterToken().position);
				}
				else
				{
					expectedKeywordsOf(start, incomplete);
				}
			}
			else
			{
				expectedKeywordsOf(start, incomplete);
			}
		}
		if (anySpecial)
		{
			special.mapDo(new Continuation2<AvailObject, AvailObject>()
			{
				@Override
				public void value (
					final AvailObject instructionObject,
					final AvailObject successorTrees)
				{
					attempt(new Continuation0()
					{
						@Override
						public void value ()
						{
							runParsingInstructionThen(
								start,
								instructionObject.extractInt(),
								firstArgOrNull,
								argsSoFar,
								innerArgsSoFar,
								initialTokenPosition,
								successorTrees,
								continuation);
						}
					},
						"Continue with instruction " + instructionObject,
						start.position);
				}
			});
		}
	}

	/**
	 * Execute one non-keyword parsing instruction, then run the continuation.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param instruction
	 *            The {@link MessageSplitter instruction} to execute.
	 * @param firstArgOrNull
	 *            Either the already-parsed first argument or null. If we're
	 *            looking for leading-argument message sends to wrap an
	 *            expression then this is not-null before the first argument
	 *            position is encountered, otherwise it's null and we should
	 *            reject attempts to start with an argument (before a keyword).
	 * @param argsSoFar
	 *            The message arguments that have been parsed so far.
	 * @param innerArgsSoFar
	 *            The list of lists of innermost arguments that have been parsed
	 *            so far. These correlate with the complete list of
	 *            non-backquoted underscores within the message name.
	 * @param initialTokenPosition
	 *            The position at which parsing of this message started. If it
	 *            was parsed as a leading argument send (i.e., firtArgOrNull
	 *            started out non-null) then the position is of the token
	 *            following the first argument.
	 * @param successorTrees
	 *            The {@link MessageBundleTreeDescriptor bundle trees} at which
	 *            to continue parsing.
	 * @param continuation
	 *            What to do with a complete {@link SendNodeDescriptor message
	 *            send}.
	 */
	void runParsingInstructionThen (
		final ParserState start,
		final int instruction,
		final AvailObject firstArgOrNull,
		final List<AvailObject> argsSoFar,
		final List<List<AvailObject>> innerArgsSoFar,
		final ParserState initialTokenPosition,
		final AvailObject successorTrees,
		final Con<AvailObject> continuation)
	{
		switch (instruction)
		{
			case 0:
			{
				// Parse an argument and continue.
				assert successorTrees.tupleSize() == 1;
				parseSendArgumentWithExplanationThen(
					start,
					" (an argument of some message)",
					firstArgOrNull,
					initialTokenPosition,
					new Con<AvailObject>("Argument of message send")
					{
						@Override
						public void value (
							final ParserState afterArg,
							final AvailObject newArg)
						{
							final List<AvailObject> newArgsSoFar =
								new ArrayList<AvailObject>(argsSoFar);
							newArgsSoFar.add(newArg);
							attempt(new Continuation0()
							{
								@Override
								public void value ()
								{
									parseRestOfSendNode(
										afterArg,
										successorTrees.tupleAt(1),
										null,
										initialTokenPosition,
										Collections.unmodifiableList(
											newArgsSoFar),
										innerArgsSoFar,
										continuation);
								}
							},
								"Continue send after argument",
								afterArg.position);
						}
					});
				break;
			}
			case 1:
			{
				// Push an empty list node and continue.
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar =
					new ArrayList<AvailObject>(argsSoFar);
				final AvailObject newTupleNode =
					TupleNodeDescriptor.newExpressions(TupleDescriptor.empty());
				newArgsSoFar.add(newTupleNode);
				attempt(new Continuation0()
				{
					@Override
					public void value ()
					{
						parseRestOfSendNode(
							start,
							successorTrees.tupleAt(1),
							firstArgOrNull,
							initialTokenPosition,
							Collections.unmodifiableList(newArgsSoFar),
							innerArgsSoFar,
							continuation);
					}
				},
					"Continue send after push empty",
					start.position);
				break;
			}
			case 2:
			{
				// Append the item that's the last thing to the
				// list that's the second last thing. Pop both and
				// push the new list (the original list must not
				// change), then continue.
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar =
					new ArrayList<AvailObject>(argsSoFar);
				final AvailObject value =
					newArgsSoFar.remove(newArgsSoFar.size() - 1);
				final AvailObject oldNode =
					newArgsSoFar.remove(newArgsSoFar.size() - 1);
				final AvailObject tupleNode = oldNode.copyWith(value);
				newArgsSoFar.add(tupleNode);
				attempt(new Continuation0()
				{
					@Override
					public void value ()
					{
						parseRestOfSendNode(
							start,
							successorTrees.tupleAt(1),
							firstArgOrNull,
							initialTokenPosition,
							Collections.unmodifiableList(newArgsSoFar),
							innerArgsSoFar,
							continuation);
					}
				},
					"Continue send after append",
					start.position);
				break;
			}
			case 3:
			{
				// Push current parse position.
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar =
					new ArrayList<AvailObject>(argsSoFar);
				final AvailObject marker =
					MarkerNodeDescriptor.mutable().create();
				marker.markerValue(IntegerDescriptor.fromInt(start.position));
				newArgsSoFar.add(marker);
				attempt(new Continuation0()
				{
					@Override
					public void value ()
					{
						parseRestOfSendNode(
							start,
							successorTrees.tupleAt(1),
							firstArgOrNull,
							initialTokenPosition,
							Collections.unmodifiableList(newArgsSoFar),
							innerArgsSoFar,
							continuation);
					}
				},
					"Continue send after push parse position",
					start.position);
				break;
			}
			case 4:
			{
				// Underpop saved parse position (from 2nd-to-top of stack).
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar = new ArrayList<AvailObject>(
					argsSoFar);
				final AvailObject marker =
					newArgsSoFar.remove(newArgsSoFar.size() - 2);
				assert marker.traversed().descriptor()
					instanceof MarkerNodeDescriptor;
				attempt(new Continuation0()
				{
					@Override
					public void value ()
					{
						parseRestOfSendNode(
							start,
							successorTrees.tupleAt(1),
							firstArgOrNull,
							initialTokenPosition,
							Collections.unmodifiableList(newArgsSoFar),
							innerArgsSoFar,
							continuation);
					}
				},
					"Continue send after underpop saved position",
					start.position);
				break;
			}
			case 5:
			{
				// Check parse progress (abort if parse position is still equal
				// to value at 2nd-to-top of stack). Also update the entry to
				// be the new parse position.
				assert successorTrees.tupleSize() == 1;
				final AvailObject marker = argsSoFar.get(argsSoFar.size() - 2);
				if (marker.markerValue().extractInt() == start.position)
				{
					return;
				}
				final List<AvailObject> newArgsSoFar =
					new ArrayList<AvailObject>(argsSoFar);
				final AvailObject newMarker =
					MarkerNodeDescriptor.mutable().create();
				newMarker.markerValue(
					IntegerDescriptor.fromInt(start.position));
				newArgsSoFar.set(newArgsSoFar.size() - 2, newMarker);
				attempt(new Continuation0()
				{
					@Override
					public void value ()
					{
						parseRestOfSendNode(
							start,
							successorTrees.tupleAt(1),
							firstArgOrNull,
							initialTokenPosition,
							Collections.unmodifiableList(newArgsSoFar),
							innerArgsSoFar,
							continuation);
					}
				},
					"Continue send after check parse progress",
					start.position);
				break;
			}
			case 6:
			case 7:
			{
				assert false : "Reserved parsing instruction";
				break;
			}
			default:
			{
				assert instruction >= 8;
				switch (instruction & 7)
				{
					case 0:
					case 1:
						for (final AvailObject successorTree : successorTrees)
						{
							attempt(new Continuation0()
							{
								@Override
								public void value ()
								{
									parseRestOfSendNode(
										start,
										successorTree,
										firstArgOrNull,
										initialTokenPosition,
										argsSoFar,
										innerArgsSoFar,
										continuation);
								}
							},
								"Continue send after branch or jump",
								start.position);
						}
						break;
					case 2:
						assert false
						: "parse-token instruction should not be dispatched";
						break;
					case 3:
						// An inner argument has just been parsed. Record it in
						// a copy of innerArgsSoFar at position
						// (instruction-3)/8 and continue. Actually subtract
						// one from that to make it a List index.
						assert successorTrees.tupleSize() == 1;
						final int position = (instruction - 3 >> 3) - 1;
						List<List<AvailObject>> newInnerArgs =
							new ArrayList<List<AvailObject>>(innerArgsSoFar);
						while (position >= newInnerArgs.size())
						{
							newInnerArgs.add(
								Collections.<AvailObject> emptyList());
						}
						final List<AvailObject> subList =
							new ArrayList<AvailObject>(
								newInnerArgs.get(position));
						subList.add(argsSoFar.get(argsSoFar.size() - 1));
						newInnerArgs.set(position, subList);
						final List<List<AvailObject>> finalNewInnerArgs =
							newInnerArgs;
						attempt(new Continuation0()
							{
								@Override
								public void value ()
								{
									parseRestOfSendNode(
										start,
										successorTrees.tupleAt(1),
										firstArgOrNull,
										initialTokenPosition,
										argsSoFar,
										finalNewInnerArgs,
										continuation);
								}
							},
							"Continue send after copyArgumentForCheck",
							start.position);
						break;
					default:
						assert false : "Reserved parsing instruction";
				}
			}
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
	 * @param start
	 *            The initial parsing state.
	 * @param argumentExpressions
	 *            The {@linkplain ParseNodeDescriptor parse nodes} that will be
	 *            arguments of the new send node.
	 * @param innerArgumentExpressions
	 *            The {@link List lists} of {@linkplain ParseNodeDescriptor
	 *            parse nodes} that will correspond to restriction positions,
	 *            which are at the non-backquoted underscores of the bundle's
	 *            message name.
	 * @param bundle
	 *            The {@link MessageBundleDescriptor message bundle} that
	 *            identifies the message to be sent.
	 * @param continuation
	 *            What to do with the resulting send node.
	 */
	void completedSendNode (
		final ParserState start,
		final List<AvailObject> argumentExpressions,
		final List<List<AvailObject>> innerArgumentExpressions,
		final AvailObject bundle,
		final Con<AvailObject> continuation)
	{
		final Mutable<Boolean> valid = new Mutable<Boolean>(true);
		AvailObject message = bundle.message();
		final AvailObject impSet = interpreter.runtime().methodsAt(message);
		AvailObject implementationsTuple = impSet.implementationsTuple();
		assert implementationsTuple.tupleSize() > 0;

		if (implementationsTuple.tupleAt(1).isMacro())
		{
			// Macro definitions and non-macro definitions are not allowed to
			// mix within an implementation set.
			List<AvailObject> argumentNodeTypes = new ArrayList<AvailObject>(
				argumentExpressions.size());
			for (AvailObject argExpr : argumentExpressions)
			{
				argumentNodeTypes.add(argExpr.type());
			}
			impSet.validateArgumentTypesInterpreterIfFail(
				argumentNodeTypes,
				interpreter,
				new Continuation1<Generator<String>>()
				{
					@Override
					public void value (final Generator<String> arg)
					{
						start.expected(
							"parse node types to agree with macro types");
						valid.value = false;
					}
				});
			if (!valid.value)
			{
				return;
			}

			// Check for excluded messages in arguments. Don't just forbid
			// conflicting send nodes, but also forbid macro substitution nodes,
			// depending on their macroName.
			checkRestrictionsIfFail(
				bundle,
				innerArgumentExpressions,
				new Continuation1<Generator<String>>()
				{
					@Override
					public void value (final Generator<String> errorGenerator)
					{
						valid.value = false;
						start.expected(errorGenerator);
					}
				});
			if (!valid.value)
			{
				return;
			}

			// Construct code to invoke the method, since it might be a
			// primitive and we can't invoke that directly as the outermost
			// closure.
			final L1InstructionWriter writer = new L1InstructionWriter();
			for (final AvailObject arg : argumentExpressions)
			{
				writer.write(new L1Instruction(
					L1Operation.L1_doPushLiteral,
					writer.addLiteral(arg)));
			}
			writer.write(new L1Instruction(
				L1Operation.L1_doCall,
				writer.addLiteral(impSet),
				writer.addLiteral(PARSE_NODE.o())));
			writer.argumentTypes();
			writer.primitiveNumber(0);
			writer.returnType(PARSE_NODE.o());
			final AvailObject newClosure = ClosureDescriptor.create(
				writer.compiledCode(),
				TupleDescriptor.empty());
			newClosure.makeImmutable();
			try
			{
				final AvailObject replacement = interpreter.runClosureArguments(
					newClosure,
					Collections.<AvailObject> emptyList());
				if (replacement.isInstanceOfSubtypeOf(PARSE_NODE.o()))
				{
					final AvailObject substitution =
						MacroSubstitutionNodeDescriptor.mutable().create();
					substitution.macroName(message);
					substitution.outputParseNode(replacement);
					attempt(start, continuation, replacement);
				}
				else
				{
					start.expected(
						"macro body ("
						+ impSet.name().name()
						+ ") to produce a parse node");
				}
			}
			catch (AvailRejectedParseException e)
			{
				start.expected(e.rejectionString().asNativeString());
			}
			return;
		}
		// It invokes a method (not a macro).
		for (AvailObject arg : argumentExpressions)
		{
			if (arg.expressionType().equals(TERMINATES.o()))
			{
				start.expected("argument to have type other than terminates");
				return;
			}
			if (arg.expressionType().equals(VOID_TYPE.o()))
			{
				start.expected("argument to have type other than void");
				return;
			}
		}
		final AvailObject returnType =
			interpreter.validateSendArgumentExpressions(
				message,
				argumentExpressions,
				new Continuation1<Generator<String>>()
				{
					@Override
					public void value (
						final Generator<String> errorGenerator)
					{
						valid.value = false;
						start.expected(errorGenerator);
					}
				});
		if (valid.value)
		{
			checkRestrictionsIfFail(
				bundle,
				innerArgumentExpressions,
				new Continuation1<Generator<String>>()
				{
					@Override
					public void value (final Generator<String> errorGenerator)
					{
						valid.value = false;
						start.expected(errorGenerator);
					}
				});
		}
		if (valid.value)
		{
			final List<AvailObject> argTypes = new ArrayList<AvailObject>(
				argumentExpressions.size());
			for (final AvailObject argumentExpression : argumentExpressions)
			{
				argTypes.add(argumentExpression.expressionType());
			}
			final String errorMessage = interpreter.validateRequiresClauses(
				bundle.message(),
				argTypes);
			if (errorMessage != null)
			{
				valid.value = false;
				start.expected(errorMessage);
			}
		}
		if (valid.value)
		{
			final AvailObject sendNode = SendNodeDescriptor.mutable().create();
			sendNode.implementationSet(impSet);
			sendNode.arguments(TupleDescriptor.fromList(argumentExpressions));
			sendNode.returnType(returnType);
			attempt(start, continuation, sendNode);
		}
	}

	/**
	 * Make sure none of my arguments are message sends that have been
	 * disallowed in that position by a negative precedence declaration.
	 *
	 * @param bundle
	 *            The bundle for which a send node was just parsed. It contains
	 *            information about any negative precedence restrictions.
	 * @param innerArguments
	 *            The inner argument expressions for the send that was just
	 *            parsed. These correspond to all non-backquoted underscores
	 *            anywhere in the message name.
	 * @param ifFail
	 *            What to do when a negative precedence rule inhibits a parse.
	 */
	void checkRestrictionsIfFail (
		final AvailObject bundle,
		final List<List<AvailObject>> innerArguments,
		final Continuation1<Generator<String>> ifFail)
	{
		for (int i = 1; i <= innerArguments.size(); i++)
		{
			List<AvailObject> argumentOccurrences = innerArguments.get(i - 1);
			for (AvailObject argument : argumentOccurrences)
			{
				final AvailObject argumentSendName =
					argument.apparentSendName();
				if (!argumentSendName.equalsVoid())
				{
					final AvailObject restrictions =
						bundle.restrictions().tupleAt(i);
					if (restrictions.hasElement(argumentSendName))
					{
						final int index = i;
						ifFail.value(new Generator<String>()
						{
							@Override
							public String value ()
							{
								return "different nesting for argument #"
									+ Integer.toString(index) + " in "
									+ bundle.message().name().toString();
							}
						});
					}
				}
			}
		}
	}

	/**
	 * Report that the parser was expecting one of several keywords. The
	 * keywords are keys of the {@link MapDescriptor map} argument
	 * {@code incomplete}.
	 *
	 * @param where
	 *            Where the keywords were expected.
	 * @param incomplete
	 *            A map of partially parsed keywords, where the keys are the
	 *            strings that were expected at this position.
	 */
	private void expectedKeywordsOf (
		final ParserState where,
		final AvailObject incomplete)
	{
		where.expected(new Generator<String>()
		{
			@Override
			public String value ()
			{
				final StringBuilder builder = new StringBuilder(200);
				builder.append("one of the following internal keywords:");
				final List<String> sorted =
					new ArrayList<String>(incomplete.mapSize());
				incomplete.mapDo(new Continuation2<AvailObject, AvailObject>()
				{
					@Override
					public void value (
						final AvailObject key,
						final AvailObject value)
					{
						sorted.add(key.asNativeString());
					}
				});
				Collections.sort(sorted);
				boolean startOfLine = true;
				builder.append("\n\t");
				final int leftColumn = 4 + 4; // ">>> " and a tab.
				int column = leftColumn;
				for (final String s : sorted)
				{
					if (!startOfLine)
					{
						builder.append("  ");
						column += 2;
					}
					startOfLine = false;
					final int lengthBefore = builder.length();
					builder.append(s);
					column += builder.length() - lengthBefore;
					if (column + 2 + s.length() > 80)
					{
						builder.append("\n\t");
						column = leftColumn;
						startOfLine = true;
					}
				}
				return builder.toString();
			}
		});
	}

	/**
	 * Parse a send node whose leading argument has already been parsed.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param leadingArgument
	 *            The argument that was already parsed.
	 * @param continuation
	 *            What to do after parsing a send node.
	 */
	void parseLeadingArgumentSendAfterThen (
		final ParserState start,
		final AvailObject leadingArgument,
		final Con<AvailObject> continuation)
	{
		parseRestOfSendNode(
			start,
			interpreter.rootBundleTree(),
			leadingArgument,
			start,
			Collections.<AvailObject> emptyList(),
			Collections.<List<AvailObject>> emptyList(),
			continuation);
	}

	/**
	 * Parse a send node. To prevent infinite left-recursion and false
	 * ambiguity, we only allow a send with a leading keyword to be parsed from
	 * here, since leading underscore sends are dealt with iteratively
	 * afterward.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do after parsing a complete send node.
	 */
	private void parseLeadingKeywordSendThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		parseRestOfSendNode(
			start,
			interpreter.rootBundleTree(),
			null,
			start,
			Collections.<AvailObject> emptyList(),
			Collections.<List<AvailObject>> emptyList(),
			continuation);
	}

	/**
	 * Parse an expression that isn't a list. Backtracking will find all valid
	 * interpretations.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param node
	 *            An expression that acts as the first argument for a potential
	 *            leading-argument message send, or possibly a chain of them.
	 * @param continuation
	 *            What to do with either the passed node, or the node wrapped in
	 *            suitable leading-argument message sends.
	 */
	void parseOptionalLeadingArgumentSendAfterThen (
		final ParserState start,
		final AvailObject node,
		final Con<AvailObject> continuation)
	{
		// It's optional, so try it with no wrapping.
		attempt(start, continuation, node);

		// Don't wrap it if its type is void.
		if (node.expressionType().equals(VOID_TYPE.o()))
		{
			return;
		}

		// Try to wrap it in a leading-argument message send.
		parseOptionalSuperCastAfterErrorSuffixThen(
			start,
			node,
			" in case it's the first argument of a non-keyword-leading message",
			new Con<AvailObject>("Optional supercast")
			{
				@Override
				public void value (
					final ParserState afterCast,
					final AvailObject cast)
				{
					parseLeadingArgumentSendAfterThen(
						afterCast,
						cast,
						new Con<AvailObject>(
							"Leading argument send after optional supercast")
						{
							@Override
							public void value (
								final ParserState afterSend,
								final AvailObject leadingSend)
							{
								parseOptionalLeadingArgumentSendAfterThen(
									afterSend,
									leadingSend,
									continuation);
							}
						});
				}
			});
	}

	/**
	 * Parse the optional primitive declaration at the start of a block. Since
	 * it's optional, try the continuation with a zero argument without having
	 * parsed anything, then try to parse "Primitive N;" for some supported
	 * integer N.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param argCount
	 *            The number of arguments accepted by the block being parsed.
	 * @param continuation
	 *            What to do with the parsed primitive number.
	 */
	void parseOptionalPrimitiveForArgCountThen (
		final ParserState start,
		final int argCount,
		final Con<Short> continuation)
	{
		// Try it first without looking for the primitive declaration.
		attempt(start, continuation, (short) 0);

		// Now look for the declaration.
		if (!start.peekToken(
			KEYWORD,
			"Primitive",
			"optional primitive declaration"))
		{
			return;
		}
		final ParserState afterPrimitiveKeyword = start.afterToken();
		final AvailObject token = afterPrimitiveKeyword.peekToken();
		if (token.tokenType() != LITERAL
				|| !token.literal().isInstanceOfSubtypeOf(
					IntegerRangeTypeDescriptor.positiveShorts()))
		{
			afterPrimitiveKeyword.expected(new Generator<String>()
			{
				@Override
				public String value ()
				{
					return "A positive short "
							+ IntegerRangeTypeDescriptor.positiveShorts()
							+ " after the Primitive keyword";
				}
			});
			return;
		}
		final short primitive = (short) token.literal().extractInt();
		if (!interpreter.supportsPrimitive(primitive))
		{
			afterPrimitiveKeyword.expected(
				"a supported primitive number, not #"
				+ Short.toString(primitive));
			return;
		}

		if (!interpreter.primitiveAcceptsThisManyArguments(primitive, argCount))
		{
			final Primitive prim = Primitive.byPrimitiveNumber(primitive);
			afterPrimitiveKeyword.expected(new Generator<String>()
			{
				@Override
				public String value ()
				{
					return "Primitive #" + Short.toString(primitive) + " ("
							+ prim.name() + ") to be passed "
							+ Integer.toString(prim.argCount())
							+ " arguments, not " + Integer.toString(argCount);
				}
			});
		}
		final ParserState afterPrimitiveNumber =
			afterPrimitiveKeyword.afterToken();
		if (!afterPrimitiveNumber.peekToken(
			END_OF_STATEMENT,
			";",
			"; after Primitive N declaration"))
		{
			return;
		}
		attempt(afterPrimitiveNumber.afterToken(), continuation, primitive);
	}

	/**
	 * An expression was parsed. Now parse the optional supercast clause that
	 * may follow it to make a supercast node.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param expr
	 *            The expression after which to look for a supercast clause.
	 * @param errorSuffix
	 *            A suffix for messages describing what was expected.
	 * @param continuation
	 *            What to do with the supercast node, if present, or just the
	 *            passed expression if not.
	 */
	void parseOptionalSuperCastAfterErrorSuffixThen (
		final ParserState start,
		final AvailObject expr,
		final String errorSuffix,
		final Con<? super AvailObject> continuation)
	{
		// Optional, so try it without a super cast.
		attempt(start, continuation, expr);

		if (!start.peekToken(OPERATOR, ":"))
		{
			return;
		}
		final ParserState afterColon = start.afterToken();
		if (!afterColon.peekToken(OPERATOR, ":"))
		{
			start.expected(new Generator<String>()
			{
				@Override
				public String value ()
				{
					return ":: to supercast an expression" + errorSuffix;
				}
			});
			return;
		}
		final ParserState afterSecondColon = afterColon.afterToken();
		attempt(new Continuation0()
		{
			@Override
			public void value ()
			{
				parseAndEvaluateExpressionYieldingInstanceOfThen(
					afterSecondColon,
					TYPE.o(),
					new Con<AvailObject>("Type expression of supercast")
					{
						@Override
						public void value (
							final ParserState afterType,
							final AvailObject type)
						{
							if (expr.expressionType().isSubtypeOf(type))
							{
								final AvailObject cast =
									SuperCastNodeDescriptor.mutable().create();
								cast.expression(expr);
								cast.type(type);
								attempt(afterType, continuation, cast);
							}
							else
							{
								afterType.expected(
									"supercast type to be supertype "
									+ "of expression's type.");
							}
						}
					});
			}
		},
			"Type expression in supercast",
			afterSecondColon.position);
	}

	/**
	 * Parse an argument to a message send. Backtracking will find all valid
	 * interpretations.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param explanation
	 *            A {@link String} indicating why it's parsing an argument.
	 * @param firstArgOrNull
	 *            Either a parse node to use as the argument, or null if we
	 *            should parse one now.
	 * @param initialTokenPosition
	 *            The position at which we started parsing the message send.
	 *            Does not include the first argument if there were no leading
	 *            keywords.
	 * @param continuation
	 *            What to do with the argument.
	 */
	void parseSendArgumentWithExplanationThen (
		final ParserState start,
		final String explanation,
		final AvailObject firstArgOrNull,
		final ParserState initialTokenPosition,
		final Con<AvailObject> continuation)
	{
		if (firstArgOrNull == null)
		{
			// There was no leading argument. If we haven't parsed any keywords
			// then don't allow this argument parse to happen, since we must be
			// trying to parse a leading-keyword message send.
			if (start.position != initialTokenPosition.position)
			{
				parseExpressionThen(start, new Con<AvailObject>(
					"Argument expression (irrespective of supercast)")
				{
					@Override
					public void value (
						final ParserState afterArgument,
						final AvailObject argument)
					{
						parseOptionalSuperCastAfterErrorSuffixThen(
							afterArgument,
							argument,
							explanation,
							continuation);
					}
				});
			}
		}
		else
		{
			// We're parsing a message send with a leading argument. There
			// should have been no way to parse any keywords or other arguments
			// yet, so make sure the position hasn't budged since we started.
			// Then use the provided first argument.
			parseOptionalSuperCastAfterErrorSuffixThen(
				initialTokenPosition,
				firstArgOrNull,
				explanation,
				continuation);
		}
	}

	/**
	 * Parse a variable, reference, or literal, then invoke the continuation.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the simple parse node.
	 */
	private void parseSimpleThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		// Try a variable use.
		parseVariableUseWithExplanationThen(start, "", continuation);

		// Try a literal.
		if (start.peekToken().tokenType() == LITERAL)
		{
			final AvailObject literalNode =
				LiteralNodeDescriptor.fromToken(start.peekToken());
			attempt(start.afterToken(), continuation, literalNode);
		}

		// Try a reference: &var.
		if (start.peekToken(OPERATOR, "&"))
		{
			final ParserState afterAmpersand = start.afterToken();
			parseVariableUseWithExplanationThen(
				afterAmpersand,
				"in reference expression",
				new Con<AvailObject>("Variable for reference")
				{
					@Override
					public void value (
						final ParserState afterVar,
						final AvailObject var)
					{
						final AvailObject declaration = var.declaration();
						String suffix = null;
						if (declaration == null)
						{
							suffix = " to have been declared";
						}
						else
						{
							switch (declaration.declarationKind())
							{
								case LOCAL_CONSTANT:
								case MODULE_CONSTANT:
									suffix = " not to be a constant";
									break;
								case ARGUMENT:
									suffix = " not to be an argument";
									break;
								case LABEL:
									suffix = " not to be a label";
									break;
								case MODULE_VARIABLE:
								case LOCAL_VARIABLE:
									attempt(
										afterVar,
										continuation,
										ReferenceNodeDescriptor.fromUse(var));
							}
							if (suffix != null)
							{
								afterAmpersand.expected("reference variable "
										+ suffix);
							}
						}
					}
				});
		}
		start.expected("simple expression");
	}

	/**
	 * Parse zero or more statements from the tokenStream. Parse as many
	 * statements as possible before invoking the continuation.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the list of statements.
	 */
	void parseStatementsThen (
		final ParserState start,
		final Con<List<AvailObject>> continuation)
	{
		parseMoreStatementsThen(
			start,
			Collections.<AvailObject> emptyList(),
			continuation);
	}

	/**
	 * Try the current list of statements but also try to parse more.
	 *
	 * @param start
	 *            Where to parse.
	 * @param statements
	 *            The preceding list of statements.
	 * @param continuation
	 *            What to do with the resulting list of statements.
	 */
	void parseMoreStatementsThen (
		final ParserState start,
		final List<AvailObject> statements,
		final Con<List<AvailObject>> continuation)
	{
		// Try it with the current list of statements.
		attempt(start, continuation, statements);

		// See if more statements would be legal.
		if (statements.size() > 0)
		{
			final AvailObject lastStatement =
				statements.get(statements.size() - 1);
			if (lastStatement.expressionType().equals(TERMINATES.o()))
			{
				start.expected(
					"end of statements, since this one always terminates");
				return;
			}
			if (!lastStatement.expressionType().equals(VOID_TYPE.o())
				&& !lastStatement.isInstanceOfSubtypeOf(ASSIGNMENT_NODE.o()))
			{
				start.expected(new Generator<String>()
				{
					@Override
					public String value ()
					{
						return "non-last statement \""
								+ lastStatement.toString()
								+ "\" to have type void, not \""
								+ lastStatement.expressionType().toString()
								+ "\".";
					}
				});
			}
		}
		start.expected("more statements");

		// Try for more statements.
		parseStatementAsOutermostCanBeLabelThen(
			start,
			false,
			statements.isEmpty(),
			new Con<AvailObject>("Another statement")
			{
				@Override
				public void value (
					final ParserState afterStatement,
					final AvailObject newStatement)
				{
					final List<AvailObject> newStatements =
						new ArrayList<AvailObject>(statements);
					newStatements.add(newStatement);
					parseMoreStatementsThen(
						afterStatement,
						newStatements,
						continuation);
				}
			});
	}

	/**
	 * Parse the use of a variable.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param explanation
	 *            The string explaining why we were parsing a use of a variable.
	 * @param continuation
	 *            What to do after parsing the variable use.
	 */
	private void parseVariableUseWithExplanationThen (
		final ParserState start,
		final String explanation,
		final Con<AvailObject> continuation)
	{
		final AvailObject token = start.peekToken();
		if (token.tokenType() != KEYWORD)
		{
			return;
		}
		final ParserState afterVar = start.afterToken();
		// First check if it's in a block scope...
		final AvailObject localDecl = lookupDeclaration(start, token.string());
		if (localDecl != null)
		{
			final AvailObject varUse = VariableUseNodeDescriptor.newUse(
				token,
				localDecl);
			attempt(afterVar, continuation, varUse);
			// Variables in inner scopes HIDE module variables.
			return;
		}
		// Not in a block scope. See if it's a module variable or module
		// constant...
		final AvailObject varName = token.string();
		if (module.variableBindings().hasKey(varName))
		{
			final AvailObject variableObject = module.variableBindings().mapAt(
				varName);
			final AvailObject moduleVarDecl =
				DeclarationNodeDescriptor.newModuleVariable(
					token,
					variableObject);
			final AvailObject varUse =
				VariableUseNodeDescriptor.mutable().create();
			varUse.token(token);
			varUse.declaration(moduleVarDecl);
			attempt(afterVar, continuation, varUse);
			return;
		}
		if (module.constantBindings().hasKey(varName))
		{
			final AvailObject valueObject =
				module.constantBindings().mapAt(varName);
			final AvailObject moduleConstDecl =
				DeclarationNodeDescriptor.newModuleConstant(token, valueObject);
			final AvailObject varUse =
				VariableUseNodeDescriptor.mutable().create();
			varUse.token(token);
			varUse.declaration(moduleConstDecl);
			attempt(afterVar, continuation, varUse);
			return;
		}
		start.expected(new Generator<String>()
		{
			@Override
			public String value ()
			{
				return "variable " + token.string()
					+ " to have been declared before use " + explanation;
			}
		});
	}

	/**
	 * A statement was parsed correctly in two different ways. There may be more
	 * ways, but we stop after two as it's already an error. Report the error.
	 *
	 * @param where
	 *            Where the expressions were parsed from.
	 * @param interpretation1
	 *            The first interpretation as a {@link ParseNodeDescriptor parse
	 *            node}.
	 * @param interpretation2
	 *            The second interpretation as a {@link ParseNodeDescriptor
	 *            parse node}.
	 */
	private void ambiguousInterpretationsAnd (
		final ParserState where,
		final AvailObject interpretation1,
		final AvailObject interpretation2)
	{
		where.expected(new Generator<String>()
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
	 * Clear the fragment cache.
	 */
	private void privateClearFrags ()
	{
		fragmentCache.clear();
	}

	/**
	 * Answer a {@linkplain Generator} that will produce the given string.
	 *
	 * @param string
	 *            The exact string to generate.
	 * @return A generator that produces the string that was provided.
	 */
	Generator<String> generate (final String string)
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
	 * Look up a local declaration that has the given name, or null if not
	 * found.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param name
	 *            The name of the variable declaration for which to look.
	 * @return The declaration or null.
	 */
	private AvailObject lookupDeclaration (
		final ParserState start,
		final AvailObject name)
	{
		AvailCompilerScopeStack scope = start.scopeStack;
		while (scope.name() != null)
		{
			if (scope.name().equals(name))
			{
				return scope.declaration();
			}
			scope = scope.next();
		}
		return null;
	}

	/**
	 * Start definition of a {@linkplain ModuleDescriptor module}. The entire
	 * definition can be rolled back because the {@linkplain Interpreter
	 * interpreter}'s context module will contain all methods and precedence
	 * rules defined between the transaction start and the rollback (or commit).
	 * Committing simply clears this information.
	 *
	 * @param moduleName
	 *            The name of the {@linkplain ModuleDescriptor module}.
	 */
	private void startModuleTransaction (final AvailObject moduleName)
	{
		assert module == null;
		module = ModuleDescriptor.newModule(moduleName);
		interpreter.setModule(module);
	}

	/**
	 * Rollback the {@linkplain ModuleDescriptor module} that was defined since
	 * the most recent {@link #startModuleTransaction(AvailObject)}.
	 */
	private void rollbackModuleTransaction ()
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
	private void commitModuleTransaction ()
	{
		assert module != null;
		interpreter.runtime().addModule(module);
		module.cleanUpAfterCompile();
		module = null;
		interpreter.setModule(null);
	}
}
