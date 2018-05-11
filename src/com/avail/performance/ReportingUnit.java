/*
 * ReportingUnit.java
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

import com.avail.AvailRuntime;

import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.POSITIVE_INFINITY;
import static java.lang.String.format;

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
public enum ReportingUnit
{
	/** The number of nanoseconds taken by some activity. */
	NANOSECONDS(
		new Range(999_999_500.0,     POSITIVE_INFINITY, 1.0e-9, "%, 8.3f s "),
		new Range(    999_999.5,     999_999_500.0,     1.0e-6, "%, 8.3f ms"),
		new Range(NEGATIVE_INFINITY,     999_999.5,     1.0e-3, "%, 8.3f µs")
	),

	/** The number of bytes consumed or produced by some activity. */
	BYTES(
		new Range(999_999_999_500.0, POSITIVE_INFINITY, 1.0e-12, "%, 8.3f TB"),
		new Range(    999_999_500.0, 999_999_999_500.0, 1.0e-9,  "%, 8.3f GB"),
		new Range(        999_999.5,     999_999_500.0, 1.0e-6,  "%, 8.3f MB"),
		new Range(NEGATIVE_INFINITY,         999_999.5, 1.0e-3,  "%, 8.3f KB")
	),

	/** A dimensionless measurement, such as a count of something. */
	DIMENSIONLESS_DOUBLE(
		new Range(NEGATIVE_INFINITY, POSITIVE_INFINITY, 1.0, "%, 10.3f")
	),

	/** A dimensionless measurement, such as a count of something. */
	DIMENSIONLESS_INTEGRAL(
		new Range(
			NEGATIVE_INFINITY,
			POSITIVE_INFINITY,
			1.0,
			"%, 8.0f",
			"%, 8.3f±%,8.3f")
	);

	/** An [inclusive, exclusive) span, a scale, and a format string. */
	private static class Range
	{
		/** The lower (inclusive) bound of the range. */
		final double low;

		/** The upper (exclusive) bound of the range. */
		final double high;

		/** The amount to multiply by before printing. */
		final double scale;

		/**
		 * The format string for presenting a statistic that falls in this
		 * range.  The first argument is the scaled sum, and the second is the
		 * scaled standard deviation of the sample set.  Both values are in the
		 * same units.
		 */
		final String format;

		/**
		 * The format string for presenting the mean of a statistic.
		 */
		final String meanFormat;

		/**
		 * Create a range.
		 *
		 * @param low
		 *        The lowest value inside the range.
		 * @param high
		 *        The lowest value just beyond the range.
		 * @param scale
		 *        A scaling factor to multiply by the value before formatting.
		 * @param format
		 *        The format string to render values or a total.  It expects the
		 *        first argument to be the (double) value to render, and the
		 *        second argument to be the standard deviation.
		 * @param meanFormat
		 *        The format string to render means.  It expects the first
		 *        argument to be the (double) value to render, and the second
		 *        argument to be the standard deviation.
		 */
		Range (
			final double low,
			final double high,
			final double scale,
			final String format,
			final String meanFormat)
		{
			this.low = low;
			this.high = high;
			this.scale = scale;
			this.format = format;
			this.meanFormat = meanFormat;
		}

		/**
		 * Create a range.
		 *
		 * @param low
		 *        The lowest value inside the range.
		 * @param high
		 *        The lowest value just beyond the range.
		 * @param scale
		 *        A scaling factor to multiply by the value before formatting.
		 * @param format
		 *        The format string to render values <em>or</em> the mean.  It
		 *        expects the first argument to be the (double) value to render,
		 *        and the second argument to be the standard deviation.
		 */
		Range (
			final double low,
			final double high,
			final double scale,
			final String format)
		{
			this(low, high, scale, format, format);
		}
	}

	/**
	 * The array of ranges to select a rendering strategy for statistics.
	 */
	final Range[] ranges;

	/**
	 * Construct a {@code ReportingUnit} with the given vararg {@link Range}s.
	 *
	 * @param ranges
	 *        The {@link Range}s used for rendering a statistic.
	 */
	ReportingUnit (final Range... ranges)
	{
		this.ranges = ranges;
	}

	/**
	 * Produce the formatted version of a statistical value for this kind of
	 * unit.
	 *
	 * @param count
	 *        How many samples were recorded.
	 * @param mean
	 *        The mean value of those samples.
	 * @param standardDeviation
	 *        The standard deviation of the samples.
	 * @param isMean
	 *        Whether to format the value as a mean, rather than a total or
	 *        sample.
	 * @return A {@link String} summarizing the samples.
	 */
	String describe (
		final long count,
		final double mean,
		final double standardDeviation,
		final boolean isMean)
	{
		final double total = count * mean;
		for (final Range range : ranges)
		{
			if (range.low <= total && total < range.high)
			{
				final String format = isMean ? range.meanFormat : range.format;
				return format(
					format,
					total * range.scale,
					standardDeviation * range.scale);
			}
		}
		return "N/A";
	}
}
