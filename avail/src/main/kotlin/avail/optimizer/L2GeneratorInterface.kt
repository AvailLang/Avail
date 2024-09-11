/*
 * L2GeneratorInterface.kt
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

package avail.optimizer

import avail.descriptor.functions.A_Function
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.types.A_Type
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.L2ConditionalJump
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_UNBOX_INT
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_TUPLE_AT_CONSTANT
import avail.interpreter.levelTwo.operation.NumericComparator
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.optimizer.L2Optimizer.GenerationMode
import avail.optimizer.L2Optimizer.GenerationMode.ByRegister
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.Frame
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.structures.EnumMap

/**
 * The interface for objects that can act as the target of L2 code generation.
 */
interface L2GeneratorInterface
{
	/**
	 * By default we automatically generate phi instructions.  In later
	 * optimization passes, the [L2ControlFlowGraph] is held together by
	 * [L2Register]s instead of [L2SemanticValue]s, so at some phase we switch
	 * this from [BySemanticValue] to [ByRegister] in the [L2Regenerator]
	 * subclass.
	 */
	val mode: GenerationMode

	/** The topmost [Frame] for translation. */
	val topFrame: Frame

	/**
	 * An enumeration of symbolic names of key blocks of the
	 * [L2ControlFlowGraph]. These are associated with optional [L2BasicBlock]s
	 * within the generator's [specialBlocks].
	 */
	enum class SpecialBlock
	{
		/**
		 * The initial block of the control flow graph, which is where the
		 * control flow implicitly starts when the [A_Function] underlying the
		 * [L2Chunk] is ultimately invoked.
		 */
		START,

		/** The block at which to resume execution after a failed primitive. */
		AFTER_OPTIONAL_PRIMITIVE,

		/**
		 * The head of the loop formed when a [P_RestartContinuation] is invoked
		 * on a label created for the current frame.
		 */
		RESTART_LOOP_HEAD
	}

	/**
	 * An [EnumMap] from symbolic [SpecialBlock] to optional [L2BasicBlock].
	 */
	val specialBlocks: EnumMap<SpecialBlock, L2BasicBlock>

	/**
	 * Answer the next value from the unique counter.  This is only used to
	 * distinguish registers for visual debugging.
	 *
	 * @return
	 *   An [Int].
	 */
	fun nextUnique(): Int

	/**
	 * Use this [L2ValueManifest] to track which [L2Register] holds which
	 * [L2SemanticValue] at the current code generation point.
	 */
	val currentManifest: L2ValueManifest

	/**
	 * Create an [L2BasicBlock], and mark it as used for reification.
	 *
	 * @param name
	 *   The name of the new block.
	 * @param zone
	 *   The [L2ControlFlowGraph.Zone] (or `null`) into which to group this
	 *   block in the [L2ControlFlowGraphVisualizer].
	 * @param isCold
	 *   Whether the block should be considered part of a "cold" path, and not
	 *   worth performing code splitting to optimize paths that lead only to it
	 *   and other cold blocks.
	 * @return
	 *   The new block.
	 */
	fun createBasicBlock(
		name: String,
		zone: L2ControlFlowGraph.Zone? = null,
		isCold: Boolean = false
	): L2BasicBlock

	/**
	 * Answer the restriction for the given [L2SemanticValue] at the current
	 * code generation position.
	 */
	fun restrictionFor(semanticValue: L2SemanticValue<*>): TypeRestriction

	/**
	 * Add an [L2Instruction].
	 *
	 * @param instruction
	 *   The instruction to add.
	 */
	fun addInstruction(instruction: L2Instruction)

	/**
	 * Add an [L2ConditionalJump] instruction, but if all but one of the edges
	 * contains an impossible restriction, replace it imeediately with an
	 * unconditional jump along the remaining edge.
	 *
	 * Note that this is an overload of the version taking an arbitrary
	 * [L2Instruction], so use an up or down cast at a call site if you want to
	 * override the behavior.
	 */
	fun addInstruction(instruction: L2ConditionalJump)

