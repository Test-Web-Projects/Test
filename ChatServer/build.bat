@echo off
REM Creare cartella out se non esiste
if not exist out mkdir out

REM Compilare tutte le classi nella cartella src
javac -d out src\*.java
if %ERRORLEVEL% neq 0 (
    echo Errore durante la compilazione
    exit /b 1
)

REM Copiare la cartella web dentro out
xcopy public out\public /E /I /Y

REM Creare il JAR
jar cfm ProgettoChat.jar manifest.txt -C out/ .

echo JAR creato con successo: ProgettoChat.jar
pause
