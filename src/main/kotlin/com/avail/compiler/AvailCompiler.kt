/*
 * AvailCompiler.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.compiler

import com.avail.AvailRuntime
import com.avail.AvailRuntime.currentRuntime
import com.avail.AvailRuntimeConfiguration
import com.avail.AvailRuntimeSupport.captureNanos
import com.avail.builder.ModuleName
import com.avail.builder.ResolvedModuleName
import com.avail.compiler.ParsingOperation.CHECK_ARGUMENT
import com.avail.compiler.ParsingOperation.Companion.decode
import com.avail.compiler.ParsingOperation.Companion.distinctInstructions
import com.avail.compiler.ParsingOperation.Companion.operand
import com.avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT
import com.avail.compiler.PragmaKind.Companion.pragmaKindByLexeme
import com.avail.compiler.problems.CompilerDiagnostics
import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.*
import com.avail.compiler.problems.Problem
import com.avail.compiler.problems.ProblemHandler
import com.avail.compiler.problems.ProblemType.EXTERNAL
import com.avail.compiler.problems.ProblemType.PARSE
import com.avail.compiler.scanning.LexingState
import com.avail.compiler.splitter.MessageSplitter.Companion.constantForIndex
import com.avail.compiler.splitter.MessageSplitter.Metacharacter
import com.avail.descriptor.*
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn
import com.avail.descriptor.AssignmentPhraseDescriptor.newAssignment
import com.avail.descriptor.CompiledCodeDescriptor.newPrimitiveRawFunction
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_CONSTANT
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_VARIABLE
import com.avail.descriptor.DeclarationPhraseDescriptor.newModuleConstant
import com.avail.descriptor.DeclarationPhraseDescriptor.newModuleVariable
import com.avail.descriptor.FiberDescriptor.GeneralFlag
import com.avail.descriptor.FiberDescriptor.newLoaderFiber
import com.avail.descriptor.FunctionDescriptor.createFunction
import com.avail.descriptor.FunctionDescriptor.createFunctionForPhrase
import com.avail.descriptor.LexerDescriptor.lexerBodyFunctionType
import com.avail.descriptor.LexerDescriptor.lexerFilterFunctionType
import com.avail.descriptor.ListPhraseDescriptor.emptyListNode
import com.avail.descriptor.ListPhraseDescriptor.newListNode
import com.avail.descriptor.LiteralPhraseDescriptor.literalNodeFromToken
import com.avail.descriptor.LiteralPhraseDescriptor.syntheticLiteralNodeFor
import com.avail.descriptor.LiteralTokenDescriptor.literalToken
import com.avail.descriptor.MacroSubstitutionPhraseDescriptor.newMacroSubstitution
import com.avail.descriptor.MapDescriptor.emptyMap
import com.avail.descriptor.MapDescriptor.mapFromPairs
import com.avail.descriptor.MarkerPhraseDescriptor.newMarkerNode
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.*
import com.avail.descriptor.ModuleDescriptor.newModule
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.*
import com.avail.descriptor.ParsingPlanInProgressDescriptor.newPlanInProgress
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.*
import com.avail.descriptor.SendPhraseDescriptor.newSendNode
import com.avail.descriptor.SetDescriptor.emptySet
import com.avail.descriptor.SetDescriptor.generateSetFrom
import com.avail.descriptor.StringDescriptor.formatString
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.descriptor.TokenDescriptor.TokenType.*
import com.avail.descriptor.TupleDescriptor.toList
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TypeDescriptor.Types.TOKEN
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.descriptor.VariableSharedGlobalDescriptor.createGlobal
import com.avail.descriptor.VariableTypeDescriptor.variableTypeFor
import com.avail.descriptor.VariableUsePhraseDescriptor.newUse
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.AtomDescriptor.*
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.*
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_SemanticRestriction
import com.avail.descriptor.objects.A_BasicObject
import com.avail.descriptor.parsing.A_Lexer
import com.avail.descriptor.parsing.A_Phrase
import com.avail.descriptor.parsing.BlockPhraseDescriptor
import com.avail.descriptor.parsing.PhraseDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.dispatch.LookupTree
import com.avail.exceptions.AvailAssertionFailedException
import com.avail.exceptions.AvailEmergencyExitException
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION
import com.avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION
import com.avail.interpreter.AvailLoader
import com.avail.interpreter.AvailLoader.Phase.COMPILING
import com.avail.interpreter.AvailLoader.Phase.EXECUTING_FOR_COMPILE
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Interpreter.runOutermostFunction
import com.avail.interpreter.Interpreter.stringifyThen
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Companion.primitiveByName
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED
import com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForConstant
import com.avail.interpreter.primitive.phrases.P_RejectParsing
import com.avail.io.SimpleCompletionHandler
import com.avail.io.TextInterface
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport.RUNNING_PARSING_INSTRUCTIONS
import com.avail.persistence.IndexedRepositoryManager
import com.avail.utility.*
import com.avail.utility.Locks.lockWhile
import com.avail.utility.PrefixSharingList.append
import com.avail.utility.PrefixSharingList.last
import com.avail.utility.StackPrinter.trace
import com.avail.utility.Strings.increaseIndentation
import com.avail.utility.evaluation.Describer
import com.avail.utility.evaluation.FormattingDescriber
import java.io.IOException
import java.lang.String.format
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.Collections.emptyList
import java.util.Comparator.comparing
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Collectors.toList
import java.util.stream.Collectors.toMap
import java.util.stream.IntStream
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.toList

/**
 * The compiler for Avail code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `AvailCompiler`.
 *
 * @param moduleHeader
 *   The [module header][ModuleHeader] of the module to compile. May be null for
 *   synthetic modules (for entry points), or when parsing the header.
 * @param module
 *   The current [module][ModuleDescriptor].`
 * @param source
 *   The source [A_String].
 * @param textInterface
 *   The [text interface][TextInterface] for any [fibers][A_Fiber] started by
 *   this compiler.
 * @param pollForAbort
 *   How to quickly check if the client wants to abort compilation.
 * @param progressReporter
 *   How to report progress to the client who instigated compilation. This
 *   [continuation][CompilerProgressReporter] that accepts the
 *   [name][ModuleName] of the [module][A_Module] undergoing
 *   [compilation][AvailCompiler], the line number on which the last complete
 *   statement concluded, the position of the ongoing parse (in bytes), and the
 *   size of the module (in bytes).
 * @param problemHandler
 *   The [ProblemHandler] used for reporting compilation problems.
 */