	/** Add an instruction that's not supposed to be reachable at runtime. */
	fun addUnreachableCode()

	/**
	 * Generate instructions to arrange for the value in the given
	 * [L2ReadOperand] to end up in an [L2Register] associated in the
	 * [L2ValueManifest] with the new [L2SemanticValue].  After the move, the
	 * synonyms for the source and destination are effectively merged, which is
	 * justified by virtue of SSA (static-single-assignment) being in effect.
	 *
	 * @param <K>
	 *   The [RegisterKind] of [L2Register] to move.
	 * @param sourceSemanticValue
	 *   Which [L2SemanticValue] to read.
	 * @param targetSemanticValues
	 *   Which [L2SemanticValue]s will have the same value as the source
	 *   semantic value.
	 */
	fun <K : RegisterKind<K>> moveRegister(
		sourceSemanticValue: L2SemanticValue<K>,
		targetSemanticValues: Iterable<L2SemanticValue<K>>)

	/**
	 * Generate instructions to arrange for the boxed value in the given
	 * [L2ReadBoxedOperand] to end up in an [L2BoxedRegister] associated in the
	 * [L2ValueManifest] with the new [L2SemanticBoxedValue].  After the move,
	 * the synonyms for the source and destination are effectively merged, which
	 * is justified by virtue of SSA (static-single-assignment) being in effect.
	 *
	 * @param sourceSemanticValue
	 *   Which [L2SemanticBoxedValue] to read.
	 * @param targetSemanticValues
	 *   Which [L2SemanticBoxedValue]s will have the same value as the source
	 *   semantic value.
	 */
	fun moveBoxedRegister(
		sourceSemanticValue: L2SemanticBoxedValue,
		targetSemanticValues: Iterable<L2SemanticBoxedValue>)

	/**
	 * Generate instructions to arrange for the unboxed [Int] value in the given
	 * [L2ReadIntOperand] to end up in an [L2IntRegister] associated in the
	 * [L2ValueManifest] with the new [L2SemanticUnboxedInt].  After the move,
	 * the synonyms for the source and destination are effectively merged, which
	 * is justified by virtue of SSA (static-single-assignment) being in effect.
	 *
	 * @param sourceSemanticValue
	 *   Which [L2SemanticUnboxedInt] to read.
	 * @param targetSemanticValues
	 *   Which [L2SemanticUnboxedInt]s will have the same value as the source
	 *   semantic value.
	 */
	fun moveIntRegister(
		sourceSemanticValue: L2SemanticValue<INTEGER_KIND>,
		targetSemanticValues: Iterable<L2SemanticValue<INTEGER_KIND>>)

	/**
	 * Allocate a new [L2IntRegister].  Answer an [L2WriteIntOperand] that
	 * writes to it as the given [L2SemanticValue]s, restricted with the given
	 * [TypeRestriction].
	 *
	 * @param semanticValues
	 *   The [L2SemanticUnboxedInt]s to write.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new write.
	 * @return
	 *   The new unboxed int write operand.
	 */
	fun intWrite(
		semanticValues: Set<L2SemanticValue<INTEGER_KIND>>,
		restriction: TypeRestriction,
		forceRegister: L2Register<INTEGER_KIND>? = null
	): L2WriteIntOperand

	/**
	 * Allocate a new [L2IntRegister].  Answer an [L2WriteIntOperand] that
	 * writes to it as a new temporary [L2SemanticValue], restricting it with
	 * the given [TypeRestriction].
	 *
	 * @param restriction
	 *   The initial [TypeRestriction] for the new operand.
	 * @return
	 *   The new unboxed int write operand.
	 */
	fun intWriteTemp(restriction: TypeRestriction): L2WriteIntOperand

