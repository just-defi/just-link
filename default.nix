# Edit this configuration file to define what should be installed on

# your system.  Help is available in the configuration.nix(5) man page
# and in the NixOS manual (accessible by running ‘nixos-help’).

{ stdenv, callPackage, openjdk8, gradle_6, makeWrapper, tree, system, perl, ...
}:
with stdenv;
let
  deps = mkDerivation {
    name = "depppps";
    version = "0.0.6";
    nativeBuildInputs =
      [ openjdk8 (gradle_6.override { java = openjdk8; }) perl ];
    src = ./.;
    buildPhase = ''
      export GRADLE_USER_HOME=$(mktemp -d);
      gradle --no-daemon resolveDependencies;
    '';
    # keep only *.{pom,jar,sha1,nbm} and delete all ephemeral files with lastModified timestamps inside
    installPhase = ''
      find $GRADLE_USER_HOME/caches/modules-2 -type f -regex '.*\.\(jar\|pom\)' \
        | perl -pe 's#(.*/([^/]+)/([^/]+)/([^/]+)/[0-9a-f]{30,40}/([^/\s]+))$# ($x = $2) =~ tr|\.|/|; "install -Dm444 $1 \$out/maven/$x/$3/$4/$5" #e' \
        | sh
      rm -rf $out/maven/org/apache/httpcomponents/httpasyncclient/4.1.1
      rm -rf $out/maven/org/slf4j/jcl-over-slf4j/1.7.25
      rm -rf $out/maven/org/hamcrest/hamcrest-core/1.3
      rm -rf $out/maven/junit/junit/4.12
      rm -rf $out/maven/org/flywaydb/flyway-core/6.3.3
      rm -rf $out/maven/org/apache/commons/commons-lang3/3.9
      rm -rf $out/maven/io/dropwizard/metrics/metrics-core/3.1.2
      rm -rf $out/maven/org/aspectj/aspectjrt/1.8.13
      rm -rf $out/maven/org/aspectj/aspectjweaver/1.8.13
      rm -rf $out/maven/org/aspectj/aspectjtools/1.8.13
      rm -rf $out/maven/io/netty/netty-buffer/4.1.48.Final
      rm -rf $out/maven/io/netty/netty-codec/4.1.48.Final
      rm -rf $out/maven/io/netty/netty-codec-http/4.1.48.Final
      rm -rf $out/maven/io/netty/netty-codec-http2/4.1.48.Final
      rm -rf $out/maven/io/netty/netty-codec-socks/4.1.48.Final
      rm -rf $out/maven/io/netty/netty-common/4.1.48.Final
      rm -rf $out/maven/io/netty/netty-handler/4.1.48.Final
      rm -rf $out/maven/io/netty/netty-handler-proxy/4.1.48.Final
      rm -rf $out/maven/io/netty/netty-parent/4.1.48.Final
      rm -rf $out/maven/io/netty/netty-transport/4.1.48.Final
    '';
    outputHashAlgo = "sha256";
    outputHashMode = "recursive";
    outputHash = "sha256-7kJTNEcKby9JJN5Q4lrdguvia0MynYhdGNGwgSNY7Ds=";
  };

  backend = mkDerivation {
    name = "winklink";
    src = ./.;
    buildInputs = [ openjdk8 gradle_6 makeWrapper perl ];
    buildPhase = ''
      export GRADLE_USER_HOME=$(mktemp -d)

      export HOME="$NIX_BUILD_TOP/home"
      export JAVA_TOOL_OPTIONS="-Duser.home='$HOME'"

      mkdir -p .m2/repository/

      cp -r ${deps}/maven/* .m2/repository

      export M2_HOME=.m2

      ls ${deps}

      # point to offline repo
      sed -i "s#mavenLocal()#mavenLocal();maven { url '${deps}/maven' }#g" build.gradle


      # point to offline repo
      sed -i "s#mavenLocal()#mavenLocal();maven { url '${deps}/maven' }#g" node/build.gradle


      # point to offline repo
      sed -i "s#mavenLocal()#mavenLocal();maven { url '${deps}/maven' }#g" settings.gradle


      gradle --offline --info --no-daemon build -x test
    '';

    installPhase = ''
      mkdir -p $out/lib
      mkdir -p $out/bin
      cp node/build/libs/*.jar $out/lib
      makeWrapper ${openjdk8}/bin/java $out/bin/winklink-node --add-flags "-jar $out/lib/node-v1.0.jar"
    '';
  };
  frontend = (callPackage ./node/webapp/default.nix { }).shell;

in frontend
