/*
 * StatisticReport.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_Module;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.MessageBundleTreeDescriptor;
import com.avail.optimizer.StackReifier;
import com.avail.utility.Pair;

import javax.annotation.Nullable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.avail.performance.ReportingUnit.DIMENSIONLESS_INTEGRAL;
import static com.avail.performance.ReportingUnit.NANOSECONDS;

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
	/** Statistics for executing parsing instructions. */
	RUNNING_PARSING_INSTRUCTIONS("Running Parsing Operations", NANOSECONDS),

	/**
	 * Statistics for {@link MessageBundleTreeDescriptor#o_Expand(AvailObject,
	 * A_Module) expanding} ParsingOperations.
	 */
	EXPANDING_PARSING_INSTRUCTIONS("Expanding Parsing Operations", NANOSECONDS),

	/** A breakdown of the time spent in L2 optimization phases. */
	L2_OPTIMIZATION_TIME("L2 Translation time", NANOSECONDS),

	/** A breakdown of the time spent in L2 optimization phases. */
	L1_NAIVE_TRANSLATION_TIME(
		"L1 -> L2 Naive translation by nybblecode", NANOSECONDS),

	/** Dimensionless values related to L2Chunk creation. */
	L2_TRANSLATION_VALUES("L2 Translation values", DIMENSIONLESS_INTEGRAL),

	/** A breakdown of time spent in JVM translation. */
	JVM_TRANSLATION_TIME("JVM Translation time", NANOSECONDS),

	/** Reifications of the Java stack.  See {@link StackReifier}.  */
	REIFICATIONS("Java stack reifications", NANOSECONDS),

	/** The Primitives report. */
	PRIMITIVES("Primitives", NANOSECONDS),

	/** The Dynamic Lookups report. */
	DYNAMIC_LOOKUP_TIME("Dynamic Lookup Time", NANOSECONDS),

	/** The Primitive Return Type Checks report. */
	PRIMITIVE_RETURNER_TYPE_CHECKS("Primitive Return Type Checks", NANOSECONDS),

	/**
	 * Non-primitive Return Type Checks report, organized by the returning raw
	 * function name.  This collects contextual timings for non-primitive
	 * returns that had to check the type of the return result.
	 */
	NON_PRIMITIVE_RETURNER_TYPE_CHECKS(
		"Non-primitive Returner Type Checks", NANOSECONDS),

	/**
	 * Non-primitive Return Type Checks report, organized by the raw function
	 * being returned into.  This collects contextual timings for non-primitive
	 * returns that had to check the type of the return result.
	 */
	NON_PRIMITIVE_RETURNEE_TYPE_CHECKS(
		"Non-primitive Returnee Type Checks", NANOSECONDS),

	/** Outermost statements of modules that are loaded. */
	TOP_LEVEL_STATEMENTS("Top Level Statements By Module", NANOSECONDS),

	/** Time spent updating text in workbench transcript. */
	WORKBENCH_TRANSCRIPT("Workbench transcript", NANOSECONDS);


	/** The title of the StatisticReport. */
	private final String title;

	/** The units which the contained reports use. */
	private final ReportingUnit unit;

	/**
	 * @return The title associated with the StatisticReport.
	 */
	public String title ()
	{
		return title;
	}

	/**
	 * @return The {@link ReportingUnit} of the statistics.
	 */
	public ReportingUnit unit ()
	{
		return unit;
	}

	/**
	 * Create the enumeration value.
	 *
	 * @param title The title of the statistic report.
	 */
	StatisticReport (final String title, final ReportingUnit unit)
	{
		this.title = title;
		this.unit = unit;
	}

	/**
	 * The {@link List} of {@link Statistic} objects that have been registered
	 * for this particular {@link StatisticReport}.
	 */
	final List<Statistic> statistics = new ArrayList<>();

	/**
	 * Register a {@link Statistic} with this {@code StatisticReport}.  This
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
			if (report.title().equals(str))
			{
				return report;
			}
		}
		return null;
	}

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
				namedSnapshots.add(new Pair<>(stat.name(), aggregate));
			}
		}
		final Collator collator = Collator.getInstance();
		namedSnapshots.sort((pair1, pair2) ->
		{
			assert pair1 != null && pair2 != null;
			final int byStat = pair1.second().compareTo(pair2.second());
			if (byStat != 0)
			{
				return byStat;
			}
			return collator.compare(pair1.first(), pair2.first());
		});
		return namedSnapshots;
	}

	/**
	 * Output the appropriate {@code StatisticReport reports}.
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
			builder.append(report.title());
			builder.append('\n');
			final List<Pair<String, PerInterpreterStatistic>> pairs =
				report.sortedPairs();
			if (!pairs.isEmpty())
			{
				final PerInterpreterStatistic total =
					new PerInterpreterStatistic();
				for (final Pair<String, PerInterpreterStatistic> pair : pairs)
				{
					pair.second().addTo(total);
				}
				pairs.add(0, new Pair<>("TOTAL", total));
			}
			for (final Pair<String, PerInterpreterStatistic> pair : pairs)
			{
				pair.second().describeOn(builder, report.unit());
				builder.append(" ");
				builder.append(pair.first());
				builder.append('\n');
			}
		}
		return builder.toString();
	}
}
