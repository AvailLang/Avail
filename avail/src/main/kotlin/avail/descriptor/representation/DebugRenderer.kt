/*
 * DebugRenderer.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
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

package avail.descriptor.representation

import org.jetbrains.annotations.Debug.Renderer

/**
 * This interface marks a class as having a renderer that can alter how to
 * presents its instances in the debugger.  The class inheriting this interface
 * is responsible for implementing the methods specified by the interface.
 *
 * Note that the [Renderer] annotation was added by JetBrains years ago, and
 * still does absolutely nothing at all for Kotlin classes (2024.10.17), hence
 * the introduction of this interface that client classes can use, without
 * requiring a separate manually constructed debugger renderer configuration per
 * class. The user still has to set this up for the DebugRenderer interface by:
 *
 * In the debugger (there doesn't seem to be another way), go to the Variables
 * pane and choose "Customize Data Views" from the bottom of the popup menu.
 * Add a new renderer with the "+" button.  Give it a name and specify that it's
 * for the class "avail.descriptor.representation.DebugRenderer".  Under "When
 * rendering a node", choose "Use following expression:", select "Java" from the
 * drop-down list, and enter "nameForDebugger()". Under "When expanding a node",
 * select "Use following expression:", select "Java" from the drop-down list,
 * and enter "describeForDebugger()".  Finally, click OK to close the dialog.
 */
@Renderer(
	text = "nameForDebugger()",
	childrenArray = "describeForDebugger()")
interface DebugRenderer
{
	/**
	 * Produce a name suitable to display in a single line in the IntelliJ (and
	 * Avail) debugger.
	 */
	fun nameForDebugger(): String

	/**
	 * Produce an [Array] of objects suitable for use in the tree expansion in
	 * the IntelliJ (and Avail) debugger.
	 */
	fun describeForDebugger(): Array<*>
}
