/**
 * LocalVariable.java
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

/**
 * A {@code LocalVariable} represents a Java method parameter or local variable.
 * It knows its textual name, {@linkplain Class type}, and slot index, and
 * {@linkplain #isLive() liveness}. It can compute its {@linkplain #slotUnits()
 * slot consumption}. Upon the last usage of the local variable, the client
 * should {@linkplain #retire() retire} it.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class LocalVariable
{
	/** The name of the {@linkplain LocalVariable local variable}. */
	private final String name;

	/**
	 * Answer the name of the {@linkplain LocalVariable local variable}.
	 *
	 * @return The name of the local variable.
	 */
	public String name ()
	{
		return name;
	}

	/** The type descriptor of the {@linkplain LocalVariable local variable}. */
	private final String descriptor;

	/**
	 * Answer the type descriptor of the {@linkplain LocalVariable local
	 * variable}.
	 *
	 * @return The type descriptor of the local variable.
	 */
	public String descriptor ()
	{
		return descriptor;
	}

	/**
	 * Answer the number of slot units consumed by the {@linkplain LocalVariable
	 * local variable}.
	 *
	 * @return The number of slot units consumed.
	 */
	int slotUnits ()
	{
		return JavaDescriptors.slotUnits(descriptor);
	}

	/** The local variable index, measured in slot units. */
	final int index;

	/**
	 * Does the {@linkplain LocalVariable local variable} require a 16-bit
	 * index?
	 *
	 * @return {@code true} if the local variable requires a 16-bit, {@code
	 *         false} otherwise.
	 */
	public boolean isWide ()
	{
		return (index & 255) != index;
	}

	@Override
	public String toString ()
	{
		return String.format("%s [#%d] : %s", name, index, descriptor);
	}

	/**
	 * Construct a new {@link LocalVariable}.
	 *
	 * @param name
	 *        The name of the local variable.
	 * @param descriptor
	 *        The type descriptor of the local variable.
	 * @param index
	 *        The local variable index, measured in slot units.
	 */
	LocalVariable (final String name, final String descriptor, final int index)
	{
		this.name = name;
		this.descriptor = descriptor;
		this.index = index;
	}

	/** Is the {@linkplain LocalVariable local variable} still live? */
	private boolean isLive = true;

	/**
	 * Is the {@linkplain LocalVariable local variable} still live?
	 *
	 * @return {@code true} if the local variable is still live, {@code false}
	 *         otherwise.
	 */
	public boolean isLive ()
	{
		return isLive;
	}

	/**
	 * Retire the {@linkplain LocalVariable local variable}: it is no longer
	 * {@linkplain #isLive() live}.
	 */
	public void retire ()
	{
		isLive = false;
	}

	/**
	 * Write the index of the {@linkplain LocalVariable local variable} to the
	 * specified {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	void writeTo (final DataOutput out) throws IOException
	{
		if (isWide())
		{
			out.writeShort(index);
		}
		else
		{
			out.writeByte(index);
		}
	}
}
