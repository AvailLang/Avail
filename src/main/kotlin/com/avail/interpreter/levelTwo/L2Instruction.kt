/*
 * L2Instruction.kt
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
package com.avail.interpreter.levelTwo

import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import com.avail.interpreter.levelTwo.operand.L2Operand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadOperand
import com.avail.interpreter.levelTwo.operand.L2WriteOperand
import com.avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.optimizer.L2BasicBlock
import com.avail.optimizer.L2ControlFlowGraph
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.reoptimizer.L2Regenerator
import com.avail.optimizer.values.L2SemanticValue
import com.avail.utility.cast
import org.objectweb.asm.MethodVisitor
import java.util.EnumSet

/**
 * `L2Instruction` is the foundation for all instructions understood by
 * the [level&#32;two&#32;Avail&#32;interpreter][Interpreter]. These
 * instructions are model objects generated and manipulated by the
 * [L2Generator].
 *
 *
 * It used to be the case that the instructions were flattened into a stream of
 * integers, operation followed by operands.  That is no longer the case, as of
 * 2013-05-01 `MvG`.  Instead, the L2Instructions themselves are kept around for
 * reoptimization and [JVM&#32;code&#32;generation][translateToJVM].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property basicBlock
 *   The [L2BasicBlock] to which the instruction belongs.
 * @property operation
 *   The [L2Operation] whose execution this instruction represents.
 *
 * @constructor
 * Construct a new `L2Instruction`.
 *
 * @param basicBlock
 *        The [L2BasicBlock] which will contain this instruction  or `null` if
 *        none.
 * @param operation
 *        The [L2Operation] that this instruction performs.
 * @param theOperands
 *        The array of [L2Operand]s on which this instruction
 *        operates.  These must agree with the operation's array of
 *        [L2NamedOperandType]s.
 */
