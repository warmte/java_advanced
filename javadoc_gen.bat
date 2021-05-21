set SRC_PATH=../../java-advanced-2021/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor
set IMPL_PATH=info/kgeorgiy/ja/bozhe/implementor

javadoc -link https://docs.oracle.com/en/java/javase/11/docs/api/ ^
	-private ^
	-d javadocs ^
    %IMPL_PATH%/Implementor.java ^
	%SRC_PATH%/ImplerException.java ^
	%SRC_PATH%/JarImpler.java ^
	%SRC_PATH%/Impler.java