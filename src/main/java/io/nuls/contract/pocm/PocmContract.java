package io.nuls.contract.pocm;

import io.nuls.contract.pocm.event.*;
import io.nuls.contract.pocm.manager.PocmInfo;
import io.nuls.contract.pocm.manager.TotalDepositManager;
import io.nuls.contract.pocm.model.CurrentMingInfo;
import io.nuls.contract.pocm.model.UserInfo;
import io.nuls.contract.pocm.ownership.Ownable;
import io.nuls.contract.sdk.*;
import io.nuls.contract.sdk.annotation.JSONSerializable;
import io.nuls.contract.sdk.annotation.PayableMultyAsset;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;
import io.nuls.contract.sdk.token.AssetWrapper;
import io.nuls.contract.sdk.token.NRC20Wrapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static io.nuls.contract.sdk.Utils.*;

/**
 * @author: PierreLuo
 * @date: 2021/8/30
 */
public class PocmContract extends Ownable implements Contract {

    // POCM合约修订版本
    private final String VERSION = "V19";
    private PocmInfo pi = new PocmInfo();// 合约基础信息
    private Map<String, UserInfo> userInfo = new HashMap<String, UserInfo>();
    private BigInteger allocationAmount = BigInteger.ZERO;//已经分配的Token数量
    private TotalDepositManager totalDepositManager;// 总抵押金额管理器
    private boolean isAllocationToken = false;//项目是否初始分配了糖果token
    private boolean isAcceptStaking = false;//是否接受质押

    /**
     * 注意: 如果糖果是NRC20资产，那么 candyAssetChainId 和 candyAssetId 要设置为0
     *
     * @param depositAssetChainId               质押资产链ID
     * @param depositAssetId                    质押资产ID
     * @param candyToken                        糖果token地址（NRC20资产，和`candyAssetChainId`,`candyAssetId`不能同时存在）
     * @param candyAssetChainId                 糖果资产链ID（非NRC20资产，和`candyToken`不能同时存在）
     * @param candyAssetId                      糖果资产ID（非NRC20资产，和`candyToken`不能同时存在）
     * @param candyPerBlock                     每个区块奖励的Token数量
     * @param candySupply                       糖果token分配总量
     * @param lockedTokenDay                    获取Token奖励的锁定天数
     * @param minimumStaking                    最低质押NULS数量
     * @param maximumStaking                    最高质押NULS数量
     * @param authorizationCode                 dapp的唯一识别码
     */
    public PocmContract(
                        int depositAssetChainId,
                        int depositAssetId,
                        Address candyToken,
                        int candyAssetChainId,
                        int candyAssetId,
                        BigInteger candyPerBlock,
                        BigInteger candySupply,
                        int lockedTokenDay,
                        BigInteger minimumStaking,
                        BigInteger maximumStaking,
                        String authorizationCode) {
        if(depositAssetChainId == 1 && depositAssetId == 1) revert("initial: depositToken not good");
        // 糖果资产检查
        int valid = candyAssetChainId + candyAssetId;
        if (candyToken == null && valid == 0) revert("initial: candyToken not good");
        require(candyToken == null || valid == 0, "initial: candyToken not good");
        if(candyAssetChainId == 1 && candyAssetId == 1) revert("initial: candyToken not good");
        if (candyToken != null) {
            require(candyToken.isContract(), "initial: candyToken not good");
            pi.isNRC20Candy = true;
            pi.candyTokenWrapper = new NRC20Wrapper(candyToken);
        } else {
            pi.isNRC20Candy = false;
            pi.candyTokenWrapper = new AssetWrapper(candyAssetChainId, candyAssetId);
        }
        pi.depositAssetDecimals = Utils.assetDecimals(depositAssetChainId, depositAssetId);
        pi.oneDeposit = BigInteger.TEN.pow(pi.depositAssetDecimals);
        require(candyPerBlock.compareTo(BigInteger.ZERO) > 0, "initial: candyPerBlock not good");
        require(candySupply.compareTo(BigInteger.ZERO) > 0, "initial: candySupply not good");
        require(lockedTokenDay >= 0, "initial: lockedTokenDay not good");
        require(minimumStaking.compareTo(BigInteger.ZERO) > 0, "initial: minimumStaking not good");
        require(maximumStaking.compareTo(minimumStaking) >= 0, "initial: maximumStaking not good");

        pi.depositTokenWrapper = new AssetWrapper(depositAssetChainId, depositAssetId);
        pi.depositAssetChainId = depositAssetChainId;
        pi.depositAssetId = depositAssetId;
        pi.candyToken = candyToken;
        pi.candyAssetChainId = candyAssetChainId;
        pi.candyAssetId = candyAssetId;
        pi.candyPerBlock = candyPerBlock;
        pi.candySupply = candySupply;
        pi.lockedTokenDay = lockedTokenDay;
        pi.minimumStaking = minimumStaking;
        pi.maximumStaking = maximumStaking;
        this.totalDepositManager = new TotalDepositManager(pi);

        pi.authorizationCode = authorizationCode;
        pi.accPerShare = BigInteger.ZERO;
        pi.lockedTime = pi.lockedTokenDay * pi.TIMEPERDAY;
        pi.lastRewardBlock = Block.number();
        pi.endBlock = Block.number() + 12717449280L;
        setPocmInfo(pi);
        //TODO pierre 创建合约事件
        //emit(new PocmCreateContract19Event(
        //        pi.isNRC20Candy ? candyToken.toString() : null,
        //        candyAssetChainId, candyAssetId,
        //        candyPerBlock, candySupply,
        //        lockedTokenDay,
        //        minimumStaking,
        //        maximumStaking,
        //        authorizationCode));
    }

