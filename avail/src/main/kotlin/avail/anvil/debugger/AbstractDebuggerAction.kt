/*
 * AbstractDebuggerAction.kt
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

package avail.anvil.debugger

import avail.anvil.actions.AbstractWorkbenchAction
import avail.anvil.shortcuts.AvailDebuggerShortCut

/**
 * An [AbstractDebuggerAction] is attached to an [AvailDebugger], and
 * automatically installs itself into the inputMap and actionMap of the root of
 * the debugger's frame, if an accelerator is provided.
 *
 * @constructor
 * Construct a new [AbstractDebuggerAction].
 *
 * @param debugger
 *   The owning [AvailDebugger].
 * @param name
 *   The name of the action.
 * @param shortcut
 *   The [AvailDebuggerShortCut] used to invoke the action or `null` if there
 *   is no shortcut for this [AbstractDebuggerAction].
 */
abstract class AbstractDebuggerAction(
	@Suppress("MemberVisibilityCanBePrivate")
	val debugger: AvailDebugger,
	name: String,
	shortcut: AvailDebuggerShortCut? = null)
: AbstractWorkbenchAction(
	debugger.workbench,
	name,
	shortcut,
	debugger.rootPane)
