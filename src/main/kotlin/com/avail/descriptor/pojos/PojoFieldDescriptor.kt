/*
 * PojoFieldDescriptor.kt
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
package com.avail.descriptor.pojos

import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.pojos.PojoFieldDescriptor.ObjectSlots.*
import com.avail.descriptor.pojos.PojoFinalFieldDescriptor.Companion.pojoFinalFieldForInnerType
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.types.*
import com.avail.descriptor.types.VariableTypeDescriptor.variableTypeFor
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import com.avail.exceptions.AvailRuntimeException
import com.avail.exceptions.MarshalingException
import com.avail.exceptions.VariableGetException
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.*

/**
 * A `PojoFieldDescriptor` is an Avail [variable][VariableDescriptor] that
 * facilitates access to the instance [Java field][Field] of a particular
 * [pojo][PojoDescriptor] or the static field of a particular
 * [pojo&#32;type][PojoTypeDescriptor]. It supports the same protocol as any
 * other variable, but reads and writes are of the pojo's field.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class PojoFieldDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability, TypeTag.VARIABLE_TAG, ObjectSlots::class.java, null)
{
	/** The layout of the object slots.  */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * A [raw&#32;pojo][RawPojoDescriptor] that wraps a reflected Java
		 * [Field].
		 */
		FIELD,

		/**
		 * The receiver [raw&#32;pojo][RawPojoDescriptor] to which the [Field]
		 * is bound.
		 */
		RECEIVER,

		/**
		 * The [kind][VariableTypeDescriptor] of the
		 * [variable][VariableDescriptor].
		 */
		KIND
	}

	override fun o_ClearValue(self: AvailObject)
	{
		val receiver = self.slot(RECEIVER).javaObjectNotNull<Any>()
		val field = self.slot(FIELD).javaObjectNotNull<Field>()
		val fieldType = field.type
		val defaultValue: Any?
		// Sadly Java does not offer reflective access to the default values of
		// its primitive types ...
		defaultValue =
			when
			{
				!fieldType.isPrimitive -> null
				fieldType == Boolean::class.javaPrimitiveType ->
					java.lang.Boolean.FALSE
				fieldType == Float::class.javaPrimitiveType -> 0.0f
				fieldType == Double::class.javaPrimitiveType -> 0.0
				fieldType == Char::class.javaPrimitiveType -> 0.toChar()
				else -> zero().marshalToJava(fieldType)
			}
		// Clear the variable by writing the appropriate default value.
		try
		{
			synchronized(receiver) { field[receiver] = defaultValue }
		}
		catch (e: Exception)
		{
			throw MarshalingException(e)
		}
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsPojoField(self.slot(FIELD), self.slot(RECEIVER))

	override fun o_EqualsPojoField(
		self: AvailObject,
		field: AvailObject,
		receiver: AvailObject
	): Boolean = (self.slot(FIELD).equals(field)
		&& self.slot(RECEIVER).equals(receiver))

	@Throws(VariableGetException::class)
	override fun o_GetValue(self: AvailObject): AvailObject
	{
		val receiver = self.slot(RECEIVER).javaObjectNotNull<Any>()
		val field = self.slot(FIELD).javaObjectNotNull<Field>()
		val expectedType = self.slot(KIND).readType()
		try
		{
			return synchronized(receiver) {
				PojoTypeDescriptor.unmarshal(field.get(receiver), expectedType)
			}
		}
		catch (e: Exception)
		{
			throw AvailRuntimeException(E_JAVA_MARSHALING_FAILED, e)
		}
	}

	override fun o_Hash(self: AvailObject): Int
	{
		var h = self.slot(FIELD).hash() xor 0x2199C0C3
		h *= multiplier
		h += self.slot(RECEIVER).hash()
		return h
	}

	override fun o_HasValue(self: AvailObject): Boolean
	{
		// A pojo field has a value by definition, since we consider Java null
		// as unequal to nil.
		return true
	}

	override fun o_Kind(self: AvailObject): A_Type = self.slot(KIND)

	override fun o_SerializerOperation(
		self: AvailObject
	): SerializerOperation
	{
		val field = self.slot(FIELD).javaObjectNotNull<Field>()
		if (field.modifiers and Modifier.STATIC != 0)
		{
			return SerializerOperation.STATIC_POJO_FIELD
		}
		throw unsupportedOperationException()
	}

	override fun o_SetValue(
		self: AvailObject,
		newValue: A_BasicObject
	)
	{
		val receiver = self.slot(RECEIVER).javaObjectNotNull<Any>()
		val field = self.slot(FIELD).javaObjectNotNull<Field>()
		val classHint = field.type
		try
		{
			synchronized(receiver) {
				field.set(receiver, newValue.marshalToJava(classHint))
			}
		}
		catch (e: Exception)
		{
			throw AvailRuntimeException(E_JAVA_MARSHALING_FAILED, e)
		}
	}

	override fun o_SetValueNoCheck(
		self: AvailObject,
		newValue: A_BasicObject
	)
	{
		// Actually check this write anyhow. Just in case.
		val receiver = self.slot(RECEIVER).javaObjectNotNull<Any>()
		val field = self.slot(FIELD).javaObjectNotNull<Field>()
		val classHint = field.type
		try
		{
			synchronized(receiver) {
				field.set(receiver, newValue.marshalToJava(classHint))
			}
		}
		catch (e: Exception)
		{
			throw AvailRuntimeException(E_JAVA_MARSHALING_FAILED, e)
		}
	}

	override fun o_Value(self: AvailObject): AvailObject
	{
		val receiver = self.slot(RECEIVER).javaObjectNotNull<Any>()
		val field = self.slot(FIELD).javaObjectNotNull<Field>()
		val expectedType = self.slot(KIND).readType()
		try
		{
			return synchronized(receiver) {
				PojoTypeDescriptor.unmarshal(field.get(receiver), expectedType)
			}
		}
		catch (e: Exception)
		{
			throw AvailRuntimeException(E_JAVA_MARSHALING_FAILED, e)
		}
	}

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
		self.value().printOnAvoidingIndent(builder, recursionMap, indent + 1)
	}

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object
	{
		/** The mutable [PojoFieldDescriptor].  */
		private val mutable = PojoFieldDescriptor(Mutability.MUTABLE)

		/** The immutable [PojoFieldDescriptor].  */
		private val immutable = PojoFieldDescriptor(Mutability.IMMUTABLE)

		/** The shared [PojoFieldDescriptor].  */
		private val shared = PojoFieldDescriptor(Mutability.SHARED)

		/**
		 * Create a `PojoFieldDescriptor variable` that reads/writes through to
		 * the specified [field][Field] and has the specified
		 * [variable&#32;type][VariableTypeDescriptor].
		 *
		 * @param field
		 *   A [raw&#32;pojo][RawPojoDescriptor] that wraps a reflected Java
		 *   field.
		 * @param receiver
		 *   The raw pojo to which the reflected Java field is bound.
		 * @param outerType
		 *   The variable type.
		 * @return
		 *   A new variable of the specified type.
		 */
		private fun forOuterType(
			field: AvailObject,
			receiver: AvailObject,
			outerType: A_Type
		): AvailObject = mutable.create().apply {
			setSlot(FIELD, field)
			setSlot(RECEIVER, receiver)
			setSlot(KIND, outerType)
		}

		/**
		 * Create a `PojoFieldDescriptor variable` that can read/write through
		 * to the specified [field][Field] values of the specified
		 * [type][TypeDescriptor].
		 *
		 * @param field
		 *   A [raw&#32;pojo][RawPojoDescriptor] that wraps a reflected Java
		 *   field.
		 * @param receiver
		 *   The [pojo][PojoDescriptor] to which the reflected Java field is
		 *   bound.
		 * @param innerType
		 *   The types of values that can be read/written.
		 * @return
		 *   A new variable able to read/write values of the specified types.
		 */
		fun pojoFieldVariableForInnerType(
			field: AvailObject,
			receiver: AvailObject,
			innerType: A_Type
		): AvailObject
		{
			val javaField = field.javaObjectNotNull<Field>()
			return when
			{
				Modifier.isFinal(javaField.modifiers) ->
					pojoFinalFieldForInnerType(field, receiver, innerType)
				else ->
					forOuterType(field, receiver, variableTypeFor(innerType))
			}
		}
	}
}
