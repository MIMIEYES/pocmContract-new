package io.nuls.contract.pocm.ownership;

import io.nuls.contract.pocm.manager.PocmInfo;
import io.nuls.contract.pocm.util.AssetWrapper;
import io.nuls.contract.pocm.util.CandyToken;
import io.nuls.contract.pocm.util.NRC20Wrapper;
import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Event;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.annotation.Payable;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;

import java.math.BigInteger;

import static io.nuls.contract.sdk.Utils.emit;
import static io.nuls.contract.sdk.Utils.require;

/**
 * @author: Long
 * @date: 2019-03-15
 */
public class Ownable {

    /**
     * 合约创建者
     */
    protected Address contractCreator;

    protected Address owner;

    protected String OFFICIAL_ADDRESS;

    private PocmInfo pi;

    public Ownable() {
        this.owner = Msg.sender();
        this.contractCreator = this.owner;
        if (this.owner.toString().startsWith("NULS")) {
            OFFICIAL_ADDRESS = "NULSd6Hgga3y4ZDKAdTHmir6R8Xf3Uei1v7LR";
        } else {
            OFFICIAL_ADDRESS = "tNULSeBaMshNPEnuqiDhMdSA4iNs6LMgjY6tcL";
        }
    }

    @View
    public Address viewOwner() {
        return owner;
    }

    @View
    public String viewContractCreator() {
        return this.contractCreator != null ? this.contractCreator.toString() : "";
    }

    protected void onlyOwner() {
        require(Msg.sender().equals(owner), "Only the owner of the contract can execute it.");
    }

    protected void onlyOwnerOrOffcial() {
        require(Msg.sender().equals(owner) || Msg.sender().toString().equals(OFFICIAL_ADDRESS), "Refused.");
    }

    protected void onlyOffcial() {
        require(Msg.sender().toString().equals(OFFICIAL_ADDRESS), "Refused.");
    }

    /**
     * 转让合约所有权
     *
     * @param newOwner
     */
    public void transferOwnership(Address newOwner) {
        onlyOwner();
        emit(new OwnershipTransferredEvent(owner, newOwner));
        owner = newOwner;
    }


    @Payable
    public void repairBalance() {
        onlyOwnerOrOffcial();
    }

    public void transferOtherNRC20(@Required Address nrc20, @Required Address to, @Required BigInteger value) {
        onlyOwner();
        require(!Msg.address().equals(nrc20), "Do nothing by yourself");
        require(nrc20.isContract(), "[" + nrc20.toString() + "] is not a contract address");
        NRC20Wrapper wrapper = new NRC20Wrapper(nrc20);
        BigInteger balance = wrapper.balanceOf(Msg.address());
        require(balance.compareTo(value) >= 0, "No enough balance");
        wrapper.transfer(to, value);
    }

    public void transferProjectCandyAsset(Address to, BigInteger value) {
        onlyOwner();
        CandyToken wrapper;
        if (pi.candyAssetChainId + pi.candyAssetId == 0) {
            wrapper = new NRC20Wrapper(pi.candyToken);
        } else {
            wrapper = new AssetWrapper(pi.candyAssetChainId, pi.candyAssetId);
        }
        BigInteger balance = wrapper.balanceOf(Msg.address());
        require(balance.compareTo(value) >= 0, "No enough balance");
        wrapper.transfer(to, value);
    }

    protected void setPocmInfo(PocmInfo pi) {
        this.pi = pi;
    }

    /**
     * 转移owner
     */
    class OwnershipTransferredEvent implements Event {

        //先前拥有者
        private Address previousOwner;

        //新的拥有者
        private Address newOwner;

        public OwnershipTransferredEvent(Address previousOwner, Address newOwner) {
            this.previousOwner = previousOwner;
            this.newOwner = newOwner;
        }

        public Address getPreviousOwner() {
            return previousOwner;
        }

        public void setPreviousOwner(Address previousOwner) {
            this.previousOwner = previousOwner;
        }

        public Address getNewOwner() {
            return newOwner;
        }

        public void setNewOwner(Address newOwner) {
            this.newOwner = newOwner;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o){
                return true;
            }
            if (o == null || getClass() != o.getClass()){
                return false;
            }

            OwnershipTransferredEvent that = (OwnershipTransferredEvent) o;

            if (previousOwner != null ? !previousOwner.equals(that.previousOwner) : that.previousOwner != null){
                return false;
            }

            return newOwner != null ? newOwner.equals(that.newOwner) : that.newOwner == null;
        }

        @Override
        public int hashCode() {
            int result = previousOwner != null ? previousOwner.hashCode() : 0;
            result = 31 * result + (newOwner != null ? newOwner.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "OwnershipTransferredEvent{" +
                    "previousOwner=" + previousOwner +
                    ", newOwner=" + newOwner +
                    '}';
        }

    }

}
