package io.nuls.contract.pocm;

import io.nuls.contract.pocm.event.*;
import io.nuls.contract.pocm.manager.ConsensusManager;
import io.nuls.contract.pocm.manager.PocmInfo;
import io.nuls.contract.pocm.manager.TotalDepositManager;
import io.nuls.contract.pocm.model.ConsensusAgentDepositInfo;
import io.nuls.contract.pocm.model.CurrentMingInfo;
import io.nuls.contract.pocm.model.UserInfo;
import io.nuls.contract.pocm.ownership.Ownable;
import io.nuls.contract.pocm.util.AssetWrapper;
import io.nuls.contract.pocm.util.CandyToken;
import io.nuls.contract.pocm.util.NRC20Wrapper;
import io.nuls.contract.pocm.util.PocmUtil;
import io.nuls.contract.sdk.*;
import io.nuls.contract.sdk.annotation.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static io.nuls.contract.pocm.util.PocmUtil.*;
import static io.nuls.contract.sdk.Utils.*;

/**
 * @author: PierreLuo
 * @date: 2021/8/30
 */
public class PocmContract extends Ownable implements Contract {

    // POCM合约修订版本
    private final String VERSION = "V11";
    private PocmInfo pi = new PocmInfo();// 合约基础信息
    private Map<String, UserInfo> userInfo = new HashMap<String, UserInfo>();
    private BigInteger allocationAmount = BigInteger.ZERO;//已经分配的Token数量
    private TotalDepositManager totalDepositManager;// 总抵押金额管理器
    private ConsensusManager consensusManager;// 共识管理器
    private CandyToken candyTokenWrapper;
    private boolean isAllocationToken = false;//项目是否初始分配了糖果token
    private boolean isAcceptDeposit = false;//是否接受抵押
    private boolean isNRC20Candy;//糖果是否是NRC20资产
    private Map<String, ConsensusAgentDepositInfo> agentDeposits = new HashMap<String, ConsensusAgentDepositInfo>();

    /**
     * @param candyToken                        糖果token地址（NRC20资产，和`candyAssetChainId`,`candyAssetId`不能同时存在）
     * @param candyAssetChainId                 糖果资产链ID（非NRC20资产，和`candyToken`不能同时存在）
     * @param candyAssetId                      糖果资产ID（非NRC20资产，和`candyToken`不能同时存在）
     * @param candyPerBlock                     每个区块奖励的Token数量
     * @param amount                            糖果token分配总量
     * @param lockedTokenDay                    获取Token奖励的锁定天数
     * @param minimumDeposit                    最低抵押NULS数量
     * @param openConsensus                     是否开启合约共识
     * @param openAwardConsensusNodeProvider    是否奖励共识节点提供者
     * @param authorizationCode                 dapp的唯一识别码
     */
    public PocmContract(Address candyToken,
                        int candyAssetChainId,
                        int candyAssetId,
                        BigInteger candyPerBlock,
                        BigInteger amount,
                        int lockedTokenDay,
                        BigInteger minimumDeposit,
                        boolean openConsensus,
                        boolean openAwardConsensusNodeProvider,
                        String authorizationCode) {
        // 糖果资产检查
        int valid = candyAssetChainId + candyAssetId;
        if (candyToken == null && valid == 0) revert("initial: candyToken not good");
        require(candyToken == null || valid == 0, "initial: candyToken not good");
        if (candyToken != null) {
            require(candyToken.isContract(), "initial: candyToken not good");
            isNRC20Candy = true;
            this.candyTokenWrapper = new NRC20Wrapper(candyToken);
        } else {
            isNRC20Candy = false;
            this.candyTokenWrapper = new AssetWrapper(candyAssetChainId, candyAssetId);
        }
        require(candyPerBlock.compareTo(BigInteger.ZERO) > 0, "initial: candyPerBlock not good");
        require(amount.compareTo(BigInteger.ZERO) > 0, "initial: amount not good");
        require(lockedTokenDay >= 0, "initial: lockedTokenDay not good");
        require(minimumDeposit.compareTo(BigInteger.ZERO) > 0, "initial: minimumDeposit not good");

        pi.candyToken = candyToken;
        pi.candyAssetChainId = candyAssetChainId;
        pi.candyAssetId = candyAssetId;
        pi.candyPerBlock = candyPerBlock;
        pi.candySupply = amount;
        pi.lockedTokenDay = lockedTokenDay;
        pi.minimumDeposit = minimumDeposit;
        this.totalDepositManager = new TotalDepositManager();
        if (openConsensus) {
            openConsensusInner();
        }
        pi.openAwardConsensusNodeProvider = openAwardConsensusNodeProvider;
        pi.authorizationCode = authorizationCode;
        pi.accPerShare = BigInteger.ZERO;
        pi.lpSupply = BigInteger.ZERO;
        pi.lockedTime = pi.lockedTokenDay * pi.TIMEPERDAY;
        pi.lastRewardBlock = Block.number();
        setPocmInfo(pi);
    }

