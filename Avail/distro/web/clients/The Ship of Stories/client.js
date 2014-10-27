/*
 * client.js
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

// These can be changed to reuse this client for another choosable path
// story.
var targetModule = '/examples/The Ship of Stories';
var storyCommand = 'Play the Ship of Stories by web';

/**
 * Connect a command channel to the Avail server. Assuming that the connection
 * completes successfully, load the target module and then run the story
 * command.
 */
function connect ()
{
	var availURI = "ws://localhost:40000/avail";

	// Open the command channel. This is needed only to run the entry point.
	var cmdChannel = new WebSocket(availURI);
	var totalBytes = 0;
	var connected = false;
	var errorReported = false;
	var copyright = null;
	cmdChannel.onopen = function (e)
	{
		connected = true;
		cmdChannel.send('version ' + protocolVersion);
	};
	cmdChannel.onclose = function (e)
	{
		if (connected)
		{
			if (!errorReported)
			{
				reportSessionClosed();
			}
		}
		else
		{
			errorReported = true;
			reportError(
				'Unable to connect to Avail server at ' + availURI + '.',
				'Make sure that the Avail server is running, and that '
				+ 'your browser correctly supports WebSocket.');
		}
	};
	cmdChannel.onerror = function (e)
	{
		if (connected)
		{
			errorReported = true;
			cmdChannel.close();
			reportError(e.data);
		}
	};
	cmdChannel.onmessage = function (e)
	{
		var response = JSON.parse(e.data);
		if ('command' in response)
		{
			switch (response.command)
			{
				// Process a response from a 'version' command.
				case 'version':
				{
					negotiateVersions(
						response.content,
						function ()
						{
							// Success! Now load the target module.
							cmdChannel.send('load module ' + targetModule);
						},
						function ()
						{
							// Failure.
							errorReported = true;
							cmdChannel.close();
							reportError('Version negotiation failed.');
						});
					break;
				}
				// Process a response from a 'load module' command.
				case 'load module':
				{
					// Process an upgrade request.
					if ('upgrade' in response)
					{
						ioConnection(
							availURI,
							response.upgrade,
							function (channel)
							{
								// Don't do anything special when the
								// upgrade completes.
							},
							function (channel, response)
							{
								// Nothing is expected on the error stream, so
								// close the channels and report the error if
								// it does.
								if (response.tag === 'err')
								{
									errorReported = true;
									channel.close();
									cmdChannel.close();
									reportLoadError(response.content);
								}
								else
								{
									// Ignore standard output. This will only
									// be debug information, if anything.
								}
							},
							function (channel, msg)
							{
								errorReported = true;
								cmdChannel.close();
								reportError(msg);
							},
							function (channel)
							{
								// Ignore closes.
							})
						return;
					}
					var content = response.content;
					switch (typeof content)
					{
						case 'object':
						{
							if ('global' in content)
							{
								content.global.forEach(function (update)
								{
									if (totalBytes === 0)
									{
										totalBytes = update.totalBytes;
									}
									var fraction =
										update.bytesSoFar / totalBytes;
									$('#progress-bar').progressbar(
										'value', fraction * 100);
								});
							}
							break;
						}
						case 'string':
						{
							if (content === 'begin')
							{
								activateProgressBar();
							}
							else if (content === 'end')
							{
								deactivateProgressBar();
								cmdChannel.send('run ' + storyCommand);
							}
							break;
						}
					}
					break;
				}
				// Process a response from a 'run entry point' command.
				case 'run entry point':
				{
					// Process an upgrade request.
					if ('upgrade' in response)
					{
						ioConnection(
							availURI,
							response.upgrade,
							function (channel)
							{
								// We are ready to start processing key events.
								$('body').keydown(function (ev)
								{
									handleGameKeydown(ev, channel);
								});
							},
							function (channel, response)
							{
								// Treat the error stream as a source of debug
								// information. Log anything that arrives thence.
								if (response.tag === 'err')
								{
									console.log(response.content);
								}
								else
								{
									// The tag should be 'out'. The content
									// should be a JSON encoded array. The
									// first message will be the copyright
									// notice. Story data will follow.
									var content = JSON.parse(response.content);
									if (copyright === null)
									{
										copyright = content;
									}
									else
									{
										updateUI(
											content,
											copyright,
											channel);
									}
								}
							},
							function (channel, msg)
							{
								errorReported = true;
								cmdChannel.close();
								reportError(msg);
							},
							function (channel)
							{
								cmdChannel.close();
							})
						return;
					}
					break;
				}
			}
		}
	};
}

/**
 * Activate the progress bar.
 */
