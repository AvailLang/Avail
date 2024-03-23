/*
 * L2NewInstruction.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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

package avail.interpreter.levelTwo.new

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.types.A_Type
import avail.exceptions.unsupported
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2Generator
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.utility.cast
import org.objectweb.asm.MethodVisitor
import java.util.concurrent.ConcurrentHashMap

/**
 * An instruction to be placed in an [L2BasicBlock] within an
 * [L2ControlFlowGraph].  It will not be interpreted, but instead converted into
 * JVM byteccodes and executed by the JVM.
 *
 * Subclasses can add fields that are typed with subtypes of [L2Operand], and
 * the [InstructionLayout] will use reflection to make those available for
 * systematic things like finding every [L2ReadBoxedOperand], say, and to
 * extract these operands from the instruction.
 */
abstract class L2NewInstruction : L2Instruction()
{
	/** A short name indicating the kind of operation this is. */
	override val name: String get() = layout.name

	/**
	 * An [InstructionLayout] object, set during construction, which captures
	 * the reflection information necessary for accessing the operands of the
	 * instruction in a generic way.  The layouts are placed in a cache as they
	 * are created, to minimize the reflection cost.
	 */
	@Suppress("UNCHECKED_CAST")
	private val layout: InstructionLayout =
		(
			layoutsByClass[javaClass] ?:
				layoutsByClass.computeIfAbsent(javaClass) {
					InstructionLayout(it.cast())
				}
		).cast()

	/**
	 * Use the cached [layout] to extract all the operands.
	 */
	override val operands: Array<L2Operand> get() = layout.operands(this)

	/**
	 * Use the cached [layout] to extract all the [L2ReadOperand]s, including
	 * those inside [L2ReadVectorOperand]s.
	 */
	override val readOperands: List<L2ReadOperand<*>>
		get() = layout.readOperands(this)

	/**
	 * Use the cached [layout] to extract all the [L2WriteOperand]s, including
	 * those inside [L2WriteBoxedVectorOperand]s.
	 */
	override val writeOperands: List<L2WriteOperand<*>>
		get() = layout.writeOperands(this)

	/**
	 * Evaluate the given function with each [L2Operand] and the
	 * [L2NamedOperandType] that it occupies.
	 *
	 * @param consumer
	 *   The lambda to evaluate.
	 */
	override fun operandsWithNamedTypesDo(
		consumer: (L2Operand, L2NamedOperandType) -> Unit
	) = layout.operandsWithNamedTypesDo(this.cast(), consumer)

	// TODO Override in L2ControlFlowOperation when it becomes an instruction.
	override open val targetEdges: List<L2PcOperand> get() = emptyList()
	override open val altersControlFlow: Boolean get() = false
	override open val hasSideEffect: Boolean get() = false

	// TODO These can turn into type tests when they become instructions.

	override open val isEntryPoint: Boolean get() = false
	override open val isMove: Boolean get() = false
	override open val isMoveBoxed: Boolean get() = false
	override open val isMoveConstant: Boolean get() = false
	override open val isMoveBoxedConstant: Boolean get() = false
	override open val isPhi: Boolean get() = false
	override open val isRunInfalliblePrimitive: Boolean get() = false
	override open val isCreateFunction: Boolean get() = false
	override open val isUnconditionalJumpForward: Boolean get() = false
	override open val isUnconditionalJumpBackward: Boolean get() = false
	override open val isExtractTagOrdinal: Boolean get() = false
	override open val isExtractObjectVariantId: Boolean get() = false
	override open val isExtractObjectTypeVariantId: Boolean get() = false
	override open val isJumpIfSubtypeOfConstant: Boolean get() = false
	override open val isJumpIfSubtypeOfObject: Boolean get() = false
	override open val isGetType: Boolean get() = false
	override open val isEnterL2Chunk: Boolean get() = false
	override open val isEnterL2ChunkForCall: Boolean get() = false
	override fun isBitLogicOperation(op: L2_BIT_LOGIC_OP): Boolean = false
	override open val isHash: Boolean get() = false
	override open val isUnreachableInstruction: Boolean get() = false
	override open val isBoxInt: Boolean get() = false
	override open val isJumpIfUnboxInt: Boolean get() = false
	override open val goesMultipleWays: Boolean get() = false
	override val constantCode: A_RawFunction? get() = null

