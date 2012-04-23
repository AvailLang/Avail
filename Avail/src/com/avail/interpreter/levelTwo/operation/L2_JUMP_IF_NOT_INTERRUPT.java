package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.*;

/**
 * Jump to the specified level two program counter if no interrupt has been
 * requested since last serviced.  Otherwise an interrupt has been requested
 * and we should do nothing then proceed to the next instruction.
 */
public class L2_JUMP_IF_NOT_INTERRUPT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_JUMP_IF_NOT_INTERRUPT();

	static
	{
		instance.init(
			PC.is("target if not interrupt"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int ifNotIndex = interpreter.nextWord();
		if (!interpreter.isInterruptRequested())
		{
			interpreter.offset(ifNotIndex);
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}
}