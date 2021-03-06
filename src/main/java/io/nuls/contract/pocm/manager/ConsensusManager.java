/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2018 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.contract.pocm.manager;

import io.nuls.contract.pocm.PocmContract;
import io.nuls.contract.pocm.event.PocmRemoveAgentEvent;
import io.nuls.contract.pocm.manager.deposit.DepositOthersManager;
import io.nuls.contract.pocm.model.ConsensusAgentDepositInfo;
import io.nuls.contract.pocm.model.ConsensusAwardInfo;
import io.nuls.contract.pocm.model.UserInfo;
import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Msg;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

import static io.nuls.contract.pocm.util.PocmUtil.toNuls;
import static io.nuls.contract.sdk.Utils.emit;
import static io.nuls.contract.sdk.Utils.require;

/**
 * @author: PierreLuo
 * @date: 2019-05-14
 */
public class ConsensusManager {
    // 2K
    private BigInteger MIN_JOIN_DEPOSIT = BigInteger.valueOf(200000000000L);
    // 50W
    public static final BigInteger MAX_TOTAL_DEPOSIT = BigInteger.valueOf(50000000000000L);

    public static final String ACTIVE_AGENT = "1";
    // ????????????
    private BigInteger availableAmount = BigInteger.ZERO;
    // ????????????????????????
    private ConsensusAwardInfo awardInfo;
    /**
     * ????????????????????????????????????
     */
    private boolean enableDepositOthers = false;
    private DepositOthersManager depositOthersManager;

    private Map<String, UserInfo> userInfo;
    private Map<String, ConsensusAgentDepositInfo> agentDeposits;
    private PocmContract pocmContract;
    private PocmInfo pi;

    public ConsensusManager(Map<String, UserInfo> userInfo,
                            Map<String, ConsensusAgentDepositInfo> agentDeposits,
                            PocmContract pocmContract, PocmInfo pi) {
        awardInfo = new ConsensusAwardInfo(Msg.address().toString());
        enableDepositOthers();
        this.userInfo = userInfo;
        this.agentDeposits = agentDeposits;
        this.pocmContract = pocmContract;
        this.pi = pi;
    }

    /**
     * ????????????????????????
     * ????????????????????????????????????????????????????????????
     *
     * @param args ???????????????????????? eg. [[address, amount]]
     */
    public void _payable(String[][] args) {
        String[] award = args[0];
        String address = award[0];
        String amount = award[1];
        awardInfo.add(new BigInteger(amount));
    }


    private void enableDepositOthers() {
        require(!enableDepositOthers, "Repeat operation");
        enableDepositOthers = true;
        depositOthersManager = new DepositOthersManager();
        depositOthersManager.modifyMinJoinDeposit(MIN_JOIN_DEPOSIT);
    }

    public String[] addOtherAgent(String agentHash) {
        require(enableDepositOthers, "This feature is not turned on");
        return depositOthersManager.addOtherAgent(agentHash);
    }

    private void remove(String agentHash){
        depositOthersManager.removeAgent(agentHash, this);
    }

