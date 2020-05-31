/*
 * Mutability.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
*
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.representation

import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.representation.Mutability.IMMUTABLE
import com.avail.descriptor.representation.Mutability.MUTABLE
import com.avail.descriptor.representation.Mutability.SHARED

/**
 * A description of the mutability of an [AvailObject]. This information is not
 * maintained by an object itself, but rather by the [AbstractDescriptor] that
 * specifies its representation and behavior.
 *
 * Much code assumes these values are [MUTABLE], [IMMUTABLE], and [SHARED], in
 * that exact order. Do not change it under any circumstances!
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
enum class Mutability {
	/**
	 * Indicates that instances of the [descriptor][Descriptor] are *mutable*.
	 * An [object][AvailObject] can be *mutable* only if there exists but a
	 * single reference to it. This is a necessary condition, but not a
	 * sufficient condition for mutability. Most objects begin existence in the
	 * *mutable* state. All slots of a *mutable* object may be modified.
	 */
	MUTABLE,

	/**
	 * Indicates that instances of the [descriptor][Descriptor] are *immutable*.
	 * An [object][AvailObject] that is *immutable* may have more than one
	 * reference, but must be reachable only by a single
	 * [fiber][FiberDescriptor]. An *immutable* object may not be modified, in
	 * general, though some
	 * [slots][AbstractDescriptor.allowsImmutableToMutableReferenceInField] may
	 * remain mutable.
	 */
	IMMUTABLE,

	/**
	 * Indicates that instances of the [descriptor][Descriptor] are immutable
	 * and shared. An [object][AvailObject] that is shared may have more than
	 * one reference and may be reachable by multiple [fibers][FiberDescriptor].
	 * [Modules][ModuleDescriptor], [methods][MethodDescriptor],
	 * [message&32;bundle&#32;trees][A_BundleTree], and
	 * [special&#32;atoms][A_Atom] begin existence in the *shared* state. A
	 * fiber begins existence *shared* only if the parent fiber retains a
	 * reference to the new child. The origin [function][A_Function] of a new
	 * fiber becomes *shared* before its execution. All special objects and
	 * other root objects begin existence *shared*. Other objects become
	 * *shared* just before assignment to the [object&#32;slot][ObjectSlotsEnum]
	 * of a *shared* object.
	 */
	SHARED
}
