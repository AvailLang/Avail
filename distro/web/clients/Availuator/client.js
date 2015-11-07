/*
 * client.js
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

/**
 * @author Todd L Smith <todd@availlang.org>
 */

var targetModule = '/avail/Availuator';
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
					presentStdout(msg);
				};
				io.stderr = function (msg)
				{
					presentCompilerError(msg);
				};
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
	$(".title").remove();
	$(".source").remove();
	$('.result').remove();
	$('.stdout').remove();
	$('.compiler').remove();
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
	title.innerHTML = 'Evaluate Avail';
	div0.appendChild(title);

	var div1 = document.createElement('div');
	var input = document.createElement('textarea');
	div1.title = '● Press Shift+Enter to submit Avail for evaluation\n\n'
		+ '● Press F1/F2︎ to navigate previously submitted expressions';
	input.id = 'expression';
	input.rows = 10;
	input.placeholder = 'Evaluate me!';
	div1.appendChild(input);

	var div2 = document.createElement('div');
	div2.className = 'result';
	var output = document.createElement('p');
	output.id = 'output';
	output.innerHTML = '&nbsp;';
	div2.appendChild(output);

	//Set up unicode palette
	var divTop = document.createElement('div');
	main.append(divTop);
	
	var divClose = document.createElement ('div');
	divClose.id = 'closediv';

	var divOpen = document.createElement('div');
	divOpen.id='opendiv';
	
	var divPalette = document.createElement('div');
	divPalette.id = 'palette';
	
	var populateFromPallete = function(content) {
		var cursor = expression.get(0).selectionStart;
		expression.val(expression.val().slice(0,cursor)
			+ this.innerHTML 
			+ expression.val().slice(cursor));
		expression.get(0).selectionStart = cursor + 1;
	}
	
	var unicodeSize = unicode.length;
	var i = 0;
	for (i = 0; i < unicodeSize; i++)
	{
		var unicodeChar = unicode[i][3];
		var uniButton = document.createElement('button');
		uniButton.className = 'unicodebutton';
		uniButton.innerHTML = unicodeChar;
		uniButton.addEventListener("click", populateFromPallete);
		divPalette.appendChild(uniButton);
	}
	
	var openButton = document.createElement('button');
	openButton.innerHTML = "Open Unicode Palette";
	openButton.addEventListener("click", function() {
		divOpen.parentNode.removeChild(divOpen);
		divTop.appendChild(divClose);
	});
	
	var closeButton = document.createElement('button');
	closeButton.innerHTML = "Close Unicode Palette";
	closeButton.addEventListener("click", function() {
		divClose.parentNode.removeChild(divClose);
		divTop.appendChild(divOpen);
	});
	
	divClose.appendChild(closeButton);
	divClose.appendChild(divPalette);
	divOpen.appendChild(openButton);
	divTop.appendChild(divOpen);
	
	main.append(div0);
	main.append(div1);
	main.append(div2);
	
	//Add line numbers to the input area
	$('textarea').numberedtextarea();
	var lineNumbers = $('.numberedtextarea-line-numbers');
	lineNumbers[0].style.fontSize = '10pt';
	lineNumbers[0].style.fontSizeFamily = 'monospace';
	lineNumbers[0].style.color = '#0000FF';
	
	var expression = $("#expression");
	
	var historyStack = [];
	var historyIndex = historyStack.length - 1;
	
	$("#expression-form").submit(function (event)
	{
		return false;
	});
	expression.keydown(function (event)
	{
		// Submit on [Shift + Return].
		if (event.shiftKey && event.keyCode == 13)
		{
			event.preventDefault();
			$(".stdout").remove();
			avail.command(
				'Run [' + input.value + ']',
				function (data)
				{
					presentResult(data.content.result);
				});
			historyStack.push(expression.val());
			historyIndex = historyStack.length - 1;
		} 
		else if (event.keyCode == 112 /*&& event.keyCode == 40*/)
		{
			event.preventDefault();
			if (historyIndex > 0)
			{
				historyIndex--;
				expression.val(historyStack[historyIndex]);
			}
		}
		else if (event.keyCode == 113 /*&& event.keyCode == 38*/)
		{
			if (historyIndex > -1 && historyIndex < historyStack.length - 1)
			{
				historyIndex++;
				expression.val(historyStack[historyIndex]);
			}
		}
		else
		{
			if (event.keyCode == 13)
			{
				event.preventDefault();
				var start = expression.get(0).selectionStart;
				var startLineIndex = beginningOfCurrentLineIndex();
				var allText = expression.val();
				var textSize = allText.length;
				var textToSearch = allText.slice(startLineIndex, start);
				var tabs = "";
				var index = 1;
				var size = textToSearch.length;
				while (index < size && textToSearch.charAt(index) == "\t") 
				{
					tabs = tabs + "\t";
					index++;
				}

				expression.val(allText.slice(0,start) + "\n" + tabs 
					+ allText.slice(start));
				var newTextLength = expression.val().length;
				var shift = newTextLength - textSize;
				expression.get(0).selectionStart = start + shift;
				expression.get(0).selectionEnd = start + shift;
			}
		}
	});
	$('body').keydown(function (event)
	{
		// Quit on [Escape].
		if (event.keyCode === 27)
		{
			avail.close();
		} 
		else 
		{
			if (event.shiftKey && event.keyCode == 9)
			{
				event.preventDefault(); 
				var start = expression.get(0).selectionStart;
				var end = expression.get(0).selectionEnd;
				var allText = expression.val();
				var textSize = allText.length;
				var startLineIndex = beginningOfCurrentLineIndex();
				var startText = expression.val().substring(0, start);
				var selectedText = 
			    	allText.slice(startLineIndex,end);


				if (startLineIndex == 0 &&
					selectedText.charAt(startLineIndex) == "\t")
				{
					selectedText = 
						selectedText.replace(/\t/,"");
				}
				selectedText = selectedText.replace(/\n\t/g,"\n");

			    expression.val(expression.val().substring(0, startLineIndex) 
					+ selectedText 
					+ expression.val().substring(end));

			    var newTextLength = expression.val().length;
			    var shift = 0;
			    if (newTextLength != textSize && startText.slice(-1) != "\n") 
			    {
			    	shift = -1;
			    }

			    resetSelectedText(textSize, newTextLength, start, end, shift);
			}
			else
			{
				if (event.keyCode == 9) 
				{ 
					event.preventDefault();
					var start = expression.get(0).selectionStart;
					var end = expression.get(0).selectionEnd;
					var allText = expression.val();
					
					//var start = expression.get(0).selectionStart;
					
					if (end > start)
					{
						var textSize = allText.length;
						var startLineIndex = beginningOfCurrentLineIndex();
						var selectedText = 
					    	allText.slice(startLineIndex,end);
	
						if (startLineIndex == 0 && 
							selectedText.charAt(startLineIndex) != "\n")
						{
							selectedText = "\t" + selectedText;
						}
						
						if (selectedText.length > 1 
							&& selectedText.slice(-1) == "\n")
						{
							selectedText = 
								selectedText.slice(0, selectedText.length - 1);
							selectedText = selectedText.replace(/\n/g,"\n\t");
							selectedText = selectedText + "\n";
						}
						else
						{
							selectedText = selectedText.replace(/\n/g,"\n\t");
						}
	
						expression.val(allText.substring(0, startLineIndex)
							+ selectedText
							+ expression.val().substring(end));
	
						var newTextLength = expression.val().length;
						resetSelectedText(textSize, newTextLength, start, 
							end, 1);
					} 
					else
					{
						expression.val(allText.substring(0, start)
							+ "\t"
							+ expression.val().substring(start));
						
						 expression.get(0).selectionStart = start + 1;
						 expression.get(0).selectionEnd = start + 1;
					}
				}
			}
		}
	});
}

