pragma solidity ^0.5.0;

import "./VRF.sol";

/**
 * @title Ownable
 * @dev The Ownable contract has an owner address, and provides basic authorization control
 * functions, this simplifies the implementation of "user permissions".
 */
contract Ownable {
    address public owner;


    event OwnershipRenounced(address indexed previousOwner);
    event OwnershipTransferred(
        address indexed previousOwner,
        address indexed newOwner
    );


    /**
     * @dev The Ownable constructor sets the original `owner` of the contract to the sender
     * account.
     */
    constructor() public {
        owner = msg.sender;
    }

    /**
     * @dev Throws if called by any account other than the owner.
     */
    modifier onlyOwner() {
        require(msg.sender == owner);
        _;
    }

    /**
     * @dev Allows the current owner to transfer control of the contract to a newOwner.
     * @param _newOwner The address to transfer ownership to.
     */
    function transferOwnership(address _newOwner) public onlyOwner {
        _transferOwnership(_newOwner);
    }

    /**
     * @dev Transfers control of the contract to a newOwner.
     * @param _newOwner The address to transfer ownership to.
     */
    function _transferOwnership(address _newOwner) internal {
        require(_newOwner != address(0));
        emit OwnershipTransferred(owner, _newOwner);
        owner = _newOwner;
    }
}


/**
 * @title SafeMath
 * @dev Math operations with safety checks that throw on error
 */
library SafeMath {

    /**
    * @dev Multiplies two numbers, throws on overflow.
    */
    function mul(uint256 _a, uint256 _b) internal pure returns (uint256 c) {
        // Gas optimization: this is cheaper than asserting 'a' not being zero, but the
        // benefit is lost if 'b' is also tested.
        // See: https://github.com/OpenZeppelin/openzeppelin-solidity/pull/522
        if (_a == 0) {
            return 0;
        }

        c = _a * _b;
        assert(c / _a == _b);
        return c;
    }

    /**
    * @dev Integer division of two numbers, truncating the quotient.
    */
    function div(uint256 _a, uint256 _b) internal pure returns (uint256) {
        // assert(_b > 0); // Solidity automatically throws when dividing by 0
        // uint256 c = _a / _b;
        // assert(_a == _b * c + _a % _b); // There is no case in which this doesn't hold
        return _a / _b;
    }

    /**
    * @dev Subtracts two numbers, throws on overflow (i.e. if subtrahend is greater than minuend).
    */
    function sub(uint256 _a, uint256 _b) internal pure returns (uint256) {
        assert(_b <= _a);
        return _a - _b;
    }

    /**
    * @dev Adds two numbers, throws on overflow.
    */
    function add(uint256 _a, uint256 _b) internal pure returns (uint256 c) {
        c = _a + _b;
        assert(c >= _a);
        return c;
    }
}

interface JustlinkRequestInterface {
    function oracleRequest(
        address sender,
        uint256 payment,
        bytes32 id,
        address callbackAddress,
        bytes4 callbackFunctionId,
        uint256 nonce,
        uint256 version,
        bytes calldata data
    ) external;

    function cancelOracleRequest(
        bytes32 requestId,
        uint256 payment,
        bytes4 callbackFunctionId,
        uint256 expiration
    ) external;
}

interface OracleInterface {
    function fulfillOracleRequest(
        bytes32 requestId,
        uint256 payment,
        address callbackAddress,
        bytes4 callbackFunctionId,
        uint256 expiration,
        bytes32 data
    ) external returns (bool);
    function getAuthorizationStatus(address node) external view returns (bool);
    function setFulfillmentPermission(address node, bool allowed) external;
    function withdraw(address recipient, uint256 amount) external;
    function withdrawable() external view returns (uint256);
}

contract JustMid {

    function setToken(address tokenAddress) public ;

    function transferAndCall(address from, address to, uint tokens, bytes memory _data) public returns (bool success) ;

    function balanceOf(address guy) public view returns (uint);

    function transferFrom(address src, address dst, uint wad) public returns (bool);

    function allowance(address src, address guy) public view returns (uint);

}

