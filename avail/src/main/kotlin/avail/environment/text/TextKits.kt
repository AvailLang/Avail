/*
 * TextKits.kt
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

import avail.environment.AvailEditor.Companion.editor
import avail.environment.AvailWorkbench
import avail.environment.editor.GoToDialog
import avail.environment.tasks.BuildTask
import avail.environment.text.AvailEditorKit.Companion.goToDialog
import avail.environment.text.AvailEditorKit.Companion.openStructureView
import avail.environment.text.AvailEditorKit.Companion.refresh
import avail.environment.text.CodeKit.Companion.breakLine
import avail.environment.text.CodeKit.Companion.cancelTemplateSelection
import avail.environment.text.CodeKit.Companion.centerCurrentLine
import avail.environment.text.CodeKit.Companion.expandTemplate
import avail.environment.text.CodeKit.Companion.indent
import avail.environment.text.CodeKit.Companion.outdent
import avail.environment.text.CodeKit.Companion.redo
import avail.environment.text.CodeKit.Companion.space
import avail.environment.text.CodeKit.Companion.undo
import avail.environment.text.CodePane.Companion.codePane
import avail.environment.views.StructureViewPanel
import avail.utility.Strings.tabs
import java.awt.Point
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JTextPane
import javax.swing.JViewport
import javax.swing.SwingUtilities.getAncestorOfClass
import javax.swing.UIManager
import javax.swing.text.BadLocationException
import javax.swing.text.Caret
import javax.swing.text.Document
import javax.swing.text.EditorKit
import javax.swing.text.JTextComponent
import javax.swing.text.StyledEditorKit
import javax.swing.text.TextAction
import javax.swing.undo.CompoundEdit
import javax.swing.undo.UndoManager
import kotlin.math.max
import kotlin.math.min

/**
 * An [EditorKit] that supports multiline Avail source code, but not necessarily
 * for the purpose of editing an Avail source module.
 *
 * @property workbench
 *   The associated [AvailWorkbench].
 * @author Todd L Smith &lt;todd@availlong.org&gt;
 */
open class CodeKit constructor(
	val workbench: AvailWorkbench
) : StyledEditorKit()
{
	/** The default actions registered by this [kit][EditorKit]. */
	protected open val defaultActions = arrayOf<Action>(
		InsertSpace,
		BreakLine,
		IncreaseIndentation,
		DecreaseIndentation,
		CenterCurrentLine,
		Undo,
		Redo,
		ExpandTemplate,
		CancelTemplateSelection
	)

	final override fun getActions(): Array<Action> =
		TextAction.augmentList(super.getActions(), defaultActions)

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

		/** The name of the [CenterCurrentLine] action. */
		const val centerCurrentLine = "center-current-line"

		/** The name of the [Undo] action. */
		const val undo = "undo"

		/** The name of the [Redo] action. */
		const val redo = "redo"

		/** The name of the [ExpandTemplate] action. */
		const val expandTemplate = "expand-template"

		/** The name of the [CancelTemplateSelection] action. */
		const val cancelTemplateSelection = "cancel-template-selection"
	}
}

/**
 * An [EditorKit] that supports editing an Avail source module.
 *
 * @author Todd L Smith &lt;todd@availlong.org&gt;
 *
 * @constructor
 *
 * Construct an [AvailEditorKit].
 *
 * @param workbench
 *   The associated [AvailWorkbench].
 */
class AvailEditorKit constructor(workbench: AvailWorkbench) : CodeKit(workbench)
{
	override val defaultActions = super.defaultActions + arrayOf<Action>(
		GoToDialogAction,
		OpenStructureView,
		Refresh
	)

	companion object
	{
		/** The name of the [GoToDialog] action. */
		const val goToDialog = "go-to-dialog"

		/** The name of the [OpenStructureView] action. */
		const val openStructureView = "open-structure-view"

		/** The name of the [OpenStructureView] action. */
		const val refresh = "refresh"
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
			CodePane::currentEdit.name) as? CompoundEdit
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

/**
 * The undo action.
 */
private object Undo: TextAction(undo)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val codePane = e.codePane
		codePane.currentEdit?.end()
		codePane.undoManager.undo()
		codePane.clearStaleTemplateSelectionState()
	}
}

/**
 * The redo action.
 */
private object Redo: TextAction(redo)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val codePane = e.codePane
		codePane.currentEdit?.end()
		codePane.undoManager.redo()
		codePane.clearStaleTemplateSelectionState()
	}
}

/**
 * Expand the template selection.
 */
private object ExpandTemplate: TextAction(expandTemplate)
{
	override fun actionPerformed(e: ActionEvent)
	{
		e.codePane.expandTemplate()
	}
}

/**
 * Cancel the template selection.
 */
private object CancelTemplateSelection: TextAction(cancelTemplateSelection)
{
	override fun actionPerformed(e: ActionEvent)
	{
		e.codePane.cancelTemplateExpansion()
	}
}

/**
 * Open the [StructureViewPanel].
 */
private object OpenStructureView: TextAction(openStructureView)
{
	override fun actionPerformed(e: ActionEvent)
	{
		e.editor.openStructureView(false)
	}
}

/**
 * Open the [GoToDialog].
 */
private object GoToDialogAction: TextAction(goToDialog)
{
	override fun actionPerformed(e: ActionEvent)
	{
		GoToDialog(e.editor)
	}
}

/**
 * Rebuild the open editor's module and refresh the screen style.
 */
private object Refresh: TextAction(refresh)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val editor = e.editor
		val workbench = editor.workbench
		val buildTask = BuildTask(workbench, editor.resolvedName)
		workbench.backgroundTask = buildTask
		workbench.availBuilder.checkStableInvariants()
		workbench.setEnablements()
		buildTask.execute()
	}
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
 * Set the [caret dot][Caret.setDot] and [caret mark][Caret.getMark] from the
 * given [MarkToDotRange].
 *
 * @param range
 *   The [MarkToDotRange] to use to  position the [JTextComponent.caret].
 */
fun JTextComponent.setCaretFrom (range: MarkToDotRange)
{
	caret.apply {
		dot = range.markPosition.offset
		moveDot(range.dotPosition.offset)
	}
}

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
 * [undoManager][CodePane.undoManager]) on the receiver.
 *
 * @param edit
 *   The scope of the transaction. Any edits performed during application can be
 *   undone/redone with a single action.
 */
internal inline fun JTextPane.transaction(edit: JTextPane.()->Unit)
{
	val undoManager = getClientProperty(
		CodePane::undoManager.name) as? UndoManager
	if (undoManager === null)
	{
		edit()
	}
	else
	{
		val currentEdit = getClientProperty(
			CodePane::currentEdit.name) as? CompoundEdit
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
