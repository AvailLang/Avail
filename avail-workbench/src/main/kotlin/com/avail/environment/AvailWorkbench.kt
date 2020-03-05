/*
 * AvailWorkbench.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
import com.avail.builder.*
import com.avail.descriptor.A_Module
import com.avail.descriptor.ModuleDescriptor
import com.avail.environment.AvailWorkbench.StreamStyle.*
import com.avail.environment.actions.*
import com.avail.environment.nodes.*
import com.avail.environment.tasks.BuildTask
import com.avail.io.ConsoleInputChannel
import com.avail.io.ConsoleOutputChannel
import com.avail.io.TextInterface
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport.WORKBENCH_TRANSCRIPT
import com.avail.stacks.StacksGenerator
import com.avail.utility.*
import com.avail.utility.Casts.cast
import com.avail.utility.Locks.lockWhile
import com.bulenkov.darcula.DarculaLaf
import java.awt.*
import java.awt.event.*
import java.io.*
import java.lang.Integer.parseInt
import java.lang.String.format
import java.lang.System.arraycopy
import java.lang.System.currentTimeMillis
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.Comparator.comparing
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import javax.swing.*
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
import javax.swing.SwingUtilities.invokeLater
import javax.swing.text.*
import javax.swing.tree.*
import kotlin.collections.Map.Entry
import kotlin.math.max
import kotlin.math.min

/**
 * `AvailWorkbench` is a simple user interface for the
 * [Avail builder][AvailBuilder].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property resolver
 *   The [module name resolver][ModuleNameResolver].
 *
 * @constructor
 * Construct a new `AvailWorkbench`.
 *
 * @param resolver
 *   The [module name resolver][ModuleNameResolver].
 */
