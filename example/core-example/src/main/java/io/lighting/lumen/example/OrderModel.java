package io.lighting.lumen.example;

import io.lighting.lumen.active.Model;
import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.LogicDelete;
import io.lighting.lumen.meta.Table;

@Table(name = "orders")
public class OrderModel extends Model<OrderModel> {
    @Id(strategy = IdStrategy.AUTO)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "status")
    private String status;

    @LogicDelete(active = "0", deleted = "1")
    @Column(name = "deleted")
    private Integer deleted;

    public OrderModel() {
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
