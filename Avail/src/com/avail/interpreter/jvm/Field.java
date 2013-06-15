/**
 * Field.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.interpreter.jvm.FieldModifier.*;
import java.io.DataOutput;
import java.io.IOException;
import com.avail.interpreter.jvm.ConstantPool.Entry;
import com.avail.interpreter.jvm.ConstantPool.FieldrefEntry;
import com.avail.interpreter.jvm.ConstantPool.Utf8Entry;

/**
 * {@code Field} describes a Java field specified by the {@linkplain
 * CodeGenerator code generator}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class Field
extends Emitter<FieldModifier>
{
	/**
	 * The {@linkplain CodeGenerator code generator} that produced this
	 * {@linkplain Field field}.
	 */
	private final CodeGenerator codeGenerator;

	/** The name {@linkplain Utf8Entry entry}. */
	private final Utf8Entry nameEntry;

	/**
	 * Answer the name of the {@linkplain Field field}.
	 *
	 * @return The name of the field.
	 */
	public String name ()
	{
		return nameEntry.toString();
	}

	/** The descriptor {@linkplain Utf8Entry entry}. */
	private final Utf8Entry descriptorEntry;

	/**
	 * Answer the type descriptor of the {@linkplain Field field}.
	 *
	 * @return The type descriptor of the field.
	 */
	public String descriptor ()
	{
		return descriptorEntry.toString();
	}

	/**
	 * Construct a new {@link Field}.
	 *
	 * @param codeGenerator
	 *        The {@linkplain CodeGenerator code generator}.
	 * @param nameEntry
	 *        The field name.
	 * @param descriptorEntry
	 *        The field's type descriptor.
	 */
	Field (
		final CodeGenerator codeGenerator,
		final String nameEntry,
		final String descriptorEntry)
	{
		super(codeGenerator.constantPool, FieldModifier.class, SYNTHETIC);
		this.codeGenerator = codeGenerator;
		this.nameEntry = constantPool.utf8(nameEntry);
		this.descriptorEntry = constantPool.utf8(descriptorEntry);
	}

	/**
	 * Answer a {@linkplain FieldrefEntry field reference entry} for the
	 * {@linkplain Field receiver}.
	 *
	 * @return A field reference entry for this field.
	 */
	FieldrefEntry reference ()
	{
		return constantPool.fieldref(
			codeGenerator.classEntry.toString(),
			nameEntry.toString(),
			descriptorEntry.toString());
	}

	/**
	 * Can a {@linkplain ConstantValueAttribute constant value attribute} be
	 * supplied?
	 *
	 * @return {@code true} if the constant value entry can be set, {@code
	 *         false} otherwise.
	 */
	private boolean canSetConstantValue ()
	{
		return modifiers().contains(FINAL)
			&& modifiers().contains(STATIC)
			&& attributes().containsKey(ConstantValueAttribute.name);
	}

	/**
	 * Set the constant value for this {@linkplain Field field}.
	 *
	 * @param value
	 *        An {@code int}.
	 */
	public void setConstantValue (final int value)
	{
		assert canSetConstantValue();
		final Class<?> type = JavaDescriptors.toConstantType(descriptor());
		assert type == Integer.TYPE;
		final Entry constantValueEntry = constantPool.constant(value);
		final Attribute attribute =
			new ConstantValueAttribute(constantValueEntry);
		setAttribute(attribute);
	}

	/**
	 * Set the constant value for this {@linkplain Field field}.
	 *
	 * @param value
	 *        A {@code long}.
	 */
	public void setConstantValue (final long value)
	{
		assert canSetConstantValue();
		final Class<?> type = JavaDescriptors.toConstantType(descriptor());
		assert type == Long.TYPE;
		final Entry constantValueEntry = constantPool.constant(value);
		final Attribute attribute =
			new ConstantValueAttribute(constantValueEntry);
		setAttribute(attribute);
	}

	/**
	 * Set the constant value for this {@linkplain Field field}.
	 *
	 * @param value
	 *        A {@code float}.
	 */
	public void setConstantValue (final float value)
	{
		assert canSetConstantValue();
		final Class<?> type = JavaDescriptors.toConstantType(descriptor());
		assert type == Integer.TYPE;
		final Entry constantValueEntry = constantPool.constant(value);
		final Attribute attribute =
			new ConstantValueAttribute(constantValueEntry);
		setAttribute(attribute);
	}

	/**
	 * Set the constant value for this {@linkplain Field field}.
	 *
	 * @param value
	 *        A {@code double}.
	 */
	public void setConstantValue (final double value)
	{
		assert canSetConstantValue();
		final Class<?> type = JavaDescriptors.toConstantType(descriptor());
		assert type == Long.TYPE;
		final Entry constantValueEntry = constantPool.constant(value);
		final Attribute attribute =
			new ConstantValueAttribute(constantValueEntry);
		setAttribute(attribute);
	}

	/**
	 * Set the constant value for this {@linkplain Field field}.
	 *
	 * @param value
	 *        A {@link String}.
	 */
	public void setConstantValue (final String value)
	{
		assert canSetConstantValue();
		final Class<?> type = JavaDescriptors.toConstantType(descriptor());
		assert type == Long.TYPE;
		final Entry constantValueEntry = constantPool.constant(value);
		final Attribute attribute =
			new ConstantValueAttribute(constantValueEntry);
		setAttribute(attribute);
	}

	/**
	 * Force the {@linkplain Field field} to be {@linkplain Deprecated
	 * deprecated}.
	 */
	public void beDeprecated ()
	{
		assert !attributes().containsKey(DeprecatedAttribute.name);
		final Attribute attribute = new DeprecatedAttribute();
		setAttribute(attribute);
	}

	/**
	 * Write the {@linkplain Field field} as a {@code field_info} structure to
	 * the specified {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 * @see <a
	 *     href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.5">
	 *     Fields</a>
	 */
	@Override
	void writeBodyTo (final DataOutput out) throws IOException
	{
		nameEntry.writeIndexTo(out);
		descriptorEntry.writeIndexTo(out);
	}
}
