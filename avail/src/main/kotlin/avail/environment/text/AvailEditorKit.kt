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
import avail.environment.text.AvailEditorKit.Companion.indent
import avail.environment.text.AvailEditorKit.Companion.outdent
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JFrame
import javax.swing.JTextPane
import javax.swing.text.BadLocationException
import javax.swing.text.Document
import javax.swing.text.StyledEditorKit
import javax.swing.text.TextAction
import javax.swing.undo.CompoundEdit
import javax.swing.undo.UndoManager

class AvailEditorKit constructor(
	val workbench: AvailWorkbench,
	val frame: JFrame
) : StyledEditorKit()
{
	private val defaultActions = arrayOf<Action>(
		IncreaseIndentation,
		DecreaseIndentation
	)

	override fun getActions(): Array<Action>
	{
		return TextAction.augmentList(super.getActions(), defaultActions)
	}

	companion object
	{
		const val indent = insertTabAction
		const val outdent = "outdent"
	}
}

/**
 * If some text is selected, then indent the lines enclosing the selection. If
 * no text is selected, then insert horizontal tabulation (U+0009) at the caret.
 */
private object IncreaseIndentation : TextAction(indent)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val sourcePane = e.source as JTextPane
		val document = sourcePane.document
		val selectionStart = sourcePane.selectionStart
		val selectionEnd = sourcePane.selectionEnd
		if (selectionStart == selectionEnd)
		{
			// There is no text selection, so insert a tab at the caret.
			document.insertString(selectionStart, "\t", null)
		}
		else
		{
			sourcePane.transaction {
				// Insert a tab at each insertion point. Iterate backward to
				// avoid altering the positions.
				val insertionPoints = sourcePane.lineStartsInSelection()
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
 *   The position from which to scan leftward in search of the beginning
 *   of the enclosing line.
 * @return
 *   The beginning of the line. This is either the beginning of the
 *   text or a position immediately following a linefeed (U+000A).
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
 * Locate the beginnings of all lines enclosing the active selection. If no text
 * is selected, then locate the beginning of the line containing the caret.
 *
 * @return
 *   The positions of the desired linefeed (U+0009) characters.
 */
fun JTextPane.lineStartsInSelection(): List<Int>
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
	(firstLineStart + 1 until lastLineStart - 2).forEach { position ->
		// The bounds are a bit tricky to avoid redundant computation
		// for the two termini.
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
internal inline fun JTextPane.transaction(edit: ()->Unit)
{
	val undoManager = getClientProperty(
		AvailEditor::undoManager.name) as? UndoManager
	if (undoManager === null)
	{
		edit()
	}
	else
	{
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
