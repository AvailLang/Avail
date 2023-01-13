/*
 * CodePane.kt
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

import avail.anvil.AvailWorkbench
import avail.anvil.BoundStyle
import avail.anvil.StyleRegistry
import avail.anvil.SystemColors
import avail.anvil.shortcuts.CodePaneShortcut
import avail.utility.PrefixTree.Companion.payloads
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit.getDefaultToolkit
import java.awt.event.ActionEvent
import javax.swing.BorderFactory
import javax.swing.InputMap
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.event.CaretEvent
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument
import javax.swing.text.TabSet
import javax.swing.text.TabStop
import javax.swing.undo.CompoundEdit
import javax.swing.undo.UndoManager

/**
 * A [text&#32;][JTextPane] suitable for editing Avail source code. It is
 * editable by default, but may be locked down after construction.
 *
 * Currently supports:
 *
 * * Basic editing.
 * * Basic undo/redo.
 * * Template expansion, with prefix shortening and explicit single caret
 *   positioning.
 *
 * @property workbench
 *   The owning workbench.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a [CodePane].
 *
 * @param workbench
 *   The associated [AvailWorkbench].
 * @param kit
 *   The [editor&#32;kit][CodeKit].
 */
class CodePane
constructor(
	internal val workbench: AvailWorkbench,
	isEditable: Boolean = true,
	kit: CodeKit = CodeKit(workbench)
): JTextPane()
{
	/**
	 * The last recorded caret position, set upon receipt of a [CaretEvent].
	 * Used for supporting aggregate undo/redo.
	 */
	private var lastCaretPosition = Int.MIN_VALUE

	/** The current edit, for aggregate undo/redo. */
	internal var currentEdit: CompoundEdit? = null

	/**
	 * The [UndoManager] for supplying undo/redo for edits to the underlying
	 * document.
	 */
	internal val undoManager = UndoManager().apply { limit = 1000 }

	/**
	 * The state of an ongoing template selection.
	 *
	 * @property startPosition
	 *   The start position of the template expansion site within the document.
	 * @property templatePrefix
	 *   The alleged prefix of a recognized template.
	 * @property candidateExpansions
	 *   The candidate template expansions available for selection.
	 */
	data class TemplateSelectionState constructor(
		val startPosition: Int,
		val templatePrefix: String,
		val candidateExpansions: List<String>
	) {
		/** The index of the active candidate template expansion. */
		var candidateIndex: Int = 0

		/** Whether the template expansion algorithm is running. */
		var expandingTemplate: Boolean = true

		/** The current candidate. */
		val candidate get() = candidateExpansions[candidateIndex]
	}

	/**
	 * The state of an ongoing template selection. Set to `null` whenever the
	 * caret moves for any reason.
	 */
	private var templateSelectionState: TemplateSelectionState? = null

	/**
	 * Clear the [template&#32;selection&#32;state][TemplateSelectionState] iff
	 * it is stale.
	 */
	fun clearStaleTemplateSelectionState()
	{
		if (templateSelectionState?.expandingTemplate != true)
		{
			templateSelectionState = null
		}
	}

	init
	{
		editorKit = kit
		border = BorderFactory.createEtchedBorder()
		this.isEditable = isEditable
		isEnabled = true
		isFocusable = true
		preferredSize = Dimension(0, 500)
		font = Font.decode("${workbench.globalSettings.font} 13")
		font = font.deriveFont(workbench.globalSettings.codePaneFontSize)
		foreground = SystemColors.active.baseCode
		background = SystemColors.active.codeBackground
		registerStyles()
		registerKeystrokes()
		addKeyListener(JTextPaneKeyTypedAdapter(
			WrapInDoubleQuotes,
			WrapInSingleQuotes,
			WrapInDoubleSmartQuotes,
			WrapInSingleSmartQuotes,
			WrapInGuillemets,
			WrapInBackticks,
			WrapInParenthesis,
			WrapInAngleBrackets,
			WrapInBrackets,
			WrapInBraces
		))
		if (isEditable)
		{
			installUndoSupport()
			putClientProperty(CodePane::undoManager.name, undoManager)
			putClientProperty(CodePane::currentEdit.name, currentEdit)
		}
	}

	/**
	 * Change the font size to the provided font size.
	 *
	 * @param updatedSize
	 *   The new font size.
	 */
	fun changeFontSize (updatedSize: Float)
	{
		font = font.deriveFont(updatedSize)
	}

	/**
	 * Change the font to the provided font name and size.
	 *
	 * @param name
	 *   The [name][Font.name] of the [Font] to set.
	 * @param updatedSize
	 *   The size of the [Font] to set.
	 */
	fun changeFont (name: String, updatedSize: Float)
	{
		font = Font.decode(name).deriveFont(updatedSize)
	}

	/**
	 * Register all [BoundStyle]s with the underlying [StyledDocument].
	 */
	internal fun registerStyles()
	{
		val attributes = SimpleAttributeSet()
		StyleConstants.setTabSet(attributes, tabSet)
		StyleConstants.setFontFamily(attributes, "Monospaced")
		styledDocument.run {
			setParagraphAttributes(0, length, attributes, false)
			val defaultStyle = BoundStyle.defaultStyle
			defaultStyle.addAttributes(attributes)
			StyleRegistry.addAllStyles(this)
		}
	}

	/**
	 * Install undo/redo support for the underlying [StyledDocument].
	 */
	private fun installUndoSupport()
	{
		addCaretListener { e ->
			clearStaleTemplateSelectionState()
			val dot = e.dot
			val currentEdit = currentEdit
			currentEdit?.let {
				if (dot != lastCaretPosition && dot != lastCaretPosition + 1)
				{
					// If the caret's location is inconsistent with entry of a
					// single character, then end the current aggregation.
					currentEdit.end()
				}
			}
			lastCaretPosition = dot
		}
		document.addUndoableEditListener {
			var edit = currentEdit
			if (edit === null || !edit.isInProgress)
			{
				edit = CompoundEdit()
				undoManager.addEdit(edit)
				currentEdit = edit
				putClientProperty(CodePane::currentEdit.name, currentEdit)
			}
			edit.addEdit(it.edit)
		}
	}

	/**
	 * Register all [KeyStroke]s with the [InputMap].
	 */
	internal fun registerKeystrokes()
	{
		// To add a new shortcut, add it as a subtype of the sealed class
		// CodePaneShortcut.
		CodePaneShortcut::class.sealedSubclasses.forEach {
			it.objectInstance?.addToInputMap(inputMap)
		}
	}

	/**
	 * Attempt to expand the nonwhitespace text prior to the caret using one of
	 * the known template substitutions.
	 */
	internal fun expandTemplate()
	{
		val document = styledDocument
		var length: Int
		var state = templateSelectionState
		if (state === null)
		{
			// This is a brand new template expansion, so determine the
			// candidates. Scan backwards to the first character after a
			// whitespace, treating the start of the document as such a
			// character.
			val caretPosition = caretPosition
			var startPosition = run {
				// Start the search just before the caret.
				var i = caretPosition - 1
				while (i >= 0)
				{
					val c = document.getText(i, 1).codePointAt(0)
					if (Character.isWhitespace(c)) return@run i + 1
					i--
				}
				0
			}
			// Use the substring from boundary to caret as an index into the
			// prefix tree of available templates.
			length = caretPosition - startPosition
			if (length == 0)
			{
				// There are no characters in the prefix. Don't allow a random
				// walk through all possible expansions, as this has no utility.
				getDefaultToolkit().beep()
				return
			}
			lateinit var prefix: String
			lateinit var candidates: List<String>
			while (length > 0)
			{
				// Scan shorter and shorter prefixes until we find some
				// candidates, giving up only if nothing before the caret leads
				// to a template. I verified this was still real-time for 120
				// leading characters (2022.08.17).
				prefix = document.getText(startPosition, length)
				candidates = workbench.templates.payloads(prefix).flatten()
				if (candidates.isNotEmpty())
				{
					// We found some candidates, so bail on the shortening
					// search and continue with the candidates at this prefix.
					break
				}
				// Shorten the prefix from the start. This is especially helpful
				// when trying to expand templates near boundary punctuation.
				startPosition++
				length--
			}
			if (candidates.isEmpty())
			{
				// There are no candidates. Emit a beep, but don't transform any
				// text or change any internal state, as there's nothing to do.
				getDefaultToolkit().beep()
				return
			}
			templateSelectionState = TemplateSelectionState(
				startPosition,
				prefix,
				candidates
			)
			state = templateSelectionState
		}
		else
		{
			// This is an ongoing template expansion. Arrange to clear out the
			// rejected candidate, taking care to deal with caret insertion
			// characters (⁁) correctly.
			state.expandingTemplate = true
			val oldCandidate = state.candidate
			length = oldCandidate.expandedLength
			state.candidateIndex++
			// Select the next candidate, wrapping around if necessary.
			if (state.candidateIndex == state.candidateExpansions.size)
			{
				// Beep twice to alert the user that the candidate list is
				// recycling, i.e., the user has already seen and rejected every
				// candidate.
				state.candidateIndex = 0
				getDefaultToolkit().beep()
			}
		}
		// Perform the expansion.
		val candidate = state!!.candidate
		val startPosition = state.startPosition
		document.remove(startPosition, length)
		document.insertString(startPosition, candidate, null)
		// Search for the caret insertion character (⁁). If found, then position
		// the caret thereat and delete the character.
		val desiredCharacterPosition = candidate.indexOf('⁁')
		if (desiredCharacterPosition >= 0)
		{
			this.caretPosition = startPosition + desiredCharacterPosition
			document.remove(this.caretPosition, 1)
		}
		else
		{
			this.caretPosition = startPosition + candidate.length
		}
		state.expandingTemplate = false
	}
	/**
	 * Cancel an ongoing iteration through template candidates. Restore the
	 * original text.
	 */
	internal fun cancelTemplateExpansion()
	{
		val state = templateSelectionState
		if (state !== null)
		{
			val startPosition = state.startPosition
			val length = state.candidate.expandedLength
			val document = document
			document.remove(startPosition, length)
			val prefix = state.templatePrefix
			document.insertString(startPosition, prefix, null)
			// Positioning the caret is not strictly necessary, as the insertion
			// should have placed it correctly. Manually position it though just
			// to be safe.
			caretPosition = startPosition + prefix.length
			templateSelectionState = null
		}
	}

	companion object
	{
		/** The [tab&#32;set][TabSet]. */
		private val tabSet = TabSet(Array(500) { TabStop(32.0f * (it + 1)) })

		/** The length of the receiver after template expansion. */
		private val String.expandedLength get() =
			length - count { it == '⁁' }

		/** The [CodePane] that sourced the [receiver][ActionEvent]. */
		internal val ActionEvent.codePane get() = source as CodePane
	}
}
