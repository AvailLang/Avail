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

/**
 * The stable identity of an equivalence class of [L2SemanticValue]s within an
 * [L2ValueManifest].
 *
 * A `ValueClass` deliberately holds **no state at all** beyond its [id].  Every
 * fact about the class – its membership, its [TypeRestriction], its registers,
 * its postponed instruction – lives in the manifest that is asking, so that the
 * same identity can safely be shared between the manifests of a control flow
 * graph while each manifest holds its own immutable, private view of what that
 * value is known to be.
 *
 * This is the whole reason the identity is separated from the state.  Both
 * outbound edges of a branch refer to the same values, and must agree about
 * *which* value is which, while disagreeing about what is known of them.  A
 * mutable identity – for instance a classic union-find node with a parent
 * pointer, or an [L2Synonym] whose member set could grow in place – would let
 * a merge performed along one edge silently alter the other.  Merging is
 * therefore recorded in each manifest's own forwarding map, not here.
 *
 * The [id]s are numbered *per manifest*, starting at one, and are copied along
 * with the rest of a manifest's state when a manifest is cloned or inherited.
 * They are meaningless across unrelated manifests, and since a `ValueClass`
 * never escapes the manifest that made it – the only operation that passes one
 * outward, [L2SemanticValue.recordDerivationIn], hands it straight back – there
 * is nothing to be gained from numbering them globally, and a shared atomic
 * counter to be avoided.
 *
 * Because equality and hashing follow the [id], and the ids of a manifest are
 * small and assigned deterministically, a collection keyed by `ValueClass` is
 * no longer at the mercy of identity hash codes and cannot make code generation
 * vary between runs.
 *
 * @property id
 *   Distinguishes this class from every other in the same [L2ValueManifest],
 *   and is unrelated to those in any other manifest.  Small enough to be
 *   legible while debugging, and to serve as a subscript.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@JvmInline
value class ValueClass(val id: Int)
{
	override fun toString(): String = "V$id"
}
