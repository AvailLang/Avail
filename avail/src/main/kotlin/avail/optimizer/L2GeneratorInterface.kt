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

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_ChunkDependable
import avail.descriptor.representation.A_Function
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.AvailObject
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
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operation.L2_GET_CURRENT_FUNCTION
import avail.interpreter.levelTwo.operation.L2_IMPOSSIBLE_CODE
import avail.interpreter.levelTwo.operation.L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.NumericComparator
import avail.interpreter.levelTwo.operation.tuples.L2_TUPLE_AT_CONSTANT
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.interpreter.primitive.controlflow.P_RestartContinuation
import avail.optimizer.L2ControlFlowGraph.Zone
import avail.optimizer.L2Optimizer.GenerationMode
import avail.optimizer.L2Optimizer.GenerationMode.ByRegister
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.Frame
import avail.optimizer.values.L2SemanticValue
import avail.utility.structures.EnumMap

/**
 * The interface for objects that can act as the target of L2 code generation.
 */
interface L2GeneratorInterface : L2Visualizable
{
	/**
	 * The amount of [effort][OptimizationLevel] to apply to the current
	 * optimization attempt.
	 */
	val optimizationLevel: OptimizationLevel

	/**
	 * Declare [toString] in this interface so implementers will redirect to the
	 * version in [L2Generator].
	 */
	override fun toString(): String

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
	 * An indicator that retroactive generation (prior to a generated edge) is
	 * currently taking place.
	 */
	var isGeneratingRetroactively: Boolean

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
		zone: Zone? = null,
		isCold: Boolean = false
	): L2BasicBlock

	/**
	 * Answer the restriction for the given [L2SemanticValue] at the current
	 * code generation position.
	 */
	fun restrictionFor(semanticValue: L2SemanticValue): TypeRestriction

	/**
	 * Add an [L2Instruction].
	 *
	 * @param instruction
	 *   The instruction to add.
	 */
	fun addInstruction(instruction: L2Instruction)

	/**
	 * A convenience operation.  When an [L2GeneratorInterface] is in scope as a
	 * receiver, the unary "+" will add a provided instruction, perhaps
	 * postponing it, but still updating the manifest.
	 */
	operator fun L2Instruction.unaryPlus()
	{
		assert(this !is L2_PHI<*>)
		if (!canBePostponed || mode != BySemanticValue)
		{
			addInstruction(this)
			return
		}
		postponeInstruction(this@L2GeneratorInterface)
	}

	/** Add an instruction that should not be reachable at runtime. */
	fun addUnreachableCode()

	/**
	 * Create a new [L2SemanticValue] to use as a temporary value.
	 *
	 * @param name
	 *   The optional name to describe the purpose of the temp.
	 */
	fun newTemp(name: String?): L2SemanticValue

	/**
	 * Place the source and targets in the same synonym if they're not already.
	 *
	 * @param sourceSemanticValue
	 *   Which [L2SemanticValue] to read.
	 * @param targetSemanticValues
	 *   Which [L2SemanticValue]s will have the same value as the source
	 *   semantic value.
	 */
	fun move(
		sourceSemanticValue: L2SemanticValue,
		targetSemanticValues: Iterable<L2SemanticValue>)

	/**
	 * Write instructions to extract the current function, and answer an
	 * [L2ReadBoxedOperand] for the register that will hold the function
	 * afterward.
	 */
	@Override
	fun currentFunction(
		frame: Frame,
		exactFunctionOrNull: A_Function?,
		functionType: A_Type
	): L2ReadBoxedOperand
	{
		val semanticFunction = frame.function()
		if (currentManifest.hasSemanticValue(semanticFunction))
		{
			// Note the current function can't ever be an int or float.
			return readBoxed(semanticFunction)
		}
		// We have to get it into a register.
		if (exactFunctionOrNull !== null)
		{
			// The exact function is known.
			return boxedConstant(exactFunctionOrNull)
		}
		// The exact function isn't known, but we know the raw function, so
		// we statically know the function type.
		val restriction = restrictionForType(functionType)
		val functionWrite = boxedWrite(semanticFunction, restriction)
		+L2_GET_CURRENT_FUNCTION(functionWrite)
		return readBoxed(functionWrite)
	}

	/**
	 * Cause a tuple to be constructed from the given [L2ReadBoxedOperand]s.
	 *
	 * @param elements
	 *   The [L2ReadBoxedOperand] that supply the elements of the tuple.
	 * @return
	 *   An [L2ReadBoxedOperand] that will contain the tuple.
	 */
	fun createTuple(elements: List<L2ReadBoxedOperand>): L2ReadBoxedOperand

	/**
	 * Allocate a new [L2IntRegister].  Answer an [L2WriteIntOperand] that
	 * writes to it as the given [L2SemanticValue]s, restricted with the given
	 * [TypeRestriction].
	 *
	 * @param semanticValues
	 *   The [L2SemanticValue]s to write.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new write.
	 * @return
	 *   The new unboxed int write operand.
	 */
	fun intWrite(
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		forceRegister: L2Register<INTEGER_KIND>? = null
	): L2WriteIntOperand

	/**
	 * Allocate a new [L2IntRegister].  Answer an [L2WriteIntOperand] that
	 * writes to it as a new temporary [L2SemanticValue], restricting it with
	 * the given [TypeRestriction].
	 *
	 * @param name
	 *   An optional short name that describes the purpose of this temp.  It
	 *   does not need to be unique.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new operand.
	 * @return
	 *   The new unboxed int write operand.
	 */
	fun intWriteTemp(
		name: String?,
		restriction: TypeRestriction
	): L2WriteIntOperand

	/**
	 * An instruction is being forced, and this is one of its reads.  Ensure any
	 * postponed instructions needed to populate it (recursively) are also
	 * forced.
	 *
	 * @param read
	 *   The [L2ReadOperand] whose [L2SemanticValue] needs to be populaated by
	 *   forcing a postponed instruction.
	 */
	fun <K : RegisterKind<K>> populateForRead(read: L2ReadOperand<K>)

	/**
	 * Emit an instruction to jump to the specified [L2BasicBlock].
	 *
	 * @param targetBlock
	 *   The target [L2BasicBlock].
	 * @param optionalName
	 *   An optional name to display for the edge for presenting in graphs, if
	 *   the branching operation's name for that edge isn't informative.
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
	@Deprecated("")
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
	@Deprecated("")
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
	@Deprecated("")
	fun unboxedFloatConstant(value: Double): L2ReadFloatOperand

	/**
	 * Populate the [L2SemanticValue] if it isn't already.  Handle it already
	 * being populated, being a postponed value in a synonym that has at least
	 * one value with a definition, being a constant, and being output from a
	 * postponed instruction.
	 *
	 * This method *must* populate the semantic value before returning.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to ensure is populated.
	 */
	fun <K: RegisterKind<K>> ensureDefinedOrEmitMove(
		semanticValue: L2SemanticValue,
		kind: K
	): Unit

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
		semanticBoxed: L2SemanticValue
	): L2ReadBoxedOperand

	/**
	 * Return an [L2ReadIntOperand] for the given [L2SemanticValue]. The
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
	 * @param semanticValue
	 *   The [L2SemanticValue] to read as an unboxed int.
	 * @param onFailure
	 *   Where to jump in the event that a dynamic type test against [i32]
	 *   fails. The manifest at this location will not contain bindings for
	 *   the unboxed `int` (since unboxing was not possible).
	 * @return
	 *   The unboxed [L2ReadIntOperand], with this generator set to the success
	 *   path if possible, otherwise answer `null` with no current block.
	 */
	fun readIntInternal(
		semanticValue: L2SemanticValue,
		onFailure: L2BasicBlock
	): L2ReadIntOperand?

	/**
	 * Return an [L2ReadIntOperand] for the given [L2SemanticValue]. The
	 * [TypeRestriction] must have been proven by the VM.  If the semantic value
	 * only has a boxed form, generate code to unbox it.
	 *
	 * If the unboxing could fail due to the [TypeRestriction] not guaranteeing
	 * an [i32], please use the [readInt] generation method instead.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to read as an unboxed int.
	 * @return
	 *   The unboxed [L2ReadIntOperand].
	 */
	fun readIntNoFail(
		semanticValue: L2SemanticValue
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
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction
	): L2WriteBoxedOperand

	/**
	 * Allocate a new [L2BoxedRegister].  Answer an [L2WriteBoxedOperand] that
	 * writes to it as the given [L2SemanticValue], restricting it with the
	 * given [TypeRestriction].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to write.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new write.
	 * @return
	 *   The new boxed write operand.
	 */
	fun boxedWrite(
		semanticValue: L2SemanticValue,
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
		readBoxed(write.pickSemanticValue()).also { read ->
			write.registerIfKnown()?.let(read::setRegister)
		}

	/**
	 * Allocate a new [L2BoxedRegister].  Answer an [L2WriteBoxedOperand] that
	 * writes to it as a new temporary [L2SemanticValue], restricting it with
	 * the given [TypeRestriction].
	 *
	 * @param name
	 *   An optional short name that describes the purpose of this temp.  It
	 *   does not need to be unique.
	 * @param restriction
	 *   The initial [TypeRestriction] for the new operand.
	 * @return
	 *   The new boxed write operand.
	 */
	fun boxedWriteTemp(
		name: String?,
		restriction: TypeRestriction
	): L2WriteBoxedOperand

	/**
	 * Return an [L2ReadFloatOperand] for the given [L2SemanticValue].
	 * The [TypeRestriction] *must* have been proven by the VM.  If the semantic
	 * value only has a boxed form, generate code to unbox it.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to read as an unboxed double.
	 * @return
	 *   The unboxed [L2ReadFloatOperand].
	 */
	fun readFloatNoFail(
		semanticValue: L2SemanticValue
	): L2ReadFloatOperand

	/**
	 * Attempt to read the given [L2SemanticValue], answering a suitable
	 * [L2ReadOperand] with the given [RegisterKind].  If the requested
	 * [semanticValue] is not present in the [currentManifest] for that kind,
	 * but an equivalent semantic value is present, [L2_MOVE] it into the
	 * specified [semanticValue].  If no equivalent semantic value is present,
	 * answer `null`.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to ensure is populated, if possible.
	 * @param kind
	 *   The [RegisterKind] for the value to be read.
	 * @return
	 *   An [L2ReadOperand] that reads the request [semanticValue], or `null` if
	 *   it cannot be arronged to be present with only a move.
	 */
	fun <K: RegisterKind<K>> readIfAvailable(
		semanticValue: L2SemanticValue,
		kind: K
	): L2ReadOperand<K>?

	/**
	 * Answer the current [L2BasicBlock] being generated, or `null` if none.
	 *
	 * @return
	 *   The current [L2BasicBlock] or `null`.
	 */
	fun currentBlockOrNull(): L2BasicBlock?

	/**
	 * Answer the current [L2BasicBlock] being generated.
	 *
	 * @return
	 *   The current [L2BasicBlock].
	 */
	fun currentBlock(): L2BasicBlock = currentBlockOrNull()!!

	/**
	 * Start code regeneration for the given [L2BasicBlock].  This is not a loop
	 * head, so ensure all predecessor blocks have already finished generation.
	 *
	 * If [mode] is [BySemanticValue] (the default), reconcile the live
	 * [L2SemanticValue]s and how they're grouped into [L2Synonym]s in each
	 * predecessor edge, creating [L2_PHI]s as needed.
	 *
	 * @param block
	 *   The [L2BasicBlock] beginning its code generation.
	 * @param regenerator
	 *   The optional [L2Regenerator] being written to, if available.
	 */
	fun startBlock(
		block: L2BasicBlock,
		regenerator: L2Regenerator? = null
	): Unit

	/**
	 * Given a register containing a function and a parameter index, emit code
	 * to extract the parameter type at runtime from the actual function.
	 *
	 * @param functionRead
	 *   The register that will hold the function at runtime.
	 * @param parameterIndex
	 *   Which function parameter should have its type extracted.
	 * @return
	 *   The register containing the parameter type.
	 */
	fun extractParameterTypeFromFunction(
		functionRead: L2ReadBoxedOperand,
		parameterIndex: Int
	): L2ReadBoxedOperand

	/**
	 * Create an [L2BasicBlock], and mark it as a loop head.
	 *
	 * @param name
	 *   The name of the new loop head block.
	 * @return
	 *   The loop head block.
	 */
	fun createLoopHeadBlock(name: String): L2BasicBlock

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
	 * Generate a conditional branch to either [equalBlock] or [unequalBlock],
	 * depending on whether [firstValue] is equal to [secondValue].
	 *
	 * @param firstValue
	 *   The source of the first value to compare.
	 * @param secondValue
	 *   The source of the second value of the comparison
	 * @param equalBlock
	 *   Where to go if the values are equal.
	 * @param unequalBlock
	 *   Where to go if the values are unequal.
	 */
	fun jumpIfEqualsObjects(
		firstValue: L2ReadBoxedOperand,
		secondValue: L2ReadBoxedOperand,
		equalBlock: L2BasicBlock,
		unequalBlock: L2BasicBlock)

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
	 * @param readToTest
	 *   The [L2ReadBoxedOperand] whose content should be compared.
	 * @param constantValue
	 *   The [A_BasicObject] to compare against.
	 * @param passBlock
	 *   Where to go if the register's value equals the constant.
	 * @param failBlock
	 *   Where to go if the register's value does not equal the constant.
	 */
	fun jumpIfEqualsConstant(
		readToTest: L2ReadBoxedOperand,
		constantValue: A_BasicObject,
		passBlock: L2BasicBlock,
		failBlock: L2BasicBlock)

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
	 * Given a register that holds the function to invoke, answer either the
	 * [A_RawFunction] it will be known to run, or `null`.
	 *
	 * @param functionToCallRead
	 *   The [L2ReadBoxedOperand] containing the function to invoke.
	 * @return
	 *   Either `null` or the function's [A_RawFunction].
	 */
	fun determineRawFunction(
		functionToCallRead: L2ReadBoxedOperand
	): A_RawFunction?

	/**
	 * Record the fact that the chunk being created depends on the given
	 * [A_ChunkDependable].  If that `A_ChunkDependable` changes, the chunk will
	 * be invalidated.
	 *
	 * @param contingentValue
	 *   The [AvailObject] that the chunk will be contingent on.
	 */
	fun addContingentValue(contingentValue: A_ChunkDependable)

	/**
	 * Generate a [Level&#32;Two&#32;chunk][L2Chunk] from the control flow
	 * graph, install it, and return it.
	 *
	 * @param code
	 *   The [A_RawFunction] which is the source of chunk creation.
	 * @return
	 *   The [L2Chunk] that was created and installed.
	 */
	fun createChunk(code: A_RawFunction): L2Chunk

	/**
	 * Given an [L2ReadBoxedOperand] that will hold a tuple and a fixed index
	 * that is known to be in range, generate code to populate the given
	 * [L2SemanticValue]s with that element.
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
	 *   The [L2SemanticValue]s that will containing the element.
	 */
	fun extractTupleElement(
		tupleRead: L2ReadBoxedOperand,
		index: Int,
		destinationSemanticValues: Set<L2SemanticValue>)

	/**
	 * Given a register that will hold a tuple, check that the tuple has the
	 * number of elements and statically satisfies the corresponding provided
	 * type constraints.  If so, generate code and answer a list of register
	 * reads corresponding to the elements of the tuple; otherwise, generate no
	 * code and answer null.
	 *
	 * Depending on the source of the tuple, this may cause the creation of
	 * the tuple to be entirely elided.
	 *
	 * @param tupleRead
	 *   The [L2ReadBoxedOperand] providing the tuple.
	 * @param requiredTypes
	 *   The required [types][A_Type] against which to check the tuple's own
	 *   type.
	 * @return
	 *   A [List] of [L2ReadBoxedOperand]s corresponding to the tuple's
	 *   elements, or `null` if the tuple could not be proven to have the
	 *   required shape and type.
	 */
	fun explodeTupleIfPossible(
		tupleRead: L2ReadBoxedOperand,
		requiredTypes: List<A_Type>
	): List<L2ReadBoxedOperand>?

	/**
	 * Force all postponed instructions for any semantic value synonymous with
	 * the given [semanticValue], and having the specified [kind].
	 */
	fun <K: RegisterKind<K>> forceTranslationForRead(
		semanticValue: L2SemanticValue,
		kind: K)

	/**
	 * Force all postponed instructions to be generated now.  Some of these may
	 * end up being considered dead code, and will be removed by a later pass.
	 *
	 * If [omitConstantMoves] is true, don't translate [L2_MOVE_CONSTANT]
	 * instructions unless the value is needed by some other instruction being
	 * translated here.
	 */
	fun forceAllPostponedTranslationsExceptConstantMoves(
		omitConstantMoves: Boolean)

	/**
	 * During a control flow merge, the phi creation mechanism detected that the
	 * incoming edges all provided a particular [L2SemanticValue] for a
	 * [RegisterKind], at least one source was a postponed instruction, and *not
	 * all* of the incoming edges had their values postponed by the same
	 * instruction.
	 *
	 * Go back to just before the edge (the caller must call [splitEdge] if
	 * necessary, to ensure it's in edge-split SSA form locally, so the new
	 * instruction won't appear on irrelevant paths), and generate the postponed
	 * instruction responsible for the given semantic value.  Due to edge-split
	 * form (locally), the predecessor block always ends with an unconditional
	 * jump, so we ensure the instruction plays its effect against that jump's
	 * sole edge's manifest.
	 *
	 * Do this for each (semantic value, kind) pair in the iterable.
	 */
	fun forcePostponedTranslationsBeforeEdge(
		edge: L2PcOperand,
		semanticValuesAndKinds:
			Iterable<Pair<L2SemanticValue, RegisterKind<*>>>)

	/**
	 * Split the given edge into two, with a new block in the middle.  Given the
	 * edge
	 *
	 * ```A --e1-> C```
	 *
	 * between blocks A and C, the end state should be
	 *
	 * ```A --e2-> B --e1-> C```
	 *
	 * Note that the orignal edge stays connected to the final target block C,
	 * and e2 is added between A and the new block B. e2's initial manifest is a
	 * copy of e1's manifest.  B contains only an unconditional [L2_JUMP], whose
	 * sole [L2PcOperand] is the edge e1.
	 *
	 * * Be careful to maintain predecessor order at the target block C.
	 * * Block B is inserted into the list of blocks immediately after A.
	 * * Block B will be in the same [Zone] as block A.
	 * * Block B will be considered [L2BasicBlock.isCold] if C is cold.
	 *
	 * @param edge
	 *   The [L2PcOperand] to split within this generator's graph.
	 */
	fun splitEdge(edge: L2PcOperand)

	/**
	 * Force emission of any delayed writes to local variables.
	 */
	fun forcePostponedWritesToLocals()

	/**
	 * Answer a suitable instruction to add to indicate the code at the current
	 * position should not be reachable due to an impossible constraint.  Note
	 * that we normally use an [L2_IMPOSSIBLE_CODE], but if we're in the middle
	 * of generating code retroactively before an existing edge, we must not
	 * destroy that edge, so use an [L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW]
	 * which doesn't itself alter control flow.
	 */
	fun impossibleCodeInstruction(): L2Instruction = when
	{
		isGeneratingRetroactively -> L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW()
		else -> L2_IMPOSSIBLE_CODE()
	}

	/**
	 * Pass-through to [L2ControlFlowGraph].  This can be used in the debugger
	 * to open a view of the graph in an external program associated with the
	 * ".dot" file type.
	 */
	abstract override fun visualize(
		generator: L2Generator?,
		focusValue: L2SemanticValue?)

	/**
	 * Pass-through to [L2ControlFlowGraph].  This can be used in the debugger
	 * to open a view of the graph in an external program associated with the
	 * ".dot" file type.
	 */
	abstract override fun simplyVisualize(
		generator: L2Generator?,
		focusValue: L2SemanticValue?)

	companion object
	{
		/**
		 * Return an [L2ReadIntOperand] for the given [L2SemanticValue].
		 * The [TypeRestriction] must have been proven by the VM.  If the
		 * semantic value only has a boxed form, generate code to unbox it.
		 *
		 * In the case that unboxing may fail, a branch to the supplied
		 * onFailure [L2BasicBlock] will be generated.  If the unboxing cannot
		 * fail (or if a corresponding [L2IntRegister] already exists), no
		 * branch will lead to onFailure, which can be determined by the client
		 * by testing [L2BasicBlock.currentlyReachable].
		 *
		 * In any case, if success is possible, the generation position after
		 * this call is along the success path.
		 *
		 * If unboxing will *always* fail, invoke [ifCannotSucceed], which
		 * yields [Nothing] (does not return), allowing an enforced escape
		 * without having to construct a dummy [L2ReadIntOperand].
		 *
		 * @param semanticValue
		 *   The [L2SemanticValue] to read as an unboxed int.
		 * @param onFailure
		 *   Where to jump in the event that a dynamic type test against [i32]
		 *   fails. The manifest at this location will not contain bindings for
		 *   the unboxed `int` (since unboxing was not possible).
		 * @param ifCannotSucceed
		 *   What to execute if the unboxing will always fail.  This is
		 *   evaluated without having affected the initial current block.
		 * @return
		 *   The unboxed [L2ReadIntOperand], with this generator set to the
		 *   success path.
		 */
		inline fun L2GeneratorInterface.readInt(
			semanticValue: L2SemanticValue,
			onFailure: L2BasicBlock,
			ifCannotSucceed: ()->Nothing
		): L2ReadIntOperand
		{
			if (!currentlyReachable()) ifCannotSucceed()
			return readIntInternal(semanticValue, onFailure) ?: run {
				assert(!currentlyReachable())
				ifCannotSucceed()
			}
		}

		/**
		 * Generate code to extract two boxed values into int registers.  If
		 * there's a path where they both succeed, return the [Pair] of [L2ReadIntOperand]s,
		 * with the generator positioned at the success path.  If there was a
		 * way for one or the other extraction to fail, there will be a path
		 * to the supplied onFailure [L2BasicBlock].  If one of the extractions
		 * *cannot* succeed, control flow is merged if necessary (i.e., if this
		 * is detected during the second extraction), and [ifCannotSucceed] is
		 * invoked with control flow unaffected.  Since the lambda has a return
		 * type of [Nothing], the invocation should escape in some way.
		 *
		 * @param semanticValue1
		 *   The first [L2SemanticValue] to read as an unboxed int.
		 * @param semanticValue2
		 *   The second [L2SemanticValue] to read as an unboxed int.
		 * @param onFailure
		 *   Where to jump in the event that a dynamic type test against [i32]
		 *   fails. The manifest at this location will not contain bindings for
		 *   the unboxed `int` (since unboxing was not possible).
		 * @param ifCannotSucceed
		 *   What to execute if one of the unboxings will always fail.  This
		 *   is evaluated without having affected the initial current block.
		 * @return
		 *   The [Pair] of unboxed [L2ReadIntOperand]s, with this generator
		 *   set to the success path.
		 */
		inline fun L2GeneratorInterface.readTwoInts(
			semanticValue1: L2SemanticValue,
			semanticValue2: L2SemanticValue,
			onFailure: L2BasicBlock,
			ifCannotSucceed: ()->Nothing
		): Pair<L2ReadIntOperand, L2ReadIntOperand>
		{
			if (!currentManifest.canUnboxInt(semanticValue1)
				|| !currentManifest.canUnboxInt(semanticValue2))
			{
				ifCannotSucceed()
			}
			val firstInt = readInt(semanticValue1, onFailure) {
				error("First unboxing should have been possible")
			}
			val secondInt = readInt(semanticValue2, onFailure) {
				error("Second unboxing should have been possible")
			}
			return Pair(firstInt, secondInt)
		}
	}

	/**
	 * Using the manifest in the implied receiver, answer whether it's possible
	 * to extract a 32-bit integer from the given unboxed semantic value.
	 *
	 * @receiver
	 *   The [L2ValueManifest] to use for checking unboxing possibility.
	 * @param semanticValue
	 *   The target [L2SemanticValue] to check for unboxability.
	 * @return
	 *   Whether unboxing the given semantic value is possible.
	 */
	fun L2ValueManifest.canUnboxInt(
		semanticValue: L2SemanticValue
	): Boolean
	{
		if (hasSemanticValue(semanticValue))
			return restrictionFor(semanticValue).intersectsType(i32)
		equivalentSemanticValue(semanticValue)?.let {
			return restrictionFor(it).intersectsType(i32)
		}
		return false
	}
}
