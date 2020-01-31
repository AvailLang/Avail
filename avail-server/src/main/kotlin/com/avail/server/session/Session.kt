/*
 * Session.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

package com.avail.server.session

import com.avail.server.AvailServer
import com.avail.server.io.AvailServerChannel
import com.avail.server.io.DisconnectReason
import com.avail.server.io.ParentChannelDisconnect
import com.avail.server.io.files.FileManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * `Session` is a context of data for a singular user-controlled instance of
 * an [AvailServer] `connection`. A _connection_ being a single
 * [AvailServerChannel] that is a
 * [command channel][AvailServerChannel.ProtocolState.COMMAND] and all of its
 * spawned [child channels][AvailServerChannel]. A _child channel_ being any
 * `AvailServerChannel` that has been
 * [upgraded][AvailServerChannel.ProtocolState.ELIGIBLE_FOR_UPGRADE] into a
 * [non-command channel][AvailServerChannel.ProtocolState.COMMAND].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property commandChannel
 *   A [AvailServerChannel] that has a [AvailServerChannel.state] set to
 *   [AvailServerChannel.ProtocolState.COMMAND] and is eligible to create child
 *   `AvailServerChannel`s.
 *
 * @constructor
 * Construct a new [Session].
 *
 * @param commandChannel
 *   A [AvailServerChannel] that has a [AvailServerChannel.state] set to
 *   [AvailServerChannel.ProtocolState.COMMAND] and is eligible to create child
 *   `AvailServerChannel`s.
 */
class Session constructor(val commandChannel: AvailServerChannel)
{
	// TODO
	//  - Maintain space for user data, this includes
	//  - Make space for subscriptions for updates/notifications
	//  - User capability matrix by ChannelType and "organization?"

	/**
	 * The [UUID] that uniquely identifies this [Session]. This is also the
	 * [commandChannel]'s [AvailServerChannel.id].
	 */
	val id: UUID = commandChannel.id

	/**
	 * The [Map] from [AvailServerChannel.id] to [AvailServerChannel] for all
	 * the `AvailServerChannel`s created by the [commandChannel].
	 */
	val childChannels = ConcurrentHashMap<UUID, AvailServerChannel>()

	/**
	 * The [DisconnectReason] indicating why this [Session] was [closed][close].
	 */
	var disconnectReason: DisconnectReason? = null

	/**
	 * `true` indicates this [Session] is active; `false` indicates this
	 * `Session` is disconnected or in the process of disconnecting.
	 */
	val isActive = disconnectReason == null

	/**
	 * Add the child [AvailServerChannel] to this [Session] if this `Session`
	 * [is active][isActive]. If the session is inactive,
	 * [close][AvailServerChannel.scheduleClose] the child.
	 *
	 * @param child
	 *   The `AvailServerChannel` to add.
	 */
	@Synchronized
	fun addChildChannel (child: AvailServerChannel)
	{
		if(isActive)
		{
			childChannels[child.id] = child
		}
		else
		{
			child.scheduleClose(ParentChannelDisconnect)
		}
	}

	/**
	 * Remove the child [AvailServerChannel] to this [Session] if this `Session`
	 * [is active][isActive].
	 *
	 * @param child
	 *   The `AvailServerChannel` to add.
	 */
	@Synchronized
	fun removeChildChannel (child: AvailServerChannel)
	{
		if(isActive)
		{
			childChannels.remove(child.id)
		}
	}

	/**
	 * The [FreeList] that contains [FileManager] cache ids for opened files.
	 */
	private val sessionFileCache = FreeList<UUID>()

	/**
	 * The [Map] from [FileManager] cache ids for opened files to its
	 * [sessionFileCache] id.
	 */
	private val fileCacheIdSessionId = mutableMapOf<UUID, Int>()

	/**
	 * Close this [Session] and all of its [childChannels].
	 *
	 * @param reason
	 *   The [DisconnectReason] for the close.
	 */
	@Synchronized
	fun close (reason: DisconnectReason)
	{
		disconnectReason = reason
		childChannels.values.forEach {
			it.scheduleClose(ParentChannelDisconnect)
		}
		commandChannel.server.sessions.remove(id)
		fileCacheIdSessionId.keys.forEach {
			commandChannel.server.fileManager.deregisterInterest(it)
		}
		AvailServer.logger.log(Level.INFO, "Session $id closed")
	}

	/**
	 * Add the [FileManager] cache id to this [Session] and answer a
	 * `Session`-specific cache key for the file.
	 *
	 * If there is already an id for this, the existing id is returned.
	 *
	 * @param uuid
	 *   The `FileManager` cache id to add.
	 * @return
	 *   The session id for the file that can be used to refer to the file.
	 */
	fun addFileCacheId (uuid: UUID): Int =
		fileCacheIdSessionId[uuid]?.let {
			// If we got here, which we shouldn't unless the client "forgot"
			// it had a file open and re-requested it, then this Session would
			// have been double-counted in the ServerFileWrapper's
			// interestCount, which needs to be adjusted.
			commandChannel.server.fileManager.deregisterInterest(uuid)
			it
		} ?: sessionFileCache.add(uuid).apply {
				fileCacheIdSessionId[uuid] = this
			}

	/**
	 * Add the [FileManager] cache id to this [Session] and answer a
	 * `Session`-specific cache key for the file.
	 *
	 * @param id
	 *   The session id for the file to remove.
	 * @return
	 *   The provided `id` if there were something at that location to remove
	 *   or `null` if that location was invalid or empty.
	 */
	fun removeFileCacheId (id: Int) =
		sessionFileCache.remove(id)?.let {
			commandChannel.server.fileManager.deregisterInterest(it)
			fileCacheIdSessionId.remove(it)
		}

	/**
	 * Answer the [FileManager] id linked to the provided [FreeList] index id.
	 *
	 * @param id
	 *   The location in the [sessionFileCache] to retrieve the file [UUID]
	 *   from.
	 * @return
	 *   A `UUID` if one is available or `null` if either id is invalid or the
	 *   id points to a location with no stored [value][Link.value].
	 */
	fun getFile (id: Int): UUID? = sessionFileCache[id]
}