/*
 * GraphTracer.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.builder;

import com.avail.builder.AvailBuilder.ModuleTree;
import com.avail.io.SimpleCompletionHandler;
import com.avail.utility.Graph;
import com.avail.utility.MutableInt;
import com.avail.utility.Strings;
import com.avail.utility.evaluation.Continuation1NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Collections.singleton;

/**
 * Used for graphics generation.
 */
final class GraphTracer
{
	/** The {@link AvailBuilder} for which to generate a graph. */
	private final AvailBuilder availBuilder;
	/**
	 * The module whose ancestors are to be graphed.
	 */
	private final ResolvedModuleName targetModule;

	/**
	 * The output file into which the graph should be written in .gv "dot"
	 * format.
	 */
	private final File outputFile;

	/**
	 * Construct a new {@code GraphTracer}.
	 *
	 * @param availBuilder
	 *        The {@link AvailBuilder} for which to generate a graph.
	 * @param targetModule
	 *        The module whose ancestors are to be graphed.
	 * @param outputFile
	 *        The {@linkplain File file} into which to write the graph.
	 */
	GraphTracer (
		final AvailBuilder availBuilder,
		final ResolvedModuleName targetModule,
		final File outputFile)
	{
		this.availBuilder = availBuilder;
		this.targetModule = targetModule;
		this.outputFile = outputFile;
	}

	/**
	 * Scan all module files in all visible source directories, writing the
	 * graph as a Graphviz .gv file in <strong>dot</strong> format.
	 */
	public void traceGraph ()
	{
		if (!availBuilder.shouldStopBuild())
		{
			final Graph<ResolvedModuleName> ancestry =
				availBuilder.moduleGraph.ancestryOfAll(singleton(targetModule));
			final Graph<ResolvedModuleName> dag = ancestry.spanningDag();
			final Graph<ResolvedModuleName> reduced =
				ancestry.withoutRedundantEdges(dag);
			renderGraph(reduced, dag);
		}
		availBuilder.trimGraphToLoadedModules();
	}

	/**
	 * All full names that have been encountered for nodes so far, with
	 * their more readable abbreviations.
	 */
	final Map<String, String> encounteredNames = new HashMap<>();

	/**
	 * All abbreviated names that have been allocated so far as values of
	 * {@link #encounteredNames}.
	 */
	final Set<String> allocatedNames = new HashSet<>();

	/**
	 * Convert a fully qualified module name into a suitably tidy symbolic
	 * name for a node.  This uses both {@link #encounteredNames} and {@link
	 * #allocatedNames} to bypass conflicts.
	 *
	 * <p>Node names are rather restrictive in Graphviz, so non-alphanumeric
	 * characters are converted to "_xxxxxx_", where the x's are a
	 * non-padded hex representation of the character's Unicode code point.
	 * Slashes are converted to "__".</p>
	 *
	 * @param input The fully qualified module name.
	 * @return A unique node name.
	 */
	private String asNodeName (final String input)
	{
		if (encounteredNames.containsKey(input))
		{
			return encounteredNames.get(input);
		}
		assert input.charAt(0) == '/';
		// Try naming it locally first.
		int startPosition = input.length() + 1;
		final StringBuilder output = new StringBuilder(startPosition + 10);
		while (startPosition > 1)
		{
			// Include successively more context until it works.
			output.setLength(0);
			startPosition = input.lastIndexOf('/', startPosition - 2) + 1;
			int c;
			for (
				int i = startPosition;
				i < input.length();
				i += Character.charCount(c))
			{
				c = input.codePointAt(i);
				if (('a' <= c && c <= 'z')
					|| ('A' <= c && c <= 'Z')
					|| (i > startPosition && '0' <= c && c <= '9'))
				{
					output.appendCodePoint(c);
				}
				else if (c == '/')
				{
					output.append("__");
				}
				else
				{
					output.append(format("_%x_", c));
				}
			}
			final String outputString = output.toString();
			if (!allocatedNames.contains(outputString))
			{
				allocatedNames.add(outputString);
				encounteredNames.put(input, outputString);
				return outputString;
			}
		}
		// Even the complete name is in conflict.  Append a single
		// underscore and some unique decimal digits.
		output.append("_");
		final String leadingPart = output.toString();
		int sequence = 2;
		while (true)
		{
			final String outputString = leadingPart + sequence;
			if (!allocatedNames.contains(outputString))
			{
				allocatedNames.add(outputString);
				encounteredNames.put(input, outputString);
				return outputString;
			}
			sequence++;
		}
	}

