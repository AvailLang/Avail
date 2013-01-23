/**
 * AvailCompiler.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.annotations.Nullable;
import com.avail.builder.ModuleName;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.Generator;

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
	 * @param interpreter
	 *            The interpreter used to execute code during compilation.
	 * @param moduleName
	 *            The {@link ModuleName} of the module being compiled.
	 * @param source
	 *            The {@link String} containing the module's source.
	 * @param tokens
	 *            The {@link List} of {@linkplain TokenDescriptor tokens}
	 *            scanned from the module's source.
	 */
	public AvailCompiler (
		final L2Interpreter interpreter,
		final ModuleName moduleName,
		final String source,
		final List<AvailObject> tokens)
	{
		super(interpreter, moduleName, source, tokens);
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
		final Con<AvailObject> continuation)
	{
		tryIfUnambiguousThen(
			start,
			new Con<Con<AvailObject>>("Detect ambiguity")
			{
				@Override
				public void value (
					final @Nullable ParserState ignored,
					final @Nullable Con<AvailObject> whenFoundStatement)
				{
					assert whenFoundStatement != null;
					parseExpressionThen(
						start,
						new Con<AvailObject>("End of statement")
						{
							@Override
							public void value (
								final @Nullable ParserState afterExpression,
								final @Nullable AvailObject expression)
							{
								assert afterExpression != null;
								assert expression != null;
								if (expression.expressionType().equals(TOP.o()))
								{
									whenFoundStatement.value(
										afterExpression,
										expression);
								}
								else
								{
									afterExpression.expected(
										"outer level statement "
										+ "to have top type");
								}
							}
						});
				}
			},
			continuation);
	}

	@Override
	void parseExpressionUncachedThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		final Con<AvailObject> newContinuation =
			new Con<AvailObject>("Optional leading argument send")
			{
				@Override
				public void value (
					final @Nullable ParserState afterSubexpression,
					final @Nullable AvailObject subexpression)
				{
					assert afterSubexpression != null;
					assert subexpression != null;
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
		final List<AvailObject> passedArgumentExpressions,
		final AvailObject bundle,
		final AvailObject method,
		final Con<AvailObject> continuation)
	{
		final AvailObject definitions = method.definitionsTuple();
		assert definitions.tupleSize() == 1;
		final AvailObject macroDefinition = definitions.tupleAt(1);
		final AvailObject macroBody = macroDefinition.bodyBlock();
		final AvailObject macroBodyKind = macroBody.kind();
		final List<AvailObject> argumentExpressions =
			new ArrayList<AvailObject>(passedArgumentExpressions.size());
		// Strip off macro substitution wrappers from the arguments.  These
		// were preserved only long enough to test grammatical restrictions.
		for (final AvailObject argumentExpression : passedArgumentExpressions)
		{
			argumentExpressions.add(argumentExpression.stripMacro());
		}
		if (!macroBodyKind.acceptsListOfArgValues(argumentExpressions))
		{
			stateAfterCall.expected(
				new Generator<String>()
				{
					@Override
					public String value ()
					{
						final List<Integer> disagreements =
							new ArrayList<Integer>();
						for (int i = 1; i <= macroBody.code().numArgs(); i++)
						{
							final AvailObject type =
								macroBodyKind.argsTupleType().typeAtIndex(i);
							final AvailObject value =
								argumentExpressions.get(i - 1);
							if (!value.isInstanceOf(type))
							{
								disagreements.add(i);
							}
						}
						assert disagreements.size() > 0;
						return String.format(
							"macro arguments %s to agree with definition:%n"
							+ "\tmacro = %s%n"
							+ "\texpected = %s%n"
							+ "\targuments = %s%n",
							disagreements,
							method.name(),
							macroBodyKind,
							argumentExpressions);
					}
				});
			return;
		}
		// A macro can't have semantic restrictions, so just run it.
		try
		{
			// Declarations introduced in the macro should now be moved
			// out of scope.
			final ParserState reportedStateDuringValidation = new ParserState(
				stateAfterCall.position - 1,
				stateBeforeCall.clientDataMap);
			final ParserState stateAfter = new ParserState(
				stateAfterCall.position,
				stateBeforeCall.clientDataMap);
			interpreter.currentParserState = reportedStateDuringValidation;
			AvailObject replacement;
			try
			{
				replacement = interpreter.runFunctionArguments(
					macroBody,
					argumentExpressions);
			}
			finally
			{
				interpreter.currentParserState = null;
			}
			if (replacement.isInstanceOfKind(PARSE_NODE.mostGeneralType()))
			{
				final AvailObject substitution =
					MacroSubstitutionNodeDescriptor.fromNameAndNode(
						bundle.message(),
						replacement);
				attempt(stateAfter, continuation, substitution);
			}
			else
			{
				stateAfterCall.expected(
					"macro body ("
					+ method.name().name()
					+ ") to produce a parse node");
			}
		}
		catch (final AvailRejectedParseException e)
		{
			stateAfterCall.expected(e.rejectionString().asNativeString());
		}
		catch (final Exception e)
		{
			stateAfterCall.expected(
				"evaluation of macro body not to raise an unhandled "
				+ "exception:\n\t"
				+ e);
		}
		return;
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
		final Con<AvailObject> continuation)
	{
		// Try a literal.
		if (start.peekToken().tokenType() == LITERAL)
		{
			final AvailObject literalNode =
				LiteralNodeDescriptor.fromToken(start.peekToken());
			attempt(start.afterToken(), continuation, literalNode);
		}
		else
		{
			start.expected("simple expression");
		}
	}
}
