// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts-upgradeable/access/Ownable2StepUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuardTransient.sol";

contract BlockchainTestContractV5 is Initializable, Ownable2StepUpgradeable, UUPSUpgradeable, ReentrancyGuardTransient {
    using SafeERC20 for IERC20;

    enum CompetitionState {
        Open,
        Settled,
        Cancelled
    }

    struct Competition {
        bytes32 competitionKey;
        address creator;
        address winner;
        address paymentToken;
        uint256 prizeAmount;
        uint256 entryFeeAmount;
        uint32 requiredParticipants;
        uint32 participantCount;
        uint64 joinDeadline;
        uint64 submitDeadline;
        CompetitionState state;
    }

    struct SubmissionResult {
        uint256 competitionId;
        uint256 submissionId;
        address participant;
        bytes32 submissionHash;
        bytes32 codeHash;
        bytes32 challengeHash;
        bytes32 resultHash;
        bytes32 sandboxImageHash;
        uint32 runtimeMs;
        uint32 memoryUsedKb;
        uint16 consensusNodes;
        uint64 recordedAt;
    }

    struct StoredSubmission {
        address participant;
        uint32 runtimeMs;
        uint32 memoryUsedKb;
    }

    struct SubmissionResultInput {
        uint256 competitionId;
        uint256 submissionId;
        bytes32 submissionHash;
        bytes32 codeHash;
        bytes32 challengeHash;
        bytes32 resultHash;
        bytes32 sandboxImageHash;
        uint32 runtimeMs;
        uint32 memoryUsedKb;
        uint16 consensusNodes;
    }

    event CompetitionCreated(
        uint256 indexed competitionId,
        bytes32 indexed competitionKey,
        address indexed creator,
        address paymentToken,
        uint256 prizeAmount,
        uint256 entryFeeAmount,
        uint256 joinDeadline,
        uint256 submitDeadline,
        uint256 requiredParticipants
    );

    event CompetitionJoined(
        uint256 indexed competitionId,
        address indexed participant,
        uint256 participantCount
    );

    event CompetitionSettled(
        uint256 indexed competitionId,
        address indexed winner,
        address paymentToken,
        uint256 prizeAmount,
        uint256 creatorPayoutAmount,
        uint256 platformFeeAmount
    );

    event CompetitionCancelled(uint256 indexed competitionId, uint256 participantCount);
    event ParticipantRefundClaimed(uint256 indexed competitionId, address indexed participant, uint256 amount);
    event CreatorPrizeRefundClaimed(uint256 indexed competitionId, address indexed creator, uint256 amount);
    event SubmissionResultRecorded(
        uint256 indexed competitionId,
        uint256 indexed submissionId,
        address indexed participant,
        bytes32 submissionHash,
        bytes32 codeHash,
        bytes32 challengeHash,
        bytes32 resultHash,
        bytes32 sandboxImageHash,
        uint32 runtimeMs,
        uint32 memoryUsedKb,
        uint16 consensusNodes
    );
    event BestSubmissionUpdated(uint256 indexed competitionId, uint256 indexed submissionId);
    event OperatorUpdated(address indexed previousOperator, address indexed newOperator);
    event TreasuryUpdated(address indexed previousTreasury, address indexed newTreasury);
    event PlatformFeeBpsUpdated(uint16 previousFeeBps, uint16 newFeeBps);
    event SandboxImageApprovalUpdated(bytes32 indexed sandboxImageHash, bool approved);
    event PaymentTokenSupportUpdated(address indexed token, bool supported);

    mapping(uint256 => Competition) private competitions;
    mapping(bytes32 => bool) public competitionKeyUsed;
    mapping(uint256 => mapping(address => bool)) public hasJoined;
    mapping(uint256 => mapping(address => bool)) public participantRefundClaimed;
    mapping(uint256 => bool) public creatorPrizeRefundClaimed;

    mapping(uint256 => SubmissionResult) private resultsBySubmissionId;
    mapping(uint256 => uint256[]) private submissionIdsByCompetition;
    mapping(bytes32 => bool) public approvedSandboxImageHash;
    mapping(uint256 => uint256) public bestSubmissionIdByCompetition;
    mapping(address => bool) public supportedPaymentToken;

    uint256 public nextCompetitionId;
    address public operator;
    address public treasury;
    uint16 public platformFeeBps;
    mapping(uint256 => StoredSubmission) private submissionScoresById;

    error Unauthorized();
    error InvalidAddress();
    error InvalidCompetition();
    error InvalidSubmission();
    error InvalidHash();
    error CompetitionAlreadyExists(bytes32 competitionKey);
    error InvalidDeadline();
    error InvalidParticipants();
    error InvalidPrize();
    error InvalidPlatformFeeBps();
    error RegistrationClosed();
    error SubmitWindowNotFinished();
    error CompetitionNotOpen();
    error CompetitionNotCancelled();
    error CompetitionFull();
    error AlreadyJoined();
    error IncorrectEntryFee();
    error IncorrectNativeValue();
    error NativeValueNotExpected();
    error CompetitionNotReadyForSettlement();
    error CompetitionNotReadyForCancellation();
    error WinnerMustBeParticipant();
    error WinningSubmissionNotRecorded();
    error ParticipantNotJoined();
    error SubmissionWindowClosed();
    error SandboxImageNotApproved(bytes32 sandboxImageHash);
    error SubmissionAlreadyRecorded(uint256 submissionId);
    error UnsupportedPaymentToken(address token);
    error UnsupportedTokenBehavior(address token);
    error NothingToRefund();
    error TransferFailed();
    error InvalidSignature();

    modifier onlyOperator() {
        if (msg.sender != operator) revert Unauthorized();
        _;
    }

    /// Prevents the implementation contract from being initialized directly.
    /// The deployed proxy is initialized through initialize(), while this standalone
    /// implementation instance must remain locked.
    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    /// Returns a human-readable version of the contract logic.
    /// It is useful for deployment checks and for confirming which implementation
    /// is currently attached to the proxy.
    function version() external pure returns (string memory) {
        return "5.0.0";
    }

    /// Initializes the storage used by the proxy after deployment.
    /// Sets the owner, operator wallet, treasury wallet, platform fee, first approved
    /// sandbox image hash, and optional ERC-20 payment token.
    function initialize(
        address initialOwner,
        address initialOperator,
        address initialTreasury,
        uint16 initialPlatformFeeBps,
        bytes32 initialApprovedSandboxImageHash,
        address initialSupportedPaymentToken
    ) public initializer {
        if (initialOwner == address(0) || initialOperator == address(0) || initialTreasury == address(0)) {
            revert InvalidAddress();
        }

        __Ownable_init(initialOwner);
        __Ownable2Step_init();
        nextCompetitionId = 1;
        operator = initialOperator;
        treasury = initialTreasury;
        _setPlatformFeeBps(initialPlatformFeeBps);

        emit OperatorUpdated(address(0), initialOperator);
        emit TreasuryUpdated(address(0), initialTreasury);

        if (initialApprovedSandboxImageHash != bytes32(0)) {
            approvedSandboxImageHash[initialApprovedSandboxImageHash] = true;
            emit SandboxImageApprovalUpdated(initialApprovedSandboxImageHash, true);
        }
        if (initialSupportedPaymentToken != address(0)) {
            supportedPaymentToken[initialSupportedPaymentToken] = true;
            emit PaymentTokenSupportUpdated(initialSupportedPaymentToken, true);
        }
    }

    /// Creates a new competition and locks the creator's prize in escrow.
    /// The function validates deadlines, uniqueness, prize size, participant limit,
    /// and payment token support before storing the competition data.
    function createCompetition(
        bytes32 competitionKey,
        address paymentToken,
        uint256 prizeAmount,
        uint256 entryFeeAmount,
        uint64 joinDeadline,
        uint64 submitDeadline,
        uint32 requiredParticipants
    ) external payable nonReentrant returns (uint256 competitionId) {
        if (competitionKey == bytes32(0)) revert InvalidCompetition();
        if (competitionKeyUsed[competitionKey]) revert CompetitionAlreadyExists(competitionKey);
        if (prizeAmount == 0) revert InvalidPrize();
        if (requiredParticipants == 0) revert InvalidParticipants();
        if (submitDeadline <= joinDeadline || joinDeadline <= block.timestamp) revert InvalidDeadline();

        _ensurePaymentTokenSupported(paymentToken);
        _collectPayment(paymentToken, msg.sender, prizeAmount);

        competitionId = nextCompetitionId++;
        competitionKeyUsed[competitionKey] = true;

        Competition storage competition = competitions[competitionId];
        competition.competitionKey = competitionKey;
        competition.creator = msg.sender;
        competition.paymentToken = paymentToken;
        competition.prizeAmount = prizeAmount;
        competition.entryFeeAmount = entryFeeAmount;
        competition.requiredParticipants = requiredParticipants;
        competition.joinDeadline = joinDeadline;
        competition.submitDeadline = submitDeadline;
        competition.state = CompetitionState.Open;

        emit CompetitionCreated(
            competitionId,
            competitionKey,
            msg.sender,
            paymentToken,
            prizeAmount,
            entryFeeAmount,
            joinDeadline,
            submitDeadline,
            requiredParticipants
        );
    }

    /// Registers the caller as a participant in an open competition.
    /// It rejects late registrations, full competitions, duplicate participants,
    /// and collects the required entry fee before increasing participantCount.
    function joinCompetition(uint256 competitionId) external payable nonReentrant {
        Competition storage competition = competitions[competitionId];
        if (competition.creator == address(0)) revert InvalidCompetition();
        if (competition.state != CompetitionState.Open) revert CompetitionNotOpen();
        if (block.timestamp > competition.joinDeadline) revert RegistrationClosed();
        if (competition.participantCount >= competition.requiredParticipants) revert CompetitionFull();
        if (hasJoined[competitionId][msg.sender]) revert AlreadyJoined();

        _collectJoinPayment(competition, msg.sender);

        hasJoined[competitionId][msg.sender] = true;
        competition.participantCount += 1;

        emit CompetitionJoined(competitionId, msg.sender, competition.participantCount);
    }

    /// Finalizes a competition after the submission period has ended.
    /// The best recorded submission receives the prize, the creator receives entry fees
    /// minus the platform fee, and the treasury receives the platform fee.
    function settleCompetition(uint256 competitionId) external nonReentrant {
        Competition storage competition = competitions[competitionId];
        if (competition.creator == address(0)) revert InvalidCompetition();
        if (competition.state != CompetitionState.Open) revert CompetitionNotOpen();
        if (block.timestamp <= competition.submitDeadline) revert SubmitWindowNotFinished();
        if (competition.participantCount < competition.requiredParticipants) revert CompetitionNotReadyForSettlement();
        uint256 winningSubmissionId = bestSubmissionIdByCompetition[competitionId];
        if (winningSubmissionId == 0) revert WinningSubmissionNotRecorded();
        address winner = _loadStoredSubmission(winningSubmissionId).participant;
        if (!hasJoined[competitionId][winner]) revert WinnerMustBeParticipant();

        competition.state = CompetitionState.Settled;
        competition.winner = winner;

        uint256 grossEntryFees = competition.entryFeeAmount * competition.participantCount;
        uint256 platformFeeAmount = (grossEntryFees * platformFeeBps) / 10_000;
        uint256 creatorPayoutAmount = grossEntryFees - platformFeeAmount;

        _transferPayment(competition.paymentToken, winner, competition.prizeAmount);
        if (creatorPayoutAmount > 0) {
            _transferPayment(competition.paymentToken, competition.creator, creatorPayoutAmount);
        }
        if (platformFeeAmount > 0) {
            _transferPayment(competition.paymentToken, treasury, platformFeeAmount);
        }

        emit CompetitionSettled(
            competitionId,
            winner,
            competition.paymentToken,
            competition.prizeAmount,
            creatorPayoutAmount,
            platformFeeAmount
        );
    }

    /// Cancels an open competition when it cannot or should not be settled.
    /// Cancellation is allowed when registration ended without enough participants
    /// or when the submission period finished without a completed settlement.
    function cancelCompetition(uint256 competitionId) external {
        Competition storage competition = competitions[competitionId];
        if (competition.creator == address(0)) revert InvalidCompetition();
        if (competition.state != CompetitionState.Open) revert CompetitionNotOpen();
        bool registrationFailed = block.timestamp > competition.joinDeadline &&
            competition.participantCount < competition.requiredParticipants;
        bool submissionsFinished = block.timestamp > competition.submitDeadline;
        if (!registrationFailed && !submissionsFinished) {
            revert CompetitionNotReadyForCancellation();
        }

        competition.state = CompetitionState.Cancelled;
        emit CompetitionCancelled(competitionId, competition.participantCount);
    }

    /// Lets a registered participant reclaim the entry fee after cancellation.
    /// Each participant can claim only once, and only competitions marked as Cancelled
    /// are eligible for this refund path.
    function claimParticipantRefund(uint256 competitionId) external nonReentrant {
        Competition storage competition = competitions[competitionId];
        if (competition.creator == address(0)) revert InvalidCompetition();
        if (competition.state != CompetitionState.Cancelled) revert CompetitionNotCancelled();
        if (!hasJoined[competitionId][msg.sender]) revert NothingToRefund();
        if (participantRefundClaimed[competitionId][msg.sender]) revert NothingToRefund();

        participantRefundClaimed[competitionId][msg.sender] = true;
        _transferPayment(competition.paymentToken, msg.sender, competition.entryFeeAmount);

        emit ParticipantRefundClaimed(competitionId, msg.sender, competition.entryFeeAmount);
    }

    /// Lets the creator reclaim the original prize deposit after cancellation.
    /// The caller must be the competition creator, and the prize can only be refunded
    /// once to prevent double withdrawal from escrow.
    function claimCreatorPrizeRefund(uint256 competitionId) external nonReentrant {
        Competition storage competition = competitions[competitionId];
        if (competition.creator == address(0)) revert InvalidCompetition();
        if (competition.state != CompetitionState.Cancelled) revert CompetitionNotCancelled();
        if (msg.sender != competition.creator) revert Unauthorized();
        if (creatorPrizeRefundClaimed[competitionId]) revert NothingToRefund();

        creatorPrizeRefundClaimed[competitionId] = true;
        _transferPayment(competition.paymentToken, msg.sender, competition.prizeAmount);

        emit CreatorPrizeRefundClaimed(competitionId, msg.sender, competition.prizeAmount);
    }

    /// Records an accepted solution result submitted by a competition participant.
    /// The platform operator signs the measured result off-chain, and this function
    /// verifies that signature before storing runtime and memory metrics on-chain.
    function recordSubmissionResult(
        uint256 competitionId,
        uint256 submissionId,
        bytes32 submissionHash,
        bytes32 codeHash,
        bytes32 challengeHash,
        bytes32 resultHash,
        bytes32 sandboxImageHash,
        uint32 runtimeMs,
        uint32 memoryUsedKb,
        uint16 consensusNodes,
        bytes calldata signature
    ) external {
        SubmissionResultInput memory input = SubmissionResultInput({
            competitionId: competitionId,
            submissionId: submissionId,
            submissionHash: submissionHash,
            codeHash: codeHash,
            challengeHash: challengeHash,
            resultHash: resultHash,
            sandboxImageHash: sandboxImageHash,
            runtimeMs: runtimeMs,
            memoryUsedKb: memoryUsedKb,
            consensusNodes: consensusNodes
        });
        _recordSubmissionResult(input, signature);
    }

    /// Performs the full validation flow for recording a submission result.
    /// It checks identifiers, hashes, competition state, deadline, participant membership,
    /// sandbox approval, duplicate records, and the operator signature.
    function _recordSubmissionResult(SubmissionResultInput memory input, bytes calldata signature) private {
        if (input.competitionId == 0 || input.submissionId == 0 || msg.sender == address(0)) revert InvalidSubmission();
        if (
            input.submissionHash == bytes32(0) ||
            input.codeHash == bytes32(0) ||
            input.challengeHash == bytes32(0) ||
            input.resultHash == bytes32(0) ||
            input.sandboxImageHash == bytes32(0)
        ) {
            revert InvalidHash();
        }
        Competition storage competition = competitions[input.competitionId];
        if (competition.creator == address(0)) revert InvalidCompetition();
        if (competition.state != CompetitionState.Open) revert CompetitionNotOpen();
        if (block.timestamp > competition.submitDeadline) revert SubmissionWindowClosed();
        if (!hasJoined[input.competitionId][msg.sender]) revert ParticipantNotJoined();
        if (!approvedSandboxImageHash[input.sandboxImageHash]) revert SandboxImageNotApproved(input.sandboxImageHash);
        if (_submissionRecordExists(input.submissionId)) {
            revert SubmissionAlreadyRecorded(input.submissionId);
        }

        _verifySubmissionResultSignature(input, signature);

        submissionScoresById[input.submissionId] = StoredSubmission({
            participant: msg.sender,
            runtimeMs: input.runtimeMs,
            memoryUsedKb: input.memoryUsedKb
        });

        _updateBestSubmission(input.competitionId, input.submissionId);

        emit SubmissionResultRecorded(
            input.competitionId,
            input.submissionId,
            msg.sender,
            input.submissionHash,
            input.codeHash,
            input.challengeHash,
            input.resultHash,
            input.sandboxImageHash,
            input.runtimeMs,
            input.memoryUsedKb,
            input.consensusNodes
        );
    }

    /// Returns the complete stored state of a competition.
    /// This read method exposes the fields needed by the backend and UI without allowing
    /// direct mutation of the private competitions mapping.
    function getCompetition(uint256 competitionId)
    external
    view
    returns (
        bytes32 competitionKey,
        address creator,
        address winner,
        address paymentToken,
        uint256 prizeAmount,
        uint256 entryFeeAmount,
        uint32 requiredParticipants,
        uint32 participantCount,
        uint64 joinDeadline,
        uint64 submitDeadline,
        CompetitionState state
    )
    {
        Competition storage competition = competitions[competitionId];
        if (competition.creator == address(0)) revert InvalidCompetition();

        return (
            competition.competitionKey,
            competition.creator,
            competition.winner,
            competition.paymentToken,
            competition.prizeAmount,
            competition.entryFeeAmount,
            competition.requiredParticipants,
            competition.participantCount,
            competition.joinDeadline,
            competition.submitDeadline,
            competition.state
        );
    }

    /// Returns the stored participant and performance metrics for a submission.
    /// The loader supports both the compact score mapping and the older full result
    /// mapping, so historical records remain readable after upgrades.
    function getSubmissionResult(uint256 submissionId) external view returns (StoredSubmission memory) {
        return _loadStoredSubmission(submissionId);
    }

    /// Returns the current best submission score for a competition.
    /// The best submission is updated whenever a better accepted result is recorded,
    /// using runtime, memory usage, and submission id as tie-breakers.
    function getBestSubmissionResult(uint256 competitionId) external view returns (StoredSubmission memory) {
        uint256 submissionId = bestSubmissionIdByCompetition[competitionId];
        if (submissionId == 0) revert InvalidSubmission();
        return _loadStoredSubmission(submissionId);
    }

    /// Changes the operator wallet allowed to sign submission results.
    /// Only the contract owner can update it, because accepting invalid operator
    /// signatures would allow untrusted benchmark results to be recorded.
    function setOperator(address newOperator) external onlyOwner {
        if (newOperator == address(0)) revert InvalidAddress();
        address previousOperator = operator;
        operator = newOperator;
        emit OperatorUpdated(previousOperator, newOperator);
    }

    /// Changes the treasury wallet that receives platform fees during settlement.
    /// The address must be non-zero, and the emitted event provides an auditable
    /// history of treasury changes.
    function setTreasury(address newTreasury) external onlyOwner {
        if (newTreasury == address(0)) revert InvalidAddress();
        address previousTreasury = treasury;
        treasury = newTreasury;
        emit TreasuryUpdated(previousTreasury, newTreasury);
    }

    /// Updates the platform fee expressed in basis points.
    /// The actual validation is delegated to _setPlatformFeeBps so initialization
    /// and later owner updates share the same fee limit.
    function setPlatformFeeBps(uint16 newPlatformFeeBps) external onlyOwner {
        _setPlatformFeeBps(newPlatformFeeBps);
    }

    /// Allows or blocks a sandbox image hash for accepted result recording.
    /// A submission result can only be stored if it was produced by an approved
    /// sandbox image, which protects the benchmark evidence from unknown runtimes.
    function setSandboxImageApproved(bytes32 sandboxImageHash, bool approved) external onlyOwner {
        if (sandboxImageHash == bytes32(0)) revert InvalidHash();
        approvedSandboxImageHash[sandboxImageHash] = approved;
        emit SandboxImageApprovalUpdated(sandboxImageHash, approved);
    }

    /// Allows or blocks an ERC-20 token as a competition payment asset.
    /// Native ETH is represented by address(0), while ERC-20 tokens must be explicitly
    /// supported before they can be used for prizes or entry fees.
    function setSupportedPaymentToken(address token, bool supported) external onlyOwner {
        if (token == address(0)) revert InvalidAddress();
        supportedPaymentToken[token] = supported;
        emit PaymentTokenSupportUpdated(token, supported);
    }

    /// Authorizes UUPS implementation upgrades.
    /// OpenZeppelin calls this hook during upgradeToAndCall, and the onlyOwner modifier
    /// ensures that only the configured owner can replace the contract logic.
    function _authorizeUpgrade(address newImplementation) internal view override onlyOwner {
        if (newImplementation == address(0)) revert InvalidAddress();
    }

    /// Stores a validated platform fee value.
    /// The fee is capped at 20 percent, represented as 2000 basis points, and every
    /// change emits an event for auditability.
    function _setPlatformFeeBps(uint16 newPlatformFeeBps) private {
        if (newPlatformFeeBps > 2_000) revert InvalidPlatformFeeBps();
        uint16 previousFeeBps = platformFeeBps;
        platformFeeBps = newPlatformFeeBps;
        emit PlatformFeeBpsUpdated(previousFeeBps, newPlatformFeeBps);
    }

    /// Checks whether the selected payment token is supported.
    /// Native ETH is always allowed through address(0); ERC-20 tokens require explicit
    /// approval by the owner before they can be used in competitions.
    function _ensurePaymentTokenSupported(address paymentToken) private view {
        if (paymentToken == address(0)) {
            return;
        }
        if (!supportedPaymentToken[paymentToken]) {
            revert UnsupportedPaymentToken(paymentToken);
        }
    }

    /// Collects the participant entry fee in native currency or ERC-20 tokens.
    /// Native payments must match the configured fee exactly, while ERC-20 payments are
    /// pulled from the participant after checking that no native value was sent.
    function _collectJoinPayment(Competition storage competition, address payer) private {
        if (competition.paymentToken == address(0)) {
            if (msg.value != competition.entryFeeAmount) revert IncorrectEntryFee();
            return;
        }
        if (msg.value != 0) revert NativeValueNotExpected();
        _pullExactErc20(competition.paymentToken, payer, competition.entryFeeAmount);
    }

    /// Collects a fixed payment amount in native currency or ERC-20 tokens.
    /// This helper is used for creator prize deposits and keeps native and ERC-20
    /// payment validation consistent.
    function _collectPayment(address paymentToken, address payer, uint256 amount) private {
        if (paymentToken == address(0)) {
            if (msg.value != amount) revert IncorrectNativeValue();
            return;
        }
        if (msg.value != 0) revert NativeValueNotExpected();
        _pullExactErc20(paymentToken, payer, amount);
    }

    /// Transfers native currency or ERC-20 tokens from the contract to a recipient.
    /// It skips zero-value transfers, uses call for native ETH, and SafeERC20 for token
    /// transfers to handle non-standard ERC-20 return values.
    function _transferPayment(address paymentToken, address recipient, uint256 amount) private {
        if (amount == 0) {
            return;
        }
        if (paymentToken == address(0)) {
            (bool success,) = payable(recipient).call{value: amount}("");
            if (!success) revert TransferFailed();
            return;
        }
        IERC20(paymentToken).safeTransfer(recipient, amount);
    }

    /// Pulls the exact ERC-20 amount from a payer into this contract.
    /// The balance delta must equal the requested amount, which rejects fee-on-transfer
    /// tokens that would otherwise break escrow accounting.
    function _pullExactErc20(address token, address payer, uint256 amount) private {
        IERC20 erc20 = IERC20(token);
        uint256 balanceBefore = erc20.balanceOf(address(this));
        erc20.safeTransferFrom(payer, address(this), amount);
        uint256 receivedAmount = erc20.balanceOf(address(this)) - balanceBefore;
        if (receivedAmount != amount) revert UnsupportedTokenBehavior(token);
    }

    /// Loads the participant and score for a submission.
    /// New records are stored in the compact submissionScoresById mapping, but the
    /// fallback keeps older records readable after the storage layout was simplified.
    function _loadStoredSubmission(uint256 submissionId) private view returns (StoredSubmission memory) {
        StoredSubmission memory score = submissionScoresById[submissionId];
        if (score.participant != address(0)) {
            return score;
        }

        SubmissionResult memory legacy = resultsBySubmissionId[submissionId];
        if (legacy.submissionId == 0) revert InvalidSubmission();
        return StoredSubmission({
            participant: legacy.participant,
            runtimeMs: legacy.runtimeMs,
            memoryUsedKb: legacy.memoryUsedKb
        });
    }

    /// Checks whether a submission id has already been recorded.
    /// The check covers both the current compact storage and the legacy result storage
    /// so old submissions cannot be overwritten after an upgrade.
    function _submissionRecordExists(uint256 submissionId) private view returns (bool) {
        if (submissionScoresById[submissionId].participant != address(0)) {
            return true;
        }
        return resultsBySubmissionId[submissionId].submissionId != 0;
    }

    /// Builds the digest that the operator signs for a submission result.
    /// The digest includes contract address, chain id, competition id, submission id,
    /// participant wallet, hashes, metrics, and consensus count to prevent replay.
    function _submissionResultDigest(
        uint256 competitionId,
        uint256 submissionId,
        address participant,
        bytes32 submissionHash,
        bytes32 codeHash,
        bytes32 challengeHash,
        bytes32 resultHash,
        bytes32 sandboxImageHash,
        uint32 runtimeMs,
        uint32 memoryUsedKb,
        uint16 consensusNodes
    ) private view returns (bytes32) {
        return keccak256(
            abi.encode(
                address(this),
                block.chainid,
                competitionId,
                submissionId,
                participant,
                submissionHash,
                codeHash,
                challengeHash,
                resultHash,
                sandboxImageHash,
                runtimeMs,
                memoryUsedKb,
                consensusNodes
            )
        );
    }

    /// Recovers the signer address from a raw ECDSA signature.
    /// It accepts only 65-byte signatures, normalizes v when needed, and rejects invalid
    /// recovery results before returning the signer address.
    function _recoverSigner(bytes32 digest, bytes calldata signature) private pure returns (address signer) {
        if (signature.length != 65) revert InvalidSignature();

        bytes32 r;
        bytes32 s;
        uint8 v;

        assembly {
            r := calldataload(signature.offset)
            s := calldataload(add(signature.offset, 32))
            v := byte(0, calldataload(add(signature.offset, 64)))
        }

        if (v < 27) {
            v += 27;
        }
        if (v != 27 && v != 28) revert InvalidSignature();

        signer = ecrecover(digest, v, r, s);
        if (signer == address(0)) revert InvalidSignature();
    }

    /// Verifies that a submission result was signed by the current operator.
    /// The participant address used in the digest is msg.sender, so a valid signature
    /// cannot be reused by another wallet.
    function _verifySubmissionResultSignature(SubmissionResultInput memory input, bytes calldata signature) private view {
        bytes32 digest = _submissionResultDigest(
            input.competitionId,
            input.submissionId,
            msg.sender,
            input.submissionHash,
            input.codeHash,
            input.challengeHash,
            input.resultHash,
            input.sandboxImageHash,
            input.runtimeMs,
            input.memoryUsedKb,
            input.consensusNodes
        );
        address recoveredSigner = _recoverSigner(digest, signature);
        if (recoveredSigner != operator) revert InvalidSignature();
    }

    /// Updates the best submission pointer when a better score is recorded.
    /// If no result exists yet, the current submission becomes best; otherwise the
    /// comparison uses runtime first, memory second, and submission id as a tie-breaker.
    function _updateBestSubmission(uint256 competitionId, uint256 submissionId) private {
        uint256 currentBestSubmissionId = bestSubmissionIdByCompetition[competitionId];
        if (
            currentBestSubmissionId == 0 ||
            _isBetterSubmission(
                submissionId,
                _loadStoredSubmission(submissionId),
                currentBestSubmissionId,
                _loadStoredSubmission(currentBestSubmissionId)
            )
        ) {
            bestSubmissionIdByCompetition[competitionId] = submissionId;
            emit BestSubmissionUpdated(competitionId, submissionId);
        }
    }

    /// Compares two accepted submissions for ranking.
    /// Lower runtime wins, lower memory wins when runtime is equal, and the lower
    /// submission id wins only when both performance metrics are identical.
    function _isBetterSubmission(
        uint256 candidateSubmissionId,
        StoredSubmission memory candidate,
        uint256 currentBestSubmissionId,
        StoredSubmission memory currentBest
    ) private pure returns (bool) {
        if (candidate.runtimeMs != currentBest.runtimeMs) {
            return candidate.runtimeMs < currentBest.runtimeMs;
        }
        if (candidate.memoryUsedKb != currentBest.memoryUsedKb) {
            return candidate.memoryUsedKb < currentBest.memoryUsedKb;
        }
        return candidateSubmissionId < currentBestSubmissionId;
    }

    uint256[42] private __gap;
}
