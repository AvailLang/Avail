package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.levelTwo.L2Interpreter.debugL1;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

/**
 * Look up the method to invoke.  Use the provided vector of argument
 * <em>types</em> to perform a polymorphic lookup.  Write the resulting
 * function into the specified destination register.
 */
public class L2_LOOKUP_BY_TYPES extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_LOOKUP_BY_TYPES();

	static
	{
		instance.init(
			SELECTOR.is("method"),
			READ_VECTOR.is("argument types"),
			WRITE_POINTER.is("looked up function"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int selectorIndex = interpreter.nextWord();
		final int argumentTypesIndex = interpreter.nextWord();
		final int resultingFunctionIndex = interpreter.nextWord();
		final AvailObject vect = interpreter.vectorAt(argumentTypesIndex);
		interpreter.argsBuffer.clear();
		final int numArgs = vect.tupleSize();
		for (int i = 1; i <= numArgs; i++)
		{
			interpreter.argsBuffer.add(
				interpreter.pointerAt(vect.tupleIntAt(i)));
		}
		final AvailObject selector =
			interpreter.chunk().literalAt(selectorIndex);
		if (debugL1)
		{
			System.out.printf(
				"  --- looking up: %s%n",
				selector.name().name());
		}
		final AvailObject signatureToCall =
			selector.lookupByTypesFromList(interpreter.argsBuffer);
		if (signatureToCall.equalsNull())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (!signatureToCall.isMethod())
		{
			error("Attempted to call a non-implementation signature");
			return;
		}
		interpreter.pointerAtPut(
			resultingFunctionIndex,
			signatureToCall.bodyBlock());
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		// Find all possible implementations (taking into account the types
		// of the argument registers).  Then build an enumeration type over
		// those functions.
		final L2SelectorOperand selectorOperand =
			(L2SelectorOperand) instruction.operands[0];
		final L2ReadVectorOperand argsTypesOperand =
			(L2ReadVectorOperand) instruction.operands[1];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[2];
		final List<L2ObjectRegister> argTypeRegisters =
			argsTypesOperand.vector.registers();
		final int numArgs = argTypeRegisters.size();
		final List<AvailObject> argTypeBounds =
			new ArrayList<AvailObject>(numArgs);
		for (final L2ObjectRegister argTypeRegister : argTypeRegisters)
		{
			AvailObject type = registers.constantAt(argTypeRegister);
			if (type == null)
			{
				final AvailObject meta = registers.typeAt(argTypeRegister);
				if (meta != null && !meta.equals(TYPE.o()))
				{
					assert meta.instanceCount().equals(
						IntegerDescriptor.one());
					type = meta.instances().asTuple().tupleAt(1);
				}
				else
				{
					type = TOP.o();
				}
			}
			argTypeBounds.add(type);
		}
		// Figure out what could be invoked at runtime given these argument
		// type constraints.
		final List<AvailObject> possibleFunctions =
			new ArrayList<AvailObject>();
		final List<AvailObject> possibleSignatures =
			selectorOperand.method.implementationsAtOrBelow(argTypeBounds);
		for (final AvailObject signature : possibleSignatures)
		{
			if (signature.isMethod())
			{
				possibleFunctions.add(signature.bodyBlock());
			}
		}
		if (possibleFunctions.size() == 1)
		{
			// Only one function could be looked up (it's monomorphic for
			// this call site).  Therefore we know strongly what the
			// function is.
			registers.constantAtPut(
				destinationOperand.register,
				possibleFunctions.get(0));
		}
		else
		{
			final AvailObject enumType =
				AbstractEnumerationTypeDescriptor.withInstances(
					SetDescriptor.fromCollection(possibleFunctions));
			registers.typeAtPut(destinationOperand.register, enumType);
		}
	}
}