/*
 * Serializer.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.serialization

import com.avail.AvailRuntime
import com.avail.descriptor.*
import java.io.IOException
import java.io.OutputStream
import java.util.*

/**
 * A `Serializer` converts a series of objects passed individually to
 * [serialize] into a stream of bytes which, when replayed in a [Deserializer],
 * will reconstruct an analogous series of objects.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class Serializer
{
	/**
	 * This keeps track of all objects that have been encountered.  It's a map
	 * from each [AvailObject] to the [SerializerInstruction] that will be
	 * output for it at the appropriate time.
	 */
	private val encounteredObjects:
		MutableMap<A_BasicObject, SerializerInstruction> = HashMap(100)

	/**
	 * All variables that must have their values assigned to them upon
	 * deserialization.  The set is cleared at every checkpoint.
	 */
	private val variablesToAssign = HashSet<A_Variable>(100)

	/** The number of instructions that have been written to the [output]. */
	private var instructionsWritten = 0

	/**
	 * This maintains a stack of [serializer][SerializerInstruction] that need
	 * to be processed.  It's a stack to ensure depth first writing of
	 * instructions before their parents.  This mechanism avoids using the JVM's
	 * limited stack, since Avail structures may in theory be exceptionally
	 * deep.
	 */
	private val workStack = ArrayDeque<() -> Unit>(1000)

	/** The [OutputStream] on which to write the serialized objects. */
	private val output: OutputStream

	/**
	 * The module within which serialization is occurring.  If non-null, it is
	 * used to detect capture of atoms that are not defined in ancestor modules.
	 */
	val module: A_Module?

	/**
	 * Check that the atom is defined in the ancestry of the current module, if
	 * any.  Fail if it isn't.
	 *
	 * @param atom
	 *   The [A_Atom] to check.
	 */
	internal fun checkAtom(atom: A_Atom)
	{
		if (module === null)
		{
			return
		}
		val atomModule = atom.issuingModule()
		if (atomModule.equalsNil())
		{
			return
		}
		assert(module.allAncestors().hasElement(atomModule))
	}

	/**
	 * Output an unsigned byte.  It must be in the range 0 ≤ n ≤ 255.
	 *
	 * @param byteValue
	 *   The unsigned byte to output, as an `int`,
	 */
	internal fun writeByte(byteValue: Int)
	{
		assert(byteValue and 255 == byteValue)
		try
		{
			output.write(byteValue)
		}
		catch (e: IOException)
		{
			throw RuntimeException(e)
		}
	}

	/**
	 * Output an unsigned short.  It must be in the range 0 ≤ n ≤ 65535.  Use
	 * big endian order.
	 *
	 * @param shortValue
	 *   The unsigned short to output, as a `short`.
	 */
	internal fun writeShort(shortValue: Int)
	{
		assert(shortValue and 0xFFFF == shortValue)
		try
		{
			output.write(shortValue shr 8)
			output.write(shortValue)
		}
		catch (e: IOException)
		{
			throw RuntimeException(e)
		}
	}

	/**
	 * Output an int.  Use big endian order.
	 *
	 * @param intValue
	 *   The `int` to output.
	 */
	internal fun writeInt(intValue: Int)
	{
		try
		{
			output.write(intValue shr 24)
			output.write(intValue shr 16)
			output.write(intValue shr 8)
			output.write(intValue)
		}
		catch (e: IOException)
		{
			throw RuntimeException(e)
		}
	}

	/**
	 * Look up the object.  If it is already in the [encounteredObjects] list,
	 * answer the corresponding [SerializerInstruction].
	 *
	 * @param object
	 *   The object to look up.
	 * @return
	 *   The object's zero-based index in `encounteredObjects`.
	 */
	internal fun instructionForObject(
			obj: A_BasicObject): SerializerInstruction =
		encounteredObjects[obj]!!

	/**
	 * Look up the object and return the existing instruction that produces it.
	 * The instruction must have an index other than -1, which indicates that
	 * the instruction has not yet been written; that is, the instruction must
	 * already have been written.
	 *
	 * @param object
	 *   The object to look up.
	 * @return
	 *   The (non-negative) index of the instruction that produced the object.
	 */
	internal fun indexOfExistingObject(obj: A_BasicObject): Int
	{
		val instruction = encounteredObjects[obj]!!
		assert(instruction.hasBeenWritten)
		return instruction.index
	}

	/**
	 * Trace the object and answer, but don't emit, a [SerializerInstruction]
	 * suitable for adding to the [encounteredObjects] [Map].
	 *
	 * @param object
	 *   The [A_BasicObject] to trace.
	 * @return
	 *   The new [SerializerInstruction].
	 */
	private fun newInstruction(obj: A_BasicObject): SerializerInstruction
	{
		return SerializerInstruction(
			if (specialObjects.containsKey(obj))
				SerializerOperation.SPECIAL_OBJECT
			else
				obj.serializerOperation(),
			obj,
			this)
	}

	/**
	 * Trace an object, ensuring that it and its subobjects will be written out
	 * in the correct order during actual serialization.  Use the [workStack]
	 * rather than recursion to avoid JVM stack overflow for deep Avail
	 * structures.
	 *
	 * To trace an object X with children Y and Z, first push onto the work
	 * stack an action which will write X's [SerializerInstruction].  Then
	 * examine X to discover Y and Z, pushing continuations which will trace Y
	 * then trace Z.  Since those will be processed completely before the first
	 * action gets a chance to run (i.e., to generate the instruction for X), we
	 * ensure Y and Z are always created before X.  Note that the continuation
	 * to trace Y must check if Y has already been traced, since Z might
	 * recursively contain a reference to Y, leading to Y needing to be traced
	 * prior to Z.
	 *
	 * @param object
	 *   The object to trace.
	 */
	internal fun traceOne(obj: AvailObject)
	{
		// Build but don't yet emit the instruction.
		val instruction =
			encounteredObjects.computeIfAbsent(obj) {
				newInstruction(it)
			}
		// Do nothing if the object's instruction has already been emitted.
		if (!instruction.hasBeenWritten)
		{
			// The object has not yet been traced.  (1) Stack an action that
			// will assemble the object after the parts have been assembled,
			// then (2) stack actions to ensure the parts have been assembled.
			// Note that we have to add these actions even if we've already
			// stacked equivalent actions, since it's the last one we push that
			// will cause the instruction to be emitted.
			workStack.addLast {
				if (!instruction.hasBeenWritten)
				{
					instruction.index = instructionsWritten++
					instruction.writeTo(this@Serializer)
					assert(instruction.hasBeenWritten)
				}
			}
			// Push actions for the subcomponents in reverse order to make the
			// serialized file slightly easier to debug.  Any order is correct.
			val operands = instruction.operation.operands
			assert(instruction.subobjectsCount == operands.size)
			for (i in instruction.subobjectsCount - 1 downTo 0)
			{
				val operand = operands[i]
				val operandValue = instruction.getSubobject(i)
				workStack.addLast {
					operand.trace(operandValue as AvailObject, this)
				}
			}
			if (instruction.operation.isVariableCreation
				&& !obj.value().equalsNil())
			{
				variablesToAssign.add(obj)
				// Output an action to the *start* of the workStack to trace the
				// variable's value.  This prevents recursion, but ensures that
				// everything reachable, including through variables, will be
				// traced.
				workStack.addFirst { traceOne(obj.value()) }
			}
		}
	}

	/**
	 * Construct a new `Serializer`.
	 *
	 * @param output
	 *   An [OutputStream] on which to write the module.
	 * @param module
	 *   The [A_Module] being compiled.
	 */
	constructor(output: OutputStream, module: A_Module)
	{
		this.output = output
		this.module = module
	}

	/**
	 * Construct a new `Serializer`.
	 *
	 * @param output
	 *   An [OutputStream] on which to write the module.
	 */
	constructor(output: OutputStream)
	{
		this.output = output
		this.module = null
	}

	/**
	 * Serialize this [AvailObject] so that it will appear as the next
	 * checkpoint object during deserialization.
	 *
	 * @param object
	 *   An object to serialize.
	 */
	fun serialize(obj: A_BasicObject)
	{
		val strongObject = obj as AvailObject
		traceOne(strongObject)
		while (!workStack.isEmpty())
		{
			workStack.removeLast()()
		}
		// Next, do all variable assignments...
		for (variable in variablesToAssign)
		{
			assert(!variable.value().equalsNil())
			val assignment = SerializerInstruction(
				SerializerOperation.ASSIGN_TO_VARIABLE,
				variable,
				this)
			assignment.index = instructionsWritten
			instructionsWritten++
			assignment.writeTo(this)
			assert(assignment.hasBeenWritten)
		}
		variablesToAssign.clear()
		// Finally, write a checkpoint to say there's something ready for the
		// deserializer to answer.
		val checkpoint = SerializerInstruction(
			SerializerOperation.CHECKPOINT,
			strongObject,
			this)
		checkpoint.index = instructionsWritten
		instructionsWritten++
		checkpoint.writeTo(this)
		assert(checkpoint.hasBeenWritten)
	}

	companion object
	{
		/**
		 * The inverse of the [AvailRuntime]'s [special
		 * objects][AvailRuntime.specialObjects] list.  Entries that are `null`
		 * (i.e., unused entries} are not included.
		 */
		private val specialObjects = HashMap<A_BasicObject, Int>(1000)

		/**
		 * Special system [atoms][AtomDescriptor] that aren't already in the
		 * list of [special atoms][AvailRuntime.specialAtoms].
		 */
		private val specialAtoms = HashMap<A_Atom, Int>(100)

		/**
		 * Special system [atoms][AtomDescriptor] that aren't already in the
		 * list of [special atoms][AvailRuntime.specialAtoms], keyed by their
		 * [A_String], where the value is the [A_Atom] itself.
		 */
		internal val specialAtomsByName: MutableMap<A_String, A_Atom> =
			HashMap(100)

		/**
		 * Look up the object.  If it is a [special
		 * object][AvailRuntime.specialObjects], then answer which special
		 * object it is, otherwise answer -1.
		 *
		 * @param object
		 *   The object to look up.
		 * @return
		 *   The object's zero-based index in `encounteredObjects`.
		 */
		internal fun indexOfSpecialObject(obj: A_BasicObject): Int =
			specialObjects[obj] ?: -1

		/**
		 * Look up the object.  If it is a [special
		 * atom][AvailRuntime.specialAtoms], then answer which special atom it
		 * is, otherwise answer -1.
		 *
		 * @param
		 *   object The object to look up.
		 * @return
		 *   The object's zero-based index in `encounteredObjects`.
		 */
		internal fun indexOfSpecialAtom(obj: A_Atom): Int =
			specialAtoms[obj] ?: -1

		init
		{
			// Build the inverse of AvailRuntime#specialObjects().
			val objectList = AvailRuntime.specialObjects()
			for (i in objectList.indices)
			{
				val specialObject = objectList[i]
				if (specialObject !== null)
				{
					specialObjects[specialObject] = i
				}
			}
			// And build the inverse of AvailRuntime#specialAtoms().
			val atomList = AvailRuntime.specialAtoms()
			for (i in atomList.indices)
			{
				val specialAtom = atomList[i]
				if (specialAtom !== null)
				{
					specialAtoms[specialAtom] = i
					specialAtomsByName[specialAtom.atomName()] = specialAtom
				}
			}
		}
	}
}
