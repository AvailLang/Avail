/*
 * OptimizationPhase.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL;
import com.avail.optimizer.L2ControlFlowGraph.StateFlag;
import com.avail.optimizer.L2ControlFlowGraph.StateFlag.HAS_ELIMINATED_PHIS;
import com.avail.optimizer.L2ControlFlowGraph.StateFlag.IS_EDGE_SPLIT;
import com.avail.optimizer.L2ControlFlowGraph.StateFlag.IS_SSA;
import com.avail.optimizer.annotations.Clears;
import com.avail.optimizer.annotations.Requires;
import com.avail.optimizer.annotations.RequiresNot;
import com.avail.optimizer.annotations.Sets;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.avail.optimizer.DataCouplingMode.FOLLOW_REGISTERS;
import static com.avail.optimizer.DataCouplingMode.FOLLOW_SEMANTIC_VALUES_AND_REGISTERS;
import static java.util.Collections.addAll;

/**
 * The collection of phases of L2 optimization, in sequence.
 */
enum OptimizationPhase
{
	/**
	 * Start by eliminating debris created during the initial L1 → L2
	 * translation.
	 */
	@Requires(IS_SSA.class)
	REMOVE_DEAD_CODE_1(
		L2Optimizer::removeDeadCode, FOLLOW_SEMANTIC_VALUES_AND_REGISTERS),

	/**
	 * Transform into SSA edge-split form, to avoid inserting redundant
	 * phi-moves.
	 */
	@Requires(IS_SSA.class)
	@Sets(IS_EDGE_SPLIT.class)
	BECOME_EDGE_SPLIT_SSA(L2Optimizer::transformToEdgeSplitSSA),

	/**
	 * Determine which registers are sometimes-live-in and/or always-live-in
	 * at each edge, in preparation for postponing instructions that don't
	 * have their outputs consumed in the same block, and aren't
	 * always-live-in in every successor.
	 */
	COMPUTE_LIVENESS_AT_EDGES(L2Optimizer::computeLivenessAtEachEdge),

	/**
	 * Try to move any side-effect-less instructions to later points in the
	 * control flow graph.  If such an instruction defines a register that's
	 * used in the same basic block, don't bother moving it.  Also don't
	 * attempt to move it if it's always-live-in at each successor block,
	 * since the point of moving it forward is to avoid inessential
	 * computations.
	 *
	 * <p>Note that this breaks SSA by duplicating defining instructions.
	 * It also always recomputes liveness after each change, so there's no
	 * need to recompute it after this phase.</p>
	 */
	@Requires(IS_EDGE_SPLIT.class)
	@Clears(IS_SSA.class)
	POSTPONE_CONDITIONALLY_USED_VALUES_1(
		L2Optimizer::postponeConditionallyUsedValues),

	/**
	 * Postponing conditionally used values can introduce idempotent
	 * redundancies, which are dead code.  Remove them for clarity before we
	 * replace placeholder instructions.
	 */
	@Requires(IS_EDGE_SPLIT.class)
	REMOVE_DEAD_CODE_AFTER_POSTPONEMENTS(
		L2Optimizer::removeDeadCode, FOLLOW_SEMANTIC_VALUES_AND_REGISTERS),

	/**
	 * If there are any {@link L2_VIRTUAL_CREATE_LABEL} instructions still
	 * extant, replace them with the rather complex code that will reify the
	 * caller if necessary, and create a label continuation.
	 */
	@Requires(IS_EDGE_SPLIT.class)
	REPLACE_PLACEHOLDER_INSTRUCTIONS(
		L2Optimizer::replacePlaceholderInstructions),

	/**
	 * Placeholder instructions may have been replaced with new subgraphs of
	 * generated code.  Some of that might be dead, so clean it up, otherwise
	 * the {@link L2Optimizer#postponeConditionallyUsedValues()} might get upset
	 * about an instruction being in a place with no downstream uses.
	 */
	@Requires(IS_EDGE_SPLIT.class)
	REMOVE_DEAD_CODE_AFTER_REPLACEMENTS(
		L2Optimizer::removeDeadCode, FOLLOW_SEMANTIC_VALUES_AND_REGISTERS),

