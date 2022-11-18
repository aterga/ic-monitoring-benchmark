FROM ocaml/opam:debian-11-ocaml-4.13-flambda AS build

RUN sudo apt-get update && sudo apt-get install -y \
    libgmp-dev \
    maven \
    && sudo apt-get clean && sudo rm -rf /var/lib/apt/lists/* \
    && opam update

ENV PREFIX=/home/opam/dist

COPY --chown=opam:opam monpoly monpoly
WORKDIR monpoly
RUN opam install -y --deps-only --ignore-constraints-on=libmonpoly . \
    && eval $(opam env) \
    && dune build --profile=release @install @runtest \
    && dune install --prefix=$PREFIX --relocatable monpoly

WORKDIR /home/opam
COPY --chown=opam:opam replayer replayer
WORKDIR replayer
RUN mvn package \
    && mkdir -p $PREFIX/lib \
    && cp replayer/target/replayer-1.0-SNAPSHOT.jar $PREFIX/lib/ \
    && cp replayer.sh $PREFIX/bin/replayer

FROM debian:11.5

RUN apt-get update && apt-get install -y \
    time \
    default-jre \
    libgmp10 \
    python3-pip \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

COPY --from=build /home/opam/dist /usr/local/

WORKDIR /work
COPY experiments/requirements.txt .
RUN pip3 install --no-cache-dir -r ./requirements.txt

ENTRYPOINT ["/bin/bash"]