    public BigInteger otherDepositLockedAmount() {
        return depositOthersManager.otherDepositLockedAmount();
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     * ????????????????????????????????????????????????????????????
     *
     * @param value          ???????????????
     */
    public void createOrDepositIfPermitted(BigInteger value) {
        availableAmount = availableAmount.add(value);
        /**
         * ??????????????????
         */
        if(availableAmount.compareTo(MIN_JOIN_DEPOSIT) >= 0) {
            depositOthersManager.deposit(availableAmount, this);
        }
    }

    /**
     * ??????????????????????????????????????????????????????????????????????????????????????????
     */
    public void depositManually() {
        BigInteger amount = availableAmount;
        require(amount.compareTo(MIN_JOIN_DEPOSIT) >= 0, "The available amount is not enough to stake the node");
        require(depositOthersManager.otherAgentsSize() > 0, "No consensus node added");
        /**
         * ??????????????????
         */
        BigInteger actualDeposit = depositOthersManager.deposit(availableAmount, this);
        require(actualDeposit.compareTo(BigInteger.ZERO) > 0, "All consensus nodes have been fully staked");
    }

    public Set<String> getAgents() {
        return depositOthersManager.getAgents();
    }

    /**
     * ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     *
     * @param value ?????????????????????
     * @return true - ???????????????????????????, false - ????????????????????????????????????????????????
     */
    public boolean withdrawIfPermittedWrapper(BigInteger value) {
        if (availableAmount.compareTo(value) >= 0) {
            availableAmount = availableAmount.subtract(value);
            return true;
        } else {
            value = value.subtract(availableAmount);
            availableAmount = BigInteger.ZERO;
        }
        // ?????????????????????????????????
        depositOthersManager.withdrawInner(value, this);
        if (availableAmount.compareTo(value) < 0) {
            return false;
        }
        availableAmount = availableAmount.subtract(value);
        /**
         * ?????????????????????????????????????????????
         */
        if(availableAmount.compareTo(MIN_JOIN_DEPOSIT) >= 0) {
            depositOthersManager.deposit(availableAmount, this);
        }
        return true;
    }

    /**
     * ????????????????????????
     */
    public void transferConsensusReward(Address beneficiary) {
        BigInteger availableAward = awardInfo.getAvailableAward();
        require(availableAward.compareTo(BigInteger.ZERO) > 0, "No consensus reward amount available");
        // ??????
        awardInfo.resetAvailableAward();
        beneficiary.transfer(availableAward);
    }

    /**
     * ????????????????????????
     */
    public BigInteger getAvailableConsensusReward() {
        return awardInfo.getAvailableAward();
    }

    /**
     * ????????????????????????
     */
    public BigInteger getTransferedConsensusReward() {
        return awardInfo.getTransferedAward();
    }

    /**
     * ????????????????????????????????????
     */
    public BigInteger getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(BigInteger availableAmount) {
        this.availableAmount = availableAmount;
    }

    public void addAvailableAmount(BigInteger availableAmount) {
        this.availableAmount = this.availableAmount.add(availableAmount);
    }
    public void subAvailableAmount(BigInteger availableAmount) {
        this.availableAmount = this.availableAmount.subtract(availableAmount);
    }

    public void repairAmount(BigInteger value) {
        this.availableAmount = this.availableAmount.add(value);
    }

    public void repairConsensusDeposit(BigInteger value) {
        if(enableDepositOthers) {
            this.depositOthersManager.repairDeposit(value);
        }
    }

    public void modifyMinJoinDeposit(BigInteger value) {
        MIN_JOIN_DEPOSIT = value;
        if(enableDepositOthers) {
            depositOthersManager.modifyMinJoinDeposit(MIN_JOIN_DEPOSIT);
        }
    }

    public BigInteger getMinJoinDeposit() {
        return MIN_JOIN_DEPOSIT;
    }

    public void withdrawSpecifiedAmount(BigInteger value) {
        // ?????????????????????????????????
        depositOthersManager.withdrawInner(value, this);
    }

    public void emergencyRemoveAgentInner(String agentHash) {
        this.removeAgentInner(agentHash, true);
    }

    private void removeAgentInner(String agentHash, boolean emergency) {
        require(pi.openConsensus, "Consensus is not turned on");
        this.remove(agentHash);
        emit(new PocmRemoveAgentEvent(agentHash));

        //1.???????????????????????????????????????
        ConsensusAgentDepositInfo agentDepositInfo = agentDeposits.get(agentHash);
        require(agentDepositInfo != null, "This consensus node is not registered");

        String userAddress = agentDepositInfo.getDepositorAddress();
        UserInfo user = userInfo.get(userAddress);
        if (user != null) {
            if (!emergency) {
                // ?????????????????????????????????
                pocmContract.receiveAwardsByAddress(new Address(userAddress));
            }

            //2.??????????????????????????????
            BigInteger agentAmount = user.getAgentAmount();
            boolean openNodeAward = user.isOpenNodeAward();
            // ??????????????????????????????????????????user????????????????????????????????????????????????
            if (openNodeAward) {
                user.subAmount(BigInteger.ZERO, agentAmount);
                pi.subLpSupply(agentAmount);
            }
            if (user.getAvailableAmount().compareTo(BigInteger.ZERO) > 0) {
                user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));
                user.setAgentAmount(BigInteger.ZERO);
                user.setOpenNodeAward(false);
            } else {
                userInfo.remove(userAddress);
            }
        }
        agentDeposits.remove(agentHash);
    }

    public void removeAgentInner(String agentHash) {
        this.removeAgentInner(agentHash, false);
    }

    public BigInteger consensusEmergencyWithdraw(String joinAgentHash) {
        return depositOthersManager.consensusEmergencyWithdraw(joinAgentHash, this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"availableAmount\":")
                .append('\"').append(toNuls(availableAmount).toPlainString()).append('\"');
        sb.append(",\"awardInfo\":")
                .append(awardInfo.toString());
        if(enableDepositOthers) {
            sb.append(",\"depositOthersManager\":")
                    .append(depositOthersManager.toString());
        }
        sb.append('}');
        return sb.toString();
    }

}
