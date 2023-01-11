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
import avail.anvil.MenuBarBuilder.Companion.createMenuBar
import avail.anvil.RenderingEngine.applyStyleRuns
import avail.anvil.PhrasePathStyleApplicator.PhraseNodeAttributeKey
import avail.anvil.PhrasePathStyleApplicator.TokenStyle
import avail.anvil.PhrasePathStyleApplicator.applyPhrasePaths
import avail.anvil.actions.FindAction
import avail.anvil.actions.RefreshStylesheetAction
import avail.anvil.shortcuts.AvailEditorShortcut
import avail.anvil.shortcuts.KeyboardShortcut
import avail.anvil.text.AvailEditorKit
import avail.anvil.text.CodePane
import avail.anvil.text.MarkToDotRange
import avail.anvil.text.goTo
import avail.anvil.text.markToDotRange
import avail.anvil.views.PhraseViewPanel
import avail.anvil.views.StructureViewPanel
import avail.anvil.window.AvailEditorLayoutConfiguration
import avail.anvil.window.LayoutConfiguration
import avail.anvil.window.WorkbenchFrame
import avail.builder.AvailBuilder
import avail.builder.ModuleName
import avail.builder.ResolvedModuleName
import avail.compiler.ModuleManifestEntry
import avail.descriptor.module.A_Module
import avail.persistence.cache.Repository
import avail.persistence.cache.Repository.ManifestRecord
import avail.persistence.cache.Repository.ModuleCompilation
import avail.persistence.cache.Repository.ModuleVersion
import avail.persistence.cache.Repository.ModuleVersionKey
import avail.persistence.cache.Repository.PhraseNode
import avail.persistence.cache.Repository.PhrasePathRecord
import avail.persistence.cache.Repository.StylingRecord
import avail.utility.notNullAnd
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
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
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Caret
import javax.swing.text.Position
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument
import kotlin.math.max
import kotlin.math.min

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
	val moduleName: ModuleName,
	afterTextLoaded: (AvailEditor) -> Unit = {}
) : WorkbenchFrame("Avail Editor: $moduleName")
{
	/**
	 * The cryptographic hash of the editor's text.  This gets cleared every
	 * time the document is edited.
	 */
	private var latestDigest: ByteArray? = null

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
	 * A class that maintains the position of a [ModuleManifestEntry] even as
	 * the underlying [StyledDocument] is edited.
	 */
	data class ManifestEntryInDocument constructor(
		val entry: ModuleManifestEntry,
		val startInDocument: Position)

	/**
	 * The [List] of [ManifestEntryInDocument], if known, for the module.
	 */
	private var manifestEntriesInDocument: List<ManifestEntryInDocument>? =
		null

	override fun saveWindowPosition()
	{
		super.saveWindowPosition()
		(layoutConfiguration as AvailEditorLayoutConfiguration).range =
			this@AvailEditor.sourcePane.markToDotRange()
	}

	/**
	 * Locate the most recent compilation of the current document, fetch its
	 * manifest entries, map them into [ManifestEntryInDocument]s, and update
	 * [manifestEntriesInDocument] accordingly.  These entries are tied to
	 * [Position]s in the document, so they will maintain "the same" textual
	 * position as text is added and removed.
	 */
	internal fun fetchManifestEntries()
	{
		assert(SwingUtilities.isEventDispatchThread())
		val semaphore = Semaphore(0)
		// In case of an error, don't update the manifest list, since the
		// previous version is as close as we can get.
		var entriesInDocument = manifestEntriesInDocument
		getActiveStylingAndPhrasePathRecords(
			onSuccess = { _, _, manifestRecord ->
				val entries = manifestRecord?.manifestEntries ?: emptyList()
				val document = sourcePane.styledDocument
				val root = document.defaultRootElement
				entriesInDocument = entries.map { entry ->
					val line = entry.topLevelStartingLine
					val normalizedLine =
						max(0, min(line - 1, root.elementCount - 1))
					val element = root.getElement(normalizedLine)
					val lineStart = element.startOffset
					val position = document.createPosition(lineStart)
					ManifestEntryInDocument(entry, position)
				}
				semaphore.release()
			},
			onError = {
				semaphore.release()
			})
		semaphore.acquire()
		manifestEntriesInDocument = entriesInDocument
	}

	/**
	 * Get the [List] of [ManifestEntryInDocument]s for the associated
	 * [AvailBuilder.LoadedModule].
	 *
	 * @return
	 *  The [List] of [ManifestEntryInDocument]s.
	 */
	private fun manifestEntriesInDocument(): List<ManifestEntryInDocument>
	{
		manifestEntriesInDocument ?: fetchManifestEntries()
		return manifestEntriesInDocument!!
	}

	/**
	 * Open the [StructureViewPanel] associated with this [AvailEditor].
	 *
	 * Must execute in the event dispatch thread.
	 *
	 * @param giveEditorFocus
	 *   `true` gives focus to this [AvailEditor]; `false` give focus to
	 *   [AvailWorkbench.structureViewPanel].
	 */
	fun openStructureView (giveEditorFocus: Boolean = true)
	{
		assert(SwingUtilities.isEventDispatchThread())
		val structView = workbench.structureViewPanel
		structView.updateView(this@AvailEditor, manifestEntriesInDocument())
		{
			if (giveEditorFocus)
			{
				toFront()
				requestFocus()
				sourcePane.requestFocus()
			}
		}
		structView.isVisible = true
	}

	/**
	 * Open the [PhraseViewPanel] associated with this [AvailEditor], if it's
	 * not already visible.
	 */
	fun openPhraseView ()
	{
		updatePhraseStructure()
		if (!workbench.phraseViewIsOpen)
		{
			// Open the phrase view.
			workbench.phraseViewPanel.isVisible = true
			workbench.phraseViewPanel.requestFocus()
		}
	}

	/**
	 * Go to the top starting line of the given [ModuleManifestEntry].
	 */
	internal fun goTo (entry: ManifestEntryInDocument)
	{
		assert(SwingUtilities.isEventDispatchThread())
		val document = sourcePane.styledDocument
		val root = document.defaultRootElement
		val line = root.getElementIndex(entry.startInDocument.offset)
		sourcePane.goTo(line)
	}

	/**
	 * Fetch the active [StylingRecord] and [PhrasePathRecord] for the target
	 * [module][A_Module].
	 *
	 * @param onSuccess
	 *   What to do with [StylingRecord] and [PhrasePathRecord]. One or both
	 *   arguments may be `null`, if nothing went wrong but no [ModuleVersion],
	 *   [ModuleCompilation], or [StylingRecord] exists for the target module,
	 *   e.g., because the module has never been compiled.
	 * @param onError
	 *   What to do when the fetch fails unexpectedly, e.g., because of a
	 *   corrupt [Repository] or [StylingRecord].
	 */
	private fun getActiveStylingAndPhrasePathRecords(
		onSuccess: (StylingRecord?, PhrasePathRecord?, ManifestRecord?)->Unit,
		onError: (Throwable?)->Unit)
	{
		val repository = resolvedName.repository
		repository.reopenIfNecessary()
		val repositoryFile = repository.repository!!
		val archive = repository.getArchive(resolvedName.rootRelativeName)
		archive.digestForFile(
			resolvedName,
			false,
			withDigest = { digest ->
				try
				{
					val versionKey = ModuleVersionKey(resolvedName, digest)
					val compilation = archive.getVersion(versionKey)
							?.allCompilations
							?.maxByOrNull(ModuleCompilation::compilationTime) ?:
						return@digestForFile onSuccess(null, null, null)
					val stylingRecord = StylingRecord(
						repositoryFile[compilation.recordNumberOfStyling])
					val phrasePathRecord = PhrasePathRecord(
						repositoryFile[compilation.recordNumberOfPhrasePaths])
					val manifestRecord = ManifestRecord(
						repositoryFile[compilation.recordNumberOfManifest])
					onSuccess(stylingRecord, phrasePathRecord, manifestRecord)
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
	internal var range: MarkToDotRange
		private set

	/**
	 * Compute the sequence of nested send phrases that describe how the
	 * selected token ended up being embedded in the final parse structure.
	 * Update the [AvailWorkbench.phraseViewPanel] to show this information.
	 */
	fun updatePhraseStructure()
	{
		// Skip it if the phrase view isn't open.
		if (!workbench.phraseViewIsOpen) return
		val doc = sourcePane.styledDocument
		// First look to the right of the cursor position.
		val dot = range.dotPosition.offset
		var element = doc.getCharacterElement(dot)
		var tokenStyle = element.attributes.getAttribute(PhraseNodeAttributeKey)
			as? TokenStyle
		if (tokenStyle == null)
		{
			// If there's no phrase structure information for the character to
			// the right of the cursor, try looking to the left.
			element = doc.getCharacterElement(max(dot - 1, 0))
			tokenStyle = element.attributes.getAttribute(PhraseNodeAttributeKey)
				as? TokenStyle
		}
		workbench.phraseViewPanel.updateView(this, tokenStyle)
	}

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
		initializeStyles()
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
			updatePhraseStructure()
		}

		// To add a new shortcut, add it as a subtype of the sealed class
		// AvailEditorShortcut.
		AvailEditorShortcut::class.sealedSubclasses.forEach {
			it.objectInstance?.addToInputMap(inputMap)
		}

		document.addDocumentListener(object : DocumentListener
		{
			override fun insertUpdate(e: DocumentEvent) = editorChanged()
			override fun changedUpdate(e: DocumentEvent) = editorChanged()
			override fun removeUpdate(e: DocumentEvent) = editorChanged()
		})
		putClientProperty(availEditor, this@AvailEditor)
	}

	/**
	 * Refresh the [KeyboardShortcut]s for this [AvailEditor].
	 */
	fun refreshShortcuts ()
	{
		sourcePane.inputMap.clear()
		// To add a new shortcut, add it as a subtype of the sealed class
		// AvailEditorShortcut.
		AvailEditorShortcut::class.sealedSubclasses.forEach {
			it.objectInstance?.addToInputMap(sourcePane.inputMap)
		}
		sourcePane.registerKeystrokes()
		SwingUtilities.invokeLater {
			sourcePane.revalidate()
		}
	}

	init
	{
		populateSourcePane(afterTextLoaded)
		range = sourcePane.markToDotRange()
		caretRangeLabel.text = range.toString()
		sourcePane.undoManager.discardAllEdits()
	}

	/** The scroll wrapper around the [sourcePane]. */
	private val sourcePaneScroll = sourcePane.scrollTextWithLineNumbers(
		workbench.globalSettings.editorGuideLines)

	/** The [styling&#32;record][StylingRecord] for the module. */
	private var stylingRecord: StylingRecord? = null

	/** The [phrase&#32;path&#32;record][PhrasePathRecord] for the module. */
	private var phrasePathRecord: PhrasePathRecord? = null

	/**
	 * Populate the [source&#32;pane][sourcePane] and obtain the most recently
	 * recorded [styling&#32;record][StylingRecord] for the underlying
	 * [module][A_Module]. [Highlight][highlightCode] the source code.
	 *
	 * Also apply the semantic styling that associates a [PhraseNode] with each
	 * token that was part of a parsed phrase.
	 *
	 * Also create a [Position] for each [ManifestEntryInDocument], so that it
	 * can navigate to the correct line even after edits (as long as the file
	 * had no edits since the last compilation at the time that the editor was
	 * opened).
	 *
	 * @param then
	 *   Action to perform after population and then highlighting are complete.
	 */
	internal fun populateSourcePane(then: (AvailEditor) -> Unit = {})
	{
		val semaphore = Semaphore(0)
		val info = SourceCodeInfo(runtime, resolverReference)
		info.sourceAndDelimiter.withValue { (normalizedText, delimiter) ->
			lineEndDelimiter = delimiter
			sourcePane.text = normalizedText
			getActiveStylingAndPhrasePathRecords(
				onSuccess = { stylingRec, phrasePathRec, _ ->
					stylingRecord = stylingRec
					phrasePathRecord = phrasePathRec
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
		highlightCode { then(this) }
	}

	/**
	 * Apply style highlighting to the text in the
	 * [source&#32;pane][sourcePane].
	 *
	 * @param then
	 *   Action to perform after highlighting is complete.
	 */
	internal fun highlightCode(then: (AvailEditor)->Unit = {})
	{
		stylingRecord?.let {
			sourcePane.styledDocument.applyStyleRuns(
				workbench.stylesheet, it.styleRuns)
		}
		phrasePathRecord?.let {
			sourcePane.styledDocument.applyPhrasePaths(it)
		}
		then(this)
	}

	/**
	 * The editor has indicated that the module has just been edited.
	 * Only call within the Swing UI thread.
	 */
	private fun editorChanged()
	{
		latestDigest = null
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

	/**
	 * The [code&#32;guide][CodeGuide] for the [source&#32;pane][sourcePane].
	 */
	private val codeGuide get() = sourcePane.getClientProperty(
		CodeGuide::class.java.name) as CodeGuide

	/**
	 * What to do after [refreshing][RefreshStylesheetAction] a
	 * [stylesheet][Stylesheet].
	 */
	fun afterRefreshStylesheet()
	{
		val stylesheet = workbench.stylesheet
		sourcePane.background = sourcePane.computeBackground(stylesheet)
		sourcePane.foreground = sourcePane.computeForeground(stylesheet)
		codeGuide.guideColor = codeGuide.computeColor()
	}

	/** Open the editor window. */
	init
	{
		highlightCode()
		range = sourcePane.markToDotRange()
		caretRangeLabel.text = range.toString()
		jMenuBar = createMenuBar {
			menu("Edit")
			{
				item(FindAction(workbench, this@AvailEditor))
			}
			addWindowMenu(this@AvailEditor)
		}
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
				if (workbench.phraseViewIsOpen)
				{
					if (workbench.phraseViewPanel.editor != this@AvailEditor)
					{
						updatePhraseStructure()
					}
				}
			}

			override fun windowLostFocus(e: WindowEvent?) = Unit
		})
		setLocationRelativeTo(workbench)
		val panel = JPanel(BorderLayout(20, 20))
		panel.border = EmptyBorder(10, 10, 10, 10)
		background = panel.background

		val sourcePaneScroll = sourcePane.scrollTextWithLineNumbers(
			workbench,
			workbench.globalSettings.editorGuideLines
		)
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
		afterTextLoaded(this@AvailEditor)
		if (workbench.structureViewIsOpen)
			updatePhraseStructure()
		isVisible = true
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
