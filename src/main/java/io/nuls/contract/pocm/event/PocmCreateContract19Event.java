package io.nuls.contract.pocm.event;

import io.nuls.contract.sdk.Event;

import java.math.BigInteger;

public class PocmCreateContract19Event implements Event {
    private String tokenAddress;
    private int candyAssetChainId;
    private int candyAssetId;
    private BigInteger candyPerBlock;
    private BigInteger candySupply;
    private int lockedTokenDay;
    private BigInteger minimumStaking;
    private BigInteger maximumStaking;
    private boolean openConsensus;
    boolean openAwardConsensusNodeProvider;
    private String authorizationCode;
    private int operatingModel;
    private int rewardDrawRatioForLp;

}
