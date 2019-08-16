/*
 * DotWriter.java
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

package com.avail.utility.dot;

import com.avail.annotations.InnerAccess;
import com.avail.utility.CheckedConsumer;
import com.avail.utility.Pair;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.avail.utility.Strings.tabs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;

/**
 * {@code DotWriter} produces source text for <strong>{@code dot}</strong>, the
 * widely supported graph description language. Such source text can be passed
 * to numerous renderers, most notably <em>Graphviz</em>, to produce graph
 * visualizations.
 *
 * <p>
 * {@code DotWriter} is not able to generate every sentence recognized by
 * {@code dot}, but it is able to access every semantic feature afforded by
 * {@code dot}. All APIs automatically escape identifiers as necessary, always
 * choosing to provide a minimal text when several semantically equivalent texts
 * are available. Support is deliberately omitted for the {@code _} {@linkplain
 * CompassPoint compass point}, since omitting the compass point of a node in an
 * edge specification is equivalent to specifying {@code _} as the compass
 * point.
 * </p>
 *
 * <p>
 * {@code DotWriter} exposes the ability to generate {@code dot} source files
 * through a hierarchy of contextual emitters rooted at {@link AttributeWriter}.
 * These emitters are provided to {@link CheckedConsumer} through dependency
 * injection, and care should be taken in the implementation of a {@link
 * CheckedConsumer} only to use the injected emitter (and not some other
 * lexically available emitter created in an outer dynamic scope).
 * </p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a href="https://www.graphviz.org/doc/info/lang.html">The DOT Language</a>
 * @see <a href="https://www.graphviz.org/doc/info/attrs.html">Node, Edge, and Graph Attributes</a>
 * @see <a href="https://www.graphviz.org/doc/info/shapes.html">Node Shapes</a>
 * @see <a href="http://viz-js.com/">Graphviz for a web browser</a>
 */
public final class DotWriter
{
	/**
	 * The name of the graph.
	 */
	private final String name;

	/**
	 * If {@code true}, then a directed graph will be generated; otherwise, an
	 * undirected graph will be generated.
	 */
	@InnerAccess final boolean isDirected;

	/**
	 * The number of characters to emit per line. Only applies to formatting
	 * of block comments.
	 */
	@InnerAccess final int charactersPerLine;

	/**
	 * The accumulator for the generated source code.
	 */
	@InnerAccess final Appendable accumulator;

	/**
	 * Construct a new {@code DotWriter}.
	 *
	 * @param name
	 *        The name of the graph.
	 * @param isDirected
	 *        If {@code true}, then a directed graph will be generated;
	 *        otherwise, an undirected graph will be generated.
	 * @param charactersPerLine
	 *        The number of characters to emit per line. Only applies to
	 *        formatting of block comments.
	 * @param accumulator
	 *        The accumulator for the generated source code.
	 */
	public DotWriter (
		final String name,
		final boolean isDirected,
		final int charactersPerLine,
		final Appendable accumulator)
	{
		this.name = name;
		this.isDirected = isDirected;
		this.charactersPerLine = charactersPerLine;
		this.accumulator = accumulator;
	}

	/** A single tab character as a {@link Pattern}. */
	private static final Pattern tab = Pattern.compile("\t", Pattern.LITERAL);

	/**
	 * Answer a variant of the specified text such that tabs are replaced by
	 * spaces, for use with the {@code label}, {@code headlabel}, and {@code
	 * taillabel} attributes.
	 *
	 * @param text
	 *        Some arbitrary text.
	 * @return The requested variant.
	 */
	public static String label (final String text)
	{
		return tab.matcher(text).replaceAll(Matcher.quoteReplacement("    "));
	}

	/** A single line-feed character as a {@link Pattern}. */
	private static final Pattern lineFeed =
		Pattern.compile("\n", Pattern.LITERAL);

