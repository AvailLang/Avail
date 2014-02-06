/**
 * SerializerInstruction.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import com.avail.descriptor.*;

/**
 * A {@code SerializerInstruction} combines an {@link AvailObject} and a
 * {@link SerializerOperation} suitable for serializing it.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class SerializerInstruction
{
	/**
	 * The {@link AvailObject} to be serialized.
	 */
	final AvailObject object;

	/**
	 * Answer the {@link AvailObject} that this instruction is serializing.
	 *
	 * @return The AvailObject to serialize.
	 */
	AvailObject object ()
	{
		return object;
	}

	/**
	 * The index of this instruction in the list of instructions produced by a
	 * {@link Serializer}.
	 */
	int index = -1;

	/**
	 * Set this instruction's absolute index in its {@link Serializer}'s
	 * list of instructions.
	 *
	 * @param theIndex The instruction's index.
	 */
	void index (final int theIndex)
	{
		assert index == -1;
		index = theIndex;
	}

	/**
	 * Answer this instruction's absolute index in its {@link Serializer}'s
	 * list of instructions.
	 *
	 * @return The instruction's index.
	 */
	int index ()
	{
		return index;
	}

	/**
	 * Answer whether this instruction has been assigned an instruction index,
	 * which happens when the instruction is written.
	 *
	 * @return Whether this instruction has been written.
	 */
	boolean hasBeenWritten ()
	{
		return index >= 0;
	}

	/**
	 * The {@link SerializerOperation} that can decompose this object for
	 * serialization.
	 */
	final SerializerOperation operation;

	/**
	 * Answer the {@link SerializerOperation} that will serialize the object.
	 *
	 * @return The {@code SerializerOperation} used to decompose the object.
	 */
	SerializerOperation operation ()
	{
		return operation;
	}

	/**
	 * Answer an array of {@link AvailObject}s that correspond with my
	 * operation's {@link SerializerOperand operands}.  These may contain
	 * subobjects that must be serialized before me, but its up to each operand
	 * to determine that, as well as the encoding mechanism.
	 *
	 * @return The array of {@code AvailObject}s for my operation's operands to
	 *         interpret.
	 */
	A_BasicObject[] decomposed ()
	{
		return operation.decompose(object);
	}

	/**
	 * Write this already traced instruction to the {@link Serializer}.
	 *
	 * @param serializer Where to write the instruction.
	 */
	void writeTo (final Serializer serializer)
	{
		operation.writeObject(object, serializer);
	}

	/**
	 * Construct a new {@link SerializerInstruction}.
	 *
	 * @param object
	 *            The {@link AvailObject} to serialize.
	 * @param operation
	 *            The {@link SerializerOperation} that will decompose the object
	 *            for serialization.
	 */
	public SerializerInstruction (
		final AvailObject object,
		final SerializerOperation operation)
	{
		this.object = object;
		this.operation = operation;
	}
}
