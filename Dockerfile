FROM fcomb/jre8-sbt-alpine:2.12
MAINTAINER Timothy Klim <fcomb@timothyklim.com>

USER root

RUN adduser -D -g '' -h /app -H fcomb
ENV APP /app
ENV WORKDIR /home/java/project

COPY . ${WORKDIR}
WORKDIR ${WORKDIR}

RUN apk add --update --no-cache nodejs nodejs-dev && \
    npm install -g --progress=false yarn && \
    chown -R java:java ${WORKDIR} && \
    su java -c "${WORKDIR}/sbt universal:packageZipTarball" && \
    tar -xf ${WORKDIR}/target/universal/dist.tgz -C / && \
    mv /dist ${APP} && \
    mkdir /data && \
    chown -R fcomb:fcomb ${APP} /data && \
    deluser --remove-home java && \
    npm uninstall -g yarn && \
    apk del --purge nodejs nodejs-dev && \
    rm -rf /home/java /usr/lib/node_modules

EXPOSE 8080 8443

COPY docker/run.sh /
WORKDIR ${APP}
CMD /run.sh
