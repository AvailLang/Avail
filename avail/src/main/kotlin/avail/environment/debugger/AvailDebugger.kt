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

package avail.environment.debugger

import avail.AvailDebuggerModel
import avail.AvailRuntimeSupport.AvailLazyFuture
import avail.builder.ModuleName
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
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.PAUSED
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
import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.descriptor.module.A_Module.Companion.stylingRecord
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.PrimitiveTypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.environment.AdaptiveColor
import avail.environment.AvailWorkbench
import avail.environment.MenuBarBuilder
import avail.environment.actions.FindAction
import avail.environment.codeSuitableTextPane
import avail.environment.scroll
import avail.environment.scrollTextWithLineNumbers
import avail.environment.showTextRange
import avail.interpreter.levelOne.L1Disassembler
import avail.persistence.cache.Repository.StylingRecord
import avail.utility.safeWrite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
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
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel.SINGLE_SELECTION
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter
import javax.swing.text.Highlighter.HighlightPainter

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

	val runtime = debuggerModel.runtime

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
				"[$executionState] ${fiberName.asNativeString()}"
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
		override fun actionPerformed(e: ActionEvent)
		{
			stackListPane.selectedValue?.let {
				inspect(it.function().code().toString(), it as AvailObject)
			}
		}
	}

	/** Helper for capturing values and labels for the variables list view. */
	data class Variable(
		val name: String,
		val value: AvailObject)
	{
		val presentationString = "$name = ${stringIfSimple(value)}"

		/**
		 * Answer a [String] representation of the given [value] if it's simple,
		 * otherwise null.
		 */
		private fun stringIfSimple(
			value: AvailObject,
			depth: Int = 0
		): String = when {
			depth > 3 -> "***depth***"
			value.isNil -> "nil"
			value.isString -> value.toString()
			value.isInstanceOf(Types.NUMBER.o) -> value.toString()
			value.isInstanceOf(Types.MESSAGE_BUNDLE.o) ->
				value.message.atomName.asNativeString()
			value.isInstanceOf(Types.METHOD.o) -> value.toString()
			value.isAtom -> value.toString()
			value.isCharacter -> value.toString()
			value.isInstanceOf(mostGeneralVariableType) ->
				"var(${stringIfSimple(value.getValueForDebugger(), depth + 1)})"
			!value.isType -> "(${value.typeTag})"
			value.isTop -> value.toString()
			value.isBottom -> value.toString()
			value.traversed().descriptor() is PrimitiveTypeDescriptor ->
				value.toString()
			value.instanceCount.equalsInt(1) ->
				"${stringIfSimple(value.instance, depth + 1)}'s type"
			else -> "(${value.typeTag})"
		}
	}

	/** Renders variables in the variables list view. */
	class VariablesRenderer : DefaultListCellRenderer()
	{
		override fun getListCellRendererComponent(
			list: JList<*>?,
			variable: Any?,
			index: Int,
			isSelected: Boolean,
			cellHasFocus: Boolean
		): Component = super.getListCellRendererComponent(
			list,
			(variable as Variable).presentationString,
			index,
			isSelected,
			cellHasFocus)
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
					val after = length
					map[pc] = before .. after
					append("\n")
				}
			}
			// Write to the cache, even if it overwrites.
			disassemblyCache[code] = string to map
			then(string, map)
		}
	}

	/**
	 * Information about the source code for some module.
	 *
	 * @constructor
	 * Create a new record with the given content.
	 */
	inner class SourceCodeInfo
	constructor (val module: A_Module)
	{
		val resolverReference = runtime.moduleNameResolver
			.resolve(ModuleName(module.moduleNameNative), null)
			.resolverReference

		val source = AvailLazyFuture<String>(runtime) { withSource ->
			resolverReference.readFileString(
				false,
				{ string, _ -> withSource(string) },
				{ errorCode, _ ->
					// Cheesy, but good enough.
					withSource("Cannot retrieve source: $errorCode")
				})

		}

		val lineEnds = AvailLazyFuture<List<Int>>(runtime) { withLineEnds ->
			source.withValue { string ->
				val ends = string.withIndex()
					.filter { it.value == '\n' }
					.map(IndexedValue<Char>::index)
				withLineEnds(ends)
			}
		}

		val stylingRecord: StylingRecord get() = module.stylingRecord()
	}

	/**
	 * A cache of the source code for [A_Module]s, each with a [List] of
	 * positions in the string where a linefeed occurs.
	 */
	private val sourceCache =
		synchronizedMap<A_Module, SourceCodeInfo>(mutableMapOf())

	/**
	 * Extract the source of the current frame's module, along with the list
	 * of positions where linefeeds are.
	 */
	private fun sourceWithLineEndsAndStylingThen(
		module: A_Module,
		then: (String, List<Int>, StylingRecord)->Unit)
	{
		val info = sourceCache.computeIfAbsent(module, ::SourceCodeInfo)
		info.source.withValue { source ->
			info.lineEnds.withValue { lineEnds ->
				then(source, lineEnds, info.stylingRecord)
			}
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
		KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0))
	{
		override fun actionPerformed(e: ActionEvent) =
			runtime.whenSafePointDo(debuggerPriority) {
				runtime.runtimeLock.safeWrite {
					fiberListPane.selectedValue?.let { fiber ->
						if (fiber.executionState == PAUSED)
						{
							debuggerModel.singleStep(fiber)
						}
					}
				}
			}

		init
		{
			putValue(
				Action.SHORT_DESCRIPTION,
				"Step one instruction, or into a call.")
		}
	}

	private val stepOverAction = object : AbstractDebuggerAction(
		this,
		"Over (F8)",
		KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0))
	{
		init { isEnabled = false }

		override fun actionPerformed(e: ActionEvent)
		{
			JOptionPane.showMessageDialog(
				this@AvailDebugger, "\"Over\" is not implemented")
		}
	}

	private val stepOutAction = object : AbstractDebuggerAction(
		this,
		"Out (⇧F8)",
		KeyStroke.getKeyStroke(KeyEvent.VK_F8, KeyEvent.SHIFT_DOWN_MASK))
	{
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
		KeyStroke.getKeyStroke(KeyEvent.VK_R, AvailWorkbench.menuShortcutMask))
	{
		override fun actionPerformed(e: ActionEvent)
		{
			runtime.whenSafePointDo(debuggerPriority) {
				runtime.runtimeLock.safeWrite {
					fiberListPane.selectedValue?.let { fiber ->
						debuggerModel.resume(fiber)
					}
				}
			}
		}
	}

	private val restartAction = object : AbstractDebuggerAction(
		this,
		"Restart")
	{
		init { isEnabled = false }

		override fun actionPerformed(e: ActionEvent)
		{
			JOptionPane.showMessageDialog(
				this@AvailDebugger, "\"Restart\" is not implemented")
		}
	}

	private val captureAction = object : AbstractDebuggerAction(
		this,
		"Capture")
	{
		override fun actionPerformed(e: ActionEvent)
		{
			val checkBox = e.source as JCheckBox
			val install = checkBox.isSelected
			val installed = debuggerModel.installFiberCapture(install)
			if (!installed)
			{
				JOptionPane.showMessageDialog(
					this@AvailDebugger,
					"Could not " + (if (install) "" else "un") +
						"install capture mode for this debugger",
					"Warning",
					JOptionPane.WARNING_MESSAGE)
			}
			checkBox.isSelected = debuggerModel.isCapturingNewFibers
		}
	}

	private val inspectVariable = object : AbstractDebuggerAction(
		this,
		"Inspect")
	{
		override fun actionPerformed(e: ActionEvent)
		{
			variablesPane.selectedValue?.run { inspect(name, value) }
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
	private val captureButton = JCheckBox(captureAction).also {
		debuggerModel.isCapturingNewFibers
	}

	/** A view of the L1 disassembly for the selected frame. */
	private val disassemblyPane = codeSuitableTextPane(workbench).apply {
		isEditable = false
	}

	/** A view of the source code for the selected frame. */
	private val sourcePane = codeSuitableTextPane(workbench).apply {
		isEditable = false
	}

	/** The list of variables in scope in the selected frame. */
	private val variablesPane = JList(arrayOf<Variable>()).apply {
		cellRenderer = VariablesRenderer()
		selectionMode = SINGLE_SELECTION
		selectionModel.addListSelectionListener {
			if (!it.valueIsAdjusting)
			{
				updateVariableValuePane()
			}
		}
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

	/**
	 * A [Triple] consisting of the current source [String], a [List] of [Int]s
	 * that indicate the position of each linefeed in the source, and the
	 * [StylingRecord] to apply to the text.
	 */
	private var currentSourceAndLineEndsAndStyling:
			Triple<String, List<Int>, StylingRecord> =
		Triple("", emptyList(), StylingRecord(emptyList(), emptyList()))

	/**
	 * The list of fibers has changed.  Refresh its visual presentation,
	 * maintaining the selected fiber if possible.
	 */
	private fun updateFiberList() = fiberListPane.run {
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
						disassemblyPane.highlighter.removeAllHighlights()
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
					SwingUtilities.invokeLater {
						sourcePane.text = "Fetching source..."
					}
					if (module.notNil)
					{
						sourceWithLineEndsAndStylingThen(module) {
								source, lineEnds, stylingRecord ->
							currentSourceAndLineEndsAndStyling =
								Triple(source, lineEnds, stylingRecord)
							SwingUtilities.invokeLater {
								val doc = sourcePane.styledDocument
								doc.remove(0, doc.length)
								doc.insertString(0, source, null)
								stylingRecord.styleRuns.forEach {
										(range, styleName) ->
									doc.setCharacterAttributes(
										range.first - 1,
										range.last - range.first + 1,
										doc.getStyle(styleName),
										false)
								}
								// Setting the source does not immediately
								// update the layout, so postpone scrolling to
								// the selected line.
								SwingUtilities.invokeLater {
									highlightSourceLine(
										frame.currentLineNumber(isTopFrame),
										isTopFrame)
								}
							}
						}
					}
				}
				else
				{
					// We still have to update the highlight.
					highlightSourceLine(
						frame.currentLineNumber(isTopFrame),
						isTopFrame)
				}
			}
		}
	}

	/**
	 * Highlight the line in the [sourcePane] corresponding to the line number
	 * that is listed for the current L1 program counter in the current frame.
	 */
	private fun highlightSourceLine(
		lineNumber: Int,
		isTopFrame: Boolean)
	{
		val (source, lineEnds, _) = currentSourceAndLineEndsAndStyling
		if (lineNumber == 0)
		{
			// Don't change the highlight if the line number is 0, indicating
			// an instruction from a generated phrase.
			return
		}
		val rangeStart = when
		{
			lineNumber <= 1 -> 0
			lineNumber <= lineEnds.size + 1 ->
				lineEnds[lineNumber - 2] + 1
			else -> source.length - 1
		}
		val rangeEnd = when
		{
			lineEnds.isEmpty() -> source.length
			lineNumber <= 1 -> lineEnds[0]
			lineNumber <= lineEnds.size ->
				lineEnds[lineNumber - 1]
			else -> source.length
		}
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
	 * in scope.  Try to preserve selection of a [Variable] with the same
	 * [name][Variable.name] as the previous selection.
	 */
	private fun updateVariablesList()
	{
		val oldName = variablesPane.selectedValue?.name
		val entries = mutableListOf<Variable>()
		stackListPane.selectedValue?.let { frame ->
			val function = frame.function()
			val code = function.code()
			val numArgs = code.numArgs()
			val numLocals = code.numLocals
			val numConstants = code.numConstants
			val numOuters = code.numOuters
			val names = code.declarationNames.map(AvailObject::asNativeString)
				.iterator()
			var frameIndex = 1
			repeat (numArgs)
			{
				entries.add(
					Variable(
						"[arg] " + names.next(),
						frame.frameAt(frameIndex++)))
			}
			repeat (numLocals)
			{
				entries.add(
					Variable(
						"[local] " + names.next(),
						frame.frameAt(frameIndex++)))
			}
			repeat (numConstants)
			{
				entries.add(
					Variable(
						"[const] " + names.next(),
						frame.frameAt(frameIndex++)))
			}
			for (i in 1..numOuters)
			{
				entries.add(
					Variable(
						"[outer] " + names.next(),
						function.outerVarAt(i)))
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
		variablesPane.valueIsAdjusting = true
		try
		{
			val index = entries.indexOfFirst { it.name == oldName }
			variablesPane.setListData(entries.toTypedArray())
			variablesPane.selectedIndices =
				if (index == -1) intArrayOf() else intArrayOf(index)
		}
		finally
		{
			variablesPane.valueIsAdjusting = false
		}
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
		val variable = variablesPane.selectedValue
		// Do some trickery to avoid stringifying null or nil.
		val valueToStringify = when
		{
			variable == null -> trueObject //dummy
			variable.value.isNil -> trueObject //dummy
			else -> variable.value
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
			val string = when
			{
				variable == null -> ""
				variable.value.isNil -> "nil\n"
				else -> "${variable.value.typeTag}\n\n$it\n"
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
				releaseAllFibers()
				if (debuggerModel.isCapturingNewFibers)
				{
					debuggerModel.installFiberCapture(false)
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
						.addComponent(captureButton))
					.addGroup(createSequentialGroup()
						.addComponent(disassemblyPane.scroll(), 100, 100, max)
						.addComponent(
							sourcePane.scrollTextWithLineNumbers(),
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
						.addComponent(captureButton, pref, pref, pref))
					.addGroup(createParallelGroup()
						.addComponent(disassemblyPane.scroll(), 150, 150, max)
						.addComponent(
							sourcePane.scrollTextWithLineNumbers(),
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
		preferredSize = Dimension(1000, 1000)
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
				item(FindAction(workbench))
			}
		}
	}

	/**
	 * For every existing fiber that isn't already captured by another debugger,
	 * capture that fiber with this debugger.  Those fibers are not permitted to
	 * run unless *this* debugger says they may.  Any fibers launched after this
	 * point (say, to compute a print representation or evaluate an expression)
	 * will *not* be captured by this debugger.
	 *
	 * Note that this operation will block the current thread (which should be
	 * a UI-spawned thread) while holding the runtime at a safe point, to ensure
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
		debuggerModel.gatherFibersThen(fibersProvider) {
			captureButton.isSelected = debuggerModel.isCapturingNewFibers
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
		debuggerModel.whenPausedActions.clear()
		debuggerModel.releaseFibersThen { semaphore.release() }
	}
}

/**
 * Helper function to minimize which variables will presented in scope
 * when using the "inspect" action.
 */
@Suppress("UNUSED_PARAMETER")
fun inspect(name: String, value: AvailObject)
{
	// Put a Kotlin debugger breakpoint on the next line.
	@Suppress("UNUSED_EXPRESSION") value
}
