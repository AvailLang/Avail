/**
 * interpreter/levelOne/L1Disassembler.java
 * Copyright (c) 2011, Mark van Gulik.
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

import static com.avail.descriptor.AvailObject.error;
import java.util.List;
import com.avail.descriptor.*;

public class L1Disassembler
{
	AvailObject _code;
	StringBuilder _builder;
	List<AvailObject> _recursionList;
	int _indent;
	AvailObject _nybbles;
	int _pc;


	L1OperandTypeDispatcher operandTypePrinter = new L1OperandTypeDispatcher()
	{

		@Override
		public void doImmediate ()
		{
			_builder.append("immediate=" + getInteger());
		}

		@Override
		public void doLiteral ()
		{
			final int index = getInteger();
			_builder.append("literal#" + index + "=");
			_code.literalAt(index).printOnAvoidingIndent(
				_builder,
				_recursionList,
				_indent + 1);
		}

		@Override
		public void doLocal ()
		{
			final int index = getInteger();
			if (index <= _code.numArgs())
			{
				_builder.append("arg#" + index);
			}
			else
			{
				_builder.append("local#" + (index - _code.numArgs()));
			}
		}

		@Override
		public void doOuter ()
		{
			_builder.append("outer#" + getInteger());
		}

		@Override
		public void doExtension ()
		{
			error("Extension nybblecode should be dealt with another way.");
		}

	};

	/**
	 * Parse the given compiled code object into a sequence of L1 instructions,
	 * printing them on the provided stream.
	 *
	 * @param code
	 *        The {@link CompiledCodeDescriptor code} to decompile.
	 * @param builder
	 *        Where to write the decompilation.
	 * @param recursionList
	 *        Which objects are already being visited.
	 * @param indent
	 *        The indentation level.
	 */
	public void disassemble (
		final AvailObject code,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		_code = code;
		_builder = builder;
		_recursionList = recursionList;
		_indent = indent;

		_nybbles = code.nybbles();
		_pc = 1;
		boolean first = true;
		while (_pc <= _nybbles.tupleSize())
		{
			if (!first)
			{
				builder.append("\n");
			}
			first = false;
			for (int i = indent; i > 0; i--)
			{
				builder.append("\t");
			}
			builder.append(_pc + ": ");
			int nybble = _nybbles.extractNybbleFromTupleAt(_pc++);
			if (nybble == L1Operation.L1_doExtension.ordinal())
			{
				nybble = 16 + _nybbles.extractNybbleFromTupleAt(_pc++);
			}
			final L1Operation operation = L1Operation.values()[nybble];
			final L1OperandType[] operandTypes = operation.operandTypes();
			builder.append(operation.name());
			if (operandTypes.length > 0)
			{
				builder.append("(");
				for (int i = 0; i < operandTypes.length; i++)
				{
					if (i > 0)
					{
						builder.append(", ");
					}
					operandTypes[i].dispatch(operandTypePrinter);
				}
				builder.append(")");
			}
		}
	}



	/**
	 * Extract an encoded integer from the nybblecode instruction stream.  The
	 * encoding uses only a nybble for very small operands, and can still
	 * represent up to {@link Integer#MAX_VALUE} if necessary.
	 * <p>
	 * Adjust the {@link #_pc program counter} to skip the integer.
	 *
	 * @return The integer extracted from the nybblecode stream.
	 */
	public int getInteger ()
	{
		final int tag = _nybbles.extractNybbleFromTupleAt(_pc);
		if (tag < 10)
		{
			_pc++;
			return tag;
		}
		int integer;
		if (tag <= 12)
		{
			integer = tag * 16 - 150 + _nybbles.extractNybbleFromTupleAt(_pc + 1);
			_pc += 2;
			return integer;
		}
		if (tag == 13)
		{
			integer = (_nybbles.extractNybbleFromTupleAt(_pc + 1) << 4)
			+ _nybbles.extractNybbleFromTupleAt(_pc + 2)
			+ 58;
			_pc += 3;
			return integer;
		}
		if (tag == 14)
		{
			integer = 0;
			for (int _count1 = 1; _count1 <= 4; _count1++)
			{
				integer <<= 4;
				integer += _nybbles.extractNybbleFromTupleAt(++_pc);
			}
			//  making 5 nybbles total
			_pc++;
			return integer;
		}
		if (tag == 15)
		{
			integer = 0;
			for (int _count2 = 1; _count2 <= 8; _count2++)
			{
				integer <<= 4;
				integer += _nybbles.extractNybbleFromTupleAt(++_pc);
			}
			//  making 9 nybbles total
			_pc++;
			return integer;
		}
		error("Impossible nybble");
		return 0;
	}

}
