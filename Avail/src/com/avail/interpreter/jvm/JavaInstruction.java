/**
 * JavaInstruction.java
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

/**
 * {@code JavaInstruction} is the abstract base for all fully reified Java
 * instructions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class JavaInstruction
{
	/** The canonical invalid address. */
	protected static long invalidAddress = Long.MAX_VALUE;

	/** The address of the label within the compiled method. */
	private long address = invalidAddress;

	/**
	 * Answer the address of the label within the compiled method.
	 *
	 * @return The address of the label within the compiled method.
	 */
	public final long address ()
	{
		assert address != invalidAddress;
		return address;
	}

	/**
	 * Set the address of the label.
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
	abstract boolean isLabel ();

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
