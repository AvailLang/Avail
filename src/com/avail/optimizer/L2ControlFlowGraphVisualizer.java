/*
 * L2ControlFlowGraphVisualizer.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.utility.dot.DotWriter;
import com.avail.utility.dot.DotWriter.GraphWriter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.COMMENT;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.utility.dot.DotWriter.DefaultAttributeBlockType.EDGE;
import static com.avail.utility.dot.DotWriter.DefaultAttributeBlockType.NODE;
import static com.avail.utility.dot.DotWriter.node;
import static java.util.Comparator.comparing;

/**
 * An {@code L2ControlFlowGraphVisualizer} generates a {@code dot} source file
 * that visualizes an {@link L2ControlFlowGraph}. It is intended to aid in
 * debugging {@link L2Chunk}s.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
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
			System.getProperty("user.name"),
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
				final int offset = basicBlock.offset();
				final int id = offset == -1 ? ++blockId : offset;
				final String prefix =
					String.format(offset == -1 ? "[id: %d]" : "[pc: %d]", id);
				return String.format(
					"%s %s",
					prefix,
					basicBlock.name());
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
				builder.append("&nbsp;&nbsp;&nbsp;&nbsp;");
			}
			else
			{
				builder.appendCodePoint(cp);
			}
			//noinspection AssignmentToForLoopParameter
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
	 * @return The requested description.
	 */
	private static String instruction (final L2Instruction instruction)
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
					"<font face=\"Helvetica\" color=\"#404040\"><i>");
				builder.append(escape(operand.toString()));
				builder.append("</i></font><br/>");
			}
		});
		// Make a note of the current length of the builder. We will need to
		// escape everything after this point.
		final int escapeIndex = builder.length();
		final Set<L2OperandType> desiredTypes =
			EnumSet.complementOf(EnumSet.of(PC, COMMENT));
		instruction.operation().toString(instruction, desiredTypes, builder);
		// Escape everything since the saved position.
		return builder.replace(
			escapeIndex,
			builder.length(),
			escape(builder.substring(escapeIndex))).toString();
	}

	/**
	 * Emit the specified {@link L2BasicBlock}.
	 *
	 * @param basicBlock
	 *        A {@code L2BasicBlock}.
	 * @param writer
	 *        The {@link GraphWriter} for emission.
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
		final String bgcolor;
		final String fontcolor;
		if (!started)
		{
			bgcolor = "#202080";
			fontcolor = "#ffffff";
		}
		else if (basicBlock.instructions().stream().anyMatch(
			i -> i.operation() == L2_UNREACHABLE_CODE.instance))
		{
			bgcolor = "#400000";
			fontcolor = "#ffffff";
		}
		else if (first != null && first.operation().isEntryPoint(first))
		{
			bgcolor = "#ffd394";
			fontcolor = "#000000";
		}
		else
		{
			bgcolor = "#c1f0f6";
			fontcolor = "#000000";
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
					+ "<font face=\"Helvetica\" color=\"%s\">%s</font>"
				+ "</td>"
			+ "</tr>",
			bgcolor,
			fontcolor,
			escape(basicBlock.name())));
		if (!instructions.isEmpty())
		{
			int instructionId = 0;
			for (final L2Instruction instruction : basicBlock.instructions())
			{
				final int offset = instruction.offset();
				final int id = offset == -1 ? ++instructionId : offset;
				builder.append(String.format(
					"<tr><td align=\"left\" balign=\"left\" border=\"1\" "
						+ "port=\"%d\" valign=\"top\">",
					id));
				builder.append(instruction(instruction));
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
	 * Emit all incoming control flow edges for the specified {@link
	 * L2BasicBlock}.
	 *
	 * @param targetBlock
	 *        An {@code L2BasicBlock}.
	 * @param writer
	 *        The {@link GraphWriter} for emission.
	 * @param started
	 *        Whether code generation has started for the targetBlock.
	 */
	private void edges (
		final L2BasicBlock targetBlock,
		final GraphWriter writer,
		final boolean started)
	{
		final boolean isTargetTheUnreachableBlock =
			targetBlock.instructions().stream()
				.anyMatch(
					instr -> instr.operation() == L2_UNREACHABLE_CODE.instance);
		final Iterator<L2PcOperand> iterator =
			targetBlock.predecessorEdgesIterator();
		while (iterator.hasNext())
		{
			final L2PcOperand edge = iterator.next();
			final L2BasicBlock sourceBlock = edge.sourceBlock();
			final L2Instruction last = sourceBlock.finalInstruction();
			final L2NamedOperandType[] types = last.operation().operandTypes();
			final L2Operand[] operands = last.operands();
			int i;
			for (i = 0; i < operands.length; i++)
			{
				// Find the L2PcOperand corresponding to this edge.
				final L2Operand operand = last.operand(i);
				if (operand.operandType() == PC
					&& ((L2PcOperand) operand).targetBlock() == targetBlock)
				{
					break;
				}
			}
			assert i < operands.length : "didn't find the control edge";
			final L2NamedOperandType type = types[i];
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
			if (visualizeLiveness)
			{
				if (!edge.alwaysLiveInRegisters.isEmpty())
				{
					builder.append(
						"<font face=\"Helvetica\"><i>always live-in:</i></font>"
							+ "<br/><b>&nbsp;&nbsp;&nbsp;&nbsp;");
					edge.alwaysLiveInRegisters.stream()
						.sorted(Comparator.comparingInt(L2Register::finalIndex))
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
					builder.append(
						"<font face=\"Helvetica\"><i>sometimes live-in:</i></font>"
							+ "<br/><b>&nbsp;&nbsp;&nbsp;&nbsp;");
					notAlwaysLiveInRegisters.stream()
						.sorted(Comparator.comparingInt(L2Register::finalIndex))
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
				final List<L2Synonym> synonyms =
					new ArrayList<>(manifest.synonyms());
				if (!synonyms.isEmpty())
				{
					builder.append(
						"<font face=\"Helvetica\"><i>manifest:</i></font>");
					synonyms.sort(comparing(L2Synonym::toString));
					synonyms.forEach(
						synonym ->
						{
							builder.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;");
							builder.append(escape(synonym.toString()));
							if (synonym.semanticValues().stream().noneMatch(
								L2SemanticValue::isConstant))
							{
								builder.append(
									"<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
									+ "&nbsp;&nbsp;"
									+ ":&nbsp;");
								final TypeRestriction restriction =
									manifest.restrictionFor(
										synonym.pickSemanticValue());
								builder.append(escape(restriction.toString()));
							}
						});
				}
			}
			builder.append("</td></tr></table>");
			try
			{
				writer.edge(
					node(
						basicBlockName(sourceBlock),
						Integer.toString(
							sourceBlock.finalInstruction().offset() != -1
								? sourceBlock.finalInstruction().offset()
								: sourceBlock.instructions().size())),
					targetBlock.offset() != -1
						? sourceBlock.offset() <= targetBlock.offset()
							? node(basicBlockName(targetBlock))
							: node(
								basicBlockName(targetBlock),
								Integer.toString(targetBlock.offset()))
						: node(basicBlockName(targetBlock)),
					attr ->
					{
						if (!started)
						{
							attr.attribute("color", "#4040ff");
							attr.attribute("style", "dotted");
						}
						else if (isTargetTheUnreachableBlock)
						{
							attr.attribute("color", "#804040");
							attr.attribute("style", "dotted");
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
									attr.attribute("color", "#e54545");
									break;
								case OFF_RAMP:
									attr.attribute("style", "dashed");
									break;
								case ON_RAMP:
									attr.attribute("style", "dashed");
									attr.attribute("color", "#6aaf6a");
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
	}

	/**
	 * Visualize the {@link L2ControlFlowGraph} by {@linkplain DotWriter
	 * writing} an appropriate {@code dot} source file to the {@linkplain
	 * #accumulator}.
	 */
	public void visualize ()
	{
		final DotWriter writer = new DotWriter(
			name, true, charactersPerLine, accumulator);
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
				graph.attribute("rankdir", "TB");
				graph.attribute("newrank", "true");
				graph.attribute("overlap", "false");
				graph.attribute("splines", "true");
				graph.defaultAttributeBlock(NODE, attr ->
				{
					attr.attribute("fixedsize", "false");
					attr.attribute("fontname", "Courier");
					attr.attribute("fontsize", "8");
					attr.attribute("fontcolor", "#000000");
					attr.attribute("shape", "none");
				});
				graph.defaultAttributeBlock(EDGE, attr ->
				{
					attr.attribute("fontname", "Courier");
					attr.attribute("fontsize", "6");
					attr.attribute("fontcolor", "#000000");
					attr.attribute("style", "solid");
					attr.attribute("color", "#000000");
				});
				final Set<L2BasicBlock> startedBlocks = new HashSet<>(
					controlFlowGraph.basicBlockOrder);
				final Set<L2BasicBlock> unstartedBlocks = new HashSet<>();
				startedBlocks.forEach(
					startedBlock -> startedBlock.successorEdgesCopy().stream()
						.map(L2PcOperand::targetBlock)
						.filter(b -> !startedBlocks.contains(b))
						.forEach(unstartedBlocks::add));

				controlFlowGraph.basicBlockOrder.forEach(
					block -> basicBlock(block, graph, true));
				unstartedBlocks.forEach(
					block -> basicBlock(block, graph, false));
				controlFlowGraph.basicBlockOrder.forEach(
					block -> edges(block, graph, true));
				unstartedBlocks.forEach(block -> edges(block, graph, false));
			});
		}
		catch (final IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}
}
