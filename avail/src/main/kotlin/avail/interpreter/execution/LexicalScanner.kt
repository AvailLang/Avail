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

import avail.compiler.scanning.LexingState
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import avail.descriptor.fiber.FiberDescriptor.Companion.newLoaderFiber
import avail.descriptor.methods.A_Method.Companion.chooseBundle
import avail.descriptor.methods.A_Method.Companion.lexer
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.addLexer
import avail.descriptor.module.A_Module.Companion.moduleState
import avail.descriptor.module.ModuleDescriptor.State.Loading
import avail.descriptor.parsing.A_Lexer
import avail.descriptor.parsing.A_Lexer.Companion.definitionModule
import avail.descriptor.parsing.A_Lexer.Companion.lexerApplicability
import avail.descriptor.parsing.A_Lexer.Companion.lexerFilterFunction
import avail.descriptor.parsing.A_Lexer.Companion.lexerMethod
import avail.descriptor.parsing.A_Lexer.Companion.setLexerApplicability
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.asTuple
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.StringDescriptor.Companion.formatString
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A [LexicalScanner] tracks all visible [A_Lexer]s while compiling a module. It
 * maintains an [AtomicReferenceArray] from Latin-1 codepoints (U+0000..U+00FF)
 * to the [A_Tuple] of lexers whose filters passed for that codepoint.  A
 * separate [ConcurrentHashMap] tracks all remaining codepoints.
 *
 * There is also a special instance that is reused for scanning module headers.
 *
 * @property moduleNameProducer
 *   A function that produces the name of the module that this scanner is
 *   operating on behalf of, or "" for the scanner used for module headers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class LexicalScanner
