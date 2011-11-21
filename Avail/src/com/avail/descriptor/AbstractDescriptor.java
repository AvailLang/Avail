/**
 * com.avail.descriptor/AbstractDescriptor.java Copyright (c) 2010, Mark van
 * Gulik. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

import static com.avail.descriptor.AvailObject.error;
import java.lang.reflect.Field;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.node.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.compiler.node.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.compiler.scanning.TokenDescriptor;
import com.avail.descriptor.ProcessDescriptor.ExecutionState;
import com.avail.exceptions.*;
import com.avail.exceptions.ArithmeticException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Interpreter;
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
	public final short id ()
	{
		return myId;
	}

	/**
	 * A flag indicating whether instances of me can be modified in place.
	 * Generally, as soon as there are two references from {@link AvailObject
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
	 * Construct a new {@link AbstractDescriptor descriptor}.
	 *
	 * @param isMutable Does the {@linkplain AbstractDescriptor descriptor}
	 *                  represent a mutable object?
	 */
	@SuppressWarnings("unchecked")
	protected AbstractDescriptor (final boolean isMutable)
	{
		this.myId = (short) allDescriptors.size();
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
	 * Answer whether the field at the given offset is allowed to be modified
	 * even in an immutable object.
	 *
	 * @param e The byte offset of the field to check.
	 * @return Whether the specified field can be written even in an immutable
	 *         object.
	 */
	public boolean allowsImmutableToMutableReferenceInField (final Enum<?> e)
	{
		return false;
	}

	/**
	 * Answer how many levels of printing to allow before elision.
	 *
	 * @return The number of levels.
	 */
	public int maximumIndent ()
	{
		return 12;
	}

	/**
	 * Ensure that the specified field is writable.
	 *
	 * @param e An {@code enum} value whose ordinal is the field position.
	 */
	public final void checkWriteForField (final @NotNull Enum<?> e)
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
		return create(0);
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
	public void printObjectOnAvoidingIndent (
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

		final Class<Descriptor> cls = (Class<Descriptor>) this.getClass();
		final ClassLoader loader = cls.getClassLoader();
		Class<Enum<?>> enumClass;
		Enum<?>[] instances;

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

		for (int i = 1, limit = object.integerSlotsCount(); i <= limit; i++)
		{
			builder.append('\n');
			for (int tab = 0; tab < indent; tab++)
			{
				builder.append('\t');
			}
			final int ordinal = Math.min(i, instances.length) - 1;
			final Enum<?> slot = instances[ordinal];
			final String slotName = slot.name();
			int value;
			if (slotName.charAt(slotName.length() - 1) == '_')
			{
				final int subscript = i - instances.length + 1;
				value = object.integerSlotAt(slot, subscript);
				builder.append(slotName, 0, slotName.length() - 1);
				builder.append('[');
				builder.append(subscript);
				builder.append("]");
			}
			else
			{
				value = object.integerSlot(slot);
				builder.append(slotName);
			}
			builder.append(" = ");
			builder.append(value);
			try
			{
				final Field slotMirror = slot.getClass().getField(slot.name());
				final EnumField enumAnnotation =
					slotMirror.getAnnotation(EnumField.class);
				final BitFields bitFieldsAnnotation =
					slotMirror.getAnnotation(BitFields.class);
				if (enumAnnotation != null)
				{
					final Class<? extends Enum<?>> describingClass =
						enumAnnotation.describedBy();
					final Enum<?>[] allValues =
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
								"(enum out of range: 0x%08X)",
								value & 0xFFFFFFFFL));
					}
				}
				else if (bitFieldsAnnotation != null)
				{
					// Show each bit field.
					final Class<?> describingClass =
						bitFieldsAnnotation.describedBy();
					final Field[] allSubfields =
						describingClass.getDeclaredFields();
					builder.append(" (");
					for (
						int subfieldIndex = 0;
						subfieldIndex < allSubfields.length;
						subfieldIndex++)
					{
						if (subfieldIndex > 0)
						{
							builder.append(", ");
						}
						final Field subfield = allSubfields[subfieldIndex];
						builder.append(subfield.getName());
						builder.append("=");
						BitField bitField;
						bitField = (BitField)subfield.get(null);
						final int subfieldValue = object.bitSlot(slot, bitField);
						builder.append(subfieldValue);
					}
					builder.append(")");
				}
				else
				{
					builder.append(
						new Formatter().format(
							" = 0x%08X",
							value & 0xFFFFFFFFL));
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
		instances = enumClass != null
		? enumClass.getEnumConstants()
				: new Enum<?>[0];

		for (int i = 1, limit = object.objectSlotsCount(); i <= limit; i++)
		{
			builder.append('\n');
			for (int tab = 0; tab < indent; tab++)
			{
				builder.append('\t');
			}
			final int ordinal = Math.min(i, instances.length) - 1;
			final Enum<?> slot = instances[ordinal];
			final String slotName = slot.name();
			if (slotName.charAt(slotName.length() - 1) == '_')
			{
				final int subscript = i - instances.length + 1;
				builder.append(slotName, 0, slotName.length() - 1);
				builder.append('[');
				builder.append(subscript);
				builder.append("] = ");
				object.objectSlotAt(slot, subscript).printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
			else
			{
				builder.append(slotName);
				builder.append(" = ");
				object.objectSlot(slot).printOnAvoidingIndent(
					builder,
					recursionList,
					indent + 1);
			}
		}
	}

	/**
	 * Extract the {@link BitField} that is specified as an annotation of the
	 * member of the given class with the given name.  This uses reflection so
	 * it might be a bit slow.  It's recommended that the resulting BitField be
	 * stored somewhere statically, preferably as the value of the field
	 * itself.
	 *
	 * @param theClass
	 *            The class which defines one or more {@code BitField}s as
	 *            static members having the {@link BitField @BitField}
	 *            annotation.
	 * @param fieldName
	 *            The name of the static member for which to extract the {@code
	 *            BitField} annotation.
	 * @return
	 *            The {@code BitField} that the specified static member was
	 *            annotated with.
	 */
	public static BitField bitField (
		final Class<?> theClass,
		final String fieldName)
	{
		BitField bitField;
		try
		{
			final Field field = theClass.getDeclaredField(fieldName);
			bitField = field.getAnnotation(BitField.class);
		}
		catch (final NoSuchFieldException e)
		{
			throw new RuntimeException(e);
		}
		assert bitField.shift() >= 0;
		assert bitField.shift() <= 31;
		assert bitField.bits() > 0;
		assert bitField.shift() + bitField.bits() <= 32;
		return bitField;
	}



	/**
	 * @param object
	 * @param functionType
	 * @return
	 */
	public abstract boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final AvailObject functionType);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @param numArgs
	 * @return
	 */
	public abstract boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final AvailObject continuation,
		final int stackp,
		final int numArgs);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param argValues
	 * @return
	 */
	public abstract boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final List<AvailObject> argValues);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final AvailObject argTypes);

	/**
	 * @param object
	 * @param arguments
	 * @return
	 */
	public abstract boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final AvailObject arguments);

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	public abstract void o_AddDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex);

	/**
	 * @param object
	 * @param implementation
	 */
	public abstract void o_AddImplementation (
		final @NotNull AvailObject object,
		final AvailObject implementation);

	/**
	 * @param object
	 * @param restrictions
	 */
	public abstract void o_AddRestrictions (
		final @NotNull AvailObject object,
		final AvailObject restrictions);

	/**
	 * Add the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#plusCanDestroy(AvailObject, boolean) plusCanDestroy}. It
	 * exists for double-dispatch only.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param anInfinity
	 *        An {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         InfinityDescriptor infinities} of differing signs.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract @NotNull AvailObject o_AddToInfinityCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException;

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
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param methodName
	 * @param illegalArgMsgs
	 */
	public abstract void o_AddGrammaticalMessageRestrictions (
		final @NotNull AvailObject object,
		final AvailObject methodName,
		final AvailObject illegalArgMsgs);

	/**
	 * @param object
	 * @param methodName
	 * @param implementation
	 */
	public abstract void o_AddMethodImplementation (
		final @NotNull AvailObject object,
		final AvailObject methodName,
		final AvailObject implementation);

	/**
	 * @param object
	 * @param message
	 * @param bundle
	 */
	public abstract void o_AtMessageAddBundle (
		final @NotNull AvailObject object,
		final AvailObject message,
		final AvailObject bundle);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	public abstract void o_AtNameAdd (
		final @NotNull AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	public abstract void o_AtNewNamePut (
		final @NotNull AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param stringName
	 * @param trueName
	 */
	public abstract void o_AtPrivateNameAdd (
		final @NotNull AvailObject object,
		final AvailObject stringName,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_BinElementAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_BinElementAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_BinHash (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_BinSize (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_BinUnionTypeOrTop (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_BitVector (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_BreakpointBlock (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param bundleTree
	 */
	public abstract void o_BuildFilteredBundleTreeFrom (
		final @NotNull AvailObject object,
		final AvailObject bundleTree);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Caller (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Function (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Code (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_CodePoint (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param anotherObject
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean o_CompareFromToWithStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aTuple
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean o_CompareFromToWithAnyTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTuple,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteString
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean o_CompareFromToWithByteStringStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteString,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aByteTuple
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean o_CompareFromToWithByteTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aByteTuple,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aNybbleTuple
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean o_CompareFromToWithNybbleTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aNybbleTuple,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param anObjectTuple
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean o_CompareFromToWithObjectTupleStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anObjectTuple,
		final int startIndex2);

	/**
	 * @param object
	 * @param startIndex1
	 * @param endIndex1
	 * @param aTwoByteString
	 * @param startIndex2
	 * @return
	 */
	public abstract boolean o_CompareFromToWithTwoByteStringStartingAt (
		final @NotNull AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject aTwoByteString,
		final int startIndex2);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_LazyComplete (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @return
	 */
	public abstract int o_ComputeHashFromTo (
		final @NotNull AvailObject object,
		final int start,
		final int end);

	/**
	 * @param object
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_ConcatenateTuplesCanDestroy (
		final @NotNull AvailObject object,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Continuation (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param filteredBundleTree
	 * @param visibleNames
	 */
	public abstract void o_CopyToRestrictedTo (
		final @NotNull AvailObject object,
		final AvailObject filteredBundleTree,
		final AvailObject visibleNames);

	/**
	 * @param object
	 * @param start
	 * @param end
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_CopyTupleFromToCanDestroy (
		final @NotNull AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_DataAtIndex (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_DataAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ParsingPc (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * Divide the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#divideIntoIntegerCanDestroy(AvailObject, boolean)
	 * divideIntoIntegerCanDestroy} or {@link
	 * AvailObject#divideIntoInfinityCanDestroy(AvailObject, boolean)
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
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject divisor} was {@linkplain
	 *         IntegerDescriptor#zero() zero}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	public abstract @NotNull AvailObject o_DivideCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException;

	/**
	 * Divide the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#divideCanDestroy(AvailObject, boolean) divideCanDestroy}. It
	 * exists for double-dispatch only.</p>

	 * @param object
	 *        The divisor, an integral numeric.
	 * @param anInfinity
	 *        The dividend, an {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject divisor} was {@linkplain
	 *         IntegerDescriptor#zero() zero}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_DivideIntoInfinityCanDestroy (
			final @NotNull AvailObject object,
			final AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException;

	/**
	 * Divide the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#divideCanDestroy(AvailObject, boolean) divideCanDestroy}. It
	 * exists for double-dispatch only.</p>

	 * @param object
	 *        The divisor, an integral numeric.
	 * @param anInfinity
	 *        The dividend, an {@linkplain IntegerDescriptor integer}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject divisor} was {@linkplain
	 *         IntegerDescriptor#zero() zero}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract AvailObject o_DivideIntoIntegerCanDestroy (
			final @NotNull AvailObject object,
			final AvailObject anInteger,
			final boolean canDestroy)
		throws ArithmeticException;

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_ElementAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_ElementAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int o_EndOfZone (
		final @NotNull AvailObject object,
		final int zone);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int o_EndSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ExecutionState (
		final @NotNull AvailObject object,
		final ExecutionState value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract byte o_ExtractNybbleFromTupleAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract List<AvailObject> o_FilterByTypes (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param zone
	 * @param newSubtuple
	 * @param startSubtupleIndex
	 * @param endOfZone
	 * @return
	 */
	public abstract AvailObject
		o_ForZoneSetSubtupleStartSubtupleIndexEndOfZone (
			final @NotNull AvailObject object,
			final int zone,
			final AvailObject newSubtuple,
			final int startSubtupleIndex,
			final int endOfZone);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_GreaterThanInteger (
		final @NotNull AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_GreaterThanSignedInfinity (
		final @NotNull AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param elementObject
	 * @return
	 */
	public abstract boolean o_HasElement (
		final @NotNull AvailObject object,
		final AvailObject elementObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Hash (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public abstract int o_HashFromTo (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_HashOrZero (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	public abstract boolean o_HasKey (
		final @NotNull AvailObject object,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param argTypes
	 * @return
	 */
	public abstract List<AvailObject> o_ImplementationsAtOrBelow (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes);

	/**
	 * @param object
	 * @param messageBundle
	 * @return
	 */
	public abstract AvailObject o_IncludeBundle (
		final @NotNull AvailObject object,
		final AvailObject messageBundle);

	/**
	 * @param object
	 * @param imp
	 * @return
	 */
	public abstract boolean o_IncludesImplementation (
		final @NotNull AvailObject object,
		final AvailObject imp);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_LazyIncomplete (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Index (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_InternalHash (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_InterruptRequestFlag (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_InvocationCount (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param aBoolean
	 */
	public abstract void o_IsSaved (
		final @NotNull AvailObject object,
		final boolean aBoolean);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_IsSubsetOf (
		final @NotNull AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfContainerType (
		final @NotNull AvailObject object,
		final AvailObject aContainerType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final AvailObject anObjectType);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType);

	/**
	 * @param object
	 * @param aPrimitiveType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final AvailObject aPrimitiveType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param aUnionMeta
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfUnionMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aUnionMeta);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_KeyAtIndex (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param keyObject
	 */
	public abstract void o_KeyAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_LessOrEqual (
		final @NotNull AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_LessThan (
		final @NotNull AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param chunk
	 * @param offset
	 */
	public abstract void o_LevelTwoChunkOffset (
		final @NotNull AvailObject object,
		final AvailObject chunk,
		final int offset);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Literal (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_LiteralAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_ArgOrLocalOrStackAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_ArgOrLocalOrStackAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_LocalTypeAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param argumentTypeList
	 * @return
	 */
	public abstract AvailObject o_LookupByTypesFromList (
		final @NotNull AvailObject object,
		final List<AvailObject> argumentTypeList);

	/**
	 * @param object
	 * @param continuation
	 * @param stackp
	 * @return
	 */
	public abstract AvailObject o_LookupByTypesFromContinuationStackp (
		final @NotNull AvailObject object,
		final AvailObject continuation,
		final int stackp);

	/**
	 * @param object
	 * @param argumentTypeTuple
	 * @return
	 */
	public abstract AvailObject o_LookupByTypesFromTuple (
		final @NotNull AvailObject object,
		final AvailObject argumentTypeTuple);

	/**
	 * @param object
	 * @param argumentList
	 * @return
	 */
	public abstract AvailObject o_LookupByValuesFromList (
		final @NotNull AvailObject object,
		final List<AvailObject> argumentList);

	/**
	 * @param object
	 * @param argumentTuple
	 * @return
	 */
	public abstract AvailObject o_LookupByValuesFromTuple (
		final @NotNull AvailObject object,
		final AvailObject argumentTuple);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	public abstract AvailObject o_MapAt (
		final @NotNull AvailObject object,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param keyObject
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_MapAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject keyObject,
		final AvailObject newValueObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_MapSize (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param keyObject
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_MapWithoutKeyCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject keyObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Message (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_MessageParts (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#subtractFromIntegerCanDestroy(AvailObject, boolean)
	 * subtractFromIntegerCanDestroy} or {@link
	 * AvailObject#subtractFromInfinityCanDestroy(AvailObject, boolean)
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
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         InfinityDescriptor infinities} of like signs.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	public abstract AvailObject o_MinusCanDestroy (
			final @NotNull AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException;

	/**
	 * Multiply the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>This method should only be called from {@link
	 * AvailObject#timesCanDestroy(AvailObject, boolean) timesCanDestroy}. It
	 * exists for double-dispatch only.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param anInfinity
	 *        An {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         IntegerDescriptor#zero() zero} and {@linkplain InfinityDescriptor
	 *         infinity}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract @NotNull AvailObject o_MultiplyByInfinityCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException;

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
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         IntegerDescriptor#zero() zero} and {@linkplain InfinityDescriptor
	 *         infinity}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract @NotNull AvailObject o_MultiplyByIntegerCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInteger,
			final boolean canDestroy)
		throws ArithmeticException;

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_MyRestrictions (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_MyType (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Name (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param trueName
	 * @return
	 */
	public abstract boolean o_NameVisible (
		final @NotNull AvailObject object,
		final AvailObject trueName);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_NumBlanks (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract boolean o_OptionallyNilOuterVar (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_OuterTypeAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_OuterVarAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_OuterVarAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Parent (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Pc (final AvailObject object, final int value);

	/**
	 * Add the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#addToIntegerCanDestroy(AvailObject, boolean)
	 * addToIntegerCanDestroy} or {@link
	 * AvailObject#addToInfinityCanDestroy(AvailObject, boolean)
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
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         InfinityDescriptor infinities} of differing signs.
	 */
	public abstract AvailObject o_PlusCanDestroy (
			final @NotNull AvailObject object,
			final AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException;

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Priority (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	public abstract AvailObject o_PrivateAddElement (
		final @NotNull AvailObject object,
		final AvailObject element);

	/**
	 * @param object
	 * @param element
	 * @return
	 */
	public abstract AvailObject o_PrivateExcludeElement (
		final @NotNull AvailObject object,
		final AvailObject element);

	/**
	 * @param object
	 * @param element
	 * @param knownIndex
	 * @return
	 */
	public abstract AvailObject o_PrivateExcludeElementKnownIndex (
		final @NotNull AvailObject object,
		final AvailObject element,
		final int knownIndex);

	/**
	 * @param object
	 * @param keyObject
	 * @return
	 */
	public abstract AvailObject o_PrivateExcludeKey (
		final @NotNull AvailObject object,
		final AvailObject keyObject);

	/**
	 * @param object
	 * @param keyObject
	 * @param valueObject
	 * @return
	 */
	public abstract AvailObject o_PrivateMapAtPut (
		final @NotNull AvailObject object,
		final AvailObject keyObject,
		final AvailObject valueObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ProcessGlobals (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract short o_RawByteAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	public abstract void o_RawByteAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract short o_RawByteForCharacterAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	public abstract void o_RawByteForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract byte o_RawNybbleAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param aNybble
	 */
	public abstract void o_RawNybbleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final byte aNybble);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract short o_RawShortForCharacterAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param anInteger
	 */
	public abstract void o_RawShortForCharacterAtPut (
		final @NotNull AvailObject object,
		final int index,
		final short anInteger);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int o_RawSignedIntegerAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_RawSignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract long o_RawUnsignedIntegerAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_RawUnsignedIntegerAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value);

	/**
	 * @param object
	 * @param aChunkIndex
	 */
	public abstract void o_RemoveDependentChunkIndex (
		final @NotNull AvailObject object,
		final int aChunkIndex);

	/**
	 * @param object
	 * @param anInterpreter
	 */
	public abstract void o_RemoveFrom (
		final @NotNull AvailObject object,
		final L2Interpreter anInterpreter);

	/**
	 * @param object
	 * @param implementation
	 */
	public abstract void o_RemoveImplementation (
		final @NotNull AvailObject object,
		final AvailObject implementation);

	/**
	 * @param object
	 * @param bundle
	 * @return
	 */
	public abstract boolean o_RemoveBundle (
		final @NotNull AvailObject object,
		AvailObject bundle);

	/**
	 * @param object
	 * @param obsoleteRestrictions
	 */
	public abstract void o_RemoveRestrictions (
		final @NotNull AvailObject object,
		final AvailObject obsoleteRestrictions);

	/**
	 * @param object
	 * @param forwardImplementation
	 * @param methodName
	 */
	public abstract void o_ResolvedForwardWithName (
		final @NotNull AvailObject object,
		final AvailObject forwardImplementation,
		final AvailObject methodName);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ReturnType (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_RootBin (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SetIntersectionCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SetMinusCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param zoneIndex
	 * @param newTuple
	 */
	public abstract void o_SetSubtupleForZoneTo (
		final @NotNull AvailObject object,
		final int zoneIndex,
		final AvailObject newTuple);

	/**
	 * @param object
	 * @param otherSet
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SetUnionCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject otherSet,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param newValue
	 */
	public abstract void o_SetValue (
		final @NotNull AvailObject object,
		final AvailObject newValue);

	/**
	 * @param object
	 * @param newElementObject
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SetWithElementCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject newElementObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param elementObjectToExclude
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_SetWithoutElementCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject elementObjectToExclude,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Size (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int o_SizeOfZone (final AvailObject object, final int zone);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_LazySpecialActions (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param slotIndex
	 * @return
	 */
	public abstract AvailObject o_StackAt (
		final @NotNull AvailObject object,
		final int slotIndex);

	/**
	 * @param object
	 * @param slotIndex
	 * @param anObject
	 */
	public abstract void o_StackAtPut (
		final @NotNull AvailObject object,
		final int slotIndex,
		final AvailObject anObject);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Stackp (final AvailObject object, final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Start (
		final @NotNull AvailObject object,
		final int value);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_StartingChunk (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int o_StartOfZone (
		final @NotNull AvailObject object,
		final int zone);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract int o_StartSubtupleIndexInZone (
		final @NotNull AvailObject object,
		final int zone);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_String (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#subtractFromIntegerCanDestroy(AvailObject, boolean)
	 * subtractFromIntegerCanDestroy} or {@link
	 * AvailObject#subtractFromInfinityCanDestroy(AvailObject, boolean)
	 * subtractFromInfinityCanDestroy}, where actual implementations of the
	 * subtraction operation should reside.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param anInfinity
	 *        An {@linkplain InfinityDescriptor infinity}.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         InfinityDescriptor infinities} of like signs.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	abstract @NotNull AvailObject o_SubtractFromInfinityCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject anInfinity,
			final boolean canDestroy)
		throws ArithmeticException;

	/**
	 * Difference the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#subtractFromIntegerCanDestroy(AvailObject, boolean)
	 * subtractFromIntegerCanDestroy} or {@link
	 * AvailObject#subtractFromInfinityCanDestroy(AvailObject, boolean)
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
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param zone
	 * @return
	 */
	public abstract AvailObject o_SubtupleForZone (
		final @NotNull AvailObject object,
		final int zone);

	/**
	 * Multiply the {@linkplain AvailObject operands} and answer the result.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * AvailObject#multiplyByIntegerCanDestroy(AvailObject, boolean)
	 * multiplyByIntegerCanDestroy} or {@link
	 * AvailObject#multiplyByInfinityCanDestroy(AvailObject, boolean)
	 * multiplyByInfinityCanDestroy}, where actual implementations of the
	 * multiplication operation should reside.</p>
	 *
	 * @param object
	 *        An integral numeric.
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 * @throws ArithmeticException
	 *         If the {@linkplain AvailObject operands} were {@linkplain
	 *         IntegerDescriptor#zero() zero} and {@linkplain InfinityDescriptor
	 *         infinity}.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	public abstract @NotNull AvailObject o_TimesCanDestroy (
			final @NotNull AvailObject object,
			final @NotNull AvailObject aNumber,
			final boolean canDestroy)
		throws ArithmeticException;

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_TokenType (
		final @NotNull AvailObject object,
		final TokenDescriptor.TokenType value);

	/**
	 * @param object
	 * @param tupleIndex
	 * @param zoneIndex
	 * @return
	 */
	public abstract int o_TranslateToZone (
		final @NotNull AvailObject object,
		final int tupleIndex,
		final int zoneIndex);

	/**
	 * @param object
	 * @param stringName
	 * @return
	 */
	public abstract AvailObject o_TrueNamesForStringName (
		final @NotNull AvailObject object,
		final AvailObject stringName);

	/**
	 * @param object
	 * @param newTupleSize
	 * @return
	 */
	public abstract AvailObject o_TruncateTo (
		final @NotNull AvailObject object,
		final int newTupleSize);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_TupleAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param aNybbleObject
	 */
	public abstract void o_TupleAtPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject aNybbleObject);

	/**
	 * @param object
	 * @param index
	 * @param newValueObject
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_TupleAtPuttingCanDestroy (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int o_TupleIntAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Type (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType);

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfContainerType (
		final @NotNull AvailObject object,
		final AvailObject aContainerType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param someMeta
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final AvailObject someMeta);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final AvailObject anObjectType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType);

	/**
	 * @param object
	 * @param aContainerType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfContainerType (
		final @NotNull AvailObject object,
		final AvailObject aContainerType);

	/**
	 * @param object
	 * @param aContinuationType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aCompiledCodeType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param anIntegerRangeType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject anIntegerRangeType);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param anObjectType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final AvailObject anObjectType);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Unclassified (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	public abstract AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int o_UntranslatedDataAt (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param value
	 */
	public abstract void o_UntranslatedDataAtPut (
		final @NotNull AvailObject object,
		final int index,
		final int value);

	/**
	 * @param object
	 * @param argTypes
	 * @param anAvailInterpreter
	 * @param failBlock
	 * @return
	 */
	public abstract AvailObject o_ValidateArgumentTypesInterpreterIfFail (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes,
		final Interpreter anAvailInterpreter,
		final Continuation1<Generator<String>> failBlock);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Value (
		final @NotNull AvailObject object,
		final AvailObject value);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract AvailObject o_ValueAtIndex (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @param index
	 * @param valueObject
	 */
	public abstract void o_ValueAtIndexPut (
		final @NotNull AvailObject object,
		final int index,
		final AvailObject valueObject);

	/**
	 * @param object
	 * @param index
	 * @return
	 */
	public abstract int o_ZoneForIndex (
		final @NotNull AvailObject object,
		final int index);

	/**
	 * @param object
	 * @return
	 */
	public abstract String o_AsNativeString (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_AsObject (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_AsSet (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_AsTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_BitsPerEntry (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_BitVector (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_BodyBlock (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_BodySignature (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_BreakpointBlock (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Caller (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Capacity (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_CleanUpAfterCompile (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_ClearValue (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Function (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_FunctionType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Code (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_CodePoint (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_LazyComplete (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ConstantBindings (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ContentType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Continuation (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_CopyAsMutableContinuation (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_CopyAsMutableObjectTuple (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_CopyAsMutableSpliceTuple (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_DefaultType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_ParsingPc (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_DisplayTestingTree (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_EnsureMutable (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract ExecutionState o_ExecutionState (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract void o_Expand (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_ExtractBoolean (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract short o_ExtractByte (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract double o_ExtractDouble (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract float o_ExtractFloat (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_ExtractInt (final AvailObject object);

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
	public abstract long o_ExtractLong (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract byte o_ExtractNybble (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_FieldMap (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_FieldTypeMap (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_FilteredBundleTree (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_GetInteger (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_GetValue (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_HashOrZero (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_HasRestrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ImplementationsTuple (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_LazyIncomplete (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Index (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_InternalHash (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_InterruptRequestFlag (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_InvocationCount (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsAbstract (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsFinite (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsForward (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsMethod (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsPositive (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSaved (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSplice (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSupertypeOfBottom (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsValid (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract List<AvailObject> o_KeysAsArray (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_KeysAsSet (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_KeyType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_LevelTwoChunk (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_LevelTwoOffset (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Literal (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_LowerBound (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_LowerInclusive (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_MapSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_MaxStackDepth (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Message (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_MessageParts (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Methods (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_MyRestrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Name (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Names (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_NewNames (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumArgs (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumArgsAndLocalsAndStack (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumberOfZones (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumBlanks (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumDoubles (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumIntegers (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumLiterals (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumLocals (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumObjects (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumOuters (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_NumOuterVars (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Nybbles (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Parent (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Pc (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_PrimitiveNumber (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Priority (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_PrivateNames (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ProcessGlobals (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_ReleaseVariableOrMakeContentsImmutable (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_RemoveRestrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_GrammaticalRestrictions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ReturnType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_RootBin (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_SetSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Signature (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_SizeRange (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_LazySpecialActions (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Stackp (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Start (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_StartingChunk (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_Step (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_String (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_TestingTree (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract TokenDescriptor.TokenType o_TokenType (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_TrimExcessInts (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_TupleSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_TypeTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Unclassified (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_UpperBound (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_UpperInclusive (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Value (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ValuesAsTuple (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ValueType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_VariableBindings (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Vectors (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_Verify (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_VisibleNames (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_InfinitySign (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Wordcodes (final AvailObject object);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean o_EqualsAnyTuple (
		final @NotNull AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	public abstract boolean o_EqualsByteString (
		final @NotNull AvailObject object,
		final AvailObject aString);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean o_EqualsByteTuple (
		final @NotNull AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param otherCodePoint
	 * @return
	 */
	public abstract boolean o_EqualsCharacterWithCodePoint (
		final @NotNull AvailObject object,
		final int otherCodePoint);

	/**
	 * @param object
	 * @param aFunction
	 * @return
	 */
	public abstract boolean o_EqualsFunction (
		final @NotNull AvailObject object,
		final AvailObject aFunction);

	/**
	 * @param object
	 * @param aFunctionType
	 * @return
	 */
	public abstract boolean o_EqualsFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType);

	/**
	 * @param object
	 * @param aCompiledCode
	 * @return
	 */
	public abstract boolean o_EqualsCompiledCode (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCode);

	/**
	 * @param object
	 * @param aContainer
	 * @return
	 */
	public abstract boolean o_EqualsContainer (
		final @NotNull AvailObject object,
		final AvailObject aContainer);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_EqualsContainerType (
		final @NotNull AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @param aContinuation
	 * @return
	 */
	public abstract boolean o_EqualsContinuation (
		final @NotNull AvailObject object,
		final AvailObject aContinuation);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_EqualsContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_EqualsCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType);

	/**
	 * @param object
	 * @param aDoubleObject
	 * @return
	 */
	public abstract boolean o_EqualsDouble (
		final @NotNull AvailObject object,
		final AvailObject aDoubleObject);

	/**
	 * @param object
	 * @param aFloatObject
	 * @return
	 */
	public abstract boolean o_EqualsFloat (
		final @NotNull AvailObject object,
		final AvailObject aFloatObject);

	/**
	 * @param object
	 * @param anInfinity
	 * @return
	 */
	public abstract boolean o_EqualsInfinity (
		final @NotNull AvailObject object,
		final AvailObject anInfinity);

	/**
	 * @param object
	 * @param anAvailInteger
	 * @return
	 */
	public abstract boolean o_EqualsInteger (
		final @NotNull AvailObject object,
		final AvailObject anAvailInteger);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_EqualsIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject another);

	/**
	 * @param object
	 * @param aMap
	 * @return
	 */
	public abstract boolean o_EqualsMap (
		final @NotNull AvailObject object,
		final AvailObject aMap);

	/**
	 * @param object
	 * @param aMapType
	 * @return
	 */
	public abstract boolean o_EqualsMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean o_EqualsNybbleTuple (
		final @NotNull AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param anObject
	 * @return
	 */
	public abstract boolean o_EqualsObject (
		final @NotNull AvailObject object,
		final AvailObject anObject);

	/**
	 * @param object
	 * @param aTuple
	 * @return
	 */
	public abstract boolean o_EqualsObjectTuple (
		final @NotNull AvailObject object,
		final AvailObject aTuple);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_EqualsPrimitiveType (
		final @NotNull AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @param aSet
	 * @return
	 */
	public abstract boolean o_EqualsSet (
		final @NotNull AvailObject object,
		final AvailObject aSet);

	/**
	 * @param object
	 * @param aSetType
	 * @return
	 */
	public abstract boolean o_EqualsSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract boolean o_EqualsTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param aString
	 * @return
	 */
	public abstract boolean o_EqualsTwoByteString (
		final @NotNull AvailObject object,
		final AvailObject aString);

	/**
	 * @param object
	 * @param potentialInstance
	 * @return
	 */
	public abstract boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final AvailObject potentialInstance);

	/**
	 * @param object
	 * @param anotherObject
	 * @return
	 */
	public abstract boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final AvailObject anotherObject);

	/**
	 * @param object
	 * @param aTupleType
	 * @return
	 */
	public abstract boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_EqualsBlank (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_EqualsNull (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_EqualsNullOrBlank (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Hash (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsFunction (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_MakeImmutable (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_MakeSubobjectsImmutable (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Kind (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsBoolean (final AvailObject object);

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
	public abstract boolean o_IsByteTuple (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsCharacter (final AvailObject object);

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
	public abstract boolean o_IsString (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param aFunction
	 * @return
	 */
	public abstract boolean o_ContainsBlock (
		final @NotNull AvailObject object,
		final AvailObject aFunction);

	/**
	 * @param object
	 */
	public abstract void o_PostFault (final AvailObject object);

	/**
	 * @param object
	 */
	public abstract void o_ReadBarrierFault (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Traversed (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsMap (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsByte (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsNybble (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSet (final AvailObject object);

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param myLevel
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_BinAddingElementHashLevelCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @return
	 */
	public abstract boolean o_BinHasElementHash (
		final @NotNull AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash);

	/**
	 * @param object
	 * @param elementObject
	 * @param elementObjectHash
	 * @param canDestroy
	 * @return
	 */
	public abstract AvailObject o_BinRemoveElementHashCanDestroy (
		final @NotNull AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy);

	/**
	 * @param object
	 * @param potentialSuperset
	 * @return
	 */
	public abstract boolean o_IsBinSubsetOf (
		final @NotNull AvailObject object,
		final AvailObject potentialSuperset);

	/**
	 * @param object
	 * @param mutableTuple
	 * @param startingIndex
	 * @return
	 */
	public abstract int o_PopulateTupleStartingAt (
		final @NotNull AvailObject object,
		final AvailObject mutableTuple,
		final int startingIndex);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_BinHash (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_BinSize (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_BinUnionTypeOrTop (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsTuple (final AvailObject object);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_TypeEquals (
		final @NotNull AvailObject object,
		final AvailObject aType);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_HashOfType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsAtom (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsExtendedInteger (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsIntegerRangeType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsMapType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSetType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsTupleType (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsType (final AvailObject object);

	/**
	 * @param object
	 * @param visitor
	 */
	public abstract void o_ScanSubobjects (
		final @NotNull AvailObject object,
		final AvailSubobjectVisitor visitor);

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
	public abstract @NotNull
	Iterator<AvailObject> o_Iterator (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_ParsingInstructions (
		final @NotNull AvailObject object,
		AvailObject instructionsTuple);

	/**
	 * @param object
	 * @param value
	 */
	public abstract AvailObject o_ParsingInstructions (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param expression
	 * @return
	 */
	public abstract void o_Expression (
		final @NotNull AvailObject object,
		final AvailObject expression);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Expression (final AvailObject object);

	/**
	 * @param object
	 * @param variable
	 */
	public abstract void o_Variable (
		final @NotNull AvailObject object,
		final AvailObject variable);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Variable (final AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ArgumentsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_StatementsTuple (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ResultType (AvailObject object);

	/**
	 * @param object
	 * @param neededVariables
	 */
	public abstract void o_NeededVariables (
		final @NotNull AvailObject object,
		AvailObject neededVariables);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_NeededVariables (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_Primitive (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_DeclaredType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract DeclarationKind o_DeclarationKind (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param initializationExpression
	 */
	public abstract void o_InitializationExpression (
		final @NotNull AvailObject object,
		AvailObject initializationExpression);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_InitializationExpression (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_LiteralObject (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Token (AvailObject object);

	/**
	 * @param object
	 * @param markerValue
	 */
	public abstract void o_MarkerValue (
		final @NotNull AvailObject object,
		AvailObject markerValue);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_MarkerValue (AvailObject object);

	/**
	 * @param object
	 * @param arguments
	 */
	public abstract void o_Arguments (
		final @NotNull AvailObject object,
		AvailObject arguments);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Arguments (AvailObject object);

	/**
	 * @param object
	 * @param implementationSet
	 */
	public abstract void o_ImplementationSet (
		final @NotNull AvailObject object,
		AvailObject implementationSet);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ImplementationSet (AvailObject object);

	/**
	 * @param object
	 * @param superCastType
	 */
	public abstract void o_SuperCastType (
		final @NotNull AvailObject object,
		AvailObject superCastType);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_SuperCastType (AvailObject object);

	/**
	 * @param object
	 * @param expressionsTuple
	 */
	public abstract void o_ExpressionsTuple (
		final @NotNull AvailObject object,
		AvailObject expressionsTuple);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ExpressionsTuple (AvailObject object);

	/**
	 * @param object
	 * @param tupleType
	 */
	public abstract void o_TupleType (
		final @NotNull AvailObject object,
		AvailObject tupleType);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_TupleType (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Declaration (AvailObject object);

	/**
	 * @return
	 */
	public abstract AvailObject o_ExpressionType (AvailObject object);

	/**
	 * @param object
	 * @param codeGenerator
	 */
	public abstract void o_EmitEffectOn (
		final @NotNull AvailObject object,
		AvailCodeGenerator codeGenerator);

	/**
	 * @param object
	 * @param codeGenerator
	 */
	public abstract void o_EmitValueOn (
		final @NotNull AvailObject object,
		AvailCodeGenerator codeGenerator);

	/**
	 * Map my children through the (destructive) transformation specified by
	 * aBlock.
	 */
	public abstract void o_ChildrenMap (
		final @NotNull AvailObject object,
		Transformer1<AvailObject, AvailObject> aBlock);

	/**
	 * Visit my child parse nodes with aBlock.
	 */
	public abstract void o_ChildrenDo (
		final @NotNull AvailObject object,
		Continuation1<AvailObject> aBlock);

	/**
	 * @param object
	 * @param parent
	 * @param outerBlocks
	 * @param anAvailInterpreter
	 */
	public abstract void o_ValidateLocally (
		 final @NotNull AvailObject object,
		 AvailObject parent,
		 List<AvailObject> outerBlocks,
		 L2Interpreter anAvailInterpreter);

	/**
	 * @param object
	 * @param codeGenerator
	 * @return
	 */
	public abstract AvailObject o_Generate (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator);

	/**
	 * @param object
	 * @param newParseNode
	 * @return
	 */
	public abstract AvailObject o_CopyWith (
		final @NotNull AvailObject object,
		AvailObject newParseNode);

	/**
	 * @param object
	 * @param isLastUse
	 */
	public abstract void o_IsLastUse (
		final @NotNull AvailObject object,
		final boolean isLastUse);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsLastUse (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsMacro (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Macros (
		final @NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Macros (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_CopyMutableParseNode (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_BinUnionKind (AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_MacroName (
		final @NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_MacroName (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_OutputParseNode (
		final @NotNull AvailObject object,
		AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_OutputParseNode (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ApparentSendName (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param statementsTuple
	 */
	public abstract void o_Statements (
		final @NotNull AvailObject object, AvailObject statementsTuple);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Statements (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param accumulatedStatements
	 */
	public abstract void o_FlattenStatementsInto (
		final @NotNull AvailObject object,
		List<AvailObject> accumulatedStatements);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_LineNumber (AvailObject object, int value);

	/**
	 * @param object
	 * @return
	 */
	public abstract int o_LineNumber (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_AllBundles (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsSetBin (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract MapDescriptor.MapIterable o_MapIterable (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Complete (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Incomplete (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_SpecialActions (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 */
	public abstract @NotNull AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsInt (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsLong (AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ArgsTupleType (AvailObject object);

	/**
	 * @param object
	 * @param anInstanceType
	 * @return
	 */
	public abstract boolean o_EqualsInstanceTypeFor (
		final @NotNull AvailObject object,
		AvailObject anObject);

	/**
	 * @param object
	 * @return
	 */
	public abstract @NotNull AvailObject o_Instances (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param aSet
	 * @return
	 */
	public boolean o_EqualsUnionTypeWithSet (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSet)
	{
		return false;
	}

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsAbstractUnionType (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param aType
	 * @return
	 */
	public abstract boolean o_IsInstanceOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType);

	/**
	 * @param object
	 * @param potentialInstance
	 * @return
	 */
	public abstract boolean o_AbstractUnionTypeIncludesInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_ComputeSuperkind (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param key
	 * @param value
	 */
	public abstract void o_SetAtomProperty (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final @NotNull AvailObject value);

	/**
	 * @param object
	 * @param key
	 * @return
	 */
	public abstract @NotNull AvailObject o_GetAtomProperty (
		final @NotNull AvailObject object,
		AvailObject key);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_InnerKind (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param another
	 * @return
	 */
	public abstract boolean o_EqualsUnionMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsUnionMeta (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract @NotNull AvailObject o_ReadType (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @return
	 */
	public abstract @NotNull AvailObject o_WriteType (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param value
	 */
	public abstract void o_Versions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value);

	/**
	 * @param object
	 * @return
	 */
	public abstract AvailObject o_Versions (final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	public abstract boolean o_EqualsParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType);

	/**
	 * @param object
	 * @param aParseNodeType
	 * @return
	 */
	public abstract @NotNull AvailObject o_TypeUnionOfParseNodeType (
		final @NotNull AvailObject object,
		AvailObject aParseNodeType);

	/**
	 * @param object
	 * @return
	 */
	public abstract @NotNull ParseNodeKind o_ParseNodeKind (
		final @NotNull AvailObject object);

	/**
	 * @param availObject
	 * @param expectedParseNodeKind
	 * @return
	 */
	public abstract boolean o_ParseNodeKindIsUnder (
		final @NotNull AvailObject object,
		final @NotNull ParseNodeKind expectedParseNodeKind);

	/**
	 * @param object
	 * @param restrictionSignature
	 * @return
	 */
	public abstract void o_AddTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictionSignature);

	/**
	 * @param object
	 * @param restrictionSignature
	 * @return
	 */
	public abstract void o_RemoveTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictionSignature);

	/**
	 * Return the {@linkplain ImplementationSetDescriptor implementation set}'s
	 * {@linkplain TupleDescriptor tuple} of {@linkplain FunctionDescriptor
	 * functions} that statically restrict call sites by argument type.
	 *
	 * @param object The implementation set.
	 */
	public abstract @NotNull AvailObject o_TypeRestrictions (
		final @NotNull AvailObject object);

	/**
	 * @param object
	 * @param tupleType
	 */
	public abstract void o_AddSealedArgumentsType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType);

	/**
	 * @param object
	 * @param tupleType
	 */
	public abstract void o_RemoveSealedArgumentsType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType);

	/**
	 * @param object
	 * @return
	 */
	public abstract @NotNull AvailObject o_SealedArgumentsTypesTuple (
		final @NotNull AvailObject object);

	/**
	 * @param availObject
	 * @param methodNameAtom
	 * @param typeRestrictionFunction
	 */
	public abstract void o_AddTypeRestriction (
		final @NotNull AvailObject object,
		final @NotNull AvailObject methodNameAtom,
		final @NotNull AvailObject typeRestrictionFunction);

	/**
	 * @param object
	 * @param name
	 * @param constantBinding
	 */
	public abstract void o_AddConstantBinding (
		final @NotNull AvailObject object,
		final @NotNull AvailObject name,
		final @NotNull AvailObject constantBinding);

	/**
	 * @param object
	 * @param name
	 * @param variableBinding
	 */
	public abstract void o_AddVariableBinding (
		final @NotNull AvailObject object,
		final @NotNull AvailObject name,
		final @NotNull AvailObject variableBinding);

	/**
	 * @param object
	 * @return
	 */
	public abstract boolean o_IsImplementationSetEmpty (
		final @NotNull AvailObject object);
}
