/*
 * PatternBuilder.kt
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
package avail.optimizer.values

import avail.descriptor.representation.A_Number.Companion.equalsLong
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.primitive.Primitive
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.PatternBuilder.Companion.pattern
import avail.optimizer.values.PatternBuilder.L2PatternDsl
import avail.utility.cast

/**
 * This is a mechanism for describing an [L2SemanticValue] that's a tree of
 * [L2SemanticPrimitiveInvocation]s.  The pattern is constructed via [pattern],
 * through the [PatternBuilder]'s DSL declaration language, which consists of
 * what look like primitive invocations mixed with [capture] calls.  The indices
 * of the captures indicate where to place the corresponding semantic value
 * extracted from the base semantic value.
 *
 * If the same capture index is used multiple times, the pattern only matches if
 * all occurrences in the tree are equal, which effects a semi-unification
 * matching algorithm.
 *
 * The actual matching is performed by [L2SemanticPattern.matchForEach], which
 * invokes a function with the captured values for each way that the pattern
 * matches the value, in the context of a supplied [L2ValueManifest] (for
 * synonyms).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@L2PatternDsl
internal class PatternBuilder
{
	/**
	 * Indicate that the [PatternBuilder] implicit receiver should use Kotlin
	 * DSL rules for resolving an implicit receiver.
	 */
	@DslMarker
	annotation class L2PatternDsl

	/**
	 * Construct a pattern that matches an invocation of a primitive, if the
	 * primitive and arguments all match.
	 *
	 * @return
	 *   The [Primitive] to match with this pattern.
	 * @param arguments
	 *   The vararg array of patterns to match against the primitive
	 *   invocation's arguments.
	 * @return
	 *   The [L2SemanticPattern] that will do the requested matching.
	 */
	operator fun Primitive.invoke(
		vararg arguments: L2SemanticPattern
	): L2SemanticPattern
	{
		assert(arguments.size == argCount)
		return L2SemanticPrimitivePattern(this, arguments.toList())
	}

	/**
	 * Construct a pattern that matches the supplied semantic value, arranging
	 * to place the matched semantic value into the indicated [index] of the
	 * match array.  If multiple captures with the same [index] occur, they must
	 * be equal to each other for the whole pattern to match.
	 *
	 * @param index
	 *   The zero-based [index] of a capture array into which to place a
	 *   semantic value matching this part of the pattern.
	 * @return
	 *   An [L2SemanticPattern] that will do the requested matching.
	 */
	internal fun capture(index: Int): L2SemanticPattern =
		L2CapturePattern(index)

	/**
	 * Construct a pattern that matches the supplied semantic value, but only if
	 * it's a constant, arranging to place the matched semantic value into the
	 * indicated [index] of the match array.  If multiple captures with the same
	 * [index] occur, they must be equal to each other for the whole pattern to
	 * match.
	 *
	 * @param index
	 *   The zero-based [index] of a capture array into which to place a
	 *   semantic value matching this part of the pattern.
	 * @return
	 *   An [L2SemanticPattern] that will do the requested matching.
	 */
	internal fun captureConstant(index: Int): L2SemanticPattern =
		L2CaptureConstantPattern(index)

	/**
	 * Construct a pattern that matches an [L2SemanticConstant] having a
	 * particular boxed [Long].
	 *
	 * @param longValue
	 *   The [Long] that matches against any [L2SemanticConstant] having that
	 *   long as its constant, in boxed form.
	 * @return
	 *   An [L2SemanticPattern] that will do the requested matching.
	 */
	internal fun constantLong(longValue: Long): L2SemanticPattern =
		L2ConstantLongPattern(longValue)

	abstract class L2SemanticPattern
	{
		/**
		 * Given an [L2SemanticPattern], match it recursively against the
		 * provided [value].  For each way that it's successful, if any, invoke
		 * the [withCaptures] function with an [Array] of matched
		 * [L2SemanticBoxedValue]s organized by each [L2CapturePattern]'s
		 * [index][L2CapturePattern.index].
		 *
		 * @param value
		 *   The top-most [L2SemanticValue] to match.
		 * @param manifest
		 *   The optionar [L2ValueManifest] to use for detecting synonyms during
		 *   the pattern matching.
		 * @param withCaptures
		 *   A function to invoke with the array of captured semantic values for
		 *   each way that the pattern matches.
		 */
		fun matchForEach(
			value: L2SemanticValue<BOXED_KIND>,
			manifest: L2ValueManifest? = null,
			withCaptures: (List<L2SemanticBoxedValue>)->Unit
		): Unit
		{
			this as L2SemanticPatternImpl
			val captures =
				arrayOfNulls<L2SemanticValue<BOXED_KIND>>(maxCapture + 1)
			val equivalents = buildSet {
				add(value)
				manifest?.equivalentSemanticValue(value)?.let { equivalent ->
					addAll(
						manifest.semanticValueToSynonym(equivalent)
							.semanticValues())
				}
			}
			equivalents.forEach { equivalent ->
				privateMatchForEach(
					equivalent,
					manifest,
					captures
				) {
					assert(captures.all { it !== null })
					withCaptures(captures.toList().cast())
				}
				// Backtracking from the semi-unification must clear any
				// introduced bindings.
				assert(captures.all { it === null })
			}
		}
	}

	/**
	 * A representation of a pattern or portion of a pattern that will be
	 * matched against a portion of a tree-shaped [L2SemanticValue].
	 */
	private sealed class L2SemanticPatternImpl: L2SemanticPattern()
	{
		/** The maximum capture index within this subpattern. */
		abstract val maxCapture: Int

		/**
		 * Attempt to match the given [L2SemanticValue] against the pattern,
		 * updating the [captures] array, and invoking the [body] only if this
		 * pattern matches the [L2SemanticValue].
		 */
		abstract fun privateMatchForEach(
			value: L2SemanticValue<BOXED_KIND>,
			manifest: L2ValueManifest?,
			captures: Array<L2SemanticValue<BOXED_KIND>?>,
			body: ()->Unit)
	}

	/**
	 * An [L2SemanticPattern] that matches an [L2SemanticPrimitiveInvocation].
	 *
	 * @constructor
	 *   Create an [L2SemanticPrimitivePattern].
	 * @property primitive
	 *   The [Primitive] to expect.
	 * @property
	 *   The [List] of argument [L2SemanticPattern]s to match the primitive
	 *   arguments.
	 */
	private class L2SemanticPrimitivePattern
	constructor(
		val primitive: Primitive,
		argumentPatterns: List<L2SemanticPattern>
	): L2SemanticPatternImpl()
	{
		val argumentPatterns: List<L2SemanticPatternImpl> =
			argumentPatterns.cast()

		override val maxCapture =
			this.argumentPatterns.maxOfOrNull { it.maxCapture } ?: -1

		override fun privateMatchForEach(
			value: L2SemanticValue<BOXED_KIND>,
			manifest: L2ValueManifest?,
			captures: Array<L2SemanticValue<BOXED_KIND>?>,
			body: ()->Unit)
		{
			if (value !is L2SemanticPrimitiveInvocation) return
			if (value.primitive !== primitive) return
			privateMoreMatches(
				0, value.argumentSemanticValues, manifest, captures, body)
		}

		private fun privateMoreMatches(
			argumentIndex: Int,
			arguments: List<L2SemanticValue<BOXED_KIND>>,
			manifest: L2ValueManifest?,
			captures: Array<L2SemanticValue<BOXED_KIND>?>,
			body: ()->Unit)
		{
			if (argumentIndex >= argumentPatterns.size)
			{
				// We've visited all the arguments, recursively, so we now have
				// a match of this primitive invocation pattern.
				body()
				return
			}
			argumentPatterns[argumentIndex].privateMatchForEach(
				arguments[argumentIndex], manifest, captures
			) {
				privateMoreMatches(
					argumentIndex + 1, arguments, manifest, captures, body)
			}
		}
	}

	/**
	 * An [L2SemanticPattern] that matches any [L2SemanticValue], as long as
	 * each occurrence having the same [index] is matched against equal
	 * semantic values.
	 *
	 * @constructor
	 *   Create an [L2CapturePattern].
	 * @property index
	 *   The zero-based [index] at which to write an [L2SemanticValue] matching
	 *   this part of the pattern.
	 */
	private open class L2CapturePattern
	constructor(
		val index: Int
	): L2SemanticPatternImpl()
	{
		override val maxCapture get() = index

		override fun privateMatchForEach(
			value: L2SemanticValue<BOXED_KIND>,
			manifest: L2ValueManifest?,
			captures: Array<L2SemanticValue<BOXED_KIND>?>,
			body: ()->Unit)
		{
			when (captures[index])
			{
				null ->
				{
					// Bind the capture, run the body, and clear the capture.
					captures[index] = value
					body()
					captures[index] = null
				}
				value ->
				{
					// The capture is already bound, but it's to a compatible
					// value.
					body()
				}
			}
		}
	}

	private class L2CaptureConstantPattern
	constructor(index: Int) : L2CapturePattern(index)
	{
		override fun privateMatchForEach(
			value: L2SemanticValue<BOXED_KIND>,
			manifest: L2ValueManifest?,
			captures: Array<L2SemanticValue<BOXED_KIND>?>,
			body: ()->Unit)
		{
			if (value.isConstant)
			{
				super.privateMatchForEach(
					L2SemanticConstant(value.constant!!),
					manifest,
					captures,
					body)
			}
		}
	}

	private class L2ConstantLongPattern
	constructor(val longValue: Long) : L2SemanticPatternImpl()
	{
		override val maxCapture: Int get() = -1

		override fun privateMatchForEach(
			value: L2SemanticValue<BOXED_KIND>,
			manifest: L2ValueManifest?,
			captures: Array<L2SemanticValue<BOXED_KIND>?>,
			body: ()->Unit)
		{
			if (value.isConstant && value.constant!!.equalsLong(longValue))
			{
				body()
			}
		}
	}

	companion object
	{
		/**
		 * Construct an [L2SemanticPattern] suitable for later matching against
		 * a tree of [L2SemanticPrimitiveInvocation]s and [L2SemanticConstant]s.
		 *
		 * @param body
		 *   The function that produces the [L2SemanticPattern] using the DSL
		 *   provided by [PatternBuilder].
		 */
		fun pattern(body: PatternBuilder.()->L2SemanticPattern) =
			PatternBuilder().body()
	}
}
