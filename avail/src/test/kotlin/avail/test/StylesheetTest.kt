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
import avail.anvil.Stylesheet
import avail.anvil.UnvalidatedRenderingContext
import avail.anvil.UnvalidatedStylePattern
import org.availlang.artifact.environment.project.Palette
import org.availlang.artifact.environment.project.StyleAttributes
import org.availlang.json.JSONObject
import org.availlang.json.JSONReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import org.junit.jupiter.params.provider.Arguments.of as argumentsOf
import java.util.stream.Stream.of as streamOf

/**
 * Tests for [stylesheets][Stylesheet], especially [style&#32;pattern][StylePattern]
 * compilation and execution.
 *
 * @author Todd Smith &lt;todd@availlang.org&gt;
 */
class StylesheetTest
{
	/**
	 * Test the [positive&#32;exemplars][positiveExemplars]. This is an
	 * end-to-end test of parsing, code generation, rendering context
	 * validation, and disassembly.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("getPositiveExemplars")
	fun testPositiveExemplars(source: String, expected: String)
	{
		val pattern = UnvalidatedStylePattern(source, renderingContext)
		val rule = compile(pattern, palette)
		assertEquals(expected, rule.toString())
	}

	companion object
	{
		/**
		 * The [rendering&#32;context][RenderingContext] for testing the
		 * exemplars.
		 */
		val renderingContext = UnvalidatedRenderingContext(StyleAttributes())

		/**
		 * The [palette][Palette] for testing the exemplars. Excerpted from the
		 * Avail configuration file (`avail-config.json`) as it existed at the
		 * time of writing (2022.11.26)
		 */
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
						0
					instructions:
						@0: match literal #0 <#foo>
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
						01
					instructions:
						@0: match literal #0 <#foo>
						@1: match literal #1 <#baz>
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
						010
					instructions:
						@0: match literal #0 <#foo>
						@1: match literal #1 <#baz>
						@2: match literal #0 <#foo>
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
						00
					instructions:
						@0: match literal #0 <#foo>
						@1: match literal #0 <#foo>
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
						000
					instructions:
						@0: match literal #0 <#foo>
						@1: match literal #0 <#foo>
						@2: match literal #0 <#foo>
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
						012340414243
					instructions:
						@0: match literal #0 <#a>
						@1: match literal #1 <#b>
						@2: match literal #2 <#c>
						@3: match literal #3 <#d>
						@4: match literal #4 <#e>
						@6: match literal #5 <#f>
						@8: match literal #6 <#g>
						@10: match literal #7 <#h>
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
						506050
					instructions:
						@0: match literal #0 <#foo> or jump to @0
						@2: match literal #1 <#bar> or jump to @0
						@4: match literal #0 <#foo> or jump to @0
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
						5050
					instructions:
						@0: match literal #0 <#foo> or jump to @0
						@2: match literal #0 <#foo> or jump to @0
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
						50A5050
					instructions:
						@0: match literal #0 <#foo> or jump to @0
						@2: fork to @0
						@3: match literal #0 <#foo> or jump to @0
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
				"#a,#a,#a,#b",
				"""
					#a,#a,#a,#b
					nybblecodes:
						50A505060
					instructions:
						@0: match literal #0 <#a> or jump to @0
						@2: fork to @0
						@3: match literal #0 <#a> or jump to @0
						@5: match literal #0 <#a> or jump to @0
						@7: match literal #1 <#b> or jump to @0
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
						50605070
					instructions:
						@0: match literal #0 <#a> or jump to @0
						@2: match literal #1 <#b> or jump to @0
						@4: match literal #0 <#a> or jump to @0
						@6: match literal #2 <#c> or jump to @0
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
						50605070A5060507080
					instructions:
						@0: match literal #0 <#a> or jump to @0
						@2: match literal #1 <#b> or jump to @0
						@4: match literal #0 <#a> or jump to @0
						@6: match literal #2 <#c> or jump to @0
						@8: fork to @0
						@9: match literal #0 <#a> or jump to @0
						@11: match literal #1 <#b> or jump to @0
						@13: match literal #0 <#a> or jump to @0
						@15: match literal #2 <#c> or jump to @0
						@17: match literal #3 <#d> or jump to @0
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
	}
}
