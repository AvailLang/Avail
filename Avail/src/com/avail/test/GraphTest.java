/**
 * GraphTest.java
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

package com.avail.test;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.*;
import com.avail.utility.Graph;
import com.avail.utility.Graph.GraphPreconditionFailure;

/**
 * Basic functionality test of {@link Graph}s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class GraphTest
{
	/**
	 * Test: Check properties of the empty graph.
	 */
	@Test
	public void emptyGraphTest ()
	{
		final Graph<Integer> emptyGraph = new Graph<>();
		assertFalse(emptyGraph.includesVertex(5));
		assertFalse(emptyGraph.includesVertex(7));
		assertEquals(0, emptyGraph.size());
	}

	/**
	 * Test: Check properties of some very small graphs.
	 */
	@Test
	public void tinyGraphTest ()
	{
		final Graph<Integer> tinyGraph = new Graph<>();
		tinyGraph.addVertex(5);
		assertTrue(tinyGraph.includesVertex(5));
		assertFalse(tinyGraph.includesVertex(6));
		assertFalse(tinyGraph.includesEdge(5, 5));
		tinyGraph.addVertex(6);
		assertFalse(tinyGraph.includesEdge(5, 6));
		assertFalse(tinyGraph.includesEdge(6, 5));
		assertEquals(2, tinyGraph.size());

		tinyGraph.addEdge(5, 6);
		assertFalse(tinyGraph.includesEdge(5, 5));
		assertTrue(tinyGraph.includesEdge(5, 6));
		assertFalse(tinyGraph.includesEdge(6, 5));
		assertFalse(tinyGraph.includesEdge(6, 6));

		tinyGraph.addVertices(Arrays.asList(7, 8, 9));
		assertEquals(5, tinyGraph.size());
		assertTrue(tinyGraph.includesVertex(5));
		assertTrue(tinyGraph.includesVertex(6));
		assertTrue(tinyGraph.includesVertex(7));
		assertTrue(tinyGraph.includesVertex(8));
		assertTrue(tinyGraph.includesVertex(9));
	}

	/**
	 * Test: Check invalid addVertex().
	 */
	@Test(expected = GraphPreconditionFailure.class)
	public void testInvalidAddVertex ()
	{
		final Graph<Integer> tinyGraph = new Graph<>();
		tinyGraph.addVertex(5);
		tinyGraph.addVertex(5);
	}

	/**
	 * Test: Check includeVertex().
	 */
	@Test
	public void testIncludeVertex ()
	{
		final Graph<Integer> tinyGraph = new Graph<>();
		tinyGraph.addVertex(5);
		tinyGraph.includeVertex(5);
	}

	/**
	 * Test: Check adding/removing edges.
	 */
	@Test
	public void testAddAndRemovingEdges ()
	{
		final Graph<Integer> tinyGraph = new Graph<>();
		tinyGraph.addVertex(5);
		tinyGraph.addVertex(6);
		tinyGraph.addEdge(5, 6);
		assertTrue(tinyGraph.includesEdge(5, 6));
		tinyGraph.includeEdge(5, 6);
		assertTrue(tinyGraph.includesEdge(5, 6));
		tinyGraph.removeEdge(5, 6);
		assertFalse(tinyGraph.includesEdge(5, 6));
		tinyGraph.excludeEdge(5, 6);
		assertFalse(tinyGraph.includesEdge(5, 6));
		tinyGraph.addEdge(5, 6);
		assertTrue(tinyGraph.includesEdge(5, 6));
		tinyGraph.excludeEdge(5, 6);
		assertFalse(tinyGraph.includesEdge(5, 6));
	}

	/**
	 * Test: Check invalid removeEdge() when neither vertex is present.
	 */
	@Test(expected = GraphPreconditionFailure.class)
	public void testInvalidRemoveEdgeWithNeitherVertexPresent ()
	{
		final Graph<Integer> tinyGraph = new Graph<>();
		tinyGraph.removeEdge(5, 6);
	}

	/**
	 * Test: Check invalid removeEdge() when only the source vertex is present.
	 */
	@Test(expected = GraphPreconditionFailure.class)
	public void testInvalidRemoveEdgeWithOnlySourceVertexPresent ()
	{
		final Graph<Integer> tinyGraph = new Graph<>();
		tinyGraph.addVertex(5);
		tinyGraph.removeEdge(5, 6);
	}

	/**
	 * Test: Check invalid removeEdge() when only the target vertex is present.
	 */
	@Test(expected = GraphPreconditionFailure.class)
	public void testInvalidRemoveEdgeWithOnlyTargetVertexPresent ()
	{
		final Graph<Integer> tinyGraph = new Graph<>();
		tinyGraph.addVertex(6);
		tinyGraph.removeEdge(5, 6);
	}

	/**
	 * Test: Check invalid removeEdge() when both vertices are present.
	 */
	@Test(expected = GraphPreconditionFailure.class)
	public void testInvalidRemoveEdgeWithBothVerticesPresent ()
	{
		final Graph<Integer> tinyGraph = new Graph<>();
		tinyGraph.addVertex(5);
		tinyGraph.addVertex(6);
		tinyGraph.removeEdge(5, 6);
	}
}
