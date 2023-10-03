/*
 * StylingRecord.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.persistence.cache.record

import avail.descriptor.module.A_Module
import avail.utility.decodeString
import avail.utility.sizedString
import avail.utility.unvlqInt
import avail.utility.vlq
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Styling information that was collected during compilation of a
 * [module][A_Module].
 */
class StylingRecord
{
	/**
	 * An ascending sequence of non-overlapping, non-empty [IntRange]s, with the
	 * style name to apply to that range.
	 */
	val styleRuns: List<StyleRun>

	/**
	 * Information about variable uses and declarations.  The pairs go from use
	 * to declaration.  The [IntRange]s are all one-based.
	 */
	val variableUses: List<Pair<IntRange, IntRange>>

	/**
	 * Information about variable declarations.  The [IntRange]s are all
	 * one-based.
	 */
	val declarations: List<IntRange>

	/**
	 * Output this styling record to the provided [DataOutputStream]. It can
	 * later be reconstructed via the constructor taking a [DataInputStream].
	 *
	 * @param binaryStream
	 *   A DataOutputStream on which to write this styling record.
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	internal fun write(binaryStream: DataOutputStream)
	{
		val allStyles = styleRuns.map(StyleRun::second).distinct().sorted()
		val styleToIndex =
			allStyles.withIndex().associate { (i, name) -> name to i + 1 }
		// Output all style names.
		binaryStream.vlq(allStyles.size)
		allStyles.forEach(binaryStream::sizedString)
		var pos = 0
		// Collect the <style#, length> pairs, dropping any zero-length spans.
		val nonemptyRuns = mutableListOf<Pair<Int, Int>>()
		styleRuns.forEach { (run, styleName) ->
			val delta = run.first - pos
			assert (delta >= 0)
			if (delta > 0)
			{
				// Output an unstyled span.
				nonemptyRuns.add(0 to delta)
			}
			assert (run.last > run.first)
			nonemptyRuns.add(
				styleToIndex[styleName]!! to run.last - run.first)
			pos = run.last
		}
		// Output contiguous styled (and unstyled) spans.
		binaryStream.vlq(nonemptyRuns.size)
		nonemptyRuns.forEach { (styleNumber, length) ->
			binaryStream.vlq(styleNumber)
			binaryStream.vlq(length)
		}

		// Output declaration information.
		val declarationsWithUses = declarationsWithUses()
		binaryStream.vlq(declarationsWithUses.size)
		var previousDeclarationEnd = 0
		declarationsWithUses.forEach { (declaration, uses) ->
			// Write declaration's start relative to previous declaration's end
			binaryStream.vlq(declaration.first - previousDeclarationEnd)
			// And the declaration string size.
			val declarationSize = declaration.last - declaration.first + 1
			binaryStream.vlq(declarationSize)
			// Use a compact encoding if the uses are all after the declaration,
			// and the uses all have the same size as the declaration.  This
			// will be the usual case.
			val isCompact = uses.all {
				it.last - it.first + 1 == declarationSize
					&& it.first > declaration.last
			}
			if (isCompact)
			{
				binaryStream.vlq(uses.size)
				var previousUseEnd = previousDeclarationEnd
				uses.forEach { use ->
					binaryStream.vlq(use.first - previousUseEnd)
					previousUseEnd = use.last + 1
				}
			}
			else
			{
				// Flag to indicate it was non-compact.
				binaryStream.vlq(0)
				binaryStream.vlq(uses.size)
				var previousUseEnd = 0
				uses.forEach { use ->
					binaryStream.vlq(use.first - previousUseEnd)
					binaryStream.vlq(use.last - use.first + 1)
					previousUseEnd = use.last + 1
				}
			}
			previousDeclarationEnd = declaration.last + 1
		}

		// Output only those declarations ranges that had no uses.
		val unusedDeclarations =
			declarations - declarationsWithUses.map(Pair<IntRange, *>::first)
		binaryStream.vlq(unusedDeclarations.size)
		unusedDeclarations.forEach { range ->
			binaryStream.vlq(range.first)
			binaryStream.vlq(range.last - range.first + 1)
		}
	}

	/**
	 * Using the [variableUses], collate the use/definition pairs into
	 * definition/all-uses pairs.  The result is a [List] of such entries
	 * ordered by the definitions' positions.  Each entry is a [Pair] consisting
	 * of an [IntRange] for the definition and an inner [List] of [IntRange]s
	 * for each use, also ordered by position.  All ranges are one-based.
	 */
	fun declarationsWithUses(): List<Pair<IntRange, List<IntRange>>> =
		variableUses
			// Convert to map<declaration, list<uses>>.
			.groupBy({ it.second }) { it.first }
			// Convert to list<pair<declaration, sorted_list<uses>>>.
			.map { (k, v) -> k to v.sortedBy { it.first } }
			// Convert to sorted_list<pair<declaration, sorted_list<uses>>>.
			.sortedBy { it.first.first }

