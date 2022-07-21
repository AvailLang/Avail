/*
 * ParsingOperation.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.compiler

import avail.compiler.ParsingConversionRule.Companion.ruleNumber
import avail.compiler.ParsingOperation.PARSE_PART
import avail.compiler.ParsingOperation.PARSE_PART_CASE_INSENSITIVELY
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK
import avail.compiler.splitter.MessageSplitter
import avail.compiler.splitter.MessageSplitter.Companion.constantForIndex
import avail.compiler.splitter.MessageSplitter.Companion.permutationAtIndex
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.bundles.A_BundleTree
import avail.descriptor.bundles.A_BundleTree.Companion.allParsingPlansInProgress
import avail.descriptor.bundles.A_BundleTree.Companion.expand
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.maps.A_Map.Companion.keysAsSet
import avail.descriptor.maps.A_Map.Companion.mapIterable
import avail.descriptor.maps.A_Map.Companion.mapSize
import avail.descriptor.maps.MapDescriptor
import avail.descriptor.phrases.A_Phrase.Companion.copyConcatenating
import avail.descriptor.phrases.A_Phrase.Companion.copyWith
import avail.descriptor.phrases.A_Phrase.Companion.declaration
import avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.macroOriginalSendNode
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.stripMacro
import avail.descriptor.phrases.ListPhraseDescriptor
import avail.descriptor.phrases.ListPhraseDescriptor.Companion.emptyListNode
import avail.descriptor.phrases.ListPhraseDescriptor.Companion.newListNode
import avail.descriptor.phrases.LiteralPhraseDescriptor
import avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.literalNodeFromToken
import avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.Companion.newMacroSubstitution
import avail.descriptor.phrases.PermutedListPhraseDescriptor.Companion.newPermutedListNode
import avail.descriptor.phrases.PhraseDescriptor
import avail.descriptor.phrases.ReferencePhraseDescriptor
import avail.descriptor.phrases.ReferencePhraseDescriptor.Companion.referenceNodeFromUse
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import avail.descriptor.tokens.TokenDescriptor
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tokens.TokenDescriptor.TokenType.COMMENT
import avail.descriptor.tokens.TokenDescriptor.TokenType.END_OF_FILE
import avail.descriptor.tokens.TokenDescriptor.TokenType.KEYWORD
import avail.descriptor.tokens.TokenDescriptor.TokenType.LITERAL
import avail.descriptor.tokens.TokenDescriptor.TokenType.WHITESPACE
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.toList
import avail.descriptor.types.A_Type
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.VARIABLE_USE_PHRASE
import avail.descriptor.variables.VariableDescriptor
import avail.performance.Statistic
import avail.performance.StatisticReport.EXPANDING_PARSING_INSTRUCTIONS
import avail.performance.StatisticReport.RUNNING_PARSING_INSTRUCTIONS
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.PrefixSharingList.Companion.withoutLast
import avail.utility.evaluation.Describer
import avail.utility.trace
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `ParsingOperation` describes the operations available for parsing Avail
 * message names.
 *
 * @property modulus
 *   The modulus that represents the operation uniquely for its arity.
 * @property commutesWithParsePart
 *   Whether this instance commutes with [PARSE_PART] instructions.
 * @property canRunIfHasFirstArgument
 *   Whether this operation can run successfully if there is a pre-parsed first
 *   argument that has not yet been consumed.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `ParsingOperation` for this enum.
 *
 * @param modulus
 *   The modulus that represents the operation uniquely for its arity.
 * @param commutesWithParsePart
 *   Whether a [PARSE_PART] or [PARSE_PART_CASE_INSENSITIVELY] instructions can
 *   be moved safely leftward over this instruction.
 * @param canRunIfHasFirstArgument
 *   Whether this instruction can be run if the first argument has been parsed
 *   but not yet consumed by a PARSE_ARGUMENT instruction.
 */
