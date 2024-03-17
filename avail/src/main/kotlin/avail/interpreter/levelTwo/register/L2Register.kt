/*
 * L2Register.kt
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
package avail.interpreter.levelTwo.register

import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2Entity
import avail.optimizer.L2Generator
import avail.optimizer.reoptimizer.L2Regenerator

/**
 * `L2Register` models the conceptual use of a register by a [level&#32;two Avail
 * operation][L2Operation] in the [L2Generator].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property uniqueValue
 *   A value used to distinguish distinct registers.
 *
 * @constructor
 * Construct a new `L2BoxedRegister`.
 *
 * @param uniqueValue
 *   A value used to distinguish distinct registers.
 */
abstract class L2Register<K: RegisterKind<K>>
constructor (
	val uniqueValue: Int
) : L2Entity<K>
{
	/**
	 * A coloring number to be used by the [interpreter][Interpreter] at runtime
	 * to identify the storage location of a [register][L2Register].
	 */
	private var finalIndex = -1

	/**
	 * Answer the coloring number to be used by the [interpreter][Interpreter]
	 * at runtime to identify the storage location of a [register][L2Register].
	 *
	 * @return
	 * An `L2Register` coloring number.
	 */
	fun finalIndex(): Int = finalIndex

	/**
	 * Set the coloring number to be used by the [interpreter][Interpreter] at
	 * runtime to identify the storage location of an `L2Register`.
	 *
	 * @param theFinalIndex
	 *   An `L2Register` coloring number.
	 */
	fun setFinalIndex(theFinalIndex: Int)
	{
		assert(finalIndex == -1)
		{ "Only set the finalIndex of an L2RegisterIdentity once" }
		finalIndex = theFinalIndex
	}

	/**
	 * The [L2WriteOperand]s that assign to this register.  While the
	 * [L2ControlFlowGraph] is in SSA form, there should be exactly one.
	 */
	private val definitions = mutableSetOf<L2WriteOperand<K>>()

	/**
	 * Record this [L2WriteOperand] in my set of defining write operands.
	 *
	 * @param write
	 *   An [L2WriteOperand] that's an operand of an instruction that writes to
	 *   this register in the control flow graph of basic blocks.
	 */
	fun addDefinition(write: L2WriteOperand<K>)
	{
		definitions.add(write)
	}

	/**
	 * Remove the given [L2WriteOperand] as one of the writers to this register.
	 *
	 * @param write
	 *   The [L2WriteOperand] to remove from my set of defining write operands.
	 */
	fun removeDefinition(write: L2WriteOperand<K>)
	{
		definitions.remove(write)
	}

	/**
	 * Answer the [L2WriteOperand] of an [L2Instruction] which assigns this
	 * register in the SSA control flow graph. It must have been assigned
	 * already, and there must be exactly one (when the control flow graph is in
	 * SSA form).
	 *
	 * @return
	 *   The requested `L2WriteOperand`.
	 */
	fun definition(): L2WriteOperand<K>
	{
		assert(definitions.size == 1)
		return definitions.single()
	}

	/**
	 * Answer the [L2WriteOperand]s which assign this register in the control
	 * flow graph, which is not necessarily in SSA form. It must be non-empty.
	 *
	 * @return
	 *   This register's defining [L2WriteOperand]s.
	 */
	fun definitions(): Collection<L2WriteOperand<K>> = definitions

	/**
	 * The [L2ReadOperand]s of emitted [L2Instruction]s that read from this
	 * register.
	 */
	private val uses = mutableSetOf<L2ReadOperand<*>>()

	/**
	 * Capture another [L2ReadOperand] of an emitted [L2Instruction] that uses
	 * this register.
	 *
	 * @param read
	 *   The [L2ReadOperand] that reads from this register.
	 */
	fun addUse(read: L2ReadOperand<*>)
	{
		uses.add(read)
	}

	/**
	 * Drop a use of this register by an [L2ReadOperand] of an [L2Instruction]
	 * that is now dead.
	 *
	 * @param read
	 *   An [L2ReadOperand] that no longer reads from this register because its
	 *   instruction has been removed.
	 */
	fun removeUse(read: L2ReadOperand<*>)
	{
		uses.remove(read)
	}

	/**
	 * Answer the [Set] of [L2ReadOperand]s that read from this register.
	 * Callers must not modify the returned collection.
	 *
	 * @return
	 *   A [Set] of [L2ReadOperand]s.
	 */
	fun uses(): Set<L2ReadOperand<*>> = uses

	/**
	 * Answer a new register like this one.
	 *
	 * @param generator
	 *   The [L2Generator] for which copying is requested.
	 * @return
	 *   The new `L2Register`.
	 */
	abstract fun copyForTranslator(generator: L2Generator): L2Register<K>

	/**
	 * Answer a new register like this one, but where the uniqueValue has been
	 * set to the finalIndex.
	 *
	 * @return
	 *   The new `L2Register`.
	 */
	abstract fun copyAfterColoring(): L2Register<K>

	/**
	 * Answer a copy of the receiver. Subclasses can be covariantly stronger in
	 * the return type.
	 *
	 * @param regenerator
	 *   The [L2Regenerator] for which copying is requested.
	 * @return
	 *   A copy of the receiver.
	 */
	abstract fun copyForRegenerator(regenerator: L2Regenerator): L2Register<K>

	override fun toString() = buildString {
		append(kind.prefix)
		if (finalIndex() != -1) append(finalIndex())
		else append(uniqueValue)
	}
}
