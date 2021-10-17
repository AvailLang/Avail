/*
 * Multigraph.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package avail.utility

import avail.utility.Multigraph.Edge

/**
 * This is an implementation of a directed multigraph. A graph consists of a
 * collection of vertices connected by (directed) edges. A vertex v1 may be
 * connected to another vertex v2 by more than one edge, which makes this a
 * multigraph implementation. However, note that v1 and v2 can not be multiply
 * connected by the *same* edge (i.e., two edges connecting v1 and v2 must not
 * be [equal][equals] to each other).
 *
 * Multigraphs are not thread-safe, so they should be protected by some form of
 * explicit synchronization if they are to be accessed by multiple threads.
 *
 * @param V
 *   The type of vertices of the graph.
 * @param E
 *   The type of [Edge]s in the graph.
 * @author Mark van Gulik <mark@availlang.org>
 *
 * @constructor
 *
 * Construct a new `Multigraph` based on an existing one. The two
 * multigraphs will have the same vertices and edges.
 *
 * @param original
 *   The multigraph to copy.
 */
@Suppress("unused")
class Multigraph<V, E : Edge<V>> constructor (original: Multigraph<V, E>)
{
	init
	{
		// Add each vertex.
		original.vertices.values.forEach { originalVertex ->
			addVertex(originalVertex.vertex)
		}
		// Add each edge (once).
		original.vertices.values.forEach { originalVertex ->
			originalVertex.outbound.values.forEach { originalEdges ->
				originalEdges.forEach { originalEdge ->
					addEdge(originalEdge)
				}
			}
		}
	}

	/**
	 * An edge of a [Multigraph]. The source and destination are final, to avoid
	 * corruption of the multigraph.
	 *
	 * Note that by default Edges compare by identity, trivially allowing
	 * multiple edges with the same source and destination to be added to the
	 * graph. Subclasses may supply different equality semantics.
	 *
	 * @param V
	 *   The type of vertices of the graph.
	 * @property source
	 *   The vertex from which this edge originates.
	 * @property destination
	 *   The vertex to which this edge points.
	 * @author Todd L Smith <todd@availlang.org>
	 *
	 * @constructor
	 *
	 * Construct a new edge.
	 *
	 * @param V
	 *   The type of vertices of the graph.
	 * @param source
	 *   The source vertex.
	 * @param destination
	 *   The destination vertex.
	 */
	class Edge<V> constructor (
		val source: V,
		val destination: V)

	/**
	 * A private structure holding a vertex and information about its local
	 * neighborhood within a graph.
	 *
	 * @property vertex
	 *   The vertex that this `VertexInGraph` represents.
	 *
	 * @constructor
	 *
	 * Construct a new vertex.
	 *
	 * @param vertex
	 *   The actual vertex to wrap.
	 */
	private inner class VertexInGraph internal constructor (val vertex: V)
	{
		/**
		 * A [Map] from each destination VertexInGraph to the (non-empty)
		 * [set][Set] of [edges][Edge] leading from the receiver vertex to that
		 * destination vertex.
		 *
		 * Each such edge must also be present in the destination
		 * `VertexInGraph`'s [inbound] structure.
		 */
		val outbound = mutableMapOf<VertexInGraph, MutableSet<E>>()

		/**
		 * A [Map] from each source VertexInGraph to the (non-empty) [set][Set]
		 * of [edges][Edge] leading to the receiver vertex from that source
		 * vertex.
		 *
		 * Each such edge must also be present in the source `VertexInGraph`'s
		 * [outbound] structure.
		 */
		val inbound = mutableMapOf<VertexInGraph, MutableSet<E>>()

		/**
		 * Remove all edges leading to or from the vertex.
		 */
		fun removeEdges ()
		{
			outbound.keys.forEach { destination ->
				destination.inbound.remove(this@VertexInGraph)
			}
			inbound.keys.forEach { source ->
				source.outbound.remove(this@VertexInGraph)
			}
			outbound.clear()
			inbound.clear()
		}
	}

	/**
	 * A mapping from each vertex to its [VertexInGraph].
	 */
	private val vertices = mutableMapOf<V, VertexInGraph>()

