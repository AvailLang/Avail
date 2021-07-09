/*
 * Serializer.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.atoms.A_Atom.Companion.issuingModule
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.module.A_Module.Companion.hasAncestor
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.variables.A_Variable
import com.avail.serialization.SerializerOperation.ASSIGN_TO_VARIABLE
import com.avail.serialization.SerializerOperation.CHECKPOINT
import com.avail.serialization.SerializerOperation.SPECIAL_OBJECT
import java.io.IOException
import java.io.OutputStream
import java.util.ArrayDeque

/**
 * A `Serializer` converts a series of objects passed individually to
 * [serialize] into a stream of bytes which, when replayed in a [Deserializer],
 * will reconstruct an analogous series of objects.
 *
 * The serializer is also provided a function for recognizing objects that do
 * not need to be scanned because they're explicitly provided.  These objects
 * are numbered with negative indices.  The inverse of this function needs to be
 * provided to the [Deserializer], so that negative indices can be converted to
 * objects.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *   Construct a [Serializer] which converts a series of objects into bytes.
 * @property output
 *   An [OutputStream] on which to write the serialized objects.
 * @property module
 *   The optional [A_Module] within which serialization is occurring.  If
 *   present, it is used to detect capture of atoms that are not defined in
 *   ancestor modules.
 * @property lookupPumpedObject
 *   A function that checks if the provided [A_BasicObject] happens to be one of
 *   the objects that this serializer was primed with.  If so, it answers the
 *   object's index, which must be negative.  If not present, it answers 0,
 *   which implies the object will need to be serialized.  Positive indices are
 *   not permitted.  It's up to the caller to decide how to map from objects to
 *   indices, but a [Deserializer] must be provided the inverse of this function
 *   to convert negative indices to objects.
 */
