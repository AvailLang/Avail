/**
 * interpreter/levelOne/L1InstructionWriter.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.avail.interpreter.levelOne;

import static com.avail.descriptor.TypeDescriptor.Types.TYPE;
import java.io.ByteArrayOutputStream;
import java.util.*;
import com.avail.descriptor.*;

public class L1InstructionWriter
{

	private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

	final List<AvailObject> literals = new ArrayList<AvailObject>();

	private final Map<AvailObject, Integer> reverseLiterals = new HashMap<AvailObject, Integer>();

	public int addLiteral (final AvailObject literal)
	{
		Integer index = reverseLiterals.get(literal);
		if (index == null)
		{
			literals.add(literal);
			index = literals.size();
			reverseLiterals.put(literal, index);
		}
		return index;
	}

	private List<AvailObject> argumentTypes;

	public void argumentTypes (final AvailObject ... argTypes)
	{
		assert localTypes.size() == 0: "Must declare argument types before allocating locals";
		this.argumentTypes = Arrays.asList(argTypes);
	}

	private AvailObject returnType;

	public void returnType (final AvailObject retType)
	{
		this.returnType = retType;
	}

	private final List<AvailObject> localTypes = new ArrayList<AvailObject>();

	public int createLocal (final AvailObject localType)
	{
		assert argumentTypes != null : "Must declare argument types before allocating locals";
		assert localType.isInstanceOfSubtypeOf(TYPE.o());
		localTypes.add(localType);
		return localTypes.size();
	}

	private final List<AvailObject> outerTypes = new ArrayList<AvailObject>();

	public int createOuter (final AvailObject outerType)
	{
		outerTypes.add(outerType);
		return outerTypes.size();
	}

	private int primitiveNumber = 0;

	public void primitiveNumber (final int primNumber)
	{
		assert this.primitiveNumber == 0 : "Don't set the primitive twice";
		this.primitiveNumber = primNumber;
	}

	L1StackTracker stackTracker = new L1StackTracker ()
	{
		@Override AvailObject literalAt (final int literalIndex)
		{
			return literals.get(literalIndex - 1);
		}
	};



	private void writeOperand (final int operand)
	{
		if (operand < 10)
		{
			stream.write(operand);
		}
		else if (operand < 58)
		{
			stream.write(operand + 150 >>> 4);
			stream.write(operand + 150 & 15);
		}
		else if (operand < 314)
		{
			stream.write(13);
			stream.write(operand - 58 >>> 4);
			stream.write(operand - 59 & 15);
		}
		else if (operand < 65536)
		{
			stream.write(14);
			stream.write(operand >>> 12);
			stream.write(operand >>> 8 & 15);
			stream.write(operand >>> 4 & 15);
			stream.write(operand & 15);
		}
		else
		{
			stream.write(15);
			stream.write(operand >>> 28);
			stream.write(operand >>> 24 & 15);
			stream.write(operand >>> 20 & 15);
			stream.write(operand >>> 16 & 15);
			stream.write(operand >>> 12 & 15);
			stream.write(operand >>> 8 & 15);
			stream.write(operand >>> 4 & 15);
			stream.write(operand & 15);
		}
	}


	public void write (final L1Instruction instruction)
	{
		stackTracker.track(instruction);
		final byte opcode = (byte)instruction.operation().ordinal();
		if (opcode <= 15)
		{
			stream.write(opcode);
		}
		else
		{
			stream.write(L1Operation.L1_doExtension.ordinal());
			stream.write(opcode - 16);
		}
		final int [] operands = instruction.operands();
		for (final int operand : operands)
		{
			writeOperand(operand);
		}
	}


	private AvailObject nybbles ()
	{
		final AvailObject nybbles =
			NybbleTupleDescriptor.mutableObjectOfSize(stream.size());
		nybbles.hashOrZero(0);
		final byte [] byteArray = stream.toByteArray();
		for (int i = 0; i < byteArray.length; i++)
		{
			nybbles.rawNybbleAtPut(i + 1, byteArray[i]);
		}
		nybbles.makeImmutable();
		return nybbles;
	}


	public AvailObject compiledCode ()
	{
		return CompiledCodeDescriptor.create(
			nybbles(),
			localTypes.size(),
			stackTracker.maxDepth(),
			ClosureTypeDescriptor.create(
				TupleDescriptor.fromList(argumentTypes),
				returnType),
			primitiveNumber,
			TupleDescriptor.fromList(literals),
			TupleDescriptor.fromList(localTypes),
			TupleDescriptor.fromList(outerTypes));
	}
}