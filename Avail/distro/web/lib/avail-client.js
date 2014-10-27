/*
 * avail-client.avail
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

/**
 * @author Todd L Smith <todd@availlang.org>
 */

// Protocol versions.
var protocolVersion = 4;
var supportedProtocolVersions = [protocolVersion];

/**
 * Negotiate a protocol version with the server.
 *
 * @param content
 *        The server's response to our 'version' command.
 * @param ok
 *        What to do if negotiation is successful.
 * @param bad
 *        What to do if negotiation is unsuccessful.
 */
function negotiateVersions (content, ok, bad)
{
	if (content !== protocolVersion)
	{
		var serverVersions = content.supported;
		var compatible = serverVersions.filter(
			function (v)
			{
				return supportedProtocolVersions.indexOf(
					v) != -1;
			});
		if (compatible.length === 0)
		{
			bad();
			return;
		}
	}
	ok();
}

/**
 * Obtain a new connection upgraded for general text I/O.
 *
 * @param uuid
 *        The UUID of the outstanding upgrade request.
 * @param onUpgraded
 *        What to do when the upgrade is complete. Applied with the
 *        upgraded channel.
 * @param onMessage
 *        What to do when a message arrives on the upgraded channel. Applied
 *        with 1) the channel and 2) the object parsed from the JSON response.
 * @param onError
 *        What to do when an error occurs on the channel. Applied with 1) the
 *        upgraded channel and 2) the error message.
 * @param onClose
 *        What to do when the upgraded connection closes. Applied with the
 *        upgraded channel.
 * @returns The new connection.
 */
function ioConnection (uri, uuid, onUpgraded, onMessage, onError, onClose)
{
	var versionNegotiated = false;
	var channel = new WebSocket(uri);
	channel.onopen = function (e)
	{
		channel.send('version ' + protocolVersion);
	};
	channel.onclose = function (e)
	{
		onClose(channel);
	};
	channel.onerror = function (e)
	{
		channel.close();
		onError(channel, e.data);
	};
	channel.onmessage = function (e)
	{
		var response = JSON.parse(e.data);
		if (!versionNegotiated)
		{
			negotiateVersions(
				response.content,
				function ()
				{
					channel.send('upgrade ' + uuid);
					onUpgraded(channel);
				},
				function ()
				{
					channel.close();
					onError(channel, 'incompatible versions');
				});
			versionNegotiated = true;
		}
		else
		{
			onMessage(channel, response);
		}
	};
	return channel;
}
