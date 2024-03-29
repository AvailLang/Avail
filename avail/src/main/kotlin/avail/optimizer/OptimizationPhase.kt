/*
 * OptimizationPhase.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.optimizer

import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_MULTIWAY_JUMP
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL
import avail.optimizer.DataCouplingMode.FOLLOW_SEMANTIC_VALUES_AND_REGISTERS
import avail.optimizer.L2ControlFlowGraph.StateFlag
import avail.optimizer.annotations.Clears
import avail.optimizer.annotations.Requires
import avail.optimizer.annotations.RequiresNot
import avail.optimizer.annotations.Sets
import avail.optimizer.jvm.JVMTranslator
import avail.performance.Statistic
import avail.performance.StatisticReport.L2_OPTIMIZATION_TIME
import java.lang.reflect.Field
import java.util.Collections.addAll
import kotlin.reflect.KClass

/**
 * The collection of phases of L2 optimization, in sequence.
 *
 * @property action
 *   The optimization action to perform for this pass.
 *
 * @constructor
 * Create the enumeration value.
 *
 * @param action
 *   The action to perform for this pass.
 */
internal enum class OptimizationPhase constructor(
	internal val action: L2Optimizer.() -> Unit)
{
	/**
	 * Start by eliminating debris created during the initial L1 → L2
	 * translation.
	 */
	REMOVE_DEAD_CODE_1({ removeDeadCode(FOLLOW_SEMANTIC_VALUES_AND_REGISTERS) }),

	/**
	 * Transform into SSA edge-split form, to avoid inserting redundant
	 * phi-moves.
	 */
	BECOME_EDGE_SPLIT_SSA({ transformToEdgeSplitSSA() }),

	/**
	 * Try to move any side-effect-less instructions to later points in the
	 * control flow graph.  If such an instruction defines a register that's
	 * used in the same basic block, don't bother moving it.  Also don't
	 * attempt to move it if it's always-live-in at each successor block,
	 * since the point of moving it forward is to avoid inessential
	 * computations.
	 *
	 * Note that this breaks SSA by duplicating defining instructions.
	 * It also always recomputes liveness after each change, so there's no
	 * need to recompute it after this phase.
	 */
	POSTPONE_CONDITIONALLY_USED_VALUES_1({ postponeConditionallyUsedValues() }),

	/**
	 * Postponing conditionally used values can introduce idempotent
	 * redundancies, which are dead code.  Remove them for clarity before we
	 * replace placeholder instructions.
	 */
	REMOVE_DEAD_CODE_AFTER_POSTPONEMENTS(
		{ removeDeadCode(FOLLOW_SEMANTIC_VALUES_AND_REGISTERS) }),

	/**
	 * If there are any [L2_VIRTUAL_CREATE_LABEL] instructions still extant,
	 * replace them with the rather complex code that will reify the caller if
	 * necessary, and create a label continuation.
	 *
	 * There are other placeholder instructions that get transformed here as
	 * well, such as [L2_MULTIWAY_JUMP].
	 */
	REPLACE_PLACEHOLDER_INSTRUCTIONS({ replacePlaceholderInstructions() }),

	/**
	 * Placeholder instructions may have been replaced with new subgraphs of
	 * generated code.  Some of that might be dead, so clean it up, otherwise
	 * the [L2Optimizer.postponeConditionallyUsedValues] might get upset about
	 * an instruction being in a place with no downstream uses.
	 */
	REMOVE_DEAD_CODE_AFTER_REPLACEMENTS(
		{ removeDeadCode(FOLLOW_SEMANTIC_VALUES_AND_REGISTERS) }),

	/**
	 * If [REPLACE_PLACEHOLDER_INSTRUCTIONS] made any changes, give one more try
	 * at pushing conditionally used values.  Otherwise do nothing.
	 */
	POSTPONE_CONDITIONALLY_USED_VALUES_2({ postponeConditionallyUsedValues() }),

	/**
	 * Replace every use of a constant register with a fresh register with no
	 * defining write.  The code generator will notice these are constants, and
	 * will fetch the constant itself at each place it is read.
	 */
	REPLACE_CONSTANT_REGISTERS({ replaceConstantRegisters() }),

	/**
	 * Insert phi moves along preceding edges.  This requires the CFG to be in
	 * edge-split form, although strict SSA isn't required.
	 */
	INSERT_PHI_MOVES({ insertPhiMoves() }),

	/**
	 * Remove constant moves made unnecessary by the introduction of new
	 * constant moves after phis (the ones that are constant-valued).
	 */
	REMOVE_DEAD_CODE_AFTER_PHI_MOVES(
		{ removeDeadCode(FOLLOW_SEMANTIC_VALUES_AND_REGISTERS, false) }),

	/**
	 * Compute the register-coloring interference graph while we're just out of
	 * SSA form – phis have been replaced by moves on incoming edges.
	 */
	COMPUTE_INTERFERENCE_GRAPH({ computeInterferenceGraph() }),

	/**
	 * Color all registers, using the previously computed interference graph.
	 * This creates a dense finalIndex numbering for the registers in such a way
	 * that no two registers that have to maintain distinct values at the same
	 * time will have the same number.
	 */
	COALESCE_REGISTERS_IN_NONINTERFERING_MOVES(
		{ coalesceNoninterferingMoves() }),

	/** Compute and assign final register colors. */
	ASSIGN_REGISTER_COLORS({ computeColors() }),

	/**
	 * Create a replacement register for each used color (of each kind).
	 * Transform each reference to an old register into a reference to the
	 * replacement, updating structures as needed.
	 */
	REPLACE_REGISTERS_BY_COLOR({ replaceRegistersByColor() }),

	/**
	 * Remove any remaining moves between two registers of the same color.
	 */
	REMOVE_SAME_COLOR_MOVES({ removeSameColorMoves() }),

	/**
	 * Every L2PcOperand that leads to an L2_JUMP should now be redirected
	 * to the target of the jump (transitively, if the jump leads to another
	 * jump).  We specifically do this after inserting phi moves to ensure
	 * we don't jump past irremovable phi moves.
	 */
	ADJUST_EDGES_LEADING_TO_JUMPS({ adjustEdgesLeadingToJumps() }),

	/**
	 * Having adjusted edges to avoid landing on L2_JUMPs, some blocks may
	 * have become unreachable.
	 */
	REMOVE_UNREACHABLE_BLOCKS({ removeUnreachableBlocks() }),

	/**
	 * Choose an order for the blocks.  This isn't important while we're
	 * interpreting L2Chunks, but it will ultimately affect the quality of
	 * JVM translation.  Prefer to have the target block of an unconditional
	 * jump to follow the jump, since final code generation elides the jump.
	 */
	ORDER_BLOCKS({ orderBlocks() }),

	/**
	 * Recompute liveness information about all registers on each edge.  This
	 * information is only needed by the [JVMTranslator], to determine which
	 * registers need to be saved and restored around pairs of
	 * [L2_SAVE_ALL_AND_PC_TO_INT] and [L2_ENTER_L2_CHUNK] instructions.  Make
	 * sure this phase happens after any phases that might regenerate the
	 * [L2ControlFlowGraph], since this information is not preserved across such
	 * a regeneration.
	 */
	COMPUTE_LIVENESS_AT_EDGES_2({ computeLivenessAtEachEdge() });

	// Additional optimization ideas:
	//		-Strengthen the types of all registers and register uses.
	//		-Ask instructions to regenerate if they want.
	//		-When optimizing, keep track of when a TypeRestriction on a phi
	//		  register is too weak to qualify, but the types of some of the phi
	//		  source registers would qualify it for a reasonable expectation of
	//		  better performance.  Write a hint into such phis.  If we have a
	//		  high enough requested optimization level, apply code-splitting.
	//		  The block that defines that phi can be duplicated for each
	//		  interesting incoming edge.  That way the duplicated blocks will
	//		  get more specific types to work with.
	//		-Splitting for int32s.
	//		-Leverage more inter-primitive identities.

	/** The [Statistic] for tracking this pass's cost. */
	val stat: Statistic = Statistic(L2_OPTIMIZATION_TIME, name)

	/** The [StateFlag]s to require to already be set as preconditions. */
	private val requiresFlags = mutableListOf<KClass<out StateFlag>>()

	/** The [StateFlag]s that should already be clear as preconditions. */
	private val requiresNotFlags= mutableListOf<KClass<out StateFlag>>()

	/** The [StateFlag]s to set after this phase. */
	private val setsFlags = mutableListOf<KClass<out StateFlag>>()

	/** The [StateFlag]s to clear after this phase. */
	private val clearsFlags = mutableListOf<KClass<out StateFlag>>()

	/**
	 * Perform this phase's action.  Also check precondition [StateFlag]s and
	 * set or clear them as indicated by this phase's annotations.
	 *
	 * @param optimizer
	 *   The optimizer for which to run this phase.
	 */
	fun run(optimizer: L2Optimizer)
	{
		optimizer.check(requiresFlags)
		optimizer.checkNot(requiresNotFlags)
		optimizer.action()
		optimizer.set(setsFlags)
		optimizer.clear(clearsFlags)
	}

	init
	{
		val enumMirror: Field =
			try
			{
				javaClass.getField(name)
			}
			catch (e: NoSuchFieldException)
			{
				throw RuntimeException(
					"Enum class didn't recognize its own instance",
					e)
			}
		enumMirror.getAnnotation(Requires::class.java)?.let {
			addAll(requiresFlags, *it.value)
		}
		enumMirror.getAnnotation(RequiresNot::class.java)?.let {
			addAll(requiresNotFlags, *it.value)
		}
		enumMirror.getAnnotation(Sets::class.java)?.let {
			addAll(setsFlags, *it.value)
		}
		enumMirror.getAnnotation(Clears::class.java)?.let {
			addAll(clearsFlags, *it.value)
		}
	}
}
