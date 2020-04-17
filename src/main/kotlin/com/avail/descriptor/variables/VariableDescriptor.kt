/*
 * VariableDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.variables

import com.avail.AvailRuntimeSupport
import com.avail.annotations.AvailMethod
import com.avail.descriptor.A_Fiber
import com.avail.descriptor.Descriptor
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.representation.*
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.IntegerRangeTypeDescriptor
import com.avail.descriptor.types.TypeTag
import com.avail.descriptor.types.VariableTypeDescriptor
import com.avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.AvailException
import com.avail.exceptions.VariableGetException
import com.avail.exceptions.VariableSetException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.primitive.variables.P_SetValue
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.instanceMethod
import com.avail.optimizer.jvm.CheckedMethod.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * My [object instances][AvailObject] are variables which can hold any object
 * that agrees with my [inner type][newVariableWithContentType]. A variable may
 * also hold no value at all.  Any attempt to read the [current
 * value][A_Variable.getValue] of a variable that holds no value will fail
 * immediately.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `VariableDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no integer slots.
 */
open class VariableDescriptor protected constructor(
		mutability: Mutability,
		typeTag: TypeTag,
		objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
		integerSlotsEnumClass: Class<out IntegerSlotsEnum>?
) : Descriptor(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
{
	/**
	 * A `VariableAccessReactor` records a one-shot
	 * [function][FunctionDescriptor]. It is cleared upon read.
	 */
	class VariableAccessReactor(initialFunction: A_Function)
	{
		/** The [reactor function][FunctionDescriptor].  */
		private val function = AtomicReference(initialFunction)

		/**
		 * Atomically get and clear [reactor][FunctionDescriptor].
		 *
		 * @return
		 *   The reactor function, or [nil] if the reactor function has already
		 *   been requested (and the reactor is therefore invalid).
		 */
		fun getAndClearFunction(): A_Function = function.getAndSet(nil)

		/**
		 * Answer whether the `VariableAccessReactor` is invalid.
		 *
		 * @return
		 *   `true` if the reactor is invalid, `false` otherwise.
		 */
		fun isInvalid(): Boolean = function.get().equalsNil()
	}

	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the [HASH_OR_ZERO], but the upper 32 can
		 * be used by subclasses.
		 */
		HASH_AND_MORE;

		companion object
		{
			/**
			 * A slot to hold the cached hash value.  Zero if not yet computed.
			 */
			@JvmField
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [contents][AvailObject] of the [variable][VariableDescriptor].
		 */
		VALUE,

		/**
		 * The [kind][AvailObject] of the [variable][VariableDescriptor].  Note
		 * that this is always a [variable type][VariableTypeDescriptor].
		 */
		KIND,

		/**
		 * A [raw pojo][RawPojoDescriptor] that wraps a [map][Map] from
		 * arbitrary [Avail values][AvailObject] to
		 * [writer reactors][VariableAccessReactor] that respond to writes of
		 * the [variable][VariableDescriptor].
		 */
		WRITE_REACTORS
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean =
			e === ObjectSlots.VALUE
				|| e === IntegerSlots.HASH_AND_MORE
				|| e === ObjectSlots.WRITE_REACTORS

	@AvailMethod
	override fun o_Hash(`object`: AvailObject): Int
	{
		var hash = `object`.slot(IntegerSlots.HASH_OR_ZERO)
		if (hash == 0)
		{
			do
			{
				hash = AvailRuntimeSupport.nextHash()
			}
			while (hash == 0)
			`object`.setSlot(IntegerSlots.HASH_OR_ZERO, hash)
		}
		return hash
	}

	@AvailMethod
	override fun o_Value(`object`: AvailObject): AvailObject =
		`object`.slot(ObjectSlots.VALUE)

	@AvailMethod
	@Throws(VariableGetException::class)
	override fun o_GetValue(`object`: AvailObject): AvailObject
	{
		try
		{
			val interpreter = Interpreter.current()
			if (interpreter.traceVariableReadsBeforeWrites())
			{
				val fiber = interpreter.fiber()
				fiber.recordVariableAccess(`object`, true)
			}
		}
		catch (e: ClassCastException)
		{
			// No implementation required.
		}
		// Answer the current value of the variable. Fail if no value is
		// currently assigned.
		val value = `object`.slot(ObjectSlots.VALUE)
		if (value.equalsNil())
		{
			throw VariableGetException(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE)
		}
		if (mutability === Mutability.IMMUTABLE)
		{
			value.makeImmutable()
		}
		return value
	}

	@AvailMethod
	override fun o_HasValue(`object`: AvailObject): Boolean
	{
		try
		{
			val interpreter = Interpreter.current()
			if (interpreter.traceVariableReadsBeforeWrites())
			{
				val fiber = interpreter.fiber()
				fiber.recordVariableAccess(`object`, true)
			}
		}
		catch (e: ClassCastException)
		{
			// No implementation required.
		}
		val value = `object`.slot(ObjectSlots.VALUE)
		return !value.equalsNil()
	}

	@AvailMethod
	override fun o_SerializerOperation(`object`: AvailObject)
		: SerializerOperation = SerializerOperation.LOCAL_VARIABLE

	@AvailMethod
	@Throws(VariableSetException::class)
	override fun o_SetValue(`object`: AvailObject, newValue: A_BasicObject)
	{
		handleVariableWriteTracing(`object`)
		val outerKind: A_Type = `object`.slot(ObjectSlots.KIND)
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw VariableSetException(
				AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		`object`.setSlot(ObjectSlots.VALUE, newValue)
	}

	@AvailMethod
	@Throws(VariableSetException::class)
	override fun o_SetValueNoCheck(
		`object`: AvailObject, newValue: A_BasicObject)
	{
		assert(!newValue.equalsNil())
		handleVariableWriteTracing(`object`)
		`object`.setSlot(ObjectSlots.VALUE, newValue)
	}

	@AvailMethod
	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_GetAndSetValue(
		`object`: AvailObject, newValue: A_BasicObject): AvailObject
	{
		handleVariableWriteTracing(`object`)
		val outerKind = `object`.slot(ObjectSlots.KIND)
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw VariableSetException(
				AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		val value = `object`.slot(ObjectSlots.VALUE)
		if (value.equalsNil())
		{
			throw VariableGetException(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE)
		}
		`object`.setSlot(ObjectSlots.VALUE, newValue)
		if (mutability === Mutability.MUTABLE)
		{
			value.makeImmutable()
		}
		return value
	}

	@AvailMethod
	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_CompareAndSwapValues(
		`object`: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject): Boolean
	{
		handleVariableWriteTracing(`object`)
		val outerKind = `object`.slot(ObjectSlots.KIND)
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw VariableSetException(
				AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		val value = `object`.slot(ObjectSlots.VALUE)
		if (value.equalsNil())
		{
			throw VariableGetException(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE)
		}
		val swap = value.equals(reference)
		if (swap)
		{
			`object`.setSlot(ObjectSlots.VALUE, newValue)
		}
		if (mutability === Mutability.MUTABLE)
		{
			value.makeImmutable()
		}
		return swap
	}

	@AvailMethod
	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_FetchAndAddValue(
		`object`: AvailObject, addend: A_Number): A_Number
	{
		handleVariableWriteTracing(`object`)
		val outerKind: A_Type = `object`.slot(ObjectSlots.KIND)
		assert(outerKind.readType().isSubtypeOf(
			IntegerRangeTypeDescriptor.extendedIntegers()))
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		val value: A_Number = `object`.slot(ObjectSlots.VALUE)
		if (value.equalsNil())
		{
			throw VariableGetException(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE)
		}
		val newValue = value.plusCanDestroy(addend, false)
		if (!newValue.isInstanceOf(outerKind.writeType()))
		{
			throw VariableSetException(
				AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		`object`.setSlot(ObjectSlots.VALUE, newValue)
		if (mutability === Mutability.MUTABLE)
		{
			value.makeImmutable()
		}
		return value
	}

	@AvailMethod
	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicAddToMap(
		`object`: AvailObject,
		key: A_BasicObject,
		value: A_BasicObject)
	{
		handleVariableWriteTracing(`object`)
		val outerKind: A_Type = `object`.slot(ObjectSlots.KIND)
		val readType = outerKind.readType()
		assert(readType.isMapType)
		val oldMap: A_Map = `object`.slot(ObjectSlots.VALUE)
		if (oldMap.equalsNil())
		{
			throw VariableGetException(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE)
		}
		assert(oldMap.isMap)
		if (readType.isMapType)
		{
			// Make sure the new map will satisfy the writeType.  We do these
			// checks before modifying the map, since the new key/value pair can
			// be added destructively.
			if (!key.isInstanceOf(readType.keyType())
			    || !value.isInstanceOf(readType.valueType()))
			{
				throw VariableSetException(
					AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
			}
			if (readType.sizeRange().upperBound().equalsInt(oldMap.mapSize()))
			{
				// Map is as full as the type will allow.  Ensure we're
				// replacing a key, not adding one.
				if (!oldMap.hasKey(key))
				{
					throw VariableSetException(
						AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
				}
			}
		}
		val newMap = oldMap.mapAtPuttingCanDestroy(key, value, true)
		// We already checked the key, value, and resulting size, so we can skip
		// a separate type check.
		`object`.setSlot(ObjectSlots.VALUE, newMap.makeShared())
	}

	@AvailMethod
	@Throws(VariableGetException::class)
	override fun o_VariableMapHasKey(
		`object`: AvailObject, key: A_BasicObject): Boolean
	{
		handleVariableWriteTracing(`object`)
		val outerKind: A_Type = `object`.slot(ObjectSlots.KIND)
		val readType = outerKind.readType()
		assert(readType.isMapType)
		val oldMap: A_Map = `object`.slot(ObjectSlots.VALUE)
		if (oldMap.equalsNil())
		{
			throw VariableGetException(
				AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE)
		}
		return oldMap.hasKey(key)
	}

	@AvailMethod
	override fun o_ClearValue(`object`: AvailObject)
	{
		handleVariableWriteTracing(`object`)
		`object`.setSlot(ObjectSlots.VALUE, nil)
	}

	@AvailMethod
	override fun o_AddDependentChunk(`object`: AvailObject, chunk: L2Chunk)
	{
		assert(!isShared)
		val sharedVariable: A_Variable = `object`.makeShared()
		sharedVariable.addDependentChunk(chunk)
		`object`.becomeIndirectionTo(sharedVariable)
	}

	@AvailMethod
	override fun o_RemoveDependentChunk(`object`: AvailObject, chunk: L2Chunk)
	{
		assert(!isShared)
		assert(false) { "Chunk removed but not added!" }
		throw unsupportedOperationException()
	}

	@AvailMethod
	override fun o_AddWriteReactor(
		`object`: AvailObject,
		key: A_Atom,
		reactor: VariableAccessReactor)
	{
		var rawPojo = `object`.slot(ObjectSlots.WRITE_REACTORS)
		if (rawPojo.equalsNil())
		{
			rawPojo = RawPojoDescriptor.identityPojo(
				HashMap<A_Atom, VariableAccessReactor>())
			`object`.setMutableSlot(ObjectSlots.WRITE_REACTORS, rawPojo)
		}
		val writeReactors =
			rawPojo.javaObjectNotNull<MutableMap<A_Atom, VariableAccessReactor?>>()
		discardInvalidWriteReactors(writeReactors)
		writeReactors[key] = reactor
	}

	@AvailMethod
	@Throws(AvailException::class)
	override fun o_RemoveWriteReactor(`object`: AvailObject, key: A_Atom)
	{
		val rawPojo = `object`.slot(ObjectSlots.WRITE_REACTORS)
		if (rawPojo.equalsNil())
		{
			throw AvailException(AvailErrorCode.E_KEY_NOT_FOUND)
		}
		val writeReactors =
			rawPojo.javaObjectNotNull<MutableMap<A_Atom, VariableAccessReactor?>>()
		discardInvalidWriteReactors(writeReactors)
		if (writeReactors.remove(key) == null)
		{
			throw AvailException(AvailErrorCode.E_KEY_NOT_FOUND)
		}
	}

	@AvailMethod
	override fun o_ValidWriteReactorFunctions(`object`: AvailObject): A_Set
	{
		val rawPojo = `object`.slot(ObjectSlots.WRITE_REACTORS)
		if (!rawPojo.equalsNil())
		{
			val writeReactors =
				rawPojo.javaObjectNotNull<MutableMap<A_Atom, VariableAccessReactor>>()
			var set = SetDescriptor.emptySet()
			for ((_, value) in writeReactors)
			{
				val function = value.getAndClearFunction()
				if (!function.equalsNil())
				{
					set = set.setWithElementCanDestroy(function, true)
				}
			}
			writeReactors.clear()
			return set
		}
		return SetDescriptor.emptySet()
	}

	@AvailMethod
	override fun o_Kind(`object`: AvailObject): A_Type =
		`object`.slot(ObjectSlots.KIND)

	@AvailMethod
	override fun o_Equals(
		`object`: AvailObject,
		another: A_BasicObject): Boolean =
			another.equalsVariable(`object`)

	@AvailMethod
	override fun o_EqualsVariable(
		`object`: AvailObject,
		aVariable: AvailObject): Boolean =
			`object`.sameAddressAs(aVariable)

	@AvailMethod
	override fun o_MakeImmutable(`object`: AvailObject): AvailObject
	{
		// If I am being frozen (a variable), I don't need to freeze my current
		// value. I do, on the other hand, have to freeze my kind object.
		if (isMutable)
		{
			`object`.setDescriptor(immutable)
			`object`.slot(ObjectSlots.KIND).makeImmutable()
		}
		return `object`
	}

	override fun o_MakeShared(`object`: AvailObject): AvailObject
	{
		assert(!isShared)
		return VariableSharedDescriptor.createSharedFrom(
			`object`.slot(ObjectSlots.KIND),
			`object`.hash(),
			`object`.slot(ObjectSlots.VALUE),
			`object`)
	}

	override fun o_IsInitializedWriteOnceVariable(`object`: AvailObject)
		: Boolean = false

	@AvailMethod
	override fun o_IsGlobal(`object`: AvailObject): Boolean = false

	@AvailMethod
	override fun o_ValueWasStablyComputed(
		`object`: AvailObject): Boolean
	{
		// The override in VariableSharedWriteOnceDescriptor answer a stored
		// flag set during initialization, but other variables always answer
		// false.
		return false
	}

	override fun o_WriteTo(`object`: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("variable")
		writer.write("variable type")
		`object`.slot(ObjectSlots.KIND).writeTo(writer)
		if (!`object`.slot(ObjectSlots.VALUE).equalsNil())
		{
			writer.write("value")
			`object`.slot(ObjectSlots.VALUE).writeSummaryTo(writer)
		}
		writer.endObject()
	}

	override fun o_WriteSummaryTo(`object`: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("variable")
		writer.write("variable type")
		`object`.slot(ObjectSlots.KIND).writeSummaryTo(writer)
		writer.endObject()
	}

	override fun mutable(): VariableDescriptor = mutable

	override fun immutable(): VariableDescriptor = immutable

	override fun shared(): VariableDescriptor =
		VariableSharedDescriptor.shared

	companion object
	{
		/**
		 * Discard all [invalid][VariableAccessReactor.isInvalid] [write
		 * reactors][VariableAccessReactor] from the specified [map][Map].
		 *
		 * @param writeReactors
		 *   The map of write reactors.
		 */
		fun discardInvalidWriteReactors(
			writeReactors: MutableMap<A_Atom, VariableAccessReactor?>)
		{
			writeReactors.values.removeIf { obj: VariableAccessReactor? ->
				obj!!.isInvalid()
			}
		}

		/**
		 * If [variable write tracing][Interpreter.traceVariableWrites] is
		 * enabled, then [record the write][A_Fiber.recordVariableAccess]. If
		 * variable write tracing is disabled, but the variable has write
		 * reactors, then raise an [exception][VariableSetException] with
		 * [AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED] as the
		 * error code.
		 *
		 * @param object
		 *   The variable.
		 * @throws VariableSetException
		 *   If variable write tracing is disabled, but the variable has write
		 *   reactors.
		 */
		@Throws(VariableSetException::class)
		private fun handleVariableWriteTracing(`object`: AvailObject)
		{
			try
			{
				val interpreter = Interpreter.current()
				if (interpreter.traceVariableWrites())
				{
					val fiber = interpreter.fiber()
					fiber.recordVariableAccess(`object`, false)
				}
				else
				{
					val rawPojo = `object`.slot(ObjectSlots.WRITE_REACTORS)
					if (!rawPojo.equalsNil())
					{
						val writeReactors =
							rawPojo.javaObjectNotNull<MutableMap<A_Atom, VariableAccessReactor?>>()
						discardInvalidWriteReactors(writeReactors)
						// If there are write reactors, but write tracing isn't
						// active, then raise an exception.
						if (writeReactors.isNotEmpty())
						{
							throw VariableSetException(
								AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED)
						}
					}
				}
			}
			catch (e: ClassCastException)
			{
				// No implementation required.
			}
		}

		/** The [CheckedMethod] for [A_Variable.clearValue].  */
		@JvmField
		val clearVariableMethod: CheckedMethod = instanceMethod(
			A_Variable::class.java,
			A_Variable::clearValue.name,
			Void.TYPE)

		/**
		 * The bootstrapped [assignment function][P_SetValue] used to restart
		 * implicitly observed assignments.
		 */
		@JvmField
		val bootstrapAssignmentFunction: A_Function =
			FunctionDescriptor.createFunction(
				CompiledCodeDescriptor.newPrimitiveRawFunction(
					P_SetValue, nil, 0),
			TupleDescriptor.emptyTuple()).makeShared()

		/**
		 * Create a `VariableDescriptor variable` which can only contain values
		 * of the specified type.  The new variable initially holds no value.
		 *
		 * @param contentType
		 *   The type of objects the new variable can contain.
		 * @return
		 *   A new variable able to hold the specified type of objects.
		 */
		@JvmStatic
		fun newVariableWithContentType(contentType: A_Type): AvailObject =
			newVariableWithOuterType(
				VariableTypeDescriptor.variableTypeFor(contentType))

		/**
		 * Create a `variable` of the specified
		 * [variable type][VariableTypeDescriptor].  The new variable initially
		 * holds no value.
		 *
		 * @param variableType
		 *   The [variable type][VariableTypeDescriptor].
		 * @return
		 *   A new variable of the given type.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun newVariableWithOuterType(variableType: A_Type?): AvailObject
		{
			val result = mutable.create()
			result.setSlot(ObjectSlots.KIND, variableType!!)
			result.setSlot(IntegerSlots.HASH_OR_ZERO, 0)
			result.setSlot(ObjectSlots.VALUE, nil)
			result.setSlot(ObjectSlots.WRITE_REACTORS, nil)
			return result
		}

		/**
		 * The [CheckedMethod] for [newVariableWithOuterType].
		 */
		@JvmField
		val newVariableWithOuterTypeMethod: CheckedMethod = staticMethod(
			VariableDescriptor::class.java,
			::newVariableWithOuterType.name,
			AvailObject::class.java,
			A_Type::class.java)

		/** The mutable [VariableDescriptor].  */
		private val mutable = VariableDescriptor(
			Mutability.MUTABLE,
			TypeTag.VARIABLE_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)

		/** The immutable [VariableDescriptor].  */
		private val immutable = VariableDescriptor(
			Mutability.IMMUTABLE,
			TypeTag.VARIABLE_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)
	}
}