/**
 * Present the result.
 *
 * @param result
 *        The result.
 */
function presentResult (result)
{
	$(".compiler").remove();
	$("#output").html(result);
}

/**
 * Get the text highlighted by the user
 *
 * @returns {string}
 */
function beginningOfCurrentLineIndex() 
{
    var expression = $("#expression");
    var allText = expression.val();
    var start = expression.get(0).selectionStart;
    var i = start;
    while (i > 0 && allText.charAt(i) != "\n") 
    {
    	i--;
    }
    return i;
}

function resetSelectedText(initialSize, newSize, start, end, shift) 
{
	 var carretOffset = initialSize - newSize;
	 var expression = $("#expression");
	 
	 // put caret at right position again
	 expression.get(0).selectionStart = start + shift;
	 expression.get(0).selectionEnd = end - carretOffset;
}

/**
 * Present a message written to standard output.
 *
 * @param result
 *        The result.
 */
function presentStdout (result)
{
	if ($("#stdout").length === 0)
	{
		var div = document.createElement('div');
		div.className = 'stdout';
		var pre = document.createElement('pre');
		pre.id = 'stdout';
		div.appendChild(pre);
		$("#client-ui").append(div);
	}
	$("#stdout").html($("#stdout").html() + result);
}

/**
 * Present a compiler error.
 *
 * @param result
 */
function presentCompilerError (result)
{
	$(".stdout").remove();
	if ($("#compiler").length === 0)
	{
		var div = document.createElement('div');
		div.className = 'compiler';
		var pre = document.createElement('pre');
		pre.id = 'compiler';
		div.appendChild(pre);
		$("#client-ui").append(div);
	}
	$("#compiler").html(result);
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
