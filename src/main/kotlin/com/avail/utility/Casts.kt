/*
 * Casts.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.utility

/**
 * When you know better, this bypasses static type-safety, while leaving
 * dynamic type-safety intact, other than generics and nulls.
 *
 * @param I
 *   The input type.
 * @param O
 *   The output type.
 * @return
 *   The receiver, strengthened to the indicated type `O`.
 */
@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <I, O : I?> I.cast (): O = this as O

/**
 * If the receiver is `null`, answer `false`. Otherwise run the body with the
 * non-`null` receiver and answer the resulting [Boolean].
 *
 * @receiver
 *   Either `null` or a value to use as the receiver of the provided function.
 * @param body
 *   `true` iff the receiver is non-null and the [body] yields `true` for it.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
inline fun <T> T?.notNullAnd (body: T.() -> Boolean): Boolean =
	this !== null && body(this)
