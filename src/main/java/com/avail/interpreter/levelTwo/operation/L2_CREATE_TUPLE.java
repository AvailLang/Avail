/*
 * L2_CREATE_TUPLE.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ObjectTupleDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

/**
 * Create a {@link TupleDescriptor tuple} from the {@linkplain AvailObject
 * objects} in the specified registers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_CREATE_TUPLE
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_TUPLE}.
	 */
	private L2_CREATE_TUPLE ()
	{
		super(
			READ_BOXED_VECTOR.is("elements"),
			WRITE_BOXED.is("tuple"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_CREATE_TUPLE instance = new L2_CREATE_TUPLE();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ReadBoxedVectorOperand values = instruction.operand(0);
		final L2WriteBoxedOperand tuple = instruction.operand(1);

		final int size = values.elements().size();
		final A_Type sizeRange = fromInt(size).kind();
		final List<A_Type> types = new ArrayList<>(size);
		for (final L2ReadBoxedOperand element: values.elements())
		{
			if (registerSet.hasTypeAt(element.register()))
			{
				types.add(registerSet.typeAt(element.register()));
			}
			else
			{
				types.add(ANY.o());
			}
		}
		final A_Type tupleType =
			tupleTypeForSizesTypesDefaultType(sizeRange,
				tupleFromList(types), bottom());
		tupleType.makeImmutable();
		registerSet.removeConstantAt(tuple.register());
		registerSet.typeAtPut(
			tuple.register(),
			tupleType,
			instruction);
		if (registerSet.allRegistersAreConstant(values.elements()))
		{
			final List<AvailObject> constants = new ArrayList<>(size);
			for (final L2ReadBoxedOperand element : values.elements())
			{
				constants.add(registerSet.constantAt(element.register()));
			}
			final A_Tuple newTuple = tupleFromList(constants);
			newTuple.makeImmutable();
			assert newTuple.isInstanceOf(tupleType);
			registerSet.typeAtPut(
				tuple.register(),
				instanceType(newTuple),
				instruction);
			registerSet.constantAtPut(
				tuple.register(), newTuple, instruction);
		}
	}

	/**
	 * Given an {@link L2Instruction} using this operation, extract the list of
	 * registers that supply the elements of the tuple.
	 *
	 * @param instruction
	 *        The tuple creation instruction to examine.
	 * @return The instruction's {@link List} of {@link L2ReadBoxedOperand}s
	 *         that supply the tuple elements.
	 */
	public static List<L2ReadBoxedOperand> tupleSourceRegistersOf (
		final L2Instruction instruction)
	{
		assert instruction.operation() == instance;
		final L2ReadBoxedVectorOperand vector = instruction.operand(0);
		return vector.elements();
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedVectorOperand values = instruction.operand(0);
		final L2WriteBoxedOperand tuple = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(tuple.registerString());
		builder.append(" ← ");
		builder.append(values.elements());
	}

	/**
	 * Generated code uses:
	 * <ul>
	 *     <li>{@link ObjectTupleDescriptor#tupleFromArray(
	 *         A_BasicObject...)}</li>
	 *     <li>{@link TupleDescriptor#emptyTuple()}</li>
	 *     <li>{@link ObjectTupleDescriptor#tuple(A_BasicObject)}</li>
	 *     <li>{@link ObjectTupleDescriptor#tuple(
	 *         A_BasicObject, A_BasicObject)}</li>
	 *     <li>{@link ObjectTupleDescriptor#tuple(
	 *         A_BasicObject, A_BasicObject, A_BasicObject)}</li>
	 *     <li>{@link ObjectTupleDescriptor#tuple(
	 *         A_BasicObject, A_BasicObject, A_BasicObject, A_BasicObject)}</li>
	 *     <li>{@link ObjectTupleDescriptor#tuple(
	 *         A_BasicObject,
	 *         A_BasicObject,
	 *         A_BasicObject,
	 *         A_BasicObject,
	 *         A_BasicObject)}</li>
	 * </ul>
	 */
	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedVectorOperand values = instruction.operand(0);
		final L2WriteBoxedOperand tuple = instruction.operand(1);

		final List<L2ReadBoxedOperand> elements = values.elements();
		final int size = elements.size();
		if (size <= 5)
		{
			// Special cases for small tuples
			if (size == 0)
			{
				// :: destination = theEmptyTupleLiteral;
				translator.literal(method, emptyTuple());
				translator.store(method, tuple.register());
				return;
			}
			// :: destination = TupleDescriptor.tuple(element1...elementN);
			for (int i = 0; i < size; i++)
			{
				translator.load(method, elements.get(i).register());
			}
			final Type[] callSignature = new Type[size];
			Arrays.fill(callSignature, getType(A_BasicObject.class));
			method.visitMethodInsn(
				INVOKESTATIC,
				getInternalName(ObjectTupleDescriptor.class),
				"tuple",
				getMethodDescriptor(
					getType(A_Tuple.class),
					callSignature),
				false);
			method.visitTypeInsn(CHECKCAST, getInternalName(AvailObject.class));
			translator.store(method, tuple.register());
			return;
		}
		// :: destination = TupleDescriptor.tupleFromArray(elements);
		translator.objectArray(method, elements, A_BasicObject.class);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(ObjectTupleDescriptor.class),
			"tupleFromArray",
			getMethodDescriptor(
				getType(A_Tuple.class),
				getType(A_BasicObject[].class)),
			false);
		method.visitTypeInsn(CHECKCAST, getInternalName(AvailObject.class));
		translator.store(method, tuple.register());
	}
}
