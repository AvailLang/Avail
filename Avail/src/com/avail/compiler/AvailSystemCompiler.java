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

import static com.avail.compiler.AbstractAvailCompiler.ExpectedToken.*;
import static com.avail.compiler.scanning.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.node.*;
import com.avail.compiler.scanning.TokenDescriptor;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * I parse a source file to create a {@link ModuleDescriptor module}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailSystemCompiler
extends AbstractAvailCompiler
{
	/**
	 * Construct a new {@link AvailSystemCompiler}.
	 *
	 * @param interpreter
	 *            The interpreter used to execute code during compilation.
	 * @param source
	 *            The {@link String} of source code to be parsed.
	 * @param tokens
	 *            The list of {@linkplain TokenDescriptor tokens} to be parsed.
	 */
	public AvailSystemCompiler (
		final L2Interpreter interpreter,
		final String source,
		final List<AvailObject> tokens)
	{
		super(interpreter, source, tokens);
	}

	@Override
	boolean isSystemCompiler ()
	{
		return true;
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
		tryIfUnambiguousThen(
			start,
			new Con<Con<AvailObject>>("Detect ambiguity")
			{
				@Override
				public void value (
					final ParserState ignored,
					final Con<AvailObject> whenFoundStatement)
				{
					parseDeclarationThen(
						start,
						new Con<AvailObject>("Semicolon after declaration")
						{
							@Override
							public void value (
								final ParserState afterDeclaration,
								final AvailObject declaration)
							{
								if (afterDeclaration.peekToken(
									SEMICOLON,
									"; to end declaration statement"))
								{
									ParserState afterSemicolon =
										afterDeclaration.afterToken();
									if (outermost)
									{
										afterSemicolon = new ParserState(
											afterSemicolon.position,
											new AvailCompilerScopeStack(
												null, null));
									}
									whenFoundStatement.value(
										afterSemicolon,
										declaration);
								}
							}
						});
					parseAssignmentThen(
						start,
						new Con<AvailObject>("Semicolon after assignment")
						{
							@Override
							public void value (
								final ParserState afterAssignment,
								final AvailObject assignment)
							{
								if (afterAssignment.peekToken(
									SEMICOLON,
									"; to end assignment statement"))
								{
									whenFoundStatement.value(
										afterAssignment.afterToken(),
										assignment);
								}
							}
						});
					parseExpressionThen(
						start,
						new Con<AvailObject>("Semicolon after expression")
						{
							@Override
							public void value (
								final ParserState afterExpression,
								final AvailObject expression)
							{
								if (!afterExpression.peekToken(
									SEMICOLON,
									"; to end statement"))
								{
									return;
								}
								if (!outermost
									|| expression.expressionType().equals(
										TOP.o()))
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
					if (canBeLabel)
					{
						parseLabelThen(
							start,
							new Con<AvailObject>("Semicolon after label")
							{
								@Override
								public void value (
									final ParserState afterDeclaration,
									final AvailObject label)
								{
									if (afterDeclaration.peekToken(
										SEMICOLON,
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
	 * Parse an assignment statement.
	 *
	 * @param start
	 *        Where to start parsing.
	 * @param continuation
	 *        What to do with the parsed assignment statement.
	 */
	void parseAssignmentThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		if (start.peekToken().tokenType() != KEYWORD)
		{
			// Don't suggest it's an assignment attempt with no evidence.
			return;
		}
		parseVariableUseWithExplanationThen(
			start,
			"for an assignment",
			new Con<AvailObject>("Variable use for assignment")
			{
				@Override
				public void value (
					final ParserState afterVar,
					final AvailObject varUse)
				{
					if (!afterVar.peekToken(COLON, ":= for assignment"))
					{
						return;
					}
					final ParserState afterColon = afterVar.afterToken();
					if (!afterColon.peekToken(
						EQUALS,
						"= part of := for assignment"))
					{
						return;
					}

					final ParserState afterEquals = afterColon.afterToken();
					final Mutable<AvailObject> varType =
						new Mutable<AvailObject>();
					final AvailObject declaration = varUse.declaration();
					boolean ok = false;
					if (declaration == null)
					{
						start.expected("variable to have been declared");
					}
					else
					{
						String errorSuffix = null;
						switch (declaration.declarationKind())
						{
							case ARGUMENT:
								errorSuffix = "not to be an argument";
								break;
							case LABEL:
								errorSuffix = "to be a variable, not a label";
								break;
							case LOCAL_CONSTANT:
							case MODULE_CONSTANT:
								errorSuffix = "not to be a constant";
								break;
							case MODULE_VARIABLE:
							case LOCAL_VARIABLE:
								varType.value = declaration.declaredType();
								ok = true;
						}
						if (errorSuffix != null)
						{
							start.expected(
								"assignment variable " + errorSuffix);
						}
					}

					if (!ok)
					{
						return;
					}
					parseExpressionThen(
						afterEquals,
						new Con<AvailObject>(
							"Expression for right side of assignment")
						{
							@Override
							public void value (
								final ParserState afterExpr,
								final AvailObject expr)
							{
								if (afterExpr.peekToken().tokenType()
										!= END_OF_STATEMENT)
								{
									afterExpr.expected(
										"; to end assignment statement");
									return;
								}
								if (expr.expressionType().isSubtypeOf(
									varType.value))
								{
									final AvailObject assignment =
										AssignmentNodeDescriptor.from(
											varUse, expr);
									attempt(
										afterExpr,
										continuation,
										assignment);
								}
								else
								{
									afterExpr.expected(new Generator<String>()
									{
										@Override
										public String value ()
										{
											return String.format(
												"assignment expression's type "
												+ "(%s) to match variable type "
												+ "(%s)",
												expr.expressionType(),
												varType.value);
										}
									});
								}
							}
						});
				}
			});
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
			DOLLAR_SIGN,
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
			COLON,
			"colon for label's type declaration"))
		{
			return;
		}
		final ParserState afterColon = atColon.afterToken();
		attempt(
			new Continuation0()
			{
				@Override
				public void value ()
				{
					parseAndEvaluateExpressionYieldingInstanceOfThen(
						afterColon,
						ContinuationTypeDescriptor.meta(),
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
			COLON,
			": or ::= for simple/constant/initializing declaration"))
		{
			return;
		}
		final ParserState afterFirstColon = afterVar.afterToken();
		if (afterFirstColon.peekToken(
			COLON,
			"second colon for constant declaration (a ::= expr)"))
		{
			final ParserState afterSecondColon = afterFirstColon.afterToken();
			if (afterSecondColon.peekToken(
				EQUALS,
				"= part of ::= in constant declaration"))
			{
				final ParserState afterEquals = afterSecondColon.afterToken();
				parseExpressionThen(
					afterEquals,
					new Con<AvailObject>("Complete var ::= expr")
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
					if (type.equals(TOP.o())
						|| type.equals(BottomTypeDescriptor.bottom()))
					{
						afterType.expected(
							"a type for the variable other than"
							+ " top or bottom");
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
						COLON,
						"Second colon of var : type := init"))
					{
						return;
					}
					final ParserState afterSecondColon = afterType.afterToken();
					if (!afterSecondColon.peekToken(
						EQUALS,
						"Equals sign in var : type := init"))
					{
						return;
					}
					final ParserState afterEquals =
						afterSecondColon.afterToken();

					parseExpressionThen(
						afterEquals,
						new Con<AvailObject>("After expr of var : type := expr")
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
										new Generator<String>()
										{
											@Override
											public String value()
											{
												return String.format(
													"initializing expression's "
													+ "type (%s) to agree with "
													+ "declared type (%s)",
													initExpr.expressionType(),
													type);
											};
										});
								}
							}
						});
				}
			});
	}

	/**
	 * Parse a primitive failure variable declaration. This is simple
	 * declaration form only: (var : type).
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the primitive failure variable declaration.
	 */
	void parsePrimitiveFailureDeclarationThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		final AvailObject localName = start.peekToken();
		if (localName.tokenType() != KEYWORD)
		{
			start.expected("a primitive failure variable declaration");
			return;
		}
		final ParserState afterVar = start.afterToken();
		if (!afterVar.peekToken(
			COLON,
			": for primitive failure variable declaration"))
		{
			return;
		}
		final ParserState afterColon = afterVar.afterToken();
		parseAndEvaluateExpressionYieldingInstanceOfThen(
			afterColon,
			TYPE.o(),
			new Con<AvailObject>("Type expression of var : type")
			{
				@Override
				public void value (
					final @NotNull ParserState afterType,
					final @NotNull AvailObject type)
				{
					if (type.equals(TOP.o())
						|| type.equals(BottomTypeDescriptor.bottom()))
					{
						afterType.expected(
							"a type for the variable other than"
							+ " top or bottom");
						return;
					}
					final AvailObject declaration =
						DeclarationNodeDescriptor.newVariable(localName, type);
					attempt(
						afterType.withDeclaration(declaration),
						continuation,
						declaration);
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
		if (start.peekToken(COMMA, "comma and more block arguments"))
		{
			parseBlockArgumentThen(
				start.afterToken(),
				new Con<AvailObject>("Additional block argument")
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
			VERTICAL_BAR,
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
		parseBlockArgumentThen(
			start,
			new Con<AvailObject>("Block argument")
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
			start.expected("block argument name then : and type");
			return;
		}
		final ParserState afterArgName = start.afterToken();
		if (!afterArgName.peekToken(COLON, ": then argument type"))
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
					if (type.equals(TOP.o()))
					{
						afterArgType.expected(
							"a type for the argument other than top");
					}
					else if (type.equals(BottomTypeDescriptor.bottom()))
					{
						afterArgType.expected(
							"a type for the argument other than bottom");
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
	 * Parse a block (a function).
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
		if (!start.peekToken(OPEN_SQUARE))
		{
			// Don't suggest a block was expected here unless at least the "["
			// was present.
			return;
		}
		final AvailCompilerScopeStack scopeOutsideBlock = start.scopeStack;
		parseBlockArgumentsThen(
			start.afterToken(),
			new Con<List<AvailObject>>("Block arguments")
			{
				@Override
				public void value (
					final ParserState afterArguments,
					final List<AvailObject> arguments)
				{
					parseOptionalPrimitiveForArgCountThen(
						afterArguments,
						arguments.size(),
						new Con<Integer>("Optional primitive declaration")
						{
							@Override
							public void value (
								final ParserState afterOptionalPrimitive,
								final Integer primitive)
							{
								final Primitive thePrimitive =
									Primitive.byPrimitiveNumber(primitive);
								if (thePrimitive != null
									&& thePrimitive.hasFlag(Flag.CannotFail))
								{
									finishBlockThen(
										afterOptionalPrimitive,
										arguments,
										primitive,
										Collections.<AvailObject>emptyList(),
										scopeOutsideBlock,
										continuation);
									return;
								}
								parseStatementsThen(
									afterOptionalPrimitive,
									primitive == 0,
									primitive == 0
										? Collections.<AvailObject>emptyList()
										: Collections.<AvailObject>
											singletonList(
												afterOptionalPrimitive
													.scopeStack.declaration()),
									new Con<List<AvailObject>>(
										"Block statements")
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
	 * @param primitiveNumber
	 *            The primitive number
	 * @param statements
	 *            The list of statements.
	 * @param scopeOutsideBlock
	 *            The scope that existed before the block started to be parsed.
	 * @param continuation
	 *            What to do with the {@link BlockNodeDescriptor block}.
	 */
	@InnerAccess void finishBlockThen (
		final ParserState afterStatements,
		final List<AvailObject> arguments,
		final int primitiveNumber,
		final List<AvailObject> statements,
		final AvailCompilerScopeStack scopeOutsideBlock,
		final Con<AvailObject> continuation)
	{
		if (!afterStatements.peekToken(
			CLOSE_SQUARE,
			"close bracket (']') to end block"))
		{
			return;
		}

		final ParserState afterClose = afterStatements.afterToken();
		final Primitive thePrimitive =
			Primitive.byPrimitiveNumber(primitiveNumber);
		if (statements.isEmpty()
			&& thePrimitive != null
			&& !thePrimitive.hasFlag(Flag.CannotFail))
		{
			afterClose.expected(
				"one or more statements to follow fallible "
				+ "primitive declaration ");
			return;
		}

		final Mutable<AvailObject> lastStatementType =
			new Mutable<AvailObject>();
		if (statements.size() > 0)
		{
			final AvailObject stmt = statements.get(statements.size() - 1);
			if (stmt.isInstanceOfKind(DECLARATION_NODE.o()))
			{
				lastStatementType.value = TOP.o();
			}
			else
			{
				lastStatementType.value = stmt.expressionType();
			}
		}
		else
		{
			lastStatementType.value = TOP.o();
		}

		final ParserState stateOutsideBlock = new ParserState(
			afterClose.position,
			scopeOutsideBlock);

		final List<AvailObject> argumentTypesList =
			new ArrayList<AvailObject>(arguments.size());
		for (final AvailObject argument : arguments)
		{
			argumentTypesList.add(argument.declaredType());
		}
		boolean blockTypeGood = true;
		if (statements.size() > 0
				&& statements.get(0).isInstanceOfKind(LABEL_NODE.o()))
		{
			final AvailObject labelNode = statements.get(0);
			final AvailObject labelFunctionType =
				labelNode.declaredType().functionType();
			final AvailObject implicitBlockType =
				FunctionTypeDescriptor.create(
					TupleDescriptor.fromList(argumentTypesList),
					lastStatementType.value,
					SetDescriptor.empty());
			blockTypeGood = labelFunctionType.equals(implicitBlockType);
		}

		if (!blockTypeGood)
		{
			stateOutsideBlock.expected(
				"label's declared type to exactly match "
				+ "enclosing block's basic type");
		}
		else if (thePrimitive == null)
		{
			parseOptionalBlockExceptionsClauseThen(
				stateOutsideBlock,
				new Con<AvailObject>("Block checked exceptions")
				{
					@Override
					public void value(
						final ParserState state,
						final AvailObject checkedExceptions)
					{
						final AvailObject blockNode =
							BlockNodeDescriptor.newBlockNode(
								arguments,
								primitiveNumber,
								statements,
								lastStatementType.value,
								checkedExceptions);
						attempt(stateOutsideBlock, continuation, blockNode);
					};
				});
		}

		if (!stateOutsideBlock.peekToken(
			COLON,
			thePrimitive != null
				? "primitive block's mandatory return type declaration"
				: "optional block return type declaration"))
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
					final AvailObject explicitBlockType =
						FunctionTypeDescriptor.create(
							TupleDescriptor.fromList(argumentTypesList),
							returnType);
					if (thePrimitive != null)
					{
						final AvailObject intrinsicType =
							thePrimitive.blockTypeRestriction();
						if (!intrinsicType.isSubtypeOf(explicitBlockType))
						{
							afterReturnType.expected(
								new Generator<String>()
								{
									@Override
									public String value ()
									{
										return String.format(
											"block type (%s) to agree with "
											+ "primitive %s's intrinsic type "
											+ "(%s)",
											explicitBlockType,
											thePrimitive.name(),
											intrinsicType);
									}
								});
							return;
						}
					}

					if ((thePrimitive == null
							|| !thePrimitive.hasFlag(Flag.CannotFail))
						&& !lastStatementType.value.isSubtypeOf(returnType))
					{
						afterReturnType.expected(
							new Generator<String>()
							{
								@Override
								public String value ()
								{
									return String.format(
										"last statement's type (%s) "
										+ "to agree with block's declared "
										+ "result type (%s)",
										lastStatementType.value,
										returnType);
								}
							});
						return;
					}

					boolean blockTypeGood2 = true;
					if (statements.size() > 0
						&& statements.get(0).isInstanceOfKind(
							LABEL_NODE.o()))
					{
						final AvailObject labelNode = statements.get(0);
						final AvailObject labelFunctionType =
							labelNode.declaredType().functionType();
						blockTypeGood2 = labelFunctionType.equals(
							explicitBlockType);
					}
					if (blockTypeGood2)
					{
						parseOptionalBlockExceptionsClauseThen(
							afterReturnType,
							new Con<AvailObject>("Block checked exceptions")
							{
								@Override
								public void value(
									final ParserState afterExceptions,
									final AvailObject checkedExceptions)
								{
									final AvailObject blockNode =
										BlockNodeDescriptor.newBlockNode(
											arguments,
											primitiveNumber,
											statements,
											returnType,
											checkedExceptions);
									attempt(
										afterExceptions,
										continuation,
										blockNode);
								};
							});
					}
					else
					{
						stateOutsideBlock.expected(
							"label's declared type to exactly match "
							+ "enclosing block's declared type");
					}
				}
			});
	}

	/**
	 * Parse the optional declaration of exceptions after a block.  This is a
	 * caret (^) followed by a comma-separated list of expressions that yield
	 * exception types.
	 *
	 * @param start Where to start parsing.
	 * @param continuation What to do with the resulting exception set.
	 */
	@InnerAccess void parseOptionalBlockExceptionsClauseThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		attempt(start, continuation, SetDescriptor.empty());

		if (!start.peekToken(
			CARET,
			"optional block exceptions declaration"))
		{
			return;
		}
		final ParserState afterColon = start.afterToken();
		parseMoreExceptionClausesThen (
			afterColon,
			SetDescriptor.empty(),
			continuation);
	}

	/**
	 * Parse at least one more exception clause, trying the continuation with
	 * each potential set of exceptions that are parsed.
	 *
	 * @param atNextException Where to start parsing the next exception.
	 * @param exceptionsAlready The exception set that has been parsed so far.
	 * @param continuation What to do with the extended exception set.
	 */
	@InnerAccess void parseMoreExceptionClausesThen (
		final ParserState atNextException,
		final AvailObject exceptionsAlready,
		final Con<AvailObject> continuation)
	{
		parseAndEvaluateExpressionYieldingInstanceOfThen(
			atNextException,
			ObjectDescriptor.objectFromMap(MapDescriptor.empty()),
			new Con<AvailObject>("Exception declaration entry for block")
			{
				@Override
				public void value (
					final ParserState afterException,
					final AvailObject exceptionType)
				{
					final AvailObject newExceptionSet =
						exceptionsAlready.setWithElementCanDestroy(
							exceptionType,
							false);
					newExceptionSet.makeImmutable();
					attempt(
						afterException,
						continuation,
						newExceptionSet);
					if (afterException.peekToken(
						COMMA,
						"Comma in exception declarations"))
					{
						parseMoreExceptionClausesThen(
							afterException.afterToken(),
							newExceptionSet,
							continuation);
					}
				}
			});
	}

	@Override
	void parseExpressionUncachedThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		parseLeadingKeywordSendThen(
			start,
			new Con<AvailObject>("Uncached leading keyword send")
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
		parseSimpleThen(
			start,
			new Con<AvailObject>("Uncached simple expression")
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
		parseBlockThen(
			start,
			new Con<AvailObject>("Uncached block expression")
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
					attempt(
						new Continuation0()
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
	 *            The {@link TupleDescriptor tuple} of {@link
	 *            MessageBundleTreeDescriptor bundle trees} at which to continue
	 *            parsing.
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
							attempt(
								new Continuation0()
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
				attempt(
					new Continuation0()
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
				attempt(
					new Continuation0()
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
				attempt(
					new Continuation0()
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
				final List<AvailObject> newArgsSoFar =
					new ArrayList<AvailObject>(argsSoFar);
				final AvailObject marker =
					newArgsSoFar.remove(newArgsSoFar.size() - 2);
				assert marker.traversed().descriptor()
					instanceof MarkerNodeDescriptor;
				attempt(
					new Continuation0()
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
				attempt(
					new Continuation0()
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
							attempt(
								new Continuation0()
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
						final List<List<AvailObject>> newInnerArgs =
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
						attempt(
							new Continuation0()
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
	 * If this is a macro, fail immediately, since the system compiler isn't
	 * supposed to process macros.
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
		final AvailObject message = bundle.message();
		final AvailObject impSet = interpreter.runtime().methodsAt(message);
		assert !impSet.equalsNull();
		final AvailObject implementationsTuple = impSet.implementationsTuple();
		assert implementationsTuple.tupleSize() > 0;

		if (implementationsTuple.tupleAt(1).isMacro())
		{
			start.expected(
				new Generator<String>()
				{
					@Override
					public String value ()
					{
						return
							"something other than an invocation of the macro "
							+ bundle.message().name();
					}
				});
			return;
		}

		// It invokes a method (not a macro).
		for (final AvailObject arg : argumentExpressions)
		{
			if (arg.expressionType().equals(
				BottomTypeDescriptor.bottom()))
			{
				start.expected("argument to have type other than bottom");
				return;
			}
			if (arg.expressionType().equals(TOP.o()))
			{
				start.expected("argument to have type other than top");
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
			final List<AvailObject> argumentOccurrences =
				innerArguments.get(i - 1);
			for (final AvailObject argument : argumentOccurrences)
			{
				final AvailObject argumentSendName =
					argument.apparentSendName();
				if (!argumentSendName.equalsNull())
				{
					final AvailObject restrictions =
						bundle.restrictions().tupleAt(i);
					if (restrictions.hasElement(argumentSendName))
					{
						final int index = i;
						ifFail.value(
							new Generator<String>()
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
		where.expected(
			new Generator<String>()
			{
				@Override
				public String value ()
				{
					final StringBuilder builder = new StringBuilder(200);
					builder.append("one of the following internal keywords:");
					final List<String> sorted =
						new ArrayList<String>(incomplete.mapSize());
					for (final MapDescriptor.Entry entry
						: incomplete.mapIterable())
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

		// Don't wrap it if its type is top.
		if (node.expressionType().equals(TOP.o()))
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
		final Con<Integer> continuation)
	{
		// Try it first without looking for the primitive declaration.
		attempt(start, continuation, 0);

		// Now look for the declaration.
		if (!start.peekToken(
			PRIMITIVE,
			"optional primitive declaration"))
		{
			return;
		}
		final ParserState afterPrimitiveKeyword = start.afterToken();
		final AvailObject token = afterPrimitiveKeyword.peekToken();
		if (token.tokenType() != LITERAL
				|| !token.literal().isInstanceOfKind(
					IntegerRangeTypeDescriptor.unsignedShorts())
				|| token.literal().extractInt() == 0)
		{
			afterPrimitiveKeyword.expected(
				new Generator<String>()
				{
					@Override
					public String value ()
					{
						return "A non-zero unsigned short [1..65535] "
								+ "after the Primitive keyword";
					}
				});
			return;
		}
		final int primitiveNumber = token.literal().extractInt();
		if (!interpreter.supportsPrimitive(primitiveNumber))
		{
			afterPrimitiveKeyword.expected(
				"a supported primitive number, not #"
				+ Integer.toString(primitiveNumber));
			return;
		}

		final Primitive prim = Primitive.byPrimitiveNumber(primitiveNumber);
		if (!interpreter.primitiveAcceptsThisManyArguments(
			primitiveNumber, argCount))
		{
			afterPrimitiveKeyword.expected(
				new Generator<String>()
				{
					@Override
					public String value ()
					{
						return String.format(
							"Primitive (%s) to be passed %i arguments, not %i",
							prim.name(),
							prim.argCount(),
							argCount);
					}
				});
		}

		final ParserState afterPrimitiveNumber =
			afterPrimitiveKeyword.afterToken();
		if (prim.hasFlag(Flag.CannotFail))
		{
			if (!afterPrimitiveNumber.peekToken(
				SEMICOLON,
				"; after infallible primitive declaration"))
			{
				return;
			}

			attempt(
				afterPrimitiveNumber.afterToken(),
				continuation,
				primitiveNumber);
		}

		if (!afterPrimitiveNumber.peekToken(
			OPEN_PARENTHESIS,
			"open parenthesis after fallible primitive number"))
		{
			return;
		}

		final ParserState afterOpenParenthesis =
			afterPrimitiveNumber.afterToken();
		parsePrimitiveFailureDeclarationThen(
			afterOpenParenthesis,
			new Con<AvailObject>("after declaring primitive failure variable")
			{
				@Override
				public void value (
					final @NotNull ParserState afterDeclaration,
					final @NotNull AvailObject declaration)
				{
					if (!prim.failureVariableType().isSubtypeOf(
						declaration.declaredType()))
					{
						afterDeclaration.expected(
							"primitive #"
							+ primitiveNumber
							+ " failure variable to be able to hold values "
							+ "of type ("
							+ prim.failureVariableType()
							+ ")");
						return;
					}
					if (!afterDeclaration.peekToken(
						CLOSE_PARENTHESIS,
						"close parenthesis after primitive failure variable "
						+ "declaration"))
					{
						return;
					}

					final ParserState afterCloseParenthesis =
						afterDeclaration.afterToken();
					if (!afterCloseParenthesis.peekToken(
						SEMICOLON,
						"; after close parenthesis"))
					{
						return;
					}

					attempt(
						afterCloseParenthesis.afterToken(),
						continuation,
						primitiveNumber);
				}
			});
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

		if (!start.peekToken(COLON))
		{
			return;
		}
		final ParserState afterColon = start.afterToken();
		if (!afterColon.peekToken(COLON))
		{
			start.expected(
				new Generator<String>()
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
		attempt(
			new Continuation0()
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
										SuperCastNodeDescriptor.create(
											expr,
											type);
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
				parseExpressionThen(
					start,
					new Con<AvailObject>(
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
		parseReferenceThen(start, continuation);

		start.expected("simple expression");
	}

	/**
	 * Parse a reference expression, which is an ampersand (&) followed by a
	 * variable name.
	 *
	 * @param start
	 *            Where to start parsing the reference expression.
	 * @param continuation
	 *            What to do if a reference expression is found.
	 */
	void parseReferenceThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		if (start.peekToken(AMPERSAND))
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
								afterAmpersand.expected(
									"reference variable " + suffix);
							}
						}
					}
				});
		}
	}

	/**
	 * Try the current list of statements but also try to parse more.
	 *
	 * @param start
	 *            Where to parse.
	 * @param canHaveLabel
	 *            Whether the statements can start with a label declaration.
	 * @param statements
	 *            The preceding list of statements.
	 * @param continuation
	 *            What to do with the resulting list of statements.
	 */
	void parseStatementsThen (
		final ParserState start,
		final boolean canHaveLabel,
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
			if (lastStatement.expressionType().equals(
				BottomTypeDescriptor.bottom()))
			{
				start.expected(
					"end of statements, since this one always bottom");
				return;
			}
			if (!lastStatement.expressionType().equals(TOP.o())
				&& !lastStatement.isInstanceOfKind(ASSIGNMENT_NODE.o()))
			{
				start.expected(
					new Generator<String>()
					{
						@Override
						public String value ()
						{
							return String.format(
								"non-last statement \"%s\" "
								+ " to have type top, not \"%s\".",
								lastStatement,
								lastStatement.expressionType());
						}
					});
			}
		}
		start.expected("more statements");

		// Try for more statements.
		parseStatementAsOutermostCanBeLabelThen(
			start,
			false,
			canHaveLabel && statements.isEmpty(),
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
					parseStatementsThen(
						afterStatement,
						false,
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
		start.expected(
			new Generator<String>()
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
