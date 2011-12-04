/**
 * descriptor/MessageBundleDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

import static com.avail.descriptor.TypeDescriptor.Types.MESSAGE_BUNDLE;
import java.util.*;
import com.avail.annotations.*;

public class MessageBundleDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		MESSAGE,
		MESSAGE_PARTS,
		MY_RESTRICTIONS,
		PARSING_INSTRUCTIONS
	}

	@Override @AvailMethod
	void o_AddRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject restrictions)
	{
		assert restrictions.isTuple();
		restrictions.makeImmutable();
		AvailObject merged = object.myRestrictions();
		if (merged.equalsNull())
		{
			object.myRestrictions(restrictions);
			return;
		}
		for (int i = merged.tupleSize(); i >= 1; i--)
		{
			merged = merged.tupleAtPuttingCanDestroy(
				i,
				merged.tupleAt(i).setUnionCanDestroy(
					restrictions.tupleAt(i),
					true),
				true);
		}
		object.myRestrictions(merged);
	}

	@Override @AvailMethod
	void o_RemoveRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject obsoleteRestrictions)
	{
		assert obsoleteRestrictions.isTuple();
		AvailObject reduced = object.myRestrictions();
		if (reduced.equals(obsoleteRestrictions))
		{
			object.myRestrictions(NullDescriptor.nullObject());
			return;
		}
		for (int i = reduced.tupleSize(); i >= 1; i--)
		{
			reduced = reduced.tupleAtPuttingCanDestroy(
				i,
				reduced.tupleAt(i).setMinusCanDestroy(
					obsoleteRestrictions.tupleAt(i),
					true),
				true);
		}
		object.myRestrictions(reduced);
	}

	@Override @AvailMethod
	boolean o_HasRestrictions (
		final @NotNull AvailObject object)
	{
		if (object.myRestrictions().equalsNull())
		{
			return false;
		}
		for (final AvailObject setForArgument : object.myRestrictions())
		{
			if (setForArgument.setSize() > 0)
			{
				return true;
			}
		}
		return false;
	}

	@Override @AvailMethod
	void o_RemoveRestrictions (
		final @NotNull AvailObject object)
	{
		object.myRestrictions(NullDescriptor.nullObject());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_GrammaticalRestrictions (
		final @NotNull AvailObject object)
	{
		AvailObject restrictions = object.myRestrictions();
		if (restrictions.equalsNull())
		{
			final AvailObject parts = object.messageParts();
			int count = 0;
			for (final AvailObject part : parts)
			{
				if (part.equals(StringDescriptor.underscore()))
				{
					count++;
				}
			}
			restrictions = TupleDescriptor.fromCollection(
				Collections.nCopies(count, SetDescriptor.empty()));
			object.myRestrictions(restrictions.makeImmutable());
		}
		return restrictions;
	}

	@Override @AvailMethod
	void o_Message (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.MESSAGE, value);
	}

	@Override @AvailMethod
	void o_MessageParts (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.MESSAGE_PARTS, value);
	}

	@Override @AvailMethod
	void o_MyRestrictions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.MY_RESTRICTIONS, value);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Message (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MESSAGE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MessageParts (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MESSAGE_PARTS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MyRestrictions (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MY_RESTRICTIONS);
	}

	@Override @AvailMethod
	void o_ParsingInstructions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject instructionsTuple)
	{
		object.objectSlotPut(
			ObjectSlots.PARSING_INSTRUCTIONS,
			instructionsTuple);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ParsingInstructions (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PARSING_INSTRUCTIONS);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == ObjectSlots.MY_RESTRICTIONS;
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		// The existing implementations are also printed in parentheses to help
		// distinguish polymorphism from occurrences of non-polymorphic
		// homonyms.
		if (isMutable)
		{
			aStream.append("(mut)");
		}
		aStream.append("bundle\"");
		aStream.append(object.message().name().asNativeString());
		aStream.append("\"");
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.message().hash() ^ 0x312CAB9;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return MESSAGE_BUNDLE.o();
	}

	/**
	 * Create a new {@linkplain MessageBundleDescriptor message bundle} for the
	 * given message.  Also use the provided tuple of message parts and parsing
	 * instructions.
	 *
	 * @param message The message name, an {@linkplain AtomDescriptor atom}.
	 * @param parts A tuple of strings constituting the message name.
	 * @param instructions A tuple of integers encoding parsing instructions.
	 * @return A new {@linkplain MessageBundleDescriptor message bundle}.
	 */
	public static AvailObject newBundle (
		final AvailObject message,
		final AvailObject parts,
		final AvailObject instructions)
	{
		final AvailObject result = mutable().create();
		assert message.isAtom();
		result.message(message);
		result.messageParts(parts);
		result.myRestrictions(NullDescriptor.nullObject());
		result.parsingInstructions(instructions);
		result.makeImmutable();
		return result;
	}

	/**
	 * Construct a new {@link MessageBundleDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected MessageBundleDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MessageBundleDescriptor}.
	 */
	private final static MessageBundleDescriptor mutable =
		new MessageBundleDescriptor(true);

	/**
	 * Answer the mutable {@link MessageBundleDescriptor}.
	 *
	 * @return The mutable {@link MessageBundleDescriptor}.
	 */
	public static MessageBundleDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link MessageBundleDescriptor}.
	 */
	private final static MessageBundleDescriptor immutable =
		new MessageBundleDescriptor(false);

	/**
	 * Answer the immutable {@link MessageBundleDescriptor}.
	 *
	 * @return The immutable {@link MessageBundleDescriptor}.
	 */
	public static MessageBundleDescriptor immutable ()
	{
		return immutable;
	}
}
