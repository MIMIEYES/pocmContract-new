package io.nuls.contract.pocm.event;


import io.nuls.contract.sdk.Event;

import java.math.BigInteger;

/**
 * @author: PierreLuo
 * @date: 2021/9/3
 */
public class DepositDetailInfoEvent implements Event {
    private BigInteger depositValue;
    private long depositNumber;
    private BigInteger depositAmount;
    private BigInteger availableAmount;
    private BigInteger lockedAmount;
    private long depositHeight;
    private String miningAddress;

    public DepositDetailInfoEvent() {
    }

    public DepositDetailInfoEvent(BigInteger depositValue, long depositNumber, BigInteger depositAmount, BigInteger availableAmount, BigInteger lockedAmount, long depositHeight, String miningAddress) {
        this.depositValue = depositValue;
        this.depositNumber = depositNumber;
        this.depositAmount = depositAmount;
        this.availableAmount = availableAmount;
        this.lockedAmount = lockedAmount;
        this.depositHeight = depositHeight;
        this.miningAddress = miningAddress;
    }

    public BigInteger getDepositValue() {
        return depositValue;
    }

    public void setDepositValue(BigInteger depositValue) {
        this.depositValue = depositValue;
    }

    public long getDepositNumber() {
        return depositNumber;
    }

    public void setDepositNumber(long depositNumber) {
        this.depositNumber = depositNumber;
    }

    public BigInteger getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigInteger depositAmount) {
        this.depositAmount = depositAmount;
    }

    public BigInteger getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(BigInteger availableAmount) {
        this.availableAmount = availableAmount;
    }

    public BigInteger getLockedAmount() {
        return lockedAmount;
    }

    public void setLockedAmount(BigInteger lockedAmount) {
        this.lockedAmount = lockedAmount;
    }

    public long getDepositHeight() {
        return depositHeight;
    }

    public void setDepositHeight(long depositHeight) {
        this.depositHeight = depositHeight;
    }

    public String getMiningAddress() {
        return miningAddress;
    }

    public void setMiningAddress(String miningAddress) {
        this.miningAddress = miningAddress;
    }
}