	/**
	 * Recompute liveness information about all registers, now that dead
	 * code has been eliminated after placeholder replacements.
	 */
	COMPUTE_LIVENESS_AT_EDGES_2(L2Optimizer::computeLivenessAtEachEdge),

	/**
	 * If {@link #REPLACE_PLACEHOLDER_INSTRUCTIONS} made any changes,
	 * give one more try at pushing conditionally used values.  Otherwise do
	 * nothing.
	 */
	@Requires(IS_EDGE_SPLIT.class)
	POSTPONE_CONDITIONALLY_USED_VALUES_2(
		L2Optimizer::postponeConditionallyUsedValues),

	/**
	 * Insert phi moves along preceding edges.  This requires the CFG to be
	 * in edge-split form, although strict SSA isn't required.
	 */
	@Requires(IS_EDGE_SPLIT.class)
	@Sets(HAS_ELIMINATED_PHIS.class)
	INSERT_PHI_MOVES(L2Optimizer::insertPhiMoves),

	/**
	 * Remove constant moves made unnecessary by the introduction of new
	 * constant moves after phis (the ones that are constant-valued).
	 */
	@Requires(HAS_ELIMINATED_PHIS.class)
	REMOVE_DEAD_CODE_AFTER_PHI_MOVES(
		L2Optimizer::removeDeadCode, FOLLOW_REGISTERS),

	/**
	 * Compute the register-coloring interference graph while we're just
	 * out of SSA form – phis have been replaced by moves on incoming edges.
	 */
	COMPUTE_INTERFERENCE_GRAPH(L2Optimizer::computeInterferenceGraph),

	/**
	 * Color all registers, using the previously computed interference
	 * graph.  This creates a dense finalIndex numbering for the registers
	 * in such a way that no two registers that have to maintain distinct
	 * values at the same time will have the same number.
	 */
	COALESCE_REGISTERS_IN_NONINTERFERING_MOVES(
		L2Optimizer::coalesceNoninterferingMoves),

	/** Compute and assign final register colors. */
	ASSIGN_REGISTER_COLORS(L2Optimizer::computeColors),

	/**
	 * Create a replacement register for each used color (of each kind).
	 * Transform each reference to an old register into a reference to the
	 * replacement, updating structures as needed.
	 */
	REPLACE_REGISTERS_BY_COLOR(L2Optimizer::replaceRegistersByColor),

	/**
	 * Remove any remaining moves between two registers of the same color.
	 */
	REMOVE_SAME_COLOR_MOVES(L2Optimizer::removeSameColorMoves),

	/**
	 * Every L2PcOperand that leads to an L2_JUMP should now be redirected
	 * to the target of the jump (transitively, if the jump leads to another
	 * jump).  We specifically do this after inserting phi moves to ensure
	 * we don't jump past irremovable phi moves.
	 */
	ADJUST_EDGES_LEADING_TO_JUMPS(L2Optimizer::adjustEdgesLeadingToJumps),

	/**
	 * Having adjusted edges to avoid landing on L2_JUMPs, some blocks may
	 * have become unreachable.
	 */
	REMOVE_UNREACHABLE_BLOCKS(
		optimizer ->
		{
			optimizer.removeUnreachableBlocks();
			return Unit.INSTANCE;
		}),

	/**
	 * Choose an order for the blocks.  This isn't important while we're
	 * interpreting L2Chunks, but it will ultimately affect the quality of
	 * JVM translation.  Prefer to have the target block of an unconditional
	 * jump to follow the jump, since final code generation elides the jump.
	 */
	ORDER_BLOCKS(L2Optimizer::orderBlocks);

