FROM fcomb/alpine-jre8-sbt:v2.11_0.13
MAINTAINER Timothy Klim <fcomb@timothyklim.com>

USER root

RUN adduser -D -g '' -h /app fcomb
ENV APP /app
ENV HOME /home/java
ENV WORKDIR ${HOME}/project

COPY . ${WORKDIR}
WORKDIR ${WORKDIR}

RUN chown -R java:java ${WORKDIR} && \
    su java -c "${HOME}/bin/sbt universal:packageZipTarball" && \
    tar -xf ${WORKDIR}/target/universal/dist.tgz -C / && \
    mv /dist ${APP} && \
    chown -R fcomb:fcomb ${APP} && \
    deluser --remove-home java

USER fcomb
WORKDIR ${APP}
CMD ${APP}/bin/start