	/**
	 * Answer a left-justified variant of the specified text, for use with the
	 * {@code label}, {@code headlabel}, and {@code taillabel} attributes. Calls
	 * {@link #label(String)} first to convert tabs to spaces.
	 *
	 * @param text
	 *        Some arbitrary text.
	 * @return The requested variant.
	 */
	@SuppressWarnings("unused")
	public static String leftJustified (final String text)
	{
		return lineFeed.matcher(label(text))
			.replaceAll(Matcher.quoteReplacement("\\l"));
	}

	/**
	 * Answer a right-justified variant of the specified text, for use with the
	 * {@code label}, {@code headlabel}, and {@code taillabel} attributes. Calls
	 * {@link #label(String)} first to convert tabs to spaces.
	 *
	 * @param text
	 *        Some arbitrary text.
	 * @return The requested variant.
	 */
	@SuppressWarnings("unused")
	public static String rightJustified (final String text)
	{
		return lineFeed.matcher(label(text))
			.replaceAll(Matcher.quoteReplacement("\\r"));
	}

	/**
	 * The keywords reserved by {@code dot}. Identifiers must not collide with
	 * these names.
	 */
	@InnerAccess static final Set<String> keywords =
		unmodifiableSet(new HashSet<>(asList(
			"strict", "graph", "digraph", "subgraph", "node", "edge")));

	/**
	 * The indentation level.
	 */
	@InnerAccess int indentationLevel = 0;

	/**
	 * Was the last character to be emitted a linefeed? Initialize this to
	 * {@code true} so that the first {@link AttributeWriter#indent()} is valid.
	 */
	@InnerAccess boolean justEmittedLinefeed = true;

	/** A pattern for matching tokens. */
	@InnerAccess static final Pattern tokenPattern =
		Pattern.compile("[A-Za-z0-9]+");

	/**
	 * An {@code AttributeWriter} provides the ability to write generally
	 * available {@code dot} elements, e.g., indentation, comments, identifiers,
	 * attributes, etc.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	public class AttributeWriter
	{
		/**
		 * Increase the indentation during application of the supplied {@link
		 * CheckedConsumer}.
		 *
		 * @param block
		 *        The {@code CheckedConsumer} to apply while indentation is
		 *        increased.
		 * @throws IOException
		 *         If emission fails.
		 */
		<T extends AttributeWriter> void increaseIndent (
			final T writer,
			final CheckedConsumer<? super T> block)
		throws IOException
		{
			indentationLevel++;
			try
			{
				block.accept(writer);
			}
			finally
			{
				indentationLevel--;
			}
		}

		/**
		 * Emit an appropriate amount of indentation, as horizontal tabs. It is
		 * assumed that the most recently written character is a line feed.
		 *
		 * @throws IOException
		 *         If emission fails.
		 */
		protected void indent () throws IOException
		{
			assert indentationLevel >= 0
				: "indentation level must not be negative";
			assert justEmittedLinefeed
				: "indentation must only be emitted after a linefeed";
			accumulator.append(tabs(indentationLevel));
			justEmittedLinefeed = false;
		}

		/**
		 * Emit arbitrary text. Updates {@link #justEmittedLinefeed}
		 * appropriately, based on whether the last character of the specified
		 * text is a linefeed.
		 *
		 * @param text
		 *        The arbitrary text to emit.
		 * @throws IOException
		 *         If emission fails.
		 */
		protected void emit (final String text) throws IOException
		{
			accumulator.append(text);
			justEmittedLinefeed = text.endsWith("\n");
		}

		/**
		 * Emit a linefeed. Updates {@link #justEmittedLinefeed} to {@code
		 * true}.
		 *
		 * @throws IOException
		 *         If emission fails.
		 */
		void linefeed () throws IOException
		{
			accumulator.append('\n');
			justEmittedLinefeed = true;
		}

		/**
		 * Emit an end-of-line comment.
		 *
		 * @param comment
		 *        The end-of-line comment.
		 * @throws IOException
		 *         If emission fails.
		 */
		@SuppressWarnings("WeakerAccess")
		public void endOfLineComment (final String comment) throws IOException
		{
			accumulator.append("//");
			if (!comment.isEmpty())
			{
				accumulator.append(' ');
				accumulator.append(comment);
			}
			linefeed();
		}

