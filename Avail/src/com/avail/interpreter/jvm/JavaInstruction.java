/**
 * JavaInstruction.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.interpreter.jvm;

import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import com.avail.annotations.Nullable;

/**
 * {@code JavaInstruction} is the abstract base for all fully reified Java
 * instructions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class JavaInstruction
{
	/**
	 * Has the instruction been emitted to an {@linkplain InstructionWriter
	 * instruction writer} yet?
	 */
	boolean emitted = false;

	/** The canonical invalid address. */
	protected static long invalidAddress = -1L;

	/** The address of the instruction within the compiled method. */
	private long address = invalidAddress;

	/**
	 * Answer the address of the instruction within the compiled method.
	 *
	 * @return The address of the instruction within the compiled method.
	 */
	public final long address ()
	{
		return address;
	}

	/**
	 * A {@linkplain Label} label should only be returned for subclasses of
	 * {@linkplain JavaInstruction} that target one or more {@linkplain Label
	 * labels}.
	 *
	 * @return The target labels.
	 */
	public Label[] labels ()
	{
		return new Label[0];
	}

	/**
	 * Set the address of the instruction.
	 *
	 * @param address
	 *        The address within the compiled method.
	 */
	final void setAddress (final long address)
	{
		this.address = address;
	}

	/**
	 * Does the {@linkplain JavaInstruction instruction} have a valid address
	 * within the compiled method?
	 *
	 * @return {@code true} if the instruction has a valid address, {@code
	 *         false} otherwise.
	 */
	final boolean hasValidAddress ()
	{
		return address != invalidAddress;
	}

	/** An empty array of {@linkplain JavaOperand operands}. */
	static final JavaOperand[] noOperands = {};

	/**
	 * Answer the {@linkplain JavaOperand operands} consumed by the {@linkplain
	 * JavaInstruction instruction}.
	 *
	 * @return The operands consumed by the instruction.
	 */
	abstract JavaOperand[] inputOperands ();

	/**
	 * Answer the {@linkplain JavaOperand operands} produced by the {@linkplain
	 * JavaInstruction instruction}.
	 *
	 * @param operands
	 *        The operand stack before the instruction consumes its input
	 *        operands.
	 * @return The operands produced by the instruction.
	 */
	abstract JavaOperand[] outputOperands (List<JavaOperand> operands);

	/** The {@linkplain JavaOperand operand} stack. */
	private @Nullable List<JavaOperand> operandStack;

	/**
	 * Answer the expected state of the {@linkplain JavaOperand operand} stack
	 * at the start of this {@linkplain JavaInstruction instruction}.
	 *
	 * @return The operand stack.
	 */
	@Nullable List<JavaOperand> operandStack ()
	{
		return operandStack;
	}

	/**
	 * Set the expected state of the {@linkplain JavaOperand operand} stack at
	 * the start of this {@linkplain JavaInstruction instruction}.
	 *
	 * @param operandStack The operand stack.
	 */
	void setOperandStack (final List<JavaOperand> operandStack)
	{
		this.operandStack = operandStack;
	}

	/**
	 * Can this {@linkplain JavaInstruction instruction} type-safely consume
	 * its {@linkplain JavaOperand operands} from the specified operand stack?
	 *
	 * @param operands
	 *        An operand stack.
	 * @return {@code true} if the instruction can consume its operands, {@code
	 *         false} otherwise.
	 */
	boolean canConsumeOperands (final List<JavaOperand> operands)
	{
		try
		{
			final JavaOperand[] inputOperands = inputOperands();
			for (int i = 0,
					j = operands.size() - 1,
					size = inputOperands.length;
				i < size;
				i++, j--)
			{
				if (inputOperands[i].baseOperand()
					!= operands.get(j).baseOperand())
				{
					return false;
				}
			}
			return true;
		}
		catch (final IndexOutOfBoundsException e)
		{
			return false;
		}
	}

	/**
	 * Answer the size of the {@linkplain JavaInstruction instruction}, in
	 * bytes.
	 *
	 * @return The size of the instruction, in bytes.
	 */
	abstract int size ();

	/**
	 * Does the {@linkplain JavaInstruction instruction} represent a label?
	 *
	 * @return {@code true} if the instruction represents a label, {@code
	 *         false} otherwise.
	 */
	boolean isLabel ()
	{
		return false;
	}

	/**
	 * Does the {@linkplain JavaInstruction instruction} affect a {@code
	 * return}?
	 *
	 * @return {@code true} if the instruction affects a return, {@code false}
	 *         otherwise.
	 */
	boolean isReturn ()
	{
		return false;
	}

	/**
	 * Does the {@linkplain JavaBytecode bytecode} create a branch?
	 *
	 * @return {@code true} if the bytecode creates a branch, {@code false}
	 *         otherwise.
	 */
	public boolean isBranch ()
	{
		return false;
	}

	/**
	 * Can the {@linkplain JavaBytecode bytecode} pass control to the next
	 * bytecode?
	 *
	 * @return {@code true} if the bytecode can fall through, {@code false}
	 * 		otherwise.
	 */
	public boolean canFallThrough ()
	{
		return true;
	}

	/**
	 * Write the appropriate {@linkplain JavaBytecode bytecode} to the specified
	 * {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	abstract void writeBytecodeTo (DataOutput out) throws IOException;

	/**
	 * Write any immediate values required by the {@linkplain JavaBytecode
	 * bytecode} to the specified {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	abstract void writeImmediatesTo (DataOutput out) throws IOException;

	/**
	 * Write the {@linkplain JavaInstruction instruction} to the specified
	 * {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	final void writeTo (final DataOutput out) throws IOException
	{
		writeBytecodeTo(out);
		writeImmediatesTo(out);
	}
}
