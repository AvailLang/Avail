/*
 * FloatDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *    list of conditions and the following disclaimer in the documentation
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
package com.avail.descriptor.numbers

import com.avail.annotations.AvailMethod
import com.avail.annotations.ThreadSafe
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order.*
import com.avail.descriptor.numbers.DoubleDescriptor.Companion.addDoubleAndIntegerCanDestroy
import com.avail.descriptor.numbers.DoubleDescriptor.Companion.compareDoubleAndInteger
import com.avail.descriptor.numbers.DoubleDescriptor.Companion.fromDoubleRecycling
import com.avail.descriptor.numbers.FloatDescriptor.IntegerSlots.Companion.RAW_INT
import com.avail.descriptor.representation.*
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeTag
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.lang.Double.isNaN
import java.lang.Float.*
import java.util.*
import kotlin.math.floor

/**
 * A boxed, identityless Avail representation of IEEE-754 floating point values.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class FloatDescriptor private constructor(
	mutability: Mutability
) : AbstractNumberDescriptor(
	mutability, TypeTag.FLOAT_TAG, null, IntegerSlots::class.java
) {
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/** Only the low 32 bits are used for the [RAW_INT]. */
		RAW_INT_AND_MORE;

		companion object {
			/** The Java `float` value, packed into an `int` field. */
			@JvmField
			val RAW_INT = BitField(RAW_INT_AND_MORE, 0, 32)
		}
	}

	@AvailMethod
	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		aStream: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		aStream.append(self.extractFloat())
	}

	@AvailMethod
	override fun o_AddToInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = fromFloatRecycling(
		sign.limitFloat() + getFloat(self), self, canDestroy)

	@AvailMethod
	override fun o_AddToIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number {
		val sum: Double = addDoubleAndIntegerCanDestroy(
			getDouble(self),
			anInteger,
			canDestroy)
		return fromFloatRecycling(sum.toFloat(), self, canDestroy)
	}

	@AvailMethod
	override fun o_AddToDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number = fromDoubleRecycling(
		doubleObject.extractDouble() + getFloat(self),
		doubleObject,
		canDestroy)

	@AvailMethod
	override fun o_AddToFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number = objectFromFloatRecycling(
		floatObject.extractFloat() + getFloat(self),
		self,
		floatObject,
		canDestroy)

	@AvailMethod
	override fun o_DivideCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = aNumber.divideIntoFloatCanDestroy(self, canDestroy)

	@AvailMethod
	override fun o_DivideIntoInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = fromFloatRecycling(
		sign.limitFloat() / getFloat(self), self, canDestroy)

	/*
	 * Do the math with doubles so that spurious overflows *can't* happen. That
	 * is, conversion from an integer to a float might overflow even though the
	 * quotient wouldn't, but the expanded range of a double should safely hold
	 * any integer that wouldn't cause the quotient to go out of finite float
	 * range.
	 */
	@AvailMethod
	override fun o_DivideIntoIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = fromFloatRecycling(
		(anInteger.extractDouble() / getDouble(self)).toFloat(),
		self,
		canDestroy)

	@AvailMethod
	override fun o_DivideIntoDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number = fromDoubleRecycling(
		doubleObject.extractDouble() / getDouble(self),
		doubleObject,
		canDestroy)

	@AvailMethod
	override fun o_DivideIntoFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number = objectFromFloatRecycling(
		floatObject.extractFloat() / getFloat(self),
		self,
		floatObject,
		canDestroy)

	@AvailMethod
	override fun o_ExtractFloat(self: AvailObject): Float = getFloat(self)

	@AvailMethod
	override fun o_ExtractDouble(self: AvailObject): Double = getDouble(self)

	@AvailMethod
	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean {
		when {
			!another.equalsFloat(getFloat(self)) -> return false
			!isShared -> self.becomeIndirectionTo(another.makeImmutable())
			!another.descriptor().isShared ->
				another.becomeIndirectionTo(self.makeImmutable())
		}
		return true
	}

	/*
	 * Java float equality is irreflexive, and therefore useless to us, since
	 * Avail sets (at least) require reflexive equality.  Compare the exact bits
	 * instead.
	 */
	@AvailMethod
	override fun o_EqualsFloat(
		self: AvailObject,
		aFloat: Float
	): Boolean =
		floatToRawIntBits(getFloat(self)) == floatToRawIntBits(aFloat)

	@AvailMethod
	override fun o_Hash(self: AvailObject): Int =
		(self.slot(RAW_INT) xor 0x16AE2BFD) * multiplier

	@AvailMethod
	override fun o_IsFloat(self: AvailObject) = true

	@AvailMethod
	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	) = aType.isSupertypeOfPrimitiveTypeEnum(Types.FLOAT)

	override fun o_IsNumericallyIntegral(self: AvailObject): Boolean =
		getFloat(self).let {
			!isInfinite(it)
				&& !isNaN(it)
				&& floor(it.toDouble()) == it.toDouble()
		}

	@AvailMethod
	override fun o_Kind(self: AvailObject): A_Type = Types.FLOAT.o()

	override fun o_MarshalToJava(
		self: AvailObject,
		ignoredClassHint: Class<*>?
	): Any? = getFloat(self)

	@AvailMethod
	override fun o_MinusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = aNumber.subtractFromFloatCanDestroy(self, canDestroy)

	@AvailMethod
	override fun o_MultiplyByInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = fromFloatRecycling(
		(sign.limitDouble() * getFloat(self)).toFloat(), self, canDestroy)

	/*
	 * Do the math with doubles to avoid intermediate overflow of the integer in
	 * the case that the product could still be represented as a float.
	 */
	@AvailMethod
	override fun o_MultiplyByIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = fromFloatRecycling(
		(anInteger.extractDouble() * getDouble(self)).toFloat(),
		self,
		canDestroy)

	@AvailMethod
	override fun o_MultiplyByDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number = fromDoubleRecycling(
		doubleObject.extractDouble() * getDouble(self),
		doubleObject,
		canDestroy)

	@AvailMethod
	override fun o_MultiplyByFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number = objectFromFloatRecycling(
		floatObject.extractFloat() * getFloat(self),
		self,
		floatObject,
		canDestroy)

	@AvailMethod
	override fun o_NumericCompare(
		self: AvailObject,
		another: A_Number
	): Order = another.numericCompareToDouble(getDouble(self)).reverse()

	@AvailMethod
	override fun o_NumericCompareToDouble(
		self: AvailObject,
		double1: Double
	): Order = getDouble(self).let {
		when {
			it == double1 -> EQUAL
			it < double1 -> LESS
			it > double1 -> MORE
			else -> INCOMPARABLE
		}
	}

	@AvailMethod
	override fun o_NumericCompareToInfinity(
		self: AvailObject,
		sign: Sign
	): Order {
		val thisDouble = getDouble(self)
		if (isNaN(thisDouble)) {
			return INCOMPARABLE
		}
		val comparison = thisDouble.compareTo(sign.limitDouble())
		return when {
			comparison < 0 -> LESS
			comparison > 0 -> MORE
			else -> EQUAL
		}
	}

	@AvailMethod
	override fun o_NumericCompareToInteger(
		self: AvailObject,
		anInteger: AvailObject
	): Order = compareDoubleAndInteger(getDouble(self), anInteger)

	@AvailMethod
	override fun o_PlusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = aNumber.addToFloatCanDestroy(self, canDestroy)

	@AvailMethod
	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.FLOAT

	@AvailMethod
	override fun o_SubtractFromInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = fromFloatRecycling(
		sign.limitDouble().toFloat() - getFloat(self), self, canDestroy)

	@AvailMethod
	override fun o_SubtractFromIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = fromFloatRecycling(
		addDoubleAndIntegerCanDestroy(-getDouble(self), anInteger, canDestroy)
			.toFloat(),
		self,
		canDestroy)

	@AvailMethod
	override fun o_SubtractFromDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number = fromDoubleRecycling(
		doubleObject.extractDouble() - getFloat(self),
		doubleObject,
		canDestroy)

	@AvailMethod
	override fun o_SubtractFromFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number = objectFromFloatRecycling(
		floatObject.extractFloat() - getFloat(self),
		self,
		floatObject,
		canDestroy)

	@AvailMethod
	override fun o_TimesCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = aNumber.multiplyByFloatCanDestroy(self, canDestroy)

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.write(getFloat(self))

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object {
		/**
		 * Extract a Java `float` from the argument, an Avail
		 * [float][FloatDescriptor].
		 *
		 * @param self
		 *   An Avail single-precision floating point number.
		 * @return
		 *   The corresponding Java float.
		 */
		private fun getFloat(self: AvailObject): Float =
			intBitsToFloat(self.slot(RAW_INT))

		/**
		 * Extract a Java `double` from the argument, an [ ].
		 *
		 * @param self
		 *   An Avail single-precision floating point number.
		 * @return
		 *   The corresponding Java double.
		 */
		private fun getDouble(self: AvailObject): Double =
			getFloat(self).toDouble()

		/**
		 * Construct an Avail boxed floating point object from the passed
		 * `float`.  Don't answer an existing object.
		 *
		 * @param aFloat
		 *   The Java `float` to box.
		 * @return
		 *   The boxed Avail `float`.
		 */
		@JvmStatic
		fun fromFloat(aFloat: Float): A_Number =
			mutable.create().apply {
				setSlot(RAW_INT, floatToRawIntBits(aFloat))
			}

		/**
		 * Construct an Avail boxed floating point object from the passed
		 * `float`.
		 *
		 * @param aFloat
		 *   The Java `float` to box.
		 * @param recyclable1
		 *   A boxed float that may be reused if it's mutable.
		 * @param canDestroy
		 *   Whether the given float can be reused if it's mutable.
		 * @return
		 *   The boxed Avail `FloatDescriptor floating point object`.
		 */
		fun fromFloatRecycling(
			aFloat: Float,
			recyclable1: A_Number,
			canDestroy: Boolean
		): A_Number {
			val result =
				if (canDestroy && recyclable1.descriptor().isMutable) {
					recyclable1 as AvailObject
				} else {
					mutable.create()
				}
			result.setSlot(RAW_INT, floatToRawIntBits(aFloat))
			return result
		}

		/**
		 * Construct an Avail boxed floating point object from the passed
		 * `float`.
		 *
		 * @param aFloat
		 *   The Java `float` to box.
		 * @param recyclable1
		 *   A boxed Avail `float` that may be reused if it's mutable.
		 * @param recyclable2
		 *   Another boxed Avail `float` that may be reused if it's mutable.
		 * @param canDestroy
		 *   Whether one of the given boxed Avail floats can be reused if it's
		 *   mutable.
		 * @return
		 *   The boxed Avail `float`.
		 */
		fun objectFromFloatRecycling(
			aFloat: Float,
			recyclable1: A_Number,
			recyclable2: A_Number,
			canDestroy: Boolean
		): A_Number {
			val result: AvailObject = when {
				canDestroy && recyclable1.descriptor().isMutable ->
					recyclable1 as AvailObject
				canDestroy && recyclable2.descriptor().isMutable ->
					recyclable2 as AvailObject
				else -> mutable.create()
			}
			result.setSlot(RAW_INT, floatToRawIntBits(aFloat))
			return result
		}

		/**
		 * Answer the Avail object representing [Float.POSITIVE_INFINITY].
		 *
		 * @return
		 *   The Avail object for float positive infinity.
		 */
		@JvmStatic
		fun floatPositiveInfinity(): A_Number = Sign.POSITIVE.limitFloatObject()

		/**
		 * Answer the Avail object representing [Float.NEGATIVE_INFINITY].
		 *
		 * @return
		 *   The Avail object for float negative infinity.
		 */
		@JvmStatic
		fun floatNegativeInfinity(): A_Number = Sign.NEGATIVE.limitFloatObject()

		/**
		 * Answer the Avail object representing `Float#NaN`.
		 *
		 * @return
		 *   The Avail object for float not-a-number.
		 */
		@JvmStatic
		fun floatNotANumber(): A_Number = Sign.INDETERMINATE.limitFloatObject()

		/**
		 * Answer the Avail object representing `0.0f`.
		 *
		 * @return
		 *   The Avail object for float (positive) zero.
		 */
		fun floatZero(): A_Number = Sign.ZERO.limitFloatObject()

		/** The mutable [FloatDescriptor].  */
		private val mutable = FloatDescriptor(Mutability.MUTABLE)

		/** The immutable [FloatDescriptor].  */
		private val immutable = FloatDescriptor(Mutability.IMMUTABLE)

		/** The shared [FloatDescriptor].  */
		private val shared = FloatDescriptor(Mutability.SHARED)
	}
}