/*
 * StatisticReport.kt
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

package com.avail.performance

import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.bundles.A_BundleTree.Companion.expand
import com.avail.optimizer.StackReifier
import com.avail.performance.ReportingUnit.BYTES
import com.avail.performance.ReportingUnit.DIMENSIONLESS_INTEGRAL
import com.avail.performance.ReportingUnit.NANOSECONDS
import com.avail.utility.ifZero
import java.text.Collator
import java.util.EnumSet

/**
 * The statistic reports requested of the compiler:
 *
 *  * L2Operations ~ The most time-intensive level-two operations
 *  * DynamicLookups ~ The most time-intensive dynamic method lookups.
 *  * Primitives ~ The primitives that are the most time-intensive to run
 *  overall.
 *  * PrimitiveReturnTypeChecks ~ The primitives that take the most time
 *  checking return types.
 *  * NonprimitiveReturnTypeChecks ~ Returns from non-primitives that had to
 *  check the return type.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property title
 *  The title of the StatisticReport.
 * @property unit
 *   The units which the contained reports use.
 * @constructor
 * Create the enumeration value.
 *
 * @param title
 * The title of the statistic report.
 */
enum class StatisticReport constructor(
	val title: String, val unit: ReportingUnit)
{
	/** Statistics for executing parsing instructions. */
	RUNNING_PARSING_INSTRUCTIONS("Running Parsing Operations", NANOSECONDS),

	/**
	 * Statistics for [expanding][A_BundleTree.expand] ParsingOperations.
	 */
	EXPANDING_PARSING_INSTRUCTIONS("Expanding Parsing Operations", NANOSECONDS),

	/** A breakdown of the time spent in L2 optimization phases. */
	L2_OPTIMIZATION_TIME("L2 Translation time", NANOSECONDS),

	/** A breakdown of the time spent in L2 optimization phases. */
	L1_NAIVE_TRANSLATION_TIME(
		"L1 -> L2 Naive translation by nybblecode", NANOSECONDS),

	/** Dimensionless values related to L2Chunk creation. */
	L2_TRANSLATION_VALUES("L2 Translation values", DIMENSIONLESS_INTEGRAL),

	/** A breakdown of final generation phases of L2->JVM. */
	FINAL_JVM_TRANSLATION_TIME("Final JVM Translation time", NANOSECONDS),

	/** Reifications of the Java stack.  See [StackReifier]. */
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
	WORKBENCH_TRANSCRIPT("Workbench transcript", NANOSECONDS),

	/** Time spent serializing, by SerializerOperation. */
	SERIALIZE_TRACE("Serialization tracing", NANOSECONDS),

	/** Time spent serializing, by SerializerOperation. */
	SERIALIZE_WRITE("Serialization writing", NANOSECONDS),

	/** Time spent deserializing, by SerializerOperation. */
	DESERIALIZE("Deserialization", NANOSECONDS),

	/**
	 * The estimated number of bytes allocated for descriptors with the given
	 * class name.
	 */
	ALLOCATIONS_BY_DESCRIPTOR_CLASS("Allocations by initial descriptor", BYTES);

	/**
	 * The [List] of [Statistic] objects that have been registered
	 * for this particular [StatisticReport].
	 */
	internal val statistics = mutableListOf<Statistic>()

	/**
	 * Register a [Statistic] with this `StatisticReport`.  This happens when
	 * the statistic is first created, as part of its constructor. Access to the
	 * [List] of [statistics] is synchronized on the list, to ensure atomic
	 * access among registrations and between registrations and enumeration of
	 * the list.
	 *
	 * @param statistic The [Statistic] to be registered.
	 */
	fun registerStatistic(statistic: Statistic) =
		synchronized(statistics) { statistics.add(statistic) }

	/** Clear all my [Statistic]s. */
	fun clear() = synchronized(statistics) { statistics.forEach { it.clear() } }

	/**
	 * Collect the aggregates of my statistics, filter out the ones with zero
	 * counts, then sort descending by their sums.  Maintain names with the
	 * aggregated statistics as [Pair]s.
	 *
	 * @return A sorted [List] of [Pair]&lt;[String],
	 * [PerInterpreterStatistic]&gt;.
	 */
	fun sortedPairs(): MutableList<Pair<String, PerInterpreterStatistic>> =
		synchronized(statistics) {
			val namedSnapshots =
				statistics.map { it.name() to it.aggregate() }.toMutableList()
			namedSnapshots.removeIf {
				(_, aggregate) -> aggregate.count() == 0L
			}
			val collator = Collator.getInstance()
			namedSnapshots.sortWith {
				(name1, aggregate1), (name2, aggregate2) ->
				aggregate1.compareTo(aggregate2).ifZero {
					collator.compare(name1, name2)
				}
			}
			return namedSnapshots
		}

	companion object
	{
		/**
		 * Answer the StatisticReport associated with the given keyword.
		 *
		 * @param str
		 *   The keyword.
		 * @return
		 *   The corresponding StatisticReport.
		 */
		fun reportFor(str: String): StatisticReport?
		{
			return values().firstOrNull { it.title == str }
		}

		/**
		 * Output the appropriate `StatisticReport reports`.
		 *
		 * @param reports
		 *   The compiler configuration where the report settings are stored.
		 * @return
		 *   The specified reports as a single [String].
		 */
		fun produceReports(reports: EnumSet<StatisticReport>): String =
			StringBuilder("\n").apply {
				reports.forEach { report ->
					append("\n${report.title}\n")
					val pairs = report.sortedPairs()
					if (pairs.isNotEmpty()) {
						val total = PerInterpreterStatistic()
						pairs.forEach { (_, stat) -> stat.addTo(total) }
						pairs.add(0, "TOTAL" to total)
						pairs.forEach { (name, stat) ->
							stat.describeOn(this@apply, report.unit)
							append(" $name\n")
						}
					}
				}
			}.toString()
	}
}
