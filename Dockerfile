FROM java:8

#Install maven
RUN apt-get update
RUN apt-get install -y maven


# gpg keys listed at https://github.com/nodejs/node
#RUN set -ex \
#    && for key in \
#          4ED778F539E3634C779C87C6D7062848A1AB005C \
#          B9E2F5981AA6E0CD28160D9FF13993A75599653C \
#          94AE36675C464D64BAFA68DD7434390BDBE9B9C5 \
#          B9AE9905FFD7803F25714661B63B535A4C206CA9 \
#          77984A986EBC2AA786BC0F66B01FBB92821C587A \
#          71DCFD284A79C3B38668286BC97EC7A07EDE3FC1 \
#          FD3A5288F042B6850C66B31F09FE44734EB7990E \
#          8FCCA13FEF1D0C2E91008E09770F7A9A5AE15600 \
#          C4F0DFFF4E8C1A8236409D08E73BC641CC11F4C8 \
#          DD8F2338BAE7501E3DD5AC78C273792F7D83545D \
#          A48C2BEE680E841632CD4E44F07496B3EB3C1762 \
#
#   ; do \
#        gpg --keyserver pool.sks-keyservers.net --recv-keys "$key"; \
#    done
    
gpg --keyserver pool.sks-keyservers.net --recv-keys 4ED778F539E3634C779C87C6D7062848A1AB005C
gpg --keyserver pool.sks-keyservers.net --recv-keys B9E2F5981AA6E0CD28160D9FF13993A75599653C
gpg --keyserver pool.sks-keyservers.net --recv-keys 94AE36675C464D64BAFA68DD7434390BDBE9B9C5
gpg --keyserver pool.sks-keyservers.net --recv-keys B9AE9905FFD7803F25714661B63B535A4C206CA9
gpg --keyserver pool.sks-keyservers.net --recv-keys 77984A986EBC2AA786BC0F66B01FBB92821C587A
gpg --keyserver pool.sks-keyservers.net --recv-keys 71DCFD284A79C3B38668286BC97EC7A07EDE3FC1
gpg --keyserver pool.sks-keyservers.net --recv-keys FD3A5288F042B6850C66B31F09FE44734EB7990E
gpg --keyserver pool.sks-keyservers.net --recv-keys 8FCCA13FEF1D0C2E91008E09770F7A9A5AE15600
gpg --keyserver pool.sks-keyservers.net --recv-keys C4F0DFFF4E8C1A8236409D08E73BC641CC11F4C8
gpg --keyserver pool.sks-keyservers.net --recv-keys DD8F2338BAE7501E3DD5AC78C273792F7D83545D
gpg --keyserver pool.sks-keyservers.net --recv-keys A48C2BEE680E841632CD4E44F07496B3EB3C1762

ENV NPM_CONFIG_LOGLEVEL info
ENV NODE_VERSION 6.3.1

RUN curl -SLO "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-x64.tar.xz" \
    && curl -SLO "https://nodejs.org/dist/v$NODE_VERSION/SHASUMS256.txt.asc" \
    && gpg --batch --decrypt --output SHASUMS256.txt SHASUMS256.txt.asc \
    && grep " node-v$NODE_VERSION-linux-x64.tar.xz\$" SHASUMS256.txt | sha256sum -c - \
    && tar -xJf "node-v$NODE_VERSION-linux-x64.tar.xz" -C /usr/local --strip-components=1 \
    && rm "node-v$NODE_VERSION-linux-x64.tar.xz" SHASUMS256.txt.asc SHASUMS256.txt


WORKDIR /build

# Dependencies
ADD pom.xml /build/pom.xml
RUN ["mvn", "dependency:resolve"]
RUN ["mvn", "verify"]
RUN ["npm", "install"]

# Compile and package jar
ADD src /build/src
RUN ["mvn", "package"]

#Setup Config
ADD server-keystore.jks /build/server-keystore.jks
ADD configuration.xml /build/configuration.xml

#Run msg-node
EXPOSE 9090
CMD ["mvn", "exec:java"]
