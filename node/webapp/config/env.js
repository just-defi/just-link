

const fs = require('fs');
const path = require('path');
const paths = require('./paths');

delete require.cache[require.resolve('./paths')];

const NODE_ENV = process.env.NODE_ENV;
if (!NODE_ENV) {
  throw new Error(
    'The NODE_ENV environment variable is required but was not specified.'
  );
}

var dotenvFiles = [
  `${paths.dotenv}.${NODE_ENV}.local`,
  `${paths.dotenv}.${NODE_ENV}`,
  NODE_ENV !== 'test' && `${paths.dotenv}.local`,
  paths.dotenv,
].filter(Boolean);


dotenvFiles.forEach(dotenvFile => {
  if (fs.existsSync(dotenvFile)) {
    require('dotenv-expand')(
      require('dotenv').config({
        path: dotenvFile,
      })
    );
  }
});

const appDirectory = fs.realpathSync(process.cwd());
process.env.NODE_PATH = (process.env.NODE_PATH || '')
  .split(path.delimiter)
  .filter(folder => folder && !path.isAbsolute(folder))
  .map(folder => path.resolve(appDirectory, folder))
  .join(path.delimiter);


const REACT_APP = /^REACT_APP_/i;

function getClientEnvironment(publicUrl) {
  const raw = Object.keys(process.env)
    .filter(key => REACT_APP.test(key))
    .reduce(
      (env, key) => {
        env[key] = process.env[key];
        return env;
      },
      {
        NODE_ENV: process.env.NODE_ENV || 'development',
        PUBLIC_URL: publicUrl,
        API_URL: process.env.API_URL || `http://localhost:8080`,
        API_URLS: process.env.API_URLS || [
            {text:"winklink-price-001" ,value:"http://localhost:8080"},
            {text:"winklink-price-002", value:"http://localhost:8081"}
        ],
        LIST_OF_DATASOURCE: process.env.LIST_OF_DATASOURCE || [
            {text: 'Kucoin', value:"kucoin"},
            {text: 'Kraken', value: "kraken"},
            {text: 'Huobi', value: 'huobi'},
            {text: "Bitrex", value: "bittrex"},
            {text: "OKX", value: "okex"},
            {text: "Poloniex", value: "poloniex"},
            {text: "Gate.io", value: "gateio"},
            {text: "bitfinex", value: "bitfinex"},
            {text: "Bitmart", value: "bitmart"},
            {text: "coingecko", value: "coingecko"},
            {text: "coinbase", value: "coinbase"},
            {text: "VRF", value: "vrf"},
            {text: "JustSwap", value:"justswap"},
        ],
        DATASOURCE_SIZE_PER_RETRIEVAL: process.env.DATASOURCE_SIZE_PER_RETRIEVAL || 100,
      }
    );
  console.log("Raw : ", raw);
  const stringified = {
    'process.env': Object.keys(raw).reduce((env, key) => {
      env[key] = JSON.stringify(raw[key]);
      return env;
    }, {}),
  };
  console.log("Stringified: ", stringified);
  return { raw, stringified };
}

module.exports = getClientEnvironment;
