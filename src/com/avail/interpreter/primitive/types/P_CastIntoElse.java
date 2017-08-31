/**
 * P_CastIntoElse.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.types;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import javax.annotation.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2ImmediateOperand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operation.L2_CREATE_CONTINUATION;
import com.avail.interpreter.levelTwo.operation.L2_FUNCTION_PARAMETER_TYPE;
import com.avail.interpreter.levelTwo.operation.L2_INVOKE;
import com.avail.interpreter.levelTwo.operation.L2_JUMP;
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_IS_NOT_KIND_OF_OBJECT;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;

/**
 * <strong>Primitive:</strong> If the second argument, a {@linkplain A_Function
 * function}, accepts the first argument as its parameter, do the invocation.
 * Otherwise invoke the third argument, a zero-argument function.
 */
public final class P_CastIntoElse extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_CastIntoElse().init(
			3, Invokes, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_BasicObject value = args.get(0);
		final A_Function castBlock = args.get(1);
		final A_Function elseBlock = args.get(2);
		if (value.isInstanceOf(
			castBlock.code().functionType().argsTupleType().typeAtIndex(1)))
		{
			return interpreter.invokeFunction(
				castBlock,
				Collections.singletonList(value),
				false);
		}
		return interpreter.invokeFunction(
			elseBlock, Collections.<AvailObject>emptyList(), false);
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type trueBlockType = argumentTypes.get(1);
		return trueBlockType.returnType();
	}

	@Override
	public @Nullable L2ObjectRegister foldOutInvoker (
		final List<L2ObjectRegister> args,
		final L1NaiveTranslator naiveTranslator)
	{
		// Don't fold out the invoker here.  Generate the ordinary call, but
		// allow the call to be regenerated as something more precise.
		return null;
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final L1NaiveTranslator naiveTranslator,
		final RegisterSet registerSet)
	{
		// Inline the invocation of this P_CastIntoElse primitive, such that it
		// does a type test for the type being cast to, then either invokes the
		// first block with the value being cast or the second block with no
		// arguments.  Subsequent optimization passes may be able to eliminate
		// the test.
		assert instruction.operation == L2_INVOKE.instance;

		final L2ObjectRegister continuationReg =
			instruction.readObjectRegisterAt(0);
//		final L2ObjectRegister invokerFunctionReg =
//			instruction.readObjectRegisterAt(1);
		final L2RegisterVector invokerArgumentsVector =
			instruction.readVectorRegisterAt(2);
//		final int skipCheck = instruction.immediateAt(3);

		// Separate the three arguments to the cast: the value, the intoBlock,
		// and the elseBlock.
		final List<L2ObjectRegister> arguments =
			invokerArgumentsVector.registers();
		final L2ObjectRegister valueReg = arguments.get(0);
		final L2ObjectRegister intoBlockReg = arguments.get(1);
		final L2ObjectRegister elseBlockReg = arguments.get(2);

		// Extract information about the continuation's construction.  Hopefully
		// we'll switch to SSA form before implementing code movement, so assume
		// the registers populating the continuation are still live after the
		// type test and branch, whether taken or not.
		final List<L2Instruction> continuationCreationInstructions =
			registerSet.stateForReading(continuationReg).sourceInstructions();
		if (continuationCreationInstructions.size() != 1)
		{
			// We can't figure out where the continuation got built.  Give up.
			return false;
		}
		final L2Instruction continuationCreationInstruction =
			continuationCreationInstructions.get(0);
		if (!(continuationCreationInstruction.operation
			instanceof L2_CREATE_CONTINUATION))
		{
			// We found the origin of the continuation register, but not the
			// creation instruction itself.  Give up.
			return false;
		}
		final L2Instruction elseLabel = naiveTranslator.newLabel("cast failed");
		final L2Instruction afterIntoCallLabel =
			naiveTranslator.newLabel("after into call of cast");
		final L2Instruction afterElseCallLabel =
			naiveTranslator.newLabel("after else call of cast");
		final L2Instruction afterAllLabel =
			naiveTranslator.newLabel("after entire cast");

		// Inline a type-test and branch...
		final L2ObjectRegister typeReg = naiveTranslator.newObjectRegister();
		naiveTranslator.addInstruction(
			L2_FUNCTION_PARAMETER_TYPE.instance,
			new L2ReadPointerOperand(intoBlockReg),
			new L2ImmediateOperand(1),
			new L2WritePointerOperand(typeReg));
		naiveTranslator.addInstruction(
			L2_JUMP_IF_IS_NOT_KIND_OF_OBJECT.instance,
			new L2PcOperand(elseLabel),
			new L2ReadPointerOperand(valueReg),
			new L2ReadPointerOperand(typeReg));
		// The type check succeeded (i.e., didn't branch).
		final L2ObjectRegister continuationCase1Reg =
			naiveTranslator.newObjectRegister();
		naiveTranslator.addInstruction(
			L2_CREATE_CONTINUATION.instance,
			new L2ReadPointerOperand(
				continuationCreationInstruction.readObjectRegisterAt(0)),
			new L2ReadPointerOperand(
				continuationCreationInstruction.readObjectRegisterAt(1)),
			new L2ImmediateOperand(
				continuationCreationInstruction.immediateAt(2)),
			new L2ImmediateOperand(
				continuationCreationInstruction.immediateAt(3)),
			new L2ReadIntOperand(
				continuationCreationInstruction.readIntRegisterAt(4)),
			new L2ReadVectorOperand(
				continuationCreationInstruction.readVectorRegisterAt(5)),
			new L2PcOperand(afterIntoCallLabel),
			new L2WritePointerOperand(continuationCase1Reg));
		naiveTranslator.addInstruction(
			L2_INVOKE.instance,
			new L2ReadPointerOperand(continuationCase1Reg),
			new L2ReadPointerOperand(intoBlockReg),
			new L2ReadVectorOperand(
				naiveTranslator.createVector(
					Collections.singletonList(valueReg))),
			new L2ImmediateOperand(0));
		naiveTranslator.unreachableCode(
			naiveTranslator.newLabel("unreachable after into call of cast"));
		naiveTranslator.addLabel(afterIntoCallLabel);
		naiveTranslator.addInstruction(
			L2_JUMP.instance,
			new L2PcOperand(afterAllLabel));

		// Now generate the else part...
		naiveTranslator.addLabel(elseLabel);
		final L2ObjectRegister continuationCase2Reg =
			naiveTranslator.newObjectRegister();
		naiveTranslator.addInstruction(
			L2_CREATE_CONTINUATION.instance,
			new L2ReadPointerOperand(
				continuationCreationInstruction.readObjectRegisterAt(0)),
			new L2ReadPointerOperand(
				continuationCreationInstruction.readObjectRegisterAt(1)),
			new L2ImmediateOperand(
				continuationCreationInstruction.immediateAt(2)),
			new L2ImmediateOperand(
				continuationCreationInstruction.immediateAt(3)),
			new L2ReadIntOperand(
				continuationCreationInstruction.readIntRegisterAt(4)),
			new L2ReadVectorOperand(
				continuationCreationInstruction.readVectorRegisterAt(5)),
			new L2PcOperand(afterElseCallLabel),
			new L2WritePointerOperand(continuationCase2Reg));
		naiveTranslator.addInstruction(
			L2_INVOKE.instance,
			new L2ReadPointerOperand(continuationCase2Reg),
			new L2ReadPointerOperand(elseBlockReg),
			new L2ReadVectorOperand(
				naiveTranslator.createVector(
					Collections.emptyList())),
			new L2ImmediateOperand(0));
		naiveTranslator.unreachableCode(
			naiveTranslator.newLabel("unreachable after else call of cast"));
		naiveTranslator.addLabel(afterElseCallLabel);
		naiveTranslator.addLabel(afterAllLabel);
		return true;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ANY.o(),
				FunctionTypeDescriptor.create(
					TupleDescriptor.from(
						BottomTypeDescriptor.bottom()),
					TOP.o()),
				FunctionTypeDescriptor.create(
					TupleDescriptor.empty(),
					TOP.o())),
			TOP.o());
	}
}
