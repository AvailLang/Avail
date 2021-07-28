/*
 * AvailWorkbench.kt
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

package com.avail.environment

import com.avail.AvailRuntime
import com.avail.AvailRuntimeConfiguration.activeVersionSummary
import com.avail.builder.AvailBuilder
import com.avail.builder.ModuleName
import com.avail.builder.ModuleNameResolver
import com.avail.builder.ModuleRoot
import com.avail.builder.ModuleRoots
import com.avail.builder.RenamesFileParser
import com.avail.builder.ResolvedModuleName
import com.avail.builder.UnresolvedDependencyException
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.environment.LayoutConfiguration.Companion.basePreferences
import com.avail.environment.LayoutConfiguration.Companion.moduleRenameSourceSubkeyString
import com.avail.environment.LayoutConfiguration.Companion.moduleRenameTargetSubkeyString
import com.avail.environment.LayoutConfiguration.Companion.moduleRenamesKeyString
import com.avail.environment.LayoutConfiguration.Companion.moduleRootsKeyString
import com.avail.environment.LayoutConfiguration.Companion.moduleRootsSourceSubkeyString
import com.avail.environment.actions.AboutAction
import com.avail.environment.actions.BuildAction
import com.avail.environment.actions.CancelAction
import com.avail.environment.actions.CleanAction
import com.avail.environment.actions.CleanModuleAction
import com.avail.environment.actions.ClearTranscriptAction
import com.avail.environment.actions.CreateProgramAction
import com.avail.environment.actions.ExamineCompilationAction
import com.avail.environment.actions.ExamineRepositoryAction
import com.avail.environment.actions.GenerateDocumentationAction
import com.avail.environment.actions.GenerateGraphAction
import com.avail.environment.actions.InsertEntryPointAction
import com.avail.environment.actions.ParserIntegrityCheckAction
import com.avail.environment.actions.PreferencesAction
import com.avail.environment.actions.RefreshAction
import com.avail.environment.actions.ResetCCReportDataAction
import com.avail.environment.actions.ResetVMReportDataAction
import com.avail.environment.actions.RetrieveNextCommand
import com.avail.environment.actions.RetrievePreviousCommand
import com.avail.environment.actions.SetDocumentationPathAction
import com.avail.environment.actions.ShowCCReportAction
import com.avail.environment.actions.ShowVMReportAction
import com.avail.environment.actions.SubmitInputAction
import com.avail.environment.actions.ToggleDebugInterpreterL1
import com.avail.environment.actions.ToggleDebugInterpreterL2
import com.avail.environment.actions.ToggleDebugInterpreterPrimitives
import com.avail.environment.actions.ToggleDebugJVM
import com.avail.environment.actions.ToggleDebugWorkUnits
import com.avail.environment.actions.ToggleFastLoaderAction
import com.avail.environment.actions.ToggleL2SanityCheck
import com.avail.environment.actions.TraceCompilerAction
import com.avail.environment.actions.TraceLoadedStatementsAction
import com.avail.environment.actions.TraceMacrosAction
import com.avail.environment.actions.TraceSummarizeStatementsAction
import com.avail.environment.actions.UnloadAction
import com.avail.environment.actions.UnloadAllAction
import com.avail.environment.nodes.AbstractBuilderFrameTreeNode
import com.avail.environment.nodes.EntryPointModuleNode
import com.avail.environment.nodes.EntryPointNode
import com.avail.environment.nodes.ModuleOrPackageNode
import com.avail.environment.nodes.ModuleRootNode
import com.avail.environment.streams.BuildInputStream
import com.avail.environment.streams.BuildOutputStream
import com.avail.environment.streams.BuildOutputStreamEntry
import com.avail.environment.streams.BuildPrintStream
import com.avail.environment.streams.StreamStyle
import com.avail.environment.streams.StreamStyle.BUILD_PROGRESS
import com.avail.environment.streams.StreamStyle.ERR
import com.avail.environment.streams.StreamStyle.INFO
import com.avail.environment.streams.StreamStyle.OUT
import com.avail.environment.tasks.BuildTask
import com.avail.files.FileManager
import com.avail.io.ConsoleInputChannel
import com.avail.io.ConsoleOutputChannel
import com.avail.io.TextInterface
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport.WORKBENCH_TRANSCRIPT
import com.avail.persistence.cache.Repositories
import com.avail.resolver.ModuleRootResolver
import com.avail.resolver.ModuleRootResolverRegistry
import com.avail.resolver.ResolverReference
import com.avail.resolver.ResourceType
import com.avail.stacks.StacksGenerator
import com.avail.utility.IO
import com.avail.utility.cast
import com.avail.utility.safeWrite
import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.DarculaTheme
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.io.Reader
import java.io.StringReader
import java.io.UnsupportedEncodingException
import java.lang.Integer.parseInt
import java.lang.String.format
import java.lang.System.currentTimeMillis
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.Arrays.sort
import java.util.Collections
import java.util.Collections.synchronizedMap
import java.util.Enumeration
import java.util.Queue
import java.util.TimerTask
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.prefs.BackingStoreException
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.GroupLayout
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JSplitPane.DIVIDER_LOCATION_PROPERTY
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
import javax.swing.SwingUtilities.invokeLater
import javax.swing.SwingWorker
import javax.swing.WindowConstants
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext
import javax.swing.text.StyledDocument
import javax.swing.text.TabSet
import javax.swing.text.TabStop
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import kotlin.concurrent.schedule
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

/**
 * `AvailWorkbench` is a simple user interface for the
 * [Avail&#32;builder][AvailBuilder].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property runtime
 *   The [AvailRuntime] for this workbench to use.
 * @property fileManager
 *   The [FileManager] used to manage Avail files.
 * @property resolver
 *   The [module&#32;name resolver][ModuleNameResolver].
 *
 * @constructor
 * Construct a new `AvailWorkbench`.
 *
 * @param fileManager
 *   The [FileManager] used to manage Avail files.
 * @param resolver
 *   The [module&#32;name resolver][ModuleNameResolver].
 */
