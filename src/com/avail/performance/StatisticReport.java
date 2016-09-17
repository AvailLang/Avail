/**
 * StatisticReport.java
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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import com.avail.descriptor.A_RawFunction;
import com.avail.utility.Pair;

/**
 * The statistic reports requested of the compiler:
 * <ul>
 * <li>L2Operations ~ The most time-intensive level-two operations</li>
 * <li>DynamicLookups ~ The most time-intensive dynamic method lookups.</li>
 * <li>Primitives ~ The primitives that are the most time-intensive to run
 *     overall.</li>
 * <li>PrimitiveReturnTypeChecks ~ The primitives that take the most time
 *     checking return types.</li>
 * <li>NonprimitiveReturnTypeChecks ~ Returns from non-primitives that had to
 *     check the return type.</li>
 * </ul>
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public enum StatisticReport
{
	/** Level-Two Operations report. */
	L2_OPERATIONS
	{
		@Override
		public String keyword ()
		{
			return "L2Operations";
		}
	},

	/** The Dynamic Lookups report. */
	DYNAMIC_LOOKUPS
	{
		@Override
		public String keyword ()
		{
			return "DynamicLookups";
		}
	},

	/** The Primitives report. */
	PRIMITIVES
	{
		@Override
		public String keyword ()
		{
			return "Primitives";
		}
	},

	/** The Primitive Return Type Checks report. */
	PRIMITIVE_RETURN_TYPE_CHECKS
	{
		@Override
		public String keyword ()
		{
			return "PrimitiveReturnTypeChecks";
		}
	},

	/** Report for outermost statements of modules that are loaded. */
	TOP_LEVEL_STATEMENTS
	{
		@Override
		public String keyword ()
		{
			return "TopLevelStatements";
		}
	},

	/**
	 * The Non-primitive Return Type Checks report.  This collects timings for
	 * non-primitive returns that had to check the type of the return result,
	 * summarized as L1 (unoptimized) versus L2 (optimized) code doing the
	 * returning.
	 */
	NON_PRIMITIVE_RETURN_LEVELS
	{
		@Override
		public String keyword ()
		{
			return "ReturnTypeChecksSummary";
		}
	},

	/**
	 * The Non-primitive Return Type Checks report.  This collects contextual
	 * timings for non-primitive returns that had to check the type of the
	 * return result.  They're organized by the {@link A_RawFunction} that the
	 * return is from, and the {@link A_RawFunction} that it's to.
	 */
	NON_PRIMITIVE_RETURN_TYPE_CHECKS
	{
		@Override
		public String keyword ()
		{
			return "NonprimitiveReturnTypeChecksDetail";
		}
	};

	/**
	 * The {@link List} of {@link Statistic} objects that have been registered
	 * for this particular {@link StatisticReport}.
	 */
	final List<Statistic> statistics = new ArrayList<>();

	/**
	 * Register a {@link Statistic} with this {@link StatisticReport}.  This
	 * happens when the statistic is first created, as part of its constructor.
	 * Access to the {@link List} of {@link #statistics} is synchronized on the
	 * list, to ensure atomic access among registrations and between
	 * registrations and enumeration of the list.
	 *
	 * @param statistic The {@link Statistic} to be registered.
	 */
	public void registerStatistic (final Statistic statistic)
	{
		synchronized (statistics)
		{
			statistics.add(statistic);
		}
	}

	/**
	 * Answer the StatisticReport associated with the given keyword.
	 *
	 * @param str The keyword.
	 * @return The corresponding StatisticReport.
	 */
	public static @Nullable StatisticReport reportFor (final String str)
	{
		for (final StatisticReport report : values())
		{
			if (report.keyword().equals(str))
			{
				return report;
			}
		}
		return null;
	}

	/**
	 * @return The String keyword associated with the StatisticReport.
	 */
	public abstract String keyword ();

	/**
	 * Clear all my {@link Statistic}s.
	 */
	public void clear ()
	{
		synchronized (statistics)
		{
			for (final Statistic stat : statistics)
			{
				stat.clear();
			}
		}
	}

	/**
	 * Collect the aggregates of my statistics, filter out the ones with zero
	 * counts, then sort descending by their sums.  Maintain names with the
	 * aggregated statistics as {@link Pair}s.
	 *
	 * @return A sorted {@link List} of {@link Pair}&lt;{@link String},
	 *         {@link PerInterpreterStatistic}&gt;.
	 */
	public List<Pair<String, PerInterpreterStatistic>> sortedPairs ()
	{
		final List<Pair<String, PerInterpreterStatistic>> namedSnapshots =
			new ArrayList<>(statistics.size());
		for (final Statistic stat : statistics)
		{
			final PerInterpreterStatistic aggregate = stat.aggregate();
			if (aggregate.count() > 0)
			{
				namedSnapshots.add(
					new Pair<String, PerInterpreterStatistic>(
						stat.name(), aggregate));
			}
		}
		Collections.sort(
			namedSnapshots,
			new Comparator<Pair<String, PerInterpreterStatistic>>()
			{
				@Override
				public int compare (
					final @Nullable Pair<String, PerInterpreterStatistic> pair1,
					final @Nullable Pair<String, PerInterpreterStatistic> pair2)
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

	/**
	 * Output the appropriate {@linkplain StatisticReport reports}.
	 *
	 * @param reports
	 *        The compiler configuration where the report settings are stored.
	 * @return The specified reports as a single {@link String}.
	 */
	public static String produceReports (
		final EnumSet<StatisticReport> reports)
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("\n");
		for (final StatisticReport report : reports)
		{
			builder.append('\n');
			builder.append(report.keyword());
			builder.append('\n');
			final List<Pair<String, PerInterpreterStatistic>> pairs =
				report.sortedPairs();
			for (final Pair<String, PerInterpreterStatistic> pair : pairs)
			{
				pair.second().describeNanosecondsOn(builder);
				builder.append(" ");
				builder.append(pair.first());
				builder.append('\n');
			}
		}
		return builder.toString();
	}
}
