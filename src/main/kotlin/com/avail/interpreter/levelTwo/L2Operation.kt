/*
 * L2Operation.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.interpreter.levelTwo

import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.bundles.A_Bundle.Companion.message
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.typeAtIndex
import com.avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import com.avail.descriptor.variables.A_Variable
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand
import com.avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.interpreter.levelTwo.operand.L2Operand
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadOperand
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import com.avail.interpreter.levelTwo.operand.L2SelectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding
import com.avail.interpreter.levelTwo.operation.L2ControlFlowOperation
import com.avail.interpreter.levelTwo.operation.L2_MOVE_OUTER_VARIABLE
import com.avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import com.avail.interpreter.levelTwo.operation.L2_TUPLE_AT_CONSTANT
import com.avail.interpreter.levelTwo.operation.L2_VIRTUAL_CREATE_LABEL
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind
import com.avail.optimizer.L2BasicBlock
import com.avail.optimizer.L2ControlFlowGraph.Zone
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.reoptimizer.L2Inliner
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport.L2_TO_JVM_TRANSLATION_TIME
import com.avail.utility.Strings.escape
import com.avail.utility.Strings.increaseIndentation
import com.avail.utility.cast
import org.objectweb.asm.MethodVisitor

/**
 * The instruction set for the
 * [Level&#32;Two&#32;Avail&#32;interpreter][Interpreter]. Avail programs can
 * only see as far down as the level one nybblecode representation.  Level two
 * translations are invisibly created as necessary to boost performance of
 * frequently executed code.  Technically level two is an optional part of an
 * Avail implementation, but modern hardware has enough memory that this should
 * really always be present.
 *
 * @constructor
 *   Protect the constructor so the subclasses can maintain a fly-weight pattern
 *   (or arguably a singleton).
 *
 * @param theNamedOperandTypes
 *   The [L2NamedOperandType]s that describe the layout of operands for my
 *   instructions.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class L2Operation
@Suppress("LeakingThis")
protected constructor(
	name: String?,
	vararg theNamedOperandTypes: L2NamedOperandType)
{
	protected constructor(
		vararg theNamedOperandTypes: L2NamedOperandType
	) : this(null, *theNamedOperandTypes)

	/**
	 * A brief hierarchy of classes for sensibly parameterizing the
	 * [ReadsHiddenVariable] and [WritesHiddenVariable] annotations on an
	 * `L2Operation`s.  We'd use an `enum` here, but they don't play at all
	 * nicely with annotations in Java.
	 */
	abstract class HiddenVariable
	{
		/** How the current continuation field is affected.  */
		@HiddenVariableShift(0)
		class CURRENT_CONTINUATION : HiddenVariable()

		/** How the current function field is affected.  */
		@HiddenVariableShift(1)
		class CURRENT_FUNCTION : HiddenVariable()

		/** How the current arguments of this frame are affected.  */
		@HiddenVariableShift(2)
		class CURRENT_ARGUMENTS : HiddenVariable()

		/** How the latest return value field is affected.  */
		@HiddenVariableShift(3)
		class LATEST_RETURN_VALUE : HiddenVariable()

		/** How the current stack reifier field is affected.  */
		@HiddenVariableShift(4)
		class STACK_REIFIER : HiddenVariable()

		/**
		 * How any other global variables are affected.  This includes things
		 * like the global exception reporter, the stringification function,
		 * observerless setup, etc.
		 *
		 * [Primitive]s are annotated with the [Flag.ReadsFromHiddenGlobalState]
		 * and [Flag.WritesToHiddenGlobalState] flags in their constructors to
		 * indicate that `GLOBAL_STATE` is affected.
		 */
		@HiddenVariableShift(5)
		class GLOBAL_STATE : HiddenVariable()
	}

	/**
	 * The bitwise-or of the masks of [HiddenVariable]s that are read by
	 * [L2Instruction]s using this operation.  Note that all reads are
	 * considered to happen before all writes.
	 */
	@JvmField
	var readsHiddenVariablesMask = 0

	/**
	 * The bitwise-or of the masks of [HiddenVariable]s that are overwritten by
	 * [L2Instruction]s using this operation.  Note that all reads are
	 * considered to happen before all writes.
	 */
	@JvmField
	var writesHiddenVariablesMask = 0

	/**
	 * Is the enclosing [L2Instruction] an entry point into its [L2Chunk]?
	 *
	 * @param instruction
	 *   The enclosing `L2Instruction`.
	 * @return
	 *   `true` if this `L2Operation` is an entry point, `false` otherwise.
	 */
	open fun isEntryPoint(instruction: L2Instruction): Boolean = false

	/**
	 * The [named&#32;operand&#32;types][L2NamedOperandType] that this
	 * [operation][L2Operation] expects.
	 */
	@JvmField
	val namedOperandTypes: Array<out L2NamedOperandType> =
		theNamedOperandTypes.clone().also { types ->
			assert(this is L2ControlFlowOperation
				|| this is L2_SAVE_ALL_AND_PC_TO_INT
				|| types.none { it.operandType() == L2OperandType.PC })
		}

	/**
	 * Answer the [named&#32;operand&#32;types][L2NamedOperandType] that this
	 * `L2Operation operation` expects.
	 *
	 * @return The named operand types that this operation expects.
	 */
	fun operandTypes(): Array<out L2NamedOperandType> = namedOperandTypes

	/**
	 * Initialize the name from the constructor argument, or produce a default
	 * if it was unspecified or null.
	 */
	private val name: String = name ?: computeDefaultName()

	/**
	 * Answer the name of this `L2Operation`.
	 *
	 * @return
	 *   The operation name, suitable for symbolic debugging of level two
	 *   generated code.
	 */
	fun name(): String = name

	/**
	 * A [Statistic] that records the number of nanoseconds spent while
	 * executing [L2Instruction]s that use this operation.
	 */
	@JvmField
	val jvmTranslationTime = Statistic(L2_TO_JVM_TRANSLATION_TIME, name())

	/**
	 * Answer a suitable default symbolic name for this operation.
	 */
	private fun computeDefaultName(): String =
		with(javaClass.simpleName) {
			when
			{
				startsWith("L2_") -> substring(3)
				else -> this
			}
		}

	/**
	 * Propagate type, value, alias, and source instruction information due to
	 * the execution of this instruction.  If the operation
	 * [altersControlFlow], expect a [RegisterSet] per outgoing edge, in the
	 * same order.  Otherwise expect one [RegisterSet].
	 *
	 * @param instruction
	 *   The L2Instruction containing this L2Operation.
	 * @param registerSets
	 *   A list of RegisterSets to update with information that this operation
	 *   provides.
	 * @param generator
	 *   The L2Generator for which to advance the type analysis.
	 */
	protected open fun propagateTypes(
		instruction: L2Instruction,
		registerSets: List<RegisterSet>,
		generator: L2Generator)
	{
		assert(this !is L2ControlFlowOperation)
		throw UnsupportedOperationException(
			"Multi-target propagateTypes is only applicable to an "
				+ "L2ControlFlowOperation")
	}

	/**
	 * Propagate type, value, alias, and source instruction information due to
	 * the execution of this instruction.  The instruction must not have
	 * multiple possible successor instructions.
	 *
	 * @param instruction
	 *   The L2Instruction containing this L2Operation.
	 * @param registerSet
	 *   A [RegisterSet] to supply with information.
	 * @param generator
	 *   The L2Generator for which to advance the type analysis.
	 * @see [propagateTypes]
	 */
	protected open fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		// We're phasing this out, so don't make this an abstract method.
		throw UnsupportedOperationException(
			"Single-target propagateTypes should be overridden.")
	}

	/**
	 * Answer whether an instruction using this operation should be emitted
	 * during final code generation. For example, a move between registers with
	 * the same finalIndex can be left out during code generation, although it
	 * can't actually be removed before then.
	 *
	 * @param instruction
	 *   The instruction containing this operation.
	 * @return
	 *   A `boolean` indicating if this operation should be emitted.
	 */
	open fun shouldEmit(instruction: L2Instruction): Boolean = true

	/**
	 * Answer whether this `L2Operation` changes the state of the interpreter in
	 * any way other than by writing to its destination registers. Most
	 * operations are computational and don't have side effects.
	 *
	 * @return Whether this operation has any side effect.
	 */
	open fun hasSideEffect(): Boolean = false

	/**
	 * Answer whether the given [L2Instruction] (whose operation must be the
	 * receiver) changes the state of the interpreter in any way other than by
	 * writing to its destination registers. Most operations are computational
	 * and don't have side effects.
	 *
	 * Most enum instances can override [hasSideEffect] if `false` isn't good
	 * enough, but some might need to know details of the actual [L2Instruction]
	 * – in which case they should override this method instead.
	 *
	 * @param instruction
	 *   The `L2Instruction` for which a side effect test is being performed.
	 * @return
	 *   Whether that L2Instruction has any side effect.
	 */
	open fun hasSideEffect(instruction: L2Instruction): Boolean
	{
		assert(instruction.operation() === this)
		return hasSideEffect()
	}

	/**
	 * Answer whether execution of this instruction can divert the flow of
	 * control from the next instruction.  An L2Operation either always falls
	 * through or always alters control.
	 *
	 * @return
	 *   Whether this operation alters the flow of control.
	 */
	open fun altersControlFlow(): Boolean = false

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
	open fun goesMultipleWays(): Boolean = false

	/**
	 * Answer whether execution of this instruction causes a
	 * [variable][A_Variable] to be read.
	 *
	 * @return
	 *   Whether the instruction causes a variable to be read.
	 */
	open val isVariableGet: Boolean
		get() = false

	/**
	 * Answer whether execution of this instruction causes a
	 * [variable][A_Variable] to be written.
	 *
	 * @return
	 *   Whether the instruction causes a variable to be written.
	 */
	open val isVariableSet: Boolean
		get() = false

	/**
	 * Answer whether this operation is a move between (compatible) registers.
	 *
	 * @return
	 *   `true` if this operation simply moves data between two registers of the
	 *   same [RegisterKind], otherwise `false`.
	 */
	open val isMove: Boolean
		get() = false

	/**
	 * Answer whether this operation is a phi-function.  This is a convenient
	 * fiction that allows control flow to merge while in SSA form.
	 *
	 * @return
	 *   `true` if this is a phi operation, `false` otherwise.
	 */
	open val isPhi: Boolean
		get() = false

	/**
	 * Answer whether this operation is a placeholder, and should be replaced
	 * using [L2Generator.replaceInstructionByGenerating].
	 * Placeholder instructions (like [L2_VIRTUAL_CREATE_LABEL]) are free
	 * to be moved through much of the control flow graph, even though the
	 * subgraphs they eventually get replaced by would be too complex to move.
	 * The mobility of placeholder instructions is essential to postponing stack
	 * reification and label creation into off-ramps (reification [Zone]s)
	 * as much as possible.
	 *
	 * @return
	 *   Whether the [L2Instruction] using this operation is a placeholder,
	 *   subject to later substitution.
	 */
	open fun isPlaceholder(instruction: L2Instruction) = false

	/**
	 * Answer whether this operation causes unconditional control flow jump to
	 * another [L2BasicBlock].
	 *
	 * @return `true` iff this is an unconditional jump.
	 */
	open val isUnconditionalJump: Boolean get() = false

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
		instruction: L2Instruction, manifest: L2ValueManifest)
	{
		val edgeIndexOrder = mutableListOf<Int>()
		val operands = instruction.operands()
		for (i in operands.indices)
		{
			val namedOperandType = namedOperandTypes[i]
			val purpose = namedOperandType.purpose()
			val operand = operands[i]
			if (purpose === null)
			{
				// Process all operands without a purpose first.
				operand.instructionWasAdded(manifest)
			}
			else if (operand is L2PcOperand)
			{
				edgeIndexOrder.add(i)
			}
		}
		// Create separate copies of the manifest for each outgoing edge.
		for (operandIndex in edgeIndexOrder)
		{
			val edge = operands[operandIndex].cast<L2Operand?, L2PcOperand>()
			val purpose = namedOperandTypes[operandIndex].purpose()
			val manifestCopy = L2ValueManifest(manifest)
			for (i in operands.indices)
			{
				val namedOperandType = namedOperandTypes[i]
				if (namedOperandType.purpose() == purpose
					&& operands[i] !is L2PcOperand)
				{
					operands[i].instructionWasAdded(manifestCopy)
				}
			}
			edge.instructionWasAdded(manifestCopy)
		}
	}

	/**
	 * This is the operation for the given instruction, which was just inserted
	 * into its basic block as part of an optimization pass.  Do any
	 * post-processing appropriate for having inserted the instruction.
	 *
	 * @param instruction
	 *   The [L2Instruction] that was just inserted.
	 */
	fun instructionWasInserted(instruction: L2Instruction)
	{
		assert(!isEntryPoint(instruction)
			   || instruction.basicBlock().instructions()[0] == instruction)
		{ "Entry point instruction must be at start of a block" }
		instruction.operandsDo { it.instructionWasInserted(instruction) }
	}

	/**
	 * Write the given instruction's equivalent effect through the given
	 * [L2Inliner].  The given [L2Instruction]'s
	 * [operation][L2Instruction.operation] must be the current receiver.
	 *
	 * @param instruction
	 *   The [L2Instruction] for which to write an equivalent effect to the
	 *   inliner.
	 * @param transformedOperands
	 *   The operands of the instruction, already transformed for the inliner.
	 * @param retranslator
	 *   The [L2Inliner] through which to write the instruction's equivalent
	 *   effect.
	 */
	fun emitTransformedInstruction(
		instruction: L2Instruction,
		transformedOperands: Array<L2Operand>,
		retranslator: L2Inliner)
	{
		assert(instruction.operation() === this)
		retranslator.emitInstruction(this, *transformedOperands)
	}

	/**
	 * Emit code to extract the specified outer value from the function produced
	 * by this instruction.  The new code is appended to the provided list of
	 * instructions, which may be at a code generation position unrelated to the
	 * receiver.  The extracted outer variable will be written to the provided
	 * target register.
	 *
	 * @param instruction
	 *   The instruction that produced the function.  Its
	 *   [operation][L2Instruction.operation] is the receiver.
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
		instruction: L2Instruction,
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
		generator: L2Generator): L2ReadBoxedOperand
	{
		assert(instruction.operation() === this)
		val writer = generator.boxedWriteTemp(
			restrictionForType(
				outerType, RestrictionFlagEncoding.BOXED))
		generator.addInstruction(
			L2_MOVE_OUTER_VARIABLE,
			L2IntImmediateOperand(outerIndex),
			functionRegister,
			writer)
		return generator.readBoxed(writer)
	}

	/**
	 * Extract the constant [A_RawFunction] that's enclosed by the function
	 * produced or passed along by this instruction.
	 *
	 * @param instruction
	 *   The instruction to examine.
	 * @return
	 *   The constant [A_RawFunction] extracted from the instruction, or `null`
	 *   if unknown.
	 */
	open fun getConstantCodeFrom(instruction: L2Instruction): A_RawFunction?
	{
		assert(instruction.operation() === this)
		return null
	}

	/**
	 * If this instruction is an attempt to execute a primitive, answer the
	 * register into which the primitive's result will be written if successful.
	 * Otherwise answer `null`.
	 *
	 * @param instruction
	 *   The [L2Instruction] for which the receiver is the `L2Operation`.
	 * @return
	 *   The register into which the primitive attempted by this instruction
	 *   will write its result, or null if the instruction isn't an attempt to
	 *   run a primitive.
	 */
	open fun primitiveResultRegister(
		instruction: L2Instruction): L2WriteBoxedOperand?
	{
		assert(instruction.operation() === this)
		return null
	}

	/**
	 * Extract the operands which are [L2PcOperand]s.  These are what lead
	 * to other [L2BasicBlock]s.  They also carry an edge-specific array
	 * of slots, and edge-specific [TypeRestriction]s for registers.
	 *
	 * @param instruction
	 *   The [L2Instruction] to examine.
	 * @return
	 *   The [List] of target [L2PcOperand]s that are operands of the given
	 *   instruction.  These may be reachable directly via a control flow
	 *   change, or reachable only from some other mechanism like continuation
	 *   reification and later resumption of a continuation.
	 */
	open fun targetEdges(instruction: L2Instruction): List<L2PcOperand> =
		emptyList()

	// Skip the L2_ prefix, as it is redundant in context.
	override fun toString(): String = name()

	/**
	 * Produce a sensible preamble for the textual rendition of the specified
	 * [L2Instruction] that includes the [offset][L2Instruction.offset] and
	 * [name][toString] of the `L2Operation`.
	 *
	 * @param instruction
	 *   The `L2Instruction`.
	 * @param builder
	 *   The `StringBuilder` to which the preamble should be written.
	 */
	protected fun renderPreamble(
		instruction: L2Instruction, builder: StringBuilder)
	{
		assert(this === instruction.operation())
		val offset = instruction.offset()
		if (offset != -1)
		{
			builder.append(instruction.offset())
			builder.append(". ")
		}
		builder.append(this)
	}

	/**
	 * Generically render all [operands][L2Operand] of the specified
	 * [L2Instruction] starting at the specified index.
	 *
	 * @param instruction
	 *   The `L2Instruction`.
	 * @param start
	 *   The start index.
	 * @param desiredTypes
	 *   The [L2OperandType]s of [L2Operand]s to be included in generic
	 *   renditions. Customized renditions may not honor these types.
	 * @param builder
	 *   The [StringBuilder] to which the rendition should be written.
	 */
	protected fun renderOperandsStartingAt(
		instruction: L2Instruction,
		start: Int,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder)
	{
		val operands = instruction.operands()
		val types = operandTypes()
		var i = start
		val limit = operands.size
		while (i < limit)
		{
			val type = types[i]
			if (desiredTypes.contains(type.operandType()))
			{
				val operand = instruction.operand<L2Operand>(i)
				builder.append("\n\t")
				assert(operand.operandType() == type.operandType())
				builder.append(type.name())
				builder.append(" = ")
				builder.append(increaseIndentation(operand.toString(), 1))
			}
			i++
		}
	}

	/**
	 * Produce a sensible textual rendition of the specified [L2Instruction].
	 *
	 * @param instruction
	 *   The `L2Instruction`.
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
	open fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this === instruction.operation())
		renderPreamble(instruction, builder)
		val types = operandTypes()
		val operands = instruction.operands()
		var i = 0
		val limit = operands.size
		while (i < limit)
		{
			val type = types[i]
			if (desiredTypes.contains(type.operandType()))
			{
				val operand = instruction.operand<L2Operand>(i)
				builder.append("\n\t")
				assert(operand.operandType() == type.operandType())
				builder.append(type.name())
				builder.append(" = ")
				operand.appendWithWarningsTo(builder, 1, warningStyleChange)
			}
			i++
		}
	}

	/**
	 * Output the instruction compactly to the builder.
	 */
	fun simpleAppendTo(instruction: L2Instruction, builder: StringBuilder)
	{
		assert(this === instruction.operation())
		renderPreamble(instruction, builder)
		builder.append(": ")
		val operands = instruction.operands()
		val targets = mutableListOf<String>()
		val sources = mutableListOf<String>()
		val commands = mutableListOf<String>()
		for (operand in operands)
		{
			when (operand)
			{
				is L2ConstantOperand -> {
					val value = operand.constant
					when {
						value.isFunction ->
							sources.add(
								value.code().methodName().asNativeString())
						value.isInstanceOf(mostGeneralCompiledCodeType()) ->
							sources.add(value.methodName().asNativeString())
						else -> sources.add(
							escape(operand.constant.toString().run {
								if (length > 20) substring(0, 20) + "…"
								else this
							}).run { substring(1, length - 1) })
					}
				}
				is L2FloatImmediateOperand ->
					sources.add(operand.value.toString())
				is L2IntImmediateOperand ->
					sources.add(operand.value.toString())
				is L2PrimitiveOperand ->
					commands.add(operand.primitive.name)
				is L2ReadOperand<*> ->
					sources.add(operand.register().toString())
				is L2ReadVectorOperand<*, *> -> sources.add(
					operand.elements().joinToString(", ", "[", "]") {
						it.register().toString()
					})
				is L2SelectorOperand ->
					commands.add(operand.bundle.message().atomName().toString())
				is L2WriteOperand<*> ->
					targets.add(operand.register().toString())
			}
		}
		targets.joinTo(builder)
		builder.append(" ⇦ ")
		commands.joinTo(builder, "/")
		sources.joinTo(builder, ", ", "(", ")")
	}

	/**
	 * Translate the specified [L2Instruction] into corresponding JVM
	 * instructions.
	 *
	 * @param translator
	 *   The [JVMTranslator] responsible for the translation.
	 * @param method
	 *   The [method][MethodVisitor] into which the generated JVM instructions
	 *   will be written.
	 * @param instruction
	 *   The [L2Instruction] to translate.
	 */
	abstract fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)

	/**
	 * Generate code to replace this [L2Instruction].  The instruction has
	 * already been removed.  Leave the generator in a state that ensures any
	 * [L2Register]s that would have been written by the old instruction
	 * are instead written by the new code.
	 *
	 * Leave the code generation at the point where subsequent instructions of
	 * the rebuilt block will be re-emitted, whether that's in the same block
	 * or not.
	 *
	 * @param instruction
	 *   The [L2Instruction] being replaced.
	 * @param generator
	 *   An [L2Generator] that has been configured for writing arbitrary
	 *   replacement code for this instruction.
	 */
	open fun generateReplacement(
		instruction: L2Instruction,
		generator: L2Generator)
	{
		throw RuntimeException(
			"A ${instruction.operation()} cannot be transformed " +
				"by regeneration")
	}

	/**
	 * Update the given [L2ValueManifest] with the effect of the given
	 * [L2Instruction].  If a [L2NamedOperandType.Purpose] is given, alter the
	 * manifest to agree with outbound edges having that purpose.
	 *
	 * @param instruction
	 *   The [L2Instruction] having this operation.
	 * @param manifest
	 *   The [L2ValueManifest] to update with the effect of this instruction.
	 * @param optionalPurpose
	 *   If non-`null`, produce tha manifest that should be active along
	 *   outbound edges having the indicated [L2NamedOperandType.Purpose].
	 */
	open fun updateManifest(
		instruction: L2Instruction,
		manifest: L2ValueManifest,
		optionalPurpose: L2NamedOperandType.Purpose?)
	{
		assert(this === instruction.operation())
		val count = namedOperandTypes.size
		val operands = instruction.operands()
		assert(operands.size == count)
		for (i in 0 until count)
		{
			val operand = instruction.operand<L2Operand>(i)
			if (operand is L2WriteOperand<*>)
			{
				val write =
					operand.cast<L2Operand?, L2WriteOperand<*>>()
				// Pay attention to purpose-less writes, or writes for the
				// specified purpose.
				if (namedOperandTypes[i].purpose() === null
					|| namedOperandTypes[i].purpose() == optionalPurpose)
				{
					manifest.recordDefinition(write)
				}
			}
		}
	}

	/**
	 * The given instruction has been declared dead code (the receiver is that
	 * instruction's operation).  If there's an alternative form of that
	 * instruction that should replace it, provide it.
	 *
	 * Note that the old instruction will be removed and the new one added,
	 * so now's a good time to switch [L2PcOperand]s that may need to be
	 * moved between the instructions.
	 *
	 * @param instruction
	 *   The instruction about to be removed or replaced.
	 * @return
	 *   Either null or a replacement `L2Instruction` for the given dead one.
	 */
	open fun optionalReplacementForDeadInstruction(
		instruction: L2Instruction): L2Instruction? = null

	// Do some more initialization for both constructors.
	init
	{
		val readsAnnotation =
			javaClass.getAnnotation(ReadsHiddenVariable::class.java)
		var readMask = 0
		if (readsAnnotation !== null)
		{
			for (hiddenVariableSubclass in readsAnnotation.value)
			{
				val shiftAnnotation =
					hiddenVariableSubclass.java.getAnnotation(
						HiddenVariableShift::class.java)
				readMask = readMask or (1 shl shiftAnnotation.value)
			}
		}
		readsHiddenVariablesMask = readMask
		val writesAnnotation =
			javaClass.getAnnotation(WritesHiddenVariable::class.java)
		var writeMask = 0
		if (writesAnnotation !== null)
		{
			for (hiddenVariableSubclass in writesAnnotation.value)
			{
				val shiftAnnotation =
					hiddenVariableSubclass.java.getAnnotation(
						HiddenVariableShift::class.java)
				writeMask = writeMask or (1 shl shiftAnnotation.value)
			}
		}
		writesHiddenVariablesMask = writeMask
	}

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
	 * @param
	 *   An [L2ReadBoxedOperand] that will contain the specified tuple element.
	 */
	open fun extractTupleElement(
		tupleReg: L2ReadBoxedOperand,
		index: Int,
		generator: L2Generator): L2ReadBoxedOperand
	{
		// The default case is to dynamically extract the value from the tuple.
		val elementWriter = generator.boxedWriteTemp(
			restrictionForType(
				tupleReg.type().typeAtIndex(index),
				RestrictionFlagEncoding.BOXED))
		generator.addInstruction(
			L2_TUPLE_AT_CONSTANT,
			tupleReg,
			L2IntImmediateOperand(index),
			elementWriter)
		return generator.readBoxed(elementWriter)
	}
}
