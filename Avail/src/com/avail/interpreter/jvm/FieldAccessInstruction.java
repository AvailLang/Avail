/**
 * FieldAccessInstruction.java
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
import java.util.Arrays;
import java.util.List;
import com.avail.interpreter.jvm.ConstantPool.FieldrefEntry;

/**
 * The immediate of a {@code FieldAccessInstruction} is the 2-byte index of a
 * {@linkplain FieldrefEntry field reference} in the {@linkplain ConstantPool
 * constant pool}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class FieldAccessInstruction
extends JavaInstruction
{
	/**
	 * The {@linkplain FieldrefEntry field reference entry} for the referenced
	 * {@linkplain Field field}.
	 */
	private final FieldrefEntry fieldrefEntry;

	/** Is the referenced {@linkplain Field field} {@code static}? */
	private final boolean isStatic;

	@Override
	int size ()
	{
		return 3;
	}

	/**
	 * Answer the appropriate static accessor {@linkplain JavaBytecode
	 * bytecode} for this {@linkplain FieldrefEntry field reference entry}.
	 *
	 * @return The appropriate bytecode.
	 */
	abstract JavaBytecode staticBytecode ();

	/**
	 * Answer the appropriate instance accessor {@linkplain JavaBytecode
	 * bytecode} for this {@linkplain FieldrefEntry field reference entry}.
	 *
	 * @return The appropriate bytecode.
	 */
	abstract JavaBytecode instanceBytecode ();

	/**
	 * Answer the appropriate {@linkplain JavaBytecode bytecode} for the
	 * {@linkplain FieldAccessInstruction instruction}.
	 *
	 * @return The appropriate bytecode.
	 */
	private JavaBytecode bytecode ()
	{
		return isStatic ? staticBytecode() : instanceBytecode();
	}

	/**
	 * Quasi-destructively update the specified array to replace occurrences of
	 * {@link JavaOperand#VALUE VALUE} with the {@linkplain JavaOperand operand}
	 * that corresponds with the {@linkplain FieldrefEntry referenced}
	 * {@linkplain Field field}'s {@linkplain Class type}.
	 *
	 * @param operands
	 *        An array of operands.
	 * @return An array of operands that reflects the substitution.
	 */
	private JavaOperand[] substituteOperands (final JavaOperand[] operands)
	{
		JavaOperand[] result = operands;
		boolean copied = false;
		for (int i = 0; i < operands.length; i++)
		{
			if (operands[i] == JavaOperand.VALUE)
			{
				// Copy the operands on write.
				if (!copied)
				{
					result = Arrays.copyOf(operands, operands.length);
					copied = true;
				}
				final Class<?> type = JavaDescriptors.typeForDescriptor(
					fieldrefEntry.descriptor());
				final JavaOperand newOperand = JavaOperand.forType(type);
				result[i] = newOperand;
			}
		}
		return result;
	}

	@Override
	final JavaOperand[] inputOperands ()
	{
		return substituteOperands(bytecode().inputOperands());
	}

	@Override
	final JavaOperand[] outputOperands (final List<JavaOperand> operandStack)
	{
		return substituteOperands(bytecode().outputOperands());
	}

	@Override
	final void writeBytecodeTo (final DataOutput out) throws IOException
	{
		bytecode().writeTo(out);
	}

	@Override
	final void writeImmediatesTo (final DataOutput out) throws IOException
	{
		fieldrefEntry.writeIndexTo(out);
	}

	@Override
	public final String toString ()
	{
		return String.format(
			"%-15s%s",
			bytecode().mnemonic(),
			fieldrefEntry.simpleString());
	}

	/**
	 * Construct a new {@link FieldAccessInstruction}.
	 *
	 * @param fieldrefEntry
	 *        A {@linkplain FieldrefEntry field reference entry} for the
	 *        referenced {@linkplain Field field}.
	 * @param isStatic
	 *        {@code true} if the referenced field is {@code static}, {@code
	 *        false} otherwise.
	 */
	FieldAccessInstruction (
		final FieldrefEntry fieldrefEntry,
		final boolean isStatic)
	{
		this.fieldrefEntry = fieldrefEntry;
		this.isStatic = isStatic;
	}
}
