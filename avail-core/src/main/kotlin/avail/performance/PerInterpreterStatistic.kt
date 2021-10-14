/*
 * PerInterpreterStatistic.kt
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

package avail.performance

import avail.AvailRuntimeConfiguration
import avail.interpreter.execution.Interpreter
import java.lang.String.format
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A `PerInterpreterStatistic` is an incremental, summarized recording of
 * a set of integral values and times.  It is synchronized, although the typical
 * usage is that it will only be written by a single [Thread] at a time,
 * and read by another [Thread] only rarely.
 *
 *
 * If you want to record samples from multiple processes, use a Statistic,
 * which holds a PerInterpreterStatistic for up to
 * [AvailRuntimeConfiguration.maxInterpreters] separate Threads to access,
 * without any locks.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property count
 *  The number of samples recorded so far.
 * @property min
 *  The smallest sample yet encountered.
 * @property max
 *   The largest sample yet encountered.
 * @property mean
 *   The average of all samples recorded so far.
 * @property sumOfDeltaSquares
 *   The sum of the squares of differences from the current mean.  This is more
 *   numerically stable in calculating the variance than the sum of squares of
 *   the samples.  See <cite> Donald E. Knuth (1998). The Art of Computer
 *   Programming, volume 2: Seminumerical Algorithms, 3rd edn., p. 232. Boston:
 *   Addison-Wesley</cite>.  That cites a 1962 paper by <cite>B. P.
 *   Welford</cite>.
 * @constructor
 * Construct a new statistic with the given values.
 *
 * @param count
 *   The number of samples.
 * @param min
 *   The minimum sample.
 * @param max
 *   The maximum sample.
 * @param mean
 *   The mean of the samples.
 * @param sumOfDeltaSquares
 *   The sum of squares of differences of the samples from the mean.
 */
