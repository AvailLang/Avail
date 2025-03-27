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

import avail.anvil.AvailWorkbench.Companion.darkMode
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType.Companion.COMMENT
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.L2OperandType.Companion.PC_VECTOR
import avail.interpreter.levelTwo.L2OperandType.Companion.allOperandTypes
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_NOP
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.values.L2SemanticValue
import avail.utility.Strings.increaseIndentation
import avail.utility.Strings.repeated
import avail.utility.Strings.tag
import avail.utility.Strings.tagIf
import avail.utility.Strings.truncateTo
import avail.utility.deepForEach
import avail.utility.dot.DotWriter
import avail.utility.dot.DotWriter.BooleanAttributeName.constraint
import avail.utility.dot.DotWriter.BooleanAttributeName.fixedsize
import avail.utility.dot.DotWriter.BooleanAttributeName.newrank
import avail.utility.dot.DotWriter.BooleanAttributeName.overlap
import avail.utility.dot.DotWriter.BooleanAttributeName.splines
import avail.utility.dot.DotWriter.ColorAttributeName.bgcolor
import avail.utility.dot.DotWriter.ColorAttributeName.color
import avail.utility.dot.DotWriter.ColorAttributeName.fontcolor
import avail.utility.dot.DotWriter.Companion.node
import avail.utility.dot.DotWriter.CompassPoint
import avail.utility.dot.DotWriter.DefaultAttributeBlockType
import avail.utility.dot.DotWriter.GraphWriter
import avail.utility.dot.DotWriter.JustificationAttributeName.Justification.left
import avail.utility.dot.DotWriter.JustificationAttributeName.labeljust
import avail.utility.dot.DotWriter.NumberAttributeName.fontsize
import avail.utility.dot.DotWriter.NumberAttributeName.labelangle
import avail.utility.dot.DotWriter.NumberAttributeName.labeldistance
import avail.utility.dot.DotWriter.NumberAttributeName.penwidth
import avail.utility.dot.DotWriter.RankDirectionAttribuuteName.RankDirection.TopBottom
import avail.utility.dot.DotWriter.RankDirectionAttribuuteName.rankdir
import avail.utility.dot.DotWriter.StringAttributeName.arrowhead
import avail.utility.dot.DotWriter.StringAttributeName.fontname
import avail.utility.dot.DotWriter.StringAttributeName.headlabel
import avail.utility.dot.DotWriter.StringAttributeName.id
import avail.utility.dot.DotWriter.StringAttributeName.label
import avail.utility.dot.DotWriter.StringAttributeName.shape
import avail.utility.dot.DotWriter.StringAttributeName.style
import avail.utility.mapToSet
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
	 * The set of manifests that occur on more than one edge, which is
	 * forbidden.
	 */
	val duplicateManifests = controlFlowGraph.basicBlockOrder
		.flatMap(L2BasicBlock::successorEdges)
		.mapNotNull(L2PcOperand::manifestOrNull)
		// Map from manifest to a list of its occurrences.
		.groupBy { it }
		.entries
		.filter { it.value.size > 1 }
		.mapToSet(transform = Map.Entry<L2ValueManifest, *>::key)

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
	 * A map from each edge (accumulated during basicBlock emission) to the name
	 * of the port in the source block that it should originate from.
	 */
	private val sourcePortNamesByEdge = mutableMapOf<L2PcOperand, String>()

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
	 * @receiver
	 *   The [GraphWriter] for emission.
	 * @param basicBlock
	 *   A `L2BasicBlock`.
	 * @param started
	 *   `true` if the basic block is starting, `false` otherwise.
	 */
	@Suppress("SpellCheckingInspection")
	private fun GraphWriter.basicBlock(
		basicBlock: L2BasicBlock,
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
				var (fill: String, grid: String, font: String) = when
				{
					isCurrent -> Triple(
						currentBlockBackColor,
						currentBlockGridColor,
						currentBlockForeColor)
					!started -> Triple(
						"#202080/303000",
						"#ffffff/e0e0e0",
						"#ffffff/e0e0e0")
					basicBlock.instructions().any {
						it is L2_UNREACHABLE_CODE
					} -> Triple(
						"#400000/600000",
						"#ffffff/ffffff",
						"#ffffff/ffffff")
					basicBlock.isLoopHead -> Triple(
						"#9070ff/302090",
						"#c0c0c0/404040",
						"#000000/f0f0f0")
					basicBlock.entryPointOrNull() !== null -> Triple(
						"#ffd394/604000",
						"#c0c0c0/404040",
						"#000000/e0e0e0")
					else -> Triple(
						"#c1f0f6/104048",
						"#c0c0c0/404040",
						"#000000/e0e0e0")
				}
				val fillcolor = adjust(fill)
				val gridcolor = adjust(grid)
				val fontcolor = adjust(font)
				// Block heading.
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
							face = "Arial",
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
								face = "Arial",
								color = adjust(commentTextColor))
							{
								basicBlock.debugNote.lines().joinTo(
									this@buildString,
									separator = "<br/>",
									prefix = "<br/>",
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
							face = "Arial",
							color = adjust(commentTextColor))
						{
							append("#" + (basicBlockNumbers[basicBlock] ?: "?"))
						}
					}
				}
				if (instructions.isNotEmpty())
				{
					instructions.forEachIndexed { port, instruction ->
						instructionTableRow(
							gridcolor, instruction, this@basicBlock, basicBlock)
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
			node(basicBlockName(basicBlock)) {
				label(rhs)
			}
			if (isCurrent)
			{
				val manifestText = buildString {
					tag("table", "border" to "0", "cellspacing" to "0") {
						tag("tr") {
							tag(
								"td",
								"balign" to "left",
								"bgcolor" to adjust(currentBlockBackColor)
							) {
								manifest(
									generator!!.currentManifest,
									this@basicBlock,
									generator.currentBlock().predecessorEdges())
							}
						}
					}
				}
				val manifestNodeName = "(current manifest)"
				node(manifestNodeName) {
					style("rounded,dashed")
					label(manifestText)
				}
				// Draw an edge from the current block to its manifest, which is
				// written as a vertex with no border.
				edge(basicBlockName(basicBlock), manifestNodeName) {
					style("dashed")
					arrowhead("dot")
					color(currentBlockBackColor)
				}
			}
		}
		catch (e: IOException)
		{
			throw UncheckedIOException(e)
		}
	}

	private fun StringBuilder.instructionTableRow(
		gridcolor: String,
		instruction: L2Instruction,
		writer: GraphWriter,
		basicBlock: L2BasicBlock)
	{
		tag("tr") {
			val cellAttributes = mutableMapOf(
				"colspan" to "2",
				"align" to "left",
				"balign" to "left",
				"border" to "1",
				"color" to gridcolor,
				"valign" to "top")
			when
			{
				instruction is L2_NOP ->
					cellAttributes["bgcolor"] = writer.adjust("#ffe0ff/#602860")
				instruction.isPlaceholder ->
					cellAttributes["bgcolor"] = writer.adjust("#ffC090/#604800")
				basicBlock.isCold ->
				{
					cellAttributes["bgcolor"] =
						writer.adjust(coldInstructionBackColor)
					cellAttributes["color"] = coldInstructionGridColor
				}
			}
			if (!instruction.altersControlFlow
				|| instruction.targetEdges.size <= 1)
			{
				tagIf(true, "td", cellAttributes) {
					append(writer.instruction(instruction))
				}
			}
			else
			{
				// Add outbound connection ports in a table
				// nested in its own row.  Have the final
				// instruction produce the port labels.
				var portNamesByEdge = instruction.suggestVisualPortNames()
				assert(portNamesByEdge.size
					== portNamesByEdge.values.toSet().size)
				if (portNamesByEdge.isNotEmpty())
				{
					tag(
						"td",
						"colspan" to "2",
						"border" to "0",
						"cellspacing" to "0",
						"cellpadding" to "0"
					) {
						tag(
							"table",
							"border" to "0",
							"cellspacing" to "0"
						) {
							tag("tr") {
								cellAttributes["colspan"] =
									portNamesByEdge.size.toString()
								tagIf(true, "td", cellAttributes) {
									append(writer.instruction(instruction))
								}
							}
							tag("tr") {
								var leftmost = true
								portNamesByEdge.forEach { edge, port ->
									sourcePortNamesByEdge[edge] = port
									tag(
										"td",
										"border" to "1",
										"color" to gridcolor,
										"sides" to
											(if (leftmost) "BLR" else "BR"),
										"port" to port
									) {
										font(
											italic = true,
											size = 10)
										{
											append(escape(port))
										}
									}
									leftmost = false
								}
							}
						}
					}
				}
			}
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
		val targetBlock = edge.targetBlock()
		val isTargetTheUnreachableBlock = targetBlock.instructions()
			.any { it is L2_UNREACHABLE_CODE }
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
					tag("td",
						"balign" to "left",
						// Spacing between the edge line and its label.
						"cellpadding" to "5"
					) {
						font(bold = true) {
							append(escape(basicName))
							namedOperandType?.purpose?.let {
								append(" ($it)")
							}
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
							font(
								italic = true,
								size = 20,
								color = writer.adjust("#400000/ff0000")
							) { append("CLAMPED:") }
							append("<br/>")
							font(bold = true) {
								append(indentString)
								append(escape(edge.forcedClampedEntities))
							}
							append("<br/>")
						}
						if (visualizeLiveness)
						{
							if (!edge.alwaysLiveInEntities.isNullOrEmpty())
							{
								val alwaysEscaped = edge.alwaysLiveInEntities!!
									.sorted()
									.map(::escape)
								val sizeEstimate =
									alwaysEscaped.sumOf { it.length + 2 }
								font(italic = true) {
									append("always live-in:")
								}
								append("<br/>")
								font(bold = true) {
									append(indentString)
									alwaysEscaped.joinTo(
										this,
										if (sizeEstimate > 50)
											",<br/>" + indentString
										else ", ")
								}
								append("<br/>")
							}
							edge.sometimesLiveInEntities?.let { sometimes ->
								val notAlwaysLiveInRegisters =
									sometimes.toMutableSet()
								notAlwaysLiveInRegisters.removeAll(
									edge.alwaysLiveInEntities ?: emptySet())
								if (notAlwaysLiveInRegisters.isNotEmpty())
								{
									val someEscaped = notAlwaysLiveInRegisters
										.sorted()
										.map(::escape)
									val sizeEstimate =
										someEscaped.sumOf { it.length + 2 }
									font(italic = true) {
										append("sometimes live-in:")
									}
									append("<br/>")
									font(bold = true) {
										append(indentString)
										someEscaped.joinTo(
											this,
											if (sizeEstimate > 50)
												",<br/>" + indentString
											else ", ")
									}
									append("<br/>")
								}
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
			writer.edge(
				source = node(
					basicBlockName(sourceBlock),
					sourcePortNamesByEdge[edge],
					null),
				target = when
				{
					edge.isBackward ->
						node(basicBlockName(targetBlock), "1", CompassPoint.N)
					else -> node(basicBlockName(targetBlock))
				})
			{
				// Number each edge uniquely, to allow a multigraph.
				id(edgeCounter.getAndIncrement().toString())
				if (!targetBlock.isCold && !sourceBlock.isCold)
				{
					penwidth(5)
				}
				else
				{
					penwidth(0.4)
				}
				if (!started)
				{
					color("#4040ff/8080ff")
					style("dotted")
				}
				else if (isTargetTheUnreachableBlock)
				{
					color("#804040/c06060")
					style("dotted")
				}
				else if (edge.isBackward)
				{
					constraint(false)
					color(
						if (sourceBlock.zone === null) "#9070ff/6050ff"
						else "#20b040/60ff70")
					style("dashed")
				}
				else
				{
					when (namedOperandType!!.purpose)
					{
						// Nothing. The default styling will be fine.
						null -> Unit
						SUCCESS -> Unit
						Purpose.FAILURE -> color("#e54545/c03030")
						Purpose.OFF_RAMP -> style("dashed")
						Purpose.ON_RAMP ->
						{
							style("dashed")
							color("#6aaf6a")
						}
						Purpose.REFERENCED_AS_INT ->
						{
							style("dashed")
							color("#6080ff")
						}
					}
				}
				label(edgeLabel)
				if (targetBlock.instructions().any { it is L2_PHI<*> })
				{
					// The target includes phi instructions, so label this
					// incoming edge with its index within the target's list of
					// predecessors, which corresponds with the phis' vectors
					// of source values.
					val predecessors = edge.targetBlock().predecessorEdges()
					val targetIndex = predecessors.indexOf(edge) + 1
					headlabel(
						buildString
						{
							font(
								size = 8,
								color = writer.adjust("#400040/ff00ff"))
							{
								append("#$targetIndex/${predecessors.size}")
							}
						})
				}
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
		if (manifest in duplicateManifests)
		{
			font(
				italic = true,
				size = 20,
				color = writer.adjust("#400000/ff0000")
			) { append("DUPLICATE MANIFEST!!!") }
		}
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
					manifest.getAllDefinitions(pick),
					predecessorEdges)
			}
		}
		val postponements = manifest.postponedInstructions()
		if (postponements.isNotEmpty())
		{
			append("<br/>")
			font(italic = true) { append("postponements:") }
			append("<br/>")
		}
		val sortedSubmap = postponements.entries.sortedBy { it.key }
		// Group the postponements by instruction, capturing the list of
		// semantic values that would be written.
		val grouped = sortedSubmap.groupBy(
			keySelector = Map.Entry<*, L2Instruction>::value,
			valueTransform = Map.Entry<L2SemanticValue<*>, *>::key)
		grouped.forEach { (instruction, semanticValues) ->
			if (instruction is L2_MOVE<*>
				&& instruction.source.constantOrNull
					.notNullAnd(nil::equals))
			{
				// Skip propagations of nil, since they're noisy.
				return@forEach
			}
			font(color = writer.adjust(
				if (semanticValues[0].kind == BOXED_KIND) postponementsColor
				else unboxedSynonymColor))
			{
				for (semanticValue in semanticValues)
				{
					append(indentString)
					append(semanticValue.kind.kindName)
					append("/")
					append(escape(semanticValue))
					append("<br/>")
				}
				append(indent2String)
				val badReads = instruction.readOperands
					.filter { it.restriction().isImpossible }
				val badWrites = instruction.writeOperands
					.filter { it.restriction().isImpossible }
				if (badReads.isNotEmpty() || badWrites.isNotEmpty())
				{
					append(escape("PROBLEMS: ${badReads + badWrites}\n"))
					append(escape(increaseIndentation(instruction.toString(), 2)))
				}
				else
				{
					append(escape(increaseIndentation(instruction.toString(), 2)))
				}
				append("<br/>")
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
		val noRegs = definitions.toList().isEmpty()
		val (synonymColor, restrictionColor, definitionsColor) = when
		{
			isError -> Triple(errorTextColor, errorTextColor, errorTextColor)
			newSynonym -> Triple(newEntryColor, newEntryColor, newEntryColor)
			noRegs ->
				Triple(noRegistersColor, noRegistersColor, noRegistersColor)
			else ->
				Triple(
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
			append(indentString)
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
			append(indent2String)
			append(":&nbsp;")
			append(escape(increaseIndentation(restriction.toString(), 2)))
		}
		append("<br/>")
		font(color = writer.adjust(definitionsColor ?: "")) {
			append(indent2String)
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
	 * @receiver
	 *   The [GraphWriter] on which to render the cluster.
	 * @param zone
	 *   The [L2ControlFlowGraph.Zone] to render.
	 * @param isStarted
	 *   A test to tell if a block has started to be generated.
	 * @throws IOException
	 *   If it can't write.
	 */
	@Suppress("SpellCheckingInspection")
	@Throws(IOException::class)
	private fun GraphWriter.cluster(
		zone: L2ControlFlowGraph.Zone,
		isStarted: (L2BasicBlock) -> Boolean)
	{
		subgraph("cluster_" + subgraphNumber++)
		{
			fontcolor("#000000/ffffff")
			labeljust(left)
			label(zone.zoneName)
			zone.zoneType.color?.let { color(it) }
			zone.zoneType.bgcolor?.let { bgcolor(it) }
			defaultAttributeBlock(DefaultAttributeBlockType.GRAPH)
			{
				style("rounded")
				penwidth(5)
			}
			blocksByZone[zone]!!.forEach { block ->
				basicBlock(block, isStarted(block))
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
			darkMode,
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
			writer.graph {
				fontname("Helvetica")
				bgcolor("#ffffff/000000")
				rankdir(TopBottom)
				newrank(true)
				overlap(false)
				splines(true)
				defaultAttributeBlock(DefaultAttributeBlockType.NODE) {
					fontname("Helvetica")
					bgcolor("#ffffff/a0a0a0")
					color("#000000/b0b0b0")
					fixedsize(false)
					fontsize(11)
					fontcolor("#000000/d0d0d0")
					shape("none")
				}
				defaultAttributeBlock(DefaultAttributeBlockType.EDGE) {
					labeldistance(3)
					labelangle(-75)
					fontname("Helvetica")
					fontsize(8)
					fontcolor("#000000/dddddd")
					style("solid")
					color("#000000/e0e0e0")
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
					cluster(zone) { !unstartedBlocks.contains(it) }
				}
				controlFlowGraph.basicBlockOrder
					.filter { it.zone === null }
					.forEach { basicBlock(it, true) }
				unstartedBlocks
					.filter { it.zone === null }
					.forEach { basicBlock(it, false) }
				val edgeCounter = AtomicInteger(1)
				controlFlowGraph.basicBlockOrder
					.deepForEach(L2BasicBlock::predecessorEdges) {
						edge(it, this@graph, true, edgeCounter)
					}
				unstartedBlocks.deepForEach(L2BasicBlock::predecessorEdges) {
					edge(it, this@graph, false, edgeCounter)
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
	 * @receiver
	 *   A [GraphWriter] used to mediate the styling.
	 * @param instruction
	 *   An `L2Instruction`.
	 * @return
	 *   The requested description.
	 */
	private fun GraphWriter.instruction(
		instruction: L2Instruction
	): String = buildString {
		// Hoist a comment operand, if one is present.
		instruction.operands.forEach { operand: L2Operand ->
			if (operand.operandType === COMMENT)
			{
				font(
					italic = true,
					color = adjust(
						operand.isMisconnected,
						errorTextColor,
						commentTextColor))
				{
					append(escape(operand))
				}
				append("<br/>")
			}
		}
		// An L2_NOP emits just the comment (on a suitable background color).
		if (instruction is L2_NOP) return@buildString
		// Make a note of the current length of the builder. We will need to
		// escape everything after this point.
		val escapeIndex = length
		val desiredTypes = allOperandTypes - listOf(PC, PC_VECTOR, COMMENT)
		if (!instruction.producesAnyJvmCode)
		{
			// Show instructions that generate no code in gray.
			font(
				italic = true,
				color = adjust(
					condition = instruction is L2_JUMP
						&& instruction.target.isMisconnected,
					trueString = errorTextColor,
					falseString = "#b0b0b0/808080"))
			{
				val escapableStart = length
				if (visualizeRegisterDescriptions)
				{
					instruction.run {
						appendToWithWarnings(desiredTypes) { }
					}
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
				instruction.run {
					appendToWithWarnings(desiredTypes) {
						assert(it == (styleChanges.size % 2 == 0))
						styleChanges.add(length)
					}
				}
			}
			else
			{
				// Use a simplified instruction output.
				instruction.simpleAppendTo(this)
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
						val color = adjust(errorTextColor)
						escaped.append("<font color=\"$color\"><i>")
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
		private const val currentBlockBackColor = "#f0a0a0/803030"

		/**
		 * A color [String] suitable for [GraphWriter.adjust], specifying what
		 * foreground text color to use for an [L2Generator]'s current block.
		 */
		private const val currentBlockForeColor = "#200000/ffd0d0"

		private const val currentBlockGridColor = "#a08080/a86060"

		private const val commentTextColor = "#404040/a0a0a0"

		private const val unboxedSynonymColor = "#4040c0/a0a0f0"

		private const val postponementsColor = "#803030/ffc0c0"

		private const val newEntryColor = "#209020/b0ffb0"

		private const val noRegistersColor = "#707070/909090"

		private const val changedEntryColor = "#909020/e0e0a0"

		private const val coldInstructionBackColor = "#e0ffff/407070"

		private const val coldInstructionGridColor = "#405050/98b0b0"

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
						|| cp == '>'.code || cp == '&'.code
					-> append("&#$cp;")
					cp == '\n'.code -> append("<br/>")
					cp == '\t'.code -> append(indentString)
					else -> appendCodePoint(cp)
				}
				i += Character.charCount(cp)
			}
		}

		/**
		 * Apply font attributes to the text defined in the [body].
		 *
		 * @receiver
		 *   The [StringBuilder] on which to generate the styled text.
		 * @param face
		 *   The optional font face name.
		 * @param size
		 *   The optional font size.
		 * @param bold
		 *   Whether the text should be bold. Defaults to false.
		 * @param italic
		 *   Whether the text should be italic. Defaults to false.
		 * @param color
		 *   The optional font color.
		 * @param body
		 *   A function that produces the text to style.  Its receiver is the
		 *   [StringBuilder].
		 */
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
			val attributes = mutableMapOf<String, String>()
			if (face !== null || bold || italic)
			{
				var adjustedFace = face ?: "Arial"
				if (bold) adjustedFace += " bold"
				if (italic) adjustedFace += " italic"
				attributes["face"] = adjustedFace
			}
			size?.let { attributes["point-size"] = size.toString() }
			if (color.notNullAnd(String::isNotEmpty))
				attributes["color"] = color!!
			tagIf(
				attributes.isNotEmpty(),
				"font",
				attributes,
				body = body)
		}

		/** Four non-breaking spaces, escaped. */
		private val indentString = repeated("&nbsp;", 4)

		/** Eight non-breaking spaces, escaped. */
		private val indent2String = repeated("&nbsp;", 8)
	}
}
