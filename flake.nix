{
  description = "Posuzovani shody";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-20.09";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let pkgs = nixpkgs.legacyPackages.${system};
      in {
        devShell = import ./shell.nix {
          pkgs = pkgs.extend
            (final: prev: { jre = prev.adoptopenjdk-hotspot-bin-8; });
        };
      });
}
