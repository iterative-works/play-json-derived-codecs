{ pkgs ? import <nixpkgs> { } }:

with pkgs;
mkShell { buildInputs = [ jre ammonite coursier bloop sbt scalafmt ]; }
