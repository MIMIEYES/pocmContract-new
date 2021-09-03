package io.nuls.contract.pocm.event;


import io.nuls.contract.sdk.Event;

import java.util.List;

/**
 * @author: PierreLuo
 * @date: 2021/9/3
 */
public class QuitDepositEvent implements Event {
    private List<Long> depositNumbers;
    private String  depositorAddress;

    public QuitDepositEvent() {
    }

    public QuitDepositEvent(List<Long> depositNumbers, String depositorAddress) {
        this.depositNumbers = depositNumbers;
        this.depositorAddress = depositorAddress;
    }

    public List<Long> getDepositNumbers() {
        return depositNumbers;
    }

    public void setDepositNumbers(List<Long> depositNumbers) {
        this.depositNumbers = depositNumbers;
    }

    public String getDepositorAddress() {
        return depositorAddress;
    }

    public void setDepositorAddress(String depositorAddress) {
        this.depositorAddress = depositorAddress;
    }
}
