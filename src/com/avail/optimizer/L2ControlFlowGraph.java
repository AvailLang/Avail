/**
 * L2ControlFlowGraph.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.optimizer;

import java.util.HashSet;
import java.util.Set;

/**
 * This is a control graph.  The vertices are {@link L2BasicBlock}s, which are
 * connected via their successor and predeccessor lists.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2ControlFlowGraph
{
	/** The set of all {@link L2BasicBlock}s in this graph. */
	final Set<L2BasicBlock> basicBlocks = new HashSet<>();

	/**
	 * The {@link L2BasicBlock} that acts as the starting point of this control
	 * flow graph.
	 */
	final L2BasicBlock initialBlock;

	/**
	 * Create a new {@link L2BasicBlock} in this control flow graph.  Don't
	 * connect it to anything yet.
	 *
	 * @param blockName The descriptive name of the basic block.
	 * @return The new basic block.
	 */
	L2BasicBlock createBasicBlock (final String blockName)
	{
		final L2BasicBlock newBlock = new L2BasicBlock(blockName);
		basicBlocks.add(newBlock);
		return newBlock;
	}

	/**
	 * Create a new control flow graph starting at the given initial {@link
	 * L2BasicBlock}.
	 *
	 * @param initialBlock
	 *        The basic block representing the function's entry point.
	 */
	L2ControlFlowGraph (final L2BasicBlock initialBlock)
	{
		this.initialBlock = initialBlock;
		basicBlocks.add(initialBlock);
	}
}