    @Override
    public void _payable() {
        revert("Do not accept direct transfers");
    }

    @Override
    @PayableMultyAsset
    public void _payableMultyAsset() {
        require(!pi.isNRC20Candy, "transfer: do not accept direct transfers");
        MultyAssetValue[] values = Msg.multyAssetValues();
        if (values == null) return;
        require(values.length == 1, "transfer: asset not good");
        MultyAssetValue value = values[0];
        require(value.getAssetChainId() == pi.candyAssetChainId && value.getAssetId() == pi.candyAssetId, "transfer: asset not good");
    }

    public void addCandySupply(BigInteger _amount) {
        onlyOwnerOrOfficial();
        pi.candySupply = pi.candySupply.add(_amount);
        emit(new PocmCandySupplyEvent(pi.candySupply));
    }

    public void updateMaximumStaking(BigInteger maximumStaking) {
        onlyOwnerOrOfficial();
        require(maximumStaking.compareTo(pi.minimumStaking) >= 0, "update: maximumStaking not good");
        pi.maximumStaking = maximumStaking;
        emit(new PocmMaximumDepositEvent(pi.maximumStaking));
    }

    private BigInteger checkDepositAmount() {
        MultyAssetValue[] values = Msg.multyAssetValues();
        require(values != null && values.length == 1, "deposit: asset not good");
        MultyAssetValue value = values[0];
        require(value.getAssetChainId() == pi.depositAssetChainId && value.getAssetId() == pi.depositAssetId, "deposit: asset not good");
        return value.getValue();
    }

    @PayableMultyAsset
    public void depositForOwn() {
        require(isAllocationToken(), "No enough candy token in the contract");
        require(isAcceptStaking(), "Cannot staking, please check the contract");
        Address sender = Msg.sender();
        String senderAddress = sender.toString();
        UserInfo user = this.userInfo.get(senderAddress);
        BigInteger _amount = this.checkDepositAmount();
        // 退还抵押金的小数位
        require(_amount.compareTo(pi.minimumStaking) >= 0, "amount not good[minimum]");
        require(_amount.compareTo(pi.maximumStaking) <= 0, "amount not good[maximum]");
        updatePool();
        if (user != null) {
            // 领取奖励
            this.receiveInternal(sender, user);
        }

        long blockNumber = Block.number();

        if (user == null) {
            user = new UserInfo(_amount, BigInteger.ZERO, blockNumber);
            this.userInfo.put(senderAddress, user);
        } else {
            user.addAmount(_amount);
            user.setLastDepositHeight(blockNumber);
        }
        require(user.getAmount().compareTo(pi.maximumStaking) <= 0, "user amount not good[maximum]");
        pi.addLpSupply(_amount);
        user.setRewardDebt(user.getAmount().multiply(pi.accPerShare).divide(pi._1e12));

        totalDepositManager.add(_amount);

        // 抵押事件
        emit(new DepositDetailInfoEvent(_amount, 0, _amount, _amount, BigInteger.ZERO, blockNumber, senderAddress));
    }

    public void receiveAwards(String unused) {
        this.receiveAwardsByAddress(Msg.sender());
    }

