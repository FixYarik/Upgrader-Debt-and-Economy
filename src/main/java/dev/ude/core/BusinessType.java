package dev.ude.core;
public enum BusinessType {
    FARM(2_000), SHOP(3_000), MINE(5_000), LOGISTICS(7_500), FACTORY(15_000);
    public final long purchasePrice;
    BusinessType(long purchasePrice) { this.purchasePrice=purchasePrice; }
}
