// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {console2} from "forge-std/console2.sol";
import {Script} from "forge-std/Script.sol";
import {Options} from "openzeppelin-foundry-upgrades/Options.sol";
import {Upgrades} from "openzeppelin-foundry-upgrades/Upgrades.sol";

contract UpgradeBlockchainTestContract is Script {
    string internal constant CONTRACT_NAME = "BlockchainTestContractV4.sol:BlockchainTestContractV4";
    string internal constant REFERENCE_CONTRACT = "BlockchainTestContractV3.sol:BlockchainTestContractV3";

    function run() external returns (address implementation) {
        address proxy = proxyAddress();
        Options memory opts;
        opts.referenceContract = REFERENCE_CONTRACT;

        vm.startBroadcast(walletPrivateKey());
        Upgrades.upgradeProxy(proxy, CONTRACT_NAME, "", opts);
        vm.stopBroadcast();

        implementation = Upgrades.getImplementationAddress(proxy);

        console2.log("Proxy:", proxy);
        console2.log("Implementation:", implementation);
    }

    function walletPrivateKey() internal view returns (uint256) {
        return envUintRequired("ETH_PLATFORM_OPERATOR_PRIVATE_KEY");
    }

    function proxyAddress() internal view returns (address) {
        return vm.envAddress("ETH_PLATFORM_PROXY_ADDRESS");
    }

    function envString(string memory key) internal view returns (string memory) {
        return vm.envOr(key, string(""));
    }

    function envUintRequired(string memory key) internal view returns (uint256) {
        string memory raw = envString(key);
        require(bytes(raw).length > 0, string.concat(key, " must be configured."));
        return vm.parseUint(normalizeUintString(raw));
    }

    function normalizeUintString(string memory raw) internal pure returns (string memory) {
        bytes memory value = bytes(raw);
        if (value.length >= 2 && value[0] == "0" && (value[1] == "x" || value[1] == "X")) {
            return raw;
        }
        if (_isDecimalString(value)) {
            return raw;
        }
        return string.concat("0x", raw);
    }

    function _isDecimalString(bytes memory value) internal pure returns (bool) {
        for (uint256 index = 0; index < value.length; index++) {
            bytes1 char = value[index];
            if (char < "0" || char > "9") {
                return false;
            }
        }
        return value.length > 0;
    }
}
