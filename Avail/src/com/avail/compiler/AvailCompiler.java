/**
 * compiler/AvailCompiler.java
 * Copyright (c) 2010, Mark van Gulik.
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

import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
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
import com.avail.compiler.scanner.AvailScanner;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ByteStringDescriptor;
import com.avail.descriptor.ClosureDescriptor;
import com.avail.descriptor.ContainerDescriptor;
import com.avail.descriptor.CyclicTypeDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.AvailInterpreter;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.newcompiler.LiteralTokenDescriptor;
import com.avail.newcompiler.TokenDescriptor;
import com.avail.newcompiler.TokenDescriptor.TokenType;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.avail.descriptor.AvailObject.*;


/**
 * I parse a source file to create a {@link ModuleDescriptor module}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailCompiler
{
	/**
	 * The {@linkplain L2Interpreter interpreter} to use when evaluating
	 * top-level expressions.
	 */
	final @NotNull L2Interpreter interpreter;

	/**
	 * Construct a new {@link AvailCompiler} which will use the given {@link
	 * AvailInterpreter} to evaluate expressions.
	 *
	 * @param interpreter The interpreter to be used for evaluating expressions.
	 */
	public AvailCompiler (final @NotNull L2Interpreter interpreter)
	{
		this.interpreter = interpreter;
	}

	private List<AvailObject> tokens;
	private int position;
	AvailCompilerScopeStack scopeStack;
	private int greatestGuess;
	private List<Generator<String>> greatExpectations;
	AvailObject module;
	AvailCompilerFragmentCache fragmentCache;
	List<AvailObject> extendedModules;
	List<AvailObject> usedModules;
	List<AvailObject> exportedNames;
	private Continuation3<ModuleName, Long, Long> progressBlock;


	/**
	 * Evaluate an {@link AvailParseNode} in the module's context; lexically
	 * enclosing variables are not considered in scope, but module variables
	 * and constants are in scope.
	 * 
	 * @param expressionNode An {@link AvailParseNode}.
	 * @return The result of generating a {@link ClosureDescriptor closure}
	 *         from the argument and evaluating it.
	 */
	private AvailObject evaluate (
			final AvailParseNode expressionNode)
	{
		//  Evaluate a parse tree node.

		AvailBlockNode block = new AvailBlockNode();
		block.arguments(new ArrayList<AvailVariableDeclarationNode>());
		block.primitive(0);
		List<AvailParseNode> statements;
		statements = new ArrayList<AvailParseNode>(1);
		statements.add(expressionNode);
		block.statements(statements);
		block.resultType(Types.voidType.object());
		block = ((AvailBlockNode)(block.validatedWithInterpreter(interpreter)));
		final AvailCodeGenerator codeGenerator = new AvailCodeGenerator();
		final AvailObject compiledBlock = block.generateOn(codeGenerator);
		//  The block is guaranteed context-free (because imported variables/values are embedded
		//  directly as constants in the generated code), so build a closure with no copied data.
		final AvailObject closure = ClosureDescriptor.newMutableObjectWithCodeAndCopiedTuple(compiledBlock, TupleDescriptor.empty());
		closure.makeImmutable();
		List<AvailObject> args;
		args = new ArrayList<AvailObject>();
		final AvailObject result = interpreter.runClosureArguments(closure, args);
		// System.out.println(Integer.toString(position) + " evaluated (" + expressionNode.toString() + ") = " + result.toString());
		return result;
	}

	private void evaluateModuleStatement (
			final AvailParseNode expressionNode)
	{
		//  Evaluate a parse tree node.  It's a top-level statement in a module.  Declarations are
		//  handled differently - they cause a variable to be declared in the module's scope.

		if (!expressionNode.isDeclaration())
		{
			evaluate(expressionNode);
			return;
		}
		//  It's a declaration...
		final AvailVariableDeclarationNode declarationExpression = ((AvailVariableDeclarationNode)(expressionNode));
		final AvailObject name = declarationExpression.name().string();
		if (declarationExpression.isConstant())
		{
			AvailObject val = evaluate(((AvailInitializingDeclarationNode)(declarationExpression)).initializingExpression());
			module.constantBindings(module.constantBindings().mapAtPuttingCanDestroy(
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
			module.variableBindings(module.variableBindings().mapAtPuttingCanDestroy(
				name,
				var.makeImmutable(),
				true));
		}
	}

	/**
	 * Tokenize the {@linkplain ModuleDescriptor module} specified by the
	 * fully-qualified {@linkplain ModuleName module name}.
	 *
	 * @param qualifiedName
	 *        A fully-qualified {@linkplain ModuleName module name}.
	 * @param stopAfterNamesToken
	 *        Stop scanning after encountering the <em>Names</em> token?
	 * @return The {@linkplain ResolvedModuleName resolved module name}.
	 * @throws AvailCompilerException
	 *         If tokenization failed for any reason.
	 */
	private @NotNull ResolvedModuleName tokenize (
		final @NotNull ModuleName qualifiedName,
		final boolean stopAfterNamesToken)
		throws AvailCompilerException
	{
		final ModuleNameResolver resolver =
			interpreter.runtime().moduleNameResolver();
		final ResolvedModuleName resolvedName = resolver.resolve(qualifiedName);
		if (resolvedName == null)
		{
			throw new AvailCompilerException(
				qualifiedName,
				0,
				"Unable to resolve fully-qualified module name \""
				+ qualifiedName.qualifiedName()
				+ "\" to an existing file");
		}

		final String source;
		try
		{
			final StringBuilder sourceBuilder = new StringBuilder(4096);
			final char[] buffer = new char[4096];
			final Reader reader = new BufferedReader(new FileReader(
				resolvedName.fileReference()));
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
				"Encountered an I/O exception while reading source module \""
				+ qualifiedName.qualifiedName()
				+ "\" (resolved to \""
				+ resolvedName.fileReference().getAbsolutePath()
				+ "\")");
		}

		tokens = new AvailScanner().scanString(source, stopAfterNamesToken);
		return resolvedName;
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} and install it into the
	 * {@linkplain AvailRuntime runtime}.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ModuleName qualified name} of the {@linkplain
	 *        ModuleDescriptor source module}.
	 * @param aBlock
	 *        A {@linkplain Continuation3 continuation} that accepts the
	 *        {@linkplain ModuleName name} of the {@linkplain ModuleDescriptor
	 *        module} undergoing {@linkplain AvailCompiler compilation}, the
	 *        position of the ongoing parse (in bytes), and the size of the
	 *        module (in bytes).
	 * @throws AvailCompilerException
	 *         If compilation fails.
	 */
	public void parseModule (
			final @NotNull ModuleName qualifiedName,
			final @NotNull Continuation3<ModuleName, Long, Long> aBlock)
		throws AvailCompilerException
	{
		progressBlock = aBlock;
		greatestGuess = -1;
		greatExpectations = null;
		clearScopeStack();
		position = 0;
		final ResolvedModuleName resolvedName = tokenize(qualifiedName, false);

		startModuleTransaction();
		try
		{
			parseModule(resolvedName);
		}
		catch (final AvailCompilerException e)
		{
			rollbackModuleTransaction();
			throw e;
		}
		assert peekToken().tokenType() == TokenType.END_OF_FILE
			: "Expected end of text, not " + peekToken().string();
		commitModuleTransaction();
	}

	/**
	 * Parse a {@linkplain ModuleDescriptor module} header from the
	 * specified string. Populate {@link #extendedModules} and {@link
	 * #usedModules}.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ModuleName qualified name} of the {@linkplain
	 *        ModuleDescriptor source module}.
	 * @throws AvailCompilerException
	 *         If compilation fails.
	 * @author Todd L Smith &lt;anarkul@gmail.com&gt;
	 */
	void parseModuleHeader (final @NotNull ModuleName qualifiedName)
		throws AvailCompilerException
	{
		progressBlock = null;
		greatestGuess = -1;
		greatExpectations = null;
		clearScopeStack();
		position = 0;
		final ResolvedModuleName resolvedName = tokenize(qualifiedName, true);
		if (!parseHeaderForDependenciesOnly(resolvedName))
		{
			reportError(resolvedName);
			assert false;
		}
		assert peekToken().tokenType() == TokenType.END_OF_FILE
			: "Expected end of text, not " + peekToken().string();
	}

	/**
	 * Report an error by throwing an {@link AvailCompilerException}. The
	 * exception encapsulates the {@linkplain ModuleName module name} of the
	 * {@linkplain ModuleDescriptor module} undergoing compilation, the error
	 * string, and the text position. This position is the rightmost position
	 * encountered during the parse, and the error strings in {@link
	 * #greatExpectations} are the things that were expected but not found at
	 * that position.  This seems to work very well in practice.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ModuleName qualified name} of the {@linkplain
	 *        ModuleDescriptor source module}.
	 * @throws AvailCompilerException
	 *         Always thrown.
	 */
	private void reportError (final @NotNull ModuleName qualifiedName)
		throws AvailCompilerException
	{
		int tokenPosSave = position();
		position(greatestGuess);
		long charPos = peekToken().start();
		position(tokenPosSave);
		StringBuilder text = new StringBuilder(100);
		text.append("<-- Expected...\n");
		assert greatExpectations.size() > 0 : "Bug - empty expectation list";
		Set<String> alreadySeen = new HashSet<String>(greatExpectations.size());
		for (final Generator<String> generator : greatExpectations)
		{
			final String str = generator.value();
			if (!alreadySeen.contains(str))
			{
				alreadySeen.add(str);
				text.append("\t");
				text.append(str.replace("\n", "\n\t\t"));
				text.append("\n");
			}
		}
		throw new AvailCompilerException(qualifiedName, charPos, text.toString());
	}



	// private - parsing body

	void parseAdditionalBlockArgumentsAfterThen (
			final List<AvailVariableDeclarationNode> argsSoFar,
			final Continuation1<List<AvailVariableDeclarationNode>> continuation)
	{
		//  Parse more of a block's formal arguments from the token stream.  A verticalBar
		//  is required after the arguments if there are any (which there are if we're here).

		parseTokenTypeStringErrorContinue(
			TokenType.OPERATOR,
			",",
			"comma and more block arguments",
			new Continuation0 ()
			{
				@Override
				public void value()
				{
					parseBlockArgumentThen(new Continuation1<AvailVariableDeclarationNode> ()
					{
						@Override
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
			TokenType.OPERATOR,
			"|",
			"comma and more block arguments or a vertical bar",
			new Continuation0 ()
			{
				@Override
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
		final AvailCompilerScopeStack oldStack = scopeStack;
		clearScopeStack();
		maxExprs.value = new ArrayList<AvailParseNode>();
		maxPos.value = -1;
		parseExpressionThen(new Continuation1<AvailParseNode> ()
		{
			@Override
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
						@Override
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
			scopeStack = oldStack;
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
		scopeStack = oldStack;
		position(oldPosition);
	}

	void parseAssignmentThen (
			final Continuation1<AvailAssignmentNode> continuation)
	{
		//  Since this is now a statement instead of an expression, we don't need
		//  a special rule about rightmost parse anymore (it must be followed by a
		//  semicolon).

		if ((peekToken().tokenType() == TokenType.KEYWORD))
		{
			parseVariableUseWithExplanationThen("for an assignment", new Continuation1<AvailVariableUseNode> ()
			{
				@Override
				public void value(final AvailVariableUseNode varUse)
				{
					parseTokenTypeStringErrorContinue(
						TokenType.OPERATOR,
						":",
						":= for assignment",
						new Continuation0 ()
						{
							@Override
							public void value()
							{
								parseTokenTypeStringErrorContinue(
									TokenType.OPERATOR,
									"=",
									"= part of := for assignment",
									new Continuation0 ()
									{
										@Override
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
													@Override
													public void value(final AvailParseNode expr)
													{
														if ((peekToken().tokenType() == TokenType.END_OF_STATEMENT))
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
																	@Override
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

	private void parseBlockArgumentsThen (
			final Continuation1<List<AvailVariableDeclarationNode>> continuation)
	{
		//  Parse a block's formal arguments from the token stream.  A verticalBar
		//  is required after the arguments if there are any.

		continuation.value(new ArrayList<AvailVariableDeclarationNode>());
		parseBlockArgumentThen(new Continuation1<AvailVariableDeclarationNode> ()
		{
			@Override
			public void value(final AvailVariableDeclarationNode firstArg)
			{
				List<AvailVariableDeclarationNode> argsList;
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

		final AvailObject localName = peekToken();
		if ((localName.tokenType() == TokenType.KEYWORD))
		{
			nextToken();
			parseTokenTypeStringErrorContinue(
				TokenType.OPERATOR,
				":",
				": then argument type",
				new Continuation0 ()
				{
					@Override
					public void value()
					{
						parseAndEvaluateExpressionYieldingInstanceOfThen(Types.type.object(), new Continuation1<AvailObject> ()
						{
							@Override
							public void value(final AvailObject type)
							{
								AvailCompilerScopeStack oldScope;
								if (type.equals(Types.voidType.object()))
								{
									expected("a type for the argument other than void");
								}
								else if (type.equals(Types.terminates.object()))
								{
									expected("a type for the argument other than terminates");
								}
								else
								{
									final AvailVariableDeclarationNode decl = new AvailVariableDeclarationNode();
									decl.name(localName);
									decl.declaredType(type);
									decl.isArgument(true);
									oldScope = scopeStack;
									pushDeclaration(decl);
									continuation.value(decl);
									scopeStack = oldScope;
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

	private void parseBlockThen (
			final Continuation1<AvailBlockNode> continuation)
	{
		//  Parse a block from the token stream.

		if (peekTokenIsTypeString(TokenType.OPERATOR, "["))
		{
			final AvailCompilerScopeStack scopeOutsideBlock = scopeStack;
			nextToken();
			parseBlockArgumentsThen(new Continuation1<List<AvailVariableDeclarationNode>> ()
			{
				@Override
				public void value(final List<AvailVariableDeclarationNode> arguments)
				{
					parseOptionalPrimitiveForArgCountThen(arguments.size(), new Continuation1<Short> ()
					{
						@Override
						public void value(final Short primitive)
						{
							parseStatementsThen(new Continuation1<List<AvailParseNode>> ()
							{
								@Override
								public void value(final List<AvailParseNode> statements)
								{
									if (((primitive != 0) && ((primitive != 256) && statements.isEmpty())))
									{
										expected("mandatory failure code for primitive method (except #256)");
									}
									else
									{
										parseTokenTypeStringErrorContinue(
											TokenType.OPERATOR,
											"]",
											"close bracket to end block",
											new Continuation0 ()
											{
												@Override
												public void value()
												{
													final Mutable<AvailObject> lastStatementType = new Mutable<AvailObject>();
													if ((statements.size() > 0))
													{
														final AvailParseNode lastStatement = statements.get((statements.size() - 1));
														if ((lastStatement.isDeclaration() || lastStatement.isAssignment()))
														{
															lastStatementType.value = Types.voidType.object();
														}
														else
														{
															lastStatementType.value = lastStatement.type();
														}
													}
													else
													{
														lastStatementType.value = Types.voidType.object();
													}
													final AvailCompilerScopeStack scopeInBlock = scopeStack;
													scopeStack = scopeOutsideBlock;
													parseTokenTypeStringErrorContinue(
														TokenType.OPERATOR,
														":",
														"optional block return type declaration",
														new Continuation0 ()
														{
															@Override
															public void value()
															{
																parseAndEvaluateExpressionYieldingInstanceOfThen(Types.type.object(), new Continuation1<AvailObject> ()
																{
																	@Override
																	public void value(final AvailObject returnType)
																	{
																		if ((statements.isEmpty() && primitive != null) || lastStatementType.value.isSubtypeOf(returnType))
																		{
																			boolean blockTypeGood = true;
																			if (statements.size() > 0 && statements.get(0).isLabel())
																			{
																				AvailObject labelClosureType = ((AvailLabelNode)statements.get(0)).declaredType().closureType();
																				blockTypeGood = labelClosureType.numArgs() == arguments.size()
																					&& labelClosureType.returnType().equals(returnType);
																				if (blockTypeGood)
																				{
																					for (int i = 1, _end1 = arguments.size(); i <= _end1; i++)
																					{
																						if (!labelClosureType.argTypeAt(i).equals(arguments.get(i - 1).declaredType()))
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
																				@Override
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
																	if (!labelClosureType.argTypeAt(i).equals(arguments.get(i - 1).declaredType()))
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
													scopeStack = scopeInBlock;
												}
											});
									}
								}
							});
						}
					});
				}
			});
			scopeStack = scopeOutsideBlock;
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
		final AvailCompilerScopeStack oldScope = scopeStack;
		final AvailObject localName = peekToken();
		if (localName.tokenType() == TokenType.KEYWORD)
		{
			nextToken();
			parseTokenTypeStringErrorContinue(
				TokenType.OPERATOR,
				":",
				": or ::= for simple / constant / initializing declaration",
				new Continuation0 ()
				{
					@Override
					public void value()
					{
						parseTokenTypeStringErrorContinue(
							TokenType.OPERATOR,
							":",
							"second colon for constant declaration (a ::= expr)",
							new Continuation0 ()
							{
								@Override
								public void value()
								{
									parseTokenTypeStringErrorContinue(
										TokenType.OPERATOR,
										"=",
										"= part of ::= in constant declaration",
										new Continuation0 ()
										{
											@Override
											public void value()
											{
												parseExpressionThen(new Continuation1<AvailParseNode> ()
												{
													@Override
													public void value(final AvailParseNode initExpr)
													{
														final AvailConstantDeclarationNode constantDeclaration = new AvailConstantDeclarationNode();
														constantDeclaration.name(localName);
														constantDeclaration.declaredType(initExpr.type());
														constantDeclaration.initializingExpression(initExpr);
														constantDeclaration.isArgument(false);
														pushDeclaration(constantDeclaration);
														continuation.value(constantDeclaration);
														scopeStack = oldScope;
													}
												});
											}
										});
								}
							});
						parseAndEvaluateExpressionYieldingInstanceOfThen(Types.type.object(), new Continuation1<AvailObject> ()
						{
							@Override
							public void value(final AvailObject type)
							{
								if ((type.equals(Types.voidType.object()) || type.equals(Types.terminates.object())))
								{
									expected("a type for the variable other than void or terminates");
								}
								else
								{
									parseTokenTypeStringContinue(
										TokenType.OPERATOR,
										":",
										new Continuation0 ()
										{
											@Override
											public void value()
											{
												parseTokenTypeStringContinue(
													TokenType.OPERATOR,
													"=",
													new Continuation0 ()
													{
														@Override
														public void value()
														{
															parseExpressionThen(new Continuation1<AvailParseNode> ()
															{
																@Override
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
																		scopeStack = oldScope;
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
									scopeStack = oldScope;
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

		if (peekTokenIsTypeString(TokenType.OPERATOR, ","))
		{
			parseTokenTypeStringErrorContinue(
				TokenType.OPERATOR,
				",",
				"comma for a list",
				new Continuation0 ()
				{
					@Override
					public void value()
					{
						parseExpressionListItemThen(new Continuation1<AvailParseNode> ()
						{
							@Override
							public void value(final AvailParseNode newItem)
							{
								List<AvailParseNode> newItems;
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
			@Override
			public void value(final AvailSendNode sendNode)
			{
				parseOptionalLeadingArgumentSendAfterThen(sendNode, continuation);
			}
		});
		parseSimpleThen(new Continuation1<AvailParseNode> ()
		{
			@Override
			public void value(final AvailParseNode simpleNode)
			{
				parseOptionalLeadingArgumentSendAfterThen(simpleNode, continuation);
			}
		});
		parseBlockThen(new Continuation1<AvailBlockNode> ()
		{
			@Override
			public void value(final AvailBlockNode blockNode)
			{
				parseOptionalLeadingArgumentSendAfterThen(blockNode, continuation);
			}
		});
	}

	/**
	 * Parse an expression.  Backtracking will find all valid interpretations.
	 * Note that a list expression requires at least two terms to form a list
	 * node.  This method is a key optimization point, so the fragmentCache is
	 * used to keep track of parsing solutions at this point, simply replaying
	 * them on subsequent parses, as long as the variable declarations up to
	 * that point were identical.
	 * 
	 * @param originalContinuation What to do with the expression.
	 */
	void parseExpressionThen (
			final Continuation1<AvailParseNode> originalContinuation)
	{
		final int start = position();
		final AvailCompilerScopeStack originalScope = scopeStack;
		if (!fragmentCache.hasComputedTokenPositionScopeStack(start, originalScope))
		{
			fragmentCache.startComputingTokenPositionScopeStack(start, originalScope);
			final Continuation1<AvailParseNode> justRecord = new Continuation1<AvailParseNode> ()
			{
				@Override
				public void value(final AvailParseNode expr)
				{
					fragmentCache.atTokenPositionScopeStackAddSolution(
						start,
						originalScope,
						newCachedSolutionEndPositionScopeStack(
							expr,
							position(),
							scopeStack));
				}
			};
			parseExpressionListItemThen(new Continuation1<AvailParseNode> ()
			{
				@Override
				public void value(final AvailParseNode firstItem)
				{
					justRecord.value(firstItem);
					List<AvailParseNode> itemList;
					itemList = new ArrayList<AvailParseNode>(1);
					itemList.add(firstItem);
					parseExpressionListItemsBeyondThen(itemList, justRecord);
				}
			});
			if (!(position() == start && scopeStack == originalScope))
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
		final List<AvailCompilerCachedSolution> solutions =
			fragmentCache.atTokenPositionScopeStack(start, originalScope);
		for (final AvailCompilerCachedSolution solution : solutions)
		{
			position(solution.endPosition());
			scopeStack = solution.scopeStack();
			originalContinuation.value(solution.parseNode());
		}
		scopeStack = originalScope;
		position(start);
	}

	/**
	 * We've parsed part of a send.  Try to finish the job.
	 * @param bundleTree The bundle tree used to parse at this position.
	 * @param firstArgOrNull Either null or an argument that must be consumed
	 *                       before any keywords (or completion of a send).
	 * @param initialTokenPosition The parse position where the send node
	 *                             started to be processed.  Does not count the
	 *                             position of the first argument if there are
	 *                             no leading keywords.
	 * @param argsSoFar The collection of arguments parsed so far.  I do not
	 *                  modify it.
	 * @param continuation What to do with a fully parsed send node.
	 */
	void parseRestOfSendNode (
			final AvailObject bundleTree,
			final AvailParseNode firstArgOrNull,
			final int initialTokenPosition,
			final List<AvailParseNode> argsSoFar,
			final Continuation1<AvailSendNode> continuation)
	{
		final int start = position();
		final AvailObject complete = bundleTree.complete();
		final AvailObject incomplete = bundleTree.incomplete();
		final AvailObject special = bundleTree.specialActions();
		final int completeMapSize = complete.mapSize();
		final int incompleteMapSize = incomplete.mapSize();
		final int specialSize = special.mapSize();
		assert completeMapSize + incompleteMapSize + specialSize > 0
		: "Expected a nonempty list of possible messages";
		if (completeMapSize != 0 && firstArgOrNull == null)
		{
			complete.mapDo(new Continuation2<AvailObject, AvailObject>()
			{
				@Override
				public void value (
					final AvailObject message,
					final AvailObject bundle)
				{
					if (interpreter.runtime().hasMethodsAt(message))
					{
						final Mutable<Boolean> valid = new Mutable<Boolean>();
						final AvailObject impSet =
							interpreter.runtime().methodsAt(message);
						valid.value = true;
						List<AvailObject> typesSoFar;
						typesSoFar = new ArrayList<AvailObject>(argsSoFar.size());
						for (AvailParseNode arg : argsSoFar)
						{
							typesSoFar.add(arg.type());
						}
						final AvailObject returnType =
							interpreter.validateTypesOfMessageSendArgumentTypesIfFail(
								message,
								typesSoFar,
								new Continuation1<Generator<String>> ()
								{
									@Override
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
			});
		}
		if (incompleteMapSize != 0 && firstArgOrNull == null)
		{
			final AvailObject keywordToken = peekToken();
			if (keywordToken.tokenType() == TokenType.KEYWORD
					|| keywordToken.tokenType() == TokenType.OPERATOR)
			{
				final AvailObject keywordString = keywordToken.string();
				if (incomplete.hasKey(keywordString))
				{
					nextToken();
					final AvailObject subtree = incomplete.mapAt(keywordString);
					parseRestOfSendNode(
						subtree,
						null,
						initialTokenPosition,
						argsSoFar,
						continuation);
					backupToken();
				}
				else
				{
					expectedKeywordsOf(incomplete);
				}
			}
			else
			{
				expectedKeywordsOf(incomplete);
			}
		}
		if (specialSize != 0)
		{
			// Interpret a parse instruction.
			special.mapDo(new Continuation2<AvailObject, AvailObject>()
			{
				@Override
				public void value (
					final AvailObject instructionObject,
					final AvailObject successorTrees)
				{
					int instruction = instructionObject.extractInt();
					switch (instruction)
					{
						case 0:
						{
							// Parse an argument and recurse.
							assert successorTrees.tupleSize() == 1;
							final AvailObject successorTree = successorTrees.tupleAt(1);
							parseSendArgumentWithExplanationThen(
								" (an argument of some message)",
								firstArgOrNull,
								initialTokenPosition,
								new Continuation1<AvailParseNode> ()
								{
									@Override
									public void value(final AvailParseNode newArg)
									{
										Continuation1<AvailSendNode> continueAfterFiltering = continuation;
										if (newArg.isSend())
										{
											continueAfterFiltering = new Continuation1<AvailSendNode> ()
											{
												@Override
												public void value(final AvailSendNode outerSend)
												{
													final AvailObject restrictions = outerSend.bundle().restrictions().tupleAt(argsSoFar.size() + 1);
													if (restrictions.hasElement(((AvailSendNode)newArg).message()))
													{
														expected(new Generator<String> ()
														{
															@Override
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
										List<AvailParseNode> newArgsSoFar = new ArrayList<AvailParseNode>(argsSoFar);
										newArgsSoFar.add(newArg);
										parseRestOfSendNode(
											successorTree,
											null,
											initialTokenPosition,
											newArgsSoFar,
											continueAfterFiltering);
									}
								});
							break;
						}
						case 1:
						{
							// Push an empty list node.
							assert successorTrees.tupleSize() == 1;
							List<AvailParseNode> newArgsSoFar =
								new ArrayList<AvailParseNode>(argsSoFar); 
							AvailListNode newListNode = new AvailListNode();
							newListNode.expressions(
								Collections.<AvailParseNode>emptyList());
							newArgsSoFar.add(newListNode);
							parseRestOfSendNode(
								successorTrees.tupleAt(1),
								null,
								initialTokenPosition,
								newArgsSoFar,
								continuation);
							break;
						}
						case 2:
						{
							// Append the item that's the last thing to the
							// list that's the second last thing.  Pop both and
							// push the new list (the original list must not
							// change).
							assert successorTrees.tupleSize() == 1;
							List<AvailParseNode> newArgsSoFar =
								new ArrayList<AvailParseNode>(argsSoFar);
							AvailParseNode value =
								newArgsSoFar.remove(newArgsSoFar.size() - 1);
							AvailListNode oldListNode =
								(AvailListNode)newArgsSoFar.remove(
									newArgsSoFar.size() - 1);
							AvailParseNode newListNode =
								oldListNode.copyWith(value);
							newArgsSoFar.add(newListNode);
							parseRestOfSendNode(
								successorTrees.tupleAt(1),
								null,
								initialTokenPosition,
								newArgsSoFar,
								continuation);
							break;
						}
						case 3:
						{
							// Reserved
							error("Illegal parsing instruction");
							break;
						}
						default:
						{
							// Branch or jump, or else we shouldn't be here.
							assert (instruction & 3) <= 1;
							for (AvailObject successorTree : successorTrees)
							{
								parseRestOfSendNode(
									successorTree,
									null,
									initialTokenPosition,
									argsSoFar,
									continuation);
								assert start == position();
							}
						}
					}
				}
			});
		}
		assert start == position();
	}

	/**
	 * Report that the parser was expecting one of several keywords.  The
	 * keywords are keys of the {@link MapDescriptor map} argument {@code
	 * incomplete}.
	 * 
	 * @param incomplete A map of partially parsed keywords, where the keys are
	 *                   the strings that were expected at this position.
	 */
	private void expectedKeywordsOf (
		final AvailObject incomplete)
	{
		expected(new Generator<String> ()
		{
			@Override
			public String value()
			{
				final StringBuilder builder = new StringBuilder(200);
				builder.append("one of the following internal keywords: ");
				final List<String> sorted = new ArrayList<String>(
					incomplete.mapSize());
				incomplete.mapDo(new Continuation2<AvailObject, AvailObject>()
					{
						@Override
						public void value (AvailObject key, AvailObject value)
						{
							sorted.add(key.asNativeString());
						}
					});
				Collections.sort(sorted);
				if (incomplete.mapSize() > 5)
				{
					builder.append("\n\t");
				}
				for (String s : sorted)
				{
					builder.append(s);
					builder.append("  ");
				}
				return builder.toString();
			}
		});
	}

	/**
	 * Parse a label declaration, then invoke the continuation.
	 * 
	 * @param continuation What to do after parsing a label.
	 */
	void parseLabelThen (
			final Continuation1<AvailLabelNode> continuation)
	{
		parseTokenTypeStringErrorContinue(
			TokenType.OPERATOR,
			"$",
			"label statement starting with \"$\"",
			new Continuation0 ()
			{
				@Override
				public void value()
				{
					final AvailObject token = peekToken();
					if ((token.tokenType() == TokenType.KEYWORD))
					{
						nextToken();
						parseTokenTypeStringErrorContinue(
							TokenType.OPERATOR,
							":",
							"colon for label's type declaration",
							new Continuation0 ()
							{
								@Override
								public void value()
								{
									parseAndEvaluateExpressionYieldingInstanceOfThen(Types.continuationType.object(), new Continuation1<AvailObject> ()
									{
										@Override
										public void value(final AvailObject contType)
										{
											final AvailLabelNode label = new AvailLabelNode();
											label.name(token);
											label.declaredType(contType);
											final AvailCompilerScopeStack oldScope = scopeStack;
											pushDeclaration(label);
											continuation.value(label);
											scopeStack = oldScope;
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

	
	/**
	 * Parse a send node whose leading argument has already been parsed.
	 * 
	 * @param leadingArgument The argument that was already parsed.
	 * @param continuation What to do after parsing a send node.
	 */
	void parseLeadingArgumentSendAfterThen (
			final AvailParseNode leadingArgument,
			final Continuation1<AvailSendNode> continuation)
	{
		parseRestOfSendNode(
			interpreter.rootBundleTree(),
			leadingArgument,
			position,
			Collections.<AvailParseNode>emptyList(),
			continuation);
	}

	
	/**
	 * Parse a send node.  To prevent infinite left-recursion and false
	 * ambiguity, we only allow a send with a leading keyword to be parsed from
	 * here, since leading underscore sends are dealt with iteratively
	 * afterward.
	 * 
	 * @param continuation What to do after parsing a complete send node.
	 */
	private void parseLeadingKeywordSendThen (
			final Continuation1<AvailSendNode> continuation)
	{
		parseRestOfSendNode(
			interpreter.rootBundleTree(),
			null,
			position,
			Collections.<AvailParseNode>emptyList(),
			continuation);
	}


	void parseOptionalLeadingArgumentSendAfterThen (
			final AvailParseNode node,
			final Continuation1<AvailParseNode> continuation)
	{
		//  Parse an expression that isn't a list.  Backtracking will find all valid interpretations.

		continuation.value(node);
		if (node.type().equals(Types.voidType.object()))
		{
			return;
		}
		parseOptionalSuperCastAfterErrorSuffixThen(
			node,
			" in case it's the first argument of a non-keyword-leading message",
			new Continuation1<AvailParseNode> ()
			{
				@Override
				public void value(final AvailParseNode cast)
				{
					parseLeadingArgumentSendAfterThen(cast, new Continuation1<AvailSendNode> ()
					{
						@Override
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
			TokenType.KEYWORD,
			"Primitive",
			"primitive declaration",
			new Continuation0 ()
			{
				@Override
				public void value()
				{
					final AvailObject token = peekToken();
					if (token.tokenType() == TokenType.LITERAL
							&& token.literal().isInstanceOfSubtypeOf(
								IntegerRangeTypeDescriptor.integers()))
					{
						nextToken();
						num.value = (short)(token.literal().extractInt());
						if (interpreter.supportsPrimitive(num.value))
						{
							if ((interpreter.argCountForPrimitive(num.value) == argCount))
							{
								parseTokenTypeStringContinue(
									TokenType.END_OF_STATEMENT,
									";",
									new Continuation0 ()
									{
										@Override
										public void value()
										{
											continuation.value(num.value);
										}
									});
							}
							else
							{
								final int expectedArgCount = interpreter.argCountForPrimitive(num.value);
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
		if (num.value == 0)
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
		if (peekTokenIsTypeString(TokenType.OPERATOR, ":"))
		{
			nextToken();
			parseTokenTypeStringErrorGeneratorContinue(
				TokenType.OPERATOR,
				":",
				new Generator<String> ()
				{
					@Override
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
					@Override
					public void value()
					{
						parseAndEvaluateExpressionYieldingInstanceOfThen(Types.type.object(), new Continuation1<AvailObject> ()
						{
							@Override
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

	/**
	 * Parse an argument to a message send.  Backtracking will find all valid
	 * interpretations.  
	 * 
	 * @param explanation A {@link String} indicating why it's parsing an
	 *                    argument.
	 * @param firstArgOrNull Either a parse node to use as the argument, or
	 *                       null if we should parse one now. 
	 * @param initialTokenPosition The position at which we started parsing the
	 *                             message send.  Does not include the first
	 *                             argument if there were no leading keywords.
	 * @param continuation What to do with the argument.
	 */
	void parseSendArgumentWithExplanationThen (
			final String explanation,
			final AvailParseNode firstArgOrNull,
			final int initialTokenPosition,
			final Continuation1<AvailParseNode> continuation)
	{
		if (firstArgOrNull == null)
		{
			// There was no leading argument.  If we haven't parsed any keywords
			// then don't allow this argument parse to happen, since we must be
			// trying to parse a leading-keyword message send.
			if (position != initialTokenPosition)
			{
				parseExpressionThen(new Continuation1<AvailParseNode> ()
				{
					@Override
					public void value(final AvailParseNode expr)
					{
						parseOptionalSuperCastAfterErrorSuffixThen(
							expr,
							explanation,
							continuation);
					}
				});
			}
		}
		else
		{
			// We're parsing a message send with a leading argument.  There
			// should have been no way to parse any keywords or other arguments
			// yet, so make sure the position hasn't budged since we started.
			// Then use the provided first argument.
			assert position == initialTokenPosition;
			parseOptionalSuperCastAfterErrorSuffixThen(
				firstArgOrNull,
				explanation,
				continuation);
		}
	}

	/**
	 * Parse a variable, reference, or literal, then invoke the continuation.
	 * 
	 * @param continuation What to do with the simple parse node.
	 */
	private void parseSimpleThen (
			final Continuation1<? super AvailParseNode> continuation)
	{
		final int originalPosition = position;
		parseVariableUseWithExplanationThen("", continuation);
		if ((peekToken().tokenType() == TokenType.LITERAL))
		{
			final AvailLiteralNode literalNode = new AvailLiteralNode();
			literalNode.token(nextToken());
			continuation.value(literalNode);
			backupToken();
		}
		if (peekTokenIsTypeString(TokenType.OPERATOR, "&"))
		{
			nextToken();
			parseVariableUseWithExplanationThen("in reference expression", new Continuation1<AvailVariableUseNode> ()
			{
				@Override
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
		assert (position == originalPosition);
	}

	private void parseStatementAsOutermostCanBeLabelThen (
			final boolean outermost,
			final boolean canBeLabel,
			final Continuation1<AvailParseNode> continuation)
	{
		//  Parse a statement.  This is the boundary for the backtracking grammar.
		//  A statement must be unambiguous (in isolation) to be valid.  The passed
		//  continuation will be invoked at most once, and only if the statement had
		//  a single interpretation.

		assert !(outermost & canBeLabel);
		tryIfUnambiguousThen(new Continuation1<Continuation1<AvailParseNode>> ()
		{
			@Override
			public void value(final Continuation1<AvailParseNode> whenFoundStatement)
			{
				if (canBeLabel)
				{
					parseLabelThen(new Continuation1<AvailLabelNode> ()
					{
						@Override
						public void value(final AvailLabelNode label)
						{
							parseTokenTypeStringErrorContinue(
								TokenType.END_OF_STATEMENT,
								";",
								"; to end label statement",
								new Continuation0 ()
								{
									@Override
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
					@Override
					public void value(final AvailVariableDeclarationNode declaration)
					{
						parseTokenTypeStringErrorContinue(
							TokenType.END_OF_STATEMENT,
							";",
							"; to end declaration statement",
							new Continuation0 ()
							{
								@Override
								public void value()
								{
									whenFoundStatement.value(declaration);
								}
							});
					}
				});
				parseAssignmentThen(new Continuation1<AvailAssignmentNode> ()
				{
					@Override
					public void value(final AvailAssignmentNode assignment)
					{
						parseTokenTypeStringErrorContinue(
							TokenType.END_OF_STATEMENT,
							";",
							"; to end assignment statement",
							new Continuation0 ()
							{
								@Override
								public void value()
								{
									whenFoundStatement.value(assignment);
								}
							});
					}
				});
				parseExpressionThen(new Continuation1<AvailParseNode> ()
				{
					@Override
					public void value(final AvailParseNode expr)
					{
						parseTokenTypeStringErrorContinue(
							TokenType.END_OF_STATEMENT,
							";",
							"; to end statement",
							new Continuation0 ()
							{
								@Override
								public void value()
								{
									if ((!outermost) || expr.type().equals(Types.voidType.object()))
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
			@Override
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
		final AvailCompilerScopeStack oldScope = scopeStack;
		List<AvailParseNode> statements;
		statements = new ArrayList<AvailParseNode>(10);
		while (true)
		{
			final Mutable<Boolean> ok = new Mutable<Boolean>();
			ok.value = false;
			boolean problem = false;
			if ((statements.size() > 0))
			{
				final AvailParseNode lastStatement = statements.get((statements.size() - 1));
				if (!lastStatement.isAssignment()
					&& !lastStatement.isDeclaration()
					&& !lastStatement.isLabel())
				{
					if (lastStatement.type().equals(Types.terminates.object()))
					{
						problem = true;
						expected("previous statement to be the last statement because it always terminates");
					}
					if (!lastStatement.type().equals(Types.voidType.object()))
					{
						problem = true;
						expected(new Generator<String> ()
						{
							@Override
							public String value()
							{
								return "non-last statement \"" + lastStatement.toString() + "\" to have type void, not \"" + lastStatement.type().toString() + "\".";
							}
						});
					}
				}
			}
			if (!problem)
			{
				parseStatementAsOutermostCanBeLabelThen(
					false,
					statements.isEmpty(),
					new Continuation1<AvailParseNode> ()
					{
						@Override
						public void value(final AvailParseNode stmt)
						{
							postPosition.value = position();
							postScope.value = scopeStack;
							theStatement.value = stmt;
							ok.value = true;
						}
					});
			}
			if (!ok.value)
			{
				break;
			}
			position(postPosition.value);
			scopeStack = postScope.value;
			statements.add(theStatement.value);
		}
		continuation.value(statements);
		expected("more statements");
		position(where);
		scopeStack = oldScope;
	}

	/**
	 * Parse a string literal.  Answer the Avail {@link ByteStringDescriptor
	 * string} itself if found, otherwise answer null.
	 * 
	 * @return The actual Avail {@link ByteStringDescriptor string} or null.
	 */
	AvailObject parseStringLiteral ()
	{
		final AvailObject token = peekToken();
		if (!(token.descriptor() instanceof LiteralTokenDescriptor))
		{
			return null;
		}
		final AvailObject stringObject = token.literal();
		if (!stringObject.isString())
		{
			return null;
		}
		nextToken();
		return stringObject;
	}

	/**
	 * Parse one or more string literals separated by commas.  This parse isn't
	 * backtracking like the rest of the grammar - it's greedy.  It considers a
	 * comma followed by something other than a string literal to be an
	 * unrecoverable parsing error (not a backtrack).
	 * 
	 * @return The list of {@link ByteStringDescriptor strings}.
	 */
	List<AvailObject> parseStrings ()
	{
		List<AvailObject> list;
		list = new ArrayList<AvailObject>();
		AvailObject string = parseStringLiteral();
		if (string == null)
		{
			return list;
		}
		list.add(string);
		while (parseTokenIsTypeString(TokenType.OPERATOR, ",")) {
			string = parseStringLiteral();
			if (string == null)
			{
				error("Problem parsing literal strings");
				return null;
			}
			list.add(string);
		}
		return list;
	}

	/**
	 * Parse one or more string literals separated by commas.  This parse isn't
	 * like the rest of the grammar - it's greedy.  It considers a comma
	 * followed by something other than a string literal to be an unrecoverable
	 * parsing error (not a backtrack).
	 * 
	 * @param continuation What to do after parsing the strings.
	 */
	void parseStringsThen (
			final Continuation1<List<AvailObject>> continuation)
	{
		final int where = position();
		final List<AvailObject> strings = parseStrings();
		continuation.value(strings);
		position(where);
	}

	/**
	 * Parse the use of a variable.
	 * 
	 * @param explanation The string explaining why we were parsing a use of a
	 *                    variable.
	 * @param continuation What to do after parsing the variable use.
	 */
	private void parseVariableUseWithExplanationThen (
			final String explanation,
			final Continuation1<? super AvailVariableUseNode> continuation)
	{

		if (peekToken().tokenType() != TokenType.KEYWORD)
		{
			return;
		}
		final AvailObject token = nextToken();
		assert (token.tokenType() == TokenType.KEYWORD) : "Supposed to be a keyword here";
		//  First check if it's in a block scope...
		final AvailVariableDeclarationNode localDecl = lookupDeclaration(
			token.string());
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
		final AvailObject varName = token.string();
		if (module.variableBindings().hasKey(varName))
		{
			final AvailObject variableObject = module.variableBindings().mapAt(varName);
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
		if (module.constantBindings().hasKey(varName))
		{
			final AvailObject valueObject = module.constantBindings().mapAt(varName);
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
			@Override
			public String value()
			{
				return "variable " + token.string() + " to have been declared before use " + explanation;
			}
		});
		backupToken();
	}



	// private - parsing main

	private boolean parseHeader (
		final @NotNull ResolvedModuleName qualifiedName)
	{
		//  Parse the header of the module from the token stream.  See my class's
		//  'documentation - grammar' protocol for details.  Leave the token stream set
		//  at the start of the body.  Answer whether the header was parsed correctly.

		final Mutable<Integer> where = new Mutable<Integer>();
		final Mutable<Boolean> ok = new Mutable<Boolean>();
		extendedModules = new ArrayList<AvailObject>();
		usedModules = new ArrayList<AvailObject>();
		exportedNames = new ArrayList<AvailObject>();
		ok.value = false;
		parseTokenTypeStringErrorContinue(
			TokenType.KEYWORD,
			"Module",
			"initial Module keyword",
			new Continuation0 ()
			{
				@Override
				public void value()
				{
					final int savePosition = position();
					final AvailObject localName = parseStringLiteral();
					assert localName != null : "Expected string literal";
					final String nativeLocalName = localName.asNativeString();
					expected(
						"declared local module name to agree with "
						+ "fully-qualified module name");
					if (!qualifiedName.localName().equals(nativeLocalName))
					{
						position(savePosition);
						return;
					}
					module.name(
						ByteStringDescriptor.mutableObjectFromNativeString(
							qualifiedName.qualifiedName()));
					if (parseTokenIsTypeString(TokenType.KEYWORD, "Pragma"))
					{
						for (final AvailObject pragmaString : parseStrings())
						{
							String pragmaValue;
							String pragmaKey;
							String[] pragmaParts = pragmaString.asNativeString().split("=");
							assert pragmaParts.length == 2;
							pragmaKey = pragmaParts[0].trim();
							pragmaValue = pragmaParts[1].trim();
							if (!pragmaKey.matches("\\w+"))
							{
								expected("pragma key (" + pragmaKey + ") must not contain internal whitespace");
							}
							if (pragmaKey.equals("bootstrapDefiningMethod"))
							{
								interpreter.bootstrapDefiningMethod(pragmaValue);
							}
							if (pragmaKey.equals("bootstrapSpecialObject"))
							{
								interpreter.bootstrapSpecialObject(pragmaValue);
							}
						}
					}
					parseTokenTypeStringErrorContinue(
						TokenType.KEYWORD,
						"Extends",
						"Extends keyword",
						new Continuation0 ()
						{
							@Override
							public void value()
							{
								parseStringsThen(new Continuation1<List<AvailObject>> ()
								{
									@Override
									public void value(final List<AvailObject> extendsStrings)
									{
										extendedModules = extendsStrings;
										parseTokenTypeStringErrorContinue(
											TokenType.KEYWORD,
											"Uses",
											"Uses keyword",
											new Continuation0 ()
											{
												@Override
												public void value()
												{
													parseStringsThen(new Continuation1<List<AvailObject>> ()
													{
														@Override
														public void value(final List<AvailObject> usesStrings)
														{
															usedModules = usesStrings;
															parseTokenTypeStringErrorContinue(
																TokenType.KEYWORD,
																"Names",
																"Names keyword",
																new Continuation0 ()
																{
																	@Override
																	public void value()
																	{
																		parseStringsThen(new Continuation1<List<AvailObject>> ()
																		{
																			@Override
																			public void value(final List<AvailObject> namesStrings)
																			{
																				exportedNames = namesStrings;
																				parseTokenTypeStringErrorContinue(
																					TokenType.KEYWORD,
																					"Body",
																					"Body keyword",
																					new Continuation0 ()
																					{
																						@Override
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
		if (ok.value)
		{
			position(where.value);
		}
		return ok.value;
	}

	/**
	 * Parse the header of the module up to the <em>Names</em> token (inclusive)
	 * from the (truncated) {@linkplain TokenDescriptor token} stream. Populate
	 * {@link #extendedModules} and {@link #usedModules}.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ResolvedModuleName resolved name} of the
	 *        {@linkplain ModuleDescriptor source module}.
	 * @return {@code true} if the parse succeeded, {@code false} otherwise.
	 * @throws AvailCompilerException
	 *         If parsing fails.
	 */
	private boolean parseHeaderForDependenciesOnly (
			final @NotNull ResolvedModuleName qualifiedName)
		throws AvailCompilerException
	{
		final Mutable<Integer> where = new Mutable<Integer>();
		final Mutable<Boolean> ok = new Mutable<Boolean>();
		extendedModules = new ArrayList<AvailObject>();
		usedModules = new ArrayList<AvailObject>();
		exportedNames = new ArrayList<AvailObject>();
		ok.value = false;
		parseTokenTypeStringErrorContinue(
			TokenType.KEYWORD,
			"Module",
			"initial Module keyword",
			new Continuation0 ()
			{
				@Override
				public void value()
				{
					final int savePosition = position();
					final AvailObject localName = parseStringLiteral();
					assert localName != null : "Expected string literal";
					final String nativeLocalName = localName.asNativeString();
					expected(
						"declared local module name to agree with "
						+ "fully-qualified module name");
					if (!qualifiedName.localName().equals(nativeLocalName))
					{
						position(savePosition);
						return;
					}
					if (parseTokenIsTypeString(TokenType.KEYWORD, "Pragma"))
					{
						parseStrings();
					}
					parseTokenTypeStringErrorContinue(
						TokenType.KEYWORD,
						"Extends",
						"Extends keyword",
						new Continuation0 ()
						{
							@Override
							public void value()
							{
								parseStringsThen(new Continuation1<List<AvailObject>> ()
								{
									@Override
									public void value(final List<AvailObject> extendsStrings)
									{
										extendedModules = extendsStrings;
										parseTokenTypeStringErrorContinue(
											TokenType.KEYWORD,
											"Uses",
											"Uses keyword",
											new Continuation0 ()
											{
												@Override
												public void value()
												{
													parseStringsThen(new Continuation1<List<AvailObject>> ()
													{
														@Override
														public void value(final List<AvailObject> usesStrings)
														{
															usedModules = usesStrings;
															parseTokenTypeStringErrorContinue(
																TokenType.KEYWORD,
																"Names",
																"Names keyword",
																new Continuation0 ()
																{
																	@Override
																	public void value()
																	{
																		where.value = position();
																		ok.value = true;
																		expected("names for export next... (you should never see this message)");
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
		if (ok.value)
		{
			position(where.value);
		}
		return ok.value;
	}

	/**
	 * Parse the {@linkplain ModuleDescriptor module} with the specified
	 * fully-qualified {@linkplain ModuleName module name} from the {@linkplain
	 * TokenDescriptor token} stream.
	 *
	 * @param qualifiedName
	 *        The {@linkplain ResolvedModuleName resolved name} of the
	 *        {@linkplain ModuleDescriptor source module}.
	 * @throws AvailCompilerException
	 *         If compilation fails.
	 */
	private void parseModule (
			final @NotNull ResolvedModuleName qualifiedName)
		throws AvailCompilerException
	{
		final AvailRuntime runtime = interpreter.runtime();
		final ModuleNameResolver resolver = runtime.moduleNameResolver();
		final long sourceLength = qualifiedName.fileReference().length();
		final Mutable<AvailParseNode> interpretation =
			new Mutable<AvailParseNode>();
		final Mutable<Integer> endOfStatement = new Mutable<Integer>();
		interpreter.checkUnresolvedForwards();
		greatestGuess = 0;
		greatExpectations = new ArrayList<Generator<String>>();
		if (!parseHeader(qualifiedName))
		{
			reportError(qualifiedName);
			assert false;
		}
		greatestGuess = 0;
		greatExpectations = new ArrayList<Generator<String>>();
		if (!atEnd())
		{
			progressBlock.value(
				qualifiedName,
				(long) peekToken().start(),
				sourceLength);
		}
		for (final AvailObject modName : extendedModules)
		{
			assert modName.isString();
			ModuleName ref = resolver.canonicalNameFor(
				qualifiedName.asSibling(modName.asNativeString()));
			AvailObject availRef =
				ByteStringDescriptor.mutableObjectFromNativeString(
					ref.qualifiedName());
			if (!runtime.includesModuleNamed(availRef))
			{
				expected(
					"module \""
					+ ref.qualifiedName()
					+ "\" to be loaded already");
				reportError(qualifiedName);
				assert false;
			}
			AvailObject mod = runtime.moduleAt(availRef);
			AvailObject modNames = mod.names().keysAsSet();
			for (final AvailObject strName : modNames)
			{
				AvailObject trueNames = mod.names().mapAt(strName);
				for (final AvailObject trueName : trueNames)
				{
					module.atNameAdd(strName, trueName);
				}
			}
		}
		for (final AvailObject modName : usedModules)
		{
			assert modName.isString();
			ModuleName ref = resolver.canonicalNameFor(
				qualifiedName.asSibling(modName.asNativeString()));
			AvailObject availRef =
				ByteStringDescriptor.mutableObjectFromNativeString(
					ref.qualifiedName());
			if (!runtime.includesModuleNamed(availRef))
			{
				expected(
					"module \""
					+ ref.qualifiedName()
					+ "\" to be loaded already");
				reportError(qualifiedName);
				assert false;
			}
			AvailObject mod = runtime.moduleAt(availRef);
			AvailObject modNames = mod.names().keysAsSet();
			for (final AvailObject strName : modNames)
			{
				AvailObject trueNames = mod.names().mapAt(strName);
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
				CyclicTypeDescriptor.newCyclicTypeWithName(stringObject);
			module.atNameAdd(stringObject, trueNameObject);
			module.atNewNamePut(stringObject, trueNameObject);
		}
		module.buildFilteredBundleTreeFrom(
			interpreter.runtime().rootBundleTree());
		fragmentCache = new AvailCompilerFragmentCache();
		while (peekToken().tokenType() != TokenType.END_OF_FILE)
		{
			greatestGuess = 0;
			greatExpectations = new ArrayList<Generator<String>>();
			interpretation.value = null;
			endOfStatement.value = -1;
			parseStatementAsOutermostCanBeLabelThen(
				true,
				false,
				new Continuation1<AvailParseNode> ()
				{
					@Override
					public void value(final AvailParseNode stmt)
					{
						assert interpretation.value == null
							: "Statement parser was supposed to catch ambiguity";
						interpretation.value = stmt;
						endOfStatement.value = position();
					}
				});
			if (interpretation.value == null)
			{
				reportError(qualifiedName);
				assert false;
			}
			//  Clear the section of the fragment cache associated with the
			// (outermost) statement just parsed...
			privateClearFrags();
			//  Now execute the statement so defining words have a chance to
			//  run.  This lets the defined words be used in subsequent code.
			//  It's even callable in later statements and type expressions.
			position(endOfStatement.value);
			evaluateModuleStatement(interpretation.value);
			if (!atEnd())
			{
				backupToken();
				progressBlock.value(
					qualifiedName,
					(long) (nextToken().start() + 2),
					sourceLength);
			}
		}
		interpreter.checkUnresolvedForwards();
	}



	/**
	 * A statement was parsed correctly in two different ways (there may be more
	 * ways, but we stop after two as it's already an error).
	 * 
	 * @param interpretation1 The first interpretation as a
	 *                        {@link AvailParseNode}.
	 * @param interpretation2 The second interpretation as a
	 *                        {@link AvailParseNode}.
	 */
	private void ambiguousInterpretationsAnd (
			final AvailParseNode interpretation1,
			final AvailParseNode interpretation2)
	{
		expected(new Generator<String> ()
		{
			@Override
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

	/**
	 * Answer whether we're at the end of the token stream.
	 * 
	 * @return True if we've read to the end of the tokens.
	 */
	private boolean atEnd ()
	{
		return (position == tokens.size());
	}

	/**
	 * Step one token backwards.
	 */
	void backupToken ()
	{
		if (position == 0)
		{
			error("Can't backup any more");
			return;
		}
		position--;
	}

	/**
	 * Record an expectation at the current parse position.  The expectations
	 * captured at the rightmost parse position constitute the error message in
	 * case the parse fails.
	 * <p>
	 * The expectation is a {@link Generator Generator<String>}, in case
	 * constructing a {@link String} would be prohibitive.  There is also
	 * {@link #expected(String) another} version of this method that accepts a
	 * String directly.
	 * 
	 * @param stringGenerator The {@code Generator<String>} to capture.
	 */
	void expected (
			final Generator<String> stringGenerator)
	{
		// System.out.println(Integer.toString(position) + " expected " + stringBlock.value());
		if (position() == greatestGuess)
		{
			greatExpectations.add(stringGenerator);
		}
		if (position() > greatestGuess)
		{
			greatestGuess = position();
			greatExpectations = new ArrayList<Generator<String>>(2);
			greatExpectations.add(stringGenerator);
		}
	}

	/**
	 * Return and consume the next {@link TokenDescriptor token}.
	 * 
	 * @return The consumed token.
	 */
	AvailObject nextToken ()
	{
		assert !atEnd();
		// System.out.println(Integer.toString(position) + " next = " + tokens.get(position));
		position++;
		return tokens.get(position - 1);
	}

	/**
	 * If the next token has the specified type and content then consume it and
	 * return true, otherwise leave the position unchanged and return false.
	 *  
	 * @param tokenType The {@link TokenType type} of token to look for.
	 * @param string The exact token content to look for.
	 * @return Whether the specified token was found and consumed.
	 */
	boolean parseTokenIsTypeString (
			final TokenType tokenType,
			final String string)
	{
		final AvailObject token = peekToken();
		if (token.tokenType() == tokenType
				&& token.string().asNativeString().equals(string))
		{
			nextToken();
			return true;
		}
		return false;
	}

	/**
	 * Parse a token with the specified type and content, then invoke the
	 * continuation.  If the current token does not have the specified type and
	 * content then do nothing.
	 * 
	 * @param tokenType The {@link TokenType type} of token to look for.
	 * @param string The exact token content to look for.
	 * @param continuationNoArgs What to do if the token was found and consumed.
	 */
	void parseTokenTypeStringContinue (
			final TokenType tokenType,
			final String string,
			final Continuation0 continuationNoArgs)
	{
		final int where = position();
		final AvailCompilerScopeStack oldScope = scopeStack;
		final AvailObject token = peekToken();
		if (token.tokenType() == tokenType
				&& token.string().asNativeString().equals(string))
		{
			nextToken();
			continuationNoArgs.value();
			backupToken();
		}
		else
		{
			expected(new Generator<String> ()
			{
				@Override
				public String value()
				{
					return string.toString() + ", not " + token.string().toString();
				}
			});
		}
		assert (where == position());
		assert (oldScope == scopeStack);
	}

	/**
	 * Parse a token with a specific
	 * 
	 * @param tokenType
	 * @param string
	 * @param error
	 * @param continuationNoArgs
	 */
	void parseTokenTypeStringErrorContinue (
			final TokenType tokenType,
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

	private void parseTokenTypeStringErrorGeneratorContinue (
			final TokenType tokenType,
			final String string,
			final Generator<String> errorGenerator,
			final Continuation0 continuationNoArgs)
	{
		final int where = position();
		final AvailCompilerScopeStack oldScope = scopeStack;
		final AvailObject token = peekToken();
		if (token.tokenType() == tokenType
				&& token.string().asNativeString().equals(string))
		{
			nextToken();
			continuationNoArgs.value();
			backupToken();
		}
		else
		{
			expected(errorGenerator);
		}
		if (where != position() || oldScope != scopeStack)
		{
			error("token stream position and scopeStack were not preserved");
			return;
		}
	}

	AvailObject peekToken ()
	{
		assert !atEnd();
		// System.out.println(Integer.toString(position) + " peek = " + tokens.get(position));
		return tokens.get(position);
	}

	private boolean peekTokenIsTypeString (
			final TokenType tokenType,
			final String string)
	{
		final AvailObject token = peekToken();
		return ((token.tokenType() == tokenType)
				&& token.string().asNativeString().equals(string));
	}

	int position ()
	{
		return position;
	}

	void position (
			final int anInteger)
	{
		position = anInteger;
	}

	private void privateClearFrags ()
	{
		//  Clear the fragment cache.  Only the range minFrag to maxFrag could be notNil.

		fragmentCache.clear();
	}

	private void tryIfUnambiguousThen (
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
		final AvailCompilerScopeStack oldScope = scopeStack;
		count.value = 0;
		tryBlock.value(new Continuation1<AvailParseNode> ()
		{
			@Override
			public void value(final AvailParseNode aSolution)
			{
				if (count.value == 0)
				{
					solution.value = aSolution;
					where.value = position();
					whatScope.value = scopeStack;
				}
				else
				{
					if (aSolution == solution.value) error("Same solution was presented twice!");
					anotherSolution.value = aSolution;
				}
				++count.value;
			}
		});
		if (count.value > 1)
		{
			position(where.value);
			scopeStack = whatScope.value;
			ambiguousInterpretationsAnd(solution.value, anotherSolution.value);
			position(oldPosition);
			scopeStack = oldScope;
			return;
		}
		if (count.value == 0)
		{
			return;
		}
		assert (count.value == 1);
		//  We found exactly one solution.  Advance the token stream just past it, and redo
		//  any side-effects to the scopeStack, then invoke the continuation with the solution.
		//
		//  We need to reset the stream and stack after attempting this.
		position(where.value);
		scopeStack = whatScope.value;
		continuation.value(solution.value);
		scopeStack = oldScope;
		position(oldPosition);
	}

	private Generator<String> wrapInGenerator (
			final String aString)
	{
		//  Answer a block that will yield aString.  The Java version should create a Generator.

		return new Generator<String> ()
		{
			@Override
			public String value()
			{
				return aString;
			}
		};
	}



	// scope

	private AvailVariableDeclarationNode lookupDeclaration (
			final AvailObject name)
	{
		AvailCompilerScopeStack scope = scopeStack;
		while (scope.name() != null) {
			if (scope.name().equals(name))
			{
				return scope.declaration();
			}
			scope = scope.next();
		}
		return null;
	}

	void pushDeclaration (
			final AvailVariableDeclarationNode declaration)
	{
		scopeStack = new AvailCompilerScopeStack(
			declaration.name().string(),
			declaration,
			scopeStack);
	}



	// scope / cache

	private void clearScopeStack ()
	{
		scopeStack = new AvailCompilerScopeStack(
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

	/**
	 * Start definition of a {@linkplain ModuleDescriptor module}. The
	 * entire definition can be rolled back because the {@linkplain
	 * AvailInterpreter interpreter}'s context module will contain all methods
	 * and precedence rules defined between the transaction start and the
	 * rollback (or commit). Committing simply clears this information.
	 */
	private void startModuleTransaction ()
	{
		assert module == null;
		module = ModuleDescriptor.newModule();
		interpreter.setModule(module);
	}

	/**
	 * Rollback the {@linkplain ModuleDescriptor module} that was defined
	 * since the most recent {@link #startModuleTransaction()
	 * startModuleTransaction}.
	 */
	private void rollbackModuleTransaction ()
	{
		assert module != null;
		module.removeFrom(interpreter);
		module = null;
		interpreter.setModule(null);
	}

	/**
	 * Commit the {@linkplain ModuleDescriptor module} that was defined
	 * since the most recent {@link #startModuleTransaction()
	 * startModuleTransaction}. Simply clear the "{@linkplain #module module}"
	 * instance variable.
	 */
	private void commitModuleTransaction ()
	{
		assert module != null;
		interpreter.runtime().addModule(module);
		module.cleanUpAfterCompile();
		module = null;
		interpreter.setModule(null);
	}

	// Declare helper "expected(String)" to call "expected(Generator<String>)".
	void expected (final String aString)
	{
		expected(wrapInGenerator(aString));
	}
}
