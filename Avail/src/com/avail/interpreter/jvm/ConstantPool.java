/**
 * ConstantPool.java
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

import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import com.avail.annotations.Nullable;

/**
 * {@code ConstantPool} represents a per-class constant pool.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a
 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4">
 *    The Constant Pool</a>
 */
class ConstantPool
{
	/**
	 * The enumeration of {@code class} file constant pool tags.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4-140">
	 *    Constant pool tags</a>
	 */
	static enum Tag
	{
		@SuppressWarnings("javadoc") Utf8 (1),
		@SuppressWarnings("javadoc") Integer (3),
		@SuppressWarnings("javadoc") Float (4),
		@SuppressWarnings("javadoc") Long (5),
		@SuppressWarnings("javadoc") Double (6),
		@SuppressWarnings("javadoc") Class (7),
		@SuppressWarnings("javadoc") String (8),
		@SuppressWarnings("javadoc") Fieldref (9),
		@SuppressWarnings("javadoc") Methodref (10),
		@SuppressWarnings("javadoc") InterfaceMethodref (11),
		@SuppressWarnings("javadoc") NameAndType (12),
		@SuppressWarnings("javadoc") MethodHandle (15),
		@SuppressWarnings("javadoc") MethodType (16),
		@SuppressWarnings("javadoc") InvokeDynamic (18);

		/** The value of the tag. */
		final byte value;

		/**
		 * Construct a new {@link Tag}.
		 *
		 * @param value
		 *        The value of the tag.
		 */
		private Tag (final int value)
		{
			assert (value & 255) == value;
			this.value = (byte) value;
		}

		/**
		 * Write the {@linkplain Tag tag}'s {@linkplain #value} to the specified
		 * {@linkplain DataOutput binary stream}.
		 *
		 * @param out
		 *        A binary output stream.
		 * @throws IOException
		 *         If the operation fails.
		 */
		void writeTo (final DataOutput out) throws IOException
		{
			out.writeByte(value);
		}
	}

	/**
	 * The subclasses of {@code Entry} represent constant pool entries.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4">
	 *    The Constant Pool</a>
	 */
	static abstract class Entry
	{
		/** The index into the {@linkplain ConstantPool constant pool}. */
		final int index;

		/**
		 * Answer the {@linkplain Tag tag}.
		 *
		 * @return The tag.
		 */
		abstract Tag tag ();

		/**
		 * Write the body of the {@linkplain Entry entry} to the specified
		 * {@linkplain DataOutput binary stream}.
		 *
		 * @param out
		 *        A binary output stream.
		 * @throws IOException
		 *         If the operation fails.
		 */
		abstract void writeBodyTo (DataOutput out) throws IOException;

		/**
		 * Write the {@linkplain ConstantPool constant pool} index to the
		 * specified {@linkplain DataOutput binary stream}.
		 *
		 * @param out
		 *        A binary output stream.
		 * @throws IOException
		 *         If the operation fails.
		 */
		final void writeIndexTo (final DataOutput out) throws IOException
		{
			out.writeShort(index);
		}

		/**
		 * Write the {@linkplain Entry entry} to the specified {@linkplain
		 * DataOutput binary stream}.
		 *
		 * @param out
		 *        A binary output stream.
		 * @throws IOException
		 *         If the operation fails.
		 */
		final void writeTo (final DataOutput out) throws IOException
		{
			tag().writeTo(out);
			writeBodyTo(out);
		}

		/**
		 * Construct a new {@link Entry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 */
		Entry (final int index)
		{
			this.index = index;
		}
	}

	/**
	 * {@code Utf8Entry} corresponds to the {@code CONSTANT_Utf8_info}
	 * structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.7">
	 *    The <code>CONSTANT_Utf8_info</code> Structure</a>
	 */
	static final class Utf8Entry
	extends Entry
	{
		@Override
		Tag tag ()
		{
			return Tag.Utf8;
		}

		/** The {@linkplain String data}. */
		private final String data;

		@Override
		void writeBodyTo (final DataOutput out) throws IOException
		{
			out.writeUTF(data);
		}

		@Override
		public String toString ()
		{
			return data;
		}

		/**
		 * Construct a new {@link Utf8Entry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param data
		 *        The {@linkplain String data}.
		 */
		Utf8Entry (final int index, final String data)
		{
			super(index);
			this.data = data;
		}
	}

	/**
	 * {@code IntegerEntry} corresponds to the {@code CONSTANT_Integer_info}
	 * structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.4">
	 *    The <code>CONSTANT_Integer_info</code> and
	 *    <code>CONSTANT_Float_info</code> Structure</a>
	 */
	static final class IntegerEntry
	extends Entry
	{
		@Override
		Tag tag ()
		{
			return Tag.Integer;
		}

		/** The data. */
		private final int data;

		@Override
		void writeBodyTo (final DataOutput out) throws IOException
		{
			out.writeInt(data);
		}

		@Override
		public String toString ()
		{
			return Integer.toString(data);
		}

		/**
		 * Construct a new {@link IntegerEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param data
		 *        The data.
		 */
		IntegerEntry (final int index, final int data)
		{
			super(index);
			this.data = data;
		}
	}

	/**
	 * {@code FloatEntry} corresponds to the {@code CONSTANT_Float_info}
	 * structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.4">
	 *    The <code>CONSTANT_Integer_info</code> and
	 *    <code>CONSTANT_Float_info</code> Structure</a>
	 */
	static final class FloatEntry
	extends Entry
	{
		@Override
		Tag tag ()
		{
			return Tag.Float;
		}

		/** The data. */
		private final float data;

		@Override
		void writeBodyTo (final DataOutput out) throws IOException
		{
			out.writeFloat(data);
		}

		@Override
		public String toString ()
		{
			return Float.toString(data);
		}

		/**
		 * Construct a new {@link IntegerEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param data
		 *        The data.
		 */
		FloatEntry (final int index, final float data)
		{
			super(index);
			this.data = data;
		}
	}

