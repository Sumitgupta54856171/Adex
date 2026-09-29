#!/bin/bash
unzip -q ~/.gradle/caches/transforms-3/*/transformed/api-13.1.5/jars/classes.jar -d temp_classes || true
find temp_classes -name "Shizuku.class" -exec javap -public {} \;
rm -rf temp_classes
