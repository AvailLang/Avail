/*
 * L2_RUN_INFALLIBLE_PRIMITIVE.java
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

import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.CONSTANT;
import static com.avail.interpreter.levelTwo.L2OperandType.PRIMITIVE;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.DUP2;
import static org.objectweb.asm.Opcodes.DUP2_X2;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IF_ACMPNE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.VOID_TYPE;
import static org.objectweb.asm.Type.getDescriptor;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

/**
 * Execute a primitive with the provided arguments, writing the result into
 * the specified register.  The primitive must not fail.  Don't check the result
 * type, since the VM has already guaranteed it is correct.
 *
 * <p>
 * Unlike for {@link L2_INVOKE} and related operations, we do not provide
 * the calling continuation here.  That's because by inlining the primitive
 * attempt we have avoided (or at worst postponed) construction of the
 * continuation that reifies the current function execution.  This is a Good
 * Thing, performance-wise.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_RUN_INFALLIBLE_PRIMITIVE
extends L2Operation
{
	/**
	 * Construct an {@code L2_RUN_INFALLIBLE_PRIMITIVE}.
	 */
	private L2_RUN_INFALLIBLE_PRIMITIVE ()
	{
		super(
			CONSTANT.is("raw function"),  // Used for inlining/reoptimization.
			PRIMITIVE.is("primitive to run"),
			READ_BOXED_VECTOR.is("arguments"),
			WRITE_BOXED.is("primitive result"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_RUN_INFALLIBLE_PRIMITIVE instance =
		new L2_RUN_INFALLIBLE_PRIMITIVE();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ConstantOperand rawFunction = instruction.operand(0);
		final L2PrimitiveOperand primitive = instruction.operand(1);
		final L2ReadBoxedVectorOperand arguments = instruction.operand(2);
		final L2WriteBoxedOperand result = instruction.operand(3);

		final List<A_Type> argTypes =
			new ArrayList<>(arguments.elements().size());
		for (final L2ReadBoxedOperand arg : arguments.elements())
		{
			assert registerSet.hasTypeAt(arg.register());
			argTypes.add(registerSet.typeAt(arg.register()));
		}
		// We can at least believe what the primitive itself says it returns.
		final A_Type guaranteedType =
			primitive.primitive.returnTypeGuaranteedByVM(
				rawFunction.object, argTypes);
		registerSet.removeTypeAt(result.register());
		registerSet.removeConstantAt(result.register());
		if (!guaranteedType.isBottom())
		{
			registerSet.typeAtPut(
				result.register(), guaranteedType, instruction);
		}
	}

	@Override
	public boolean hasSideEffect (final L2Instruction instruction)
	{
		// It depends on the primitive.
		assert instruction.operation() == this;
//		final L2ConstantOperand rawFunction = instruction.operand(0);
		final L2PrimitiveOperand primitive = instruction.operand(1);
//		final L2ReadBoxedVectorOperand arguments = instruction.operand(2);
//		final L2WriteBoxedOperand result = instruction.operand(3);


		final Primitive prim = primitive.primitive;
		return prim.hasFlag(Flag.HasSideEffect)
			|| prim.hasFlag(Flag.CatchException)
			|| prim.hasFlag(Flag.Invokes)
			|| prim.hasFlag(Flag.CanSwitchContinuations)
			|| prim.hasFlag(Flag.Unknown);
	}

	@Override
	public L2WriteBoxedOperand primitiveResultRegister (
		final L2Instruction instruction)
	{
		assert instruction.operation() == instance;
		return instruction.operand(3);
	}

	/**
	 * Extract the {@link Primitive} from the provided instruction.
	 *
 	 * @param instruction
	 *        The {@link L2Instruction} from which to extract the {@link
	 *        Primitive}.
	 * @return The {@link Primitive} invoked by this instruction.
	 */
	public static Primitive primitiveOf (
		final L2Instruction instruction)
	{
		assert instruction.operation() == instance;
		final L2PrimitiveOperand primitive = instruction.operand(1);
		return primitive.primitive;
	}

	/**
	 * Extract the {@link List} of {@link L2ReadBoxedOperand}s that supply the
	 * arguments to the primitive.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} from which to extract the list of
	 *        arguments.
	 * @return The {@link List} of {@link L2ReadBoxedOperand}s that supply
	 *         arguments to the primitive.
	 */
	public static List<L2ReadBoxedOperand> argsOf (
		final L2Instruction instruction)
	{
		assert instruction.operation() == instance;
		final L2ReadBoxedVectorOperand vector = instruction.operand(2);
		return vector.elements();
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
//		final L2ConstantOperand rawFunction = instruction.operand(0);
		final L2PrimitiveOperand primitive = instruction.operand(1);
		final L2ReadBoxedVectorOperand arguments = instruction.operand(2);
		final L2WriteBoxedOperand result = instruction.operand(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(result.registerString());
		builder.append(" ← ");
		builder.append(primitive);
		builder.append('(');
		builder.append(arguments.elements());
		builder.append(')');
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
//		final L2ConstantOperand rawFunction = instruction.operand(0);
		final L2PrimitiveOperand primitive = instruction.operand(1);
		final L2ReadBoxedVectorOperand arguments = instruction.operand(2);
		final L2WriteBoxedOperand result = instruction.operand(3);

		// :: argsBuffer = interpreter.argsBuffer;
		translator.loadInterpreter(method);
		// [interp]
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"argsBuffer",
			getDescriptor(List.class));
		// [argsBuffer]
		// :: argsBuffer.clear();
		if (!arguments.elements().isEmpty())
		{
			method.visitInsn(DUP);
		}
		// [argsBuffer[, argsBuffer if #args > 0]]
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(List.class),
			"clear",
			getMethodDescriptor(VOID_TYPE),
			true);
		// [argsBuffer if #args > 0]
		for (int i = 0, limit = arguments.elements().size(); i < limit; i++)
		{
			// :: argsBuffer.add(«argument[i]»);
			if (i < limit - 1)
			{
				method.visitInsn(DUP);
			}
			translator.load(method, arguments.elements().get(i).register());
			method.visitMethodInsn(
				INVOKEINTERFACE,
				getInternalName(List.class),
				"add",
				getMethodDescriptor(BOOLEAN_TYPE, getType(Object.class)),
				true);
			method.visitInsn(POP);
		}
		// []

		translator.loadInterpreter(method);
		// [interp]
		translator.literal(method, primitive.primitive);
		// [interp, prim]
		method.visitInsn(DUP2);
		// [interp, prim, interp, prim]
		method.visitInsn(DUP2);
		// [interp, prim, interp, prim, interp, prim]
		// :: long timeBefore = beforeAttemptPrimitive(primitive);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"beforeAttemptPrimitive",
			getMethodDescriptor(
				getType(Long.TYPE),
				getType(Primitive.class)),
			false);
		// [interp, prim, interp, prim, timeBeforeLong]
		method.visitInsn(DUP2_X2);  // Form 2: v3,v2,v1x2 -> v1x2,v3,v2,v1x2
		// [interp, prim, timeBeforeLong, interp, prim, timeBeforeLong]
		method.visitInsn(POP2);  // Form 2: v1x2 -> empty
		// [interp, prim, timeBeforeLong, interp, prim]
		method.visitInsn(SWAP);
		// [interp, prim, timeBeforeLong, prim, interp]
		// :: Result success = primitive.attempt(interpreter)
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(primitive.primitive.getClass()),
			"attempt",
			getMethodDescriptor(
				getType(Result.class),
				getType(Interpreter.class)),
			false);
		// [interp, prim, timeBeforeLong, success]

		// :: afterAttemptPrimitive(primitive, timeBeforeLong, success);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"afterAttemptPrimitive",
			getMethodDescriptor(
				getType(Result.class),
				getType(Primitive.class),
				getType(Long.TYPE),
				getType(Result.class)),
			false);
		// [success] (returned as a nicety by afterAttemptPrimitive)

		// If the infallible primitive definitely switches continuations, then
		// return null to force the context switch.
		if (primitive.primitive.hasFlag(Flag.AlwaysSwitchesContinuation))
		{
			// :: return null;
			method.visitInsn(POP);
			method.visitInsn(ACONST_NULL);
			method.visitInsn(ARETURN);
		}
		// If the infallible primitive cannot switch continuations, then by
		// definition it can only succeed.
		else if (!primitive.primitive.hasFlag(Flag.CanSwitchContinuations))
		{
			// :: result = interpreter.latestResult();
			method.visitInsn(POP);
			translator.loadInterpreter(method);
			method.visitMethodInsn(
				INVOKEVIRTUAL,
				getInternalName(Interpreter.class),
				"latestResult",
				getMethodDescriptor(getType(AvailObject.class)),
				false);
			translator.store(method, result.register());
		}
		// Otherwise, determine whether the infallible primitive switched
		// continuations and react accordingly.
		else
		{
			// :: if (res == Result.SUCCESS) {
			method.visitFieldInsn(
				GETSTATIC,
				getInternalName(Result.class),
				"SUCCESS",
				getDescriptor(Result.class));
			final Label switchedContinuations = new Label();
			method.visitJumpInsn(IF_ACMPNE, switchedContinuations);
			// ::    result = interpreter.latestResult();
			translator.loadInterpreter(method);
			method.visitMethodInsn(
				INVOKEVIRTUAL,
				getInternalName(Interpreter.class),
				"latestResult",
				getMethodDescriptor(getType(AvailObject.class)),
				false);
			translator.store(method, result.register());
			// ::    goto success;
			final Label success = new Label();
			method.visitJumpInsn(GOTO, success);
			// :: } else {
			method.visitLabel(switchedContinuations);
			// We switched continuations, so we need to return control to the
			// caller in order to honor the switch.
			// ::    return null;
			method.visitInsn(ACONST_NULL);
			method.visitInsn(ARETURN);
			// :: }
			method.visitLabel(success);
		}
	}
}
