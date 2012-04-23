package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.FixedRegister;

/**
 * This marks a re-entry point into optimized (level two) code.  At re-entry,
 * only the architectural {@link FixedRegister#CALLER} register has a value.
 * This mechanism is used for a re-entry point to which a return should
 * arrive, as well as when restarting a continuation created from a
 * {@link L1Operation#L1Ext_doPushLabel push-label L1 instruction}.  In the
 * former, the return value has already been written into the continuation,
 * and in the latter only the continuation's argument slots are non-nil.
 */
public class L2_REENTER_L2_CHUNK extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_REENTER_L2_CHUNK();

	static
	{
		instance.init(
			WRITE_POINTER.is("continuation"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		error("Re-enter chunk wordcode is not executable\n");
	}

	@Override
	public boolean shouldEmit ()
	{
		return false;
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Don't eliminate, even though no wordcodes would be generated.
		return true;
	}
}