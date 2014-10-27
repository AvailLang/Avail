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
