/**
 * com.avail.compiler/AbstractAvailCompiler.java
 *
 * Copyright (c) 2011, Mark van Gulik. All rights reserved.
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

import static com.avail.compiler.AbstractAvailCompiler.ExpectedToken.*;
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
	 * The {@linkplain ModuleDescriptor modules} extended by the module
	 * undergoing compilation. Each element is a {@linkplain TupleDescriptor
	 * 2-tuple} whose first element is a module {@linkplain ByteStringDescriptor
	 * name} and whose second element is the {@linkplain SetDescriptor set} of
	 * {@linkplain MethodSignatureDescriptor method} names to import (and
	 * re-export).
	 */
	@InnerAccess List<AvailObject> extendedModules;

	/**
	 * The {@linkplain ModuleDescriptor modules} used by the module undergoing
	 * compilation. Each element is a {@linkplain TupleDescriptor 2-tuple} whose
	 * first element is a module {@linkplain ByteStringDescriptor name} and
	 * whose second element is the {@linkplain SetDescriptor set} of {@linkplain
	 * MethodSignatureDescriptor method} names to import.
	 */
	@InnerAccess List<AvailObject> usedModules;

	/**
	 * The {@linkplain CyclicTypeDescriptor names} defined and exported by the
	 * {@linkplain ModuleDescriptor module} undergoing compilation.
	 */
	@InnerAccess List<AvailObject> exportedNames;

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

	enum ExpectedToken
	{
		SYSTEM("System", KEYWORD),
		MODULE("Module", KEYWORD),
		PRAGMA("Pragma", KEYWORD),
		EXTENDS("Extends", KEYWORD),
		USES("Uses", KEYWORD),
		NAMES("Names", KEYWORD),
		BODY("Body", KEYWORD),
		PRIMITIVE("Primitive", KEYWORD),

		DOLLAR_SIGN("$", OPERATOR),
		AMPERSAND("&", OPERATOR),
		COMMA(",", OPERATOR),
		COLON(":", OPERATOR),
		EQUALS("=", OPERATOR),
		OPEN_SQUARE("[", OPERATOR),
		CLOSE_SQUARE("]", OPERATOR),
		VERTICAL_BAR("|", OPERATOR),

		OPEN_PARENTHESIS("(", OPERATOR),
		CLOSE_PARENTHESIS(")", OPERATOR),

		SEMICOLON(";", END_OF_STATEMENT);


		private final AvailObject string;

		private final TokenType tokenType;

		AvailObject string ()
		{
			return string;
		}

		TokenType tokenType ()
		{
			return tokenType;
		}

		ExpectedToken (
			@NotNull final String string,
			@NotNull final TokenType tokenType)
		{
			this.string = ByteStringDescriptor.from(string);
			this.tokenType = tokenType;
		}
	}

	/**
	 * Construct a suitable {@linkplain AbstractAvailCompiler compiler} to
	 * parse the specified {@linkplain ModuleName module name}, using the given
	 * {@linkplain L2Interpreter interpreter}.
	 */
	static @NotNull AbstractAvailCompiler create (
		final @NotNull ModuleName qualifiedName,
		final @NotNull L2Interpreter interpreter,
		final boolean stopAfterNamesToken)
	{
		AvailRuntime runtime = interpreter.runtime();
		final ResolvedModuleName resolvedName =
			runtime.moduleNameResolver().resolve(qualifiedName);
		final String source = extractSource(qualifiedName, resolvedName);
		final List<AvailObject> tokens = tokenize(
			source,
			stopAfterNamesToken);
		AbstractAvailCompiler compiler;
		if (!tokens.isEmpty()
			&& tokens.get(0).string().equals(SYSTEM.string()))
		{
			compiler = new AvailSystemCompiler(interpreter, source, tokens);
		}
		else
		{
			compiler = new AvailCompiler(interpreter, source, tokens);
		}
		return compiler;
	}

	/**
	 * Construct a new {@link AbstractAvailCompiler} which will use the given
	 * {@link Interpreter} to evaluate expressions.
	 *
	 * @param interpreter
	 *            The interpreter to be used for evaluating expressions.
	 * @param source
	 *            The source code {@linkplain ByteStringDescriptor string}.
	 * @param tokens
	 *            The list of {@linkplain TokenDescriptor tokens}.
	 */
	public AbstractAvailCompiler (
		final L2Interpreter interpreter,
		final String source,
		final List<AvailObject> tokens)
	{
		this.interpreter = interpreter;
		this.source = source;
		this.tokens = tokens;
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
	abstract class Con<AnswerType> implements
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
	void tryIfUnambiguousThen (
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
		 * @param expectedToken
		 *            The {@link ExpectedToken expected token} to look for.
		 * @return Whether the specified token was found.
		 */
		boolean peekToken (
			final ExpectedToken expectedToken)
		{
			if (atEnd())
			{
				return false;
			}
			final AvailObject token = peekToken();
			return token.tokenType() == expectedToken.tokenType()
				&& token.string().equals(expectedToken.string());
		}

		/**
		 * Answer whether the current token has the specified type and content.
		 *
		 * @param expectedToken
		 *            The {@link ExpectedToken expected token} to look for.
		 * @param expected
		 *            A generator of a string message to record if the specified
		 *            token is not present.
		 * @return Whether the specified token is present.
		 */
		boolean peekToken (
			final @NotNull ExpectedToken expectedToken,
			final @NotNull Generator<String> expected)
		{
			final AvailObject token = peekToken();
			final boolean found = token.tokenType() == expectedToken.tokenType()
					&& token.string().equals(expectedToken.string());
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
		 *            The {@link ExpectedToken expected token} to look for.
		 * @param expected
		 *            A message to record if the specified token is not present.
		 * @return Whether the specified token is present.
		 */
		boolean peekToken (
			final @NotNull ExpectedToken expectedToken,
			final String expected)
		{
			return peekToken(expectedToken, generate(expected));
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
	 *            The initially empty list of strings to populate.
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
	 * {@linkplain ByteStringDescriptor name} and second element is the
	 * collection of {@linkplain MethodSignatureDescriptor method} names to
	 * import.
	 * </p>
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param imports
	 *            The initially empty list of imports to populate.
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
			if (state.peekToken(OPEN_PARENTHESIS))
			{
				state = state.afterToken();
				final List<AvailObject> names = new ArrayList<AvailObject>();
				state = parseStringLiterals(state, names);
				if (!state.peekToken(
					CLOSE_PARENTHESIS,
					"a close parenthesis following imported method names"))
				{
					return null;
				}
				state = state.afterToken();
				imports.add(TupleDescriptor.from(
					moduleName, TupleDescriptor.fromList(names).asSet()));
			}
			else
			{
				imports.add(TupleDescriptor.from(moduleName));
			}
		}
		while (state.peekToken(COMMA) && (state = state.afterToken()) != null);

		return state;
	}

	/**
	 * Read the source string for the {@linkplain ModuleDescriptor module}
	 * specified by the fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param qualifiedName
	 *            A fully-qualified {@linkplain ModuleName module name}.
	 * @param resolvedName
	 *            The {@linkplain ResolvedModuleName resolved name} of the
	 *            module.
	 * @return The module's {@linkplain String source code}.
	 * @throws AvailCompilerException
	 *             If source extraction failed for any reason.
	 */
	static @NotNull String extractSource (
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
	 *            The {@linkplain String string} containing the module's source
	 *            code.
	 * @param stopAfterNamesToken
	 *            Stop scanning after encountering the <em>Names</em> token?
	 * @return The {@linkplain ResolvedModuleName resolved module name}.
	 * @throws AvailCompilerException
	 *             If tokenization failed for any reason.
	 */
	static @NotNull List<AvailObject> tokenize (
		final @NotNull String source,
		final boolean stopAfterNamesToken) throws AvailCompilerException
	{
		return new AvailScanner().scanString(source, stopAfterNamesToken);
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
	static AvailObject treeMapWithParent (
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
		objectCopy.childrenMap(
			new Transformer1<AvailObject, AvailObject>()
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
	static void treeDoWithParent (
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
	void reportError (
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
		attempt(
			new Continuation0()
			{
				@Override
				public void value ()
				{
					continuation.value(here, argument);
				}
			},
			continuation.description, here.position);
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
	 *            Where to start parsing.
	 * @param name
	 *            The name of the variable declaration for which to look.
	 * @return The declaration or null.
	 */
	AvailObject lookupDeclaration (
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
	void startModuleTransaction (final AvailObject moduleName)
	{
		assert module == null;
		module = ModuleDescriptor.newModule(moduleName);
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
	 *            The expression to compile and evaluate as a top-level
	 *            statement in the module.
	 */
	void evaluateModuleStatement (final AvailObject expr)
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
	 *            {@linkplain AbstractAvailCompiler compilation}, the position
	 *            of the ongoing parse (in bytes), and the size of the module
	 *            (in bytes).
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
		final ResolvedModuleName resolvedName =
			interpreter.runtime().moduleNameResolver().resolve(qualifiedName);
		source = extractSource(qualifiedName, resolvedName);
		tokens = tokenize(source, false);
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
		for (final AvailObject modImport : extendedModules)
		{
			assert modImport.isTuple();
			assert modImport.tupleSize() > 0 && modImport.tupleSize() < 3;

			final ModuleName ref = resolver.canonicalNameFor(
				qualifiedName.asSibling(
					modImport.tupleAt(1).asNativeString()));
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
			final AvailObject modNames = modImport.tupleSize() == 2
				? modImport.tupleAt(2)
				: mod.names().keysAsSet();
			for (final AvailObject strName : modNames)
			{
				if (!mod.names().hasKey(strName))
				{
					state.value.expected(
						"module \"" + ref.qualifiedName()
						+ "\" to export \"" + strName + "\"");
					reportError(state.value, qualifiedName);
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
			assert modImport.tupleSize() > 0 && modImport.tupleSize() < 3;

			final ModuleName ref = resolver.canonicalNameFor(
				qualifiedName.asSibling(
					modImport.tupleAt(1).asNativeString()));
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
			final AvailObject modNames = modImport.tupleSize() == 2
				? modImport.tupleAt(2)
				: mod.names().keysAsSet();
			for (final AvailObject strName : modNames)
			{
				if (!mod.names().hasKey(strName))
				{
					state.value.expected(
						"module \"" + ref.qualifiedName()
						+ "\" to export \"" + strName + "\"");
					reportError(state.value, qualifiedName);
					assert false;
				}
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
	 *            The expected module name.
	 * @throws AvailCompilerException
	 *             If compilation fails.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	void parseModuleHeader (
		final @NotNull ModuleName qualifiedName)
	throws AvailCompilerException
	{
		progressBlock = null;
		greatestGuess = -1;
		greatExpectations.clear();
		final ResolvedModuleName resolvedName =
			interpreter.runtime().moduleNameResolver().resolve(qualifiedName);
		if (parseHeader(resolvedName, true) == null)
		{
			reportError(
				new ParserState(0, new AvailCompilerScopeStack(null, null)),
				resolvedName);
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
		ParserState state = new ParserState(
			0,
			new AvailCompilerScopeStack(null, null));

		if (isSystemCompiler())
		{
			if (!state.peekToken(SYSTEM, "initial System keyword"))
			{
				return null;
			}
			state = state.afterToken();
		}
		if (!state.peekToken(MODULE, "initial Module keyword"))
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

		if (state.peekToken(PRAGMA))
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
				}
			}
		}

		if (!state.peekToken(EXTENDS, "Extends keyword"))
		{
			return null;
		}
		state = state.afterToken();
		state = parseImports(state, extendedModules);
		if (state == null)
		{
			return null;
		}

		if (!state.peekToken(USES, "Uses keyword"))
		{
			return null;
		}
		state = state.afterToken();
		state = parseImports(state, usedModules);
		if (state == null)
		{
			return null;
		}

		if (!state.peekToken(NAMES, "Names keyword"))
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

		if (!state.peekToken(BODY, "Body keyword"))
		{
			return null;
		}
		state = state.afterToken();

		assert workStack.isEmpty();
		return state;
	}

	/**
	 * Parse an expression. Backtracking will find all valid interpretations.
	 * This method is a key optimization point, so the fragmentCache is used to
	 * keep track of parsing solutions at this point, simply replaying them on
	 * subsequent parses, as long as the variable declarations up to that point
	 * were identical.
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
			attempt(
				new Continuation0()
				{
					@Override
					public void value ()
					{
						markerFired.value = true;
					}
				},
				"Expression marker",
				start.position);
			fragmentCache.startComputingForState(start);
			final Con<AvailObject> justRecord =
				new Con<AvailObject>("Expression")
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
			attempt(
				new Continuation0()
				{
					@Override
					public void value ()
					{
						parseExpressionUncachedThen(start, justRecord);
					}
				},
				"Capture expression for caching",
				start.position);
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
	abstract void parseStatementAsOutermostCanBeLabelThen (
		final ParserState start,
		final boolean outermost,
		final boolean canBeLabel,
		final Con<AvailObject> continuation);

	/**
	 * Parse an expression, without directly using the
	 * {@linkplain #fragmentCache}.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the expression.
	 */
	abstract void parseExpressionUncachedThen (
		final ParserState start,
		final Con<AvailObject> continuation);

}
