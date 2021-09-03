package io.nuls.contract.pocm.event;

import io.nuls.contract.pocm.model.CurrentMingInfo;
import io.nuls.contract.sdk.Event;

import java.util.List;

public class CurrentMiningInfoEvent implements Event {

    List<CurrentMingInfo> mingInfosList;

    public CurrentMiningInfoEvent(List<CurrentMingInfo> list){
        this.mingInfosList=list;
    }

    public List<CurrentMingInfo> getMingInfosList() {
        return mingInfosList;
    }

    public void setMingInfosList(List<CurrentMingInfo> mingInfosList) {
        this.mingInfosList = mingInfosList;
    }
}


