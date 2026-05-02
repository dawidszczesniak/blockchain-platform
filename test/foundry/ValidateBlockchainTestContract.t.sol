// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {Upgrades} from "openzeppelin-foundry-upgrades/Upgrades.sol";
import {Options} from "openzeppelin-foundry-upgrades/Options.sol";

contract ValidateBlockchainTestContractTest is Test {
    string internal constant CONTRACT_NAME = "BlockchainTestContract.sol:BlockchainTestContract";

    function test_validateBlockchainTestContractImplementation() external {
        Options memory opts;
        Upgrades.validateImplementation(CONTRACT_NAME, opts);
    }
}
