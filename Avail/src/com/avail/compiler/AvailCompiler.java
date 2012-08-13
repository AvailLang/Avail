/**
 * AvailCompiler.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import com.avail.builder.ModuleName;
import com.avail.descriptor.*;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * I parse a source file to create a {@linkplain ModuleDescriptor module}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
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
					final ParserState ignored,
					final Con<AvailObject> whenFoundStatement)
				{
					parseExpressionThen(
						start,
						new Con<AvailObject>("End of statement")
						{
							@Override
							public void value (
								final ParserState afterExpression,
								final AvailObject expression)
							{
								if (expression.expressionType().equals(TOP.o()))
								{
									whenFoundStatement.value(
										afterExpression.afterToken(),
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
					final ParserState afterSubexpression,
					final AvailObject subexpression)
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
		final List<AvailObject> argumentExpressions,
		final AvailObject bundle,
		final AvailObject method,
		final Con<AvailObject> continuation)
	{
		final Mutable<Boolean> valid = new Mutable<Boolean>(true);
		final List<AvailObject> argumentNodeTypes = new ArrayList<AvailObject>(
			argumentExpressions.size());
		for (final AvailObject argExpr : argumentExpressions)
		{
			argumentNodeTypes.add(argExpr.kind());
		}
		method.validateArgumentTypesInterpreterIfFail(
			argumentNodeTypes,
			interpreter,
			new Continuation1<Generator<String>>()
			{
				@Override
				public void value (final Generator<String> arg)
				{
					stateAfterCall.expected(
						"parse node types to agree with macro types");
					valid.value = false;
				}
			});
		if (!valid.value)
		{
			return;
		}

		// Construct code to invoke the method, since it might be a
		// primitive and we can't invoke that directly as the outermost
		// function.
		final L1InstructionWriter writer = new L1InstructionWriter(
			module,
			stateBeforeCall.peekToken().lineNumber());
		for (final AvailObject arg : argumentExpressions)
		{
			writer.write(new L1Instruction(
				L1Operation.L1_doPushLiteral,
				writer.addLiteral(arg)));
		}
		writer.write(new L1Instruction(
			L1Operation.L1_doCall,
			writer.addLiteral(method),
			writer.addLiteral(PARSE_NODE.mostGeneralType())));
		writer.argumentTypes();
		writer.primitiveNumber(0);
		writer.returnType(PARSE_NODE.mostGeneralType());
		final AvailObject newFunction = FunctionDescriptor.create(
			writer.compiledCode(),
			TupleDescriptor.empty());
		newFunction.makeImmutable();
		try
		{
			final AvailObject replacement = interpreter.runFunctionArguments(
				newFunction,
				Collections.<AvailObject> emptyList());
			if (replacement.isInstanceOfKind(PARSE_NODE.mostGeneralType()))
			{
				final AvailObject substitution =
					MacroSubstitutionNodeDescriptor.fromNameAndNode(
						bundle.message(),
						replacement);
				// Declarations introduced in the macro should now be moved
				// out of scope.
				attempt(
					new ParserState(
						stateAfterCall.position,
						stateBeforeCall.scopeMap),
					continuation,
					substitution);
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
