package dev.ude.core;

/** Fixed total fee, not daily compounding. No real-world financial service. */
public enum LoanType {
    MICRO(100, 1_000, 15), PERSONAL(1_000, 10_000, 10), BUSINESS(5_000, 100_000, 8), COLLATERAL(100, 100_000, 5);
    public final long min, max;
    public final int feePercent;
    LoanType(long min, long max, int feePercent) { this.min=min; this.max=max; this.feePercent=feePercent; }
}
