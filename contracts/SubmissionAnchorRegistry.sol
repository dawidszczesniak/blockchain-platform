// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @title SubmissionAnchorRegistry
/// @notice Stores immutable Merkle roots for off-chain submission batches.
contract SubmissionAnchorRegistry {
    event SubmissionBatchAnchored(
        uint256 indexed batchId,
        bytes32 indexed merkleRoot,
        uint256 indexed fromSubmissionId,
        uint256 toSubmissionId,
        uint256 leavesCount,
        address anchoredBy
    );

    mapping(uint256 => bytes32) public batchRootById;
    mapping(uint256 => bool) public batchAnchored;

    error BatchAlreadyAnchored(uint256 batchId);
    error InvalidBatchRange();
    error InvalidLeavesCount();
    error EmptyRoot();

    function anchorSubmissionBatch(
        bytes32 merkleRoot,
        uint256 batchId,
        uint256 fromSubmissionId,
        uint256 toSubmissionId,
        uint256 leavesCount
    ) external {
        if (merkleRoot == bytes32(0)) revert EmptyRoot();
        if (batchAnchored[batchId]) revert BatchAlreadyAnchored(batchId);
        if (fromSubmissionId > toSubmissionId) revert InvalidBatchRange();
        if (leavesCount == 0) revert InvalidLeavesCount();

        batchAnchored[batchId] = true;
        batchRootById[batchId] = merkleRoot;

        emit SubmissionBatchAnchored(
            batchId,
            merkleRoot,
            fromSubmissionId,
            toSubmissionId,
            leavesCount,
            msg.sender
        );
    }
}
