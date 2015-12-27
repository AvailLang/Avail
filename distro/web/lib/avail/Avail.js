/*
 * Avail.js
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

// The global channel id source, a monotonically increasing integer.
var ___avail_channel_id = 1;

/**
 * @author Todd L Smith <todd@availlang.org>
 */
AvailState =
{
	virgin: 'virgin',
	open: 'open',
	error: 'error',
	closed: 'closed'
};

/**
 * @param {string} hostParam -
 *        The host on which the Avail server resides. Defaults to
 *        <strong>localhost</strong>.
 * @param {number} portParam -
 *        The port on which the Avail server is listening. Defaults to
 *        <strong>40000</strong>.
 * @author Todd L Smith <todd@availlang.org>
 */
function Avail (hostParam, portParam)
{
	Object.defineProperty(this, 'id',
	{
		value: ___avail_channel_id++,
		configurable: false,
		writable: false,
		enumerable: false
	});

	var host = hostParam || 'localhost';
	Object.defineProperty(this, 'hostName',
	{
		value: host,
		configurable: false,
		writable: false,
		enumerable: true
	});

	var port = portParam || 40000;
	Object.defineProperty(this, 'port',
	{
		value: port,
		configurable: false,
		writable: false,
		enumerable: true
	});

	Object.defineProperty(this, 'url',
	{
		get: function ()
		{
			return 'ws://' + host + ':' + port + '/avail';
		}
	});

	Object.defineProperty(this, 'protocolVersion',
	{
		get: function ()
		{
			return 4;
		}
	});

	Object.defineProperty(this, 'supportedProtocolVersions',
	{
		get: function ()
		{
			return [this.protocolVersion];
		}
	});

	Object.defineProperty(this, 'ws',
	{
		value: null,
		configurable: false,
		writable: true,
		enumerable: false
	});

	Object.defineProperty(this, 'state',
	{
		value: AvailState.virgin,
		configurable: false,
		writable: true,
		enumerable: false
	});

	Object.defineProperty(this, 'commandId',
	{
		value: 0,
		configurable: true,
		writable: true,
		enumerable: false
	});

	Object.defineProperty(this, 'callbacks',
	{
		value: {},
		configurable: true,
		writable: true,
		enumerable: false
	});

	Object.defineProperty(this, 'wsDebug',
	{
		value: false,
		configurable: false,
		writable: true,
		enumerable: false
	});

	Object.defineProperty(this, 'availDebug',
	{
		value: false,
		configurable: false,
		writable: true,
		enumerable: false
	});
}

/**
 * @param {Avail} parent -
 *        The Avail connection that owns this subordinate I/O connection.
 * @param {string} uuid -
 *        The UUID that transiently identifies this I/O connection to the Avail
 *        server.
 * @author Todd L Smith <todd@availlang.org>
 */
function AvailIO (parent, uuid)
{
	Avail.call(this, parent.host, parent.port);

	// Remove unneeded properties.
	delete this.commandId;
	delete this.callbacks;

	Object.defineProperty(this, 'parent',
	{
		value: parent,
		configurable: false,
		writable: false,
		enumerable: false
	});

	Object.defineProperty(this, 'uuid',
	{
		value: uuid,
		configurable: false,
		writable: false,
		enumerable: false
	});
}

AvailIO.prototype = Object.create(Avail.prototype);

/**
 * Log a WebSocket debug message.
 */
Avail.prototype.wsLog = function ()
{
	if (this.wsDebug)
	{
		console.log(
			'[WS#' + this.id + '] '
			+ Array.prototype.slice.call(arguments).join(''));
	}
}

/**
 * Log an Avail debug message.
 */
Avail.prototype.availLog = function ()
{
	if (this.availDebug)
	{
		console.log(
			'[AV#' + this.id + '] '
			+ Array.prototype.slice.call(arguments).join(''));
	}
}

/**
 * Establish a WebSocket connection to the Avail server.
 */
Avail.prototype.connect = function ()
{
	this.wsLog('Connecting to ', this.url, '...');
	this.ws = new WebSocket(this.url);
	this.ws.onopen = (function (self)
	{
		return function (event)
		{
			self.state = AvailState.open;
			// Begin version negotiation with the server.
			self.version();
			self.wsLog(
				'Connected to ',
				self.url,
				':',
				JSON.stringify(event));
			self.wsLog(
				'Sending protocol version = ',
				self.protocolVersion);
			self.opened(event);
		};
	})(this);
	this.ws.onclose = (function (self)
	{
		return function (event)
		{
			if (self.state === AvailState.virgin)
			{
				self.state = AvailState.closed;
				self.wsLog(
					'Unable to connect to ',
					self.url,
					' => ',
					JSON.stringify(event));
				self.connectFailed(event);
				return;
			}
			if (self.state === AvailState.open)
			{
				self.state = AvailState.closed;
				self.wsLog(
					'Disconnected from ',
					self.url,
					' => ',
					JSON.stringify(event));
				self.closed(event);
				return;
			}
			if (self.state === AvailState.error)
			{
				self.state = AvailState.closed;
				self.wsLog(
					'Disconnected from ',
					self.url,
					' => ',
					JSON.stringify(event));
				self.failed(event);
				return;
			}
			self.wsLog(
				'Bad transition from state=',
				self.state,
				' to state=',
				AvailState.closed,
				' => ',
				JSON.stringify(event));
		};
	})(this);
	this.ws.onerror = (function (self)
	{
		return function (event)
		{
			// The state should be 'open' (because the state of the WebSocket
			// should be 'open' or 'closing').
			self.wsLog(
				'Error on ',
				self.url,
				' => ',
				JSON.stringify(event));
			if (self.state !== AvailState.virgin)
			{
				self.state = AvailState.error;
			}
		};
	})(this);
	this.ws.onmessage = (function (self)
	{
		return function (event)
		{
			var data = JSON.parse(event.data);
			self.wsLog(
				'Message from ',
				self.url,
				' => ',
				JSON.stringify(data));
			self.dispatch(data);
		};
	})(this);
};

