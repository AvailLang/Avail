/*
 * AvailEditor.kt
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

package avail.environment

import avail.builder.ModuleName
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Point2D
import java.util.TimerTask
import java.util.concurrent.Semaphore
import javax.swing.GroupLayout
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.BadLocationException
import kotlin.math.max
import kotlin.math.min

class AvailEditor
constructor(
	val workbench: AvailWorkbench,
	moduleName: ModuleName
) : JFrame("Avail Editor: $moduleName")
{
	private val runtime = workbench.runtime

	private var firstUnsavedEditTime = 0L

	private var lastEditTime = 0L

	private var lastSaveTime = 0L

	private val resolverReference =
		runtime.moduleNameResolver.resolve(moduleName).resolverReference

	private val sourcePane = JTextArea().apply {
		lineWrap = false
		tabSize = 2
		val semaphore = Semaphore(0)
		resolverReference.readFileString(
			true,
			{ string, _ ->
				text = string
				semaphore.release()
			},
			{
				code, throwable ->
				text = "Error reading module: $throwable, code=$code"
				semaphore.release()
			})
		semaphore.acquire()
		isEditable = resolverReference.resolver.canSave
		document.addDocumentListener(object : DocumentListener {
			override fun insertUpdate(e: DocumentEvent) = editorChanged()
			override fun changedUpdate(e: DocumentEvent) = editorChanged()
			override fun removeUpdate(e: DocumentEvent) = editorChanged()
		})
	}

	private fun editorChanged()
	{
		val editTime = lastEditTime
		lastEditTime = System.currentTimeMillis()
		if (editTime <= lastSaveTime)
		{
			// This is the first change since the latest save.
			firstUnsavedEditTime = lastEditTime
			eventuallySave()
		}
	}

	private fun eventuallySave()
	{
		val maximumStaleness = 10_000L  //ms
		val idleBeforeWrite = 200L  //ms
		runtime.timer.schedule(
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

	private fun forceWrite()
	{
		val string = sourcePane.text
		val semaphore = Semaphore(0)
		var throwable: Throwable? = null
		resolverReference.resolver.saveFile(
			resolverReference,
			string.toByteArray(),
			{ semaphore.release() },
			{ _, t ->
				throwable = t
				semaphore.release()
			})
		semaphore.acquire()
		lastSaveTime = System.currentTimeMillis()
		throwable?.let { throw it }
	}

	fun open()
	{
		addWindowListener(object : WindowAdapter()
		{
			override fun windowClosing(e: WindowEvent) {
				if (lastSaveTime < lastEditTime) forceWrite()
				workbench.openEditors.remove(resolverReference.moduleName)
			}
		})
		val panel = JPanel(BorderLayout(20, 20))
		panel.border = EmptyBorder(10, 10, 10, 10)
		background = panel.background
		panel.layout = GroupLayout(panel).apply {
			//val pref = GroupLayout.PREFERRED_SIZE
			//val def = DEFAULT_SIZE
			//val max = Int.MAX_VALUE
			autoCreateGaps = true
			setHorizontalGroup(
				createSequentialGroup()
					.addComponent(
						scrollTextWithLineNumbers(sourcePane)))
			setVerticalGroup(
				createSequentialGroup()
					.addComponent(
						scrollTextWithLineNumbers(sourcePane)))
		}
		minimumSize = Dimension(550, 350)
		preferredSize = Dimension(800, 1000)
		add(panel)
		pack()
		(scrollTextWithLineNumbers(sourcePane).rowHeader.view
			as LineNumberComponent
		).adjustWidth()
		isVisible = true
	}

	companion object {
		/**
		 * Either places the given JTextArea inside a JScrollPane with line
		 * numbers presented as row headers, or answers the JScrollPane that
		 * it's already inside.
		 */
		private fun scrollTextWithLineNumbers(textArea: JTextArea): JScrollPane
		{
			textArea.parent?.parent?.let { return it as JScrollPane }
			val lineNumberModel = object : LineNumberModel {
				override val numberOfLines: Int get() = textArea.lineCount

				override fun lineToRectangle(line: Int): Rectangle = try
				{
					val rectangle2D = textArea.modelToView2D(
						textArea.getLineStartOffset(line))
					Rectangle(
						rectangle2D.x.toInt(),
						rectangle2D.y.toInt(),
						rectangle2D.width.toInt(),
						rectangle2D.height.toInt())
				}
				catch (e: BadLocationException)
				{
					Rectangle()
				}

				override fun visibleLines(): IntRange
				{
					val rectangle = textArea.visibleRect
					val startOffset = textArea.viewToModel2D(
						Point2D.Double(rectangle.minX, rectangle.minY))
					val firstLine =
						max(textArea.getLineOfOffset(startOffset) - 1, 0)
					val endOffset = textArea.viewToModel2D(
						Point2D.Double(rectangle.maxX, rectangle.maxY))
					val lastLine =
						min(
							textArea.getLineOfOffset(endOffset) + 1,
							textArea.lineCount - 1)
					return firstLine..lastLine
				}
			}
			val lineNumberComponent = LineNumberComponent(lineNumberModel)
			val scroller = JScrollPane(textArea)
			scroller.setRowHeaderView(lineNumberComponent)
			textArea.document.addDocumentListener(
				object : DocumentListener
				{
					override fun changedUpdate(arg0: DocumentEvent) =
						lineNumberComponent.adjustWidth()

					override fun insertUpdate(arg0: DocumentEvent) =
						lineNumberComponent.adjustWidth()

					override fun removeUpdate(arg0: DocumentEvent) =
						lineNumberComponent.adjustWidth()
				})
			return scroller
		}
	}

}

