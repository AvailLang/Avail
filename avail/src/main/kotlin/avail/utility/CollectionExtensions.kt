/*
 * CollectionExtensions.kt
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

@file:Suppress("unused")

package avail.utility

import avail.utility.structures.EnumMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Project the receiver onto an {@link EnumMap}, applying the function to each
 * enum value of the array.
 *
 * @param K
 *   The key type, an [Enum].
 * @param V
 *   The value type produced by the function.
 * @param generator
 *   The function to map keys to values.
 * @return
 *   A fully populated [EnumMap].
 */
inline fun <K : Enum<K>, V: Any> Array<K>.toEnumMap (
	generator: (K) -> V
) : EnumMap<K, V>
{
	val map = EnumMap<K, V>(this)
	this.forEach { key -> map[key] = generator(key) }
	return map
}

/**
 * Transform the receiver via the supplied function and collect the results into
 * an optionally provided set. Answer the result set.
 *
 * @param T
 *   The element type of the incoming [Iterable].
 * @param R
 *   The element type of the outgoing [Set].
 * @param destination
 *   The destination [MutableSet]. Defaults to [mutableSetOf].
 * @param transform
 *   The function to map keys to values.
 * @return
 *   The resultant [MutableSet].
 */
inline fun <T, R> Iterable<T>.mapToSet (
	destination: MutableSet<R> = mutableSetOf(),
	transform: (T) -> R
) : MutableSet<R> = mapTo(destination, transform)

/**
 * Given an [Iterable] receiver, run the normal `forEach` operation to produce
 * a series of values, apply the extractor extension function to each of those
 * values to produce an `Iterable` for each of them, and run `forEach` on those
 * `Iterables`, in order, using the [body] function.
 *
 * @receiver
 *   The outer [Iterable] to visit.
 * @param extractor
 *   A transformation from values produced by the receiver to an Iterable that
 *   should be visited with the [body].
 * @param body
 *   The function to run with each value produced by each of the invocations of
 *   the [extractor] on each element of the receiver [Iterable].
 */
inline fun <A: Iterable<B>, B, C > A.deepForEach (
	extractor: B.()->Iterable<C>,
	body: (C)->Unit
) = this.forEach { b -> b.extractor().forEach(body) }

/**
 * Given an [Iterable] receiver, run the normal `forEach` operation to produce
 * a series of values, apply the [extractor1] extension function to each of
 * those values to produce an `Iterable` for each of them, run the [extractor2]
 * extension function to produce an `Iterable` to run `forEach` on, with the
 * [body] function.
 *
 * and run `forEach` on those
 * `Iterables`, in order.
 *
 * @receiver
 *   The outer [Iterable] to visit.
 * @param extractor1
 *   A transformation from values produced by the receiver to an Iterable that
 *   should be visited.
 * @param extractor2
 *   A transformation from values produced by the extractor1's iterator, to an
 *   Iterable that should be visited with the [body].
 * @param body
 *   The function to run with each value produced by each of the invocations of
 *   the [extractor2] on each element produced by the [extractor1] on each
 *   element of the receiver [Iterable].
 */
inline fun <A: Iterable<B>, B, C, D> A.deepForEach (
	extractor1: B.()->Iterable<C>,
	extractor2: C.()->Iterable<D>,
	body: (D)->Unit
) = this.forEach { b ->
	b.extractor1().forEach { c ->
		c.extractor2().forEach(body)
	}
}

/**
 * Kotlin has one of these in experimental, which forces the Universe to say
 * it's also experimental.  So boo.
 */
fun<E> MutableList<E>.removeLast(): E = this.removeAt(size - 1)

/**
 * For each element in the collection, execute the [action], passing a
 * zero-argument function to run exactly once afterward (in this [Thread] or
 * another).  When the last element's zero-argument function has been invoked,
 * or immediately if the collection is empty, invoke [then].
 *
 * @param action
 *   What to do with each element.  It should invoke the second argument when
 *   processing of that element is considered complete.
 * @param then
 *   What to do after each element has reported completion.
 */
fun<E> Collection<E>.parallelDoThen(
	action: (E, ()->Unit)->Unit,
	then: ()->Unit)
{
	if (isEmpty())
	{
		then()
		return
	}
	val countdown = AtomicInteger(size)
	val decrement = {
		if (countdown.decrementAndGet() == 0) then()
	}
	forEach { e -> action(e, decrement) }
}

