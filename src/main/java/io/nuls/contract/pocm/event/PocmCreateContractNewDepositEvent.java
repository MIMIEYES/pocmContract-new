package io.nuls.contract.pocm.event;

import io.nuls.contract.sdk.Event;

import java.math.BigInteger;

public class PocmCreateContractNewDepositEvent implements Event {
    private String depositAddress;
    private int depositAssetChainId;
    private int depositAssetId;
    private String tokenAddress;
    private int candyAssetChainId;
    private int candyAssetId;
    private BigInteger candyPerBlock;
    private BigInteger candySupply;
    private int lockedTokenDay;
    private BigInteger minimumStaking;
    private BigInteger maximumStaking;
    private String authorizationCode;

    public PocmCreateContractNewDepositEvent(String depositAddress, int depositAssetChainId, int depositAssetId, String tokenAddress, int candyAssetChainId, int candyAssetId, BigInteger candyPerBlock, BigInteger candySupply, int lockedTokenDay, BigInteger minimumStaking, BigInteger maximumStaking, String authorizationCode) {
        this.depositAddress = depositAddress;
        this.depositAssetChainId = depositAssetChainId;
        this.depositAssetId = depositAssetId;
        this.tokenAddress = tokenAddress;
        this.candyAssetChainId = candyAssetChainId;
        this.candyAssetId = candyAssetId;
        this.candyPerBlock = candyPerBlock;
        this.candySupply = candySupply;
        this.lockedTokenDay = lockedTokenDay;
        this.minimumStaking = minimumStaking;
        this.maximumStaking = maximumStaking;
        this.authorizationCode = authorizationCode;
    }
}