	/**
	 * Answer whether the specified vertex is in the graph.
	 *
	 * @param vertex
	 *   The vertex to test for membership in the graph.
	 * @return
	 *   Whether the vertex is present.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun containsVertex (vertex: V) = vertices.containsKey(vertex)

	/**
	 * Add a vertex to the graph. It must not already be present.
	 *
	 * @param vertex
	 *   The vertex to add.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun addVertex (vertex: V)
	{
		assert(!containsVertex(vertex)) { "Vertex is already present" }
		vertices[vertex] = VertexInGraph(vertex)
	}

	/**
	 * Add a vertex to the graph. Do nothing if the vertex is already present.
	 * Answer whether the graph was changed (i.e., whether the vertex was not
	 * already present).
	 *
	 * @param vertex
	 *   The vertex to include.
	 * @return
	 *   Whether the graph changed.
	 */
	fun includeVertex (vertex: V): Boolean
	{
		if (!containsVertex(vertex))
		{
			vertices[vertex] = VertexInGraph(vertex)
			return true
		}
		return false
	}

	/**
	 * Remove a vertex from the graph. The vertex must be present. Remove all
	 * edges connected to or from this vertex.
	 *
	 * @param vertex
	 *   The vertex to remove.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun removeVertex (vertex: V)
	{
		assert(containsVertex(vertex)) {
			"The vertex to remove was not present"
		}
		val privateVertex = vertices[vertex]!!
		privateVertex.removeEdges()
		vertices.remove(vertex)
	}

	/**
	 * Remove a vertex from the graph if it was present. If it was not present
	 * then do nothing. Answer whether the graph was changed (i.e., whether
	 * the vertex was present).
	 *
	 * @param vertex
	 *   The vertex to remove.
	 * @return
	 *   Whether the graph changed.
	 */
	fun excludeVertex (vertex: V): Boolean
	{
		if (containsVertex(vertex))
		{
			removeVertex(vertex)
			return true
		}
		return false
	}

	/**
	 * Answer whether the specified edge is present in the graph. The source and
	 * destination vertices of the edge *do not* have to be present in the
	 * graph; if not, the answer is simply `false`.
	 *
	 * @param edge
	 *   The [Edge] to look up.
	 * @return
	 *   Whether the edge is present in the graph.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun containsEdge (edge: E): Boolean
	{
		val sourceInGraph = vertices[edge.source]
		if (sourceInGraph !== null)
		{
			val edgeSet = sourceInGraph.outbound[vertices[edge.destination]]
			if (edgeSet !== null)
			{
				return edgeSet.contains(edge)
			}
		}
		return false
	}

	/**
	 * Add an edge to the graph. The edge (or an equal one) must not already be
	 * present in the graph. The edge's source and destination vertices must
	 * already be present in the graph.
	 *
	 * @param edge
	 *   The edge to add.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun addEdge (edge: E)
	{
		val source = edge.source
		val destination = edge.destination
		val privateSource = vertices[source]
		val privateDestination = vertices[destination]
		assert(privateSource !== null) { "source of edge not in graph" }
		assert(privateDestination !== null) {
			"destination of edge not in graph"
		}
		val sourceOut =
			privateSource!!.outbound.computeIfAbsent(privateDestination!!) {
				mutableSetOf()
			}
		assert(!sourceOut.contains(edge)) { "Edge is already present" }
		sourceOut.add(edge)
		val destinationIn =
			privateDestination.inbound.computeIfAbsent(privateSource) {
				mutableSetOf()
			}
		// Consistency check
		assert(!destinationIn.contains(edge))
		destinationIn.add(edge)
	}

	/**
	 * Include an edge in the graph. If the edge (or an [equal][equals] one) is
	 * already present, do nothing. The edge's source and destination vertices
	 * must already be present in the graph. Answer whether the graph changed
	 * (i.e., if this edge was not already present in the graph).
	 *
	 * @param edge
	 *   The edge to include.
	 * @return
	 *   Whether the graph changed.
	 */
	fun includeEdge (edge: E): Boolean
	{
		if (!containsEdge(edge))
		{
			addEdge(edge)
			return true
		}
		return false
	}