/**
 * For each element in the collection, execute the [action], passing a
 * zero-argument function to run exactly once afterward (in this [Thread] or
 * another).  When the last element's zero-argument function has been invoked,
 * or immediately if the collection is empty, invoke [then].
 *
 * @param action
 *   What to do with each element.  It should invoke the second argument when
 *   processing of that element is considered complete.
 * @param then
 *   What to do after each element has reported completion.
 * @param S
 *   The source element type.
 * @param T
 *   The target element type, produced by the action.
 */
inline fun<S, reified T> Collection<S>.parallelMapThen(
	crossinline action: (S, (T)->Unit)->Unit,
	crossinline then: (List<T>)->Unit)
{
	if (isEmpty())
	{
		then(emptyList())
		return
	}
	val resultsArray = arrayOfNulls<T>(size)
	val countdown = AtomicInteger(size)
	val decrement = { i: Int, target: T ->
		resultsArray[i] = target
		if (countdown.decrementAndGet() == 0)
		{
			then(resultsArray.toList().cast())
		}
	}
	forEachIndexed { i, source ->
		action(source) { target -> decrement(i, target) }
	}
}

/**
 * Partition the receiver into [partitions] approximately equal sublists.  Some
 * may be empty if count is larger than the receiver's size.  Invoke the
 * supplied [body] for each sublist.  The body must eventually, perhaps in
 * another [Thread], invoke a function passed to it, to indicate completion, and
 * to provide a list containing the element-wise transformation of the original
 * sublist.  These transformed sublists are then concatenated to form a new
 * list, which is passed to the [after] function, perhaps in another [Thread].
 *
 * The original calling thread returns after each body returns, *not* after the
 * bodies call their completion function, so if they offload the responsibility
 * to run the completion function to another thread, that may be where the
 * [after] function is executed as well.  If no offloading happens, the original
 * thread will run the [after] function.
 */
fun<E, R> List<E>.partitionedMap(
	partitions: Int,
	body: (List<E>, (List<R>)->Unit)->Unit,
	after: (List<R>)->Unit)
{
	val size = size
	if (size == 0)
	{
		after(emptyList())
		return
	}
	val sublists = (0L until partitions).map { i ->
		subList(
			(i * size / partitions).toInt(),
			((i + 1) * size / partitions).toInt())
	}
	sublists.parallelMapThen(
		action = body,
		then = { outputLists -> after(outputLists.flatten()) })
}

/**
 * Given a list and a key-extraction function, produce a list of non-empty lists
 * of elements that have the same key.  Note that it only looks for runs of a
 * key, and treats reoccurrences of some key after a gap to be a new key.
 *
 * @receiver
 *   The list of elements.
 * @param groupingKey
 *   The key extraction lambda.
 * @return
 *   A list (empty iff the receiver is empty) of non-empty lists, each producing
 *   the same key.  Note that flattening this list produces the original list.
 */
fun<E, K> List<E>.partitionRunsBy(
	groupingKey: (E) -> K
) : List<List<E>>
{
	if (isEmpty()) return emptyList()
	val result = mutableListOf<List<E>>()
	var currentGroup: MutableList<E> = mutableListOf()
	var currentKey: K? = null
	forEach { e ->
		val newKey = groupingKey(e)
		if (currentGroup.isNotEmpty() && newKey != currentKey)
		{
			result.add(currentGroup)
			currentGroup = mutableListOf()
		}
		currentGroup.add(e)
		currentKey = newKey
	}
	result.add(currentGroup)
	return result
}

/**
 * Drain the elements of the receiver into a new [MutableSet], leaving the
 * receiver empty.
 *
 * @return
 *   A copy of the receiver.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
fun <T> MutableSet<T>.drain(): MutableSet<T>
{
	val elements = toMutableSet()
	clear()
	return elements
}

/** Tuple of length 1. */
data class Tuple1<T1> constructor (val t1: T1)

/** Tuple of length 2. */
typealias Tuple2<T1, T2> = Pair<T1, T2>

