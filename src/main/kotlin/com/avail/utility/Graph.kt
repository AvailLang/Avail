/*
 * Graph.kt
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
package com.avail.utility

import com.avail.utility.evaluation.Combinator.recurse
import org.jetbrains.annotations.Contract
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Comparator
import java.util.Deque
import java.util.LinkedHashSet
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A `Graph` is an unordered collection of vertices, along with the
 * successor-predecessor relationships between them.  From the Graph's
 * viewpoint, the vertices are merely mementos that support [equals] and
 * [hashCode].  Edges are not explicitly represented, but instead are a
 * consequence of how the [outEdges] and [inEdges] are populated.
 *
 * [Graph] is not synchronized.
 *
 * @param Vertex
 *   The vertex type with which to parameterize the Graph.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class Graph<Vertex> constructor ()
{
	/**
	 * A map from each vertex of the [Graph] to its set of successor vertices
	 * within the graph. Each such set is unordered.
	 *
	 * This mapping is maintained in lock-step with the [inEdges], which is the
	 * reverse relationship.
	 */
	private val outEdges = mutableMapOf<Vertex, MutableSet<Vertex>>()

	/**
	 * A map from each vertex of the [Graph] to its set of predecessor vertices
	 * within the graph. Each such set is unordered.
	 *
	 * This mapping is maintained in lock-step with the [outEdges], which is the
	 * reverse relationship.
	 */
	private val inEdges = mutableMapOf<Vertex, MutableSet<Vertex>>()

	/**
	 * Construct a new graph with the same vertices and edges as the argument.
	 *
	 * @param graph
	 *   The graph to copy.
	 */
	constructor (graph: Graph<Vertex>) : this()
	{
		graph.outEdges.entries.associateTo(outEdges) {
			(k, v) -> k to v.toMutableSet()
		}
		graph.inEdges.entries.associateTo(inEdges) {
			(k, v) -> k to v.toMutableSet()
		}
	}

	/**
	 * A [GraphPreconditionFailure] is thrown whenever a precondition of a graph
	 * manipulation operation does not hold. The preconditions are described in
	 * the JavaDoc comment for each operation.
	 *
	 * @constructor
	 *
	 * Construct a new `GraphPreconditionFailure` with the given
	 * message.
	 *
	 * @param message
	 *   The message describing the specific problem.
	 */
	class GraphPreconditionFailure internal constructor (message: String?)
		: RuntimeException(message)

	/**
	 * Remove all edges and vertices from the graph.
	 */
	fun clear ()
	{
		outEdges.clear()
		inEdges.clear()
	}

	/**
	 * Add a vertex to the graph. Fail if the vertex is already present in the
	 * graph. The vertex initially has no edges within this graph.
	 *
	 * @param vertex
	 *   The vertex to add to the graph.
	 */
	fun addVertex (vertex: Vertex)
	{
		ensure(!outEdges.containsKey(vertex),"vertex is already in graph")
		outEdges[vertex] = mutableSetOf()
		inEdges[vertex] = mutableSetOf()
	}

	/**
	 * Add a collection of vertices to the graph. Fail if any vertex is already
	 * present in the graph, or if it occurs multiple times in the given
	 * collection. The vertices initially have no edges within this graph.
	 *
	 * @param vertices
	 *   The vertices to add to the graph.
	 */
	fun addVertices (vertices: Collection<Vertex>)
	{
		for (vertex in vertices)
		{
			ensure(
				!outEdges.containsKey(vertex),
				"vertex is already in graph"
			)
			outEdges[vertex] = mutableSetOf()
			inEdges[vertex] = mutableSetOf()
		}
	}

	/**
	 * Add a vertex to the graph if it's not already present. If the vertex is
	 * already present, do nothing. If the vertex is added it initially has no
	 * edges within this graph.
	 *
	 * @param vertex
	 *   The vertex to add to the graph if not already present.
	 */
	fun includeVertex (vertex: Vertex)
	{
		if (!outEdges.containsKey(vertex))
		{
			outEdges[vertex] = mutableSetOf()
			inEdges[vertex] = mutableSetOf()
		}
	}

	/**
	 * Remove a vertex from the graph, failing if it has any connected edges.
	 * Fail if the vertex is not present in the graph.
	 *
	 * @param vertex
	 *   The vertex to attempt to remove from the graph.
	 */
	@Suppress("unused")
	fun removeVertex (vertex: Vertex)
	{
		ensure(outEdges.containsKey(vertex), "vertex is not present")
		excludeVertex(vertex)
	}

	/**
	 * Remove a vertex from the graph, removing any connected edges. Do nothing
	 * if the vertex is not in the graph.
	 *
	 * @param vertex
	 *   The vertex to exclude from the graph.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun excludeVertex (vertex: Vertex)
	{
		val outVertices: Set<Vertex>? = outEdges[vertex]
		if (outVertices !== null)
		{
			val inVertices: Set<Vertex> = inEdges[vertex]
				?: error("Inconsistent edge information in graph")
			ensure(outVertices.isEmpty(), "vertex has outbound edges")
			ensure(inVertices.isEmpty(), "vertex has inbound edges")
			outEdges.remove(vertex)
			inEdges.remove(vertex)
		}
	}

	/**
	 * If the given vertex is present in the graph, remove its incoming and
	 * outgoing edges, then the vertex itself. If the given vertex is not in the
	 * graph, do nothing.
	 *
	 * @param vertex
	 *   The vertex to excise from the graph, if present.
	 */
	fun exciseVertex (vertex: Vertex)
	{
		excludeEdgesFrom(vertex)
		excludeEdgesTo(vertex)
		excludeVertex(vertex)
	}

	/**
	 * Determine if the given vertex is in the graph.
	 *
	 * @param vertex
	 *   The vertex to test for membership in the graph.
	 * @return
	 *   Whether the vertex is in the graph.
	 */
	fun includesVertex (vertex: Vertex): Boolean
	{
		return outEdges.containsKey(vertex)
	}

	/**
	 * Add an edge to the graph from the source vertex to the target vertex.
	 * Fail if either vertex is not present in the graph or if it already
	 * contains an edge from the source vertex to the target vertex.
	 *
	 * @param sourceVertex
	 *   The source of the edge to attempt to add.
	 * @param targetVertex
	 *   The target of the edge to attempt to add.
	 */
	fun addEdge (sourceVertex: Vertex, targetVertex: Vertex)
	{
		val sourceOutSet = outEdges[sourceVertex]
		notNull(sourceOutSet, "source vertex is not present")
		val targetInSet = inEdges[targetVertex]
		notNull(targetInSet, "target vertex is not present")
		ensure(
			!sourceOutSet!!.contains(targetVertex),
			"edge is already present"
		)
		sourceOutSet.add(targetVertex)
		targetInSet!!.add(sourceVertex)
	}

	/**
	 * Add an edge to the graph from the source vertex to the target vertex.
	 * Fail if either vertex is not present in the graph. If the graph already
	 * contains an edge from the source vertex to the target vertex then do
	 * nothing.
	 *
	 * @param sourceVertex
	 *   The source of the edge to include.
	 * @param targetVertex
	 *   The target of the edge to include.
	 */
	fun includeEdge (sourceVertex: Vertex, targetVertex: Vertex)
	{
		val sourceOutSet = outEdges[sourceVertex]
		notNull(sourceOutSet, "source vertex is not in graph")
		val targetInSet = inEdges[targetVertex]
		notNull(targetInSet,"target vertex is not in graph")
		sourceOutSet!!.add(targetVertex)
		targetInSet!!.add(sourceVertex)
	}

	/**
	 * Remove an edge from the graph, from the source vertex to the target
	 * vertex. Fail if either vertex is not present in the graph, or if there is
	 * no such edge in the graph.
	 *
	 * @param sourceVertex
	 *   The source of the edge to remove.
	 * @param targetVertex
	 *   The target of the edge to remove.
	 */
	fun removeEdge (sourceVertex: Vertex, targetVertex: Vertex)
	{
		val sourceOutSet = outEdges[sourceVertex]
		notNull(sourceOutSet, "source vertex is not in graph")
		val targetInSet = inEdges[targetVertex]
		notNull(targetInSet, "target vertex is not in graph")
		ensure(
			sourceOutSet!!.contains(targetVertex),
			"edge is not in graph"
		)
		sourceOutSet.remove(targetVertex)
		targetInSet!!.remove(sourceVertex)
	}

	/**
	 * Remove an edge from the graph, from the source vertex to the target
	 * vertex. Fail if either vertex is not present in the graph. If there is no
	 * such edge in the graph then do nothing.
	 *
	 * @param sourceVertex
	 *   The source of the edge to exclude.
	 * @param targetVertex
	 *   The target of the edge to exclude.
	 */
	fun excludeEdge (sourceVertex: Vertex, targetVertex: Vertex)
	{
		val sourceOutSet = outEdges[sourceVertex]
		notNull(sourceOutSet, "source vertex is not in graph")
		val targetInSet = inEdges[targetVertex]
		notNull(targetInSet, "target vertex is not in graph")
		sourceOutSet!!.remove(targetVertex)
		targetInSet!!.remove(sourceVertex)
	}

	/**
	 * Remove all edges from the graph which originate at the given vertex. Fail
	 * if the vertex is not present in the graph.
	 *
	 * @param sourceVertex
	 *   The source vertex of the edges to exclude.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun excludeEdgesFrom (sourceVertex: Vertex)
	{
		val sourceOutSet = outEdges[sourceVertex]
		notNull(sourceOutSet, "source vertex is not in graph")
		sourceOutSet!!.forEach { inEdges[it]!!.remove(sourceVertex) }
		sourceOutSet.clear()
	}

	/**
	 * Remove all edges from the graph which terminate at the given vertex. Fail
	 * if the vertex is not present in the graph.
	 *
	 * @param targetVertex
	 *   The target vertex of the edges to exclude.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun excludeEdgesTo (targetVertex: Vertex)
	{
		val targetInSet = inEdges[targetVertex]
		notNull(targetInSet, "target vertex is not in graph")
		targetInSet!!.forEach { outEdges[it]!!.remove(targetVertex) }
		targetInSet.clear()
	}

	/**
	 * Determine if the graph contains an edge from the source vertex to the
	 * target vertex. Fail if either vertex is not present in the graph.
	 *
	 * @param sourceVertex
	 *   The vertex which is the source of the purported edge.
	 * @param targetVertex
	 *   The vertex which is the target of the purported edge.
	 * @return
	 *   Whether the graph contains the specified edge.
	 */
	fun includesEdge (sourceVertex: Vertex, targetVertex: Vertex): Boolean
	{
		val sourceOutSet = outEdges[sourceVertex]
		notNull(sourceOutSet, "source vertex is not in graph")
		val targetInSet = inEdges[targetVertex]
		notNull(targetInSet, "target vertex is not in graph")
		return sourceOutSet!!.contains(targetVertex)
	}

	/**
	 * The number of edges in the graph.
	 */
	val size get () = outEdges.size

	/**
	 * `true` if the graph is empty, `false` otherwise.
	 */
	val isEmpty get () = outEdges.isEmpty()

	/**
	 * The graph's vertices.
	 */
	val vertices: Set<Vertex> get () = outEdges.keys.toSet()

	/**
	 * The vertex count of the graph.
	 */
	val vertexCount get () = outEdges.size

	/**
	 * Answer the set of successors of the specified vertex. Fail if the vertex
	 * is not present in the graph.
	 *
	 * @param vertex
	 *   The vertex for which to answer successors.
	 * @return
	 *   The successors of the vertex.
	 */
	fun successorsOf (vertex: Vertex): Set<Vertex>
	{
		ensure(
			outEdges.containsKey(vertex),
			"source vertex is not in graph"
		)
		return outEdges[vertex]!!.toSet()
	}

	/**
	 * Answer the set of predecessors of the specified vertex. Fail if the
	 * vertex is not present in the graph.
	 *
	 * @param vertex
	 *   The vertex for which to answer predecessors.
	 * @return
	 *   The predecessors of the vertex.
	 */
	fun predecessorsOf (vertex: Vertex): Set<Vertex>
	{
		ensure(
			inEdges.containsKey(vertex),
			"target vertex is not in graph"
		)
		return inEdges[vertex]!!.toSet()
	}

	/**
	 * The roots of the graph. These are the vertices with no incoming edges.
	 */
	val roots get () = inEdges.keys.filter { inEdges[it]!!.isEmpty() }.toSet()

	/**
	 * `true` iff the graph is cyclic, otherwise `false`.
	 */
	val isCyclic: Boolean
		get ()
		{
			val predecessorCountdowns = mutableMapOf<Vertex, Int>()
			val stack: Deque<Vertex> = ArrayDeque()
			inEdges.forEach { (vertex, value) ->
				val predecessorsSize = value.size
				if (predecessorsSize == 0)
				{
					// Seed it with 1, as though each root has an incoming edge.
					// Also stack the root as though that edge has been visited.
					predecessorCountdowns[vertex] = 1
					stack.add(vertex)
				}
				else
				{
					predecessorCountdowns[vertex] = predecessorsSize
				}
			}
			while (!stack.isEmpty())
			{
				val vertex = stack.removeLast()
				val countdown = predecessorCountdowns[vertex]!!
				if (countdown == 1)
				{
					predecessorCountdowns.remove(vertex)
					stack.addAll(outEdges[vertex]!!)
				}
				else
				{
					assert(countdown > 1)
					predecessorCountdowns[vertex] = countdown - 1
				}
			}
			// If anything is left in predecessorCountdowns, it was unreachable
			// from the roots (impossible by definition if acyclic), or it was
			// descended from a cycle.
			return predecessorCountdowns.isNotEmpty()
		}

	/**
	 * The first cycle in this cyclic graph, as a [List] of [vertices][Vertex]
	 * in the order in which they occur in the cycle.
	 *
	 * This is currently written with recursion, but pathologically deep
	 * module dependency graphs could overflow the Java stack.
	 */
	val firstCycle: List<Vertex>
		get ()
		{
			// Potentially expensive.
			assert(isCyclic)
			val stack = LinkedHashSet<Vertex>()
			val reached = mutableSetOf<Vertex>()
			var solution: MutableList<Vertex>? = null
			for (start in outEdges.keys)
			{
				assert(stack.isEmpty())
				recurse(start) { vertex, body ->
					if (solution !== null)
					{
						return@recurse
					}
					if (stack.contains(vertex))
					{
						// Found a cycle.
						val list = stack.toMutableList()
						// Include this vertex twice.
						list.add(vertex)
						solution = list.subList(
							list.indexOf(vertex), list.size)
						return@recurse
					}
					if (!reached.contains(vertex))
					{
						reached.add(vertex)
						stack.add(vertex)
						outEdges[vertex]!!.forEach { successor ->
							body(successor)
						}
						stack.remove(vertex)
					}
				}
				if (solution !== null)
				{
					break
				}
			}
			return solution!!
		}

	/**
	 * A copy of this `Graph` with the same vertices, but with every edge having
	 * the reverse direction.
	 */
	val reverse: Graph<Vertex>
		get ()
		{
			val result = Graph<Vertex>()
			outEdges.entries.associateTo(result.outEdges) {
				(k, v) -> k to v.toMutableSet()
			}
			inEdges.entries.associateTo(result.inEdges) {
				(k, v) -> k to v.toMutableSet()
			}
			return result
		}

	/**
	 * Visit the vertices in DAG order. The action is invoked for each vertex,
	 * also passing a completion action to invoke when that vertex visit is
	 * considered complete, allowing successors for which all predecessors have
	 * completed to be visited.
	 *
	 * The afterTraversal action runs at the end of the last completed vertex's
	 * completion action (passed as the second argument to the visitAction).
	 * Thus, if the completion actions always run in the same [Thread] as the
	 * visitAction, the graph is effectively traversed serially, running the
	 * afterTraversal action at the end, just before returning from this method.
	 *
	 * The receiver must not be [cyclic][isCyclic].
	 *
	 * @param visitAction
	 *   What to do for each vertex. The action takes the vertex and a
	 *   zero-argument action to run when processing the vertex is considered
	 *   finished (with regard to ordering the traversal).
	 * @param afterTraversal
	 *   What to do after traversing the entire graph.
	 */
	fun parallelVisitThen (
		visitAction: (Vertex, () -> Unit) -> Unit,
		afterTraversal: () -> Unit)
	{
		ParallelVisitor(visitAction, afterTraversal).execute()
	}

	/**
	 * Visit the vertices in DAG order. The action is invoked for each vertex,
	 * also passing a completion action to invoke when that vertex visit is
	 * considered complete, allowing successors for which all predecessors have
	 * completed to be visited.
	 *
	 * Block this [Thread] until traversal is complete.
	 *
	 * The receiver must not be [cyclic][isCyclic].
	 *
	 * @param visitAction
	 *   What to do for each vertex.
	 */
	fun parallelVisit (visitAction: (Vertex, () -> Unit) -> Unit)
	{
		assert(!isCyclic)
		val semaphore = Semaphore(0)
		val safetyCheck = AtomicBoolean(false)
		parallelVisitThen(visitAction) {
			val old = safetyCheck.getAndSet(true)
			assert(!old) { "Reached end of graph traversal twice" }
			semaphore.release()
		}
		semaphore.acquireUninterruptibly()
	}

	/**
	 * A [ParallelVisitor] is a mechanism for visiting the vertices of its graph
	 * in successor order – a vertex is visited exactly once, when its
	 * predecessors have all indicated they are complete.
	 *
	 * @property visitAction
	 *   This action is invoked during [execution][execute] exactly once for
	 *   each vertex, precisely when that vertex's predecessors have all
	 *   completed. This vertex must indicate its own completion by invoking the
	 *   passed action.
	 *
	 *   Note that this completion action does not have to be invoked within the
	 *   same thread as the invocation of the visitAction, it merely has to
	 *   indicate temporally when the node's visit is complete. This flexibility
	 *   allows thread pools and other mechanisms to be leveraged for parallel
	 *   graph traversal.
	 *
	 *   Do not modify the graph during this traversal. Also, do not invoke on a
	 *   cyclic graph. Do not invoke any completion action more than once.
	 * @property afterTraversal
	 *   This action runs after a complete traversal of the graph. This runs as
	 *   part of the last completed vertex's [visitAction], so if the
	 *   [visitAction] always runs its completion action (its second argument)
	 *   within the same [Thread], this final action will also run in that
	 *   thread.
	 *
	 * @constructor
	 *
	 * Construct a new `ParallelVisitor`.
	 *
	 * @param visitAction
	 *   What to perform for each vertex being visited. The second argument to
	 *   this action is a to invoke when the [Vertex] has been fully processed.
	 * @param afterTraversal
	 *   What to perform after the entire traversal has completed.
	 */
	private inner class ParallelVisitor internal constructor (
		private val visitAction: (Vertex, () -> Unit) -> Unit,
		private val afterTraversal: () -> Unit)
	{
		/**
		 * A [Map] keeping track of how many predecessors of each vertex have
		 * not yet been completed.
		 */
		private val predecessorCountdowns =
			mutableMapOf<Vertex, AtomicInteger>()

		/**
		 * A collection of all outstanding vertices which have had a predecessor
		 * complete.
		 */
		val queue: Deque<Vertex> = ArrayDeque()

		/**
		 * Counts down to zero to determine when the last vertex has completed
		 * its visit.
		 */
		val completionCountdown = AtomicInteger(-1)

		/**
		 * Compute the [Map] that tracks how many predecessors of each vertex
		 * have not yet been completed.
		 */
		@Synchronized
		private fun computePredecessorCountdowns ()
		{
			assert(predecessorCountdowns.isEmpty())
			assert(queue.isEmpty())
			completionCountdown.set(vertexCount)
			inEdges.forEach { (vertex, value) ->
				val predecessorsSize = value.size
				if (predecessorsSize == 0)
				{
					// Seed it with 1, as though each root has an incoming edge.
					// Also queue the root as though that edge has been visited.
					predecessorCountdowns[vertex] = AtomicInteger(1)
					queue.addLast(vertex)
				}
				else
				{
					predecessorCountdowns[vertex] = AtomicInteger(predecessorsSize)
				}
			}
		}

		/**
		 * Process the queue until it's exhausted *and* there are no more
		 * unvisited vertices. This mechanism completely avoids recursion, while
		 * working correctly regardless of whether the [visitAction] invokes its
		 * completion action in the same [Thread] or in another.
		 */
		@Synchronized
		private fun visitRemainingVertices ()
		{
			// If all vertices have already been visited, just exit. The last
			// one to complete will have already executed the afterTraversal
			// action.
			if (predecessorCountdowns.isEmpty())
			{
				assert(queue.isEmpty())
				return
			}
			// Otherwise, visit vertices until none are immediately available.
			// Running out does not signify completion of the graph traversal,
			// since vertex completion might happen in other threads, but the
			// vertex completion action also invokes this method.
			while (!queue.isEmpty())
			{
				val vertex = queue.removeFirst()
				val countdown = predecessorCountdowns[vertex]
				val value = countdown!!.decrementAndGet()
				assert(value >= 0)
				if (value == 0)
				{
					predecessorCountdowns.remove(vertex)
					val alreadyRan = AtomicBoolean(false)
					visitAction(vertex) {
						// At this point the client code is finished with this
						// vertex and is explicitly allowing any successors to
						// run if they have no unfinished predecessors.
						val old = alreadyRan.getAndSet(true)
						assert(!old) {
							"Ran completion action twice for vertex"
						}
						queueSuccessors(successorsOf(vertex))
						if (completionCountdown.decrementAndGet() == 0)
						{
							assert(queue.isEmpty())
							assert(predecessorCountdowns.isEmpty())
							afterTraversal()
						}
						else
						{
							visitRemainingVertices()
						}
					}
				}
			}
		}

		/**
		 * Queue the given set of vertices, each of which has just had a
		 * predecessor complete its visit.
		 *
		 * @param vertices
		 *   The vertices to queue.
		 */
		@Synchronized
		private fun queueSuccessors (vertices: Set<Vertex>)
		{
			queue.addAll(vertices)
		}

		/**
		 * Perform a traversal of the graph in such an order that a vertex can
		 * only be processed after its predecessors have all completed.
		 *
		 * This operation blocks until completion only if the [visitAction] runs
		 * its passed completion action in the same thread.
		 *
		 * Whichever thread runs the last completion action will also execute
		 * the [afterTraversal] action immediately afterward.
		 */
		fun execute ()
		{
			computePredecessorCountdowns()
			if (predecessorCountdowns.isEmpty())
			{
				// There are no vertices, so run the afterTraversal action now.
				afterTraversal()
				return
			}
			// Otherwise, the last completion action to run will invoke the
			// afterTraversal action.
			visitRemainingVertices()
		}
	}

	/**
	 * Create a subgraph containing each of the provided vertices and all of
	 * their ancestors.
	 *
	 * Note: Don't use [parallelVisitThen], because the graph may contain cycles
	 * that we're interested in rendering (i.e., to determine how to break
	 * them).
	 *
	 * @param seeds
	 *   The vertices whose ancestors to include.
	 * @return
	 *   The specified subgraph of the receiver.
	 */
	fun ancestryOfAll (seeds: Collection<Vertex>): Graph<Vertex>
	{
		val ancestrySet = seeds.toMutableSet()
		var newAncestors = seeds.toMutableSet()
		while (newAncestors.isNotEmpty())
		{
			val previousAncestors: Set<Vertex> = newAncestors
			newAncestors = mutableSetOf()
			previousAncestors.forEach { vertex ->
				newAncestors.addAll(predecessorsOf(vertex))
			}
			newAncestors.removeAll(ancestrySet)
			ancestrySet.addAll(newAncestors)
		}
		val ancestryGraph = Graph(this)
		ArrayList(ancestryGraph.outEdges.keys).forEach { vertex ->
			if (!ancestrySet.contains(vertex))
			{
				ancestryGraph.exciseVertex(vertex)
			}
		}
		return ancestryGraph
	}

	/**
	 * An acyclic subgraph of the receiver, containing all vertices, and a
	 * locally maximal subset of the edges (i.e., no additional edge of the
	 * original graph can be added without making the resulting graph acyclic).
	 *
	 * Multiple solutions are possible, depending on which edges are included
	 * first.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	val spanningDag: Graph<Vertex>
		get()
		{
			val spanningDag = Graph<Vertex>()
			spanningDag.addVertices(vertices)
			val stackSet = mutableSetOf<Vertex>()
			val stackList = mutableListOf<Vertex>()
			val scanned = mutableListOf<Vertex>()
			outEdges.entries.stream().sorted(
				Comparator
					.comparingInt { e: Map.Entry<Vertex, Set<Vertex>> ->
						e.value.size
					}
					.thenComparing { e -> e.key.toString() }
			)
				.map { obj -> obj.key }
				.forEach { startVertex ->
					assert(stackSet.isEmpty() && stackList.isEmpty())
					recurse(startVertex) { vertex, body ->
						if (!stackSet.contains(vertex))
						{
							// The edge is part of a dag formed by a
							// depth-first traversal. Include it.
							if (stackList.isNotEmpty())
							{
								spanningDag.addEdge(
									stackList[stackList.size - 1],
									vertex)
							}
							if (!scanned.contains(vertex))
							{
								scanned.add(vertex)
								stackSet.add(vertex)
								stackList.add(vertex)
								outEdges[vertex]!!.forEach { p -> body(p) }
								stackSet.remove(vertex)
								stackList.remove(vertex)
							}
						}
					}
				}
			return spanningDag
		}

	/**
	 * A subgraph of an acyclic graph that excludes any edges for which there is
	 * another path connecting those edges in the original graph.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	val dagWithoutRedundantEdges: Graph<Vertex>
		get()
		{
			assert(!isCyclic)
			val ancestorSets = mutableMapOf<Vertex, Set<Vertex>>()
			parallelVisit { vertex, completionAction ->
				val ancestorSet: Set<Vertex>
				val predecessors = predecessorsOf(vertex)
				when
				{
					predecessors.isEmpty() ->
					{
						ancestorSet = emptySet()
					}
					predecessors.size == 1 ->
					{
						val predecessor = predecessors.iterator().next()
						ancestorSet = ancestorSets[predecessor]!!.toMutableSet()
						ancestorSet.add(predecessor)
					}
					else ->
					{
						ancestorSet = mutableSetOf()
						for (predecessor in predecessors)
						{
							ancestorSet.addAll(ancestorSets[predecessor]!!)
							ancestorSet.add(predecessor)
						}
					}
				}
				ancestorSets[vertex] = ancestorSet
				completionAction()
			}
			val result = Graph<Vertex>()
			result.addVertices(vertices)
			// Add edges to the new graph that don't have a corresponding path
			// in the original graph with length at least 2. If such a path
			// exists, this edge is redundant. We detect such a path by checking
			// if any of the destination's predecessors have the source in their
			// sets of (proper) ancestors.
			inEdges.forEach { (destination, destinationPredecessors) ->
				destinationPredecessors.forEach { source ->
					// Check if there is a path of length at least 2 from the
					// source to the destination in the original graph.
					var isRedundant = false
					for (destinationPredecessor in destinationPredecessors)
					{
						if (ancestorSets[destinationPredecessor]!!.contains(
								source
							))
						{
							isRedundant = true
							break
						}
					}
					if (!isRedundant)
					{
						result.addEdge(source, destination)
					}
				}
			}
			return result
		}

	/**
	 * Given an acyclic graph, produce a subgraph that excludes any edges for
	 * which there is another path connecting those edges in the original graph.
	 *
	 * @param spanningDag
	 *   The spanning directed acyclic graph to structure.
	 * @return
	 *   A reduced copy of this graph.
	 */
	fun withoutRedundantEdges (spanningDag: Graph<Vertex>): Graph<Vertex>
	{
		val reduced = spanningDag.dagWithoutRedundantEdges
		// Add in all back-edges from the original graph, even though they may
		// appear redundant.  That's to ensure that cycles appear clearly.
		outEdges.forEach { (vertex, originalSuccessors) ->
			val dagSuccessors = spanningDag.successorsOf(vertex)
			if (dagSuccessors.size < originalSuccessors.size)
			{
				originalSuccessors.forEach { successor ->
					if (!dagSuccessors.contains(successor))
					{
						reduced.addEdge(vertex, successor)
					}
				}
			}
		}
		return reduced
	}

	companion object
	{
		/**
		 * Check that the condition is true, otherwise throw a
		 * [GraphPreconditionFailure] with the given message.
		 *
		 * @param condition
		 *   The condition that should be true.
		 * @param message
		 *   A description of the failed precondition.
		 * @throws GraphPreconditionFailure
		 *   If the condition was false.
		 */
		@Throws(GraphPreconditionFailure::class)
		@Contract("false, _ -> fail")
		fun ensure (condition: Boolean, message: String?)
		{
			if (!condition)
			{
				throw GraphPreconditionFailure(message)
			}
		}

		/**
		 * Check that the object is not `null`, otherwise throw a
		 * [GraphPreconditionFailure] with the given message.
		 *
		 * @param o
		 *   The object o check for nullness.
		 * @param message
		 *   A description of the failure.
		 * @throws GraphPreconditionFailure
		 *   If the object is `null`.
		 */
		@Throws(GraphPreconditionFailure::class)
		@Contract("null, _ -> fail")
		fun notNull (o: Any?, message: String?)
		{
			if (o === null)
			{
				throw GraphPreconditionFailure(message)
			}
		}
	}
}
