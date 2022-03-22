/*
 * FindAction.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.environment.actions

import avail.environment.AvailWorkbench
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.regex.PatternSyntaxException
import javax.swing.Action
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter
import javax.swing.text.Highlighter
import javax.swing.text.JTextComponent
import javax.swing.text.TextAction

/**
 * A [FindAction] presents the Find/Replace window for the supplied [JTextPane].
 *
 * @constructor
 * Construct a new [FindAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class FindAction constructor(
	workbench: AvailWorkbench
) : AbstractWorkbenchAction(workbench, "Find/Replace…")
{
	init
	{
		putValue(Action.SHORT_DESCRIPTION, "Find/Replace…")
		putValue(
			Action.ACCELERATOR_KEY,
			KeyStroke.getKeyStroke(
				KeyEvent.VK_F, AvailWorkbench.menuShortcutMask))
	}

	private var findDialog: JDialog? = null

	private var textPane: JTextComponent? = null

	private var highlighter: Highlighter? = null

	private val allMatches = mutableListOf<Pair<MatchResult, Any>>()

	private var currentMatchIndex: Int? = null

	// Replaced by a blend of selection color and background.
	private var allMatchesPainter = DefaultHighlightPainter(Color.YELLOW)

	// Replaced by the selection color.
	private var currentMatchPainter = DefaultHighlightPainter(Color.RED)

	private val documentListener = object : DocumentListener {
		override fun insertUpdate(e: DocumentEvent?) = updateHighlights()
		override fun changedUpdate(e: DocumentEvent?) = updateHighlights()
		override fun removeUpdate(e: DocumentEvent?) = updateHighlights()
	}

	/** Open or reopen the find dialog. */
	override fun actionPerformed(event: ActionEvent?)
	{
		if (findDialog === null)
		{
			createDialog()
		}
		textPane = workbench.mostRecentFocusOwner as? JTextComponent ?: return
		highlighter = textPane!!.highlighter
		textPane!!.document.addDocumentListener(documentListener)
		val selectionColor = textPane!!.selectionColor
		val currentMatchColor = AvailWorkbench.AdaptiveColor(
			selectionColor.darker(), selectionColor.brighter())
		allMatchesPainter = DefaultHighlightPainter(selectionColor)
		currentMatchPainter = DefaultHighlightPainter(currentMatchColor.color)
		updateHighlights()
		findDialog!!.isVisible = true
	}

	private fun updateHighlights()
	{
		val pane = textPane ?: return
		val body = pane.text
		val matches = try
		{
			when (val text = findText.text)
			{
				"" -> emptyList()
				else -> Regex(text).findAll(body).toList()
			}
		}
		catch (e : PatternSyntaxException)
		{
			// Report malformed regexes only when using them.
			emptyList()
		}
		matchCountLabel.text = when (val count = matches.size)
		{
			0 -> "No matches"
			else -> "$count matches"
		}
		allMatches.forEach { highlighter!!.removeHighlight(it.second) }
		currentMatchIndex = null
		allMatches.clear()
		matches.mapTo(allMatches) { match ->
			match to highlighter!!.addHighlight(
				match.range.first, match.range.last + 1, allMatchesPainter)
		}
	}

	// Button actions.

	private val findNextAction = object : TextAction("Find Next")
	{
		override fun actionPerformed(e: ActionEvent)
		{
			val pane = textPane ?: return
			val selectionEnd = pane.selectionEnd
			// TODO Not efficient, but good enough for now.
			allMatches.forEachIndexed { i, (match, tag) ->
				if (match.range.first >= selectionEnd)
				{
					pane.select(match.range.first, match.range.last + 1)
					currentMatchIndex?.let { j ->
						// Remove the current match highlighting, replacing it
						// with the highlighting for all matches.
						val (m, t) = allMatches[j]
						highlighter!!.removeHighlight(t)
						val newTag = highlighter!!.addHighlight(
							m.range.first, m.range.last + 1,
							allMatchesPainter)
						allMatches[j] = m to newTag
					}
					// Remove the all-matches highlight for this match, and
					// add a replacement current-match highlight.
					currentMatchIndex = i
					highlighter!!.removeHighlight(tag)
					val newTag = highlighter!!.addHighlight(
						match.range.first, match.range.last + 1,
						currentMatchPainter)
					allMatches[i] = match to newTag
					return
				}
			}
			// Indicate there are no more matches.
			pane.toolkit.beep()
		}
	}

	private val findPreviousAction = object : TextAction("Find Previous")
	{
		override fun actionPerformed(e: ActionEvent)
		{
			val pane = textPane ?: return
			val selectionStart = pane.selectionStart
			// TODO Not efficient, but good enough for now.
			allMatches.withIndex().reversed().forEach { (i, pair) ->
				val (match, tag) = pair
				if (match.range.last + 1 <= selectionStart)
				{
					pane.select(match.range.first, match.range.last + 1)
					currentMatchIndex?.let { j ->
						// Remove the current match highlighting, replacing it with the
						// highlighting for all matches.
						val (m, t) = allMatches[j]
						highlighter!!.removeHighlight(t)
						val newTag = highlighter!!.addHighlight(
							m.range.first, m.range.last + 1,
							allMatchesPainter)
						allMatches[j] = m to newTag
					}
					// Remove the all-matches highlight for this match, and
					// add a replacement current-match highlight.
					currentMatchIndex = i
					highlighter!!.removeHighlight(tag)
					val newTag = highlighter!!.addHighlight(
						match.range.first, match.range.last + 1,
						currentMatchPainter)
					allMatches[i] = match to newTag
					return
				}
			}
			// Indicate there are no more matches.
			pane.toolkit.beep()
		}
	}

	private val replaceNextAction = object : TextAction("Replace Next")
	{
		override fun actionPerformed(e: ActionEvent)
		{
			//TODO
			textPane?.toolkit?.beep()
		}
	}

	private val replaceAllAction = object : TextAction("Replace All")
	{
		override fun actionPerformed(e: ActionEvent)
		{
			//TODO
			textPane?.toolkit?.beep()
		}
	}

	private val closeAction = object : TextAction("Close")
	{
		override fun actionPerformed(e: ActionEvent)
		{
			findDialog?.isVisible = false
			dialogWasClosed()
		}
	}

	// The controls.
	private val findLabel = JLabel("Find (regex):")
	private val replaceLabel = JLabel("Replace:")
	private val findText = JTextPane().apply {
		document.addDocumentListener(object : DocumentListener {
			override fun insertUpdate(e: DocumentEvent) = updateHighlights()
			override fun changedUpdate(e: DocumentEvent) = updateHighlights()
			override fun removeUpdate(e: DocumentEvent) = updateHighlights()
		})
	}
	private val replaceText = JTextPane()
	private val matchCountLabel = JLabel("")
	private val findNextButton = JButton(findNextAction)
	private val findPreviousButton = JButton(findPreviousAction)
	private val replaceNextButton = JButton(replaceNextAction)
	private val replaceAllButton = JButton(replaceAllAction)
	private val closeButton = JButton(closeAction)

	/**
	 * Actually show the Find dialog.  This is provided separately from the
	 * usual [ActionListener.actionPerformed] mechanism so that we can invoke it
	 * directly whenever we want, without having to synthesize an [ActionEvent].
	 */
	private fun createDialog()
	{
		val panel = JPanel(BorderLayout(20, 20))
		panel.border = EmptyBorder(10, 10, 10, 10)

		panel.run {
			add(findLabel)
			add(replaceLabel)
			add(findText)
			add(replaceText)
			add(matchCountLabel)
			add(findNextButton)
			add(findPreviousButton)
			add(replaceNextButton)
			add(replaceAllButton)
			add(closeButton)
			// Pressing escape in the find dialog should close it.
			actionMap.put("Close", closeAction)
			getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).run {
				put(KeyStroke.getKeyStroke("ESCAPE"), "Close")
			}
			// TODO Add "replace" functionality
			replaceText.isEnabled = false
			replaceNextButton.isEnabled = false
			replaceAllButton.isEnabled = false
		}

		panel.layout = GroupLayout(panel).apply {
			autoCreateGaps = true
			setHorizontalGroup(
				createParallelGroup()
					.addGroup(createSequentialGroup()
						.addGroup(createParallelGroup(Alignment.LEADING)
							.addComponent(findLabel)
							.addComponent(replaceLabel))
						.addGroup(createParallelGroup(Alignment.LEADING)
							.addComponent(findText)
							.addComponent(replaceText)))
					.addGroup(createParallelGroup(Alignment.LEADING)
						.addComponent(matchCountLabel))
					.addGroup(createSequentialGroup()
						.addComponent(findNextButton)
						.addComponent(findPreviousButton)
						.addComponent(replaceNextButton)
						.addComponent(replaceAllButton)
						.addComponent(closeButton)))
			setVerticalGroup(
				createSequentialGroup()
					.addGroup(createParallelGroup()
						.addComponent(findLabel)
						.addComponent(findText))
					.addComponent(matchCountLabel)
					.addGroup(createParallelGroup()
						.addComponent(replaceLabel)
						.addComponent(replaceText))
					.addGroup(createParallelGroup()
						.addComponent(findNextButton)
						.addComponent(findPreviousButton)
						.addComponent(replaceNextButton)
						.addComponent(replaceAllButton)
						.addComponent(closeButton)))
			linkSize(
				SwingConstants.HORIZONTAL,
				findNextButton,
				findPreviousButton,
				replaceNextButton,
				replaceAllButton,
				closeButton)
		}
		findDialog = JDialog(workbench, name(), ModalityType.MODELESS)
		findDialog!!.run {
			minimumSize = Dimension(200, 200)
			preferredSize = Dimension(600, 200)
			contentPane.add(panel)
			isResizable = true
			pack()
			val topLeft = workbench.location
			setLocation(topLeft.x + workbench.width - width - 60, topLeft.y + 22)
			addWindowListener(object : WindowAdapter()
			{
				override fun windowClosing(e: WindowEvent) {
					isVisible = false
					dialogWasClosed()
				}
			})
		}
	}

	private fun dialogWasClosed()
	{
		allMatches.forEach { highlighter?.removeHighlight(it.second) }
		allMatches.clear()
		currentMatchIndex = null
		textPane?.document?.removeDocumentListener(documentListener)
		textPane = null
		highlighter = null
	}
}
