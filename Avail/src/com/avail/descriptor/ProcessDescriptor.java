/**
 * descriptor/ProcessDescriptor.java
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
import com.avail.descriptor.ProcessDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;

import java.util.Random;
import static com.avail.descriptor.AvailObject.*;

@IntegerSlots({
	"hashOrZero", 
	"priority", 
	"executionMode", 
	"executionState", 
	"interruptRequestFlag"
})
@ObjectSlots({
	"continuation", 
	"breakpointBlock", 
	"processGlobals"
})
public class ProcessDescriptor extends Descriptor
{


	// GENERATED accessors

	/**
	 * Setter for field !B!reakpointBlock.
	 */
	@Override
	public void ObjectBreakpointBlock (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-8, value);
	}

	/**
	 * Setter for field !C!ontinuation.
	 */
	@Override
	public void ObjectContinuation (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Setter for field !E!xecutionMode.
	 */
	@Override
	public void ObjectExecutionMode (
			final AvailObject object, 
			final int value)
	{
		object.integerSlotAtByteIndexPut(12, value);
	}

	/**
	 * Setter for field !E!xecutionState.
	 */
	@Override
	public void ObjectExecutionState (
			final AvailObject object, 
			final int value)
	{
		object.integerSlotAtByteIndexPut(16, value);
	}

	/**
	 * Setter for field !H!ashOrZero.
	 */
	@Override
	public void ObjectHashOrZero (
			final AvailObject object, 
			final int value)
	{
		object.integerSlotAtByteIndexPut(4, value);
	}

	/**
	 * Setter for field !I!nterruptRequestFlag.
	 */
	@Override
	public void ObjectInterruptRequestFlag (
			final AvailObject object, 
			final int value)
	{
		object.integerSlotAtByteIndexPut(20, value);
	}

	/**
	 * Setter for field !P!riority.
	 */
	@Override
	public void ObjectPriority (
			final AvailObject object, 
			final int value)
	{
		object.integerSlotAtByteIndexPut(8, value);
	}

	/**
	 * Setter for field !P!rocessGlobals.
	 */
	@Override
	public void ObjectProcessGlobals (
			final AvailObject object, 
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-12, value);
	}

	/**
	 * Getter for field !B!reakpointBlock.
	 */
	@Override
	public AvailObject ObjectBreakpointBlock (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-8);
	}

	/**
	 * Getter for field !C!ontinuation.
	 */
	@Override
	public AvailObject ObjectContinuation (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}

	/**
	 * Getter for field !E!xecutionMode.
	 */
	@Override
	public int ObjectExecutionMode (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(12);
	}

	/**
	 * Getter for field !E!xecutionState.
	 */
	@Override
	public int ObjectExecutionState (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(16);
	}

	/**
	 * Getter for field !H!ashOrZero.
	 */
	@Override
	public int ObjectHashOrZero (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(4);
	}

	/**
	 * Getter for field !I!nterruptRequestFlag.
	 */
	@Override
	public int ObjectInterruptRequestFlag (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(20);
	}

	/**
	 * Getter for field !P!riority.
	 */
	@Override
	public int ObjectPriority (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(8);
	}

	/**
	 * Getter for field !P!rocessGlobals.
	 */
	@Override
	public AvailObject ObjectProcessGlobals (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-12);
	}



	// GENERATED special mutable slots

	@Override
	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if (index == -4)
		{
			return true;
		}
		if (index == -8)
		{
			return true;
		}
		if (index == -12)
		{
			return true;
		}
		if (index == 4)
		{
			return true;
		}
		if (index == 8)
		{
			return true;
		}
		if (index == 12)
		{
			return true;
		}
		if (index == 16)
		{
			return true;
		}
		if (index == 20)
		{
			return true;
		}
		return false;
	}



	// operations

	@Override
	public boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Compare processes by address (identity).

		return another.traversed().sameAddressAs(object);
	}

	@Override
	public AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.process.object();
	}

	@Override
	public int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value.

		int hash = object.hashOrZero();
		while (hash == 0)
		{
			hash = hashGenerator.nextInt();
		}
		object.hashOrZero(hash);
		return hash;
	}

	@Override
	public AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  If I am being frozen (a container), I don't need to freeze my current value.
		//  I do, on the other hand, have to freeze my type object.
		//
		//  Do nothing.  My subobjects are all allowed to be mutable even if I'm immutable.

		object.descriptor(ProcessDescriptor.immutableDescriptor());
		return object;
	}

	@Override
	public AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.process.object();
	}



	// operations-processes

	@Override
	public int ObjectGetInteger (
			final AvailObject object)
	{
		//  Answer an integer extracted at the current program counter from the continuation.  The program
		//  counter will be adjusted to skip over the integer.  Use a totally naive implementation for now, with
		//  very little caching.

		AvailObject contObject = object.continuation();
		int pc = contObject.pc();
		AvailObject nybblesObject = contObject.nybbles();
		byte firstNybble = nybblesObject.extractNybbleFromTupleAt(pc);
		int result = firstNybble;
		switch (firstNybble)
		{
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
				pc ++;
				break;
			case 10:
				result = nybblesObject.extractNybbleFromTupleAt(pc+1) + 10;
				pc += 2;
				break;
			case 11:
				result = nybblesObject.extractNybbleFromTupleAt(pc+1) + 26;
				pc += 2;
				break;
			case 12:
				result = nybblesObject.extractNybbleFromTupleAt(pc+1) + 42;
				pc += 2;
				break;
			case 13:
				result = nybblesObject.extractNybbleFromTupleAt(pc+1) * 4 + nybblesObject.extractNybbleFromTupleAt(pc+2) + 58;
				pc += 3;
				break;
			case 14:
				result = 0;
				{
					for (int i = 1; i <= 4; ++ i)
						result = result * 16 + nybblesObject.extractNybbleFromTupleAt(pc+i) + 10;
				}
				pc += 5;
				break;
			case 15:
				result = 0;
				{
					for (int i = 1; i <= 8; ++ i)
						result = result * 16 + nybblesObject.extractNybbleFromTupleAt(pc+i) + 10;
				}
				pc += 9;
				break;
			default:
				error("Hey, that's not a nybble!");
		};
		contObject.pc(pc);
		return result;
	}

	@Override
	public void ObjectStep (
			final AvailObject object)
	{
		//  Execute one step of the process.

		error("Process stepping is not implemented");
	}

	private static Random hashGenerator = new Random();

	/**
	 * Construct a new {@link ProcessDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param numberOfFixedObjectSlots
	 *        The number of fixed {@linkplain AvailObject object} slots.
	 * @param numberOfFixedIntegerSlots The number of fixed integer slots.
	 * @param hasVariableObjectSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable object slots?
	 * @param hasVariableIntegerSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable integer slots?
	 */
	protected ProcessDescriptor (
		final int myId,
		final boolean isMutable,
		final int numberOfFixedObjectSlots,
		final int numberOfFixedIntegerSlots,
		final boolean hasVariableObjectSlots,
		final boolean hasVariableIntegerSlots)
	{
		super(
			myId,
			isMutable,
			numberOfFixedObjectSlots,
			numberOfFixedIntegerSlots,
			hasVariableObjectSlots,
			hasVariableIntegerSlots);
	}

	public static ProcessDescriptor mutableDescriptor()
	{
		return (ProcessDescriptor) allDescriptors [140];
	}

	public static ProcessDescriptor immutableDescriptor()
	{
		return (ProcessDescriptor) allDescriptors [141];
	}
}
