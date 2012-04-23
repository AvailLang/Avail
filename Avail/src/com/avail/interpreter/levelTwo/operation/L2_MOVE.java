package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.*;

/**
 * Move an {@link AvailObject} from the source to the destination.  The
 * {@link L2Translator} creates more moves than are strictly necessary, but
 * various mechanisms cooperate to remove redundant inter-register moves.
 *
 * <p>
 * The object being moved is not made immutable by this operation, as that
 * is the responsibility of the {@link L2_MAKE_IMMUTABLE} operation.
 * </p>
 */
public class L2_MOVE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_MOVE();

	static
	{
		instance.init(
			READ_POINTER.is("source"),
			WRITE_POINTER.is("destination"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int fromIndex = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();
		interpreter.pointerAtPut(
			destIndex,
			interpreter.pointerAt(fromIndex));
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
		final L2Register sourceRegister = sourceOperand.register;
		final L2Register destinationRegister = destinationOperand.register;

		assert sourceRegister != destinationRegister;

		if (registers.hasTypeAt(sourceRegister))
		{
			registers.typeAtPut(
				destinationRegister,
				registers.typeAt(sourceRegister));
		}
		else
		{
			registers.removeTypeAt(destinationRegister);
		}
		if (registers.hasConstantAt(sourceRegister))
		{
			registers.constantAtPut(
				destinationRegister,
				registers.constantAt(sourceRegister));
		}
		else
		{
			registers.removeConstantAt(destinationRegister);
		}

	registers.propagateMove(sourceRegister, destinationRegister);
	}
}