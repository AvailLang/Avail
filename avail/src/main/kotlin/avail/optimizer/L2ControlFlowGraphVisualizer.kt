/*
 * L2ControlFlowGraphVisualizer.kt
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
package avail.optimizer

import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.COMMENT
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.utility.Strings.repeated
import avail.utility.Strings.tag
import avail.utility.Strings.tagIf
import avail.utility.Strings.truncateTo
import avail.utility.deepForEach
import avail.utility.dot.DotWriter
import avail.utility.dot.DotWriter.AttributeWriter
import avail.utility.dot.DotWriter.Companion.node
import avail.utility.dot.DotWriter.CompassPoint
import avail.utility.dot.DotWriter.DefaultAttributeBlockType
import avail.utility.dot.DotWriter.GraphWriter
import avail.utility.notNullAnd
import java.io.IOException
import java.io.UncheckedIOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.ArrayDeque
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
 * @param generator
 *   The [L2Generator], if any, that is in the process of populating the graph.
 */
class L2ControlFlowGraphVisualizer constructor(
	private val fileName: String,
	private val name: String,
	private val charactersPerLine: Int,
	private val controlFlowGraph: L2ControlFlowGraph,
	private val visualizeLiveness: Boolean,
	private val visualizeManifest: Boolean,
	private val visualizeRegisterDescriptions: Boolean,
	private val accumulator: Appendable,
	private val generator: L2Generator? = null)
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
	 * The zero-based node ordering number of the [L2BasicBlock]s, as a
	 * [map][Map] from [L2BasicBlock] to [Int].
	 */
	private val basicBlockNumbers = controlFlowGraph.basicBlockOrder.withIndex()
		.associate { it.value to it.index }

	/**
	 * The node name of the [L2BasicBlock]s, as a [map][Map]
	 * from each [L2BasicBlock] to the [String] that names it.
	 */
	private val basicBlockNames =
		controlFlowGraph.basicBlockOrder.withIndex().associate {
			(index, block) ->
			val offset = block.offset()
			val id = if (offset == -1) index else offset
			val prefix = if (offset == -1) "[id: $id]" else "[pc: $id]"
			val clean = matchUglies.matcher(block.name()).replaceAll("")
			block to "$prefix $clean"
		}

	/**
	 * Answer a descriptive, not necessarily unique name for the specified
	 * [L2BasicBlock].
	 *
	 * @param basicBlock
	 *   The [L2BasicBlock].
	 * @return
	 *   A unique name that includes the [L2BasicBlock]'s level two
	 *   [program&#32;counter][L2BasicBlock.offset] and its non-unique semantic
	 *   [name][L2BasicBlock.name].
	 */
	private fun basicBlockName(basicBlock: L2BasicBlock) =
		basicBlockNames[basicBlock]?:"(not generated: ${basicBlock.name()})"

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
	@Suppress("SpellCheckingInspection")
	private fun basicBlock(
		basicBlock: L2BasicBlock,
		writer: GraphWriter,
		started: Boolean)
	{
		val isCurrent = generator.notNullAnd {
			currentlyReachable() &&
				currentBlock() == basicBlock && !basicBlock.hasControlFlowAtEnd
		}
		val rhs = buildString {
			tag(
				"table",
				"border" to (if (basicBlock.isCold) "0" else "5"),
				"cellspacing" to "0"
			) {
				val instructions = basicBlock.instructions()
				var (fillcolor: String, fontcolor: String) = when
				{
					isCurrent -> currentBlockBackColor to currentBlockForeColor
					!started -> "#202080/303000" to "#ffffff/e0e0e0"
					basicBlock.instructions().any {
						it.isUnreachableInstruction
					} -> "#400000/600000" to "#ffffff/ffffff"
					basicBlock.isLoopHead ->
						"#9070ff/302090" to "#000000/f0f0f0"
					basicBlock.entryPointOrNull() !== null ->
						"#ffd394/604000" to "#000000/e0e0e0"
					else -> "#c1f0f6/104048" to "#000000/e0e0e0"
				}
				fillcolor = writer.adjust(fillcolor)
				fontcolor = writer.adjust(fontcolor)
				tag("tr") {
					tag(
						"td",
						"align" to "left",
						"balign" to "left",
						"border" to "1",
						"sides" to "LTB",
						"bgcolor" to fillcolor
					) {
						font(
							face = "Courier",
							color = fontcolor)
						{
							if (basicBlock.isCold)
							{
								append("COLD<br/>")
							}
							append(escape(basicBlock.name()))
						}
						if (basicBlock.debugNote.isNotEmpty())
						{
							font(
								face = "Courier",
								color = writer.adjust(commentTextColor))
							{
								basicBlock.debugNote.lines().joinTo(
									this@buildString,
									"<br/>",
									transform = ::escape)
							}
						}
					}
					tag(
						"td",
						"align" to "right",
						"border" to "1",
						"sides" to "RTB",
						"bgcolor" to fillcolor
					) {
						font(
							face = "Courier",
							color = writer.adjust(commentTextColor))
						{
							append("#" + (basicBlockNumbers[basicBlock] ?: "?"))
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
								"valign" to "top")
							if (instruction.isPlaceholder) {
								cellAttributes.add(
									"bgcolor" to
										writer.adjust("#ffC090/#604800"))
							}
							else if (basicBlock.isCold)
							{
								cellAttributes.add(
									"bgcolor" to
										writer.adjust(coldInstructionBackColor))
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
			writer.node(basicBlockName(basicBlock)) {
				it.attribute("label", rhs)
			}
			if (isCurrent)
			{
				val manifestText = buildString {
					tag("table", "border" to "0", "cellspacing" to "0") {
						tag("tr") {
							tag(
								"td",
								"balign" to "left",
								"bgcolor" to
									writer.adjust(currentBlockBackColor)
							) {
								manifest(
									generator!!.currentManifest,
									writer,
									generator.currentBlock().predecessorEdges())
							}
						}
					}
				}
				val manifestNodeName = "(current manifest)"
				writer.node(manifestNodeName) {
					it.attribute("style", "rounded,dashed")
					it.attribute("label", manifestText)
				}
				// Draw an edge from the current block to its manifest, which is
				// written as a vertex with no border.
				writer.edge(basicBlockName(basicBlock), manifestNodeName) {
					it.attribute("style", "dashed")
					it.attribute("arrowhead", "dot")
					it.attribute(
						"color", writer.adjust(currentBlockBackColor))
				}
			}
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
	@Suppress("SpellCheckingInspection")
	private fun edge(
		edge: L2PcOperand,
		writer: GraphWriter,
		started: Boolean,
		edgeCounter: AtomicInteger)
	{
		val sourceBlock = edge.sourceBlock()
		val sourceInstruction = edge.instruction
		val targetBlock = edge.targetBlock()
		val isTargetTheUnreachableBlock = targetBlock.instructions()
			.any { it.isUnreachableInstruction }
		var namedOperandType: L2NamedOperandType? = null
		edge.instruction.operandsWithNamedTypesDo { operand, namedType ->
			when (operand)
			{
				is L2PcOperand ->
					if (operand == edge) namedOperandType = namedType
				is L2PcVectorOperand ->
					if (edge in operand.edges) namedOperandType = namedType
			}
		}
		val basicName = edge.optionalName ?: namedOperandType!!.name()
		val edgeLabel = buildString {
			tag(
				"table",
				"border" to "0",
				"cellspacing" to "0"
			) {
				tag("tr") {
					tag("td", "balign" to "left") {
						font(bold = true) {
							append(escape(basicName))
						}
						append("<br/>")

						if ((visualizeLiveness || visualizeManifest)
							&& edge.forcedClampedEntities !== null)
						{
							// Show any clamped entities for this edge.  These
							// are registers and semantic values that are
							// declared always live along this edge, and act as
							// the (cycle breaking) end-roots for dead code
							// analysis.
							font(italic = true) { append("CLAMPED:") }
							append("<br/>")
							font(bold = true) {
								append(repeated("&nbsp;", 4))
								append(escape(edge.forcedClampedEntities))
							}
							append("<br/>")
						}
						if (visualizeLiveness)
						{
							if (edge.alwaysLiveInRegisters.isNotEmpty())
							{
								font(italic = true) {
									append("always live-in:")
								}
								append("<br/>")
								font(bold = true) {
									append(repeated("&nbsp;", 4))
									edge.alwaysLiveInRegisters
										.sortedBy(L2Register<*>::finalIndex)
										.joinTo(this, ", ") { escape(it) }
								}
								append("<br/>")
							}
							val notAlwaysLiveInRegisters =
								edge.sometimesLiveInRegisters.toMutableSet()
							notAlwaysLiveInRegisters.removeAll(
								edge.alwaysLiveInRegisters)
							if (notAlwaysLiveInRegisters.isNotEmpty())
							{
								font(italic = true) {
									append("sometimes live-in:")
								}
								append("<br/>")
								font(bold = true) {
									append(repeated("&nbsp;", 4))
									notAlwaysLiveInRegisters
										.sortedBy(L2Register<*>::finalIndex)
										.joinTo(this, ", ") { escape(it) }
								}
								append("<br/>")
							}
						}
						val manifest = edge.manifestOrNull()
						val predecessorEdges =
							edge.instruction.basicBlock().predecessorEdges()
						if (visualizeManifest && manifest != null)
						{
							manifest(manifest, writer, predecessorEdges)
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
				}
			) { attr: AttributeWriter ->
				// Number each edge uniquely, to allow a multigraph.
				attr.attribute("id", edgeCounter.getAndIncrement().toString())
				if (!targetBlock.isCold && !sourceBlock.isCold)
				{
					attr.attribute("penwidth", "5")
				}
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
					when (namedOperandType!!.purpose!!)
					{
						// Nothing. The default styling will be fine.
						Purpose.SUCCESS -> Unit
						Purpose.FAILURE ->
							attr.attribute("color", "#e54545/c03030")
						Purpose.OFF_RAMP ->
							attr.attribute("style", "dashed")
						Purpose.ON_RAMP ->
						{
							attr.attribute("style", "dashed")
							attr.attribute("color", "#6aaf6a")
						}
						Purpose.REFERENCED_AS_INT ->
						{
							attr.attribute("style", "dashed")
							attr.attribute("color", "#6080ff")
						}
					}
				}
				attr.attribute("label", edgeLabel)
			}
		}
		catch (e: IOException)
		{
			throw UncheckedIOException(e)
		}
	}

	/**
	 * Output a description of the given manifest to the receiver.
	 */
	private fun StringBuilder.manifest(
		manifest: L2ValueManifest,
		writer: GraphWriter,
		predecessorEdges: Iterable<L2PcOperand>)
	{
		val synonyms = manifest.synonymsArray()
		if (synonyms.isNotEmpty())
		{
			font(italic = true) { append("manifest:") }
			synonyms.sort()
			for (synonym in synonyms)
			{
				val pick = synonym.pickSemanticValue()
				synonym(
					writer,
					synonym,
					manifest.restrictionFor(pick),
					manifest.getDefinitions(pick),
					predecessorEdges)
			}
		}
		manifest.postponedInstructions().let { postponements ->
			if (postponements.isNotEmpty())
			{
				append("<br/>")
				font(italic = true) { append("postponements:") }
				append("<br/>")
			}
			val sortedSubmap = postponements.entries.sortedBy { it.key }
			sortedSubmap.forEach { (semanticValue, oldInstructions) ->
				font(color = writer.adjust(
					if (semanticValue.kind == BOXED_KIND) postponementsColor
					else unboxedSynonymColor))
				{
					append(repeated("&nbsp;", 4))
					append(semanticValue.kind.kindName)
					append("/")
					append(escape(semanticValue))
					append(" = ")
					when (oldInstructions.size)
					{
						0 -> append("ERROR: No instructions")
						1 -> append(escape(oldInstructions[0]))
						else ->
						{
							oldInstructions.forEach {
								append("<br/>")
								append(repeated("&nbsp;", 8))
								append(escape(it))
							}
						}
					}
					append("<br/>")
				}
			}
		}
	}

	private fun StringBuilder.synonym(
		writer: GraphWriter,
		synonym: L2Synonym<*>,
		restriction: TypeRestriction,
		definitions: Iterable<L2Register<*>>,
		predecessorEdges: Iterable<L2PcOperand>)
	{
		// If the restriction flags and the available register kinds disagree,
		// show the synonym entry in red.
		val kindsOfRegisters = mutableSetOf<RegisterKind<*>>()
		synonym.semanticValues().mapTo(kindsOfRegisters) { it.kind }
		definitions.mapTo(kindsOfRegisters, L2Register<*>::kind)
		// If any edge has a different synonym or the synonym has a different
		// constraint than in any predecessor edge's manifest, highlight this
		// synonym to show that the basic block altered it in some way.
		var newSynonym = false
		var changedSynonym = false
		var changedRestriction = false
		var changedDefinitions = false
		predecessorEdges.forEach { previousEdge ->
			val otherManifest = previousEdge.manifest()
			val pick = synonym.semanticValues().firstNotNullOfOrNull {
				otherManifest.equivalentSemanticValue(it)
			}
			if (pick == null)
			{
				// None of the semantic values of the synonym are present in
				// that previous edge.  This will color the entire entry to show
				// the synonym is new.
				newSynonym = true
				return@forEach
			}
			val otherSynonym = otherManifest.semanticValueToSynonym(pick)
			if (otherSynonym != synonym)
			{
				// The synonym membership has changed, so highlight the synonym
				// line.
				changedSynonym = true
			}
			if (otherManifest.restrictionFor(pick) != restriction)
			{
				// The restriction changed (or is entirely new).
				changedRestriction = true
			}
			if (otherManifest.getDefinitions(pick) != definitions)
			{
				// There's a new or removed definition.
				changedDefinitions = true
			}
		}
		val isError = (kindsOfRegisters.size != 1 || restriction.isImpossible)
		val isUnboxed = kindsOfRegisters != setOf(BOXED_KIND)
		val (synonymColor, restrictionColor, definitionsColor) = when
		{
			isError -> listOf(errorTextColor, errorTextColor, errorTextColor)
			newSynonym -> listOf(newEntryColor, newEntryColor, newEntryColor)
			else ->
				listOf(
					when
					{
						changedSynonym -> changedEntryColor
						isUnboxed -> unboxedSynonymColor
						else -> null
					},
					if (changedRestriction) changedEntryColor else null,
					if (changedDefinitions) changedEntryColor else null)
		}
		append("<br/>")
		font(color = writer.adjust(synonymColor ?: "")) {
			append(repeated("&nbsp;", 4))
			// Truncate synonyms of Constant(nil), since they tend to be long
			// and not very interesting.
			var synonymText = synonym.toString()
			if (restriction.constantOrNull.notNullAnd { isNil })
			{
				synonymText = synonymText.truncateTo(30)
			}
			append(escape(synonymText))
		}
		append("<br/>")
		font(color = writer.adjust(restrictionColor ?: "")) {
			append(repeated("&nbsp;", 8))
			append(":&nbsp;")
			append(escape(restriction))
		}
		append("<br/>")
		font(color = writer.adjust(definitionsColor ?: "")) {
			append(repeated("&nbsp;", 8))
			definitions.joinTo(this, ", ", "in {", "}")
		}
	}

	/**
	 * The subgraphs ([L2ControlFlowGraph.Zone]s) that have been discovered so
	 * far.
	 */
	private val blocksByZone =
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
			block.zone?.let { zone ->
				blocksByZone.computeIfAbsent(zone) { mutableSetOf() }.add(block)
			}
		}
	}

	/** A counter for uniquely naming subgraphs. */
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
	@Suppress("SpellCheckingInspection")
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
	@Suppress("SpellCheckingInspection")
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
				graph.attribute("fontname", "Helvetica")
				graph.attribute("bgcolor", "#00ffff/000000")
				graph.attribute("rankdir", "TB")
				graph.attribute("newrank", "true")
				graph.attribute("overlap", "false")
				graph.attribute("splines", "true")
				graph.defaultAttributeBlock(DefaultAttributeBlockType.NODE) {
					it.attribute("fontname", "Helvetica")
					it.attribute("bgcolor", "#ffffff/a0a0a0")
					it.attribute("color", "#000000/b0b0b0")
					it.attribute("fixedsize", "false")
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
				val startedBlocks = controlFlowGraph.basicBlockOrder.toSet()
				val unstartedBlocks = startedBlocks
					.flatMapTo(mutableSetOf()) { startedBlock ->
						startedBlock.successorEdges()
							.map(L2PcOperand::targetBlock)
							.filterNot(startedBlocks::contains)
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
				controlFlowGraph.basicBlockOrder
					.deepForEach(L2BasicBlock::predecessorEdges) {
						edge(it, graph, true, edgeCounter)
					}
				unstartedBlocks.deepForEach(L2BasicBlock::predecessorEdges) {
					edge(it, graph, false, edgeCounter)
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
		instruction.operands.forEach { operand: L2Operand ->
			if (operand.operandType === COMMENT)
			{
				font(
					italic = true,
					color = writer.adjust(
						operand.isMisconnected,
						errorTextColor,
						commentTextColor))
				{
					append(escape(operand))
				}
				append("<br/>")
			}
		}
		// Make a note of the current length of the builder. We will need to
		// escape everything after this point.
		val escapeIndex = length
		val desiredTypes = L2OperandType.allOperandTypes - listOf(PC, COMMENT)
		if (instruction.isUnconditionalJumpForward
			&& instruction.offset != -1
			&& (L2_JUMP.jumpTarget(instruction).offset()
				== instruction.offset))
		{
			// Show fall-through jumps in grey.
			val edge = L2_JUMP.jumpTarget(instruction)
			font(
				italic = true,
				color = writer.adjust(
					edge.isMisconnected,
					errorTextColor,
					"#404040/808080"))
			{
				val escapableStart = length
				if (visualizeRegisterDescriptions)
				{
					instruction.appendToWithWarnings(desiredTypes, this) { }
				}
				else
				{
					// Use a simplified instruction output.
					instruction.simpleAppendTo(this)
				}
				replace(
					escapableStart,
					length,
					escape(substring(escapableStart)))
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
				instruction.simpleAppendTo( this)
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

		/**
		 * A color [String] suitable for [GraphWriter.adjust], specifying what
		 * background color to use for an [L2Generator]'s current block.
		 */
		private const val currentBlockBackColor = "#c08080/803030"

		/**
		 * A color [String] suitable for [GraphWriter.adjust], specifying what
		 * foreground text color to use for an [L2Generator]'s current block.
		 */
		private const val currentBlockForeColor = "#200000/ffd0d0"

		private const val commentTextColor = "#404040/a0a0a0"

		private const val unboxedSynonymColor = "#4040c0/a0a0f0"

		private const val postponementsColor = "#803030/ffc0c0"

		private const val newEntryColor = "#209020/b0ffb0"

		private const val changedEntryColor = "#909020/e0e0a0"

		private const val coldInstructionBackColor = "#e0ffff/407070"

		/** Characters that should be removed outright from class names. */
		private val matchUglies = Pattern.compile("[\"\\\\]")

		/**
		 * Escape the specified text for inclusion into an HTML-like identifier.
		 *
		 * @param value
		 *   Something to be converted via [toString] to a [String].
		 * @return
		 *   The escaped text.
		 */
		private fun escape(value: Any?): String = buildString {
			val s = value.toString()
			val limit = s.length
			var i = 0
			while (i < limit)
			{
				val cp = s.codePointAt(i)
				when
				{
					cp > 127 || cp == '"'.code || cp == '<'.code
						|| cp == '>'.code || cp == '&'.code ->
					{
						append("&#")
						append(cp)
						append(';')
					}
					cp == '\n'.code ->
					{
						append("<br/>")
					}
					cp == '\t'.code ->
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

fun StringBuilder.font(
	face: String? = null,
	size: Int? = null,
	bold: Boolean = false,
	italic: Boolean = false,
	color: String? = null,
	body: StringBuilder.()->Unit)
{
	if (face === null
		&& size === null
		&& !bold
		&& !italic
		&& (color === null || color.isEmpty()))
	{
		body()
		return
	}
	val attributes = mutableListOf<Pair<String, String>>()
	face?.let { attributes.add("face" to face) }
	size?.let { attributes.add("point-size" to size.toString()) }
	if (color.notNullAnd(String::isNotEmpty)) attributes.add("color" to color!!)
	tagIf(
		attributes.isNotEmpty(),
		"font",
		*attributes.toTypedArray())
	{
		tagIf(bold, "B")
		{
			tagIf(italic, "I")
			{
				body()
			}
		}
	}
}
