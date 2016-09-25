FROM fcomb/jre8-sbt-alpine
MAINTAINER Timothy Klim <fcomb@timothyklim.com>

USER root

RUN adduser -D -g '' -h /app -H fcomb
ENV APP /app
ENV WORKDIR /home/java/project

COPY . ${WORKDIR}
WORKDIR ${WORKDIR}

RUN apk update && \
    apk add nodejs nodejs-dev && \
    chown -R java:java ${WORKDIR} && \
    su java -c "/home/java/bin/sbt universal:packageZipTarball" && \
    tar -xf ${WORKDIR}/target/universal/dist.tgz -C / && \
    mv /dist ${APP} && \
    chown -R fcomb:fcomb ${APP} && \
    deluser --remove-home java && \
    apk del --purge nodejs nodejs-dev && \
    rm -rf /var/cache/apk/*

EXPOSE 8080

COPY docker/run.sh /
WORKDIR ${APP}
CMD /run.sh
