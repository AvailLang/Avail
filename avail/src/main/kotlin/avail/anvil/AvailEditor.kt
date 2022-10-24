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

import avail.AvailRuntime
import avail.AvailTask
import avail.anvil.AvailWorkbench.Companion.menuShiftShortcutMask
import avail.anvil.MenuBarBuilder.Companion.createMenuBar
import avail.anvil.StyleApplicator.applyStyleRuns
import avail.anvil.actions.FindAction
import avail.anvil.text.AvailEditorKit
import avail.anvil.text.AvailEditorKit.Companion.goToDialog
import avail.anvil.text.AvailEditorKit.Companion.openStructureView
import avail.anvil.text.AvailEditorKit.Companion.refresh
import avail.anvil.text.CodePane
import avail.anvil.text.MarkToDotRange
import avail.anvil.text.goTo
import avail.anvil.text.markToDotRange
import avail.anvil.views.StructureViewPanel
import avail.anvil.window.AvailEditorLayoutConfiguration
import avail.anvil.window.LayoutConfiguration
import avail.anvil.window.WorkbenchFrame
import avail.builder.AvailBuilder
import avail.builder.ModuleName
import avail.builder.ResolvedModuleName
import avail.compiler.ModuleManifestEntry
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.manifestEntries
import avail.persistence.cache.Repository
import avail.persistence.cache.Repository.ModuleCompilation
import avail.persistence.cache.Repository.ModuleVersion
import avail.persistence.cache.Repository.ModuleVersionKey
import avail.persistence.cache.Repository.StylingRecord
import avail.utility.notNullAnd
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit.getDefaultToolkit
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent.VK_F5
import java.awt.event.KeyEvent.VK_L
import java.awt.event.KeyEvent.VK_M
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.util.TimerTask
import java.util.concurrent.Semaphore
import javax.swing.GroupLayout
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.KeyStroke.getKeyStroke
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Caret

/**
 * An editor for an Avail source module. Currently supports:
 *
 * * Basic editing.
 * * Basic undo/redo.
 * * Template expansion, with prefix shortening and explicit single caret
 *   positioning.
 * * Syntax highlighting.
 * * Go to line/column.
 * * Open [structure&#32;view][StructureViewPanel].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct an [AvailEditor].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 * @param moduleName
 *   The [ModuleName] of the module being opened in the editor.
 * @param afterTextLoaded
 *   Action to perform after text has been loaded to [sourcePane].
 */
