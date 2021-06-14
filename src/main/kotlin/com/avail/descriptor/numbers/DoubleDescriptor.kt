/*
 * DoubleDescriptor.kt
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
package com.avail.descriptor.numbers

import com.avail.annotations.ThreadSafe
import com.avail.descriptor.numbers.A_Number.Companion.addToDoubleCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.bitShift
import com.avail.descriptor.numbers.A_Number.Companion.divideIntoDoubleCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.equalsDouble
import com.avail.descriptor.numbers.A_Number.Companion.extractDouble
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.multiplyByDoubleCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.noFailMinusCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.numericCompare
import com.avail.descriptor.numbers.A_Number.Companion.numericCompareToDouble
import com.avail.descriptor.numbers.A_Number.Companion.rawUnsignedIntegerAtPut
import com.avail.descriptor.numbers.A_Number.Companion.subtractFromDoubleCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.trimExcessInts
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order.EQUAL
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order.INCOMPARABLE
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order.LESS
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order.MORE
import com.avail.descriptor.numbers.DoubleDescriptor.IntegerSlots.LONG_BITS
import com.avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import com.avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.createUninitializedInteger
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.truncatedFromDouble
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import com.avail.descriptor.types.TypeDescriptor.Types.DOUBLE
import com.avail.descriptor.types.TypeTag
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.lang.Double.doubleToRawLongBits
import java.lang.Double.isInfinite
import java.lang.Double.isNaN
import java.lang.Double.longBitsToDouble
import java.lang.Math.getExponent
import java.lang.Math.scalb
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * A boxed, identityless Avail representation of IEEE-754 double-precision
 * floating point values.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class DoubleDescriptor private constructor(
	mutability: Mutability
) : AbstractNumberDescriptor(
	mutability, TypeTag.DOUBLE_TAG, null, IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/** A [Long] whose bits are to be interpreted as a `double`. */
		LONG_BITS
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		builder.append(getDouble(self))
	}

	override fun o_AddToInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = fromDoubleRecycling(
		sign.limitDouble() + getDouble(self), self, canDestroy)

	override fun o_AddToIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number {
		val sum = addDoubleAndIntegerCanDestroy(
			getDouble(self), anInteger, canDestroy)
		return fromDoubleRecycling(sum, self, canDestroy)
	}

	override fun o_AddToDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		return objectFromDoubleRecycling(
			getDouble(self) + doubleObject.extractDouble(),
			self,
			doubleObject,
			canDestroy)
	}

	override fun o_AddToFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		return fromDoubleRecycling(
			getDouble(self) + floatObject.extractDouble(),
			self,
			canDestroy)
	}

	override fun o_DivideCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number {
		return aNumber.divideIntoDoubleCanDestroy(self, canDestroy)
	}

	override fun o_DivideIntoInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number {
		return fromDoubleRecycling(
			sign.limitDouble() / getDouble(self),
			self,
			canDestroy)
	}

	override fun o_DivideIntoIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number
	{
		return fromDoubleRecycling(
			anInteger.extractDouble() / getDouble(self),
			self,
			canDestroy)
	}

	override fun o_DivideIntoDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		return objectFromDoubleRecycling(
			doubleObject.extractDouble() / getDouble(self),
			self,
			doubleObject,
			canDestroy)
	}

	override fun o_DivideIntoFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		return fromDoubleRecycling(
			floatObject.extractDouble() / getDouble(self),
			self,
			canDestroy)
	}

	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean {
		when {
			!(another as A_Number).equalsDouble(getDouble(self)) -> return false
			!isShared -> self.becomeIndirectionTo(another.makeImmutable())
			!another.descriptor().isShared ->
				another.becomeIndirectionTo(self.makeImmutable())
		}
		return true
	}

	override fun o_EqualsDouble(
		self: AvailObject,
		aDouble: Double
	): Boolean {
		// Java double equality is irreflexive, and therefore useless to us,
		// since Avail sets (at least) require reflexive equality. Compare the
		// exact bits instead.
		return (doubleToRawLongBits(getDouble(self))
			== doubleToRawLongBits(aDouble))
	}

	override fun o_ExtractDouble(self: AvailObject): Double = getDouble(self)

	override fun o_ExtractFloat(self: AvailObject) = getDouble(self).toFloat()

	override fun o_Hash(self: AvailObject): Int {
		val bits = self.slot(LONG_BITS)
		val low = (bits shr 32).toInt()
		val high = bits.toInt()
		return (low xor 0x29F2EAB8) * multiplier - (high xor 0x47C453FD)
	}

	override fun o_IsDouble(self: AvailObject) = true

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	) = aType.isSupertypeOfPrimitiveTypeEnum(DOUBLE)

	override fun o_IsNumericallyIntegral(self: AvailObject) =
		getDouble(self).let {
			!isInfinite(it) && !isNaN(it) && floor(it) == it
		}

	override fun o_Kind(self: AvailObject): A_Type = DOUBLE.o

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?
	) = getDouble(self)

	override fun o_MinusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = aNumber.subtractFromDoubleCanDestroy(self, canDestroy)

	override fun o_MultiplyByInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = fromDoubleRecycling(
		sign.limitDouble() * getDouble(self), self, canDestroy)

	override fun o_MultiplyByIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = fromDoubleRecycling(
		anInteger.extractDouble() * getDouble(self), self, canDestroy)

	override fun o_MultiplyByDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number = objectFromDoubleRecycling(
		doubleObject.extractDouble() * getDouble(self),
		self,
		doubleObject,
		canDestroy)

	override fun o_MultiplyByFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number = fromDoubleRecycling(
		floatObject.extractDouble() * getDouble(self), self, canDestroy)

	override fun o_NumericCompare(
		self: AvailObject,
		another: A_Number
	): Order = another.numericCompareToDouble(getDouble(self)).reverse()

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

	override fun o_NumericCompareToInteger(
		self: AvailObject,
		anInteger: AvailObject
	): Order = compareDoubleAndInteger(getDouble(self), anInteger)

	override fun o_NumericCompareToDouble(
		self: AvailObject,
		aDouble: Double
	): Order = compareDoubles(getDouble(self), aDouble)

	override fun o_PlusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = aNumber.addToDoubleCanDestroy(self, canDestroy)

	@ThreadSafe
	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.DOUBLE

	override fun o_SubtractFromInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = fromDoubleRecycling(
		sign.limitDouble() - getDouble(self), self, canDestroy)

	override fun o_SubtractFromIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = fromDoubleRecycling(
		addDoubleAndIntegerCanDestroy(-getDouble(self), anInteger, canDestroy),
		self,
		canDestroy)

	override fun o_SubtractFromDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number = objectFromDoubleRecycling(
		doubleObject.extractDouble() - getDouble(self),
		self,
		doubleObject,
		canDestroy)

	override fun o_SubtractFromFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number = fromDoubleRecycling(
		floatObject.extractDouble() - getDouble(self), self, canDestroy)

	override fun o_TimesCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = aNumber.multiplyByDoubleCanDestroy(self, canDestroy)

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.write(getDouble(self))

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object {
		/**
		 * Extract the Kotlin [Double] from the argument, an Avail
		 * [double][DoubleDescriptor].
		 *
		 * @param self
		 *   An Avail double-precision floating point number.
		 * @return
		 *   The corresponding Java double.
		 */
		private fun getDouble(self: AvailObject): Double =
			longBitsToDouble(self.slot(LONG_BITS))

		/**
		 * Compare two Java double-precision floating point numbers.
		 *
		 * @param double1
		 *   The first double.
		 * @param double2
		 *   The second double.
		 * @return
		 *   An [Order][AbstractNumberDescriptor.Order] describing how double1
		 *   compares to double2.
		 */
		fun compareDoubles(
			double1: Double,
			double2: Double
		) = when {
			double1 == double2 -> EQUAL
			double1 < double2 -> LESS
			double1 > double2 -> MORE
			else -> INCOMPARABLE
		}

		/**
		 * @param aDouble
		 *   The `double` value to compare numerically.
		 * @param anInteger
		 *   An Avail [integer][IntegerDescriptor] to compare against.
		 * @return
		 *   The [Order][AbstractNumberDescriptor.Order] of the double and
		 *   integer.
		 */
		fun compareDoubleAndInteger(
			aDouble: Double,
			anInteger: A_Number
		): Order {
			when {
				isNaN(aDouble) -> return INCOMPARABLE
				isInfinite(aDouble) -> {
					// Compare double precision infinity to a finite integer.
					// Easy, as negative double infinity is below all integers
					// and positive double infinity is above them all.
					return compareDoubles(aDouble, 0.0)
				}
				anInteger.isInt -> {
					// Doubles can exactly represent every int (but not every
					// long).
					return compareDoubles(
						aDouble, anInteger.extractInt().toDouble())
				}
				aDouble == 0.0 -> return zero.numericCompare(anInteger)
			}
			// The integer is beyond an int's range.  Perhaps even beyond a
			// double. For boundary purposes, check now if it's exactly
			// integral.
			val floorD = floor(aDouble)
			// Produce an Avail integer with the exact value from floorD.  If
			// it's more than about 2^60, scale it down to have about 60 bits of
			// data. Since the mantissa is only 53 bits, this will be exact.
			// Since floorD is an integer, it also can't lose any information if
			// it's *not* scaled before being converted to a long.
			val exponent = getExponent(aDouble)
			val exponentAdjustment = max(exponent - 60, 0)
			val normalD = scalb(floorD, -exponentAdjustment)
			assert(Long.MIN_VALUE < normalD && normalD < Long.MAX_VALUE)
			var integer: A_Number = fromLong(normalD.toLong())
			if (exponentAdjustment > 0) {
				integer = integer.bitShift(fromInt(exponentAdjustment), true)
			}
			// We now have an Avail integer representing the exact same quantity as
			// floorD.
			val integerOrder = integer.numericCompare(anInteger)
			val isIntegral = aDouble == floorD
			if (!isIntegral && integerOrder == EQUAL) {
				// d is actually a fraction of a unit bigger than the integer we
				// built, so if that integer and another happen to be equal, the
				// double argument must be considered bigger.
				return MORE
			}
			return integerOrder
		}

		/**
		 * Compute the sum of a Kotlin [Double] and an Avail
		 * [integer][IntegerDescriptor].  Answer the double nearest this ideal
		 * value.
		 *
		 * @param aDouble
		 *   A `double` value.
		 * @param anInteger
		 *   An Avail integer to add.
		 * @param canDestroy
		 *   Whether anInteger can be destroyed (if it's mutable).
		 * @return
		 *   The sum as a `double`.
		 */
		fun addDoubleAndIntegerCanDestroy(
			aDouble: Double,
			anInteger: A_Number,
			canDestroy: Boolean
		): Double {
			if (isInfinite(aDouble)) {
				// The other value is finite, so it doesn't affect the sum.
				return aDouble
			}
			val anIntegerAsDouble = anInteger.extractDouble()
			if (!isInfinite(anIntegerAsDouble)) {
				// Both values are representable as finite doubles.  Easy.
				return anIntegerAsDouble + aDouble
			}
			// The integer is too big for a double, but the sum isn't
			// necessarily.  Split the double operand into truncation toward
			// zero and residue,  Add the truncation (as an integer) to the
			// integer, convert to double, and add the residue (-1 < r < 1).
			val adjustment = floor(aDouble)
			val adjustmentAsInteger: A_Number = truncatedFromDouble(adjustment)
			val adjustedInteger =
				anInteger.minusCanDestroy(adjustmentAsInteger, canDestroy)
			val adjustedIntegerAsDouble = adjustedInteger.extractDouble()
			return aDouble - adjustment + adjustedIntegerAsDouble
		}

		/**
		 * Construct an Avail double-precision floating point object from the
		 * passed `double`.  Do not answer an existing object.
		 *
		 * @param aDouble
		 *   The Kotlin [Double] to box.
		 * @return
		 *   The boxed Avail [double][DoubleDescriptor]-precision floating point
		 *   object.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun fromDouble(aDouble: Double): A_Number =
			mutable.create {
				setSlot(LONG_BITS, doubleToRawLongBits(aDouble))
			}

		/** The [CheckedMethod] for [fromDouble].  */
		val fromDoubleMethod: CheckedMethod = staticMethod(
			DoubleDescriptor::class.java,
			::fromDouble.name,
			A_Number::class.java,
			Double::class.javaPrimitiveType!!)

		/**
		 * Construct an Avail boxed [double][DoubleDescriptor]-precision
		 * floating point object from the passed `double`.
		 *
		 * @param aDouble
		 *   The Kotlin [Double] to box.
		 * @param recyclable1
		 *   An Avail `double` that may be reused if it's mutable.
		 * @param canDestroy
		 *   Whether the passed recyclable can be replaced if it's mutable.
		 * @return
		 *   The boxed Avail `DoubleDescriptor double-precision floating point
		 *   object`.
		 */
		fun fromDoubleRecycling(
			aDouble: Double,
			recyclable1: A_Number,
			canDestroy: Boolean
		): A_Number {
			val result =
				if (canDestroy && recyclable1.descriptor().isMutable) {
					recyclable1 as AvailObject
				} else {
					mutable.create { }
				}
			result.setSlot(LONG_BITS, doubleToRawLongBits(aDouble))
			return result
		}

		/**
		 * Construct an Avail boxed double-precision floating point object from
		 * the passed `double`.
		 *
		 * @param aDouble
		 *   The Kotlin [Double] to box.
		 * @param recyclable1
		 *   An Avail `double` that may be reused if it's mutable.
		 * @param recyclable2
		 *   Another Avail `double` that may be reused if it's mutable.
		 * @param canDestroy
		 *   Whether one of the passed recyclables can be replaced if it's
		 *   mutable.
		 * @return
		 *   The boxed Avail `double`.
		 */
		fun objectFromDoubleRecycling(
			aDouble: Double,
			recyclable1: A_Number,
			recyclable2: A_Number,
			canDestroy: Boolean
		): A_Number {
			val result: AvailObject = when {
				canDestroy && recyclable1.descriptor().isMutable ->
					recyclable1 as AvailObject
				canDestroy && recyclable2.descriptor().isMutable ->
					recyclable2 as AvailObject
				else -> mutable.create { }
			}
			val castAsLong = doubleToRawLongBits(aDouble)
			result.setSlot(LONG_BITS, castAsLong)
			return result
		}

		/**
		 * Given a `double`, produce the `extended integer` that is nearest it
		 * but rounding toward zero.  Double infinities are converted to
		 * extended integer [infinities][InfinityDescriptor].  Do not call with
		 * a `NaN` value.
		 *
		 * @param inputD
		 *   The input `double`.
		 * @return
		 *   The output `extended integer`.
		 */
		fun doubleTruncatedToExtendedInteger(
			inputD: Double
		): A_Number {
			assert(!isNaN(inputD))
			if (inputD >= Long.MIN_VALUE && inputD <= Long.MAX_VALUE) {
				// Common case -- it fits in a long.
				return fromLong(inputD.toLong())
			}
			val neg = inputD < 0.0
			if (isInfinite(inputD)) {
				// Return the corresponding integral infinity.
				return if (neg) negativeInfinity else positiveInfinity
			}
			var d = abs(inputD)
			val exponent = getExponent(d)
			val slots = exponent + 33 shr 5 // probably needs work
			var out: A_Number = createUninitializedInteger(slots)
			d = scalb(d, 1 - slots shl 5)
			// In theory we could extract just the top three 32-bit sections.
			// That would guarantee 65 bits of mantissa, which is more than a
			// double actually captures.
			for (i in slots downTo 1) {
				val unsignedIntSlice = d.toLong()
				out.rawUnsignedIntegerAtPut(i, unsignedIntSlice.toInt())
				d -= unsignedIntSlice.toDouble()
				d = scalb(d, 32)
			}
			out.trimExcessInts()
			if (neg) {
				out = zero.noFailMinusCanDestroy(out, true)
			}
			return out
		}

		/**
		 * Answer the Avail object representing
		 * [java.lang.Double.POSITIVE_INFINITY].
		 *
		 * @return
		 *   The Avail object for double-precision positive infinity.
		 */
		fun doublePositiveInfinity(): A_Number =
			Sign.POSITIVE.limitDoubleObject()

		/**
		 * Answer the Avail object representing
		 * [java.lang.Double.NEGATIVE_INFINITY].
		 *
		 * @return
		 *   The Avail object for double-precision negative infinity.
		 */
		fun doubleNegativeInfinity(): A_Number =
			Sign.NEGATIVE.limitDoubleObject()

		/**
		 * Answer the Avail object representing [java.lang.Double.NaN].
		 *
		 * @return The Avail object for double-precision not-a-number.
		 */
		fun doubleNotANumber(): A_Number =
			Sign.INDETERMINATE.limitDoubleObject()

		/**
		 * Answer the Avail object representing `0.0d`.
		 *
		 * @return
		 *   The Avail object for double-precision (positive) zero.
		 */
		@Suppress("unused")
		fun doubleZero(): A_Number = Sign.ZERO.limitDoubleObject()

		/** The mutable [DoubleDescriptor].  */
		private val mutable = DoubleDescriptor(Mutability.MUTABLE)

		/** The immutable [DoubleDescriptor].  */
		private val immutable = DoubleDescriptor(Mutability.IMMUTABLE)

		/** The shared [DoubleDescriptor].  */
		private val shared = DoubleDescriptor(Mutability.SHARED)
	}
}
