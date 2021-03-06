package com.demo.transfer.domain.model.account;

import com.demo.transfer.common.BaseEvent;
import com.demo.transfer.common.MqMessageStatus;
import com.demo.transfer.domain.model.transfer.TransferRecord;

/**
 * description: 账户扣款事件 <br>
 * date: 2020/2/8 <br>
 * author: Kehong <br>
 * version: 1.0 <br>
 */
public class DeductAccountEvent extends BaseEvent {

    private TransferRecord transferRecord;

    public DeductAccountEvent(MqMessageStatus mqMessageStatus, TransferRecord transferRecord) {
        super(mqMessageStatus);
        this.transferRecord = transferRecord;
    }

    public TransferRecord getTransferRecord() {
        return transferRecord;
    }

    public void setTransferRecord(TransferRecord transferRecord) {
        this.transferRecord = transferRecord;
    }
}
