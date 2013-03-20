#/bin/bash

HERE=$(pwd)
java -ea -Xdock:name=AvailBuildEnvironment -DavailRoots="avail=$HERE/avail.repo,$HERE/new-avail" -jar AvailBuildEnvironment.jar
