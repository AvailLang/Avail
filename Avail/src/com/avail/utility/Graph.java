/**
 * Graph.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.utility;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.avail.annotations.InnerAccess;
import com.avail.utility.Graph;

/**
 * A {@code Graph} is an unordered collection of vertices, along with the
 * successor-predecessor relationships between them.  From the Graph's
 * viewpoint, the vertices are merely mementos that support {@link
 * #equals(Object)} and {@link #hashCode()}.  Edges are not explicit
 * represented, but instead are a consequence of how the {@link #outEdges} and
 * {@linkplain #inEdges} are populated.
 *
 * <p>
 * {@link Graph} is not synchronized.
 * </p>
 *
 * @param <Vertex> The vertex type with which to parameterize the Graph.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class Graph <Vertex>
{
	/**
	 * A {@link GraphPreconditionFailure} is thrown whenever a precondition of
	 * a graph manipulation operation does not hold.  The preconditions are
	 * described in the JavaDoc comment for each operation.
	 */
	public final static class GraphPreconditionFailure extends RuntimeException
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = -4084330590821139287L;

		/**
		 * Construct a new {@link GraphPreconditionFailure} with the given
		 * message.
		 *
		 * @param message The message describing the specific problem.
		 */
		@InnerAccess GraphPreconditionFailure (final String message)
		{
			super(message);
		}
	}

	/**
	 * Check that the condition is true, otherwise throw a {@link
	 * GraphPreconditionFailure} with the given message.
	 *
	 * @param condition The condition that should be true.
	 * @param message A description of the failed precondition.
	 * @throws GraphPreconditionFailure If the condition was false.
	 */
	private final void ensure (final boolean condition, final String message)
	throws GraphPreconditionFailure
	{
		if (!condition)
		{
			throw new GraphPreconditionFailure(message);
		}
	}

	/**
	 * A map from each vertex of the {@link Graph} to its set of successor
	 * vertices within the graph.  Each such set is unordered.
	 *
	 * <p>
	 * This mapping is maintained in lock-step with the {@link #inEdges}, which
	 * is the reverse relationship.
	 * </p>
	 */
	private final Map<Vertex, Set<Vertex>> outEdges = new HashMap<>();

	/**
	 * A map from each vertex of the {@link Graph} to its set of predecessor
	 * vertices within the graph.  Each such set is unordered.
	 *
	 * <p>
	 * This mapping is maintained in lock-step with the {@link #outEdges}, which
	 * is the reverse relationship.
	 * </p>
	 */
	private final Map<Vertex, Set<Vertex>> inEdges = new HashMap<>();

	/**
	 * Add a vertex to the graph.  Fail if the vertex is already present in the
	 * graph.  The vertex initially has no edges within this graph.
	 *
	 * @param vertex The vertex to add to the graph.
	 */
	public void addVertex (final Vertex vertex)
	{
		ensure(!outEdges.containsKey(vertex), "vertex is already in graph");
		outEdges.put(vertex, new HashSet<Vertex>());
		inEdges.put(vertex, new HashSet<Vertex>());
	}

	/**
	 * Add a collection of vertices to the graph.  Fail if any vertex is already
	 * present in the graph, or if it occurs multiple times in the given
	 * collection.  The vertices initially have no edges within this graph.
	 *
	 * @param vertices The vertices to add to the graph.
	 */
	public void addVertices (final Collection<Vertex> vertices)
	{
		for (final Vertex vertex : vertices)
		{
			ensure(!outEdges.containsKey(vertex), "vertex is already in graph");
			outEdges.put(vertex, new HashSet<Vertex>());
			inEdges.put(vertex, new HashSet<Vertex>());
		}
	}

	/**
	 * Add a vertex to the graph if it's not already present.  If the vertex is
	 * already present, do nothing.  If the vertex is added it initially has no
	 * edges within this graph.
	 *
	 * @param vertex The vertex to add to the graph if not already present.
	 */
	public void includeVertex (final Vertex vertex)
	{
		if (!outEdges.containsKey(vertex))
		{
			outEdges.put(vertex, new HashSet<Vertex>());
			inEdges.put(vertex, new HashSet<Vertex>());
		}
	}

	/**
	 * Remove a vertex from the graph, removing any connected edges.  Fail if
	 * the vertex is not present in the graph.
	 *
	 * @param vertex The vertex to attempt to remove to the graph.
	 */
	@SuppressWarnings("null")
	public void removeVertex (final Vertex vertex)
	{
		final Set<Vertex> outVertices = outEdges.get(vertex);
		ensure (outVertices != null, "source vertex is not present");
		final Set<Vertex> inVertices = inEdges.get(vertex);
		ensure (inVertices != null, "target vertex is not present");
		for (final Vertex successor : outVertices)
		{
			if (!successor.equals(vertex))
			{
				inEdges.get(successor).remove(vertex);
			}
		}
		for (final Vertex predecessor : inVertices)
		{
			if (!predecessor.equals(vertex))
			{
				outEdges.get(predecessor).remove(vertex);
			}
		}
		outEdges.remove(vertex);
		inEdges.remove(vertex);
	}


	/**
	 * Remove a vertex from the graph, removing any connected edges.  Do nothing
	 * if the vertex is not in the graph.
	 *
	 * @param vertex The vertex to exclude from the graph.
	 */
	public void excludeVertex (final Vertex vertex)
	{
		final Set<Vertex> outVertices = outEdges.get(vertex);
		if (outVertices != null)
		{
			final Set<Vertex> inVertices = inEdges.get(vertex);
			assert inVertices != null
				: "Inconsistent edge information in graph";
			for (final Vertex successor : outVertices)
			{
				if (!successor.equals(vertex))
				{
					inEdges.get(successor).remove(vertex);
				}
			}
			for (final Vertex predecessor : inVertices)
			{
				if (!predecessor.equals(vertex))
				{
					outEdges.get(predecessor).remove(vertex);
				}
			}
			outEdges.remove(vertex);
			inEdges.remove(vertex);
		}
	}

	/**
	 * Add an edge to the graph from the source vertex to the target vertex.
	 * Fail if either vertex is not present in the graph or if it already
	 * contains an edge from the source vertex to the target vertex.
	 *
	 * @param sourceVertex The source of the edge to attempt to add.
	 * @param targetVertex The target of the edge to attempt to add.
	 */
	@SuppressWarnings("null")
	public void addEdge (
		final Vertex sourceVertex,
		final Vertex targetVertex)
	{
		final Set<Vertex> sourceOutSet = outEdges.get(sourceVertex);
		ensure (sourceOutSet != null, "source vertex is not present");
		final Set<Vertex> targetInSet = inEdges.get(targetVertex);
		ensure (targetInSet != null, "target vertex is not present");
		ensure (
			!sourceOutSet.contains(targetVertex),
			"edge is already present");
		sourceOutSet.add(targetVertex);
		targetInSet.add(sourceVertex);
	}

	/**
	 * Add an edge to the graph from the source vertex to the target vertex.
	 * Fail if either vertex is not present in the graph.  If the graph already
	 * contains an edge from the source vertex to the target vertex then do
	 * nothing.
	 *
	 * @param sourceVertex The source of the edge to include.
	 * @param targetVertex The target of the edge to include.
	 */
	@SuppressWarnings("null")
	public void includeEdge (
		final Vertex sourceVertex,
		final Vertex targetVertex)
	{
		final Set<Vertex> sourceOutSet = outEdges.get(sourceVertex);
		ensure (sourceOutSet != null, "source vertex is not in graph");
		final Set<Vertex> targetInSet = inEdges.get(targetVertex);
		ensure (targetInSet != null, "target vertex is not in graph");
		sourceOutSet.add(targetVertex);
		targetInSet.add(sourceVertex);
	}

	/**
	 * Remove an edge from the graph, from the source vertex to the target
	 * vertex. Fail if either vertex is not present in the graph, or if there is
	 * no such edge in the graph.
	 *
	 * @param sourceVertex The source of the edge to remove.
	 * @param targetVertex The target of the edge to remove.
	 */
	@SuppressWarnings("null")
	public void removeEdge (
		final Vertex sourceVertex,
		final Vertex targetVertex)
	{
		final Set<Vertex> sourceOutSet = outEdges.get(sourceVertex);
		ensure (sourceOutSet != null, "source vertex is not in graph");
		final Set<Vertex> targetInSet = inEdges.get(targetVertex);
		ensure (targetInSet != null, "target vertex is not in graph");
		ensure (sourceOutSet.contains(targetVertex), "edge is not in graph");
		sourceOutSet.remove(targetVertex);
		targetInSet.remove(sourceVertex);
	}

	/**
	 * Remove an edge from the graph, from the source vertex to the target
	 * vertex. Fail if either vertex is not present in the graph.  If there is
	 * no such edge in the graph then do nothing.
	 *
	 * @param sourceVertex The source of the edge to exclude.
	 * @param targetVertex The target of the edge to exclude.
	 */
	@SuppressWarnings("null")
	public void excludeEdge (
		final Vertex sourceVertex,
		final Vertex targetVertex)
	{
		final Set<Vertex> sourceOutSet = outEdges.get(sourceVertex);
		ensure (sourceOutSet != null, "source vertex is not in graph");
		final Set<Vertex> targetInSet = inEdges.get(targetVertex);
		ensure (targetInSet != null, "target vertex is not in graph");
		sourceOutSet.remove(targetVertex);
		targetInSet.remove(sourceVertex);
	}

	/**
	 * Determine if the given vertex is in the graph.
	 *
	 * @param vertex The vertex to test for membership in the graph.
	 * @return Whether the vertex is in the graph.
	 */
	public boolean includesVertex (final Vertex vertex)
	{
		return outEdges.containsKey(vertex);
	}

	/**
	 * Determine if the graph contains an edge from the source vertex to the
	 * target vertex.  Fail if either vertex is not present in the graph.
	 *
	 * @param sourceVertex The vertex which is the source of the purported edge.
	 * @param targetVertex The vertex which is the target of the purported edge.
	 * @return Whether the graph contains the specified edge.
	 */
	@SuppressWarnings("null")
	public boolean includesEdge (
		final Vertex sourceVertex,
		final Vertex targetVertex)
	{
		final Set<Vertex> sourceOutSet = outEdges.get(sourceVertex);
		ensure (sourceOutSet != null, "source vertex is not in graph");
		final Set<Vertex> targetInSet = inEdges.get(targetVertex);
		ensure (targetInSet != null, "target vertex is not in graph");
		return sourceOutSet.contains(targetVertex);
	}

	/**
	 * Answer the number of edges in the graph.
	 *
	 * @return The vertex count of the graph.
	 */
	public int size ()
	{
		return outEdges.size();
	}
}
