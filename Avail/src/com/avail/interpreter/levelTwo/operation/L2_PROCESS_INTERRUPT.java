package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.*;

/**
 * Handle an interrupt that has been requested.
 */
public class L2_PROCESS_INTERRUPT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_PROCESS_INTERRUPT();

	static
	{
		instance.init(
			READ_POINTER.is("continuation"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		interpreter.interruptProcess();
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Don't remove this kind of instruction.
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		// Process will resume with the given continuation.
		return false;
	}
}