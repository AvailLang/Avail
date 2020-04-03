/*
 * L2_CREATE_OBJECT.java
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

import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.objects.ObjectLayoutVariant;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.avail.descriptor.objects.ObjectDescriptor.createUninitializedObjectMethod;
import static com.avail.descriptor.objects.ObjectDescriptor.setFieldMethod;
import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Create an object using a constant pojo holding an {@link ObjectLayoutVariant}
 * and a vector of values, in the order the variant lays them out as fields.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_CREATE_OBJECT
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_OBJECT}.
	 */
	private L2_CREATE_OBJECT ()
	{
		super(
			CONSTANT.is("variant pojo"),
			READ_BOXED_VECTOR.is("field values"),
			WRITE_BOXED.is("new object"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_CREATE_OBJECT instance = new L2_CREATE_OBJECT();

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ConstantOperand variantOperand = instruction.operand(0);
		final L2ReadBoxedVectorOperand fieldsVector = instruction.operand(1);
		final L2WriteBoxedOperand object = instruction.operand(2);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(object.registerString());
		builder.append(" ← {");
		final ObjectLayoutVariant variant =
			variantOperand.object.javaObjectNotNull();
		final List<A_Atom> realSlots = variant.realSlots;
		final List<L2ReadBoxedOperand> fieldSources = fieldsVector.elements();
		assert realSlots.size() == fieldSources.size();
		for (int i = 0; i < realSlots.size(); i++)
		{
			if (i > 0)
			{
				builder.append(", ");
			}
			final A_Atom key = realSlots.get(i);
			final L2ReadBoxedOperand value = fieldSources.get(i);
			builder.append(key);
			builder.append(": ");
			builder.append(value.registerString());
		}
		builder.append('}');
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		assert this == instruction.operation();
		final L2ConstantOperand variantOperand = instruction.operand(0);
		final L2ReadBoxedVectorOperand fieldsVector = instruction.operand(1);
		final L2WriteBoxedOperand object = instruction.operand(2);

		final ObjectLayoutVariant variant =
			variantOperand.object.javaObjectNotNull();
		method.visitLdcInsn(variant);
		createUninitializedObjectMethod.generateCall(method);
		final List<L2ReadBoxedOperand> fieldSources = fieldsVector.elements();
		for (int i = 0, limit = fieldSources.size(); i < limit; i++)
		{
			method.visitLdcInsn(i + 1);
			translator.load(method, fieldSources.get(i).register());
			setFieldMethod.generateCall(method); // Returns object for chaining.
		}
		translator.store(method, object.register());
	}
}
