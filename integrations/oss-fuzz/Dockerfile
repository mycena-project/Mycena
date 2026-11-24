# Copyright 2026 Mycena Project — OSS-Fuzz builder image (submit with google/oss-fuzz PR).
FROM gcr.io/oss-fuzz-base/base-builder-rust

ARG MYCENA_REPO=https://github.com/mycena-project/Mycena.git
ARG MYCENA_REF=main

RUN git clone --depth 1 --branch ${MYCENA_REF} ${MYCENA_REPO} mycena
WORKDIR $SRC/mycena

COPY build.sh $SRC/
