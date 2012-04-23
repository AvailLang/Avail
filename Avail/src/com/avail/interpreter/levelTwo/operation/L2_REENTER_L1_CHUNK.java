package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2Interpreter.*;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.*;

/**
 * Arrive here by returning from a called method into unoptimized (level
 * one) code.  Explode the current continuation's slots into the registers
 * that level one expects.
 */
public class L2_REENTER_L1_CHUNK extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_REENTER_L1_CHUNK();

	static
	{
		instance.init();
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final AvailObject continuation = interpreter.pointerAt(CALLER);
		final int numSlots = continuation.numArgsAndLocalsAndStack();
		for (int i = 1; i <= numSlots; i++)
		{
			interpreter.pointerAtPut(
				argumentOrLocalRegister(i),
				continuation.stackAt(i));
		}
		interpreter.integerAtPut(pcRegister(), continuation.pc());
		interpreter.integerAtPut(
			stackpRegister(),
			argumentOrLocalRegister(continuation.stackp()));
		interpreter.pointerAtPut(FUNCTION, continuation.function());
		interpreter.pointerAtPut(CALLER, continuation.caller());
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}
}