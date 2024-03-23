/*
 * L2OldInstruction.kt
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
import avail.interpreter.levelTwo.new.L2NewInstruction
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
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
 * This class is lightly deprecated, and is intended to be gradually supplanted
 * by [L2NewInstruction].  Its representation is some basic things like an
 * [L2BasicBlock] (only set when the instruction is part of that block), and an
 * [L2Operation], where the behavioral variation currently (2024-03-21) lies.
 * [L2NewInstruction] will eventually have subclasses to support that variation.
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
 * Construct a new `L2OldInstruction`.
 *
 * @param basicBlock
 *   The [L2BasicBlock] which will contain this instruction or `null` if none.
 * @param operation
 *   The [L2Operation] that this instruction performs.
 * @param theOperands
 *   The array of [L2Operand]s on which this instruction operates.  These must
 *   agree with the operation's array of [L2NamedOperandType]s.
 */
class L2OldInstruction
constructor(
	private val operation: L2Operation,
	vararg theOperands: L2Operand
): L2Instruction()
{
	override val name: String get() = operation.name

	override val writesHiddenVariablesMask: Int
		get() = operation.writesHiddenVariablesMask

	override val readsHiddenVariablesMask: Int
		get() = operation.readsHiddenVariablesMask

	override val operands: Array<L2Operand>

	init
	{
		assert(operation.namedOperandTypes.size == theOperands.size)
		operands = Array(theOperands.size) { i ->
			val operand = theOperands[i].clone()
			assert(operand.operandType
				== operation.namedOperandTypes[i].operandType)
			operand.adjustCloneForInstruction(this)
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
	override fun operandsWithNamedTypesDo(
		consumer: (L2Operand, L2NamedOperandType) -> Unit)
	{
		operands.forEachIndexed { i, operand ->
			consumer(operand, operation.namedOperandTypes[i])
		}
	}

	override val targetEdges get() = operation.targetEdges(this)

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
	override val altersControlFlow get() = operation.altersControlFlow

	/**
	 * Answer whether this instruction has any observable effect besides
	 * writing to its destination registers.
	 *
	 * @return
	 *   s\Whether this instruction has side effects.
	 */
	override val hasSideEffect get() = operation.hasSideEffect(this)

	/**
	 * Answer whether this instruction is an entry point, which uses the
	 * operation [L2_ENTER_L2_CHUNK].
	 *
	 * @return
	 *   Whether the instruction is an entry point.
	 */
	override val isEntryPoint get() = operation.isEntryPoint(this)

	/**
	 * Answer whether this operation is a move between (compatible) registers.
	 *
	 * @return
	 *   `true` if this operation simply moves data between two registers of the
	 *   same [RegisterKind], otherwise `false`.
	 */
	override val isMove: Boolean get() = operation is L2_MOVE<*>

	override val isMoveBoxed: Boolean get() = operation == L2_MOVE.boxed
	/**
	 * Answer whether this operation is a move of a constant to a register.
	 *
	 * @return
	 *   `true` if this operation simply moves constant data to a register,
	 *   otherwise `false`.
	 */
	override val isMoveConstant: Boolean get() = operation is L2_MOVE_CONSTANT<*, *>

	/** Answer whether this operation is *boxed* constant move. */
	override val isMoveBoxedConstant: Boolean
		get() = operation == L2_MOVE_CONSTANT.Companion.boxed

	/**
	 * Answer whether this instruction is a phi-function.  This is a convenient
	 * fiction that allows control flow to merge while in SSA form.
	 *
	 * @return
	 *   `true` if this is a phi instruction, `false` otherwise.
	 */
	override val isPhi get() = operation is L2_PHI_PSEUDO_OPERATION<*>

	/**
	 * Answer true if this instruction runs an infallible primitive, otherwise
	 * false.
	 */
	override val isRunInfalliblePrimitive
		get() = operation is L2_RUN_INFALLIBLE_PRIMITIVE

	/**
	 * Answer whether this instruction creates a function from a constant raw
	 * function and a vector of outer values.
	 */
	override val isCreateFunction get() = operation is L2_CREATE_FUNCTION

	/**
	 * Answer whether this instruction causes unconditional control flow jump to
	 * another [L2BasicBlock] which is "forward" in the graph.
	 */
	override val isUnconditionalJumpForward get() = operation is L2_JUMP

	/**
	 * Answer whether this instruction causes unconditional control flow jump to
	 * another [L2BasicBlock] which is "backward" in the graph.
	 */
	override val isUnconditionalJumpBackward get() = operation is L2_JUMP_BACK

	/**
	 * Answer whether this instruction extracts the tag ordinal from some value.
	 */
	override val isExtractTagOrdinal get() = operation is L2_EXTRACT_TAG_ORDINAL

	/**
	 * Answer whether this instruction extracts an object's variant id.
	 */
	override val isExtractObjectVariantId
		get() = operation is L2_EXTRACT_OBJECT_VARIANT_ID

	/**
	 * Answer whether this instruction extracts an object type's variant id.
	 */
	override val isExtractObjectTypeVariantId: Boolean
		get() = operation is L2_EXTRACT_OBJECT_TYPE_VARIANT_ID

	/**
	 * Answer whether this instruction branches based on whether a value in a
	 * boxed register is a subtype of a constant.
	 */
	override val isJumpIfSubtypeOfConstant
		get() = operation is L2_JUMP_IF_SUBTYPE_OF_CONSTANT

	/**
	 * Answer whether this instruction branches based on whether a value in a
	 * boxed register is a subtype of a type in another boxed register.
	 */
	override val isJumpIfSubtypeOfObject: Boolean
		get() = operation is L2_JUMP_IF_SUBTYPE_OF_OBJECT

	/** Answer whether this instruction gets the type of a value. */
	override val isGetType get() = operation is L2_GET_TYPE

	/** Answer whether this instruction is a re-entry point. */
	override val isEnterL2Chunk get() = operation is L2_ENTER_L2_CHUNK

	/** Answer whether this instruction is the main entry point. */
	override val isEnterL2ChunkForCall get() = operation is L2_ENTER_L2_CHUNK_FOR_CALL

	/**
	 * Answer whether this instruction performs the given infallible bit-logic
	 * operation.
	 */
	override fun isBitLogicOperation(op: L2_BIT_LOGIC_OP): Boolean = operation == op

	/**
	 * Answer whether this instruction computes the 32-bit hash of an object.
	 */
	override val isHash: Boolean get() = operation == L2_HASH

	/** Ansswer whether this is an unreachable-code instruction. */
	override val isUnreachableInstruction: Boolean
		get() = operation is L2_UNREACHABLE_CODE

	/** Answer whether this boxed an int. */
	override val isBoxInt: Boolean get() = operation is L2_BOX_INT

	/**
	 * Answer whether this conditionally unboxes an int, jumping somewhere on
	 * failure.
	 */
	override val isJumpIfUnboxInt: Boolean get() = operation is L2_JUMP_IF_UNBOX_INT

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
	override val goesMultipleWays: Boolean get() = operation.goesMultipleWays

	/**
	 * Extract the constant [A_RawFunction] that's enclosed by the function
	 * produced or passed along by this instruction.  Answer `null` if it cannot
	 * be determined statically.
	 *
	 * @return
	 *   The constant [A_RawFunction] extracted from the instruction, or `null`
	 *   if unknown.
	 */
	override val constantCode: A_RawFunction?
		get() = operation.getConstantCodeFrom(this)

	override fun <K: RegisterKind<K>> sourceOfMove(): L2ReadOperand<K> =
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
	override fun extractTupleElement(
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
	override fun extractFunctionOuter(
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
	override val phiSourceRegisterReads: List<L2ReadOperand<*>>
		get()
		{
			assert(isPhi)
			return (operation as L2_PHI_PSEUDO_OPERATION<*>)
				.phiSourceRegisterReads(this)
		}

	/**
	 * Answer the [L2WriteOperand] into which a phi-merged value is written.
	 */
	override val phiDestinationRegisterWrite: L2WriteOperand<*>
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
	override val phiMoveOperation: L2_MOVE<*>
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
	override fun predecessorBlocksForUseOf(
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
	override fun <K: RegisterKind<K>> updateLoopHeadPhi(
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
	 *   A replacement [L2OldInstruction], whose operation may be either another
	 *   `L2_PHI_PSEUDO_OPERATION` or an [L2_MOVE].
	 */
	override fun <K: RegisterKind<K>> phiWithoutIndex(
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
	override val referenceOfSaveAll: L2PcOperand
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
	override val isCold get() = operation.isCold(this)

	/**
	 * Generate code to replace this [L2OldInstruction].  Leave the generator in a
	 * state that ensures any [L2SemanticValue]s that would have been written by
	 * the old instruction are instead written by the new code.  Leave the code
	 * regenerator at the point where subsequent instructions of the rebuilt
	 * block will be re-emitted, whether that's in the same block or not.
	 *
	 * @param regenerator
	 *   An [L2Regenerator] that has been configured for writing arbitrary
	 *   replacement code for this instruction.
	 */
	override fun generateReplacement(
		regenerator: L2Regenerator
	): Unit = operation.generateReplacement(this, regenerator)

	/**
	 * This is the operation for the given instruction, which was just added to
	 * its basic block.  Do any post-processing appropriate for having added
	 * the instruction.  Its operands have already had their instruction fields
	 * set to the given instruction.
	 *
	 * Automatically handle [L2WriteOperand]s that list a
	 * [L2NamedOperandType.Purpose] in their corresponding [L2NamedOperandType],
	 * ensuring the write is only considered to happen along the edge
	 * ([L2PcOperand]) having the same purpose.  Subclasses may want to do
	 * additional postprocessing.
	 *
	 * @param instruction
	 *   The [L2Instruction] that was just added.
	 * @param manifest
	 *   The [L2ValueManifest] that is active at this instruction.
	 */
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		operation.instructionWasAdded(this, manifest)
	}

	/**
	 * This instruction was just added to its [L2BasicBlock] as part of an
	 * optimization pass.
	 */
	override fun justInserted()
	{
		operands.forEach { it.setInstruction(this) }
		operation.instructionWasInserted(this)
	}

	/**
	 * Answer whether this instruction should be emitted during final code
	 * generation (from the non-SSA [L2ControlFlowGraph] into a flat
	 * sequence of `L2OldInstruction`s.  Allow the operation to decide.
	 *
	 * @return
	 *   Whether to preserve this instruction during final code generation.
	 */
	override val shouldEmit: Boolean get() = operation.shouldEmit(this)

	/**
	 * Output this instruction to the given builder, invoking the given lambda
	 * with a boolean to turn warning style on or off, if tracked by the caller.
	 *
	 * @param builder
	 *   Where to write the description of this instruction.
	 * @param desiredOperandTypes
	 *   Which [L2OperandType]s to include.
	 * @param warningStyleChange
	 *   A lambda that takes `true` to start the warning style at the
	 *   current builder position, and `false` to end it.  It must be invoked in
	 *   (true, false) pairs.
	 */
	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean) -> Unit)
	{
		if (!hasBeenEmitted)
		{
			warningStyleChange(true)
			builder.append("DEAD: ")
			warningStyleChange(false)
		}
		operation.appendToWithWarnings(
			this,
			desiredOperandTypes,
			builder,
			warningStyleChange)
	}

	override fun transformedByRegenerator(
		regenerator: L2Regenerator
	): L2Instruction
	{
		val newOperands = operands.map {
			regenerator.transformOperand(it)
		}
		return L2OldInstruction(operation, *newOperands.toTypedArray())
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator
	) = operation.emitTransformedInstruction(operands, regenerator)

	/**
	 * Translate the `L2OldInstruction` into corresponding JVM instructions.
	 *
	 * @param translator
	 *   The [JVMTranslator] responsible for the translation.
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 */
	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor
	) = operation.translateToJVM(translator, method, this)

	/**
	 * Determine if this instruction is a place-holding instruction that will be
	 * re-emitted as an arbitrary graph of instructions at some point, via
	 * [generateReplacement].
	 */
	override val isPlaceholder get() = operation.isPlaceholder

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
	override fun interestingConditions(): List<L2SplitCondition?> =
		operation.interestingSplitConditions(this)

	/**
	 * Produce a sensible textual rendition of this [L2OldInstruction].
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
	override fun appendToWithWarnings(
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit
	): Unit = operation.appendToWithWarnings(
		this, desiredTypes, builder, warningStyleChange)

	/**
	 * Output this [L2OldInstruction] compactly to the builder.
	 *
	 * @param builder
	 *   The [StringBuilder] on which to write this instruction compactly.
	 */
	override fun simpleAppendTo(builder: StringBuilder) =
		operation.simpleAppendTo(this, builder)
}
