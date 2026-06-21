/*
 * StatisticReport.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

import avail.compiler.ParsingOperation
import avail.descriptor.representation.A_BundleTree
import avail.optimizer.StackReifier
import avail.performance.ReportingUnit.BYTES
import avail.performance.ReportingUnit.NANOSECONDS
import avail.utility.Strings.buildUnicodeBox
import avail.utility.ifZero
import avail.utility.iterableWith
import java.text.Collator
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicReference

/**
 * The statistic reports that group specific [Statistic]s collected by the
 * runtime.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property title
 *  The title of the [StatisticReport].
 * @property unit
 *   The units which the contained reports use.
 * @constructor
 * Create the enumeration value.
 *
 * @param title
 *   The title of the statistic report.
 * @param unit
 *   The [ReportingUnit] used to measure statistics within this report.
 */
enum class StatisticReport constructor(
	val title: String,
	val unit: ReportingUnit)
{
	/** Statistics for executing parsing instructions. */
	RUNNING_PARSING_INSTRUCTIONS("Running Parsing Operations", NANOSECONDS),

	/** Statistics for type checking while parsing. */
	TYPE_CHECKING_FOR_PARSER("Parser Type Check", NANOSECONDS),

	/** Statistics for expanding the [A_BundleTree] for [ParsingOperation]s. */
	EXPANDING_PARSING_INSTRUCTIONS(
		"Expanding Parsing Operations", NANOSECONDS),

	/** A breakdown of the time spent in L2 optimization phases. */
	L2_OPTIMIZATION_TIME("L2 Translation time", NANOSECONDS),

	/** A breakdown of final generation phases of L2->JVM. */
	FINAL_JVM_TRANSLATION_TIME("Final JVM Translation time", NANOSECONDS),

	/** Reifications of the Java stack.  See [StackReifier]. */
	REIFICATIONS("Java stack reifications", NANOSECONDS),

	/** The Primitives report. */
	PRIMITIVES("Primitives", NANOSECONDS),

	/** A report of how long some primitives take for type checks. */
	TYPE_CHECKS_IN_PRIMITIVES("Type Checks in Primitives", NANOSECONDS),

	/** Miscellaneous costs associated with running L2Simple-specific code. */
	L2SIMPLE_NILPOTENT("L2Simple nilpotent attempt", NANOSECONDS),

	/** A report of how long and deep dynamic lookups are. */
	DYNAMIC_LOOKUP_BY_TARGET("Dynamic lookup by target", NANOSECONDS),

	/** A report of how long and deep dynamic lookups are. */
	DYNAMIC_LOOKUP_BY_CALLER("Dynamic lookup by caller", NANOSECONDS),

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
	TOP_LEVEL_STATEMENTS(
		"Top Level Statements By Module (CPU time)", NANOSECONDS),

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
	 * for this particular [StatisticReport].  Entries are added by prepending
	 * with a compare-and-set loop.  Reads should use volatile semantics.
	 */
	val statisticsChain = AtomicReference<Statistic?>(null)

	/**
	 * Register a [Statistic] with this `StatisticReport`.  This happens when
	 * the statistic is first created, as part of its constructor.  Adding to
	 * the [statisticsChain] should be performed with compare-and-set semantics,
	 * and reads should use volatile semantics.
	 *
	 * @param statistic The [Statistic] to be registered.
	 */
	fun registerStatistic(statistic: Statistic)
	{
		while (true)
		{
			val existingHead = statisticsChain.get()
			statistic.nextInReport = existingHead
			if (statisticsChain.compareAndSet(existingHead, statistic)) break
		}
	}

	/** Clear my [Statistic]s. */
	fun clear()
	{
		statisticsChain.get()
			.iterableWith(Statistic::nextInReport)
			.forEach(Statistic::clear)
	}

	/**
	 * Collect the aggregates of my statistics, filter out the ones with zero
	 * counts, then sort descending by their sums.  Maintain names with the
	 * aggregated statistics as [Pair]s.
	 *
	 * @return A sorted [List] of [Pair]&lt;[String],
	 * [PerInterpreterStatistic]&gt;.
	 */
	fun sortedPairs(): MutableList<Pair<String, PerInterpreterStatistic>>
	{
		val namedSnapshots = statisticsChain.get()
			.iterableWith(Statistic::nextInReport)
			.map { it.name() to it.aggregate() }
			.filter { (_, aggregate) -> aggregate.count() > 0L }
			.toMutableList()
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
		fun reportFor(str: String) = entries.firstOrNull { it.title == str }

		/**
		 * Output the appropriate `StatisticReport reports`.
		 *
		 * @param reports
		 *   The compiler configuration where the report settings are stored.
		 * @return
		 *   The specified reports as a single [String].
		 */
		fun produceReports(reports: EnumSet<StatisticReport>) =
			buildString {
				reports.forEachIndexed { index, report ->
					val reportText = buildUnicodeBox(report.title) {
						val pairs = report.sortedPairs()
						if (pairs.isNotEmpty())
						{
							val total = PerInterpreterStatistic()
							pairs.forEach { (_, stat) -> stat.addTo(total) }
							pairs.add(0, "TOTAL" to total)
							pairs.forEach { (name, stat) ->
								stat.describeOn(
									this@buildUnicodeBox,
									report.unit
								)
								append(" $name\n")
							}
						}
					}
					append(reportText)
					if (index != reports.size - 1) append("\n")
				}
			}
	}
}
