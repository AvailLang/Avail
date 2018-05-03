/*
 * Deserializer.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.AvailRuntime;
import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Deserializer} takes a stream of bytes and reconstructs objects that
 * had been previously {@linkplain Serializer#serialize(A_BasicObject)
 * serialized} with a {@link Serializer}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class Deserializer extends AbstractDeserializer
{
	/**
	 * The objects that have been assembled so far.
	 */
	private final List<AvailObject> assembledObjects =
		new ArrayList<>(1000);

	/**
	 * The most recent object produced by deserialization.
	 */
	private @Nullable AvailObject producedObject;

	/**
	 * Look up the {@linkplain AvailRuntime#specialAtoms() special atom}.
	 *
	 * @param index The special atom's ordinal.
	 * @return The special atom known to the virtual machine's runtime.
	 */
	static A_Atom specialAtom (final int index)
	{
		return AvailRuntime.specialAtoms().get(index);
	}

	/**
	 * Record a newly reconstituted object.
	 *
	 * @param newObject The object that should be recorded.
	 */
	private void addObject (
		final AvailObject newObject)
	{
		assembledObjects.add(newObject);
	}

	/** A reusable buffer of operand objects. */
	private final AvailObject[] subobjectsBuffer =
		new AvailObject[SerializerOperation.maxSubobjects];

	/**
	 * Deserialize an object from the {@link #input} and return it.  If there
	 * are no more objects in the input then answer null.  If the stream is
	 * malformed throw a MalformedSerialStreamException.
	 *
	 * @return A fully deserialized object or {@code null}.
	 * @throws MalformedSerialStreamException
	 *         If the stream is malformed.
	 */
	public @Nullable AvailObject deserialize ()
		throws MalformedSerialStreamException
	{
		assert producedObject == null;
		try
		{
			if (input.available() == 0)
			{
				return null;
			}
			while (producedObject == null)
			{
				final int ordinal = readByte();
				final SerializerOperation operation =
					SerializerOperation.byOrdinal(ordinal);
				final SerializerOperand[] operands = operation.operands();
				for (int i = 0; i < operands.length; i++)
				{
					subobjectsBuffer[i] = operands[i].read(this);
				}
				final A_BasicObject newObject =
					operation.compose(subobjectsBuffer, this);
				newObject.makeImmutable();
				addObject((AvailObject) newObject);
			}
			final AvailObject temp = producedObject;
			producedObject = null;
			return temp;
		}
		catch (final Exception e)
		{
			throw new MalformedSerialStreamException(e);
		}
	}

	/**
	 * Construct a new {@code Deserializer}.
	 *
	 * @param input
	 *            An {@link InputStream} from which to reconstruct objects.
	 * @param runtime
	 *            The {@link AvailRuntime} from which to locate well-known
	 *            objects during deserialization.
	 */
	public Deserializer (
		final InputStream input,
		final AvailRuntime runtime)
	{
		super(input, runtime);
	}

	@Override
	AvailObject objectFromIndex (final int index)
	{
		return assembledObjects.get(index);
	}

	/**
	 * Record the provided object as an end product of deserialization.
	 *
	 * @param object The object that was produced.
	 */
	@Override
	void recordProducedObject (
		final AvailObject object)
	{
		assert producedObject == null;
		producedObject = object;
	}
}
