/**
 * A_BundleTree.java
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

package com.avail.descriptor;

/**
 * {@code A_BundleTree} is an interface that specifies the {@linkplain
 * MessageBundleTreeDescriptor message-bundle-tree}-specific operations that an
 * {@link AvailObject} must implement.  It's a sub-interface of {@link
 * A_BasicObject}, the interface that defines the behavior that all AvailObjects
 * are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_BundleTree
extends A_BasicObject
{
	/**
	 * @return
	 */
	A_Map allBundles ();

	/**
	 * Dispatch to the descriptor.
	 * @return
	 */
	A_Map lazyComplete ();

	/**
	 * Dispatch to the descriptor.
	 * @return
	 */
	A_Map lazyIncomplete ();

	/**
	 * @return
	 */
	A_Map lazyIncompleteCaseInsensitive ();

	/**
	 * Dispatch to the descriptor.
	 * @return
	 */
	A_Map lazyActions ();

	/**
	 * @return
	 */
	A_Map lazyPrefilterMap ();

	/**
	 * @param bundle
	 */
	void addBundle (A_Bundle bundle);

	/**
	 * Dispatch to the descriptor.
	 * @param message
	 * @return
	 */
	boolean removeBundleNamed (A_Atom message);

	/**
	 * Expand the bundle tree if there's anything currently unclassified in it.
	 * By postponing this until necessary, construction of the parsing rules for
	 * the grammar is postponed until actually necessary.
	 *
	 * @param module The current module in which this bundle tree is being used
	 *               to parse.
	 */
	void expand (A_Module module);

	/**
	 * The specified bundle has been added or modified in this bundle tree.
	 * Adjust the bundle tree as needed.
	 *
	 * @param bundle The {@link MessageBundleDescriptor bundle} that has been
	 *               added or modified in this bundle tree.
	 */
	void flushForNewOrChangedBundle (A_Bundle bundle);
}
