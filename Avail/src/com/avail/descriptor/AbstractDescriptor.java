/**
 * AbstractDescriptor.java
 * Copyright (c) 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.descriptor;

import static com.avail.descriptor.Mutability.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.InfinityDescriptor.IntegerSlots;
import com.avail.descriptor.MapDescriptor.MapIterable;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.SetDescriptor.SetIterator;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.exceptions.AvailUnsupportedOperationException;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.*;
import com.avail.visitor.AvailSubobjectVisitor;

/**
 * {@link AbstractDescriptor} is the base descriptor type.  An {@link
 * AvailObject} contains a descriptor, to which it delegates nearly all of its
 * behavior.  That allows interesting operations like effective type mutation
 * (within a language that does not support it directly, such as Java).  It also
 * allows multiple representations of equivalent objects, such as more than one
 * representation for the tuple {@code <1,2,3>}.  It can be represented as an
 * AvailObject using either an {@link ObjectTupleDescriptor}, a {@link
 * ByteTupleDescriptor}, a {@link NybbleTupleDescriptor}, or a {@link
 * SpliceTupleDescriptor}.  It could even be an {@link IndirectionDescriptor} if
 * there is another object that already represents this tuple.
 *
 * <p>In particular, {@link AbstractDescriptor} is abstract and has two
 * children, the class {@link Descriptor} and the class {@link
 * IndirectionDescriptor}, the latter of which has no classes.  When a new
 * operation is added in an ordinary descriptor class (below {@code Descriptor
 * }), it should be added with an {@code @Override} annotation.  A quick fix on
 * that error allows an implementation to be generated in AbstractDescriptor,
 * which should be converted manually to an abstract method.  That will make
 * both {@code Descriptor} and {@code IndirectionDescriptor} (and all subclasses
 * of {@code Descriptor} except the one in which the new method first appeared)
 * to indicate an error, in that they need to implement this method.  A quick
 * fix can add it to {@code Descriptor}, after which it can be tweaked to
 * indicate a runtime error.  Another quick fix adds it to {@code
 * IndirectionDescriptor}, and copying nearby implementations leads it to
 * invoke the non "o_" method in {@link AvailObject}.  This will show up as an
 * error, and one more quick fix can generate the corresponding method in
 * {@code AvailObject} whose implementation, like methods near it, extracts the
 * {@link AvailObject#descriptor() descriptor} and invokes upon it the
 * original message (that started with "o_"), passing {@code this} as the first
 * argument.  Code generation will eventually make this relatively onerous task
 * more tractable and less error prone.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class AbstractDescriptor
{
	/** The {@linkplain Mutability mutability} of my instances. */
	final Mutability mutability;

	/**
	 * Are {@linkplain AvailObject objects} using this {@linkplain
	 * AbstractDescriptor descriptor} {@linkplain Mutability#MUTABLE mutable}?
	 *
	 * @return {@code true} if the described object is mutable, {@code false}
	 *         otherwise.
	 */
	public final boolean isMutable ()
	{
		return mutability == MUTABLE;
	}

	/**
	 * Answer the {@linkplain Mutability#MUTABLE mutable} version of this
	 * {@linkplain AbstractDescriptor descriptor}.
	 *
	 * @return A mutable descriptor equivalent to the receiver.
	 */
	abstract AbstractDescriptor mutable ();

	/**
	 * Answer the {@linkplain Mutability#IMMUTABLE immutable} version of this
	 * {@linkplain AbstractDescriptor descriptor}.
	 *
	 * @return An immutable descriptor equivalent to the receiver.
	 */
	abstract AbstractDescriptor immutable ();

	/**
	 * Answer the {@linkplain Mutability#SHARED shared} version of this
	 * {@linkplain AbstractDescriptor descriptor}.
	 *
	 * @return A shared descriptor equivalent to the receiver.
	 */
	abstract AbstractDescriptor shared ();

	/**
	 * Are {@linkplain AvailObject objects} using this {@linkplain
	 * AbstractDescriptor descriptor} {@linkplain Mutability#SHARED shared}?
	 *
	 * @return {@code true} if the described object is shared, {@code false}
	 *         otherwise.
	 */
	public final boolean isShared ()
	{
		return mutability == SHARED;
	}

	/**
	 * The minimum number of object slots an {@link AvailObject} can have if it
	 * uses this {@linkplain AbstractDescriptor descriptor}. Does not include
	 * indexed slots possibly at the end. Populated automatically by the
	 * constructor.
	 */
	protected final int numberOfFixedObjectSlots;

	/**
	 * Answer the minimum number of object slots an {@link AvailObject} can have
	 * if it uses this {@linkplain AbstractDescriptor descriptor}. Does not
	 * include indexed slots possibly at the end. Populated automatically by the
	 * constructor.
	 *
	 * @return The minimum number of object slots featured by an object using
	 *         this {@linkplain AbstractDescriptor descriptor}.
	 */
	final int numberOfFixedObjectSlots ()
	{
		return numberOfFixedObjectSlots;
	}

	/**
	 * The minimum number of integer slots an {@link AvailObject} can have if it
	 * uses this {@linkplain AbstractDescriptor descriptor}. Does not include
	 * indexed slots possibly at the end. Populated automatically by the
	 * constructor.
	 */
	protected final int numberOfFixedIntegerSlots;

	/**
	 * Answer the minimum number of integer slots an {@link AvailObject} can
	 * have if it uses this {@linkplain AbstractDescriptor descriptor}. Does not
	 * include indexed slots possibly at the end. Populated automatically by the
	 * constructor.
	 *
	 * @return The minimum number of integer slots featured by an object using
	 *         this {@linkplain AbstractDescriptor descriptor}.
	 */
	final int numberOfFixedIntegerSlots ()
	{
		return numberOfFixedIntegerSlots;
	}

	/**
	 * Whether an {@linkplain AvailObject object} using this {@linkplain
	 * AbstractDescriptor descriptor} can have more than the minimum number of
	 * object slots. Populated automatically by the constructor.
	 */
	final boolean hasVariableObjectSlots;

	/**
	 * Can an {@linkplain AvailObject object} using this {@linkplain
	 * AbstractDescriptor descriptor} have more than the {@linkplain
	 * #numberOfFixedObjectSlots() minimum number of object slots}?
	 *
	 * @return {@code true} if it is permissible for an {@linkplain AvailObject
	 *         object} using this {@linkplain AbstractDescriptor descriptor}
	 *         to have more than the {@linkplain #numberOfFixedObjectSlots()
	 *         minimum number of object slots}, {@code false} otherwise.
	 */
	protected final boolean hasVariableObjectSlots ()
	{
		return hasVariableObjectSlots;
	}

	/**
	 * Whether an {@linkplain AvailObject object} using this {@linkplain
	 * AbstractDescriptor descriptor} can have more than the minimum number of
	 * integer slots. Populated automatically by the constructor.
	 */
	final boolean hasVariableIntegerSlots;


	/**
	 * Can an {@linkplain AvailObject object} using this {@linkplain
	 * AbstractDescriptor descriptor} have more than the {@linkplain
	 * #numberOfFixedIntegerSlots() minimum number of integer slots}?
	 *
	 * @return {@code true} if it is permissible for an {@linkplain AvailObject
	 *         object} using this {@linkplain AbstractDescriptor descriptor}
	 *         to have more than the {@linkplain #numberOfFixedIntegerSlots()
	 *         minimum number of integer slots}, {@code false} otherwise.
	 */
	protected final boolean hasVariableIntegerSlots ()
	{
		return hasVariableIntegerSlots;
	}

	/**
	 * Note: This is a logical shift *without* Java's implicit modulus on the
	 * shift amount.
	 *
	 * @param value The value to shift.
	 * @param leftShift The amount to shift left. If negative, shift right by
	 *                  the corresponding positive amount.
	 * @return The shifted integer, modulus 2^32 then cast to {@code int}.
	 */
	protected static int bitShift (final int value, final int leftShift)
	{
		if (leftShift >= 32)
		{
			return 0;
		}
		if (leftShift >= 0)
		{
			return value << leftShift;
		}
		if (leftShift > -32)
		{
			return value >>> -leftShift;
		}
		return 0;
	}

	/**
	 * Note: This is a logical shift *without* Java's implicit modulus on the
	 * shift amount.
	 *
	 * @param value The value to shift.
	 * @param leftShift The amount to shift left. If negative, shift right by
	 *                  the corresponding positive amount.
	 * @return The shifted integer, modulus 2^64 then cast to {@code long}.
	 */
	protected static long bitShift (final long value, final int leftShift)
	{
		if (leftShift >= 64)
		{
			return 0L;
		}
		if (leftShift >= 0)
		{
			return value << leftShift;
		}
		if (leftShift > -64)
		{
			return value >>> -leftShift;
		}
		return 0L;
	}

	/**
	 * Note: This is an arithmetic (i.e., signed) shift *without* Java's
	 * implicit modulus on the shift amount.
	 *
	 * @param value The value to shift.
	 * @param leftShift The amount to shift left. If negative, shift right by
	 *                  the corresponding positive amount.
	 * @return The shifted integer, modulus 2^64 then cast to {@code long}.
	 */
	protected static long arithmeticBitShift (
		final long value,
		final int leftShift)
	{
		if (leftShift >= 64)
		{
			return 0L;
		}
		if (leftShift >= 0)
		{
			return value << leftShift;
		}
		if (leftShift > -64)
		{
			return value >> -leftShift;
		}
		// Preserve the sign.
		return value >> 63;
	}

	/**
	 * Construct a new {@linkplain AbstractDescriptor descriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	@SuppressWarnings("unchecked")
	protected AbstractDescriptor (final Mutability mutability)
	{
		this.mutability = mutability;

		final Class<Descriptor> cls = (Class<Descriptor>) this.getClass();
		final ClassLoader loader = cls.getClassLoader();
		Class<Enum<?>> enumClass;
		Enum<?>[] instances;

		try
		{
			enumClass = (Class<Enum<?>>) loader.loadClass(
				cls.getCanonicalName() + "$ObjectSlots");
		}
		catch (final ClassNotFoundException e)
		{
			enumClass = null;
		}
		instances = enumClass != null
			? enumClass.getEnumConstants()
			: new Enum<?>[0];
		hasVariableObjectSlots =
			instances.length > 0
			&& instances[instances.length-1].name().matches(".*_");
		numberOfFixedObjectSlots =
			instances.length - (hasVariableObjectSlots ? 1 : 0);

		try
		{
			enumClass = (Class<Enum<?>>) loader.loadClass(
				cls.getCanonicalName() + "$IntegerSlots");
		}
		catch (final ClassNotFoundException e)
		{
			enumClass = null;
		}
		instances = enumClass != null
			? enumClass.getEnumConstants()
			: new Enum<?>[0];
		hasVariableIntegerSlots =
			instances.length > 0
			&& instances[instances.length-1].name().matches(".*_");
		numberOfFixedIntegerSlots =
			instances.length - (hasVariableIntegerSlots ? 1 : 0);
	}


	/**
	 * Look up the specified {@link Annotation} from the {@link Enum} constant.
	 * If the enumeration constant does not have an annotation of that type then
	 * answer null.
	 *
	 * @param <A> The {@code Annotation} type.
	 * @param enumConstant The {@code Enum} value.
	 * @param annotationClass The {@link Class} of the {@code Annotation} type.
	 * @return
	 */
	private static @Nullable <A extends Annotation> A getAnnotation (
		final Enum<? extends Enum<?>> enumConstant,
		final Class<A> annotationClass)
	{
		final Class<?> enumClass = enumConstant.getClass();
		final Field slotMirror;
		try
		{
			slotMirror = enumClass.getField(enumConstant.name());
		}
		catch (final NoSuchFieldException e)
		{
			throw new RuntimeException(
				"Enum class didn't recognize its own instance",
				e);
		}
		assert annotationClass != null;
		return slotMirror.getAnnotation(annotationClass);
	}

	/**
	 * Describe the object for the Eclipse debugger.
	 *
	 * @param object
	 * @return
	 */
	@SuppressWarnings("unchecked")
	AvailObjectFieldHelper[] o_DescribeForDebugger (
		final AvailObject object)
	{
		final List<AvailObjectFieldHelper> fields =
			new ArrayList<AvailObjectFieldHelper>();
		final Class<Descriptor> cls = (Class<Descriptor>) this.getClass();
		final ClassLoader loader = cls.getClassLoader();
		Class<Enum<?>> enumClass;
		Enum<?>[] slots;

		try
		{
			enumClass = (Class<Enum<?>>) loader.loadClass(
				cls.getCanonicalName() + "$IntegerSlots");
		}
		catch (final ClassNotFoundException e)
		{
			enumClass = null;
		}
		if (enumClass != null)
		{
			slots = enumClass.getEnumConstants();
			for (int i = 0; i < numberOfFixedIntegerSlots; i++)
			{
				final Enum<?> slot = slots[i];
				if (getAnnotation(slot, HideFieldInDebugger.class) == null)
				{
					fields.add(
						new AvailObjectFieldHelper(
							object,
							(IntegerSlotsEnum)slot,
							-1,
							new AvailIntegerValueHelper(
								object.slot((IntegerSlotsEnum)slot))));
				}
			}
			final Enum<?> slot = slots[slots.length - 1];
			if (getAnnotation(slot, HideFieldInDebugger.class) == null)
			{
				for (
					int i = numberOfFixedIntegerSlots;
					i < object.integerSlotsCount();
					i++)
				{
					final int subscript = i - numberOfFixedIntegerSlots + 1;
					fields.add(
						new AvailObjectFieldHelper(
							object,
							(IntegerSlotsEnum)slot,
							subscript,
							new AvailIntegerValueHelper(
								object.slot(
									(IntegerSlotsEnum)slot,
									subscript))));
				}
			}
		}

		try
		{
			enumClass = (Class<Enum<?>>) loader.loadClass(
				cls.getCanonicalName() + "$ObjectSlots");
		}
		catch (final ClassNotFoundException e)
		{
			enumClass = null;
		}
		if (enumClass != null)
		{
			slots = enumClass.getEnumConstants();
			for (int i = 0; i < numberOfFixedObjectSlots; i++)
			{
				final Enum<?> slot = slots[i];
				if (getAnnotation(slot, HideFieldInDebugger.class) == null)
				{
					fields.add(
						new AvailObjectFieldHelper(
							object,
							(ObjectSlotsEnum)slot,
							-1,
							object.slot((ObjectSlotsEnum)slot)));
				}
			}
			final Enum<?> slot = slots[slots.length - 1];
			if (getAnnotation(slot, HideFieldInDebugger.class) == null)
			{
				for (
					int i = numberOfFixedObjectSlots;
					i < object.objectSlotsCount();
					i++)
				{
					final int subscript = i - numberOfFixedObjectSlots + 1;
					fields.add(
						new AvailObjectFieldHelper(
							object,
							(ObjectSlotsEnum)slot,
							subscript,
							object.slot((ObjectSlotsEnum)slot, subscript)));
				}
			}
		}

		return fields.toArray(new AvailObjectFieldHelper[fields.size()]);
	}


	/**
	 * Answer whether the field at the given offset is allowed to be modified
	 * even in an immutable object.
	 *
	 * @param e The byte offset of the field to check.
	 * @return Whether the specified field can be written even in an immutable
	 *         object.
	 */
	boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return false;
	}

	/**
	 * Answer how many levels of printing to allow before elision.
	 *
	 * @return The number of levels.
	 */
	int maximumIndent ()
	{
		return 12;
	}

	/**
	 * Ensure that the specified field is writable.
	 *
	 * @param e An {@code enum} value whose ordinal is the field position.
	 */
	final void checkWriteForField (final AbstractSlotsEnum e)
	{
		assert isMutable() || allowsImmutableToMutableReferenceInField(e);
	}

	/**
	 * Create a new {@linkplain AvailObject object} whose {@linkplain
	 * AbstractDescriptor descriptor} is the receiver, and which has the
	 * specified number of indexed (variable) slots.
	 *
	 * @param indexedSlotCount The number of variable slots to include.
	 * @return The new uninitialized {@linkplain AvailObject object}.
	 */
	final AvailObject create (final int indexedSlotCount)
	{
		return AvailObject.newIndexedDescriptor(indexedSlotCount, this);
	}

	/**
	 * Create a new {@linkplain AvailObject object} whose {@linkplain
	 * AbstractDescriptor descriptor} is the receiver, and which has no indexed
	 * (variable) slots.
	 *
	 * @return The new uninitialized {@linkplain AvailObject object}.
	 */
	final AvailObject create ()
	{
		return AvailObject.newIndexedDescriptor(0, this);
	}

	/**
	 * Print the {@linkplain AvailObject object} to the {@link StringBuilder}.
	 * By default show it as the {@linkplain AbstractDescriptor descriptor} name
	 * and a line-by-line list of fields. If the indent is beyond the {@link
	 * #maximumIndent() maximumIndent}, indicate it's too deep without
	 * recursing. If the object is in the specified recursion list, indicate a
	 * recursive print and return.
	 *
	 * @param object The object to print (its descriptor is me).
	 * @param builder Where to print the object.
	 * @param recursionList Which ancestor objects are currently being printed.
	 * @param indent What level to indent subsequent lines.
	 */
	@SuppressWarnings("unchecked")
	@ThreadSafe
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append('a');
		final String className = getClass().getSimpleName();
		final String shortenedName = className.substring(
			0,
			className.length() - 10);
		switch (shortenedName.codePointAt(0))
		{
			case 'A':
			case 'E':
			case 'I':
			case 'O':
			case 'U':
				builder.append('n');
				break;
			default:
				break;
		}
		builder.append(' ');
		builder.append(shortenedName);
		if (isMutable())
		{
			// Script capital M.
			builder.append("\u2133");
		}
		else if (isShared())
		{
			// Mathematical S.
			builder.append("\uD835\uDCAE");
		}
		final Class<Descriptor> cls = (Class<Descriptor>) this.getClass();
		final ClassLoader loader = cls.getClassLoader();
		Class<IntegerSlotsEnum> intEnumClass;

		try
		{
			intEnumClass = (Class<IntegerSlotsEnum>) loader.loadClass(
				cls.getCanonicalName() + "$IntegerSlots");
		}
		catch (final ClassNotFoundException e)
		{
			intEnumClass = null;
		}
		final IntegerSlotsEnum[] intSlots = intEnumClass != null
			? (IntegerSlotsEnum[])intEnumClass.getEnumConstants()
			: new IntegerSlotsEnum[0];

		for (int i = 1, limit = object.integerSlotsCount(); i <= limit; i++)
		{
			final int ordinal = Math.min(i, intSlots.length) - 1;
			final IntegerSlotsEnum slot = intSlots[ordinal];
			if (getAnnotation((Enum<?>)slot, HideFieldInDebugger.class) == null
				&& getAnnotation((Enum<?>)slot, HideFieldJustForPrinting.class)
					== null)
			{
				builder.append('\n');
				for (int tab = 0; tab < indent; tab++)
				{
					builder.append('\t');
				}
				final String slotName = slot.name();
				assert slotName != null;
				int value;
				if (slotName.charAt(slotName.length() - 1) == '_')
				{
					final int subscript = i - intSlots.length + 1;
					value = object.slot(slot, subscript);
					builder.append(slotName, 0, slotName.length() - 1);
					builder.append('[');
					builder.append(subscript);
					builder.append("]");
				}
				else
				{
					value = object.slot(slot);
					builder.append(slotName);
				}
				builder.append(" = ");
				builder.append(value);
				describeIntegerSlot(object, value, slot, builder);
			}
		}

		Class<ObjectSlotsEnum> objectEnumClass;
		try
		{
			objectEnumClass = (Class<ObjectSlotsEnum>) loader.loadClass(
				cls.getCanonicalName() + "$ObjectSlots");
		}
		catch (final ClassNotFoundException e)
		{
			objectEnumClass = null;
		}
		final ObjectSlotsEnum[] objectSlots = objectEnumClass != null
			? (ObjectSlotsEnum[])objectEnumClass.getEnumConstants()
			: new ObjectSlotsEnum[0];

		for (int i = 1, limit = object.objectSlotsCount(); i <= limit; i++)
		{
			final int ordinal = Math.min(i, objectSlots.length) - 1;
			final ObjectSlotsEnum slot = objectSlots[ordinal];
			if (getAnnotation((Enum<?>)slot, HideFieldInDebugger.class) == null
				&& getAnnotation((Enum<?>)slot, HideFieldJustForPrinting.class)
					== null)
			{
				builder.append('\n');
				for (int tab = 0; tab < indent; tab++)
				{
					builder.append('\t');
				}
				final String slotName = slot.name();
				assert slotName != null;
				if (slotName.charAt(slotName.length() - 1) == '_')
				{
					final int subscript = i - objectSlots.length + 1;
					builder.append(slotName, 0, slotName.length() - 1);
					builder.append('[');
					builder.append(subscript);
					builder.append("] = ");
					object.slot(slot, subscript).printOnAvoidingIndent(
						builder,
						recursionList,
						indent + 1);
				}
				else
				{
					builder.append(slotName);
					builder.append(" = ");
					object.slot(slot).printOnAvoidingIndent(
						builder,
						recursionList,
						indent + 1);
				}
			}
		}
	}

	/**
	 * A static cache of mappings from {@link IntegerSlotsEnum integer slots} to
	 * {@link List}s of {@link BitField}s.  Access to the map must be
	 * synchronized, which isn't much of a penalty since it only affects the
	 * default object printing mechanism.
	 */
	private static final Map<IntegerSlotsEnum, List<BitField>> bitFieldsCache =
		new HashMap<IntegerSlotsEnum, List<BitField>>(500);

	/**
	 * Describe the integer field onto the provided {@link StringDescriptor}.
	 * The pre-extracted {@code int} value is provided, as well as the
	 * containing {@link AvailObject} and the {@link IntegerSlotsEnum} instance.
	 * Take into account annotations on the slot enumeration object which may
	 * define the way it should be described.
	 *
	 * @param object The object containing the {@code int} value in some slot.
	 * @param value The {@code int} value of the slot.
	 * @param slot The {@linkplain IntegerSlotsEnum integer slot} definition.
	 * @param builder Where to write the description.
	 */
	static void describeIntegerSlot (
		final AvailObject object,
		final int value,
		final IntegerSlotsEnum slot,
		final StringBuilder builder)
	{
		try
		{
			List<BitField> bitFields;
			synchronized(bitFieldsCache)
			{
				bitFields = bitFieldsCache.get(slot);
				if (bitFields == null)
				{
					final Enum<?> slotAsEnum = (Enum<?>) slot;
					final Class<?> slotClass = slotAsEnum.getDeclaringClass();
					bitFields = new ArrayList<BitField>();
					for (final Field field : slotClass.getDeclaredFields())
					{
						if (Modifier.isStatic(field.getModifiers())
							&& BitField.class.isAssignableFrom(field.getType()))
						{
							final BitField bitField =
								(BitField) (field.get(null));
							if (bitField.integerSlot == slot)
							{
								bitField.name = field.getName();
								bitFields.add(bitField);
							}
						}
					}
					Collections.sort(bitFields);
					bitFieldsCache.put(slot, bitFields);
				}
			}
			final Field slotMirror = slot.getClass().getField(slot.name());
			final EnumField enumAnnotation =
				slotMirror.getAnnotation(EnumField.class);
			if (enumAnnotation != null)
			{
				final Class<? extends IntegerEnumSlotDescriptionEnum>
					describingClass = enumAnnotation.describedBy();
				final String lookupName = enumAnnotation.lookupMethodName();
				if (lookupName.isEmpty())
				{
					// Look it up by ordinal (must be an actual Enum).
					final IntegerEnumSlotDescriptionEnum[] allValues =
						describingClass.getEnumConstants();
					if (0 <= value && value < allValues.length)
					{
						builder.append(" = ");
						builder.append(allValues[value].name());
					}
					else
					{
						builder.append(
							new Formatter().format(
								" (enum out of range: 0x%08X)",
								value & 0xFFFFFFFFL));
					}
				}
				else
				{
					// Look it up via the specified static lookup method.
					// It's only required to be an
					// IntegerEnumSlotDescriptionEnum in this case, not
					// necessarily an Enum.
					final Method lookupMethod =
						describingClass.getMethod(
							lookupName,
							Integer.TYPE);
					final IntegerEnumSlotDescriptionEnum lookedUp =
						(IntegerEnumSlotDescriptionEnum)lookupMethod.invoke(
							null,
							value);
					if (lookedUp == null)
					{
						builder.append(
							new Formatter().format(
								" (enum out of range: 0x%08X)",
								value & 0xFFFFFFFFL));
					}
					else
					{
						if (lookedUp instanceof Enum)
						{
							assert ((Enum<?>)lookedUp).getDeclaringClass()
								== describingClass;
						}
						builder.append(" = ");
						builder.append(lookedUp.name());
					}
				}
			}
			else if (!bitFields.isEmpty())
			{
				// Show each bit field.
				assert object != null;
				builder.append(" (");
				boolean first = true;
				for (final BitField bitField : bitFields)
				{
					if (!first)
					{
						builder.append(", ");
					}
					builder.append(bitField.name);
					builder.append("=");
					final int subfieldValue = object.slot(bitField);
					builder.append(subfieldValue);
					first = false;
				}
				builder.append(")");
			}
			else
			{
				builder.append(
					new Formatter().format(" = 0x%08X", value & 0xFFFFFFFFL));
			}
		}
		catch (final NoSuchFieldException e)
		{
			throw new RuntimeException(e);
		}
		catch (final IllegalAccessException e)
		{
			throw new RuntimeException(e);
		}
		catch (final SecurityException e)
		{
			throw new RuntimeException(e);
		}
		catch (final NoSuchMethodException e)
		{
			throw new RuntimeException(e);
		}
		catch (final IllegalArgumentException e)
		{
			throw new RuntimeException(e);
		}
		catch (final InvocationTargetException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Create a {@link BitField} for the specified {@link IntegerSlotsEnum
	 * integer slot}.  The {@code BitField} should be stored back into a static
	 * field of the {@link IntegerSlotsEnum} subclass in which the integer slot
	 * is defined.  This method may be quite slow, so it should only be invoked
	 * by static code during class loading.
	 *
	 * @param integerSlot
	 *            The {@linkplain IntegerSlotsEnum integer slot} in which this
	 *            {@link BitField} will occur.
	 * @param shift
	 *            The position of the lowest order bit of this {@code BitField}.
	 * @param bits
	 *            The number of bits occupied by this {@code BitField}.
	 * @return A BitField
	 */
	static BitField bitField (
		final IntegerSlotsEnum integerSlot,
		final int shift,
		final int bits)
	{
		return new BitField(
			integerSlot,
			shift,
			bits);
	}

	/**
	 * Answer an {@linkplain AvailUnsupportedOperationException unsupported
	 * operation exception} suitable to be thrown by the sender.  We don't throw
	 * it here, since Java sadly has no way of indicating that a method
	 * <em>always</em> throws an exception (i.e., doesn't return), forcing one
	 * to have to add stupid dead statements like {@code return null;} after the
	 * never-returning call.
	 *
	 * <p>
	 * The exception indicates that the receiver does not meaningfully implement
	 * the method that immediately invoked this.  This is a strong indication
	 * that the wrong kind of object is being used somewhere.
	 * </p>
	 *
	 * @return an AvailUnsupportedOperationException suitable to be thrown.
	 */
	public AvailUnsupportedOperationException unsupportedOperationException ()
	{
		final String callerName;
		try
		{
			throw new Exception("just want the caller's frame");
		}
		catch (final Exception e)
		{
			callerName = e.getStackTrace()[1].getMethodName();
		}
		return new AvailUnsupportedOperationException(getClass(), callerName);
	}

	/**
	 * Answer whether the {@linkplain AvailObject#argsTupleType() argument
	 * types} supported by the specified {@linkplain FunctionTypeDescriptor
	 * function type} are acceptable argument types for invoking a {@linkplain
	 * FunctionDescriptor function} whose type is the {@code object}.
	 *
	 * @see AvailObject#acceptsArgTypesFromFunctionType(AvailObject)
	 * @param object A function type.
	 * @param functionType A function type.
	 * @return {@code true} if the arguments of {@code object} are, pairwise,
	 *         more general than those of {@code functionType}, {@code false}
	 *         otherwise.
	 */
	abstract boolean o_AcceptsArgTypesFromFunctionType (
		AvailObject object,
		AvailObject functionType);

	/**
	 * Answer whether these are acceptable {@linkplain TypeDescriptor argument
	 * types} for invoking a {@linkplain FunctionDescriptor function} whose type
	 * is the {@code object}.
	 *
	 * @see AvailObject#acceptsListOfArgTypes(List)
	 * @param object The receiver.
	 * @param argTypes A list containing the argument types to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than those within the {@code argTypes} list, {@code
	 *         false} otherwise.
	 */
	abstract boolean o_AcceptsListOfArgTypes (
		AvailObject object,
		List<AvailObject> argTypes);

	/**
	 * Answer whether these are acceptable arguments for invoking a {@linkplain
	 * FunctionDescriptor function} whose type is the {@code object}.
	 *
	 * @see AvailObject#acceptsListOfArgValues(List)
	 * @param object The receiver.
	 * @param argValues A list containing the argument values to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than the types of the values within the {@code argValues}
	 *         list, {@code false} otherwise.
	 */
	abstract boolean o_AcceptsListOfArgValues (
		AvailObject object,
		List<AvailObject> argValues);

	/**
	 * Answer whether these are acceptable {@linkplain TypeDescriptor argument
	 * types} for invoking a {@linkplain FunctionDescriptor function} that is an
	 * instance of {@code object}. There may be more entries in the {@linkplain
	 * TupleDescriptor tuple} than are required by the {@linkplain
	 * FunctionTypeDescriptor function type}.
	 *
	 * @see AvailObject#acceptsTupleOfArgTypes(AvailObject)
	 * @param object The receiver.
	 * @param argTypes A tuple containing the argument types to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than the corresponding elements of the {@code argTypes}
	 *         tuple, {@code false} otherwise.
	 */
	abstract boolean o_AcceptsTupleOfArgTypes (
		AvailObject object,
		AvailObject argTypes);

	/**
	 * Answer whether these are acceptable arguments for invoking a {@linkplain
	 * FunctionDescriptor function} that is an instance of {@code object}. There
	 * may be more entries in the {@linkplain TupleDescriptor tuple} than are
	 * required by the {@linkplain FunctionTypeDescriptor function type}.
	 *
	 * @param object The receiver.
	 * @param arguments A tuple containing the argument values to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than the types of the corresponding elements of the
	 *         {@code arguments} tuple, {@code false} otherwise.
	 * @see AvailObject#acceptsTupleOfArguments(AvailObject)
	 */
	abstract boolean o_AcceptsTupleOfArguments (
		AvailObject object,
		AvailObject arguments);

	/**
	 *
	 *
	 * @param object
	 * @param aChunkIndex
	 */
	abstract void o_AddDependentChunkIndex (
		AvailObject object,
		int aChunkIndex);

	/**
	 * Add a {@linkplain DefinitionDescriptor definition} to the receiver.
	 * Causes dependent chunks to be invalidated.
	 *
	 * Macro signatures and non-macro definitions should not be combined in the
	 * same method.
	 *
	 * @param object The receiver.
	 * @param definition The definition to be added.
	 * @throws SignatureException
	 *         If the definition could not be added.
	 * @see AvailObject#methodAddDefinition(AvailObject)
	 */
	abstract void o_MethodAddDefinition (
			AvailObject object,
			AvailObject definition)
		throws SignatureException;

	/**
	 * Add a set of {@linkplain MessageBundleDescriptor grammatical
	 * restrictions} to the receiver.
	 *
	 * @param object The receiver.
	 * @param restrictions The set of grammatical restrictions to be added.
	 * @see AvailObject#addGrammaticalRestrictions(AvailObject)
	 */
	abstract void o_AddGrammaticalRestrictions (
		AvailObject object,
		AvailObject restrictions);

	/**
	 * Add the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#plusCanDestroy(AvailObject, boolean) plusCanDestroy}. It
	 * exists for double-dispatch only.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param sign
	 *        The {@linkplain Sign sign} of the infinity.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_AddToInfinityCanDestroy (
		AvailObject object,
		Sign sign,
		boolean canDestroy);

	/**
	 * Add the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#plusCanDestroy(AvailObject, boolean) plusCanDestroy}. It
	 * exists for double-dispatch only.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param anInteger
	 *        An {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_AddToIntegerCanDestroy (
		AvailObject object,
		AvailObject anInteger,
		boolean canDestroy);

	/**
	 * @param object
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	abstract void o_AddGrammaticalMessageRestrictions (
		AvailObject object,
		AvailObject methodName,
		AvailObject illegalArgMsgs);

	/**
	 * @param object
	 * @param definition
	 */
	abstract void o_ModuleAddDefinition (
		AvailObject object,
		AvailObject definition);

	/**
	 * @param object
	 * @param message
	 * @param bundle
	 */
	abstract void o_AtMessageAddBundle (
		AvailObject object,
		AvailObject message,
		AvailObject bundle);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	abstract void o_AtNameAdd (
		AvailObject object,
		AvailObject stringName,
		AvailObject trueName);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	abstract void o_AtNewNamePut (
		AvailObject object,
		AvailObject stringName,
		AvailObject trueName);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	abstract void o_AtPrivateNameAdd (
		AvailObject object,
		AvailObject stringName,
		AvailObject trueName);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_BinElementAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_BinElementAtPut (
		AvailObject object,
		int index,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_BinHash (AvailObject object, int value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_BinSize (AvailObject object, int value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_BitVector (
		AvailObject object,
		int value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_BreakpointBlock (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param bundleTree
	 */
	abstract void o_BuildFilteredBundleTreeFrom (
		AvailObject object,
		AvailObject bundleTree);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Caller (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Function (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Code (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_CodePoint (
		AvailObject object,
		int value);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of another object. The size of the subrange of both objects is
	 * determined by the index range supplied for the receiver.
	 *
	 * @param object
	 *        The receiver.
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anotherObject
	 *        The other object used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the other object's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 * @see AvailObject#compareFromToWithStartingAt(int, int, AvailObject, int)
	 */
	abstract boolean o_CompareFromToWithStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject anotherObject,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain TupleDescriptor tuple}. The size of the
	 * subrange of both objects is determined by the index range supplied for
	 * the receiver.
	 *
	 * @param object
	 *        The receiver.
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aTuple
	 *        The tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 * @see AvailObject#compareFromToWithAnyTupleStartingAt(int, int, AvailObject, int)
	 */
	abstract boolean o_CompareFromToWithAnyTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject aTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain ByteStringDescriptor byte string}. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param object
	 *        The receiver.
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aByteString
	 *        The byte string used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte string's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 * @see AvailObject#compareFromToWithByteStringStartingAt(int, int, AvailObject, int)
	 */
	abstract boolean o_CompareFromToWithByteStringStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject aByteString,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain ByteTupleDescriptor byte tuple}. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param object
	 *        The receiver.
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aByteTuple
	 *        The byte tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 * @see AvailObject#compareFromToWithByteTupleStartingAt(int, int, AvailObject, int)
	 */
	abstract boolean o_CompareFromToWithByteTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject aByteTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain NybbleTupleDescriptor nybble tuple}.
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param object
	 *        The receiver.
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aNybbleTuple
	 *        The nybble tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the nybble tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 * @see AvailObject#compareFromToWithNybbleTupleStartingAt(int, int, AvailObject, int)
	 */
	abstract boolean o_CompareFromToWithNybbleTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject aNybbleTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain ObjectTupleDescriptor object tuple}.
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param object
	 *        The receiver.
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anObjectTuple
	 *        The object tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the object tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 * @see AvailObject#compareFromToWithObjectTupleStartingAt(int, int, AvailObject, int)
     */
	abstract boolean o_CompareFromToWithObjectTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject anObjectTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain TwoByteStringDescriptor two-byte
	 * string}. The size of the subrange of both objects is determined by the
	 * index range supplied for the receiver.
	 *
	 * @param object
	 *        The receiver.
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aTwoByteString
	 *        The two-byte string used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the two-byte string's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 * @see AvailObject#compareFromToWithTwoByteStringStartingAt(int, int, AvailObject, int)
     */
	abstract boolean o_CompareFromToWithTwoByteStringStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject aTwoByteString,
		int startIndex2);

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @return
	 */
	abstract int o_ComputeHashFromTo (
		AvailObject object,
		int start,
		int end);

	/**
	 * @param object
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_ConcatenateTuplesCanDestroy (
		AvailObject object,
		boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Continuation (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param filteredBundleTree
	 * @param visibleNames
	 */
	abstract void o_CopyToRestrictedTo (
		AvailObject object,
		AvailObject filteredBundleTree,
		AvailObject visibleNames);

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_CopyTupleFromToCanDestroy (
		AvailObject object,
		int start,
		int end,
		boolean canDestroy);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	abstract boolean o_CouldEverBeInvokedWith (
		AvailObject object,
		List<AvailObject> argTypes);

	/**
	 * Divide the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#divideIntoIntegerCanDestroy(AvailObject, boolean)
	 * divideIntoIntegerCanDestroy} or {@link
	 * AvailObject#divideIntoInfinityCanDestroy(Sign, boolean)
	 * divideIntoInfinityCanDestroy}, where actual implementations of the
	 * division operation should reside.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_DivideCanDestroy (
		AvailObject object,
		AvailObject aNumber,
		boolean canDestroy);

	/**
	 * Divide an infinity with the given {@linkplain Sign sign} by the
	 * {@linkplain AvailObject object} and answer the {@linkplain AvailObject
	 * result}.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#divideCanDestroy(AvailObject, boolean) divideCanDestroy}. It
	 * exists for double-dispatch only.</p>

	 * @param object
	 *        The divisor, an integral numeric.
	 * @param sign
	 *        The sign of the infinity.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_DivideIntoInfinityCanDestroy (
		AvailObject object,
		Sign sign,
		boolean canDestroy);

	/**
	 * Divide the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#divideCanDestroy(AvailObject, boolean) divideCanDestroy}. It
	 * exists for double-dispatch only.</p>

	 * @param object
	 *        The divisor, an integral numeric.
	 * @param anInteger
	 *        The dividend, an {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either operand, {@code
	 *        false} otherwise.
	 * @return The result of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_DivideIntoIntegerCanDestroy (
		AvailObject object,
		AvailObject anInteger,
		boolean canDestroy);

	/**
	 * Answer the element at the given index of the {@code AvailObject object}.
	 *
	 * @param object A sequenceable collection.
	 * @param index An integer.
	 * @return The element at the given index.
	 * @see AvailObject#elementAt(int)
	 */
	abstract AvailObject o_ElementAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_ElementAtPut (
		AvailObject object,
		int index,
		AvailObject value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	abstract int o_EndOfZone (
		AvailObject object,
		int zone);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	abstract int o_EndSubtupleIndexInZone (
		AvailObject object,
		int zone);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_ExecutionState (
		AvailObject object,
		ExecutionState value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract byte o_ExtractNybbleFromTupleAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	abstract List<AvailObject> o_FilterByTypes (
		AvailObject object,
		List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param zone
	 * @param newSubtuple
	 * @param startSubtupleIndex
	 * @param endOfZone
	 * @return
	 */
	abstract AvailObject o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone (
		AvailObject object,
		int zone,
		AvailObject newSubtuple,
		int startSubtupleIndex,
		int endOfZone);

	/**
	 * Answer whether the {@linkplain AvailObject receiver} contains the
	 * specified element.
	 *
	 * @param object The receiver.
	 * @param elementObject The element.
	 * @return {@code true} if the receiver contains the element, {@code false}
	 *         otherwise.
	 * @see AvailObject#hasElement(AvailObject)
	 */
	abstract boolean o_HasElement (
		AvailObject object,
		AvailObject elementObject);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	abstract int o_HashFromTo (
		AvailObject object,
		int startIndex,
		int endIndex);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_HashOrZero (
		AvailObject object,
		int value);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	abstract boolean o_HasKey (
		AvailObject object,
		AvailObject keyObject);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	abstract List<AvailObject> o_DefinitionsAtOrBelow (
		AvailObject object,
		List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param messageBundle
	 * @return
	 */
	abstract AvailObject o_IncludeBundle (
		AvailObject object,
		AvailObject messageBundle);

	/**
	 * @param object
	 * @param definition
	 * @return
	 */
	abstract boolean o_IncludesDefinition (
		AvailObject object,
		AvailObject definition);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Index (
		AvailObject object,
		int value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_SetInterruptRequestFlag (
		AvailObject object,
		BitField value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_CountdownToReoptimize (
		AvailObject object,
		int value);

	/**
	 * @param object
	 * @param aBoolean
	 */
	@Deprecated
	abstract void o_IsSaved (
		AvailObject object,
		boolean aBoolean);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract boolean o_IsSubsetOf (
		AvailObject object,
		AvailObject another);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_IsSubtypeOf (
		AvailObject object,
		AvailObject aType);

	/**
	 * @param object
	 * @param aVariableType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfVariableType (
		AvailObject object,
		AvailObject aVariableType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfContinuationType (
		AvailObject object,
		AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfCompiledCodeType (
		AvailObject object,
		AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfFunctionType (
		AvailObject object,
		AvailObject aFunctionType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfIntegerRangeType (
		AvailObject object,
		AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfMapType (
		AvailObject object,
		AvailObject aMapType);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfObjectType (
		AvailObject object,
		AvailObject anObjectType);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfParseNodeType (
		AvailObject object,
		AvailObject aParseNodeType);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfPojoType (
		AvailObject object,
		AvailObject aPojoType);

	/**
	 * @param object
	 * @param primitiveTypeEnum
	 * @return
	 */
	abstract boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfSetType (
		AvailObject object,
		AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfTupleType (
		AvailObject object,
		AvailObject aTupleType);

	/**
	 * @param object
	 * @param anEnumerationType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfEnumerationType (
		AvailObject object,
		AvailObject anEnumerationType);

	/**
	 * @param object
	 * @param chunk
	 * @param offset
	 */
	abstract void o_LevelTwoChunkOffset (
		AvailObject object,
		AvailObject chunk,
		int offset);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_LiteralAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_ArgOrLocalOrStackAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_ArgOrLocalOrStackAtPut (
		AvailObject object,
		int index,
		AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_LocalTypeAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param argumentTypeTuple
	 * @return
	 */
	abstract AvailObject o_LookupByTypesFromTuple (
		AvailObject object,
		AvailObject argumentTypeTuple);

	/**
	 * @param object
	 * @param argumentList
	 * @return
	 */
	abstract AvailObject o_LookupByValuesFromList (
		AvailObject object,
		List<AvailObject> argumentList);

	/**
	 * @param object
	 * @param argumentTuple
	 * @return
	 */
	abstract AvailObject o_LookupByValuesFromTuple (
		AvailObject object,
		AvailObject argumentTuple);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	abstract AvailObject o_MapAt (
		AvailObject object,
		AvailObject keyObject);

	/**
	 * @param object
	 * @param keyObject
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_MapAtPuttingCanDestroy (
		AvailObject object,
		AvailObject keyObject,
		AvailObject newValueObject,
		boolean canDestroy);

	/**
	 * @param object
	 * @param keyObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_MapWithoutKeyCanDestroy (
		AvailObject object,
		AvailObject keyObject,
		boolean canDestroy);

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#subtractFromIntegerCanDestroy(AvailObject, boolean)
	 * subtractFromIntegerCanDestroy} or {@link
	 * AvailObject#subtractFromInfinityCanDestroy(Sign, boolean)
	 * subtractFromInfinityCanDestroy}, where actual implementations of the
	 * subtraction operation should reside.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_MinusCanDestroy (
		AvailObject object,
		AvailObject aNumber,
		boolean canDestroy);

	/**
	 * Multiply the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#timesCanDestroy(AvailObject, boolean) timesCanDestroy}. It
	 * exists for double-dispatch only.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param sign
	 *        The {@link Sign} of the {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         IntegerDescriptor#zero() zero} and {@linkplain InfinityDescriptor
	 *         infinity}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_MultiplyByInfinityCanDestroy (
		AvailObject object,
		Sign sign,
		boolean canDestroy);

	/**
	 * Multiply the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#timesCanDestroy(AvailObject, boolean) timesCanDestroy}. It
	 * exists for double-dispatch only.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param anInteger
	 *        An {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_MultiplyByIntegerCanDestroy (
		AvailObject object,
		AvailObject anInteger,
		boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Name (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param trueName
	 * @return
	 */
	abstract boolean o_NameVisible (
		AvailObject object,
		AvailObject trueName);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract boolean o_OptionallyNilOuterVar (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_OuterTypeAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_OuterVarAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_OuterVarAtPut (
		AvailObject object,
		int index,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Parent (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Pc (AvailObject object, int value);

	/**
	 * Add the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#addToIntegerCanDestroy(AvailObject, boolean)
	 * addToIntegerCanDestroy} or {@link
	 * AvailObject#addToInfinityCanDestroy(Sign, boolean)
	 * addToInfinityCanDestroy}, where actual implementations of the addition
	 * operation should reside.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 */
	abstract AvailObject o_PlusCanDestroy (
		AvailObject object,
		AvailObject aNumber,
		boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Priority (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	abstract AvailObject o_PrivateAddElement (
		AvailObject object,
		AvailObject element);

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	abstract AvailObject o_PrivateExcludeElement (
		AvailObject object,
		AvailObject element);

	/**
	 * @param object
	 * @param element
	 * @param knownIndex
	 * @return
	 */
	abstract AvailObject o_PrivateExcludeElementKnownIndex (
		AvailObject object,
		AvailObject element,
		int knownIndex);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_FiberGlobals (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract short o_RawByteAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	abstract void o_RawByteAtPut (
		AvailObject object,
		int index,
		short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract short o_RawByteForCharacterAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	abstract void o_RawByteForCharacterAtPut (
		AvailObject object,
		int index,
		short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract byte o_RawNybbleAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param aNybble
	 */
	abstract void o_RawNybbleAtPut (
		AvailObject object,
		int index,
		byte aNybble);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract int o_RawShortForCharacterAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	abstract void o_RawShortForCharacterAtPut (
		AvailObject object,
		int index,
		int anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract int o_RawSignedIntegerAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_RawSignedIntegerAtPut (
		AvailObject object,
		int index,
		int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract long o_RawUnsignedIntegerAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_RawUnsignedIntegerAtPut (
		AvailObject object,
		int index,
		int value);

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	abstract void o_RemoveDependentChunkIndex (
		AvailObject object,
		int aChunkIndex);

	/**
	 * @param object
	 * @param anInterpreter
	 */
	abstract void o_RemoveFrom (
		AvailObject object,
		L2Interpreter anInterpreter);

	/**
	 * @param object
	 * @param definition
	 */
	abstract void o_RemoveDefinition (
		AvailObject object,
		AvailObject definition);

	/**
	 * @param object
	 * @param message
	 * @return
	 */
	abstract boolean o_RemoveBundleNamed (
		AvailObject object,
		AvailObject message);

	/**
	 * @param object
	 * @param obsoleteRestrictions
	 */
	abstract void o_RemoveGrammaticalRestrictions (
		AvailObject object,
		AvailObject obsoleteRestrictions);

	/**
	 * @param object
	 * @param forwardImplementation
	 * @param methodName
	 */
	abstract void o_ResolvedForwardWithName (
		AvailObject object,
		AvailObject forwardImplementation,
		AvailObject methodName);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SetIntersectionCanDestroy (
		AvailObject object,
		AvailObject otherSet,
		boolean canDestroy);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SetMinusCanDestroy (
		AvailObject object,
		AvailObject otherSet,
		boolean canDestroy);

	/**
	 * @param object
	 * @param zoneIndex
	 * @param newTuple
	 */
	abstract void o_SetSubtupleForZoneTo (
		AvailObject object,
		int zoneIndex,
		AvailObject newTuple);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SetUnionCanDestroy (
		AvailObject object,
		AvailObject otherSet,
		boolean canDestroy);

	/**
	 * @param object
	 * @param newValue
	 */
	abstract void o_SetValue (
		AvailObject object,
		AvailObject newValue);

	/**
	 * @param object
	 * @param newValue
	 */
	abstract void o_SetValueNoCheck (
		AvailObject object,
		AvailObject newValue);

	/**
	 * @param object
	 * @param newElementObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SetWithElementCanDestroy (
		AvailObject object,
		AvailObject newElementObject,
		boolean canDestroy);

	/**
	 * @param object
	 * @param elementObjectToExclude
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SetWithoutElementCanDestroy (
		AvailObject object,
		AvailObject elementObjectToExclude,
		boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Size (AvailObject object, int value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	abstract int o_SizeOfZone (AvailObject object, int zone);

	/**
	 * @param object
	 * @param slotIndex
	 * @return
	 */
	abstract AvailObject o_StackAt (
		AvailObject object,
		int slotIndex);

	/**
	 * @param object
	 * @param slotIndex
	 * @param anObject
	 */
	abstract void o_StackAtPut (
		AvailObject object,
		int slotIndex,
		AvailObject anObject);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Stackp (AvailObject object, int value);

	/**
	 * @param object
	 * @param chunk
	 * @param countdown
	 */
	abstract void o_SetStartingChunkAndReoptimizationCountdown (
		AvailObject object,
		AvailObject chunk,
		int countdown);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	abstract int o_StartOfZone (
		AvailObject object,
		int zone);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	abstract int o_StartSubtupleIndexInZone (
		AvailObject object,
		int zone);

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#subtractFromIntegerCanDestroy(AvailObject, boolean)
	 * subtractFromIntegerCanDestroy} or {@link
	 * AvailObject#subtractFromInfinityCanDestroy(Sign, boolean)
	 * subtractFromInfinityCanDestroy}, where actual implementations of the
	 * subtraction operation should reside.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param sign
	 *        The {@link Sign} of the {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_SubtractFromInfinityCanDestroy (
		AvailObject object,
		Sign sign,
		boolean canDestroy);

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#subtractFromIntegerCanDestroy(AvailObject, boolean)
	 * subtractFromIntegerCanDestroy} or {@link
	 * AvailObject#subtractFromInfinityCanDestroy(Sign, boolean)
	 * subtractFromInfinityCanDestroy}, where actual implementations of the
	 * subtraction operation should reside.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param anInteger
	 *        An {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_SubtractFromIntegerCanDestroy (
		AvailObject object,
		AvailObject anInteger,
		boolean canDestroy);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	abstract AvailObject o_SubtupleForZone (
		AvailObject object,
		int zone);

	/**
	 * Multiply the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>
	 * Implementations may double-dispatch to {@link
	 * AvailObject#multiplyByIntegerCanDestroy(AvailObject, boolean)
	 * multiplyByIntegerCanDestroy} or {@link
	 * AvailObject#multiplyByInfinityCanDestroy(Sign, boolean)
	 * multiplyByInfinityCanDestroy}, where actual implementations of the
	 * multiplication operation should reside.  Other implementations may exist
	 * for other type families (e.g., floating point).
	 * </p>
	 *
	 * @param object
	 *        A {@linkplain AbstractNumberDescriptor numeric} value.
	 * @param aNumber
	 *        Another {@linkplain AbstractNumberDescriptor numeric} value.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either operand,
	 *        {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_TimesCanDestroy (
		AvailObject object,
		AvailObject aNumber,
		boolean canDestroy);

	/**
	 * @param object
	 * @param tupleIndex
	 * @param zoneIndex
	 * @return
	 */
	abstract int o_TranslateToZone (
		AvailObject object,
		int tupleIndex,
		int zoneIndex);

	/**
	 * @param object
	 * @param stringName
	 * @return
	 */
	abstract AvailObject o_TrueNamesForStringName (
		AvailObject object,
		AvailObject stringName);

	/**
	 * @param object
	 * @param newTupleSize
	 * @return
	 */
	abstract AvailObject o_TruncateTo (
		AvailObject object,
		int newTupleSize);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_TupleAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param aNybbleObject
	 */
	abstract void o_TupleAtPut (
		AvailObject object,
		int index,
		AvailObject aNybbleObject);

	/**
	 * @param object
	 * @param index
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_TupleAtPuttingCanDestroy (
		AvailObject object,
		int index,
		AvailObject newValueObject,
		boolean canDestroy);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract int o_TupleIntAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Type (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_TypeAtIndex (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract AvailObject o_TypeIntersection (
		AvailObject object,
		AvailObject another);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfFunctionType (
		AvailObject object,
		AvailObject aFunctionType);

	/**
	 * @param object
	 * @param aVariableType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfVariableType (
		AvailObject object,
		AvailObject aVariableType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfContinuationType (
		AvailObject object,
		AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfCompiledCodeType (
		AvailObject object,
		AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfIntegerRangeType (
		AvailObject object,
		AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfMapType (
		AvailObject object,
		AvailObject aMapType);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfObjectType (
		AvailObject object,
		AvailObject anObjectType);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfParseNodeType (
		AvailObject object,
		AvailObject aParseNodeType);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfPojoType (
		AvailObject object,
		AvailObject aPojoType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfSetType (
		AvailObject object,
		AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfTupleType (
		AvailObject object,
		AvailObject aTupleType);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract AvailObject o_TypeUnion (
		AvailObject object,
		AvailObject another);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfFunctionType (
		AvailObject object,
		AvailObject aFunctionType);

	/**
	 * @param object
	 * @param aVariableType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfVariableType (
		AvailObject object,
		AvailObject aVariableType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfContinuationType (
		AvailObject object,
		AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfCompiledCodeType (
		AvailObject object,
		AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfIntegerRangeType (
		AvailObject object,
		AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfMapType (
		AvailObject object,
		AvailObject aMapType);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfObjectType (
		AvailObject object,
		AvailObject anObjectType);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfPojoType (
		AvailObject object,
		AvailObject aPojoType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfSetType (
		AvailObject object,
		AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfTupleType (
		AvailObject object,
		AvailObject aTupleType);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	abstract AvailObject o_UnionOfTypesAtThrough (
		AvailObject object,
		int startIndex,
		int endIndex);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract int o_UntranslatedDataAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_UntranslatedDataAtPut (
		AvailObject object,
		int index,
		int value);

	/**
	 * @param object
	 * @param argTypes
	 * @param anAvailInterpreter
	 * @param failBlock
	 * @return
	 */
	abstract AvailObject o_ValidateArgumentTypesInterpreterIfFail (
		AvailObject object,
		List<AvailObject> argTypes,
		Interpreter anAvailInterpreter,
		Continuation1<Generator<String>> failBlock);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Value (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract int o_ZoneForIndex (
		AvailObject object,
		int index);

	/**
	 * Construct a Java {@linkplain String string} from the given Avail
	 * {@linkplain StringDescriptor string}.
	 *
	 * @see AvailObject#asNativeString()
	 * @param object An Avail string.
	 * @return The corresponding Java string.
	 */
	abstract String o_AsNativeString (AvailObject object);

	/**
	 * Construct a Java {@linkplain Set set} from the given {@linkplain
	 * TupleDescriptor tuple}.
	 *
	 * @see AvailObject#asSet()
	 * @param object A tuple.
	 * @return A set containing each element in the tuple.
	 */
	abstract AvailObject o_AsSet (AvailObject object);

	/**
	 * Construct a {@linkplain TupleDescriptor tuple} from the given {@linkplain
	 * SetDescriptor set}. Element ordering in the tuple will be arbitrary and
	 * unstable.
	 *
	 * @see AvailObject#asTuple()
	 * @param object A set.
	 * @return A tuple containing each element in the set.
	 */
	abstract AvailObject o_AsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_BitsPerEntry (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_BodyBlock (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_BodySignature (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_BreakpointBlock (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Caller (AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_CleanUpAfterCompile (AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_ClearValue (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Function (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_FunctionType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Code (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_CodePoint (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_LazyComplete (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ConstantBindings (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ContentType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Continuation (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_CopyAsMutableContinuation (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_CopyAsMutableObjectTuple (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_CopyAsMutableSpliceTuple (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_DefaultType (AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_DisplayTestingTree (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_EnsureMutable (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract ExecutionState o_ExecutionState (AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_Expand (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_ExtractBoolean (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract short o_ExtractUnsignedByte (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract double o_ExtractDouble (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract float o_ExtractFloat (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_ExtractInt (AvailObject object);

	/**
	 * Extract a 64-bit signed Java {@code long} from the specified Avail
	 * {@linkplain IntegerDescriptor integer}.
	 *
	 * @param object An {@link AvailObject}.
	 * @return A 64-bit signed Java {@code long}
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	/**
	 * @param object
	 * @return
	 */
	abstract long o_ExtractLong (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract byte o_ExtractNybble (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_FieldMap (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_FieldTypeMap (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_FilteredBundleTree (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_GetInteger (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_GetValue (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_HashOrZero (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_HasGrammaticalRestrictions (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_DefinitionsTuple (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_LazyIncomplete (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_Index (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_InterruptRequestFlags (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_CountdownToReoptimize (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsAbstract (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsAbstractDefinition (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsFinite (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsForwardDefinition (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsInstanceMeta (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsMethodDefinition (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPositive (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	@Deprecated
	abstract boolean o_IsSaved (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsSplice (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsSupertypeOfBottom (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsValid (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_KeysAsSet (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_KeyType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_LevelTwoChunk (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_LevelTwoOffset (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Literal (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_LowerBound (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_LowerInclusive (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_MapSize (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_MaxStackDepth (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Message (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_MessageParts (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Methods (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Name (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Names (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_NewNames (AvailObject object);

	/**
	 * Answer how many arguments my instances expect.  This is applicable to
	 * both {@linkplain MethodDescriptor methods} and {@linkplain
	 * CompiledCodeDescriptor compiled code}.
	 *
	 * @param object The method or compiled code.
	 * @return The number of arguments expected.
	 */
	abstract int o_NumArgs (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_NumArgsAndLocalsAndStack (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_NumberOfZones (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_NumDoubles (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_NumIntegers (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_NumLiterals (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_NumLocals (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_NumObjects (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_NumOuters (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_NumOuterVars (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Nybbles (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Parent (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_Pc (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_PrimitiveNumber (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Priority (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_PrivateNames (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_FiberGlobals (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_GrammaticalRestrictions (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ReturnType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_SetSize (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_SizeRange (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_LazyActions (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_Stackp (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_Start (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_StartingChunk (AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_Step (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_String (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_TestingTree (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract TokenDescriptor.TokenType o_TokenType (
		AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_TrimExcessInts (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_TupleSize (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_TypeTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_UpperBound (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_UpperInclusive (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Value (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ValuesAsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ValueType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_VariableBindings (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Vectors (AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_Verify (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_VisibleNames (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Wordcodes (AvailObject object);

	/**
	 * Answer whether the arguments, both {@linkplain AvailObject objects}, are
	 * equal in value.
	 *
	 * @param object The receiver.
	 * @param another The second object used in the comparison.
	 * @return {@code true} if the two objects are of equal value, {@code false}
	 *         otherwise.
	 * @see AvailObject#equals(AvailObject)
	 */
	abstract boolean o_Equals (
		AvailObject object,
		AvailObject another);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain TupleDescriptor tuple}, are equal in value.
	 *
	 * @param object The receiver.
	 * @param aTuple The tuple used in the comparison.
	 * @return {@code true} if the receiver is a tuple and of value equal to the
	 *         argument, {@code false} otherwise.
	 * @see AvailObject#equalsAnyTuple(AvailObject)
	 */
	abstract boolean o_EqualsAnyTuple (
		AvailObject object,
		AvailObject aTuple);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain ByteStringDescriptor byte string}, are equal in value.
	 *
	 * @param object The receiver.
	 * @param aByteString The byte string used in the comparison.
	 * @return {@code true} if the receiver is a byte string and of value equal
	 *         to the argument, {@code false} otherwise.
	 * @see AvailObject#equalsByteString(AvailObject)
	 */
	abstract boolean o_EqualsByteString (
		AvailObject object,
		AvailObject aByteString);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object}, and a
	 * {@linkplain ByteTupleDescriptor byte tuple}, are equal in value.
	 *
	 * @param object The receiver.
	 * @param aByteTuple The byte tuple used in the comparison.
	 * @return {@code true} if the receiver is a byte tuple and of value equal
	 *         to the argument, {@code false} otherwise.
	 * @see AvailObject#equalsByteString(AvailObject)
	 */
	abstract boolean o_EqualsByteTuple (
		AvailObject object,
		AvailObject aByteTuple);

	/**
	 * Answer whether the receiver, an {@linkplain AvailObject object}, is a
	 * character with a code point equal to the integer argument.
	 *
	 * @param object The receiver.
	 * @param aCodePoint The code point to be compared to the receiver.
	 * @return {@code true} if the receiver is a character with a code point
	 *         equal to the argument, {@code false} otherwise.
	 * @see AvailObject#equalsCharacterWithCodePoint(int)
	 */
	abstract boolean o_EqualsCharacterWithCodePoint (
		AvailObject object,
		int aCodePoint);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain FunctionDescriptor function}, are equal in value.
	 *
	 * @param object The receiver.
	 * @param aFunction The function used in the comparison.
	 * @return {@code true} if the receiver is a function and of value equal to
	 *         the argument, {@code false} otherwise.
	 * @see AvailObject#equalsFunction(AvailObject)
	 */
	abstract boolean o_EqualsFunction (
		AvailObject object,
		AvailObject aFunction);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain FunctionTypeDescriptor function type}, are equal.
	 *
	 * @param object The receiver.
	 * @param aFunctionType The function type used in the comparison.
	 * @return {@code true} IFF the receiver is also a function type and:
	 *
	 * <p><ul>
	 * <li>The {@linkplain AvailObject#argsTupleType() argument types}
	 * correspond,</li>
	 * <li>The {@linkplain AvailObject#returnType() return types}
	 * correspond, and</li>
	 * <li>The {@linkplain AvailObject#declaredExceptions() raise types}
	 * correspond.</li>
	 * </ul></p>
	 * @see AvailObject#equalsFunctionType(AvailObject)
	 */
	abstract boolean o_EqualsFunctionType (
		AvailObject object,
		AvailObject aFunctionType);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain CompiledCodeDescriptor compiled code}, are equal.
	 *
	 * @param object The receiver.
	 * @param aCompiledCode The compiled code used in the comparison.
	 * @return {@code true} if the receiver is a compiled code and of value
	 *         equal to the argument, {@code false} otherwise.
	 * @see AvailObject#equalsCompiledCode(AvailObject)
	 */
	abstract boolean o_EqualsCompiledCode (
		AvailObject object,
		AvailObject aCompiledCode);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain VariableDescriptor variable}, are the exact same object,
	 * comparing by address (Java object identity). There's no need to traverse
	 * the objects before comparing addresses, because this message was a
	 * double-dispatch that would have skipped (and stripped) the indirection
	 * objects in either path.
	 *
     * @param object The receiver.
	 * @param aVariable The variable used in the comparison.
	 * @return {@code true} if the receiver is a variable with the same identity
	 *         as the argument, {@code false} otherwise.
	 * @see AvailObject#equalsVariable(AvailObject)
	 */
	abstract boolean o_EqualsVariable (
		AvailObject object,
		AvailObject aVariable);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_EqualsVariableType (
		AvailObject object,
		AvailObject aType);

	/**
	 * @param object
	 * @param aContinuation
	 * @return
	 */
	abstract boolean o_EqualsContinuation (
		AvailObject object,
		AvailObject aContinuation);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract boolean o_EqualsContinuationType (
		AvailObject object,
		AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract boolean o_EqualsCompiledCodeType (
		AvailObject object,
		AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param aDouble
	 * @return
	 */
	abstract boolean o_EqualsDouble (
		final AvailObject object,
		final double aDouble);

	/**
	 * @param object
	 * @param aFloat
	 * @return
	 */
	abstract boolean o_EqualsFloat (
		final AvailObject object,
		final float aFloat);

	/**
	 * Answer whether the {@linkplain AvailObject receiver} is an {@linkplain
	 * InfinityDescriptor infinity} with the specified {@link
	 * IntegerSlots#SIGN}.
	 *
	 * @param object The receiver.
	 * @param sign The type of infinity for comparison.
	 * @return {@code true} if the receiver is an infinity of the specified
	 *         sign, {@code false} otherwise.
	 * @see AvailObject#equalsInfinity(Sign)
	 */
	abstract boolean o_EqualsInfinity (
		final AvailObject object,
		final Sign sign);

	/**
	 * @param object
	 * @param anAvailInteger
	 * @return
	 */
	abstract boolean o_EqualsInteger (
		AvailObject object,
		AvailObject anAvailInteger);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract boolean o_EqualsIntegerRangeType (
		AvailObject object,
		AvailObject another);

	/**
	 * @param object
	 * @param aMap
	 * @return
	 */
	abstract boolean o_EqualsMap (
		AvailObject object,
		AvailObject aMap);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	abstract boolean o_EqualsMapType (
		AvailObject object,
		AvailObject aMapType);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	abstract boolean o_EqualsNybbleTuple (
		AvailObject object,
		AvailObject aTuple);

	/**
	 * @param object
	 * @param anObject
	 * @return
	 */
	abstract boolean o_EqualsObject (
		AvailObject object,
		AvailObject anObject);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	abstract boolean o_EqualsObjectTuple (
		AvailObject object,
		AvailObject aTuple);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract boolean o_EqualsParseNodeType (
		AvailObject object,
		AvailObject aParseNodeType);

	/**
	 * @param object
	 * @param aPojo
	 * @return
	 */
	abstract boolean o_EqualsPojo (
		AvailObject object,
		AvailObject aPojo);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract boolean o_EqualsPojoType (
		AvailObject object,
		AvailObject aPojoType);

	/**
	 * @param object
	 * @param aPrimitiveType
	 * @return
	 */
	abstract boolean o_EqualsPrimitiveType (
		final AvailObject object,
		final AvailObject aPrimitiveType);

	/**
	 * @param object
	 * @param aPojo
	 * @return
	 */
	abstract boolean o_EqualsRawPojo (
		AvailObject object,
		AvailObject aPojo);

	/**
	 * @param object
	 * @param aSet
	 * @return
	 */
	abstract boolean o_EqualsSet (
		AvailObject object,
		AvailObject aSet);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	abstract boolean o_EqualsSetType (
		AvailObject object,
		AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract boolean o_EqualsTupleType (
		AvailObject object,
		AvailObject aTupleType);

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	abstract boolean o_EqualsTwoByteString (
		AvailObject object,
		AvailObject aString);

	/**
	 * @param object
	 * @param potentialInstance
	 * @return
	 */
	abstract boolean o_HasObjectInstance (
		AvailObject object,
		AvailObject potentialInstance);

	/**
	 * Given two objects that are known to be equal, is the first one in a
	 * better form (more compact, more efficient, older generation) than the
	 * second one?
	 *
	 * @param object The first object.
	 * @param anotherObject The second object, equal to the first object.
	 * @return Whether the first object is the better representation to keep.
	 */
	abstract boolean o_IsBetterRepresentationThan (
		AvailObject object,
		AvailObject anotherObject);

	/**
	 * Given two objects that are known to be equal, the second of which is in
	 * the form of a tuple type, is the first one in a better form than the
	 * second one?
	 *
	 * @param object
	 *            The first object.
	 * @param aTupleType
	 *            The second object, a tuple type equal to the first object.
	 * @return
	 *            Whether the first object is a better representation to keep.
	 */
	abstract boolean o_IsBetterRepresentationThanTupleType (
		AvailObject object,
		AvailObject aTupleType);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_IsInstanceOfKind (
		AvailObject object,
		AvailObject aType);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_EqualsNil (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_Hash (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail function?
	 *
	 * @see AvailObject#isCharacter()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is a function, {@code false}
	 *         otherwise.
	 */
	abstract boolean o_IsFunction (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_MakeImmutable (AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_MakeSubobjectsImmutable (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_MakeShared (AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_MakeSubobjectsShared (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Kind (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail boolean?
	 *
	 * @see AvailObject#isBoolean()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is a boolean, {@code false}
	 *         otherwise.
	 */
	abstract boolean o_IsBoolean (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail byte tuple?
	 *
	 * @see AvailObject#isByteTuple()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is a byte tuple, {@code false}
	 *         otherwise.
	 */
	abstract boolean o_IsByteTuple (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail character?
	 *
	 * @see AvailObject#isCharacter()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is a character, {@code false}
	 *         otherwise.
	 */
	abstract boolean o_IsCharacter (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail string?
	 *
	 * @see AvailObject#isString()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is an Avail string, {@code false}
	 *         otherwise.
	 */
	abstract boolean o_IsString (AvailObject object);

	/**
	 * @param object
	 * @param aFunction
	 * @return
	 */
	abstract boolean o_ContainsBlock (
		AvailObject object,
		AvailObject aFunction);

	/**
	 * @param object
	 */
	@Deprecated
	abstract void o_PostFault (AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_ReadBarrierFault (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Traversed (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail map?
	 *
	 * @see AvailObject#isMap()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is a map, {@code false} otherwise.
	 */
	abstract boolean o_IsMap (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail unsigned byte?
	 *
	 * @see AvailObject#isUnsignedByte()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is an unsigned byte, {@code false}
	 *         otherwise.
	 */
	abstract boolean o_IsUnsignedByte (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail nybble?
	 *
	 * @see AvailObject#isNybble()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is a nybble, {@code false}
	 *         otherwise.
	 */
	abstract boolean o_IsNybble (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail set?
	 *
	 * @see AvailObject#isSet()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is a set, {@code false} otherwise.
	 */
	abstract boolean o_IsSet (AvailObject object);

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SetBinAddingElementHashLevelCanDestroy (
		AvailObject object,
		AvailObject elementObject,
		int elementObjectHash,
		byte myLevel,
		boolean canDestroy);

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @return
	 */
	abstract boolean o_BinHasElementWithHash (
		AvailObject object,
		AvailObject elementObject,
		int elementObjectHash);

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_BinRemoveElementHashCanDestroy (
		AvailObject object,
		AvailObject elementObject,
		int elementObjectHash,
		boolean canDestroy);

	/**
	 * @param object
	 * @param potentialSuperset
	 * @return
	 */
	abstract boolean o_IsBinSubsetOf (
		AvailObject object,
		AvailObject potentialSuperset);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_BinHash (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_BinSize (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail tuple?
	 *
	 * @see AvailObject#isTuple()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is a tuple, {@code false} otherwise.
	 */
	abstract boolean o_IsTuple (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail atom?
	 *
	 * @see AvailObject#isAtom()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is an atom, {@code false} otherwise.
	 */
	abstract boolean o_IsAtom (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail extended integer?
	 *
	 * @see AvailObject#isExtendedInteger()
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is an extended integer, {@code
	 *         false} otherwise.
	 */
	abstract boolean o_IsExtendedInteger (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsIntegerRangeType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsMapType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsSetType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsTupleType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsType (AvailObject object);

	/**
	 * @param object
	 * @param visitor
	 */
	abstract void o_ScanSubobjects (
		AvailObject object,
		AvailSubobjectVisitor visitor);

	/**
	 * Answer an {@linkplain Iterator iterator} suitable for traversing the
	 * elements of the {@linkplain AvailObject object} with a Java
	 * <em>foreach</em> construct.
	 *
	 * @param object An {@link AvailObject}.
	 * @return An {@linkplain Iterator iterator}.
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	/**
	 * @param object
	 * @return
	 */
	abstract Iterator<AvailObject> o_Iterator (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ParsingInstructions (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Expression (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Variable (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ArgumentsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_StatementsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ResultType (AvailObject object);

	/**
	 * @param object
	 * @param neededVariables
	 */
	abstract void o_NeededVariables (
		AvailObject object,
		AvailObject neededVariables);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_NeededVariables (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_Primitive (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_DeclaredType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract DeclarationKind o_DeclarationKind (
		AvailObject object);

	/**
	 * @param object
	 * @param initializationExpression
	 */
	abstract void o_InitializationExpression (
		AvailObject object,
		AvailObject initializationExpression);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_InitializationExpression (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_LiteralObject (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Token (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_MarkerValue (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Method (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ExpressionsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Declaration (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ExpressionType (AvailObject object);

	/**
	 * @param object
	 * @param codeGenerator
	 */
	abstract void o_EmitEffectOn (
		AvailObject object,
		AvailCodeGenerator codeGenerator);

	/**
	 * @param object
	 * @param codeGenerator
	 */
	abstract void o_EmitValueOn (
		AvailObject object,
		AvailCodeGenerator codeGenerator);

	/**
	 * Map my children through the (destructive) transformation specified by
	 * aBlock.
	 *
	 * @param object
	 * @param aBlock
	 */
	abstract void o_ChildrenMap (
		AvailObject object,
		Transformer1<AvailObject, AvailObject> aBlock);

	/**
	 * Visit my child parse nodes with aBlock.
	 *
	 * @param object
	 * @param aBlock
	 */
	abstract void o_ChildrenDo (
		AvailObject object,
		Continuation1<AvailObject> aBlock);

	/**
	 * @param object
	 * @param parent
	 */
	abstract void o_ValidateLocally (
		AvailObject object,
		@Nullable AvailObject parent);

	/**
	 * @param object
	 * @param codeGenerator
	 * @return
	 */
	abstract AvailObject o_Generate (
		AvailObject object,
		AvailCodeGenerator codeGenerator);

	/**
	 * @param object
	 * @param newParseNode
	 * @return
	 */
	abstract AvailObject o_CopyWith (
		AvailObject object,
		AvailObject newParseNode);

	/**
	 * @param object
	 * @param isLastUse
	 */
	abstract void o_IsLastUse (
		AvailObject object,
		boolean isLastUse);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsLastUse (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsMacroDefinition (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_CopyMutableParseNode (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_BinUnionKind (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_OutputParseNode (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ApparentSendName (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Statements (
		AvailObject object);

	/**
	 * @param object
	 * @param accumulatedStatements
	 */
	abstract void o_FlattenStatementsInto (
		AvailObject object,
		List<AvailObject> accumulatedStatements);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_LineNumber (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_AllBundles (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsSetBin (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract MapDescriptor.MapIterable o_MapIterable (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Complete (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Incomplete (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_DeclaredExceptions (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsInt (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsLong (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ArgsTupleType (AvailObject object);

	/**
	 * @param object
	 * @param anObject
	 * @return
	 */
	abstract boolean o_EqualsInstanceTypeFor (
		AvailObject object,
		AvailObject anObject);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Instances (
		AvailObject object);

	/**
	 * @param object
	 * @param aSet
	 * @return
	 */
	boolean o_EqualsEnumerationWithSet (
		final AvailObject object,
		final AvailObject aSet)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsEnumeration (
		AvailObject object);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_IsInstanceOf (
		AvailObject object,
		AvailObject aType);

	/**
	 * @param object
	 * @param potentialInstance
	 * @return
	 */
	abstract boolean o_EnumerationIncludesInstance (
		AvailObject object,
		AvailObject potentialInstance);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ComputeSuperkind (
		AvailObject object);

	/**
	 * @param object
	 * @param key
	 * @param value
	 */
	abstract void o_SetAtomProperty (
		AvailObject object,
		AvailObject key,
		AvailObject value);

	/**
	 * @param object
	 * @param key
	 * @return
	 */
	abstract AvailObject o_GetAtomProperty (
		AvailObject object,
		AvailObject key);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract boolean o_EqualsEnumerationType (
		AvailObject object,
		AvailObject another);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ReadType (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_WriteType (
		AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Versions (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Versions (AvailObject object);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfParseNodeType (
		AvailObject object,
		AvailObject aParseNodeType);

	/**
	 * @param object
	 * @return
	 */
	abstract ParseNodeKind o_ParseNodeKind (
		AvailObject object);

	/**
	 * @param object
	 * @param expectedParseNodeKind
	 * @return
	 */
	abstract boolean o_ParseNodeKindIsUnder (
		AvailObject object,
		ParseNodeKind expectedParseNodeKind);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsRawPojo (AvailObject object);

	/**
	 * @param object
	 * @param restrictionSignature
	 */
	abstract void o_AddTypeRestriction (
		AvailObject object,
		AvailObject restrictionSignature);

	/**
	 * @param object
	 * @param restrictionSignature
	 */
	abstract void o_RemoveTypeRestriction (
		AvailObject object,
		AvailObject restrictionSignature);

	/**
	 * Return the {@linkplain MethodDescriptor method}'s
	 * {@linkplain TupleDescriptor tuple} of {@linkplain FunctionDescriptor
	 * functions} that statically restrict call sites by argument type.
	 *
	 * @param object The method.
	 * @return
	 */
	abstract AvailObject o_TypeRestrictions (
		AvailObject object);

	/**
	 * @param object
	 * @param tupleType
	 */
	abstract void o_AddSealedArgumentsType (
		AvailObject object,
		AvailObject tupleType);

	/**
	 * @param object
	 * @param tupleType
	 */
	abstract void o_RemoveSealedArgumentsType (
		AvailObject object,
		AvailObject tupleType);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_SealedArgumentsTypesTuple (
		AvailObject object);

	/**
	 * @param object
	 * @param methodNameAtom
	 * @param typeRestrictionFunction
	 */
	abstract void o_AddTypeRestriction (
		AvailObject object,
		AvailObject methodNameAtom,
		AvailObject typeRestrictionFunction);

	/**
	 * @param object
	 * @param name
	 * @param constantBinding
	 */
	abstract void o_AddConstantBinding (
		AvailObject object,
		AvailObject name,
		AvailObject constantBinding);

	/**
	 * @param object
	 * @param name
	 * @param variableBinding
	 */
	abstract void o_AddVariableBinding (
		AvailObject object,
		AvailObject name,
		AvailObject variableBinding);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsMethodEmpty (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPojoSelfType (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_PojoSelfType (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_JavaClass (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsUnsignedShort (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_ExtractUnsignedShort (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsFloat (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsDouble (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_RawPojo (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPojo (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPojoType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_UpperBoundMap (AvailObject object);

	/**
	 * @param object
	 * @param aMap
	 */
	abstract void o_UpperBoundMap (
		AvailObject object,
		AvailObject aMap);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract Order o_NumericCompare (
		final AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aDouble
	 * @return
	 */
	abstract Order o_NumericCompareToDouble (
		final AvailObject object,
		double aDouble);

	/**
	 * @param object
	 * @param anInteger
	 * @return
	 */
	abstract Order o_NumericCompareToInteger (
		final AvailObject object,
		final AvailObject anInteger);

	/**
	 * @param object
	 * @param sign
	 * @return
	 */
	abstract Order o_NumericCompareToInfinity (
		final AvailObject object,
		final Sign sign);

	/**
	 * @param object
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_AddToDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_AddToFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		boolean canDestroy);

	/**
	 * @param object
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final AvailObject doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final AvailObject floatObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_LazyPrefilterMap (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract SerializerOperation o_SerializerOperation (
		final AvailObject object);

	/**
	 * @param object
	 * @param key
	 * @param keyHash
	 * @param value
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_MapBinAtHashPutLevelCanDestroy (
		final AvailObject object,
		final AvailObject key,
		final int keyHash,
		final AvailObject value,
		final byte myLevel,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param key
	 * @param keyHash
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_MapBinRemoveKeyHashCanDestroy (
		final AvailObject object,
		final AvailObject key,
		final int keyHash,
		final boolean canDestroy);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_MapBinKeyUnionKind (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_MapBinValueUnionKind (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsHashedMapBin (
		final AvailObject object);

	/**
	 * @param object
	 * @param key
	 * @param keyHash
	 * @return
	 */
	abstract AvailObject o_MapBinAtHash (
		final AvailObject object,
		final AvailObject key,
		final int keyHash);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_MapBinKeysHash (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_MapBinValuesHash (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_IssuingModule (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPojoFusedType (final AvailObject object);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfPojoBottomType (
		final AvailObject object,
		final AvailObject aPojoType);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_EqualsPojoBottomType (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_JavaAncestors (
		final AvailObject object);

	/**
	 * @param object
	 * @param aFusedPojoType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final AvailObject aFusedPojoType);

	/**
	 * @param object
	 * @param anUnfusedPojoType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final AvailObject anUnfusedPojoType);

	/**
	 * @param object
	 * @param aFusedPojoType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfPojoFusedType (
		final AvailObject object,
		final AvailObject aFusedPojoType);

	/**
	 * @param object
	 * @param anUnfusedPojoType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfPojoUnfusedType (
		final AvailObject object,
		final AvailObject anUnfusedPojoType);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPojoArrayType (final AvailObject object);

	/**
	 * @param object
	 * @param ignoredClassHint
	 * @return
	 */
	abstract Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_TypeVariables (
		final AvailObject object);

	/**
	 * @param object
	 * @param field
	 * @param receiver
	 * @return
	 */
	abstract boolean o_EqualsPojoField (
		final AvailObject object,
		final AvailObject field,
		final AvailObject receiver);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsSignedByte (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsSignedShort (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract byte o_ExtractSignedByte (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract short o_ExtractSignedShort (final AvailObject object);

	/**
	 * @param object
	 * @param aRawPojo
	 * @return
	 */
	abstract boolean o_EqualsEqualityRawPojo (
		final AvailObject object,
		final AvailObject aRawPojo);

	/**
	 * @param object
	 * @return
	 */
	abstract Object o_JavaObject (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract BigInteger o_AsBigInteger (
		final AvailObject object);

	/**
	 * @param object
	 * @param newElement
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_AppendCanDestroy (
		final AvailObject object,
		final AvailObject newElement,
		final boolean canDestroy);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_LazyIncompleteCaseInsensitive (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_LowerCaseString (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Instance (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_InstanceCount (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract long o_TotalInvocations (
		final AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_TallyInvocation (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_FieldTypeTuple (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_FieldTuple (
		final AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_ClearInterruptRequestFlags (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsSystemModule (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ArgumentsListNode (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_LiteralType (
		final AvailObject object);

	/**
	 * @param object
	 * @param aLiteralTokenType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType);

	/**
	 * @param object
	 * @param aLiteralTokenType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfLiteralTokenType(
		 final AvailObject object,
		 final AvailObject aLiteralTokenType);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsLiteralTokenType (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsLiteralToken (
		final AvailObject object);

	/**
	 * @param object
	 * @param aLiteralTokenType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType);

	/**
	 * @param object
	 * @param aLiteralTokenType
	 * @return
	 */
	abstract boolean o_EqualsLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	abstract boolean o_EqualsObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	/**
	 * @param object
	 * @param aToken
	 * @return
	 */
	abstract boolean o_EqualsToken (
		final AvailObject object,
		final AvailObject aToken);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_BitwiseAnd (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_BitwiseOr (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_BitwiseXor (
		final AvailObject object,
		final AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param methodName
	 * @param sealSignature
	 */
	abstract void o_AddSeal (
		final AvailObject object,
		final AvailObject methodName,
		final AvailObject sealSignature);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_AllocateFromCounter (
		final AvailObject object);

	/**
	 * @param object
	 * @param methodName
	 */
	abstract void o_SetMethodName (
		final AvailObject object,
		final AvailObject methodName);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_StartingLineNumber (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Module (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_MethodName (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract String o_NameForDebugger (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return true;
	}

	/**
	 * @param object
	 * @param kind
	 * @return
	 */
	abstract boolean o_BinElementsAreAllInstancesOfKind (
		final AvailObject object,
		final AvailObject kind);

	/**
	 * @param object
	 * @param kind
	 * @return
	 */
	abstract boolean o_SetElementsAreAllInstancesOfKind (
		final AvailObject object,
		final AvailObject kind);

	/**
	 * @param object
	 * @return
	 */
	abstract MapIterable o_MapBinIterable (
		final AvailObject object);

	/**
	 * @param object
	 * @param anInt
	 * @return
	 */
	abstract boolean o_RangeIncludesInt (
		final AvailObject object,
		final int anInt);

	/**
	 * @param object
	 * @param isSystemModule
	 */
	abstract void o_IsSystemModule (
		final AvailObject object,
		final boolean isSystemModule);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsMarkerNode (
		final AvailObject object);

	/**
	 * @param object
	 * @param shiftFactor
	 * @param truncationBits
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_BitShiftLeftTruncatingToBits (
		final AvailObject object,
		final AvailObject shiftFactor,
		final AvailObject truncationBits,
		final boolean canDestroy);

	/**
	 * @param object
	 * @return
	 */
	abstract SetIterator o_SetBinIterator (
		final AvailObject object);

	/**
	 * @param object
	 * @param shiftFactor
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_BitShift (
		final AvailObject object,
		final AvailObject shiftFactor,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param aParseNode
	 * @return
	 */
	abstract boolean o_EqualsParseNode (
		final AvailObject object,
		final AvailObject aParseNode);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_StripMacro (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_DefinitionMethod (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_PrefixFunctions (
		final AvailObject object);

	/**
	 * @param object
	 * @param aByteArrayTuple
	 * @return
	 */
	abstract boolean o_EqualsByteArrayTuple (
		final AvailObject object,
		final AvailObject aByteArrayTuple);

	/**
	 * @param object
	 * @param i
	 * @param tupleSize
	 * @param aByteArrayTuple
	 * @param j
	 * @return
	 */
	abstract boolean o_CompareFromToWithByteArrayTupleStartingAt (
		final AvailObject object,
		final int i,
		final int tupleSize,
		final AvailObject aByteArrayTuple,
		final int j);

	/**
	 * @param object
	 * @return
	 */
	abstract byte[] o_ByteArray (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsByteArrayTuple (final AvailObject object);

	/**
	 * @param object
	 * @param message
	 * @return
	 * @throws SignatureException
	 */
	abstract AvailObject o_IncludeBundleNamed (
		final AvailObject object,
		final AvailObject message)
	throws SignatureException;

	/**
	 * @param object
	 * @param message
	 */
	abstract void o_FlushForNewOrChangedBundleNamed (
		final AvailObject object,
		final AvailObject message);

	/**
	 * @param object
	 * @param critical
	 */
	abstract void o_Lock (
		final AvailObject object,
		final Continuation0 critical);
}
