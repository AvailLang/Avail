/*
 * CompilationContext.kt
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

import avail.AvailRuntime
import avail.AvailRuntime.Companion.currentRuntime
import avail.AvailRuntime.HookType.DEFAULT_STYLER
import avail.AvailRuntimeConfiguration.debugStyling
import avail.builder.ModuleName
import avail.builder.ResolvedModuleName
import avail.compiler.CompilationContext.StylingCompletionState.NotStyling
import avail.compiler.CompilationContext.StylingCompletionState.StylingAndWaiting
import avail.compiler.CompilationContext.StylingCompletionState.StylingNotWaiting
import avail.compiler.problems.CompilerDiagnostics
import avail.compiler.problems.Problem
import avail.compiler.problems.ProblemHandler
import avail.compiler.problems.ProblemType
import avail.compiler.problems.ProblemType.EXECUTION
import avail.compiler.problems.ProblemType.INTERNAL
import avail.compiler.scanning.LexingState
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.issuingModule
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.fiber.A_Fiber
import avail.descriptor.fiber.A_Fiber.Companion.fiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.fiberHelper
import avail.descriptor.fiber.A_Fiber.Companion.setSuccessAndFailure
import avail.descriptor.fiber.FiberDescriptor.Companion.compilerPriority
import avail.descriptor.fiber.FiberDescriptor.Companion.newLoaderFiber
import avail.descriptor.fiber.FiberDescriptor.Companion.newStylerFiber
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.originatingPhrase
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunctionForPhrase
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Method.Companion.methodStylers
import avail.descriptor.methods.A_Styler
import avail.descriptor.methods.A_Styler.Companion.function
import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.allAncestors
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.descriptor.module.A_Module.Companion.phrasePathRecord
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.parsing.A_Lexer.Companion.lexerMethod
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.apparentSendName
import avail.descriptor.phrases.A_Phrase.Companion.applyStylesThen
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.bundle
import avail.descriptor.phrases.A_Phrase.Companion.childrenDo
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.macroOriginalSendNode
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKind
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokenIndicesInName
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.PhraseDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tokens.A_Token
import avail.descriptor.tokens.A_Token.Companion.pastEnd
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_String.SurrogateIndexConverter
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.systemStyleForType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.ARGUMENT_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.ASSIGNMENT_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.BLOCK_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.DECLARATION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.FIRST_OF_SEQUENCE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LABEL_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LOCAL_CONSTANT_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LOCAL_VARIABLE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MACRO_SUBSTITUTION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MARKER_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MODULE_CONSTANT_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.MODULE_VARIABLE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PERMUTED_LIST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PRIMITIVE_FAILURE_REASON_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.REFERENCE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEQUENCE_AS_EXPRESSION_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SEQUENCE_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.SUPER_CAST_PHRASE
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.VARIABLE_USE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.CHARACTER
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailEmergencyExitException
import avail.exceptions.AvailRuntimeException
import avail.interpreter.effects.LoadingEffect
import avail.interpreter.execution.AvailLoader
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelOne.L1Decompiler
import avail.interpreter.levelOne.L1InstructionWriter
import avail.interpreter.levelOne.L1Operation
import avail.io.TextInterface
import avail.persistence.cache.Repository.PhraseNode
import avail.persistence.cache.Repository.PhraseNode.PhraseNodeToken
import avail.serialization.Serializer
import avail.utility.notNullAnd
import avail.utility.parallelDoThen
import avail.utility.stackToString
import org.availlang.persistence.IndexedFile
import java.lang.String.format
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A `CompilationContext` lasts for a module's entire compilation
 * activity.
 *
 * @property moduleHeader
 *   The header information for the current module being parsed.
 * @property module
 *   The Avail [module][ModuleDescriptor] undergoing compilation.
 * @property source
 *   The source text of the Avail [module][ModuleDescriptor] undergoing
 *   compilation.
 * @property textInterface
 *   The [text&#32;interface][TextInterface] for any [fibers][A_Fiber] started
 *   by this [compiler][AvailCompiler].
 * @property progressReporter
 *   The [CompilerProgressReporter] that reports compilation progress at various
 *   checkpoints. It accepts the [name][ResolvedModuleName] of the
 *   [module][ModuleDescriptor] undergoing [compilation][AvailCompiler], the
 *   line number on which the last complete statement concluded, the position of
 *   the ongoing parse (in bytes), and the size of the module (in bytes).
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Create a `CompilationContext` for compiling an [A_Module].
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
 *   [name][ModuleName] of the [module][ModuleDescriptor] undergoing
 *   [compilation][AvailCompiler], the line number on which the last complete
 *   statement concluded, the position of the ongoing parse (in bytes), and the
 *   size of the module (in bytes).
 * @param problemHandler
 *   The [ProblemHandler] used for reporting compilation problems.
 */
