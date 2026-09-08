package com.smartseason.payout.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartseason.ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;

    private int readCapacity = 300;
    private double readRefillPerSecond = 5.0;

    private int writeCapacity = 60;
    private double writeRefillPerSecond = 1.0;

    private int anonymousCapacity = 30;
    private double anonymousRefillPerSecond = 0.5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getReadCapacity() { return readCapacity; }
    public void setReadCapacity(int readCapacity) { this.readCapacity = readCapacity; }

    public double getReadRefillPerSecond() { return readRefillPerSecond; }
    public void setReadRefillPerSecond(double v) { this.readRefillPerSecond = v; }

    public int getWriteCapacity() { return writeCapacity; }
    public void setWriteCapacity(int writeCapacity) { this.writeCapacity = writeCapacity; }

    public double getWriteRefillPerSecond() { return writeRefillPerSecond; }
    public void setWriteRefillPerSecond(double v) { this.writeRefillPerSecond = v; }

    public int getAnonymousCapacity() { return anonymousCapacity; }
    public void setAnonymousCapacity(int anonymousCapacity) { this.anonymousCapacity = anonymousCapacity; }

    public double getAnonymousRefillPerSecond() { return anonymousRefillPerSecond; }
    public void setAnonymousRefillPerSecond(double v) { this.anonymousRefillPerSecond = v; }
}
