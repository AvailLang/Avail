/*
 * KeyTypedAdapter.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.anvil.text

import avail.anvil.shortcuts.Key
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

/**
 * An abstract wrapper for a [Key] and a lambda that takes a [KeyEvent] that
 * performs some action when the [Key] is [typed][KeyAdapter.keyTyped].
 *
 * @author Richard Arriaga
 *
 * @property key
 *   The [Key] that, when matched to a [KeyEvent], should have an action run.
 */
internal abstract class KeyTypedOverride constructor (val key: Key)

/**
 * A [KeyAdapter] that specifically overrides [KeyAdapter.keyTyped] and contains
 * a set of [KeyTypedOverride]s that will be run if the [KeyEvent] passed to
 * [KeyAdapter.keyTyped] matches the [Key].
 *
 * ***Note:*** *Multiple [KeyTypedOverride]s with the same [Key] are permitted*
 * *to be added to [keyTypedOverrides]. This allows for multiple actions to be*
 * *performed on the same [Key] press.*
 *
 * @author Richard Arriaga
 *
 * @param Type
 *   The [KeyTypedOverride] this [KeyTypedAdapter] expects in its
 *   [keyTypedOverrides].
 *
 * @constructor
 * Construct a [KeyTypedAdapter].
 *
 * @param keyTypedOverrides
 *   The [KeyTypedOverride]s of type [Type] to add to this [KeyTypedAdapter].
 */
internal abstract class KeyTypedAdapter<Type: KeyTypedOverride> constructor(
	vararg keyTypedOverrides: Type
): KeyAdapter()
{
	/**
	 * The set of [KeyTypedOverride]s that this [KeyTypedAdapter]
	 */
	protected val keyTypedOverrides = keyTypedOverrides.toSet()
}
