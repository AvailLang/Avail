/*
 * L2ControlFlowGraphVisualizer.java
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

package com.avail.optimizer;

import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2NamedOperandType;
import com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2ControlFlowGraph.Zone;
import com.avail.utility.MutableInt;
import com.avail.utility.dot.DotWriter;
import com.avail.utility.dot.DotWriter.CompassPoint;
import com.avail.utility.dot.DotWriter.GraphWriter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.avail.interpreter.levelTwo.L2OperandType.COMMENT;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.utility.Strings.repeated;
import static com.avail.utility.dot.DotWriter.DefaultAttributeBlockType.EDGE;
import static com.avail.utility.dot.DotWriter.DefaultAttributeBlockType.GRAPH;
import static com.avail.utility.dot.DotWriter.DefaultAttributeBlockType.NODE;
import static com.avail.utility.dot.DotWriter.node;
import static java.util.Arrays.asList;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;

/**
 * An {@code L2ControlFlowGraphVisualizer} generates a {@code dot} source file
 * that visualizes an {@link L2ControlFlowGraph}. It is intended to aid in
 * debugging {@link L2Chunk}s.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@SuppressWarnings("SpellCheckingInspection")
public class L2ControlFlowGraphVisualizer
{
	/**
	 * The name of the {@code dot} file.
	 */
	private final String fileName;

	/**
	 * The {@linkplain L2Chunk#name() name} of the {@link L2Chunk}, to be used
	 * as the name of the graph.
	 */
	private final String name;

	/**
	 * The number of characters to emit per line. Only applies to formatting
	 * of block comments.
	 */
	private final int charactersPerLine;

	/**
	 * A color {@link String} suitable for {@link GraphWriter#adjust(String)},
	 * specifying what foreground color to use for error text.
	 */
	private static final String errorTextColor = "#e04040/ff6060";
	/**
	 * The {@link L2ControlFlowGraph} that should be visualized by a {@code dot}
	 * renderer.
	 */
	private final L2ControlFlowGraph controlFlowGraph;

	/**
	 * Should edges be annotated with {@link L2Register} liveness?
	 */
	private final boolean visualizeLiveness;

	/**
	 * Should edges be annotated with their {@link L2ValueManifest} manifest?
	 */
	private final boolean visualizeManifest;

	/**
	 * The {@linkplain Appendable accumulator} for the generated {@code dot}
	 * source text.
	 */
	private final Appendable accumulator;

	/**
	 * Construct a new {@code L2ControlFlowGraphVisualizer} for the specified
	 * {@link L2ControlFlowGraph}.
	 *
	 * @param fileName
	 *        The name of the {@code dot} file.
	 * @param name
	 *        The {@linkplain L2Chunk#name() name} of the {@link L2Chunk}, to be
	 *        used as the name of the graph.
	 * @param charactersPerLine
	 *        The number of characters to emit per line. Only applies to
	 *        formatting of block comments.
	 * @param controlFlowGraph
	 *        The {@code L2ControlFlowGraph}.
	 * @param visualizeLiveness
	 *        {@code true} if edges should be annotated with {@link L2Register}
	 *        liveness, {@code false} otherwise.
	 * @param visualizeManifest
	 *        {@code true} if edges should be annotated with their {@link
	 *        L2ValueManifest}, {@code false otherwise}.
	 * @param accumulator
	 *        The {@linkplain Appendable accumulator} for the generated {@code
	 *        dot} source text.
	 */
	public L2ControlFlowGraphVisualizer (
		final String fileName,
		final String name,
		final int charactersPerLine,
		final L2ControlFlowGraph controlFlowGraph,
		final boolean visualizeLiveness,
		final boolean visualizeManifest,
		final Appendable accumulator)
	{
		this.fileName = fileName;
		this.name = name;
		this.charactersPerLine = charactersPerLine;
		this.controlFlowGraph = controlFlowGraph;
		this.visualizeLiveness = visualizeLiveness;
		this.visualizeManifest = visualizeManifest;
		this.accumulator = accumulator;
	}

	/**
	 * Emit a banner.
	 *
	 * @param writer
	 *        The {@link DotWriter}.
	 * @throws IOException
	 *         If emission fails.
	 */
	private void banner (final DotWriter writer) throws IOException
	{
		writer.blockComment(String.format(
			"\n"
			+ "%s.dot\n"
			+ "Copyright © %s, %s.\n"
			+ "All rights reserved.\n"
			+ "\n"
			+ "Generated by %s - do not modify!\n"
			+ "\n",
			fileName,
			writer.getCopyrightOwner(),
			LocalDateTime.ofInstant(
				Instant.now(),
				ZoneId.systemDefault()).getYear(),
			L2ControlFlowGraphVisualizer.class.getSimpleName()));
	}

	/**
	 * A generator of unique identifiers, for construction of
	 * {@link L2BasicBlock} names when the {@link L2ControlFlowGraph} is
	 * incomplete or inconsistent.
	 */
	private int blockId = 0;

	/**
	 * The node name of the {@link L2BasicBlock}s, as a {@linkplain Map map}
	 * from {@code L2BasicBlock}s to their names.
	 */
	private final Map<L2BasicBlock, String> basicBlockNames = new HashMap<>();

	/** Characters that should be removed outright from class names. */
	private static final Pattern matchUglies = Pattern.compile("[\"\\\\]");

	/**
	 * Compute a unique name for the specified {@link L2BasicBlock}.
	 *
	 * @param basicBlock
	 *        The {@code L2BasicBlock}.
	 * @return A unique name that includes the {@code L2BasicBlock}'s
	 *         {@linkplain L2BasicBlock#offset() program counter} and its
	 *         non-unique semantic {@linkplain L2BasicBlock#name() name}.
	 */
	private String basicBlockName (final L2BasicBlock basicBlock)
	{
		return basicBlockNames.computeIfAbsent(
			basicBlock,
			b ->
			{
				final int offset = b.offset();
				final int id = offset == -1 ? ++blockId : offset;
				final String prefix =
					String.format(offset == -1 ? "[id: %d]" : "[pc: %d]", id);
				return String.format(
					"%s %s",
					prefix,
					matchUglies.matcher(b.name()).replaceAll(""));
			});
	}

	/**
	 * Escape the specified text for inclusion into an HTML-like identifier.
	 *
	 * @param s
	 *        Some arbitrary text.
	 * @return The escaped text.
	 */
	private static String escape (final String s)
	{
		final int limit = s.length();
		final StringBuilder builder = new StringBuilder(limit);
		for (int i = 0; i < limit;)
		{
			final int cp = s.codePointAt(i);
			if (cp > 127
				|| cp == '"'
				|| cp == '<'
				|| cp == '>'
				|| cp == '&')
			{
				builder.append("&#");
				builder.append(cp);
				builder.append(';');
			}
			else if (cp == '\n')
			{
				builder.append("<br/>");
			}
			else if (cp == '\t')
			{
				builder.append(repeated("&nbsp;", 4));
			}
			else
			{
				builder.appendCodePoint(cp);
			}
			i += Character.charCount(cp);
		}
		return builder.toString();
	}

	/**
	 * Compute a reasonable description of the specified {@link L2Instruction}.
	 * Any {@link L2PcOperand}s will be ignored in the rendition of the
	 * {@code L2Instruction}, as they will be described along the edges instead
	 * of within the nodes.
	 *
	 * @param instruction
	 *        An {@code L2Instruction}.
	 * @param writer
	 *        A {@link GraphWriter} used to mediate the styling.
	 * @return The requested description.
	 */
	private static String instruction (
		final L2Instruction instruction,
		final GraphWriter writer)
	{
		final StringBuilder builder = new StringBuilder();
		// Hoist a comment operand, if one is present.
		instruction.operandsDo(operand ->
		{
			if (operand.operandType() == COMMENT)
			{
				// The selection of Helvetica as the font is important. Some
				// renderers, like Viz.js, only seem to fully support a small
				// number of standard, widely available fonts:
				//
				// https://github.com/mdaines/viz.js/issues/82
				//
				// In particular, Courier, Arial, Helvetica, and Times are
				// supported.
				builder.append(
					String.format(
						"<font face=\"Helvetica\" color=\"%s\"><i>",
						writer.adjust(
							operand.isMisconnected(),
							errorTextColor,
							"#404040/a0a0a0")));
				builder.append(escape(operand.toString()));
				builder.append("</i></font><br/>");
			}
			return null;
		});
		// Make a note of the current length of the builder. We will need to
		// escape everything after this point.
		final int escapeIndex = builder.length();
		final Set<L2OperandType> desiredTypes =
			EnumSet.complementOf(EnumSet.of(PC, COMMENT));
		if (instruction.operation() == L2_JUMP.INSTANCE
			&& instruction.offset() != -1
			&& L2_JUMP.jumpTarget(instruction).offset()
				== instruction.offset())
		{
			// Show fall-through jumps in grey.
			final L2PcOperand edge = L2_JUMP.jumpTarget(instruction);
			builder
				.append("<font color=\"")
				.append(
					writer.adjust(
						edge.isMisconnected(),
						errorTextColor,
						"#404040/808080"))
				.append("\"><i>");
			final int escapableStart = builder.length();
			instruction.operation().appendToWithWarnings(
				instruction, desiredTypes, builder, b -> null);
			builder.replace(
				escapableStart,
				builder.length(),
				escape(builder.substring(escapableStart)));
			builder.append("</i></font><br/>");
			return builder.toString();
		}
		final Deque<Integer> styleChanges = new ArrayDeque<>();
		instruction.appendToWithWarnings(
			builder,
			desiredTypes,
			flag ->
			{
				assert flag == (styleChanges.size() % 2 == 0);
				styleChanges.add(builder.length());
				return null;
			});

		// Escape everything since the saved position.  Add a final sentinel to
		// avoid duplicating code below.
		styleChanges.add(builder.length());
		final StringBuilder escaped = new StringBuilder();
		boolean warningFlag = false;
		int regionStart = escapeIndex;
		while (!styleChanges.isEmpty())
		{
			final int here = styleChanges.remove();
			escaped.append(escape(builder.substring(regionStart, here)));
			if (!styleChanges.isEmpty())
			{
				warningFlag = !warningFlag;
				if (warningFlag)
				{
					escaped
						.append("<font color=\"")
						.append(writer.adjust(errorTextColor))
						.append("\"><i>");
				}
				else
				{
					escaped.append("</i></font>");
				}
			}
			regionStart = here;
		}
		assert regionStart == builder.length();
		assert !warningFlag;
		builder.replace(escapeIndex, builder.length(), escaped.toString());
		return builder.toString();
	}

	/**
	 * Emit the specified {@link L2BasicBlock}.
	 *
	 * @param basicBlock
	 *        A {@code L2BasicBlock}.
	 * @param writer
	 *        The {@link GraphWriter} for emission.
	 * @param started
	 *        {@code true} if the basic block is starting, {@code false}
	 *        otherwise.
	 */
	private void basicBlock (
		final L2BasicBlock basicBlock,
		final GraphWriter writer,
		final boolean started)
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(
			"<table border=\"0\" cellspacing=\"0\">");
		final List<L2Instruction> instructions = basicBlock.instructions();
		final @Nullable L2Instruction first =
			!instructions.isEmpty() ? basicBlock.instructions().get(0) : null;
		final String fillcolor;
		final String fontcolor;
		if (!started)
		{
			fillcolor = "#202080/303000";
			fontcolor = "#ffffff/e0e0e0";
		}
		else if (basicBlock.instructions().stream().anyMatch(
			i -> i.operation() == L2_UNREACHABLE_CODE.INSTANCE))
		{
			fillcolor = "#400000/600000";
			fontcolor = "#ffffff/ffffff";
		}
		else if (basicBlock.isLoopHead)
		{
			fillcolor = "#9070ff/302090";
			fontcolor = "#000000/f0f0f0";
		}
		else if (first != null && first.isEntryPoint())
		{
			fillcolor = "#ffd394/604000";
			fontcolor = "#000000/e0e0e0";
		}
		else
		{
			fillcolor = "#c1f0f6/104048";
			fontcolor = "#000000/e0e0e0";
		}
		// The selection of Helvetica as the font is important. Some
		// renderers, like Viz.js, only seem to fully support a small number
		// of standard, widely available fonts:
		//
		// https://github.com/mdaines/viz.js/issues/82
		//
		// In particular, Courier, Arial, Helvetica, and Times are supported.
		builder.append(String.format(
			"<tr>"
				+ "<td align=\"left\" balign=\"left\" border=\"1\" "
				+ "bgcolor=\"%s\">"
					+ "<font face=\"Courier\" color=\"%s\">%s</font>"
				+ "</td>"
			+ "</tr>",
			writer.adjust(fillcolor),
			writer.adjust(fontcolor),
			escape(basicBlock.name())));
		if (!instructions.isEmpty())
		{
			int portId = 0;
			for (final L2Instruction instruction : basicBlock.instructions())
			{
				builder.append(String.format(
					"<tr><td align=\"left\" balign=\"left\" border=\"1\" "
						+ "port=\"%d\" valign=\"top\"%s>",
					++portId,
					instruction.operation().isPlaceholder()
						? " bgcolor=\"" + writer.adjust("#ff9090/#500000")
							+ "\""
						: ""));
				builder.append(instruction(instruction, writer));
				builder.append("</td></tr>");
			}
		}
		else
		{
			builder.append(
				"<tr><td align=\"left\" balign=\"left\" border=\"1\" "
					+ " valign=\"top\">"
						+ "No instructions generated."
					+ "</td></tr>");
		}
		builder.append("</table>");
		try
		{
			writer.node(
				basicBlockName(basicBlock),
				attr -> attr.attribute("label", builder.toString()));
		}
		catch (final IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Emit a control flow edge, which is an {@link L2PcOperand}.
	 *
	 * @param edge
	 *        The {@link L2PcOperand} to visit.
	 * @param writer
	 *        The {@link GraphWriter} for emission.
	 * @param started
	 *        Whether code generation has started for the targetBlock.
	 * @param edgeCounter
	 *        A {@link MutableInt}, suitable for uniquely numbering edges.
	 */
	private void edge (
		final L2PcOperand edge,
		final GraphWriter writer,
		final boolean started,
		final MutableInt edgeCounter)
	{
		final L2BasicBlock sourceBlock = edge.sourceBlock();
		final L2Instruction sourceInstruction = edge.instruction();
		final L2BasicBlock targetBlock = edge.targetBlock();
		final boolean isTargetTheUnreachableBlock =
			targetBlock.instructions().stream()
				.anyMatch(
					instr -> instr.operation() == L2_UNREACHABLE_CODE.INSTANCE);
		final L2NamedOperandType[] types =
			sourceInstruction.operation().operandTypes();
		final L2Operand[] operands = sourceInstruction.operands();
		final L2NamedOperandType type = types[asList(operands).indexOf(edge)];
		// The selection of Helvetica as the font is important. Some
		// renderers, like Viz.js, only seem to fully support a small number
		// of standard, widely available fonts:
		//
		// https://github.com/mdaines/viz.js/issues/82
		//
		// In particular, Courier, Arial, Helvetica, and Times are
		// supported.
		final StringBuilder builder = new StringBuilder();
		builder.append(
			"<table border=\"0\" cellspacing=\"0\">"
				+ "<tr><td balign=\"left\">"
					+ "<font face=\"Helvetica\"><b>");
		builder.append(type.name());
		builder.append("</b></font><br/>");
		if (visualizeLiveness || visualizeManifest)
		{
			// Show any clamped entities for this edge.  These are registers and
			// semantic values that are declared always live along this edge,
			// and act as the (cycle breaking) end-roots for dead code analysis.
			if (edge.forcedClampedEntities != null)
			{
				builder
					.append("<font face=\"Helvetica\"><i>CLAMPED:</i></font>")
					.append("<br/><b>")
					.append(repeated("&nbsp;", 4))
					.append(edge.forcedClampedEntities)
					.append("</b><br/>");
			}
		}
		if (visualizeLiveness)
		{
			if (!edge.alwaysLiveInRegisters.isEmpty())
			{
				builder
					.append("<font face=\"Helvetica\">")
					.append("<i>always live-in:</i></font><br/><b>")
					.append(repeated("&nbsp;", 4));
				edge.alwaysLiveInRegisters.stream()
					.sorted(comparingInt(L2Register::finalIndex))
					.forEach(
						r -> builder
							.append(escape(r.toString()))
							.append(", "));
				builder.setLength(builder.length() - 2);
				builder.append("</b><br/>");
			}
			final Set<L2Register> notAlwaysLiveInRegisters =
				new HashSet<>(edge.sometimesLiveInRegisters);
			notAlwaysLiveInRegisters.removeAll(edge.alwaysLiveInRegisters);
			if (!notAlwaysLiveInRegisters.isEmpty())
			{
				builder
					.append("<font face=\"Helvetica\">")
					.append("<i>sometimes live-in:</i></font><br/><b>")
					.append(repeated("&nbsp;", 4));
				notAlwaysLiveInRegisters.stream()
					.sorted(comparingInt(L2Register::finalIndex))
					.forEach(
						r -> builder
							.append(escape(r.toString()))
							.append(", "));
				builder.setLength(builder.length() - 2);
				builder.append("</b><br/>");
			}
		}
		if (visualizeManifest)
		{
			final L2ValueManifest manifest = edge.manifest();
			final L2Synonym[] synonyms = manifest.synonymsArray();
			if (synonyms.length > 0)
			{
				builder.append(
					"<font face=\"Helvetica\"><i>manifest:</i></font>");
				Arrays.sort(synonyms, comparing(L2Synonym::toString));
				for (final L2Synonym synonym : synonyms)
				{
					// If the restriction flags and the available register
					// kinds disagree, show the synonym entry in red.
					final TypeRestriction restriction =
						manifest.restrictionFor(
							synonym.pickSemanticValue());
					final Iterable<L2Register> defs =
						manifest.definitionsForDescribing(
							synonym.pickSemanticValue());
					final EnumSet<RegisterKind> kindsOfRegisters =
						EnumSet.noneOf(RegisterKind.class);
					for (final L2Register register : defs)
					{
						kindsOfRegisters.add(register.registerKind());
					}
					final boolean ok =
						restriction.kinds().equals(kindsOfRegisters);
					if (!ok) {
						builder.append(
							String.format(
								"<font color=\"%s\">",
								writer.adjust(errorTextColor)));
					}
					builder
						.append("<br/>")
						.append(repeated("&nbsp;", 4))
						.append(escape(synonym.toString()))
						.append("<br/>")
						.append(repeated("&nbsp;", 8))
						.append(":&nbsp;");
					builder
						.append(escape(restriction.toString()))
						.append("<br/>")
						.append(repeated("&nbsp;", 8))
						.append("in {");
					final Iterator<L2Register> iterator = defs.iterator();
					if (iterator.hasNext())
					{
						builder.append(iterator.next());
					}
					iterator.forEachRemaining(
						d -> builder.append(", ").append(iterator.next()));
					builder.append("}");
					if (!ok) {
						builder.append("</font>");
					}
				}
			}
		}
		builder.append("</td></tr></table>");
		try
		{
			final int sourceSubscript =
				sourceBlock.instructions().indexOf(sourceInstruction) + 1;
			writer.edge(
				edge.isBackward()
					? node(
						basicBlockName(sourceBlock),
						Integer.toString(sourceSubscript),
						CompassPoint.E)
					: node(
						basicBlockName(sourceBlock),
						Integer.toString(sourceSubscript)),
				edge.isBackward()
					? node(basicBlockName(targetBlock), "1")
					: node(basicBlockName(targetBlock)),
				attr ->
				{
					// Number each edge uniquely, to allow a multigraph.
					attr.attribute(
						"id", Integer.toString(edgeCounter.value++));
					if (!started)
					{
						attr.attribute("color", "#4040ff/8080ff");
						attr.attribute("style", "dotted");
					}
					else if (isTargetTheUnreachableBlock)
					{
						attr.attribute("color", "#804040/c06060");
						attr.attribute("style", "dotted");
					}
					else if (edge.isBackward())
					{
						attr.attribute("constraint", "false");
						attr.attribute(
							"color",
							sourceBlock.zone == null
								? "#9070ff/6050ff"
								: "#90f0a0/60ff70");
						attr.attribute("style", "dashed");
					}
					else
					{
						final @Nullable Purpose purpose = type.purpose();
						assert purpose != null;
						switch (purpose)
						{
							case SUCCESS:
								// Nothing. The default styling will be fine.
								break;
							case FAILURE:
								attr.attribute("color", "#e54545/c03030");
								break;
							case OFF_RAMP:
								attr.attribute("style", "dashed");
								break;
							case ON_RAMP:
								attr.attribute("style", "dashed");
								attr.attribute("color", "#6aaf6a");
								break;
							case REFERENCED_AS_INT:
								attr.attribute("style", "dashed");
								attr.attribute("color", "#6080ff");
								break;
						}
					}
					attr.attribute("label", builder.toString());
				});
		}
		catch (final IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	/** The subgraphs ({@link Zone}s) that have been discovered so far. */
	Map<Zone, Set<L2BasicBlock>> blocksByZone = new HashMap<>();

	/**
	 * Calculate how the basic blocks form clusters for reification sections.
	 *
	 * @param blocks A collection of {@link L2BasicBlock}s to classify.
	 */
	private void computeClusters (final Iterable<L2BasicBlock> blocks)
	{
		for (final L2BasicBlock block : blocks)
		{
			if (block.zone != null)
			{
				blocksByZone.computeIfAbsent(block.zone, z -> new HashSet<>())
					.add(block);
			}
		}
	}

	/** A counter for uniquely naming subgraphs. */
	private int subgraphNumber = 1;

	/**
	 * Render the nodes in this zone as a subgraph (cluster).
	 *
	 * @param zone
	 *        The {@link Zone} to render.
	 * @param graph
	 *        The {@link GraphWriter} to render them.
	 * @param isStarted
	 *        A test to tell if a block has started to be generated.
	 * @throws IOException If it can't write.
	 */
	private void cluster(
		final Zone zone,
		final GraphWriter graph,
		final Predicate<L2BasicBlock> isStarted)
	throws IOException
	{
		graph.subgraph(
			"cluster_" + subgraphNumber++,
			gw ->
			{
				if (zone.zoneName != null)
				{
					gw.attribute("fontcolor", "#000000/ffffff");
					gw.attribute("labeljust", "l");  // Left-aligned.
					gw.attribute("label", zone.zoneName);
				}
				gw.attribute("color", zone.zoneType.color);
				gw.attribute("bgcolor", zone.zoneType.bgcolor);
				gw.defaultAttributeBlock(GRAPH, attr ->
				{
					attr.attribute("style", "rounded");
					attr.attribute("penwidth", "5");
				});
				blocksByZone.get(zone).forEach(
					block -> basicBlock(
						block, gw, isStarted.test(block)));
			});
	}

	/**
	 * Visualize the {@link L2ControlFlowGraph} by {@linkplain DotWriter
	 * writing} an appropriate {@code dot} source file to the {@linkplain
	 * #accumulator}.
	 */
	public void visualize ()
	{
		final DotWriter writer = new DotWriter(
			name,
			true,
			charactersPerLine,
			accumulator,
			true,
			"The Avail Foundation");
		try
		{
			banner(writer);
			// The selection of Courier as the font is important. Some
			// renderers, like Viz.js, only seem to fully support a small number
			// of standard, widely available fonts:
			//
			// https://github.com/mdaines/viz.js/issues/82
			//
			// In particular, Courier, Arial, Helvetica, and Times are
			// supported.
			writer.graph(graph ->
			{
				graph.attribute("bgcolor", "#00ffff/000000");
				graph.attribute("rankdir", "TB");
				graph.attribute("newrank", "true");
				graph.attribute("overlap", "false");
				graph.attribute("splines", "true");
				graph.defaultAttributeBlock(NODE, attr ->
				{
					attr.attribute("bgcolor", "#ffffff/a0a0a0");
					attr.attribute("color", "#000000/b0b0b0");
					attr.attribute("fixedsize", "false");
					attr.attribute("fontname", "Helvetica");
					attr.attribute("fontsize", "11");
					attr.attribute("fontcolor", "#000000/d0d0d0");
					attr.attribute("shape", "none");
				});
				graph.defaultAttributeBlock(EDGE, attr ->
				{
					attr.attribute("fontname", "Helvetica");
					attr.attribute("fontsize", "8");
					attr.attribute("fontcolor", "#000000/dddddd");
					attr.attribute("style", "solid");
					attr.attribute("color", "#000000/e0e0e0");
				});
				final Set<L2BasicBlock> startedBlocks = new HashSet<>(
					controlFlowGraph.basicBlockOrder);
				final Set<L2BasicBlock> unstartedBlocks = new HashSet<>();
				for (final L2BasicBlock startedBlock : startedBlocks)
				{
					startedBlock.successorEdgesDo(edge ->
					{
						final L2BasicBlock target = edge.targetBlock();
						if (!startedBlocks.contains(target))
						{
							unstartedBlocks.add(target);
						}
					});
				}

				computeClusters(startedBlocks);
				computeClusters(unstartedBlocks);
				for (final Zone zone : blocksByZone.keySet())
				{
					cluster(zone, graph, b -> !unstartedBlocks.contains(b));
				}

				controlFlowGraph.basicBlockOrder.stream()
					.filter(block -> block.zone == null)
					.forEach(block -> basicBlock(block, graph, true));
				unstartedBlocks.stream()
					.filter(block -> block.zone == null)
					.forEach(block -> basicBlock(block, graph, false));

				final MutableInt edgeCounter = new MutableInt(1);
				controlFlowGraph.basicBlockOrder.forEach(
					block -> block.predecessorEdgesDo(
						edge -> edge(edge, graph, true, edgeCounter)));
				unstartedBlocks.forEach(
					block -> block.predecessorEdgesDo(
						edge -> edge(edge, graph, false, edgeCounter)));
			});
		}
		catch (final IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}
}
