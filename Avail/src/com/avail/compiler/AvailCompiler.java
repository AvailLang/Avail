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
import com.avail.compiler.scanning.TokenDescriptor;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
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
	 * @param source
	 *            The {@link String} containing the module's source.
	 * @param tokens
	 *            The {@link List} of {@linkplain TokenDescriptor tokens}
	 *            scanned from the module's source.
	 */
	public AvailCompiler (
		final L2Interpreter interpreter,
		final String source,
		final List<AvailObject> tokens)
	{
		super(interpreter, source, tokens);
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
					parseExpressionThen(
						start,
						new Con<AvailObject>("End of statement")
						{
							@Override
							public void value (
								final ParserState afterExpression,
								final AvailObject expression)
							{
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
				}
			},
			continuation);
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
	 *            raw tokens or keywords (or completion of a send).
	 * @param initialTokenPosition
	 *            The parse position where the send node started to be
	 *            processed. Does not count the position of the first argument
	 *            if the message started with an argument.
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
						innerArgsSoFar,
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
							final ParserState afterArgWithDeclaration =
								newArg.isInstanceOfKind(
										Types.DECLARATION_NODE.o())
									? afterArg.withDeclaration(newArg)
									: afterArg;
							attempt(
								new Continuation0()
								{
									@Override
									public void value ()
									{
										parseRestOfSendNode(
											afterArgWithDeclaration,
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
								afterArgWithDeclaration.position);
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
				final List<AvailObject> newArgsSoFar = new ArrayList<AvailObject>(
					argsSoFar);
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
			{
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
							newArgsSoFar.add(newToken);
							attempt(
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
											innerArgsSoFar,
											continuation);
									}
								},
								"Continue send after raw token for ellipsis",
								afterToken.position);
						}
					});
				break;
			}
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
	 * If this is a macro, invoke the body immediately with the argument
	 * expressions to produce a parse node.
	 * </p>
	 *
	 * @param stateBeforeCall
	 *            The initial parsing state, prior to parsing the entire
	 *            message.  TODO: deal correctly with leading argument.
	 * @param stateAfterCall
	 *            The parsing state after the message.
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
		final ParserState stateBeforeCall,
		final ParserState stateAfterCall,
		final List<AvailObject> argumentExpressions,
		final List<List<AvailObject>> innerArgumentExpressions,
		final AvailObject bundle, final Con<AvailObject> continuation)
	{
		final Mutable<Boolean> valid = new Mutable<Boolean>(true);
		final AvailObject message = bundle.message();
		final AvailObject impSet = interpreter.runtime().methodsAt(message);
		assert !impSet.equalsNull();
		final AvailObject implementationsTuple = impSet.implementationsTuple();
		assert implementationsTuple.tupleSize() > 0;

		if (implementationsTuple.tupleAt(1).isMacro())
		{
			// Macro definitions and non-macro definitions are not allowed to
			// mix within an implementation set.
			final List<AvailObject> argumentNodeTypes = new ArrayList<AvailObject>(
				argumentExpressions.size());
			for (final AvailObject argExpr : argumentExpressions)
			{
				argumentNodeTypes.add(argExpr.kind());
			}
			impSet.validateArgumentTypesInterpreterIfFail(
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
						stateAfterCall.expected(errorGenerator);
					}
				});
			if (!valid.value)
			{
				return;
			}

			// Construct code to invoke the method, since it might be a
			// primitive and we can't invoke that directly as the outermost
			// function.
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
			final AvailObject newFunction = FunctionDescriptor.create(
				writer.compiledCode(),
				TupleDescriptor.empty());
			newFunction.makeImmutable();
			try
			{
				final AvailObject replacement = interpreter.runFunctionArguments(
					newFunction,
					Collections.<AvailObject> emptyList());
				if (replacement.isInstanceOfKind(PARSE_NODE.o()))
				{
					final AvailObject substitution =
						MacroSubstitutionNodeDescriptor.mutable().create();
					substitution.macroName(message);
					substitution.outputParseNode(replacement);
					// Declarations introduced in the macro should now be moved
					// out of scope.
					attempt(
						new ParserState(
							stateAfterCall.position,
							stateBeforeCall.scopeStack),
						continuation,
						replacement);
				}
				else
				{
					stateAfterCall.expected(
						"macro body ("
						+ impSet.name().name()
						+ ") to produce a parse node");
				}
			}
			catch (final AvailRejectedParseException e)
			{
				stateAfterCall.expected(e.rejectionString().asNativeString());
			}
			return;
		}
		// It invokes a method (not a macro).
		for (final AvailObject arg : argumentExpressions)
		{
			if (arg.expressionType().equals(BottomTypeDescriptor.bottom()))
			{
				stateAfterCall.expected(
					"argument to have type other than bottom");
				return;
			}
			if (arg.expressionType().equals(TOP.o()))
			{
				stateAfterCall.expected(
					"argument to have type other than top");
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
						stateAfterCall.expected(errorGenerator);
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
						stateAfterCall.expected(errorGenerator);
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
				stateAfterCall.expected(errorMessage);
			}
		}
		if (valid.value)
		{
			final AvailObject sendNode = SendNodeDescriptor.mutable().create();
			sendNode.implementationSet(impSet);
			sendNode.arguments(TupleDescriptor.fromList(argumentExpressions));
			sendNode.returnType(returnType);
			attempt(
				new ParserState(
					stateAfterCall.position,
					stateBeforeCall.scopeStack),
				continuation,
				sendNode);
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
			final List<AvailObject> argumentOccurrences = innerArguments.get(i - 1);
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
				for (final MapDescriptor.Entry entry : incomplete.mapIterable())
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

		parseLeadingArgumentSendAfterThen(
			start,
			node,
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
					continuation);
			}
		}
		else
		{
			// We're parsing a message send with a leading argument. There
			// should have been no way to parse any keywords or other arguments
			// yet, so make sure the position hasn't budged since we started.
			// Then use the provided first argument.
			assert start.position == initialTokenPosition.position;
			attempt(
				start,
				continuation,
				firstArgOrNull);
		}
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

	/**
	 * Parse an occurrence of a raw keyword or operator token, then invoke the
	 * continuation with it.
	 *
	 * @param start
	 *            Where to start parsing.
	 * @param continuation
	 *            What to do after parsing the raw token.  This continuation
	 *            should expect a token, not a parse node.
	 */
	private void parseRawTokenThen (
		final ParserState start,
		final Con<AvailObject> continuation)
	{
		final AvailObject token = start.peekToken();
		if (token.tokenType() == KEYWORD || token.tokenType() == OPERATOR)
		{
			attempt(start.afterToken(), continuation, token);
		}
		else
		{
			start.expected("raw token");
		}
	}


}