	/**
	 * {@code LongEntry} corresponds to the {@code CONSTANT_Long_info}
	 * structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.5">
	 *    The <code>CONSTANT_Long_info</code> and
	 *    <code>CONSTANT_Double_info</code> Structure</a>
	 */
	static final class LongEntry
	extends Entry
	{
		@Override
		Tag tag ()
		{
			return Tag.Long;
		}

		/** The data. */
		private final long data;

		@Override
		void writeBodyTo (final DataOutput out) throws IOException
		{
			out.writeLong(data);
		}

		@Override
		public String toString ()
		{
			return Long.toString(data);
		}

		/**
		 * Construct a new {@link LongEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param data
		 *        The data.
		 */
		LongEntry (final int index, final long data)
		{
			super(index);
			this.data = data;
		}
	}

	/**
	 * {@code DoubleEntry} corresponds to the {@code CONSTANT_Double_info}
	 * structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.5">
	 *    The <code>CONSTANT_Long_info</code> and
	 *    <code>CONSTANT_Double_info</code> Structure</a>
	 */
	static final class DoubleEntry
	extends Entry
	{
		@Override
		Tag tag ()
		{
			return Tag.Double;
		}

		/** The data. */
		private final double data;

		@Override
		void writeBodyTo (final DataOutput out) throws IOException
		{
			out.writeDouble(data);
		}

		@Override
		public String toString ()
		{
			return Double.toString(data);
		}

		/**
		 * Construct a new {@link DoubleEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param data
		 *        The data.
		 */
		DoubleEntry (final int index, final double data)
		{
			super(index);
			this.data = data;
		}
	}

	/**
	 * {@code ClassEntry} corresponds to the {@code CONSTANT_Class_info}
	 * structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.1">
	 *    The <code>CONSTANT_Class_info</code> Structure</a>
	 */
	static final class ClassEntry
	extends Entry
	{
		@Override
		Tag tag ()
		{
			return Tag.Class;
		}

		/**
		 * The {@linkplain Utf8Entry entry} containing the {@linkplain
		 * JavaDescriptors#forType(Class) binary class name}.
		 */
		private final Utf8Entry nameEntry;

		@Override
		void writeBodyTo (final DataOutput out) throws IOException
		{
			nameEntry.writeIndexTo(out);
		}

		@Override
		public String toString ()
		{
			return nameEntry.toString();
		}

		/**
		 * Construct a new {@link ClassEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param nameEntry
		 *        The {@linkplain Utf8Entry entry} containing the {@linkplain
		 *        JavaDescriptors#forType(Class) binary class name}.
		 */
		ClassEntry (final int index, final Utf8Entry nameEntry)
		{
			super(index);
			this.nameEntry = nameEntry;
		}
	}

	/**
	 * {@code StringEntry} corresponds to the {@code CONSTANT_String_info}
	 * structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.3">
	 *    The <code>CONSTANT_String_info</code> Structure</a>
	 */
	static final class StringEntry
	extends Entry
	{
		@Override
		Tag tag ()
		{
			return Tag.String;
		}

		/** The {@linkplain Utf8Entry entry} containing the character data. */
		private final Utf8Entry utf8Entry;

		@Override
		void writeBodyTo (final DataOutput out) throws IOException
		{
			utf8Entry.writeIndexTo(out);
		}

		@Override
		public String toString ()
		{
			return utf8Entry.toString();
		}

		/**
		 * Construct a new {@link StringEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param utf8Entry
		 *        The {@linkplain Utf8Entry entry} containing the character
		 *        data.
		 */
		StringEntry (final int index, final Utf8Entry utf8Entry)
		{
			super(index);
			this.utf8Entry = utf8Entry;
		}
	}

	/**
	 * {@code RefEntry} specifies the representation and serialization of its
	 * concrete subclasses (which differ only their {@linkplain Tag tags}).
	 */
	private static abstract class RefEntry
	extends Entry
	{
		/**
		 * The {@linkplain ClassEntry entry} for the referenced {@linkplain
		 * Class class}.
		 */
		private final ClassEntry classEntry;

		/**
		 * The {@linkplain NameAndTypeEntry entry} for the member name and type.
		 */
		private final NameAndTypeEntry nameAndTypeEntry;

		@Override
		final void writeBodyTo (final DataOutput out) throws IOException
		{
			classEntry.writeIndexTo(out);
			nameAndTypeEntry.writeIndexTo(out);
		}

		@Override
		public String toString ()
		{
			return String.format(
				"%s.%s",
				classEntry,
				nameAndTypeEntry);
		}

		/**
		 * Construct a new {@link RefEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param classEntry
		 *        The {@linkplain ClassEntry entry} for the referenced
		 *        {@linkplain Class class}.
		 * @param nameAndTypeEntry
		 *        The {@linkplain NameAndTypeEntry entry} for the member name
		 *        and type.
		 */
		RefEntry (
			final int index,
			final ClassEntry classEntry,
			final NameAndTypeEntry nameAndTypeEntry)
		{
			super(index);
			this.classEntry = classEntry;
			this.nameAndTypeEntry = nameAndTypeEntry;
		}
	}

