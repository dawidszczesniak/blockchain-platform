// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {Vm} from "forge-std/Vm.sol";
import {Upgrades} from "openzeppelin-foundry-upgrades/Upgrades.sol";
import {Options} from "openzeppelin-foundry-upgrades/Options.sol";

contract ValidateBlockchainTestContractTest is Test {
    string internal constant CONTRACT_NAME = "BlockchainTestContract.sol:BlockchainTestContract";

    function test_validateBlockchainTestContractImplementation() external {
        _removeStaleBuildInfo();

        Options memory opts;
        Upgrades.validateImplementation(CONTRACT_NAME, opts);
    }

    function _removeStaleBuildInfo() private {
        Vm.DirEntry[] memory entries = vm.readDir("out/build-info", 1);
        string memory newestPath;
        uint256 newestModified;

        for (uint256 index = 0; index < entries.length; index++) {
            Vm.DirEntry memory entry = entries[index];
            if (entry.isDir || entry.isSymlink || !_endsWithJson(entry.path)) {
                continue;
            }

            uint256 modified = vm.fsMetadata(entry.path).modified;
            if (bytes(newestPath).length == 0 || modified > newestModified) {
                newestPath = entry.path;
                newestModified = modified;
            }
        }

        bytes32 newestHash = keccak256(bytes(newestPath));
        for (uint256 index = 0; index < entries.length; index++) {
            Vm.DirEntry memory entry = entries[index];
            if (entry.isDir || entry.isSymlink || !_endsWithJson(entry.path)) {
                continue;
            }

            if (keccak256(bytes(entry.path)) != newestHash) {
                vm.removeFile(entry.path);
            }
        }
    }

    function _endsWithJson(string memory path) private pure returns (bool) {
        bytes memory pathBytes = bytes(path);
        if (pathBytes.length < 5) {
            return false;
        }

        uint256 offset = pathBytes.length - 5;
        return pathBytes[offset] == 0x2e && pathBytes[offset + 1] == 0x6a && pathBytes[offset + 2] == 0x73
            && pathBytes[offset + 3] == 0x6f && pathBytes[offset + 4] == 0x6e;
    }
}