constructor(
	val moduleNameProducer : ()->String)
{
	/**
	 * The [List] of all [lexers][A_Lexer] which are visible within the module
	 * being compiled.
	 */
	val allVisibleLexers = mutableListOf<A_Lexer>()

	/**
	 * When set, fail on attempts to change the lexical scanner.  This is a
	 * safety measure.
	 */
	@Volatile
	var frozen = false

	/**
	 * Ensure new [A_Lexer]s are not added after this point.  This is a safety
	 * measure.
	 */
	fun freezeFromChanges()
	{
		assert(!frozen)
		frozen = true
	}

	/**
	 * A 256-way dispatch table that takes a Latin-1 character's Unicode
	 * codepoint (which is in [0..255]) to a [tuple][A_Tuple] of
	 * [lexers][A_Lexer].  Non-Latin1 characters (i.e., with codepoints ≥ 256)
	 * are tracked separately in [nonLatin1Lexers].
	 *
	 * This array is populated lazily and incrementally, so if an entry is null,
	 * it should be constructed by testing the character against all visible
	 * lexers' filter functions.  Place the ones that pass into a set, then
	 * normalize it by looking up that set in a [map][canonicalLexerTuples] from
	 * such sets to tuples.  This causes two equal sets of lexers to be
	 * canonicalized to the same tuple, thereby reusing it.
	 *
	 * When a new lexer is defined, we null all entries of this dispatch table
	 * and clear the supplementary map, allowing the entries to be incrementally
	 * constructed.  We also clear the map from lexer sets to canonical tuples.
	 */
	private val latin1ApplicableLexers = AtomicReferenceArray<A_Tuple>(256)

	/**
	 * A [ConcurrentHashMap] from non-Latin-1 codepoint (i.e., ≥ 256) to the
	 * tuple of lexers that should run when that character is encountered at a
	 * lexing point.
	 */
	private val nonLatin1Lexers = ConcurrentHashMap<Int, A_Tuple>()

	/**
	 * The canonical mapping from each set of lexers to a tuple of lexers.
	 */
	private val canonicalLexerTuples = mutableMapOf<A_Set, A_Tuple>()

	/**
	 * Add an [A_Lexer].  Update not just the current lexing information for
	 * this loader, but also the specified atom's bundle's method and the
	 * current module.
	 *
	 * This must be called as an L1-safe task, which precludes execution of
	 * Avail code while it's running.  That does not preclude other non-Avail
	 * code from running (i.e., other L1-safe tasks), so this method is
	 * synchronized to achieve that safety.
	 *
	 * @param lexer
	 *   The [A_Lexer] to add.
	 */
	@Synchronized
	fun addLexer(lexer: A_Lexer)
	{
		assert(!frozen)
		lexer.lexerMethod.lexer = lexer
		val module: A_Module = lexer.definitionModule
		if (module.notNil)
		{
			if (module.moduleState == Loading)
			{
				module.addLexer(lexer)
			}
		}
		// Update the loader's lexing tables...
		allVisibleLexers.add(lexer)
		// Since the existence of at least one non-null entry in the Latin-1 or
		// non-Latin-1 tables implies there must be at least one canonical tuple
		// of lexers, we can skip clearing the tables if the canonical map is
		// empty.
		if (canonicalLexerTuples.isNotEmpty())
		{
			for (i in 0 .. 255)
			{
				latin1ApplicableLexers[i] = null
			}
			nonLatin1Lexers.clear()
			canonicalLexerTuples.clear()
		}
	}

	/**
	 * Collect the [lexers][A_Lexer] that should run when we encounter a
	 * character with the given ([Int]) code point, then pass this tuple of
	 * lexers to the supplied Kotlin function.
	 *
	 * We pass it forward rather than return it, since sometimes this requires
	 * lexer filter functions to run, which we must not do synchronously.
	 * However, if the lexer filters have already run for this code point, we
	 * *may* invoke the continuation synchronously for performance.
	 *
	 * @param lexingState
	 *   The [LexingState] at which the lexical scanning is happening.
	 * @param codePoint
	 *   The full Unicode code point in the range 0..1,114,111.
	 * @param continuation
	 *   What to invoke with the tuple of tokens at this position.
	 * @param onFailure
	 *   What to do if lexical scanning fails.
	 */
	fun getLexersForCodePointThen(
		lexingState: LexingState,
		codePoint: Int,
		continuation: (A_Tuple)->Unit,
		onFailure: (Map<A_Lexer, Throwable>)->Unit)
	{
		if (codePoint and 255.inv() == 0)
		{
			latin1ApplicableLexers[codePoint]?.let {
				continuation(it)
				return
			}
			// Run the filters to produce the set of applicable lexers, then use
			// the canonical map to make it a tuple, then invoke the
			// continuation with it.
			selectLexersPassingFilterThen(lexingState, codePoint)
			{ applicable, failures ->
				when
				{
					failures.isNotEmpty() ->
					{
						onFailure(failures)
						// Don't cache the successful lexer filter results,
						// because we shouldn't continue and neither should
						// lexing of any codePoint equal to the one that caused
						// this trouble.
					}
					else ->
					{
						val lexers = canonicalTupleOfLexers(applicable)
						// Just replace it, even if another thread beat us to
						// the punch, since it's semantically idempotent.
						latin1ApplicableLexers[codePoint] = lexers
						continuation(lexers)
					}
				}
			}
			return
		}

		// It's non-Latin1.
		val tuple = nonLatin1Lexers[codePoint]
		if (tuple !== null) return continuation(tuple)
		// Run the filters to produce the set of applicable lexers, then use the
		// canonical map to make it a tuple, then invoke the continuation with
		// it.
		selectLexersPassingFilterThen(lexingState, codePoint)
		{ applicable, failures ->
			if (failures.isNotEmpty())
			{
				onFailure(failures)
				// Don't cache the successful lexer filter results, because we
				// shouldn't continue and neither should lexing of any codePoint
				// equal to the one that caused this trouble.
				return@selectLexersPassingFilterThen
			}
			val lexers = canonicalTupleOfLexers(applicable)
			// Just replace it, even if another thread beat us to the punch,
			// since it's semantically idempotent.
			nonLatin1Lexers[codePoint] = lexers
			continuation(lexers)
		}
	}

	/**
	 * Given an [A_Set] of [A_Lexer]s applicable for some character, look up the
	 * corresponding canonical [A_Tuple], recording it if necessary.
	 */
	private fun canonicalTupleOfLexers(applicable: A_Set): A_Tuple =
		synchronized(canonicalLexerTuples) {
			canonicalLexerTuples[applicable] ?: run {
				val tuple = applicable.asTuple.makeShared()
				canonicalLexerTuples[applicable.makeShared()] = tuple
				tuple
			}
		}

	/**
	 * Collect the lexers that should run when we encounter a character with the
	 * given (int) code point, then pass this set of lexers to the supplied
	 * function.
	 *
	 * We pass it forward rather than return it, since sometimes this requires
	 * lexer filter functions to run, which we must not do synchronously.
	 * However, if the lexer filters have already run for this code point, we
	 * *may* invoke the continuation synchronously for performance.
	 *
	 * @param lexingState
	 *   The [LexingState] at which scanning encountered this codePoint for the
	 *   first time.
	 * @param codePoint
	 *   The full Unicode code point in the range 0..1114111.
	 * @param continuation
	 *   What to invoke with the [set][A_Set] of [lexers][A_Lexer] and a
	 *   (normally empty) map from lexer to throwable, indicating lexer filter
	 *   invocations that raised exceptions.
	 */
	private fun selectLexersPassingFilterThen(
		lexingState: LexingState,
		codePoint: Int,
		continuation: (A_Set, Map<A_Lexer, Throwable>)->Unit)
	{
		val applicableLexers = mutableListOf<A_Lexer>()
		val undecidedLexers = mutableListOf<A_Lexer>()
		when (codePoint)
		{
			in 0 .. 255 -> allVisibleLexers.forEach {
				when (it.lexerApplicability(codePoint))
				{
					null -> undecidedLexers.add(it)
					true -> applicableLexers.add(it)
					false -> { }
				}
			}
			else -> undecidedLexers.addAll(allVisibleLexers)
		}
		var countdown = undecidedLexers.size
		if (countdown == 0)
		{
			continuation(setFromCollection(applicableLexers), emptyMap())
			return
		}
		// Initially use the immutable emptyMap for the failureMap, but
		// replace it if/when the first error happens.
		val argsList = listOf(fromCodePoint(codePoint))
		val compilationContext = lexingState.compilationContext
		val loader = compilationContext.loader
		val joinLock = ReentrantLock()
		val failureMap = mutableMapOf<A_Lexer, Throwable>()
		val fibers = undecidedLexers.map { lexer ->
			val fiber = newLoaderFiber(booleanType, loader)
			{
				formatString(
					"Lexer filter %s for U+%04x in %s",
					lexer.lexerMethod.chooseBundle(loader.module)
						.message.atomName,
					codePoint,
					moduleNameProducer())
			}
			lexingState.setFiberContinuationsTrackingWork(
				fiber,
				{ boolObject: AvailObject ->
					val boolValue = boolObject.extractBoolean
					if (codePoint in 0 .. 255)
					{
						// Cache the filter result with the lexer itself, so
						// other modules can reuse it.
						lexer.setLexerApplicability(codePoint, boolValue)
					}
					val countdownHitZero = joinLock.withLock {
						if (boolValue)
						{
							applicableLexers.add(lexer)
						}
						countdown--
						assert(countdown >= 0)
						countdown == 0
					}
					if (countdownHitZero)
					{
						// This was the fiber reporting the last result.
						continuation(
							setFromCollection(applicableLexers), failureMap)
					}
				},
				{ throwable: Throwable ->
					val countdownHitZero = joinLock.withLock {
						failureMap[lexer] = throwable
						countdown--
						assert(countdown >= 0)
						countdown == 0
					}
					if (countdownHitZero)
					{
						// This was the fiber reporting the last result (a fiber
						// failure).
						continuation(
							setFromCollection(applicableLexers), failureMap)
					}
				})
			fiber
		}
		// Launch the fibers only after they've all been created.  That's
		// because we increment the queued count while setting the fibers'
		// success/failure continuations, with the corresponding increments of
		// the completed counts dealt with by wrapping the continuations. If a
		// fiber ran to completion before we could create them all, the counters
		// could collide, running the noMoreWorkUnits action before all fibers
		// got a chance to run.
		fibers.forEachIndexed { i, fiber ->
			loader.runtime.runOutermostFunction(
				fiber, undecidedLexers[i].lexerFilterFunction, argsList)
		}
	}
}
