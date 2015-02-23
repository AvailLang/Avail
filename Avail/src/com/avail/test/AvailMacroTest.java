/**
 * AvailMacroTest.java
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

package com.avail.test;

import java.io.*;
import java.util.EnumSet;
import com.avail.builder.*;
import com.avail.performance.StatisticReport;

/**
 * Test suite for the Avail macro compiler.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailMacroTest
extends AbstractAvailTest
{
	static
	{
		roots = new ModuleRoots(
			"avail=" + new File("even-newer-avail").getAbsolutePath());
	}

	/**
	 * Test: Compile the Test module of the (new) Avail library.
	 *
	 * @throws Exception
	 *         If an {@linkplain Exception exception} occurs.
	 */
//TODO[MvG] - Restore test when it's actually supported.
//	@Test
	public void availMacroTest () throws Exception
	{
		final long startTime = System.currentTimeMillis();
		compile(new ModuleName("/avail/bootstrap-syntax"));
		System.out.flush();
		System.err.printf(
			"%ntime elapsed = %dms%n",
			System.currentTimeMillis() - startTime);
		final String reportString = StatisticReport.produceReports(
			EnumSet.<StatisticReport>of(
				StatisticReport.PRIMITIVE_RETURN_TYPE_CHECKS));
		System.err.printf(
			"%nPrimitive return type-check times:%n%s",
			reportString);
	}
}
