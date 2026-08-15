# Copyright 2026 Mycelium Project — OSS-Fuzz builder image (submit with google/oss-fuzz PR).
FROM gcr.io/oss-fuzz-base/base-builder-rust

ARG MYCELIUM_REPO=https://github.com/mycena-project/Mycelium.git
ARG MYCELIUM_REF=main

RUN git clone --depth 1 --branch ${MYCELIUM_REF} ${MYCELIUM_REPO} mycelium
WORKDIR $SRC/mycelium

COPY build.sh $SRC/
