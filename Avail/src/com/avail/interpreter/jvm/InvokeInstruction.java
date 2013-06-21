/**
 * InvokeInstruction.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.interpreter.jvm.JavaDescriptors.parameterOperands;
import static com.avail.interpreter.jvm.JavaDescriptors.returnOperand;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.avail.interpreter.jvm.ConstantPool.MethodrefEntry;

/**
 * The immediate value of an {@code InvokeInstruction} is the index of a
 * {@linkplain MethodrefEntry method entry} within the {@linkplain ConstantPool
 * constant pool}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class InvokeInstruction
extends SimpleInstruction
{
	/** The {@linkplain MethodrefEntry method entry} for the target method. */
	private final MethodrefEntry methodEntry;

	@Override
	JavaOperand[] inputOperands ()
	{
		final List<JavaOperand> operands;
		switch (bytecode())
		{
			case invokestatic:
				operands = parameterOperands(methodEntry.descriptor());
				break;
			case invokeinterface:
			case invokespecial:
			case invokevirtual:
				operands = parameterOperands(methodEntry.descriptor());
				operands.add(0, JavaOperand.OBJECTREF);
				break;
			default:
				assert false;
				throw new IllegalStateException();
		}
		return operands.toArray(new JavaOperand[operands.size()]);
	}

	@Override
	JavaOperand[] outputOperands ()
	{
		final List<JavaOperand> operands = new ArrayList<>(1);
		final JavaOperand returnOperand = returnOperand(
			methodEntry.descriptor());
		if (returnOperand != null)
		{
			operands.add(returnOperand);
		}
		return operands.toArray(new JavaOperand[operands.size()]);
	}

	@Override
	void writeImmediatesTo (final DataOutput out) throws IOException
	{
		methodEntry.writeIndexTo(out);
	}

	@Override
	public String toString ()
	{
		return String.format("%s%s", super.toString(), methodEntry);
	}

	/**
	 * Construct a new {@link InvokeInstruction}.
	 *
	 * @param bytecode
	 *        The {@linkplain JavaBytecode bytecode}.
	 * @param methodEntry
	 *        The {@linkplain MethodrefEntry method entry} for the target
	 *        method.
	 */
	public InvokeInstruction (
		final JavaBytecode bytecode,
		final MethodrefEntry methodEntry)
	{
		super(bytecode);
		this.methodEntry = methodEntry;
	}
}
