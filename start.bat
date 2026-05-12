@echo off
cd /d "%~dp0"
set JAVA_HOME=C:\Program Files\Java\jdk-26.0.1
set MAVEN_OPTS=-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.resolver.transport=wagon
"%JAVA_HOME%\bin\java.exe" -jar target\youtube-dual-sub-1.0.0.jar
