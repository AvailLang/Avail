package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.AvailObject.error;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.*;

/**
 * A place holder for invalid wordcode instructions.
 */
public class L2_UNKNOWN_WORDCODE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_UNKNOWN_WORDCODE();

	static
	{
		instance.init();
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		error("Unknown wordcode\n");
	}

	@Override
	public boolean shouldEmit ()
	{
		assert false
			: "An instruction with this operation should not be created";
		return false;
	}
}