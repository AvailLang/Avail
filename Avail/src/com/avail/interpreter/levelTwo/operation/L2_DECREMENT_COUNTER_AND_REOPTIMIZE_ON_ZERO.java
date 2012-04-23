package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2Interpreter.argumentOrLocalRegister;
import static com.avail.interpreter.levelTwo.register.FixedRegister.FUNCTION;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.optimizer.L2Translator;

/**
 * Explicitly decrement the current compiled code's countdown via {@link
 * AvailObject#countdownToReoptimize(int)}.  If it reaches zero then
 * re-optimize the code.
 */
public class L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO();

	static
	{
		instance.init();
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final AvailObject theFunction = interpreter.pointerAt(FUNCTION);
		final AvailObject theCode = theFunction.code();
		final int newCount = theCode.invocationCount() - 1;
		assert newCount >= 0;
		if (newCount != 0)
		{
			theCode.countdownToReoptimize(newCount);
		}
		else
		{
			theCode.countdownToReoptimize(
				L2ChunkDescriptor.countdownForNewlyOptimizedCode());
			final L2Translator translator = new L2Translator(theCode);
			translator.translateOptimizationFor(
				3,
				interpreter);
			interpreter.argsBuffer.clear();
			final int nArgs = theCode.numArgs();
			for (int i = 1; i <= nArgs; i++)
			{
				interpreter.argsBuffer.add(
					interpreter.pointerAt(argumentOrLocalRegister(i)));
			}
			interpreter.invokeFunctionArguments(
				theFunction,
				interpreter.argsBuffer);
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}
}