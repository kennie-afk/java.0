package com.smartseason.order.domain;

import com.smartseason.order.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cart_items", indexes = {
        @Index(name = "ix_cart_items_cart_id", columnList = "cart_id"),
        @Index(name = "ix_cart_items_listing_id", columnList = "listing_id")
})
public class CartItem extends BaseEntity {

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "commodity_code", nullable = false)
    private String commodityCode;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "seller_org_id", nullable = false)
    private UUID sellerOrgId;

    public UUID getCartId() { return cartId; }
    public void setCartId(UUID cartId) { this.cartId = cartId; }

    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }

    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public UUID getSellerOrgId() { return sellerOrgId; }
    public void setSellerOrgId(UUID sellerOrgId) { this.sellerOrgId = sellerOrgId; }

}
