/*
 * AvailWorkbench.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *

 * * Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
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
import avail.AvailRuntimeConfiguration.activeVersionSummary
import avail.anvil.MenuBarBuilder.Companion.createMenuBar
import avail.anvil.actions.AboutAction
import avail.anvil.actions.BuildAction
import avail.anvil.actions.CancelAction
import avail.anvil.actions.CleanAction
import avail.anvil.actions.CleanModuleAction
import avail.anvil.actions.ClearTranscriptAction
import avail.anvil.actions.CreateProgramAction
import avail.anvil.actions.CreateProjectAction
import avail.anvil.actions.CreateRootAction
import avail.anvil.actions.DebugAction
import avail.anvil.actions.DeleteModuleAction
import avail.anvil.actions.ExamineCompilationAction
import avail.anvil.actions.ExamineModuleManifest
import avail.anvil.actions.ExamineRepositoryAction
import avail.anvil.actions.ExamineSerializedPhrasesAction
import avail.anvil.actions.ExamineStylingAction
import avail.anvil.actions.FindAction
import avail.anvil.actions.GenerateDocumentationAction
import avail.anvil.actions.GenerateGraphAction
import avail.anvil.actions.InsertEntryPointAction
import avail.anvil.actions.NewModuleAction
import avail.anvil.actions.OpenKnownProjectAction
import avail.anvil.actions.OpenModuleAction
import avail.anvil.actions.OpenProjectAction
import avail.anvil.actions.OpenShortcutManagerAction
import avail.anvil.actions.OpenTemplateExpansionsManagerAction
import avail.anvil.actions.ParserIntegrityCheckAction
import avail.anvil.actions.PreferencesAction
import avail.anvil.actions.RefreshAction
import avail.anvil.actions.RemoveRootAction
import avail.anvil.actions.ResetCCReportDataAction
import avail.anvil.actions.ResetVMReportDataAction
import avail.anvil.actions.RetrieveNextCommand
import avail.anvil.actions.RetrievePreviousCommand
import avail.anvil.actions.SearchOpenModuleDialogAction
import avail.anvil.actions.SetDocumentationPathAction
import avail.anvil.actions.ShowCCReportAction
import avail.anvil.actions.ShowVMReportAction
import avail.anvil.actions.SubmitInputAction
import avail.anvil.actions.ToggleDebugAfterUnload
import avail.anvil.actions.ToggleDebugInterpreterL1
import avail.anvil.actions.ToggleDebugInterpreterL2
import avail.anvil.actions.ToggleDebugInterpreterPrimitives
import avail.anvil.actions.ToggleDebugJVM
import avail.anvil.actions.ToggleDebugJVMCodeGeneration
import avail.anvil.actions.ToggleDebugWorkUnits
import avail.anvil.actions.ToggleFastLoaderAction
import avail.anvil.actions.ToggleL2SanityCheck
import avail.anvil.actions.ToggleVisibleRootsAction
import avail.anvil.actions.TraceCompilerAction
import avail.anvil.actions.TraceLoadedStatementsAction
import avail.anvil.actions.TraceMacrosAction
import avail.anvil.actions.TraceStylingAction
import avail.anvil.actions.TraceSummarizeStatementsAction
import avail.anvil.actions.UnloadAction
import avail.anvil.actions.UnloadAllAction
import avail.anvil.debugger.AvailDebugger
import avail.anvil.nodes.AbstractBuilderFrameTreeNode
import avail.anvil.nodes.EntryPointModuleNode
import avail.anvil.nodes.EntryPointNode
import avail.anvil.nodes.ModuleOrPackageNode
import avail.anvil.nodes.ModuleRootNode
import avail.anvil.projects.GlobalAvailConfiguration
import avail.anvil.projects.manager.AvailProjectManager
import avail.anvil.projects.manager.OpenKnownProjectDialog
import avail.anvil.settings.TemplateExpansionsManager
import avail.anvil.settings.ShortcutManager
import avail.anvil.streams.BuildInputStream
import avail.anvil.streams.BuildOutputStream
import avail.anvil.streams.BuildOutputStreamEntry
import avail.anvil.streams.BuildPrintStream
import avail.anvil.streams.StreamStyle
import avail.anvil.streams.StreamStyle.BUILD_PROGRESS
import avail.anvil.streams.StreamStyle.ERR
import avail.anvil.streams.StreamStyle.INFO
import avail.anvil.streams.StreamStyle.OUT
import avail.anvil.tasks.BuildTask
import avail.anvil.text.CodePane
import avail.anvil.views.StructureViewPanel
import avail.anvil.window.AvailWorkbenchLayoutConfiguration
import avail.anvil.window.WorkbenchScreenState
import avail.anvil.window.WorkbenchFrame
import avail.builder.AvailBuilder
import avail.builder.ModuleName
import avail.builder.ModuleNameResolver
import avail.builder.ModuleRoot
import avail.builder.ModuleRoots
import avail.builder.RenamesFileParser
import avail.builder.ResolvedModuleName
import avail.builder.UnresolvedDependencyException
import avail.descriptor.module.A_Module
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.phrases.A_Phrase
import avail.files.FileManager
import avail.io.ConsoleInputChannel
import avail.io.ConsoleOutputChannel
import avail.io.TextInterface
import avail.performance.Statistic
import avail.performance.StatisticReport.WORKBENCH_TRANSCRIPT
import avail.persistence.cache.Repositories
import avail.resolver.ModuleRootResolver
import avail.resolver.ResolverReference
import avail.stacks.StacksGenerator
import avail.utility.IO
import avail.utility.PrefixTree
import avail.utility.PrefixTree.Companion.getOrPut
import avail.utility.cast
import avail.utility.isNullOr
import avail.utility.notNullAnd
import avail.utility.parallelDoThen
import avail.utility.safeWrite
import com.formdev.flatlaf.FlatDarculaLaf
import com.formdev.flatlaf.util.SystemInfo
import org.availlang.artifact.ResourceType
import org.availlang.artifact.environment.location.AvailRepositories
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.artifact.environment.project.AvailProjectV1
import org.availlang.json.JSONWriter
import java.awt.Color
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
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
import java.lang.String.format
import java.lang.System.currentTimeMillis
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.Arrays.sort
import java.util.Collections
import java.util.Collections.synchronizedMap
import java.util.Queue
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.swing.Action
import javax.swing.GroupLayout
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JSplitPane.DIVIDER_LOCATION_PROPERTY
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.SwingUtilities.invokeLater
import javax.swing.SwingWorker
import javax.swing.UIManager
import javax.swing.WindowConstants
import javax.swing.text.BadLocationException
import javax.swing.text.StyledDocument
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
 * @property globalAvailConfiguration
 *   The [GlobalAvailConfiguration] for the environment this [AvailWorkbench] is
 *   being launched in.
 * @property availProject
 *   The actively running [AvailProject].
 * @property availProjectFilePath
 *   The String path to the [availProject] configuration file or an empty String
 *   if the [AvailWorkbench] was started without an [AvailProject].
 * @property projectManager
 *   The [AvailProjectManager] that launched this workbench or `null` if
 *   not launched from an [AvailProjectManager].
 *
 * @constructor
 * Construct a new [AvailWorkbench].
 *
 * @param fileManager
 *   The [FileManager] used to manage Avail files.
 * @param resolver
 *   The [module&#32;name resolver][ModuleNameResolver].
 * @param globalAvailConfiguration
 *  The [GlobalAvailConfiguration] for the environment this [AvailWorkbench] is
 *  being launched in.
 * @param availProject
 *   The actively running [AvailProject].
 * @param windowTitle
 *   The [AvailWorkbench]'s frame's [title].
 * @param projectManager
 *   The [AvailProjectManager] that launched this workbench or `null` if
 *   not launched from an [AvailProjectManager].
 */
