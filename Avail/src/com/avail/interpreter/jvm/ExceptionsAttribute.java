/**
 * ExceptionsAttribute.java
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
import java.util.Formatter;
import java.util.List;
import com.avail.interpreter.jvm.ConstantPool.ClassEntry;

/**
 * An {@code ExceptionsAttribute} indicates which checked exceptions a method
 * may throw.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class ExceptionsAttribute
extends Attribute
{
	/** The name of this {@linkplain ExceptionsAttribute attribute}. */
	static final String name = "Exceptions";

	@Override
	public String name ()
	{
		return name;
	}

	/**
	 * The {@linkplain ClassEntry class entries} for the {@linkplain Throwable
	 * checked exceptions}.
	 */
	private final List<ClassEntry> throwableEntries;

	@Override
	protected int size ()
	{
		// The magic number accounts for the 2-byte table size.
		return 2 + 2 * throwableEntries.size();
	}

	@Override
	public void writeBodyTo (final DataOutput out) throws IOException
	{
		out.writeShort(throwableEntries.size());
		for (final ClassEntry entry : throwableEntries)
		{
			entry.writeIndexTo(out);
		}
	}

	@Override
	public String toString ()
	{
		@SuppressWarnings("resource")
		final Formatter formatter = new Formatter();
		formatter.format("%s:", name);
		for (final ClassEntry entry : throwableEntries)
		{
			formatter.format("%n\t%s", entry.name());
		}
		return formatter.toString();
	}

	/**
	 * Construct a new {@link ExceptionsAttribute}.
	 *
	 * @param throwableEntries
	 *        The {@linkplain ClassEntry class entries} for the {@linkplain
	 *        Throwable checked exceptions}.
	 */
	public ExceptionsAttribute (final List<ClassEntry> throwableEntries)
	{
		this.throwableEntries = throwableEntries;
	}
}