	// Additional optimization ideas:
	//    -Strengthen the types of all registers and register uses.
	//    -Ask instructions to regenerate if they want.
	//    -When optimizing, keep track of when a TypeRestriction on a phi
	//     register is too weak to qualify, but the types of some of the phi
	//     source registers would qualify it for a reasonable expectation of
	//     better performance.  Write a hint into such phis.  If we have a
	//     high enough requested optimization level, apply code-splitting.
	//     The block that defines that phi can be duplicated for each
	//     interesting incoming edge.  That way the duplicated blocks will
	//     get more specific types to work with.
	//    -Splitting for int32s.
	//    -Leverage more inter-primitive identities.
	//    -JVM target.

	/** The optimization action to perform for this pass. */
	private final Function1<L2Optimizer, Unit> action;

	/** The {@link Statistic} for tracking this pass's cost. */
	final Statistic stat;

	/** The {@link StateFlag}s to require to already be set as preconditions. */
	final List<Class<? extends StateFlag>> requiresFlags = new ArrayList<>();

	/** The {@link StateFlag}s that should already be clear as preconditions. */
	final List<Class<? extends StateFlag>> requiresNotFlags = new ArrayList<>();

	/** The {@link StateFlag}s to set after this phase. */
	final List<Class<? extends StateFlag>> setsFlags = new ArrayList<>();

	/** The {@link StateFlag}s to clear after this phase. */
	final List<Class<? extends StateFlag>> clearsFlags = new ArrayList<>();

	/**
	 * Create the enumeration value.
	 *
	 * @param action The action to perform for this pass.
	 */
	OptimizationPhase (final Function1<L2Optimizer, Unit> action)
	{
		this.action = action;
		this.stat = new Statistic(
			name(),
			StatisticReport.L2_OPTIMIZATION_TIME);

		final Field enumMirror;
		try
		{
			enumMirror = getClass().getField(name());
		}
		catch (final NoSuchFieldException e)
		{
			throw new RuntimeException(
				"Enum class didn't recognize its own instance",
				e);
		}

		final @Nullable Requires requiresAnnotation =
			enumMirror.getAnnotation(Requires.class);
		if (requiresAnnotation != null)
		{
			addAll(requiresFlags, requiresAnnotation.value());
		}

		final @Nullable RequiresNot requiresNotAnnotation =
			enumMirror.getAnnotation(RequiresNot.class);
		if (requiresNotAnnotation != null)
		{
			addAll(requiresNotFlags, requiresNotAnnotation.value());
		}

		final @Nullable Sets setsAnnotation =
			enumMirror.getAnnotation(Sets.class);
		if (setsAnnotation != null)
		{
			addAll(setsFlags, setsAnnotation.value());
		}

		final @Nullable Clears clearsAnnotation =
			enumMirror.getAnnotation(Clears.class);
		if (clearsAnnotation != null)
		{
			addAll(clearsFlags, clearsAnnotation.value());
		}
	}

	/**
	 * Create the enumeration value, capturing a parameter to pass to the
	 * optimization lambda.
	 *
	 * @param <V>
	 *        The type of value to pass to the action.
	 * @param action
	 *        The action to perform.
	 * @param value
	 *        The actual value to pass to the action.
	 */
	<V> OptimizationPhase (
		final Function2<L2Optimizer, V, Unit> action,
		final V value)
	{
		this(optimizer -> action.invoke(optimizer, value));
	}

	/**
	 * Perform this phase's action.  Also check precondition {@link StateFlag}s
	 * and set or clear them as indicated by this phase's annotations.
	 *
	 * @param optimizer
	 *        The optimizer for which to run this phase.
	 */
	final void run (final L2Optimizer optimizer)
	{
		optimizer.check(requiresFlags);
		optimizer.checkNot(requiresNotFlags);

		action.invoke(optimizer);

		optimizer.set(setsFlags);
		optimizer.clear(clearsFlags);
	}
}