	/**
	 * {@code FieldrefEntry} corresponds to the {@code
	 * CONSTANT_Fieldref_info} structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.2">
	 *    The <code>CONSTANT_Fieldref_info</code>,
	 *    <code>CONSTANT_Methodref_info</code>,
	 *    and <code>CONSTANT_InterfaceMethodref_info</code> Structures</a>
	 */
	static final class FieldrefEntry
	extends RefEntry
	{
		@Override
		Tag tag ()
		{
			return Tag.Fieldref;
		}

		@Override
		public String toString ()
		{
			return "Field " + super.toString();
		}

		/**
		 * Construct a new {@link FieldrefEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param classEntry
		 *        The {@linkplain ClassEntry entry} for the referenced
		 *        {@linkplain Class class}.
		 * @param nameAndTypeEntry
		 *        The {@linkplain NameAndTypeEntry entry} for the member name
		 *        and type.
		 */
		FieldrefEntry (
			final int index,
			final ClassEntry classEntry,
			final NameAndTypeEntry nameAndTypeEntry)
		{
			super(index, classEntry, nameAndTypeEntry);
		}
	}

	/**
	 * {@code MethodrefEntry} corresponds to the {@code
	 * CONSTANT_Methodref_info} structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.2">
	 *    The <code>CONSTANT_Fieldref_info</code>,
	 *    <code>CONSTANT_Methodref_info</code>,
	 *    and <code>CONSTANT_InterfaceMethodref_info</code> Structures</a>
	 */
	static final class MethodrefEntry
	extends RefEntry
	{
		@Override
		Tag tag ()
		{
			return Tag.Methodref;
		}

		@Override
		public String toString ()
		{
			return "Method " + super.toString();
		}

		/**
		 * Construct a new {@link MethodrefEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param classEntry
		 *        The {@linkplain ClassEntry entry} for the referenced
		 *        {@linkplain Class class}.
		 * @param nameAndTypeEntry
		 *        The {@linkplain NameAndTypeEntry entry} for the member name
		 *        and type.
		 */
		MethodrefEntry (
			final int index,
			final ClassEntry classEntry,
			final NameAndTypeEntry nameAndTypeEntry)
		{
			super(index, classEntry, nameAndTypeEntry);
		}
	}

	/**
	 * {@code InterfaceMethodrefEntry} corresponds to the {@code
	 * CONSTANT_InterfaceMethodref_info} structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.2">
	 *    The <code>CONSTANT_Fieldref_info</code>,
	 *    <code>CONSTANT_Methodref_info</code>,
	 *    and <code>CONSTANT_InterfaceMethodref_info</code> Structures</a>
	 */
	static final class InterfaceMethodrefEntry
	extends RefEntry
	{
		@Override
		Tag tag ()
		{
			return Tag.InterfaceMethodref;
		}

		@Override
		public String toString ()
		{
			return "Interface Method " + super.toString();
		}

		/**
		 * Construct a new {@link InterfaceMethodrefEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param classEntry
		 *        The {@linkplain ClassEntry entry} for the referenced
		 *        {@linkplain Class class}.
		 * @param nameAndTypeEntry
		 *        The {@linkplain NameAndTypeEntry entry} for the member name
		 *        and type.
		 */
		InterfaceMethodrefEntry (
			final int index,
			final ClassEntry classEntry,
			final NameAndTypeEntry nameAndTypeEntry)
		{
			super(index, classEntry, nameAndTypeEntry);
		}
	}

	/**
	 * {@code NameAndTypeEntry} corresponds to the {@code
	 * CONSTANT_NameAndType_info} structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.6">
	 *    The <code>CONSTANT_NameAndType_info</code> Structure</a>
	 */
	static final class NameAndTypeEntry
	extends Entry
	{
		@Override
		Tag tag ()
		{
			return Tag.NameAndType;
		}

		/** The {@linkplain Utf8Entry entry} containing the name. */
		private final Utf8Entry nameEntry;

		/** The {@linkplain Utf8Entry entry} containing the descriptor. */
		private final Utf8Entry descriptorEntry;

		@Override
		void writeBodyTo (final DataOutput out) throws IOException
		{
			nameEntry.writeIndexTo(out);
			descriptorEntry.writeIndexTo(out);
		}

		@Override
		public String toString ()
		{
			return nameEntry + ":" + descriptorEntry;
		}

		/**
		 * Construct a new {@link NameAndTypeEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param nameEntry
		 *        The {@linkplain Utf8Entry entry} containing the name.
		 * @param descriptorEntry
		 *        The {@linkplain Utf8Entry entry} containing the descriptor.
		 */
		NameAndTypeEntry (
			final int index,
			final Utf8Entry nameEntry,
			final Utf8Entry descriptorEntry)
		{
			super(index);
			this.nameEntry = nameEntry;
			this.descriptorEntry = descriptorEntry;
		}
	}

	/**
	 * {@code MethodHandleKind} describes the {@linkplain RefEntry reference}
	 * kind for a dynamic call site.
	 */
	@SuppressWarnings("javadoc")
	static enum MethodHandleKind
	{
		GetField (1),
		GetStatic (2),
		PutField (3),
		PutStatic (4),
		InvokeVirtual (5),
		InvokeStatic (6),
		InvokeSpecial (7),
		NewInvokeSpecial (8),
		InvokeInterface (9);

		/** The binary type tag. */
		private final int tag;

		/**
		 * Answer the {@linkplain RefEntry reference} {@linkplain Class class}
		 * denoted by the {@linkplain MethodHandleKind method handle kind}.
		 *
		 * @return A reference class.
		 */
		Class<? extends RefEntry> entryClass ()
		{
			switch (this)
			{
				case GetField:
				case GetStatic:
				case PutField:
				case PutStatic:
					return FieldrefEntry.class;
				case InvokeVirtual:
				case InvokeStatic:
				case InvokeSpecial:
				case NewInvokeSpecial:
					return MethodrefEntry.class;
				case InvokeInterface:
					return InterfaceMethodrefEntry.class;
				default:
					assert false : "This never happens";
					throw new IllegalStateException();
			}
		}