	override fun <K : RegisterKind<K>> sourceOfMove(): L2ReadOperand<K> =
		unsupported

	override fun extractTupleElement(
		tupleReg: L2ReadOperand<BOXED_KIND>,
		index: Int,
		generator: L2Generator
	): L2ReadBoxedOperand = unsupported

	override fun extractFunctionOuter(
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
		generator: L2Generator
	): L2ReadBoxedOperand = unsupported

	override open val phiSourceRegisterReads: List<L2ReadOperand<*>>
		get() = unsupported
	override open val phiDestinationRegisterWrite: L2WriteOperand<*>
		get() = unsupported
	override open val phiMoveOperation: L2_MOVE<*> get() = unsupported
	override open fun predecessorBlocksForUseOf(
		usedRegister: L2Register<*>
	): List<L2BasicBlock> = unsupported
	override open fun <K : RegisterKind<K>> updateLoopHeadPhi(
		predecessorManifest: L2ValueManifest
	): Unit = unsupported
	override open fun <K : RegisterKind<K>> phiWithoutIndex(
		inputIndex: Int
	): L2Instruction = unsupported

	override open val referenceOfSaveAll: L2PcOperand get() = unsupported

	override open val isCold: Boolean get() = false

	override open fun generateReplacement(regenerator: L2Regenerator) =
		regenerator.basicProcessInstruction(this)

	override val writesHiddenVariablesMask: Int
		get() = layout.writesHiddenVariablesMask

	override val readsHiddenVariablesMask: Int
		get() = layout.readsHiddenVariablesMask

	//fun instructionWasInserted(instruction: L2Instruction)
	//{
	//	if (isEntryPoint(instruction))
	//	{
	//		assert(
	//			instruction.basicBlock().instructions().all {
	//				it.isPhi || it == instruction
	//			}
	//		) {
	//			"Entry point instruction must be after phis"
	//		}
	//	}
	//	instruction.operands.forEach {
	//		it.instructionWasInserted(instruction)
	//	}
	//}
	override fun justInserted()
	{
		TODO("Not yet implemented")
	}

	override open val shouldEmit: Boolean get() = true

	override fun appendToWithWarnings(
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean)->Unit
	)
	{
		TODO("Not yet implemented")
	}

	override fun transformedByRegenerator(
		regenerator: L2Regenerator
	): L2Instruction
	{
		val clone = clone()
		layout.operandFields.forEach { field ->
			field.update(clone.cast(), regenerator::transformOperand)
		}
		return clone
	}

	override open fun emitTransformedInstruction(
		regenerator: L2Regenerator
	): Unit = regenerator.addInstruction(this)

	override open val isPlaceholder: Boolean get() = false

	override open fun interestingConditions(): List<L2SplitCondition?> =
		emptyList()

	override fun simpleAppendTo(builder: StringBuilder)
	{
		TODO("Not yet implemented")
	}

	/**
	 * Print the instruction, using the layout's operandFields for the operand
	 * names and other information shared by instances of the same instruction
	 * subclass.
	 */
	override fun toString() = buildString {
		append("${this@L2NewInstruction.javaClass.simpleName}:\n\t")
		layout.operandFields.joinTo(this, ",\n\t") {
			"${it.name} = ${it.get(this@L2NewInstruction.cast())}"
		}
	}

	/**
	 * Emit JVM bytecodes for performing the action represented by this
	 * instruction.
	 */
	override abstract fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)

	companion object
	{
		// Access to an existing layout generally involves no locks or
		// modification of contended memory.
		val layoutsByClass =
			ConcurrentHashMap<Class<*>, InstructionLayout>(100)
	}
}
