/**
 * CodeAttribute.java
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
import java.util.List;

/**
 * A {@code CodeAttribute} contains the Java Virtual Machine instructions and
 * auxiliary information for a single method, instance initialization method,
 * or class or interface initialization method.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a
 *     href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.3">
 *     The <code>Code</code> Attribute</a>
 */
final class CodeAttribute
extends Attribute
{
	/**
	 * The {@linkplain Method method} for which {@code this} is the {@link
	 * CodeAttribute Code} {@linkplain Attribute attribute}.
	 */
	private final Method method;

	/** The auxiliary {@linkplain Attribute attributes}. */
	private final List<Attribute> attributes;

	/** The name of this {@linkplain CodeAttribute attribute}. */
	static final String name = "Code";

	@Override
	public String name ()
	{
		return name;
	}

	@Override
	protected int size ()
	{
		// The magic number accounts for the 2-byte max stack, 2-byte max
		// locals, 4-byte code size, 2-byte exception table size, and 2-byte
		// attributes count.
		return 12
			+ method.codeSize()
			+ method.exceptionTable().size()
			+ attributes.size();
	}

	@Override
	public void writeBodyTo (final DataOutput out) throws IOException
	{
		out.writeShort(method.maxStackDepth());
		out.writeShort(method.maxLocals());
		method.writeInstructionsTo(out);
		method.exceptionTable().writeTo(out);
		out.writeShort(attributes.size());
		for (final Attribute attribute : attributes)
		{
			attribute.writeTo(out, method.constantPool);
		}
	}

	/**
	 * Construct a new {@link CodeAttribute}.
	 *
	 * @param method
	 *        The {@linkplain Method method} for which the new object is the
	 *        {@link CodeAttribute Code} {@linkplain Attribute attribute}.
	 * @param attributes
	 *        The auxiliary attributes.
	 */
	public CodeAttribute (final Method method, final List<Attribute> attributes)
	{
		this.method = method;
		this.attributes = attributes;
	}
}
