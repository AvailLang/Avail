/*
 * DataCouplingMode.kt
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

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.manifest.L2Liveness
import avail.optimizer.values.L2SemanticValue
import avail.utility.intersects

/**
 * Whether [L2SemanticValue]s or the underlying [L2Register]s should be
 * considered when following data flow to determine liveness.
 *
 * @property considersRegisters
 *   Answer whether [L2Register]s should be tracked by this policy.
 * @property considersSemanticValues
 *   Answer whether [L2SemanticValue]s should be tracked by this policy.
 *
 * @constructor
 *   Initialize an instance of this enumeration.
 */
enum class DataCouplingMode constructor(
	val considersRegisters: Boolean,
	val considersSemanticValues: Boolean)
{
	/**
	 * The liveness analyses should consider the flows through
	 * [L2SemanticValue]s.
	 */
	FOLLOW_SEMANTIC_VALUES(false, true),

	/**
	 * [L2SemanticValue]s can be ignored, and only [L2Register]s should be
	 * considered for the liveness analysis.
	 *
	 * Additionally, this mode is used after constant substitution, so we should
	 * exclude any registers that are constant reads (which will have no
	 * definitions anyhow).
	 */
	FOLLOW_REGISTERS(true, false),

	/**
	 * Both [L2SemanticValue]s and [L2Register]s should be traced for liveness.
	 */
	FOLLOW_SEMANTIC_VALUES_AND_REGISTERS(true, true)

	;

	/**
	 * The current state of [liveness] reflects the registers and/or semantic
	 * values that are live after the given instruction.  If the instruction has
	 * a side effect or if it produces any of the live values, remove the
	 * produced values from liveness, add any values consumed by the
	 * instruction, and answer `true` (to keep the instruction).  Otherwise
	 * answer `false`.
	 *
	 * As a simplifying assumption, pretend an altersControlFlow instruction at
	 * the end of the block populates *all* of the entities that are visible
	 * along any of its successor edges.  This is true of semantic values in
	 * SSA, and after phi move substitution.  It's also true of registers before
	 * coloring.
	 *
	 * @param instruction
	 *   The [L2Instruction] to analyze.
	 * @return
	 *   Whether the instruction should be kept, having already propagated
	 *   liveness changes if `true`.
	 */
	fun visitInstruction(
		instruction: L2Instruction,
		liveness: L2Liveness
	): Boolean
	{
		assert(instruction !is L2_PHI<*>)
		var keep = instruction.hasSideEffect
		if (!keep && considersRegisters)
		{
			keep = instruction.writeOperands.any { write ->
				val reg = write.register()
				reg in liveness.sometimesLiveInRegisters
					|| reg in liveness.alwaysLiveInRegisters
			}
		}
		if (!keep && considersSemanticValues)
		{
			keep = instruction.writeOperands.any { write ->
				val values = write.semanticValues()
				values.intersects(liveness.sometimesLiveInSemanticValues)
					|| values.intersects(liveness.alwaysLiveInSemanticValues)
			}
		}
		if (!keep) return false

		// The instruction should be kept, so apply its writes and reads.
		instruction.writeOperands.forEach { write ->
			if (considersRegisters)
			{
				val reg = write.register()
				liveness.remove(reg)
			}
			if (considersSemanticValues)
			{
				val values = write.semanticValues()
				liveness.removeAll(values)
			}
		}
		instruction.readOperands.forEach { read ->
			if (!read.register().isConstant)
			{
				if (considersRegisters)
				{
					liveness.add(read.register())
				}
				if (considersSemanticValues)
				{
					liveness.add(read.semanticValue())
				}
			}
		}
		return true
	}

	/**
	 * The current state of [livenesses] reflect the registers and/or semantic
	 * values that are live after the given phi instruction, with subsequent
	 * phi instructions' effects applied.  Update each liveness with information
	 * specific to the corresponding incoming edge for the phi.
	 *
	 * @param phi
	 *   The [L2_PHI] instruction to analyze.
	 * @param livenesses
	 *   A [List] of [L2Liveness]es corresponding to the [L2_PHI.sources].
	 */
	fun visitPhi(
		phi: L2_PHI<*>,
		livenesses: List<L2Liveness>)
	{
		assert(considersRegisters && considersSemanticValues)
		val destinationReg = phi.destination.register()
		val destinationValues = phi.destination.semanticValues()
		phi.sources.elements.zip(livenesses).forEach { (source, liveness) ->
			liveness.remove(destinationReg)
			liveness.removeAll(destinationValues)

			if (!source.isConstantRead)
			{
				liveness.add(source.register())
				liveness.add(source.semanticValue())
			}
		}
	}
}
