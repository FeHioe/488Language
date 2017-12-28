#! /bin/sh
#  Location of directory containing  dist/compiler488.jar
WHERE=.
#  Compiler reads each file specified on the command line individually and parses it.
#  Output to standard output
for file in $@; do
    java -jar $WHERE/dist/compiler488.jar $file
done
exit 0
