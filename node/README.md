# Wink Link Node

## Install

1. Install MySQL(>=5.7)
2. Install NodeJS

Initial the database using the sql file located in `${base_dir}/node/sql`.

## build

```
cd ${base_dir}
./gradlew clean build -xtest
```

The node jar path will be: `${base_dir}/node/build/libs/node-${version}.jar`.

## Run

```
java -jar node-${version}.jar -k ${key.store}
```

parameter explain:
- -k: specify the private key file

You can put your custom `application.yml` or `application-${profile}.yml` with `node-${version}.jar` in the same dir, springboot will load these configuration files with high priority.
More info refers: [Application Property Files](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-external-config-application-property-files)

