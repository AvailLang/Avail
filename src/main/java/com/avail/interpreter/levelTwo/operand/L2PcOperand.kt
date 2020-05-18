/*
 * L2PcOperand.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo.operand

import com.avail.descriptor.functions.ContinuationRegisterDumpDescriptor
import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.JavaLibrary.bitCastDoubleToLongMethod
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandDispatcher
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import com.avail.interpreter.levelTwo.operation.L2_JUMP
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind
import com.avail.optimizer.L2BasicBlock
import com.avail.optimizer.L2ControlFlowGraph
import com.avail.optimizer.L2Entity
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.jvm.JVMChunk
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.values.L2SemanticValue
import com.avail.utility.CollectionExtensions
import com.avail.utility.Nulls
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.*
import java.util.concurrent.atomic.LongAdder
import java.util.function.Consumer

/**
 * An `L2PcOperand` is an operand of type [L2OperandType.PC].
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
 * Construct a new `L2PcOperand` that leads to the specified
 * [L2BasicBlock].  Set [isBackward] to true if this is a
 * back-link to a [loop head][L2BasicBlock.isLoopHead],
 *
 * @param targetBlock
 *   The [L2BasicBlock] The target basic block.
 * @param isBackward
 *   Whether this edge is a back-link to a loop head.
 */
