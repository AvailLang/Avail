/*
 * VariableDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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
package avail.descriptor.variables

import avail.AvailRuntimeSupport
import avail.annotations.HideFieldInDebugger
import avail.annotations.HideFieldJustForPrinting
import avail.descriptor.atoms.A_Atom
import avail.descriptor.fiber.A_Fiber
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.functions.PrimitiveCompiledCodeDescriptor.Companion.newPrimitiveRawFunction
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.hasKey
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.A_Map.Companion.mapSize
import avail.descriptor.maps.A_Map.Companion.mapWithoutKeyCanDestroy
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.plusCanDestroy
import avail.descriptor.pojos.RawPojoDescriptor
import avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractSlotsEnum
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.keyType
import avail.descriptor.types.A_Type.Companion.rangeIncludesLong
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.valueType
import avail.descriptor.types.A_Type.Companion.writeType
import avail.descriptor.types.IntegerRangeTypeDescriptor
import avail.descriptor.types.TypeTag
import avail.descriptor.types.VariableTypeDescriptor
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithContentType
import avail.descriptor.variables.VariableDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.variables.VariableDescriptor.ObjectSlots.KIND
import avail.descriptor.variables.VariableDescriptor.ObjectSlots.VALUE
import avail.descriptor.variables.VariableDescriptor.ObjectSlots.WRITE_REACTORS
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_CANNOT_READ_UNASSIGNED_VARIABLE
import avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import avail.exceptions.AvailErrorCode.E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED
import avail.exceptions.AvailException
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.primitive.variables.P_SetValue
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.serialization.SerializerOperation
import avail.utility.ifZero
import avail.utility.json.JSONWriter
import java.util.concurrent.atomic.AtomicReference

/**
 * My [object&#32;instances][AvailObject] are variables which can hold any
 * object that agrees with my [inner&#32;type][newVariableWithContentType]. A
 * variable may also hold no value at all.  Any attempt to read the
 * [current&#32;value][A_Variable.getValue] of a variable that holds no value
 * will fail immediately.
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
		/** The [reactor function][FunctionDescriptor]. */
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
		fun isInvalid(): Boolean = function.get().isNil
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
		@HideFieldInDebugger
		HASH_AND_MORE;

		companion object
		{
			/**
			 * A slot to hold the cached hash value.  Zero if not yet computed.
			 */
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
		 * that this is always a [variable&#32;type][VariableTypeDescriptor].
		 */
		KIND,

		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] that wraps a [map][Map] from
		 * arbitrary [Avail&#32;values][AvailObject] to
		 * [writer&#32;reactors][VariableAccessReactor] that respond to writes
		 * of the [variable][VariableDescriptor].
		 */
		@HideFieldJustForPrinting
		WRITE_REACTORS
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean =
			e === VALUE
				|| e === IntegerSlots.HASH_AND_MORE
				|| e === WRITE_REACTORS

	override fun o_Hash(self: AvailObject): Int =
		self.slot(HASH_OR_ZERO).ifZero {
			synchronized(self) {
				self.slot(HASH_OR_ZERO).ifZero {
					AvailRuntimeSupport.nextNonzeroHash().also { hash ->
						self.setSlot(HASH_OR_ZERO, hash)
					}
				}
			}
		}

	override fun o_Value(self: AvailObject): AvailObject =
		self.slot(VALUE)

	@Throws(VariableGetException::class)
	override fun o_GetValue(self: AvailObject): AvailObject
	{
		try
		{
			val interpreter = Interpreter.current()
			if (interpreter.traceVariableReadsBeforeWrites())
			{
				val fiber = interpreter.fiber()
				fiber.recordVariableAccess(self, true)
			}
		}
		catch (e: ClassCastException)
		{
			// No implementation required.
		}
		// Answer the current value of the variable. Fail if no value is
		// currently assigned.
		val value = self.slot(VALUE)
		if (value.isNil)
		{
			throw VariableGetException(E_CANNOT_READ_UNASSIGNED_VARIABLE)
		}
		if (mutability === Mutability.IMMUTABLE)
		{
			value.makeImmutable()
		}
		return value
	}

	override fun o_HasValue(self: AvailObject): Boolean
	{
		try
		{
			val interpreter = Interpreter.current()
			if (interpreter.traceVariableReadsBeforeWrites())
			{
				val fiber = interpreter.fiber()
				fiber.recordVariableAccess(self, true)
			}
		}
		catch (e: ClassCastException)
		{
			// No implementation required.
		}
		val value = self.slot(VALUE)
		return value.notNil
	}

	override fun o_SerializerOperation(self: AvailObject)
		: SerializerOperation = SerializerOperation.LOCAL_VARIABLE

	@Throws(VariableSetException::class)
	override fun o_SetValue(self: AvailObject, newValue: A_BasicObject)
	{
		if (!newValue.isInstanceOf(self.slot(KIND).writeType))
		{
			throw VariableSetException(E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		o_SetValueNoCheck(self, newValue)
	}

	@Throws(VariableSetException::class)
	override fun o_SetValueNoCheck(
		self: AvailObject, newValue: A_BasicObject)
	{
		assert(newValue.notNil)
		handleVariableWriteTracing(self)
		self.setSlot(VALUE, newValue)
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_GetAndSetValue(
		self: AvailObject, newValue: A_BasicObject): AvailObject
	{
		handleVariableWriteTracing(self)
		val outerKind = self.slot(KIND)
		if (!newValue.isInstanceOf(outerKind.writeType))
		{
			throw VariableSetException(E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		val value = self.slot(VALUE)
		if (value.isNil)
		{
			throw VariableGetException(E_CANNOT_READ_UNASSIGNED_VARIABLE)
		}
		self.setSlot(VALUE, newValue)
		if (mutability === Mutability.MUTABLE)
		{
			value.makeImmutable()
		}
		return value
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_CompareAndSwapValues(
		self: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject): Boolean
	{
		if (!newValue.isInstanceOf(self.slot(KIND).writeType))
		{
			throw VariableSetException(E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		handleVariableWriteTracing(self)
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		val value = self.slot(VALUE)
		if (value.isNil)
		{
			throw VariableGetException(E_CANNOT_READ_UNASSIGNED_VARIABLE)
		}
		val swap = value.equals(reference)
		if (swap)
		{
			self.setSlot(VALUE, newValue)
		}
		if (mutability === Mutability.MUTABLE)
		{
			value.makeImmutable()
		}
		return swap
	}

	@Throws(VariableSetException::class)
	override fun o_CompareAndSwapValuesNoCheck(
		self: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject): Boolean
	{
		handleVariableWriteTracing(self)
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		val oldValue = self.slot(VALUE)
		if (oldValue.isNil || !oldValue.equals(reference)) return false
		self.setSlot(VALUE, newValue)
		if (mutability === Mutability.MUTABLE)
		{
			oldValue.makeImmutable()
		}
		return true
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_FetchAndAddValue(
		self: AvailObject, addend: A_Number): A_Number
	{
		handleVariableWriteTracing(self)
		val outerKind: A_Type = self.slot(KIND)
		assert(outerKind.readType.isSubtypeOf(
			IntegerRangeTypeDescriptor.extendedIntegers))
		// The variable is not visible to multiple fibers, and cannot become
		// visible to any other fiber except by an act of the current fiber,
		// therefore do not worry about atomicity.
		val value: A_Number = self.slot(VALUE)
		if (value.isNil)
		{
			throw VariableGetException(E_CANNOT_READ_UNASSIGNED_VARIABLE)
		}
		val newValue = value.plusCanDestroy(addend, false)
		if (!newValue.isInstanceOf(outerKind.writeType))
		{
			throw VariableSetException(E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		self.setSlot(VALUE, newValue)
		if (mutability === Mutability.MUTABLE)
		{
			value.makeImmutable()
		}
		return value
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicAddToMap(
		self: AvailObject,
		key: A_BasicObject,
		value: A_BasicObject)
	{
		handleVariableWriteTracing(self)
		val outerKind: A_Type = self.slot(KIND)
		val writeType = outerKind.writeType
		val oldMap: A_Map = self.slot(VALUE)
		if (oldMap.isNil)
			throw VariableGetException(E_CANNOT_READ_UNASSIGNED_VARIABLE)
		if (!oldMap.isMap)
			throw VariableSetException(E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		val newMap = oldMap.mapAtPuttingCanDestroy(key, value, true)
		if (writeType.isMapType)
		{
			// Just check the new size, new key, and new value.
			if (!writeType.sizeRange.rangeIncludesLong(
					newMap.mapSize.toLong())
				|| !key.isInstanceOf(writeType.keyType)
				|| !value.isInstanceOf(writeType.valueType))
			{
				throw VariableSetException(
					E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
			}
		}
		else
		{
			// Do a full type-check.
			if (!newMap.isInstanceOf(writeType))
				throw VariableSetException(
					E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		// We already checked the key, value, and resulting size, so we can skip
		// a separate type check.
		self.setSlot(VALUE, newMap.makeShared())
	}

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicRemoveFromMap(
		self: AvailObject,
		key: A_BasicObject)
	{
		handleVariableWriteTracing(self)
		val outerKind: A_Type = self.slot(KIND)
		val writeType = outerKind.writeType
		val oldMap: A_Map = self.slot(VALUE)
		if (oldMap.isNil)
			throw VariableGetException(E_CANNOT_READ_UNASSIGNED_VARIABLE)
		if (!oldMap.isMap)
			throw VariableSetException(E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		val newMap = oldMap.mapWithoutKeyCanDestroy(key, true)
		if (writeType.isMapType)
		{
			// Just check the new size, new key, and new value.
			if (!writeType.sizeRange.rangeIncludesLong(
					newMap.mapSize.toLong()))
			{
				throw VariableSetException(
					E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
			}
		}
		else
		{
			// Do a full type-check.
			if (!newMap.isInstanceOf(writeType))
				throw VariableSetException(
					E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		// We already checked the new size was ok, and we're not introducing any
		// new keys or values, so the new map must still be type-safe.
		self.setSlot(VALUE, newMap.makeShared())
	}

	@Throws(VariableGetException::class)
	override fun o_VariableMapHasKey(
		self: AvailObject, key: A_BasicObject): Boolean
	{
		handleVariableWriteTracing(self)
		val outerKind: A_Type = self.slot(KIND)
		val readType = outerKind.readType
		assert(readType.isMapType)
		val oldMap: A_Map = self.slot(VALUE)
		if (oldMap.isNil)
		{
			throw VariableGetException(
				E_CANNOT_READ_UNASSIGNED_VARIABLE)
		}
		return oldMap.hasKey(key)
	}

	override fun o_ClearValue(self: AvailObject)
	{
		handleVariableWriteTracing(self)
		self.setSlot(VALUE, nil)
	}

	override fun o_AddDependentChunk(self: AvailObject, chunk: L2Chunk)
	{
		assert(!isShared)
		val sharedVariable: A_Variable = self.makeShared()
		sharedVariable.addDependentChunk(chunk)
		self.becomeIndirectionTo(sharedVariable)
	}

	override fun o_RemoveDependentChunk(self: AvailObject, chunk: L2Chunk)
	{
		assert(!isShared)
		assert(false) { "Chunk removed but not added!" }
		unsupported
	}

	override fun o_AddWriteReactor(
		self: AvailObject,
		key: A_Atom,
		reactor: VariableAccessReactor)
	{
		withWriteReactorsToModify(self, true) { writeReactors ->
			discardInvalidWriteReactors(writeReactors!!)
			writeReactors[key] = reactor
		}
	}

	@Throws(AvailException::class)
	override fun o_RemoveWriteReactor(self: AvailObject, key: A_Atom)
	{
		withWriteReactorsToModify(self, true) { writeReactors ->
			discardInvalidWriteReactors(writeReactors!!)
			if (writeReactors.remove(key) === null)
			{
				throw AvailException(AvailErrorCode.E_KEY_NOT_FOUND)
			}
		}
	}

	override fun o_ValidWriteReactorFunctions(self: AvailObject): A_Set
	{
		return withWriteReactorsToModify(self, false) { writeReactors ->
			var set = emptySet
			if (writeReactors !== null)
			{
				for ((_, value) in writeReactors)
				{
					val function = value.getAndClearFunction()
					if (function.notNil)
					{
						set = set.setWithElementCanDestroy(function, true)
					}
				}
				writeReactors.clear()
			}
			set
		}
	}

	override fun o_Kind(self: AvailObject): A_Type = self.slot(KIND)

	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean = another.traversed().sameAddressAs(self)

	override fun o_MakeImmutable(self: AvailObject): AvailObject
	{
		// If I am being frozen (a variable), I don't need to freeze my current
		// value. I do, on the other hand, have to freeze my kind object.
		if (isMutable)
		{
			self.setDescriptor(immutable)
			self.slot(KIND).makeImmutable()
		}
		return self
	}

	override fun o_MakeShared(self: AvailObject): AvailObject
	{
		assert(!isShared)
		//TODO: Determine if we should be transferring the write-reactors.
		return VariableSharedDescriptor.createSharedFrom(
			self.slot(KIND),
			self.hash(),
			self.slot(VALUE),
			self)
	}

	override fun o_IsInitializedWriteOnceVariable(self: AvailObject)
		: Boolean = false

	override fun o_IsGlobal(self: AvailObject): Boolean = false

	override fun o_ValueWasStablyComputed(
		self: AvailObject): Boolean
	{
		// The override in VariableSharedWriteOnceDescriptor answer a stored
		// flag set during initialization, but other variables always answer
		// false.
		return false
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("variable") }
			at("variable type") { self.slot(KIND).writeTo(writer) }
			if (self.slot(VALUE).notNil)
			{
				at("value") {
					self.slot(VALUE).writeSummaryTo(writer)
				}
			}
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("variable") }
			at("variable type") {
				self.slot(KIND).writeSummaryTo(writer)
			}
		}

	/**
	 * Extract the given variable's write-reactors map, and pass it into the
	 * [body] function.  If toModify is true, initialize the field if
	 * needed.  If toModify is false and the field has not yet been set, use
	 * `null` instead.  Ensure that the map can not be read or written by
	 * other threads during the body.
	 *
	 * @param self
	 *   The [A_Variable] to examine and/or update.
	 * @param toModify
	 *   Whether to initialize the field if it has not yet been initialized.
	 * @body
	 *   A function that runs with either this variable's [MutableMap] from
	 *   [A_Atom] to [VariableAccessReactor], or `null`.
	 */
	open fun<T> withWriteReactorsToModify(
		self: AvailObject,
		toModify: Boolean,
		body: (MutableMap<A_Atom, VariableAccessReactor>?)->T): T
	{
		assert(this == self.descriptor())
		var pojo = self.volatileSlot(WRITE_REACTORS)
		if (pojo.isNil)
		{
			if (!toModify)
			{
				return body(null)
			}
			pojo = identityPojo(mutableMapOf<A_Atom, VariableAccessReactor>())
			self.setVolatileSlot(WRITE_REACTORS, pojo)
		}
		return body(pojo.javaObjectNotNull())
	}


	/**
	 * If [variable&#32;write&#32;tracing][Interpreter.traceVariableWrites]
	 * is enabled, then
	 * [record&#32;the&#32;write][A_Fiber.recordVariableAccess]. If variable
	 * write tracing is disabled, but the variable has write reactors, then
	 * raise an [exception][VariableSetException] with
	 * [E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED] as the
	 * error code.
	 *
	 * @param self
	 *   The variable.
	 * @throws VariableSetException
	 *   If variable write tracing is disabled, but the variable has write
	 *   reactors.
	 */
	@Throws(VariableSetException::class)
	internal fun handleVariableWriteTracing(self: AvailObject)
	{
		try
		{
			val interpreter = Interpreter.current()
			if (interpreter.traceVariableWrites())
			{
				interpreter.fiber().recordVariableAccess(self, false)
			}
			else
			{
				withWriteReactorsToModify(self, false) { writeReactors ->
					if (writeReactors !== null)
					{
						discardInvalidWriteReactors(writeReactors)
						// If there are write reactors, but write tracing isn't
						// active, then raise an exception.
						if (writeReactors.isNotEmpty())
						{
							throw VariableSetException(
								E_OBSERVED_VARIABLE_WRITTEN_WHILE_UNTRACED)
						}
					}
				}
			}
		}
		catch (e: ClassCastException)
		{
			// No implementation required.
		}
	}

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = VariableSharedDescriptor.shared

	companion object
	{
		/**
		 * Discard all [invalid][VariableAccessReactor.isInvalid]
		 * [write&#32;reactors][VariableAccessReactor] from the specified
		 * [map][Map].
		 *
		 * @param writeReactors
		 *   The map of write reactors.
		 */
		fun discardInvalidWriteReactors(
			writeReactors: MutableMap<A_Atom, VariableAccessReactor>)
		{
			writeReactors.values.removeIf(VariableAccessReactor::isInvalid)
		}

		/** The [CheckedMethod] for [A_Variable.clearValue]. */
		val clearVariableMethod = instanceMethod(
			A_Variable::class.java,
			A_Variable::clearValue.name,
			Void.TYPE)

		/**
		 * The bootstrapped [assignment&#32;function][P_SetValue] used to
		 * restart implicitly observed assignments.
		 */
		val bootstrapAssignmentFunction: A_Function =
			createFunction(
				newPrimitiveRawFunction(P_SetValue, nil, 0),
				emptyTuple
			).makeShared()

		/**
		 * Create a `VariableDescriptor variable` which can only contain values
		 * of the specified type.  The new variable initially holds no value.
		 *
		 * @param contentType
		 *   The type of objects the new variable can contain.
		 * @return
		 *   A new variable able to hold the specified type of objects.
		 */
		fun newVariableWithContentType(contentType: A_Type): AvailObject =
			newVariableWithOuterType(variableTypeFor(contentType))

		/**
		 * Create a `variable` of the specified
		 * [variable&#32;type][VariableTypeDescriptor].  The new variable
		 * initially holds no value.
		 *
		 * Note that [WRITE_REACTORS] and [VALUE] can be initialized with
		 * ordinary slot writes here, because should the variable become
		 * shared, a volatile write to its descriptor (and subsequent volatile
		 * read by other threads) protects us.
		 *
		 * @param variableType
		 *   The [variable&#32;type][VariableTypeDescriptor].
		 * @return
		 *   A new variable of the given type.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun newVariableWithOuterType(variableType: A_Type?): AvailObject =
			mutable.create {
				setSlot(KIND, variableType!!)
				setSlot(HASH_OR_ZERO, 0)
				setSlot(VALUE, nil)
				setSlot(WRITE_REACTORS, nil)
			}

		/**
		 * The [CheckedMethod] for [newVariableWithOuterType].
		 */
		val newVariableWithOuterTypeMethod = staticMethod(
			VariableDescriptor::class.java,
			::newVariableWithOuterType.name,
			AvailObject::class.java,
			A_Type::class.java)

		/** The mutable [VariableDescriptor]. */
		private val mutable = VariableDescriptor(
			Mutability.MUTABLE,
			TypeTag.VARIABLE_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)

		/** The immutable [VariableDescriptor]. */
		private val immutable = VariableDescriptor(
			Mutability.IMMUTABLE,
			TypeTag.VARIABLE_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)
	}
}
