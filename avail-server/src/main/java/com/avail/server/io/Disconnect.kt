/*
 * Disconnect.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.server.io

/**
 * A `DisconnectOrigin` is an enum that specifies whether it was the client or
 * the server to disconnect the [AvailServerChannel].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
enum class DisconnectOrigin
{
	/** The client disconnected the connection */
	CLIENT_ORIGIN,

	/** The server disconnected the connection. */
	SERVER_ORIGIN
}

/**
 * The `DisconnectReason` is an interface that defines the behavior for
 * providing a reason for a disconnected [AvailServerChannel].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
interface DisconnectReason
{
	/**
	 * The [DisconnectReason] that describes why the channel was closed.
	 */
	val origin: DisconnectOrigin

	/**
	 * A code that represents the [DisconnectReason] in a compact form.
	 */
	val code: Int
}

/**
 * A `ClientDisconnect` is a [DisconnectReason] that specifies the disconnect
 * originated from the client.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object ClientDisconnect: DisconnectReason
{
	override val origin get () = DisconnectOrigin.CLIENT_ORIGIN
	override val code get () = -1
}