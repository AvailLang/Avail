/*
 * AvailEditorKit.kt
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

package avail.environment.text

import avail.environment.AvailEditor
import avail.environment.AvailWorkbench
import avail.environment.text.AvailEditorKit.Companion.breakLine
import avail.environment.text.AvailEditorKit.Companion.centerCurrentLine
import avail.environment.text.AvailEditorKit.Companion.indent
import avail.environment.text.AvailEditorKit.Companion.outdent
import avail.environment.text.AvailEditorKit.Companion.space
import avail.utility.Strings.tabs
import java.awt.Point
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JFrame
import javax.swing.JTextPane
import javax.swing.JViewport
import javax.swing.SwingUtilities.getAncestorOfClass
import javax.swing.UIManager
import javax.swing.text.BadLocationException
import javax.swing.text.Document
import javax.swing.text.JTextComponent
import javax.swing.text.StyledEditorKit
import javax.swing.text.TextAction
import javax.swing.undo.CompoundEdit
import javax.swing.undo.UndoManager
import kotlin.math.max
import kotlin.math.min

class AvailEditorKit constructor(
	val workbench: AvailWorkbench,
	val frame: JFrame
) : StyledEditorKit()
{
	private val defaultActions = arrayOf<Action>(
		InsertSpace,
		BreakLine,
		IncreaseIndentation,
		DecreaseIndentation,
		CenterCurrentLine
	)

	override fun getActions(): Array<Action>
	{
		return TextAction.augmentList(super.getActions(), defaultActions)
	}

	companion object
	{
		/** The name of the [InsertSpace] action. */
		const val space = "insert-space"

		/** The name of the [BreakLine] action. */
		const val breakLine = insertBreakAction

		/** The name of the [IncreaseIndentation] action. */
		const val indent = insertTabAction

		/** The name of the [DecreaseIndentation] action. */
		const val outdent = "outdent"

		/**
		 * The name of the [CenterCurrentLine] action.
		 */
		const val centerCurrentLine = "center-current-line"
	}
}

/**
 * Replace the current selection with a space (U+0020) and start a new
 * [CompoundEdit] to aggregate further content updates.
 */
private object InsertSpace : TextAction(space)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val sourcePane = e.source as JTextPane
		if (!sourcePane.canEdit)
		{
			UIManager.getLookAndFeel().provideErrorFeedback(sourcePane)
			return
		}
		val currentEdit = sourcePane.getClientProperty(
			AvailEditor::currentEdit.name) as? CompoundEdit
		currentEdit?.end()
	}
}

/**
 * Break the current line by replacing the current selection with a linefeed
 * (U+000A) and as much horizontal tabulation (U+0009) as began the line.
 */
private object BreakLine : TextAction(breakLine)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val sourcePane = e.source as JTextPane
		if (!sourcePane.canEdit)
		{
			UIManager.getLookAndFeel().provideErrorFeedback(sourcePane)
			return
		}
		val document = sourcePane.document
		val caretPosition = sourcePane.caretPosition
		val lineStart = document.lineStartBefore(caretPosition - 1)
		val indent = document.indentationAt(lineStart)
		sourcePane.transaction {
			val lineSeparator = System.lineSeparator()
			replaceSelection(lineSeparator + tabs(indent))
		}
	}
}

/**
 * If some text is selected, then indent the lines enclosing the selection. If
 * no text is selected and the caret is at the beginning of the line, then
 * insert as much horizontal tabulation (U+0009) at the caret as occurred on the
 * previous line. Otherwise, insert a single horizontal tabulation (U+0009) at
 * the caret.
 */
private object IncreaseIndentation : TextAction(indent)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val sourcePane = e.source as JTextPane
		if (!sourcePane.canEdit)
		{
			UIManager.getLookAndFeel().provideErrorFeedback(sourcePane)
			return
		}
		val document = sourcePane.document
		val selectionStart = sourcePane.selectionStart
		val selectionEnd = sourcePane.selectionEnd
		if (selectionStart == selectionEnd)
		{
			// There is no text selection.
			if (selectionStart < 2)
			{
				// The caret is effectively at the beginning of the document,
				// such that there can't be any previous indented lines, so just
				// insert a tab at the caret.
				document.insertString(selectionStart, "\t", null)
			}
			else if (document.codePointAt(selectionStart - 1) == '\n'.code)
			{
				// The caret is at the beginning of a line, so insert as many
				// tabs as occurred on the previous line. Always insert at least
				// one tab.
				val indent =
					max(1, document.indentationBefore(selectionStart - 2))
				document.insertString(selectionStart, tabs(indent), null)
			}
			else
			{
				// The caret is not at the beginning of a line, so just insert
				// a tab whenever it is.
				document.insertString(selectionStart, "\t", null)
			}
		}
		else
		{
			sourcePane.transaction {
				// Insert a tab at each insertion point. Iterate backward to
				// avoid altering the positions.
				val insertionPoints = lineStartsInSelection()
				insertionPoints.reversed().forEach { position ->
					document.insertString(position, "\t", null)
				}
			}
		}
	}
}

