/*
 * StylesheetTest.kt
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

package avail.test

import avail.anvil.RenderingContext
import avail.anvil.StylePattern
import avail.anvil.StylePatternCompiler.Companion.compile
import avail.anvil.StyleRule
import avail.anvil.StyleRuleContextState.ACCEPTED
import avail.anvil.StyleRuleContextState.PAUSED
import avail.anvil.StyleRuleContextState.REJECTED
import avail.anvil.StyleRuleContextState.RUNNING
import avail.anvil.StyleRuleExecutor.endOfSequenceLiteral
import avail.anvil.StyleRuleExecutor.run
import avail.anvil.Stylesheet
import avail.anvil.UnvalidatedRenderingContext
import avail.anvil.UnvalidatedStylePattern
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.WorkStealingQueue
import avail.utility.javaNotifyAll
import avail.utility.javaWait
import org.availlang.artifact.environment.project.Palette
import org.availlang.artifact.environment.project.StyleAttributes
import org.availlang.json.JSONObject
import org.availlang.json.JSONReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import kotlin.math.pow
import org.junit.jupiter.params.provider.Arguments.of as argumentsOf
import java.util.stream.Stream.of as streamOf

/**
 * Tests for [stylesheets][Stylesheet], especially
 * [style&#32;pattern][StylePattern] compilation and execution. As of
 * 2022.12.03, the [exhaustive&#32;test][testExhaustive] was successfully run
 * with [vocabularySize]=`4` and `[maxSequenceSize]=`8`, in ~19s on a MacBook
 * Pro 2018). The values in the code strike the right compromise for efficiency
 * and thoroughness for ongoing testing, and the
 * [regression&#32;test][testRegressions] provides safety for once volatile
 * areas of the theory and/or implementation.
 *
 * @author Todd Smith &lt;todd@availlang.org&gt;
 */
class StylesheetTest
{
	/**
	 * Test the [positive&#32;exemplars][positiveExemplars]. This is an
	 * end-to-end test of parsing, code generation, rendering context
	 * validation, and disassembly.
	 *
	 * @param source
	 *   The source pattern.
	 * @param expected
	 *   The expected stringification of the compiled rule.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("getPositiveExemplars")
	fun testPositiveExemplars(source: String, expected: String)
	{
		val pattern = UnvalidatedStylePattern(source, renderingContext)
		val rule = compile(pattern, palette)
		assertEquals(expected, rule.toString())
	}

	/**
	 * Test the [execution&#32;exemplars][executionExemplars] using
	 * [naive][naiveMatch] matching.
	 *
	 * @param source
	 *   The source pattern.
	 * @param sequence
	 *   The search sequence.
	 * @param expected
	 *   The expected result of the match.
	 */
	@ParameterizedTest(name = "{0} : {1}")
	@MethodSource("getExecutionExemplars")
	fun testNaiveMatch(
		source: String,
		sequence: List<String>,
		expected: Boolean)
	{
		assertEquals(expected, naiveMatch(source, sequence))
	}

	/**
	 * Test the [execution&#32;exemplars][executionExemplars] using
	 * [rule][ruleMatch] matching.
	 *
	 * @param source
	 *   The source pattern.
	 * @param sequence
	 *   The search sequence.
	 * @param expected
	 *   The expected result of the match.
	 */
	@ParameterizedTest(name = "{0} : {1}")
	@MethodSource("getExecutionExemplars")
	fun testRuleMatch(source: String, sequence: List<String>, expected: Boolean)
	{
		val pattern = UnvalidatedStylePattern(source, renderingContext)
		val rule = compile(pattern, palette)
		val interned = sequence.map { it.intern() }
		assertEquals(expected, ruleMatch(rule, interned))
	}

	/**
	 * Test the [regression&#32;exemplars][regressionExemplars] using
	 * [rule][ruleMatch] matching.
	 *
	 * @param source
	 *   The source pattern.
	 * @param sequence
	 *   The search sequence.
	 * @param expected
	 *   The expected result of the match.
	 */
	@ParameterizedTest(name = "{0} : {1}")
	@MethodSource("getRegressionExemplars")
	fun testRegressions(
		source: String,
		sequence: List<String>,
		expected: Boolean)
	{
		val pattern = UnvalidatedStylePattern(source, renderingContext)
		val rule = compile(pattern, palette)
		val interned = sequence.map { it.intern() }
		assertEquals(expected, ruleMatch(rule, interned))
	}

	/**
	 * Test the suite itself, by ensuring that [patternGenerator] produces the
	 * correct patterns.
	 */
	@Test
	fun testPatternComprehensiveness()
	{
		val symbols = ('a' until 'a' + vocabularySize).map { "#$it".intern() }
		val patterns = patternGenerator(symbols).toList()
		assertEquals(patternCount(symbols.size), patterns.size)
		assertEquals(patterns.size, patterns.toSet().size)
	}

	/**
	 * Test the suite itself, by ensuring that [sequenceGenerator] produces the
	 * correct sequences.
	 */
	@Test
	fun testSequenceComprehensiveness()
	{
		val symbols = ('a' until 'a' + vocabularySize).map { "#$it".intern() }
		val sequences = sequenceGenerator(symbols, maxSequenceSize).toList()
		assertEquals(
			sequenceCount(symbols.size, maxSequenceSize),
			sequences.size)
		assertEquals(sequences.size, sequences.toSet().size)
	}

