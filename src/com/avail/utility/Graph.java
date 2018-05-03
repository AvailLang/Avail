/*
 * Graph.java
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

package com.avail.utility;

import com.avail.annotations.InnerAccess;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation2NotNull;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.unmodifiableSet;

/**
 * A {@code Graph} is an unordered collection of vertices, along with the
 * successor-predecessor relationships between them.  From the Graph's
 * viewpoint, the vertices are merely mementos that support {@link
 * #equals(Object)} and {@link #hashCode()}.  Edges are not explicitly
 * represented, but instead are a consequence of how the {@link #outEdges} and
 * {@linkplain #inEdges} are populated.
 *
 * <p>
 * {@link Graph} is not synchronized.
 * </p>
 *
 * @param <Vertex>
 *        The vertex type with which to parameterize the Graph.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class Graph<Vertex>
{
	/** Construct a new graph with no vertices or edges. */
	public Graph ()
	{
		outEdges = new HashMap<>();
		inEdges = new HashMap<>();
	}

	/**
	 * Construct a new graph with the same vertices and edges as the argument.
	 *
	 * @param graph The graph to copy.
	 */
	public Graph (final Graph<Vertex> graph)
	{
		outEdges = new HashMap<>();
		inEdges = new HashMap<>();
		graph.outEdges.forEach((k, v) -> outEdges.put(k, new HashSet<>(v)));
		graph.inEdges.forEach((k, v) -> inEdges.put(k, new HashSet<>(v)));
	}

	/**
	 * A {@link GraphPreconditionFailure} is thrown whenever a precondition of
	 * a graph manipulation operation does not hold.  The preconditions are
	 * described in the JavaDoc comment for each operation.
	 */
	public static final class GraphPreconditionFailure extends RuntimeException
	{
		/** The serial version identifier. */
		private static final long serialVersionUID = -4084330590821139287L;

		/**
		 * Construct a new {@code GraphPreconditionFailure} with the given
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
	@InnerAccess
	static void ensure (
		final boolean condition,
		final String message)
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
	@InnerAccess final Map<Vertex, Set<Vertex>> outEdges;

	/**
	 * A map from each vertex of the {@link Graph} to its set of predecessor
	 * vertices within the graph.  Each such set is unordered.
	 *
	 * <p>
	 * This mapping is maintained in lock-step with the {@link #outEdges}, which
	 * is the reverse relationship.
	 * </p>
	 */
	@InnerAccess final Map<Vertex, Set<Vertex>> inEdges;

	/**
	 * Remove all edges and vertices from the graph.
	 */
	public void clear ()
	{
		outEdges.clear();
		inEdges.clear();
	}

	/**
	 * Add a vertex to the graph.  Fail if the vertex is already present in the
	 * graph.  The vertex initially has no edges within this graph.
	 *
	 * @param vertex The vertex to add to the graph.
	 */
	public void addVertex (final Vertex vertex)
	{
		ensure(!outEdges.containsKey(vertex), "vertex is already in graph");
		outEdges.put(vertex, new HashSet<>());
		inEdges.put(vertex, new HashSet<>());
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
			outEdges.put(vertex, new HashSet<>());
			inEdges.put(vertex, new HashSet<>());
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
			outEdges.put(vertex, new HashSet<>());
			inEdges.put(vertex, new HashSet<>());
		}
	}

	/**
	 * Remove a vertex from the graph, failing if it has any connected edges.
	 * Fail if the vertex is not present in the graph.
	 *
	 * @param vertex The vertex to attempt to remove from the graph.
	 */
	public void removeVertex (final Vertex vertex)
	{
		ensure(outEdges.containsKey(vertex), "vertex is not present");
		excludeVertex(vertex);
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
			ensure(outVertices.isEmpty(), "vertex has outbound edges");
			ensure(inVertices.isEmpty(), "vertex has inbound edges");
			outEdges.remove(vertex);
			inEdges.remove(vertex);
		}
	}

	/**
	 * If the given vertex is present in the graph, remove its incoming and
	 * outgoing edges, then the vertex itself.  If the given vertex is not in
	 * the graph, do nothing.
	 *
	 * @param vertex The vertex to excise from the graph, if present.
	 */
	public void exciseVertex (final Vertex vertex)
	{
		excludeEdgesFrom(vertex);
		excludeEdgesTo(vertex);
		excludeVertex(vertex);
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
	 * Add an edge to the graph from the source vertex to the target vertex.
	 * Fail if either vertex is not present in the graph or if it already
	 * contains an edge from the source vertex to the target vertex.
	 *
	 * @param sourceVertex The source of the edge to attempt to add.
	 * @param targetVertex The target of the edge to attempt to add.
	 */
	public void addEdge (
		final Vertex sourceVertex,
		final Vertex targetVertex)
	{
		final Set<Vertex> sourceOutSet = outEdges.get(sourceVertex);
		ensure(sourceOutSet != null, "source vertex is not present");
		final Set<Vertex> targetInSet = inEdges.get(targetVertex);
		ensure(targetInSet != null, "target vertex is not present");
		ensure(
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
	public void includeEdge (
		final Vertex sourceVertex,
		final Vertex targetVertex)
	{
		final Set<Vertex> sourceOutSet = outEdges.get(sourceVertex);
		ensure(sourceOutSet != null, "source vertex is not in graph");
		final Set<Vertex> targetInSet = inEdges.get(targetVertex);
		ensure(targetInSet != null, "target vertex is not in graph");
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
	public void removeEdge (
		final Vertex sourceVertex,
		final Vertex targetVertex)
	{
		final Set<Vertex> sourceOutSet = outEdges.get(sourceVertex);
		ensure(sourceOutSet != null, "source vertex is not in graph");
		final Set<Vertex> targetInSet = inEdges.get(targetVertex);
		ensure(targetInSet != null, "target vertex is not in graph");
		ensure(sourceOutSet.contains(targetVertex), "edge is not in graph");
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
	public void excludeEdge (
		final Vertex sourceVertex,
		final Vertex targetVertex)
	{
		final Set<Vertex> sourceOutSet = outEdges.get(sourceVertex);
		ensure(sourceOutSet != null, "source vertex is not in graph");
		final Set<Vertex> targetInSet = inEdges.get(targetVertex);
		ensure(targetInSet != null, "target vertex is not in graph");
		sourceOutSet.remove(targetVertex);
		targetInSet.remove(sourceVertex);
	}

	/**
	 * Remove all edges from the graph which originate at the given vertex.
	 * Fail if the vertex is not present in the graph.
	 *
	 * @param sourceVertex The source vertex of the edges to exclude.
	 */
	public void excludeEdgesFrom (
		final Vertex sourceVertex)
	{
		final Set<Vertex> sourceOutSet = outEdges.get(sourceVertex);
		ensure(sourceOutSet != null, "source vertex is not in graph");
		for (final Vertex successor : sourceOutSet)
		{
			inEdges.get(successor).remove(sourceVertex);
		}
		sourceOutSet.clear();
	}

	/**
	 * Remove all edges from the graph which terminate at the given vertex.
	 * Fail if the vertex is not present in the graph.
	 *
	 * @param targetVertex The target vertex of the edges to exclude.
	 */
	public void excludeEdgesTo (
		final Vertex targetVertex)
	{
		final Set<Vertex> targetInSet = inEdges.get(targetVertex);
		ensure(targetInSet != null, "target vertex is not in graph");
		for (final Vertex predecessor : targetInSet)
		{
			outEdges.get(predecessor).remove(targetVertex);
		}
		targetInSet.clear();
	}

	/**
	 * Determine if the graph contains an edge from the source vertex to the
	 * target vertex.  Fail if either vertex is not present in the graph.
	 *
	 * @param sourceVertex The vertex which is the source of the purported edge.
	 * @param targetVertex The vertex which is the target of the purported edge.
	 * @return Whether the graph contains the specified edge.
	 */
	public boolean includesEdge (
		final Vertex sourceVertex,
		final Vertex targetVertex)
	{
		final Set<Vertex> sourceOutSet = outEdges.get(sourceVertex);
		ensure(sourceOutSet != null, "source vertex is not in graph");
		final Set<Vertex> targetInSet = inEdges.get(targetVertex);
		ensure(targetInSet != null, "target vertex is not in graph");
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

	/**
	 * Is the graph empty?
	 *
	 * @return {@code true} if the graph is empty, {@code false} otherwise.
	 */
	public boolean isEmpty ()
	{
		return outEdges.size() == 0;
	}

	/**
	 * Answer an {@linkplain Collections#unmodifiableSet(Set) unmodifiable set}
	 * containing this graph's vertices.
	 *
	 * @return The graph's vertices.
	 */
	public Set<Vertex> vertices ()
	{
		return unmodifiableSet(outEdges.keySet());
	}

	/**
	 * Answer the number of vertices in this graph.
	 *
	 * @return The vertex count of the graph.
	 */
	public int vertexCount ()
	{
		return outEdges.size();
	}

	/**
	 * Answer the {@linkplain Collections#unmodifiableSet(Set) unmodifiable set}
	 * of successors of the specified vertex.  Fail if the vertex is not present
	 * in the graph.
	 *
	 * @param vertex The vertex for which to answer successors.
	 * @return The successors of the vertex.
	 */
	public Set<Vertex> successorsOf (final Vertex vertex)
	{
		ensure(outEdges.containsKey(vertex), "source vertex is not in graph");
		return unmodifiableSet(outEdges.get(vertex));
	}

	/**
	 * Answer the {@linkplain Collections#unmodifiableSet(Set) unmodifiable set}
	 * of predecessors of the specified vertex.  Fail if the vertex is not
	 * present in the graph.
	 *
	 * @param vertex The vertex for which to answer predecessors.
	 * @return The predecessors of the vertex.
	 */
	public Set<Vertex> predecessorsOf (final Vertex vertex)
	{
		ensure(inEdges.containsKey(vertex), "target vertex is not in graph");
		return unmodifiableSet(inEdges.get(vertex));
	}

	/**
	 * Answer the roots of the graph.  These are the vertices with no incoming
	 * edges.
	 *
	 * @return The vertices that are not the target of any edge.
	 */
	public Set<Vertex> roots ()
	{
		final Set<Vertex> roots = new HashSet<>();
		for (final Entry<Vertex, Set<Vertex>> entry : inEdges.entrySet())
		{
			if (entry.getValue().isEmpty())
			{
				roots.add(entry.getKey());
			}
		}
		return roots;
	}

	/**
	 * Determine if the graph contains any (directed) cycles.
	 *
	 * @return True if the graph is cyclic, otherwise false.
	 */
	public boolean isCyclic ()
	{
		final Map<Vertex, Integer> predecessorCountdowns =
			new HashMap<>(outEdges.size());
		final Deque<Vertex> stack = new ArrayDeque<>();
		for (final Entry<Vertex, Set<Vertex>> entry : inEdges.entrySet())
		{
			final Vertex vertex = entry.getKey();
			final int predecessorsSize = entry.getValue().size();
			if (predecessorsSize == 0)
			{
				// Seed it with 1, as though each root has an incoming edge.
				// Also stack the root as though that edge has been visited.
				predecessorCountdowns.put(vertex, 1);
				stack.add(vertex);
			}
			else
			{
				predecessorCountdowns.put(vertex, predecessorsSize);
			}
		}
		while (!stack.isEmpty())
		{
			final Vertex vertex = stack.removeLast();
			final int countdown = predecessorCountdowns.get(vertex);
			if (countdown == 1)
			{
				predecessorCountdowns.remove(vertex);
				stack.addAll(outEdges.get(vertex));
			}
			else
			{
				assert countdown > 1;
				predecessorCountdowns.put(vertex, countdown - 1);
			}
		}
		// If anything is left in predecessorCountdowns, it was unreachable from
		// the roots (impossible by definition if acyclic), or it was descended
		// from a cycle.
		return !predecessorCountdowns.isEmpty();
	}

	/**
	 * Create a copy of this {@code Graph} with the same vertices, but with
	 * every edge having the reverse direction.
	 *
	 * @return The reverse of this graph.
	 */
	public Graph<Vertex> reverse ()
	{
		final Graph<Vertex> result = new Graph<>();
		outEdges.forEach((k, v) -> result.inEdges.put(k, new HashSet<>(v)));
		inEdges.forEach((k, v) -> result.outEdges.put(k, new HashSet<>(v)));
		return result;
	}

	/**
	 * Visit the vertices in DAG order.  The action is invoked for each vertex,
	 * also passing a completion action (a {@link Continuation0}) to invoke when
	 * that vertex visit is considered complete, allowing successors for which
	 * all predecessors have completed to be visited.
	 *
	 * <p>The afterTraversal action runs at the end of the last completed
	 * vertex's completion action (passed as the second argument to the
	 * visitAction).  Thus, if the completion actions always run in the same
	 * {@link Thread} as the visitAction, the graph is effectively traversed
	 * serially, running the afterTraversal action at the end, just before
	 * returning from this method.</p>
	 *
	 * <p>The receiver must not be {@link #isCyclic() cyclic}.</p>
	 *
	 * @param visitAction
	 *        What to do for each vertex.  The action takes the vertex and a
	 *        zero-argument action to run when processing the vertex is
	 *        considered finished (with regard to ordering the traversal).
	 * @param afterTraversal
	 *        What to do after traversing the entire graph.
	 */
	public void parallelVisitThen (
		final Continuation2NotNull<Vertex, Continuation0> visitAction,
		final Continuation0 afterTraversal)
	{
		new ParallelVisitor(visitAction, afterTraversal).execute();
	}

	/**
	 * Visit the vertices in DAG order.  The action is invoked for each vertex,
	 * also passing a completion action (a {@link Continuation0}) to invoke when
	 * that vertex visit is considered complete, allowing successors for which
	 * all predecessors have completed to be visited.
	 *
	 * <p>Block this {@link Thread} until traversal is complete.</p>
	 *
	 * <p>The receiver must not be {@link #isCyclic() cyclic}.</p>
	 *
	 * @param visitAction What to do for each vertex.
	 */
	public void parallelVisit (
		final Continuation2NotNull<Vertex, Continuation0> visitAction)
	{
		final Semaphore semaphore = new Semaphore(0);
		final AtomicBoolean safetyCheck = new AtomicBoolean(false);
		parallelVisitThen(
			visitAction,
			() ->
			{
				final boolean old = safetyCheck.getAndSet(true);
				assert !old : "Reached end of graph traversal twice";
				semaphore.release();
			});
		semaphore.acquireUninterruptibly();
	}

	/**
	 * A {@link ParallelVisitor} is a mechanism for visiting the vertices of its
	 * graph in successor order – a vertex is visited exactly once, when its
	 * predecessors have all indicated they are complete.
	 */
	private class ParallelVisitor
	{
		/**
		 * This action is invoked during {@link #execute() execution} exactly
		 * once for each vertex, precisely when that vertex's predecessors have
		 * all completed.  This vertex must indicate its own completion by
		 * invoking the passed {@link Continuation0 action}.
		 *
		 * <p>Note that this completion action does not have to be invoked
		 * within the same thread as the invocation of the visitAction, it
		 * merely has to indicate temporally when the node's visit is complete.
		 * This flexibility allows thread pools and other mechanisms to
		 * be leveraged for parallel graph traversal.</p>
		 *
		 * <p>Do not modify the graph during this traversal.  Also, do not
		 * invoke on a cyclic graph.  Do not invoke any completion action more
		 * than once.</p>
		 */
		private final Continuation2NotNull<Vertex, Continuation0> visitAction;

		/**
		 * This action runs after a complete traversal of the graph.  This runs
		 * as part of the last completed vertex's {@link #visitAction}, so if
		 * the {@link #visitAction} always runs its completion action (its
		 * second argument) within the same {@link Thread}, this final action
		 * will also run in that thread.
		 */
		private final Continuation0 afterTraversal;

		/**
		 * A {@link Map} keeping track of how many predecessors of each vertex
		 * have not yet been completed.
		 */
		private final Map<Vertex, MutableInt> predecessorCountdowns =
			new HashMap<>();

		/**
		 * A collection of all outstanding vertices which have had a predecessor
		 * complete.
		 */
		final Deque<Vertex> queue = new ArrayDeque<>();

		/**
		 * Counts down to zero to determine when the last vertex has completed
		 * its visit.
		 */
		final AtomicInteger completionCountdown = new AtomicInteger(-1);

		/**
		 * Construct a new {@code ParallelVisitor}.
		 *
		 * @param visitAction What to perform for each vertex being visited.
		 */
		@InnerAccess ParallelVisitor (
			final Continuation2NotNull<Vertex, Continuation0> visitAction,
			final Continuation0 afterTraversal)
		{
			this.visitAction = visitAction;
			this.afterTraversal = afterTraversal;
		}

		/**
		 * Compute the {@link Map} that tracks how many predecessors of each
		 * vertex have not yet been completed.
		 */
		private synchronized void computePredecessorCountdowns ()
		{
			assert predecessorCountdowns.isEmpty();
			assert queue.isEmpty();
			completionCountdown.set(vertexCount());
			for (final Entry<Vertex, Set<Vertex>> entry : inEdges.entrySet())
			{
				final Vertex vertex = entry.getKey();
				final int predecessorsSize = entry.getValue().size();
				if (predecessorsSize == 0)
				{
					// Seed it with 1, as though each root has an incoming edge.
					// Also queue the root as though that edge has been visited.
					predecessorCountdowns.put(vertex, new MutableInt(1));
					queue.addLast(vertex);
				}
				else
				{
					predecessorCountdowns.put(
						vertex, new MutableInt(predecessorsSize));
				}
			}
		}

		/**
		 * Process the queue until it's exhausted <em>and</em> there are no more
		 * unvisited vertices.  This mechanism completely avoids recursion,
		 * while working correctly regardless of whether the {@link
		 * #visitAction} invokes its completion action in the same {@link
		 * Thread} or in another.
		 */
		private synchronized void visitRemainingVertices ()
		{
			// If all vertices have already been visited, just exit.  The last
			// one to complete will have already executed the afterTraversal
			// action.
			if (predecessorCountdowns.isEmpty())
			{
				assert queue.isEmpty();
				return;
			}
			// Otherwise, visit vertices until none are immediately available.
			// Running out does not signify completion of the graph traversal,
			// since vertex completion might happen in other threads, but the
			// vertex completion action also invokes this method.
			while (!queue.isEmpty())
			{
				final Vertex vertex = queue.removeFirst();
				final MutableInt countdown = predecessorCountdowns.get(vertex);
				countdown.value--;
				assert countdown.value >= 0;
				if (countdown.value == 0)
				{
					predecessorCountdowns.remove(vertex);
					final AtomicBoolean alreadyRan = new AtomicBoolean(false);
					visitAction.value(
						vertex,
						() ->
						{
							// At this point the client code is finished with
							// this vertex and is explicitly allowing any
							// successors to run if they have no unfinished
							// predecessors.
							final boolean old = alreadyRan.getAndSet(true);
							assert !old
								: "Ran completion action twice for vertex";
							queueSuccessors(successorsOf(vertex));
							if (completionCountdown.decrementAndGet() == 0)
							{
								assert queue.isEmpty();
								assert predecessorCountdowns.isEmpty();
								afterTraversal.value();
							}
							else
							{
								visitRemainingVertices();
							}
						});
				}
			}
		}

		/**
		 * Queue the given set of vertices, each of which has just had a
		 * predecessor complete its visit.
		 *
		 * @param vertices
		 *        The vertices to queue.
		 */
		private synchronized void queueSuccessors (final Set<Vertex> vertices)
		{
			queue.addAll(vertices);
		}

		/**
		 * Perform a traversal of the graph in such an order that a vertex can
		 * only be processed after its predecessors have all completed.
		 *
		 * <p>This operation blocks until completion only if the {@link
		 * #visitAction} runs its passed completion action in the same
		 * thread.</p>
		 *
		 * <p>Whichever thread runs the last completion action will also execute
		 * the {@link #afterTraversal} action immediately afterward.</p>
		 */
		@InnerAccess void execute ()
		{
			computePredecessorCountdowns();
			if (predecessorCountdowns.isEmpty())
			{
				// There are no vertices, so run the afterTraversal action now.
				afterTraversal.value();
				return;
			}
			// Otherwise, the last completion action to run will invoke the
			// afterTraversal action.
			visitRemainingVertices();
		}
	}

	/**
	 * Create a subgraph containing each of the provided vertices and all of
	 * their ancestors.
	 *
	 * @param seeds The vertices whose ancestors to include.
	 * @return The specified subgraph of the receiver.
	 */
	public Graph<Vertex> ancestryOfAll (final Collection<Vertex> seeds)
	{
		final Graph<Vertex> ancestry = new Graph<>();
		ancestry.addVertices(seeds);
		reverse().parallelVisit(
			(vertex, completionAction) ->
			{
				for (final Vertex successor : successorsOf(vertex))
				{
					if (ancestry.includesVertex(successor))
					{
						ancestry.includeVertex(vertex);
						ancestry.addEdge(vertex, successor);
					}
				}
				completionAction.value();
			});
		return ancestry;
	}

	/**
	 * Given an acyclic graph, produce a subgraph that excludes any edges for
	 * which there is another path connecting those edges in the original graph.
	 *
	 * @return A reduced copy of this graph.
	 */
	public Graph<Vertex> dagWithoutRedundantEdges ()
	{
		assert !isCyclic();
		final Map<Vertex, Set<Vertex>> ancestorSets = new HashMap<>();
		parallelVisit((vertex, completionAction) ->
		{
			final Set<Vertex> ancestorSet;
			final Set<Vertex> predecessors = predecessorsOf(vertex);
			if (predecessors.size() == 0)
			{
				ancestorSet = Collections.emptySet();
			}
			else if (predecessors.size() == 1)
			{
				final Vertex predecessor = predecessors.iterator().next();
				ancestorSet = new HashSet<>(ancestorSets.get(predecessor));
				ancestorSet.add(predecessor);
			}
			else
			{
				ancestorSet = new HashSet<>(predecessors.size() * 3);
				for (final Vertex predecessor : predecessors)
				{
					ancestorSet.addAll(ancestorSets.get(predecessor));
					ancestorSet.add(predecessor);
				}
			}
			ancestorSets.put(vertex, ancestorSet);
			completionAction.value();
		});
		final Graph<Vertex> result = new Graph<>();
		result.addVertices(vertices());
		// Add edges to the new graph that don't have a corresponding path in
		// the original graph with length at least 2.  If such a path exists,
		// this edge is redundant.  We detect such a path by checking if any of
		// the destination's predecessors have the source in their sets of
		// (proper) ancestors.
		for (final Entry<Vertex, Set<Vertex>> entry : inEdges.entrySet())
		{
			final Vertex destination = entry.getKey();
			final Set<Vertex> destinationPredecessors = entry.getValue();
			for (final Vertex source : destinationPredecessors)
			{
				// Check if there is a path of length at least 2 from the source
				// to the destination in the original graph.
				boolean isRedundant = false;
				for (final Vertex destinationPredecessor
					: destinationPredecessors)
				{
					if (ancestorSets.get(destinationPredecessor).contains(
						source))
					{
						isRedundant = true;
						break;
					}
				}
				if (!isRedundant)
				{
					result.addEdge(source, destination);
				}
			}
		}
		return result;
	}
}
