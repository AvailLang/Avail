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

import avail.compiler.AvailCompiler.Companion.skipWhitespaceAndComments
import avail.compiler.ParsingConversionRule.Companion.rule
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK
import avail.compiler.splitter.InstructionGenerator
import avail.compiler.splitter.MessageSplitter
import avail.compiler.splitter.MessageSplitter.Companion.constantForIndex
import avail.compiler.splitter.MessageSplitter.Companion.permutationAtIndex
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.bundles.A_BundleTree
import avail.descriptor.bundles.A_BundleTree.Companion.allParsingPlansInProgress
import avail.descriptor.bundles.A_BundleTree.Companion.expand
import avail.descriptor.fiber.A_Fiber.Companion.fiberGlobals
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
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import avail.descriptor.tokens.TokenDescriptor
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tokens.TokenDescriptor.TokenType.COMMENT
import avail.descriptor.tokens.TokenDescriptor.TokenType.END_OF_FILE
import avail.descriptor.tokens.TokenDescriptor.TokenType.KEYWORD
import avail.descriptor.tokens.TokenDescriptor.TokenType.LITERAL
import avail.descriptor.tokens.TokenDescriptor.TokenType.WHITESPACE
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
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
import avail.utility.stackToString
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [ParsingOperation] describes the operations available for parsing Avail
 * message names.
 *
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
 * @param commutesWithParsePart
 *   Whether a [PARSE_PART] or [PARSE_PART_CASE_INSENSITIVELY] instructions can
 *   be moved safely leftward over this instruction.
 * @param canRunIfHasFirstArgument
 *   Whether this instruction can be run if the first argument has been parsed
 *   but not yet consumed by a PARSE_ARGUMENT instruction.
 */
sealed class ParsingOperation constructor(
	val commutesWithParsePart: Boolean,
	val canRunIfHasFirstArgument: Boolean)
{
	/** The name of the instruction. */
	val name = this::class.java.simpleName

	/**
	 * A [Statistic] that records the number of nanoseconds spent while
	 * executing occurrences of this [ParsingOperation].
	 */
	val parsingStatisticInNanoseconds: Statistic = Statistic(
		RUNNING_PARSING_INSTRUCTIONS, name
	)

	/**
	 * A [Statistic] that records the number of nanoseconds spent while
	 * expanding occurrences of this [ParsingOperation].
	 */
	val expandingStatisticInNanoseconds: Statistic = Statistic(
		EXPANDING_PARSING_INSTRUCTIONS, name
	)

	/**
	 * A description of the operation for the debugger.
	 */
	open val debuggerDescription: String = name

	/**
	 * Assume that the instruction encodes an operand that represents a
	 * [message&#32;part][MessageSplitter.messageParts] index: answer the
	 * operand. Answer 0 if the operand does not represent a message part.
	 *
	 * @return
	 *   The message part index, or `0` if the assumption was false.
	 */
	open val keywordIndex get(): Int = 0

	/**
	 * Given a program counter, answer the list of successor program counters
	 * that should be explored. For example, a [BRANCH_FORWARD] instruction will
	 * need to visit both the next program counter *and* the branch target.
	 *
	 * @param currentPc
	 *   The current program counter.
	 * @return
	 *   The list of successor program counters.
	 */
	open fun successorPcs(currentPc: Int) = listOf(currentPc + 1)

	/**
	 * Assume that the instruction encodes an operand that represents the index
	 * of an argument to be checked (for grammatical restrictions): answer the
	 * operand.
	 *
	 * @return
	 *   The argument index, or `0` if the assumption was false.
	 */
	open val checkArgumentIndex get(): Int = 0

	/**
	 * Extract the index of the type check argument for a [TYPE_CHECK_ARGUMENT]
	 * parsing instruction.  This indexes the static
	 * [MessageSplitter.constantForIndex].
	 *
	 * @return
	 *   The index of the type to be checked against.
	 */
	open val typeCheckArgumentIndex get(): Int
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
		successorTree: A_BundleTree
	)

	companion object
	{
		/**
		 * Given a [A_String] used for indentation, describe it for use in a
		 * diagnostic message to the user.
		 */
		@JvmStatic
		protected val A_String.q: String
			get() = when {
				this.tupleSize == 0 -> "\"\""
				else -> this.toString()
			}
	}
}

