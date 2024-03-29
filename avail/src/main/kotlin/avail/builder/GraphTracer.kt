/*
 * GraphTracer.kt
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

package avail.builder

import avail.builder.AvailBuilder.ModuleTree
import avail.io.SimpleCompletionHandler
import avail.utility.Graph
import avail.utility.Strings.tab
import avail.utility.dot.DotWriter
import java.io.File
import java.io.IOException
import java.lang.String.format
import java.nio.channels.AsynchronousFileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.EnumSet

/**
 * Used for graphics generation.
 *
 * TODO: Rewrite using [DotWriter].
 *
 * @property availBuilder
 *   The [AvailBuilder] for which to generate a graph.
 * @property targetModule
 *   The module whose ancestors are to be graphed.
 * @property outputFile
 *   The output file into which the graph should be written in `.gv` "dot"
 *   format.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `GraphTracer`.
 *
 * @param availBuilder
 *   The [AvailBuilder] for which to generate a graph.
 * @param targetModule
 *   The module whose ancestors are to be graphed.
 * @param outputFile
 *   The [file][File] into which to write the graph.
 */
internal class GraphTracer constructor(
	private val availBuilder: AvailBuilder,
	private val targetModule: ResolvedModuleName,
	private val outputFile: File)
{
	/**
	 * All full names that have been encountered for nodes so far, with their
	 * more readable abbreviations.
	 */
	private val encounteredNames: MutableMap<String, String> = mutableMapOf()

	/**
	 * All abbreviated names that have been allocated so far as values of
	 * [encounteredNames].
	 */
	private val allocatedNames = mutableSetOf<String>()

	/**
	 * Scan all module files in all visible source directories, writing the
	 * graph as a Graphviz `.gv` file in **dot** format.
	 */
	fun traceGraph()
	{
		if (!availBuilder.shouldStopBuild)
		{
			val ancestry =
				availBuilder.moduleGraph.ancestryOfAll(setOf(targetModule))
			val dag = ancestry.spanningDag
			val reduced = ancestry.withoutRedundantEdges(dag)
			renderGraph(reduced, dag)
		}
		availBuilder.trimGraphToLoadedModules()
	}

	/**
	 * Convert a fully qualified module name into a suitably tidy symbolic name
	 * for a node.  This uses both [encounteredNames] and [allocatedNames] to
	 * bypass conflicts.
	 *
	 * Node names are rather restrictive in Graphviz, so non-alphanumeric
	 * characters are converted to "_xxxxxx_", where the x's are a non-padded
	 * hex representation of the character's Unicode code point. Slashes are
	 * converted to "__".
	 *
	 * @param input
	 *   The fully qualified module name.
	 * @return
	 *   A unique node name.
	 */
	private fun asNodeName(input: String): String
	{
		if (encounteredNames.containsKey(input))
		{
			return encounteredNames[input]!!
		}
		assert(input[0] == '/')
		// Try naming it locally first.
		var startPosition = input.length + 1
		val output = StringBuilder(startPosition + 10)
		while (startPosition > 1)
		{
			// Include successively more context until it works.
			output.setLength(0)
			startPosition = input.lastIndexOf('/', startPosition - 2) + 1
			var c: Int
			var i = startPosition
			while (i < input.length)
			{
				c = input.codePointAt(i)
				if (('a'.code <= c && c <= 'z'.code)
					|| ('A'.code <= c && c <= 'Z'.code)
					|| (i > startPosition && '0'.code <= c
						&& c <= '9'.code))
				{
					output.appendCodePoint(c)
				}
				else if (c == '/'.code)
				{
					output.append("__")
				}
				else
				{
					output.append(format("_%x_", c))
				}
				i += Character.charCount(c)
			}
			val outputString = output.toString()
			if (!allocatedNames.contains(outputString))
			{
				allocatedNames.add(outputString)
				encounteredNames[input] = outputString
				return outputString
			}
		}
		// Even the complete name is in conflict.  Append a single underscore
		// and some unique decimal digits.
		output.append("_")
		val leadingPart = output.toString()
		var sequence = 2
		while (true)
		{
			val outputString = leadingPart + sequence
			if (!allocatedNames.contains(outputString))
			{
				allocatedNames.add(outputString)
				encounteredNames[input] = outputString
				return outputString
			}
			sequence++
		}
	}

	/**
	 * Write the given (reduced) module dependency graph as a **dot** file
	 * suitable for layout via Graphviz.
	 *
	 * @param reducedGraph
	 *   The graph of fully qualified module names.
	 * @param spanningDag
	 *   The reduced spanning dag used to control the layout.
	 */
	private fun renderGraph(
		reducedGraph: Graph<ResolvedModuleName>,
		spanningDag: Graph<ResolvedModuleName>)
	{
		assert(reducedGraph.vertexCount == spanningDag.vertexCount)
		val trees = mutableMapOf<String, ModuleTree>()
		val root = ModuleTree(
			"root_",
			"Module Dependencies",
			null)
		trees[""] = root
		for (moduleName in reducedGraph.vertices)
		{
			var string = moduleName.qualifiedName
			var node: ModuleTree? = ModuleTree(
				asNodeName(string),
				string.substring(string.lastIndexOf('/') + 1),
				moduleName)
			trees[string] = node!!
			while (true)
			{
				string = string.substring(0, string.lastIndexOf('/'))
				val previous = node!!
				node = trees[string]
				if (node === null)
				{
					node = ModuleTree(
						asNodeName(string),
						string.substring(string.lastIndexOf('/') + 1), null)
					trees[string] = node
					node.addChild(previous)
				}
				else
				{
					node.addChild(previous)
					break
				}
			}
		}

		val out = buildString {
			root.recursiveDo(
				// Before the node.
				{ node, depth ->
					tab(depth)
					when {
						node === root -> {
							append("digraph ")
							append(node.node)
							append("\n")
							tab(depth)
							append("{\n")
							tab(depth + 1)
							append("remincross = true;\n")
							tab(depth + 1)
							append("compound = true;\n")
							tab(depth + 1)
							append("splines = compound;\n")
							tab(depth + 1)
							append(
								"node ["
									+ "shape=box, "
									+ "margin=\"0.1,0.1\", "
									+ "width=0, "
									+ "height=0, "
									+ "style=filled, "
									+ "fillcolor=moccasin "
									+ "];\n")
							tab(depth + 1)
							append("edge [color=grey];\n")
							tab(depth + 1)
							append("label = ")
							append(node.safeLabel)
							append(";\n\n")
						}
						node.resolvedModuleName === null -> {
							append("subgraph cluster_")
							append(node.node)
							append('\n')
							tab(depth)
							append("{\n")
							tab(depth + 1)
							append("label = ")
							append(node.safeLabel)
							append(";\n")
							tab(depth + 1)
							append("penwidth = 2.0;\n")
							tab(depth + 1)
							append("fontsize = 18;\n")
						}
						else -> {
							append(node.node)
							append(" [label=")
							append(node.safeLabel)
							append("];\n")
						}
					}
				},
				// After the node.
				{ node, depth ->
					if (node === root) {
						append("\n")
						// Output *all* the edges.
						for (from in reducedGraph.vertices) {
							val qualified = from.qualifiedName
							val fromNode = trees[qualified]!!
							val parts = qualified
								.split("/".toRegex())
								.dropLastWhile { it.isEmpty() }
								.toTypedArray()
							val fromPackage = parts[parts.size - 2] ==
								parts[parts.size - 1]
							for (to in reducedGraph.successorsOf(from)) {
								val toName = asNodeName(to.qualifiedName)
								tab(depth + 1)
								append(fromNode.node)
								append(" -> ")
								append(toName)
								val edgeStrings = mutableListOf<String>()
								if (fromPackage) {
									val parent = fromNode.parent!!
									val parentName = "cluster_" + parent.node
									edgeStrings.add("ltail=$parentName")
								}
								if (!spanningDag.includesEdge(from, to)) {
									// This is a back-edge.
									edgeStrings.add("constraint=false")
									edgeStrings.add("color=crimson")
									edgeStrings.add("penwidth=3.0")
									edgeStrings.add("style=dashed")
								}
								if (edgeStrings.isNotEmpty()) {
									append("[")
									append(edgeStrings.joinToString(", "))
									append("]")
								}
								append(";\n")
							}
						}
						tab(depth)
						append("}\n")
					}
					else if (node.resolvedModuleName === null)
					{
						tab(depth)
						append("}\n")
					}
				},
				0)
		}
		val channel: AsynchronousFileChannel
		try
		{
			channel = availBuilder.runtime.ioSystem.openFile(
				outputFile.toPath(),
				EnumSet.of(
					StandardOpenOption.WRITE,
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING))
		}
		catch (e: IOException)
		{
			throw RuntimeException(e)
		}

		val buffer = StandardCharsets.UTF_8.encode(out)
		var position = 0
		SimpleCompletionHandler<Int>(
			{
				position += value
				if (buffer.hasRemaining()) {
					handler.guardedDo {
						channel.write(buffer, position.toLong(), Unit, handler)
					}
				}
			},
			{ }
		).guardedDo {
			channel.write(buffer, 0L, Unit, handler)
		}
	}
}