		/**
		 * Write the {@linkplain MethodHandleKind method handle kind} to the
		 * specified {@linkplain DataOutput binary stream}.
		 *
		 * @param out
		 *        A binary output stream.
		 * @throws IOException
		 *         If the operation fails.
		 */
		void writeTo (final DataOutput out) throws IOException
		{
			out.writeByte(tag);
		}

		/**
		 * Construct a new {@link MethodHandleKind}.
		 *
		 * @param tag
		 *        The binary type tag.
		 */
		private MethodHandleKind (final int tag)
		{
			this.tag = tag;
		}
	}

	/**
	 * {@code MethodHandleEntry} corresponds to the {@code
	 * CONSTANT_MethodHandle_info} structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.8">
	 *    The <code>CONSTANT_MethodHandle_info</code> Structure</a>
	 */
	static final class MethodHandleEntry
	extends Entry
	{
		@Override
		Tag tag ()
		{
			return Tag.MethodHandle;
		}

		/**
		 * The {@linkplain MethodHandleKind method handle kind}:
		 *
		 * <ol>
		 * <li>{@linkplain MethodHandleKind#GetField REF_getField}</li>
		 * <li>{@linkplain MethodHandleKind#GetStatic REF_getStatic}</li>
		 * <li>{@linkplain MethodHandleKind#PutField REF_putField}</li>
		 * <li>{@linkplain MethodHandleKind#PutStatic REF_putStatic}</li>
		 * <li>{@linkplain MethodHandleKind#InvokeVirtual REF_invokeVirtual}</li>
		 * <li>{@linkplain MethodHandleKind#InvokeStatic REF_invokeStatic}</li>
		 * <li>{@linkplain MethodHandleKind#InvokeSpecial REF_invokeSpecial}</li>
		 * <li>{@linkplain MethodHandleKind#NewInvokeSpecial REF_newInvokeSpecial}</li>
		 * <li>{@linkplain MethodHandleKind#InvokeInterface REF_invokeInterface}</li>
		 * </ol>
		 */
		private final MethodHandleKind kind;

		/** The {@linkplain RefEntry reference entry}. */
		private final RefEntry referenceEntry;

		@Override
		void writeBodyTo (final DataOutput out) throws IOException
		{
			kind.writeTo(out);
			referenceEntry.writeIndexTo(out);
		}

		/**
		 * Construct a new {@link MethodHandleEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param kind
		 *        The referenceEntry kind.
		 * @param referenceEntry
		 *        The referenceEntry.
		 * @see #kind
		 * @see #referenceEntry
		 */
		MethodHandleEntry (
			final int index,
			final MethodHandleKind kind,
			final RefEntry referenceEntry)
		{
			super(index);
			assert referenceEntry.getClass().isInstance(kind.entryClass());
			this.kind = kind;
			this.referenceEntry = referenceEntry;
		}
	}

	/**
	 * {@code MethodTypeEntry} corresponds to the {@code
	 * CONSTANT_MethodType_info} structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.9">
	 *    The <code>CONSTANT_MethodType_info</code> Structure</a>
	 */
	static final class MethodTypeEntry
	extends Entry
	{
		@Override
		Tag tag ()
		{
			return Tag.MethodType;
		}

		/** The {@linkplain Utf8Entry entry} for the method's descriptor. */
		private final Utf8Entry descriptorEntry;

		@Override
		void writeBodyTo (final DataOutput out) throws IOException
		{
			descriptorEntry.writeIndexTo(out);
		}

		@Override
		public String toString ()
		{
			return descriptorEntry.toString();
		}

		/**
		 * Construct a new {@link MethodTypeEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param descriptorEntry
		 *        The {@linkplain Utf8Entry entry} for the method's descriptor.
		 */
		MethodTypeEntry (final int index, final Utf8Entry descriptorEntry)
		{
			super(index);
			this.descriptorEntry = descriptorEntry;
		}
	}

	/**
	 * {@code MethodTypeEntry} corresponds to the {@code
	 * CONSTANT_MethodType_info} structure.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4.10">
	 *    The <code>CONSTANT_MethodType_info</code> Structure</a>
	 */
	static final class InvokeDynamicEntry
	extends Entry
	{
		@Override
		Tag tag ()
		{
			return Tag.InvokeDynamic;
		}

		/** A valid index into the {@code bootstrap_methods} array. */
		private final int bootstrapMethodAttrIndex;

		/**
		 * The {@linkplain NameAndTypeEntry entry} for the method name and
		 * type.
		 */
		private final NameAndTypeEntry nameAndTypeEntry;

		@Override
		void writeBodyTo (final DataOutput out) throws IOException
		{
			out.writeShort(bootstrapMethodAttrIndex);
			nameAndTypeEntry.writeIndexTo(out);
		}

		/**
		 * Construct a new {@link InvokeDynamicEntry}.
		 *
		 * @param index
		 *        The index into the {@linkplain ConstantPool constant pool}.
		 * @param bootstrapMethodAttrIndex
		 *        A valid index into the {@code bootstrap_methods} array.
		 * @param nameAndTypeEntry
		 *        The {@linkplain NameAndTypeEntry entry} for the method name
		 *        and type.
		 */
		InvokeDynamicEntry (
			final int index,
			final int bootstrapMethodAttrIndex,
			final NameAndTypeEntry nameAndTypeEntry)
		{
			super(index);
			this.bootstrapMethodAttrIndex = bootstrapMethodAttrIndex;
			this.nameAndTypeEntry = nameAndTypeEntry;
		}
	}

