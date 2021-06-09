#!/bin/sh

# docs.sh
# Copyright © 1993-2021, The Avail Foundation, LLC.
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
## Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
#
## Redistributions in binary form must reproduce the above copyright notice,
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
#
## Neither the name of the copyright holder nor the names of the contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.

# This script is used for tasks associated with generating documentation and
# making it available via a web browser.
#
# @author Rich Arriaga <rich@availlang.org>

# Options:
#  `--help`     : provides help documentation
# Commands:
#  `install`  : install system requirements

#Options
HELP="--help"

# Commands
INSTALL="install"

# Print script help documentation
print_help() {
    echo ""
    echo "Usage: docs [OPTION] / [COMMAND]"
    echo "       accepts either a single OPTION or single COMMAND."
    echo ""
    echo "This utility is used to manage the documentation for this site."
    echo ""
    echo ""
    echo "Options:"
    echo "  $HELP      : provides help documentation"
    echo ""
    echo "Commands:"
	echo "  $INSTALL  : installs all necessary packages (mkdocs, mkdocs-material"
    echo ""
}

install_packages() {
	echo "Installing mkdocs, mkdocs-material..."
	command brew install mkdocs
	pip3 install mkdocs-material
}

# Execute command based on command line argument provided
if [ $# -eq 0 ]
  then
    print_help
elif [ "$1" == $INSTALL ]
    then
        install_packages
else
    print_help
fi
