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

package avail.environment

import avail.AvailRuntime
import avail.builder.ModuleName
import avail.builder.ResolvedModuleName
import avail.descriptor.module.A_Module
import avail.environment.MenuBarBuilder.Companion.createMenuBar
import avail.environment.actions.FindAction
import avail.environment.editor.AbstractEditorAction
import avail.persistence.cache.Repository
import avail.persistence.cache.Repository.ModuleCompilation
import avail.persistence.cache.Repository.ModuleVersion
import avail.persistence.cache.Repository.ModuleVersionKey
import avail.persistence.cache.Repository.StylingRecord
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.TimerTask
import java.util.concurrent.Semaphore
import javax.swing.GroupLayout
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.undo.CannotRedoException
import javax.swing.undo.CannotUndoException
import javax.swing.undo.UndoManager

class AvailEditor
constructor(
	val workbench: AvailWorkbench,
	moduleName: ModuleName
) : JFrame("Avail Editor: $moduleName")
{
	/** The current [AvailRuntime]. */
	private val runtime = workbench.runtime

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

	/** The [resolved][ResolvedModuleName] [module&#32;name][ModuleName]. */
	private val resolvedName = runtime.moduleNameResolver.resolve(moduleName)

	/**
	 * The resolved reference to the module. **Only access within the Swing UI
	 * thread.**
	 */
	private val resolverReference = resolvedName.resolverReference

	/**
	 * The [UndoManager] for supplying undo/redo for edits to the underlying
	 * document.
	 */
	private val undoManager = UndoManager().apply {
		limit = 1000
	}

	init
	{
		// Action: undo the previous edit.
		object : AbstractEditorAction(
			this,
			"Undo",
			KeyStroke.getKeyStroke(
				KeyEvent.VK_Z,
				Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
			)
		)
		{
			override fun actionPerformed(e: ActionEvent) =
				try
				{
					undoManager.undo()
				}
				catch (e: CannotUndoException)
				{
					// Ignore.
				}
		}

		// Action: redo the previous undone edit.
		object : AbstractEditorAction(
			this,
			"Redo",
			KeyStroke.getKeyStroke(
				KeyEvent.VK_Z,
				Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx or
					KeyEvent.SHIFT_DOWN_MASK
			)
		)
		{
			override fun actionPerformed(e: ActionEvent) =
				try
				{
					undoManager.redo()
				}
				catch (e: CannotRedoException)
				{
					// Ignore.
				}
		}
	}

	/**
	 * Fetch the active [StylingRecord] for the target [module][A_Module].
	 *
	 * @param onSuccess
	 *   What to do with a [StylingRecord]. Might be applied to `null`, if
	 *   nothing went wrong but no [ModuleVersion], [ModuleCompilation], or
	 *   [StylingRecord] exists for the target module, e.g., because the module
	 *   has never been compiled.
	 * @param onError
	 *   What to do when the fetch fails unexpectedly, e.g., because of a
	 *   corrupt [Repository] or [StylingRecord].
	 */
	private fun getActiveStylingRecord(
		onSuccess: (StylingRecord?)->Unit,
		onError: (Throwable?)->Unit
	)
	{
		val repository = resolvedName.repository
		repository.reopenIfNecessary()
		val archive = repository.getArchive(resolvedName.rootRelativeName)
		archive.digestForFile(
			resolvedName,
			false,
			withDigest = { digest ->
				try
				{
					val versionKey = ModuleVersionKey(resolvedName, digest)
					val version = archive.getVersion(versionKey)
					if (version !== null)
					{
						val compilation = version.allCompilations.maxByOrNull(
							ModuleCompilation::compilationTime)
						if (compilation !== null)
						{
							val index = compilation.recordNumberOfStyling
							val stylingRecordBytes =
								repository.repository!![index]
							val inputStream = DataInputStream(
								ByteArrayInputStream(stylingRecordBytes))
							val stylingRecord = StylingRecord(inputStream)
							return@digestForFile onSuccess(stylingRecord)
						}
					}
					onSuccess(null)
				}
				catch (e: Throwable)
				{
					onError(e)
				}
			},
			failureHandler = { _, e -> onError(e) }
		)
	}

	/** The editor pane. */
	private val sourcePane = codeSuitableTextPane(workbench).apply {
		val semaphore = Semaphore(0)
		resolverReference.readFileString(
			true,
			withContents = { string, _ ->
				text = string
				getActiveStylingRecord(
					onSuccess = { stylingRecordOrNull ->
						stylingRecordOrNull?.let { stylingRecord ->
							val doc = styledDocument
							stylingRecord.styleRuns.forEach {
									(range, styleName) ->
								doc.setCharacterAttributes(
									range.first - 1,
									range.last - range.first + 1,
									doc.getStyle(styleName),
									false)
							}
						}
						semaphore.release()
					},
					onError = { e ->
						e?.let { e.printStackTrace() }
							?: System.err.println(
								"unable to style editor for $resolvedName")
						semaphore.release()
					}
				)
			},
			failureHandler = { code, throwable ->
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
		document.addUndoableEditListener(undoManager)
		// TODO Extract token/phrase style information that should have been
		// captured by stylers that ran against method/macro send phrases.
		// TODO Also, we need to capture info relating local variable
		// uses and definitions, and we should extract it here, so that we can
		// navigate, or at least highlight all the occurrences.
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
			eventuallySave()
		}
	}

	/**
	 * Cause the modified module to be written to disk soon.
	 * Only call within the Swing UI thread.
	 */
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

	/**
	 * Write the modified module to disk immediately.
	 * Only call within the Swing UI thread.
	 */
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

	/** Open the editor window. */
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
		val sourcePaneScroll = sourcePane.scrollTextWithLineNumbers()
		panel.layout = GroupLayout(panel).apply {
			//val pref = GroupLayout.PREFERRED_SIZE
			//val def = DEFAULT_SIZE
			//val max = Int.MAX_VALUE
			autoCreateGaps = true
			setHorizontalGroup(
				createSequentialGroup()
					.addComponent(sourcePaneScroll))
			setVerticalGroup(
				createSequentialGroup()
					.addComponent(sourcePaneScroll))
		}
		minimumSize = Dimension(550, 350)
		preferredSize = Dimension(800, 1000)
		add(panel)
		pack()
		isVisible = true
	}

	init
	{
		jMenuBar = createMenuBar {
			menu("Edit")
			{
				item(FindAction(workbench))
			}
		}
	}
}
