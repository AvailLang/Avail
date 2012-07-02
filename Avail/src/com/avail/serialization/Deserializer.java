/**
 * Deserializer.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import java.io.*;
import java.util.*;

/**
 * A {@link Deserializer} takes a stream of bytes and reconstructs objects that
 * had been previously {@linkplain Serializer#serialize(AvailObject) serialized}
 * with a {@link Serializer}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class Deserializer
{
	/**
	 * The objects that have been assembled so far.
	 */
	protected final List<AvailObject> assembledObjects =
		new ArrayList<AvailObject>(1000);

	/**
	 * The most recently object produced by deserialization.
	 */
	protected AvailObject producedObject;

	/**
	 * The {@link AvailRuntime} whose scope is used to decode references to
	 * constructs that need to be looked up rather than re-instantiated.
	 */
	private final AvailRuntime runtime;

	/**
	 * The current {@linkplain ModuleDescriptor module}.
	 */
	private AvailObject currentModule;

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
	protected @NotNull AvailObject specialObject (final int index)
	{
		return AvailRuntime.specialObject(index);
	}

	/**
	 * Look up the {@linkplain AvailRuntime#specialAtoms() special atom}.
	 *
	 * @param index The special atom's ordinal.
	 * @return The special atom known to the virtual machine's runtime.
	 */
	protected @NotNull AvailObject specialAtom (final int index)
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
	protected @NotNull AvailObject objectFromIndex (final int index)
	{
		return assembledObjects.get(index);
	}

	/**
	 * Record a newly reconstituted object.
	 *
	 * @param newObject The object that should be recorded.
	 */
	protected void addObject (
		final @NotNull AvailObject newObject)
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
	public @NotNull AvailObject moduleNamed (
		final @NotNull AvailObject moduleName)
	{
		assert moduleName.isString();
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
	public void currentModule (
		final @NotNull AvailObject module)
	{
		currentModule = module;
	}

	/**
	 * Return the {@link ModuleDescriptor module} currently being defined.
	 *
	 * @return The current module.
	 */
	public @NotNull AvailObject currentModule ()
	{
		return currentModule;
	}

	/**
	 * Deserialize an object from the {@link #input} and return it.  If there
	 * are no more objects in the input then answer nil.  If the stream is
	 * malformed throw a MalformedSerialStreamException.
	 *
	 * @return A fully deserialized object or null.
	 * @throws MalformedSerialStreamException If the stream is malformed.
	 */
	public AvailObject deserialize ()
	throws MalformedSerialStreamException
	{
		try
		{
			if (input.available() == 0)
			{
				return null;
			}
			while (producedObject == null)
			{
				SerializerOperation.readObject(this);
			}
			return producedObject;
		}
		catch (final IOException e)
		{
			throw new MalformedSerialStreamException(e);
		}
	}

	/**
	 * The stream from which bytes are read.
	 */
	private final @NotNull InputStream input;

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
		final @NotNull InputStream input,
		final @NotNull AvailRuntime runtime)
	{
		this.input = input;
		this.runtime = runtime;
	}

	/**
	 * Record the provided object as an end product of deserialization.
	 *
	 * @param object The object that was produced.
	 */
	protected void recordProducedObject (
		final @NotNull AvailObject object)
	{
		producedObject = object;
	}

	/**
	 * Consume an unsigned byte from the input.  Return it as an {@code int} to
	 * ensure it's unsigned, i.e., 0 ≤ b ≤ 255.
	 *
	 * @return An {@code int} containing the unsigned byte (0..255).
	 */
	protected int readByte ()
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
	protected int readShort ()
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
	protected int readInt ()
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
