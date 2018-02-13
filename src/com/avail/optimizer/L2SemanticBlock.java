/*
 * L2SemanticBlock.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.optimizer.values.Frame;
import com.avail.optimizer.values.L2SemanticValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static com.avail.descriptor.AvailObject.multiplier;

/**
 * This represents the purpose for generating an {@link L2BasicBlock}.  It's
 * somewhat analogous to {@link L2SemanticValue}s, and as such it's tied to the
 * concept of a {@link Frame}.  It's also associated with a particular Level One
 * pc, but that's not fine-grained enough to identify it if an {@link
 * L1Operation} was translated in such a way that multiple blocks were
 * generated. In such a case, additional information must be provided to
 * distinguish the role of the block, say for inlining a polymorphic dispatch's
 * target definition.
 *
 * <p>The {@code L2SemanticBlock}s must be {@link Comparable} to each other
 * to ensure predecessors are always generated before successors.  To simplify
 * the queueing of the generation actions, these must form a full order that's
 * arbitrarily but stably embedded within the directed acyclic graph formed by
 * the actual dependency.</p>
 *
 * <p>The code splitting algorithm detects when a branch is being generated, and
 * looks at the dag of phi operations that provided previous, potentially
 * stronger-typed values.  If any such values would lead to the branch being
 * always-true or always-false, we consider the edge that led into the block
 * with that phi function, without exploring any earlier in that branch of
 * history.  This is the exact point where the useful information was destroyed
 * by merging control flow.  We redirect the edge to a more specialized {@link
 * L2SemanticBlock}, one that encodes the type restriction.  Since that
 * point occurs earlier in the graph than the targets of the branch that we were
 * about to create, the entire subgraph from the split point up to the branch
 * targets will be generated before the relevant branch targets, thereby
 * ensuring topological order of block generation.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2SemanticBlock
{
	/**
	 * The {@link Frame} in which the basic block occurs.
	 */
	public final Frame frame;

	/**
	 * The pc within the frame at which this basic block exists.  The {@link
	 * #frame} and {@code pc} do not necessarily uniquely define a semantic
	 * basic block.
	 */
	public final int pc;

	/** The eagerly computed hash code for this semantic block. */
	private final int hash;

	/**
	 * The collection of {@link L2SemanticValue}s and the {@link
	 * TypeRestriction}s that help identify a particular code-split version of
	 * an actual {@link L2BasicBlock}.  This is immutable.
	 *
	 * <p>Note that the restrictions aren't just what was requested by a
	 * downstream branch trying to elide itself through splitting; they're the
	 * actual restrictions on the registers bound to the semantic values at this
	 * point.</p>
	 *
	 * <p>Also note that the only entries are for the registers which are being
	 * restricted for code splitting.</p>
	 */
	public final Map<L2SemanticValue, TypeRestriction<?>> requestedRestrictions;

	/**
	 * Construct a new {@code L2SemanticBlock}.
	 *
	 * @param frame
	 *        The {@link Frame} for which this block is generated
	 * @param pc
	 *        The Level One program counter within the frame's code at which
	 *        this block is generated.
	 * @param requestedRestrictions
	 *        A map of semantic values and their type restrictions, for which
	 *        code-splitting is occurring.
	 */
	L2SemanticBlock (
		final Frame frame,
		final int pc,
		final Map<L2SemanticValue, TypeRestriction<?>> requestedRestrictions)
	{
		this.frame = frame;
		this.pc = pc;
		this.requestedRestrictions = new HashMap<>(requestedRestrictions);

		int h = pc;
		for (final Entry<L2SemanticValue, TypeRestriction<?>> entry
			: this.requestedRestrictions.entrySet())
		{
			h ^= entry.getKey().hashCode() + entry.getValue().hashCode();
		}
		h *= multiplier;
		h += frame.hashCode();
		this.hash = h;
	}

	@Override
	public boolean equals (final Object obj)
	{
		if (!(obj instanceof L2SemanticBlock))
		{
			return false;
		}
		final L2SemanticBlock block = (L2SemanticBlock) obj;
		return hash == block.hash
			&& frame.equals(block.frame)
			&& pc == block.pc
			&& requestedRestrictions.equals(block.requestedRestrictions);
	}

	@Override
	public int hashCode ()
	{
		return hash;
	}

	@Override
	public String toString ()
	{
		return "SemanticBlock(" + frame + "@" + pc + ")";
	}
}
