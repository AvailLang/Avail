/*
 * L2NewConditionalJump.kt
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
package avail.interpreter.levelTwo.operation

;

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor

/**
 * Jump to `"if satisfied"` if some condition is met, otherwise jump to
 * `"if unsatisfied"`.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Protect the constructor so the subclasses can maintain a fly-weight
 * pattern (or arguably a singleton).
 *
 * By convention, there are always 2 [targetEdges], the first of which is the
 * "taken" branch, and the second of which is the "not taken" branch.
 */
abstract class L2ConditionalJump : L2ControlFlowInstruction()
{
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		targetEdges.forEach(L2PcOperand::installCounter)
	}

	/** This instruction jumps, which counts as a side effect. */
	override val hasSideEffect: Boolean get() = true

	/**
	 * If this instruction leads to the same target block after skipping all
	 * blocks that only contain jumps, then replace it with a jump to that block
	 * and answer `true`, otherwise answer `false`.
	 *
	 * @param generator
	 *   Where to write the jump if the target edges are equivalent.
	 * @return
	 *   Whether an [L2_JUMP] was emitted.
	 */
	fun replaceWithJumpIfPossible(generator: L2GeneratorInterface): Boolean
	{
		// If optimizations have caused the branches to go to the same place,
		// eliminate the branch entirely.
		val allTargets = targetEdges
			.mapTo(mutableSetOf(), L2PcOperand::targetBlockSkippingBareJumps)
			.distinct()
		allTargets.singleOrNull()?.let {
			val jump = L2_JUMP(
				edgeTo(targetEdges.first().targetBlock(), "elided branch"))
			println("Reduced jump to $jump")
			jump.run {
				generator.emitTransformedInstruction()
			}
			return true
		}
		return false
	}

	override fun L2Regenerator.generateReplacement(
		originalInstruction: L2Instruction)
	{
		// Determine if all edges lead to the same target block through chains
		// of jumps in blocks that have no other instructions.  If so, just emit
		// a jump to that target.  Note that we don't have to do anything to
		// preserve edge-split SSA, since the new fan-out will be 1.
		//
		// Note that this should only be done *after* code splitting, or there
		// may be missed opportunities for leveraging the narrower restrictions.
		val edgesToOriginalTargets = mutableMapOf<L2PcOperand, L2BasicBlock>()
		if (originalInstruction is L2ConditionalJump)
		{
			originalInstruction.targetEdges.forEach { edge ->
				var targetBlock: L2BasicBlock = edge.targetBlock()
				while (true)
				{
					if (targetBlock.instructions().size != 1) break
					val jump = targetBlock.finalInstruction()
					if (jump !is L2_JUMP) break
					targetBlock = jump.target.targetBlock()
				}
				edgesToOriginalTargets[edge] = targetBlock
			}
			// Just to keep it simple, only remove the multi-way jump if *all*
			// of the edges lead to the same ultimate target block.
			if (edgesToOriginalTargets.values.distinct().size == 1)
			{
				// Because the new fan-out is 1, we don't have to do anything to
				// preserve edge-split form.  Note that we jump to one (any) of
				// the new instruction's ultimate targets.
				// We *must not* jump to one of the direct targets, since there
				// will may be restrictions captuured along the subsequent old
				// edges that *will not hold* if we replace this branch with a
				// singular jump.  Jumping to the (transformation of the) common
				// *ultimate* target should be safe.
				val oldTarget = edgesToOriginalTargets.values.first()
				val newTarget = mapBlock(oldTarget)
				jumpTo(newTarget)
				return
			}
		}
		generateConditionalReplacement(originalInstruction)
	}

	/**
	 * In order for subclasses to always check for branches that always go to
	 * the same ultimate target, and turn them into a jump, we make
	 * [generateReplacement] be final in this class, but allow the herein
	 * introduced [generateConditionalReplacement] be open and called by the
	 * can't-collapse-to-a-jump case in [generateReplacement].  A default
	 * implementation is provided that uses the super [generateReplacement].
	 *
	 * @receiver
	 *   The [L2GeneratorInterface] on which to write instructions.
	 * @param originalInstruction
	 *   The [L2Instruction] on which the receiver was based.
	 */
	open fun L2GeneratorInterface.generateConditionalReplacement(
		originalInstruction: L2Instruction)
	{
		emitTransformedInstruction()
	}

	companion object
	{
		/**
		 * Emit a conditional branch, including an increment for the counters
		 * along each path.
		 *
		 * @param translator
		 *   The [JVMTranslator] controlling code generation.
		 * @param method
		 *   The [MethodVisitor] on which to write the instructions.
		 * @param instruction
		 *   The [L2Instruction] causing this code generation.
		 * @param opcode
		 *   The Java bytecode to emit for the branch instruction.
		 * @param conditionHolds
		 *   Where to jump if the condition holds.
		 * @param conditionDoesNotHold
		 *   Where to jump if the condition does not hold.
		 */
		@JvmStatic
		protected fun emitBranch(
			translator: JVMTranslator,
			method: MethodVisitor,
			instruction: L2Instruction,
			opcode: Int,
			conditionHolds: L2PcOperand,
			conditionDoesNotHold: L2PcOperand)
		{
			translator.branch(
				method,
				instruction,
				opcode,
				conditionHolds,
				conditionDoesNotHold,
				conditionHolds.counter!!,
				conditionDoesNotHold.counter!!)
		}
	}
}
