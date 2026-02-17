package io.github.yasmiins.orderexecutionservice.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Instrument {

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    protected Instrument() {
    }

    public Instrument(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Instrument instrument)) {
            return false;
        }
        return Objects.equals(symbol, instrument.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol);
    }
}
