/**
 * ObjectTypeInfo.java
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
import com.avail.annotations.Nullable;

/**
 * The {@link ObjectTypeInfo Object_variable_info} item indicates that the
 * location has the verification type which is the class represented by the
 * {@link ConstantValueAttribute CONSTANT_Class_info} structure found in the
 * {@link ConstantPool constant_pool table} at the index given by {@code
 * cpool_index}.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
class ObjectTypeInfo
extends VerificationTypeInfo
{
	/**
	 * The {@linkplain JavaDescriptors class descriptor} of the referenced
	 * class.
	 */
	private final String classDescriptor;

	/**
	 * Construct a new {@link IntegerTypeInfo}.
	 *
	 * @param classDescriptor
	 *        The {@linkplain JavaDescriptors class descriptor} of the
	 *        referenced class.
	 */
	ObjectTypeInfo (final String classDescriptor)
	{
		this.classDescriptor = classDescriptor;
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
	JavaOperand baseOperand ()
	{
		return JavaOperand.OBJECTREF;
	}

	@Override
	public boolean isSubtypeOf (final VerificationTypeInfo other)
	{
		// TODO: Fix this! This is horribly wrong!
		if (equals(other))
		{
			return true;
		}
		if (other instanceof ObjectTypeInfo)
		{
			final ObjectTypeInfo info = (ObjectTypeInfo) other;
			if (info.classDescriptor.equals(
				JavaDescriptors.forType(Object.class)))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean equals (final @Nullable Object obj)
	{
		if (obj instanceof ObjectTypeInfo)
		{
			final ObjectTypeInfo other = (ObjectTypeInfo) obj;
			return classDescriptor.equals(other.classDescriptor);
		}
		return false;
	}

	@Override
	public int hashCode ()
	{
		// The magic number is a prime.
		return classDescriptor.hashCode() * 8235991;
	}

	@Override
	void writeTo (
			final DataOutput out,
			final ConstantPool constantPool)
		throws IOException
	{
		super.writeTo(out, constantPool);
		out.writeShort(constantPool.classConstant(classDescriptor).index);
	}

	@Override
	public String toString ()
	{
		return String.format("%s(%s)", baseOperand(), classDescriptor);
	}
}