    public void receiveAwardsByAddress(Address address) {
        require(address != null, "empty address");
        String addressStr = address.toString();
        UserInfo user = this.userInfo.get(addressStr);
        require(user != null, "user not exist");
        updatePool();
        this.receiveInternal(address, user);
        user.setRewardDebt(user.getAmount().multiply(pi.accPerShare).divide(pi._1e12));
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

    // Withdraw without caring about rewards. EMERGENCY ONLY.
    public void giveUp() {
        emergencyWithdraw();
    }


    public void quit(String unused) {
        Address sender = Msg.sender();
        String senderAddress = sender.toString();
        UserInfo user = this.userInfo.get(senderAddress);
        require(user != null, "user not exist");
        this.withdrawByUser(sender, user, user.getAmount());
    }

    public void updateTotalAllocation() {
        BigInteger balance = pi.candyTokenWrapper.balanceOf(Msg.address());
        if (balance.compareTo(BigInteger.ZERO) > 0) {
            this.isAcceptStaking = true;
        } else {
            this.isAcceptStaking = false;
        }
    }

    public void refreshEndBlock(BigInteger value) {
        onlyOfficial();
        BigInteger blockCount = value.divide(pi.candyPerBlock);
        pi.endBlock += blockCount.longValue();
    }

    public void repairTotalDepositManager(BigInteger value) {
        onlyOfficial();
        totalDepositManager.repairAmount(value);
    }


    public void quitAll() {
        onlyOwnerOrOfficial();
        Set<String> userSet = this.userInfo.keySet();
        List<String> userList = new ArrayList<String>(userSet);
        for (String user : userList) {
            this.quitByUser(user);
        }
    }

    public void quitByAddresses(String[] addresses) {
        onlyOwnerOrOfficial();
        for (String user : addresses) {
            this.quitByUser(user);
        }
    }

    public void receiveAll() {
        Set<String> userSet = this.userInfo.keySet();
        List<String> userList = new ArrayList<String>(userSet);
        for (String user : userList) {
            this.receiveAwardsByAddress(new Address(user));
        }
    }

    public void receiveByAddresses(String[] addresses) {
        for (String user : addresses) {
            this.receiveAwardsByAddress(new Address(user));
        }
    }

    public void giveUpAll() {
        onlyOfficial();
        Set<String> userSet = this.userInfo.keySet();
        List<String> userList = new ArrayList<String>(userSet);
        for (String user : userList) {
            this.giveUpByUser(user);
        }
    }

    public void giveUpByAddresses(String[] addresses) {
        onlyOfficial();
        for (String user : addresses) {
            this.giveUpByUser(user);
        }
    }

    public void updateC(int c) {
        onlyOfficial();
        require(c >= 10 && c < 100, "10 <= Value < 100.");
        pi.c = BigInteger.valueOf(c);
    }

    @View
    public int totalDepositAddressCount() {
        return this.userInfo.size();
    }

    @View
    public String totalDeposit() {
        return toNuls(totalDepositManager.getTotalDeposit(), pi.depositAssetDecimals).toPlainString();
    }

    @View
    public String getAuthorizationCode() {
        return pi.authorizationCode;
    }

    @View
    public BigInteger minimumStaking() {
        return pi.minimumStaking;
    }

    @View
    public BigInteger lpSupply() {
        return pi.getLpSupply();
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

    @View
    public long getEndBlock() {
        return pi.endBlock;
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
     * 领取质押者参与质押的交易未领取的收益
     *
     * @param stakingAddress 质押者账户地址
     * @return
     */
    @View
    public String calcUnReceiveAwards(@Required Address stakingAddress, String unused) {
        return this.pendingToken(stakingAddress);
    }

    /**
     * 未分配的Token数量
     *
     * @return
     */
    @View
    public String calcUnAllocationTokenAmount() {
        return pi.candyTokenWrapper.balanceOf(Msg.address()).toString();
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
        blockNumber = blockNumber < pi.endBlock ? blockNumber : pi.endBlock;
        if (blockNumber > pi.lastRewardBlock && pi.getLpSupply().compareTo(BigInteger.ZERO) > 0) {
            BigInteger reward = BigInteger.valueOf(blockNumber - pi.lastRewardBlock).multiply(pi.candyPerBlock);
            pi.accPerShare = pi.accPerShare.add(reward.multiply(pi._1e12).divide(pi.getLpSupply()));   // 此处乘以1e12，在下面会除以1e12
        }
        BigInteger pendingReward = user.getAmount().multiply(pi.accPerShare).divide(pi._1e12).subtract(user.getRewardDebt());
        return pendingReward.toString();
    }

    private BigInteger checkCandyBalance() {
        BigInteger candyBalance = pi.candyTokenWrapper.balanceOf(Msg.address());
        require(candyBalance.compareTo(BigInteger.ZERO) > 0, "No enough candy token in the contract");
        return candyBalance;
    }

    private void receiveInternal(Address sender, UserInfo user) {
        BigInteger candyBalance = checkCandyBalance();
        if (user.getAmount().compareTo(BigInteger.ZERO) > 0) {
            BigInteger pending = user.getAmount().multiply(pi.accPerShare).divide(pi._1e12).subtract(user.getRewardDebt());
            if (pending.compareTo(BigInteger.ZERO) > 0) {
                // 发放的奖励 <= 糖果余额
                BigInteger amount;
                if (candyBalance.compareTo(pending) > 0) {
                    amount = pending;
                } else {
                    amount = candyBalance;
                    this.isAcceptStaking = false;
                }
                long lockedTime = pi.lockedTime;
                if (pi.isNRC20Candy) {
                    lockedTime += Block.timestamp();
                }
                // 奖励领取事件
                ArrayList<CurrentMingInfo> list = new ArrayList<CurrentMingInfo>();
                pi.candyTokenWrapper.transferLocked(sender, amount, lockedTime);
                list.add(new CurrentMingInfo(0, amount, sender.toString(), 0));
                this.allocationAmount = this.allocationAmount.add(amount);
                emit(new CurrentMiningInfoEvent(list));
            }
        }
    }

    private void withdrawByUser(Address sender, UserInfo user, BigInteger _amount) {
        String senderAddress = sender.toString();
        require(_amount.compareTo(BigInteger.ZERO) > 0, "withdraw: amount not good");
        require(user.getAmount().compareTo(_amount) >= 0, "withdraw: amount not good");
        updatePool();
        // 领取奖励
        this.receiveInternal(sender, user);

        boolean isEnoughBalance = totalDepositManager.subtract(_amount);
        require(isEnoughBalance, "The balance is not enough to refund the staking, please contact the project party, the staking: " + _amount);
        user.subAmount(_amount);
        pi.subLpSupply(_amount);
        user.setRewardDebt(user.getAmount().multiply(pi.accPerShare).divide(pi._1e12));
        if (_amount.compareTo(BigInteger.ZERO) > 0) {
            pi.depositTokenWrapper.transfer(sender, _amount);
        }
        if (user.getAmount().compareTo(BigInteger.ZERO) == 0) {
            this.userInfo.remove(senderAddress);
        }
        // 提现事件
        emit(new PocmWithdrawEvent(senderAddress, _amount.toString()));
    }

    private void emergencyWithdrawByUser(Address sender, UserInfo user) {
        String senderAddress = sender.toString();
        BigInteger _amount = user.getAmount();
        boolean isEnoughBalance = totalDepositManager.subtract(_amount);
        require(isEnoughBalance, "The balance is not enough to refund the staking, please contact the project party, the staking: " + _amount);
        if (_amount.compareTo(BigInteger.ZERO) > 0) {
            pi.depositTokenWrapper.transfer(sender, _amount);
            user.subAmount(_amount);
        }
        if (user.getAmount().compareTo(BigInteger.ZERO) == 0) {
            this.userInfo.remove(senderAddress);
        } else {
            updatePool();
            user.setRewardDebt(user.getAmount().multiply(pi.accPerShare).divide(pi._1e12));
        }
        pi.subLpSupply(_amount);
    }

    private boolean isAcceptStaking() {
        return isAcceptStaking;
    }

    private boolean isAllocationToken() {
        if (!isAllocationToken) {
            BigInteger balance = pi.candyTokenWrapper.balanceOf(Msg.address());
            if (balance.compareTo(BigInteger.ZERO) > 0) {
                isAllocationToken = true;
                isAcceptStaking = true;
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
        if (pi.getLpSupply().compareTo(BigInteger.ZERO) == 0) {
            pi.lastRewardBlock = blockNumber;
            return;
        }
        blockNumber = blockNumber < pi.endBlock ? blockNumber : pi.endBlock;
        BigInteger reward = BigInteger.valueOf(blockNumber - pi.lastRewardBlock).multiply(pi.candyPerBlock);
        pi.accPerShare = pi.accPerShare.add(reward.multiply(pi._1e12).divide(pi.getLpSupply()));
        pi.lastRewardBlock = blockNumber;
    }

    private BigDecimal toNuls(BigInteger na, int decimals) {
        return new BigDecimal(na).movePointLeft(decimals);
    }
}