/** Tuple of length 3. */
typealias Tuple3<T1, T2, T3> = Triple<T1, T2, T3>

/** Tuple of length 4. */
data class Tuple4<T1, T2, T3, T4> constructor (
	val t1: T1,
	val t2: T2,
	val t3: T3,
	val t4: T4)

/** Tuple of length 5. */
data class Tuple5<T1, T2, T3, T4, T5> constructor (
	val t1: T1,
	val t2: T2,
	val t3: T3,
	val t4: T4,
	val t5: T5)

/** Tuple of length 6. */
data class Tuple6<T1, T2, T3, T4, T5, T6> constructor (
	val t1: T1,
	val t2: T2,
	val t3: T3,
	val t4: T4,
	val t5: T5,
	val t6: T6)

/** Tuple of length 7. */
data class Tuple7<T1, T2, T3, T4, T5, T6, T7> constructor (
	val t1: T1,
	val t2: T2,
	val t3: T3,
	val t4: T4,
	val t5: T5,
	val t6: T6,
	val t7: T7)

/** Tuple of length 8. */
data class Tuple8<T1, T2, T3, T4, T5, T6, T7, T8> constructor (
	val t1: T1,
	val t2: T2,
	val t3: T3,
	val t4: T4,
	val t5: T5,
	val t6: T6,
	val t7: T7,
	val t8: T8)

/** Tuple of length 9. */
data class Tuple9<T1, T2, T3, T4, T5, T6, T7, T8, T9> constructor (
	val t1: T1,
	val t2: T2,
	val t3: T3,
	val t4: T4,
	val t5: T5,
	val t6: T6,
	val t7: T7,
	val t8: T8,
	val t9: T9)

/** Tuple of length 10. */
data class Tuple10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> constructor (
	val t1: T1,
	val t2: T2,
	val t3: T3,
	val t4: T4,
	val t5: T5,
	val t6: T6,
	val t7: T7,
	val t8: T8,
	val t9: T9,
	val t10: T10)

/** Construct a tuple of length 1. */
fun <T1> t(t1: T1) = Tuple1(t1)

/** Construct a tuple of length 2. */
fun <T1, T2> t(t1: T1, t2: T2) = Tuple2(t1, t2)

/** Construct a tuple of length 3. */
fun <T1, T2, T3> t(t1: T1, t2: T2, t3: T3) = Tuple3(t1, t2, t3)

/** Construct a tuple of length 4. */
fun <T1, T2, T3, T4> t(t1: T1, t2: T2, t3: T3, t4: T4) = Tuple4(t1, t2, t3, t4)

/** Construct a tuple of length 5. */
fun <T1, T2, T3, T4, T5> t(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5) =
	Tuple5(t1, t2, t3, t4, t5)

/** Construct a tuple of length 6. */
fun <T1, T2, T3, T4, T5, T6> t(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6) =
	Tuple6(t1, t2, t3, t4, t5, t6)

/** Construct a tuple of length 7. */
fun <T1, T2, T3, T4, T5, T6, T7> t(
	t1: T1,
	t2: T2,
	t3: T3,
	t4: T4,
	t5: T5,
	t6: T6,
	t7: T7
) = Tuple7(t1, t2, t3, t4, t5, t6, t7)

/** Construct a tuple of length 8. */
fun <T1, T2, T3, T4, T5, T6, T7, T8> t(
	t1: T1,
	t2: T2,
	t3: T3,
	t4: T4,
	t5: T5,
	t6: T6,
	t7: T7,
	t8: T8
) = Tuple8(t1, t2, t3, t4, t5, t6, t7, t8)

/** Construct a tuple of length 9. */
fun <T1, T2, T3, T4, T5, T6, T7, T8, T9> t(
	t1: T1,
	t2: T2,
	t3: T3,
	t4: T4,
	t5: T5,
	t6: T6,
	t7: T7,
	t8: T8,
	t9: T9
) = Tuple9(t1, t2, t3, t4, t5, t6, t7, t8, t9)

/** Construct a tuple of length 10. */
fun <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> t(
	t1: T1,
	t2: T2,
	t3: T3,
	t4: T4,
	t5: T5,
	t6: T6,
	t7: T7,
	t8: T8,
	t9: T9,
	t10: T10
) = Tuple10(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10)