	/** The next (unused) index. */
	private int nextIndex = 0;

	/**
	 * A {@linkplain Map map} from Java constants to {@linkplain Entry entries}.
	 */
	private final LinkedHashMap<Object, Entry> entries = new LinkedHashMap<>();

	/**
	 * {@code Utf8Key} serves as a key in {@link ConstantPool#entries entries}
	 * for {@link Utf8Entry} objects.
	 */
	private static final class Utf8Key
	{
		/** The value of the key. */
		private final String value;

		@Override
		public boolean equals (final @Nullable Object obj)
		{
			if (obj instanceof Utf8Key)
			{
				final Utf8Key other = (Utf8Key) obj;
				return value.equals(other.value);
			}
			return false;
		}

		@Override
		public int hashCode ()
		{
			return 37 * value.hashCode();
		}

		@Override
		public String toString ()
		{
			return String.format("%s(%s)", getClass().getSimpleName(), value);
		}

		/**
		 * Construct a new {@link Utf8Key}.
		 *
		 * @param value
		 *        The value of the key.
		 */
		Utf8Key (final String value)
		{
			this.value = value;
		}
	}

	/**
	 * Answer a {@link Utf8Entry} for the specified {@link String}, constructing
	 * and installing a new entry if necessary.
	 *
	 * @param value
	 *        The constant.
	 * @return The entry associated with the specified value.
	 */
	Utf8Entry utf8 (final String value)
	{
		final Utf8Key key = new Utf8Key(value);
		Entry entry = entries.get(key);
		if (entry == null)
		{
			entry = new Utf8Entry(nextIndex++, value);
			entries.put(key, entry);
		}
		return (Utf8Entry) entry;
	}

	/**
	 * Answer an {@link IntegerEntry} for the specified {@code int},
	 * constructing and installing a new entry if necessary.
	 *
	 * @param value
	 *        The constant.
	 * @return The entry associated with the specified value.
	 */
	IntegerEntry constant (final int value)
	{
		final Integer boxed = new Integer(value);
		Entry entry = entries.get(boxed);
		if (entry == null)
		{
			entry = new IntegerEntry(nextIndex++, value);
			entries.put(boxed, entry);
		}
		return (IntegerEntry) entry;
	}

	/**
	 * Answer a {@link LongEntry} for the specified {@code long}, constructing
	 * and installing a new entry if necessary.
	 *
	 * @param value
	 *        The constant.
	 * @return The entry associated with the specified value.
	 */
	LongEntry constant (final long value)
	{
		final Long boxed = new Long(value);
		Entry entry = entries.get(boxed);
		if (entry == null)
		{
			// CONSTANT_Long_info occupies two slots.
			final int index = nextIndex;
			entry = new LongEntry(index, value);
			nextIndex += 2;
			entries.put(boxed, entry);
		}
		return (LongEntry) entry;
	}

	/**
	 * Answer a {@link FloatEntry} for the specified {@code float}, constructing
	 * and installing a new entry if necessary.
	 *
	 * @param value
	 *        The constant.
	 * @return The entry associated with the specified value.
	 */
	FloatEntry constant (final float value)
	{
		final Float boxed = new Float(value);
		Entry entry = entries.get(boxed);
		if (entry == null)
		{
			entry = new FloatEntry(nextIndex++, value);
			entries.put(boxed, entry);
		}
		return (FloatEntry) entry;
	}

	/**
	 * Answer a {@link DoubleEntry} for the specified {@code double},
	 * constructing and installing a new entry if necessary.
	 *
	 * @param value
	 *        The constant.
	 * @return The entry associated with the specified value.
	 */
	DoubleEntry constant (final double value)
	{
		final Double boxed = new Double(value);
		Entry entry = entries.get(boxed);
		if (entry == null)
		{
			// CONSTANT_Double_info occupies two slots.
			final int index = nextIndex;
			entry = new DoubleEntry(index, value);
			nextIndex += 2;
			entries.put(boxed, entry);
		}
		return (DoubleEntry) entry;
	}

	/**
	 * Answer a {@link ClassEntry} for the specified {@linkplain Class class},
	 * constructing and installing a new entry if necessary.
	 *
	 * @param value
	 *        The constant.
	 * @return The entry associated with the specified value.
	 */
	ClassEntry constant (final Class<?> value)
	{
		Entry entry = entries.get(value);
		if (entry == null)
		{
			final String descriptor = JavaDescriptors.forType(value);
			final Utf8Entry nameEntry = utf8(descriptor);
			entry = new ClassEntry(nextIndex++, nameEntry);
			entries.put(value, entry);
		}
		return (ClassEntry) entry;
	}

	/**
	 * Answer a {@link StringEntry} for the specified {@link String},
	 * constructing and installing a new entry if necessary.
	 *
	 * @param value
	 *        The constant.
	 * @return The entry associated with the specified value.
	 */
	StringEntry constant (final String value)
	{
		Entry entry = entries.get(value);
		if (entry == null)
		{
			final Utf8Entry utf8Entry = utf8(value);
			entry = new StringEntry(nextIndex++, utf8Entry);
		}
		return (StringEntry) entry;
	}

	/**
	 * {@code RefKey} is the base for entity reference keys.
	 */
	private static abstract class RefKey
	{
		/**
		 * Answer the tag of the {@linkplain RefKey reference key}.
		 *
		 * @return The tag of the reference key.
		 */
		abstract int tag ();

		/** The {@linkplain Class class}. */
		private final Class<?> type;

		/** The name. */
		private final String name;

		/** The descriptor. */
		private final String descriptor;