		/**
		 * Make a best effort to extract a line from the specified string that
		 * is no longer than the given number of characters.
		 *
		 * @param s
		 *        The string.
		 * @param max
		 *        The maximum number of characters (excluding a linefeed).
		 * @return A {@link Pair} comprising, respectively, the next line and
		 *         the residue (i.e., everything not part of the next line or
		 *         its immediately trailing whitespace).
		 */
		private Pair<String, String> extractLine (final String s, final int max)
		{
			// The index of the last whitespace character discovered.
			int wsi = 0;
			// The run length of that whitespace.
			int wlen = 0;
			int i = 0;
			final int lineLimit = min(s.length(), max);
			while (i < max)
			{
				int cp = s.codePointAt(i);
				if (cp == '\n')
				{
					// Upon discovery of a linefeed, compute the next line and
					// the residue.
					return new Pair<>(s.substring(0, i), s.substring(i + 1));
				}
				if (Character.isWhitespace(cp))
				{
					// Note the first whitespace character discovered. Skip any
					// subsequent whitespace characters — if they occur at the
					// end of a line, then they will be omitted.
					wsi = i;
					int sz = Character.charCount(cp);
					wlen = sz;
					while (true)
					{
						i += sz;
						if (i < lineLimit)
						{
							cp = s.codePointAt(i);
							if (!Character.isWhitespace(cp))
							{
								break;
							}
							sz = Character.charCount(cp);
							wlen += sz;
						}
					}
				}
				else
				{
					// Otherwise, just move on to the next character.
					i += Character.charCount(cp);
				}
			}
			if (wsi > 0)
			{
				// If any whitespace was discovered, then answer the next line
				// and the residue.
				return new Pair<>(s.substring(0, wsi), s.substring(wsi + wlen));
			}
			else
			{
				// If no whitespace was discovered, then we cannot honor the
				// character limit strictly. Look for the next whitespace
				// character and terminate the line there.
				final int wideLimit = s.length();
				outer:
				while (i < wideLimit)
				{
					int cp = s.codePointAt(i);
					if (Character.isWhitespace(i))
					{
						wsi = i;
						int sz = Character.charCount(cp);
						wlen = sz;
						while (true)
						{
							i += sz;
							if (i < wideLimit)
							{
								cp = s.codePointAt(i);
								if (!Character.isWhitespace(cp))
								{
									break outer;
								}
								sz = Character.charCount(cp);
								wlen += sz;
							}
						}
					}
					i += Character.charCount(cp);
				}
				if (wsi == 0)
				{
					// If no whitespace characters were ever discovered and the
					// limit was exceeded, then answer the entire string as the
					// line and empty residue.
					assert wlen == 0;
					return new Pair<>(s, "");
				}
				return new Pair<>(s.substring(0, wsi), s.substring(wsi + wlen));
			}
		}

		/**
		 * Emit a block comment. A best effort will be made to keep the lines of
		 * the block comment within the specified {@link #charactersPerLine
		 * limit}, accounting for indentation and comment overhead.
		 *
		 * @param comment
		 *        The block comment.
		 * @throws IOException
		 *         If emission fails.
		 */
		@SuppressWarnings("WeakerAccess")
		public void blockComment (final String comment) throws IOException
		{
			final int limit =
				max(1, charactersPerLine - 4 * indentationLevel - 3);
			String residue = comment;
			while (!residue.isEmpty())
			{
				final Pair<String, String> pair = extractLine(residue, limit);
				final String line = pair.first();
				indent();
				endOfLineComment(line);
				residue = pair.second();
			}
		}

		/**
		 * Is the alleged identifier actually a {@code dot} keyword?
		 *
		 * @param id
		 *        The would-be identifier.
		 * @return {@code true} if the would-be identifier is actually a
		 *         {@code dot} keyword, {@code false} otherwise.
		 */
		private boolean isKeyword (final String id)
		{
			return keywords.contains(id);
		}

