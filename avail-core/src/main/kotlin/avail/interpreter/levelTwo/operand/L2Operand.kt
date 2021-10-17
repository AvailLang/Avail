/*
 * L2Operand.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package avail.interpreter.levelTwo.operand

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ValueManifest
import avail.utility.PublicCloneable
import avail.utility.Strings.increaseIndentation
import javax.annotation.OverridingMethodsMustInvokeSuper

/**
 * An `L2Operand` knows its [L2OperandType] and any specific value that needs to
 * be captured for that type of operand.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class L2Operand : PublicCloneable<L2Operand>()
{
	/**
	 * A back-pointer to the [L2Instruction] that this operand is part of. This
	 * is only populated for instructions that have already been emitted to an
	 * [L2BasicBlock].
	 */
	private var instruction: L2Instruction? = null

	/**
	 * Answer the [L2Instruction] containing this operand.
	 *
	 * @return
	 *   An [L2Instruction]
	 */
	fun instruction(): L2Instruction = instruction!!

	/**
	 * Answer whether this write operand has been written yet as the destination
	 * of some instruction.
	 *
	 * @return
	 *   `true` if this operand has been written inside an [L2Instruction],
	 *   otherwise `false`.
	 */
	fun instructionHasBeenEmitted(): Boolean = instruction !== null

	/**
	 * Assert that this operand knows its instruction, which should always be
	 * the case if the instruction has already been emitted.
	 */
	@OverridingMethodsMustInvokeSuper
	open fun assertHasBeenEmitted()
	{
		assert(instruction !== null)
	}

	/**
	 * Answer this operand's [L2OperandType].
	 *
	 * @return
	 *   An `L2OperandType`.
	 */
	abstract fun operandType(): L2OperandType

	/**
	 * Dispatch this `L2Operand` to the provided [L2OperandDispatcher].
	 *
	 * @param dispatcher
	 *   The `L2OperandDispatcher` visiting the receiver.
	 */
	abstract fun dispatchOperand(dispatcher: L2OperandDispatcher)

	/**
	 * This is an operand of the given instruction, which was just added to its
	 * basic block.  Its instruction was just set.
	 *
	 * @param manifest
	 *   The [L2ValueManifest] that is active where this [L2Instruction] was
	 *   just added to its [L2BasicBlock].
	 */
	@OverridingMethodsMustInvokeSuper
	open fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		assert(instruction !== null)
	}

	/**
	 * This is an operand of the given instruction, which was just inserted into
	 * its basic block as part of an optimization pass.
	 *
	 * @param newInstruction
	 *   The [L2Instruction] that was just inserted.
	 */
	@OverridingMethodsMustInvokeSuper
	open fun instructionWasInserted(
		newInstruction: L2Instruction)
	{
		// Nothing by default.  The L2Instruction already set my instruction
		// field in a previous pass.
	}

	/**
	 * This is an operand of the given instruction, which was just removed from
	 * its basic block.
	 */
	@OverridingMethodsMustInvokeSuper
	open fun instructionWasRemoved()
	{
		// Nothing by default.  The L2Instruction already set my instruction
		// field in a previous pass.
	}

	/**
	 * Replace occurrences in this operand of each register that is a key of
	 * this map with the register that is the corresponding value.  Do nothing
	 * to registers that are not keys of the map.  Update all secondary
	 * structures, such as the instruction's source/destination collections.
	 *
	 * @param registerRemap
	 *   A mapping to transform registers in-place.
	 * @param theInstruction
	 *   The instruction containing this operand.
	 */
	open fun replaceRegisters(
		registerRemap: Map<L2Register, L2Register>,
		theInstruction: L2Instruction)
	{
		// By default do nothing.
	}

	/**
	 * Transform each L2ReadOperand through the given lambda, producing either a
	 * new `L2Operand` of the same type, or the receiver.
	 *
	 * @param transformer
	 *   The lambda to transform [L2ReadOperand]s.
	 * @return
	 *   The transformed operand or the receiver.
	 */
	open fun transformEachRead(
		transformer: (L2ReadOperand<*>) -> L2ReadOperand<*>
	) : L2Operand = this

	/**
	 * Capture all [L2ReadOperand]s within this operand into the provided
	 * [List].
	 *
	 * @param readOperands
	 *   The mutable [List] of [L2ReadOperand]s being populated.
	 */
	open fun addReadsTo(readOperands: MutableList<L2ReadOperand<*>>)
	{
		// Do nothing by default.
	}

	/**
	 * Capture all [L2WriteOperand]s within this operand into the provided
	 * [List].
	 *
	 * @param writeOperands
	 *   The mutable [List] of [L2WriteOperand]s being populated.
	 */
	open fun addWritesTo(writeOperands: MutableList<L2WriteOperand<*>>)
	{
		// Do nothing by default.
	}

	/**
	 * Move any registers used as sources within me into the provided list.
	 *
	 * @param sourceRegisters
	 *   The [MutableList] to update.
	 */
	open fun addSourceRegistersTo(sourceRegisters: MutableList<L2Register>)
	{
		// Do nothing by default.
	}

	/**
	 * Move any registers used as destinations within me into the provided list.
	 *
	 * @param destinationRegisters
	 *   The [MutableList] to update.
	 */
	open fun addDestinationRegistersTo(
		destinationRegisters: MutableList<L2Register>)
	{
		// Do nothing by default.
	}

	override fun toString(): String
	{
		val builder = StringBuilder()
		appendWithWarningsTo(builder, 0) {  }
		return builder.toString()
	}

	/**
	 * Append a textual representation of this operand to the provided
	 * [StringBuilder].  If a style change is appropriate while building the
	 * string, invoke the warningStyleChange lambda with `true` to enable
	 * the warning style, and `false` to turn it off again.
	 *
	 * @param builder
	 *   The [StringBuilder] on which to describe this operand.
	 * @param indent
	 *   How much additional indentation to add to successive lines.
	 * @param warningStyleChange
	 *   A lambda to invoke to turn the warning style on or off, with a
	 *   mechanism specified (or ignored) by the caller.
	 */
	fun appendWithWarningsTo(
		builder: StringBuilder,
		indent: Int,
		warningStyleChange: (Boolean) -> Unit)
	{
		if (instruction === null)
		{
			warningStyleChange(true)
			builder.append("DEAD-OPERAND: ")
			warningStyleChange(false)
		}
		else if (isMisconnected)
		{
			warningStyleChange(true)
			builder.append("MISCONNECTED: ")
			warningStyleChange(false)
		}
		// Call the inner method that can be overridden.
		val temp = StringBuilder()
		appendTo(temp)
		builder.append(increaseIndentation(temp.toString(), indent))
	}// Operand wasn't found inside the instruction.

	/**
	 * Answer whether this operand is misconnected to its [L2Instruction].
	 *
	 * @return
	 *   `false` if the operand is connected correctly, otherwise `true`.
	 */
	val isMisconnected: Boolean
		get()
		{
			if (instruction === null)
			{
				return true
			}
			val operands = instruction!!.operands()

			operands.indices.forEach { i ->
				when(val operand = operands[i])
				{
					this -> return false
					is L2PcVectorOperand ->
					{
						if (operand.edges.contains(this))
						{
							return false
						}
					}
					is L2ReadVectorOperand<*, *> ->
					{
						if (operand.elements.contains(this))
						{
							return false
						}
					}
				}
			}
			// Operand wasn't found inside the instruction.
			return true
		}

	/**
	 * Write a description of this operand to the given [StringBuilder].
	 *
	 * @param builder
	 *   The [StringBuilder] on which to describe this operand.
	 */
	abstract fun appendTo(builder: StringBuilder)

	/**
	 * This is a freshly cloned operand.  Adjust it for use in the given
	 * [L2Instruction].  Note that the new instruction has not yet been
	 * installed into an [L2BasicBlock].
	 *
	 * @param theInstruction
	 *   The theInstruction that this operand is being installed in.
	 */
	@OverridingMethodsMustInvokeSuper
	open fun adjustCloneForInstruction(theInstruction: L2Instruction)
	{
		// The instruction will be set correctly when this instruction is
		// emitted to an L2BasicBlock.
		setInstruction(null)
	}

	/**
	 * Set the [instruction] field.
	 *
	 * @param theInstruction
	 *   The [L2Instruction] or `null`.
	 */
	open fun setInstruction(theInstruction: L2Instruction?)
	{
		instruction = theInstruction
	}

	/**
	 * Destructively replace any constant-valued reads of registers with reads
	 * of a fresh constant-valued register that has no writes.
	 *
	 * This pattern is recognized in later optimization passes.  It reduces the
	 * register pressure for coloring, and eliminates pointless moves.
	 *
	 * Subclasses implement this as needed.
	 */
	open fun replaceConstantRegisters() { }

	/**
	 * Now that chunk optimization has completed, remove information from this
	 * instruction that will no longer be needed in the finished chunk.  Note
	 * that during subsequent inlining of this chunk at a call site, the type
	 * and synonym information will be reconstructed without too much cost.
	 */
	open fun postOptimizationCleanup() { }
}
