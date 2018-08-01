/*
 * L2_CREATE_FUNCTION.java
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
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2Synonym;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.FunctionDescriptor.createExceptOuters;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restriction;
import static com.avail.utility.Strings.increaseIndentation;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Synthesize a new {@link FunctionDescriptor function} from the provided
 * constant compiled code and the vector of captured ("outer") variables.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_CREATE_FUNCTION
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_FUNCTION}.
	 */
	private L2_CREATE_FUNCTION ()
	{
		super(
			CONSTANT.is("compiled code"),
			READ_VECTOR.is("captured variables"),
			WRITE_POINTER.is("new function"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_CREATE_FUNCTION instance =
		new L2_CREATE_FUNCTION();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final A_RawFunction code = instruction.constantAt(0);
		final List<L2ReadPointerOperand> outerRegs =
			instruction.readVectorRegisterAt(1);
		final L2WritePointerOperand newFunctionReg =
			instruction.writeObjectRegisterAt(2);

		registerSet.typeAtPut(
			newFunctionReg.register(), code.functionType(), instruction);
		if (registerSet.allRegistersAreConstant(outerRegs))
		{
			// This can be replaced with a statically constructed function
			// during regeneration, but for now capture the exact function that
			// will be constructed.
			final int numOuters = outerRegs.size();
			assert numOuters == code.numOuters();
			final A_Function function = createExceptOuters(code, numOuters);
			for (int i = 1; i <= numOuters; i++)
			{
				function.outerVarAtPut(
					i,
					registerSet.constantAt(outerRegs.get(i - 1).register()));
			}
			registerSet.constantAtPut(
				newFunctionReg.register(), function, instruction);
		}
		else
		{
			registerSet.removeConstantAt(newFunctionReg.register());
		}
	}

	@Override
	public L2ReadPointerOperand extractFunctionOuterRegister (
		final L2Instruction instruction,
		final L2ReadPointerOperand functionRegister,
		final int outerIndex,
		final A_Type outerType,
		final L1Translator translator)
	{
		final A_RawFunction code = instruction.constantAt(0);
		final List<L2ReadPointerOperand> outerRegs =
			instruction.readVectorRegisterAt(1);
//		final int newFunctionRegIndex =
//			instruction.writeObjectRegisterAt(2).finalIndex();

		// Narrow the outerType by the type that the raw function says the outer
		// must have.
		final A_Type typeFromCode = code.outerTypeAt(outerIndex);
		A_Type intersection = outerType.typeIntersection(typeFromCode);
		assert !intersection.isBottom();

		final L2ReadPointerOperand originalRegister =
			outerRegs.get(outerIndex - 1);
		// Narrow the intersection with this register's restriction.
		intersection = intersection.typeIntersection(originalRegister.type());
		assert !intersection.isBottom();

		final @Nullable L2Synonym<L2ObjectRegister, A_BasicObject> synonym =
			translator.currentManifest().registerToSynonym(
				originalRegister.register());
		if (synonym != null)
		{
			// The register that supplied the outer value is still live.  Use it
			// directly, which may allow the function creation to be elided.
			return synonym.defaultRegisterRead().register().read(
				restriction(intersection));
		}

		// The register that supplied the value is no longer live.  Extract the
		// value from the actual function.  Note that it's still guaranteed to
		// have the strengthened type.
		final L2WritePointerOperand tempReg =
			translator.newObjectRegisterWriter(restriction(intersection));
		translator.addInstruction(
			L2_MOVE_OUTER_VARIABLE.instance,
			new L2IntImmediateOperand(outerIndex),
			functionRegister,
			tempReg);
		return tempReg.read();
	}

	/**
	 * Extract the constant {@link A_RawFunction} from the given {@link
	 * L2Instruction}, which must have {@code L2_CREATE_FUNCTION} as its
	 * operation.
	 *
	 * @param instruction
	 *        The instruction to examine.
	 * @return The constant {@link A_RawFunction} extracted from the
	 *         instruction.
	 */
	@Override
	public A_RawFunction getConstantCodeFrom (
		final L2Instruction instruction)
	{
		assert instruction.operation() == instance;
		return instruction.constantAt(0);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2Operand code = instruction.operand(0);
		final List<L2ReadPointerOperand> outerRegs =
			instruction.readVectorRegisterAt(1);
		final L2ObjectRegister newFunctionReg =
			instruction.writeObjectRegisterAt(2).register();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(newFunctionReg);
		builder.append(" ← ");
		String decompiled = code.toString();
		for (int i = 0, limit = outerRegs.size(); i < limit; i++)
		{
			decompiled = decompiled.replace(
				"Outer#" + (i + 1), outerRegs.get(i).toString());
		}
		builder.append(increaseIndentation(decompiled, 1));
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final A_RawFunction code = instruction.constantAt(0);
		final List<L2ReadPointerOperand> outerRegs =
			instruction.readVectorRegisterAt(1);
		final L2ObjectRegister newFunctionReg =
			instruction.writeObjectRegisterAt(2).register();

		final int numOuters = outerRegs.size();
		assert numOuters == code.numOuters();

		// :: function = createExceptOuters(code, numOuters);
		translator.literal(method, code);
		translator.intConstant(method, numOuters);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(FunctionDescriptor.class),
			"createExceptOuters",
			getMethodDescriptor(
				getType(A_Function.class),
				getType(A_RawFunction.class),
				INT_TYPE),
			false);
		method.visitTypeInsn(CHECKCAST, getInternalName(AvailObject.class));
		for (int i = 0; i < numOuters; i++)
		{
			// :: function.outerVarAtPut(«i + 1», «outerRegs[i]»);
			method.visitInsn(DUP);
			translator.intConstant(method, i + 1);
			translator.load(method, outerRegs.get(i).register());
			method.visitMethodInsn(
				INVOKEINTERFACE,
				getInternalName(A_Function.class),
				"outerVarAtPut",
				getMethodDescriptor(
					VOID_TYPE,
					INT_TYPE,
					getType(AvailObject.class)),
				true);
		}
		// :: newFunction = function;
		translator.store(method, newFunctionReg);
	}
}