class AvailWorkbench internal constructor (val resolver: ModuleNameResolver)
	: JFrame()
{
	/**
	 * The [StyledDocument] into which to write both error and regular
	 * output.  Lazily initialized.
	 */
	private val document: StyledDocument by lazy { transcript.styledDocument }

	/** The last moment (ms) that a UI update of the transcript completed.  */
	private val lastTranscriptUpdateCompleted = AtomicLong(0L)

	/**
	 * A [List] of [BuildOutputStreamEntry](s), each of which holds a style and
	 * a [String].  The [totalQueuedTextSize] must be updated after an add to
	 * this queue, or before a remove from this queue. This ensures that the
	 * queue contains at least as many characters as the counter indicates,
	 * although it can be more.  Additionally, to allow each enqueuer to also
	 * deque surplus entries, the [dequeLock] must be held whenever removing
	 * entries from the queue.
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
	private val dequeLock = ReentrantReadWriteLock(false).writeLock()

	/** The current [background task][AbstractWorkbenchTask].  */
	@Volatile
	var backgroundTask: AbstractWorkbenchTask? = null

	/**
	 * The documentation [path][Path] for the
	 * [Stacks generator][StacksGenerator].
	 */
	var documentationPath: Path = StacksGenerator.defaultDocumentationPath

	/** The [standard input stream][BuildInputStream].  */
	private val inputStream: BuildInputStream?

	/** The [standard error stream][PrintStream].  */
	private val errorStream: PrintStream?

	/** The [standard output stream][PrintStream].  */
	val outputStream: PrintStream

	/* UI components. */

	/** The [module][ModuleDescriptor] [tree][JTree].  */
	val moduleTree: JTree

	/**
	 * The [tree][JTree] of module [entry points][A_Module.entryPoints].
	 */
	val entryPointsTree: JTree

	/**
	 * The [AvailBuilder] used by this user interface.
	 */
	val availBuilder: AvailBuilder

	/**
	 * The [progress bar][JProgressBar] that displays the overall build progress.
	 */
	val buildProgress: JProgressBar

	/**
	 * The [text area][JTextPane] that displays the [build][AvailBuilder]
	 * transcript.
	 */
	val transcript: JTextPane

	/** The [scroll bars][JScrollPane] for the [transcript].  */
	private val transcriptScrollArea: JScrollPane

	/**
	 * The [label][JLabel] that describes the current function of the
	 * [input field][inputField].
	 */
	private val inputLabel: JLabel

	/** The [text field][JTextField] that accepts standard input.  */
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

	/** Cycle one step backward in the command history.  */
	private val retrievePreviousAction = RetrievePreviousCommand(this)

	/** Cycle one step forward in the command history.  */
	private val retrieveNextAction = RetrieveNextCommand(this)

	/* Actions. */

	/** The [refresh action][RefreshAction].  */
	private val refreshAction = RefreshAction(this)

	/** The [&quot;about Avail&quot; action][AboutAction].  */
	private val aboutAction = AboutAction(this)

	/** The [&quot;Preferences...&quot; action][PreferencesAction].  */
	private val preferencesAction = PreferencesAction(this)

	/** The [build action][BuildAction].  */
	internal val buildAction = BuildAction(this, false)

	/** The [unload action][UnloadAction].  */
	private val unloadAction = UnloadAction(this)

	/** The [unload-all action][UnloadAllAction].  */
	private val unloadAllAction = UnloadAllAction(this)

	/** The [cancel action][CancelAction].  */
	private val cancelAction = CancelAction(this)

	/** The [clean action][CleanAction].  */
	private val cleanAction = CleanAction(this)

	/** The [clean module action][CleanModuleAction].  */
	private val cleanModuleAction = CleanModuleAction(this)

	/** The [create program action][CreateProgramAction].  */
	private val createProgramAction = CreateProgramAction(this)

	/**
	 * The [generate documentation action][GenerateDocumentationAction].
	 */
	private val documentAction = GenerateDocumentationAction(this)

	/** The [generate graph action][GenerateGraphAction].  */
	private val graphAction = GenerateGraphAction(this)

	/**
	 * The [documentation path dialog action][SetDocumentationPathAction].
	 */
	private val setDocumentationPathAction = SetDocumentationPathAction(this)

	/** The [show VM report action][ShowVMReportAction].  */
	private val showVMReportAction = ShowVMReportAction(this)

	/** The [reset VM report data action][ResetVMReportDataAction].  */
	private val resetVMReportDataAction = ResetVMReportDataAction(this)

	/** The [show CC report action][ShowCCReportAction].  */
	private val showCCReportAction: ShowCCReportAction

	/** The [reset CC report data action][ResetCCReportDataAction].  */
	private val resetCCReportDataAction: ResetCCReportDataAction

	/** The [toggle trace macros action][TraceMacrosAction].  */
	private val debugMacroExpansionsAction = TraceMacrosAction(this)

	/** The [toggle trace compiler action][TraceCompilerAction].  */
	private val debugCompilerAction = TraceCompilerAction(this)

	/** The [toggle fast-loader action][ToggleFastLoaderAction].  */
	private val toggleFastLoaderAction = ToggleFastLoaderAction(this)

	/** The [toggle L1 debug action][ToggleDebugInterpreterL1].  */
	private val toggleDebugL1 = ToggleDebugInterpreterL1(this)

	/** The [toggle L2 debug action][ToggleDebugInterpreterL2].  */
	private val toggleDebugL2 = ToggleDebugInterpreterL2(this)

	/** The [ToggleL2SanityCheck] toggle L2 sanity checks action}.  */
	private val toggleL2SanityCheck = ToggleL2SanityCheck(this)

	/**
	 * The [toggle primitive debug action][ToggleDebugInterpreterPrimitives].
	 */
	private val toggleDebugPrimitives =
		ToggleDebugInterpreterPrimitives(this)

	/**
	 * The [toggle work-units debug action][ToggleDebugWorkUnits].
	 */
	private val toggleDebugWorkUnits = ToggleDebugWorkUnits(this)

	/** The [toggle JVM dump debug action][ToggleDebugJVM].  */
	private val toggleDebugJVM = ToggleDebugJVM(this)

	/**
	 * The [toggle fast-loader summarization action][TraceSummarizeStatementsAction].
	 */
	private val traceSummarizeStatementsAction =
		TraceSummarizeStatementsAction(this)

	/**
	 * The [toggle load-tracing action][TraceLoadedStatementsAction].
	 */
	private val traceLoadedStatementsAction =
		TraceLoadedStatementsAction(this)

	/** The [ParserIntegrityCheckAction].  */
	private val parserIntegrityCheckAction: ParserIntegrityCheckAction

	/** The [ExamineRepositoryAction].  */
	private val examineRepositoryAction: ExamineRepositoryAction

	/** The [ExamineCompilationAction].  */
	private val examineCompilationAction: ExamineCompilationAction

	/** The [clear transcript action][ClearTranscriptAction].  */
	private val clearTranscriptAction = ClearTranscriptAction(this)

	/** The [insert entry point action][InsertEntryPointAction].  */
	internal val insertEntryPointAction = InsertEntryPointAction(this)

	/** The [action to build an entry point module][BuildAction].  */
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

	/** Whether an entry point invocation (command line) is executing.  */
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

	/** A monitor to protect updates to the per module progress.  */
	private val perModuleProgressLock = ReentrantReadWriteLock()

	/**
	 * The progress map per module.  Protected by [perModuleProgressLock].
	 */
	//@GuardedBy("perModuleProgressLock")
	private val perModuleProgress = HashMap<ModuleName, Pair<Long, Long>>()

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
	abstract class AbstractWorkbenchTask constructor (
			val workbench: AvailWorkbench,
			protected val targetModuleName: ResolvedModuleName?)
		: SwingWorker<Void, Void>()
	{
		/** The start time.  */
		private var startTimeMillis: Long = 0

		/** The stop time.  */
		private var stopTimeMillis: Long = 0

		/** The [exception][Throwable] that terminated the build.  */
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
			status = when {
				t != null -> "Aborted (${t.javaClass.simpleName})"
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
			startTimeMillis = currentTimeMillis()
			try
			{
				// Reopen the repositories if necessary.
				for (root in workbench.resolver.moduleRoots.roots)
				{
					root.repository.reopenIfNecessary()
				}
				executeTask()
				return null
			}
			finally
			{
				// Close all the repositories.
				for (root in workbench.resolver.moduleRoots.roots)
				{
					root.repository.close()
				}
				stopTimeMillis = currentTimeMillis()
			}
		}

		/**
		 * Execute this `AbstractWorkbenchTask`.
		 *
		 * @throws Exception If anything goes wrong.
		 */
		@Throws(Exception::class)
		protected abstract fun executeTask()
	}

	/**
	 * A singular write to an output stream.  This write is considered atomic
	 * with respect to writes from other threads, and will not have content from
	 * other writes interspersed with its characters.
	 *
	 * @property style
	 *   The [StreamStyle] with which to render the string.
	 * @property string
	 *   The [String] to output.
	 *
	 * @constructor
	 * Create an entry that captures a [StreamStyle] and [String] to output.
	 *
	 * @param style
	 *   The [StreamStyle] with which to render the string.
	 * @param string
	 *   The [String] to output.
	 */
	internal class BuildOutputStreamEntry constructor (
		val style: StreamStyle, val string: String)

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
						invokeLater { this@AvailWorkbench.privateUpdateTranscriptInUIThread() }
					}
				},
				max(0, now - lastTranscriptUpdateCompleted.get()))
		}
	}

	/**
	 * Discard entries from the [updateQueue] without updating the
	 * [totalQueuedTextSize] until no more can be discarded.  The [dequeLock]
	 * must be acquired before calling this.  The caller should decrease the
	 * [totalQueuedTextSize] by the returned amount before releasing the
	 * [dequeLock].
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
			assert(dequeLock.isHeldByCurrentThread)
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
	internal fun privateUpdateTranscriptInUIThread()
	{
		assert(EventQueue.isDispatchThread())
		// Hold the dequeLock just long enough to extract all entries, only
		// decreasing totalQueuedTextSize just before unlocking.
		val lengthToInsert = MutableInt(0)
		val aggregatedEntries = ArrayList<BuildOutputStreamEntry>()
		val wentToZero = lockWhile<Boolean>(
			dequeLock
		) {
			var removedSize = privateDiscardExcessLeadingQueuedUpdates()
			var currentStyle: StreamStyle? = null
			val builder = StringBuilder()
			while (true)
			{
				val entry = if (removedSize < totalQueuedTextSize.get())
					updateQueue.poll()!!
				else
					null
				if (entry == null || entry.style != currentStyle)
				{
					// Either the final entry or a style change.
					if (currentStyle != null)
					{
						val string = builder.toString()
						aggregatedEntries.add(
							BuildOutputStreamEntry(
								currentStyle, string))
						lengthToInsert.value += string.length
						builder.setLength(0)
					}
					if (entry == null)
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
			val afterRemove = totalQueuedTextSize.addAndGet(cast(-removedSize))
			assert(afterRemove >= 0)
			afterRemove == 0L
		}

		assert(aggregatedEntries.isNotEmpty())
		assert(lengthToInsert.value > 0)
		try
		{
			val statusSize = perModuleStatusTextSize
			val length = document.length
			val amountToRemove =
				length - statusSize + lengthToInsert.value - maxDocumentSize
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
			for (entry in aggregatedEntries)
			{
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
		private val dark: Color
	) {
		val color: Color get() = if (darkMode) dark else light

		val hex: String get() = with(color) {
			format("#%02x%02x%02x", red, green, blue)
		}
	}

	/**
	 * An abstraction of the styles of streams used by the workbench.
	 *
	 * @property styleName
	 *   The name of this style.
	 * @constructor
	 * Construct a new `StreamStyle`.
	 *
	 * @param light
	 *   The color of foreground text in this style for light mode.
	 * @param dark
	 *   The color of foreground text in this style for dark mode.
	 */
	enum class StreamStyle constructor(
		private val styleName: String,
		light: Color,
		dark: Color)
	{
		/** The stream style used to echo user input.  */
		IN_ECHO(
			"input",
			light = Color(32, 144, 32),
			dark = Color(55, 156, 26)),

		/** The stream style used to display normal output.  */
		OUT(
			"output",
			light = Color.BLACK,
			dark = Color(238, 238, 238)),

		/** The stream style used to display error output.  */
		ERR(
			"error",
			light = Color.RED,
			dark = Color(231, 70, 68)),

		/** The stream style used to display informational text.  */
		INFO(
			"info",
			light = Color.BLUE,
			dark = Color(83, 148, 236)),

		/** The stream style used to echo commands.  */
		COMMAND(
			"command",
			light = Color.MAGENTA,
			dark = Color(174, 138, 190)),

		/** Progress updates produced by a build.  */
		BUILD_PROGRESS(
			"build",
			light = Color(128, 96, 0),
			dark = Color(220, 196, 87));

		/** Combine the light and dark into an AdaptiveColor. */
		private val adaptiveColor = AdaptiveColor(light, dark)

		/**
		 * Create my corresponding [Style] in the [StyledDocument].
		 *
		 * @param doc
		 * The document in which to define this style.
		 */
		internal fun defineStyleIn(doc: StyledDocument)
		{
			val defaultStyle =
				StyleContext.getDefaultStyleContext().getStyle(
				StyleContext.DEFAULT_STYLE)
			val style = doc.addStyle(styleName, defaultStyle)
			StyleConstants.setForeground(style, adaptiveColor.color)
		}

		/**
		 * Extract this style from the given [document][StyledDocument].
		 * Look up my [styleName].
		 *
		 * @param doc
		 *   The document.
		 * @return The [Style].
		 */
		fun styleIn(doc: StyledDocument): Style
		{
			return doc.getStyle(styleName)
		}
	}

	/**
	 * [BuildOutputStream] intercepts writes and updates the UI's
	 * [transcript].
	 *
	 * @property streamStyle
	 *   What [StreamStyle] should this stream render with?
	 * @constructor
	 * Construct a new `BuildOutputStream`.
	 *
	 * @param streamStyle
	 *   What [StreamStyle] should this stream render with?
	 */
	private inner class BuildOutputStream internal constructor(
		internal val streamStyle: StreamStyle) : ByteArrayOutputStream(1)
	{
		/**
		 * Transfer any data in my buffer into the updateQueue, starting up a UI
		 * task to transfer them to the document as needed.
		 */
		private fun queueForTranscript()
		{
			assert(Thread.holdsLock(this))
			val text: String
			try
			{
				text = toString(StandardCharsets.UTF_8.name())
			}
			catch (e: UnsupportedEncodingException)
			{
				assert(false) { "Somehow Java doesn't support characters" }
				throw RuntimeException(e)
			}

			if (text.isEmpty())
			{
				// Nothing new to display.
				return
			}
			reset()
			writeText(text, streamStyle)
		}

		@Synchronized
		override fun write(b: Int)
		{
			super.write(b)
			queueForTranscript()
		}

		@Synchronized
		@Throws(IOException::class)
		override fun write(b: ByteArray?)
		{
			assert(b != null)
			super.write(b!!)
			queueForTranscript()
		}

		@Synchronized
		override fun write(
			b: ByteArray?,
			off: Int,
			len: Int)
		{
			assert(b != null)
			super.write(b!!, off, len)
			queueForTranscript()
		}
	}

	/**
	 * A PrintStream specialization for better println handling.
	 *
	 * @constructor
	 * Because you can't inherit constructors.
	 *
	 * @param out
	 *   The wrapped [OutputStream].
	 * @throws UnsupportedEncodingException
	 *   Because Java won't let you catch the pointless exception thrown by the
	 *   super constructor.
	 */
	internal class BuildPrintStream
		@Throws(UnsupportedEncodingException::class) constructor(
			out: OutputStream)
		: PrintStream(out, false, StandardCharsets.UTF_8.name())
	{
		override fun println(s: String)
		{
			print(s + "\n")
		}

		override fun println(o: Any)
		{
			print(o.toString() + "\n")
		}
	}

	/**
	 * [BuildInputStream] satisfies reads from the UI's
	 * [input field][inputField]. It blocks reads unless some data is available.
	 *
	 * @constructor
	 * Construct a new `BuildInputStream`.
	 */
	inner class BuildInputStream
		: ByteArrayInputStream(ByteArray(1024), 0, 0)
	{
		/**
		 * Clear the input stream. All pending data is discarded and the stream
		 * position is reset to zero (`0`).
		 */
		@Synchronized
		fun clear()
		{
			count = 0
			pos = 0
		}

		/**
		 * Update the content of the stream with data from the
		 * [input field][inputField].
		 */
		@Synchronized
		fun update()
		{
			val text = inputField.text + "\n"
			val bytes = text.toByteArray()
			if (pos + bytes.size >= buf.size)
			{
				val newSize = max(
					buf.size shl 1, bytes.size + buf.size)
				val newBuf = ByteArray(newSize)
				arraycopy(buf, 0, newBuf, 0, buf.size)
				buf = newBuf
			}
			arraycopy(bytes, 0, buf, count, bytes.size)
			count += bytes.size
			writeText(text, IN_ECHO)
			inputField.text = ""
			javaNotifyAll()
		}

		/**
		 * The specified command string was just entered.  Present it in the
		 * [StreamStyle.COMMAND] style.  Force an extra leading new line
		 * to keep the text area from looking stupid.  Also end with a new line.
		 * The passed command should not itself have a new line included.
		 *
		 * @param commandText
		 * The command that was entered, with no leading or trailing line
		 * breaks.
		 */
		@Synchronized
		fun feedbackForCommand(
			commandText: String)
		{
			val textToInsert = "\n" + commandText + "\n"
			writeText(textToInsert, COMMAND)
		}

		override fun markSupported(): Boolean
		{
			return false
		}

		override fun mark(readAheadLimit: Int)
		{
			throw UnsupportedOperationException()
		}

		@Synchronized
		override fun reset()
		{
			throw UnsupportedOperationException()
		}

		@Synchronized
		override fun read(): Int
		{
			// Block until data is available.
			try
			{
				while (pos == count)
				{
					javaWait()
				}
			}
			catch (e: InterruptedException)
			{
				return -1
			}
			return buf[pos++].toInt() and 0xFF
		}

		@Synchronized
		override fun read(
			readBuffer: ByteArray?, start: Int, requestSize: Int): Int
		{
			assert(readBuffer != null)
			if (requestSize <= 0)
			{
				return 0
			}
			// Block until data is available.
			try
			{
				while (pos == count)
				{
					javaWait()
				}
			}
			catch (e: InterruptedException)
			{
				return -1
			}

			val bytesToTransfer = min(requestSize, count - pos)
			arraycopy(buf, pos, readBuffer!!, start, bytesToTransfer)
			pos += bytesToTransfer
			return bytesToTransfer
		}
	}

	/**
	 * Answer the [standard input stream][BuildInputStream].
	 *
	 * @return The input stream.
	 */
	fun inputStream(): BuildInputStream = inputStream!!

	/**
	 * Answer the [standard error stream][PrintStream].
	 *
	 * @return The error stream.
	 */
	fun errorStream(): PrintStream = errorStream!!

	/**
	 * Answer the [standard output stream][PrintStream].
	 *
	 * @return The output stream.
	 */
	fun outputStream(): PrintStream = outputStream

	/**
	 * Enable or disable controls and menu items based on the current state.
	 */
	fun setEnablements()
	{
		val busy = backgroundTask != null || isRunning
		buildProgress.isEnabled = busy
		buildProgress.isVisible = backgroundTask is BuildTask
		inputField.isEnabled = !busy || isRunning
		retrievePreviousAction.isEnabled = !busy
		retrieveNextAction.isEnabled = !busy
		cancelAction.isEnabled = busy
		buildAction.isEnabled = !busy && selectedModule() != null
		unloadAction.isEnabled = !busy && selectedModuleIsLoaded()
		unloadAllAction.isEnabled = !busy
		cleanAction.isEnabled = !busy
		cleanModuleAction.isEnabled =
			!busy && (selectedModuleRoot() != null || selectedModule() != null)
		refreshAction.isEnabled = !busy
		setDocumentationPathAction.isEnabled = !busy
		documentAction.isEnabled = !busy && selectedModule() != null
		graphAction.isEnabled = !busy && selectedModule() != null
		insertEntryPointAction.isEnabled = !busy && selectedEntryPoint() != null
		val selectedEntryPointModule =
			selectedEntryPointModule()
		createProgramAction.isEnabled =
			(!busy && selectedEntryPoint() != null
				&& selectedEntryPointModule != null
				&& availBuilder.getLoadedModule(selectedEntryPointModule) != null)
		examineRepositoryAction.isEnabled = !busy && selectedModuleRootNode() != null
		examineCompilationAction.isEnabled = !busy && selectedModule() != null
		buildEntryPointModuleAction.isEnabled =
			!busy && selectedEntryPointModule() != null
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
	 * Re-parse the package structure from scratch.  Answer a pair consisting
	 * of the module tree and the entry points tree, but don't install them.
	 * This can safely be run outside the UI thread.
	 *
	 * @return The &lt;module tree, entry points tree&gt; [Pair].
	 */
	fun calculateRefreshedTrees(): Pair<TreeNode, TreeNode>
	{
		resolver.clearCache()
		val modules = newModuleTree()
		val entryPoints = newEntryPointsTree()
		return Pair(modules, entryPoints)
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

		moduleTree.model = DefaultTreeModel(modules)
		for (i in moduleTree.rowCount - 1 downTo 0)
		{
			moduleTree.expandRow(i)
		}
		if (selection != null)
		{
			val path = modulePath(selection.qualifiedName)
			if (path != null)
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

	/**
	 * Answer a [FileVisitor] suitable for recursively exploring an
	 * Avail root. A new `FileVisitor` should be obtained for each Avail
	 * root.
	 *
	 * @param stack
	 *   The stack on which to place Avail roots and packages.
	 * @param moduleRoot
	 *   The [ModuleRoot] within which to scan recursively.
	 * @return A `FileVisitor`.
	 */
	private fun moduleTreeVisitor(
		stack: Deque<DefaultMutableTreeNode>,
		moduleRoot: ModuleRoot): FileVisitor<Path>
	{
		val isRoot = Mutable(true)
		return object : FileVisitor<Path>
		{
			/**
			 * Resolve a file name relative to an existing
			 * [DefaultMutableTreeNode].
			 *
			 * @param parentNode
			 *   The [DefaultMutableTreeNode] in which to resolve the file name.
			 * @param fileName
			 *   The [String] containing the file name to resolve.
			 * @return The resolved [ModuleName].
			 */
			private fun resolveModule(
				parentNode: DefaultMutableTreeNode, fileName: String): ModuleName
			{
				val localName = fileName.substring(
					0, fileName.length - ModuleNameResolver.availExtension.length)
				val moduleName: ModuleName
				if (parentNode is ModuleRootNode)
				{
					// Add a top-level package.
					val thisRoot = parentNode.moduleRoot
					assert(thisRoot == moduleRoot)
					moduleName = ModuleName(
						"/" + moduleRoot.name + "/" + localName)
				}
				else
				{
					// Add a non-top-level package.
					assert(parentNode is ModuleOrPackageNode)
					val strongParentNode = parentNode as ModuleOrPackageNode
					assert(strongParentNode.isPackage)
					val parentModuleName =
						strongParentNode.resolvedModuleName
					// The (resolved) parent is a package representative
					// module, so use its parent, the package itself.
					moduleName = ModuleName(
						parentModuleName.packageName, localName)
				}
				return moduleName
			}

			override fun preVisitDirectory(
				dir: Path?, unused: BasicFileAttributes?): FileVisitResult
			{
				assert(dir != null)
				val parentNode = stack.peekFirst()
				if (isRoot.value)
				{
					// Add a ModuleRoot.
					isRoot.value = false
					assert(stack.size == 1)
					val node = ModuleRootNode(availBuilder, moduleRoot)
					parentNode.add(node)
					stack.addFirst(node)
					return FileVisitResult.CONTINUE
				}
				val fileName = dir!!.fileName.toString()
				if (fileName.endsWith(ModuleNameResolver.availExtension))
				{
					val moduleName = resolveModule(parentNode, fileName)
					val resolved: ResolvedModuleName
					try
					{
						resolved = resolver.resolve(moduleName)
					}
					catch (e: UnresolvedDependencyException)
					{
						// The directory didn't contain the necessary package
						// representative, so simply skip the whole directory.
						return FileVisitResult.SKIP_SUBTREE
					}

					val node = ModuleOrPackageNode(
						availBuilder, moduleName, resolved, true)
					parentNode.add(node)
					if (resolved.isRename)
					{
						// Don't examine modules inside a package which is the
						// source of a rename.  They wouldn't have resolvable
						// dependencies anyhow.
						return FileVisitResult.SKIP_SUBTREE
					}
					stack.addFirst(node)
					return FileVisitResult.CONTINUE
				}
				return FileVisitResult.SKIP_SUBTREE
			}

			override fun postVisitDirectory(
				dir: Path?, ex: IOException?): FileVisitResult
			{
				assert(dir != null)
				// Pop the node from the stack.
				stack.removeFirst()
				return FileVisitResult.CONTINUE
			}

			@Throws(IOException::class)
			override fun visitFile(
				file: Path?, attributes: BasicFileAttributes?): FileVisitResult
			{
				assert(file != null)
				val parentNode = stack.peekFirst()
				if (isRoot.value)
				{
					throw IOException("Avail root should be a directory")
				}
				val fileName = file!!.fileName.toString()
				if (fileName.endsWith(ModuleNameResolver.availExtension))
				{
					val moduleName = resolveModule(parentNode, fileName)
					try
					{
						val resolved =
							resolver.resolve(moduleName)
						val node = ModuleOrPackageNode(
							availBuilder, moduleName, resolved, false)
						if (resolved.isRename || !resolved.isPackage)
						{
							parentNode.add(node)
						}
					}
					catch (e: UnresolvedDependencyException)
					{
						// TODO MvG - Find a better way of reporting broken
						// dependencies. Ignore for now (during directory scan).
						throw RuntimeException(e)
					}

				}
				return FileVisitResult.CONTINUE
			}

			override fun visitFileFailed(
				file: Path?, ex: IOException?): FileVisitResult =
					FileVisitResult.CONTINUE
		}
	}

	/**
	 * Answer a [tree node][TreeNode] that represents the (invisible) root of
	 * the Avail module tree.
	 *
	 * @return The (invisible) root of the module tree.
	 */
	private fun newModuleTree(): TreeNode
	{
		val roots = resolver.moduleRoots
		val treeRoot = DefaultMutableTreeNode(
			"(packages hidden root)")
		// Put the invisible root onto the work stack.
		val stack = ArrayDeque<DefaultMutableTreeNode>()
		stack.add(treeRoot)
		for (root in roots.roots)
		{
			// Obtain the path associated with the module root.
			root.repository.reopenIfNecessary()
			val rootDirectory = root.sourceDirectory!!
			try
			{
				Files.walkFileTree(
					Paths.get(rootDirectory.absolutePath),
					EnumSet.of(FileVisitOption.FOLLOW_LINKS),
					Integer.MAX_VALUE,
					moduleTreeVisitor(stack, root))
			}
			catch (e: IOException)
			{
				e.printStackTrace()
				stack.clear()
				stack.add(treeRoot)
			}

		}
		val enumeration: Enumeration<AbstractBuilderFrameTreeNode> =
			cast(treeRoot.preorderEnumeration())
		// Skip the invisible root.
		enumeration.nextElement()
		for (node in enumeration) { node.sortChildren() }
		return treeRoot
	}

	/**
	 * Answer a [tree node][TreeNode] that represents the (invisible) root of
	 * the Avail entry points tree.
	 *
	 * @return The (invisible) root of the entry points tree.
	 */
	private fun newEntryPointsTree(): TreeNode
	{
		val mutex = ReentrantReadWriteLock()
		val moduleNodes = mutableMapOf<String, DefaultMutableTreeNode>()
		availBuilder.traceDirectories { resolvedName, moduleVersion, after ->
			val entryPoints = moduleVersion.getEntryPoints()
			if (entryPoints.isNotEmpty())
			{
				val moduleNode =
					EntryPointModuleNode(availBuilder, resolvedName)
				for (entryPoint in entryPoints)
				{
					val entryPointNode = EntryPointNode(
						availBuilder, resolvedName, entryPoint)
					moduleNode.add(entryPointNode)
				}
				lockWhile<DefaultMutableTreeNode>(
					mutex.writeLock()
				) {
					moduleNodes.put(
						resolvedName.qualifiedName, moduleNode)
				}
			}
			after.invoke()
		}
		val mapKeys = moduleNodes.keys.toTypedArray()
		Arrays.sort(mapKeys)
		val entryPointsTreeRoot =
			DefaultMutableTreeNode("(entry points hidden root)")
		for (moduleLabel in mapKeys)
		{
			entryPointsTreeRoot.add(moduleNodes[moduleLabel])
		}
		val enumeration: Enumeration<AbstractBuilderFrameTreeNode> =
			cast(entryPointsTreeRoot.preorderEnumeration())
		// Skip the invisible root.
		enumeration.nextElement()
		for (node in enumeration) { node.sortChildren() }
		return entryPointsTreeRoot
	}

	/**
	 * Answer the [path][TreePath] to the specified module name in the
	 * [module tree][moduleTree].
	 *
	 * @param moduleName
	 *   A module name.
	 * @return A tree path, or `null` if the module name is not present in
	 *   the tree.
	 */
	fun modulePath(moduleName: String): TreePath?
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
			cast(treeRoot.children())
		var index = 1
		while (nodes.hasMoreElements())
		{
			val node: AbstractBuilderFrameTreeNode = cast(nodes.nextElement())
			if (node.isSpecifiedByString(path[index]))
			{
				index++
				if (index == path.size)
				{
					return TreePath(node.path)
				}
				nodes = cast(node.children())
			}
		}
		return null
	}

	/**
	 * Answer the currently selected [module root][ModuleRootNode], or null.
	 *
	 * @return A [ModuleRootNode], or `null` if no module root is selected.
	 */
	private fun selectedModuleRootNode(): ModuleRootNode?
	{
		val path = moduleTree.selectionPath ?: return null
		val selection =
			path.lastPathComponent as DefaultMutableTreeNode
		return if (selection is ModuleRootNode)
		{
			selection
		}
		else null
	}

	/**
	 * Answer the [ModuleRoot] that is currently selected, otherwise `null`.
	 *
	 * @return A [ModuleRoot], or `null` if no module root is selected.
	 */
	fun selectedModuleRoot(): ModuleRoot? =
		selectedModuleRootNode()?.moduleRoot

	/**
	 * Answer the currently selected [module node][ModuleOrPackageNode].
	 *
	 * @return A module node, or `null` if no module is selected.
	 */
	private fun selectedModuleNode(): ModuleOrPackageNode?
	{
		val path = moduleTree.selectionPath ?: return null
		val selection =
			path.lastPathComponent as DefaultMutableTreeNode
		return if (selection is ModuleOrPackageNode) { selection }
		else { null }
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
		return node != null && node.isLoaded
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
		val selection =
			path.lastPathComponent as DefaultMutableTreeNode
		return if (selection !is EntryPointNode) { null }
		else { selection.entryPointString }
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
		val selection =
			path.lastPathComponent as DefaultMutableTreeNode
		if (selection is EntryPointNode)
		{
			return selection.resolvedModuleName
		}
		return if (selection is EntryPointModuleNode)
		{
			selection.resolvedModuleName
		}
		else { null }
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
		lockWhile(
			buildGlobalUpdateLock.writeLock()
		) {
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
	 * Update the [build progress bar][buildProgress].
	 */
	internal fun updateBuildProgress()
	{
		val position = MutableLong(0L)
		val max = MutableLong(0L)
		lockWhile(buildGlobalUpdateLock.writeLock())
		{
			assert(hasQueuedGlobalBuildUpdate)
			position.value = latestGlobalBuildPosition
			max.value = globalBuildLimit
			hasQueuedGlobalBuildUpdate = false
		}
		val perThousand = (position.value * 1000 / max.value).toInt()
		buildProgress.value = perThousand
		val percent = perThousand / 10.0f
		buildProgress.string = format(
			"Build Progress: %,d / %,d bytes (%3.1f%%)",
			position.value,
			max.value,
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
	 */
	fun eventuallyUpdatePerModuleProgress(
		moduleName: ModuleName, moduleSize: Long, position: Long)
	{
		lockWhile(perModuleProgressLock.writeLock())
		{
			if (position == moduleSize)
			{
				perModuleProgress.remove(moduleName)
			}
			else
			{
				perModuleProgress[moduleName] = Pair(position, moduleSize)
			}
			if (!hasQueuedPerModuleBuildUpdate)
			{
				hasQueuedPerModuleBuildUpdate = true
				availBuilder.runtime.timer.schedule(
					object : TimerTask()
					{
						override fun run()
						{
							invokeLater { updatePerModuleProgressInUIThread() }
						}
					},
					100)
			}
		}
	}

	/**
	 * Update the visual presentation of the per-module statistics.  This must
	 * be invoked from within the UI dispatch thread,
	 */
	internal fun updatePerModuleProgressInUIThread()
	{
		assert(EventQueue.isDispatchThread())
		val progress = ArrayList<Entry<ModuleName, Pair<Long, Long>>>()
		lockWhile(perModuleProgressLock.writeLock())
		{
			assert(hasQueuedPerModuleBuildUpdate)
			progress.addAll(perModuleProgress.entries)
			hasQueuedPerModuleBuildUpdate = false
		}
		progress.sortWith(
			comparing<Entry<ModuleName, Pair<Long, Long>>, String> {
				entry -> entry.key.qualifiedName })
		val builder = StringBuilder(100)
		for ((key, pair) in progress)
		{
			builder.append(
				format(
					"%,6d / %,6d - %s%n",
					pair.first(),
					pair.second(),
					key))
		}
		val string = builder.toString()
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

		lockWhile(perModuleProgressLock.writeLock())
			{ perModuleStatusTextSize = string.length }
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
			for (oldChildName in rootsNode.childrenNames())
			{
				if (roots.moduleRootFor(oldChildName) == null)
				{
					rootsNode.node(oldChildName).removeNode()
				}
			}
			for (root in roots)
			{
				val childNode = rootsNode.node(root.name)
				childNode.put(
					moduleRootsRepoSubkeyString,
					root.repository.fileName.path)
				childNode.put(
					moduleRootsSourceSubkeyString,
					root.sourceDirectory!!.path)
			}

			val renamesNode =
				basePreferences.node(moduleRenamesKeyString)
			val renames = resolver.renameRules
			for (oldChildName in renamesNode.childrenNames())
			{
				val nameInt = try {
					parseInt(oldChildName)
				} catch (e: NumberFormatException) { -1 }

				if (oldChildName != nameInt.toString()
				    || nameInt < 0
				    || nameInt >= renames.size)
				{
					renamesNode.node(oldChildName).removeNode()
				}
			}
			var rowCounter = 0
			for ((key, value) in renames)
			{
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
	 * Information about the window layout.
	 */
	class LayoutConfiguration
	{
		/** The preferred location and size of the window, if specified.  */
		internal var placement: Rectangle? = null

		/**
		 * The width of the left region of the builder frame in pixels, if
		 * specified
		 */
		internal var leftSectionWidth: Int? = null

		/**
		 * The preferred location and size of the module editor window, if
		 * specified.
		 */
		var moduleViewerPlacement: Rectangle? = null

		/**
		 * The proportion, if specified, as a float between `0.0` and `1.0` of
		 * the height of the top left module region in relative proportional to
		 * the height of the entire builder frame.
		 */
		internal var moduleVerticalProportion: Double? = null

		/**
		 * Answer this configuration's recommended width in pixels for the left
		 * region of the window, supplying a suitable default if necessary.
		 *
		 * @return The recommended width of the left part.
		 */
		internal fun leftSectionWidth(): Int
		{
			val w = leftSectionWidth
			return w ?: 200
		}

		/**
		 * Add this configuration's recommended proportion of height of the
		 * modules list versus the entire frame's height, supplying a default
		 * if necessary.  It must be between 0.0 and 1.0 inclusive.
		 *
		 * @return The vertical proportion of the modules area.
		 */
		internal fun moduleVerticalProportion(): Double
		{
			val h = moduleVerticalProportion
			return if (h != null) max(0.0, min(1.0, h)) else 0.5
		}

		/**
		 * Answer a string representation of this configuration that is suitable
		 * for being stored and restored via the [LayoutConfiguration]
		 * constructor that accepts a `String`.
		 *
		 *
		 *
		 * The layout should be fairly stable to avoid treating older versions
		 * as malformed.  To that end, we use a simple list of strings, adding
		 * entries for new purposes to the end, and never removing or changing
		 * the meaning of existing entries.
		 *
		 *
		 * @return A string.
		 */
		fun stringToStore(): String
		{
			val strings = arrayOfNulls<String>(10)
			val p = placement
			if (p != null)
			{
				strings[0] = p.x.toString()
				strings[1] = p.y.toString()
				strings[2] = p.width.toString()
				strings[3] = p.height.toString()
			}

			val w = leftSectionWidth
			if (w != null)
			{
				strings[4] = w.toString()
			}
			val h = moduleVerticalProportion
			if (h != null)
			{
				strings[5] = h.toString()
			}

			val mvp = moduleViewerPlacement
			if (mvp != null)
			{
				strings[6] = mvp.x.toString()
				strings[7] = mvp.y.toString()
				strings[8] = mvp.width.toString()
				strings[9] = mvp.height.toString()
			}

			val builder = StringBuilder()
			var first = true
			for (string in strings)
			{
				if (!first)
				{
					builder.append(',')
				}

				if (string != null)
				{
					builder.append(string)
				}
				first = false
			}
			return builder.toString()
		}

		/**
		 * Construct a new `LayoutConfiguration` with no preferences specified.
		 */
		constructor()
		{
			// all null
		}

		/**
		 * Construct a new `LayoutConfiguration` with preferences specified by
		 * some private encoding in the provided [String].
		 *
		 * @param input
		 *   A string in some encoding compatible with that produced by
		 *   [stringToStore].
		 */
		constructor(input: String)
		{
			if (input.isNotEmpty())
			{
				val substrings =
					input.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
				try
				{
					val x = max(0, parseInt(substrings[0]))
					val y = max(0, parseInt(substrings[1]))
					val w = max(50, parseInt(substrings[2]))
					val h = max(50, parseInt(substrings[3]))
					placement = Rectangle(x, y, w, h)
				}
				catch (e: NumberFormatException)
				{
					// ignore
				}

				try
				{
					leftSectionWidth = max(0, parseInt(substrings[4]))
				}
				catch (e: NumberFormatException)
				{
					// ignore
				}

				try
				{
					moduleVerticalProportion =
						java.lang.Double.parseDouble(substrings[5])
				}
				catch (e: NumberFormatException)
				{
					// ignore
				}

				try
				{
					if (substrings.size >= 9)
					{
						val x = max(0, parseInt(substrings[6]))
						val y = max(0, parseInt(substrings[7]))
						val w = max(50, parseInt(substrings[8]))
						val h = max(50, parseInt(substrings[9]))
						moduleViewerPlacement = Rectangle(x, y, w, h)
					}
				}
				catch (e: NumberFormatException)
				{
					// ignore
				}
			}
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
			dequeLock.lock()
			try
			{
				waitForDequeLockStat.record(System.nanoTime() - beforeLock)
				totalQueuedTextSize.getAndAdd(
					(-privateDiscardExcessLeadingQueuedUpdates()).toLong())
			}
			finally
			{
				// Record the stat just before unlocking, to avoid the need for
				// a lock for the statistic itself.
				writeTextStat.record(System.nanoTime() - before)
				dequeLock.unlock()
			}
		}
	}

	init
	{
		val runtime = AvailRuntime(resolver)
		availBuilder = AvailBuilder(runtime)

		// Get the existing preferences early for plugging in at the right
		// times during construction.
		val configuration = initialConfiguration
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
			refreshAction)//	    		cleanModuleAction,  //TODO MvG Fix implementation and enable.
		val menuBar = JMenuBar()
		menuBar.add(buildMenu)
		if (!runningOnMac)
		{
			augment(buildMenu, null, preferencesAction)
		}
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
                override fun mouseClicked(e: MouseEvent?)
                {
                    assert(e != null)
                    if (buildAction.isEnabled
                        && e!!.clickCount == 2
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
		entryPointsTree = JTree(
			DefaultMutableTreeNode("(entry points hidden root)"))
		moduleTree.background = null
		entryPointsTree.toolTipText = "All entry points, organized by defining module."
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
				 override fun mouseClicked(e: MouseEvent?)
				 {
				     assert(e != null)
				     if (selectedEntryPoint() != null)
				     {
				         if (insertEntryPointAction.isEnabled
				             && e!!.clickCount == 2
				             && e.button == MouseEvent.BUTTON1)
				         {
				             e.consume()
				             val actionEvent = ActionEvent(
				                 entryPointsTree, -1, "Insert entry point")
				             insertEntryPointAction.actionPerformed(actionEvent)
				         }
				     }
				     else if (selectedEntryPointModule() != null)
				     {
				         if (buildEntryPointModuleAction.isEnabled
				             && e!!.clickCount == 2
				             && e.button == MouseEvent.BUTTON1)
				         {
				             e.consume()
				             val actionEvent = ActionEvent(
				                 entryPointsTree, -1, "Build entry point module")
				             buildEntryPointModuleAction.actionPerformed(actionEvent)
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
		for (i in tabStops.indices)
		{
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
		for (style in values())
		{
			style.defineStyleIn(doc)
		}

		// Redirect the standard streams.
		try
		{
			outputStream = BuildPrintStream(BuildOutputStream(OUT))
			errorStream = BuildPrintStream(BuildOutputStream(ERR))
		}
		catch (e: UnsupportedEncodingException)
		{
			// Java must support UTF_8.
			throw RuntimeException(e)
		}

		inputStream = BuildInputStream()
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

		val leftPane = JSplitPane(
			JSplitPane.VERTICAL_SPLIT,
			true,
			createScrollPane(moduleTree),
			createScrollPane(entryPointsTree))
		leftPane.setDividerLocation(configuration.moduleVerticalProportion())
		leftPane.resizeWeight = configuration.moduleVerticalProportion()
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

		val mainSplit = JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT, true, leftPane, rightPane)
		mainSplit.dividerLocation = configuration.leftSectionWidth()
		contentPane.add(mainSplit)
		pack()
		if (configuration.placement != null)
		{
			bounds = configuration.placement!!
		}

		// Save placement when closing.
		addWindowListener(
			object : WindowAdapter()
			{
			  override fun windowClosing(e: WindowEvent?)
			  {
			      val preferences =
				      placementPreferencesNodeForScreenNames(allScreenNames())
			      val prevConfiguration = LayoutConfiguration(
			          preferences.get(placementLeafKeyString, ""))
			      val saveConfiguration = LayoutConfiguration()
			      if (prevConfiguration.moduleViewerPlacement != null)
			      {
			          saveConfiguration.moduleViewerPlacement =
				          prevConfiguration.moduleViewerPlacement
			      }

			      saveConfiguration.placement = bounds
			      saveConfiguration.leftSectionWidth = mainSplit.dividerLocation
			      saveConfiguration.moduleVerticalProportion =
				      leftPane.dividerLocation / max(leftPane.height.toDouble(), 1.0)
			      preferences.put(
			          placementLeafKeyString,
			          saveConfiguration.stringToStore())
			      super.windowClosing(e)
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
	}// Set module components.

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

	companion object
	{
		/**
		 * The prefix string for resources related to the workbench.
		 */
		private const val resourcePrefix = "/resources/workbench/"

		/**
		 * Answer a properly prefixed [String] for accessing the resource having
		 * the given local name.
		 *
		 * @param localResourceName
		 *   The unqualified resource name.
		 * @return The fully qualified resource name.
		 */
		fun resource(localResourceName: String): String =
			resourcePrefix + localResourceName

		/** Determine at startup whether we're on a Mac.  */
		val runningOnMac =
			System.getProperty("os.name").toLowerCase().matches(
				"mac os x.*".toRegex())

		/** Determine at startup whether we should show developer commands.  */
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

		/** Truncate the start of the document any time it exceeds this.  */
		private const val maxDocumentSize = 10_000_000

		/** The [Statistic] for tracking text insertions.  */
		private val insertStringStat =
			Statistic("Insert string", WORKBENCH_TRANSCRIPT)

		/** The [Statistic] for tracking text deletions.  */
		private val removeStringStat =
			Statistic( "Remove string", WORKBENCH_TRANSCRIPT)

		/** The user-specific [Preferences] for this application to use.  */
		private val basePreferences =
			Preferences.userNodeForPackage(AvailWorkbench::class.java)

		/** The key under which to organize all placement information.  */
		private const val placementByMonitorNamesString =
			"placementByMonitorNames"

		/** The leaf key under which to store a single window placement.  */
		const val placementLeafKeyString = "placement"

		/** The key under which to store the [ModuleRoots].  */
		const val moduleRootsKeyString = "module roots"

		/** The subkey that holds a root's repository name.  */
		const val moduleRootsRepoSubkeyString = "repository"

		/** The subkey that holds a root's source directory name.  */
		const val moduleRootsSourceSubkeyString = "source"

		/** The key under which to store the module rename rules.  */
		const val moduleRenamesKeyString = "module renames"

		/** The subkey that holds a rename rule's source module name.  */
		const val moduleRenameSourceSubkeyString = "source"

		/** The subkey that holds a rename rule's replacement module name.  */
		const val moduleRenameTargetSubkeyString = "target"

		/**
		 * Answer a [List] of [Rectangle]s corresponding with the physical
		 * monitors into which [Frame]s may be positioned.
		 *
		 * @return The list of rectangles to which physical screens are mapped.
		 */
		fun allScreenNames(): List<String>
		{
			val graphicsEnvironment =
				GraphicsEnvironment.getLocalGraphicsEnvironment()
			val screens =
				graphicsEnvironment.screenDevices
			val allScreens = ArrayList<String>()
			for (screen in screens)
			{
				allScreens.add(screen.iDstring)
			}
			return allScreens
		}

		/**
		 * Answer the [Preferences] node responsible for holding the default
		 * window position and size for the current monitor configuration.
		 *
		 * @param screenNames
		 *   The list of [id strings][GraphicsDevice.getIDstring] of all
		 *   physical screens.
		 * @return The `Preferences` node in which placement information for
		 *   the current monitor configuration can be stored and retrieved.
		 */
		fun placementPreferencesNodeForScreenNames(screenNames: List<String>)
			: Preferences
		{
			val allNamesString = StringBuilder()
			for (name in screenNames)
			{
				allNamesString.append(name)
				allNamesString.append(";")
			}
			return basePreferences.node(
				"$placementByMonitorNamesString/$allNamesString")
		}

		/**
		 * Parse the [ModuleRoots] from the module roots preferences node.
		 *
		 * @return The `ModuleRoots` constructed from the preferences node.
		 */
		private fun loadModuleRoots(): ModuleRoots
		{
			val roots = ModuleRoots("")
			roots.clearRoots()
			val node = basePreferences.node(moduleRootsKeyString)
			try
			{
				val childNames = node.childrenNames()
				for (childName in childNames)
				{
					val childNode = node.node(childName)
					val repoName = childNode.get(
						moduleRootsRepoSubkeyString, "")
					val sourceName = childNode.get(
						moduleRootsSourceSubkeyString, "")
					roots.addRoot(
						ModuleRoot(
							childName,
							File(repoName),
							File(sourceName)))
				}
			}
			catch (e: BackingStoreException)
			{
				System.err.println(
					"Unable to read Avail roots preferences.")
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
				for (childName in childNames)
				{
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

		/**
		 * Figure out how to initially lay out the frame, based on previously
		 * saved preference information.
		 *
		 * @return The initial [LayoutConfiguration].
		 */
		private val initialConfiguration: LayoutConfiguration
			get()
			{
				val preferences =
					placementPreferencesNodeForScreenNames(allScreenNames())
				val configurationString = preferences.get(
					placementLeafKeyString, null)
						?: return LayoutConfiguration()
				return LayoutConfiguration(configurationString)
			}

		/** Statistic for waiting for updateQueue's monitor.  */
		internal val waitForDequeLockStat = Statistic(
			"Wait for lock to trim old entries",
			WORKBENCH_TRANSCRIPT)

		/** Statistic for trimming excess leading entries.  */
		internal val discardExcessLeadingStat = Statistic(
			"Trim old entries (not counting lock)",
			WORKBENCH_TRANSCRIPT)

		/**
		 * Statistic for invoking writeText, including waiting for the monitor.
		 */
		internal val writeTextStat =
			Statistic("Call writeText", WORKBENCH_TRANSCRIPT)

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
				hasFocus: Boolean): Component
			{
				return when (value) {
					is AbstractBuilderFrameTreeNode -> {
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
					if (darkMode) {
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
		private fun augment(menu: JMenu,vararg actionsAndSubmenus: Any?)
		{
			for (item in actionsAndSubmenus)
			{
				when (item) {
					null -> menu.addSeparator()
					is Action -> menu.add(item)
					is JMenuItem -> menu.add(item)
					else -> assert(false) { "Bad argument while building menu" }
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
		private fun createScrollPane(
			innerComponent: Component): JScrollPane
		{
			val scrollPane = JScrollPane()
			scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
			scrollPane.verticalScrollBarPolicy = VERTICAL_SCROLLBAR_ALWAYS
			scrollPane.minimumSize = Dimension(100, 0)
			scrollPane.setViewportView(innerComponent)
			return scrollPane
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
				OSXUtility.setDockIconBadgeMethod.invoke(
					application, activeVersionSummary())
			}
			catch (e: Exception)
			{
				throw RuntimeException(e)
			}

		}

		/**
		 * Launch the [Avail builder][AvailBuilder] [UI][AvailWorkbench].
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
			if (darkMode) {
				UIManager.setLookAndFeel(DarculaLaf())
			}

			if (runningOnMac)
			{
				setUpForMac()
			}

			val rootsString = System.getProperty("availRoots", "")
			val roots = when {
				// Read the persistent preferences file...
				rootsString.isEmpty() -> loadModuleRoots()
				// Providing availRoots on command line overrides preferences...
				else -> ModuleRoots(rootsString)
			}

			val resolver: ModuleNameResolver
			var reader: Reader? = null
			try
			{
				val renames = System.getProperty("availRenames", null)
				reader = when (renames) {
					// Load the renames from preferences further down.
					null ->  StringReader("")
					// Load renames from file specified on the command line...
					else -> BufferedReader(
						InputStreamReader(
							FileInputStream(
								File(renames)), StandardCharsets.UTF_8))
				}
				val renameParser = RenamesFileParser(reader, roots)
				resolver = renameParser.parse()
				if (renames == null)
				{
					// Now load the rename rules from preferences.
					loadRenameRulesInto(resolver)
				}
			}
			finally
			{
				IO.closeIfNotNull(reader)
			}

			// The first application argument, if any, says which module to select.
			val initial = if (args.isNotEmpty()) args[0] else ""

			// Display the UI.
			invokeLater {
				val bench = AvailWorkbench(resolver)
				if (runningOnMac)
				{
					bench.setUpInstanceForMac()
				}
				val initialRefreshTask = object : AbstractWorkbenchTask(bench, null)
				{
					override fun executeTask()
					{
						// First refresh the module and entry point trees.
						workbench.writeText(
							"Scanning all module headers.\n",
							INFO)
						val before = currentTimeMillis()
						val modulesAndEntryPoints = workbench.calculateRefreshedTrees()
						val after = currentTimeMillis()
						workbench.writeText(
							format("...done (%,3dms)\n", after - before),
							INFO)
						// Now select an initial module, if specified.
						invokeLater {
							workbench.refreshFor(
								modulesAndEntryPoints.first(),
								modulesAndEntryPoints.second())
							if (initial.isNotEmpty())
							{
								val path = workbench.modulePath(initial)
								if (path != null)
								{
									workbench.moduleTree.selectionPath = path
									workbench.moduleTree.scrollRowToVisible(
										workbench.moduleTree
											.getRowForPath(path))
								}
								else
								{
									workbench.writeText(
										format(
											"Command line argument '%s' was not a valid module path",
											initial),
										ERR)
								}
							}
							workbench.backgroundTask = null
							workbench.setEnablements()
						}
					}
				}
				bench.backgroundTask = initialRefreshTask
				bench.setEnablements()
				bench.isVisible = true
				initialRefreshTask.execute()
			}
		}
	}
}
