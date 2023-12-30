@echo off
IF EXIST "target/build/build.gradle" (
    echo Deleting old build.gradle
    del "target/build/build.gradle"
)
IF EXIST "target/build/BuildSystemShort.java" (
    echo Deleting old BuildSystemShort.java
    del "target/build/BuildSystemShort.java"
)
IF EXIST "target/build/Main.java" (
    echo Deleting old Main.java
    del "target/build/Main.java"
)
IF EXIST "target/build/README.md" (
    echo Deleting old README.md
    del "target/build/README.md"
)
echo F|xcopy /Y /F "build.gradle" "target/build/build.gradle"
echo F|xcopy /Y /F "src/BuildSystemShort.java" "target/build/BuildSystemShort.java"
echo F|xcopy /Y /F "src/Main.java" "target/build/Main.java"
echo F|xcopy /Y /F "README.md" "target/build/README.md"
echo Done copying files
cd target/build
"C:\Program Files\7-Zip\7z.exe" a -tzip "../Obfuscator-GradleShort-vLATEST.zip" *
cd ../../
IF EXIST "target/build/build.gradle" (
    echo Deleting old build.gradle
    del "target/build/build.gradle"
)
IF EXIST "target/build/BuildSystemShort.java" (
    echo Deleting old BuildSystemShort.java
    del "target/build/BuildSystemShort.java"
)
IF EXIST "target/build/Main.java" (
    echo Deleting old Main.java
    del "target/build/Main.java"
)
IF EXIST "target/build/README.md" (
    echo Deleting old README.md
    del "target/build/README.md"
)
echo Done zipping files