/*
 * Statistic.java
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

/** An immutable collection of related statistics. */
public class Statistic
{
	/** The name of this {@link Statistic}. */
	public final String name;

	/** The array of {@link PerInterpreterStatistic}s. */
	public final PerInterpreterStatistic[] statistics;

	/**
	 * Answer the name of this {@link Statistic}.
	 *
	 * @return The statistic's name.
	 */
	public String name ()
	{
		return name;
	}

	/**
	 * Construct a new {@link Statistic} with the given name.
	 *
	 * @param name
	 *        The name to give this statistic.
	 * @param report
	 *        The report under which this statistic is classified.
	 */
	public Statistic (final String name, final StatisticReport report)
	{
		this.name = name;
		statistics = new PerInterpreterStatistic[AvailRuntime.maxInterpreters];
		for (int i = 0; i < statistics.length; i++)
		{
			statistics[i] = new PerInterpreterStatistic();
		}
		report.registerStatistic(this);
	}

	/**
	 * Record a sample in my {@link PerInterpreterStatistic} having the
	 * specified contention-avoidance index.
	 *
	 * @param sample
	 *        The sample to add.
	 * @param index
	 *        The index specifying which {@link PerInterpreterStatistic} to add
	 *        the sample to.
	 */
	public void record (final double sample, final int index)
	{
		statistics[index].record(sample);
	}

	/**
	 * Aggregate the information from my array of {@link
	 * PerInterpreterStatistic}s, and return it as a new {@code
	 * PerInterpreterStatistic}.
	 *
	 * @return The aggregated {@code PerInterpreterStatistic}.
	 */
	public PerInterpreterStatistic aggregate ()
	{
		final PerInterpreterStatistic accumulator =
			new PerInterpreterStatistic();
		for (final PerInterpreterStatistic each : statistics)
		{
			each.addTo(accumulator);
		}
		return accumulator;
	}

	/**
	 * Clear each of my {@link PerInterpreterStatistic}s.
	 */
	public void clear ()
	{
		for (final PerInterpreterStatistic each : statistics)
		{
			each.clear();
		}
	}
}