function activateProgressBar ()
{
	var div = document.createElement('div');
	div.id = 'progress-bar';
	$('#client-ui').append(div);
	$('#progress-bar').progressbar({max: 100, value: 0});
}

/**
 * Deactivate the progress bar.
 */
function deactivateProgressBar ()
{
	$('#progress-bar').remove();
}

/**
 * Clear the user interface of game components.
 */
function clearUI ()
{
	$(".scene-title").remove();
	$(".scene-description").remove();
	$(".scene-transition").remove();
	$(".game-over").remove();
	$('.copyright').remove();
}

/**
 * Update the user interface based on the supplied scene content.
 *
 * @param array
 *        The scene content:
 *        - [0]   The scene title.
 *        - [1]   The scene description.
 *        - [2..] The transitions.
 * @param copyright
 *        The copyright information, as an array of lines.
 * @param channel
 *        The I/O channel.
 */
function updateUI (array, copyright, channel)
{
	var title = array[0];
	var description = array[1];
	var transitions = array.slice(2);
	clearUI();
	// Animation parameters.
	var easing = 'blind';
	var options = {};
	var duration = 600;
	// Add the scene title.
	var main = $("#client-ui");
	var div = document.createElement('div');
	div.className = 'scene-title';
	var p = document.createElement('p');
	p.innerHTML = title;
	div.appendChild(p);
	main.append(div);
	// Add the scene description.
	div = document.createElement('div');
	div.className = 'scene-description';
	p = document.createElement('p');
	p.innerHTML = description;
	div.appendChild(p);
	main.append(div);
	$('.scene-description').show(easing, options, duration);
	// If there are no transitions, then the game is
	// over.
	if (transitions.length === 0)
	{
		div = document.createElement('div');
		div.className = 'game-over';
		p = document.createElement('p');
		p.innerHTML =
			'Game over! Press [Space] to restart, or [Escape] to quit.';
		div.appendChild(p);
		main.append(div);
		$('.game-over').show(easing, options, duration);
	}
	// Otherwise, add the transitions.
	else
	{
		for (var i = 0; i < transitions.length; i++)
		{
			var transition = transitions[i];
			var id = 'transition-' + i;
			div = document.createElement('div');
			div.id = id;
			div.className = 'scene-transition';
			p = document.createElement('p');
			p.innerHTML = transition;
			div.appendChild(p);
			main.append(div);
			(function (i)
			{
				$('#transition-' + i).click(function (ev)
				{
					var c = (i + 1).toString();
					channel.send(c + '\n');
				});
			})(i);
		}
		$('.scene-transition').show(easing, options, duration);
	}
	// Add the copyright notice.
	div = document.createElement('div');
	div.className = 'copyright';
	p = document.createElement('pre');
	copyright.forEach(function (line)
	{
		p.innerHTML = p.innerHTML + line + '\n';
	});
	div.appendChild(p);
	main.append(div);
}

/**
 * React to an incoming keydown.
 *
 * @param ev
 *        The keydown event.
 * @param channel
 *        The I/O channel.
 */
function handleGameKeydown (ev, channel)
{
	// Quit on [Escape].
	if (ev.keyCode === 27)
	{
		// If shift is pressed also, then close the client connection.
		if (ev.shiftKey)
		{
			channel.close();
		}
		else
		{
			channel.send('quit\n');
		}
	}
	else
	{
		var ch = String.fromCharCode(ev.keyCode);
		if (ch === ' ')
		{
			channel.send('restart\n');
		}
		else if (!isNaN(parseInt(ch)))
		{
			channel.send(ch + '\n');
		}
	}
	ev.preventDefault();
}

/**
 * Report an error.
 *
 * @param arguments
 *        An array of lines, divided along paragraph boundaries, comprising the
 *        error message.
 */
function reportError ()
{
	clearUI();
	var div = document.createElement('div');
	div.id = 'error';
	var args = Array.prototype.slice.call(arguments);
	args.forEach(function (line)
	{
		var p = document.createElement('p');
		p.innerHTML = line;
		div.appendChild(p);
	});
	$('#client-ui').append(div);
}

/**
 * Report a load error.
 *
 * @param msg
 *        The error message.
 */
function reportLoadError (msg)
{
	clearUI();
	var div = document.createElement('div');
	div.id = 'error';
	var pre = document.createElement('pre');
	pre.innerHTML = msg;
	div.appendChild(pre);
	$('#client-ui').append(div);
}

/**
 * Report that the session has closed.
 */
function reportSessionClosed ()
{
	clearUI();
	var div = document.createElement('div');
	div.id = 'session-closed';
	var p = document.createElement('p');
	p.innerHTML = 'Session closed.';
	div.appendChild(p);
	$('#client-ui').append(div);
}
