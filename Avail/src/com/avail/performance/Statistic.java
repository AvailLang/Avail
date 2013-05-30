/**
 * Statistic.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

/**
 * A Statistic is an incremental, summarized recording of a set of integral
 * values and times.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class Statistic implements Comparable<Statistic>
{
	/**
	 * A descriptive name for this statistic.
	 */
	private final String name;

	/**
	 * The number of samples that have been recorded.
	 */
	private long count = 0;

	/**
	 * The minimum sample that has been recorded.
	 */
	private double min = Double.POSITIVE_INFINITY;

	/**
	 * The maximum sample that has been recorded.
	 */
	private double max = Double.NEGATIVE_INFINITY;

	/**
	 * The arithmetic mean of the samples that have been recorded.
	 */
	private double mean = 0.0;

	/**
	 * The sum of the squares of differences from the current mean.  This is
	 * more numerically stable in calculating the variance than the sum of
	 * squares of the samples.  See <cite> Donald E. Knuth (1998). The Art of
	 * Computer Programming, volume 2: Seminumerical Algorithms, 3rd edn.,
	 * p. 232. Boston: Addison-Wesley</cite>.  That cites a 1962 paper by <cite>
	 * B. P. Welford</cite>.
	 */
	private double sumOfDeltaSquares = 0.0;

	/**
	 * Return the name of this statistic.
	 *
	 * @return A string describing this statistic's purpose.
	 */
	public String name ()
	{
		return name;
	}

	/**
	 * Return the number of samples that have been recorded.
	 *
	 * @return The sample count.
	 */
	public long count ()
	{
		return count;
	}

	/**
	 * Return the mean sample.  This is the sum of the samples divided by the
	 * number of samples.
	 *
	 * @return The mean sample.
	 */
	public double mean ()
	{
		return mean;
	}

	/**
	 * Return the sum of the samples.
	 *
	 * @return The sum of the samples.
	 */
	public double sum ()
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
	public double variance ()
	{
		if (count <= 1L)
		{
			return 0.0;
		}
		return sumOfDeltaSquares / (count - 1L);
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
	 * Construct a new {@link Statistic}.
	 *
	 * @param name The name to attach to this statistic.
	 */
	public Statistic (final String name)
	{
		this.name = name;
	}

	/**
	 * Record a new sample, updating any cumulative statistical values.  This is
	 * synchronized to prevent unsafe concurrent updates.  Note that reading the
	 * statistical values is only thread safe if performed while holding the
	 * monitor for this object, or by creating an internally consistent {@link
	 * #copy()}, which is recommended if multiple coherent values are needed.
	 *
	 * @param sample The sample value to record.
	 */
	public synchronized void record (final double sample)
	{
		count++;
		min = Math.min(min, sample);
		max = Math.max(max, sample);
		final double delta = sample - mean;
		mean += delta / count;
		sumOfDeltaSquares += delta * (sample - mean);
	}

	/**
	 * Make a copy of the Statistic object suitable for either adding subsequent
	 * samples (fully decoupled from the receiver) or for reading mutually
	 * consistent statistical measures safely in the presence of concurrent
	 * updates to the receiver.
	 *
	 * @return An exact copy of the receiver.
	 */
	public synchronized Statistic copy ()
	{
		final Statistic copy = new Statistic(name);
		copy.count = count;
		copy.min = min;
		copy.max = max;
		copy.mean = mean;
		copy.sumOfDeltaSquares = sumOfDeltaSquares;
		return copy;
	}

	@Override
	public int compareTo (final Statistic otherStat)
	{
		// Compare by descending sums.
		return Double.compare(otherStat.sum(), this.sum());
	}

	/**
	 * Describe this statistic as though its samples are durations in
	 * nanoseconds.
	 *
	 * @param builder Where to describe this statistic.
	 */
	public void describeNanosecondsOn (final StringBuilder builder)
	{
		final double nanoseconds = sum();
		builder.append(String.format(
			nanoseconds > 1e9
				? "%1$, 8.3f s  "
				: nanoseconds > 1e6
					? "%2$, 8.3f ms "
					: nanoseconds > 1e3
						? "%3$, 8.3f µs "
						: "%4$, 3d     ns ",
			nanoseconds / 1.0e9,
			nanoseconds / 1.0e6,
			nanoseconds / 1.0e3,
			(long)nanoseconds));
		builder.append(String.format(
			"[N=%,10d] ",
			count()));
		builder.append(name());
	}

	/**
	 * Reset this statistic as though no samples had ever been recorded.
	 */
	public void clear ()
	{
		count = 0;
		min = Double.POSITIVE_INFINITY;
		max = Double.NEGATIVE_INFINITY;
		mean = 0.0;
		sumOfDeltaSquares = 0.0;
	}
}
