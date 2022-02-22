/*
 * PojoFinalFieldDescriptor.kt
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
package avail.descriptor.pojos

import avail.descriptor.pojos.PojoFinalFieldDescriptor.ObjectSlots.CACHED_VALUE
import avail.descriptor.pojos.PojoFinalFieldDescriptor.ObjectSlots.FIELD
import avail.descriptor.pojos.PojoFinalFieldDescriptor.ObjectSlots.KIND
import avail.descriptor.pojos.PojoFinalFieldDescriptor.ObjectSlots.RECEIVER
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine3
import avail.descriptor.representation.Descriptor
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.types.A_Type
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.PojoTypeDescriptor
import avail.descriptor.types.TypeDescriptor
import avail.descriptor.types.TypeTag
import avail.descriptor.types.VariableTypeDescriptor
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableReadWriteType
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.AvailErrorCode.E_CANNOT_MODIFY_FINAL_JAVA_FIELD
import avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import avail.exceptions.AvailRuntimeException
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.IdentityHashMap

/**
 * A `PojoFinalFieldDescriptor` is an Avail [variable][VariableDescriptor] that
 * facilitates access to the instance Java [Field] of a particular
 * [pojo][PojoDescriptor] or the static field of a particular
 * [pojo&#32;type][PojoTypeDescriptor]. It supports the same protocol as any
 * other variable, but reads and writes are of the pojo's field.
 *
 * It leverages the fact that the field is [final][Modifier.isFinal] by caching
 * the value and not retaining the reflected field directly.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class PojoFinalFieldDescriptor(
	mutability: Mutability
) : Descriptor(
	mutability, TypeTag.VARIABLE_TAG, ObjectSlots::class.java, null)
{
	/** The layout of the object slots. */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] that wraps a [Field].
		 */
		FIELD,

		/**
		 * The [raw&#32;pojo][RawPojoDescriptor] to which the [Field] is bound.
		 */
		RECEIVER,

		/**
		 * The cached value of the reflected Java [Field].
		 */
		CACHED_VALUE,

		/**
		 * The [kind][VariableTypeDescriptor] of the
		 * [variable][VariableDescriptor].
		 */
		KIND
	}

	override fun o_ClearValue(self: AvailObject) = throw VariableSetException(
		E_CANNOT_MODIFY_FINAL_JAVA_FIELD)

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsPojoField(self.slot(FIELD), self.slot(RECEIVER))

	override fun o_EqualsPojoField(
		self: AvailObject,
		field: AvailObject,
		receiver: AvailObject
	): Boolean = (self.slot(FIELD).equals(field)
		&& self.slot(RECEIVER).equals(receiver))

	override fun o_GetValue(self: AvailObject): AvailObject =
		self.slot(CACHED_VALUE)

	/**
	 * The clear will fail, but for correctness we have to attempt the read
	 * first.
	 */
	@Throws(VariableGetException::class)
	override fun o_GetValueClearing(self: AvailObject): AvailObject =
		self.getValue().also { self.clearValue() }

	override fun o_Hash(self: AvailObject): Int = combine3(
		self.slot(FIELD).hash(),
		self.slot(RECEIVER).hash(),
		0x2199C0C3)

	// A pojo final field has a value by definition.
	override fun o_HasValue(self: AvailObject): Boolean = true

	override fun o_Kind(self: AvailObject): A_Type = self.slot(KIND)

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation
	{
		val field = self.slot(FIELD).javaObjectNotNull<Field>()
		if (field.modifiers and Modifier.STATIC != 0)
		{
			return SerializerOperation.STATIC_POJO_FIELD
		}
		unsupportedOperation()
	}

	override fun o_SetValue(self: AvailObject, newValue: A_BasicObject): Unit =
		throw VariableSetException(E_CANNOT_MODIFY_FINAL_JAVA_FIELD)

	override fun o_SetValueNoCheck(
		self: AvailObject,
		newValue: A_BasicObject)
	{
		throw VariableSetException(E_CANNOT_MODIFY_FINAL_JAVA_FIELD)
	}

	override fun o_Value(self: AvailObject): AvailObject =
		self.slot(CACHED_VALUE)

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("variable") }
			at("variable type") { self.kind().writeTo(writer) }
			at("value") { self.value().writeSummaryTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("variable") }
			at("variable type") { self.kind().writeSummaryTo(writer) }
		}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		val field = self.slot(FIELD).javaObjectNotNull<Field>()
		if (!Modifier.isStatic(field.modifiers))
		{
			builder.append('(')
			self.slot(RECEIVER).printOnAvoidingIndent(
				builder, recursionMap, indent + 1)
			builder.append(")'s ")
		}
		builder.append(field)
		builder.append(" = ")
		self.slot(CACHED_VALUE).printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
	}

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object
	{
		/** The mutable [PojoFinalFieldDescriptor]. */
		private val mutable = PojoFinalFieldDescriptor(Mutability.MUTABLE)

		/** The immutable [PojoFinalFieldDescriptor]. */
		private val immutable = PojoFinalFieldDescriptor(Mutability.IMMUTABLE)

		/** The shared [PojoFinalFieldDescriptor]. */
		private val shared = PojoFinalFieldDescriptor(Mutability.SHARED)

		/**
		 * Create a [variable][PojoFinalFieldDescriptor] that reads through to
		 * the specified [final][Modifier.isFinal] [Field] and has the specified
		 * [variable&#32;type][VariableTypeDescriptor].
		 *
		 * @param field
		 *   A [raw&#32;pojo][RawPojoDescriptor] that wraps a reflected Java
		 *   field.
		 * @param receiver
		 *   The raw pojo to which the reflected Java field is bound.
		 * @param cachedValue
		 *   The value of the final field, already
		 *   [marshaled][AvailObject.marshalToJava].
		 * @param outerType
		 *   The variable type.
		 * @return
		 *   A new variable of the specified type.
		 */
		private fun forOuterType(
			field: AvailObject,
			receiver: AvailObject,
			cachedValue: AvailObject,
			outerType: A_Type
		): AvailObject = mutable.create {
			setSlot(FIELD, field)
			setSlot(RECEIVER, receiver)
			setSlot(CACHED_VALUE, cachedValue)
			setSlot(KIND, outerType)
		}

		/**
		 * Create a `PojoFinalFieldDescriptor variable` that can read through to
		 * the specified [field][Field] values of the specified
		 * [type][TypeDescriptor].
		 *
		 * @param field
		 *   A [raw&#32;pojo][RawPojoDescriptor] that wraps a reflected Java
		 *   field.
		 * @param receiver
		 *   The [pojo][PojoDescriptor] to which the reflected Java [Field] is
		 *   bound.
		 * @param innerType
		 *   The types of values that can be read.
		 * @return
		 *   A new variable able to read values of the specified types.
		 */
		fun pojoFinalFieldForInnerType(
			field: AvailObject,
			receiver: AvailObject,
			innerType: A_Type
		): AvailObject
		{
			val javaField = field.javaObjectNotNull<Field>()
			assert(Modifier.isFinal(javaField.modifiers))
			val javaReceiver = receiver.javaObject<Any>()
			val value: AvailObject =
				try
				{
					PojoTypeDescriptor.unmarshal(
						javaField.get(javaReceiver), innerType)
				}
				catch (e: Exception)
				{
					throw AvailRuntimeException(E_JAVA_MARSHALING_FAILED, e)
				}
			return forOuterType(
				field,
				receiver,
				value,
				variableReadWriteType(innerType, bottom))
		}
	}
}