////////////////////////////////////////////////////////////////////////////////
//                           Arity zero operations.                           //
////////////////////////////////////////////////////////////////////////////////

/**
 * Placeholder for use by an [InstructionGenerator]. Must not survive beyond
 * code generation.
 */
data object Placeholder: ParsingOperation(false, false)
{
	/** The sentinel for the placeholder. */
	val index = Integer.MIN_VALUE

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	) = throw RuntimeException("Placeholder should never be executed")
}

/**
 * Push a new [list][ListPhraseDescriptor] that contains an
 * [empty&#32;tuple][TupleDescriptor.emptyTuple] of [phrases][PhraseDescriptor]
 * onto the parse stack.
 */
data object EMPTY_LIST: ParsingOperation(true, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		stepState.push(emptyListNode())
		compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
	}
}

/**
 * Pop an argument from the parse stack of the current potential message send.
 * Pop a [list][ListPhraseDescriptor] from the parse stack. Append the argument
 * to the list. Push the resultant list onto the parse stack.
 */
data object APPEND_ARGUMENT: ParsingOperation(true, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		val value = stepState.pop()
		val list = stepState.pop()
		stepState.push(list.copyWith(value))
		compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
	}
}

/**
 * Push the current parse position onto the mark stack.
 */
data object SAVE_PARSE_POSITION: ParsingOperation(false, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		val marker = when (stepState.firstArgOrNull)
		{
			null -> stepState.start.position
			else -> stepState.initialTokenPosition.position
		}
		stepState.pushMarker(marker)
		compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
	}
}

/**
 * Pop the top marker off the mark stack.
 */
data object DISCARD_SAVED_PARSE_POSITION: ParsingOperation(true, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		stepState.popMarker()
		compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
	}
}

/**
 * Pop the top marker off the mark stack and compare it to the current parse
 * position.  If they're the same, abort the current parse, otherwise push the
 * current parse position onto the mark stack in place of the old marker and
 * continue parsing.
 */
data object ENSURE_PARSE_PROGRESS: ParsingOperation(false, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
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
}

/**
 * Parse an ordinary argument of a message send, pushing the expression onto the
 * parse stack.
 */
data object PARSE_ARGUMENT: ParsingOperation(false, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
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
				// The argument counts as something that was consumed if it's
				// not a leading argument...
				consumedAnything = firstArgOrNull === null
				firstArgOrNull = null
				push(phrase)
			}
			compiler.eventuallyParseRestOfSendNode(
				successorTree, stepStateCopy)
		}
	}
}

/**
 * Parse an expression, even one whose expressionType is ⊤, then push *a literal
 * phrase wrapping this expression* onto the parse stack.
 *
 * If we didn't wrap the phrase inside a literal phrase, we wouldn't be able to
 * process sequences of statements in macros, since they would each have an
 * expressionType of ⊤ (or if one was ⊥, the entire expressionType would also be
 * ⊥).  Instead, they will have the expressionType phrase⇒⊤ (or phrase⇒⊥), which
 * is perfectly fine to put inside a list phrase during parsing.
 */
data object PARSE_TOP_VALUED_ARGUMENT: ParsingOperation(false, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
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
				// The argument counts as something that was consumed if it's
				// not a leading argument...
				consumedAnything = firstArgOrNull === null
				firstArgOrNull = null
				push(phrase)
			}
			compiler.eventuallyParseRestOfSendNode(
				successorTree, stepStateCopy)
		}
	}
}

/**
 * Parse a [raw&#32;token][TokenDescriptor]. It should correspond to a
 * [variable][VariableDescriptor] that is in scope. Push a
 * [variable&#32;reference][ReferencePhraseDescriptor] onto the parse stack.
 */
