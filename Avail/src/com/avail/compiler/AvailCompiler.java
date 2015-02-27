/**
 * AvailCompiler.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.avail.annotations.Nullable;
import com.avail.builder.ResolvedModuleName;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.compiler.scanning.AvailScannerResult;
import com.avail.descriptor.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.io.TextInterface;
import com.avail.utility.evaluation.*;

/**
 * I parse a source file to create a {@linkplain ModuleDescriptor module}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailCompiler
extends AbstractAvailCompiler
{
	/**
	 * Construct a new {@link AvailCompiler}.
	 *
	 * @param module
	 *        The current {@linkplain ModuleDescriptor module}.
	 * @param scannerResult
	 *        An {@link AvailScannerResult}.
	 * @param textInterface
	 *        The {@linkplain TextInterface text interface} for any {@linkplain
	 *        A_Fiber fibers} started by this compiler.
	 * @param problemHandler
	 *        The {@link ProblemHandler} used for reporting compilation
	 *        problems.
	 */
	public AvailCompiler (
		final A_Module module,
		final AvailScannerResult scannerResult,
		final TextInterface textInterface,
		final ProblemHandler problemHandler)
	{
		super(module, scannerResult, textInterface, problemHandler);
	}

	/**
	 * Construct a new {@link AvailCompiler}.
	 *
	 * @param moduleName
	 *        The {@link ResolvedModuleName} of the module to compile.
	 * @param scannerResult
	 *        An {@link AvailScannerResult}.
	 * @param textInterface
	 *        The {@linkplain TextInterface text interface} for any {@linkplain
	 *        A_Fiber fibers} started by this compiler.
	 * @param problemHandler
	 *        The {@linkplain ProblemHandler problem handler}.
	 */
	public AvailCompiler (
		final ResolvedModuleName moduleName,
		final AvailScannerResult scannerResult,
		final TextInterface textInterface,
		final ProblemHandler problemHandler)
	{
		super(moduleName, scannerResult, textInterface, problemHandler);
	}

	/**
	 * Parse a top-level statement.  This is the <em>only</em> boundary for the
	 * backtracking grammar (it used to be that <em>all</em> statements had to
	 * be unambiguous, even those in blocks).  The passed continuation will be
	 * invoked at most once, and only if the top-level statement had a single
	 * interpretation.
	 */
	@Override
	void parseOutermostStatement (
		final ParserState start,
		final Con<A_Phrase> continuation,
		final Continuation0 afterFail)
	{
		// If a parsing error happens during parsing of this outermost
		// statement, only show the section of the file starting here.
		firstRelevantTokenOfSection = tokens.get(start.position);
		tryIfUnambiguousThen(
			start,
			new Con<Con<A_Phrase>>("Detect ambiguity")
			{
				@Override
				public void valueNotNull (
					final ParserState realStart,
					final Con<A_Phrase> whenFoundStatement)
				{
					parseExpressionThen(
						realStart,
						new Con<A_Phrase>("End of statement")
						{
							@Override
							public void valueNotNull (
								final ParserState afterExpression,
								final A_Phrase expression)
							{
								final ParseNodeKind parseNodeKind =
									expression.parseNodeKind();
								if (!parseNodeKind.isSubkindOf(STATEMENT_NODE))
								{
									afterExpression.expected(
										"an outer level statement, not "
										+ parseNodeKind.name()
										+ "(" + expression + ")");
								}
								else
								{
									whenFoundStatement.value(
										afterExpression,
										expression);
								}
							}
						});
				}
			},
			continuation,
			afterFail);
	}

	@Override
	void parseExpressionUncachedThen (
		final ParserState start,
		final Con<A_Phrase> continuation)
	{
		final Con<A_Phrase> newContinuation = new Con<A_Phrase>(
			"Optional leading argument send")
		{
			@Override
			public void valueNotNull (
				final ParserState afterSubexpression,
				final A_Phrase subexpression)
			{
				parseOptionalLeadingArgumentSendAfterThen(
					start,
					afterSubexpression,
					subexpression,
					continuation);
			}
		};
		parseLeadingKeywordSendThen(start, newContinuation);
		parseSimpleThen(start, newContinuation);
	}

	@Override
	void completedSendNodeForMacro (
		final ParserState stateBeforeCall,
		final ParserState stateAfterCall,
		final A_Phrase argumentsListNode,
		final A_Bundle bundle,
		final A_Definition macroDefinitionToInvoke,
		final Con<A_Phrase> continuation)
	{
		final A_Function macroBody = macroDefinitionToInvoke.bodyBlock();
		final A_Type macroBodyKind = macroBody.kind();
		final A_Tuple argumentsTuple = argumentsListNode.expressionsTuple();
		final int argCount = argumentsTuple.tupleSize();
		// Strip off macro substitution wrappers from the arguments.  These
		// were preserved only long enough to test grammatical restrictions.
		final List<A_Phrase> argumentsList = new ArrayList<>(argCount);
		for (final A_Phrase argument : argumentsTuple)
		{
			argumentsList.add(argument.stripMacro());
		}
		if (argumentsList.size() == 7)
		{
			if (argumentsList.get(4).expressionsSize() > 1)
			{
				// TODO [MvG] Remove debug
				System.out.println("Found one.");
			}
		}
		if (!macroBodyKind.acceptsListOfArgValues(argumentsList))
		{
			// TODO [MvG] Remove debug
			System.out.println(
				String.format(
					"Macro signature failure:\n\twanted:%s\n\tgot:%s",
					macroBodyKind,
					argumentsTuple));
//			stateAfterCall.expected("different argument types for macro");
			return;
		}
		startWorkUnit();
		final AtomicBoolean hasRunEither = new AtomicBoolean(false);
		evaluateMacroFunctionThen(
			macroDefinitionToInvoke,
			argumentsList,
			stateAfterCall.clientDataMap,
			workUnitCompletion(
				stateAfterCall.peekToken(),
				hasRunEither,
				new Continuation1<AvailObject>()
				{
					@Override
					public void value (final @Nullable AvailObject replacement)
					{
						assert replacement != null;
						assert replacement.isInstanceOfKind(
							PARSE_NODE.mostGeneralType());
						final A_Phrase substitution =
							MacroSubstitutionNodeDescriptor.fromNameAndNode(
								bundle.message(),
								replacement);
						// Declarations introduced inside the macro should be
						// removed from the scope (at the parse position after
						// the macro body runs).  Strip all client data, in
						// fact, to make the default mechanisms nested.
						final ParserState stateAfter = new ParserState(
							stateAfterCall.position,
							stateBeforeCall.clientDataMap);
						attempt(stateAfter, continuation, substitution);
					}
				}),
			workUnitCompletion(
				stateAfterCall.peekToken(),
				hasRunEither,
				new Continuation1<Throwable>()
				{
					@Override
					public void value (final @Nullable Throwable e)
					{
						assert e != null;
						if (e instanceof AvailRejectedParseException)
						{
							final AvailRejectedParseException rej =
								(AvailRejectedParseException) e;
							stateAfterCall.expected(
								rej.rejectionString().asNativeString());
						}
						else
						{
							stateAfterCall.expected(
								"evaluation of macro body not to raise an "
								+ "unhandled exception:\n\t"
								+ e);
						}
					}
				}));
	}

	/**
	 * Parse a literal, then invoke the continuation.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the simple parse node.
	 */
	private void parseSimpleThen (
		final ParserState start,
		final Con<A_Phrase> continuation)
	{
		// Try a literal.
		if (start.peekToken().tokenType() == LITERAL)
		{
			final A_Phrase literalNode =
				LiteralNodeDescriptor.fromToken(start.peekToken());
			attempt(start.afterToken(), continuation, literalNode);
		}
		else
		{
			start.expected("simple expression");
		}
	}
}