class AvailWorkbench internal constructor(
	internal val availProject: AvailProject,
	val runtime: AvailRuntime,
	private val fileManager: FileManager,
	val resolver: ModuleNameResolver,
	val globalAvailConfiguration: GlobalAvailConfiguration,
	internal var availProjectFilePath: String = "",
	windowTitle: String = "Avail Workbench",
	internal val projectManager: AvailProjectManager?
) : WorkbenchFrame(windowTitle)
{
	override val workbench: AvailWorkbench get() = this

	/**
	 * The file path to the [WorkbenchScreenState].
	 */
	private val workbenchScreenStatePath get() =
		availProjectFilePath.replace(".json", "-local-state.json")

	/**
	 * The directory which is the root of the project.
 	 */
	val projectHomeDirectory =
		availProjectFilePath.substringBeforeLast(File.separator)

	/**
	 * Save the [availProject] to disk in [availProjectFilePath].
	 */
	fun saveProjectFileToDisk ()
	{
		val projectFilePath = availProjectFilePath
		if (projectFilePath.isNotEmpty())
		{
			val backup = File("$availProjectFilePath.backup")
			if (backup.exists())
			{
				backup.delete()
			}
			val orig = File(projectFilePath)
			if (orig.exists())
			{
				orig.renameTo(backup)
			}
			// Update the backing project file.
			val writer = JSONWriter.newPrettyPrinterWriter()
			availProject.writeTo(writer)
			File(projectFilePath).writeText(writer.contents())
		}
	}

	/**
	 * The recognized textual templates available for interactive
	 * transformation, as a [PrefixTree] from template texts to expansion
	 * texts.
	 */
	val templates = run {
		val tree = PrefixTree<Int, MutableSet<String>>()
		availProject.availProjectRoots.forEach { root ->
			root.templates.forEach { (name, expansion) ->
				val expansions = tree.getOrPut(name) { mutableSetOf() }
				expansions.add(expansion)
			}
		}
		availProject.templates.forEach { (name, expansion) ->
			val expansions = tree.getOrPut(name) { mutableSetOf() }
			expansions.add(expansion)
		}
		tree
	}

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
	val transcript = CodePane(this)

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
	 * again and so on.  An initial cursor-down selects the first entry and goes
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
	val refreshAction = RefreshAction(this)

	/** The [FindAction] for finding/replacing text in a text area. */
	private val findAction = FindAction(this, this)

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

	/** The [DebugAction], which opens an [AvailDebugger]. */
	private val debugAction = DebugAction(this)

	/** The [show CC report action][ShowCCReportAction]. */
	private val showCCReportAction = ShowCCReportAction(this, runtime)

	/** The [reset CC report data action][ResetCCReportDataAction]. */
	private val resetCCReportDataAction = ResetCCReportDataAction(this, runtime)

	/** The [toggle trace macros action][TraceMacrosAction]. */
	private val debugMacroExpansionsAction = TraceMacrosAction(this)

	/** The [toggle trace compiler action][TraceCompilerAction]. */
	private val debugCompilerAction = TraceCompilerAction(this)

	/** The [toggle trace styling action][TraceStylingAction]. */
	private val debugStylingAction = TraceStylingAction(this)

	/** The [toggle fast-loader action][ToggleFastLoaderAction]. */
	private val toggleFastLoaderAction = ToggleFastLoaderAction(this)

	/** The [toggle L1 debug action][ToggleDebugInterpreterL1]. */
	private val toggleDebugL1 = ToggleDebugInterpreterL1(this)

	/** The [toggle L2 debug action][ToggleDebugInterpreterL2]. */
	private val toggleDebugL2 = ToggleDebugInterpreterL2(this)

	/** The [ToggleL2SanityCheck] toggle L2 sanity checks action. */
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

	/**
	 * The [ToggleDebugAfterUnload] toggles checking of references to modules
	 * after they are unloaded.
	 */
	private val toggleDebugAfterUnload = ToggleDebugAfterUnload(this)

	/** The [toggle JVM dump debug action][ToggleDebugJVM]. */
	private val toggleDebugJVM = ToggleDebugJVM(this)

	/** The [toggle JVM code generation][ToggleDebugJVMCodeGeneration]. */
	private val toggleDebugJVMCodeGeneration =
		ToggleDebugJVMCodeGeneration(this)

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
	private val parserIntegrityCheckAction =
		ParserIntegrityCheckAction(this, runtime)

	/** The [ExamineRepositoryAction]. */
	private val examineRepositoryAction = ExamineRepositoryAction(this, runtime)

	/** The [ExamineCompilationAction]. */
	private val examineCompilationAction =
		ExamineCompilationAction(this, runtime)

	/** The [ExamineSerializedPhrasesAction]. */
	private val examinePhrasesAction =
		ExamineSerializedPhrasesAction(this, runtime)

	private val examineStylingAction =
		ExamineStylingAction(this, runtime)

	/** The [ExamineModuleManifest]. */
	private val examineModuleManifestAction =
		ExamineModuleManifest(this, runtime)

	/** The [clear transcript action][ClearTranscriptAction]. */
	private val clearTranscriptAction = ClearTranscriptAction(this)

	/**
	 * The [action][InsertEntryPointAction] to insert an entry point invocation
	 * into the command area.
	 */
	internal val insertEntryPointAction = InsertEntryPointAction(this)

	/** The [action][BuildAction] to build an entry point module. */
	internal val buildEntryPointModuleAction = BuildAction(this, true)

	/** The action to open a [new][NewModuleAction] module editor. */
	private val newEditorAction = NewModuleAction(this)

	/** The action to [edit][OpenModuleAction] a module. */
	private val openEditorAction = OpenModuleAction(this)

	/** The action to open an [CreateProjectAction]. */
	private val createProjectAction = CreateProjectAction(this)

	/** The action to open an [OpenKnownProjectDialog]. */
	private val openKnownProjectAction = OpenKnownProjectAction(this)

	/** The action to open another [AvailWorkbench] for an [AvailProject]. */
	private val openProjectAction = OpenProjectAction(this)

	/** The action to open the [ShortcutManager]. */
	private val openShortcutManagerAction = OpenShortcutManagerAction(this)

	/** The action to open the [TemplateExpansionsManager]. */
	private val openTemplateExpansionManagerAction =
		OpenTemplateExpansionsManagerAction(this)

	/** The action to [delete][DeleteModuleAction] a module or package. */
	private val deleteModuleAction = DeleteModuleAction(this)

	/** The [action][CreateRootAction] to add a root to the project. */
	private val createRootAction = CreateRootAction(this)

	/** The [action][RemoveRootAction] to remove a root from the project. */
	private val removeRootAction = RemoveRootAction(this)

	/** The [action][ToggleVisibleRootsAction] to show/hide invisible roots. */
	private val toggleShowInvisibleRootAction = ToggleVisibleRootsAction(this)

	/**
	 * The action to open a dialog that enables a user to search for a module by
	 * name and open it.
	 */
	private val searchOpenModuleAction = SearchOpenModuleDialogAction(this)

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

	val openEditors: MutableMap<ModuleName, AvailEditor> = ConcurrentHashMap()

	/** Whether an entry point invocation (command line) is executing. */
	var isRunning = false

	/**
	 * The open [ShortcutManager] view or `null` if not open.
	 */
	var shortcutManager: ShortcutManager? = null

	/**
	 * The [TemplateExpansionsManager] view or `null` if not open.
	 */
	var templateExpansionManager: TemplateExpansionsManager? = null

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
	 * When false, roots marked as invisible are not shown.  When true, they are
	 * shown with an indication that they have been marked invisible.
	 */
	var showInvisibleRoots = false

	/**
	 * `AbstractWorkbenchTask` is a foundation for long-running [AvailBuilder]
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
			val durationMillis = currentTimeMillis() - startTimeMillis
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
				// We need to trim off part of the document, right after the
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
					entry.style.getStyle(document))
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

		createRootAction.isEnabled = !busy
		removeRootAction.isEnabled = !busy && selectedModuleRoot() != null
		toggleShowInvisibleRootAction.isEnabled = !busy
		openEditorAction.isEnabled = !busy && selectedModule() !== null
		newEditorAction.isEnabled = !busy && run {
			val root = selectedModuleRoot() ?: (selectedModule()?.moduleRoot)
			root.notNullAnd { getProjectRoot(name).notNullAnd { editable } }
		}
		deleteModuleAction.isEnabled = !busy && selectedModule().notNullAnd {
			moduleRoot.resolver.canSave &&
				getProjectRoot(moduleRoot.name).notNullAnd { editable }
		}
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
		examineCompilationAction.isEnabled =
			!busy && selectedModule() !== null
		examinePhrasesAction.isEnabled =
			!busy && selectedModule() !== null
		examineStylingAction.isEnabled =
			!busy && selectedModule() !== null
		examineModuleManifestAction.isEnabled =
			!busy && selectedModule() !== null
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
		val selection = moduleTree.selectionPath
		moduleTree.isVisible = false
		entryPointsTree.isVisible = false
		try
		{
			val root = moduleTree.model.root as DefaultMutableTreeNode
			val allExpanded = moduleTree
				.getExpandedDescendants(TreePath(root))
				?.toList()?.sortedBy(TreePath::getPathCount)
				?: modules.children().toList().map {
					// Expand each root on the first refresh.
					TreePath(arrayOf(root, it))
				}
			moduleTree.model = DefaultTreeModel(modules)
			allExpanded.asSequence()
				.map(TreePath::getLastPathComponent)
				.filterIsInstance<AbstractBuilderFrameTreeNode>()
				.map(AbstractBuilderFrameTreeNode::modulePathString)
				.mapNotNull(::modulePath)
				.forEach(moduleTree::expandPath)
			selection?.let { path ->
				val node =
					path.lastPathComponent as AbstractBuilderFrameTreeNode
				moduleTree.selectionPath = modulePath(node.modulePathString())
			}

			// Now process the entry points tree.
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
		val treeRoot = DefaultMutableTreeNode("(packages hidden root)")

		roots.roots.parallelDoThen(
			action = { root, after ->
				// Obtain the path associated with the module root.
				val projRoot = getProjectRoot(root.name)
				if (projRoot.isNullOr { !visible && !showInvisibleRoots })
				{
					after()
					return@parallelDoThen
				}
				root.repository.reopenIfNecessary()
				root.resolver.provideModuleRootTree(
					successHandler = {
						val node = ModuleRootNode(
							availBuilder,
							projRoot!!.editable,
							root,
							projRoot.visible)
						createRootNodesThen(node, it) {
							sortedRootNodes.add(node)
							for (each in node.preorderEnumeration())
							{
								(each as AbstractBuilderFrameTreeNode)
									.sortChildren()
							}
							after()
						}
					},
					failureHandler = { code, ex ->
						System.err.println(
							"Workbench could not walk root: " +
								"${root.name}: $code")
						ex?.printStackTrace()
						after()
					})
			},
			then = {
				sortedRootNodes.sort()
				sortedRootNodes.forEach(treeRoot::add)
				val iterator: Iterator<AbstractBuilderFrameTreeNode> =
					treeRoot.preorderEnumeration().iterator().cast()
				iterator.next()
				iterator.forEachRemaining(
					AbstractBuilderFrameTreeNode::sortChildren)
				withTreeNode(treeRoot)
			})
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
						val resolved = resolver.resolve(it.moduleName)
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
		availBuilder.traceDirectoriesThen(
			action = { resolvedName, moduleVersion, after ->
				val projectRoot = getProjectRoot(resolvedName.rootName)
				if (projectRoot.notNullAnd { visible })
				{
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
							moduleNodes[resolvedName.qualifiedName] = moduleNode
						}
					}
				}
				after()
			},
			afterAll = {
				val entryPointsTreeRoot =
					DefaultMutableTreeNode("(entry points hidden root)")
				val mapKeys = moduleNodes.keys.toTypedArray()
				sort(mapKeys)
				mapKeys.forEach { moduleLabel ->
					entryPointsTreeRoot.add(moduleNodes[moduleLabel])
				}
				val iterator: Iterator<DefaultMutableTreeNode> =
					entryPointsTreeRoot.preorderEnumeration().iterator().cast()
				// Skip the invisible top node.
				iterator.next()
				iterator.forEachRemaining { node ->
					(node as AbstractBuilderFrameTreeNode).sortChildren()
				}
				withTreeNode(entryPointsTreeRoot)
			})
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
		var node = model.root as DefaultMutableTreeNode
		for (index in 1 until path.size)
		{
			val nodes: Sequence<AbstractBuilderFrameTreeNode> =
				node.children().asSequence().cast()
			node = nodes.firstOrNull { it.isSpecifiedByString(path[index]) }
				?: return null
		}
		return TreePath(node.path)
	}

	/**
	 * Answer the currently selected [module&#32;root][ModuleRootNode], or null.
	 *
	 * @return A [ModuleRootNode], or `null` if no module root is selected.
	 */
	private fun selectedModuleRootNode(): ModuleRootNode?
	{
		// This can be called during the constructor, so it can be null.
		@Suppress("SENSELESS_COMPARISON")
		if (moduleTree === null) return null
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
		// This can be called during the constructor, so it can be null.
		@Suppress("SENSELESS_COMPARISON")
		if (moduleTree === null) return null
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
		// This can be called during the constructor, so it can be null.
		@Suppress("SENSELESS_COMPARISON")
		if (entryPointsTree === null) return null
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
	 * @param phrase
	 *   The compiled [top-level&#32;statement][A_Phrase], or `null` if no
	 *   phrase is available.
	 */
	fun eventuallyUpdatePerModuleProgress(
		moduleName: ModuleName,
		moduleSize: Long,
		position: Long,
		line: Int,
		@Suppress("UNUSED_PARAMETER") phrase: ()->A_Phrase?)
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
				BUILD_PROGRESS.getStyle(doc))
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

	/**
	 * The singular [StructureViewPanel] or `null` if none available.
	 */
	internal var structureView: StructureViewPanel? = null
		private set

	/**
	 * The singular [StructureViewPanel].
	 */
	internal val structureViewPanel: StructureViewPanel get()
	{
		var v = structureView
		if (v == null)
		{
			v = StructureViewPanel(this)
			{
				structureView = null
			}
			structureView = v
		}
		return v
	}

	/**
	 * `true` indicates a [structureViewPanel] is open; `false` indicates the
	 * window is not open.
	 */
	internal val structureViewIsOpen get() = structureView != null

	/**
	 * Close the provided [AvailEditor].
	 *
	 * @param editor
	 *   The [AvailEditor] that is closing.
	 */
	fun closeEditor (editor: AvailEditor)
	{
		openEditors.remove(editor.resolverReference.moduleName)
		structureView?.closingEditor(editor)
	}


	private val mainSplit: JSplitPane

	private val leftPane: JSplitPane

	// Get the existing preferences early for plugging in at the right times
	// during construction.
	private var screenState = WorkbenchScreenState.from(File(workbenchScreenStatePath))

	override val layoutConfiguration =
		AvailWorkbenchLayoutConfiguration.from(
			screenState.workbenchLayoutConfig)

	init
	{
		fileManager.associateRuntime(runtime)
		availBuilder = AvailBuilder(runtime)

		defaultCloseOperation =
			if (projectManager != null)
			{
				projectManager.onWorkbenchOpen(this)
				addWindowListener(object : WindowAdapter()
				{
					override fun windowClosed(e: WindowEvent)
					{
						projectManager.onWorkbenchClose(this@AvailWorkbench)
					}
				})
				WindowConstants.DISPOSE_ON_CLOSE
			}
			else
			{
				WindowConstants.EXIT_ON_CLOSE
			}

		// Set *just* the window title...
		title = windowTitle
		isResizable = true
		background = rootPane.background

		// Build the pop-up menu for the modules area.
		val buildMenu = MenuBuilder.menu("Module") {
			item(createRootAction)
			item(removeRootAction)
			check(toggleShowInvisibleRootAction)
			separator()
			item(newEditorAction)
			item(openEditorAction)
			item(searchOpenModuleAction)
			item(deleteModuleAction)
			separator()
			item(buildAction)
			item(cancelAction)
			separator()
			item(unloadAction)
			item(unloadAllAction)
			item(cleanAction)
			separator()
			//item(cleanModuleAction)  //TODO MvG Fix implementation and enable.
			item(refreshAction)
		}

		// Create the menu bar and its menus.
		jMenuBar = createMenuBar {
			if (projectManager != null)
			{
				menu("File")
				{
					item(openKnownProjectAction)
					item(createProjectAction)
					item(openProjectAction)
				}
			}
			menu("Build")
			{
				item(buildAction)
				item(cancelAction)
				separator()
				item(unloadAction)
				item(unloadAllAction)
				item(cleanAction)
				separator()
				//item(cleanModuleAction)  //TODO MvG Fix implementation and enable.
				item(refreshAction)
			}
			menu("Module")
			{
				item(createRootAction)
				item(removeRootAction)
				check(toggleShowInvisibleRootAction)
				separator()
				item(newEditorAction)
				item(openEditorAction)
				item(searchOpenModuleAction)
				item(deleteModuleAction)
			}
			menu("Edit")
			{
				item(findAction)
			}
			// TODO Stacks is not viable right now
//			menu("Document")
//			{
//				item(documentAction)
//				separator()
//				item(setDocumentationPathAction)
//			}
			menu("Run")
			{
				item(insertEntryPointAction)
				separator()
				item(clearTranscriptAction)
			}
			menu("Settings")
			{
				item(openTemplateExpansionManagerAction)
				item(openShortcutManagerAction)
			}
			addWindowMenu(this@AvailWorkbench)
			if (showDeveloperTools)
			{
				menu("Developer")
				{
					item(showVMReportAction)
					item(resetVMReportDataAction)
					separator()
					item(debugAction)
					separator()
					item(showCCReportAction)
					item(resetCCReportDataAction)
					separator()
					check(debugMacroExpansionsAction)
					check(debugCompilerAction)
					check(traceSummarizeStatementsAction)
					check(traceLoadedStatementsAction)
					check(toggleFastLoaderAction)
					check(debugStylingAction)
					separator()
					check(toggleDebugL1)
					check(toggleDebugL2)
					check(toggleL2SanityCheck)
					check(toggleDebugPrimitives)
					check(toggleDebugWorkUnits)
					check(toggleDebugAfterUnload)
					separator()
					check(toggleDebugJVM)
					check(toggleDebugJVMCodeGeneration)
					separator()
					item(parserIntegrityCheckAction)
					item(examineRepositoryAction)
					item(examineCompilationAction)
					item(examinePhrasesAction)
					item(examineStylingAction)
					item(examineModuleManifestAction)
					separator()
					item(graphAction)
				}
			}
		}

		// The refresh item needs a little help ...
		rootPane.actionMap.put("Refresh", refreshAction)
		rootPane.inputMap.put(KeyStroke.getKeyStroke("F5"), "Refresh")

		// Create the module tree.
		moduleTree = JTree(DefaultMutableTreeNode(
			"(packages hidden root)"))
		moduleTree.run {
			background = null
			toolTipText = "All modules, organized by module root."
			componentPopupMenu = buildMenu.popupMenu
			isEditable = false
			isEnabled = true
			isFocusable = true
			selectionModel.selectionMode =
				TreeSelectionModel.SINGLE_TREE_SELECTION
			toggleClickCount = 0
			showsRootHandles = true
			isRootVisible = false
			addTreeSelectionListener { setEnablements() }
			cellRenderer = treeRenderer
			addMouseListener(
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
		}

		// Create the entry points tree.
		entryPointsTree = JTree(
			DefaultMutableTreeNode("(entry points hidden root)"))
		entryPointsTree.run {
			background = null
			toolTipText = "All entry points, organized by defining module."
			isEditable = false
			isEnabled = true
			isFocusable = true
			selectionModel.selectionMode =
				TreeSelectionModel.SINGLE_TREE_SELECTION
			toggleClickCount = 0
			showsRootHandles = true
			isRootVisible = false
			addTreeSelectionListener { setEnablements() }
			cellRenderer = treeRenderer
			addMouseListener(
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
								insertEntryPointAction.actionPerformed(
									actionEvent)
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
		}

		// Create the build progress bar.
		buildProgress = JProgressBar(0, 1000).apply {
			toolTipText = "Progress indicator for the build."
			isEnabled = false
			isFocusable = false
			isIndeterminate = false
			isStringPainted = true
			string = "Build Progress:"
			value = 0
		}

		// Create the transcript.

		// Make this row and column be where the excess space goes.
		// And reset the weights...
		transcriptScrollArea = createScrollPane(transcript)

		// Create the input area.
		inputLabel = JLabel("Command:")
		inputField = JTextField().apply {
			columns = 60
			isEditable = true
			isEnabled = true
			isFocusable = true
			toolTipText =
				"Enter commands and interact with Avail programs.  Press " +
					"ENTER to submit."
			action = SubmitInputAction(this@AvailWorkbench)
			actionMap.put(
				retrievePreviousAction.getValue(Action.NAME),
				retrievePreviousAction)
			actionMap.put(
				retrieveNextAction.getValue(Action.NAME),
				retrieveNextAction)
			getInputMap(JComponent.WHEN_FOCUSED).run {
				put(
					KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
					retrievePreviousAction.getValue(Action.NAME))
				put(
					KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
					retrieveNextAction.getValue(Action.NAME))
			}
		}

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
		val textInterface = TextInterface(
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
		addWindowListener(object : WindowAdapter()
		{
			override fun windowClosing(e: WindowEvent)
			{
				saveWindowPosition()
				val sv = structureView?.let {
					it.saveWindowPosition()
					it.layoutConfiguration.stringToStore()
				} ?: ""
				val local = WorkbenchScreenState(
					layoutConfiguration.stringToStore(),
					sv)
				local.refreshOpenEditors(this@AvailWorkbench)
				File(workbenchScreenStatePath).writeText(local.fileContent)
				openEditors.values.forEach {
					it.dispatchEvent(WindowEvent(it, WindowEvent.WINDOW_CLOSING))
				}
				shortcutManager?.apply {
					dispatchEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING))
				}
				templateExpansionManager?.apply {
					dispatchEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING))
				}
			}
		})
		if (System.getProperty("os.name").startsWith("Mac"))
		{
			// Set up desktop and taskbar features.
			Desktop.getDesktop().run {
				setDefaultMenuBar(jMenuBar)
				setAboutHandler { aboutAction.showDialog() }
				setPreferencesHandler {
					preferencesAction.actionPerformed(null)
				}
			}
			jMenuBar.maximumSize = Dimension(0, 0)
			Taskbar.getTaskbar().run {
				iconImage = ImageIcon(
					AvailWorkbench::class.java.classLoader.getResource(
						"resources/workbench/AvailHammer.png")
				).image
				setIconBadge(activeVersionSummary)
			}
		}
		// Select an initial module if specified.
		validate()
		setEnablements()
	}

	override fun saveWindowPosition()
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
	 * Perform the first refresh of the workbench.
	 *
	 * @param failedResolutions
	 *   The list of specified Avail roots that could not be resolved.
	 * @param resolutionTime
	 *   The amount of time it took in milliseconds to resolve the module roots.
	 * @param initial
	 *   The name of the module to have selected when the workbench frame is
	 *   opened.
	 * @param afterExecute
	 *   The function to run after the refresh is complete.
	 */
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
			invokeLater {
				screenState.openEditors.values.forEach {
					it.open(this@AvailWorkbench)
				}
				if (screenState.structureViewLayoutConfig.isNotEmpty())
				{
					val editor = openEditors.values.first()
					structureViewPanel.apply {
						layoutConfiguration.parseInput(
							screenState.structureViewLayoutConfig)
						layoutConfiguration.placement?.let {
							this.bounds = it
						}
						this.extendedState = layoutConfiguration.extendedState
						updateView(editor)
					}
				}
			}
		}
	}

	/**
	 * Answer the [AvailProjectRoot] for the given [AvailProjectRoot.name].
	 *
	 * @param targetRootName
	 *   The [AvailProjectRoot.name] to retrieve.
	 * @return
	 *   The [AvailProjectRoot] or `null` if not found.
	 */
	fun getProjectRoot (targetRootName: String): AvailProjectRoot? =
		availProject.availProjectRoots.firstOrNull {
			it.name == targetRootName
		}

	companion object
	{
		/**
		 * Truncate progress reports containing more than this number of
		 * individual modules in progress.
		 */
		private const val maximumModulesInProgressReport = 20

		/** Determine at startup whether we should show developer commands. */
		val showDeveloperTools =
			"true".equals(
				System.getProperty("availDeveloper"), ignoreCase = true)

		/**
		 * The key for the dark mode [System.setProperty] [darkMode].
		 */
		const val DARK_MODE_KEY = "darkMode"

		/**
		 * An indicator of whether to show the user interface in dark (Darcula)
		 * mode.
		 */
		val darkMode: Boolean =
			System.getProperty(DARK_MODE_KEY)?.equals("true") ?: true

		/**
		 * The background color of the input field when a command is running.
		 */
		val inputBackgroundWhenRunning = AdaptiveColor(
			light = LightColors.consoleBackground,
			dark = DarkColors.consoleBackground)

		/**
		 * The foreground color of the input field when a command is running.
		 */
		val inputForegroundWhenRunning = AdaptiveColor(
			light = LightColors.consoleText,
			dark = DarkColors.consoleText)

		/**
		 * The numeric mask for the modifier key suitable for the current
		 * platform.
		 */
		val menuShortcutMask get() =
			Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx

		/**
		 * The current working directory of the Avail virtual machine. Because
		 * Java does not permit the current working directory to be changed, it
		 * is safe to cache the answer at class-loading time.
		 */
		private val currentWorkingDirectory: File

		// Obtain the current working directory. Try to resolve this location to
		// its real path. If resolution fails, then just use the value of the
		// "user.dir" system property.
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
		 * Launch the [Avail&#32;builder][AvailBuilder] [UI][AvailWorkbench].
		 *
		 * @param globalAvailConfiguration
		 *   The [GlobalAvailConfiguration] for the environment this
		 *   [AvailWorkbench] is being launched in.
		 * @param project
		 *   The [AvailProject] to use to launch the workbench.
		 * @param availProjectFilePath
		 *   The String path to the [availProject] configuration file or an
		 *   empty String if the [AvailWorkbench] was started without an
		 *   [AvailProject].
		 * @param initial
		 *   The name of the module to have selected when the workbench frame is
		 *   opened.
		 * @param customWindowTitle
		 *   The title to apply to the Avail Workbench window. If empty, the
		 *   default will be to determine the name based upon the rules
		 *   associated with `useProjectNameAsFullTitle`.
		 * @param useProjectNameAsFullTitle
		 *   `true` indicates only the [AvailProject.name] should be used as the
		 *   title for the entire workbench window; `false` indicates the
		 *   [AvailProject.name] will appear in parenthesis:
		 *   "Avail Workbench (<project name>)"
		 * @param projectManager
		 *   The [AvailProjectManager] that launched this workbench or
		 *   `null` if not launched from an [AvailProjectManager].
		 * @return The launched [AvailWorkbench].
		 * @throws Exception
		 * If something goes wrong.
		 */
		@Throws(Exception::class)
		@JvmStatic
		fun launchWorkbench(
			globalAvailConfiguration: GlobalAvailConfiguration,
			project: AvailProject = AvailProjectV1(
				"--No Project--",
				true,
				AvailRepositories(rootNameInJar = null)),
			availProjectFilePath: String = "",
			initial: String = "",
			customWindowTitle: String = "",
			useProjectNameAsFullTitle: Boolean,
			projectManager: AvailProjectManager?
		): AvailWorkbench
		{
			val rootsString = project.availProjectRoots.joinToString(";") {
				it.modulePath
			}
			val inDarkMode = project.darkMode
			val workbenchWindowTitle =
				when
				{
					customWindowTitle.isNotEmpty() -> customWindowTitle
					useProjectNameAsFullTitle -> project.name
					else -> "Avail Workbench (${project.name})"
				}
			val repositoryDirectory =
				File(project.repositoryLocation.fullPathNoPrefix)
			val fileManager = FileManager()
			Repositories.setDirectoryLocation(repositoryDirectory)
			// Do the slow Swing setup in parallel with other things...
			val swingReady = Semaphore(0)
			val runtimeReady = Semaphore(0)
			if (SystemInfo.isMacOS)
			{
				// enable screen menu bar
				// (moves menu bar from JFrame window to top of screen)
				System.setProperty("apple.laf.useScreenMenuBar", "true")

				// appearance of window title bars
				// possible values:
				//   - "system": use current macOS appearance (light or dark)
				//   - "NSAppearanceNameAqua": use light appearance
				//   - "NSAppearanceNameDarkAqua": use dark appearance
				System.setProperty("apple.awt.application.appearance", "system")
			}
			thread(name = "Set up LAF") {
				if (inDarkMode)
				{
					try
					{
						FlatDarculaLaf.setup()
					}
					catch (ex: Exception)
					{
						System.err.println("Failed to initialize LaF")
					}
					UIManager.put("ScrollPane.smoothScrolling", false)
				}
				swingReady.release()
			}
			val rootResolutionStart = currentTimeMillis()
			val failedResolutions = mutableListOf<String>()
			val semaphore = Semaphore(0)
			val roots = ModuleRoots(fileManager, rootsString) { fails ->
				failedResolutions.addAll(fails)
				semaphore.release()
			}
			semaphore.acquireUninterruptibly()
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
						null -> StringReader("")
						// Load renames from file specified on the command line.
						else -> BufferedReader(
							InputStreamReader(
								FileInputStream(File(renames)), UTF_8))
					}
					val renameParser = RenamesFileParser(reader, roots)
					resolver = renameParser.parse()
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
			val bench = AvailWorkbench(
				project,
				runtime,
				fileManager,
				resolver,
				globalAvailConfiguration,
				availProjectFilePath,
				workbenchWindowTitle,
				projectManager)
			// Inject a breakpoint handler into the runtime to open a debugger.
			runtime.breakpointHandler = { fiber ->
				val debugger = AvailDebugger(bench)
				// Debug just the fiber that hit the breakpoint.
				debugger.gatherFibers { listOf(fiber) }
				debugger.open()
			}
			bench.createBufferStrategy(2)
			bench.ignoreRepaint = true
			invokeLater {
				val initialRefreshTask =
					object : AbstractWorkbenchTask(bench, null)
					{
						override fun executeTaskThen(afterExecute: ()->Unit) =
							bench.initialRefresh(
								failedResolutions,
								resolutionTime,
								initial)
							{
								afterExecute()
							}
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
			return bench
		}

		/**
		 * Launch the [Avail&#32;builder][AvailBuilder] [UI][AvailWorkbench].
		 *
		 * @param globalAvailConfiguration
		 *   The [GlobalAvailConfiguration] for the environment this
		 *   [AvailWorkbench] is being launched in.
		 * @param project
		 *   The [AvailProject] to use to launch the workbench.
		 * @param availProjectFilePath
		 *   The String path to the [availProject] configuration file or an
		 *   empty String if the [AvailWorkbench] was started without an
		 *   [AvailProject].
		 * @param customWindowTitle
		 *   The title to apply to the Avail Workbench window. If empty, the
		 *   default will be to determine the name based upon the rules
		 *   associated with `useProjectNameAsFullTitle`.
		 * @param useProjectNameAsFullTitle
		 *   `true` indicates only the [AvailProject.name] should be used as the
		 *   title for the entire workbench window; `false` indicates the
		 *   [AvailProject.name] will appear in parenthesis:
		 *   "Avail Workbench (<project name>)"
		 * @param projectManager
		 *   The [AvailProjectManager] that launched this workbench or `null` if
		 *   not launched from an [AvailProjectManager].
		 * @throws Exception
		 *   If something goes wrong.
		 */
		@Throws(Exception::class)
		@JvmStatic
		fun launchWorkbenchWithProject(
			project: AvailProject,
			globalAvailConfiguration: GlobalAvailConfiguration,
			availProjectFilePath: String = "",
			customWindowTitle: String = "",
			useProjectNameAsFullTitle: Boolean = false,
			projectManager: AvailProjectManager?
		): AvailWorkbench = launchWorkbench(
				globalAvailConfiguration,
				project,
				availProjectFilePath,
				"",
				customWindowTitle,
				useProjectNameAsFullTitle,
				projectManager)

		/**
		 * Launch the [Avail&#32;builder][AvailBuilder] [UI][AvailWorkbench]
		 * independent of any [AvailProjectManager].
		 *
		 * @param globalAvailConfiguration
		 * 	 The [GlobalAvailConfiguration] for the environment this
		 * 	 [AvailWorkbench] is being launched in.
		 * @param project
		 *   The [AvailProject] to use to launch the workbench.
		 * @param availProjectFilePath
		 *   The String path to the [availProject] configuration file or an
		 *   empty String if the [AvailWorkbench] was started without an
		 *   [AvailProject].
		 * @param customWindowTitle
		 *   The title to apply to the Avail Workbench window. If empty, the
		 *   default will be to determine the name based upon the rules
		 *   associated with `useProjectNameAsFullTitle`.
		 * @param useProjectNameAsFullTitle
		 *   `true` indicates only the [AvailProject.name] should be used as the
		 *   title for the entire workbench window; `false` indicates the
		 *   [AvailProject.name] will appear in parenthesis:
		 *   "Avail Workbench (<project name>)"
		 * @throws Exception
		 *   If something goes wrong.
		 */
		@Throws(Exception::class)
		@JvmStatic
		@Suppress("unused")
		fun launchSoloWorkbench (
			globalAvailConfiguration: GlobalAvailConfiguration,
			project: AvailProject,
			availProjectFilePath: String = "",
			customWindowTitle: String = "",
			useProjectNameAsFullTitle: Boolean = true
		): AvailWorkbench
		{
			System.setProperty(DARK_MODE_KEY, project.darkMode.toString())
			return launchWorkbench(
				globalAvailConfiguration,
				project,
				availProjectFilePath,
				"",
				customWindowTitle,
				useProjectNameAsFullTitle,
				null)
		}
	}
}
