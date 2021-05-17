pragma solidity ^0.5.0;

import "./Ownable.sol";
import "./TRC20Interface.sol";


/**
 * @title An example Justlink contract with aggregation
 * @notice Requesters can use this contract as a framework for creating
 * requests to multiple Justlink nodes and running aggregation
 * as the contract receives answers.
 */
contract VRFConsumer is AggregatorInterface, JustlinkClient, Ownable {
    using SignedSafeMath for int256;

    struct Answer {
        uint128 minimumResponses;
        uint128 maxResponses;
        int256[] responses;
    }

    event ResponseReceived(int256 indexed response, uint256 indexed answerId, address indexed sender);
    event NewVRFRound(uint256 indexed roundId, address indexed startedBy, bytes32 indexed blockHash);
    event VRFRequested(bytes32 indexed id);

    int256 private currentAnswerValue;
    uint256 private updatedTimestampValue;
    uint256 private latestCompletedAnswer;
    uint128 public paymentAmount;
    uint128 public minimumResponses;
    bytes32[] public jobIds;
    address[] public oracles;

    uint256 private answerCounter = 1;
    mapping(address => bool) public authorizedRequesters;
    mapping(bytes32 => uint256) private requestAnswers;
    mapping(uint256 => Answer) private answers;
    mapping(uint256 => int256) private currentAnswers;
    mapping(uint256 => uint256) private updatedTimestamps;

    uint256 constant private MAX_ORACLE_COUNT = 28;


    address private vrfCoordinator;
    bytes32 private s_keyHash;
    uint256 private s_fee;
    mapping(bytes32 => address) private s_rollers;
    mapping(address => uint256) private s_results;
    uint256 private constant ROLL_IN_PROGRESS = 42;

    // Nonces for each VRF key from which randomness has been requested.
    //
    // Must stay in sync with VRFCoordinator[_keyHash][this]
    mapping(bytes32 /* keyHash */ => uint256 /* nonce */) private nonces;
    event DiceRolled(bytes32 indexed requestId, address indexed roller);
    event DiceLanded(bytes32 indexed requestId, uint256 indexed result);
    uint256 public randomResult;

    /**
     * @notice Deploy with the address of the LINK token and arrays of matching
     * length containing the addresses of the oracles and their corresponding
     * Job IDs.
     * @dev Sets the LinkToken address for the network, addresses of the oracles,
     * and jobIds in storage.
     * @param _link The address of the LINK token
     * @param _justMid The address of the JustMid token
     * @param _vrfCoordinator The address of the VRFCoordinator contract
     */
    constructor(address _link, address _justMid, address _vrfCoordinator) public Ownable() {
        setJustlinkToken(_link);
        setJustMid(_justMid);
        vrfCoordinator = _vrfCoordinator;
        //, uint128 _paymentAmount, uint128 _minimumResponses,
        //        address[] _oracles, bytes32[] _jobIds
        //        updateRequestDetails(_paymentAmount, _minimumResponses, _oracles, _jobIds);
    }

    ///////////////// VRF begin /////////////////////
    /**
     * @notice Set the key hash for the oracle
     *
     * @param keyHash bytes32
     */
    function setKeyHash(bytes32 keyHash) public onlyOwner {
        s_keyHash = keyHash;
    }

    /**
     * @notice Get the current key hash
     *
     * @return bytes32
     */
    function keyHash() public view returns (bytes32) {
        return s_keyHash;
    }

    /**
     * @notice Set the oracle fee for requesting randomness
     *
     * @param fee uint256
     */
    function setFee(uint256 fee) public onlyOwner {
        s_fee = fee;
    }

    /**
     * @notice Get the current fee
     *
     * @return uint256
     */
    function fee() public view returns (uint256) {
        return s_fee;
    }

    // rawFulfillRandomness is called by VRFCoordinator when it receives a valid VRF
    // proof. rawFulfillRandomness then calls fulfillRandomness, after validating
    // the origin of the call
    function rawFulfillRandomness(bytes32 requestId, uint256 randomness) external {
        require(msg.sender == vrfCoordinator, "Only VRFCoordinator can fulfill");
        fulfillRandomness(requestId, randomness);
    }

    /**
     * @notice Callback function used by VRF Coordinator to return the random number
     * to this contract.
     * @dev Some action on the contract state should be taken here, like storing the result.
     * @dev WARNING: take care to avoid having multiple VRF requests in flight if their order of arrival would result
     * in contract states with different outcomes. Otherwise miners or the VRF operator would could take advantage
     * by controlling the order.
     * @dev The VRF Coordinator will only send this function verified responses, and the parent VRFConsumerBase
     * contract ensures that this method only receives randomness from the designated VRFCoordinator.
     *
     * @param requestId bytes32
     * @param randomness The random result returned by the oracle
     */
    function fulfillRandomness(bytes32 requestId, uint256 randomness) internal {
        //uint256 d20Value = randomness.mod(20).add(1);
        //s_results[s_rollers[requestId]] = d20Value;
        //emit DiceLanded(requestId, d20Value);

        randomResult = randomness;
        emit DiceLanded(requestId, randomness);
    }

    /**
     * @notice Requests randomness from a user-provided seed
     * @dev Warning: if the VRF response is delayed, avoid calling requestRandomness repeatedly
     * as that would give miners/VRF operators latitude about which VRF response arrives first.
     * @dev You must review your implementation details with extreme care.
     *
     * @param userProvidedSeed uint256 unpredictable seed
     * @param roller address of the roller
     */
    function rollDice(uint256 userProvidedSeed, address roller)
    public
    ensureAuthorizedRequester()
    returns (bytes32 requestId) {
        require(justMid.balanceOf(address(this)) >= s_fee, "Not enough JST to pay fee");
        //require(s_results[roller] == 0, "Already rolled");
        requestId = requestRandomness(s_keyHash, s_fee, userProvidedSeed);
        //s_rollers[requestId] = roller;
        //s_results[roller] = ROLL_IN_PROGRESS;
        emit DiceRolled(requestId, roller);
    }

    /**
     * @notice Updates the vrfCoordinator and jobId with new values,
     * overwriting the old values.
     * @param _paymentAmount the amount of JST to be sent to the vrfCoordinator for each request
     * before an answer will be calculated
     * @param _vrfCoordinator The vrfCoordinator address
     * @param _keyHash Justlink node's public key hash
     */
    function updateVRFRequestDetails(
        uint128 _paymentAmount,
        address _vrfCoordinator,
        bytes32 _keyHash
    )
    public
    onlyOwner()
    {
        s_fee = _paymentAmount;
        vrfCoordinator = _vrfCoordinator;
        s_keyHash = _keyHash;
    }

    /**
     * @notice requestRandomness initiates a request for VRF output given _seed
     *
     * @dev The fulfillRandomness method receives the output, once it's provided
     * @dev by the Oracle, and verified by the vrfCoordinator.
     *
     * @dev The _keyHash must already be registered with the VRFCoordinator, and
     * @dev the _fee must exceed the fee specified during registration of the
     * @dev _keyHash.
     *
     * @dev The _seed parameter is vestigial, and is kept only for API
     * @dev compatibility with older versions. It can't *hurt* to mix in some of
     * @dev your own randomness, here, but it's not necessary because the VRF
     * @dev oracle will mix the hash of the block containing your request into the
     * @dev VRF seed it ultimately uses.
     *
     * @param _keyHash ID of public key against which randomness is generated
     * @param _fee The amount of JST to send with the request
     * @param _seed seed mixed into the input of the VRF.
     *
     * @return requestId unique ID for this request
     *
     * @dev The returned requestId can be used to distinguish responses to
     * @dev concurrent requests. It is passed as the first argument to
     * @dev fulfillRandomness.
     */
    function requestRandomness(bytes32 _keyHash, uint256 _fee, uint256 _seed)
      internal returns (bytes32 requestId)
    {
        emit NewVRFRound(nonces[_keyHash], msg.sender, blockhash(block.number - 1));

        Justlink.Request memory _req;
        _req = buildJustlinkRequest(_keyHash, address(this), this.rawFulfillRandomness.selector);
        _req.nonce = nonces[_keyHash];
        _req.buf.buf = abi.encode(_keyHash, _seed); //zyd.TODO
        token.approve(justMidAddress(), _fee);
        require(justMid.transferAndCall(address(this), vrfCoordinator, _fee, encodeVRFRequest(_req)), "unable to transferAndCall to vrfCoordinator");

        // This is the seed passed to VRFCoordinator. The oracle will mix this with
        // the hash of the block containing this request to obtain the seed/input
        // which is finally passed to the VRF cryptographic machinery.
        uint256 vRFSeed  = makeVRFInputSeed(_keyHash, _seed, address(this), nonces[_keyHash]);
        // nonces[_keyHash] must stay in sync with
        // VRFCoordinator.nonces[_keyHash][this], which was incremented by the above
        // successful justMid.transferAndCall (in VRFCoordinator.randomnessRequest).
        // This provides protection against the user repeating their input seed,
        // which would result in a predictable/duplicate output, if multiple such
        // requests appeared in the same block.
        nonces[_keyHash] = nonces[_keyHash].add(1);

        requestId = makeRequestId(_keyHash, vRFSeed);

        emit VRFRequested(requestId);

        return requestId;
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

    ///////////////// VRF end /////////////////////

    /**
     * @notice Creates a Justlink request for each oracle in the oracles array.
     * @dev This example does not include request parameters. Reference any documentation
     * associated with the Job IDs used to determine the required parameters per-request.
     */
    function requestRateUpdate()
    external
    ensureAuthorizedRequester()
    returns (bytes32)
    {
        require(oracles.length > 0, "Please set oracles and jobIds");
        Justlink.Request memory request;
        bytes32 requestId;
        uint256 oraclePayment = paymentAmount;

        for (uint i = 0; i < oracles.length; i++) {
            request = buildJustlinkRequest(jobIds[i], address(this), this.justlinkCallback.selector);
            requestId = sendJustlinkRequestTo(oracles[i], request, oraclePayment);
            requestAnswers[requestId] = answerCounter;
        }
        answers[answerCounter].minimumResponses = minimumResponses;
        answers[answerCounter].maxResponses = uint128(oracles.length);

        emit NewRound(answerCounter, msg.sender, block.timestamp);

        answerCounter = answerCounter.add(1);

        return requestId;
    }

    /**
     * @notice Receives the answer from the Justlink node.
     * @dev This function can only be called by the oracle that received the request.
     * @param _clRequestId The Justlink request ID associated with the answer
     * @param _response The answer provided by the Justlink node
     */
    function justlinkCallback(bytes32 _clRequestId, int256 _response)
    external
    {
        validateJustlinkCallback(_clRequestId);

        uint256 answerId = requestAnswers[_clRequestId];
        delete requestAnswers[_clRequestId];

        answers[answerId].responses.push(_response);
        emit ResponseReceived(_response, answerId, msg.sender);
        updateLatestAnswer(answerId);
        deleteAnswer(answerId);
    }


    /**
     * @notice Updates the arrays of oracles and jobIds with new values,
     * overwriting the old values.
     * @dev Arrays are validated to be equal length.
     * @param _paymentAmount the amount of LINK to be sent to each oracle for each request
     * @param _minimumResponses the minimum number of responses
     * before an answer will be calculated
     * @param _oracles An array of oracle addresses
     * @param _jobIds An array of Job IDs
     */
    function updateRequestDetails(
        uint128 _paymentAmount,
        uint128 _minimumResponses,
        address[] memory _oracles,
        bytes32[] memory _jobIds
    )
    public
    onlyOwner()
    validateAnswerRequirements(_minimumResponses, _oracles, _jobIds)
    {
        paymentAmount = _paymentAmount;
        minimumResponses = _minimumResponses;
        jobIds = _jobIds;
        oracles = _oracles;
    }

    function getOracleSize() public view returns (uint256)
    {
        return oracles.length;
    }

    /**
     * @notice Allows the owner of the contract to withdraw any LINK balance
     * available on the contract.
     * @dev The contract will need to have a LINK balance in order to create requests.
     * @param _recipient The address to receive the LINK tokens
     * @param _amount The amount of LINK to send from the contract
     */
    function transferLINK(address _recipient, uint256 _amount)
    public
    onlyOwner()
    {
        token.approve(justMidAddress(), _amount);
        require(justMid.transferFrom(address(this), _recipient, _amount), "LINK transfer failed");
    }

    /**
     * @notice Called by the owner to permission other addresses to generate new
     * requests to oracles.
     * @param _requester the address whose permissions are being set
     * @param _allowed boolean that determines whether the requester is
     * permissioned or not
     */
    function setAuthorization(address _requester, bool _allowed)
    external
    onlyOwner()
    {
        authorizedRequesters[_requester] = _allowed;
    }

    /**
     * @notice Cancels an outstanding Justlink request.
     * The oracle contract requires the request ID and additional metadata to
     * validate the cancellation. Only old answers can be cancelled.
     * @param _requestId is the identifier for the Justlink request being cancelled
     * @param _payment is the amount of LINK paid to the oracle for the request
     * @param _expiration is the time when the request expires
     */
    function cancelRequest(
        bytes32 _requestId,
        uint256 _payment,
        uint256 _expiration
    )
    external
    ensureAuthorizedRequester()
    {
        uint256 answerId = requestAnswers[_requestId];
        require(answerId < latestCompletedAnswer, "Cannot modify an in-progress answer");

        delete requestAnswers[_requestId];
        answers[answerId].responses.push(0);
        deleteAnswer(answerId);

        cancelJustlinkRequest(
            _requestId,
            _payment,
            this.justlinkCallback.selector,
            _expiration
        );
    }

    /**
     * @notice Called by the owner to kill the contract. This transfers all LINK
     * balance and ETH balance (if there is any) to the owner.
     */
    function destroy()
    external
    onlyOwner()
    {
        transferLINK(owner, justMid.balanceOf(address(this)));
        selfdestruct(owner);
    }

    /**
     * @dev Performs aggregation of the answers received from the Justlink nodes.
     * Assumes that at least half the oracles are honest, which cannot control the
     * middle of the ordered responses.
     * @param _answerId The answer ID associated with the group of requests
     */
    function updateLatestAnswer(uint256 _answerId)
    private
    ensureMinResponsesReceived(_answerId)
    ensureOnlyLatestAnswer(_answerId)
    {
        uint256 responseLength = answers[_answerId].responses.length;
        uint256 middleIndex = responseLength.div(2);
        int256 currentAnswerTemp;
        if (responseLength % 2 == 0) {
            int256 median1 = quickselect(answers[_answerId].responses, middleIndex);
            int256 median2 = quickselect(answers[_answerId].responses, middleIndex.add(1));
            // quickselect is 1 indexed
            currentAnswerTemp = median1.add(median2) / 2;
            // signed integers are not supported by SafeMath
        } else {
            currentAnswerTemp = quickselect(answers[_answerId].responses, middleIndex.add(1));
            // quickselect is 1 indexed
        }
        currentAnswerValue = currentAnswerTemp;
        latestCompletedAnswer = _answerId;
        updatedTimestampValue = now;
        updatedTimestamps[_answerId] = now;
        currentAnswers[_answerId] = currentAnswerTemp;
        emit AnswerUpdated(currentAnswerTemp, _answerId, now);
    }

    /**
     * @notice get the most recently reported answer
     */
    function latestAnswer()
    external
    view
    returns (int256)
    {
        return currentAnswers[latestCompletedAnswer];
    }

    /**
     * @notice get the last updated at block timestamp
     */
    function latestTimestamp()
    external
    view
    returns (uint256)
    {
        return updatedTimestamps[latestCompletedAnswer];
    }

    /**
     * @notice get past rounds answers
     * @param _roundId the answer number to retrieve the answer for
     */
    function getAnswer(uint256 _roundId)
    external
    view
    returns (int256)
    {
        return currentAnswers[_roundId];
    }

    /**
     * @notice get block timestamp when an answer was last updated
     * @param _roundId the answer number to retrieve the updated timestamp for
     */
    function getTimestamp(uint256 _roundId)
    external
    view
    returns (uint256)
    {
        return updatedTimestamps[_roundId];
    }

    /**
     * @notice get the latest completed round where the answer was updated
     */
    function latestRound()
    external
    view
    returns (uint256)
    {
        return latestCompletedAnswer;
    }

    /**
     * @dev Returns the kth value of the ordered array
     * See: http://www.cs.yale.edu/homes/aspnes/pinewiki/QuickSelect.html
     * @param _a The list of elements to pull from
     * @param _k The index, 1 based, of the elements you want to pull from when ordered
     */
    function quickselect(int256[] memory _a, uint256 _k)
    private
    pure
    returns (int256)
    {
        int256[] memory a = _a;
        uint256 k = _k;
        uint256 aLen = a.length;
        int256[] memory a1 = new int256[](aLen);
        int256[] memory a2 = new int256[](aLen);
        uint256 a1Len;
        uint256 a2Len;
        int256 pivot;
        uint256 i;

        while (true) {
            pivot = a[aLen.div(2)];
            a1Len = 0;
            a2Len = 0;
            for (i = 0; i < aLen; i++) {
                if (a[i] < pivot) {
                    a1[a1Len] = a[i];
                    a1Len++;
                } else if (a[i] > pivot) {
                    a2[a2Len] = a[i];
                    a2Len++;
                }
            }
            if (k <= a1Len) {
                aLen = a1Len;
                (a, a1) = swap(a, a1);
            } else if (k > (aLen.sub(a2Len))) {
                k = k.sub(aLen.sub(a2Len));
                aLen = a2Len;
                (a, a2) = swap(a, a2);
            } else {
                return pivot;
            }
        }
    }

    /**
     * @dev Swaps the pointers to two uint256 arrays in memory
     * @param _a The pointer to the first in memory array
     * @param _b The pointer to the second in memory array
     */
    function swap(int256[] memory _a, int256[] memory _b)
    private
    pure
    returns (int256[] memory, int256[] memory)
    {
        return (_b, _a);
    }

    /**
     * @dev Cleans up the answer record if all responses have been received.
     * @param _answerId The identifier of the answer to be deleted
     */
    function deleteAnswer(uint256 _answerId)
    private
    ensureAllResponsesReceived(_answerId)
    {
        delete answers[_answerId];
    }

    /**
     * @dev Prevents taking an action if the minimum number of responses has not
     * been received for an answer.
     * @param _answerId The the identifier of the answer that keeps track of the responses.
     */
    modifier ensureMinResponsesReceived(uint256 _answerId) {
        if (answers[_answerId].responses.length >= answers[_answerId].minimumResponses) {
            _;
        }
    }

    /**
     * @dev Prevents taking an action if not all responses are received for an answer.
     * @param _answerId The the identifier of the answer that keeps track of the responses.
     */
    modifier ensureAllResponsesReceived(uint256 _answerId) {
        if (answers[_answerId].responses.length == answers[_answerId].maxResponses) {
            _;
        }
    }

    /**
     * @dev Prevents taking an action if a newer answer has been recorded.
     * @param _answerId The current answer's identifier.
     * Answer IDs are in ascending order.
     */
    modifier ensureOnlyLatestAnswer(uint256 _answerId) {
        if (latestCompletedAnswer <= _answerId) {
            _;
        }
    }

    /**
     * @dev Ensures corresponding number of oracles and jobs.
     * @param _oracles The list of oracles.
     * @param _jobIds The list of jobs.
     */
    modifier validateAnswerRequirements(
        uint256 _minimumResponses,
        address[] memory _oracles,
        bytes32[] memory _jobIds
    ) {
        require(_oracles.length <= MAX_ORACLE_COUNT, "cannot have more than 45 oracles");
        require(_oracles.length >= _minimumResponses, "must have at least as many oracles as responses");
        require(_oracles.length == _jobIds.length, "must have exactly as many oracles as job IDs");
        _;
    }

    /**
     * @dev Reverts if `msg.sender` is not authorized to make requests.
     */
    modifier ensureAuthorizedRequester() {
        require(authorizedRequesters[msg.sender] || msg.sender == owner, "Not an authorized address for creating requests");
        _;
    }

}
