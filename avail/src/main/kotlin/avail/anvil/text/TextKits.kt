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

package avail.anvil.text

import avail.anvil.AvailEditor.Companion.editor
import avail.anvil.AvailWorkbench
import avail.anvil.Stylesheet
import avail.anvil.editor.GoToDialog
import avail.anvil.shortcuts.BreakLineShortcut
import avail.anvil.shortcuts.CamelCaseShortcut
import avail.anvil.shortcuts.CancelTemplateSelectionShortcut
import avail.anvil.shortcuts.CenterCurrentLineShortcut
import avail.anvil.shortcuts.DecreaseFontSizeShortcut
import avail.anvil.shortcuts.ExpandTemplateShortcut
import avail.anvil.shortcuts.GoToDialogShortcut
import avail.anvil.shortcuts.IncreaseFontSizeShortcut
import avail.anvil.shortcuts.InsertSpaceShortcut
import avail.anvil.shortcuts.KebabCaseShortcut
import avail.anvil.shortcuts.LowercaseShortcut
import avail.anvil.shortcuts.MoveLineDownShortcut
import avail.anvil.shortcuts.MoveLineUpShortcut
import avail.anvil.shortcuts.OpenStructureViewShortcut
import avail.anvil.shortcuts.OutdentShortcut
import avail.anvil.shortcuts.PascalCaseShortcut
import avail.anvil.shortcuts.RedoShortcut
import avail.anvil.shortcuts.RefreshShortcut
import avail.anvil.shortcuts.SnakeCaseShortcut
import avail.anvil.shortcuts.UndoShortcut
import avail.anvil.shortcuts.UppercaseShortcut
import avail.anvil.shortcuts.RefreshStylesheetShortcut
import avail.anvil.tasks.BuildTask
import avail.anvil.text.CodeKit.Companion.indent
import avail.anvil.text.CodePane.Companion.codePane
import avail.anvil.views.StructureViewPanel
import avail.utility.Strings.tabs
import org.availlang.artifact.environment.project.AvailProject
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
		CancelTemplateSelection,
		DecreaseFontSize,
		IncreaseFontSize,
		MoveLineUp,
		MoveLineDown,
		ToUppercase,
		ToLowercase,
		ToCamelCase,
		ToPascalCase,
		ToSnakeCase,
		ToKebabCase
	)

	final override fun getActions(): Array<Action> =
		TextAction.augmentList(super.getActions(), defaultActions)

	companion object
	{
		/** The name of the [IncreaseIndentation] action. */
		const val indent = insertTabAction
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
		Refresh,
		RefreshStylesheet
	)
}

/**
 * Replace the current selection with a space (U+0020) and start a new
 * [CompoundEdit] to aggregate further content updates.
 */
private object InsertSpace : TextAction(InsertSpaceShortcut.actionMapKey)
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
private object BreakLine : TextAction(BreakLineShortcut.actionMapKey)
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
		val lineStart = max(0, document.lineStartBefore(caretPosition - 1))
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
private object DecreaseIndentation : TextAction(OutdentShortcut.actionMapKey)
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
private object CenterCurrentLine
	: TextAction(CenterCurrentLineShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent) =
		(e.source as JTextComponent).centerCurrentLine()
}

/**
 * The undo action.
 */
private object Undo: TextAction(UndoShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val codePane = e.codePane
		codePane.currentEdit?.end()
		if (codePane.undoManager.canUndo())
		{
			codePane.undoManager.undo()
			codePane.clearStaleTemplateSelectionState()
		}
	}
}

/**
 * The redo action.
 */
private object Redo: TextAction(RedoShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val codePane = e.codePane
		codePane.currentEdit?.end()
		if (codePane.undoManager.canRedo())
		{
			codePane.undoManager.redo()
			codePane.clearStaleTemplateSelectionState()
		}
	}
}

/**
 * Expand the template selection.
 */
private object ExpandTemplate: TextAction(ExpandTemplateShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		e.codePane.expandTemplate()
	}
}

/**
 * Cancel the template selection.
 */
private object CancelTemplateSelection
	: TextAction(CancelTemplateSelectionShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		e.codePane.cancelTemplateExpansion()
	}
}

/**
 * Open the [StructureViewPanel].
 */