	/**
	 * Test that every pattern and sequence of classifiers, such that the
	 * classifiers are chosen from a limited vocabulary, correctly match. Use
	 * all available cores to handle the large Cartesian product of patterns and
	 * sequences. Errors discovered from running this test should be added to
	 * [regressionExemplars] (and deduplicated if necessary), to support
	 * [regression&#32;testing][testRegressions].
	 */
	@Test
	fun testExhaustive()
	{
		class Task(private val runnable: Runnable): Runnable, Comparable<Task>
		{
			override fun run() = runnable.run()

			// This is just to support WorkStealingQueue, but we're not actually
			// leveraging priority in any way.
			override fun compareTo(other: Task) = 0
		}
		data class Failure(
			val source: String,
			val sequence: List<String>,
			val expected: Boolean)
		{
			override fun toString() = buildString {
				// Emit the error as Kotlin source code that can be pasted
				// directly into regressionExemplars.
				append("argumentsOf(")
				append('"')
				append(source)
				append('"')
				append(", listOf(")
				sequence.forEach { classifier ->
					append('"')
					append(classifier)
					append('"')
					append(',')
				}
				setLength(length - 1)
				append("), ")
				append(expected)
				append("),")
			}
		}
		val availableProcessors = Runtime.getRuntime().availableProcessors()
		@Suppress("UNCHECKED_CAST")
		val threadPool = ThreadPoolExecutor(
			availableProcessors,
			availableProcessors,
			1L,
			TimeUnit.SECONDS,
			WorkStealingQueue<Task>(availableProcessors)
				as BlockingQueue<Runnable>,
			{ Thread(it) },
			ThreadPoolExecutor.AbortPolicy())
		val symbols = ('a' until 'a' + vocabularySize).map { "#$it".intern() }
		// The operations and data in support of counting are deliberately kept
		// to 32 bits, just to ensure that the number of test vectors does not
		// grow too large for practical execution.
		val patternCount = patternCount(vocabularySize)
		assertTrue(patternCount >= 0) {
			"vocabularySize is too large"
		}
		val sequenceCount = sequenceCount(vocabularySize, maxSequenceSize)
		assertTrue(sequenceCount >= 0) {
			"vocabularySize and/or maxSequenceSize is/are too large"
		}
		val total = patternCount * sequenceCount
		assertEquals(
			total.toBigInteger(),
			patternCount.toBigInteger() * sequenceCount.toBigInteger()
		) {
			"too many test vectors: reduce either/both " +
				"vocabularySize and maxSequenceSize"
		}
		val completed = AtomicInteger(0)
		val errors = ConcurrentLinkedQueue<Failure>()
		patternGenerator(symbols).forEach { source ->
			// Iterate through every pattern. Each should be valid, so we expect
			// compilation to succeed reliably.
			val pattern = UnvalidatedStylePattern(source, renderingContext)
			val rule = compile(pattern, palette)
			sequenceGenerator(symbols, maxSequenceSize).forEach { sequence ->
				// Iterate through every sequence, scheduling a work unit for
				// each sequence.
				threadPool.execute(
					Task {
						try
						{
							val expected = naiveMatch(source, sequence)
							val actual = ruleMatch(rule, sequence)
							if (actual != expected)
							{
								errors.add(Failure(source, sequence, expected))
							}
						}
						finally
						{
							if (completed.incrementAndGet() == total)
							{
								synchronized(completed) {
									completed.javaNotifyAll()
								}
							}
						}
					})
			}
		}
		synchronized(completed) {
			// Wait until every work unit completes.
			while (completed.get() < total)
			{
				// Deal with spurious wake-ups.
				completed.javaWait()
			}
		}
		assertTrue(errors.isEmpty()) {
			buildString {
				appendLine("FAILED: ${errors.size} failures")
				errors.forEach { appendLine(it) }
			}
		}
	}

