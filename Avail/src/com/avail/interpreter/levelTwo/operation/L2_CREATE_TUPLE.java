package com.avail.interpreter.levelTwo.operation;

import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.*;
import com.avail.optimizer.RegisterSet;

/**
 * Create a {@link TupleDescriptor tuple} from the {@linkplain AvailObject
 * objects} in the specified registers.
 */
public class L2_CREATE_TUPLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_CREATE_TUPLE();

	static
	{
		instance.init(
			READ_VECTOR.is("elements"),
			WRITE_POINTER.is("tuple"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int valuesIndex = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();
		final AvailObject indices = interpreter.vectorAt(valuesIndex);
		final int size = indices.tupleSize();
		final AvailObject tuple =
			ObjectTupleDescriptor.mutable().create(size);
		for (int i = 1; i <= size; i++)
		{
			tuple.tupleAtPut(
				i,
				interpreter.pointerAt(indices.tupleIntAt(i)));
		}
		interpreter.pointerAtPut(destIndex, tuple);
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2ReadVectorOperand sourcesOperand =
			(L2ReadVectorOperand) instruction.operands[0];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[1];

		final L2RegisterVector sourceVector = sourcesOperand.vector;
		final int size = sourceVector.registers().size();
		final AvailObject sizeRange =
			IntegerDescriptor.fromInt(size).kind();
		List<AvailObject> types;
		types = new ArrayList<AvailObject>(sourceVector.registers().size());
		for (final L2Register register : sourceVector.registers())
		{
			if (registers.hasTypeAt(register))
			{
				types.add(registers.typeAt(register));
			}
			else
			{
				types.add(ANY.o());
			}
		}
		final AvailObject tupleType =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				sizeRange,
				TupleDescriptor.fromCollection(types),
				BottomTypeDescriptor.bottom());
		tupleType.makeImmutable();
		registers.typeAtPut(destinationOperand.register, tupleType);
		registers.propagateWriteTo(destinationOperand.register);
		if (sourceVector.allRegistersAreConstantsIn(registers))
		{
			final List<AvailObject> constants = new ArrayList<AvailObject>(
				sourceVector.registers().size());
			for (final L2Register register : sourceVector.registers())
			{
				constants.add(registers.constantAt(register));
			}
			final AvailObject tuple = TupleDescriptor.fromCollection(
				constants);
			tuple.makeImmutable();
			assert tuple.isInstanceOf(tupleType);
			registers.constantAtPut(
				destinationOperand.register,
				tuple);
		}
		else
		{
			registers.removeConstantAt(
				destinationOperand.register);
		}
	}
}