class CompilationContext constructor(
	val moduleHeader: ModuleHeader?,
	val module: A_Module,
	internal val source: A_String,
	val textInterface: TextInterface,
	pollForAbort: () -> Boolean,
	val progressReporter: CompilerProgressReporter,
	problemHandler: ProblemHandler)
{
	/**
	 * The [CompilerDiagnostics] that tracks potential errors during
	 * compilation.
	 */
	val diagnostics = CompilerDiagnostics(
		source, moduleName, pollForAbort, problemHandler)

	/**
	 * A tool for converting character positions between Avail's Unicode strings
	 * and Java/Kotlin's UTF-16 representation.
	 */
	val surrogateIndexConverter: SurrogateIndexConverter
		get() = diagnostics.surrogateIndexConverter

	/**
	 * The [AvailRuntime] for the compiler. Since a compiler cannot migrate
	 * between two runtime environments, it is safe to cache it for efficient
	 * access.
	 */
	val runtime: AvailRuntime = currentRuntime()

	/**
	 * The [loader][AvailLoader] created and operated by this
	 * [compiler][AvailCompiler] to facilitate the loading of
	 * [modules][ModuleDescriptor].
	 */
	val loader = AvailLoader(runtime, module, textInterface).apply {
		manifestEntries = mutableListOf()
	}

	/** The number of work units that have been queued. */
	private val atomicWorkUnitsQueued = AtomicLong(0)

	/** The number of work units that have been completed. */
	private val atomicWorkUnitsCompleted = AtomicLong(0)

	/**
	 * What to do when there are no more work units.
	 */
	@Volatile
	var noMoreWorkUnits: (()->Unit)? = null
		set(newNoMoreWorkUnits)
		{
			assert(newNoMoreWorkUnits === null
				!= (this.noMoreWorkUnits === null)) {
				"noMoreWorkUnits must transition to or from null"
			}
			if (Interpreter.debugWorkUnits)
			{
				val wasNull = this.noMoreWorkUnits === null
				val isNull = newNoMoreWorkUnits === null
				println(
					(if (isNull) "\nClear" else "\nSet")
					+ " noMoreWorkUnits (was "
					+ if (wasNull) "null)" else "non-null)")

				val builder = StringBuilder()
				val e = Throwable().fillInStackTrace()
				builder.append(e.stackToString)
				val trace = builder.toString().replace(
					"\\A.*?\\R((.*?\\R){6})(.|\\R)*?\\z".toRegex(), "$1")
				logWorkUnits(
					"SetNoMoreWorkUnits:\n\t" + trace.trim { it <= ' ' })
			}
			if (newNoMoreWorkUnits === null)
			{
				field = null
			}
			else
			{
				// Wrap the provided function with safety and logging.
				val ran = AtomicBoolean(false)
				field = {
					assert(!ran.getAndSet(true)) {
						"Attempting to invoke the same noMoreWorkUnits twice"
					}
					if (Interpreter.debugWorkUnits)
					{
						logWorkUnits("Running noMoreWorkUnits")
					}
					newNoMoreWorkUnits()
				}
			}
		}

	/** The output stream on which the serializer writes. */
	val serializerOutputStream = IndexedFile.ByteArrayOutputStream(1000)

	/**
	 * The serializer that captures the sequence of bytes representing the
	 * module during compilation.
	 */
	internal val serializer = Serializer(serializerOutputStream, module)

	/** The cached module name. */
	@Volatile
	private var debugModuleName: String? = null

	/**
	 * The fully-qualified name of the [module][ModuleDescriptor] undergoing
	 * compilation.
	 */
	internal val moduleName get() =
		ModuleName(module.moduleNameNative)

	/** The current number of work units that have been queued. */
	val workUnitsQueued get() = atomicWorkUnitsQueued.get()

	/** The current number of work units that have been completed. */
	val workUnitsCompleted get() = atomicWorkUnitsCompleted.get()

	/**
	 * Record the fact that this token was encountered while parsing the current
	 * top-level statement.
	 *
	 * @param token
	 *   The token that was encountered.
	 */
	fun recordToken(token: A_Token)
	{
		diagnostics.recordToken(token)
	}

	/**
	 * Attempt the zero-argument continuation. The implementation is free to
	 * execute it now or to put it in a bag of continuations to run later *in an
	 * arbitrary order*. There may be performance and/or scale benefits to
	 * processing entries in FIFO, LIFO, or some hybrid order, but the
	 * correctness is not affected by a choice of order. The implementation may
	 * run the expression in parallel with the invoking thread and other such
	 * expressions.
	 *
	 * @param lexingState
	 *   The [LexingState] for which to report problems.
	 * @param continuation
	 *   What to do at some point in the future.
	 */
	fun eventuallyDo(lexingState: LexingState, continuation: () -> Unit)
	{
		runtime.execute(compilerPriority) {
			try
			{
				continuation()
			}
			catch (e: Exception)
			{
				reportInternalProblem(
					lexingState.lineNumber,
					lexingState.position, e)
			}
		}
	}

	/**
	 * Lazily extract the module name, and prefix all relevant debug lines with
	 * it.
	 *
	 * @param debugString
	 *   The string to log, with the module name prefixed on each line.
	 */
	private fun logWorkUnits(debugString: String)
	{
		var moduleName = debugModuleName
		if (moduleName === null)
		{
			moduleName = this.moduleName.localName
			debugModuleName = moduleName
		}
		val builder = StringBuilder()
		for (line in debugString.split("\n"))
		{
			builder.append(moduleName)
			builder.append("   ")
			builder.append(line)
			builder.append('\n')
		}
		print(builder)
	}

	/**
	 * Start `N` work units, which are about to be queued.
	 *
	 * @param countToBeQueued
	 *   The number of new work units that are being queued.
	 */
	fun startWorkUnits(countToBeQueued: Int)
	{
		assert(noMoreWorkUnits !== null)
		assert(countToBeQueued > 0)
		val queued = atomicWorkUnitsQueued.addAndGet(countToBeQueued.toLong())
		if (Interpreter.debugWorkUnits)
		{
			val completed = workUnitsCompleted
			val builder = StringBuilder()
			val e = Throwable().fillInStackTrace()
			builder.append(e.stackToString)
			var lines: Array<String?> =
				builder.toString().split("\n".toRegex(), 7).toTypedArray()
			if (lines.size > 6)
			{
				lines = lines.copyOf(lines.size - 1)
			}
			logWorkUnits(
				"Starting work unit: queued = "
				+ queued
				+ ", completed = "
				+ completed
				+ " (delta="
				+ (queued - completed)
				+ ')'.toString()
				+ (if (countToBeQueued > 1)
					" (bulk = +$countToBeQueued)"
				else
					"")
				+ "\n\t"
				+ lines.joinToString("\n").trim { it <= ' ' })
		}
		if (logger.isLoggable(Level.FINEST))
		{
			logger.log(
				Level.FINEST,
				format(
					"Started work unit: %d/%d%n",
					workUnitsCompleted,
					workUnitsQueued))
		}
	}

	/**
	 * Construct and answer a function that wraps the specified continuation in
	 * logic that will increment the
	 * [count&#32;of&#32;completed&#32;work&#32;units][workUnitsCompleted] and
	 * potentially call the [unambiguous&#32;statement][noMoreWorkUnits].
	 *
	 * @param ArgType
	 *   The type of value that will be passed to the continuation.
	 * @param lexingState
	 *   The [LexingState] for which to report problems.
	 * @param optionalSafetyCheck
	 *   Either `null` or an [AtomicBoolean] which must transition from false to
	 *   true only once.
	 * @param continuation
	 *   What to do as a work unit.
	 * @return
	 *   A new continuation. It accepts an argument of some kind, which will be
	 *   passed forward to the argument continuation.
	 */
	fun <ArgType> workUnitCompletion(
		lexingState: LexingState,
		optionalSafetyCheck: AtomicBoolean?,
		continuation: (ArgType)->Unit
	): (ArgType)->Unit
	{
		assert(noMoreWorkUnits !== null)
		val hasRunSafetyCheck = optionalSafetyCheck ?: AtomicBoolean(false)
		if (Interpreter.debugWorkUnits)
		{
			logWorkUnits(
				"Creating unit for continuation @${continuation.hashCode()}")
		}
		return { value ->
			val hadRun = hasRunSafetyCheck.getAndSet(true)
			assert(!hadRun)
			try
			{
				continuation(value)
			}
			catch (e: Exception)
			{
				reportInternalProblem(
					lexingState.lineNumber, lexingState.position, e)
			}
			finally
			{
				// We increment and read completed and then read queued.  That's
				// because at every moment completed must always be <= queued.
				// No matter how many new tasks are being queued and completed
				// by other threads, that invariant holds.  The other fact is
				// that the moment the counters have the same (nonzero) value,
				// they will forever after have that same value.  Note that we
				// still have to do the fused incrementAndGet so that we know if
				// *we* were the (sole) cause of exhaustion.
				val completed = atomicWorkUnitsCompleted.incrementAndGet()
				val queued = workUnitsQueued
				assert(completed <= queued)
				if (Interpreter.debugWorkUnits)
				{
					logWorkUnits(
						"Completed work unit: queued = "
						+ queued
						+ ", completed = "
						+ completed
						+ " (delta="
						+ (queued - completed)
						+ ')'.toString())
				}
				if (logger.isLoggable(Level.FINEST))
				{
					logger.log(
						Level.FINEST,
						format(
							"Completed work unit: %d/%d%n", completed, queued))
				}
				if (completed == queued)
				{
					try
					{
						val noMore = noMoreWorkUnits!!
						noMoreWorkUnits = null
						noMore()
					}
					catch (e: Exception)
					{
						reportInternalProblem(
							lexingState.lineNumber,
							lexingState.position, e)
					}

				}
			}
		}
	}

	/**
	 * Eventually execute the specified [List] of functions as
	 * [compiler][AvailCompiler] work units. Note that the queued work unit
	 * count must be increased by the full amount up-front to ensure the
	 * completion of the first N tasks before the N+1st can be queued doesn't
	 * trigger execution of [noMoreWorkUnits]. Each continuation will be passed
	 * the same given argument.
	 *
	 * @param ArgType
	 *   The type of the argument to pass to the continuations.
	 * @param lexingState
	 *   The [LexingState] for which to report problems.
	 * @param continuations
	 *   A non-empty list of things to do at some point in the future.
	 * @param argument
	 *   The argument to pass to each continuation.
	 */
	fun <ArgType> workUnitsDo(
		lexingState: LexingState,
		continuations: List<(ArgType)->Unit>,
		argument: ArgType)
	{
		assert(continuations.isNotEmpty())
		assert(noMoreWorkUnits !== null)

		// Start by increasing the queued counter by the number of actions we're
		// adding.
		startWorkUnits(continuations.size)
		// We're tracking work units, so we have to make sure to account for the
		// new unit being queued, to increment the completed count when it
		// completes, and to run the noMoreWorkUnits action as soon the counters
		// coincide (indicating the last work unit just completed).
		continuations.forEach { continuation ->
			val workUnit = workUnitCompletion(lexingState, null, continuation)
			runtime.execute(compilerPriority) {
				workUnit(argument)
			}
		}
	}

	/**
	 * Evaluate the specified [function][FunctionDescriptor] in the module's
	 * context; lexically enclosing variables are not considered in scope, but
	 * module variables and constants are in scope.
	 *
	 * @param function
	 *   A function.
	 * @param lexingState
	 *   The position at which this function occurs in the module.
	 * @param args
	 *   The arguments to the function.
	 * @param clientParseData
	 *   The map to associate with the [SpecialAtom.CLIENT_DATA_GLOBAL_KEY] atom
	 *   in the fiber.
	 * @param shouldSerialize
	 *   `true` if the generated function should be serialized, `false`
	 *   otherwise.
	 * @param trackTasks
	 *   Whether to track that this fiber is running, and when done, to run
	 *   [noMoreWorkUnits] if the queued/completed counts agree.
	 * @param onSuccess
	 *   What to do with the result of the evaluation.
	 * @param onFailure
	 *   What to do with a terminal [Throwable].
	 */
	private fun evaluateFunctionThen(
		function: A_Function,
		lexingState: LexingState,
		args: List<A_BasicObject>,
		clientParseData: A_Map,
		shouldSerialize: Boolean,
		trackTasks: Boolean,
		onSuccess: (AvailObject)->Unit,
		onFailure: (Throwable)->Unit)
	{
		val code = function.code()
		assert(code.numArgs() == args.size)
		val fiber = newLoaderFiber(function.kind().returnType, loader)
		{
			formatString(
				"Eval fn=%s, in %s:%d",
				code.methodName,
				code.module.shortModuleNameNative,
				code.codeStartingLineNumber)
		}
		fiber.fiberGlobals = fiber.fiberGlobals.mapAtPuttingCanDestroy(
			CLIENT_DATA_GLOBAL_KEY.atom, clientParseData, true)
		if (shouldSerialize)
		{
			loader.startRecordingEffects()
		}
		val adjustedSuccess: (AvailObject) -> Unit = when
		{
			shouldSerialize -> fiber.fiberHelper.fiberTime().let { before ->
				{ successValue ->
					val after = fiber.fiberHelper.fiberTime()
					Interpreter.current().recordTopStatementEvaluation(
						(after - before).toDouble(), module)
					loader.stopRecordingEffects()
					serializeAfterRunning(function)
					onSuccess(successValue)
				}
			}
			else -> onSuccess
		}
		if (trackTasks)
		{
			lexingState.setFiberContinuationsTrackingWork(
				fiber, adjustedSuccess, onFailure)
		}
		else
		{
			fiber.setSuccessAndFailure(adjustedSuccess, onFailure)
		}
		runtime.runOutermostFunction(fiber, function, args)
	}

	/**
	 * Generate a [function][FunctionDescriptor] from the specified
	 * [phrase][PhraseDescriptor] and evaluate it in the module's context;
	 * lexically enclosing variables are not considered in scope, but module
	 * variables and constants are in scope.
	 *
	 * @param expressionNode
	 *   A [phrase][PhraseDescriptor].
	 * @param lexingState
	 *   The position at which the expression starts.
	 * @param shouldSerialize
	 *   `true` if the generated function should be serialized, `false`
	 *   otherwise.
	 * @param trackTasks
	 *   Whether to track that this fiber is running, and when done, to run
	 *   [noMoreWorkUnits] if the queued/completed counts agree.
	 * @param onSuccess
	 *   What to do with the result of the evaluation.
	 * @param onFailure
	 *   What to do after a failure.
	 */
	fun evaluatePhraseThen(
		expressionNode: A_Phrase,
		lexingState: LexingState,
		shouldSerialize: Boolean,
		trackTasks: Boolean,
		onSuccess: (AvailObject)->Unit,
		onFailure: (Throwable)->Unit)
	{
		val function = try
		{
			createFunctionForPhrase(
				expressionNode, module, lexingState.lineNumber)
		}
		catch (e: AvailRuntimeException)
		{
			onFailure(e)
			return
		}
		if (shouldSerialize)
		{
			loader.topLevelStatementBeingCompiled =
				function.code().originatingPhrase
		}
		evaluateFunctionThen(
			function,
			lexingState,
			emptyList(),
			emptyMap,
			shouldSerialize,
			trackTasks,
			onSuccess,
			onFailure)
	}

	/**
	 * Evaluate the given phrase.  Pass the result to the continuation, or if it
	 * fails pass the exception to the failure continuation.
	 *
	 * @param lexingState
	 *   The [LexingState] at which the phrase starts.
	 * @param expression
	 *   The phrase to evaluate.
	 * @param continuation
	 *   What to do with the result of evaluation.
	 * @param onFailure
	 *   What to do after a failure.
	 */
	internal fun evaluatePhraseAtThen(
		lexingState: LexingState,
		expression: A_Phrase,
		continuation: (AvailObject)->Unit,
		onFailure: (Throwable)->Unit)
	{
		evaluatePhraseThen(
			expressionNode = expression,
			lexingState = lexingState,
			shouldSerialize = false,
			trackTasks = true,
			onSuccess = continuation,
			onFailure = onFailure)
	}

	/**
	 * Allow multiple sequential statements to have their effects accumulated.
	 * As soon as there's a non-summarizable statement, force these delayed
	 * early effects to be flushed, and then separately the regular delayed
	 * effects.
	 *
	 * The entries are <lineNumber, phrase, effect> triples, so that the
	 * generated function can more accurately identify where an effect was
	 * defined.
	 */
	private val delayedSerializedEarlyEffects =
		mutableListOf<Triple<Int, A_Phrase, LoadingEffect>>()

	/**
	 * Allow multiple sequential statements to have their effects accumulated.
	 * As soon as there's a non-summarizable statement, force these delayed
	 * effects to be flushed.
	 *
	 * The entries are <lineNumber, phrase, effect> triples, so that the
	 * generated function can more accurately identify where an effect was
	 * defined.
	 */
	private val delayedSerializedEffects =
		mutableListOf<Triple<Int, A_Phrase, LoadingEffect>>()

	/**
	 * Flush the [delayedSerializedEarlyEffects] and [delayedSerializedEffects]
	 * to top-level functions that perform those actions.
	 */
	fun flushDelayedSerializedEffects()
	{
		flushDelayedSerializedEffects(delayedSerializedEarlyEffects)
		flushDelayedSerializedEffects(delayedSerializedEffects)
	}

	/**
	 * Flush the given mutable list of effects to be flushed as a top-level
	 * function that performs that series of actions, and clear the list.
	 */
	private fun flushDelayedSerializedEffects(
		effects: MutableList<Triple<Int, A_Phrase, LoadingEffect>>)
	{
		if (effects.isEmpty()) return
		val writer = L1InstructionWriter(
			module, effects.first().first, effects.first().second)
		writer.argumentTypes()
		writer.returnType = TOP.o
		writer.returnTypeIfPrimitiveFails = TOP.o
		effects.forEachIndexed { i, (line, _, effect) ->
			if (i > 0) writer.write(0, L1Operation.L1_doPop)
			effect.writeEffectTo(writer, line)
		}
		val summaryFunction = createFunction(writer.compiledCode(), emptyTuple)
		if (AvailLoader.debugUnsummarizedStatements)
		{
			println(
				buildString {
					append(module.moduleNameNative)
					append(':')
					append(effects.first().first)
					append('-')
					append(effects.last().first)
					if (effects === delayedSerializedEarlyEffects)
						append(" Early Summary -- \n")
					else append(" Summary -- \n")
					append(L1Decompiler.decompile(summaryFunction.code()))
					append("(batch = ${effects.size})")
				})
		}
		serializer.serialize(summaryFunction)
		effects.clear()
	}

	/**
	 * Serialize either the given function or something semantically equivalent.
	 * The equivalent is expected to be faster, but can only be used if no
	 * triggers had been tripped during execution of the function.  The triggers
	 * include reading or writing shared variables, or executing certain
	 * primitives.  Reading from shared constants is ok, as long as it has the
	 * bit set to indicate it was initialized with a stably computed value.
	 *
	 * @param function
	 *   The function that has already run.
	 */
	@Synchronized
	private fun serializeAfterRunning(function: A_Function)
	{
		val phrase = function.code().originatingPhrase
		val startingLineNumber = function.code().codeStartingLineNumber
		if (loader.statementCanBeSummarized())
		{
			// Output summarized functions instead of what ran.  Associate the
			// original phrase with it, which allows the subphrases to be marked
			// up in an editor and stepped in a debugger, even though the
			// top-level phrase itself will be invalid.
			val newEarlyEffects = loader.recordedEarlyEffects()
			val newEffects = loader.recordedEffects()
			newEarlyEffects.mapTo(delayedSerializedEarlyEffects) { effect ->
				Triple(startingLineNumber, phrase, effect)
			}
			newEffects.mapTo(delayedSerializedEffects) { effect ->
				Triple(startingLineNumber, phrase, effect)
			}
		}
		else
		{
			flushDelayedSerializedEffects()
			// Can't summarize; write the original function.
			if (AvailLoader.debugUnsummarizedStatements)
			{
				println(
					module.moduleNameNative
						+ ":" + startingLineNumber
						+ " Unsummarized -- \n" + function)
			}
			serializer.serialize(function)
		}
	}

	/**
	 * Serialize the given function without attempting to summarize it into
	 * equivalent statements.
	 *
	 * @param function
	 *   The function that has already run.
	 */
	@Synchronized
	internal fun serializeWithoutSummary(
		function: A_Function)
	{
		if (AvailLoader.debugUnsummarizedStatements)
		{
			println(
				module.moduleNameNative
					+ ":" + function.code().codeStartingLineNumber
					+ " Forced -- \n" + function)
		}
		serializer.serialize(function)
	}

	/**
	 * Report an [internal][ProblemType.INTERNAL] [problem][Problem].
	 *
	 * @param lineNumber
	 *   The one-based line number on which the problem occurs.
	 * @param position
	 *   The one-based position in the source at which the problem occurs.
	 * @param e
	 *   The unexpected [exception][Throwable] that is the proximal cause of the
	 *   problem.
	 */
	internal fun reportInternalProblem(
		lineNumber: Int,
		position: Int,
		e: Throwable)
	{
		diagnostics.compilationIsInvalid = true
		val problem = object : Problem(
			moduleName,
			lineNumber,
			position.toLong(),
			INTERNAL,
			"Internal error: {0}\n{1}",
			e.message!!,
			e.stackToString)
		{
			override fun abortCompilation()
			{
				// Nothing else needed.
			}
		}
		diagnostics.handleProblem(problem)
	}

	/**
	 * Report an [execution][ProblemType.EXECUTION] [problem][Problem].
	 *
	 * @param lineNumber
	 *   The one-based line number on which the problem occurs.
	 * @param position
	 *   The one-based position in the source at which the problem occurs.
	 * @param e
	 *   The unexpected [exception][Throwable] that is the proximal cause of the
	 *   problem.
	 */
	internal fun reportExecutionProblem(
		lineNumber: Int,
		position: Int,
		e: Throwable)
	{
		diagnostics.compilationIsInvalid = true
		if (e is FiberTerminationException)
		{
			diagnostics.handleProblem(
				object : Problem(
					moduleName,
					lineNumber,
					position.toLong(),
					EXECUTION,
					"Execution error: Avail stack reported above.\n")
				{
					override fun abortCompilation()
					{
						// Nothing else needed.
					}
				})
		}
		else
		{
			diagnostics.handleProblem(
				object : Problem(
					moduleName,
					lineNumber,
					position.toLong(),
					EXECUTION,
					"Execution error: {0}\n{1}",
					e.message!!,
					e.stackToString)
				{
					override fun abortCompilation()
					{
						// Nothing else needed.
					}
				})
		}
	}

	/**
	 * Report an [emergency&#32;exit][ProblemType.EXECUTION] [problem][Problem].
	 *
	 * @param lineNumber
	 *   The one-based line number on which the problem occurs.
	 * @param position
	 *   The one-based position in the source at which the problem occurs.
	 * @param e
	 *   The [emergency&#32;exit][AvailEmergencyExitException].
	 */
	internal fun reportEmergencyExitProblem(
		lineNumber: Int,
		position: Int,
		e: AvailEmergencyExitException)
	{
		diagnostics.compilationIsInvalid = true
		diagnostics.handleProblem(object : Problem(
			moduleName,
			lineNumber,
			position.toLong(),
			EXECUTION,
			"{0}",
			e.message)
		{
			override fun abortCompilation()
			{
				// Nothing else needed.
			}
		})
	}

	/**
	 * Process all of the phrase's subphrases recursively, then style the phrase
	 * itself, [then] invoke the given action.
	 *
	 * The phrases are processed in parallel, and the [then] action is performed
	 * only when all of the phrases have been processed.
	 *
	 * @param phrases
	 *   The [Collection] of [A_Phrase]s to process.
	 * @param visitedSet
	 *   The [Set] of [A_Phrase]s that should not be traversed again.
	 * @param then
	 *   The action to invoke after the phrases have been fully processed.
	 */
	fun visitAll(
		phrases: Collection<A_Phrase>,
		visitedSet: MutableSet<A_Phrase>,
		then: ()->Unit)
	{
		when (phrases.size)
		{
			0 -> then()
			1 -> runtime.execute(compilerPriority) {
				phrases.single().applyStylesThen(this, visitedSet, then)
			}
			else -> phrases.parallelDoThen(
				action = { phrase, after ->
					runtime.execute(compilerPriority) {
						phrase.applyStylesThen(this, visitedSet, after)
					}
				},
				then = then)
		}
	}

	/**
	 * Apply the style for a single *method* send phrase, without considering
	 * its subphrases.
	 *
	 * @param originalSendPhrase
	 *   An optional send phrase that had been parsed from the source.  If this
	 *   is not a macro substitution, this will be `null`.
	 * @param transformedPhrase
	 *   The end result of applying a macro to the [originalSendPhrase], or if
	 *   no macro was involved, the parsed [send][SEND_PHRASE] phrase.
	 * @param then
	 *   What to do after styling.
	 */
	fun styleSendThen(
		originalSendPhrase: A_Phrase?,
		transformedPhrase: A_Phrase,
		then: ()->Unit)
	{
		val styles = mutableListOf<SystemStyle>()
		// 1. Add a method/macro style.
		styles.add(
			when
			{
				originalSendPhrase.notNullAnd {
					!transformedPhrase.equals(this) }
				-> SystemStyle.MACRO_SEND
				else -> SystemStyle.METHOD_SEND
			})
		// 2. Add an optional style based on the yield type, taking care not to
		//    override variable use styles.  Style literals by their type
		//    differently than non-literals.
		val phraseKind = transformedPhrase.phraseKind
		val yieldType = transformedPhrase.phraseExpressionType
		val styleByType: SystemStyle? = when (phraseKind)
		{
			// Ignore "glue" phrases.  The originalSendPhrase's styler will
			// still get a chance to run further down.
			ASSIGNMENT_PHRASE,
			BLOCK_PHRASE,
			EXPRESSION_AS_STATEMENT_PHRASE,
			EXPRESSION_PHRASE,
			FIRST_OF_SEQUENCE_PHRASE,
			LIST_PHRASE,
			MACRO_SUBSTITUTION_PHRASE,
			MARKER_PHRASE,
			PARSE_PHRASE,
			PERMUTED_LIST_PHRASE,
			REFERENCE_PHRASE,
			SEQUENCE_AS_EXPRESSION_PHRASE,
			SEQUENCE_PHRASE,
			STATEMENT_PHRASE,
			SUPER_CAST_PHRASE,
			VARIABLE_USE_PHRASE
				-> null
			// Declarations should be styled via the macros that create them.
			ARGUMENT_PHRASE,
			DECLARATION_PHRASE,
			LABEL_PHRASE,
			LOCAL_VARIABLE_PHRASE,
			LOCAL_CONSTANT_PHRASE,
			MODULE_VARIABLE_PHRASE,
			MODULE_CONSTANT_PHRASE,
			PRIMITIVE_FAILURE_REASON_PHRASE
				-> null
			// Method sends get styled by their yield type.
			SEND_PHRASE -> yieldType.systemStyleForType

			// Literals get styled specially by their type.
			LITERAL_PHRASE -> transformedPhrase.token.let { token ->
				when
				{
					// If the token was directly lexed and already styled, don't
					// add additional type-based styling.
					token.isInCurrentModule(module) &&
						token.generatingLexer.notNil &&
						getStylerFunction(token.generatingLexer.lexerMethod)
							.notNil -> null
					yieldType.isSubtypeOf(NUMBER.o) ->
						SystemStyle.NUMERIC_LITERAL
					yieldType.isSubtypeOf(CHARACTER.o) ->
						SystemStyle.CHARACTER_LITERAL
					yieldType.isSubtypeOf(booleanType) ->
						SystemStyle.BOOLEAN_LITERAL
					yieldType.isSubtypeOf(ATOM.o) ->
						SystemStyle.ATOM_LITERAL
					else ->
						yieldType.systemStyleForType ?:
							SystemStyle.OTHER_LITERAL
				}
			}
		}
		styleByType?.let { styles.add(it) }
		styles.forEach { style ->
			originalSendPhrase?.let { loader.styleTokens(it.tokens, style) }
			loader.styleTokens(transformedPhrase.tokens, style)
		}
		// 3. Run any custom styler.
		val bundleWithStyler = when
		{
			originalSendPhrase !== null -> originalSendPhrase.bundle
			transformedPhrase.phraseKindIsUnder(SEND_PHRASE) ->
				transformedPhrase.bundle
			else -> return then()
		}
		// Next, give the method's styler function a chance to run.
		val stylerFn = getStylerFunction(bundleWithStyler.bundleMethod)
		if (debugStyling)
		{
			val stylerName = when
			{
				(stylerFn.isNil) -> "(default)"
				else -> stylerFn.code().methodName.asNativeString()
			}
			println("style send: $bundleWithStyler\n\twith $stylerName")
		}
		val fiber = newStylerFiber(loader)
		{
			val stylerName = when
			{
				(stylerFn.isNil) -> "default"
				else -> stylerFn.code().methodName.asNativeString()
			}
			formatString(
				"Style %s (%s)",
				bundleWithStyler.message,
				stylerName)
		}
		fiber.setSuccessAndFailure(
			onSuccess = { then() },
			// Ignore styler failures for now.
			onFailure = { then() })
		runtime.runOutermostFunction(
			fiber,
			stylerFn.ifNil { runtime[DEFAULT_STYLER] },
			listOf(
				tupleFromList(listOfNotNull(originalSendPhrase)),
				transformedPhrase))
	}

	/**
	 * A cache mapping from [A_Method] to the [A_Function] of an [A_Styler]'s
	 * body.  It should be cleared any time a new styler is introduced (within
	 * the scope of the current module), or alternatively just before styling
	 * a top-level statement.  If a styler cannot be found for the method, store
	 * [nil] as the value to cache a negative search.
	 */
	private val styleCache = ConcurrentHashMap<A_Method, A_Function>()

	/**
	 * Clear the cache of method -> style function.  This can be done either
	 * when a new style is added within scope of this module, or just before
	 * styling a top-level statement.
	 */
	fun clearStyleCache() = styleCache.clear()

	/**
	 * Given a [method], look up stylers visible by the current module, and
	 * choose the most specific one, or the conflict styler if there is more
	 * than one that is most specific.  Answer nil if none are defined and
	 * visible to the module.
	 */
	fun getStylerFunction(method: A_Method): A_Function
	{
		styleCache[method]?.let { return it }
		val ancestorModules = module.allAncestors
		val eligibleStylers = method.methodStylers.filter {
			it.module.isNil
				|| it.module.equals(module)
				|| it.module in ancestorModules
		}
		val mostSpecificStylers = when (eligibleStylers.size)
		{
			in 0 .. 1 -> eligibleStylers
			else ->
			{
				// There can't be multiple built-in (no-module) stylers, so let
				// one defined by a module win.
				val notBuiltIn = eligibleStylers.filter { it.module.notNil }
				notBuiltIn.filter { styler ->
					notBuiltIn.none { otherStyler ->
						!styler.equals(otherStyler) &&
							styler.module in otherStyler.module.allAncestors
					}
				}
			}
		}
		val stylerFn = when (mostSpecificStylers.size)
		{
			0 -> nil
			1 -> mostSpecificStylers[0].function
			else ->
				// TODO - Use the conflict style, which isn't coded yet.
				nil
		}
		styleCache[method] = stylerFn
		return stylerFn
	}

	/**
	 * The abstract class for the styling state machine.  A statement can be in
	 * the process of being styled while the next statement is being parsed, but
	 * before that next statement is allowed to executed, the previous styling
	 * action must complete.  Otherwise, the execution of the next statement
	 * might change the stylers that are in effect while the styling of the
	 * previous statement is in progress, which is a race.
	 */
	sealed class StylingCompletionState
	{
		/**
		 * Styling is not currently happening.  Also, nobody is waiting for
		 * styling to complete.
		 */
		object NotStyling : StylingCompletionState()

		/**
		 * Styling is happening, but nobody is waiting for it to complete yet.
		 */
		object StylingNotWaiting : StylingCompletionState()

		/**
		 * Styling is happening, and the other party is waiting for it to
		 * complete, having provided an action indicating what to do when
		 * styling completes.
		 */
		class StylingAndWaiting(
			val notStylingAction : ()->Unit
		) : StylingCompletionState()
	}

	/**
	 * An [AtomicReference] that holds the current [StylingCompletionState].
	 * This is to allow styling of a statement to occur while the subsequent
	 * statement is being parsed – but not executed, since that may affect what
	 * styles are in effect.
	 */
	private val stylingState =
		AtomicReference<StylingCompletionState>(NotStyling)

	/**
	 * A styling activity is starting.  Note that styling must always be started
	 * causally *before* [whenNotStyling] may be called.
	 */
	fun beginningStyling()
	{
		if (!stylingState.compareAndSet(NotStyling, StylingNotWaiting))
			throw IllegalStateException()
	}

	/**
	 * The current styling activity has completed.  If there was a post-styling
	 * action set, run it *after* transitioning to [NotStyling].
	 */
	fun finishedStyling()
	{
		when (val oldState = stylingState.getAndSet(NotStyling))
		{
			is NotStyling -> throw IllegalStateException()
			is StylingAndWaiting -> oldState.notStylingAction()
			else -> { }
		}
	}

	/**
	 * A styling action might still be running.  If it is, ensure the [action]
	 * is executed after the styling completes.  If it was not styling, run the
	 * [action] immediately.
	 */
	fun whenNotStyling(action: ()->Unit)
	{
		do
		{
			val oldState = stylingState.get()
			val newState = when (oldState)
			{
				is NotStyling ->
				{
					action()
					return
				}
				is StylingNotWaiting -> StylingAndWaiting(action)
				// Only one post-styling action is allowed.
				is StylingAndWaiting -> throw IllegalStateException()
			}
		}
		while (!stylingState.compareAndSet(oldState, newState))
	}


	/**
	 * Capture the tree of [PhraseNode]s that describe this top-level phrase.
	 *
	 * @param rootPhrase
	 *   A top-level phrase for which to record a [PhraseNode] tree.
	 */
	fun recordPathForTopLevelPhrase(rootPhrase: A_Phrase)
	{
		val fakeRoot = PhraseNode(null, null, emptyList(), null)
		// Each pair holds the PhraseNode having its children converted, and an
		// iterator that produces subphrases of the phrase that the PhraseNode
		// was built from.
		val workStack = mutableListOf(fakeRoot to listOf(rootPhrase).iterator())
		while (workStack.isNotEmpty())
		{
			val (parent, iterator) = workStack.last()
			if (!iterator.hasNext())
			{
				workStack.removeLast()
				continue
			}
			var phrase = iterator.next()
			var moduleName: A_String? = null
			var atomName: A_String? = null
			phrase.apparentSendName.ifNotNil { atom: A_Atom ->
				atom.issuingModule.ifNotNil { module: A_Module ->
					moduleName = module.moduleName
				}
				atomName = atom.atomName
			}
			phrase = when
			{
				phrase.isMacroSubstitutionNode ->
					phrase.macroOriginalSendNode
				!phrase.phraseKindIsUnder(LITERAL_PHRASE) -> phrase
				// Prefer the generatingPhrase, if present.
				phrase.token.generatingPhrase.notNil ->
					phrase.token.generatingPhrase
				// Otherwise use the literal value, if it's a phrase.
				phrase.token.literal()
						.isInstanceOfKind(PARSE_PHRASE.mostGeneralType) ->
					phrase.token.literal()
				else -> phrase
			}
			if (atomName === null)
			{
				phrase.apparentSendName.ifNotNil { atom: A_Atom ->
					atom.issuingModule.ifNotNil { module: A_Module ->
						moduleName = module.moduleName
					}
					atomName = atom.atomName
				}
			}
			val tokenSpans =
				(phrase.tokens zip phrase.tokenIndicesInName).mapNotNull {
						(token: A_Token, indexInName: A_Number) ->
					if (!token.isInCurrentModule(module)) return@mapNotNull null
					val start = surrogateIndexConverter.availIndexToJavaIndex(
						token.start())
					val pastEnd = surrogateIndexConverter.availIndexToJavaIndex(
						token.pastEnd())
					PhraseNodeToken(start, pastEnd, indexInName.extractInt)
				}
			val phraseNode =
				PhraseNode(moduleName, atomName, tokenSpans, parent)
			parent.children.add(phraseNode)
			val children = mutableListOf<A_Phrase>()
			val childrenProvider = when
			{
				// Skip past the sole child of a send, the list phrase of
				// arguments.  Treat that list phrase's children as the send's
				// children instead.
				phrase.phraseKindIsUnder(SEND_PHRASE) ->
					phrase.argumentsListNode
				phrase.phraseKindIsUnder(LITERAL_PHRASE)
					&& phrase.token.literal().isInstanceOfKind(
						PARSE_PHRASE.mostGeneralType) -> phrase.token.literal()
				else -> phrase
			}
			childrenProvider.childrenDo(children::add)
			workStack.add(phraseNode to children.iterator())
		}
		val root = fakeRoot.children.single()
		root.parent = null
		module.phrasePathRecord().rootTrees.add(root)
	}

	companion object
	{
		/** The [logger][Logger]. */
		val logger: Logger = Logger.getLogger(
			CompilationContext::class.java.name)
	}
}
