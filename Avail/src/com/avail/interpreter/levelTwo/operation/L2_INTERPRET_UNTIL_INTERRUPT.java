package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2Interpreter.*;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import java.util.logging.Level;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.FixedRegister;
import com.avail.optimizer.RegisterSet;

/**
 * Execute a single nybblecode of the current continuation, found in {@link
 * FixedRegister#CALLER caller register}.  If no interrupt is indicated,
 * move the L2 {@link L2Interpreter#offset()} back to the same instruction
 * (which always occupies a single word, so the address is implicit).
 */
public class L2_INTERPRET_UNTIL_INTERRUPT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_INTERPRET_UNTIL_INTERRUPT();

	static
	{
		instance.init(
			PC.is("return here after a call"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final AvailObject function = interpreter.pointerAt(FUNCTION);
		final AvailObject code = function.code();
		final AvailObject nybbles = code.nybbles();
		final int pc = interpreter.integerAt(pcRegister());

		if (!interpreter.isInterruptRequested())
		{
			// Branch back to this (operandless) instruction by default.
			interpreter.offset(interpreter.offset() - 1);
		}

		int depth = 0;
		if (debugL1)
		{
			for (
				AvailObject c = interpreter.pointerAt(CALLER);
				!c.equalsNull();
				c = c.caller())
			{
				depth++;
			}
		}

		// Before we extract the nybblecode, make sure that the PC hasn't
		// passed the end of the instruction sequence. If we have, then
		// execute an L1Implied_doReturn.
		if (pc > nybbles.tupleSize())
		{
			assert pc == nybbles.tupleSize() + 1;
			if (Interpreter.logger.isLoggable(Level.FINEST))
			{
				Interpreter.logger.finest(String.format(
					"simulating %s (pc = %d)",
					L1Operation.L1Implied_Return,
					pc));
			}
			if (debugL1)
			{
				System.out.printf("%n%d  Step L1: return", depth);
			}
			interpreter.levelOneStepper.L1Implied_doReturn();
			return;
		}
		final int nybble = nybbles.extractNybbleFromTupleAt(pc);
		interpreter.integerAtPut(pcRegister(), (pc + 1));

		final L1Operation operation = L1Operation.values()[nybble];
		if (Interpreter.logger.isLoggable(Level.FINEST))
		{
			Interpreter.logger.finest(String.format(
				"simulating %s (pc = %d)",
				operation,
				pc));
		}
		if (debugL1)
		{
			System.out.printf("%n%d  Step L1: %s", depth, operation);
		}
		operation.dispatch(interpreter.levelOneStepper);
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		// No real optimization should ever be done near this wordcode.
		// Do nothing.
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Keep this instruction from being removed, since it's only used
		// by the default chunk.
		return true;
	}
}