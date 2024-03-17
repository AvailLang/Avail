/*
 * AvailDebugger.kt
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

package avail.anvil.debugger

import avail.AvailDebuggerModel
import avail.anvil.AdaptiveColor
import avail.anvil.AvailWorkbench
import avail.anvil.CodeOverlay
import avail.anvil.MenuBarBuilder
import avail.anvil.RenderingEngine.applyStylesAndPhrasePaths
import avail.anvil.SourceCodeInfo.Companion.sourceWithInfoThen
import avail.anvil.actions.FindAction
import avail.anvil.addWindowMenu
import avail.anvil.codeOverlay
import avail.anvil.scroll
import avail.anvil.scrollTextWithLineNumbers
import avail.anvil.shortcuts.ResumeActionShortcut
import avail.anvil.shortcuts.StepIntoShortcut
import avail.anvil.shortcuts.StepOutShortcut
import avail.anvil.shortcuts.StepOverShortcut
import avail.anvil.showTextRange
import avail.anvil.text.CodePane
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.DONT_DEBUG_KEY
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.character.A_Character.Companion.isCharacter
import avail.descriptor.fiber.A_Fiber
import avail.descriptor.fiber.A_Fiber.Companion.continuation
import avail.descriptor.fiber.A_Fiber.Companion.executionState
import avail.descriptor.fiber.A_Fiber.Companion.fiberName
import avail.descriptor.fiber.A_Fiber.Companion.heritableFiberGlobals
import avail.descriptor.fiber.FiberDescriptor.Companion.debuggerPriority
import avail.descriptor.fiber.FiberDescriptor.FiberKind
import avail.descriptor.fiber.FiberDescriptor.FiberKind.Companion.fiberKind
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.caller
import avail.descriptor.functions.A_Continuation.Companion.currentLineNumber
import avail.descriptor.functions.A_Continuation.Companion.frameAt
import avail.descriptor.functions.A_Continuation.Companion.function
import avail.descriptor.functions.A_Continuation.Companion.numSlots
import avail.descriptor.functions.A_Continuation.Companion.pc
import avail.descriptor.functions.A_Continuation.Companion.stackp
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.declarationNames
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numConstants
import avail.descriptor.functions.A_RawFunction.Companion.numLocals
import avail.descriptor.functions.A_RawFunction.Companion.numNybbles
import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.descriptor.module.A_Module.Companion.stylingRecord
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractDescriptor.DebuggerObjectSlots
import avail.descriptor.representation.AvailIntegerValueHelper
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.PrimitiveTypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.interpreter.levelOne.L1Disassembler
import avail.persistence.cache.record.PhrasePathRecord
import avail.persistence.cache.record.StylingRecord
import avail.utility.safeWrite
import avail.utility.structures.EnumMap.Companion.enumMap
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.Collections.synchronizedMap
import java.util.IdentityHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Action
import javax.swing.DefaultListCellRenderer
import javax.swing.GroupLayout
import javax.swing.GroupLayout.PREFERRED_SIZE
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTextArea
import javax.swing.JToggleButton
import javax.swing.JTree
import javax.swing.ListSelectionModel.SINGLE_SELECTION
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter
import javax.swing.text.Highlighter.HighlightPainter
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * [AvailDebugger] presents a user interface for debugging Avail
 * [fibers][A_Fiber].  It's associated with a [AvailWorkbench]. It can be used
 * to explore and experiment with a running Avail program.
 *
 * The layout is as follows:
 * ```
 * ┌──────────────────────┬─────────────────────────────────────┐
 * │                      │                                     │
 * │ Fibers               │ Stack                               │
 * │                      │                                     │
 * ├──────┬──────┬─────┬──┴──────┬────────┬─────────┬───────────┤
 * │ Into │ Over │ Out │ To Line │ Resume │ Restart │ ▢ Capture │
 * ├──────┴──────┴─────┴───────┬─┴────────┴─────────┴───────────┤
 * │                           │                                │
 * │ Code                      │  Source                        │
 * │                           │                                │
 * ├────────────────┬──────────┴────────────────────────────────┤
 * │                │                                           │
 * │ Variables      │ Variable Value                            │
 * │                │                                           │
 * └────────────────┴───────────────────────────────────────────┘
 * ```
 *
 * To trigger the debugger:
 *  1. Run the workbench
 *  2. Build `Availuator`
 *  3. Run "Command" `Run [Breakpoint; Print: “(1 :: integer) + 2”;]`.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Richard Arriaga
 *
 * @property workbench
 *   The [AvailWorkbench] associated with this debugger.
 *
 * @constructor
 * Construct a new [AvailDebugger].
 *
 */