class L2PcOperand constructor(
	private var targetBlock: L2BasicBlock,
	var isBackward: Boolean) : L2Operand()
{
	/**
	 * The manifest linking semantic values and registers at this control flow
	 * edge.
	 */
	private var manifest: L2ValueManifest? = null

	/**
	 * Answer whether this edge points backward to a block marked as
	 * [L2BasicBlock.isLoopHead], thereby closing a loop.
	 */

	/**
	 * The [Set] of [L2Register]s that are written in all pasts, and
	 * are consumed along all future paths after the start of this block.  This
	 * is only populated during optimization, while the control flow graph is
	 * still in SSA form.
	 *
	 *
	 * This is a superset of [sometimesLiveInRegisters].
	 */
	@JvmField
	val alwaysLiveInRegisters = mutableSetOf<L2Register>()

	/**
	 * The [Set] of [L2Register]s that are written in all pasts, and are
	 * consumed along at least one future after the start of this block. This is
	 * only populated during optimization, while the control flow graph is still
	 * in SSA form.
	 *
	 * This is a subset of [alwaysLiveInRegisters].
	 */
	@JvmField
	val sometimesLiveInRegisters= mutableSetOf<L2Register>()

	/**
	 * Either `null`, the normal case, or a set with each [L2Entity]
	 * that is allowed to pass along this edge.  This mechanism is used to break
	 * control flow cycles, allowing a simple liveness algorithm to be used,
	 * instead of iterating (backward) through loops until the live set has
	 * converged.
	 */
	@JvmField
	var forcedClampedEntities: MutableSet<L2Entity>? = null
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
	@JvmField
	var counter: LongAdder? = null

	/**
	 * Create a remapped `L2PcOperand` from the original operand, the new target
	 * [L2BasicBlock], and the transformed [L2ValueManifest]. Set [isBackward]
	 * to true if this is a back-link to a [loop head][L2BasicBlock.isLoopHead].
	 *
	 * @param newTargetBlock
	 *   The transformed target [L2BasicBlock] of the new edge.
	 * @param isBackward
	 *   Whether this edge is a back-link to a loop head.
	 * @param newManifest
	 *   The transformed [L2ValueManifest] for the new edge.
	 */
	constructor(
		newTargetBlock: L2BasicBlock,
		isBackward: Boolean,
		newManifest: L2ValueManifest) : this(newTargetBlock, isBackward)
	{
		manifest = newManifest
	}

	override fun adjustCloneForInstruction(theInstruction: L2Instruction)
	{
		super.adjustCloneForInstruction(theInstruction)
		counter = null
	}

	override fun operandType(): L2OperandType = L2OperandType.PC

	/**
	 * Answer the [L2ValueManifest] for this edge, which describes which
	 * [L2Register]s hold which [L2SemanticValue]s.
	 *
	 * @return
	 *   This edge's [L2ValueManifest].
	 */
	fun manifest(): L2ValueManifest = Nulls.stripNull(manifest)

	override fun dispatchOperand(dispatcher: L2OperandDispatcher)
	{
		dispatcher.doOperand(this)
	}

	override fun instructionWasAdded(manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		instruction().basicBlock().addSuccessorEdge(this)
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
		val sourceBlock = instruction().basicBlock()
		sourceBlock.removeSuccessorEdge(this)
		targetBlock.removePredecessorEdge(this)
		if (instruction().operation().altersControlFlow())
		{
			sourceBlock.removedControlFlowInstruction()
		}
		super.instructionWasRemoved()
	}

	override fun replaceRegisters(
		registerRemap: Map<L2Register, L2Register>,
		theInstruction: L2Instruction)
	{
		forcedClampedEntities?.toMutableList()?.forEach {
			if (registerRemap.containsKey(it))
			{
				forcedClampedEntities!!.remove(it)
				forcedClampedEntities!!.add(registerRemap[it]!!)
			}
		}
		super.replaceRegisters(registerRemap, theInstruction)
	}

	/**
	 * Answer the target [L2BasicBlock] that this operand refers to.
	 *
	 * @return
	 *   The target basic block.
	 */
	fun targetBlock(): L2BasicBlock = targetBlock

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
	fun sourceBlock(): L2BasicBlock = instruction().basicBlock()

	override fun appendTo(builder: StringBuilder)
	{
		// Show the basic block's name.
		if (offset() != -1)
		{
			builder.append("pc ").append(offset()).append(": ")
		}
		builder.append(targetBlock.name())
	}

	/**
	 * Create a new [L2BasicBlock] that will be the new target of this edge, and
	 * write an [L2_JUMP] into the new block to jump to the old target of this
	 * edge.  Be careful to maintain predecessor order at the target block.
	 *
	 * @param controlFlowGraph
	 *   The [L2ControlFlowGraph] being updated.
	 * @return
	 *   The new [L2BasicBlock] that splits the given edge. This block has not
	 *   yet been added to the controlFlowGraph, and the client should do this
	 *   to keep the graph consistent.
	 */
	fun splitEdgeWith(controlFlowGraph: L2ControlFlowGraph): L2BasicBlock
	{
		assert(instructionHasBeenEmitted())

		// Capture where this edge originated.
		val source = instruction()

		// Create a new intermediary block that initially just contains a jump
		// to itself.
		val newBlock = L2BasicBlock(
			"edge-split [${source.basicBlock().name()} / "
			 	+ "${targetBlock.name()}]",
			false,
			source.basicBlock().zone)
		controlFlowGraph.startBlock(newBlock)
		val manifestCopy = L2ValueManifest(manifest())
		newBlock.insertInstruction(
			0,
			L2Instruction(
				newBlock,
				L2_JUMP,
				L2PcOperand(newBlock, isBackward, manifestCopy)))
		val newJump = newBlock.instructions()[0]
		val jumpEdge = L2_JUMP.jumpTarget(newJump)

		// Now swap my target with the new jump's target.  I'll end up pointing
		// to the new block, which will contain a jump pointing to the block I
		// used to point to.
		val finalTarget = targetBlock
		targetBlock = jumpEdge.targetBlock
		jumpEdge.targetBlock = finalTarget
		isBackward = false

		// Fix up the blocks' predecessors edges.
		newBlock.replacePredecessorEdge(jumpEdge, this)
		finalTarget.replacePredecessorEdge(this, jumpEdge)
		return newBlock
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
	 * Write JVM bytecodes to the JVMTranslator which will push:
	 *
	 *  1. An [AvailObject][] containing the value of each live boxed register, and
	 *  1. A `long[]` containing encoded data from each live unboxed register.
	 *
	 * Also, associate within the [JVMTranslator] the information needed to
	 * extract these live registers when the target [L2_ENTER_L2_CHUNK] is
	 * reached.
	 *
	 *
	 * These arrays are suitable arguments for creating a
	 * [ContinuationRegisterDumpDescriptor] instance.
	 *
	 * @param translator
	 *   The [JVMTranslator] in which to record the saved register layout.
	 * @param method
	 *   The [MethodVisitor] on which to write code to push the register dump.
	 */
	fun createAndPushRegisterDumpArrays(
		translator: JVMTranslator, method: MethodVisitor)
	{
		// Capture both the constant L2 offset of the target, and a register
		// dump containing the state of all live registers.  A subsequent
		// L2_CREATE_CONTINUATION will use both, and the L2_ENTER_L2_CHUNK at
		// the target will restore the register dump found in the continuation.
		val targetInstruction = targetBlock.instructions()[0]
		assert(targetInstruction.operation() === L2_ENTER_L2_CHUNK)
		val liveMap =
			CollectionExtensions.populatedEnumMap<
				RegisterKind, MutableList<Int>>(
				RegisterKind::class.java) { ArrayList() }
		val liveRegistersSet: MutableSet<L2Register> = HashSet(alwaysLiveInRegisters)
		liveRegistersSet.addAll(sometimesLiveInRegisters)
		val liveRegistersList = liveRegistersSet.toMutableList()
		liveRegistersList.sortBy { it.finalIndex() }
		liveRegistersList.forEach(Consumer { reg: L2Register ->
			liveMap[reg.registerKind()]!!.add(
				translator.localNumberFromRegister(reg))
		})
		translator.liveLocalNumbersByKindPerEntryPoint[targetInstruction] = liveMap

		// Emit code to save those registers' values.  Start with the objects.
		// :: array = new «arrayClass»[«limit»];
		// :: array[0] = ...; array[1] = ...;
		val boxedLocalNumbers: List<Int> = liveMap[RegisterKind.BOXED]!!
		if (boxedLocalNumbers.isEmpty())
		{
			JVMChunk.noObjectsField.generateRead(method)
		}
		else
		{
			translator.intConstant(method, boxedLocalNumbers.size)
			method.visitTypeInsn(Opcodes.ANEWARRAY, Type.getInternalName(AvailObject::class.java))
			for (i in boxedLocalNumbers.indices)
			{
				method.visitInsn(Opcodes.DUP)
				translator.intConstant(method, i)
				method.visitVarInsn(
					RegisterKind.BOXED.loadInstruction,
					boxedLocalNumbers[i])
				method.visitInsn(Opcodes.AASTORE)
			}
		}
		// Now create the array of longs, including both ints and doubles.
		val intLocalNumbers = liveMap[RegisterKind.INTEGER]!!
		val floatLocalNumbers = liveMap[RegisterKind.FLOAT]!!
		val count = intLocalNumbers.size + floatLocalNumbers.size
		if (count == 0)
		{
			JVMChunk.noLongsField.generateRead(method)
		}
		else
		{
			translator.intConstant(method, count)
			method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_LONG)
			var i = 0
			while (i < intLocalNumbers.size)
			{
				method.visitInsn(Opcodes.DUP)
				translator.intConstant(method, i)
				method.visitVarInsn(
					RegisterKind.INTEGER.loadInstruction,
					intLocalNumbers[i])
				method.visitInsn(Opcodes.I2L)
				method.visitInsn(Opcodes.LASTORE)
				i++
			}
			for (j in 0 until floatLocalNumbers.size)
			{
				method.visitInsn(Opcodes.DUP)
				translator.intConstant(method, i)
				method.visitVarInsn(
					RegisterKind.FLOAT.loadInstruction,
					floatLocalNumbers[i])
				bitCastDoubleToLongMethod.generateCall(method)
				method.visitInsn(Opcodes.LASTORE)
				i++
			}
		}
		// The stack is now AvailObject[], long[].
	}

	/**
	 * Erase all information about the given registers from this edge's
	 * manifest, and in the manifests of any edges recursively reachable from
	 * this edge. Terminate the recursion if an edge already doesn't know any of
	 * these registers.  Ignore back-edges, as their manifests are explicitly
	 * created to contain only the function's arguments.
	 *
	 *
	 * Also fix up synonyms that no longer have backing registers, or that
	 * have lost their last register of a particular [RegisterKind].
	 *
	 * @param registersToForget
	 *   The [Set] of [L2Register]s that we must erase all knowledge about in
	 *   manifests reachable from here.
	 */
	fun forgetRegistersInManifestsRecursively(
		registersToForget: Set<L2Register>)
	{
		val workQueue: Deque<L2PcOperand> = ArrayDeque()
		workQueue.add(this)
		while (!workQueue.isEmpty())
		{
			val edge = workQueue.remove()
			if (edge.isBackward)
			{
				continue
			}
			if (edge.manifest().forgetRegisters(registersToForget))
			{
				workQueue.addAll(edge.targetBlock().successorEdgesCopy())
			}
		}
	}

	/**
	 * Create and install a [LongAdder] to count visits through this edge in the
	 * final JVM code.
	 */
	fun installCounter()
	{
		assert(counter == null // Don't install twice.
		)
		counter = LongAdder()
	}
}