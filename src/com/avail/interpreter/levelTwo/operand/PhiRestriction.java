/*
 * PhiRestriction.java
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

package com.avail.interpreter.levelTwo.operand;
import com.avail.interpreter.levelTwo.register.L2Register;

/**
 * This mechanism allows type information for an {@link L2Register} to be
 * restricted along a branch. A good example is a type-testing instruction,
 * which narrows the type along the "pass" branch, and adds exclusions to the
 * {@link TypeRestriction} along the "fail" branch.
 *
 * @author Mark van Gulik&lt;mark@availlang.org&gt;
 */
public final class PhiRestriction
{
	/**
	 * The {@link L2Register} which is to be restricted along this control flow
	 * branch.
	 */
	public final L2Register register;

	/**
	 * The new {@link TypeRestriction} for the register along the affected edge.
	 */
	public final TypeRestriction typeRestriction;

	/**
	 * Create a {@code PhiRestriction}, which narrows a register's type
	 * restriction along a control flow edge.
	 *
	 * @param register
	 *        The register to restrict along this edge.
	 * @param typeRestriction
	 *        The {@link TypeRestriction} that the register will have along the
	 *        affected edge.
	 */
	public PhiRestriction (
		final L2Register register,
		final TypeRestriction typeRestriction)
	{
		this.register = register;
		this.typeRestriction = typeRestriction;
	}
}
