/**
 * Statistic.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.performance;

import com.avail.AvailRuntime;
import org.jetbrains.annotations.Nullable;
import com.avail.interpreter.Interpreter;

/**
 * A {@code PerInterpreterStatistic} is an incremental, summarized recording of
 * a set of integral values and times.  It is synchronized, although the typical
 * usage is that it will only be written by a single {@link Thread} at a time,
 * and read by another {@link Thread} only rarely.
 *
 * <p>If you want to record samples from multiple processes, use a Statistic,
 * which holds a PerInterpreterStatistic for up to {@link
 * AvailRuntime#maxInterpreters} separate Threads to access, without any locks.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class PerInterpreterStatistic
implements Comparable<PerInterpreterStatistic>
{
	/** The number of samples recorded so far. */
	public long count;

	/** The smallest sample yet encountered. */
	public double min;

	/** The largest sample yet encountered. */
	public double max;

	/** The average of all samples recorded so far. */
	public double mean;

	/**
	 * The sum of the squares of differences from the current mean.  This is
	 * more numerically stable in calculating the variance than the sum of
	 * squares of the samples.  See <cite> Donald E. Knuth (1998). The Art
	 * of Computer Programming, volume 2: Seminumerical Algorithms, 3rd
	 * edn., p. 232. Boston: Addison-Wesley</cite>.  That cites a 1962 paper
	 * by <cite>B. P. Welford</cite>.
	 */
	public double sumOfDeltaSquares;

	/**
	 * Construct a new {@link PerInterpreterStatistic} with the given values.
	 *
	 * @param count
	 * @param min
	 * @param max
	 * @param mean
	 * @param sumOfDeltaSquares
	 */
	PerInterpreterStatistic (
		final long count,
		final double min,
		final double max,
		final double mean,
		final double sumOfDeltaSquares)
	{
		this.count = count;
		this.min = min;
		this.max = max;
		this.mean = mean;
		this.sumOfDeltaSquares = sumOfDeltaSquares;
	}

	/**
	 * An empty {@link PerInterpreterStatistic}.
	 */
	PerInterpreterStatistic ()
	{
		this(
			0,
			Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY,
			0.0,
			0.0);
	}

	/** Default sort is descending by sum. */
	@Override
	public int compareTo (final @Nullable PerInterpreterStatistic otherStat)
	{
		// Compare by descending sums.
		assert otherStat != null;
		return Double.compare(otherStat.sum(), this.sum());
	}

	/**
	 * Return the number of samples that have been recorded.
	 *
	 * @return The sample count.
	 */
	public synchronized long count ()
	{
		return count;
	}

	/**
	 * Return the sum of the samples.  This is thread-safe, but may block if
	 * an update (or other read) is in progress.
	 *
	 * @return The sum of the samples.
	 */
	public synchronized double sum ()
	{
		return mean * count;
	}

	/**
	 * Answer the corrected variance of the samples.  This is the sum of squares
	 * of differences from the mean, divided by one less than the number of
	 * samples.  Fudge it for less than two samples, pretending the variance is
	 * zero rather than undefined.
	 *
	 * @return The Bessel-corrected variance of the samples.
	 */
	public synchronized double variance ()
	{
		final long c = count;
		return c <= 1L ? 0.0 : sumOfDeltaSquares / (c - 1L);
	}

	/**
	 * Answer the Bessel-corrected ("unbiased") standard deviation of these
	 * samples.  This assumes the samples are not the entire population, and
	 * therefore the distances of the samples from the mean are really the
	 * distances from the sample mean, not the actual population mean.
	 *
	 * @return The Bessel-corrected standard deviation of the samples.
	 */
	public double standardDeviation ()
	{
		return Math.sqrt(variance());
	}

	/**
	 * Describe this statistic as though its samples are durations in
	 * nanoseconds.
	 *
	 * @param builder Where to describe this statistic.
	 */
	public void describeNanosecondsOn (
		final StringBuilder builder)
	{
		final double nanoseconds;
		final long sampleCount;
		// Read multiple fields coherently.
		synchronized (this)
		{
			nanoseconds = sum();
			sampleCount = count;
		}
		builder.append(String.format(
			nanoseconds >= 999_999_500.0
				? "%1$, 8.3f s  "
				: nanoseconds >= 999_999.5
					? "%2$, 8.3f ms "
					: "%3$, 8.3f µs ",
			nanoseconds / 1.0e9,
			nanoseconds / 1.0e6,
			nanoseconds / 1.0e3));
		builder.append(String.format("[N=%,10d] ", sampleCount));
	}

	/**
	 * Record a new sample, updating any cumulative statistical values.  This is
	 * thread-safe.  However, the locking cost should be exceedingly low if a
	 * {@link Statistic} is used to partition an array of {@link
	 * PerInterpreterStatistic}s by {@link Interpreter}.
	 *
	 * @param sample The sample value to record.
	 */
	public synchronized void record (final double sample)
	{
		count++;
		min = Math.min(sample, min);
		max = Math.max(sample, max);
		final double delta = sample - mean;
		mean = mean + delta / count;
		sumOfDeltaSquares += delta * (sample - mean);
	}

	/**
	 * Add my information to another {@link PerInterpreterStatistic}.  This is
	 * thread-safe for the receiver, and assumes the argument does not need to
	 * be treated thread-safely.
	 *
	 * @param target The statistic to add the receiver to.
	 */
	synchronized void addTo (final PerInterpreterStatistic target)
	{
		final long newCount = target.count + count;
		if (newCount > 0)
		{
			final double delta = mean - target.mean;
			final double newMean =
				(target.count * target.mean + count * mean) / newCount;
			final double newSumOfDeltas =
				target.sumOfDeltaSquares + sumOfDeltaSquares
					+ (delta * delta / newCount) * target.count * count;
			// Now overwrite the target.
			target.count = newCount;
			target.min = Math.min(target.min, min);
			target.max = Math.max(target.max, max);
			target.mean = newMean;
			target.sumOfDeltaSquares = newSumOfDeltas;
		}
	}

	/**
	 * Reset this statistic as though no samples had ever been recorded.
	 */
	public synchronized void clear ()
	{
		count = 0;
		min = Double.POSITIVE_INFINITY;
		max = Double.NEGATIVE_INFINITY;
		mean = 0.0;
		sumOfDeltaSquares = 0.0;
	}
}
