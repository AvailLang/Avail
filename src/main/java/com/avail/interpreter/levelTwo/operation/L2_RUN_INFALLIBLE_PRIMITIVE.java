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
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_ARGUMENTS;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_CONTINUATION;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_FUNCTION;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.GLOBAL_STATE;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.LATEST_RETURN_VALUE;
import com.avail.interpreter.levelTwo.ReadsHiddenVariable;
import com.avail.interpreter.levelTwo.WritesHiddenVariable;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.levelTwo.L2OperandType.*;

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
@SuppressWarnings("AbstractClassWithoutAbstractMethods")
public abstract class L2_RUN_INFALLIBLE_PRIMITIVE
extends L2Operation
{
	/** The subclass for primitives that have no global dependency. */
	@WritesHiddenVariable({
		CURRENT_CONTINUATION.class,
		CURRENT_FUNCTION.class,
		CURRENT_ARGUMENTS.class,
		LATEST_RETURN_VALUE.class
	})
	private static class L2_RUN_INFALLIBLE_PRIMITIVE_no_dependency
	extends L2_RUN_INFALLIBLE_PRIMITIVE { }

	/** The subclass for primitives that have global read dependency. */
	@ReadsHiddenVariable(GLOBAL_STATE.class)
	@WritesHiddenVariable({
		CURRENT_CONTINUATION.class,
		CURRENT_FUNCTION.class,
		CURRENT_ARGUMENTS.class,
		LATEST_RETURN_VALUE.class
	})
	private static class L2_RUN_INFALLIBLE_PRIMITIVE_read_dependency
	extends L2_RUN_INFALLIBLE_PRIMITIVE { }

	/** The subclass for primitives that have global write dependency. */
	@WritesHiddenVariable({
		CURRENT_CONTINUATION.class,
		CURRENT_FUNCTION.class,
		CURRENT_ARGUMENTS.class,
		LATEST_RETURN_VALUE.class,
		GLOBAL_STATE.class
	})
	private static class L2_RUN_INFALLIBLE_PRIMITIVE_write_dependency
	extends L2_RUN_INFALLIBLE_PRIMITIVE { }

	/** The subclass for primitives that have global read/write dependency. */
	@ReadsHiddenVariable(GLOBAL_STATE.class)
	@WritesHiddenVariable({
		CURRENT_CONTINUATION.class,
		CURRENT_FUNCTION.class,
		CURRENT_ARGUMENTS.class,
		LATEST_RETURN_VALUE.class,
		GLOBAL_STATE.class
	})
	private static class L2_RUN_INFALLIBLE_PRIMITIVE_readwrite_dependency
	extends L2_RUN_INFALLIBLE_PRIMITIVE { }

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

	/** An instance for no global dependencies. */
	@SuppressWarnings("SyntheticAccessorCall")
	private static final L2_RUN_INFALLIBLE_PRIMITIVE noDependency =
		new L2_RUN_INFALLIBLE_PRIMITIVE_no_dependency();

	/** An instance for global read dependencies. */
	@SuppressWarnings("SyntheticAccessorCall")
	private static final L2_RUN_INFALLIBLE_PRIMITIVE readDependency =
		new L2_RUN_INFALLIBLE_PRIMITIVE_read_dependency();

	/** An instance for global write dependencies. */
	@SuppressWarnings("SyntheticAccessorCall")
	private static final L2_RUN_INFALLIBLE_PRIMITIVE writeDependency =
		new L2_RUN_INFALLIBLE_PRIMITIVE_write_dependency();

	/** An instance for global read/write dependencies. */
	@SuppressWarnings("SyntheticAccessorCall")
	private static final L2_RUN_INFALLIBLE_PRIMITIVE readWriteDependency =
		new L2_RUN_INFALLIBLE_PRIMITIVE_readwrite_dependency();

	/**
	 * Select an appropriate variant of the operation for the supplied
	 * {@link Primitive}, based on its global interference declarations.
	 *
	 * @param primitive The primitive that this operation is for.
	 * @return A suitable {@code L2_RUN_INFALLIBLE_PRIMITIVE} instance.
	 */
	public static L2_RUN_INFALLIBLE_PRIMITIVE forPrimitive(
		final Primitive primitive)
	{
		// Until we have all primitives annotated with global read/write flags,
		// pay attention to other flags that we expect to prevent commutation
		// of invocations.
		if (primitive.hasFlag(HasSideEffect) || primitive.hasFlag(Unknown))
		{
			return readWriteDependency;
		}
		final boolean read = primitive.hasFlag(ReadsFromHiddenGlobalState);
		final boolean write = primitive.hasFlag(WritesToHiddenGlobalState);
		return read
			? write
				? readWriteDependency
				: readDependency
			: write
				? writeDependency
				: noDependency;
	}


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
		return prim.hasFlag(HasSideEffect)
			|| prim.hasFlag(CatchException)
			|| prim.hasFlag(Invokes)
			|| prim.hasFlag(CanSwitchContinuations)
			|| prim.hasFlag(ReadsFromHiddenGlobalState)
			|| prim.hasFlag(WritesToHiddenGlobalState)
			|| prim.hasFlag(Unknown);
	}

	@Override
	public L2WriteBoxedOperand primitiveResultRegister (
		final L2Instruction instruction)
	{
		assert instruction.operation() == this;
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
		assert instruction.operation() instanceof L2_RUN_INFALLIBLE_PRIMITIVE;
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
		assert instruction.operation() instanceof L2_RUN_INFALLIBLE_PRIMITIVE;
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

		primitive.primitive.generateJvmCode(
			translator, method, arguments, result);
	}
}
