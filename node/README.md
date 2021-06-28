# Just Link Node

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
- For oracle
```
java -jar node-${version}.jar -k ${key.store}
```

- For VRF
```
java -jar node-${version}.jar -k ${key.store} -vrfK ${vrfKeyStore.yml}
```

parameter explain:
- -k: specify the private key file
- -vrfK: specify the private key file for VRF

You can put your custom `application.yml` or `application-${profile}.yml` with `node-${version}.jar` in the same dir, springboot will load these configuration files with high priority.
More info refers: [Application Property Files](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-external-config-application-property-files)

- For oracle job creation: (replace the address with the actual oracle contract address)
```
curl --location --request POST 'http://localhost:8080/job/specs' \
  --header 'Content-Type: application/json' \
    --data-raw '{
    "initiators": [
        {
        "type": "runlog",
        "params": {
            "address": "TR9jYcLWAcQfbKcP5oau1ccSbeW7mdnqg8"
        }
        }
    ],
    "tasks": [
        {
        "type": "httpget",
        "params": {
            "get": "https://www.okex.com/api/spot/v3/instruments/TRX-USDT/ticker",
            "path": "last"
        }
        },
        {
        "type": "multiply",
        "params": {
            "times": 1000000
        }
        },
        {
        "type": "trontx"
        }
    ]
    }'
```

- For VRF job creation:
  (replace the address with the actual VRFCoordinator contract address
  and replace the publicKey with the actual VRF compressed publicKey)
```
curl --location --request POST 'http://localhost:8080/job/specs' \
  --header 'Content-Type: application/json' \
    --data-raw '{
    "initiators": [
        {
        "type": "randomnesslog",
        "params": {
            "address": "TYmwSFuFuiDZCtYsRFKCNr25byeqHH7Esb"
        }
        }
    ],
    "tasks": [
        {
        "type": "random",
        "params": {
        "publicKey":"0x79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F8179800"
        }
        },
        {
        "type": "trontx",
        "params": {
            "type": "TronVRF"
        }
	}
    ]
    }'
```