		/**
		 * Emit an appropriately escaped variant of the proposed identifier.
		 *
		 * @param proposedId
		 *        The identifier.
		 * @throws IOException
		 *         If emission fails.
		 */
		void identifier (final String proposedId) throws IOException
		{
			final String id;
			if (isKeyword(proposedId))
			{
				// If the proposed identifier is a keyword, then it needs to be
				// quoted before it can be used as an identifier.
				id = "\"" + proposedId + "\"";
			}
			else if (tokenPattern.matcher(proposedId).matches())
			{
				// The proposed identifier comprises only alphanumerics, so it
				// doesn't require any escaping.
				id = proposedId;
			}
			else if (proposedId.startsWith("<"))
			{
				// The proposed identifier is HTML-like, so escape it inside
				// angle brackets.
				id = "<" + proposedId + ">";
			}
			else {
				// The proposed identifier contains problematic characters, so
				// it must be appropriately escaped.
				id = "\"" + proposedId.codePoints()
					.mapToObj(cp ->
					{
						final String s;
						switch (cp)
						{
							case '\"':
								s = "\\\"";
								break;
							case '\n':
								s = "\\n";
								break;
							default:
								s = new String(Character.toChars(cp));
						}
						return s;
					})
					.collect(Collectors.joining()) + "\"";
			}
			emit(id);
		}

