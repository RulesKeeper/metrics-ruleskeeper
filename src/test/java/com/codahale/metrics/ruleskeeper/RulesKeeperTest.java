package com.codahale.metrics.ruleskeeper;

import java.util.concurrent.TimeUnit;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;

public class RulesKeeperTest {

	public static final MetricRegistry metricsRegistry = new MetricRegistry();

	public static void main(String[] args) {
		final RulesKeeper ruleskeeper = new RulesKeeper("http://127.0.0.1:9000/");
		final RulesKeeperReporter rk = RulesKeeperReporter.forRegistry(metricsRegistry).prefixedWith("RulesKeeperTest").convertRatesTo(TimeUnit.SECONDS)
				.convertDurationsTo(TimeUnit.MILLISECONDS).filter(MetricFilter.ALL).build(ruleskeeper);
		rk.start(5, TimeUnit.SECONDS);

		RulesKeeperSendSingleMetric worker = new RulesKeeperSendSingleMetric();
		while (true) {
			worker.run();

		}

	}

}
