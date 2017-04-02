/**
 * Deserializer.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import com.avail.descriptor.*;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * A {@link Deserializer} takes a stream of bytes and reconstructs objects that
 * had been previously {@linkplain Serializer#serialize(A_BasicObject)
 * serialized} with a {@link Serializer}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class Deserializer
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
	 * The {@link AvailRuntime} whose scope is used to decode references to
	 * constructs that need to be looked up rather than re-instantiated.
	 */
	private final AvailRuntime runtime;

	/**
	 * The current {@linkplain ModuleDescriptor module}.
	 */
	private @Nullable A_Module currentModule;

	/**
	 * Answer the deserializer's instance of {@link AvailRuntime} used for
	 * linking deserialized objects to existing important objects like
	 * {@linkplain MethodDescriptor methods} and {@linkplain AtomDescriptor
	 * atoms}.
	 *
	 * @return The {@code AvailRuntime}.
	 */
	AvailRuntime runtime ()
	{
		return runtime;
	}

	/**
	 * Look up the {@linkplain AvailRuntime#specialObject(int) special object}.
	 *
	 * @param index The special object's ordinal.
	 * @return The special object known to the virtual machine's runtime.
	 */
	static AvailObject specialObject (final int index)
	{
		return AvailRuntime.specialObject(index);
	}

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
	 * Convert an index into an object.  The object must already have been
	 * assembled.
	 *
	 * @param index The zero-based index at which to fetch the object.
	 * @return The already constructed object at the specified index.
	 */
	AvailObject objectFromIndex (final int index)
	{
		return assembledObjects.get(index);
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

	/**
	 * Look up the module of the receiver's {@link AvailRuntime} which has the
	 * given name.
	 *
	 * @param moduleName The {@link StringDescriptor name} of the module.
	 * @return The module with the specified name.
	 */
	A_Module moduleNamed (
		final A_String moduleName)
	{
		assert moduleName.isString();
		final A_Module current = currentModule;
		if (current != null && moduleName.equals(current.moduleName()))
		{
			return current;
		}
		if (!runtime.includesModuleNamed(moduleName))
		{
			throw new RuntimeException(
				"Cannot locate module named \"" + moduleName.toString() + "\"");
		}
		return runtime.moduleAt(moduleName);
	}

	/**
	 * Set which module is currently being defined.  This should not be a module
	 * of the current {@link #runtime}.
	 *
	 * @param module The active {@link ModuleDescriptor module}.
	 */
	public void currentModule (final A_Module module)
	{
		currentModule = module;
	}

	/**
	 * Return the {@link ModuleDescriptor module} currently being defined.
	 *
	 * @return The current module.
	 */
	@Nullable A_Module currentModule ()
	{
		return currentModule;
	}

	/** The maximum number of operands of any SerializerOperation. */
	private static final int maxSubobjects;

	static
	{
		int max = 0;
		for (SerializerOperation operation : SerializerOperation.values())
		{
			max = Math.max(max, operation.operands().length);
		}
		maxSubobjects = max;
	}

	/** A reusable buffer of operand objects. */
	private final AvailObject[] subobjectsBuffer =
		new AvailObject[maxSubobjects];

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
					SerializerOperation.values()[ordinal];
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
	 * The stream from which bytes are read.
	 */
	private final InputStream input;

	/**
	 * Construct a new {@link Deserializer}.
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
		this.input = input;
		this.runtime = runtime;
	}

	/**
	 * Record the provided object as an end product of deserialization.
	 *
	 * @param object The object that was produced.
	 */
	void recordProducedObject (
		final AvailObject object)
	{
		assert producedObject == null;
		producedObject = object;
	}

	/**
	 * Consume an unsigned byte from the input.  Return it as an {@code int} to
	 * ensure it's unsigned, i.e., 0 ≤ b ≤ 255.
	 *
	 * @return An {@code int} containing the unsigned byte (0..255).
	 */
	int readByte ()
	{
		try
		{
			return input.read();
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Consume an unsigned short from the input in big endian order.  Return it
	 * as an {@code int} to ensure it's unsigned, i.e., 0 ≤ b ≤ 65535.
	 *
	 * @return An {@code int} containing the unsigned short (0..65535).
	 */
	int readShort ()
	{
		try
		{
			return (input.read() << 8) + input.read();
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Consume an int from the input in big endian order.
	 *
	 * @return An {@code int} extracted from the input.
	 */
	int readInt ()
	{
		try
		{
			return
				(input.read() << 24) +
				(input.read() << 16) +
				(input.read() << 8) +
				input.read();
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}
}
