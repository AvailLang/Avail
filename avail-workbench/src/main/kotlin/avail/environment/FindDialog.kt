/*
 * FindDialog.kt
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

package avail.environment

import avail.AvailRuntime
import avail.AvailRuntimeConfiguration.activeVersionSummary
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
import avail.environment.LayoutConfiguration.Companion.basePreferences
import avail.environment.LayoutConfiguration.Companion.moduleRenameSourceSubkeyString
import avail.environment.LayoutConfiguration.Companion.moduleRenameTargetSubkeyString
import avail.environment.LayoutConfiguration.Companion.moduleRenamesKeyString
import avail.environment.LayoutConfiguration.Companion.moduleRootsKeyString
import avail.environment.LayoutConfiguration.Companion.moduleRootsSourceSubkeyString
import avail.environment.actions.AboutAction
import avail.environment.actions.BuildAction
import avail.environment.actions.CancelAction
import avail.environment.actions.CleanAction
import avail.environment.actions.CleanModuleAction
import avail.environment.actions.ClearTranscriptAction
import avail.environment.actions.CreateProgramAction
import avail.environment.actions.ExamineCompilationAction
import avail.environment.actions.ExamineModuleManifest
import avail.environment.actions.ExamineRepositoryAction
import avail.environment.actions.ExamineSerializedPhrasesAction
import avail.environment.actions.GenerateDocumentationAction
import avail.environment.actions.GenerateGraphAction
import avail.environment.actions.InsertEntryPointAction
import avail.environment.actions.ParserIntegrityCheckAction
import avail.environment.actions.PreferencesAction
import avail.environment.actions.RefreshAction
import avail.environment.actions.ResetCCReportDataAction
import avail.environment.actions.ResetVMReportDataAction
import avail.environment.actions.RetrieveNextCommand
import avail.environment.actions.RetrievePreviousCommand
import avail.environment.actions.SetDocumentationPathAction
import avail.environment.actions.ShowCCReportAction
import avail.environment.actions.ShowVMReportAction
import avail.environment.actions.SubmitInputAction
import avail.environment.actions.ToggleDebugInterpreterL1
import avail.environment.actions.ToggleDebugInterpreterL2
import avail.environment.actions.ToggleDebugInterpreterPrimitives
import avail.environment.actions.ToggleDebugJVM
import avail.environment.actions.ToggleDebugJVMCodeGeneration
import avail.environment.actions.ToggleDebugWorkUnits
import avail.environment.actions.ToggleFastLoaderAction
import avail.environment.actions.ToggleL2SanityCheck
import avail.environment.actions.TraceCompilerAction
import avail.environment.actions.TraceLoadedStatementsAction
import avail.environment.actions.TraceMacrosAction
import avail.environment.actions.TraceSummarizeStatementsAction
import avail.environment.actions.UnloadAction
import avail.environment.actions.UnloadAllAction
import avail.environment.nodes.AbstractBuilderFrameTreeNode
import avail.environment.nodes.EntryPointModuleNode
import avail.environment.nodes.EntryPointNode
import avail.environment.nodes.ModuleOrPackageNode
import avail.environment.nodes.ModuleRootNode
import avail.environment.streams.BuildInputStream
import avail.environment.streams.BuildOutputStream
import avail.environment.streams.BuildOutputStreamEntry
import avail.environment.streams.BuildPrintStream
import avail.environment.streams.StreamStyle
import avail.environment.streams.StreamStyle.BUILD_PROGRESS
import avail.environment.streams.StreamStyle.ERR
import avail.environment.streams.StreamStyle.INFO
import avail.environment.streams.StreamStyle.OUT
import avail.environment.tasks.BuildTask
import avail.files.FileManager
import avail.io.ConsoleInputChannel
import avail.io.ConsoleOutputChannel
import avail.io.TextInterface
import avail.performance.Statistic
import avail.performance.StatisticReport.WORKBENCH_TRANSCRIPT
import avail.persistence.cache.Repositories
import avail.resolver.ModuleRootResolver
import avail.resolver.ResolverReference
import avail.resolver.ResourceType
import avail.stacks.StacksGenerator
import avail.utility.IO
import avail.utility.cast
import avail.utility.safeWrite
import com.github.weisj.darklaf.LafManager
import com.github.weisj.darklaf.theme.DarculaTheme
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
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.Arrays.sort
import java.util.Collections
import java.util.Collections.synchronizedMap
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
import javax.swing.ImageIcon
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
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
 * [FindDialog] is a dialog used by the [AvailWorkbench] to search for text in
 * the Transcript text area.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property workbench
 *   The [AvailWorkbench] in which to search.
 */
class FindDialog internal constructor (
	private val workbench: AvailWorkbench)
{
	/** The text to find. */
	private var searchText = ""

	//TODO Finish this.
}
