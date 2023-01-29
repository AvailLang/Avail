/*
 * AvailEditor.kt
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

package avail.anvil

import avail.anvil.MenuBarBuilder.Companion.createMenuBar
import avail.anvil.actions.FindAction
import avail.anvil.shortcuts.KeyboardShortcut
import avail.anvil.text.CodePane
import avail.anvil.text.MarkToDotRange
import avail.anvil.text.markToDotRange
import avail.anvil.window.LayoutConfiguration
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.WindowListener
import java.io.File
import java.util.TimerTask
import javax.swing.GroupLayout
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Caret
import javax.swing.text.StyleConstants
import kotlin.math.max

/**
 * An editor for an arbitrary source code.
 *
 * @author Richard Arriaga
 *
 * @property fileLocation
 *   The absolute path of the source code file.
 *
 * @constructor
 * Construct an [CodeEditor].
 *
 * @param workbench
 *   The active [AvailWorkbench].
 * @param fileLocation
 *   The absolute path of the source code file.
 * @param frameTitle
 *   The [JFrame.title].
 */
abstract class CodeEditor<CE> constructor(
	final override val workbench: AvailWorkbench,
	protected val fileLocation: String,
	frameTitle: String
) : WorkbenchFrame(frameTitle)
{
	/**
	 * Whether to auto save the backing file to disk after changes.
	 */
	open val autoSave: Boolean = true

	/**
	 * When the first edit was after a save, or the first ever.
	 * Only access within the Swing UI thread.
	 */
	private var firstUnsavedEditTime = 0L

	/**
	 * The most recent edit time.
	 * Only access within the Swing UI thread.
	 */
	private var lastEditTime = 0L

	/**
	 * The last time the module was saved.
	 * Only access within the Swing UI thread.
	 */
	private var lastSaveTime = 0L

	/**
	 * The list of [KeyboardShortcut] specific to this [CodeEditor] type.
	 */
	protected abstract val shortcuts: List<KeyboardShortcut>

	override val layoutConfiguration: LayoutConfiguration =
		LayoutConfiguration.initialConfiguration

	/**
	 * The [MarkToDotRange] of the [Caret] in the [sourcePane].
	 */
	internal var range: MarkToDotRange
		private set

	/**
	 * The [JLabel] that displays the [range]
	 */
	private val caretRangeLabel = JLabel()

	/** The editor pane. */
	internal val sourcePane = CodePane(workbench).apply {
		addCaretListener {
			val doc = styledDocument
			range = markToDotRange()
			val dot = range.dotPosition.offset
			val element = doc.getCharacterElement(dot)
			var styleName = element.attributes.getAttribute(
				StyleConstants.NameAttribute)
			if (styleName == "default")
			{
				// There's nothing interesting to the right, so look to the left
				// for a style name to present in the caretRangeLabel.
				val leftElement = doc.getCharacterElement(max(dot - 1, 0))
				styleName = leftElement.attributes.getAttribute(
					StyleConstants.NameAttribute)
			}
			caretRangeLabel.text = "$styleName $range"
		}

		document.addDocumentListener(object : DocumentListener
		{
			override fun insertUpdate(e: DocumentEvent) = editorChanged()
			override fun changedUpdate(e: DocumentEvent) = editorChanged()
			override fun removeUpdate(e: DocumentEvent) = editorChanged()
		})
		text = File(fileLocation).readText()
	}

	/**
	 * Refresh the [KeyboardShortcut]s for this [CodeEditor].
	 */
	fun refreshShortcuts ()
	{
		sourcePane.inputMap.clear()
		// To add a new shortcut, add it as a subtype of the sealed class
		// AvailEditorShortcut.
		shortcuts.forEach {
			it.addToInputMap(sourcePane.inputMap)
		}
		sourcePane.registerKeystrokes()
		SwingUtilities.invokeLater {
			sourcePane.revalidate()
		}
	}

	/** The scroll wrapper around the [sourcePane]. */
	private val sourcePaneScroll = sourcePane.scrollTextWithLineNumbers(
		workbench, workbench.globalSettings.editorGuideLines)

	/**
	 * Populate the [source&#32;pane][sourcePane].
	 *
	 * @param then
	 *   Action to perform after population and then highlighting are complete.
	 */
	internal abstract fun populateSourcePane(then: (CE) -> Unit = {})

	/**
	 * The [code&#32;guide][CodeGuide] for the [source&#32;pane][sourcePane].
	 */
	private val codeGuide get() = sourcePane.getClientProperty(
		CodeGuide::class.java.name) as CodeGuide

	/**
	 * Apply style highlighting to the text in the
	 * [source&#32;pane][sourcePane].
	 */
	internal open fun highlightCode()
	{
		val stylesheet = workbench.stylesheet
		sourcePane.background = sourcePane.computeBackground(stylesheet)
		sourcePane.foreground = sourcePane.computeForeground(stylesheet)
		codeGuide.guideColor = codeGuide.computeColor()
	}

	/**
	 * The editor has indicated that the module has just been edited.
	 * Only call within the Swing UI thread.
	 */
	private fun editorChanged()
	{
		val editTime = lastEditTime
		lastEditTime = System.currentTimeMillis()
		if (editTime <= lastSaveTime)
		{
			// This is the first change since the latest save.
			firstUnsavedEditTime = lastEditTime
			if (autoSave)
			{
				eventuallySave()
			}
		}
	}

	/**
	 * Cause the modified module to be written to disk soon.
	 * Only call within the Swing UI thread.
	 */
	private fun eventuallySave()
	{
		if (!autoSave) return
		val maximumStaleness = 10_000L  //ms
		val idleBeforeWrite = 200L  //ms
		workbench.runtime.timer.schedule(
			object : TimerTask() {
				override fun run()
				{
					SwingUtilities.invokeLater {
						// Allow forced saves to interoperate with timed saves.
						if (lastEditTime < lastSaveTime) return@invokeLater
						val now = System.currentTimeMillis()
						when
						{
							// Too long has passed, force a write.
							now - firstUnsavedEditTime > maximumStaleness ->
								forceWrite()
							// It's been a little while since the last change,
							// so write it.
							now - lastEditTime > idleBeforeWrite -> forceWrite()
							// Otherwise, postpone some more.
							else -> eventuallySave()
						}
					}
				}
			},
			idleBeforeWrite)
	}

	/**
	 * Write the modified module to disk immediately.
	 * Only call within the Swing UI thread.
	 */
	protected open fun forceWrite()
	{
		File(fileLocation).writeText(sourcePane.text)
		lastSaveTime = System.currentTimeMillis()
	}

	/** Open the editor window. */
	init
	{
		range = sourcePane.markToDotRange()
		caretRangeLabel.text = range.toString()
		jMenuBar = createMenuBar {
			menu("Edit")
			{
				item(FindAction(workbench, this@CodeEditor))
			}
			addWindowMenu(this@CodeEditor)
		}
		setLocationRelativeTo(workbench)
	}

	/**
	 * Finalize the initialization of this [CodeEditor].
	 *
	 * @param afterTextLoaded
	 *   Action that accepts this [CodeEditor] to perform after text has been
	 *   loaded to [sourcePane].
	 */
	protected fun finalizeInitialization (afterTextLoaded: (CE)->Unit = {})
	{
		val panel = JPanel(BorderLayout(20, 20))
		panel.border = EmptyBorder(10, 10, 10, 10)
		background = panel.background
		populateSourcePane(afterTextLoaded)
		range = sourcePane.markToDotRange()
		caretRangeLabel.text = range.toString()
		sourcePane.undoManager.discardAllEdits()

		panel.layout = GroupLayout(panel).apply {
			autoCreateGaps = true
			setHorizontalGroup(
				createParallelGroup()
					.addComponent(sourcePaneScroll)
					.addComponent(
						caretRangeLabel,
						GroupLayout.Alignment.TRAILING))
			setVerticalGroup(
				createSequentialGroup()
					.addComponent(sourcePaneScroll)
					.addComponent(caretRangeLabel))
		}
		minimumSize = Dimension(650, 350)
		preferredSize = Dimension(800, 1000)
		add(panel)
		pack()
		isVisible = true
	}

	////////////////////////////////////////////////////////////////////////////
	// These final overrides prevents the warning,                            //
	//   "Calling non-final function setLocationRelativeTo in constructor".   //
	////////////////////////////////////////////////////////////////////////////
	final override fun addWindowListener(l: WindowListener?)
	{
		super.addWindowListener(l)
	}

	final override fun setLocationRelativeTo(c: Component?)
	{
		super.setLocationRelativeTo(c)
	}

	final override fun add(comp: Component?): Component
	{
		return super.add(comp)
	}

	final override fun pack()
	{
		super.pack()
	}
}