private object OpenStructureView
	: TextAction(OpenStructureViewShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		e.editor.openStructureView(false)
	}
}

/**
 * Open the [GoToDialog].
 */
private object GoToDialogAction: TextAction(GoToDialogShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		GoToDialog(e.editor)
	}
}

/**
 * Rebuild the open editor's module and refresh the screen style.
 */
private object Refresh: TextAction(RefreshShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val editor = e.editor
		val workbench = editor.workbench
		workbench.clearTranscript()
		val buildTask = BuildTask(workbench, editor.resolvedName)
		workbench.backgroundTask = buildTask
		workbench.availBuilder.checkStableInvariants()
		workbench.setEnablements()
		buildTask.execute()
	}
}

/**
 * Refresh the [stylesheet][Stylesheet] from the [project][AvailProject]'s
 * configuration file.
 */
private object RefreshStylesheet: TextAction(
	RefreshStylesheetShortcut.actionMapKey
)
{
	override fun actionPerformed(e: ActionEvent) =
		e.editor.workbench.refreshStylesheetAction.runAction()
}

////////////////////////////////////////////////////////////////////////////////
//                                    Font                                    //
////////////////////////////////////////////////////////////////////////////////

/**
 * Increase the size of the font in the [CodePane] by one point size.
 */
private object IncreaseFontSize
	: TextAction(IncreaseFontSizeShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val sourcePane = e.source as CodePane
		sourcePane.changeFontSize(sourcePane.font.size + 1.0f)
	}
}

/**
 * Decrease the size of the font in the [CodePane] by one point size.
 */
private object DecreaseFontSize
	: TextAction(DecreaseFontSizeShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val sourcePane = e.source as CodePane
		sourcePane.changeFontSize(
			(sourcePane.font.size - 1.0f).coerceAtLeast(4.0f))
	}
}

////////////////////////////////////////////////////////////////////////////////
//                                  Move Code                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * Move the current line of the [source&#32;component][JTextComponent] in its
 * enclosing [viewport][JViewport] up one line.
 */
private object MoveLineUp: TextAction(MoveLineUpShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val sourcePane = e.source as JTextPane
		if (!sourcePane.canEdit)
		{
			UIManager.getLookAndFeel().provideErrorFeedback(sourcePane)
			return
		}
		val txt = e.source as JTextComponent
		val lineStarts = txt.lineStartsInSelection()
		if (lineStarts.size == 1 && lineStarts[0] == 0)
		{
			// We are on the first line of the document so there is no line
			// above that this line can be placed before. So just do nothing.
			return
		}
		val lineEnds = txt.lineEndsInSelection()
		val lastLineEnd = lineEnds.last()
		// Get the bounds of the previous line
		val endPrevLine = lineStarts[0] - 1
		val startPrevLine = txt.document.lineStartBefore(endPrevLine)

		// Get the length of the previous line that will be moved below the
		// current selection
		val prevLineLength = lineStarts[0] - startPrevLine

		// Calculate the text that will be moved down taking into account end of
		// line offset if the last line of the file is to move up.
		val moveIncludesLastLine =
			txt.document.endPosition.offset == lastLineEnd + 1
		val txtToMoveDown =
			if (moveIncludesLastLine)
			{
				// The lines to shift up includes the last line of the document
				if (lineStarts[0] == 0)
				{
					// The entire document is highlighted so there is no line
					// shift available. Do nothing.
					return
				}
				// We are shifting up the last line of the file so we want to
				// truncate the text being moved not to include the '\n' because
				// the text being moved will be the last line and by definition
				// the last line of the file does not have a '\n' at the end
				// of the last line.
				txt.document.getText(startPrevLine, prevLineLength - 1)
			}
			else
			{
				// We are not including the last line of the file in the move so
				// no special offsets are required.
				txt.document.getText(startPrevLine, prevLineLength)
			}

		// Calculate the adjustment to the selection for the new start and end
		// of the selection after the text is moved down.
		val shiftedSelectionStart = txt.selectionStart - txtToMoveDown.length
		val shiftedSelectionEnd = txt.selectionEnd - txtToMoveDown.length

		// Move the lines as a single undo-able transaction.
		sourcePane.transaction {
			if (moveIncludesLastLine)
			{
				// Our move involves moving the last line of the file up.
				// Because "lastLineEnd" represents the end of the file, we
				// can't insert the text after the end of the file, we have to
				// insert the text at the end of the file.
				txt.document.insertString(
					lastLineEnd,
					"\n" + txtToMoveDown,
					null)
				// Remove the moved text from its original position.
				txt.document.remove(
					startPrevLine,
					prevLineLength)
			}
			else
			{
				// The line shift does not include the last line of the file so
				// our insertion of the text, "txtToMoveDown", needs to be
				// inserted one position after the "lastLineEnd".
				txt.document.insertString(
					lastLineEnd + 1,
					txtToMoveDown,
					null)
				// Remove the moved text from its original position.
				txt.document.remove(startPrevLine, prevLineLength)
			}
			// Reset the selection to highlight the originally selected text.
			txt.selectionStart = shiftedSelectionStart
			txt.selectionEnd = shiftedSelectionEnd
		}
	}
}

