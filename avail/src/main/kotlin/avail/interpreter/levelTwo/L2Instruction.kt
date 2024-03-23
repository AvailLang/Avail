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
import avail.interpreter.levelTwo.new.L2NewInstruction.Companion.toString
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
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
import avail.utility.PublicCloneable
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
 */
abstract class L2Instruction
: L2AbstractInstruction, PublicCloneable<L2Instruction>()
{
	/**
	 * The [L2BasicBlock] to which the instruction belongs.  This only gets set
	 * by [cloneFor] and special cases like [moveToBlock].  It gets cleared in
	 * the new instruction by [clone].
	 */
	private var basicBlock: L2BasicBlock? = null

	/** A short name indicating the kind of operation this is. */
	abstract val name: String

	/**
	 * The position of this instruction within its array of instructions.
	 * Only valid near the end of translation.
	 */
	var offset = -1

	/**
	 * The source [L2Register]s.
	 */
	var sourceRegisters = mutableListOf<L2Register<*>>()
		private set

	/**
	 * The destination [L2Register]s.
	 */
	var destinationRegisters = mutableListOf<L2Register<*>>()
		private set

	/**
	 * The [L2Operand]s to supply to the operation.
	 */
	abstract val operands: Array<L2Operand>

	override fun clone(): L2Instruction =
		super.clone().apply {
			basicBlock = null
			sourceRegisters = mutableListOf()
			destinationRegisters = mutableListOf()
		}

	/**
	 * Copy this instruction, but setting the [basicBlock] in the copy.
	 * Answer the copy.
	 *
	 * @param block
	 *   The [L2BasicBlock] that this instruction's clone will be inserted
	 *   into.
	 * @return
	 *   The cloned [L2Instruction].
	 */
	fun cloneFor(block: L2BasicBlock): L2Instruction
	{
		val clone = clone()
		clone.basicBlock = block
		clone.operands.forEach { operand ->
			operand.addSourceRegistersTo(clone.sourceRegisters)
			operand.addDestinationRegistersTo(clone.destinationRegisters)
		}
		return clone
	}

	/**
	 * Evaluate the given function with each [L2Operand] and the
	 * [L2NamedOperandType] that it occupies.
	 *
	 * @param consumer
	 *   The lambda to evaluate.
	 */
	@Suppress("unused")
	abstract fun operandsWithNamedTypesDo(
		consumer: (L2Operand, L2NamedOperandType) -> Unit)

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
		operandsWithNamedTypesDo { operand, namedOperandType ->
			when (operand)
			{
				is L2PcOperand -> consumer(operand, namedOperandType.purpose)
				is L2PcVectorOperand ->
					operand.edges.forEach { edge ->
						consumer(edge, namedOperandType.purpose)
					}
			}}
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
		operandsWithNamedTypesDo { operand, namedOperandType ->
			if (operand is L2WriteOperand<*>)
			{
				if (namedOperandType.purpose === purpose)
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
	open val readOperands: List<L2ReadOperand<*>>
		get() = mutableListOf<L2ReadOperand<*>>().also { list ->
			operands.forEach { it.addReadsTo(list) }
		}

	/**
	 * Answer a [List] of this instruction's [L2WriteOperand]s.
	 *
	 * @return
	 *   The list of write operands.
	 */
	open val writeOperands: List<L2WriteOperand<*>>
		get() = mutableListOf<L2WriteOperand<*>>().also { list ->
			operands.forEach { it.addWritesTo(list) }
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
	open val targetEdges: List<L2PcOperand> = emptyList()

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
	abstract val altersControlFlow: Boolean

	/**
	 * Answer whether this instruction has any observable effect besides
	 * writing to its destination registers.
	 *
	 * @return
	 *   s\Whether this instruction has side effects.
	 */
	abstract val hasSideEffect: Boolean

	/**
	 * Answer whether this instruction is an entry point, which uses the
	 * operation [L2_ENTER_L2_CHUNK].
	 *
	 * @return
	 *   Whether the instruction is an entry point.
	 */
	abstract val isEntryPoint: Boolean

	/**
	 * Answer whether this operation is a move between (compatible) registers.
	 *
	 * @return
	 *   `true` if this operation simply moves data between two registers of the
	 *   same [RegisterKind], otherwise `false`.
	 */
	abstract val isMove: Boolean

	/**
	 * Answer whether this operation is a move between boxed registers.
	 *
	 * @return
	 *   `true` if this operation simply moves data between two boxed registers,
	 *   otherwise `false`.
	 */
	abstract val isMoveBoxed: Boolean

	/**
	 * Answer whether this operation is a move of a constant to a register.
	 *
	 * @return
	 *   `true` if this operation simply moves constant data to a register,
	 *   otherwise `false`.
	 */
	abstract val isMoveConstant: Boolean

	/** Answer whether this operation is a *boxed* constant move. */
	abstract val isMoveBoxedConstant: Boolean

	/**
	 * Answer whether this instruction is a phi-function.  This is a convenient
	 * fiction that allows control flow to merge while in SSA form.
	 *
	 * @return
	 *   `true` if this is a phi instruction, `false` otherwise.
	 */
	abstract val isPhi: Boolean

	/**
	 * Answer true if this instruction runs an infallible primitive, otherwise
	 * false.
	 */
	abstract val isRunInfalliblePrimitive: Boolean

	/**
	 * Answer whether this instruction creates a function from a constant raw
	 * function and a vector of outer values.
	 */
	abstract val isCreateFunction: Boolean

	/**
	 * Answer whether this instruction causes unconditional control flow jump to
	 * another [L2BasicBlock] which is "forward" in the graph.
	 */
	abstract val isUnconditionalJumpForward: Boolean

	/**
	 * Answer whether this instruction causes unconditional control flow jump to
	 * another [L2BasicBlock] which is "backward" in the graph.
	 */
	abstract val isUnconditionalJumpBackward: Boolean

	/**
	 * Answer whether this instruction extracts the tag ordinal from some value.
	 */
	abstract val isExtractTagOrdinal: Boolean

	/**
	 * Answer whether this instruction extracts an object's variant id.
	 */
	abstract val isExtractObjectVariantId: Boolean

	/**
	 * Answer whether this instruction extracts an object type's variant id.
	 */
	abstract val isExtractObjectTypeVariantId: Boolean

	/**
	 * Answer whether this instruction branches based on whether a value in a
	 * boxed register is a subtype of a constant.
	 */
	abstract val isJumpIfSubtypeOfConstant: Boolean

	/**
	 * Answer whether this instruction branches based on whether a value in a
	 * boxed register is a subtype of a type in another boxed register.
	 */
	abstract val isJumpIfSubtypeOfObject: Boolean

	/** Answer whether this instruction gets the type of a value. */
	abstract val isGetType: Boolean

	/** Answer whether this instruction is a re-entry point. */
	abstract val isEnterL2Chunk: Boolean

	/** Answer whether this instruction is the main entry point. */
	abstract val isEnterL2ChunkForCall: Boolean

	/**
	 * Answer whether this instruction performs the given infallible bit-logic
	 * operation.
	 */
	abstract fun isBitLogicOperation(op: L2_BIT_LOGIC_OP): Boolean

	/**
	 * Answer whether this instruction computes the 32-bit hash of an object.
	 */
	abstract val isHash: Boolean

	/** Ansswer whether this is an unreachable-code instruction. */
	abstract val isUnreachableInstruction: Boolean

	/** Answer whether this boxed an int. */
	abstract val isBoxInt: Boolean

	/**
	 * Answer whether this conditionally unboxes an int, jumping somewhere on
	 * failure.
	 */
	abstract val isJumpIfUnboxInt: Boolean

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
	abstract val goesMultipleWays: Boolean

	/**
	 * Extract the constant [A_RawFunction] that's enclosed by the function
	 * produced or passed along by this instruction.  Answer `null` if it cannot
	 * be determined statically.
	 *
	 * @return
	 *   The constant [A_RawFunction] extracted from the instruction, or `null`
	 *   if unknown.
	 */
	abstract val constantCode: A_RawFunction?

	/**
	 * If this is a move instruction, extract the source [L2ReadOperand] that is
	 * moved by the instruction.  Otherwise fail.
	 *
	 * @param instruction
	 *   The move instruction to examine.
	 * @return
	 *   The move's source [L2ReadOperand].
	 */
	abstract fun <K: RegisterKind<K>> sourceOfMove(): L2ReadOperand<K>

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
	abstract fun extractTupleElement(
		tupleReg: L2ReadOperand<BOXED_KIND>,
		index: Int,
		generator: L2Generator
	): L2ReadBoxedOperand

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
	abstract fun extractFunctionOuter(
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
		generator: L2Generator
	): L2ReadBoxedOperand

	/**
	 * Answer the [List] of [L2ReadOperand]s for the receiver, which must be a
	 * phi instruction.  The order is correlated to the instruction's blocks
	 * predecessorEdges.
	 *
	 * @return
	 *   The instruction's list of sources.
	 */
	abstract val phiSourceRegisterReads: List<L2ReadOperand<*>>

	/**
	 * Answer the [L2WriteOperand] into which a phi-merged value is written.
	 */
	abstract val phiDestinationRegisterWrite: L2WriteOperand<*>

	/**
	 * Given a phi instruction, answer the [L2_MOVE] operation having the same
	 * [RegisterKind].
	 */
	abstract val phiMoveOperation: L2_MOVE<*>

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
	abstract fun predecessorBlocksForUseOf(
		usedRegister: L2Register<*>
	): List<L2BasicBlock>

	/**
	 * Update a phi instruction that's in a loop head basic block.
	 *
	 * @param predecessorManifest
	 *   The [L2ValueManifest] in some predecessor edge.
	 */
	abstract fun <K: RegisterKind<K>> updateLoopHeadPhi(
		predecessorManifest: L2ValueManifest)

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
	abstract fun <K: RegisterKind<K>> phiWithoutIndex(
		inputIndex: Int
	): L2Instruction

	/**
	 * Given an [L2_SAVE_ALL_AND_PC_TO_INT], extract the edge that leads to the
	 * code that saves the frame's live state.
	 */
	abstract val referenceOfSaveAll: L2PcOperand

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
	abstract val isCold: Boolean

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
	abstract fun generateReplacement(
		regenerator: L2Regenerator)

	/**
	 * An [Int] with bit positions corresponding to entries from
	 * [HiddenVariableShift], indicating what hidden state is written by this
	 * instruction.
	 */
	abstract val writesHiddenVariablesMask: Int

	/**
	 * An [Int] with bit positions corresponding to entries from
	 * [HiddenVariableShift], indicating what hidden state is read by this
	 * instruction.
	 */
	abstract val readsHiddenVariablesMask: Int

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
		destinationRegisters.intersect(another.sourceRegisters).isNotEmpty() ->
			false
		another.goesMultipleWays && !isMoveConstant -> false
		else ->
		{
			val writes1 = writesHiddenVariablesMask
			val reads1 = readsHiddenVariablesMask
			val writes2 = another.writesHiddenVariablesMask
			val reads2 = another.readsHiddenVariablesMask
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
		//TODO - move this part to entry point sublasses, when they exist.
		if (isEntryPoint)
		{
			assert(
				basicBlock().instructions().all {
					it.isPhi || it == this
				}
			) {
				"Entry point instruction must be after phis"
			}
		}

		operands.forEach { it.setInstruction(this) }
		instructionWasAdded(manifest)
	}

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
	open fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		// Process all operands without a purpose first.
		operandsWithNamedTypesDo { operand, namedOperandType ->
			namedOperandType.purpose ?: run {
				operand.instructionWasAdded(manifest)
			}
		}
		// Track a copy of the manifest for each purpose that occurs in a
		// non-edge operand, applying their effects.
		val manifestByPurposeOrdinal =
			arrayOfNulls<L2ValueManifest>(Purpose.entries.size)
		operandsWithNamedTypesDo { operand, namedOperandType ->
			val purpose = namedOperandType.purpose
			purpose ?: return@operandsWithNamedTypesDo
			if (operand is L2PcOperand) return@operandsWithNamedTypesDo
			if (operand is L2PcVectorOperand) return@operandsWithNamedTypesDo
			var manifestCopy = manifestByPurposeOrdinal[purpose.ordinal]
			if (manifestCopy === null)
			{
				manifestCopy = L2ValueManifest(manifest)
				manifestByPurposeOrdinal[purpose.ordinal] = manifestCopy
			}
			operand.instructionWasAdded(manifestCopy)
		}
		// Now plug the suitable-purposed manifest copies into each edge.
		operandsWithNamedTypesDo { operand, namedOperandType ->
			val purpose = namedOperandType.purpose
			purpose ?: return@operandsWithNamedTypesDo
			when (operand)
			{
				is L2PcOperand ->
				{
					operand.instructionWasAdded(
						manifestByPurposeOrdinal[purpose.ordinal] ?: manifest)
				}
				is L2PcVectorOperand ->
				{
					val manifestCopy =
						manifestByPurposeOrdinal[purpose.ordinal] ?: manifest
					operand.edges.forEach { edge ->
						edge.instructionWasAdded(manifestCopy)
					}
				}
			}
		}
	}

	/**
	 * This instruction was just added to its [L2BasicBlock] as part of an
	 * optimization pass.
	 */
	abstract fun justInserted()

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
	abstract val shouldEmit: Boolean

	override fun toString() = buildString {
		appendToWithWarnings(this, L2OperandType.allOperandTypes) { }
	}

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
	open fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		operandsWithNamedTypesDo { operand, namedOperandType ->
			if (namedOperandType.operandType() in desiredOperandTypes)
			{
				builder.append("\n\t")
				builder.append(namedOperandType.name())
				builder.append(" = ")
				operand.appendWithWarningsTo(builder, 1, warningStyleChange)
			}
		}
	}

	/**
	 * Produce a sensible preamble for the textual rendition of the specified
	 * [L2Instruction] that includes the [offset][L2Instruction.offset] and
	 * [name][toString] of the `L2Operation`.
	 *
	 * @param builder
	 *   The `StringBuilder` to which the preamble should be written.
	 */
	fun renderPreamble(builder: StringBuilder)
	{
		if (offset != -1)
		{
			builder.append(offset)
			builder.append(". ")
		}
		builder.append(name)
	}

	/**
	 * Create an equivalent of this instruction, transforming each [L2Operand]
	 * through the given [L2Regenerator].  Don't do deeper processing than just
	 * transforming each operand.
	 *
	 * @param regenerator
	 *   The [L2Regenerator] by which to transform the given insstruction.
	 * @return
	 *   A new instruction like the given one.
	 */
	abstract fun transformedByRegenerator(
		regenerator: L2Regenerator
	): L2Instruction

	/**
	 * Given this instruction, which is already a transformation of the same
	 * kind of instruction from an earlier graph, write to the regenerator an
	 * equivalent instruction or seriess of replacement instructions.
	 */
	abstract fun emitTransformedInstruction(regenerator: L2Regenerator)

	/**
	 * Translate the `L2Instruction` into corresponding JVM instructions.
	 *
	 * @param translator
	 *   The [JVMTranslator] responsible for the translation.
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 */
	abstract fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)

	/**
	 * Answer the [L2BasicBlock] to which this instruction belongs.  Fail if it
	 * has not yet been added to a basic block.
	 *
	 * @return
	 *   This instruction's [L2BasicBlock].
	 */
	fun basicBlock(): L2BasicBlock = basicBlock!!

	/**
	 * Answer true iff this instruction is in an [L2BasicBlock].
	 */
	val hasBeenEmitted: Boolean get() = basicBlock !== null

	/**
	 * Determine if this instruction is a place-holding instruction that will be
	 * re-emitted as an arbitrary graph of instructions at some point, via
	 * [generateReplacement].
	 */
	abstract val isPlaceholder: Boolean

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
	abstract fun interestingConditions(): List<L2SplitCondition?>

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
	abstract fun appendToWithWarnings(
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)

	/**
	 * Output this [L2Instruction] compactly to the builder.
	 *
	 * @param builder
	 *   The [StringBuilder] on which to write this instruction compactly.
	 */
	abstract fun simpleAppendTo(builder: StringBuilder)
}
