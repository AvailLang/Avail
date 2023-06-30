/*
 * FindAction.kt
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

package avail.anvil.actions

import avail.anvil.AvailWorkbench
import avail.anvil.GlowHighlightPainter
import avail.anvil.shortcuts.FindActionShortcut
import avail.anvil.showTextRange
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.regex.PatternSyntaxException
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
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
	workbench: AvailWorkbench,
	val frame: JFrame
) : AbstractWorkbenchAction(
	workbench,
	"Find/Replace…",
	FindActionShortcut)
{
	// Do nothing
	override fun updateIsEnabled(busy: Boolean) {}

	init
	{
		putValue(SHORT_DESCRIPTION, "Find/Replace…")
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
	private val allMatches = mutableListOf<Pair<MatchResult, List<Any>>>()

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
	override fun actionPerformed(event: ActionEvent)
	{
		if (findDialog === null)
		{
			createDialog()
		}
		textPane = frame.mostRecentFocusOwner as? JTextComponent ?: return
		highlighter = textPane!!.highlighter
		textPane!!.document.addDocumentListener(documentListener)
		updateHighlights()
		findDialog!!.isVisible = true
		findText.requestFocusInWindow()
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
		allMatches.forEach { (_, tags) ->
			tags.forEach { tag ->
				highlighter!!.removeHighlight(tag)
			}
		}
		allMatches.clear()
		currentMatchIndex = null
		matches.forEach { match ->
			val tags = addHighlight(match.range, false)
			allMatches.add(match to tags)
		}
	}

	/**
	 * Given a range and an indication if this is the current find range (versus
	 * all the other find ranges in the document), add highlights for the first
	 * character, the middle region if any, and the last character of that
	 * range.  A size-one range acts as both a first and last character, with no
	 * middle part.  This simplifies rendering of a box highlight, since the
	 * highlight mechanism is executed for each styled run separately, with no
	 * easy way to tell if the left or right walls of the box should be drawn.
	 *
	 * Answer the list of objects that Swing produced to represent the highlight
	 * areas.
	 */
	private fun addHighlight(
		range: IntRange,
		isCurrent: Boolean
	): List<Any>
	{
		val tags = mutableListOf<Any>()
		val size = range.last + 1 - range.first
		if (size == 1)
		{
			// Size one region.
			tags.add(
				highlighter!!.addHighlight(
					range.first,
					range.last + 1,
					selectPainter(isCurrent, true, true)))
		}
		else if (size >= 2)
		{
			// Left character, middle region if any, right character.
			tags.add(
				highlighter!!.addHighlight(
					range.first,
					range.first + 1,
					selectPainter(isCurrent, true, false)))
			if (size >= 3)
			{
				tags.add(
					highlighter!!.addHighlight(
						range.first + 1,
						range.last,
						selectPainter(isCurrent, false, false)))
			}
			tags.add(
				highlighter!!.addHighlight(
					range.last,
					range.last + 1,
					selectPainter(isCurrent, false, true)))
		}
		return tags
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
			allMatches.forEachIndexed { i, pair ->
				if (pair.first.range.first >= selectionEnd)
				{
					selectMatch(i)
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
				if (pair.first.range.last + 1 <= selectionStart)
				{
					selectMatch(i)
					return
				}
			}
			// Indicate there are no more matches.
			pane.toolkit.beep()
		}
	}

	private fun selectMatch(matchIndex: Int)
	{
		val (match, tags) = allMatches[matchIndex]
		val pane = textPane ?: return
		pane.select(match.range.first, match.range.last + 1)
		currentMatchIndex?.let { j ->
			// Remove the current match highlighting, replacing it with the
			// highlighting for all matches.
			val (m, oldTags) = allMatches[j]
			oldTags.forEach { oldTag ->
				highlighter!!.removeHighlight(oldTag)
			}
			allMatches[j] = m to addHighlight(m.range, false)
		}
		// Remove the all-matches highlight for this match, and
		// add a replacement current-match highlight.
		currentMatchIndex = matchIndex
		tags.forEach { newTag -> highlighter!!.removeHighlight(newTag) }
		allMatches[matchIndex] = match to addHighlight(match.range, true)
		pane.showTextRange(match.range.first, match.range.last + 1)
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
		panel.run {
			border = EmptyBorder(10, 10, 10, 10)
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
			val closeName = "Close"
			actionMap.put(closeName, closeAction)
			getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).run {
				put(KeyStroke.getKeyStroke("ESCAPE"), closeName)
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
			//linkSize(
			//	SwingConstants.HORIZONTAL,
			//	findNextButton,
			//	findPreviousButton,
			//	replaceNextButton,
			//	replaceAllButton,
			//	closeButton)
		}
		findDialog = JDialog(frame, name(), ModalityType.MODELESS)
		findDialog!!.run {
			minimumSize = Dimension(400, 200)
			preferredSize = Dimension(600, 200)
			contentPane.add(panel)
			isResizable = true
			pack()
			val topLeft = workbench.location
			setLocation(
				topLeft.x + workbench.width - width - 100, topLeft.y + 30)
			defaultCloseOperation = DO_NOTHING_ON_CLOSE
			addWindowListener(object : WindowAdapter()
			{
				override fun windowClosing(e: WindowEvent) {
					dialogWasClosed()
					dispose()
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
		allMatches.forEach { (_, tags) ->
			tags.forEach { tag -> highlighter?.removeHighlight(tag) }
		}
		allMatches.clear()
		currentMatchIndex = null
		textPane?.document?.removeDocumentListener(documentListener)
		textPane = null
		highlighter = null
	}

	companion object
	{
		private val currentMatchGlows = arrayOf(
			Color(255, 255, 0, 192),
			Color(255, 255, 0, 160),
			Color(255, 255, 0, 100),
			Color(255, 255, 0, 60),
			Color(255, 255, 0, 40))

		private val otherMatchGlows = arrayOf(
			Color(255, 255, 0, 96),
			Color(255, 255, 0, 80),
			Color(255, 255, 0, 64),
			Color(255, 255, 0, 32))

		/** The highlighters used by [selectPainter]. */
		private val allMatchesPainters =
			(0..7).map { i ->
				GlowHighlightPainter(
					if (i.and(4) != 0) currentMatchGlows else otherMatchGlows,
					i.and(2) != 0,
					i.and(1) != 0)
			}

		/**
		 * Answer the [HighlightPainter] for rendering a piece of a match.
		 *
		 * Use this function to get the highlighter to apply for a span that starts
		 * or ends with the first or last character of the range.  This simplifies
		 * the rules for drawing the box outlines.
		 *
		 * Note that since highlighting ranges are drawn piecemeal by Swing, based
		 * on the current style ranges, any start or end ranges should be one
		 * character wide, with the middle filled with the
		 * `selectPainter(isCurrent, false, false)` highlighter. If the whole range
		 * is size one, use the `selectPainter(isCurrent, true, true)` highlighter.
		 *
		 * @param isCurrent
		 *   Whether the highlight painter should be for the current find selection.
		 * @param isStart
		 *   Whether this is a highlight region that's the start of a match.
		 * @param isEnd
		 *   Whether this is a highlight region that's the end of a match.
		 */
		private fun selectPainter(
			isCurrent: Boolean,
			isStart: Boolean,
			isEnd: Boolean
		) = allMatchesPainters[
			(if (isCurrent) 4 else 0)
				+ (if (isStart) 2 else 0)
				+ (if (isEnd) 1 else 0)]
	}
}
