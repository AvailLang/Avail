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
import avail.anvil.AvailWorkbench
import avail.anvil.PhrasePathStyleApplicator.TokenStyle
import avail.anvil.createScrollPane
import avail.anvil.window.LayoutConfiguration
import avail.anvil.window.WorkbenchFrame
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_String.Companion.copyStringFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.persistence.cache.Repository.PhraseNode
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.Strings.escapedForHTML
import avail.utility.iterableWith
import avail.utility.structures.RunTree
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.GroupLayout
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * The panel for examining a module's [phrase&#32;structures][PhraseNode].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class PhraseViewPanel constructor (
	override val workbench: AvailWorkbench,
	private val onClose: () -> Unit
): WorkbenchFrame("Phrase Structure")
{
	/**
	 * The [AvailEditor] currently presenting its path through the phrase tree
	 * inside this frame.
	 */
	internal var editor: AvailEditor? = null
		private set

	override val layoutConfiguration: LayoutConfiguration =
		LayoutConfiguration.initialConfiguration

	/**
	 * The [JList] that displays the hierarchy of phrases containing the
	 * selected token.
	 */
	private val phraseStructureList = JList<PhraseExplanation>().apply {
		visibleRowCount = 15
		model = DefaultListModel()
		cellRenderer = object : DefaultListCellRenderer()
		{
			override fun getListCellRendererComponent(
				list: JList<*>?,
				phraseStructure: Any?,
				index: Int,
				isSelected: Boolean,
				cellHasFocus: Boolean
			): Component = super.getListCellRendererComponent(
				list,
				(phraseStructure as? PhraseExplanation)?.run
				{
					"<html>$htmlText</html>"
				},
				index,
				isSelected,
				cellHasFocus)
		}
	}

	init
	{
		val panel = JPanel(BorderLayout(20, 20))
		panel.border = EmptyBorder(10, 10, 10, 10)
		background = panel.background
		phraseStructureList.run {
			toolTipText = "Phrase Structure"
			isEnabled = true
			isFocusable = true
		}
		minimumSize = Dimension(550, 350)
		preferredSize = Dimension(550, 600)
		val scrollView = createScrollPane(phraseStructureList).apply {
			// Take up all of the window space that is not already reserved for
			// other pane components.
			preferredSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
		}
		panel.layout = GroupLayout(panel).apply {
			autoCreateGaps = true
			setHorizontalGroup(
				createParallelGroup()
					.addComponent(scrollView))
			setVerticalGroup(
				createSequentialGroup()
					.addComponent(scrollView))
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
		pack()
		isVisible = true
	}

	/**
	 * Update the [phraseStructureList].
	 *
	 * @param targetEditor
	 *   The associated [AvailEditor] for which this [PhraseViewPanel] shows
	 *   phrase structure at the selection.
	 * @param tokenStyle
	 *   The [TokenStyle] at the current cursor position, or `null` if there is
	 *   no such style at that position.
	 */
	fun updateView (
		targetEditor: AvailEditor? = editor,
		tokenStyle: TokenStyle?)
	{
		editor = targetEditor
		val mn = targetEditor?.resolverReference?.moduleName
		this.title = mn?.let {
			"Phrase Structure: ${it.localName}"
		} ?: "Phrase Structure"
		when (targetEditor)
		{
			null -> (phraseStructureList.model as DefaultListModel).clear()
			else -> updatePhraseStructure(tokenStyle)
		}
	}

	/**
	 * Use the [PhraseNode] within the given [TokenStyle] to rebuild the
	 * [phraseStructureList]'s list of [PhraseExplanation]s, attempting to
	 * preserve the selection.  Also use the [TokenStyle.tokenIndexInName], if
	 * non-zero, to highlight the part of the leaf phrase's message name
	 * corresponding to the selected token in the editor.
	 *
	 * If [tokenStyle] is `null`, clear the list.
	 *
	 * @param tokenStyle
	 *   The [TokenStyle] at the current cursor position in the active editor,
	 *   or `null` if there is no [TokenStyle] at that position.
	 */
	private fun updatePhraseStructure(tokenStyle: TokenStyle?)
	{
		// Capture the prior selection, in case we can reselect it after
		// rebuilding the phraseStructureList.
		val oldSelection: PhraseExplanation? = phraseStructureList.selectedValue
		// Reverse the list, so the first element is the top-most phrase.
		(phraseStructureList.model as DefaultListModel).run {
			clear()
			tokenStyle ?: return
			val phraseNode = tokenStyle.phraseNode
			val tokenIndexInName = tokenStyle.tokenIndexInName
			val nodes = phraseNode.iterableWith(PhraseNode::parent).reversed()
			val nodesSize = nodes.size
			var i = 0
			while (i < nodesSize)
			{
				val node = nodes[i++]
				val usedIndices = node.tokenSpans
					.map { it.tokenIndexInName }
					.toSet()
				val runs = RunTree<List<String>>()
				if (node.atomName !== null)
				{
					// This is a send or macro.  Highlight the part of the name
					// in which the subphrase occurs.
					var subindices = (i until nodesSize)
						.takeWhile { nodes[it].atomName === null }
						.map { nodes[it].indexInParent }
					// Consume the chain of indices.
					i += subindices.size
					// Add the indexInParent of the next child as well, but
					// don't consume it.
					if (i < nodesSize)
					{
						subindices = subindices.append(nodes[i].indexInParent)
					}
					if (node === nodes.last())
					{
						// We're at the leaf.  Color only the token indicated
						// in the tokenStyle, to indicate which token of the
						// message this was.
						if (tokenIndexInName > 0)
						{
							node.splitter!!
								.rangeToHighlightForPartIndex(tokenIndexInName)
								.let { range ->
									runs.edit(
										range.first + 0L,
										range.last + 1L
									) { old ->
										(old ?: emptyList()).append(
											"font color='red'")
									}
								}
						}
					}
					else
					{
						val range =
							node.splitter!!.highlightRangeForPath(subindices)
						runs.edit(range.first + 0L, range.last + 1L) { old ->
							(old ?: emptyList()).append("font color='red'")
						}
					}
					// Dim any Simple tokens that didn't actually occur.
					val splitter = node.splitter!!
					val simpleIndices = splitter.allSimpleLeafIndices
					for (index in simpleIndices - usedIndices)
					{
						val range =
							splitter.rangeToHighlightForPartIndex(index)
						// This range is a Simple that doesn't occur at this
						// call site, so de-emphasize it.
						runs.edit(
							range.first + 0L,
							range.last + 1L
						) { old ->
							(old ?: emptyList()).append("font color='gray'")
						}
					}
				}
				val (index, siblingCount) = when (val parent = node.parent)
				{
					null -> -1 to 0
					else -> node.indexInParent to parent.children.size
				}
				val newElement =
					PhraseExplanation(node, index, siblingCount, runs)
				addElement(newElement)
			}
			phraseStructureList.selectedIndex = indexOf(oldSelection)
		}
	}

	/**
	 * An entry in the [phraseStructureList] that shows a send phrase's bundle
	 * name and an indication of where within that name the subphrase below it
	 * occurs.
	 *
	 * @constructor
	 * Construct a new [PhraseExplanation]
	 *
	 * @param phraseNode
	 *   The [PhraseNode] that this is based on.
	 * @param childIndex
	 *   My zero-based index within my parents' children, or -1 if I am a root
	 *   phrase.
	 * @param siblingsCount
	 *   The number of children that my parent has, including me.
	 */
	data class PhraseExplanation(
		val phraseNode: PhraseNode,
		val childIndex: Int,
		val siblingsCount: Int,
		val htmlTagRuns: RunTree<List<String>>)
	{
		/**
		 * Produce suitable HTML text indicating the message sent by this
		 * phrase, and which argument position the next subphrase down occupies.
		 *
		 * Use the [htmlTagRuns] to insert html tag names (and attributes) to
		 * mark up spans of text, in the Avail one-based coordinate system.
		 */
		val htmlText = buildString {
			when (val atomName = phraseNode.atomName)
			{
				null -> append("...")
				else ->
				{
					var here = 1
					htmlTagRuns.forEach { (start, pastEnd, tags) ->
						if (start > here)
						{
							val gap = atomName.copyStringFromToCanDestroy(
								here, start.toInt() - 1, false)
							append(gap.asNativeString().escapedForHTML())
							here = start.toInt()
						}
						val part = atomName.copyStringFromToCanDestroy(
							here, pastEnd.toInt() - 1, false)
						tags.forEach { tag -> append("<$tag>") }
						append(part.asNativeString().escapedForHTML())
						// Close the tags in reverse order, only using up to the
						// space after a tag name if it has attributes.
						tags.reversed().forEach { tag ->
							append("</${tag.substringBefore(' ')}>")
						}
						here = pastEnd.toInt()
					}
					val trailer = atomName.copyStringFromToCanDestroy(
						here, atomName.tupleSize, false)
					append(trailer.asNativeString().escapedForHTML())
				}
			}
		}
	}

	/**
	 * Notify this [PhraseViewPanel] the provided [AvailEditor] is closing.
	 * If the provided [AvailEditor] matches the [editor], clear this
	 * [PhraseViewPanel].
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
				workbench.openEditors.values.first().openStructureView()
			}
		}
	}
}
