/**
 * InvokeInterfaceInstruction.java
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

import java.io.DataOutput;
import java.io.IOException;
import com.avail.interpreter.jvm.ConstantPool.InterfaceMethodrefEntry;

/**
 * The immediate values of an {@code InvokeInterfaceInstruction} are the index
 * of an {@linkplain InterfaceMethodrefEntry interface method entry} within the
 * {@linkplain ConstantPool constant pool}, the {@linkplain
 * JavaDescriptors#argumentUnits(String) argument units}, and an 8-bit
 * {@code 0}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class InvokeInterfaceInstruction
extends SimpleInstruction
{
	/**
	 * The {@linkplain InterfaceMethodrefEntry interface method entry} for the
	 * target method.
	 */
	private final InterfaceMethodrefEntry methodEntry;

	@Override
	void writeImmediatesTo (final DataOutput out) throws IOException
	{
		methodEntry.writeIndexTo(out);
		methodEntry.writeArgumentUnitsTo(out);
		out.writeByte(0);
	}

	@Override
	public String toString ()
	{
		return String.format("%s%s", super.toString(), methodEntry);
	}

	/**
	 * Construct a new {@link InvokeInterfaceInstruction}.
	 *
	 * @param methodEntry
	 *        The {@linkplain InterfaceMethodrefEntry interface method entry}
	 *        for the target method.
	 */
	public InvokeInterfaceInstruction (
		final InterfaceMethodrefEntry methodEntry)
	{
		super(JavaBytecode.invokeinterface);
		this.methodEntry = methodEntry;
	}
}
