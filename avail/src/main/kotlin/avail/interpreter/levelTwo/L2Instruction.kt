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

import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.typeAtIndex
import avail.descriptor.representation.AvailObject.Companion.combine3
import avail.descriptor.types.TypeTag
import avail.exceptions.unsupported
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operation.L2_ENTER_L2_CHUNK
import avail.interpreter.levelTwo.operation.L2_IMPOSSIBLE_CODE
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation
import avail.interpreter.levelTwo.operation.tuples.L2_TUPLE_AT_CONSTANT
import avail.interpreter.levelTwo.operation.variables.L2_CREATE_VARIABLE
import avail.interpreter.levelTwo.operation.variables.L2_GET_VARIABLE
import avail.interpreter.levelTwo.operation.variables.L2_SET_UNESCAPED_LOCAL_VARIABLE
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2Generator
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2Optimizer
import avail.optimizer.L2Optimizer.GenerationMode.WithFixedRegisterMap
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2SplitCondition.RestrictionTracer
import avail.optimizer.L2Synonym
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticValue
import avail.utility.PublicCloneable
import avail.utility.Strings.increaseIndentation
import avail.utility.cast
import avail.utility.mapToSet
import javax.annotation.CheckReturnValue
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
abstract class L2Instruction
@CheckReturnValue
constructor() :
	L2AbstractInstruction,
	PublicCloneable<L2Instruction>()
{
	/**
	 * An [InstructionLayout] object, set during construction, which captures
	 * the reflection information necessary for accessing the operands of the
	 * instruction in a generic way.  The layouts are placed in a cache as they
	 * are created, to minimize the reflection cost.
	 */
	val layout: InstructionLayout<*> =
		InstructionLayout.layoutForClass(this::class.cast())!!

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
	open val operands: List<L2Operand> get() = layout.operands(this)

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
			layout.updateOperands(this, L2Operand::clone)
		}

	/**
	 * Copy this instruction, but setting the [basicBlock] in the copy.
	 * Answer the copy.
	 *
	 * @param generator
	 *   The [L2GeneratorInterface] that this instruction's clone will soon be
	 *   written to.
	 * @param forceBlock
	 *   An optional [L2BasicBlock] indicating where this instruction will be
	 *   inserted.  If omitted or null, the [generator]'s
	 *   [current][L2GeneratorInterface.currentBlock] block will be used.
	 * @return
	 *   The cloned [L2Instruction].
	 */
	open fun cloneFor(
		generator: L2GeneratorInterface,
		forceBlock: L2BasicBlock? = null
	): L2Instruction = clone().apply {
		basicBlock = forceBlock ?: generator.currentBlock()
		operands.forEach { operand ->
			operand.adjustCloneForInstruction(this@apply, generator)
			operand.addSourceRegistersTo(sourceRegisters)
			operand.addDestinationRegistersTo(destinationRegisters)
		}
	}

	/**
	 * Replace the receiver in its [basicBlock] with the given [newInstruction].
	 *
	 * @param newInstruction
	 *   The [L2Instruction] to replace the receiver in its block.
	 */
	fun replaceWith(
		newInstruction: L2Instruction)
	{
		val block = basicBlock!!
		val index = block.instructions().indexOf(this)
		assert(index >= 0)
		justRemoved()
		newInstruction.basicBlock = block
		block.instructions()[index] = newInstruction
		newInstruction.justInserted()
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

	open val canBePostponed: Boolean
		get() = !hasSideEffect && layout.hasSingleWriteOperand

	/**
	 * Answer whether, upon discovering all edges out of this instruction but
	 * one have become impossible, the instruction may be replaced with a simple
	 * [L2_JUMP] along  the remaining edge.
	 */
	open fun canReduceToJumpIfOnePathRemains(): Boolean = false

	/**
	 * Check whether this instruction could cause any previously escaped
	 * variables to become shared or to have a reactor installed.  Assume most
	 * instructions can't do this, and override for instructions that can, like
	 * invocations of general functions, or of primitives that say they can.
	 *
	 * @param manifest
	 *   The [L2ValueManifest] in which to trace postponed instructions.
	 */
	open fun mightMakeEscapedVariableShared(
		manifest: L2ValueManifest
	): Boolean = false

	/**
	 * Answer whether this instruction produces any JVM code.  Examples of
	 * instructions that produce no JVM code include unconditional jumps that
	 * fall through to the next instruction, and moves between registers that
	 * have the same color.
	 */
	open val producesAnyJvmCode get() = true

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
	 * @param
	 *   The [L2ValueManifest] in which to trace postponed instructions.
	 * @return
	 *   The constant [A_RawFunction] extracted from the instruction, or `null`
	 *   if unknown.
	 */
	open fun getConstantCode(manifest: L2ValueManifest): A_RawFunction? = null

	/**
	 * Produce code to extract the specified [index] of the tuple constructed by
	 * this instruction, writing it to the [destinationSemanticValues].
	 *
	 * @receiver
	 *   The [L2GeneratorInterface] on which to write code to extract the tuple
	 *   element, if necessary.
	 * @param synonym
	 *   The [L2Synonym] that either will be or has been (perhaps partially)
	 *   populated by this instruction.
	 * @param index
	 *   The one-based index of the tuple element to extract.
	 * @param destinationSemanticValues
	 *   The [L2SemanticBoxedValue]s that will containing the element.
	 */
	open fun L2GeneratorInterface.extractTupleElement(
		synonym: L2Synonym<BOXED_KIND>,
		index: Int,
		destinationSemanticValues: Set<L2SemanticBoxedValue>
	): Unit =
		+L2_TUPLE_AT_CONSTANT(
			readBoxed(synonym.pickSemanticValue()),
			L2IntImmediateOperand(index),
			boxedWrite(
				destinationSemanticValues,
				boxedRestrictionForType(
					currentManifest.restrictionFor(synonym.pickSemanticValue())
						.type
						.typeAtIndex(index))))

	/**
	 * Emit code to extract the specified outer value from the function produced
	 * by this instruction.  The new code is appended to the provided list of
	 * instructions, which may be at a code generation position unrelated to the
	 * receiver.  The extracted outer variable will be written to the provided
	 * target register.
	 *
	 * @receiver
	 *   The [L2Generator] into which to write the new code.
	 * @param functionRegister
	 *   The register holding the function at the code generation point.
	 * @param outerIndex
	 *   The one-based outer index to extract from the function.
	 * @param outerType
	 *   The type of value that must be in that outer.
	 * @return
	 *   The [L2ReadBoxedOperand] holding the outer value.
	 */
	open fun L2GeneratorInterface.extractFunctionOuter(
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
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
	 * @receiver
	 *   An [L2Regenerator] that has been configured for writing
	 *   arbitrary replacement code for this instruction, which has already had
	 *   its operands transformed for the new graph.
	 */
	open fun L2Regenerator.generateReplacement(
		originalInstruction: L2Instruction
	) = cloneFor(this).run {
		emitTransformedInstruction()
	}

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
		!canBePostponed && !another.canBePostponed -> false
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
	 * This instruction is about to be passed to cloneFor(), and the result
	 * added at the current position in the [generator].  Force any postponed
	 * instructions to be written if they produce values consumed by this
	 * instruction's reads.
	 *
	 * @return
	 *   Whether the instruction should actually be emitted.
	 */
	open fun aboutToAdd(generator: L2GeneratorInterface): Boolean
	{
		if (!generator.currentManifest.caresAboutSemanticValues)
		{
			return true
		}

		// We're going to emit the instructioon, so ensure that all values it
		// consumes have been made available.
		readOperands.forEach { generator.populateForRead(it) }

		writeOperands.singleOrNull()?.let { write ->
			// Intercept instruction emission to see if we can replace it with a
			// move from a populated equivalent semantic value.
			val manifest = generator.currentManifest
			val targets = write.semanticValues()
			val (sources, allTargets) = targets
				.mapNotNull {
					manifest.equivalentPopulatedSemanticValue(it)
				}
				.plus(targets)
				.partition(manifest::hasLiveSemanticValue)
			if (allTargets.isEmpty())
			{
				// All targets are already populated.  Emit nothing.
				return false
			}
			if (sources.isNotEmpty()
				&& (this !is L2_MOVE<*> || allTargets.size > targets.size))
			{
				// We have a defined source and at least one notDefined target.
				// Emit a move.
				val source = sources.first()
				val restriction = manifest.restrictionFor(source)
					.intersection(write.restriction())
				val move = source.kind.dynamicMove(
					source,
					allTargets.toSet(),
					manifest,
					restriction)
				generator.addInstruction(move)
				return false
			}
		}
		return true
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
		// The instruction may have restrictions set on its reads and writes
		// that are stronger than what's in the manifest.  Force the manifest to
		// be as accurate as possible.
		val manifestByPurpose = mutableMapOf<Purpose?, L2ValueManifest>()
		if (altersControlFlow)
		{
			edgesAndPurposesDo { edge, purpose ->
				manifestByPurpose[purpose] = edge.manifest()
			}
			writesAndPurposesDo { write, purpose ->
				purpose?.let {
					manifestByPurpose[purpose]!!
						.updateRestriction(write.pickSemanticValue()) {
							write.restriction()
						}
				}
			}
		}
		// Phi instructions shouldn't attempt to strengthen restrictions in the
		// manifest for reads, since those reads are actually in the context of
		// the corresponding incoming edge's manifest.
		if (this !is L2_PHI<*>)
		{
			readOperands.forEach { read ->
				manifest.updateRestriction(read.semanticValue()) {
					read.restriction()
				}
			}
		}
		writeOperands.forEach { write ->
			manifest.updateRestriction(write.pickSemanticValue()) {
				write.restriction()
			}
		}
		// All manifests have now been updated, including propagation for
		// related semantic values.  Narrow the restrictions for my reads and
		// writes.  Phi instructions are excluded: their read operands each
		// belong to a specific incoming edge's manifest (not the merged
		// currentManifest), and their write restriction was already set
		// correctly by populateOneSynonym as the union of incoming restrictions.
		// Using the (stale, pre-merge) currentManifest here would incorrectly
		// intersect those restrictions down to bottom.
		if (this !is L2_PHI<*>)
		{
			val allManifests = manifestByPurpose.values + manifest
			readOperands.forEach { read ->
				// The outbound edges may vary in how they've deduced a stronger
				// restriction for the semantic value being read here, so only
				// strengthen the read up to the *union* of what the manifests
				// have recorded.
				val union = allManifests
					.map { it.restrictionFor(read) }
					.reduce(TypeRestriction::union)
				read.restrict { union }
			}
			writesAndPurposesDo { write, purpose ->
				if (purpose == null)
				{
					val intersection = allManifests
						.map { it.restrictionFor(write.pickSemanticValue()) }
						.reduce(TypeRestriction::intersection)
					write.restrict { intersection }
				}
				else
				{
					// The write is associated with only one edge, so use the
					// manifest that was just updated on that edge.
					write.restrict {
						manifestByPurpose[purpose]!!
							.restrictionFor(write.pickSemanticValue())
					}
				}
			}
		}
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
		assert(basicBlock != null)
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
	override fun toString() = toString(false, false)

	/**
	 * Print the instruction, using the layout's operandFields for the operand
	 * names and other information shared by instances of the same instruction
	 * subclass.
	 *
	 * @param ignoreMisconnections
	 *   If true, don't bring attention to the lack of backlink to the
	 *   instruction, as that's normal for a postponed instruction.
	 * @param omitEmptyWrite
	 *   If true, *omit* writes to an empty set of semantic values, which is
	 *   normal for a postponed instruction.
	 */
	fun toString(
		ignoreMisconnections: Boolean,
		omitEmptyWrite: Boolean
	) = buildString {
		val instruction = this@L2Instruction
		var pairs = mutableListOf<Pair<String, L2Operand>>()
		operandsWithNamedTypesDo { operand, namedOperandType ->
			if (!namedOperandType.hideInAllVisualizations
				&& (operand !is L2WriteOperand<*>
					|| !omitEmptyWrite
					|| operand.semanticValues().isNotEmpty()))
			{
				pairs.add(namedOperandType.name to operand)
			}
		}
		if (pairs.size == 1)
		{
			val (name, operand) = pairs.single()
			val operandString =
				increaseIndentation(operand.toString(ignoreMisconnections), 2)
			append("${instruction.name}: $name = $operandString")
		}
		else
		{
			append("${instruction.name}:\n\t")
			pairs.joinTo(this, ",\n\t") { (name, operand) ->
				val operandString =
					increaseIndentation(operand.toString(ignoreMisconnections), 2)
				"$name = $operandString"
			}
		}
	}

	/**
	 * Output this instruction to the given builder, invoking the given lambda
	 * with a boolean to turn warning style on or off, if tracked by the caller.
	 *
	 * @receiver
	 *   Where to write the description of this instruction.
	 * @param desiredOperandTypes
	 *   Which [L2OperandType]s to include.
	 * @param ignoreMisconnections
	 *   If true, suppress presenting warnings for misconnected operands.
	 * @param warningStyleChange
	 *   A lambda that takes `true` to start the warning style at the
	 *   current builder position, and `false` to end it.  It must be invoked in
	 *   (true, false) pairs.
	 */
	open fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		operandsWithNamedTypesDo { operand, namedOperandType ->
			if (!namedOperandType.hideInAllVisualizations)
			{
				if (namedOperandType.operandType() in desiredOperandTypes)
				{
					append("\n\t")
					append(namedOperandType.name())
					append(" = ")
					operand.run {
						appendWithWarningsTo(
							1, ignoreMisconnections, warningStyleChange)
					}
				}
			}
		}
	}

	/**
	 * Produce a sensible preamble for the textual rendition of the specified
	 * [L2Instruction] that includes the [offset][L2Instruction.offset] and
	 * [name][toString] of the instruction.
	 *
	 * @receiver
	 *   The [StringBuilder] to which the preamble should be written.
	 */
	fun StringBuilder.renderPreamble()
	{
		if (offset != -1)
		{
			append(offset)
			append(". ")
		}
		append(name)
	}

	/**
	 * If this instruction includes a move from a source register to this
	 * [destinationRegister], return that source.  Otherwise return null.
	 * This should only be called if this instruction contains a write to that
	 * register.
	 */
	open fun sourceOfMoveToRegister(
		destinationRegister: L2Register<*>
	): L2Register<*>? = null

	/**
	 * Create an equivalent of this instruction, transforming each [L2Operand]
	 * through the given [L2Regenerator].  Don't do deeper processing than just
	 * transforming each operand in the copy.
	 *
	 * @param regenerator
	 *   The [L2Regenerator] by which to transform the given instruction.
	 * @return
	 *   A new instruction like the given one.
	 */
	open fun transformedByRegenerator(
		regenerator: L2Regenerator
	): L2Instruction
	{
		val manifest = regenerator.currentManifest
		if (readOperands.any { manifest.restrictionFor(it).isImpossible }
			|| writeOperands.any { manifest.restrictionFor(it).isImpossible })
		{
			return regenerator.impossibleCodeInstruction()
		}
		if (regenerator.mode is WithFixedRegisterMap
			|| !altersControlFlow
			|| this is L2_SAVE_ALL_AND_PC_TO_INT)
		{
			return clone().also { clone ->
				layout.updateOperands(clone, regenerator::transformOperand)
			}
		}
		// See if all but one path was deemed impossible in a previous pass.
		// Note that this isn't appropriaate for L2_JUMP_IF_UNBOX_INT, since the
		// in-range path still has to do the unboxing anyhow, so it overrides
		// this method.
		val (impossible, possible) =
			targetEdges.partition { originalEdge ->
				originalEdge.targetBlock().instructions().last() is
					L2_IMPOSSIBLE_CODE
			}
		if (impossible.isNotEmpty())
		{
			when (possible.size)
			{
				// All paths are impossible, and none are possible. Rewrite as
				// an impossible instruction.
				0 -> return regenerator.impossibleCodeInstruction()
				// At least one edge is impossible, but there's only one that's
				// still possible, so rewrite it as a jump.
				1 if canReduceToJumpIfOnePathRemains() ->
				{
					return L2_JUMP(possible.single())
						.transformedByRegenerator(regenerator)
				}
			}
		}
		readOperands.forEach { read ->
			if (manifest.restrictionFor(read).isImpossible)
			{
				// Even though we're attempting to generate this instruction, a
				// condition exists in the ancestry that makes the instruction
				// unreachable, even though branches leading to the current
				// block didn't detect it.  Just write an L2_IMPOSSIBLE_CODE
				// instruction instead, and hope it really isn't hiding a bug,
				// since it shouldn't be executed at runtime.  This instruction
				// will propagate backward in a later pass, eventually
				// eliminating the arm of the branch instruction that led to it.
				return regenerator.impossibleCodeInstruction()
			}
		}
		return clone().also { clone ->
			layout.updateOperands(clone, regenerator::transformOperand)
		}
	}

	/**
	 * Create an equivalent of this instruction, transforming each
	 * [L2ReadOperand] through the given [transformer].  Don't do deeper
	 * processing than just transforming the operands in the copy.
	 *
	 * @param transformer
	 *   A function mapping each [L2ReadOperand] to itself or a new one of the
	 *   same type.
	 * @return
	 *   A new instruction like the given one.
	 */
	open fun transformEachRead(
		transformer: (L2ReadOperand<*>)->L2ReadOperand<*>
	): L2Instruction
	{
		val clone = clone()
		layout.updateOperands(clone) { it.transformEachRead(transformer) }
		return clone
	}

	/**
	 * Create an equivalent of this instruction, transforming each
	 * [L2WriteOperand] through the given [transformer].  Don't do deeper
	 * processing than just transforming the operands in the copy.
	 *
	 * @param transformer
	 *   A function mapping each [L2WriteOperand] to itself or a new one of the
	 *   same type.
	 * @return
	 *   A new instruction like the given one.
	 */
	open fun transformEachWrite(
		transformer: (L2WriteOperand<*>)->L2WriteOperand<*>
	): L2Instruction
	{
		val clone = clone()
		layout.updateOperands(clone) { it.transformEachWrite(transformer) }
		return clone
	}

	/**
	 * This instruction has no side-effect.  Ensure this instruction or an
	 * equivalent effect is added as a postponed instruction in the generator's
	 * current manifest.
	 */
	open fun postponeInstruction(generator: L2GeneratorInterface)
	{
		assert(canBePostponed)
		val originalWrite = writeOperands.single()
		assert(originalWrite.semanticValues().isNotEmpty())
		val manifest = generator.currentManifest
		// See if there's already an existing equivalent value.
		val existing = originalWrite.semanticValues()
			.firstNotNullOfOrNull { manifest.equivalentSemanticValue(it) }
		if (existing != null)
		{
			// Just augment the existing synonym.
			val newRestriction = manifest.restrictionFor(existing)
				.intersection(originalWrite.restriction())
			if (newRestriction.isImpossible)
			{
				// Emit an instruction that notes the impossibility of this
				// path.
				generator.addInstruction(generator.impossibleCodeInstruction())
			}
			manifest.dynamicAgglomerateSynonym(
				originalWrite.semanticValues() + existing,
				newRestriction)
			return
		}
		manifest.agglomerateSynonym(
			originalWrite.semanticValues(),
			originalWrite.restriction())
		manifest.recordPostponedInstruction(
			originalWrite.pickSemanticValue(),
			clone().apply {
				writeOperands[0].retroactivelySetSemanticValues(emptySet())
			})
	}

	/**
	 * After transformation, analyze and optionally rewrite this instruction.
	 * Read operands have already been strengthened via manifest intersection.
	 *
	 * The typical pattern:
	 * 1. Strengthen write restrictions based on read restrictions,
	 * 2. Check for constant folding opportunities,
	 * 3. Return replacement instruction or this.
	 *
	 * @return
	 *   The instruction to emit, or null if it already handled emission.
	 */
	open fun L2GeneratorInterface.analyzeAndOptionallyRewrite(): L2Instruction?
	{
		return this@L2Instruction
	}

	/**
	 * Given this instruction, which is already a transformation of the same
	 * kind of instruction from an earlier graph, write to the regenerator an
	 * equivalent instruction or series of replacement instructions.
	 *
	 * @receiver
	 *   The [L2GeneratorInterface] on which to emit the transformed
	 *   instruction.
	 */
	open fun L2GeneratorInterface.emitTransformedInstruction(): Unit
	{
		analyzeAndOptionallyRewrite()?.let(::addInstruction)
	}

	/**
	 * This instruction, adapted from a previous control flow graph, was
	 * encountered during postponement optimization.  Depending on the kind of
	 * instruction, either emit it to the regenerator, record it as a postponed
	 * instruction, or do something else like writing an arbitrary
	 * transformation.
	 *
	 * @receiver
	 *   The [L2Regenerator] on which to write the effect.
	 */
	open fun L2Regenerator.regenerateForPostponement()
	{
		analyzeAndOptionallyRewrite()?.run { basicRegenerateForPostponement() }
	}

	/**
	 * The default implementation of [regenerateForPostponement], since super is
	 * not possible for Kotlin methods taking an extra receiver.
	 */
	fun L2GeneratorInterface.basicRegenerateForPostponement()
	{
		if (!canBePostponed)
		{
			// Emit the translation right now.
			forcePostponedTranslationNow()
			return
		}
		if (targetEdges.size > 1 &&
			destinationRegisters.all { writeReg ->
				targetEdges.all { edge ->
					writeReg in edge.alwaysLiveInEntities!!
				}
			})
		{
			// We're going to branch soon, but the result will always be needed
			// along all the successor edges.  While we *could* postpone the
			// instruction, we choose not to, since the increase of register
			// pressure is minor compared to the cost of the duplicated code.
			forcePostponedTranslationNow()
			return
		}
		// Emit a constant move for each constant output, then postpone the
		// instruction if any outputs were non-constant.
		val write = writeOperands.single()
		val constant = write.restriction().constantOrNull
		if (constant == null)
			+this@L2Instruction
		else
			write.moveConstantForWrite(constant, this)
	}

	/**
	 * If the source instruction has no side effects and isn't a phi, check if
	 * its sole write has a semantic value equivalent to a value already in the
	 * manifest.  If so, emit a move into the not-yet-populated semantic values,
	 * and answer `true`.  Otherwise answer `false`.
	 *
	 * @receiver
	 *   The [L2GeneratorInterface] on which emission should occur if possible.
	 * @return
	 *   Whether the writes that this original instruction (from a previous
	 *   graph) were replaced by moves from equivalent registers.
	 */
	open fun L2GeneratorInterface.populateFromSourceInstructionIfPossible(
	): Boolean
	{
		// If it has side effect, do the default processing.
		if (!canBePostponed) return false
		return populateForMoveTo(writeOperands.single())
	}

	private fun <K: RegisterKind<K>> L2GeneratorInterface.populateForMoveTo(
		write: L2WriteOperand<K>
	): Boolean
	{
		// See if there's an equivalent value alredy computed, and if so, just
		// move it to that write's destinations, eliding the sourceInstruction.
		val semanticValues = write.semanticValues()
		val unpopulated = semanticValues
			.filterNotTo(mutableSetOf(), currentManifest::isPopulated)
		val constantSource = semanticValues.find(L2SemanticValue<*>::isConstant)
		val constant =
			constantSource?.constant ?: write.restriction().constantOrNull
		if (constant != null)
		{
			// The value is known, so do a constant move if any destinations are
			// not yet populated.
			if (unpopulated.isNotEmpty())
			{
				write.moveConstantForWrite(constant, this)
			}
			return true
		}
		val possibleSources = semanticValues.mapNotNull {
			currentManifest.equivalentPopulatedSemanticValue(it)
		}
		if (possibleSources.isEmpty()) return false
		if (unpopulated.isEmpty())
		{
			// All destination semantic values are already populated, so
			// there's no need to do anything else.  However, since we
			// know they're supposed to be equivalent to each other, we
			// merge their synonyms.  Note that since there won't be an
			// instruction to repeat this in subsequent passes, we'll
			// lose out on the values being synonymous, but at least we
			// can cause them now to all to have the intersection of the
			// restrictions.
			val synonymRepresentatives = possibleSources.mapToSet {
				currentManifest.semanticValueToSynonym(it)
					.pickSemanticValue()
			}.toList()
			for (i in 1 ..< synonymRepresentatives.size)
			{
				currentManifest.dynamicMergeExistingSemanticValues(
					synonymRepresentatives[0],
					synonymRepresentatives[i])
			}
			return true
		}
		// Not all destinations have been filled, so populate them with
		// a move.
		when (constantSource)
		{
			null ->
				+write.kind.dynamicMove(
					possibleSources.first(),
					unpopulated,
					currentManifest,
					write.restriction())

			else -> +constantSource.kind.moveConstant(
				constantSource.constant!!,
				currentManifest.getDefinitionOrNull(constantSource)
					?.let { unpopulated }
					?: (unpopulated + constantSource))
		}
		return true
	}

	/**
	 * Find the [currentEdge] within this [L2Instruction] and replace it with
	 * [newEdge].  Also set the operand's reference to the instruction.  Don't
	 * do any other adjustments.
	 */
	fun replaceEdgeWith(
		currentEdge: L2PcOperand,
		newEdge: L2PcOperand)
	{
		var found = 0
		layout.updateOperands(this) { operand ->
			when (operand)
			{
				currentEdge ->
				{
					found++
					newEdge.setInstruction(this)
					newEdge
				}
				is L2PcVectorOperand ->
				{
					var replacedCount = 0
					val newVector = operand.edges.map { edge ->
						if (edge == currentEdge)
						{
							++replacedCount
							newEdge.setInstruction(this)
							newEdge
						}
						else edge
					}
					if (replacedCount == 0) operand
					else
					{
						found += replacedCount
						val replacement = L2PcVectorOperand(newVector)
						replacement.setInstruction(this)
						replacement
					}
				}
				else -> operand
			}
		}
		assert(found == 1)
	}

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
	 * @param registerToValueMap
	 *   A mutable map from the old graph's [L2Register] to the new graph's
	 *   [L2SemanticValue].
	 */
	open fun replaceConstantReads(
		generator: L2GeneratorInterface,
		registerToValueMap: MutableMap<L2Register<*>, L2SemanticValue<*>>)
	{
		// Note: We have to run this against all readOperands, so don't replace
		// the count{} with any{}, which short-circuits.
		if (readOperands.count(L2ReadOperand<*>::replaceIfConstantRead) > 0)
		{
			// Rebuild the sourceRegisters list if anything changed.
			sourceRegisters.clear()
			operands.forEach { operand ->
				operand.addSourceRegistersTo(sourceRegisters)
			}
		}
	}

	/**
	 * Rewrite this postponed instruction in the manifest, replacing it and
	 * sometimes others in the process.  For example, if an [L2_CREATE_VARIABLE]
	 * produces a variable that's used in an [L2_SET_UNESCAPED_LOCAL_VARIABLE],
	 * both can be replaced by an [L2_CREATE_VARIABLE] that has the more
	 * up-to-date value as its initialization value.
	 *
	 * Since postponed instructions have an empty list for their write operand's
	 * semantic values, we pass the synonym under which the instruction has been
	 * postponed.
	 *
	 * @receiver
	 *   The [L2ValueManifest] containing this postponed instruction.
	 * @param
	 *   The [L2Synonym] under which the instruction is to be postponed.
	 * @return
	 *   `true` if a replacement was made, otherwise `false`.
	 */
	open fun L2ValueManifest.rewritePostponed(
		synonym: L2Synonym<*>
	): Boolean = false

	/**
	 * We're performing [L2Optimizer.postponeConditionallyUsedValues], and we
	 * have decided that this instruction (cloned from the original graph) needs
	 * to be cloned and emitted in the new graph.  If the instruction uses
	 * values that are not yet available in registers due to postponement, first
	 * translate the instructions that produce those values.
	 *
	 * This instruction must not currently be in the current
	 * `postponedInstructions` map.
	 *
	 * Subclasses may choose to look up any still-postponed predecessor
	 * instructions, and perform instruction-specific special transformations as
	 * they slip past this instruction.  Variable elision uses this technique,
	 * allowing an [L2_CREATE_VARIABLE] to slip past an [L2_GET_VARIABLE], by
	 * emitting a move (of the initialization value of the create-variable) in
	 * place of the get.
	 *
	 * @receiver
	 *   The [L2GeneratorInterface] on which to generate.
	 */
	open fun L2GeneratorInterface.forcePostponedTranslationNow()
	{
		basicForcePostponedTranslationNow()
	}

	/**
	 * The default implementation of [forcePostponedTranslationNow], since super
	 * is not supported in Kotlin for methods with an extra receiver.
	 */
	fun L2GeneratorInterface.basicForcePostponedTranslationNow()
	{
		if (canBePostponed)
		{
			// If we already have a live value for each of the *writes* of this
			// instruction, we can elide the instruction and write extending
			// moves instead.
			val liveWriteRepresentatives = writeOperands.map { write ->
				write to
					write.semanticValues()
						.filter(currentManifest::hasLiveSemanticValue)
			}
			if (liveWriteRepresentatives.all { (_, reps) -> reps.isNotEmpty() })
			{
				// We have an assigned semantic value from each of the write
				// operands.  Generate extending moves as needed, and omit the
				// redundant postponed instruction.
				liveWriteRepresentatives.forEach { (write, reps) ->
					val notYetAssigned = write.semanticValues() - reps
					moveRegister(reps.first(), notYetAssigned.cast())
				}
				return
			}
		}
		// At least one write didn't have a semantic value already populated (or
		// the instruction has a side-effect), so we have to emit the postponed
		// instruction.  Emit any necessary predecessors first.
		readOperands.forEach { read ->
			currentManifest.check() //TODO Remove
			forceTranslationForRead(read.semanticValue())
			currentManifest.check() //TODO Remove
		}
		cloneFor(this).run {
			emitTransformedInstruction()
		}
	}

	/**
	 * We're doing mutability analysis to determine where to insert
	 * [L2_MAKE_IMMUTABLE] instructions in an [L2ControlFlowGraph].  This call
	 * happens for instructions in program order (neglecting loops). The caller
	 * is tracking when a register's value is used for the first time, and if
	 * it's used later in the same block or is live along an outbound edge.
	 * Essentially, make the value immutable at its first use if it will be used
	 * more than once in a way that could destroy it.
	 *
	 * The control flow graph containing this instruction is assumed no longer
	 * to be in SSA form, and only the register information is considered, not
	 * the semantic values.
	 *
	 * @param firstUses
	 *   The map from [L2BoxedRegister] to its block's index of the instruction
	 *   that first uses it, and the [L2ReadBoxedOperand] that does the read.
	 * @param mutables
	 *   The set of registers that might still be mutable when this instruction
	 *   is reached in the analysis.
	 */
	open fun propagateMutability(
		firstUses: MutableMap<L2BoxedRegister, Pair<Int, L2ReadBoxedOperand>>,
		mutables: MutableSet<L2BoxedRegister>)
	{
		destinationRegisters
			.filterIsInstance<L2BoxedRegister>()
			.forEach { writeReg ->
				firstUses.remove(writeReg)
				mutables.remove(writeReg)
				mutables.add(writeReg)
			}
	}

	/**
	 * Translate the [L2Instruction] into corresponding JVM instructions.
	 *
	 * @receiver
	 *   The [JVMTranslator] responsible for the translation.
	 */
	abstract fun JVMTranslator.translateToJVM()

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
	 * this instruction will be "split" into a duplicate subgraph, allowing that
	 * condition to be preserved.  This eliminates extra type tests, unboxing to
	 * int registers, recomputing stable primitives, etc.
	 *
	 * @return A [List] of [L2SplitCondition] to watch for upstream.
	 */
	open fun interestingConditions(): List<L2SplitCondition?> = emptyList()

	/**
	 * Given a [writeOperand] from this instruction, and a [restriction] that is
	 * an interesting condition for it to satisfy, update the [tracer] to ensure
	 * any ancestor values will be traced, but perhaps with a transformed
	 * restriction.  The transformation can be boxing/unboxing, mapping from a
	 * [TypeTag]'s numerical restriction to a restriction on the underlying
	 * value's suprema, etc.
	 */
	open fun traceCandidateSplitConditions(
		writeOperand: L2WriteOperand<*>,
		restriction: TypeRestriction,
		tracer: RestrictionTracer)
	{
		// Do nothing by default.
	}

	/**
	 * Output this [L2Instruction] compactly to the builder.
	 *
	 * @param builder
	 *   The [StringBuilder] on which to write this instruction compactly.
	 */
	open fun simpleAppendTo(builder: StringBuilder)
	{
		builder.renderPreamble()
		val targets = mutableListOf<String>()
		val sources = mutableListOf<String>()
		val commands = mutableListOf<String>()
		operandsWithNamedTypesDo { operand, namedOperandType ->
			if (!namedOperandType.hideInSimpleVisualization
				&& !namedOperandType.hideInAllVisualizations)
			{
				operand.simpleAppendOperand(commands, sources, targets)
			}
		}
		val hasTargets = targets.isNotEmpty()
		val hasSources = sources.isNotEmpty() || commands.isNotEmpty()
		when
		{
			!hasTargets && !hasSources -> { }
			hasTargets && !hasSources ->
			{
				builder.append(": → ")
				targets.joinTo(builder)
			}
			hasTargets && hasSources ->
			{
				builder.append(": ")
				targets.joinTo(builder)
				builder.append(" ⇦ ")
				commands.joinTo(builder, "/")
				sources.joinTo(builder, ", ")
			}
			!hasTargets && hasSources ->
			{
				builder.append(": ")
				commands.joinTo(builder, "/")
				sources.joinTo(builder, ", ")
			}
		}
	}

	/**
	 * Compare this [L2Instruction] to an [other] one, to detirmine whether the
	 * two postponed instructions can be combined into one at a control flow
	 * merge point.
	 */
	open fun equivalentTo(other: L2Instruction): Boolean
	{
		if (javaClass != other.javaClass) return false
		return operands.zip(other.operands).all { (a, b) -> a.equivalentTo(b) }
	}

	/**
	 * Compute a hash value that's stable for this instruction, such that two
	 * instructions
	 */
	open val equivalentHash: Int
		get() = combine3(
			javaClass.hashCode(),
			operands.sumOf(L2Operand::equivalentHash),
			0x59547A47)

	/**
	 * Generically render all [operands][L2Operand] of this [L2Instruction],
	 * except for those linked to the given [Field]s.
	 *
	 * @receiver
	 *   The [StringBuilder] to which the rendition should be written.
	 * @param excludedFields
	 *   The vararg array of [fields][KProperty] whose corresponding
	 *   [L2Operand]s should be excluded.
	 * @param desiredOperandTypes
	 *   The [L2OperandType]s of [L2Operand]s to be included in generic
	 *   renditions. Customized renditions may not honor these types.
	 */
	fun StringBuilder.renderOperandsExcludingFields(
		desiredOperandTypes: Set<L2OperandType>,
		vararg excludedFields: KMutableProperty0<out L2Operand>)
	{
		val excludedNames = excludedFields.mapTo(mutableSetOf()) { it.name }
		operandsWithNamedTypesDo { operand, namedOperandType ->
			if (namedOperandType.operandType in desiredOperandTypes
				&& namedOperandType.name !in excludedNames)
			{
				append("\n\t")
				append(namedOperandType.name())
				append(" = ")
				append(increaseIndentation(operand.toString(), 2))
			}
		}
	}

	/**
	 * Produce a map from my edges to suitable short names for them for labeling
	 * a visualized graph.
	 */
	open fun suggestVisualPortNames(): Map<L2PcOperand, String> = buildMap {
		operandsWithNamedTypesDo { operand, namedOperandType ->
			when (operand)
			{
				is L2PcOperand -> put(operand, namedOperandType.name)
				is L2PcVectorOperand ->
				{
					operand.edges.forEachIndexed { i, edge ->
						put(edge, "${namedOperandType.name} #${i+1}")
					}
				}
			}
		}
	}

	/**
	 * Given a [List] of [L2Instruction]s that represent the same effective
	 * instruction, produce a new instruction that has a suitable broadening of
	 * [TypeRestriction]s for [L2ReadOperand]s and [L2WriteOperand]s, and a
	 * narrowing of [L2SemanticValue]s in the [L2WriteOperand].  The first
	 * element of the list is the receiver.
	 *
	 * @param instructionsToMerge
	 *   The [List] of instructions to combine into one.  The first item of the
	 *   list is the receiver.
	 */
	open fun mergeInstructions(
		instructionsToMerge: List<L2Instruction>
	): L2Instruction
	{
		assert(this == instructionsToMerge[0])
		val operandsByInstruction = instructionsToMerge.map { instruction ->
			assert(instruction.javaClass == javaClass)
			instruction.operands
		}
		val newInstruction = clone()
		var i = 0
		newInstruction.layout.updateOperands(newInstruction) { operand ->
			val originalOperands = operandsByInstruction.map { it[i] }
			assert(originalOperands.mapToSet { it.javaClass }.size == 1)
			i++
			val clone = originalOperands[0].clone()
			clone.mergeFromOperands(originalOperands)
			clone
		}
		return newInstruction
	}

	/**
	 * Update the given data structures to accomodate this instruction's effect.
	 *
	 * @param firstUses
	 *   The positions within this instruction's block where a register is used
	 *   for the first time.
	 * @param insertions
	 *   The positions at which to insert an [L2_MAKE_IMMUTABLE] for a register.
	 * @param mutables
	 *   The registers that might still be mutable at this instruction.
	 * @param uniqueGenerator
	 *   A nullary function to produce an [Int] unique to the current
	 *   [L2Generator].
	 */
	open fun processForMakeImmutable(
		firstUses: MutableMap<L2BoxedRegister, Pair<Int, L2ReadBoxedOperand>>,
		insertions: MutableList<Pair<Int, L2ReadBoxedOperand>>,
		mutables: MutableSet<L2BoxedRegister>,
		uniqueGenerator: ()->Int)
	{
		val instruction = this
		instruction.readsThatMightDestroy.forEach { read ->
			if (read.restriction().isImmutable) return@forEach
			val readReg = read.register()
			val pair = firstUses[readReg]
			when
			{
				pair !== null ->
				{
					// We just hit the second use within the block.
					insertions.add(pair)
					// It's no longer mutable.
					mutables.remove(readReg)
					firstUses.remove(readReg)
				}

				readReg in mutables ->
				{
					// Record this first use of a mutable.
					firstUses[readReg] =
						basicBlock!!.instructions().indexOf(this) to read
				}
			}
		}
		// Deal with the register writes.
		instruction.propagateMutability(firstUses, mutables)
	}

	/**
	 * This class is a wrapper to facilitate matching postponed [L2Instruction]s
	 * from different incoming edges at a contral flow merge. Its equality and
	 * hash semantics defer to the wrapped instruction's [equivalentTo] and
	 * [equivalentHash].
	 *
	 * An [L2Synonym] is also captured.  This is the synonym that would be
	 * populated by the postponed instruction.
	 */
	class InstructionEquivalence(
		val instruction: L2Instruction,
		val synonym: L2Synonym<*>)
	{
		/** Cache the instruction's [equivalentHash] upon creation. */
		private val cachedHash =
			instruction.equivalentHash xor synonym.hashCode()

		override fun equals(other: Any?): Boolean =
			other is InstructionEquivalence
				&& cachedHash == other.cachedHash
				&& instruction.equivalentTo(other.instruction)
				&& synonym == other.synonym

		override fun hashCode(): Int = cachedHash

		override fun toString(): String = "EQ($instruction for $synonym)"
	}
}
