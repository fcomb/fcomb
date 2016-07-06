FROM fcomb/alpine-jre8-sbt:v2.11_0.13
MAINTAINER Timothy Klim <fcomb@timothyklim.com>

ENV HOME /home/java
ENV APPDIR ${HOME}/app
ENV WORKDIR ${HOME}/project

COPY . ${WORKDIR}
WORKDIR ${WORKDIR}

USER root
RUN chown -R java:java ${WORKDIR}

USER java
RUN ${HOME}/bin/sbt universal:packageZipTarball && \
    tar -xf ${WORKDIR}/target/universal/dist.tgz -C /tmp && \
    mv /tmp/dist ${APPDIR} && \
    rm -rf ${WORKDIR} ${HOME}/bin ${HOME}/.sbt ${HOME}/.coursier ${HOME}/.ivy2

WORKDIR ${APPDIR}
CMD ${APPDIR}/bin/start
