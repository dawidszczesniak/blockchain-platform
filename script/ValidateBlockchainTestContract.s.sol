// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {console2} from "forge-std/console2.sol";
import {Script} from "forge-std/Script.sol";
import {Upgrades} from "openzeppelin-foundry-upgrades/Upgrades.sol";
import {Options} from "openzeppelin-foundry-upgrades/Options.sol";

contract ValidateBlockchainTestContract is Script {
    string internal constant CONTRACT_NAME = "BlockchainTestContract.sol:BlockchainTestContract";

    function run() external {
        Options memory opts;
        Upgrades.validateImplementation(CONTRACT_NAME, opts);
        console2.log("BlockchainTestContract passed OpenZeppelin UUPS implementation validation.");
    }
}
