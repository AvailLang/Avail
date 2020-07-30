/*
 * DotWriter.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

@file:Suppress("unused")

package com.avail.utility.dot

import com.avail.utility.Strings.tabs
import com.avail.utility.cast
import com.avail.utility.dot.DotWriter.AttributeWriter
import com.avail.utility.dot.DotWriter.DefaultAttributeBlockType.EDGE
import com.avail.utility.dot.DotWriter.DefaultAttributeBlockType.GRAPH
import com.avail.utility.dot.DotWriter.DefaultAttributeBlockType.NODE
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors
import kotlin.math.max
import kotlin.math.min

/**
 * `DotWriter` produces source text for **`dot`**, the widely supported graph
 * description language. Such source text can be passed to numerous renderers,
 * most notably *Graphviz*, to produce graph visualizations.
 *
 * `DotWriter` is not able to generate every sentence recognized by `dot`, but
 * it is able to access every semantic feature afforded by `dot`. All APIs
 * automatically escape identifiers as necessary, always choosing to provide a
 * minimal text when several semantically equivalent texts are available.
 * Support is deliberately omitted for the `_`
 * [CompassPoint][compass&#32;point], since omitting the compass point of a node
 * in an edge specification is equivalent to specifying `_` as the compass
 * point.
 *
 * `DotWriter` exposes the ability to generate `dot` source files through a
 * hierarchy of contextual emitters rooted at [AttributeWriter]. These emitters
 * are provided to a lambda through dependency injection, and care should be
 * taken in the implementation of a lambda only to use the injected emitter (and
 * not some other lexically available emitter created in an outer dynamic
 * scope).
 *
 * @property name
 *   The name of the graph.
 * @property isDirected
 *   If `true`, then a directed graph will be generated; otherwise, an
 *   undirected graph will be generated.
 * @property charactersPerLine
 *   The number of characters to emit per line. Only applies to formatting of
 *   block comments.
 * @property accumulator
 *   The accumulator for the generated source code.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see [The&#32;DOT Language](https://www.graphviz.org/doc/info/lang.html)
 * @see [Node, Edge, and Graph Attributes](https://www.graphviz.org/doc/info/attrs.html)
 * @see [Node&#32;Shapes](https://www.graphviz.org/doc/info/shapes.html)
 * @see [Graphviz&#32;for a web browser](http://viz-js.com/)
 *
 * @constructor
 *
 * Construct a new `DotWriter`.
 *
 * @param name
 *   The name of the graph.
 * @param isDirected
 *   If `true`, then a directed graph will be generated; otherwise, an
 *   undirected graph will be generated.
 * @param charactersPerLine
 *   The number of characters to emit per line. Only applies to formatting of
 *   block comments.
 * @param accumulator
 *   The accumulator for the generated source code.
 * @param darkMode
 *   If true, draw with light colors on a dark background.
 * @param copyrightOwner
 *   This name is embedded in a copyright claim within the document.
 */