contract TRC20Interface {

    function totalSupply() public view returns (uint);
    function balanceOf(address guy) public view returns (uint);
    function allowance(address src, address guy) public view returns (uint);
    function approve(address guy, uint wad) public returns (bool);
    function transfer(address dst, uint wad) public returns (bool);
    function transferFrom(address src, address dst, uint wad) public returns (bool);

    event Transfer(address indexed from, address indexed to, uint tokens);
    event Approval(address indexed tokenOwner, address indexed spender, uint tokens);
}


/**
 * @title The Justlink Oracle contract
 * @notice Node operators can deploy this contract to fulfill requests sent to them
 */
contract VRFCoordinator is JustlinkRequestInterface, OracleInterface, Ownable, VRF {
    using SafeMath for uint256;

    uint256 constant public EXPIRY_TIME = 5 minutes;
    // We initialize fields to 1 instead of 0 so that the first invocation
    // does not cost more gas.
    uint256 constant private ONE_FOR_CONSISTENT_GAS_COST = 1;
    uint256 constant private SELECTOR_LENGTH = 4;
    uint256 constant private EXPECTED_REQUEST_WORDS = 2;
    uint256 constant private MINIMUM_REQUEST_LENGTH = SELECTOR_LENGTH + (32 * EXPECTED_REQUEST_WORDS);

    JustMid internal justMid;
    TRC20Interface internal token;
    mapping(bytes32 => bytes32) private commitments;
    mapping(address => bool) private authorizedNodes;
    //uint256 private withdrawableTokens = ONE_FOR_CONSISTENT_GAS_COST;
    mapping(address /* oracle */ => uint256 /* JST balance */)
        public withdrawableTokens;

    event OracleRequest(
        bytes32 indexed specId,
        address requester,
        bytes32 requestId,
        uint256 payment,
        address callbackAddr,
        bytes4 callbackFunctionId,
        uint256 cancelExpiration,
        uint256 dataVersion,
        bytes data
    );

    // The Justlink node only needs the jobID to look up the VRF, but specifying public
    // key as well prevents a malicious Justlink node from inducing VRF outputs from
    // another Justlink node by reusing the jobID.
    event VRFRequest(
        bytes32 keyHash,
        uint256 seed,
        bytes32 indexed jobID,
        address sender,
        uint256 fee,
        bytes32 requestID);

    struct ServiceAgreement { // Tracks oracle commitments to VRF service
        address vRFOracle; // Oracle committing to respond with VRF service
        uint96 fee; // Minimum payment for oracle response. Total LINK=1e9*1e18<2^96
        bytes32 jobID; // ID of corresponding chainlink job in oracle's DB
    }

    struct Callback { // Tracks an ongoing request
        address callbackContract; // Requesting contract, which will receive response
        // Amount of JST paid at request time. Total JST = 1e9 * 1e18 < 2^96, so
        // this representation is adequate, and saves a word of storage when this
        // field follows the 160-bit callbackContract address.
        uint96 randomnessFee;
        // Commitment to seed passed to oracle by this contract, and the number of
        // the block in which the request appeared. This is the keccak256 of the
        // concatenation of those values. Storing this commitment saves a word of
        // storage.
        bytes32 seedAndBlockNum;
    }

    mapping(bytes32 /* provingKey */ => ServiceAgreement)
        public serviceAgreements;
    mapping(bytes32 /* provingKey */ => mapping(address /* consumer */ => uint256))
        private nonces;
    mapping(bytes32 /* (provingKey, seed) */ => Callback) public callbacks;

    event NewServiceAgreement(bytes32 keyHash, uint256 fee);
    event ZydTestCallbackinfo(address callbackContract, uint96 randomnessFee, bytes32 seedAndBlockNum, bytes4 callbackFunctionId);
    event ZydTestKeySeed(bytes32 keyHash, uint256 seed, uint256 nonce);

    event RandomnessRequestFulfilled(bytes32 requestId, uint256 output);

    event CancelOracleRequest(
        bytes32 indexed requestId
    );

    /**
     * @notice Deploy with the address of the LINK token
     * @dev Sets the LinkToken address for the imported LinkTokenInterface
     * @param _link The address of the LINK token
     */
    constructor(address _link, address _justMid) public Ownable() {
        token = TRC20Interface(_link); // external but already deployed and unalterable
        justMid = JustMid(_justMid);
    }

    /**
     * @notice Called when LINK is sent to the contract via `transferAndCall`
     * @dev The data payload's first 2 words will be overwritten by the `_sender` and `_amount`
     * values to ensure correctness. Calls oracleRequest.
     * @param _sender Address of the sender
     * @param _amount Amount of LINK sent (specified in wei)
     * @param _data Payload of the transaction
     */
    function onTokenTransfer(
        address _sender,
        uint256 _amount,
        bytes memory _data
    )
    public
    onlyJustMid
    validRequestLength(_data)
    permittedFunctionsForLINK(_data)
    {
        assembly { // solhint-disable-line no-inline-assembly
            mstore(add(_data, 36), _sender) // ensure correct sender is passed
            mstore(add(_data, 68), _amount) // ensure correct amount is passed
        }
        // solhint-disable-next-line avoid-low-level-calls
        (bool status, ) = address(this).delegatecall(_data);
        require(status, "Unable to create request"); // calls oracleRequest
    }

    /**
    * @notice Retrieves the stored address of the LINK token
    * @return The address of the LINK token
    */
    function justMidAddress()
    public
    view
    returns (address)
    {
        return address(justMid);
    }

    /**
     * @notice Creates the Justlink request
     * @dev Stores the hash of the params as the on-chain commitment for the request.
     * Emits OracleRequest event for the Justlink node to detect.
     * @param _sender The sender of the request
     * @param _payment The amount of payment given (specified in wei)
     * @param _specId The Job Specification ID
     * @param _callbackAddress The callback address for the response
     * @param _callbackFunctionId The callback function ID for the response
     * @param _nonce The nonce sent by the requester
     * @param _dataVersion The specified data version
     * @param _data The CBOR payload of the request
     */
    function oracleRequest(
        address _sender,
        uint256 _payment,
        bytes32 _specId,
        address _callbackAddress,
        bytes4 _callbackFunctionId,
        uint256 _nonce,
        uint256 _dataVersion,
        bytes calldata _data
    )
    external
    onlyJustMid
    checkCallbackAddress(_callbackAddress)
    {
        bytes32 requestId = keccak256(abi.encodePacked(_sender, _nonce));
        require(commitments[requestId] == 0, "Must use a unique ID");
        // solhint-disable-next-line not-rely-on-time
        uint256 expiration = now.add(EXPIRY_TIME);

        commitments[requestId] = keccak256(
            abi.encodePacked(
                _payment,
                _callbackAddress,
                _callbackFunctionId,
                expiration
            )
        );

        emit OracleRequest(
            _specId,
            _sender,
            requestId,
            _payment,
            _callbackAddress,
            _callbackFunctionId,
            expiration,
            _dataVersion,
            _data);
    }

    /**
    * @notice Commits calling address to serve randomness
    * @param _fee minimum LINK payment required to serve randomness
    * @param _node the address of the justlink node with the proving key and job
    * @param _publicProvingKey public key used to prove randomness
    * @param _jobID ID of the corresponding justlink job in the justlink node's db
    */
    function registerProvingKey(uint256 _fee, address _node, uint256[2] calldata _publicProvingKey, bytes32 _jobID)
        external
    {
        bytes32 keyHash = hashOfKey(_publicProvingKey);
        address oldVRFOracle = serviceAgreements[keyHash].vRFOracle;
        require(oldVRFOracle == address(0), "please register a new key");
        require(_node != address(0), "_node must not be 0x0");
        serviceAgreements[keyHash].vRFOracle = _node;
        serviceAgreements[keyHash].jobID = _jobID;
        // Yes, this revert message doesn't fit in a word
        /*require(_fee <= 1e9 ether,
        "you can't charge more than all the LINK in the world, greedy");*/
        serviceAgreements[keyHash].fee = uint96(_fee);
        emit NewServiceAgreement(keyHash, _fee);
    }

    /**
     * @notice Returns the serviceAgreements key associated with this public key
     * @param _publicKey the key to return the address for
     */
    function hashOfKey(uint256[2] memory _publicKey) public pure returns (bytes32) {
        return keccak256(abi.encodePacked(_publicKey));
    }

    /**
     * @dev Reverts if amount is not at least what was agreed upon in the service agreement
     * @param _feePaid The payment for the request
     * @param _keyHash The key which the request is for
     */
    function sufficientJST(uint256 _feePaid, bytes32 _keyHash) public view {
        require(_feePaid >= serviceAgreements[_keyHash].fee, "Below agreed payment");
    }


    /**
     * @notice Creates the VRF request
     * @dev Stores the hash of the params as the on-chain commitment for the request.
     * Emits VRFRequest event for the Justlink node to detect.
     * @param _sender The sender of the request
     * @param _feePaid The amount of payment given (specified in JST) //TODO wei?
     * @param _callbackAddress The callback address for the response
     * @param _callbackFunctionId The callback function ID for the response
     * @param _data The CBOR payload of the request
     */
    function vrfRequest(
        address _sender,
        uint256 _feePaid,
        bytes32 _specId,
        address _callbackAddress,
        bytes4 _callbackFunctionId,
        uint256 _nonce,
        uint256 _dataVersion,
        bytes calldata _data
    )
    external
    onlyJustMid
    checkCallbackAddress(_callbackAddress)
    {
        (bytes32 keyHash, uint256 consumerSeed) = abi.decode(_data, (bytes32, uint256));
        uint256 nonce = nonces[keyHash][_sender];
        emit ZydTestKeySeed(keyHash, consumerSeed, nonce);
        sufficientJST(_feePaid, keyHash);
        address sender = _sender;
        uint256 feePadi = _feePaid;
        uint256 preSeed = makeVRFInputSeed(keyHash, consumerSeed, sender, nonce);
        bytes32 requestId = makeRequestId(keyHash, preSeed);

        require(commitments[requestId] == 0, "Must use a unique ID");
        // solhint-disable-next-line not-rely-on-time
        uint256 expiration = now.add(EXPIRY_TIME);

        commitments[requestId] = keccak256(
            abi.encodePacked(
                _feePaid,
                _callbackAddress,
                _callbackFunctionId,
                expiration
            )
        );

        // Cryptographically guaranteed by preSeed including an increasing nonce
        assert(callbacks[requestId].callbackContract == address(0));
        callbacks[requestId].callbackContract = sender;
        assert(feePadi < 1e27); // Total JST fits in uint96 //ZYD TODO
        callbacks[requestId].randomnessFee = uint96(feePadi);
        callbacks[requestId].seedAndBlockNum = keccak256(abi.encodePacked(
          preSeed, block.number));
        emit VRFRequest(keyHash, preSeed, serviceAgreements[keyHash].jobID,
          sender, feePadi, requestId);
        nonces[keyHash][sender] = nonces[keyHash][sender].add(1);

        emit ZydTestCallbackinfo(callbacks[requestId].callbackContract, callbacks[requestId].randomnessFee,
            callbacks[requestId].seedAndBlockNum, _callbackFunctionId);
    }

    /**
    * @notice returns the seed which is actually input to the VRF coordinator
    *
    * @dev To prevent repetition of VRF output due to repetition of the
    * @dev user-supplied seed, that seed is combined in a hash with the
    * @dev user-specific nonce, and the address of the consuming contract. The
    * @dev risk of repetition is mostly mitigated by inclusion of a blockhash in
    * @dev the final seed, but the nonce does protect against repetition in
    * @dev requests which are included in a single block.
    *
    * @param _userSeed VRF seed input provided by user
    * @param _requester Address of the requesting contract
    * @param _nonce User-specific nonce at the time of the request
    */
    function makeVRFInputSeed(bytes32 _keyHash, uint256 _userSeed,
        address _requester, uint256 _nonce)
        internal pure returns (uint256)
    {
        return  uint256(keccak256(abi.encode(_keyHash, _userSeed, _requester, _nonce)));
    }

    /**
    * @notice Returns the id for this request
    * @param _keyHash The serviceAgreement ID to be used for this request
    * @param _vRFInputSeed The seed to be passed directly to the VRF
    * @return The id for this request
    *
    * @dev Note that _vRFInputSeed is not the seed passed by the consuming
    * @dev contract, but the one generated by makeVRFInputSeed
    */
    function makeRequestId(
        bytes32 _keyHash, uint256 _vRFInputSeed) internal pure returns (bytes32) {
        return keccak256(abi.encodePacked(_keyHash, _vRFInputSeed));
    }

    // Offsets into fulfillRandomnessRequest's _proof of various values
    //
    // Public key. Skips byte array's length prefix.
    uint256 public constant PUBLIC_KEY_OFFSET = 0x20;
    // Seed is 7th word in proof, plus word for length, (6+1)*0x20=0xe0
    uint256 public constant PRESEED_OFFSET = 0xe0;


    /**
       * @notice Called by the justlink node to fulfill requests
       *
       * param _proof the proof of randomness. Actual random output built from this
       *
       * @dev The structure of _proof corresponds to vrf.MarshaledOnChainResponse,
       * @dev in the node source code. I.e., it is a vrf.MarshaledProof with the
       * @dev seed replaced by the preSeed, followed by the hash of the requesting
       * @dev block.
       */
    function fulfillRandomnessRequest(bytes memory _proof, bytes4 _callbackFunctionId) public
    /*function fulfillRandomnessRequest(
        bytes32 _requestId,
        uint256 _payment,
        address _callbackAddress,
        bytes4 _callbackFunctionId,
        bytes32 _data
    )
    external*/
    onlyAuthorizedNode
    //isValidRequest(_requestId)
    returns (bool)
    {
        (bytes32 currentKeyHash, Callback memory callback, bytes32 requestId,
        uint256 randomness) = getRandomnessFromProof(_proof);

        require(commitments[requestId] != 0, "Must have a valid requestId");

        // Pay oracle
        address oadd = serviceAgreements[currentKeyHash].vRFOracle;
        withdrawableTokens[oadd] = withdrawableTokens[oadd].add(
        callback.randomnessFee);

        // Forget request. Must precede callback (prevents reentrancy)
        delete callbacks[requestId];
        bool status = callBackWithRandomness(requestId, randomness, callback.callbackContract, _callbackFunctionId);

        emit RandomnessRequestFulfilled(requestId, randomness);

        return status;


        /*emit RandomnessRequestFulfilled(_data, _payment);
        // All updates to the oracle's fulfillment should come before calling the
        // callback(addr+functionId) as it is untrusted.
        // See: https://solidity.readthedocs.io/en/develop/security-considerations.html#use-the-checks-effects-interactions-pattern
        //return _callbackAddress.call(_callbackFunctionId, _requestId, _data); // solhint-disable-line avoid-low-level-calls
        (bool status, ) = _callbackAddress.call(abi.encodePacked(_callbackFunctionId, _requestId, _data));
        return status;*/
    }

    function callBackWithRandomness(bytes32 requestId, uint256 randomness,
        address consumerContract, bytes4 _callbackFunctionId) internal returns (bool) {
        /*// Dummy variable; allows access to method selector in next line. See
        // https://github.com/ethereum/solidity/issues/3506#issuecomment-553727797
        VRFConsumerBase v;
        bytes memory resp = abi.encodeWithSelector(
          v.rawFulfillRandomness.selector, requestId, randomness);
        // The bound b here comes from https://eips.ethereum.org/EIPS/eip-150. The
        // actual gas available to the consuming contract will be b-floor(b/64).
        // This is chosen to leave the consuming contract ~200k gas, after the cost
        // of the call itself.
        uint256 b = 206000;
        require(gasleft() >= b, "not enough gas for consumer");*/
        // A low-level call is necessary, here, because we don't want the consuming
        // contract to be able to revert this execution, and thus deny the oracle
        // payment for a valid randomness response. This also necessitates the above
        // check on the gasleft, as otherwise there would be no indication if the
        // callback method ran out of gas.
        //
        // solhint-disable-next-line avoid-low-level-calls
        //(bool success,) = consumerContract.call(resp);
        (bool success,) = consumerContract.call(abi.encodePacked(_callbackFunctionId, requestId, randomness));
        // Avoid unused-local-variable warning. (success is only present to prevent
        // a warning that the return value of consumerContract.call is unused.)
        return success;
    }

    function getRandomnessFromProof(bytes memory _proof)
        internal view returns (bytes32 currentKeyHash, Callback memory callback,
          bytes32 requestId, uint256 randomness) {
        // blockNum follows proof, which follows length word (only direct-number
        // constants are allowed in assembly, so have to compute this in code)
        uint256 BLOCKNUM_OFFSET = 0x20 + PROOF_LENGTH;
        // _proof.length skips the initial length word, so not including the
        // blocknum in this length check balances out.
        require(_proof.length == BLOCKNUM_OFFSET, "wrong proof length");
        uint256[2] memory publicKey;
        uint256 preSeed;
        uint256 blockNum;
        uint256 publicKeyOffset = PUBLIC_KEY_OFFSET;
        uint256 preseedOffset = PRESEED_OFFSET;
        uint256 blocknumOffset = BLOCKNUM_OFFSET;
        assembly { // solhint-disable-line no-inline-assembly
          publicKey := add(_proof, publicKeyOffset)
          preSeed := mload(add(_proof, preseedOffset))
          blockNum := mload(add(_proof, blocknumOffset))
        }
        currentKeyHash = hashOfKey(publicKey);
        requestId = makeRequestId(currentKeyHash, preSeed);
        callback = callbacks[requestId];
        require(callback.callbackContract != address(0), "no corresponding request");
        require(callback.seedAndBlockNum == keccak256(abi.encodePacked(preSeed,
          blockNum)), "wrong preSeed or block num");

        bytes32 blockHash = blockhash(blockNum);
        if (blockHash == bytes32(0)) {
          //blockHash = blockHashStore.getBlockhash(blockNum); //ZYD TODO
          require(blockHash != bytes32(0), "please prove blockhash");
        }
        // The seed actually used by the VRF machinery, mixing in the blockhash
        uint256 actualSeed = uint256(keccak256(abi.encodePacked(preSeed, blockHash)));
        uint256 proofLength = PROOF_LENGTH;
        // solhint-disable-next-line no-inline-assembly
        assembly { // Construct the actual proof from the remains of _proof
          mstore(add(_proof, preseedOffset), actualSeed)
          mstore(_proof, proofLength)
        }
        randomness = VRF.randomValueFromVRFProof(_proof); // Reverts on failure
    }

    /**
     * @notice Called by the Justlink node to fulfill requests
     * @dev Given params must hash back to the commitment stored from `oracleRequest`.
     * Will call the callback address' callback function without bubbling up error
     * checking in a `require` so that the node can get paid.
     * @param _requestId The fulfillment request ID that must match the requester's
     * @param _payment The payment amount that will be released for the oracle (specified in wei)
     * @param _callbackAddress The callback address to call for fulfillment
     * @param _callbackFunctionId The callback function ID to use for fulfillment
     * @param _expiration The expiration that the node should respond by before the requester can cancel
     * @param _data The data to return to the consuming contract
     * @return Status if the external call was successful
     */
    /*function fulfillOracleRequest(
        bytes32 _requestId,
        uint256 _payment,
        address _callbackAddress,
        bytes4 _callbackFunctionId,
        uint256 _expiration,
        bytes32 _data
    )
    external
    onlyAuthorizedNode
    isValidRequest(_requestId)
    returns (bool)
    {
        bytes32 paramsHash = keccak256(
            abi.encodePacked(
                _payment,
                _callbackAddress,
                _callbackFunctionId,
                _expiration
            )
        );
        require(commitments[_requestId] == paramsHash, "Params do not match request ID");
        withdrawableTokens = withdrawableTokens.add(_payment);
        delete commitments[_requestId];
        // All updates to the oracle's fulfillment should come before calling the
        // callback(addr+functionId) as it is untrusted.
        // See: https://solidity.readthedocs.io/en/develop/security-considerations.html#use-the-checks-effects-interactions-pattern
        //return _callbackAddress.call(_callbackFunctionId, _requestId, _data); // solhint-disable-line avoid-low-level-calls
        (bool status, ) = _callbackAddress.call(abi.encodePacked(_callbackFunctionId, _requestId, _data));
        return status;
    }*/

    /**
     * @notice Use this to check if a node is authorized for fulfilling requests
     * @param _node The address of the Justlink node
     * @return The authorization status of the node
     */
    function getAuthorizationStatus(address _node) external view returns (bool) {
        return authorizedNodes[_node];
    }

    /**
     * @notice Sets the fulfillment permission for a given node. Use `true` to allow, `false` to disallow.
     * @param _node The address of the Justlink node
     * @param _allowed Bool value to determine if the node can fulfill requests
     */
    function setFulfillmentPermission(address _node, bool _allowed) external onlyOwner {
        authorizedNodes[_node] = _allowed;
    }

    /**
     * @notice Allows the node operator to withdraw earned LINK to a given address
     * @dev The owner of the contract can be another wallet and does not have to be a Justlink node
     * @param _recipient The address to send the LINK token to
     * @param _amount The amount to send (specified in wei)
     */
    function withdraw(address _recipient, uint256 _amount)
    external
    onlyOwner
    hasAvailableFunds(_amount)
    {
        withdrawableTokens[msg.sender] = withdrawableTokens[msg.sender].sub(_amount);
        token.approve(justMidAddress(), _amount);
        assert(justMid.transferFrom(address(this), _recipient, _amount));
    }

    /**
     * @notice Displays the amount of LINK that is available for the node operator to withdraw
     * @dev We use `ONE_FOR_CONSISTENT_GAS_COST` in place of 0 in storage
     * @return The amount of withdrawable LINK on the contract
     */
    function withdrawable() external view onlyOwner returns (uint256) {
        return withdrawableTokens[msg.sender].sub(ONE_FOR_CONSISTENT_GAS_COST);
    }

    /**
     * @notice Allows requesters to cancel requests sent to this oracle contract. Will transfer the LINK
     * sent for the request back to the requester's address.
     * @dev Given params must hash to a commitment stored on the contract in order for the request to be valid
     * Emits CancelOracleRequest event.
     * @param _requestId The request ID
     * @param _payment The amount of payment given (specified in wei)
     * @param _callbackFunc The requester's specified callback address
     * @param _expiration The time of the expiration for the request
     */
    function cancelOracleRequest(
        bytes32 _requestId,
        uint256 _payment,
        bytes4 _callbackFunc,
        uint256 _expiration
    ) external {
        bytes32 paramsHash = keccak256(
            abi.encodePacked(
                _payment,
                msg.sender,
                _callbackFunc,
                _expiration)
        );
        require(paramsHash == commitments[_requestId], "Params do not match request ID");
        // solhint-disable-next-line not-rely-on-time
        require(_expiration <= now, "Request is not expired");

        delete commitments[_requestId];
        emit CancelOracleRequest(_requestId);
        token.approve(justMidAddress(), _payment);
        assert(justMid.transferFrom(address(this), msg.sender, _payment));
    }

    // MODIFIERS

    /**
     * @dev Reverts if amount requested is greater than withdrawable balance
     * @param _amount The given amount to compare to `withdrawableTokens`
     */
    modifier hasAvailableFunds(uint256 _amount) {
        require(withdrawableTokens[msg.sender] >= _amount.add(ONE_FOR_CONSISTENT_GAS_COST), "Amount requested is greater than withdrawable balance");
        _;
    }

    /**
     * @dev Reverts if request ID does not exist
     * @param _requestId The given request ID to check in stored `commitments`
     */
    modifier isValidRequest(bytes32 _requestId) {
        require(commitments[_requestId] != 0, "Must have a valid requestId");
        _;
    }

    /**
     * @dev Reverts if `msg.sender` is not authorized to fulfill requests
     */
    modifier onlyAuthorizedNode() {
        require(authorizedNodes[msg.sender] || msg.sender == owner, "Not an authorized node to fulfill requests");
        _;
    }

    /**
     * @dev Reverts if not sent from the LINK token
     */
    modifier onlyJustMid() {
        require(msg.sender == address(justMid), "Must use JustMid");
        _;
    }

    /**
     * @dev Reverts if the given data does not begin with the `oracleRequest` function selector
     * @param _data The data payload of the request
     */
    modifier permittedFunctionsForLINK(bytes memory _data) {
        bytes4 funcSelector;
        assembly { // solhint-disable-line no-inline-assembly
            funcSelector := mload(add(_data, 32))
        }
        require(funcSelector == this.oracleRequest.selector || funcSelector == this.vrfRequest.selector, "Must use whitelisted functions");
        _;
    }

    /**
     * @dev Reverts if the callback address is the LINK token
     * @param _to The callback address
     */
    modifier checkCallbackAddress(address _to) {
        require(_to != address(justMid), "Cannot callback to LINK");
        _;
    }

    /**
     * @dev Reverts if the given payload is less than needed to create a request
     * @param _data The request payload
     */
    modifier validRequestLength(bytes memory _data) {
        require(_data.length >= MINIMUM_REQUEST_LENGTH, "Invalid request length");
        _;
    }

}