		@Override
		public final boolean equals (final @Nullable Object obj)
		{
			if (obj instanceof RefKey)
			{
				final RefKey other = (RefKey) obj;
				return tag() == other.tag()
					&& type.equals(other.type)
					&& name.equals(other.name)
					&& descriptor.equals(other.descriptor);
			}
			return false;
		}

		@Override
		public final int hashCode ()
		{
			return 71 * tag()
				+ 23 * type.hashCode()
				+ 59 * name.hashCode()
				+ 17 * descriptor.hashCode();
		}

		@Override
		public final String toString ()
		{
			return String.format(
				"%s(%s.%s:%s)",
				getClass().getSimpleName(),
				type.getClass().getName(),
				name,
				descriptor);
		}

		/**
		 * Construct a new {@link RefKey}.
		 *
		 * @param type
		 *        The {@linkplain Class type}.
		 * @param name
		 *        The name of the referent.
		 * @param descriptor
		 *        The descriptor of the referent.
		 */
		RefKey (
			final Class<?> type,
			final String name,
			final String descriptor)
		{
			this.type = type;
			this.name = name;
			this.descriptor = descriptor;
		}
	}

	/**
	 * {@code FieldrefKey} serves as a key in {@link ConstantPool#entries
	 * entries} for {@link FieldrefKey} objects.
	 */
	private static final class FieldrefKey
	extends RefKey
	{
		@Override
		int tag ()
		{
			return 1;
		}

		/**
		 * Construct a new {@link FieldrefKey}.
		 *
		 * @param type
		 *        The {@linkplain Class type}.
		 * @param name
		 *        The name of the referent.
		 * @param descriptor
		 *        The descriptor of the referent.
		 */
		FieldrefKey (
			final Class<?> type,
			final String name,
			final String descriptor)
		{
			super(type, name, descriptor);
		}
	}

	/**
	 * Answer a {@link FieldrefEntry} for the specified field reference,
	 * constructing and installing a new entry if necessary.
	 *
	 * @param definer
	 *        The {@linkplain Class class} that defines the field.
	 * @param name
	 *        The name of the referent.
	 * @param fieldType
	 *        The type of the referent.
	 * @return The entry associated with the specified field reference.
	 */
	FieldrefEntry fieldref (
		final Class<?> definer,
		final String name,
		final Class<?> fieldType)
	{
		final String fieldDescriptor = JavaDescriptors.forType(fieldType);
		final FieldrefKey key = new FieldrefKey(definer, name, fieldDescriptor);
		Entry entry = entries.get(key);
		if (entry == null)
		{
			final ClassEntry classEntry = constant(definer);
			final NameAndTypeEntry nameAndType =
				nameAndType(name, fieldDescriptor);
			entry = new FieldrefEntry(nextIndex++, classEntry, nameAndType);
			entries.put(key, entry);
		}
		return (FieldrefEntry) entry;
	}

	/**
	 * {@code MethodrefKey} serves as a key in {@link ConstantPool#entries
	 * entries} for {@link MethodrefKey} objects.
	 */
	private static final class MethodrefKey
	extends RefKey
	{
		@Override
		int tag ()
		{
			return 2;
		}

		/**
		 * Construct a new {@link MethodrefKey}.
		 *
		 * @param type
		 *        The {@linkplain Class type}.
		 * @param name
		 *        The name of the referent.
		 * @param descriptor
		 *        The descriptor of the referent.
		 */
		MethodrefKey (
			final Class<?> type,
			final String name,
			final String descriptor)
		{
			super(type, name, descriptor);
		}
	}

	/**
	 * Answer a {@link MethodrefEntry} for the specified method reference,
	 * constructing and installing a new entry if necessary.
	 *
	 * @param definer
	 *        The {@linkplain Class class} that defines the field.
	 * @param name
	 *        The name of the referent.
	 * @param returnType
	 *        The return type of the referent.
	 * @param parameterTypes
	 *        The parameter types of the referent.
	 * @return The entry associated with the specified field reference.
	 */
	MethodrefEntry methodref (
		final Class<?> definer,
		final String name,
		final Class<?> returnType,
		final Class<?>... parameterTypes)
	{
		final String methodDescriptor = JavaDescriptors.forMethod(
			returnType, parameterTypes);
		final MethodrefKey key = new MethodrefKey(
			returnType, name, methodDescriptor);
		Entry entry = entries.get(key);
		if (entry == null)
		{
			final ClassEntry classEntry = constant(definer);
			final NameAndTypeEntry nameAndType =
				nameAndType(name, methodDescriptor);
			entry = new FieldrefEntry(nextIndex++, classEntry, nameAndType);
			entries.put(key, entry);
		}
		return (MethodrefEntry) entry;
	}

	/**
	 * Answer a {@link MethodrefEntry} for the specified method reference,
	 * constructing and installing a new entry if necessary.
	 *
	 * @param method
	 *        The {@linkplain Method referent}.
	 * @return The entry associated with the specified field reference.
	 */
	MethodrefEntry methodref (final Method method)
	{
		assert !method.getDeclaringClass().isInterface();
		return methodref(
			method.getDeclaringClass(),
			method.getName(),
			method.getReturnType(),
			method.getParameterTypes());
	}

	/**
	 * {@code InterfaceMethodrefKey} serves as a key in {@link
	 * ConstantPool#entries entries} for {@link InterfaceMethodrefKey} objects.
	 */
	private static final class InterfaceMethodrefKey
	extends RefKey
	{
		@Override
		int tag ()
		{
			return 3;
		}

		/**
		 * Construct a new {@link InterfaceMethodrefKey}.
		 *
		 * @param type
		 *        The {@linkplain Class type}.
		 * @param name
		 *        The name of the referent.
		 * @param descriptor
		 *        The descriptor of the referent.
		 */
		InterfaceMethodrefKey (
			final Class<?> type,
			final String name,
			final String descriptor)
		{
			super(type, name, descriptor);
		}
	}

