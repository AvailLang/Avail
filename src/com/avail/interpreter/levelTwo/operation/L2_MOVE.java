/*
 * L2_MOVE.java
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2NamedOperandType;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteOperand;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Move an {@link AvailObject} from the source to the destination.  The
 * {@link L2Generator} creates more moves than are strictly necessary, but
 * various mechanisms cooperate to remove redundant inter-register moves.
 *
 * <p>
 * The object being moved is not made immutable by this operation, as that
 * is the responsibility of the {@link L2_MAKE_IMMUTABLE} operation.
 * </p>
 *
 * @param <R> The kind of {@link L2Register} to be moved.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_MOVE <R extends L2Register>
extends L2Operation
{
	/**
	 * Construct an {@code L2_MOVE} operation.
	 *
	 * @param theNamedOperandTypes
	 *        An array of {@link L2NamedOperandType}s that describe this
	 *        particular L2Operation, allowing it to be specialized by register
	 *        type.
	 */
	@InnerAccess L2_MOVE (final L2NamedOperandType... theNamedOperandTypes)
	{
		super(theNamedOperandTypes);
	}

	/**
	 * Initialize the move operation for boxed values.
	 */
	public static final L2_MOVE<L2BoxedRegister> boxed = new L2_MOVE<>(
		READ_BOXED.is("source boxed"),
		WRITE_BOXED.is("destination boxed"));

	/**
	 * Initialize the move operation for int values.
	 */
	public static final L2_MOVE<L2IntRegister> unboxedInt = new L2_MOVE<>(
		READ_INT.is("source int"),
		WRITE_INT.is("destination int"));

	/**
	 * Initialize the move operation for float values.
	 */
	public static final L2_MOVE<L2FloatRegister> unboxedFloat = new L2_MOVE<>(
		READ_FLOAT.is("source float"),
		WRITE_FLOAT.is("destination float"));

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ReadOperand<R> source = instruction.operand(0);
		final L2WriteOperand<R> destination = instruction.operand(1);

		assert source.register() != destination.register();
		registerSet.removeConstantAt(destination.register());
		if (registerSet.hasTypeAt(source.register()))
		{
			registerSet.typeAtPut(
				destination.register(),
				registerSet.typeAt(source.register()),
				instruction);
		}
		else
		{
			registerSet.removeTypeAt(destination.register());
		}

		if (registerSet.hasConstantAt(source.register()))
		{
			registerSet.constantAtPut(
				destination.register(),
				registerSet.constantAt(source.register()),
				instruction);
		}
		registerSet.propagateMove(
			source.register(), destination.register(), instruction);
	}

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		assert this == instruction.operation();
		final L2ReadOperand<R> source = instruction.operand(0);
		final L2WriteOperand<R> destination = instruction.operand(1);

		// Ensure the new write ends up in the same synonym as the source.
		source.instructionWasAdded(instruction, manifest);
		destination.instructionWasAddedForMove(
			instruction, source.semanticValue(), manifest);
	}

	@Override
	public L2ReadBoxedOperand extractFunctionOuter (
		final L2Instruction instruction,
		final L2ReadBoxedOperand functionRegister,
		final int outerIndex,
		final A_Type outerType,
		final L2Generator generator)
	{
		assert this == instruction.operation() && this == boxed;
		final L2ReadBoxedOperand sourceRead = instruction.operand(0);
//		final L2WriteBoxedOperand destinationWrite = instruction.operand(1);

		// Trace it back toward the actual function creation.
		final L2Instruction earlierInstruction =
			sourceRead.definitionSkippingMoves(true);
		return earlierInstruction.operation().extractFunctionOuter(
			earlierInstruction,
			sourceRead,
			outerIndex,
			outerType,
			generator);
	}

	@Override
	public @Nullable A_RawFunction getConstantCodeFrom (
		final L2Instruction instruction)
	{
		assert instruction.operation() == boxed;
		final L2ReadBoxedOperand source = sourceOf(instruction);
		final L2Instruction producer = source.register().definition();
		return producer.operation().getConstantCodeFrom(producer);
	}

	@Override
	public boolean shouldEmit (final L2Instruction instruction)
	{
		assert instruction.operation() == this;
		final L2ReadOperand<R> sourceReg = instruction.operand(0);
		final L2WriteOperand<R> destinationReg = instruction.operand(1);

		return sourceReg.finalIndex() != destinationReg.finalIndex();
	}

	@Override
	public boolean isMove ()
	{
		return true;
	}

	/**
	 * Given an {@link L2Instruction} using this operation, extract the source
	 * {@link L2ReadOperand} that is moved by the instruction.
	 *
	 * @param instruction
	 *        The move instruction to examine.
	 * @param <RR>
	 *        The subtype of {@link L2ReadOperand} to return.  This is only
	 *        checked at runtime.
	 * @param <R>
	 *        The type of {@link L2Register} that is being read.
	 * @return The move's source {@link L2ReadOperand}.
	 */
	public static <
		RR extends L2ReadOperand<R>,
		R extends L2Register>
	RR sourceOf (
		final L2Instruction instruction)
	{
		assert instruction.operation() instanceof L2_MOVE;
		return instruction.operand(0);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadOperand<R> source = instruction.operand(0);
		final L2WriteOperand<R> destination = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destination.registerString());
		builder.append(" ← ");
		builder.append(source.registerString());
	}

	@Override
	public String toString ()
	{
		final String kind =
			(this == boxed)
				? "boxed"
				: (this == unboxedInt)
					? "int"
					: (this == unboxedFloat)
						? "float"
						: "unknown";
		return super.toString() + "(" + kind + ")";
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadOperand<R> source = instruction.operand(0);
		final L2WriteOperand<R> destination = instruction.operand(1);

		// :: destination = source;
		translator.load(method, source.register());
		translator.store(method, destination.register());
	}
}
