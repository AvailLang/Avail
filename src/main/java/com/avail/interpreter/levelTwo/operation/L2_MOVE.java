/*
 * L2_MOVE.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.functions.A_RawFunction;
import com.avail.descriptor.types.A_Type;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2NamedOperandType;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2BoxedRegister;
import com.avail.interpreter.levelTwo.register.L2FloatRegister;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.utility.Casts;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Set;
import java.util.function.Consumer;

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
 * @param <RR> The kind of {@link L2ReadOperand} to use for reading.
 * @param <WR> The kind of {@link L2WriteOperand} to use for writing.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class L2_MOVE <
	R extends L2Register,
	RR extends L2ReadOperand<R>,
	WR extends L2WriteOperand<R>>
extends L2Operation
{
	/**
	 * A map from the {@link RegisterKind}s to the appropriate {@code L2_MOVE}
	 * operations.
	 */
	private static final EnumMap<RegisterKind, L2_MOVE<?, ?, ?>> movesByKind =
		new EnumMap<>(RegisterKind.class);

	/**
	 * Construct an {@code L2_MOVE} operation.
	 *
	 * @param kind
	 *        The {@link RegisterKind} serviced by this operation.
	 * @param theNamedOperandTypes
	 *        An array of {@link L2NamedOperandType}s that describe this
	 *        particular L2Operation, allowing it to be specialized by register
	 *        type.
	 */
	L2_MOVE (
		final RegisterKind kind,
		final L2NamedOperandType... theNamedOperandTypes)
	{
		super("MOVE(" + kind.kindName + ")", theNamedOperandTypes);
		this.kind = kind;
		assert movesByKind.get(kind) == null;
		//noinspection ThisEscapedInObjectConstruction
		movesByKind.put(kind, this);
	}

	/**
	 * Initialize the move operation for boxed values.
	 */
	public static final
	L2_MOVE<L2BoxedRegister, L2ReadBoxedOperand, L2WriteBoxedOperand>
		boxed = new L2_MOVE<
				L2BoxedRegister, L2ReadBoxedOperand, L2WriteBoxedOperand>(
			RegisterKind.BOXED,
			READ_BOXED.is("source boxed"),
			WRITE_BOXED.is("destination boxed"))
	{
		@Override
		public L2WriteBoxedOperand createWrite (
			final L2Generator generator,
			final L2SemanticValue semanticValue,
			final TypeRestriction restriction)
		{
			return generator.boxedWrite(semanticValue, restriction);
		}
	};

	/**
	 * Initialize the move operation for int values.
	 */
	public static final
	L2_MOVE<L2IntRegister, L2ReadIntOperand, L2WriteIntOperand>
		unboxedInt = new L2_MOVE<
				L2IntRegister, L2ReadIntOperand, L2WriteIntOperand>(
			RegisterKind.INTEGER,
			READ_INT.is("source int"),
			WRITE_INT.is("destination int"))
	{
		@Override
		public L2WriteIntOperand createWrite (
			final L2Generator generator,
			final L2SemanticValue semanticValue,
			final TypeRestriction restriction)
		{
			return generator.intWrite(semanticValue, restriction);
		}
	};

	/**
	 * Initialize the move operation for float values.
	 */
	public static final
	L2_MOVE<L2FloatRegister, L2ReadFloatOperand, L2WriteFloatOperand>
		unboxedFloat = new L2_MOVE<
				L2FloatRegister, L2ReadFloatOperand, L2WriteFloatOperand>(
			RegisterKind.FLOAT,
			READ_FLOAT.is("source float"),
			WRITE_FLOAT.is("destination float"))
	{
		@Override
		public L2WriteFloatOperand createWrite (
			final L2Generator generator,
			final L2SemanticValue semanticValue,
			final TypeRestriction restriction)
		{
			return generator.floatWrite(semanticValue, restriction);
		}
	};

	/**
	 * Synthesize an {@link L2WriteOperand} of the appropriately strengthened
	 * type {@link WR}.
	 *
	 * @param generator
	 *        The {@link L2Generator} used for creating the write.
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to populate.
	 * @param restriction
	 *        The {@link TypeRestriction} that the stored values will satisfy.
	 * @return A new {@link L2WriteOperand} of the appropriate type {@link WR}.
	 */
	public abstract WR createWrite (
		final L2Generator generator,
		final L2SemanticValue semanticValue,
		final TypeRestriction restriction);

	/**
	 * Answer an {@code L2_MOVE} suitable for transferring data of the given
	 * {@link RegisterKind}.
	 *
	 * @param <R>
	 *        The subtype of {@link L2Register} to use.
	 * @param <RR>
	 *        The subtype of {@link L2ReadOperand} to use.
	 * @param <WR>
	 *        The subtype of {@link L2WriteOperand} to use.
	 * @param registerKind
	 *        The {@link RegisterKind} to be transferred by the move.
	 * @return The requested {@code L2_MOVE} operation.
	 */
	public static <
		R extends L2Register,
		RR extends L2ReadOperand<R>,
		WR extends L2WriteOperand<R>>
	L2_MOVE<R, RR, WR> moveByKind (final RegisterKind registerKind)
	{
		return Casts.<L2_MOVE<?, ?, ?>, L2_MOVE<R, RR, WR>>cast(
			movesByKind.get(registerKind));
	}

	/** The kind of data moved by this operation. */
	public final RegisterKind kind;

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
		source.instructionWasAdded(manifest);
		destination.instructionWasAddedForMove(
			source.semanticValue(), manifest);
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
		final RR source = sourceOf(instruction);
		final L2Instruction producer = source.definition().instruction();
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
	 * Given an {@link L2Instruction} using an operation of this class, extract
	 * the source {@link L2ReadOperand} that is moved by the instruction.
	 *
	 * @param instruction
	 *        The move instruction to examine.
	 * @return The move's source {@link L2ReadOperand}.
	 */
	public RR sourceOf (
		final L2Instruction instruction)
	{
		assert instruction.operation() instanceof L2_MOVE;
		return instruction.operand(0);
	}

	/**
	 * Given an {@link L2Instruction} using this operation, extract the source
	 * {@link L2ReadOperand} that is moved by the instruction.
	 *
	 * @param instruction
	 *        The move instruction to examine.
	 * @return The move's source {@link L2WriteOperand}.
	 */
	public WR destinationOf (
		final L2Instruction instruction)
	{
		assert instruction.operation() == this;
		return instruction.operand(1);
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ReadOperand<R> source = instruction.operand(0);
		final L2WriteOperand<R> destination = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(' ');
		destination.appendWithWarningsTo(builder, 0, warningStyleChange);
		builder.append(" ← ");
		source.appendWithWarningsTo(builder, 0, warningStyleChange);
	}

	@Override
	public String toString ()
	{
		return name();
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