enum class ParsingOperation constructor(
	private val modulus: Int,
	val commutesWithParsePart: Boolean,
	val canRunIfHasFirstArgument: Boolean)
{
	/*
	 * Arity zero entries:
	 */

	/**
	 * `0` - Push a new [list][ListPhraseDescriptor] that contains an
	 * [empty&#32;tuple][TupleDescriptor.emptyTuple] of
	 * [phrases][PhraseDescriptor] onto the parse stack.
	 */
	EMPTY_LIST(0, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			stepState.push(emptyListNode())
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}
	},

	/**
	 * `1` - Pop an argument from the parse stack of the current potential
	 * message send. Pop a [list][ListPhraseDescriptor] from the parse stack.
	 * Append the argument to the list. Push the resultant list onto the parse
	 * stack.
	 */
	APPEND_ARGUMENT(1, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val value = stepState.pop()
			val list = stepState.pop()
			stepState.push(list.copyWith(value))
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}
	},

	/**
	 * `2` - Push the current parse position onto the mark stack.
	 */
	SAVE_PARSE_POSITION(2, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val marker = when (stepState.firstArgOrNull)
			{
				null -> stepState.start.position
				else -> stepState.initialTokenPosition.position
			}
			stepState.pushMarker(marker)
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}
	},

	/**
	 * `3` - Pop the top marker off the mark stack.
	 */
	DISCARD_SAVED_PARSE_POSITION(3, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			stepState.popMarker()
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}
	},

	/**
	 * `4` - Pop the top marker off the mark stack and compare it to the current
	 * parse position.  If they're the same, abort the current parse, otherwise
	 * push the current parse position onto the mark stack in place of the old
	 * marker and continue parsing.
	 */
	ENSURE_PARSE_PROGRESS(4, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val oldMarker = stepState.popMarker()
			val newMarker = stepState.start.position
			if (oldMarker != newMarker)
			{
				// Progress has been made.  Continue on this path.
				stepState.pushMarker(newMarker)
				compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
			}
		}
	},

	/**
	 * `5` - Parse an ordinary argument of a message send, pushing the
	 * expression onto the parse stack.
	 */
	PARSE_ARGUMENT(5, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val partialSubexpressionList = when (stepState.firstArgOrNull)
			{
				null -> stepState.superexpressions!!.advancedTo(successorTree)
				else -> stepState.superexpressions
			}
			compiler.parseSendArgumentWithExplanationThen(
				stepState.start,
				"argument",
				stepState.firstArgOrNull,
				stepState.firstArgOrNull === null
					&& stepState.initialTokenPosition.lexingState
						!= stepState.start.lexingState,
				false,
				partialSubexpressionList
			) { afterArg, phrase ->
				val stepStateCopy = stepState.copy {
					start = afterArg
					// We're about to accept an argument, so whatever was in
					// consumedAnything should be moved into
					// consumedAnythingBeforeLatestArgument.
					consumedAnythingBeforeLatestArgument = consumedAnything
					// The argument counts as something that was consumed if
					// it's not a leading argument...
					consumedAnything = firstArgOrNull === null
					firstArgOrNull = null
					push(phrase)
				}
				compiler.eventuallyParseRestOfSendNode(
					successorTree, stepStateCopy)
			}
		}
	},

	/**
	 * `6` - Parse an expression, even one whose expressionType is ⊤, then push
	 * *a literal phrase wrapping this expression* onto the parse stack.
	 *
	 * If we didn't wrap the phrase inside a literal phrase, we wouldn't be able
	 * to process sequences of statements in macros, since they would each have
	 * an expressionType of ⊤ (or if one was ⊥, the entire expressionType would
	 * also be ⊥).  Instead, they will have the expressionType phrase⇒⊤ (or
	 * phrase⇒⊥), which is perfectly fine to put inside a list phrase during
	 * parsing.
	 */
	PARSE_TOP_VALUED_ARGUMENT(6, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val partialSubexpressionList = when (stepState.firstArgOrNull)
			{
				null -> stepState.superexpressions!!.advancedTo(successorTree)
				else -> stepState.superexpressions
			}
			compiler.parseSendArgumentWithExplanationThen(
				stepState.start,
				"top-valued argument",
				stepState.firstArgOrNull,
				stepState.firstArgOrNull === null
					&& stepState.initialTokenPosition.lexingState
						!= stepState.start.lexingState,
				true,
				partialSubexpressionList
			) { afterArg, phrase ->
				val stepStateCopy = stepState.copy {
					start = afterArg
					// We're about to accept an argument, so whatever was in
					// consumedAnything should be moved into
					// consumedAnythingBeforeLatestArgument.
					consumedAnythingBeforeLatestArgument = consumedAnything
					// The argument counts as something that was consumed if
					// it's not a leading argument...
					consumedAnything = firstArgOrNull === null
					firstArgOrNull = null
					push(phrase)
				}
				compiler.eventuallyParseRestOfSendNode(
					successorTree, stepStateCopy)
			}
		}
	},

	/**
	 * `7` - Parse a [raw&#32;token][TokenDescriptor]. It should correspond to a
	 * [variable][VariableDescriptor] that is in scope. Push a
	 * [variable&#32;reference][ReferencePhraseDescriptor] onto the parse stack.
	 */
	PARSE_VARIABLE_REFERENCE(7, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val partialSubexpressionList = when (stepState.firstArgOrNull)
			{
				null -> stepState.superexpressions!!.advancedTo(successorTree)
				else -> stepState.superexpressions
			}
			compiler.parseSendArgumentWithExplanationThen(
				stepState.start,
				"variable reference",
				stepState.firstArgOrNull,
				stepState.firstArgOrNull === null
					&& stepState.initialTokenPosition.lexingState
						!= stepState.start.lexingState,
				false,
				partialSubexpressionList
			) { afterUse, variableUse ->
				val rawVariableUse = variableUse.stripMacro
				if (!rawVariableUse.phraseKindIsUnder(VARIABLE_USE_PHRASE))
				{
					if (stepState.consumedAnything)
					{
						// At least one token besides the variable use has
						// been encountered, so go ahead and report that we
						// expected a variable.
						afterUse.expected(
							if (stepState.consumedStaticTokens.isEmpty()) WEAK
							else STRONG,
							describeWhyVariableUseIsExpected(successorTree))
					}
					// It wasn't a variable use phrase, so give up.
					return@parseSendArgumentWithExplanationThen
				}
				// Make sure taking a reference is appropriate.
				val declarationKind =
					rawVariableUse.declaration.declarationKind()
				if (!declarationKind.isVariable)
				{
					if (stepState.consumedAnything)
					{
						// Only complain about this not being a variable if
						// we've parsed something besides the variable
						// reference argument.
						afterUse.expected(
							WEAK,
							"variable for reference argument to be "
							+ "assignable, not "
							+ declarationKind.nativeKindName())
					}
					return@parseSendArgumentWithExplanationThen
				}
				// Create a variable reference from this use.
				val rawVariableReference = referenceNodeFromUse(rawVariableUse)
				val variableReference =
					if (variableUse.isMacroSubstitutionNode)
						newMacroSubstitution(
							variableUse.macroOriginalSendNode,
							rawVariableReference)
					else rawVariableReference
				val stepStateCopy = stepState.copy {
					start = afterUse
					// We're about to accept an argument, so whatever was in
					// consumedAnything should be moved into
					// consumedAnythingBeforeLatestArgument.
					consumedAnythingBeforeLatestArgument = consumedAnything
					// The argument counts as something that was consumed if
					// it's not a leading argument...
					consumedAnything = firstArgOrNull === null
					firstArgOrNull = null
					// Push the new argument phrase.
					push(variableReference)
				}
				compiler.eventuallyParseRestOfSendNode(
					successorTree, stepStateCopy)
			}
		}
	},

	/**
	 * `8` - Parse an argument of a message send, using the *outermost (module)
	 * scope*.  Leave it on the parse stack.
	 */
	PARSE_ARGUMENT_IN_MODULE_SCOPE(8, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			compiler.parseArgumentInModuleScopeThen(stepState, successorTree)
		}
	},

	/**
	 * `9` - Parse *any* [raw&#32;token][TokenDescriptor], leaving it on the
	 * parse stack.  In particular, push a literal phrase whose token is a
	 * synthetic literal token whose value is the actual token that was parsed.
	 */
	PARSE_ANY_RAW_TOKEN(9, false, false)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			if (stepState.firstArgOrNull !== null)
			{
				// Starting with a parseRawToken can't cause unbounded
				// left-recursion, so treat it more like reading an expected
				// token than like parseArgument.  Thus, if a firstArgument has
				// been provided (i.e., we're attempting to parse a
				// leading-argument message to wrap a leading expression), then
				// reject the parse.
				return
			}
			compiler.nextNonwhitespaceTokensDo(stepState.start) { token ->
				val tokenType = token.tokenType()
				assert(tokenType != WHITESPACE && tokenType != COMMENT)
				if (tokenType == END_OF_FILE)
				{
					stepState.start.expected(
						if (stepState.consumedStaticTokens.isEmpty()) WEAK
						else STRONG,
						"any token, not end-of-file")
					return@nextNonwhitespaceTokensDo
				}
				val syntheticToken = literalToken(
					token.string(),
					token.start(),
					token.lineNumber(),
					token)
				syntheticToken.setCurrentModule(
					compiler.compilationContext.module)
				val stepStateCopy = stepState.copy {
					start = ParserState(
						token.nextLexingState(), stepState.start.clientDataMap)
					// Until we've passed the type test, we don't consider
					// tokens read past it in the stream to have been truly
					// encountered.
					consumedAnythingBeforeLatestArgument = consumedAnything
					consumedAnything = true
					firstArgOrNull = null
					// Push the new argument phrase.
					push(literalNodeFromToken(syntheticToken))
				}
				compiler.eventuallyParseRestOfSendNode(
					successorTree, stepStateCopy)
			}
		}
	},

	/**
	 * `10` - Parse a raw *[keyword][TokenType.KEYWORD]*
	 * [token][TokenDescriptor], leaving it on the parse stack.
	 */
	PARSE_RAW_KEYWORD_TOKEN(10, false, false)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			if (stepState.firstArgOrNull !== null)
			{
				// Starting with a parseRawToken can't cause unbounded
				// left-recursion, so treat it more like reading an expected
				// token than like parseArgument.  Thus, if a firstArgument has
				// been provided (i.e., we're attempting to parse a
				// leading-argument message to wrap a leading expression), then
				// reject the parse.
				return
			}
			compiler.nextNonwhitespaceTokensDo(stepState.start) { token ->
				val tokenType = token.tokenType()
				if (tokenType != KEYWORD)
				{
					if (stepState.consumedAnything)
					{
						stepState.start.expected(
							if (stepState.consumedStaticTokens.isEmpty()) WEAK
							else STRONG
						) {
							it(
								"a keyword token, not " +
									when (tokenType)
									{
										END_OF_FILE -> "end-of-file"
										LITERAL -> token.literal()
										else -> token.string()
									})
						}
					}
					return@nextNonwhitespaceTokensDo
				}
				val syntheticToken = literalToken(
					token.string(),
					token.start(),
					token.lineNumber(),
					token)
				syntheticToken.setCurrentModule(
					compiler.compilationContext.module)
				val stepStateCopy = stepState.copy {
					start = ParserState(
						token.nextLexingState(), stepState.start.clientDataMap)
					// Until we've passed the type test, we don't consider
					// tokens read past it in the stream to have been truly
					// encountered.
					consumedAnythingBeforeLatestArgument = consumedAnything
					consumedAnything = true
					firstArgOrNull = null
					// Push the new argument phrase.
					push(literalNodeFromToken(syntheticToken))
				}
				compiler.eventuallyParseRestOfSendNode(
					successorTree, stepStateCopy)
			}
		}
	},

	/**
	 * `11` - Parse a raw *[literal][TokenType.LITERAL]*
	 * [token][TokenDescriptor], leaving it on the parse stack.
	 */
	PARSE_RAW_LITERAL_TOKEN(11, false, false)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			if (stepState.firstArgOrNull !== null)
			{
				// Starting with a parseRawToken can't cause unbounded
				// left-recursion, so treat it more like reading an expected
				// token than like parseArgument.  Thus, if a firstArgument has
				// been provided (i.e., we're attempting to parse a
				// leading-argument message to wrap a leading expression), then
				// reject the parse.
				return
			}
			compiler.nextNonwhitespaceTokensDo(stepState.start) { token ->
				val tokenType = token.tokenType()
				if (tokenType != LITERAL)
				{
					if (stepState.consumedAnything)
					{
						stepState.start.expected(
							if (stepState.consumedStaticTokens.isEmpty()) WEAK
							else STRONG
						) {
							it(
								"a literal token, not " +
									when (tokenType)
									{
										END_OF_FILE -> "end-of-file"
										else -> token.string()
									})
						}
					}
					return@nextNonwhitespaceTokensDo
				}
				val syntheticToken = literalToken(
					token.string(),
					token.start(),
					token.lineNumber(),
					token)
				syntheticToken.setCurrentModule(
					compiler.compilationContext.module)
				val stepStateCopy = stepState.copy {
					start = ParserState(
						token.nextLexingState(), stepState.start.clientDataMap)
					// Until we've passed the type test, we don't consider
					// tokens read past it in the stream to have been truly
					// encountered.
					consumedAnythingBeforeLatestArgument = consumedAnything
					consumedAnything = true
					firstArgOrNull = null
					// Push the new argument phrase.
					push(literalNodeFromToken(syntheticToken))
				}
				compiler.eventuallyParseRestOfSendNode(
					successorTree, stepStateCopy)
			}
		}
	},

	/**
	 * `12` - Concatenate the two lists that have been pushed previously.
	 */
	CONCATENATE(12, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val right = stepState.pop()
			val left = stepState.pop()
			when (left.expressionsSize)
			{
				0 -> stepState.push(right)
				else -> stepState.push(left.copyConcatenating(right))
			}
			compiler.eventuallyParseRestOfSendNode(
				successorTree, stepState)
		}
	},

	/**
	 * `13` - Reserved for future use.
	 */
	@Suppress("unused")
	RESERVED_13(13, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			throw UnsupportedOperationException(
				"Illegal reserved parsing operation")
		}
	},

	/**
	 * `14` - Reserved for future use.
	 */
	@Suppress("unused")
	RESERVED_14(14, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			throw UnsupportedOperationException(
				"Illegal reserved parsing operation")
		}
	},

	/**
	 * `15` - Reserved for future use.
	 */
	@Suppress("unused")
	RESERVED_15(15, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			throw UnsupportedOperationException(
				"Illegal reserved parsing operation")
		}
	},

	////////////////////////////////////
	//       Arity one entries        //
	////////////////////////////////////

	/**
	 * `16*N+0` - Branch to instruction `N`, which must be after the current
	 * instruction. Attempt to continue parsing at both the next instruction and
	 * instruction `N`.
	 */
	BRANCH_FORWARD(0, false, true)
	{
		override fun successorPcs(instruction: Int, currentPc: Int) =
			listOf(operand(instruction), currentPc + 1)

		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			throw UnsupportedOperationException(
				"$name instruction should not be dispatched")
		}
	},

	/**
	 * `16*N+1` - Jump to instruction `N`, which must be after the current
	 * instruction. Attempt to continue parsing only at instruction `N`.
	 */
	JUMP_FORWARD(1, false, true)
	{
		override fun successorPcs(instruction: Int, currentPc: Int) =
			listOf(operand(instruction))

		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			throw UnsupportedOperationException(
				"$name instruction should not be dispatched")
		}
	},

	/**
	 * `16*N+2` - Jump to instruction `N`, which must be before the current
	 * instruction. Attempt to continue parsing only at instruction `N`.
	 */
	JUMP_BACKWARD(2, false, true)
	{
		override fun successorPcs(instruction: Int, currentPc: Int) =
			listOf(operand(instruction))

		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			throw UnsupportedOperationException(
				"$name instruction should not be dispatched")
		}
	},

	/**
	 * `16*N+3` - Parse the `N`<sup>th</sup>
	 * [message&#32;part][MessageSplitter.messageParts] of the current
	 * message. This will be a specific [token][TokenDescriptor]. It should be
	 * matched case sensitively against the source token.
	 */
	PARSE_PART(3, false, false)
	{
		override fun keywordIndex(instruction: Int) = operand(instruction)

		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			throw UnsupportedOperationException(
				"$name instruction should not be dispatched")
		}
	},

	/**
	 * `16*N+4` - Parse the `N`<sup>th</sup>
	 * [message&#32;part][MessageSplitter.messageParts] of the current
	 * message. This will be a specific [token][TokenDescriptor]. It should be
	 * matched case insensitively against the source token.
	 */
	PARSE_PART_CASE_INSENSITIVELY(4, false, false)
	{
		override fun keywordIndex(instruction: Int) = operand(instruction)

		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			throw UnsupportedOperationException(
				"$name instruction should not be dispatched")
		}
	},

	/**
	 * `16*N+5` - Apply grammatical restrictions to the `N`<sup>th</sup> leaf
	 * argument (underscore/ellipsis) of the current message, which is on the
	 * stack.
	 */
	CHECK_ARGUMENT(5, true, true)
	{
		override fun checkArgumentIndex(instruction: Int) = operand(instruction)

		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			assert(stepState.firstArgOrNull === null)
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}
	},

	/**
	 * `16*N+6` - Pop an argument from the parse stack and apply the
	 * [conversion&#32;rule][ParsingConversionRule] specified by `N`.
	 */
	CONVERT(6, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val input = stepState.pop()
			val sanityFlag = AtomicBoolean()
			val conversionRule = ruleNumber(operand(instruction))
			conversionRule.convert(
				compiler.compilationContext,
				stepState.start.lexingState,
				input,
				{ replacementExpression ->
					assert(sanityFlag.compareAndSet(false, true))
					stepState.push(replacementExpression)
					compiler.eventuallyParseRestOfSendNode(
						successorTree, stepState)
				},
				{ e ->
					// Deal with a failed conversion.  As of 2016-08-28, this
					// can only happen during an expression evaluation.
					assert(sanityFlag.compareAndSet(false, true))
					stepState.start.expected(STRONG) {
						it(
							"evaluation of expression not to have "
								+ "thrown Java exception:\n${trace(e)}")
					}
				})
		}

		override fun describe(operand: Int): String
		{
			return super.describe(operand) + " = " + ruleNumber(operand)
		}
	},

	/**
	 * `16*N+7` - A macro has been parsed up to a section checkpoint (§). Make a
	 * copy of the parse stack, then perform the equivalent of an
	 * [APPEND_ARGUMENT] on the copy, the specified number of times minus one
	 * (because zero is not a legal operand).  Make it into a single
	 * [list&#32;phrase][ListPhraseDescriptor] and push it onto the original
	 * parse stack. It will be consumed by a subsequent [RUN_PREFIX_FUNCTION].
	 *
	 * This instruction is detected specially by the
	 * [message&#32;bundle&#32;tree][A_BundleTree]'s
	 * [expand][A_BundleTree.expand] operation.  Its successors are separated
	 * into distinct message bundle trees, one per message bundle.
	 */
	PREPARE_TO_RUN_PREFIX_FUNCTION(7, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			var stackCopy = stepState.argsSoFar
			// Only do N-1 steps.  We simply couldn't encode zero as an operand,
			// so we always bias by one automatically.
			val fixupDepth = operand(instruction)
			for (i in fixupDepth downTo 2)
			{
				// Pop the last element and append it to the second last.
				val value = stackCopy.last()
				val poppedOnce = stackCopy.withoutLast()
				val oldNode = poppedOnce.last()
				val listNode = oldNode.copyWith(value)
				stackCopy = poppedOnce.withoutLast().append(listNode)
			}
			stepState.push(stackCopy.single())
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}
	},

	/**
	 * `16*N+8` - A macro has been parsed up to a section checkpoint (§), and a
	 * copy of the cleaned up parse stack has been pushed, so invoke the
	 * N<sup>th</sup> prefix function associated with the macro.  Consume the
	 * previously pushed copy of the parse stack.  The current [ParserState]'s
	 * [ParserState.clientDataMap] is stashed in the new
	 * [fiber][FiberDescriptor]'s [globals&#32;map][AvailObject.setFiberGlobals]
	 * and retrieved afterward, so the prefix function and macros can alter the
	 * scope or communicate with each other by manipulating this
	 * [map][MapDescriptor].  This technique prevents chatter between separate
	 * fibers (i.e., parsing can still be done in parallel) and between separate
	 * linguistic abstractions (the keys are atoms and are therefore modular).
	 */
	RUN_PREFIX_FUNCTION(8, false, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			// Look inside the only successor to find the only bundle.
			val bundlesMap = successorTree.allParsingPlansInProgress
			assert(bundlesMap.mapSize == 1)
			val submap = bundlesMap.mapIterable.first().value()
			assert(submap.mapSize == 1)
			val definition = submap.mapIterable.first().key()
			val prefixFunctions = definition.prefixFunctions()
			val prefixIndex = operand(instruction)
			val prefixFunction = prefixFunctions.tupleAt(prefixIndex)
			val arguments = stepState.pop().expressionsTuple
			compiler.runPrefixFunctionThen(
				successorTree,
				stepState,
				prefixFunction,
				toList(arguments))
		}
	},

	/**
	 * `16*N+9` - Permute the elements of the list phrase on the top of the
	 * stack via the permutation found via [MessageSplitter.permutationAtIndex].
	 *  The list phrase must be the same size as the permutation.
	 */
	PERMUTE_LIST(9, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val permutationIndex = operand(instruction)
			val permutation = permutationAtIndex(permutationIndex)
			stepState.push(newPermutedListNode(stepState.pop(), permutation))
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}

		override fun describe(operand: Int): String
		{
			return super.describe(operand) + " = " + permutationAtIndex(operand)
		}
	},

	/**
	 * `16*N+10` - Check that the list phrase on the top of the stack has at
	 * least the specified size.  Proceed to the next instruction only if this
	 * is the case.
	 */
	CHECK_AT_LEAST(10, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val limit = operand(instruction)
			val top = stepState.argsSoFar.last()
			if (top.expressionsSize >= limit)
			{
				compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
			}
		}
	},

	/**
	 * `16*N+11` - Check that the list phrase on the top of the stack has at
	 * most the specified size.  Proceed to the next instruction only if this is
	 * the case.
	 */
	CHECK_AT_MOST(11, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val limit = operand(instruction)
			val top = stepState.argsSoFar.last()
			if (top.expressionsSize <= limit)
			{
				compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
			}
		}
	},

	/**
	 * `16*N+12` - Use the type of the argument just parsed to select among
	 * successor message bundle trees.  Those message bundle trees are filtered
	 * by the allowable leaf argument type.  This test is *precise*, and
	 * requires repeated groups to be unrolled for the tuple type specific to
	 * that argument slot of that definition, or at least until the
	 * [A_Type.defaultType] of the tuple type has been reached.
	 */
	TYPE_CHECK_ARGUMENT(12, true, true)
	{
		override fun typeCheckArgumentIndex(instruction: Int) =
			operand(instruction)

		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			throw UnsupportedOperationException(
				"$name instruction should not be dispatched")
		}
	},

	/**
	 * `16*N+13` - Pop N arguments from the parse stack of the current potential
	 * message send. Create an N-element [list][ListPhraseDescriptor] with them,
	 * and push the list back onto the parse stack.
	 *
	 * This is the equivalent of pushing an empty list prior to pushing those
	 * arguments, then using [APPEND_ARGUMENT] after each argument is parsed to
	 * add them to the list.  The advantage of using this operation instead is
	 * to allow the pure stack manipulation operations to occur after parsing an
	 * argument and/or fixed tokens, which increases the conformity between the
	 * non-repeating and repeating clauses, which in turn reduces (at least) the
	 * number of actions executed each time the root bundle tree is used to
	 * start parsing a subexpression.
	 */
	WRAP_IN_LIST(13, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val listSize = operand(instruction)
			stepState.run {
				val totalSize = argsSoFar.size
				val unpopped = argsSoFar.subList(0, totalSize - listSize)
				val popped = argsSoFar.subList(totalSize - listSize, totalSize)
				val newListNode = newListNode(tupleFromList(popped))
				argsSoFar = unpopped.append(newListNode)
			}
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}
	},

	/**
	 * `16*N+14` - Push a [literal&#32;phrase][LiteralPhraseDescriptor]
	 * containing the constant found at the position in the type list indicated
	 * by the operand.
	 */
	PUSH_LITERAL(14, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val constant = constantForIndex(operand(instruction))
			val token = literalToken(
				stringFrom(constant.toString()),
				stepState.initialTokenPosition.position,
				stepState.initialTokenPosition.lineNumber,
				constant)
			stepState.push(literalNodeFromToken(token))
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}

		override fun describe(operand: Int): String
		{
			return super.describe(operand) + " = " + constantForIndex(operand)
		}
	},

	/**
	 * `16*N+15` - Reverse the `N` top elements of the stack.  The new stack has
	 * the same depth as the old stack.
	 */
	REVERSE_STACK(15, true, true)
	{
		override fun execute(
			compiler: AvailCompiler,
			stepState: ParsingStepState,
			instruction: Int,
			successorTree: A_BundleTree)
		{
			val depthToReverse = operand(instruction)
			stepState.run {
				val totalSize = argsSoFar.size
				val unpopped = argsSoFar.subList(0, totalSize - depthToReverse)
				val popped =
					argsSoFar.subList(totalSize - depthToReverse, totalSize)
				argsSoFar = unpopped + popped.reversed()
			}
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}
	};

	/**
	 * A [Statistic] that records the number of nanoseconds spent while
	 * executing occurrences of this [ParsingOperation].
	 */
	val parsingStatisticInNanoseconds: Statistic = Statistic(
		RUNNING_PARSING_INSTRUCTIONS, name)

	/**
	 * A [Statistic] that records the number of nanoseconds spent while
	 * expanding occurrences of this [ParsingOperation].
	 */
	val expandingStatisticInNanoseconds: Statistic = Statistic(
		EXPANDING_PARSING_INSTRUCTIONS, name)

	/** The instruction coding of the receiver. */
	val encoding: Int
		get()
		{
			if (ordinal >= distinctInstructions)
			{
				throw UnsupportedOperationException()
			}
			return modulus
		}

	/**
	 * Describe the operation for the debugger, using the supplied operand.
	 */
	open fun describe(operand: Int) = when (operand)
	{
		0 -> name
		else -> "$name (#$operand)"
	}

	/**
	 * Answer the instruction coding of the receiver for the given operand. The
	 * receiver must be arity one (`1`), which is equivalent to its
	 * ordinal being greater than or equal to [distinctInstructions].
	 *
	 * @param operand
	 *   The operand.
	 * @return
	 *   The instruction coding.
	 */
	fun encoding(operand: Int): Int
	{
		if (ordinal < distinctInstructions)
		{
			throw UnsupportedOperationException()
		}
		// The operand should be positive, but allow -1 to represent undefined
		// branch targets.  The generated code with a -1 operand will be wrong,
		// but the first pass of code emission calculates the correct branch
		// targets, and the second pass uses the correct targets.
		assert(operand > 0 || operand == -1)
		val result = (operand shl distinctInstructionsShift) + modulus
		assert(operand(result) == operand) { "Overflow detected" }
		return result
	}

	/**
	 * Assume that the instruction encodes an operand that represents a
	 * [message&#32;part][MessageSplitter.messageParts] index: answer the
	 * operand. Answer 0 if the operand does not represent a message part.
	 *
	 * @param instruction
	 *   A coded instruction.
	 * @return
	 *   The message part index, or `0` if the assumption was false.
	 */
	open fun keywordIndex(instruction: Int): Int = 0

	/**
	 * Given an instruction and program counter, answer the list of successor
	 * program counters that should be explored. For example, a [BRANCH_FORWARD]
	 * instruction will need to visit both the next program counter *and* the
	 * branch target.
	 *
	 * @param instruction
	 *   The encoded parsing instruction at the specified program counter.
	 * @param currentPc
	 *   The current program counter.
	 * @return
	 *   The list of successor program counters.
	 */
	open fun successorPcs(instruction: Int, currentPc: Int): List<Int> =
		listOf(currentPc + 1)

	/**
	 * Assume that the instruction encodes an operand that represents the index
	 * of an argument to be checked (for grammatical restrictions): answer the
	 * operand.
	 *
	 * @param instruction
	 *   A coded instruction.
	 * @return
	 *   The argument index, or `0` if the assumption was false.
	 */
	open fun checkArgumentIndex(instruction: Int): Int = 0

	/**
	 * Extract the index of the type check argument for a [TYPE_CHECK_ARGUMENT]
	 * parsing instruction.  This indexes the static
	 * [MessageSplitter.constantForIndex].
	 *
	 * @param instruction
	 *   A coded instruction
	 * @return
	 *   The index of the type to be checked against.
	 */
	open fun typeCheckArgumentIndex(instruction: Int): Int
	{
		throw RuntimeException("Parsing instruction is inappropriate")
	}

	/**
	 * Perform one parsing instruction.
	 *
	 * @param compiler
	 *   The [AvailCompiler] which is parsing.
	 * @param stepState
	 *   Information about the current partially-constructed message or macro
	 *   send that has not yet been completed.
	 * @param instruction
	 *   An [Int] encoding the [ParsingOperation] to execute.
	 * @param successorTree
	 *   The [A_BundleTree] at which to continue parsing.
	 * @return
	 *   An optional [ParsingStepState] that the caller is expected to continue
	 *   to execute, if present.  By returning a successor state, we avoid the
	 *   cost of the work queue machinery in the common cases.  This also
	 *   reduces the parameter-passing cost, since the [ParsingStepState] may be
	 *   mutated and returned, as long as any forked executions make suitable
	 *   copies first.
	 */
	internal abstract fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		instruction: Int,
		successorTree: A_BundleTree)

	companion object
	{
		/**
		 * My array of values, since [ParsingOperation.values] makes a copy
		 * every time.
		 */
		internal val all = values()

		/**
		 * The binary logarithm of the number of distinct instructions supported
		 * by the coding scheme.  It must be integral.
		 */
		internal const val distinctInstructionsShift = 4

		/**
		 * The number of distinct instructions supported by the coding scheme.
		 * It must be a power of two.
		 */
		const val distinctInstructions = 1 shl distinctInstructionsShift

		/**
		 * Answer the operand given a coded instruction (that represents the
		 * same operation as the receiver).
		 *
		 * @param instruction
		 *   A coded instruction.
		 * @return
		 *   The operand.
		 */
		fun operand(instruction: Int) =
			instruction shr distinctInstructionsShift

		/**
		 * Decode the specified instruction into a `ParsingOperation`.
		 *
		 * @param instruction
		 *   A coded instruction.
		 * @return
		 *   The decoded operation.
		 */
		fun decode(instruction: Int): ParsingOperation
		{
			if (instruction < distinctInstructions)
			{
				return all[instruction]
			}
			// It's parametric, so it resides in the next 'distinctInstructions'
			// region of enum values.  Mask it and add the offset.
			val subscript =
				(instruction and distinctInstructions - 1) +
					distinctInstructions
			return all[subscript]
		}

		/**
		 * Produce a [Describer] that says a variable use was expected, and
		 * indicates why.
		 *
		 * @param successorTree
		 *   The next [A_BundleTree] after the current instruction.
		 * @return
		 *   The [Describer].
		 */
		private fun describeWhyVariableUseIsExpected(
				successorTree: A_BundleTree): Describer =
			{ continuation ->
				val bundles = successorTree.allParsingPlansInProgress.keysAsSet
				val builder = StringBuilder()
				builder.append("a variable use, for one of:")
				if (bundles.setSize > 2)
				{
					builder.append("\n\t")
				}
				else
				{
					builder.append(' ')
				}
				var first = true
				for (bundle in bundles)
				{
					if (!first)
					{
						builder.append(", ")
					}
					builder.append(bundle.message.atomName)
					first = false
				}
				continuation(builder.toString())
			}
	}
}
