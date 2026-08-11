/*
 * ValueClass.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.optimizer.values.L2SemanticValue
import java.util.concurrent.atomic.AtomicLong

/**
 * The stable identity of an equivalence class of [L2SemanticValue]s within an
 * [L2ValueManifest].
 *
 * A `ValueClass` deliberately holds **no state at all** beyond a debugging
 * label.  Every fact about the class – its membership, its [TypeRestriction],
 * its registers, its postponed instruction – lives in the manifest that is
 * asking, so that the same identity can safely be shared between the manifests
 * of a control flow graph while each manifest holds its own immutable, private
 * view of what that value is known to be.
 *
 * This is the whole reason the identity is separated from the state.  Both
 * outbound edges of a branch refer to the same values, and must agree about
 * *which* value is which, while disagreeing about what is known of them.  A
 * mutable identity – for instance a classic union-find node with a parent
 * pointer, or an [L2Synonym] whose member set could grow in place – would let
 * a merge performed along one edge silently alter the other.  Merging is
 * therefore recorded in each manifest's own forwarding map, not here.
 *
 * Note that instances compare by identity.  Any collection keyed by
 * `ValueClass` must preserve insertion order, or code generation becomes
 * dependent on identity hash codes and so varies between runs.  Kotlin's
 * `mutableMapOf` and `mutableSetOf` answer `LinkedHashMap` and `LinkedHashSet`,
 * which is exactly right; do not substitute a `HashMap` or `HashSet`.
 *
 * @property debugId
 *   A small integer that makes instances legible while debugging.  It takes no
 *   part in equality or hashing, and must not be used to order anything.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ValueClass private constructor(private val debugId: Long)
{
	override fun toString(): String = "V$debugId"

	companion object
	{
		/** Supplies [debugId]s.  Purely cosmetic. */
		private val counter = AtomicLong(0)

		/**
		 * Answer a brand new [ValueClass], distinct from every other.
		 *
		 * @return
		 *   The new [ValueClass].
		 */
		fun newValueClass() = ValueClass(counter.incrementAndGet())
	}
}