class AvailWorkbench internal constructor (
	private val runtime: AvailRuntime,
	private val fileManager: FileManager,
	val resolver: ModuleNameResolver) : JFrame()
{
	/**
	 * The [StyledDocument] into which to write both error and regular
	 * output.  Lazily initialized.
	 */
	private val document: StyledDocument by lazy { transcript.styledDocument }

	/** The last moment (ms) that a UI update of the transcript completed. */
	private val lastTranscriptUpdateCompleted = AtomicLong(0L)

	/**
	 * A [List] of [BuildOutputStreamEntry](s), each of which holds a style and
	 * a [String].  The [totalQueuedTextSize] must be updated after an add to
	 * this queue, or before a remove from this queue. This ensures that the
	 * queue contains at least as many characters as the counter indicates,
	 * although it can be more.  Additionally, to allow each enqueuer to also
	 * deque surplus entries, the [dequeLock] must be held exclusively (for
	 * write) whenever removing entries from the queue.
	 */
	private val updateQueue: Queue<BuildOutputStreamEntry> =
		ConcurrentLinkedQueue()

	/**
	 * The sum of the lengths of the strings in the updateQueue.  This value
	 * must always be ≤ the actual sum of the lengths of the strings at any
	 * moment, so it's updated after an add but before a remove from the queue.
	 */
	private val totalQueuedTextSize = AtomicLong()

	/**
	 * A lock that's held when removing things from the [updateQueue].
	 */
	private val dequeLock = ReentrantReadWriteLock(false)

	/** The current [background task][AbstractWorkbenchTask]. */
	@Volatile
	var backgroundTask: AbstractWorkbenchTask? = null

	/**
	 * The documentation [path][Path] for the
	 * [Stacks&#32;generator][StacksGenerator].
	 */
	var documentationPath: Path = StacksGenerator.defaultDocumentationPath

	/** The [standard input stream][BuildInputStream]. */
	private val inputStream: BuildInputStream?

	/** The [standard error stream][PrintStream]. */
	private val errorStream: PrintStream?

	/** The [standard output stream][PrintStream]. */
	val outputStream: PrintStream

	/* UI components. */

	/** The [module][ModuleDescriptor] [tree][JTree]. */
	val moduleTree: JTree

	/**
	 * The [tree][JTree] of module [entry&#32;points][A_Module.entryPoints].
	 */
	val entryPointsTree: JTree

	/**
	 * The [AvailBuilder] used by this user interface.
	 */
	val availBuilder: AvailBuilder

	/**
	 * The [progress&#32;bar][JProgressBar] that displays the overall build
	 * progress.
	 */
	val buildProgress: JProgressBar

	/**
	 * The [text&#32;area][JTextPane] that displays the [build][AvailBuilder]
	 * transcript.
	 */
	val transcript: JTextPane

	/** The [scroll bars][JScrollPane] for the [transcript]. */
	private val transcriptScrollArea: JScrollPane

	/**
	 * The [label][JLabel] that describes the current function of the
	 * [input&#32;field][inputField].
	 */
	private val inputLabel: JLabel

	/** The [text field][JTextField] that accepts standard input. */
	val inputField: JTextField

	/**
	 * Keep track of recent commands in a history buffer.  Each submitted
	 * command is added to the end of the list.  Cursor-up retrieves the most
	 * recent selected line, and subsequent cursors-up retrieve previous lines,
	 * back to the first entry, then an empty command line, then the last entry
	 * again an so on.  An initial cursor-down selects the first entry and goes
	 * from there.
	 */
	val commandHistory = mutableListOf<String>()

	/**
	 * Which command was most recently retrieved by a cursor key since the last
	 * command was submitted.  -1 indicates no command has been retrieved by a
	 * cursor key, or that the entire list has been cycled an integral number of
	 * times (and the command line was blanked upon reaching -1).
	 */
	var commandHistoryIndex = -1

	/** Cycle one step backward in the command history. */
	private val retrievePreviousAction = RetrievePreviousCommand(this)

	/** Cycle one step forward in the command history. */
	private val retrieveNextAction = RetrieveNextCommand(this)

	/* Actions. */

	/** The [refresh action][RefreshAction]. */
	private val refreshAction = RefreshAction(this)

	/** The [&quot;about Avail&quot; action][AboutAction]. */
	private val aboutAction = AboutAction(this)

	/** The [&quot;Preferences...&quot; action][PreferencesAction]. */
	private val preferencesAction = PreferencesAction(this)

	/** The [build action][BuildAction]. */
	internal val buildAction = BuildAction(this, false)

	/** The [unload action][UnloadAction]. */
	private val unloadAction = UnloadAction(this)

	/** The [unload-all action][UnloadAllAction]. */
	private val unloadAllAction = UnloadAllAction(this)

	/** The [cancel action][CancelAction]. */
	private val cancelAction = CancelAction(this)

	/** The [clean action][CleanAction]. */
	private val cleanAction = CleanAction(this)

	/** The [clean module action][CleanModuleAction]. */
	private val cleanModuleAction = CleanModuleAction(this)

	/** The [create program action][CreateProgramAction]. */
	private val createProgramAction = CreateProgramAction(this)

	/**
	 * The [generate&#32;documentation&#32;action][GenerateDocumentationAction].
	 */
	private val documentAction = GenerateDocumentationAction(this)

	/** The [generate graph action][GenerateGraphAction]. */
	private val graphAction = GenerateGraphAction(this)

	/**
	 * The
	 * [documentation&#32;path&#32;dialog&#32;action][SetDocumentationPathAction].
	 */
	private val setDocumentationPathAction = SetDocumentationPathAction(this)

	/** The [show VM report action][ShowVMReportAction]. */
	private val showVMReportAction = ShowVMReportAction(this)

	/** The [reset VM report data action][ResetVMReportDataAction]. */
	private val resetVMReportDataAction = ResetVMReportDataAction(this)

	/** The [show CC report action][ShowCCReportAction]. */
	private val showCCReportAction: ShowCCReportAction

	/** The [reset CC report data action][ResetCCReportDataAction]. */
	private val resetCCReportDataAction: ResetCCReportDataAction

	/** The [toggle trace macros action][TraceMacrosAction]. */
	private val debugMacroExpansionsAction = TraceMacrosAction(this)

	/** The [toggle trace compiler action][TraceCompilerAction]. */
	private val debugCompilerAction = TraceCompilerAction(this)

	/** The [toggle fast-loader action][ToggleFastLoaderAction]. */
	private val toggleFastLoaderAction = ToggleFastLoaderAction(this)

	/** The [toggle L1 debug action][ToggleDebugInterpreterL1]. */
	private val toggleDebugL1 = ToggleDebugInterpreterL1(this)

	/** The [toggle L2 debug action][ToggleDebugInterpreterL2]. */
	private val toggleDebugL2 = ToggleDebugInterpreterL2(this)

	/** The [ToggleL2SanityCheck] toggle L2 sanity checks action}. */
	private val toggleL2SanityCheck = ToggleL2SanityCheck(this)

	/**
	 * The
	 * [toggle&#32;primitive&#32;debug&#32;action][ToggleDebugInterpreterPrimitives].
	 */
	private val toggleDebugPrimitives =
		ToggleDebugInterpreterPrimitives(this)

	/**
	 * The [toggle&#32;work-units&#32;debug&#32;action][ToggleDebugWorkUnits].
	 */
	private val toggleDebugWorkUnits = ToggleDebugWorkUnits(this)

	/** The [toggle JVM dump debug action][ToggleDebugJVM]. */
	private val toggleDebugJVM = ToggleDebugJVM(this)

	/**
	 * The
	 * [toggle&#32;fast-loader&#32;summarization&#32;action][TraceSummarizeStatementsAction].
	 */
	private val traceSummarizeStatementsAction =
		TraceSummarizeStatementsAction(this)

	/**
	 * The [toggle&#32;load-tracing&#32;action][TraceLoadedStatementsAction].
	 */
	private val traceLoadedStatementsAction =
		TraceLoadedStatementsAction(this)

	/** The [ParserIntegrityCheckAction]. */
	private val parserIntegrityCheckAction: ParserIntegrityCheckAction

	/** The [ExamineRepositoryAction]. */
	private val examineRepositoryAction: ExamineRepositoryAction

	/** The [ExamineCompilationAction]. */
	private val examineCompilationAction: ExamineCompilationAction

	/** The [clear transcript action][ClearTranscriptAction]. */
	private val clearTranscriptAction = ClearTranscriptAction(this)

	/** The [insert entry point action][InsertEntryPointAction]. */
	internal val insertEntryPointAction = InsertEntryPointAction(this)

	/** The [action to build an entry point module][BuildAction]. */
	internal val buildEntryPointModuleAction = BuildAction(this, true)

//	/**
//	 * The {@linkplain DisplayCodeCoverageReport action to display the current
//	 * code coverage session's report data}.
//	 */
//	val displayCodeCoverageReport = DisplayCodeCoverageReport(this, true)

//	/**
//	 * The {@linkplain ResetCodeCoverageDataAction action to reset the code
//	 * coverage data and thereby start a new code coverage session}.
//	 */
//	val resetCodeCoverageDataAction = ResetCodeCoverageDataAction(this, true)

	/** Whether an entry point invocation (command line) is executing. */
	var isRunning = false

	/**
	 * A monitor to serialize access to the current build status information.
	 */
	private val buildGlobalUpdateLock = ReentrantReadWriteLock()

	/**
	 * The position up to which the current build has completed.  Protected by
	 * [buildGlobalUpdateLock].
	 */
	//@GuardedBy("buildGlobalUpdateLock")
	private var latestGlobalBuildPosition: Long = -1

	/**
	 * The total number of bytes of code to be loaded.  Protected by
	 * [buildGlobalUpdateLock].
	 */
	//@GuardedBy("buildGlobalUpdateLock")
	private var globalBuildLimit: Long = -1

	/**
	 * Whether a user interface task for updating the visible build progress has
	 * been queued but not yet completed.  Protected by [buildGlobalUpdateLock].
	 */
	//@GuardedBy("buildGlobalUpdateLock")
	private var hasQueuedGlobalBuildUpdate = false

	/** A monitor to protect updates to the per module progress. */
	private val perModuleProgressLock = ReentrantReadWriteLock()

	/**
	 * The progress map per module.  Protected by [perModuleProgressLock].
	 */
	//@GuardedBy("perModuleProgressLock")
	private val perModuleProgress =
		mutableMapOf<ModuleName, Triple<Long, Long, Int>>()

	/**
	 * Whether a user interface task for updating the visible per-module
	 * information has been queued but not yet completed.  Protected by
	 * [perModuleProgressLock].
	 */
	//@GuardedBy("perModuleProgressLock")
	private var hasQueuedPerModuleBuildUpdate = false

	/**
	 * The number of characters of text at the start of the transcript which is
	 * currently displaying per-module progress information.  Only accessible in
	 * the event thread.
	 */
	private var perModuleStatusTextSize = 0

	/**
	 * A gate that prevents multiple [AbstractWorkbenchTask]s from running at
	 * once. `true` indicates a task is running and will prevent a second task
	 * from starting; `false` indicates the workbench is available to run a
	 * task.
	 */
	private var taskGate = AtomicBoolean(false)

	/**
	 * `AbstractWorkbenchTask` is a foundation for long running [AvailBuilder]
	 * operations.
	 *
	 * @property workbench
	 *   The owning [AvailWorkbench].
	 * @property targetModuleName
	 *   The resolved name of the target [module][ModuleDescriptor].
	 *
	 * @constructor
	 * Construct a new `AbstractWorkbenchTask`.
	 *
	 * @param workbench
	 *   The owning [AvailWorkbench].
	 * @param targetModuleName
	 *   The resolved name of the target [module][ModuleDescriptor].
	 */
	abstract class AbstractWorkbenchTask constructor(
		val workbench: AvailWorkbench,
		protected val targetModuleName: ResolvedModuleName?
	) : SwingWorker<Void, Void>()
	{
		/** The start time. */
		private var startTimeMillis: Long = 0

		/** The stop time. */
		private var stopTimeMillis: Long = 0

		/** The [exception][Throwable] that terminated the build. */
		private var terminator: Throwable? = null

		/** Cancel the current task. */
		fun cancel() = workbench.availBuilder.cancel()

		/**
		 * Ensure the target module name is not null, then answer it.
		 *
		 * @return The non-null target module name.
		 */
		protected fun targetModuleName(): ResolvedModuleName =
			targetModuleName!!

		/**
		 * Report completion (and timing) to the [transcript][transcript].
		 */
		protected fun reportDone()
		{
			val durationMillis = stopTimeMillis - startTimeMillis
			val status: String?
			val t = terminator
			status = when
			{
				t !== null -> "Aborted (${t.javaClass.simpleName})"
				workbench.availBuilder.shouldStopBuild ->
					workbench.availBuilder.stopBuildReason
				else -> "Done"
			}
			workbench.writeText(
				format(
					"%s (%d.%03ds).%n",
					status,
					durationMillis / 1000,
					durationMillis % 1000),
				INFO)
		}

		@Throws(Exception::class)
		override fun doInBackground(): Void?
		{
			if (workbench.taskGate.getAndSet(true))
			{
				// task is running
				return null
			}
			startTimeMillis = currentTimeMillis()
			executeTaskThen {
				try
				{
					Repositories.closeAllRepositories()
					stopTimeMillis = currentTimeMillis()
				}
				finally
				{
					workbench.taskGate.set(false)
				}

			}
			return null
		}

		/**
		 * Execute this `AbstractWorkbenchTask`.
		 *
		 * @param afterExecute
		 *   The lambda to run after the task completes.
		 * @throws Exception If anything goes wrong.
		 */
		@Throws(Exception::class)
		protected abstract fun executeTaskThen(afterExecute: ()->Unit)
	}

	/**
	 * Update the [transcript] by appending the (non-empty) queued
	 * text to it.  Only output what was already queued by the time the UI
	 * runnable starts; if additional output is detected afterward, another UI
	 * runnable will be queued to deal with the residue (iteratively).  This
	 * maximizes efficiency while avoiding starvation of the UI process in the
	 * event that a high volume of data is being written.
	 */
	private fun updateTranscript()
	{
		assert(totalQueuedTextSize.get() > 0)
		val now = currentTimeMillis()
		if (now - lastTranscriptUpdateCompleted.get() > 200)
		{
			// It's been more than 200ms since the last UI update completed, so
			// process the update immediately.
			invokeLater { this.privateUpdateTranscriptInUIThread() }
		}
		else
		{
			// Wait until 200ms have actually elapsed.
			availBuilder.runtime.timer.schedule(
				object : TimerTask()
				{
					override fun run()
					{
						invokeLater { privateUpdateTranscriptInUIThread() }
					}
				},
				max(0, now - lastTranscriptUpdateCompleted.get()))
		}
	}

	/**
	 * Discard entries from the [updateQueue] without updating the
	 * [totalQueuedTextSize] until no more can be discarded.  The [dequeLock]
	 * must be acquired for write before calling this.  The caller should
	 * decrease the [totalQueuedTextSize] by the returned amount before
	 * releasing the [dequeLock].
	 *
	 * Assume the [totalQueuedTextSize] is accurate prior to the call.
	 *
	 * @return The number of characters removed from the queue.
	 */
	private fun privateDiscardExcessLeadingQueuedUpdates(): Int
	{
		val before = System.nanoTime()
		try
		{
			assert(dequeLock.isWriteLockedByCurrentThread)
			var excessSize = totalQueuedTextSize.get() - maxDocumentSize
			var removed = 0
			while (true)
			{
				val entry = updateQueue.peek() ?: return removed
				val size = entry.string.length
				if (size >= excessSize)
				{
					return removed
				}
				val entry2 = updateQueue.remove()
				assert(entry == entry2)
				excessSize -= size.toLong()
				removed += size
			}
		}
		finally
		{
			discardExcessLeadingStat.record(System.nanoTime() - before)
		}
	}

	/**
	 * Must be called in the dispatch thread.  Actually update the transcript.
	 */
	private fun privateUpdateTranscriptInUIThread()
	{
		assert(EventQueue.isDispatchThread())
		// Hold the dequeLock just long enough to extract all entries, only
		// decreasing totalQueuedTextSize just before unlocking.
		var lengthToInsert = 0
		val aggregatedEntries = mutableListOf<BuildOutputStreamEntry>()
		val wentToZero = dequeLock.safeWrite {
			var removedSize = privateDiscardExcessLeadingQueuedUpdates()
			var currentStyle: StreamStyle? = null
			val builder = StringBuilder()
			while (true)
			{
				val entry = if (removedSize < totalQueuedTextSize.get())
					updateQueue.poll()!!
				else
					null
				if (entry === null || entry.style != currentStyle)
				{
					// Either the final entry or a style change.
					if (currentStyle !== null)
					{
						val string = builder.toString()
						aggregatedEntries.add(
							BuildOutputStreamEntry(currentStyle, string))
						lengthToInsert += string.length
						builder.setLength(0)
					}
					if (entry === null)
					{
						// The queue has been emptied.
						break
					}
					currentStyle = entry.style
				}
				builder.append(entry.string)
				removedSize += entry.string.length
			}
			// Only now should we decrease the counter, otherwise writers could
			// have kept adding things unboundedly while we were removing them.
			// Adding things "boundedly" is fine, however (i.e., blocking on the
			// dequeLock if too much is added).
			val afterRemove =
				totalQueuedTextSize.addAndGet((-removedSize).toLong())
			assert(afterRemove >= 0)
			afterRemove == 0L
		}

		assert(aggregatedEntries.isNotEmpty())
		assert(lengthToInsert > 0)
		try
		{
			val statusSize = perModuleStatusTextSize
			val length = document.length
			val amountToRemove =
				length - statusSize + lengthToInsert - maxDocumentSize
			if (amountToRemove > 0)
			{
				// We need to trim off some of the document, right after the
				// module status area.
				val beforeRemove = System.nanoTime()
				document.remove(
					statusSize, min(amountToRemove, length - statusSize))
				// Always use index 0, since this only happens in the UI thread.
				removeStringStat.record(System.nanoTime() - beforeRemove)
			}
			aggregatedEntries.forEach { entry ->
				val before = System.nanoTime()
				document.insertString(
					document.length, // The current length
					entry.string,
					entry.style.styleIn(document))
				// Always use index 0, since this only happens in the UI thread.
				insertStringStat.record(System.nanoTime() - before)
			}
		}
		catch (e: BadLocationException)
		{
			// Ignore the failed append, which should be impossible.
		}

		lastTranscriptUpdateCompleted.set(currentTimeMillis())
		transcript.repaint()
		if (!wentToZero)
		{
			updateTranscript()
		}
	}

	/**
	 * An abstraction for a color that's dependent on light versus dark mode.
	 *
	 * @property light
	 *   The color to use in light mode.
	 * @property light
	 *   The color to use in dark mode.
	 * @constructor
	 * Construct a new `AdaptiveColor`.
	 */
	data class AdaptiveColor constructor(
		private val light: Color,
		private val dark: Color)
	{
		val color: Color get() = if (darkMode) dark else light

		val hex: String
			get() = with(color) {
				format("#%02x%02x%02x", red, green, blue)
			}
	}

	/**
	 * Answer the [standard&#32;input&#32;stream][BuildInputStream].
	 *
	 * @return The input stream.
	 */
	fun inputStream(): BuildInputStream = inputStream!!

	/**
	 * Answer the [standard&#32;error&#32;stream][PrintStream].
	 *
	 * @return The error stream.
	 */
	fun errorStream(): PrintStream = errorStream!!

	/**
	 * Answer the [standard&#32;output&#32;stream][PrintStream].
	 *
	 * @return The output stream.
	 */
	fun outputStream(): PrintStream = outputStream

	/**
	 * Enable or disable controls and menu items based on the current state.
	 */
	fun setEnablements()
	{
		val busy = backgroundTask !== null || isRunning
		buildProgress.isEnabled = busy
		buildProgress.isVisible = backgroundTask is BuildTask
		inputField.isEnabled = !busy || isRunning
		retrievePreviousAction.isEnabled = !busy
		retrieveNextAction.isEnabled = !busy
		cancelAction.isEnabled = busy
		buildAction.isEnabled = !busy && selectedModule() !== null
		unloadAction.isEnabled = !busy && selectedModuleIsLoaded()
		unloadAllAction.isEnabled = !busy
		cleanAction.isEnabled = !busy
		cleanModuleAction.isEnabled =
			!busy && (selectedModuleRoot() !== null || selectedModule() !== null)
		refreshAction.isEnabled = !busy
		setDocumentationPathAction.isEnabled = !busy
		documentAction.isEnabled = !busy && selectedModule() !== null
		graphAction.isEnabled = !busy && selectedModule() !== null
		insertEntryPointAction.isEnabled = !busy && selectedEntryPoint() !== null
		val selectedEntryPointModule = selectedEntryPointModule()
		createProgramAction.isEnabled =
			(!busy && selectedEntryPoint() !== null
				&& selectedEntryPointModule !== null
				&& availBuilder.getLoadedModule(selectedEntryPointModule)
				!= null)
		examineRepositoryAction.isEnabled =
			!busy && selectedModuleRootNode() !== null
		examineCompilationAction.isEnabled = !busy && selectedModule() !== null
		buildEntryPointModuleAction.isEnabled =
			!busy && selectedEntryPointModule() !== null
		inputLabel.text = if (isRunning) "Console Input:" else "Command:"
		inputField.background =
			if (isRunning) inputBackgroundWhenRunning.color else null
		inputField.foreground =
			if (isRunning) inputForegroundWhenRunning.color else null
	}

	/**
	 * Clear the [transcript][transcript].
	 */
	fun clearTranscript()
	{
		invokeLater {
			try
			{
				val beforeRemove = System.nanoTime()
				document.remove(
					perModuleStatusTextSize,
					document.length - perModuleStatusTextSize)
				// Always use index 0, since this only happens in the UI thread.
				removeStringStat.record(System.nanoTime() - beforeRemove)
			}
			catch (e: BadLocationException)
			{
				// Shouldn't happen.
				assert(false)
			}
		}
	}

	/**
	 * `true` indicates the entire tree is currently being calculated; `false`
	 * permits a new calculation of the tree.
	 */
	private val treeCalculationInProgress = AtomicBoolean(false)

	/**
	 * Re-parse the package structure from scratch.  Invoke the provided closure
	 * with the module tree and entry points tree, but don't install them. This
	 * can safely be run outside the UI thread.
	 *
	 * @param withTrees
	 *   Lambda that accepts the modules tree and entry points tree.
	 */
	fun calculateRefreshedTreesThen(
		withTrees: (TreeNode, TreeNode) -> Unit)
	{
		if (!treeCalculationInProgress.getAndSet(true))
		{
			resolver.clearCache()
			newModuleTreeThen { modules ->
				newEntryPointsTreeThen { entryPoints ->
					withTrees(modules, entryPoints)
					treeCalculationInProgress.set(false)
				}
			}
		}
	}

	/**
	 * Re-populate the visible tree structures based on the provided tree of
	 * modules and tree of entry points.  Attempt to preserve selection and
	 * expansion information.
	 *
	 * @param modules
	 *   The [TreeNode] of modules to present.
	 * @param entryPoints
	 *   The [TreeNode] of entry points to present.
	 */
	fun refreshFor(modules: TreeNode, entryPoints: TreeNode)
	{
		val selection = selectedModule()
		moduleTree.isVisible = false
		entryPointsTree.isVisible = false
		try
		{
			moduleTree.model = DefaultTreeModel(modules)
			for (i in moduleTree.rowCount - 1 downTo 0)
			{
				moduleTree.expandRow(i)
			}
			if (selection !== null)
			{
				val path = modulePath(selection.qualifiedName)
				if (path !== null)
				{
					moduleTree.selectionPath = path
				}
			}

			entryPointsTree.model = DefaultTreeModel(entryPoints)
			for (i in entryPointsTree.rowCount - 1 downTo 0)
			{
				entryPointsTree.expandRow(i)
			}
		}
		finally
		{
			moduleTree.isVisible = true
			entryPointsTree.isVisible = true
		}
	}

	/**
	 * Answer a [tree&#32;node][TreeNode] that represents the (invisible) root
	 * of the Avail module tree.
	 *
	 * @param withTreeNode
	 *   The lambda that accepts the (invisible) root of the module tree.
	 */
	private fun newModuleTreeThen(withTreeNode: (TreeNode) -> Unit)
	{
		val roots = resolver.moduleRoots
		val sortedRootNodes = Collections.synchronizedList(
			mutableListOf<ModuleRootNode>())

		val treeRoot = DefaultMutableTreeNode(
			"(packages hidden root)")
		val rootCount = AtomicInteger(roots.roots.size)
		// Put the invisible root onto the work stack.
		roots.roots.forEach { root ->
			// Obtain the path associated with the module root.
			root.repository.reopenIfNecessary()
			root.resolver.provideModuleRootTree(
				{
					val node = ModuleRootNode(availBuilder, root)
					createRootNodesThen(node, it) {
						sortedRootNodes.add(node)
						// Need to wait for every module to finish the
						// resolution process before handing in the hidden, top
						// level tree node.
						if (rootCount.decrementAndGet() == 0)
						{
							sortedRootNodes.sort()
							sortedRootNodes.forEach { m -> treeRoot.add(m) }
							val enumeration: Enumeration<DefaultMutableTreeNode> =
								treeRoot.preorderEnumeration().cast()
							// Skip the invisible root.
							enumeration.nextElement()
							while (enumeration.hasMoreElements())
							{
								val subNode: AbstractBuilderFrameTreeNode =
									enumeration.nextElement().cast()
								subNode.sortChildren()
							}
							withTreeNode(treeRoot)
						}
					}
				},
				{ code, ex ->
					System.err.println(
						"Workbench could not walk root: ${root.name}: $code")
					ex?.printStackTrace()
				})
		}
	}

	/**
	 * Populate the provided [rootNode][ModuleRootNode] with tree nodes to be
	 * displayed in the workbench.
	 *
	 * @param rootNode
	 *   The [ModuleRootNode] to populate.
	 * @param parentRef
	 *   The root's [ResolverReference] that will be
	 *   [traversed][ResolverReference.walkChildrenThen].
	 * @param afterCreated
	 *   What to do after all root nodes have been recursively created. Accepts
	 *   the count of created nodes across all roots.
	 */
	private fun createRootNodesThen (
		rootNode: ModuleRootNode,
		parentRef: ResolverReference,
		afterCreated: (Int)->Unit)
	{
		val parentMap = mutableMapOf<String, DefaultMutableTreeNode>()
		parentMap[parentRef.qualifiedName] = rootNode
		parentRef.walkChildrenThen(
			false,
			{
				if (it.isRoot)
				{
					return@walkChildrenThen
				}
				val parentNode = parentMap[it.parentName]
					?: return@walkChildrenThen
				if (it.type == ResourceType.MODULE)
				{
					try
					{
						val resolved =
							resolver.resolve(it.moduleName)
						val node = ModuleOrPackageNode(
							availBuilder, it.moduleName, resolved, false)
						parentNode.add(node)
					}
					catch (e: UnresolvedDependencyException)
					{
						// TODO MvG - Find a better way of reporting broken
						//  dependencies. Ignore for now (during scan).
						throw RuntimeException(e)
					}
				}
				else if (it.type == ResourceType.PACKAGE)
				{
					val moduleName = it.moduleName
					val resolved: ResolvedModuleName
					try
					{
						resolved = resolver.resolve(moduleName)
					}
					catch (e: UnresolvedDependencyException)
					{
						// The directory didn't contain the necessary package
						// representative, so simply skip the whole directory.
						return@walkChildrenThen
					}

					val node = ModuleOrPackageNode(
						availBuilder, moduleName, resolved, true)
					parentNode.add(node)
					if (resolved.isRename)
					{
						// Don't examine modules inside a package which is the
						// source of a rename.  They wouldn't have resolvable
						// dependencies anyhow.
						return@walkChildrenThen
					}
					parentMap[it.qualifiedName] = node
				}
			},
			afterCreated)
	}

	/**
	 * Provide a [tree&#32;node][TreeNode] that represents the (invisible) root
	 * of the Avail entry points tree.
	 *
	 * @param withTreeNode
	 *   Function that accepts a [TreeNode] that contains all the
	 *   [EntryPointModuleNode]s.
	 */
	private fun newEntryPointsTreeThen(withTreeNode: (TreeNode) -> Unit)
	{
		val mutex = ReentrantReadWriteLock()
		val moduleNodes = synchronizedMap(
			mutableMapOf<String, DefaultMutableTreeNode>())
		availBuilder.traceDirectoriesThen({ resolvedName, moduleVersion, after ->
			val entryPoints = moduleVersion.getEntryPoints()
			if (entryPoints.isNotEmpty())
			{
				val moduleNode =
					EntryPointModuleNode(availBuilder, resolvedName)
				entryPoints.forEach { entryPoint ->
					val entryPointNode = EntryPointNode(
						availBuilder, resolvedName, entryPoint)
					moduleNode.add(entryPointNode)
				}
				mutex.safeWrite {
					moduleNodes.put(resolvedName.qualifiedName, moduleNode)
				}
			}
			after()
		}) {
			val entryPointsTreeRoot =
				DefaultMutableTreeNode("(entry points hidden root)")

			val mapKeys = moduleNodes.keys.toTypedArray()
			sort(mapKeys)
			mapKeys.forEach { moduleLabel ->
				entryPointsTreeRoot.add(moduleNodes[moduleLabel])
			}
			val enumeration: Enumeration<DefaultMutableTreeNode> =
				entryPointsTreeRoot.preorderEnumeration().cast()
			// Skip the invisible root.
			enumeration.nextElement()
			for (node in enumeration)
			{
				val strongNode: AbstractBuilderFrameTreeNode = node.cast()
				strongNode.sortChildren()
			}
			 withTreeNode(entryPointsTreeRoot)
		}
	}

	/**
	 * Answer the [path][TreePath] to the specified module name in the
	 * [module&#32;tree][moduleTree].
	 *
	 * @param moduleName
	 *   A module name.
	 * @return A tree path, or `null` if the module name is not present in
	 *   the tree.
	 */
	private fun modulePath(moduleName: String): TreePath?
	{
		val path = moduleName.split('/', '\\')
		if (path.size < 2 || path[0] != "")
		{
			// Module paths start with a slash, so we need at least 2 segments
			return null
		}
		val model = moduleTree.model
		val treeRoot = model.root as DefaultMutableTreeNode
		var nodes: Enumeration<DefaultMutableTreeNode> =
			treeRoot.children().cast()
		var index = 1
		while (nodes.hasMoreElements())
		{
			val node: AbstractBuilderFrameTreeNode = nodes.nextElement().cast()
			if (node.isSpecifiedByString(path[index]))
			{
				index++
				if (index == path.size)
				{
					return TreePath(node.path)
				}
				nodes = node.children().cast()
			}
		}
		return null
	}

	/**
	 * Answer the currently selected [module&#32;root][ModuleRootNode], or null.
	 *
	 * @return A [ModuleRootNode], or `null` if no module root is selected.
	 */
	private fun selectedModuleRootNode(): ModuleRootNode?
	{
		val path = moduleTree.selectionPath ?: return null
		return when (val selection = path.lastPathComponent)
		{
			is ModuleRootNode -> selection
			else -> null
		}
	}

	/**
	 * Answer the [ModuleRoot] that is currently selected, otherwise `null`.
	 *
	 * @return A [ModuleRoot], or `null` if no module root is selected.
	 */
	fun selectedModuleRoot(): ModuleRoot? =
		selectedModuleRootNode()?.moduleRoot

	/**
	 * Answer the currently selected [module&#32;node][ModuleOrPackageNode].
	 *
	 * @return A module node, or `null` if no module is selected.
	 */
	private fun selectedModuleNode(): ModuleOrPackageNode?
	{
		val path = moduleTree.selectionPath ?: return null
		return when (val selection = path.lastPathComponent)
		{
			is ModuleOrPackageNode -> selection
			else -> null
		}
	}

	/**
	 * Is the selected [module][ModuleDescriptor] loaded?
	 *
	 * @return `true` if the selected module is loaded, `false` if no module is
	 *   selected or the selected module is not loaded.
	 */
	private fun selectedModuleIsLoaded(): Boolean
	{
		val node = selectedModuleNode()
		return node !== null && node.isLoaded
	}

	/**
	 * Answer the [name][ResolvedModuleName] of the currently selected
	 * [module][ModuleDescriptor].
	 *
	 * @return A fully-qualified module name, or `null` if no module is
	 *   selected.
	 */
	fun selectedModule(): ResolvedModuleName? =
		selectedModuleNode()?.resolvedModuleName

	/**
	 * Answer the currently selected entry point, or `null` if none.
	 *
	 * @return An entry point name, or `null` if no entry point is selected.
	 */
	fun selectedEntryPoint(): String?
	{
		val path = entryPointsTree.selectionPath ?: return null
		return when (val selection = path.lastPathComponent)
		{
			is EntryPointNode -> selection.entryPointString
			else -> null
		}
	}

	/**
	 * Answer the resolved name of the module selected in the [entryPointsTree],
	 * or the module defining the entry point that's selected, or `null` if
	 * none.
	 *
	 * @return A [ResolvedModuleName] or `null`.
	 */
	fun selectedEntryPointModule(): ResolvedModuleName?
	{
		val path = entryPointsTree.selectionPath ?: return null
		return when (val selection = path.lastPathComponent)
		{
			is EntryPointNode -> selection.resolvedModuleName
			is EntryPointModuleNode -> selection.resolvedModuleName
			else -> null
		}
	}

	/**
	 * Ensure the new build position information will eventually be presented to
	 * the display.
	 *
	 * @param position
	 *   The global parse position, in bytes.
	 * @param globalCodeSize
	 *   The target number of bytes to parse.
	 */
	fun eventuallyUpdateBuildProgress(position: Long, globalCodeSize: Long)
	{
		buildGlobalUpdateLock.safeWrite {
			latestGlobalBuildPosition = position
			globalBuildLimit = globalCodeSize
			if (!hasQueuedGlobalBuildUpdate)
			{
				hasQueuedGlobalBuildUpdate = true
				availBuilder.runtime.timer.schedule(
					object : TimerTask()
					{
						override fun run()
						{
							invokeLater { updateBuildProgress() }
						}
					},
					100)
			}
		}
	}

	/**
	 * Update the [build&#32;progress&#32;bar][buildProgress].
	 */
	internal fun updateBuildProgress()
	{
		var position = 0L
		var max = 0L
		buildGlobalUpdateLock.safeWrite {
			assert(hasQueuedGlobalBuildUpdate)
			position = latestGlobalBuildPosition
			max = globalBuildLimit
			hasQueuedGlobalBuildUpdate = false
		}
		val perThousand = (position * 1000 / max).toInt()
		buildProgress.value = perThousand
		val percent = perThousand / 10.0f
		buildProgress.string = format(
			"Build Progress: %,d / %,d bytes (%3.1f%%)",
			position,
			max,
			percent)
	}

	/**
	 * Progress has been made at loading a module.  Ensure this is presented
	 * to the user in the near future.
	 *
	 * @param moduleName
	 *   The [ModuleName] being loaded.
	 * @param moduleSize
	 *   The size of the module in bytes.
	 * @param position
	 *   The byte position in the module at which loading has been achieved.
	 * @param line
	 *   The line number at which a top-level statement is being parsed, or
	 *   where the parsed statement being executed begins.  [Int.MAX_VALUE]
	 *   indicates a completed module.
	 */
	fun eventuallyUpdatePerModuleProgress(
		moduleName: ModuleName, moduleSize: Long, position: Long, line: Int)
	{
		perModuleProgressLock.safeWrite {
			if (position == moduleSize)
			{
				perModuleProgress.remove(moduleName)
			}
			else
			{
				perModuleProgress[moduleName] =
					Triple(position, moduleSize, line)
			}
			if (!hasQueuedPerModuleBuildUpdate)
			{
				hasQueuedPerModuleBuildUpdate = true
				availBuilder.runtime.timer.schedule(100) {
					invokeLater { updatePerModuleProgressInUIThread() }
				}
			}
		}
	}

	/**
	 * Update the visual presentation of the per-module statistics.  This must
	 * be invoked from within the UI dispatch thread,
	 */
	private fun updatePerModuleProgressInUIThread()
	{
		assert(EventQueue.isDispatchThread())
		val progress = perModuleProgressLock.safeWrite {
			assert(hasQueuedPerModuleBuildUpdate)
			hasQueuedPerModuleBuildUpdate = false
			perModuleProgress.entries.toMutableList()
		}
		progress.sortBy { it.key.qualifiedName }
		val count = progress.size
		val truncatedCount = max(0, count - maximumModulesInProgressReport)
		val truncatedProgress = progress.subList(0, count - truncatedCount)
		var string = truncatedProgress.joinToString("") { (key, triple) ->
			val (position, size, line) = triple
			val suffix = when (line)
			{
				Int.MAX_VALUE -> ""
				else -> ":$line"
			}
			format("%,6d / %,6d - %s%s%n", position, size, key, suffix)
		}
		if (truncatedCount > 0) string += "(and $truncatedCount more)\n"
		val doc = transcript.styledDocument
		try
		{
			val beforeRemove = System.nanoTime()
			doc.remove(0, perModuleStatusTextSize)
			// Always use index 0, since this only happens in the UI thread.
			removeStringStat.record(System.nanoTime() - beforeRemove)

			val beforeInsert = System.nanoTime()
			doc.insertString(
				0,
				string,
				BUILD_PROGRESS.styleIn(doc))
			// Always use index 0, since this only happens in the UI thread.
			insertStringStat.record(System.nanoTime() - beforeInsert)
		}
		catch (e: BadLocationException)
		{
			// Shouldn't happen.
			assert(false)
		}

		perModuleProgressLock.safeWrite {
			perModuleStatusTextSize = string.length
		}
	}

	/**
	 * Save the current [ModuleRoots] and rename rules to the preferences
	 * storage.
	 */
	fun saveModuleConfiguration()
	{
		try
		{
			val rootsNode = basePreferences.node(moduleRootsKeyString)
			val roots = resolver.moduleRoots
			rootsNode.childrenNames().forEach { oldChildName ->
				if (roots.moduleRootFor(oldChildName) === null)
				{
					rootsNode.node(oldChildName).removeNode()
				}
			}
			roots.forEach { root ->
				val childNode = rootsNode.node(root.name)
				childNode.put(
					moduleRootsSourceSubkeyString,
					root.resolver.uri.toString())
			}

			val renamesNode =
				basePreferences.node(moduleRenamesKeyString)
			val renames = resolver.renameRules
			renamesNode.childrenNames().forEach { oldChildName ->
				val nameInt = try
				{
					parseInt(oldChildName)
				}
				catch (e: NumberFormatException)
				{
					-1
				}

				if (oldChildName != nameInt.toString()
					|| nameInt < 0
					|| nameInt >= renames.size)
				{
					renamesNode.node(oldChildName).removeNode()
				}
			}
			var rowCounter = 0
			renames.forEach { (key, value) ->
				val childNode = renamesNode.node(rowCounter.toString())
				childNode.put(moduleRenameSourceSubkeyString, key)
				childNode.put(moduleRenameTargetSubkeyString, value)
				rowCounter++
			}
			basePreferences.flush()
		}
		catch (e: BackingStoreException)
		{
			System.err.println(
				"Unable to write Avail roots/renames preferences.")
		}
	}

	/**
	 * Write text to the transcript with the given [StreamStyle].
	 *
	 * @param text
	 *   The text to write.
	 * @param streamStyle
	 *   The style to write it in.
	 */
	fun writeText(text: String, streamStyle: StreamStyle)
	{
		val before = System.nanoTime()
		val size = text.length
		assert(size > 0)
		updateQueue.add(BuildOutputStreamEntry(streamStyle, text))
		val previous = totalQueuedTextSize.getAndAdd(size.toLong())
		if (previous == 0L)
		{
			// We transitioned from zero to positive.
			updateTranscript()
		}
		if (previous + size > maxDocumentSize + (maxDocumentSize shr 2))
		{
			// We're more than 125% capacity.  Discard old stuff that won't be
			// displayed because it would be rolled off anyhow.  Since this has
			// to happen within the dequeLock, it nicely blocks this writer
			// while whoever owns the lock does its own cleanup.
			val beforeLock = System.nanoTime()
			dequeLock.safeWrite {
				waitForDequeLockStat.record(System.nanoTime() - beforeLock)
				try
				{
					totalQueuedTextSize.getAndAdd(
						(-privateDiscardExcessLeadingQueuedUpdates()).toLong())
				}
				finally
				{
					// Record the stat just before unlocking, to avoid the need for
					// a lock for the statistic itself.
					writeTextStat.record(System.nanoTime() - before)
				}
			}
		}
	}

	private val mainSplit: JSplitPane

	private val leftPane: JSplitPane

	/**
	 * Get the existing preferences early for plugging in at the right times
	 * during construction.
	 */
	private val layoutConfiguration = LayoutConfiguration.initialConfiguration

	init
	{
		fileManager.associateRuntime(runtime)
		availBuilder = AvailBuilder(runtime)

		defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE

		// Set *just* the window title...
		title = "Avail Workbench"
		isResizable = true
		rootPane.isDoubleBuffered = true

		// Create the menu bar and its menus.
		val buildMenu = menu("Build")
		if (!runningOnMac)
		{
			augment(buildMenu, aboutAction, null)
		}
		augment(
			buildMenu,
			buildAction, cancelAction, null,
			unloadAction, unloadAllAction, cleanAction, null,
//			cleanModuleAction,  //TODO MvG Fix implementation and enable.
			refreshAction)
		val menuBar = JMenuBar()
		if (!runningOnMac)
		{
			augment(buildMenu, null, preferencesAction)
		}
		menuBar.add(buildMenu)
		menuBar.add(
			menu(
				"Document",
				documentAction,
				null,
				setDocumentationPathAction))
		menuBar.add(
			menu("Run", insertEntryPointAction, null, clearTranscriptAction))
		showCCReportAction = ShowCCReportAction(this, runtime)
		resetCCReportDataAction = ResetCCReportDataAction(this, runtime)
		parserIntegrityCheckAction = ParserIntegrityCheckAction(this, runtime)
		examineRepositoryAction = ExamineRepositoryAction(this, runtime)
		examineCompilationAction = ExamineCompilationAction(this, runtime)
		if (showDeveloperTools)
		{
			menuBar.add(
				menu(
					"Developer",
					showVMReportAction,
					resetVMReportDataAction,
					null,
					showCCReportAction,
					resetCCReportDataAction,
					null,
					JCheckBoxMenuItem(debugMacroExpansionsAction),
					JCheckBoxMenuItem(debugCompilerAction),
					JCheckBoxMenuItem(traceSummarizeStatementsAction),
					JCheckBoxMenuItem(traceLoadedStatementsAction),
					JCheckBoxMenuItem(toggleFastLoaderAction),
					null,
					JCheckBoxMenuItem(toggleDebugL1),
					JCheckBoxMenuItem(toggleDebugL2),
					JCheckBoxMenuItem(toggleL2SanityCheck),
					JCheckBoxMenuItem(toggleDebugPrimitives),
					JCheckBoxMenuItem(toggleDebugWorkUnits),
					null,
					JCheckBoxMenuItem(toggleDebugJVM),
					null,
					parserIntegrityCheckAction,
					examineRepositoryAction,
					examineCompilationAction,
					null,
					graphAction))
		}
		jMenuBar = menuBar

		// The refresh item needs a little help ...
		var inputMap = getRootPane().getInputMap(
			JComponent.WHEN_IN_FOCUSED_WINDOW)
		var actionMap = getRootPane().actionMap
		inputMap.put(KeyStroke.getKeyStroke("F5"), "refresh")
		actionMap.put("refresh", refreshAction)

		val transcriptPopup = menu("Transcript", clearTranscriptAction)

		// Create the module tree.
		moduleTree = JTree(
			DefaultMutableTreeNode("(packages hidden root)"))
		moduleTree.background = null
		moduleTree.toolTipText = "All modules, organized by module root."
		moduleTree.componentPopupMenu = buildMenu.popupMenu
		moduleTree.isEditable = false
		moduleTree.isEnabled = true
		moduleTree.isFocusable = true
		moduleTree.selectionModel.selectionMode =
			TreeSelectionModel.SINGLE_TREE_SELECTION
		moduleTree.toggleClickCount = 0
		moduleTree.showsRootHandles = true
		moduleTree.isRootVisible = false
		moduleTree.addTreeSelectionListener { setEnablements() }
		moduleTree.cellRenderer = treeRenderer
		moduleTree.addMouseListener(
			object : MouseAdapter()
			{
				override fun mouseClicked(e: MouseEvent)
				{
					if (buildAction.isEnabled
						&& e.clickCount == 2
						&& e.button == MouseEvent.BUTTON1)
					{
						e.consume()
						buildAction.actionPerformed(
							ActionEvent(moduleTree, -1, "Build"))
					}
				}
			})
		inputMap = moduleTree.getInputMap(
			JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
		actionMap = moduleTree.actionMap
		inputMap.put(KeyStroke.getKeyStroke("ENTER"), "build")
		actionMap.put("build", buildAction)
		// Expand rows bottom-to-top to expand only the root nodes.
		for (i in moduleTree.rowCount - 1 downTo 0)
		{
			moduleTree.expandRow(i)
		}

		// Create the entry points tree.
		entryPointsTree =
			JTree(DefaultMutableTreeNode("(entry points hidden root)"))
		moduleTree.background = null
		entryPointsTree.toolTipText =
			"All entry points, organized by defining module."
		entryPointsTree.isEditable = false
		entryPointsTree.isEnabled = true
		entryPointsTree.isFocusable = true
		entryPointsTree.selectionModel.selectionMode =
			TreeSelectionModel.SINGLE_TREE_SELECTION
		entryPointsTree.toggleClickCount = 0
		entryPointsTree.showsRootHandles = true
		entryPointsTree.isRootVisible = false
		entryPointsTree.addTreeSelectionListener { setEnablements() }
		entryPointsTree.cellRenderer = treeRenderer
		entryPointsTree.addMouseListener(
			object : MouseAdapter()
			{
				override fun mouseClicked(e: MouseEvent)
				{
					if (selectedEntryPoint() !== null)
					{
						if (insertEntryPointAction.isEnabled
							&& e.clickCount == 2
							&& e.button == MouseEvent.BUTTON1)
						{
							e.consume()
							val actionEvent = ActionEvent(
								entryPointsTree, -1, "Insert entry point")
							insertEntryPointAction.actionPerformed(actionEvent)
						}
					}
					else if (selectedEntryPointModule() !== null)
					{
						if (buildEntryPointModuleAction.isEnabled
							&& e.clickCount == 2
							&& e.button == MouseEvent.BUTTON1)
						{
							e.consume()
							val actionEvent = ActionEvent(
								entryPointsTree,
								-1,
								"Build entry point module")
							buildEntryPointModuleAction
								.actionPerformed(actionEvent)
						}
					}
				}
			})
		inputMap = entryPointsTree.getInputMap(
			JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
		actionMap = entryPointsTree.actionMap
		inputMap.put(KeyStroke.getKeyStroke("ENTER"), "build")
		actionMap.put("build", buildAction)
		for (i in 0 until entryPointsTree.rowCount)
		{
			entryPointsTree.expandRow(i)
		}

		// Create the build progress bar.
		buildProgress = JProgressBar(0, 1000)
		buildProgress.toolTipText = "Progress indicator for the build."
		buildProgress.isEnabled = false
		buildProgress.isFocusable = false
		buildProgress.isIndeterminate = false
		buildProgress.isStringPainted = true
		buildProgress.string = "Build Progress:"
		buildProgress.value = 0

		// Create the transcript.

		// Make this row and column be where the excess space goes.
		// And reset the weights...
		transcript = JTextPane()
		transcript.border = BorderFactory.createEtchedBorder()
		transcript.componentPopupMenu = transcriptPopup.popupMenu
		transcript.isEditable = false
		transcript.isEnabled = true
		transcript.isFocusable = true
		transcript.preferredSize = Dimension(0, 500)
		transcriptScrollArea = createScrollPane(transcript)

		// Create the input area.
		inputLabel = JLabel("Command:")
		inputField = JTextField()
		inputField.toolTipText =
			"Enter commands and interact with Avail programs.  Press " +
				"ENTER to submit."
		inputField.action = SubmitInputAction(this)
		inputMap = inputField.getInputMap(JComponent.WHEN_FOCUSED)
		actionMap = inputField.actionMap
		inputMap.put(KeyStroke.getKeyStroke("UP"), "up")
		actionMap.put("up", retrievePreviousAction)
		inputMap.put(KeyStroke.getKeyStroke("DOWN"), "down")
		actionMap.put("down", retrieveNextAction)
		inputField.columns = 60
		inputField.isEditable = true
		inputField.isEnabled = true
		inputField.isFocusable = true

		// Subscribe to module loading events.
		availBuilder.subscribeToModuleLoading { loadedModule, _ ->
			// Postpone repaints up to 250ms to avoid thrash.
			moduleTree.repaint(250)
			if (loadedModule.entryPoints.isNotEmpty())
			{
				// Postpone repaints up to 250ms to avoid thrash.
				entryPointsTree.repaint(250)
			}
		}

		// Set up styles for the transcript.
		val doc = transcript.styledDocument
		val tabStops = arrayOfNulls<TabStop>(500)
		tabStops.indices.forEach { i ->
			tabStops[i] = TabStop(
				32.0f * (i + 1),
				TabStop.ALIGN_LEFT,
				TabStop.LEAD_NONE)
		}
		val tabSet = TabSet(tabStops)
		val attributes = SimpleAttributeSet()
		StyleConstants.setTabSet(attributes, tabSet)
		doc.setParagraphAttributes(0, doc.length, attributes, false)
		val defaultStyle =
			StyleContext.getDefaultStyleContext()
				.getStyle(StyleContext.DEFAULT_STYLE)
		defaultStyle.addAttributes(attributes)
		StyleConstants.setFontFamily(defaultStyle, "Monospaced")
		StreamStyle.values().forEach { style -> style.defineStyleIn(doc) }

		// Redirect the standard streams.
		try
		{
			outputStream = BuildPrintStream(BuildOutputStream(this, OUT))
			errorStream = BuildPrintStream(BuildOutputStream(this, ERR))
		}
		catch (e: UnsupportedEncodingException)
		{
			// Java must support UTF_8.
			throw RuntimeException(e)
		}

		inputStream = BuildInputStream(this)
		System.setOut(outputStream)
		System.setErr(errorStream)
		System.setIn(inputStream)
		val textInterface =
			TextInterface(
				ConsoleInputChannel(inputStream),
				ConsoleOutputChannel(outputStream),
				ConsoleOutputChannel(errorStream))
		runtime.setTextInterface(textInterface)
		availBuilder.textInterface = textInterface

		leftPane = JSplitPane(
			JSplitPane.VERTICAL_SPLIT,
			true,
			createScrollPane(moduleTree),
			createScrollPane(entryPointsTree))
		leftPane.setDividerLocation(
			layoutConfiguration.moduleVerticalProportion())
		leftPane.resizeWeight =
			layoutConfiguration.moduleVerticalProportion()
		leftPane.addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY) {
			saveWindowPosition()
		}
		val rightPane = JPanel()
		val rightPaneLayout = GroupLayout(rightPane)
		rightPane.layout = rightPaneLayout
		rightPaneLayout.autoCreateGaps = true
		val outputLabel = JLabel("Transcript:")
		rightPaneLayout.setHorizontalGroup(
			rightPaneLayout.createParallelGroup()
				.addComponent(buildProgress)
				.addComponent(outputLabel)
				.addComponent(transcriptScrollArea)
				.addComponent(inputLabel)
				.addComponent(inputField))
		rightPaneLayout.setVerticalGroup(
			rightPaneLayout.createSequentialGroup()
				.addGroup(
					rightPaneLayout.createSequentialGroup()
						.addComponent(
							buildProgress,
							GroupLayout.PREFERRED_SIZE,
							GroupLayout.DEFAULT_SIZE,
							GroupLayout.PREFERRED_SIZE))
				.addGroup(
					rightPaneLayout.createSequentialGroup()
						.addComponent(outputLabel)
						.addComponent(
							transcriptScrollArea,
							0,
							300,
							Short.MAX_VALUE.toInt()))
				.addGroup(
					rightPaneLayout.createSequentialGroup()
						.addComponent(inputLabel)
						.addComponent(
							inputField,
							GroupLayout.PREFERRED_SIZE,
							GroupLayout.DEFAULT_SIZE,
							GroupLayout.PREFERRED_SIZE)))

		mainSplit = JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT, true, leftPane, rightPane)
		mainSplit.dividerLocation = layoutConfiguration.leftSectionWidth()
		mainSplit.addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY) {
			saveWindowPosition()
		}
		contentPane.add(mainSplit)
		pack()
		layoutConfiguration.placement?.let { bounds = it }
		extendedState = layoutConfiguration.extendedState

		// Save placement during resizes and moves.
		addComponentListener(object : ComponentAdapter()
		{
			override fun componentResized(e: ComponentEvent?)
			{
				saveWindowPosition()
			}

			override fun componentMoved(e: ComponentEvent?)
			{
				saveWindowPosition()
			}
		})
		if (runningOnMac)
		{
			OSXUtility.setQuitHandler {
				// Quit was pressed.  Close the workbench, which should
				// save window position state then exit.
				// Apple's apple.eawt.quitStrategy has never worked, to
				// the best of my knowledge.  It's a trick.  We must
				// close the workbench window explicitly to give it a
				// chance to save.
				val closeEvent = WindowEvent(
					this@AvailWorkbench,
					WindowEvent.WINDOW_CLOSING)
				dispatchEvent(closeEvent)
				true
			}
			OSXUtility.setAboutHandler {
				aboutAction.showDialog()
				true
			}
		}

		// Select an initial module if specified.
		validate()
		setEnablements()
	}

	private fun saveWindowPosition()
	{
		layoutConfiguration.extendedState = extendedState
		if (extendedState == NORMAL)
		{
			// Only capture the bounds if it's not zoomed or minimized.
			layoutConfiguration.placement = bounds
		}
		if (extendedState != ICONIFIED)
		{
			// Capture the divider positions if it's not minimized.
			layoutConfiguration.leftSectionWidth = mainSplit.dividerLocation
			layoutConfiguration.moduleVerticalProportion =
				leftPane.dividerLocation / max(leftPane.height.toDouble(), 1.0)
		}
		layoutConfiguration.saveWindowPosition()
	}

	/**
	 * Make the workbench instance behave more like a Mac application.
	 */
	private fun setUpInstanceForMac()
	{
		assert(runningOnMac)
		try
		{
			// Set up Mac-specific preferences menu handler...
			OSXUtility.setPreferencesHandler {
				preferencesAction.actionPerformed(null)
				true
			}
		}
		catch (e: Exception)
		{
			throw RuntimeException(e)
		}
	}

	/** Perform the first refresh of the workbench. */
	private fun initialRefresh(
		failedResolutions: MutableList<String>,
		resolutionTime: Long,
		initial: String,
		afterExecute: ()->Unit)
	{
		if (failedResolutions.isEmpty())
		{
			writeText(
				format("Resolved all module roots in %,3dms\n", resolutionTime),
				INFO)
		}
		else
		{
			writeText(
				format(
					"Resolved module roots in %,3dms with failures:\n",
					resolutionTime),
				INFO)
			failedResolutions.forEach {
				writeText(format("%s\n", it), INFO)
			}
		}
		// First refresh the module and entry point trees.
		writeText("Scanning all module headers...\n", INFO)
		val before = currentTimeMillis()
		calculateRefreshedTreesThen { modules, entryPoints ->
			val after = currentTimeMillis()
			writeText(format("...done (%,3dms)\n", after - before), INFO)
			// Now select an initial module, if specified.
			refreshFor(modules, entryPoints)
			if (initial.isNotEmpty())
			{
				val path = modulePath(initial)
				if (path !== null)
				{
					moduleTree.selectionPath = path
					moduleTree.scrollRowToVisible(
						moduleTree.getRowForPath(path))
				}
				else
				{
					writeText(
						"Command line argument '$initial' was "
							+ "not a valid module path",
						ERR)
				}
			}
			backgroundTask = null
			setEnablements()
			afterExecute()
			ignoreRepaint = false
			repaint()
			isVisible = true
		}
	}

	companion object
	{
		/**
		 * Truncate progress reports containing more than this number of
		 * individual modules in progress.
		 */
		private const val maximumModulesInProgressReport = 20

		/** Determine at startup whether we're on a Mac. */
		val runningOnMac =
			System
				.getProperty("os.name")
				.lowercase()
				.matches("mac os x.*".toRegex())

		/** Determine at startup whether we should show developer commands. */
		val showDeveloperTools =
			"true".equals(
				System.getProperty("availDeveloper"), ignoreCase = true)

		/**
		 * An indicator of whether to show the user interface in dark (Darcula)
		 * mode.
		 */
		val darkMode: Boolean =
			System.getProperty("darkMode")?.equals("true") ?: true

		/**
		 * The background color of the input field when a command is running.
		 */
		val inputBackgroundWhenRunning = AdaptiveColor(
			light = Color(192, 255, 192),
			dark = Color(55, 156, 26))

		/**
		 * The foreground color of the input field when a command is running.
		 */
		val inputForegroundWhenRunning = AdaptiveColor(
			light = Color.black,
			dark = Color.white)

		/**
		 * The numeric mask for the modifier key suitable for the current
		 * platform.
		 */
		val menuShortcutMask = Toolkit.getDefaultToolkit().menuShortcutKeyMask

		/**
		 * The current working directory of the Avail virtual machine. Because
		 * Java does not permit the current working directory to be changed, it
		 * is safe to cache the answer at class-loading time.
		 */
		private val currentWorkingDirectory: File

		// Obtain the current working directory. Try to resolve this location to its
		// real path. If resolution fails, then just use the value of the "user.dir"
		// system property.
		init
		{
			val userDir = System.getProperty("user.dir")
			val path = FileSystems.getDefault().getPath(userDir)

			currentWorkingDirectory = File(
				try
				{
					path.toRealPath().toString()
				}
				catch (e: IOException)
				{
					userDir
				}
				catch (e: SecurityException)
				{
					userDir
				})
		}

		/** Truncate the start of the document any time it exceeds this. */
		private const val maxDocumentSize = 10_000_000

		/** The [Statistic] for tracking text insertions. */
		private val insertStringStat =
			Statistic(WORKBENCH_TRANSCRIPT, "Insert string")

		/** The [Statistic] for tracking text deletions. */
		private val removeStringStat =
			Statistic(WORKBENCH_TRANSCRIPT, "Remove string")

		/**
		 * Parse the [ModuleRoots] from the module roots preferences node.
		 *
		 * @param fileManager
		 *   The [FileManager] used to manage Avail files.
		 * @return The `ModuleRoots` constructed from the preferences node.
		 */
		private fun loadModuleRoots(fileManager: FileManager): ModuleRoots
		{
			val outerSemaphore = Semaphore(0)
			val roots = ModuleRoots(fileManager, "") {
				outerSemaphore.release()
			}
			outerSemaphore.acquireUninterruptibly()
			roots.clearRoots()
			val node = basePreferences.node(moduleRootsKeyString)
			try
			{
				val childNames = node.childrenNames()
				childNames.forEach { childName ->
					val childNode = node.node(childName)
					val sourceName = childNode.get(
						moduleRootsSourceSubkeyString, "")
					val resolver =
						if (sourceName.isEmpty())
						{
							RuntimeException(
								"ModuleRoot, $childName, is missing a source URI"
							).printStackTrace()
							return@forEach
						}
						else
						{
							val uri = URI(sourceName)
							ModuleRootResolverRegistry.createResolver(
								childName, uri, fileManager)
						}
					roots.addRoot(ModuleRoot(childName, resolver))
				}
			}
			catch (e: BackingStoreException)
			{
				System.err.println("Unable to read Avail roots preferences.")
			}
			return roots
		}

		/**
		 * Parse the [ModuleRoots] from the module roots preferences node.
		 *
		 * @param resolver
		 *   The [ModuleNameResolver] used for resolving module names.
		 */
		private fun loadRenameRulesInto(resolver: ModuleNameResolver)
		{
			resolver.clearRenameRules()
			val node = basePreferences.node(moduleRenamesKeyString)
			try
			{
				val childNames = node.childrenNames()
				childNames.forEach { childName ->
					val childNode = node.node(childName)
					val source = childNode.get(
						moduleRenameSourceSubkeyString, "")
					val target = childNode.get(
						moduleRenameTargetSubkeyString, "")
					// Ignore empty sources and targets, although they shouldn't
					// occur.
					if (source.isNotEmpty() && target.isNotEmpty())
					{
						resolver.addRenameRule(source, target)
					}
				}
			}
			catch (e: BackingStoreException)
			{
				System.err.println(
					"Unable to read Avail rename rule preferences.")
			}
		}

		/** Statistic for waiting for updateQueue's monitor. */
		internal val waitForDequeLockStat = Statistic(
			WORKBENCH_TRANSCRIPT, "Wait for lock to trim old entries")

		/** Statistic for trimming excess leading entries. */
		internal val discardExcessLeadingStat = Statistic(
			WORKBENCH_TRANSCRIPT, "Trim old entries (not counting lock)")

		/**
		 * Statistic for invoking writeText, including waiting for the monitor.
		 */
		internal val writeTextStat =
			Statistic(WORKBENCH_TRANSCRIPT, "Call writeText")

		/**
		 * The [DefaultTreeCellRenderer] that knows how to render tree nodes for
		 * my [moduleTree] and my [entryPointsTree].
		 */
		private val treeRenderer = object : DefaultTreeCellRenderer()
		{
			override fun getTreeCellRendererComponent(
				tree: JTree,
				value: Any?,
				selected: Boolean,
				expanded: Boolean,
				leaf: Boolean,
				row: Int,
				hasFocus: Boolean
			): Component
			{
				return when (value)
				{
					is AbstractBuilderFrameTreeNode ->
					{
						val icon = value.icon(tree.rowHeight)
						setLeafIcon(icon)
						setOpenIcon(icon)
						setClosedIcon(icon)
						var html = value.htmlText(selected)
						html = "<html>$html</html>"
						super.getTreeCellRendererComponent(
							tree, html, selected, expanded, leaf, row, hasFocus)
					}
					else -> return super.getTreeCellRendererComponent(
						tree, value, selected, expanded, leaf, row, hasFocus)
				}.apply {
					if (darkMode)
					{
						// Fully transparent.
						backgroundNonSelectionColor = Color(45, 45, 45, 0)
					}
				}
			}
		}

		/**
		 * Create a menu with the given name and entries, which can be null to
		 * indicate a separator, a JMenuItem, or an Action to wrap in a
		 * JMenuItem.
		 *
		 * @param name
		 *   The name of the menu to create.
		 * @param actionsAndSubmenus
		 *   A varargs array of [Action]s, [JMenuItem]s for submenus, and
		 *   `null`s for separator lines.
		 * @return A new [JMenu].
		 */
		private fun menu(name: String, vararg actionsAndSubmenus: Any?): JMenu
		{
			val menu = JMenu(name)
			augment(menu, *actionsAndSubmenus)
			return menu
		}

		/**
		 * Augment the given menu with the array of entries, which can be null
		 * to indicate a separator, a JMenuItem, or an Action to wrap in a
		 * JMenuItem.
		 *
		 * @param menu
		 *   A [JMenu] to add items to.
		 * @param actionsAndSubmenus
		 *   A varargs array of [Action]s, [JMenuItem]s for submenus, and
		 *   `null`s for separator lines.
		 */
		private fun augment(menu: JMenu, vararg actionsAndSubmenus: Any?)
		{
			actionsAndSubmenus.forEach { item ->
				when (item)
				{
					null -> menu.addSeparator()
					is Action -> menu.add(item)
					is JMenuItem -> menu.add(item)
					else -> assert(false) {
						"Bad argument while building menu"
					}
				}
			}
		}

		/**
		 * Answer the pane wrapped in a JScrollPane.
		 *
		 * @param innerComponent
		 * The [Component] to be wrapped with scrolling capability.
		 * @return The new [JScrollPane].
		 */
		private fun createScrollPane(innerComponent: Component): JScrollPane =
			JScrollPane().apply {
				horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED
				verticalScrollBarPolicy = VERTICAL_SCROLLBAR_ALWAYS
				minimumSize = Dimension(100, 50)
				setViewportView(innerComponent)
			}

		/**
		 * Make the workbench behave more like a Mac application.
		 */
		private fun setUpForMac()
		{
			assert(runningOnMac)
			try
			{
				System.setProperty("apple.laf.useScreenMenuBar", "true")
				System.setProperty(
					"com.apple.mrj.application.apple.menu.about.name",
					"Avail Workbench")
				System.setProperty(
					"com.apple.awt.graphics.UseQuartz",
					"true")
				val application = OSXUtility.macOSXApplication
				OSXUtility.setDockIconBadgeMethod(
					application, activeVersionSummary)
			}
			catch (e: Exception)
			{
				throw RuntimeException(e)
			}
		}

		/**
		 * Launch the [Avail&#32;builder][AvailBuilder] [UI][AvailWorkbench].
		 *
		 * @param args
		 * The command line arguments.
		 * @throws Exception
		 * If something goes wrong.
		 */
		@Throws(Exception::class)
		@JvmStatic
		fun main(args: Array<String>)
		{
			val fileManager = FileManager()
			// Do the slow Swing setup in parallel with other things...
			val swingReady = Semaphore(0)
			val runtimeReady = Semaphore(0)
			thread(name = "Set up LAF") {
				if (runningOnMac)
				{
					setUpForMac()
				}
				if (darkMode)
				{
					LafManager.install(DarculaTheme())
				}
				swingReady.release()
			}
			val rootResolutionStart = currentTimeMillis()
			val failedResolutions = mutableListOf<String>()
			val rootsString = System.getProperty("availRoots", "")
			val roots = when
			{
				// Read the persistent preferences file...
				rootsString.isEmpty() -> loadModuleRoots(fileManager)
				// Providing availRoots on command line overrides preferences...
				else ->
				{
					val semaphore = Semaphore(0)
					val roots = ModuleRoots(fileManager, rootsString) { fails ->
						if (fails.isNotEmpty())
						{
							failedResolutions.addAll(fails)
						}
						semaphore.release()
					}
					semaphore.acquireUninterruptibly()
					roots
				}
			}
			val resolutionTime = currentTimeMillis() - rootResolutionStart
			lateinit var resolver: ModuleNameResolver
			lateinit var runtime: AvailRuntime
			thread(name = "Parse renames") {
				var reader: Reader? = null
				try
				{
					val renames = System.getProperty("availRenames", null)
					reader = when (renames)
					{
						// Load the renames from preferences further down.
						null -> StringReader("")
						// Load renames from file specified on the command line...
						else -> BufferedReader(
							InputStreamReader(
								FileInputStream(File(renames)),
								UTF_8))
					}
					val renameParser = RenamesFileParser(reader, roots)
					resolver = renameParser.parse()
					if (renames === null)
					{
						// Now load the rename rules from preferences.
						loadRenameRulesInto(resolver)
					}
				}
				finally
				{
					IO.closeIfNotNull(reader)
				}
				runtime = AvailRuntime(resolver, fileManager)
				runtimeReady.release()
			}

			runtimeReady.acquire()
			swingReady.acquire()

			// Display the UI.
			val bench = AvailWorkbench(runtime, fileManager, resolver)
			bench.createBufferStrategy(2)
			bench.ignoreRepaint = true
			if (runningOnMac)
			{
				bench.setUpInstanceForMac()
			}
			invokeLater {
				val initialRefreshTask =
					object : AbstractWorkbenchTask(bench, null)
					{
						override fun executeTaskThen(afterExecute: ()->Unit) =
							bench.initialRefresh(
								failedResolutions,
								resolutionTime,
								if (args.isNotEmpty()) args[0] else "",
								afterExecute)
					}
				bench.backgroundTask = initialRefreshTask
				bench.setEnablements()
				resolver.moduleRoots.roots.forEach {
					it.resolver.subscribeRootWatcher { event, _ ->
						when (event)
						{
							ModuleRootResolver.WatchEventType.CREATE,
							ModuleRootResolver.WatchEventType.MODIFY,
							ModuleRootResolver.WatchEventType.DELETE ->
								bench.refreshAction.runAction()
						}
					}
				}
				initialRefreshTask.execute()
			}
		}
	}
}
