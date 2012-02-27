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

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.ProcessDescriptor.ExecutionState;
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
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public abstract class AbstractDescriptor
{
	/**
	 * A unique short, monotonically allocated and set automatically by the
	 * constructor.  It equals the {@linkplain AbstractDescriptor descriptor's}
	 * index into {@link #allDescriptors}, which is also populated by the
	 * constructor.
	 */
	final short myId;

	/**
	 * Answer a unique short, monotonically allocated and set automatically by
	 * the constructor.  It equals the {@linkplain AbstractDescriptor
	 * descriptor}'s index into {@link #allDescriptors}, which is also populated
	 * by the constructor.
	 *
	 * @return The {@linkplain AbstractDescriptor descriptor}'s identifier.
	 */
	final short id ()
	{
		return myId;
	}

	/**
	 * A flag indicating whether instances of me can be modified in place.
	 * Generally, as soon as there are two references from {@linkplain AvailObject
	 * Avail objects}.
	 */
	protected final boolean isMutable;

	/**
	 * Can instances of me be modified in place?
	 *
	 * @return {@code true} if it is permissible to modify the object in place,
	 *         {@code false} otherwise.
	 */
	public final boolean isMutable ()
	{
		return isMutable;
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
	public final int numberOfFixedObjectSlots ()
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
	public final int numberOfFixedIntegerSlots ()
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

	/** The registry of all {@linkplain AbstractDescriptor descriptors}. */
	protected static final List<AbstractDescriptor> allDescriptors =
		new ArrayList<AbstractDescriptor>(200);

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
	 * Construct a new {@linkplain AbstractDescriptor descriptor}.
	 *
	 * @param isMutable Does the {@linkplain AbstractDescriptor descriptor}
	 *                  represent a mutable object?
	 */
	@SuppressWarnings("unchecked")
	protected AbstractDescriptor (final boolean isMutable)
	{
		this.myId = (short) allDescriptors.size();
		assert (this.myId % 2 == 0) == isMutable;
		allDescriptors.add(this);
		this.isMutable = isMutable;

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
	private static <A extends Annotation> A getAnnotation (
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
		return slotMirror.getAnnotation(annotationClass);
	}

	/**
	 * Describe the object for the Eclipse debugger.
	 *
	 * @param object
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public AvailObjectFieldHelper[] o_DescribeForDebugger (
		final @NotNull AvailObject object)
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
		final @NotNull AbstractSlotsEnum e)
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
	final void checkWriteForField (final @NotNull AbstractSlotsEnum e)
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
	public final @NotNull AvailObject create (final int indexedSlotCount)
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
	public final @NotNull AvailObject create ()
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
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
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
		if (isMutable)
		{
			builder.append("\u2133");
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
			if (getAnnotation((Enum<?>)slot, HideFieldInDebugger.class) == null)
			{
				builder.append('\n');
				for (int tab = 0; tab < indent; tab++)
				{
					builder.append('\t');
				}
				final String slotName = slot.name();
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
			if (getAnnotation((Enum<?>)slot, HideFieldInDebugger.class) == null)
			{
				builder.append('\n');
				for (int tab = 0; tab < indent; tab++)
				{
					builder.append('\t');
				}
				final String slotName = slot.name();
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
		final @NotNull AvailObject object,
		final int value,
		final @NotNull IntegerSlotsEnum slot,
		final @NotNull StringBuilder builder)
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
					// Look it up by ordinal.
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
					final Method lookupMethod =
						describingClass.getMethod(
							lookupName,
							Integer.TYPE);
					@SuppressWarnings("rawtypes")
					final
					Enum lookedUp = (Enum)lookupMethod.invoke(null, value);
					if (lookedUp == null)
					{
						builder.append(
							new Formatter().format(
								" (enum out of range: 0x%X)",
								value & 0xFFFFFFFFL));
					}
					else
					{
						assert lookedUp.getDeclaringClass()
							== describingClass;
						builder.append(" = ");
						builder.append(lookedUp.name());
					}
				}
			}
			else if (!bitFields.isEmpty())
			{
				// Show each bit field.
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
		final @NotNull IntegerSlotsEnum integerSlot,
		final int shift,
		final int bits)
	{
		return new BitField(
			integerSlot,
			shift,
			bits);
	}

	/**
	 * @param object
	 * @param functionType
	 * @return
	 */
	abstract boolean o_AcceptsArgTypesFromFunctionType (
		@NotNull AvailObject object,
		AvailObject functionType);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @param numArgs
	 * @return
	 */
	abstract boolean o_AcceptsArgumentTypesFromContinuation (
		@NotNull AvailObject object,
		AvailObject continuation,
		int stackp,
		int numArgs);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	abstract boolean o_AcceptsListOfArgTypes (
		@NotNull AvailObject object,
		List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param argValues
	 * @return
	 */
	abstract boolean o_AcceptsListOfArgValues (
		@NotNull AvailObject object,
		List<AvailObject> argValues);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	abstract boolean o_AcceptsTupleOfArgTypes (
		@NotNull AvailObject object,
		AvailObject argTypes);

	/**
	 * @param object
	 * @param arguments
	 * @return
	 */
	abstract boolean o_AcceptsTupleOfArguments (
		@NotNull AvailObject object,
		AvailObject arguments);

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	abstract void o_AddDependentChunkIndex (
		@NotNull AvailObject object,
		int aChunkIndex);

	/**
	 * @param object
	 * @param implementation
	 */
	abstract void o_AddImplementation (
		@NotNull AvailObject object,
		AvailObject implementation);

	/**
	 * @param object
	 * @param restrictions
	 */
	abstract void o_AddGrammaticalRestrictions (
		@NotNull AvailObject object,
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
	abstract @NotNull AvailObject o_AddToInfinityCanDestroy (
		@NotNull AvailObject object,
		@NotNull Sign sign,
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
	abstract @NotNull AvailObject o_AddToIntegerCanDestroy (
		@NotNull AvailObject object,
		@NotNull AvailObject anInteger,
		boolean canDestroy);

	/**
	 * @param object
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	abstract void o_AddGrammaticalMessageRestrictions (
		@NotNull AvailObject object,
		AvailObject methodName,
		AvailObject illegalArgMsgs);

	/**
	 * @param object
	 * @param methodName
	 * @param implementation
	 */
	abstract void o_AddMethodImplementation (
		@NotNull AvailObject object,
		AvailObject methodName,
		AvailObject implementation);

	/**
	 * @param object
	 * @param message
	 * @param bundle
	 */
	abstract void o_AtMessageAddBundle (
		@NotNull AvailObject object,
		AvailObject message,
		AvailObject bundle);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	abstract void o_AtNameAdd (
		@NotNull AvailObject object,
		AvailObject stringName,
		AvailObject trueName);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	abstract void o_AtNewNamePut (
		@NotNull AvailObject object,
		AvailObject stringName,
		AvailObject trueName);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	abstract void o_AtPrivateNameAdd (
		@NotNull AvailObject object,
		AvailObject stringName,
		AvailObject trueName);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_BinElementAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_BinElementAtPut (
		@NotNull AvailObject object,
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
	abstract void o_BinUnionTypeOrNull (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_BitVector (
		@NotNull AvailObject object,
		int value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_BreakpointBlock (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param bundleTree
	 */
	abstract void o_BuildFilteredBundleTreeFrom (
		@NotNull AvailObject object,
		AvailObject bundleTree);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Caller (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Function (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Code (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_CodePoint (
		@NotNull AvailObject object,
		int value);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param anotherObject
	 * @param startIndex2
	 * @return
	 */
	abstract boolean o_CompareFromToWithStartingAt (
		@NotNull AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject anotherObject,
		int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aTuple
	 * @param startIndex2
	 * @return
	 */
	abstract boolean o_CompareFromToWithAnyTupleStartingAt (
		@NotNull AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject aTuple,
		int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteString
	 * @param startIndex2
	 * @return
	 */
	abstract boolean o_CompareFromToWithByteStringStartingAt (
		@NotNull AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject aByteString,
		int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteTuple
	 * @param startIndex2
	 * @return
	 */
	abstract boolean o_CompareFromToWithByteTupleStartingAt (
		@NotNull AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject aByteTuple,
		int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aNybbleTuple
	 * @param startIndex2
	 * @return
	 */
	abstract boolean o_CompareFromToWithNybbleTupleStartingAt (
		@NotNull AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject aNybbleTuple,
		int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param anObjectTuple
	 * @param startIndex2
	 * @return
	 */
	abstract boolean o_CompareFromToWithObjectTupleStartingAt (
		@NotNull AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject anObjectTuple,
		int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aTwoByteString
	 * @param startIndex2
	 * @return
	 */
	abstract boolean o_CompareFromToWithTwoByteStringStartingAt (
		@NotNull AvailObject object,
		int startIndex1,
		int endIndex1,
		AvailObject aTwoByteString,
		int startIndex2);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_LazyComplete (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @return
	 */
	abstract int o_ComputeHashFromTo (
		@NotNull AvailObject object,
		int start,
		int end);

	/**
	 * @param object
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_ConcatenateTuplesCanDestroy (
		@NotNull AvailObject object,
		boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Continuation (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param filteredBundleTree
	 * @param visibleNames
	 */
	abstract void o_CopyToRestrictedTo (
		@NotNull AvailObject object,
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
		@NotNull AvailObject object,
		int start,
		int end,
		boolean canDestroy);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	abstract boolean o_CouldEverBeInvokedWith (
		@NotNull AvailObject object,
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
	abstract @NotNull AvailObject o_DivideCanDestroy (
		@NotNull AvailObject object,
		@NotNull AvailObject aNumber,
		boolean canDestroy);

	/**
	 * Divide the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#divideCanDestroy(AvailObject, boolean) divideCanDestroy}. It
	 * exists for double-dispatch only.</p>

	 * @param object
	 *        The divisor, an integral numeric.
	 * @param sign
	 *        The dividend, an {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_DivideIntoInfinityCanDestroy (
		@NotNull AvailObject object,
		@NotNull Sign sign,
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
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_DivideIntoIntegerCanDestroy (
		@NotNull AvailObject object,
		AvailObject anInteger,
		boolean canDestroy);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_ElementAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_ElementAtPut (
		@NotNull AvailObject object,
		int index,
		AvailObject value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	abstract int o_EndOfZone (
		@NotNull AvailObject object,
		int zone);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	abstract int o_EndSubtupleIndexInZone (
		@NotNull AvailObject object,
		int zone);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_ExecutionState (
		@NotNull AvailObject object,
		ExecutionState value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract byte o_ExtractNybbleFromTupleAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	abstract List<AvailObject> o_FilterByTypes (
		@NotNull AvailObject object,
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
		@NotNull AvailObject object,
		int zone,
		AvailObject newSubtuple,
		int startSubtupleIndex,
		int endOfZone);

	/**
	 * @param object
	 * @param elementObject
	 * @return
	 */
	abstract boolean o_HasElement (
		@NotNull AvailObject object,
		AvailObject elementObject);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Hash (
		@NotNull AvailObject object,
		int value);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	abstract int o_HashFromTo (
		@NotNull AvailObject object,
		int startIndex,
		int endIndex);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_HashOrZero (
		@NotNull AvailObject object,
		int value);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	abstract boolean o_HasKey (
		@NotNull AvailObject object,
		AvailObject keyObject);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	abstract List<AvailObject> o_ImplementationsAtOrBelow (
		@NotNull AvailObject object,
		List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param messageBundle
	 * @return
	 */
	abstract AvailObject o_IncludeBundle (
		@NotNull AvailObject object,
		AvailObject messageBundle);

	/**
	 * @param object
	 * @param imp
	 * @return
	 */
	abstract boolean o_IncludesImplementation (
		@NotNull AvailObject object,
		AvailObject imp);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_LazyIncomplete (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Index (
		@NotNull AvailObject object,
		int value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_InterruptRequestFlag (
		@NotNull AvailObject object,
		int value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_InvocationCount (
		@NotNull AvailObject object,
		int value);

	/**
	 * @param object
	 * @param aBoolean
	 */
	abstract void o_IsSaved (
		@NotNull AvailObject object,
		boolean aBoolean);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract boolean o_IsSubsetOf (
		@NotNull AvailObject object,
		AvailObject another);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_IsSubtypeOf (
		@NotNull AvailObject object,
		AvailObject aType);

	/**
	 * @param object
	 * @param aVariableType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfVariableType (
		@NotNull AvailObject object,
		AvailObject aVariableType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfContinuationType (
		@NotNull AvailObject object,
		AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfCompiledCodeType (
		@NotNull AvailObject object,
		AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfFunctionType (
		@NotNull AvailObject object,
		AvailObject aFunctionType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfIntegerRangeType (
		@NotNull AvailObject object,
		AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfMapType (
		@NotNull AvailObject object,
		AvailObject aMapType);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfObjectType (
		@NotNull AvailObject object,
		AvailObject anObjectType);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfParseNodeType (
		@NotNull AvailObject object,
		@NotNull AvailObject aParseNodeType);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfPojoType (
		@NotNull AvailObject object,
		@NotNull AvailObject aPojoType);

	/**
	 * @param object
	 * @param aPrimitiveType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfPrimitiveType (
		@NotNull AvailObject object,
		AvailObject aPrimitiveType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfSetType (
		@NotNull AvailObject object,
		AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfTupleType (
		@NotNull AvailObject object,
		AvailObject aTupleType);

	/**
	 * @param object
	 * @param anEnumerationType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfEnumerationType (
		@NotNull AvailObject object,
		@NotNull AvailObject anEnumerationType);

	/**
	 * @param object
	 * @param chunk
	 * @param offset
	 */
	abstract void o_LevelTwoChunkOffset (
		@NotNull AvailObject object,
		AvailObject chunk,
		int offset);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Literal (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_LiteralAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_ArgOrLocalOrStackAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_ArgOrLocalOrStackAtPut (
		@NotNull AvailObject object,
		int index,
		AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_LocalTypeAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param argumentTypeList
	 * @return
	 */
	abstract AvailObject o_LookupByTypesFromList (
		@NotNull AvailObject object,
		List<AvailObject> argumentTypeList);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	abstract AvailObject o_LookupByTypesFromContinuationStackp (
		@NotNull AvailObject object,
		AvailObject continuation,
		int stackp);

	/**
	 * @param object
	 * @param argumentTypeTuple
	 * @return
	 */
	abstract AvailObject o_LookupByTypesFromTuple (
		@NotNull AvailObject object,
		AvailObject argumentTypeTuple);

	/**
	 * @param object
	 * @param argumentList
	 * @return
	 */
	abstract AvailObject o_LookupByValuesFromList (
		@NotNull AvailObject object,
		List<AvailObject> argumentList);

	/**
	 * @param object
	 * @param argumentTuple
	 * @return
	 */
	abstract AvailObject o_LookupByValuesFromTuple (
		@NotNull AvailObject object,
		AvailObject argumentTuple);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	abstract AvailObject o_MapAt (
		@NotNull AvailObject object,
		AvailObject keyObject);

	/**
	 * @param object
	 * @param keyObject
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_MapAtPuttingCanDestroy (
		@NotNull AvailObject object,
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
		@NotNull AvailObject object,
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
		@NotNull AvailObject object,
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
	abstract @NotNull AvailObject o_MultiplyByInfinityCanDestroy (
		@NotNull AvailObject object,
		@NotNull Sign sign,
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
	abstract @NotNull AvailObject o_MultiplyByIntegerCanDestroy (
		@NotNull AvailObject object,
		@NotNull AvailObject anInteger,
		boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_MyType (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Name (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param trueName
	 * @return
	 */
	abstract boolean o_NameVisible (
		@NotNull AvailObject object,
		AvailObject trueName);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract boolean o_OptionallyNilOuterVar (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_OuterTypeAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_OuterVarAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_OuterVarAtPut (
		@NotNull AvailObject object,
		int index,
		AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Parent (
		@NotNull AvailObject object,
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
		@NotNull AvailObject object,
		AvailObject aNumber,
		boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Priority (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	abstract AvailObject o_PrivateAddElement (
		@NotNull AvailObject object,
		AvailObject element);

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	abstract AvailObject o_PrivateExcludeElement (
		@NotNull AvailObject object,
		AvailObject element);

	/**
	 * @param object
	 * @param element
	 * @param knownIndex
	 * @return
	 */
	abstract AvailObject o_PrivateExcludeElementKnownIndex (
		@NotNull AvailObject object,
		AvailObject element,
		int knownIndex);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_ProcessGlobals (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract short o_RawByteAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	abstract void o_RawByteAtPut (
		@NotNull AvailObject object,
		int index,
		short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract short o_RawByteForCharacterAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	abstract void o_RawByteForCharacterAtPut (
		@NotNull AvailObject object,
		int index,
		short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract byte o_RawNybbleAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param aNybble
	 */
	abstract void o_RawNybbleAtPut (
		@NotNull AvailObject object,
		int index,
		byte aNybble);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract int o_RawShortForCharacterAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	abstract void o_RawShortForCharacterAtPut (
		@NotNull AvailObject object,
		int index,
		int anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract int o_RawSignedIntegerAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_RawSignedIntegerAtPut (
		@NotNull AvailObject object,
		int index,
		int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract long o_RawUnsignedIntegerAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_RawUnsignedIntegerAtPut (
		@NotNull AvailObject object,
		int index,
		int value);

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	abstract void o_RemoveDependentChunkIndex (
		@NotNull AvailObject object,
		int aChunkIndex);

	/**
	 * @param object
	 * @param anInterpreter
	 */
	abstract void o_RemoveFrom (
		@NotNull AvailObject object,
		L2Interpreter anInterpreter);

	/**
	 * @param object
	 * @param implementation
	 */
	abstract void o_RemoveImplementation (
		@NotNull AvailObject object,
		AvailObject implementation);

	/**
	 * @param object
	 * @param bundle
	 * @return
	 */
	abstract boolean o_RemoveBundle (
		@NotNull AvailObject object,
		AvailObject bundle);

	/**
	 * @param object
	 * @param obsoleteRestrictions
	 */
	abstract void o_RemoveGrammaticalRestrictions (
		@NotNull AvailObject object,
		AvailObject obsoleteRestrictions);

	/**
	 * @param object
	 * @param forwardImplementation
	 * @param methodName
	 */
	abstract void o_ResolvedForwardWithName (
		@NotNull AvailObject object,
		AvailObject forwardImplementation,
		AvailObject methodName);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_ReturnType (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SetIntersectionCanDestroy (
		@NotNull AvailObject object,
		AvailObject otherSet,
		boolean canDestroy);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SetMinusCanDestroy (
		@NotNull AvailObject object,
		AvailObject otherSet,
		boolean canDestroy);

	/**
	 * @param object
	 * @param zoneIndex
	 * @param newTuple
	 */
	abstract void o_SetSubtupleForZoneTo (
		@NotNull AvailObject object,
		int zoneIndex,
		AvailObject newTuple);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SetUnionCanDestroy (
		@NotNull AvailObject object,
		AvailObject otherSet,
		boolean canDestroy);

	/**
	 * @param object
	 * @param newValue
	 */
	abstract void o_SetValue (
		@NotNull AvailObject object,
		AvailObject newValue);

	/**
	 * @param object
	 * @param newElementObject
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SetWithElementCanDestroy (
		@NotNull AvailObject object,
		AvailObject newElementObject,
		boolean canDestroy);

	/**
	 * @param object
	 * @param elementObjectToExclude
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_SetWithoutElementCanDestroy (
		@NotNull AvailObject object,
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
	 * @param value
	 */
	abstract void o_LazyActions (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param slotIndex
	 * @return
	 */
	abstract AvailObject o_StackAt (
		@NotNull AvailObject object,
		int slotIndex);

	/**
	 * @param object
	 * @param slotIndex
	 * @param anObject
	 */
	abstract void o_StackAtPut (
		@NotNull AvailObject object,
		int slotIndex,
		AvailObject anObject);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Stackp (AvailObject object, int value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Start (
		@NotNull AvailObject object,
		int value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_StartingChunk (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	abstract int o_StartOfZone (
		@NotNull AvailObject object,
		int zone);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	abstract int o_StartSubtupleIndexInZone (
		@NotNull AvailObject object,
		int zone);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_String (
		@NotNull AvailObject object,
		AvailObject value);

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
	abstract @NotNull AvailObject o_SubtractFromInfinityCanDestroy (
		@NotNull AvailObject object,
		@NotNull Sign sign,
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
	abstract @NotNull AvailObject o_SubtractFromIntegerCanDestroy (
		@NotNull AvailObject object,
		@NotNull AvailObject anInteger,
		boolean canDestroy);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	abstract AvailObject o_SubtupleForZone (
		@NotNull AvailObject object,
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
	abstract @NotNull AvailObject o_TimesCanDestroy (
		@NotNull AvailObject object,
		@NotNull AvailObject aNumber,
		boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_TokenType (
		@NotNull AvailObject object,
		TokenDescriptor.TokenType value);

	/**
	 * @param object
	 * @param tupleIndex
	 * @param zoneIndex
	 * @return
	 */
	abstract int o_TranslateToZone (
		@NotNull AvailObject object,
		int tupleIndex,
		int zoneIndex);

	/**
	 * @param object
	 * @param stringName
	 * @return
	 */
	abstract AvailObject o_TrueNamesForStringName (
		@NotNull AvailObject object,
		AvailObject stringName);

	/**
	 * @param object
	 * @param newTupleSize
	 * @return
	 */
	abstract AvailObject o_TruncateTo (
		@NotNull AvailObject object,
		int newTupleSize);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_TupleAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param aNybbleObject
	 */
	abstract void o_TupleAtPut (
		@NotNull AvailObject object,
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
		@NotNull AvailObject object,
		int index,
		AvailObject newValueObject,
		boolean canDestroy);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract int o_TupleIntAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Type (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract AvailObject o_TypeAtIndex (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract AvailObject o_TypeIntersection (
		@NotNull AvailObject object,
		AvailObject another);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfFunctionType (
		@NotNull AvailObject object,
		AvailObject aFunctionType);

	/**
	 * @param object
	 * @param aVariableType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfVariableType (
		@NotNull AvailObject object,
		AvailObject aVariableType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfContinuationType (
		@NotNull AvailObject object,
		AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfCompiledCodeType (
		@NotNull AvailObject object,
		AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfIntegerRangeType (
		@NotNull AvailObject object,
		AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfMapType (
		@NotNull AvailObject object,
		AvailObject aMapType);

	/**
	 * @param object
	 * @param someMeta
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfMeta (
		@NotNull AvailObject object,
		AvailObject someMeta);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfObjectType (
		@NotNull AvailObject object,
		AvailObject anObjectType);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfParseNodeType (
		@NotNull AvailObject object,
		AvailObject aParseNodeType);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfPojoType (
		@NotNull AvailObject object,
		@NotNull AvailObject aPojoType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfSetType (
		@NotNull AvailObject object,
		AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract AvailObject o_TypeIntersectionOfTupleType (
		@NotNull AvailObject object,
		AvailObject aTupleType);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract AvailObject o_TypeUnion (
		@NotNull AvailObject object,
		AvailObject another);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfFunctionType (
		@NotNull AvailObject object,
		AvailObject aFunctionType);

	/**
	 * @param object
	 * @param aVariableType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfVariableType (
		@NotNull AvailObject object,
		AvailObject aVariableType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfContinuationType (
		@NotNull AvailObject object,
		AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfCompiledCodeType (
		@NotNull AvailObject object,
		AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfIntegerRangeType (
		@NotNull AvailObject object,
		AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfMapType (
		@NotNull AvailObject object,
		AvailObject aMapType);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfObjectType (
		@NotNull AvailObject object,
		AvailObject anObjectType);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfPojoType (
		@NotNull AvailObject object,
		@NotNull AvailObject aPojoType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfSetType (
		@NotNull AvailObject object,
		AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract AvailObject o_TypeUnionOfTupleType (
		@NotNull AvailObject object,
		AvailObject aTupleType);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Unclassified (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	abstract AvailObject o_UnionOfTypesAtThrough (
		@NotNull AvailObject object,
		int startIndex,
		int endIndex);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract int o_UntranslatedDataAt (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	abstract void o_UntranslatedDataAtPut (
		@NotNull AvailObject object,
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
		@NotNull AvailObject object,
		List<AvailObject> argTypes,
		Interpreter anAvailInterpreter,
		Continuation1<Generator<String>> failBlock);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Value (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract int o_ZoneForIndex (
		@NotNull AvailObject object,
		int index);

	/**
	 * @param object
	 * @return
	 */
	abstract String o_AsNativeString (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_AsSet (AvailObject object);

	/**
	 * @param object
	 * @return
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
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_CopyAsMutableObjectTuple (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_CopyAsMutableSpliceTuple (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_DefaultType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_ParsingPc (AvailObject object);

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
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	/**
	 * @param object
	 * @return
	 */
	abstract long o_ExtractLong (@NotNull AvailObject object);

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
	abstract AvailObject o_ImplementationsTuple (
		@NotNull AvailObject object);

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
	abstract int o_InterruptRequestFlag (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_InvocationCount (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsAbstract (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsFinite (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsForward (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsMethod (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPositive (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
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
		@NotNull AvailObject object);

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
	 * @param object
	 * @return
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
	abstract AvailObject o_ProcessGlobals (AvailObject object);

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
	abstract AvailObject o_Signature (AvailObject object);

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
		@NotNull AvailObject object);

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
	abstract AvailObject o_Unclassified (AvailObject object);

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
	 * @param object
	 * @param another
	 * @return
	 */
	abstract boolean o_Equals (
		@NotNull AvailObject object,
		AvailObject another);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	abstract boolean o_EqualsAnyTuple (
		@NotNull AvailObject object,
		AvailObject aTuple);

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	abstract boolean o_EqualsByteString (
		@NotNull AvailObject object,
		AvailObject aString);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	abstract boolean o_EqualsByteTuple (
		@NotNull AvailObject object,
		AvailObject aTuple);

	/**
	 * @param object
	 * @param otherCodePoint
	 * @return
	 */
	abstract boolean o_EqualsCharacterWithCodePoint (
		@NotNull AvailObject object,
		int otherCodePoint);

	/**
	 * @param object
	 * @param aFunction
	 * @return
	 */
	abstract boolean o_EqualsFunction (
		@NotNull AvailObject object,
		AvailObject aFunction);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	abstract boolean o_EqualsFunctionType (
		@NotNull AvailObject object,
		AvailObject aFunctionType);

	/**
	 * @param object
	 * @param aCompiledCode
	 * @return
	 */
	abstract boolean o_EqualsCompiledCode (
		@NotNull AvailObject object,
		AvailObject aCompiledCode);

	/**
	 * @param object
	 * @param aVariable
	 * @return
	 */
	abstract boolean o_EqualsVariable (
		@NotNull AvailObject object,
		AvailObject aVariable);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_EqualsVariableType (
		@NotNull AvailObject object,
		AvailObject aType);

	/**
	 * @param object
	 * @param aContinuation
	 * @return
	 */
	abstract boolean o_EqualsContinuation (
		@NotNull AvailObject object,
		AvailObject aContinuation);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract boolean o_EqualsContinuationType (
		@NotNull AvailObject object,
		AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract boolean o_EqualsCompiledCodeType (
		@NotNull AvailObject object,
		AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param aDouble
	 * @return
	 */
	abstract boolean o_EqualsDouble (
		final @NotNull AvailObject object,
		final double aDouble);

	/**
	 * @param object
	 * @param aFloat
	 * @return
	 */
	abstract boolean o_EqualsFloat (
		final @NotNull AvailObject object,
		final float aFloat);

	/**
	 * @param object
	 * @param sign
	 * @return
	 */
	abstract boolean o_EqualsInfinity (
		final @NotNull AvailObject object,
		final @NotNull Sign sign);

	/**
	 * @param object
	 * @param anAvailInteger
	 * @return
	 */
	abstract boolean o_EqualsInteger (
		@NotNull AvailObject object,
		AvailObject anAvailInteger);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract boolean o_EqualsIntegerRangeType (
		@NotNull AvailObject object,
		AvailObject another);

	/**
	 * @param object
	 * @param aMap
	 * @return
	 */
	abstract boolean o_EqualsMap (
		@NotNull AvailObject object,
		AvailObject aMap);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	abstract boolean o_EqualsMapType (
		@NotNull AvailObject object,
		AvailObject aMapType);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	abstract boolean o_EqualsNybbleTuple (
		@NotNull AvailObject object,
		AvailObject aTuple);

	/**
	 * @param object
	 * @param anObject
	 * @return
	 */
	abstract boolean o_EqualsObject (
		@NotNull AvailObject object,
		AvailObject anObject);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	abstract boolean o_EqualsObjectTuple (
		@NotNull AvailObject object,
		AvailObject aTuple);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract boolean o_EqualsParseNodeType (
		@NotNull AvailObject object,
		@NotNull AvailObject aParseNodeType);

	/**
	 * @param object
	 * @param aPojo
	 * @return
	 */
	abstract boolean o_EqualsPojo (
		@NotNull AvailObject object,
		@NotNull AvailObject aPojo);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract boolean o_EqualsPojoType (
		@NotNull AvailObject object,
		@NotNull AvailObject aPojoType);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_EqualsPrimitiveType (
		@NotNull AvailObject object,
		AvailObject aType);

	/**
	 * @param object
	 * @param aPojo
	 * @return
	 */
	abstract boolean o_EqualsRawPojo (
		@NotNull AvailObject object,
		@NotNull AvailObject aPojo);

	/**
	 * @param object
	 * @param aSet
	 * @return
	 */
	abstract boolean o_EqualsSet (
		@NotNull AvailObject object,
		AvailObject aSet);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	abstract boolean o_EqualsSetType (
		@NotNull AvailObject object,
		AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract boolean o_EqualsTupleType (
		@NotNull AvailObject object,
		AvailObject aTupleType);

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	abstract boolean o_EqualsTwoByteString (
		@NotNull AvailObject object,
		AvailObject aString);

	/**
	 * @param object
	 * @param potentialInstance
	 * @return
	 */
	abstract boolean o_HasObjectInstance (
		@NotNull AvailObject object,
		AvailObject potentialInstance);

	/**
	 * @param object
	 * @param anotherObject
	 * @return
	 */
	abstract boolean o_IsBetterRepresentationThan (
		@NotNull AvailObject object,
		AvailObject anotherObject);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract boolean o_IsBetterRepresentationThanTupleType (
		@NotNull AvailObject object,
		AvailObject aTupleType);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_IsInstanceOfKind (
		@NotNull AvailObject object,
		AvailObject aType);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_EqualsNull (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_Hash (AvailObject object);

	/**
	 * @param object
	 * @return
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
	abstract AvailObject o_Kind (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsBoolean (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail byte tuple?
	 *
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is a byte tuple, {@code false}
	 *         otherwise.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsByteTuple (@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsCharacter (AvailObject object);

	/**
	 * Is the specified {@link AvailObject} an Avail string?
	 *
	 * @param object An {@link AvailObject}.
	 * @return {@code true} if the argument is an Avail string, {@code false}
	 *         otherwise.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsString (@NotNull AvailObject object);

	/**
	 * @param object
	 * @param aFunction
	 * @return
	 */
	abstract boolean o_ContainsBlock (
		@NotNull AvailObject object,
		AvailObject aFunction);

	/**
	 * @param object
	 */
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
	 * @param object
	 * @return
	 */
	abstract boolean o_IsMap (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsUnsignedByte (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsNybble (AvailObject object);

	/**
	 * @param object
	 * @return
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
		@NotNull AvailObject object,
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
		@NotNull AvailObject object,
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
		@NotNull AvailObject object,
		AvailObject elementObject,
		int elementObjectHash,
		boolean canDestroy);

	/**
	 * @param object
	 * @param potentialSuperset
	 * @return
	 */
	abstract boolean o_IsBinSubsetOf (
		@NotNull AvailObject object,
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
	 * @param object
	 * @return
	 */
	abstract AvailObject o_BinUnionTypeOrNull (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsAtom (AvailObject object);

	/**
	 * @param object
	 * @return
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
		@NotNull AvailObject object,
		AvailSubobjectVisitor visitor);

	/**
	 * Answer an {@linkplain Iterator iterator} suitable for traversing the
	 * elements of the {@linkplain AvailObject object} with a Java
	 * <em>foreach</em> construct.
	 *
	 * @param object An {@link AvailObject}.
	 * @return An {@linkplain Iterator iterator}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull Iterator<AvailObject> o_Iterator (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ParsingInstructions (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param expression
	 */
	abstract void o_Expression (
		@NotNull AvailObject object,
		AvailObject expression);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Expression (AvailObject object);

	/**
	 * @param object
	 * @param variable
	 */
	abstract void o_Variable (
		@NotNull AvailObject object,
		AvailObject variable);

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
		@NotNull AvailObject object,
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
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param initializationExpression
	 */
	abstract void o_InitializationExpression (
		@NotNull AvailObject object,
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
	 * @param markerValue
	 */
	abstract void o_MarkerValue (
		@NotNull AvailObject object,
		AvailObject markerValue);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_MarkerValue (AvailObject object);

	/**
	 * @param object
	 * @param arguments
	 */
	abstract void o_Arguments (
		@NotNull AvailObject object,
		AvailObject arguments);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Arguments (AvailObject object);

	/**
	 * @param object
	 * @param method
	 */
	abstract void o_Method (
		@NotNull AvailObject object,
		AvailObject method);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Method (AvailObject object);

	/**
	 * @param object
	 * @param superCastType
	 */
	abstract void o_SuperCastType (
		@NotNull AvailObject object,
		AvailObject superCastType);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_SuperCastType (AvailObject object);

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
		@NotNull AvailObject object,
		AvailCodeGenerator codeGenerator);

	/**
	 * @param object
	 * @param codeGenerator
	 */
	abstract void o_EmitValueOn (
		@NotNull AvailObject object,
		AvailCodeGenerator codeGenerator);

	/**
	 * Map my children through the (destructive) transformation specified by
	 * aBlock.
	 *
	 * @param object
	 * @param aBlock
	 */
	abstract void o_ChildrenMap (
		@NotNull AvailObject object,
		Transformer1<AvailObject, AvailObject> aBlock);

	/**
	 * Visit my child parse nodes with aBlock.
	 *
	 * @param object
	 * @param aBlock
	 */
	abstract void o_ChildrenDo (
		@NotNull AvailObject object,
		Continuation1<AvailObject> aBlock);

	/**
	 * @param object
	 * @param parent
	 * @param outerBlocks
	 * @param anAvailInterpreter
	 */
	abstract void o_ValidateLocally (
		 @NotNull AvailObject object,
		 AvailObject parent,
		 List<AvailObject> outerBlocks,
		 L2Interpreter anAvailInterpreter);

	/**
	 * @param object
	 * @param codeGenerator
	 * @return
	 */
	abstract AvailObject o_Generate (
		@NotNull AvailObject object,
		AvailCodeGenerator codeGenerator);

	/**
	 * @param object
	 * @param newParseNode
	 * @return
	 */
	abstract AvailObject o_CopyWith (
		@NotNull AvailObject object,
		AvailObject newParseNode);

	/**
	 * @param object
	 * @param isLastUse
	 */
	abstract void o_IsLastUse (
		@NotNull AvailObject object,
		boolean isLastUse);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsLastUse (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsMacro (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Macros (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Macros (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_CopyMutableParseNode (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_BinUnionKind (AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_MacroName (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_MacroName (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_OutputParseNode (
		@NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_OutputParseNode (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ApparentSendName (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param statementsTuple
	 */
	abstract void o_Statements (
		@NotNull AvailObject object, AvailObject statementsTuple);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Statements (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param accumulatedStatements
	 */
	abstract void o_FlattenStatementsInto (
		@NotNull AvailObject object,
		List<AvailObject> accumulatedStatements);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_LineNumber (AvailObject object, int value);

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
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Complete (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Incomplete (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Actions (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_CheckedExceptions (
		@NotNull AvailObject object);

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
		@NotNull AvailObject object,
		AvailObject anObject);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_Instances (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param aSet
	 * @return
	 */
	boolean o_EqualsEnumerationWithSet (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSet)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsEnumeration (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_IsInstanceOf (
		@NotNull AvailObject object,
		@NotNull AvailObject aType);

	/**
	 * @param object
	 * @param potentialInstance
	 * @return
	 */
	abstract boolean o_EnumerationIncludesInstance (
		@NotNull AvailObject object,
		@NotNull AvailObject potentialInstance);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_ComputeSuperkind (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param key
	 * @param value
	 */
	abstract void o_SetAtomProperty (
		@NotNull AvailObject object,
		@NotNull AvailObject key,
		@NotNull AvailObject value);

	/**
	 * @param object
	 * @param key
	 * @return
	 */
	abstract @NotNull AvailObject o_GetAtomProperty (
		@NotNull AvailObject object,
		AvailObject key);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_InnerKind (@NotNull AvailObject object);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract boolean o_EqualsEnumerationType (
		@NotNull AvailObject object,
		@NotNull AvailObject another);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsEnumerationType (@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_ReadType (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_WriteType (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Versions (
		@NotNull AvailObject object,
		@NotNull AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_Versions (@NotNull AvailObject object);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract @NotNull AvailObject o_TypeUnionOfParseNodeType (
		@NotNull AvailObject object,
		AvailObject aParseNodeType);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull ParseNodeKind o_ParseNodeKind (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param expectedParseNodeKind
	 * @return
	 */
	abstract boolean o_ParseNodeKindIsUnder (
		@NotNull AvailObject object,
		@NotNull ParseNodeKind expectedParseNodeKind);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsRawPojo (@NotNull AvailObject object);

	/**
	 * @param object
	 * @param restrictionSignature
	 */
	abstract void o_AddTypeRestriction (
		@NotNull AvailObject object,
		@NotNull AvailObject restrictionSignature);

	/**
	 * @param object
	 * @param restrictionSignature
	 */
	abstract void o_RemoveTypeRestriction (
		@NotNull AvailObject object,
		@NotNull AvailObject restrictionSignature);

	/**
	 * Return the {@linkplain MethodDescriptor method}'s
	 * {@linkplain TupleDescriptor tuple} of {@linkplain FunctionDescriptor
	 * functions} that statically restrict call sites by argument type.
	 *
	 * @param object The method.
	 * @return
	 */
	abstract @NotNull AvailObject o_TypeRestrictions (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param tupleType
	 */
	abstract void o_AddSealedArgumentsType (
		@NotNull AvailObject object,
		@NotNull AvailObject tupleType);

	/**
	 * @param object
	 * @param tupleType
	 */
	abstract void o_RemoveSealedArgumentsType (
		@NotNull AvailObject object,
		@NotNull AvailObject tupleType);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_SealedArgumentsTypesTuple (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @param methodNameAtom
	 * @param typeRestrictionFunction
	 */
	abstract void o_AddTypeRestriction (
		@NotNull AvailObject object,
		@NotNull AvailObject methodNameAtom,
		@NotNull AvailObject typeRestrictionFunction);

	/**
	 * @param object
	 * @param name
	 * @param constantBinding
	 */
	abstract void o_AddConstantBinding (
		@NotNull AvailObject object,
		@NotNull AvailObject name,
		@NotNull AvailObject constantBinding);

	/**
	 * @param object
	 * @param name
	 * @param variableBinding
	 */
	abstract void o_AddVariableBinding (
		@NotNull AvailObject object,
		@NotNull AvailObject name,
		@NotNull AvailObject variableBinding);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsMethodEmpty (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPojoSelfType (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_PojoSelfType (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_JavaClass (
		@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsUnsignedShort (@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_ExtractUnsignedShort (@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsFloat (@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsDouble (@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_RawPojo (@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPojo (@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPojoType (@NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_UpperBoundMap (@NotNull AvailObject object);

	/**
	 * @param object
	 * @param aMap
	 */
	abstract void o_UpperBoundMap (
		@NotNull AvailObject object,
		@NotNull AvailObject aMap);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract @NotNull Order o_NumericCompare (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	/**
	 * @param object
	 * @param aDouble
	 * @return
	 */
	abstract @NotNull Order o_NumericCompareToDouble (
		final @NotNull AvailObject object,
		double aDouble);

	/**
	 * @param object
	 * @param anInteger
	 * @return
	 */
	abstract @NotNull Order o_NumericCompareToInteger (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger);

	/**
	 * @param object
	 * @param sign
	 * @return
	 */
	abstract @NotNull Order o_NumericCompareToInfinity (
		final @NotNull AvailObject object,
		final @NotNull Sign sign);

	/**
	 * @param object
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	abstract @NotNull AvailObject o_AddToDoubleCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract @NotNull AvailObject o_AddToFloatCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject floatObject,
		boolean canDestroy);

	/**
	 * @param object
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	abstract @NotNull AvailObject o_SubtractFromDoubleCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract @NotNull AvailObject o_SubtractFromFloatCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject floatObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	abstract @NotNull AvailObject o_MultiplyByDoubleCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract @NotNull AvailObject o_MultiplyByFloatCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject floatObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	abstract @NotNull AvailObject o_DivideIntoDoubleCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract @NotNull AvailObject o_DivideIntoFloatCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject floatObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_AllBundles (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_LazyPrefilterMap (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value);


	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_LazyPrefilterMap (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param key
	 * @param keyHash
	 * @param value
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	abstract @NotNull AvailObject o_MapBinAtHashPutLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final int keyHash,
		final @NotNull AvailObject value,
		final byte myLevel,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param key
	 * @param keyHash
	 * @param canDestroy
	 * @return
	 */
	abstract @NotNull AvailObject o_MapBinRemoveKeyHashCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final int keyHash,
		final boolean canDestroy);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_MapBinKeyUnionKind (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_MapBinValueUnionKind (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsHashedMapBin (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param key
	 * @param keyHash
	 * @return
	 */
	abstract @NotNull AvailObject o_MapBinAtHash (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final int keyHash);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_MapBinKeysHash (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_MapBinValuesHash (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_IssuingModule (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPojoFusedType (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfPojoBottomType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_EqualsPojoBottomType (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_JavaAncestors (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param aFusedPojoType
	 * @return
	 */
	abstract @NotNull AvailObject o_TypeIntersectionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType);

	/**
	 * @param object
	 * @param anUnfusedPojoType
	 * @return
	 */
	abstract @NotNull AvailObject o_TypeIntersectionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType);

	/**
	 * @param object
	 * @param aFusedPojoType
	 * @return
	 */
	abstract @NotNull AvailObject o_TypeUnionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType);

	/**
	 * @param object
	 * @param anUnfusedPojoType
	 * @return
	 */
	abstract @NotNull AvailObject o_TypeUnionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsPojoArrayType (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param ignoredClassHint
	 * @return
	 */
	abstract Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull AvailObject o_TypeVariables (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param field
	 * @param receiver
	 * @return
	 */
	abstract boolean o_EqualsPojoField (
		final @NotNull AvailObject object,
		final @NotNull AvailObject field,
		final @NotNull AvailObject receiver);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsSignedByte (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsSignedShort (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract byte o_ExtractSignedByte (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract short o_ExtractSignedShort (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param aRawPojo
	 * @return
	 */
	abstract boolean o_EqualsEqualityRawPojo (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aRawPojo);

	/**
	 * @param object
	 * @return
	 */
	abstract Object o_JavaObject (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @NotNull BigInteger o_AsBigInteger (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param newElement
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_AppendCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject newElement,
		final boolean canDestroy);
}