    @Override
    public void _payable() {
        revert("Do not accept direct transfers");
    }

    @Override
    @PayableMultyAsset
    public void _payableMultyAsset() {
        require(!isNRC20Candy, "transfer: do not accept direct transfers");
        MultyAssetValue[] values = Msg.multyAssetValues();
        if (values == null) return;
        require(values.length == 1, "transfer: asset not good");
        MultyAssetValue value = values[0];
        require(value.getAssetChainId() == pi.candyAssetChainId && value.getAssetId() == pi.candyAssetId, "transfer: asset not good");
    }

    /**
     * 共识奖励收益处理
     * 创建的节点的100%佣金比例，收益地址只有当前合约地址
     * (底层系统调用，不能被任何人调用)
     *
     * @param args 区块奖励地址明细 eg. [[address, amount], [address, amount], ...]
     */
    @Override
    @Payable
    public void _payable(String[][] args) {
        consensusManager._payable(args);
    }

    public void addCandySupply(BigInteger _amount) {
        onlyOwnerOrOffcial();
        pi.candySupply = pi.candySupply.add(_amount);
    }

    @Payable
    public void depositForOwn() {
        require(isAllocationToken() && isAcceptDeposit(), "No enough candy token in the contract");
        require(unreachedLimitCandySupply(), "No enough candy supply in the contract");
        Address sender = Msg.sender();
        String senderAddress = sender.toString();
        UserInfo user = this.userInfo.get(senderAddress);
        BigInteger _amount = Msg.value();
        // 退还抵押金的小数位
        BigInteger decimalValue = PocmUtil.extractDecimal(_amount);
        boolean hasDecimal = decimalValue.compareTo(BigInteger.ZERO) > 0;
        if (hasDecimal) {
            // 防止退回的小数金额太小
            if (decimalValue.compareTo(MININUM_TRANSFER_AMOUNT) < 0) {
                decimalValue = decimalValue.add(ONE_NULS);
            }
            _amount = _amount.subtract(decimalValue);
            require(decimalValue.add(_amount).compareTo(Msg.value()) == 0, "Decimal parsing error, deposit: " + Msg.value());
        }
        require(_amount.compareTo(pi.minimumDeposit) >= 0, "amount not good");
        updatePool();
        if (user != null) {
            // 领取奖励
            this.receiveInternal(sender, user);
        }

        // 90% available for deposit
        BigDecimal bigDecimalValue = new BigDecimal(_amount);
        BigInteger availableAmount = AVAILABLE_PERCENT.multiply(bigDecimalValue).toBigInteger();
        long blockNumber = Block.number();

        if (user == null) {
            user = new UserInfo(_amount, availableAmount, BigInteger.ZERO, blockNumber);
            this.userInfo.put(senderAddress, user);
        } else {
            user.addAmount(_amount, availableAmount);
            user.setLastDepositHeight(blockNumber);
        }
        pi.lpSupply = pi.lpSupply.add(availableAmount);
        user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));

        totalDepositManager.add(availableAmount);

        // 退还抵押金的小数位
        if (hasDecimal) {
            sender.transfer(decimalValue);
        }
        // 抵押事件
        emit(new DepositDetailInfoEvent(_amount, 0, _amount, availableAmount, _amount.subtract(availableAmount), blockNumber, senderAddress));
    }

    public void receiveAwards(String unused) {
        this.receiveAwardsByAddress(Msg.sender());
    }

    public void receiveAwardsByAddress(Address address) {
        require(address != null, "empty address");
        String addressStr = address.toString();
        UserInfo user = this.userInfo.get(addressStr);
        require(user != null, "deposit user not exist");
        updatePool();
        this.receiveInternal(address, user);
        user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));
    }

    // Withdraw LP tokens from pool.
    public void withdraw(BigInteger _amount) {
        Address sender = Msg.sender();
        String senderAddress = sender.toString();
        UserInfo user = this.userInfo.get(senderAddress);
        require(user != null, "user not exist");
        this.withdrawByUser(sender, user, _amount);
    }

    // Withdraw without caring about rewards. EMERGENCY ONLY.
    public void emergencyWithdraw() {
        Address sender = Msg.sender();
        String senderAddress = sender.toString();
        UserInfo user = this.userInfo.get(senderAddress);
        require(user != null, "user not exist");
        this.emergencyWithdrawByUser(sender, user);
    }


    public void quit(String unused) {
        Address sender = Msg.sender();
        String senderAddress = sender.toString();
        UserInfo user = this.userInfo.get(senderAddress);
        require(user != null, "user not exist");
        this.withdrawByUser(sender, user, user.getAmount());
    }

    public void updateTotalAllocation() {
        BigInteger balance = candyTokenWrapper.balanceOf(Msg.address());
        if (balance.compareTo(BigInteger.ZERO) > 0) {
            this.isAcceptDeposit = true;
        } else {
            this.isAcceptDeposit = false;
        }
    }

    public void clearContract() {
        onlyOwner();
        BigInteger balance = Msg.address().balance();
        require(balance.compareTo(ONE_NULS) <= 0, "余额不得大于1NULS");
        require(balance.compareTo(BigInteger.ZERO) > 0, "余额为零，无需清空");
        contractCreator.transfer(balance);
    }


    /**
     * 开启共识获得糖果奖励
     */
    public void openConsensusNodeAward() {
        onlyOffcial();
        pi.openAwardConsensusNodeProvider = true;
    }
    /**
     * 关闭共识获得糖果奖励
     */
    public void closeConsensusNodeAward() {
        onlyOffcial();
        pi.openAwardConsensusNodeProvider = false;
    }
    /**
     * 开启共识功能
     */
    public void openConsensus() {
        onlyOffcial();
        openConsensusInner();
    }

    public void closeConsensus() {
        onlyOffcial();
        require(pi.openConsensus, "Consensus has been turned off");
        pi.openConsensus = false;
        totalDepositManager.closeConsensus();
    }

    public void modifyMinJoinDeposit(BigInteger value) {
        onlyOffcial();
        require(pi.openConsensus, "Consensus is not turned on");
        require(value.compareTo(pi._2000_NULS) >= 0, "Amount too small");
        consensusManager.modifyMinJoinDeposit(value);
    }

    public void consensusWithdrawSpecifiedAmount(BigInteger value) {
        onlyOwnerOrOffcial();
        require(pi.openConsensus, "Consensus is not turned on");
        consensusManager.withdrawSpecifiedAmount(value);
    }

    public void repairConsensus(BigInteger value) {
        onlyOffcial();
        require(pi.openConsensus, "Consensus is not turned on");
        consensusManager.repairAmount(value);
    }

    public void repairConsensusDeposit(BigInteger value) {
        onlyOffcial();
        require(pi.openConsensus, "Consensus is not turned on");
        consensusManager.repairConsensusDeposit(value);
    }

    public void repairTotalDepositManager(BigInteger value) {
        onlyOffcial();
        totalDepositManager.repairAmount(value);
    }

    /**
     * 手动把闲置的抵押金委托到共识节点
     */
    public void depositConsensusManually() {
        require(pi.openConsensus, "Consensus is not turned on");
        consensusManager.depositManually();
    }

    /**
     * 合约拥有者获取共识奖励金额
     */
    public void transferConsensusRewardByOwner() {
        onlyOwner();
        require(pi.openConsensus, "Consensus is not turned on");
        consensusManager.transferConsensusReward(owner);
    }

    /**
     * 添加其他节点的共识信息
     *
     * @param agentHash 其他共识节点的hash
     */
    public void addOtherAgent(String agentHash) {
        onlyOwnerOrOffcial();
        require(pi.openConsensus, "Consensus is not turned on");
        require(isAllocationToken() && isAcceptDeposit(), "No enough candy token in the contract");
        String[] agentInfo = consensusManager.addOtherAgent(agentHash);
        String agentAddress = agentInfo[0];
        Collection<ConsensusAgentDepositInfo> agentDepositInfos = agentDeposits.values();
        for (ConsensusAgentDepositInfo agentDepositInfo : agentDepositInfos) {
            require(!agentDepositInfo.getDepositorAddress().equals(agentAddress), "The creator address of the node being added conflicts with the creator address of the added node");
        }
        BigInteger agentValue = new BigInteger(agentInfo[3]);
        emit(new AgentEvent(agentHash, agentValue));
        BigInteger availableValue = agentValue;
        // 不给节点提供者奖励
        if (!pi.openAwardConsensusNodeProvider) {
            availableValue = BigInteger.ZERO;
        }
        updatePool();
        long currentHeight = Block.number();
        UserInfo user = this.userInfo.get(agentAddress);
        if (user == null) {
            user = new UserInfo(BigInteger.ZERO, availableValue, BigInteger.ZERO, currentHeight);
            user.setAgentAmount(agentValue);
            this.userInfo.put(agentAddress, user);
        } else {
            // 存在抵押记录，领取奖励
            this.receiveInternal(new Address(agentAddress), user);

            //更新抵押信息
            user.addAmount(BigInteger.ZERO, availableValue);
            user.setAgentAmount(agentValue);
        }
        user.setOpenNodeAward(pi.openAwardConsensusNodeProvider);
        pi.lpSupply = pi.lpSupply.add(availableValue);
        user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));

        ConsensusAgentDepositInfo agentDepositInfo = new ConsensusAgentDepositInfo(agentHash, agentAddress, 0);
        agentDeposits.put(agentHash, agentDepositInfo);
    }

    /**
     * 删除节点信息
     *
     * @param agentHash 其他共识节点的hash
     */
    public void removeAgent(String agentHash) {
        onlyOwnerOrOffcial();
        consensusManager.removeAgentInner(agentHash);
    }

    public void quitAll() {
        onlyOwnerOrOffcial();
        require(consensusManager.getAgents() == null, "Please remove the consensus node first");
        boolean hasAgents = !agentDeposits.isEmpty();
        Set<String> skippedSet = new HashSet<String>();
        if (hasAgents) {
            Collection<ConsensusAgentDepositInfo> agentDepositInfos = agentDeposits.values();
            for (ConsensusAgentDepositInfo info : agentDepositInfos) {
                skippedSet.add(info.getDepositorAddress());
            }
        }
        Set<String> userSet = this.userInfo.keySet();
        List<String> userList = new ArrayList<String>(userSet);
        for (String user : userList) {
            if (hasAgents && skippedSet.contains(user)) {
                continue;
            }
            this.quitByUser(user);
        }
    }

    public void giveUpAll() {
        onlyOffcial();
        require(consensusManager.getAgents() == null, "Please remove the consensus node first");
        boolean hasAgents = !agentDeposits.isEmpty();
        Set<String> skippedSet = new HashSet<String>();
        if (hasAgents) {
            Collection<ConsensusAgentDepositInfo> agentDepositInfos = agentDeposits.values();
            for (ConsensusAgentDepositInfo info : agentDepositInfos) {
                skippedSet.add(info.getDepositorAddress());
            }
        }
        Set<String> userSet = this.userInfo.keySet();
        List<String> userList = new ArrayList<String>(userSet);
        for (String user : userList) {
            if (hasAgents && skippedSet.contains(user)) {
                continue;
            }
            this.giveUpByUser(user);
        }
    }

    public BigInteger consensusEmergencyWithdraw(String joinAgentHash) {
        onlyOffcial();
        return consensusManager.consensusEmergencyWithdraw(joinAgentHash);
    }

    @View
    public int totalDepositAddressCount() {
        return this.userInfo.size();
    }

    @View
    public String totalDeposit() {
        return toNuls(totalDepositManager.getTotalDeposit()).toPlainString();
    }

    @View
    public String totalDepositDetail() {
        return totalDepositManager.getTotalDepositDetail();
    }

    @View
    public String getAuthorizationCode() {
        return pi.authorizationCode;
    }

    @View
    public BigInteger minimumDeposit() {
        return pi.minimumDeposit;
    }

    @View
    public BigInteger lpSupply() {
        return pi.lpSupply;
    }

    @View
    public int getLockedDay() {
        return pi.lockedTokenDay;
    }

    @View
    public String version() {
        return VERSION;
    }

    @View
    public BigInteger getCandySupply() {
        return pi.candySupply;
    }
    /**
     * 查询可领取的共识奖励金额
     */
    @View
    public String ownerAvailableConsensusAward() {
        require(pi.openConsensus, "未开启共识功能");
        return consensusManager.getAvailableConsensusReward().toString();
    }

    /**
     * 查询共识总奖励金额
     */
    @View
    public String ownerTotalConsensusAward() {
        require(pi.openConsensus, "未开启共识功能");
        return consensusManager.getAvailableConsensusReward().add(consensusManager.getTransferedConsensusReward()).toString();
    }

    /**
     * 查询可委托共识的空闲金额
     */
    @View
    public String freeAmountForConsensusDeposit() {
        require(pi.openConsensus, "未开启共识功能");
        return consensusManager.getAvailableAmount().toString();
    }

    @View
    public String getMinJoinDeposit() {
        require(pi.openConsensus, "未开启共识功能");
        return consensusManager.getMinJoinDeposit().toString();
    }

    /**
     * 查询合约当前所有信息
     */
    @View
    public String wholeConsensusInfo() {
        String totalDepositDetail = totalDepositDetail();
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalDepositDetail\":")
                .append('\"').append(totalDepositDetail).append('\"');
        if (pi.openConsensus) {
            sb.append(",\"consensusManager\":")
                    .append(consensusManager == null ? "0" : consensusManager.toString());
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * 查找用户的抵押信息
     *
     * @return
     */
    @View
    @JSONSerializable
    public UserInfo getDepositInfo(Address address) {
        return this.userInfo.get(address.toString());
    }

    /**
     * 领取抵押者参与抵押的交易未领取的收益
     *
     * @param depositorAddress 抵押者账户地址
     * @return
     */
    @View
    public String calcUnReceiveAwards(@Required Address depositorAddress, String unused) {
        return this.pendingToken(depositorAddress);
    }

    /**
     * 未分配的Token数量
     *
     * @return
     */
    @View
    public String calcUnAllocationTokenAmount() {
        return this.candyTokenWrapper.balanceOf(Msg.address()).toString();
    }

    /**
     * 已分配的Token数量
     *
     * @return
     */
    @View
    public String getAllocationAmount() {
        return this.allocationAmount.toString();
    }

    // View function to see pending token on frontend.
    @View
    public String pendingToken(Address _user) {
        String userAddress = _user.toString();
        UserInfo user = this.userInfo.get(userAddress);
        require(user != null, "user not exist");
        long blockNumber = Block.number();
        if (blockNumber > pi.lastRewardBlock && pi.lpSupply.compareTo(BigInteger.ZERO) > 0) {
            BigInteger reward = BigInteger.valueOf(blockNumber - pi.lastRewardBlock).multiply(pi.candyPerBlock);
            pi.accPerShare = pi.accPerShare.add(reward.multiply(pi._1e12).divide(pi.lpSupply));   // 此处乘以1e12，在下面会除以1e12
        }
        BigInteger pendingReward = user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12).subtract(user.getRewardDebt());
        return pendingReward.toString();
    }

    private void receiveInternal(Address sender, UserInfo user) {
        BigInteger candyBalance = this.candyTokenWrapper.balanceOf(Msg.address());
        require(candyBalance.compareTo(BigInteger.ZERO) > 0, "No enough candy token in the contract");
        require(unreachedLimitCandySupply(), "No enough candy supply in the contract");
        if (user.getAvailableAmount().compareTo(BigInteger.ZERO) > 0) {
            BigInteger pending = user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12).subtract(user.getRewardDebt());
            if (pending.compareTo(BigInteger.ZERO) > 0) {
                // 发放的奖励 <= 糖果余额
                BigInteger amount;
                if (candyBalance.compareTo(pending) > 0) {
                    amount = pending;
                } else {
                    amount = candyBalance;
                    this.isAcceptDeposit = false;
                }
                // 发放的奖励 + 已发放的奖励 <= 糖果发行量
                BigInteger limitCandy = pi.candySupply.subtract(this.allocationAmount);
                if (limitCandy.compareTo(amount) <= 0) {
                    amount = limitCandy;
                }
                this.candyTokenWrapper.transferLocked(sender, amount, pi.lockedTime);
                this.allocationAmount = this.allocationAmount.add(amount);
                // 奖励领取事件
                ArrayList<CurrentMingInfo> list = new ArrayList<CurrentMingInfo>();
                list.add(new CurrentMingInfo(0, amount, sender.toString(), 0));
                emit(new CurrentMiningInfoEvent(list));
            }
        }
    }

    private void withdrawByUser(Address sender, UserInfo user, BigInteger _amount) {
        String senderAddress = sender.toString();
        require(user.getAmount().compareTo(_amount) >= 0, "withdraw: amount not good");
        updatePool();
        // 领取奖励
        this.receiveInternal(sender, user);

        BigInteger available = AVAILABLE_PERCENT.multiply(new BigDecimal(_amount)).toBigInteger();
        boolean isEnoughBalance = totalDepositManager.subtract(available);
        require(isEnoughBalance, "The balance is not enough to refund the deposit, please contact the project party, the deposit: " + available);
        user.subAmount(_amount, available);
        pi.lpSupply = pi.lpSupply.subtract(available);
        user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));
        if (_amount.compareTo(BigInteger.ZERO) > 0) {
            sender.transfer(_amount);
        }
        if (user.getAvailableAmount().equals(BigInteger.ZERO) && user.getAgentAmount().equals(BigInteger.ZERO)) {
            this.userInfo.remove(senderAddress);
        }
        // 提现事件
        emit(new Withdraw(senderAddress, _amount.toString()));
    }

    private void emergencyWithdrawByUser(Address sender, UserInfo user) {
        String senderAddress = sender.toString();
        BigInteger _amount = user.getAmount();
        BigInteger available = AVAILABLE_PERCENT.multiply(new BigDecimal(_amount)).toBigInteger();
        boolean isEnoughBalance = totalDepositManager.subtract(available);
        require(isEnoughBalance, "The balance is not enough to refund the deposit, please contact the project party, the deposit: " + available);
        if (_amount.compareTo(BigInteger.ZERO) > 0) {
            sender.transfer(_amount);
            user.subAmount(_amount, available);
            // 紧急提现，退出抵押事件，避免节点创建者紧急提现，QuitDepositEvent的事件在后台可能会把正常抵押删除
            List<Long> depositNumbers = new ArrayList<Long>();
            depositNumbers.add(0L);
            emit(new QuitDepositEvent(depositNumbers, senderAddress));
        }
        if (user.getAvailableAmount().equals(BigInteger.ZERO)) {
            this.userInfo.remove(senderAddress);
        } else {
            updatePool();
            user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));
        }
        pi.lpSupply = pi.lpSupply.subtract(available);
    }

    private boolean isAcceptDeposit() {
        return isAcceptDeposit;
    }

    private boolean unreachedLimitCandySupply() {
        return pi.candySupply.compareTo(this.allocationAmount) > 0;
    }

    private boolean isAllocationToken() {
        if (!isAllocationToken) {
            BigInteger balance = candyTokenWrapper.balanceOf(Msg.address());
            if (balance.compareTo(pi.candySupply) >= 0) {
                isAllocationToken = true;
                isAcceptDeposit = true;
            }
        }
        return isAllocationToken;
    }

    private void giveUpByUser(String userAddress) {
        UserInfo userInfo = this.userInfo.get(userAddress);
        if (userInfo == null) {
            return;
        }
        this.emergencyWithdrawByUser(new Address(userAddress), userInfo);
    }

    private void quitByUser(String userAddress) {
        UserInfo userInfo = this.userInfo.get(userAddress);
        if (userInfo == null) {
            return;
        }
        this.withdrawByUser(new Address(userAddress), userInfo, userInfo.getAmount());
    }

    private void updatePool() {
        long blockNumber = Block.number();
        if (blockNumber <= pi.lastRewardBlock) {
            return;
        }
        if (pi.lpSupply.equals(BigInteger.ZERO)) {
            pi.lastRewardBlock = blockNumber;
            return;
        }
        BigInteger reward = BigInteger.valueOf(blockNumber - pi.lastRewardBlock).multiply(pi.candyPerBlock);
        pi.accPerShare = pi.accPerShare.add(reward.multiply(pi._1e12).divide(pi.lpSupply));
        pi.lastRewardBlock = blockNumber;
    }

    private void openConsensusInner() {
        require(!pi.openConsensus, "Consensus has been turned on");
        pi.openConsensus = true;
        if (this.consensusManager == null) {
            this.consensusManager = new ConsensusManager(this.userInfo, this.agentDeposits, this, this.pi);
        }
        this.totalDepositManager.openConsensus(this.consensusManager);
    }

}