class AvailCompiler(
	moduleHeader: ModuleHeader?,
	module: A_Module,
	source: A_String,
	textInterface: TextInterface,
	pollForAbort: () -> Boolean,
	progressReporter: CompilerProgressReporter,
	problemHandler: ProblemHandler)
{
	/**
	 * The [CompilationContext] for this compiler.  It tracks parsing and lexing
	 * tasks, and handles serialization to a
	 * [repository][IndexedRepositoryManager] if necessary.
	 */
	val compilationContext: CompilationContext = CompilationContext(
		moduleHeader,
		module,
		source,
		textInterface,
		pollForAbort,
		progressReporter,
		problemHandler)

	/** The memoization of results of previous parsing attempts. */
	private val fragmentCache = AvailCompilerFragmentCache()

	/**
	 * The Avail [A_String] containing the complete content of the module
	 * being compiled.
	 */
	val source: A_String get() = compilationContext.source

	/**
	 * The [module header][ModuleHeader] for the current
	 * [module][ModuleDescriptor] being parsed.
	 */
	private val moduleHeader get() = compilationContext.moduleHeader!!

	/**
	 * The fully-qualified name of the [module][ModuleDescriptor] undergoing
	 * compilation.
	 *
	 * @return
	 *   The module name.
	 */
	private val moduleName get() = ModuleName(
		compilationContext.module.moduleName().asNativeString())

	/**
	 * A list of subexpressions being parsed, represented by [message bundle
	 * trees][A_BundleTree] holding the positions within all outer send
	 * expressions.
	 *
	 * @property bundleTree
	 *   The [A_BundleTree] being parsed at this moment.
	 * @property parent
	 *   The parent [PartialSubexpressionList] being parsed.
	 * @constructor
	 *
	 * Construct a new `PartialSubexpressionList`.
	 *
	 * @param bundleTree
	 *   The current [A_BundleTree] being parsed.
	 * @param parent
	 *   The enclosing partially-parsed super-expressions being parsed.
	 */
	internal class PartialSubexpressionList constructor(
		val bundleTree: A_BundleTree,
		val parent: PartialSubexpressionList?)
	{
		/** How many subexpressions deep that we're parsing.  */
		val depth: Int = if (parent === null) 1 else parent.depth + 1

		/**
		 * Create a list like the receiver, but with a different [message bundle
		 * tree][A_BundleTree].
		 *
		 * @param newBundleTree
		 *   The new [A_BundleTree] to replace the one in the receiver within
		 *   the copy.
		 * @return
		 *   A `PartialSubexpressionList` like the receiver, but with a
		 *   different message bundle tree.
		 */
		fun advancedTo(newBundleTree: A_BundleTree) =
			PartialSubexpressionList(newBundleTree, parent)
	}

	/**
	 * Output a description of the layers of message sends that are being parsed
	 * at this point in history.
	 *
	 * @param partialSubexpressions
	 *   The [PartialSubexpressionList] that captured the nesting of partially
	 *   parsed superexpressions.
	 * @param builder
	 *   Where to describe the chain of superexpressions.
	 */
	private fun describeOn(
		partialSubexpressions: PartialSubexpressionList?,
		builder: StringBuilder)
	{
		var pointer = partialSubexpressions
		if (pointer === null)
		{
			builder.append("\n\t(top level expression)")
			return
		}
		val maxDepth = 10
		val limit = max(pointer.depth - maxDepth, 0)
		while (pointer !== null && pointer.depth >= limit)
		{
			builder.append("\n\t")
			builder.append(pointer.depth)
			builder.append(". ")
			val bundleTree = pointer.bundleTree
			if (bundleTree.equals(compilationContext.loader!!.rootBundleTree()))
			{
				builder.append("an expression")
			}
			else
			{
				// Reduce to the plans' unique bundles.
				val bundlesMap = bundleTree.allParsingPlansInProgress()
				val bundles = toList<A_Bundle>(bundlesMap.keysAsSet().asTuple())
				bundles.sortWith(
					comparing<A_Bundle, String> {
						b -> b.message().atomName().asNativeString()
					})
				var first = true
				val maxBundles = 3
				for (bundle in bundles.subList(0, min(bundles.size,maxBundles)))
				{
					if (!first)
					{
						builder.append(", ")
					}
					val plans = bundlesMap.mapAt(bundle)
					// Pick an active plan arbitrarily for this bundle.
					val plansInProgress = plans.mapIterable().next().value()
					val planInProgress = plansInProgress.iterator().next()
					// Adjust the pc to refer to the actual instruction that
					// caused the argument parse, not the successor instruction
					// that was captured.
					val adjustedPlanInProgress = newPlanInProgress(
						planInProgress.parsingPlan(),
						planInProgress.parsingPc() - 1)
					builder.append(adjustedPlanInProgress.nameHighlightingPc())
					first = false
				}
				if (bundles.size > maxBundles)
				{
					builder.append("… (and ")
					builder.append(bundles.size - maxBundles)
					builder.append(" others)")
				}
			}
			pointer = pointer.parent
		}
	}

	/**
	 * Execute `#tryBlock`, passing a [continuation][Con1] that it should run
	 * upon finding exactly one local [solution][CompilerSolution].  Report
	 * ambiguity as an error.
	 *
	 * @param start
	 *   Where to start parsing.
	 * @param tryBlock
	 *   What to try. This is a continuation that accepts a continuation that
	 *   tracks completion of parsing.
	 * @param supplyAnswer
	 *   What to do if exactly one result was produced. This is a continuation
	 *   that accepts a solution.
	 */
	private fun tryIfUnambiguousThen(
		start: ParserState,
		tryBlock: (Con1)->Unit,
		supplyAnswer: Con1)
	{
		assert(compilationContext.noMoreWorkUnits === null)
		val solutions = ArrayList<CompilerSolution>(1)
		compilationContext.noMoreWorkUnits = noMoreWorkUnits@{
			if (compilationContext.diagnostics.pollForAbort())
			{
				// We may have been asked to abort sub-tasks by a failure in
				// another module, so we can't trust the count of solutions.
				compilationContext.diagnostics.reportError()
				return@noMoreWorkUnits
			}
			when (solutions.size)
			{
				0 ->
				{
					// No solutions were found.  Report the problems.
					compilationContext.diagnostics.reportError()
				}
				1 ->
				{
					// A unique solution was found.
					supplyAnswer(solutions[0])
				}
				else ->
				{
					val solution1 = solutions[0]
					val solution2 = solutions[1]
					reportAmbiguousInterpretations(
						solution1.endState,
						solution1.phrase,
						solution2.phrase)
				}
			}
		}
		start.workUnitDo(
			tryBlock,
			Con1(supplyAnswer.superexpressions) {
				newSolution -> captureOneMoreSolution(newSolution, solutions)
			})
	}

	/**
	 * As part of determining an unambiguous interpretation of some top-level
	 * expression, deal with one (more) solution having been found.
	 *
	 * @param newSolution
	 *   The new [CompilerSolution].
	 * @param solutions
	 *   The mutable [List] that collects [CompilerSolution]s, at least up to
	 *   the second solution to arrive.
	 */
	@Synchronized
	private fun captureOneMoreSolution(
		newSolution: CompilerSolution,
		solutions: MutableList<CompilerSolution>)
	{
		// Ignore any solutions discovered after the first two.
		if (solutions.size < 2)
		{
			solutions.add(newSolution)
		}
	}

	/**
	 * A statement was parsed correctly in two different ways. There may be more
	 * ways, but we stop after two as it's already an error. Report the error.
	 *
	 * @param where
	 *   Where the expressions were parsed from.
	 * @param interpretation1
	 *   The first interpretation as a [phrase][PhraseDescriptor].
	 * @param interpretation2
	 *   The second interpretation as a [phrase][PhraseDescriptor].
	 */
	private fun reportAmbiguousInterpretations(
		where: ParserState,
		interpretation1: A_Phrase,
		interpretation2: A_Phrase)
	{
		val phrase1 = Mutable(interpretation1)
		val phrase2 = Mutable(interpretation2)
		if (phrase1.value.equals(phrase2.value))
		{
			where.expected(
				STRONG,
				listOf(
					phrase1.value,
					phrase1.value.macroOriginalSendNode())
			) { strings ->
				("unambiguous interpretation.  "
					 + "At least two parses produced the same "
					 + "phrase:\n\t"
					 + strings[0]
					 + "\n...where the pre-macro expression is:\n\t"
					 + strings[1])
			}
		}
		else
		{
			findParseTreeDiscriminants(phrase1, phrase2)
			if (phrase1.value.isMacroSubstitutionNode
				&& phrase2.value.isMacroSubstitutionNode)
			{
				where.expected(
					STRONG,
					listOf(
						phrase1.value,
						phrase2.value,
						phrase1.value.macroOriginalSendNode(),
						phrase2.value.macroOriginalSendNode())
				) { strings ->
					val print1 = strings[0]
					val print2 = strings[1]
					val original1 = strings[2]
					val original2 = strings[3]
					if (print1 == print2)
					{
						("unambiguous interpretation.  At least two parses "
							+ "produced same-looking phrases after macro "
							+ "substitution.  The post-macro phrase is:\n"
							+ "\t$print1\n...and the pre-macro phrases are:\n"
							+ "\t$original1\n"
							+ "\t$original2")
					}
					else
					{
						("unambiguous interpretation.  Here are two possible "
							+ "parsings...\n\t$print1\n\t$print2")
					}
				}
			}
			else
			{
				where.expected(
					STRONG,
					listOf(phrase1.value, phrase2.value)
				) { strings ->
					val print1 = strings[0]
					val print2 = strings[1]
					if (print1 == print2)
					{
						("unambiguous interpretation.  "
							+ "At least two parses produced unequal but "
							+ "same-looking phrases:\n\t"
							+ print1)
					}
					else
					{
						("unambiguous interpretation.  "
							+ "Here are two possible parsings...\n\t"
							+ print1
							+ "\n\t"
							+ print2)
					}
				}
			}
		}
		compilationContext.diagnostics.reportError()
	}

	/**
	 * Start definition of a [module][ModuleDescriptor]. The entire definition
	 * can be rolled back because the [interpreter][Interpreter]'s context
	 * module will contain all methods and precedence rules defined between the
	 * transaction start and the rollback (or commit). Committing simply clears
	 * this information.
	 */
	private fun startModuleTransaction()
	{
		val newLoader = AvailLoader(
			compilationContext.module,
			compilationContext.textInterface)
		compilationContext.loader = newLoader
	}

	/**
	 * Rollback the [module][ModuleDescriptor] that was defined since the most
	 * recent [startModuleTransaction].
	 *
	 * @param afterRollback
	 *   What to do after rolling back.
	 */
	private fun rollbackModuleTransaction(afterRollback: ()->Unit) =
		compilationContext.module.removeFrom(
			compilationContext.loader!!, afterRollback)

	/**
	 * Commit the [module][ModuleDescriptor] that was defined since the most
	 * recent [startModuleTransaction].
	 */
	private fun commitModuleTransaction() =
		compilationContext.runtime.addModule(compilationContext.module)

	/**
	 * Evaluate the specified semantic restriction [function][A_Function] in the
	 * module's context; lexically enclosing variables are not considered in
	 * scope, but module variables and constants are in scope.
	 *
	 * @param restriction
	 *   A [semantic restriction][SemanticRestrictionDescriptor].
	 * @param args
	 *   The arguments to the function.
	 * @param lexingState
	 *   The position at which the semantic restriction is being evaluated.
	 * @param onSuccess
	 *   What to do with the result of the evaluation.
	 * @param onFailure
	 *   What to do with a terminal [Throwable].
	 */
	private fun evaluateSemanticRestrictionFunctionThen(
		restriction: A_SemanticRestriction,
		args: List<A_BasicObject>,
		lexingState: LexingState,
		onSuccess: (AvailObject)->Unit,
		onFailure: (Throwable)->Unit)
	{
		val function = restriction.function()
		val code = function.code()
		val mod = code.module()
		val fiber = newLoaderFiber(
			function.kind().returnType(),
			compilationContext.loader!!
		) {
			formatString(
				"Semantic restriction %s, in %s:%d",
				restriction.definitionMethod().bundles()
					.iterator().next().message(),
				if (mod.equals(nil))
					"no module"
				else
					mod.moduleName(),
				code.startingLineNumber())
		}
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE)
		fiber.textInterface(compilationContext.textInterface)
		lexingState.setFiberContinuationsTrackingWork(
			fiber, onSuccess, onFailure)
		runOutermostFunction(compilationContext.runtime, fiber, function, args)
	}

	/**
	 * Evaluate the specified macro [function][FunctionDescriptor] in the
	 * module's context; lexically enclosing variables are not considered in
	 * scope, but module variables and constants are in scope.
	 *
	 * @param macro
	 *   A [macro definition][MacroDefinitionDescriptor].
	 * @param args
	 *   The argument phrases to supply the macro.
	 * @param clientParseData
	 *   The map to associate with the [SpecialAtom.CLIENT_DATA_GLOBAL_KEY] atom
	 *   in the fiber.
	 * @param clientParseDataOut
	 *   A [MutableOrNull] into which we will store an [A_Map] when the fiber
	 *   completes successfully.  The map will be the content of the fiber
	 *   variable holding the client data, extracted just after the fiber
	 *   completes.  If unsuccessful, don't assign to the `MutableOrNull`.
	 * @param lexingState
	 *   The position at which the macro body is being evaluated.
	 * @param onSuccess
	 *   What to do with the result of the evaluation, an [A_Phrase].
	 * @param onFailure
	 *   What to do with a terminal [Throwable].
	 */
	private fun evaluateMacroFunctionThen(
		macro: A_Definition,
		args: List<A_Phrase>,
		clientParseData: A_Map,
		clientParseDataOut: MutableOrNull<A_Map>,
		lexingState: LexingState,
		onSuccess: (AvailObject)->Unit,
		onFailure: (Throwable)->Unit)
	{
		val function = macro.bodyBlock()
		val code = function.code()
		val mod = code.module()
		val fiber = newLoaderFiber(
			function.kind().returnType(),
			compilationContext.loader!!
		) {
			formatString(
				"Macro evaluation %s, in %s:%d",
				macro.definitionMethod().bundles()
					.iterator().next().message(),
				if (mod.equals(nil))
					"no module"
				else
					mod.moduleName(),
				code.startingLineNumber())
		}
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE)
		fiber.setGeneralFlag(GeneralFlag.IS_EVALUATING_MACRO)
		var fiberGlobals = fiber.fiberGlobals()
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			CLIENT_DATA_GLOBAL_KEY.atom, clientParseData, true)
		fiber.fiberGlobals(fiberGlobals)
		fiber.textInterface(compilationContext.textInterface)
		lexingState.setFiberContinuationsTrackingWork(
			fiber,
			{ outputPhrase ->
				clientParseDataOut.value =
					fiber.fiberGlobals().mapAt(CLIENT_DATA_GLOBAL_KEY.atom)
				onSuccess(outputPhrase)
			},
			onFailure)
		runOutermostFunction(compilationContext.runtime, fiber, function, args)
	}

	/**
	 * Evaluate a phrase. It's a top-level statement in a module. Declarations
	 * are handled differently - they cause a variable to be declared in the
	 * module's scope.
	 *
	 * @param startState
	 *   The start [ParserState], for line number reporting.
	 * @param afterStatement
	 *   The [ParserState] just after the statement.
	 * @param expression
	 *   The expression to compile and evaluate as a top-level statement in the
	 *   module.
	 * @param declarationRemap
	 *   A [Map] holding the isomorphism between phrases and their replacements.
	 *   This is especially useful for keeping track of how to transform
	 *   references to prior declarations that have been transformed from
	 *   local-scoped to module-scoped.
	 * @param onSuccess
	 *   What to do after success. Note that the result of executing the
	 *   statement must be [nil][NilDescriptor.nil], so there is no point in
	 *   having the continuation accept this value, hence the nullary
	 *   continuation.
	 */
	internal fun evaluateModuleStatementThen(
		startState: ParserState,
		afterStatement: ParserState,
		expression: A_Phrase,
		declarationRemap: MutableMap<A_Phrase, A_Phrase>,
		onSuccess: ()->Unit)
	{
		assert(!expression.isMacroSubstitutionNode)
		// The mapping through declarationRemap has already taken place.
		val replacement = treeMapWithParent(
			expression,
			{ phrase, _, _ -> phrase },
			nil,
			ArrayList(),
			declarationRemap)

		val phraseFailure = { e: Throwable ->
			when (e)
			{
				is AvailAssertionFailedException ->
					compilationContext.reportAssertionFailureProblem(
						startState.lineNumber,
						startState.position,
						e)
				is AvailEmergencyExitException ->
					compilationContext.reportEmergencyExitProblem(
						startState.lineNumber,
						startState.position,
						e)
				else -> compilationContext.reportExecutionProblem(
					startState.lineNumber,
					startState.position,
					e)
			}
			compilationContext.diagnostics.reportError()
		}

		if (!replacement.phraseKindIsUnder(DECLARATION_PHRASE))
		{
			// Only record module statements that aren't declarations. Users of
			// the module don't care if a module variable or constant is only
			// reachable from the module's methods.
			compilationContext.evaluatePhraseThen(
				replacement,
				afterStatement.lexingState,
				true,
				false,
				{ onSuccess() },
				phraseFailure)
			return
		}
		// It's a declaration, but the parser couldn't previously tell that it
		// was at module scope.  Serialize a function that will cause the
		// declaration to happen, so that references to the global
		// variable/constant from a subsequent module will be able to find it by
		// name.
		val module = compilationContext.module
		val loader = compilationContext.loader!!
		val name = replacement.token().string()
		val shadowProblem =
			when
			{
				module.variableBindings().hasKey(name) -> "module variable"
				module.constantBindings().hasKey(name) -> "module constant"
				else -> null
			}
		when (replacement.declarationKind())
		{
			LOCAL_CONSTANT ->
			{
				if (shadowProblem !== null)
				{
					afterStatement.expected(
						STRONG,
						"new module constant "
						+ name
						+ " not to have same name as existing "
						+ shadowProblem)
					compilationContext.diagnostics.reportError()
					return
				}
				loader.startRecordingEffects()
				compilationContext.evaluatePhraseThen(
					replacement.initializationExpression(),
					afterStatement.lexingState,
					false,
					false,
					{ value ->
						loader.stopRecordingEffects()
						val canSummarize = loader.statementCanBeSummarized()
						val innerType = instanceTypeOrMetaOn(value)
						val varType = variableTypeFor(innerType)
						val creationSend = newSendNode(
							TupleDescriptor.emptyTuple(),
							CREATE_MODULE_VARIABLE.bundle,
							newListNode(
								tuple(
									syntheticLiteralNodeFor(module),
									syntheticLiteralNodeFor(name),
									syntheticLiteralNodeFor(varType),
									syntheticLiteralNodeFor(trueObject()),
									syntheticLiteralNodeFor(
										objectFromBoolean(canSummarize)))),
							TOP.o())
						val creationFunction = createFunctionForPhrase(
							creationSend,
							module,
							replacement.token().lineNumber())
						// Force the declaration to be serialized.
						compilationContext.serializeWithoutSummary(
							creationFunction)
						val variable = createGlobal(varType, module, name, true)
						variable.valueWasStablyComputed(canSummarize)
						module.addConstantBinding(name, variable)
						// Update the map so that the local constant goes to a
						// module constant.  Then subsequent statements in this
						// sequence will transform uses of the constant
						// appropriately.
						val newConstant = newModuleConstant(
							replacement.token(),
							variable,
							replacement.initializationExpression())
						declarationRemap[expression] = newConstant
						// Now create a module variable declaration (i.e.,
						// cheat) JUST for this initializing assignment.
						val newDeclaration = newModuleVariable(
							replacement.token(),
							variable,
							nil,
							replacement.initializationExpression())
						val assign = newAssignment(
							newUse(replacement.token(), newDeclaration),
							syntheticLiteralNodeFor(value),
							expression.tokens(),
							false)
						val assignFunction = createFunctionForPhrase(
							assign,
							module,
							replacement.token().lineNumber())
						compilationContext.serializeWithoutSummary(
							assignFunction)
						variable.setValue(value)
						onSuccess()
					},
					phraseFailure)
			}
			LOCAL_VARIABLE ->
			{
				if (shadowProblem !== null)
				{
					afterStatement.expected(
						STRONG,
						"new module variable "
						+ name
						+ " not to have same name as existing "
						+ shadowProblem)
					compilationContext.diagnostics.reportError()
					return
				}
				val varType = variableTypeFor(replacement.declaredType())
				val creationSend = newSendNode(
					TupleDescriptor.emptyTuple(),
					CREATE_MODULE_VARIABLE.bundle,
					newListNode(
						tuple(
							syntheticLiteralNodeFor(module),
							syntheticLiteralNodeFor(name),
							syntheticLiteralNodeFor(varType),
							syntheticLiteralNodeFor(falseObject()),
							syntheticLiteralNodeFor(falseObject()))),
					TOP.o())
				val creationFunction = createFunctionForPhrase(
					creationSend,
					module,
					replacement.token().lineNumber())
				creationFunction.makeImmutable()
				// Force the declaration to be serialized.
				compilationContext.serializeWithoutSummary(creationFunction)
				val variable = createGlobal(varType, module, name, false)
				module.addVariableBinding(name, variable)
				if (!replacement.initializationExpression().equalsNil())
				{
					val newDeclaration = newModuleVariable(
						replacement.token(),
						variable,
						replacement.typeExpression(),
						replacement.initializationExpression())
					declarationRemap[expression] = newDeclaration
					val assign = newAssignment(
						newUse(replacement.token(), newDeclaration),
						replacement.initializationExpression(),
						tuple(expression.token()),
						false)
					val assignFunction = createFunctionForPhrase(
						assign, module, replacement.token().lineNumber())
					compilationContext.evaluatePhraseThen(
						replacement.initializationExpression(),
						afterStatement.lexingState,
						false,
						false,
						{ value ->
							variable.setValue(value)
							compilationContext.serializeWithoutSummary(
								assignFunction)
							onSuccess()
						},
						phraseFailure)
				}
				else
				{
					onSuccess()
				}
			}
			else -> assert(false) {
				"Expected top-level declaration to have been parsed as local"
			}
		}
	}

	/**
	 * Report that the parser was expecting one of several keywords. The
	 * keywords are keys of the [A_Map] argument `incomplete`.
	 *
	 * @param where
	 *   Where the keywords were expected.
	 * @param incomplete
	 *   A map of partially parsed keywords, where the keys are the strings that
	 *   were expected at this position.
	 * @param caseInsensitive
	 *   `true` if the parsed keywords are case-insensitive, `false` otherwise.
	 * @param excludedStrings
	 *   The [Set] of [A_String]s to omit from the message, since they were the
	 *   actual encountered tokens' texts.  Note that this set may have multiple
	 *   elements because multiple lexers may have produced competing tokens at
	 *   this position.
	 */
	private fun expectedKeywordsOf(
		where: ParserState,
		incomplete: A_Map,
		caseInsensitive: Boolean,
		excludedStrings: Set<A_String>)
	{
		where.expected(MEDIUM) { withString ->
			val builder = StringBuilder(200)
			if (caseInsensitive)
			{
				builder.append(
					"one of the following case-insensitive tokens:")
			}
			else
			{
				builder.append("one of the following tokens:")
			}
			val sorted = ArrayList<String>(incomplete.mapSize())
			val detail = incomplete.mapSize() < 10
			for (entry in incomplete.mapIterable())
			{
				val availTokenString = entry.key()
				if (!excludedStrings.contains(availTokenString))
				{
					if (!detail)
					{
						sorted.add(availTokenString.asNativeString())
						continue
					}
					// Collect the plans-in-progress and deduplicate them by
					// their string representation (including the indicator at
					// the current parsing location). We can't just deduplicate
					// by bundle, since the current bundle tree might be
					// eligible for continued parsing at multiple positions.
					val strings = HashSet<String>()
					val nextTree = entry.value()
					for (successorBundleEntry
						in nextTree.allParsingPlansInProgress().mapIterable())
					{
						val bundle = successorBundleEntry.key()
						for (definitionEntry
							in successorBundleEntry.value().mapIterable())
						{
							for (inProgress in definitionEntry.value())
							{
								val previousPlan = newPlanInProgress(
									inProgress.parsingPlan(),
									max(inProgress.parsingPc() - 1, 1))
								val issuingModule =
									bundle.message().issuingModule()
								val moduleName =
									if (issuingModule.equalsNil())
										"(built-in)"
									else
										issuingModule.moduleName()
											.asNativeString()
								val shortModuleName = moduleName.substring(
									moduleName.lastIndexOf('/') + 1)
								strings.add(
									previousPlan.nameHighlightingPc()
									+ " from "
									+ shortModuleName)
							}
						}
					}
					val sortedStrings = ArrayList(strings)
					sortedStrings.sort()
					val buffer = StringBuilder()
					buffer.append(availTokenString.asNativeString())
					buffer.append("  (")
					var first = true
					for (progressString in sortedStrings)
					{
						if (!first)
						{
							buffer.append(", ")
						}
						buffer.append(progressString)
						first = false
					}
					buffer.append(')')
					sorted.add(buffer.toString())
				}
			}
			sorted.sort()
			var startOfLine = true
			val leftColumn = 4 + 4 // ">>> " and a tab.
			var column = leftColumn
			for (s in sorted)
			{
				if (startOfLine)
				{
					builder.append("\n\t")
					column = leftColumn
				}
				else
				{
					builder.append("  ")
					column += 2
				}
				startOfLine = false
				val lengthBefore = builder.length
				builder.append(s)
				column += builder.length - lengthBefore
				if (detail || column + 2 + s.length > 80)
				{
					startOfLine = true
				}
			}
			compilationContext.eventuallyDo(where.lexingState) {
				withString(builder.toString())
			}
		}
	}

	/**
	 * Parse a send phrase. To prevent infinite left-recursion and false
	 * ambiguity, we only allow a send with a leading keyword to be parsed from
	 * here, since leading underscore sends are dealt with iteratively
	 * afterward.
	 *
	 * @param start
	 *   Where to start parsing.
	 * @param continuation
	 *   What to do after parsing a complete send phrase.
	 */
	private fun parseLeadingKeywordSendThen(
		start: ParserState,
		continuation: Con1)
	{
		val loader = compilationContext.loader!!
		parseRestOfSendNode(
			start,
			loader.rootBundleTree(),
			null,
			start,
			false, // Nothing consumed yet.
			false, // (ditto)
			emptyList(),
			initialParseStack,
			initialMarkStack,
			Con1(
				PartialSubexpressionList(
					loader.rootBundleTree(),
					continuation.superexpressions),
				continuation))
	}

	/**
	 * Parse a send phrase whose leading argument has already been parsed.
	 *
	 * @param start
	 *   Where to start parsing.
	 * @param leadingArgument
	 *   The argument that was already parsed.
	 * @param initialTokenPosition
	 *   Where the leading argument started.
	 * @param continuation
	 *   What to do after parsing a send phrase.
	 */
	private fun parseLeadingArgumentSendAfterThen(
		start: ParserState,
		leadingArgument: A_Phrase,
		initialTokenPosition: ParserState,
		continuation: Con1)
	{
		assert(start.lexingState != initialTokenPosition.lexingState)
		val loader = compilationContext.loader!!
		parseRestOfSendNode(
			start,
			loader.rootBundleTree(),
			leadingArgument,
			initialTokenPosition,
			false, // Leading argument does not yet count as something parsed.
			false, // (ditto)
			emptyList(),
			initialParseStack,
			initialMarkStack,
			Con1(
				PartialSubexpressionList(
					loader.rootBundleTree(),
					continuation.superexpressions),
				continuation))
	}

	/**
	 * Parse an expression with an optional leading-argument message send around
	 * it. Backtracking will find all valid interpretations.
	 *
	 * @param startOfLeadingArgument
	 *   Where the leading argument started.
	 * @param afterLeadingArgument
	 *   Just after the leading argument.
	 * @param phrase
	 *   An expression that acts as the first argument for a potential
	 *   leading-argument message send, or possibly a chain of them.
	 * @param continuation
	 *   What to do with either the passed phrase, or the phrase wrapped in a
	 *   leading-argument send.
	 */
	private fun parseOptionalLeadingArgumentSendAfterThen(
		startOfLeadingArgument: ParserState,
		afterLeadingArgument: ParserState,
		phrase: A_Phrase,
		continuation: Con1)
	{
		// It's optional, so try it with no wrapping.  We have to try this even
		// if it's a supercast, since we may be parsing an expression to be a
		// non-leading argument of some send.
		afterLeadingArgument.workUnitDo(
			continuation,
			CompilerSolution(afterLeadingArgument, phrase))
		// Try to wrap it in a leading-argument message send.
		val con = Con1(continuation.superexpressions) { solution2 ->
			parseLeadingArgumentSendAfterThen(
				solution2.endState,
				solution2.phrase,
				startOfLeadingArgument,
				Con1(continuation.superexpressions) { solutionAfter ->
					parseOptionalLeadingArgumentSendAfterThen(
						startOfLeadingArgument,
						solutionAfter.endState,
						solutionAfter.phrase,
						continuation)
				})
		}
		afterLeadingArgument.workUnitDo(
			con,
			CompilerSolution(afterLeadingArgument, phrase))
	}

	/**
	 * We've parsed part of a send. Try to finish the job.
	 *
	 * @param start
	 *   Where to start parsing.
	 * @param bundleTreeArg
	 *   The bundle tree used to parse at this position.
	 * @param firstArgOrNull
	 *   Either `null` or an argument that must be consumed before any keywords
	 *   (or completion of a send).
	 * @param initialTokenPosition
	 *   The parse position where the send phrase started to be processed. Does
	 *   not count the position of the first argument if there are no leading
	 *   keywords.
	 * @param consumedAnything
	 *   Whether any actual tokens have been consumed so far for this send
	 *   phrase.  That includes any leading argument.
	 * @param consumedAnythingBeforeLatestArgument
	 *   Whether any tokens or arguments had been consumed before encountering
	 *   the most recent argument.  This is to improve diagnostics when argument
	 *   type checking is postponed past matches for subsequent tokens.
	 * @param consumedTokens
	 *   The immutable [List] of [A_Token]s that have been consumed so far in
	 *   this potential method/macro send, only including tokens that correspond
	 *   with literal message parts of this send (like the `"+"` of `"_+_"`),
	 *   and raw tokens (like the `"…"` of `"…:=_;"`).
	 * @param argsSoFar
	 *   The list of arguments parsed so far. I do not modify it. This is a
	 *   stack of expressions that the parsing instructions will assemble into a
	 *   list that correlates with the top-level non-backquoted underscores and
	 *   guillemet groups in the message name.
	 * @param marksSoFar
	 *   The stack of mark positions used to test if parsing certain
	 *   subexpressions makes progress.
	 * @param continuation
	 *   What to do with a fully parsed send phrase.
	 */
	private fun parseRestOfSendNode(
		start: ParserState,
		bundleTreeArg: A_BundleTree,
		firstArgOrNull: A_Phrase?,
		initialTokenPosition: ParserState,
		consumedAnything: Boolean,
		consumedAnythingBeforeLatestArgument: Boolean,
		consumedTokens: List<A_Token>,
		argsSoFar: List<A_Phrase>,
		marksSoFar: List<Int>,
		continuation: Con1)
	{
		var tempBundleTree = bundleTreeArg
		// If a bundle tree is marked as a source of a cycle, its latest
		// backward jump field is always the target.  Just continue processing
		// there and it'll never have to expand the current node.  However, it's
		// the expand() that might set up the cycle in the first place...
		tempBundleTree.expand(compilationContext.module)
		while (tempBundleTree.isSourceOfCycle)
		{
			// Jump to its (once-)equivalent ancestor.
			tempBundleTree = tempBundleTree.latestBackwardJump()
			// Give it a chance to find an equivalent ancestor of its own.
			tempBundleTree.expand(compilationContext.module)
			// Abort if the bundle trees have diverged.
			if (!tempBundleTree.allParsingPlansInProgress().equals(
					bundleTreeArg.allParsingPlansInProgress()))
			{
				// They've diverged.  Disconnect the backward link.
				bundleTreeArg.isSourceOfCycle(false)
				tempBundleTree = bundleTreeArg
				break
			}
		}
		val bundleTree = tempBundleTree

		var skipCheckArgumentAction = false
		if (firstArgOrNull === null)
		{
			// A call site is only valid if at least one token has been parsed.
			if (consumedAnything)
			{
				val complete = bundleTree.lazyComplete()
				if (complete.setSize() > 0)
				{
					// There are complete messages, we didn't leave a leading
					// argument stranded, and we made progress in the file
					// (i.e., the message contains at least one token).
					assert(marksSoFar.isEmpty())
					assert(argsSoFar.size == 1)
					val args = argsSoFar[0]
					for (bundle in complete)
					{
						if (AvailRuntimeConfiguration.debugCompilerSteps)
						{
							println(
								"Completed send/macro: ${bundle.message()} "
								+ "$args")
						}
						completedSendNode(
							initialTokenPosition,
							start,
							args,
							bundle,
							consumedTokens,
							continuation)
					}
				}
			}
			val incomplete = bundleTree.lazyIncomplete()
			if (incomplete.mapSize() > 0)
			{
				attemptToConsumeToken(
					start,
					initialTokenPosition,
					consumedAnythingBeforeLatestArgument,
					consumedTokens,
					argsSoFar,
					marksSoFar,
					continuation,
					incomplete,
					false)
			}
			val caseInsensitive = bundleTree.lazyIncompleteCaseInsensitive()
			if (caseInsensitive.mapSize() > 0)
			{
				attemptToConsumeToken(
					start,
					initialTokenPosition,
					consumedAnythingBeforeLatestArgument,
					consumedTokens,
					argsSoFar,
					marksSoFar,
					continuation,
					caseInsensitive,
					true)
			}
			val prefilter = bundleTree.lazyPrefilterMap()
			if (prefilter.mapSize() > 0)
			{
				val latestArgument = last(argsSoFar)
				if (latestArgument.isMacroSubstitutionNode || latestArgument
						.isInstanceOfKind(SEND_PHRASE.mostGeneralType()))
				{
					val argumentBundle = latestArgument.apparentSendName().bundleOrNil()
					assert(!argumentBundle.equalsNil())
					if (prefilter.hasKey(argumentBundle))
					{
						val successor = prefilter.mapAt(argumentBundle)
						if (AvailRuntimeConfiguration.debugCompilerSteps)
						{
							println(
								"Grammatical prefilter: $argumentBundle to "
								+ "$successor")
						}
						eventuallyParseRestOfSendNode(
							start,
							successor,
							null,
							initialTokenPosition,
							consumedAnything,
							consumedAnythingBeforeLatestArgument,
							consumedTokens,
							argsSoFar,
							marksSoFar,
							continuation)
						// Don't allow any check-argument actions to be
						// processed normally, as it would ignore the
						// restriction which we've been so careful to prefilter.
						skipCheckArgumentAction = true
					}
					// The argument name was not in the prefilter map, so fall
					// through to allow normal action processing, including the
					// default check-argument action if it's present.
				}
			}
			val typeFilterTreePojo = bundleTree.lazyTypeFilterTreePojo()
			if (!typeFilterTreePojo.equalsNil())
			{
				// Use the most recently pushed phrase's type to look up the
				// successor bundle tree.  This implements aggregated argument
				// type filtering.
				val latestPhrase = last(argsSoFar)
				val typeFilterTree = typeFilterTreePojo.javaObjectNotNull<
					LookupTree<A_Tuple, A_BundleTree>>()
				val timeBefore = captureNanos()
				val successor =
					MessageBundleTreeDescriptor.parserTypeChecker.lookupByValue(
						typeFilterTree,
						latestPhrase,
						bundleTree.latestBackwardJump())
				val timeAfter = captureNanos()
				typeCheckArgumentStat.record(timeAfter - timeBefore)
				if (AvailRuntimeConfiguration.debugCompilerSteps)
				{
					println("Type filter: $latestPhrase -> $successor")
				}
				// Don't complain if at least one plan was happy with the type
				// of the argument.  Otherwise list all argument type/plan
				// expectations as neatly as possible.
				if (successor.allParsingPlansInProgress().mapSize() == 0)
				{
					// Also be silent if no static tokens have been consumed
					// yet.
					if (consumedTokens.isNotEmpty())
					{
						start.expected(MEDIUM) { continueWithDescription ->
							stringifyThen(
								compilationContext.runtime,
								compilationContext.textInterface,
								latestPhrase.expressionType()
							) { actualTypeString ->
								describeFailedTypeTestThen(
									actualTypeString,
									bundleTree,
									continueWithDescription)
							}
						}
					}
				}
				eventuallyParseRestOfSendNode(
					start,
					successor,
					null,
					initialTokenPosition,
					consumedAnything,
					consumedAnythingBeforeLatestArgument,
					consumedTokens,
					argsSoFar,
					marksSoFar,
					continuation)
				// Parse instruction optimization allows there to be some plans
				// that do a type filter here, but some that are able to
				// postpone it.  Therefore, also allow general actions to be
				// collected here by falling through.
			}
		}
		val actions = bundleTree.lazyActions()
		if (actions.mapSize() > 0)
		{
			for (entry in actions.mapIterable())
			{
				val keyInt = entry.key().extractInt()
				val op = decode(keyInt)
				if (skipCheckArgumentAction && op === CHECK_ARGUMENT)
				{
					// Skip this action, because the latest argument was a send
					// that had an entry in the prefilter map, so it has already
					// been dealt with.
					continue
				}
				// Eliminate it before queueing a work unit if it shouldn't run
				// due to there being a first argument already pre-parsed.
				if (firstArgOrNull === null || op.canRunIfHasFirstArgument)
				{
					start.workUnitDo(
						{ value ->
							runParsingInstructionThen(
								start,
								keyInt,
								firstArgOrNull,
								argsSoFar,
								marksSoFar,
								initialTokenPosition,
								consumedAnything,
								consumedAnythingBeforeLatestArgument,
								consumedTokens,
								value,
								continuation)
						},
						entry.value())
				}
			}
		}
	}

	/**
	 * Attempt to consume a token from the source.
	 *
	 * @param start
	 *   Where to start consuming the token.
	 * @param initialTokenPosition
	 *   Where the current potential send phrase started.
	 * @param consumedAnythingBeforeLatestArgument
	 *   Whether any tokens or arguments had been consumed before encountering
	 *   the most recent argument.  This is to improve diagnostics when argument
	 *   type checking is postponed past matches for subsequent tokens.
	 * @param consumedTokens
	 *   An immutable [PrefixSharingList] of [A_Token]s that have been consumed
	 *   so far for the current potential send phrase. The tokens are only those
	 *   that match literal parts of the message name or explicit token
	 *   arguments.
	 * @param argsSoFar
	 *   The argument phrases that have been accumulated so far.
	 * @param marksSoFar
	 *   The mark stack.
	 * @param continuation
	 *   What to do when the current potential send phrase is complete.
	 * @param tokenMap
	 *   A map from string to message bundle tree, used for parsing tokens when
	 *   in this state.
	 * @param caseInsensitive
	 *   Whether to match the token case-insensitively.
	 */
	private fun attemptToConsumeToken(
		start: ParserState,
		initialTokenPosition: ParserState,
		consumedAnythingBeforeLatestArgument: Boolean,
		consumedTokens: List<A_Token>,
		argsSoFar: List<A_Phrase>,
		marksSoFar: List<Int>,
		continuation: Con1,
		tokenMap: A_Map,
		caseInsensitive: Boolean)
	{
		skipWhitespaceAndComments(start) { afterWhiteSpaceStates ->
			for (afterWhiteSpace in afterWhiteSpaceStates)
			{
				afterWhiteSpace.lexingState.withTokensDo { tokens ->
					// At least one of them must be a non-whitespace, but we can
					// completely ignore the whitespaces(/comments).
					var foundOne = false
					var recognized = false
					for (token in tokens)
					{
						val tokenType = token.tokenType()
						if (tokenType == COMMENT || tokenType == WHITESPACE)
						{
							continue
						}
						foundOne = true
						val string =
							if (caseInsensitive) token.lowerCaseString()
							else token.string()
						if (tokenType != KEYWORD && tokenType != OPERATOR)
						{
							continue
						}
						if (!tokenMap.hasKey(string))
						{
							continue
						}
						val timeBefore = captureNanos()
						val successor = tokenMap.mapAt(string)
						if (AvailRuntimeConfiguration.debugCompilerSteps)
						{
							val insensitive =
								if (caseInsensitive) "insensitive token"
								else "token"
							println(
								"Matched $insensitive: $string " +
									"@${token.lineNumber()} for $successor")
						}
						recognized = true
						// Record this token for the call site.
						val afterToken = ParserState(
							token.nextLexingState(), start.clientDataMap)
						eventuallyParseRestOfSendNode(
							afterToken,
							successor,
							null,
							initialTokenPosition,
							true, // Just consumed a token.
							consumedAnythingBeforeLatestArgument,
							append(consumedTokens, token),
							argsSoFar,
							marksSoFar,
							continuation)
						val timeAfter = captureNanos()
						val stat =
							if (caseInsensitive) matchTokenInsensitivelyStat
							else matchTokenStat
						stat.record(timeAfter - timeBefore)
					}
					assert(foundOne)
					// Only report if at least one static token has been
					// consumed.
					if (!recognized && consumedTokens.isNotEmpty())
					{
						val strings = tokens.mapTo(
							mutableSetOf<A_String>(),
							if (caseInsensitive)
							{
								A_Token::lowerCaseString
							}
							else
							{
								A_Token::string
							})
						expectedKeywordsOf(
							start, tokenMap, caseInsensitive, strings)
					}
				}
			}
		}
	}

	/**
	 * Skip whitespace and comments, and evaluate the given function with each
	 * possible successive [A_Token]s.
	 *
	 * @param start
	 *   Where to start scanning.
	 * @param continuation
	 *   What to do with each possible next non-whitespace token.
	 */
	internal fun nextNonwhitespaceTokensDo(
		start: ParserState,
		continuation: (A_Token)->Unit)
	{
		compilationContext.startWorkUnits(1)
		skipWhitespaceAndComments(
			start,
			compilationContext.workUnitCompletion(
				start.lexingState, null
			) { statesAfterWhitespace ->
				for (state in statesAfterWhitespace)
				{
					state.lexingState.withTokensDo { tokens ->
						for (token in tokens)
						{
							val tokenType = token.tokenType()
							if (tokenType != WHITESPACE && tokenType != COMMENT)
							{
								state.workUnitDo(continuation, token)
							}
						}
					}
				}
			})
	}

	/**
	 * A type test for a leaf argument of a potential method or macro invocation
	 * site has failed to produce any viable candidates.  Arrange to have a
	 * suitable diagnostic description of the problem produced, then passed to
	 * the given continuation.  This method may or may not return before the
	 * description has been constructed and passed to the continuation.
	 *
	 * @param actualTypeString
	 *   A [String] describing the actual type of the argument.
	 * @param bundleTree
	 *   The [A_BundleTree] at which parsing was foiled.  There may be multiple
	 *   potential methods and/or macros at this position, none of which will
	 *   have survived the type test.
	 * @param continuation
	 *   What to do once a description of the problem has been produced.
	 */
	private fun describeFailedTypeTestThen(
		actualTypeString: String,
		bundleTree: A_BundleTree,
		continuation: (String)->Unit)
	{
		val typeSet = HashSet<A_Type>()
		val typesByPlanString = HashMap<String, MutableSet<A_Type>>()
		for (entry in bundleTree.allParsingPlansInProgress().mapIterable())
		{
			val submap = entry.value()
			for (subentry in submap.mapIterable())
			{
				for (planInProgress in subentry.value())
				{
					val plan = planInProgress.parsingPlan()
					val instructions = plan.parsingInstructions()
					val instruction =
						instructions.tupleIntAt(planInProgress.parsingPc())
					val typeIndex =
						TYPE_CHECK_ARGUMENT.typeCheckArgumentIndex(instruction)
					// TODO(MvG) Present the full phrase type if it can be a
					// macro argument.
					val argType = constantForIndex(typeIndex).expressionType()
					typeSet.add(argType)
					// Add the type under the given plan *string*, even if it's
					// a different underlying message bundle.
					val typesForPlan = typesByPlanString.computeIfAbsent(
						planInProgress.nameHighlightingPc()
					) { HashSet() }
					typesForPlan.add(argType)
				}
			}
		}
		val typeList = ArrayList(typeSet)
		// Generate the type names in parallel.
		stringifyThen(
			compilationContext.runtime,
			compilationContext.textInterface,
			typeList
		) { typeNamesList ->
			assert(typeList.size == typeNamesList.size)
			val typeMap = IntStream.range(0, typeList.size)
				.boxed()
				.collect(toMap(typeList::get, typeNamesList::get))
			// Stitch the type names back onto the plan strings, prior to
			// sorting by type name.
			val entries: ArrayList<Map.Entry<String, Set<A_Type>>> =
				ArrayList(typesByPlanString.entries)
			entries.sortWith(
				comparing<Map.Entry<String, Set<A_Type>>, String> { it.key })

			val builder = StringBuilder(100)
			builder.append("phrase to have a type other than ")
			builder.append(actualTypeString)
			builder.append(".  Expecting:")
			for (entry in entries)
			{
				val planString = entry.key
				val types = entry.value
				builder.append("\n\t")
				builder.append(planString)
				builder.append("   ")
				val typeNames = types.stream()
					.map { typeMap[it] }
					.sorted()
					.collect(toList<String>())
				var first = true
				for (typeName in typeNames)
				{
					if (!first)
					{
						builder.append(", ")
					}
					first = false
					builder.append(increaseIndentation(typeName, 2))
				}
			}
			continuation(builder.toString())
		}
	}

	/**
	 * Execute one non-keyword-parsing instruction, then run the continuation.
	 *
	 * @param start
	 *   Where to start parsing.
	 * @param instruction
	 *   An int encoding the [parsing][ParsingOperation] to execute.
	 * @param firstArgOrNull
	 *   Either the already-parsed first argument or `null`. If we're looking
	 *   for leading-argument message sends to wrap an expression then this is
	 *   not-`null` before the first argument position is encountered, otherwise
	 *   it's `null` and we should reject attempts to start with an argument
	 *   (before a keyword).
	 * @param argsSoFar
	 *   The message arguments that have been parsed so far.
	 * @param marksSoFar
	 *   The parsing markers that have been recorded so far.
	 * @param initialTokenPosition
	 *   The position at which parsing of this message started. If it was parsed
	 *   as a leading argument send (i.e., firstArgOrNull started out
	 *   non-`null`) then the position is of the token following the first
	 *   argument.
	 * @param consumedAnything
	 *   Whether any tokens or arguments have been consumed yet.
	 * @param consumedAnythingBeforeLatestArgument
	 *   Whether any tokens or arguments had been consumed before encountering
	 *   the most recent argument.  This is to improve diagnostics when argument
	 *   type checking is postponed past matches for subsequent tokens.
	 * @param consumedTokens
	 *   The immutable [List] of "static" [A_Token]s that have been encountered
	 *   and consumed for the current method or macro invocation being parsed.
	 *   These are the tokens that correspond with tokens that occur verbatim
	 *   inside the name of the method or macro.
	 * @param successorTrees
	 *   The [tuple][TupleDescriptor] of [message bundle tree][A_BundleTree] at
	 *   which to continue parsing.
	 * @param continuation
	 *   What to do with a complete [message send phrase][SendPhraseDescriptor].
	 */
	private fun runParsingInstructionThen(
		start: ParserState,
		instruction: Int,
		firstArgOrNull: A_Phrase?,
		argsSoFar: List<A_Phrase>,
		marksSoFar: List<Int>,
		initialTokenPosition: ParserState,
		consumedAnything: Boolean,
		consumedAnythingBeforeLatestArgument: Boolean,
		consumedTokens: List<A_Token>,
		successorTrees: A_Tuple,
		continuation: Con1)
	{
		val op = decode(instruction)
		if (AvailRuntimeConfiguration.debugCompilerSteps)
		{
			if (op.ordinal >= distinctInstructions)
			{
				println(
					"Instr @"
					+ start.shortString()
					+ ": "
					+ op.name
					+ " ("
					+ operand(instruction)
					+ ") -> "
					+ successorTrees)
			}
			else
			{
				println(
					"Instr @"
					+ start.shortString()
					+ ": "
					+ op.name
					+ " -> "
					+ successorTrees)
			}
		}

		val timeBefore = captureNanos()
		op.execute(
			this,
			instruction,
			successorTrees,
			start,
			firstArgOrNull,
			argsSoFar,
			marksSoFar,
			initialTokenPosition,
			consumedAnything,
			consumedAnythingBeforeLatestArgument,
			consumedTokens,
			continuation)
		val timeAfter = captureNanos()
		op.parsingStatisticInNanoseconds.record(timeAfter - timeBefore)
	}

	/**
	 * Attempt the specified prefix function.  It may throw an
	 * [AvailRejectedParseException] if a specific parsing problem needs to be
	 * described.
	 *
	 * @param start
	 *   The [ParserState] at which the prefix function is being run.
	 * @param successorTree
	 *   The [A_BundleTree] with which to continue parsing.
	 * @param prefixFunction
	 *   The prefix [A_Function] to invoke.
	 * @param listOfArgs
	 *   The argument [phrases][A_Phrase] to pass to the prefix function.
	 * @param firstArgOrNull
	 *   The leading argument if it has already been parsed but not consumed.
	 * @param initialTokenPosition
	 *   The [ParserState] at which the current potential macro invocation
	 *   started.
	 * @param consumedAnything
	 *   Whether any tokens have been consumed so far at this macro site.
	 * @param consumedAnythingBeforeLatestArgument
	 *   Whether any tokens or arguments had been consumed before encountering
	 *   the most recent argument.  This is to improve diagnostics when argument
	 *   type checking is postponed past matches for subsequent tokens.
	 * @param consumedTokens
	 *   The list of [A_Token]s that have been consumed so far for this message
	 *   send.
	 * @param argsSoFar
	 *   The stack of phrases.
	 * @param marksSoFar
	 *   The stack of markers that detect epsilon transitions (subexpressions
	 *   consisting of no tokens).
	 * @param continuation
	 *   What should eventually be done with the completed macro invocation,
	 *   should parsing ever get that far.
	 */
	internal fun runPrefixFunctionThen(
		start: ParserState,
		successorTree: A_BundleTree,
		prefixFunction: A_Function,
		listOfArgs: List<AvailObject>,
		firstArgOrNull: A_Phrase?,
		initialTokenPosition: ParserState,
		consumedAnything: Boolean,
		consumedAnythingBeforeLatestArgument: Boolean,
		consumedTokens: List<A_Token>,
		argsSoFar: List<A_Phrase>,
		marksSoFar: List<Int>,
		continuation: Con1)
	{
		if (!prefixFunction.kind().acceptsListOfArgValues(listOfArgs))
		{
			return
		}
		val fiber = newLoaderFiber(
			prefixFunction.kind().returnType(),
			compilationContext.loader!!
		) {
			val code = prefixFunction.code()
			formatString(
				"Macro prefix %s, in %s:%d",
				code.methodName(),
				code.module().moduleName(),
				code.startingLineNumber())
		}
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE)
		val withTokens = start.clientDataMap
			.mapAtPuttingCanDestroy(
				ALL_TOKENS_KEY.atom,
				tupleFromList(start.lexingState.allTokens),
				false)
			.mapAtPuttingCanDestroy(
				STATIC_TOKENS_KEY.atom, tupleFromList(consumedTokens), false)
		var fiberGlobals = fiber.fiberGlobals()
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			CLIENT_DATA_GLOBAL_KEY.atom, withTokens.makeImmutable(), true)
		fiber.fiberGlobals(fiberGlobals)
		fiber.textInterface(compilationContext.textInterface)
		start.lexingState.setFiberContinuationsTrackingWork(
			fiber,
			{
				// The prefix function ran successfully.
				val replacementClientDataMap =
					fiber.fiberGlobals().mapAt(CLIENT_DATA_GLOBAL_KEY.atom)
				eventuallyParseRestOfSendNode(
					start.withMap(replacementClientDataMap),
					successorTree,
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedAnythingBeforeLatestArgument,
					consumedTokens,
					argsSoFar,
					marksSoFar,
					continuation)
			},
			{ e ->
				// The prefix function failed in some way.
				if (e is AvailAcceptedParseException)
				{
					// Prefix functions are allowed to explicitly accept a
					// parse.
					val replacementClientDataMap =
						fiber.fiberGlobals().mapAt(CLIENT_DATA_GLOBAL_KEY.atom)
					eventuallyParseRestOfSendNode(
						start.withMap(replacementClientDataMap),
						successorTree,
						firstArgOrNull,
						initialTokenPosition,
						consumedAnything,
						consumedAnythingBeforeLatestArgument,
						consumedTokens,
						argsSoFar,
						marksSoFar,
						continuation)
				}
				if (e is AvailRejectedParseException)
				{
					start.expected(
						e.level,
						e.rejectionString.asNativeString())
				}
				else
				{
					start.expected(
						STRONG,
						FormattingDescriber(
							"prefix function not to have failed with:\n%s", e))
				}
			})
		runOutermostFunction(
			compilationContext.runtime, fiber, prefixFunction, listOfArgs)
	}

	/**
	 * Check the proposed message send for validity. Use not only the applicable
	 * [method definitions][MethodDefinitionDescriptor], but also any semantic
	 * restrictions. The semantic restrictions may choose to [reject the
	 * parse][P_RejectParsing], indicating that the argument types are mutually
	 * incompatible.  If all semantic restrictions succeed, invoke onSuccess
	 * with the intersection of the produced types and the applicable method
	 * body return types.
	 *
	 * @param bundle
	 *   A [message bundle][MessageBundleDescriptor].
	 * @param argTypes
	 *   The argument types.
	 * @param state
	 *   The [parser state][ParserState] after the function evaluates
	 *   successfully.
	 * @param macroOrNil
	 *   A [macro definition][MacroDefinitionDescriptor] if this is for a macro
	 *   invocation, otherwise `nil`.
	 * @param onSuccess
	 *   What to do with the strengthened return type.  This may be invoked at
	 *   most once, and only if no semantic restriction rejected the parse.
	 */
	private fun validateArgumentTypes(
		bundle: A_Bundle,
		argTypes: List<A_Type>,
		macroOrNil: A_Definition,
		state: ParserState,
		onSuccess: (A_Type)->Unit)
	{
		val method = bundle.bundleMethod()
		val methodDefinitions = method.definitionsTuple()
		val restrictions = method.semanticRestrictions()
		// Filter the definitions down to those that are locally most specific.
		// Fail if more than one survives.
		if (methodDefinitions.tupleSize() > 0)
		{
			// There are method definitions.
			// Compiler should have assured there were no bottom or
			// top argument expressions.
			argTypes.forEach { assert(!it.isBottom && !it.isTop) }
		}
		// Find all method definitions that could match the argument types.
		// Only consider definitions that are defined in the current module or
		// an ancestor.
		val allAncestors = compilationContext.module.allAncestors()
		val filteredByTypes =
			if (macroOrNil.equalsNil())
				method.filterByTypes(argTypes)
			else
				listOf(macroOrNil)
		val satisfyingDefinitions = ArrayList<A_Definition>()
		for (definition in filteredByTypes)
		{
			val definitionModule = definition.definitionModule()
			if (definitionModule.equalsNil()
				|| allAncestors.hasElement(definitionModule))
			{
				satisfyingDefinitions.add(definition)
			}
		}
		if (satisfyingDefinitions.isEmpty())
		{
			state.expected(
				STRONG,
				describeWhyDefinitionsAreInapplicable(
					bundle,
					argTypes,
					if (macroOrNil.equalsNil()) methodDefinitions
					else tuple(macroOrNil),
					allAncestors))
			return
		}
		// Compute the intersection of the return types of the possible callees.
		// Macro bodies return phrases, but that's not what we want here.
		val intersection: Mutable<A_Type>
		if (macroOrNil.equalsNil())
		{
			intersection = Mutable(
				satisfyingDefinitions[0].bodySignature().returnType())
			var i = 1
			val end = satisfyingDefinitions.size
			while (i < end)
			{
				intersection.value = intersection.value.typeIntersection(
					satisfyingDefinitions[i].bodySignature().returnType())
				i++
			}
		}
		else
		{
			// The macro's semantic type (expressionType) is the authoritative
			// type to check against the macro body's actual return phrase's
			// semantic type.  Semantic restrictions may still narrow it below.
			intersection = Mutable(
				macroOrNil.bodySignature().returnType().expressionType())
		}
		// Determine which semantic restrictions are relevant.
		val restrictionsToTry =
			ArrayList<A_SemanticRestriction>(restrictions.setSize())
		for (restriction in restrictions)
		{
			val definitionModule = restriction.definitionModule()
			if (definitionModule.equalsNil()
				|| allAncestors.hasElement(restriction.definitionModule()))
			{
				if (restriction.function().kind().acceptsListOfArgValues(
						argTypes))
				{
					restrictionsToTry.add(restriction)
				}
			}
		}
		// If there are no relevant semantic restrictions, then immediately
		// invoke the success continuation and exit.
		if (restrictionsToTry.isEmpty())
		{
			onSuccess(intersection.value)
			return
		}
		// Run all relevant semantic restrictions, in parallel, computing the
		// type intersection of their results.
		val outstanding = AtomicInteger(restrictionsToTry.size)
		val failureCount = AtomicInteger(0)
		val outstandingLock = ReentrantReadWriteLock()
		// This runs when the last applicable semantic restriction finishes.
		val whenDone = {
			assert(outstanding.get() == 0)
			if (failureCount.get() == 0)
			{
				// No failures occurred.  Invoke success.
				onSuccess(intersection.value)
			}
		}
		val intersectAndDecrement = { restrictionType: AvailObject ->
			assert(restrictionType.isType)
			lockWhile(outstandingLock.writeLock()) {
				if (failureCount.get() == 0)
				{
					intersection.value = intersection.value.typeIntersection(
						restrictionType)
				}
			}
			if (outstanding.decrementAndGet() == 0)
			{
				whenDone()
			}
		}
		// Launch the semantic restrictions in parallel.
		for (restriction in restrictionsToTry)
		{
			evaluateSemanticRestrictionFunctionThen(
				restriction,
				argTypes,
				state.lexingState,
				intersectAndDecrement,
				{ e ->
					if (e is AvailAcceptedParseException)
					{
						// This is really a success.
						intersectAndDecrement(TOP.o())
						return@evaluateSemanticRestrictionFunctionThen
					}
					when (e)
					{
						is AvailRejectedParseException -> state.expected(
							e.level,
							e.rejectionString.asNativeString()
								+ " (while parsing send of "
								+ bundle.message()
									.atomName().asNativeString()
								+ ')'.toString())
						is FiberTerminationException -> state.expected(
							STRONG,
							"semantic restriction not to raise an "
								+ "unhandled exception (while parsing "
								+ "send of "
								+ bundle.message().atomName().asNativeString()
								+ "):\n\t"
								+ e)
						is AvailAssertionFailedException -> state.expected(
							STRONG,
							"assertion not to have failed "
								+ "(while parsing send of "
								+ bundle.message().atomName().asNativeString()
								+ "):\n\t"
								+ e.assertionString.asNativeString())
						else -> state.expected(
							STRONG,
							FormattingDescriber(
								"unexpected error: %s", e))
					}
					failureCount.incrementAndGet()
					if (outstanding.decrementAndGet() == 0)
					{
						whenDone()
					}
				})
		}
	}

	/**
	 * Given a collection of definitions, whether for methods or for macros, but
	 * not both, and given argument types (phrase types in the case of macros)
	 * for a call site, produce a reasonable explanation of why the definitions
	 * were all rejected.
	 *
	 * @param bundle
	 *   The target bundle for the call site.
	 * @param argTypes
	 *   The types of the arguments, or their phrase types if this is for a
	 *   macro lookup.
	 * @param definitionsTuple
	 *   The method or macro (but not both) definitions that were visible
	 *   (defined in the current or an ancestor module) but not applicable.
	 * @param allAncestorModules
	 *   The [set][A_Set] containing the current [module][A_Module] and its
	 *   ancestors.
	 * @return
	 *   A [Describer] able to describe why none of the definitions were
	 *   applicable.
	 */
	private fun describeWhyDefinitionsAreInapplicable(
		bundle: A_Bundle,
		argTypes: List<A_Type>,
		definitionsTuple: A_Tuple,
		allAncestorModules: A_Set): Describer
	{
		assert(definitionsTuple.tupleSize() > 0)
		return { c ->
			val kindOfDefinition =
				if (definitionsTuple.tupleAt(1).isMacroDefinition)
					"macro"
				else
					"method"
			val allVisible = ArrayList<A_Definition>()
			for (def in definitionsTuple)
			{
				val definingModule = def.definitionModule()
				if (definingModule.equalsNil()
					|| allAncestorModules.hasElement(def.definitionModule()))
				{
					allVisible.add(def)
				}
			}
			val allFailedIndices = ArrayList<Int>(3)
			run {
				var i = 1
				val end = argTypes.size
				each_arg@ while (i <= end)
				{
					for (definition in allVisible)
					{
						val sig = definition.bodySignature()
						if (argTypes[i - 1].isSubtypeOf(
								sig.argsTupleType().typeAtIndex(i)))
						{
							i++
							continue@each_arg
						}
					}
					allFailedIndices.add(i)
					i++
				}
			}
			if (allFailedIndices.size == 0)
			{
				// Each argument applied to at least one definition, so put
				// the blame on them all instead of none.
				var i = 1
				val end = argTypes.size
				while (i <= end)
				{
					allFailedIndices.add(i)
					i++
				}
			}
			// Don't stringify all the argument types, just the failed ones. And
			// don't stringify the same value twice. Obviously side effects in
			// stringifiers won't work right here…
			val uniqueValues = ArrayList<A_BasicObject>()
			val valuesToStringify = HashMap<A_BasicObject, Int>()
			for (i in allFailedIndices)
			{
				val argType = argTypes[i - 1]
				if (!valuesToStringify.containsKey(argType))
				{
					valuesToStringify[argType] = uniqueValues.size
					uniqueValues.add(argType)
				}
				for (definition in allVisible)
				{
					val signatureArgumentsType =
						definition.bodySignature().argsTupleType()
					val sigType = signatureArgumentsType.typeAtIndex(i)
					if (!valuesToStringify.containsKey(sigType))
					{
						valuesToStringify[sigType] = uniqueValues.size
						uniqueValues.add(sigType)
					}
				}
			}
			stringifyThen(
				compilationContext.runtime,
				compilationContext.textInterface,
				uniqueValues
			) { strings ->
				val builder = Formatter()
				builder.format(
					"arguments at indices %s of message %s to "
					+ "match a visible %s definition:%n",
					allFailedIndices,
					bundle.message().atomName(),
					kindOfDefinition)
				builder.format("\tI got:%n")
				for (i in allFailedIndices)
				{
					val argType = argTypes[i - 1]
					val s = strings[valuesToStringify[argType]!!]
					builder.format("\t\t#%d = %s%n", i, s)
				}
				builder.format(
					"\tI expected%s:",
					if (allVisible.size > 1) " one of" else "")
				for (definition in allVisible)
				{
					builder.format(
						"%n\t\tFrom module %s @ line #%s,",
						definition.definitionModuleName(),
						if (definition.isMethodDefinition)
							definition.bodyBlock().code()
								.startingLineNumber()
						else
							"unknown")
					val signatureArgumentsType =
						definition.bodySignature().argsTupleType()
					for (i in allFailedIndices)
					{
						val sigType = signatureArgumentsType.typeAtIndex(i)
						val s = strings[valuesToStringify[sigType]!!]
						builder.format("%n\t\t\t#%d = %s", i, s)
					}
				}
				if (allVisible.isEmpty())
				{
					c(
						"[[[Internal problem - No visible implementations;"
						+ " should have been excluded.]]]\n"
						+ builder)
				}
				else
				{
					c(builder.toString())
				}
			}
		}
	}

	/**
	 * A complete [send phrase][SendPhraseDescriptor] has been parsed.
	 * Create the send phrase and invoke the continuation.
	 *
	 * If this is a macro, invoke the body immediately with the argument
	 * expressions to produce a phrase.
	 *
	 * @param stateBeforeCall
	 *   The initial parsing state, prior to parsing the entire message.
	 * @param stateAfterCall
	 *   The parsing state after the message.
	 * @param argumentsListNode
	 *   The [list phrase][ListPhraseDescriptor] that will hold all the
	 *   arguments of the new send phrase.
	 * @param bundle
	 *   The [message bundle][MessageBundleDescriptor] that identifies the
	 *   message to be sent.
	 * @param consumedTokens
	 *   The list of all tokens collected for this send phrase.  This includes
	 *   only those tokens that are operator or keyword tokens that correspond
	 *   with parts of the method name itself, not the arguments.
	 * @param continuation
	 *   What to do with the resulting send phrase.
	 */
	private fun completedSendNode(
		stateBeforeCall: ParserState,
		stateAfterCall: ParserState,
		argumentsListNode: A_Phrase,
		bundle: A_Bundle,
		consumedTokens: List<A_Token>,
		continuation: Con1)
	{
		val method = bundle.bundleMethod()
		val macroDefinitionsTuple = method.macroDefinitionsTuple()
		val definitionsTuple = method.definitionsTuple()
		if (definitionsTuple.tupleSize() + macroDefinitionsTuple.tupleSize()
			== 0)
		{
			stateAfterCall.expected(
				STRONG,
				"there to be a method or macro definition for "
				+ bundle.message()
				+ ", but there wasn't")
			return
		}

		// An applicable macro definition (even if ambiguous) prevents this site
		// from being a method invocation.
		var macro: A_Definition = nil
		if (macroDefinitionsTuple.tupleSize() > 0)
		{
			// Find all macro definitions that could match the argument phrases.
			// Only consider definitions that are defined in the current module
			// or an ancestor.
			val allAncestors = compilationContext.module.allAncestors()
			val visibleDefinitions =
				ArrayList<A_Definition>(macroDefinitionsTuple.tupleSize())
			for (definition in macroDefinitionsTuple)
			{
				val definitionModule = definition.definitionModule()
				if (definitionModule.equalsNil()
					|| allAncestors.hasElement(definitionModule))
				{
					visibleDefinitions.add(definition)
				}
			}
			var errorCode: AvailErrorCode? = null
			if (visibleDefinitions.size == macroDefinitionsTuple.tupleSize())
			{
				// All macro definitions are visible.  Use the lookup tree.
				val matchingMacros = method.lookupMacroByPhraseTuple(
					argumentsListNode.expressionsTuple())
				when (matchingMacros.tupleSize())
				{
					0 ->
					{
						errorCode = E_NO_METHOD_DEFINITION
					}
					1 ->
					{
						macro = matchingMacros.tupleAt(1)
					}
					else ->
					{
						errorCode = E_AMBIGUOUS_METHOD_DEFINITION
					}
				}
			}
			else
			{
				// Some of the macro definitions are not visible.  Search the
				// hard (but hopefully infrequent) way.
				val phraseRestrictions =
					ArrayList<TypeRestriction>(method.numArgs())
				for (argPhrase in argumentsListNode.expressionsTuple())
				{
					phraseRestrictions.add(
						restrictionForConstant(argPhrase, BOXED))
				}
				val filtered = ArrayList<A_Definition>()
				for (macroDefinition in visibleDefinitions)
				{
					if (macroDefinition.bodySignature().couldEverBeInvokedWith(
							phraseRestrictions))
					{
						filtered.add(macroDefinition)
					}
				}

				when (filtered.size)
				{
					0 ->
					{
						// Nothing is visible.
						stateAfterCall.expected(
							WEAK,
							"perhaps some definition of the macro "
								+ bundle.message()
								+ " to be visible")
						errorCode = E_NO_METHOD_DEFINITION
						// Fall through.
					}
					1 -> macro = filtered[0]
					else ->
					{
						// Find the most specific macro(s).
						// assert filtered.size() > 1;
						val mostSpecific = ArrayList<A_Definition>()
						for (candidate in filtered)
						{
							var isMostSpecific = true
							for (other in filtered)
							{
								if (!candidate.equals(other))
								{
									if (candidate.bodySignature()
											.acceptsArgTypesFromFunctionType(
												other.bodySignature()))
									{
										isMostSpecific = false
										break
									}
								}
							}
							if (isMostSpecific)
							{
								mostSpecific.add(candidate)
							}
						}
						assert(mostSpecific.size >= 1)
						if (mostSpecific.size == 1)
						{
							// There is one most-specific macro.
							macro = mostSpecific[0]
						}
						else
						{
							// There are multiple most-specific macros.
							errorCode = E_AMBIGUOUS_METHOD_DEFINITION
						}
					}
				}
			}

			if (macro.equalsNil())
			{
				// Failed lookup.
				if (errorCode !== E_NO_METHOD_DEFINITION)
				{
					val finalErrorCode = errorCode!!
					stateAfterCall.expected(MEDIUM) {
						it(
							if (finalErrorCode === E_AMBIGUOUS_METHOD_DEFINITION)
								"unambiguous definition of macro " +
									bundle.message()
							else
								"successful macro lookup, not: " +
									finalErrorCode.name)
					}
					// Don't try to treat it as a method invocation.
					return
				}
				if (definitionsTuple.tupleSize() == 0)
				{
					// There are only macro definitions, but the arguments were
					// not the right types.
					val phraseTypes = ArrayList<A_Type>(method.numArgs())
					for (argPhrase in argumentsListNode.expressionsTuple())
					{
						phraseTypes.add(instanceTypeOrMetaOn(argPhrase))
					}
					stateAfterCall.expected(
						MEDIUM,
						describeWhyDefinitionsAreInapplicable(
							bundle,
							phraseTypes,
							macroDefinitionsTuple,
							allAncestors))
					// Don't report it as a failed method lookup, since there
					// were none.
					return
				}
				// No macro definition matched, and there are method definitions
				// also possible, so fall through and treat it as a potential
				// method invocation site instead.
			}
			// Fall through to test semantic restrictions and run the macro if
			// one was found.
		}
		// It invokes a method (not a macro).  We compute the union of the
		// superUnionType() and the expressionType() for lookup, since if this
		// is a supercall we want to know what semantic restrictions and
		// function return types will be reached by the method definition(s)
		// actually being invoked.
		val argTupleType = argumentsListNode.superUnionType().typeUnion(
			argumentsListNode.expressionType())
		val argCount = argumentsListNode.expressionsSize()
		val argTypes = ArrayList<A_Type>(argCount)
		for (i in 1 .. argCount)
		{
			argTypes.add(argTupleType.typeAtIndex(i))
		}
		// Parsing a macro send must not affect the scope.
		val afterState = stateAfterCall.withMap(stateBeforeCall.clientDataMap)
		val finalMacro = macro
		// Validate the message send before reifying a send phrase.
		validateArgumentTypes(
			bundle,
			argTypes,
			finalMacro,
			stateAfterCall
		) { expectedYieldType ->
			if (finalMacro.equalsNil())
			{
				val sendNode = newSendNode(
					tupleFromList(consumedTokens),
					bundle,
					argumentsListNode,
					expectedYieldType)
				afterState.workUnitDo(
					continuation,
					CompilerSolution(afterState, sendNode))
				return@validateArgumentTypes
			}
			completedSendNodeForMacro(
				stateAfterCall,
				argumentsListNode,
				bundle,
				consumedTokens,
				finalMacro,
				expectedYieldType,
				Con1(continuation.superexpressions) { macroSolution ->
					assert(macroSolution.phrase.isMacroSubstitutionNode)
					continuation(macroSolution)
				})
		}
	}

	/**
	 * Parse an argument to a message send. Backtracking will find all valid
	 * interpretations.
	 *
	 * @param start
	 *   Where to start parsing.
	 * @param kindOfArgument
	 *   A [String], in the form of a noun phrase, saying the kind of argument
	 *   that is expected.
	 * @param firstArgOrNull
	 *   Either a phrase to use as the argument, or `null` if we should parse
	 *   one now.
	 * @param canReallyParse
	 *   Whether any tokens may be consumed.  This should be `false`
	 *   specifically when the leftmost argument of a leading-argument message
	 *   is being parsed.
	 * @param wrapInLiteral
	 *   Whether the argument should be wrapped inside a literal phrase. This
	 *   allows statements to be more easily processed by macros.
	 * @param continuation
	 *   What to do with the argument.
	 */
	internal fun parseSendArgumentWithExplanationThen(
		start: ParserState,
		kindOfArgument: String,
		firstArgOrNull: A_Phrase?,
		canReallyParse: Boolean,
		wrapInLiteral: Boolean,
		continuation: Con1)
	{
		if (firstArgOrNull !== null)
		{
			// We're parsing a message send with a leading argument, and that
			// argument was explicitly provided to the parser.  We should
			// consume the provided first argument now.
			assert(!canReallyParse)

			// wrapInLiteral allows us to accept anything, even expressions that
			// are ⊤- or ⊥-valued.
			if (wrapInLiteral)
			{
				start.workUnitDo(
					continuation,
					CompilerSolution(start, wrapAsLiteral(firstArgOrNull)))
				return
			}
			val expressionType = firstArgOrNull.expressionType()
			if (expressionType.isTop)
			{
				start.expected(
					WEAK, "leading argument not to be ⊤-valued.")
				return
			}
			if (expressionType.isBottom)
			{
				start.expected(
					WEAK, "leading argument not to be ⊥-valued.")
				return
			}
			start.workUnitDo(
				continuation, CompilerSolution(start, firstArgOrNull))
			return
		}
		// There was no leading argument, or it has already been accounted for.
		// If we haven't actually consumed anything yet then don't allow a
		// *leading* argument to be parsed here.  That would lead to ambiguous
		// left-recursive parsing.
		if (!canReallyParse)
		{
			return
		}
		parseExpressionThen(
			start,
			Con1(continuation.superexpressions) { solution ->
				// Only accept a ⊤-valued or ⊥-valued expression if
				// wrapInLiteral is true.
				val argument = solution.phrase
				val afterArgument = solution.endState
				if (!wrapInLiteral)
				{
					val type = argument.expressionType()
					val badTypeName =
						when
						{
							type.isTop -> "⊤"
							type.isBottom -> "⊥"
							else -> null
						}
					if (badTypeName !== null)
					{
						afterArgument.expected(WEAK) {
							it(
								buildString {
									append(kindOfArgument)
									append(" to have a type other than ")
									append(badTypeName)
									append(" in:")
									describeOn(
										continuation.superexpressions, this)
								})
						}
						return@Con1
					}
				}
				val argument1 = CompilerSolution(
					afterArgument,
					if (wrapInLiteral) wrapAsLiteral(argument) else argument)
				afterArgument.workUnitDo(continuation, argument1)
			})
	}

	/**
	 * Parse an argument in the top-most scope.  This is an important capability
	 * for parsing type expressions, and the macro facility may make good use of
	 * it for other purposes.
	 *
	 * @param start
	 *   The position at which parsing should occur.
	 * @param initialTokenPosition
	 *   The parse position where the send phrase started to be processed. Does
	 *   not count the position of the first argument if there are no leading
	 *   keywords.
	 * @param firstArgOrNull
	 *   An optional already parsed expression which, if present, must be used
	 *   as a leading argument.  If it's `null` then no leading argument has
	 *   been parsed, and a request to parse a leading argument should simply
	 *   produce no local solution.
	 * @param consumedAnything
	 *   Whether anything has yet been consumed for this invocation.
	 * @param consumedTokens
	 *   The tokens that have been consumed for this invocation.
	 * @param argsSoFar
	 *   The list of arguments parsed so far. I do not modify it. This is a
	 *   stack of expressions that the parsing instructions will assemble into a
	 *   list that correlates with the top-level non-backquoted underscores and
	 *   guillemet groups in the message name.
	 * @param marksSoFar
	 *   The stack of mark positions used to test if parsing certain
	 *   subexpressions makes progress.
	 * @param successorTrees
	 *   A [tuple][TupleDescriptor] of [message bundle trees] along which to
	 *   continue parsing if a local solution is found.
	 * @param continuation
	 *   What to do once we have a fully parsed send phrase (of which we are
	 *   currently parsing an argument).
	 */
	internal fun parseArgumentInModuleScopeThen(
		start: ParserState,
		initialTokenPosition: ParserState,
		firstArgOrNull: A_Phrase?,
		consumedAnything: Boolean,
		consumedTokens: List<A_Token>,
		argsSoFar: List<A_Phrase>,
		marksSoFar: List<Int>,
		successorTrees: A_Tuple,
		continuation: Con1)
	{
		// Parse an argument in the outermost (module) scope and continue.
		assert(successorTrees.tupleSize() == 1)
		val clientDataInGlobalScope =
			start.clientDataMap.mapAtPuttingCanDestroy(
				COMPILER_SCOPE_MAP_KEY.atom,
				emptyMap(),
				false)
		parseSendArgumentWithExplanationThen(
			start.withMap(clientDataInGlobalScope),
			"module-scoped argument",
			firstArgOrNull,
			firstArgOrNull === null
				&& initialTokenPosition.lexingState != start.lexingState,
			false, // Static argument can't be top-valued
			Con1(continuation.superexpressions) { solution ->
				val newArg = solution.phrase
				val afterArg = solution.endState
				if (newArg.hasSuperCast())
				{
					afterArg.expected(
						STRONG,
						"global-scoped argument, not supercast")
					return@Con1
				}

				if (firstArgOrNull !== null)
				{
					// A leading argument was already supplied.  We couldn't
					// prevent it from referring to variables that were in scope
					// during its parsing, but we can reject it if the leading
					// argument is supposed to be parsed in global scope, which
					// is the case here, and there are references to local
					// variables within the argument's parse tree.
					val usedLocals = usesWhichLocalVariables(newArg)
					if (usedLocals.setSize() > 0)
					{
						// A leading argument was supplied which used at least
						// one local.  It shouldn't have.
						afterArg.expected(WEAK) {
							val localNames = ArrayList<String>()
							for (usedLocal in usedLocals)
							{
								val name = usedLocal.token().string()
								localNames.add(name.asNativeString())
							}
							it(
								"a leading argument which "
									+ "was supposed to be parsed in "
									+ "module scope, but it referred to "
									+ "some local variables: "
									+ localNames)
						}
						return@Con1
					}
				}
				val newArgsSoFar = append(argsSoFar, newArg)
				eventuallyParseRestOfSendNode(
					afterArg.withMap(start.clientDataMap),
					successorTrees.tupleAt(1),
					null,
					initialTokenPosition,
					// The argument counts as something that was consumed if
					// it's not a leading argument...
					firstArgOrNull === null,
					// We're about to parse an argument, so whatever was in
					// consumedAnything should be moved into
					// consumedAnythingBeforeLatestArgument.
					consumedAnything,
					consumedTokens,
					newArgsSoFar,
					marksSoFar,
					continuation)
			})
	}

	/**
	 * A macro invocation has just been parsed.  Run its body now to produce a
	 * substitute phrase.
	 *
	 * @param stateAfterCall
	 *   The parsing state after the message.
	 * @param argumentsListNode
	 *   The [list phrase][ListPhraseDescriptor] that will hold all the
	 *   arguments of the new send phrase.
	 * @param bundle
	 *   The [message bundle][MessageBundleDescriptor] that identifies the
	 *   message to be sent.
	 * @param consumedTokens
	 *   The list of all tokens collected for this send phrase.  This includes
	 *   only those tokens that are operator or keyword tokens that correspond
	 *   with parts of the method name itself, not the arguments.
	 * @param macroDefinitionToInvoke
	 *   The actual [macro definition][MacroDefinitionDescriptor] to invoke
	 *   (statically).
	 * @param expectedYieldType
	 *   What semantic type the expression returned from the macro invocation is
	 *   expected to yield.  This will be narrowed further by the actual phrase
	 *   returned by the macro body, although if it's not a send phrase then the
	 *   resulting phrase is *checked* against this expected yield type instead.
	 * @param continuation
	 *   What to do with the resulting send phrase solution.
	 */
	private fun completedSendNodeForMacro(
		stateAfterCall: ParserState,
		argumentsListNode: A_Phrase,
		bundle: A_Bundle,
		consumedTokens: List<A_Token>,
		macroDefinitionToInvoke: A_Definition,
		expectedYieldType: A_Type,
		continuation: Con1)
	{
		val argumentsTuple = argumentsListNode.expressionsTuple()
		val argCount = argumentsTuple.tupleSize()
		// Strip off macro substitution wrappers from the arguments.  These were
		// preserved only long enough to test grammatical restrictions.
		val argumentsList = ArrayList<A_Phrase>(argCount)
		for (argument in argumentsTuple)
		{
			argumentsList.add(argument)
		}
		// Capture all of the tokens that comprised the entire macro send.
		val withTokensAndBundle = stateAfterCall.clientDataMap
			.mapAtPuttingCanDestroy(
				STATIC_TOKENS_KEY.atom, tupleFromList(consumedTokens), false)
			.mapAtPuttingCanDestroy(
				ALL_TOKENS_KEY.atom,
				tupleFromList(stateAfterCall.lexingState.allTokens),
				false)
			.mapAtPuttingCanDestroy(MACRO_BUNDLE_KEY.atom, bundle, true)
			.makeShared()
		if (AvailRuntimeConfiguration.debugMacroExpansions)
		{
			println(
				"PRE-EVAL:"
				+ stateAfterCall.lineNumber
				+ '('.toString()
				+ stateAfterCall.position
				+ ") "
				+ macroDefinitionToInvoke
				+ ' '.toString()
				+ argumentsList)
		}
		val clientDataAfterRunning = MutableOrNull<A_Map>()
		evaluateMacroFunctionThen(
			macroDefinitionToInvoke,
			argumentsList,
			withTokensAndBundle,
			clientDataAfterRunning,
			stateAfterCall.lexingState,
			{ replacement ->
				assert(clientDataAfterRunning.value !== null)
				// In theory a fiber can produce anything, although you have to
				// mess with continuations to get it wrong.
				if (!replacement.isInstanceOfKind(
						PARSE_PHRASE.mostGeneralType()))
				{
					stateAfterCall.expected(
						STRONG,
						listOf(replacement)
					) { list ->
						format(
							"Macro body for %s to have "
								+ "produced a phrase, not %s",
							bundle.message(),
							list[0])
					}
					return@evaluateMacroFunctionThen
				}
				val adjustedReplacement: A_Phrase
				when
				{
					// Strengthen the send phrase produced by the macro.
					replacement.phraseKindIsUnder(SEND_PHRASE) ->
						adjustedReplacement = newSendNode(
							replacement.tokens(),
							replacement.bundle(),
							replacement.argumentsListNode(),
							replacement.expressionType().typeIntersection(
								expectedYieldType))
					// No adjustment necessary.
					replacement.expressionType().isSubtypeOf(
						expectedYieldType) ->
						adjustedReplacement = replacement
					// Not a send phrase, so it's impossible to strengthen it to
					// what the semantic restrictions promised it should be.
					else ->
					{
						stateAfterCall.expected(
							STRONG,
							"macro "
								+ bundle.message().atomName()
								+ " to produce either a send phrase to "
								+ "be strengthened, or a phrase that "
								+ "yields "
								+ expectedYieldType
								+ ", not "
								+ replacement)
						return@evaluateMacroFunctionThen
					}
				}
				// Continue after this macro invocation with whatever client
				// data was set up by the macro.
				val stateAfter = stateAfterCall.withMap(
					clientDataAfterRunning.value())
				val original = newSendNode(
					tupleFromList(consumedTokens),
					bundle,
					argumentsListNode,
					macroDefinitionToInvoke.bodySignature().returnType())
				val substitution =
					newMacroSubstitution(original, adjustedReplacement)
				if (AvailRuntimeConfiguration.debugMacroExpansions)
				{
					println(
						":"
						+ stateAfter.lineNumber
						+ '('.toString()
						+ stateAfter.position
						+ ") "
						+ substitution)
				}
				stateAfter.workUnitDo(
					continuation,
					CompilerSolution(stateAfter, substitution))
			},
			{ e ->
				when (e)
				{
					is AvailAcceptedParseException -> stateAfterCall.expected(
						STRONG,
						"macro body to reject the parse or produce "
							+ "a replacement expression, not merely "
							+ "accept its phrases like a semantic "
							+ "restriction")
					is AvailRejectedParseException ->
					{
						stateAfterCall.expected(
							e.level,
							e.rejectionString.asNativeString())
					}
					else -> stateAfterCall.expected(
						STRONG,
						"evaluation of macro body not to raise an "
							+ "unhandled exception:\n\t"
							+ e)
				}
			})
	}

	/**
	 * Check a property of the Avail virtual machine.
	 *
	 * @param state
	 *   The [ParserState] at which the pragma was found.
	 * @param propertyName
	 *   The name of the property that is being checked.
	 * @param propertyValue
	 *   A value that should be checked, somehow, for conformance.
	 * @param success
	 *   What to do after the check completes successfully.
	 */
	internal fun pragmaCheckThen(
		state: ParserState,
		propertyName: String,
		propertyValue: String,
		success: ()->Unit)
	{
		if ("version" == propertyName)
		{
			// Split the versions at commas.
			val versions = propertyValue.split(",").toTypedArray()
			for (i in versions.indices)
			{
				versions[i] = versions[i].trim { it <= ' ' }
			}
			// Put the required versions into a set.
			val requiredVersions = generateSetFrom(versions, ::stringFrom)
			// Ask for the guaranteed versions.
			val activeVersions = AvailRuntimeConfiguration.activeVersions()
			// If the intersection of the sets is empty, then the module and
			// the virtual machine are incompatible.
			if (!requiredVersions.setIntersects(activeVersions))
			{
				state.expected(
					STRONG,
					format(
						"Module and virtual machine are not compatible; "
						+ "the virtual machine guarantees versions %s, "
						+ "but the current module requires %s",
						activeVersions,
						requiredVersions))
				return
			}
		}
		else
		{
			val viableAssertions = HashSet<String>()
			viableAssertions.add("version")
			state.expected(
				STRONG,
				format(
					"Expected check pragma to assert one of the following "
					+ "properties: %s",
					viableAssertions))
			return
		}
		success()
	}

	/**
	 * Create a bootstrap primitive method. Use the primitive's type declaration
	 * as the argument types.  If the primitive is fallible then generate
	 * suitable primitive failure code (to invoke the [CRASH]
	 * [method][MethodDescriptor]'s [bundle][A_BundleTree]).
	 *
	 * @param state
	 *   The [state][ParserState] following a parse of the [module
	 *   header][ModuleHeader].
	 * @param token
	 *   A token with which to associate the definition of the function. Since
	 *   this is a bootstrap method, it's appropriate to use the string token
	 *   within the pragma for this purpose.
	 * @param methodName
	 *   The name of the primitive method being defined.
	 * @param primitiveName
	 *   The [primitive name][Primitive.name] of the [method][MethodDescriptor]
	 *   being defined.
	 * @param success
	 *   What to do after the method is bootstrapped successfully.
	 */
	internal fun bootstrapMethodThen(
		state: ParserState,
		token: A_Token,
		methodName: String,
		primitiveName: String,
		success: ()->Unit)
	{
		val availName = stringFrom(methodName)
		val nameLiteral = syntheticLiteralNodeFor(availName)
		val primitive = primitiveByName(primitiveName)!!
		val function = createFunction(
			newPrimitiveRawFunction(
				primitive,
				compilationContext.module,
				token.lineNumber()),
			TupleDescriptor.emptyTuple())
		function.makeShared()
		val send = newSendNode(
			TupleDescriptor.emptyTuple(),
			METHOD_DEFINER.bundle,
			newListNode(tuple(nameLiteral, syntheticLiteralNodeFor(function))),
			TOP.o())
		evaluateModuleStatementThen(
			state, state, send, HashMap(), success)
	}

	/**
	 * Create a bootstrap primitive [macro][MacroDefinitionDescriptor]. Use the
	 * primitive's type declaration as the argument types.  If the primitive is
	 * fallible then generate suitable primitive failure code (to invoke the
	 * [CRASH]'s [method's][MethodDescriptor] [bundle][A_BundleTree]).
	 *
	 * @param state
	 *   The [state][ParserState] following a parse of the [module
	 *   header][ModuleHeader].
	 * @param token
	 *   A token with which to associate the definition of the function(s).
	 *   Since this is a bootstrap macro (and possibly prefix functions), it's
	 *   appropriate to use the string token within the pragma for this purpose.
	 * @param macroName
	 *   The name of the primitive macro being defined.
	 * @param primitiveNames
	 *   The array of [String]s that are bootstrap macro names. These correspond
	 *   to the occurrences of the [SECTION_SIGN][Metacharacter.SECTION_SIGN]
	 *   (§) in the macro name, plus a final body for the complete macro.
	 * @param success
	 *   What to do after the macro is defined successfully.
	 */
	internal fun bootstrapMacroThen(
		state: ParserState,
		token: A_Token,
		macroName: String,
		primitiveNames: Array<String>,
		success: ()->Unit)
	{
		assert(primitiveNames.isNotEmpty())
		val availName = stringFrom(macroName)
		val token1 = literalToken(
			stringFrom(availName.toString()),
			0,
			0,
			availName)
		val nameLiteral = literalNodeFromToken(token1)
		val functionLiterals = ArrayList<A_Phrase>()
		try
		{
			for (primitiveName in primitiveNames)
			{
				val prim = primitiveByName(primitiveName)!!
				functionLiterals.add(
					syntheticLiteralNodeFor(
						createFunction(
							newPrimitiveRawFunction(
								prim,
								compilationContext.module,
								token.lineNumber()),
							TupleDescriptor.emptyTuple())))
			}
		}
		catch (e: RuntimeException)
		{
			compilationContext.reportInternalProblem(
				state.lineNumber,
				state.position,
				e)
			compilationContext.diagnostics.reportError()
			return
		}

		val bodyLiteral = functionLiterals.removeAt(functionLiterals.size - 1)
		val send = newSendNode(
			TupleDescriptor.emptyTuple(),
			MACRO_DEFINER.bundle,
			newListNode(
				tuple(
					nameLiteral,
					newListNode(tupleFromList(functionLiterals)),
					bodyLiteral)),
			TOP.o())
		evaluateModuleStatementThen(state, state, send, HashMap(), success)
	}

	/**
	 * Create a bootstrap primitive lexer. Validate the primitive's type
	 * declaration against what's needed for a lexer function.  If either
	 * primitive is fallible then generate suitable primitive failure code for
	 * it (to invoke the [CRASH] [method's][MethodDescriptor]
	 * [bundle][A_BundleTree]).
	 *
	 * The filter takes a character and answers a boolean indicating whether the
	 * lexer should be attempted when that character is next in the source file.
	 *
	 * The body takes a character (which has already passed the filter), the
	 * entire source string, and the one-based index of the current character in
	 * the string.  It returns nothing, but it invokes a success primitive for
	 * each successful lexing (passing a tuple of tokens and the character
	 * position after what was lexed), and/or invokes a failure primitive to
	 * give specific diagnostics about what went wrong.
	 *
	 * @param state
	 *   The [state][ParserState] following a parse of the [module
	 *   header][ModuleHeader].
	 * @param token
	 *   A token with which to associate the definition of the lexer function.
	 *   Since this is a bootstrap lexer, it's appropriate to use the string
	 *   token within the pragma for this purpose.
	 * @param lexerAtom
	 *   The name (an [atom][A_Atom]) of the lexer being defined.
	 * @param filterPrimitiveName
	 *   The [primitive name][Primitive.name] of the filter for the lexer being
	 *   defined.
	 * @param bodyPrimitiveName
	 *   The [primitive name][Primitive.name] of the body of the
	 *   [lexer][A_Lexer] being defined.
	 * @param success
	 *   What to do after the method is bootstrapped successfully.
	 */
	internal fun bootstrapLexerThen(
		state: ParserState,
		token: A_Token,
		lexerAtom: A_Atom,
		filterPrimitiveName: String,
		bodyPrimitiveName: String,
		success: ()->Unit)
	{
		// Process the filter primitive.
		val filterPrimitive = primitiveByName(filterPrimitiveName)
		if (filterPrimitive === null)
		{
			state.expected(STRONG, "a valid primitive for the lexer filter")
			return
		}
		val filterFunctionType = filterPrimitive.blockTypeRestriction()
		if (!filterFunctionType.equals(lexerFilterFunctionType()))
		{
			state.expected(
				STRONG,
				"a primitive lexer filter function with type "
				+ lexerFilterFunctionType()
				+ ", not "
				+ filterFunctionType)
			return
		}
		val filterFunction = createFunction(
			newPrimitiveRawFunction(
				filterPrimitive,
				compilationContext.module,
				token.lineNumber()),
			TupleDescriptor.emptyTuple())

		// Process the body primitive.
		val bodyPrimitive = primitiveByName(bodyPrimitiveName)
		if (bodyPrimitive === null)
		{
			state.expected(
				STRONG,
				"a valid primitive for the lexer body")
			return
		}
		val bodyFunctionType = bodyPrimitive.blockTypeRestriction()
		if (!bodyFunctionType.equals(lexerBodyFunctionType()))
		{
			state.expected(
				STRONG,
				"a primitive lexer body function with type "
				+ lexerBodyFunctionType()
				+ ", not "
				+ bodyFunctionType)
		}
		val bodyFunction = createFunction(
			newPrimitiveRawFunction(
				bodyPrimitive,
				compilationContext.module,
				token.lineNumber()),
			TupleDescriptor.emptyTuple())

		// Process the lexer name.
		val nameLiteral = syntheticLiteralNodeFor(lexerAtom)

		// Build a phrase to define the lexer.
		val send = newSendNode(
			TupleDescriptor.emptyTuple(),
			LEXER_DEFINER.bundle,
			newListNode(
				tuple(
					nameLiteral,
					syntheticLiteralNodeFor(filterFunction),
					syntheticLiteralNodeFor(bodyFunction))),
			TOP.o())
		evaluateModuleStatementThen(
			ParserState(token.nextLexingState(), emptyMap()),
			state,
			send,
			HashMap(),
			success)
	}

	/**
	 * Apply any pragmas detected during the parse of the [module
	 * header][ModuleHeader].
	 *
	 * @param state
	 *   The [parse state][ParserState] following a parse of the module header.
	 * @param success
	 *   What to do after the pragmas have been applied successfully.
	 */
	private fun applyPragmasThen(state: ParserState, success: ()->Unit)
	{
		val iterator = moduleHeader.pragmas.iterator()
		compilationContext.loader!!.setPhase(EXECUTING_FOR_COMPILE)
		var recurse: (()->Unit)? = null
		recurse = recurse@ {
			if (!iterator.hasNext())
			{
				// Done with all the pragmas, if any.  Report any new
				// problems relative to the body section.
				recordExpectationsRelativeTo(state)
				success()
				return@recurse
			}
			val pragmaToken = iterator.next()
			val pragmaString = pragmaToken.literal()
			val nativeString = pragmaString.asNativeString()
			val pragmaParts =
				nativeString.split("=".toRegex(), 2).toTypedArray()
			if (pragmaParts.size != 2)
			{
				compilationContext.diagnostics.reportError(
					pragmaToken.nextLexingState(),
					"Malformed pragma at %s on line %d:",
					"Pragma should have the form key=value")
				return@recurse
			}
			val pragmaKindString = pragmaParts[0].trim { it <= ' ' }
			val pragmaValue = pragmaParts[1].trim { it <= ' ' }
			val pragmaKind = pragmaKindByLexeme(pragmaKindString)
			if (pragmaKind === null)
			{
				compilationContext.diagnostics.reportError(
					pragmaToken.nextLexingState(),
					"Unsupported pragma kind at %s on line %d:",
					"Pragma kind should be one of: "
					+ Arrays.stream(PragmaKind.values())
						.map { it.lexeme }
						.toList())
				return@recurse
			}
			pragmaKind.applyThen(
				this@AvailCompiler,
				pragmaToken,
				pragmaValue,
				state
			) {
				state.lexingState.compilationContext.eventuallyDo(
					state.lexingState, recurse!!)
			}
		}
		recurse.invoke()
	}

	/**
	 * Parse a [module header][ModuleHeader] from the [token
	 * list][TokenDescriptor] and apply any side-effects. Then [parse the module
	 * body][parseAndExecuteOutermostStatements] and apply any side-effects.
	 * Finally, execute the [CompilerDiagnostics]'s [success
	 * reporter][CompilerDiagnostics.successReporter].
	 */
	private fun parseModuleCompletely()
	{
		parseModuleHeader { afterHeader ->
			compilationContext.progressReporter.invoke(
				moduleName,
				source.tupleSize().toLong(),
				afterHeader.position.toLong())
			// Run any side-effects implied by this module header against
			// the module.
			val errorString = moduleHeader.applyToModule(
				compilationContext.module,
				compilationContext.runtime)
			if (errorString !== null)
			{
				compilationContext.progressReporter.invoke(
					moduleName,
					source.tupleSize().toLong(),
					source.tupleSize().toLong())
				afterHeader.expected(STRONG, errorString)
				compilationContext.diagnostics.reportError()
				return@parseModuleHeader
			}
			compilationContext.loader!!.prepareForCompilingModuleBody()
			applyPragmasThen(afterHeader) {
				parseAndExecuteOutermostStatements(afterHeader)
			}
		}
	}

	/**
	 * Parse a top-level statement, execute it, and repeat if we're not at the
	 * end of the module.
	 *
	 * @param start
	 *   The [parse state][ParserState] after parsing a [module
	 *   header][ModuleHeader].
	 */
	private fun parseAndExecuteOutermostStatements(start: ParserState)
	{
		compilationContext.loader!!.setPhase(COMPILING)
		// Forget any accumulated tokens from previous top-level statements.
		val startLexingState = start.lexingState
		val startWithoutAnyTokens = ParserState(
			LexingState(
				startLexingState.compilationContext,
				startLexingState.position,
				startLexingState.lineNumber,
				emptyList()),
			start.clientDataMap)
		parseOutermostStatement(
			startWithoutAnyTokens,
			Con1(null) { solution ->
				// The counters must be read in this order for correctness.
				assert(compilationContext.workUnitsCompleted
					== compilationContext.workUnitsQueued)

				// Check if we're cleanly at the end.
				if (solution.phrase.equals(endOfFileMarkerPhrase))
				{
					reachedEndOfModule(solution.endState)
					return@Con1
				}

				// In case the top level statement is compound, process the
				// base statements individually.
				val afterStatement = solution.endState
				val unambiguousStatement = solution.phrase
				val simpleStatements = ArrayList<A_Phrase>()
				unambiguousStatement.statementsDo { simpleStatement ->
					assert(simpleStatement.phraseKindIsUnder(
						STATEMENT_PHRASE))
					simpleStatements.add(simpleStatement)
				}

				// For each top-level simple statement, (1) transform it to have
				// referenced previously transformed top-level declarations
				// mapped from local scope into global scope, (2) if it's itself
				// a declaration, transform it and record the transformation for
				// subsequent statements, and (3) execute it.  The
				// declarationRemap accumulates the transformations.  Parts 2
				// and 3 actually happen together so that module constants can
				// have types as strong as the actual values produced by running
				// their initialization expressions.

				// What to do after running all these simple statements.
				val resumeParsing = {
					// Report progress.
					compilationContext.progressReporter.invoke(
						moduleName,
						source.tupleSize().toLong(),
						afterStatement.position.toLong())
					parseAndExecuteOutermostStatements(
						afterStatement.withMap(start.clientDataMap))
				}

				compilationContext.loader!!.setPhase(EXECUTING_FOR_COMPILE)
				// Run the simple statements in succession.
				val simpleStatementIterator = simpleStatements.iterator()
				val declarationRemap = HashMap<A_Phrase, A_Phrase>()
				var recurse: (()->Unit)? = null
				recurse = recurse@ {
					if (!simpleStatementIterator.hasNext())
					{
						resumeParsing()
						return@recurse
					}
					val statement = simpleStatementIterator.next()
					if (AvailLoader.debugLoadedStatements)
					{
						println(
							moduleName.qualifiedName
								+ ':'.toString() + start.lineNumber
								+ " Running statement:\n" + statement)
					}
					evaluateModuleStatementThen(
						start,
						afterStatement,
						statement,
						declarationRemap,
						recurse!!)
				}
				recurse.invoke()
			})
	}

	/**
	 * We just reached the end of the module.
	 *
	 * @param afterModule
	 *   The position at the end of the module.
	 */
	private fun reachedEndOfModule(afterModule: ParserState)
	{
		val theLoader = compilationContext.loader!!
		if (theLoader.pendingForwards.setSize() != 0)
		{
			val formatter = Formatter()
			formatter.format("the following forwards to be resolved:")
			for (forward in theLoader.pendingForwards)
			{
				formatter.format("%n\t%s", forward)
			}
			afterModule.expected(STRONG, formatter.toString())
			compilationContext.diagnostics.reportError()
			return
		}
		// Clear the section of the fragment cache associated with the
		// (outermost) statement just parsed and executed...
		synchronized(fragmentCache) { fragmentCache.clear() }
		compilationContext.diagnostics.successReporter!!.invoke()
	}

	/**
	 * Clear any information about potential problems encountered during
	 * parsing.  Reset the problem information to record relative to the given
	 * [ParserState].
	 *
	 * @param positionInSource
	 *   The [ParserState] at the earliest source position for which we should
	 *   record problem information.
	 */
	@Synchronized
	private fun recordExpectationsRelativeTo(positionInSource: ParserState) =
		compilationContext.diagnostics.startParsingAt(positionInSource)

	/**
	 * Parse a [module][ModuleDescriptor] from the source and install it into
	 * the [runtime][AvailRuntime].  This method generally returns long before
	 * the module has been parsed, but either the onSuccess or afterFail
	 * continuation is invoked when module parsing has completed or failed.
	 *
	 * @param onSuccess
	 *   What to do when the entire module has been parsed successfully.
	 * @param afterFail
	 *   What to do after compilation fails.
	 */
	@Synchronized
	fun parseModule(
		onSuccess: (A_Module)->Unit,
		afterFail: ()->Unit)
	{
		val ran = AtomicBoolean(false)
		compilationContext.diagnostics.setSuccessAndFailureReporters(
			{
				val old = ran.getAndSet(true)
				assert(!old) { "Attempting to succeed twice." }
				serializePublicationFunction(true)
				serializePublicationFunction(false)
				commitModuleTransaction()
				onSuccess(compilationContext.module)
			},
			{
				rollbackModuleTransaction(afterFail)
			})
		startModuleTransaction()
		parseModuleCompletely()
	}

	/**
	 * Parse a command, compiling it into the current
	 * [module][ModuleDescriptor], from the [token][TokenDescriptor] list.
	 *
	 * @param onSuccess
	 *   What to do after compilation succeeds. This continuation is invoked
	 *   with a list of [phrases][A_Phrase] that represent the possible
	 *   solutions of compiling the command and a continuation that cleans up
	 *   this compiler and its module (and then continues with a post-cleanup
	 *   continuation).
	 * @param afterFail
	 *   What to do after compilation fails.
	 */
	@Synchronized
	fun parseCommand(
		onSuccess: (List<A_Phrase>, (()->Unit)->Unit)->Unit,
		afterFail: ()->Unit)
	{
		compilationContext.diagnostics.setSuccessAndFailureReporters(
			{}, afterFail)
		assert(compilationContext.workUnitsCompleted == 0L
			&& compilationContext.workUnitsQueued == 0L)
		// Start a module transaction, just to complete any necessary
		// initialization. We are going to rollback this transaction no matter
		// what happens.
		startModuleTransaction()
		val loader = compilationContext.loader!!
		loader.prepareForCompilingModuleBody()
		val clientData = mapFromPairs(
			COMPILER_SCOPE_MAP_KEY.atom,
			emptyMap(),
			STATIC_TOKENS_KEY.atom,
			TupleDescriptor.emptyTuple(),
			ALL_TOKENS_KEY.atom,
			TupleDescriptor.emptyTuple())
		val start = ParserState(
			LexingState(compilationContext, 1, 1, emptyList()), clientData)
		val solutions = ArrayList<A_Phrase>()
		compilationContext.noMoreWorkUnits = noMoreWorkUnits@ {
			// The counters must be read in this order for correctness.
			assert(compilationContext.workUnitsCompleted == compilationContext.workUnitsQueued)
			// If no solutions were found, then report an error.
			if (solutions.isEmpty())
			{
				start.expected(STRONG, "an invocation of an entry point")
				compilationContext.diagnostics.reportError()
				return@noMoreWorkUnits
			}
			onSuccess(solutions) { this.rollbackModuleTransaction(it) }
		}
		recordExpectationsRelativeTo(start)
		parseExpressionThen(
			start,
			Con1(null) { solution ->
				val expression = solution.phrase
				val afterExpression = solution.endState
				if (expression.equals(endOfFileMarkerPhrase))
				{
					afterExpression.expected(
						STRONG,
						"a valid command, not just whitespace")
					return@Con1
				}
				if (expression.hasSuperCast())
				{
					afterExpression.expected(
						STRONG,
						"a valid command, not a supercast")
					return@Con1
				}
				// Check that after the expression is only whitespace and
				// the end-of-file.
				nextNonwhitespaceTokensDo(afterExpression) { token ->
					if (token.tokenType() == END_OF_FILE)
					{
						synchronized(solutions) {
							solutions.add(expression)
						}
					}
					else
					{
						afterExpression.expected(
							STRONG, "end of command")
					}
				}
			})
	}

	/**
	 * Process a header that has just been parsed.
	 *
	 * @param headerPhrase
	 *   The invocation of [SpecialMethodAtom.MODULE_HEADER] that was just
	 *   parsed.
	 * @param stateAfterHeader
	 *   The [ParserState] after the module's header.
	 * @return
	 *   Whether header processing was successful.  If unsuccessful,
	 *   arrangements will already have been made (and perhaps already executed)
	 *   to present the error.
	 */
	private fun processHeaderMacro(
		headerPhrase: A_Phrase,
		stateAfterHeader: ParserState): Boolean
	{
		assert(headerPhrase.phraseKindIsUnder(SEND_PHRASE))
		assert(headerPhrase.apparentSendName().equals(MODULE_HEADER.atom))
		val args = convertHeaderPhraseToValue(headerPhrase.argumentsListNode())
		assert(args.tupleSize() == 6)
		val (
			moduleNameToken,
			optionalVersionsPart,
			allImportsPart,
			optionalNamesPart,
			optionalEntriesPart,
			optionalPragmasPart) = args

		// Module name was checked against file name in a prefix function.
		val moduleName = stringFromToken(moduleNameToken)
		assert(moduleName.asNativeString() == this.moduleName.localName)

		// Module versions were already checked for duplicates.
		if (optionalVersionsPart.tupleSize() > 0)
		{
			assert(optionalVersionsPart.tupleSize() == 1)
			for (versionStringToken in optionalVersionsPart.tupleAt(1))
			{
				val versionString = stringFromToken(versionStringToken)
				assert(!moduleHeader.versions.contains(versionString))
				moduleHeader.versions.add(versionString)
			}
		}

		// Imports section (all Extends/Uses subsections)
		for ((importKindToken, importEntries) in allImportsPart)
		{
			assert(importKindToken.isInstanceOfKind(TOKEN.o()))
			val importKind = importKindToken.literal()
			assert(importKind.isInt)
			val importKindInt = importKind.extractInt()
			assert(importKindInt in 1 .. 2)
			val isExtension = importKindInt == 1

			for ((
				importedModuleToken,
				optionalImportVersions,
				optionNameImports) in importEntries)
			{
				val importedModuleName = stringFromToken(importedModuleToken)
				assert(optionalImportVersions.isTuple)

				var importVersions = emptySet()
				if (optionalImportVersions.tupleSize() > 0)
				{
					assert(optionalImportVersions.tupleSize() == 1)
					for (importVersionToken in optionalImportVersions.tupleAt(1))
					{
						val importVersionString =
							stringFromToken(importVersionToken)
						// Guaranteed by P_ModuleHeaderPrefixCheckImportVersion.
						assert(!importVersions.hasElement(importVersionString))
						importVersions =
							importVersions.setWithElementCanDestroy(
								importVersionString, true)
					}
				}

				var importedNames = emptySet()
				var importedRenames = emptyMap()
				var importedExcludes = emptySet()
				var wildcard = true

				// <filterEntries, finalEllipsis>?
				if (optionNameImports.tupleSize() > 0)
				{
					assert(optionNameImports.tupleSize() == 1)
					val namesPart = optionNameImports.tupleAt(1)
					val (filterEntries, finalEllipsisToken) = namesPart
					for ((negationToken, nameToken, optionalRename)
						in filterEntries)
					{
						val negation = negationToken.literal().extractBoolean()
						val name = stringFromToken(nameToken)
						when
						{
							optionalRename.tupleSize() > 0 ->
							{
								// Process a renamed import.
								assert(optionalRename.tupleSize() == 1)
								val renameToken = optionalRename.tupleAt(1)
								if (negation)
								{
									renameToken
										.nextLexingState()
										.expected(
											STRONG,
											"negated or renaming import, but "
												+ "not both")
									compilationContext.diagnostics.reportError()
									return false
								}
								val rename = stringFromToken(renameToken)
								if (importedRenames.hasKey(rename))
								{
									renameToken
										.nextLexingState()
										.expected(
											STRONG,
											"renames to specify distinct "
												+ "target names")
									compilationContext.diagnostics.reportError()
									return false
								}
								importedRenames =
									importedRenames.mapAtPuttingCanDestroy(
										rename, name, true)
							}
							negation ->
							{
								// Process an excluded import.
								if (importedExcludes.hasElement(name))
								{
									nameToken.nextLexingState().expected(
										STRONG,
										"import exclusions to be unique")
									compilationContext.diagnostics.reportError()
									return false
								}
								importedExcludes =
									importedExcludes.setWithElementCanDestroy(
										name, true)
							}
							else ->
							{
								// Process a regular import (neither a negation
								// nor an exclusion).
								if (importedNames.hasElement(name))
								{
									nameToken.nextLexingState().expected(
										STRONG, "import names to be unique")
									compilationContext.diagnostics.reportError()
									return false
								}
								importedNames =
									importedNames.setWithElementCanDestroy(
										name, true)
							}
						}
					}
					// Check for the trailing ellipsis.
					wildcard = finalEllipsisToken.literal().extractBoolean()
				}

				try
				{
					moduleHeader.importedModules.add(
						ModuleImport(
							importedModuleName,
							importVersions,
							isExtension,
							importedNames,
							importedRenames,
							importedExcludes,
							wildcard))
				}
				catch (e: ImportValidationException)
				{
					importedModuleToken.nextLexingState().expected(
						STRONG, e.message!!)
					compilationContext.diagnostics.reportError()
					return false
				}

			}  // modules of an import subsection
		}  // imports section

		// Names section
		if (optionalNamesPart.tupleSize() > 0)
		{
			assert(optionalNamesPart.tupleSize() == 1)
			for (nameToken in optionalNamesPart.tupleAt(1))
			{
				val nameString = stringFromToken(nameToken)
				if (moduleHeader.exportedNames.contains(nameString))
				{
					compilationContext.diagnostics.reportError(
						nameToken.nextLexingState(),
						"Duplicate declared name detected at %s on line %d:",
						format(
							"Declared name %s should be unique",
							nameString))
					return false
				}
				moduleHeader.exportedNames.add(nameString)
			}
		}

		// Entries section
		if (optionalEntriesPart.tupleSize() > 0)
		{
			assert(optionalEntriesPart.tupleSize() == 1)
			for (entryToken in optionalEntriesPart.tupleAt(1))
			{
				moduleHeader.entryPoints.add(stringFromToken(entryToken))
			}
		}

		// Pragmas section
		if (optionalPragmasPart.tupleSize() > 0)
		{
			assert(optionalPragmasPart.tupleSize() == 1)
			for (pragmaToken in optionalPragmasPart.tupleAt(1))
			{
				val innerToken = pragmaToken.literal()
				moduleHeader.pragmas.add(innerToken)
			}
		}
		moduleHeader.startOfBodyPosition = stateAfterHeader.position
		moduleHeader.startOfBodyLineNumber = stateAfterHeader.lineNumber
		return true
	}

	/**
	 * Parse the header of the module from the token stream. If successful,
	 * invoke onSuccess with the [ParserState] just after the header, otherwise
	 * invoke onFail without reporting the problem.
	 *
	 * If the `dependenciesOnly` parameter is true, only parse the bare minimum
	 * needed to determine information about which modules are used by this one.
	 *
	 * @param onSuccess
	 *   What to do after successfully parsing the header.  The compilation
	 *   context's header will have been updated, and the continuation will be
	 *   passed the [ParserState] after the header.
	 */
	fun parseModuleHeader(onSuccess: (ParserState)->Unit)
	{
		// Create the initial parser state: no tokens have been seen, and no
		// names are in scope.
		val clientData = mapFromPairs(
			COMPILER_SCOPE_MAP_KEY.atom,
			emptyMap(),
			STATIC_TOKENS_KEY.atom,
			emptyMap(),
			ALL_TOKENS_KEY.atom,
			TupleDescriptor.emptyTuple())
		val state = ParserState(
			LexingState(compilationContext, 1, 1, emptyList()), clientData)

		recordExpectationsRelativeTo(state)

		// Parse an invocation of the special module header macro.
		parseOutermostStatement(
			state,
			Con1(null) { solution ->
				val headerPhrase = solution.phrase
				if (headerPhrase.phraseKindIsUnder(MARKER_PHRASE))
				{
					// It made it to the end of the file.  This mechanism is
					// used to determine when we've already parsed the last
					// top-level statement of a module, when only whitespace and
					// comments remain.  Not appropriate for a module header.
					compilationContext.diagnostics.reportError(
						solution.endState.lexingState,
						"Unexpectedly reached end of file without "
							+ "encountering module header.",
						"")
					return@Con1
				}
				if (!headerPhrase.phraseKindIsUnder(
						EXPRESSION_AS_STATEMENT_PHRASE)
					|| !headerPhrase.apparentSendName().equals(
						MODULE_HEADER.atom))
				{
					// This shouldn't be possible, but in theory we might some
					// day introduce non-root macros for arguments.
					stringifyThen(
						compilationContext.runtime,
						compilationContext.textInterface,
						headerPhrase
					) { headerPhraseAsString ->
						compilationContext.diagnostics.reportError(
							solution.endState.lexingState,
							"Expected module header, but found this "
								+ "phrase instead: %s",
							headerPhraseAsString)
					}
					return@Con1
				}
				try
				{
					val ok = processHeaderMacro(
						headerPhrase.expression(), solution.endState)
					if (ok)
					{
						onSuccess(solution.endState)
					}
				}
				catch (e: Exception)
				{
					compilationContext.diagnostics.reportError(
						solution.endState.lexingState,
						"Unexpected exception encountered while processing "
							+ "module header (ends at %s, line %d):",
						trace(e))
				}
			})
	}

	/**
	 * Parse an expression. Backtracking will find all valid interpretations.
	 * This method is a key optimization point, so the fragmentCache is used to
	 * keep track of parsing solutions at this point, simply replaying them on
	 * subsequent parses, as long as the variable declarations up to that point
	 * were identical.
	 *
	 * Additionally, the [fragmentCache] also keeps track of actions to perform
	 * when another solution is found at this position, so the solutions and
	 * actions can be added in arbitrary order while ensuring that each action
	 * gets a chance to try each solution.
	 *
	 * @param start
	 *   Where to start parsing.
	 * @param originalContinuation
	 *   What to do with the expression.
	 */
	private fun parseExpressionThen(
		start: ParserState,
		originalContinuation: Con1)
	{
		// The first time we parse at this position the fragmentCache will have
		// no knowledge about it.
		val rendezvous = fragmentCache.getRendezvous(start)
		if (!rendezvous.andSetStartedParsing)
		{
			// We're the (only) cause of the transition from hasn't-started to
			// has-started.  Suppress reporting if there are no superexpressions
			// being parsed, or if we're at the root of the bundle tree.
			val isRoot = originalContinuation.superexpressions === null
				|| originalContinuation.superexpressions.bundleTree.equals(
					compilationContext.loader!!.rootBundleTree())
			start.expected(if (isRoot) SILENT else WEAK) {
				val builder = StringBuilder()
				builder.append("an expression for (at least) this reason:")
				describeOn(originalContinuation.superexpressions, builder)
				it(builder.toString())
			}
			start.workUnitDo(
				{ a -> parseExpressionUncachedThen(start, a) },
				Con1(originalContinuation.superexpressions) {
					rendezvous.addSolution(it)
				})
		}
		start.workUnitDo({ rendezvous.addAction(it) }, originalContinuation)
	}

	/**
	 * Parse a top-level statement.  This is the *only* boundary for the
	 * backtracking grammar (it used to be that *all* statements had to be
	 * unambiguous, even those in blocks).  The passed continuation will be
	 * invoked at most once, and only if the top-level statement had a single
	 * interpretation.
	 *
	 * @param start
	 *   Where to start parsing a top-level statement.
	 * @param continuation
	 *   What to do with the (unambiguous) top-level statement.
	 */
	private fun parseOutermostStatement(start: ParserState, continuation: Con1)
	{
		// If a parsing error happens during parsing of this outermost
		// statement, only show the section of the file starting here.
		recordExpectationsRelativeTo(start)
		tryIfUnambiguousThen(
			start,
			{ whenFoundStatement ->
				parseExpressionThen(
					start,
					Con1(null) { solution ->
						val expression = solution.phrase
						val afterExpression = solution.endState
						if (expression.phraseKindIsUnder(STATEMENT_PHRASE))
						{
							whenFoundStatement(
								CompilerSolution(
									afterExpression, expression))
							return@Con1
						}
						afterExpression.expected(
							STRONG,
							FormattingDescriber(
								"an outer level statement, not %s (%s)",
								expression.phraseKind(),
								expression))
					})
				nextNonwhitespaceTokensDo(start) { token ->
					if (token.tokenType() == END_OF_FILE)
					{
						whenFoundStatement(
							CompilerSolution(start, endOfFileMarkerPhrase))
					}
				}
			},
			continuation)
	}

	/**
	 * Parse an expression, without directly using the [fragmentCache].
	 *
	 * @param start
	 *   Where to start parsing.
	 * @param continuation
	 *   What to do with the expression.
	 */
	private fun parseExpressionUncachedThen(
		start: ParserState,
		continuation: Con1)
	{
		parseLeadingKeywordSendThen(
			start,
			Con1(continuation.superexpressions) { solution ->
				parseOptionalLeadingArgumentSendAfterThen(
					start,
					solution.endState,
					solution.phrase,
					continuation)
			})
	}

	/**
	 * A helper method to queue a parsing activity for continuing to parse a
	 * [send phrase][SendPhraseDescriptor].
	 *
	 * @param start
	 *   The current [ParserState].
	 * @param bundleTree
	 *   The current [A_BundleTree] being applied.
	 * @param firstArgOrNull
	 *   Either `null` or a pre-parsed first argument phrase.
	 * @param initialTokenPosition
	 *   The position at which parsing of this message started. If it was parsed
	 *   as a leading argument send (i.e., `firstArgOrNull` started out
	 *   non-`null`) then the position is of the token following the first
	 *   argument.
	 * @param consumedAnything
	 *   Whether any tokens have been consumed yet.
	 * @param consumedAnythingBeforeLatestArgument
	 *   Whether any tokens or arguments had been consumed before encountering
	 *   the most recent argument.  This is to improve diagnostics when argument
	 *   type checking is postponed past matches for subsequent tokens.
	 * @param consumedTokens
	 *   The [A_Token]s that have been consumed so far for this invocation.
	 * @param argsSoFar
	 *   The arguments stack.
	 * @param marksSoFar
	 *   The marks stack.
	 * @param continuation
	 *   What to do with a completed phrase.
	 */
	internal fun eventuallyParseRestOfSendNode(
		start: ParserState,
		bundleTree: A_BundleTree,
		firstArgOrNull: A_Phrase?,
		initialTokenPosition: ParserState,
		consumedAnything: Boolean,
		consumedAnythingBeforeLatestArgument: Boolean,
		consumedTokens: List<A_Token>,
		argsSoFar: List<A_Phrase>,
		marksSoFar: List<Int>,
		continuation: Con1)
	{
		start.workUnitDo(
			{
				parseRestOfSendNode(
					start,
					bundleTree,
					firstArgOrNull,
					initialTokenPosition,
					consumedAnything,
					consumedAnythingBeforeLatestArgument,
					consumedTokens,
					argsSoFar,
					marksSoFar,
					continuation)
			},
			"ignored")
	}

	/**
	 * Serialize a function that will publish all atoms that are currently
	 * public in the module.
	 *
	 * @param isPublic
	 *   `true` if the atoms are public, `false` if they are private.
	 */
	private fun serializePublicationFunction(isPublic: Boolean)
	{
		// Output a function that publishes the initial public set of atoms.
		val sourceNames =
			if (isPublic)
				compilationContext.module.importedNames()
			else
				compilationContext.module.privateNames()
		var names = emptySet()
		for (entry in sourceNames.mapIterable())
		{
			names = names.setUnionCanDestroy(
				entry.value().makeImmutable(), true)
		}
		val send = newSendNode(
			TupleDescriptor.emptyTuple(),
			PUBLISH_ATOMS.bundle,
			newListNode(
				tuple(
					syntheticLiteralNodeFor(names),
					syntheticLiteralNodeFor(objectFromBoolean(isPublic)))),
			TOP.o())
		val function = createFunctionForPhrase(
			send, compilationContext.module, 0)
		function.makeImmutable()
		privateSerializeFunction(function)
	}

	/**
	 * Hold the monitor and serialize the given function.
	 *
	 * @param function
	 *   The [A_Function] to serialize.
	 */
	@Synchronized
	private fun privateSerializeFunction(function: A_Function) =
		compilationContext.serializer.serialize(function)

	companion object
	{
		/**
		 * Asynchronously construct a suitable `AvailCompiler` to parse the
		 * specified [module name][ModuleName].
		 *
		 * @param resolvedName
		 *   The [resolved name][ResolvedModuleName] of the
		 *   [module][ModuleDescriptor] to compile.
		 * @param textInterface
		 *   The [text interface][TextInterface] for any [fiber][A_Fiber]
		 *   started by the new compiler.
		 * @param pollForAbort
		 *   A function that indicates whether to abort.
		 * @param reporter
		 *   The [CompilerProgressReporter] used to report progress.
		 * @param afterFail
		 *   What to do after a failure that the [problem
		 *   handler][ProblemHandler] does not choose to continue.
		 * @param problemHandler
		 *   A problem handler.
		 * @param succeed
		 *   What to do with the resultant compiler in the event of success.
		 *   This is a continuation that accepts the new compiler.
		 */
		@JvmStatic
		fun create(
			resolvedName: ResolvedModuleName,
			textInterface: TextInterface,
			pollForAbort: () -> Boolean,
			reporter: CompilerProgressReporter,
			afterFail: ()->Unit,
			problemHandler: ProblemHandler,
			succeed: (AvailCompiler)->Unit)
		{
			extractSourceThen(resolvedName, afterFail, problemHandler) {
				sourceText ->
				succeed(
					AvailCompiler(
						ModuleHeader(resolvedName),
						newModule(stringFrom(resolvedName.qualifiedName)),
						stringFrom(sourceText),
						textInterface,
						pollForAbort,
						reporter,
						problemHandler))
			}
		}

		/**
		 * Read the source string for the [module][ModuleDescriptor] specified
		 * by the fully-qualified [module name][ModuleName].
		 *
		 * @param resolvedName
		 *   The [resolved name][ResolvedModuleName] of the module.
		 * @param fail
		 *   What to do in the event of a failure that the [problem
		 *   handler][ProblemHandler] does not wish to continue.
		 * @param problemHandler
		 *   A problem handler.
		 * @param withSource
		 *   What to do after the source module has been completely read.
		 *   Accepts the source text of the module.
		 */
		private fun extractSourceThen(
			resolvedName: ResolvedModuleName,
			fail: ()->Unit,
			problemHandler: ProblemHandler,
			withSource: (String)->Unit)
		{
			val runtime = currentRuntime()
			val ref = resolvedName.sourceReference
			val decoder = StandardCharsets.UTF_8.newDecoder()
			decoder.onMalformedInput(CodingErrorAction.REPLACE)
			decoder.onUnmappableCharacter(CodingErrorAction.REPLACE)
			val input = ByteBuffer.allocateDirect(4096)
			val output = CharBuffer.allocate(4096)
			val file: AsynchronousFileChannel
			try
			{
				file = runtime.ioSystem().openFile(
					ref.toPath(), EnumSet.of(StandardOpenOption.READ))
			}
			catch (e: IOException)
			{
				val problem = object : Problem(
					resolvedName,
					1,
					0,
					PARSE,
					"Unable to open source module \"{0}\" [{1}]: {2}",
					resolvedName,
					ref.absolutePath,
					e.localizedMessage)
				{
					override fun abortCompilation()
					{
						fail()
					}
				}
				problemHandler.handle(problem)
				return
			}

			val sourceBuilder = StringBuilder(4096)
			val filePosition = MutableLong(0L)
			// Kick off the asynchronous read.
			SimpleCompletionHandler<Int, Any?>(
				{ bytesRead, _, handler ->
					try
					{
						var moreInput = true
						if (bytesRead == -1)
						{
							moreInput = false
						}
						else
						{
							filePosition.value += bytesRead.toLong()
						}
						input.flip()
						val result = decoder.decode(
							input, output, !moreInput)
						// UTF-8 never compresses data, so the number of
						// characters encoded can be no greater than the number
						// of bytes encoded. The input buffer and the output
						// buffer are equally sized (in units), so an overflow
						// cannot occur.
						assert(!result.isOverflow)
						assert(!result.isError)
						// If the decoder didn't consume all of the bytes, then
						// preserve the unconsumed bytes in the next buffer (for
						// decoding).
						if (input.hasRemaining())
						{
							input.compact()
						}
						else
						{
							input.clear()
						}
						output.flip()
						sourceBuilder.append(output)
						// If more input remains, then queue another read.
						if (moreInput)
						{
							output.clear()
							handler.guardedDo(
								file::read, input, filePosition.value, null)
						}
						// Otherwise, close the file channel and queue the
						// original continuation.
						else
						{
							decoder.flush(output)
							sourceBuilder.append(output)
							file.close()
							runtime.execute(
								FiberDescriptor.compilerPriority
							) { withSource(sourceBuilder.toString()) }
						}
					}
					catch (e: IOException)
					{
						val problem = object : Problem(
							resolvedName,
							1,
							0,
							PARSE,
							"Invalid UTF-8 encoding in source module "
								+ "\"{0}\": {1}\n{2}",
							resolvedName,
							e.localizedMessage,
							trace(e))
						{
							override fun abortCompilation()
							{
								fail()
							}
						}
						problemHandler.handle(problem)
					}
				},
				// failure
				{ e, _, _ ->
					val problem = object : Problem(
						resolvedName,
						1,
						0,
						EXTERNAL,
						"Unable to read source module \"{0}\": {1}\n{2}",
						resolvedName,
						e.localizedMessage,
						trace(e))
					{
						override fun abortCompilation()
						{
							fail()
						}
					}
					problemHandler.handle(problem)
				}
			).guardedDo(file::read, input, 0L, null)
		}

		/**
		 * Map the entire phrase through the (destructive) transformation
		 * specified by aBlock, children before parents. The block takes three
		 * arguments: the phrase, its parent, and the list of enclosing block
		 * phrases. Answer the recursively transformed phrase.
		 *
		 * @param obj
		 *   The current [phrase][PhraseDescriptor].
		 * @param transformer
		 *   What to do with each descendant.
		 * @param parentPhrase
		 *   This phrase's parent.
		 * @param outerPhrases
		 *   The list of [blocks][BlockPhraseDescriptor] surrounding this
		 *   phrase, from outermost to innermost.
		 * @param phraseMap
		 *   The [Map] from old [phrases][PhraseDescriptor] to newly copied,
		 *   mutable phrases.  This should ensure the consistency of declaration
		 *   references.
		 * @return
		 *   A replacement for this phrase, possibly this phrase itself.
		 */
		private fun treeMapWithParent(
			obj: A_Phrase,
			transformer: (A_Phrase, A_Phrase, List<A_Phrase>) -> A_Phrase,
			parentPhrase: A_Phrase,
			outerPhrases: List<A_Phrase>,
			phraseMap: MutableMap<A_Phrase, A_Phrase>): A_Phrase
		{
			if (phraseMap.containsKey(obj))
			{
				return phraseMap[obj]!!
			}
			val objectCopy = obj.copyMutablePhrase()
			objectCopy.childrenMap { child ->
				assert(child !== null)
				assert(child!!.isInstanceOfKind(PARSE_PHRASE.mostGeneralType()))
				treeMapWithParent(
					child, transformer, objectCopy, outerPhrases, phraseMap)
			}
			val transformed = transformer(
				objectCopy, parentPhrase, outerPhrases)
			transformed.makeShared()
			phraseMap[obj] = transformed
			return transformed
		}

		/**
		 * Given two unequal phrases, find the smallest descendant phrases that
		 * still contain all the differences.  The given [Mutable] objects
		 * initially contain references to the root phrases, but are updated to
		 * refer to the most specific pair of phrases that contain all the
		 * differences.
		 *
		 * @param phrase1
		 *   A `Mutable` reference to a [phrase][PhraseDescriptor].  Updated to
		 *   hold the most specific difference.
		 * @param phrase2
		 *   The `Mutable` reference to the other phrase. Updated to hold the
		 *   most specific difference.
		 */
		private fun findParseTreeDiscriminants(
			phrase1: Mutable<A_Phrase>,
			phrase2: Mutable<A_Phrase>)
		{
			while (true)
			{
				assert(!phrase1.value.equals(phrase2.value))
				if (phrase1.value.phraseKind() != phrase2.value.phraseKind())
				{
					// The phrases are different kinds, so present them as what's
					// different.
					return
				}
				if (phrase1.value.isMacroSubstitutionNode
					&& phrase2.value.isMacroSubstitutionNode)
				{
					if (phrase1.value.macroOriginalSendNode().equals(
							phrase2.value.macroOriginalSendNode()))
					{
						// Two occurrences of the same macro.  Drill into the
						// resulting phrases.
						phrase1.value = phrase1.value.outputPhrase()
						phrase2.value = phrase2.value.outputPhrase()
						continue
					}
					// Otherwise the macros are different and we should stop.
					return
				}
				if (phrase1.value.isMacroSubstitutionNode
					|| phrase2.value.isMacroSubstitutionNode)
				{
					// They aren't both macros, but one is, so they're different.
					return
				}
				if (phrase1.value.phraseKindIsUnder(SEND_PHRASE)
					&& !phrase1.value.bundle().equals(phrase2.value.bundle()))
				{
					// They're sends of different messages, so don't go any
					// deeper.
					return
				}
				val parts1 = ArrayList<A_Phrase>()
				phrase1.value.childrenDo { parts1.add(it) }
				val parts2 = ArrayList<A_Phrase>()
				phrase2.value.childrenDo { parts2.add(it) }
				val isBlock = phrase1.value.phraseKindIsUnder(BLOCK_PHRASE)
				if (parts1.size != parts2.size && !isBlock)
				{
					// Different structure at this level.
					return
				}
				val differentIndices = ArrayList<Int>()
				for (i in 0 until min(parts1.size, parts2.size))
				{
					if (!parts1[i].equals(parts2[i]))
					{
						differentIndices.add(i)
					}
				}
				if (isBlock)
				{
					if (differentIndices.size == 0)
					{
						// Statement or argument lists are probably different
						// sizes. Use the block itself.
						return
					}
					// Show the first argument or statement that differs.
					// Fall through.
				}
				else if (differentIndices.size != 1)
				{
					// More than one part differs, so we can't drill deeper.
					return
				}
				// Drill into the only part that differs.
				phrase1.value = parts1[differentIndices[0]]
				phrase2.value = parts2[differentIndices[0]]
			}
		}

		/**
		 * Pre-build the state of the initial parse stack.  Now that the
		 * top-most arguments get concatenated into a list, simply start with a
		 * list containing one empty list phrase.
		 */
		private val initialParseStack = listOf<A_Phrase>(emptyListNode())

		/**
		 * Pre-build the state of the initial mark stack.  This stack keeps
		 * track of parsing positions to detect if progress has been made at
		 * certain points. This mechanism serves to prevent empty expressions
		 * from being considered an occurrence of a repeated or optional
		 * subexpression, even if it would otherwise be recognized as such.
		 */
		private val initialMarkStack = emptyList<Int>()

		/** Statistic for matching an exact token.  */
		private val matchTokenStat = Statistic(
			"(Match particular token)",
			RUNNING_PARSING_INSTRUCTIONS)

		/** Statistic for matching a token case-insensitively.  */
		private val matchTokenInsensitivelyStat = Statistic(
			"(Match insensitive token)",
			RUNNING_PARSING_INSTRUCTIONS)

		/** Statistic for type-checking an argument.  */
		private val typeCheckArgumentStat = Statistic(
			"(type-check argument)",
			RUNNING_PARSING_INSTRUCTIONS)

		/** Marker phrase to signal cleanly reaching the end of the input.  */
		private val endOfFileMarkerPhrase =
			newMarkerNode(stringFrom("End of file marker")).makeShared()

		/**
		 * Skip over whitespace and comment tokens, collecting the latter.
		 * Produce a [List] of [ParserState]s corresponding to the possible
		 * positions after completely parsing runs of whitespaces and comments
		 * (i.e., the potential [A_Token]s that follow each such [ParserState]
		 * must include at least one token that isn't whitespace or a comment).
		 * Invoke the continuation with this list of parser states.
		 *
		 * Informally, it just skips as many whitespace and comment tokens as it
		 * can, but the nature of the ambiguous lexer makes this more subtle to
		 * express.
		 *
		 * Note that the continuation always gets invoked exactly once, after
		 * any relevant lexing has completed.
		 *
		 * @param start
		 *   Where to start consuming the token.
		 * @param continuation
		 *   What to invoke with the collection of successor [ParserState]s.
		 */
		private fun skipWhitespaceAndComments(
			start: ParserState,
			continuation: (List<ParserState>)->Unit)
		{
			val ran = AtomicBoolean(false)
			skipWhitespaceAndComments(
				start,
				{ list ->
					val old = ran.getAndSet(true)
					assert(!old) { "Completed skipping whitespace twice." }
					continuation(list)
				},
				AtomicBoolean(false))
		}

		/**
		 * Skip over whitespace and comment tokens, collecting the latter.
		 * Produce a [List] of [ParserState]s corresponding to the possible
		 * positions after completely parsing runs of whitespaces and comments
		 * (i.e., the potential [A_Token]s that follow each such [ParserState]
		 * must include at least one token that isn't whitespace or a comment).
		 * Invoke the continuation with this list of parser states.
		 *
		 * Informally, it just skips as many whitespace and comment tokens as it
		 * can, but the nature of the ambiguous lexer makes this more subtle to
		 * express.
		 *
		 * @param start
		 *   Where to start consuming the token.
		 * @param continuation
		 *   What to invoke with the collection of successor [ParserState]s.
		 * @param ambiguousWhitespace
		 *   An [AtomicBoolean], which should be set to `false` by the outermost
		 *   caller, but will be set to `true` if any part of the sequence of
		 *   whitespace tokens is determined to be ambiguously scanned.  The
		 *   ambiguity will be reported in that case, so there's no need for the
		 *   client to ever read the value.
		 */
		private fun skipWhitespaceAndComments(
			start: ParserState,
			continuation: (List<ParserState>)->Unit,
			ambiguousWhitespace: AtomicBoolean)
		{
			if (ambiguousWhitespace.get())
			{
				// Should probably be queued instead of called directly.
				continuation(emptyList())
				return
			}
			start.lexingState.withTokensDo { tokens ->
				val toSkip = ArrayList<A_Token>(1)
				val toKeep = ArrayList<A_Token>(1)
				for (token in tokens)
				{
					val tokenType = token.tokenType()
					if (tokenType == COMMENT || tokenType == WHITESPACE)
					{
						for (previousToSkip in toSkip)
						{
							if (previousToSkip.string().equals(token.string()))
							{
								ambiguousWhitespace.set(true)
								if (tokenType == WHITESPACE
									&& token.string().tupleSize() < 50)
								{
									start.expected(
										STRONG,
										"the whitespace " + token.string()
											+ " to be uniquely lexically"
											+ " scanned.  There are probably"
											+ " multiple conflicting lexers"
											+ " visible in this module.")
								}
								else if (tokenType == COMMENT
									&& token.string().tupleSize() < 100)
								{
									start.expected(
										STRONG,
										"the comment " + token.string()
											+ " to be uniquely lexically"
											+ " scanned.  There are probably"
											+ " multiple conflicting lexers"
											+ " visible in this module.")
								}
								else
								{
									start.expected(
										STRONG,
										"the comment or whitespace ("
											+ token.string().tupleSize()
											+ " characters) to be uniquely"
											+ " lexically scanned.  There are"
											+ " probably multiple conflicting"
											+ " lexers visible in this"
											+ " module.")
								}
								continuation(emptyList())
								return@withTokensDo
							}
						}
						toSkip.add(token)
					}
					else
					{
						toKeep.add(token)
					}
				}
				if (toSkip.size == 0)
				{
					if (toKeep.size == 0)
					{
						start.expected(
							STRONG,
							"a way to parse tokens here, but all lexers were "
								+ "unproductive")
						continuation(emptyList())
					}
					else
					{
						// The common case where no interpretation is
						// whitespace/comment, but there's a non-whitespace
						// token (or end of file).  Allow parsing to continue
						// right here.
						continuation(listOf(start))
					}
					return@withTokensDo
				}
				if (toSkip.size == 1 && toKeep.size == 0)
				{
					// Common case of an unambiguous whitespace/comment token.
					val token = toSkip[0]
					skipWhitespaceAndComments(
						ParserState(
							token.nextLexingState(), start.clientDataMap),
						continuation,
						ambiguousWhitespace)
					return@withTokensDo
				}
				// Rarer, more complicated cases with at least two
				// interpretations, at least one of which is whitespace/comment.
				val result = ArrayList<ParserState>(3)
				if (toKeep.size > 0)
				{
					// There's at least one non-whitespace token present at
					// start.
					result.add(start)
				}
				val countdown = MutableInt(toSkip.size)
				for (tokenToSkip in toSkip)
				{
					// Common case of an unambiguous whitespace/comment token.
					val after = ParserState(
						tokenToSkip.nextLexingState(), start.clientDataMap)
					skipWhitespaceAndComments(
						after,
						{ partialList ->
							synchronized(countdown) {
								result.addAll(partialList)
								countdown.value--
								assert(countdown.value >= 0)
								if (countdown.value == 0)
								{
									continuation(result)
								}
							}
						},
						ambiguousWhitespace)
				}
			}
		}

		/**
		 * Transform the argument, a [phrase][A_Phrase], into a [literal phrase]
		 * whose value is the original phrase. If the given phrase is a [macro
		 * substitution phrase] then extract its [A_Phrase.apparentSendName],
		 * strip off the macro substitution, wrap the resulting expression in a
		 * literal phrase, then re-apply the same apparentSendName to the new
		 * literal phrase to produce another macro substitution phrase.
		 *
		 * @param phrase
		 *   A phrase.
		 * @return
		 *   A literal phrase that yields the given phrase as its value.
		 */
		private fun wrapAsLiteral(phrase: A_Phrase): A_Phrase
		{
			return if (phrase.isMacroSubstitutionNode)
			{
				newMacroSubstitution(
					phrase.macroOriginalSendNode(),
					syntheticLiteralNodeFor(phrase))
			}
			else syntheticLiteralNodeFor(phrase)
		}

		/**
		 * The given phrase must contain only subexpressions that are literal
		 * phrases or list phrases.  Convert the structure into a nested tuple
		 * of tokens.
		 *
		 * The tokens are kept, rather than extracting the literal strings or
		 * integers, so that error reporting can refer to the token positions.
		 *
		 * @param phrase
		 *   The root literal phrase or list phrase.
		 * @return
		 *   The token of the literal phrase, or a tuple with the (recursive)
		 *   tuples of the list phrase's subexpressions' tokens.
		 */
		private fun convertHeaderPhraseToValue(phrase: A_Phrase): AvailObject
		{
			return when (phrase.phraseKind())
			{
				LITERAL_PHRASE -> phrase.token() as AvailObject
				LIST_PHRASE, PERMUTED_LIST_PHRASE ->
				{
					val expressions = phrase.expressionsTuple()
					generateObjectTupleFrom(expressions.tupleSize() ) { index ->
						convertHeaderPhraseToValue(expressions.tupleAt(index))
					}
				}
				MACRO_SUBSTITUTION_PHRASE ->
					convertHeaderPhraseToValue(phrase.stripMacro())
				else ->
				{
					throw RuntimeException(
						"Unexpected phrase type in header: "
							+ phrase.phraseKind().name)
				}
			}
		}

		/**
		 * Extract a [string][A_String] from the given string literal
		 * [token][A_Token].
		 *
		 * @param token
		 *   The string literal token.
		 * @return
		 *   The token's string.
		 */
		private fun stringFromToken(token: A_Token): A_String
		{
			assert(token.isInstanceOfKind(TOKEN.o()))
			val innerToken = token.literal()
			val literal = innerToken.literal()
			assert(literal.isInstanceOfKind(stringType()))
			return literal
		}

		/**
		 * Answer the [set][SetDescriptor] of [declaration phrases] which are
		 * used by this parse tree but are locally declared (i.e., not at global
		 * module scope).
		 *
		 * @param phrase
		 *   The phrase to recursively examine.
		 * @return
		 *   The set of the local declarations that were used in the phrase.
		 */
		private fun usesWhichLocalVariables(phrase: A_Phrase): A_Set
		{
			val usedDeclarations = Mutable(emptySet())
			phrase.childrenDo { childPhrase ->
				if (childPhrase.isInstanceOfKind(
						VARIABLE_USE_PHRASE.mostGeneralType()))
				{
					val declaration = childPhrase.declaration()
					if (!declaration.declarationKind().isModuleScoped)
					{
						usedDeclarations.value =
							usedDeclarations.value.setWithElementCanDestroy(
								declaration, true)
					}
				}
			}
			return usedDeclarations.value
		}
	}
}
