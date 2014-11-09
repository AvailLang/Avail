/**
 * Attributable.java
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@code Emitter} provides the basic facilities common to all emitters of
 * Java class file content. In particular, it provides support for access and
 * property {@linkplain Modifier modifiers} and {@linkplain Attribute
 * attributes}. Modifiers and attributes may only be set <strong>once</strong>;
 * e.g., a subsequent call to {@link #setModifiers(EnumSet) setModifiers} or
 * {@link #setAttribute(Attribute) setAttribute} will raise an {@link
 * AssertionError} (if assertions are enabled).
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <T>
 *        An implementation of {@link Modifier}.
 */
abstract class Emitter<T extends Enum<T> & Modifier>
{
	/** A {@linkplain ConstantPool constant pool}. */
	final ConstantPool constantPool;

	/** The access and property {@linkplain Modifier modifiers}. */
	final EnumSet<T> modifiers;

	/**
	 * Answer the access and property {@linkplain Modifier modifiers}.
	 *
	 * @return The access and property modifiers.
	 */
	public final Set<T> modifiers ()
	{
		return Collections.unmodifiableSet(modifiers);
	}

	/**
	 * Is the specified {@linkplain EnumSet set} of {@linkplain Modifier
	 * modifiers} valid?
	 *
	 * @param mods
	 *        The set of modifiers.
	 * @return {@code true} if the class described by the set of modifiers is
	 *         logically coherent, {@code false} otherwise.
	 */
	private boolean isValid (final EnumSet<T> mods)
	{
		try
		{
			return (boolean) validationMethod.invoke(null, mods);
		}
		catch (
			IllegalAccessException
			| IllegalArgumentException
			| InvocationTargetException e)
		{
			assert false : "This never happens!";
			throw new RuntimeException(e);
		}
	}

	/** Have the {@linkplain #modifiers modifiers} already been set? */
	private boolean hasSetModifiers = false;

	/**
	 * Set the access and property {@linkplain Modifier modifiers} for the
	 * {@linkplain Emitter receiver}.
	 *
	 * @param mods
	 *        The modifiers.
	 */
	public void setModifiers (final EnumSet<T> mods)
	{
		assert !hasSetModifiers;
		assert isValid(mods);
		modifiers.addAll(mods);
		hasSetModifiers = true;
	}

	/**
	 * The {@linkplain Method method} to invoke reflectively to validate the
	 * coherence of a {@linkplain EnumSet set} of {@linkplain Modifier
	 * modifiers}.
	 */
	private final Method validationMethod;

	/**
	 * The {@linkplain Method method} to invoke reflectively to compose a
	 * bitmask of a {@linkplain EnumSet set} of {@linkplain Modifier modifiers}.
	 */
	private final Method bitmaskMethod;

	/**
	 * Construct a new {@link Emitter}.
	 *
	 * @param constantPool
	 *        A {@linkplain ConstantPool constant pool}.
	 * @param modifierClass
	 *        The {@linkplain Class class} of the {@linkplain Modifier
	 *        modifier}.
	 * @param bootstrapModifiers
	 *        The modifiers to bootstrap.
	 */
	@SuppressWarnings("unchecked")
	Emitter (
		final ConstantPool constantPool,
		final Class<T> modifierClass,
		final Modifier... bootstrapModifiers)
	{
		this.constantPool = constantPool;
		modifiers = EnumSet.noneOf(modifierClass);
		for (final Modifier modifier : bootstrapModifiers)
		{
			modifiers.add((T) modifier);
		}
		// Bind the Modifier.isValid() method, used by isValid() below.
		try
		{
			Method method = modifierClass.getDeclaredMethod(
				"isValid", EnumSet.class);
			validationMethod = method;
			method = modifierClass.getDeclaredMethod(
				"bitmaskFor", EnumSet.class);
			bitmaskMethod = method;
		}
		catch (NoSuchMethodException | SecurityException e)
		{
			assert false : "This never happens!";
			throw new RuntimeException(e);
		}
		// Always include the Synthetic attribute.
		final SyntheticAttribute attribute = new SyntheticAttribute();
		setAttribute(attribute);
	}

	/**
	 * An {@linkplain LinkedHashMap ordered map} from attribute names to
	 * {@linkplain Attribute attributes}.
	 */
	private final LinkedHashMap<String, Attribute> attributes =
		new LinkedHashMap<>();

	/**
	 * Answer an {@linkplain Collections#unmodifiableCollection(Collection)
	 * unmodifiable} {@linkplain Map map} of the current {@linkplain Attribute
	 * attributes}.
	 *
	 * @return The current attributes.
	 */
	Map<String, Attribute> attributes ()
	{
		return Collections.unmodifiableMap(attributes);
	}

	/**
	 * Set the specified {@linkplain Attribute attribute} for the {@linkplain
	 * Emitter emitter}.
	 *
	 * @param attribute
	 *        An attribute.
	 */
	void setAttribute (final Attribute attribute)
	{
		final String name = attribute.name();
		assert !attributes.containsKey(name);
		constantPool.utf8(name);
		attributes.put(name, attribute);
	}

	/**
	 * Force the {@linkplain Emitter emitter} to be {@linkplain Deprecated
	 * deprecated}.
	 */
	public void beDeprecated ()
	{
		assert !attributes().containsKey(DeprecatedAttribute.name);
		final Attribute attribute = new DeprecatedAttribute();
		setAttribute(attribute);
	}

	/**
	 * Write any necessary header information to the specified {@linkplain
	 * DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	void writeHeaderTo (final DataOutput out) throws IOException
	{
		// Do nothing.
	}

	/**
	 * Write the {@linkplain Modifier modifiers} to the specified {@linkplain
	 * DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	private void writeModifiersTo (final DataOutput out) throws IOException
	{
		try
		{
			final int bitmask = (int) bitmaskMethod.invoke(null, modifiers);
			assert (bitmask & 65535) == bitmask;
			out.writeShort(bitmask);
		}
		catch (
			IllegalAccessException
			| IllegalArgumentException
			| InvocationTargetException e)
		{
			assert false : "This never happens!";
			throw new RuntimeException(e);
		}
	}

	/**
	 * Write the main body of the data accumulated by the {@linkplain
	 * Emitter emitter} to the specified {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	abstract void writeBodyTo (DataOutput out) throws IOException;

	/**
	 * Write the size-prefixed table of {@linkplain Attribute attributes} to the
	 * specified {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	private void writeAttributesTo (final DataOutput out) throws IOException
	{
		out.writeShort(attributes().size());
		for (final Attribute attribute : attributes.values())
		{
			attribute.writeTo(out, constantPool);
		}
	}

	/**
	 * Write the {@linkplain #writeHeaderTo(DataOutput) header}, {@linkplain
	 * #writeModifiersTo(DataOutput) modifiers}, {@linkplain
	 * #writeBodyTo(DataOutput) main body}, and {@linkplain
	 * #writeAttributesTo(DataOutput) attributes} to the specified {@linkplain
	 * DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	final void writeTo (final DataOutput out) throws IOException
	{
		writeHeaderTo(out);
		writeModifiersTo(out);
		writeBodyTo(out);
		writeAttributesTo(out);
	}
}
