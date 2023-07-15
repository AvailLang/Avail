/*
 * DocumentPosition.kt
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

package avail.anvil.text

import avail.anvil.AvailEditor
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import javax.swing.text.Caret
import javax.swing.text.Document
import javax.swing.text.Element
import javax.swing.text.JTextComponent
import kotlin.math.absoluteValue

/**
 * An abstract location within a document opened in an [AvailEditor].
 *
 * @author Richard Arriaga
 */
sealed class DocumentPosition: JSONFriendly
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

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(DocumentPosition::line.name) { write(line) }
			at(DocumentPosition::characterInLine.name) {
				write(characterInLine)
			}
			at(DocumentPosition::offset.name) { write(offset) }
		}
	}

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
		"$lineOneBased:$characterInLineOneBased (@$offset)"

	companion object
	{
		/**
		 * Extract the [MarkPosition] from the provided [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract the data from.
		 * @return
		 *   A [MarkPosition].
		 */
		fun from (obj: JSONObject): MarkPosition
		{
			if (!obj.containsKey(MarkPosition::line.name)
				|| !obj.containsKey(MarkPosition::characterInLine.name)
				|| !obj.containsKey(MarkPosition::offset.name))
			{
				return MarkPosition(0, 0, 0)
			}
			return MarkPosition(
				obj.getNumber(MarkPosition::line.name).int,
				obj.getNumber(MarkPosition::characterInLine.name).int,
				obj.getNumber(MarkPosition::offset.name).int)
		}
	}
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

	companion object
	{
		/**
		 * Extract the [DotPosition] from the provided [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract the data from.
		 * @return
		 *   A [DotPosition].
		 */
		fun from (obj: JSONObject): DotPosition
		{
			if (!obj.containsKey(DotPosition::line.name)
				|| !obj.containsKey(DotPosition::characterInLine.name)
				|| !obj.containsKey(DotPosition::offset.name))
			{
				return DotPosition(0, 0, 0)
			}
			return DotPosition(
				obj.getNumber(DotPosition::line.name).int,
				obj.getNumber(DotPosition::characterInLine.name).int,
				obj.getNumber(DotPosition::offset.name).int)
		}
	}
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
	val dotPosition: DotPosition
): JSONFriendly
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

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(MarkToDotRange::markPosition.name) {
				markPosition.writeTo(this)
			}
			at(MarkToDotRange::dotPosition.name) {
				dotPosition.writeTo(this)
			}
		}
	}

	override fun toString(): String
	{
		val m = markPosition
		val d = dotPosition
		return when (count.absoluteValue)
		{
			0 -> m.toString()
			1 ->
				"${m.lineOneBased}:${m.characterInLineOneBased} — " +
					"${d.lineOneBased}:${d.characterInLineOneBased} " +
					"(1 char @${m.offset})"

			else ->
				"${m.lineOneBased}:${m.characterInLineOneBased} — " +
					"${d.lineOneBased}:${d.characterInLineOneBased} " +
					"($count chars @${m.offset})"
		}
	}

	companion object
	{
		/**
		 * Extract the [MarkToDotRange] from the provided [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract the data from.
		 * @return
		 *   A [MarkToDotRange].
		 */
		fun from (obj: JSONObject): MarkToDotRange
		{
			if (!obj.containsKey(MarkToDotRange::markPosition.name)
				|| !obj.containsKey(MarkToDotRange::dotPosition.name))
			{
				return MarkToDotRange(
					MarkPosition(0, 0, 0),
					DotPosition(0, 0, 0))
			}
			return MarkToDotRange(
				MarkPosition.from(
					obj.getObject(MarkToDotRange::markPosition.name)),
				DotPosition.from(
					obj.getObject(MarkToDotRange::dotPosition.name)))
		}
	}
}
