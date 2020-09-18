/*
 * client.js
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

/**
 * @author Todd L Smith <todd@availlang.org>
 */

var targetModule = '/examples/Sudoku';
var updateFrequency = 20;
var timeout = 10000;
var avail = null;

/**
 * Connect a command channel to the Avail server. Assuming that the connection
 * completes successfully, load the target module.
 */
function connect ()
{
	var totalBytes = 0;
	var errorReported = false;

	// Configure the server connection.
	var hostName = location.hostname == '' ? 'localhost' : location.hostname;
	avail = new Avail(hostName, 40000);
	avail.connectFailed = function (event)
	{
		reportError(
			'Unable to connect to Avail server at ' + this.url + '.',
			'Make sure that the Avail server is running, and that '
			+ 'your browser correctly supports WebSocket.');

	};
	avail.closed = function (event)
	{
		if (!errorReported)
		{
			reportSessionClosed();
		}
	};
	avail.failed = function (event)
	{
		errorReported = true;
		reportError(event.data);
	};
	avail.ready = function ()
	{
		this.loadModule(targetModule);
	};
	avail.loadModuleStarted = function (data)
	{
		activateProgressBar();
	};
	avail.loadModuleUpdated = function (data)
	{
		data.content.global.forEach(function (update)
		{
			if (totalBytes === 0)
			{
				totalBytes = update.totalBytes;
			}
			var fraction = update.bytesSoFar / totalBytes;
			$('#progress-bar').progressbar('value', fraction * 100);
		});
	};
	avail.loadModuleEnded = function (data)
	{
		deactivateProgressBar();
		presentUI();
	};
	avail.upgrade = function (io, data)
	{
		switch (data.command)
		{
			case 'load module':
			{
				io.failed = function (event)
				{
					avail.close(1000, event.reason);
				};
				io.stderr = function (msg)
				{
					errorReported = true;
					this.close(1000, 'compilation error');
					reportLoadError(msg);
				};
				return;
			}
			case 'run entry point':
			{
				io.stdout = function (msg)
				{
					updateBoard(JSON.parse(msg));
				};
				io.stderr = function (msg)
				{
					reportNoSolution(msg);
				};
				return;
			}
		}
	};

	// Connect!
	avail.connect();
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
	$('.title').remove();
	$('.sudoku').remove();
	$('.no-solution').remove();
}

/**
 * Answer the class name for the cell at the specified coordinates.
 *
 * @param {integer} x
 * @param {integer} y
 * @returns {string}
 */
function classNameForCellAt (x, y)
{
	var className;
	switch (y)
	{
		case 9:
		{
			className = 'cell-' + (((x-1) % 3 === 0) ? 'bl' : 'bm');
			break;
		}
		default:
		{
			switch ((y-1) % 3)
			{
				case 0:
				{
					className = 'cell-' + (((x-1) % 3 === 0) ? 'tl' : 'tm');
					break;
				}
				default:
				{
					className = 'cell-' + (((x-1) % 3 === 0) ? 'ml' : 'mm');
					break;
				}
			}
		}
	}
	return className;
}

/**
 * Present the user interface.
 */
function presentUI ()
{
	var main = $("#client-ui");

	var div0 = document.createElement('div');
	div0.className = 'title';
	var title = document.createElement('p');
	title.innerHTML = 'Solve Sudoku';
	div0.appendChild(title);

	var div1 = document.createElement('div');
	div1.className = 'sudoku';
	div1.title = 'Press Enter to solve, Shift+Backspace to reset';
	var form = document.createElement('form');
	form.id = 'sudoku-form';
	var table = document.createElement('table');
	table.cellSpacing = 0;
	table.cellPadding = 0;
	var tbody = document.createElement('tbody');
	var i, j, cellNumber = 1;
	for (j = 1; j <= 9; j++)
	{
		var tr = document.createElement('tr');
		for (i = 1; i <= 9; i++)
		{
			var td = document.createElement('td');
			var input = document.createElement('input');
			input.id = 'cell' + cellNumber;
			input.className = classNameForCellAt(i, j);
			input.size = 2;
			input.maxLength = 1;
			input.autocomplete = 'off';
			input.onkeypress = validateCell;
			td.appendChild(input);
			tr.appendChild(td);
			cellNumber++;
		}
		tbody.appendChild(tr);
	}
	table.appendChild(tbody);
	form.appendChild(table);
	div1.appendChild(form);

	main.append(div0);
	main.append(div1);

	$('#sudoku-form').submit(function (event)
	{
		return false;
	});
	$('body').keydown(function (event)
	{
		var i;
		// Quit on [Escape].
		if (event.keyCode === 27)
		{
			avail.close();
		}
		// Submit on [Return].
		else if (event.keyCode === 13)
		{
			event.preventDefault();
			$('.no-solution').remove();
			var command = 'solve <';
			for (i = 1; i <= 81; i++)
			{
				if (i != 1)
				{
					command += ',';
				}
				var v = $('#cell' + i).val();
				command += v === '' ? '0' : v;
			}
			command +=
				'> for web, updating every '
				+ updateFrequency
				+ ' ms, giving up after '
				+ timeout
				+ ' ms';
			avail.command(
				command,
				function (data)
				{
					updateBoard(JSON.parse(JSON.parse(data.content.result)));
				});
		}
		// Clear board on [Shift+Backspace] or [Shift+Delete].
		else if (event.shiftKey && (event.keyCode === 8||event.keyCode === 46))
		{
			event.preventDefault();
			$('.no-solution').remove();
			for (i = 1; i <= 81; i++)
			{
				$('#cell' + i).val('');
			}
		}
	});
}

/**
 * Validate the specified keypress event.
 *
 * @param event
 */
function validateCell (event)
{
	var s = String.fromCharCode(event.keyCode);
	if (!/[1-9]/.test(s))
	{
		event.preventDefault();
	}
}

/**
 * Update the board.
 *
 * @param board
 *        The board.
 */
function updateBoard (board)
{
	var i;
	for (i = 1; i <= 81; i++)
	{
		var v = board[i - 1];
		$('#cell' + i).val(v === 0 ? '' : v);
	}
}

/**
 * Report that there is no solution to the current puzzle.
 *
 * @param {string} msg -
 *        The error message.
 */
function reportNoSolution (msg)
{
	var main = $("#client-ui");
	var div = document.createElement('div');
	div.className = 'no-solution';
	var p = document.createElement('p');
	if (/no-solution exception/.test(msg))
	{
		p.innerHTML = 'No Solution';
	}
	else if (/too-long-to-solve exception/.test(msg))
	{
		p.innerHTML = 'Timed Out';
	}
	else
	{
		p.innerHTML = 'Unexpected Problem';
		console.log(msg);
	}
	div.appendChild(p);
	main.append(div);
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
