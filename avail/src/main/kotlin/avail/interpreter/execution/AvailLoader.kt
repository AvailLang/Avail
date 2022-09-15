/*
 * AvailLoader.kt
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
package avail.interpreter.execution

import avail.AvailRuntime
import avail.AvailRuntimeConfiguration.debugStyling
import avail.AvailThread
import avail.annotations.ThreadSafe
import avail.compiler.ModuleManifestEntry
import avail.compiler.SideEffectKind
import avail.compiler.splitter.MessageSplitter
import avail.compiler.splitter.MessageSplitter.Metacharacter.BACK_QUOTE
import avail.compiler.splitter.MessageSplitter.Metacharacter.CLOSE_GUILLEMET
import avail.compiler.splitter.MessageSplitter.Metacharacter.Companion.canBeBackQuoted
import avail.compiler.splitter.MessageSplitter.Metacharacter.DOUBLE_DAGGER
import avail.compiler.splitter.MessageSplitter.Metacharacter.DOUBLE_QUESTION_MARK
import avail.compiler.splitter.MessageSplitter.Metacharacter.ELLIPSIS
import avail.compiler.splitter.MessageSplitter.Metacharacter.EXCLAMATION_MARK
import avail.compiler.splitter.MessageSplitter.Metacharacter.OCTOTHORP
import avail.compiler.splitter.MessageSplitter.Metacharacter.OPEN_GUILLEMET
import avail.compiler.splitter.MessageSplitter.Metacharacter.QUESTION_MARK
import avail.compiler.splitter.MessageSplitter.Metacharacter.SECTION_SIGN
import avail.compiler.splitter.MessageSplitter.Metacharacter.SINGLE_DAGGER
import avail.compiler.splitter.MessageSplitter.Metacharacter.TILDE
import avail.compiler.splitter.MessageSplitter.Metacharacter.UNDERSCORE
import avail.compiler.splitter.MessageSplitter.Metacharacter.UP_ARROW
import avail.compiler.splitter.MessageSplitter.Metacharacter.VERTICAL_BAR
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import avail.descriptor.atoms.A_Atom.Companion.issuingModule
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomDescriptor.Companion.createAtom
import avail.descriptor.atoms.AtomDescriptor.Companion.createSpecialAtom
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.HERITABLE_KEY
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.addGrammaticalRestriction
import avail.descriptor.bundles.A_Bundle.Companion.bundleAddMacro
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.definitionParsingPlans
import avail.descriptor.bundles.A_Bundle.Companion.macrosTuple
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.bundles.A_Bundle.Companion.messageSplitter
import avail.descriptor.bundles.A_BundleTree
import avail.descriptor.bundles.A_BundleTree.Companion.addPlanInProgress
import avail.descriptor.bundles.A_BundleTree.Companion.removePlanInProgress
import avail.descriptor.bundles.A_BundleTree.Companion.updateForNewGrammaticalRestriction
import avail.descriptor.bundles.MessageBundleTreeDescriptor.Companion.newBundleTree
import avail.descriptor.character.A_Character
import avail.descriptor.character.A_Character.Companion.codePoint
import avail.descriptor.fiber.A_Fiber
import avail.descriptor.fiber.A_Fiber.Companion.setSuccessAndFailure
import avail.descriptor.fiber.FiberDescriptor.Companion.loaderPriority
import avail.descriptor.fiber.FiberDescriptor.Companion.newFiber
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.functions.PrimitiveCompiledCodeDescriptor.Companion.newPrimitiveRawFunction
import avail.descriptor.maps.A_Map.Companion.forEach
import avail.descriptor.maps.A_Map.Companion.mapAt
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.maps.A_Map.Companion.mapIterable
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_Definition.Companion.definitionMethod
import avail.descriptor.methods.A_GrammaticalRestriction
import avail.descriptor.methods.A_Macro
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Method.Companion.bundles
import avail.descriptor.methods.A_Method.Companion.chooseBundle
import avail.descriptor.methods.A_Method.Companion.definitionsTuple
import avail.descriptor.methods.A_Method.Companion.includesDefinition
import avail.descriptor.methods.A_Method.Companion.methodAddDefinition
import avail.descriptor.methods.A_Method.Companion.methodStylers
import avail.descriptor.methods.A_Method.Companion.numArgs
import avail.descriptor.methods.A_Method.Companion.removeDefinition
import avail.descriptor.methods.A_Method.Companion.updateStylers
import avail.descriptor.methods.A_SemanticRestriction
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.methods.A_Sendable.Companion.isAbstractDefinition
import avail.descriptor.methods.A_Sendable.Companion.isForwardDefinition
import avail.descriptor.methods.A_Sendable.Companion.isMethodDefinition
import avail.descriptor.methods.A_Styler
import avail.descriptor.methods.A_Styler.Companion.module
import avail.descriptor.methods.A_Styler.Companion.stylerFunctionType
import avail.descriptor.methods.AbstractDefinitionDescriptor
import avail.descriptor.methods.AbstractDefinitionDescriptor.Companion.newAbstractDefinition
import avail.descriptor.methods.DefinitionDescriptor
import avail.descriptor.methods.ForwardDefinitionDescriptor
import avail.descriptor.methods.ForwardDefinitionDescriptor.Companion.newForwardDefinition
import avail.descriptor.methods.GrammaticalRestrictionDescriptor.Companion.newGrammaticalRestriction
import avail.descriptor.methods.MacroDescriptor
import avail.descriptor.methods.MacroDescriptor.Companion.newMacroDefinition
import avail.descriptor.methods.MethodDefinitionDescriptor
import avail.descriptor.methods.MethodDefinitionDescriptor.Companion.newMethodDefinition
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.CREATE_ATOM
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.CREATE_EXPLICIT_SUBCLASS_ATOM
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.CREATE_HERITABLE_ATOM
import avail.descriptor.methods.SemanticRestrictionDescriptor
import avail.descriptor.methods.StylerDescriptor
import avail.descriptor.methods.StylerDescriptor.Companion.newStyler
import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.addPrivateName
import avail.descriptor.module.A_Module.Companion.addSeal
import avail.descriptor.module.A_Module.Companion.buildFilteredBundleTree
import avail.descriptor.module.A_Module.Companion.createLexicalScanner
import avail.descriptor.module.A_Module.Companion.hasAncestor
import avail.descriptor.module.A_Module.Companion.importedNames
import avail.descriptor.module.A_Module.Companion.moduleAddDefinition
import avail.descriptor.module.A_Module.Companion.moduleAddGrammaticalRestriction
import avail.descriptor.module.A_Module.Companion.moduleAddMacro
import avail.descriptor.module.A_Module.Companion.moduleAddSemanticRestriction
import avail.descriptor.module.A_Module.Companion.moduleAddStyler
import avail.descriptor.module.A_Module.Companion.newNames
import avail.descriptor.module.A_Module.Companion.privateNames
import avail.descriptor.module.A_Module.Companion.resolveForward
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.module.A_Module.Companion.trueNamesForStringName
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.parsing.A_DefinitionParsingPlan
import avail.descriptor.parsing.A_Lexer
import avail.descriptor.parsing.A_ParsingPlanInProgress
import avail.descriptor.parsing.LexerDescriptor.Companion.newLexer
import avail.descriptor.parsing.ParsingPlanInProgressDescriptor.Companion.newPlanInProgress
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.startingLineNumber
import avail.descriptor.phrases.VariableUsePhraseDescriptor
import avail.descriptor.representation.AvailObject.Companion.error
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.hasElement
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.A_Set.Companion.setWithoutElementCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tokens.A_Token
import avail.descriptor.tokens.A_Token.Companion.end
import avail.descriptor.tokens.A_Token.Companion.pastEnd
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.acceptsArgTypesFromFunctionType
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AmbiguousNameException
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.exceptions.AvailErrorCode.E_MACRO_MUST_RETURN_A_PHRASE
import avail.exceptions.AvailErrorCode.E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED
import avail.exceptions.AvailErrorCode.E_REDEFINED_WITH_SAME_ARGUMENT_TYPES
import avail.exceptions.AvailErrorCode.E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS
import avail.exceptions.AvailErrorCode.E_STYLER_ALREADY_SET_BY_THIS_MODULE
import avail.exceptions.AvailException
import avail.exceptions.MalformedMessageException
import avail.exceptions.SignatureException
import avail.interpreter.Primitive
import avail.interpreter.effects.LoadingEffect
import avail.interpreter.effects.LoadingEffectToAddDefinition
import avail.interpreter.effects.LoadingEffectToAddMacro
import avail.interpreter.effects.LoadingEffectToRunPrimitive
import avail.interpreter.execution.AvailLoader.Phase.COMPILING
import avail.interpreter.execution.AvailLoader.Phase.EXECUTING_FOR_COMPILE
import avail.interpreter.execution.AvailLoader.Phase.EXECUTING_FOR_LOAD
import avail.interpreter.execution.AvailLoader.Phase.INITIALIZING
import avail.interpreter.execution.AvailLoader.Phase.LOADING
import avail.interpreter.execution.AvailLoader.Phase.UNLOADING
import avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerKeywordBody
import avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerKeywordFilter
import avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerOperatorBody
import avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerOperatorFilter
import avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerSlashStarCommentBody
import avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerSlashStarCommentFilter
import avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerStringBody
import avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerStringFilter
import avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerWhitespaceBody
import avail.interpreter.primitive.bootstrap.lexing.P_BootstrapLexerWhitespaceFilter
import avail.interpreter.primitive.methods.P_Alias
import avail.io.TextInterface
import avail.utility.evaluation.Combinator.recurse
import avail.utility.safeWrite
import avail.utility.structures.RunTree
import avail.utility.trace
import java.util.ArrayDeque
import java.util.TreeMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.annotation.concurrent.GuardedBy

/**
 * An `AvailLoader` is responsible for orchestrating module-level side-effects,
 * such as those caused by adding [method][MethodDefinitionDescriptor],
 * [abstract][AbstractDefinitionDescriptor], and
 * [forward][ForwardDefinitionDescriptor] definitions.  Also
 * [macros][MacroDescriptor], [A_Lexer]s, [A_SemanticRestriction]s,
 * [A_GrammaticalRestriction]s, and method [seals][AvailRuntime.addSeal].
 *
 * @constructor
 *
 * @property runtime
 *   The current [AvailRuntime].
 * @property module
 *   The Avail [module][ModuleDescriptor] undergoing loading.
 * @property textInterface
 *   The [TextInterface] for any [fibers][A_Fiber] started by this
 *   [AvailLoader].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class AvailLoader
constructor(
	val runtime: AvailRuntime,
	val module: A_Module,
	val textInterface: TextInterface)
{
	/**
	 * The macro-state of the loader.  During compilation from a file, a loader
	 * will ratchet between [COMPILING] while parsing a top-level statement, and
	 * [EXECUTING_FOR_COMPILE] while executing the compiled statement.
	 * Similarly, when loading from a file, the loader's [phase] alternates
	 * between [LOADING] and [EXECUTING_FOR_LOAD].
	 *
	 * @constructor
	 *
	 * @property isExecuting
	 *   Whether this phase represents a time when execution is happening.
	 */
	enum class Phase(val isExecuting: Boolean = false)
	{
		/** No statements have been loaded or compiled yet. */
		INITIALIZING,

		/**
		 * The header has been compiled and processed, and is now being styled.
		 */
		STYLING_HEADER(true),

		/** A top-level statement is being compiled. */
		COMPILING,

		/** A top-level statement is being loaded from a repository. */
		LOADING,

		/** A top-level parsed statement is being executed. */
		EXECUTING_FOR_COMPILE(true),

		/** A top-level deserialized statement is being executed. */
		EXECUTING_FOR_LOAD(true),

		/** The fully-loaded module is now being unloaded. */
		UNLOADING,

		/**
		 * The [AvailLoader] is parsing an expression within some anonymous
		 * module.  The current fiber is attempting to execute some Avail code
		 * as requested by the compilation.
		 *
		 * Note that this is permitted after loading has completed, but also
		 * during loading if the code being loaded explicitly creates an
		 * anonymous module and uses it to compile an expression.  In both cases
		 * the current loader will be tied to an anonymous module.
		 */
		// TODO: [MvG] Finish supporting eval.
		@Suppress("unused")
		COMPILING_FOR_EVAL(false);
	}

	/** The current loading [Phase]. */
	@Volatile
	var phase: Phase = INITIALIZING

	/**
	 * The [LexicalScanner] used for creating tokens from source code for this
	 * [AvailLoader].
	 *
	 * Start by using the module header lexical scanner, and replace it after
	 * the header has been fully parsed.
	 */
	var lexicalScanner: LexicalScanner? = moduleHeaderLexicalScanner

	/**
	 * The [A_BundleTree] that this [AvailLoader] is using to parse its
	 * [module][ModuleDescriptor]. Start it out as the [moduleHeaderBundleRoot]
	 * for parsing the header, then switch it out to parse the body.
	 */
	var rootBundleTree: A_BundleTree = moduleHeaderBundleRoot
		private set

	/**
	 * During module compilation, this holds the top-level zero-argument block
	 * phrase that wraps the parsed statement, and is in the process of being
	 * evaluated.
	 */
	var topLevelStatementBeingCompiled: A_Phrase? = null

	/**
	 * A stream on which to serialize each [ModuleManifestEntry] when the
	 * definition actually occurs during compilation.  After compilation, the
	 * bytes of this stream are written to a record whose index is captured in
	 * the [A_Module]'s [manifestEntries], and fetched from the repository and
	 * decoded into a pojo array when needed.
	 */
	var manifestEntries: MutableList<ModuleManifestEntry>? = null

	/**
	 * A flag that is cleared before executing each top-level statement of a
	 * module, and set whenever execution of the statement causes behavior that
	 * can't simply be summarized by a sequence of [LoadingEffect]s.
	 */
	@Volatile
	private var statementCanBeSummarized = true

	/**
	 * A flag that indicates whether we are attempting to determine whether an
	 * expression can be summarized into a series of [LoadingEffect]s.
	 */
	private var determiningSummarizability = false

	/**
	 * Replace the boolean that indicates whether the current statement can be
	 * summarized into a sequence of [LoadingEffect]s.  It is set to true before
	 * executing a top-level statement, and set to false if an activity is
	 * performed that cannot be summarized.
	 *
	 * @param summarizable
	 *   The new value of the flag.
	 */
	@Synchronized
	fun statementCanBeSummarized(summarizable: Boolean)
	{
		if (determiningSummarizability)
		{
			if (debugUnsummarizedStatements
				&& !summarizable
				&& statementCanBeSummarized)
			{
				// Here's a good place for a breakpoint, to see why an
				// expression couldn't be summarized.
				val e = Throwable().fillInStackTrace()
				println("Disabled summary:\n${trace(e)}")
			}
			statementCanBeSummarized = summarizable
		}
	}

	/**
	 * Answer whether the current statement can be summarized into a sequence of
	 * [LoadingEffect]s.
	 *
	 * @return
	 *   The current value of the flag.
	 */
	fun statementCanBeSummarized() = statementCanBeSummarized

	/**
	 * The sequence of special effects performed by the current top-level
	 * statement of a module being compiled, prior to deserializing and
	 * executing the main [effectsAddedByTopStatement].
	 */
	private val earlyEffectsAddedByTopStatement = mutableListOf<LoadingEffect>()

	/**
	 * The sequence of effects performed by the current top-level statement of a
	 * module being compiled.
	 */
	private val effectsAddedByTopStatement = mutableListOf<LoadingEffect>()

	/**
	 * Record a [LoadingEffect] to ensure it will be replayed when the module
	 * which is currently being compiled is later loaded.
	 *
	 * @param anEffect
	 *   The effect to record.
	 */
	@Synchronized
	fun recordEarlyEffect(anEffect: LoadingEffect)
	{
		if (determiningSummarizability)
		{
			earlyEffectsAddedByTopStatement.add(anEffect)
		}
	}

	/**
	 * Record a [LoadingEffect] to ensure it will be replayed when the module
	 * which is currently being compiled is later loaded.
	 *
	 * @param anEffect
	 *   The effect to record.
	 */
	@Synchronized
	fun recordEffect(anEffect: LoadingEffect)
	{
		if (determiningSummarizability)
		{
			effectsAddedByTopStatement.add(anEffect)
		}
	}

	/**
	 * Set a flag that indicates we are determining if the effects of running
	 * a function can be summarized, and if so into what [LoadingEffect]s.
	 */
	@Synchronized
	fun startRecordingEffects()
	{
		assert(!determiningSummarizability)
		determiningSummarizability = true
		statementCanBeSummarized = enableFastLoader
		earlyEffectsAddedByTopStatement.clear()
		effectsAddedByTopStatement.clear()
	}

	/**
	 * Clear the flag that indicates whether we are determining if the effects
	 * of running a function can be summarized into [LoadingEffect]s.
	 */
	@Synchronized
	fun stopRecordingEffects()
	{
		assert(determiningSummarizability)
		determiningSummarizability = false
	}

	/**
	 * Answer the list of special [LoadingEffect]s that execute before the
	 * ordinary effects can be deserialized and executed.
	 *
	 * @return
	 *   The recorded [LoadingEffect]s.
	 */
	@Synchronized
	fun recordedEarlyEffects(): List<LoadingEffect> =
		earlyEffectsAddedByTopStatement.toList()

	/**
	 * Answer the list of ordinary [LoadingEffect]s.
	 *
	 * @return
	 *   The recorded [LoadingEffect]s.
	 */
	@Synchronized
	fun recordedEffects(): List<LoadingEffect> =
		effectsAddedByTopStatement.toList()

	/**
	 * A lock for the [styledRanges] run tree.
	 */
	private val styledRangesLock = ReentrantReadWriteLock()

	/**
	 * A [TreeMap] containing this module's styling information during
	 * compilation.
	 */
	@GuardedBy("styledRangesLock")
	private val styledRanges = RunTree<String>()

	/**
	 * Access the styledRanges in the [action] while holding the lock.
	 *
	 * @param action
	 *   The action to perform with [styledRanges] while holding the lock.
	 */
	@ThreadSafe
	fun <T> lockStyles(action: RunTree<String>.()->T): T =
		styledRangesLock.safeWrite { styledRanges.action() }

	/**
	 * Helper method to style a token's range in a particular named style, using
	 * the specified function to merge any style information previously attached
	 * to all or parts of the token's range.
	 *
	 * @param token
	 *   The token to style.  Only a token taken from the source of the current
	 *   module will have its style honored.
	 * @param style
	 *   The [system&#32;style][SystemStyle] to apply to the token.
	 * @param overwrite
	 *   Whether the new style should clobber the old style.
	 * @param editor
	 *   How to reconcile an existing style with the new style. Applied to the
	 *   existing style. Evaluates to the replacement style, which may be a
	 *   comma-separated composite of styles. Defaults to style composition.
	 */
	@ThreadSafe
	fun styleToken(
		token: A_Token,
		style: SystemStyle,
		overwrite: Boolean = false,
		editor: (String?)->String? = { old ->
			when (old)
			{
				null -> style.kotlinString
				else -> "$old,${style.kotlinString}"
			}
		})
	{
		if (!token.isInCurrentModule(module)) return
		lockStyles {
			val start = token.start().toLong()
			val pastEnd = token.pastEnd().toLong()
			edit(start, pastEnd) { old ->
				when (overwrite)
				{
					true -> style.kotlinString
					false -> editor(old)
				}
			}
		}
	}

	/**
	 * Helper method to style a token's range in a particular named style, using
	 * the specified function to merge any style information previously attached
	 * to all or parts of the token's range.
	 *
	 * @param tokens
	 *   The tokens to style.  Only tokens actually taken from the source of the
	 *   current module will be styled.
	 * @param style
	 *   The [system&#32;style][SystemStyle] to apply to the token.
	 * @param overwrite
	 *   Whether the new style should clobber the old style.
	 * @param editor
	 *   How to reconcile an existing style with the new style. Applied to the
	 *   existing style. Evaluates to the replacement style, which may be a
	 *   comma-separated composite of styles. Defaults to style composition.
	 */
	@ThreadSafe
	fun styleTokens(
		tokens: Iterable<A_Token>,
		style: SystemStyle,
		overwrite: Boolean = false,
		editor: (String?)->String? = { old ->
			when (old)
			{
				null -> style.kotlinString
				else -> "$old,${style.kotlinString}"
			}
		})
	{
		if (debugStyling)
		{
			val tokensString =
				tokens.joinToString(", ") { it.string().asNativeString() }
			println(
				"style macro tokens: $tokensString with ${style.kotlinString}"
			)
		}
		lockStyles {
			tokens.forEach { token ->
				if (token.isInCurrentModule(module))
				{
					val start = token.start().toLong()
					val pastEnd = token.pastEnd().toLong()
					edit(start, pastEnd) { old ->
						when (overwrite)
						{
							true -> style.kotlinString
							false -> editor(old)
						}
					}
				}
			}
		}
	}

	/**
	 * Helper method to style a token's range in a particular named style, using
	 * the specified function to merge any style information previously attached
	 * to all or parts of the token's range.
	 *
	 * @param token
	 *   The token to style.  Only a token taken from the source of the current
	 *   module will have its style honored.
	 * @param style
	 *   The optional style to apply to the token. If `null`, then clear the
	 *   style iff `overwrite` is `true`; otherwise, preserve the original
	 *   style.
	 * @param overwrite
	 *   Whether the new style should clobber the old style.
	 * @param editor
	 *   How to reconcile an existing style with the new style. Applied to the
	 *   existing style. Evaluates to the replacement style, which may be a
	 *   comma-separated composite of styles. Defaults to style composition.
	 */
	@ThreadSafe
	fun styleToken(
		token: A_Token,
		style: String?,
		overwrite: Boolean = false,
		editor: (String?)->String? = { old ->
			when
			{
				old === null -> style
				style === null -> old
				else -> "$old,$style"
			}
		})
	{
		if (!token.isInCurrentModule(module)) return
		lockStyles {
			val start = token.start().toLong()
			val pastEnd = token.pastEnd().toLong()
			edit(start, pastEnd) { old ->
				when (overwrite)
				{
					true -> style
					false -> editor(old)
				}
			}
		}
	}

	/**
	 * Helper method to style a token's range in a particular named style, using
	 * the specified function to merge any style information previously attached
	 * to all or parts of the token's range.
	 *
	 * @param tokens
	 *   The tokens to style.  Only tokens actually taken from the source of the
	 *   current module will be styled.
	 * @param style
	 *   The optional style to apply to the token. If `null`, then clear the
	 *   style iff `overwrite` is `true`; otherwise, preserve the original
	 *   style.
	 * @param overwrite
	 *   Whether the new style should clobber the old style.
	 * @param editor
	 *   How to reconcile an existing style with the new style. Applied to the
	 *   existing style. Evaluates to the replacement style, which may be a
	 *   comma-separated composite of styles. Defaults to style composition.
	 */
	@ThreadSafe
	fun styleTokens(
		tokens: Iterable<A_Token>,
		style: String?,
		overwrite: Boolean = false,
		editor: (String?)->String? = { old ->
			when
			{
				old === null -> style
				style === null -> old
				else -> "$old,$style"
			}
		}
	) = lockStyles {
		tokens.forEach { token ->
			if (token.isInCurrentModule(module))
			{
				val start = token.start().toLong()
				val pastEnd = token.pastEnd().toLong()
				edit(start, pastEnd) { old ->
					when (overwrite)
					{
						true -> style
						false -> editor(old)
					}
				}
			}
		}
	}

	/**
	 * Helper method to style an ordinary string literal, i.e., one that is not
	 * also a method name.
	 *
	 * @param stringLiteralToken
	 *   The string literal to style.
	 */
	@ThreadSafe
	fun styleStringLiteral(stringLiteralToken: A_Token)
	{
		if (!stringLiteralToken.isInCurrentModule(module)) return
		lockStyles {
			val characters = stringLiteralToken.string().iterator() as
				ListIterator<A_Character>
			var start = stringLiteralToken.start().toLong()
			while (characters.hasNext())
			{
				var count = 0L
				when (characters.next().codePoint)
				{
					'"'.code ->
					{
						edit(start, start + 1) {
							SystemStyle.STRING_ESCAPE_SEQUENCE.kotlinString
						}
						start++
					}
					'\\'.code ->
					{
						count++
						// We know that the string lexed correctly, so there
						// can't be a dangling escape.
						when (characters.next().codePoint)
						{
							'('.code ->
							{
								count++
								// Search for the close parenthesis.
								while (characters.hasNext())
								{
									count++
									if (characters.next().codePoint == ')'.code)
									{
										edit(start, start + count) {
											SystemStyle
												.STRING_ESCAPE_SEQUENCE
												.kotlinString
										}
										start += count
										break
									}
								}
							}
							'n'.code, 'r'.code, 't'.code,
							'\\'.code, '\"'.code, '|'.code ->
							{
								count++
								edit(start, start + count) {
									SystemStyle
										.STRING_ESCAPE_SEQUENCE
										.kotlinString
								}
								start += count
							}
							'\r'.code, '\n'.code ->
							{
								// Explicitly don't style the escaped carriage
								// return or line feed, but do style the
								// backslash.
								edit(start, start + count) {
									SystemStyle
										.STRING_ESCAPE_SEQUENCE
										.kotlinString
								}
								start += count + 1
							}
							// We know that the string lexed correctly, so there
							// can't be a malformed escape.
							else ->
							{
								assert(false) { "Unreachable" }
							}
						}
					}
					else ->
					{
						count++
						while (characters.hasNext())
						{
							val c = characters.next().codePoint
							if (c == '"'.code || c == '\\'.code)
							{
								edit(start, start + count) {
									SystemStyle.STRING_LITERAL.kotlinString
								}
								start += count
								characters.previous()
								break
							}
							else
							{
								count++
							}
						}
						// We know that the string lexed correctly, so there
						// have to be more characters (because we haven't seen
						// the closing double quote yet).
						assert(characters.hasNext())
					}
				}
			}
		}
	}

	/**
	 * Helper method to style a method name. Supersedes [styleStringLiteral]. If
	 * the [stringLiteralToken] is not a well-formed method name, don't style
	 * it.
	 *
	 * @param stringLiteralToken
	 *   The string literal to style as a method name.
	 */
	@ThreadSafe
	fun styleMethodName(stringLiteralToken: A_Token)
	{
		if (!stringLiteralToken.isInCurrentModule(module)) return
		val innerToken = stringLiteralToken.literal()
		if (!innerToken.isLiteralToken()) return
		try
		{
			MessageSplitter(innerToken.literal())
		}
		catch (e: MalformedMessageException)
		{
			// This string literal is not actually a well-formed method name, so
			// don't attempt to style it as such.
			return
		}
		var start = stringLiteralToken.start().toLong()
		lockStyles {
			val characters =
				stringLiteralToken.string().iterator() as
					ListIterator<A_Character>
			while (characters.hasNext())
			{
				var count = 0L
				when (characters.next().codePoint)
				{
					'"'.code ->
					{
						edit(start, start + 1) {
							SystemStyle.METHOD_NAME.kotlinString
						}
						start++
					}
					'\\'.code ->
					{
						count++
						// We know that the string lexed correctly, so there
						// can't be a dangling escape.
						when (characters.next().codePoint)
						{
							'('.code ->
							{
								count++
								edit(start, start + count) {
									SystemStyle
										.STRING_ESCAPE_SEQUENCE
										.kotlinString
								}
								start += count
								count = 0
								var value = 0
								// Process the Unicode escape sequences, looking
								// for hidden metacharacters.
								while (characters.hasNext())
								{
									when (val c = characters.next().codePoint)
									{
										','.code, ')'.code ->
										{
											edit(start, start + count) {
												if (canBeBackQuoted(value))
												{
													SystemStyle
														.METHOD_NAME
														.kotlinString
												}
												else
												{
													SystemStyle
														.STRING_ESCAPE_SEQUENCE
														.kotlinString
												}
											}
											start += count
											count = 0
											value = 0
											edit(start, start + 1) {
												SystemStyle
													.STRING_ESCAPE_SEQUENCE
													.kotlinString
											}
											start++
											if (c == ')'.code) break
										}
										in '0'.code .. '9'.code ->
										{
											count++
											value = (value shl 4) + c - '0'.code
										}
										in 'A'.code .. 'F'.code ->
										{
											count++
											value = (value shl 4) +
												c - 'A'.code + 10
										}
										in 'a'.code .. 'f'.code ->
										{
											count++
											value = (value shl 4) +
												c - 'a'.code + 10
										}
										else ->
										{
											assert(false) { "Unreachable" }
										}
									}
								}
							}
							'n'.code, 'r'.code, 't'.code,
							'\\'.code, '\"'.code, '|'.code ->
							{
								count++
								edit(start, start + count) {
									SystemStyle
										.STRING_ESCAPE_SEQUENCE
										.kotlinString
								}
								start += count
							}
							'\r'.code, '\n'.code ->
							{
								// Explicitly don't style the escaped carriage
								// return or line feed, but do style the
								// backslash.
								edit(start, start + count) {
									SystemStyle
										.STRING_ESCAPE_SEQUENCE
										.kotlinString
								}
								start += count + 1
							}
							// We know that the string lexed correctly, so there
							// can't be a malformed escape.
							else ->
							{
								assert(false) { "Unreachable" }
							}
						}
					}
					BACK_QUOTE.codepoint ->
					{
						// We know that the message split correctly, so there
						// can't be a dangling escape.
						val c = characters.next().codePoint
						assert(canBeBackQuoted(c))
						edit(start, start + 1) {
							SystemStyle.METHOD_NAME.kotlinString
						}
						edit(start + 1, start + 2) {
							SystemStyle.STRING_LITERAL.kotlinString
						}
						start += 2
					}
					CLOSE_GUILLEMET.codepoint,
					DOUBLE_DAGGER.codepoint,
					DOUBLE_QUESTION_MARK.codepoint,
					ELLIPSIS.codepoint,
					EXCLAMATION_MARK.codepoint,
					OCTOTHORP.codepoint,
					OPEN_GUILLEMET.codepoint,
					QUESTION_MARK.codepoint,
					SECTION_SIGN.codepoint,
					SINGLE_DAGGER.codepoint,
					TILDE.codepoint,
					UNDERSCORE.codepoint,
					UP_ARROW.codepoint,
					VERTICAL_BAR.codepoint ->
					{
						edit(start, start + 1) {
							SystemStyle.METHOD_NAME.kotlinString
						}
						start++
					}
					else ->
					{
						count++
						while (characters.hasNext())
						{
							val c = characters.next().codePoint
							if (c == '"'.code
								|| c == '\\'.code
								|| canBeBackQuoted(c))
							{
								edit(start, start + count) {
									SystemStyle.STRING_LITERAL.kotlinString
								}
								start += count
								characters.previous()
								break
							}
							else
							{
								count++
							}
						}
						// We know that the string lexed correctly, so there
						// have to be more characters (because we haven't seen
						// the closing double quote yet).
						assert(characters.hasNext())
					}
				}
			}
		}
	}

	/**
	 * A mapping from ranges where variable uses occur to ranges where the
	 * corresponding declarations occur.
	 */
	@GuardedBy("styledRangesLock")
	val usesToDefinitions = RunTree<LongRange>()

	/**
	 * Access the [usesToDefinitions] in the [action] while holding the lock.
	 *
	 * @param action
	 *   The action to perform with [usesToDefinitions] while holding the lock.
	 */
	@ThreadSafe
	fun <T> lockUsesToDefinitions(action: RunTree<LongRange>.()->T): T =
		styledRangesLock.safeWrite { usesToDefinitions.action() }

	/**
	 * A [variable-use][VariableUsePhraseDescriptor] was encountered, so record
	 * information about where it is, and where its associated definition is.
	 */
	fun addVariableUse(useToken: A_Token, declarationToken: A_Token)
	{
		if (!useToken.isInCurrentModule(module)) return
		if (!declarationToken.isInCurrentModule(module)) return
		val useStart = useToken.start().toLong()
		val declarationRange = declarationToken.start().toLong() ..
			declarationToken.end().toLong()
		styledRangesLock.safeWrite {
			usesToDefinitions.edit(useStart, useToken.pastEnd().toLong()) {
				// Just overwrite it, in the unexpected case of a conflict.
				declarationRange
			}
		}
	}

	/**
	 * Set up the [rootBundleTree] and [lexicalScanner] for compiling the body
	 * of the module.
	 */
	fun prepareForCompilingModuleBody()
	{
		rootBundleTree = module.buildFilteredBundleTree()
		lexicalScanner = module.createLexicalScanner()
	}

	/**
	 * Clear the [rootBundleTree] and [lexicalScanner] in preparation for
	 * loading (not compiling) the body of the module.
	 */
	fun prepareForLoadingModuleBody()
	{
		rootBundleTree = nil
		lexicalScanner = null
	}

	/** The currently unresolved forward method declarations. */
	var pendingForwards: A_Set = emptySet

	/**
	 * The given forward is in the process of being resolved. A real definition
	 * is about to be added to the method tables, so remove the forward now.
	 *
	 * @param forwardDefinition
	 *   A [forward][ForwardDefinitionDescriptor] declaration.
	 */
	private fun removeForward(forwardDefinition: A_Definition)
	{
		val method = forwardDefinition.definitionMethod
		when
		{
			!pendingForwards.hasElement(forwardDefinition) ->
				error("Inconsistent forward declaration handling code")
			!method.includesDefinition(forwardDefinition) ->
				error("Inconsistent forward declaration handling code")
		}
		pendingForwards =
			pendingForwards.setWithoutElementCanDestroy(forwardDefinition, true)
				.makeShared()
		method.removeDefinition(forwardDefinition)
		module.resolveForward(forwardDefinition)
	}

	/**
	 * This is a forward declaration of a method. Insert an appropriately
	 * stubbed definition in the module's method dictionary, and add it to the
	 * list of methods needing to be declared later in this module.
	 *
	 * @param methodName
	 *   The [method&#32;name][AtomDescriptor].
	 * @param bodySignature
	 *   A function [type][MethodDefinitionDescriptor] at which to create a
	 *   forward definition.
	 * @throws MalformedMessageException
	 *   If the message name is malformed.
	 * @throws SignatureException
	 *   If there is a problem with the signature.
	 */
	@Throws(
		MalformedMessageException::class,
		SignatureException::class)
	fun addForwardStub(
		methodName: A_Atom,
		bodySignature: A_Type)
	{
		methodName.makeShared()
		bodySignature.makeShared()
		val bundle: A_Bundle = methodName.bundleOrCreate()
		val splitter: MessageSplitter = bundle.messageSplitter
		splitter.checkImplementationSignature(bodySignature)
		val bodyArgsTupleType = bodySignature.argsTupleType
		// Add the stubbed method definition.
		val method: A_Method = bundle.bundleMethod
		method.definitionsTuple.forEach { definition ->
			val existingType = definition.bodySignature()
			if (existingType.argsTupleType.equals(bodyArgsTupleType))
			{
				throw SignatureException(E_REDEFINED_WITH_SAME_ARGUMENT_TYPES)
			}
			if (existingType.acceptsArgTypesFromFunctionType(bodySignature))
			{
				if (!bodySignature.returnType.isSubtypeOf(
						existingType.returnType))
				{
					throw SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS)
				}
			}
			if (bodySignature.acceptsArgTypesFromFunctionType(existingType))
			{
				if (!existingType.returnType.isSubtypeOf(
						bodySignature.returnType))
				{
					throw SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS)
				}
			}
		}
		// Only bother with adding and resolving forwards during compilation.
		if (phase == EXECUTING_FOR_COMPILE)
		{
			val newForward: A_Definition = newForwardDefinition(
				method, module, bodySignature)
			method.methodAddDefinition(newForward)
			recordEffect(LoadingEffectToAddDefinition(bundle, newForward))
			val theModule = module
			val root = rootBundleTree
			theModule.lock {
				theModule.moduleAddDefinition(newForward)
				pendingForwards =
					pendingForwards.setWithElementCanDestroy(newForward, true)
						.makeShared()
				val plan = bundle.definitionParsingPlans.mapAt(newForward)
				val planInProgress = newPlanInProgress(plan, 1)
				root.addPlanInProgress(planInProgress)
			}
		}
	}

	/**
	 * Add the method definition. The precedence rules can change at any time.
	 *
	 * @param methodName
	 *   The method's [name][AtomDescriptor].
	 * @param bodyBlock
	 *   The body [function][FunctionDescriptor].
	 * @throws MalformedMessageException
	 *   If the message name is malformed.
	 * @return
	 *   The newly added [A_Definition].
	 * @throws SignatureException
	 *   If the signature is invalid.
	 */
	@Throws(
		MalformedMessageException::class,
		SignatureException::class)
	fun addMethodBody(
		methodName: A_Atom,
		bodyBlock: A_Function
	): A_Definition
	{
		assert(methodName.isAtom)
		assert(bodyBlock.isFunction)
		val bundle = methodName.bundleOrCreate()
		val splitter = bundle.messageSplitter
		splitter.checkImplementationSignature(bodyBlock.kind())
		val numArgs = splitter.numberOfArguments
		if (bodyBlock.code().numArgs() != numArgs)
		{
			throw SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		val newDefinition = newMethodDefinition(
			bundle.bundleMethod,
			module,
			bodyBlock.makeShared())
		addDefinition(methodName.makeShared(), newDefinition)
		return newDefinition
	}

	/**
	 * Add the [abstract][AbstractDefinitionDescriptor] method signature. A
	 * class is considered abstract if there are any abstract methods that
	 * haven't been overridden with definitions for it.
	 *
	 * @param methodName
	 *   A method [name][AtomDescriptor].
	 * @param bodySignature
	 *   The function [type][FunctionTypeDescriptor].
	 * @throws MalformedMessageException
	 *   If the message name is malformed.
	 * @throws SignatureException
	 *   If there is a problem with the signature.
	 */
	@Throws(MalformedMessageException::class, SignatureException::class)
	fun addAbstractSignature(
		methodName: A_Atom,
		bodySignature: A_Type)
	{
		val bundle: A_Bundle = methodName.bundleOrCreate()
		val splitter: MessageSplitter = bundle.messageSplitter
		val numArgs = splitter.numberOfArguments
		val bodyArgsSizes = bodySignature.argsTupleType.sizeRange
		if (!bodyArgsSizes.lowerBound.equalsInt(numArgs)
			|| !bodyArgsSizes.upperBound.equalsInt(numArgs))
		{
			throw SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		assert(bodyArgsSizes.upperBound.equalsInt(numArgs))
		{
			"Wrong number of arguments in abstract method signature"
		}
		addDefinition(
			methodName.makeShared(),
			newAbstractDefinition(
				bundle.bundleMethod,
				module,
				bodySignature.makeShared()))
	}

	/**
	 * Add the new [A_Definition] to its [A_Method], via the captured [A_Bundle]
	 * (otherwise using a random bundle from the method would break replay if a
	 * [P_Alias] happens after the definition in the same grouped step). Also
	 * update the loader's [rootBundleTree] as needed.
	 *
	 * @param newDefinition
	 *   The definition to add.
	 * @throws SignatureException
	 *   If the signature disagrees with existing definitions and forwards.
	 */
	@Throws(SignatureException::class)
	private fun addDefinition(
		methodName: A_Atom,
		newDefinition: A_Definition)
	{
		val method = newDefinition.definitionMethod
		val bodySignature = newDefinition.bodySignature()
		val argsTupleType = bodySignature.argsTupleType
		var forward: A_Definition? = null
		method.definitionsTuple.forEach { existingDefinition ->
			val existingType = existingDefinition.bodySignature()
			val same = existingType.argsTupleType.equals(argsTupleType)
			if (same)
			{
				when
				{
					!existingDefinition.isForwardDefinition() ->
						throw SignatureException(
							E_REDEFINED_WITH_SAME_ARGUMENT_TYPES)
					!existingType.returnType.equals(bodySignature.returnType) ->
						throw SignatureException(
							E_METHOD_RETURN_TYPE_NOT_AS_FORWARD_DECLARED)
				}
				forward = existingDefinition
			}
			if (existingType.acceptsArgTypesFromFunctionType(bodySignature))
			{
				if (!bodySignature.returnType.isSubtypeOf(
						existingType.returnType))
				{
					throw SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS)
				}
			}
			if (bodySignature.acceptsArgTypesFromFunctionType(existingType))
			{
				if (!existingType.returnType.isSubtypeOf(
						bodySignature.returnType))
				{
					throw SignatureException(
						E_RESULT_TYPE_SHOULD_COVARY_WITH_ARGUMENTS)
				}
			}
		}
		if (phase == EXECUTING_FOR_COMPILE)
		{
			module.lock {
				val root = rootBundleTree
				forward?.let { forward ->
					method.bundles.forEach { bundle ->
						if (module.hasAncestor(bundle.message.issuingModule))
						{
							// Remove the appropriate forwarder plan from the
							// bundle tree.
							val plan: A_DefinitionParsingPlan =
								bundle.definitionParsingPlans.mapAt(forward)
							val planInProgress = newPlanInProgress(plan, 1)
							root.removePlanInProgress(planInProgress)
						}
					}
					removeForward(forward)
				}
				try
				{
					method.methodAddDefinition(newDefinition)
				}
				catch (e: SignatureException)
				{
					assert(false) { "Signature was already vetted" }
					return@lock
				}
				recordEffect(
					LoadingEffectToAddDefinition(
						methodName.bundleOrCreate(), newDefinition))
				method.bundles.forEach { bundle ->
					if (module.hasAncestor(bundle.message.issuingModule))
					{
						val plan: A_DefinitionParsingPlan =
							bundle.definitionParsingPlans.mapAt(newDefinition)
						val planInProgress = newPlanInProgress(plan, 1)
						root.addPlanInProgress(planInProgress)
					}
				}
				module.moduleAddDefinition(newDefinition)
				val topStart = topLevelStatementBeingCompiled!!
					.startingLineNumber
				manifestEntries!!.add(
					when
					{
						newDefinition.isMethodDefinition() ->
						{
							val body = newDefinition.bodyBlock()
							ModuleManifestEntry(
								SideEffectKind.METHOD_DEFINITION_KIND,
								methodName.atomName.asNativeString(),
								topStart,
								body.code().codeStartingLineNumber,
								body)
						}
						newDefinition.isForwardDefinition() ->
							ModuleManifestEntry(
								SideEffectKind.FORWARD_METHOD_DEFINITION_KIND,
								methodName.atomName.asNativeString(),
								topStart,
								topStart)
						newDefinition.isAbstractDefinition() ->
							ModuleManifestEntry(
								SideEffectKind.ABSTRACT_METHOD_DEFINITION_KIND,
								methodName.atomName.asNativeString(),
								topStart,
								topStart)
						else -> throw UnsupportedOperationException(
							"Unknown definition kind")
					})
			}
		}
		else
		{
			try
			{
				method.methodAddDefinition(newDefinition)
			}
			catch (e: SignatureException)
			{
				assert(false) { "Signature was already vetted" }
				return
			}
			module.moduleAddDefinition(newDefinition)
		}
	}

	/**
	 * Add the macro definition. The precedence rules can not change after the
	 * first definition is encountered, so set them to 'no restrictions' if
	 * they're not set already.
	 *
	 * @param methodName
	 *   The macro's name, an [atom][AtomDescriptor].
	 * @param macroBody
	 *   A [function][FunctionDescriptor] that transforms phrases.
	 * @param prefixFunctions
	 *   The tuple of functions to run during macro parsing, corresponding with
	 *   occurrences of section checkpoints ("§") in the macro name.
	 * @throws MalformedMessageException
	 *   If the macro signature is malformed.
	 * @throws SignatureException
	 *   If the macro signature is invalid.
	 */
	@Throws(
		MalformedMessageException::class,
		SignatureException::class)
	fun addMacroBody(
		methodName: A_Atom,
		macroBody: A_Function,
		prefixFunctions: A_Tuple,
		ignoreSeals: Boolean)
	{
		assert(methodName.isAtom)
		assert(macroBody.isFunction)
		val bundle = methodName.bundleOrCreate()
		val splitter = bundle.messageSplitter
		val numArgs = splitter.numberOfArguments
		val macroCode = macroBody.code()
		when
		{
			macroCode.numArgs() != numArgs ->
				throw SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS)
			!macroCode.functionType().returnType.isSubtypeOf(
					PARSE_PHRASE.mostGeneralType) ->
				throw SignatureException(E_MACRO_MUST_RETURN_A_PHRASE)
		}
		// Make it so we can safely hold onto these things in the VM.
		methodName.makeShared()
		macroBody.makeShared()
		// Add the macro definition.
		val macroDefinition = newMacroDefinition(
			bundle, module, macroBody, prefixFunctions)
		val macroBodyType = macroBody.kind()
		val argsType = macroBodyType.argsTupleType
		// Note: Macro definitions don't have to satisfy a covariance
		// relationship with their result types, since they're static.
		if (bundle.macrosTuple.any { existingDef ->
			argsType.equals(existingDef.bodySignature().argsTupleType)
		})
		{
			throw SignatureException(E_REDEFINED_WITH_SAME_ARGUMENT_TYPES)
		}
		// This may throw a SignatureException prior to making semantic changes
		// to the runtime.
		bundle.bundleAddMacro(macroDefinition, ignoreSeals)
		module.moduleAddMacro(macroDefinition)
		if (phase == EXECUTING_FOR_COMPILE)
		{
			recordEffect(LoadingEffectToAddMacro(bundle, macroDefinition))
			module.lock {
				manifestEntries!!.add(
					ModuleManifestEntry(
						SideEffectKind.MACRO_DEFINITION_KIND,
						methodName.atomName.asNativeString(),
						topLevelStatementBeingCompiled!!.startingLineNumber,
						macroCode.codeStartingLineNumber,
						macroBody))
				val plan: A_DefinitionParsingPlan =
					bundle.definitionParsingPlans.mapAt(macroDefinition)
				val planInProgress = newPlanInProgress(plan, 1)
				rootBundleTree.addPlanInProgress(planInProgress)
			}
		}
	}

	/**
	 * Add a semantic restriction to its associated method.
	 *
	 * @param restriction
	 *   A [semantic&#32;restriction][SemanticRestrictionDescriptor] that
	 *   validates the static types of arguments at call sites.
	 * @throws SignatureException
	 *   If the signature is invalid.
	 */
	@Throws(SignatureException::class)
	fun addSemanticRestriction(restriction: A_SemanticRestriction)
	{
		val method = restriction.definitionMethod()
		val function = restriction.function()
		if (function.code().numArgs() != method.numArgs)
		{
			throw SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		runtime.addSemanticRestriction(restriction)
		val atom = method.chooseBundle(module).message
		recordEffect(
			LoadingEffectToRunPrimitive(
				SpecialMethodAtom.SEMANTIC_RESTRICTION.bundle,
				atom,
				function))
		val theModule = module
		val code = function.code()
		theModule.lock {
			theModule.moduleAddSemanticRestriction(restriction)
			if (phase == EXECUTING_FOR_COMPILE)
			{
				manifestEntries!!.add(
					ModuleManifestEntry(
						SideEffectKind.SEMANTIC_RESTRICTION_KIND,
						atom.atomName.asNativeString(),
						topLevelStatementBeingCompiled!!.startingLineNumber,
						code.codeStartingLineNumber,
						function))
			}
		}
	}

	/**
	 * Add a seal to the method associated with the given method name.
	 *
	 * @param methodName
	 *   The method name, an [atom][AtomDescriptor].
	 * @param seal
	 *   The signature at which to seal the method.
	 * @throws MalformedMessageException
	 *   If the macro signature is malformed.
	 * @throws SignatureException
	 *   If the macro signature is invalid.
	 */
	@Throws(
		MalformedMessageException::class,
		SignatureException::class)
	fun addSeal(
		methodName: A_Atom,
		seal: A_Tuple)
	{
		assert(methodName.isAtom)
		assert(seal.isTuple)
		val bundle = methodName.bundleOrCreate()
		val splitter = bundle.messageSplitter
		if (seal.tupleSize != splitter.numberOfArguments)
		{
			throw SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		methodName.makeShared()
		seal.makeShared()
		runtime.addSeal(methodName, seal)
		module.addSeal(methodName, seal)
		recordEffect(
			LoadingEffectToRunPrimitive(
				SpecialMethodAtom.SEAL.bundle, methodName, seal))
		if (phase == EXECUTING_FOR_COMPILE)
		{
			manifestEntries!!.add(
				ModuleManifestEntry(
					SideEffectKind.SEAL_KIND,
					methodName.atomName.asNativeString(),
					topLevelStatementBeingCompiled!!.startingLineNumber,
					topLevelStatementBeingCompiled!!.startingLineNumber))
		}
	}

	/**
	 * The modularity scheme should prevent all inter-modular method conflicts.
	 * Precedence is specified as an array of message sets that are not allowed
	 * to be messages generating the arguments of this message.  For example,
	 * `<{"_+_"}, {"_+_", "_*_"}>` for the `"_*_"` operator makes `*` bind
	 * tighter than `+`, and also groups multiple `*`'s left-to-right.
	 *
	 * Note that we don't have to prevent L2 code from running, since the
	 * grammatical restrictions only affect parsing.  We still have to latch
	 * access to the grammatical restrictions to avoid read/write conflicts.
	 *
	 * @param parentAtoms
	 *   An [A_Set] of [A_Atom]s that name the message bundles that are to have
	 *   their arguments constrained.
	 * @param illegalArgumentMessages
	 *   The [A_Tuple] of [A_Set]s of [A_Atom]s that name methods.
	 * @throws MalformedMessageException
	 *   If one of the specified names is inappropriate as a method name.
	 * @throws SignatureException
	 *   If one of the specified names is inappropriate as a method name.
	 */
	@Throws(
		MalformedMessageException::class,
		SignatureException::class)
	fun addGrammaticalRestrictions(
		parentAtoms: A_Set,
		illegalArgumentMessages: A_Tuple)
	{
		parentAtoms.makeShared()
		illegalArgumentMessages.makeShared()
		val bundleSetList = illegalArgumentMessages.map { atomsSet ->
			var bundleSet = emptySet
			atomsSet.forEach { atom ->
				bundleSet = bundleSet.setWithElementCanDestroy(
					atom.bundleOrCreate(), true)
			}
			bundleSet.makeShared()
		}
		val bundleSetTuple = tupleFromList(bundleSetList)
		parentAtoms.forEach { parentAtom ->
			val bundle: A_Bundle = parentAtom.bundleOrCreate()
			val splitter: MessageSplitter = bundle.messageSplitter
			val numArgs = splitter.leafArgumentCount
			if (illegalArgumentMessages.tupleSize != numArgs)
			{
				throw SignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS)
			}
			val grammaticalRestriction =
				newGrammaticalRestriction(bundleSetTuple, bundle, module)
			val root = rootBundleTree
			val theModule = module
			theModule.lock {
				bundle.addGrammaticalRestriction(grammaticalRestriction)
				theModule.moduleAddGrammaticalRestriction(
					grammaticalRestriction)
				if (phase != EXECUTING_FOR_COMPILE) return@lock
				// Update the message bundle tree to accommodate the new
				// grammatical restriction.
				val treesToVisit =
					ArrayDeque<Pair<A_BundleTree, A_ParsingPlanInProgress>>()
				bundle.definitionParsingPlans.forEach { _, plan ->
					treesToVisit.addLast(root to newPlanInProgress(plan, 1))
					while (treesToVisit.isNotEmpty())
					{
						val (tree, planInProgress) = treesToVisit.removeLast()
						tree.updateForNewGrammaticalRestriction(
							planInProgress, treesToVisit)
					}
				}
				if (phase == EXECUTING_FOR_COMPILE)
				{
					manifestEntries!!.add(
						ModuleManifestEntry(
							SideEffectKind.GRAMMATICAL_RESTRICTION_KIND,
							parentAtom.atomName.asNativeString(),
							topLevelStatementBeingCompiled!!.startingLineNumber,
							topLevelStatementBeingCompiled!!.startingLineNumber))
				}
			}
		}
		recordEffect(
			LoadingEffectToRunPrimitive(
				SpecialMethodAtom.GRAMMATICAL_RESTRICTION.bundle,
				parentAtoms,
				illegalArgumentMessages))
	}

	/**
	 * Define and install a new [styler][StylerDescriptor] based on the given
	 * [stylerFunction].  Install it for the given [A_Bundle]'s method, within
	 * the current [A_Module].
	 *
	 * @param bundle
	 *   The [A_Bundle] whose method will have the styler added.
	 * @param stylerFunction
	 *   The [A_Function], a [stylerFunctionType], which is to be executed to
	 *   style invocations of the [bundle] or its aliases.
	 *
	 * @throws AvailException
	 *   With [E_STYLER_ALREADY_SET_BY_THIS_MODULE] if this module has already
	 *   defined a styler for this method.
	 */
	@Throws(AvailException::class)
	fun addStyler(bundle: A_Bundle, stylerFunction: A_Function)
	{
		val method = bundle.bundleMethod
		val styler = newStyler(stylerFunction, method, module)
		var bad = false
		method.updateStylers {
			bad = any { it.module.equals(module) }
			if (bad) this else setWithElementCanDestroy(styler, true)
		}
		if (bad)
		{
			throw AvailException(E_STYLER_ALREADY_SET_BY_THIS_MODULE)
		}
		module.moduleAddStyler(styler)
		if (phase == EXECUTING_FOR_COMPILE)
		{
			recordEffect(
				LoadingEffectToRunPrimitive(
					SpecialMethodAtom.SET_STYLER.bundle,
					bundle.message,
					stylerFunction))
		}
		val atomName = bundle.message.atomName
		val name = atomName.asNativeString()
		val code = stylerFunction.code()
		val stylerPrimSuffix = when (val stylerPrim = code.codePrimitive())
		{
			null -> ""
			else -> " (${stylerPrim.name})"
		}
		code.methodName = stringFrom(
			"Styler for $name$stylerPrimSuffix")
		if (debugStyling)
		{
			println("Defined styler: ${code.methodName}")
		}
	}

	/**
	 * Unbind the specified method definition from this loader and runtime.
	 *
	 * @param definition
	 *   A [definition][DefinitionDescriptor].
	 */
	fun removeDefinition(definition: A_Definition)
	{
		if (definition.isForwardDefinition())
		{
			pendingForwards =
				pendingForwards.setWithoutElementCanDestroy(definition, true)
		}
		runtime.removeDefinition(definition)
	}

	/**
	 * Unbind the specified macro definition from this loader and runtime.
	 *
	 * @param macro
	 *   A [definition][DefinitionDescriptor].
	 */
	fun removeMacro(macro: A_Macro)
	{
		runtime.removeMacro(macro)
	}

	/**
	 * Run the specified [tuple][A_Tuple] of [functions][A_Function]
	 * sequentially.
	 *
	 * @param unloadFunctions
	 *   A tuple of unload functions.
	 * @param afterRunning
	 *   What to do after every unload function has completed.
	 */
	fun runUnloadFunctions(
		unloadFunctions: A_Tuple,
		afterRunning: () -> Unit)
	{
		val size = unloadFunctions.tupleSize
		// The index into the tuple of unload functions.
		if (size == 0)
		{
			// When there's at least one unload function, the fact that it forks
			// a fiber ensures the stack doesn't grow arbitrarily deep from
			// running afterRunning functions with ever deeper recursion, due to
			// the Thread agnosticism of Graph.ParallelVisitor.  Here we have
			// no unload functions, so break the direct cyclic call explicitly
			// by queueing an action.
			runtime.execute(loaderPriority, afterRunning)
		}
		else
		{
			var index = 1
			recurse { again ->
				if (index <= size)
				{
					val currentIndex = index++
					val unloadFunction: A_Function =
						unloadFunctions.tupleAt(currentIndex)
					val fiber = newFiber(
						TOP.o, runtime, textInterface, loaderPriority)
					{
						formatString(
							"Unload function #%d/%d for module %s",
							currentIndex,
							size,
							module.shortModuleNameNative)
					}
					fiber.setSuccessAndFailure({ again() }, { again() })
					runtime.runOutermostFunction(
						fiber, unloadFunction, emptyList())
				}
				else
				{
					runtime.execute(loaderPriority, afterRunning)
				}
			}
		}
	}

	/**
	 * Look up the given [A_String] in the current [module][ModuleDescriptor]'s
	 * namespace. Answer the [atom][AtomDescriptor] associated with the string,
	 * creating the true name if necessary. A local true name always hides other
	 * true names.  If `isExplicitSubclassAtom` is true and we're creating a new
	 * atom, add the [SpecialAtom.EXPLICIT_SUBCLASSING_KEY] property.
	 *
	 * @param stringName
	 *   An Avail [string][A_String].
	 * @param ifNew
	 *   An [A_Atom] lambda to run if the atom had to be created.  This should
	 *   set up basic properties of the new atom, such as heritability and
	 *   whether it's for explicit object type subclassing.
	 * @return
	 *   An [atom][A_Atom].
	 * @throws AmbiguousNameException
	 *   If the string could represent several different true names.
	 */
	@JvmOverloads
	@Throws(AmbiguousNameException::class)
	fun lookupName(
		stringName: A_String,
		ifNew: (A_Atom.()->Unit)? = null
	): A_Atom = module.lock {
		//  Check if it's already defined somewhere...
		val who = module.trueNamesForStringName(stringName)
		when (who.setSize)
		{
			1 -> who.single()
			0 ->
			{
				val newAtom = createAtom(stringName, module)
				ifNew?.invoke(newAtom)
				// Hoist creation of the atom to a block that runs prior to any
				// place that it might be used.
				recordEarlyEffect(
					LoadingEffectToRunPrimitive(
						when
						{
							newAtom.getAtomProperty(
								HERITABLE_KEY.atom
							).notNil -> CREATE_HERITABLE_ATOM.bundle
							newAtom.getAtomProperty(
								EXPLICIT_SUBCLASSING_KEY.atom
							).notNil -> CREATE_EXPLICIT_SUBCLASS_ATOM.bundle
							else -> CREATE_ATOM.bundle
						},
						stringName))
				if (phase == EXECUTING_FOR_COMPILE)
				{
					val topStart = topLevelStatementBeingCompiled!!
						.startingLineNumber
					manifestEntries!!.add(
						ModuleManifestEntry(
							SideEffectKind.ATOM_DEFINITION_KIND,
							stringName.asNativeString(),
							topStart,
							topStart))
				}
				newAtom.makeShared()
				module.addPrivateName(newAtom)
				newAtom
			}
			else -> throw AmbiguousNameException()
		}
	}

	/**
	 * Look up the given [string][StringDescriptor] in the current
	 * [module][ModuleDescriptor]'s namespace. Answer every
	 * [atom][AtomDescriptor] associated with the string. Never create a new
	 * atom.
	 *
	 * @param stringName
	 *   An Avail [string][A_String].
	 * @return
	 *   Every [atom][AtomDescriptor] associated with the name.
	 */
	fun lookupAtomsForName(stringName: A_String): A_Set = module.lock {
		val name = module.newNames.mapAtOrNull(stringName)
		val newNames = setFromCollection(listOfNotNull(name))
		val publicNames =
			module.importedNames.mapAtOrNull(stringName) ?: emptySet
		val privateNames =
			module.privateNames.mapAtOrNull(stringName) ?: emptySet
		newNames
			.setUnionCanDestroy(publicNames, true)
			.setUnionCanDestroy(privateNames, true)
	}

	companion object
	{
		/**
		 * Allow investigation of why a top-level expression is being excluded
		 * from summarization.
		 */
		var debugUnsummarizedStatements = false

		/**
		 * Show the top-level statements that are executed during loading or
		 * compilation.
		 */
		var debugLoadedStatements = false

		/**
		 * A flag that controls whether compilation attempts to use the
		 * fast-loader to rewrite some top-level statements into a faster form.
		 */
		var enableFastLoader = true

		/**
		 * If the current [Thread] is an [AvailThread], extract its
		 * [AvailLoader], if any.  Otherwise answer `null`.
		 */
		fun currentLoaderOrNull(): AvailLoader?
		{
			val availThread =
				Thread.currentThread() as? AvailThread ?: return null
			return availThread.interpreter.availLoaderOrNull()
		}

		/**
		 * Create an `AvailLoader` suitable for unloading the specified
		 * [module][ModuleDescriptor].
		 *
		 * @param runtime
		 *   The current [AvailRuntime].
		 * @param module
		 *   The module that will be unloaded.
		 * @param textInterface
		 *   The [TextInterface] for any [fiber][A_Fiber] started by the new
		 *   builder. @return An AvailLoader suitable for unloading the module.
		 */
		fun forUnloading(
			runtime: AvailRuntime,
			module: A_Module,
			textInterface: TextInterface
		): AvailLoader
		{
			val loader = AvailLoader(runtime, module, textInterface)
			// We had better not be removing forward declarations from an
			// already fully-loaded module.
			loader.pendingForwards = nil
			loader.phase = UNLOADING
			return loader
		}

		/**
		 * Define a special root bundle tree that's *only* for parsing method
		 * headers.
		 */
		private val moduleHeaderBundleRoot = newBundleTree(nil).apply {
			// Add the method that allows the header to be parsed.
			val headerMethodBundle = try
			{
				SpecialMethodAtom.MODULE_HEADER.atom.bundleOrCreate()
			}
			catch (e: MalformedMessageException)
			{
				assert(false) { "Malformed module header method name" }
				throw RuntimeException(e)
			}
			val headerPlan: A_DefinitionParsingPlan =
				headerMethodBundle.definitionParsingPlans.mapIterable
					.first()
					.value()
			addPlanInProgress(newPlanInProgress(headerPlan, 1))
		}

		/**
		 * The [LexicalScanner] used only for parsing module headers.
		 */
		private val moduleHeaderLexicalScanner = LexicalScanner { "(headers)" }
			.apply {
				// Add the string literal lexer.
				createPrimitiveLexerForHeaderParsing(
					P_BootstrapLexerStringFilter,
					P_BootstrapLexerStringBody,
					"string token lexer")

				// The module header uses keywords, e.g. "Extends".
				createPrimitiveLexerForHeaderParsing(
					P_BootstrapLexerKeywordFilter,
					P_BootstrapLexerKeywordBody,
					"keyword token lexer")

				// There's also punctuation in there, like commas.
				createPrimitiveLexerForHeaderParsing(
					P_BootstrapLexerOperatorFilter,
					P_BootstrapLexerOperatorBody,
					"operator token lexer")

				// It would be tricky with no whitespace!
				createPrimitiveLexerForHeaderParsing(
					P_BootstrapLexerWhitespaceFilter,
					P_BootstrapLexerWhitespaceBody,
					"whitespace lexer")

				// Slash-star-star-slash comments are legal in the header.
				createPrimitiveLexerForHeaderParsing(
					P_BootstrapLexerSlashStarCommentFilter,
					P_BootstrapLexerSlashStarCommentBody,
					"comment lexer")

				freezeFromChanges()
			}

		/**
		 * Create an [A_Lexer] from the given filter and body primitives, and
		 * install it in the specified atom's bundle.  Add the lexer to the root
		 * [A_BundleTree] of the receiver, a [LexicalScanner] used for parsing
		 * module headers.
		 *
		 * @param filterPrimitive
		 *   A primitive for filtering the lexer by its first character.
		 * @param bodyPrimitive
		 *   A primitive for constructing a tuple of tokens at the current
		 *   position.  Typically the tuple has zero or one tokens, but more can
		 *   be produced to indicate ambiguity within the lexer.
		 * @param atomName
		 *   The [A_Atom] under which to record the new lexer.
		 */
		private fun LexicalScanner.createPrimitiveLexerForHeaderParsing(
			filterPrimitive: Primitive,
			bodyPrimitive: Primitive,
			atomName: String)
		{
			val lexerFilter = createFunction(
				newPrimitiveRawFunction(filterPrimitive, nil, 0),
				emptyTuple)
			val lexerBody = createFunction(
				newPrimitiveRawFunction(bodyPrimitive, nil, 0),
				emptyTuple)
			val atom = createSpecialAtom(atomName)
			val bundle: A_Bundle = try
			{
				atom.bundleOrCreate()
			}
			catch (e: MalformedMessageException)
			{
				assert(false) { "Invalid special lexer name: $atomName" }
				throw RuntimeException(e)
			}
			val lexer = newLexer(
				lexerFilter, lexerBody, bundle.bundleMethod, nil)
			addLexer(lexer)
			// If the lexer body is a primitive which specifies a
			// bootstrapStyler, add that styler to the lexer's method.
			addBootstrapStyler(lexerBody.code(), atom, nil)
		}

		/**
		 * Answer a merge function that accepts an existing regional style and
		 * clobbers it with [replacement] iff the existing style is [original].
		 * The resultant function is suitable for use with [styleToken].
		 *
		 * @param original
		 *   The [style][SystemStyle] to replace.
		 * @param replacement
		 *   The replacement [style][SystemStyle] to use for [original].
		 * @return
		 *   The requested merge function.
		 */
		fun overrideStyle(
			original: SystemStyle,
			replacement: SystemStyle
		): (String?)->String? = { old ->
			when (old)
			{
				original.kotlinString -> replacement.kotlinString
				// Anything else was chosen for a narrower contextual reason, so
				// honor the styling decisions already made.
				else -> old
			}
		}

		/**
		 * Create and add a bootstrap [A_Styler] in the method specified by the
		 * [atom].  If the method already has a styler, do nothing.  Also do
		 * nothing if the [bodyCode] is not primitive, or its primitive doesn't
		 * specify a [bootstrapStyler][Primitive.bootstrapStyler].
		 *
		 * DO NOT record this action, as it will happen as an automatic
		 * consequence of adding the method definition or macro during
		 * fast-loading.
		 *
		 * @param bodyCode
		 *   The body of the method definition, macro, or lexer that was
		 *   defined.  If it's a primitive, its
		 *   [bootstrapStyler][Primitive.bootstrapStyler] will be looked up.
		 * @param atom
		 *   The [A_Atom] whose bundle's method that should have a styler added,
		 *   if indicated.
		 * @param module
		 *   Either [nil] for a system method, or the [A_Module] with which to
		 *   associate the new styler.
		 */
		fun addBootstrapStyler(
			bodyCode: A_RawFunction,
			atom: A_Atom,
			module: A_Module)
		{
			val prim = bodyCode.codePrimitive() ?: return
			val stylerPrim = prim.bootstrapStyler() ?: return
			val bundle = atom.bundleOrCreate()
			val method = bundle.bundleMethod
			if (method.methodStylers.setSize != 0) return
			// Pretend the synthetic styler function starts on the same line as the
			// body function.
			val stylerRawFunction = newPrimitiveRawFunction(
				stylerPrim, module, bodyCode.codeStartingLineNumber)
			val name = atom.atomName.asNativeString()
			val stylerPrimName = stylerPrim.name
			stylerRawFunction.methodName =
				stringFrom("Styler for $name ($stylerPrimName)")
			val styler = newStyler(
				createFunction(stylerRawFunction, emptyTuple), method, module)
			method.updateStylers { setWithElementCanDestroy(styler, true) }
			if (module.notNil)
			{
				module.moduleAddStyler(styler)
			}
			if (debugStyling)
			{
				println("Defined bootstrap styler: $atom")
			}
		}
	}
}
