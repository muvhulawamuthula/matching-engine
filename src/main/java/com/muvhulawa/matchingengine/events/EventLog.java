package com.muvhulawa.matchingengine.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EventLog {
    private final List<Trade> trades = new ArrayList<>();
    private final List<ExecutionReport> reports = new ArrayList<>();

    public void recordTrade(Trade trade) {
        trades.add(trade);
    }

    public void recordReport(ExecutionReport report) {
        reports.add(report);
    }

    public List<Trade> getTrades() {
        return Collections.unmodifiableList(trades);
    }

    public List<ExecutionReport> getReports() {
        return Collections.unmodifiableList(reports);
    }
}
