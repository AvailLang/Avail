/*
 * L2_EXPLODE_TUPLE.java
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
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_VECTOR;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Extract all elements from a known-length {@link TupleDescriptor tuple} into
 * a vector of registers.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2_EXPLODE_TUPLE
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_EXPLODE_TUPLE().init(
			READ_POINTER.is("tuple"),
			WRITE_VECTOR.is("elements"));

	@Override
	protected void propagateTypes (
		@NotNull final L2Instruction instruction,
		@NotNull final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ReadPointerOperand tupleReg =
			instruction.readObjectRegisterAt(0);
		final List<L2WritePointerOperand> elements =
			instruction.writeVectorRegisterAt(1);

		final A_Type tupleType = tupleReg.type();
		// Make all the contained types immutable.
		tupleType.makeImmutable();
		final int tupleSize = tupleType.sizeRange().lowerBound().extractInt();
		assert tupleSize == elements.size();
		for (int i = 1; i <= tupleSize; i++)
		{
			registerSet.typeAtPut(
				elements.get(i - 1).register(),
				tupleType.typeAtIndex(i),
				instruction);
		}
	}

	/**
	 * Given a register that will hold a tuple, check that the tuple has the
	 * number of elements and statically satisfies the corresponding provided
	 * type constraints.  If so, generate code and answer a list of register
	 * reads corresponding to the elements of the tuple; otherwise, generate no
	 * code and answer null.
	 *
	 * <p>Depending on the source of the tuple, this may cause the creation of
	 * the tuple to be entirely elided.</p>
	 *
	 * @param tupleReg
	 *        The {@link L2ObjectRegister} containing the tuple.
	 * @param requiredTypes
	 *        The required {@linkplain A_Type types} against which to check the
	 *        tuple's own type.
	 * @param translator
	 *        The {@link L1Translator}.
	 */
	public static @Nullable List<L2ReadPointerOperand> explodeTupleIfPossible (
		final L2ReadPointerOperand tupleReg,
		final List<A_Type> requiredTypes,
		final L1Translator translator)
	{
		// First see if there's enough type information available about the
		// tuple.
		final A_Type tupleType = tupleReg.type();
		final A_Type tupleTypeSizes = tupleType.sizeRange();
		if (!tupleTypeSizes.upperBound().isInt()
			|| !tupleTypeSizes.lowerBound().equals(tupleTypeSizes.upperBound()))
		{
			// The exact tuple size is not known.  Give up.
			return null;
		}
		final int tupleSize = tupleTypeSizes.upperBound().extractInt();
		if (tupleSize != requiredTypes.size())
		{
			// The tuple is the wrong size.
			return null;
		}

		// Check the tuple elements for type safety.
		for (int i = 1; i <= tupleSize; i++)
		{
			if (!tupleType.typeAtIndex(i).isInstanceOf(
				requiredTypes.get(i - 1)))
			{
				// This tuple element's type isn't strong enough.
				return null;
			}
		}

		// Check the tuple element types against the required types.
		for (int i = 1; i <= tupleSize; i++)
		{
			if (!tupleType.typeAtIndex(i).isInstanceOf(
				requiredTypes.get(i - 1)))
			{
				// This tuple element's type isn't strong enough.  Give up.
				return null;
			}
		}

		// At this point we know the tuple has the right type.  If we know where
		// the tuple was created, use the registers that provided values to the
		// creation.
		final L2Instruction tupleDefinitionInstruction =
			tupleReg.register().definitionSkippingMoves();
		if (tupleDefinitionInstruction.operation == L2_CREATE_TUPLE.instance)
		{
			return L2_CREATE_TUPLE.tupleSourceRegistersOf(
				tupleDefinitionInstruction);
		}

		// We have to extract the elements.
		final List<L2ReadPointerOperand> elementReaders =
			new ArrayList<>(tupleSize);
		for (int i = 1; i <= tupleSize; i++)
		{
			final A_Type elementType = tupleType.typeAtIndex(i);
			final L2WritePointerOperand elementWriter =
				translator.newObjectRegisterWriter(elementType, null);
			translator.addInstruction(
				L2_TUPLE_AT_CONSTANT.instance,
				tupleReg,
				new L2ImmediateOperand(i),
				elementWriter);
			elementReaders.add(elementWriter.read());
		}
		return elementReaders;
	}


	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ObjectRegister tupleReg =
			instruction.readObjectRegisterAt(0).register();
		final List<L2WritePointerOperand> elementsVector =
			instruction.writeVectorRegisterAt(1);

		// Exploding an empty tuple into no registers is pointless; forbid it.
		assert !elementsVector.isEmpty();

		// :: tuple.makeImmutable();
		translator.load(method, tupleReg);
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_BasicObject.class),
			"makeImmutable",
			getMethodDescriptor(getType(AvailObject.class)),
			true);

		for (int i = 0, limit = elementsVector.size(); i < limit; i++)
		{
			// :: «elements[i]» = tuple.tupleAt(i + 1);
			if (i < limit - 1)
			{
				method.visitInsn(DUP);
			}
			translator.intConstant(method, i + 1);
			method.visitMethodInsn(
				INVOKEINTERFACE,
				getInternalName(A_Tuple.class),
				"tupleAt",
				getMethodDescriptor(getType(AvailObject.class), INT_TYPE),
				true);
			translator.store(method, elementsVector.get(i).register());
		}
	}
}
