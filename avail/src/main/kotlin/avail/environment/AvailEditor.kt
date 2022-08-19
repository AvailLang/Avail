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
import avail.environment.StyleApplicator.applyStyleRuns
import avail.environment.actions.FindAction
import avail.environment.editor.AbstractEditorAction
import avail.environment.text.AvailEditorKit.Companion.breakLine
import avail.environment.text.AvailEditorKit.Companion.outdent
import avail.persistence.cache.Repository
import avail.persistence.cache.Repository.ModuleCompilation
import avail.persistence.cache.Repository.ModuleVersion
import avail.persistence.cache.Repository.ModuleVersionKey
import avail.persistence.cache.Repository.StylingRecord
import avail.utility.PrefixTree.Companion.payloads
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CTRL_DOWN_MASK
import java.awt.event.KeyEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_SPACE
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.KeyEvent.VK_Z
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.TimerTask
import java.util.concurrent.Semaphore
import javax.swing.GroupLayout
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.JTextPane
import javax.swing.KeyStroke.getKeyStroke
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.undo.CannotRedoException
import javax.swing.undo.CannotUndoException
import javax.swing.undo.UndoManager

/**
 * An editor for an Avail source module. Currently supports:
 *
 * * Basic editing.
 * * Basic undo/redo.
 * * Template expansion, with prefix shortening and explicit single caret
 *   positioning.
 * * Syntax highlighting.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class AvailEditor constructor(
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
	internal val undoManager = UndoManager().apply {
		limit = 1000
	}

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
		// Action: undo the previous edit.
		object : AbstractEditorAction(
			this,
			"Undo",
			getKeyStroke(
				VK_Z,
				Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
			)
		) {
			override fun actionPerformed(e: ActionEvent) =
				try
				{
					undoManager.undo()
					clearStaleTemplateSelectionState()
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
			getKeyStroke(
				VK_Z,
				Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx or
					SHIFT_DOWN_MASK
			)
		) {
			override fun actionPerformed(e: ActionEvent) =
				try
				{
					undoManager.redo()
					clearStaleTemplateSelectionState()
				}
				catch (e: CannotRedoException)
				{
					// Ignore.
				}
		}

		// Expand template.
		object : AbstractEditorAction(
			this,
			"Expand Template",
			getKeyStroke(
				VK_SPACE,
				CTRL_DOWN_MASK
			)
		) {
			override fun actionPerformed(e: ActionEvent) = expandTemplate()
		}

		// Cancel template candidate selection.
		object : AbstractEditorAction(
			this,
			"Cancel Template Selection",
			getKeyStroke(KeyEvent.VK_ESCAPE, 0)
		) {
			override fun actionPerformed(e: ActionEvent) =
				cancelTemplateExpansion()
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

	/** The editor pane. */
	private val sourcePane = codeSuitableTextPane(workbench, this).apply {
		var stylingRecord: StylingRecord? = null
		val semaphore = Semaphore(0)
		resolverReference.readFileString(
			true,
			withContents = { string, _ ->
				text = string
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
					}
				)
			},
			failureHandler = { code, throwable ->
				text = "Error reading module: $throwable, code=$code"
				semaphore.release()
			})
		semaphore.acquire()
		stylingRecord?.let {
			styledDocument.applyStyleRuns(it.styleRuns)
		}
		isEditable = resolverReference.resolver.canSave
		addCaretListener { clearStaleTemplateSelectionState() }
		inputMap.put(getKeyStroke(VK_TAB, SHIFT_DOWN_MASK), outdent)
		inputMap.put(getKeyStroke(VK_ENTER, 0), breakLine)
		document.addDocumentListener(object : DocumentListener
		{
			override fun insertUpdate(e: DocumentEvent) = editorChanged()
			override fun changedUpdate(e: DocumentEvent) = editorChanged()
			override fun removeUpdate(e: DocumentEvent) = editorChanged()
		})
		document.addUndoableEditListener(undoManager)
		// Arrange for the undo manager to be available when only the source
		// pane is in scope.
		putClientProperty(AvailEditor::undoManager.name, undoManager)
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

	/**
	 * Attempt to expand the nonwhitespace text prior to the caret using one of
	 * the known template substitutions.
	 */
	private fun expandTemplate()
	{
		val document = sourcePane.styledDocument
		var length: Int
		var state = templateSelectionState
		if (state === null)
		{
			// This is a brand new template expansion, so determine the
			// candidates. Scan backwards to the first character after a
			// whitespace, treating the start of the document as such a
			// character.
			val caretPosition = sourcePane.caretPosition
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
				Toolkit.getDefaultToolkit().beep()
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
				Toolkit.getDefaultToolkit().beep()
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
				Toolkit.getDefaultToolkit().beep()
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
			sourcePane.caretPosition = startPosition + desiredCharacterPosition
			document.remove(sourcePane.caretPosition, 1)
		}
		else
		{
			sourcePane.caretPosition = startPosition + candidate.length
		}
		state.expandingTemplate = false
	}

	/**
	 * Cancel an ongoing iteration through template candidates. Restore the
	 * original text.
	 */
	private fun cancelTemplateExpansion()
	{
		val state = templateSelectionState
		if (state !== null)
		{
			val startPosition = state.startPosition
			val length = state.candidate.expandedLength
			val document = sourcePane.document
			document.remove(startPosition, length)
			val prefix = state.templatePrefix
			document.insertString(startPosition, prefix, null)
			// Positioning the caret is not strictly necessary, as the insertion
			// should have placed it correctly. Manually position it though just
			// to be safe.
			sourcePane.caretPosition = startPosition + prefix.length
			templateSelectionState = null
		}
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
		setLocationRelativeTo(workbench)
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
		minimumSize = Dimension(650, 350)
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
				item(FindAction(workbench, this@AvailEditor))
			}
			addWindowMenu(this@AvailEditor)
		}
	}

	companion object
	{
		/** The [AvailEditor] that sourced the [receiver][ActionEvent]. */
		@Suppress("unused")
		private val ActionEvent.editor get() =
			(source as JRootPane).parent as AvailEditor

		/** The length of the receiver after template expansion. */
		private val String.expandedLength get() =
			length - count { it == '⁁' }

		/**
		 * Locate the beginning of the line enclosing [position].
		 *
		 * @param position
		 *   The position from which to scan leftward in search of the beginning
		 *   of the enclosing line.
		 * @return
		 *   The beginning of the line. This is either the beginning of the
		 *   text or a position immediately following a linefeed (U+000A).
		 */
		private fun JTextPane.lineStartBefore(position: Int): Int
		{
			var i = position
			while (i > 0)
			{
				val c = getText(i, 1).codePointAt(0)
				if (c == '\n'.code)
				{
					// We actually want to skip past the linefeed to place the
					// insertion point.
					i++
					break
				}
				i--
			}
			// `i` is now positioned either (1) at the beginning of the text or
			// (2) just past the nearest linefeed left of `position`.
			return i
		}
	}
}
