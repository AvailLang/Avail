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
import com.avail.descriptor.TypeDescriptor;
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

	void ObjectBreakpointBlock (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-8, value);
	}

	void ObjectContinuation (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-4, value);
	}

	void ObjectExecutionMode (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(12, value);
	}

	void ObjectExecutionState (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(16, value);
	}

	void ObjectHashOrZero (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(4, value);
	}

	void ObjectInterruptRequestFlag (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(20, value);
	}

	void ObjectPriority (
			final AvailObject object, 
			final int value)
	{
		//  GENERATED setter method.

		object.integerSlotAtByteIndexPut(8, value);
	}

	void ObjectProcessGlobals (
			final AvailObject object, 
			final AvailObject value)
	{
		//  GENERATED setter method.

		object.objectSlotAtByteIndexPut(-12, value);
	}

	AvailObject ObjectBreakpointBlock (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-8);
	}

	AvailObject ObjectContinuation (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-4);
	}

	int ObjectExecutionMode (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(12);
	}

	int ObjectExecutionState (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(16);
	}

	int ObjectHashOrZero (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(4);
	}

	int ObjectInterruptRequestFlag (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(20);
	}

	int ObjectPriority (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.integerSlotAtByteIndex(8);
	}

	AvailObject ObjectProcessGlobals (
			final AvailObject object)
	{
		//  GENERATED getter method.

		return object.objectSlotAtByteIndex(-12);
	}



	// GENERATED special mutable slots

	boolean allowsImmutableToMutableReferenceAtByteIndex (
			final int index)
	{
		//  GENERATED special mutable slots method.

		if ((index == -4))
		{
			return true;
		}
		if ((index == -8))
		{
			return true;
		}
		if ((index == -12))
		{
			return true;
		}
		if ((index == 4))
		{
			return true;
		}
		if ((index == 8))
		{
			return true;
		}
		if ((index == 12))
		{
			return true;
		}
		if ((index == 16))
		{
			return true;
		}
		if ((index == 20))
		{
			return true;
		}
		return false;
	}



	// operations

	boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		//  Compare processes by address (identity).

		return another.traversed().sameAddressAs(object);
	}

	AvailObject ObjectExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.process();
	}

	int ObjectHash (
			final AvailObject object)
	{
		//  Answer a 32-bit hash value.

		int hash = object.hashOrZero();
		while (hash == 0)
		{
			hash = (hashGenerator.nextInt()) & HashMask;
		}
		object.hashOrZero(hash);
		return hash;
	}

	AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  If I am being frozen (a container), I don't need to freeze my current value.
		//  I do, on the other hand, have to freeze my type object.
		//
		//  Do nothing.  My subobjects are all allowed to be mutable even if I'm immutable.

		object.descriptor(ProcessDescriptor.immutableDescriptor());
		return object;
	}

	AvailObject ObjectType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return TypeDescriptor.process();
	}



	// operations-processes

	int ObjectGetInteger (
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

	void ObjectStep (
			final AvailObject object)
	{
		//  Execute one step of the process.

		error("Process stepping is not implemented");
	}





	static Random hashGenerator = new Random();


	/* Descriptor lookup */
	public static ProcessDescriptor mutableDescriptor()
	{
		return (ProcessDescriptor) allDescriptors [140];
	};
	public static ProcessDescriptor immutableDescriptor()
	{
		return (ProcessDescriptor) allDescriptors [141];
	};

}
