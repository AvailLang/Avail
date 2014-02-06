/**
 * AvailSystemCompiler.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import static java.util.Arrays.asList;
import java.util.*;
import com.avail.annotations.*;
import com.avail.builder.*;
import com.avail.compiler.AbstractAvailCompiler.ParserState;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Flag;
import com.avail.utility.*;
import com.avail.utility.evaluation.*;

/**
 * I parse a source file to create a {@linkplain ModuleDescriptor module}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class AvailSystemCompiler
extends AbstractAvailCompiler
{
	/**
	 * Construct a new {@link AvailSystemCompiler}.
	 *
	 * @param moduleName
	 *        The {@link ResolvedModuleName} of the module to compile.
	 * @param source
	 *        The {@link String} of source code to be parsed.
	 * @param tokens
	 *        The list of {@linkplain TokenDescriptor tokens} to be parsed.
	 * @param problemHandler
	 *        The {@linkplain ProblemHandler problem handler}.
	 */
	public AvailSystemCompiler (
		final ResolvedModuleName moduleName,
		final String source,
		final List<A_Token> tokens,
		final ProblemHandler problemHandler)
	{
		super(moduleName, source, tokens, problemHandler);
	}

	@Override
	boolean isSystemCompiler ()
	{
		return true;
	}

	/**
	 * Return a new {@linkplain ParserState parser state} like the given one,
	 * but with the given declaration added.
	 *
	 * @param originalState
	 *        The parser state to start with.
	 * @param declaration
	 *        The {@linkplain DeclarationNodeDescriptor declaration} to add
	 *        to the map of visible bindings.
	 * @return The new parser state including the declaration.
	 */
	ParserState withDeclaration (
		final ParserState originalState,
		final A_Phrase declaration)
	{
		final A_Map clientMap = originalState.clientDataMap;
		final A_Atom scopeMapKey = AtomDescriptor.compilerScopeMapKey();
		final A_Map scopeMap = clientMap.hasKey(scopeMapKey)
			? clientMap.mapAt(scopeMapKey)
			: MapDescriptor.empty();
		final A_String name = declaration.token().string();
		assert !scopeMap.hasKey(name);
		final A_Map newScopeMap = scopeMap.mapAtPuttingCanDestroy(
			name,
			declaration,
			false);
		final A_Map newClientMap = clientMap.mapAtPuttingCanDestroy(
			scopeMapKey,
			newScopeMap,
			false);
		newClientMap.makeImmutable();
		return new ParserState(
			originalState.position,
			newClientMap);
	}

	/**
	 * Look up a local declaration that has the given name, or null if not
	 * found.
	 *
	 * @param state
	 *        The parser state in which to look up a declaration.
	 * @param name
	 *        The name of the declaration for which to look.
	 * @return The declaration or {@code null}.
	 */
	@Override
	@Nullable A_Phrase lookupLocalDeclaration (
		final ParserState state,
		final A_String name)
	{
		final A_Map clientMap = state.clientDataMap;
		final A_Atom scopeMapKey = AtomDescriptor.compilerScopeMapKey();
		final A_Map scopeMap = clientMap.hasKey(scopeMapKey)
			? clientMap.mapAt(scopeMapKey)
			: MapDescriptor.empty();
		if (scopeMap.hasKey(name))
		{
			return scopeMap.mapAt(name);
		}
		return null;
	}

	/**
	 * Parse a top-level statement.  This is the <em>only</em> boundary for the
	 * backtracking grammar.  The success continuation will be invoked at most
	 * once (with the resulting {@linkplain ParseNodeDescriptor phrase}), and
	 * only if the top-level statement had a single interpretation.  Otherwise
	 * the failure will be reported and the afterFail continuation will run.
	 */
	@Override
	void parseOutermostStatement (
		final ParserState start,
		final Con<A_Phrase> success,
		final Continuation0 afterFail)
	{
		tryIfUnambiguousThen(
			start,
			new Con<Con<A_Phrase>>("Detect ambiguity")
			{
				@Override
				public void value (
					final @Nullable ParserState realStart,
					final @Nullable Con<A_Phrase> whenFoundStatement)
				{
					assert realStart != null;
					assert whenFoundStatement != null;
					parseDeclarationThen(
						realStart,
						new Con<A_Phrase>("Semicolon after declaration")
						{
							@Override
							public void value (
								final @Nullable ParserState afterDeclaration,
								final @Nullable A_Phrase declaration)
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
						realStart,
						new Con<A_Phrase>("Semicolon after assignment")
						{
							@Override
							public void value (
								final @Nullable ParserState afterAssignment,
								final @Nullable A_Phrase assignment)
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
						realStart,
						new Con<A_Phrase>("Semicolon after expression")
						{
							@SuppressWarnings("null")
							@Override
							public void value (
								final @Nullable ParserState afterExpression,
								final @Nullable A_Phrase expression)
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
			success,
			afterFail);
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
		final @Nullable List<A_Phrase> argDecls,
		final Con<A_Phrase> continuation)
	{
		parseDeclarationThen(
			start,
			new Con<A_Phrase>("Semicolon after declaration")
			{
				@Override
				public void value (
					final @Nullable ParserState afterDeclaration,
					final @Nullable A_Phrase declaration)
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
			new Con<A_Phrase>("Semicolon after assignment")
			{
				@Override
				public void value (
					final @Nullable ParserState afterAssignment,
					final @Nullable A_Phrase assignment)
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
			new Con<A_Phrase>("Semicolon after expression")
			{
				@Override
				public void value (
					final @Nullable ParserState afterExpression,
					final @Nullable A_Phrase expression)
				{
					assert afterExpression != null;
					assert expression != null;
					if (!afterExpression.peekToken(
						SEMICOLON,
						"; to end statement"))
					{
						return;
					}
					if (expression.expressionType().isTop())
					{
						continuation.value(
							afterExpression.afterToken(),
							expression);
					}
					else
					{
						afterExpression.expected(
							asList(expression.expressionType()),
							new Transformer1<List<String>, String>()
							{
								@Override
								public @Nullable String value (
									final @Nullable List<String> list)
								{
									assert list != null;
									return String.format(
										"statement to have type ⊤, not %s",
										list.get(0));
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
				new Con<A_Phrase>("Semicolon after label")
				{
					@Override
					public void value (
						final @Nullable ParserState afterDeclaration,
						final @Nullable A_Phrase label)
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
		final Con<A_Phrase> continuation)
	{
		if (start.peekToken().tokenType() != KEYWORD)
		{
			// Don't suggest it's an assignment attempt with no evidence.
			return;
		}
		parseVariableUseWithExplanationThen(
			start,
			"for an assignment",
			new Con<A_Phrase>("Variable use for assignment")
			{
				@Override
				public void value (
					final @Nullable ParserState afterVar,
					final @Nullable A_Phrase varUse)
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
					final Mutable<A_Type> varType =
						new Mutable<A_Type>(NilDescriptor.nil());
					final A_Phrase declaration = varUse.declaration();
					boolean ok = false;
					if (declaration.equalsNil())
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
						new Con<A_Phrase>(
							"Expression for right side of assignment")
						{
							@Override
							public void value (
								final @Nullable ParserState afterExpr,
								final @Nullable A_Phrase expr)
							{
								assert afterExpr != null;
								assert expr != null;
								if (!afterExpr.peekToken(SEMICOLON))
								{
									afterExpr.expected(
										"; to end assignment statement");
									return;
								}
								if (expr.expressionType().isBottom())
								{
									afterExpr.expected(
										"assignment expression to have a type "
										+ "other than ⊥");
									return;
								}
								if (expr.expressionType().isSubtypeOf(
									varType.value))
								{
									final A_Phrase assignment =
										AssignmentNodeDescriptor.from(
											varUse, expr, false);
									attempt(
										afterExpr,
										continuation,
										assignment);
								}
								else
								{
									afterExpr.expected(
										asList(
											expr.expressionType(),
											varType.value),
										new Transformer1<List<String>, String>()
										{
											@Override
											public @Nullable String value (
												final @Nullable
													List<String> list)
											{
												assert list != null;
												return String.format(
													"assignment expression's "
													+ "type (%s) to match "
													+ "variable type (%s)",
													list.get(0),
													list.get(1));
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
		final @Nullable List<A_Phrase> argDecls,
		final Con<A_Phrase> continuation)
	{
		assert argDecls != null;
		if (!start.peekToken(
			DOLLAR_SIGN,
			"label statement starting with \"$\""))
		{
			return;
		}
		final ParserState afterDollar = start.afterToken();
		final A_Token token = afterDollar.peekToken();
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
					final List<A_Type> argTypes =
						new ArrayList<>(argDecls.size());
					for (final A_Phrase decl : argDecls)
					{
						argTypes.add(decl.declaredType());
					}
					final A_Type contType =
						ContinuationTypeDescriptor.forFunctionType(
							FunctionTypeDescriptor.create(
								TupleDescriptor.fromList(argTypes),
								returnType));
					final A_Phrase label =
						DeclarationNodeDescriptor.newLabel(
							token,
							contType);
					if (lookupLocalDeclaration(afterExpression, token.string())
						!= null)
					{
						afterExpression.expected(
							"label name not to shadow"
							+ " another declaration");
						return;
					}
					final ParserState afterDeclaration =
						withDeclaration(afterExpression, label);
					attempt(afterDeclaration, continuation, label);
				}
			};
		final ParserState afterName = afterDollar.afterToken();
		if (afterName.peekToken(
			COLON,
			"colon for label's return type declaration"))
		{
			final ParserState afterColon = afterName.afterToken();
			workUnitDo(
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
				afterColon);
		}
		else
		{
			workUnitDo(
				new Continuation0()
				{
					@Override
					public void value ()
					{
						finishLabel.value(
							afterName,
							(AvailObject)BottomTypeDescriptor.bottom());
					}
				},
				"Default label return type",
				afterName);
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
		final Con<A_Phrase> continuation)
	{
		final A_Token localName = start.peekToken();
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
					new Con<A_Phrase>("Complete var ::= expr")
					{
						@Override
						public void value (
							final @Nullable ParserState afterInitExpression,
							final @Nullable A_Phrase initExpression)
						{
							assert afterInitExpression != null;
							assert initExpression != null;

							if (lookupLocalDeclaration(
									afterInitExpression,
									localName.string())
								!= null)
							{
								afterInitExpression.expected(
									"constant name not to shadow another"
									+ " declaration");
								return;
							}
							final A_Type expressionType =
								initExpression.expressionType();
							if (expressionType.isTop()
								|| expressionType.isBottom())
							{
								afterInitExpression.expected(
									asList(expressionType),
									new Transformer1<List<String>, String>()
									{
										@Override
										public @Nullable String value (
											final @Nullable List<String> list)
										{
											assert list != null;
											return String.format(
												"constant expression to have a "
												+ "type other than %s",
												list.get(0));
										}
									});
								return;
							}
							final A_Phrase constantDeclaration =
								DeclarationNodeDescriptor.newConstant(
									localName,
									initExpression);
							attempt(
								withDeclaration(
									afterInitExpression,
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

					if (type.isTop() || type.isBottom())
					{
						afterType.expected(
							"a type for the variable other than "
							+ type.toString());
						return;
					}
					// Try the simple declaration... var : type;
					if (lookupLocalDeclaration(afterType, localName.string())
						!= null)
					{
						afterType.expected(
							"variable name not to shadow another declaration");
					}
					else
					{
						final A_Phrase simpleDeclaration =
							DeclarationNodeDescriptor.newVariable(
								localName,
								type);
						attempt(
							withDeclaration(afterType, simpleDeclaration),
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
						new Con<A_Phrase>("After expr of var : type := expr")
						{
							@Override
							public void value (
								final @Nullable ParserState afterInit,
								final @Nullable A_Phrase initExpr)
							{
								assert afterInit != null;
								assert initExpr != null;

								if (initExpr.expressionType().isTop())
								{
									afterInit.expected(
										"initializing expression to have a "
										+ "type other than ⊤");
									return;
								}
								if (initExpr.expressionType().isBottom())
								{
									afterInit.expected(
										"initializing expression to have a "
										+ "type other than ⊥");
									return;
								}
								if (initExpr.expressionType().isSubtypeOf(type))
								{
									final A_Phrase initDecl =
										DeclarationNodeDescriptor.newVariable(
											localName,
											type,
											initExpr);
									if (lookupLocalDeclaration(
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
										withDeclaration(afterInit, initDecl),
										continuation,
										initDecl);
								}
								else
								{
									afterInit.expected(
										asList(
											initExpr.expressionType(),
											type),
										new Transformer1<List<String>, String>()
										{
											@Override
											public @Nullable String value (
												final @Nullable
													List<String> list)
											{
												assert list != null;
												return String.format(
													"initializing expression's "
													+ "type (%s) to agree with "
													+ "declared type (%s)",
													list.get(0),
													list.get(1));
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
		final Con<A_Phrase> continuation)
	{
		final A_Token localName = start.peekToken();
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

					if (type.isTop() || type.isBottom())
					{
						afterType.expected(
							asList(type),
							new Transformer1<List<String>, String>()
							{
								@Override
								public @Nullable String value (
									final @Nullable List<String> list)
								{
									assert list != null;
									return String.format(
										"a type for the variable other "
										+ "than %s",
										list.get(0));
								}
							});
						return;
					}
					if (lookupLocalDeclaration(afterType, localName.string())
						!= null)
					{
						afterType.expected(
							"primitive failure variable not to shadow"
							+ " another declaration");
						return;
					}
					final A_Phrase declaration =
						DeclarationNodeDescriptor.newPrimitiveFailureVariable(
							localName,
							type);
					attempt(
						withDeclaration(afterType, declaration),
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
		final List<A_Phrase> argsSoFar,
		final Con<List<A_Phrase>> continuation)
	{
		if (start.peekToken(COMMA, "comma and more block arguments"))
		{
			parseBlockArgumentThen(
				start.afterToken(),
				new Con<A_Phrase>("Additional block argument")
				{
					@Override
					public void value (
						final @Nullable ParserState afterArgument,
						final @Nullable A_Phrase arg)
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
			"comma and more block arguments or a vertical bar"))
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
		final Con<List<A_Phrase>> continuation)
	{
		// Try it with no arguments.
		attempt(start, continuation, Collections.<A_Phrase>emptyList());
		parseBlockArgumentThen(
			start,
			new Con<A_Phrase>("Block argument")
			{
				@Override
				public void value (
					final @Nullable ParserState afterFirstArg,
					final @Nullable A_Phrase firstArg)
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
		final Con<A_Phrase> continuation)
	{
		final A_Token localName = start.peekToken();
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

					if (type.isTop() || type.isBottom())
					{
						afterArgType.expected(
							asList(type),
							new Transformer1<List<String>, String>()
							{
								@Override
								public @Nullable String value (
									final @Nullable List<String> list)
								{
									assert list != null;
									return
										"a type for the argument other than "
										+ list.get(0);
								}
							});
					}
					else
					{
						if (lookupLocalDeclaration(
								afterArgType,
								localName.string())
							!= null)
						{
							afterArgType.expected(
								"block argument name not to shadow another"
								+ " declaration");
							return;
						}
						final A_Phrase decl =
							DeclarationNodeDescriptor.newArgument(
								localName,
								type);
						attempt(
							withDeclaration(afterArgType, decl),
							continuation,
							decl);
					}
				}
			});
	}

	/**
	 * Parse a block (a function).
	 *
	 * @param startOfBlock
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do with the parsed block.
	 */
	private void parseBlockThen (
		final ParserState startOfBlock,
		final Con<A_Phrase> continuation)
	{
		if (!startOfBlock.peekToken(OPEN_SQUARE))
		{
			// Don't suggest a block was expected here unless at least the "["
			// was present.
			return;
		}
		parseBlockArgumentsThen(
			startOfBlock.afterToken(),
			new Con<List<A_Phrase>>("Block arguments")
			{
				@Override
				public void value (
					final @Nullable ParserState afterArguments,
					final @Nullable List<A_Phrase> arguments)
				{
					assert afterArguments != null;
					assert arguments != null;

					parseOptionalPrimitiveForArgCountThen(
						afterArguments,
						arguments.size(),
						new Con<A_Tuple>("Optional primitive declaration")
						{
							@Override
							public void value (
								final @Nullable
									ParserState afterOptionalPrimitive,
								final @Nullable A_Tuple primitiveAndFailure)
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
									Primitive.byPrimitiveNumberOrNull(
										primitive);
								if (thePrimitive != null
									&& thePrimitive.hasFlag(Flag.CannotFail))
								{
									assert primitiveAndFailure.tupleSize() == 1;
									finishBlockThen(
										afterOptionalPrimitive,
										arguments,
										primitive,
										Collections.<A_Phrase>emptyList(),
										startOfBlock,
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
										? Collections.<A_Phrase>emptyList()
										: Collections.<A_Phrase>singletonList(
											primitiveAndFailure.tupleAt(2)),
									new Con<List<A_Phrase>>(
										"Block statements")
									{
										@Override
										public void value (
											final @Nullable
												ParserState afterStatements,
											final @Nullable
												List<A_Phrase> statements)
										{
											assert afterStatements != null;
											assert statements != null;
											finishBlockThen(
												afterStatements,
												arguments,
												primitive,
												statements,
												startOfBlock,
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
	 * @param startOfBlock
	 *            The parser state at the start of the block.
	 * @param continuation
	 *            What to do with the {@linkplain BlockNodeDescriptor block}.
	 */
	@InnerAccess void finishBlockThen (
		final ParserState afterStatements,
		final List<A_Phrase> arguments,
		final int primitiveNumber,
		final List<A_Phrase> statements,
		final ParserState startOfBlock,
		final Con<A_Phrase> continuation)
	{
		if (!afterStatements.peekToken(
			CLOSE_SQUARE,
			"close bracket (']') to end block"))
		{
			return;
		}

		final ParserState afterClose = afterStatements.afterToken();
		final Primitive thePrimitive =
			Primitive.byPrimitiveNumberOrNull(primitiveNumber);
		if (statements.isEmpty()
			&& thePrimitive != null
			&& !thePrimitive.hasFlag(Flag.CannotFail))
		{
			afterClose.expected(
				"one or more statements to follow fallible "
				+ "primitive declaration ");
			return;
		}

		final Mutable<A_Type> lastStatementType =
			new Mutable<A_Type>(NilDescriptor.nil());
		if (statements.size() > 0)
		{
			final A_Phrase stmt = statements.get(statements.size() - 1);
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
			startOfBlock.clientDataMap);

		final List<A_Type> argumentTypesList =
			new ArrayList<>(arguments.size());
		for (final A_Phrase argument : arguments)
		{
			argumentTypesList.add(argument.declaredType());
		}
		boolean blockTypeGood = true;
		if (statements.size() > 0
			&& statements.get(0).isInstanceOfKind(LABEL_NODE.mostGeneralType()))
		{
			final A_Phrase labelNode = statements.get(0);
			final A_Type labelType = labelNode.declaredType();
			final A_Type implicitBlockType =
				FunctionTypeDescriptor.create(
					TupleDescriptor.fromList(argumentTypesList),
					lastStatementType.value,
					SetDescriptor.empty());
			final A_Type implicitContType =
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
				new Con<A_Set>("Block checked exceptions")
				{
					@Override
					public void value(
						final @Nullable ParserState afterExceptions,
						final @Nullable A_Set checkedExceptions)
					{
						assert afterExceptions != null;
						assert checkedExceptions != null;
						final A_Phrase blockNode =
							BlockNodeDescriptor.newBlockNode(
								arguments,
								primitiveNumber,
								statements,
								lastStatementType.value,
								checkedExceptions,
								startOfBlock.peekToken().lineNumber());
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
					final A_Type explicitBlockType =
						FunctionTypeDescriptor.create(
							TupleDescriptor.fromList(argumentTypesList),
							returnType);
					if (thePrimitive != null)
					{
						final A_Type intrinsicType =
							thePrimitive.blockTypeRestriction();
						if (!intrinsicType.isSubtypeOf(explicitBlockType))
						{
							afterReturnType.expected(
								asList(explicitBlockType, intrinsicType),
								new Transformer1<List<String>, String>()
								{
									@Override
									public @Nullable
									String value (
										final @Nullable List<String> list)
									{
										assert list != null;
										return String.format(
											"block type (%s) to agree with "
											+ "primitive %s's intrinsic type "
											+ "(%s)",
											list.get(0),
											thePrimitive.name(),
											list.get(1));
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
							asList(lastStatementType.value, returnType),
							new Transformer1<List<String>, String>()
							{
								@Override
								public @Nullable String value (
									final @Nullable List<String> list)
								{
									assert list != null;
									return String.format(
										"last statement's type (%s) "
										+ "to agree with block's declared "
										+ "result type (%s)",
										list.get(0),
										list.get(1));
								}
							});
						return;
					}

					boolean blockTypeGood2 = true;
					if (statements.size() > 0
						&& statements.get(0).kind().parseNodeKindIsUnder(
							LABEL_NODE))
					{
						final A_Phrase labelNode = statements.get(0);
						final A_Type labelType = labelNode.declaredType();
						final A_Type contType =
							ContinuationTypeDescriptor.forFunctionType(
								explicitBlockType);
						blockTypeGood2 = contType.isSubtypeOf(labelType);
					}
					if (blockTypeGood2)
					{
						parseOptionalBlockExceptionsClauseThen(
							afterReturnType,
							new Con<A_Set>("Block checked exceptions")
							{
								@Override
								public void value(
									final @Nullable ParserState afterExceptions,
									final @Nullable A_Set checkedExceptions)
								{
									assert afterExceptions != null;
									assert checkedExceptions != null;
									final A_Phrase blockNode =
										BlockNodeDescriptor.newBlockNode(
											arguments,
											primitiveNumber,
											statements,
											returnType,
											checkedExceptions,
											startOfBlock.peekToken().lineNumber());
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
		final Con<A_Set> continuation)
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
		final A_Set exceptionsAlready,
		final Con<A_Set> continuation)
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
					final A_Set newExceptionSet =
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
		final Con<A_Phrase> continuation)
	{
		final Con<A_Phrase> newContinuation =
			new Con<A_Phrase>("Optional leading argument send")
			{
				@Override
				public void value (
					final @Nullable ParserState afterSubexpression,
					final @Nullable A_Phrase subexpression)
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
		final List<A_Phrase> argumentExpressions,
		final A_Bundle bundle,
		final Con<A_Phrase> continuation)
	{
		stateAfterCall.expected(new Describer()
		{
			@Override
			public void describeThen (
				final @Nullable Continuation1<String> c)
			{
				assert c != null;
				c.value(
					"something other than an invocation of the macro "
					+ bundle.message().atomName());
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
		final Con<A_Tuple> continuation)
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
		final A_Token token = afterPrimitiveKeyword.peekToken();
		if (token.tokenType() != LITERAL
				|| !token.literal().isInstanceOfKind(
					IntegerRangeTypeDescriptor.unsignedShorts())
				|| token.literal().extractInt() == 0)
		{
			afterPrimitiveKeyword.expected(
				"A non-zero unsigned short [1..65535] "
				+ "after the Primitive keyword");
			return;
		}
		final int primitiveNumber = token.literal().extractInt();
		if (!Primitive.supportsPrimitive(primitiveNumber))
		{
			afterPrimitiveKeyword.expected(
				"a supported primitive number, not #"
				+ Integer.toString(primitiveNumber));
			return;
		}

		final Primitive prim =
			Primitive.byPrimitiveNumberOrNull(primitiveNumber);
		assert prim != null;
		if (!Primitive.primitiveAcceptsThisManyArguments(
			primitiveNumber, argCount))
		{
			afterPrimitiveKeyword.expected(new Describer()
			{
				@Override
				public void describeThen (final Continuation1<String> c)
				{
					c.value(String.format(
						"Primitive (%s) to be passed %d arguments, not %d",
						prim.name(),
						prim.argCount(),
						argCount));
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
			new Con<A_Phrase>("after declaring primitive failure variable")
			{
				@Override
				public void value (
					final @Nullable ParserState afterDeclaration,
					final @Nullable A_Phrase declaration)
				{
					assert afterDeclaration != null;
					assert declaration != null;

					if (!prim.failureVariableType().isSubtypeOf(
						declaration.declaredType()))
					{
						afterDeclaration.expected(
							asList(prim.failureVariableType()),
							new Transformer1<List<String>, String>()
							{
								@Override
								public String value (
									final @Nullable List<String> list)
								{
									assert list != null;
									return String.format(
										"primitive #%s's failure variable to "
										+ "be able to hold values of type (%s)",
										primitiveNumber,
										list.get(0));
								}
							});
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
		final Con<A_Phrase> continuation)
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

		// Try a reference: ↑var.
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
		final Con<A_Phrase> continuation)
	{
		if (start.peekToken(UP_ARROW))
		{
			final ParserState afterUpArrow = start.afterToken();
			parseVariableUseWithExplanationThen(
				afterUpArrow,
				"in reference expression",
				new Con<A_Phrase>("Variable for reference")
				{
					@Override
					public void value (
						final @Nullable ParserState afterVar,
						final @Nullable A_Phrase var)
					{
						assert afterVar != null;
						assert var != null;

						final A_Phrase declaration = var.declaration();
						String suffix = null;
						if (declaration.equalsNil())
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
		final @Nullable List<A_Phrase> argDecls,
		final List<A_Phrase> statements,
		final Con<List<A_Phrase>> continuation)
	{
		// Try it with the current list of statements.
		attempt(start, continuation, statements);

		// See if more statements would be legal.
		if (statements.size() > 0)
		{
			final A_Phrase lastStatement =
				statements.get(statements.size() - 1);
			if (lastStatement.expressionType().isBottom())
			{
				start.expected(
					"end of statements, since the previous "
					+ "one has type ⊥");
				return;
			}
			if (!lastStatement.expressionType().isTop())
			{
				start.expected(
					asList(lastStatement, lastStatement.expressionType()),
					new Transformer1<List<String>, String>()
					{
						@Override
						public @Nullable String value (
							final @Nullable List<String> list)
						{
							assert list != null;
							return String.format(
								"statement \"%s\" to have type ⊤, not \"%s\"",
								list.get(0),
								list.get(1));
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
			new Con<A_Phrase>("Another statement")
			{
				@Override
				public void value (
					final @Nullable ParserState afterStatement,
					final @Nullable A_Phrase newStatement)
				{
					assert afterStatement != null;
					assert newStatement != null;

					if (newStatement.kind().parseNodeKindIsUnder(
						DECLARATION_NODE))
					{
						// Check for name collisions with declarations from the
						// same block.
						final A_String newName =
							newStatement.token().string();
						if (lookupLocalDeclaration(start, newName) != null)
						{
							afterStatement.expected(
								"new declaration (\""
								+ newName.toString()
								+ "\") not to shadow an existing declaration");
							return;
						}
					}
					final List<A_Phrase> newStatements =
						new ArrayList<>(statements);
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
			new Con<A_Phrase>("Final expression")
			{
				@Override
				public void value (
					final @Nullable ParserState afterFinalExpression,
					final @Nullable A_Phrase finalExpression)
				{
					assert afterFinalExpression != null;
					assert finalExpression != null;
					if (!finalExpression.expressionType().isTop())
					{
						final List<A_Phrase> newStatements =
							new ArrayList<>(statements);
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
		final Con<A_Phrase> continuation)
	{
		final A_Token token = start.peekToken();
		if (token.tokenType() != KEYWORD)
		{
			return;
		}
		final ParserState afterVar = start.afterToken();
		// First check if it's in a block scope...
		final A_Phrase localDecl = lookupLocalDeclaration(
			start,
			token.string());
		if (localDecl != null)
		{
			final A_Phrase varUse = VariableUseNodeDescriptor.newUse(
				token,
				localDecl);
			attempt(afterVar, continuation, varUse);
			// Variables in inner scopes HIDE module variables.
			return;
		}
		// Not in a block scope. See if it's a module variable or module
		// constant...
		final A_String varName = token.string();
		if (module.variableBindings().hasKey(varName))
		{
			final A_BasicObject variableObject = module.variableBindings().mapAt(
				varName);
			final A_Phrase moduleVarDecl =
				DeclarationNodeDescriptor.newModuleVariable(
					token,
					variableObject,
					NilDescriptor.nil());
			final A_Phrase varUse = VariableUseNodeDescriptor.newUse(
				token,
				moduleVarDecl);
			attempt(afterVar, continuation, varUse);
			return;
		}
		if (module.constantBindings().hasKey(varName))
		{
			final A_BasicObject variableObject =
				module.constantBindings().mapAt(varName);
			final A_Phrase moduleConstDecl =
				DeclarationNodeDescriptor.newModuleConstant(
					token,
					variableObject,
					NilDescriptor.nil());
			final A_Phrase varUse = VariableUseNodeDescriptor.newUse(
				token,
				moduleConstDecl);
			attempt(afterVar, continuation, varUse);
			return;
		}
		start.expected(
			new Describer()
			{
				@Override
				public void describeThen (final Continuation1<String> c)
				{
					c.value(
						"variable "
						+ token.string()
						+ " to have been declared before use "
						+ explanation);
				}
			});
	}
}