	/**
	 * Emit an instruction to jump to the specified [L2BasicBlock].
	 *
	 * @param targetBlock
	 *   The target [L2BasicBlock].
	 * @param optionalName
	 *   An optional name to display for the edge for presenting in graphs, if
	 *   the branching operation's name for that edge isn't sufficiently
	 *   informative.
	 */
	fun jumpTo(
		targetBlock: L2BasicBlock,
		optionalName: String? = null
	)

	/**
	 * Generate code to move the given constant into a boxed register, if it's
	 * not already known to be in a boxed register.  Answer an
	 * [L2ReadBoxedOperand] to retrieve this value.
	 *
	 * @param value
	 *   The constant value to write to a register.
	 * @return
	 *   The [L2ReadBoxedOperand] that retrieves the value.
	 */
	fun boxedConstant(value: A_BasicObject): L2ReadBoxedOperand

	/**
	 * Generate code to move the given [Int] constant into an [L2IntRegister],
	 * if it's not already known to be in such a register.  Answer an
	 * [L2ReadIntOperand] to retrieve this value.
	 *
	 * @param value
	 *   The constant [Int] to write to an [L2IntRegister].
	 * @return
	 *   The [L2ReadIntOperand] that retrieves the value.
	 */
	fun unboxedIntConstant(value: Int): L2ReadIntOperand

	/**
	 * Generate code to move the given [Double] constant into an
	 * [L2FloatRegister], if it's not already known to be in such a register.
	 * Answer an [L2ReadFloatOperand] to retrieve this value.
	 *
	 * @param value
	 *   The constant [Double] to write to an [L2FloatRegister].
	 * @return
	 *   The [L2ReadFloatOperand] that retrieves the value.
	 */
	fun unboxedFloatConstant(value: Double): L2ReadFloatOperand

	/**
	 * Answer an [L2ReadBoxedOperand] for the given [L2SemanticValue],
	 * generating code to transform it as necessary.
	 *
	 * @param semanticBoxed
	 *   The [L2SemanticValue] to read.
	 * @return
	 *   A suitable [L2ReadBoxedOperand] that captures the current
	 *   [TypeRestriction] for the semantic value.
	 */
	fun readBoxed(
		semanticBoxed: L2SemanticValue<BOXED_KIND>
	): L2ReadBoxedOperand

	/**
	 * Return an [L2ReadIntOperand] for the given [L2SemanticUnboxedInt]. The
	 * [TypeRestriction] must have been proven by the VM.  If the semantic value
	 * only has a boxed form, generate code to unbox it.
	 *
	 * In the case that unboxing may fail, a branch to the supplied onFailure
	 * [L2BasicBlock] will be generated. If the unboxing cannot fail (or if a
	 * corresponding [L2IntRegister] already exists), no branch will lead to
	 * onFailure, which can be determined by the client by testing
	 * [L2BasicBlock.currentlyReachable].
	 *
	 * In any case, the generation position after this call is along the
	 * success path.  This may itself be unreachable in the event that the
	 * unboxing will *always* fail.
	 *
	 * @param semanticUnboxed
	 *   The [L2SemanticUnboxedInt] to read as an unboxed int.
	 * @param onFailure
	 *   Where to jump in the event that an [L2_JUMP_IF_UNBOX_INT] fails. The
	 *   manifest at this location will not contain bindings for the unboxed
	 *   `int` (since unboxing was not possible).
	 * @return
	 *   The unboxed [L2ReadIntOperand].
	 */
	fun readInt(
		semanticUnboxed: L2SemanticUnboxedInt,
		onFailure: L2BasicBlock
	): L2ReadIntOperand

	/**
	 * Return an [L2ReadIntOperand] for the given [L2SemanticUnboxedInt]. The
	 * [TypeRestriction] must have been proven by the VM.  If the semantic value
	 * only has a boxed form, generate code to unbox it.
	 *
	 * If the unboxing could fail due to the [TypeRestriction] not guaranteeing
	 * an [i32], use the [readInt] generation method instead.
	 *
	 * @param semanticUnboxed
	 *   The [L2SemanticUnboxedInt] to read as an unboxed int.
	 * @return
	 *   The unboxed [L2ReadIntOperand].
	 */
	fun readIntNoFail(
		semanticUnboxed: L2SemanticValue<INTEGER_KIND>
	): L2ReadIntOperand

