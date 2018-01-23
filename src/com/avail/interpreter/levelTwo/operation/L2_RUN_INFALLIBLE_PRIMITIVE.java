/*
 * L2_RUN_INFALLIBLE_PRIMITIVE.java
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

import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

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
public class L2_RUN_INFALLIBLE_PRIMITIVE
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_RUN_INFALLIBLE_PRIMITIVE().init(
			CONSTANT.is("raw function"),  // Used for inlining/reoptimization.
			PRIMITIVE.is("primitive to run"),
			READ_VECTOR.is("arguments"),
			WRITE_POINTER.is("primitive result"));

	@Override
	protected void propagateTypes (
		@NotNull final L2Instruction instruction,
		@NotNull final RegisterSet registerSet,
		final L2Translator translator)
	{
		final A_RawFunction rawFunction = instruction.constantAt(0);
		final Primitive primitive = instruction.primitiveAt(1);
		final List<L2ReadPointerOperand> argsVector =
			instruction.readVectorRegisterAt(2);
		final L2WritePointerOperand resultReg =
			instruction.writeObjectRegisterAt(3);

		final List<A_Type> argTypes = new ArrayList<>(argsVector.size());
		for (final L2ReadPointerOperand arg : argsVector)
		{
			assert registerSet.hasTypeAt(arg.register());
			argTypes.add(registerSet.typeAt(arg.register()));
		}
		// We can at least believe what the primitive itself says it returns.
		final A_Type guaranteedType =
			primitive.returnTypeGuaranteedByVM(rawFunction, argTypes);
		registerSet.removeTypeAt(resultReg.register());
		registerSet.removeConstantAt(resultReg.register());
		if (!guaranteedType.isBottom())
		{
			registerSet.typeAtPut(
				resultReg.register(), guaranteedType, instruction);
		}
	}

	@Override
	public boolean hasSideEffect (final L2Instruction instruction)
	{
		// It depends on the primitive.
		assert instruction.operation == this;
		final L2PrimitiveOperand primitiveOperand =
			(L2PrimitiveOperand) instruction.operands[1];
		final Primitive primitive = primitiveOperand.primitive;
		return primitive.hasFlag(Flag.HasSideEffect)
			|| primitive.hasFlag(Flag.CatchException)
			|| primitive.hasFlag(Flag.Invokes)
			|| primitive.hasFlag(Flag.CanSwitchContinuations)
			|| primitive.hasFlag(Flag.Unknown);
	}

	@Override
	public L2WritePointerOperand primitiveResultRegister (
		final L2Instruction instruction)
	{
		assert instruction.operation == instance;
		return instruction.writeObjectRegisterAt(3);
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
		assert instruction.operation == instance;
		return instruction.primitiveAt(1);
	}

	/**
	 * Extract the {@link List} of {@link L2ReadPointerOperand}s that supply the
	 * arguments to the primitive.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} from which to extract the list of
	 *        arguments.
	 * @return The {@link List} of {@link L2ReadPointerOperand}s that supply
	 *         arguments to the primitive.
	 */
	public static List<L2ReadPointerOperand> argsOf (
		final L2Instruction instruction)
	{
		assert instruction.operation == instance;
		return instruction.readVectorRegisterAt(2);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final A_RawFunction rawFunction = instruction.constantAt(0);
		final Primitive primitive = instruction.primitiveAt(1);
		final List<L2ReadPointerOperand> argumentRegs =
			instruction.readVectorRegisterAt(2);
		final L2ObjectRegister resultReg =
			instruction.writeObjectRegisterAt(3).register();

		// :: argsBuffer = interpreter.argsBuffer;
		translator.loadInterpreter(method);
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"argsBuffer",
			getDescriptor(List.class));
		// :: argsBuffer.clear();
		if (!argumentRegs.isEmpty())
		{
			method.visitInsn(DUP);
		}
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(List.class),
			"clear",
			getMethodDescriptor(VOID_TYPE),
			true);
		for (int i = 0, limit = argumentRegs.size(); i < limit; i++)
		{
			// :: argsBuffer.add(«argument[i]»);
			if (i < limit - 1)
			{
				method.visitInsn(DUP);
			}
			translator.load(method, argumentRegs.get(i).register());
			method.visitMethodInsn(
				INVOKEINTERFACE,
				getInternalName(List.class),
				"add",
				getMethodDescriptor(BOOLEAN_TYPE, getType(Object.class)),
				true);
			method.visitInsn(POP);
		}
		// :: res = interpreter.attemptPrimitive(primitive, false);
		translator.loadInterpreter(method);
		method.visitFieldInsn(
			GETSTATIC,
			getInternalName(primitive.getClass()),
			"instance",
			getDescriptor(Primitive.class));
		// Only primitive P_PushConstant is infallible and yet needs the
		// function, and it's always folded.  In the case that
		// P_PushConstant is known to produce the wrong type at some site
		// (potentially dead code due to inlining of an unreachable branch),
		// it is converted to an explicit failure instruction.  Thus we could
		// null the function here and restore it after the call, for additional
		// safety.  Note also that primitives which have to suspend the fiber
		// (to perform a level one unsafe operation and then switch back to
		// level one safe mode) must *never* be inlined, otherwise they couldn't
		// reach a safe inter-nybblecode position.
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"attemptPrimitive",
			getMethodDescriptor(
				getType(Result.class),
				getType(Primitive.class)),
			false);
		// If the infallible primitive definitely switches continuations, then
		// return null to force the context switch.
		if (primitive.hasFlag(Flag.AlwaysSwitchesContinuation))
		{
			// :: return null;
			method.visitInsn(POP);
			method.visitInsn(ACONST_NULL);
			method.visitInsn(ARETURN);
		}
		// If the infallible primitive cannot switch continuations, then by
		// definition it can only succeed.
		else if (!primitive.hasFlag(Flag.CanSwitchContinuations))
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
			translator.store(method, resultReg);
		}
		// Otherwise, determine whether the infallible primitive switched
		// continuations and react accordingly.
		else
		{
			// :: if (res == Result.SUCCESS) {
			final Label switchedContinuations = new Label();
			method.visitFieldInsn(
				GETSTATIC,
				getInternalName(Result.class),
				"SUCCESS",
				getDescriptor(Result.class));
			method.visitJumpInsn(IF_ACMPNE, switchedContinuations);
			// ::    result = interpreter.latestResult();
			translator.loadInterpreter(method);
			method.visitMethodInsn(
				INVOKEVIRTUAL,
				getInternalName(Interpreter.class),
				"latestResult",
				getMethodDescriptor(getType(AvailObject.class)),
				false);
			translator.store(method, resultReg);
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