/**
 * If some text is selected, then outdent the lines enclosing the selection. If
 * no text is selected, then remove at most one horizontal tabulation (U+0009)
 * at the beginning of the line containing the caret.
 */
private object DecreaseIndentation : TextAction(outdent)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val sourcePane = e.source as JTextPane
		if (!sourcePane.canEdit)
		{
			UIManager.getLookAndFeel().provideErrorFeedback(sourcePane)
			return
		}
		val document = sourcePane.document
		val selectionStart = sourcePane.selectionStart
		val selectionEnd = sourcePane.selectionEnd
		if (selectionStart == selectionEnd)
		{
			// There is no text selection, so remove a tab at the beginning of
			// the line containing the caret.
			val lineStart = document.lineStartBefore(
				sourcePane.caretPosition - 1)
			val c = document.codePointAt(lineStart)
			if (c == '\t'.code)
			{
				document.remove(lineStart, 1)
			}
		}
		else
		{
			sourcePane.transaction {
				// Remove a tab at each removal point. Iterate backward to avoid
				// altering the positions.
				val removalPoint = sourcePane.lineStartsInSelection()
				removalPoint.reversed().forEach { position ->
					val c = document.codePointAt(position)
					if (c == '\t'.code)
					{
						document.remove(position, 1)
					}
				}
			}
		}
	}
}

/**
 * Center the current line of the [source&#32;component][JTextComponent] in its
 * enclosing [viewport][JViewport]. If no viewport encloses the receiver, then
 * do not move the caret.
 */
private object CenterCurrentLine: TextAction(centerCurrentLine)
{
	override fun actionPerformed(e: ActionEvent) =
		(e.source as JTextComponent).centerCurrentLine()
}

////////////////////////////////////////////////////////////////////////////////
//                                  Support.                                  //
////////////////////////////////////////////////////////////////////////////////

/**
 * Interrogate the code point at the specified position.
 *
 * @param position
 *   The target position.
 * @return
 *   The requested code point.
 * @throws BadLocationException
 *   If the document is empty.
 */
fun Document.codePointAt(position: Int) =
	getText(position, 1).codePointAt(0)

/**
 * @return
 *  The [DotPosition] of the [JTextPane.caret] in this [JTextPane].
 */
fun JTextComponent.dotPosition(): DotPosition
{
	val offset = caret.dot
	val root = document.defaultRootElement
	val line = root.getElementIndex(offset)
	val element = root.getElement(line)

	return DotPosition(
		line,
		offset - element.startOffset,
		offset)
}

/**
 * @return
 *  The [MarkPosition] of the [JTextPane.caret] in this [JTextPane].
 */
fun JTextComponent.markPosition(): MarkPosition
{
	val offset = caret.mark
	val root = document.defaultRootElement
	val line = root.getElementIndex(offset)
	val element = root.getElement(line)

	return MarkPosition(
		line,
		offset - element.startOffset,
		offset)
}

/**
 * @return
 *  The [MarkToDotRange] of the [JTextPane.caret] in this [JTextPane].
 */
fun JTextComponent.markToDotRange(): MarkToDotRange =
	MarkToDotRange(markPosition(), dotPosition())

/**
 * Interrogate the code point at the specified position.
 *
 * @param position
 *   The target position.
 * @return
 *   The requested code point, or `null` if the document is empty.
 */
@Suppress("unused")
fun Document.codePointOrNullAt(position: Int) =
	try
	{
		getText(position, 1).codePointAt(0)
	}
	catch (e: BadLocationException)
	{
		null
	}

/**
 * Locate the beginning of the line enclosing [position].
 *
 * @param position
 *   The position from which to scan leftward in search of the beginning of the
 *   enclosing line.
 * @return
 *   The beginning of the line. This is either the beginning of the text or a
 *   position immediately following a linefeed (U+000A).
 */
fun Document.lineStartBefore(position: Int): Int
{
	var i = position
	while (i > 0)
	{
		val c = codePointAt(i)
		if (c == '\n'.code)
		{
			// We actually want to skip past the linefeed to place the
			// insertion point.
			i++
			break
		}
		i--
	}
	// `i` is now positioned either (1) at the beginning of the text or
	// (2) just past the nearest linefeed left of `position`.
	return i
}

/**
 * Tally the indentation at [position]. Only horizontal tabulation (U+0009) is
 * considered indentation.
 *
 * @param position
 *   The position at which to scan rightward for indentation.
 * @return
 *   The amount of indentation discovered.
 */
