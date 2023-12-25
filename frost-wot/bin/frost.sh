#!/bin/bash

# figure out the full path to where the frost.sh script is stored
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

# change to the frost.jar directory, in case the user executed frost.sh from somewhere else
# this ensures that all relative paths in Frost work (such as the "downloads/" directory)
cd "${DIR}"

# you may need to uncomment this if you are using the old "Beryl" window manager for Linux
#export AWT_TOOLKIT="MToolkit"

# add some special Apple flags if this is being launched on OS X
if [ "$(uname)" = "Darwin" ]; then
    ADDFLAGS='-Dapple.laf.useScreenMenuBar=true -Xdock:name=Frost'
else
    ADDFLAGS=''
fi

java -Xmx384M $ADDFLAGS -jar frost.jar "$@"
