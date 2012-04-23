package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static com.avail.interpreter.levelTwo.register.FixedRegister.CALLER;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.*;

/**
 * Return into the provided continuation with the given return value.  The
 * continuation may be running as either level one or level two.
 */
public class L2_RETURN extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_RETURN();

	static
	{
		instance.init(
			READ_POINTER.is("continuation"),
			READ_POINTER.is("return value"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		// Return to the calling continuation with the given value.
		final int continuationIndex = interpreter.nextWord();
		final int valueIndex = interpreter.nextWord();
		assert continuationIndex == CALLER.ordinal();

		final AvailObject caller = interpreter.pointerAt(continuationIndex);
		final AvailObject valueObject = interpreter.pointerAt(valueIndex);
		interpreter.returnToCaller(caller, valueObject);
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove this.
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		return false;
	}
}