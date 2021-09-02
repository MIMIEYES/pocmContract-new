package io.nuls.contract.pocm.util;

import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Event;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;

import java.math.BigInteger;

public interface NRC20Token {

    String name();

    String symbol();

    int decimals();

    BigInteger totalSupply();

    BigInteger balanceOf(Address owner);

    BigInteger allowance(Address owner, Address spender);

    boolean transfer(Address to, BigInteger value);

    boolean transferLocked(Address to, BigInteger value, long lockedTime);

    boolean transferFrom(Address from, Address to, BigInteger value);

    boolean approve(Address spender, BigInteger value);


}
