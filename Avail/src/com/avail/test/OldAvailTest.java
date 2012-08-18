/**
 * OldAvailTest.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import org.junit.*;
import com.avail.builder.*;
import com.avail.optimizer.L2Translator;

/**
 * Broad test suite for the Avail compiler, interpreter, and library.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class OldAvailTest
extends AbstractAvailTest
{
	static
	{
		roots = new ModuleRoots("avail=" + new File("avail").getAbsolutePath());
	}

	/**
	 * Test: Compile the Guillemet-Test module.
	 *
	 * @throws Exception
	 *         If an {@linkplain Exception exception} occurs.
	 */
	@Test
	public void guillemetTest () throws Exception
	{
		final long startTime = System.currentTimeMillis();
		compile(new ModuleName("/avail/Guillemet-Test"));
		System.out.flush();
		System.err.printf(
			"%ntime elapsed = %dms", System.currentTimeMillis() - startTime);
		System.out.printf("Instructions%n\tGen=%d%n\tKept=%d%n\tRemv=%d%n",
			L2Translator.generatedInstructionCount,
			L2Translator.keptInstructionCount,
			L2Translator.removedInstructionCount);
	}

//	@Test
//	public void guillemetTest2 () throws Exception
//	{
//		guillemetTest();
//	}
//	@Test
//	public void guillemetTest3 () throws Exception
//	{
//		guillemetTest();
//	}
//	@Test
//	public void guillemetTest4 () throws Exception
//	{
//		guillemetTest();
//	}
//	@Test
//	public void guillemetTest5 () throws Exception
//	{
//		guillemetTest();
//	}
//	@Test
//	public void guillemetTest6 () throws Exception
//	{
//		guillemetTest();
//	}
//	@Test
//	public void guillemetTest7 () throws Exception
//	{
//		guillemetTest();
//	}
//	@Test
//	public void guillemetTest8 () throws Exception
//	{
//		guillemetTest();
//	}
//	@Test
//	public void guillemetTest9 () throws Exception
//	{
//		guillemetTest();
//	}
//	@Test
//	public void guillemetTest10 () throws Exception
//	{
//		guillemetTest();
//	}
}