	/**
	 * Answer a {@link InterfaceMethodrefEntry} for the specified method
	 * reference, constructing and installing a new entry if necessary.
	 *
	 * @param definer
	 *        The {@linkplain Class class} that defines the field.
	 * @param name
	 *        The name of the referent.
	 * @param returnType
	 *        The return type of the referent.
	 * @param parameterTypes
	 *        The parameter types of the referent.
	 * @return The entry associated with the specified field reference.
	 */
	InterfaceMethodrefEntry interfaceMethodref (
		final Class<?> definer,
		final String name,
		final Class<?> returnType,
		final Class<?>... parameterTypes)
	{
		final String methodDescriptor = JavaDescriptors.forMethod(
			returnType, parameterTypes);
		final InterfaceMethodrefKey key = new InterfaceMethodrefKey(
			returnType, name, methodDescriptor);
		Entry entry = entries.get(key);
		if (entry == null)
		{
			final ClassEntry classEntry = constant(definer);
			final NameAndTypeEntry nameAndType =
				nameAndType(name, methodDescriptor);
			entry = new FieldrefEntry(nextIndex++, classEntry, nameAndType);
			entries.put(key, entry);
		}
		return (InterfaceMethodrefEntry) entry;
	}

	/**
	 * Answer a {@link InterfaceMethodrefEntry} for the specified method
	 * reference, constructing and installing a new entry if necessary.
	 *
	 * @param method
	 *        The {@linkplain Method referent}.
	 * @return The entry associated with the specified field reference.
	 */
	InterfaceMethodrefEntry interfaceMethodref (final Method method)
	{
		assert method.getDeclaringClass().isInterface();
		return interfaceMethodref(
			method.getDeclaringClass(),
			method.getName(),
			method.getReturnType(),
			method.getParameterTypes());
	}

	/**
	 * {@code NameAndTypeKey} serves as a key in {@link ConstantPool#entries
	 * entries} for {@link NameAndTypeEntry} objects.
	 */
	private static final class NameAndTypeKey
	{
		/** The name. */
		private final String name;

		/** The descriptor. */
		private final String descriptor;

		@Override
		public boolean equals (final @Nullable Object obj)
		{
			if (obj instanceof NameAndTypeKey)
			{
				final NameAndTypeKey other = (NameAndTypeKey) obj;
				return name.equals(other.name)
					&& descriptor.equals(other.descriptor);
			}
			return false;
		}

		@Override
		public int hashCode ()
		{
			return 59 * name.hashCode() + 17 * descriptor.hashCode();
		}

		@Override
		public String toString ()
		{
			return String.format(
				"%s(%s:%s)",
				getClass().getSimpleName(),
				name,
				descriptor);
		}

		/**
		 * Construct a new {@link NameAndTypeKey}.
		 *
		 * @param name
		 *        The name.
		 * @param descriptor
		 *        The descriptor.
		 */
		NameAndTypeKey (final String name, final String descriptor)
		{
			this.name = name;
			this.descriptor = descriptor;
		}
	}

	/**
	 * Answer a {@link NameAndTypeEntry} for the specified name and descriptor,
	 * constructing and installing a new entry if necessary.
	 *
	 * @param name
	 *        The name.
	 * @param descriptor
	 *        A descriptor.
	 * @return The entry associated with the specified value.
	 */
	private NameAndTypeEntry nameAndType (
		final String name,
		final String descriptor)
	{
		final NameAndTypeKey key = new NameAndTypeKey(name, descriptor);
		Entry entry = entries.get(key);
		if (entry == null)
		{
			final Utf8Entry nameEntry = utf8(name);
			final Utf8Entry descriptorEntry = utf8(descriptor);
			entry = new NameAndTypeEntry(
				nextIndex++, nameEntry, descriptorEntry);
			entries.put(key, entry);
		}
		return (NameAndTypeEntry) entry;
	}

	/**
	 * Answer a {@link NameAndTypeEntry} for the specified name and {@linkplain
	 * Class type}, constructing and installing a new entry if necessary.
	 *
	 * @param name
	 *        The name.
	 * @param type
	 *        A Java type.
	 * @return The entry associated with the specified value.
	 */
	NameAndTypeEntry nameAndType (
		final String name,
		final Class<?> type)
	{
		final String descriptor = JavaDescriptors.forType(type);
		return nameAndType(name, descriptor);
	}

	/**
	 * Answer a {@link NameAndTypeEntry} for the specified {@linkplain Method
	 * method}, constructing and installing a new entry if necessary.
	 *
	 * @param method
	 *        A Java method.
	 * @return The entry associated with the specified value.
	 */
	NameAndTypeEntry nameAndType (final Method method)
	{
		final String descriptor = JavaDescriptors.forMethod(method);
		return nameAndType(method.getName(), descriptor);
	}

	/**
	 * Answer a {@link NameAndTypeEntry} for the specified name and {@linkplain
	 * Method method} signature, constructing and installing a new entry if
	 * necessary.
	 *
	 * @param name
	 *        The name.
	 * @param returnType
	 *        The method's return type.
	 * @param parameterTypes
	 *        The method's parameter types.
	 * @return The entry associated with the specified value.
	 */
	NameAndTypeEntry nameAndType (
		final String name,
		final Class<?> returnType,
		final Class<?>... parameterTypes)
	{
		final String descriptor = JavaDescriptors.forMethod(
			returnType, parameterTypes);
		return nameAndType(name, descriptor);
	}

