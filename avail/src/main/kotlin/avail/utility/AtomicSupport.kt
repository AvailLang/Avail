/*
 * AtomicSupport.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.utility

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater
import kotlin.reflect.KProperty1

/**
 * Create an [AtomicReferenceFieldUpdater] for the given class that contains the
 * (non-static) field, the type of the field, and the field itself, as a
 * property, conveniently extractable with the MyClass::myField reference
 * syntax.  Having this as a helper function allows disambiguation of the field
 * from non-property references (like methods) having the same name.  It's much
 * harder for the client code to do this, as it requires a fresh variable and
 * explicit mention of [KProperty1].
 *
 * This helper uses reified types [T] and [V] to further simplify the caller, as
 * the types can usually be derived directly from the types of the [field].
 */
inline fun <reified T, reified V> atomicUpdater(
	field: KProperty1<T, V>
): AtomicReferenceFieldUpdater<T, V> = newUpdater(
	T::class.java,
	V::class.java,
	field.name)

/**
 * Extract the value of the field, run the [body] transformation, and repeatedly
 * try to [AtomicReferenceFieldUpdater.compareAndSet] from the old value to the
 * new value until successful.
 */
fun <T, V> AtomicReferenceFieldUpdater<T, V>.update(
	parent: T,
	body: V.() -> V
): Unit
{
	do
	{
		val old = get(parent!!)
		val new = old.body()
		val success = compareAndSet(parent, old, new)
	} while (!success)
}