	/**
	 * Remove an edge from the graph. The edge must be present in the graph.
	 *
	 * @param edge
	 *   The edge to remove.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun removeEdge (edge: E)
	{
		assert(containsEdge(edge)) { "The edge to remove was not present" }
		val sourceInGraph = vertices[edge.source]
		val destInGraph = vertices[edge.destination]
		val forwardEdgeSet = sourceInGraph!!.outbound[destInGraph]
		forwardEdgeSet!!.remove(edge)
		if (forwardEdgeSet.isEmpty())
		{
			sourceInGraph.outbound.remove(destInGraph)
		}
		val reverseEdgeSet = destInGraph!!.inbound[sourceInGraph]
		reverseEdgeSet!!.remove(edge)
		if (reverseEdgeSet.isEmpty())
		{
			destInGraph.inbound.remove(sourceInGraph)
		}
	}

	/**
	 * Remove an edge from the graph, doing nothing if it's not present. Answer
	 * whether the graph was changed (i.e., if the edge was present).
	 *
	 * @param edge
	 *   The edge to exclude.
	 * @return
	 *   Whether the graph was changed.
	 */
	fun excludeEdge (edge: E): Boolean
	{
		if (containsEdge(edge))
		{
			removeEdge(edge)
			return true
		}
		return false
	}

	/**
	 * Answer an unmodifiable set of edges (if any) between the specified
	 * vertices. Note that subsequent changes to the graph may affect this set.
	 *
	 * @param source
	 *   The source vertex.
	 * @param destination
	 *   The destination vertex.
	 * @return
	 *   The edges that connect the source and destination vertices.
	 */
	fun edgesFromTo (source: V, destination: V): Set<E>
	{
		val sourceInGraph = vertices[source]
		val destInGraph = vertices[destination]
		return (
			if (sourceInGraph !== null
				&& destInGraph !== null
				&& sourceInGraph.outbound.containsKey(destInGraph))
			{
				sourceInGraph.outbound[destInGraph]!!.toMutableSet()
			}
			else emptySet())
	}

	/**
	 * Answer an unmodifiable set of edges (if any) leading from the specified
	 * source vertex. Note that subsequent changes to the graph may affect this
	 * set.
	 *
	 * @param source
	 *   The source vertex.
	 * @return
	 *   All edges that lead from the source vertex.
	 */
	fun edgesFrom (source: V): Set<E>
	{
		val sourceInGraph = vertices[source]
		if (sourceInGraph !== null && sourceInGraph.outbound.isNotEmpty())
		{
			if (sourceInGraph.outbound.size == 1)
			{
				return sourceInGraph.outbound.values.single().toSet()
			}
			val edges = mutableSetOf<E>()
			sourceInGraph.outbound.values.forEach { submap ->
				edges.addAll(submap)
			}
			return edges.toSet()
		}
		return emptySet()
	}

	/**
	 * Answer an unmodifiable set of edges (if any) leading to the specified
	 * destination vertex. Note that subsequent changes to the graph may affect
	 * this set.
	 *
	 * @param destination
	 *   The destination vertex.
	 * @return
	 *   All edges that lead to the destination vertex.
	 */
	fun edgesTo (destination: V): Set<E>
	{
		val destinationInGraph = vertices[destination]
		if (destinationInGraph !== null
			&& destinationInGraph.inbound.isNotEmpty())
		{
			if (destinationInGraph.inbound.size == 1)
			{
				return destinationInGraph.inbound.values.single().toSet()
			}
			val edges = mutableSetOf<E>()
			destinationInGraph.inbound.values.forEach { submap ->
				edges.addAll(submap)
			}
			return edges.toSet()
		}
		return emptySet()
	}

	/**
	 * Answer the multigraph's [Set] of vertices. The set may not be modified.
	 * Modifications to the graph may make this set invalid.
	 *
	 * @return
	 *   The set of vertices.
	 */
	fun vertices (): Set<V>
	{
		return vertices.keys
	}

	/**
	 * An immutable [List] of all edges of this graph.
	 */
	val edges: List<E>
		get()
		{
			val edges = mutableListOf<E>()
			vertices.values.forEach { vertexInGraph ->
				vertexInGraph.outbound.values.forEach { edgeSet ->
					edges.addAll(edgeSet)
				}
			}
			return edges
		}
}