	override fun toString(): String =
		String.format(
			"StylingRecord (%d styled runs)",
			styleRuns.size)

	/**
	 * Reconstruct a [StylingRecord], having previously been written via
	 * [write].
	 *
	 * @param bytes
	 *   Where to read the [StylingRecord] from.
	 * @throws IOException
	 *   If I/O fails.
	 */
	@Throws(IOException::class)
	internal constructor(bytes: ByteArray)
	{
		val binaryStream = DataInputStream(ByteArrayInputStream(bytes))
		val styles = Array(binaryStream.unvlqInt()) {
			binaryStream.decodeString()
		}
		// Read all the spans, including unstyled ones.
		var pos = 0
		val allRuns = mutableListOf<StyleRun>()
		repeat(binaryStream.unvlqInt()) {
			val styleNumber = binaryStream.unvlqInt()
			val length = binaryStream.unvlqInt()
			pos += length
			if (styleNumber > 0)
			{
				allRuns.add(
					(pos - length .. pos) to styles[styleNumber - 1])
			}
		}
		styleRuns = allRuns

		// Reconstruct variableUses.
		val usesToDeclarations = mutableListOf<Pair<IntRange, IntRange>>()
		var previousDeclarationEnd = 0
		repeat(binaryStream.unvlqInt()) {
			val declarationStart =
				previousDeclarationEnd + binaryStream.unvlqInt()
			val length = binaryStream.unvlqInt()
			val declaration = declarationStart until declarationStart + length
			val usagesOrZero = binaryStream.unvlqInt()
			if (usagesOrZero == 0)
			{
				// Special form, where uses may precede declaration, or have
				// tokens of a different size.  Rare.
				var previousUseEnd = 0
				repeat(binaryStream.unvlqInt()) {
					val useStart = previousUseEnd + binaryStream.unvlqInt()
					val size = binaryStream.unvlqInt()
					val use = useStart until useStart + size
					usesToDeclarations.add(use to declaration)
					previousUseEnd = use.last + 1
				}
			}
			else
			{
				// Compact form.  Uses must follow declaration, and must all
				// have the same size token as the declaration.
				var previousUseEnd = previousDeclarationEnd
				repeat(usagesOrZero) {
					val useStart = previousUseEnd + binaryStream.unvlqInt()
					val use = useStart until useStart + length
					usesToDeclarations.add(use to declaration)
					previousUseEnd = use.last + 1
				}
			}
			previousDeclarationEnd = declarationStart + length
		}
		variableUses = usesToDeclarations

		val numUnusedDeclarations = binaryStream.unvlqInt()
		val theDeclarations = mutableSetOf<IntRange>()
		repeat(numUnusedDeclarations) {
			val start = binaryStream.unvlqInt()
			val size = binaryStream.unvlqInt()
			theDeclarations.add(start until start + size)
		}
		usesToDeclarations.mapTo(theDeclarations, Pair<*, IntRange>::second)
		declarations = theDeclarations.sortedBy(IntRange::first)

		assert(binaryStream.available() == 0)
	}

	/**
	 * Construct a new [StylingRecord] from its parts.
	 *
	 * @param styleRuns
	 *   An ascending sequence of non-overlapping, non-empty [IntRange]s, with
	 *   the style name to apply to that range.  The ranges are
	 *   `start .. pastEnd` in zero-based numbering within the UTF-16 Java
	 *   string.
	 * @param variableUses
	 *   Information about variable uses and definitions.  The pairs go from use
	 *   to definition.  The [IntRange]s are as for [styleRuns].
	 * @param declarations
	 *   All local declarations within the file, even if not referenced by any
	 *   entries in [variableUses].  Each range is as per [styleRuns].
	*/
	constructor(
		styleRuns: List<StyleRun>,
		variableUses: List<Pair<IntRange, IntRange>>,
		declarations: List<IntRange>)
	{
		this.styleRuns = styleRuns
		this.variableUses = variableUses
		this.declarations = declarations
	}
}

/**
 * An [IntRange] and name of the style to apply to the source characters of that
 * range.
 */
typealias StyleRun = Pair<IntRange, String>
