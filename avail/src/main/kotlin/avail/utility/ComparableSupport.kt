/*
 * ComparableSupport.kt
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

package avail.utility

/**
 * Having already compared two things to get an [Int] that represents less-than,
 * equal, or greater-than, answer that if it's not equal (i.e., not 0).
 * Otherwise use the inlined zero-argument block to do further comparisons.
 */
inline fun Int.ifZero(minorBody: () -> Int): Int
{
	// Avoid duplicating the test when inlining.  Not sure if Kotlin deals with
	// this intrinsically.
	return if (this != 0) this else minorBody()
}

/**
 * Having already compared two things to get an [Int] that represents less-than,
 * equal, or greater-than, answer that if it's not equal (i.e., not 0).
 * Otherwise evaluate the two lambdas and use ([Comparable.compareTo]) to
 * produce an [Int] to use instead.
 */
@Suppress("unused")
inline fun <reified C : Comparable<C>> Int.ifZero(
	minor1: () -> C,
	minor2: () -> C
) : Int
{
	// Avoid duplicating the test when inlining.  Not sure if Kotlin deals with
	// this intrinsically.
	return if (this != 0) this else (minor1().compareTo(minor2()))
}

class ChainedComparator<E, C1: Comparable<C1>, C2: Comparable<C2>>
constructor(
	val extractor1: E.()->C1,
	val extractor2: E.()->C2
): Comparator<E>
{
	override fun compare(a: E, b: E): Int =
		a.extractor1().compareTo(b.extractor1())
			.ifZero {
				a.extractor2().compareTo(b.extractor2())
			}
}

/**
 * Given the receiver function and another function, each of which can extract
 * comparable objects, answer a two-argument function that will first use the
 * receiver to extract comparables from its argument, compare them, and if
 * equal, use the other extractor to produce a pair of comparables which are
 * used instead.
 *
 * An example would helpful here.  Say we have a list of sets:
 * ```
 *    val aList = listOf<Set<Long>>()
 *    aList.sortedWith(Set<*>::size thenBy Any::toString)
 * ```
 *
 * This sorts a list by ascending size of each set, breaking ties by
 * alphabetizing by the textual representations.
 */
infix fun <E, C1: Comparable<C1>, C2: Comparable<C2>> ((E)->C1).thenBy(
	otherExtractor: (E)->C2
): (E, E)->Int =
	{ a: E, b: E ->
		this(a).compareTo(this(b)).ifZero {
			otherExtractor(a).compareTo(otherExtractor(b))
		}
	}

/**
 * Similar to the infix [thenBy], this non-infix form takes a vararg of
 * extractors as arguments.
 *
 * Due to limitations in fixed-arity genericity, the extractors are allowed to
 * be any functions that produce a [Comparable], even of different type.  Since
 * only values produced by *the same* extractor will be compared, we just cast
 * without too much worry.
 *
 * An example would helpful here.  Say we have a list of sets:
 * ```
 * val aList = listOf<Set<Long>>()
 * val sorted =
 *     aList.sortedWith(
 *         compareChained({it.size}, {it.toString().take(10)}, {it.max()}))
 * ```
 *
 * This sorts a list by ascending size of each set, breaking ties by
 * alphabetizing by the first (up to) 10 characters of the textual
 * representations, then breaking those ties by the maximum value in the sets.
 */
fun <E> compareChained(
	vararg extractors: (E)->Comparable<*>
): (E, E)->Int =
	function@ { a: E, b: E ->
		for (e in extractors)
		{
			val comparison = e(a)::compareTo.call(e(b))
			if (comparison != 0) return@function comparison
		}
		0
	}
