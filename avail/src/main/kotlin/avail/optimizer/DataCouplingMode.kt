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
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.values.L2SemanticValue

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
	@Suppress("unused")
	FOLLOW_SEMANTIC_VALUES(false, true)
	{
		override fun addEntitiesFromRead(
			readOperand: L2ReadOperand<*>,
			accumulatingSet: MutableSet<L2Entity<*>>)
		{
			accumulatingSet.add(readOperand.semanticValue())
		}

		override fun addEntitiesFromWrite(
			writeOperand: L2WriteOperand<*>,
			accumulatingSet: MutableSet<L2Entity<*>>)
		{
			accumulatingSet.addAll(writeOperand.semanticValues())
		}
	},

	/**
	 * [L2SemanticValue]s can be ignored, and only [L2Register]s should be
	 * considered for the liveness analysis.
	 */
	FOLLOW_REGISTERS(true, false)
	{
		override fun addEntitiesFromRead(
			readOperand: L2ReadOperand<*>,
			accumulatingSet: MutableSet<L2Entity<*>>)
		{
			accumulatingSet.add(readOperand.register())
		}

		override fun addEntitiesFromWrite(
			writeOperand: L2WriteOperand<*>,
			accumulatingSet: MutableSet<L2Entity<*>>)
		{
			accumulatingSet.add(writeOperand.register())
		}
	},

	/**
	 * Both [L2SemanticValue]s and [L2Register]s should be traced for liveness.
	 */
	FOLLOW_SEMANTIC_VALUES_AND_REGISTERS(true, true)
	{
		override fun addEntitiesFromRead(
			readOperand: L2ReadOperand<*>,
			accumulatingSet: MutableSet<L2Entity<*>>)
		{
			accumulatingSet.add(readOperand.semanticValue())
			accumulatingSet.add(readOperand.register())
		}

		override fun addEntitiesFromWrite(
			writeOperand: L2WriteOperand<*>,
			accumulatingSet: MutableSet<L2Entity<*>>)
		{
			accumulatingSet.addAll(writeOperand.semanticValues())
			accumulatingSet.add(writeOperand.register())
		}
	};

	/**
	 * Extract each [L2Entity] that this policy is concerned with from
	 * the given [L2ReadOperand].
	 *
	 * @param readOperand
	 *   The [L2ReadOperand] to examine.
	 * @param accumulatingSet
	 *   A [Set] into which to accumulate the read entities.
	 */
	abstract fun addEntitiesFromRead(
		readOperand: L2ReadOperand<*>,
		accumulatingSet: MutableSet<L2Entity<*>>)

	/**
	 * Extract each [L2Entity] that this policy is concerned with from the given
	 * [L2WriteOperand].
	 *
	 * @param writeOperand
	 *   The [L2WriteOperand] to examine.
	 * @param accumulatingSet
	 *   A [Set] into which to accumulate the written entities.
	 */
	abstract fun addEntitiesFromWrite(
		writeOperand: L2WriteOperand<*>,
		accumulatingSet: MutableSet<L2Entity<*>>)

	/**
	 * Extract each relevant [L2Entity] consumed by the given [L2ReadOperand].
	 *
	 * @param readOperand
	 *   The [L2ReadOperand] to examine.
	 * @return
	 *   Each [L2Entity] read by the [L2ReadOperand], and which the policy deems
	 *   relevant.
	 */
	fun readEntitiesOf(readOperand: L2ReadOperand<*>): Set<L2Entity<*>>
	{
		val entitiesRead = mutableSetOf<L2Entity<*>>()
		addEntitiesFromRead(readOperand, entitiesRead)
		return entitiesRead
	}

	/**
	 * Extract each relevant [L2Entity] produced by the given [L2WriteOperand].
	 *
	 * @param writeOperand
	 *   The [L2WriteOperand] to examine.
	 * @return
	 *   Each [L2Entity] written by the [L2WriteOperand], and which the policy
	 *   deems relevant.
	 */
	@Suppress("unused")
	fun writeEntitiesOf(writeOperand: L2WriteOperand<*>): Set<L2Entity<*>>
	{
		val entitiesWritten = mutableSetOf<L2Entity<*>>()
		addEntitiesFromWrite(writeOperand, entitiesWritten)
		return entitiesWritten
	}

	/**
	 * Extract each relevant [L2Entity] consumed by the given [L2Instruction].
	 *
	 * @param instruction
	 *   The [L2Instruction] to examine.
	 * @return
	 *   Each [L2Entity] read by the instruction, and which the policy deems
	 *   relevant.
	 */
	fun readEntitiesOf(instruction: L2Instruction): Set<L2Entity<*>>
	{
		val entitiesRead = mutableSetOf<L2Entity<*>>()
		instruction.readOperands
			.forEach { addEntitiesFromRead(it, entitiesRead) }
		return entitiesRead
	}

	/**
	 * Extract each relevant [L2EntityAndKind] produced by the given
	 * [L2Instruction].
	 *
	 * @param instruction
	 *   The [L2Instruction] to examine.
	 * @return
	 *   Each [L2EntityAndKind] written by the instruction, and which the policy
	 *   deems relevant.
	 */
	fun writeEntitiesOf(instruction: L2Instruction): Set<L2Entity<*>>
	{
		val entitiesWritten = mutableSetOf<L2Entity<*>>()
		instruction.writeOperands
			.forEach { addEntitiesFromWrite(it, entitiesWritten) }
		return entitiesWritten
	}
}