/**
 * Move the current line of the [source&#32;component][JTextComponent] in its
 * enclosing [viewport][JViewport] down one line.
 */
private object MoveLineDown: TextAction(MoveLineDownShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		val sourcePane = e.source as JTextPane
		if (!sourcePane.canEdit)
		{
			UIManager.getLookAndFeel().provideErrorFeedback(sourcePane)
			return
		}
		val txt = e.source as JTextComponent
		val lineEnds = txt.lineEndsInSelection()
		val lastLineEnd = lineEnds.last()
		if (txt.document.endPosition.offset == lastLineEnd + 1)
		{
			// The last line of the file is part of the selection so there is no
			// line beneath that the selection can be moved below.
			return
		}

		// Get the start of all lines that will be moving down.
		val lineStarts = txt.lineStartsInSelection()

		// Get the bounds of the next line that will move up above selection.
		val startNextLine = lastLineEnd + 1
		val endNextLine = txt.document.lineEndAfter(startNextLine)

		// Get the length of the next line that will be moved above the current
		// selection
		val nextLineLength = endNextLine - startNextLine

		// Calculate text of the next line that will be moved up taking into
		// account any position containing the beginning/end of file or a blank
		// line as the last line to shift down.
		val txtToMoveUp =
			when
			{
				// The lines to shift down includes the first line of the
				// document
				lineStarts[0] == 0 ->
				{
					if (txt.document.endPosition.offset == lastLineEnd + 1)
					{
						// The entire document is highlighted so there is no
						// line shift available. Do nothing.
						return
					}
					txt.document.getText(startNextLine, nextLineLength)
				}
				// The last line of the document is to move above the selection.
				endNextLine + 1 == txt.document.endPosition.offset ->
					txt.document.getText(startNextLine, nextLineLength)
				// The last line of the selection to move down in an empty line.
				lineStarts.last() == lineEnds.last() ->
					txt.document.getText(startNextLine, nextLineLength + 1)
				// No edge case; shift is fully internal to the document.
				else -> txt.document.getText(startNextLine - 1, nextLineLength)
			}

		// Calculate the adjustment to the selection for the new start and end
		// of the selection after the text is moved down.
		val shiftedSelectionStart = txt.selectionStart + txtToMoveUp.length
		val shiftedSelectionEnd = txt.selectionEnd + txtToMoveUp.length

		// Move the lines as a single undo-able transaction.
		sourcePane.transaction {
			when
			{
				// The last line is being moved above selection
				endNextLine + 1 == txt.document.endPosition.offset ->
				{
					// Remove the last '\n' of the second to last line of the
					// file text, the end of the last line to be moved down, all
					// the way to the end of the file.
					txt.document.remove(lastLineEnd, endNextLine - lastLineEnd)

					// The line shift does not include the first line of the file so
					// our insertion of the text, "txtToMoveUp", needs to be
					// inserted one position before the "lineStarts[0]".
					txt.document.insertString(
						lineStarts[0],
						txtToMoveUp + "\n",
						null)
				}
				// The first line is being shifted down
				lineStarts[0] == 0 ->
				{
					// Remove the moved text from its original position.
					txt.document.remove(
						startNextLine,
						nextLineLength + 1)

					// Our move involves moving the first line of the file down.
					// Because "lineStarts[0]" represents the start of the file, we
					// can't insert the text before the start of the file, we have
					// to insert the text at the start of the file.
					txt.document.insertString(
						0,
						txtToMoveUp + "\n",
						null)
				}
				// The last line of the selection to move down is an empty line.
				lineStarts.last() == lineEnds.last() ->
				{
					// Remove the moved text from its original position.
					txt.document.remove(startNextLine, nextLineLength + 1)

					// The line shift does not include the first line of the file so
					// our insertion of the text, "txtToMoveUp", needs to be
					// inserted one position before the "lineStarts[0]".
					txt.document.insertString(
						lineStarts[0],
						txtToMoveUp,
						null)
				}
				// Text movement is fully internal to the document.
				else ->
				{
					// Remove the moved text from its original position.
					txt.document.remove(startNextLine, nextLineLength + 1)

					// The line shift does not include the first line of the file so
					// our insertion of the text, "txtToMoveUp", needs to be
					// inserted one position before the "lineStarts[0]".
					txt.document.insertString(
						lineStarts[0] - 1,
						txtToMoveUp,
						null)
				}
			}
			// Reset the selection to highlight the originally selected text.
			txt.selectionStart = shiftedSelectionStart
			txt.selectionEnd = shiftedSelectionEnd
		}
	}
}

