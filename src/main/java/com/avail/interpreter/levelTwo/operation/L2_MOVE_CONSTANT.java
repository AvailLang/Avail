/*
 * L2_MOVE_CONSTANT.java
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
import com.avail.descriptor.functions.A_Function;
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
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.values.L2SemanticValue;
import com.avail.utility.evaluation.Continuation3NotNull;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Move a constant {@link AvailObject} into a register.  Instances of this
 * operation are customized for different {@link RegisterKind}s.
 *
 * @param <C> The {@link L2Operand} that provides the constant value.
 * @param <R> The kind of {@link L2Register} to populate.
 * @param <WR> The kind of {@link L2WriteOperand} used to write to the register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_MOVE_CONSTANT <
	C extends L2Operand,
	R extends L2Register,
	WR extends L2WriteOperand<R>>
extends L2Operation
{
	/** A {@link Continuation3NotNull} to invoke to push the constant value. */
	private final Continuation3NotNull<JVMTranslator, MethodVisitor, C>
		pushConstant;

	/**
	 * Construct an {@code L2_MOVE_CONSTANT} operation.
	 *
	 * @param pushConstant
	 *        A {@link Continuation3NotNull} to invoke to generate JVM code to
	 *        push the constant value.
	 * @param theNamedOperandTypes
	 *        An array of {@link L2NamedOperandType}s that describe this
	 *        particular L2Operation, allowing it to be specialized by register
	 *        type.
	 */
	private L2_MOVE_CONSTANT (
		final Continuation3NotNull<JVMTranslator, MethodVisitor, C> pushConstant,
		final L2NamedOperandType... theNamedOperandTypes)
	{
		super(theNamedOperandTypes);
		this.pushConstant = pushConstant;
	}

	/**
	 * Initialize the move-constant operation for boxed values.
	 */
	public static final L2_MOVE_CONSTANT<
			L2ConstantOperand, L2BoxedRegister, L2WriteBoxedOperand>
		boxed = new L2_MOVE_CONSTANT<>(
			(translator, method, operand) ->
				translator.literal(method, operand.object),
			CONSTANT.is("constant"),
			WRITE_BOXED.is("destination boxed"));

	/**
	 * Initialize the move-constant operation for int values.
	 */
	public static final L2_MOVE_CONSTANT<
			L2IntImmediateOperand, L2IntRegister, L2WriteIntOperand>
		unboxedInt = new L2_MOVE_CONSTANT<>(
			(translator, method, operand) ->
				translator.literal(method, operand.value),
			INT_IMMEDIATE.is("constant int"),
			WRITE_INT.is("destination int"));

	/**
	 * Initialize the move-constant operation for float values.
	 */
	public static final L2_MOVE_CONSTANT<
			L2FloatImmediateOperand, L2FloatRegister, L2WriteFloatOperand>
		unboxedFloat = new L2_MOVE_CONSTANT<>(
			(translator, method, operand) ->
				translator.literal(method, operand.value),
			FLOAT_IMMEDIATE.is("constant float"),
			WRITE_FLOAT.is("destination float"));

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		assert this == instruction.operation();
		final C source = instruction.operand(0);
		final WR destination = instruction.operand(1);

		// Ensure the new write ends up in the same synonym as the source.
		source.instructionWasAdded(manifest);
		final L2SemanticValue semanticValue = destination.pickSemanticValue();
		if (manifest.hasSemanticValue(semanticValue))
		{
			// The constant semantic value exists, but for another register
			// kind.
			destination.instructionWasAddedForMove(semanticValue, manifest);
		}
		else
		{
			// The constant semantic value has not been encountered for any
			// register kinds yet.
			destination.instructionWasAdded(manifest);
		}
	}

	@Override
	public L2ReadBoxedOperand extractFunctionOuter (
		final L2Instruction instruction,
		final L2ReadBoxedOperand functionRegister,
		final int outerIndex,
		final A_Type outerType,
		final L2Generator generator)
	{
		// The exact function is known statically.
		assert this == instruction.operation() && this == boxed;
		final A_Function constantFunction = constantOf(instruction);
		return generator.boxedConstant(constantFunction.outerVarAt(outerIndex));
	}

	/**
	 * Given an {@link L2Instruction} using the boxed form of this operation,
	 * extract the boxed constant that is moved by the instruction.
	 *
	 * @param instruction
	 *        The boxed-constant-moving instruction to examine.
	 * @return The constant {@link AvailObject} that is moved by the
	 *         instruction.
	 */
	public static AvailObject constantOf (final L2Instruction instruction)
	{
		assert instruction.operation() == boxed;
		final L2ConstantOperand constant = instruction.operand(0);
		return constant.object;
	}

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final C constant = instruction.operand(0);
		final WR destination = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(' ');
		destination.appendWithWarningsTo(builder, 0, warningStyleChange);
		builder.append(" ← ");
		builder.append(constant);
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
		final C constantOperand = instruction.operand(0);
		final WR destinationWriter = instruction.operand(1);

		// :: destination = constant;
		pushConstant.value(translator, method, constantOperand);
		translator.store(method, destinationWriter.register());
	}
}
