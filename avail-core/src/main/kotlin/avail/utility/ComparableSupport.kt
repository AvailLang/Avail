/*
 * ComparableSupport.kt
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
