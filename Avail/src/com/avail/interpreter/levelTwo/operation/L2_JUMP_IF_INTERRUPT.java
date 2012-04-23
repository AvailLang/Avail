package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.*;

/**
 * Jump to the specified level two program counter if an interrupt has been
 * requested but not yet serviced.  Otherwise do nothing.
 */
public class L2_JUMP_IF_INTERRUPT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_JUMP_IF_INTERRUPT();

	static
	{
		instance.init(
			PC.is("target if interrupt"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int ifIndex = interpreter.nextWord();
		if (interpreter.isInterruptRequested())
		{
			interpreter.offset(ifIndex);
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}
}