	/**
	 * {@code MethodHandleKey} serves as a key in {@link ConstantPool#entries
	 * entries} for {@link MethodHandleEntry} objects.
	 */
	private static final class MethodHandleKey
	{
		/** The {@linkplain MethodHandleKind method handle kind}. */
		private final MethodHandleKind kind;

		/** The {@linkplain RefEntry reference entry}. */
		private final RefEntry referenceEntry;

		@Override
		public boolean equals (final @Nullable Object obj)
		{
			if (obj instanceof MethodHandleKey)
			{
				final MethodHandleKey other = (MethodHandleKey) obj;
				return kind == other.kind
					&& referenceEntry == other.referenceEntry;
			}
			return false;
		}

		@Override
		public int hashCode ()
		{
			return 137 * kind.hashCode() + 29 * referenceEntry.hashCode();
		}

		@Override
		public String toString ()
		{
			return String.format(
				"%s(%s, %s)",
				getClass().getSimpleName(),
				kind,
				referenceEntry);
		}

		/**
		 * Construct a new {@link MethodHandleKey}.
		 *
		 * @param kind
		 *        The {@linkplain MethodHandleKind method handle kind}.
		 * @param referenceEntry
		 *        The {@linkplain RefEntry reference entry}.
		 */
		MethodHandleKey (
			final MethodHandleKind kind,
			final RefEntry referenceEntry)
		{
			assert referenceEntry.getClass().isInstance(kind.entryClass());
			this.kind = kind;
			this.referenceEntry = referenceEntry;
		}
	}

	/**
	 * Answer a {@link MethodHandleEntry}, constructing and installing a new
	 * entry if necessary.
	 *
	 * @param kind
	 *        The {@linkplain MethodHandleKind method handle kind}.
	 * @param referenceEntry
	 *        The {@linkplain RefEntry reference entry}.
	 * @return The entry associated with the specified value.
	 */
	MethodHandleEntry methodHandle (
		final MethodHandleKind kind,
		final RefEntry referenceEntry)
	{
		assert referenceEntry.getClass().isInstance(kind.entryClass());
		final MethodHandleKey key = new MethodHandleKey(kind, referenceEntry);
		Entry entry = entries.get(key);
		if (entry == null)
		{
			entry = new MethodHandleEntry(nextIndex++, kind, referenceEntry);
			entries.put(key, entry);
		}
		return (MethodHandleEntry) entry;
	}

	/**
	 * {@code MethodTypeKey} serves as a key in {@link ConstantPool#entries
	 * entries} for {@link MethodTypeEntry} objects.
	 */
	private static final class MethodTypeKey
	{
		/**
		 * The {@linkplain JavaDescriptors#forMethod(Class, Class...)
		 * method descriptor}.
		 */
		private final String descriptor;

		@Override
		public boolean equals (final @Nullable Object obj)
		{
			if (obj instanceof MethodTypeKey)
			{
				final MethodTypeKey other = (MethodTypeKey) obj;
				return descriptor.equals(other.descriptor);
			}
			return false;
		}

		@Override
		public int hashCode ()
		{
			return 61 * descriptor.hashCode();
		}

		@Override
		public String toString ()
		{
			return String.format(
				"%s(%s)",
				getClass().getSimpleName(),
				descriptor);
		}

		/**
		 * Construct a new {@link MethodTypeKey}.
		 *
		 * @param descriptor
		 *        The {@linkplain JavaDescriptors#forMethod(Class, Class...)
		 *        method descriptor}.
		 */
		MethodTypeKey (final String descriptor)
		{
			this.descriptor = descriptor;
		}
	}

	/**
	 * Answer a {@link MethodTypeEntry} for the specified method descriptor,
	 * constructing and installing a new entry if necessary.
	 *
	 * @param descriptor
	 *        A method descriptor.
	 * @return The entry associated with the specified value.
	 */
	private MethodTypeEntry methodType (final String descriptor)
	{
		final MethodTypeKey key = new MethodTypeKey(descriptor);
		Entry entry = entries.get(key);
		if (entry == null)
		{
			final Utf8Entry descriptorEntry = utf8(descriptor);
			entry = new MethodTypeEntry(nextIndex++, descriptorEntry);
			entries.put(key, entry);
		}
		return (MethodTypeEntry) entry;
	}

	/**
	 * Answer a {@link MethodTypeEntry} for the {@linkplain Method method}
	 * signature, constructing and installing a new entry if necessary.
	 *
	 * @param returnType
	 *        The method's return type.
	 * @param parameterTypes
	 *        The method's parameter types.
	 * @return The entry associated with the specified value.
	 */
	MethodTypeEntry methodType (
		final Class<?> returnType,
		final Class<?>... parameterTypes)
	{
		final String descriptor = JavaDescriptors.forMethod(
			returnType, parameterTypes);
		return methodType(descriptor);
	}

	/**
	 * Answer a {@link MethodTypeEntry} for the {@linkplain Method method}
	 * signature, constructing and installing a new entry if necessary.
	 *
	 * @param method
	 *        A method.
	 * @return The entry associated with the specified value.
	 */
	MethodTypeEntry methodType (final Method method)
	{
		final String descriptor = JavaDescriptors.forMethod(method);
		return methodType(descriptor);
	}

	// TODO: [TLS] Finish support for InvokeDynamicEntry.

	/**
	 * Write the {@linkplain ConstantPool constant pool} to the specified
	 * {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	void writeTo (final DataOutput out) throws IOException
	{
		out.writeShort(nextIndex);
		for (final Entry entry : entries.values())
		{
			entry.writeTo(out);
		}
	}
}