////////////////////////////////////////////////////////////////////////////////
//                                Change Case.                                //
////////////////////////////////////////////////////////////////////////////////

/**
 * Transform the selected text and insert the result of the transformation where
 * the selected text is in this [JTextPane].
 *
 * @param transformer
 *   A zero-argument function that transforms the selected text.
 */
fun JTextPane.transform(transformer: String.()->String)
{
	if (!canEdit)
	{
		UIManager.getLookAndFeel().provideErrorFeedback(this)
		return
	}
	val txt =this as JTextComponent
	if (txt.selectionStart == txt.selectionEnd)
	{
		// No text is selected; do nothing
		return
	}
	val textToTransform = txt.selectedText
	val startPosition = txt.selectionStart
	val transformed = textToTransform.transformer()
	transaction {
		txt.document.remove(startPosition, textToTransform.length)
		txt.document.insertString(startPosition, transformed, null)
	}
}

/**
 * Change the selection to all uppercase characters.
 */
private object ToUppercase: TextAction(UppercaseShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		(e.source as JTextPane).transform(String::uppercase)
	}
}

/**
 * Change the selection to all lowercase characters.
 */
private object ToLowercase: TextAction(LowercaseShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		(e.source as JTextPane).transform(String::lowercase)
	}
}

/**
 * Change the selection to camel case: "foo_bar" -> "fooBar".
 */
private object ToCamelCase: TextAction(CamelCaseShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		(e.source as JTextPane).transform { toCamelCase(this) }
	}
}

/**
 * Change the selection to Pascal case: "fooBar" -> "FooBar".
 */
private object ToPascalCase: TextAction(PascalCaseShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		(e.source as JTextPane).transform { toPascalCase(this) }
	}
}

/**
 * Change the selection to snake case: "fooBar" -> "foo_bar".
 */
private object ToSnakeCase: TextAction(SnakeCaseShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		(e.source as JTextPane).transform { toSnakeCase(this) }
	}
}

/**
 * Change the selection to kebab case: "fooBar" -> "foo-bar".
 */