class PerInterpreterStatistic internal constructor(
	private val lock: AtomicInteger = AtomicInteger(0),
	private var count: Long = 0L,
	private var min: Double = Double.POSITIVE_INFINITY,
	private var max: Double = Double.NEGATIVE_INFINITY,
	private var mean: Double = 0.0,
	private var sumOfDeltaSquares: Double = 0.0
) : Comparable<PerInterpreterStatistic> {
	/**
	 * Acquire a spin-lock, run the body, and release the spin-lock.  The body
	 * should be very short (or the lock rarely contended) to avoid starvation.
	 *
	 * @param body
	 *   What to do while holding the spin lock.
	 */
	private inline fun <A> spinLockWhile(body: () -> A): A
	{
		while (!lock.compareAndSet(0, 1)) Thread.yield()
		return try
		{
			body()
		}
		finally
		{
			val ok = lock.compareAndSet(1, 0)
			assert(ok) { "Invalid spinlock state" }
		}
	}

	/** Default sort is descending by sum. */
	override operator fun compareTo(other: PerInterpreterStatistic): Int
	{
		// Compare by descending sums.
		return other.sum().compareTo(this.sum())
	}

	/**
	 * Return the number of samples that have been recorded.
	 *
	 * Unsynchronized, so only use if you know no other Thread could have
	 * written to it since the last happens-before/happens-after fence.
	 *
	 * @return
	 *   The sample count.
	 */
	internal fun count() = count

	/**
	 * Return the sum of the samples.  This is thread-safe, but may block if an
	 * update (or other read) is in progress.
	 *
	 * Unsynchronized, so only use if you know no other Thread could have
	 * written to it since the last happens-before/happens-after fence.
	 *
	 * @return
	 *   The sum of the samples.
	 */
	internal fun sum() = mean * count

	/**
	 * Answer the corrected variance of the samples.  This is the sum of squares
	 * of differences from the mean, divided by one less than the number of
	 * samples.  Fudge it for less than two samples, pretending the variance is
	 * zero rather than undefined.
	 *
	 * Unsynchronized, so only use if you know no other Thread could have
	 * written to it since the last happens-before/happens-after fence.
	 *
	 * @return
	 *   The Bessel-corrected variance of the samples.
	 */
	internal fun variance() = computeVariance(count, sumOfDeltaSquares)

	/**
	 * Given a count of samples and the sum of squares of their differences from
	 * the mean, compute the variance.
	 *
	 * Unsynchronized, so only use if you know no other Thread could have
	 * written to it since the last happens-before/happens-after fence.
	 *
	 * @param theCount
	 *   The number of samples.
	 * @param theSumOfDeltaSquares
	 *   The sum of the squares of distances from the mean of the samples.
	 * @return
	 *   The statistical variance.
	 */
	private fun computeVariance(
		theCount: Long,
		theSumOfDeltaSquares: Double
	) = if (theCount <= 1L) 0.0 else theSumOfDeltaSquares / (theCount - 1L)

	/**
	 * Answer the Bessel-corrected ("unbiased") standard deviation of these
	 * samples.  This assumes the samples are not the entire population, and
	 * therefore the distances of the samples from the mean are really the
	 * distances from the sample mean, not the actual population mean.
	 *
	 * Unsynchronized, so only use if you know no other Thread could have
	 * written to it since the last happens-before/happens-after fence.
	 *
	 * @return
	 *   The Bessel-corrected standard deviation of the samples.
	 */
	@Suppress("unused")
	internal fun standardDeviation() = sqrt(variance())

	/**
	 * Describe this statistic as though its samples are durations in
	 * nanoseconds.
	 *
	 * Unsynchronized, so only use if you know no other Thread could have
	 * written to it since the last happens-before/happens-after fence.
	 *
	 * @param builder
	 *   Where to describe this statistic.
	 * @param unit
	 *   The [units][ReportingUnit] to use to report the statistic.
	 */
	fun describeOn(builder: StringBuilder, unit: ReportingUnit)
	{
		val capturedCount = count
		val capturedMean = mean
		val capturedSumOfDeltaSquares = sumOfDeltaSquares

		val standardDeviation =
			sqrt(computeVariance(capturedCount, capturedSumOfDeltaSquares))
		builder.append(
			unit.describe(
				capturedCount, capturedMean, 0.0, false))
		builder.append(format(" [N=%,10d] ", capturedCount))
		// We could use a chi (x) with a line over it, "\u0304x", but this makes
		// the text area REALLY SLOW.  Like, over ten seconds to insert a report
		// from running Avail for five seconds.  So we spell out "mean".
		builder.append("(mean=")
		builder.append(unit.describe(1, capturedMean, standardDeviation, true))
		builder.append(")")
	}

	/**
	 * Record a new sample, updating any cumulative statistical values.  This is
	 * thread-safe.  However, the locking cost is very low due to a spinlock, as
	 * long as there's very low contention.  Since [Statistic] partitions use of
	 * [PerInterpreterStatistic]s by [Interpreter] (and hence by [Thread]), the
	 * contention is non-existent, except when accumulating stats to print.
	 *
	 * @param sample
	 *   The sample value to record.
	 */
	fun record(sample: Double)
	{
		spinLockWhile {
			count++
			min = min(sample, min)
			max = max(sample, max)
			val delta = sample - mean
			mean += delta / count
			sumOfDeltaSquares += delta * (sample - mean)
		}
	}

	/**
	 * Add my information to another `PerInterpreterStatistic`.  This is
	 * thread-safe for the receiver, and assumes the argument does not need to
	 * be treated thread-safely.
	 *
	 * @param target
	 *   The statistic to add the receiver to.
	 */
	internal fun addTo(target: PerInterpreterStatistic)
	{
		spinLockWhile {
			if (count > 0) {
				val newCount = target.count + count
				val delta = mean - target.mean
				// Be careful of the order of overwrites.
				target.mean = (target.count * target.mean + count * mean) / newCount
				target.sumOfDeltaSquares =
					target.sumOfDeltaSquares +
						sumOfDeltaSquares +
						delta * delta / newCount * target.count.toDouble() * count.toDouble()
				// Now overwrite the target.
				target.count = newCount
				target.min = min(target.min, min)
				target.max = max(target.max, max)
			}
		}
	}

	/** Reset this statistic as though no samples had ever been recorded. */
	fun clear()
	{
		spinLockWhile {
			count = 0
			min = java.lang.Double.POSITIVE_INFINITY
			max = java.lang.Double.NEGATIVE_INFINITY
			mean = 0.0
			sumOfDeltaSquares = 0.0
		}
	}
}
