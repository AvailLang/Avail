/*
 * LookupStatistics.kt
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

package avail.dispatch

import avail.performance.Statistic
import avail.performance.StatisticReport
import java.util.concurrent.atomic.AtomicReference

/**
 * A helper class holding a pair of [Statistic]s, one by the amount of time for
 * each dynamic lookup, and one by the depth traveled within the lookup tree.
 *
 */
class LookupStatistics constructor (
	private val baseName: String,
	private val report: StatisticReport)
{
	/** Create one of my [Statistic]s. */
	private fun createStatistic(index: Int) =
		Statistic(report, "$baseName (depth = $index)")

	/** An array of statistics, indexed by depth, but recording time. */
	private val timeStats = AtomicReference(Array(3, this::createStatistic))

	/**
	 * Record the fact that a lookup has just taken place, that it took the
	 * given time in nanoseconds, and that it reached the given depth.
	 *
	 * @param nanos
	 *   A `double` indicating how many nanoseconds it took.
	 * @param depth
	 *   An [Int] indicating how deep the search had to go in the [LookupTree].
	 */
	fun recordDynamicLookup(
		nanos: Double,
		depth: Int)
	{
		// Don't bother to even record lookups with no arguments.
		if (depth == 0) return
		var stats = timeStats.get()
		while (depth >= stats.size)
		{
			// Grow the stats array atomically, leaving a pad for efficiency.
			val biggerStats = Array(depth + 3) { i ->
				if (i < stats.size) stats[i]
				else createStatistic(i)
			}
			stats = when
			{
				timeStats.compareAndSet(stats, biggerStats) -> biggerStats
				else -> timeStats.get()
			}
		}
		stats[depth].record(nanos)
	}
}
