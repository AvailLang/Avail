/**
 * Multigraph.java
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

import java.util.*;
import com.avail.utility.Multigraph;
import com.avail.utility.Multigraph.Edge;

/**
 * This is an implementation of a directed multigraph.  A graph consists of a
 * collection of vertices connected by (directed) edges.  A vertex v1 may be
 * connected to another vertex v2 by more than one edge, which makes this a
 * multigraph implementation.  However, note that v1 and v2 can not be multiply
 * connected by the <em>same</em> edge (i.e., two edges connecting v1 and v2
 * must not be {@link #equals(Object) equal} to each other.
 *
 * <p>
 * Multigraphs are not thread-safe, so they should be protected by some form of
 * explicit synchronization if they are to be accessed by multiple threads.
 * </p>
 *
 * @param <V> The type of vertices of the graph.
 * @param <E> The type of {@link Edge}s in the graph.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class Multigraph<V, E extends Edge<V>>
{
	/**
	 * An edge of a {@link Multigraph}.  The source and destination are final,
	 * to avoid corruption of the multigraph.
	 *
	 * <p>
	 * Note that by default Edges compare by identity, trivially allowing
	 * multiple edges with the same source and destination to be added to the
	 * graph.  Subclasses may supply different equality semantics.
	 * </p>
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 * @param <V>
	 */
	public static class Edge<V>
	{
		/**
		 * The vertex from which this edge originates.
		 */
		private final V source;

		/**
		 * The vertex to which this edge points.
		 */
		private final V destination;

		/**
		 * Construct a new {@link Edge}.
		 *
		 * @param source The source vertex.
		 * @param destination The destination vertex.
		 */
		public Edge (final V source, final V destination)
		{
			this.source = source;
			this.destination = destination;
		}

		/**
		 * Answer the vertex from which this edge originates.
		 *
		 * @return The source vertex.
		 */
		public final V source ()
		{
			return source;
		}

		/**
		 * Answer the destination vertex of this edge.
		 *
		 * @return The target vertex.
		 */
		public final V destination ()
		{
			return destination;
		}
	}

	/**
	 * A private structure holding a vertex and information about its local
	 * neighborhood within a graph.
	 */
	private final class VertexInGraph
	{
		/**
		 * The vertex that this {@code VertexInGraph} represents.
		 */
		final V vertex;

		/**
		 * A {@link Map} from each destination VertexInGraph to the (non-empty)
		 * {@link Set set} of {@link Edge edges} leading from the receiver
		 * vertex to that destination vertex.
		 *
		 * <p>
		 * Each such edge must also be present in the destination
		 * VertexInGraph's {@link #inbound} structure.
		 * </p>
		 */
		final Map<VertexInGraph, Set<E>> outbound =
			new HashMap<>();

		/**
		 * A {@link Map} from each source VertexInGraph to the (non-empty)
		 * {@link Set set} of {@link Edge edges} leading to the receiver
		 * vertex to that source vertex.
		 *
		 * <p>
		 * Each such edge must also be present in the source VertexInGraph's
		 * {@link #outbound} structure.
		 * </p>
		 */
		final Map<VertexInGraph, Set<E>> inbound =
			new HashMap<>();

		/**
		 * Construct a new {@link Multigraph.VertexInGraph}.
		 *
		 * @param vertex The actual vertex to wrap.
		 */
		public VertexInGraph (final V vertex)
		{
			this.vertex = vertex;
		}

		/**
		 * Remove all edges leading to or from the vertex.
		 */
		public void removeEdges ()
		{
			for (final VertexInGraph destination: outbound.keySet())
			{
				destination.inbound.remove(VertexInGraph.this);
			}
			for (final VertexInGraph source : inbound.keySet())
			{
				source.outbound.remove(VertexInGraph.this);
			}
			outbound.clear();
			inbound.clear();
		}
	}

	/**
	 * A mapping from each vertex to its {@link VertexInGraph}.
	 */
	private final Map<V, VertexInGraph> vertices =
		new HashMap<>();

	/**
	 * Construct a new {@link Multigraph} based on an existing one.  The two
	 * multigraphs will have the same vertices and edges.
	 *
	 * @param original The multigraph to copy.
	 */
	public Multigraph (final Multigraph<V, E> original)
	{
		// Add each vertex.
		for (final VertexInGraph originalVertex : original.vertices.values())
		{
			addVertex(originalVertex.vertex);
		}
		// Add each edge (once).
		for (final VertexInGraph originalVertex : original.vertices.values())
		{
			for (final Set<E> originalEdges : originalVertex.outbound.values())
			{
				for (final E originalEdge : originalEdges)
				{
					addEdge(originalEdge);
				}
			}
		}
	}

	/**
	 * Answer whether the specified vertex is in the graph.
	 *
	 * @param vertex The vertex to test for membership in the graph.
	 *
	 * @return Whether the vertex is present.
	 */
	public boolean containsVertex (final V vertex)
	{
		return vertices.containsKey(vertex);
	}

	/**
	 * Add a vertex to the graph.  It must not already be present.
	 *
	 * @param vertex The vertex to add.
	 */
	public void addVertex (final V vertex)
	{
		assert !containsVertex(vertex) : "Vertex is already present";
		vertices.put(vertex, new VertexInGraph(vertex));
	}

	/**
	 * Add a vertex to the graph.  Do nothing if the vertex is already present.
	 * Answer whether the graph was changed (i.e., whether the vertex was not
	 * already present).
	 *
	 * @param vertex The vertex to include.
	 * @return Whether the graph changed.
	 */
	public boolean includeVertex (final V vertex)
	{
		if (!containsVertex(vertex))
		{
			vertices.put(vertex, new VertexInGraph(vertex));
			return true;
		}
		return false;
	}

	/**
	 * Remove a vertex from the graph.  The vertex must be present.  Remove all
	 * edges connected to or from this vertex.
	 *
	 * @param vertex The vertex to remove.
	 */
	public void removeVertex (final V vertex)
	{
		assert containsVertex(vertex) : "The vertex to remove was not present";
		final VertexInGraph privateVertex = vertices.get(vertex);
		assert privateVertex != null;
		privateVertex.removeEdges();
		vertices.remove(vertex);
	}

	/**
	 * Remove a vertex from the graph if it was present.  If it was not present
	 * then do nothing.  Answer whether the graph was changed (i.e., whether
	 * the vertex was present).
	 *
	 * @param vertex The vertex to remove.
	 * @return Whether the graph changed.
	 */
	public boolean excludeVertex (final V vertex)
	{
		if (containsVertex(vertex))
		{
			removeVertex(vertex);
			return true;
		}
		return false;
	}

	/**
	 * Answer whether the specified edge is present in the graph.  The source
	 * and destination vertices of the edge <em>do not</em> have to be present
	 * in the graph; if not, the answer is simply {@code false}.
	 *
	 * @param edge The {@link Edge} to look up.
	 * @return Whether the edge is present in the graph.
	 */
	public boolean containsEdge (final E edge)
	{
		final VertexInGraph sourceInGraph = vertices.get(edge.source());
		if (sourceInGraph != null)
		{
			final Set<E> edgeSet = sourceInGraph.outbound.get(edge.destination());
			if (edgeSet != null)
			{
				return edgeSet.contains(edge);
			}
		}
		return false;
	}

	/**
	 * Add an edge to the graph.  The edge (or an equal one) must not already be
	 * present in the graph.  The edge's source and destination vertices must
	 * already be present in the graph.
	 *
	 * @param edge The edge to add.
	 */
	public void addEdge (final E edge)
	{
		final V source = edge.source();
		final V destination = edge.destination();
		final VertexInGraph privateSource = vertices.get(source);
		final VertexInGraph privateDestination = vertices.get(destination);
		assert privateSource != null : "source of edge not in graph";
		assert privateDestination != null : "destination of edge not in graph";
		Set<E> sourceOut = privateSource.outbound.get(privateDestination);
		if (sourceOut == null)
		{
			sourceOut = new HashSet<E>(1);
			privateSource.outbound.put(privateDestination, sourceOut);
		}
		assert !sourceOut.contains(edge) : "Edge is already present";
		sourceOut.add(edge);
		Set<E> destinationIn = privateDestination.inbound.get(privateSource);
		if (destinationIn == null)
		{
			destinationIn = new HashSet<E>(1);
			privateDestination.inbound.put(privateSource, destinationIn);
		}
		assert !destinationIn.contains(edge); // Consistency check
		destinationIn.add(edge);
	}

	/**
	 * Include an edge in the graph.  If the edge (or an {@linkplain
	 * #equals(Object) equal} one) is already present, do nothing.  The edge's
	 * source and destination vertices must already be present in the graph.
	 * Answer whether the graph changed (i.e., if this edge was not already
	 * present in the graph).
	 *
	 * @param edge The edge to include.
	 * @return Whether the graph changed.
	 */
	public boolean includeEdge (final E edge)
	{
		if (!containsEdge(edge))
		{
			addEdge(edge);
			return true;
		}
		return false;
	}

	/**
	 * Remove an edge from the graph.  The edge must be present in the graph.
	 *
	 * @param edge The edge to remove.
	 */
	public void removeEdge (final E edge)
	{
		assert containsEdge(edge) : "The edge to remove was not present";
		final VertexInGraph sourceInGraph = vertices.get(edge.source());
		final Set<E> edgeSet = sourceInGraph.outbound.get(edge.destination());
		edgeSet.remove(edge);
		if (edgeSet.isEmpty())
		{
			sourceInGraph.outbound.remove(edge.destination());
		}
	}

	/**
	 * Remove an edge from the graph, doing nothing if it's not present.  Answer
	 * whether the graph was changed (i.e., if the edge was present).
	 *
	 * @param edge The edge to exclude.
	 * @return Whether the graph was changed.
	 */
	public boolean excludeEdge (final E edge)
	{
		if (containsEdge(edge))
		{
			removeEdge(edge);
			return true;
		}
		return false;
	}

	/**
	 * Answer an unmodifiable set of edges (if any) between the specified
	 * vertices.  Note that subsequent changes to the graph may affect this set.
	 *
	 * @param source The source vertex.
	 * @param destination The destination vertex.
	 * @return The edges that connect the source and destination vertices.
	 */
	public Set<E> edgesFromTo (final V source, final V destination)
	{
		final VertexInGraph sourceInGraph = vertices.get(source);
		if (sourceInGraph != null
			&& sourceInGraph.outbound.containsKey(destination))
		{
			return Collections.unmodifiableSet(
				sourceInGraph.outbound.get(destination));
		}
		return Collections.emptySet();
	}

	/**
	 * Answer an unmodifiable set of edges (if any) leading from the specified
	 * source vertex.  Note that subsequent changes to the graph may affect this
	 * set.
	 *
	 * @param source The source vertex.
	 * @return All edges that lead from the source vertex.
	 */
	public Set<E> edgesFrom (final V source)
	{
		final VertexInGraph sourceInGraph = vertices.get(source);
		if (sourceInGraph != null
			&& !sourceInGraph.outbound.isEmpty())
		{
			if (sourceInGraph.outbound.size() == 1)
			{
				return Collections.unmodifiableSet(
					sourceInGraph.outbound.values().iterator().next());
			}
			final Set<E> edges = new HashSet<>(sourceInGraph.outbound.size());
			for (final Set<E> submap : sourceInGraph.outbound.values())
			{
				edges.addAll(submap);
			}
			return Collections.unmodifiableSet(edges);
		}
		return Collections.emptySet();
	}

	/**
	 * Answer an unmodifiable set of edges (if any) leading to the specified
	 * destination vertex.  Note that subsequent changes to the graph may affect
	 * this set.
	 *
	 * @param destination The destination vertex.
	 * @return All edges that lead to the destination vertex.
	 */
	public Set<E> edgesTo (final V destination)
	{
		final VertexInGraph destinationInGraph = vertices.get(destination);
		if (destinationInGraph != null
			&& !destinationInGraph.inbound.isEmpty())
		{
			if (destinationInGraph.inbound.size() == 1)
			{
				return Collections.unmodifiableSet(
					destinationInGraph.inbound.values().iterator().next());
			}
			final Set<E> edges = new HashSet<>(
				destinationInGraph.inbound.size());
			for (final Set<E> submap : destinationInGraph.inbound.values())
			{
				edges.addAll(submap);
			}
			return Collections.unmodifiableSet(edges);
		}
		return Collections.emptySet();
	}

	/**
	 * Answer the multigraph's {@link Set} of vertices.  The set may not be
	 * modified.  Modifications to the graph may make this set invalid.
	 *
	 * @return The set of vertices.
	 */
	Set<V> vertices ()
	{
		return Collections.unmodifiableSet(vertices.keySet());
	}

	/**
	 * Answer an immutable {@link List} of all edges of this graph.
	 *
	 * @return The graph's edges.
	 */
	List<E> edges ()
	{
		final List<E> edges = new ArrayList<>(vertices.size());
		for (final VertexInGraph vertexInGraph : vertices.values())
		{
			for (final Set<E> edgeSet : vertexInGraph.outbound.values())
			{
				edges.addAll(edgeSet);
			}
		}
		return edges;
	}
}
