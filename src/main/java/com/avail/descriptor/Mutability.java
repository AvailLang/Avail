/*
 * Mutability.java
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

package com.avail.descriptor;

/**
 * A description of the mutability of an {@linkplain AvailObject object}. This
 * information is not maintained by an object itself, but rather by the
 * {@linkplain Descriptor descriptor} that specifies its representation and
 * behavior.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
enum Mutability
{
	/*
	 * Much code assumes the exact ordering shown below. Do not change it under
	 * any circumstances!
	 */

	/**
	 * Indicates that instances of the {@linkplain Descriptor descriptor} are
	 * <em>mutable</em>. An {@linkplain AvailObject object} can be
	 * <em>mutable</em> only if there exists but a single reference to it. This
	 * is a necessary condition, but not a sufficient condition for mutability.
	 * Most objects begin existence in the <em>mutable</em> state. All slots of
	 * a <em>mutable</em> object may be modified.
	 */
	MUTABLE,

	/**
	 * Indicates that instances of the {@linkplain Descriptor descriptor} are
	 * <em>immutable</em>. An {@linkplain AvailObject object} that is
	 * <em>immutable</em> may have more than one reference, but must be
	 * reachable only by a single {@linkplain FiberDescriptor fiber}. An
	 * <em>immutable</em> object may not be modified, in general, though some
	 * {@linkplain AbstractDescriptor#
	 * allowsImmutableToMutableReferenceInField(AbstractSlotsEnum) slots} may
	 * remain mutable.
	 */
	IMMUTABLE,

	/**
	 * Indicates that instances of the {@linkplain Descriptor descriptor} are
	 * immutable and shared. An {@linkplain AvailObject object} that is shared
	 * may have more than one reference and may be reachable by multiple
	 * {@linkplain FiberDescriptor fibers}. {@linkplain ModuleDescriptor
	 * Modules}, {@linkplain MethodDescriptor methods}, {@linkplain
	 * MessageBundleTreeDescriptor message bundle trees}, and {@linkplain
	 * AtomDescriptor special atoms} begin existence in the <em>shared</em>
	 * state. A fiber begins existence <em>shared</em> only if the parent fiber
	 * retains a reference to the new child. The origin {@linkplain
	 * FunctionDescriptor function} of a new fiber becomes <em>shared</em>
	 * before its execution. All special objects and other root objects begin
	 * existence <em>shared</em>. Other objects become <em>shared</em> just
	 * before assignment to the {@linkplain ObjectSlotsEnum object slot} of a
	 * <em>shared</em> object.
	 */
	SHARED;

	/*
	 * Remember how we said you shouldn't change the order of these?  Let's
	 * make sure everyone has read the comments before changing stuff.
	 */
	static
	{
		assert MUTABLE.ordinal() == 0;
		assert IMMUTABLE.ordinal() == 1;
		assert SHARED.ordinal() == 2;
	}
}