	/**
	 * Write the given (reduced) module dependency graph as a
	 * <strong>dot</strong> file suitable for layout via Graphviz.
	 *
	 * @param reducedGraph
	 *        The graph of fully qualified module names.
	 * @param spanningDag
	 *        The reduced spanning dag used to control the layout.
	 */
	private void renderGraph (
		final Graph<ResolvedModuleName> reducedGraph,
		final Graph<ResolvedModuleName> spanningDag)
	{
		assert reducedGraph.vertexCount() == spanningDag.vertexCount();
		final Map<String, ModuleTree> trees = new HashMap<>();
		final ModuleTree root =
			new ModuleTree("root_", "Module Dependencies", null);
		trees.put("", root);
		for (final ResolvedModuleName moduleName : reducedGraph.vertices())
		{
			String string = moduleName.qualifiedName();
			ModuleTree node = new ModuleTree(
				asNodeName(string),
				string.substring(string.lastIndexOf('/') + 1),
				moduleName);
			trees.put(string, node);
			while (true)
			{
				string = string.substring(0, string.lastIndexOf('/'));
				final ModuleTree previous = node;
				node = trees.get(string);
				if (node == null)
				{
					node = new ModuleTree(
						asNodeName(string),
						string.substring(string.lastIndexOf('/') + 1),
						null);
					trees.put(string, node);
					node.addChild(previous);
				}
				else
				{
					node.addChild(previous);
					break;
				}
			}
		}

		final StringBuilder out = new StringBuilder();
		final Continuation1NotNull<Integer> tab =
			count -> Strings.tab(out, count);
		root.recursiveDo(
			// Before the node.
			(node, depth) ->
			{
				tab.value(depth);
				if (node == root)
				{
					out.append("digraph ");
					out.append(node.node);
					out.append("\n");
					tab.value(depth);
					out.append("{\n");
					tab.value(depth + 1);
					out.append("remincross = true;\n");
					tab.value(depth + 1);
					out.append("compound = true;\n");
					tab.value(depth + 1);
					out.append("splines = compound;\n");
					tab.value(depth + 1);
					out.append(
						"node ["
						+ "shape=box, "
						+ "margin=\"0.1,0.1\", "
						+ "width=0, "
						+ "height=0, "
						+ "style=filled, "
						+ "fillcolor=moccasin "
						+ "];\n");
					tab.value(depth + 1);
					out.append("edge [color=grey];\n");
					tab.value(depth + 1);
					out.append("label = ");
					out.append(node.safeLabel());
					out.append(";\n\n");
				}
				else if (node.resolvedModuleName == null)
				{
					out.append("subgraph cluster_");
					out.append(node.node);
					out.append('\n');
					tab.value(depth);
					out.append("{\n");
					tab.value(depth + 1);
					out.append("label = ");
					out.append(node.safeLabel());
					out.append(";\n");
					tab.value(depth + 1);
					out.append("penwidth = 2.0;\n");
					tab.value(depth + 1);
					out.append("fontsize = 18;\n");
				}
				else
				{
					out.append(node.node);
					out.append(" [label=");
					out.append(node.safeLabel());
					out.append("];\n");
				}
			},
			// After the node.
			(node, depth) ->
			{
				if (node == root)
				{
					out.append("\n");
					// Output *all* the edges.
					for (final ResolvedModuleName from :
						reducedGraph.vertices())
					{
						final String qualified = from.qualifiedName();
						final ModuleTree fromNode = trees.get(qualified);
						final String [] parts = qualified.split("/");
						final boolean fromPackage =
							parts[parts.length - 2].equals(
								parts[parts.length - 1]);
						for (final ResolvedModuleName to :
							reducedGraph.successorsOf(from))
						{
							final String toName =
								asNodeName(to.qualifiedName());
							tab.value(depth + 1);
							out.append(fromNode.node);
							out.append(" -> ");
							out.append(toName);
							final List<String> edgeStrings = new ArrayList<>();
							if (fromPackage)
							{
								final ModuleTree parent =
									stripNull(fromNode.parent());
								final String parentName =
									"cluster_" + parent.node;
								edgeStrings.add("ltail=" + parentName);
							}
							if (!spanningDag.includesEdge(from, to))
							{
								// This is a back-edge.
								edgeStrings.add("constraint=false");
								edgeStrings.add("color=crimson");
								edgeStrings.add("penwidth=3.0");
								edgeStrings.add("style=dashed");
							}
							if (!edgeStrings.isEmpty())
							{
								out.append("[");
								out.append(join(", ", edgeStrings));
								out.append("]");
							}
							out.append(";\n");
						}
					}
					tab.value(depth);
					out.append("}\n");
				}
				else if (node.resolvedModuleName == null)
				{
					tab.value(depth);
					out.append("}\n");
				}
			},
			0);
		final AsynchronousFileChannel channel;
		try
		{
			channel = availBuilder.runtime.ioSystem().openFile(
				outputFile.toPath(),
				EnumSet.of(
					StandardOpenOption.WRITE,
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING));
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
		final ByteBuffer buffer = StandardCharsets.UTF_8.encode(out.toString());
		final MutableInt position = new MutableInt(0);
		channel.write(
			buffer,
			0,
			null,
			new SimpleCompletionHandler<>(
				(result, unused, handler) -> {
					position.value += stripNull(result);
					if (buffer.hasRemaining())
					{
						channel.write(
							buffer,
							position.value,
							null,
							handler);
					}
				},
				(t, unused, handler) -> { }));
	}
}
