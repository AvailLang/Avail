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

import static com.avail.descriptor.AvailObject.error;
import java.util.Random;
import com.avail.descriptor.TypeDescriptor.Types;

public class ProcessDescriptor extends Descriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		HASH_OR_ZERO,
		PRIORITY,
		EXECUTION_MODE,
		EXECUTION_STATE,
		INTERRUPT_REQUEST_FLAG
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		CONTINUATION,
		BREAKPOINT_BLOCK,
		PROCESS_GLOBALS
	}


	// GENERATED accessors

	/**
	 * Setter for field breakpointBlock.
	 */
	@Override
	public void o_BreakpointBlock (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.BREAKPOINT_BLOCK, value);
	}

	/**
	 * Setter for field continuation.
	 */
	@Override
	public void o_Continuation (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CONTINUATION, value);
	}

	/**
	 * Setter for field executionMode.
	 */
	@Override
	public void o_ExecutionMode (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.EXECUTION_MODE, value);
	}

	/**
	 * Setter for field executionState.
	 */
	@Override
	public void o_ExecutionState (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.EXECUTION_STATE, value);
	}

	/**
	 * Setter for field hashOrZero.
	 */
	@Override
	public void o_HashOrZero (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.HASH_OR_ZERO, value);
	}

	/**
	 * Setter for field interruptRequestFlag.
	 */
	@Override
	public void o_InterruptRequestFlag (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.INTERRUPT_REQUEST_FLAG, value);
	}

	/**
	 * Setter for field priority.
	 */
	@Override
	public void o_Priority (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.PRIORITY, value);
	}

	/**
	 * Setter for field processGlobals.
	 */
	@Override
	public void o_ProcessGlobals (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.PROCESS_GLOBALS, value);
	}

	/**
	 * Getter for field breakpointBlock.
	 */
	@Override
	public AvailObject o_BreakpointBlock (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.BREAKPOINT_BLOCK);
	}

	/**
	 * Getter for field continuation.
	 */
	@Override
	public AvailObject o_Continuation (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CONTINUATION);
	}

	/**
	 * Getter for field executionMode.
	 */
	@Override
	public int o_ExecutionMode (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.EXECUTION_MODE);
	}

	/**
	 * Getter for field executionState.
	 */
	@Override
	public int o_ExecutionState (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.EXECUTION_STATE);
	}

	/**
	 * Getter for field hashOrZero.
	 */
	@Override
	public int o_HashOrZero (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HASH_OR_ZERO);
	}

	/**
	 * Getter for field interruptRequestFlag.
	 */
	@Override
	public int o_InterruptRequestFlag (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.INTERRUPT_REQUEST_FLAG);
	}

	/**
	 * Getter for field priority.
	 */
	@Override
	public int o_Priority (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.PRIORITY);
	}

	/**
	 * Getter for field processGlobals.
	 */
	@Override
	public AvailObject o_ProcessGlobals (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.PROCESS_GLOBALS);
	}



	@Override
	public boolean allowsImmutableToMutableReferenceInField (
			final Enum<?> e)
	{
		return e == ObjectSlots.CONTINUATION
			|| e == ObjectSlots.BREAKPOINT_BLOCK
			|| e == ObjectSlots.PROCESS_GLOBALS
			|| e == IntegerSlots.HASH_OR_ZERO
			|| e == IntegerSlots.PRIORITY
			|| e == IntegerSlots.EXECUTION_MODE
			|| e == IntegerSlots.EXECUTION_STATE
			|| e == IntegerSlots.INTERRUPT_REQUEST_FLAG;
	}



	// operations

	@Override
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		//  Compare processes by address (identity).

		return another.traversed().sameAddressAs(object);
	}

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.PROCESS.o();
	}

	@Override
	public int o_Hash (
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
	public AvailObject o_MakeImmutable (
			final AvailObject object)
	{
		//  If I am being frozen (a container), I don't need to freeze my current value.
		//  I do, on the other hand, have to freeze my type object.
		//
		//  Do nothing.  My subobjects are all allowed to be mutable even if I'm immutable.

		object.descriptor(ProcessDescriptor.immutable());
		return object;
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		//  Answer the object's type.

		return Types.PROCESS.o();
	}


	/**
	 * Answer an integer extracted at the current program counter from the
	 * continuation.  The program counter will be adjusted to skip over the
	 * integer.  Use a totally naive implementation for now, with very little
	 * caching.
	 */

	@Override
	public int o_GetInteger (
			final AvailObject object)
	{
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
			case 11:
			case 12:
				// (x-10)*16+10+y
				result = (result << 4)
					- 150
					+ nybblesObject.extractNybbleFromTupleAt(pc+1);
				pc += 2;
				break;
			case 13:
				result = (nybblesObject.extractNybbleFromTupleAt(pc+1) << 4)
					+ nybblesObject.extractNybbleFromTupleAt(pc+2) + 58;
				pc += 3;
				break;
			case 14:
				result = 0;
				{
					for (int i = 1; i <= 4; ++ i)
					{
						result = result * 16
							+ nybblesObject.extractNybbleFromTupleAt(pc+i);
					}
				}
				pc += 5;
				break;
			case 15:
				result = 0;
				{
					for (int i = 1; i <= 8; ++ i)
					{
						result = result * 16
							+ nybblesObject.extractNybbleFromTupleAt(pc+i);
					}
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
	public void o_Step (
			final AvailObject object)
	{
		//  Execute one step of the process.

		error("Process stepping is not implemented");
	}

	/**
	 * A random generator used for creating hash values as needed.
	 */
	private static Random hashGenerator = new Random();

	/**
	 * Construct a new {@link ProcessDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ProcessDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ProcessDescriptor}.
	 */
	private final static ProcessDescriptor mutable = new ProcessDescriptor(true);

	/**
	 * Answer the mutable {@link ProcessDescriptor}.
	 *
	 * @return The mutable {@link ProcessDescriptor}.
	 */
	public static ProcessDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ProcessDescriptor}.
	 */
	private final static ProcessDescriptor immutable = new ProcessDescriptor(false);

	/**
	 * Answer the immutable {@link ProcessDescriptor}.
	 *
	 * @return The immutable {@link ProcessDescriptor}.
	 */
	public static ProcessDescriptor immutable ()
	{
		return immutable;
	}
}
