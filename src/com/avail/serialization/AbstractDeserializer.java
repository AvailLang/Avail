/*
 * AbstractDeserializer.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.A_String;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.MethodDescriptor;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.NilDescriptor;
import com.avail.descriptor.StringDescriptor;

import java.io.IOException;
import java.io.InputStream;

import static com.avail.descriptor.NilDescriptor.nil;

/**
 * An {@code AbstractDeserializer} consumes a stream of bytes to reconstruct
 * objects that had been previously {@linkplain Serializer#serialize(
 * A_BasicObject) serialized} with a {@link Serializer}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class AbstractDeserializer
{
	/**
	 * The {@link AvailRuntime} whose scope is used to decode references to
	 * constructs that need to be looked up rather than re-instantiated.
	 */
	protected final AvailRuntime runtime;

	/**
	 * The stream from which bytes are read.
	 */
	protected final InputStream input;

	/**
	 * The current {@linkplain ModuleDescriptor module}.
	 */
	private A_Module currentModule = nil;

	/**
	 * Construct a new {@code Deserializer}.
	 *
	 * @param input
	 *            An {@link InputStream} from which to reconstruct objects.
	 * @param runtime
	 *            The {@link AvailRuntime} from which to locate well-known
	 *            objects during deserialization.
	 */
	public AbstractDeserializer (
		final InputStream input,
		final AvailRuntime runtime)
	{
		this.input = input;
		this.runtime = runtime;
	}

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
	 * Consume an unsigned byte from the input.  Return it as an {@code int} to
	 * ensure it's unsigned, i.e., 0 ≤ b ≤ 255.
	 *
	 * @return An {@code int} containing the unsigned byte (0..255).
	 */
	public int readByte ()
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
	public int readShort ()
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
	public int readInt ()
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
		if (!current.equalsNil() && moduleName.equals(current.moduleName()))
		{
			return current;
		}
		if (!runtime.includesModuleNamed(moduleName))
		{
			throw new RuntimeException(
				"Cannot locate module named " + moduleName);
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
	 * Return the {@link ModuleDescriptor module} currently being defined, or
	 * {@link NilDescriptor#nil} if there isn't one.
	 *
	 * @return The current module or {@code nil}.
	 */
	A_Module currentModule ()
	{
		return currentModule;
	}

	/**
	 * Convert an index into an object.  The object must already have been
	 * assembled.
	 *
	 * @param index The zero-based index at which to fetch the object.
	 * @return The already constructed object at the specified index.
	 */
	abstract AvailObject objectFromIndex (final int index);

	/**
	 * Record the provided object as an end product of deserialization.
	 *
	 * @param object The object that was produced.
	 */
	abstract void recordProducedObject (final AvailObject object);
}
