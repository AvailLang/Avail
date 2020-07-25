/*
 * L2ControlFlowGraphVisualizer.kt
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
package com.avail.optimizer

import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operand.L2Operand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operation.L2_JUMP
import com.avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind
import com.avail.utility.Strings.repeated
import com.avail.utility.Strings.tag
import com.avail.utility.dot.DotWriter
import com.avail.utility.dot.DotWriter.AttributeWriter
import com.avail.utility.dot.DotWriter.Companion.node
import com.avail.utility.dot.DotWriter.CompassPoint
import com.avail.utility.dot.DotWriter.DefaultAttributeBlockType
import com.avail.utility.dot.DotWriter.GraphWriter
import java.io.IOException
import java.io.UncheckedIOException
import java.lang.Integer.toHexString
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.ArrayDeque
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

/**
 * An `L2ControlFlowGraphVisualizer` generates a `dot` source file that
 * visualizes an [L2ControlFlowGraph]. It is intended to aid in debugging
 * [L2Chunk]s.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property fileName
 *   The name of the `dot` file.
 * @property name
 *   The [name][L2Chunk.name] of the [L2Chunk], to be used as the name of the
 *   graph.
 * @property charactersPerLine
 *   The number of characters to emit per line. Only applies to formatting of
 *   block comments.
 * @property controlFlowGraph
 *   The [L2ControlFlowGraph] that should be visualized by a `dot` renderer.
 * @property visualizeLiveness
 *   `true` if edges should be annotated with [L2Register] liveness, `false`
 *   otherwise.
 * @property visualizeManifest
 *   `true` if edges should be annotated with their [L2ValueManifest], `false
 *   otherwise`.
 * @property visualizeRegisterDescriptions
 *   Whether to include descriptions with registers.
 * @property accumulator
 *   The [accumulator][Appendable] for the generated `dot` source text.
 *
 * @constructor
 * Construct a new `L2ControlFlowGraphVisualizer` for the specified
 * [L2ControlFlowGraph].
 *
 * @param fileName
 *   The name of the `dot` file.
 * @param name
 *   The [name][L2Chunk.name] of the [L2Chunk], to be used as the name of the
 *   graph.
 * @param charactersPerLine
 *   The number of characters to emit per line. Only applies to formatting of
 *   block comments.
 * @param controlFlowGraph
 *   The [L2ControlFlowGraph] that should be visualized by a `dot` renderer.
 * @param visualizeLiveness
 *   `true` if edges should be annotated with [L2Register] liveness, `false`
 *   otherwise.
 * @param visualizeManifest
 *   `true` if edges should be annotated with their [L2ValueManifest], `false
 *   otherwise`.
 * @param visualizeRegisterDescriptions
 *   Whether to include descriptions with registers.
 * @param accumulator
 *   The [accumulator][Appendable] for the generated `dot` source text.
 */
