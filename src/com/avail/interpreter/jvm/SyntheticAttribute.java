/**
 * SyntheticAttribute.java
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
 * A class member that does not appear in the source code must be marked using a
 * {@code SyntheticAttribute}, or else it must have the {@code ACC_SYNTHETIC}
 * flag set.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a
 *     href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.8">
 *     The <code>Synthetic</code> Attribute</a>
 */
final class SyntheticAttribute
extends Attribute
{
	/** The name of the {@link SyntheticAttribute attribute}. */
	static final String name = "Synthetic";

	@Override
	public String name ()
	{
		return name;
	}

	@Override
	protected int size ()
	{
		return 0;
	}

	@Override
	public void writeBodyTo (
			final DataOutput out,
			final ConstantPool constantPool)
		throws IOException
	{
		// No body.
	}

	@Override
	public String toString ()
	{
		return name;
	}
}