	/**
	 * Allocate a new [L2BoxedRegister].  Answer an [L2WriteBoxedOperand] that
	 * writes to it as the given [L2SemanticValue]s, restricting it with the
	 * given [TypeRestriction].
	 *
	 * @param semanticValues
	 *   The [L2SemanticValue]s to write.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new write.
	 * @return
	 *   The new boxed write operand.
	 */
	fun boxedWrite(
		semanticValues: Set<L2SemanticValue<BOXED_KIND>>,
		restriction: TypeRestriction
	): L2WriteBoxedOperand

	/**
	 * Given an [L2WriteBoxedOperand], produce an [L2ReadBoxedOperand] of the
	 * same value, but with the current manifest's [TypeRestriction] applied.
	 *
	 * @param write
	 *   The [L2WriteBoxedOperand] for which to generate a read.
	 * @return
	 *   The [L2ReadBoxedOperand] that reads the value.
	 */
	fun readBoxed(write: L2WriteOperand<BOXED_KIND>): L2ReadBoxedOperand =
		readBoxed(write.pickSemanticValue() as L2SemanticBoxedValue)

	/**
	 * Allocate a new [L2BoxedRegister].  Answer an [L2WriteBoxedOperand] that
	 * writes to it as a new temporary [L2SemanticValue], restricting it with
	 * the given [TypeRestriction].
	 *
	 * @param restriction
	 *   The initial [TypeRestriction] for the new operand.
	 * @return
	 *   The new boxed write operand.
	 */
	fun boxedWriteTemp(restriction: TypeRestriction): L2WriteBoxedOperand

	/**
	 * Attempt to read the given [L2SemanticValue], answering a suitable
	 * [L2ReadOperand] for with the same [RegisterKind].  If the requested
	 * [semanticValue] is not present in the [currentManifest], but an
	 * equivalent semantic value is present, [L2_MOVE] it into the specified
	 * [semanticValue].  If no equivalent semantic value is present, answer
	 * `null`.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to ensure is populated, if possible.
	 * @return
	 *   An [L2ReadOperand] that reads the request [semanticValue], or `null` if
	 *   it cannot be arronged to be present with only a move.
	 */
	fun <K: RegisterKind<K>> readIfAvailable(
		semanticValue: L2SemanticValue<K>
	): L2ReadOperand<K>?

	/**
	 * Answer the current [L2BasicBlock] being generated.
	 *
	 * @return
	 *   The current [L2BasicBlock].
	 */
	fun currentBlock(): L2BasicBlock

	/**
	 * Determine whether the current block is probably reachable.  If it has no
	 * predecessors and is removable, it's unreachable, but otherwise we assume
	 * it's reachable, at least until dead code elimination.
	 *
	 * @return
	 *   Whether the current block is probably reachable.
	 */
	fun currentlyReachable(): Boolean

	/**
	 * Compare the two ints with the [comparator], branching to one of the
	 * target edges.
	 *
	 * @param comparator
	 *   The numeric comparator to use for comparison.
	 * @param int1Reg
	 *   The first integer register operand.
	 * @param int2Reg
	 *   The second integer register operand.
	 * @param ifTrue
	 *   The target program counter operand if the comparison is true.
	 * @param ifFalse
	 *   The target program counter operand if the comparison is false.
	 */
	fun compareAndBranchInt(
		comparator: NumericComparator,
		int1Reg: L2ReadIntOperand,
		int2Reg: L2ReadIntOperand,
		ifTrue: L2PcOperand,
		ifFalse: L2PcOperand
	): Unit

