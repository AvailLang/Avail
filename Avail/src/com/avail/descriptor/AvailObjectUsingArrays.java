/**
 * descriptor/AvailObjectUsingArrays.java
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

import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.IndirectionDescriptor;
import com.avail.descriptor.VoidDescriptor;
import com.avail.visitor.AvailMarkUnreachableSubobjectVisitor;
import java.util.List;

public class AvailObjectUsingArrays extends AvailObject
{
	AbstractDescriptor _descriptor;
	AvailObject [] _objectSlots;
	int [] _intSlots;


	// gc helpers

	@Override
	public AvailObject saveOrForward ()
	{
		//  The object is in FromSpace.  If its slotsSize is >= 32768, it represents a forwarding
		//  pointer into ToSpace (and the pointer is in the first slot).  Otherwise, save the
		//  object as per GCReadBarrierDescriptor class>>documentation.

		error("The array-based AvailObject representation should not do this.");
		return VoidDescriptor.voidObject();
	}

	@Override
	public void printOnAvoidingIndent (
		final @NotNull StringBuilder builder, 
		final @NotNull List<AvailObject> recursionList, 
		final int indent)
	{
		if (isDestroyed())
		{
			builder.append("*** A DESTROYED OBJECT ***");
			return;
		}

		if (indent > descriptor().maximumIndent())
		{
			builder.append("*** DEPTH ***");
			return;
		}

		for (final AvailObject candidate : recursionList)
		{
			if (candidate == this)
			{
				builder.append("**RECURSION**");
				return;
			}
		}

		recursionList.add(this);
		descriptor().printObjectOnAvoidingIndent(
			this, builder, recursionList, indent);
		recursionList.remove(recursionList.size() - 1);
	}



	// primitive accessing

	@Override
	public void becomeIndirectionTo (
			final AvailObject anotherObject)
	{
		//  Turn me into an indirection to anotherObject.  WARNING: This alters my slots and descriptor.

		verifyToSpaceAddress();
		if (traversed().sameAddressAs(anotherObject.traversed()))
		{
			return;
		}
		final int oldSlotsSize = objectSlotsCount();
		if (oldSlotsSize == 0)
		{
			_objectSlots = new AvailObject[1];
			_objectSlots[0] = VoidDescriptor.voidObject();
		}
		if (descriptor().isMutable())
		{
			if (CanDestroyObjects())
			{
				scanSubobjects(new AvailMarkUnreachableSubobjectVisitor(anotherObject));
			}
			descriptor(IndirectionDescriptor.mutableDescriptor());
			target(anotherObject.traversed());
		}
		else
		{
			anotherObject.makeImmutable();
			descriptor(IndirectionDescriptor.mutableDescriptor());
			target(anotherObject.traversed());
			makeImmutable();
		}
	}

	@Override
	public short byteSlotAtByteIndex (
			final int index)
	{
		//  Extract the byte at the given byte-index.  Always use little endian encoding.

		verifyToSpaceAddress();
		assert(index >= 4);
		assert(index <= integerSlotsCount()*4+3);
		return (short)((_intSlots[(index / 4) - 1] >> ((index & 3) * 8)) & 0xFF);
	}

	@Override
	public void byteSlotAtByteIndexPut (
			final int index, 
			final short aByte)
	{
		//  Store the byte at the given byte-index.  Always use little endian encoding.

		checkWriteAtByteIndex(index);
		verifyToSpaceAddress();
		assert(index >= 4);
		assert(index <= integerSlotsCount()*4+3);
		assert aByte == (aByte & 0xFF);
		int leftShift = (index & 3) * 8;
		int temp = _intSlots[(index / 4) - 1];
		temp &= ~(0xFF << leftShift);
		temp |= aByte << leftShift;
		_intSlots[(index / 4) - 1] = temp;
	}

	@Override
	public void checkValidAddress ()
	{
		//  Check if my address is valid.  Fail if it's outside all the current pages.

		return;
	}

	@Override
	public void checkValidAddressWithByteIndex (
			final int byteIndex)
	{
		//  Check if my address is valid.  Fail if it's outside all the current pages.

		return;
	}

	@Override
	public AbstractDescriptor descriptor ()
	{
		return _descriptor;
	}

	@Override
	public void descriptor (
			final AbstractDescriptor aDescriptor)
	{
		_descriptor = aDescriptor;
	}

	public AvailObject descriptorObjectSlotsSizeIntSlotsSize (
			final AbstractDescriptor theDescriptor, 
			final int objectSlotsSize, 
			final int intSlotsCount)
	{
		_descriptor = theDescriptor;
		_objectSlots = new AvailObject[objectSlotsSize];
		_intSlots = new int[intSlotsCount];
		return this;
	}

	@Override
	public short descriptorId ()
	{
		return _descriptor.id();
	}

	@Override
	public void descriptorId (
			final short anInteger)
	{
		_descriptor = AbstractDescriptor.allDescriptors.get(anInteger);
		checkValidAddress();
	}

	@Override
	public int integerSlotAtByteIndex (
			final int index)
	{
		//  Extract the (unsigned 32-bit) integer at the given byte-index.

		verifyToSpaceAddress();
		assert(index >= 4);
		assert(index <= integerSlotsCount()*4+3);
		assert((index & 3) == 0);
		return _intSlots[(index / 4) - 1];
	}

	@Override
	public void integerSlotAtByteIndexPut (
			final int index, 
			final int anInteger)
	{
		//  Store the (signed 32-bit) integer in the four bytes starting at the given byte-index.

		checkWriteAtByteIndex(index);
		verifyToSpaceAddress();
		assert(index >= 4);
		assert(index <= integerSlotsCount()*4+3);
		assert((index & 3) == 0);
		_intSlots[(index / 4) - 1] = anInteger;
	}

	@Override
	public int integerSlotsCount ()
	{
		return _intSlots.length;
	}

	@Override
	public AvailObject objectSlotAtByteIndex (
			final int index)
	{
		//  Extract the object at the given byte-index.  It must be an object.

		verifyToSpaceAddress();
		assert index <= -4;
		assert index >= objectSlotsCount() * -4;
		assert (index & 3) == 0;
		AvailObject result = _objectSlots[(index / -4) - 1];
		result.verifyToSpaceAddress();
		return result;
	}

	@Override
	public void objectSlotAtByteIndexPut (
			final int index, 
			final AvailObject anAvailObject)
	{
		//  Store the object at the given byte-index.

		verifyToSpaceAddress();
		assert index <= -4;
		assert index >= objectSlotsCount() * -4;
		assert (index & 3) == 0;
		_objectSlots[(index / -4) - 1] = anAvailObject;
	}

	@Override
	public int objectSlotsCount ()
	{
		return _objectSlots.length;
	}

	@Override
	public boolean sameAddressAs (
			final AvailObject anotherObject)
	{
		//  Answer whether the objects occupy the same memory addresses.

		verifyToSpaceAddress();
		anotherObject.verifyToSpaceAddress();
		return this == anotherObject;
	}

	@Override
	public short shortSlotAtByteIndex (
			final int index)
	{
		//  Extract the 16-bit integer at the given byte-index.  Use little endian encoding.

		verifyToSpaceAddress();
		assert(index >= 4);
		assert(index <= integerSlotsCount()*4+3);
		assert((index & 1) == 0);
		return (short)(_intSlots[(index / 4) - 1] >> ((index & 2) * 8) & 0xFFFF);
	}

	@Override
	public void shortSlotAtByteIndexPut (
			final int index, 
			final short aShort)
	{
		//  Store the byte at the given byte-index.

		checkWriteAtByteIndex(index);
		verifyToSpaceAddress();
		assert(index >= 4);
		assert(index <= integerSlotsCount()*4+3);
		assert((index & 1) == 0);
		int leftShift = (index & 2) * 8;
		int temp = _intSlots[(index / 4) - 1];
		temp &= ~(0xFFFF << leftShift);
		temp |= aShort << leftShift;
		_intSlots[(index / 4) - 1] = temp;
	}

	@Override
	public void truncateWithFillerForNewIntegerSlotsCount (
			final int newIntegerSlotsCount)
	{
		//  Slice the current object into two parts, one of which is a Filler object and
		//  is never referred to directly (so doesn't need any slots for becoming an
		//  indirection.

		// Slice the current object into two objects, the left one (at the same starting
		// address as the input), and the right one (a Filler object that nobody should
		// ever create a pointer to).  The new Filler can have zero post-header slots
		// (i.e., just the header), but the left object must not, since it may turn into an
		// Indirection some day and will require at least one slot for the target pointer.
		verifyToSpaceAddress();
		// assert(objectSlotsCount > 0);
		final int oldIntegerSlotsCount = integerSlotsCount();
		assert(newIntegerSlotsCount < oldIntegerSlotsCount);
		// final int fillerSlotCount = oldIntegerSlotsCount - newIntegerSlotsCount - 1;
		// Here's where we would write a filler header into raw memory.
		// Slots *filler = (Slots *)(_pointer.address() + 4 + (newIntegerSlotsCount * 4));
		// filler->descriptorId() = FillerDescriptor.mutableDescriptor().id();
		//  filler->sizeInLongs() = fillerSlotCount;
		int [] newIntSlots = new int [newIntegerSlotsCount];
		System.arraycopy(_intSlots, 0, newIntSlots, 0, newIntegerSlotsCount);
		_intSlots = newIntSlots;
	}

	@Override
	public void truncateWithFillerForNewObjectSlotsCount (
			final int newObjectSlotsCount)
	{
		//  Slice the current object into two parts, one of which is a Filler object and
		//  is never referred to directly (so doesn't need any slots for becoming an
		//  indirection.

		// Slice the current object into two objects, the left one (at the same starting
		// address as the input), and the right one (a Filler object that nobody should
		// ever create a pointer to).  The new Filler can have zero post-header slots
		// (i.e., just the header), but the left object must not, since it may turn into an
		// Indirection some day and will require at least one slot for the target pointer.
		verifyToSpaceAddress();
		assert(newObjectSlotsCount > 0);
		final int oldObjectSlotsCount = objectSlotsCount();
		assert(newObjectSlotsCount < oldObjectSlotsCount);
		// final int fillerSlotCount = oldObjectSlotsCount - newObjectSlotsCount - 1;
		// Here's where we would write a filler header into raw memory.
		// Slots *filler = (Slots *)(_pointer.address() + 4 + (newSlotsSize * 4));
		// filler->descriptorId() = FillerDescriptor.mutableDescriptor().id();
		// filler->sizeInLongs() = fillerSlotCount;
		AvailObject newObjectSlots [] = new AvailObject [newObjectSlotsCount];
		System.arraycopy(_objectSlots, 0, newObjectSlots, 0, newObjectSlotsCount);
		_objectSlots = newObjectSlots;
	}

	@Override
	public void verifyFromSpaceAddress ()
	{
		//  Check that my address is a valid pointer to FromSpace.

		return;
	}

	@Override
	public void verifyToSpaceAddress ()
	{
		//  Check that my address is a valid pointer to ToSpace.

		return;
	}





	public static AvailObject newIndexedDescriptor(int size, AbstractDescriptor descriptor)
	{
		assert CanAllocateObjects();
		int objectSlotCount = descriptor.numberOfFixedObjectSlots();
		if (descriptor.hasVariableObjectSlots())
		{
			objectSlotCount += size;
		}
		int integerSlotCount = descriptor.numberOfFixedIntegerSlots();
		if (descriptor.hasVariableIntegerSlots())
		{
			integerSlotCount += size;
		}
		return new AvailObjectUsingArrays().descriptorObjectSlotsSizeIntSlotsSize(
			descriptor,
			objectSlotCount,
			integerSlotCount);
	};

	public static AvailObject newObjectIndexedIntegerIndexedDescriptor(
			int variableObjectSlots,
			int variableIntegerSlots,
			AbstractDescriptor descriptor)
	{
		assert CanAllocateObjects();
		assert descriptor.hasVariableObjectSlots() || variableObjectSlots == 0;
		assert descriptor.hasVariableIntegerSlots() || variableIntegerSlots == 0;
		int objectSlotCount = descriptor.numberOfFixedObjectSlots() + variableObjectSlots;
		int integerSlotCount = descriptor.numberOfFixedIntegerSlots() + variableIntegerSlots;
		return new AvailObjectUsingArrays().descriptorObjectSlotsSizeIntSlotsSize(
			descriptor,
			objectSlotCount,
			integerSlotCount);
	};

}
