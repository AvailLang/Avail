/*
 * StructureViewPanel.kt
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

package avail.anvil.views

import avail.anvil.AvailEditor
import avail.anvil.AvailEditor.ManifestEntryInDocument
import avail.anvil.AvailWorkbench
import avail.anvil.createScrollPane
import avail.anvil.icons.structure.ExpansionIcons
import avail.anvil.icons.structure.SideEffectIcons
import avail.anvil.icons.structure.SortIcons
import avail.anvil.nodes.ManifestEntryNameNode
import avail.anvil.nodes.ManifestEntryNode
import avail.anvil.window.LayoutConfiguration
import avail.anvil.WorkbenchFrame
import avail.compiler.ModuleManifestEntry
import avail.compiler.SideEffectKind
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.BorderFactory.createLineBorder
import javax.swing.GroupLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JTree
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * The panel for a module's structure view that lists the top level
 * [ModuleManifestEntry]s.
 *
 * @author Richard Arriaga
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class StructureViewPanel constructor (
	override val workbench: AvailWorkbench,
	private val onClose: () -> Unit
): WorkbenchFrame("Structure")
{
	/**
	 * The presently associated [AvailEditor] for the displayed
	 * [ModuleManifestEntry]s or `null` if none displayed.
	 */
	internal var editor: AvailEditor? = null
		private set

	override val layoutConfiguration: LayoutConfiguration =
		LayoutConfiguration.initialConfiguration

	/**
	 * The list of [ManifestEntryInDocument]s being displayed.
	 */
	private var manifestEntriesInDocument = listOf<ManifestEntryInDocument>()

	/**
	 * The [Set] of [SideEffectKind]s that are to be excluded from the view.
	 */
	private val filterExcludeSet = mutableSetOf<SideEffectKind>()

	/**
	 * The last selected [SortBy] for sorting the [manifestEntriesInDocument] in
	 * the view.
	 */
	private var lastSortBy = SortBy.LINE_NUMBER

	/**
	 * The path to the module.
	 */
	private val modulePath = JLabel("")

	private fun selectedBorder (): Border =
		BorderFactory.createCompoundBorder(
			createLineBorder(
				Color.GREEN, SELECTION_BORDER_THICKNESS),
			EmptyBorder(3, 3, 3, 3))

	private fun unselectedBorder (): Border =
		BorderFactory.createCompoundBorder(
			createLineBorder(
				translucent, SELECTION_BORDER_THICKNESS),
			EmptyBorder(3, 3, 3, 3))

	/**
	 * The top bar for choosing options for viewing the structure.
	 */
	private val optionsBar = JPanel().apply {
		minimumSize = Dimension(500, 50)
		preferredSize = Dimension(500, 50)
		this.layout = FlowLayout(FlowLayout.LEFT)

		val expandAll = JButton(ExpansionIcons.EXPAND_ALL.icon(19))
		expandAll.apply {
			toolTipText = ExpansionIcons.EXPAND_ALL.toolTip
			border = unselectedBorder()
			addActionListener {
				var i = 0
				while (i < structureViewTree.rowCount) {
					structureViewTree.expandRow(i)
					i++
				}
			}
		}
		add(expandAll)
		val collapseAll = JButton(ExpansionIcons.COLLAPSE_ALL.icon(19))
		collapseAll.apply {
			toolTipText = ExpansionIcons.COLLAPSE_ALL.toolTip
			border = unselectedBorder()
			addActionListener {
				var i = 0
				while (i < structureViewTree.rowCount) {
					structureViewTree.collapseRow(i)
					i++
				}
			}
		}
		add(collapseAll)

		add(JSeparator())
		add(JSeparator())

		val sortAlpha = JButton(SortIcons.ALPHABETICAL.icon(19))
		sortAlpha.apply {
			isSelected = false
			toolTipText = SortIcons.ALPHABETICAL.toolTip
			border = unselectedBorder()
			addActionListener {
				isSelected = !isSelected
				if (isSelected)
				{
					border = selectedBorder()
					updateView(editor, sortBy = SortBy.SUMMARY_TEXT)
				}
				else
				{
					border = unselectedBorder()
					updateView(editor, sortBy = SortBy.LINE_NUMBER)
				}
			}
		}
		add(sortAlpha)

		add(JSeparator())
		add(JSeparator())

		SideEffectKind.entries.forEach {
			val iconLabel = JButton(SideEffectIcons.icon(19, it))
			iconLabel.apply {
				border = selectedBorder()
				toolTipText = it.toolTipText
				addActionListener { _ ->
					if (filterExcludeSet.contains(it))
					{
						iconLabel.border = selectedBorder()
						filterExcludeSet.remove(it)
					}
					else
					{
						iconLabel.border = unselectedBorder()
						filterExcludeSet.add(it)
					}
					updateView(editor)
				}
			}
			add(iconLabel)
		}
	}

	/**
	 * The [JTree] that contains the structure.
	 */
	private val structureViewTree: JTree =
		JTree(DefaultMutableTreeNode("Structure")).apply {
			ToolTipManager.sharedInstance().registerComponent(this)
		}

	init
	{
		val panel = JPanel(BorderLayout(20, 20))
		panel.border = EmptyBorder(10, 10, 10, 10)
		background = panel.background
		structureViewTree.run {
			background = null
			toolTipText = "Structure View"
			isEditable = false
			isEnabled = true
			isFocusable = true
			selectionModel.selectionMode =
				TreeSelectionModel.SINGLE_TREE_SELECTION
			toggleClickCount = 0
			showsRootHandles = true
			isRootVisible = false
			cellRenderer = treeRenderer
			addMouseListener(
				object : MouseAdapter()
				{
					override fun mouseClicked(e: MouseEvent)
					{
						if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1)
						{
							e.consume()
							doubleClick()
						}
					}
				})
			addKeyListener(object: KeyAdapter() {
				override fun keyPressed(e: KeyEvent)
				{
					if(e.keyCode == KeyEvent.VK_ENTER)
					{
						doubleClick()
					}
				}
			})
		}
		minimumSize = Dimension(550, 350)
		preferredSize = Dimension(550, 600)
		val scrollView = createScrollPane(structureViewTree).apply {
			verticalScrollBarPolicy =
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
			// Take up all of the window space that is not already reserved for
			// other pane components.
			preferredSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
		}
		panel.layout = GroupLayout(panel).apply {
			autoCreateGaps = true
			setHorizontalGroup(
				createParallelGroup()
					.addComponent(optionsBar, GroupLayout.Alignment.LEADING)
					.addComponent(scrollView)
					.addComponent(modulePath))
			setVerticalGroup(
				createSequentialGroup()
					.addComponent(optionsBar)
					.addComponent(scrollView)
					.addComponent(modulePath))
		}
		setLocationRelativeTo(workbench)
		add(panel)
		addWindowListener(object : WindowAdapter()
		{
			override fun windowClosing(e: WindowEvent)
			{
				onClose()
			}
		})
	}

	/**
	 * The action to perform when a node in the [structureViewTree] is
	 * double-clicked.
	 */
	private fun JTree.doubleClick()
	{
		val path = structureViewTree.selectionPath ?: return
		when (val selection = path.lastPathComponent)
		{
			is ManifestEntryNode ->
			{
				editor?.toFront()
				editor?.goTo(selection.entry)
				editor?.sourcePane?.requestFocus()
			}
			is ManifestEntryNameNode ->
			{
				if (isExpanded(path))
				{
					collapsePath(path)
				}
				else
				{
					expandPath(path)
				}
			}
		}
	}

	/**
	 * Update the [structureViewTree].
	 *
	 * @param targetEditor
	 *   The associated [AvailEditor] that this [StructureViewPanel] shows
	 *   [ModuleManifestEntry]s for.
	 * @param entries
	 *   The list of [ModuleManifestEntry]s to display.
	 * @param sortBy
	 *   The sorting method by which the [ModuleManifestEntry]s should be sorted
	 *   and displayed.
	 */
	fun updateView (
		targetEditor: AvailEditor? = editor,
		entries: List<ManifestEntryInDocument> =
			manifestEntriesInDocument.toList(),
		sortBy: SortBy = lastSortBy,
		excludeSet: Set<SideEffectKind> = filterExcludeSet.toSet(),
		then: () -> Unit = {})
	{
		assert(SwingUtilities.isEventDispatchThread())
		editor = targetEditor
		manifestEntriesInDocument = entries
		filterExcludeSet.apply {
			clear()
			addAll(excludeSet)
		}
		lastSortBy = sortBy
		val structureTreeRoot =
			DefaultMutableTreeNode("(Structure hidden root)")
		val mn = targetEditor?.resolverReference?.moduleName
		this.title = mn?.let { "Structure: ${it.localName}" } ?: "Structure"
		modulePath.text = mn?.rootRelativeName ?: ""
		if (targetEditor != null)
		{
			val entryMap =
				mutableMapOf<String, MutableList<ManifestEntryInDocument>>()
			entries.forEach {
				val entry = it.entry
				if (!filterExcludeSet.contains(entry.kind))
				{
					entryMap.getOrPut(entry.summaryText) { mutableListOf() }
						.add(it)
				}
			}
			entryMap.values.forEach {
				it.sortBy { m -> m.entry.topLevelStartingLine }
			}
			val mapKeys = entryMap.keys.toMutableList()
			when (sortBy)
			{
				SortBy.SUMMARY_TEXT -> mapKeys.sort()
				SortBy.LINE_NUMBER ->
					mapKeys.sortBy {
						entryMap[it]!!.first().entry.topLevelStartingLine
					}
			}
			mapKeys.forEach {
				val manifestEntries = entryMap[it]!!
				if (manifestEntries.size == 1)
				{
					structureTreeRoot.add(
						ManifestEntryNode(
							targetEditor,
							manifestEntries.first()))
				}
				else
				{
					val nameNode = ManifestEntryNameNode(targetEditor, it)
					manifestEntries.forEach { entry ->
						nameNode.add(ManifestEntryNode(targetEditor, entry))
					}
					structureTreeRoot.add(nameNode)
				}

			}
		}
		structureViewTree.model = DefaultTreeModel(structureTreeRoot)
		then()
	}

	/**
	 * Notify this [StructureViewPanel] the provided [AvailEditor] is closing.
	 * If the provided [AvailEditor] matches the [editor], clear this
	 * [StructureViewPanel].
	 *
	 * @param targetEditor
	 *   The [AvailEditor] that is closing.
	 */
	internal fun closingEditor (targetEditor: AvailEditor)
	{
		if (targetEditor == editor)
		{
			if (workbench.openEditors.isEmpty())
			{
				dispatchEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING))
			}
			else
			{
				workbench.openEditors.values.last().openStructureView(true)
			}
		}
	}

	/**
	 * The types of strategies for sorting the [ModuleManifestEntry]s in the
	 * view.
	 */
	enum class SortBy
	{
		/**
		 * Sort by [ModuleManifestEntry.summaryText].
		 */
		SUMMARY_TEXT,

		/**
		 * Sort by [ModuleManifestEntry.topLevelStartingLine].
		 *
		 * Note for any [ManifestEntryNameNode], the position in the list will
		 * be relative to the [ModuleManifestEntry.topLevelStartingLine] of
		 * [ModuleManifestEntry], with the lowest starting line number.
		 */
		LINE_NUMBER
	}

	companion object
	{
		/**
		 * The translucent color.
		 */
		private val translucent = Color(0f,0f,0f,0f)

		/**
		 * The thickness of the border for options selected in the [optionsBar].
		 */
		private const val SELECTION_BORDER_THICKNESS = 1

		/**
		 * The [DefaultTreeCellRenderer] that knows how to render tree nodes for
		 * my structure tree.
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
			): Component =
				when (value)
				{
					is ManifestEntryNode ->
					{
						val icon = value.icon(tree.rowHeight)
						setLeafIcon(icon)
						toolTipText =
							"Line: ${value.entry.entry.topLevelStartingLine}"
						super.getTreeCellRendererComponent(
							tree,
							value.entry.entry.summaryText,
							selected,
							expanded,
							leaf,
							row,
							hasFocus)
					}
					is ManifestEntryNameNode ->
					{
						super.getTreeCellRendererComponent(
							tree,
							value.entryName,
							selected,
							expanded,
							leaf,
							row,
							hasFocus)
					}
					else -> super.getTreeCellRendererComponent(
						tree, value, selected, expanded, leaf, row, hasFocus)
				}.apply {
					if (AvailWorkbench.darkMode)
					{
						// Fully transparent.
						backgroundNonSelectionColor = Color(45, 45, 45, 0)
					}
				}
		}
	}
}
