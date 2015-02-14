/**
 * StackMapTableAttribute.java
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

/**
 * The {@code StackMapTable} attribute is a variable-length attribute in the
 * {@linkplain Attribute attributes table} of a {@link CodeAttribute}. There may
 * be at most one {@code StackMapTable} attribute in the attributes table of a
 * {@code Code} attribute.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 * @see <a
 *     href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.4">
 *     The <code>StackMapTable</code> Attribute</a>
 */
class StackMapTableAttribute
extends Attribute
{
	/** The name of the {@link StackMapTableAttribute attribute}. */
	static final String name = "StackMapTable";

	@Override
	public String name ()
	{
		return name;
	}

	/** The list of {@linkplain StackMapFrame stack map frames}. */
	private final List<StackMapFrame> stackMapFrames;

	/**
	 * Construct a new {@link StackMapTableAttribute}.
	 *
	 * @param stackMapFrames
	 *        The list of {@linkplain StackMapFrame stack map frames}.s
	 */
	public StackMapTableAttribute (final List<StackMapFrame> stackMapFrames)
	{
		this.stackMapFrames = stackMapFrames;
	}

	@Override
	protected int size ()
	{
		int mySize = 2;
		for (int i = 0; i < stackMapFrames.size(); i++)
		{
			mySize = mySize + stackMapFrames.get(i).size();
		}
		return mySize;
	}

	@Override
	public void writeBodyTo (final DataOutput out) throws IOException
	{
		out.writeShort((short)stackMapFrames.size());

		for (int i = 0; i < stackMapFrames.size(); i++)
		{
			stackMapFrames.get(i).writeTo(out);
		}
	}
}
