/*
 * AvailCompiler.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import avail.AvailRuntime
import avail.AvailRuntimeConfiguration
import avail.AvailRuntimeSupport.captureNanos
import avail.builder.ModuleName
import avail.builder.ResolvedModuleName
import avail.compiler.ParsingOperation.CHECK_ARGUMENT
import avail.compiler.ParsingOperation.Companion.decode
import avail.compiler.ParsingOperation.Companion.distinctInstructions
import avail.compiler.ParsingOperation.Companion.operand
import avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT
import avail.compiler.PragmaKind.Companion.pragmaKindByLexeme
import avail.compiler.problems.CompilerDiagnostics
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.MEDIUM
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.SILENT
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK
import avail.compiler.problems.Problem
import avail.compiler.problems.ProblemHandler
import avail.compiler.problems.ProblemType.EXTERNAL
import avail.compiler.problems.ProblemType.PARSE
import avail.compiler.scanning.LexingState
import avail.compiler.splitter.MessageSplitter.Companion.constantForIndex
import avail.compiler.splitter.MessageSplitter.Metacharacter
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import avail.descriptor.atoms.A_Atom.Companion.issuingModule
import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.ALL_TOKENS_KEY
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_MAP_KEY
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.MACRO_BUNDLE_KEY
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.STATIC_TOKENS_KEY
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.lookupMacroByPhraseTuple
import avail.descriptor.bundles.A_Bundle.Companion.macrosTuple
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.bundles.A_BundleTree
import avail.descriptor.bundles.A_BundleTree.Companion.allParsingPlansInProgress
import avail.descriptor.bundles.A_BundleTree.Companion.expand
import avail.descriptor.bundles.A_BundleTree.Companion.isSourceOfCycle
import avail.descriptor.bundles.A_BundleTree.Companion.latestBackwardJump
import avail.descriptor.bundles.A_BundleTree.Companion.lazyActions
import avail.descriptor.bundles.A_BundleTree.Companion.lazyComplete
import avail.descriptor.bundles.A_BundleTree.Companion.lazyIncomplete
import avail.descriptor.bundles.A_BundleTree.Companion.lazyIncompleteCaseInsensitive
import avail.descriptor.bundles.A_BundleTree.Companion.lazyPrefilterMap
import avail.descriptor.bundles.A_BundleTree.Companion.lazyTypeFilterTreePojo
import avail.descriptor.bundles.MessageBundleDescriptor
import avail.descriptor.bundles.MessageBundleTreeDescriptor
import avail.descriptor.fiber.A_Fiber
import avail.descriptor.fiber.A_Fiber.Companion.fiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.setGeneralFlag
import avail.descriptor.fiber.A_Fiber.Companion.textInterface
import avail.descriptor.fiber.FiberDescriptor.Companion.compilerPriority
import avail.descriptor.fiber.FiberDescriptor.Companion.newLoaderFiber
import avail.descriptor.fiber.FiberDescriptor.GeneralFlag
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunctionForPhrase
import avail.descriptor.functions.PrimitiveCompiledCodeDescriptor.Companion.newPrimitiveRawFunction
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.forEach
import avail.descriptor.maps.A_Map.Companion.hasKey
import avail.descriptor.maps.A_Map.Companion.keysAsSet
import avail.descriptor.maps.A_Map.Companion.mapAt
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.A_Map.Companion.mapAtReplacingCanDestroy
import avail.descriptor.maps.A_Map.Companion.mapIterable
import avail.descriptor.maps.A_Map.Companion.mapSize
import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.maps.MapDescriptor.Companion.mapFromPairs
import avail.descriptor.methods.A_Macro
import avail.descriptor.methods.A_Method.Companion.bundles
import avail.descriptor.methods.A_Method.Companion.definitionsTuple
import avail.descriptor.methods.A_Method.Companion.filterByTypes
import avail.descriptor.methods.A_Method.Companion.semanticRestrictions
import avail.descriptor.methods.A_SemanticRestriction
import avail.descriptor.methods.A_Sendable
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.methods.A_Sendable.Companion.definitionModule
import avail.descriptor.methods.A_Sendable.Companion.definitionModuleName
import avail.descriptor.methods.A_Sendable.Companion.isMethodDefinition
import avail.descriptor.methods.MacroDescriptor
import avail.descriptor.methods.MethodDefinitionDescriptor
import avail.descriptor.methods.MethodDescriptor
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.CRASH
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.CREATE_MODULE_VARIABLE
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.LEXER_DEFINER
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.MACRO_DEFINER
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.METHOD_DEFINER
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.MODULE_HEADER
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.PUBLISH_ALL_ATOMS_FROM_OTHER_MODULE
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.PUBLISH_ATOMS
import avail.descriptor.methods.SemanticRestrictionDescriptor
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.addConstantBinding
import avail.descriptor.module.A_Module.Companion.addVariableBinding
import avail.descriptor.module.A_Module.Companion.constantBindings
import avail.descriptor.module.A_Module.Companion.exportedNames
import avail.descriptor.module.A_Module.Companion.hasAncestor
import avail.descriptor.module.A_Module.Companion.importedNames
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.descriptor.module.A_Module.Companion.privateNames
import avail.descriptor.module.A_Module.Companion.removeFrom
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.module.A_Module.Companion.variableBindings
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.module.ModuleDescriptor.Companion.newModule
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.parsingInstructions
import avail.descriptor.parsing.A_Lexer
import avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.nameHighlightingPc
import avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.parsingPc
import avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.parsingPlan
import avail.descriptor.parsing.LexerDescriptor.Companion.lexerBodyFunctionType
import avail.descriptor.parsing.LexerDescriptor.Companion.lexerFilterFunctionType
import avail.descriptor.parsing.ParsingPlanInProgressDescriptor.Companion.newPlanInProgress
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.apparentSendName
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.bundle
import avail.descriptor.phrases.A_Phrase.Companion.childrenDo
import avail.descriptor.phrases.A_Phrase.Companion.childrenMap
import avail.descriptor.phrases.A_Phrase.Companion.copyMutablePhrase
import avail.descriptor.phrases.A_Phrase.Companion.declaration
import avail.descriptor.phrases.A_Phrase.Companion.declaredType
import avail.descriptor.phrases.A_Phrase.Companion.expression
import avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.hasSuperCast
import avail.descriptor.phrases.A_Phrase.Companion.initializationExpression
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.macroOriginalSendNode
import avail.descriptor.phrases.A_Phrase.Companion.outputPhrase
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.statementsDo
import avail.descriptor.phrases.A_Phrase.Companion.stripMacro
import avail.descriptor.phrases.A_Phrase.Companion.superUnionType
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.A_Phrase.Companion.typeExpression
import avail.descriptor.phrases.AssignmentPhraseDescriptor.Companion.newAssignment
import avail.descriptor.phrases.BlockPhraseDescriptor
import avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newModuleConstant
import avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newModuleVariable
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_CONSTANT
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_VARIABLE
import avail.descriptor.phrases.ListPhraseDescriptor
import avail.descriptor.phrases.ListPhraseDescriptor.Companion.emptyListNode
import avail.descriptor.phrases.ListPhraseDescriptor.Companion.newListNode
import avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.literalNodeFromToken
import avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.syntheticLiteralNodeFor
import avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.Companion.newMacroSubstitution
import avail.descriptor.phrases.MarkerPhraseDescriptor.Companion.newMarkerNode
import avail.descriptor.phrases.PhraseDescriptor
import avail.descriptor.phrases.SendPhraseDescriptor
import avail.descriptor.phrases.SendPhraseDescriptor.Companion.newSendNode
import avail.descriptor.phrases.VariableUsePhraseDescriptor.Companion.newUse
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.asTuple
import avail.descriptor.sets.A_Set.Companion.hasElement
import avail.descriptor.sets.A_Set.Companion.setIntersects
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.generateSetFrom
import avail.descriptor.tokens.A_Token
import avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import avail.descriptor.tokens.TokenDescriptor
import avail.descriptor.tokens.TokenDescriptor.TokenType.COMMENT
import avail.descriptor.tokens.TokenDescriptor.TokenType.END_OF_FILE
import avail.descriptor.tokens.TokenDescriptor.TokenType.KEYWORD
import avail.descriptor.tokens.TokenDescriptor.TokenType.OPERATOR
import avail.descriptor.tokens.TokenDescriptor.TokenType.WHITESPACE
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.component1
import avail.descriptor.tuples.A_Tuple.Companion.component2
import avail.descriptor.tuples.A_Tuple.Companion.component3
import avail.descriptor.tuples.A_Tuple.Companion.component4
import avail.descriptor.tuples.A_Tuple.Companion.component5
import avail.descriptor.tuples.A_Tuple.Companion.component6
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.tuples.TupleDescriptor.Companion.toList
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.acceptsArgTypesFromFunctionType
import avail.descriptor.types.A_Type.Companion.acceptsListOfArgValues
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.couldEverBeInvokedWith
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.BLOCK_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.DECLARATION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MACRO_SUBSTITUTION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MARKER_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PERMUTED_LIST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.VARIABLE_USE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOKEN
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.descriptor.variables.VariableSharedGlobalDescriptor.Companion.createGlobal
import avail.dispatch.LookupStatistics
import avail.exceptions.AvailEmergencyExitException
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION
import avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Companion.primitiveByName
import avail.interpreter.execution.AvailLoader
import avail.interpreter.execution.AvailLoader.Phase.COMPILING
import avail.interpreter.execution.AvailLoader.Phase.EXECUTING_FOR_COMPILE
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.runOutermostFunction
import avail.interpreter.execution.Interpreter.Companion.stringifyThen
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.primitive.compiler.P_RejectParsing
import avail.io.TextInterface
import avail.performance.Statistic
import avail.performance.StatisticReport.RUNNING_PARSING_INSTRUCTIONS
import avail.performance.StatisticReport.TYPE_CHECKING_FOR_PARSER
import avail.persistence.cache.Repository
import avail.utility.Mutable
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.StackPrinter.Companion.trace
import avail.utility.Strings.increaseIndentation
import avail.utility.evaluation.Describer
import avail.utility.evaluation.FormattingDescriber
import avail.utility.safeWrite
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Collections.emptyList
import java.util.Formatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Collectors.toList
import kotlin.concurrent.withLock
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
 *   The [module&#32;header][ModuleHeader] of the module to compile. May be null
 *   for synthetic modules (for entry points), or when parsing the header.
 * @param module
 *   The current [module][ModuleDescriptor].`
 * @param source
 *   The source [A_String].
 * @param textInterface
 *   The [text&#32;interface][TextInterface] for any [fibers][A_Fiber] started
 *   by this compiler.
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
class AvailCompiler constructor(
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
	 * [repository][Repository] if necessary.
	 */
	val compilationContext = CompilationContext(
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
	 * The [module&#32;header][ModuleHeader] for the current
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
		compilationContext.module.moduleNameNative)

	/**
	 * A list of subexpressions being parsed, represented by
	 * [message&#32;bundle&#32;trees][A_BundleTree] holding the positions
	 * within all outer send expressions.
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
		/** How many subexpressions deep that we're parsing. */
		val depth: Int = if (parent === null) 1 else parent.depth + 1

		/**
		 * Create a list like the receiver, but with a different
		 * [message&#32;bundle&#32;tree][A_BundleTree].
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
			if (bundleTree.equals(compilationContext.loader.rootBundleTree()))
			{
				builder.append("an expression")
			}
			else
			{
				// Reduce to the plans' unique bundles.
				val bundlesMap = bundleTree.allParsingPlansInProgress
				val bundles = toList<A_Bundle>(bundlesMap.keysAsSet.asTuple)
				bundles.sortedBy { it.message.atomName.asNativeString() }
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
					val plansInProgress = plans.mapIterable.next().value()
					val planInProgress = plansInProgress.first()
					// Adjust the pc to refer to the actual instruction that
					// caused the argument parse, not the successor instruction
					// that was captured.
					val adjustedPlanInProgress = newPlanInProgress(
						planInProgress.parsingPlan,
						planInProgress.parsingPc - 1)
					builder.append(adjustedPlanInProgress.nameHighlightingPc)
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
	 * Execute `#tryBlock`, passing a function that it should run upon finding
	 * exactly one local [solution][CompilerSolution].  Report ambiguity as an
	 * error.
	 *
	 * @param start
	 *   Where to start parsing.
	 * @param acceptAnswer
	 *   What to do if exactly one result was produced. This is a continuation
	 *   that accepts the [ParserState] after the uniquely parsed phrase, and
	 *   the [A_Phrase] itself that was parsed.
	 */
	private fun tryIfUnambiguousThen(
		start: ParserState,
		acceptAnswer: (ParserState, A_Phrase)->Unit)
	{
		val solutions = mutableListOf<CompilerSolution>()

		// Set up an action to perform when the last work unit for this compiler
		// has completed.  That's the moment when we can check how many
		// solutions were actually found.
		assert(compilationContext.noMoreWorkUnits === null)
		compilationContext.noMoreWorkUnits = {
			when
			{
				compilationContext.diagnostics.pollForAbort() ->
				{
					// We may have been asked to abort sub-tasks by a failure in
					// another module, so we can't trust the count of solutions.
					compilationContext.diagnostics.reportError()
				}
				solutions.size == 0 ->
				{
					// No solutions were found.  Report the problems.
					compilationContext.diagnostics.reportError()
				}
				solutions.size == 1 ->
				{
					// A unique solution was found.
					acceptAnswer(
						solutions[0].endState,
						solutions[0].phrase)
				}
				else ->
				{
					// Multiple solutions were found.  Report the ambiguity.
					reportAmbiguousInterpretations(
						solutions[0].endState,
						solutions[0].phrase,
						solutions[1].phrase)
				}
			}
		}
		start.workUnitDo {
			parseExpressionThen(start, null) {
				afterExpression, expression ->
				when
				{
					expression.phraseKindIsUnder(STATEMENT_PHRASE) ->
						captureOneMoreSolution(
							CompilerSolution(afterExpression, expression),
							solutions)
					else ->
						afterExpression.expected(
							SILENT,
							FormattingDescriber(
								"an outer level statement, not %s (%s)",
								expression.phraseKind,
								expression))
				}
			}
			nextNonwhitespaceTokensDo(start) { token ->
				if (token.tokenType() == END_OF_FILE)
				{
					captureOneMoreSolution(
						CompilerSolution(start, endOfFileMarkerPhrase),
						solutions)
				}
			}
		}
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
					phrase1.value.macroOriginalSendNode)
			) { (print1, print2) ->
				("unambiguous interpretation. At least two parses produced " +
					"the same phrase:\n\t$print1\n...where the pre-macro " +
					"expression is:\n\t$print2")
			}
		}
		else
		{
			findParseTreeDiscriminants(phrase1, phrase2)
			val tokens = phrase1.value.tokens
			val line =
				if (tokens.tupleSize > 0) tokens.tupleAt(1).lineNumber()
				else 0
			if (phrase1.value.isMacroSubstitutionNode
				&& phrase2.value.isMacroSubstitutionNode)
			{
				where.expected(
					STRONG,
					listOf(
						phrase1.value,
						phrase2.value,
						phrase1.value.macroOriginalSendNode,
						phrase2.value.macroOriginalSendNode)
				) { (print1, print2, original1, original2) ->
					if (print1 == print2)
					{
						val atom1 =
							phrase1.value.macroOriginalSendNode.bundle.message
						val atom2 =
							phrase2.value.macroOriginalSendNode.bundle.message
						("unambiguous interpretation near line $line. At " +
							"least two parses produced same-looking phrases " +
							"after macro substitution. The post-macro " +
							"phrase is:\n"
							+ "\t$print1\n...and the pre-macro phrases are:\n"
							+ "\t$original1 (bundle=$atom1)\n"
							+ "\t$original2 (bundle=$atom2)")
					}
					else
					{
						("unambiguous interpretation near line $line. Here " +
							"are two possible parsings..." +
							"\n\t$print1\n\t$print2")
					}
				}
			}
			else
			{
				where.expected(
					STRONG,
					listOf(phrase1.value, phrase2.value)
				) { (print1, print2) ->
					if (print1 == print2)
					{
						("unambiguous interpretation near line $line. "
							+ "At least two parses produced unequal but "
							+ "same-looking phrases:\n\t"
							+ print1)
					}
					else
					{
						("unambiguous interpretation near line $line. "
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
		// This currently does nothing.  Eval might need this still.
	}

	/**
	 * Rollback the [module][ModuleDescriptor] that was defined since the most
	 * recent [startModuleTransaction]. [Close][A_Module.moduleState] the
	 * module.
	 *
	 * @param afterRollback
	 *   What to do after rolling back.
	 */
	private fun rollbackModuleTransaction(afterRollback: ()->Unit) =
		compilationContext.module.removeFrom(
			compilationContext.loader, afterRollback)

	/**
	 * Commit the [module][A_Module] that was defined since the most recent
	 * [startModuleTransaction].  This also closes the module against further
	 * changes by [setting][A_Module.moduleState] its state to
	 * [Loaded][ModuleDescriptor.State.Loaded].
	 */
	private fun commitModuleTransaction() =
		compilationContext.runtime.addModule(compilationContext.module)

	/**
	 * Evaluate the specified semantic restriction [function][A_Function] in the
	 * module's context; lexically enclosing variables are not considered in
	 * scope, but module variables and constants are in scope.
	 *
	 * @param restriction
	 *   A [semantic&#32;restriction][SemanticRestrictionDescriptor].
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
		val mod = code.module
		val fiber = newLoaderFiber(
			function.kind().returnType,
			compilationContext.loader)
		{
			formatString(
				"Semantic restriction %s, in %s:%d",
				restriction.definitionMethod().bundles.first().message,
				if (mod.isNil) "no module" else mod.shortModuleNameNative,
				code.codeStartingLineNumber)
		}
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE)
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
	 *   A [macro&#32;definition][MacroDescriptor].
	 * @param args
	 *   The argument phrases to supply the macro.
	 * @param clientParseData
	 *   The map to associate with the [SpecialAtom.CLIENT_DATA_GLOBAL_KEY] atom
	 *   in the fiber.
	 * @param clientParseDataOut
	 *   A [Mutable] into which we will store an [A_Map] when the fiber
	 *   completes successfully.  The map will be the content of the fiber
	 *   variable holding the client data, extracted just after the fiber
	 *   completes.  If unsuccessful, don't assign to the `Mutable`.
	 * @param lexingState
	 *   The position at which the macro body is being evaluated.
	 * @param onSuccess
	 *   What to do with the result of the evaluation, an [A_Phrase].
	 * @param onFailure
	 *   What to do with a terminal [Throwable].
	 */
	private fun evaluateMacroFunctionThen(
		macro: A_Macro,
		args: List<A_Phrase>,
		clientParseData: A_Map,
		clientParseDataOut: Mutable<A_Map?>,
		lexingState: LexingState,
		onSuccess: (AvailObject)->Unit,
		onFailure: (Throwable)->Unit)
	{
		val function = macro.bodyBlock()
		val fiber = newLoaderFiber(
			function.kind().returnType,
			compilationContext.loader
		) {
			val code = function.code()
			val mod = code.module
			formatString(
				"Macro evaluation %s, in %s:%d",
				macro.definitionBundle().message,
				if (mod.isNil) "no module" else mod.shortModuleNameNative,
				code.codeStartingLineNumber)
		}
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE)
		fiber.setGeneralFlag(GeneralFlag.IS_EVALUATING_MACRO)
		var fiberGlobals = fiber.fiberGlobals
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			CLIENT_DATA_GLOBAL_KEY.atom, clientParseData, true)
		fiber.fiberGlobals = fiberGlobals
		lexingState.setFiberContinuationsTrackingWork(
			fiber,
			{ outputPhrase ->
				clientParseDataOut.value =
					fiber.fiberGlobals.mapAt(CLIENT_DATA_GLOBAL_KEY.atom)
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
	 *   The start [LexingState], for line number reporting.
	 * @param afterStatement
	 *   The [LexingState] just after the statement.
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
		startState: LexingState,
		afterStatement: LexingState,
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
			mutableListOf(),
			declarationRemap)

		val phraseFailure = { e: Throwable ->
			when (e)
			{
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
				startState,
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
		val loader = compilationContext.loader
		val name = replacement.token.string()
		val shadowProblem =
			when
			{
				module.variableBindings.hasKey(name) -> "module variable"
				module.constantBindings.hasKey(name) -> "module constant"
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
					replacement.initializationExpression,
					afterStatement,
					false,
					false,
					{ value ->
						loader.stopRecordingEffects()
						val canSummarize = loader.statementCanBeSummarized()
						val innerType = instanceTypeOrMetaOn(value)
						val varType = variableTypeFor(innerType)
						val creationSend = newSendNode(
							emptyTuple,
							CREATE_MODULE_VARIABLE.bundle,
							newListNode(
								tuple(
									syntheticLiteralNodeFor(module),
									syntheticLiteralNodeFor(name),
									syntheticLiteralNodeFor(varType),
									syntheticLiteralNodeFor(trueObject),
									syntheticLiteralNodeFor(
										objectFromBoolean(canSummarize)))),
							TOP.o)
						val creationFunction = createFunctionForPhrase(
							creationSend,
							module,
							replacement.token.lineNumber())
						// Force the declaration to be serialized.
						compilationContext.serializeWithoutSummary(
							creationFunction)
						val variable = createGlobal(varType, module, name, true)
						variable.setValueWasStablyComputed(canSummarize)
						module.addConstantBinding(name, variable)
						// Update the map so that the local constant goes to a
						// module constant.  Then subsequent statements in this
						// sequence will transform uses of the constant
						// appropriately.
						val newConstant = newModuleConstant(
							replacement.token,
							variable,
							replacement.initializationExpression)
						declarationRemap[expression] = newConstant
						// Now create a module variable declaration (i.e.,
						// cheat) JUST for this initializing assignment.
						val newDeclaration = newModuleVariable(
							replacement.token,
							variable,
							nil,
							replacement.initializationExpression)
						val assign = newAssignment(
							newUse(replacement.token, newDeclaration),
							syntheticLiteralNodeFor(value),
							expression.tokens,
							false)
						val assignFunction = createFunctionForPhrase(
							assign,
							module,
							replacement.token.lineNumber())
						compilationContext.serializeWithoutSummary(
							assignFunction)
						loader.manifestEntries!!.add(
							ModuleManifestEntry(
								SideEffectKind.MODULE_CONSTANT_KIND,
								name.asNativeString(),
								startState.lineNumber,
								replacement.token.lineNumber(),
								assignFunction))
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
				val varType = variableTypeFor(replacement.declaredType)
				val creationSend = newSendNode(
					emptyTuple,
					CREATE_MODULE_VARIABLE.bundle,
					newListNode(
						tuple(
							syntheticLiteralNodeFor(module),
							syntheticLiteralNodeFor(name),
							syntheticLiteralNodeFor(varType),
							syntheticLiteralNodeFor(falseObject),
							syntheticLiteralNodeFor(falseObject))),
					TOP.o)
				val creationFunction = createFunctionForPhrase(
					creationSend,
					module,
					replacement.token.lineNumber())
				creationFunction.makeImmutable()
				// Force the declaration to be serialized.
				compilationContext.serializeWithoutSummary(creationFunction)
				val variable = createGlobal(varType, module, name, false)
				module.addVariableBinding(name, variable)
				if (replacement.initializationExpression.notNil)
				{
					val newDeclaration = newModuleVariable(
						replacement.token,
						variable,
						replacement.typeExpression,
						replacement.initializationExpression)
					declarationRemap[expression] = newDeclaration
					val assign = newAssignment(
						newUse(replacement.token, newDeclaration),
						replacement.initializationExpression,
						tuple(expression.token),
						false)
					val assignFunction = createFunctionForPhrase(
						assign, module, replacement.token.lineNumber())
					compilationContext.evaluatePhraseThen(
						replacement.initializationExpression,
						afterStatement,
						false,
						false,
						{ value ->
							variable.setValue(value)
							compilationContext.serializeWithoutSummary(
								assignFunction)
							module.lock {
								loader.manifestEntries!!.add(
									ModuleManifestEntry(
										SideEffectKind.MODULE_VARIABLE_KIND,
										name.asNativeString(),
										startState.lineNumber,
										replacement.token.lineNumber(),
										assignFunction))
							}
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
			val builder = buildString {
				if (caseInsensitive)
				{
					append("one of the following case-insensitive tokens:")
				}
				else
				{
					append("one of the following tokens:")
				}
				val sorted = mutableListOf<String>()
				val detail = incomplete.mapSize < 10
				incomplete.forEach { availTokenString, nextTree ->
					if (!excludedStrings.contains(availTokenString))
					{
						if (!detail)
						{
							sorted.add(availTokenString.asNativeString())
							return@forEach
						}
						// Collect the plans-in-progress and deduplicate them by
						// their string representation (including the indicator
						// at the current parsing location). We can't just
						// deduplicate by bundle, since the current bundle tree
						// might be eligible for continued parsing at multiple
						// positions.
						val strings = mutableSetOf<String>()
						nextTree.allParsingPlansInProgress.forEach {
							bundle, definitions ->
							definitions.forEach { _, plans ->
								plans.forEach { inProgress ->
									val previousPlan = newPlanInProgress(
										inProgress.parsingPlan,
										max(inProgress.parsingPc - 1, 1))
									val issuingModule =
										bundle.message.issuingModule
									val moduleName =
										if (issuingModule.isNil)
											"(built-in)"
										else
											issuingModule.moduleName
												.asNativeString()
									val shortModuleName =
										moduleName.substring(
											moduleName.lastIndexOf('/') + 1)
									strings.add(
										previousPlan.nameHighlightingPc
											+ " from "
											+ shortModuleName)
								}
							}
						}
						val sortedStrings = strings.sorted()
						val buffer = buildString {
							append(availTokenString.asNativeString())
							append("  (")
							var first = true
							for (progressString in sortedStrings)
							{
								if (!first) append(", ")
								append(progressString)
								first = false
							}
							append(')')
						}
						sorted.add(buffer)
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
						append("\n\t")
						column = leftColumn
					}
					else
					{
						append("  ")
						column += 2
					}
					startOfLine = false
					val lengthBefore = length
					append(s)
					column += length - lengthBefore
					if (detail || column + 2 + s.length > 80)
					{
						startOfLine = true
					}
				}
			}
			compilationContext.eventuallyDo(where.lexingState) {
				withString(builder)
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
	 * @param superexpressions
	 *   The enclosing partially-parsed expressions, if any.
	 * @param continuation
	 *   What to do after parsing a complete send phrase.
	 */
	private fun parseLeadingKeywordSendThen(
		start: ParserState,
		superexpressions: PartialSubexpressionList?,
		continuation: (ParserState, A_Phrase)->Unit)
	{
		val loader = compilationContext.loader
		parseRestOfSendNode(
			loader.rootBundleTree(),
			ParsingStepState(
				start,
				null,
				initialParseStack,
				initialMarkStack,
				start,
				false,
				false,
				emptyList(),
				PartialSubexpressionList(
					loader.rootBundleTree(), superexpressions),
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
	 * @param superexpressions
	 *   The enclosing partially-parsed expressions, if any.
	 * @param continuation
	 *   What to do after parsing a send phrase.
	 */
	private fun parseLeadingArgumentSendAfterThen(
		start: ParserState,
		leadingArgument: A_Phrase,
		initialTokenPosition: ParserState,
		superexpressions: PartialSubexpressionList?,
		continuation: (ParserState, A_Phrase)->Unit)
	{
		assert(start.lexingState != initialTokenPosition.lexingState)
		val loader = compilationContext.loader
		parseRestOfSendNode(
			loader.rootBundleTree(),
			ParsingStepState(
				start,
				leadingArgument,
				initialParseStack,
				initialMarkStack,
				initialTokenPosition,
				false,
				false,
				emptyList(),
				PartialSubexpressionList(
					loader.rootBundleTree(), superexpressions),
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
	 * @param superexpressions
	 *   The enclosing partially-parsed expressions, if any.
	 * @param continuation
	 *   What to do with either the passed phrase, or the phrase wrapped in a
	 *   leading-argument send.
	 */
	private fun parseOptionalLeadingArgumentSendAfterThen(
		startOfLeadingArgument: ParserState,
		afterLeadingArgument: ParserState,
		phrase: A_Phrase,
		superexpressions: PartialSubexpressionList?,
		continuation: (ParserState, A_Phrase)->Unit)
	{
		// It's optional, so try it with no wrapping.  We have to try this even
		// if it's a supercast, since we may be parsing an expression to be a
		// non-leading argument of some send.
		afterLeadingArgument.workUnitDo {
			continuation(afterLeadingArgument, phrase)
		}
		// Try to wrap it in a leading-argument message send.
		afterLeadingArgument.workUnitDo {
			parseLeadingArgumentSendAfterThen(
				afterLeadingArgument,
				phrase,
				startOfLeadingArgument,
				superexpressions
			) { afterSend, sendPhrase ->
				parseOptionalLeadingArgumentSendAfterThen(
					startOfLeadingArgument,
					afterSend,
					sendPhrase,
					superexpressions,
					continuation)
			}
		}
	}

	/**
	 * We've parsed part of a send. Try to finish the job.
	 *
	 * @param bundleTreeArg
	 *   The bundle tree used to parse at this position.
	 * @param stepState
	 *   The [ParsingStepState] that represents the current state of parsing of
	 *   some partial method or macro invocation.
	 */
	private fun parseRestOfSendNode(
		bundleTreeArg: A_BundleTree,
		stepState: ParsingStepState)
	{
		var tempBundleTree = bundleTreeArg
		// If a bundle tree is marked as a source of a cycle, its latest
		// backward jump field is always the target.  Just continue processing
		// there, and it'll never have to expand the current node.  However,
		// it's the expand() that might set up the cycle in the first place...
		tempBundleTree.expand(compilationContext.module)
		while (tempBundleTree.isSourceOfCycle)
		{
			// Jump to its (once-)equivalent ancestor.
			tempBundleTree = tempBundleTree.latestBackwardJump
			// Give it a chance to find an equivalent ancestor of its own.
			tempBundleTree.expand(compilationContext.module)
			// Abort if the bundle trees have diverged.
			if (!tempBundleTree.allParsingPlansInProgress.equals(
					bundleTreeArg.allParsingPlansInProgress))
			{
				// They've diverged.  Disconnect the backward link.
				bundleTreeArg.isSourceOfCycle = false
				tempBundleTree = bundleTreeArg
				break
			}
		}
		val bundleTree = tempBundleTree

		var skipCheckArgumentAction = false
		if (stepState.firstArgOrNull === null)
		{
			// A call site is only valid if at least one token has been parsed.
			if (stepState.consumedAnything)
			{
				val complete = bundleTree.lazyComplete
				if (complete.setSize > 0)
				{
					// There are complete messages, we didn't leave a leading
					// argument stranded, and we made progress in the file
					// (i.e., the message contains at least one token).
					assert(stepState.marksSoFar.isEmpty())
					assert(stepState.argsSoFar.size == 1)
					val args = stepState.argsSoFar[0]
					for (bundle in complete)
					{
						if (AvailRuntimeConfiguration.debugCompilerSteps)
						{
							println(
								"Completed send/macro: ${bundle.message} "
								+ "$args")
						}
						completedSendNode(
							stepState.initialTokenPosition,
							stepState.start,
							args,
							bundle,
							stepState.consumedStaticTokens,
							stepState.continuation)
					}
				}
			}
			val incomplete = bundleTree.lazyIncomplete
			if (incomplete.mapSize > 0)
			{
				attemptToConsumeToken(stepState.copy(), incomplete, false)
			}
			val caseInsensitive = bundleTree.lazyIncompleteCaseInsensitive
			if (caseInsensitive.mapSize > 0)
			{
				attemptToConsumeToken(stepState.copy(), caseInsensitive, true)
			}
			val prefilter = bundleTree.lazyPrefilterMap
			if (prefilter.mapSize > 0)
			{
				val latestArgument = stepState.argsSoFar.last()
				if (latestArgument.isMacroSubstitutionNode
					|| latestArgument.isInstanceOfKind(
						SEND_PHRASE.mostGeneralType))
				{
					val argumentBundle =
						latestArgument.apparentSendName.bundleOrNil
					assert(argumentBundle.notNil)
					prefilter.mapAtOrNull(argumentBundle)?.let { successor ->
						if (AvailRuntimeConfiguration.debugCompilerSteps)
						{
							println(
								"Grammatical prefilter: $argumentBundle to "
								+ "$successor")
						}
						eventuallyParseRestOfSendNode(
							successor, stepState.copy())
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
			val typeFilterTreePojo = bundleTree.lazyTypeFilterTreePojo
			if (typeFilterTreePojo.notNil)
			{
				// Use the most recently pushed phrase's type to look up the
				// successor bundle tree.  This implements type filtering for
				// aggregated arguments.
				val latestPhrase = stepState.argsSoFar.last()
				val successor =
					MessageBundleTreeDescriptor.parserTypeChecker.lookupByValue(
						typeFilterTreePojo.javaObjectNotNull(),
						latestPhrase,
						bundleTree.latestBackwardJump,
						typeCheckArgumentStat)
				if (AvailRuntimeConfiguration.debugCompilerSteps)
				{
					println("Type filter: $latestPhrase -> $successor")
				}
				// Don't complain if at least one plan was happy with the type
				// of the argument.  Otherwise, list all argument type/plan
				// expectations as neatly as possible.
				if (successor.allParsingPlansInProgress.mapSize == 0)
				{
					// Also be silent if no static tokens have been consumed
					// yet.
					if (stepState.consumedStaticTokens.isNotEmpty())
					{
						stepState.start.expected(MEDIUM) { withDescription ->
							stringifyThen(
								compilationContext.runtime,
								compilationContext.textInterface,
								latestPhrase.phraseExpressionType
							) { actualTypeString ->
								describeFailedTypeTestThen(
									actualTypeString,
									bundleTree,
									stepState.superexpressions,
									withDescription)
							}
						}
					}
				}
				assert(stepState.firstArgOrNull === null)
				eventuallyParseRestOfSendNode(successor, stepState.copy())
				// Parse instruction optimization allows there to be some plans
				// that do a type filter here, but some that are able to
				// postpone it.  Therefore, also allow general actions to be
				// collected here by falling through.
			}
		}
		val actions = bundleTree.lazyActions
		if (actions.mapSize > 0)
		{
			actions.forEach { operation: A_Number, successors: A_Tuple ->
				val operationInt = operation.extractInt
				val op = decode(operationInt)
				when
				{
					skipCheckArgumentAction && op === CHECK_ARGUMENT ->
					{
						// Skip this action, because the latest argument was a
						// send that had an entry in the prefilter map, so it
						// has already been dealt with.
					}
					stepState.firstArgOrNull === null
						|| op.canRunIfHasFirstArgument ->
					{
						// Eliminate it before queueing a work unit if it
						// shouldn't run due to there being a first argument
						// already pre-parsed.
						successors.forEach { successor ->
							stepState.start.workUnitDo {
								runParsingInstructionThen(
									operationInt, stepState.copy(), successor)
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Attempt to consume a token from the source.
	 *
	 * @param stepState
	 *   The [ParsingStepState] that represents the current state of parsing of
	 *   some partial method or macro invocation.
	 * @param tokenMap
	 *   A map from string to message bundle tree, used for parsing tokens when
	 *   in this state.
	 * @param caseInsensitive
	 *   Whether to match the token case-insensitively.
	 */
	private fun attemptToConsumeToken(
		stepState: ParsingStepState,
		tokenMap: A_Map,
		caseInsensitive: Boolean)
	{
		skipWhitespaceAndComments(stepState.start) { afterWhiteSpaceStates ->
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
						val timeBefore = captureNanos()
						val successor = tokenMap.mapAtOrNull(string) ?: continue
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
							token.nextLexingState(),
							stepState.start.clientDataMap)
						val stepStateCopy = stepState.copy {
							start = afterToken
							consumedStaticTokens =
								consumedStaticTokens.append(token)
							consumedAnything = true
						}
						eventuallyParseRestOfSendNode(successor, stepStateCopy)
						val timeAfter = captureNanos()
						val stat =
							if (caseInsensitive) matchTokenInsensitivelyStat
							else matchTokenStat
						stat.record(timeAfter - timeBefore)
					}
					assert(foundOne)
					// Only report if at least one static token had already been
					// consumed.
					if (!recognized &&
						stepState.consumedStaticTokens.isNotEmpty())
					{
						val strings = tokens.mapTo(
							mutableSetOf(),
							when {
								caseInsensitive -> A_Token::lowerCaseString
								else -> A_Token::string
							})
						expectedKeywordsOf(
							stepState.start, tokenMap, caseInsensitive, strings)
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
				start.lexingState,
				null
			) { statesAfterWhitespace ->
				statesAfterWhitespace.forEach { state ->
					state.lexingState.withTokensDo { tokens ->
						tokens.forEach { token ->
							val tokenType = token.tokenType()
							if (tokenType != WHITESPACE && tokenType != COMMENT)
							{
								state.workUnitDo { continuation(token) }
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
	 * @param superexpressions
	 *   The optional [PartialSubexpressionList] used to describe the
	 *   superexpressions of the method parse that has failed a type test.
	 * @param continuation
	 *   What to do once a description of the problem has been produced.
	 */
	private fun describeFailedTypeTestThen(
		actualTypeString: String,
		bundleTree: A_BundleTree,
		superexpressions: PartialSubexpressionList?,
		continuation: (String)->Unit)
	{
		val typeSet = mutableSetOf<A_Type>()
		val typesByPlanString = mutableMapOf<String, MutableSet<A_Type>>()
		bundleTree.allParsingPlansInProgress.forEach { _, submap ->
			submap.forEach { _, plans ->
				plans.forEach { planInProgress ->
					val plan = planInProgress.parsingPlan
					val instructions = plan.parsingInstructions
					val instruction =
						instructions.tupleIntAt(planInProgress.parsingPc)
					val typeIndex =
						TYPE_CHECK_ARGUMENT.typeCheckArgumentIndex(instruction)
					// TODO(MvG) Present the full phrase type if it can be a
					// macro argument.
					val argType =
						constantForIndex(typeIndex).phraseTypeExpressionType
					typeSet.add(argType)
					// Add the type under the given plan *string*, even if it's
					// a different underlying message bundle.
					val typesForPlan = typesByPlanString.computeIfAbsent(
						planInProgress.nameHighlightingPc
					) { mutableSetOf() }
					typesForPlan.add(argType)
				}
			}
		}
		val typeList = typeSet.toList()
		// Generate the type names in parallel.
		stringifyThen(
			compilationContext.runtime,
			compilationContext.textInterface,
			typeList
		) { typeNamesList ->
			assert(typeList.size == typeNamesList.size)
			val typeMap = (typeList zip typeNamesList).toMap()
			// Stitch the type names back onto the plan strings, prior to
			// sorting by type name.
			val entries = typesByPlanString.entries.sortedBy { it.key }
			val string = buildString {
				append("phrase to have a type other than:\n\t\t")
				append(increaseIndentation(actualTypeString, 2))
				append(".\n\tExpecting:")
				for ((planString, types) in entries) {
					append("\n\t\t")
					append(planString)
					append("   ")
					val typeNames = types.stream()
						.map { typeMap[it] }
						.sorted()
						.collect(toList<String>())
					typeNames.joinTo(this) { typeName ->
						increaseIndentation(typeName, 3)
					}
				}
				append("\n\tin:")
				StringBuilder().let { innerBuilder ->
					this@AvailCompiler.describeOn(
						superexpressions, innerBuilder)
					append(increaseIndentation(innerBuilder.toString(), 1))
				}
			}
			continuation(string)
		}
	}

	/**
	 * Execute one non-keyword-parsing instruction, then run the continuation.
	 *
	 * @param instruction
	 *   An [Int] encoding the [ParsingOperation] to execute.
	 * @param stepState
	 *   The [ParsingStepState] that represents the current state of parsing of
	 *   some partial method or macro invocation.
	 * @param successorTree
	 *   The [A_BundleTree]s at which to continue parsing.
	 */
	private fun runParsingInstructionThen(
		instruction: Int,
		stepState: ParsingStepState,
		successorTree: A_BundleTree)
	{
		val op = decode(instruction)
		if (AvailRuntimeConfiguration.debugCompilerSteps)
		{
			if (op.ordinal >= distinctInstructions)
			{
				println(
					"Instr @"
					+ stepState.start.shortString()
					+ ": "
					+ op.name
					+ " ("
					+ operand(instruction)
					+ ") -> "
					+ successorTree)
			}
			else
			{
				println(
					"Instr @"
					+ stepState.start.shortString()
					+ ": "
					+ op.name
					+ " -> "
					+ successorTree)
			}
		}
		val timeBefore = captureNanos()
		op.execute(this, stepState, instruction, successorTree)
		val timeAfter = captureNanos()
		op.parsingStatisticInNanoseconds.record(timeAfter - timeBefore)
	}

	/**
	 * Attempt the specified prefix function.  It may throw an
	 * [AvailRejectedParseException] if a specific parsing problem needs to be
	 * described.
	 *
	 * @param successorTree
	 *   The [A_BundleTree] with which to continue parsing.
	 * @param stepState
	 *   The [ParsingStepState] that represents the current state of parsing of
	 *   some partial method or macro invocation.
	 * @param prefixFunction
	 *   The prefix [A_Function] to invoke.
	 * @param listOfArgs
	 *   The argument [phrases][A_Phrase] to pass to the prefix function.
	 */
	internal fun runPrefixFunctionThen(
		successorTree: A_BundleTree,
		stepState: ParsingStepState,
		prefixFunction: A_Function,
		listOfArgs: List<AvailObject>)
	{
		val code = prefixFunction.code()
		if (!prefixFunction.kind().acceptsListOfArgValues(listOfArgs))
		{
			stepState.start.expected(
				STRONG,
				FormattingDescriber(
					"macro prefix function %s to accept the given argument " +
						"types.",
					code.methodName))
			return
		}
		val fiber = newLoaderFiber(
			prefixFunction.kind().returnType,
			compilationContext.loader)
		{
			val m = code.module
			val modName = if (m.isNil) "nil" else m.shortModuleNameNative
			formatString(
				"Macro prefix %s, in %s:%d",
				code.methodName,
				modName,
				code.codeStartingLineNumber)
		}
		fiber.setGeneralFlag(GeneralFlag.CAN_REJECT_PARSE)
		val withTokens = stepState.start.clientDataMap
			.mapAtPuttingCanDestroy(
				ALL_TOKENS_KEY.atom,
				tupleFromList(stepState.start.lexingState.allTokens),
				false)
			.mapAtPuttingCanDestroy(
				STATIC_TOKENS_KEY.atom,
				tupleFromList(stepState.consumedStaticTokens),
				false)
		var fiberGlobals = fiber.fiberGlobals
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			CLIENT_DATA_GLOBAL_KEY.atom, withTokens.makeImmutable(), true)
		fiber.fiberGlobals = fiberGlobals
		stepState.start.lexingState.setFiberContinuationsTrackingWork(
			fiber,
			{
				// The prefix function ran successfully.
				val replacementClientDataMap =
					fiber.fiberGlobals.mapAt(CLIENT_DATA_GLOBAL_KEY.atom)
				stepState.start =
					stepState.start.withMap(replacementClientDataMap)
				eventuallyParseRestOfSendNode(successorTree, stepState)
			},
			{ e ->
				// The prefix function failed in some way.
				if (e is AvailAcceptedParseException)
				{
					// Prefix functions are allowed to explicitly accept a
					// parse.
					val replacementClientDataMap =
						fiber.fiberGlobals.mapAt(CLIENT_DATA_GLOBAL_KEY.atom)
					stepState.start =
						stepState.start.withMap(replacementClientDataMap)
					eventuallyParseRestOfSendNode(successorTree, stepState)
				}
				if (e is AvailRejectedParseException)
				{
					stepState.start.expected(
						e.level,
						e.rejectionString.asNativeString())
				}
				else
				{
					stepState.start.expected(
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
	 * [method&#32;definitions][MethodDefinitionDescriptor], but also any
	 * semantic restrictions. The semantic restrictions may choose to
	 * [reject&#32;the&#32;parse][P_RejectParsing], indicating that the argument
	 * types are mutually incompatible.  If all semantic restrictions succeed,
	 * invoke onSuccess with the intersection of the produced types and the
	 * applicable method body return types.
	 *
	 * @param bundle
	 *   A [message&#32;bundle][MessageBundleDescriptor].
	 * @param argTypes
	 *   The argument types.
	 * @param state
	 *   The [parser&#32;state][ParserState] after the function evaluates
	 *   successfully.
	 * @param macroOrNil
	 *   A [macro&#32;definition][MacroDescriptor] if this is for a
	 *   macro invocation, otherwise `nil`.
	 * @param onSuccess
	 *   What to do with the strengthened return type.  This may be invoked at
	 *   most once, and only if no semantic restriction rejected the parse.
	 */
	private fun validateArgumentTypes(
		bundle: A_Bundle,
		argTypes: List<A_Type>,
		macroOrNil: A_Macro,
		state: ParserState,
		onSuccess: (A_Type)->Unit)
	{
		argTypes.forEach { it.makeShared() }
		val method = bundle.bundleMethod
		val methodDefinitions = method.definitionsTuple
		val restrictions = method.semanticRestrictions
		// Filter the definitions down to those that are locally most specific.
		// Fail if more than one survives.
		if (methodDefinitions.tupleSize > 0)
		{
			// There are method definitions.
			// Compiler should have assured there were no bottom or
			// top argument expressions.
			assert(argTypes.all { !it.isBottom && !it.isTop })
		}
		// Find all method definitions that could match the argument types.
		// Only consider definitions that are defined in the current module or
		// an ancestor.
		val filteredByTypes =
			if (macroOrNil.isNil) method.filterByTypes(argTypes)
			else listOf(macroOrNil)
		val satisfyingDefinitions = filteredByTypes.filter { definition ->
			val definitionModule = definition.definitionModule()
			definitionModule.isNil
				|| compilationContext.module.hasAncestor(definitionModule)
		}
		if (satisfyingDefinitions.isEmpty())
		{
			state.expected(
				STRONG,
				describeWhyDefinitionsAreInapplicable(
					bundle,
					argTypes,
					if (macroOrNil.isNil) methodDefinitions
					else emptyTuple(),
					if (macroOrNil.isNil) emptyTuple()
					else tuple(macroOrNil),
					compilationContext.module))
			return
		}
		// Compute the intersection of the return types of the possible callees.
		// Macro bodies return phrases, but that's not what we want here.
		var intersection: A_Type = if (macroOrNil.isNil)
		{
			satisfyingDefinitions.fold(TOP.o) { type: A_Type, def ->
				type.typeIntersection(def.bodySignature().returnType)
			}
		}
		else
		{
			// The macro's semantic type (expressionType) is the authoritative
			// type to check against the macro body's actual return phrase's
			// semantic type.  Semantic restrictions may still narrow it below.
			macroOrNil.bodySignature().returnType.phraseTypeExpressionType
		}
		// Determine which semantic restrictions are relevant.
		val restrictionsToTry = mutableListOf<A_SemanticRestriction>()
		restrictions.forEach { restriction ->
			val definitionModule = restriction.definitionModule()
			if (definitionModule.isNil
				|| compilationContext.module.hasAncestor(definitionModule))
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
			onSuccess(intersection)
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
				onSuccess(intersection)
			}
		}
		val intersectAndDecrement = { restrictionType: AvailObject ->
			assert(restrictionType.isType)
			outstandingLock.safeWrite {
				if (failureCount.get() == 0)
				{
					intersection =
						intersection.typeIntersection(restrictionType)
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
				intersectAndDecrement) { e ->
					if (e is AvailAcceptedParseException)
					{
						// This is really a success.
						intersectAndDecrement(TOP.o)
						return@evaluateSemanticRestrictionFunctionThen
					}
					when (e)
					{
						is AvailRejectedParseException -> state.expected(
							e.level,
							e.rejectionString.asNativeString()
								+ " (while parsing send of "
								+ bundle.message
								.atomName.asNativeString()
								+ ")")
						is FiberTerminationException -> state.expected(
							STRONG,
							"semantic restriction not to raise an "
								+ "unhandled exception (while parsing "
								+ "send of "
								+ bundle.message.atomName.asNativeString()
								+ "):\n\t"
								+ e)
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
			}
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
	 *   The method definitions that were visible (defined in the current or an
	 *   ancestor module) but not applicable.
	 * @param macrosTuple
	 *   The [A_Macro] definitions that were visible (defined in the current or
	 *   an ancestor module) but not applicable.  This and the definitionsTuple
	 *   should not both be non-empty.
	 * @param scopeModule
	 *   The [A_Module] for which the message should be tailored, showing only
	 *   content visible within that module and its ancestors.
	 * @return
	 *   A [Describer] able to describe why none of the definitions were
	 *   applicable.
	 */
	private fun describeWhyDefinitionsAreInapplicable(
		bundle: A_Bundle,
		argTypes: List<A_Type>,
		definitionsTuple: A_Tuple,
		macrosTuple: A_Tuple,
		scopeModule: A_Module): Describer
	{
		assert((definitionsTuple.tupleSize > 0)
			xor (macrosTuple.tupleSize > 0))
		return { c ->
			val kindOfDefinition = when
			{
				macrosTuple.tupleSize > 0 -> "macro"
				else -> "method"
			}
			val allVisible = mutableListOf<A_Sendable>()
			definitionsTuple.forEach { def ->
				val definingModule = def.definitionModule()
				if (definingModule.isNil
					|| scopeModule.hasAncestor(definingModule))
				{
					allVisible.add(def)
				}
			}
			macrosTuple.forEach { def ->
				val definingModule = def.definitionModule()
				if (definingModule.isNil
					|| scopeModule.hasAncestor(definingModule))
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
								sig.argsTupleType.typeAtIndex(i)))
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
			val uniqueValues = mutableListOf<A_BasicObject>()
			val valuesToStringify = mutableMapOf<A_BasicObject, Int>()
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
						definition.bodySignature().argsTupleType
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
					bundle.message.atomName,
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
						if (definition.isMethodDefinition())
							definition.bodyBlock().code().codeStartingLineNumber
						else
							"unknown")
					val signatureArgumentsType =
						definition.bodySignature().argsTupleType
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
	 * A complete [send&#32;phrase][SendPhraseDescriptor] has been parsed.
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
	 *   The [list&#32;phrase][ListPhraseDescriptor] that will hold all the
	 *   arguments of the new send phrase.
	 * @param bundle
	 *   The [message&#32;bundle][MessageBundleDescriptor] that identifies the
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
		continuation: (ParserState, A_Phrase)->Unit)
	{
		val method = bundle.bundleMethod
		val definitions = method.definitionsTuple
		val macros = bundle.macrosTuple
		if (definitions.tupleSize + macros.tupleSize == 0)
		{
			stateAfterCall.expected(
				STRONG,
				"there to be a method or macro definition for "
				+ bundle.message
				+ ", but there wasn't")
			return
		}

		// An applicable macro definition (even if ambiguous) prevents this site
		// from being a method invocation.
		var macro: A_Macro = nil
		if (macros.tupleSize > 0)
		{
			// Find all macro definitions that could match the argument phrases.
			// Only consider definitions that are defined in the current module
			// or an ancestor.
			val visibleDefinitions = macros.filter { definition ->
				val definitionModule = definition.definitionModule()
				definitionModule.isNil
					|| compilationContext.module.hasAncestor(definitionModule)
			}
			var errorCode: AvailErrorCode? = null
			if (visibleDefinitions.size == macros.tupleSize)
			{
				// All macro definitions are visible.  Use the lookup tree.
				val matchingMacros = bundle.lookupMacroByPhraseTuple(
					argumentsListNode.expressionsTuple)
				when (matchingMacros.tupleSize)
				{
					1 -> macro = matchingMacros.tupleAt(1)
					0 -> errorCode = E_NO_METHOD_DEFINITION
					else -> errorCode = E_AMBIGUOUS_METHOD_DEFINITION
				}
			}
			else
			{
				// Some macro definitions are not visible.  Search the hard (but
				// hopefully infrequent) way.
				val phraseRestrictions = argumentsListNode.expressionsTuple
					.map { restrictionForConstant(it, BOXED_FLAG) }
				val filtered = visibleDefinitions.filter { macroDefinition ->
					macroDefinition.bodySignature()
						.couldEverBeInvokedWith(phraseRestrictions)
				}

				when (filtered.size)
				{
					1 -> macro = filtered[0]
					0 ->
					{
						// Nothing is visible.
						stateAfterCall.expected(
							WEAK,
							"perhaps some definition of the macro "
								+ bundle.message
								+ " to be visible")
						errorCode = E_NO_METHOD_DEFINITION
						// Fall through.
					}
					else ->
					{
						// Find the most specific macro(s).
						val mostSpecific = filtered.filter { candidate ->
							filtered.none {
								!candidate.equals(it) &&
									candidate.bodySignature()
										.acceptsArgTypesFromFunctionType(
											it.bodySignature())
							}
						}
						assert(mostSpecific.isNotEmpty())
						when (mostSpecific.size)
						{
							1 -> macro = mostSpecific[0]
							else -> errorCode = E_AMBIGUOUS_METHOD_DEFINITION
						}
					}
				}
			}

			if (macro.isNil)
			{
				// Failed lookup.
				when (errorCode)
				{
					E_NO_METHOD_DEFINITION -> { } // fall through
					E_AMBIGUOUS_METHOD_DEFINITION ->
					{
						stateAfterCall.expected(MEDIUM) {
							it("unambiguous definition of macro " +
								bundle.message)
						}
						// Don't try to treat it as a method invocation.
						return
					}
					else ->
					{
						stateAfterCall.expected(MEDIUM) {
							it("successful macro lookup, not: " +
								errorCode!!.name)
						}
						// Don't try to treat it as a method invocation.
						return
					}
				}
				if (definitions.tupleSize == 0)
				{
					// There are only macro definitions, but the arguments were
					// not the right types.
					val phraseTypes = mutableListOf<A_Type>()
					for (argPhrase in argumentsListNode.expressionsTuple)
					{
						phraseTypes.add(instanceTypeOrMetaOn(argPhrase))
					}
					stateAfterCall.expected(
						MEDIUM,
						describeWhyDefinitionsAreInapplicable(
							bundle,
							phraseTypes,
							emptyTuple(),
							macros,
							compilationContext.module))
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
		val argTupleType = argumentsListNode.superUnionType.typeUnion(
			argumentsListNode.phraseExpressionType)
		val argCount = argumentsListNode.expressionsSize
		val argTypes = (1..argCount).map { argTupleType.typeAtIndex(it) }
		// Parsing a macro send must not affect the scope.
		val afterState = stateAfterCall.withMap(stateBeforeCall.clientDataMap)
		// Validate the message send before reifying a send phrase.
		validateArgumentTypes(
			bundle,
			argTypes,
			macro,
			stateAfterCall
		) { expectedYieldType ->
			if (macro.isNil)
			{
				val sendNode = newSendNode(
					tupleFromList(consumedTokens),
					bundle,
					argumentsListNode,
					expectedYieldType)
				afterState.workUnitDo {
					continuation(afterState, sendNode)
				}
				return@validateArgumentTypes
			}
			else
			{
				completedSendNodeForMacro(
					stateAfterCall,
					argumentsListNode,
					bundle,
					consumedTokens,
					macro,
					expectedYieldType
				) { endState, macroPhrase ->
					assert(macroPhrase.isMacroSubstitutionNode)
					continuation(endState, macroPhrase)
				}
			}
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
	 * @param superexpressions
	 *   The enclosing partially-parsed expressions, if any.
	 * @param continuation
	 *   What to do with the argument.
	 */
	internal fun parseSendArgumentWithExplanationThen(
		start: ParserState,
		kindOfArgument: String,
		firstArgOrNull: A_Phrase?,
		canReallyParse: Boolean,
		wrapInLiteral: Boolean,
		superexpressions: PartialSubexpressionList?,
		continuation: (ParserState, A_Phrase)->Unit)
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
				start.workUnitDo {
					continuation(start, wrapAsLiteral(firstArgOrNull))
				}
				return
			}
			val expressionType = firstArgOrNull.phraseExpressionType
			when
			{
				expressionType.isTop ->
					start.expected(WEAK, "leading argument not to be ⊤-valued.")
				expressionType.isBottom ->
					start.expected(WEAK, "leading argument not to be ⊥-valued.")
				else -> start.workUnitDo { continuation(start, firstArgOrNull) }
			}
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
		parseExpressionThen(start, superexpressions) {
			afterArgument, argument ->
			// Only accept a ⊤-valued or ⊥-valued expression if
			// wrapInLiteral is true.
			if (!wrapInLiteral)
			{
				val type = argument.phraseExpressionType
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
								describeOn(superexpressions, this)
							})
					}
					return@parseExpressionThen
				}
			}
			afterArgument.workUnitDo {
				continuation(
					afterArgument,
					if (wrapInLiteral) wrapAsLiteral(argument) else argument)
			}
		}
	}

	/**
	 * Parse an argument in the top-most scope.  This is an important capability
	 * for parsing type expressions, and the macro facility may make good use of
	 * it for other purposes.
	 *
	 * @param stepState
	 *   The [ParsingStepState] that represents the current state of parsing of
	 *   some partial method or macro invocation.
	 * @param successorTree
	 *   The [A_BundleTree] along which to continue parsing if a local
	 *   solution is found.
	 */
	internal fun parseArgumentInModuleScopeThen(
		stepState: ParsingStepState,
		successorTree: A_BundleTree)
	{
		val clientDataInGlobalScope =
			stepState.start.clientDataMap.mapAtPuttingCanDestroy(
				COMPILER_SCOPE_MAP_KEY.atom,
				emptyMap,
				false)
		parseSendArgumentWithExplanationThen(
			stepState.start.withMap(clientDataInGlobalScope),
			"module-scoped argument",
			stepState.firstArgOrNull,
			stepState.firstArgOrNull === null
				&& stepState.initialTokenPosition.lexingState
					!= stepState.start.lexingState,
			false, // Static argument can't be top-valued
			stepState.superexpressions
		) { afterArg, newArg ->
			if (newArg.hasSuperCast)
			{
				afterArg.expected(
					STRONG,
					"global-scoped argument, not supercast")
				return@parseSendArgumentWithExplanationThen
			}
			if (stepState.firstArgOrNull !== null)
			{
				// A leading argument was already supplied.  We couldn't prevent
				// it from referring to variables that were in scope during its
				// parsing, but we can reject it if the leading argument is
				// supposed to be parsed in global scope, which is the case
				// here, and there are references to local variables within the
				// argument's parse tree.
				val usedLocals = usesWhichLocalVariables(newArg)
				if (usedLocals.setSize > 0)
				{
					// A leading argument was supplied which used at least
					// one local.  It shouldn't have.
					afterArg.expected(WEAK) {
						val localNames = usedLocals.map {
							it.token.string().asNativeString()
						}
						it(
							"a leading argument which "
								+ "was supposed to be parsed in "
								+ "module scope, but it referred to "
								+ "some local variables: "
								+ localNames)
					}
					return@parseSendArgumentWithExplanationThen
				}
			}
			val stepStateCopy = stepState.copy {
				start = afterArg.withMap(stepState.start.clientDataMap)
				// We're about to parse an argument, so whatever was in
				// consumedAnything should be moved into
				// consumedAnythingBeforeLatestArgument.
				consumedAnythingBeforeLatestArgument = consumedAnything
				// The argument counts as something that was consumed if
				// it's not a leading argument...
				consumedAnything = firstArgOrNull === null
				firstArgOrNull = null
				// Push the new argument phrase.
				push(newArg)
			}
			eventuallyParseRestOfSendNode(successorTree, stepStateCopy)
		}
	}

	/**
	 * A macro invocation has just been parsed.  Run its body now to produce a
	 * substitute phrase.
	 *
	 * @param stateAfterCall
	 *   The parsing state after the message.
	 * @param argumentsListNode
	 *   The [list&#32;phrase][ListPhraseDescriptor] that will hold all the
	 *   arguments of the new send phrase.
	 * @param bundle
	 *   The [message&#32;bundle][MessageBundleDescriptor] that identifies the
	 *   message to be sent.
	 * @param consumedTokens
	 *   The list of all tokens collected for this send phrase.  This includes
	 *   only those tokens that are operator or keyword tokens that correspond
	 *   with parts of the method name itself, not the arguments.
	 * @param macroDefinitionToInvoke
	 *   The actual [macro&#32;definition][MacroDescriptor] to invoke
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
		macroDefinitionToInvoke: A_Macro,
		expectedYieldType: A_Type,
		continuation: (ParserState, A_Phrase)->Unit)
	{
		val argumentsTuple = argumentsListNode.expressionsTuple
		// Strip off macro substitution wrappers from the arguments.  These were
		// preserved only long enough to test grammatical restrictions.
		val argumentsList = mutableListOf<A_Phrase>()
		for (argument in argumentsTuple)
		{
			argumentsList.add(argument)
		}
		// Capture all tokens that comprised the entire macro send.
		val withTokensAndBundle = stateAfterCall.clientDataMap
			.mapAtPuttingCanDestroy(
				STATIC_TOKENS_KEY.atom, tupleFromList(consumedTokens), false)
			.mapAtPuttingCanDestroy(
				ALL_TOKENS_KEY.atom,
				tupleFromList(stateAfterCall.lexingState.allTokens),
				true)
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
		val clientDataAfterRunning = Mutable<A_Map?>(null)
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
				val adjustedReplacement: A_Phrase = when {
					!replacement.isInstanceOfKind(
						PARSE_PHRASE.mostGeneralType) ->
					{
						stateAfterCall.expected(
							STRONG,
							listOf(replacement)) {
							"Macro body for ${bundle.message} to have " +
								"produced a phrase, not ${it[0]}"
						}
						return@evaluateMacroFunctionThen
					}
					replacement.phraseKindIsUnder(SEND_PHRASE) ->
						newSendNode(
							replacement.tokens,
							replacement.bundle,
							replacement.argumentsListNode,
							replacement.phraseExpressionType
								.typeIntersection(expectedYieldType))
					replacement.phraseExpressionType
							.isSubtypeOf(expectedYieldType) ->
						replacement
					else ->
					{
						stateAfterCall.expected(
							STRONG,
							"macro "
								+ bundle.message.atomName
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
					clientDataAfterRunning.value!!)
				val original = newSendNode(
					tupleFromList(consumedTokens),
					bundle,
					argumentsListNode,
					macroDefinitionToInvoke.bodySignature().returnType)
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
				stateAfter.workUnitDo { continuation(stateAfter, substitution) }
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
	 *   The [LexingState] at which the pragma was found.
	 * @param token
	 *   The token containing the pragma check information.
	 * @param propertyName
	 *   The name of the property that is being checked.
	 * @param propertyValue
	 *   A value that should be checked, somehow, for conformance.
	 * @param success
	 *   What to do after the check completes successfully.
	 */
	internal fun pragmaCheckThen(
		state: LexingState,
		token: A_Token,
		propertyName: String,
		propertyValue: String,
		success: ()->Unit)
	{
		if ("version" != propertyName)
		{
			val viableAssertions = mutableSetOf<String>()
			viableAssertions.add("version")
			state.compilationContext.diagnostics.reportError(
				token.synthesizeCurrentLexingState(),
				"Malformed pragma at %s on line %d:",
				"Expected check pragma to assert one of the following "
					+ "properties: $viableAssertions")
			return
		}
		// Split the versions at commas.
		val versions = propertyValue.split(",").toTypedArray()
		for (i in versions.indices)
		{
			versions[i] = versions[i].trim { it <= ' ' }
		}
		// Put the required versions into a set.
		val requiredVersions = generateSetFrom(versions, ::stringFrom)
		// Ask for the guaranteed versions.
		val activeVersions = generateSetFrom(
			AvailRuntimeConfiguration.activeVersions, ::stringFrom)
		// If the intersection of the sets is empty, then the module and
		// the virtual machine are incompatible.
		if (!requiredVersions.setIntersects(activeVersions))
		{
			state.compilationContext.diagnostics.reportError(
				token.synthesizeCurrentLexingState(),
				"Malformed pragma at %s on line %d:",
				"Module and virtual machine are not compatible; the virtual "
					+ "machine guarantees versions $activeVersions, but the "
					+ "current module requires $requiredVersions")
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
	 *   The [state][LexingState] following a parse of the
	 *   [module&#32;header][ModuleHeader].
	 * @param token
	 *   A token with which to associate the definition of the function. Since
	 *   this is a bootstrap method, it's appropriate to use the string token
	 *   within the pragma for this purpose.
	 * @param methodName
	 *   The name of the primitive method being defined.
	 * @param primitiveName
	 *   The [primitive&#32;name][Primitive.name] of the
	 *   [method][MethodDescriptor] being defined.
	 * @param success
	 *   What to do after the method is bootstrapped successfully.
	 */
	internal fun bootstrapMethodThen(
		state: LexingState,
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
			emptyTuple)
		function.makeShared()
		val send = newSendNode(
			emptyTuple,
			METHOD_DEFINER.bundle,
			newListNode(tuple(nameLiteral, syntheticLiteralNodeFor(function))),
			TOP.o)
		evaluateModuleStatementThen(
			token.synthesizeCurrentLexingState(),
			state,
			send,
			mutableMapOf(),
			success)
	}

	/**
	 * Create a bootstrap primitive [macro][MacroDescriptor]. Use the
	 * primitive's type declaration as the argument types.  If the primitive is
	 * fallible then generate suitable primitive failure code (to invoke the
	 * [CRASH]'s [method's][MethodDescriptor] [bundle][A_BundleTree]).
	 *
	 * @param state
	 *   The [state][LexingState] following a parse of the
	 *   [module&#32;header][ModuleHeader].
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
		state: LexingState,
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
		val functionLiterals = mutableListOf<A_Phrase>()
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
							emptyTuple)))
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
			emptyTuple,
			MACRO_DEFINER.bundle,
			newListNode(
				tuple(
					nameLiteral,
					newListNode(tupleFromList(functionLiterals)),
					bodyLiteral)),
			TOP.o)
		evaluateModuleStatementThen(
			token.synthesizeCurrentLexingState(),
			state,
			send,
			mutableMapOf(),
			success)
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
	 *   The [state][LexingState] following a parse of the
	 *   [module&#32;header][ModuleHeader].
	 * @param token
	 *   A token with which to associate the definition of the lexer function.
	 *   Since this is a bootstrap lexer, it's appropriate to use the string
	 *   token within the pragma for this purpose.
	 * @param lexerAtom
	 *   The name (an [atom][A_Atom]) of the lexer being defined.
	 * @param filterPrimitiveName
	 *   The [name][Primitive.name] of the filter primitive for the lexer being
	 *   defined.
	 * @param bodyPrimitiveName
	 *   The [name][Primitive.name] of the body primitive of the
	 *   [lexer][A_Lexer] being defined.
	 * @param success
	 *   What to do after the method is bootstrapped successfully.
	 */
	internal fun bootstrapLexerThen(
		state: LexingState,
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
			state.compilationContext.diagnostics.reportError(
				token.nextLexingState(),
				"Malformed pragma at %s on line %d:",
				"Lexer pragma's filter function $filterPrimitiveName is not "
					+ "a valid primitive")
			return
		}
		val filterFunctionType = filterPrimitive.blockTypeRestriction()
		if (!filterFunctionType.equals(lexerFilterFunctionType()))
		{
			state.compilationContext.diagnostics.reportError(
				token.nextLexingState(),
				"Malformed pragma at %s on line %d:",
				"Lexer pragma's filter function $filterPrimitiveName does not "
					+ "have a suitable signature")
			return
		}
		val filterFunction = createFunction(
			newPrimitiveRawFunction(
				filterPrimitive,
				compilationContext.module,
				token.lineNumber()),
			emptyTuple)

		// Process the body primitive.
		val bodyPrimitive = primitiveByName(bodyPrimitiveName)
		if (bodyPrimitive === null)
		{
			state.compilationContext.diagnostics.reportError(
				token.nextLexingState(),
				"Malformed pragma at %s on line %d:",
				"Lexer pragma's body function $bodyPrimitiveName is not "
					+ "a valid primitive")
			return
		}
		val bodyFunctionType = bodyPrimitive.blockTypeRestriction()
		if (!bodyFunctionType.equals(lexerBodyFunctionType()))
		{
			state.compilationContext.diagnostics.reportError(
				token.nextLexingState(),
				"Malformed pragma at %s on line %d:",
				"Lexer pragma's body function $bodyPrimitive does not have "
					+ "a suitable signature")
			return
		}
		val bodyFunction = createFunction(
			newPrimitiveRawFunction(
				bodyPrimitive,
				compilationContext.module,
				token.lineNumber()),
			emptyTuple)

		// Process the lexer name.
		val nameLiteral = syntheticLiteralNodeFor(lexerAtom)

		// Build a phrase to define the lexer.
		val send = newSendNode(
			emptyTuple,
			LEXER_DEFINER.bundle,
			newListNode(
				tuple(
					nameLiteral,
					syntheticLiteralNodeFor(filterFunction),
					syntheticLiteralNodeFor(bodyFunction))),
			TOP.o)
		evaluateModuleStatementThen(
			token.synthesizeCurrentLexingState(),
			state,
			send,
			mutableMapOf(),
			success)
	}

	/**
	 * Apply any pragmas detected during the parse of the
	 * [module&#32;header][ModuleHeader].
	 *
	 * @param state
	 *   The [state][LexingState] following a parse of the module header.
	 * @param success
	 *   What to do after the pragmas have been applied successfully.
	 */
	private fun applyPragmasThen(state: LexingState, success: ()->Unit)
	{
		val iterator = moduleHeader.pragmas.iterator()
		compilationContext.loader.setPhase(EXECUTING_FOR_COMPILE)
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
				state.compilationContext.eventuallyDo(state, recurse!!)
			}
		}
		recurse()
	}

	/**
	 * Parse a [module&#32;header][ModuleHeader] from the
	 * [token&#32;list][TokenDescriptor] and apply any side effects. Then
	 * [parse&#32;the&#32;module&#32;body][parseAndExecuteOutermostStatements]
	 * and apply any side effects. Finally, execute the [CompilerDiagnostics]'s
	 * [success&#32;reporter][CompilerDiagnostics.successReporter].
	 */
	private fun parseModuleCompletely()
	{
		parseModuleHeader { afterHeader ->
			compilationContext.progressReporter(
				moduleName,
				source.tupleSize.toLong(),
				afterHeader.position.toLong(),
				afterHeader.lineNumber
			) { null }
			// Run any side effects implied by this module header against
			// the module.
			val errorString = moduleHeader.applyToModule(
				compilationContext.loader)
			if (errorString !== null)
			{
				compilationContext.progressReporter(
					moduleName,
					source.tupleSize.toLong(),
					source.tupleSize.toLong(),
					afterHeader.lineNumber
				) { null }
				afterHeader.expected(STRONG, errorString)
				compilationContext.diagnostics.reportError()
				return@parseModuleHeader
			}
			compilationContext.loader.prepareForCompilingModuleBody()
			applyPragmasThen(afterHeader.lexingState) {
				compilationContext.diagnostics.run {
					positionsToTrack = 3
					silentPositionsToTrack = 3
				}
				parseAndExecuteOutermostStatements(afterHeader)
			}
		}
	}

	/**
	 * Parse a top-level statement, execute it, and repeat if we're not at the
	 * end of the module.
	 *
	 * @param start
	 *   The [parse&#32;state][ParserState] after parsing a
	 *   [module&#32;header][ModuleHeader].
	 */
	private fun parseAndExecuteOutermostStatements(start: ParserState)
	{
		compilationContext.loader.setPhase(COMPILING)
		// Forget any accumulated tokens from previous top-level statements.
		val startLexingState = start.lexingState
		val startWithoutAnyTokens = ParserState(
			LexingState(
				startLexingState.compilationContext,
				startLexingState.position,
				startLexingState.lineNumber,
				emptyList()),
			start.clientDataMap)
		parseOutermostStatement(startWithoutAnyTokens) {
			afterStatement, unambiguousStatement ->
			// The counters must be read in this order for correctness.
			assert(
				compilationContext.workUnitsCompleted
					== compilationContext.workUnitsQueued)

			// Check if we're cleanly at the end.
			if (unambiguousStatement.equals(endOfFileMarkerPhrase))
			{
				reachedEndOfModule(afterStatement)
				return@parseOutermostStatement
			}

			// In case the top level statement is compound, process the
			// base statements individually.
			val simpleStatements = mutableListOf<A_Phrase>()
			unambiguousStatement.statementsDo { simpleStatement ->
				assert(
					simpleStatement.phraseKindIsUnder(STATEMENT_PHRASE))
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
				compilationContext.progressReporter(
					moduleName,
					source.tupleSize.toLong(),
					afterStatement.position.toLong(),
					afterStatement.lineNumber
				) { unambiguousStatement }
				parseAndExecuteOutermostStatements(
					afterStatement.withMap(start.clientDataMap))
			}

			compilationContext.loader.setPhase(EXECUTING_FOR_COMPILE)
			// Run the simple statements in succession.
			val simpleStatementIterator = simpleStatements.iterator()
			val declarationRemap = mutableMapOf<A_Phrase, A_Phrase>()
			var recurse: (()->Unit)? = null
			recurse = recurse@{
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
				val beforeFirstNonwhiteToken =
					afterStatement.lexingState.allTokens.firstOrNull {
						it.tokenType().let { type ->
							type != WHITESPACE
								&& type != COMMENT
								&& type != END_OF_FILE
						}
					}?.synthesizeCurrentLexingState() ?: startLexingState
				evaluateModuleStatementThen(
					beforeFirstNonwhiteToken,
					afterStatement.lexingState,
					statement,
					declarationRemap,
					recurse!!)
			}
			recurse()
		}
	}

	/**
	 * We just reached the end of the module.
	 *
	 * @param afterModule
	 *   The position at the end of the module.
	 */
	private fun reachedEndOfModule(afterModule: ParserState)
	{
		val theLoader = compilationContext.loader
		if (theLoader.pendingForwards.setSize != 0)
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
		compilationContext.diagnostics.successReporter!!()
	}

	/**
	 * Clear any information about potential problems encountered during
	 * parsing.  Reset the problem information to record relative to the given
	 * [ParserState].
	 *
	 * @param positionInSource
	 *   The [LexingState] at the earliest source position for which we should
	 *   record problem information.
	 */
	@Synchronized
	private fun recordExpectationsRelativeTo(positionInSource: LexingState) =
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
		// initialization. We are going to roll back this transaction no matter
		// what happens.
		startModuleTransaction()
		val loader = compilationContext.loader
		loader.prepareForCompilingModuleBody()
		compilationContext.diagnostics.run {
			positionsToTrack = 3
			silentPositionsToTrack = 3
		}
		val clientData = mapFromPairs(
			COMPILER_SCOPE_MAP_KEY.atom,
			emptyMap,
			STATIC_TOKENS_KEY.atom,
			emptyTuple,
			ALL_TOKENS_KEY.atom,
			emptyTuple)
		val start = ParserState(
			LexingState(compilationContext, 1, 1, emptyList()), clientData)
		val solutions = mutableListOf<A_Phrase>()
		compilationContext.noMoreWorkUnits = noMoreWorkUnits@ {
			// The counters must be read in this order for correctness.
			assert(compilationContext.workUnitsCompleted ==
				compilationContext.workUnitsQueued)
			// If no solutions were found, then report an error.
			if (solutions.isEmpty())
			{
				start.expected(STRONG, "an invocation of an entry point")
				compilationContext.diagnostics.reportError()
				return@noMoreWorkUnits
			}
			onSuccess(solutions, this::rollbackModuleTransaction)
		}
		recordExpectationsRelativeTo(start.lexingState)
		if (loader.lexicalScanner().allVisibleLexers.isEmpty())
		{
			start.expected(
				STRONG,
				"module to export at least 1 lexer in order to handle command")
		}
		parseExpressionThen(start, null) { afterExpression, expression ->
			if (expression.equals(endOfFileMarkerPhrase))
			{
				afterExpression.expected(
					STRONG,
					"a valid command, not just whitespace")
				return@parseExpressionThen
			}
			if (expression.hasSuperCast)
			{
				afterExpression.expected(
					STRONG,
					"a valid command, not a supercast")
				return@parseExpressionThen
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
					afterExpression.expected(STRONG, "end of command")
				}
			}
		}
	}

	/**
	 * Process a header that has just been parsed.
	 *
	 * @param headerPhrase
	 *   The invocation of [MODULE_HEADER] that was just parsed.
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
		assert(headerPhrase.apparentSendName.equals(MODULE_HEADER.atom))
		val args = convertHeaderPhraseToValue(headerPhrase.argumentsListNode)
		assert(args.tupleSize == 6)
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
		if (optionalVersionsPart.tupleSize > 0)
		{
			assert(optionalVersionsPart.tupleSize == 1)
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
			assert(importKindToken.isInstanceOfKind(TOKEN.o))
			val importKind = importKindToken.literal()
			val importKindInt = importKind.extractInt
			assert(importKindInt in 1 .. 2)
			val isExtension = importKindInt == 1

			for ((
				importedModuleToken,
				optionalImportVersions,
				optionNameImports) in importEntries)
			{
				val importedModuleName = stringFromToken(importedModuleToken)
				assert(optionalImportVersions.isTuple)

				var importVersions = emptySet
				if (optionalImportVersions.tupleSize > 0)
				{
					assert(optionalImportVersions.tupleSize == 1)
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

				var importedNames = emptySet
				var importedRenames = emptyMap
				var importedExcludes = emptySet
				var wildcard = true

				// <filterEntries, finalEllipsis>?
				if (optionNameImports.tupleSize > 0)
				{
					assert(optionNameImports.tupleSize == 1)
					val namesPart = optionNameImports.tupleAt(1)
					val (filterEntries, finalEllipsisToken) = namesPart
					for ((negationToken, nameToken, optionalRename)
						in filterEntries)
					{
						val negation = negationToken.literal().extractBoolean
						val name = stringFromToken(nameToken)
						when
						{
							optionalRename.tupleSize > 0 ->
							{
								// Process a renamed import.
								assert(optionalRename.tupleSize == 1)
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
					wildcard = finalEllipsisToken.literal().extractBoolean
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
		if (optionalNamesPart.tupleSize > 0)
		{
			assert(optionalNamesPart.tupleSize == 1)
			for (nameToken in optionalNamesPart.tupleAt(1))
			{
				val nameString = stringFromToken(nameToken)
				if (moduleHeader.exportedNames.contains(nameString))
				{
					compilationContext.diagnostics.reportError(
						nameToken.nextLexingState(),
						"Duplicate declared name detected at %s on line %d:",
						"Declared name $nameString should be unique")
					return false
				}
				moduleHeader.exportedNames.add(nameString)
			}
		}

		// Entries section
		if (optionalEntriesPart.tupleSize > 0)
		{
			assert(optionalEntriesPart.tupleSize == 1)
			for (entryToken in optionalEntriesPart.tupleAt(1))
			{
				moduleHeader.entryPoints.add(stringFromToken(entryToken))
			}
		}

		// Pragmas section
		if (optionalPragmasPart.tupleSize > 0)
		{
			assert(optionalPragmasPart.tupleSize == 1)
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
			emptyMap,
			STATIC_TOKENS_KEY.atom,
			emptyMap,
			ALL_TOKENS_KEY.atom,
			emptyTuple)
		val state = ParserState(
			LexingState(compilationContext, 1, 1, emptyList()), clientData)

		recordExpectationsRelativeTo(state.lexingState)

		// Parse an invocation of the special module header macro.
		parseOutermostStatement(state) { endState, headerPhrase ->
			if (headerPhrase.phraseKindIsUnder(MARKER_PHRASE))
			{
				// It made it to the end of the file.  This mechanism is
				// used to determine when we've already parsed the last
				// top-level statement of a module, when only whitespace and
				// comments remain.  Not appropriate for a module header.
				compilationContext.diagnostics.reportError(
					endState.lexingState,
					"Unexpectedly reached end of file without "
						+ "encountering module header.",
					"")
				return@parseOutermostStatement
			}
			if (!headerPhrase.phraseKindIsUnder(
					EXPRESSION_AS_STATEMENT_PHRASE)
				|| !headerPhrase.apparentSendName.equals(
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
						endState.lexingState,
						"Expected module header, but found this "
							+ "phrase instead: %s",
						headerPhraseAsString)
				}
				return@parseOutermostStatement
			}
			try
			{
				if (processHeaderMacro(headerPhrase.expression, endState))
				{
					onSuccess(endState)
				}
			}
			catch (e: Exception)
			{
				// TODO we don't know which thing failed here?
				compilationContext.diagnostics.reportError(
					endState.lexingState,
					"Unexpected exception encountered while processing "
						+ "module header (ends at %s, line %d):",
					trace(e))
			}
		}
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
	 * @param superexpressions
	 *   The enclosing partially-parsed expressions, if any.
	 * @param originalContinuation
	 *   What to do with the expression.
	 */
	private fun parseExpressionThen(
		start: ParserState,
		superexpressions: PartialSubexpressionList?,
		originalContinuation: (ParserState, A_Phrase)->Unit)
	{
		// The first time we parse at this position the fragmentCache will have
		// no knowledge about it.
		val rendezvous = fragmentCache.getRendezvous(start)
		@Suppress("GrazieInspection")
		if (!rendezvous.getAndSetStartedParsing())
		{
			// We're the (only) cause of the transition from hasn't-started to
			// has-started.  Suppress reporting if there are no superexpressions
			// being parsed, or if we're at the root of the bundle tree.
			val isRoot = superexpressions === null
				|| superexpressions.bundleTree.equals(
					compilationContext.loader.rootBundleTree())
			start.expected(if (isRoot) SILENT else WEAK) {
				val builder = StringBuilder()
				builder.append("an expression for (at least) this reason:")
				describeOn(superexpressions, builder)
				it(builder.toString())
			}
			start.workUnitDo {
				parseExpressionUncachedThen(
					start, superexpressions, rendezvous::addSolution)
			}
		}
		start.workUnitDo { rendezvous.addAction(originalContinuation) }
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
	private fun parseOutermostStatement(
		start: ParserState,
		continuation: (ParserState, A_Phrase)->Unit)
	{
		// If a parsing error happens during parsing of this outermost
		// statement, only show the section of the file starting here.
		recordExpectationsRelativeTo(start.lexingState)
		tryIfUnambiguousThen(start, continuation)
	}

	/**
	 * Parse an expression, without directly using the [fragmentCache].
	 *
	 * @param start
	 *   Where to start parsing.
	 * @param superexpressions
	 *   The enclosing partially-parsed expressions, if any.
	 * @param continuation
	 *   What to do with the expression.
	 */
	private fun parseExpressionUncachedThen(
		start: ParserState,
		superexpressions: PartialSubexpressionList?,
		continuation: (ParserState, A_Phrase)->Unit)
	{
		parseLeadingKeywordSendThen(start, superexpressions) {
			endState, phrase ->
			parseOptionalLeadingArgumentSendAfterThen(
				start, endState, phrase, superexpressions, continuation)
		}
	}

	/**
	 * A helper method to queue a parsing activity for continuing to parse a
	 * [send&#32;phrase][SendPhraseDescriptor].
	 *
	 * @param bundleTree
	 *   The current [A_BundleTree] being applied.
	 * @param stepState
	 *   Information about the current partially-constructed message or macro
	 *   send that has not yet been completed.
	 */
	internal fun eventuallyParseRestOfSendNode(
		bundleTree: A_BundleTree,
		stepState: ParsingStepState)
	{
		stepState.start.workUnitDo {
			parseRestOfSendNode(bundleTree, stepState)
		}
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
				compilationContext.module.importedNames
			else
				compilationContext.module.privateNames
		var namesByModule = emptyMap
		sourceNames.forEach { _, atoms ->
			atoms.forEach { atom ->
				namesByModule = namesByModule.mapAtReplacingCanDestroy(
					atom.issuingModule, emptySet, true
				) { _, set ->
					set.setWithElementCanDestroy(atom, true)
				}
			}
		}
		var completeModuleNames = emptySet
		var leftovers = emptySet
		namesByModule.forEach { module, names ->
			if (!module.equals(compilationContext.module)
				&& module.exportedNames.equals(names))
			{
				// All published names were imported from that module, which
				// is a common case.
				completeModuleNames =
					completeModuleNames.setWithElementCanDestroy(
						module.moduleName, true)
			}
			else
			{
				leftovers = leftovers.setUnionCanDestroy(names, true)
			}
		}
		if (completeModuleNames.setSize > 0)
		{
			val send = newSendNode(
				emptyTuple,
				PUBLISH_ALL_ATOMS_FROM_OTHER_MODULE.bundle,
				newListNode(
					tuple(
						syntheticLiteralNodeFor(
							completeModuleNames,
							stringFrom("(complete module imports)")),
						syntheticLiteralNodeFor(
							objectFromBoolean(isPublic)))),
				TOP.o)
			val function = createFunctionForPhrase(
				send, compilationContext.module, 0)
			privateSerializeFunction(function.makeImmutable())
		}
		if (leftovers.setSize > 0)
		{
			// Deal with every atom that was not part of a complete import of
			// its defining module.
			val send = newSendNode(
				emptyTuple,
				PUBLISH_ATOMS.bundle,
				newListNode(
					tuple(
						syntheticLiteralNodeFor(
							leftovers,
							stringFrom("(${leftovers.setSize} atoms)")),
						syntheticLiteralNodeFor(objectFromBoolean(isPublic)))),
				TOP.o)
			val function = createFunctionForPhrase(
				send, compilationContext.module, 0)
			function.makeImmutable()
			privateSerializeFunction(function)
		}
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
		 * specified [module&#32;name][ModuleName].
		 *
		 * @param resolvedName
		 *   The [resolved&#32;name][ResolvedModuleName] of the
		 *   [module][ModuleDescriptor] to compile.
		 * @param textInterface
		 *   The [text&#32;interface][TextInterface] for any [fiber][A_Fiber]
		 *   started by the new compiler.
		 * @param pollForAbort
		 *   A function that indicates whether to abort.
		 * @param reporter
		 *   The [CompilerProgressReporter] used to report progress.
		 * @param afterFail
		 *   What to do after a failure that the
		 *   [problem&#32;handler][ProblemHandler] does not choose to continue.
		 * @param problemHandler
		 *   A problem handler.
		 * @param succeed
		 *   What to do with the resultant compiler in the event of success.
		 *   This is a continuation that accepts the new compiler.
		 */
		fun create(
			resolvedName: ResolvedModuleName,
			runtime: AvailRuntime,
			textInterface: TextInterface,
			pollForAbort: ()->Boolean,
			reporter: CompilerProgressReporter,
			afterFail: ()->Unit,
			problemHandler: ProblemHandler,
			succeed: (AvailCompiler)->Unit)
		{
			extractSourceThen(resolvedName, runtime, afterFail, problemHandler) {
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
		 * by the fully-qualified [module&#32;name][ModuleName].
		 *
		 * @param resolvedName
		 *   The [resolved&#32;name][ResolvedModuleName] of the module.
		 * @param fail
		 *   What to do in the event of a failure that the
		 *   [problem&#32;handler][ProblemHandler] does not wish to continue.
		 * @param problemHandler
		 *   A problem handler.
		 * @param withSource
		 *   What to do after the source module has been completely read.
		 *   Accepts the source text of the module.
		 */
		private fun extractSourceThen(
			resolvedName: ResolvedModuleName,
			runtime: AvailRuntime,
			fail: ()->Unit,
			problemHandler: ProblemHandler,
			withSource: (String)->Unit)
		{
			val ref = resolvedName.resolverReference
			val decoder = StandardCharsets.UTF_8.newDecoder()
			decoder.onMalformedInput(CodingErrorAction.REPLACE)
			decoder.onUnmappableCharacter(CodingErrorAction.REPLACE)
			ref.readFile(
				false,
				{ content, _ ->
					try
					{
						val source =
							decoder.decode(ByteBuffer.wrap(content)).toString()
						runtime.execute(compilerPriority)
						{
							withSource(source)
						}
					}
					catch (e: Throwable)
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
				})
			{ code, ex ->
				val problem = object : Problem(
					resolvedName,
					1,
					0,
					EXTERNAL,
					"Unable to open source module \"{0}\" [{1}]: {2}: {3}",
					resolvedName,
					ref.uri,
					code,
					ex?.localizedMessage ?: "no exception")
				{
					override fun abortCompilation()
					{
						fail()
					}
				}
				problemHandler.handle(problem)
			}
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
				assert(child.isInstanceOfKind(PARSE_PHRASE.mostGeneralType))
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
				if (phrase1.value.phraseKind != phrase2.value.phraseKind)
				{
					// The phrases are different kinds, so present them as
					// what's different.
					return
				}
				val isBlock = phrase1.value.phraseKindIsUnder(BLOCK_PHRASE)
				if (phrase1.value.isMacroSubstitutionNode
					&& phrase2.value.isMacroSubstitutionNode)
				{
					if (phrase1.value.macroOriginalSendNode.bundle.equals(
							phrase2.value.macroOriginalSendNode.bundle))
					{
						// Two occurrences of the same macro.  Figure out
						// whether to drill into the original or the transformed
						// phrases.
						if (!isBlock
							&& phrase1.value.macroOriginalSendNode.equals(
								phrase2.value.macroOriginalSendNode))
						{
							// The originals were the same.  Drill into the
							// transformed phrases.
							phrase1.update { outputPhrase }
							phrase2.update { outputPhrase }
							continue
						}
						else
						{
							// The originals differed.  Drill into them.
							phrase1.update { macroOriginalSendNode }
							phrase2.update { macroOriginalSendNode }
							continue
						}
					}
					// Otherwise, the macros are different, and we should stop.
					return
				}
				if (phrase1.value.isMacroSubstitutionNode
					|| phrase2.value.isMacroSubstitutionNode)
				{
					// They aren't both macros, but one is, so they're different.
					return
				}
				if (phrase1.value.phraseKindIsUnder(SEND_PHRASE)
					&& !phrase1.value.bundle.equals(phrase2.value.bundle))
				{
					// They're sends of different messages, so don't go any
					// deeper.
					return
				}
				// Some DSLs produce literal phrases whose values are phrases.
				// If that's the case here, drill into the inner phrase.
				if (phrase1.value.phraseKindIsUnder(LITERAL_PHRASE))
				{
					val literal1 = phrase1.value.token.literal()
					val literal2 = phrase2.value.token.literal()
					if (literal1.isInstanceOfKind(
							PARSE_PHRASE.mostGeneralType)
						&& literal2.isInstanceOfKind(
							PARSE_PHRASE.mostGeneralType))
					{
						phrase1.value = literal1
						phrase2.value = literal2
						continue
					}
				}
				val parts1 = mutableListOf<A_Phrase>()
				phrase1.value.childrenDo(parts1::add)
				val parts2 = mutableListOf<A_Phrase>()
				phrase2.value.childrenDo(parts2::add)
				if (parts1.size != parts2.size && !isBlock)
				{
					// Different structure at this level.
					return
				}
				val differentIndices = mutableListOf<Int>()
				for (i in 0 until min(parts1.size, parts2.size))
				{
					if (!parts1[i].equals(parts2[i]))
					{
						differentIndices.add(i)
					}
				}
				if (differentIndices.size == 0)
				{
					// There were no differences, so we're at the problem.
					return
				}
				// Drill into the *FIRST* part that differs.  Otherwise the
				// expression would be too big to figure out.  This will get us
				// to a very specific subexpression that contributes to the
				// ambiguity, which is a better diagnostic than the smallest
				// phrase that contains *all* of the ambiguities.
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

		/** Statistic for matching an exact token. */
		private val matchTokenStat = Statistic(
			RUNNING_PARSING_INSTRUCTIONS,
			"Match particular token")

		/** Statistic for matching a token case-insensitively. */
		private val matchTokenInsensitivelyStat = Statistic(
			RUNNING_PARSING_INSTRUCTIONS,
			"Match insensitive token")

		/** The [LookupStatistics] for type-checking an argument. */
		private val typeCheckArgumentStat = LookupStatistics(
			"Type-check argument",
			TYPE_CHECKING_FOR_PARSER)

		/** Marker phrase to signal cleanly reaching the end of the input. */
		private val endOfFileMarkerPhrase =
			newMarkerNode(
				stringFrom("End of file marker"),
				TOP.o
			).makeShared()

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
			skipWhitespaceAndComments(start, AtomicBoolean(false)) { list ->
				val old = ran.getAndSet(true)
				assert(!old) { "Completed skipping whitespace twice." }
				continuation(list)
			}
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
			ambiguousWhitespace: AtomicBoolean,
			continuation: (List<ParserState>)->Unit)
		{
			if (ambiguousWhitespace.get())
			{
				// Should probably be queued instead of called directly.
				continuation(emptyList())
				return
			}
			start.lexingState.withTokensDo { tokens ->
				val toSkip = mutableListOf<A_Token>()
				val toKeep = mutableListOf<A_Token>()
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
									&& token.string().tupleSize < 50)
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
									&& token.string().tupleSize < 100)
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
											+ token.string().tupleSize
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
							MEDIUM,
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
						ambiguousWhitespace,
						continuation)
					return@withTokensDo
				}
				// Rarer, more complicated cases with at least two
				// interpretations, at least one of which is whitespace/comment.
				val result = mutableListOf<ParserState>()
				if (toKeep.size > 0)
				{
					// There's at least one non-whitespace token present at
					// start.
					result.add(start)
				}
				val lock = ReentrantLock()
				var countdown = toSkip.size
				for (tokenToSkip in toSkip)
				{
					// Common case of an unambiguous whitespace/comment token.
					val after = ParserState(
						tokenToSkip.nextLexingState(), start.clientDataMap)
					skipWhitespaceAndComments(after, ambiguousWhitespace) {
							partialList ->
						lock.withLock {
							result.addAll(partialList)
							countdown--
							assert(countdown >= 0)
							if (countdown == 0)
							{
								start.lexingState.workUnitDo {
									continuation(result)
								}
							}
						}
					}
				}
			}
		}

		/**
		 * Transform the argument, a [phrase][A_Phrase], into a
		 * [literal&#32;phrase] whose value is the original phrase. If the given
		 * phrase is a [macro&#32;substitution&#32;phrase] then extract its
		 * [A_Phrase.apparentSendName], strip off the macro substitution, wrap
		 * the resulting expression in a literal phrase, then re-apply the same
		 * apparentSendName to the new literal phrase to produce another macro
		 * substitution phrase.
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
					phrase.macroOriginalSendNode,
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
			return when (phrase.phraseKind)
			{
				LITERAL_PHRASE -> phrase.token as AvailObject
				LIST_PHRASE, PERMUTED_LIST_PHRASE ->
				{
					val expressions = phrase.expressionsTuple
					generateObjectTupleFrom(expressions.tupleSize) { index ->
						convertHeaderPhraseToValue(expressions.tupleAt(index))
					}
				}
				MACRO_SUBSTITUTION_PHRASE ->
					convertHeaderPhraseToValue(phrase.stripMacro)
				else ->
				{
					throw RuntimeException(
						"Unexpected phrase type in header: "
							+ phrase.phraseKind.name)
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
			assert(token.isInstanceOfKind(TOKEN.o))
			val innerToken = token.literal()
			val literal = innerToken.literal()
			assert(literal.isInstanceOfKind(stringType))
			return literal
		}

		/**
		 * Answer the [set][SetDescriptor] of [declaration&#32;phrases] which
		 * are used by this parse tree but are locally declared (i.e., not at
		 * global module scope).
		 *
		 * @param phrase
		 *   The phrase to recursively examine.
		 * @return
		 *   The set of the local declarations that were used in the phrase.
		 */
		private fun usesWhichLocalVariables(phrase: A_Phrase): A_Set
		{
			var usedDeclarations = emptySet
			phrase.childrenDo { childPhrase ->
				if (childPhrase.isInstanceOfKind(
						VARIABLE_USE_PHRASE.mostGeneralType))
				{
					val declaration = childPhrase.declaration
					if (!declaration.declarationKind().isModuleScoped)
					{
						usedDeclarations =
							usedDeclarations.setWithElementCanDestroy(
								declaration, true)
					}
				}
			}
			return usedDeclarations
		}
	}
}
