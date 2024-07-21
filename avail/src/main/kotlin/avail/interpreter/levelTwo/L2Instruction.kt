/*
 * L2Instruction.kt
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

package avail.interpreter.levelTwo

import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.MESSAGE_BUNDLE
import avail.exceptions.unsupported
import avail.interpreter.Primitive
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP.BitOperation
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.interpreter.levelTwo.operation.L2_TUPLE_AT_CONSTANT
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2Generator
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticValue
import avail.utility.PublicCloneable
import avail.utility.Strings
import avail.utility.cast
import avail.utility.mapToSet
import org.objectweb.asm.MethodVisitor
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.internal.impl.metadata.jvm.deserialization.JvmMemberSignature.Field

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
abstract class L2Instruction : L2AbstractInstruction, PublicCloneable<L2Instruction>()
{
	/**
	 * The [L2BasicBlock] to which the instruction belongs.  This only gets set
	 * by [cloneFor] and special cases like [moveToBlock].  It gets cleared in
	 * the new instruction by [clone].
	 */
	private var basicBlock: L2BasicBlock? = null

	/**
	 * The position of this instruction within its array of instructions.
	 * Only valid near the end of translation.
	 */
	var offset = -1

	/**
	 * Use the cached [layout] to extract all the operands.
	 */
	open val operands: Array<L2Operand> get() = layout.operands(this)

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

	/** Strengthen [clone]'s type as a convenience. */
	override fun clone(): L2Instruction =
		super.clone().apply {
			basicBlock = null
			sourceRegisters = mutableListOf()
			destinationRegisters = mutableListOf()
			layout.transformOperands(this@apply, L2Operand::clone)
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
	open fun cloneFor(block: L2BasicBlock): L2Instruction =
		clone().apply {
			basicBlock = block
			operands.forEach { operand ->
				operand.adjustCloneForInstruction(this@apply)
				operand.addSourceRegistersTo(sourceRegisters)
				operand.addDestinationRegistersTo(destinationRegisters)
			}
		}

	/** A short name indicating the kind of operation this is. */
	open val name: String get() = layout.name

	/**
	 * Evaluate the given function with each [L2Operand] and the
	 * [L2NamedOperandType] that it occupies.
	 *
	 * @param consumer
	 *   The lambda to evaluate.
	 */
	fun operandsWithNamedTypesDo(
		consumer: (L2Operand, L2NamedOperandType) -> Unit
	) = layout.operandsWithNamedTypesDo(this, consumer)

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
			}
		}
	}

	/**
	 * Evaluate the given function with each [L2WriteOperand] and its
	 * corresponding [Purpose], which correlates with the `Purpose` along each
	 * outbound [edge][L2PcOperand] to indicate which writes take effect along
	 * which outbound edges.  Note that any [L2WriteBoxedVectorOperand] will
	 * cause the [consumer] to be invoked for each constituent [L2WriteOperand].
	 *
	 * While applicable to all instructions, only those with [altersControlFlow]
	 * can supply a non-null [Purpose].
	 *
	 * @param consumer
	 *   The lambda to evaluate with each [L2WriteOperand] and [Purpose].
	 */
	fun writesAndPurposesDo(
		consumer: (L2WriteOperand<*>, Purpose?) -> Unit)
	{
		if (altersControlFlow)
		{
			operandsWithNamedTypesDo { operand, namedOperandType ->
				when (operand)
				{
					is L2WriteOperand<*> ->
						consumer(operand, namedOperandType.purpose)
					is L2WriteBoxedVectorOperand ->
						operand.elements.forEach { write ->
							consumer(write, namedOperandType.purpose)
						}
				}
			}
		}
		else
		{
			// Quicker than visiting all operands.
			writeOperands.forEach { consumer(it, null) }
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
		get() = layout.readOperands(this)

	/**
	 * Answer a [List] of this instruction's [L2WriteOperand]s.
	 *
	 * @return
	 *   The list of write operands.
	 */
	open val writeOperands: List<L2WriteOperand<*>>
		get() = layout.writeOperands(this)

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
	open val targetEdges: List<L2PcOperand> get() = emptyList()

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
	open val altersControlFlow get() = false

	/**
	 * Answer whether this instruction has any observable effect besides
	 * writing to its destination registers.
	 *
	 * @return
	 *   s\Whether this instruction has side effects.
	 */
	open val hasSideEffect get() = false

	/**
	 * Answer whether this instruction is an entry point, which uses the
	 * operation [L2_ENTER_L2_CHUNK].
	 *
	 * @return
	 *   Whether the instruction is an entry point.
	 */
	open val isEntryPoint get() = false

	/**
	 * Answer whether this instruction performs the given infallible bit-logic
	 * operation.
	 */
	open fun isBitLogicOperation(op: BitOperation) = false

	/**
	 * Extract the constant [A_RawFunction] that's enclosed by the function
	 * produced or passed along by this instruction.  Answer `null` if it cannot
	 * be determined statically.
	 *
	 * @return
	 *   The constant [A_RawFunction] extracted from the instruction, or `null`
	 *   if unknown.
	 */
	open val constantCode: A_RawFunction? get() = null

	/**
	 * Produce code to extract the specified [index] of the [tupleRead], writing
	 * it to the [destinationSemanticValues].
	 *
	 * @param tupleRead
	 *   The [L2ReadBoxedOperand] holding the tuple.
	 * @param index
	 *   The one-based index of the tuple element to extract.
	 * @param destinationSemanticValues
	 *   The [L2SemanticBoxedValue]s that will containing the element.
	 * @param generator
	 *   The [L2Generator] on which to write code to extract the tuple element,
	 *   if necessary.
	 */
	open fun extractTupleElement(
		tupleRead: L2ReadBoxedOperand,
		index: Int,
		destinationSemanticValues: Set<L2SemanticBoxedValue>,
		generator: L2Generator)
	{
		val elementType = tupleRead.type().typeAtIndex(index)
		val write = generator.boxedWrite(
			destinationSemanticValues,
			boxedRestrictionForType(elementType))
		generator.addInstruction(
			L2_TUPLE_AT_CONSTANT(
				tupleRead, L2IntImmediateOperand(index), write))
	}

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
	open fun extractFunctionOuter(
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
		generator: L2Generator
	): L2ReadBoxedOperand = unsupported

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
	open val isCold get() = false

	/**
	 * An [Int] with bit positions corresponding to entries from
	 * [HiddenVariableShift], indicating what hidden state is written by this
	 * instruction.
	 */
	open val writesHiddenVariablesMask: Int
		get() = layout.writesHiddenVariablesMask

	/**
	 * An [Int] with bit positions corresponding to entries from
	 * [HiddenVariableShift], indicating what hidden state is read by this
	 * instruction.
	 */
	open val readsHiddenVariablesMask: Int
		get() = layout.readsHiddenVariablesMask

	/**
	 * Generate code to replace this [L2Instruction].  Leave the generator in a
	 * state that ensures any [L2SemanticValue]s that would have been written by
	 * the old instruction are instead written by the new code.  Leave the code
	 * regenerator at the point where subsequent instructions of the rebuilt
	 * block will be re-emitted, whether that's in the same block or not.
	 *
	 * Note that the receiver, the instruction, has already undergone a basic
	 * transformation from registers and blocks of the old graph into registers
	 * and blocks in the new graph, so the operands can be directly used in
	 * alternative instructions.
	 *
	 * @param regenerator
	 *   An [L2Regenerator] that has been configured for writing arbitrary
	 *   replacement code for this instruction, which has already had its
	 *   operands transformed for the new graph.
	 */
	open fun generateReplacement(regenerator: L2Regenerator) =
		emitTransformedInstruction(regenerator)

	/**
	 * Determine whether this instruction can commute with [another] instruction
	 * which follows it.  Here are the scenarios that prevent the commutation:
	 *  1. *Both* instructions have side effects.
	 *  2. The second instruction reads a register written by the first.
	 *  3. The later instruction is an [L2_SAVE_ALL_AND_PC_TO_INT], and the
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
	 * hit an [L2_SAVE_ALL_AND_PC_TO_INT], but allowing constant moves through.
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
		another is L2_SAVE_ALL_AND_PC_TO_INT
			&& this !is L2_MOVE_CONSTANT<*, *>
			-> false
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
					it is L2_PHI<*> || it == this
				}
			) {
				"Entry point instruction must be after phis"
			}
		}
		operands.forEach { it.setInstruction(this) }
		if (manifest.hasEliminatedPhis)
		{
			// Remove register definitions for any registers that are about to
			// be overwritten by this instruction.
			val registersToBeOverwritten =
				writeOperands.mapToSet { it.register() }
			manifest.removeRegisters(registersToBeOverwritten)
		}
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
		operandsWithNamedTypesDo nextOperand@{ operand, namedOperandType ->
			val purpose = namedOperandType.purpose
			purpose ?: return@nextOperand
			if (operand is L2PcOperand) return@nextOperand
			if (operand is L2PcVectorOperand) return@nextOperand
			var manifestCopy = manifestByPurposeOrdinal[purpose.ordinal]
			if (manifestCopy === null)
			{
				manifestCopy = L2ValueManifest(manifest)
				manifestByPurposeOrdinal[purpose.ordinal] = manifestCopy
			}
			operand.instructionWasAdded(manifestCopy)
		}
		// Now plug the suitably-purposed manifest copies into each edge.
		operandsWithNamedTypesDo nextOperand@{ operand, namedOperandType ->
			val purpose = namedOperandType.purpose
			purpose ?: return@nextOperand
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
	fun justInserted()
	{
		operands.forEach { it.setInstruction(this) }
		if (isEntryPoint)
		{
			assert(
				basicBlock().instructions().all {
					it is L2_PHI<*> || it == this
				}
			) {
				"Entry point instruction must be after phis"
			}
		}
		operands.forEach {
			it.instructionWasInserted(this)
		}
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
	open val shouldEmit get() = true

	/**
	 * Answer a collection of [L2ReadBoxedOperand]s that this instruction reads.
	 * Reads that can't destroy the value, or modify it in any detectable way,
	 * can be omitted.
	 */
	open val readsThatMightDestroy: List<L2ReadBoxedOperand>
		get() = readOperands.filterIsInstance<L2ReadBoxedOperand>()

	/**
	 * Print the instruction, using the layout's operandFields for the operand
	 * names and other information shared by instances of the same instruction
	 * subclass.
	 */
	override fun toString() = buildString {
		val instruction = this@L2Instruction
		append("${instruction::class.simpleName}:\n\t")
		var pairs = mutableListOf<Pair<String, L2Operand>>()
		operandsWithNamedTypesDo { operand, namedOperandType ->
			pairs.add(namedOperandType.name to operand)
		}
		pairs.joinTo(this, ",\n\t") { (name, operand) ->
			val operandString =
				Strings.increaseIndentation(operand.toString(), 2)
			"$name = $operandString"
		}
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
	 * [name][toString] of the instruction.
	 *
	 * @param builder
	 *   The [StringBuilder] to which the preamble should be written.
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
	open fun transformedByRegenerator(
		regenerator: L2Regenerator
	): L2Instruction
	{
		val clone = clone()
		layout.updateOperands(clone, regenerator::transformOperand)
		return clone
	}

	/**
	 * Given this instruction, which is already a transformation of the same
	 * kind of instruction from an earlier graph, write to the regenerator an
	 * equivalent instruction or seriess of replacement instructions.
	 */
	open fun emitTransformedInstruction(regenerator: L2Regenerator): Unit =
		regenerator.addInstruction(this)

	/**
	 * Examine each [L2ReadOperand], and if it's restricted to a constant,
	 * replace its register with a fresh one that has no definition. Do not do
	 * the additional step of removing [L2_MOVE_CONSTANT] instructions, as some
	 * could provide values through a phi (or its replacement non-SSA moves),
	 * and can't be removed. A separate pass will remove them if they're truly
	 * dead code.
	 *
	 * @param generator
	 *   The [L2GeneratorInterface] that's used to generate unique ids.
	 */
	open fun replaceConstantReads(
		generator: L2GeneratorInterface,
		registerToValueMap: MutableMap<L2Register<*>, L2SemanticValue<*>>)
	{
		var any = false
		readOperands.forEach { readOperand ->
			if (readOperand.restriction().constantOrNull !== null)
			{
				readOperand.replaceConstantRead()
				any = true
			}
			else
			{
//TODO Remove L2SemanticDummy entirely?
//				readOperand.replaceNonconstantRead(
//					generator, registerToValueMap)
			}
		}
//TODO Remove?
		//writeOperands.forEach {
		//	it.clearSemanticValues(generator, registerToValueMap)
		//}
		if (any)
		{
			// Rebuild the sourceRegisters list if anything changed.
			sourceRegisters.clear()
			readOperands.forEach { operand ->
				operand.addSourceRegistersTo(sourceRegisters)
			}
		}
	}

	/**
	 * Translate the [L2Instruction] into corresponding JVM instructions.
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
	open val isPlaceholder: Boolean get() = false

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
	open fun interestingConditions(): List<L2SplitCondition?> = emptyList()

	/**
	 * Output this [L2Instruction] compactly to the builder.
	 *
	 * @param builder
	 *   The [StringBuilder] on which to write this instruction compactly.
	 */
	fun simpleAppendTo(builder: StringBuilder)
	{
		renderPreamble(builder)
		builder.append(": ")
		val targets = mutableListOf<String>()
		val sources = mutableListOf<String>()
		val commands = mutableListOf<String>()
		for (operand in operands)
		{
			when (operand)
			{
				is L2ArbitraryConstantOperand<*> -> when(
					val constant = operand.constant)
				{
					is Primitive -> commands.add(constant.name)
					else -> sources.add(
						Strings
							.escape(operand.constant.javaClass.simpleName.run {
								if (length > 20) substring(0, 20) + "…"
								else this
							}).run { substring(1, length - 1) })
				}
				is L2ConstantOperand ->
				{
					val value = operand.constant
					when
					{
						value.isInstanceOf(MESSAGE_BUNDLE.o) ->
						{
							commands.add(
								operand.constant.message.atomName.toString())
						}
						value.isFunction ->
						{
							val code: A_RawFunction = value.code()
							var str = code.methodName.asNativeString()
							val mod = code.module
							if (mod.notNil)
							{
								val shortName = mod.shortModuleNameNative
								val line = code.codeStartingLineNumber
								str += "@$shortName:$line"
							}
							sources.add(str)
						}
						value.isInstanceOf(mostGeneralCompiledCodeType()) ->
						{
							val code: A_RawFunction = value
							var str = code.methodName.asNativeString()
							val mod = code.module
							if (mod.notNil)
							{
								val shortName = mod.shortModuleNameNative
								val line = code.codeStartingLineNumber
								str += "@$shortName:$line"
							}
							sources.add(str)
						}
						else -> sources.add(
							Strings.escape(value.toString().run {
								if (length > 20) substring(0, 20) + "…"
								else this
							}).run { substring(1, length - 1) })
					}
				}
				is L2FloatImmediateOperand ->
					sources.add(operand.value.toString())
				is L2IntImmediateOperand ->
					sources.add(operand.value.toString())
				is L2ReadOperand<*> ->
					sources.add(operand.register().toString())
				is L2ReadVectorOperand<*> -> sources.add(
					operand.elements.joinToString(", ", "[", "]") {
						it.register().toString()
					})
				is L2WriteOperand<*> ->
					targets.add(operand.register().toString())
			}
		}
		targets.joinTo(builder)
		if (sources.isNotEmpty() || commands.isNotEmpty())
		{
			builder.append(" ⇦ ")
			commands.joinTo(builder, "/")
			sources.joinTo(builder, ", ", "(", ")")
		}
	}

	/**
	 * An [InstructionLayout] object, set during construction, which captures
	 * the reflection information necessary for accessing the operands of the
	 * instruction in a generic way.  The layouts are placed in a cache as they
	 * are created, to minimize the reflection cost.
	 */
	val layout: InstructionLayout<out L2Instruction> =
		InstructionLayout.layoutForClass(this::class)!!

	/**
	 * Generically render all [operands][L2Operand] of this [L2Instruction],
	 * except for those linked to the given [Field]s.
	 *
	 * @param excludedFields
	 *   The vararg array of [fields][KProperty] whose corresponding
	 *   [L2Operand]s should be excluded.
	 * @param desiredOperandTypes
	 *   The [L2OperandType]s of [L2Operand]s to be included in generic
	 *   renditions. Customized renditions may not honor these types.
	 * @param builder
	 *   The [StringBuilder] to which the rendition should be written.
	 */
	fun renderOperandsExcludingFields(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		vararg excludedFields: KMutableProperty0<out L2Operand>)
	{
		val excludedNames = excludedFields.mapTo(mutableSetOf()) { it.name }
		operandsWithNamedTypesDo { operand, namedOperandType ->
			if (namedOperandType.operandType in desiredOperandTypes
				&& namedOperandType.name !in excludedNames)
			{
				builder.append("\n\t")
				builder.append(namedOperandType.name())
				builder.append(" = ")
				builder.append(
					Strings.increaseIndentation(operand.toString(), 2))
			}
		}
	}
}
