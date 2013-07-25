/**
 * ConstantValueAttribute.java
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
import com.avail.interpreter.jvm.ConstantPool.Entry;

/**
 * A {@code ConstantValueAttribute} represents the value of a constant
 * {@linkplain Field field}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a
 *     href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.2">
 *     The <code>ConstantValue</code> Attribute</a>
 */
final class ConstantValueAttribute
extends Attribute
{
	/** The {@linkplain Entry entry} for the initial value. */
	private final Entry initialValueEntry;

	/**
	 * Answer the {@linkplain Entry entry} for the initial value.
	 *
	 * @return The entry for the initial value.
	 */
	Entry initialValueEntry ()
	{
		return initialValueEntry;
	}

	/** The name of this {@linkplain ConstantValueAttribute attribute}. */
	static final String name = "ConstantValue";

	@Override
	public String name ()
	{
		return name;
	}

	@Override
	protected int size ()
	{
		return 2;
	}

	@Override
	public void writeBodyTo (final DataOutput out) throws IOException
	{
		initialValueEntry.writeIndexTo(out);
	}

	/**
	 * Construct a new {@link ConstantValueAttribute}.
	 *
	 * @param initialValueEntry
	 *        The {@linkplain Entry entry} for the initial value.
	 */
	public ConstantValueAttribute (final Entry initialValueEntry)
	{
		this.initialValueEntry = initialValueEntry;
	}
}