data object PARSE_VARIABLE_REFERENCE: ParsingOperation(false, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
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
					// At least one token besides the variable use has been
					// encountered, so go ahead and report that we expected a
					// variable.
					afterUse.expected(
						if (stepState.staticTokens.isEmpty()) WEAK
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
					// Only complain about this not being a variable if we've
					// parsed something besides the variable reference argument.
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
				// The argument counts as something that was consumed if it's
				// not a leading argument...
				consumedAnything = firstArgOrNull === null
				firstArgOrNull = null
				// Push the new argument phrase.
				push(variableReference)
			}
			compiler.eventuallyParseRestOfSendNode(
				successorTree, stepStateCopy)
		}
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
		successorTree: A_BundleTree
	): Describer =
		{ continuation ->
			val bundles = successorTree.allParsingPlansInProgress.keysAsSet
			val builder = StringBuilder()
			builder.append("a variable use, for one of:")
			if (bundles.setSize > 2) {
				builder.append("\n\t")
			} else {
				builder.append(' ')
			}
			var first = true
			for (bundle in bundles) {
				if (!first) {
					builder.append(", ")
				}
				builder.append(bundle.message.atomName)
				first = false
			}
			continuation(builder.toString())
		}
}

/**
 * Parse an argument of a message send, using the *outermost (module) scope*.
 * Leave it on the parse stack.
 */
data object PARSE_ARGUMENT_IN_MODULE_SCOPE: ParsingOperation(false, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		compiler.parseArgumentInModuleScopeThen(stepState, successorTree)
	}
}

/**
 * Parse *any* [raw&#32;token][TokenDescriptor], leaving it on the parse stack.
 * In particular, push a literal phrase whose token is a synthetic literal token
 * whose value is the actual token that was parsed.
 */