fun Document.indentationAt(position: Int): Int
{
	var i = position
	while (i < length)
	{
		val c = codePointAt(i)
		if (c != '\t'.code) break
		i++
	}
	return i - position
}

/**
 * Tally the indentation for the line enclosing [position]. Only leading
 * horizontal tabulation (U+0009) is considered indentation.
 *
 * @param position
 *   The position from which to scan leftward in search of the beginning of the
 *   enclosing line.
 * @return
 *   The amount of indentation discovered.
 */
fun Document.indentationBefore(position: Int): Int =
	indentationAt(lineStartBefore(position))

/**
 * Whether the receiver can really be edited.
 */
val JTextComponent.canEdit get() = isEnabled && isEditable

/**
 * Center the current line of the receiver in its enclosing
 * [viewport][JViewport] by moving the caret. If no viewport encloses the
 * receiver, then do not move the caret.
 */
fun JTextComponent.centerCurrentLine()
{
	val viewport =
		getAncestorOfClass(JViewport::class.java, this) as? JViewport
	// If there is no container, then centering is impossible.
	if (viewport === null) return
	try
	{
		val bounds = modelToView2D(caretPosition)
		val extentHeight = viewport.extentSize.height.toDouble()
		val viewHeight = viewport.viewSize.height.toDouble()
		val y = min(
			max(0.0, bounds.y - (extentHeight - bounds.height) / 2),
			viewHeight - extentHeight
		).toInt()
		viewport.viewPosition = Point(0, y)
	}
	catch (e: BadLocationException)
	{
		// We tried our best, but the caret was somehow invalid. Give up.
	}
}

/**
 * Move the caret to the specified line and optional intra-line position.
 * Normalize the positions, to ensure that the document and line limits are not
 * exceeded.
 *
 * @param line
 *   The 0-based target line.
 * @param characterInLine
 *   The 0-based target character position within the line. Defaults to `0`.
 */
fun JTextComponent.goTo(line: Int, characterInLine: Int = 0)
{
	val root = document.defaultRootElement
	val normalizedLine = max(0, min(line, root.elementCount - 1))
	val element = root.getElement(normalizedLine)
	val lineStart = element.startOffset
	val position = max(
		min(
		lineStart + characterInLine,
		element.endOffset - 1
		),
		lineStart
	)
	caretPosition = position
	centerCurrentLine()
	requestFocus()
}

/**
 * Locate the beginnings of all lines enclosing the active selection. If no text
 * is selected, then locate the beginning of the line containing the caret.
 *
 * @return
 *   The positions of the desired linefeed (U+0009) characters.
 */
fun JTextComponent.lineStartsInSelection(): List<Int>
{
	if (selectionStart == selectionEnd)
	{
		// There's no text selection, so answer the start of the line featuring
		// the caret.
		return listOf(document.lineStartBefore(selectionStart))
	}
	// Some text is selected, so find the beginnings of the enclosing
	// lines.
	val document = document
	val firstLineStart =
		document.lineStartBefore(selectionStart)
	val lastLineStart =
		document.lineStartBefore(selectionEnd - 1)
	val insertionPoints = mutableListOf(firstLineStart)
	// Avoid unnecessary work if only a single line was involved.
	if (firstLineStart == lastLineStart) return insertionPoints
	(firstLineStart + 1 until lastLineStart - 2).forEach { position ->
		// The bounds are a bit tricky to avoid redundant computation for the
		// two termini.
		val c = document.codePointAt(position)
		if (c == '\n'.code)
		{
			insertionPoints.add(position + 1)
		}
	}
	insertionPoints.add(lastLineStart)
	return insertionPoints
}

/**
 * Aggregate edits on the receiver, such that they may be undone/redone with a
 * single action. Assumes that the an [UndoManager] is available via a
 * [client&#32;property][JTextPane.getClientProperty] (called
 * [undoManager][AvailEditor.undoManager]) on the receiver.
 *
 * @param edit
 *   The scope of the transaction. Any edits performed during application can be
 *   undone/redone with a single action.
 */
internal inline fun JTextPane.transaction(edit: JTextPane.()->Unit)
{
	val undoManager = getClientProperty(
		AvailEditor::undoManager.name) as? UndoManager
	if (undoManager === null)
	{
		edit()
	}
	else
	{
		val currentEdit = getClientProperty(
			AvailEditor::currentEdit.name) as? CompoundEdit
		currentEdit?.end()
		val compoundEdit = CompoundEdit()
		undoManager.addEdit(compoundEdit)
		try
		{
			edit()
		}
		finally
		{
			compoundEdit.end()
		}
	}
}
