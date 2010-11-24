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
import com.avail.compiler.scanner.AvailLiteralToken;
import com.avail.compiler.scanner.AvailScanner;
import com.avail.compiler.scanner.AvailToken;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ByteStringDescriptor;
import com.avail.descriptor.ClosureDescriptor;
import com.avail.descriptor.ContainerDescriptor;
import com.avail.descriptor.CyclicTypeDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.AvailInterpreter;
import com.avail.interpreter.levelTwo.L2Interpreter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.avail.descriptor.AvailObject.*;

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

	private List<AvailToken> tokens;
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
		ArrayList<AvailParseNode> statements;
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
		ArrayList<AvailObject> args;
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
		final AvailObject name = ByteStringDescriptor.mutableObjectFromNativeString(declarationExpression.name().string()).makeImmutable();
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
		assert peekToken().type() == AvailToken.TokenType.end
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
		assert peekToken().type() == AvailToken.TokenType.end
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
			AvailToken.TokenType.operator,
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
			AvailToken.TokenType.operator,
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

		if ((peekToken().type() == AvailToken.TokenType.keyword))
		{
			parseVariableUseWithExplanationThen("for an assignment", new Continuation1<AvailVariableUseNode> ()
			{
				@Override
				public void value(final AvailVariableUseNode varUse)
				{
					parseTokenTypeStringErrorContinue(
						AvailToken.TokenType.operator,
						":",
						":= for assignment",
						new Continuation0 ()
						{
							@Override
							public void value()
							{
								parseTokenTypeStringErrorContinue(
									AvailToken.TokenType.operator,
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

		if (peekTokenIsTypeString(AvailToken.TokenType.operator, "["))
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
											AvailToken.TokenType.operator,
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
														AvailToken.TokenType.operator,
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
					@Override
					public void value()
					{
						parseTokenTypeStringErrorContinue(
							AvailToken.TokenType.operator,
							":",
							"second colon for constant declaration (a ::= expr)",
							new Continuation0 ()
							{
								@Override
								public void value()
								{
									parseTokenTypeStringErrorContinue(
										AvailToken.TokenType.operator,
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
										AvailToken.TokenType.operator,
										":",
										new Continuation0 ()
										{
											@Override
											public void value()
											{
												parseTokenTypeStringContinue(
													AvailToken.TokenType.operator,
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

		if (peekTokenIsTypeString(AvailToken.TokenType.operator, ","))
		{
			parseTokenTypeStringErrorContinue(
				AvailToken.TokenType.operator,
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

	void parseExpressionThen (
			final Continuation1<AvailParseNode> originalContinuation)
	{
		//  Parse an expression.  Backtracking will find all valid interpretations.  Note that
		//  a list expression requires at least two terms to form a list node.  This method is a
		//  key optimization point, so the fragmentCache is used to keep track of parsing
		//  solutions at this point, simply replaying them on subsequent parses, as long as
		//  the variable declarations up to that point were identical.

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
					ArrayList<AvailParseNode> itemList;
					itemList = new ArrayList<AvailParseNode>(1);
					itemList.add(firstItem);
					parseExpressionListItemsBeyondThen(itemList, justRecord);
				}
			});
			if (!((position() == start) && (scopeStack == originalScope)))
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
		final List<AvailCompilerCachedSolution> solutions = fragmentCache.atTokenPositionScopeStack(start, originalScope);
		for (final AvailCompilerCachedSolution solution : solutions)
		{
			position(solution.endPosition());
			scopeStack = solution.scopeStack();
			originalContinuation.value(solution.parseNode());
		}
		scopeStack = originalScope;
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
		if (completeMapSize != 0)
		{
			AvailObject.lock(complete);
			for (int mapIndex = 1, _end1 = complete.capacity(); mapIndex <= _end1; mapIndex++)
			{
				final AvailObject message = complete.keyAtIndex(mapIndex);
				if (!message.equalsVoidOrBlank())
				{
					if (interpreter.runtime().hasMethodsAt(message))
					{
						final Mutable<Boolean> valid = new Mutable<Boolean>();
						final AvailObject impSet =
							interpreter.runtime().methodsAt(message);
						final AvailObject bundle = complete.valueAtIndex(mapIndex);
						valid.value = true;
						List<AvailObject> typesSoFar;
						typesSoFar = new ArrayList<AvailObject>(argsSoFar.size());
						for (int argIndex = 1, _end2 = argsSoFar.size(); argIndex <= _end2; argIndex++)
						{
							typesSoFar.add(argsSoFar.get(argIndex - 1).type());
						}
						final AvailObject returnType = interpreter.validateTypesOfMessageSendArgumentTypesIfFail(
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
			}
			AvailObject.unlock(complete);
		}
		if (incompleteMapSize != 0)
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
						@Override
						public String value()
						{
							StringBuilder builder = new StringBuilder(200);
							builder.append("one of the following internal keywords: ");
							if (incompleteMapSize > 10)
							{
								builder.append("\n\t\t");
							}
							AvailObject.lock(incomplete);
							final AvailObject incompleteTuple = incomplete.keysAsSet();
							for (final AvailObject string : incompleteTuple)
							{
								for (final AvailObject ch : string)
								{
									builder.appendCodePoint(ch.codePoint());
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
									final AvailObject restrictions = outerSend.bundle().restrictions().tupleAt((argsSoFar.size() + 1));
									if (restrictions.hasElement(((AvailSendNode)(newArg)).message()))
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
				@Override
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

	void parseLeadingArgumentSendAfterThen (
			final AvailParseNode leadingArgument,
			final Continuation1<AvailSendNode> continuation)
	{
		//  Parse a send node whose leading argument has already been parsed.

		final AvailObject complete = interpreter.completeBundlesStartingWith(TupleDescriptor.underscoreTuple());
		final AvailObject incomplete = interpreter.incompleteBundlesStartingWith(TupleDescriptor.underscoreTuple());
		if (complete.mapSize() != 0 || incomplete.mapSize() != 0)
		{
			ArrayList<AvailParseNode> argsSoFar;
			argsSoFar = new ArrayList<AvailParseNode>(2);
			argsSoFar.add(leadingArgument);
			Continuation1<AvailSendNode> innerContinuation;
			if (leadingArgument.isSend())
			{
				innerContinuation = new Continuation1<AvailSendNode> ()
				{
					@Override
					public void value(final AvailSendNode outerSend)
					{
						if (outerSend.bundle().restrictions().tupleAt(1).hasElement(((AvailSendNode)(leadingArgument)).message()))
						{
							expected(new Generator<String> ()
							{
								@Override
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

	private void parseLeadingKeywordSendThen (
			final Continuation1<AvailSendNode> continuation)
	{
		//  Parse a send node.  To prevent infinite left-recursion and false ambiguity, we only
		//  allow a send with a leading keyword to be parsed from here.

		final AvailToken peekToken = peekToken();
		if (((peekToken.type() == AvailToken.TokenType.keyword) || (peekToken.type() == AvailToken.TokenType.operator)))
		{
			final AvailObject start = ByteStringDescriptor.mutableObjectFromNativeString(nextToken().string());
			final AvailObject complete = interpreter.completeBundlesStartingWith(start);
			final AvailObject incomplete = interpreter.incompleteBundlesStartingWith(start);
			ArrayList<AvailParseNode> argsSoFar;
			argsSoFar = new ArrayList<AvailParseNode>(3);
			if (complete.mapSize() != 0 || incomplete.mapSize() != 0)
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
			AvailToken.TokenType.keyword,
			"Primitive",
			"primitive declaration",
			new Continuation0 ()
			{
				@Override
				public void value()
				{
					final AvailToken token = peekToken();
					if (((token.type() == AvailToken.TokenType.literal) && ((AvailLiteralToken)(token)).literal().isInstanceOfSubtypeOf(IntegerRangeTypeDescriptor.integers())))
					{
						nextToken();
						num.value = ((short)(((AvailLiteralToken)(token)).literal().extractInt()));
						if (interpreter.supportsPrimitive(num.value))
						{
							if ((interpreter.argCountForPrimitive(num.value) == argCount))
							{
								parseTokenTypeStringContinue(
									AvailToken.TokenType.endOfStatement,
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
		if (peekTokenIsTypeString(AvailToken.TokenType.operator, ":"))
		{
			nextToken();
			parseTokenTypeStringErrorGeneratorContinue(
				AvailToken.TokenType.operator,
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

	private void parseSendArgumentWithExplanationThen (
			final String explanation,
			final Continuation1<AvailParseNode> continuation)
	{
		//  Parse an argument to a message send.  Backtracking will find all valid interpretations.

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

	private void parseSimpleThen (
			final Continuation1<? super AvailParseNode> continuation)
	{
		//  Look for a variable, reference, or literal.

		final int originalPosition = position;
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
								AvailToken.TokenType.endOfStatement,
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
							AvailToken.TokenType.endOfStatement,
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
							AvailToken.TokenType.endOfStatement,
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
							AvailToken.TokenType.endOfStatement,
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

	private void parseVariableUseWithExplanationThen (
			final String exp,
			final Continuation1<? super AvailVariableUseNode> continuation)
	{
		//  Parse the use of a variable.

		if (peekToken().type() != AvailToken.TokenType.keyword)
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
				return "variable \"" + token.string() + "\" to have been declared before use " + exp;
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
			AvailToken.TokenType.keyword,
			"Module",
			"initial Module keyword",
			new Continuation0 ()
			{
				@Override
				public void value()
				{
					final int savePosition = position();
					final AvailObject localName = parseStringLiteral();
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
					if (parseTokenIsTypeString(AvailToken.TokenType.keyword, "Pragma"))
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
						AvailToken.TokenType.keyword,
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
											AvailToken.TokenType.keyword,
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
																AvailToken.TokenType.keyword,
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
																					AvailToken.TokenType.keyword,
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
	 * from the (truncated) {@linkplain AvailToken token} stream. Populate
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
			AvailToken.TokenType.keyword,
			"Module",
			"initial Module keyword",
			new Continuation0 ()
			{
				@Override
				public void value()
				{
					final int savePosition = position();
					final AvailObject localName = parseStringLiteral();
					final String nativeLocalName = localName.asNativeString();
					expected(
						"declared local module name to agree with "
						+ "fully-qualified module name");
					if (!qualifiedName.localName().equals(nativeLocalName))
					{
						position(savePosition);
						return;
					}
					if (parseTokenIsTypeString(AvailToken.TokenType.keyword, "Pragma"))
					{
						parseStrings();
					}
					parseTokenTypeStringErrorContinue(
						AvailToken.TokenType.keyword,
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
											AvailToken.TokenType.keyword,
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
																AvailToken.TokenType.keyword,
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
	 * AvailToken token} stream.
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
				peekToken().start(),
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
		while (peekToken().type() != AvailToken.TokenType.end)
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
					(nextToken().start() + 2),
					sourceLength);
			}
		}
		interpreter.checkUnresolvedForwards();
	}



	// private - parsing utilities

	private void ambiguousInterpretationsAnd (
			final AvailParseNode interpretation1,
			final AvailParseNode interpretation2)
	{
		//  A statement was parsed correctly in two different ways (there may be more ways,
		//  but we stop after two as it's already an error).

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

	private boolean atEnd ()
	{
		return (position == tokens.size());
	}

	void backupToken ()
	{
		if (position == 0)
		{
			error("Can't backup any more");
			return;
		}
		position--;
	}

	void expected (
			final Generator<String> stringBlock)
	{
		// System.out.println(Integer.toString(position) + " expected " + stringBlock.value());
		if ((position() == greatestGuess))
		{
			greatExpectations.add(stringBlock);
		}
		if ((position() > greatestGuess))
		{
			greatestGuess = position();
			greatExpectations = new ArrayList<Generator<String>>(2);
			greatExpectations.add(stringBlock);
		}
	}

	AvailToken nextToken ()
	{
		assert !atEnd();
		// System.out.println(Integer.toString(position) + " next = " + tokens.get(position));
		position++;
		return tokens.get(position - 1);
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
		final AvailCompilerScopeStack oldScope = scopeStack;
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

	private void parseTokenTypeStringErrorGeneratorContinue (
			final AvailToken.TokenType tokenType,
			final String string,
			final Generator<String> errorGenerator,
			final Continuation0 continuationNoArgs)
	{
		final int where = position();
		final AvailCompilerScopeStack oldScope = scopeStack;
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
		if (where != position() || oldScope != scopeStack)
		{
			error("token stream position and scopeStack were not preserved");
			return;
		}
	}

	AvailToken peekToken ()
	{
		assert !atEnd();
		// System.out.println(Integer.toString(position) + " peek = " + tokens.get(position));
		return tokens.get(position);
	}

	private boolean peekTokenIsTypeString (
			final AvailToken.TokenType tokenType,
			final String string)
	{
		final AvailToken token = peekToken();
		return ((token.type() == tokenType) && token.string().equals(string));
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
			final String name)
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

	// TODO: [TLS] Implement module hierarchies!
}
