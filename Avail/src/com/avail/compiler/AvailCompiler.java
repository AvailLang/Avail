/**
 * compiler/AvailCompiler.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this *   list of conditions and the following disclaimer.
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

import com.avail.compiler.AvailAssignmentNode;
import com.avail.compiler.AvailBlockNode;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.AvailCompilerCachedSolution;
import com.avail.compiler.AvailCompilerFragmentCache;
import com.avail.compiler.AvailCompilerScopeStack;
import com.avail.compiler.AvailConstantDeclarationNode;
import com.avail.compiler.AvailConstantSyntheticDeclarationNode;
import com.avail.compiler.AvailInitializingDeclarationNode;
import com.avail.compiler.AvailLabelNode;
import com.avail.compiler.AvailListNode;
import com.avail.compiler.AvailLiteralNode;
import com.avail.compiler.AvailParseNode;
import com.avail.compiler.AvailReferenceNode;
import com.avail.compiler.AvailSendNode;
import com.avail.compiler.AvailSuperCastNode;
import com.avail.compiler.AvailVariableDeclarationNode;
import com.avail.compiler.AvailVariableSyntheticDeclarationNode;
import com.avail.compiler.AvailVariableUseNode;
import com.avail.compiler.Continuation0;
import com.avail.compiler.Continuation1;
import com.avail.compiler.Generator;
import com.avail.compiler.Mutable;
import com.avail.compiler.scanner.AvailLiteralToken;
import com.avail.compiler.scanner.AvailScanner;
import com.avail.compiler.scanner.AvailToken;
import com.avail.descriptor.AvailModuleDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ByteStringDescriptor;
import com.avail.descriptor.ClosureDescriptor;
import com.avail.descriptor.ContainerDescriptor;
import com.avail.descriptor.CyclicTypeDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.levelTwo.L2Interpreter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.avail.descriptor.AvailObject.*;

public class AvailCompiler
{
	List<AvailToken> _tokens;
	int _position;
	L2Interpreter _interpreter;
	AvailCompilerScopeStack _scopeStack;
	int _greatestGuess;
	List<Generator<String>> _greatExpectations;
	AvailObject _module;
	AvailCompilerFragmentCache _fragmentCache;
	List<AvailObject> _extends;
	List<AvailObject> _uses;
	List<AvailObject> _names;
	Continuation2<Integer, Integer> _progressBlock;


	// evaluation

	AvailObject evaluate (
			final AvailParseNode expressionNode)
	{
		//  Evaluate a parse tree node.

		AvailBlockNode block = new AvailBlockNode();
		block.arguments(new ArrayList<AvailVariableDeclarationNode>());
		block.primitive(0);
		ArrayList<AvailParseNode> statements;
		statements = new ArrayList<AvailParseNode>(1);
		statements.add(expressionNode);
		block.statements(statements);
		block.resultType(TypeDescriptor.voidType());
		block = ((AvailBlockNode)(block.validatedWithInterpreter(_interpreter)));
		final AvailCodeGenerator codeGenerator = new AvailCodeGenerator();
		final AvailObject compiledBlock = block.generateOn(codeGenerator);
		//  The block is guaranteed context-free (because imported variables/values are embedded
		//  directly as constants in the generated code), so build a closure with no copied data.
		final AvailObject closure = ClosureDescriptor.newMutableObjectWithCodeAndCopiedTuple(compiledBlock, TupleDescriptor.empty());
		closure.makeImmutable();
		ArrayList<AvailObject> args;
		args = new ArrayList<AvailObject>();
		final AvailObject result = _interpreter.runClosureArguments(closure, args);
		// System.out.println(Integer.toString(_position) + " evaluated (" + expressionNode.toString() + ") = " + result.toString());
		return result;
	}

	void evaluateModuleStatement (
			final AvailParseNode expressionNode)
	{
		//  Evaluate a parse tree node.  It's a top-level statement in a module.  Declarations are
		//  handled differently - they cause a variable to be declared in the module's scope.

		if (! expressionNode.isDeclaration())
		{
			evaluate(expressionNode);
			return;
		}
		//  It's a declaration...
		final AvailVariableDeclarationNode declarationExpression = ((AvailVariableDeclarationNode)(expressionNode));
		final AvailObject name = ByteStringDescriptor.mutableObjectFromNativeString(declarationExpression.name().string()).makeImmutable();
		if (declarationExpression.isConstant())
		{
			AvailObject val = evaluate(((AvailInitializingDeclarationNode)(declarationExpression)).initializingExpression());
			_module.constantBindings(_module.constantBindings().mapAtPuttingCanDestroy(
				name,
				val.makeImmutable(),
				true));
		}
		else
		{
			final AvailObject var = ContainerDescriptor.newContainerWithInnerType(declarationExpression.declaredType());
			if (declarationExpression.isInitializing())
			{
				AvailObject val = evaluate(((AvailInitializingDeclarationNode)(declarationExpression)).initializingExpression());
				var.setValue(val);
			}
			_module.variableBindings(_module.variableBindings().mapAtPuttingCanDestroy(
				name,
				var.makeImmutable(),
				true));
		}
	}



	// parsing entry point

	public void parseModuleFromStringInterpreterProgressBlock (
			final String string, 
			final L2Interpreter theInterpreter, 
			final Continuation2<Integer, Integer> aBlock)
	{
		//  This is sent directly from the AvailBrowser during a make.

		final Mutable<Boolean> ok = new Mutable<Boolean>();
		_progressBlock = aBlock;
		_interpreter = theInterpreter;
		_greatestGuess = -1;
		_greatExpectations = null;
		_tokens = new AvailScanner().scanString(string);
		_position = 0;
		clearScopeStack();
		ok.value = false;
		startModuleTransaction();
		try
		{
			ok.value = parseModule();
		}
		finally
		{
			if (! ok.value)
			{
				rollbackModuleTransaction();
			}
		}
		if (! ok.value)
		{
			error("Unhandled error during compilation of module.");
			return;
		}
		if (! (peekToken().type() == AvailToken.TokenType.end))
		{
			error("Expected end of text, not " + peekToken().string());
			return;
		}
		commitModuleTransaction();
	}



	// parsing errors

	void reportError ()
	{
		//  Report an error by raising the AvailCompiler compilerErrorSignal.  The parameter to the
		//  error is a two element Array with the error string computed greatExpectations and the text
		//  position found in greatestGuess.  This position is the rightmost position encountered
		//  during the parse, and the error strings in greatExpectations are the things that were
		//  expected but not found at that position.  This seems to work very well in practice.

		int tokenPosSave = position();
		position(_greatestGuess);
		int charPos = peekToken().start();
		position(tokenPosSave);
		StringBuilder text = new StringBuilder(100);
		text.append("<-- Expected...\n");
		assert _greatExpectations.size() > 0 : "Bug - empty expectation list";
		Set<String> alreadySeen = new HashSet<String>(_greatExpectations.size());
		for (Generator<String> generator : _greatExpectations)
		{
			String str = generator.value();
			if (! alreadySeen.contains(str))
			{
				alreadySeen.add(str);
				text.append("\t");
				text.append(str.replace("\n", "\n\t\t"));
				text.append("\n");
			}
		}
		throw new AvailCompilerException(charPos, text.toString());
	}



	// private - parsing body

	void parseAdditionalBlockArgumentsAfterThen (
			final List<AvailVariableDeclarationNode> argsSoFar, 
			final Continuation1<List<AvailVariableDeclarationNode>> continuation)
	{
		//  Parse more of a block's formal arguments from the token stream.  A verticalBar
		//  is required after the arguments if there are any (which there are if we're here).

		parseTokenTypeStringErrorContinue(
			AvailToken.TokenType.operator,
			",",
			"comma and more block arguments",
			new Continuation0 ()
			{
				public void value()
				{
					parseBlockArgumentThen(new Continuation1<AvailVariableDeclarationNode> ()
					{
						public void value(final AvailVariableDeclarationNode arg)
						{
							argsSoFar.add(arg);
							parseAdditionalBlockArgumentsAfterThen(argsSoFar, continuation);
							argsSoFar.remove(argsSoFar.size() - 1);
						}
					});
				}
			});
		parseTokenTypeStringErrorContinue(
			AvailToken.TokenType.operator,
			"|",
			"comma and more block arguments or a vertical bar",
			new Continuation0 ()
			{
				public void value()
				{
					continuation.value(new ArrayList<AvailVariableDeclarationNode>(argsSoFar));
				}
			});
	}

	void parseAndEvaluateExpressionYieldingInstanceOfThen (
			final AvailObject someType, 
			final Continuation1<AvailObject> continuation)
	{
		//  Parse an expression whose type is (at least) someType.  Evaluate the expression,
		//  yielding a type, and pass that to the continuation.  Note that the longest syntactically
		//  correct and type correct expression is what gets used.  It's an ambiguity error if two or
		//  more possible parses of this maximum length are possible.

		final Mutable<ArrayList<AvailParseNode>> maxExprs = new Mutable<ArrayList<AvailParseNode>>();
		final Mutable<Integer> maxPos = new Mutable<Integer>();
		final int oldPosition = position();
		final AvailCompilerScopeStack oldStack = _scopeStack;
		clearScopeStack();
		maxExprs.value = new ArrayList<AvailParseNode>();
		maxPos.value = -1;
		parseExpressionThen(new Continuation1<AvailParseNode> ()
		{
			public void value(final AvailParseNode expr)
			{
				if (expr.type().isSubtypeOf(someType))
				{
					if ((position() > maxPos.value))
					{
						maxPos.value = position();
						maxExprs.value = new ArrayList<AvailParseNode>();
						maxExprs.value.add(expr);
					}
					else if ((position() == maxPos.value))
					{
						maxExprs.value.add(expr);
					}
				}
				else
				{
					expected(new Generator<String> ()
					{
						public String value()
						{
							return "expression to have type " + someType.toString();
						}
					});
				}
			}
		});
		AvailObject value;
		if ((maxExprs.value.size() == 1))
		{
			_scopeStack = oldStack;
			if (maxExprs.value.get(0).type().isSubtypeOf(someType))
			{
				value = evaluate(maxExprs.value.get(0));
				if (value.isInstanceOfSubtypeOf(someType))
				{
					position(maxPos.value);
					continuation.value(value);
				}
				else
				{
					expected("expression to respect its own type declaration");
				}
			}
			else
			{
				expected("expression to yield a " + someType.toString());
			}
		}
		else if ((maxExprs.value.size() == 0))
		{
			expected("compile-time expression that yields a " + someType.toString());
		}
		else
		{
			position(maxPos.value);
			assert (maxExprs.value.size() > 1) : "What happened?";
			expected("unambiguous longest parse of compile-time expression");
		}
		_scopeStack = oldStack;
		position(oldPosition);
	}

	void parseAssignmentThen (
			final Continuation1<AvailAssignmentNode> continuation)
	{
		//  Since this is now a statement instead of an expression, we don't need
		//  a special rule about rightmost parse anymore (it must be followed by a
		//  semicolon).

		if ((peekToken().type() == AvailToken.TokenType.keyword))
		{
			parseVariableUseWithExplanationThen("for an assignment", new Continuation1<AvailVariableUseNode> ()
			{
				public void value(final AvailVariableUseNode varUse)
				{
					parseTokenTypeStringErrorContinue(
						AvailToken.TokenType.operator,
						":",
						":= for assignment",
						new Continuation0 ()
						{
							public void value()
							{
								parseTokenTypeStringErrorContinue(
									AvailToken.TokenType.operator,
									"=",
									"= part of := for assignment",
									new Continuation0 ()
									{
										public void value()
										{
											final Mutable<AvailObject> varType = new Mutable<AvailObject>();
											final AvailVariableDeclarationNode declaration = varUse.associatedDeclaration();
											boolean ok = true;
											if (declaration == null)
											{
												ok = false;
												expected("variable to have been declared");
											}
											else if (declaration.isSyntheticVariableDeclaration())
											{
												varType.value = declaration.declaredType();
											}
											else
											{
												if (declaration.isArgument())
												{
													ok = false;
													expected("assignment variable to be local, not argument");
												}
												if (declaration.isLabel())
												{
													ok = false;
													expected("assignment variable to be local, not label");
												}
												if (declaration.isConstant())
												{
													ok = false;
													expected("assignment variable not to be constant");
												}
												varType.value = declaration.declaredType();
											}
											if (ok)
											{
												parseExpressionThen(new Continuation1<AvailParseNode> ()
												{
													public void value(final AvailParseNode expr)
													{
														if ((peekToken().type() == AvailToken.TokenType.endOfStatement))
														{
															if (expr.type().isSubtypeOf(varType.value))
															{
																final AvailAssignmentNode assignment = new AvailAssignmentNode();
																assignment.variable(varUse);
																assignment.expression(expr);
																continuation.value(assignment);
															}
															else
															{
																expected(new Generator<String> ()
																{
																	public String value()
																	{
																		return "assignment expression's type (" + expr.type().toString() + ") to match variable type (" + varType.value.toString() + ")";
																	}
																});
															}
														}
														else
														{
															expected("; to end assignment statement");
														}
													}
												});
											}
										}
									});
							}
						});
				}
			});
		}
	}

	void parseBlockArgumentsThen (
			final Continuation1<List<AvailVariableDeclarationNode>> continuation)
	{
		//  Parse a block's formal arguments from the token stream.  A verticalBar
		//  is required after the arguments if there are any.

		continuation.value(new ArrayList<AvailVariableDeclarationNode>());
		parseBlockArgumentThen(new Continuation1<AvailVariableDeclarationNode> ()
		{
			public void value(final AvailVariableDeclarationNode firstArg)
			{
				ArrayList<AvailVariableDeclarationNode> argsList;
				argsList = new ArrayList<AvailVariableDeclarationNode>(1);
				argsList.add(firstArg);
				parseAdditionalBlockArgumentsAfterThen(argsList, continuation);
			}
		});
	}

	void parseBlockArgumentThen (
			final Continuation1<AvailVariableDeclarationNode> continuation)
	{
		//  Requires block arguments to be named.

		final AvailToken localName = peekToken();
		if ((localName.type() == AvailToken.TokenType.keyword))
		{
			nextToken();
			parseTokenTypeStringErrorContinue(
				AvailToken.TokenType.operator,
				":",
				": then argument type",
				new Continuation0 ()
				{
					public void value()
					{
						parseAndEvaluateExpressionYieldingInstanceOfThen(TypeDescriptor.type(), new Continuation1<AvailObject> ()
						{
							public void value(final AvailObject type)
							{
								AvailCompilerScopeStack oldScope;
								if (type.equals(TypeDescriptor.voidType()))
								{
									expected("a type for the argument other than void");
								}
								else if (type.equals(TypeDescriptor.terminates()))
								{
									expected("a type for the argument other than terminates");
								}
								else
								{
									final AvailVariableDeclarationNode decl = new AvailVariableDeclarationNode();
									decl.name(localName);
									decl.declaredType(type);
									decl.isArgument(true);
									oldScope = _scopeStack;
									pushDeclaration(decl);
									continuation.value(decl);
									_scopeStack = oldScope;
								}
							}
						});
					}
				});
			backupToken();
		}
		else
		{
			expected("block argument declaration");
		}
	}

	void parseBlockThen (
			final Continuation1<AvailBlockNode> continuation)
	{
		//  Parse a block from the token stream.

		if (peekTokenIsTypeString(AvailToken.TokenType.operator, "["))
		{
			final AvailCompilerScopeStack scopeOutsideBlock = _scopeStack;
			nextToken();
			parseBlockArgumentsThen(new Continuation1<List<AvailVariableDeclarationNode>> ()
			{
				public void value(final List<AvailVariableDeclarationNode> arguments)
				{
					parseOptionalPrimitiveForArgCountThen(arguments.size(), new Continuation1<Short> ()
					{
						public void value(final Short primitive)
						{
							parseStatementsThen(new Continuation1<List<AvailParseNode>> ()
							{
								public void value(final List<AvailParseNode> statements)
								{
									if (((primitive != 0) && ((primitive != 256) && statements.isEmpty())))
									{
										expected("mandatory failure code for primitive method (except #256)");
									}
									else
									{
										parseTokenTypeStringErrorContinue(
											AvailToken.TokenType.operator,
											"]",
											"close bracket to end block",
											new Continuation0 ()
											{
												public void value()
												{
													final Mutable<AvailObject> lastStatementType = new Mutable<AvailObject>();
													if ((statements.size() > 0))
													{
														final AvailParseNode lastStatement = statements.get((statements.size() - 1));
														if ((lastStatement.isDeclaration() || lastStatement.isAssignment()))
														{
															lastStatementType.value = TypeDescriptor.voidType();
														}
														else
														{
															lastStatementType.value = lastStatement.type();
														}
													}
													else
													{
														lastStatementType.value = TypeDescriptor.voidType();
													}
													final AvailCompilerScopeStack scopeInBlock = _scopeStack;
													_scopeStack = scopeOutsideBlock;
													parseTokenTypeStringErrorContinue(
														AvailToken.TokenType.operator,
														":",
														"optional block return type declaration",
														new Continuation0 ()
														{
															public void value()
															{
																parseAndEvaluateExpressionYieldingInstanceOfThen(TypeDescriptor.type(), new Continuation1<AvailObject> ()
																{
																	public void value(final AvailObject returnType)
																	{
																		if (((statements.isEmpty() && primitive != null) || lastStatementType.value.isSubtypeOf(returnType)))
																		{
																			boolean blockTypeGood = true;
																			if (((statements.size() > 0) && statements.get(0).isLabel()))
																			{
																				AvailObject labelClosureType = ((AvailLabelNode)(statements.get(0))).declaredType().closureType();
																				blockTypeGood = ((labelClosureType.numArgs() == arguments.size()) && labelClosureType.returnType().equals(returnType));
																				if (blockTypeGood)
																				{
																					for (int i = 1, _end1 = arguments.size(); i <= _end1; i++)
																					{
																						if (! labelClosureType.argTypeAt(i).equals(arguments.get((i - 1)).declaredType()))
																						{
																							blockTypeGood = true;
																						}
																					}
																				}
																			}
																			if (blockTypeGood)
																			{
																				AvailBlockNode blockNode = new AvailBlockNode();
																				blockNode.arguments(arguments);
																				blockNode.primitive(primitive);
																				blockNode.statements(statements);
																				blockNode.resultType(returnType);
																				continuation.value(blockNode);
																			}
																			else
																			{
																				expected("block's label's type to agree with block's type");
																			}
																		}
																		else
																		{
																			expected(new Generator<String> ()
																			{
																				public String value()
																				{
																					return "last statement's type \"" + lastStatementType.value.toString() + "\" to agree with block's declared result type \"" + returnType.toString() + "\".";
																				}
																			});
																		}
																	}
																});
															}
														});
													if ((statements.isEmpty() && (primitive != 0)))
													{
														expected("return type declaration for primitive method with no statements");
													}
													else
													{
														boolean blockTypeGood = true;
														if (((statements.size() > 0) && statements.get(0).isLabel()))
														{
															AvailObject labelClosureType = ((AvailLabelNode)(statements.get(0))).declaredType().closureType();
															blockTypeGood = ((labelClosureType.numArgs() == arguments.size()) && labelClosureType.returnType().equals(lastStatementType.value));
															if (blockTypeGood)
															{
																for (int i = 1, _end2 = arguments.size(); i <= _end2; i++)
																{
																	if (! labelClosureType.argTypeAt(i).equals(arguments.get((i - 1)).declaredType()))
																	{
																		blockTypeGood = false;
																	}
																}
															}
														}
														if (blockTypeGood)
														{
															AvailBlockNode blockNode = new AvailBlockNode();
															blockNode.arguments(arguments);
															blockNode.primitive(primitive);
															blockNode.statements(statements);
															blockNode.resultType(lastStatementType.value);
															continuation.value(blockNode);
														}
														else
														{
															expected("block with label to have return type void (otherwise exiting would need to provide a value)");
														}
													}
													_scopeStack = scopeInBlock;
												}
											});
									}
								}
							});
						}
					});
				}
			});
			_scopeStack = scopeOutsideBlock;
			//  open square bracket.
			backupToken();
		}
	}

	void parseDeclarationThen (
			final Continuation1<AvailVariableDeclarationNode> continuation)
	{
		//  Parse a local variable declaration.  These are one of three forms:
		//  a simple declaration (var : type),
		//  an initializing declaration (var : type := value),
		//  or a constant declaration (var ::= expr).

		final int where = position();
		final AvailCompilerScopeStack oldScope = _scopeStack;
		final AvailToken localName = peekToken();
		if ((localName.type() == AvailToken.TokenType.keyword))
		{
			nextToken();
			parseTokenTypeStringErrorContinue(
				AvailToken.TokenType.operator,
				":",
				": or ::= for simple / constant / initializing declaration",
				new Continuation0 ()
				{
					public void value()
					{
						parseTokenTypeStringErrorContinue(
							AvailToken.TokenType.operator,
							":",
							"second colon for constant declaration (a ::= expr)",
							new Continuation0 ()
							{
								public void value()
								{
									parseTokenTypeStringErrorContinue(
										AvailToken.TokenType.operator,
										"=",
										"= part of ::= in constant declaration",
										new Continuation0 ()
										{
											public void value()
											{
												parseExpressionThen(new Continuation1<AvailParseNode> ()
												{
													public void value(final AvailParseNode initExpr)
													{
														final AvailConstantDeclarationNode constantDeclaration = new AvailConstantDeclarationNode();
														constantDeclaration.name(localName);
														constantDeclaration.declaredType(initExpr.type());
														constantDeclaration.initializingExpression(initExpr);
														constantDeclaration.isArgument(false);
														pushDeclaration(constantDeclaration);
														continuation.value(constantDeclaration);
														_scopeStack = oldScope;
													}
												});
											}
										});
								}
							});
						parseAndEvaluateExpressionYieldingInstanceOfThen(TypeDescriptor.type(), new Continuation1<AvailObject> ()
						{
							public void value(final AvailObject type)
							{
								if ((type.equals(TypeDescriptor.voidType()) || type.equals(TypeDescriptor.terminates())))
								{
									expected("a type for the variable other than void or terminates");
								}
								else
								{
									parseTokenTypeStringContinue(
										AvailToken.TokenType.operator,
										":",
										new Continuation0 ()
										{
											public void value()
											{
												parseTokenTypeStringContinue(
													AvailToken.TokenType.operator,
													"=",
													new Continuation0 ()
													{
														public void value()
														{
															parseExpressionThen(new Continuation1<AvailParseNode> ()
															{
																public void value(final AvailParseNode initExpr)
																{
																	if (initExpr.type().isSubtypeOf(type))
																	{
																		final AvailInitializingDeclarationNode initializingDeclaration = new AvailInitializingDeclarationNode();
																		initializingDeclaration.name(localName);
																		initializingDeclaration.declaredType(type);
																		initializingDeclaration.initializingExpression(initExpr);
																		initializingDeclaration.isArgument(false);
																		pushDeclaration(initializingDeclaration);
																		continuation.value(initializingDeclaration);
																		_scopeStack = oldScope;
																	}
																	else
																	{
																		expected("initializing expression's type to agree with declared type");
																	}
																}
															});
														}
													});
											}
										});
									//  Try the simple declaration... var : type;
									final AvailVariableDeclarationNode simpleDeclaration = new AvailVariableDeclarationNode();
									simpleDeclaration.name(localName);
									simpleDeclaration.declaredType(type);
									simpleDeclaration.isArgument(false);
									pushDeclaration(simpleDeclaration);
									continuation.value(simpleDeclaration);
									_scopeStack = oldScope;
								}
							}
						});
					}
				});
		}
		else
		{
			expected("a variable or constant declaration");
		}
		position(where);
	}

	void parseExpressionListItemsBeyondThen (
			final List<AvailParseNode> itemsSoFar, 
			final Continuation1<AvailParseNode> continuation)
	{
		//  Given a sequenceable collection of itemsSoFar, attempt to extend that
		//  collection by parsing comma and another item.  If successful, try that
		//  answer and then look for more items.

		if (peekTokenIsTypeString(AvailToken.TokenType.operator, ","))
		{
			parseTokenTypeStringErrorContinue(
				AvailToken.TokenType.operator,
				",",
				"comma for a list",
				new Continuation0 ()
				{
					public void value()
					{
						parseExpressionListItemThen(new Continuation1<AvailParseNode> ()
						{
							public void value(final AvailParseNode newItem)
							{
								ArrayList<AvailParseNode> newItems;
								newItems = new ArrayList<AvailParseNode>(itemsSoFar);
								newItems.add(newItem);
								//  Answer this list.
								final AvailListNode listNode = new AvailListNode();
								listNode.expressions(newItems);
								continuation.value(listNode);
								//  Try for more items, too...
								parseExpressionListItemsBeyondThen(newItems, continuation);
							}
						});
					}
				});
		}
	}

	void parseExpressionListItemThen (
			final Continuation1<AvailParseNode> continuation)
	{
		//  Parse an expression that isn't a list.  Backtracking will find all valid interpretations.

		parseLeadingKeywordSendThen(new Continuation1<AvailSendNode> ()
		{
			public void value(final AvailSendNode sendNode)
			{
				parseOptionalLeadingArgumentSendAfterThen(sendNode, continuation);
			}
		});
		parseSimpleThen(new Continuation1<AvailParseNode> ()
		{
			public void value(final AvailParseNode simpleNode)
			{
				parseOptionalLeadingArgumentSendAfterThen(simpleNode, continuation);
			}
		});
		parseBlockThen(new Continuation1<AvailBlockNode> ()
		{
			public void value(final AvailBlockNode blockNode)
			{
				parseOptionalLeadingArgumentSendAfterThen(blockNode, continuation);
			}
		});
	}

	void parseExpressionThen (
			final Continuation1<AvailParseNode> originalContinuation)
	{
		//  Parse an expression.  Backtracking will find all valid interpretations.  Note that
		//  a list expression requires at least two terms to form a list node.  This method is a
		//  key optimization point, so the fragmentCache is used to keep track of parsing
		//  solutions at this point, simply replaying them on subsequent parses, as long as
		//  the variable declarations up to that point were identical.

		final int start = position();
		final AvailCompilerScopeStack originalScope = _scopeStack;
		if (! _fragmentCache.hasComputedTokenPositionScopeStack(start, originalScope))
		{
			_fragmentCache.startComputingTokenPositionScopeStack(start, originalScope);
			final Continuation1<AvailParseNode> justRecord = new Continuation1<AvailParseNode> ()
			{
				public void value(final AvailParseNode expr)
				{
					_fragmentCache.atTokenPositionScopeStackAddSolution(
						start,
						originalScope,
						newCachedSolutionEndPositionScopeStack(
							expr,
							position(),
							_scopeStack));
				}
			};
			parseExpressionListItemThen(new Continuation1<AvailParseNode> ()
			{
				public void value(final AvailParseNode firstItem)
				{
					justRecord.value(firstItem);
					ArrayList<AvailParseNode> itemList;
					itemList = new ArrayList<AvailParseNode>(1);
					itemList.add(firstItem);
					parseExpressionListItemsBeyondThen(itemList, justRecord);
				}
			});
			if (! ((position() == start) && (_scopeStack == originalScope)))
			{
				error("token stream position and scopeStack were not restored correctly");
				return;
			}
		}
		//  Deja vu!  We were asked to parse an expression starting at this point
		//  before.  Luckily we had the foresight to record what those resulting
		//  expressions were (as well as the scopeStack just after parsing each).
		//  Replay just these solutions to the passed continuation.  This has the
		//  effect of eliminating each 'local' misparsing exactly once.  I'm not sure
		//  what happens to the order of the algorithm, but it might go from
		//  exponential to small polynomial.
		final List<AvailCompilerCachedSolution> solutions = _fragmentCache.atTokenPositionScopeStack(start, originalScope);
		for (int solutionIndex = 1, _end1 = solutions.size(); solutionIndex <= _end1; solutionIndex++)
		{
			final AvailCompilerCachedSolution solution = solutions.get((solutionIndex - 1));
			position(solution.endPosition());
			_scopeStack = solution.scopeStack();
			originalContinuation.value(solution.parseNode());
		}
		_scopeStack = originalScope;
		position(start);
	}

	void parseFromCompleteBundlesIncompleteBundlesArgumentsSoFarThen (
			final int partIndex, 
			final AvailObject complete, 
			final AvailObject incomplete, 
			final List<AvailParseNode> argsSoFar, 
			final Continuation1<AvailSendNode> continuation)
	{
		//  We've parsed part of a send.  Try to finish the job.

		final int completeMapSize = complete.mapSize();
		final int incompleteMapSize = incomplete.mapSize();
		if (((completeMapSize == 0) && (incompleteMapSize == 0)))
		{
			error("Expected a nonempty list of possible messages");
			return;
		}
		if (! (completeMapSize == 0))
		{
			AvailObject.lock(complete);
			for (int mapIndex = 1, _end1 = complete.capacity(); mapIndex <= _end1; mapIndex++)
			{
				final AvailObject message = complete.keyAtIndex(mapIndex);
				if (! message.equalsVoidOrBlank())
				{
					if (_interpreter.hasMethodsAt(message))
					{
						final Mutable<Boolean> valid = new Mutable<Boolean>();
						final AvailObject impSet = _interpreter.methodsAt(message);
						final AvailObject bundle = complete.valueAtIndex(mapIndex);
						valid.value = true;
						List<AvailObject> typesSoFar;
						typesSoFar = new ArrayList<AvailObject>(argsSoFar.size());
						for (int argIndex = 1, _end2 = argsSoFar.size(); argIndex <= _end2; argIndex++)
						{
							typesSoFar.add(argsSoFar.get((argIndex - 1)).type());
						}
						final AvailObject returnType = _interpreter.validateTypesOfMessageSendArgumentTypesIfFail(
							message,
							typesSoFar,
							new Continuation1<Generator<String>> ()
							{
								public void value(final Generator<String> f)
								{
									valid.value = false;
									expected(f);
								}
							});
						if (valid.value)
						{
							final AvailSendNode sendNode = new AvailSendNode();
							sendNode.message(bundle.message());
							sendNode.bundle(bundle);
							sendNode.implementationSet(impSet);
							sendNode.arguments(argsSoFar);
							sendNode.returnType(returnType);
							continuation.value(sendNode);
						}
					}
				}
			}
			AvailObject.unlock(complete);
		}
		if (! (incompleteMapSize == 0))
		{
			final AvailToken keywordToken = peekToken();
			if (((keywordToken.type() == AvailToken.TokenType.keyword) || (keywordToken.type() == AvailToken.TokenType.operator)))
			{
				final AvailObject keywordString = ByteStringDescriptor.mutableObjectFromNativeString(keywordToken.string());
				if (incomplete.hasKey(keywordString))
				{
					nextToken();
					final AvailObject subtree = incomplete.mapAt(keywordString);
					parseFromCompleteBundlesIncompleteBundlesArgumentsSoFarThen(
						(partIndex + 1),
						subtree.complete(),
						subtree.incomplete(),
						argsSoFar,
						continuation);
					backupToken();
				}
				else
				{
					expected(new Generator<String> ()
					{
						public String value()
						{
							StringBuilder builder = new StringBuilder(200);
							builder.append("one of the following internal keywords: ");
							if (incompleteMapSize > 10)
							{
								builder.append("\n\t\t");
							}
							AvailObject.lock(incomplete);
							final AvailObject incompleteTuple = incomplete.keysAsSet().asTuple();
							for (int index = 1, end1 = incompleteTuple.tupleSize(); index <= end1; index++)
							{
								AvailObject string = incompleteTuple.tupleAt(index);
								for (int i = 1, end2 = string.tupleSize(); i <= end2; i++)
								{
									builder.appendCodePoint(string.tupleAt(i).codePoint());
								}
								builder.append("  ");
							}
							AvailObject.unlock(incomplete);
							return builder.toString();
						}
					});
				}
			}
			if (incomplete.hasKey(TupleDescriptor.underscoreTuple()))
			{
				final AvailObject expectingArgSubtree = incomplete.mapAt(TupleDescriptor.underscoreTuple());
				parseSendArgumentWithExplanationThen(" (an argument of some message)", new Continuation1<AvailParseNode> ()
				{
					public void value(final AvailParseNode newArg)
					{
						Continuation1<AvailSendNode> continueAfterFiltering = continuation;
						if (newArg.isSend())
						{
							continueAfterFiltering = new Continuation1<AvailSendNode> ()
							{
								public void value(final AvailSendNode outerSend)
								{
									final AvailObject restrictions = outerSend.bundle().restrictions().tupleAt((argsSoFar.size() + 1));
									if (restrictions.hasElement(((AvailSendNode)(newArg)).message()))
									{
										expected(new Generator<String> ()
										{
											public String value()
											{
												return "different nesting in" + outerSend.toString();
											}
										});
									}
									else
									{
										continuation.value(outerSend);
									}
								}
							};
						}
						List<AvailParseNode> newArgsSoFar;
						newArgsSoFar = new ArrayList<AvailParseNode>(argsSoFar);
						newArgsSoFar.add(newArg);
						parseFromCompleteBundlesIncompleteBundlesArgumentsSoFarThen(
							(partIndex + 1),
							expectingArgSubtree.complete(),
							expectingArgSubtree.incomplete(),
							newArgsSoFar,
							continueAfterFiltering);
					}
				});
			}
		}
	}

	void parseLabelThen (
			final Continuation1<AvailLabelNode> continuation)
	{
		//  Parse a label declaration.

		parseTokenTypeStringErrorContinue(
			AvailToken.TokenType.operator,
			"$",
			"label statement starting with \"$\"",
			new Continuation0 ()
			{
				public void value()
				{
					final AvailToken token = peekToken();
					if ((token.type() == AvailToken.TokenType.keyword))
					{
						nextToken();
						parseTokenTypeStringErrorContinue(
							AvailToken.TokenType.operator,
							":",
							"colon for label's type declaration",
							new Continuation0 ()
							{
								public void value()
								{
									parseAndEvaluateExpressionYieldingInstanceOfThen(TypeDescriptor.continuationType(), new Continuation1<AvailObject> ()
									{
										public void value(final AvailObject contType)
										{
											final AvailLabelNode label = new AvailLabelNode();
											label.name(token);
											label.declaredType(contType);
											final AvailCompilerScopeStack oldScope = _scopeStack;
											pushDeclaration(label);
											continuation.value(label);
											_scopeStack = oldScope;
										}
									});
								}
							});
						backupToken();
					}
					else
					{
						expected("name of label");
					}
				}
			});
	}

	void parseLeadingArgumentSendAfterThen (
			final AvailParseNode leadingArgument, 
			final Continuation1<AvailSendNode> continuation)
	{
		//  Parse a send node whose leading argument has already been parsed.

		final AvailObject complete = _interpreter.completeBundlesStartingWith(TupleDescriptor.underscoreTuple());
		final AvailObject incomplete = _interpreter.incompleteBundlesStartingWith(TupleDescriptor.underscoreTuple());
		if (! ((complete.mapSize() == 0) && (incomplete.mapSize() == 0)))
		{
			ArrayList<AvailParseNode> argsSoFar;
			argsSoFar = new ArrayList<AvailParseNode>(2);
			argsSoFar.add(leadingArgument);
			Continuation1<AvailSendNode> innerContinuation;
			if (leadingArgument.isSend())
			{
				innerContinuation = new Continuation1<AvailSendNode> ()
				{
					public void value(final AvailSendNode outerSend)
					{
						if (outerSend.bundle().restrictions().tupleAt(1).hasElement(((AvailSendNode)(leadingArgument)).message()))
						{
							expected(new Generator<String> ()
							{
								public String value()
								{
									return "different nesting of " + leadingArgument.toString() + " in " + outerSend.toString();
								}
							});
						}
						else
						{
							continuation.value(outerSend);
						}
					}
				};
			}
			else
			{
				innerContinuation = continuation;
			}
			parseFromCompleteBundlesIncompleteBundlesArgumentsSoFarThen(
				2,
				complete,
				incomplete,
				argsSoFar,
				innerContinuation);
		}
	}

	void parseLeadingKeywordSendThen (
			final Continuation1<AvailSendNode> continuation)
	{
		//  Parse a send node.  To prevent infinite left-recursion and false ambiguity, we only
		//  allow a send with a leading keyword to be parsed from here.

		final AvailToken peekToken = peekToken();
		if (((peekToken.type() == AvailToken.TokenType.keyword) || (peekToken.type() == AvailToken.TokenType.operator)))
		{
			final AvailObject start = ByteStringDescriptor.mutableObjectFromNativeString(nextToken().string());
			final AvailObject complete = _interpreter.completeBundlesStartingWith(start);
			final AvailObject incomplete = _interpreter.incompleteBundlesStartingWith(start);
			ArrayList<AvailParseNode> argsSoFar;
			argsSoFar = new ArrayList<AvailParseNode>(3);
			if (! ((complete.mapSize() == 0) && (incomplete.mapSize() == 0)))
			{
				parseFromCompleteBundlesIncompleteBundlesArgumentsSoFarThen(
					2,
					complete,
					incomplete,
					argsSoFar,
					continuation);
			}
			backupToken();
		}
	}

	void parseOptionalLeadingArgumentSendAfterThen (
			final AvailParseNode node, 
			final Continuation1<AvailParseNode> continuation)
	{
		//  Parse an expression that isn't a list.  Backtracking will find all valid interpretations.

		continuation.value(node);
		if (node.type().equals(TypeDescriptor.voidType()))
		{
			return;
		}
		parseOptionalSuperCastAfterErrorSuffixThen(
			node,
			" in case it's the first argument of a non-keyword-leading message",
			new Continuation1<AvailParseNode> ()
			{
				public void value(final AvailParseNode cast)
				{
					parseLeadingArgumentSendAfterThen(cast, new Continuation1<AvailSendNode> ()
					{
						public void value(final AvailSendNode leadingSend)
						{
							parseOptionalLeadingArgumentSendAfterThen(leadingSend, continuation);
						}
					});
				}
			});
	}

	void parseOptionalPrimitiveForArgCountThen (
			final int argCount, 
			final Continuation1<Short> continuation)
	{
		//  Parse the optional primitive declaration at the start of a block.
		//  Pass the primitive number (or nil) into the continuation.

		final Mutable<Short> num = new Mutable<Short>();
		num.value = 0;
		parseTokenTypeStringErrorContinue(
			AvailToken.TokenType.keyword,
			"Primitive",
			"primitive declaration",
			new Continuation0 ()
			{
				public void value()
				{
					final AvailToken token = peekToken();
					if (((token.type() == AvailToken.TokenType.literal) && ((AvailLiteralToken)(token)).literal().isInstanceOfSubtypeOf(IntegerRangeTypeDescriptor.integers())))
					{
						nextToken();
						num.value = ((short)(((AvailLiteralToken)(token)).literal().extractInt()));
						if (_interpreter.supportsPrimitive(num.value))
						{
							if ((_interpreter.argCountForPrimitive(num.value) == argCount))
							{
								parseTokenTypeStringContinue(
									AvailToken.TokenType.endOfStatement,
									";",
									new Continuation0 ()
									{
										public void value()
										{
											continuation.value(num.value);
										}
									});
							}
							else
							{
								final int expectedArgCount = _interpreter.argCountForPrimitive(num.value);
								expected("primitive #" + Short.toString(num.value) + " to be passed " + Integer.toString(expectedArgCount) + " arguments, not " + Integer.toString(argCount));
							}
						}
						else
						{
							expected("a supported primitive number, not #" + Short.toString(num.value));
						}
						backupToken();
					}
					else
					{
						expected("primitive number");
					}
				}
			});
		if ((num.value == 0))
		{
			continuation.value(((short)(0)));
		}
	}

	void parseOptionalSuperCastAfterErrorSuffixThen (
			final AvailParseNode expr, 
			final String errorSuffix, 
			final Continuation1<? super AvailParseNode> continuation)
	{
		//  An expression was parsed.  Now parse the optional supercast clause that may
		//  follow it to make a supercast node.

		continuation.value(expr);
		if (peekTokenIsTypeString(AvailToken.TokenType.operator, ":"))
		{
			nextToken();
			parseTokenTypeStringErrorGeneratorContinue(
				AvailToken.TokenType.operator,
				":",
				new Generator<String> ()
				{
					public String value()
					{
						StringBuilder str = new StringBuilder(100);
						str.append("second : of :: to supercast an expression");
						str.append(errorSuffix);
						return str.toString();
					}
				},
				new Continuation0 ()
				{
					public void value()
					{
						parseAndEvaluateExpressionYieldingInstanceOfThen(TypeDescriptor.type(), new Continuation1<AvailObject> ()
						{
							public void value(final AvailObject type)
							{
								if (expr.type().isSubtypeOf(type))
								{
									final AvailSuperCastNode cast = new AvailSuperCastNode();
									cast.expression(expr);
									cast.type(type);
									continuation.value(cast);
								}
								else
								{
									expected("supercast type to be supertype of expression's type.");
								}
							}
						});
					}
				});
			backupToken();
		}
	}

	void parseSendArgumentWithExplanationThen (
			final String explanation, 
			final Continuation1<AvailParseNode> continuation)
	{
		//  Parse an argument to a message send.  Backtracking will find all valid interpretations.

		parseExpressionThen(new Continuation1<AvailParseNode> ()
		{
			public void value(final AvailParseNode expr)
			{
				parseOptionalSuperCastAfterErrorSuffixThen(
					expr,
					explanation,
					continuation);
			}
		});
	}

	void parseSimpleThen (
			final Continuation1<? super AvailParseNode> continuation)
	{
		//  Look for a variable, reference, or literal.

		final int originalPosition = _position;
		parseVariableUseWithExplanationThen("", continuation);
		if ((peekToken().type() == AvailToken.TokenType.literal))
		{
			final AvailLiteralNode literalNode = new AvailLiteralNode();
			literalNode.token(((AvailLiteralToken)(nextToken())));
			continuation.value(literalNode);
			backupToken();
		}
		if (peekTokenIsTypeString(AvailToken.TokenType.operator, "&"))
		{
			nextToken();
			parseVariableUseWithExplanationThen("in reference expression", new Continuation1<AvailVariableUseNode> ()
			{
				public void value(final AvailVariableUseNode var)
				{
					final AvailVariableDeclarationNode declaration = var.associatedDeclaration();
					if (declaration == null)
					{
						expected("reference variable to have been declared");
					}
					else if (declaration.isConstant())
					{
						expected("reference variable not to be a constant");
					}
					else if (declaration.isArgument())
					{
						expected("reference variable not to be an argument");
					}
					else
					{
						final AvailReferenceNode referenceNode = new AvailReferenceNode();
						referenceNode.variable(var);
						continuation.value(referenceNode);
					}
				}
			});
			backupToken();
		}
		expected("simple expression");
		assert (_position == originalPosition);
	}

	void parseStatementAsOutermostCanBeLabelThen (
			final boolean outermost, 
			final boolean canBeLabel, 
			final Continuation1<AvailParseNode> continuation)
	{
		//  Parse a statement.  This is the boundary for the backtracking grammar.
		//  A statement must be unambiguous (in isolation) to be valid.  The passed
		//  continuation will be invoked at most once, and only if the statement had
		//  a single interpretation.

		assert (! (outermost & canBeLabel));
		tryIfUnambiguousThen(new Continuation1<Continuation1<AvailParseNode>> ()
		{
			public void value(final Continuation1<AvailParseNode> whenFoundStatement)
			{
				if (canBeLabel)
				{
					parseLabelThen(new Continuation1<AvailLabelNode> ()
					{
						public void value(final AvailLabelNode label)
						{
							parseTokenTypeStringErrorContinue(
								AvailToken.TokenType.endOfStatement,
								";",
								"; to end label statement",
								new Continuation0 ()
								{
									public void value()
									{
										whenFoundStatement.value(label);
									}
								});
						}
					});
				}
				parseDeclarationThen(new Continuation1<AvailVariableDeclarationNode> ()
				{
					public void value(final AvailVariableDeclarationNode declaration)
					{
						parseTokenTypeStringErrorContinue(
							AvailToken.TokenType.endOfStatement,
							";",
							"; to end declaration statement",
							new Continuation0 ()
							{
								public void value()
								{
									whenFoundStatement.value(declaration);
								}
							});
					}
				});
				parseAssignmentThen(new Continuation1<AvailAssignmentNode> ()
				{
					public void value(final AvailAssignmentNode assignment)
					{
						parseTokenTypeStringErrorContinue(
							AvailToken.TokenType.endOfStatement,
							";",
							"; to end assignment statement",
							new Continuation0 ()
							{
								public void value()
								{
									whenFoundStatement.value(assignment);
								}
							});
					}
				});
				parseExpressionThen(new Continuation1<AvailParseNode> ()
				{
					public void value(final AvailParseNode expr)
					{
						parseTokenTypeStringErrorContinue(
							AvailToken.TokenType.endOfStatement,
							";",
							"; to end statement",
							new Continuation0 ()
							{
								public void value()
								{
									if (((! outermost) || expr.type().equals(TypeDescriptor.voidType())))
									{
										whenFoundStatement.value(expr);
									}
									else
									{
										expected("outer level statement to have void type");
									}
								}
							});
					}
				});
			}
		}, new Continuation1<AvailParseNode> ()
		{
			public void value(final AvailParseNode statement)
			{
				continuation.value(statement);
			}
		});
	}

	void parseStatementsThen (
			final Continuation1<List<AvailParseNode>> continuation)
	{
		//  Parse zero or more statements from the tokenStream.  Parse as many
		//  statements as possible before invoking the continuation.

		final Mutable<Integer> postPosition = new Mutable<Integer>();
		final Mutable<AvailCompilerScopeStack> postScope = new Mutable<AvailCompilerScopeStack>();
		final Mutable<AvailParseNode> theStatement = new Mutable<AvailParseNode>();
		final int where = position();
		final AvailCompilerScopeStack oldScope = _scopeStack;
		ArrayList<AvailParseNode> statements;
		statements = new ArrayList<AvailParseNode>(10);
		while (true)
		{
			final Mutable<Boolean> ok = new Mutable<Boolean>();
			ok.value = false;
			boolean problem = false;
			if ((statements.size() > 0))
			{
				final AvailParseNode lastStatement = statements.get((statements.size() - 1));
				if (! (lastStatement.isAssignment() || (lastStatement.isDeclaration() || lastStatement.isLabel())))
				{
					if (lastStatement.type().equals(TypeDescriptor.terminates()))
					{
						problem = true;
						expected("previous statement to be the last statement because it always terminates");
					}
					if (! lastStatement.type().equals(TypeDescriptor.voidType()))
					{
						problem = true;
						expected(new Generator<String> ()
						{
							public String value()
							{
								return "non-last statement \"" + lastStatement.toString() + "\" to have type void, not \"" + lastStatement.type().toString() + "\".";
							}
						});
					}
				}
			}
			if (! problem)
			{
				parseStatementAsOutermostCanBeLabelThen(
					false,
					statements.isEmpty(),
					new Continuation1<AvailParseNode> ()
					{
						public void value(final AvailParseNode stmt)
						{
							postPosition.value = position();
							postScope.value = _scopeStack;
							theStatement.value = stmt;
							ok.value = true;
						}
					});
			}
			if ((! ok.value))
			{
				break;
			}
			position(postPosition.value);
			_scopeStack = postScope.value;
			statements.add(theStatement.value);
		}
		continuation.value(statements);
		expected("more statements");
		position(where);
		_scopeStack = oldScope;
	}

	AvailObject parseStringLiteral ()
	{
		//  Parse a string literal.  Report an error if one isn't present.

		final AvailToken token = peekToken();
		final AvailObject stringObject = token.stringLiteralOrNil();
		if (stringObject == null)
		{
			error("expected string literal");
			return VoidDescriptor.voidObject();
		}
		nextToken();
		return stringObject;
	}

	List<AvailObject> parseStrings ()
	{
		//  Parse one or more string literals separated by commas.  This parse isn't
		//  backtracking like the rest of the grammar - it's greedy.  It considers a
		//  comma followed by something other than a string literal to be an
		//  unrecoverable parsing error (not a backtrack).

		final AvailToken firstString = peekToken();
		AvailObject stringLiteral = firstString.stringLiteralOrNil();
		List<AvailObject> list;
		list = new ArrayList<AvailObject>();
		if (stringLiteral == null)
		{
			return list;
		}
		list.add(stringLiteral);
		nextToken();
		while (parseTokenIsTypeString(AvailToken.TokenType.operator, ",")) {
			stringLiteral = peekToken().stringLiteralOrNil();
			if (stringLiteral == null)
			{
				error("Problem parsing literal strings");
				return null;
			}
			list.add(stringLiteral);
			nextToken();
		}
		return list;
	}

	void parseStringsThen (
			final Continuation1<List<AvailObject>> continuation)
	{
		//  Parse one or more string literals separated by commas.  This parse isn't
		//  like the rest of the grammar - it's greedy.  It considers a comma followed
		//  by something other than a string literal to be an unrecoverable parsing
		//  error (not a backtrack).

		final int where = position();
		final List<AvailObject> strings = parseStrings();
		continuation.value(strings);
		position(where);
	}

	void parseVariableUseWithExplanationThen (
			final String exp, 
			final Continuation1<? super AvailVariableUseNode> continuation)
	{
		//  Parse the use of a variable.

		if (! (peekToken().type() == AvailToken.TokenType.keyword))
		{
			return;
		}
		final AvailToken token = nextToken();
		assert (token.type() == AvailToken.TokenType.keyword) : "Supposed to be a keyword here";
		//  First check if it's in a block scope...
		final AvailVariableDeclarationNode localDecl = lookupDeclaration(token.string());
		if (localDecl != null)
		{
			AvailVariableUseNode varUse = new AvailVariableUseNode();
			varUse.name(token);
			varUse.associatedDeclaration(localDecl);
			continuation.value(varUse);
			backupToken();
			return;
		}
		//  Not in a block scope.  See if it's a module variable or module constant...
		final AvailObject varName = ByteStringDescriptor.mutableObjectFromNativeString(token.string());
		if (_module.variableBindings().hasKey(varName))
		{
			final AvailObject variableObject = _module.variableBindings().mapAt(varName);
			final AvailVariableSyntheticDeclarationNode moduleVarDecl = new AvailVariableSyntheticDeclarationNode();
			moduleVarDecl.name(token);
			moduleVarDecl.declaredType(variableObject.type().innerType());
			moduleVarDecl.isArgument(false);
			moduleVarDecl.availVariable(variableObject);
			AvailVariableUseNode varUse = new AvailVariableUseNode();
			varUse.name(token);
			varUse.associatedDeclaration(moduleVarDecl);
			continuation.value(varUse);
			backupToken();
			return;
		}
		if (_module.constantBindings().hasKey(varName))
		{
			final AvailObject valueObject = _module.constantBindings().mapAt(varName);
			final AvailConstantSyntheticDeclarationNode moduleConstDecl = new AvailConstantSyntheticDeclarationNode();
			moduleConstDecl.name(token);
			moduleConstDecl.declaredType(valueObject.type());
			moduleConstDecl.isArgument(false);
			moduleConstDecl.availValue(valueObject);
			AvailVariableUseNode varUse = new AvailVariableUseNode();
			varUse.name(token);
			varUse.associatedDeclaration(moduleConstDecl);
			continuation.value(varUse);
			backupToken();
			return;
		}
		expected(new Generator<String> ()
		{
			public String value()
			{
				return "variable \"" + token.string() + "\" to have been declared before use " + exp;
			}
		});
		backupToken();
	}



	// private - parsing main

	boolean parseHeader ()
	{
		//  Parse a the header of the module from the token stream.  See my class's
		//  'documentation - grammar' protocol for details.  Leave the token stream set
		//  at the start of the body.  Answer whether the header was parsed correctly.

		final Mutable<Integer> where = new Mutable<Integer>();
		final Mutable<Boolean> ok = new Mutable<Boolean>();
		_extends = new ArrayList<AvailObject>();
		_uses = new ArrayList<AvailObject>();
		_names = new ArrayList<AvailObject>();
		ok.value = false;
		parseTokenTypeStringErrorContinue(
			AvailToken.TokenType.keyword,
			"Module",
			"initial Module keyword",
			new Continuation0 ()
			{
				public void value()
				{
					final int savePosition = position();
					_module.name(parseStringLiteral());
					if (parseTokenIsTypeString(AvailToken.TokenType.keyword, "Pragma"))
					{
						final List<AvailObject> pragmaStrings = parseStrings();
						for (int i = 1, _end1 = pragmaStrings.size(); i <= _end1; i++)
						{
							String pragmaValue;
							String pragmaKey;
							String[] pragmaParts = pragmaStrings.get(i - 1).asNativeString().split("=");
							assert pragmaParts.length == 2;
							pragmaKey = pragmaParts[0].trim();
							pragmaValue = pragmaParts[1].trim();
							if (! pragmaKey.matches("\\w+"))
							{
								expected("pragma key (" + pragmaKey + ") must not contain internal whitespace");
							}
							if (pragmaKey.equals("bootstrapDefiningMethod"))
							{
								_interpreter.bootstrapDefiningMethod(pragmaValue);
							}
							if (pragmaKey.equals("bootstrapSpecialObject"))
							{
								_interpreter.bootstrapSpecialObject(pragmaValue);
							}
						}
					}
					parseTokenTypeStringErrorContinue(
						AvailToken.TokenType.keyword,
						"Extends",
						"Extends keyword",
						new Continuation0 ()
						{
							public void value()
							{
								parseStringsThen(new Continuation1<List<AvailObject>> ()
								{
									public void value(final List<AvailObject> extendsStrings)
									{
										_extends = extendsStrings;
										parseTokenTypeStringErrorContinue(
											AvailToken.TokenType.keyword,
											"Uses",
											"Uses keyword",
											new Continuation0 ()
											{
												public void value()
												{
													parseStringsThen(new Continuation1<List<AvailObject>> ()
													{
														public void value(final List<AvailObject> usesStrings)
														{
															_uses = usesStrings;
															parseTokenTypeStringErrorContinue(
																AvailToken.TokenType.keyword,
																"Names",
																"Names keyword",
																new Continuation0 ()
																{
																	public void value()
																	{
																		parseStringsThen(new Continuation1<List<AvailObject>> ()
																		{
																			public void value(final List<AvailObject> namesStrings)
																			{
																				_names = namesStrings;
																				parseTokenTypeStringErrorContinue(
																					AvailToken.TokenType.keyword,
																					"Body",
																					"Body keyword",
																					new Continuation0 ()
																					{
																						public void value()
																						{
																							where.value = position();
																							ok.value = true;
																							expected("body of module next... (you should never see this message)");
																						}
																					});
																			}
																		});
																	}
																});
														}
													});
												}
											});
									}
								});
							}
						});
					position(savePosition);
				}
			});
		position(where.value);
		return ok.value;
	}

	boolean parseModule ()
	{
		//  Parse a module from the token stream.  It's a big keyword-expression as described
		//  in my class's documentation protocol.

		final Mutable<AvailParseNode> interpretation = new Mutable<AvailParseNode>();
		final Mutable<Integer> endOfStatement = new Mutable<Integer>();
		_interpreter.checkUnresolvedForwards();
		_greatestGuess = 0;
		_greatExpectations = new ArrayList<Generator<String>>();
		if (! parseHeader())
		{
			reportError();
			return false;
		}
		int oldStart = (peekToken() == null ? 1 : (peekToken().start() + 1));
		if (! atEnd())
		{
			_progressBlock.value(1, peekToken().start());
		}
		for (int extendsIndex = 1, _end1 = _extends.size(); extendsIndex <= _end1; extendsIndex++)
		{
			AvailObject modName = _extends.get((extendsIndex - 1));
			AvailObject mod = _interpreter.moduleAt(modName);
			AvailObject modNamesTuple = mod.names().keysAsSet().asTuple();
			for (int modNamesIndex = 1, _end2 = modNamesTuple.tupleSize(); modNamesIndex <= _end2; modNamesIndex++)
			{
				AvailObject strName = modNamesTuple.tupleAt(modNamesIndex);
				AvailObject trueNamesTuple = mod.names().mapAt(strName).asTuple();
				for (int trueNamesIndex = 1, _end3 = trueNamesTuple.tupleSize(); trueNamesIndex <= _end3; trueNamesIndex++)
				{
					AvailObject trueName = trueNamesTuple.tupleAt(trueNamesIndex);
					_module.atNameAdd(strName, trueName);
				}
			}
		}
		for (int index = 1, _end4 = _uses.size(); index <= _end4; index++)
		{
			AvailObject modName = _uses.get((index - 1));
			AvailObject mod = _interpreter.moduleAt(modName);
			AvailObject modNamesTuple = mod.names().keysAsSet().asTuple();
			for (int modNamesIndex = 1, _end5 = modNamesTuple.tupleSize(); modNamesIndex <= _end5; modNamesIndex++)
			{
				AvailObject strName = modNamesTuple.tupleAt(modNamesIndex);
				AvailObject trueNamesTuple = mod.names().mapAt(strName).asTuple();
				for (int trueNamesIndex = 1, _end6 = trueNamesTuple.tupleSize(); trueNamesIndex <= _end6; trueNamesIndex++)
				{
					AvailObject trueName = trueNamesTuple.tupleAt(trueNamesIndex);
					_module.atPrivateNameAdd(strName, trueName);
				}
			}
		}
		for (int index = 1, _end7 = _names.size(); index <= _end7; index++)
		{
			final AvailObject stringObject = _names.get((index - 1));
			final AvailObject trueNameObject = CyclicTypeDescriptor.newCyclicTypeWithName(stringObject);
			_module.atNameAdd(stringObject, trueNameObject);
			_module.atNewNamePut(stringObject, trueNameObject);
		}
		_module.buildFilteredBundleTreeFrom(_interpreter.rootBundleTree());
		_fragmentCache = new AvailCompilerFragmentCache();
		while (! ((peekToken().type() == AvailToken.TokenType.end))) {
			_greatestGuess = 0;
			_greatExpectations = new ArrayList<Generator<String>>();
			interpretation.value = null;
			endOfStatement.value = -1;
			parseStatementAsOutermostCanBeLabelThen(
				true,
				false,
				new Continuation1<AvailParseNode> ()
				{
					public void value(final AvailParseNode stmt)
					{
						assert interpretation.value == null : "Statement parser was supposed to catch ambiguity";
						interpretation.value = stmt;
						endOfStatement.value = position();
					}
				});
			if (interpretation.value == null)
			{
				reportError();
				return false;
			}
			//  Clear the section of the fragment cache associated with the (outermost) statement just parsed...
			privateClearFrags();
			//  Now execute the statement so defining words have a chance to
			//  run.  This lets the defined words be used in subsequent code.
			//  It's even callable in later statements and type expressions.
			position(endOfStatement.value);
			evaluateModuleStatement(interpretation.value);
			if (! atEnd())
			{
				backupToken();
				_progressBlock.value(oldStart, (nextToken().start() + 2));
				oldStart = (peekToken().start() + 1);
			}
		}
		_interpreter.checkUnresolvedForwards();
		return true;
	}



	// private - parsing utilities

	void ambiguousInterpretationsAnd (
			final AvailParseNode interpretation1, 
			final AvailParseNode interpretation2)
	{
		//  A statement was parsed correctly in two different ways (there may be more ways,
		//  but we stop after two as it's already an error).

		expected(new Generator<String> ()
		{
			public String value()
			{
				StringBuilder builder = new StringBuilder(200);
				builder.append("unambiguous interpretation.  Here are two possible parsings...\n");
				builder.append("(tree printing not yet implemented)");
				builder.append("\n\t");
				builder.append(interpretation1.toString());
				builder.append("\n\t");
				builder.append(interpretation2.toString());
				return builder.toString();
			}
		});
	}

	boolean atEnd ()
	{
		return (_position == _tokens.size());
	}

	void backupToken ()
	{
		if ((_position == 0))
		{
			error("Can't backup any more");
			return;
		}
		--_position;
	}

	void expected (
			final Generator<String> stringBlock)
	{
		// System.out.println(Integer.toString(_position) + " expected " + stringBlock.value());
		if ((position() == _greatestGuess))
		{
			_greatExpectations.add(stringBlock);
		}
		if ((position() > _greatestGuess))
		{
			_greatestGuess = position();
			_greatExpectations = new ArrayList<Generator<String>>(2);
			_greatExpectations.add(stringBlock);
		}
	}

	AvailToken nextToken ()
	{
		assert (! atEnd());
		// System.out.println(Integer.toString(_position) + " next = " + _tokens.get(_position));
		++_position;
		return _tokens.get((_position - 1));
	}

	boolean parseTokenIsTypeString (
			final AvailToken.TokenType tokenType, 
			final String string)
	{
		final AvailToken token = peekToken();
		if (((token.type() == tokenType) && token.string().equals(string)))
		{
			nextToken();
			return true;
		}
		return false;
	}

	void parseTokenTypeStringContinue (
			final AvailToken.TokenType tokenType, 
			final String string, 
			final Continuation0 continuationNoArgs)
	{
		final int where = position();
		final AvailCompilerScopeStack oldScope = _scopeStack;
		final AvailToken token = peekToken();
		if (((token.type() == tokenType) && token.string().equals(string)))
		{
			nextToken();
			continuationNoArgs.value();
			backupToken();
		}
		else
		{
			expected(new Generator<String> ()
			{
				public String value()
				{
					return string.toString() + ", not " + token.string().toString();
				}
			});
		}
		assert (where == position());
		assert (oldScope == _scopeStack);
	}

	void parseTokenTypeStringErrorContinue (
			final AvailToken.TokenType tokenType, 
			final String string, 
			final String error, 
			final Continuation0 continuationNoArgs)
	{
		parseTokenTypeStringErrorGeneratorContinue(
			tokenType,
			string,
			wrapInGenerator(error),
			continuationNoArgs);
	}

	void parseTokenTypeStringErrorGeneratorContinue (
			final AvailToken.TokenType tokenType, 
			final String string, 
			final Generator<String> errorGenerator, 
			final Continuation0 continuationNoArgs)
	{
		final int where = position();
		final AvailCompilerScopeStack oldScope = _scopeStack;
		final AvailToken token = peekToken();
		if (((token.type() == tokenType) && token.string().equals(string)))
		{
			nextToken();
			continuationNoArgs.value();
			backupToken();
		}
		else
		{
			expected(errorGenerator);
		}
		if (! ((where == position()) && (oldScope == _scopeStack)))
		{
			error("token stream position and scopeStack were not preserved");
			return;
		}
	}

	AvailToken peekToken ()
	{
		assert (! atEnd());
		// System.out.println(Integer.toString(_position) + " peek = " + _tokens.get(_position));
		return _tokens.get(((_position + 1) - 1));
	}

	boolean peekTokenIsTypeString (
			final AvailToken.TokenType tokenType, 
			final String string)
	{
		final AvailToken token = peekToken();
		return ((token.type() == tokenType) && token.string().equals(string));
	}

	int position ()
	{
		return _position;
	}

	void position (
			final int anInteger)
	{
		_position = anInteger;
	}

	void privateClearFrags ()
	{
		//  Clear the fragment cache.  Only the range minFrag to maxFrag could be notNil.

		_fragmentCache.clear();
	}

	void tryIfUnambiguousThen (
			final Continuation1<Continuation1<AvailParseNode>> tryBlock, 
			final Continuation1<AvailParseNode> continuation)
	{
		//  Execute the block, passing a continuation that it should run upon finding a
		//  local solution.  If exactly one solution is found, unwrap the stack (but not the
		//  token stream position or scopeStack), and pass the result to the continuation.
		//  Otherwise report that an unambiguous statement was expected.

		final Mutable<Integer> count = new Mutable<Integer>();
		final Mutable<AvailParseNode> solution = new Mutable<AvailParseNode>();
		final Mutable<AvailParseNode> anotherSolution = new Mutable<AvailParseNode>();
		final Mutable<Integer> where = new Mutable<Integer>();
		final Mutable<AvailCompilerScopeStack> whatScope = new Mutable<AvailCompilerScopeStack>();
		final int oldPosition = position();
		final AvailCompilerScopeStack oldScope = _scopeStack;
		count.value = 0;
		tryBlock.value(new Continuation1<AvailParseNode> ()
		{
			public void value(final AvailParseNode aSolution)
			{
				if ((count.value == 0))
				{
					solution.value = aSolution;
					where.value = position();
					whatScope.value = _scopeStack;
				}
				else
				{
					if (aSolution == solution.value) error("Same solution was presented twice!");
					anotherSolution.value = aSolution;
				}
				++count.value;
			}
		});
		if ((count.value > 1))
		{
			position(where.value);
			_scopeStack = whatScope.value;
			ambiguousInterpretationsAnd(solution.value, anotherSolution.value);
			position(oldPosition);
			_scopeStack = oldScope;
			return;
		}
		if ((count.value == 0))
		{
			return;
		}
		assert (count.value == 1);
		//  We found exactly one solution.  Advance the token stream just past it, and redo
		//  any side-effects to the scopeStack, then invoke the continuation with the solution.
		//
		//  We need to reset the stream and stack after attempting this.
		position(where.value);
		_scopeStack = whatScope.value;
		continuation.value(solution.value);
		_scopeStack = oldScope;
		position(oldPosition);
	}

	Generator<String> wrapInGenerator (
			final String aString)
	{
		//  Answer a block that will yield aString.  The Java version should create a Generator.

		return new Generator<String> ()
		{
			public String value()
			{
				return aString;
			}
		};
	}



	// scope

	AvailVariableDeclarationNode lookupDeclaration (
			final String name)
	{
		AvailCompilerScopeStack scope = _scopeStack;
		while (! (scope.name() == null)) {
			if (scope.name().equals(name))
			{
				return scope.declaration();
			}
			scope = scope.next();
		}
		return null;
	}

	void popDeclaration ()
	{
		_scopeStack = _scopeStack.next();
	}

	void pushDeclaration (
			final AvailVariableDeclarationNode declaration)
	{
		_scopeStack = new AvailCompilerScopeStack(
			declaration.name().string(),
			declaration,
			_scopeStack);
	}



	// scope / cache

	void clearScopeStack ()
	{
		_scopeStack = new AvailCompilerScopeStack(
			null,
			null,
			null);
	}

	AvailCompilerCachedSolution newCachedSolutionEndPositionScopeStack (
			final AvailParseNode parseNode, 
			final int endPosition, 
			final AvailCompilerScopeStack savedScopeStack)
	{
		return new AvailCompilerCachedSolution(parseNode, endPosition, savedScopeStack);
	}



	// transactions

	void commitModuleTransaction ()
	{
		//  Commit the module that was defined since the most recent
		//  startModuleTransaction.  Simply clear the module instance
		//  variable.

		assert _module != null;
		_interpreter.addModule(_module);
		_module.cleanUpAfterCompile();
		_module = null;
		_interpreter.module(null);
	}

	void rollbackModuleTransaction ()
	{
		//  Rollback the module that was defined since the most recent
		//  startModuleTransaction.  This is done by asking the current
		//  module to remove itself.

		assert _module != null;
		_module.removeFrom(_interpreter);
		_module = null;
		_interpreter.module(null);
	}

	void startModuleTransaction ()
	{
		//  Start definition of a module.  The entire definition can be rolled back
		//  because the interpreter's instance variable 'module' will contain all
		//  methods and precedence rules defined between the transaction
		//  start and the rollback (or commit).  Committing simply clears this
		//  information.

		assert _module == null;
		_module = AvailModuleDescriptor.newModule();
		_interpreter.module(_module);
	}





	// Declare helper "expected(String)" to call "expected(Generator<String>)".
	void expected (final String aString)
	{
		expected(wrapInGenerator(aString));
	}

}
