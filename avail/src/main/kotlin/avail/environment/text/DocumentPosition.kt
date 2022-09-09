/*
 * CursorPosition.kt
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

package avail.environment.text

import avail.environment.AvailEditor
import javax.swing.text.Caret
import javax.swing.text.Document
import javax.swing.text.Element
import javax.swing.text.JTextComponent

/**
 * An abstract location within a document opened in an [AvailEditor].
 *
 * @author Richard Arriaga
 */
sealed class DocumentPosition
{
	/**
	 * The 0-based line number of this [DocumentPosition].
	 */
	abstract val line: Int

	/**
	 * The 1-based line number of this [DocumentPosition].
	 */
	val lineOneBased: Int get() = line + 1

	/**
	 * The 0-based character position within the [line].
	 */
	abstract val characterInLine: Int

	/**
	 * The 1-based character position within the [lineOneBased].
	 */
	val characterInLineOneBased: Int get() = characterInLine + 1

	/**
	 * The 0-based character position relative to the start of the document.
	 */
	abstract val offset: Int

	/**
	 * Answer the [Element] inside which this [DocumentPosition] resides.
	 *
	 * @param document
	 *   The [Document] containing this [DocumentPosition].
	 * @return
	 *   The [Element] containing this [DocumentPosition].
	 */
	fun containedInElement (document: Document): Element =
		document.defaultRootElement.getElement(line)
}

/**
 * The [DocumentPosition] of the [Caret.getMark]; the leftmost position.
 *
 * @author Richard Arriaga
 */
data class MarkPosition constructor(
	override val line: Int,
	override val characterInLine: Int,
	override val offset: Int
): DocumentPosition()
{
	override fun toString(): String =
		"$lineOneBased:$characterInLineOneBased ($offset)"
}

/**
 * The [DocumentPosition] of the [Caret.getDot]; the rightmost position.
 *
 * @author Richard Arriaga
 */
data class DotPosition constructor(
	override val line: Int,
	override val characterInLine: Int,
	override val offset: Int
): DocumentPosition()
{
	override fun toString(): String =
		"$lineOneBased:$characterInLineOneBased ($offset)"
}

/**
 * The range between the cursor dot and the cursor caret.
 *
 * @author Richard Arriaga
 *
 * @property markPosition
 *   The cursor's [MarkPosition].
 * @property dotPosition
 *   The cursor's [DotPosition].
 */
data class MarkToDotRange constructor(
	val markPosition: MarkPosition,
	val dotPosition: DotPosition)
{
	/**
	 * Answer the number of characters included in this [MarkToDotRange].
	 */
	val count: Int get() = dotPosition.offset - markPosition.offset

	/**
	 * Answer the String of characters selected between the [DotPosition] and
	 * the [MarkPosition].
	 *
	 * @param jTextComponent
	 *   The [JTextComponent] to extract the selected text from.
	 * @return
	 *   The selected text.
	 */
	fun selected (jTextComponent: JTextComponent): String =
		jTextComponent.document.getText(markPosition.offset, count)

	override fun toString(): String =
		if (count == 0)
		{
			markPosition.toString()
		}
		else
		{
			"${markPosition.lineOneBased}:${markPosition.characterInLineOneBased} — " +
				"${dotPosition.lineOneBased}:${dotPosition.characterInLineOneBased} " +
				"($count chars)"
		}
}
