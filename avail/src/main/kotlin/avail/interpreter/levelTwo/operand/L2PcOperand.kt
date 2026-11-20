/*
 * L2PcOperand.kt
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
package avail.interpreter.levelTwo.operand

import avail.descriptor.functions.RegisterDumpDescriptor.Companion.createRegisterDumpMethod
import avail.descriptor.functions.RegisterDumpDescriptor.Companion.emptyRegisterDump
import avail.descriptor.representation.A_RegisterDump
import avail.descriptor.representation.AvailObject
import avail.exceptions.unsupported
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.operation.L2_CREATE_CONTINUATION
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.jvm.JVMChunk
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.manifest.L2Liveness
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.values.L2SemanticValue
import org.objectweb.asm.Opcodes
import java.util.concurrent.atomic.LongAdder

/**
 * An [L2PcOperand] is an operand of type [L2OperandType.PC].
 * It refers to a target [L2BasicBlock], that either be branched to at
 * runtime, or captured in some other way that flow control may end up there.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property targetBlock
 *   The [L2BasicBlock] that this operand leads to.
 * @property isBackward
 *   Whether this edge points backward to a block marked as
 *   [L2BasicBlock.isLoopHead], thereby closing a loop.
 *
 * @constructor
 * Construct a new [L2PcOperand] that leads to the specified [L2BasicBlock].
 * Set [isBackward] to true if this is a back-link to a
 * [loop&#32;head][L2BasicBlock.isLoopHead],
 *
 * @property targetBlock
 *   The [L2BasicBlock] The target basic block.
 * @property isBackward
 *   Whether this edge is a back-link to a loop head.
 * @property manifest
 *   If supplied, the [L2ValueManifest] linking semantic values and registers at
 *   this control flow edge.
 */
