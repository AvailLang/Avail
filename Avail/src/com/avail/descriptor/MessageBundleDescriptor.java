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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VoidDescriptor;
import java.util.List;

public class MessageBundleDescriptor extends Descriptor
{

	public enum ObjectSlots
	{
		MESSAGE,
		MESSAGE_PARTS,
		MY_RESTRICTIONS,
		PARSING_INSTRUCTIONS
	}


	// accessing

	@Override
	public void o_AddRestrictions (
			final AvailObject object,
			final AvailObject restrictions)
	{
		assert restrictions.isTuple();
		restrictions.makeImmutable();
		AvailObject merged = object.myRestrictions();
		if (merged.equalsVoid())
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

	@Override
	public void o_RemoveRestrictions (
			final AvailObject object,
			final AvailObject obsoleteRestrictions)
	{
		assert obsoleteRestrictions.isTuple();
		AvailObject reduced = object.myRestrictions();
		if (reduced.equals(obsoleteRestrictions))
		{
			object.myRestrictions(VoidDescriptor.voidObject());
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

	@Override
	public boolean o_HasRestrictions (
			final AvailObject object)
	{
		if (object.myRestrictions().equalsVoid())
		{
			return false;
		}
		for (AvailObject setForArgument : object.myRestrictions())
		{
			if (setForArgument.setSize() > 0)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public void o_RemoveRestrictions (
			final AvailObject object)
	{
		object.myRestrictions(VoidDescriptor.voidObject());
	}

	@Override
	public AvailObject o_Restrictions (
			final AvailObject object)
	{
		AvailObject restrictions = object.myRestrictions();
		if (restrictions.equalsVoid())
		{
			final AvailObject parts = object.messageParts();
			int count = 0;
			for (AvailObject part : parts)
			{
				if (part.equals(TupleDescriptor.underscoreTuple()))
				{
					count++;
				}
			}
			restrictions = parts.copyTupleFromToCanDestroy(
				1,
				count,
				false);
			for (int index = 1; index <= count; index++)
			{
				restrictions = restrictions.tupleAtPuttingCanDestroy(
					index,
					SetDescriptor.empty(),
					true);
			}
			object.myRestrictions(restrictions.makeImmutable());
		}
		return restrictions;
	}



	/**
	 * Setter for field message.
	 */
	@Override
	public void o_Message (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.MESSAGE, value);
	}

	/**
	 * Setter for field messageParts.
	 */
	@Override
	public void o_MessageParts (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.MESSAGE_PARTS, value);
	}

	/**
	 * Setter for field myRestrictions.
	 */
	@Override
	public void o_MyRestrictions (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.MY_RESTRICTIONS, value);
	}

	/**
	 * Getter for field message.
	 */
	@Override
	public AvailObject o_Message (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MESSAGE);
	}

	/**
	 * Getter for field messageParts.
	 */
	@Override
	public AvailObject o_MessageParts (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MESSAGE_PARTS);
	}

	/**
	 * Getter for field myRestrictions.
	 */
	@Override
	public AvailObject o_MyRestrictions (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MY_RESTRICTIONS);
	}


	/**
	 * Setter for field parsingInstructions.
	 */
	@Override
	public void o_ParsingInstructions (
			final AvailObject object,
			final AvailObject instructionsTuple)
	{
		object.objectSlotPut(
			ObjectSlots.PARSING_INSTRUCTIONS,
			instructionsTuple);
	}

	/**
	 * Getter for field parsingInstructions.
	 */
	@Override
	public AvailObject o_ParsingInstructions (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PARSING_INSTRUCTIONS);
	}

	
	@Override
	public boolean allowsImmutableToMutableReferenceInField (
			final Enum<?> e)
	{
		if (e == ObjectSlots.MY_RESTRICTIONS)
		{
			return true;
		}
		return false;
	}



	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
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



	@Override
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	/**
	 * Answer the object's type.  Don't answer an ApproximateType.
	 */
	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		return Types.messageBundle.object();
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		return (object.message().hash() ^ 0x312CAB9);
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		return Types.messageBundle.object();
	}



	/**
	 * Create a new {@link MessageBundleDescriptor message bundle} for the
	 * given message.  Also use the provided tuple of message parts and parsing
	 * instructions.
	 * 
	 * @param message The message name, a {@link CyclicTypeDescriptor cyclic
	 *                type}.
	 * @param parts A tuple of strings constituting the message name.
	 * @param instructions A tuple of integers encoding parsing instructions.
	 * @return A new {@link MessageBundleDescriptor message bundle}.
	 */
	public static AvailObject newBundle (
		AvailObject message,
		AvailObject parts,
		AvailObject instructions)
	{
		AvailObject result = mutable().create();
		assert message.isCyclicType();	
		result.message(message);
		result.messageParts(parts);
		result.myRestrictions(VoidDescriptor.voidObject());
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
	private final static MessageBundleDescriptor mutable = new MessageBundleDescriptor(true);

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
	private final static MessageBundleDescriptor immutable = new MessageBundleDescriptor(false);

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
