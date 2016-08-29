/**
 * AbstractDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import static java.lang.Math.max;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.descriptor.FiberDescriptor.GeneralFlag;
import com.avail.descriptor.FiberDescriptor.InterruptRequestFlag;
import com.avail.descriptor.FiberDescriptor.SynchronizationFlag;
import com.avail.descriptor.FiberDescriptor.TraceFlag;
import com.avail.descriptor.InfinityDescriptor.IntegerSlots;
import com.avail.descriptor.MapDescriptor.MapIterable;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.SetDescriptor.SetIterator;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.AvailException;
import com.avail.exceptions.AvailUnsupportedOperationException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.SignatureException;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.io.TextInterface;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.Generator;
import com.avail.utility.MutableOrNull;
import com.avail.utility.Strings;
import com.avail.utility.evaluation.*;
import com.avail.utility.json.JSONWriter;
import com.avail.utility.visitor.AvailSubobjectVisitor;

/**
 * {@link AbstractDescriptor} is the base descriptor type.  An {@link
 * AvailObject} contains a descriptor, to which it delegates nearly all of its
 * behavior.  That allows interesting operations like effective type mutation
 * (within a language that does not support it directly, such as Java).  It also
 * allows multiple representations of equivalent objects, such as more than one
 * representation for the tuple {@code <1,2,3>}.  It can be represented as an
 * AvailObject using either an {@link ObjectTupleDescriptor}, a {@link
 * ByteTupleDescriptor}, a {@link NybbleTupleDescriptor}, or a {@link
 * TreeTupleDescriptor}.  It could even be an {@link IndirectionDescriptor} if
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
	 * Used for quickly checking object fields when {@link
	 * AvailObjectRepresentation#shouldCheckSlots} is enabled.
	 */
	public final ObjectSlotsEnum [][] debugObjectSlots;

	/**
	 * Used for quickly checking integer fields when {@link
	 * AvailObjectRepresentation#shouldCheckSlots} is enabled.
	 */
	public final IntegerSlotsEnum [][] debugIntegerSlots;


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
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	@SuppressWarnings("null")
	protected AbstractDescriptor (
		final Mutability mutability,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		this.mutability = mutability;

		final ObjectSlotsEnum [] objectSlots = objectSlotsEnumClass != null
			? objectSlotsEnumClass.getEnumConstants()
			: new ObjectSlotsEnum[0];
		debugObjectSlots =
			new ObjectSlotsEnum[max(objectSlots.length, 1)][];
		hasVariableObjectSlots =
			objectSlots.length > 0
			&& objectSlots[objectSlots.length - 1].name().endsWith("_");
		numberOfFixedObjectSlots =
			objectSlots.length - (hasVariableObjectSlots ? 1 : 0);

		final IntegerSlotsEnum [] integerSlots = integerSlotsEnumClass != null
			? integerSlotsEnumClass.getEnumConstants()
			: new IntegerSlotsEnum[0];
		debugIntegerSlots =
			new IntegerSlotsEnum [max(integerSlots.length, 1)][];
		hasVariableIntegerSlots =
			integerSlots.length > 0
			&& integerSlots[integerSlots.length - 1].name().endsWith("_");
		numberOfFixedIntegerSlots =
			integerSlots.length - (hasVariableIntegerSlots ? 1 : 0);
	}


	/**
	 * Look up the specified {@link Annotation} from the {@link Enum} constant.
	 * If the enumeration constant does not have an annotation of that type then
	 * answer null.
	 *
	 * @param <A> The {@code Annotation} type.
	 * @param enumConstant The {@code Enum} value.
	 * @param annotationClass The {@link Class} of the {@code Annotation} type.
	 * @return The requested annotation or null.
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
	 *        The {@link AvailObject} to describe.
	 * @return An array of {@link AvailObjectFieldHelper}s that describe the
	 *         logical parts of the given object.
	 */
	@SuppressWarnings("unchecked")
	AvailObjectFieldHelper[] o_DescribeForDebugger (
		final AvailObject object)
	{
		final List<AvailObjectFieldHelper> fields = new ArrayList<>();
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
									(IntegerSlotsEnum)slot, subscript))));
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
	 * @param recursionMap Which ancestor objects are currently being printed.
	 * @param indent What level to indent subsequent lines.
	 */
	@SuppressWarnings("unchecked")
	@ThreadSafe
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
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
			// Circled Latin capital letter M.
			builder.append("\u24C2");
		}
		else if (isShared())
		{
			// Circled Latin capital letter S.
			builder.append("\u24C8");
		}
		final Class<Descriptor> cls = (Class<Descriptor>) this.getClass();
		final ClassLoader loader = cls.getClassLoader();
		Class<? extends IntegerSlotsEnum> intEnumClass;

		try
		{
			intEnumClass =
				(Class<? extends IntegerSlotsEnum>) loader.loadClass(
					cls.getCanonicalName() + "$IntegerSlots");
		}
		catch (final ClassNotFoundException e)
		{
			intEnumClass = null;
		}
		final IntegerSlotsEnum[] intSlots = intEnumClass != null
			? intEnumClass.getEnumConstants()
			: new IntegerSlotsEnum[0];

		for (int i = 1, limit = object.integerSlotsCount(); i <= limit; i++)
		{
			final int ordinal = Math.min(i, intSlots.length) - 1;
			final IntegerSlotsEnum slot = intSlots[ordinal];
			if (getAnnotation((Enum<?>)slot, HideFieldInDebugger.class) == null
				&& getAnnotation((Enum<?>)slot, HideFieldJustForPrinting.class)
					== null)
			{
				Strings.newlineTab(builder, indent);
				final String slotName = slot.name();
				assert slotName != null;
				long value;
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

		Class<? extends ObjectSlotsEnum> objectEnumClass;
		try
		{
			objectEnumClass =
				(Class<? extends ObjectSlotsEnum>) loader.loadClass(
					cls.getCanonicalName() + "$ObjectSlots");
		}
		catch (final ClassNotFoundException e)
		{
			objectEnumClass = null;
		}
		final ObjectSlotsEnum[] objectSlots = objectEnumClass != null
			? objectEnumClass.getEnumConstants()
			: new ObjectSlotsEnum[0];

		for (int i = 1, limit = object.objectSlotsCount(); i <= limit; i++)
		{
			final int ordinal = Math.min(i, objectSlots.length) - 1;
			final ObjectSlotsEnum slot = objectSlots[ordinal];
			if (getAnnotation((Enum<?>)slot, HideFieldInDebugger.class) == null
				&& getAnnotation((Enum<?>)slot, HideFieldJustForPrinting.class)
					== null)
			{
				Strings.newlineTab(builder, indent);
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
						recursionMap,
						indent + 1);
				}
				else
				{
					builder.append(slotName);
					builder.append(" = ");
					object.slot(slot).printOnAvoidingIndent(
						builder,
						recursionMap,
						indent + 1);
				}
			}
		}
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		final Class<? extends AbstractDescriptor> thisClass = getClass();
		builder.append(thisClass.getSimpleName());
		int fieldCount = 0;
		final List<Class<?>> supers = new ArrayList<>();
		for (
			Class<? extends Object> cls = thisClass;
			cls != Object.class;
			cls = cls.getSuperclass())
		{
			supers.add(0, cls);  // top-down
		}
		for (final Class<?> cls : supers)
		{
			for (final Field f : cls.getDeclaredFields())
			{
				if (!Modifier.isStatic(f.getModifiers()))
				{
					fieldCount++;
					if (fieldCount == 1)
					{
						builder.append("(");
					}
					else
					{
						builder.append(", ");
					}
					builder.append(f.getName());
					builder.append("=");
					try
					{
						builder.append(f.get(this));
					}
					catch (IllegalArgumentException | IllegalAccessException e)
					{
						builder.append("(inaccessible)");
					}
				}
			}
		}
		if (fieldCount > 0)
		{
			builder.append(")");
		}
		return builder.toString();
	}

	/**
	 * A static cache of mappings from {@link IntegerSlotsEnum integer slots} to
	 * {@link List}s of {@link BitField}s.  Access to the map must be
	 * synchronized, which isn't much of a penalty since it only affects the
	 * default object printing mechanism.
	 */
	private static final Map<IntegerSlotsEnum, List<BitField>> bitFieldsCache =
		new HashMap<>(500);

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
		final long value,
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
					bitFields = new ArrayList<>();
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
						builder.append(allValues[(int)value].name());
					}
					else
					{
						builder.append(
							new Formatter().format(
								" (enum out of range: 0x%08X_%08X)",
								value >>> 32L,
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
						describingClass.getMethod(lookupName, Integer.TYPE);
					final IntegerEnumSlotDescriptionEnum lookedUp =
						(IntegerEnumSlotDescriptionEnum)lookupMethod.invoke(
							null, value);
					if (lookedUp == null)
					{
						builder.append(
							new Formatter().format(
								" (enum out of range: 0x%08X_%08X)",
								value >>> 32L,
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
					new Formatter().format(" = 0x%08X_%08X = %d",
						value >>> 32L, value & 0xFFFFFFFFL, value));
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
	 * @see AvailObject#acceptsArgTypesFromFunctionType(A_Type)
	 * @param object A function type.
	 * @param functionType A function type.
	 * @return {@code true} if the arguments of {@code object} are, pairwise,
	 *         more general than those of {@code functionType}, {@code false}
	 *         otherwise.
	 */
	abstract boolean o_AcceptsArgTypesFromFunctionType (
		AvailObject object,
		A_Type functionType);

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
		List<? extends A_Type> argTypes);

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
		List<? extends A_BasicObject> argValues);

	/**
	 * Answer whether these are acceptable {@linkplain TypeDescriptor argument
	 * types} for invoking a {@linkplain FunctionDescriptor function} that is an
	 * instance of {@code object}. There may be more entries in the {@linkplain
	 * TupleDescriptor tuple} than are required by the {@linkplain
	 * FunctionTypeDescriptor function type}.
	 *
	 * @see AvailObject#acceptsTupleOfArgTypes(A_Tuple)
	 * @param object The receiver.
	 * @param argTypes A tuple containing the argument types to be checked.
	 * @return {@code true} if the arguments of the receiver are, pairwise, more
	 *         general than the corresponding elements of the {@code argTypes}
	 *         tuple, {@code false} otherwise.
	 */
	abstract boolean o_AcceptsTupleOfArgTypes (
		AvailObject object,
		A_Tuple argTypes);

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
	 * @see AvailObject#acceptsTupleOfArguments(A_Tuple)
	 */
	abstract boolean o_AcceptsTupleOfArguments (
		AvailObject object,
		A_Tuple arguments);

	/**
	 *
	 *
	 * @param object
	 * @param chunk
	 */
	abstract void o_AddDependentChunk (
		AvailObject object,
		L2Chunk chunk);

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
	 * @see AvailObject#methodAddDefinition(A_Definition)
	 */
	abstract void o_MethodAddDefinition (
			AvailObject object,
			A_Definition definition)
		throws SignatureException;

	/**
	 * Add a {@linkplain GrammaticalRestrictionDescriptor grammatical
	 * restriction} to the receiver.
	 *
	 * @param object
	 *            The receiver, a {@linkplain MessageBundleDescriptor message
	 *            bundle}.
	 * @param grammaticalRestriction
	 *            The grammatical restriction to be added.
	 * @see A_Bundle#addGrammaticalRestriction(A_GrammaticalRestriction)
	 */
	abstract void o_AddGrammaticalRestriction (
		AvailObject object,
		A_GrammaticalRestriction grammaticalRestriction);

	/**
	 * Add the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#plusCanDestroy(A_Number, boolean) plusCanDestroy}. It
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
	abstract A_Number o_AddToInfinityCanDestroy (
		AvailObject object,
		Sign sign,
		boolean canDestroy);

	/**
	 * Add the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#plusCanDestroy(A_Number, boolean) plusCanDestroy}. It
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
	abstract A_Number o_AddToIntegerCanDestroy (
		AvailObject object,
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * @param object
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	abstract void o_AddGrammaticalRestrictions (
		AvailObject object,
		A_Atom methodName,
		A_Tuple illegalArgMsgs);

	/**
	 * @param object
	 * @param definition
	 */
	abstract void o_ModuleAddDefinition (
		AvailObject object,
		A_BasicObject definition);

	/**
	 * @param object
	 * @param bundle
	 */
	abstract void o_AddBundle (AvailObject object, A_Bundle bundle);

	/**
	 * @param object
	 * @param plan
	 */
	abstract void o_AddDefinitionParsingPlan (
		AvailObject object,
		A_DefinitionParsingPlan plan);

	/**
	 * @param object
	 * @param trueName
	 */
	abstract void o_AddImportedName (
		AvailObject object,
		A_Atom trueName);

	/**
	 * @param object
	 * @param trueName
	 */
	abstract void o_IntroduceNewName (
		AvailObject object,
		A_Atom trueName);

	/**
	 * @param object
	 * @param trueName
	 */
	abstract void o_AddPrivateName (
		AvailObject object,
		A_Atom trueName);

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
	 * @param value
	 */
	abstract void o_BreakpointBlock (
		AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	abstract A_BundleTree o_BuildFilteredBundleTree (
		AvailObject object);

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
	 * @see AvailObject#compareFromToWithStartingAt(int, int, A_Tuple, int)
	 */
	abstract boolean o_CompareFromToWithStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		A_Tuple anotherObject,
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
	 * @see A_Tuple#compareFromToWithAnyTupleStartingAt(int, int, A_Tuple, int)
	 */
	abstract boolean o_CompareFromToWithAnyTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		A_Tuple aTuple,
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
	 * @see AvailObject#compareFromToWithByteStringStartingAt(int, int, A_String, int)
	 */
	abstract boolean o_CompareFromToWithByteStringStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		A_String aByteString,
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
	 * @see AvailObject#compareFromToWithByteTupleStartingAt(int, int, A_Tuple, int)
	 */
	abstract boolean o_CompareFromToWithByteTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		A_Tuple aByteTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain IntegerIntervalTupleDescriptor integer
	 * interval tuple}. The size of the subrange of both objects is determined
	 * by the index range supplied for the receiver.
	 *
	 * @param object
	 *        The receiver.
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anIntegerIntervalTuple
	 *        The integer interval tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the integer interval tuple's
	 *        subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 * @see AvailObject#compareFromToWithByteTupleStartingAt(int, int, A_Tuple, int)
	 */
	abstract boolean o_CompareFromToWithIntegerIntervalTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		A_Tuple anIntegerIntervalTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain SmallIntegerIntervalTupleDescriptor small integer
	 * interval tuple}. The size of the subrange of both objects is determined
	 * by the index range supplied for the receiver.
	 *
	 * @param object
	 *        The receiver.
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aSmallIntegerIntervalTuple
	 *        The small integer interval tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the small integer interval tuple's
	 *        subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 * @see AvailObject#compareFromToWithByteTupleStartingAt(int, int, A_Tuple, int)
	 */
	abstract boolean o_CompareFromToWithSmallIntegerIntervalTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		A_Tuple aSmallIntegerIntervalTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain RepeatedElementTupleDescriptor repeated
	 * element tuple}. The size of the subrange of both objects is determined
	 * by the index range supplied for the receiver.
	 *
	 * @param object
	 *        The receiver.
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aRepeatedElementTuple
	 *        The repeated element tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the repeated element tuple's
	 *        subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 * @see AvailObject#compareFromToWithByteTupleStartingAt(int, int, A_Tuple, int)
	 */
	abstract boolean o_CompareFromToWithRepeatedElementTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		A_Tuple aRepeatedElementTuple,
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
	 * @see AvailObject#compareFromToWithNybbleTupleStartingAt(int, int, A_Tuple, int)
	 */
	abstract boolean o_CompareFromToWithNybbleTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		A_Tuple aNybbleTuple,
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
	 * @see AvailObject#compareFromToWithObjectTupleStartingAt(int, int, A_Tuple, int)
     */
	abstract boolean o_CompareFromToWithObjectTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		A_Tuple anObjectTuple,
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
	 * @see AvailObject#compareFromToWithTwoByteStringStartingAt(int, int, A_String, int)
     */
	abstract boolean o_CompareFromToWithTwoByteStringStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		A_String aTwoByteString,
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
	abstract A_Tuple o_ConcatenateTuplesCanDestroy (
		AvailObject object,
		boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Continuation (
		AvailObject object,
		A_Continuation value);

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @param canDestroy
	 * @return
	 */
	abstract A_Tuple o_CopyTupleFromToCanDestroy (
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
		List<? extends A_Type> argTypes);

	/**
	 * Divide the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#divideIntoIntegerCanDestroy(A_Number, boolean)
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
	abstract A_Number o_DivideCanDestroy (
		AvailObject object,
		A_Number aNumber,
		boolean canDestroy);

	/**
	 * Divide an infinity with the given {@linkplain Sign sign} by the
	 * {@linkplain AvailObject object} and answer the {@linkplain AvailObject
	 * result}.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#divideCanDestroy(A_Number, boolean) divideCanDestroy}. It
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
	abstract A_Number o_DivideIntoInfinityCanDestroy (
		AvailObject object,
		Sign sign,
		boolean canDestroy);

	/**
	 * Divide the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#divideCanDestroy(A_Number, boolean) divideCanDestroy}. It
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
	abstract A_Number o_DivideIntoIntegerCanDestroy (
		AvailObject object,
		A_Number anInteger,
		boolean canDestroy);

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
	abstract List<A_Definition> o_FilterByTypes (
		AvailObject object,
		List<? extends A_Type> argTypes);

	/**
	 * Answer whether the {@linkplain AvailObject receiver} contains the
	 * specified element.
	 *
	 * @param object The receiver.
	 * @param elementObject The element.
	 * @return {@code true} if the receiver contains the element, {@code false}
	 *         otherwise.
	 * @see AvailObject#hasElement(A_BasicObject)
	 */
	abstract boolean o_HasElement (
		AvailObject object,
		A_BasicObject elementObject);

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
		A_BasicObject keyObject);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	abstract List<A_Definition> o_DefinitionsAtOrBelow (
		AvailObject object,
		List<? extends A_Type> argTypes);

	/**
	 * @param object
	 * @param definition
	 * @return
	 */
	abstract boolean o_IncludesDefinition (
		AvailObject object,
		A_Definition definition);

	/**
	 * @param object
	 * @param flag
	 */
	abstract void o_SetInterruptRequestFlag (
		AvailObject object,
		InterruptRequestFlag flag);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_CountdownToReoptimize (
		AvailObject object,
		int value);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract boolean o_IsSubsetOf (
		AvailObject object,
		A_Set another);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_IsSubtypeOf (
		AvailObject object,
		A_Type aType);

	/**
	 * @param object
	 * @param aVariableType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfVariableType (
		AvailObject object,
		A_Type aVariableType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfContinuationType (
		AvailObject object,
		A_Type aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfCompiledCodeType (
		AvailObject object,
		A_Type aCompiledCodeType);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfFiberType (
		AvailObject object,
		A_Type aType);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfFunctionType (
		AvailObject object,
		A_Type aFunctionType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfIntegerRangeType (
		AvailObject object,
		A_Type anIntegerRangeType);

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
		A_Type anObjectType);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfParseNodeType (
		AvailObject object,
		A_Type aParseNodeType);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfPojoType (
		AvailObject object,
		A_Type aPojoType);

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
		A_BasicObject anEnumerationType);

	/**
	 * @param object
	 * @param chunk
	 * @param offset
	 */
	abstract void o_LevelTwoChunkOffset (
		AvailObject object,
		L2Chunk chunk,
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
	abstract A_Type o_LocalTypeAt (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param argumentTypeTuple
	 * @return
	 * @throws MethodDefinitionException
	 */
	abstract A_Definition o_LookupByTypesFromTuple (
			AvailObject object,
			A_Tuple argumentTypeTuple)
		throws MethodDefinitionException;

	/**
	 * @param object
	 * @param argumentList
	 * @param errorCode
	 * @return
	 */
	abstract A_Definition o_LookupByValuesFromList (
		AvailObject object,
		List<? extends A_BasicObject> argumentList)
	throws MethodDefinitionException;

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	abstract AvailObject o_MapAt (
		AvailObject object,
		A_BasicObject keyObject);

	/**
	 * @param object
	 * @param keyObject
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	abstract A_Map o_MapAtPuttingCanDestroy (
		AvailObject object,
		A_BasicObject keyObject,
		A_BasicObject newValueObject,
		boolean canDestroy);

	/**
	 * @param object
	 * @param keyObject
	 * @param canDestroy
	 * @return
	 */
	abstract A_Map o_MapWithoutKeyCanDestroy (
		AvailObject object,
		A_BasicObject keyObject,
		boolean canDestroy);

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#subtractFromIntegerCanDestroy(A_Number, boolean)
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
	abstract A_Number o_MinusCanDestroy (
		AvailObject object,
		A_Number aNumber,
		boolean canDestroy);

	/**
	 * Multiply the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#timesCanDestroy(A_Number, boolean) timesCanDestroy}. It
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
	abstract A_Number o_MultiplyByInfinityCanDestroy (
		AvailObject object,
		Sign sign,
		boolean canDestroy);

	/**
	 * Multiply the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#timesCanDestroy(A_Number, boolean) timesCanDestroy}. It
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
	abstract A_Number o_MultiplyByIntegerCanDestroy (
		AvailObject object,
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * @param object
	 * @param trueName
	 * @return
	 */
	abstract boolean o_NameVisible (
		AvailObject object,
		A_Atom trueName);

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
	abstract A_Type o_OuterTypeAt (
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
	 * Add the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#addToIntegerCanDestroy(A_Number, boolean)
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
	abstract A_Number o_PlusCanDestroy (
		AvailObject object,
		A_Number aNumber,
		boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Priority (
		AvailObject object,
		int value);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_FiberGlobals (
		AvailObject object,
		A_Map value);

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
	 * @param chunk
	 */
	abstract void o_RemoveDependentChunk (
		AvailObject object,
		L2Chunk chunk);

	/**
	 * @param object
	 * @param aLoader
	 * @param afterRemoval
	 */
	abstract void o_RemoveFrom (
		AvailObject object,
		AvailLoader aLoader,
		Continuation0 afterRemoval);

	/**
	 * @param object
	 * @param definition
	 */
	abstract void o_RemoveDefinition (
		AvailObject object,
		A_Definition definition);

	/**
	 * @param object
	 * @param obsoleteRestriction
	 */
	abstract void o_RemoveGrammaticalRestriction (
		AvailObject object,
		A_GrammaticalRestriction obsoleteRestriction);

	/**
	 * @param object
	 * @param forwardDefinition
	 */
	abstract void o_ResolveForward (
		AvailObject object,
		A_BasicObject forwardDefinition);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	abstract A_Set o_SetIntersectionCanDestroy (
		AvailObject object,
		A_Set otherSet,
		boolean canDestroy);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	abstract A_Set o_SetMinusCanDestroy (
		AvailObject object,
		A_Set otherSet,
		boolean canDestroy);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	abstract A_Set o_SetUnionCanDestroy (
		AvailObject object,
		A_Set otherSet,
		boolean canDestroy);

	/**
	 * @param object
	 * @param newValue
	 * @throws VariableSetException
	 */
	abstract void o_SetValue (
			AvailObject object,
			A_BasicObject newValue)
		throws VariableSetException;

	/**
	 * @param object
	 * @param newValue
	 */
	abstract void o_SetValueNoCheck (
		AvailObject object,
		A_BasicObject newValue);

	/**
	 * @param object
	 * @param newElementObject
	 * @param canDestroy
	 * @return
	 */
	abstract A_Set o_SetWithElementCanDestroy (
		AvailObject object,
		A_BasicObject newElementObject,
		boolean canDestroy);

	/**
	 * @param object
	 * @param elementObjectToExclude
	 * @param canDestroy
	 * @return
	 */
	abstract A_Set o_SetWithoutElementCanDestroy (
		AvailObject object,
		A_BasicObject elementObjectToExclude,
		boolean canDestroy);

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
		A_BasicObject anObject);

	/**
	 * @param object
	 * @param chunk
	 * @param countdown
	 */
	abstract void o_SetStartingChunkAndReoptimizationCountdown (
		AvailObject object,
		L2Chunk chunk,
		long countdown);

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#subtractFromIntegerCanDestroy(A_Number, boolean)
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
	abstract A_Number o_SubtractFromInfinityCanDestroy (
		AvailObject object,
		Sign sign,
		boolean canDestroy);

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#subtractFromIntegerCanDestroy(A_Number, boolean)
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
	abstract A_Number o_SubtractFromIntegerCanDestroy (
		AvailObject object,
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * Multiply the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>
	 * Implementations may double-dispatch to {@link
	 * AvailObject#multiplyByIntegerCanDestroy(A_Number, boolean)
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
	abstract A_Number o_TimesCanDestroy (
		AvailObject object,
		A_Number aNumber,
		boolean canDestroy);

	/**
	 * @param object
	 * @param stringName
	 * @return
	 */
	abstract A_Set o_TrueNamesForStringName (
		AvailObject object,
		A_String stringName);

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
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	abstract A_Tuple o_TupleAtPuttingCanDestroy (
		AvailObject object,
		int index,
		A_BasicObject newValueObject,
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
	 * @return
	 */
	abstract A_Tuple o_TupleReverse (
		AvailObject object);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract A_Type o_TypeAtIndex (
		AvailObject object,
		int index);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract A_Type o_TypeIntersection (
		AvailObject object,
		A_Type another);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfContinuationType (
		AvailObject object,
		A_Type aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfCompiledCodeType (
		AvailObject object,
		A_Type aCompiledCodeType);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfFiberType (
		AvailObject object,
		A_Type aType);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfFunctionType (
		AvailObject object,
		A_Type aFunctionType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfIntegerRangeType (
		AvailObject object,
		A_Type anIntegerRangeType);

	/**
	 * @param object
	 * @param aListNodeType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfListNodeType (
		AvailObject object,
		A_Type aListNodeType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfMapType (
		AvailObject object,
		A_Type aMapType);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfObjectType (
		AvailObject object,
		A_Type anObjectType);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfParseNodeType (
		AvailObject object,
		A_Type aParseNodeType);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfPojoType (
		AvailObject object,
		A_Type aPojoType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfSetType (
		AvailObject object,
		A_Type aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfTupleType (
		AvailObject object,
		A_Type aTupleType);

	/**
	 * @param object
	 * @param aVariableType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfVariableType (
		AvailObject object,
		A_Type aVariableType);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract A_Type o_TypeUnion (
		AvailObject object,
		A_Type another);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfFiberType (AvailObject object, A_Type aType);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfFunctionType (
		AvailObject object,
		A_Type aFunctionType);

	/**
	 * @param object
	 * @param aVariableType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfVariableType (
		AvailObject object,
		A_Type aVariableType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfContinuationType (
		AvailObject object,
		A_Type aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfCompiledCodeType (
		AvailObject object,
		A_Type aCompiledCodeType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfIntegerRangeType (
		AvailObject object,
		A_Type anIntegerRangeType);

	/**
	 * @param object
	 * @param aListNodeType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfListNodeType (
		AvailObject object,
		A_Type aListNodeType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfMapType (
		AvailObject object,
		A_Type aMapType);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfObjectType (
		AvailObject object,
		A_Type anObjectType);

	/**
	 * @param object
	 * @param aPojoType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfPojoType (
		AvailObject object,
		A_Type aPojoType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfSetType (
		AvailObject object,
		A_Type aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfTupleType (
		AvailObject object,
		A_Type aTupleType);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	abstract A_Type o_UnionOfTypesAtThrough (
		AvailObject object,
		int startIndex,
		int endIndex);

	/**
	 * @param object
	 * @param value
	 */
	abstract void o_Value (
		AvailObject object,
		A_BasicObject value);

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
	abstract A_Set o_AsSet (AvailObject object);

	/**
	 * Construct a {@linkplain TupleDescriptor tuple} from the given {@linkplain
	 * SetDescriptor set}. Element ordering in the tuple will be arbitrary and
	 * unstable.
	 *
	 * @see AvailObject#asTuple()
	 * @param object A set.
	 * @return A tuple containing each element in the set.
	 */
	abstract A_Tuple o_AsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_BitsPerEntry (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Function o_BodyBlock (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_BodySignature (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_BasicObject o_BreakpointBlock (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Continuation o_Caller (AvailObject object);

	/**
	 * @param object
	 */
	abstract void o_ClearValue (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Function o_Function (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_FunctionType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_RawFunction o_Code (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_CodePoint (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_LazyComplete (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_ConstantBindings (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_ContentType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Continuation o_Continuation (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_CopyAsMutableObjectTuple (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_DefaultType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Continuation o_EnsureMutable (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract ExecutionState o_ExecutionState (AvailObject object);

	/**
	 * @param object
	 * @param module
	 * @param sampleArgsStack
	 */
	abstract void o_Expand (
		AvailObject object,
		A_Module module,
		List<A_Phrase> sampleArgsStack);

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
	abstract A_Map o_FieldMap (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_FieldTypeMap (AvailObject object);

	/**
	 * @param object
	 * @return
	 * @throws VariableGetException
	 */
	abstract AvailObject o_GetValue (AvailObject object)
		throws VariableGetException;

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
	abstract A_Tuple o_DefinitionsTuple (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_LazyIncomplete (AvailObject object);

	/**
	 * @param object
	 * @param continuation
	 */
	abstract void o_DecrementCountdownToReoptimize (
		AvailObject object,
		Continuation0 continuation);

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
	abstract boolean o_IsSupertypeOfBottom (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_KeysAsSet (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_KeyType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract L2Chunk o_LevelTwoChunk (AvailObject object);

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
	abstract A_Number o_LowerBound (AvailObject object);

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
	abstract A_Atom o_Message (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_MessageParts (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_MethodDefinitions (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_String o_AtomName (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_ImportedNames (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_NewNames (AvailObject object);

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
	abstract A_Tuple o_Nybbles (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_BasicObject o_Parent (AvailObject object);

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
	abstract int o_Priority (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_PrivateNames (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_FiberGlobals (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_GrammaticalRestrictions (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_ReturnType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_SetSize (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_SizeRange (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_LazyActions (AvailObject object);

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
	abstract L2Chunk o_StartingChunk (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_String o_String (AvailObject object);

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
	abstract A_Tuple o_TypeTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Number o_UpperBound (AvailObject object);

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
	abstract A_Tuple o_ValuesAsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_ValueType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_VariableBindings (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_VisibleNames (AvailObject object);

	/**
	 * Answer whether the arguments, both {@linkplain AvailObject objects}, are
	 * equal in value.
	 *
	 * @param object The receiver.
	 * @param another The second object used in the comparison.
	 * @return {@code true} if the two objects are of equal value, {@code false}
	 *         otherwise.
	 * @see AvailObject#equals(A_BasicObject)
	 */
	abstract boolean o_Equals (
		AvailObject object,
		A_BasicObject another);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain TupleDescriptor tuple}, are equal in value.
	 *
	 * @param object The receiver.
	 * @param aTuple The tuple used in the comparison.
	 * @return {@code true} if the receiver is a tuple and of value equal to the
	 *         argument, {@code false} otherwise.
	 * @see AvailObject#equalsAnyTuple(A_Tuple)
	 */
	abstract boolean o_EqualsAnyTuple (
		AvailObject object,
		A_Tuple aTuple);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain ByteStringDescriptor byte string}, are equal in value.
	 *
	 * @param object The receiver.
	 * @param aByteString The byte string used in the comparison.
	 * @return {@code true} if the receiver is a byte string and of value equal
	 *         to the argument, {@code false} otherwise.
	 * @see AvailObject#equalsByteString(A_String)
	 */
	abstract boolean o_EqualsByteString (
		AvailObject object,
		A_String aByteString);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object}, and a
	 * {@linkplain ByteTupleDescriptor byte tuple}, are equal in value.
	 *
	 * @param object The receiver.
	 * @param aByteTuple The byte tuple used in the comparison.
	 * @return {@code true} if the receiver is a byte tuple and of value equal
	 *         to the argument, {@code false} otherwise.
	 * @see AvailObject#equalsByteString(A_String)
	 */
	abstract boolean o_EqualsByteTuple (
		AvailObject object,
		A_Tuple aByteTuple);

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
	 * {@linkplain FiberTypeDescriptor fiber type}, are equal in value.
	 *
	 * @param object The receiver.
	 * @param aType A fiber type.
	 * @return {@code true} if the receiver is a fiber type and of value equal
	 *         to the argument, {@code false} otherwise.
	 * @see AvailObject#equalsFiberType(A_Type)
	 */
	abstract boolean o_EqualsFiberType (AvailObject object, A_Type aType);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain FunctionDescriptor function}, are equal in value.
	 *
	 * @param object The receiver.
	 * @param aFunction The function used in the comparison.
	 * @return {@code true} if the receiver is a function and of value equal to
	 *         the argument, {@code false} otherwise.
	 * @see AvailObject#equalsFunction(A_Function)
	 */
	abstract boolean o_EqualsFunction (
		AvailObject object,
		A_Function aFunction);

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
	 * @see AvailObject#equalsFunctionType(A_Type)
	 */
	abstract boolean o_EqualsFunctionType (
		AvailObject object,
		A_Type aFunctionType);

	/**
	 * Answer whether the arguments, an {@linkplain AvailObject object} and a
	 * {@linkplain CompiledCodeDescriptor compiled code}, are equal.
	 *
	 * @param object The receiver.
	 * @param aCompiledCode The compiled code used in the comparison.
	 * @return {@code true} if the receiver is a compiled code and of value
	 *         equal to the argument, {@code false} otherwise.
	 * @see AvailObject#equalsCompiledCode(A_RawFunction)
	 */
	abstract boolean o_EqualsCompiledCode (
		AvailObject object,
		A_RawFunction aCompiledCode);

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
		A_Type aType);

	/**
	 * @param object
	 * @param aContinuation
	 * @return
	 */
	abstract boolean o_EqualsContinuation (
		AvailObject object,
		A_Continuation aContinuation);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	abstract boolean o_EqualsContinuationType (
		AvailObject object,
		A_Type aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	abstract boolean o_EqualsCompiledCodeType (
		AvailObject object,
		A_Type aCompiledCodeType);

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
	 * @see A_Number#equalsInfinity(Sign)
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
		A_Number anAvailInteger);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract boolean o_EqualsIntegerRangeType (
		AvailObject object,
		A_Type another);

	/**
	 * @param object
	 * @param aMap
	 * @return
	 */
	abstract boolean o_EqualsMap (
		AvailObject object,
		A_Map aMap);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	abstract boolean o_EqualsMapType (
		AvailObject object,
		A_Type aMapType);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	abstract boolean o_EqualsNybbleTuple (
		AvailObject object,
		A_Tuple aTuple);

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
		A_Tuple aTuple);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract boolean o_EqualsParseNodeType (
		AvailObject object,
		A_Type aParseNodeType);

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
		final A_Type aPrimitiveType);

	/**
	 * @param object
	 * @param otherRawPojo
	 * @param otherJavaObject
	 * @return
	 */
	abstract boolean o_EqualsRawPojoFor (
		AvailObject object,
		AvailObject otherRawPojo,
		@Nullable Object otherJavaObject);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */

	abstract boolean o_EqualsReverseTuple (
		AvailObject object,
		A_Tuple aTuple);

	/**
	 * @param object
	 * @param aSet
	 * @return
	 */
	abstract boolean o_EqualsSet (
		AvailObject object,
		A_Set aSet);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	abstract boolean o_EqualsSetType (
		AvailObject object,
		A_Type aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	abstract boolean o_EqualsTupleType (
		AvailObject object,
		A_Type aTupleType);

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	abstract boolean o_EqualsTwoByteString (
		AvailObject object,
		A_String aString);

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
		A_BasicObject anotherObject);

	/**
	 * Given two objects that are known to be equal, the second of which is in
	 * the form of a tuple type, is the first one in a better form than the
	 * second one?
	 *
	 * @param object
	 *            The first object.
	 * @return
	 *            Whether the first object is a better representation to keep.
	 */
	abstract int o_RepresentationCostOfTupleType (
		AvailObject object);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	abstract boolean o_IsInstanceOfKind (
		AvailObject object,
		A_Type aType);

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
	abstract A_Type o_Kind (AvailObject object);

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
	abstract A_BasicObject o_SetBinAddingElementHashLevelCanDestroy (
		AvailObject object,
		A_BasicObject elementObject,
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
		A_BasicObject elementObject,
		int elementObjectHash);

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	abstract AvailObject o_BinRemoveElementHashLevelCanDestroy (
		AvailObject object,
		A_BasicObject elementObject,
		int elementObjectHash,
		byte myLevel,
		boolean canDestroy);

	/**
	 * @param object
	 * @param potentialSuperset
	 * @return
	 */
	abstract boolean o_IsBinSubsetOf (
		AvailObject object,
		A_Set potentialSuperset);

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
	abstract boolean o_IsIntegerIntervalTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsSmallIntegerIntervalTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsRepeatedElementTuple (AvailObject object);

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
	abstract A_Tuple o_ParsingInstructions (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Phrase o_Expression (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Phrase o_Variable (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_ArgumentsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_StatementsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_ResultType (AvailObject object);

	/**
	 * @param object
	 * @param neededVariables
	 */
	abstract void o_NeededVariables (
		AvailObject object,
		A_Tuple neededVariables);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_NeededVariables (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @Nullable Primitive o_Primitive (AvailObject object);

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
	abstract A_Token o_Token (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_MarkerValue (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Bundle o_Bundle (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_ExpressionsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Phrase o_Declaration (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_ExpressionType (AvailObject object);

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
		Transformer1<A_Phrase, A_Phrase> aBlock);

	/**
	 * Visit my child parse nodes with aBlock.
	 *
	 * @param object
	 * @param aBlock
	 */
	abstract void o_ChildrenDo (
		AvailObject object,
		Continuation1<A_Phrase> aBlock);

	/**
	 * @param object
	 * @param parent
	 */
	abstract void o_ValidateLocally (
		AvailObject object,
		@Nullable A_Phrase parent);

	/**
	 * @param object
	 * @param module
	 * @return
	 */
	abstract A_RawFunction o_GenerateInModule (
		AvailObject object,
		A_Module module);

	/**
	 * @param object
	 * @param newParseNode
	 * @return
	 */
	abstract A_Phrase o_CopyWith (
		AvailObject object,
		A_Phrase newParseNode);

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
	abstract A_Phrase o_CopyMutableParseNode (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_BinUnionKind (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Phrase o_OutputParseNode (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Atom o_ApparentSendName (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_Statements (
		AvailObject object);

	/**
	 * @param object
	 * @param accumulatedStatements
	 */
	abstract void o_FlattenStatementsInto (
		AvailObject object,
		List<A_Phrase> accumulatedStatements);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_LineNumber (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_AllParsingPlans (AvailObject object);

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
	abstract A_Set o_DeclaredExceptions (
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
	abstract A_Type o_ArgsTupleType (AvailObject object);

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
	abstract A_Set o_Instances (
		AvailObject object);

	/**
	 * @param object
	 * @param aSet
	 * @return
	 */
	boolean o_EqualsEnumerationWithSet (
		final AvailObject object,
		final A_Set aSet)
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
		A_Type aType);

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
	abstract A_Type o_ComputeSuperkind (
		AvailObject object);

	/**
	 * @param object
	 * @param key
	 * @param value
	 */
	abstract void o_SetAtomProperty (
		AvailObject object,
		A_Atom key,
		A_BasicObject value);

	/**
	 * @param object
	 * @param key
	 * @return
	 */
	abstract AvailObject o_GetAtomProperty (
		AvailObject object,
		A_Atom key);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	abstract boolean o_EqualsEnumerationType (
		AvailObject object,
		A_BasicObject another);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_ReadType (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_WriteType (
		AvailObject object);

	/**
	 * @param object
	 * @param versionStrings
	 */
	abstract void o_Versions (
		AvailObject object,
		A_Set versionStrings);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_Versions (AvailObject object);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfParseNodeType (
		AvailObject object,
		A_Type aParseNodeType);

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
	abstract void o_AddSemanticRestriction (
		AvailObject object,
		A_SemanticRestriction restrictionSignature);

	/**
	 * @param object
	 * @param restriction
	 */
	abstract void o_RemoveSemanticRestriction (
		AvailObject object,
		A_SemanticRestriction restriction);

	/**
	 * Return the {@linkplain MethodDescriptor method}'s
	 * {@linkplain TupleDescriptor tuple} of {@linkplain FunctionDescriptor
	 * functions} that statically restrict call sites by argument type.
	 *
	 * @param object The method.
	 * @return
	 */
	abstract A_Set o_SemanticRestrictions (
		AvailObject object);

	/**
	 * @param object
	 * @param typeTuple
	 */
	abstract void o_AddSealedArgumentsType (
		AvailObject object,
		A_Tuple typeTuple);

	/**
	 * @param object
	 * @param typeTuple
	 */
	abstract void o_RemoveSealedArgumentsType (
		AvailObject object,
		A_Tuple typeTuple);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_SealedArgumentsTypesTuple (
		AvailObject object);

	/**
	 * @param object
	 * @param semanticRestriction
	 */
	abstract void o_ModuleAddSemanticRestriction (
		AvailObject object,
		A_SemanticRestriction semanticRestriction);

	/**
	 * @param object
	 * @param name
	 * @param constantBinding
	 */
	abstract void o_AddConstantBinding (
		AvailObject object,
		A_String name,
		A_Variable constantBinding);

	/**
	 * @param object
	 * @param name
	 * @param variableBinding
	 */
	abstract void o_AddVariableBinding (
		AvailObject object,
		A_String name,
		A_Variable variableBinding);

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
	abstract A_Type o_PojoSelfType (
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
	 * @param another
	 * @return
	 */
	abstract Order o_NumericCompare (
		final AvailObject object,
		final A_Number another);

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
		final A_Number anInteger);

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
	abstract A_Number o_AddToDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract A_Number o_AddToFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		boolean canDestroy);

	/**
	 * @param object
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	abstract A_Number o_SubtractFromDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract A_Number o_SubtractFromFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	abstract A_Number o_MultiplyByDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract A_Number o_MultiplyByFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	abstract A_Number o_DivideIntoDoubleCanDestroy (
		final AvailObject object,
		final A_Number doubleObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	abstract A_Number o_DivideIntoFloatCanDestroy (
		final AvailObject object,
		final A_Number floatObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_LazyPrefilterMap (
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
	abstract A_BasicObject o_MapBinAtHashPutLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash,
		final A_BasicObject value,
		final byte myLevel,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param key
	 * @param keyHash
	 * @param canDestroy
	 * @return
	 */
	abstract A_BasicObject o_MapBinRemoveKeyHashCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash,
		final boolean canDestroy);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_MapBinKeyUnionKind (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_MapBinValueUnionKind (
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
		final A_BasicObject key,
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
	abstract A_Module o_IssuingModule (
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
		final A_Type aPojoType);

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
	abstract A_Type o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType);

	/**
	 * @param object
	 * @param anUnfusedPojoType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType);

	/**
	 * @param object
	 * @param aFusedPojoType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType);

	/**
	 * @param object
	 * @param anUnfusedPojoType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType);

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
	abstract @Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_TypeVariables (
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
	 * @param otherEqualityRawPojo
	 * @param otherJavaObject
	 * @return
	 */
	abstract boolean o_EqualsEqualityRawPojo (
		final AvailObject object,
		final AvailObject otherEqualityRawPojo,
		final @Nullable Object otherJavaObject);

	/**
	 * @param object
	 * @return
	 */
	abstract @Nullable Object o_JavaObject (final AvailObject object);

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
	abstract A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_LazyIncompleteCaseInsensitive (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_String o_LowerCaseString (
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
	abstract A_Number o_InstanceCount (
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
	abstract A_Tuple o_FieldTypeTuple (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_FieldTuple (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Phrase o_ArgumentsListNode (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_LiteralType (
		final AvailObject object);

	/**
	 * @param object
	 * @param aLiteralTokenType
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType);

	/**
	 * @param object
	 * @param aLiteralTokenType
	 * @return
	 */
	abstract A_Type o_TypeUnionOfLiteralTokenType(
		 final AvailObject object,
		 final A_Type aLiteralTokenType);

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
		final A_Type aLiteralTokenType);

	/**
	 * @param object
	 * @param aLiteralTokenType
	 * @return
	 */
	abstract boolean o_EqualsLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	abstract boolean o_EqualsObjectType (
		final AvailObject object,
		final A_Type anObjectType);

	/**
	 * @param object
	 * @param aToken
	 * @return
	 */
	abstract boolean o_EqualsToken (
		final AvailObject object,
		final A_Token aToken);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	abstract A_Number o_BitwiseAnd (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	abstract A_Number o_BitwiseOr (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	abstract A_Number o_BitwiseXor (
		final AvailObject object,
		final A_Number anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param methodName
	 * @param argumentTypes
	 */
	abstract void o_AddSeal (
		final AvailObject object,
		final A_Atom methodName,
		final A_Tuple argumentTypes);

	/**
	 * @param object
	 * @param methodName
	 */
	abstract void o_SetMethodName (
		final AvailObject object,
		final A_String methodName);

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
	abstract A_Module o_Module (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_String o_MethodName (
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
		final A_Type kind);

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
	 * @param shiftFactor
	 * @param truncationBits
	 * @param canDestroy
	 * @return
	 */
	abstract A_Number o_BitShiftLeftTruncatingToBits (
		final AvailObject object,
		final A_Number shiftFactor,
		final A_Number truncationBits,
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
	abstract A_Number o_BitShift (
		final AvailObject object,
		final A_Number shiftFactor,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param aParseNode
	 * @return
	 */
	abstract boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Phrase o_StripMacro (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Method o_DefinitionMethod (
		final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_PrefixFunctions (
		final AvailObject object);

	/**
	 * @param object
	 * @param aByteArrayTuple
	 * @return
	 */
	abstract boolean o_EqualsByteArrayTuple (
		final AvailObject object,
		final A_Tuple aByteArrayTuple);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteArrayTuple
	 * @param startIndex2
	 * @return
	 */
	abstract boolean o_CompareFromToWithByteArrayTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteArrayTuple,
		final int startIndex2);

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
	 * @param bundle
	 */
	abstract void o_FlushForNewOrChangedBundle (
		final AvailObject object,
		final A_Bundle bundle);

	/**
	 * @param object
	 * @param critical
	 */
	abstract void o_Lock (
		final AvailObject object,
		final Continuation0 critical);

	/**
	 * @param object
	 * @return
	 */
	abstract A_String o_ModuleName (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Method o_BundleMethod (final AvailObject object);

	/**
	 * @param object
	 * @param newValue
	 * @return
	 * @throws VariableGetException
	 * @throws VariableSetException
	 */
	abstract AvailObject o_GetAndSetValue (
			AvailObject object,
			A_BasicObject newValue)
		throws VariableGetException, VariableSetException;

	/**
	 * @param object
	 * @param reference
	 * @param newValue
	 * @return
	 * @throws VariableGetException
	 * @throws VariableSetException
	 */
	abstract boolean o_CompareAndSwapValues (
			AvailObject object,
			A_BasicObject reference,
			A_BasicObject newValue)
		throws VariableGetException, VariableSetException;

	/**
	 * @param object
	 * @param addend
	 * @return
	 * @throws VariableGetException
	 * @throws VariableSetException
	 */
	abstract A_Number o_FetchAndAddValue (
			final AvailObject object,
			final A_Number addend)
		throws VariableGetException, VariableSetException;

	/**
	 * @param object
	 * @return
	 */
	abstract Continuation1<Throwable> o_FailureContinuation (
		AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract Continuation1<AvailObject> o_ResultContinuation (
		AvailObject object);

	/**
	 * @param object
	 * @param continuation
	 */
	abstract void o_ResultContinuation (
		AvailObject object,
		Continuation1<AvailObject> continuation);

	/**
	 * @param object
	 * @param continuation
	 */
	abstract void o_FailureContinuation (
		AvailObject object,
		Continuation1<Throwable> continuation);

	/**
	 * @param object
	 * @return
	 */
	abstract @Nullable AvailLoader o_AvailLoader (AvailObject object);

	/**
	 * @param object
	 * @param loader
	 */
	abstract void o_AvailLoader (
		AvailObject object,
		@Nullable AvailLoader loader);

	/**
	 * @param object
	 * @param flag
	 * @return
	 */
	abstract boolean o_InterruptRequestFlag (
		AvailObject object,
		InterruptRequestFlag flag);

	/**
	 * @param object
	 * @param flag
	 * @return
	 */
	abstract boolean o_GetAndClearInterruptRequestFlag (
		AvailObject object,
		InterruptRequestFlag flag);

	/**
	 * @param object
	 * @param flag
	 * @param newValue
	 * @return
	 */
	abstract boolean o_GetAndSetSynchronizationFlag (
		AvailObject object,
		SynchronizationFlag flag,
		boolean newValue);

	/**
	 * @param object
	 * @return
	 */
	abstract AvailObject o_FiberResult (final AvailObject object);

	/**
	 * @param object
	 * @param result
	 */
	abstract void o_FiberResult (AvailObject object, A_BasicObject result);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_JoiningFibers (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract @Nullable TimerTask o_WakeupTask (AvailObject object);

	/**
	 * @param object
	 * @param task
	 */
	abstract void o_WakeupTask (AvailObject object, @Nullable TimerTask task);

	/**
	 * @param object
	 * @param joiners
	 */
	abstract void o_JoiningFibers (AvailObject object, A_Set joiners);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_HeritableFiberGlobals (AvailObject object);

	/**
	 * @param object
	 * @param globals
	 */
	abstract void o_HeritableFiberGlobals (
		AvailObject object,
		A_Map globals);

	/**
	 * @param object
	 * @param flag
	 * @return
	 */
	abstract boolean o_GeneralFlag (AvailObject object, GeneralFlag flag);

	/**
	 * @param object
	 * @param flag
	 */
	abstract void o_SetGeneralFlag (AvailObject object, GeneralFlag flag);

	/**
	 * @param object
	 * @param flag
	 */
	abstract void o_ClearGeneralFlag (AvailObject object, GeneralFlag flag);

	/**
	 * @param object
	 * @param aByteBufferTuple
	 * @return
	 */
	abstract boolean o_EqualsByteBufferTuple (
		AvailObject object,
		A_Tuple aByteBufferTuple);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteBufferTuple
	 * @param startIndex2
	 * @return
	 */
	abstract boolean o_CompareFromToWithByteBufferTupleStartingAt (
		AvailObject object,
		int startIndex1,
		int endIndex1,
		A_Tuple aByteBufferTuple,
		int startIndex2);

	/**
	 * @param object
	 * @return
	 */
	abstract ByteBuffer o_ByteBuffer (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsByteBufferTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_String o_FiberName (AvailObject object);

	/**
	 * @param object
	 * @param generator
	 */
	abstract void o_FiberNameGenerator (
		AvailObject object,
		Generator<A_String> generator);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_Bundles (AvailObject object);

	/**
	 * @param object
	 * @param bundle
	 */
	abstract void o_MethodAddBundle (AvailObject object, A_Bundle bundle);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Module o_DefinitionModule (AvailObject object);

	/**
	 * @param object
	 * @return
	 * @throws MalformedMessageException
	 */
	abstract A_Bundle o_BundleOrCreate (AvailObject object)
		throws MalformedMessageException;

	/**
	 * @param object
	 * @return
	 */
	abstract A_Bundle o_BundleOrNil (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Map o_EntryPoints (AvailObject object);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	abstract void o_AddEntryPoint (
		AvailObject object,
		A_String stringName,
		A_Atom trueName);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_AllAncestors (final AvailObject object);

	/**
	 * @param object
	 * @param moreAncestors
	 */
	abstract void o_AddAncestors (AvailObject object, A_Set moreAncestors);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_ArgumentRestrictionSets (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Bundle o_RestrictedBundle (AvailObject object);

	/**
	 * @param object
	 * @param pc
	 * @param stackp
	 */
	abstract void o_AdjustPcAndStackp (AvailObject object, int pc, int stackp);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_TreeTupleLevel (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_ChildCount (AvailObject object);

	/**
	 * @param object
	 * @param childIndex
	 * @return
	 */
	abstract A_Tuple o_ChildAt (AvailObject object, int childIndex);

	/**
	 * @param object
	 * @param otherTuple
	 * @param canDestroy
	 * @return
	 */
	abstract A_Tuple o_ConcatenateWith (
		AvailObject object,
		A_Tuple otherTuple,
		boolean canDestroy);

	/**
	 * @param object
	 * @param newFirst
	 * @return
	 */
	abstract A_Tuple o_ReplaceFirstChild (
		AvailObject object,
		A_Tuple newFirst);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsByteString (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsTwoByteString (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_SkipReturnFlag (AvailObject object);

	/**
	 * @param object
	 * @param anIntegerIntervalTuple
	 * @return
	 */
	abstract boolean o_EqualsIntegerIntervalTuple (
		AvailObject object,
		A_Tuple anIntegerIntervalTuple);

	/**
	 * @param object
	 * @param aSmallIntegerIntervalTuple
	 * @return
	 */
	abstract boolean o_EqualsSmallIntegerIntervalTuple (
		AvailObject object,
		A_Tuple aSmallIntegerIntervalTuple);

	/**
	 * @param object
	 * @param aRepeatedElementTuple
	 * @return
	 */
	abstract boolean o_EqualsRepeatedElementTuple (
		AvailObject object,
		A_Tuple aRepeatedElementTuple);

	/**
	 * @param object
	 * @param key
	 * @param reactor
	 * @return
	 */
	abstract A_Variable o_AddWriteReactor (
		AvailObject object,
		A_Atom key,
		VariableAccessReactor reactor);

	/**
	 * @param object
	 * @param key
	 * @throws AvailException
	 */
	abstract void o_RemoveWriteReactor (AvailObject object, A_Atom key)
		throws AvailException;

	/**
	 * @param object
	 * @param flag
	 * @return
	 */
	abstract boolean o_TraceFlag (AvailObject object, TraceFlag flag);

	/**
	 * @param object
	 * @param flag
	 */
	abstract void o_SetTraceFlag (AvailObject object, TraceFlag flag);

	/**
	 * @param object
	 * @param flag
	 */
	abstract void o_ClearTraceFlag (AvailObject object, TraceFlag flag);

	/**
	 * @param object
	 * @param var
	 * @param wasRead
	 */
	abstract void o_RecordVariableAccess (
		AvailObject object,
		A_Variable var,
		boolean wasRead);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_VariablesReadBeforeWritten (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_VariablesWritten (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_ValidWriteReactorFunctions (AvailObject object);

	/**
	 * @param object
	 * @param newCaller
	 * @return
	 */
	abstract A_Continuation o_ReplacingCaller (
		AvailObject object,
		A_Continuation newCaller);

	/**
	 * @param object
	 * @param whenReified
	 */
	abstract void o_WhenContinuationIsAvailableDo (
		AvailObject object,
		Continuation1<A_Continuation> whenReified);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_GetAndClearReificationWaiters (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsBottom (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsTop (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsAtomSpecial (AvailObject object);

	/**
	 * @param object
	 * @param primitiveNumber
	 */
	abstract void o_RecordLatestPrimitive (
		AvailObject object,
		short primitiveNumber);

	/**
	 * @param object
	 * @param trueNames
	 */
	abstract void o_AddPrivateNames (AvailObject object, A_Set trueNames);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_HasValue (AvailObject object);

	/**
	 * @param object
	 * @param unloadFunction
	 */
	abstract void o_AddUnloadFunction (
		AvailObject object,
		A_Function unloadFunction);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_ExportedNames (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_String o_LeadingWhitespace (AvailObject object);
	/**
	 * @param object
	 * @return
	 */
	abstract A_String o_TrailingWhitespace (AvailObject object);

	/**
	 * @param object
	 * @param trailingWhitespace
	 */
	abstract void o_TrailingWhitespace (
		AvailObject object,
		A_String trailingWhitespace);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsInitializedWriteOnceVariable (AvailObject object);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @param outputByteBuffer
	 */
	abstract void o_TransferIntoByteBuffer (
		AvailObject object,
		int startIndex,
		int endIndex,
		ByteBuffer outputByteBuffer);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @param type
	 * @return
	 */
	abstract boolean o_TupleElementsInRangeAreInstancesOf (
		AvailObject object,
		int startIndex,
		int endIndex,
		A_Type type);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsNumericallyIntegral (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract TextInterface o_TextInterface (AvailObject object);

	/**
	 * @param object
	 * @param textInterface
	 */
	abstract void o_TextInterface (
		AvailObject object,
		TextInterface textInterface);

	/**
	 * @param object
	 * @param writer
	 */
	abstract void o_WriteTo (AvailObject object, JSONWriter writer);

	/**
	 * @param object
	 * @param writer
	 */
	abstract void o_WriteSummaryTo (AvailObject object, JSONWriter writer);

	/**
	 * @param object
	 * @param primitiveTypeEnum
	 * @return
	 */
	abstract A_Type o_TypeIntersectionOfPrimitiveTypeEnum (
		AvailObject object,
		Types primitiveTypeEnum);

	/**
	 * @param object
	 * @param primitiveTypeEnum
	 * @return
	 */
	abstract A_Type o_TypeUnionOfPrimitiveTypeEnum (
		AvailObject object,
		Types primitiveTypeEnum);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	abstract A_Tuple o_TupleOfTypesFromTo (
		AvailObject object,
		int startIndex,
		int endIndex);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Phrase o_List (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_Permutation (AvailObject object);

	/**
	 * @param object
	 * @param codeGenerator
	 */
	abstract void o_EmitAllValuesOn (
		AvailObject object,
		AvailCodeGenerator codeGenerator);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_SuperUnionType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_HasSuperCast (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_MacroDefinitionsTuple (AvailObject object);

	/**
	 * @param object
	 * @param argumentPhraseTuple
	 * @param errorCode
	 * @return
	 */
	abstract A_Definition o_LookupMacroByPhraseTuple (
		AvailObject object,
		A_Tuple argumentPhraseTuple)
	throws MethodDefinitionException;

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	abstract A_Phrase o_ExpressionAt (AvailObject object, int index);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_ExpressionsSize (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_ParsingPc (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_IsMacroSubstitutionNode (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract MessageSplitter o_MessageSplitter (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract int o_TokenIndex (AvailObject object);

	/**
	 * @param object
	 * @param continuation
	 */
	abstract void o_StatementsDo (
		AvailObject object,
		Continuation1<A_Phrase> continuation);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Phrase o_MacroOriginalSendNode (AvailObject object);

	/**
	 * @param object
	 * @param theInt
	 * @return
	 */
	abstract boolean o_EqualsInt (
		final AvailObject object,
		final int theInt);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Tuple o_Tokens (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Bundle o_ChooseBundle (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract boolean o_ValueWasStablyComputed (AvailObject object);

	/**
	 * @param object
	 * @param wasStablyComputed
	 */
	abstract void o_ValueWasStablyComputed (
		AvailObject object,
		boolean wasStablyComputed);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Definition o_Definition (AvailObject object);

	/**
	 * @param object
	 * @param pc
	 * @return
	 */
	abstract String o_NameHighlightingPc (AvailObject object, int pc);

	/**
	 * @param object
	 * @param otherSet
	 * @return
	 */
	abstract boolean o_SetIntersects (AvailObject object, A_Set otherSet);

	/**
	 * @param object
	 * @param plan
	 */
	abstract void o_RemoveDefinitionParsingPlan (
		AvailObject object,
		A_DefinitionParsingPlan plan);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Set o_DefinitionParsingPlans (AvailObject object);

	/**
	 * @param object
	 * @param aListNodeType
	 * @return
	 */
	abstract boolean o_EqualsListNodeType (
		AvailObject object,
		A_Type aListNodeType);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_SubexpressionsTupleType (AvailObject object);

	/**
	 * @param object
	 * @param aListNodeType
	 * @return
	 */
	abstract boolean o_IsSupertypeOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType);

	/**
	 * @param object
	 * @return
	 */
	abstract long o_UniqueId (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	abstract A_BasicObject o_LazyTypeFilterTreePojo (final AvailObject object);

	/**
	 * @param object
	 * @param plan
	 */
	abstract void o_AddPlan (
		final AvailObject object,
		final A_DefinitionParsingPlan plan);

	/**
	 * @param object
	 * @return
	 */
	abstract A_Type o_ParsingSignature (final AvailObject object);
}
