package io.lighting.lumen.example;

import io.lighting.lumen.active.ActiveRecord;
import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.LogicDelete;
import io.lighting.lumen.meta.Table;

@Table(name = "ORDERS")
public class OrderRecord extends ActiveRecord<OrderRecord> {
    @Id(strategy = IdStrategy.AUTO)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ORDER_NO")
    private String orderNo;

    @Column(name = "STATUS")
    private String status;

    @LogicDelete(active = "0", deleted = "1")
    @Column(name = "DELETED")
    private Integer deleted;

    public OrderRecord() {
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getStatus() {
        return status;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
