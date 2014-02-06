/**
 * StatisticReport.java
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

package com.avail.tools.compiler.configuration;

import com.avail.annotations.Nullable;

/**
 * The statistic reports requested of the compiler:
 * <ul>
 * <li>L2Operations ~ The most time-intensive level-two operations</li>
 * <li>DynamicLookups ~ The most time-intensive dynamic method lookups.</li>
 * <li>Primitives ~ The primitives that are the most time-intensive to run
 *                  overall.</li>
 * <li>PrimitiveReturnTypeChecks ~ The primitives that take the most time
 *                                 checking return types.</li>
 * </ul>
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public enum StatisticReport
{
	/** The Level-Two Operations report */
	L2_OPERATIONS
	{
		@Override
		public String keyword ()
		{
			return "L2Operations";
		}
		@Override
		public boolean displayL2Operations()
		{
			return true;
		}
	},

	/** The Dynamic Lookups report */
	DYNAMIC_LOOKUPS
	{
		@Override
		public String keyword ()
		{
			return "DynamicLookups";
		}
		@Override
		public boolean displayDynamicLookups()
		{
			return true;
		}
	},

	/** The Primitives report */
	PRIMITIVES
	{
		@Override
		public String keyword ()
		{
			return "Primitives";
		}
		@Override
		public boolean displayPrimitives()
		{
			return true;
		}
	},

	/** The Primitive Return Type Checks report */
	PRIMITIVE_RETURN_TYPE_CHECKS
	{
		@Override
		public String keyword ()
		{
			return "PrimitiveReturnTypeChecks";
		}
		@Override
		public boolean displayPrimitiveReturnTypeChecks()
		{
			return true;
		}
	};

	/**
	 * @return True only when the user has specified that they wish to see all
	 *         reports or the Level-Two Operations report specifically. This is
	 *         accomplished through method overrides.
	 */
	public boolean displayL2Operations()
	{
		return false;
	}

	/**
	 * @return True only when the user has specified that they wish to see all
	 *         reports or the Dynamic Lookups report specifically. This is
	 *         accomplished through method overrides.
	 */
	public boolean displayDynamicLookups()
	{
		return false;
	}

	/**
	 * @return True only when the user has specified that they wish to see all
	 *         reports or the Primitives report specifically. This is
	 *         accomplished through method overrides.
	 */
	public boolean displayPrimitives()
	{
		return false;
	}

	/**
	 * @return True only when the user has specified that they wish to see all
	 *         reports or the Primitive Return Type Checks report specifically.
	 *         This is accomplished through method overrides.
	 */
	public boolean displayPrimitiveReturnTypeChecks()
	{
		return false;
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
}
