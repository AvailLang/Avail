/**
 * Statistic.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.avail.annotations.Nullable;
import com.avail.utility.Pair;

/**
 * A Statistic is an incremental, summarized recording of a set of integral
 * values and times.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class Statistic
{
	/** An immutable collection of related statistics. */
	public static class StatisticSnapshot
	implements Comparable<StatisticSnapshot>
	{
		/** The number of samples recorded so far. */
		public final long count;

		/** The smallest sample yet encountered. */
		public final double min;

		/** The largest sample yet encountered. */
		public final double max;

		/** The average of all samples recorded so far. */
		public final double mean;

		/**
		 * The sum of the squares of differences from the current mean.  This is
		 * more numerically stable in calculating the variance than the sum of
		 * squares of the samples.  See <cite> Donald E. Knuth (1998). The Art
		 * of Computer Programming, volume 2: Seminumerical Algorithms, 3rd
		 * edn., p. 232. Boston: Addison-Wesley</cite>.  That cites a 1962 paper
		 * by <cite>B. P. Welford</cite>.
		 */
		public final double sumOfDeltaSquares;

		/**
		 * Construct a new {@link Statistic.StatisticSnapshot} with the given
		 * values.
		 *
		 * @param count
		 * @param min
		 * @param max
		 * @param mean
		 * @param sumOfDeltaSquares
		 */
		StatisticSnapshot (
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

		/** The default starting snapshot representing the absence of data. */
		static StatisticSnapshot initialState = new StatisticSnapshot(
			0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, 0.0);

		/** Default sort is descending by sum. */
		@Override
		public int compareTo (final @Nullable StatisticSnapshot otherSnapshot)
		{
			// Compare by descending sums.
			assert otherSnapshot != null;
			return Double.compare(otherSnapshot.sum(), this.sum());
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
		 * Describe this statistic as though its samples are durations in
		 * nanoseconds.
		 *
		 * @param builder Where to describe this statistic.
		 */
		public void describeNanosecondsOn (final StringBuilder builder)
		{
			final double nanoseconds = sum();
			builder.append(String.format(
				nanoseconds >= 999_999_500.0
					? "%1$, 8.3f s  "
					: nanoseconds >= 999_999.5
						? "%2$, 8.3f ms "
						: "%3$, 8.3f µs ",
				nanoseconds / 1.0e9,
				nanoseconds / 1.0e6,
				nanoseconds / 1.0e3));
			builder.append(String.format("[N=%,10d] ", count));
		}
	}

	/**
	 * A descriptive name for this statistic.
	 */
	private final String name;

	/**
	 * The {@link AtomicReference} holding the current {@link StatisticSnapshot
	 * snapshot} of this statistics collector.  It's collected together into an
	 * atomically replaced immutable aggregate object to reduce contention when
	 * updating.
	 */
	private final AtomicReference<StatisticSnapshot> currentState =
		new AtomicReference<>(StatisticSnapshot.initialState);

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
	 * Return an immutable snapshot of this statistic.
	 * @return The current snapshot.
	 */
	public StatisticSnapshot snapshot ()
	{
		return currentState.get();
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
	 * carefully crafted to serialize concurrent updates.
	 *
	 * @param sample The sample value to record.
	 */
	public void record (final double sample)
	{
		while (true)
		{
			final StatisticSnapshot oldState = currentState.get();
			final long newCount = oldState.count + 1;
			final double delta = sample - oldState.mean;
			final double newMean = oldState.mean + delta / newCount;
			final StatisticSnapshot newState = new StatisticSnapshot(
				newCount,
				Math.min(sample, oldState.min),
				Math.max(sample, oldState.max),
				newMean,
				oldState.sumOfDeltaSquares + delta * (sample - newMean));
			if (currentState.compareAndSet(oldState, newState))
			{
				break;
			}
		}
	}

	/**
	 * Reset this statistic as though no samples had ever been recorded.
	 */
	public synchronized void clear ()
	{
		currentState.set(StatisticSnapshot.initialState);
	}


	/**
	 * Sort a collection of statistics, extracting their snapshots and names as
	 * pairs.
	 *
	 * @param statistics The collection of statistics to sort.
	 * @return A sorted collection of (String, StatisticsSnapshot) pairs.
	 */
	public static List<Pair<String, StatisticSnapshot>> sortedSnapshotPairs (
		final Collection<Statistic> statistics)
	{
		final List<Pair<String, StatisticSnapshot>> namedSnapshots =
			new ArrayList<>(statistics.size());
		for (final Statistic stat : statistics)
		{
			namedSnapshots.add(
				new Pair<String, Statistic.StatisticSnapshot>(
					stat.name(), stat.snapshot()));
		}
		Collections.sort(
			namedSnapshots,
			new Comparator<Pair<String, StatisticSnapshot>>()
			{
				@Override
				public int compare (
					final @Nullable Pair<String, StatisticSnapshot> pair1,
					final @Nullable Pair<String, StatisticSnapshot> pair2)
				{
					assert pair1 != null && pair2 != null;
					final int byStat = pair1.second().compareTo(pair2.second());
					if (byStat != 0)
					{
						return byStat;
					}
					return Collator.getInstance().compare(
						pair1.first(), pair2.first());
				}
			});
		return namedSnapshots;
	}

}