class AvailDebugger internal constructor (
	val workbench: AvailWorkbench
) : JFrame("Avail Debugger")
{
	/**
	 * The [AvailDebuggerModel] through which the fibers are controlled.
	 */
	val debuggerModel = AvailDebuggerModel(workbench.runtime)

	val runtime = workbench.runtime

	/** Renders entries in the list of fibers. */
	class FiberRenderer : DefaultListCellRenderer()
	{
		override fun getListCellRendererComponent(
			list: JList<*>?,
			fiber: Any?,
			index: Int,
			isSelected: Boolean,
			cellHasFocus: Boolean
		): Component = super.getListCellRendererComponent(
			list,
			(fiber as A_Fiber).run {
				"${fiberKind.veryShortName} [$executionState] " +
					fiberName.asNativeString()
			},
			index,
			isSelected,
			cellHasFocus)
	}

	/** Renders entries in the list of frames ([A_Continuation]s) of a fiber. */
	class FrameRenderer : DefaultListCellRenderer()
	{
		override fun getListCellRendererComponent(
			list: JList<*>?,
			frame: Any?,
			index: Int,
			isSelected: Boolean,
			cellHasFocus: Boolean
		): Component = super.getListCellRendererComponent(
			list,
			(frame as A_Continuation).run {
				val code = function().code()
				val module = code.module
				String.format(
					"%s (%s:%d) pc=%d",
					code.methodName.asNativeString(),
					if (module.isNil) "?"
					else module.moduleNameNative,
					frame.currentLineNumber(index == 0),
					frame.pc())
			},
			index,
			isSelected,
			cellHasFocus)
	}

	private val inspectFrame = object : AbstractDebuggerAction(
		this,
		"Inspect")
	{
		override fun updateIsEnabled(busy: Boolean)
		{
			// Do nothing
		}

		override fun actionPerformed(e: ActionEvent)
		{
			stackListPane.selectedValue?.let {
				inspect(it.function().code().toString(), it as AvailObject)
			}
		}
	}

	/** Helper for capturing values and labels for the variables list view. */
	private data class Variable constructor(
		val name: String,
		val value: AvailObject)
	{
		val presentationString = "$name = ${stringIfSimple(value)}"

		/**
		 * Answer a [String] representation of the given [value] if it isn't too
		 * complex, otherwise some sort of summary.
		 */
		private fun stringIfSimple(
			value: AvailObject,
			depth: Int = 0
		): String = when {
			depth > 3 -> "***depth***"
			value.isNil -> "nil"
			value.isString && value.tupleSize < 80 -> value.toString()
			value.isInstanceOf(Types.NUMBER.o) -> value.toString()
			value.isInstanceOf(Types.MESSAGE_BUNDLE.o) ->
				value.message.atomName.asNativeString()
			value.isInstanceOf(Types.METHOD.o) -> value.toString()
			value.isAtom -> value.toString()
			value.isCharacter -> value.toString()
			value.isInstanceOf(mostGeneralVariableType) ->
				"var(${stringIfSimple(value.getValueForDebugger(), depth + 1)})"
			!value.isType -> "(${value.typeTag.name.removeSuffix("_TAG")})"
			value.isTop -> value.toString()
			value.isBottom -> value.toString()
			value.traversed().descriptor() is PrimitiveTypeDescriptor ->
				value.toString()
			value.equals(booleanType) -> "boolean"
			value.instanceCount.equalsInt(1) ->
				"{${stringIfSimple(value.instance, depth + 1)}}ᵀ"
			else -> "(${value.typeTag})"
		}
	}

	/**
	 * Renders variables in the top level (excluding the invisible root) of the
	 * variables tree view.
	 */
	object VariableRenderer : DefaultTreeCellRenderer()
	{
		init
		{
			leafIcon = null
			openIcon = null
			closedIcon = null
		}

		override fun getTreeCellRendererComponent(
			tree: JTree,
			value: Any?,
			sel: Boolean,
			expanded: Boolean,
			leaf: Boolean,
			row: Int,
			hasFocus: Boolean
		): Component
		{
			val name = (value as LazyTreeNode).name
			return super.getTreeCellRendererComponent(
				tree, name, sel, expanded, leaf, row, hasFocus)
		}
	}

	/**
	 * A [DefaultMutableTreeNode] that can generate children dynamically, as
	 * the tree is expanded.
	 */
	class LazyTreeNode constructor(
		value: Any?
	) : DefaultMutableTreeNode(value, true)
	{
		/** Lazily calculate the name. */
		val name: String = when (val v = userObject)
		{
			null -> "null"
			is AvailObject -> v.nameForDebugger()
			is AvailObjectFieldHelper -> v.nameForDebugger()
			is AvailIntegerValueHelper -> v.longValue.toString()
			else -> "unknown(${v.javaClass.simpleName})"
		}

		/** Whether the children of this node have been computed yet. */
		private var childrenComputed = false

		/**
		 * Lazily calculate the list of children.  Answer true if children
		 * were added.
		 */
		fun computeChildren(): Boolean
		{
			if  (childrenComputed) return false
			val list = when (val value = userObject)
			{
				null -> emptyList()
				is AvailObject ->
					value.describeForDebugger().map(::LazyTreeNode)
				is AvailObjectFieldHelper ->
					value.describeForDebugger().map(::LazyTreeNode)
				is AvailIntegerValueHelper -> emptyList()
				else -> emptyList()
			}
			list.forEach(::add)
			childrenComputed = true
			return list.isNotEmpty()
		}
	}

	/**
	 * A cache of the disassembly of [A_RawFunction]s, each with a map from
	 * Level One pc to the character range to highlight for that pc.
	 */
	private val disassemblyCache =
		synchronizedMap<A_RawFunction, Pair<String, Map<Int, IntRange>>>(
			mutableMapOf())

	/**
	 * Produce a Pair containing the textual disassembly of the given
	 * [A_RawFunction] and the map from pc to character range.
	 */
	private fun disassembledWithMapThen(
		code: A_RawFunction,
		then: (String, Map<Int, IntRange>)->Unit)
	{
		// Access the cache in a wait-free manner, where multiple simultaneous
		// requesters for the same code will simply compute the (idempotent)
		// value redundantly.
		disassemblyCache[code]?.let {
			then(it.first, it.second)
			return
		}
		// The disassembly isn't cached yet.  Compute it in an AvailThread.
		runtime.whenRunningInterpretersDo(debuggerPriority) {
			val map = mutableMapOf<Int, IntRange>()
			val string = buildString {
				L1Disassembler(code).printInstructions(
					IdentityHashMap<A_BasicObject, Void>(10), 0)
				{ pc, line, string ->
					val before = length
					append("$pc. [:$line] $string")
					map[pc] = before .. length
					append("\n")
				}
				val pcPastEnd = code.numNybbles + 1
				val returnStart = length
				append("($pcPastEnd. return)")
				map[pcPastEnd] = returnStart .. length
				append("\n")
			}
			// Write to the cache, even if it overwrites.
			disassemblyCache[code] = string to map
			then(string, map)
		}
	}

	/**
	 * The [HighlightPainter] with which to show the current instruction in the
	 * [disassemblyPane].  This is updated to something sensible as part of
	 * opening the frame.
	 */
	private var codeHighlightPainter = DefaultHighlightPainter(Color.BLACK)

	/**
	 * The [HighlightPainter] with which to show the current instruction of any
	 * frame that is *not* the top-of-stack (i.e., for any frame that is
	 * currently waiting for a call to complete).  This is updated to something
	 * sensible as part of opening the frame.
	 */
	private var secondaryCodeHighlightPainter =
		DefaultHighlightPainter(Color.BLACK)

	/**
	 * The single-step action.  Allow the selected fiber to execute one L1
	 * nybblecode.
	 */
	private val stepIntoAction = object : AbstractDebuggerAction(
		this,
		"Into (F7)",
		"Step one instruction, or into a call.",
		StepIntoShortcut)
	{
		override fun updateIsEnabled(busy: Boolean)
		{
			// Do nothing
		}

		override fun actionPerformed(e: ActionEvent) =
			debugger.runFiberAction(AvailDebuggerModel::doSingleStep)
	}

	private val stepOverAction = object : AbstractDebuggerAction(
		this,
		"Over (F8)",
		"Step over one instruction or call in the selected frame.",
		StepOverShortcut)
	{
		override fun updateIsEnabled(busy: Boolean)
		{
			// Do nothing
		}

		override fun actionPerformed(e: ActionEvent) =
			debugger.runFiberAction(AvailDebuggerModel::doStepOver)
	}

	private val stepOutAction = object : AbstractDebuggerAction(
		this,
		"Out (⇧F8)",
		null,
		StepOutShortcut)
	{
		override fun updateIsEnabled(busy: Boolean)
		{
			// Do nothing
		}

		init { isEnabled = false }

		override fun actionPerformed(e: ActionEvent)
		{
			JOptionPane.showMessageDialog(
				this@AvailDebugger, "\"Out\" is not implemented")
		}
	}

	private val stepToLineAction = object : AbstractDebuggerAction(
		this,
		"To Line")
	{
		override fun updateIsEnabled(busy: Boolean)
		{
			// Do nothing
		}

		init { isEnabled = false }

		override fun actionPerformed(e: ActionEvent)
		{
			JOptionPane.showMessageDialog(
				this@AvailDebugger, "\"To Line\" is not implemented")
		}
	}

	private val resumeAction = object : AbstractDebuggerAction(
		this,
		"Resume (⌘R)",
		null,
		ResumeActionShortcut)
	{
		override fun updateIsEnabled(busy: Boolean)
		{
			// Do nothing
		}

		override fun actionPerformed(e: ActionEvent)
		{
			runtime.whenSafePointDo(debuggerPriority) {
				runtime.runtimeLock.safeWrite {
					SwingUtilities.invokeLater {
						fiberListPane.selectedValue?.let { fiber ->
							debuggerModel.resume(fiber)
						}
					}
				}
			}
		}
	}

	private val restartAction = object : AbstractDebuggerAction(
		this,
		"Restart")
	{
		override fun updateIsEnabled(busy: Boolean)
		{
			// Do nothing
		}

		init { isEnabled = false }

		override fun actionPerformed(e: ActionEvent)
		{
			JOptionPane.showMessageDialog(
				this@AvailDebugger, "\"Restart\" is not implemented")
		}
	}

	fun runFiberAction(
		doAction: AvailDebuggerModel.(A_Fiber, A_Continuation)->Unit)
	{
		fiberListPane.selectedValue?.let { fiber ->
			stackListPane.selectedValue?.let { continuation ->
				debuggerModel.runFiberAction(fiber, continuation, doAction)
			}
		}
	}

	private val captureActions: Map<FiberKind, Action> = enumMap { fiberKind ->
		object : AbstractDebuggerAction(this, fiberKind.veryShortName)
		{
			override fun updateIsEnabled(busy: Boolean)
			{
				isEnabled = debuggerModel.isCapturingNewFibers(fiberKind)
					|| runtime.newFiberHandlers[fiberKind]!!.get() === null
			}

			override fun actionPerformed(e: ActionEvent)
			{
				val checkBox = e.source as JToggleButton
				val install = checkBox.isSelected
				val installed =
					debuggerModel.installFiberCapture(fiberKind, install)
				if (!installed)
				{
					val un = if (install) "" else "un"
					JOptionPane.showMessageDialog(
						this@AvailDebugger,
						"Could not ${un}install capture mode for $fiberKind " +
							"fibers for this debugger",
						"Warning",
						JOptionPane.WARNING_MESSAGE)
				}
				checkBox.isSelected =
					debuggerModel.isCapturingNewFibers(fiberKind)
			}
		}.apply {
			fiberKind.icon?.let { icon ->
				putValue(Action.SMALL_ICON, icon)
			}
		}
	}

	private val inspectVariable = object : AbstractDebuggerAction(
		this,
		"Inspect")
	{
		override fun updateIsEnabled(busy: Boolean)
		{
			// Do nothing
		}

		override fun actionPerformed(e: ActionEvent)
		{
			(variablesPane.lastSelectedPathComponent as? LazyTreeNode)?.run {
				inspect(name, (userObject as? AvailObject) ?: nil)
			}
		}
	}

	////////////////////////////////////
	///      Visual components       ///
	////////////////////////////////////

	/** The list of fibers captured by the debugger. */
	private val fiberListPane = JList(arrayOf<A_Fiber>()).apply {
		cellRenderer = FiberRenderer()
		selectionMode = SINGLE_SELECTION
		selectionModel.addListSelectionListener { updateStackList() }
	}

	/** The selected fiber's stack frames. */
	private val stackListPane = JList(arrayOf<A_Continuation>()).apply {
		cellRenderer = FrameRenderer()
		selectionMode = SINGLE_SELECTION
		selectionModel.addListSelectionListener {
			if (!it.valueIsAdjusting)
			{
				updateDisassemblyAndSourcePanes()
				updateVariablesList()
			}
		}
	}
	/** The button to single-step. */
	private val stepIntoButton = JButton(stepIntoAction)
	private val stepOverButton = JButton(stepOverAction)
	private val stepOutButton = JButton(stepOutAction)
	private val stepToLineButton = JButton(stepToLineAction)
	private val resumeButton = JButton(resumeAction)
	private val restartButton = JButton(restartAction)
	private val captureButtons = captureActions.map { (kind, action) ->
		JToggleButton(action).apply {
			toolTipText = kind.name
			icon = action.getValue(Action.SMALL_ICON) as Icon?
		}
	}

	/** A view of the L1 disassembly for the selected frame. */
	private val disassemblyPane = CodePane(workbench, false)


	/** A view of the source code for the selected frame. */
	private val sourcePane = CodePane(workbench, false)

	/**
	 * Change the font to the provided font name and size.
	 *
	 * @param name
	 *   The [name][Font.name] of the [Font] to set.
	 * @param updatedSize
	 *   The size of the [Font] to set.
	 */
	fun changeCodeFont (name: String, updatedSize: Float)
	{
		disassemblyPane.font = Font.decode(name).deriveFont(updatedSize)
		sourcePane.font = Font.decode(name).deriveFont(updatedSize)
	}

	/** The list of variables in scope in the selected frame. */
	private val variablesPane = JTree(LazyTreeNode("dummy"), true).apply {
		cellRenderer = VariableRenderer
		background = null
		isEditable = false
		isEnabled = true
		showsRootHandles = true
		isFocusable = true
		selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
		toggleClickCount = 0
		isRootVisible = false
		selectionModel.addTreeSelectionListener {
			updateVariableValuePane()
		}
		val treeModel = model as DefaultTreeModel
		treeModel.setAsksAllowsChildren(false)
		addTreeExpansionListener(object : TreeExpansionListener {
			override fun treeExpanded(event: TreeExpansionEvent?) {
				(lastSelectedPathComponent as? LazyTreeNode)?.let { n ->
					// This node must already have been computed as part of the
					// look-beneath.  Since we're expanding it and therefore
					// need to decide how to show each child, ask each child to
					// compute its own children internally.
					n.children().asIterator().forEach { c ->
						c as LazyTreeNode
						c.computeChildren()
						treeModel.nodeStructureChanged(c)
					}
				}
			}

			override fun treeCollapsed(event: TreeExpansionEvent?) { }
		})
	}

	/** The stringification of the selected variable. */
	private val variableValuePane = JTextArea().apply {
		tabSize = 2
	}

	/**
	 * The [A_RawFunction] currently displayed in the [disassemblyPane].  This
	 * assists caching to avoid having to disassemble the code repeatedly during
	 * stepping.
	 */
	private var currentCode: A_RawFunction = nil

	/**
	 * The [A_Module] of the [A_RawFunction] currently displayed in the
	 * [sourcePane].  This assists caching to avoid having to fetch the source
	 * repeatedly during stepping.
	 */
	private var currentModule: A_Module = nil

	/** The latest source that was used for styling. */
	private var currentSource = ""

	/** The string that was found to break lines within the file. */
	private var lineDelimiter = ""

	/** The list of line ends in the [currentSource]. */
	private var lineEnds = emptyList<Int>()

	/** The [StylingRecord] to apply to the text. */
	private var stylingRecord =
		StylingRecord(emptyList(), emptyList(), emptyList())

	/** The [PhrasePathRecord] to apply to the text for demarcating phrases. */
	private var phrasePathRecord = PhrasePathRecord()

	/**
	 * The list of fibers has changed.  Refresh its visual presentation,
	 * maintaining the selected fiber if possible.
	 */
	private fun updateFiberList() = fiberListPane.run {
		assert(SwingUtilities.isEventDispatchThread())
		val oldFiber = selectedValue
		var changedFiber = false
		val wasAdjusting = valueIsAdjusting
		val newArray = debuggerModel.debuggedFibers.toTypedArray()
		valueIsAdjusting = true
		try
		{
			setListData(newArray)
			var newIndex = newArray.indexOf(oldFiber)
			if (newIndex == -1 && newArray.isNotEmpty())
			{
				// The old fiber died.  Select the one at the end of the list.
				newIndex = newArray.size - 1
				changedFiber = true
			}
			selectedIndex = newIndex
		}
		finally
		{
			valueIsAdjusting = wasAdjusting
		}
		if (changedFiber)
		{
			updateStackList()
		}
	}

	/**
	 * Either the current fiber has changed or that fiber has made progress, so
	 * update the presented stack frames.
	 */
	private fun updateStackList()
	{
		assert(SwingUtilities.isEventDispatchThread())
		when (val fiber = fiberListPane.selectedValue)
		{
			null ->
			{
				stackListPane.setListData(emptyArray())
			}
			else ->
			{
				var frame = fiber.continuation.makeShared()
				val frames = mutableListOf<A_Continuation>()
				while (frame.notNil)
				{
					frames.add(frame)
					frame = frame.caller() as AvailObject
				}
				stackListPane.valueIsAdjusting = true
				try
				{
					stackListPane.setListData(frames.toTypedArray())
					stackListPane.selectedIndices = intArrayOf(0)
				}
				finally
				{
					stackListPane.valueIsAdjusting = false
				}
			}
		}
	}

	/**
	 * The selected stack frame has changed.  Note that stepping causes the top
	 * stack frame to be replaced.
	 */
	private fun updateDisassemblyAndSourcePanes()
	{
		assert(SwingUtilities.isEventDispatchThread())
		val isTopFrame = stackListPane.selectedIndex == 0
		when (val frame = stackListPane.selectedValue)
		{
			null ->
			{
				currentCode = nil
				disassemblyPane.highlighter.removeAllHighlights()
				disassemblyPane.text = ""
				sourcePane.highlighter.removeAllHighlights()
				sourcePane.text = ""
			}
			else ->
			{
				val code = frame.function().code()
				disassembledWithMapThen(code) { text, map ->
					SwingUtilities.invokeLater {
						if (!code.equals(currentCode))
						{
							currentCode = code
							disassemblyPane.text = text
						}
						val pc = frame.pc()
						val highlightPc = when (isTopFrame)
						{
							true -> frame.pc()
							// Highlight the previous instruction, which is the
							// call that is outstanding.
							else -> map.keys.maxOf {
								if (it < pc) it else Int.MIN_VALUE
							}
						}
						disassemblyPane.highlighter.removeAllHighlights()
						map[highlightPc]?.let { range ->
							disassemblyPane.highlighter.addHighlight(
								range.first,
								range.last + 1,
								if (isTopFrame) codeHighlightPainter
								else secondaryCodeHighlightPainter)
							try
							{
								disassemblyPane.select(
									range.first, range.last + 1)
								disassemblyPane.showTextRange(
									range.first, range.last + 1)
							}
							catch (e: BadLocationException)
							{
								// Ignore.
							}
							catch (e: IllegalArgumentException)
							{
								// Ignore.
							}
						}
					}
				}
				val module = code.module
				if (!module.equals(currentModule))
				{
					currentModule = module
					if (module.notNil)
					{
						sourcePane.text = "Fetching source..."
						sourceWithInfoThen(runtime, module) {
								src, delimiter, ends ->
							SwingUtilities.invokeLater {
								currentSource = src
								lineDelimiter = delimiter
								lineEnds = ends
								stylingRecord = module.stylingRecord()
								val doc = sourcePane.styledDocument
								doc.remove(0, doc.length)
								doc.insertString(0, src, null)
								styleCode()
								// Setting the src does not immediately
								// update the layout, so postpone scrolling to
								// the selected line.
								SwingUtilities.invokeLater {
									highlightSourceLine(
										frame.currentLineNumber(isTopFrame),
										isTopFrame
									)
								}
							}
						}
					}
					else
					{
						currentSource = ""
						sourcePane.text = "(no module)"
					}
				}
				else
				{
					// We still have to update the highlight.
					SwingUtilities.invokeLater {
						highlightSourceLine(
							frame.currentLineNumber(isTopFrame),
							isTopFrame)
					}
				}
			}
		}
	}

	/**
	 * Apply style highlighting to the text in the
	 * [source&#32;pane][sourcePane].
	 */
	internal fun styleCode()
	{
		val stylesheet = workbench.stylesheet
		sourcePane.background = sourcePane.computeBackground(stylesheet)
		sourcePane.foreground = sourcePane.computeForeground(stylesheet)
		codeGuide.guideColor = codeGuide.computeColor()
		applyStylesAndPhrasePaths(
			sourcePane.styledDocument,
			stylesheet,
			stylingRecord,
			phrasePathRecord)
	}

	/**
	 * Highlight the line in the [sourcePane] corresponding to the line number
	 * that is listed for the current L1 program counter in the current frame.
	 */
	private fun highlightSourceLine(
		lineNumber: Int,
		isTopFrame: Boolean)
	{
		if (lineNumber == 0)
		{
			// Don't change the highlight if the line number is 0, indicating
			// an instruction from a generated phrase.
			return
		}
		val rangeStart = when
		{
			lineNumber <= 1 -> 0
			lineNumber <= lineEnds.size + 1 -> lineEnds[lineNumber - 2] + 1
			else -> currentSource.length
		}
		val rangeEnd = when
		{
			lineEnds.isEmpty() -> currentSource.length
			lineNumber <= 1 -> lineEnds[0]
			lineNumber <= lineEnds.size -> lineEnds[lineNumber - 1]
			else -> currentSource.length
		}
		sourcePane.highlighter.removeAllHighlights()
		sourcePane.highlighter.addHighlight(
			rangeStart,
			rangeEnd + 1,
			if (isTopFrame) codeHighlightPainter
			else secondaryCodeHighlightPainter)
		sourcePane.select(rangeStart, rangeEnd)
		sourcePane.showTextRange(rangeStart, rangeEnd)
	}

	/**
	 * The current stack frame has changed, so re-present the variables that are
	 * in scope.  TODO Try to preserve selection of a [Variable] with the same
	 * [name][Variable.name] as the previous selection.
	 */
	private fun updateVariablesList()
	{
		//val oldPath = variablesPane.selectionPath
		val entries = mutableListOf<Variable>()
		stackListPane.selectedValue?.let { frame ->
			val function = frame.function()
			val code = function.code()
			val numArgs = code.numArgs()
			val numLocals = code.numLocals
			val numConstants = code.numConstants
			val numOuters = code.numOuters
			val names = code.declarationNames
				.map { it.asNativeString() }
				.iterator()
			var frameIndex = 1
			repeat(numArgs)
			{
				entries.add(
					Variable(
						"[arg] " + names.next(),
						frame.frameAt(frameIndex++)
					)
				)
			}
			repeat(numLocals)
			{
				entries.add(
					Variable(
						"[local] " + names.next(),
						frame.frameAt(frameIndex++)
					)
				)
			}
			repeat(numConstants)
			{
				entries.add(
					Variable(
						"[const] " + names.next(),
						frame.frameAt(frameIndex++)
					)
				)
			}
			for (i in 1 .. numOuters)
			{
				entries.add(
					Variable(
						"[outer] " + names.next(),
						function.outerVarAt(i)
					)
				)
			}
			for (i in frame.numSlots() downTo frame.stackp())
			{
				// Give the top-of-stack its own name, so that leaving it
				// selected during stepping will continue to re-select it.
				val name =
					if (i == frame.stackp()) "stack top"
					else "stack [$i]"
				entries.add(Variable(name, frame.frameAt(i)))
			}
		}
		val root = variablesPane.model.root as LazyTreeNode
		root.removeAllChildren()
		entries.forEach {
			val node = LazyTreeNode(
				AvailObjectFieldHelper(
					null,
					DebuggerObjectSlots.DUMMY_DEBUGGER_SLOT,
					-1,
					it.value,
					slotName = it.name,
					forcedName = it.presentationString
				)
			)
			node.computeChildren()
			root.add(node)
		}
		val model = variablesPane.model as DefaultTreeModel
		model.nodeStructureChanged(root)
		variablesPane.repaint()

		//TODO Preserve selection based on oldPath
		// variablesPane.selectionPath = oldPath
	}

	/**
	 * A mechanism by which a monotonic [Long] is allocated, to associate with a
	 * variable stringification request; when the stringification completes, the
	 * completion lock is held while the [variableValuePane] is updated, but
	 * only if the associated [Long] is the largest that has been completed.
	 */
	private val paneVersionTracker = object
	{
		val allocator = AtomicLong(0)

		var renderedVersion: Long = -1
	}

	/**
	 * The user has indicated the desire to textually render a variable's value
	 * in the [variableValuePane]. Allow multiple overlapping requests, but only
	 * ever display the most recently allocated request which has completed.  In
	 * theory, we could terminate the stringification fibers corresponding to
	 * values we'll never see (because a newer request has already been
	 * satisfied), but we'll save that for a later optimization.
	 */
	private fun updateVariableValuePane()
	{
		val id = paneVersionTracker.allocator.getAndIncrement()
		val node = variablesPane.lastSelectedPathComponent as? LazyTreeNode
		// Do some trickery to avoid stringifying null or nil.  In either case,
		// just plug in the trueObject instead.
		var v = node?.userObject
		if (v is AvailObjectFieldHelper)
		{
			v = v.value
		}
		if (v is AvailIntegerValueHelper)
		{
			v = v.longValue
		}
		val valueToStringify = when (v)
		{
			null -> trueObject //dummy
			is AvailObject -> v.ifNil { trueObject }
			else -> trueObject //any non-AvailObject
		}
		runtime.stringifyThen(
			valueToStringify,
			setup = {
				// Prevent the debugger from capturing this fiber, or any fibers
				// that it forks.
				heritableFiberGlobals =
					heritableFiberGlobals.mapAtPuttingCanDestroy(
						DONT_DEBUG_KEY.atom, trueObject, true)
			})
		{
			// Undo the above trickery.
			val string = when (v)
			{
				null -> ""
				nil -> "nil\n"
				is AvailObject -> "${v.typeTag}\n\n$it\n"
				else -> "${v.javaClass.simpleName}\n\n$v\n"
			}
			// Delegate to the UI thread, for safety and simplicity.
			SwingUtilities.invokeLater {
				// We're now in the UI thread, so check if we should replace the
				// text.  There's no need for a lock, since the UI thread runs
				// such actions serially.
				if (id > paneVersionTracker.renderedVersion)
				{
					// It's a more recent stringification than the currently
					// displayed string, so replace it.
					variableValuePane.text = string
					paneVersionTracker.renderedVersion = id
				}
			}
		}
	}

	/**
	 * The [code&#32;guide][CodeOverlay] for the [source&#32;pane][sourcePane].
	 */
	private val codeGuide: CodeOverlay get() = sourcePane.codeOverlay

	/** Construct the user interface and display it. */
	fun open()
	{
		// Pay attention to any debugged fibers changing state.
		debuggerModel.whenPausedActions.add {
			if (it === fiberListPane.selectedValue)
			{
				if (it.executionState.indicatesTermination)
				{
					// This fiber has ended, so get rid of all fibers that have
					// ended.
					updateFiberList()
				}
				else
				{
					// Regenerate the stack.
					updateStackList()
				}
			}
			repaint()
		}
		debuggerModel.whenAddedFiberActions.add {
			updateFiberList()
		}
		addWindowListener(object : WindowAdapter()
		{
			override fun windowClosing(e: WindowEvent) {
				workbench.closeDebugger(this@AvailDebugger)
				releaseAllFibers()
				FiberKind.all.forEach { kind ->
					if (debuggerModel.isCapturingNewFibers(kind))
					{
						debuggerModel.installFiberCapture(kind, false)
					}
				}
			}
		})
		val panel = JPanel(BorderLayout(20, 20))
		panel.border = EmptyBorder(10, 10, 10, 10)
		background = panel.background
		panel.layout = GroupLayout(panel).apply {
			val pref = PREFERRED_SIZE
			//val def = DEFAULT_SIZE
			val max = Int.MAX_VALUE
			autoCreateGaps = true
			setHorizontalGroup(
				createParallelGroup()
					.addGroup(createSequentialGroup()
						.addComponent(fiberListPane.scroll(), 100, 100, max)
						.addComponent(stackListPane.scroll(), 200, 200, max))
					.addGroup(createSequentialGroup()
						.addComponent(stepIntoButton)
						.addComponent(stepOverButton)
						.addComponent(stepOutButton)
						.addComponent(stepToLineButton)
						.addComponent(resumeButton)
						.addComponent(restartButton)
						.addGroup(
							createSequentialGroup().apply {
								captureButtons.forEach { addComponent(it) }
							}))
					.addGroup(createSequentialGroup()
						.addComponent(disassemblyPane.scroll(), 100, 100, max)
						.addComponent(
							sourcePane.scrollTextWithLineNumbers(
								workbench,
								workbench.globalSettings.editorGuideLines),
							100,
							100,
							max))
					.addGroup(createSequentialGroup()
						.addComponent(variablesPane.scroll(), 60, 60, max)
						.addComponent(
							variableValuePane.scroll(), 100, 100, max)))
			setVerticalGroup(
				createSequentialGroup()
					.addGroup(createParallelGroup()
						.addComponent(fiberListPane.scroll(), 60, 60, max)
						.addComponent(stackListPane.scroll(), 60, 60, max))
					.addGroup(createParallelGroup()
						.addComponent(stepIntoButton, pref, pref, pref)
						.addComponent(stepOverButton, pref, pref, pref)
						.addComponent(stepOutButton, pref, pref, pref)
						.addComponent(stepToLineButton, pref, pref, pref)
						.addComponent(resumeButton, pref, pref, pref)
						.addComponent(restartButton, pref, pref, pref)
						.addGroup(
							createParallelGroup().apply {
								captureButtons.forEach {
									addComponent(it, pref, pref, pref)
								}
							}))
					.addGroup(createParallelGroup()
						.addComponent(disassemblyPane.scroll(), 150, 150, max)
						.addComponent(
							sourcePane.scrollTextWithLineNumbers(
								workbench,
								workbench.globalSettings.editorGuideLines),
							150,
							150,
							max))
					.addGroup(createParallelGroup()
						.addComponent(variablesPane.scroll(), 80, 80, max)
						.addComponent(variableValuePane.scroll(), 80, 80, max)))
			linkSize(
				SwingConstants.HORIZONTAL,
				stepIntoButton,
				stepOverButton,
				stepOutButton,
				stepToLineButton,
				resumeButton,
				restartButton)
		}
		minimumSize = Dimension(550, 350)
		preferredSize = Dimension(1040, 900)
		add(panel)
		pack()
		codeHighlightPainter = run {
			val selectionColor = disassemblyPane.selectionColor
			val currentLineColor = AdaptiveColor(
				selectionColor.darker(), selectionColor.brighter()
			).blend(background, 0.2f)
			DefaultHighlightPainter(currentLineColor.color)
		}
		secondaryCodeHighlightPainter = run {
			val washedOut = AdaptiveColor.blend(
				codeHighlightPainter.color,
				background,
				// Half as strong as the primary highlight.
				0.5f)
			DefaultHighlightPainter(washedOut)
		}

		stackListPane.componentPopupMenu = JPopupMenu("Stack").apply {
			add(inspectFrame)
		}
		variablesPane.componentPopupMenu = JPopupMenu("Variable").apply {
			add(inspectVariable)
		}
		isVisible = true
		updateFiberList()
	}

	init
	{
		jMenuBar = MenuBarBuilder.createMenuBar {
			menu("Edit")
			{
				item(FindAction(workbench, this@AvailDebugger))
			}
			addWindowMenu(this@AvailDebugger)
		}
	}

	/**
	 * For every existing fiber that isn't already captured by another debugger,
	 * capture that fiber with this debugger.  Those fibers are not permitted to
	 * run unless *this* debugger says they may.  Any fibers launched after this
	 * point (say, to compute a print representation or evaluate an expression)
	 * will *not* be captured by this debugger.
	 *
	 * Note that this operation will block the current thread (which should be a
	 * UI-spawned thread) while holding the runtime at a safe point, to ensure
	 * no other fibers are running, and to ensure other debuggers don't conflict
	 * with this one.
	 *
	 * @param fibersProvider
	 *   A nullary function that will be executed within a safe point to produce
	 *   the collection of fibers to be debugged.  Already-terminated fibers
	 *   will be automatically excluded.
	 */
	fun gatherFibers(
		fibersProvider: () -> Collection<A_Fiber>)
	{
		val semaphore = Semaphore(0)
		runtime.whenSafePointDo(debuggerPriority) {
			debuggerModel.gatherFibers(fibersProvider)
			(FiberKind.all zip captureButtons).forEach { (kind, button) ->
				button.isSelected = debuggerModel.isCapturingNewFibers(kind)
			}
			semaphore.release()
		}
		semaphore.acquire()
	}

	/**
	 * Un-capture all of the debugger's captured fibers, allowing them to
	 * continue running freely.
	 */
	private fun releaseAllFibers()
	{
		val semaphore = Semaphore(0)
		runtime.whenSafePointDo(debuggerPriority) {
			runtime.runtimeLock.safeWrite {
				debuggerModel.whenPausedActions.clear()
				debuggerModel.releaseFibersThen { semaphore.release() }
			}
		}
		semaphore.acquire()
	}
}

/**
 * Helper function to minimize which variables will be presented in scope when
 * using the "inspect" action.
 */
@Suppress("UNUSED_PARAMETER")
fun inspect(name: String, value: AvailObject)
{
	// Put a Kotlin debugger breakpoint on the next line.
	@Suppress("UNUSED_EXPRESSION") value
}