	companion object
	{
		/**
		 * The [rendering&#32;context][RenderingContext] for testing the
		 * exemplars.
		 */
		private val renderingContext = UnvalidatedRenderingContext(StyleAttributes())

		/**
		 * The [palette][Palette] for testing the exemplars. Excerpted from the
		 * Avail configuration file (`avail-config.json`) as it existed at the
		 * time of writing (2022.11.26)
		 */
		@Suppress("SpellCheckingInspection")
		val palette = Palette.from(
			JSONReader(
				"""
					{
						"extreme": "#FFFFFFFF/000000FF",
						"textBackground": "#FFFFFF4A/21121940",
						"codeBackground": "#F7F7F7FF/02232EFF",
						"consoleBackground": "#E6E6E6FF/053646FF",
						"baseCode": "#000000EE/FFFFFFEE",
						"consoleText": "#000000FF/FFFFFFFF",
						"strongGray": "#00000077/FFFFFF77",
						"weakGray": "#00000044/FFFFFF44",
						"lilac": "#8174FFFF/6659E2FF",
						"magenta": "#D03B83FF/D25B94FF",
						"transparentMagenta": "#D03B83AA/D25B94AA",
						"rose": "#DC4444FF",
						"transparentRose": "#DC444488",
						"faintTransparentRose": "#DC444422",
						"mango": "#EE731BFF/FF8836FF",
						"mustard": "#E8B000FF/FFD659FF",
						"transparentMustard": "#E8B00018/FFD65918",
						"green": "#4C853EFF/78B669FF",
						"transparentGreen": "#4C853E88/78B66988",
						"blue": "#2294C6FF/38B1E5FF",
						"faintTransparentIndigo": "#4B35A920/3B279530",
						"deemphasize": "#80808080/60606080"
					}
				""".trimIndent().reader()
			).read() as JSONObject
		)

		/**
		 * The positive exemplars, as pairs of source pattern and expected
		 * disassemblies.
		 */
		@JvmStatic
		val positiveExemplars: Stream<Arguments> = streamOf(
			argumentsOf(
				"=",
				"""
					=
					nybblecodes:
						[no instructions]
					instructions:
						[no instructions]
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"=#foo",
				"""
					=#foo
					nybblecodes:
						0C
					instructions:
						@0: match literal #0 <#foo>
						@1: match end of sequence
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"=#foo,#baz",
				"""
					=#foo,#baz
					nybblecodes:
						01C
					instructions:
						@0: match literal #0 <#foo>
						@1: match literal #1 <#baz>
						@2: match end of sequence
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"=#foo,#baz,#foo",
				"""
					=#foo,#baz,#foo
					nybblecodes:
						010C
					instructions:
						@0: match literal #0 <#foo>
						@1: match literal #1 <#baz>
						@2: match literal #0 <#foo>
						@3: match end of sequence
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"=#foo,#foo",
				"""
					=#foo,#foo
					nybblecodes:
						00C
					instructions:
						@0: match literal #0 <#foo>
						@1: match literal #0 <#foo>
						@2: match end of sequence
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"=#foo,#foo,#foo",
				"""
					=#foo,#foo,#foo
					nybblecodes:
						000C
					instructions:
						@0: match literal #0 <#foo>
						@1: match literal #0 <#foo>
						@2: match literal #0 <#foo>
						@3: match end of sequence
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"=#a,#b,#c,#d,#e,#f,#g,#h",
				"""
					=#a,#b,#c,#d,#e,#f,#g,#h
					nybblecodes:
						012340414243C
					instructions:
						@0: match literal #0 <#a>
						@1: match literal #1 <#b>
						@2: match literal #2 <#c>
						@3: match literal #3 <#d>
						@4: match literal #4 <#e>
						@6: match literal #5 <#f>
						@8: match literal #6 <#g>
						@10: match literal #7 <#h>
						@12: match end of sequence
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#foo",
				"""
					#foo
					nybblecodes:
						50
					instructions:
						@0: match literal #0 <#foo> or jump to @0
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#foo,#bar",
				"""
					#foo,#bar
					nybblecodes:
						5060
					instructions:
						@0: match literal #0 <#foo> or jump to @0
						@2: match literal #1 <#bar> or jump to @0
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#foo,#bar,#foo",
				"""
					#foo,#bar,#foo
					nybblecodes:
						5060A50
					instructions:
						@0: match literal #0 <#foo> or jump to @0
						@2: match literal #1 <#bar> or jump to @0
						@4: fork to @0
						@5: match literal #0 <#foo> or jump to @0
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#a,#b,#c,#d,#e,#f,#g,#h",
				"""
					#a,#b,#c,#d,#e,#f,#g,#h
					nybblecodes:
						50607080900910920930
					instructions:
						@0: match literal #0 <#a> or jump to @0
						@2: match literal #1 <#b> or jump to @0
						@4: match literal #2 <#c> or jump to @0
						@6: match literal #3 <#d> or jump to @0
						@8: match literal #4 <#e> or jump to @0
						@11: match literal #5 <#f> or jump to @0
						@14: match literal #6 <#g> or jump to @0
						@17: match literal #7 <#h> or jump to @0
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#foo,#foo",
				"""
					#foo,#foo
					nybblecodes:
						50A50
					instructions:
						@0: match literal #0 <#foo> or jump to @0
						@2: fork to @0
						@3: match literal #0 <#foo> or jump to @0
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#foo,#foo,#foo",
				"""
					#foo,#foo,#foo
					nybblecodes:
						50A50A50
					instructions:
						@0: match literal #0 <#foo> or jump to @0
						@2: fork to @0
						@3: match literal #0 <#foo> or jump to @0
						@5: fork to @0
						@6: match literal #0 <#foo> or jump to @0
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#a,#a,#a,#b",
				"""
					#a,#a,#a,#b
					nybblecodes:
						50A50A5060
					instructions:
						@0: match literal #0 <#a> or jump to @0
						@2: fork to @0
						@3: match literal #0 <#a> or jump to @0
						@5: fork to @0
						@6: match literal #0 <#a> or jump to @0
						@8: match literal #1 <#b> or jump to @0
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#a,#b,#a,#c",
				"""
					#a,#b,#a,#c
					nybblecodes:
						5060A5070
					instructions:
						@0: match literal #0 <#a> or jump to @0
						@2: match literal #1 <#b> or jump to @0
						@4: fork to @0
						@5: match literal #0 <#a> or jump to @0
						@7: match literal #2 <#c> or jump to @0
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#a,#b,#a,#c,#a,#b,#a,#c,#d",
				"""
					#a,#b,#a,#c,#a,#b,#a,#c,#d
					nybblecodes:
						5060A5070A5060A507080
					instructions:
						@0: match literal #0 <#a> or jump to @0
						@2: match literal #1 <#b> or jump to @0
						@4: fork to @0
						@5: match literal #0 <#a> or jump to @0
						@7: match literal #2 <#c> or jump to @0
						@9: fork to @0
						@10: match literal #0 <#a> or jump to @0
						@12: match literal #1 <#b> or jump to @0
						@14: fork to @0
						@15: match literal #0 <#a> or jump to @0
						@17: match literal #2 <#c> or jump to @0
						@19: match literal #3 <#d> or jump to @0
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#foo<#bar",
				"""
					#foo<#bar
					nybblecodes:
						5062
					instructions:
						@0: match literal #0 <#foo> or jump to @0
						@2: match literal #1 <#bar> or jump to @2
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#foo<#bar<#baz",
				"""
					#foo<#bar<#baz
					nybblecodes:
						506274
					instructions:
						@0: match literal #0 <#foo> or jump to @0
						@2: match literal #1 <#bar> or jump to @2
						@4: match literal #2 <#baz> or jump to @4
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#a<#b<#c<#d<#e<#f<#g<#h",
				"""
					#a<#b<#c<#d<#e<#f<#g<#h
					nybblecodes:
						50627486908191C1928293C2
					instructions:
						@0: match literal #0 <#a> or jump to @0
						@2: match literal #1 <#b> or jump to @2
						@4: match literal #2 <#c> or jump to @4
						@6: match literal #3 <#d> or jump to @6
						@8: match literal #4 <#e> or jump to @8
						@12: match literal #5 <#f> or jump to @12
						@16: match literal #6 <#g> or jump to @16
						@20: match literal #7 <#h> or jump to @20
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#a,#b < #c,#d",
				"""
					#a,#b < #c,#d
					nybblecodes:
						50607484
					instructions:
						@0: match literal #0 <#a> or jump to @0
						@2: match literal #1 <#b> or jump to @0
						@4: match literal #2 <#c> or jump to @4
						@6: match literal #3 <#d> or jump to @4
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent()),
			argumentsOf(
				"#a,#b,#a,#b,#c < #c,#d,#c,#d,#e < #f",
				"""
					#a,#b,#a,#b,#c < #c,#d,#c,#d,#e < #f
					nybblecodes:
						5060A5060707B18B1BA17B18B190B191E3
					instructions:
						@0: match literal #0 <#a> or jump to @0
						@2: match literal #1 <#b> or jump to @0
						@4: fork to @0
						@5: match literal #0 <#a> or jump to @0
						@7: match literal #1 <#b> or jump to @0
						@9: match literal #2 <#c> or jump to @0
						@11: match literal #2 <#c> or jump to @11
						@14: match literal #3 <#d> or jump to @11
						@17: fork to @11
						@20: match literal #2 <#c> or jump to @11
						@23: match literal #3 <#d> or jump to @11
						@26: match literal #4 <#e> or jump to @11
						@30: match literal #5 <#f> or jump to @30
					renderingContext:
						fontFamily = Monospaced
						foreground = baseCode
						background = codeBackground
						bold = false
						italic = false
						underline = false
						superscript = false
						subscript = false
						strikethrough = false
				""".trimIndent())
		)

		/**
		 * The exemplars to support [testNaiveMatch] and [testRuleMatch]. This
		 * supports meta-testing, for building confidence in the implementation
		 * of the test suite itself, in furtherance of the
		 * [exhausted][testExhaustive] test.
		 */
		@JvmStatic
		val executionExemplars get(): Stream<Arguments> = streamOf(
			argumentsOf("=", emptyList<String>(), true),
			argumentsOf("=", listOf("#a"), false),
			argumentsOf("=#a", listOf("#a"), true),
			argumentsOf("=#a", listOf("#b"), false),
			argumentsOf("=#a,#b", listOf("#a"), false),
			argumentsOf("=#a,#b", listOf("#a", "#b"), true),
			argumentsOf("=#a,#b", listOf("#a", "#b", "#c"), false),
			argumentsOf("=#a,#b", listOf("#a", "#a", "#b"), false),
			argumentsOf("=#a,#b,#c", listOf("#a", "#b", "#c"), true),
			argumentsOf("#a", listOf("#a"), true),
			argumentsOf("#a", listOf("#b"), false),
			argumentsOf("#a,#b", listOf("#a"), false),
			argumentsOf("#a,#b", listOf("#a", "#b"), true),
			argumentsOf("#a,#b", listOf("#a", "#b", "#c"), true),
			argumentsOf("#a,#b", listOf("#a", "#a", "#b"), true),
			argumentsOf("#a,#b,#c", listOf("#a", "#b", "#c"), true),
			argumentsOf("#a<#b", listOf("#a"), false),
			argumentsOf("#a<#b", listOf("#a", "#a"), false),
			argumentsOf("#a<#b", listOf("#a", "#b"), true),
			argumentsOf("#a<#b", listOf("#a", "#b", "#c"), true),
			argumentsOf("#a<#b", listOf("#a", "#a", "#b"), true),
			argumentsOf("#a<#b", listOf("#a", "#c", "#b"), true),
			argumentsOf("#a<#b<#c", listOf("#a", "#b", "#c"), true),
			argumentsOf("#a<#b<#c", listOf("#a", "#c", "#b"), false),
			argumentsOf("#a<#b<#c", listOf("#a", "#c", "#b", "#c"), true),
			argumentsOf("#a,#b<#a,#b", listOf("#a", "#b", "#a", "#b"), true),
			argumentsOf("#a<#b,#a<#b", listOf("#a", "#b", "#a", "#b"), true),
			argumentsOf(
				"#a<#b,#a<#b",
				listOf("#a", "#b", "#c", "#a", "#b"),
				false),
			argumentsOf(
				"#a<#b,#a<#b",
				listOf("#a", "#b", "#a", "#a", "#b"),
				true),
			argumentsOf(
				"#a<#b,#a<#b",
				listOf("#a", "#b", "#a", "#b", "#a"),
				true),
			argumentsOf(
				"#a<#b,#a<#b",
				listOf("#a", "#b", "#c", "#a", "#b"),
				false),
			argumentsOf(
				"#a<#b,#a<#b",
				listOf("#a", "#b", "#a", "#a", "#b"),
				true),
			argumentsOf(
				"#a<#b,#a<#b",
				listOf("#a", "#b", "#a", "#b", "#a"),
				true),
			argumentsOf("#a,#a,#b", listOf("#a", "#a", "#b"), true),
			argumentsOf("#a,#a,#b", listOf("#a", "#a", "#a", "#b"), true),
			argumentsOf("#a,#a,#b", listOf("#a", "#a", "#a", "#a", "#b"), true),
			argumentsOf(
				"#a,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#a", "#b"),
				true),
			argumentsOf("#a,#a,#b", listOf("#a", "#a", "#a", "#c"), false),
			argumentsOf("#a,#b,#a,#b", listOf("#a", "#b", "#a", "#b"), true),
			argumentsOf(
				"#a,#b,#a,#b",
				listOf("#a", "#a", "#b", "#a", "#b"),
				true),
			argumentsOf(
				"#a,#b,#a,#b",
				listOf("#a", "#a", "#a", "#b", "#a", "#b"),
				true),
			argumentsOf(
				"#a,#b,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#b", "#a", "#b"),
				true),
			argumentsOf(
				"#a,#b,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#b", "#a", "#c"),
				false)
		)

		/**
		 * The exemplars to support [testRegressions], mostly discovered and
		 * emitted by [testExhaustive].
		 */
		@JvmStatic
		val regressionExemplars: Stream<Arguments> = streamOf(
			argumentsOf(
				"=#a,#a,#a",
				listOf("#a", "#a", "#a", "#a", "#a", "#a"),
				false),
			argumentsOf("=#a,#a,#b", listOf("#a", "#a", "#a", "#b"), false),
			argumentsOf(
				"=#a,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#a", "#b"),
				false),
			argumentsOf(
				"=#a,#a,#a,#a",
				listOf("#a", "#a", "#a", "#a", "#a"),
				false),
			argumentsOf(
				"=#a,#a,#b,#b",
				listOf("#a", "#a", "#a", "#a", "#b", "#b"),
				false),
			argumentsOf(
				"=#a,#a,#b,#c",
				listOf("#a", "#a", "#a", "#a", "#b", "#c"),
				false),
			argumentsOf(
				"=#a,#b,#a,#a",
				listOf("#a", "#b", "#a", "#b", "#a", "#a"),
				false),
			argumentsOf(
				"=#a,#b,#a,#b",
				listOf("#a", "#b", "#a", "#b", "#a", "#b"),
				false),
			argumentsOf(
				"=#a,#a,#b,#c",
				listOf("#a", "#a", "#a", "#b", "#c"),
				false),
			argumentsOf(
				"=#a,#a,#b,#b",
				listOf("#a", "#a", "#a", "#b", "#b"),
				false),
			argumentsOf(
				"=#a,#a,#b,#a",
				listOf("#a", "#a", "#a", "#a", "#b", "#a"),
				false),
			argumentsOf(
				"=#a,#a,#b,#a",
				listOf("#a", "#a", "#a", "#b", "#a"),
				false),
			argumentsOf(
				"=#a,#a,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#a", "#b"),
				false),
			argumentsOf(
				"=#a,#a,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#b"),
				false),
			argumentsOf(
				"=#a,#a,#a,#a",
				listOf("#a", "#a", "#a", "#a", "#a", "#a"),
				false),
			argumentsOf(
				"=#a,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#b"),
				false),
			argumentsOf(
				"=#a,#a,#a",
				listOf("#a", "#a", "#a", "#a", "#a"),
				false),
			argumentsOf(
				"=#a,#a",
				listOf("#a", "#a", "#a", "#a", "#a", "#a"),
				false),
			argumentsOf("=#a,#a", listOf("#a", "#a", "#a"), false),
			argumentsOf("=#a,#a", listOf("#a", "#a", "#a", "#a", "#a"), false),
			argumentsOf("=#a,#a", listOf("#a", "#a", "#a", "#a"), false),
			argumentsOf("=#a,#a,#a", listOf("#a", "#a", "#a", "#a"), false),
			argumentsOf(
				"=#a,#b,#a",
				listOf("#a", "#b", "#a", "#b", "#a"),
				false
			),
			argumentsOf(
				"=#a,#b,#a,#c",
				listOf("#a", "#b", "#a", "#b", "#a", "#c"),
				false
			),
			argumentsOf(
				"#a,#b,#a,#c",
				listOf("#a", "#b", "#a", "#b", "#a", "#c"),
				true),
			argumentsOf(
				"#a,#b,#a,#a",
				listOf("#a", "#b", "#a", "#b", "#a", "#a"),
				true),
			argumentsOf(
				"#a<#a,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#b", "#d"),
				true),
			argumentsOf(
				"#a<#a,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#b", "#c"),
				true),
			argumentsOf(
				"#a<#a,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#b"),
				true),
			argumentsOf(
				"#a<#b,#b,#a",
				listOf("#a", "#a", "#b", "#b", "#b", "#a"),
				true),
			argumentsOf(
				"#a<#b,#b,#a",
				listOf("#a", "#c", "#b", "#b", "#b", "#a"),
				true),
			argumentsOf(
				"#a<#b,#b,#c",
				listOf("#a", "#b", "#b", "#b", "#c"),
				true),
			argumentsOf(
				"#a<#b,#b,#c",
				listOf("#a", "#a", "#b", "#b", "#b", "#c"),
				true),
			argumentsOf(
				"#a<#b,#b,#c",
				listOf("#b", "#a", "#b", "#b", "#b", "#c"),
				true),
			argumentsOf(
				"#a<#b,#b,#c",
				listOf("#d", "#a", "#b", "#b", "#b", "#c"),
				true),
			argumentsOf(
				"#a<#b,#b,#c",
				listOf("#c", "#a", "#b", "#b", "#b", "#c"),
				true),
			argumentsOf(
				"#a<#b,#b,#c",
				listOf("#a", "#b", "#b", "#b", "#c", "#a"),
				true),
			argumentsOf(
				"#a<#b,#b,#a",
				listOf("#a", "#b", "#b", "#b", "#a", "#c"),
				true),
			argumentsOf(
				"#a<#b,#b,#a",
				listOf("#a", "#b", "#b", "#b", "#a", "#b"),
				true),
			argumentsOf(
				"#a<#b,#b,#a",
				listOf("#c", "#a", "#b", "#b", "#b", "#a"),
				true),
			argumentsOf(
				"#a<#b,#b,#a",
				listOf("#d", "#a", "#b", "#b", "#b", "#a"),
				true),
			argumentsOf(
				"#a<#b,#b,#a",
				listOf("#a", "#d", "#b", "#b", "#b", "#a"),
				true),
			argumentsOf(
				"#a<#b,#b,#a",
				listOf("#a", "#b", "#b", "#b", "#a", "#a"),
				true),
			argumentsOf(
				"#a<#b,#b,#a",
				listOf("#a", "#b", "#b", "#b", "#a"),
				true),
			argumentsOf(
				"#a<#b,#b,#a",
				listOf("#b", "#a", "#b", "#b", "#b", "#a"),
				true),
			argumentsOf(
				"#a<#b,#b,#a",
				listOf("#a", "#b", "#b", "#b", "#a", "#d"),
				true),
			argumentsOf(
				"#a<#a,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#b", "#a"),
				true),
			argumentsOf(
				"#a<#a,#a,#b",
				listOf("#a", "#b", "#a", "#a", "#a", "#b"),
				true),
			argumentsOf(
				"#a<#a,#a,#b",
				listOf("#b", "#a", "#a", "#a", "#a", "#b"),
				true),
			argumentsOf(
				"#a<#a,#a,#b",
				listOf("#c", "#a", "#a", "#a", "#a", "#b"),
				true),
			argumentsOf(
				"#a<#a,#a,#b",
				listOf("#a", "#d", "#a", "#a", "#a", "#b"),
				true),
			argumentsOf(
				"#a<#a,#a,#b",
				listOf("#d", "#a", "#a", "#a", "#a", "#b"),
				true),
			argumentsOf(
				"#a<#a,#a,#b",
				listOf("#a", "#c", "#a", "#a", "#a", "#b"),
				true),
			argumentsOf(
				"#a<#a,#a,#b",
				listOf("#a", "#a", "#a", "#a", "#b", "#b"),
				true),
			argumentsOf(
				"#a<#b,#b,#c",
				listOf("#a", "#b", "#b", "#b", "#c", "#b"),
				true),
			argumentsOf(
				"#a<#b,#b,#c",
				listOf("#a", "#d", "#b", "#b", "#b", "#c"),
				true),
			argumentsOf(
				"#a<#b,#b,#c",
				listOf("#a", "#c", "#b", "#b", "#b", "#c"),
				true),
			argumentsOf(
				"#a<#b,#b,#c",
				listOf("#a", "#b", "#b", "#b", "#c", "#c"),
				true),
			argumentsOf(
				"#a<#b,#b,#c",
				listOf("#a", "#b", "#b", "#b", "#c", "#d"),
				true)
		)

		/**
		 * Drain the elements of the receiver into a new [MutableSet], leaving the
		 * receiver empty.
		 *
		 * @return
		 *   A copy of the receiver.
		 */
		private fun <T> MutableSet<T>.drain(): MutableSet<T>
		{
			val elements = toMutableSet()
			clear()
			return elements
		}

		/**
		 * Match the specified [sequence] against the given pattern, using naive
		 * techniques. This is used for determining the expected value of a
		 * [rule&#32;match][ruleMatch].
		 *
		 * @param pattern
		 *   The pattern source. Must be free of whitespace.
		 * @param sequence
		 *   The sequence.
		 * @return
		 *   Whether the pattern accepts the sequence, according to the naive
		 *   mechanism.
		 */
		private fun naiveMatch(pattern: String, sequence: List<String>): Boolean
		{
			if (pattern.startsWith('='))
			{
				val succession = pattern
					.substring(1)
					.split(',')
					.filter { it.isNotEmpty() }
				return succession == sequence
			}
			var index = 0
			val subsequences = pattern.split('<')
			val successions = subsequences.map { it.split(',') }
			successions.forEach { succession ->
				index = sequence.indexOfSequence(index, succession)
				if (index < 0) return false
				index += succession.size
			}
			return true
		}

		/**
		 * Determine the index of the first occurrence of [s] within the
		 * receiver, starting the search at [startIndex].
		 *
		 * @param startIndex
		 *   The index into the receiver at which to begin the search for [s].
		 * @param s
		 *   The search sequence.
		 * @return
		 *   The index of the first occurrence of [s], or `-1` if the receiver
		 *   does not contain [s].
		 */
		private fun <T> List<T>.indexOfSequence(startIndex: Int, s: List<T>): Int
		{
			val sliceSize = s.size
			val lastIndex = size - sliceSize
			for (index in startIndex .. lastIndex)
			{
				val slice = slice(index until index + sliceSize)
				if (slice == s) return index
			}
			return -1
		}

		/**
		 * Match the specified [sequence] against the given [rule].
		 *
		 * @param rule
		 *   The rule.
		 * @param sequence
		 *   The sequence, sans the [end-of-sequence][endOfSequenceLiteral]
		 *   classifier.
		 * @return
		 *   Whether the rule accepts the sequence.
		 */
		private fun ruleMatch(rule: StyleRule, sequence: List<String>): Boolean
		{
			// Handle the empty rule and sequence specially.
			if (rule.instructions.isEmpty()) return sequence.isEmpty()
			val normalizedSequence = sequence.append(endOfSequenceLiteral)
			// Run the rule, processing the original context and any derivatives
			// until the rule has passed or failed.
			val classifiers = normalizedSequence.iterator()
			val paused = mutableSetOf(rule.initialContext)
			while (classifiers.hasNext() && paused.isNotEmpty())
			{
				val running = paused.drain().mapTo(mutableSetOf()) {
					it.copy(state = RUNNING)
				}
				val classifier = classifiers.next()
				while (running.isNotEmpty())
				{
					running.drain().forEach { context ->
						val nextContext =
							run(context, classifier) { forked ->
								assertEquals(RUNNING, forked.state)
								running.add(forked)
							}
						when (nextContext.state)
						{
							PAUSED -> paused.add(nextContext)
							RUNNING -> running.add(nextContext)
							ACCEPTED -> return true
							REJECTED -> {}
						}
					}
				}
			}
			return false
		}

		/** The size of the vocabulary for testing. */
		const val vocabularySize = 4

		/** The maximum sequence size for testing. */
		const val maxSequenceSize = 6

		/**
		 * Answer a generator of patterns for the
		 * [exhaustive&#32;test][testExhaustive]. For efficiency, generated
		 * patterns have unique structures, so, e.g., only one of `#a,a,#b` and
		 * `#b,#b,#c` is included in the comprehension, because the patterns are
		 * isomorphic under renaming. Note that this is equivalent to generating
		 * the partitions of the symbol set.
		 *
		 * For demonstration purposes, these are the 24 possible partitions of
		 * the vocabulary `A, B, C, D`, sans operators, organized by partition
		 * size:
		 *
		 * * 0: () [1]
		 * * 1: (A) [1]
		 * * 2: (A A) (A B) [2]
		 * * 3: (A A A) (A A B) (A B A) (A B B) (A B C) [5]
		 * * 4: (A A A A) (A A A B) (A A B A) (A A B B) (A A B C) (A B A A)
		 *      (A B A B) (A B A C) (A B B A) (A B B B) (A B B C) (A B C A)
		 *      (A B C B) (A B C C) (A B C D) [15]
		 *
		 * Note that the partition counts (in square brackets) exactly align
		 * with Bell numbers `B<sub>0</sub>` through `B<sub>4</sub>`.
		 *
		 * @param symbols
		 *   The vocabulary of symbols for pattern generation.
		 * @return
		 *   The generator.
		 * @see <a href="https://en.wikipedia.org/wiki/Partition_of_a_set">Partition of a set</a>
		 * @see <a href="https://en.wikipedia.org/wiki/Bell_number#Rhyme_schemes">Bell number</a>
		 */
		private fun patternGenerator(symbols: List<String>): Sequence<String>
		{
			// This is what an exhaustive generator looks like in a language
			// that doesn't support continuations or backtracking … Anyway, the
			// mutable variables serve as coordinates in the state space of the
			// generator.
			val exactMatch = listOf("", "=")
			var operators = listOf(",", "<")
			var symbolIndices = mutableListOf<Int>()
			var operatorIndices = mutableListOf<Int>()
			var exactMatchIndex = 0
			val makePattern = {
				buildString {
					append(exactMatch[exactMatchIndex])
					append(symbols[symbolIndices[0]])
					operatorIndices.zip(symbolIndices.drop(1))
						.forEach { (operatorIndex, symbolIndex) ->
							append(operators[operatorIndex])
							append(symbols[symbolIndex])
						}
				}
			}
			return generateSequence {
				if (symbolIndices.isEmpty())
				{
					// This simplifies generation of the initial rules for
					// anchored and unanchored rule sets.
					symbolIndices.add(0)
					return@generateSequence makePattern()
				}
				for (symbolIndex in symbolIndices.indices.drop(1).reversed())
				{
					// Iterate backwards through the symbol indices, replacing
					// symbols as we advance through the state space. To ensure
					// that structures are never repeated, never introduce a
					// symbol index further than 1 ahead of those seen left of
					// the introduction.
					val predecessors = symbolIndices.slice(0 until symbolIndex)
					val nextAllowedSymbolIndex = predecessors.maxOrNull()!! + 1
					if (symbolIndices[symbolIndex] < nextAllowedSymbolIndex)
					{
						symbolIndices[symbolIndex] =
							symbolIndices[symbolIndex] + 1
						return@generateSequence makePattern()
					}
					else
					{
						symbolIndices[symbolIndex] = 0
					}
				}
				for (operatorIndex in operatorIndices.indices)
				{
					// Iterate through every possible combination of operators.
					if (operatorIndices[operatorIndex] < operators.lastIndex)
					{
						operatorIndices[operatorIndex] =
							operatorIndices[operatorIndex] + 1
						return@generateSequence makePattern()
					}
					else
					{
						operatorIndices[operatorIndex] = 0
					}
				}
				if (symbolIndices.size < symbols.size)
				{
					// We have exhausted the possibilities for partitions of the
					// current size, so increase the partition size.
					symbolIndices = (0 .. symbolIndices.size).mapTo(
						mutableListOf()
					) { 0 }
					operatorIndices = (0 .. operatorIndices.size).mapTo(
						mutableListOf()
					) { 0 }
					return@generateSequence makePattern()
				}
				if (exactMatchIndex < exactMatch.lastIndex)
				{
					// We have exhausted the possibilities for unanchored
					// patterns, so move on to the anchored ones.
					operators = listOf(",")
					symbolIndices = mutableListOf()
					operatorIndices = mutableListOf()
					exactMatchIndex = 1
					// Handle the empty case specially, as it greatly simplifies
					// the algorithm.
					return@generateSequence "="
				}
				// We are done! Answer the terminal sentinel.
				null
			}
		}
	}

	/**
	 * Compute the number of [possible&#32;patterns][patternGenerator] given a
	 * vocabulary of [n] symbols.
	 *
	 * @param n
	 *   The size of the vocabulary.
	 * @return
	 *   The count of patterns.
	 */
	private fun patternCount(n: Int): Int
	{
		val exactMatchCount = (0 .. n).sumOf { bellNumber(it) }
		val unanchoredCount = (0 .. n).sumOf {
			// The base for the exponent is the number of binary operators.
			bellNumber(it) * 2.0.pow(it - 1).toInt()
		}
		return exactMatchCount + unanchoredCount
	}

	/**
	 * Answer a generator of sequences of the specified [symbols], such that
	 * each sequence contains no more than [maxSize] symbols.
	 *
	 * @param symbols
	 *   The vocabulary of [interned][String.intern] symbols for sequence
	 *   generation.
	 * @param maxSize
	 *   The maximum size of a generated sequence.
	 * @return
	 *   The generator.
	 */
	private fun sequenceGenerator(
		symbols: List<String>,
		maxSize: Int
	): Sequence<List<String>>
	{
		var first = true
		var symbolIndices = mutableListOf<Int>()
		val makeSequence = { symbolIndices.map { symbols[it] } }
		return generateSequence {
			if (first)
			{
				// This simplifies generation of the initial sequence.
				first = false
				return@generateSequence emptyList<String>()
			}
			for (symbolIndex in symbolIndices.indices)
			{
				// Iterate through every possible combination of operators.
				if (symbolIndices[symbolIndex] < symbols.lastIndex)
				{
					symbolIndices[symbolIndex] =
						symbolIndices[symbolIndex] + 1
					return@generateSequence makeSequence()
				}
				else
				{
					symbolIndices[symbolIndex] = 0
				}
			}
			if (symbolIndices.size < maxSize)
			{
				// We have exhausted the possibilities for sequences of the
				// current size, so increase the sequence size.
				symbolIndices = (0 .. symbolIndices.size).mapTo(
					mutableListOf()
				) { 0 }
				return@generateSequence makeSequence()
			}
			null
		}
	}

	/**
	 * Compute the number of [possible&#32;sequences][sequenceGenerator] given
	 * a vocabulary of [n] symbols and a maximum sequence size of [maxSize].
	 *
	 * @param n
	 *   The size of the vocabulary.
	 * @param maxSize
	 *   The maximum sequence size.
	 * @return
	 *   The count of patterns.
	 */
	@Suppress("SameParameterValue")
	private fun sequenceCount(n: Int, maxSize: Int) =
		(0 .. maxSize).sumOf { n.toDouble().pow(it).toInt() }

	/**
	 * Compute the [n]-th Bell number.
	 *
	 * @param n
	 *   The ordinal for the recurrence relation.
	 * @return
	 *   The [n]-th Bell number.
	 * @see <a href="https://en.wikipedia.org/wiki/Bell_number#Triangle_scheme_for_calculations">Bell number</a>
	 */
	private fun bellNumber(n: Int): Int =
		when (n)
		{
			0, 1 -> 1
			else -> (0 until n).sumOf { k ->
				((n - 1) choose k) * bellNumber(k)
			}
		}

	/**
	 * Compute the binomial coefficient for `n` and [k], where `n` is the
	 * receiver. This is the coefficient of the `x^k` term in the polynomial
	 * expansion of the binomial power `(1 + x)^n`.
	 *
	 * @param k
	 *   The exponent.
	 * @return
	 *   The binomial coefficient for `this` and [k].
	 * @see <a href="https://en.wikipedia.org/wiki/Binomial_coefficient">Binomial coefficient</a>
	 */
	private infix fun Int.choose(k: Int) =
		factorial(this) / (factorial(k) * factorial(this - k))

	/**
	 * Compute the factorial of [n].
	 *
	 * @param n
	 *   A nonzero integer.
	 * @return
	 *   The factorial of [n].
	 * @see <a href="https://en.wikipedia.org/wiki/Factorial">Factorial</a>
	 */
	private fun factorial(n: Int) =
		when (n)
		{
			0, 1 -> 1
			else -> (2 .. n).reduce { p, t -> p * t }
		}
}
