/*
 * RunTree.kt
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

package avail.utility.structures

import avail.utility.isNullOr
import avail.utility.notNullAnd
import java.util.TreeMap
import java.util.concurrent.locks.ReadWriteLock
import kotlin.math.min

/**
 * A [RunTree] maintains a mapping from [Long] to some value which is expected
 * to be the same for runs of consecutive [Long]s.  This is not thread-safe, and
 * should be protected with an external [ReadWriteLock] to allow readers and
 * writers to coordinate safely.
 *
 * The `null` value is treated as the absence of a run.  The [edit] method takes
 * two [Long]s representing the affected range (degenerate ranges have no
 * effect) and a function taking a [Value] or null, and returning a [Value] or
 * null to use in its place.  The function is evaluated zero or more times to
 * produce edits of existing runs, which will then be subject to the [RunTree]`s
 * normalization rules:
 *  * No empty ranges,
 *  * No `null` values in ranges,
 *  * No overlapping ranges,
 *  * Two contiguous ranges will not have the same [Value],
 *  by [equality][equals].
 *  * Ranges are not allowed to include [Long.MAX_VALUE].
 *
 * In addition, the [RunTree] is [Iterable], allowing traversal over the
 * <start, pastEnd, value> triples in ascending order.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class RunTree<Value>: Iterable<Triple<Long, Long, Value>>
{
	/**
	 * The basic representation is a [TreeMap] from each run's start to a [Pair]
	 * containing the position just past the run's end and the value associated
	 * with that run.  This is maintained in such a way that no runs overlap, no
	 * runs are empty, and there are no contiguous runs with equal values (they
	 * are automatically merged).
	 */
	val tree = TreeMap<Long, Pair<Long, Value>>()

	/**
	 * Run the edit action for each [Value] or `null` within the given range,
	 * to produce an alternative [Value].
	 *
	 * [Long.MAX_VALUE] must not occur within the span, which is ensured by
	 * the constraint [start] <= [pastEnd].  The stronger condition,
	 * [start] < [pastEnd] also prevents empty ranges.
	 *
	 * @param start
	 *   The start of the range to insert.
	 * @param pastEnd
	 *   The index just past the end of the range to insert.
	 * @param edit
	 *   The function to evaluate to map from the old value of some range to a
	 *   new value for that range.  The input and/or output may be null,
	 *   indicating an absent range.
	 */
	fun edit(start: Long, pastEnd: Long, edit: (Value?)->Value?)
	{
		assert(start < pastEnd)
		var runningStart = start
		while (runningStart < pastEnd)
		{
			val floorEntry = tree.floorEntry(runningStart)
			if (floorEntry.isNullOr { value.first <= runningStart })
			{
				// There is a null range containing runningStart.
				// Determine where the null range ends.
				val runEnd = when (
					val ceilingEntry = tree.ceilingEntry(runningStart + 1))
				{
					// It's the rest of the range.
					null -> pastEnd
					// Truncate it to either the start of the next range or the
					// pastEnd limit, whichever is less.
					else -> min(ceilingEntry.key, pastEnd)
				}
				edit(null)?.let { newValue ->
					insert(runningStart, runEnd, newValue)
				}
				runningStart = runEnd
				continue
			}
			// We're at a non-null range.
			val oldValue = floorEntry.value.second
			val newValue = edit(oldValue)
			val entryPastEnd = floorEntry.value.first
			// Remove the entry, add the left portion of it back in if
			// non-empty, add the newValue if it's non-null, then add back the
			// right portion of it if non-empty.
			tree.remove(floorEntry.key)
			if (floorEntry.key < runningStart)
				insert(floorEntry.key, runningStart, oldValue)
			newValue?.let {
				insert(runningStart, min(pastEnd, entryPastEnd), it)
			}
			if (pastEnd < entryPastEnd)
				insert(pastEnd, entryPastEnd, oldValue)
			runningStart = min(pastEnd, entryPastEnd)
		}
	}

	/**
	 * Run the edit action for each [Value] or `null` within the given range,
	 * to produce an alternative [Value].  This form takes [Int] arguments as a
	 * convenience.
	 *
	 * @param start
	 *   The start of the range to insert.
	 * @param pastEnd
	 *   The index just past the end of the range to insert.
	 * @param edit
	 *   The function to evaluate to map from the old value of some range to a
	 *   new value for that range.  The input and/or output may be null,
	 *   indicating an absent range.
	 */
	fun edit(start: Int, pastEnd: Int, edit: (Value?)->Value?) =
		edit(start.toLong(), pastEnd.toLong(), edit)

	/**
	 * Insert a range that is currently not in the tree, and is assumed not to
	 * overlap any range in the tree.  This operation deals with left and/or
	 * right neighbors that are contiguous with the new range and have a value
	 * equal to the new range's value.
	 *
	 * @param start
	 *   The start of the range to insert.
	 * @param pastEnd
	 *   The index just past the end of the range to insert.
	 * @param newValue
	 *   The value to associate with the new range.
	 */
	private fun insert(start: Long, pastEnd: Long, newValue: Value)
	{
		assert(tree[start] == null)
		val previousEntry = tree.floorEntry(start - 1)
		val joinLeft = previousEntry.notNullAnd {
			value.first == start && value.second == newValue
		}
		val nextEntryValue = tree[pastEnd]
		val joinRight = nextEntryValue.notNullAnd { second == newValue }
		if (joinLeft) tree.remove(previousEntry.key)
		if (joinRight) tree.remove(pastEnd)
		tree[if (joinLeft) previousEntry.key else start] =
			(if (joinRight) nextEntryValue!!.first else pastEnd) to newValue
	}

	/**
	 * Produce an iterator over the (non-null) ranges, in ascending order.  The
	 * ranges are [Triple]s of the form <start, pastEnd, value>.
	 */
	override fun iterator(): Iterator<Triple<Long, Long, Value>> =
		tree.asSequence()
			.map { Triple(it.key, it.value.first, it.value.second) }
			.iterator()

	/**
	 * Transform each (non-null) range of the receiver via a [transform]
	 * function, yielding another [RunTree] with the same ranges and
	 * corresponding transformed values.  If the [transform] produces a null,
	 * that range is dropped in the output.  If two or more contiguous ranges
	 * transform into non-null [ResultValue]s that are [equal][equals], they
	 * will be collapsed into a single range in the output.
	 */
	fun<ResultValue> mapRuns(
		transform: (Value)->ResultValue?
	): RunTree<ResultValue> =
		RunTree<ResultValue>().also { output ->
			forEach { (start, pastEnd, value) ->
				output.edit(start, pastEnd) { _ -> transform(value) }
			}
		}

	/**
	 * Given the receiver, which is a [RunTree]&lt;[Value]>, and the
	 * [otherTree], which is a [RunTree]&lt;[OtherValue]>, produce an aggregate
	 * [RunTree]&lt;[Pair]&lt;[Value]?, [OtherValue]?>.  The ranges will be
	 * split as needed to ensure that the spans in the output have:
	 *   1. the same [Pair.first] as the value in the receiver (or null if
	 *     there was no such range in the receiver), and
	 *   2. the same [Pair.second] as the value in the [otherTree] (or null if
	 *     there was no such range in the [otherTree].
	 *
	 * The [Pair] (`null`, `null`) will not occur in the output.
	 */
	infix fun<OtherValue> zipRuns(
		otherTree: RunTree<OtherValue>
	): RunTree<Pair<Value?, OtherValue?>> =
		RunTree<Pair<Value?, OtherValue?>>().also { output ->
			forEach { (start, pastEnd, value) ->
				output.edit(start, pastEnd) { _ -> value to null }
			}
			otherTree.forEach { (start, pastEnd, otherValue) ->
				output.edit(start, pastEnd) { oldPair ->
					oldPair?.first to otherValue
				}
			}
		}


	/**
	 * Given the receiver, which is a [RunTree]&lt;[Value]>, and the
	 * [otherTree], which is a [RunTree]&lt;[OtherValue]>, produce an aggregate
	 * [RunTree]&lt;[OutputValue]>.  The [OutputValue] for each range will be
	 * computed from a [transform] function taking a nullable [Value] and a
	 * nullable [OtherValue] (but both won't be null), and producing a nullable
	 * [OutputValue]. The resulting [RunTree] respects the normalization rules
	 * of the class.
	 *
	 * @param otherTree
	 *   The second source of runs (the first being the receiver).
	 * @param transform
	 *   A function taking a nullable run value from the receiver and a nullable
	 *   run value from the [otherTree] and producing a nullable [OutputValue].
	 * @return
	 *   The [RunTree] of [OutputValue]s produced by the [transform] function.
	 */
	fun<OtherValue, OutputValue> zipMapRuns(
		otherTree: RunTree<OtherValue>,
		transform: (Value?, OtherValue?)->OutputValue?
	) = (this zipRuns otherTree).mapRuns { (a, b) -> transform(a, b) }
}