data object PARSE_ANY_RAW_TOKEN: ParsingOperation(false, false)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		if (stepState.firstArgOrNull !== null)
		{
			// Starting with a parseRawToken can't cause unbounded
			// left-recursion, so treat it more like reading an expected token
			// than like parseArgument.  Thus, if a firstArgument has been
			// provided (i.e., we're attempting to parse a leading-argument
			// message to wrap a leading expression), then reject the parse.
			return
		}
		compiler.nextNonwhitespaceTokensDo(stepState.start) { token ->
			val tokenType = token.tokenType()
			assert(tokenType != WHITESPACE && tokenType != COMMENT)
			if (tokenType == END_OF_FILE)
			{
				stepState.start.expected(
					if (stepState.staticTokens.isEmpty()) WEAK
					else STRONG,
					"any token, not end-of-file")
				return@nextNonwhitespaceTokensDo
			}
			val syntheticToken = literalToken(
				token.string(),
				token.start(),
				token.lineNumber(),
				token,
				token.generatingLexer)
			syntheticToken.setCurrentModule(
				compiler.compilationContext.module)
			val stepStateCopy = stepState.copy {
				start = ParserState(
					token.nextLexingState(), stepState.start.clientDataMap)
				// Until we've passed the type test, we don't consider tokens
				// read past it in the stream to have been truly encountered.
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
}

/**
 * Parse a raw *[keyword][TokenType.KEYWORD]* [token][TokenDescriptor], leaving
 * it on the parse stack.
 */
data object PARSE_RAW_KEYWORD_TOKEN: ParsingOperation(false, false)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		if (stepState.firstArgOrNull !== null)
		{
			// Starting with a parseRawToken can't cause unbounded
			// left-recursion, so treat it more like reading an expected token
			// than like parseArgument.  Thus, if a firstArgument has been
			// provided (i.e., we're attempting to parse a leading-argument
			// message to wrap a leading expression), then reject the parse.
			return
		}
		compiler.nextNonwhitespaceTokensDo(stepState.start) { token ->
			val tokenType = token.tokenType()
			if (tokenType != KEYWORD)
			{
				if (stepState.consumedAnything)
				{
					stepState.start.expected(
						if (stepState.staticTokens.isEmpty()) WEAK
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
				token,
				token.generatingLexer)
			syntheticToken.setCurrentModule(
				compiler.compilationContext.module)
			val stepStateCopy = stepState.copy {
				start = ParserState(
					token.nextLexingState(), stepState.start.clientDataMap)
				// Until we've passed the type test, we don't consider tokens
				// read past it in the stream to have been truly encountered.
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
}

/**
 * Parse a raw *[literal][TokenType.LITERAL]* [token][TokenDescriptor], leaving
 * it on the parse stack.
 */
data object PARSE_RAW_LITERAL_TOKEN: ParsingOperation(false, false)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		if (stepState.firstArgOrNull !== null)
		{
			// Starting with a parseRawToken can't cause unbounded
			// left-recursion, so treat it more like reading an expected token
			// than like parseArgument.  Thus, if a firstArgument has been
			// provided (i.e., we're attempting to parse a leading-argument
			// message to wrap a leading expression), then reject the parse.
			return
		}
		compiler.nextNonwhitespaceTokensDo(stepState.start) { token ->
			val tokenType = token.tokenType()
			if (tokenType != LITERAL)
			{
				if (stepState.consumedAnything)
				{
					stepState.start.expected(
						if (stepState.staticTokens.isEmpty()) WEAK
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
				token,
				token.generatingLexer)
			syntheticToken.setCurrentModule(
				compiler.compilationContext.module)
			val stepStateCopy = stepState.copy {
				start = ParserState(
					token.nextLexingState(), stepState.start.clientDataMap)
				// Until we've passed the type test, we don't consider tokens
				// read past it in the stream to have been truly encountered.
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
}

/**
 * Concatenate the two lists that have been pushed previously.
 */
data object CONCATENATE: ParsingOperation(false, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
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
}

/**
 * Check whether we are positioned immediately after a newline and whitespace
 * that matches the line on which the first token of this phrase or its leading
 * argument occurred.  If the indentation string (i.e., spaces and tabs) agrees,
 * continue parsing, otherwise report a weak error and abandon this parse
 * theory.
 */
data object MATCH_INDENT: ParsingOperation(false, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		skipWhitespaceAndComments(stepState.start) { successorStates ->
			successorStates.forEach { parsingState ->
				val state = stepState.copy { start = parsingState }
				val initialIndent = state.initialIndentationString()
				val currentIndent = state.currentIndentationString()
				when
				{
					currentIndent === null ->
					{
						state.start.expected(STRONG) { accept ->
							accept(
								"an indentation to match that at the " +
									"beginning of the phrase " +
									"(${initialIndent.q}), but this " +
									"position is not after leading " +
									"whitespace on the line")
						}
					}
					!currentIndent.equals(initialIndent) ->
					{
						state.start.expected(STRONG) { accept ->
							accept(
								"the indentation to exactly match that " +
									"at the beginning of the phrase. The " +
									"indentation at the beginning was " +
									"${initialIndent.q}, but the current " +
									"line begins with ${currentIndent.q}.")
						}
					}
					else -> compiler.eventuallyParseRestOfSendNode(
						successorTree, state)
				}
			}
		}
	}
}

/**
 * Check whether we are positioned immediately after a newline and whitespace
 * that contains as a prefix the whitespace on which the first token of this
 * phrase or its leading argument occurred.  If the current indentation string
 * is a proper extension of the initial whitespace, continue parsing, otherwise
 * report a weak error and abandon this parse theory.
 */
data object INCREASE_INDENT: ParsingOperation(false, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		skipWhitespaceAndComments(stepState.start) { successorStates ->
			successorStates.forEach { parsingState ->
				val state = stepState.copy { start = parsingState }
				val initialIndent = state.initialIndentationString()
				val currentIndent = state.currentIndentationString()
				when
				{
					currentIndent === null ->
						state.start.expected(STRONG) { accept ->
							accept(
								"an indentation to exceed that at the " +
									"beginning of the phrase " +
									"(${initialIndent.q}), but this " +
									"position is not after leading " +
									"whitespace on the line")
						}
					currentIndent.tupleSize > initialIndent.tupleSize
						&& currentIndent.compareFromToWithStartingAt(
						1, initialIndent.tupleSize, initialIndent, 1)
					-> compiler.eventuallyParseRestOfSendNode(
						successorTree, state)
					initialIndent.equals(currentIndent) ->
						state.start.expected(STRONG) { accept ->
							accept(
								"the indentation at this position to be " +
									"strictly deeper than at the " +
									"beginning of the phrase, but " +
									"they're equal (${currentIndent.q}).")
						}
					currentIndent.equals(initialIndent) ->
						state.start.expected(STRONG) { accept ->
							accept(
								"the indentation at this position to be " +
									"strictly deeper than at the " +
									"beginning of the phrase. The " +
									"initial indentation is " +
									"${initialIndent.q}, but the current " +
									"indentation is ${currentIndent.q}.")
						}
					else -> state.start.expected(STRONG) { accept ->
						accept(
							"the indentation to exceed that at the " +
								"beginning of the phrase. The " +
								"indentation at the beginning was " +
								"${initialIndent.q}, but the current line " +
								"begins with ${currentIndent.q}."
						)
					}
				}
			}
		}
	}
}

/**
 * Ensure we are *not* positioned at a series of whitespace and comments that
 * include a line break.  This test is specifically to support "⇥⁇" and "↹⁇".
 * Without this filter instruction, the normal token processing would allow the
 * "didn't look for indent" path to consume any whitespace at all, including a
 * wrong indents, making the construct pointless.
 */
data object NO_LINE_BREAK: ParsingOperation(false, true)
{
	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		val line = stepState.start.lineNumber
		skipWhitespaceAndComments(stepState.start) { successorStates ->
			val (sameLine, otherLine) =
				successorStates.partition { it.lineNumber == line }
			when
			{
				sameLine.isNotEmpty() ->
				{
					// Continue parsing after the whitespace, since it's
					// still on the same line.  Silently ignore any other
					// interpretations that contained line breaks, since we
					// found at least one without.
					sameLine.forEach { s ->
						compiler.eventuallyParseRestOfSendNode(
							successorTree, stepState.copy { start = s })
					}
				}
				otherLine.isNotEmpty() ->
				{
					// There were only interpretations that had whitespace
					// that contained a line break, which are forbidden by
					// this parsing instruction
					val furthest = otherLine.maxBy(ParserState::position)
					furthest.expected(WEAK) { accept ->
						accept(
							"no line break in this interpretation, " +
								"although an alternative form with a " +
								"specific indentation scheme has also " +
								"been attempted.")
					}
				}
				else ->
				{
					// No way was found to parse (optional) whitespace
					// successfully at this position.  Assume that failure
					// already produced a better diagnostic than we can do
					// here.  Neither warn nor proceed.
				}
			}
		}
	}
}

////////////////////////////////////////////////////////////////////////////////
//                           Arity one operations.                            //
////////////////////////////////////////////////////////////////////////////////

/**
 * [ArityOneParsingOperation] is a [ParsingOperation] that takes a single
 * operand.
 *
 * @property operand
 *   The operand.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [ArityOneParsingOperation] for this enum.
 *
 * @param commutesWithParsePart
 *   Whether a [PARSE_PART] or [PARSE_PART_CASE_INSENSITIVELY] instructions can
 *   be moved safely leftward over this instruction.
 * @param canRunIfHasFirstArgument
 *   Whether this instruction can be run if the first argument has been parsed
 *   but not yet consumed by a PARSE_ARGUMENT instruction.
 */
abstract class ArityOneParsingOperation constructor(
	commutesWithParsePart: Boolean,
	canRunIfHasFirstArgument: Boolean
): ParsingOperation(commutesWithParsePart, canRunIfHasFirstArgument)
{
	abstract val operand: Int

	override val debuggerDescription by lazy {
		"$name (#$operand)"
	}

	/**
	 * Copy the receiver, replacing its operand with the given value.
	 *
	 * @param operand
	 *   The operand.
	 * @return
	 *   A suitably adjusted copy of the receiver.
	 */
	abstract fun newOperand(operand: Int): ArityOneParsingOperation
}

/**
 * Branch to the specified instruction, which must be after the current
 * instruction. Attempt to continue parsing at both the next instruction and the
 * specified instruction.
 */
data class BRANCH_FORWARD constructor(
	override val operand: Int
): ArityOneParsingOperation(false, true)
{
	override fun successorPcs(currentPc: Int) = listOf(operand, currentPc + 1)
	override fun newOperand(operand: Int) = BRANCH_FORWARD(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		throw UnsupportedOperationException(
			"$name instruction should not be dispatched")
	}
}

/**
 * Jump to the specified instruction, which must be after the current
 * instruction. Attempt to continue parsing only at the specified instruction.
 */
data class JUMP_FORWARD constructor(
	override val operand: Int
): ArityOneParsingOperation(false, true)
{
	override fun successorPcs(currentPc: Int) = listOf(operand)
	override fun newOperand(operand: Int) = JUMP_FORWARD(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		throw UnsupportedOperationException(
			"$name instruction should not be dispatched")
	}
}

/**
 * Jump to instruction specified instruction, which must be before the current
 * instruction. Attempt to continue parsing only at the specified instruction.
 */
data class JUMP_BACKWARD constructor(
	override val operand: Int
): ArityOneParsingOperation(false, true)
{
	override fun successorPcs(currentPc: Int) = listOf(operand)
	override fun newOperand(operand: Int) = JUMP_BACKWARD(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		throw UnsupportedOperationException(
			"$name instruction should not be dispatched")
	}
}

/**
 * Parse the [operand]<sup>th</sup>
 * [message&#32;part][MessageSplitter.messageParts] of the current message. This
 * will be a specific [token][TokenDescriptor]. It should be matched case
 * sensitively against the source token.
 */
data class PARSE_PART constructor(
	override val operand: Int
): ArityOneParsingOperation(false, false)
{
	override val keywordIndex = operand
	override fun newOperand(operand: Int) = PARSE_PART(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		throw UnsupportedOperationException(
			"$name instruction should not be dispatched")
	}
}

/**
 * Parse the [operand]<sup>th</sup>
 * [message&#32;part][MessageSplitter.messageParts] of the current message. This
 * will be a specific [token][TokenDescriptor]. It should be matched case
 * insensitively against the source token.
 */
data class PARSE_PART_CASE_INSENSITIVELY constructor(
	override val operand: Int
): ArityOneParsingOperation(false, false)
{
	override val keywordIndex = operand
	override fun newOperand(operand: Int) =
		PARSE_PART_CASE_INSENSITIVELY(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		throw UnsupportedOperationException(
			"$name instruction should not be dispatched")
	}
}

/**
 * Apply grammatical restrictions to the [operand]<sup>th</sup> leaf argument
 * (underscore/ellipsis) of the current message, which is on the stack.
 */
data class CHECK_ARGUMENT constructor(
	override val operand: Int
): ArityOneParsingOperation(true, true)
{
	override val checkArgumentIndex = operand
	override fun newOperand(operand: Int) = CHECK_ARGUMENT(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		assert(stepState.firstArgOrNull === null)
		compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
	}
}

/**
 * Pop an argument from the parse stack and apply the
 * [conversion&#32;rule][ParsingConversionRule] specified by [operand].
 */
data class CONVERT constructor(
	override val operand: Int
): ArityOneParsingOperation(true, true)
{
	override fun newOperand(operand: Int) = CONVERT(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		val input = stepState.pop()
		val sanityFlag = AtomicBoolean()
		val conversionRule = rule(operand)
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
				// Deal with a failed conversion.  As of 2016-08-28, this can
				// only happen during an expression evaluation.
				assert(sanityFlag.compareAndSet(false, true))
				stepState.start.expected(STRONG) {
					it(
						"evaluation of expression not to have "
							+ "thrown Java exception:\n${e.stackToString}")
				}
			})
	}

	override val debuggerDescription get() =
		super.debuggerDescription + " = " + rule(operand)
}

/**
 * A macro has been parsed up to a section checkpoint (§). Make a copy of the
 * parse stack, then perform the equivalent of an [APPEND_ARGUMENT] on the copy,
 * the specified number of times minus one (because zero is not a legal
 * operand).  Make it into a single [list&#32;phrase][ListPhraseDescriptor] and
 * push it onto the original parse stack. It will be consumed by a subsequent
 * [RUN_PREFIX_FUNCTION].
 *
 * This instruction is detected specially by the
 * [message&#32;bundle&#32;tree][A_BundleTree]'s [expand][A_BundleTree.expand]
 * operation.  Its successors are separated into distinct message bundle trees,
 * one per message bundle.
 */
data class PREPARE_TO_RUN_PREFIX_FUNCTION constructor(
	override val operand: Int
): ArityOneParsingOperation(false, true)
{
	override fun newOperand(operand: Int) =
		PREPARE_TO_RUN_PREFIX_FUNCTION(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		var stackCopy = stepState.argsSoFar
		// Only do N-1 steps.  We simply couldn't encode zero as an operand, so
		// we always bias by one automatically.
		for (i in operand downTo 2)
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
}

/**
 * A macro has been parsed up to a section checkpoint (§), and a copy of the
 * cleaned up parse stack has been pushed, so invoke the [operand]<sup>th</sup>
 * prefix function associated with the macro. Consume the previously pushed copy
 * of the parse stack.  The current [ParserState]'s
 * [client&#32;data&#32;map][ParserState.clientDataMap] is stashed in the new
 * [fiber][FiberDescriptor]'s [globals&#32;map][fiberGlobals] and retrieved
 * afterward, so the prefix function and macros can alter the scope or
 * communicate with each other by manipulating this [map][MapDescriptor]. This
 * technique prevents chatter between separate fibers (i.e., parsing can still
 * be done in parallel) and between separate linguistic abstractions (the keys
 * are atoms and are therefore modular).
 */
data class RUN_PREFIX_FUNCTION constructor(
	override val operand: Int
): ArityOneParsingOperation(false, true)
{
	override fun newOperand(operand: Int) = RUN_PREFIX_FUNCTION(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		// Look inside the only successor to find the only bundle.
		val bundlesMap = successorTree.allParsingPlansInProgress
		assert(bundlesMap.mapSize == 1)
		val submap = bundlesMap.mapIterable.first().value()
		assert(submap.mapSize == 1)
		val definition = submap.mapIterable.first().key()
		val prefixFunctions = definition.prefixFunctions()
		val prefixFunction = prefixFunctions.tupleAt(operand)
		val arguments = stepState.pop().expressionsTuple
		compiler.runPrefixFunctionThen(
			successorTree,
			stepState,
			prefixFunction,
			toList(arguments))
	}
}

/**
 * Permute the elements of the list phrase on the top of the stack via the
 * permutation found via [MessageSplitter.permutationAtIndex]. The list phrase
 * must be the same size as the permutation.
 */
data class PERMUTE_LIST constructor(
	override val operand: Int
): ArityOneParsingOperation(true, true)
{
	override fun newOperand(operand: Int) = PERMUTE_LIST(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		val permutation = permutationAtIndex(operand)
		stepState.push(newPermutedListNode(stepState.pop(), permutation))
		compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
	}

	override val debuggerDescription get() =
		super.debuggerDescription + " = " + permutationAtIndex(operand)
}

/**
 * Check that the list phrase on the top of the stack has at least the specified
 * size.  Proceed to the next instruction only if this is the case.
 */
data class CHECK_AT_LEAST constructor(
	override val operand: Int
): ArityOneParsingOperation(true, true)
{
	override fun newOperand(operand: Int) = CHECK_AT_LEAST(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		val top = stepState.argsSoFar.last()
		if (top.expressionsSize >= operand)
		{
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}
	}
}

/**
 * Check that the list phrase on the top of the stack has at most the specified
 * size.  Proceed to the next instruction only if this is the case.
 */
data class CHECK_AT_MOST constructor(
	override val operand: Int
): ArityOneParsingOperation(true, true)
{
	override fun newOperand(operand: Int) = CHECK_AT_MOST(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		val top = stepState.argsSoFar.last()
		if (top.expressionsSize <= operand)
		{
			compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
		}
	}
}

/**
 * Use the type of the argument just parsed to select among successor message
 * bundle trees.  Those message bundle trees are filtered by the allowable leaf
 * argument type.  This test is *precise*, and requires repeated groups to be
 * unrolled for the tuple type specific to that argument slot of that
 * definition, or at least until the [A_Type.defaultType] of the tuple type has
 * been reached.
 */
data class TYPE_CHECK_ARGUMENT constructor(
	override val operand: Int
): ArityOneParsingOperation(true, true)
{
	override val typeCheckArgumentIndex = operand
	override fun newOperand(operand: Int) = TYPE_CHECK_ARGUMENT(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		throw UnsupportedOperationException(
			"$name instruction should not be dispatched")
	}
}

/**
 * Pop [operand] arguments from the parse stack of the current potential message
 * send. Create an [operand]-element [list][ListPhraseDescriptor] with them, and
 * push the list back onto the parse stack.
 *
 * This is the equivalent of pushing an empty list prior to pushing those
 * arguments, then using [APPEND_ARGUMENT] after each argument is parsed to add
 * them to the list.  The advantage of using this operation instead is to allow
 * the pure stack manipulation operations to occur after parsing an argument
 * and/or fixed tokens, which increases the conformity between the non-repeating
 * and repeating clauses, which in turn reduces (at least) the number of actions
 * executed each time the root bundle tree is used to start parsing a
 * subexpression.
 */
data class WRAP_IN_LIST constructor(
	override val operand: Int
): ArityOneParsingOperation(true, true)
{
	override fun newOperand(operand: Int) = WRAP_IN_LIST(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		stepState.run {
			val totalSize = argsSoFar.size
			val unpopped = argsSoFar.subList(0, totalSize - operand)
			val popped = argsSoFar.subList(totalSize - operand, totalSize)
			val newListNode = newListNode(tupleFromList(popped))
			argsSoFar = unpopped.append(newListNode)
		}
		compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
	}
}

/**
 * Push a [literal&#32;phrase][LiteralPhraseDescriptor] containing the constant
 * found at the [position][operand] in the type list indicated by the
 * operand.
 */
data class PUSH_LITERAL constructor(
	override val operand: Int
): ArityOneParsingOperation(true, true)
{
	override fun newOperand(operand: Int) = PUSH_LITERAL(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		val constant = constantForIndex(operand)
		val token = literalToken(
			stringFrom(constant.toString()),
			stepState.initialTokenPosition.position,
			stepState.initialTokenPosition.lineNumber,
			constant,
			nil)
		stepState.push(literalNodeFromToken(token))
		compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
	}

	override val debuggerDescription get() =
		super.debuggerDescription + " = " + constantForIndex(operand)
}

/**
 * Reverse the [operand] top elements of the stack.  The new stack has the same
 * depth as the old stack.
 */
data class REVERSE_STACK constructor(
	override val operand: Int
): ArityOneParsingOperation(true, true)
{
	override fun newOperand(operand: Int) = REVERSE_STACK(operand)

	override fun execute(
		compiler: AvailCompiler,
		stepState: ParsingStepState,
		successorTree: A_BundleTree
	)
	{
		stepState.run {
			val totalSize = argsSoFar.size
			val unpopped = argsSoFar.subList(0, totalSize - operand)
			val popped = argsSoFar.subList(totalSize - operand, totalSize)
			argsSoFar = unpopped + popped.reversed()
		}
		compiler.eventuallyParseRestOfSendNode(successorTree, stepState)
	}
}
