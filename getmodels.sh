#!/bin/bash

MODELDIR=./src/main/resources/models/

URLS=(
    'http://opennlp.sourceforge.net/models-1.5/en-token.bin'
    'http://opennlp.sourceforge.net/models-1.5/en-sent.bin'
    'http://jmlr.csail.mit.edu/papers/volume5/lewis04a/a11-smart-stop-list/english.stop'
)

for URL in ${URLS[@]}
do
    wget $URL --directory-prefix=$MODELDIR
done

