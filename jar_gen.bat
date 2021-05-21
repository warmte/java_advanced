set MODULE=info.kgeorgiy.ja.bozhe.implementor
set DIR=info\kgeorgiy\ja\bozhe\implementor

mkdir %MODULE%
copy module-info.java %MODULE%
copy MANIFEST.MF %MODULE%
mkdir %MODULE%\%DIR%
copy %DIR%\*.java %MODULE%\%DIR%

javac ^
	-d .\javac_files ^
	-p ..\..\java-advanced-2021\artifacts\;..\..\java-advanced-2021\lib ^
	--module-source-path . ^
	--module info.kgeorgiy.ja.bozhe.implementor

jar -c -f Implementor.jar -m %MODULE%\MANIFEST.MF -C javac_files/%MODULE% .
del /s/q %MODULE%
rmdir /s/q %MODULE%
del /s/q javac_files
rmdir /s/q javac_files