	/**
	 * Compare the two boxed numeric values with the [comparator], branching to
	 * one of the target edges.
	 *
	 * @param comparator
	 *   The numeric comparator to use for comparison.
	 * @param number1Reg
	 *   The first boxed numeric value.
	 * @param number2Reg
	 *   The second boxed numeric value.
	 * @param ifTrue
	 *   The target program counter operand if the comparison is true.
	 * @param ifFalse
	 *   The target program counter operand if the comparison is false.
	 */
	fun compareAndBranchBoxed(
		comparator: NumericComparator,
		number1Reg: L2ReadBoxedOperand,
		number2Reg: L2ReadBoxedOperand,
		ifTrue: L2PcOperand,
		ifFalse: L2PcOperand
	): Unit

	/**
	 * Generate a conditional branch to either `passBlock` or `failBlock`, based
	 * on whether the given register equals the given constant value.
	 *
	 * If the constant to compare against is a boolean, check the provenance
	 * of the register.  If it's the result of a suitable comparison primitive,
	 * generate a more efficient compare-and-branch instruction instead of
	 * creating the boolean only to have it compared to a boolean constant.
	 *
	 * If the value of the boolean-producing instruction is not used, it will
	 * eventually be removed as dead code.
	 *
	 * @param registerToTest
	 *   The register whose content should be compared.
	 * @param constantValue
	 *   The constant value to compare against.
	 * @param passBlock
	 *   Where to go if the register's value equals the constant.
	 * @param failBlock
	 *   Where to go if the register's value does not equal the constant.
	 */
	fun jumpIfEqualsConstant(
		registerToTest: L2ReadBoxedOperand,
		constantValue: A_BasicObject,
		passBlock: L2BasicBlock,
		failBlock: L2BasicBlock
	)

	/**
	 * Generate code to test the value in `valueRead` against the constant
	 * `expectedType`, jumping to `passedCheck` if it conforms, or `failedCheck`
	 * otherwise.
	 *
	 * @param valueRead
	 *   The [L2ReadBoxedOperand] that provides the value to check.
	 * @param expectedType
	 *   The exact [A_Type] to check the value against.
	 * @param passedCheck
	 *   Where to jump if the value's type is of the expected type.
	 * @param failedCheck
	 *   Where to jump if the value's type is not of the expected type.
	 */
	fun jumpIfKindOfConstant(
		valueRead: L2ReadBoxedOperand,
		expectedType: A_Type,
		passedCheck: L2BasicBlock,
		failedCheck: L2BasicBlock)

	/**
	 * Given an [L2ReadBoxedOperand] that will hold a tuple and a fixed index
	 * that is known to be in range, generate code to populate the given
	 * [L2SemanticBoxedValue]s with that element.
	 *
	 * Depending on the source of the tuple, this may cause the creation of the
	 * tuple to be entirely elided.
	 *
	 * This is only effective if the control flow graph is still in SSA form, or
	 * if the register providing the tuple happened to have a single defining
	 * write, otherwise a simple [L2_TUPLE_AT_CONSTANT] instruction will be
	 * emitted.
	 *
	 * @param tupleRead
	 *   The [L2BoxedRegister] containing the tuple.
	 * @param index
	 *   The one-based subscript into the tuple.
	 * @param destinationSemanticValues
	 *   The [L2SemanticBoxedValue]s that will containing the element.
	 */
	fun extractTupleElement(
		tupleRead: L2ReadBoxedOperand,
		index: Int,
		destinationSemanticValues: Set<L2SemanticBoxedValue>)

	/**
	 * Pass-through to [L2ControlFlowGraph].  This can be used in the debugger
	 * to produce a String suitable for opening in Graphviz, even for an
	 * incomplete code generation.
	 */
	@Suppress("Unused")
	fun visualize(): String

	/**
	 * Pass-through to [L2ControlFlowGraph].  This can be used in the debugger
	 * to produce a String suitable for opening in Graphviz, even for an
	 * incomplete code generation.
	 */
	@Suppress("Unused")
	fun simplyVisualize(): String
}
