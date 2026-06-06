// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {ERC1967Proxy} from "@openzeppelin/contracts/proxy/ERC1967/ERC1967Proxy.sol";
import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {Test} from "forge-std/Test.sol";
import {BlockchainTestContract} from "../contracts/BlockchainTestContract.sol";

contract BlockchainTestContractTest is Test {
    struct SubmissionCall {
        bytes32 submissionHash;
        bytes32 codeHash;
        bytes32 challengeHash;
        bytes32 resultHash;
        uint32 runtimeMs;
        uint32 memoryUsedKb;
        bytes signature;
    }

    BlockchainTestContract private platform;
    MockERC20 private usdc;
    FeeOnTransferMockERC20 private feeToken;

    address private constant OWNER = address(0x1001);
    address private constant TREASURY = address(0x1003);
    address private constant CREATOR = address(0x1004);
    address private constant ALICE = address(0x1005);
    address private constant BOB = address(0x1006);
    uint256 private constant OPERATOR_PRIVATE_KEY = uint256(0xA11CE);

    address private OPERATOR;

    uint256 private constant ETH_PRIZE = 1 ether;
    uint256 private constant ETH_ENTRY_FEE = 0.1 ether;
    uint256 private constant USDC_PRIZE = 100e6;
    uint256 private constant USDC_ENTRY_FEE = 10e6;
    bytes32 private constant APPROVED_SANDBOX_HASH = bytes32(uint256(0xA11CE));

    function setUp() external {
        vm.warp(1);
        OPERATOR = vm.addr(OPERATOR_PRIVATE_KEY);

        usdc = new MockERC20("USD Coin", "USDC", 6);
        feeToken = new FeeOnTransferMockERC20();

        BlockchainTestContract implementation = new BlockchainTestContract();
        ERC1967Proxy proxy = new ERC1967Proxy(
            address(implementation),
            abi.encodeCall(
                BlockchainTestContract.initialize,
                (OWNER, OPERATOR, TREASURY, 500, APPROVED_SANDBOX_HASH, address(usdc))
            )
        );
        platform = BlockchainTestContract(address(proxy));

        vm.deal(CREATOR, 10 ether);
        vm.deal(ALICE, 10 ether);
        vm.deal(BOB, 10 ether);

        usdc.mint(CREATOR, 1_000_000e6);
        usdc.mint(ALICE, 1_000_000e6);
        usdc.mint(BOB, 1_000_000e6);

        feeToken.mint(CREATOR, 1_000_000 ether);

        vm.prank(OWNER);
        platform.setSupportedPaymentToken(address(feeToken), true);
    }

    function test_settleCompetition_allowsAnyCallerAfterDeadlineForNativeEth() external {
        uint256 competitionId = createNativeCompetition(bytes32(uint256(1)), 10, 20, 2);

        joinNativeCompetition(competitionId, ALICE);
        joinNativeCompetition(competitionId, BOB);

        recordSubmission(competitionId, 101, ALICE, 150, 512);
        recordSubmission(competitionId, 102, BOB, 120, 768);

        uint256 creatorBalanceBefore = CREATOR.balance;
        uint256 winnerBalanceBefore = BOB.balance;
        uint256 treasuryBalanceBefore = TREASURY.balance;

        vm.warp(22);
        vm.prank(ALICE);
        platform.settleCompetition(competitionId);

        (
            ,
            ,
            address winner,
            ,
            ,
            ,
            ,
            ,
            ,
            ,
            BlockchainTestContract.CompetitionState state
        ) = platform.getCompetition(competitionId);

        assertEq(winner, BOB);
        assertEq(uint256(state), uint256(BlockchainTestContract.CompetitionState.Settled));
        assertEq(winnerBalanceBefore + ETH_PRIZE, BOB.balance);
        assertEq(creatorBalanceBefore + 0.19 ether, CREATOR.balance);
        assertEq(treasuryBalanceBefore + 0.01 ether, TREASURY.balance);
    }

    function test_settleCompetition_allowsAnyCallerAfterDeadlineForErc20() external {
        uint256 competitionId = createUsdcCompetition(bytes32(uint256(2)), 10, 20, 2);

        joinUsdcCompetition(competitionId, ALICE);
        joinUsdcCompetition(competitionId, BOB);

        recordSubmission(competitionId, 201, ALICE, 220, 700);
        recordSubmission(competitionId, 202, BOB, 180, 680);

        uint256 creatorBalanceBefore = usdc.balanceOf(CREATOR);
        uint256 winnerBalanceBefore = usdc.balanceOf(BOB);
        uint256 treasuryBalanceBefore = usdc.balanceOf(TREASURY);

        vm.warp(22);
        vm.prank(ALICE);
        platform.settleCompetition(competitionId);

        (
            ,
            ,
            address winner,
            address paymentToken,
            ,
            ,
            ,
            ,
            ,
            ,
            BlockchainTestContract.CompetitionState state
        ) = platform.getCompetition(competitionId);

        assertEq(winner, BOB);
        assertEq(paymentToken, address(usdc));
        assertEq(uint256(state), uint256(BlockchainTestContract.CompetitionState.Settled));
        assertEq(winnerBalanceBefore + USDC_PRIZE, usdc.balanceOf(BOB));
        assertEq(creatorBalanceBefore + 19e6, usdc.balanceOf(CREATOR));
        assertEq(treasuryBalanceBefore + 1e6, usdc.balanceOf(TREASURY));
    }

    function test_createCompetition_rejectsFeeOnTransferToken() external {
        uint64 joinDeadline = uint64(block.timestamp + 10);
        uint64 submitDeadline = uint64(block.timestamp + 20);
        uint256 prizeAmount = 100 ether;

        vm.startPrank(CREATOR);
        feeToken.approve(address(platform), prizeAmount);
        vm.expectRevert(
            abi.encodeWithSelector(
                BlockchainTestContract.UnsupportedTokenBehavior.selector,
                address(feeToken)
            )
        );
        platform.createCompetition(
            bytes32(uint256(3)),
            address(feeToken),
            prizeAmount,
            10 ether,
            joinDeadline,
            submitDeadline,
            2
        );
        vm.stopPrank();
    }

    function test_cancelCompetition_revertsBeforeDeadlineAndAllowsAnyCallerAfterJoinDeadlineFailure() external {
        uint256 competitionId = createNativeCompetition(bytes32(uint256(4)), 10, 20, 2);

        joinNativeCompetition(competitionId, ALICE);

        vm.prank(BOB);
        vm.expectRevert(BlockchainTestContract.CompetitionNotReadyForCancellation.selector);
        platform.cancelCompetition(competitionId);

        vm.warp(12);
        vm.prank(BOB);
        platform.cancelCompetition(competitionId);

        (
            ,
            ,
            ,
            ,
            ,
            ,
            ,
            ,
            ,
            ,
            BlockchainTestContract.CompetitionState state
        ) = platform.getCompetition(competitionId);

        assertEq(uint256(state), uint256(BlockchainTestContract.CompetitionState.Cancelled));
    }

    function createNativeCompetition(
        bytes32 competitionKey,
        uint64 joinDelaySeconds,
        uint64 submitDelaySeconds,
        uint32 requiredParticipants
    ) private returns (uint256 competitionId) {
        vm.prank(CREATOR);
        competitionId = platform.createCompetition{value: ETH_PRIZE}(
            competitionKey,
            address(0),
            ETH_PRIZE,
            ETH_ENTRY_FEE,
            uint64(block.timestamp + joinDelaySeconds),
            uint64(block.timestamp + submitDelaySeconds),
            requiredParticipants
        );
    }

    function createUsdcCompetition(
        bytes32 competitionKey,
        uint64 joinDelaySeconds,
        uint64 submitDelaySeconds,
        uint32 requiredParticipants
    ) private returns (uint256 competitionId) {
        vm.startPrank(CREATOR);
        usdc.approve(address(platform), USDC_PRIZE);
        competitionId = platform.createCompetition(
            competitionKey,
            address(usdc),
            USDC_PRIZE,
            USDC_ENTRY_FEE,
            uint64(block.timestamp + joinDelaySeconds),
            uint64(block.timestamp + submitDelaySeconds),
            requiredParticipants
        );
        vm.stopPrank();
    }

    function joinNativeCompetition(uint256 competitionId, address participant) private {
        vm.prank(participant);
        platform.joinCompetition{value: ETH_ENTRY_FEE}(competitionId);
    }

    function joinUsdcCompetition(uint256 competitionId, address participant) private {
        vm.startPrank(participant);
        usdc.approve(address(platform), USDC_ENTRY_FEE);
        platform.joinCompetition(competitionId);
        vm.stopPrank();
    }

    function recordSubmission(
        uint256 competitionId,
        uint256 submissionId,
        address participant,
        uint32 runtimeMs,
        uint32 memoryUsedKb
    ) private {
        SubmissionCall memory submission = buildSubmissionCall(
            competitionId,
            submissionId,
            participant,
            runtimeMs,
            memoryUsedKb
        );

        vm.prank(participant);
        platform.recordSubmissionResult(
            competitionId,
            submissionId,
            submission.submissionHash,
            submission.codeHash,
            submission.challengeHash,
            submission.resultHash,
            APPROVED_SANDBOX_HASH,
            submission.runtimeMs,
            submission.memoryUsedKb,
            3,
            submission.signature
        );
    }

    function buildSubmissionCall(
        uint256 competitionId,
        uint256 submissionId,
        address participant,
        uint32 runtimeMs,
        uint32 memoryUsedKb
    ) private view returns (SubmissionCall memory) {
        uint256 base = submissionId * 10;
        bytes32 submissionHash = bytes32(base + 1);
        bytes32 codeHash = bytes32(base + 2);
        bytes32 challengeHash = bytes32(base + 3);
        bytes32 resultHash = bytes32(base + 4);
        return SubmissionCall({
            submissionHash: submissionHash,
            codeHash: codeHash,
            challengeHash: challengeHash,
            resultHash: resultHash,
            runtimeMs: runtimeMs,
            memoryUsedKb: memoryUsedKb,
            signature: signSubmissionResult(
                competitionId,
                submissionId,
                participant,
                runtimeMs,
                memoryUsedKb
            )
        });
    }

    function signSubmissionResult(
        uint256 competitionId,
        uint256 submissionId,
        address participant,
        uint32 runtimeMs,
        uint32 memoryUsedKb
    ) private view returns (bytes memory) {
        uint256 base = submissionId * 10;
        bytes32 digest = keccak256(
            abi.encode(
                address(platform),
                block.chainid,
                competitionId,
                submissionId,
                participant,
                bytes32(base + 1),
                bytes32(base + 2),
                bytes32(base + 3),
                bytes32(base + 4),
                APPROVED_SANDBOX_HASH,
                runtimeMs,
                memoryUsedKb,
                uint16(3)
            )
        );
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(OPERATOR_PRIVATE_KEY, digest);
        return abi.encodePacked(r, s, v);
    }
}

contract MockERC20 is ERC20 {
    uint8 private immutable customDecimals;

    constructor(string memory name_, string memory symbol_, uint8 decimals_) ERC20(name_, symbol_) {
        customDecimals = decimals_;
    }

    function decimals() public view override returns (uint8) {
        return customDecimals;
    }

    function mint(address account, uint256 amount) external {
        _mint(account, amount);
    }
}

contract FeeOnTransferMockERC20 is MockERC20 {
    address internal constant FEE_COLLECTOR = address(0xFEE);

    constructor() MockERC20("Fee Token", "FEE", 18) {}

    function _update(address from, address to, uint256 value) internal override {
        if (from == address(0) || to == address(0) || value == 0) {
            super._update(from, to, value);
            return;
        }

        uint256 fee = value / 100;
        if (fee == 0) {
            fee = 1;
        }

        super._update(from, FEE_COLLECTOR, fee);
        super._update(from, to, value - fee);
    }
}
