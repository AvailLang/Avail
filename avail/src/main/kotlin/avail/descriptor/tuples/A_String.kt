/*
 * A_String.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.tuples

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.utility.cast

/**
 * `A_String` is an interface that specifies the string-specific operations that
 * an [AvailObject] must implement.  It's a sub-interface of [A_Tuple] (which is
 * itself a sub-interface of [A_BasicObject].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_String : A_Tuple
{
	/**
	 * A construct for mapping indices within Avail strings, which can contain
	 * code points up to 0x10FFFF, to and from indices in the corresponding Java
	 * strings, which must use two UTF-16 code points (a surrogate pair) for any
	 * character above 0xFFFF.
	 */
	class SurrogateIndexConverter(string: String)
	{
		private val highSurrogatesInJavaString = string.indices.filter {
			string[it].isHighSurrogate()
		}.toTypedArray()

		private val highCodePointsInAvailString = highSurrogatesInJavaString
			.mapIndexed { arrayPos, javaIndex ->
				javaIndex - arrayPos
			}.toTypedArray()

		/**
		 * Convert from a zero-based index into the Avail string, where code
		 * points can be up to 0x10FFFF, to a zero-based index into the Java
		 * string that would have to use surrogate pairs (two [Char]s) per code
		 * point over 0xFFFF.  The resulting index will be greater than or equal
		 * to the Avail index, depending how many large code points occur prior
		 * to the given index.
		 */
		fun availIndexToJavaIndex(availZeroIndex: Int): Int
		{
			val position =
				highCodePointsInAvailString.binarySearch(availZeroIndex)
			return when
			{
				// An exact match, which means we're pointing directly at a code
				// point bigger than 0xFFFF.
				position >= 0 -> availZeroIndex + position
				else -> availZeroIndex + (-1 - position)
			}
		}

		/**
		 * Convert from a zero-based index into a Java string, which contains
		 * only code points in 0..0xFFFF, using two to represent Unicode values
		 * in 0x10000-0x10FFFF, into zero-based index into an Avail string,
		 * which can contain arbitrary code points.
		 */
		fun javaIndexToAvailIndex(javaZeroIndex: Int): Int
		{
			val position =
				highSurrogatesInJavaString.binarySearch(javaZeroIndex)
			return when
			{
				// An exact match, which means we're pointing directly at a high
				// surrogate in the Java string.
				position >= 0 -> javaZeroIndex - position
				else -> javaZeroIndex - (-1 - position)
			}
		}
	}

	companion object
	{
		/**
		 * Construct a Java [string][String] from the receiver, an Avail
		 * [string][StringDescriptor].
		 *
		 * @return
		 *   The corresponding Java string.
		 */
		fun A_String.asNativeString(): String =
			dispatch { o_AsNativeString(it) }

		/**
		 * Even though [A_Tuple.copyTupleFromToCanDestroy] would perform the
		 * same activity, this method returns the stronger [A_String] type as a
		 * convenience, when the code knows it's working on strings.
		 *
		 * @param start
		 *   The start of the range to extract.
		 * @param end
		 *   The end of the range to extract.
		 * @param canDestroy
		 *   Whether the original object may be destroyed if mutable.
		 * @return
		 *   The substring.
		 */
		fun A_String.copyStringFromToCanDestroy(
			start: Int,
			end: Int,
			canDestroy: Boolean
		): A_String = dispatch {
			// The cast is safe, since a subtuple of a string is a string.
			o_CopyTupleFromToCanDestroy(it, start, end, canDestroy).cast()
		}
	}
}