/**
 * This method is invoked when the WebSocket connection to the Avail server
 * becomes fully established.
 *
 * @param {event} event -
 *        An open event.
 */
Avail.prototype.opened = function (event)
{
	// Do nothing.
};

/**
 * This method is invoked when the WebSocket client determines that it could
 * not connect to the Avail server.
 *
 * @param {CloseEvent} event -
 *        A close event.
 */
Avail.prototype.connectFailed = function (event)
{
	// Do nothing.
};

/**
 * This method is invoked when the WebSocket connection closes.
 *
 * @param {CloseEvent} event -
 *        A close event.
 */
Avail.prototype.closed = function (event)
{
	// Do nothing.
};

/**
 * This method is invoked when the WebSocket connection fails because of an
 * error condition. By default, it calls {@link Avail#closed()}.
 *
 * @param {CloseEvent} event -
 *        A close event.
 */
Avail.prototype.failed = function (event)
{
	this.closed(event);
};

/**
 * Send a raw command to the Avail server. Increment the command id.
 *
 * @param {string} cmd -
 *        The raw command.
 */
Avail.prototype.rawCommand = function (cmd)
{
	this.commandId++;
	this.ws.send(cmd);
};

/**
 * Send the protocol version request to the server.
 */
Avail.prototype.version = function ()
{
	this.rawCommand('version ' + this.protocolVersion);
};

/**
 * Load the specified module.
 *
 * @param {string} moduleName -
 *        The fully-qualified name of the module to load.
 */
Avail.prototype.loadModule = function (moduleName)
{
	this.rawCommand('load module ' + moduleName);
};

/**
 * Unload the specified module.
 *
 * @param {string} moduleName -
 *        The fully-qualified name of the module to unload.
 */
Avail.prototype.unloadModule = function (moduleName)
{
	this.rawCommand('unload module ' + moduleName);
};

/**
 * Unload all loaded modules.
 */
Avail.prototype.unloadAllModules = function ()
{
	this.rawCommand('unload all modules');
};

/**
 * Run the specified entry point command.
 *
 * @param {string} command -
 *        An entry point command.
 */
Avail.prototype.command = function (command, callback)
{
	this.rawCommand('run ' + command);
	this.callbacks[this.commandId] = callback;
};

/**
 * Close the connection to the Avail server.
 *
 * @param {short} code -
 *        A WebSocket status code. Default is 1000 (transaction complete).
 * @param {string} reason -
 *        A human-readable close reason. May be omitted.
 */
Avail.prototype.close = function (code, reason)
{
	this.ws.close(code || 1000, reason);
};

/**
 * Dispatch control based on the results of commands issued to the Avail server.
 *
 * @param {object} data -
 *        The server's response to a previous command.
 */
Avail.prototype.dispatch = function (data)
{
	switch (data.command)
	{
		case 'version':
		{
			if (this.negotiateVersion(data.content))
			{
				this.availLog('Version negotiated successfully');
				this.ready();
			}
			else
			{
				this.error('Version negotiation failed.');
			}
			return;
		}
		case 'load module':
		{
			if ('upgrade' in data)
			{
				this.availLog(
					data.command,
					' upgrade requested (',
					data.upgrade,
					') => [#',
					___avail_channel_id,
					']');
				var io = new AvailIO(this, data.upgrade);
				this.upgrade(io, data);
				io.connect();
			}
			else
			{
				switch (typeof data.content)
				{
					case 'string':
					{
						if (data.content === 'begin')
						{
							this.availLog('load module started');
							this.loadModuleStarted(data);
						}
						else if (data.content === 'end')
						{
							this.availLog('load module ended');
							this.loadModuleEnded(data);
						}
						break;
					}
					case 'object':
					{
						this.availLog('load module updated');
						this.loadModuleUpdated(data);
						break;
					}
					default:
					{
						this.availLog(
							'unexpected load module update: ',
							JSON.stringify(data));
						break;
					}
				}
			}
			return;
		}
		case 'unload module':
		case 'unload all modules':
		{
			if ('upgrade' in data)
			{
				this.availLog(
					data.command,
					' upgrade requested (',
					data.upgrade,
					') => [#',
					___avail_channel_id,
					']');
				var io = new AvailIO(this, data.upgrade);
				this.upgrade(io, data);
				io.connect();
			}
			return;
		}
		case 'run entry point':
		{
			if ('upgrade' in data)
			{
				this.availLog(
					data.command,
					' upgrade requested (',
					data.upgrade,
					') => [#',
					___avail_channel_id,
					']');
				var io = new AvailIO(this, data.upgrade);
				this.upgrade(io, data);
				io.connect();
			}
			else
			{
				this.callbacks[data.id](data);
				delete this.callbacks[data.id];
			}
			return;
		}
		default:
		{
			this.availLog(
				'unexpected command: ',
				JSON.stringify(data));
			return;
		}
	}
};

