/*
 * L2Entity.kt
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
package avail.optimizer

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.register.L2Register.RegisterKind

/**
 * An `L2Entity` is an abstraction for things that have reads and writes within
 * the [L2Instruction]s of an [L2BasicBlock] of an [L2ControlFlowGraph].
 *
 * @see DataCouplingMode
 */
interface L2Entity
// No methods are needed, beyond equals() and hashCode().

/**
 * An `L2EntityAndKind` bundles an [L2Entity] and [RegisterKind].  It's useful
 * for keeping track of the needs and production of values in the
 * [L2ControlFlowGraph] by the [DeadCodeAnalyzer], to determine which
 * instructions can be removed and which are essential.
 */
data class L2EntityAndKind constructor(
	val entity: L2Entity,
	val kind: RegisterKind)
{
	override fun toString(): String
	{
		return "$entity[${kind.kindName}]"
	}
}