class AvailEditor constructor(
	override val workbench: AvailWorkbench,
	moduleName: ModuleName,
	afterTextLoaded: (AvailEditor) -> Unit = {}
) : WorkbenchFrame("Avail Editor: $moduleName")
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

	/**
	 * The line-end delimiter that should be used when saving the file.
	 */
	private var lineEndDelimiter = "\n"

	/** The [resolved][ResolvedModuleName] [module&#32;name][ModuleName]. */
	internal val resolvedName = runtime.moduleNameResolver.resolve(moduleName)

	/**
	 * The resolved reference to the module. **Only access within the Swing UI
	 * thread.**
	 */
	internal val resolverReference = resolvedName.resolverReference

	override val layoutConfiguration: LayoutConfiguration =
		AvailEditorLayoutConfiguration(resolvedName.qualifiedName)

	/** Any open dialogs owned by the receiver. */
	internal val openDialogs = mutableSetOf<JFrame>()

	/**
	 * The [ModuleManifestEntry] list for the represented model.
	 */
	private var manifestEntriesList: List<ModuleManifestEntry>? = null

	override fun saveWindowPosition()
	{
		super.saveWindowPosition()
		(layoutConfiguration as AvailEditorLayoutConfiguration).range =
			this@AvailEditor.sourcePane.markToDotRange()
	}

	internal fun updateManifestEntriesList (then: (List<ModuleManifestEntry>) -> Unit)
	{
		workbench.runtime.execute(
			AvailTask(FiberDescriptor.loaderPriority) {
				val newList = workbench.availBuilder.getLoadedModule(resolvedName)
					?.module?.manifestEntries() ?: emptyList()
				manifestEntriesList = newList
				then(newList)
			})
	}

	/**
	 * Get the [List] of [ModuleManifestEntry]s for the associated
	 * [AvailBuilder.LoadedModule] then provide it to the given lambda.
	 *
	 * @param then
	 *  The lambda that accepts the [List] of [ModuleManifestEntry]s.
	 */
	internal fun manifestEntries (then: (List<ModuleManifestEntry>) -> Unit)
	{
		val mel = manifestEntriesList
		if (mel != null)
		{
			then(mel)
		}
		else
		{
			updateManifestEntriesList(then)
		}
	}

	/**
	 * Open the [StructureViewPanel] associated with this [AvailEditor].
	 *
	 * @param giveEditorFocus
	 *   `true` gives focus to this [AvailEditor]; `false` give focus to
	 *   [AvailWorkbench.structureViewPanel].
	 */
	fun openStructureView (giveEditorFocus: Boolean = true)
	{
		manifestEntries {
			SwingUtilities.invokeLater {
				workbench.structureViewPanel.apply {
					updateView(this@AvailEditor, it)
					{
						if (giveEditorFocus)
						{
							this@AvailEditor.toFront()
							this@AvailEditor.requestFocus()
							this@AvailEditor.sourcePane.requestFocus()
						}
						else
						{
							requestFocus()
						}
					}
					isVisible = true
				}
			}
		}
	}

	/**
	 * Go to the top starting line of the given [ModuleManifestEntry].
	 */
	internal fun goTo (entry: ModuleManifestEntry)
	{
		sourcePane.goTo(entry.topLevelStartingLine - 1)
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
							val stylingRecord = StylingRecord(
								repository.repository!![index])
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

	/**
	 * The [MarkToDotRange] of the [Caret] in the [sourcePane].
	 */
	internal var range: MarkToDotRange // TODO can this be set?

	/**
	 * The [JLabel] that displays the [range]
	 */
	private val caretRangeLabel = JLabel()

	/** The editor pane. */
	internal val sourcePane = CodePane(
		workbench,
		isEditable = resolverReference.resolver.canSave &&
			workbench.getProjectRoot(resolverReference.moduleName.rootName)
				.notNullAnd { editable },
		AvailEditorKit(workbench)
	).apply {
		registerStyles()
		addCaretListener {
			range = markToDotRange()
			caretRangeLabel.text = range.toString()
		}
		inputMap.put(
			getKeyStroke(VK_L, getDefaultToolkit().menuShortcutKeyMaskEx),
			goToDialog)
		inputMap.put(
			getKeyStroke(VK_M, menuShiftShortcutMask),
			openStructureView)
		inputMap.put(getKeyStroke(VK_F5, 0), refresh)
		document.addDocumentListener(object : DocumentListener
		{
			override fun insertUpdate(e: DocumentEvent) = editorChanged()
			override fun changedUpdate(e: DocumentEvent) = editorChanged()
			override fun removeUpdate(e: DocumentEvent) = editorChanged()
		})
		putClientProperty(availEditor, this@AvailEditor)
	}

	init
	{
		highlightCode(afterTextLoaded)
		range = sourcePane.markToDotRange()
		caretRangeLabel.text = range.toString()
	}

	/**
	 * Apply style highlighting to the text in the [JTextPane].
	 *
	 * @param then
	 *   Action to perform after text has been loaded to [sourcePane].
	 */
	internal fun highlightCode(then: (AvailEditor) -> Unit = {})
	{
		var stylingRecord: StylingRecord? = null
		val semaphore = Semaphore(0)
		val info = SourceCodeInfo(runtime, resolverReference)
		info.sourceAndDelimiter.withValue { (normalizedText, delimiter) ->
			lineEndDelimiter = delimiter
			sourcePane.text = normalizedText
			getActiveStylingRecord(
				onSuccess = { stylingRecordOrNull ->
					stylingRecord = stylingRecordOrNull
					semaphore.release()
				},
				onError = { e ->
					e?.let { e.printStackTrace() }
						?: System.err.println(
							"unable to style editor for $resolvedName")
					semaphore.release()
				})
		}
		semaphore.acquire()
		stylingRecord?.let {
			sourcePane.styledDocument.applyStyleRuns(it.styleRuns)
		}
		then(this)
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
		val adjustedString = when (lineEndDelimiter)
		{
			"\n" -> string
			else -> string.replace("\n", lineEndDelimiter)
		}
		resolverReference.resolver.saveFile(
			resolverReference,
			adjustedString.toByteArray(),
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
	init
	{
		jMenuBar = createMenuBar {
			menu("Edit")
			{
				item(FindAction(workbench, this@AvailEditor))
			}
			addWindowMenu(this@AvailEditor)
		}
		// TODO add gutter in here
		addWindowListener(object : WindowAdapter()
		{
			override fun windowClosing(e: WindowEvent) {
				if (lastSaveTime < lastEditTime) forceWrite()
				workbench.closeEditor(this@AvailEditor)
				openDialogs.forEach { it.dispose() }
			}
		})
		addWindowFocusListener(object : WindowFocusListener
		{
			override fun windowGainedFocus(e: WindowEvent?)
			{
				if (workbench.structureViewIsOpen)
				{
					if (workbench.structureViewPanel.editor != this@AvailEditor)
					{
						openStructureView(true)
					}
				}
			}

			override fun windowLostFocus(e: WindowEvent?) = Unit
		})
		setLocationRelativeTo(workbench)
		val panel = JPanel(BorderLayout(20, 20))
		panel.border = EmptyBorder(10, 10, 10, 10)
		background = panel.background
		val sourcePaneScroll = sourcePane.scrollTextWithLineNumbers()
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
		if (workbench.structureViewIsOpen) openStructureView(true)
	}

	companion object
	{
		/** The client property key for an [AvailEditor] from a [JTextPane]. */
		const val availEditor = "avail-editor"

		/** The [AvailEditor] that sourced the [receiver][ActionEvent]. */
		internal val ActionEvent.editor get() =
			(source as JTextPane).getClientProperty(availEditor) as AvailEditor
	}
}