class L2PcOperand
constructor (
	private var targetBlock: L2BasicBlock,
	var isBackward: Boolean,
	private var manifest: L2ValueManifest? = null,
	val optionalName: String? = null
) : L2Operand()
{
	/**
	 * A data structure that stores liveness information about registers while
	 * the graph is in SSA form and it's requested to be computed.
	 */
	var liveness: L2Liveness? = null

	/**
	 * Either `null`, the normal case, or a set with each [L2Register] that is
	 * allowed to pass along this edge.  This mechanism is used to break control
	 * flow cycles, allowing a simple liveness algorithm to be used, instead of
	 * iterating (backward) through loops until the live set has converged.
	 */
	var forcedClampedRegisters: Set<L2Register<*>>? = null


	/**
	 * Either `null`, the normal case, or a set with each [L2SemanticValue] that
	 * is allowed to pass along this edge.  This mechanism is used to break
	 * control flow cycles, allowing a simple liveness algorithm to be used,
	 * instead of iterating (backward) through loops until the live set has
	 * converged.
	 */
	var forcedClampedSemanticValues: Set<L2SemanticValue>? = null

	/**
	 * A counter of how many times this edge has been traversed.  This will be
	 * used to determine the amount of effort to apply to subsequent
	 * re-optimization attempts, modulating inlining, order of tests, whether to
	 * optimize for space, run time, or compile time; that sort of thing.  The
	 * counter itself (a [LongAdder]) is passed as a constant through a
	 * special class loader, and captured as a final constant within the
	 * [L2Chunk]'s class.
	 *
	 * Most edges don't benefit from having a counter, and a final optimized
	 * form has no need for any counters, so this field can be `null`.
	 */
	var counter: LongAdder? = null

	override val operandType: L2OperandType get() = PC

	override fun adjustCloneForInstruction(
		theInstruction: L2Instruction,
		generator: L2GeneratorInterface)
	{
		super.adjustCloneForInstruction(theInstruction, generator)
		manifest = null
		counter = null
	}

	override fun addEdgesTo(list: MutableList<L2PcOperand>)
	{
		list.add(this)
	}

	/**
	 * Answer the [L2ValueManifest] for this edge, which describes which
	 * [L2Register]s hold which [L2SemanticValue]s.
	 *
	 * @return
	 *   This edge's [L2ValueManifest].
	 */
	fun manifest(): L2ValueManifest = manifest!!

	/**
	 * If the [L2ValueManifest] has not yet been stripped from the containing
	 * chunk, answer it, otherwise answer `null`.
	 *
	 * @return
	 *   Either this edge's value manifest or `null`.
	 */
	fun manifestOrNull(): L2ValueManifest? = manifest

	/**
	 * Write a clone of the given manifest into this edge.
	 */
	fun setManifestToCloneOf(newManifest: L2ValueManifest)
	{
		manifest = L2ValueManifest(newManifest)
	}

	override fun dispatchOperand(dispatcher: L2OperandDispatcher) =
		dispatcher.doOperand(this)

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		instruction.basicBlock().addSuccessorEdge(this)
		this.manifest = L2ValueManifest(manifest)
		targetBlock.addPredecessorEdge(this)
	}

	override fun instructionWasInserted(
		newInstruction: L2Instruction)
	{
		super.instructionWasInserted(newInstruction)
		newInstruction.basicBlock().addSuccessorEdge(this)
		manifest = L2ValueManifest(manifest())
		targetBlock.addPredecessorEdge(this)
	}

	override fun instructionWasRemoved()
	{
		val sourceBlock = instruction.basicBlock()
		sourceBlock.removeSuccessorEdge(this)
		targetBlock.removePredecessorEdge(this)
		if (instruction.altersControlFlow)
		{
			sourceBlock.removedControlFlowInstruction()
		}
		super.instructionWasRemoved()
	}

	/**
	 * Answer the target [L2BasicBlock] that this operand refers to.
	 *
	 * @return
	 *   The target basic block.
	 */
	fun targetBlock(): L2BasicBlock = targetBlock

	/**
	 * Answer the [targetBlock] that's pointed to by this edge, after skipping
	 * through any blocks that contain only an [L2_JUMP].
	 */
	fun targetBlockSkippingBareJumps(): L2BasicBlock
	{
		var target = targetBlock
		while (true)
		{
			if (target.isIrremovable || target.isLoopHead) return target
			val soleInstruction = target.instructions().singleOrNull()
			if (soleInstruction !is L2_JUMP) return target
			target = soleInstruction.target.targetBlock
		}
	}

	/**
	 * Answer the L2 offset at the start of the [L2BasicBlock] that this operand
	 * refers to.
	 *
	 * @return
	 *   The target L2 offset.
	 */
	fun offset(): Int = targetBlock.offset()

	/**
	 * Answer the source [L2BasicBlock] that this operand is an edge from.
	 *
	 * @return
	 *   The source basic block.
	 */
	fun sourceBlock(): L2BasicBlock = instruction.basicBlock()

	override fun appendTo(builder: StringBuilder)
	{
		// Lead with #block!instr coordinates when both have been assigned.
		val blockNum = targetBlock.blockNumber
		val firstInstrOffset = targetBlock.instructions().firstOrNull()?.offset ?: -1
		if (blockNum >= 0 && firstInstrOffset >= 0)
		{
			builder.append("#$blockNum!$firstInstrOffset ")
		}
		// Show the basic block's name.
		if (offset() != -1)
		{
			builder.append("pc ").append(offset()).append(": ")
		}
		builder.append("--> ")
		builder.append(targetBlock.name())
	}

	/**
	 * In a non-SSA control flow graph that has had its phi functions removed
	 * and converted to moves, switch the target of this edge.
	 *
	 * @param newTarget
	 *   The new target [L2BasicBlock] of this edge.
	 * @param isBackwardFlag
	 *   Whether to also mark it as a backward edge.
	 */
	fun switchTargetBlockNonSSA(
		newTarget: L2BasicBlock,
		isBackwardFlag: Boolean)
	{
		val oldTarget = targetBlock
		targetBlock = newTarget
		oldTarget.removePredecessorEdge(this)
		newTarget.addPredecessorEdge(this)
		isBackward = isBackwardFlag
	}

	/**
	 * Alter the target of this edge, updating the graph as needed.
	 *
	 * @param newTarget
	 *   The new target of this edge.
	 */
	fun changeUngeneratedTarget(
		newTarget: L2BasicBlock)
	{
		val oldTarget = targetBlock
		assert(!oldTarget.hasStartedCodeGeneration)
		assert(!newTarget.hasStartedCodeGeneration)
		targetBlock = newTarget
		oldTarget.removePredecessorEdge(this)
		newTarget.addPredecessorEdge(this)
	}

	/**
	 * Write JVM bytecodes to the JVMTranslator which will push a
	 * [A_RegisterDump] holding the values of the registers live along this
	 * edge.  If the [JVMTranslator] has recorded a
	 * [freeze&#32;reservation][JVMTranslator.freezeReservations] for this
	 * edge's [L2_SAVE_ALL_AND_PC_TO_INT], the dump also has room after those
	 * values for the registers that the freeze subgraph of the corresponding
	 * [L2_CREATE_CONTINUATION] needs.
	 *
	 * Also, associate within the [JVMTranslator] the information needed to
	 * extract these live registers when the target [L2_ENTER_L2_CHUNK] is
	 * reached – from resumption of a continuation (using this register dump)
	 * that is still mutable.
	 *
	 * Note that this also gets invoked when creating the initial reification
	 * edge, from something other than [L2_SAVE_ALL_AND_PC_TO_INT].  That
	 * creates a dummy continuation, to allow reification to proceed in the
	 * right direction (earliest calls first).  There is no L1 progress during
	 * the lifetime of a dummy continuation, and no way for that continuation to
	 * become immutable or shared, so it never needs a freeze reservation.
	 *
	 * @receiver
	 *   The [JVMTranslator] in which to record the saved register dump.
	 * @param fallbackDefaultEntryPoint
	 *   The [DefaultEntryPoint] to jump to in the [DefaultL1Chunk] if the
	 *   continuation becomes immutable or shared and later resumed.
	 */
	fun JVMTranslator.createAndPushRegisterDump(
		fallbackDefaultEntryPoint: DefaultEntryPoint)
	{
		// Capture both the constant L2 offset of the target, and a register
		// dump containing the state of all live registers.  A subsequent
		// L2_CREATE_CONTINUATION will use both, and the L2_ENTER_L2_CHUNK at
		// the target will restore the register dump found in the continuation.
		val layout = registerDumpLayout(this@L2PcOperand, 0, 0)
		when (val targetInstruction = targetBlock.instructions()[0])
		{
			is L2_ENTER_L2_CHUNK ->
			{
				entryPointLiveInfo[targetInstruction.offset] = layout
			}
			else -> throw AssertionError("Invalid target of $this")
		}
		val reservation = freezeReservations[instruction]
		if (layout.isEmpty && reservation === null)
		{
			// Nothing needs to be saved, so we can reuse the empty register
			// dump object.
			loadLiteralObject(
				emptyRegisterDump(fallbackDefaultEntryPoint.offset()))
			return
		}
		// :: [fallback]
		intConstant(fallbackDefaultEntryPoint.offset())
		// Emit code to save live registers' values.  Start with the objects.
		// :: array = new «arrayClass»[«limit»];
		// :: array[0] = ...; array[1] = ...;
		objectArrayFromRegisters(layout.boxed, AvailObject::class.java)
		// Now create the array of longs, including both ints and doubles.
		if (layout.longCount == 0)
		{
			load(JVMChunk.noLongsField)
		}
		else
		{
			intConstant(layout.longCount)
			method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_LONG)
			layout.longRegisters.forEachIndexed { i, register ->
				method.visitInsn(Opcodes.DUP)
				intConstant(i)
				loadRegisterAsLong(register)
				method.visitInsn(Opcodes.LASTORE)
			}
		}
		// :: [fallback, objects, longs, reservedObjects, reservedLongs]
		intConstant(reservation?.objectCount ?: 0)
		intConstant(reservation?.longCount ?: 0)
		generateCall(createRegisterDumpMethod)
	}

	/** Instructions that branch are not eligible for postponement. */
	override fun equivalentTo(other: L2Operand) = unsupported

	/** Instructions that branch are not eligible for postponement. */
	override val equivalentHash: Int get() = unsupported

	/** Instructions that branch are not eligible for postponement. */
	override fun mergeFromOperands(operands: List<L2Operand>) = unsupported

	/**
	 * Create and install a [LongAdder] to count visits through this edge in the
	 * final JVM code.
	 */
	fun installCounter()
	{
		assert(counter === null) // Don't install twice.
		counter = LongAdder()
	}

	override fun postOptimizationCleanup()
	{
		manifest = null
		liveness = null
		forcedClampedSemanticValues = null
	}
}
