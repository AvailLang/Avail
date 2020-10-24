/*
 * GraphTest.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation
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
package com.avail.test

import com.avail.utility.Graph
import com.avail.utility.Graph.GraphPreconditionFailure
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Basic functionality test of [Graph]s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class GraphTest
{
	/**
	 * Test: Check properties of the emptyTuple graph.
	 */
	@Test
	fun emptyGraphTest()
	{
		val emptyGraph = Graph<Int>()
		Assertions.assertFalse(emptyGraph.includesVertex(5))
		Assertions.assertFalse(emptyGraph.includesVertex(7))
		Assertions.assertEquals(0, emptyGraph.size)
	}

	/**
	 * Test: Check properties of some very small graphs.
	 */
	@Test
	fun tinyGraphTest()
	{
		val tinyGraph = Graph<Int>()
		tinyGraph.addVertex(5)
		Assertions.assertTrue(tinyGraph.includesVertex(5))
		Assertions.assertFalse(tinyGraph.includesVertex(6))
		Assertions.assertFalse(tinyGraph.includesEdge(5, 5))
		tinyGraph.addVertex(6)
		Assertions.assertFalse(tinyGraph.includesEdge(5, 6))
		Assertions.assertFalse(tinyGraph.includesEdge(6, 5))
		Assertions.assertEquals(2, tinyGraph.size)
		tinyGraph.addEdge(5, 6)
		Assertions.assertFalse(tinyGraph.includesEdge(5, 5))
		Assertions.assertTrue(tinyGraph.includesEdge(5, 6))
		Assertions.assertFalse(tinyGraph.includesEdge(6, 5))
		Assertions.assertFalse(tinyGraph.includesEdge(6, 6))
		tinyGraph.addVertices(listOf(7, 8, 9))
		Assertions.assertEquals(5, tinyGraph.size)
		Assertions.assertTrue(tinyGraph.includesVertex(5))
		Assertions.assertTrue(tinyGraph.includesVertex(6))
		Assertions.assertTrue(tinyGraph.includesVertex(7))
		Assertions.assertTrue(tinyGraph.includesVertex(8))
		Assertions.assertTrue(tinyGraph.includesVertex(9))
	}

	/** Test: Check that a simple graph can be reversed successfully. */
	@Test
	fun reverseGraph()
	{
		val tinyGraph = Graph<Int>()
		tinyGraph.addVertices(listOf(1, 2, 3, 4))
		tinyGraph.addEdge(1, 2)
		tinyGraph.addEdge(1, 3)
		tinyGraph.addEdge(2, 4)
		tinyGraph.addEdge(3, 4)

		val reverse = tinyGraph.reverse
		Assertions.assertTrue(reverse.includesEdge(2, 1))
		Assertions.assertTrue(reverse.includesEdge(3, 1))
		Assertions.assertTrue(reverse.includesEdge(4, 2))
		Assertions.assertTrue(reverse.includesEdge(4, 3))

		// Make sure the original was not destroyed.
		Assertions.assertTrue(tinyGraph.includesEdge(1, 2))
		// Also make sure the reverse doesn't include the forward edges.
		Assertions.assertFalse(reverse.includesEdge(1, 2))

		// Add 1->4 in the original.
		tinyGraph.addEdge(1, 4)
		Assertions.assertTrue(tinyGraph.includesEdge(1, 4))
		Assertions.assertFalse(tinyGraph.includesEdge(4, 1))
		// ...and make sure the reverse graph is unaffected.
		Assertions.assertFalse(reverse.includesEdge(1, 4))
		Assertions.assertFalse(reverse.includesEdge(4, 1))
	}

	/**
	 * Test: Check invalid addVertex().
	 */
	@Test
	fun testInvalidAddVertex()
	{
		val tinyGraph = Graph<Int>()
		tinyGraph.addVertex(5)
		Assertions.assertThrows(GraphPreconditionFailure::class.java) {
			tinyGraph.addVertex(5)
		}
	}

	/**
	 * Test: Check that includeVertex() works even if the element is present.
	 */
	@Test
	fun testIncludeVertex()
	{
		val tinyGraph = Graph<Int>()
		tinyGraph.addVertex(5)
		Assertions.assertTrue(tinyGraph.includesVertex(5))
		tinyGraph.includeVertex(5)
		Assertions.assertTrue(tinyGraph.includesVertex(5))
	}

	/**
	 * Test: Check adding/removing edges.
	 */
	@Test
	fun testAddAndRemovingEdges()
	{
		val tinyGraph = Graph<Int>()
		tinyGraph.addVertex(5)
		tinyGraph.addVertex(6)
		tinyGraph.addEdge(5, 6)
		Assertions.assertTrue(tinyGraph.includesEdge(5, 6))
		tinyGraph.includeEdge(5, 6)
		Assertions.assertTrue(tinyGraph.includesEdge(5, 6))
		tinyGraph.removeEdge(5, 6)
		Assertions.assertFalse(tinyGraph.includesEdge(5, 6))
		tinyGraph.excludeEdge(5, 6)
		Assertions.assertFalse(tinyGraph.includesEdge(5, 6))
		tinyGraph.addEdge(5, 6)
		Assertions.assertTrue(tinyGraph.includesEdge(5, 6))
		tinyGraph.excludeEdge(5, 6)
		Assertions.assertFalse(tinyGraph.includesEdge(5, 6))
	}

	/**
	 * Test: Check invalid removeEdge() when neither vertex is present.
	 */
	@Test
	fun testInvalidRemoveEdgeWithNeitherVertexPresent()
	{
		val tinyGraph = Graph<Int>()
		Assertions.assertThrows(GraphPreconditionFailure::class.java) {
			tinyGraph.removeEdge(5, 6)
		}
	}

	/**
	 * Test: Check invalid removeEdge() when only the source vertex is present.
	 */
	@Test
	fun testInvalidRemoveEdgeWithOnlySourceVertexPresent()
	{
		val tinyGraph = Graph<Int>()
		tinyGraph.addVertex(5)
		Assertions.assertThrows(GraphPreconditionFailure::class.java) {
			tinyGraph.removeEdge(5, 6)
		}
	}

	/**
	 * Test: Check invalid removeEdge() when only the target vertex is present.
	 */
	@Test
	fun testInvalidRemoveEdgeWithOnlyTargetVertexPresent()
	{
		val tinyGraph = Graph<Int>()
		tinyGraph.addVertex(6)
		Assertions.assertThrows(GraphPreconditionFailure::class.java) {
			tinyGraph.removeEdge(5, 6)
		}
	}

	/**
	 * Test: Check invalid removeEdge() when both vertices are present.
	 */
	@Test
	fun testInvalidRemoveEdgeWithBothVerticesPresent()
	{
		val tinyGraph = Graph<Int>()
		tinyGraph.addVertex(5)
		tinyGraph.addVertex(6)
		Assertions.assertThrows(GraphPreconditionFailure::class.java) {
			tinyGraph.removeEdge(5, 6)
		}
	}

	/**
	 * Test: Check cyclicity detection.
	 */
	@Test
	fun testCyclicity()
	{
		val tinyGraph = Graph<Int>()
		tinyGraph.addVertex(5)
		tinyGraph.addVertex(6)
		Assertions.assertFalse(tinyGraph.isCyclic)
		tinyGraph.addEdge(5, 6)
		Assertions.assertFalse(tinyGraph.isCyclic)
		tinyGraph.addEdge(6, 5)
		Assertions.assertTrue(tinyGraph.isCyclic)
	}

	/**
	 * Test: Check the parallel DAG visit mechanism.
	 */
	@Test
	fun testSerialEnumeration()
	{
		val tinyGraph = Graph<Int>()
		val scale = 50
		for (i in 1 .. scale)
		{
			tinyGraph.addVertex(i)
		}
		for (i in 1 .. scale)
		{
			for (j in i + 1 .. scale)
			{
				if (j % i == 0)
				{
					tinyGraph.addEdge(i, j)
				}
			}
		}
		val visitedVertices = mutableListOf<Int>()
		val mainThread = Thread.currentThread()
		tinyGraph.parallelVisit { vertex: Int, completion: Function0<Unit> ->
			Assertions.assertEquals(
				mainThread,
				Thread.currentThread()
			)
			for (previousVertex in visitedVertices)
			{
				Assertions.assertFalse(previousVertex % vertex == 0)
			}
			visitedVertices.add(vertex)
			completion.invoke()
		}
		Assertions.assertEquals(scale, visitedVertices.size)
		Assertions.assertEquals(scale, visitedVertices.toSet().size)
	}
}
