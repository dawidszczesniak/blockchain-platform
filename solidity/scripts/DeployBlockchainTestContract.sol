// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {console2} from "forge-std/console2.sol";
import {Script} from "forge-std/Script.sol";
import {Upgrades} from "openzeppelin-foundry-upgrades/Upgrades.sol";
import {BlockchainTestContractV4} from "../contracts/BlockchainTestContractV4.sol";

contract DeployBlockchainTestContract is Script {
    string internal constant CONTRACT_NAME = "BlockchainTestContractV4.sol:BlockchainTestContractV4";

    function run() external returns (address proxy, address implementation) {
        bytes memory initializerData = abi.encodeCall(
                BlockchainTestContractV4.initialize,
            (
                initialOwner(),
                initialOperator(),
                initialTreasury(),
                initialPlatformFeeBps(),
                initialApprovedSandboxImageHash(),
                initialSupportedPaymentToken()
            )
        );

        vm.startBroadcast(walletPrivateKey());
        proxy = Upgrades.deployUUPSProxy(CONTRACT_NAME, initializerData);
        vm.stopBroadcast();

        implementation = Upgrades.getImplementationAddress(proxy);

        console2.log("Wallet:", walletAddress());
        console2.log("Proxy:", proxy);
        console2.log("Implementation:", implementation);
        console2.log("Store proxy as ETH_PLATFORM_PROXY_ADDRESS.");
    }

    function walletPrivateKey() internal view returns (uint256) {
        return envUintRequired("ETH_PLATFORM_OPERATOR_PRIVATE_KEY");
    }

    function walletAddress() internal view returns (address) {
        return vm.addr(walletPrivateKey());
    }

    function initialOwner() internal view returns (address) {
        return walletAddress();
    }

    function initialOperator() internal view returns (address) {
        return walletAddress();
    }

    function initialTreasury() internal view returns (address) {
        return walletAddress();
    }

    function initialPlatformFeeBps() internal pure returns (uint16) {
        return 500;
    }

    function initialApprovedSandboxImageHash() internal view returns (bytes32) {
        return bytes32(envUintOr("SANDBOX_IMAGE_HASH", 0));
    }

    function initialSupportedPaymentToken() internal view returns (address) {
        address explicitToken = envAddressOr("ETH_PLATFORM_INITIAL_SUPPORTED_PAYMENT_TOKEN", address(0));
        if (explicitToken != address(0)) {
            return explicitToken;
        }

        string memory configuredTokens = envString("ETH_PLATFORM_SUPPORTED_ERC20_TOKENS");
        if (bytes(configuredTokens).length == 0) {
            return address(0);
        }

        string[] memory entries = vm.split(configuredTokens, ";");
        for (uint256 index = 0; index < entries.length; index++) {
            if (bytes(entries[index]).length == 0) {
                continue;
            }
            string[] memory parts = vm.split(entries[index], "|");
            require(
                parts.length == 5,
                "ETH_PLATFORM_SUPPORTED_ERC20_TOKENS entries must use CODE|DISPLAY_NAME|SYMBOL|DECIMALS|ADDRESS."
            );
            if (_equals(parts[0], "USDC")) {
                return vm.parseAddress(parts[4]);
            }
        }

        return address(0);
    }

    function envString(string memory key) internal view returns (string memory) {
        return vm.envOr(key, string(""));
    }

    function envUintRequired(string memory key) internal view returns (uint256) {
        string memory raw = envString(key);
        require(bytes(raw).length > 0, string.concat(key, " must be configured."));
        return vm.parseUint(normalizeUintString(raw));
    }

    function envUintOr(string memory key, uint256 defaultValue) internal view returns (uint256) {
        string memory raw = envString(key);
        if (bytes(raw).length == 0) {
            return defaultValue;
        }
        return vm.parseUint(normalizeUintString(raw));
    }

    function envAddressOr(string memory key, address defaultValue) internal view returns (address) {
        string memory raw = envString(key);
        if (bytes(raw).length == 0) {
            return defaultValue;
        }
        return vm.parseAddress(raw);
    }

    function _equals(string memory left, string memory right) internal pure returns (bool) {
        return keccak256(bytes(left)) == keccak256(bytes(right));
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
