{
  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachSystem nixpkgs.lib.systems.flakeExposed (
      system:
      let
        pkgs = import nixpkgs { inherit system; };
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            pkgs.jdk21
            pkgs.gradle
            pkgs.jq
            pkgs.nodejs
          ];

          DB_URL = "jdbc:postgresql://localhost:5432/app";
          DB_USER = "app";
          DB_PASSWORD = "app";

          POSTGRES_DB = "app";
          POSTGRES_USER = "app";
          POSTGRES_PASSWORD = "app";
        };
      }
    );
}
