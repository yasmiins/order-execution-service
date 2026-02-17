package io.github.yasmiins.orderexecutionservice.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order.validation")
public class OrderValidationProperties {

    private List<String> supportedSymbols;
    private BigDecimal maxOrderSize;

    public List<String> getSupportedSymbols() {
        return supportedSymbols;
    }

    public void setSupportedSymbols(List<String> supportedSymbols) {
        this.supportedSymbols = supportedSymbols;
    }

    public BigDecimal getMaxOrderSize() {
        return maxOrderSize;
    }

    public void setMaxOrderSize(BigDecimal maxOrderSize) {
        this.maxOrderSize = maxOrderSize;
    }
}
