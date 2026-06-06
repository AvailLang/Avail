/*
 * StateOfL1.kt
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
package avail.interpreter.levelTwoSimple

import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_RegisterDump
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.interpreter.levelTwo.L1InstructionStepper
import avail.interpreter.levelTwoSimple.instructions.L2SimpleInstruction
import avail.interpreter.levelTwoSimple.instructions.registers.Read
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray

/**
 * A [StateOfL1] hold some of the information needed to construct an L1
 * [A_Continuation] during reification within L2Simple.
 *
 * @property pc
 *   The level one program counter in the current frame.
 * @property stackp
 *   The level one stack pointer in the current frame.
 * @property liveSlots
 *   A [ReadArray] of [Read]s used to set up the level one slots of a reified
 *   continuation.  A [Read]`(0)` indicates the value is [nil].  Note that these
 *   slots are *not* examined when reentering the continuation *except* when it
 *   has become invalid and has fallen back to the L1 interpreter
 *   ([L1InstructionStepper]).
 * @property allLiveRegisters
 *   After register coloring, this field holds the complete set of live
 *   registers at some point where this [StateOfL1] is in an
 *   [L2SimpleInstruction], It does not include any registers dropped after
 *   final consumption by reads in this instruction, nor any registers written
 *   by this instruction.  These registers will be saved and restored via an
 *   [A_RegisterDump] if reification happens.  Note that this field *is not*
 *   subject to visitation in the [L2SimpleInstruction.transformed] method.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
data class StateOfL1
constructor(
	val pc: Int,
	val stackp: Int,
	val liveSlots: ReadArray,
	var allLiveRegisters: ReadArray? = null)
