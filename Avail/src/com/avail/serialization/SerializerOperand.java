/**
 * SerializerOperand.java
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

package com.avail.serialization;

import com.avail.descriptor.*;

/**
 * A {@code SerializerOperand} is part of a {@link SerializerOperation}.  It
 * indicates how to serialize part of an object already provided in an
 * appropriate form, and it knows how to describe the relationship between the
 * parent object and this part of it.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class SerializerOperand
{
	/**
	 * The {@linkplain SerializerOperandEncoding encoding} used by this operand.
	 */
	private final SerializerOperandEncoding operandEncoding;

	/**
	 * The name of the role this operand fulfills within its {@linkplain
	 * SerializerOperation operation}.
	 */
	private final String roleName;

	/**
	 * Construct a new {@link SerializerOperand}.
	 *
	 * @param operandEncoding
	 *            The {@linkplain SerializerOperandEncoding encoding} to use.
	 * @param roleName
	 *            The role this occupies in its containing {@linkplain
	 *            SerializerOperation operation}.
	 */
	SerializerOperand (
		final SerializerOperandEncoding operandEncoding,
		final String roleName)
	{
		this.operandEncoding = operandEncoding;
		this.roleName = roleName;
	}

	/**
	 * Trace the {@link AvailObject}, visiting any subobjects to ensure they
	 * will get a chance to be serialized before this object.  The object is
	 * potentially synthetic, perhaps representing just one aspect of the real
	 * object being traced.  For example, a {@link CompiledCodeDescriptor}
	 * object may produce a tuple of its literals for use by the operand
	 * responsible for literals, even though the actual representation does not
	 * use a separate tuple.
	 *
	 * @param object
	 *            The object to trace.
	 * @param serializer
	 *            The {@link Serializer} onto which to record the object's
	 *            parts.
	 */
	public void trace (final AvailObject object, final Serializer serializer)
	{
		operandEncoding.trace(object, serializer);
	}

	/**
	 * Write the {@link AvailObject}'s subobjects as described by the {@link
	 * #operandEncoding}.  As with {@link #trace(AvailObject, Serializer)}, the
	 * object may be synthetic, produced solely for pre-processing information
	 * for serialization of a single operand.
	 *
	 * @param object The object to deconstruct and write.
	 * @param serializer The serializer to which to write the object.
	 */
	public void write (final AvailObject object, final Serializer serializer)
	{
		operandEncoding.write(object, serializer);
	}

	/**
	 * Read an {@link AvailObject} from the {@link Deserializer} using the
	 * receiver's {@link #operandEncoding}.
	 *
	 * @param deserializer The deserializer from which to read an object.
	 * @return The newly decoded object.
	 */
	public AvailObject read (final Deserializer deserializer)
	{
		return (AvailObject)operandEncoding.read(deserializer);
	}

	@Override
	public String toString ()
	{
		return operandEncoding + "(" + roleName + ")";
	}
}
