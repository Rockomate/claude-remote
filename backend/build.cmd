@echo off
REM Claude Remote - Build script for Windows
SET JAVA_HOME=D:\IntelliJ IDEA 2025.1.4.1\jbr
SET MAVEN_OPTS=-Dfile.encoding=UTF-8
"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 -classpath "D:\IntelliJ IDEA 2025.1.4.1\plugins\maven\lib\maven3\boot\plexus-classworlds-*.jar" "-Dclassworlds.conf=D:\IntelliJ IDEA 2025.1.4.1\plugins\maven\lib\maven3\bin\m2.conf" "-Dmaven.home=D:\IntelliJ IDEA 2025.1.4.1\plugins\maven\lib\maven3" "-Dmaven.multiModuleProjectDirectory=%~dp0" org.codehaus.plexus.classworlds.launcher.Launcher %* -s "%~dp0\.mvn\settings-custom.xml"