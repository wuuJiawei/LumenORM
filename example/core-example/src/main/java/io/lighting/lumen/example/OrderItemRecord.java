package io.lighting.lumen.example;

import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.Table;

@Table(name = "ORDER_ITEMS")
public class OrderItemRecord {
    @Id(strategy = IdStrategy.AUTO)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ORDER_ID")
    private Long orderId;

    @Column(name = "SKU")
    private String sku;

    public OrderItemRecord() {
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getSku() {
        return sku;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }
}
