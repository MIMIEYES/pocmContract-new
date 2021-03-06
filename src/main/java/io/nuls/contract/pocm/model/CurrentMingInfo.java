package io.nuls.contract.pocm.model;

import java.math.BigInteger;

/**
 * @author: PierreLuo
 * @date: 2021/9/3
 */
public class CurrentMingInfo {
    //抵押编号
    private long depositNumber;
    // 本次挖矿金额
    private BigInteger miningAmount=BigInteger.ZERO;

    //本次采矿获得的Token的接收地址
    private String receiverMiningAddress;
    // 本次挖矿次数
    private int miningCount;

    public CurrentMingInfo() {
    }

    public CurrentMingInfo(long depositNumber, BigInteger miningAmount, String receiverMiningAddress, int miningCount) {
        this.depositNumber = depositNumber;
        this.miningAmount = miningAmount;
        this.receiverMiningAddress = receiverMiningAddress;
        this.miningCount = miningCount;
    }

    public long getDepositNumber() {
        return depositNumber;
    }

    public void setDepositNumber(long depositNumber) {
        this.depositNumber = depositNumber;
    }

    public BigInteger getMiningAmount() {
        return miningAmount;
    }

    public void setMiningAmount(BigInteger miningAmount) {
        this.miningAmount = miningAmount;
    }

    public String getReceiverMiningAddress() {
        return receiverMiningAddress;
    }

    public void setReceiverMiningAddress(String receiverMiningAddress) {
        this.receiverMiningAddress = receiverMiningAddress;
    }

    public int getMiningCount() {
        return miningCount;
    }

    public void setMiningCount(int miningCount) {
        this.miningCount = miningCount;
    }

    @Override
    public String toString(){
        return "{depositNumber:"+depositNumber+",miningAmount:"+miningAmount.toString()+
                ",receiverMiningAddress:"+receiverMiningAddress
                +",miningCount:"+miningCount+"}";
    }
}
