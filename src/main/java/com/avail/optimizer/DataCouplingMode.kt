/*
 * DataCouplingMode.java
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

package com.avail.optimizer;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteOperand;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.values.L2SemanticValue;

import java.util.HashSet;
import java.util.Set;

/**
 * Whether {@link L2SemanticValue}s or the underlying {@link L2Register}s
 * should be considered when following data flow to determine liveness.
 */
enum DataCouplingMode
{
	/**
	 * The liveness analyses should consider the flows through
	 * {@link L2SemanticValue}s.
	 */
	FOLLOW_SEMANTIC_VALUES
	{
		@Override
		void addEntitiesFromRead (
			final L2ReadOperand<?> readOperand,
			final Set<L2Entity> accumulatingSet)
		{
			accumulatingSet.add(readOperand.semanticValue());
		}

		@Override
		void addEntitiesFromWrite (
			final L2WriteOperand<?> writeOperand,
			final Set<L2Entity> accumulatingSet)
		{
			accumulatingSet.addAll(writeOperand.semanticValues());
		}

		@Override
		boolean considersRegisters ()
		{
			return false;
		}

		@Override
		boolean considersSemanticValues ()
		{
			return true;
		}
	},

	/**
	 * {@link L2SemanticValue}s can be ignored, and only {@link L2Register}s
	 * should be considered for the liveness analysis.
	 */
	FOLLOW_REGISTERS
	{
		@Override
		void addEntitiesFromRead (
			final L2ReadOperand<?> readOperand,
			final Set<L2Entity> accumulatingSet)
		{
			accumulatingSet.add(readOperand.register());
		}

		@Override
		void addEntitiesFromWrite (
			final L2WriteOperand<?> writeOperand,
			final Set<L2Entity> accumulatingSet)
		{
			accumulatingSet.add(writeOperand.register());
		}

		@Override
		boolean considersRegisters ()
		{
			return true;
		}

		@Override
		boolean considersSemanticValues ()
		{
			return false;
		}
	},

	/**
	 * Both {@link L2SemanticValue}s and {@link L2Register}s should be
	 * traced for liveness.
	 */
	FOLLOW_SEMANTIC_VALUES_AND_REGISTERS
	{
		@Override
		void addEntitiesFromRead (
			final L2ReadOperand<?> readOperand,
			final Set<L2Entity> accumulatingSet)
		{
			accumulatingSet.add(readOperand.semanticValue());
			accumulatingSet.add(readOperand.register());
		}

		@Override
		void addEntitiesFromWrite (
			final L2WriteOperand<?> writeOperand,
			final Set<L2Entity> accumulatingSet)
		{
			accumulatingSet.addAll(writeOperand.semanticValues());
			accumulatingSet.add(writeOperand.register());
		}

		@Override
		boolean considersRegisters ()
		{
			return true;
		}

		@Override
		boolean considersSemanticValues ()
		{
			return true;
		}
	};

	/**
	 * Extract each {@link L2Entity} that this policy is concerned with from
	 * the given {@link L2ReadOperand}.
	 *
	 * @param readOperand
	 *        The {@link L2ReadOperand} to examine.
	 * @param accumulatingSet
	 *        A {@link Set} into which to accumulate the read entities.
	 */
	abstract void addEntitiesFromRead (
		final L2ReadOperand<?> readOperand,
		final Set<L2Entity> accumulatingSet);

	/**
	 * Extract each {@link L2Entity} that this policy is concerned with from
	 * the given {@link L2WriteOperand}.
	 *
	 * @param writeOperand
	 *        The {@link L2WriteOperand} to examine.
	 * @param accumulatingSet
	 *        A {@link Set} into which to accumulate the written entities.
	 */
	abstract void addEntitiesFromWrite (
		final L2WriteOperand<?> writeOperand,
		final Set<L2Entity> accumulatingSet);

	/**
	 * Answer whether {@link L2Register}s should be tracked by this policy.
	 *
	 * @return {@code true} iff registers should be tracked.
	 */
	abstract boolean considersRegisters ();

	/**
	 * Answer whether {@link L2SemanticValue}s should be tracked by this policy.
	 *
	 * @return {@code true} iff semantic values should be tracked.
	 */
	abstract boolean considersSemanticValues ();

	/**
	 * Extract each relevant {@link L2Entity} consumed by the given
	 * {@link L2ReadOperand}.
	 *
	 * @param readOperand
	 *        The {@link L2ReadOperand} to examine.
	 * @return Each {@link L2Entity} read by the {@link L2ReadOperand}, and
	 *         which the policy deems relevant.
	 */
	public final Set<L2Entity> readEntitiesOf (
		final L2ReadOperand<?> readOperand)
	{
		final Set<L2Entity> entitiesRead = new HashSet<>(3);
		addEntitiesFromRead(readOperand, entitiesRead);
		return entitiesRead;
	}

	/**
	 * Extract each relevant {@link L2Entity} produced by the given
	 * {@link L2WriteOperand}.
	 *
	 * @param writeOperand
	 *        The {@link L2WriteOperand} to examine.
	 * @return Each {@link L2Entity} written by the {@link L2WriteOperand}, and
	 *         which the policy deems relevant.
	 */
	public final Set<L2Entity> writeEntitiesOf (
		final L2WriteOperand<?> writeOperand)
	{
		final Set<L2Entity> entitiesWritten = new HashSet<>(3);
		addEntitiesFromWrite(writeOperand, entitiesWritten);
		return entitiesWritten;
	}

	/**
	 * Extract each relevant {@link L2Entity} consumed by the given
	 * {@link L2Instruction}.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} to examine.
	 * @return Each {@link L2Entity} read by the instruction, and which the
	 *         policy deems relevant.
	 */
	public final Set<L2Entity> readEntitiesOf (
		final L2Instruction instruction)
	{
		final Set<L2Entity> entitiesRead = new HashSet<>();
		instruction.readOperands().forEach(
			read -> addEntitiesFromRead(read, entitiesRead));
		return entitiesRead;
	}

	/**
	 * Extract each relevant {@link L2Entity} produced by the given
	 * {@link L2Instruction}.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} to examine.
	 * @return Each {@link L2Entity} written by the instruction, and which
	 *         the policy deems relevant.
	 */
	public final Set<L2Entity> writeEntitiesOf (
		final L2Instruction instruction)
	{
		final Set<L2Entity> entitiesWritten = new HashSet<>();
		instruction.writeOperands().forEach(
			write -> addEntitiesFromWrite(write, entitiesWritten));
		return entitiesWritten;
	}
}