		/**
		 * Emit a simple attribute statement.
		 *
		 * @param lhs
		 *        The assignment target.
		 * @param rhs
		 *        The value to bind to the assignment target.
		 * @throws IOException
		 *         If emission fails.
		 */
		public void attribute (final String lhs, final String rhs)
		throws IOException
		{
			indent();
			identifier(lhs);
			emit(" = ");
			identifier(rhs);
			linefeed();
		}
	}

	/**
	 * The prebuilt {@link AttributeWriter} for dependency injection.
	 */
	@InnerAccess final AttributeWriter attributeWriter = new AttributeWriter();

	/**
	 * {@code DefaultAttributeBlockType} represents the scope of a default
	 * attributes block. The three supported scopes are {@link #GRAPH}, {@link
	 * #NODE}, and {@link #EDGE}. The {@link #name() names} of the enumeration
	 * values are chosen to match {@code dot} keywords and must not be changed.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	public enum DefaultAttributeBlockType
	{
		/** Graph scope. */
		GRAPH,

		/** Node scope. */
		NODE,

		/** Edge scope. */
		EDGE
	}

	/**
	 * {@code CompassPoint} represents one of the compass points allowed for
	 * edge attachments to node ports. The {@link #name() names} of the
	 * enumeration values are chosen to match {@code dot} keywords and must not
	 * be changed.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	public enum CompassPoint
	{
		/** Northwest. */
		NW,

		/** North. */
		N,

		/** Northeast. */
		NE,

		/** West. */
		W,

		/** Center. */
		C,

		/** East. */
		E,

		/** Southwest. */
		SW,

		/** South. */
		S,

		/** Southeast. */
		SE
	}

	/**
	 * {@code DecoratedNode} represents a decorated node that includes an
	 * optional port and an optional {@link CompassPoint}. It exists only for
	 * edge specification, as {@code dot} does not permit decorated nodes to
	 * appear on their own.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	public static final class DecoratedNode
	{
		/** The name of the node. */
		final String name;

		/** The name of the port, if any. */
		final @Nullable String port;

		/** The {@link CompassPoint}, if any. */
		final @Nullable CompassPoint compassPoint;

		/**
		 * Construct a new {@code DecoratedNode}.
		 *
		 * @param name
		 *        The name of the node.
		 * @param port
		 *        The name of the port.
		 * @param compassPoint
		 *        The {@link CompassPoint}, if any.
		 */
		@InnerAccess DecoratedNode (
			final String name,
			final @Nullable String port,
			final @Nullable CompassPoint compassPoint)
		{
			this.name = name;
			this.port = port;
			this.compassPoint = compassPoint;
		}
	}

	/**
	 * Answer a {@link DecoratedNode} suitable for complex edge specification.
	 *
	 * @param name
	 *        The name of the node.
	 * @return The requested node.
	 */
	public static DecoratedNode node (final String name)
	{
		return new DecoratedNode(name, null, null);
	}

	/**
	 * Answer a {@link DecoratedNode} suitable for complex edge specification.
	 *
	 * @param name
	 *        The name of the node.
	 * @param port
	 *        The name of the port.
	 * @return The requested node.
	 */
	public static DecoratedNode node (final String name, final String port)
	{
		return new DecoratedNode(name, port, null);
	}

	/**
	 * Answer a {@link DecoratedNode} suitable for complex edge specification.
	 *
	 * @param name
	 *        The name of the node.
	 * @param port
	 *        The name of the port.
	 * @param compassPoint
	 *        The {@link CompassPoint}.
	 * @return The requested node.
	 */
	public static DecoratedNode node (
		final String name,
		final String port,
		final CompassPoint compassPoint)
	{
		return new DecoratedNode(name, port, compassPoint);
	}

	/**
	 * A {@code GraphWriter} provides the capability of writing entire graphs,
	 * and as such is able to emit {@linkplain #defaultAttributeBlock(
	 * DefaultAttributeBlockType, CheckedConsumer) default attribute blocks},
	 * {@linkplain #node(String, CheckedConsumer) nodes}, and {@linkplain
	 * #edge(String, String, CheckedConsumer) edges}.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	public final class GraphWriter
	extends AttributeWriter
	{
		/**
		 * Emit an appropriately indented attribute block.
		 *
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to emit the content of
		 * 	      the block.
		 * @throws IOException
		 * 	       If emission fails.
		 */
		private void attributeBlock (
			final CheckedConsumer<AttributeWriter> block)
		throws IOException
		{
			indent();
			emit("[\n");
			increaseIndent(attributeWriter, block);
			indent();
			emit("]\n");
		}

		/**
		 * Emit an appropriately indented default attribute block.
		 *
		 * @param type
		 * 	      The {@link DefaultAttributeBlockType} of the default attribute
		 * 	      block.
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to emit the content of
		 * 	      the block.
		 * @throws IOException
		 * 	       If emission fails.
		 */
		public void defaultAttributeBlock (
			final DefaultAttributeBlockType type,
			final CheckedConsumer<AttributeWriter> block)
		throws IOException
		{
			indent();
			emit(type.name().toLowerCase());
			linefeed();
			attributeBlock(block);
		}

		/**
		 * Write an appropriately indented anonymous subgraph block.
		 *
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to emit the content of
		 * 	      the subgraph.
		 * @throws IOException
		 *         If emission fails.
		 */
		@SuppressWarnings("WeakerAccess")
		public void subgraph (final CheckedConsumer<GraphWriter> block)
		throws IOException
		{
			indent();
			emit("{\n");
			increaseIndent(graphWriter, block);
			indent();
			emit("}\n");
		}

		/**
		 * Write an appropriately indented named subgraph block.
		 *
		 * @param subgraphName
		 *        The name of the subgraph.
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to emit the content of
		 * 	      the subgraph.
		 * @throws IOException
		 *         If emission fails.
		 */
		@SuppressWarnings("unused")
		public void subgraph (
			final String subgraphName,
			final CheckedConsumer<GraphWriter> block)
		throws IOException
		{
			indent();
			emit("subgraph ");
			identifier(subgraphName);
			linefeed();
			subgraph(block);
		}

		/**
		 * Emit a node with attributes.
		 *
		 * @param nodeName
		 * 	      The identifier of the node.
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to generate the node's
		 * 	      attributes.
		 * @throws IOException
		 * 	       If emission fails.
		 */
		public void node (
			final String nodeName,
			final @Nullable CheckedConsumer<AttributeWriter> block)
		throws IOException
		{
			indent();
			identifier(nodeName);
			linefeed();
			if (block != null)
			{
				attributeBlock(block);
			}
		}

		/**
		 * Emit the appropriate edge operator.
		 *
		 * @throws IOException
		 *         If emission fails.
		 */
		private void edgeOperator () throws IOException
		{
			emit(isDirected ? " -> " : " -- ");
		}

		/**
		 * Emit an edge with attributes.
		 *
		 * @param source
		 *        The identifier of the source node.
		 * @param target
		 *        The identifier of the target node.
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to generate the edge's
		 * 	      attributes.
		 * @throws IOException
		 *         If emission fails.
		 */
		public void edge (
			final String source,
			final String target,
			final @Nullable CheckedConsumer<AttributeWriter> block)
		throws IOException
		{
			indent();
			identifier(source);
			edgeOperator();
			identifier(target);
			linefeed();
			if (block != null)
			{
				attributeBlock(block);
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 *        The source {@linkplain DecoratedNode node}.
		 * @param target
		 *        The target {@linkplain DecoratedNode node}.
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to generate the edge's
		 * 	      attributes.
		 * @throws IOException
		 *         If emission fails.
		 */
		public void edge (
			final DecoratedNode source,
			final DecoratedNode target,
			final @Nullable CheckedConsumer<AttributeWriter> block)
		throws IOException
		{
			indent();
			identifier(source.name);
			if (source.port != null)
			{
				emit(":");
				identifier(source.port);
			}
			if (source.compassPoint != null)
			{
				emit(":");
				emit(source.compassPoint.name().toLowerCase());
			}
			edgeOperator();
			identifier(target.name);
			if (target.port != null)
			{
				emit(":");
				identifier(target.port);
			}
			if (target.compassPoint != null)
			{
				emit(":");
				emit(target.compassPoint.name().toLowerCase());
			}
			linefeed();
			if (block != null)
			{
				attributeBlock(block);
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 * 	      The {@link CheckedConsumer} to apply to generate the source
		 * 	      subgraph.
		 * @param target
		 *        The identifier of the target node.
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to generate the edge's
		 * 	      attributes.
		 * @throws IOException
		 *         If emission fails.
		 */
		public void edge (
			final CheckedConsumer<GraphWriter> source,
			final String target,
			final @Nullable CheckedConsumer<AttributeWriter> block)
		throws IOException
		{
			subgraph(source);
			edgeOperator();
			identifier(target);
			linefeed();
			if (block != null)
			{
				attributeBlock(block);
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 * 	      The {@link CheckedConsumer} to apply to generate the source
		 * 	      subgraph.
		 * @param target
		 *        The target {@linkplain DecoratedNode node}.
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to generate the edge's
		 * 	      attributes.
		 * @throws IOException
		 *         If emission fails.
		 */
		public void edge (
			final CheckedConsumer<GraphWriter> source,
			final DecoratedNode target,
			final @Nullable CheckedConsumer<AttributeWriter> block)
		throws IOException
		{
			subgraph(source);
			edgeOperator();
			identifier(target.name);
			if (target.port != null)
			{
				emit(":");
				identifier(target.port);
			}
			if (target.compassPoint != null)
			{
				emit(":");
				emit(target.compassPoint.name().toLowerCase());
			}
			linefeed();
			if (block != null)
			{
				attributeBlock(block);
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 *        The identifier of the source node.
		 * @param target
		 * 	      The {@link CheckedConsumer} to apply to generate the target
		 * 	      subgraph.
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to generate the edge's
		 * 	      attributes.
		 * @throws IOException
		 *         If emission fails.
		 */
		public void edge (
			final String source,
			final CheckedConsumer<GraphWriter> target,
			final @Nullable CheckedConsumer<AttributeWriter> block)
		throws IOException
		{
			identifier(source);
			edgeOperator();
			linefeed();
			subgraph(target);
			linefeed();
			if (block != null)
			{
				attributeBlock(block);
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 *        The source {@linkplain DecoratedNode node}.
		 * @param target
		 * 	      The {@link CheckedConsumer} to apply to generate the target
		 * 	      subgraph.
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to generate the edge's
		 * 	      attributes.
		 * @throws IOException
		 *         If emission fails.
		 */
		public void edge (
			final DecoratedNode source,
			final CheckedConsumer<GraphWriter> target,
			final @Nullable CheckedConsumer<AttributeWriter> block)
		throws IOException
		{
			identifier(source.name);
			if (source.port != null)
			{
				emit(":");
				identifier(source.port);
			}
			if (source.compassPoint != null)
			{
				emit(":");
				emit(source.compassPoint.name().toLowerCase());
			}
			edgeOperator();
			linefeed();
			subgraph(target);
			linefeed();
			if (block != null)
			{
				attributeBlock(block);
			}
		}

		/**
		 * Emit an edge.
		 *
		 * @param source
		 * 	      The {@link CheckedConsumer} to apply to generate the source
		 * 	      subgraph.
		 * @param target
		 * 	      The {@link CheckedConsumer} to apply to generate the target
		 * 	      subgraph.
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to generate the edge's
		 * 	      attributes.
		 * @throws IOException
		 *         If emission fails.
		 */
		public void edge (
			final CheckedConsumer<GraphWriter> source,
			final CheckedConsumer<GraphWriter> target,
			final @Nullable CheckedConsumer<AttributeWriter> block)
		throws IOException
		{
			subgraph(source);
			edgeOperator();
			linefeed();
			subgraph(target);
			linefeed();
			if (block != null)
			{
				attributeBlock(block);
			}
		}

		/**
		 * Emit interleaved nodes and edges. If no nodes are specified, then
		 * do nothing.
		 *
		 * @param nodes
		 *        The nodes.
		 * @param block
		 * 	      The {@link CheckedConsumer} to apply to generate the edges'
		 * 	      attributes.
		 * @throws IOException
		 *         If emission fails.
		 */
		@SuppressWarnings({"unused", "unchecked"})
		public void interleaved (
			final List<Object> nodes,
			final @Nullable CheckedConsumer<AttributeWriter> block)
		throws IOException
		{
			if (!nodes.isEmpty())
			{
				for (int i = 0, limit = nodes.size(); i < limit; i++)
				{
					final Object o = nodes.get(i);
					if (o instanceof String)
					{
						identifier((String) o);
					}
					else if (o instanceof DecoratedNode)
					{
						final DecoratedNode node = (DecoratedNode) o;
						identifier(node.name);
						if (node.port != null)
						{
							emit(":");
							identifier(node.port);
						}
						if (node.compassPoint != null)
						{
							emit(":");
							identifier(node.compassPoint.name().toLowerCase());
						}
					}
					else if (o instanceof CheckedConsumer)
					{
						if (i != 0)
						{
							linefeed();
						}
						subgraph((CheckedConsumer<GraphWriter>) o);
					}
					else
					{
						assert false
							: String.format(
								"allowed node types are String, DecoratedNode, "
								+ "and CheckedConsumer<GraphWriter>, but %s is "
								+ "none of these",
								o.getClass().getName());
						throw new RuntimeException();
					}
					if (i < limit - 1)
					{
						edgeOperator();
					}
				}
				linefeed();
				if (block != null)
				{
					attributeBlock(block);
				}
			}
		}
	}

	/**
	 * The prebuilt {@link GraphWriter} for dependency injection.
	 */
	@InnerAccess final GraphWriter graphWriter = new GraphWriter();

	/**
	 * Emit a block comment, such as a copyright banner or statement of
	 * purpose.
	 *
	 * @param comment
	 *        The comment.
	 * @throws IOException
	 *         If emission fails.
	 */
	public void blockComment (final String comment) throws IOException
	{
		graphWriter.blockComment(comment);
	}

	/**
	 * Emit an entire graph.
	 *
	 * @param block
	 * 	      The {@link CheckedConsumer} to apply to emit the content of the
	 * 	      graph.
	 * @throws IOException
	 *         If emission fails.
	 */
	public void graph (final CheckedConsumer<GraphWriter> block)
	throws IOException
	{
		final GraphWriter writer = graphWriter;
		writer.indent();
		writer.emit(isDirected ? "digraph " : "graph ");
		writer.identifier(name);
		writer.linefeed();
		writer.subgraph(block);
	}
}
