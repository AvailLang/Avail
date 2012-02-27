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
import com.avail.compiler.AbstractAvailCompiler;
import com.avail.descriptor.*;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * I parse a source file to create a {@linkplain ModuleDescriptor module}.
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
	void parseInnerStatement (
		final ParserState start,
		final boolean canBeLabel,
		final Con<AvailObject> continuation)
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
					continuation.value(
						afterExpression.afterToken(),
						expression);
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
		final AvailObject actions = bundleTree.lazyActions();
		final boolean anyComplete = complete.mapSize() > 0;
		final boolean anyIncomplete = incomplete.mapSize() > 0;
		final boolean anyActions = actions.mapSize() > 0;
		assert anyComplete || anyIncomplete || anyActions
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
					eventuallyDo(new Continuation0()
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
					expectedKeywordsOf(start, incomplete);
				}
			}
			else
			{
				expectedKeywordsOf(start, incomplete);
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
	 * Execute one non-keyword parsing instruction, then run the continuation.
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
	 *            The {@linkplain MessageBundleTreeDescriptor bundle trees} at which
	 *            to continue parsing.
	 * @param continuation
	 *            What to do with a complete {@linkplain SendNodeDescriptor message
	 *            send}.
	 */
	void runParsingInstructionThen (
		final ParserState start,
		final int instruction,
		final AvailObject firstArgOrNull,
		final List<AvailObject> argsSoFar,
		final ParserState initialTokenPosition,
		final AvailObject successorTrees,
		final Con<AvailObject> continuation)
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
							final ParserState afterArgWithDeclaration;
							if (newArg.isInstanceOfKind(
								DECLARATION_NODE.mostGeneralType()))
							{
								if (lookupDeclaration(
										afterArg,
										newArg.token().string())
									!= null)
								{
									afterArg.expected(
										"macro argument that's a declaration"
										+ " not to shadow another declaration");
									return;
								}
								afterArgWithDeclaration =
									afterArg.withDeclaration(newArg);
							}
							else
							{
								afterArgWithDeclaration = afterArg;
							}
							eventuallyDo(
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
											continuation);
									}
								},
								"Continue send after argument",
								afterArgWithDeclaration.position);
						}
					});
				break;
			}
			case newList:
			{
				// Push an empty list node and continue.
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
				final List<AvailObject> newArgsSoFar = new ArrayList<AvailObject>(
					argsSoFar);
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
							final AvailObject syntheticToken =
								LiteralTokenDescriptor.mutable().create(
									newToken.string(),
									newToken.start(),
									newToken.lineNumber(),
									SYNTHETIC_LITERAL);
							syntheticToken.literal(newToken);
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
				// An argument has just been parsed. Record it in
				// a copy of innerArgsSoFar at position
				// (instruction-3)/8 and continue. Actually subtract
				// one from that to make it a List index.
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
							LiteralTokenDescriptor.mutable().create(
								StringDescriptor.from(count.toString()),
								initialTokenPosition.peekToken().start(),
								initialTokenPosition.peekToken().lineNumber(),
								LITERAL);
						token.literal(count);
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
							LiteralTokenDescriptor.mutable().create(
								StringDescriptor.from(nonempty.toString()),
								initialTokenPosition.peekToken().start(),
								initialTokenPosition.peekToken().lineNumber(),
								LITERAL);
						token.literal(nonempty);
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
		final AvailObject impSet,
		final Con<AvailObject> continuation)
	{
		final Mutable<Boolean> valid = new Mutable<Boolean>(true);
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
					MacroSubstitutionNodeDescriptor.mutable().create();
				substitution.macroName(bundle.message());
				substitution.outputParseNode(replacement);
				// Declarations introduced in the macro should now be moved
				// out of scope.
				attempt(
					new ParserState(
						stateAfterCall.position,
						stateBeforeCall.scopeMap),
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

	/**
	 * Report that the parser was expecting one of several keywords. The
	 * keywords are keys of the {@linkplain MapDescriptor map} argument
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
}