private object ToKebabCase: TextAction(KebabCaseShortcut.actionMapKey)
{
	override fun actionPerformed(e: ActionEvent)
	{
		(e.source as JTextPane).transform { toKebabCase(this) }
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
	while (true)
	{
		// Have we hit start of file or is there a line feed before the current
		// position
		if ( i <= 0 || codePointAt(i - 1) == '\n'.code)
		{
			return i
		}
		// We haven't reached the start of the file nor have we reached a '\n'
		// so we need to keep walking backwards in the file.
		i--
	}
}

/**
 * Locate the end of the line enclosing [position].
 *
 * @param position
 *   The position from which to scan rightward in search of the end of the
 *   enclosing line.
 * @return
 *   The end of the line. This is either the end of the text or a position
 *   immediately preceding a linefeed (U+000A).
 */
fun Document.lineEndAfter(position: Int): Int
{
	var i = position
	while (i < endPosition.offset)
	{
		val c = codePointAt(i)
		if (c == '\n'.code)
		{
			// We don't want to skip past the linefeed so we can place the
			// insertion point before linefeed.
			break
		}
		i++
	}
	// `i` is now positioned either
	//   (1) at the end of the text or
	//   (2) just before the next linefeed right of `position`.
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
 * Locate the ends of all lines enclosing the active selection. If no text
 * is selected, then locate the end of the line containing the caret.
 *
 * @return
 *   The positions of the desired linefeed (U+0009) characters.
 */
fun JTextComponent.lineEndsInSelection(): List<Int>
{
	if (selectionStart == selectionEnd)
	{
		// There's no text selection, so answer the start of the line featuring
		// the caret.
		return listOf(document.lineEndAfter(selectionStart))
	}
	// Some text is selected, so find the beginnings of the enclosing
	// lines.
	val document = document
	val firstLineEnd =
		document.lineEndAfter(selectionStart)
	val lastLineEnd =
		document.lineEndAfter(selectionEnd - 1)
	val insertionPoints = mutableListOf(firstLineEnd)
	// Avoid unnecessary work if only a single line was involved.
	if (firstLineEnd == lastLineEnd) return insertionPoints
	(firstLineEnd + 1 until lastLineEnd - 2).forEach { position ->
		// The bounds are a bit tricky to avoid redundant computation for the
		// two termini.
		val c = document.codePointAt(position)
		if (c == '\n'.code)
		{
			insertionPoints.add(position + 1)
		}
	}
	insertionPoints.add(lastLineEnd)
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

/**
 * Holds Strings that are queued to be transformed into a different case
 * representation.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct a [StringCaseTransformQueue].
 *
 * @param toTransform
 *   The String be transformed to a different case strategy.
 */
internal class StringCaseTransformQueue constructor(toTransform: String)
{
	/**
	 * The Strings that are to be transformed according to the target rules.
	 */
	val transformStringQueue: List<String>

	/**
	 * The non-alphanumeric characters that are placed in between the strings in
	 * [transformStringQueue].
	 */
	val delimiters: List<String>

	init
	{
		val tsq = mutableListOf<String>()
		val d = mutableListOf<String>()
		var nextDelimiter = ""
		var nextTransformString = ""
		var collectingDelimiters = false
		toTransform.forEach { c ->
			when
			{
				c.isLetterOrDigit() || c == '_' || c == '-' ->
				{
					if (collectingDelimiters)
					{
						collectingDelimiters = false
						d.add(nextDelimiter)
						nextDelimiter = ""
					}
					nextTransformString += c
				}
				else ->
				{
					if (!collectingDelimiters)
					{
						tsq.add(nextTransformString)
						nextTransformString = ""
					}
					collectingDelimiters = true
					nextDelimiter += c
				}
			}
		}
		if (nextTransformString.isNotBlank())
		{
			tsq.add(nextTransformString)
		}
		if (nextDelimiter.isNotBlank())
		{
			d.add(nextDelimiter)
		}
		delimiters = d
		transformStringQueue = tsq
	}

	/**
	 * Transform each String in [transformStringQueue] using the provided
	 * transformer interleaving the [delimiters] between each transformed
	 * screen.
	 *
	 * @param transformer
	 *   The transformer to transform the elements in [transformStringQueue].
	 * @return
	 *   The transformed assembled string.
	 */
	fun interleave(transformer: (String) -> String): String
	{
		val tQueue = transformStringQueue.iterator()
		val dQueue = delimiters.iterator()
		return buildString {
			while (tQueue.hasNext())
			{
				val next = tQueue.next()
				if (next.isEmpty())
				{
					append(next)
				}
				else
				{
					append(transformer(next))
				}
				if (dQueue.hasNext())
				{
					append(dQueue.next())
				}
			}
		}
	}

	/**
	 * Convert the text to either camel case or pascal case depending on the
	 * input for `capFirstLetter`.
	 *
	 * @param textToTransform
	 *   The text to transform.
	 * @param capFirstLetter
	 *   `true` capitalize the first character; `false` lowercase.
	 */
	private fun toCamelPascalCase (
		textToTransform: String,
		capFirstLetter: Boolean = false
	): String =
		buildString {
			var capNextLetter = false
			var previousCapped = false
			if (capFirstLetter)
			{
				append(textToTransform[0].uppercase())
			}
			else
			{
				append(textToTransform[0].lowercase())
			}
			textToTransform.substring(1).forEach {
				if (it == '-' || it == '_')
				{
					capNextLetter = true
					previousCapped = false
				}
				else
				{
					if (capNextLetter && !previousCapped)
					{
						append(it.uppercase())
						capNextLetter = false
						previousCapped = true
					}
					else
					{
						when
						{
							it.isUpperCase() && previousCapped ->
							{
								append(it.lowercase())
								capNextLetter = false
							}
							it.isUpperCase() ->
							{
								append(it)
								previousCapped = true
							}
							capNextLetter ->
							{
								append(it.uppercase())
								capNextLetter = false
								previousCapped = true
							}
							else ->
							{
								append(it)
								previousCapped = false
							}
						}
					}
				}

			}
		}


	/**
	 * Convert the text to either snake case or kebab case depending on the
	 * input for `delimiter`.
	 *
	 * @param textToTransform
	 *   The text to transform.
	 * @param delimiter
	 *   The snake underscore character or the kebab hyphen character.
	 */
	private fun toSnakeKebabCase (
		textToTransform: String,
		delimiter: String
	): String =
		buildString {
			var previousIsDelimiter = false
			append(textToTransform[0].lowercase())
			textToTransform.substring(1).forEach {
				previousIsDelimiter = if (it == '-' || it == '_')
				{
					append(delimiter)
					true
				}
				else
				{
					if (it.isUpperCase() && !previousIsDelimiter)
					{
						append(delimiter)
					}
					append(it.lowercase())
					false
				}
			}
		}

	/**
	 * Transform the following text to camel case.
	 *
	 * @param textToTransform
	 *   The text to transform to camel case.
	 * @return
	 *   The transformed text.
	 */
	private fun toCamelCase(textToTransform: String) =
		toCamelPascalCase(textToTransform)

	/**
	 * Transform the following text to pascal case.
	 *
	 * @param textToTransform
	 *   The text to transform to pascal case.
	 * @return
	 *   The transformed text.
	 */
	private fun toPascalCase(textToTransform: String) =
		toCamelPascalCase(textToTransform, true)

	/**
	 * Transform the following text to snake case.
	 *
	 * @param textToTransform
	 *   The text to transform to snake case.
	 * @return
	 *   The transformed text.
	 */
	private fun toSnakeCase(textToTransform: String) =
		toSnakeKebabCase(textToTransform, "_")

	/**
	 * Transform the following text to snake case.
	 *
	 * @param textToTransform
	 *   The text to transform to snake case.
	 * @return
	 *   The transformed text.
	 */
	private fun toKebabCase(textToTransform: String) =
		toSnakeKebabCase(textToTransform, "-")

	/**
	 * The camel case transformation.
	 */
	val camelCase: String = interleave { toCamelCase(it) }

	/**
	 * The pascal case transformation.
	 */
	val pascalCase: String = interleave { toPascalCase(it) }

	/**
	 * The snake case transformation.
	 */
	val snakeCase: String = interleave { toSnakeCase(it) }

	/**
	 * The snake case transformation.
	 */
	val kebabCase: String = interleave { toKebabCase(it) }
}

/**
 * Transform the following text to camel case.
 *
 * @param textToTransform
 *   The text to transform to camel case.
 * @return
 *   The transformed text.
 */
fun toCamelCase(textToTransform: String) =
	StringCaseTransformQueue(textToTransform).camelCase

/**
 * Transform the following text to pascal case.
 *
 * @param textToTransform
 *   The text to transform to pascal case.
 * @return
 *   The transformed text.
 */
fun toPascalCase(textToTransform: String) =
	StringCaseTransformQueue(textToTransform).pascalCase

/**
 * Transform the following text to snake case.
 *
 * @param textToTransform
 *   The text to transform to snake case.
 * @return
 *   The transformed text.
 */
fun toSnakeCase(textToTransform: String) =
	StringCaseTransformQueue(textToTransform).snakeCase

/**
 * Transform the following text to kebab case.
 *
 * @param textToTransform
 *   The text to transform to kebab case.
 * @return
 *   The transformed text.
 */
fun toKebabCase(textToTransform: String) =
	StringCaseTransformQueue(textToTransform).kebabCase
