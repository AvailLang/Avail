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
import static com.avail.utility.PrefixSharingList.*;
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
					final @Nullable ParserState ignored,
					final @Nullable Con<AvailObject> whenFoundStatement)
				{
					assert whenFoundStatement != null;
					parseDeclarationThen(
						start,
						new Con<AvailObject>("Semicolon after declaration")
						{
							@Override
							public void value (
								final @Nullable ParserState afterDeclaration,
								final @Nullable AvailObject declaration)
							{
								assert afterDeclaration != null;
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
								final @Nullable ParserState afterAssignment,
								final @Nullable AvailObject assignment)
							{
								assert afterAssignment != null;
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
								final @Nullable ParserState afterExpression,
								final @Nullable AvailObject expression)
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
	 *        The enclosing block's argument declarations, or {@code null}.
	 * @param continuation
	 *        What to do with the unambiguous, parsed statement.
	 */
	void parseInnerStatement (
		final ParserState start,
		final boolean canBeLabel,
		final @Nullable List<AvailObject> argDecls,
		final Con<AvailObject> continuation)
	{
		parseDeclarationThen(
			start,
			new Con<AvailObject>("Semicolon after declaration")
			{
				@Override
				public void value (
					final @Nullable ParserState afterDeclaration,
					final @Nullable AvailObject declaration)
				{
					assert afterDeclaration != null;
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
					final @Nullable ParserState afterAssignment,
					final @Nullable AvailObject assignment)
				{
					assert afterAssignment != null;
					assert assignment != null;
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
					final @Nullable ParserState afterExpression,
					final @Nullable AvailObject expression)
				{
					assert afterExpression != null;
					assert expression != null;
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
						final @Nullable ParserState afterDeclaration,
						final @Nullable AvailObject label)
					{
						assert afterDeclaration != null;
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
					final @Nullable ParserState afterVar,
					final @Nullable AvailObject varUse)
				{
					assert afterVar != null;
					assert varUse != null;
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
					if (declaration.equalsNull())
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
								final @Nullable ParserState afterExpr,
								final @Nullable AvailObject expr)
							{
								assert afterExpr != null;
								assert expr != null;
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
	 *        The enclosing block's argument declarations, or {@code null}.
	 * @param continuation
	 *        What to do after parsing a label.
	 */
	void parseLabelThen (
		final ParserState start,
		final @Nullable List<AvailObject> argDecls,
		final Con<AvailObject> continuation)
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
					final @Nullable ParserState afterExpression,
					final @Nullable AvailObject returnType)
				{
					assert afterExpression != null;
					assert returnType != null;
					final List<AvailObject> argTypes =
						new ArrayList<AvailObject>(argDecls.size());
					for (final AvailObject decl : argDecls)
					{
						argTypes.add(decl.declaredType());
					}
					final AvailObject contType =
						ContinuationTypeDescriptor.forFunctionType(
							FunctionTypeDescriptor.create(
								TupleDescriptor.fromList(argTypes),
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
							InstanceMetaDescriptor.topMeta(),
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
							final @Nullable ParserState afterInitExpression,
							final @Nullable AvailObject initExpression)
						{
							assert afterInitExpression != null;
							assert initExpression != null;

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
			InstanceMetaDescriptor.topMeta(),
			new Con<AvailObject>("Type expression of var : type")
			{
				@Override
				public void value (
					final @Nullable ParserState afterType,
					final @Nullable AvailObject type)
				{
					assert afterType != null;
					assert type != null;

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
								final @Nullable ParserState afterInit,
								final @Nullable AvailObject initExpr)
							{
								assert afterInit != null;
								assert initExpr != null;

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
			InstanceMetaDescriptor.topMeta(),
			new Con<AvailObject>("Type expression of var : type")
			{
				@Override
				public void value (
					final @Nullable ParserState afterType,
					final @Nullable AvailObject type)
				{
					assert afterType != null;
					assert type != null;

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
						final @Nullable ParserState afterArgument,
						final @Nullable AvailObject arg)
					{
						assert afterArgument != null;
						assert arg != null;
						parseAdditionalBlockArgumentsAfterThen(
							afterArgument,
							append(argsSoFar, arg),
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
				argsSoFar);
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
					final @Nullable ParserState afterFirstArg,
					final @Nullable AvailObject firstArg)
				{
					assert afterFirstArg != null;
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
			InstanceMetaDescriptor.topMeta(),
			new Con<AvailObject>("Type of block argument")
			{
				@Override
				public void value (
					final @Nullable ParserState afterArgType,
					final @Nullable AvailObject type)
				{
					assert afterArgType != null;
					assert type != null;

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
		final AvailObject scopeOutsideBlock = start.scopeMap;
		final AvailObject firstToken = start.peekToken();
		if (!start.peekToken(OPEN_SQUARE))
		{
			// Don't suggest a block was expected here unless at least the "["
			// was present.
			return;
		}
		parseBlockArgumentsThen(
			start.afterToken(),
			new Con<List<AvailObject>>("Block arguments")
			{
				@Override
				public void value (
					final @Nullable ParserState afterArguments,
					final @Nullable List<AvailObject> arguments)
				{
					assert afterArguments != null;
					assert arguments != null;

					parseOptionalPrimitiveForArgCountThen(
						afterArguments,
						arguments.size(),
						new Con<AvailObject>("Optional primitive declaration")
						{
							@Override
							public void value (
								final @Nullable
									ParserState afterOptionalPrimitive,
								final @Nullable AvailObject primitiveAndFailure)
							{
								assert afterOptionalPrimitive != null;
								assert primitiveAndFailure != null;

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
										firstToken,
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
											final @Nullable
												ParserState afterStatements,
											final @Nullable
												List<AvailObject> statements)
										{
											assert afterStatements != null;
											assert statements != null;
											finishBlockThen(
												afterStatements,
												arguments,
												primitive,
												statements,
												scopeOutsideBlock,
												firstToken,
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
	 * @param firstToken
	 *            The first token participating in the block.
	 * @param continuation
	 *            What to do with the {@linkplain BlockNodeDescriptor block}.
	 */
	@InnerAccess void finishBlockThen (
		final ParserState afterStatements,
		final List<AvailObject> arguments,
		final int primitiveNumber,
		final List<AvailObject> statements,
		final AvailObject scopeOutsideBlock,
		final AvailObject firstToken,
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
					TupleDescriptor.fromList(argumentTypesList),
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
						final @Nullable ParserState afterExceptions,
						final @Nullable AvailObject checkedExceptions)
					{
						assert afterExceptions != null;
						assert checkedExceptions != null;
						final AvailObject blockNode =
							BlockNodeDescriptor.newBlockNode(
								arguments,
								primitiveNumber,
								statements,
								lastStatementType.value,
								checkedExceptions,
								firstToken.lineNumber());
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
			InstanceMetaDescriptor.topMeta(),
			new Con<AvailObject>("Block return type declaration")
			{
				@Override
				public void value (
					final @Nullable ParserState afterReturnType,
					final @Nullable AvailObject returnType)
				{
					assert afterReturnType != null;
					assert returnType != null;
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
									final @Nullable ParserState afterExceptions,
									final @Nullable
										AvailObject checkedExceptions)
								{
									assert afterExceptions != null;
									assert checkedExceptions != null;
									final AvailObject blockNode =
										BlockNodeDescriptor.newBlockNode(
											arguments,
											primitiveNumber,
											statements,
											returnType,
											checkedExceptions,
											firstToken.lineNumber());
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
					final @Nullable ParserState afterException,
					final @Nullable AvailObject exceptionType)
				{
					assert afterException != null;
					assert exceptionType != null;
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
		parseBlockThen(start, newContinuation);
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
					final @Nullable ParserState afterDeclaration,
					final @Nullable AvailObject declaration)
				{
					assert afterDeclaration != null;
					assert declaration != null;

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
						final @Nullable ParserState afterVar,
						final @Nullable AvailObject var)
					{
						assert afterVar != null;
						assert var != null;

						final AvailObject declaration = var.declaration();
						String suffix = null;
						if (declaration.equalsNull())
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
		final @Nullable List<AvailObject> argDecls,
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
					final @Nullable ParserState afterStatement,
					final @Nullable AvailObject newStatement)
				{
					assert afterStatement != null;
					assert newStatement != null;

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
					final @Nullable ParserState afterFinalExpression,
					final @Nullable AvailObject finalExpression)
				{
					assert afterFinalExpression != null;
					assert finalExpression != null;
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
					variableObject,
					NullDescriptor.nullObject());
			final AvailObject varUse = VariableUseNodeDescriptor.newUse(
				token,
				moduleVarDecl);
			attempt(afterVar, continuation, varUse);
			return;
		}
		if (module.constantBindings().hasKey(varName))
		{
			final AvailObject variableObject = module.constantBindings().mapAt(
				varName);
			final AvailObject moduleConstDecl =
				DeclarationNodeDescriptor.newModuleConstant(
					token,
					variableObject,
					NullDescriptor.nullObject());
			final AvailObject varUse = VariableUseNodeDescriptor.newUse(
				token,
				moduleConstDecl);
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
