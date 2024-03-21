/*
 * L2Instruction.kt
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
package avail.interpreter.levelTwo

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.types.A_Type
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.L2_BOX_INT
import avail.interpreter.levelTwo.operation.L2_CREATE_FUNCTION
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK_FOR_CALL
import avail.interpreter.levelTwo.operation.L2_EXTRACT_OBJECT_TYPE_VARIANT_ID
import avail.interpreter.levelTwo.operation.L2_EXTRACT_OBJECT_VARIANT_ID
import avail.interpreter.levelTwo.operation.L2_EXTRACT_TAG_ORDINAL
import avail.interpreter.levelTwo.operation.L2_GET_TYPE
import avail.interpreter.levelTwo.operation.L2_HASH
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_JUMP_BACK
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_SUBTYPE_OF_CONSTANT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_SUBTYPE_OF_OBJECT
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_UNBOX_INT
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.interpreter.levelTwo.operation.L2_UNBOX_INT
import avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE
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
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast
import org.objectweb.asm.MethodVisitor

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
 *   The [L2BasicBlock] which will contain this instruction or `null` if none.
 * @param operation
 *   The [L2Operation] that this instruction performs.
 * @param theOperands
 *   The array of [L2Operand]s on which this instruction operates.  These must
 *   agree with the operation's array of [L2NamedOperandType]s.
 */
