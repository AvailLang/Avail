package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.TypeDescriptor.Types.TYPE;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

/**
 * Extract the {@link InstanceTypeDescriptor exact type} of an object in a
 * register, writing the type to another register.
 */
public class L2_GET_TYPE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_GET_TYPE();

	static
	{
		instance.init(
			READ_POINTER.is("value"),
			WRITE_POINTER.is("value's type"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int srcIndex = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();
		interpreter.pointerAtPut(
			destIndex,
			InstanceTypeDescriptor.on(interpreter.pointerAt(srcIndex)));
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2ReadPointerOperand sourceOperand =
			(L2ReadPointerOperand) instruction.operands[0];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[1];

		final L2ObjectRegister sourceRegister = sourceOperand.register;
		final L2ObjectRegister destinationRegister =
			destinationOperand.register;
		if (registers.hasTypeAt(sourceRegister))
		{
			final AvailObject type =
				registers.typeAt(sourceRegister);
			// Apply the rule of metacovariance. It says that given types T1
			// and T2, T1 <= T2 implies T1 type <= T2 type. It is guaranteed
			// true for all types in Avail.
			final AvailObject meta = InstanceTypeDescriptor.on(type);
			registers.typeAtPut(destinationRegister, meta);
		}
		else
		{
			registers.typeAtPut(destinationRegister, TYPE.o());
		}

	if (registers.hasConstantAt(sourceRegister))
		{
			registers.constantAtPut(
				destinationRegister,
				registers.constantAt(sourceRegister).kind());
		}
		else
		{
			registers.removeConstantAt(destinationRegister);
		}
		registers.propagateWriteTo(destinationRegister);
	}
}