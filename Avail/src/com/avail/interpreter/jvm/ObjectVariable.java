/**
 * ObjectVariable.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
 * The {@link ObjectVariable Object_variable_info} item indicates that the
 * location has the verification type which is the class represented by the
 * {@link ConstantValueAttribute CONSTANT_Class_info} structure found in the
 * {@link ConstantPool constant_pool table} at the index given by {@code
 * cpool_index}.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class ObjectVariable
extends VerificationTypeInfo
{
	/**
	 *  The index into the {@link ConstantPool}
	 */
	private final short constantPoolIndex;

	/**
	 * Construct a new {@link IntegerVariable}.
	 *
	 * @param constantPoolIndex
	 *        The {@linkplain ConstantPool constant pool} index.
	 */
	ObjectVariable (final short cpoolIndex)
	{
		this.constantPoolIndex = cpoolIndex;
	}

	@Override
	protected int size ()
	{
		return 3;
	}

	@Override
	byte typeValue ()
	{
		return 7;
	}

	@Override
	void writeTo (final DataOutput out) throws IOException
	{
		out.writeByte(typeValue());
		out.writeByte(constantPoolIndex);
	}
}