class DotWriter constructor(
	private val name: String,
	internal val isDirected: Boolean,
	internal val charactersPerLine: Int,
	internal val accumulator: Appendable,
	private val darkMode: Boolean = true,
	val copyrightOwner: String = System.getProperty("user.name"))
{
	/**
	 * The indentation level.
	 */
	internal var indentationLevel = 0

	/**
	 * Was the last character to be emitted a linefeed? Initialize this to
	 * `true` so that the first [AttributeWriter.indent] is valid.
	 */
	internal var justEmittedLinefeed = true

	/**
	 * The prebuilt [AttributeWriter] for dependency injection.
	 */
	internal val attributeWriter = AttributeWriter()

	/**
	 * The prebuilt [GraphWriter] for dependency injection.
	 */
	internal val graphWriter = GraphWriter()

	/**
	 * An `AttributeWriter` provides the ability to write generally
	 * available `dot` elements, e.g., indentation, comments, identifiers,
	 * attributes, etc.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	open inner class AttributeWriter
	{
		/**
		 * Increase the indentation during application of the supplied lambda.
		 *
		 * @param block
		 *   The lambda to apply while indentation is increased.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		internal fun <T : AttributeWriter> increaseIndent(
			writer: T,
			block: (T) -> Unit)
		{
			indentationLevel++
			try
			{
				block(writer)
			}
			finally
			{
				indentationLevel--
			}
		}

		/**
		 * Emit an appropriate amount of indentation, as horizontal tabs. It is
		 * assumed that the most recently written character is a line feed.
		 *
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun indent()
		{
			assert(indentationLevel >= 0) {
				"indentation level must not be negative"
			}
			assert(justEmittedLinefeed) {
				"indentation must only be emitted after a linefeed"
			}
			accumulator.append(tabs(indentationLevel))
			justEmittedLinefeed = false
		}

		/**
		 * Emit arbitrary text. Updates [justEmittedLinefeed]
		 * appropriately, based on whether the last character of the specified
		 * text is a linefeed.
		 *
		 * @param text
		 *   The arbitrary text to emit.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun emit(text: String)
		{
			accumulator.append(text)
			justEmittedLinefeed = text.endsWith("\n")
		}

		/**
		 * Emit a linefeed. Updates [justEmittedLinefeed] to `true`.
		 *
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		internal fun linefeed()
		{
			accumulator.append('\n')
			justEmittedLinefeed = true
		}

		/**
		 * Emit an end-of-line comment.
		 *
		 * @param comment
		 *   The end-of-line comment.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun endOfLineComment(comment: String)
		{
			accumulator.append("//")
			if (comment.isNotEmpty())
			{
				accumulator.append(' ')
				accumulator.append(comment)
			}
			linefeed()
		}

		/**
		 * Make a best effort to extract a line from the specified string that
		 * is no longer than the given number of characters.
		 *
		 * @param s
		 *   The string.
		 * @param max
		 *   The maximum number of characters (excluding a linefeed).
		 * @return
		 *   A [Pair] comprising, respectively, the next line and the residue
		 *   (i.e., everything not part of the next line or its immediately
		 *   trailing whitespace).
		 */
		private fun extractLine(s: String, max: Int): Pair<String, String>
		{
			// The index of the last whitespace character discovered.
			var whiteIndex = 0
			// The run length of that whitespace.
			var whiteRun = 0
			var i = 0
			val lineLimit = min(s.length, max)
			while (i < lineLimit)
			{
				var cp = s.codePointAt(i)
				if (cp == '\n'.toInt())
				{
					// Upon discovery of a linefeed, compute the next line and
					// the residue.
					return Pair(s.substring(0, i), s.substring(i + 1))
				}
				if (Character.isWhitespace(cp))
				{
					// Note the first whitespace character discovered. Skip any
					// subsequent whitespace characters — if they occur at the
					// end of a line, then they will be omitted.
					whiteIndex = i
					whiteRun = 0
					do
					{
						val sz = Character.charCount(cp)
						i += sz
						whiteRun += sz
						cp =
							if (i < s.length) s.codePointAt(i) else 'x'.toInt()
					}
					while (Character.isWhitespace(cp))
				}
				else
				{
					// Otherwise, just move on to the next character.
					i += Character.charCount(cp)
				}
			}
			if (whiteIndex > 0)
			{
				// If any whitespace was discovered, then answer the next line
				// and the residue.
				return Pair(
					s.substring(0, whiteIndex),
					s.substring(whiteIndex + whiteRun))
			}
			// If no whitespace was discovered, then we cannot honor the
			// character limit strictly. Look for the next whitespace
			// character and terminate the line there.
			val wideLimit = s.length
			while (i < wideLimit)
			{
				var cp = s.codePointAt(i)
				if (Character.isWhitespace(cp))
				{
					whiteIndex = i
					whiteRun = 0
					do
					{
						val sz = Character.charCount(cp)
						i += sz
						whiteRun += sz
						cp =
							if (i < wideLimit) s.codePointAt(i) else 'x'.toInt()
					}
					while (Character.isWhitespace(cp))
					break
				}
				else
				{
					i += Character.charCount(cp)
				}
			}
			if (whiteIndex == 0)
			{
				// If no whitespace characters were ever discovered and the
				// limit was exceeded, then answer the entire string as the
				// line and empty residue.
				assert(whiteRun == 0)
				return Pair(s, "")
			}
			return Pair(
				s.substring(0, whiteIndex),
				s.substring(whiteIndex + whiteRun))
		}

		/**
		 * Emit a block comment. A best effort will be made to keep the lines of
		 * the block comment within the specified [limit][charactersPerLine],
		 * accounting for indentation and comment overhead.
		 *
		 * @param comment
		 *   The block comment.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun blockComment(comment: String)
		{
			val limit = max(1, charactersPerLine - 4 * indentationLevel - 3)
			var residue = comment
			while (residue.isNotEmpty())
			{
				val pair = extractLine(residue, limit)
				val line = pair.first
				indent()
				endOfLineComment(line)
				residue = pair.second
			}
		}

		/**
		 * Is the alleged identifier actually a `dot` keyword?
		 *
		 * @param id
		 *   The would-be identifier.
		 * @return
		 *   `true` if the would-be identifier is actually a `dot` keyword,
		 *   `false` otherwise.
		 */
		private fun isKeyword(id: String) = keywords.contains(id)

		/**
		 * Emit an appropriately escaped variant of the proposed identifier.
		 *
		 * @param proposedId
		 *   The identifier.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		internal fun identifier(proposedId: String)
		{
			val id: String
			when
			{
				isKeyword(proposedId) ->
					// If the proposed identifier is a keyword, then it needs to
					// be quoted before it can be used as an identifier.
					id = """"$proposedId""""
				tokenPattern.matcher(proposedId).matches() ->
					// The proposed identifier comprises only alphanumerics, so
					// it doesn't require any escaping.
					id = proposedId
				proposedId.startsWith("<") ->
					// The proposed identifier is HTML-like, so escape it inside
					// angle brackets.
					id = "<$proposedId>"
				else ->
					// The proposed identifier contains problematic characters,
					// so it must be appropriately escaped.
					id = "\"" + proposedId.codePoints()
						.mapToObj { cp ->
							val s = when (cp)
							{
								'\"'.toInt() -> "\\\""
								'\n'.toInt() -> "\\n"
								else -> String(Character.toChars(cp))
							}
							s
						}
						.collect(Collectors.joining()) + "\""
			}
			emit(id)
		}

		/**
		 * Emit a simple attribute statement.
		 *
		 * @param lhs
		 *   The assignment target.
		 * @param rhs
		 *   The value to bind to the assignment target.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun attribute(lhs: String, rhs: String)
		{
			val isColor = lhs.contains("color")
			indent()
			identifier(lhs)
			emit(" = ")
			identifier(if (isColor) adjust(rhs) else rhs)
			linefeed()
		}

		/**
		 * Answer an adjusted rhs (right hand side), taking into account which
		 * format the rhs takes, and whether dark mode is active.
		 */
		fun adjust(rhs: String): String {
			// Look for #xxxxxx/xxxxxx (light/dark) notation first.
			val multiMatcher = multicolorPattern.matcher(rhs)
			if (multiMatcher.find()) {
				return "#" + multiMatcher.group(if (darkMode) 2 else 1)
			}
			if (!darkMode) {
				return rhs
			}
			val uniMatcher = unicolorPattern.matcher(rhs)
			if (uniMatcher.find()) {
				val original = Integer.parseInt(uniMatcher.group(1), 16)
				var r = original shr 16
				var g = original shr 16 and 255
				var b = original and 255
				// Compress each component into the upper 1/2 range.
				r = 255 - ((255 - r) shr 1)
				g = 255 - ((255 - g) shr 1)
				b = 255 - ((255 - b) shr 1)
				return String.format("#%02x%02x%02x", r, g, b)
			}
			// Maybe a symbolic color name.  Leave it alone.
			return rhs
		}

		/**
		 * Answer an adjusted rhs (right hand side), taking into account which
		 * format the rhs takes, and whether dark mode is active.  This method
		 * takes a boolean, which if true processes the second argument,
		 * otherwise the third.
		 */
		fun adjust(
			condition: Boolean, trueString: String, falseString: String
		): String =
			adjust(if (condition) trueString else falseString)
	}

	/**
	 * `DefaultAttributeBlockType` represents the scope of a default attributes
	 * block. The three supported scopes are [GRAPH], [NODE], and [EDGE]. The
	 * [names][name] of the enumeration values are chosen to match `dot`
	 * keywords and must not be changed.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	enum class DefaultAttributeBlockType
	{
		/** Graph scope.  */
		GRAPH,

		/** Node scope.  */
		NODE,

		/** Edge scope.  */
		EDGE
	}

	/**
	 * `CompassPoint` represents one of the compass points allowed for edge
	 * attachments to node ports. The [names][name] of the enumeration values
	 * are chosen to match `dot` keywords and must not be changed.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	enum class CompassPoint
	{
		/** Northwest.  */
		NW,

		/** North.  */
		N,

		/** Northeast.  */
		NE,

		/** West.  */
		W,

		/** Center.  */
		C,

		/** East.  */
		E,

		/** Southwest.  */
		SW,

		/** South.  */
		S,

		/** Southeast.  */
		SE
	}

	/**
	 * `DecoratedNode` represents a decorated node that includes an optional
	 * port and an optional [CompassPoint]. It exists only for edge
	 * specification, as `dot` does not permit decorated nodes to appear on
	 * their own.
	 *
	 * @property name
	 *   The name of the node.
	 * @property port
	 *   The name of the port.
	 * @property compassPoint
	 *   The [CompassPoint], if any.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 *
	 * @constructor
	 *
	 * Construct a new `DecoratedNode`.
	 *
	 * @param name
	 *   The name of the node.
	 * @param port
	 *   The name of the port.
	 * @param compassPoint
	 *   The [CompassPoint], if any.
	 */
	class DecoratedNode internal constructor(
		internal val name: String,
		internal val port: String?,
		internal val compassPoint: CompassPoint?)

	/**
	 * A `GraphWriter` provides the capability of writing entire graphs, and as
	 * such is able to emit
	 * [default&#32;attribute&#32;blocks][defaultAttributeBlock], [nodes][node],
	 * and [edges][edge].
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	inner class GraphWriter : AttributeWriter()
	{
		/**
		 * Emit an appropriately indented attribute block.
		 *
		 * @param block
		 *   The lambda to apply to emit the content of the block.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		private fun attributeBlock(block: (AttributeWriter) -> Unit)
		{
			indent()
			emit("[\n")
			increaseIndent(attributeWriter, block)
			indent()
			emit("]\n")
		}

		/**
		 * Emit an appropriately indented default attribute block.
		 *
		 * @param type
		 *   The [DefaultAttributeBlockType] of the default attribute block.
		 * @param block
		 *   The lambda to apply to emit the content of the block.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun defaultAttributeBlock(
			type: DefaultAttributeBlockType,
			block: (AttributeWriter) -> Unit)
		{
			indent()
			emit(type.name.toLowerCase())
			linefeed()
			attributeBlock(block)
		}

		/**
		 * Write an appropriately indented anonymous subgraph block.
		 *
		 * @param block
		 *   The lambda to apply to emit the content of the subgraph.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun subgraph(block: (GraphWriter) -> Unit)
		{
			indent()
			emit("{\n")
			increaseIndent(graphWriter, block)
			indent()
			emit("}\n")
		}

		/**
		 * Write an appropriately indented named subgraph block.
		 *
		 * @param subgraphName
		 *   The name of the subgraph.
		 * @param block
		 *   The lambda to apply to emit the content of the subgraph.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun subgraph(subgraphName: String, block: (GraphWriter) -> Unit)
		{
			indent()
			emit("subgraph ")
			identifier(subgraphName)
			linefeed()
			subgraph(block)
		}

		/**
		 * Emit a node with attributes.
		 *
		 * @param nodeName
		 *   The identifier of the node.
		 * @param block
		 *   The lambda to apply to generate the node's attributes.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun node(
			nodeName: String,
			block: ((AttributeWriter) -> Unit)?)
		{
			indent()
			identifier(nodeName)
			linefeed()
			if (block !== null)
			{
				attributeBlock(block)
			}
		}

		/**
		 * Emit the appropriate edge operator.
		 *
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		private fun edgeOperator() = emit(if (isDirected) " -> " else " -- ")

		/**
		 * Emit an edge with attributes.
		 *
		 * @param source
		 *   The identifier of the source node.
		 * @param target
		 *   The identifier of the target node.
		 * @param block
		 *   The lambda to apply to generate the edge's attributes.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun edge(
			source: String,
			target: String,
			block: ((AttributeWriter) -> Unit)?)
		{
			indent()
			identifier(source)
			edgeOperator()
			identifier(target)
			linefeed()
			if (block !== null)
			{
				attributeBlock(block)
			}
		}

		/**
		 * Emit a [DecoratedNode] with port and [compass][CompassPoint]
		 * information.
		 *
		 * @param node
		 *   The node.
		 */
		private fun nodeReference(node: DecoratedNode)
		{
			identifier(node.name)
			if (node.port !== null)
			{
				emit(":")
				identifier(node.port)
			}
			if (node.compassPoint !== null)
			{
				emit(":")
				emit(node.compassPoint.name.toLowerCase())
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 *   The source [node][DecoratedNode].
		 * @param target
		 *   The target [node][DecoratedNode].
		 * @param block
		 *   The lambda to apply to generate the edge's attributes.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun edge(
			source: DecoratedNode,
			target: DecoratedNode,
			block: ((AttributeWriter) -> Unit)?)
		{
			indent()
			nodeReference(source)
			edgeOperator()
			nodeReference(target)
			linefeed()
			if (block !== null)
			{
				attributeBlock(block)
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 *   The lambda to apply to generate the source subgraph.
		 * @param target
		 *   The identifier of the target node.
		 * @param block
		 *   The lambda to apply to generate the edge's attributes.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun edge(
			source: (GraphWriter) -> Unit,
			target: String,
			block: ((AttributeWriter) -> Unit)?)
		{
			subgraph(source)
			edgeOperator()
			identifier(target)
			linefeed()
			if (block !== null)
			{
				attributeBlock(block)
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 *   The lambda to apply to generate the source subgraph.
		 * @param target
		 *   The target [node][DecoratedNode].
		 * @param block
		 *   The lambda to apply to generate the edge's attributes.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun edge(
			source: (GraphWriter) -> Unit,
			target: DecoratedNode,
			block: ((AttributeWriter) -> Unit)?)
		{
			subgraph(source)
			edgeOperator()
			nodeReference(target)
			linefeed()
			if (block !== null)
			{
				attributeBlock(block)
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 *   The identifier of the source node.
		 * @param target
		 *   The lambda to apply to generate the target subgraph.
		 * @param block
		 *   The lambda to apply to generate the edge's attributes.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun edge(
			source: String,
			target: (GraphWriter) -> Unit,
			block: ((AttributeWriter) -> Unit)?)
		{
			identifier(source)
			edgeOperator()
			linefeed()
			subgraph(target)
			linefeed()
			if (block !== null)
			{
				attributeBlock(block)
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 *   The source [node][DecoratedNode].
		 * @param target
		 *   The lambda to apply to generate the target subgraph.
		 * @param block
		 *   The lambda to apply to generate the edge's attributes.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun edge(
			source: DecoratedNode,
			target: (GraphWriter) -> Unit,
			block: ((AttributeWriter) -> Unit)?)
		{
			nodeReference(source)
			edgeOperator()
			linefeed()
			subgraph(target)
			linefeed()
			if (block !== null)
			{
				attributeBlock(block)
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 *   The lambda to apply to generate the source subgraph.
		 * @param target
		 *   The lambda to apply to generate the target subgraph.
		 * @param block
		 *   The lambda to apply to generate the edge's attributes.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun edge(
			source: (GraphWriter) -> Unit,
			target: (GraphWriter) -> Unit,
			block: ((AttributeWriter) -> Unit)?)
		{
			subgraph(source)
			edgeOperator()
			linefeed()
			subgraph(target)
			linefeed()
			if (block !== null)
			{
				attributeBlock(block)
			}
		}

		/**
		 * Emit interleaved nodes and edges. If no nodes are specified, then
		 * do nothing.
		 *
		 * @param nodes
		 *   The nodes.
		 * @param block
		 *   The lambda to apply to generate the edges' attributes.
		 * @throws IOException
		 *   If emission fails.
		 */
		@Throws(IOException::class)
		fun interleaved(
			nodes: List<Any>,
			block: ((AttributeWriter) -> Unit)?)
		{
			if (nodes.isNotEmpty())
			{
				var i = 0
				val limit = nodes.size
				while (i < limit)
				{
					val o = nodes[i]
					if (o is String)
					{
						identifier(o)
					}
					else if (o is DecoratedNode)
					{
						identifier(o.name)
						if (o.port !== null)
						{
							emit(":")
							identifier(o.port)
						}
						if (o.compassPoint !== null)
						{
							emit(":")
							identifier(o.compassPoint.name.toLowerCase())
						}
					}
					else if (o is Function1<*, *>)
					{
						if (i != 0)
						{
							linefeed()
						}
						subgraph(o.cast())
					}
					else
					{
						assert(false) {
							"""allowed node types are String, DecoratedNode
							and CheckedConsumer<GraphWriter>, but
							${o.javaClass.name} is none of these""".trim()
						}
						throw RuntimeException()
					}
					if (i < limit - 1)
					{
						edgeOperator()
					}
					i++
				}
				linefeed()
				if (block !== null)
				{
					attributeBlock(block)
				}
			}
		}
	}

	/**
	 * Emit a block comment, such as a copyright banner or statement of
	 * purpose.
	 *
	 * @param comment
	 *   The comment.
	 * @throws IOException
	 *   If emission fails.
	 */
	@Throws(IOException::class)
	fun blockComment(comment: String) = graphWriter.blockComment(comment)

	/**
	 * Emit an entire graph.
	 *
	 * @param block
	 *   The lambda to apply to emit the content of the graph.
	 * @throws IOException
	 *   If emission fails.
	 */
	@Throws(IOException::class)
	fun graph(block: (GraphWriter) -> Unit)
	{
		val writer = graphWriter
		writer.indent()
		writer.emit(if (isDirected) "digraph " else "graph ")
		writer.identifier(name)
		writer.linefeed()
		writer.subgraph(block)
	}

	companion object
	{

		/** A single tab character as a [Pattern].  */
		private val tab = Pattern.compile("\t", Pattern.LITERAL)

		/**
		 * Answer a variant of the specified text such that tabs are replaced by
		 * spaces, for use with the `label`, `headlabel`, and `taillabel`
		 * attributes.
		 *
		 * @param text
		 *   Some arbitrary text.
		 * @return
		 *   The requested variant.
		 */
		fun label(text: String): String
		{
			return tab.matcher(text).replaceAll(
				Matcher.quoteReplacement("    "))
		}

		/** A single line-feed character as a [Pattern].  */
		private val lineFeed = Pattern.compile("\n", Pattern.LITERAL)

		/**
		 * Answer a left-justified variant of the specified text, for use with
		 * the `label`, `headlabel`, and `taillabel` attributes. Calls [label]
		 * first to convert tabs to spaces.
		 *
		 * @param text
		 *   Some arbitrary text.
		 * @return
		 *   The requested variant.
		 */
		fun leftJustified(text: String): String =
			lineFeed.matcher(label(text)).replaceAll(
				Matcher.quoteReplacement("\\l"))

		/**
		 * Answer a right-justified variant of the specified text, for use with
		 * the `label`, `headlabel`, and `taillabel` attributes. Calls [label]
		 * first to convert tabs to spaces.
		 *
		 * @param text
		 *   Some arbitrary text.
		 * @return
		 *   The requested variant.
		 */
		fun rightJustified(text: String): String =
			lineFeed.matcher(label(text)).replaceAll(
				Matcher.quoteReplacement("\\r"))

		/**
		 * The keywords reserved by `dot`. Identifiers must not collide with
		 * these names.
		 */
		internal val keywords = setOf(
			"strict", "graph", "digraph", "subgraph", "node", "edge")

		/** A pattern for matching tokens.  */
		internal val tokenPattern = Pattern.compile("[A-Za-z0-9]+")

		/**
		 * Answer a [DecoratedNode] suitable for complex edge specification.
		 *
		 * @param name
		 *   The name of the node.
		 * @return
		 *   The requested node.
		 */
		@JvmStatic fun node(name: String) = DecoratedNode(name, null, null)

		/**
		 * Answer a [DecoratedNode] suitable for complex edge specification.
		 *
		 * @param name
		 *   The name of the node.
		 * @param port
		 *   The name of the port.
		 * @return
		 *   The requested node.
		 */
		@JvmStatic fun node(name: String, port: String) =
			DecoratedNode(name, port, null)

		/**
		 * Answer a [DecoratedNode] suitable for complex edge specification.
		 *
		 * @param name
		 *   The name of the node.
		 * @param port
		 *   The name of the port.
		 * @param compassPoint
		 *   The [CompassPoint].
		 * @return
		 *   The requested node.
		 */
		@JvmStatic fun node(
				name: String,
				port: String,
				compassPoint: CompassPoint) =
			DecoratedNode(name, port, compassPoint)

		/** The pattern to match for light/dark bimodal colors. */
		val multicolorPattern: Pattern =
			Pattern.compile("^#([0-9a-fA-F]{6})/#?([0-9a-fA-F]{6})$")

		/** The pattern to match for light-only colors. */
		val unicolorPattern: Pattern = Pattern.compile("^#([0-9a-fA-F]{6})$")
	}
}
