/*
 * L2Operation.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
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

package com.avail.interpreter.levelTwo;

import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2ControlFlowOperation;
import com.avail.interpreter.levelTwo.operation.L2_MOVE_OUTER_VARIABLE;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2BasicBlock;
import com.avail.optimizer.L2Inliner;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.avail.interpreter.levelTwo.operand.TypeRestriction
	.restriction;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.Strings.increaseIndentation;
import static java.util.Collections.emptyList;

/**
 * The instruction set for the {@linkplain Interpreter level two Avail
 * interpreter}.  Avail programs can only see as far down as the level one
 * nybblecode representation.  Level two translations are invisibly created as
 * necessary to boost performance of frequently executed code.  Technically
 * level two is an optional part of an Avail implementation, but modern hardware
 * has enough memory that this should really always be present.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class L2Operation
{
	/**
	 * Is the enclosing {@link L2Instruction} an entry point into its {@link
	 * L2Chunk}?
	 *
	 * @param instruction
	 *        The enclosing {@code L2Instruction}.
	 * @return {@code true} if this {@code L2Operation} is an entry point,
	 *         {@code false} otherwise.
	 */
	public boolean isEntryPoint (final L2Instruction instruction)
	{
		return false;
	}

	/**
	 * The {@linkplain L2NamedOperandType named operand types} that this
	 * {@linkplain L2Operation operation} expects.
	 */
	protected L2NamedOperandType[] namedOperandTypes;

	/**
	 * Answer the {@linkplain L2NamedOperandType named operand types} that this
	 * {@code L2Operation operation} expects.
	 *
	 * @return The named operand types that this operation expects.
	 */
	public L2NamedOperandType[] operandTypes ()
	{
		return stripNull(namedOperandTypes);
	}

	/**
	 * The name of this level two operation.  This is initialized to be the
	 * {@linkplain Class#getSimpleName() simple name} of the {@link Class}.
	 */
	private final @Nullable String name;

	/**
	 * Answer the name of this {@code L2Operation}.
	 *
	 * @return The operation name, suitable for symbolic debugging of level two
	 *         generated code.
	 */
	public String name ()
	{
		return stripNull(name);
	}

	/**
	 * A {@link Statistic} that records the number of nanoseconds spent while
	 * executing {@link L2Instruction}s that use this operation.
	 */
	public final Statistic jvmTranslationTime;

	/**
	 * Protect the constructor so the subclasses can maintain a fly-weight
	 * pattern (or arguably a singleton).
	 */
	protected L2Operation (final L2NamedOperandType... theNamedOperandTypes)
	{
		final String className = this.getClass().getSimpleName();
		name = className;
		jvmTranslationTime = new Statistic(
			className, StatisticReport.L2_TO_JVM_TRANSLATION_TIME);
		namedOperandTypes = theNamedOperandTypes.clone();

		// The number of targets won't be large, so don't worry about the
		// quadratic cost.  An N-way dispatch would be a different story.
		assert this instanceof L2ControlFlowOperation
			|| Arrays.stream(namedOperandTypes).noneMatch(
				x -> x.operandType() == L2OperandType.PC);
	}

	/**
	 * Propagate type, value, alias, and source instruction information due to
	 * the execution of this instruction.  If the operation {@link
	 * #altersControlFlow()}, expect a {@link RegisterSet} per outgoing edge,
	 * in the same order.  Otherwise expect one {@link RegisterSet}.
	 *
	 * @param instruction
	 *        The L2Instruction containing this L2Operation.
	 * @param registerSets
	 *        A list of RegisterSets to update with information that this
	 *        operation provides.
	 * @param translator
	 *        The L2Translator for which to advance the type analysis.
	 */
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		assert !(this instanceof L2ControlFlowOperation);
		throw new UnsupportedOperationException(
			"Multi-target propagateTypes is only applicable to an "
				+ "L2ControlFlowOperation");
	}

	/**
	 * Propagate type, value, alias, and source instruction information due to
	 * the execution of this instruction.  The instruction must not have
	 * multiple possible successor instructions.
	 *
	 * @param instruction
	 *            The L2Instruction containing this L2Operation.
	 * @param registerSet
	 *            A RegisterSet to supply with information about
	 *            A list of RegisterSets to update with information that this
	 *            operation provides.
	 * @param translator
	 *            The L2Translator for which to advance the type analysis.
	 * @see #propagateTypes(L2Instruction, List, L2Translator)
	 */
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		// We're phasing this out, so don't make this an abstract method.
		throw new UnsupportedOperationException(
			"Single-target propagateTypes should be overridden.");
	}

	/**
	 * Answer whether an instruction using this operation should be emitted
	 * during final code generation. For example, a move between registers with
	 * the same finalIndex can be left out during code generation, although it
	 * can't actually be removed before then.
	 *
	 * @return A {@code boolean} indicating if this operation should be emitted.
	 */
	public boolean shouldEmit (final L2Instruction instruction)
	{
		return true;
	}

	/**
	 * Answer whether this {@code L2Operation} changes the state of the
	 * interpreter in any way other than by writing to its destination
	 * registers. Most operations are computational and don't have side effects.
	 *
	 * @return Whether this operation has any side effect.
	 */
	public boolean hasSideEffect ()
	{
		return false;
	}

	/**
	 * Answer whether the given {@link L2Instruction} (whose operation must be
	 * the receiver) changes the state of the interpreter in any way other than
	 * by writing to its destination registers. Most operations are
	 * computational and don't have side effects.
	 *
	 * <p>
	 * Most enum instances can override {@link #hasSideEffect()} if
	 * {@code false} isn't good enough, but some might need to know details of
	 * the actual {@link L2Instruction} – in which case they should override
	 * this method instead.
	 * </p>
	 *
	 * @param instruction
	 *            The {@code L2Instruction} for which a side effect test is
	 *            being performed.
	 * @return Whether that L2Instruction has any side effect.
	 */
	public boolean hasSideEffect (final L2Instruction instruction)
	{
		assert instruction.operation() == this;
		return hasSideEffect();
	}

	/**
	 * Answer whether execution of this instruction can divert the flow of
	 * control from the next instruction.  An L2Operation either always falls
	 * through or always alters control.
	 *
	 * @return Whether this operation alters the flow of control.
	 */
	public boolean altersControlFlow ()
	{
		return false;
	}

	/**
	 * Answer whether execution of this instruction causes a {@linkplain
	 * A_Variable variable} to be read.
	 *
	 * @return Whether the instruction causes a variable to be read.
	 */
	public boolean isVariableGet ()
	{
		return false;
	}

	/**
	 * Answer whether execution of this instruction causes a {@linkplain
	 * A_Variable variable} to be written.
	 *
	 * @return Whether the instruction causes a variable to be written.
	 */
	public boolean isVariableSet ()
	{
		return false;
	}

	/**
	 * Answer whether this operation is a move between (compatible) registers.
	 *
	 * @return {@code true} if this operation simply moves data between two
	 *         registers of the same {@link RegisterKind}, otherwise {@code
	 *         false}.
	 */
	public boolean isMove ()
	{
		return false;
	}

	/**
	 * Answer whether this operation is a phi-function.  This is a convenient
	 * fiction that allows control flow to merge while in SSA form.
	 *
	 * @return {@code true} if this is a phi operation, {@code false} otherwise.
	 */
	public boolean isPhi ()
	{
		return false;
	}

	/**
	 * This is the operation for the given instruction, which was just added to
	 * its basic block.  Note that the operands have not yet been given the
	 * opportunity
	 *
	 * @param instruction
	 *        The {@link L2Instruction} that was just added.
	 */
	public void instructionWasAdded (final L2Instruction instruction)
	{
		assert !isEntryPoint(instruction)
				|| instruction.basicBlock.instructions().get(0) == instruction
			: "Entry point instruction must be at start of a block";
	}

	/**
	 * Write the given instruction's equivalent effect through the given {@link
	 * L2Inliner}.  The given {@link L2Instruction}'s {@linkplain
	 * L2Instruction#operation operation} must be the current receiver.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} for which to write an equivalent effect
	 *        to the inliner.
	 * @param transformedOperands
	 *        The operands of the instruction, already transformed for the
	 *        inliner.
	 * @param inliner
	 *        The {@link L2Inliner} through which to write the instruction's
	 *        equivalent effect.
	 */
	public void emitTransformedInstruction (
		final L2Instruction instruction,
		final L2Operand[] transformedOperands,
		final L2Inliner inliner)
	{
		assert instruction.operation() == this;
		inliner.emitInstruction(this, transformedOperands);
	}

	/**
	 * Write an alternative to this instruction into the given {@link List} of
	 * instructions.  The state at the start of this instruction has been
	 * provided, but should not be modified.  Answer whether a semantic change
	 * has taken place that might require another pass of flow analysis.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} containing this operation.
	 * @param registerSet
	 *        A {@link RegisterSet} holding type and value information about all
	 *        live registers upon arriving at this instruction.  This method
	 *        should modify the registerSet in-place to indicate the effect on
	 *        register types and values that this instruction will have.
	 * @param translator
	 *        The list of instructions to augment.
	 * @return Whether the regenerated instructions are different enough to
	 *         warrant another pass of flow analysis.
	 */
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1Translator translator)
	{
		// By default just produce the same instruction.
		assert instruction.operation() == this;
		translator.addInstruction(instruction);
		return false;
	}

	/**
	 * Emit code to extract the specified outer value from the function produced
	 * by this instruction.  The new code is appended to the provided list of
	 * instructions, which may be at a code generation position unrelated to the
	 * receiver.  The extracted outer variable will be written to the provided
	 * target register.
	 *
	 * @param instruction
	 *        The instruction that produced the function.  Its {@linkplain
	 *        L2Instruction#operation operation} is the receiver.
	 * @param functionRegister
	 *        The register holding the function after this instruction runs.
	 * @param outerIndex
	 *        The one-based outer index to extract from the function.
	 * @param outerType
	 *        The type of value that must be in that outer.
	 * @param translator
	 *        The {@link L1Translator} into which to write the new code.
	 * @return The {@link L2ReadPointerOperand} holding the outer value.
	 */
	public L2ReadPointerOperand extractFunctionOuterRegister (
		final L2Instruction instruction,
		final L2ReadPointerOperand functionRegister,
		final int outerIndex,
		final A_Type outerType,
		final L1Translator translator)
	{
		assert instruction.operation() == this;
		final L2WritePointerOperand writer =
			translator.newObjectRegisterWriter(restriction(outerType));
		translator.addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2IntImmediateOperand(outerIndex),
			functionRegister,
			writer);
		return writer.read();
	}

	/**
	 * Extract the constant {@link A_RawFunction} that's enclosed by the
	 * function produced or passed along by this instruction.
	 *
	 * @param instruction
	 *        The instruction to examine.
	 * @return The constant {@link A_RawFunction} extracted from the
	 *         instruction, or {@code null} if unknown.
	 */
	public @Nullable A_RawFunction getConstantCodeFrom (
		final L2Instruction instruction)
	{
		assert instruction.operation() == this;
		return null;
	}

	/**
	 * If this instruction is an attempt to execute a primitive, answer the
	 * register into which the primitive's result will be written if successful.
	 * Otherwise answer {@code null}.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} for which the receiver is the {@code
	 *        L2Operation}.
	 * @return The register into which the primitive attempted by this
	 *         instruction will write its result, or null if the instruction
	 *         isn't an attempt to run a primitive.
	 */
	@SuppressWarnings("unused")
	public @Nullable L2WritePointerOperand primitiveResultRegister (
		final L2Instruction instruction)
	{
		assert instruction.operation() == this;
		return null;
	}

	/**
	 * Extract the operands which are {@link L2PcOperand}s.  These are what lead
	 * to other {@link L2BasicBlock}s.  They also carry an edge-specific array
	 * of slots, and edge-specific {@link TypeRestriction}s for registers.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} to examine.
	 * @return The {@link List} of target {@link L2PcOperand}s that are operands
	 *         of the given instruction.  These may be reachable directly via a
	 *         control flow change, or reachable only from some other mechanism
	 *         like continuation reification and later resumption of a
	 *         continuation.
	 */
	public List<L2PcOperand> targetEdges (final L2Instruction instruction)
	{
		return emptyList();
	}

	@Override
	public final String toString ()
	{
		// Skip the L2_ prefix, as it is redundant in context.
		return name().substring(3);
	}

	/**
	 * Produce a sensible preamble for the textual rendition of the specified
	 * {@link L2Instruction} that includes the {@linkplain
	 * L2Instruction#offset() offset} and {@linkplain #toString() name} of the
	 * {@code L2Operation}.
	 *
	 * @param instruction
	 *        The {@code L2Instruction}.
	 * @param builder
	 *        The {@code StringBuilder} to which the preamble should be written.
	 */
	protected final void renderPreamble (
		final L2Instruction instruction,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final int offset = instruction.offset();
		if (offset != -1)
		{
			builder.append(instruction.offset());
			builder.append(". ");
		}
		builder.append(this);
	}

	/**
	 * Generically render all {@linkplain L2Operand operands} of the specified
	 * {@link L2Instruction} starting at the specified index.
	 *
	 * @param instruction
	 *        The {@code L2Instruction}.
	 * @param start
	 *        The start index.
	 * @param desiredTypes
	 *        The {@link L2OperandType}s of {@link L2Operand}s to be included in
	 *        generic renditions. Customized renditions may not honor these
	 *        types.
	 * @param builder
	 *        The {@link StringBuilder} to which the rendition should be
	 *        written.
	 */
	protected final void renderOperandsStartingAt (
		final L2Instruction instruction,
		final int start,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		final L2Operand[] operands = instruction.operands();
		final L2NamedOperandType[] types = operandTypes();
		for (int i = start, limit = operands.length; i < limit; i++)
		{
			final L2NamedOperandType type = types[i];
			if (desiredTypes.contains(type.operandType()))
			{
				final L2Operand operand = instruction.operand(i);
				builder.append("\n\t");
				assert operand.operandType() == type.operandType();
				builder.append(type.name());
				builder.append(" = ");
				builder.append(increaseIndentation(operand.toString(), 1));
			}
		}
	}

	/**
	 * Produce a sensible textual rendition of the specified {@link
	 * L2Instruction}.
	 *
	 * @param instruction
	 *        The {@code L2Instruction}.
	 * @param desiredTypes
	 *        The {@link L2OperandType}s of {@link L2Operand}s to be included in
	 *        generic renditions. Customized renditions may not honor these
	 *        types.
	 * @param builder
	 *        The {@link StringBuilder} to which the rendition should be
	 *        written.
	 */
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		renderPreamble(instruction, builder);
		final L2NamedOperandType[] types = operandTypes();
		final L2Operand[] operands = instruction.operands();
		for (int i = 0, limit = operands.length; i < limit; i++)
		{
			final L2NamedOperandType type = types[i];
			if (desiredTypes.contains(type.operandType()))
			{
				final L2Operand operand = instruction.operand(i);
				builder.append("\n\t");
				assert operand.operandType() == type.operandType();
				builder.append(type.name());
				builder.append(" = ");
				builder.append(increaseIndentation(operand.toString(), 1));
			}
		}
	}

	/**
	 * Translate the specified {@link L2Instruction} into corresponding JVM
	 * instructions.
	 *
	 * @param translator
	 *        The {@link JVMTranslator} responsible for the translation.
	 * @param method
	 *        The {@linkplain MethodVisitor method} into which the generated JVM
	 *        instructions will be written.
	 * @param instruction
	 *        The {@link L2Instruction} to translate.
	 */
	public abstract void translateToJVM (
		JVMTranslator translator,
		MethodVisitor method,
		L2Instruction instruction);

	/**
	 * Augment the array of operands with any that are supposed to be supplied
	 * implicitly by this class.
	 *
	 * @param operands The origginal array of {@link L2Operand}s.
	 * @return The augmented array of {@link L2Operand}s, which may be the same
	 *         as the given array.
	 */
	public L2Operand[] augment (
		final L2Operand[] operands)
	{
		return operands;
	}
}
