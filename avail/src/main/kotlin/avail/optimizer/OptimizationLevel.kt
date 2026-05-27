/*
 * OptimizationLevel.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.optimizer

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.countdownToReoptimize
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2SimpleChunk
import avail.interpreter.levelTwoSimple.L2SimpleTranslator

/**
 * [OptimizationLevel] is an enum class indicating the possible degrees of
 * optimization effort.  These are arranged approximately monotonically
 * increasing in terms of both cost to generate and expected performance
 * improvement.
 *
 * @property countdown
 *   The value to use for the countdown after the translation at this level has
 *   taken place (i.e., how many calls to endure before the next level of
 *   optimization is attempted).
 *
 * @constructor
 * Construct the enum value.
 */
enum class OptimizationLevel
constructor(val countdown: Long)
{
	/**
	 * Unoptimized code, interpreted via Level One machinery.  Technically the
	 * current implementation only executes Level Two code, but the default
	 * Level Two chunk relies on a Level Two instruction that simply fetches
	 * each nybblecode and interprets it.
	 *
	 * The [countdown] is very small to encourage early translation of any
	 * function that is executed even a small number of times.
	 */
	UNOPTIMIZED(1L)
	{
		override fun optimize(code: A_RawFunction, interpreter: Interpreter)
		{
			L2SimpleTranslator.`🌺translateToLevelTwoSimple`(
				code, SIMPLE_TRANSLATION, interpreter)
		}
	},

	/**
	 * Translate the nybblecodes quickly into an [L2SimpleChunk].
	 */
	SIMPLE_TRANSLATION(1_000_000_000_000_000_000L) //TODO 1_000L) -- suppress L2.
	{
		override fun optimize(code: A_RawFunction, interpreter: Interpreter)
		{
			code.countdownToReoptimize(countdown)
			L1Translator.`🌼translateToLevelTwo`(
				code, FIRST_JVM_TRANSLATION, interpreter)
		}
	},

	/**
	 * The initial translation into Level Two instructions customized to a
	 * particular raw function.  This at least should avoid the cost of fetching
	 * nybblecodes.  It also avoids looking up monomorphic methods at execution
	 * time, and can inline or even fold calls to suitable primitives.  The
	 * inlined calls to infallible primitives are simpler than the calls to
	 * fallible ones or non-primitives or polymorphic methods.  Inlined
	 * primitive attempts avoid having to reify the calling continuation in the
	 * case that they're successful, but have to reify if the primitive fails.
	 */
	FIRST_JVM_TRANSLATION(10_000_000L)
	{
		override fun optimize(code: A_RawFunction, interpreter: Interpreter)
		{
			code.countdownToReoptimize(countdown)
			L1Translator.`🌼translateToLevelTwo`(
				code, SECOND_JVM_TRANSLATION, interpreter)
		}
	},

	/**
	 * The initial translation into Level Two instructions customized to a
	 * particular raw function.  This at least should avoid the cost of fetching
	 * nybblecodes.  It also avoids looking up monomorphic methods at execution
	 * time, and can inline or even fold calls to suitable primitives.  The
	 * inlined calls to infallible primitives are simpler than the calls to
	 * fallible ones or non-primitives or polymorphic methods.  Inlined
	 * primitive attempts avoid having to reify the calling continuation in the
	 * case that they're successful, but have to reify if the primitive fails.
	 */
	SECOND_JVM_TRANSLATION(Long.MAX_VALUE)
	{
		override fun optimize(code: A_RawFunction, interpreter: Interpreter)
		{
			code.countdownToReoptimize(countdown)
			// Note - Reoptimization should stay at this level forever, at least
			// as long as invalidation doesn't send it back to UNOPTIMIZED.
			// Fallback lookups might trigger additional reoptimization, but it
			// stays at this level.
			L1Translator.`🌼translateToLevelTwo`(
				code, SECOND_JVM_TRANSLATION, interpreter)
		}
	},

	/**
	 * Unimplemented.  The idea is that at this level some inlining of
	 * non-primitives will take place, emphasizing inlining of function
	 * application.  Invocations of methods that take a literal function should
	 * tend very strongly to get inlined, as the potential to turn things like
	 * continuation-based conditionals and loops into mere jumps is expected to
	 * be highly profitable.
	 */
	@Suppress("unused")
	CHASED_BLOCKS(Long.MAX_VALUE)
	{
		override fun optimize(code: A_RawFunction, interpreter: Interpreter)
		{
			code.countdownToReoptimize(countdown)
			L1Translator.`🌼translateToLevelTwo`(
				code,
				// Remain at this level, even if fallback lookups make us
				// reoptimize.
				CHASED_BLOCKS,
				interpreter)
		}
	};

	/**
	 * Perform this level of optimization on the given [A_RawFunction].
	 */
	abstract fun optimize(code: A_RawFunction, interpreter: Interpreter)

	companion object
	{
		/**
		 * Answer the `OptimizationLevel` for the given ordinal value.
		 *
		 * @param currentOptimizationLevel
		 *   The ordinal value, an `int`.
		 * @return
		 *   The corresponding `OptimizationLevel`, failing if the ordinal
		 *   was out of range.
		 */
		fun optimizationLevel(currentOptimizationLevel: Int) =
			entries[currentOptimizationLevel]

		/**
		 * If a chunk performs this many slow lookups (that weren't excluded due
		 * to complexity), trigger reoptimization on the next invocation.
		 */
		const val maxSlowLookupsBeforeReoptimization = 1_000L

		/**
		 * After enough slow lookups have been encountered in the current chunk,
		 * set the call countdown to this value.
		 */
		const val countdownResetAfterEnoughFallbackLookups = 100L
	}
}
