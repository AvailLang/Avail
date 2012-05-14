/**
 * AvailSystemCompiler.java
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

import static com.avail.compiler.AbstractAvailCompiler.ExpectedToken.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TokenDescriptor.TokenType.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.builder.ModuleName;
import com.avail.descriptor.*;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * I parse a source file to create a {@linkplain ModuleDescriptor module}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class AvailSystemCompiler
extends AbstractAvailCompiler
{
	/**
	 * Construct a new {@link AvailSystemCompiler}.
	 *
	 * @param interpreter
	 *            The interpreter used to execute code during compilation.
	 * @param moduleName
	 *            The {@link ModuleName} of the module being parsed.
	 * @param source
	 *            The {@link String} of source code to be parsed.
	 * @param tokens
	 *            The list of {@linkplain TokenDescriptor tokens} to be parsed.
	 */
	public AvailSystemCompiler (
		final L2Interpreter interpreter,
		final ModuleName moduleName,
		final String source,
		final List<AvailObject> tokens)
	{
		super(interpreter, moduleName, source, tokens);
	}

	@Override
	boolean isSystemCompiler ()
	{
		return true;
	}

	/**
	 * Parse a top-level statement.  This is the <em>only</em> boundary for the
	 * backtracking grammar.  The passed continuation will be invoked at most
	 * once, and only if the top-level statement had a single interpretation.
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
									final ParserState afterSemicolon =
										afterDeclaration.afterToken();
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
								whenFoundStatement.value(
									afterExpression.afterToken(),
									expression);
							}
						});
				}
			},
			continuation);
	}

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
	void parseInnerStatement (
		final @NotNull ParserState start,
		final boolean canBeLabel,
		final @NotNull List<AvailObject> argDecls,
		final @NotNull Con<AvailObject> continuation)
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
						final ParserState afterSemicolon =
							afterDeclaration.afterToken();
						continuation.value(
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
						continuation.value(
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
					if (expression.expressionType().equals(TOP.o()))
					{
						continuation.value(
							afterExpression.afterToken(),
							expression);
					}
					else
					{
						afterExpression.expected(
							new Generator<String>()
							{
								@Override
								public String value ()
								{
									return String.format(
										"statement to have type ⊤, not %s",
										expression.expressionType());
								}
							});
					}
				}
			});
		if (canBeLabel)
		{
			parseLabelThen(
				start,
				argDecls,
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
							continuation.value(
								afterDeclaration.afterToken(),
								label);
						}
					}
				});
		}
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
							case PRIMITIVE_FAILURE_REASON:
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
								if (expr.expressionType().equals(
									BottomTypeDescriptor.bottom()))
								{
									afterExpr.expected(
										"assignment expression to have a type "
										+ "other than ⊥");
									return;
								}
								if (expr.expressionType().isSubtypeOf(
									varType.value))
								{
									final AvailObject assignment =
										AssignmentNodeDescriptor.from(
											varUse, expr, false);
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
	 *        Where to start parsing
	 * @param argDecls
	 *        The enclosing block's argument declarations.
	 * @param continuation
	 *        What to do after parsing a label.
	 */
	void parseLabelThen (
		final @NotNull ParserState start,
		final @NotNull List<AvailObject> argDecls,
		final @NotNull Con<AvailObject> continuation)
	{
		assert argDecls != null;
		if (!start.peekToken(
			DOLLAR_SIGN,
			"label statement starting with \"$\""))
		{
			return;
		}
		final ParserState afterDollar = start.afterToken();
		final AvailObject token = afterDollar.peekToken();
		if (token.tokenType() != KEYWORD)
		{
			afterDollar.expected("name of label after $");
			return;
		}
		final Con<AvailObject> finishLabel = new Con<AvailObject>(
			"Label return type expression")
			{
				@Override
				public void value (
					final ParserState afterExpression,
					final AvailObject returnType)
				{
					final List<AvailObject> argTypes =
						new ArrayList<AvailObject>(argDecls.size());
					for (final AvailObject decl : argDecls)
					{
						argTypes.add(decl.declaredType());
					}
					final AvailObject contType =
						ContinuationTypeDescriptor.forFunctionType(
							FunctionTypeDescriptor.create(
								TupleDescriptor.fromCollection(argTypes),
								returnType));
					final AvailObject label =
						DeclarationNodeDescriptor.newLabel(
							token,
							contType);
					if (lookupDeclaration(
							afterExpression,
							token.string())
						!= null)
					{
						afterExpression.expected(
							"label name not to shadow"
							+ " another declaration");
						return;
					}
					final ParserState afterDeclaration =
						afterExpression.withDeclaration(label);
					attempt(afterDeclaration, continuation, label);
				}
			};
		final ParserState afterName = afterDollar.afterToken();
		if (afterName.peekToken(
			COLON,
			"colon for label's return type declaration"))
		{
			final ParserState afterColon = afterName.afterToken();
			eventuallyDo(
				new Continuation0()
				{
					@Override
					public void value ()
					{
						parseAndEvaluateExpressionYieldingInstanceOfThen(
							afterColon,
							TYPE.o(),
							finishLabel);
					}
				},
				"Label type",
				afterColon.position);
		}
		else
		{
			eventuallyDo(
				new Continuation0()
				{
					@Override
					public void value ()
					{
						finishLabel.value(
							afterName, BottomTypeDescriptor.bottom());
					}
				},
				"Default label return type",
				afterName.position);
		}
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
							if (lookupDeclaration(
									afterInitExpression,
									localName.string())
								!= null)
							{
								afterInitExpression.expected(
									"constant name not to shadow another"
									+ " declaration");
								return;
							}
							final AvailObject expressionType =
								initExpression.expressionType();
							if (expressionType.equals(TOP.o())
								|| expressionType.equals(
									BottomTypeDescriptor.bottom()))
							{
								afterInitExpression.expected(
									"constant expression to have a type other "
									+ "than "
									+ expressionType.toString());
								return;
							}
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
							"a type for the variable other than "
							+ type.toString());
						return;
					}
					// Try the simple declaration... var : type;
					if (lookupDeclaration(afterType, localName.string())
						!= null)
					{
						afterType.expected(
							"variable name not to shadow another declaration");
					}
					else
					{
						final AvailObject simpleDeclaration =
							DeclarationNodeDescriptor.newVariable(
								localName,
								type);
						attempt(
							afterType.withDeclaration(simpleDeclaration),
							continuation,
							simpleDeclaration);
					}

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
								if (initExpr.expressionType().equals(TOP.o()))
								{
									afterInit.expected(
										"initializing expression to have a "
										+ "type other than ⊤");
									return;
								}
								if (initExpr.expressionType().equals(
									BottomTypeDescriptor.bottom()))
								{
									afterInit.expected(
										"initializing expression to have a "
										+ "type other than ⊥");
									return;
								}
								if (initExpr.expressionType().isSubtypeOf(type))
								{
									final AvailObject initDecl =
										DeclarationNodeDescriptor.newVariable(
											localName,
											type,
											initExpr);
									if (lookupDeclaration(
											afterInit,
											localName.string())
										!= null)
									{
										afterInit.expected(
											"variable name not to shadow"
											+ " another declaration");
										return;
									}
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
											}
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
							+ type.toString());
						return;
					}
					if (lookupDeclaration(afterType, localName.string())
						!= null)
					{
						afterType.expected(
							"primitive failure variable not to shadow"
							+ " another declaration");
						return;
					}
					final AvailObject declaration =
						DeclarationNodeDescriptor.newPrimitiveFailureVariable(
							localName,
							type);
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
					if (type.equals(TOP.o())
						|| type.equals(BottomTypeDescriptor.bottom()))
					{
						afterArgType.expected(
							"a type for the argument other than "
							+ type.toString());
					}
					else
					{
						if (lookupDeclaration(afterArgType, localName.string())
							!= null)
						{
							afterArgType.expected(
								"block argument name not to shadow another"
								+ " declaration");
							return;
						}
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
		final AvailObject scopeOutsideBlock = start.scopeMap;
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
						new Con<AvailObject>("Optional primitive declaration")
						{
							@Override
							public void value (
								final ParserState afterOptionalPrimitive,
								final AvailObject primitiveAndFailure)
							{
								// The primitiveAndFailure is either a 1-tuple
								// with the (CannotFail) primitive number, or a
								// 2-tuple with the primitive number and the
								// declaration of the primitive failure
								// variable.
								final int primitive =
									primitiveAndFailure.tupleIntAt(1);
								final Primitive thePrimitive =
									Primitive.byPrimitiveNumber(primitive);
								if (thePrimitive != null
									&& thePrimitive.hasFlag(Flag.CannotFail))
								{
									assert primitiveAndFailure.tupleSize() == 1;
									finishBlockThen(
										afterOptionalPrimitive,
										arguments,
										primitive,
										Collections.<AvailObject>emptyList(),
										scopeOutsideBlock,
										continuation);
									return;
								}
								assert primitive == 0
									|| primitiveAndFailure.tupleSize() == 2;
								parseStatementsThen(
									afterOptionalPrimitive,
									primitive == 0,
									arguments,
									primitive == 0
										? Collections.<AvailObject>emptyList()
										: Collections.singletonList(
											primitiveAndFailure.tupleAt(2)),
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
	 *            What to do with the {@linkplain BlockNodeDescriptor block}.
	 */
	@InnerAccess void finishBlockThen (
		final ParserState afterStatements,
		final List<AvailObject> arguments,
		final int primitiveNumber,
		final List<AvailObject> statements,
		final AvailObject scopeOutsideBlock,
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
			if (stmt.isInstanceOfKind(DECLARATION_NODE.mostGeneralType()))
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
			&& statements.get(0).isInstanceOfKind(LABEL_NODE.mostGeneralType()))
		{
			final AvailObject labelNode = statements.get(0);
			final AvailObject labelType = labelNode.declaredType();
			final AvailObject implicitBlockType =
				FunctionTypeDescriptor.create(
					TupleDescriptor.fromCollection(argumentTypesList),
					lastStatementType.value,
					SetDescriptor.empty());
			final AvailObject implicitContType =
				ContinuationTypeDescriptor.forFunctionType(implicitBlockType);
			blockTypeGood = implicitContType.isSubtypeOf(labelType);
		}

		if (!blockTypeGood)
		{
			stateOutsideBlock.expected(
				"label's type to be a supertype of enclosing block's basic " +
				"type");
		}
		else if (thePrimitive == null)
		{
			parseOptionalBlockExceptionsClauseThen(
				stateOutsideBlock,
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
								lastStatementType.value,
								checkedExceptions);
						attempt(afterExceptions, continuation, blockNode);
					}
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
							TupleDescriptor.fromCollection(argumentTypesList),
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
						&& statements.get(0).kind().parseNodeKindIsUnder(
							LABEL_NODE))
					{
						final AvailObject labelNode = statements.get(0);
						final AvailObject labelType =
							labelNode.declaredType();
						final AvailObject contType =
							ContinuationTypeDescriptor.forFunctionType(
								explicitBlockType);
						blockTypeGood2 = contType.isSubtypeOf(labelType);
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
								}
							});
					}
					else
					{
						afterReturnType.expected(
							"label's type to be a supertype of enclosing "
							+ "block's declared type");
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

		if (start.peekToken(
			CARET,
			"optional block exceptions declaration"))
		{
			final ParserState afterColon = start.afterToken();
			parseMoreExceptionClausesThen (
				afterColon,
				SetDescriptor.empty(),
				continuation);
		}
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
			ObjectTypeDescriptor.meta(),
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
		final Con<AvailObject> newContinuation =
			new Con<AvailObject>("Optional leading argument send")
			{
				@Override
				public void value (
					final ParserState afterSubexpression,
					final AvailObject subexpression)
				{
					parseOptionalLeadingArgumentSendAfterThen(
						afterSubexpression,
						subexpression,
						continuation);
				}
			};
		parseLeadingKeywordSendThen(start, newContinuation);
		parseSimpleThen(start, newContinuation);
		parseBlockThen(start, newContinuation);
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
	 *            non-backquoted underscores and guillemet groups in the message
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
		final Con<AvailObject> continuation)
	{
		bundleTree.expand();
		final AvailObject complete = bundleTree.lazyComplete();
		final AvailObject incomplete = bundleTree.lazyIncomplete();
		final AvailObject caseInsensitive =
			bundleTree.lazyIncompleteCaseInsensitive();
		final AvailObject actions = bundleTree.lazyActions();
		final AvailObject prefilter = bundleTree.lazyPrefilterMap();
		final boolean anyComplete = complete.mapSize() > 0;
		final boolean anyIncomplete = incomplete.mapSize() > 0;
		final boolean anyCaseInsensitive = caseInsensitive.mapSize() > 0;
		final boolean anyActions = actions.mapSize() > 0;
		final boolean anyPrefilter = prefilter.mapSize() > 0;

		if (!(anyComplete
			|| anyIncomplete
			|| anyCaseInsensitive
			|| anyActions
			|| anyPrefilter))
		{
			return;
		}
		if (anyComplete
			&& firstArgOrNull == null
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
						initialTokenPosition,
						start,
						argsSoFar,
						entry.value,
						continuation);
				}
			}
		}
		if (anyIncomplete
			&& firstArgOrNull == null
			&& !start.atEnd())
		{
			final AvailObject keywordToken = start.peekToken();
			if (keywordToken.tokenType() == KEYWORD
					|| keywordToken.tokenType() == OPERATOR)
			{
				final AvailObject keywordString = keywordToken.string();
				if (incomplete.hasKey(keywordString))
				{
					final AvailObject subtree = incomplete.mapAt(keywordString);
					eventuallyDo(
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
									continuation);
							}
						},
						"Continue send after keyword: "
							+ keywordString.asNativeString(),
						start.afterToken().position);
				}
				else
				{
					expectedKeywordsOf(start, incomplete, false);
				}
			}
			else
			{
				expectedKeywordsOf(start, incomplete, false);
			}
		}
		if (anyCaseInsensitive
			&& firstArgOrNull == null
			&& !start.atEnd())
		{
			final AvailObject keywordToken = start.peekToken();
			if (keywordToken.tokenType() == KEYWORD
					|| keywordToken.tokenType() == OPERATOR)
			{
				final AvailObject keywordString =
					keywordToken.lowerCaseString();
				if (caseInsensitive.hasKey(keywordString))
				{
					final AvailObject subtree =
						caseInsensitive.mapAt(keywordString);
					eventuallyDo(
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
									continuation);
							}
						},
						"Continue send after keyword: "
							+ keywordString.asNativeString(),
						start.afterToken().position);
				}
				else
				{
					expectedKeywordsOf(start, caseInsensitive, true);
				}
			}
			else
			{
				expectedKeywordsOf(start, caseInsensitive, true);
			}
		}
		if (anyPrefilter)
		{
			final AvailObject latestArgument =
				argsSoFar.get(argsSoFar.size() - 1);
			if (latestArgument.isInstanceOfKind(SEND_NODE.mostGeneralType()))
			{
				final AvailObject methodName = latestArgument.method().name();
				if (prefilter.hasKey(methodName))
				{
					parseRestOfSendNode(
						start,
						prefilter.mapAt(methodName),
						firstArgOrNull,
						initialTokenPosition,
						argsSoFar,
						continuation);
					// Don't allow normal action processing, as it would ignore
					// the restriction which we've been so careful to prefilter.
					return;
				}
			}
		}
		if (anyActions)
		{
			for (final MapDescriptor.Entry entry : actions.mapIterable())
			{
				final AvailObject key = entry.key;
				final AvailObject value = entry.value;
				eventuallyDo(
					new Continuation0()
					{
						@Override
						public void value ()
						{
							runParsingInstructionThen(
								start,
								key.extractInt(),
								firstArgOrNull,
								argsSoFar,
								initialTokenPosition,
								value,
								continuation);
						}
					},
					"Continue with instruction " + key,
					start.position);
			}
		}
	}

	/**
	 * Execute one non-keyword-parsing instruction, then run the continuation.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param instruction
	 *            The {@linkplain MessageSplitter instruction} to execute.
	 * @param firstArgOrNull
	 *            Either the already-parsed first argument or null. If we're
	 *            looking for leading-argument message sends to wrap an
	 *            expression then this is not-null before the first argument
	 *            position is encountered, otherwise it's null and we should
	 *            reject attempts to start with an argument (before a keyword).
	 * @param argsSoFar
	 *            The message arguments that have been parsed so far.
	 * @param initialTokenPosition
	 *            The position at which parsing of this message started. If it
	 *            was parsed as a leading argument send (i.e., firtArgOrNull
	 *            started out non-null) then the position is of the token
	 *            following the first argument.
	 * @param successorTrees
	 *            The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *            MessageBundleTreeDescriptor bundle trees} at which to continue
	 *            parsing.
	 * @param continuation
	 *            What to do with a complete {@linkplain SendNodeDescriptor
	 *            message send}.
	 */
	void runParsingInstructionThen (
		final @NotNull ParserState start,
		final int instruction,
		final @NotNull AvailObject firstArgOrNull,
		final @NotNull List<AvailObject> argsSoFar,
		final @NotNull ParserState initialTokenPosition,
		final @NotNull AvailObject successorTrees,
		final @NotNull Con<AvailObject> continuation)
	{
		final ParsingOperation op = ParsingOperation.decode(instruction);
		switch (op)
		{
			case parseArgument:
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
							eventuallyDo(
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
											continuation);
									}
								},
								"Continue send after argument",
								afterArg.position);
						}
					});
				break;
			}
			case newList:
			{
				// Push an empty tuple node and continue.
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar =
					new ArrayList<AvailObject>(argsSoFar);
				final AvailObject newTupleNode = TupleNodeDescriptor.empty();
				newArgsSoFar.add(newTupleNode);
				eventuallyDo(
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
								continuation);
						}
					},
					"Continue send after push empty",
					start.position);
				break;
			}
			case appendArgument:
			{
				// Append the item that's the last thing to the tuple that's the
				// second last thing. Pop both and push the new tuple (the
				// original tuple must not change), then continue.
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar =
					new ArrayList<AvailObject>(argsSoFar);
				final AvailObject value =
					newArgsSoFar.remove(newArgsSoFar.size() - 1);
				final AvailObject oldNode =
					newArgsSoFar.remove(newArgsSoFar.size() - 1);
				final AvailObject tupleNode = oldNode.copyWith(value);
				newArgsSoFar.add(tupleNode);
				eventuallyDo(
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
								continuation);
						}
					},
					"Continue send after append",
					start.position);
				break;
			}
			case saveParsePosition:
			{
				// Push current parse position.
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar =
					new ArrayList<AvailObject>(argsSoFar);
				final AvailObject marker =
					MarkerNodeDescriptor.mutable().create();
				marker.markerValue(IntegerDescriptor.fromInt(start.position));
				newArgsSoFar.add(marker);
				eventuallyDo(
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
								continuation);
						}
					},
					"Continue send after push parse position",
					start.position);
				break;
			}
			case discardSavedParsePosition:
			{
				// Underpop saved parse position (from 2nd-to-top of stack).
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar =
					new ArrayList<AvailObject>(argsSoFar);
				final AvailObject marker =
					newArgsSoFar.remove(newArgsSoFar.size() - 2);
				assert marker.traversed().descriptor()
					instanceof MarkerNodeDescriptor;
				eventuallyDo(
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
								continuation);
						}
					},
					"Continue send after underpop saved position",
					start.position);
				break;
			}
			case ensureParseProgress:
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
				eventuallyDo(
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
								continuation);
						}
					},
					"Continue send after check parse progress",
					start.position);
				break;
			}
			case parseRawToken:
				// Parse a raw token and continue.
				assert successorTrees.tupleSize() == 1;
				parseRawTokenThen(
					start,
					new Con<AvailObject>(
						"Raw token for ellipsis (…) in some message")
					{
						@Override
						public void value (
							final ParserState afterToken,
							final AvailObject newToken)
						{
							final List<AvailObject> newArgsSoFar =
								new ArrayList<AvailObject>(argsSoFar);
							final AvailObject syntheticToken =
								LiteralTokenDescriptor.create(
									newToken.string(),
									newToken.start(),
									newToken.lineNumber(),
									SYNTHETIC_LITERAL,
									newToken);
							final AvailObject literalNode =
								LiteralNodeDescriptor.fromToken(syntheticToken);
							newArgsSoFar.add(literalNode);
							eventuallyDo(
								new Continuation0()
								{
									@Override
									public void value ()
									{
										parseRestOfSendNode(
											afterToken,
											successorTrees.tupleAt(1),
											null,
											initialTokenPosition,
											Collections.unmodifiableList(
												newArgsSoFar),
											continuation);
									}
								},
								"Continue send after raw token for ellipsis",
								afterToken.position);
						}
					});
				break;
			case pop:
			{
				// Pop the parse stack.
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar =
					new ArrayList<AvailObject>(argsSoFar);
				newArgsSoFar.remove(newArgsSoFar.size() - 1);
				eventuallyDo(
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
								continuation);
						}
					},
					"Continue send after pop",
					start.position);
				break;
			}
			case branch:
				// $FALL-THROUGH$
				// Fall through.  The successorTrees will be different
				// for the jump versus parallel-branch.
			case jump:
				for (final AvailObject successorTree : successorTrees)
				{
					eventuallyDo(
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
									continuation);
							}
						},
						"Continue send after branch or jump",
						start.position);
				}
				break;
			case parsePart:
				assert false
				: "parse-token instruction should not be dispatched";
				break;
			case checkArgument:
			{
				// CheckArgument.  An argument has just been parsed (and
				// pushed).  Make sure it satisfies any grammatical
				// restrictions.  The message bundle tree's lazy
				// prefilter map deals with that efficiently.
				assert successorTrees.tupleSize() == 1;
				eventuallyDo(
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
								continuation);
						}
					},
					"Continue send after copyArgumentForCheck",
					start.position);
				break;
			}
			case convert:
			{
				// Convert the argument.
				assert successorTrees.tupleSize() == 1;
				final List<AvailObject> newArgsSoFar =
					new ArrayList<AvailObject>(argsSoFar);
				final AvailObject target = newArgsSoFar.get(
					newArgsSoFar.size() - 1);
				final AvailObject replacement;
				switch (op.conversionRule(instruction))
				{
					case noConversion:
						replacement = target;
						break;
					case listToSize:
					{
						final AvailObject expressions =
							target.expressionsTuple();
						final AvailObject count = IntegerDescriptor.fromInt(
							expressions.tupleSize());
						final AvailObject token =
							LiteralTokenDescriptor.create(
								StringDescriptor.from(count.toString()),
								initialTokenPosition.peekToken().start(),
								initialTokenPosition.peekToken().lineNumber(),
								LITERAL,
								count);
						final AvailObject literalNode =
							LiteralNodeDescriptor.fromToken(token);
						replacement = literalNode;
						break;
					}
					case listToNonemptiness:
					{
						final AvailObject expressions =
							target.expressionsTuple();
						final AvailObject nonempty =
							AtomDescriptor.objectFromBoolean(
								expressions.tupleSize() > 0);
						final AvailObject token =
							LiteralTokenDescriptor.create(
								StringDescriptor.from(nonempty.toString()),
								initialTokenPosition.peekToken().start(),
								initialTokenPosition.peekToken().lineNumber(),
								LITERAL,
								nonempty);
						final AvailObject literalNode =
							LiteralNodeDescriptor.fromToken(token);
						replacement = literalNode;
						break;
					}
					default:
					{
						replacement = target;
						assert false : "Conversion rule not handled";
						break;
					}
				}
				newArgsSoFar.set(newArgsSoFar.size() - 1, replacement);
				eventuallyDo(
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
								continuation);
						}
					},
					"Continue send after conversion",
					start.position);
				break;
			}
			default:
				assert false : "Reserved parsing instruction";
		}
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
		stateAfterCall.expected(
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
	}

	/**
	 * Report that the parser was expecting one of several keywords. The
	 * keywords are keys of the {@linkplain MapDescriptor map} argument
	 * {@code incomplete}.
	 *
	 * @param where
	 *        Where the keywords were expected.
	 * @param incomplete
	 *        A map of partially parsed keywords, where the keys are the strings
	 *        that were expected at this position.
	 * @param caseInsensitive
	 *        {@code true} if the parsed keywords are case-insensitive, {@code
	 *        false} otherwise.
	 */
	private void expectedKeywordsOf (
		final @NotNull ParserState where,
		final @NotNull AvailObject incomplete,
		final boolean caseInsensitive)
	{
		where.expected(
			new Generator<String>()
			{
				@Override
				public String value ()
				{
					final StringBuilder builder = new StringBuilder(200);
					if (caseInsensitive)
					{
						builder.append(
							"one of the following case-insensitive internal "
							+ "keywords:");
					}
					else
					{
						builder.append(
							"one of the following internal keywords:");
					}
					final List<String> sorted = new ArrayList<String>(
						incomplete.mapSize());
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
		attempt(
			start,
			new Con<AvailObject>("Possible leading argument send")
			{
				@Override
				public void value (
					final ParserState afterCast,
					final AvailObject cast)
				{
					parseLeadingArgumentSendAfterThen(
						afterCast,
						cast,
						new Con<AvailObject>("Leading argument send")
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
			},
			node);
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
	 *            What to do with a tuple consisting of the parsed primitive
	 *            number and the failure declaration (or a tuple with just the
	 *            parsed primitive number if the primitive can't fail).
	 */
	void parseOptionalPrimitiveForArgCountThen (
		final ParserState start,
		final int argCount,
		final Con<AvailObject> continuation)
	{
		// Try it first without looking for the primitive declaration.
		attempt(
			start,
			continuation,
			TupleDescriptor.from(IntegerDescriptor.zero()));

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
				TupleDescriptor.from(
					IntegerDescriptor.fromInt(primitiveNumber)));
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
						TupleDescriptor.from(
							IntegerDescriptor.fromInt(primitiveNumber),
							declaration));
				}
			});
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
					new Con<AvailObject>("Argument expression")
					{
						@Override
						public void value (
							final ParserState afterArgument,
							final AvailObject argument)
						{
							attempt(afterArgument, continuation, argument);
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
			attempt(initialTokenPosition, continuation, firstArgOrNull);
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
	 * Parse a reference expression, which is an up arrow (↑) followed by a
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
		if (start.peekToken(UP_ARROW))
		{
			final ParserState afterUpArrow = start.afterToken();
			parseVariableUseWithExplanationThen(
				afterUpArrow,
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
								case PRIMITIVE_FAILURE_REASON:
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
								afterUpArrow.expected(
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
	 *        Where to parse.
	 * @param canHaveLabel
	 *        Whether the statements can start with a label declaration.
	 * @param argDecls
	 *        The enclosing block's argument declarations, or {@code null} if
	 *        there is no enclosing block.
	 * @param statements
	 *        The preceding list of statements.
	 * @param continuation
	 *        What to do with the resulting list of statements.
	 */
	void parseStatementsThen (
		final ParserState start,
		final boolean canHaveLabel,
		final List<AvailObject> argDecls,
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
					"end of statements, since the previous "
					+ "one has type ⊥");
				return;
			}
			if (!lastStatement.expressionType().equals(TOP.o()))
			{
				start.expected(
					new Generator<String>()
					{
						@Override
						public String value ()
						{
							return String.format(
								"statement \"%s\" to have type ⊤, not \"%s\"",
								lastStatement,
								lastStatement.expressionType());
						}
					});
				return;
			}
			start.expected("more statements or final expression");
		}
		else
		{
			start.expected("statements or final expression");
		}

		// Try for more statements.
		parseInnerStatement(
			start,
			canHaveLabel && statements.isEmpty(),
			argDecls,
			new Con<AvailObject>("Another statement")
			{
				@Override
				public void value (
					final ParserState afterStatement,
					final AvailObject newStatement)
				{
					if (newStatement.kind().parseNodeKindIsUnder(
						DECLARATION_NODE))
					{
						// Check for name collisions with declarations from the
						// same block.
						final AvailObject newName =
							newStatement.token().string();
						if (lookupDeclaration(start, newName) != null)
						{
							afterStatement.expected(
								"new declaration (\""
								+ newName.toString()
								+ "\") not to shadow an existing declaration");
							return;
						}
					}
					final List<AvailObject> newStatements =
						new ArrayList<AvailObject>(statements);
					newStatements.add(newStatement);
					parseStatementsThen(
						afterStatement,
						false,
						null,
						newStatements,
						continuation);
				}
			});

		// Try for a final value-yielding expression (with no trailing
		// semicolon).
		parseExpressionThen(
			start,
			new Con<AvailObject>("Final expression")
			{
				@Override
				public void value (
					final ParserState afterFinalExpression,
					final AvailObject finalExpression)
				{
					if (!finalExpression.expressionType().equals(TOP.o()))
					{
						final List<AvailObject> newStatements =
							new ArrayList<AvailObject>(statements);
						newStatements.add(finalExpression);
						attempt(
							afterFinalExpression,
							continuation,
							newStatements);
					}
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
		final AvailObject localDecl = lookupDeclaration(
			start,
			token.string());
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
			final AvailObject varUse = VariableUseNodeDescriptor.newUse(
				token,
				moduleVarDecl);
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
				VariableUseNodeDescriptor.newUse(token, moduleConstDecl);
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
