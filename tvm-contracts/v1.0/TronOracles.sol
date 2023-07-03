pragma solidity ^0.4.25;


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

interface WinklinkRequestInterface {
    function oracleRequest(
        address sender,
        uint256 payment,
        bytes32 id,
        address callbackAddress,
        bytes4 callbackFunctionId,
        uint256 nonce,
        uint256 version,
        bytes data
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

contract WinkMid {

    function setToken(address tokenAddress) public ;

    function transferAndCall(address from, address to, uint tokens, bytes _data) public returns (bool success) ;

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
 * @title The Winklink Oracle contract
 * @notice Node operators can deploy this contract to fulfill requests sent to them
 */
contract Oracle is WinklinkRequestInterface, OracleInterface, Ownable {
    using SafeMath for uint256;

    uint256 constant public EXPIRY_TIME = 5 minutes;
    // We initialize fields to 1 instead of 0 so that the first invocation
    // does not cost more gas.
    uint256 constant private ONE_FOR_CONSISTENT_GAS_COST = 1;
    uint256 constant private SELECTOR_LENGTH = 4;
    uint256 constant private EXPECTED_REQUEST_WORDS = 2;
    uint256 constant private MINIMUM_REQUEST_LENGTH = SELECTOR_LENGTH + (32 * EXPECTED_REQUEST_WORDS);

    WinkMid internal winkMid;
    TRC20Interface internal token;
    mapping(bytes32 => bytes32) private commitments;
    mapping(address => bool) private authorizedNodes;
    uint256 private withdrawableTokens = ONE_FOR_CONSISTENT_GAS_COST;

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

    event CancelOracleRequest(
        bytes32 indexed requestId
    );

    /**
     * @notice Deploy with the address of the LINK token
     * @dev Sets the LinkToken address for the imported LinkTokenInterface
     * @param _link The address of the LINK token
     */
    constructor(address _link, address _winkMid) public Ownable() {
        token = TRC20Interface(_link); // external but already deployed and unalterable
        winkMid = WinkMid(_winkMid);
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
        bytes _data
    )
    public
    onlyWinkMid
    validRequestLength(_data)
    permittedFunctionsForLINK(_data)
    {
        assembly { // solhint-disable-line no-inline-assembly
            mstore(add(_data, 36), _sender) // ensure correct sender is passed
            mstore(add(_data, 68), _amount) // ensure correct amount is passed
        }
        // solhint-disable-next-line avoid-low-level-calls
        require(address(this).delegatecall(_data), "Unable to create request"); // calls oracleRequest
    }

    /**
    * @notice Retrieves the stored address of the LINK token
    * @return The address of the LINK token
    */
    function winkMidAddress()
    public
    view
    returns (address)
    {
        return address(winkMid);
    }

    /**
     * @notice Creates the Winklink request
     * @dev Stores the hash of the params as the on-chain commitment for the request.
     * Emits OracleRequest event for the Winklink node to detect.
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
        bytes _data
    )
    external
    onlyWinkMid
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
     * @notice Called by the Winklink node to fulfill requests
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
    function fulfillOracleRequest(
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
        return _callbackAddress.call(_callbackFunctionId, _requestId, _data); // solhint-disable-line avoid-low-level-calls
    }

    /**
     * @notice Use this to check if a node is authorized for fulfilling requests
     * @param _node The address of the Winklink node
     * @return The authorization status of the node
     */
    function getAuthorizationStatus(address _node) external view returns (bool) {
        return authorizedNodes[_node];
    }

    /**
     * @notice Sets the fulfillment permission for a given node. Use `true` to allow, `false` to disallow.
     * @param _node The address of the Winklink node
     * @param _allowed Bool value to determine if the node can fulfill requests
     */
    function setFulfillmentPermission(address _node, bool _allowed) external onlyOwner {
        authorizedNodes[_node] = _allowed;
    }

    /**
     * @notice Allows the node operator to withdraw earned LINK to a given address
     * @dev The owner of the contract can be another wallet and does not have to be a Winklink node
     * @param _recipient The address to send the LINK token to
     * @param _amount The amount to send (specified in wei)
     */
    function withdraw(address _recipient, uint256 _amount)
    external
    onlyOwner
    hasAvailableFunds(_amount)
    {
        withdrawableTokens = withdrawableTokens.sub(_amount);
        token.approve(winkMidAddress(), _amount);
        assert(winkMid.transferFrom(address(this), _recipient, _amount));
    }

    /**
     * @notice Displays the amount of LINK that is available for the node operator to withdraw
     * @dev We use `ONE_FOR_CONSISTENT_GAS_COST` in place of 0 in storage
     * @return The amount of withdrawable LINK on the contract
     */
    function withdrawable() external view onlyOwner returns (uint256) {
        return withdrawableTokens.sub(ONE_FOR_CONSISTENT_GAS_COST);
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
        token.approve(winkMidAddress(), _payment);
        assert(winkMid.transferFrom(address(this), msg.sender, _payment));
    }

    // MODIFIERS

    /**
     * @dev Reverts if amount requested is greater than withdrawable balance
     * @param _amount The given amount to compare to `withdrawableTokens`
     */
    modifier hasAvailableFunds(uint256 _amount) {
        require(withdrawableTokens >= _amount.add(ONE_FOR_CONSISTENT_GAS_COST), "Amount requested is greater than withdrawable balance");
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
    modifier onlyWinkMid() {
        require(msg.sender == address(winkMid), "Must use WinkMid");
        _;
    }

    /**
     * @dev Reverts if the given data does not begin with the `oracleRequest` function selector
     * @param _data The data payload of the request
     */
    modifier permittedFunctionsForLINK(bytes _data) {
        bytes4 funcSelector;
        assembly { // solhint-disable-line no-inline-assembly
            funcSelector := mload(add(_data, 32))
        }
        require(funcSelector == this.oracleRequest.selector, "Must use whitelisted functions");
        _;
    }

    /**
     * @dev Reverts if the callback address is the LINK token
     * @param _to The callback address
     */
    modifier checkCallbackAddress(address _to) {
        require(_to != address(winkMid), "Cannot callback to LINK");
        _;
    }

    /**
     * @dev Reverts if the given payload is less than needed to create a request
     * @param _data The request payload
     */
    modifier validRequestLength(bytes _data) {
        require(_data.length >= MINIMUM_REQUEST_LENGTH, "Invalid request length");
        _;
    }

}