/**
 * Negotiate a protocol version with the Avail server.
 *
 * @returns {boolean}
 *          true if version negotiation succeeded, false otherwise.
 */
Avail.prototype.negotiateVersion = function (content)
{
	// If the server accepted the given protocol version, then content is simply
	// an echo of this information.
	if (content !== this.protocolVersion)
	{
		// Otherwise, the 'supported' array should contain the protocol versions
		// that the server will accept.
		var serverVersions = content.supported;
		var compatible = serverVersions.filter(
			function (v)
			{
				return this.supportedProtocolVersions.indexOf(v) != -1;
			});
		if (compatible.length === 0)
		{
			return false;
		}
	}
	return true;
};

/**
 * This method is invoked when the Avail server is ready to begin receiving
 * commands.
 */
Avail.prototype.ready = function ()
{
	// Do nothing.
};

/**
 * This method is invoked when the Avail server makes an upgrade request. Any
 * behavioral customization for an I/O channel should occur here.
 *
 * @param {AvailIO} io -
 *        The unconnected I/O channel that will be connected and upgraded after
 *        this method returns.
 * @param {object} data -
 *        A message from the Avail server, containing all context for the
 *        upgrade request.
 */
Avail.prototype.upgrade = function (io, data)
{
	// Do nothing.
};

/**
 * This method is invoked when the Avail server acknowledges that the loading of
 * a module has begun.
 *
 * @param {object} data -
 *        The server's response.
 */
Avail.prototype.loadModuleStarted = function (data)
{
	// Do nothing.
};

/**
 * This method is invoked when the Avail server provides a progress update
 * related to module loading.
 *
 * @param {object} data -
 *        The server's response.
 */
Avail.prototype.loadModuleUpdated = function (data)
{
	// Do nothing.
};

/**
 * This method is invoked when the Avail server indicates that the loading of
 * a module has completed.
 *
 * @param {object} data -
 *        The server's response.
 */
Avail.prototype.loadModuleEnded = function (data)
{
	// Do nothing.
};

/**
 * Close the WebSocket connection to the Avail server and issue the specified
 * error message.
 *
 * @param {string} msg -
 *        An explanation of the error.
 */
Avail.prototype.error = function (msg)
{
	this.ws.close();
	this.reportError(msg);
};

/**
 * Report the specified error message. The WebSocket connection has already been
 * closed, and {@link Avail#connectFailed()}, {@link Avail#closed()}, or {@link
 * Avail#failed()} called as appropriate.
 */
Avail.prototype.reportError = function ()
{
	console.log(
		'[ERROR#' + this.id + '] '
		+ Array.prototype.slice.call(arguments).join(''));
};

AvailIO.prototype.rawCommand = function (cmd)
{
	this.ws.send(cmd);
};

AvailIO.prototype.dispatch = function (data)
{
	if (this.negotiateVersion(data.content))
	{
		// Once versions have been negotiated successfully, replace the
		// dispatcher with one that expects only I/O data.
		this.ws.send('upgrade ' + this.uuid);
		this.dispatch = function (ioData)
		{
			switch (ioData.tag)
			{
				case 'err':
				{
					this.stderr(ioData.content);
					return;
				}
				case 'out':
				{
					this.stdout(ioData.content);
					return;
				}
			}
		};
		this.ready();
	}
	else
	{
		this.error('Version negotiation failed.');
	}
};

/**
 * Receive a message through the standard error (stderr) subchannel.
 *
 * @param {string} msg -
 *        The message that has arrived on the standard error subchannel.
 */
AvailIO.prototype.stderr = function (msg)
{
	console.log(
		'<stderr#' + this.id + '> '
		+ JSON.stringify(msg));
};

/**
 * Receive a message through the standard output (stdout) subchannel.
 *
 * @param {string} msg -
 *        The message that has arrived on the standard output subchannel.
 */
AvailIO.prototype.stdout = function (msg)
{
	console.log(
		'<stdout#' + this.id + '> '
		+ JSON.stringify(msg));
};

/**
 * Write the specified message through the standard input (stdin) subchannel.
 *
 * @param {string} msg -
 *        The message that should be delivered on the standard input subchannel.
 */
AvailIO.prototype.stdin = function (msg)
{
	this.ws.send(msg);
};
