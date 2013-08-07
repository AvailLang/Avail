#/bin/bash

HERE=$(pwd)
java -ea -Xdock:name=AvailBuildEnvironment -DavailRoots="avail=$HERE/avail.repo,$HERE/avail" -jar AvailBuildEnvironment.jar
