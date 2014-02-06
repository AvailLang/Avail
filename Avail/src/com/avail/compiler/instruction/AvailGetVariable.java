/**
 * AvailGetVariable.java
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

package com.avail.compiler.instruction;


/**
 * Push the value of a variable of some sort.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class AvailGetVariable extends AvailInstructionWithIndex
{
	/**
	 * Whether this instruction should be the clearing form of get or the
	 * non-clearing form.  The clearing form is used only when this is the last
	 * use of the variable before the next write.
	 *
	 */
	boolean canClear;


	/**
	 * Construct a new {@link AvailGetVariable}.
	 *
	 * @param variableIndex The index of the variable in some unspecified
	 *                      coordinate system.
	 */
	public AvailGetVariable (int variableIndex)
	{
		super(variableIndex);
	}


	/**
	 * Set whether this is a clearing get (true) or a regular duplicating get
	 * (false).  A clearing get is the last use of the variable until the next
	 * write, so it's safe to clear the variable's contents.  This avoids
	 * increasing the value's reference count unnecessarily.
	 * <p>
	 * This must be set correctly prior to final code generation.
	 *
	 * @param newFlag The new value of the flag.
	 */
	public void canClear (
			final boolean newFlag)
	{
		canClear = newFlag;
	}
}
