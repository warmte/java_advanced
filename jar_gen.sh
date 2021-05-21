#!/bin/bash

MODULE=info.kgeorgiy.ja.bozhe.implementor
DIR=info/kgeorgiy/ja/bozhe/implementor

mkdir ${MODULE}
cp module-info.java ${MODULE}
cp MANIFEST.MF ${MODULE}
mkdir --parents ${MODULE}/${DIR}
cp ${DIR}/*.java ${MODULE}/${DIR}

javac \
	-d ./javac_files \
	-p ../../java-advanced-2021/artifacts/:../../java-advanced-2021/lib \
	--module-source-path . \
	--module ${MODULE}

jar -c -f Implementor.jar -m ${MODULE}/MANIFEST.MF -C javac_files/${MODULE} .
rm -rf ${MODULE}
rm -rf javac_files
