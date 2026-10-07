FROM tomcat:9.0-jdk11-temurin

RUN rm -rf /usr/local/tomcat/webapps/ROOT

COPY target/hello-1.0.0.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

CMD ["catalina.sh", "run"]