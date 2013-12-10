@echo off 

set MAVEN_OPTS="-Xmx1024m" 
call mvn -U eclipse:clean eclipse:eclipse clean package -DskipTests -DdownloadSources=false -DdownloadDocs=false