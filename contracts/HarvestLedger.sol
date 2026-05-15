// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

/**
 * @title HarvestLedger
 * @notice Immutable on-chain record of farm harvests for traceability.
 *         Deployed on Sepolia testnet via Alchemy.
 */
contract HarvestLedger {

    struct Harvest {
        uint256 recordId;
        string  cropName;
        uint256 quantityGrams;   // kg * 1000 for precision
        string  grade;           // A, B, or C
        uint256 plotId;
        uint256 harvestDate;     // unix epoch (seconds)
        uint256 blockTimestamp;  // when recorded on-chain
    }

    address public owner;
    uint256 public harvestCount;

    mapping(uint256 => Harvest) public harvests;     // recordId => Harvest
    mapping(uint256 => bool)    public recorded;     // recordId => exists

    event HarvestRecorded(
        uint256 indexed recordId,
        string  cropName,
        uint256 quantityGrams,
        string  grade,
        uint256 plotId,
        uint256 harvestDate
    );

    modifier onlyOwner() {
        require(msg.sender == owner, "Not authorized");
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    /**
     * @notice Store a harvest record on-chain. Each recordId can only be recorded once.
     */
    function recordHarvest(
        uint256 _recordId,
        string  calldata _cropName,
        uint256 _quantityGrams,
        string  calldata _grade,
        uint256 _plotId,
        uint256 _harvestDate
    ) external onlyOwner {
        require(!recorded[_recordId], "Already recorded");

        harvests[_recordId] = Harvest({
            recordId:       _recordId,
            cropName:       _cropName,
            quantityGrams:  _quantityGrams,
            grade:          _grade,
            plotId:         _plotId,
            harvestDate:    _harvestDate,
            blockTimestamp: block.timestamp
        });
        recorded[_recordId] = true;
        harvestCount++;

        emit HarvestRecorded(_recordId, _cropName, _quantityGrams, _grade, _plotId, _harvestDate);
    }

    /**
     * @notice Verify a harvest record exists on-chain and return its data.
     */
    function getHarvest(uint256 _recordId) external view returns (
        string  memory cropName,
        uint256 quantityGrams,
        string  memory grade,
        uint256 plotId,
        uint256 harvestDate,
        uint256 blockTimestamp
    ) {
        require(recorded[_recordId], "Not found");
        Harvest storage h = harvests[_recordId];
        return (h.cropName, h.quantityGrams, h.grade, h.plotId, h.harvestDate, h.blockTimestamp);
    }
}
