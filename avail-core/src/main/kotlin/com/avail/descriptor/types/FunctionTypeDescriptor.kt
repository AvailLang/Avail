/*
 * FunctionTypeDescriptor.kt
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
package com.avail.descriptor.types

import com.avail.annotations.ThreadSafe
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.numbers.A_Number.Companion.equalsInt
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.A_Number.Companion.lessThan
import com.avail.descriptor.objects.ObjectTypeDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.synchronizeIf
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.A_Set.Companion.setSize
import com.avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import com.avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.declaredExceptions
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfFunctionType
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.returnType
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.typeAtIndex
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.typeIntersectionOfFunctionType
import com.avail.descriptor.types.A_Type.Companion.typeTuple
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.A_Type.Companion.typeUnionOfFunctionType
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.types.FunctionTypeDescriptor.IntegerSlots.HASH_AND_MORE
import com.avail.descriptor.types.FunctionTypeDescriptor.ObjectSlots.ARGS_TUPLE_TYPE
import com.avail.descriptor.types.FunctionTypeDescriptor.ObjectSlots.DECLARED_EXCEPTIONS
import com.avail.descriptor.types.FunctionTypeDescriptor.ObjectSlots.RETURN_TYPE
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.serialization.SerializerOperation
import com.avail.utility.Strings.newlineTab
import com.avail.utility.json.JSONWriter
import java.lang.Integer.max
import java.util.IdentityHashMap

/**
 * Function types are the types of [functions][FunctionDescriptor]. They contain
 * information about the [types][TypeDescriptor] of arguments that may be
 * accepted, the types of [values][AvailObject] that may be produced upon
 * successful execution, and the types of exceptions that may be raised to
 * signal unsuccessful execution.
 *
 * Function types are contravariant by
 * [argument&#32;types][A_Type.argsTupleType], covariant by
 * [return&#32;type][A_Type.returnType], and covariant by the coverage of types
 * that are members of the [exception&#32;set][A_Type.declaredExceptions].
 * I.e., if there is a type in the exception set of A that isn't equal to or a
 * subtype of an element of the exception set of B, then A can't be a subtype of
 * B.
 *
 * Note that the [A_Type.argsTupleType] can be [bottom] (⊥) instead of a tuple
 * type.  Because bottom is more specific than any tuple type, the resulting
 * function type is considered more general than one with a tuple type (if the
 * other variances also hold).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new function type.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class FunctionTypeDescriptor private constructor(mutability: Mutability)
	: TypeDescriptor(
		mutability,
		TypeTag.FUNCTION_TYPE_TAG,
		TypeTag.FUNCTION_TAG,
		ObjectSlots::class.java,
		IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for caching the hash, but the upper 32 can
		 * be used by other [BitField]s in subclasses.
		 */
		HASH_AND_MORE;

		companion object
		{
			/**
			 * The hash, or zero (`0`) if the hash has not yet been computed.
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
		 * The normalized [set][SetDescriptor] of checked exceptions that may be
		 * raised by message sends performed from within a
		 * [function][FunctionDescriptor] described by this function type.
		 */
		DECLARED_EXCEPTIONS,

		/**
		 * The most general [type][TypeDescriptor] of [value][AvailObject] that
		 * may be produced by a successful completion of a
		 * [function][FunctionDescriptor] described by this function type.
		 */
		RETURN_TYPE,

		/**
		 * A tuple type indicating what kinds of arguments this type's instances
		 * will accept.
		 */
		ARGS_TUPLE_TYPE
	}

	public override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean = e === HASH_AND_MORE

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append('[')
		val list = mutableListOf<A_BasicObject?>()
		val tupleType = self.argsTupleType
		if (tupleType.isBottom)
		{
			builder.append("…")
		}
		else
		{
			val minObject = tupleType.sizeRange.lowerBound
			val maxObject = tupleType.sizeRange.upperBound
			if (minObject.isInt)
			{
				val min = minObject.extractInt
				for (i in 1 .. min)
				{
					list.add(tupleType.typeAtIndex(i))
				}
				if (!minObject.equals(maxObject))
				{
					// Add "..., opt1, opt2" etc. to show the optional
					// arguments.
					list.add(null)
					var max = tupleType.typeTuple.tupleSize + 1
					max = max(max, min + 1)
					for (i in min + 1 .. max)
					{
						list.add(tupleType.typeAtIndex(i))
					}
					if (!maxObject.equalsInt(max))
					{
						// Add "..., 5" or whatever the max size is (or
						// infinity).
						list.add(null)
						list.add(maxObject)
					}
				}
				printListOnAvoidingIndent(list, builder, recursionMap, indent)
			}
			else
			{
				builder.append("?")
			}
		}
		builder.append("]→")
		self.returnType.printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
		if (self.declaredExceptions.setSize > 0)
		{
			builder.append("^")
			list.clear()
			for (elem in self.declaredExceptions)
			{
				list.add(elem)
			}
			printListOnAvoidingIndent(list, builder, recursionMap, indent)
		}
	}

	override fun o_AcceptsArgTypesFromFunctionType(
		self: AvailObject,
		functionType: A_Type): Boolean =
			functionType.argsTupleType.isSubtypeOf(
				self.slot(ARGS_TUPLE_TYPE))

	override fun o_AcceptsListOfArgTypes(
		self: AvailObject,
		argTypes: List<A_Type>): Boolean
	{
		val tupleType: A_Type = self.slot(ARGS_TUPLE_TYPE)
		var i = 1
		val end = argTypes.size
		while (i <= end)
		{
			if (!argTypes[i - 1].isSubtypeOf(tupleType.typeAtIndex(i)))
			{
				return false
			}
			i++
		}
		return true
	}

	override fun o_AcceptsListOfArgValues(
		self: AvailObject,
		argValues: List<A_BasicObject>): Boolean
	{
		val tupleType: A_Type = self.slot(ARGS_TUPLE_TYPE)
		var i = 1
		val end = argValues.size
		while (i <= end)
		{
			val arg = argValues[i - 1]
			if (!arg.isInstanceOf(tupleType.typeAtIndex(i)))
			{
				return false
			}
			i++
		}
		return true
	}

	override fun o_AcceptsTupleOfArgTypes(
		self: AvailObject,
		argTypes: A_Tuple): Boolean
	{
		val tupleType: A_Type = self.slot(ARGS_TUPLE_TYPE)
		var i = 1
		val end = argTypes.tupleSize
		while (i <= end)
		{
			if (!argTypes.tupleAt(i).isSubtypeOf(tupleType.typeAtIndex(i)))
			{
				return false
			}
			i++
		}
		return true
	}

	override fun o_AcceptsTupleOfArguments(
		self: AvailObject,
		arguments: A_Tuple): Boolean =
			arguments.isInstanceOf(self.slot(ARGS_TUPLE_TYPE))

	override fun o_ArgsTupleType(self: AvailObject): A_Type =
		self.slot(ARGS_TUPLE_TYPE)

	override fun o_CouldEverBeInvokedWith(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>): Boolean
	{
		val tupleType: A_Type = self.slot(ARGS_TUPLE_TYPE)
		var i = 1
		val end = argRestrictions.size
		while (i <= end)
		{
			if (!argRestrictions[i - 1].intersectsType(
					tupleType.typeAtIndex(i)))
			{
				return false
			}
			i++
		}
		return true
	}

	override fun o_DeclaredExceptions(self: AvailObject): A_Set =
		self.slot(DECLARED_EXCEPTIONS)

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsFunctionType(self)

	override fun o_EqualsFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): Boolean
	{
		when
		{
			self.sameAddressAs(aFunctionType) -> return true
			self.hash() != aFunctionType.hash() -> return false
			!self.slot(ARGS_TUPLE_TYPE)
				.equals(aFunctionType.argsTupleType) -> return false
			!self.slot(RETURN_TYPE)
				.equals(aFunctionType.returnType) -> return false
			!self.slot(DECLARED_EXCEPTIONS).equals(
				aFunctionType.declaredExceptions
			) -> return false
			!isShared ->
			{
				aFunctionType.makeImmutable()
				self.becomeIndirectionTo(aFunctionType)
			}
			!aFunctionType.descriptor().isShared ->
			{
				self.makeImmutable()
				aFunctionType.becomeIndirectionTo(self)
			}
		}
		return true
	}

	override fun o_Hash(self: AvailObject): Int =
		self.synchronizeIf(isShared) { hash(self) }

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfFunctionType(self)

	override fun o_IsSupertypeOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): Boolean
	{
		if (self.equals(aFunctionType))
		{
			return true
		}
		if (!aFunctionType.returnType.isSubtypeOf(self.slot(RETURN_TYPE)))
		{
			return false
		}
		val inners: A_Set = self.slot(DECLARED_EXCEPTIONS)
		// A ⊆ B if everything A can throw was declared by B
		each_outer@ for (outer in aFunctionType.declaredExceptions)
		{
			for (inner in inners)
			{
				if (outer.isSubtypeOf(inner))
				{
					continue@each_outer
				}
			}
			return false
		}
		return self.slot(ARGS_TUPLE_TYPE).isSubtypeOf(
			aFunctionType.argsTupleType)
	}

	override fun o_IsVacuousType(self: AvailObject): Boolean
	{
		val argsTupleType: A_Type = self.slot(ARGS_TUPLE_TYPE)
		val sizeRange = argsTupleType.sizeRange
		return sizeRange.lowerBound.lessThan(sizeRange.upperBound)
	}

	override fun o_ReturnType(self: AvailObject): A_Type =
		self.slot(RETURN_TYPE)

	override fun o_TypeIntersection(
		self: AvailObject,
		another: A_Type
	): A_Type = when
	{
		self.isSubtypeOf(another) -> self
		another.isSubtypeOf(self) -> another
		else -> another.typeIntersectionOfFunctionType(self)
	}

	override fun o_TypeIntersectionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): A_Type
	{
		val tupleTypeUnion =
			self.slot(ARGS_TUPLE_TYPE).typeUnion(
				aFunctionType.argsTupleType)
		val returnType =
			self.slot(RETURN_TYPE).typeIntersection(
				aFunctionType.returnType)
		var exceptions = emptySet
		for (outer in self.slot(DECLARED_EXCEPTIONS))
		{
			for (inner in aFunctionType.declaredExceptions)
			{
				exceptions = exceptions.setWithElementCanDestroy(
					outer.typeIntersection(inner), true)
			}
		}
		exceptions = normalizeExceptionSet(exceptions)
		return functionTypeFromArgumentTupleType(
			tupleTypeUnion, returnType, exceptions)
	}

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type = when
	{
		self.isSubtypeOf(another) -> another
		another.isSubtypeOf(self) -> self
		else -> another.typeUnionOfFunctionType(self)
	}

	override fun o_TypeUnionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): A_Type
	{
		// Subobjects may be shared with result.
		self.makeSubobjectsImmutable()
		val tupleTypeIntersection =
			self.slot(ARGS_TUPLE_TYPE).typeIntersection(
				aFunctionType.argsTupleType)
		val returnType =
			self.slot(RETURN_TYPE).typeUnion(aFunctionType.returnType)
		val exceptions = normalizeExceptionSet(
			self.slot(DECLARED_EXCEPTIONS).setUnionCanDestroy(
				aFunctionType.declaredExceptions, true))
		return functionTypeFromArgumentTupleType(
			tupleTypeIntersection, returnType, exceptions)
	}

	@ThreadSafe
	override fun o_SerializerOperation(
		self: AvailObject): SerializerOperation =
			SerializerOperation.FUNCTION_TYPE

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("function type")
		writer.write("arguments type")
		self.slot(ARGS_TUPLE_TYPE).writeSummaryTo(writer)
		writer.write("return type")
		self.slot(RETURN_TYPE).writeSummaryTo(writer)
		writer.write("declared exceptions")
		self.slot(DECLARED_EXCEPTIONS).writeSummaryTo(writer)
		writer.endObject()
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("function type")
		writer.write("arguments type")
		self.slot(ARGS_TUPLE_TYPE).writeTo(writer)
		writer.write("return type")
		self.slot(RETURN_TYPE).writeTo(writer)
		writer.write("declared exceptions")
		self.slot(DECLARED_EXCEPTIONS).writeTo(writer)
		writer.endObject()
	}

	override fun mutable(): FunctionTypeDescriptor = mutable

	override fun immutable(): FunctionTypeDescriptor = immutable

	override fun shared(): FunctionTypeDescriptor = shared

	companion object
	{
		/**
		 * Prettily print the specified [list][List] of [objects][AvailObject]
		 * to the specified [stream][StringBuilder].
		 *
		 * @param objects
		 *   The objects to print.
		 * @param aStream
		 *   Where to print the objects.
		 * @param recursionMap
		 *   Which ancestor objects are currently being printed.
		 * @param indent
		 *   What level to indent subsequent lines.
		 */
		private fun printListOnAvoidingIndent(
			objects: List<A_BasicObject?>,
			builder: StringBuilder,
			recursionMap: IdentityHashMap<A_BasicObject, Void>,
			indent: Int
		) : Unit = with(builder)
		{
			val objectCount = objects.size
			var anyBreaks = false
			val tempStrings = mutableListOf<String>()
			for (elem in objects)
			{
				val str = elem?.toString() ?: "…"
				tempStrings.add(str)
				if (str.indexOf('\n') > -1)
				{
					anyBreaks = true
				}
			}
			if (anyBreaks)
			{
				for (i in 0 until objectCount)
				{
					if (i > 0)
					{
						append(',')
					}
					newlineTab(indent)
					val item = objects[i]
					item?.printOnAvoidingIndent(
						builder,
						recursionMap,
						indent + 1)
					?: append("…")
				}
			}
			else
			{
				for (i in 0 until objectCount)
				{
					if (i > 0)
					{
						append(", ")
					}
					append(tempStrings[i])
				}
			}
		}

		/**
		 * The hash value is stored raw in the object's
		 * [hashOrZero][IntegerSlots.HASH_OR_ZERO] slot if it has been computed,
		 * otherwise that slot is zero. If a zero is detected, compute the hash
		 * and store it in hashOrZero. Note that the hash can (extremely rarely)
		 * be zero, in which case the hash must be computed on demand every time
		 * it is requested. Answer the raw hash value.
		 *
		 * @param self
		 *   The object.
		 * @return
		 *   The hash.
		 */
		private fun hash(self: AvailObject): Int
		{
			var hash = self.slot(HASH_OR_ZERO)
			if (hash == 0)
			{
				hash = AvailObject.combine4(
					self.slot(RETURN_TYPE).hash(),
					self.slot(DECLARED_EXCEPTIONS).hash(),
					self.slot(ARGS_TUPLE_TYPE).hash(),
					0x10447107)
				self.setSlot(HASH_OR_ZERO, hash)
			}
			return hash
		}

		/** The mutable [FunctionTypeDescriptor]. */
		private val mutable = FunctionTypeDescriptor(Mutability.MUTABLE)

		/** The immutable [FunctionTypeDescriptor]. */
		private val immutable = FunctionTypeDescriptor(Mutability.IMMUTABLE)

		/** The shared [FunctionTypeDescriptor]. */
		private val shared = FunctionTypeDescriptor(Mutability.SHARED)

		/**
		 * The most general function type.
		 */
		private val mostGeneralType: A_Type =
			functionTypeReturning(TOP.o).makeShared()

		/**
		 * Answer the top (i.e., most general) function type.
		 *
		 * @return
		 *   The function type "[…]→⊤".
		 */
		fun mostGeneralFunctionType(): A_Type = mostGeneralType

		/**
		 * The metatype of any function types.
		 */
		private val meta: A_Type = instanceMeta(mostGeneralType).makeShared()

		/**
		 * Answer the metatype for all function types.  This is just an
		 * [instance&#32;type][InstanceTypeDescriptor] on the
		 * [most&#32;general&#32;type][mostGeneralFunctionType].
		 *
		 * @return
		 *   The (meta-)type of the function type "[…]→⊤".
		 */
		fun functionMeta(): A_Type = meta

		/**
		 * Normalize the specified exception [set][SetDescriptor] by eliminating
		 * bottom and types for which a supertype is also present.
		 *
		 * @param exceptionSet
		 *   An exception [set][SetDescriptor]. Must include only
		 *   [types][TypeDescriptor].
		 * @return
		 *   A normalized exception [set][SetDescriptor].
		 * @see [A_Type.declaredExceptions]
		 */
		private fun normalizeExceptionSet(exceptionSet: A_Set): A_Set
		{
			val setSize = exceptionSet.setSize
			return when
			{
				// This is probably the most common case – no checked
				// exceptions. Return the argument.
				setSize == 0 -> emptySet
				// This is probably the next most common case – just one checked
				// exception. If the element is bottom, then exclude it.
				setSize == 1 && exceptionSet.single().isBottom -> emptySet
				setSize == 1 -> exceptionSet

				// Actually normalize the set. That is, eliminate types for
				// which a supertype is already present. Also, eliminate bottom.
				else ->
				{
					var normalizedSet = emptySet
					each_outer@ for (outer in exceptionSet)
					{
						if (!outer.isBottom)
						{
							for (inner in exceptionSet)
							{
								if (outer.isSubtypeOf(inner))
								{
									continue@each_outer
								}
							}
							normalizedSet =
								normalizedSet.setWithElementCanDestroy(
									outer, true)
						}
					}
					normalizedSet
				}
			}
		}

		/**
		 * Answer a new function type whose instances accept arguments which, if
		 * collected in a tuple, match the specified
		 * [tuple&#32;type][TupleTypeDescriptor].  The instances of this
		 * function type should also produce values that conform to the return
		 * type, and may only raise checked exceptions whose instances are
		 * subtypes of one or more members of the supplied exception set.
		 *
		 * @param argsTupleType
		 *   A [tuple&#32;type][TupleTypeDescriptor] describing the
		 *   [types][TypeDescriptor] of the arguments that instances should
		 *   accept.
		 * @param returnType
		 *   The [type][TypeDescriptor] of value that an instance should produce.
		 * @param exceptionSet
		 *   The [set][SetDescriptor] of checked
		 *   [exception&#32;types][ObjectTypeDescriptor] that an instance may
		 *   raise.
		 * @return
		 *  A function type.
		 */
		fun functionTypeFromArgumentTupleType(
			argsTupleType: A_Type,
			returnType: A_Type?,
			exceptionSet: A_Set): A_Type
		{
			assert(argsTupleType.isTupleType)
			val exceptionsReduced = normalizeExceptionSet(exceptionSet)
			return mutable.createImmutable {
				setSlot(ARGS_TUPLE_TYPE, argsTupleType)
				setSlot(DECLARED_EXCEPTIONS, exceptionsReduced)
				setSlot(RETURN_TYPE, returnType!!)
				setSlot(HASH_OR_ZERO, 0)
			}
		}

		/**
		 * Answer a new function type whose instances accept arguments whose
		 * types conform to the corresponding entries in the provided tuple of
		 * types, produce values that conform to the return type, and raise no
		 * checked exceptions.
		 *
		 * @param argTypes
		 *   A [tuple][TupleDescriptor] of [types][TypeDescriptor] of the
		 *   arguments that instances should accept.
		 * @param returnType
		 *  The [type][TypeDescriptor] of value that an instance should produce.
		 * @return
		 *   A function type.
		 */
		@JvmOverloads
		fun functionType(
			argTypes: A_Tuple,
			returnType: A_Type,
			exceptionSet: A_Set = emptySet): A_Type
		{
			val tupleType = tupleTypeForSizesTypesDefaultType(
				singleInt(argTypes.tupleSize), argTypes, bottom)
			return functionTypeFromArgumentTupleType(
				tupleType, returnType, exceptionSet)
		}

		/**
		 * Answer a new function type that doesn't specify how many arguments
		 * its conforming functions have.  This is a useful kind of function
		 * type for discussing things like a general function invocation
		 * operation.
		 *
		 * @param returnType
		 *   The type of object returned by a function that conforms to the
		 *   function type being defined.
		 * @return
		 *   A function type.
		 */
		fun functionTypeReturning(returnType: A_Type): A_Type =
			functionTypeFromArgumentTupleType(
				bottom,
				returnType,  // TODO: [MvG] Probably should allow any exception.
				emptySet)
	}
}
