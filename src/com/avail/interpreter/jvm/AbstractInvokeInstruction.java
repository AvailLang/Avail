/**
 * AbstractInvokeInstruction.java
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

import static com.avail.interpreter.jvm.JavaDescriptors.*;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.avail.interpreter.jvm.ConstantPool.MethodrefEntry;
import com.avail.interpreter.jvm.ConstantPool.RefEntry;

/**
 * An {@code AbstractInvokeInstruction} provides behavior common to the
 * different invoke instructions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class AbstractInvokeInstruction
extends SimpleInstruction
{
	/** The {@linkplain MethodrefEntry method entry} for the target method. */
	final RefEntry methodEntry;

	/**
	 * Answer the {@linkplain VerificationTypeInfo parameters} expected by the
	 * referenced method.
	 *
	 * @return The expected parameters.
	 */
	List<VerificationTypeInfo> parameters ()
	{
		final List<VerificationTypeInfo> parameters =
			parameterOperands(methodEntry.descriptor());
		parameters.add(
			0,
			JavaOperand.OBJECTREF.create(methodEntry.classDescriptor()));
		return parameters;
	}

	@Override
	final boolean canConsumeOperands (final List<VerificationTypeInfo> operands)
	{
		final List<VerificationTypeInfo> parameters = parameters();
		final int size = operands.size();
		try
		{
			final int parametersSize = parameters.size();
			final List<VerificationTypeInfo> topOperands = operands.subList(
				size - parametersSize, size);
			for (int i = 0; i < parametersSize; i++)
			{
				if (!topOperands.get(i).isSubtypeOf(parameters.get(i)))
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

	@Override
	final VerificationTypeInfo[] inputOperands ()
	{
		final List<VerificationTypeInfo> parameters = parameters();
		return parameters.toArray(new VerificationTypeInfo[parameters.size()]);
	}

	@Override
	final VerificationTypeInfo[] outputOperands (
		final List<VerificationTypeInfo> operandStack)
	{
		final List<VerificationTypeInfo> operands = new ArrayList<>(1);
		final VerificationTypeInfo returnOperand = returnOperand(
			methodEntry.descriptor());
		if (returnOperand != null)
		{
			operands.add(returnOperand);
		}
		return operands.toArray(new VerificationTypeInfo[operands.size()]);
	}

	@Override
	void writeImmediatesTo (final DataOutput out) throws IOException
	{
		methodEntry.writeIndexTo(out);
	}

	@Override
	public final String toString ()
	{
		return String.format(
			"%s%s",
			super.toString(),
			methodEntry.simpleString());
	}

	/**
	 * Construct a new {@link AbstractInvokeInstruction}.
	 *
	 * @param bytecode
	 *        The {@linkplain JavaBytecode bytecode}.
	 * @param methodEntry
	 *        The {@linkplain MethodrefEntry method entry} for the target
	 *        method.
	 */
	AbstractInvokeInstruction (
		final JavaBytecode bytecode,
		final RefEntry methodEntry)
	{
		super(bytecode);
		this.methodEntry = methodEntry;
	}
}