class L2Instruction
constructor(
	private var basicBlock: L2BasicBlock?,
	private val operation: L2Operation,
	vararg theOperands: L2Operand
): L2AbstractInstruction()
{
	/**
	 * The position of this instruction within its array of instructions.
	 * Only valid near the end of translation.
	 */
	var offset = -1

	/**
	 * The source [L2Register]s.
	 */
	val sourceRegisters = mutableListOf<L2Register<*>>()

	/**
	 * The destination [L2Register]s.
	 */
	val destinationRegisters = mutableListOf<L2Register<*>>()

	/**
	 * The [L2Operand]s to supply to the operation.
	 */
	val operands = run {
		assert(operation.namedOperandTypes.size == theOperands.size)
		for ((i, namedOperandType) in operation.namedOperandTypes.withIndex())
		{
			assert(
				theOperands[i].operandType === namedOperandType.operandType())
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
		operands.forEachIndexed { i, operand ->
			consumer(operand, operation.namedOperandTypes[i])
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
		operands.forEachIndexed { i, operand ->
			when (operand)
			{
				is L2PcOperand ->
					consumer(operand, operation.namedOperandTypes[i].purpose())
				is L2PcVectorOperand ->
					operand.edges.forEach { edge ->
						consumer(edge, operation.namedOperandTypes[i].purpose())
					}
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
		operands.forEachIndexed { i, operand ->
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
		vararg theOperands: L2Operand
	) : this(generator.currentBlock(), operation, *theOperands)

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
	 * Answer a [List] of this instruction's [L2ReadOperand]s.
	 *
	 * @return
	 *  The list of read operands.
	 */
	val readOperands: List<L2ReadOperand<*>>
		get()
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
	val writeOperands: List<L2WriteOperand<*>>
		get()
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
	val targetEdges get() = operation.targetEdges(this)

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
	val altersControlFlow get() = operation.altersControlFlow

	/**
	 * Answer whether this instruction has any observable effect besides
	 * writing to its destination registers.
	 *
	 * @return
	 *   s\Whether this instruction has side effects.
	 */
	val hasSideEffect get() = operation.hasSideEffect(this)

	/**
	 * Answer whether this instruction is an entry point, which uses the
	 * operation [L2_ENTER_L2_CHUNK].
	 *
	 * @return
	 *   Whether the instruction is an entry point.
	 */
	val isEntryPoint get() = operation.isEntryPoint(this)

	/**
	 * Answer whether this operation is a move between (compatible) registers.
	 *
	 * @return
	 *   `true` if this operation simply moves data between two registers of the
	 *   same [RegisterKind], otherwise `false`.
	 */
	val isMove: Boolean get() = operation is L2_MOVE<*>

	val isMoveBoxed: Boolean get() = operation == L2_MOVE.boxed
	/**
	 * Answer whether this operation is a move of a constant to a register.
	 *
	 * @return
	 *   `true` if this operation simply moves constant data to a register,
	 *   otherwise `false`.
	 */
	val isMoveConstant: Boolean get() = operation is L2_MOVE_CONSTANT<*, *>

	/** Answer whether this operation is *boxed* constant move. */
	val isMoveBoxedConstant: Boolean
		get() = operation == L2_MOVE_CONSTANT.Companion.boxed

	/**
	 * Answer whether this instruction is a phi-function.  This is a convenient
	 * fiction that allows control flow to merge while in SSA form.
	 *
	 * @return
	 *   `true` if this is a phi instruction, `false` otherwise.
	 */
	val isPhi get() = operation is L2_PHI_PSEUDO_OPERATION<*>

	/**
	 * Answer true if this instruction runs an infallible primitive, otherwise
	 * false.
	 */
	val isRunInfalliblePrimitive
		get() = operation is L2_RUN_INFALLIBLE_PRIMITIVE

	/**
	 * Answer whether this instruction creates a function from a constant raw
	 * function and a vector of outer values.
	 */
	val isCreateFunction get() = operation is L2_CREATE_FUNCTION

	/**
	 * Answer whether this instruction causes unconditional control flow jump to
	 * another [L2BasicBlock] which is "forward" in the graph.
	 */
	val isUnconditionalJumpForward get() = operation is L2_JUMP

	/**
	 * Answer whether this instruction causes unconditional control flow jump to
	 * another [L2BasicBlock] which is "backward" in the graph.
	 */
	val isUnconditionalJumpBackward get() = operation is L2_JUMP_BACK

	/**
	 * Answer whether this instruction extracts the tag ordinal from some value.
	 */
	val isExtractTagOrdinal get() = operation is L2_EXTRACT_TAG_ORDINAL

	/**
	 * Answer whether this instruction extracts an object's variant id.
	 */
	val isExtractObjectVariantId
		get() = operation is L2_EXTRACT_OBJECT_VARIANT_ID

	/**
	 * Answer whether this instruction extracts an object type's variant id.
	 */
	val isExtractObjectTypeVariantId: Boolean
		get() = operation is L2_EXTRACT_OBJECT_TYPE_VARIANT_ID

	/**
	 * Answer whether this instruction branches based on whether a value in a
	 * boxed register is a subtype of a constant.
	 */
	val isJumpIfSubtypeOfConstant
		get() = operation is L2_JUMP_IF_SUBTYPE_OF_CONSTANT

	/**
	 * Answer whether this instruction branches based on whether a value in a
	 * boxed register is a subtype of a type in another boxed register.
	 */
	val isJumpIfSubtypeOfObject: Boolean
		get() = operation is L2_JUMP_IF_SUBTYPE_OF_OBJECT

	/** Answer whether this instruction gets the type of a value. */
	val isGetType get() = operation is L2_GET_TYPE

	/** Answer whether this instruction is a re-entry point. */
	val isEnterL2Chunk get() = operation is L2_ENTER_L2_CHUNK

	/** Answer whether this instruction is the main entry point. */
	val isEnterL2ChunkForCall get() = operation is L2_ENTER_L2_CHUNK_FOR_CALL

	/**
	 * Answer whether this instruction performs the given infallible bit-logic
	 * operation.
	 */
	fun isBitLogicOperation(op: L2_BIT_LOGIC_OP): Boolean = operation == op

	/**
	 * Answer whether this instruction computes the 32-bit hash of an object.
	 */
	val isHash: Boolean get() = operation == L2_HASH

	/** Ansswer whether this is an unreachable-code instruction. */
	val isUnreachableInstruction: Boolean
		get() = operation is L2_UNREACHABLE_CODE

	/** Answer whether this boxed an int. */
	val isBoxInt: Boolean get() = operation is L2_BOX_INT

	/** Answer whether this unconditional unboxes an int. */
	val isUnboxInt: Boolean get() = operation is L2_UNBOX_INT

	/**
	 * Answer whether this conditionally unboxes an int, jumping somewhere on
	 * failure.
	 */
	val isJumpIfUnboxInt: Boolean get() = operation is L2_JUMP_IF_UNBOX_INT

	/**
	 * Answer true if this instruction leads to multiple targets, *multiple* of
	 * which can be reached.  This is not the same as a branch, in which only
	 * one will be reached for any circumstance of reaching this instruction.
	 * In particular, an [L2_SAVE_ALL_AND_PC_TO_INT] instruction jumps to
	 * its fall-through label, but after reification has saved the live register
	 * state, it gets restored again and winds up traversing the other edge.
	 *
	 * This is an important distinction, in that this type of instruction
	 * should act as a barrier against redundancy elimination.  Otherwise an
	 * object with identity (i.e., a variable) created in the first branch won't
	 * be the same as the one produced in the second branch.
	 *
	 * Also, we must treat as always-live-in to this instruction any values
	 * that are used in *either* branch, since they'll both be taken.
	 *
	 * @return
	 *   Whether multiple branches may be taken following the circumstance of
	 *   arriving at this instruction.
	 */
	val goesMultipleWays: Boolean get() = operation.goesMultipleWays

	/**
	 * Answer the [L2NamedOperandType]s that this instruction requires.
	 *
	 * @return The named operand types that this operation requires.
	 */
	val operandTypes get() = operation.operandTypes

	/**
	 * Extract the constant [A_RawFunction] that's enclosed by the function
	 * produced or passed along by this instruction.  Answer `null` if it cannot
	 * be determined statically.
	 *
	 * @return
	 *   The constant [A_RawFunction] extracted from the instruction, or `null`
	 *   if unknown.
	 */
	val constantCode: A_RawFunction? get() = operation.getConstantCodeFrom(this)

	fun <K: RegisterKind<K>> sourceOfMove(): L2ReadOperand<K> =
		(operation as L2_MOVE<*>).sourceOfMove(this).cast()

	/**
	 * Produce an [L2ReadBoxedOperand] that provides the specified index of the
	 * tuple in the given register.  If the source of that index is not readily
	 * available, generate code to produce it from the tuple, and answer the
	 * resulting [L2ReadBoxedOperand].
	 *
	 * @param tupleReg
	 *   The [L2ReadBoxedOperand] holding the tuple.
	 * @param index
	 *   The one-based index of the tuple element to extract.
	 * @param generator
	 *   The [L2Generator] on which to write code to extract the tuple element,
	 *   if necessary.
	 * @return
	 *   An [L2ReadBoxedOperand] that will contain the specified tuple element.
	 */
	fun extractTupleElement(
		tupleReg: L2ReadOperand<BOXED_KIND>,
		index: Int,
		generator: L2Generator
	): L2ReadBoxedOperand =
		operation.extractTupleElement(tupleReg, index, generator)

	/**
	 * Emit code to extract the specified outer value from the function produced
	 * by this instruction.  The new code is appended to the provided list of
	 * instructions, which may be at a code generation position unrelated to the
	 * receiver.  The extracted outer variable will be written to the provided
	 * target register.
	 *
	 * @param functionRegister
	 *   The register holding the function at the code generation point.
	 * @param outerIndex
	 *   The one-based outer index to extract from the function.
	 * @param outerType
	 *   The type of value that must be in that outer.
	 * @param generator
	 *   The [L2Generator] into which to write the new code.
	 * @return
	 *   The [L2ReadBoxedOperand] holding the outer value.
	 */
	fun extractFunctionOuter(
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
		generator: L2Generator
	): L2ReadBoxedOperand =
		operation.extractFunctionOuter(
			this, functionRegister, outerIndex, outerType, generator)

	/**
	 * Answer the [List] of [L2ReadOperand]s for the receiver, which must be a
	 * phi instruction.  The order is correlated to the instruction's blocks
	 * predecessorEdges.
	 *
	 * @return
	 *   The instruction's list of sources.
	 */
	val phiSourceRegisterReads: List<L2ReadOperand<*>>
		get()
		{
			assert(isPhi)
			return (operation as L2_PHI_PSEUDO_OPERATION<*>)
				.phiSourceRegisterReads(this)
		}

	/**
	 * Answer the [L2WriteOperand] into which a phi-merged value is written.
	 */
	val phiDestinationRegisterWrite: L2WriteOperand<*>
		get()
		{
			assert(isPhi)
			return (operation as L2_PHI_PSEUDO_OPERATION<*>)
				.phiDestinationRegisterWrite(this)
		}


	/**
	 * Given a phi instruction, answer the [L2_MOVE] operation having the same
	 * [RegisterKind].
	 */
	val phiMoveOperation: L2_MOVE<*>
		get() = (operation as L2_PHI_PSEUDO_OPERATION<*>).moveOperation

	/**
	 * Examine this phi instruction and answer the predecessor [L2BasicBlock]s
	 * that supply a value from the specified register.
	 *
	 * @param usedRegister
	 *   The [L2Register] whose use we're trying to trace back to its
	 *   definition.
	 * @return
	 *   A [List] of predecessor blocks that supplied the usedRegister as an
	 *   input to this phi operation.
	 */
	fun predecessorBlocksForUseOf(
		usedRegister: L2Register<*>
	): List<L2BasicBlock>
	{
		assert(isPhi)
		return (operation as L2_PHI_PSEUDO_OPERATION<*>)
			.predecessorBlocksForUseOf(this, usedRegister)
	}

	/**
	 * Update a phi instruction that's in a loop head basic block.
	 *
	 * @param predecessorManifest
	 *   The [L2ValueManifest] in some predecessor edge.
	 */
	fun <K: RegisterKind<K>> updateLoopHeadPhi(
		predecessorManifest: L2ValueManifest)
	{
		val phiOperation: L2_PHI_PSEUDO_OPERATION<K> = operation.cast()
		phiOperation.updateLoopHeadPhi(predecessorManifest, this)
	}

	/**
	 * One of this phi instruction's predecessors has been removed because it's
	 * dead code.  Clean up its vector of inputs by removing the specified
	 * index, answering the new instruction.  If there's only one entry left,
	 * answer a simple move instead.
	 *
	 * @param inputIndex
	 *   The index to remove.
	 * @return
	 *   A replacement [L2Instruction], whose operation may be either another
	 *   `L2_PHI_PSEUDO_OPERATION` or an [L2_MOVE].
	 */
	fun <K: RegisterKind<K>> phiWithoutIndex(
		inputIndex: Int
	): L2Instruction
	{
		val phiOperation: L2_PHI_PSEUDO_OPERATION<K> = operation.cast()
		return phiOperation.phiWithoutIndex(this, inputIndex)
	}

	/**
	 * Given an [L2_SAVE_ALL_AND_PC_TO_INT], extract the edge that leads to the
	 * code that saves the frame's live state.
	 */
	val referenceOfSaveAll: L2PcOperand
		get() = L2_SAVE_ALL_AND_PC_TO_INT.referenceOfSaveAll(this)

	/**
	 * Answer whether this instruction, which occurs at the end of a basic
	 * block, should cause the block to be treated as cold.  Non-terminal blocks
	 * whose successors are all cold are also treated as cold, recursively.  Any
	 * [L2SplitCondition]s that would otherwise be requested by the instructions
	 * in a cold block are ignored.  That's to reduce the amount of fruitless
	 * code splitting that happens along paths that aren't expected to be
	 * reached very often (i.e., they're "cold").  Reification and error paths
	 * are considered cold, among other circumstances.
	 */
	val isCold get() = operation.isCold(this)

	/**
	 * Generate code to replace this [L2Instruction].  Leave the generator in a
	 * state that ensures any [L2SemanticValue]s that would have been written by
	 * the old instruction are instead written by the new code.  Leave the code
	 * regenerator at the point where subsequent instructions of the rebuilt
	 * block will be re-emitted, whether that's in the same block or not.
	 *
	 * @param regenerator
	 *   An [L2Regenerator] that has been configured for writing arbitrary
	 *   replacement code for this instruction.
	 */
	fun generateReplacement(
		regenerator: L2Regenerator
	): Unit = operation.generateReplacement(this, regenerator)

	/**
	 * Determine whether this instruction can commute with [another] instruction
	 * which follows it.  Here are the scenarios that prevent the commutation:
	 *  1. *Both* instructions have side effects.
	 *  2. The second instruction reads a register written by the first.
	 *  3. The later instruction [L2Instruction.goesMultipleWays], and the
	 *   earlier instruction is not an [L2_MOVE_CONSTANT].
	 *  4. One is annotated as [WritesHiddenVariable], and the other is marked
	 *   as either [WritesHiddenVariable] or [ReadsHiddenVariable] for the same
	 *   [HiddenVariableShift].
	 * In all other circumstances, it's acceptable to change the order in which
	 * the instructions execute.
	 *
	 * Note: As of 2024.03.13, this mechanism is not used.  Instead, the
	 * instruction postponement pass allows entirely side-effect-free
	 * instructions to be postponed until their values are needed, or until they
	 * hit an [L2Instruction.goesMultipleWays], but allowing constant moves
	 * through.
	 *
	 * Eventually, the postponement phase will record in each edge a *directed
	 * graph* of instructions, where an edge indicates the source instruction
	 * must execute before the destination instruction.  This will have value in
	 * determining instruction scheduling to produce values at the most
	 * convenient time when making function calls, as well as "sucking down"
	 * phi-equivalent instructions at control flow merges, rather than requiring
	 * only *identical* instructions to be eligible for motion across a merge.
	 */
	fun canCommuteWith(another: L2Instruction): Boolean = when
	{
		hasSideEffect && another.hasSideEffect -> false
		destinationRegisters.intersect(another.sourceRegisters)
			.isNotEmpty() -> false
		another.goesMultipleWays
			&& operation !is L2_MOVE_CONSTANT<*, *> -> false
		else ->
		{
			val writes1 = operation.writesHiddenVariablesMask
			val reads1 = operation.readsHiddenVariablesMask
			val writes2 = another.operation.writesHiddenVariablesMask
			val reads2 = another.operation.readsHiddenVariablesMask
			(writes1 and (writes2 or reads2)) or (reads1 and writes2) == 0
		}
	}


	/**
	 * Replace all registers in this instruction using the registerRemap.  If a
	 * register is not present as a key of that map, leave it alone.  Do not
	 * assume SSA form.
	 *
	 * @param registerRemap
	 *   A mapping from existing [L2Register]s to replacement [L2Register]s
	 *   having the same [L2Register.kind].
	 */
	fun replaceRegisters(registerRemap: Map<L2Register<*>, L2Register<*>>)
	{
		val sourcesBefore: List<L2Register<*>> = sourceRegisters.toList()
		val destinationsBefore: List<L2Register<*>> =
			destinationRegisters.toList()
		operands.forEach { it.replaceRegisters(registerRemap, this) }
		sourceRegisters.replaceAll { r -> registerRemap[r] ?: r }
		destinationRegisters.replaceAll { r -> registerRemap[r] ?: r }
		assert(sourceRegisters.size == sourcesBefore.size)
		assert(destinationRegisters.size == destinationsBefore.size)
	}

	/**
	 * This instruction was just added to its [L2BasicBlock].
	 *
	 * @param manifest
	 *   The [L2ValueManifest] that is active where this instruction was just
	 *   added to its [L2BasicBlock].
	 */
	fun justAdded(manifest: L2ValueManifest)
	{
		if (isEntryPoint)
		{
			assert(
				basicBlock().instructions().all {
					it === this || it.isPhi
				}
			) {
				"Entry point instruction must be after phis"
			}
		}
		operands.forEach { it.setInstruction(this) }
		operation.instructionWasAdded(this, manifest)
	}

	/**
	 * This instruction was just added to its [L2BasicBlock] as part of an
	 * optimization pass.
	 */
	fun justInserted()
	{
		operands.forEach { it.setInstruction(this) }
		operation.instructionWasInserted(this)
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
	 * Remove this instruction from its current [L2BasicBlock], and insert it at
	 * the specified index in the [newBlock]'s instructions.
	 */
	fun moveToBlock(newBlock: L2BasicBlock, index: Int)
	{
		val oldBlock = basicBlock!!
		oldBlock.instructions().remove(this)
		basicBlock = newBlock
		newBlock.instructions().add(index, this)
		if (altersControlFlow)
		{
			assert(index == newBlock.instructions().size - 1)
			assert (oldBlock.hasControlFlowAtEnd)
			oldBlock.hasControlFlowAtEnd = false
			assert (!newBlock.hasControlFlowAtEnd)
			newBlock.hasControlFlowAtEnd = true
			targetEdges.forEach { edge ->
				oldBlock.removeSuccessorEdge(edge)
				newBlock.addSuccessorEdge(edge)
			}
		}
	}

	/**
	 * Answer whether this instruction should be emitted during final code
	 * generation (from the non-SSA [L2ControlFlowGraph] into a flat
	 * sequence of `L2Instruction`s.  Allow the operation to decide.
	 *
	 * @return
	 *   Whether to preserve this instruction during final code generation.
	 */
	val shouldEmit: Boolean get() = operation.shouldEmit(this)

	override fun toString() = buildString {
		appendToWithWarnings(this, L2OperandType.allOperandTypes) { }
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
		operation.appendToWithWarnings(
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
	fun transformAndEmitOn(regenerator: L2Regenerator)
	{
		val transformedOperands = Array(operands.size) {
			regenerator.transformOperand(operand(it))
		}
		operation.emitTransformedInstruction(transformedOperands, regenerator)
	}

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
		operation.translateToJVM(translator, method, this)

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
	 * [generateReplacement].
	 */
	val isPlaceholder get() = operation.isPlaceholder

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

	/**
	 * Returns a list of [L2SplitCondition]s which, if they were satisfied at
	 * this instruction, would be likely to lead to a useful optimization.  If
	 * this condition is determined to be true at some upstream edge, but will
	 * be destroyed after merging control flow, the graph between that edge and
	 * this instruction will be "split" into a duplicate code, allowing that
	 * condition to be preserved.  This eliminates extra type tests, unboxing to
	 * int registers, recomputing stable primitives, etc.
	 *
	 * @return A [List] of [L2SplitCondition] to watch for upstream.
	 */
	fun interestingConditions(): List<L2SplitCondition?> =
		operation.interestingSplitConditions(this)

	/**
	 * Produce a sensible textual rendition of this [L2Instruction].
	 *
	 * @param desiredTypes
	 *   The [L2OperandType]s of [L2Operand]s to be included in generic
	 *   renditions. Customized renditions may not honor these types.
	 * @param builder
	 *   The [StringBuilder] to which the rendition should be written.
	 * @param warningStyleChange
	 *   A mechanism to turn on and off a warning style, which the caller may
	 *   listen to, to track regions of the builder to highlight in its own
	 *   warning style.  This must be invoked in (true, false) pairs.
	 */
	fun appendToWithWarnings(
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit
	): Unit = operation.appendToWithWarnings(
		this, desiredTypes, builder, warningStyleChange)

	/**
	 * Output this [L2Instruction] compactly to the builder.
	 *
	 * @param builder
	 *   The [StringBuilder] on which to write this instruction compactly.
	 */
	fun simpleAppendTo(builder: StringBuilder) =
		operation.simpleAppendTo(this, builder)
}
