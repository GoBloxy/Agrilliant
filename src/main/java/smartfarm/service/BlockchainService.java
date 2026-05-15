package smartfarm.service;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.io.InputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Service for recording harvest data on the Ethereum Sepolia testnet
 * via the HarvestLedger smart contract using Alchemy as the RPC provider.
 */
public class BlockchainService {

    private Web3j web3j;
    private Credentials credentials;
    private String contractAddress;
    private boolean enabled;
    private long chainId = 11155111L; // Sepolia

    private static BlockchainService instance;

    private BlockchainService() {
        loadConfig();
    }

    public static synchronized BlockchainService getInstance() {
        if (instance == null) {
            instance = new BlockchainService();
        }
        return instance;
    }

    private void loadConfig() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("blockchain.properties")) {
            if (is == null) {
                System.err.println("[Blockchain] blockchain.properties not found — disabled");
                enabled = false;
                return;
            }
            Properties props = new Properties();
            props.load(is);

            String rpcUrl = props.getProperty("blockchain.rpc.url", "");
            String privateKey = props.getProperty("blockchain.private.key", "");
            contractAddress = props.getProperty("blockchain.contract.address", "");
            enabled = Boolean.parseBoolean(props.getProperty("blockchain.enabled", "false"));

            if (!enabled) {
                System.out.println("[Blockchain] Blockchain recording is disabled");
                return;
            }

            if (rpcUrl.contains("YOUR_") || privateKey.contains("YOUR_") || contractAddress.contains("YOUR_")) {
                System.err.println("[Blockchain] Config contains placeholder values — disabled");
                enabled = false;
                return;
            }

            web3j = Web3j.build(new HttpService(rpcUrl));
            credentials = Credentials.create(privateKey);
            System.out.println("[Blockchain] Connected to Sepolia via Alchemy");
            System.out.println("[Blockchain] Wallet: " + credentials.getAddress());
            System.out.println("[Blockchain] Contract: " + contractAddress);
        } catch (Exception e) {
            System.err.println("[Blockchain] Failed to initialize: " + e.getMessage());
            enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Record a harvest on-chain. Returns the transaction hash, or null on failure.
     *
     * @param recordId    the database record ID
     * @param cropName    name of the crop
     * @param quantityKg  harvest quantity in kg
     * @param grade       quality grade (A/B/C)
     * @param plotId      the plot ID
     * @param harvestDate the harvest date
     * @return transaction hash (0x...) or null
     */
    public String recordHarvest(int recordId, String cropName, double quantityKg,
                                 String grade, int plotId, LocalDate harvestDate) {
        if (!enabled) return null;

        try {
            // Convert kg to grams (integer) for on-chain precision
            BigInteger quantityGrams = BigInteger.valueOf(Math.round(quantityKg * 1000));
            BigInteger harvestEpoch = BigInteger.valueOf(
                    harvestDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond());

            // Build the function call: recordHarvest(uint256,string,uint256,string,uint256,uint256)
            Function function = new Function(
                    "recordHarvest",
                    Arrays.asList(
                            new Uint256(BigInteger.valueOf(recordId)),
                            new Utf8String(cropName),
                            new Uint256(quantityGrams),
                            new Utf8String(grade),
                            new Uint256(BigInteger.valueOf(plotId)),
                            new Uint256(harvestEpoch)
                    ),
                    Collections.emptyList()
            );
            String encodedFunction = FunctionEncoder.encode(function);

            // Get nonce
            EthGetTransactionCount nonceResp = web3j.ethGetTransactionCount(
                    credentials.getAddress(), DefaultBlockParameterName.PENDING).send();
            BigInteger nonce = nonceResp.getTransactionCount();

            // Get gas price
            EthGasPrice gasPriceResp = web3j.ethGasPrice().send();
            BigInteger gasPrice = gasPriceResp.getGasPrice();

            // Estimate gas (with a safety margin)
            BigInteger gasLimit = BigInteger.valueOf(300_000);

            // Build raw transaction
            RawTransaction rawTx = RawTransaction.createTransaction(
                    nonce, gasPrice, gasLimit, contractAddress,
                    BigInteger.ZERO, encodedFunction);

            // Sign and send
            byte[] signedMessage = TransactionEncoder.signMessage(rawTx, chainId, credentials);
            String hexValue = Numeric.toHexString(signedMessage);

            EthSendTransaction txResponse = web3j.ethSendRawTransaction(hexValue).send();

            if (txResponse.hasError()) {
                System.err.println("[Blockchain] TX error: " + txResponse.getError().getMessage());
                return null;
            }

            String txHash = txResponse.getTransactionHash();
            System.out.println("[Blockchain] Harvest #" + recordId + " recorded — TX: " + txHash);
            return txHash;

        } catch (Exception e) {
            System.err.println("[Blockchain] Failed to record harvest #" + recordId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Verify that a harvest record exists on-chain by calling getHarvest(recordId).
     * Returns true if the record is found, false otherwise.
     */
    public boolean verifyHarvest(int recordId) {
        if (!enabled) return false;

        try {
            Function function = new Function(
                    "getHarvest",
                    Arrays.asList(new Uint256(BigInteger.valueOf(recordId))),
                    Arrays.asList(
                            new TypeReference<Utf8String>() {},  // cropName
                            new TypeReference<Uint256>() {},     // quantityGrams
                            new TypeReference<Utf8String>() {},  // grade
                            new TypeReference<Uint256>() {},     // plotId
                            new TypeReference<Uint256>() {},     // harvestDate
                            new TypeReference<Uint256>() {}      // blockTimestamp
                    )
            );
            String encodedFunction = FunctionEncoder.encode(function);

            org.web3j.protocol.core.methods.request.Transaction ethCall =
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            credentials.getAddress(), contractAddress, encodedFunction);

            EthCall response = web3j.ethCall(ethCall, DefaultBlockParameterName.LATEST).send();

            if (response.hasError()) {
                return false;
            }

            List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            return !decoded.isEmpty();

        } catch (Exception e) {
            System.err.println("[Blockchain] Verify failed for #" + recordId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the Etherscan Sepolia URL for a given transaction hash.
     */
    public static String getEtherscanUrl(String txHash) {
        return "https://sepolia.etherscan.io/tx/" + txHash;
    }

    public void shutdown() {
        if (web3j != null) {
            web3j.shutdown();
        }
    }
}
