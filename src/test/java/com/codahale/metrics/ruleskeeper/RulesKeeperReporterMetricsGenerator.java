package com.codahale.metrics.ruleskeeper;

import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.ruleskeeper.RulesKeeper;
import com.codahale.metrics.ruleskeeper.RulesKeeperReporter;

public class RulesKeeperReporterMetricsGenerator {

	final MetricRegistry metrics = new MetricRegistry();
	private final Histogram generateTime = metrics.histogram(MetricRegistry.name("RulesKeeperReporterMetricsGenerator", "generate-time"));
	private final Timer responseTime = metrics.timer(MetricRegistry.name("generateMethod", "responseTime"));

	private int callCounter4Gauge = 0;

	public RulesKeeperReporterMetricsGenerator() {
		final RulesKeeper ruleskeeper = new RulesKeeper("http://127.0.0.1:9000/");
		final RulesKeeperReporter reporter = RulesKeeperReporter.forRegistry(metrics).prefixedWith("ruleskeeper-reporter").convertRatesTo(TimeUnit.SECONDS)
				.convertDurationsTo(TimeUnit.MILLISECONDS).filter(MetricFilter.ALL).build(ruleskeeper);
		reporter.start(5, TimeUnit.SECONDS);

		// final ConsoleReporter console = ConsoleReporter.forRegistry(metrics).convertRatesTo(TimeUnit.SECONDS).convertDurationsTo(TimeUnit.MILLISECONDS).build();
		// console.start(5, TimeUnit.SECONDS);
	}

	public static void main(String[] args) {
		System.out.println("Starting RulesKeeperReporterMetricsGenerator");
		final RulesKeeperReporterMetricsGenerator gen = new RulesKeeperReporterMetricsGenerator();

		gen.metrics.register(MetricRegistry.name("GaugeRKTest", "size"), new Gauge<Integer>() {
			@Override
			public Integer getValue() {
				return gen.callCounter4Gauge;
			}
		});

		Counter counter = gen.metrics.counter(MetricRegistry.name("CounterRKTest", "PendingJobs"));

		Meter requests = gen.metrics.meter(MetricRegistry.name("MeterRKTest", "callToGeneratorMethod"));

		try {
			while (true) {
				gen.generate();
				counter.inc();
				requests.mark();
				Thread.sleep((long) (Math.random() * 1000));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void generate() {
		final Timer.Context context = responseTime.time();
		callCounter4Gauge++;
		generateTime.update((long) (Math.random() * 1000));
		context.stop();
	}
}
