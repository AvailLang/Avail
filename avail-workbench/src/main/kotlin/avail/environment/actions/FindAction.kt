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
import javax.swing.JTextArea
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter
import javax.swing.text.Highlighter
import javax.swing.text.Highlighter.HighlightPainter
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

	/** The non-modal Find dialog, created lazily but cached when dismissed.  */
	private var findDialog: JDialog? = null

	/** The JTextComponent on which to perform searches. */
	private var textPane: JTextComponent? = null

	/** The [textPane]'s highlighter. */
	private var highlighter: Highlighter? = null

	/**
	 * The ordered [List] of (MatchResult, highlight tag) pairs, for all places
	 * that match the current pattern.
	 */
	private val allMatches = mutableListOf<Pair<MatchResult, Any>>()

	/**
	 * When non-null, this is the index into [allMatches] representing the
	 * current match.  Find Next and Find Previous always use the current caret
	 * position or selection as the starting point for the search, but they not
	 * only alter the [HighlightPainter] used to render the current match, they
	 * also set the current selection to the same range.  Note that the
	 * selection/caret is invisible while the dialog has focus.
	 */
	private var currentMatchIndex: Int? = null

	/**
	 * The [HighlightPainter] for rendering all matches but the current one.
	 * Initialized to a dummy value.
	 */
	private var allMatchesPainter = DefaultHighlightPainter(Color.BLACK)

	/**
	 * The [HighlightPainter] for rendering the current match. Initialized to a
	 * dummy value.
	 */
	private var currentMatchPainter = DefaultHighlightPainter(Color.BLACK)

	/**
	 * A [DocumentListener] that detects textual changes to in the [textPane],
	 * triggering a recalculation of matches.
	 */
	private val documentListener = object : DocumentListener {
		override fun insertUpdate(e: DocumentEvent?) = updateHighlights()
		override fun changedUpdate(e: DocumentEvent?) = updateHighlights()
		override fun removeUpdate(e: DocumentEvent?) = updateHighlights()
	}

	/**
	 * Open or reopen the find dialog.  Connect it to the most recently focused
	 * text pane of the workbench.
	 */
	override fun actionPerformed(event: ActionEvent?)
	{
		if (findDialog === null)
		{
			createDialog()
		}
		textPane = workbench.mostRecentFocusOwner as? JTextArea ?: return
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

	/**
	 * Either the document or the search pattern has changed, or we have just
	 * made the dialog visible.  Find all matches and highlight them, preserving
	 * the current match if possible.
	 */
	private fun updateHighlights()
	{
		val pane = textPane ?: return
		val body = pane.text
		var matches = emptyList<MatchResult>()
		matchCountLabel.text = when (val text = findText.text)
		{
			"" -> ""
			else ->
				try
				{
					matches = Regex(text).findAll(body).toList()
					"${matches.size} matches"
				}
				catch (e : PatternSyntaxException)
				{
					// Report malformed regexes only when using them.
					e.message
				}
		}
		allMatches.forEach { highlighter!!.removeHighlight(it.second) }
		currentMatchIndex = null
		allMatches.clear()
		matches.mapTo(allMatches) { match ->
			match to highlighter!!.addHighlight(
				match.range.first, match.range.last + 1, allMatchesPainter)
		}
	}

	/**
	 * Starting at the caret or selection, find the next match, making it
	 * current *and* changing the text pane's selection to correspond.
	 */
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

	/**
	 * Starting at the caret or selection, find the textually previous match,
	 * making it current *and* changing the text pane's selection to correspond.
	 */
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

	/**
	 * If there is a current match, replace it using the regex replacement
	 * pattern, then advance to the next match.
	 */
	private val replaceCurrentAction = object : TextAction("Replace")
	{
		override fun actionPerformed(e: ActionEvent)
		{
			//TODO
			textPane?.toolkit?.beep()
		}
	}

	/**
	 * Given the already computed list of matches, perform the replacement for
	 * each one.  The change to the document will trigger [updateHighlights] to
	 * find any new matches after this batch of substitutions.
	 */
	private val replaceAllAction = object : TextAction("Replace All")
	{
		override fun actionPerformed(e: ActionEvent)
		{
			//TODO
			textPane?.toolkit?.beep()
		}
	}

	/**
	 * Hide the (non-modal) find dialog, removing all highlighting.
	 */
	private val closeAction = object : TextAction("Close")
	{
		override fun actionPerformed(e: ActionEvent)
		{
			findDialog?.isVisible = false
			dialogWasClosed()
		}
	}

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
	private val replaceNextButton = JButton(replaceCurrentAction)
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
			minimumSize = Dimension(300, 200)
			preferredSize = Dimension(600, 200)
			contentPane.add(panel)
			isResizable = true
			pack()
			val topLeft = workbench.location
			setLocation(
				topLeft.x + workbench.width - width - 100, topLeft.y + 30)
			addWindowListener(object : WindowAdapter()
			{
				override fun windowClosing(e: WindowEvent) {
					//isVisible = false
					dialogWasClosed()
				}
			})
		}
	}

	/**
	 * The dialog has been closed, but may be reopened later.  Hide all
	 * highlights.
	 */
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
