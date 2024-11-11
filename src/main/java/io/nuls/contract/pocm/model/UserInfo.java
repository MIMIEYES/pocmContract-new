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
package io.nuls.contract.pocm.model;

import java.math.BigInteger;

/**
 * @author: PierreLuo
 * @date: 2021/8/31
 */
public class UserInfo {

    private BigInteger amount;
    private BigInteger rewardDebt;
    private long lastDepositHeight;// last deposit height

    public UserInfo(BigInteger amount, BigInteger rewardDebt, long lastDepositHeight) {
        this.amount = amount;
        this.rewardDebt = rewardDebt;
        this.lastDepositHeight = lastDepositHeight;
    }

    public void addAmount(BigInteger amount) {
        this.amount = this.amount.add(amount);
    }

    public void subAmount(BigInteger amount) {
        this.amount = this.amount.subtract(amount);
    }

    public BigInteger getAmount() {
        return amount;
    }

    public void setAmount(BigInteger amount) {
        this.amount = amount;
    }

    public BigInteger getRewardDebt() {
        return rewardDebt;
    }

    public void setRewardDebt(BigInteger rewardDebt) {
        this.rewardDebt = rewardDebt;
    }

    public long getLastDepositHeight() {
        return lastDepositHeight;
    }

    public void setLastDepositHeight(long lastDepositHeight) {
        this.lastDepositHeight = lastDepositHeight;
    }

}
