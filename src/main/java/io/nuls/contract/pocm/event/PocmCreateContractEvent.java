package io.nuls.contract.pocm.event;

import io.nuls.contract.sdk.Event;

import java.math.BigDecimal;
import java.math.BigInteger;

public class PocmCreateContractEvent implements Event {
    private String tokenAddress;
    private int candyAssetChainId;
    private int candyAssetId;
    private BigInteger candyPerBlock;
    private BigInteger candySupply;
    private int lockedTokenDay;
    private BigInteger minimumStaking;
    private boolean openConsensus;
    boolean openAwardConsensusNodeProvider;
    private String authorizationCode;

    public PocmCreateContractEvent(String tokenAddress, int candyAssetChainId, int candyAssetId, BigInteger candyPerBlock, BigInteger candySupply, int lockedTokenDay, BigInteger minimumStaking, boolean openConsensus, boolean openAwardConsensusNodeProvider, String authorizationCode) {
        this.tokenAddress = tokenAddress;
        this.candyAssetChainId = candyAssetChainId;
        this.candyAssetId = candyAssetId;
        this.candyPerBlock = candyPerBlock;
        this.candySupply = candySupply;
        this.lockedTokenDay = lockedTokenDay;
        this.minimumStaking = minimumStaking;
        this.openConsensus = openConsensus;
        this.openAwardConsensusNodeProvider = openAwardConsensusNodeProvider;
        this.authorizationCode = authorizationCode;
    }

    public String getTokenAddress() {
        return tokenAddress;
    }

    public void setTokenAddress(String tokenAddress) {
        this.tokenAddress = tokenAddress;
    }

    public int getCandyAssetChainId() {
        return candyAssetChainId;
    }

    public void setCandyAssetChainId(int candyAssetChainId) {
        this.candyAssetChainId = candyAssetChainId;
    }

    public int getCandyAssetId() {
        return candyAssetId;
    }

    public void setCandyAssetId(int candyAssetId) {
        this.candyAssetId = candyAssetId;
    }

    public BigInteger getCandyPerBlock() {
        return candyPerBlock;
    }

    public void setCandyPerBlock(BigInteger candyPerBlock) {
        this.candyPerBlock = candyPerBlock;
    }

    public BigInteger getCandySupply() {
        return candySupply;
    }

    public void setCandySupply(BigInteger candySupply) {
        this.candySupply = candySupply;
    }

    public int getLockedTokenDay() {
        return lockedTokenDay;
    }

    public void setLockedTokenDay(int lockedTokenDay) {
        this.lockedTokenDay = lockedTokenDay;
    }

    public BigInteger getMinimumStaking() {
        return minimumStaking;
    }

    public void setMinimumStaking(BigInteger minimumStaking) {
        this.minimumStaking = minimumStaking;
    }

    public boolean isOpenConsensus() {
        return openConsensus;
    }

    public void setOpenConsensus(boolean openConsensus) {
        this.openConsensus = openConsensus;
    }

    public boolean isOpenAwardConsensusNodeProvider() {
        return openAwardConsensusNodeProvider;
    }

    public void setOpenAwardConsensusNodeProvider(boolean openAwardConsensusNodeProvider) {
        this.openAwardConsensusNodeProvider = openAwardConsensusNodeProvider;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }
}
