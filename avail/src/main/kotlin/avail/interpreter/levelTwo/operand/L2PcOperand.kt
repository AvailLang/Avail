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
import avail.descriptor.representation.A_RegisterDump.Companion.encodeLocalValue
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import avail.exceptions.unsupported
import avail.interpreter.JavaLibrary.bitCastDoubleToLongMethod
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.PC
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK_FOR_CALL
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.DefaultL1ExecutableChunk.DefaultEntryPoint
import avail.optimizer.DefaultL1ExecutableChunk.DefaultL1Chunk
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Entity
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMChunk
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast
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
	 * The [Set] of every [L2Entity] that is written in all pasts, and is
	 * consumed along all future paths after the start of this block.  This is
	 * only populated during optimization, while the control flow graph is still
	 * in SSA form.  A null value should be replaced with a fresh [MutableSet]
	 * when adding the first element.
	 *
	 * This is a subset of [sometimesLiveInEntities].
	 */
	var alwaysLiveInEntities: MutableSet<L2Entity<*>>? = null

	/**
	 * The [Set] of every [L2Entity] that is written in all pasts, and is
	 * consumed along at least one future after the start of this block. This is
	 * only populated during optimization, while the control flow graph is still
	 * in SSA form.  A null value should be replaced with a fresh [MutableSet]
	 * when adding the first element.
	 *
	 * This is a superset of [alwaysLiveInEntities].
	 */
	var sometimesLiveInEntities: MutableSet<L2Entity<*>>? = null

	/**
	 * Either `null`, the normal case, or a set with each [L2Entity] that is
	 * allowed to pass along this edge.  This mechanism is used to break control
	 * flow cycles, allowing a simple liveness algorithm to be used, instead of
	 * iterating (backward) through loops until the live set has converged.
	 */
	var forcedClampedEntities: Set<L2Entity<*>>? = null

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
	 * [A_RegisterDump].  The register dump includes information that a
	 * continuation needs to create elided variables should it become immutable
	 * or shared.
	 *
	 * Also, associate within the [JVMTranslator] the information needed to
	 * extract these live registers when the target [L2_ENTER_L2_CHUNK] is
	 * reached – from resumption of a continuation (using this register dump)
	 * that is still mutable.
	 *
	 * Note that this also gets invoked when creating the initial reification
	 * edge, from something other than [L2_SAVE_ALL_AND_PC_TO_INT], but it only
	 * has to save registers, not deal with capturing local variable
	 * initialization values.  That's because it's a dummy continuation that
	 * gets created at that point, to allow reification to proceed in the right
	 * direction (earliest calls first).  So there is no L1 progress during the
	 * lifetime of a dummy continuation, and no way for that continuation to
	 * become immutable or shared.
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
		val liveMap =
			RegisterKind.all.associateWith { mutableListOf<L2Register<*>>() }
		val liveRegistersList =
			sometimesLiveInEntities!!
				.filterIsInstance<L2Register<*>>()
				.sortedBy(L2Register<*>::finalIndex)
				.distinct()
		liveRegistersList.forEach {
			liveMap[it.kind]!!.add(it)
		}
		// ALSO add any registers that must be preserved because elided variable
		// creation will need it as an initial value (in the reification path).
		val sourceInstruction = instruction
		if (sourceInstruction is L2_SAVE_ALL_AND_PC_TO_INT)
		{
			sourceInstruction.dirtyLocals.elements.forEach { read ->
				liveMap[read.kind]!!.add(read.register())
			}
		}

		// Stably deduplicate them.
		val liveLocalsByKind = liveMap.mapValues { (_, list) ->
			list.map(::localNumberFromRegister).distinct()
		}
		when (val targetInstruction = targetBlock.instructions()[0])
		{
			is L2_ENTER_L2_CHUNK ->
			{
				entryPointLiveInfo[targetInstruction.offset] = liveLocalsByKind
			}
			is L2_ENTER_L2_CHUNK_FOR_CALL ->
			{
				// There should be no live registers on the edge back to the
				// start due to a surviving L2_VIRTUAL_CREATE_LABEL.
				assert(liveMap.values.all(List<*>::isEmpty))
			}
			else -> throw AssertionError("Invalid target of $this")
		}
		if (liveMap.values.all(List<*>::isEmpty))
		{
			// Nothing needs to be saved, so we can reuse the empty register
			// dump object.  Note that since there are no saved values, it's
			// also the case that there are no saved values that would be used
			// to initialize new variables if the continuation becomes immutable
			// or shared.
			loadLiteralObject(
				emptyRegisterDump(fallbackDefaultEntryPoint.offset()))
			return
		}
		// The stack is now AvailObject[], long[].  At least one of the arrays
		// is non-empty.  Create the encoded tuple of local/source info for
		// initializing variables.  See ENCODED_ELIDED_LOCALS in
		// RegisterDumpDescriptor.
		loadLiteralObject(fallbackDefaultEntryPoint.offset())
		if (sourceInstruction is L2_SAVE_ALL_AND_PC_TO_INT)
		{
			// This is a real continuation that can become immutable or shared,
			// so we have to capture the local initialization plan.  That takes
			// the form of a tuple of Ints, alternating between L1 local index
			// and its source in the object array or long array, as specified
			// in RegisterDumpDescriptor.ENCODED_ELIDED_LOCALS.
			assert(sourceInstruction.dirtyLocals.elements.size
				== sourceInstruction.dirtyLocalIndices.constant.size)
			val ints = mutableListOf<Int>()
			sourceInstruction.dirtyLocals.elements.forEachIndexed { i, read ->
				ints.add(sourceInstruction.dirtyLocalIndices.constant[i])
				val liveIndexInKind =
					liveMap[read.kind]!!.indexOf(read.register())
				assert(liveIndexInKind >= 0)
				ints.add(encodeLocalValue(read.kind, liveIndexInKind))
			}
			val intTuple = tupleFromIntegerList(ints).makeShared()
			loadLiteralObject(intTuple)
			// :: encodedIntTuple
		}
		else
		{
			// This is a dummy continuation created to allow L2 code to handle
			// reification itself, when it's its turn to run (oldest calls
			// first).  A dummy continuation only survivess during the chain of
			// reifications, and no L1 progress can be made during that time, so
			// it cannot become immutable or shared.
			loadLiteralObject(nil)
		}
		// Emit code to save live registers' values.  Start with the objects.
		// :: array = new «arrayClass»[«limit»];
		// :: array[0] = ...; array[1] = ...;
		val boxedLocal: List<L2BoxedRegister> = liveMap[BOXED_KIND]!!.cast()
		objectArrayFromRegisters(boxedLocal, AvailObject::class.java)
		// Now create the array of longs, including both ints and doubles.
		val intLocals = liveMap[INTEGER_KIND]!!
		val floatLocals = liveMap[FLOAT_KIND]!!
		val count = intLocals.size + floatLocals.size
		if (count == 0)
		{
			load(JVMChunk.noLongsField)
		}
		else
		{
			intConstant(count)
			method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_LONG)
			var i = 0
			while (i < intLocals.size)
			{
				method.visitInsn(Opcodes.DUP)
				intConstant(i)
				method.visitVarInsn(
					INTEGER_KIND.jvmLoadInstruction,
					localNumberFromRegister(intLocals[i]))
				method.visitInsn(Opcodes.I2L)
				method.visitInsn(Opcodes.LASTORE)
				i++
			}
			for (floatIndex in 0 until floatLocals.size)
			{
				method.visitInsn(Opcodes.DUP)
				intConstant(i)
				method.visitVarInsn(
					FLOAT_KIND.jvmLoadInstruction,
					localNumberFromRegister(floatLocals[floatIndex]))
				generateCall(bitCastDoubleToLongMethod)
				method.visitInsn(Opcodes.LASTORE)
				i++
			}
		}
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
		alwaysLiveInEntities = null
		sometimesLiveInEntities = null
		forcedClampedEntities = forcedClampedEntities
			?.filterIsInstance<L2Register<*>>()
			?.toSet()
	}
}
