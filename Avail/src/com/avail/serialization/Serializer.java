/**
 * Serializer.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.serialization;

import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.utility.Continuation0;
import java.io.*;
import java.util.*;

/**
 * A {@code Serializer} converts a series of objects passed individually to
 * {@link #serialize(AvailObject)} into a stream of bytes which, when replayed
 * in a {@link Deserializer}, will reconstruct an analogous series of objects.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class Serializer
{
	/**
	 * The inverse of the {@link AvailRuntime}'s {@linkplain
	 * AvailRuntime#specialObjects() special objects} list.  Entries that are
	 * {@code null} (i.e., unused entries} are not included.
	 */
	static final Map<AvailObject, Integer> specialObjects =
		new HashMap<AvailObject, Integer>(1000);

	/**
	 * Special system {@link AtomDescriptor atoms} that aren't already in the
	 * list of {@linkplain AvailRuntime#specialAtoms() special atoms}.
	 */
	static final Map<AvailObject, Integer> specialAtoms =
		new HashMap<AvailObject, Integer>(100);

	/**
	 * This keeps track of all objects that have been encountered.  It's a map
	 * from each {@link AvailObject} to the {@link SerializerInstruction} that
	 * will be output for it at the appropriate time.
	 */
	final Map<AvailObject, SerializerInstruction> encounteredObjects =
		new HashMap<AvailObject, SerializerInstruction>(1000);

	/**
	 * The number of instructions that have been written to the {@link #output}.
	 */
	int instructionsWritten = 0;

	/**
	 * This maintains a stack of {@linkplain SerializerInstruction serializer
	 * instructions} that need to be processed.  It's a stack to ensure depth
	 * first writing of instructions before their parents.  This mechanism
	 * avoids using Java's limited stack, since Avail structures may in theory
	 * be exceptionally deep.  TODO
	 */
	final Deque<Continuation0> workStack =
		new ArrayDeque<Continuation0>(1000);

	/**
	 * The {@link OutputStream} on which to write the serialized objects.
	 */
	final @NotNull OutputStream output;

	/**
	 * Output an unsigned byte.  It must be in the range 0 ≤ n ≤ 255.
	 *
	 * @param byteValue The unsigned byte to output, as an {@code int},
	 */
	void writeByte (final int byteValue)
	{
		assert (byteValue & 255) == byteValue;
		try
		{
			output.write(byteValue);
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Output an unsigned short.  It must be in the range 0 ≤ n ≤ 65535.  Use
	 * big endian order.
	 *
	 * @param shortValue The unsigned short to output, as a {@code short}.
	 */
	void writeShort (final int shortValue)
	{
		assert (shortValue & 0xFFFF) == shortValue;
		try
		{
			output.write(shortValue>>8);
			output.write(shortValue);
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Output an int.  Use big endian order.
	 *
	 * @param intValue The {@code int} to output.
	 */
	void writeInt (final int intValue)
	{
		try
		{
			output.write(intValue>>24);
			output.write(intValue>>16);
			output.write(intValue>>8);
			output.write(intValue);
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Look up the object.  If it is already in the {@link #encounteredObjects}
	 * list, answer the corresponding {@link SerializerInstruction}.
	 *
	 * @param object The object to look up.
	 * @return The object's zero-based index in {@code encounteredObjects}.
	 */
	SerializerInstruction instructionForObject (
		final @NotNull AvailObject object)
	{
		return encounteredObjects.get(object);
	}

	/**
	 * Look up the object and return the existing instruction that produces it.
	 * The instruction must have an index other than -1, which indicates that
	 * the instruction has not yet been written; that is, the instruction must
	 * already have been written.
	 *
	 * @param object
	 *            The object to look up.
	 * @return
	 *            The (non-negative) index of the instruction that produced the
	 *            object.
	 */
	int indexOfExistingObject (
		final @NotNull AvailObject object)
	{
		final SerializerInstruction instruction =
			encounteredObjects.get(object);
		assert instruction.hasBeenWritten();
		final int index = instruction.index();
		return index;
	}

	/**
	 * Look up the object.  If it is a {@linkplain AvailRuntime#specialObjects()
	 * special object}, then answer which special object it is, otherwise answer
	 * -1.
	 *
	 * @param object The object to look up.
	 * @return The object's zero-based index in {@code encounteredObjects}.
	 */
	static int indexOfSpecialObject (
		final @NotNull AvailObject object)
	{
		final Integer index = specialObjects.get(object);
		if (index == null)
		{
			return -1;
		}
		return index;
	}

	/**
	 * Look up the object.  If it is a {@linkplain AvailRuntime#specialAtoms()
	 * special atom}, then answer which special atom it is, otherwise answer
	 * -1.
	 *
	 * @param object The object to look up.
	 * @return The object's zero-based index in {@code encounteredObjects}.
	 */
	static int indexOfSpecialAtom (
		final @NotNull AvailObject object)
	{
		final Integer index = specialAtoms.get(object);
		if (index == null)
		{
			return -1;
		}
		return index;
	}

	/**
	 * Trace an object, ensuring that it and its subobjects will be written out
	 * in the correct order during actual serialization.  Use the {@link
	 * #workStack} rather than recursion to avoid Java stack overflow for deep
	 * Avail structures.
	 *
	 * <p>
	 * To trace an object X with children Y and Z, first push onto the workstack
	 * an action (a {@link Continuation0}) which will write X's {@link
	 * SerializerInstruction}.  Then examine X to discover Y and Z, pushing
	 * {@code Continuation0}s which will trace Y then trace Z.  Since those will
	 * be processed completely before the first action gets a chance to run
	 * (i.e., to generate the instruction for X), we ensure Y and Z are always
	 * created before X.  Note that the continuation to trace Y must check if Y
	 * has already been traced, since Z might recursively contain a reference to
	 * Y, leading to Y needing to be traced prior to Z.
	 * </p>
	 *
	 * @param object The object to trace.
	 */
	void traceOne (
		final @NotNull AvailObject object)
	{
		final SerializerInstruction instruction;
		if (encounteredObjects.containsKey(object))
		{
			instruction = encounteredObjects.get(object);
		}
		else
		{
			// Build but don't yet emit the instruction.
			final SerializerOperation operation;
			final Integer specialObjectIndex = specialObjects.get(object);
			if (specialObjectIndex != null)
			{
				operation = SerializerOperation.SPECIAL_OBJECT;
			}
			else
			{
				final Integer specialAtomIndex = specialAtoms.get(object);
				if (specialAtomIndex != null)
				{
					operation = SerializerOperation.SPECIAL_ATOM;
				}
				else
				{
					operation = object.serializerOperation();
				}
			}
			instruction = new SerializerInstruction(
				object,
				operation);
			encounteredObjects.put(object, instruction);
		}
		// Do nothing if the object's instruction has already been emitted.
		if (!instruction.hasBeenWritten())
		{
			// The object has not yet been traced.  (1) Stack an action that
			// will assemble the object after the parts have been assembled,
			// then (2) stack actions to ensure the parts have been assembled.
			// Note that we have to add these actions even if we've already
			// stacked equivalent actions, since it's the last one we push that
			// will cause the instruction to be emitted.
			workStack.addLast(new Continuation0()
			{
				@Override
				public void value ()
				{
					if (!instruction.hasBeenWritten())
					{
						instruction.index(instructionsWritten);
						instructionsWritten++;
						instruction.writeTo(Serializer.this);
						assert instruction.hasBeenWritten();
					}
				}
			});
			// Push actions for the subcomponents in reverse order to make the
			// serialized file slightly easier to debug.  Any order is correct.
			final AvailObject[] subobjects = instruction.decomposed();
			final SerializerOperand[] operands =
				instruction.operation().operands();
			assert subobjects.length == operands.length;
			for (int i = operands.length - 1; i >= 0; i--)
			{
				final int index = i;
				workStack.addLast(new Continuation0()
				{
					@Override
					public void value ()
					{
						operands[index].trace(
							subobjects[index],
							Serializer.this);
					}
				});
			}
		}
	}


	/**
	 * Create any cached {@link AvailObject}s.
	 */
	public static void createWellKnownObjects ()
	{
		// Build the inverse of AvailRuntime#specialObjects().
		final List<AvailObject> objectList = AvailRuntime.specialObjects();
		for (int i = 0; i < objectList.size(); i++)
		{
			final AvailObject specialObject = objectList.get(i);
			if (specialObject != null)
			{
				specialObjects.put(specialObject, i);
			}
		}
		// And build the inverse of AvailRuntime#specialAtoms().
		final List<AvailObject> atomList = AvailRuntime.specialAtoms();
		for (int i = 0; i < atomList.size(); i++)
		{
			final AvailObject specialAtom = atomList.get(i);
			if (specialAtom != null)
			{
				specialAtoms.put(specialAtom, i);
			}
		}
	}

	/**
	 * Release all references to {@link AvailObject}s held by this class.
	 */
	public static void clearWellKnownObjects ()
	{
		specialObjects.clear();
		specialAtoms.clear();
	}


	/**
	 * Construct a new {@link Serializer}.
	 *
	 * @param output An {@link OutputStream} on which to write the module.
	 */
	public Serializer (
		final @NotNull OutputStream output)
	{
		this.output = output;
	}

	/**
	 * Serialize this {@link AvailObject} so that it will appear as the next
	 * checkpoint object during deserialization.
	 *
	 * @param object An object to serialize.
	 */
	public void serialize (
		final @NotNull AvailObject object)
	{
		traceOne(object);
		while (!workStack.isEmpty())
		{
			workStack.removeLast().value();
		}
		final SerializerInstruction checkpoint = new SerializerInstruction(
			object,
			SerializerOperation.CHECKPOINT);
		checkpoint.index(instructionsWritten);
		instructionsWritten++;
		checkpoint.writeTo(this);
		assert checkpoint.hasBeenWritten();
	}
}
