package io.github.yasmiins.orderexecutionservice.config;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulator")
public class SimulatedFillProperties {

    private boolean enabled = true;
    private long tickMs = 1000;
    private BigDecimal minFillPercent = new BigDecimal("0.25");
    private BigDecimal maxFillPercent = new BigDecimal("0.50");
    private BigDecimal defaultPrice = new BigDecimal("100");
    private Map<String, BigDecimal> prices = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTickMs() {
        return tickMs;
    }

    public void setTickMs(long tickMs) {
        this.tickMs = tickMs;
    }

    public BigDecimal getMinFillPercent() {
        return minFillPercent;
    }

    public void setMinFillPercent(BigDecimal minFillPercent) {
        this.minFillPercent = minFillPercent;
    }

    public BigDecimal getMaxFillPercent() {
        return maxFillPercent;
    }

    public void setMaxFillPercent(BigDecimal maxFillPercent) {
        this.maxFillPercent = maxFillPercent;
    }

    public BigDecimal getDefaultPrice() {
        return defaultPrice;
    }

    public void setDefaultPrice(BigDecimal defaultPrice) {
        this.defaultPrice = defaultPrice;
    }

    public Map<String, BigDecimal> getPrices() {
        return prices;
    }

    public void setPrices(Map<String, BigDecimal> prices) {
        this.prices = prices;
    }
}
