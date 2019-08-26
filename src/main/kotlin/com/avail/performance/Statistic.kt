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

package com.avail.performance

import com.avail.AvailRuntimeConfiguration.maxInterpreters

/**
 * An immutable collection of related statistics.
 *
 * @property nameSupplier
 *   The name of this [Statistic].
 *
 * @constructor
 * Construct a new `Statistic` with the given name.
 *
 * @param nameSupplier
 *   A lambda that supplies the name for this statistic.
 * @param report
 *   The report under which this statistic is classified.
 */
class Statistic constructor(
	private val nameSupplier: () -> String, report: StatisticReport)
{
	/** The array of [PerInterpreterStatistic]s.  */
	val statistics: Array<PerInterpreterStatistic>

	/**
	 * Answer the name of this `Statistic`.  Note that the [nameSupplier] may
	 * produce different [String]s at different times.
	 *
	 * @return
	 *   The statistic's current name.
	 */
	fun name(): String = nameSupplier.invoke()

	init
	{
		val temp =
			arrayOfNulls<PerInterpreterStatistic>(maxInterpreters)
		for (i in 0 until maxInterpreters)
		{
			temp[i] = PerInterpreterStatistic.emptyStatistic()
		}
		statistics = temp.requireNoNulls()
		report.registerStatistic(this)
	}

	/**
	 * Construct a new `Statistic` with the given fixed name.
	 *
	 * @param name
	 *   The name to give this statistic.
	 * @param report
	 *   The report under which this statistic is classified.
	 */
	constructor(name: String, report: StatisticReport) : this({ name }, report)

	/**
	 * Record a sample in my [PerInterpreterStatistic] having the specified
	 * contention-avoidance index.
	 *
	 * @param sample
	 *   The sample to add.
	 * @param index
	 *   The index specifying which [PerInterpreterStatistic] to add the sample
	 *   to.
	 */
	fun record(sample: Double, index: Int)
	{
		statistics[index].record(sample)
	}

	/**
	 * Aggregate the information from my array of [PerInterpreterStatistic]s,
	 * and return it as a new `PerInterpreterStatistic`.
	 *
	 * @return
	 *   The aggregated `PerInterpreterStatistic`.
	 */
	fun aggregate(): PerInterpreterStatistic
	{
		val accumulator =
			PerInterpreterStatistic.emptyStatistic()
		for (each in statistics)
		{
			each.addTo(accumulator)
		}
		return accumulator
	}

	/** Clear each of my [PerInterpreterStatistic]s. */
	fun clear()
	{
		for (each in statistics)
		{
			each.clear()
		}
	}
}
