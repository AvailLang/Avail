/*
 * Notifications.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.server.messages

import com.avail.builder.ModuleName
import com.avail.builder.ModuleRoot
import com.avail.server.AvailServer
import com.avail.server.io.AvailServerChannel
import com.avail.server.messages.NotificationType.*
import com.avail.utility.json.JSONFriendly
import com.avail.utility.json.JSONWriter

/**
 * A `Notification` is an [AvailServer]-originating message sent to a client
 * to provide it with information.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property notificationType
 *   The [NotificationType] of this [Notification].
 *
 * @constructor
 * Construct a new [Notification].
 *
 * @param notificationType
 *   The [NotificationType] of this [Notification].
 */
abstract class Notification constructor(
	protected val notificationType: NotificationType) : JSONFriendly
{
	override fun writeTo(writer: JSONWriter)
	{
		notificationType.writeTo(writer)
		writer.write("notification")
	}

	/**
	 * Send this [Notification] to the provided [AvailServerChannel].
	 *
	 * @param channel
	 *   The `AvailServerChannel` to notify.
	 */
	fun send(channel: AvailServerChannel)
	{
		channel.enqueueMessageThen(
			Message(
				JSONWriter().apply {
					this@Notification.writeTo(this)
				},
				channel.state)
		) { }
	}
}

/**
 * A `SimpleNotification` is a [Notification] used to send basic notifications
 * with a simple string [notification] message.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property notification
 *   The body of the notification.
 *
 * @constructor
 * Construct a new [SimpleNotification].
 *
 * @param notification
 *   The body of the notification.
 * @param notificationType
 *   The [NotificationType] of this [Notification].
 */
class SimpleNotification constructor(
	private val notification: String,
	notificationType: NotificationType): Notification(notificationType)
{
	override fun writeTo(writer: JSONWriter)
	{
		super.writeTo(writer)
		writer.write("notification")
		writer.write(notification)
	}
}

/**
 * A `RootChange` is a [Notification] indicating that an update has occurred to
 * a [ModuleRoot].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property root
 *   The name of the updated [ModuleRoot].
 * @property rootRelativeName
 *   The [ModuleName.rootRelativeName] of the changed resource.
 *
 * @constructor
 * Construct a new [RootChange].
 *
 * @param root
 *   The name of the updated [ModuleRoot].
 * @param rootRelativeName
 *   The [ModuleName.rootRelativeName] of the changed resource.
 * @param notificationType
 *   The [NotificationType] of this [Notification].
 */
open class RootChange constructor(
	private val root: String,
	private val rootRelativeName: String,
	notificationType: NotificationType): Notification(notificationType)
{
	override fun writeTo(writer: JSONWriter)
	{
		super.writeTo(writer)
		writer.at("notification") {
			writeObject {
				write("root")
				write(root)
				write("rootRelativeName")
				write(rootRelativeName)
			}
		}
	}
}

/**
 * `Create` is a [RootChange] announcing a resource has been created in a
 * [ModuleRoot].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new [Create].
 *
 * @param root
 *   The name of the updated [ModuleRoot].
 * @param rootRelativeName
 *   The [ModuleName.rootRelativeName] of the changed resource.
 */
class Create constructor(root: String, rootRelativeName: String):
	RootChange(root, rootRelativeName, CREATE)

/**
 * `Delete` is a [RootChange] announcing that a resource has been created in a
 * [ModuleRoot].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new [Create].
 *
 * @param root
 *   The name of the updated [ModuleRoot].
 * @param rootRelativeName
 *   The [ModuleName.rootRelativeName] of the changed resource.
 */
class Delete constructor(root: String, rootRelativeName: String):
	RootChange(root, rootRelativeName, DELETE)

/**
 * `Modify` is a [RootChange] announcing that a resource has been modified in a
 * [ModuleRoot].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new [Create].
 *
 * @param root
 *   The name of the updated [ModuleRoot].
 * @param rootRelativeName
 *   The [ModuleName.rootRelativeName] of the changed resource.
 */
class Modify constructor(root: String, rootRelativeName: String):
	RootChange(root, rootRelativeName, MODIFY)
