/*
 * DataCouplingMode.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.optimizer

import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.operand.L2ReadOperand
import com.avail.interpreter.levelTwo.operand.L2WriteOperand
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.optimizer.values.L2SemanticValue
import java.util.*

/**
 * Whether [L2SemanticValue]s or the underlying [L2Register]s should be
 * considered when following data flow to determine liveness.
 */
enum class DataCouplingMode
{
	/**
	 * The liveness analyses should consider the flows through
	 * [L2SemanticValue]s.
	 */
	FOLLOW_SEMANTIC_VALUES
	{
		override fun addEntitiesFromRead(
			readOperand: L2ReadOperand<*>,
			accumulatingSet: MutableSet<L2Entity>)
		{
			accumulatingSet.add(readOperand.semanticValue())
		}

		override fun addEntitiesFromWrite(
			writeOperand: L2WriteOperand<*>,
			accumulatingSet: MutableSet<L2Entity>)
		{
			accumulatingSet.addAll(writeOperand.semanticValues())
		}

		override fun considersRegisters(): Boolean = false

		override fun considersSemanticValues(): Boolean = true
	},

	/**
	 * [L2SemanticValue]s can be ignored, and only [L2Register]s should be
	 * considered for the liveness analysis.
	 */
	FOLLOW_REGISTERS
	{
		override fun addEntitiesFromRead(
			readOperand: L2ReadOperand<*>,
			accumulatingSet: MutableSet<L2Entity>)
		{
			accumulatingSet.add(readOperand.register())
		}

		override fun addEntitiesFromWrite(
			writeOperand: L2WriteOperand<*>,
			accumulatingSet: MutableSet<L2Entity>)
		{
			accumulatingSet.add(writeOperand.register())
		}

		override fun considersRegisters(): Boolean = true

		override fun considersSemanticValues(): Boolean = false
	},

	/**
	 * Both [L2SemanticValue]s and [L2Register]s should be traced for liveness.
	 */
	FOLLOW_SEMANTIC_VALUES_AND_REGISTERS
	{
		override fun addEntitiesFromRead(
			readOperand: L2ReadOperand<*>,
			accumulatingSet: MutableSet<L2Entity>)
		{
			accumulatingSet.add(readOperand.semanticValue())
			accumulatingSet.add(readOperand.register())
		}

		override fun addEntitiesFromWrite(
			writeOperand: L2WriteOperand<*>,
			accumulatingSet: MutableSet<L2Entity>)
		{
			accumulatingSet.addAll(writeOperand.semanticValues())
			accumulatingSet.add(writeOperand.register())
		}

		override fun considersRegisters(): Boolean = true

		override fun considersSemanticValues(): Boolean = true
	};

	/**
	 * Extract each [L2Entity] that this policy is concerned with from the given
	 * [L2ReadOperand].
	 *
	 * @param readOperand
	 *   The [L2ReadOperand] to examine.
	 * @param accumulatingSet
	 *   A [Set] into which to accumulate the read entities.
	 */
	abstract fun addEntitiesFromRead(
		readOperand: L2ReadOperand<*>,
		accumulatingSet: MutableSet<L2Entity>)

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
		accumulatingSet: MutableSet<L2Entity>)

	/**
	 * Answer whether [L2Register]s should be tracked by this policy.
	 *
	 * @return
	 *   `true` iff registers should be tracked.
	 */
	abstract fun considersRegisters(): Boolean

	/**
	 * Answer whether [L2SemanticValue]s should be tracked by this policy.
	 *
	 * @return
	 * `true` iff semantic values should be tracked.
	 */
	abstract fun considersSemanticValues(): Boolean

	/**
	 * Extract each relevant [L2Entity] consumed by the given [L2ReadOperand].
	 *
	 * @param readOperand
	 *   The [L2ReadOperand] to examine.
	 * @return
	 *   Each [L2Entity] read by the [L2ReadOperand], and which the policy deems
	 *   relevant.
	 */
	fun readEntitiesOf(readOperand: L2ReadOperand<*>): Set<L2Entity>
	{
		val entitiesRead: MutableSet<L2Entity> = HashSet(3)
		addEntitiesFromRead(readOperand, entitiesRead)
		return entitiesRead
	}

	/**
	 * Extract each relevant [L2Entity] produced by the given [L2WriteOperand].
	 *
	 * @param writeOperand
	 *   The [L2WriteOperand] to examine.
	 * @return
	 *   Each [L2Entity] written by the [L2WriteOperand], and which the policy deems relevant.
	 */
	fun writeEntitiesOf(writeOperand: L2WriteOperand<*>): Set<L2Entity>
	{
		val entitiesWritten: MutableSet<L2Entity> = HashSet(3)
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
	fun readEntitiesOf(instruction: L2Instruction): Set<L2Entity>
	{
		val entitiesRead: MutableSet<L2Entity> = HashSet()
		instruction.readOperands()
			.forEach { addEntitiesFromRead(it, entitiesRead) }
		return entitiesRead
	}

	/**
	 * Extract each relevant [L2Entity] produced by the given [L2Instruction].
	 *
	 * @param instruction
	 *   The [L2Instruction] to examine.
	 * @return
	 *   Each [L2Entity] written by the instruction, and which the policy deems
	 *   relevant.
	 */
	fun writeEntitiesOf(instruction: L2Instruction): Set<L2Entity>
	{
		val entitiesWritten: MutableSet<L2Entity> = HashSet()
		instruction.writeOperands()
			.forEach { addEntitiesFromWrite(it, entitiesWritten) }
		return entitiesWritten
	}
}