class Serializer constructor (
	val output: OutputStream,
	val module: A_Module? = null,
	private val lookupPumpedObject: (A_BasicObject)->Int = { 0 })
{
	/**
	 * This keeps track of all objects that have been encountered.  It's a map
	 * from each [A_BasicObject] to the [SerializerInstruction] that will be
	 * output for it at the appropriate time.
	 */
	private val encounteredObjects =
		mutableMapOf<A_BasicObject, SerializerInstruction>()


	/**
	 * The [map][A_Map] from objects that were serialized by this serializer, to
	 * their one-based index.  This is only valid after serialization completes.
	 */
	private val serializedObjectsMap by lazy {
		var m = emptyMap
		serializedObjectsList.forEachIndexed { zeroIndex, obj ->
			m = m.mapAtPuttingCanDestroy(obj, fromInt(zeroIndex + 1), true)
		}
		m.makeShared()
	}

	/**
	 * The actual sequence of objects that have been serialized so far.  This is
	 * not needed by serialization itself, but it's useful to collect the
	 * objects to allow a [tuple][A_Tuple]] built from this [List] to be
	 * extracted after.
	 */
	private val serializedObjectsList = mutableListOf<A_BasicObject>()

	/**
	 * The [tuple][A_Tuple] of objects that were serialized by this serializer.
	 * This is only valid after serialization completes.
	 */
	private val serializedObjects by lazy {
		tupleFromList(serializedObjectsList)
	}

	/**
	 * All variables that must have their values assigned to them upon
	 * deserialization.  The set is cleared at every checkpoint.
	 */
	private val variablesToAssign = mutableSetOf<A_Variable>()

	/**
	 * The [IndexCompressor] used for encoding indices.  This must agree with
	 * the compressor which will be used later by the [Deserializer].
	 */
	private val compressor = FourStreamIndexCompressor()

	/**
	 * This maintains a stack of [serializer][SerializerInstruction] that need
	 * to be processed.  It's a stack to ensure depth first writing of
	 * instructions before their parents.  This mechanism avoids using the JVM's
	 * limited stack, since Avail structures may in theory be exceptionally
	 * deep.
	 */
	private val workStack = ArrayDeque<() -> Unit>(1000)

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
		val atomModule = atom.issuingModule
		if (atomModule.isNil)
		{
			return
		}
		assert(module.hasAncestor(atomModule))
	}

	/**
	 * Output an unsigned byte.  It must be in the range 0 ≤ n ≤ 255.
	 *
	 * @param byteValue
	 *   The unsigned byte to output, as an [Int],
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
	 *   The [Int] to output.
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
	 * Look up the object and return the existing instruction that produces it.
	 * The instruction must have an index other than -1, which indicates that
	 * the instruction has not yet been written; that is, the instruction must
	 * already have been written.
	 *
	 * @param obj
	 *   The object to look up.
	 * @return
	 *   The (non-negative) index of the instruction that produced the object.
	 */
	internal fun compressedObjectIndex(obj: A_BasicObject): Int
	{
		val instruction = encounteredObjects[obj]!!
		assert(instruction.hasBeenWritten)
		return compressor.compress(instruction.index)
	}

	/**
	 * Trace the object and answer, but don't emit, a [SerializerInstruction]
	 * suitable for adding to the [encounteredObjects] [Map].
	 *
	 * @param obj
	 *   The [A_BasicObject] to trace.
	 * @return
	 *   The new [SerializerInstruction].
	 */
	private fun newInstruction(obj: A_BasicObject): SerializerInstruction
	{
		val operation = if (specialObjects.containsKey(obj)) SPECIAL_OBJECT
		else obj.serializerOperation()
		return SerializerInstruction(operation, obj, this)
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
	 * @param obj
	 *   The object to trace.
	 */
	internal fun traceOne(obj: AvailObject)
	{
		val before = System.nanoTime()
		// Build but don't yet emit the instruction.
		val instruction =
			encounteredObjects.computeIfAbsent(obj) { newInstruction(it) }
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
					instruction.index = compressor.currentIndex()
					instruction.writeTo(this@Serializer)
					assert(instruction.hasBeenWritten)
					compressor.incrementIndex()
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
				&& obj.value().notNil)
			{
				variablesToAssign.add(obj)
				// Output an action to the *start* of the workStack to trace the
				// variable's value.  This prevents recursion, but ensures that
				// everything reachable, including through variables, will be
				// traced.
				workStack.addFirst { traceOne(obj.value()) }
			}
			instruction.operation.traceStat.record(System.nanoTime() - before)
		}
	}

	/**
	 * Serialize this [AvailObject] so that it will appear as the next
	 * checkpoint object during deserialization.
	 *
	 * @param obj
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
			ASSIGN_TO_VARIABLE.serializeStat.record {
				assert(variable.value().notNil)
				val assignment = SerializerInstruction(
					ASSIGN_TO_VARIABLE,
					variable,
					this)
				assignment.index = compressor.currentIndex()
				assignment.writeTo(this)
				assert(assignment.hasBeenWritten)
				compressor.incrementIndex()
			}
		}
		variablesToAssign.clear()
		// Finally, write a checkpoint to say there's something ready for the
		// deserializer to answer.
		CHECKPOINT.serializeStat.record {
			val checkpoint = SerializerInstruction(
				CHECKPOINT,
				strongObject,
				this)
			checkpoint.index = compressor.currentIndex()
			checkpoint.writeTo(this)
			assert(checkpoint.hasBeenWritten)
			compressor.incrementIndex()
		}
	}

	/**
	 * Answer an [A_Tuple] of objects that were serialized.
	 */
	fun serializedObjects(): A_Tuple = serializedObjects

	/**
	 * Answer an [A_Map] from objects that were serialized to their one-based
	 * indices.
	 */
	fun serializedObjectsMap(): A_Map = serializedObjectsMap

	companion object
	{
		/**
		 * The inverse of the [AvailRuntime]'s
		 * [special&#32;objects][AvailRuntime.specialObjects] list.  Entries
		 * that are `null` (i.e., unused entries} are not included.
		 */
		private val specialObjects =
			AvailRuntime.specialObjects.withIndex().associate {
				it.value to it.index
			}

		/**
		 * Special system [atoms][AtomDescriptor] that aren't already in the
		 * list of [special&#32;atoms][AvailRuntime.specialAtoms].
		 */
		private val specialAtoms =
			AvailRuntime.specialAtoms.withIndex().associate {
				it.value to it.index
			}

		/**
		 * Special system [atoms][AtomDescriptor] that aren't already in the
		 * list of [special&#32;atoms][AvailRuntime.specialAtoms], keyed by
		 * their [A_String], where the value is the [A_Atom] itself.
		 */
		internal val specialAtomsByName =
			specialAtoms.keys.associateBy { it.atomName }

		/**
		 * Look up the object.  If it is a
		 * [special&#32;object][AvailRuntime.specialObjects], then answer which
		 * special object it is, otherwise answer -1.
		 *
		 * @param obj
		 *   The object to look up.
		 * @return
		 *   The object's zero-based index in `encounteredObjects`.
		 */
		internal fun indexOfSpecialObject(obj: A_BasicObject): Int =
			specialObjects[obj] ?: -1

		/**
		 * Look up the object.  If it is a
		 * [special&#32;atom][AvailRuntime.specialAtoms], then answer which
		 * special atom it is, otherwise answer `-1`.
		 *
		 * @param
		 *   object The object to look up.
		 * @return
		 *   The object's zero-based index in `encounteredObjects`.
		 */
		internal fun indexOfSpecialAtom(obj: A_Atom) = specialAtoms[obj] ?: -1
	}
}