class L2Instruction constructor(
	private var basicBlock: L2BasicBlock?,
	private val operation: L2Operation,
	vararg theOperands: L2Operand)
{
	/**
	 * The position of this instruction within its array of instructions.
	 * Only valid near the end of translation.
	 */
	private var offset = -1

	/**
	 * The source [L2Register]s.
	 */
	private val sourceRegisters = mutableListOf<L2Register>()

	/**
	 * The destination [L2Register]s.
	 */
	private val destinationRegisters = mutableListOf<L2Register>()

	/**
	 * The [L2Operand]s to supply to the operation.
	 */
	private val operands = run {
		assert(operation.namedOperandTypes.size == theOperands.size)
		for ((i, namedOperandType) in operation.namedOperandTypes.withIndex())
		{
			assert(
				theOperands[i].operandType() === namedOperandType.operandType())
		}
		Array(theOperands.size) {
			val operand = theOperands[it].clone()
			operand.adjustCloneForInstruction(this)
			operand.addSourceRegistersTo(sourceRegisters)
			operand.addDestinationRegistersTo(destinationRegisters)
			operand
		}
	}

	/**
	 * The [L2Operation] whose execution this instruction represents.
	 *
	 * @return
	 *   The instruction's [L2Operation].
	 */
	fun operation(): L2Operation = operation

	/**
	 * Answer the [L2Operand]s to supply to the operation.
	 *
	 * @return
	 *   The instruction's array of [L2Operand]s.
	 */
	fun operands(): Array<L2Operand> = operands

	/**
	 * Evaluate the given function with each [L2Operand] and the
	 * [L2NamedOperandType] that it occupies.
	 *
	 * @param consumer
	 *   The lambda to evaluate.
	 */
	@Suppress("unused")
	fun operandsWithNamedTypesDo(
		consumer: (L2Operand, L2NamedOperandType) -> Unit)
	{
		for (i in operands.indices)
		{
			consumer(operands[i], operation.namedOperandTypes[i])
		}
	}

	/**
	 * Evaluate the given function with each [edge][L2PcOperand] and its
	 * corresponding [L2NamedOperandType.Purpose].  An [L2WriteOperand] is only
	 * considered to take place if its [L2NamedOperandType.Purpose] is null, or
	 * if it is the same as the [edge][L2PcOperand] that is taken by this
	 * `L2Instruction`.
	 *
	 * This is only applicable to an instruction which [altersControlFlow]
	 *
	 * @param consumer
	 *  The lambda to evaluate.
	 */
	fun edgesAndPurposesDo(
		consumer: (L2PcOperand, Purpose?) -> Unit)
	{
		for (i in operands.indices)
		{
			val operand = operands[i]
			if (operand is L2PcOperand)
			{
				consumer(operand, operation.namedOperandTypes[i].purpose())
			}
		}
	}

	/**
	 * Evaluate the given function with each [L2WriteOperand] having the given
	 * [L2NamedOperandType.Purpose], which correlates with the `Purpose` along
	 * each outbound [edge][L2PcOperand] to indicate which writes have effect
	 * along which outbound edges.
	 *
	 * This is only applicable to an instruction which [altersControlFlow]
	 *
	 * @param purpose
	 *   The [L2NamedOperandType.Purpose] with which to filter
	 *   [L2WriteOperand]s.
	 * @param consumer
	 *   The lambda to evaluate with each [L2WriteOperand] having the given
	 *   [L2NamedOperandType.Purpose].
	 */
	fun writesForPurposeDo(
		purpose: Purpose,
		consumer: (L2WriteOperand<*>) -> Unit)
	{
		assert(altersControlFlow)
		for (i in operands.indices)
		{
			val operand = operands[i]
			if (operand is L2WriteOperand<*>)
			{
				if (operation.namedOperandTypes[i].purpose() === purpose)
				{
					consumer(operand)
				}
			}
		}
	}

	/**
	 * Answer the Nth [L2Operand] to supply to the operation.
	 *
	 * @param index
	 *   The zero-based operand index.
	 * @param O
	 *   The specialization of [L2Operand] to return.
	 * @return
	 *   The specified operand.
	 */
	fun <O : L2Operand> operand(index: Int): O = operands[index].cast()

	/**
	 * Answer the position of this instruction within its array of instructions.
	 *
	 * @return
	 *   The position of the instruction in its chunk's instruction array.
	 */
	fun offset(): Int = offset

	/**
	 * Set the final position of this instruction within its [L2Chunk]'s array
	 * of instructions.
	 *
	 * @param offset
	 *   The final position of the instruction within the array.
	 */
	fun setOffset(offset: Int)
	{
		this.offset = offset
	}

	/**
	 * Construct a new `L2Instruction`.  The instruction will be added somewhere
	 * within the given [L2Generator]'s current [L2BasicBlock].
	 *
	 * @param generator
	 *   The [L2Generator] in which this instruction is being regenerated.
	 * @param operation
	 *   The [L2Operation] that this instruction performs.
	 * @param theOperands
	 *   The array of [L2Operand]s on which this instruction operates.  These
	 *   must agree with the operation's array of [L2NamedOperandType]s.
	 */
	constructor(
			generator: L2Generator,
			operation: L2Operation,
			vararg theOperands: L2Operand)
		: this(generator.currentBlock(), operation, *theOperands)

	/**
	 * Check that this instruction's [basicBlock] has been set, and that each
	 * operand's instruction field has also been set.
	 */
	fun assertHasBeenEmitted()
	{
		assert(basicBlock !== null)
		operands.forEach(L2Operand::assertHasBeenEmitted)
	}

	/**
	 * Answer the [list][List] of [L2Register]s read by this
	 * `L2Instruction instruction`.
	 *
	 * @return
	 *   The source [registers][L2Register].
	 */
	fun sourceRegisters(): List<L2Register> = sourceRegisters

	/**
	 * Answer the [list][List] of [L2Register]s modified by this `L2Instruction
	 * instruction`.
	 *
	 * @return
	 *   The source [L2Register]s.
	 */
	fun destinationRegisters(): List<L2Register> = destinationRegisters

	/**
	 * Answer a [List] of this instruction's [L2ReadOperand]s.
	 *
	 * @return
	 *  The list of read operands.
	 */
	fun readOperands(): List<L2ReadOperand<*>>
	{
		val list = mutableListOf<L2ReadOperand<*>>()
		operands.forEach { it.addReadsTo(list) }
		return list
	}

	/**
	 * Answer a [List] of this instruction's [L2WriteOperand]s.
	 *
	 * @return
	 *   The list of write operands.
	 */
	fun writeOperands(): List<L2WriteOperand<*>>
	{
		val list = mutableListOf<L2WriteOperand<*>>()
		operands.forEach { it.addWritesTo(list) }
		return list
	}

	/**
	 * Answer all possible [L2PcOperand]s within this instruction.  These edges
	 * lead to other [L2BasicBlock]s, and carry a [L2ValueManifest].
	 *
	 * This is empty for instructions that don't alter control flow and just
	 * fall through to the next instruction of the same basic block.
	 *
	 * @return
	 *   A [List] of [L2PcOperand]s leading to the successor [L2BasicBlock]s.
	 */
	val targetEdges get() = operation().targetEdges(this)

	/**
	 * Answer whether this instruction can alter control flow.  That's true for
	 * any kind of instruction that has more than one successor (e.g., a branch)
	 * or no successors at all (e.g., a return).
	 *
	 * An instruction for which this is true must occur at the end of each
	 * [L2BasicBlock], but never before the end.
	 *
	 * @return
	 *   Whether this instruction can do something other than fall through to
	 *   the next instruction of its basic block.
	 */
	val altersControlFlow get() = operation().altersControlFlow()

	/**
	 * Answer whether this instruction has any observable effect besides
	 * writing to its destination registers.
	 *
	 * @return
	 *   s\Whether this instruction has side effects.
	 */
	val hasSideEffect get() = operation().hasSideEffect(this)

	/**
	 * Answer whether this instruction is an entry point, which uses the
	 * operation [L2_ENTER_L2_CHUNK].
	 *
	 * @return
	 *   Whether the instruction is an entry point.
	 */
	val isEntryPoint get() = operation().isEntryPoint(this)

	/**
	 * Replace all registers in this instruction using the registerRemap.  If a
	 * register is not present as a key of that map, leave it alone.  Do not
	 * assume SSA form.
	 *
	 * @param registerRemap
	 *   A mapping from existing [L2Register]s to replacement [L2Register]s
	 *   having the same [L2Register.registerKind].
	 */
	fun replaceRegisters(registerRemap: Map<L2Register, L2Register>)
	{
		val sourcesBefore: List<L2Register> = sourceRegisters.toList()
		val destinationsBefore: List<L2Register> = destinationRegisters.toList()
		operands.forEach { it.replaceRegisters(registerRemap, this) }
		sourceRegisters.replaceAll { r -> registerRemap[r] ?: r }
		destinationRegisters.replaceAll { r -> registerRemap[r] ?: r }
		assert(sourceRegisters.size == sourcesBefore.size)
		assert(destinationRegisters.size == destinationsBefore.size)
	}

	/**
	 * For every read of a register which is restricted to a constant, replace
	 * it with a read of a brand new register with that same restriction, but
	 * having no definitions.  This pattern is acceptable in later phases,
	 * starting just before phi replacement.  It should reduce register
	 * pressure.
	 */
	fun replaceConstantRegisters() =
		operands.forEach(L2Operand::replaceConstantRegisters)

	/**
	 * This instruction was just added to its [L2BasicBlock].
	 *
	 * @param manifest
	 *   The [L2ValueManifest] that is active where this instruction was just
	 *   added to its [L2BasicBlock].
	 */
	fun justAdded(manifest: L2ValueManifest)
	{
		assert(!isEntryPoint || basicBlock().instructions()[0] == this) {
			"Entry point instruction must be at start of a block"
		}
		operands.forEach { it.setInstruction(this) }
		operation().instructionWasAdded(this, manifest)
	}

	/**
	 * This instruction was just added to its [L2BasicBlock] as part of an
	 * optimization pass.
	 */
	fun justInserted()
	{
		operands.forEach { it.setInstruction(this) }
		operation().instructionWasInserted(this)
	}

	/**
	 * This instruction was just removed from its [L2BasicBlock]'s list of
	 * instructions, and needs to finish its removal by breaking back-pointers,
	 * plus whatever else specific operands need to do when they're no longer
	 * considered part of the code.
	 */
	fun justRemoved()
	{
		operands.forEach(L2Operand::instructionWasRemoved)
		operands.forEach { it.setInstruction(null) }
		basicBlock = null
	}

	/**
	 * Recreate the [L2ValueManifest] that was in effect just prior to the
	 * instruction that is the receiver.
	 *
	 * @return
	 *   An [L2ValueManifest].
	 */
	@Suppress("unused")
	fun recreateIncomingManifest(): L2ValueManifest
	{
		// Start with the intersection of the incoming manifests.
		val incomingEdges = basicBlock().predecessorEdges()
		val manifest = L2ValueManifest.intersectionOfManifests(
			incomingEdges.map(L2PcOperand::manifest)
		)
		// Now record all writes until we reach the instruction.
		for (instruction in basicBlock().instructions())
		{
			if (instruction == this)
			{
				return manifest
			}
			instruction.writeOperands().forEach(manifest::recordDefinition)
		}
		throw RuntimeException("Instruction was not found in its block")
	}

	/**
	 * Update the given manifest with the effect of this instruction.  If a
	 * [L2NamedOperandType.Purpose] is given, alter the manifest to agree with
	 * outbound edges having that purpose.
	 *
	 * @param manifest
	 *   The [L2ValueManifest] to update with the effect of this instruction.
	 * @param optionalPurpose
	 *   If non-`null`, produce tha manifest that should be active along
	 *   outbound edges having the indicated [L2NamedOperandType.Purpose].
	 */
	@Suppress("unused")
	fun updateManifest(
		manifest: L2ValueManifest?,
		optionalPurpose: Purpose?
	) = operation.updateManifest(this, manifest!!, optionalPurpose)

	/**
	 * Attempt to create a copy of this instruction, but using the
	 * [L2SemanticValue]s present in the given [L2ValueManifest]. Answer `null`
	 * if the transformation won't work because of a missing read operand.
	 *
	 * @param newBlock
	 *   The [L2BasicBlock] in which the instruction will eventually be
	 *   inserted.
	 * @param manifest
	 *   The [L2ValueManifest] that's active where the new instruction would be
	 *   inserted.
	 * @return
	 *   The new instruction or `null`.
	 */
	@Suppress("unused")
	fun copyInstructionForManifest(
		newBlock: L2BasicBlock, manifest: L2ValueManifest): L2Instruction?
	{
		var failed = false
		val transform: (L2ReadOperand<*>) -> L2ReadOperand<*> =
			 {
				read: L2ReadOperand<*> ->
				val registers = manifest.getDefinitions<L2Register>(
					read.semanticValue(), read.registerKind())
				when
				{
					registers.isEmpty() ->
					{
						failed = true
						read
					}
					else -> read.copyForRegister(registers[0])
				}
			}
		val operandsCopy = operands.map { operand ->
			operand.transformEachRead(transform)
		}.toTypedArray()

		return if (failed) null
			else L2Instruction(newBlock, operation, *operandsCopy)
	}

	/**
	 * Answer whether this instruction should be emitted during final code
	 * generation (from the non-SSA [L2ControlFlowGraph] into a flat
	 * sequence of `L2Instruction`s.  Allow the operation to decide.
	 *
	 * @return
	 *   Whether to preserve this instruction during final code generation.
	 */
	val shouldEmit: Boolean get() = operation().shouldEmit(this)

	override fun toString(): String
	{
		val builder = StringBuilder()
		appendToWithWarnings(
			builder, EnumSet.allOf(L2OperandType::class.java)) {  }
		return builder.toString()
	}

	/**
	 * Output this instruction to the given builder, invoking the given lambda
	 * with a boolean to turn warning style on or off, if tracked by the caller.
	 *
	 * @param builder
	 *   Where to write the description of this instruction.
	 * @param operandTypes
	 *   Which [L2OperandType]s to include.
	 * @param warningStyleChange
	 *   A lambda that takes `true` to start the warning style at the
	 *   current builder position, and `false` to end it.  It must be invoked in
	 *   (true, false) pairs.
	 */
	fun appendToWithWarnings(
		builder: StringBuilder,
		operandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean) -> Unit)
	{
		if (basicBlock === null)
		{
			warningStyleChange(true)
			builder.append("DEAD: ")
			warningStyleChange(false)
		}
		operation().appendToWithWarnings(
			this,
			operandTypes,
			builder,
			warningStyleChange)
	}

	/**
	 * Write the equivalent of this instruction through the given
	 * [L2Regenerator]. Certain types of [L2Operation]s are transformed in ways
	 * specific to the kind of regenerator being used.
	 *
	 * @param regenerator
	 *   The [L2Regenerator] through which to write this instruction's
	 *   equivalent effect.
	 */
	fun transformAndEmitOn(regenerator: L2Regenerator) =
		operation().emitTransformedInstruction(
			Array(operands().size) {
				regenerator.transformOperand<L2Operand>(operand(it))
			},
			regenerator)

	/**
	 * Translate the `L2Instruction` into corresponding JVM instructions.
	 *
	 * @param translator
	 *   The [JVMTranslator] responsible for the translation.
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 */
	fun translateToJVM(translator: JVMTranslator, method: MethodVisitor) =
		operation().translateToJVM(translator, method, this)

	/**
	 * Answer the [L2BasicBlock] to which this instruction belongs.
	 *
	 * @return
	 *   This instruction's [L2BasicBlock].
	 */
	fun basicBlock(): L2BasicBlock = basicBlock!!

	/**
	 * Determine if this instruction is a place-holding instruction that will be
	 * re-emitted as an arbitrary graph of instructions at some point, via
	 * [L2Operation.generateReplacement].
	 */
	val isPlaceholder get() = operation.isPlaceholder(this)

	/**
	 * Now that chunk optimization has completed, remove information from this
	 * instruction that will no longer be needed in the finished chunk.  Note
	 * that during subsequent inlining of this chunk at a call site, the type
	 * and synonym information will be reconstructed without too much cost.
	 */
	fun postOptimizationCleanup()
	{
		sourceRegisters.clear()
		destinationRegisters.clear()
		for (operand in operands)
		{
			// Note that this includes L2PcOperands, all edges.
			operand.postOptimizationCleanup()
		}
	}
}
