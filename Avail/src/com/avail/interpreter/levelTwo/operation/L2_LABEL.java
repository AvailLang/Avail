package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.levelTwo.L2OperandType.COMMENT;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.*;

/**
 * A label can be the target of a branching instruction.  It is not actually
 * emitted in the instruction stream, but it acts as a place holder during
 * code generation and optimization.
 */
public class L2_LABEL extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_LABEL();

	static
	{
		instance.init(
			COMMENT.is("Name of label"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		error("Label wordcode is not executable\n");
	}

	@Override
	public boolean shouldEmit ()
	{
		return false;
	}
}