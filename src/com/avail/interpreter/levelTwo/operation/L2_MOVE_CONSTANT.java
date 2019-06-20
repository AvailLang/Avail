/*
 * L2_MOVE_CONSTANT.java
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
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2NamedOperandType;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2FloatImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteOperand;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.utility.evaluation.Continuation3NotNull;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.utility.Casts.cast;

/**
 * Move a constant {@link AvailObject} into a register.  Instances of this
 * operation are customized for different {@link RegisterKind}s.
 *
 * @param <P> The {@link L2Operand} that provides the constant value.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2_MOVE_CONSTANT <P extends L2Operand>
extends L2Operation
{
	/** A {@link Continuation3NotNull} to invoke to push the constant value. */
	final Continuation3NotNull<JVMTranslator, MethodVisitor, P> pushConstant;

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
	@InnerAccess
	L2_MOVE_CONSTANT (
		final Continuation3NotNull<JVMTranslator, MethodVisitor, P> pushConstant,
		final L2NamedOperandType... theNamedOperandTypes)
	{
		super(theNamedOperandTypes);
		this.pushConstant = pushConstant;
	}

	/**
	 * Initialize the move-constant operation for boxed values.
	 */
	public static final L2_MOVE_CONSTANT<L2ConstantOperand>
		boxed = new L2_MOVE_CONSTANT<>(
			(translator, method, operand) ->
				translator.literal(method, operand.object),
			CONSTANT.is("constant"),
			WRITE_BOXED.is("destination boxed"));

	/**
	 * Initialize the move-constant operation for int values.
	 */
	public static final L2_MOVE_CONSTANT<L2IntImmediateOperand> unboxedInt =
		new L2_MOVE_CONSTANT<>(
			(translator, method, operand) ->
				translator.literal(method, operand.value),
			INT_IMMEDIATE.is("constant int"),
			WRITE_INT.is("destination int"));

	/**
	 * Initialize the move-constant operation for float values.
	 */
	public static final L2_MOVE_CONSTANT<L2FloatImmediateOperand> unboxedFloat =
		new L2_MOVE_CONSTANT<>(
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
		final P source = cast(instruction.operand(0));
		final L2WriteOperand<?> destination = cast(instruction.operand(1));

		// Ensure the new write ends up in the same synonym as the source.
		source.instructionWasAdded(instruction, manifest);
		if (manifest.hasSemanticValue(destination.semanticValue()))
		{
			// The constant semantic value exists, but for another register
			// kind.
			destination.instructionWasAddedForMove(
				instruction, destination.semanticValue(), manifest);
		}
		else
		{
			// The constant semantic value has not been encountered for any
			// register kinds yet.
			destination.instructionWasAdded(instruction, manifest);
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
		final A_Function constantFunction = instruction.constantAt(0);
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
		return instruction.constantAt(0);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2Operand constant = instruction.operand(0);
		final L2Register destinationReg =
			instruction.writeRegisterAt(1).register();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destinationReg);
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
		final P constantOperand = cast(instruction.operand(0));
		final L2WriteOperand<?> destinationWriter =
			cast(instruction.operand(1));

		// :: destination = constant;
		pushConstant.value(translator, method, constantOperand);
		translator.store(method, destinationWriter.register());
	}
}
