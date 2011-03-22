/**
 * compiler/AvailCompiler.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
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

import static com.avail.compiler.scanning.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.compiler.node.*;
import com.avail.descriptor.*;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * I parse a source file to create a {@link ModuleDescriptor module}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailCompiler extends AbstractAvailCompiler
{

	/**
	 * Construct a new {@link AvailCompiler}.
	 *
	 * @param interpreter
	 *            The interpreter used to execute code during compilation.
	 */
	public AvailCompiler (final L2Interpreter interpreter)
	{
		super(interpreter);
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
	@Override
	void parseStatementAsOutermostCanBeLabelThen (
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
	@Override
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
		final AvailObject complete = bundleTree.lazyComplete();
		final AvailObject incomplete = bundleTree.lazyIncomplete();
		final AvailObject special = bundleTree.lazySpecialActions();
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
			for (final MapDescriptor.Entry entry : complete.mapIterable())
			{
				if (interpreter.runtime().hasMethodsAt(entry.key))
				{
					completedSendNode(
						start,
						argsSoFar,
						innerArgsSoFar,
						entry.value,
						continuation);
				}
			}
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
			for (final MapDescriptor.Entry entry : special.mapIterable())
			{
				attempt(
					new Continuation0()
					{
						@Override
						public void value ()
						{
							runParsingInstructionThen(
								start,
								entry.key.extractInt(),
								firstArgOrNull,
								argsSoFar,
								innerArgsSoFar,
								initialTokenPosition,
								entry.value,
								continuation);
						}
					},
					"Continue with instruction " + entry.key,
					start.position);
			}
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
				for (MapDescriptor.Entry entry : incomplete.mapIterable())
				{
					sorted.add(entry.key.asNativeString());
				}
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


}
