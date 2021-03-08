# FluxAggregator Uses

This page outlines the uses of the FluxAggregator contract for the node operators that feed data into it.

## Contract Deploy

Import all the files located in `tvm-contracts/v2.0` into Tronscan and compile the `FluxAggregator.sol`.

After compiling finished, you should type the arguments that the constructor needs.

Here is the paraphrase:

- `_jst`: The address of the JST token.
- `_pay,entAmount`: The amount paid of JST paid to each oracle per submission, in wei (units of 10⁻¹⁸ JST).
- `_timeout`:  the number of seconds after the previous round that are allowed to lapse before allowing an oracle to skip an unfinished round.
- `_validator`: an optional contract address for validating external validation of answers.
- `_minSubmissionValue`: an immutable check for a lower bound of what submission values are accepted from an oracle.
- `_maxSubmissionValue`: an immutable check for an upper bound of what submission values are accepted from an oracle.
- `_decimals`: represents the number of decimals to offset the answer by.
- `_description`: a short description of what is being reported.

## Node Deployment

The node service deployment is the same as before as the service is compatible with both types of Aggregators.

## Withdrawing funds

Keep in mind the oracle variable is currently your node's address rather than your oracle contract's address.

## Testnet(Nile)

|Pair|Contract|
|:--|:--|
|TRX-USDT|TGm9cecRyrHAUziKrmRASPLb8fgZbJJmF9|


## Mainnet
|Pair|Contract|
|:--|:--|
| TRX/USD	| TWDRdAdbXuegDNGURDkAq41iACRNLohyt1 |
| JST/USD	| TMbR5ByrK7PUVtzo7HTb1CdCqAU1EbpKy4 |
| WIN/USD	| TXA4c5g1hxEpinm44rCByATVTG8PbDXREu |
| SUN/USD	| TCQkjqrJA5CpBYXLuUJUwXGBjko9hf5pWF |
| DICE/USD	| TQMADUvfrzoKN4fzvaDRU8ikz5KYEcKq1R |
| BTC/USD	| TSh5bfR9L9dxy7MA3Pah7uUVHKRwiAbFeF |
| USDJ/USD	| TTsmtL45Rcz6VKKsPjgrPbzpPKMNCxiuvZ |
| USDT/USD	| TCcnAueqMNo9QgWbMqH89KoAeNGbbU82Vt |
| LIVE/USD	| THaQ5Nfdrcn9YraNCSCvwBpvz2t9KCvucy |
| BTT/USD	| TEEdevx4m6hpo3dBZrXHVXUn8jGKbDp3C7 |
| JST/TRX	| TDNHjMKsstUgwUQxhYXcGZHMHpjExthJTS |
| WIN/TRX	| TExcFg5NDNbbqAiw2tsobEgVXAAKr4d7h8 |
| SUN/TRX	| TRD5w7BQNyivjHZgyGhzhNAYbAWs2cB2hS |
| DICE/TRX	| TUaA7xy1NbMWPbQhV4J2MfR9hNrnrpwBeP |
| BTC/TRX	| TKVsr2g31EyaDFaJRKc4x1bARRQKHF17iQ |
| USDJ/TRX	| TCnjeoqJwmWFWxp9jKTSrLk2fJpqaaVHsD |
| USDT/TRX	| TWzjJthkw7gy8A1Ff5vohF15Gokkd6DYWq |
| BTT/TRX	| TR9wiHB8cnrS9oPDqQUQWcCWRJ9hpDW3no |
| ETH/TRX	| TGSw7F7QUSqYAj7XiWkcuvSoBzmTUHX3pN |
