// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @title SubmissionAnchorRegistry
/// @notice Stores immutable commitment hashes for individual submissions.
contract SubmissionAnchorRegistry {
    event SubmissionAnchored(
        uint256 indexed submissionId,
        bytes32 indexed commitmentHash,
        address anchoredBy
    );

    mapping(uint256 => bytes32) public commitmentBySubmissionId;
    mapping(uint256 => bool) public submissionAnchored;

    error SubmissionAlreadyAnchored(uint256 submissionId);
    error EmptyCommitment();

    function anchorSubmission(bytes32 commitmentHash, uint256 submissionId) external {
        if (commitmentHash == bytes32(0)) revert EmptyCommitment();
        if (submissionAnchored[submissionId]) revert SubmissionAlreadyAnchored(submissionId);

        submissionAnchored[submissionId] = true;
        commitmentBySubmissionId[submissionId] = commitmentHash;

        emit SubmissionAnchored(submissionId, commitmentHash, msg.sender);
    }
}
