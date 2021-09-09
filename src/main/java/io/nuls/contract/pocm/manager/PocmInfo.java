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

import io.nuls.contract.sdk.Address;

import java.math.BigInteger;

/**
 * @author: PierreLuo
 * @date: 2021/9/9
 */
public class PocmInfo {

    //1天=24*60*60秒
    public final long TIMEPERDAY = 86400;
    public BigInteger _2000_NULS = BigInteger.valueOf(200000000000L);
    public final BigInteger _1e12 = BigInteger.TEN.pow(12);
    public Address candyToken; // Address of candy token contract.
    public int candyAssetChainId;// chainId of candy token contract.
    public int candyAssetId;// assetId of candy token contract.
    public Long lastRewardBlock;  // Last block number that token distribution occurs.
    public BigInteger accPerShare;    // Accumulated token per share, times 1e12. See below.
    public BigInteger candyPerBlock;
    public BigInteger lpSupply; // 抵押总量
    public BigInteger candySupply;// 糖果发行总量
    public int lockedTokenDay;// 获取Token奖励的锁定天数
    public long lockedTime;
    public BigInteger minimumDeposit;// 最低抵押na数量(1亿个na等于1个NULS）
    public boolean openConsensus = false;//是否开启合约共识功能
    public boolean openAwardConsensusNodeProvider = false;//是否奖励共识节点提供者
    public String authorizationCode;//dapp的唯一识别码

}
