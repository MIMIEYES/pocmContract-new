package io.nuls.contract.pocm;

import io.nuls.contract.pocm.event.*;
import io.nuls.contract.pocm.manager.ConsensusManager;
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
    //1天=24*60*60秒
    private final long TIMEPERDAY = 86400;
    public BigInteger _2000_NULS = BigInteger.valueOf(200000000000L);
    private final BigInteger _1e12 = BigInteger.TEN.pow(12);

    Map<String, UserInfo> userInfo = new HashMap<String, UserInfo>();
    Address candyToken; // Address of candy token contract.
    int candyAssetChainId;// chainId of candy token contract.
    int candyAssetId;// assetId of candy token contract.
    Long lastRewardBlock;  // Last block number that token distribution occurs.
    BigInteger accPerShare;    // Accumulated token per share, times 1e12. See below.
    BigInteger candyPerBlock;
    BigInteger lpSupply; // 抵押总量
    BigInteger candySupply;// 糖果发行总量
    private BigInteger allocationAmount = BigInteger.ZERO;//已经分配的Token数量
    private int lockedTokenDay;// 获取Token奖励的锁定天数
    private long lockedTime;
    private BigInteger minimumDeposit;// 最低抵押na数量(1亿个na等于1个NULS）
    private int minimumLocked;// 最短锁定区块（参数类型为数字，N个块后才可退出抵押）
    private boolean openConsensus = false;//是否开启合约共识功能
    private boolean openAwardConsensusNodeProvider = false;//是否奖励共识节点提供者
    private String authorizationCode;//dapp的唯一识别码
    private BigInteger candyPerCycle;// 单周期奖励的Token数量
    private int awardingCycle;// 奖励发放周期（参数类型为数字，每过N块发放一次）
    private TotalDepositManager totalDepositManager;// 总抵押金额管理器
    private ConsensusManager consensusManager;// 共识管理器
    private CandyToken candyTokenWrapper;
    private boolean isAllocationToken = false;//项目是否初始分配了糖果token
    private boolean isAcceptDeposit = false;//是否接受抵押
    private boolean isNRC20Candy = false;//糖果是否是NRC20资产
    private Map<String, ConsensusAgentDepositInfo> agentDeposits = new HashMap<String, ConsensusAgentDepositInfo>();

    /**
     * @param candyToken                        糖果token地址（NRC20资产，和`candyAssetChainId`,`candyAssetId`不能同时存在）
     * @param candyAssetChainId                 糖果资产链ID（非NRC20资产，和`candyToken`不能同时存在）
     * @param candyAssetId                      糖果资产ID（非NRC20资产，和`candyToken`不能同时存在）
     * @param candyPerCycle                     单周期奖励的Token数量
     * @param awardingCycle                     奖励发放周期
     * @param amount                            糖果token分配总量
     * @param lockedTokenDay                    获取Token奖励的锁定天数
     * @param minimumDeposit                    最低抵押NULS数量
     * @param minimumLocked                     锁定区块个数（N个块后才可退出抵押）
     * @param openConsensus                     是否开启合约共识
     * @param openAwardConsensusNodeProvider    是否奖励共识节点提供者
     * @param authorizationCode                 dapp的唯一识别码
     */
    public PocmContract(Address candyToken,
                        int candyAssetChainId,
                        int candyAssetId,
                        BigInteger candyPerCycle,
                        int awardingCycle,
                        BigInteger amount,
                        int lockedTokenDay,
                        BigInteger minimumDeposit,
                        int minimumLocked,
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
        require(candyPerCycle.compareTo(BigInteger.ZERO) > 0, "initial: candyPerCycle not good");
        require(awardingCycle > 0, "initial: awardingCycle not good");
        require(amount.compareTo(BigInteger.ZERO) > 0, "initial: amount not good");
        require(lockedTokenDay >= 0, "initial: lockedTokenDay not good");
        require(minimumDeposit.compareTo(BigInteger.ZERO) > 0, "initial: minimumDeposit not good");
        require(minimumLocked > 0, "initial: minimumLocked not good");

        this.candyToken = candyToken;
        this.candyAssetChainId = candyAssetChainId;
        this.candyAssetId = candyAssetId;
        this.candyPerCycle = candyPerCycle;
        this.awardingCycle = awardingCycle;
        this.candySupply = amount;
        this.lockedTokenDay = lockedTokenDay;
        this.minimumDeposit = minimumDeposit;
        this.minimumLocked = minimumLocked;
        this.openConsensus = openConsensus;
        this.totalDepositManager = new TotalDepositManager();
        if (openConsensus) {
            openConsensus();
        }
        this.openAwardConsensusNodeProvider = openAwardConsensusNodeProvider;
        this.authorizationCode = authorizationCode;
        this.accPerShare = BigInteger.ZERO;
        this.lpSupply = BigInteger.ZERO;
        this.lockedTime = this.lockedTokenDay * TIMEPERDAY;
        this.lastRewardBlock = Block.number();
        this.candyPerBlock = candyPerCycle.divide(BigInteger.valueOf(awardingCycle));
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
        require(value.getAssetChainId() == candyAssetChainId && value.getAssetId() == candyAssetId, "transfer: asset not good");
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
        this.candySupply = this.candySupply.add(_amount);
    }

    @Payable
    public void depositForOwn() {
        require(isAllocationToken() && isAcceptDeposit(), "No candy token in the contract");
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
        require(_amount.compareTo(minimumDeposit) >= 0, "amount not good");
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
        this.lpSupply = this.lpSupply.add(availableAmount);
        user.setRewardDebt(user.getAvailableAmount().multiply(this.accPerShare).divide(_1e12));

        totalDepositManager.add(availableAmount);

        // 退还抵押金的小数位
        if (hasDecimal) {
            sender.transfer(decimalValue);
        }
        // 抵押事件
        emit(new DepositDetailInfoEvent(_amount, 0, _amount, availableAmount, _amount.subtract(availableAmount), blockNumber, senderAddress));
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

    public void receiveAwards(String unused) {
        this.receiveAwardsByAddress(Msg.sender());
    }

    public void receiveAwardsByAddress(Address address) {
        require(address != null, "empty address");
        String addressStr = address.toString();
        UserInfo user = this.userInfo.get(addressStr);
        require(user != null, "deposit user not exist");
        this.receiveInternal(address, user);
    }

    /**
     * 在抵押期间，更新合约可分配的Token数量
     */
    public void updateTotalAllocation() {
        BigInteger balance = candyTokenWrapper.balanceOf(Msg.address());
        if (balance.compareTo(BigInteger.ZERO) > 0) {
            this.isAcceptDeposit = true;
        } else {
            this.isAcceptDeposit = false;
        }
    }

    /**
     * 合约创建者清空剩余余额
     */
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
        onlyOwner();
        this.openAwardConsensusNodeProvider = true;
    }
    /**
     * 关闭共识获得糖果奖励
     */
    public void closeConsensusNodeAward() {
        onlyOwnerOrOffcial();
        this.openAwardConsensusNodeProvider = false;
    }
    /**
     * 开启共识功能
     */
    public void openConsensus() {
        onlyOwner();
        require(!this.openConsensus, "Consensus has been turned on");
        this.openConsensus = true;
        if (this.consensusManager == null) {
            this.consensusManager = new ConsensusManager();
        }
        this.totalDepositManager.openConsensus(this.consensusManager);
    }

    public void closeConsensus() {
        onlyOwnerOrOffcial();
        require(openConsensus, "Consensus has been turned off");
        this.openConsensus = false;
        totalDepositManager.closeConsensus();
    }

    public void modifyMinJoinDeposit(BigInteger value) {
        onlyOffcial();
        require(openConsensus, "Consensus is not turned on");
        require(value.compareTo(_2000_NULS) >= 0, "Amount too small");
        consensusManager.modifyMinJoinDeposit(value);
    }

    public void withdrawSpecifiedAmount(BigInteger value) {
        onlyOwnerOrOffcial();
        require(openConsensus, "Consensus is not turned on");
        consensusManager.withdrawSpecifiedAmount(value);
    }

    public void repairConsensus(BigInteger value) {
        onlyOffcial();
        require(openConsensus, "Consensus is not turned on");
        consensusManager.repairAmount(value);
    }

    public void repairTotalDepositManager(BigInteger value) {
        onlyOffcial();
        totalDepositManager.repairAmount(value);
    }

    /**
     * 手动把闲置的抵押金委托到共识节点
     */
    public void depositConsensusManually() {
        require(openConsensus, "Consensus is not turned on");
        consensusManager.depositManually();
    }

    /**
     * 合约拥有者获取共识奖励金额
     */
    public void transferConsensusRewardByOwner() {
        onlyOwner();
        require(openConsensus, "Consensus is not turned on");
        consensusManager.transferConsensusReward(owner);
    }

    /**
     * 添加其他节点的共识信息
     *
     * @param agentHash 其他共识节点的hash
     */
    public void addOtherAgent(String agentHash) {
        onlyOwnerOrOffcial();
        require(openConsensus, "Consensus is not turned on");
        require(isAllocationToken() && isAcceptDeposit(), "No candy token in the contract");
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
        if (!openAwardConsensusNodeProvider) {
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
        user.setOpenNodeAward(openAwardConsensusNodeProvider);
        this.lpSupply = this.lpSupply.add(availableValue);
        user.setRewardDebt(user.getAvailableAmount().multiply(this.accPerShare).divide(_1e12));

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
        require(openConsensus, "Consensus is not turned on");
        consensusManager.removeAgent(agentHash);
        emit(new RemoveAgentEvent(agentHash));

        //1.共识节点的创建者先领取奖励
        ConsensusAgentDepositInfo agentDepositInfo = agentDeposits.get(agentHash);
        require(agentDepositInfo != null, "This consensus node is not registered");

        String userAddress = agentDepositInfo.getDepositorAddress();
        UserInfo user = this.userInfo.get(userAddress);
        require(user != null, "user not exist");
        // 存在抵押记录，领取奖励
        this.receiveInternal(new Address(userAddress), user);

        //2.共识节点的创建者退出
        BigInteger agentAmount = user.getAgentAmount();
        boolean openNodeAward = user.isOpenNodeAward();
        // 如果共识节点有糖果奖励，扣减user的可用抵押金，扣减项目的总抵押金
        if (openNodeAward) {
            user.subAmount(BigInteger.ZERO, agentAmount);
            this.lpSupply = this.lpSupply.subtract(agentAmount);
        }
        if (!user.getAvailableAmount().equals(BigInteger.ZERO)) {
            user.setRewardDebt(user.getAvailableAmount().multiply(this.accPerShare).divide(_1e12));
        } else {
            this.userInfo.remove(userAddress);
        }
        user.setAgentAmount(BigInteger.ZERO);
        user.setOpenNodeAward(false);
        agentDeposits.remove(agentHash);
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
    public long awardingCycle() {
        return this.awardingCycle;
    }

    @View
    public BigInteger getCandyPerCycle() {
        return candyPerCycle;
    }

    @View
    public String getAuthorizationCode() {
        return authorizationCode;
    }

    @View
    public BigInteger minimumDeposit() {
        return this.minimumDeposit;
    }

    @View
    public int minimumLocked() {
        return this.minimumLocked;
    }

    @View
    public int getLockedDay() {
        return this.lockedTokenDay;
    }

    @View
    public String version() {
        return VERSION;
    }

    /**
     * 查询可领取的共识奖励金额
     */
    @View
    public String ownerAvailableConsensusAward() {
        require(openConsensus, "未开启共识功能");
        return consensusManager.getAvailableConsensusReward().toString();
    }

    /**
     * 查询共识总奖励金额
     */
    @View
    public String ownerTotalConsensusAward() {
        require(openConsensus, "未开启共识功能");
        return consensusManager.getAvailableConsensusReward().add(consensusManager.getTransferedConsensusReward()).toString();
    }

    /**
     * 查询可委托共识的空闲金额
     */
    @View
    public String freeAmountForConsensusDeposit() {
        require(openConsensus, "未开启共识功能");
        return consensusManager.getAvailableAmount().toString();
    }

    @View
    public String getMinJoinDeposit() {
        require(openConsensus, "未开启共识功能");
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
        if (openConsensus) {
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
        if (blockNumber > this.lastRewardBlock && this.lpSupply.compareTo(BigInteger.ZERO) > 0) {
            BigInteger reward = BigInteger.valueOf(blockNumber - this.lastRewardBlock).multiply(this.candyPerBlock);
            this.accPerShare = this.accPerShare.add(reward.multiply(_1e12).divide(this.lpSupply));   // 此处乘以1e12，在下面会除以1e12
        }
        BigInteger pendingReward = user.getAvailableAmount().multiply(this.accPerShare).divide(_1e12).subtract(user.getRewardDebt());
        return pendingReward.toString();
    }

    private void receiveInternal(Address sender, UserInfo user) {
        BigInteger candyBalance = this.candyTokenWrapper.balanceOf(Msg.address());
        require(candyBalance.compareTo(BigInteger.ZERO) > 0, "No candy token in the contract");
        require(unreachedLimitCandySupply(), "No enough candy supply in the contract");
        if (user.getAvailableAmount().compareTo(BigInteger.ZERO) > 0) {
            BigInteger pending = user.getAvailableAmount().multiply(this.accPerShare).divide(_1e12).subtract(user.getRewardDebt());
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
                BigInteger limitCandy = this.candySupply.subtract(this.allocationAmount);
                if (limitCandy.compareTo(amount) <= 0) {
                    amount = limitCandy;
                }
                this.candyTokenWrapper.transferLocked(sender, amount, lockedTime);
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
        require(checkDepositLocked(user) == -1, "withdraw: deposit locked!");
        updatePool();
        // 领取奖励
        this.receiveInternal(sender, user);

        BigInteger available = AVAILABLE_PERCENT.multiply(new BigDecimal(_amount)).toBigInteger();
        boolean isEnoughBalance = totalDepositManager.subtract(available);
        require(isEnoughBalance, "The balance is not enough to refund the deposit, please contact the project party, the deposit: " + available);
        user.subAmount(_amount, available);
        this.lpSupply = this.lpSupply.subtract(available);
        user.setRewardDebt(user.getAvailableAmount().multiply(this.accPerShare).divide(_1e12));
        if (_amount.compareTo(BigInteger.ZERO) > 0) {
            sender.transfer(_amount);
        }
        if (user.getAvailableAmount().equals(BigInteger.ZERO)) {
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
        }
        // 紧急提现，退出抵押事件
        List<Long> depositNumbers = new ArrayList<Long>();
        depositNumbers.add(0L);
        emit(new QuitDepositEvent(depositNumbers, senderAddress));
        this.lpSupply = this.lpSupply.subtract(available);
        this.userInfo.remove(senderAddress);
    }

    private boolean isAcceptDeposit() {
        return isAcceptDeposit;
    }

    private boolean unreachedLimitCandySupply() {
        return this.candySupply.compareTo(this.allocationAmount) > 0;
    }

    private boolean isAllocationToken() {
        if (!isAllocationToken) {
            BigInteger balance = candyTokenWrapper.balanceOf(Msg.address());
            if (balance.compareTo(candySupply) >= 0) {
                isAllocationToken = true;
                isAcceptDeposit = true;
            }
        }
        return isAllocationToken;
    }

    private long checkDepositLocked(UserInfo userInfo) {
        long currentHeight = Block.number();
        long unLockedHeight = userInfo.getLastDepositHeight() + minimumLocked + 1;
        if (unLockedHeight > currentHeight) {
            // 锁定中
            return unLockedHeight;
        }
        //已解锁
        return -1;
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
        if (blockNumber <= this.lastRewardBlock) {
            return;
        }
        if (this.lpSupply.equals(BigInteger.ZERO)) {
            this.lastRewardBlock = blockNumber;
            return;
        }
        BigInteger reward = BigInteger.valueOf(blockNumber - this.lastRewardBlock).multiply(this.candyPerBlock);
        this.accPerShare = this.accPerShare.add(reward.multiply(_1e12).divide(lpSupply));
        this.lastRewardBlock = blockNumber;
    }



}