class L2ControlFlowGraphVisualizer constructor(
	private val fileName: String,
	private val name: String,
	private val charactersPerLine: Int,
	private val controlFlowGraph: L2ControlFlowGraph,
	private val visualizeLiveness: Boolean,
	private val visualizeManifest: Boolean,
	private val visualizeRegisterDescriptions: Boolean,
	private val accumulator: Appendable)
{
	/**
	 * Emit a banner.
	 *
	 * @param writer
	 *   The [DotWriter].
	 * @throws IOException
	 *   If emission fails.
	 */
	@Throws(IOException::class)
	private fun banner(writer: DotWriter)
	{
		writer.blockComment(String.format(
			"""
				
				%s.dot
				Copyright © %s, %s.
				All rights reserved.
				
				Generated by %s - do not modify!
				
				
				""".trimIndent(),
			fileName,
			writer.copyrightOwner,
			LocalDateTime.ofInstant(
				Instant.now(),
				ZoneId.systemDefault()).year,
			L2ControlFlowGraphVisualizer::class.java.simpleName))
	}

	/**
	 * A generator of unique identifiers, for construction of [L2BasicBlock]
	 * names when the [L2ControlFlowGraph] is incomplete or inconsistent.
	 */
	private var blockId = 0

	/**
	 * The node name of the [L2BasicBlock]s, as a [map][Map]
	 * from `L2BasicBlock`s to their names.
	 */
	private val basicBlockNames = mutableMapOf<L2BasicBlock, String>()

	/**
	 * Compute a unique name for the specified [L2BasicBlock].
	 *
	 * @param basicBlock
	 *   The `L2BasicBlock`.
	 * @return
	 *   A unique name that includes the `L2BasicBlock`'s [program
	 *   counter][L2BasicBlock.offset] and its non-unique semantic
	 *   [name][L2BasicBlock.name].
	 */
	private fun basicBlockName(basicBlock: L2BasicBlock): String =
		basicBlockNames.computeIfAbsent(basicBlock) {
			val offset = it.offset()
			val id = if (offset == -1) ++blockId else offset
			val prefix = if (offset == -1) "[id: $id]" else "[pc: $id]"
			"$prefix ${matchUglies.matcher(it.name()).replaceAll("")}"
		}

	/**
	 * Emit the specified [L2BasicBlock].
	 *
	 * @param basicBlock
	 *   A `L2BasicBlock`.
	 * @param writer
	 *   The [GraphWriter] for emission.
	 * @param started
	 *   `true` if the basic block is starting, `false` otherwise.
	 */
	private fun basicBlock(
		basicBlock: L2BasicBlock,
		writer: GraphWriter,
		started: Boolean)
	{
		val rhs = buildString {
			tag("table", "border" to "0", "cellspacing" to "0") {
				val instructions = basicBlock.instructions()
				val first =
					if (instructions.isNotEmpty()) basicBlock.instructions()[0]
					else null
				val fillcolor: String
				val fontcolor: String
				when
				{
					!started ->
					{
						fillcolor = "#202080/303000"
						fontcolor = "#ffffff/e0e0e0"
					}
					basicBlock.instructions().any {
						it.operation() === L2_UNREACHABLE_CODE
					} ->
					{
						fillcolor = "#400000/600000"
						fontcolor = "#ffffff/ffffff"
					}
					basicBlock.isLoopHead ->
					{
						fillcolor = "#9070ff/302090"
						fontcolor = "#000000/f0f0f0"
					}
					first !== null && first.isEntryPoint ->
					{
						fillcolor = "#ffd394/604000"
						fontcolor = "#000000/e0e0e0"
					}
					else ->
					{
						fillcolor = "#c1f0f6/104048"
						fontcolor = "#000000/e0e0e0"
					}
				}
				// The selection of Helvetica as the font is important. Some
				// renderers, like Viz.js, only seem to fully support a small number
				// of standard, widely available fonts:
				//
				// https://github.com/mdaines/viz.js/issues/82
				//
				// In particular, Courier, Arial, Helvetica, and Times are supported.
				tag("tr") {
					tag(
						"td",
						"align" to "left",
						"balign" to "left",
						"border" to "1",
						"sides" to "LTB",
						"bgcolor" to writer.adjust(fillcolor)
					) {
						tag(
							"font",
							"face" to "Courier",
							"color" to writer.adjust(fontcolor)
						) {
							append(escape(basicBlock.name()))
						}
					}
					tag(
						"td",
						"align" to "right",
						"border" to "1",
						"sides" to "RTB",
						"bgcolor" to writer.adjust(fillcolor)
					) {
						tag(
							"font",
							"face" to "Courier",
							"color" to writer.adjust(commentTextColor)
						) {
							append(escape(toHexString(basicBlock.hashCode())))
						}
					}
				}
				if (instructions.isNotEmpty())
				{
					basicBlock.instructions().forEachIndexed {
						port, instruction ->
						tag("tr") {
							val cellAttributes = mutableListOf(
								"colspan" to "2",
								"align" to "left",
								"balign" to "left",
								"border" to "1",
								"port" to (port + 1).toString(),
								"valign" to "top"
							)
							if (instruction.isPlaceholder) {
								cellAttributes.add(
									"bgcolor" to
										writer.adjust("#ff9090/#500000"))
							}
							tag("td", *cellAttributes.toTypedArray()) {
								append(instruction(instruction, writer))
							}
						}
					}
				}
				else
				{
					tag("tr") {
						tag(
							"td",
							"colspan" to "2",
							"align" to "left",
							"balign" to "left",
							"border" to "1",
							"valign" to "top"
						) {
							append("No instructions generated.")
						}
					}
				}
			}
		}
		try
		{
			writer.node(basicBlockName(basicBlock))
				{ it.attribute("label", rhs) }
		}
		catch (e: IOException)
		{
			throw UncheckedIOException(e)
		}
	}

	/**
	 * Emit a control flow edge, which is an [L2PcOperand].
	 *
	 * @param edge
	 *   The [L2PcOperand] to visit.
	 * @param writer
	 *   The [GraphWriter] for emission.
	 * @param started
	 *   Whether code generation has started for the targetBlock.
	 * @param edgeCounter
	 *   An [AtomicInteger], suitable for uniquely numbering edges.
	 */
	private fun edge(
		edge: L2PcOperand,
		writer: GraphWriter,
		started: Boolean,
		edgeCounter: AtomicInteger)
	{
		val sourceBlock = edge.sourceBlock()
		val sourceInstruction = edge.instruction()
		val targetBlock = edge.targetBlock()
		val isTargetTheUnreachableBlock = targetBlock.instructions()
			.any { it.operation() === L2_UNREACHABLE_CODE }
		val types: Array<out L2NamedOperandType> =
			sourceInstruction.operation().operandTypes()
		val operands = sourceInstruction.operands()
		val type = types[listOf(*operands).indexOf(edge)]
		// The selection of Helvetica as the font is important. Some
		// renderers, like Viz.js, only seem to fully support a small number
		// of standard, widely available fonts:
		//
		// https://github.com/mdaines/viz.js/issues/82
		//
		// In particular, Courier, Arial, Helvetica, and Times are
		// supported.
		val edgeLabel = buildString {
			tag("table", "border" to "0", "cellspacing" to "0") {
				tag("tr") {
					tag("td", "balign" to "left") {
						tag("font", "face" to "Helvetica") {
							tag("b") { append(type.name()) }
						}
						append("<br/>")

						if (visualizeLiveness || visualizeManifest)
						{
							// Show any clamped entities for this edge.  These are registers
							// and semantic values that are declared always live along this
							// edge, and act as the (cycle breaking) end-roots for dead code
							// analysis.
							if (edge.forcedClampedEntities !== null)
							{
								tag("font", "face" to "Helvetica") {
									tag("i") { append("CLAMPED:") }
								}
								append("<br/>")
								tag("b") {
									append(repeated("&nbsp;", 4))
									append(edge.forcedClampedEntities)
								}
								append("<br/>")
							}
						}
						if (visualizeLiveness)
						{
							if (edge.alwaysLiveInRegisters.isNotEmpty())
							{
								tag("font", "face" to "Helvetica") {
									tag("i") { append("always live-in:") }
								}
								append("<br/>")
								tag("b") {
									append(repeated("&nbsp;", 4))
									edge.alwaysLiveInRegisters
										.sortedBy { it.finalIndex() }
										.joinTo(this, ", ") {
											escape(it.toString())
										}
								}
								append("<br/>")
							}
							val notAlwaysLiveInRegisters =
								edge.sometimesLiveInRegisters.toMutableSet()
							notAlwaysLiveInRegisters.removeAll(
								edge.alwaysLiveInRegisters)
							if (notAlwaysLiveInRegisters.isNotEmpty())
							{
								tag("font", "face" to "Helvetica") {
									tag("i") {
										append("sometimes live-in:")
									}
								}
								append("<br/>")
								tag("b") {
									append(repeated("&nbsp;", 4))
									notAlwaysLiveInRegisters
										.sortedBy { it.finalIndex() }
										.joinTo(this, ", ") {
											escape(it.toString())
										}
								}
								append("<br/>")
							}
						}
						if (visualizeManifest && edge.manifestOrNull() != null)
						{
							val manifest = edge.manifest()
							val synonyms = manifest.synonymsArray()
							if (synonyms.isNotEmpty())
							{
								tag("font", "face" to "Helvetica") {
									tag("i") { append("manifest:") }

								}
								synonyms.sortBy { it.toString() }
								for (synonym in synonyms)
								{
									// If the restriction flags and the available
									// register kinds disagree, show the synonym
									// entry in red.
									val restriction = manifest.restrictionFor(
										synonym.pickSemanticValue())
									val defs = manifest.definitionsForDescribing(
										synonym.pickSemanticValue())
									val kindsOfRegisters =
										EnumSet.noneOf(RegisterKind::class.java)
									for (register in defs)
									{
										kindsOfRegisters.add(
											register.registerKind())
									}
									val body: StringBuilder.()->Unit = {
										append("<br/>")
										append(repeated("&nbsp;", 4))
										append(escape(synonym.toString()))
										append("<br/>")
										append(repeated("&nbsp;", 8))
										append(":&nbsp;")
										append(escape(restriction.toString()))
										append("<br/>")
										append(repeated("&nbsp;", 8))
										defs.joinTo(this, ", ", "in {", "}") {
											it.toString()
										}
									}
									if (restriction.kinds() == kindsOfRegisters)
										body()
									else
										tag(
											"font",
											"color" to
												writer.adjust(errorTextColor),
											body = body)
								}
							}
						}
					}
				}
			}
		}
		try
		{
			val sourceSubscript =
				sourceBlock.instructions().indexOf(sourceInstruction) + 1
			writer.edge(
				if (edge.isBackward) node(
					basicBlockName(sourceBlock),
					sourceSubscript.toString(),
					CompassPoint.E)
				else node(
					basicBlockName(sourceBlock),
					sourceSubscript.toString()),
				if (edge.isBackward)
				{
					node(basicBlockName(targetBlock), "1")
				}
				else
				{
					node(basicBlockName(targetBlock))
				})
				{ attr: AttributeWriter ->
					// Number each edge uniquely, to allow a multigraph.
					attr.attribute(
						"id", (edgeCounter.getAndIncrement()).toString())
					if (!started)
					{
						attr.attribute("color", "#4040ff/8080ff")
						attr.attribute("style", "dotted")
					}
					else if (isTargetTheUnreachableBlock)
					{
						attr.attribute("color", "#804040/c06060")
						attr.attribute("style", "dotted")
					}
					else if (edge.isBackward)
					{
						attr.attribute("constraint", "false")
						attr.attribute(
							"color",
							if (sourceBlock.zone === null) "#9070ff/6050ff"
							else "#90f0a0/60ff70")
						attr.attribute("style", "dashed")
					}
					else
					{
						when (type.purpose()!!)
						{
							// Nothing. The default styling will be fine.
							L2NamedOperandType.Purpose.SUCCESS -> Unit
							L2NamedOperandType.Purpose.FAILURE ->
								attr.attribute("color", "#e54545/c03030")
							L2NamedOperandType.Purpose.OFF_RAMP ->
								attr.attribute("style", "dashed")
							L2NamedOperandType.Purpose.ON_RAMP ->
							{
								attr.attribute("style", "dashed")
								attr.attribute("color", "#6aaf6a")
							}
							L2NamedOperandType.Purpose.REFERENCED_AS_INT ->
							{
								attr.attribute("style", "dashed")
								attr.attribute("color", "#6080ff")
							}
						}
					}
					attr.attribute("label", edgeLabel.toString())
				}
		}
		catch (e: IOException)
		{
			throw UncheckedIOException(e)
		}
	}

	/**
	 * The subgraphs ([L2ControlFlowGraph.Zone]s) that have been discovered so
	 * far.
	 */
	var blocksByZone =
		mutableMapOf<L2ControlFlowGraph.Zone, MutableSet<L2BasicBlock>>()

	/**
	 * Calculate how the basic blocks form clusters for reification sections.
	 *
	 * @param blocks
	 *   A collection of [L2BasicBlock]s to classify.
	 */
	private fun computeClusters(blocks: Iterable<L2BasicBlock>)
	{
		for (block in blocks)
		{
			val zone = block.zone
			if (zone !== null)
			{
				blocksByZone.computeIfAbsent(zone) { mutableSetOf() }.add(block)
			}
		}
	}

	/** A counter for uniquely naming subgraphs.  */
	private var subgraphNumber = 1

	/**
	 * Render the nodes in this zone as a subgraph (cluster).
	 *
	 * @param zone
	 *   The [L2ControlFlowGraph.Zone] to render.
	 * @param graph
	 *   The [GraphWriter] to render them.
	 * @param isStarted
	 *   A test to tell if a block has started to be generated.
	 * @throws IOException
	 *   If it can't write.
	 */
	@Throws(IOException::class)
	private fun cluster(
		zone: L2ControlFlowGraph.Zone,
		graph: GraphWriter,
		isStarted: (L2BasicBlock) -> Boolean)
	{
		graph.subgraph("cluster_" + subgraphNumber++)
		{ gw: GraphWriter ->
			gw.attribute("fontcolor", "#000000/ffffff")
			gw.attribute("labeljust", "l") // Left-aligned.
			gw.attribute("label", zone.zoneName)
			gw.attribute("color", zone.zoneType.color)
			gw.attribute("bgcolor", zone.zoneType.bgcolor)
			gw.defaultAttributeBlock(DefaultAttributeBlockType.GRAPH)
			{ attr: AttributeWriter ->
				attr.attribute("style", "rounded")
				attr.attribute("penwidth", "5")
			}
			blocksByZone[zone]!!.forEach { block ->
				basicBlock(block, gw, isStarted(block))
			}
		}
	}

	/**
	 * Visualize the [L2ControlFlowGraph] by [writing][DotWriter] an
	 * appropriate `dot` source file to the [accumulator].
	 */
	fun visualize()
	{
		val writer = DotWriter(
			name,
			true,
			charactersPerLine,
			accumulator,
			true,
			"The Avail Foundation")
		try
		{
			banner(writer)
			// The selection of Helvetica as the font is important. Some
			// renderers, like Viz.js, only seem to fully support a small number
			// of standard, widely available fonts:
			//
			// https://github.com/mdaines/viz.js/issues/82
			//
			// In particular, Courier, Arial, Helvetica, and Times are
			// supported.
			writer.graph { graph: GraphWriter ->
				graph.attribute("bgcolor", "#00ffff/000000")
				graph.attribute("rankdir", "TB")
				graph.attribute("newrank", "true")
				graph.attribute("overlap", "false")
				graph.attribute("splines", "true")
				graph.defaultAttributeBlock(DefaultAttributeBlockType.NODE) {
					it.attribute("bgcolor", "#ffffff/a0a0a0")
					it.attribute("color", "#000000/b0b0b0")
					it.attribute("fixedsize", "false")
					it.attribute("fontname", "Helvetica")
					it.attribute("fontsize", "11")
					it.attribute("fontcolor", "#000000/d0d0d0")
					it.attribute("shape", "none")
				}
				graph.defaultAttributeBlock(DefaultAttributeBlockType.EDGE) {
					it.attribute("fontname", "Helvetica")
					it.attribute("fontsize", "8")
					it.attribute("fontcolor", "#000000/dddddd")
					it.attribute("style", "solid")
					it.attribute("color", "#000000/e0e0e0")
				}
				val startedBlocks: Set<L2BasicBlock> =
					controlFlowGraph.basicBlockOrder.toSet()
				val unstartedBlocks = mutableSetOf<L2BasicBlock>()
				for (startedBlock in startedBlocks)
				{
					startedBlock.successorEdgesDo { edge: L2PcOperand ->
						val target = edge.targetBlock()
						if (!startedBlocks.contains(target))
						{
							unstartedBlocks.add(target)
						}
					}
				}
				computeClusters(startedBlocks)
				computeClusters(unstartedBlocks)
				for (zone in blocksByZone.keys)
				{
					cluster(zone, graph) { !unstartedBlocks.contains(it) }
				}
				controlFlowGraph.basicBlockOrder
					.filter { it.zone === null }
					.forEach { basicBlock(it, graph, true) }
				unstartedBlocks
					.filter { it.zone === null }
					.forEach { basicBlock(it, graph, false) }
				val edgeCounter = AtomicInteger(1)
				controlFlowGraph.basicBlockOrder.forEach {
					it.predecessorEdges().forEach { edge ->
						edge(edge, graph, true, edgeCounter)
					}
				}
				unstartedBlocks.forEach {
					it.predecessorEdges().forEach { edge ->
						edge(edge, graph, false, edgeCounter)
					}
				}
			}
		}
		catch (e: IOException)
		{
			throw UncheckedIOException(e)
		}
	}

	/**
	 * Compute a reasonable description of the specified [L2Instruction].
	 * Any [L2PcOperand]s will be ignored in the rendition of the
	 * `L2Instruction`, as they will be described along the edges instead of
	 * within the nodes.
	 *
	 * @param instruction
	 *   An `L2Instruction`.
	 * @param writer
	 *   A [GraphWriter] used to mediate the styling.
	 * @return
	 *   The requested description.
	 */
	private fun instruction(
		instruction: L2Instruction,
		writer: GraphWriter
	): String = buildString {
		// Hoist a comment operand, if one is present.
		instruction.operandsDo { operand: L2Operand ->
			if (operand.operandType() === L2OperandType.COMMENT)
			{
				// The selection of Helvetica as the font is important. Some
				// renderers, like Viz.js, only seem to fully support a
				// small number of standard, widely available fonts:
				//
				// https://github.com/mdaines/viz.js/issues/82
				//
				// In particular, Courier, Arial, Helvetica, and Times are
				// supported.
				tag(
					"font",
					"face" to "Helvetica",
					"color" to writer.adjust(
						operand.isMisconnected,
						errorTextColor,
						commentTextColor)) {
					tag("i") {
						append(escape(operand.toString()))
					}
				}
				append("<br/>")
			}
		}
		// Make a note of the current length of the builder. We will need to
		// escape everything after this point.
		val escapeIndex = length
		val desiredTypes: Set<L2OperandType> =
			EnumSet.complementOf(
				EnumSet.of(L2OperandType.PC, L2OperandType.COMMENT))
		if (instruction.operation() === L2_JUMP
			&& instruction.offset() != -1
			&& (L2_JUMP.jumpTarget(instruction).offset()
				== instruction.offset()))
		{
			// Show fall-through jumps in grey.
			val edge = L2_JUMP.jumpTarget(instruction)
			tag(
				"font",
				"color" to writer.adjust(
					edge.isMisconnected,
					errorTextColor,
					"#404040/808080")) {
				tag("i") {
					val escapableStart = length
					if (visualizeRegisterDescriptions)
					{
						instruction.operation().appendToWithWarnings(
							instruction, desiredTypes, this) { }
					}
					else
					{
						// Use a simplified instruction output.
						instruction.operation().simpleAppendTo(
							instruction, this)
					}
					replace(
						escapableStart,
						length,
						escape(substring(escapableStart)))
				}
			}
			append("<br/>")
		}
		else
		{
			val styleChanges = ArrayDeque<Int>()
			if (visualizeRegisterDescriptions)
			{
				instruction.appendToWithWarnings(this, desiredTypes) {
					assert(it == (styleChanges.size % 2 == 0))
					styleChanges.add(length)
				}
			}
			else
			{
				// Use a simplified instruction output.
				instruction.operation().simpleAppendTo(instruction, this)
			}
			// Escape everything since the saved position.  Add a final sentinel
			// to avoid duplicating code below.
			styleChanges.add(length)
			val escaped = StringBuilder()
			var warningFlag = false
			var regionStart = escapeIndex
			while (!styleChanges.isEmpty())
			{
				val here = styleChanges.remove()
				escaped.append(escape(this.substring(regionStart, here)))
				if (!styleChanges.isEmpty())
				{
					warningFlag = !warningFlag
					if (warningFlag)
					{
						escaped
							.append("<font color=\"")
							.append(writer.adjust(errorTextColor))
							.append("\"><i>")
					}
					else
					{
						escaped.append("</i></font>")
					}
				}
				regionStart = here
			}
			assert(regionStart == length)
			assert(!warningFlag)
			replace(escapeIndex, length, escaped.toString())
		}
	}

	companion object
	{
		/**
		 * A color [String] suitable for [GraphWriter.adjust], specifying what
		 * foreground color to use for error text.
		 */
		private const val errorTextColor = "#e04040/ff6060"

		private const val commentTextColor = "#404040/a0a0a0"

		/** Characters that should be removed outright from class names.  */
		private val matchUglies = Pattern.compile("[\"\\\\]")

		/**
		 * Escape the specified text for inclusion into an HTML-like identifier.
		 *
		 * @param s
		 *   Some arbitrary text.
		 * @return
		 *   The escaped text.
		 */
		private fun escape(s: String): String = buildString {
			val limit = s.length
			var i = 0
			while (i < limit)
			{
				val cp = s.codePointAt(i)
				when
				{
					cp > 127 || cp == '"'.toInt() || cp == '<'.toInt()
					|| cp == '>'.toInt() || cp == '&'.toInt() ->
					{
						append("&#")
						append(cp)
						append(';')
					}
					cp == '\n'.toInt() ->
					{
						append("<br/>")
					}
					cp == '\t'.toInt() ->
					{
						append(repeated("&nbsp;", 4))
					}
					else ->
					{
						appendCodePoint(cp)
					}
				}
				i += Character.charCount(cp)
			}
		}
	}
}
