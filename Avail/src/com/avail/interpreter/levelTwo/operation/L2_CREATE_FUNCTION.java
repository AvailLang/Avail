package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

/**
 * Synthesize a new {@link FunctionDescriptor function} from the provided
 * constant compiled code and the vector of captured ("outer") variables.
 */
public class L2_CREATE_FUNCTION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_CREATE_FUNCTION();

	static
	{
		instance.init(
			CONSTANT.is("compiled code"),
			READ_VECTOR.is("captured variables"),
			WRITE_POINTER.is("new function"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int codeIndex = interpreter.nextWord();
		final int outersIndex = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();
		final AvailObject outers = interpreter.vectorAt(outersIndex);
		final AvailObject clos = FunctionDescriptor.mutable().create(
			outers.tupleSize());
		clos.code(interpreter.chunk().literalAt(codeIndex));
		for (int i = 1, end = outers.tupleSize(); i <= end; i++)
		{
			clos.outerVarAtPut(
				i,
				interpreter.pointerAt(outers.tupleIntAt(i)));
		}
		interpreter.pointerAtPut(destIndex, clos);
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2ConstantOperand codeOperand =
			(L2ConstantOperand) instruction.operands[0];
		final L2ReadVectorOperand outersOperand =
			(L2ReadVectorOperand) instruction.operands[1];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[2];
		registers.typeAtPut(
			destinationOperand.register,
			codeOperand.object.functionType());
		registers.propagateWriteTo(destinationOperand.register);
		if (outersOperand.vector.allRegistersAreConstantsIn(registers))
		{
			final AvailObject function =
				FunctionDescriptor.mutable().create(
					outersOperand.vector.registers().size());
			function.code(codeOperand.object);
			int index = 1;
			for (final L2ObjectRegister outer : outersOperand.vector)
			{
				function.outerVarAtPut(
					index++,
					registers.constantAt(outer));
			}
		}
		else
		{
			registers.removeConstantAt(destinationOperand.register);
		}
	}
}